package com.mygame.tank.dungeon;

import com.mygame.tank.entity.Tank;

import java.util.*;

/**
 * Quản lý tiến trình dọn phòng trong dungeon.
 * Thay thế {@link com.mygame.tank.world.AreaManager}.
 *
 * <h3>Logic:</h3>
 * <ul>
 *   <li>Phòng SPAWN luôn cleared ngay từ đầu.</li>
 *   <li>Phòng ENEMY: cleared khi tất cả quái đã chết → mở cửa sang phòng tiếp theo.</li>
 *   <li>Phòng BOSS: có cửa khóa (boss room door) đến khi boss chết.
 *       Sau khi boss chết VÀ tất cả mini-tank do boss sinh ra cũng chết
 *       → cleared → mở cửa sang phòng tiếp theo.</li>
 *   <li>Một phòng chỉ "active" (quái tấn công) khi phòng trước đã cleared.</li>
 * </ul>
 *
 * <h3>Win condition:</h3>
 * <p>Chỉ cần phòng BOSS <em>cuối cùng</em> (theo thứ tự tuyến tính) được cleared.
 * Không cần tất cả boss rooms đều cleared (phòng boss trước đã phải clear mới
 * đến được phòng boss cuối, nên condition này đủ).
 *
 * <h3>Entrance door — cơ chế khóa (đã sửa):</h3>
 * <p>Entrance door của một phòng CHƯA CLEAR mặc định LUÔN KHÓA ngay khi
 * phòng đó active (phòng trước đã clear) — {@link #isDoorOpen(String)} trả
 * false cho tới khi phòng được clear. Điều này chặn player bước vào SÂU vào
 * phòng kế tiếp (chỉ có thể áp sát cửa, bị đẩy lùi lại) cho tới khi dọn xong
 * phòng hiện tại.
 */
public class RoomProgressionManager {

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Quái được đăng ký theo room key (ROOM_N).
     * Với phòng BOSS: gồm boss + mini-tank sinh ra trong trận.
     */
    private final Map<String, List<Tank>> roomEnemies = new LinkedHashMap<>();

    /**
     * Boss được đăng ký theo door key (BOSS_ROOM_N).
     */
    private final Map<String, Tank> roomBosses = new LinkedHashMap<>();

    /**
     * Các phòng đã dọn sạch (theo roomKey).
     */
    private final Set<String> clearedRooms = new HashSet<>();

    /**
     * Danh sách door keys đang MỞ.
     */
    private final Set<String> openDoors = new HashSet<>();

    /**
     * Thứ tự các phòng (roomKey theo orderIndex tăng dần).
     */
    private final List<String> roomOrder = new ArrayList<>();

    /**
     * Loại phòng (SPAWN/ENEMY/BOSS) theo roomKey — biết trước từ lúc init(),
     * KHÔNG suy đoán qua việc có Tank được đăng ký hay không. Điều này tránh
     * bug: nếu 1 phòng BOSS vì lý do nào đó chưa kịp registerBoss() (ví dụ
     * spawn thất bại), phòng đó sẽ KHÔNG bị nhầm là "phòng trống" rồi tự
     * động cleared — nó phải đợi đúng điều kiện boss chết mới clear.
     */
    private final Map<String, Room.Type> roomTypes = new HashMap<>();

    /**
     * roomKey của phòng BOSS cuối cùng trong chuỗi tuyến tính.
     * Win khi phòng này được cleared.
     */
    private String lastBossRoomKey = null;

    /**
     * true khi phòng boss cuối cùng đã bị dọn sạch → game won.
     */
    private boolean gameWon = false;

    // ─── Setup ───────────────────────────────────────────────────────────────

