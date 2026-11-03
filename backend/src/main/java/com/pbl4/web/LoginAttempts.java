package com.pbl4.web;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
/** Bounded account-based throttling; no permanent account lock. */
final class LoginAttempts {
 private record Window(long start,int count){}
 private final Map<String,Window> attempts=new HashMap<>();
 synchronized void check(String username){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->now-e.getValue().start()>60_000);String key=username.toLowerCase(Locale.ROOT);var old=attempts.get(key);if(old!=null&&old.count()>=10||old==null&&attempts.size()>=4096)throw new AuthService.Failure(429,"Thử đăng nhập quá nhiều lần. Hãy chờ một phút.",Map.of());attempts.put(key,new Window(old==null?now:old.start(),old==null?1:old.count()+1));}
 synchronized void success(String username){attempts.remove(username.toLowerCase(Locale.ROOT));}
}
