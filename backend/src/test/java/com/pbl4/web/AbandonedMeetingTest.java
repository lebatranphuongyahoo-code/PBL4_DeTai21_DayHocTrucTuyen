package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbandonedMeetingTest {
    @Test void restartClosesOpenMeetingWithoutDeletingConnectionsOrMembers() throws Exception {
        String path=Path.of("target","restart-close-"+UUID.randomUUID()+".db").toString();long id;
        {
            var db=new Database(path);
            try {
                var auth=new AuthService(db);var rooms=new ClassroomService(db);
                auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
                var owner=TestAccounts.asTeacher(db,"owner");
                id=rooms.create(owner,"Class").id();rooms.setMeeting(owner,id,true);
                rooms.joinHistory(owner,id);
            } finally {db.close();}
        }
        var reopened=new Database(path);
        try {
            var rooms=new ClassroomService(reopened);var owner=reopened.findAccount("owner");
            assertFalse(rooms.isMeetingOpen(id));
            var history=rooms.history(owner,id);assertEquals(1,history.size());assertNotNull(history.get(0).endedAt());
            assertEquals(1,history.get(0).connections().size());assertTrue(history.get(0).connections().get(0).interrupted());
        } finally {reopened.close();}
    }
    @Test void ownerHeartbeatAllowsReconnectButExpiresAtTenMinutes() throws Exception {
        var db=new Database(Path.of("target","lease-"+UUID.randomUUID()+".db").toString());
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
            auth.register(new AuthService.Registration("member","Member","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");long id=rooms.create(owner,"Class").id();rooms.add(owner,id,"member");rooms.setMeeting(owner,id,true);
            assertThrows(AuthService.Failure.class,()->rooms.ownerHeartbeat(db.findAccount("member"),id));
            db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE meeting_sessions SET owner_seen_at=1000000");}return null;});
            assertTrue(rooms.closeAbandonedMeetings(1_599_999).isEmpty());
            assertEquals(java.util.List.of(id),rooms.closeAbandonedMeetings(1_600_000));
            assertFalse(rooms.isMeetingOpen(id));assertEquals("OWNER_TIMEOUT",rooms.history(owner,id).get(0).endReason());
            rooms.setMeeting(owner,id,true);rooms.ownerHeartbeat(owner,id);
            assertTrue(rooms.closeAbandonedMeetings(System.currentTimeMillis()+590_000).isEmpty());
        } finally {db.close();}
    }
}
