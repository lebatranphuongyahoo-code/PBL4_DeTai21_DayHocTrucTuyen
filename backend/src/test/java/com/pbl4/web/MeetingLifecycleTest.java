package com.pbl4.web;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeetingLifecycleTest {
    @Test void endingPersistsWithoutRemovingClassOrMembers()throws Exception {
        String path=Path.of("target","meeting-"+UUID.randomUUID(),"db.sqlite").toString();long id;
        var db=new Database(path);try{var auth=new AuthService(db);var rooms=new ClassroomService(db);
            auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));auth.register(new AuthService.Registration("member","Member","Strong!23",null));
            var owner=db.findAccount("owner");var member=db.findAccount("member");var room=rooms.create(owner,"Online class");id=room.id();rooms.add(owner,id,"member");
            assertThrows(AuthService.Failure.class,()->rooms.setMeeting(member,room.id(),false));
            rooms.setMeeting(owner,id,false);assertFalse(rooms.detail(member,id).meetingOpen());assertEquals(2,rooms.detail(member,id).members().size());
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(member,room.id()));assertEquals(1,db.listClassrooms(member.id()).size());
        }finally{db.close();}
        var reopened=new Database(path);try{var rooms=new ClassroomService(reopened);var owner=reopened.findAccount("owner");assertFalse(rooms.isMeetingOpen(id));rooms.setMeeting(owner,id,true);assertTrue(rooms.mediaAccess(owner,id));assertFalse(rooms.mediaAccess(reopened.findAccount("member"),id));}finally{reopened.close();}
    }
}
