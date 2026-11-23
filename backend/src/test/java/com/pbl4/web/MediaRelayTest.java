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
    @Autowired MediaWebSocketHandler media;
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
        com.fasterxml.jackson.databind.JsonNode event(String type) throws Exception {
            for(int i=0;i<30;i++){String message=texts.poll(3,TimeUnit.SECONDS);assertNotNull(message);var event=new com.fasterxml.jackson.databind.ObjectMapper().readTree(message);if(event.path("type").asText().equals(type))return event;}
            throw new AssertionError(type+" missing");
        }
    }
    private WebSocket connect(HttpClient client,String token,Listener listener)throws Exception {
        String ticket=tickets.issue(AuthService.tokenHash(token));
        return client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/classroom?ticket="+ticket),listener).get(5,TimeUnit.SECONDS);
    }
    private WebSocket connect(HttpClient client,String token,Listener listener,String origin)throws Exception {
        String ticket=tickets.issue(AuthService.tokenHash(token));
        return client.newWebSocketBuilder().header("Origin",origin).buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/classroom?ticket="+ticket),listener).get(5,TimeUnit.SECONDS);
    }

    @Test void quickTunnelOriginCanOpenMediaSocket() throws Exception {
        auth.register(new AuthService.Registration("tunnelowner","Tunnel owner","Strong!23",null));
        var owner=db.findAccount("tunnelowner");
        var room=rooms.create(owner,"Tunnel class");
        rooms.setMeeting(owner,room.id(),true);
        var session=auth.login(new AuthService.Login("tunnelowner","Strong!23"));
        try(var client=HttpClient.newHttpClient()) {
            Listener listener=new Listener();
            var socket=connect(client,session.token(),listener,"https://powder-ultra-thomas-model.trycloudflare.com");
            try { socket.sendText("{\"type\":\"join\",\"roomId\":"+room.id()+"}",true).join(); listener.joined(); }
            finally { socket.abort(); }
        }
    }

    @Test void meetingChatDoesNotLeakIntoNewSessionAndHistoryIsMemberReadable() throws Exception {
        for(String name:List.of("sessionowner","sessionmember"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        var owner=db.findAccount("sessionowner");var member=db.findAccount("sessionmember");long id=rooms.create(owner,"Session chat").id();rooms.add(owner,id,"sessionmember");rooms.setMeeting(owner,id,true);
        long first=rooms.detail(owner,id).meetingId();var token=auth.login(new AuthService.Login("sessionmember","Strong!23")).token();String join="{\"type\":\"join\",\"roomId\":"+id+"}";
        try(var client=HttpClient.newHttpClient()){
            Listener a=new Listener();var one=connect(client,token,a);
            try{one.sendText(join,true).join();a.joined();assertTrue(a.event("chat-history").path("messages").isEmpty());one.sendText("{\"type\":\"chat\",\"text\":\"Only first meeting\"}",true).join();assertEquals(first,a.event("chat").path("meetingId").asLong());
                synchronized(media){rooms.setMeeting(owner,id,false);media.endMeeting(id);rooms.setMeeting(owner,id,true);}assertEquals(4000,a.closed.get(4,TimeUnit.SECONDS));
            }finally{one.abort();}
            Listener stale=new Listener();var outdated=connect(client,token,stale);
            try{outdated.sendText("{\"type\":\"join\",\"roomId\":"+id+",\"meetingId\":"+first+"}",true).join();assertEquals(4000,stale.closed.get(4,TimeUnit.SECONDS),"A stale tab must not join a different meeting");}finally{outdated.abort();}
            Listener b=new Listener();var two=connect(client,token,b);
            try{two.sendText(join,true).join();b.joined();var history=b.event("chat-history");assertNotEquals(first,history.path("meetingId").asLong());assertTrue(history.path("messages").isEmpty());
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/rooms/"+id+"/messages?meeting="+first)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("Only first meeting"));
                var meetings=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/rooms/"+id+"/meetings")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,meetings.statusCode());assertFalse(meetings.body().contains("sessionmember"),"Do not expose account connection history to members");
            }finally{two.abort();synchronized(media){rooms.setMeeting(owner,id,false);media.endMeeting(id);}}
        }
    }

    @Test void microphoneBlockAppliesToOtherTabsAndReconnectsUntilMeetingEnds() throws Exception {
        for(String name:List.of("blockowner","blockmember"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        var owner=db.findAccount("blockowner");long id=rooms.create(owner,"Block class").id();
        rooms.add(owner,id,"blockmember");rooms.setMeeting(owner,id,true);
        var ot=auth.login(new AuthService.Login("blockowner","Strong!23")).token();
        var mt=auth.login(new AuthService.Login("blockmember","Strong!23")).token();
        String join="{\"type\":\"join\",\"roomId\":"+id+"}";
        try(var client=HttpClient.newHttpClient()) {
            Listener ol=new Listener(),first=new Listener(),second=new Listener();
            var ow=connect(client,ot,ol);var a=connect(client,mt,first);var b=connect(client,mt,second);
            try {
                ow.sendText(join,true).join();ol.joined();a.sendText(join,true).join();first.joined();
                String peer=first.controls().path("peerId").asText();
                ow.sendText("{\"type\":\"mic-block\",\"peerId\":\""+peer+"\",\"blocked\":true}",true).join();
                var blocked=first.controls();for(var p:blocked.path("participants"))if(p.path("username").asText().equals("blockmember"))assertFalse(p.path("micAllowed").asBoolean());
                // The next connection must inherit the account block.
                b.sendText(join,true).join();second.joined();var state=second.controls();
                for(var participant:state.path("participants"))if(participant.path("username").asText().equals("blockmember"))assertFalse(participant.path("micAllowed").asBoolean());
                ol.packets.clear();b.sendBinary(ByteBuffer.wrap(new byte[645]).put(0,(byte)2),true).join();assertNull(ol.packets.poll(250,TimeUnit.MILLISECONDS));
                a.sendClose(1000,"leave").join();first.closed.get(4,TimeUnit.SECONDS);
                b.sendClose(1000,"leave").join();second.closed.get(4,TimeUnit.SECONDS);
                Listener reconnected=new Listener();var again=connect(client,mt,reconnected);
                try {
                    again.sendText(join,true).join();reconnected.joined();var next=reconnected.controls();
                    for(var p:next.path("participants"))if(p.path("username").asText().equals("blockmember"))assertTrue(p.path("micBlocked").asBoolean());
                    ow.sendText("{\"type\":\"mic-block\",\"peerId\":\""+next.path("peerId").asText()+"\",\"blocked\":false}",true).join();
                    var unlocked=reconnected.controls();for(var p:unlocked.path("participants"))if(p.path("username").asText().equals("blockmember"))assertTrue(p.path("micAllowed").asBoolean());
                } finally {again.abort();}
            } finally {ow.abort();a.abort();b.abort();rooms.setMeeting(owner,id,false);}
        }
    }

    @Test void emptyRoomKeepsMicPolicyAndNewMeetingResetsIt() throws Exception {
        for(String name:List.of("emptyowner","emptymember"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        var owner=db.findAccount("emptyowner");long id=rooms.create(owner,"Empty room").id();rooms.add(owner,id,"emptymember");rooms.setMeeting(owner,id,true);
        var ot=auth.login(new AuthService.Login("emptyowner","Strong!23")).token();var mt=auth.login(new AuthService.Login("emptymember","Strong!23")).token();
        String join="{\"type\":\"join\",\"roomId\":"+id+"}";
        try(var client=HttpClient.newHttpClient()) {
            Listener ol=new Listener();var ow=connect(client,ot,ol);
            try{ow.sendText(join,true).join();ol.joined();ol.controls();ow.sendText("{\"type\":\"mic-lock\",\"locked\":true}",true).join();assertTrue(ol.controls().path("microphonesLocked").asBoolean());ow.sendClose(1000,"leave").join();ol.closed.get(4,TimeUnit.SECONDS);}finally{ow.abort();}
            Listener ml=new Listener();var mw=connect(client,mt,ml);
            try{mw.sendText(join,true).join();ml.joined();assertTrue(ml.controls().path("microphonesLocked").asBoolean());
                synchronized(media){rooms.blockMicrophone(owner,id,"emptymember",true);rooms.setMeeting(owner,id,false);media.endMeeting(id);rooms.setMeeting(owner,id,true);}
                assertEquals(4000,ml.closed.get(4,TimeUnit.SECONDS));
            }finally{mw.abort();}
            Listener fresh=new Listener();var socket=connect(client,mt,fresh);
            try{socket.sendText(join,true).join();fresh.joined();var state=fresh.controls();assertFalse(state.path("microphonesLocked").asBoolean());assertTrue(rooms.blockedMicrophones(id).isEmpty());for(var p:state.path("participants"))assertTrue(p.path("micAllowed").asBoolean());}
            finally{socket.abort();synchronized(media){rooms.setMeeting(owner,id,false);media.endMeeting(id);}}
        }
    }

    @Test void savedChatReplaysOnReconnectAndOwnerTimeoutClosesConnectedPeers() throws Exception {
        for(String name:List.of("chatowner","chatmember"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        var owner=db.findAccount("chatowner");long id=rooms.create(owner,"Replay room").id();rooms.add(owner,id,"chatmember");rooms.setMeeting(owner,id,true);
        var token=auth.login(new AuthService.Login("chatmember","Strong!23")).token();String join="{\"type\":\"join\",\"roomId\":"+id+"}";
        try(var client=HttpClient.newHttpClient()) {
            Listener first=new Listener();var socket=connect(client,token,first);long messageId;
            try{socket.sendText(join,true).join();first.joined();assertTrue(first.event("chat-history").path("messages").isEmpty());
                socket.sendText("{\"type\":\"chat\",\"text\":\"Saved message\"}",true).join();var saved=first.event("chat");messageId=saved.path("id").asLong();assertTrue(messageId>0);assertTrue(saved.path("sentAt").asLong()>0);
                socket.sendClose(1000,"leave").join();first.closed.get(4,TimeUnit.SECONDS);
            }finally{socket.abort();}
            Listener next=new Listener();var again=connect(client,token,next);
            try{again.sendText(join,true).join();next.joined();var messages=next.event("chat-history").path("messages");assertEquals(1,messages.size());assertEquals(messageId,messages.get(0).path("id").asLong());assertEquals("Saved message",messages.get(0).path("text").asText());
                synchronized(media){db.transaction(c->{try(var s=c.prepareStatement("UPDATE meeting_sessions SET owner_seen_at=0 WHERE classroom_id=? AND ended_at IS NULL")){s.setLong(1,id);s.executeUpdate();}return null;});media.closeAbandonedMeetings();}
                assertEquals(4000,next.closed.get(4,TimeUnit.SECONDS));assertFalse(rooms.isMeetingOpen(id));assertEquals("OWNER_TIMEOUT",rooms.history(owner,id).get(0).endReason());
                var uri=URI.create("http://127.0.0.1:"+port+"/api/rooms/"+id+"/messages");var response=client.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());assertTrue(response.body().contains("Saved message"));
            }finally{again.abort();}
        }
    }

    @Test void endingMeetingClosesEveryoneAndBlocksReentryUntilOwnerRestarts() throws Exception {
        for(String name:List.of("lifeowner","lifemember"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        var owner=db.findAccount("lifeowner");long id=rooms.create(owner,"Lifecycle class").id();
        rooms.add(owner,id,"lifemember");
        var ownerSession=auth.login(new AuthService.Login("lifeowner","Strong!23"));
        var memberSession=auth.login(new AuthService.Login("lifemember","Strong!23"));
        try(var client=HttpClient.newHttpClient()) {
            String join="{\"type\":\"join\",\"roomId\":"+id+"}";
            var start=URI.create("http://127.0.0.1:"+port+"/api/rooms/"+id+"/meeting/start");
            var end=URI.create("http://127.0.0.1:"+port+"/api/rooms/"+id+"/meeting/end");
            assertEquals(403,client.send(HttpRequest.newBuilder(start).header("Authorization","Bearer "+memberSession.token()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            Listener blocked=new Listener();var bw=connect(client,memberSession.token(),blocked);
            try {bw.sendText(join,true).join();assertEquals(4000,blocked.closed.get(4,TimeUnit.SECONDS));} finally {bw.abort();}
            assertEquals(200,client.send(HttpRequest.newBuilder(start).header("Authorization","Bearer "+ownerSession.token()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            Listener ol=new Listener(),ml=new Listener();
            var ow=connect(client,ownerSession.token(),ol);var mw=connect(client,memberSession.token(),ml);
            try {
                ow.sendText(join,true).join();ol.joined();mw.sendText(join,true).join();ml.joined();
                assertEquals(403,client.send(HttpRequest.newBuilder(end).header("Authorization","Bearer "+memberSession.token()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
                assertTrue(rooms.isMeetingOpen(id));
                assertEquals(200,client.send(HttpRequest.newBuilder(end).header("Authorization","Bearer "+ownerSession.token()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(4000,ol.closed.get(4,TimeUnit.SECONDS));assertEquals(4000,ml.closed.get(4,TimeUnit.SECONDS));
            } finally {ow.abort();mw.abort();}
            Listener rejected=new Listener();var rw=connect(client,memberSession.token(),rejected);
            try {rw.sendText(join,true).join();assertEquals(4000,rejected.closed.get(4,TimeUnit.SECONDS));} finally {rw.abort();}
            assertEquals(200,client.send(HttpRequest.newBuilder(start).header("Authorization","Bearer "+ownerSession.token()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            Listener reopened=new Listener();var socket=connect(client,memberSession.token(),reopened);
            try {socket.sendText(join,true).join();reopened.joined();assertEquals(2,rooms.history(owner,id).size());} finally {socket.abort();}
            rooms.setMeeting(owner,id,false);
        }
    }

    @Test void realSocketsEnforceRoomIsolationPacketsAndRevocation() throws Exception {
        for(String name:List.of("teacher","learner","outsider"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE accounts SET role='TEACHER' WHERE username='learner'");}return null;});
        var teacher=db.findAccount("teacher");var student=db.findAccount("learner");
        var first=rooms.create(teacher,"First class");var second=rooms.create(student,"Other class");
        rooms.setMeeting(teacher,first.id(),true);rooms.setMeeting(student,second.id(),true);
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
                var history=rooms.history(teacher,first.id()).get(0).connections();
                assertEquals(3,history.size());
                assertTrue(history.stream().anyMatch(entry->entry.username().equals("learner")&&entry.leftAt()!=null));
                assertTrue(history.stream().anyMatch(entry->entry.username().equals("outsider")&&entry.leftAt()!=null));
                rooms.archive(teacher,first.id());assertEquals(1008,tl.closed.get(4,TimeUnit.SECONDS));
            } finally {tw.abort();sw.abort();ow.abort();}
        }
    }
}
