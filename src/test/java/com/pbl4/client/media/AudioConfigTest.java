package com.pbl4.client.media;

import com.pbl4.common.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra tham số âm thanh khớp với giao thức UDP (648 byte = 4 + 4 + 640). */
class AudioConfigTest {

    @Test
    void selfCheckPasses() {
        assertTrue(AudioConfig.selfCheck());
    }

    @Test
    void chunkBytesIs640() {
        assertEquals(640, AudioConfig.CHUNK_BYTES);
    }

    @Test
    void chunkBytesMatchesProtocol() {
        assertEquals(Protocol.UDP_PCM_BYTES, AudioConfig.CHUNK_BYTES,
                "PCM trong goi UDP va chunk ghi doc phai cung kich thuoc");
    }
}
