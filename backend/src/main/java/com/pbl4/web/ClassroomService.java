package com.pbl4.web;

import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Permission decisions and compound writes run in the same SQLite transaction. */
@Service
public class ClassroomService {
    public record Detail(long id, String name, String owner, boolean owned, String inviteCode, List<Member> members) {}
    public record Member(long id, String username, String displayName, String role) {}
    public record Request(long accountId, String username, String displayName, long requestedAt) {}
    public record JoinStatus(long roomId, String roomName, String status) {}
    private record Stored(long id, String name, long ownerId, String owner, String invite, String status) {}
    private final Database db;
    private final SecureRandom random = new SecureRandom();
    public ClassroomService(Database db) { this.db = db; }
    static AuthService.Failure denied() { return new AuthService.Failure(403,"Bạn không có quyền thực hiện thao tác này.",Map.of()); }
    static AuthService.Failure invalid(String message) { return new AuthService.Failure(400,message,Map.of()); }
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
    private void owner(Database.Account user,Stored room) { if(room.ownerId()!=user.id() || !user.role().equals("TEACHER")) throw denied(); active(room); }
    private void access(Connection c,Database.Account user,Stored room) throws SQLException {
        active(room);if(room.ownerId()!=user.id() && !member(c,room.id(),user.id())) throw denied();
    }
    public Detail detail(Database.Account user,long id) throws SQLException {
        return db.transaction(c->{Stored room=room(c,id);access(c,user,room);return detail(c,user,room);});
    }
    /** Small access query used for every relay operation, including existing sockets. */
    public boolean mediaAccess(Database.Account user,long id) throws SQLException {
        return db.transaction(c->{Stored room=room(c,id);access(c,user,room);
            return room.ownerId()==user.id() && user.role().equals("TEACHER");});
    }
    private Detail detail(Connection c,Database.Account user,Stored room) throws SQLException {
        var members=new ArrayList<Member>();
        try(var s=c.prepareStatement("SELECT a.id,a.username,a.display_name,a.role FROM accounts a WHERE a.id=? OR EXISTS(SELECT 1 FROM classroom_members m WHERE m.classroom_id=? AND m.account_id=a.id) ORDER BY a.display_name,a.id")) {
            s.setLong(1,room.ownerId());s.setLong(2,room.id());try(var r=s.executeQuery()){while(r.next()) members.add(new Member(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));}
        }
        return new Detail(room.id(),room.name(),room.owner(),room.ownerId()==user.id(),room.ownerId()==user.id()?room.invite():null,members);
    }
    public Detail create(Database.Account user,String name) throws SQLException {
        if(!user.role().equals("TEACHER")) throw denied();
        String clean=name==null?"":name.strip();
        if(clean.length()<2||clean.length()>100||clean.codePoints().anyMatch(Character::isISOControl))throw invalid("Tên lớp cần 2–100 ký tự.");
        return db.transaction(c->{
            for(int attempt=0;attempt<10;attempt++){
                byte[] bytes=new byte[6];random.nextBytes(bytes);String invite=HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
                try(var s=c.prepareStatement("INSERT INTO classrooms(name,owner_id,invite_code,created_at) VALUES(?,?,?,?) ON CONFLICT(invite_code) DO NOTHING")){
                    s.setString(1,clean);s.setLong(2,user.id());s.setString(3,invite);s.setLong(4,System.currentTimeMillis());
                    if(s.executeUpdate()==0)continue;
                }
                long id;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")){r.next();id=r.getLong(1);}
                return detail(c,user,room(c,id));
            }
            throw new SQLException("Invite allocation failed");
        });
    }
    public void add(Database.Account user,long id,String username) throws SQLException {
        db.transaction(c->{Stored room=room(c,id);owner(user,room);
            try(var s=c.prepareStatement("SELECT id FROM accounts WHERE username=? AND role='STUDENT' AND status='ACTIVE'")){
                s.setString(1,username==null?"":username.strip());try(var r=s.executeQuery()){
                    if(!r.next())throw invalid("Không tìm thấy tài khoản học viên đang hoạt động.");
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
        db.transaction(c->{Stored room=room(c,id);owner(user,room);
            try(var s=c.prepareStatement("UPDATE classrooms SET status='ARCHIVED' WHERE id=?")){s.setLong(1,id);s.executeUpdate();}return null;
        });
    }
    public void requestJoin(Database.Account user,String code) throws SQLException {
        if(!user.role().equals("STUDENT"))throw denied();
        db.transaction(c->{
            long id;
            try(var s=c.prepareStatement("SELECT id FROM classrooms WHERE invite_code=? AND status='ACTIVE'")){
                s.setString(1,code==null?"":code.strip().toUpperCase(Locale.ROOT));
                try(var r=s.executeQuery()){if(!r.next())throw invalid("Mã lớp không hợp lệ.");id=r.getLong(1);}
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
            try(var s=c.prepareStatement("UPDATE join_requests SET status=?,reviewed_at=?,reviewed_by=? WHERE classroom_id=? AND account_id=? AND status='PENDING' AND EXISTS(SELECT 1 FROM accounts a WHERE a.id=? AND a.status='ACTIVE' AND a.role='STUDENT')")){
                s.setString(1,approve?"APPROVED":"REJECTED");s.setLong(2,System.currentTimeMillis());s.setLong(3,user.id());s.setLong(4,id);s.setLong(5,account);s.setLong(6,account);
                if(s.executeUpdate()!=1)throw new AuthService.Failure(409,"Yêu cầu không còn chờ duyệt.",Map.of());
            }
            if(approve)addMember(c,id,account);return null;
        });
    }
}
