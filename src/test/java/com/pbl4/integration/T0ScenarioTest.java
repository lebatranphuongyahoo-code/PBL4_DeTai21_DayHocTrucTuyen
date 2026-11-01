package com.pbl4.integration;

import com.pbl4.server.ServerConfig;
import com.pbl4.tools.T0Demo;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

class T0ScenarioTest {

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void t0ScenarioPassesEveryCheck() throws Exception {
        T0Demo.Result r = T0Demo.run(ServerConfig.ephemeral());
        assertEquals(0, r.failed(), "so check KHONG DAT");
        assertEquals(20, r.passed());
    }
}
