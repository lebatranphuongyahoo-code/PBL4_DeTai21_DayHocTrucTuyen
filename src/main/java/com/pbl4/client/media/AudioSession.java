package com.pbl4.client.media;

import javax.sound.sampled.LineUnavailableException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Gom DatagramSocket + AudioPlayer + AudioRecorder của MỘT lần vào lớp.
 *
 * <p>Lý do tồn tại: recorder và player BẮT BUỘC dùng chung một socket (server trừ người gửi theo
 * cặp IP:cổng — xem AudioRelayServer). Trước đây socket do HomeUI tạo nhưng ClassroomUI đóng;
 * giờ một đối tượng sở hữu trọn vòng đời.</p>
 */
public final class AudioSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final AudioPlayer player;
    private AudioRecorder recorder;
    private boolean closed;

    private AudioSession(DatagramSocket socket) {
        this.socket = socket;
        this.player = new AudioPlayer(socket, new JitterBuffer(), new MediaStats());
    }

    /** Bind cổng UDP ngẫu nhiên do hệ điều hành cấp. */
    public static AudioSession open() throws SocketException {
        return new AudioSession(new DatagramSocket(0));
    }

    /** Cổng phải báo cho server trong JOIN_ROOM_REQ. */
    public int localPort() {
        return socket.getLocalPort();
    }

    /** Mở loa ngay khi vào lớp (xem ghi chú a4d9580 trong README 3.2). */
    public synchronized void startPlayback() throws LineUnavailableException {
        player.start();
    }

    /** Gọi SAU khi đã gửi AUDIO_ON. */
    public synchronized void startMic(InetAddress server, int serverUdpPort, int roomId)
            throws LineUnavailableException {
        if (recorder == null) {
            recorder = new AudioRecorder(socket, server, serverUdpPort);
        }
        recorder.start(roomId);
    }

    public synchronized void stopMic() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    public synchronized boolean isMicOn() {
        return recorder != null && recorder.isRunning();
    }

    /** Dừng mic, loa, đóng socket. Gọi nhiều lần không lỗi. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopMic();
        player.stop();
        socket.close();
    }
}