    /**
     * Khởi tạo manager với danh sách phòng đã sắp xếp theo BFS.
     * Gọi 1 lần khi tạo GameWorld.
     */
    public void init(List<Room> rooms) {
        roomOrder.clear();
        clearedRooms.clear();
        openDoors.clear();
        roomTypes.clear();
        lastBossRoomKey = null;

        for (Room room : rooms) {
            String key = DungeonMap.roomKey(room);
            roomOrder.add(key);
            roomTypes.put(key, room.type);

            if (room.type == Room.Type.SPAWN) {
                // Phòng spawn luôn cleared.
                clearedRooms.add(key);
                openDoors.add("ENTRANCE_" + key);
            }

            if (room.type == Room.Type.BOSS) {
                // Track boss room cuối cùng — room list đã theo thứ tự BFS nên
                // phòng BOSS xuất hiện sau cùng là boss cuối của chuỗi tuyến tính.
                lastBossRoomKey = key;
            }
        }

        // Mở cửa (boss + entrance + access) của phòng NGAY SAU spawn, vì phòng spawn coi
        // như đã "clear" từ đầu nên phòng kế tiếp phải active/mở được ngay.
        if (roomOrder.size() > 1) {
            String firstKey = roomOrder.get(1);
            openDoors.add("BOSS_" + firstKey);
            openDoors.add("ENTRANCE_" + firstKey);
            openDoors.add(firstKey);
        }
    }

    /**
     * Đăng ký danh sách quái cho phòng ENEMY.
     */
    public void registerEnemies(String roomKey, List<Tank> enemies) {
        roomEnemies.put(roomKey, new ArrayList<>(enemies));
    }

    /**
     * Đăng ký boss cho phòng BOSS. Key = doorKey (BOSS_ROOM_N).
     */
    public void registerBoss(String doorKey, Tank boss) {
        roomBosses.put(doorKey, boss);
    }

    /**
     * Đăng ký mini-tank do boss spawn vào danh sách của phòng boss tương ứng.
     * Mini-tank phải chết hết (cùng boss) trước khi phòng boss được cleared.
     *
     * @param roomKey   roomKey của phòng boss (ROOM_N, không phải BOSS_ROOM_N)
     * @param miniTanks danh sách mini-tank vừa spawn
     */
    public void registerMiniTanks(String roomKey, List<Tank> miniTanks) {
        roomEnemies.computeIfAbsent(roomKey, k -> new ArrayList<>()).addAll(miniTanks);
    }

    // ─── Update (gọi mỗi frame) ───────────────────────────────────────────────

    /**
     * Kiểm tra tiến trình dọn phòng và mở cửa tương ứng.
     * Gọi sau khi cập nhật tất cả quái/boss.
     */
    public void update() {
        for (int i = 0; i < roomOrder.size(); i++) {
            String key = roomOrder.get(i);
            if (clearedRooms.contains(key)) continue;

            // Chỉ kiểm tra nếu phòng trước đã clear
            if (i > 0 && !clearedRooms.contains(roomOrder.get(i - 1))) continue;

            Room.Type type = roomTypes.get(key);
            if (type == null) continue; // dữ liệu thiếu — an toàn: không clear

            switch (type) {
                case SPAWN:
                    // Không nên tới đây (SPAWN đã cleared sẵn ở init()), nhưng
                    // giữ nhánh để rõ ràng, tường minh về mọi trường hợp.
                    clearRoom(key, i);
                    break;

                case ENEMY:
                    if (isRoomEnemiesCleared(key)) {
                        clearRoom(key, i);
                    }
                    break;

                case BOSS:
                    // Phòng BOSS CHỈ được clear khi boss đã đăng ký, đã chết,
                    // VÀ mọi mini-tank cũng chết. Nếu boss chưa được đăng ký
                    // (registerBoss() chưa gọi tới, ví dụ do lỗi spawn), phòng
                    // này PHẢI đứng yên — không được tự động coi là "trống rồi
                    // clear luôn" như logic cũ (đó chính là nguồn gốc bug
                    // khiến game kết thúc sớm sau khi mới hạ boss thứ 2/3).
                    if (isBossRoomCleared(key)) {
                        clearRoom(key, i);
                    }
                    break;
            }
        }

        // Win condition: phòng boss CUỐI CÙNG trong chuỗi tuyến tính được cleared
        if (!gameWon && lastBossRoomKey != null && clearedRooms.contains(lastBossRoomKey)) {
            gameWon = true;
        }
    }

    /**
     * Phòng ENEMY được coi là dọn sạch khi có danh sách quái đăng ký VÀ
     * tất cả đã chết. Nếu chưa có gì đăng ký (chưa kịp registerEnemies),
     * trả về false — chờ, không tự clear.
     */
    private boolean isRoomEnemiesCleared(String roomKey) {
        List<Tank> enemiesList = roomEnemies.get(roomKey);
        return enemiesList != null && !enemiesList.isEmpty()
            && enemiesList.stream().noneMatch(Tank::isAlive);
    }

