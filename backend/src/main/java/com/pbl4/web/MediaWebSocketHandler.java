package com.pbl4.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/** Classroom relay: independent bounded queues keep a slow recipient off the receive thread. */
@Component
public class MediaWebSocketHandler extends BinaryWebSocketHandler {
    private static final int SCREEN=1, AUDIO=2, MAX_SCREEN=1_500_000, AUDIO_INPUT=645;
    private final ClassroomService rooms;
    private final MediaTicketService tickets;
    private final ObjectMapper json=new ObjectMapper();
    private final ConcurrentMap<String,Peer> connections=new ConcurrentHashMap<>();

    private static final class Peer {
        final ConcurrentWebSocketSessionDecorator socket;
        final String sessionHash;
        final ThreadPoolExecutor sends;
        final java.util.concurrent.atomic.AtomicInteger queuedBytes=new java.util.concurrent.atomic.AtomicInteger();
        volatile long room;
        long screenAt, audioAt, chatAt;
        int audioBurst;
        Peer(WebSocketSession raw, String sessionHash) {
            this.socket=new ConcurrentWebSocketSessionDecorator(raw,2000,2*1024*1024);
            this.sessionHash=sessionHash;
            this.sends=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),
                Thread.ofVirtual().name("media-send-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
            this.sends.allowCoreThreadTimeOut(true);
        }
    }
    public MediaWebSocketHandler(ClassroomService rooms,MediaTicketService tickets){this.rooms=rooms;this.tickets=tickets;}

