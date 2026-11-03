package com.pbl4.web;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordChangeTest {
 @Test void currentPasswordRequiredAndEverySessionRevoked()throws Exception{
  var db=new Database(Path.of("target","password-"+UUID.randomUUID(),"db.sqlite").toString());try{var auth=new AuthService(db);auth.register(new AuthService.Registration("account","Account","Strong!23",null));var one=auth.login(new AuthService.Login("account","Strong!23"));var two=auth.login(new AuthService.Login("account","Strong!23"));var user=db.findAccount("account");
   assertThrows(AuthService.Failure.class,()->auth.changePassword(user,"wrong","Better!234"));assertThrows(AuthService.Failure.class,()->auth.changePassword(user,"Strong!23","weak"));assertThrows(AuthService.Failure.class,()->auth.changePassword(user,"Strong!23","Strong!23"));assertNotNull(db.sessionAccount(AuthService.tokenHash(one.token())));
   auth.changePassword(user,"Strong!23","Better!234");assertNull(db.sessionAccount(AuthService.tokenHash(one.token())));assertNull(db.sessionAccount(AuthService.tokenHash(two.token())));assertThrows(AuthService.Failure.class,()->auth.login(new AuthService.Login("account","Strong!23")));assertNotNull(auth.login(new AuthService.Login("account","Better!234")));
  }finally{db.close();}
 }
}
