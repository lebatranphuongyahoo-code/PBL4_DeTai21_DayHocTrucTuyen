package com.pbl4.client.media;

import com.pbl4.client.ui.ClassroomUI;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * LỚP LẮP GHÉP — nối bộ máy media của TV2 vào giao diện của TV3.
 *
 * <h2>Vì sao cần lớp này</h2>
 * <p>Ba lớp {@link AudioPlayer}, {@link AudioRecorder}, {@link ScreenCapturer} đều đã hoàn thành
 * và tự kiểm tra đạt, nhưng trước đây <b>không lớp nào được giao diện gọi tới</b>: giao diện chỉ
 * gửi gói tin đi, còn "động cơ" media thì nằm im. Kết quả là bấm nút Mic thì nút bị khoá, bấm
 * chia sẻ màn hình thì server có báo "đang chia sẻ" mà không có frame nào được chụp.</p>
 *
 * <p>Lớp này giữ đúng một bộ đối tượng media cho mỗi lần vào lớp và cài đặt ba interface mà
 * {@link ClassroomUI} đã chừa sẵn, nên giao diện không phải biết gì về chi tiết kỹ thuật.</p>
 *
 * <h2>Ba ràng buộc bắt buộc (nếu vi phạm là hỏng ngay)</h2>
 * <ol>
 *   <li><b>Chung một {@link DatagramSocket}</b> cho cả thu và phát. Server nhận diện người gửi
 *       theo cặp (IP, cổng UDP nguồn) đã đăng ký trong {@code JOIN_ROOM_REQ}; dùng hai socket
 *       khác cổng thì server không loại được chính bạn ra khỏi danh sách nhận
 *       ⇒ bạn nghe lại giọng của mình.</li>
 *   <li><b>Bật loa trước, bật mic sau.</b> {@link #start()} mở loa và trả về cổng UDP để giao
 *       diện gửi {@code AUDIO_ON}; chỉ sau đó {@link #start(String, int, int)} mới được gọi.
 *       Ngược thứ tự thì server chưa biết cổng của bạn và bỏ hết gói thoại.</li>
 *   <li><b>Gửi {@code SCREEN_START} trước khi chụp.</b> {@code ClientHandler.handleScreenFrame}
 *       chỉ chuyển tiếp frame khi cờ chia sẻ của lớp đang bật, nên frame gửi quá sớm bị bỏ.</li>
 * </ol>
 *
 * @see ClassroomUI.AudioPlayerControl
 * @see ClassroomUI.AudioRecorderControl
 * @see ClassroomUI.ScreenShareControl
 */
public class MediaController implements ClassroomUI.AudioPlayerControl,
        ClassroomUI.AudioRecorderControl,
        ClassroomUI.ScreenShareControl {

    /** Socket UDP dùng chung cho cả thu và phát thoại (ràng buộc 1). */
    private final DatagramSocket audioSocket;

    /** Nơi nhận từng frame JPEG đã nén; lớp giao diện sẽ gói vào {@code SCREEN_FRAME}. */
    private final Consumer<byte[]> jpegSender;

    /** Bộ đếm dùng chung cho cả ba lớp — cũng là số liệu đưa vào báo cáo. */
    private final MediaStats stats = new MediaStats();

    /** Đệm chống giật mặc định: sức chứa 10 gói, chờ 3 gói (~60 ms) rồi mới phát. */
    private final JitterBuffer jitterBuffer = new JitterBuffer();

    private AudioPlayer player;
    private AudioRecorder recorder;
    private ScreenCapturer capturer;

    /**
     * @param audioSocket socket UDP đã bind cổng ngẫu nhiên, dùng chung với giao diện
     * @param jpegSender  hàm nhận frame JPEG; thường là {@code jpeg -> connection.send(SCREEN_FRAME, jpeg)}
     */
    public MediaController(DatagramSocket audioSocket, Consumer<byte[]> jpegSender) {
        this.audioSocket = Objects.requireNonNull(audioSocket, "audioSocket");
        this.jpegSender = Objects.requireNonNull(jpegSender, "jpegSender");
    }

    // ==================================================================
    // PHÍA PHÁT (loa) — ClassroomUI.AudioPlayerControl
    // ==================================================================

    /**
     * Mở loa và ba luồng của {@link AudioPlayer}, rồi trả về cổng UDP đã bind.
     *
     * <p>{@code AudioPlayer.start()} không trả về cổng (nó dùng socket có sẵn), nên lớp lắp ghép
     * lấy {@code socket.getLocalPort()} để giao diện gửi kèm {@code AUDIO_ON}. Đây chính là cổng
     * mà server sẽ dùng để nhận diện bạn.</p>
     */
    @Override
    public int start() throws Exception {
        if (player == null) {
            player = new AudioPlayer(audioSocket, jitterBuffer, stats);
        }
        player.start();
        return audioSocket.getLocalPort();
    }

    // ==================================================================
    // PHÍA THU (mic) — ClassroomUI.AudioRecorderControl
    // ==================================================================

    /** Mở mic và bắt đầu đẩy gói 648 byte lên server (ràng buộc 2). */
    @Override
    public void start(String serverIp, int serverPort, int roomId) throws Exception {
        if (recorder == null) {
            recorder = new AudioRecorder(audioSocket, InetAddress.getByName(serverIp), serverPort);
        }
        recorder.start(roomId);
    }

    /**
     * Tắt cả thu lẫn phát.
     *
     * <p>Hai interface cha đều khai báo {@code void stop()} với cùng chữ ký nên Java gộp thành
     * một hàm duy nhất — không thể tách. Điều này không sai vì {@code ClassroomUI} luôn gọi
     * {@code audioRecorder.stop()} và {@code audioPlayer.stop()} ngay cạnh nhau, nên dừng chung
     * đúng bằng ý định của giao diện. Hàm gọi lại nhiều lần vẫn an toàn.</p>
     */
    @Override
    public void stop() {
        if (recorder != null) {
            recorder.stop();
        }
        if (player != null) {
            player.stop();
        }
    }

    // ==================================================================
    // CHIA SẺ MÀN HÌNH — ClassroomUI.ScreenShareControl
    // ==================================================================

    /**
     * Bật chụp màn hình 10 FPS, JPEG chất lượng 0.6, bỏ frame trùng.
     *
     * <p>Phải được gọi <b>sau</b> khi gói {@code SCREEN_START} đã gửi lên server (ràng buộc 3).</p>
     */
    @Override
    public void startCapture() throws Exception {
        if (capturer == null) {
            capturer = new ScreenCapturer(jpegSender, stats);
        }
        capturer.start();
    }

    /** Dừng chụp. Gọi trước khi gửi {@code SCREEN_STOP} để không còn frame bay đi sau đó. */
    @Override
    public void stopCapture() {
        if (capturer != null) {
            capturer.stop();
        }
    }

    // ==================================================================
    // TRUY VẤN TRẠNG THÁI — dùng cho giao diện và cho phần đo đạc
    // ==================================================================

    /** Bộ đếm dùng chung: số frame, số gói thoại, số gói mất… */
    public MediaStats stats() {
        return stats;
    }

    /** Đệm chống giật đang dùng — cần cho thí nghiệm Chặng 4. */
    public JitterBuffer jitterBuffer() {
        return jitterBuffer;
    }

    /** {@code null} nếu chưa từng bật mic. */
    public AudioPlayer player() {
        return player;
    }

    /** {@code null} nếu chưa từng bật mic. */
    public AudioRecorder recorder() {
        return recorder;
    }

    /** {@code null} nếu chưa từng chia sẻ màn hình. */
    public ScreenCapturer capturer() {
        return capturer;
    }

    /** Cổng UDP thực tế của client — cổng mà server dùng để nhận diện bạn. */
    public int localUdpPort() {
        return audioSocket.getLocalPort();
    }

    /**
     * Đóng mọi thứ khi rời lớp. Không ném exception: đây là đường dọn dẹp.
     */
    public void close() {
        stopCapture();
        stop();
        try {
            // Socket do giao diện tạo và cũng do giao diện đóng (HomeUI/ClassroomUI),
            // nên ở đây chỉ dừng thiết bị, không đóng socket.
            if (player != null && player.isRunning()) {
                player.stop();
            }
        } catch (RuntimeException ignored) {
            // dọn dẹp thì không được làm sập giao diện
        }
    }

    /**
     * Gửi một frame JPEG lên server — tách riêng để nơi gọi không phải tự bắt {@link IOException}.
     *
     * @return {@code true} nếu gửi được; {@code false} khi mất kết nối
     */
    public boolean sendFrame(byte[] jpeg) {
        try {
            jpegSender.accept(jpeg);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
