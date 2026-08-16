package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

import java.util.*;

/**
 * Thay thế MapManager — quản lý dungeon được sinh procedurally bằng BSP.
 *
 * <p>Expose cùng interface mà {@link com.mygame.tank.world.GameWorld} và
 * {@link com.mygame.tank.render.GameRenderer} cần:
 * <ul>
 *   <li>{@link #getCollisionRects()} — danh sách tường cho CollisionSystem</li>
 *   <li>{@link #getDoorRects()}      — cửa khóa trước phòng boss</li>
 *   <li>{@link #getPlayerSpawn()}    — vị trí spawn player (tâm phòng 0)</li>
 *   <li>{@link #getEnemySpawns()}    — Map&lt;roomId, List&lt;Vector2&gt;&gt;</li>
 *   <li>{@link #getBossSpawns()}     — vị trí spawn boss theo thứ tự phòng</li>
 *   <li>{@link #getRooms()}          — danh sách phòng (đã sắp xếp BFS)</li>
 *   <li>{@link #getCorridors()}      — danh sách hành lang</li>
 *   <li>{@link #getTileMap()}        — lưới tile để renderer vẽ texture</li>
 * </ul>
 *
 * <p>Cửa (Door) được đặt ở giữa hành lang nối vào phòng BOSS, khóa cho đến khi
 * boss phòng đó chết. Key của doorRects = "ROOM_" + orderIndex của phòng boss.
 */
public class DungeonMap {

    // ─── Core data ────────────────────────────────────────────────────────────
    private List<Room> rooms = Collections.emptyList();
    private List<Corridor> corridors = Collections.emptyList();
    private DungeonTileMap tileMap;

    // ─── Derived data (built after generate) ─────────────────────────────────
    private Vector2 playerSpawn;
    private final Map<String, List<Vector2>> enemySpawns = new LinkedHashMap<>();
    private final List<Vector2> bossSpawns = new ArrayList<>();

    /**
     * doorRects: key = "ROOM_N" (N = orderIndex của boss room),
     * value = Rectangle chặn ngang hành lang vào boss room đó.
     */
    private final Map<String, Rectangle> doorRects = new LinkedHashMap<>();

    /**
     * entranceDoorRects: cửa đầu vào mỗi phòng ENEMY và BOSS (phía trước phòng đó,
     * ngăn player chạy khỏi phòng trước khi clear).
     * Key = "ENTRANCE_ROOM_N" với N = orderIndex.
     */
    private final Map<String, Rectangle> entranceDoorRects = new LinkedHashMap<>();

    // ─── Generate ─────────────────────────────────────────────────────────────

    /**
     * Sinh dungeon mới với seed ngẫu nhiên.
     * Gọi 1 lần khi bắt đầu màn chơi.
     *
     * @param seed random seed (dùng System.currentTimeMillis() để luôn khác nhau)
     */
    public void generate(long seed) {
        BSPDungeonGenerator gen = new BSPDungeonGenerator(seed);
        gen.generate(GameConfig.MAP_COLS, GameConfig.MAP_ROWS);

        this.rooms = new ArrayList<>(gen.getRooms());
        this.corridors = new ArrayList<>(gen.getCorridors());

        // Build tile map (grid 2D + collision rects)
        tileMap = new DungeonTileMap();
        tileMap.build(rooms, corridors);

        // Trích xuất spawn data
        extractSpawnData();

        // Tạo cửa khóa trước mỗi phòng boss + entrance door mỗi phòng
        buildDoorRects();
        buildEntranceDoorRects();
    }

    // ─── Spawn data extraction ────────────────────────────────────────────────

    private void extractSpawnData() {
        enemySpawns.clear();
        bossSpawns.clear();
        playerSpawn = null;

        for (Room room : rooms) {
            switch (room.type) {
                case SPAWN:
                    playerSpawn = room.getCenter();
                    break;
                case ENEMY:
                    if (!room.enemySpawnPoints.isEmpty()) {
                        enemySpawns.put(roomKey(room), new ArrayList<>(room.enemySpawnPoints));
                    }
                    break;
                case BOSS:
                    if (room.bossSpawnPoint != null) {
                        bossSpawns.add(new Vector2(room.bossSpawnPoint));
                    }
                    break;
            }
        }

        // Fallback nếu không tìm được phòng spawn
        if (playerSpawn == null) {
            playerSpawn = new Vector2(GameConfig.MAP_TILE_SIZE * 3f, GameConfig.MAP_TILE_SIZE * 3f);
        }
    }

    // ─── Door creation ────────────────────────────────────────────────────────

