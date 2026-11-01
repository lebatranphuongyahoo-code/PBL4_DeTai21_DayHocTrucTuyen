package com.pbl4.integration;

import com.pbl4.server.ServerConfig;
import com.pbl4.tools.LoadTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadSmokeTest {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void loadSmokePasses() throws Exception {
        LoadTest.Result r = LoadTest.run(ServerConfig.ephemeral(), 5, 1);
        assertEquals(0, r.failed());
        assertTrue(r.passed() > 0);
    }
}
