package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;
import com.mygame.tank.config.GameConfig;

import java.util.ArrayList;
import java.util.List;
public class DungeonTileMap {

    public static final byte WALL = 0;
    public static final byte FLOOR = 1;

    private static final int TILE = GameConfig.MAP_TILE_SIZE;
    private static final int COLS = GameConfig.MAP_COLS;
    private static final int ROWS = GameConfig.MAP_ROWS;

    /**
     * Lưới tile [row][col].
     */
    private final byte[][] grid = new byte[ROWS][COLS];

    /**
     * Danh sách collision rects (tường) cho CollisionSystem.
     */
    private final List<Rectangle> collisionRects = new ArrayList<>();

    /**
     * Danh sách collision rects của cover blocks (tách riêng khỏi tường biên
     * để renderer có thể vẽ bằng cover.png thay vì wall.png, nhưng vẫn được
     * gộp chung vào collisionRects để CollisionSystem chặn va chạm như tường).
     */
    private final List<Rectangle> coverCollisionRects = new ArrayList<>();

    // ─── Build từ dungeon layout ──────────────────────────────────────────────

    /**
     * Khởi tạo lưới từ danh sách phòng + hành lang, sau đó sinh collision rects.
     */
    public void build(List<Room> rooms, List<Corridor> corridors) {
        // Mặc định toàn bộ là tường
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = WALL;
            }
        }

        // Đánh dấu sàn cho từng phòng
        for (Room room : rooms) {
            fillFloor(room.bounds);
        }

        // Đánh dấu sàn cho từng đoạn hành lang
        for (Corridor corr : corridors) {
            for (Rectangle seg : corr.segments) {
                fillFloor(seg);
            }
        }

        // Sinh collision rects từ tường biên (gộp theo hàng)
        buildCollisionRects();

        // Cover blocks: KHÔNG đục sàn thành tường trên grid (enemy A* vẫn tính
        // né được nếu cần mở rộng sau này); thay vào đó thêm rect collision
        // riêng, tách biệt với danh sách tường biên để renderer vẽ đúng texture.
        buildCoverCollisionRects(rooms);
    }

    /**
     * Thêm collision rect cho từng cover block đã được BSPDungeonGenerator sinh
     * sẵn trong room.coverBlocks. Đồng thời gộp vào collisionRects chính để
     * CollisionSystem chặn va chạm (tank/projectile) giống như tường.
     */
    private void buildCoverCollisionRects(List<Room> rooms) {
        coverCollisionRects.clear();
        for (Room room : rooms) {
            for (Rectangle cover : room.coverBlocks) {
                Rectangle rectCopy = new Rectangle(cover);
                coverCollisionRects.add(rectCopy);
                collisionRects.add(rectCopy);
            }
        }
    }

    /**
     * Đánh dấu vùng chữ nhật (pixel) là FLOOR trên lưới tile.
     * Clamp để không vượt quá biên lưới.
     */
    private void fillFloor(Rectangle r) {
        int c0 = (int) (r.x / TILE);
        int c1 = (int) ((r.x + r.width) / TILE);
        int r0 = (int) (r.y / TILE);
        int r1 = (int) ((r.y + r.height) / TILE);

        c0 = Math.max(0, Math.min(COLS - 1, c0));
        c1 = Math.max(0, Math.min(COLS, c1));
        r0 = Math.max(0, Math.min(ROWS - 1, r0));
        r1 = Math.max(0, Math.min(ROWS, r1));

        for (int row = r0; row < r1; row++) {
            for (int col = c0; col < c1; col++) {
                grid[row][col] = FLOOR;
            }
        }
    }

    /**
     * Sinh danh sách collision rects bằng cách quét từng hàng lưới,
     * gộp các tile WALL liền kề có tiếp giáp với ít nhất 1 tile FLOOR.
     *
     * <p>Chỉ xét tường "biên" (kề sàn) để tránh tạo quá nhiều rect
     * ở vùng tường xa. Kết quả được gộp ngang (horizontal strip merging).
     */
    private void buildCollisionRects() {
        collisionRects.clear();

        for (int row = 0; row < ROWS; row++) {
            int runStart = -1;

            for (int col = 0; col <= COLS; col++) {
                // Xác định tile hiện tại có phải tường biên không
                boolean isBorderWall = (col < COLS)
                    && (grid[row][col] == WALL)
                    && hasAdjacentFloor(row, col);

                if (isBorderWall && runStart == -1) {
                    runStart = col;
                } else if (!isBorderWall && runStart != -1) {
                    // Kết thúc run → tạo 1 rect ngang
                    collisionRects.add(new Rectangle(
                        runStart * TILE,
                        row * TILE,
                        (col - runStart) * TILE,
                        TILE
                    ));
                    runStart = -1;
                }
            }
        }
    }

    /**
     * Kiểm tra xem tile (row, col) có kề ít nhất 1 tile FLOOR không
     * (4 hướng: trên, dưới, trái, phải).
     */
    private boolean hasAdjacentFloor(int row, int col) {
        return isFloor(row - 1, col)
            || isFloor(row + 1, col)
            || isFloor(row, col - 1)
            || isFloor(row, col + 1);
    }

    private boolean isFloor(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return false;
        return grid[row][col] == FLOOR;
    }


    /**
     * @return giá trị tile tại tọa độ tile (col, row).
     */
    public byte getTile(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return WALL;
        return grid[row][col];
    }

    public List<Rectangle> getCollisionRects() {
        return collisionRects;
    }

    /**
     * Collision rects riêng của cover blocks — dùng bởi renderer để vẽ cover.png.
     */
    public List<Rectangle> getCoverCollisionRects() {
        return coverCollisionRects;
    }

}
