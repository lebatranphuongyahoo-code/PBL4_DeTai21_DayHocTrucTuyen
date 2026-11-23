package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeetingChatTest {
    @Test void chatIsIsolatedByMeetingAndHistoryRemainsReadableWithoutAttendanceData() throws Exception {
        var db=new Database(Path.of("target","meeting-chat-"+UUID.randomUUID()+".db").toString());
        try{
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String name:java.util.List.of("owner","member","outside"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
            var owner=db.findAccount("owner");var member=db.findAccount("member");var outside=db.findAccount("outside");long id=rooms.create(owner,"Class").id();rooms.add(owner,id,"member");
            rooms.setMeeting(owner,id,true);long first=rooms.history(owner,id).get(0).id();rooms.joinHistory(member,id);
            for(int i=0;i<55;i++)rooms.postMessage(member,id,"First "+i);
            rooms.setMeeting(owner,id,false);assertEquals(50,rooms.messages(member,id,Long.MAX_VALUE).size());
            rooms.setMeeting(owner,id,true);long second=rooms.history(owner,id).get(0).id();
            assertTrue(rooms.messages(member,id,Long.MAX_VALUE).isEmpty(),"New meeting must not replay previous chat");
            rooms.postMessage(member,id,second,"Second only");
            assertEquals("Second only",rooms.messages(member,id,second,Long.MAX_VALUE).get(0).text());
            var old=rooms.messages(member,id,first,Long.MAX_VALUE);assertEquals(50,old.size());assertEquals("First 5",old.get(0).text());assertEquals(5,rooms.messages(member,id,first,old.get(0).id()).size());
            assertThrows(AuthService.Failure.class,()->rooms.postMessage(member,id,first,"stale send"));
            assertThrows(AuthService.Failure.class,()->rooms.messages(outside,id,first,Long.MAX_VALUE));
            long other=rooms.create(owner,"Other").id();assertThrows(AuthService.Failure.class,()->rooms.messages(owner,other,first,Long.MAX_VALUE));
            var memberHistory=rooms.history(member,id);assertEquals(2,memberHistory.size());assertTrue(memberHistory.stream().allMatch(m->m.connections().isEmpty()));
            rooms.setMeeting(owner,id,false);assertThrows(AuthService.Failure.class,()->rooms.postMessage(member,id,second,"closed"));
            rooms.remove(owner,id,member.id());assertThrows(AuthService.Failure.class,()->rooms.messages(member,id,first,Long.MAX_VALUE));assertThrows(AuthService.Failure.class,()->rooms.history(member,id));
        }finally{db.close();}
    }

    @Test void migrationPreservesUnassignedOldMessagesAcrossRestarts() throws Exception {
        String path=Path.of("target","chat-migration-"+UUID.randomUUID()+".db").toString();long id;
        var db=new Database(path);
        try{
            var auth=new AuthService(db);var rooms=new ClassroomService(db);auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));var owner=db.findAccount("owner");id=rooms.create(owner,"Legacy").id();rooms.setMeeting(owner,id,true);rooms.setMeeting(owner,id,false);
            db.transaction(c->{try(var s=c.createStatement()){
                s.execute("DROP TABLE class_messages");s.execute("DELETE FROM schema_versions WHERE version=6");
                s.execute("CREATE TABLE class_messages(id INTEGER PRIMARY KEY,classroom_id INTEGER NOT NULL REFERENCES classrooms(id),account_id INTEGER NOT NULL REFERENCES accounts(id),body TEXT NOT NULL,sent_at INTEGER NOT NULL)");
                s.executeUpdate("INSERT INTO class_messages VALUES(1,"+id+","+owner.id()+",'Old preserved',123)");
            }return null;});
        }finally{db.close();}
        for(int i=0;i<2;i++){
            var reopened=new Database(path);
            try{var rooms=new ClassroomService(reopened);var owner=reopened.findAccount("owner");var legacy=rooms.messages(owner,id,0,Long.MAX_VALUE);assertEquals(1,legacy.size());assertEquals("Old preserved",legacy.get(0).text());
                rooms.setMeeting(owner,id,true);assertTrue(rooms.messages(owner,id,Long.MAX_VALUE).isEmpty());rooms.setMeeting(owner,id,false);
            }finally{reopened.close();}
        }
    }
}
