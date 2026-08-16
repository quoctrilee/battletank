package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

import java.util.*;

/**
 * Thuật toán sinh dungeon ngẫu nhiên dùng BSP Tree (Binary Space Partitioning)
 * để đặt vị trí phòng, nhưng nối hành lang theo CHUỖI TUYẾN TÍNH (không dùng
 * cây BSP để tạo hành lang — tránh sinh nhiều lối đi đan xen).
 *
 * <h3>Các bước:</h3>
 * <ol>
 *   <li>Chia đệ quy toàn bộ không gian (60×60 tile) thành 8 vùng lá (depth 3).</li>
 *   <li>Tạo 1 phòng (Room) trong mỗi vùng lá với kích thước ngẫu nhiên + margin.</li>
 *   <li>Sắp xếp phòng thành 1 chuỗi tuyến tính bằng nearest-neighbor chain,
 *       xuất phát từ phòng gần góc bottom-left nhất (spawn).</li>
 *   <li>Gán loại phòng theo thứ tự cố định: SPAWN, ENEMY, ENEMY, BOSS,
 *       ENEMY, ENEMY, BOSS, BOSS.</li>
 *   <li>Nối hành lang chữ L giữa MỖI CẶP PHÒNG LIỀN KỀ trong chuỗi
 *       (0↔1, 1↔2, ...) — đúng {@code rooms.size() - 1} hành lang, mỗi
 *       phòng chỉ có tối đa 2 lối ra (trước/sau), không rẽ nhánh.</li>
 *   <li>Đặt spawn point quái/boss trong từng phòng.</li>
 * </ol>
 */
public class BSPDungeonGenerator {

    // ─── Hằng số từ GameConfig ────────────────────────────────────────────────
    private static final int TILE = GameConfig.MAP_TILE_SIZE;
    private static final int CORR = GameConfig.CORRIDOR_WIDTH_TILES * TILE;
    private static final int HALF    = CORR / 2;
    private static final int MAX_SEG = GameConfig.MAX_CORRIDOR_LENGTH_TILES * TILE;

    // Thứ tự loại phòng cố định: SPAWN → ENEMY × 2 → BOSS → ENEMY × 2 → BOSS → BOSS
    private static final Room.Type[] TYPE_ORDER = {
        Room.Type.SPAWN,
        Room.Type.ENEMY, Room.Type.ENEMY,
        Room.Type.BOSS,
        Room.Type.ENEMY, Room.Type.ENEMY,
        Room.Type.BOSS,
        Room.Type.BOSS
    };

    // ─── State ────────────────────────────────────────────────────────────────
    private final Random random;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Corridor> corridors = new ArrayList<>();
    private BSPNode root;

