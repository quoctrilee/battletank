package com.mygame.tank.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

/**
 * Quản lý lịch sử ván chơi — đọc/ghi file {@code game_history.json} ở thư mục local.
 *
 * <ul>
 *   <li>Giới hạn tối đa {@value #MAX_RECORDS} bản ghi gần nhất.</li>
 *   <li>Dùng {@link Json} của LibGDX — không cần thư viện ngoài.</li>
 *   <li>Singleton nhẹ: tạo instance mới mỗi lần cần, không giữ state toàn cục.</li>
 * </ul>
 */
public class GameHistoryManager {

    private static final String FILE_NAME  = "game_history.json";
    private static final int    MAX_RECORDS = 50;

    private final Json json = new Json();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Lưu một bản ghi mới vào đầu danh sách.
     * Nếu vượt quá {@value #MAX_RECORDS}, bản ghi cũ nhất sẽ bị xóa.
     */
    public void save(GameRecord record) {
        Array<GameRecord> records = loadAll();
        records.insert(0, record);
        if (records.size > MAX_RECORDS) {
            records.truncate(MAX_RECORDS);
        }
        write(records);
    }

    /**
     * Trả về toàn bộ danh sách lịch sử (mới nhất trước).
     * Nếu file chưa tồn tại hoặc lỗi parse, trả về danh sách rỗng.
     */
    @SuppressWarnings("unchecked")
    public Array<GameRecord> loadAll() {
        FileHandle fh = Gdx.files.local(FILE_NAME);
        if (!fh.exists()) return new Array<>();
        try {
            Array<GameRecord> records = json.fromJson(Array.class, GameRecord.class, fh);
            return records != null ? records : new Array<>();
        } catch (Exception e) {
            Gdx.app.error("GameHistoryManager", "Failed to parse history file", e);
            return new Array<>();
        }
    }

    /** Xóa toàn bộ lịch sử. */
    public void clear() {
        FileHandle fh = Gdx.files.local(FILE_NAME);
        if (fh.exists()) fh.delete();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void write(Array<GameRecord> records) {
        try {
            FileHandle fh = Gdx.files.local(FILE_NAME);
            fh.writeString(json.toJson(records, Array.class, GameRecord.class), false);
        } catch (Exception e) {
            Gdx.app.error("GameHistoryManager", "Failed to write history file", e);
        }
    }
}
