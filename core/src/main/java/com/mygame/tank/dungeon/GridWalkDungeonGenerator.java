package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;

import java.util.*;

/**
 * Thuật toán sinh dungeon ngẫu nhiên dùng GRID-BASED RANDOM WALK để đặt vị
 * trí phòng, thay thế hoàn toàn cho BSP Tree trước đây.
 *
 * <h3>Vì sao đổi từ BSP sang Random Walk:</h3>
 * <p>Bản BSP cũ chỉ dùng cây BSP để ĐẶT vị trí 8 phòng (chia không gian
 * thành 8 vùng lá), rồi bỏ hẳn cấu trúc cây khi nối hành lang — thứ tự chơi
 * được suy ra sau đó bằng nearest-neighbor chain. Cách này có 2 vấn đề:
 * <ol>
 *   <li>Nearest-neighbor chain không đảm bảo 2 phòng liền kề trong chuỗi
 *       chơi cũng liền kề trong không gian → hành lang có thể rất dài, phải
 *       chia đệ quy qua điểm trung gian, tạo đường zigzag không tự nhiên,
 *       thậm chí cắt ngang qua phòng khác.</li>
 *   <li>Không có gì đảm bảo layout tuyến tính "đẹp" — phòng có thể bị bỏ
 *       lại ở giữa map, buộc hành lang phải vòng qua.</li>
 * </ol>
 *
 * <p>Random Walk giải quyết cả 2 vấn đề bằng cách đảm bảo TÍNH LIỀN KỀ THEO
 * CẤU TRÚC: mỗi bước đi luôn tạo 1 phòng mới ngay cạnh (trên lưới cell) phòng
 * vừa đi qua. Thứ tự sinh phòng CHÍNH LÀ thứ tự chơi — không cần suy luận
 * lại bằng BFS/nearest-neighbor. Hành lang giữa 2 phòng liên tiếp luôn ngắn
 * (đúng 1 cell) nên hầu như luôn là 1 đoạn thẳng hoặc 1 góc chữ L đơn giản.
 *
 * <h3>Các bước:</h3>
 * <ol>
 *   <li>Chia không gian (60×60 tile) thành lưới các CELL vuông, đủ lớn để
 *       chứa 1 phòng kích thước ngẫu nhiên (kể cả margin).</li>
 *   <li>Random Walk {@code TYPE_ORDER.length} bước trên lưới cell, bắt đầu
 *       gần góc bottom-left. Mỗi bước chọn ngẫu nhiên 1 trong 4 hướng
 *       (không lặp lại hướng ngược 180° vừa đi), bỏ qua cell đã thăm.</li>
 *   <li>Mỗi cell đã thăm → tạo 1 phòng (Room) kích thước ngẫu nhiên, đặt
 *       ngẫu nhiên bên trong cell (giống logic random cũ).</li>
 *   <li>Gán loại phòng theo thứ tự cố định: SPAWN, ENEMY, ENEMY, BOSS,
 *       ENEMY, ENEMY, BOSS, BOSS (thứ tự = thứ tự random walk).</li>
 *   <li>Nối hành lang chữ L giữa MỖI CẶP PHÒNG LIỀN KỀ trong chuỗi
 *       (0↔1, 1↔2, ...) — đúng {@code rooms.size() - 1} hành lang.</li>
 *   <li>Đặt spawn point quái/boss trong từng phòng.</li>
 *   <li>Sinh cover blocks ngẫu nhiên trong phòng ENEMY.</li>
 * </ol>
 */
public class GridWalkDungeonGenerator {

    // ─── Hằng số từ GameConfig ────────────────────────────────────────────────
    private static final int TILE = GameConfig.MAP_TILE_SIZE;
    // Nếu CORRIDOR_WIDTH_TILES là số lẻ, CORR/2 mất phần dư nửa tile → toàn
    // bộ corridor segment (và do đó door rect tính từ nó) bị lệch khỏi lưới
    // tile đúng nửa TILE, khiến fillFloor()/door rect cắt hụt gần 1 tile so
    // với bề rộng hành lang thật. Ép CORR lên số tile CHẴN gần nhất để HALF
    // luôn là bội số nguyên của TILE, giữ corridor thẳng lưới tuyệt đối.
    private static final int CORRIDOR_WIDTH_TILES_EVEN =
        GameConfig.CORRIDOR_WIDTH_TILES + (GameConfig.CORRIDOR_WIDTH_TILES % 2);
    private static final int CORR = CORRIDOR_WIDTH_TILES_EVEN * TILE;
    private static final int HALF = CORR / 2;
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