    /**
     * @param seed random seed — truyền System.currentTimeMillis() để luôn mới.
     */
    public BSPDungeonGenerator(long seed) {
        this.random = new Random(seed);
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    /**
     * Sinh dungeon trên grid totalCols × totalRows tile.
     * Kết quả được lưu trong {@link #getRooms()} và {@link #getCorridors()}.
     */
    public void generate(int totalCols, int totalRows) {
        rooms.clear();
        corridors.clear();

        float totalW = totalCols * TILE;
        float totalH = totalRows * TILE;
        root = new BSPNode(new Rectangle(0, 0, totalW, totalH));

        // Bước 1: Chia đệ quy đến độ sâu tối đa
        splitNode(root, 0);

        // Bước 2: Tạo phòng trong mỗi lá
        int[] counter = {0};
        buildRooms(root, counter);

        // Bước 3: Xếp thứ tự phòng theo BFS từ phòng spawn (dùng đồ thị
        // liền kề tạm thời suy ra từ cấu trúc cây BSP, KHÔNG phải hành lang
        // thật — xem orderRoomsByBfs()).
        orderRoomsByBfs();

        // Bước 4: Gán loại phòng theo orderIndex vừa có
        assignTypes();

        // Bước 5: Nối hành lang TUYẾN TÍNH — mỗi phòng chỉ nối với đúng
        // phòng liền trước nó theo orderIndex (0-1, 1-2, 2-3, ...). Đây là
        // điểm khác biệt cốt lõi so với bản BSP-tree cũ: bản cũ tạo 1 hành
        // lang tại MỖI node nội bộ của cây BSP (spanning tree toàn cây),
        // sinh ra nhiều lối đi đan xen. Giờ ta bỏ hẳn cách đó và chỉ nối
        // theo chuỗi chơi tuyến tính đã xác định ở BFS order.
        buildLinearCorridors();

        // Bước 6: Đặt spawn points
        placeSpawnPoints();

        // Bước 7: Sinh khối cover ngẫu nhiên trong từng phòng ENEMY/BOSS
        placeCoverBlocks();
    }

    // ─── Bước 1: Chia đệ quy ─────────────────────────────────────────────────

    private void splitNode(BSPNode node, int depth) {
        if (depth >= GameConfig.BSP_MAX_DEPTH) return;

        // Kích thước tối thiểu mỗi vùng sau khi chia:
        // phòng (min) + 2 × margin + hành lang, cộng thêm đệm
        float minSectionSize = (GameConfig.ROOM_MIN_TILES + 2 * GameConfig.ROOM_MARGIN_TILES + 2) * TILE;

        // Chọn hướng chia: ưu tiên chia theo chiều dài hơn
        boolean splitHoriz;
        float ratio = node.area.width / node.area.height;
        if (ratio > 1.25f) {
            splitHoriz = false; // rộng → chia dọc (left/right)
        } else if (1f / ratio > 1.25f) {
            splitHoriz = true;  // cao → chia ngang (top/bottom)
        } else {
            splitHoriz = random.nextBoolean();
        }

        if (splitHoriz) {
            // Chia theo chiều Y
            float lo = node.area.y + minSectionSize;
            float hi = node.area.y + node.area.height - minSectionSize;
            if (lo >= hi) return; // vùng quá nhỏ, bỏ qua
            float splitY = snapToTile(lo + random.nextFloat() * (hi - lo));
            node.left = new BSPNode(new Rectangle(node.area.x, node.area.y,
                node.area.width, splitY - node.area.y));
            node.right = new BSPNode(new Rectangle(node.area.x, splitY,
                node.area.width, node.area.y + node.area.height - splitY));
        } else {
            // Chia theo chiều X
            float lo = node.area.x + minSectionSize;
            float hi = node.area.x + node.area.width - minSectionSize;
            if (lo >= hi) return;
            float splitX = snapToTile(lo + random.nextFloat() * (hi - lo));
            node.left = new BSPNode(new Rectangle(node.area.x, node.area.y,
                splitX - node.area.x, node.area.height));
            node.right = new BSPNode(new Rectangle(splitX, node.area.y,
                node.area.x + node.area.width - splitX, node.area.height));
        }

        splitNode(node.left, depth + 1);
        splitNode(node.right, depth + 1);
    }

    // ─── Bước 2: Tạo phòng ───────────────────────────────────────────────────

    private void buildRooms(BSPNode node, int[] counter) {
        if (node.isLeaf()) {
            int marginPx = GameConfig.ROOM_MARGIN_TILES * TILE;

            // Kích thước phòng ngẫu nhiên trong khoảng [min, max] tile
            int wTiles = GameConfig.ROOM_MIN_TILES
                + random.nextInt(GameConfig.ROOM_MAX_TILES - GameConfig.ROOM_MIN_TILES + 1);
            int hTiles = GameConfig.ROOM_MIN_TILES
                + random.nextInt(GameConfig.ROOM_MAX_TILES - GameConfig.ROOM_MIN_TILES + 1);

            // Giới hạn không vượt quá vùng BSP (trừ margin)
            float maxW = node.area.width - 2 * marginPx;
            float maxH = node.area.height - 2 * marginPx;
            float roomW = Math.min(wTiles * TILE, maxW);
            float roomH = Math.min(hTiles * TILE, maxH);
            if (roomW < GameConfig.ROOM_MIN_TILES * TILE) roomW = GameConfig.ROOM_MIN_TILES * TILE;
            if (roomH < GameConfig.ROOM_MIN_TILES * TILE) roomH = GameConfig.ROOM_MIN_TILES * TILE;

            // Vị trí ngẫu nhiên trong vùng, snap về tile
            float rangeX = maxW - roomW;
            float rangeY = maxH - roomH;
            float roomX = snapToTile(node.area.x + marginPx + (rangeX > 0 ? random.nextFloat() * rangeX : 0));
            float roomY = snapToTile(node.area.y + marginPx + (rangeY > 0 ? random.nextFloat() * rangeY : 0));

            Room room = new Room(counter[0]++, new Rectangle(roomX, roomY, roomW, roomH));
            node.room = room;
            rooms.add(room);
        } else {
            if (node.left != null) buildRooms(node.left, counter);
            if (node.right != null) buildRooms(node.right, counter);
        }
    }

    // ─── Bước 5: Nối hành lang tuyến tính ────────────────────────────────────

    /**
     * Nối hành lang chữ L giữa MỖI CẶP PHÒNG LIỀN KỀ theo {@code orderIndex}
     * (0↔1, 1↔2, 2↔3, ...). Đây là điểm khác biệt cốt lõi so với bản BSP-tree
     * cũ: bản cũ gọi {@code buildCorridors(root)} tạo 1 hành lang tại MỖI
     * node nội bộ của cây BSP (spanning tree phủ toàn cây, ~7 hành lang với
     * depth=3), khiến dungeon có nhiều lối đi đan xen thay vì 1 đường thẳng.
     * Giờ ta chỉ tạo đúng {@code rooms.size() - 1} hành lang, mỗi cái nối
     * đúng 1 cặp phòng theo thứ tự chơi đã xác định ở {@link #orderRoomsByBfs()}.
     */
    private void buildLinearCorridors() {
        for (int i = 0; i + 1 < rooms.size(); i++) {
            Room a = rooms.get(i);
            Room b = rooms.get(i + 1);
            corridors.add(makeLCorridor(a, b));
        }
    }

    /**
     * Tạo hành lang nối tâm phòng A với tâm phòng B.
     *
     * <p>Logic:
     * <ul>
     *   <li>Nếu 2 phòng thẳng hàng ngang (ay ≈ by) → 1 đoạn ngang duy nhất.</li>
     *   <li>Nếu 2 phòng thẳng hàng dọc (ax ≈ bx) → 1 đoạn dọc duy nhất.</li>
     *   <li>Ngược lại → gọi {@link #addLSegments} để tạo đường chữ L (hoặc nhiều
     *       đoạn qua điểm trung gian nếu đoạn nào vượt {@code MAX_SEG}).</li>
     * </ul>
     */
    private Corridor makeLCorridor(Room a, Room b) {
        Corridor corr = new Corridor(a, b);

        float ax = snapToTile(a.getCenter().x);
        float ay = snapToTile(a.getCenter().y);
        float bx = snapToTile(b.getCenter().x);
        float by = snapToTile(b.getCenter().y);

        addLSegments(corr, ax, ay, bx, by);
        return corr;
    }

    /**
     * Thêm các đoạn hành lang từ điểm (ax, ay) đến (bx, by) vào {@code corr}.
     *
     * <ul>
     *   <li><b>Thẳng hàng ngang</b> (|ay−by| < TILE): 1 đoạn ngang.</li>
     *   <li><b>Thẳng hàng dọc</b> (|ax−bx| < TILE): 1 đoạn dọc.</li>
     *   <li><b>Đoạn quá dài</b> (dx > MAX_SEG hoặc dy > MAX_SEG): chia đệ quy
     *       qua điểm trung gian (midX, midY).</li>
     *   <li><b>Bình thường</b>: hành lang chữ L chuẩn (ngang rồi dọc).</li>
     * </ul>
     */
    private void addLSegments(Corridor corr,
                              float ax, float ay,
                              float bx, float by) {
        boolean sameY = Math.abs(ay - by) < TILE;
        boolean sameX = Math.abs(ax - bx) < TILE;

        if (sameY) {
            // ── Thẳng hàng ngang: chỉ cần 1 đoạn ngang ──────────────────────
            float left  = Math.min(ax, bx) - HALF;
            float right = Math.max(ax, bx) + HALF;
            corr.segments.add(new Rectangle(left, ay - HALF, right - left, CORR));
            return;
        }

        if (sameX) {
            // ── Thẳng hàng dọc: chỉ cần 1 đoạn dọc ──────────────────────────
            float bot = Math.min(ay, by) - HALF;
            float top = Math.max(ay, by) + HALF;
            corr.segments.add(new Rectangle(ax - HALF, bot, CORR, top - bot));
            return;
        }

        float dx = Math.abs(bx - ax);
        float dy = Math.abs(by - ay);

        if (dx > MAX_SEG || dy > MAX_SEG) {
            // ── Đoạn quá dài: chia qua điểm trung gian ───────────────────────
            float midX = snapToTile((ax + bx) / 2f);
            float midY = snapToTile((ay + by) / 2f);
            addLSegments(corr, ax, ay, midX, midY);
            addLSegments(corr, midX, midY, bx, by);
            return;
        }

        // ── Hành lang chữ L bình thường (ngang → dọc, góc tại bx, ay) ────────
        float hLeft  = Math.min(ax, bx) - HALF;
        float hRight = Math.max(ax, bx) + HALF;
        corr.segments.add(new Rectangle(hLeft, ay - HALF, hRight - hLeft, CORR));

        float vBot = Math.min(ay, by) - HALF;
        float vTop = Math.max(ay, by) + HALF;
        corr.segments.add(new Rectangle(bx - HALF, vBot, CORR, vTop - vBot));
    }

    // ─── Bước 3: Sắp thứ tự phòng ────────────────────────────────────────────

    /**
     * Sắp {@code rooms} theo thứ tự chơi tuyến tính, xuất phát từ phòng gần
     * góc bottom-left nhất (spawn), sau đó luôn đi tới PHÒNG CHƯA THĂM GẦN
     * NHẤT tiếp theo (nearest-neighbor chain). Không dùng cấu trúc cây BSP
     * hay danh sách corridor để suy luận thứ tự — vì tại bước này hành lang
     * còn chưa được tạo (xem thứ tự bước mới trong {@link #generate}).
     *
     * <p>Tên hàm giữ "Bfs" cho tương thích code cũ dù thuật toán giờ là
     * nearest-neighbor chain, phù hợp hơn với yêu cầu dungeon tuyến tính.
     */
    private void orderRoomsByBfs() {
        if (rooms.isEmpty()) return;

        // Chọn phòng spawn = phòng có tâm gần góc bottom-left nhất
        Room spawn = rooms.stream()
            .min(Comparator.comparingDouble(r -> r.getCenter().x + r.getCenter().y))
            .orElse(rooms.get(0));

        List<Room> remaining = new ArrayList<>(rooms);
        List<Room> ordered = new ArrayList<>();

        Room curr = spawn;
        remaining.remove(curr);
        ordered.add(curr);

        while (!remaining.isEmpty()) {
            final Room from = curr;
            Room next = remaining.stream()
                .min(Comparator.comparingDouble(r -> r.getCenter().dst(from.getCenter())))
                .orElse(null);
            remaining.remove(next);
            ordered.add(next);
            curr = next;
        }

        // Gán orderIndex theo thứ tự chuỗi vừa xây
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).orderIndex = i;
        }