    /**
     * Tạo door slice (dày 1 tile, khớp chiều rộng hành lang) đặt NGAY TẠI
     * RÀO CHẮN / MIỆNG PHÒNG ĐÍCH ({@code roomB}).
     *
     * <p>Nguyên nhân bug trước đây:
     * Hành lang chữ L kéo dài từ tâm phòng A tới tận tâm phòng B. Logic cũ
     * lấy điểm cuối của segment hành lang (nằm ở tâm phòng B) rồi lùi 1 tile,
     * khiến cửa rơi vào chính giữa phòng thay vì ở ranh giới tường ngoài.
     *
     * <p>Cách tính chuẩn:
     * Duyệt các đoạn segment của hành lang (ưu tiên từ cuối về đầu vì hành lang
     * nối tới roomB). Tìm segment nào cắt qua đúng 1 trong 4 cạnh ranh giới của
     * {@code roomB.bounds} (Bottom, Top, Left, Right) và đặt rectangle cửa dày
     * đúng 1 tile ngay tại vị trí mép tường ranh giới bị đục lỗ đó.
     */
    private Rectangle createCorridorDoorRect(Corridor corr, Room roomA, Room roomB) {
        if (corr == null || corr.segments.isEmpty()) return null;

        int tileSize = GameConfig.MAP_TILE_SIZE;
        Rectangle target = roomB.bounds;

        // Duyệt từ segment cuối về đầu (vì segment cuối kết thúc ở tâm roomB)
        for (int i = corr.segments.size() - 1; i >= 0; i--) {
            Rectangle seg = corr.segments.get(i);

            // 1. Cắt qua cạnh dưới của phòng (Bottom edge: y = target.y)
            if (seg.height >= seg.width || seg.width <= GameConfig.CORRIDOR_WIDTH_TILES * tileSize + 1) {
                boolean xOverlap = (seg.x + seg.width > target.x) && (seg.x < target.x + target.width);
                boolean crossesBottom = (seg.y < target.y) && (seg.y + seg.height > target.y);
                if (xOverlap && crossesBottom) {
                    float doorX = Math.round(seg.x / tileSize) * tileSize;
                    float doorY = target.y - tileSize;
                    return new Rectangle(doorX, doorY, seg.width, tileSize);
                }

                // 2. Cắt qua cạnh trên của phòng (Top edge: y = target.y + target.height)
                boolean crossesTop = (seg.y < target.y + target.height) && (seg.y + seg.height > target.y + target.height);
                if (xOverlap && crossesTop) {
                    float doorX = Math.round(seg.x / tileSize) * tileSize;
                    float doorY = target.y + target.height;
                    return new Rectangle(doorX, doorY, seg.width, tileSize);
                }
            }

            // 3. Cắt qua cạnh trái của phòng (Left edge: x = target.x)
            if (seg.width >= seg.height || seg.height <= GameConfig.CORRIDOR_WIDTH_TILES * tileSize + 1) {
                boolean yOverlap = (seg.y + seg.height > target.y) && (seg.y < target.y + target.height);
                boolean crossesLeft = (seg.x < target.x) && (seg.x + seg.width > target.x);
                if (yOverlap && crossesLeft) {
                    float doorX = target.x - tileSize;
                    float doorY = Math.round(seg.y / tileSize) * tileSize;
                    return new Rectangle(doorX, doorY, tileSize, seg.height);
                }

                // 4. Cắt qua cạnh phải của phòng (Right edge: x = target.x + target.width)
                boolean crossesRight = (seg.x < target.x + target.width) && (seg.x + seg.width > target.x + target.width);
                if (yOverlap && crossesRight) {
                    float doorX = target.x + target.width;
                    float doorY = Math.round(seg.y / tileSize) * tileSize;
                    return new Rectangle(doorX, doorY, tileSize, seg.height);
                }
            }
        }

        // Fallback an toàn nếu không tìm thấy giao điểm cạnh (không nên xảy ra)
        Rectangle lastSeg = corr.segments.get(corr.segments.size() - 1);
        boolean horizontal = lastSeg.width >= lastSeg.height;
        if (horizontal) {
            float doorX = (lastSeg.x + lastSeg.width / 2f < target.x + target.width / 2f)
                ? target.x - tileSize : target.x + target.width;
            return new Rectangle(doorX, lastSeg.y, tileSize, lastSeg.height);
        } else {
            float doorY = (lastSeg.y + lastSeg.height / 2f < target.y + target.height / 2f)
                ? target.y - tileSize : target.y + target.height;
            return new Rectangle(lastSeg.x, doorY, lastSeg.width, tileSize);
        }
    }


    /**
     * Tạo rectangle "cửa" chặn ngang hành lang dẫn vào mỗi phòng BOSS.
     * Cửa nằm ở giữa hành lang giữa phòng trước và phòng boss.
     */
    private void buildDoorRects() {
        doorRects.clear();

        for (Room bossRoom : rooms) {
            if (bossRoom.type != Room.Type.BOSS) continue;

            int prevIndex = bossRoom.orderIndex - 1;
            Room prevRoom = getRoomAtIndex(prevIndex);
            if (prevRoom == null) continue;

            for (Corridor corr : corridors) {
                boolean connects = (corr.roomA.id == bossRoom.id && corr.roomB.id == prevRoom.id)
                    || (corr.roomB.id == bossRoom.id && corr.roomA.id == prevRoom.id);
                if (!connects) continue;

                Rectangle doorRect = createCorridorDoorRect(corr, prevRoom, bossRoom);
                if (doorRect != null) {
                    doorRects.put(doorKey(bossRoom), doorRect);
                }
                break;
            }
        }
    }

