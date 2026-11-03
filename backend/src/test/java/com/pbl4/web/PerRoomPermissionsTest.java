package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerRoomPermissionsTest {
    @Test void legacyRolesDoNotGrantPermissionsInOtherClassesAndDataSurvivesRestart() throws Exception {
        String path=Path.of("target","roles-"+UUID.randomUUID(),"roles.db").toString();
        long roomId, aliceId;
        var db=new Database(path);try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String name:new String[]{"alice","bobby","outsider"})auth.register(new AuthService.Registration(name,name,"Strong!23",null));
            db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE accounts SET role='TEACHER' WHERE username='bobby'");}return null;});
            var alice=db.findAccount("alice");var bob=db.findAccount("bobby");var outsider=db.findAccount("outsider");
            var a=rooms.create(alice,"Alice teaches");var b=rooms.create(bob,"Bob teaches");roomId=a.id();aliceId=alice.id();
            rooms.requestJoin(bob,a.inviteCode());rooms.review(alice,a.id(),bob.id(),true);
            rooms.add(bob,b.id(),"alice");
            assertTrue(rooms.mediaAccess(alice,a.id()));assertFalse(rooms.mediaAccess(bob,a.id()));
            assertTrue(rooms.mediaAccess(bob,b.id()));assertFalse(rooms.mediaAccess(alice,b.id()));
            assertEquals("MEMBER",rooms.detail(alice,b.id()).members().stream().filter(m->m.id()==alice.id()).findFirst().orElseThrow().role());
            assertEquals("OWNER",rooms.detail(alice,a.id()).members().stream().filter(m->m.id()==alice.id()).findFirst().orElseThrow().role());
            assertTrue(db.listClassrooms(alice.id()).stream().filter(r->r.id()==a.id()).findFirst().orElseThrow().owned());
            assertThrows(AuthService.Failure.class,()->rooms.add(bob,a.id(),"outsider"));
            assertThrows(AuthService.Failure.class,()->rooms.requests(bob,a.id()));
            assertThrows(AuthService.Failure.class,()->rooms.review(bob,a.id(),outsider.id(),true));
            assertThrows(AuthService.Failure.class,()->rooms.remove(bob,a.id(),alice.id()));
            assertThrows(AuthService.Failure.class,()->rooms.archive(bob,a.id()));
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(outsider,a.id()));
            assertThrows(AuthService.Failure.class,()->rooms.requestJoin(alice,a.inviteCode()));
            assertThrows(AuthService.Failure.class,()->rooms.add(alice,a.id(),"alice"));
            assertThrows(AuthService.Failure.class,()->rooms.leave(alice,a.id()));
            assertThrows(AuthService.Failure.class,()->rooms.remove(alice,a.id(),alice.id()));
            rooms.remove(alice,a.id(),bob.id());assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(bob,a.id()));
            assertTrue(rooms.mediaAccess(bob,b.id()));
        } finally {db.close();}
        var reopened=new Database(path);try {
            var rooms=new ClassroomService(reopened);var alice=reopened.findAccount("alice");
            assertEquals(aliceId,alice.id());assertTrue(rooms.detail(alice,roomId).owned());
            assertTrue(rooms.mediaAccess(alice,roomId));
            assertTrue(new AuthService(reopened).login(new AuthService.Login("alice","Strong!23")).user().role().equals("USER"));
        } finally {reopened.close();}
    }
}
