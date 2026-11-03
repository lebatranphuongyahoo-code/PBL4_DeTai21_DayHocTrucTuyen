package com.pbl4.web;

import java.nio.file.Path;
import java.util.UUID;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class FoundationTest {
    private Path temporary() { return Path.of("target", "test-db-" + UUID.randomUUID()); }
    @Test void strongCredentialsPersistAndPublicRolesCannotEscalate() throws Exception {
        String path=temporary().resolve("auth.db").toString();
        var db=new Database(path); var auth=new AuthService(db);
        for (String password : new String[]{"short","abcdefgh!1","ABCDEFGH!1","StrongPassword!","StrongPassword1","Strong123 ","Aa1!".repeat(19),"Strong!23\u0000"})
            assertEquals(400,assertThrows(AuthService.Failure.class,()->auth.register(new AuthService.Registration("learner","Nguyễn An",password,null))).status);
        for(String role:new String[]{"TEACHER","ADMIN"})
            assertTrue(assertThrows(AuthService.Failure.class,()->auth.register(new AuthService.Registration("learner","Nguyễn An","Strong!23",role))).errors.containsKey("role"));
        auth.register(new AuthService.Registration("learner","Nguyễn An","Strong!23",null));
        auth.register(new AuthService.Registration("learner2","Nguyễn Bình","Strong!23",null));
        assertNotEquals(db.findAccount("learner").passwordHash(),db.findAccount("learner2").passwordHash());
        assertTrue(new BCryptPasswordEncoder().matches("Strong!23",db.findAccount("learner").passwordHash()));
        assertEquals(409,assertThrows(AuthService.Failure.class,()->auth.register(new AuthService.Registration("LEARNER","Nguyễn An","Strong!23",null))).status);
        assertThrows(AuthService.Failure.class,()->auth.login(new AuthService.Login("learner","wrong")));
        var session=auth.login(new AuthService.Login("LEARNER","Strong!23"));
        assertEquals("USER",session.user().role());
        db.close();
        var reopened=new Database(path);
        assertEquals("learner",reopened.sessionAccount(AuthService.tokenHash(session.token())).username());
        assertEquals(0,reopened.listClassrooms(reopened.findAccount("learner").id()).size());
        reopened.deleteSession(AuthService.tokenHash(session.token()));
        assertNull(reopened.sessionAccount(AuthService.tokenHash(session.token())));
        reopened.close();
    }
    @Test void expiryMembershipAndLegacyGuardAreEnforced() throws Exception {
        Path folder=temporary();
        String path=folder.resolve("members.db").toString();
        var db=new Database(path); var auth=new AuthService(db);
        auth.register(new AuthService.Registration("owner","Owner","Strong!23",null));
        auth.register(new AuthService.Registration("learner","Learner","Strong!23",null));
        long owner=db.findAccount("owner").id(), learner=db.findAccount("learner").id();
        db.saveSession("expired",learner,0); assertNull(db.sessionAccount("expired"));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+path); var s=connection.createStatement()) {
            s.executeUpdate("INSERT INTO classrooms VALUES(1,'Toán',"+owner+",'invite','ACTIVE',0)");
            assertEquals(1,db.listClassrooms(owner).size()); assertTrue(db.listClassrooms(learner).isEmpty());
            s.executeUpdate("INSERT INTO classroom_members VALUES(1,"+learner+",0)");
            assertEquals(1,db.listClassrooms(learner).size());
        }
        db.close();
        String legacy=folder.resolve("legacy.db").toString();
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+legacy); var s=connection.createStatement()) { s.execute("CREATE TABLE Users(username TEXT)"); }
        assertThrows(IllegalStateException.class,()->new Database(legacy));
    }
}