    /**
     * Tạo entrance door cho mỗi phòng ENEMY: nằm ở giữa đoạn corridor
     * nối giữa phòng trước và phòng đó.
     * Key = "ENTRANCE_ROOM_N".
     */
    private void buildEntranceDoorRects() {
        entranceDoorRects.clear();

        for (Room room : rooms) {
            if (room.type == Room.Type.SPAWN || room.type == Room.Type.BOSS) continue;
            int prevIndex = room.orderIndex - 1;
            Room prevRoom = getRoomAtIndex(prevIndex);
            if (prevRoom == null) continue;

            for (Corridor corr : corridors) {
                boolean connects = (corr.roomA.id == room.id && corr.roomB.id == prevRoom.id)
                    || (corr.roomB.id == room.id && corr.roomA.id == prevRoom.id);
                if (!connects) continue;

                Rectangle doorRect = createCorridorDoorRect(corr, prevRoom, room);
                if (doorRect != null) {
                    entranceDoorRects.put(entranceKey(room), doorRect);
                }
                break;
            }
        }
    }

    /**
     * Key entrance door: "ENTRANCE_ROOM_N".
     */
    public static String entranceKey(Room room) {
        return "ENTRANCE_ROOM_" + room.orderIndex;
    }

    /**
     * Key cho enemy spawn map: "ROOM_N" với N = orderIndex.
     */
    public static String roomKey(Room room) {
        return "ROOM_" + room.orderIndex;
    }

    /**
     * Key cho door map: "BOSS_ROOM_N" với N = orderIndex.
     */
    public static String doorKey(Room room) {
        return "BOSS_ROOM_" + room.orderIndex;
    }

    // ─── Getters (compatible với interface cũ) ────────────────────────────────

    /**
     * Entrance door rects: key = "ENTRANCE_ROOM_N".
     */
    public Map<String, Rectangle> getEntranceDoorRects() {
        return entranceDoorRects;
    }

    /**
     * Danh sách tường (collision) cho {@link com.mygame.tank.world.CollisionSystem}.
     */
    public List<Rectangle> getCollisionRects() {
        return tileMap != null ? tileMap.getCollisionRects() : Collections.emptyList();
    }

    /**
     * Danh sách collision rects của riêng cover blocks (đã nằm trong
     * {@link #getCollisionRects()} để va chạm hoạt động, nhưng tách riêng
     * ở đây để {@link com.mygame.tank.render.GameRenderer} có thể vẽ bằng
     * texture cover.png thay vì wall.png).
     */
    public List<Rectangle> getCoverRects() {
        return tileMap != null ? tileMap.getCoverCollisionRects() : Collections.emptyList();
    }

    /**
     * Cửa boss: key = doorKey(bossRoom), value = Rectangle chặn hành lang.
     * Dùng bởi CollisionSystem và GameRenderer.
     */
    public Map<String, Rectangle> getDoorRects() {
        return doorRects;
    }

    /**
     * Không còn dùng AreaBounds (replaced bởi Room.bounds).
     */
    public Map<String, Rectangle> getAreaBounds() {
        return Collections.emptyMap();
    }

    /**
     * Vị trí spawn player (tâm phòng SPAWN).
     */
    public Vector2 getPlayerSpawn() {
        return playerSpawn;
    }

    /**
     * Vị trí spawn quái theo phòng.
     * Key = "ROOM_N" với N là orderIndex của phòng ENEMY.
     */
    public Map<String, List<Vector2>> getEnemySpawns() {
        return enemySpawns;
    }

    /**
     * Vị trí spawn boss theo thứ tự phòng BOSS (index 0, 1, 2...).
     * GameWorld dùng index để lấy spawn position tương ứng.
     */
    public List<Vector2> getBossSpawns() {
        return bossSpawns;
    }

    /**
     * Danh sách phòng đã sắp xếp theo BFS từ spawn.
     */
    public List<Room> getRooms() {
        return rooms;
    }

    /**
     * Lấy phòng theo index thứ tự.
     */
    public Room getRoomAtIndex(int index) {
        if (index < 0 || index >= rooms.size()) return null;
        for (Room r : rooms) if (r.orderIndex == index) return r;
        return null;
    }

    /**
     * Danh sách hành lang.
     */
    public List<Corridor> getCorridors() {
        return corridors;
    }

    /**
     * Lưới tile 2D (dùng bởi renderer).
     */
    public DungeonTileMap getTileMap() {
        return tileMap;
    }

    /**
     * Trả về Room chứa vị trí pixel (wx, wy), hoặc null nếu không trong phòng nào.
     * Dùng để xác định player đang ở phòng nào.
     */
    public Room getRoomAt(float wx, float wy) {
        for (Room room : rooms) {
            if (room.bounds.contains(wx, wy)) return room;
        }
        return null;
    }

    /**
     * Dummy dispose (không cần giải phóng gì).
     */
    public void dispose() { /* không có Texture native ở đây */ }
}