        // Sắp xếp lại danh sách rooms theo orderIndex
        rooms.sort(Comparator.comparingInt(r -> r.orderIndex));
    }

    // ─── Bước 4: Gán loại phòng ──────────────────────────────────────────────

    private void assignTypes() {
        for (int i = 0; i < rooms.size() && i < TYPE_ORDER.length; i++) {
            rooms.get(i).type = TYPE_ORDER[i];
        }
    }

    // ─── Bước 6: Đặt spawn points ────────────────────────────────────────────

    private void placeSpawnPoints() {
        float padding = TILE * 1.5f; // khoảng cách tối thiểu từ tường phòng
        for (Room room : rooms) {
            switch (room.type) {
                case ENEMY:
                    // 2-3 quái mỗi phòng, vị trí ngẫu nhiên bên trong phòng,
                    // đảm bảo cách nhau tối thiểu ENEMY_SPAWN_MIN_DISTANCE
                    // (rejection sampling — thử vài lần, nếu không tìm được vị trí
                    // đủ xa thì chấp nhận vị trí tốt nhất tìm được để tránh vòng lặp vô hạn).
                    int count = 2 + random.nextInt(2);
                    List<Vector2> chosen = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        Vector2 pos = pickSpacedPoint(room, padding, chosen);
                        chosen.add(pos);
                        room.enemySpawnPoints.add(pos);
                    }
                    break;
                case BOSS:
                    // Boss spawn tại tâm phòng
                    room.bossSpawnPoint = room.getCenter();
                    break;
                case SPAWN:
                default:
                    // Phòng spawn không có địch
                    break;
            }
        }
    }

    /**
     * Chọn 1 điểm ngẫu nhiên bên trong phòng, cách xa các điểm đã chọn
     * ít nhất {@code GameConfig.ENEMY_SPAWN_MIN_DISTANCE}. Thử tối đa 30 lần;
     * nếu không thành công, trả về ứng viên có khoảng cách xa nhất tìm được
     * (đảm bảo luôn trả về 1 điểm hợp lệ, không loop vô hạn).
     */
    private Vector2 pickSpacedPoint(Room room, float padding, List<Vector2> existing) {
        float minDist = GameConfig.ENEMY_SPAWN_MIN_DISTANCE;
        Vector2 best = null;
        float bestScore = -1f;

        int attempts = 30;
        for (int a = 0; a < attempts; a++) {
            float ex = room.bounds.x + padding
                + random.nextFloat() * Math.max(0f, room.bounds.width - 2 * padding);
            float ey = room.bounds.y + padding
                + random.nextFloat() * Math.max(0f, room.bounds.height - 2 * padding);
            Vector2 candidate = new Vector2(ex, ey);

            float nearestDist = Float.MAX_VALUE;
            for (Vector2 p : existing) {
                nearestDist = Math.min(nearestDist, candidate.dst(p));
            }
            if (existing.isEmpty()) nearestDist = Float.MAX_VALUE;

            if (nearestDist >= minDist) {
                return candidate; // đủ xa — chấp nhận ngay
            }
            if (nearestDist > bestScore) {
                bestScore = nearestDist;
                best = candidate;
            }
        }
        return best != null ? best : room.getCenter();
    }

    // ─── Bước 7: Sinh cover blocks ────────────────────────────────────────────

    /**
     * Sinh 1-3 khối cover (kích thước COVER_SIZE_TILES × COVER_SIZE_TILES) trong
     * mỗi phòng ENEMY/BOSS, tránh chồng lên nhau, tránh sát tường (padding),
     * và tránh vùng tâm phòng (nơi boss spawn / entrance) bằng
     * COVER_CENTER_CLEARANCE_TILES. Cover blocks không đè lên corridor vì
     * chúng chỉ đặt trong bounds phòng.
     */
    private void placeCoverBlocks() {
        for (Room room : rooms) {
            if (room.type != Room.Type.ENEMY) continue;

            int coverCount = GameConfig.COVER_MIN_PER_ROOM
                + random.nextInt(GameConfig.COVER_MAX_PER_ROOM - GameConfig.COVER_MIN_PER_ROOM + 1);
            float coverSize = GameConfig.COVER_SIZE_TILES * TILE;
            float wallPad = GameConfig.COVER_WALL_PADDING_TILES * TILE;
            float centerClearance = GameConfig.COVER_CENTER_CLEARANCE_TILES * TILE;

            float minX = room.bounds.x + wallPad;
            float maxX = room.bounds.x + room.bounds.width - wallPad - coverSize;
            float minY = room.bounds.y + wallPad;
            float maxY = room.bounds.y + room.bounds.height - wallPad - coverSize;
            if (maxX <= minX || maxY <= minY) continue; // phòng quá nhỏ, bỏ qua

            Vector2 center = room.getCenter();
            int placed = 0;
            int attempts = 0;
            while (placed < coverCount && attempts < 40) {
                attempts++;
                float cx = snapToTile(minX + random.nextFloat() * (maxX - minX));
                float cy = snapToTile(minY + random.nextFloat() * (maxY - minY));
                Rectangle candidate = new Rectangle(cx, cy, coverSize, coverSize);

                // Tránh vùng tâm phòng (boss spawn point / lối vào)
                float candCx = cx + coverSize / 2f;
                float candCy = cy + coverSize / 2f;
                if (Vector2.dst(candCx, candCy, center.x, center.y) < centerClearance) continue;

                // Tránh chồng lên cover đã đặt (thêm biên nhỏ để không dính sát nhau)
                boolean overlaps = false;
                for (Rectangle existing : room.coverBlocks) {
                    Rectangle inflated = new Rectangle(
                        existing.x - TILE, existing.y - TILE,
                        existing.width + 2 * TILE, existing.height + 2 * TILE);
                    if (candidate.overlaps(inflated)) {
                        overlaps = true;
                        break;
                    }
                }
                if (overlaps) continue;

                room.coverBlocks.add(candidate);
                placed++;
            }
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /**
     * Làm tròn giá trị pixel xuống cạnh tile gần nhất.
     */
    private float snapToTile(float px) {
        return (float) (Math.round(px / TILE)) * TILE;
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public List<Room> getRooms() {
        return Collections.unmodifiableList(rooms);
    }

    public List<Corridor> getCorridors() {
        return Collections.unmodifiableList(corridors);
    }

    public BSPNode getRoot() {
        return root;
    }
}
