package com.pbl4.web;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Permission decisions and compound writes run in the same SQLite transaction. */
@Service
public class ClassroomService {
    public record Detail(long id, String name, String owner, boolean owned, String inviteCode, List<Member> members, boolean meetingOpen, Long meetingId) {}
    public record Member(long id, String username, String displayName, String role) {}
    public record Request(long accountId, String username, String displayName, long requestedAt) {}
    public record JoinStatus(long roomId, String roomName, String status) {}
    public record ConnectionHistory(long id,String username,String displayName,long joinedAt,Long leftAt,boolean interrupted) {}
    public record MeetingHistory(long id,long startedAt,Long endedAt,boolean startUncertain,String endReason,List<ConnectionHistory> connections) {}
    public record ChatMessage(long id,String username,String text,long sentAt,Long meetingId) {}
    public record ActivitySubmission(long accountId,String username,String displayName,String answer,Long submittedAt,Integer score,String feedback,Long reviewedAt) {}
    public record ActivityView(long id,String title,String prompt,long createdAt,int submissionCount,ActivitySubmission submission) {}
    public record AdminDetail(long id,String name,long ownerId,String ownerUsername,String ownerDisplayName,String status,
                              boolean meetingOpen,List<Member> members) {}
    private record Stored(long id, String name, long ownerId, String owner, String invite, String status) {}
    private final Database db;
    private final SecureRandom random = new SecureRandom();
    public ClassroomService(Database db) { this.db = db; }
    static AuthService.Failure denied() { return new AuthService.Failure(403,"Bạn không có quyền thực hiện thao tác này.",Map.of()); }
    static AuthService.Failure invalid(String message) { return new AuthService.Failure(400,message,Map.of()); }
    private void admin(Database.Account user) { if (!"ADMIN".equals(user.role())) throw denied(); }
    private Stored room(Connection c, long id) throws SQLException {
        try (var s=c.prepareStatement("SELECT c.*,a.display_name owner FROM classrooms c JOIN accounts a ON a.id=c.owner_id WHERE c.id=?")) {
            s.setLong(1,id);
            try(var r=s.executeQuery()) {
                if(!r.next()) throw new AuthService.Failure(404,"Không tìm thấy lớp học.",Map.of());
                return new Stored(id,r.getString("name"),r.getLong("owner_id"),r.getString("owner"),r.getString("invite_code"),r.getString("status"));
            }
        }
    }
    private boolean member(Connection c,long room,long account) throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM classroom_members WHERE classroom_id=? AND account_id=?")) {
            s.setLong(1,room);s.setLong(2,account);try(var r=s.executeQuery()){return r.next();}
        }
    }
    private void active(Stored room) { if(!room.status().equals("ACTIVE")) throw invalid("Lớp đã được lưu trữ."); }
    private void owner(Database.Account user,Stored room) { if(room.ownerId()!=user.id()) throw denied(); active(room); }
    private void ownerOrAdmin(Database.Account user,Stored room) { if(!"ADMIN".equals(user.role())&&room.ownerId()!=user.id()) throw denied(); active(room); }
    private void access(Connection c,Database.Account user,Stored room) throws SQLException {
        active(room);if(room.ownerId()!=user.id() && !member(c,room.id(),user.id())) throw denied();
    }
    public Detail detail(Database.Account user,long id) throws SQLException {
        return db.transaction(c->{Stored room=room(c,id);access(c,user,room);return detail(c,user,room);});
    }
    public List<Database.AdminClassroom> adminClassrooms(Database.Account user) throws SQLException {
        admin(user); return db.listAdminClassrooms();
    }
    public List<Database.AdminUser> adminUsers(Database.Account user) throws SQLException {
        admin(user); return db.listAdminUsers();
    }
    public void promoteStudentToTeacher(Database.Account user,long accountId) throws SQLException {
        admin(user);
        if(!db.promoteStudentToTeacher(accountId))throw new AuthService.Failure(409,"Chỉ tài khoản học viên đang hoạt động mới được cấp quyền giảng viên.",Map.of());
    }
    public AdminDetail adminDetail(Database.Account user,long id) throws SQLException {
        return db.transaction(c->{
            admin(user); Stored room=room(c,id);
            var members=new ArrayList<Member>();
            try(var s=c.prepareStatement("SELECT a.id,a.username,a.display_name FROM accounts a WHERE a.id=? OR EXISTS(SELECT 1 FROM classroom_members m WHERE m.classroom_id=? AND m.account_id=a.id) ORDER BY a.display_name,a.id")) {
                s.setLong(1,room.ownerId()); s.setLong(2,id);
                try(var r=s.executeQuery()){while(r.next()) members.add(new Member(r.getLong(1),r.getString(2),r.getString(3),r.getLong(1)==room.ownerId()?"OWNER":"MEMBER"));}
            }
            try(var s=c.prepareStatement("SELECT username,display_name FROM accounts WHERE id=?")) {
                s.setLong(1,room.ownerId());
                try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Class owner missing");
                    return new AdminDetail(id,room.name(),room.ownerId(),r.getString(1),r.getString(2),room.status(),meetingOpen(c,id),members);
                }
            }
        });
    }
    /** Small access query used for every relay operation, including existing sockets. */
    public boolean mediaAccess(Database.Account user,long id) throws SQLException {
        return db.transaction(c->{Stored room=room(c,id);access(c,user,room);
            if(!meetingOpen(c,id))throw invalid("Buổi học đã kết thúc. Chờ chủ lớp bắt đầu buổi mới.");
            return room.ownerId()==user.id();});
    }
    private Detail detail(Connection c,Database.Account user,Stored room) throws SQLException {
        var members=new ArrayList<Member>();
        try(var s=c.prepareStatement("SELECT a.id,a.username,a.display_name,a.role FROM accounts a WHERE a.id=? OR EXISTS(SELECT 1 FROM classroom_members m WHERE m.classroom_id=? AND m.account_id=a.id) ORDER BY a.display_name,a.id")) {
            s.setLong(1,room.ownerId());s.setLong(2,room.id());try(var r=s.executeQuery()){while(r.next()) members.add(new Member(r.getLong(1),r.getString(2),r.getString(3),r.getLong(1)==room.ownerId()?"OWNER":"MEMBER"));}
        }
        return new Detail(room.id(),room.name(),room.owner(),room.ownerId()==user.id(),room.ownerId()==user.id()?room.invite():null,members,meetingOpen(c,room.id()),latestMeeting(c,room.id()));
    }
    public Detail create(Database.Account user,String name) throws SQLException {
        if(!"TEACHER".equals(user.role()))throw denied();
        String clean=className(name);
        return db.transaction(c->{
            long id=create(c,user.id(),clean);
            return detail(c,user,room(c,id));
        });
    }
    public long createForTeacher(Database.Account user,String name,String teacherUsername) throws SQLException {
        admin(user);
        String clean=className(name),username=teacherUsername==null?"":teacherUsername.strip();
        return db.transaction(c->{
            long ownerId;
            try(var s=c.prepareStatement("SELECT id FROM accounts WHERE username=? AND role='TEACHER' AND status='ACTIVE'")){
                s.setString(1,username);try(var r=s.executeQuery()){
                    if(!r.next())throw new AuthService.Failure(400,"Hãy chọn một tài khoản giảng viên đang hoạt động.",Map.of("teacherUsername","Tài khoản phải có quyền giảng viên."));
                    ownerId=r.getLong(1);
                }
            }
            return create(c,ownerId,clean);
        });
    }
    private String className(String name) {
        String clean=name==null?"":name.strip();
        if(clean.length()<2||clean.length()>100||clean.codePoints().anyMatch(Character::isISOControl))throw invalid("Tên lớp cần 2–100 ký tự.");
        return clean;
    }
    private long create(Connection c,long ownerId,String name) throws SQLException {
        for(int attempt=0;attempt<10;attempt++){
            byte[] bytes=new byte[6];random.nextBytes(bytes);String invite=HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
            try(var s=c.prepareStatement("INSERT INTO classrooms(name,owner_id,invite_code,created_at) VALUES(?,?,?,?) ON CONFLICT(invite_code) DO NOTHING")){
                s.setString(1,name);s.setLong(2,ownerId);s.setString(3,invite);s.setLong(4,System.currentTimeMillis());
                if(s.executeUpdate()==0)continue;
            }
            long id;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();id=r.getLong(1);}
            try(var meeting=c.prepareStatement("INSERT INTO classroom_meetings(classroom_id,is_open) VALUES(?,0)")){meeting.setLong(1,id);meeting.executeUpdate();}
            return id;
        }
        throw new SQLException("Invite allocation failed");
    }
    private boolean meetingOpen(Connection c,long id)throws SQLException {try(var s=c.prepareStatement("SELECT is_open FROM classroom_meetings WHERE classroom_id=?")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()&&r.getInt(1)==1;}}}
    private Long latestMeeting(Connection c,long id)throws SQLException {
        try(var s=c.prepareStatement("SELECT id FROM meeting_sessions WHERE classroom_id=? ORDER BY id DESC LIMIT 1")){
            s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?r.getLong(1):null;}}
    }
    private void setMeeting(Connection c,long id,boolean open)throws SQLException {
        if(meetingOpen(c,id)==open)return;
        try(var s=c.prepareStatement("INSERT INTO classroom_meetings(classroom_id,is_open) VALUES(?,?) ON CONFLICT(classroom_id) DO UPDATE SET is_open=excluded.is_open")){s.setLong(1,id);s.setInt(2,open?1:0);s.executeUpdate();}
        String sql=open?"INSERT INTO meeting_sessions(classroom_id,started_at,start_uncertain,owner_seen_at) VALUES(?,?,0,?)":"UPDATE meeting_sessions SET ended_at=?,end_reason='OWNER' WHERE classroom_id=? AND ended_at IS NULL";
        try(var s=c.prepareStatement(sql)){if(open){long now=System.currentTimeMillis();s.setLong(1,id);s.setLong(2,now);s.setLong(3,now);}else{s.setLong(1,System.currentTimeMillis());s.setLong(2,id);}s.executeUpdate();}
    }
    public void setMeeting(Database.Account user,long id,boolean open)throws SQLException {db.transaction(c->{owner(user,room(c,id));setMeeting(c,id,open);return null;});}
    public boolean isMeetingOpen(long id)throws SQLException {return db.transaction(c->meetingOpen(c,id));}
    public void ownerHeartbeat(Database.Account user,long id)throws SQLException {
        db.transaction(c->{owner(user,room(c,id));
            try(var s=c.prepareStatement("UPDATE meeting_sessions SET owner_seen_at=? WHERE classroom_id=? AND ended_at IS NULL")){
                s.setLong(1,System.currentTimeMillis());s.setLong(2,id);s.executeUpdate();}return null;});
    }
    public List<Long> closeAbandonedMeetings(long now)throws SQLException {
        return db.transaction(c->{var expired=new ArrayList<Long>();
            try(var s=c.prepareStatement("SELECT classroom_id FROM meeting_sessions WHERE ended_at IS NULL AND owner_seen_at<=?")){
                s.setLong(1,now-600_000);try(var r=s.executeQuery()){while(r.next())expired.add(r.getLong(1));}}
            for(long id:expired){
                setMeeting(c,id,false);
                try(var s=c.prepareStatement("UPDATE meeting_sessions SET ended_at=?,end_reason='OWNER_TIMEOUT' WHERE classroom_id=? AND id=(SELECT MAX(id) FROM meeting_sessions WHERE classroom_id=?)")){
                    s.setLong(1,now);s.setLong(2,id);s.setLong(3,id);s.executeUpdate();}
            }return expired;});
    }
    public Set<String> blockedMicrophones(long id)throws SQLException {
        return db.transaction(c->{var names=new HashSet<String>();
            try(var s=c.prepareStatement("SELECT b.username FROM meeting_microphone_blocks b JOIN meeting_sessions m ON m.id=b.session_id WHERE m.classroom_id=? AND m.ended_at IS NULL")){
                s.setLong(1,id);try(var r=s.executeQuery()){while(r.next())names.add(r.getString(1));}}
            return names;});
    }
    public void blockMicrophone(Database.Account user,long id,String username,boolean blocked)throws SQLException {
        db.transaction(c->{owner(user,room(c,id));if(!meetingOpen(c,id))throw invalid("Buổi học đã kết thúc.");
            String sql=blocked?"INSERT OR IGNORE INTO meeting_microphone_blocks(session_id,username) SELECT id,? FROM meeting_sessions WHERE classroom_id=? AND ended_at IS NULL"
                :"DELETE FROM meeting_microphone_blocks WHERE username=? AND session_id IN (SELECT id FROM meeting_sessions WHERE classroom_id=? AND ended_at IS NULL)";
            try(var s=c.prepareStatement(sql)){s.setString(1,username);s.setLong(2,id);s.executeUpdate();}return null;});
    }
    public long joinHistory(Database.Account user,long id)throws SQLException {
        return db.transaction(c->{Stored room=room(c,id);access(c,user,room);
            if(!meetingOpen(c,id))throw invalid("Buổi học đã kết thúc. Chờ chủ lớp bắt đầu buổi mới.");
            try(var s=c.prepareStatement("INSERT INTO meeting_connections(session_id,account_id,joined_at) SELECT id,?,? FROM meeting_sessions WHERE classroom_id=? AND ended_at IS NULL")){
                s.setLong(1,user.id());s.setLong(2,System.currentTimeMillis());s.setLong(3,id);if(s.executeUpdate()!=1)throw new SQLException("Open meeting history missing");}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();return r.getLong(1);}
        });
    }
    public void leaveHistory(long id)throws SQLException {db.transaction(c->{try(var s=c.prepareStatement("UPDATE meeting_connections SET left_at=? WHERE id=? AND left_at IS NULL AND interrupted=0")){s.setLong(1,System.currentTimeMillis());s.setLong(2,id);s.executeUpdate();}return null;});}
    public List<MeetingHistory> history(Database.Account user,long id)throws SQLException {return history(user,id,Long.MAX_VALUE);}
    public List<MeetingHistory> history(Database.Account user,long id,long before)throws SQLException {
        return db.transaction(c->{var room=room(c,id);access(c,user,room);boolean owned=room.ownerId()==user.id();var result=new ArrayList<MeetingHistory>();
            try(var s=c.prepareStatement("SELECT id,started_at,ended_at,start_uncertain,end_reason FROM meeting_sessions WHERE classroom_id=? AND id<? ORDER BY id DESC LIMIT 50")){
                s.setLong(1,id);s.setLong(2,before);try(var r=s.executeQuery()){while(r.next()){
                    long session=r.getLong(1);var entries=new ArrayList<ConnectionHistory>();
                    try(var q=c.prepareStatement("SELECT mc.id,a.username,a.display_name,mc.joined_at,mc.left_at,mc.interrupted FROM meeting_connections mc JOIN accounts a ON a.id=mc.account_id WHERE mc.session_id=? ORDER BY mc.id DESC LIMIT 200")){
                        q.setLong(1,session);if(owned)try(var e=q.executeQuery()){while(e.next())entries.add(new ConnectionHistory(e.getLong(1),e.getString(2),e.getString(3),e.getLong(4),e.getObject(5)==null?null:e.getLong(5),e.getInt(6)!=0));}}
                    result.add(new MeetingHistory(session,r.getLong(2),r.getObject(3)==null?null:r.getLong(3),r.getInt(4)!=0,r.getString(5),entries));
                }}
            }return result;
        });
    }
    public ChatMessage postMessage(Database.Account user,long id,String body)throws SQLException {
        return postMessage(user,id,0,body);
    }
    public ChatMessage postMessage(Database.Account user,long id,long session,String body)throws SQLException {
        String clean=body==null?"":body.strip();
        if(clean.isEmpty()||clean.length()>500)throw invalid("Tin nhắn cần 1–500 ký tự.");
        return db.transaction(c->{access(c,user,room(c,id));if(!meetingOpen(c,id))throw invalid("Buổi học đã kết thúc.");
            Long current=latestMeeting(c,id);
            if(current==null||(session!=0&&session!=current))throw invalid("Buổi học đã kết thúc. Hãy kết nối buổi hiện tại.");
            long now=System.currentTimeMillis();
            try(var s=c.prepareStatement("INSERT INTO class_messages(classroom_id,account_id,body,sent_at,session_id) VALUES(?,?,?,?,?)")){
                s.setLong(1,id);s.setLong(2,user.id());s.setString(3,clean);s.setLong(4,now);s.setLong(5,current);s.executeUpdate();}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();return new ChatMessage(r.getLong(1),user.username(),clean,now,current);}
        });
    }
    public List<ChatMessage> messages(Database.Account user,long id,long before)throws SQLException {
        return messages(user,id,-1,before);
    }
    public List<ChatMessage> messages(Database.Account user,long id,long session,long before)throws SQLException {
        return db.transaction(c->{access(c,user,room(c,id));var result=new ArrayList<ChatMessage>();
            Long selected=session==-1?latestMeeting(c,id):session==0?null:session;
            if(session==-1&&selected==null)return result;
            if(selected!=null)try(var s=c.prepareStatement("SELECT 1 FROM meeting_sessions WHERE id=? AND classroom_id=?")){
                s.setLong(1,selected);s.setLong(2,id);try(var r=s.executeQuery()){if(!r.next())throw new AuthService.Failure(404,"Không tìm thấy buổi học.",Map.of());}}
            try(var s=c.prepareStatement("SELECT m.id,a.username,m.body,m.sent_at FROM class_messages m JOIN accounts a ON a.id=m.account_id WHERE m.classroom_id=? AND m.session_id IS ? AND m.id<? ORDER BY m.id DESC LIMIT 50")){
                s.setLong(1,id);if(selected==null)s.setNull(2,Types.INTEGER);else s.setLong(2,selected);s.setLong(3,before);try(var r=s.executeQuery()){while(r.next())result.add(new ChatMessage(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),selected));}}
            Collections.reverse(result);return result;});
    }
    public List<ActivityView> activities(Database.Account user,long roomId)throws SQLException {
        return db.transaction(c->{Stored room=room(c,roomId);access(c,user,room);boolean owned=room.ownerId()==user.id();var result=new ArrayList<ActivityView>();
            try(var s=c.prepareStatement("SELECT id,title,prompt,created_at,(SELECT count(*) FROM activity_submissions sub WHERE sub.activity_id=a.id) FROM class_activities a WHERE classroom_id=? ORDER BY id DESC")){
                s.setLong(1,roomId);try(var r=s.executeQuery()){while(r.next()){
                    long activityId=r.getLong(1);int count=r.getInt(5);ActivitySubmission submission=owned?null:activitySubmission(c,activityId,user.id());
                    result.add(new ActivityView(activityId,r.getString(2),r.getString(3),r.getLong(4),owned?count:0,submission));
                }}
            }return result;
        });
    }
    public ActivityView createActivity(Database.Account user,long roomId,String title,String prompt)throws SQLException {
        String cleanTitle=title==null?"":title.strip(),cleanPrompt=prompt==null?"":prompt.strip();
        if(cleanTitle.length()<2||cleanTitle.length()>120)throw invalid("Tiêu đề bài tập cần 2–120 ký tự.");
        if(cleanPrompt.isEmpty()||cleanPrompt.length()>2000)throw invalid("Nội dung bài tập cần 1–2000 ký tự.");
        return db.transaction(c->{Stored room=room(c,roomId);owner(user,room);
            try(var s=c.prepareStatement("INSERT INTO class_activities(classroom_id,title,prompt,created_at) VALUES(?,?,?,?)")){
                s.setLong(1,roomId);s.setString(2,cleanTitle);s.setString(3,cleanPrompt);s.setLong(4,System.currentTimeMillis());s.executeUpdate();
            }
            long id;try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();id=r.getLong(1);}
            return new ActivityView(id,cleanTitle,cleanPrompt,System.currentTimeMillis(),0,null);
        });
    }
    public ActivitySubmission submitActivity(Database.Account user,long roomId,long activityId,String answer)throws SQLException {
        String clean=answer==null?"":answer.strip();if(clean.isEmpty()||clean.length()>5000)throw invalid("Câu trả lời cần 1–5000 ký tự.");
        return db.transaction(c->{Stored room=room(c,roomId);access(c,user,room);if(room.ownerId()==user.id())throw denied();
            requireActivity(c,roomId,activityId);
            try(var s=c.prepareStatement("INSERT INTO activity_submissions(activity_id,account_id,answer,submitted_at) VALUES(?,?,?,?) ON CONFLICT(activity_id,account_id) DO UPDATE SET answer=excluded.answer,submitted_at=excluded.submitted_at,score=NULL,feedback=NULL,reviewed_at=NULL")){
                s.setLong(1,activityId);s.setLong(2,user.id());s.setString(3,clean);s.setLong(4,System.currentTimeMillis());s.executeUpdate();
            }
            return activitySubmission(c,activityId,user.id());
        });
    }
    public List<ActivitySubmission> activitySubmissions(Database.Account user,long roomId,long activityId)throws SQLException {
        return db.transaction(c->{Stored room=room(c,roomId);owner(user,room);requireActivity(c,roomId,activityId);var result=new ArrayList<ActivitySubmission>();
            String sql="SELECT a.id,a.username,a.display_name,s.answer,s.submitted_at,s.score,s.feedback,s.reviewed_at FROM classroom_members m JOIN accounts a ON a.id=m.account_id LEFT JOIN activity_submissions s ON s.activity_id=? AND s.account_id=a.id WHERE m.classroom_id=? AND a.status='ACTIVE' ORDER BY a.display_name,a.id";
            try(var s=c.prepareStatement(sql)){s.setLong(1,activityId);s.setLong(2,roomId);try(var r=s.executeQuery()){while(r.next())result.add(new ActivitySubmission(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),nullableLong(r,5),nullableInt(r,6),r.getString(7),nullableLong(r,8)));}}
            return result;
        });
    }
    public ActivitySubmission reviewActivity(Database.Account user,long roomId,long activityId,long accountId,Integer score,String feedback)throws SQLException {
        String clean=feedback==null?"":feedback.strip();if(score!=null&&(score<0||score>10))throw invalid("Điểm cần nằm trong khoảng 0–10.");
        if(clean.length()>2000)throw invalid("Nhận xét tối đa 2000 ký tự.");if(score==null&&clean.isEmpty())throw invalid("Hãy nhập điểm hoặc nhận xét.");
        return db.transaction(c->{Stored room=room(c,roomId);owner(user,room);requireActivity(c,roomId,activityId);
            try(var s=c.prepareStatement("UPDATE activity_submissions SET score=?,feedback=?,reviewed_at=? WHERE activity_id=? AND account_id=? AND EXISTS(SELECT 1 FROM classroom_members m JOIN accounts a ON a.id=m.account_id WHERE m.classroom_id=? AND m.account_id=? AND a.status='ACTIVE')")){
                if(score==null)s.setNull(1,Types.INTEGER);else s.setInt(1,score);if(clean.isEmpty())s.setNull(2,Types.VARCHAR);else s.setString(2,clean);s.setLong(3,System.currentTimeMillis());s.setLong(4,activityId);s.setLong(5,accountId);s.setLong(6,roomId);s.setLong(7,accountId);
                if(s.executeUpdate()!=1)throw invalid("Học viên chưa nộp bài hoặc không còn trong lớp.");
            }
            return activitySubmission(c,activityId,accountId);
        });
    }
    private void requireActivity(Connection c,long roomId,long activityId)throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM class_activities WHERE id=? AND classroom_id=?")){s.setLong(1,activityId);s.setLong(2,roomId);try(var r=s.executeQuery()){if(!r.next())throw new AuthService.Failure(404,"Không tìm thấy bài tập.",Map.of());}}
    }
    private ActivitySubmission activitySubmission(Connection c,long activityId,long accountId)throws SQLException {
        try(var s=c.prepareStatement("SELECT a.id,a.username,a.display_name,s.answer,s.submitted_at,s.score,s.feedback,s.reviewed_at FROM activity_submissions s JOIN accounts a ON a.id=s.account_id WHERE s.activity_id=? AND s.account_id=?")){
            s.setLong(1,activityId);s.setLong(2,accountId);try(var r=s.executeQuery()){return r.next()?new ActivitySubmission(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),nullableInt(r,6),r.getString(7),nullableLong(r,8)):null;}
        }
    }
    private static Long nullableLong(ResultSet r,int column)throws SQLException {long value=r.getLong(column);return r.wasNull()?null:value;}
    private static Integer nullableInt(ResultSet r,int column)throws SQLException {int value=r.getInt(column);return r.wasNull()?null:value;}
    public void add(Database.Account user,long id,String username) throws SQLException {
        db.transaction(c->{Stored room=room(c,id);owner(user,room);
            try(var s=c.prepareStatement("SELECT id FROM accounts WHERE username=? AND status='ACTIVE'")){
                s.setString(1,username==null?"":username.strip());try(var r=s.executeQuery()){
                    if(!r.next())throw invalid("Không tìm thấy tài khoản học viên đang hoạt động.");
                    if(r.getLong(1)==room.ownerId())throw invalid("Tài khoản này đã là chủ lớp.");
                    addMember(c,id,r.getLong(1));
                }
            }return null;
        });
    }
    private void addMember(Connection c,long room,long account) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO classroom_members VALUES(?,?,?) ON CONFLICT DO NOTHING")){s.setLong(1,room);s.setLong(2,account);s.setLong(3,System.currentTimeMillis());s.executeUpdate();}
        try(var s=c.prepareStatement("UPDATE join_requests SET status='APPROVED',reviewed_at=? WHERE classroom_id=? AND account_id=? AND status='PENDING'")){s.setLong(1,System.currentTimeMillis());s.setLong(2,room);s.setLong(3,account);s.executeUpdate();}
    }
    public void remove(Database.Account user,long id,long account) throws SQLException {
        db.transaction(c->{Stored room=room(c,id);owner(user,room);if(account==room.ownerId())throw invalid("Không thể xóa chủ lớp.");
            try(var s=c.prepareStatement("DELETE FROM classroom_members WHERE classroom_id=? AND account_id=?")){s.setLong(1,id);s.setLong(2,account);s.executeUpdate();}
            return null;
        });
    }
    public void leave(Database.Account user,long id) throws SQLException {
        db.transaction(c->{Stored room=room(c,id);access(c,user,room);if(room.ownerId()==user.id())throw invalid("Chủ lớp hãy lưu trữ lớp thay vì rút khỏi danh sách.");
            try(var s=c.prepareStatement("DELETE FROM classroom_members WHERE classroom_id=? AND account_id=?")){s.setLong(1,id);s.setLong(2,user.id());s.executeUpdate();}return null;
        });
    }
    public void archive(Database.Account user,long id) throws SQLException {
        db.transaction(c->{Stored room=room(c,id);ownerOrAdmin(user,room);
            setMeeting(c,id,false);
            try(var s=c.prepareStatement("UPDATE classrooms SET status='ARCHIVED' WHERE id=?")){s.setLong(1,id);s.executeUpdate();}return null;
        });
    }
    public void archiveAsAdmin(Database.Account user,long id) throws SQLException {
        db.transaction(c->{
            admin(user); Stored room=room(c,id); active(room);
            try(var s=c.prepareStatement("UPDATE classrooms SET status='ARCHIVED' WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
            return null;
        });
    }
    public void restore(Database.Account user,long id) throws SQLException {
        db.transaction(c->{
            admin(user); Stored room=room(c,id);
            if("ARCHIVED".equals(room.status()))try(var s=c.prepareStatement("UPDATE classrooms SET status='ACTIVE' WHERE id=?")){
                s.setLong(1,id);s.executeUpdate();
            }
            return null;
        });
    }
    public boolean assignOwner(Database.Account user,long id,String username) throws SQLException {
        return db.transaction(c->{
            admin(user); Stored room=room(c,id); active(room); long target;
            try(var s=c.prepareStatement("SELECT id,role FROM accounts WHERE username=? AND status='ACTIVE'")) {
                s.setString(1,username==null?"":username.strip());
                try(var r=s.executeQuery()) {
                    if(!r.next())throw new AuthService.Failure(404,"Không tìm thấy tài khoản phụ trách.",Map.of());
                    if(!"TEACHER".equals(r.getString("role")))throw invalid("Tài khoản được giao phụ trách phải có vai trò giảng viên.");
                    target=r.getLong(1);
                }
            }
            if(target==room.ownerId())return false;
            setMeeting(c,id,false);
            try(var s=c.prepareStatement("INSERT OR IGNORE INTO classroom_members(classroom_id,account_id,joined_at) VALUES(?,?,?)")) {
                s.setLong(1,id); s.setLong(2,room.ownerId()); s.setLong(3,System.currentTimeMillis()); s.executeUpdate();
            }
            try(var s=c.prepareStatement("DELETE FROM classroom_members WHERE classroom_id=? AND account_id=?")) { s.setLong(1,id); s.setLong(2,target); s.executeUpdate(); }
            try(var s=c.prepareStatement("UPDATE classrooms SET owner_id=? WHERE id=?")) { s.setLong(1,target); s.setLong(2,id); s.executeUpdate(); }
            return true;
        });
    }
    public String transferMember(Database.Account user,long sourceId,long accountId,long targetId) throws SQLException {
        return db.transaction(c->{
            admin(user);
            if(sourceId==targetId)throw invalid("Lớp nguồn và lớp đích phải khác nhau.");
            Stored source=room(c,sourceId), target=room(c,targetId); active(source); active(target);
            if(accountId==source.ownerId()||accountId==target.ownerId())throw invalid("Không thể chuyển chủ lớp bằng thao tác học viên.");
            if(!member(c,sourceId,accountId))throw invalid("Học viên không thuộc lớp nguồn.");
            if(member(c,targetId,accountId))throw invalid("Học viên đã thuộc lớp đích.");
            String username;
            try(var s=c.prepareStatement("SELECT username FROM accounts WHERE id=? AND status='ACTIVE'")){s.setLong(1,accountId);try(var r=s.executeQuery()){if(!r.next())throw invalid("Không tìm thấy học viên.");username=r.getString(1);}}
            try(var s=c.prepareStatement("DELETE FROM classroom_members WHERE classroom_id=? AND account_id=?")) {
                s.setLong(1,sourceId); s.setLong(2,accountId); if(s.executeUpdate()!=1)throw new SQLException("Transfer source changed");
            }
            try(var s=c.prepareStatement("INSERT INTO classroom_members(classroom_id,account_id,joined_at) VALUES(?,?,?)")) {
                s.setLong(1,targetId); s.setLong(2,accountId); s.setLong(3,System.currentTimeMillis()); s.executeUpdate();
            }
            return username;
        });
    }
    public void requestJoin(Database.Account user,String code) throws SQLException {
        db.transaction(c->{
            long id;
            try(var s=c.prepareStatement("SELECT id,owner_id FROM classrooms WHERE invite_code=? AND status='ACTIVE'")){
                s.setString(1,code==null?"":code.strip().toUpperCase(Locale.ROOT));
                try(var r=s.executeQuery()){if(!r.next())throw invalid("Mã lớp không hợp lệ.");id=r.getLong(1);if(r.getLong(2)==user.id())throw invalid("Bạn đã là chủ lớp.");}
            }
            if(member(c,id,user.id()))throw invalid("Bạn đã là thành viên lớp.");
            try(var s=c.prepareStatement("INSERT INTO join_requests(classroom_id,account_id,status,requested_at) VALUES(?,?,'PENDING',?) ON CONFLICT(classroom_id,account_id) DO UPDATE SET status='PENDING',requested_at=excluded.requested_at,reviewed_at=NULL,reviewed_by=NULL WHERE join_requests.status!='PENDING'")){
                s.setLong(1,id);s.setLong(2,user.id());s.setLong(3,System.currentTimeMillis());s.executeUpdate();
            }return null;
        });
    }
    public List<Request> requests(Database.Account user,long id)throws SQLException {
        return db.transaction(c->{owner(user,room(c,id));var requests=new ArrayList<Request>();
            try(var s=c.prepareStatement("SELECT a.id,a.username,a.display_name,r.requested_at FROM join_requests r JOIN accounts a ON a.id=r.account_id WHERE r.classroom_id=? AND r.status='PENDING' AND a.status='ACTIVE' ORDER BY r.requested_at")){
                s.setLong(1,id);try(var r=s.executeQuery()){while(r.next())requests.add(new Request(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4)));}
            }return requests;
        });
    }
    public List<JoinStatus> myRequests(Database.Account user) throws SQLException {
        return db.transaction(c->{
            var result = new ArrayList<JoinStatus>();
            try(var s=c.prepareStatement("SELECT c.id,c.name,r.status FROM join_requests r JOIN classrooms c ON c.id=r.classroom_id WHERE r.account_id=? AND c.status='ACTIVE' ORDER BY r.requested_at DESC LIMIT 100")){
                s.setLong(1,user.id());
                try(var r=s.executeQuery()){while(r.next())result.add(new JoinStatus(r.getLong(1),r.getString(2),r.getString(3)));}
            }
            return result;
        });
    }
    public void review(Database.Account user,long id,long account,boolean approve)throws SQLException {
        db.transaction(c->{owner(user,room(c,id));
            try(var s=c.prepareStatement("UPDATE join_requests SET status=?,reviewed_at=?,reviewed_by=? WHERE classroom_id=? AND account_id=? AND status='PENDING' AND EXISTS(SELECT 1 FROM accounts a WHERE a.id=? AND a.status='ACTIVE')")){
                s.setString(1,approve?"APPROVED":"REJECTED");s.setLong(2,System.currentTimeMillis());s.setLong(3,user.id());s.setLong(4,id);s.setLong(5,account);s.setLong(6,account);
                if(s.executeUpdate()!=1)throw new AuthService.Failure(409,"Yêu cầu không còn chờ duyệt.",Map.of());
            }
            if(approve)addMember(c,id,account);return null;
        });
    }

}