    /**
     * Phòng BOSS được coi là dọn sạch khi boss đã đăng ký, đã chết, và mọi
     * mini-tank (nếu có) sinh ra trong trận cũng đã chết.
     */
    private boolean isBossRoomCleared(String roomKey) {
        Tank boss = roomBosses.get("BOSS_" + roomKey);
        if (boss == null || boss.isAlive()) return false;

        List<Tank> miniTanks = roomEnemies.get(roomKey);
        return miniTanks == null || miniTanks.stream().noneMatch(Tank::isAlive);
    }

    private void clearRoom(String roomKey, int index) {
        clearedRooms.add(roomKey);

        // Mở entrance door của phòng vừa clear — player được tự do đi lại
        // trong phòng này và tiến sang phòng kế tiếp.
        openDoors.add("ENTRANCE_" + roomKey);

        // Mở cửa sang phòng tiếp theo
        if (index + 1 < roomOrder.size()) {
            String nextKey = roomOrder.get(index + 1);
            openDoors.add("ENTRANCE_" + nextKey); // mở cửa vào phòng tiếp theo
            openDoors.add("BOSS_" + nextKey);     // mở cửa boss của phòng tiếp (nếu là phòng boss)
            openDoors.add(nextKey);               // cũng mở "access door" để quái active
        }
        // Mở cửa boss của phòng vừa clear (nếu là boss room)
        openDoors.add("BOSS_" + roomKey);
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    /**
     * Cửa có đang mở không? Dùng bởi CollisionSystem và GameRenderer.
     * Key = "BOSS_ROOM_N" (doorKey của boss room) hoặc "ENTRANCE_ROOM_N".
     *
     * <p>Entrance door của một phòng CHƯA CLEAR mặc định LUÔN ĐÓNG — không cần
     * chờ player bước vào rồi mới khóa (khác hành vi cũ). Điều này chặn được
     * player chạy xuyên nhiều phòng trong 1 frame trước khi bị khóa lại.
     * Cửa chỉ mở khi {@link #clearRoom} được gọi cho đúng phòng đó.
     */
    public boolean isDoorOpen(String doorKey) {
        return openDoors.contains(doorKey);
    }

    /**
     * Phòng có đang "active" không (quái trong phòng được phép hoạt động)?
     * Phòng active khi tất cả phòng trước đó đã cleared.
     *
     * @param roomKey "ROOM_N"
     */
    public boolean isRoomActive(String roomKey) {
        int idx = roomOrder.indexOf(roomKey);
        if (idx <= 0) return true; // phòng đầu luôn active
        // Active khi phòng trước đã clear
        return clearedRooms.contains(roomOrder.get(idx - 1));
    }

    /**
     * @return true khi phòng boss cuối cùng đã bị dọn sạch → win condition.
     */
    public boolean isGameWon() {
        return gameWon;
    }

    /**
     * @return true khi phòng này đã được dọn sạch.
     */
    public boolean isRoomCleared(String roomKey) {
        return clearedRooms.contains(roomKey);
    }

    /**
     * Lấy boss đã đăng ký theo doorKey.
     * Dùng bởi GameRenderer để hiển thị health bar boss.
     */
    public Collection<Tank> getAllBosses() {
        return roomBosses.values();
    }

    /**
     * Debug helper: liệt kê các phòng BOSS đã active (phòng trước đã clear)
     * nhưng CHƯA có boss đăng ký. Bình thường danh sách này phải rỗng —
     * nếu không rỗng nghĩa là {@code GameWorld.spawnAllEntities()} đã bỏ sót
     * việc spawn/registerBoss cho phòng đó, và progression sẽ bị kẹt vĩnh
     * viễn tại phòng này (đây là hành vi ĐÚNG sau khi sửa bug — thà kẹt và
     * lộ lỗi rõ ràng còn hơn im lặng auto-clear và kết thúc game sai).
     */
    public List<String> getActiveBossRoomsMissingBoss() {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < roomOrder.size(); i++) {
            String key = roomOrder.get(i);
            if (roomTypes.get(key) != Room.Type.BOSS) continue;
            if (clearedRooms.contains(key)) continue;
            boolean prevCleared = (i == 0) || clearedRooms.contains(roomOrder.get(i - 1));
            if (prevCleared && !roomBosses.containsKey("BOSS_" + key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}