    // 4 hướng đi trên lưới cell: RIGHT, UP, LEFT, DOWN (dx, dy)
    private static final int[][] DIRS = {
        {1, 0}, {0, 1}, {-1, 0}, {0, -1}
    };

    // ─── State ────────────────────────────────────────────────────────────────
    private final Random random;
    private final List<Room> rooms = new ArrayList<>();
    private final List<Corridor> corridors = new ArrayList<>();

    /**
     * Kích thước 1 cell (pixel) trên lưới random walk — đủ chỗ cho 1 phòng max-size + margin.
     */
    private float cellSize;

    /**
     * Số cell theo mỗi trục của lưới walk.
     */
    private int gridCols;
    private int gridRows;

    /**
     * Map (col,row) cell → Room đã đặt tại đó, theo thứ tự random walk.
     */
    private final Map<Long, Room> cellToRoom = new LinkedHashMap<>();

    /**
     * @param seed random seed — truyền System.currentTimeMillis() để luôn mới.
     */
    public GridWalkDungeonGenerator(long seed) {
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
        cellToRoom.clear();

        float totalW = totalCols * TILE;
        float totalH = totalRows * TILE;

        // Bước 1: Tính kích thước cell đủ lớn để chứa phòng max-size + margin
        // + đệm cho hành lang, rồi suy ra kích thước lưới walk.
        float minCellSize = (GameConfig.ROOM_MAX_TILES + 2 * GameConfig.ROOM_MARGIN_TILES + 2) * TILE;
        gridCols = Math.max(1, (int) (totalW / minCellSize));
        gridRows = Math.max(1, (int) (totalH / minCellSize));
        cellSize = Math.min(totalW / gridCols, totalH / gridRows);

        // Bước 2: Random walk để chọn chuỗi cell + tạo phòng trong từng cell,
        // theo ĐÚNG thứ tự chơi (thứ tự sinh = thứ tự walk).
        walkAndBuildRooms();

        // Bước 3: Gán loại phòng theo orderIndex (đã đúng thứ tự walk sẵn)
        assignTypes();

        // Bước 4: Nối hành lang TUYẾN TÍNH — mỗi phòng chỉ nối với đúng
        // phòng liền trước nó theo orderIndex (0-1, 1-2, 2-3, ...).
        buildLinearCorridors();

        // Bước 5: Đặt spawn points
        placeSpawnPoints();

        // Bước 6: Sinh khối cover ngẫu nhiên trong từng phòng ENEMY
        placeCoverBlocks();
    }

    // ─── Bước 2: Random walk + tạo phòng ──────────────────────────────────────

    /**
     * Đi ngẫu nhiên trên lưới cell, mỗi bước tạo 1 phòng mới trong cell đó.
     * Bắt đầu từ 1 cell gần góc bottom-left (giữ tinh thần "spawn ở góc"
     * của bản cũ). Không quay đầu 180° ngay lập tức.
     *
     * <p><b>Dùng DFS với backtrack ĐẦY ĐỦ (không chỉ lùi 1 bước)</b> để đảm
     * bảo luôn tìm đủ {@code TYPE_ORDER.length} cell nếu lưới đủ khả năng
     * chứa 1 đường đi như vậy. Bug đã sửa: bản trước chỉ lùi lại đúng 1 ô
     * mỗi khi bị kẹt rồi thử lại — trên lưới nhỏ (ví dụ 3×3 = 9 cell cho 8
     * bước, gần như phải phủ hết lưới) cách này thường tự nhốt vào góc và
     * backtrack liên tục về đúng điểm xuất phát rồi dừng hẳn (chỉ còn 1
     * phòng), khiến {@code buildLinearCorridors()} không tạo được hành lang
     * nào. DFS với backtrack đệ quy đầy đủ (lùi bao nhiêu bước cũng được,
     * thử hướng khác ở MỌI cấp) loại bỏ hoàn toàn tình huống đó.
     */
    private void walkAndBuildRooms() {
        int startCol = random.nextInt(Math.max(1, gridCols / 3));
        int startRow = random.nextInt(Math.max(1, gridRows / 3));

        List<int[]> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        int targetSteps = Math.min(TYPE_ORDER.length, gridCols * gridRows);

        walkDfs(startCol, startRow, -1, path, visited, targetSteps);

        // Tạo phòng cho từng cell trong path, theo đúng thứ tự walk.
        int idCounter = 0;
        for (int[] cell : path) {
            Room room = buildRoomInCell(cell[0], cell[1], idCounter);
            room.orderIndex = idCounter;
            idCounter++;
            rooms.add(room);
            cellToRoom.put(cellKey(cell[0], cell[1]), room);
        }
    }

