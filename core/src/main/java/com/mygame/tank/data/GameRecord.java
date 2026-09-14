package com.mygame.tank.data;

/**
 * Lưu kết quả một ván chơi.
 *
 * <ul>
 *   <li>{@link #result} — "WIN" hoặc "LOSE"</li>
 *   <li>{@link #durationSeconds} — tổng thời gian chơi (giây)</li>
 *   <li>{@link #timestamp} — thời điểm kết thúc ván (epoch millis)</li>
 * </ul>
 *
 * Phải có constructor mặc định (no-arg) để LibGDX Json deserialization hoạt động.
 */
public class GameRecord {

    public String result;          // "WIN" or "LOSE"
    public int    durationSeconds; // total play time in seconds
    public long   timestamp;       // epoch millis when the game ended

    /** No-arg constructor — required by LibGDX Json. */
    public GameRecord() {}

    public GameRecord(String result, int durationSeconds, long timestamp) {
        this.result          = result;
        this.durationSeconds = durationSeconds;
        this.timestamp       = timestamp;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Trả về chuỗi "mm:ss" từ {@link #durationSeconds}. */
    public String formattedTime() {
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    /**
     * Trả về chuỗi ngày giờ đọc được từ {@link #timestamp}.
     * Dùng java.text.SimpleDateFormat để tránh phụ thuộc ngoài.
     */
    public String formattedDate() {
        java.text.SimpleDateFormat sdf =
            new java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}
