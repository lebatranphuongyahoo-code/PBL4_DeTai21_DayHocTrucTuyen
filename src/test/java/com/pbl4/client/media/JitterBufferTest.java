package com.pbl4.client.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra hành vi jitter buffer: prebuffer, drop-oldest, clear, tham số sai. */
class JitterBufferTest {

    @Test
    void takeReturnsNullUntilPrebuffered() throws InterruptedException {
        JitterBuffer jb = new JitterBuffer(10, 3);
        jb.put(new byte[] {1});
        jb.put(new byte[] {2});
        assertNull(jb.take(10), "Chua du 3 goi prebuffer thi phai tra null");
        jb.put(new byte[] {3});
        byte[] out = jb.take(10);
        assertTrue(out != null && out[0] == 1, "Du prebuffer thi lay ra goi CU NHAT");
    }

    @Test
    void dropsOldestWhenFull() {
        JitterBuffer jb = new JitterBuffer(2, 0);
        jb.put(new byte[] {65});   // A
        jb.put(new byte[] {66});   // B
        jb.put(new byte[] {67});   // C -> day, bo A
        assertEquals(1, jb.getDroppedCount());
        try {
            assertEquals(66, jb.take(10)[0], "Goi bi bo phai la A (cu nhat)");
            assertEquals(67, jb.take(10)[0]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void clearResetsPrebuffer() throws InterruptedException {
        JitterBuffer jb = new JitterBuffer(10, 3);
        jb.put(new byte[] {1});
        jb.put(new byte[] {2});
        jb.put(new byte[] {3});
        // put() không bật cờ prebuffer — cờ chỉ được bật khi consumer gọi take().
        assertTrue(jb.take(10) != null, "Du prebuffer thi take phai tra goi");
        assertTrue(jb.isPrebuffered());
        jb.clear();
        assertFalse(jb.isPrebuffered(), "clear phai xoa ca co prebuffer");
        assertNull(jb.take(10), "Sau clear phai cho tich lai du prebuffer");
    }

    @Test
    void constructorRejectsBadArgs() {
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(2, 3));
    }
}