    /**
     * DFS đệ quy có backtrack đầy đủ: thử đi tới 1 cell lân cận hợp lệ (chưa
     * thăm, trong lưới, không quay đầu 180°) theo thứ tự ngẫu nhiên; nếu
     * nhánh đó cuối cùng không đạt được {@code targetSteps} cell, hàm tự
     * quay lui (bỏ cell vừa thêm khỏi {@code path}/{@code visited}) và thử
     * hướng khác — kể cả ở những cấp đệ quy sâu hơn phía trên, chứ không chỉ
     * lùi đúng 1 bước như thuật toán duyệt vòng lặp trước đây.
     *
     * @return true nếu từ (col,row) đã tìm được đường đủ dài (path đã được
     * cập nhật thành đường đi đó); false nếu nhánh này bế tắc (path
     * đã được phục hồi về trạng thái trước khi gọi hàm).
     */
    private boolean walkDfs(int col, int row, int lastDir,
                            List<int[]> path, Set<Long> visited, int targetSteps) {
        path.add(new int[]{col, row});
        visited.add(cellKey(col, row));

        if (path.size() >= targetSteps) {
            return true;
        }

        List<Integer> candidates = new ArrayList<>();
        int oppositeOfLast = (lastDir == -1) ? -1 : (lastDir + 2) % 4;
        for (int d = 0; d < DIRS.length; d++) {
            if (d == oppositeOfLast) continue; // cấm quay đầu 180°
            int nc = col + DIRS[d][0];
            int nr = row + DIRS[d][1];
            if (nc < 0 || nc >= gridCols || nr < 0 || nr >= gridRows) continue;
            if (visited.contains(cellKey(nc, nr))) continue;
            candidates.add(d);
        }
        Collections.shuffle(candidates, random);

        for (int d : candidates) {
            int nc = col + DIRS[d][0];
            int nr = row + DIRS[d][1];
            if (walkDfs(nc, nr, d, path, visited, targetSteps)) {
                return true;
            }
        }

        // Bế tắc từ (col,row) — quay lui: gỡ cell này khỏi path/visited để
        // cấp gọi phía trên (hoặc vòng for ở trên) thử hướng khác.
        path.remove(path.size() - 1);
        visited.remove(cellKey(col, row));
        return false;
    }

    /**
     * Tạo 1 phòng kích thước ngẫu nhiên (trong khoảng ROOM_MIN..ROOM_MAX tile),
     * đặt ngẫu nhiên bên trong cell (col, row) có margin, giống logic random
     * vị trí/kích thước của bản BSP cũ ({@code buildRooms}).
     */
    private Room buildRoomInCell(int col, int row, int id) {
        int marginPx = GameConfig.ROOM_MARGIN_TILES * TILE;

        int wTiles = GameConfig.ROOM_MIN_TILES
            + random.nextInt(GameConfig.ROOM_MAX_TILES - GameConfig.ROOM_MIN_TILES + 1);
        int hTiles = GameConfig.ROOM_MIN_TILES
            + random.nextInt(GameConfig.ROOM_MAX_TILES - GameConfig.ROOM_MIN_TILES + 1);

        float cellX = col * cellSize;
        float cellY = row * cellSize;

        float maxW = cellSize - 2 * marginPx;
        float maxH = cellSize - 2 * marginPx;
        float roomW = Math.min(wTiles * TILE, maxW);
        float roomH = Math.min(hTiles * TILE, maxH);
        if (roomW < GameConfig.ROOM_MIN_TILES * TILE) roomW = GameConfig.ROOM_MIN_TILES * TILE;
        if (roomH < GameConfig.ROOM_MIN_TILES * TILE) roomH = GameConfig.ROOM_MIN_TILES * TILE;

        float rangeX = maxW - roomW;
        float rangeY = maxH - roomH;
        float roomX = snapToTile(cellX + marginPx + (rangeX > 0 ? random.nextFloat() * rangeX : 0));
        float roomY = snapToTile(cellY + marginPx + (rangeY > 0 ? random.nextFloat() * rangeY : 0));

        return new Room(id, new Rectangle(roomX, roomY, roomW, roomH));
    }

