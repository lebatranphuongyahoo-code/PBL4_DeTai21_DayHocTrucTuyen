package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomActivityTest {
    @Test void meetingHistoryPaginatesMeetingsAndConnections() throws Exception {
        var db=new Database(Path.of("target","pages-"+UUID.randomUUID()+".db").toString());
        try{
            var auth=new AuthService(db);var rooms=new ClassroomService(db);auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));var owner=TestAccounts.asTeacher(db,"owner");long id=rooms.create(owner,"Pages").id();rooms.setMeeting(owner,id,true);long session=rooms.history(owner,id).get(0).id();
            db.transaction(c->{try(var s=c.createStatement()){
                s.executeUpdate("UPDATE meeting_sessions SET started_at=1000,ended_at=1000000 WHERE id="+session);
                for(int i=0;i<205;i++)s.executeUpdate("INSERT INTO meeting_connections(session_id,account_id,joined_at,left_at) VALUES("+session+","+owner.id()+","+(1000+i*2000)+","+(2000+i*2000)+")");
                for(int i=0;i<55;i++)s.executeUpdate("INSERT INTO meeting_sessions(classroom_id,started_at,ended_at) VALUES("+id+",1000,2000)");
            }return null;});
            var history=rooms.history(owner,id);assertEquals(50,history.size());var older=rooms.history(owner,id,history.get(49).id());assertEquals(6,older.size());assertEquals(session,older.get(5).id());assertEquals(200,older.get(5).connections().size());
        }finally{db.close();}
    }
    @Test void chatPersistsPaginatesAndRejectsClosedOrUnauthorizedSenders() throws Exception {
        String path=Path.of("target","chat-"+UUID.randomUUID()+".db").toString();long id,last;
        var db=new Database(path);
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String name:java.util.List.of("owner","member","outsider"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
            var owner=TestAccounts.asTeacher(db,"owner");var member=db.findAccount("member");var outsider=db.findAccount("outsider");
            id=rooms.create(owner,"Chat class").id();rooms.add(owner,id,"member");
            assertThrows(AuthService.Failure.class,()->rooms.postMessage(member,id,"closed"));
            rooms.setMeeting(owner,id,true);
            assertThrows(AuthService.Failure.class,()->rooms.postMessage(outsider,id,"no access"));
            assertThrows(AuthService.Failure.class,()->rooms.postMessage(member,id," "));
            assertThrows(AuthService.Failure.class,()->rooms.postMessage(member,id,"x".repeat(501)));
            for(int i=0;i<55;i++)rooms.postMessage(member,id,"Message "+i);
            var recent=rooms.messages(member,id,Long.MAX_VALUE);
            assertEquals(50,recent.size());assertEquals("Message 5",recent.get(0).text());assertEquals("Message 54",recent.get(49).text());
            last=recent.get(49).id();var older=rooms.messages(member,id,recent.get(0).id());
            assertEquals(5,older.size());assertEquals("Message 0",older.get(0).text());
            long other=rooms.create(owner,"Other class").id();assertTrue(rooms.messages(owner,other,Long.MAX_VALUE).isEmpty());
            assertThrows(AuthService.Failure.class,()->rooms.messages(outsider,id,Long.MAX_VALUE));
            rooms.remove(owner,id,member.id());assertThrows(AuthService.Failure.class,()->rooms.messages(member,id,Long.MAX_VALUE));
        } finally {db.close();}
        var reopened=new Database(path);
        try {var rooms=new ClassroomService(reopened);assertEquals(last,rooms.messages(reopened.findAccount("owner"),id,Long.MAX_VALUE).get(49).id());}
        finally {reopened.close();}
    }

    @Test void activitiesKeepTeacherAndStudentActionsSeparate() throws Exception {
        var db=new Database(Path.of("target","activities-"+UUID.randomUUID()+".db").toString());
        try {
            var auth=new AuthService(db);var rooms=new ClassroomService(db);
            for(String name:java.util.List.of("teacher","student","outside"))auth.register(new AuthService.Registration(name,name,"Strong!23",null));
            var teacher=TestAccounts.asTeacher(db,"teacher");var student=db.findAccount("student");var outside=db.findAccount("outside");
            long id=rooms.create(teacher,"Activity class").id();rooms.add(teacher,id,"student");
            var activity=rooms.createActivity(teacher,id,"Bài đọc","Giải thích ý chính trong 500 từ.");
            assertEquals(0,rooms.activities(teacher,id).getFirst().submissionCount());
            assertThrows(AuthService.Failure.class,()->rooms.createActivity(student,id,"Cấm","Không được tạo."));
            assertThrows(AuthService.Failure.class,()->rooms.activities(outside,id));
            var submission=rooms.submitActivity(student,id,activity.id(),"Câu trả lời đầu tiên");
            assertEquals(student.id(),submission.accountId());
            assertThrows(AuthService.Failure.class,()->rooms.reviewActivity(student,id,activity.id(),student.id(),8,"Tự chấm"));
            var reviewed=rooms.reviewActivity(teacher,id,activity.id(),student.id(),8,"Lập luận rõ ràng.");
            assertEquals(8,reviewed.score());assertEquals("Lập luận rõ ràng.",reviewed.feedback());
            var resubmitted=rooms.submitActivity(student,id,activity.id(),"Câu trả lời đã sửa");
            assertEquals("Câu trả lời đã sửa",resubmitted.answer());assertNull(resubmitted.score());assertNull(resubmitted.feedback());
            assertEquals(1,rooms.activitySubmissions(teacher,id,activity.id()).size());
            rooms.remove(teacher,id,student.id());
            assertThrows(AuthService.Failure.class,()->rooms.submitActivity(student,id,activity.id(),"Không còn quyền"));
        } finally { db.close(); }
    }

}
