package com.pbl4.web;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeetingLifecycleTest {
    @Test void newClassStartsClosedUntilOwnerStartsMeeting() throws Exception {
        String path=Path.of("target","meeting-default-closed-"+UUID.randomUUID(),"db.sqlite").toString();
        var db=new Database(path);
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");var room=rooms.create(owner,"Scheduled class");
            assertFalse(room.meetingOpen());
            assertFalse(rooms.isMeetingOpen(room.id()));
            assertTrue(rooms.history(owner,room.id()).isEmpty());
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(owner,room.id()));
            auth.register(new AuthService.Registration("member","Member","Strong!23",null));
            var member=db.findAccount("member");rooms.add(owner,room.id(),"member");
            assertThrows(AuthService.Failure.class,()->rooms.setMeeting(member,room.id(),true));
            assertThrows(AuthService.Failure.class,()->rooms.joinHistory(member,room.id()));
            rooms.setMeeting(owner,room.id(),true);
            assertTrue(rooms.isMeetingOpen(room.id()));
            long first=rooms.joinHistory(member,room.id());rooms.leaveHistory(first);
            assertTrue(rooms.isMeetingOpen(room.id()));
            long second=rooms.joinHistory(member,room.id());assertNotEquals(first,second);
            assertEquals(1,rooms.history(owner,room.id()).size());
        } finally { db.close(); }
    }

    @Test void missingMeetingStateDoesNotGrantMediaAccess() throws Exception {
        var db=new Database(Path.of("target","meeting-missing-"+UUID.randomUUID(),"db.sqlite").toString());
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");long id=rooms.create(owner,"Class").id();
            db.transaction(c->{try(var s=c.prepareStatement("DELETE FROM classroom_meetings WHERE classroom_id=?")){s.setLong(1,id);s.executeUpdate();}return null;});
            assertFalse(rooms.isMeetingOpen(id));
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(owner,id));
            rooms.setMeeting(owner,id,true);assertTrue(rooms.mediaAccess(owner,id));
        } finally {db.close();}
    }

    @Test void migrationClosesOldMeetingsWithoutDuplicatingHistory() throws Exception {
        String path=Path.of("target","meeting-migration-"+UUID.randomUUID(),"db.sqlite").toString(); long openId,closedId;
        var db=new Database(path);
        try {
            var auth=new AuthService(db); var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner"); openId=rooms.create(owner,"Open class").id(); closedId=rooms.create(owner,"Closed class").id();
            rooms.setMeeting(owner,openId,true);
            rooms.setMeeting(owner,closedId,false);
            db.transaction(c->{try(var s=c.createStatement()){s.execute("DROP TABLE meeting_microphone_blocks");s.execute("DROP TABLE meeting_connections");s.execute("DROP TABLE meeting_sessions");s.execute("DELETE FROM schema_versions WHERE version IN (3,4,5)");}return null;});
        } finally {db.close();}
        for(int i=0;i<2;i++) {
            var reopened=new Database(path);
            try {
                var rooms=new ClassroomService(reopened); assertFalse(rooms.isMeetingOpen(openId)); assertFalse(rooms.isMeetingOpen(closedId));
                reopened.transaction(c->{try(var s=c.prepareStatement("SELECT classroom_id,ended_at FROM meeting_sessions")){try(var r=s.executeQuery()){
                    assertTrue(r.next());assertEquals(openId,r.getLong(1));assertNotNull(r.getObject(2));assertFalse(r.next());}}return null;});
            } finally {reopened.close();}
        }
    }
    @Test void connectionHistoryIsOwnerOnlyAndInterruptedConnectionsHaveNoInventedLeaveTime() throws Exception {
        String path=Path.of("target","attendance-"+UUID.randomUUID(),"db.sqlite").toString(); long id, first;
        var db=new Database(path);
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));auth.register(new AuthService.Registration("member","Member","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");var member=db.findAccount("member");id=rooms.create(owner,"Class").id();rooms.add(owner,id,"member");rooms.setMeeting(owner,id,true);
            first=rooms.joinHistory(member,id);rooms.joinHistory(member,id);rooms.leaveHistory(first);
            assertTrue(rooms.history(member,id).get(0).connections().isEmpty());
            assertEquals(2,rooms.history(owner,id).get(0).connections().size());
        } finally {db.close();}
        var reopened=new Database(path);
        try {
            var rooms=new ClassroomService(reopened);var connections=rooms.history(reopened.findAccount("owner"),id).get(0).connections();
            assertEquals(1,connections.stream().filter(c->c.leftAt()!=null).count());
            assertEquals(1,connections.stream().filter(c->c.interrupted() && c.leftAt()==null).count());
            rooms.setMeeting(reopened.findAccount("owner"),id,false);
            assertThrows(AuthService.Failure.class,()->rooms.joinHistory(reopened.findAccount("member"),id));
        } finally {reopened.close();}
    }
    @Test void meetingHistoryRecordsTransitionsAndSurvivesRestart() throws Exception {
        String path=Path.of("target","meeting-history-"+UUID.randomUUID(),"db.sqlite").toString(); long id;
        var db=new Database(path);
        try {
            var auth=new AuthService(db); var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner"); id=rooms.create(owner,"History class").id();
            rooms.setMeeting(owner,id,true);
            rooms.setMeeting(owner,id,false); rooms.setMeeting(owner,id,false);
            rooms.setMeeting(owner,id,true); rooms.setMeeting(owner,id,true);
            db.transaction(c->{try(var s=c.prepareStatement("SELECT started_at,ended_at FROM meeting_sessions WHERE classroom_id=? ORDER BY id")){
                s.setLong(1,id);try(var r=s.executeQuery()){
                    assertTrue(r.next()); assertTrue(r.getLong(1)>0); assertTrue(r.getLong(2)>0);
                    assertTrue(r.next()); assertTrue(r.getLong(1)>0); assertNull(r.getObject(2)); assertFalse(r.next());
                }}return null;});
        } finally { db.close(); }
        var reopened=new Database(path);
        try { reopened.transaction(c->{try(var s=c.prepareStatement("SELECT count(*) FROM meeting_sessions WHERE classroom_id=?")){
            s.setLong(1,id);try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(2,r.getInt(1));}}return null;}); }
        finally { reopened.close(); }
    }
    @Test void endingPersistsWithoutRemovingClassOrMembers()throws Exception {
        String path=Path.of("target","meeting-"+UUID.randomUUID(),"db.sqlite").toString();long id;
        var db=new Database(path);try{var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));auth.register(new AuthService.Registration("member","Member","Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");var member=db.findAccount("member");var room=rooms.create(owner,"Online class");id=room.id();rooms.add(owner,id,"member");
            assertThrows(AuthService.Failure.class,()->rooms.setMeeting(member,room.id(),false));
            rooms.setMeeting(owner,id,true);rooms.setMeeting(owner,id,false);assertFalse(rooms.detail(member,id).meetingOpen());assertEquals(2,rooms.detail(member,id).members().size());
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(member,room.id()));assertEquals(1,db.listClassrooms(member.id()).size());
        }finally{db.close();}
        var reopened=new Database(path);try{var rooms=new ClassroomService(reopened);var owner=reopened.findAccount("owner");assertFalse(rooms.isMeetingOpen(id));rooms.setMeeting(owner,id,true);assertTrue(rooms.mediaAccess(owner,id));assertFalse(rooms.mediaAccess(reopened.findAccount("member"),id));}finally{reopened.close();}
    }
}
