package com.pbl4.web;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomTest {
    private Path temporary() { return Path.of("target", "test-db-" + UUID.randomUUID()); }
    @Test void ownershipApprovalAndConcurrentMembership()throws Exception {
        var db=new Database(temporary().resolve("rooms.db").toString());
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String username:new String[]{"teacher","other","student"})auth.register(new AuthService.Registration(username,username,"Strong!23",null));
            db.transaction(c->{try(var s=c.createStatement()){s.executeUpdate("UPDATE accounts SET role='TEACHER' WHERE username IN ('teacher','other')");}return null;});
            var teacher=db.findAccount("teacher");var other=db.findAccount("other");var student=db.findAccount("student");
            var studentOwned=rooms.create(student,"Lớp của tài khoản cũ STUDENT");
            assertTrue(rooms.mediaAccess(student,studentOwned.id()));
            rooms.archive(student,studentOwned.id());
            var room=rooms.create(teacher,"Toán ứng dụng");
            assertThrows(AuthService.Failure.class,()->rooms.detail(student,room.id()));
            assertThrows(AuthService.Failure.class,()->rooms.add(other,room.id(),"student"));
            rooms.requestJoin(student,room.inviteCode());rooms.requestJoin(student,room.inviteCode());
            assertEquals("PENDING",rooms.myRequests(student).getFirst().status());
            assertTrue(rooms.myRequests(other).isEmpty());
            assertEquals(1,rooms.requests(teacher,room.id()).size());
            assertThrows(AuthService.Failure.class,()->rooms.review(other,room.id(),student.id(),true));
            rooms.review(teacher,room.id(),student.id(),true);
            assertEquals("APPROVED",rooms.myRequests(student).getFirst().status());
            assertNull(rooms.detail(student,room.id()).inviteCode());
            rooms.remove(teacher,room.id(),student.id());
            assertThrows(AuthService.Failure.class,()->rooms.detail(student,room.id()));
            try(var pool=Executors.newFixedThreadPool(8)) {
                var work=new java.util.ArrayList<Future<?>>();
                for(int i=0;i<20;i++)work.add(pool.submit(()->{try{rooms.add(teacher,room.id(),"student");}catch(Exception e){throw new RuntimeException(e);}}));
                for(var future:work)future.get(10,TimeUnit.SECONDS);
            }
            assertEquals(2,rooms.detail(teacher,room.id()).members().size());
            rooms.leave(student,room.id());
            rooms.requestJoin(student,room.inviteCode());
            rooms.review(teacher,room.id(),student.id(),false);
            assertEquals("REJECTED",rooms.myRequests(student).getFirst().status());
            assertThrows(AuthService.Failure.class,()->rooms.detail(student,room.id()));
            rooms.archive(teacher,room.id());
            assertTrue(rooms.myRequests(student).isEmpty());
            assertTrue(db.listClassrooms(teacher.id()).isEmpty());
            assertThrows(AuthService.Failure.class,()->rooms.add(teacher,room.id(),"student"));
        } finally {db.close();}
    }
}