    private long cellKey(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    // ─── Bước 3: Gán loại phòng ──────────────────────────────────────────────

    private void assignTypes() {
        for (int i = 0; i < rooms.size() && i < TYPE_ORDER.length; i++) {
            rooms.get(i).type = TYPE_ORDER[i];
        }
    }

    // ─── Bước 4: Nối hành lang tuyến tính ────────────────────────────────────

    /**
     * Nối hành lang chữ L giữa MỖI CẶP PHÒNG LIỀN KỀ theo {@code orderIndex}
     * (0↔1, 1↔2, 2↔3, ...) — đúng {@code rooms.size() - 1} hành lang. Vì mỗi
     * cặp phòng liên tiếp trong random walk luôn ở 2 cell liền kề trên lưới,
     * khoảng cách tâm-tâm luôn nhỏ (≈ cellSize) nên hành lang gần như luôn
     * là 1 đoạn thẳng hoặc 1 góc chữ L đơn giản — không cần chia đệ quy qua
     * điểm trung gian như bản cũ (dù logic đó vẫn được giữ làm an toàn dự
     * phòng cho trường hợp cell quá lớn).
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
     *       qua điểm trung gian (midX, midY) — dự phòng an toàn, hiếm khi
     *       xảy ra với grid walk vì khoảng cách 2 phòng liên tiếp luôn nhỏ.</li>
     *   <li><b>Bình thường</b>: hành lang chữ L chuẩn (ngang rồi dọc).</li>
     * </ul>
     */
    private void addLSegments(Corridor corr,
                              float ax, float ay,
                              float bx, float by) {
        boolean sameY = Math.abs(ay - by) < TILE;
        boolean sameX = Math.abs(ax - bx) < TILE;

        if (sameY) {
            float left = Math.min(ax, bx) - HALF;
            float right = Math.max(ax, bx) + HALF;
            corr.segments.add(new Rectangle(left, ay - HALF, right - left, CORR));
            return;
        }

        if (sameX) {
            float bot = Math.min(ay, by) - HALF;
            float top = Math.max(ay, by) + HALF;
            corr.segments.add(new Rectangle(ax - HALF, bot, CORR, top - bot));
            return;
        }

        float dx = Math.abs(bx - ax);
        float dy = Math.abs(by - ay);

        if (dx > MAX_SEG || dy > MAX_SEG) {
            float midX = snapToTile((ax + bx) / 2f);
            float midY = snapToTile((ay + by) / 2f);
            addLSegments(corr, ax, ay, midX, midY);
            addLSegments(corr, midX, midY, bx, by);
            return;
        }

        float hLeft = Math.min(ax, bx) - HALF;
        float hRight = Math.max(ax, bx) + HALF;
        corr.segments.add(new Rectangle(hLeft, ay - HALF, hRight - hLeft, CORR));

        float vBot = Math.min(ay, by) - HALF;
        float vTop = Math.max(ay, by) + HALF;
        corr.segments.add(new Rectangle(bx - HALF, vBot, CORR, vTop - vBot));
    }

    // ─── Bước 5: Đặt spawn points ────────────────────────────────────────────

    private void placeSpawnPoints() {
        float padding = TILE * 1.5f;
        for (Room room : rooms) {
            switch (room.type) {
                case ENEMY:
                    int count = 2 + random.nextInt(2);
                    List<Vector2> chosen = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        Vector2 pos = pickSpacedPoint(room, padding, chosen);
                        chosen.add(pos);
                        room.enemySpawnPoints.add(pos);
                    }
                    break;
                case BOSS:
                    room.bossSpawnPoint = room.getCenter();
                    break;
                case SPAWN:
                default:
                    break;
            }
        }
    }

    /**
     * Chọn 1 điểm ngẫu nhiên bên trong phòng, cách xa các điểm đã chọn
     * ít nhất {@code GameConfig.ENEMY_SPAWN_MIN_DISTANCE}. Thử tối đa 30 lần;
     * nếu không thành công, trả về ứng viên có khoảng cách xa nhất tìm được.
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
                return candidate;
            }
            if (nearestDist > bestScore) {
                bestScore = nearestDist;
                best = candidate;
            }
        }
        return best != null ? best : room.getCenter();
    }

    // ─── Bước 6: Sinh cover blocks ────────────────────────────────────────────

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
            if (maxX <= minX || maxY <= minY) continue;

            Vector2 center = room.getCenter();
            int placed = 0;
            int attempts = 0;
            while (placed < coverCount && attempts < 40) {
                attempts++;
                float cx = snapToTile(minX + random.nextFloat() * (maxX - minX));
                float cy = snapToTile(minY + random.nextFloat() * (maxY - minY));
                Rectangle candidate = new Rectangle(cx, cy, coverSize, coverSize);

                float candCx = cx + coverSize / 2f;
                float candCy = cy + coverSize / 2f;
                if (Vector2.dst(candCx, candCy, center.x, center.y) < centerClearance) continue;

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
}
