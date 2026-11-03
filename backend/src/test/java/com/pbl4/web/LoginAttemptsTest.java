package com.pbl4.web;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LoginAttemptsTest {
 @Test void caseVariantsShareLimitAndOtherAccountsRemainAvailable(){var attempts=new LoginAttempts();for(int i=0;i<10;i++)attempts.check("Alice");assertEquals(429,assertThrows(AuthService.Failure.class,()->attempts.check("ALICE")).status);assertDoesNotThrow(()->attempts.check("Bob"));attempts.success("alice");assertDoesNotThrow(()->attempts.check("ALICE"));}
}
