package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminPermissionsTest {
    @Test void adminManagesClassesWithoutReceivingMediaAccess() throws Exception {
        var db=new Database(Path.of("target","admin-"+UUID.randomUUID()+".db").toString());
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String name:new String[]{"admin","teacher","teacher2","student","other"})auth.register(new AuthService.Registration(name,name,"Strong!23",null));
            db.setRole("admin","ADMIN");db.setRole("teacher","TEACHER");db.setRole("teacher2","TEACHER");
            var admin=db.findAccount("admin");var teacher=db.findAccount("teacher");var teacher2=db.findAccount("teacher2");var student=db.findAccount("student");var other=db.findAccount("other");
            long source=rooms.create(teacher,"Nguồn").id(),target=rooms.create(teacher,"Đích").id();
            rooms.add(teacher,source,"student");rooms.add(teacher,source,"other");
            assertEquals(2,rooms.adminClassrooms(admin).size());
            assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(admin,source));
            var moved=rooms.transferMember(admin,source,student.id(),target);assertEquals("student",moved);
            assertThrows(AuthService.Failure.class,()->rooms.detail(student,source));assertDoesNotThrow(()->rooms.detail(student,target));
            assertThrows(AuthService.Failure.class,()->rooms.assignOwner(admin,target,"student"));
            assertTrue(rooms.assignOwner(admin,target,"teacher2"));assertEquals("teacher2",rooms.adminDetail(admin,target).ownerUsername());
            rooms.archive(admin,source);assertThrows(AuthService.Failure.class,()->rooms.detail(other,source));
            assertEquals("ARCHIVED",rooms.adminDetail(admin,source).status());
            assertTrue(rooms.adminClassrooms(admin).stream().anyMatch(room->room.id()==source&&room.status().equals("ARCHIVED")));
            rooms.restore(admin,source);assertEquals("ACTIVE",rooms.adminDetail(admin,source).status());
            assertThrows(AuthService.Failure.class,()->rooms.restore(teacher,source));
            assertThrows(AuthService.Failure.class,()->rooms.adminClassrooms(teacher));
        } finally {db.close();}
    }
}
