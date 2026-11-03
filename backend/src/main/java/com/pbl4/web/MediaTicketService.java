package com.pbl4.web;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** One-use, short-lived WebSocket tickets keep long-lived bearer sessions out of URLs. */
@Service
public class MediaTicketService {
    public record Grant(Database.Account user, String sessionHash) {}
    private record Ticket(String sessionHash, long expiresAt) {}
    private final SecureRandom random = new SecureRandom();
    private final Map<String,Ticket> tickets = new ConcurrentHashMap<>();
    private final Database db;
    public MediaTicketService(Database db) { this.db=db; }
    public synchronized String issue(String sessionHash) {
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt() <= System.currentTimeMillis());
        if(tickets.size()>=1000)throw new AuthService.Failure(429,"Quá nhiều kết nối chờ. Hãy thử lại sau.",Map.of());
        byte[] raw = new byte[32]; random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(hash(token), new Ticket(sessionHash, System.currentTimeMillis()+30_000));
        return token;
    }
    public Database.Account consume(String token) {
        Grant grant=consumeGrant(token);return grant==null?null:grant.user();
    }
    public Grant consumeGrant(String token) {
        if (token == null || token.isBlank() || token.length() > 128) return null;
        Ticket ticket = tickets.remove(hash(token));
        if (ticket == null || ticket.expiresAt() < System.currentTimeMillis()) return null;
        Database.Account user=current(ticket.sessionHash());
        return user==null?null:new Grant(user,ticket.sessionHash());
    }
    public Database.Account current(String sessionHash) {
        try { return db.sessionAccount(sessionHash); } catch (java.sql.SQLException e) { return null; }
    }
    private static String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
