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
    private final ConcurrentMap<Long,MediaControls> controls=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sourceIds=new java.util.concurrent.atomic.AtomicLong();
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MediaWebSocketHandler.class);
    private final ConcurrentMap<String,Peer> connections=new ConcurrentHashMap<>();

    private static final class Peer {
        final ConcurrentWebSocketSessionDecorator socket;
        final String sessionHash;
        long sourceId; String username; boolean owner;
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
        var peer=new Peer(raw,grant.sessionHash());peer.sourceId=sourceIds.incrementAndGet();peer.username=grant.user().username();connections.put(raw.getId(),peer);
        send(peer,text(Map.of("type","ready")));
    }
    @Override protected synchronized void handleTextMessage(WebSocketSession raw,TextMessage message) {
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
                    boolean owner=rooms.mediaAccess(user,room);
                    if(peer.room==room)return;
                    leave(peer);peer.room=room;peer.owner=owner;controls.computeIfAbsent(room,k->new MediaControls());
                    send(peer,text(Map.of("type","joined","roomId",room)));
                    broadcast(room,Map.of("type","presence","username",user.username(),"state","joined"));
                    publishControls(room);
                    return;
                }
                if(peer.room==0||!valid(peer))return;
                if(control(peer,type,command))return;
                if(type.equals("ping")){send(peer,text(Map.of("type","pong")));return;}
                if(type.equals("chat")) {
                    String content=command.path("text").asText("").strip();
                    long now=System.nanoTime();
                    if(content.isEmpty()||content.length()>500||now-peer.chatAt<200_000_000L)return;
                    peer.chatAt=now;broadcast(peer.room,Map.of("type","chat","username",user.username(),"text",content));
                } else if(type.equals("screen-stop")) {
                    var state=controls.get(peer.room);
                    synchronized(state){if(state.presenter.equals(peer.socket.getId())){state.presenter="";broadcast(peer.room,Map.of("type","screen-stop"));publishControls(peer.room);}}
                }
            }
        } catch(AuthService.Failure denied){if(denied.getMessage().startsWith("Buổi học đã kết thúc"))disconnect(peer,new CloseStatus(4000,"meeting-ended"));else disconnect(peer);}
        catch(IOException|IllegalArgumentException malformed){send(peer,text(Map.of("type","error","message","Dữ liệu không hợp lệ.")));}
        catch(java.sql.SQLException unavailable){disconnect(peer);}
    }
    @Override protected synchronized void handleBinaryMessage(WebSocketSession raw,BinaryMessage message) {
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
                    var state=controls.get(sender.room);
                    synchronized(state){if(!state.presenter.equals(sender.socket.getId()))return;}
                    if(now-sender.screenAt<90_000_000L)return;
                    sender.screenAt=now;
                } else {
                    var state=controls.get(sender.room);
                    synchronized(state){if(!state.microphoneAllowed(sender.socket.getId(),sender.owner))return;}
                    if(now-sender.audioAt>1_000_000_000L){sender.audioAt=now;sender.audioBurst=0;}
                    if(++sender.audioBurst>65)return;
                }
                byte[] packet;
                if(kind==AUDIO) {
                    packet=new byte[size+9];ByteBuffer out=ByteBuffer.wrap(packet);
                    out.put((byte)AUDIO).putLong(sender.sourceId).put(input);
                } else {packet=new byte[size+1];input.rewind();input.get(packet);}
                long room=sender.room;
                for(Peer target:connections.values())if(target!=sender&&target.room==room&&valid(target))
                    send(target,new BinaryMessage(packet));
            }
        } catch(RuntimeException unavailable){log.warn("Media processing failed source={} room={} cause={}",sender.sourceId,sender.room,unavailable.getClass().getSimpleName());disconnect(sender);}
    }
    private boolean valid(Peer peer) {
        if(peer.room==0)return false;
        try {if(!rooms.isMeetingOpen(peer.room)){disconnect(peer,new CloseStatus(4000,"meeting-ended"));return false;}var user=tickets.current(peer.sessionHash);if(user!=null){rooms.mediaAccess(user,peer.room);return true;}}
        catch(java.sql.SQLException|AuthService.Failure denied){ /* Revoke existing connections as well. */ }
        disconnect(peer);return false;
    }
    private void send(Peer peer,WebSocketMessage<?> message) {
        if(!peer.socket.isOpen())return;
        int bytes=message.getPayloadLength();
        if(peer.queuedBytes.addAndGet(bytes)>2*1024*1024){peer.queuedBytes.addAndGet(-bytes);disconnect(peer,new CloseStatus(1013,"slow-recipient"));return;}
        try {peer.sends.execute(()->{
            try {if(peer.socket.isOpen())peer.socket.sendMessage(message);}
            catch(IOException|RuntimeException failure){log.warn("Media send failed source={} room={} cause={}",peer.sourceId,peer.room,failure.getClass().getSimpleName());disconnect(peer,new CloseStatus(1011,"send-failed"));}
            finally{peer.queuedBytes.addAndGet(-bytes);}
        });} catch(RejectedExecutionException slow){peer.queuedBytes.addAndGet(-bytes);disconnect(peer,new CloseStatus(1013,"slow-recipient"));}
    }
    private boolean control(Peer peer,String type,com.fasterxml.jackson.databind.JsonNode command) {
        if(!java.util.Set.of("screen-request","screen-cancel","screen-start","screen-review","screen-revoke","mic-lock","mic-request","mic-review","mic-block","screen-grant").contains(type))return false;
        var state=controls.computeIfAbsent(peer.room,k->new MediaControls());
        synchronized(state) {
            String id=peer.socket.getId(), targetId=command.path("peerId").asText();
            Peer target=connections.get(targetId);
            if(type.equals("screen-request")){if(!peer.owner&&!state.presenter.equals(id))state.screenRequests.add(id);}
            else if(type.equals("screen-cancel")){state.screenRequests.remove(id);}
            else if(type.equals("screen-start")){if(peer.owner||state.presenter.equals(id)){if(!state.presenter.equals(id))broadcast(peer.room,Map.of("type","screen-stop"));state.presenter=id;}}
            else if(type.equals("mic-request")){if(!state.microphoneAllowed(id,peer.owner))state.microphoneRequests.add(id);}
            else {
                if(!peer.owner){send(peer,text(Map.of("type","error","message","Chỉ người dạy được quản lý quyền media.")));return true;}
                if(type.equals("mic-lock")){state.microphonesLocked=command.path("locked").asBoolean();state.microphoneGrants.clear();state.microphoneBlocks.clear();state.microphoneRequests.clear();}
                else if(type.equals("screen-revoke")){state.presenter="";broadcast(peer.room,Map.of("type","screen-stop"));}
                else if(target!=null&&target.room==peer.room&&!target.owner){
                    boolean approve=command.path("approve").asBoolean();
                    if(type.equals("mic-block")){if(command.path("blocked").asBoolean()){state.microphoneBlocks.add(targetId);state.microphoneGrants.remove(targetId);}else state.microphoneBlocks.remove(targetId);}
                    else if(type.equals("screen-grant")){broadcast(peer.room,Map.of("type","screen-stop"));state.presenter=targetId;state.screenRequests.remove(targetId);send(target,text(Map.of("type","notice","message","Chủ lớp đã mời bạn trình bày. Bấm Chia sẻ bài giảng để chọn nguồn.")));}
                    else if(type.equals("screen-review")){if(state.screenRequests.remove(targetId)&&approve){broadcast(peer.room,Map.of("type","screen-stop"));state.presenter=targetId;}}
                    else if(type.equals("mic-review")){state.microphoneRequests.remove(targetId);if(approve){state.microphoneBlocks.remove(targetId);state.microphoneGrants.add(targetId);}else state.microphoneGrants.remove(targetId);}
                }
            }
            publishControls(peer.room);return true;
        }
    }
    private void publishControls(long room) {
        var state=controls.get(room);if(state==null)return;
        synchronized(state){
            var participants=connections.values().stream().filter(p->p.room==room).map(p->Map.of(
                "peerId",p.socket.getId(),"sourceId",Long.toString(p.sourceId),"username",p.username,"owner",p.owner,
                "micAllowed",state.microphoneAllowed(p.socket.getId(),p.owner),"screenRequested",state.screenRequests.contains(p.socket.getId()),
                "micRequested",state.microphoneRequests.contains(p.socket.getId()),"micBlocked",state.microphoneBlocks.contains(p.socket.getId()))).toList();
            for(Peer peer:connections.values())if(peer.room==room)send(peer,text(Map.of("type","controls","peerId",peer.socket.getId(),
                "presenter",state.presenter,"microphonesLocked",state.microphonesLocked,"participants",participants)));
        }
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
        var state=controls.get(room);
        if(state!=null){synchronized(state){boolean presenting=state.presenter.equals(peer.socket.getId());state.remove(peer.socket.getId());if(presenting)broadcast(room,Map.of("type","screen-stop"));publishControls(room);}
            if(connections.values().stream().noneMatch(p->p.room==room))controls.remove(room,state);}
        var user=tickets.current(peer.sessionHash);
        broadcast(room,Map.of("type","presence","username",user==null?"Thành viên":user.username(),"state","left"));
    }
    private synchronized void disconnect(Peer peer) {
        disconnect(peer,new CloseStatus(1008,"session-or-membership-revoked"));
    }
    private synchronized void disconnect(Peer peer,CloseStatus status) {
        if(!connections.remove(peer.socket.getId(),peer))return;
        log.info("Media closed source={} room={} code={} reason={}",peer.sourceId,peer.room,status.getCode(),status.getReason());
        leave(peer);peer.sends.shutdownNow();close(peer.socket,status);
    }
    public synchronized void endMeeting(long room){for(Peer peer:connections.values())if(peer.room==room)disconnect(peer,new CloseStatus(4000,"meeting-ended"));controls.remove(room);}
    @Scheduled(fixedDelay=1000) public synchronized void sweep() {
        for(Peer peer:connections.values())if(peer.room!=0)valid(peer);
        else if(tickets.current(peer.sessionHash)==null)disconnect(peer);
    }
    @Override public void afterConnectionClosed(WebSocketSession raw,CloseStatus status){Peer peer=connections.get(raw.getId());if(peer!=null)disconnect(peer,status);}
    @Override public void handleTransportError(WebSocketSession raw,Throwable failure){Peer peer=connections.get(raw.getId());if(peer!=null){log.warn("Media transport error source={} cause={}",peer.sourceId,failure.getClass().getSimpleName());disconnect(peer,new CloseStatus(1011,"transport-error"));}}
    @PreDestroy public void stop(){for(Peer peer:connections.values())disconnect(peer);}
    private static String queryTicket(WebSocketSession s){String query=s.getUri()==null?null:s.getUri().getQuery();if(query==null)return "";for(String field:query.split("&"))if(field.startsWith("ticket="))return field.substring(7);return "";}
    private static void close(WebSocketSession s,CloseStatus status){try{s.close(status);}catch(IOException ignored){}}
}