    @Override public synchronized void afterConnectionEstablished(WebSocketSession raw) {
        var grant=tickets.consumeGrant(queryTicket(raw));
        if(grant==null || connections.size()>=128){close(raw,CloseStatus.POLICY_VIOLATION);return;}
        raw.setBinaryMessageSizeLimit(MAX_SCREEN+1);raw.setTextMessageSizeLimit(4096);
        var peer=new Peer(raw,grant.sessionHash());connections.put(raw.getId(),peer);
        send(peer,text(Map.of("type","ready")));
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) {
        Peer peer=connections.get(raw.getId());if(peer==null)return;
        if(message.getPayloadLength()>4096){disconnect(peer);return;}
        try {
            var command=json.readTree(message.getPayload());
            String type=command.path("type").asText();
            synchronized(peer) {
                if(type.equals("leave")){leave(peer);return;}
                var user=tickets.current(peer.sessionHash);
                if(user==null){disconnect(peer);return;}
                if(type.equals("join")) {
                    long room=command.path("roomId").asLong(0);
                    rooms.mediaAccess(user,room);
                    if(peer.room==room)return;
                    leave(peer);peer.room=room;
                    send(peer,text(Map.of("type","joined","roomId",room)));
                    broadcast(room,Map.of("type","presence","username",user.username(),"state","joined"));
                    return;
                }
                if(peer.room==0||!valid(peer))return;
                if(type.equals("chat")) {
                    String content=command.path("text").asText("").strip();
                    long now=System.nanoTime();
                    if(content.isEmpty()||content.length()>500||now-peer.chatAt<200_000_000L)return;
                    peer.chatAt=now;broadcast(peer.room,Map.of("type","chat","username",user.username(),"text",content));
                } else if(type.equals("screen-stop") && rooms.mediaAccess(user,peer.room)) {
                    broadcast(peer.room,Map.of("type","screen-stop"));
                }
            }
        } catch(AuthService.Failure denied){disconnect(peer);}
        catch(IOException|IllegalArgumentException malformed){send(peer,text(Map.of("type","error","message","Dữ liệu không hợp lệ.")));}
        catch(java.sql.SQLException unavailable){disconnect(peer);}
    }
    @Override protected void handleBinaryMessage(WebSocketSession raw,BinaryMessage message) {
        Peer sender=connections.get(raw.getId());if(sender==null||sender.room==0)return;
        ByteBuffer input=message.getPayload().asReadOnlyBuffer();if(!input.hasRemaining())return;
        int kind=Byte.toUnsignedInt(input.get());int size=input.remaining();
        if((kind==SCREEN&&(size<1||size>MAX_SCREEN))||(kind==AUDIO&&size!=AUDIO_INPUT-1)||(kind!=SCREEN&&kind!=AUDIO))return;
        try {
            synchronized(sender) {
                if(!valid(sender))return;
                var user=tickets.current(sender.sessionHash);if(user==null){disconnect(sender);return;}
                long now=System.nanoTime();
                if(kind==SCREEN) {
                    if(!rooms.mediaAccess(user,sender.room)||now-sender.screenAt<90_000_000L)return;
                    sender.screenAt=now;
                } else {
                    if(now-sender.audioAt>1_000_000_000L){sender.audioAt=now;sender.audioBurst=0;}
                    if(++sender.audioBurst>65)return;
                }
                byte[] packet;
                if(kind==AUDIO) {
                    packet=new byte[size+9];ByteBuffer out=ByteBuffer.wrap(packet);
                    out.put((byte)AUDIO).putLong(user.id()).put(input);
                } else {packet=new byte[size+1];input.rewind();input.get(packet);}
                long room=sender.room;
                for(Peer target:connections.values())if(target!=sender&&target.room==room&&valid(target))
                    send(target,new BinaryMessage(packet));
            }
        } catch(java.sql.SQLException unavailable){disconnect(sender);}
    }
    private boolean valid(Peer peer) {
        if(peer.room==0)return false;
        try {var user=tickets.current(peer.sessionHash);if(user!=null){rooms.mediaAccess(user,peer.room);return true;}}
        catch(java.sql.SQLException|AuthService.Failure denied){ /* Revoke existing connections as well. */ }
        disconnect(peer);return false;
    }
    private void send(Peer peer,WebSocketMessage<?> message) {
        if(!peer.socket.isOpen())return;
        int bytes=message.getPayloadLength();
        if(peer.queuedBytes.addAndGet(bytes)>2*1024*1024){peer.queuedBytes.addAndGet(-bytes);disconnect(peer);return;}
        try {peer.sends.execute(()->{
            try {if(peer.socket.isOpen())peer.socket.sendMessage(message);}
            catch(IOException|RuntimeException failure){disconnect(peer);}
            finally{peer.queuedBytes.addAndGet(-bytes);}
        });} catch(RejectedExecutionException slow){peer.queuedBytes.addAndGet(-bytes);disconnect(peer);}
    }
    private TextMessage text(Map<String,?> value) {
        try {return new TextMessage(json.writeValueAsString(value));}
        catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private void broadcast(long room,Map<String,?> event) {
        var message=text(event);
        for(Peer peer:connections.values())if(peer.room==room&&valid(peer))send(peer,message);
    }
    private void leave(Peer peer) {
        long room=peer.room;peer.room=0;if(room==0)return;
        var user=tickets.current(peer.sessionHash);
        broadcast(room,Map.of("type","presence","username",user==null?"Thành viên":user.username(),"state","left"));
        try {if(user!=null&&rooms.mediaAccess(user,room))broadcast(room,Map.of("type","screen-stop"));}
        catch(java.sql.SQLException|AuthService.Failure ignored){}
    }
    private void disconnect(Peer peer) {
        if(!connections.remove(peer.socket.getId(),peer))return;
        leave(peer);peer.sends.shutdownNow();close(peer.socket,CloseStatus.POLICY_VIOLATION);
    }
    @Scheduled(fixedDelay=1000) public void sweep() {
        for(Peer peer:connections.values())if(peer.room!=0)valid(peer);
        else if(tickets.current(peer.sessionHash)==null)disconnect(peer);
    }
    @Override public void afterConnectionClosed(WebSocketSession raw,CloseStatus status){Peer peer=connections.get(raw.getId());if(peer!=null)disconnect(peer);}
    @Override public void handleTransportError(WebSocketSession raw,Throwable failure){Peer peer=connections.get(raw.getId());if(peer!=null)disconnect(peer);}
    @PreDestroy public void stop(){for(Peer peer:connections.values())disconnect(peer);}
    private static String queryTicket(WebSocketSession s){String query=s.getUri()==null?null:s.getUri().getQuery();if(query==null)return "";for(String field:query.split("&"))if(field.startsWith("ticket="))return field.substring(7);return "";}
    private static void close(WebSocketSession s,CloseStatus status){try{s.close(status);}catch(IOException ignored){}}
}
