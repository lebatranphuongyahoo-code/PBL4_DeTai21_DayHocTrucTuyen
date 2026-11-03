package com.pbl4.web;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class MediaRelayTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.database",()->Path.of("target","relay-"+UUID.randomUUID()+".db").toString());
    }
    @LocalServerPort int port;
    @Autowired Database db;
    @Autowired AuthService auth;
    @Autowired ClassroomService rooms;
    @Autowired MediaTicketService tickets;
    private static class Listener implements WebSocket.Listener {
        final BlockingQueue<String> texts=new LinkedBlockingQueue<>();
        final BlockingQueue<byte[]> packets=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        final StringBuilder text=new StringBuilder();
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){text.append(data);if(last){texts.add(text.toString());text.setLength(0);}ws.request(1);return null;}
        public CompletionStage<?> onBinary(WebSocket ws,ByteBuffer data,boolean last){byte[] chunk=new byte[data.remaining()];data.get(chunk);bytes.writeBytes(chunk);if(last){packets.add(bytes.toByteArray());bytes.reset();}ws.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket ws,int status,String reason){closed.complete(status);return null;}
        void joined() throws Exception {for(int i=0;i<8;i++){String message=texts.poll(3,TimeUnit.SECONDS);assertNotNull(message);if(message.contains("joined"))return;}fail("join not acknowledged");}
        com.fasterxml.jackson.databind.JsonNode controls() throws Exception {
            for(int i=0;i<30;i++){String message=texts.poll(3,TimeUnit.SECONDS);assertNotNull(message);var event=new com.fasterxml.jackson.databind.ObjectMapper().readTree(message);if(event.path("type").asText().equals("controls"))return event;}
            throw new AssertionError("controls missing");
        }
    }
    private WebSocket connect(HttpClient client,String token,Listener listener)throws Exception {
        String ticket=tickets.issue(AuthService.tokenHash(token));
        return client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/classroom?ticket="+ticket),listener).get(5,TimeUnit.SECONDS);
    }
    @Test void realSocketsEnforceRoomIsolationPacketsAndRevocation() throws Exception {
        for(String name:List.of("teacher","learner","outsider"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE accounts SET role='TEACHER' WHERE username='learner'");}return null;});
        var teacher=db.findAccount("teacher");var student=db.findAccount("learner");
        var first=rooms.create(teacher,"First class");var second=rooms.create(student,"Other class");
        rooms.add(teacher,first.id(),"learner");rooms.add(student,second.id(),"outsider");
        var teacherSession=auth.login(new AuthService.Login("teacher","Strong!23"));
        var studentSession=auth.login(new AuthService.Login("learner","Strong!23"));
        var otherSession=auth.login(new AuthService.Login("outsider","Strong!23"));
        try(var client=HttpClient.newHttpClient()) {
            Listener tl=new Listener(),sl=new Listener(),ol=new Listener();
            var tw=connect(client,teacherSession.token(),tl);var sw=connect(client,studentSession.token(),sl);var ow=connect(client,otherSession.token(),ol);
            try {
                tw.sendText("{\"type\":\"join\",\"roomId\":"+first.id()+"}",true).join();tl.joined();
                sw.sendText("{\"type\":\"join\",\"roomId\":"+first.id()+"}",true).join();sl.joined();
                ow.sendText("{\"type\":\"join\",\"roomId\":"+second.id()+"}",true).join();ol.joined();
                tw.sendText("{\"type\":\"screen-start\"}",true).join();
                byte[] screen={1,12,34,56};tw.sendBinary(ByteBuffer.wrap(screen),true).join();
                assertArrayEquals(screen,sl.packets.poll(3,TimeUnit.SECONDS));assertNull(ol.packets.poll(150,TimeUnit.MILLISECONDS));assertNull(tl.packets.poll());
                sw.sendBinary(ByteBuffer.wrap(screen),true).join();assertNull(tl.packets.poll(150,TimeUnit.MILLISECONDS));
                byte[] audio=new byte[645];ByteBuffer.wrap(audio).put((byte)2).putInt(42).putShort((short)123);
                sw.sendBinary(ByteBuffer.wrap(audio),true).join();
                byte[] received=tl.packets.poll(3,TimeUnit.SECONDS);assertNotNull(received);assertEquals(653,received.length);
                ByteBuffer decoded=ByteBuffer.wrap(received);assertEquals(2,decoded.get());long studentSource=decoded.getLong();assertTrue(studentSource>0);assertEquals(42,decoded.getInt());
                assertArrayEquals(Arrays.copyOfRange(audio,5,audio.length),Arrays.copyOfRange(received,13,received.length));
                assertNull(ol.packets.poll());
                sw.sendBinary(ByteBuffer.wrap(new byte[]{2,0}),true).join();assertNull(tl.packets.poll(150,TimeUnit.MILLISECONDS));
                tw.sendText("{\"type\":\"chat\",\"text\":\"quote \\\" and newline\\nworks\"}",true).join();
                String chat=null;for(int i=0;i<8;i++){String msg=sl.texts.poll(2,TimeUnit.SECONDS);if(msg!=null&&msg.contains("\"chat\"")){chat=msg;break;}}
                assertNotNull(chat);assertEquals("quote \" and newline\nworks",new com.fasterxml.jackson.databind.ObjectMapper().readTree(chat).get("text").asText());
                // Two receive threads send concurrently to the same recipient.
                sl.texts.clear();tl.texts.clear();
                sw.sendText("{\"type\":\"screen-request\"}",true).join();
                var requested=sl.controls();String studentPeer=requested.path("peerId").asText();
                tw.sendText("{\"type\":\"screen-review\",\"peerId\":\""+studentPeer+"\",\"approve\":true}",true).join();
                assertEquals(studentPeer,sl.controls().path("presenter").asText());
                sw.sendBinary(ByteBuffer.wrap(screen),true).join();assertArrayEquals(screen,tl.packets.poll(3,TimeUnit.SECONDS));
                tw.sendText("{\"type\":\"screen-revoke\"}",true).join();sl.controls();
                sw.sendBinary(ByteBuffer.wrap(screen),true).join();assertNull(tl.packets.poll(150,TimeUnit.MILLISECONDS));
                tw.sendText("{\"type\":\"mic-lock\",\"locked\":true}",true).join();assertTrue(sl.controls().path("microphonesLocked").asBoolean());
                sw.sendBinary(ByteBuffer.wrap(audio),true).join();assertNull(tl.packets.poll(150,TimeUnit.MILLISECONDS));
                sw.sendText("{\"type\":\"mic-lock\",\"locked\":false}",true).join();
                sw.sendText("{\"type\":\"mic-request\"}",true).join();assertTrue(sl.controls().path("microphonesLocked").asBoolean());
                tw.sendText("{\"type\":\"mic-review\",\"peerId\":\""+studentPeer+"\",\"approve\":true}",true).join();sl.controls();
                sw.sendBinary(ByteBuffer.wrap(audio),true).join();assertNotNull(tl.packets.poll(3,TimeUnit.SECONDS));
                tw.sendText("{\"type\":\"mic-lock\",\"locked\":false}",true).join();sl.controls();
                rooms.add(teacher,first.id(),"outsider");ol.texts.clear();
                ow.sendText("{\"type\":\"join\",\"roomId\":"+first.id()+"}",true).join();ol.joined();
                try(var workers=Executors.newFixedThreadPool(2)) {
                    var a=workers.submit(()->{for(int i=0;i<10;i++)tw.sendBinary(ByteBuffer.wrap(audio),true).join();});
                    var b=workers.submit(()->{for(int i=0;i<10;i++)sw.sendBinary(ByteBuffer.wrap(audio),true).join();});
                    a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
                }
                var senders=new HashSet<Long>();
                for(int i=0;i<20;i++){byte[] packet=ol.packets.poll(3,TimeUnit.SECONDS);assertNotNull(packet);assertEquals(653,packet.length);senders.add(ByteBuffer.wrap(packet).getLong(1));}
                assertEquals(2,senders.size());assertTrue(senders.contains(studentSource));
                rooms.remove(teacher,first.id(),student.id());assertEquals(1008,sl.closed.get(4,TimeUnit.SECONDS));
                db.deleteSession(AuthService.tokenHash(otherSession.token()));assertEquals(1008,ol.closed.get(4,TimeUnit.SECONDS));
                rooms.archive(teacher,first.id());assertEquals(1008,tl.closed.get(4,TimeUnit.SECONDS));
            } finally {tw.abort();sw.abort();ow.abort();}
        }
    }
}
