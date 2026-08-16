package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;

/**
 * Đại diện một phòng trong dungeon.
 *
 * <p>Mỗi phòng là một hình chữ nhật trong không gian thế giới (world coords, pixel).
 * Phòng có kiểu (SPAWN / ENEMY / BOSS) và danh sách spawn point bên trong.
 */
public class Room {

    /**
     * Kiểu phòng:
     * <ul>
     *   <li>SPAWN — phòng bắt đầu, không có địch</li>
     *   <li>ENEMY — phòng quái thường</li>
     *   <li>BOSS  — phòng boss, có cửa khóa đến khi boss chết</li>
     * </ul>
     */
    public enum Type {SPAWN, ENEMY, BOSS}

    /**
     * ID nội bộ (gán lại theo thứ tự BFS sau khi sinh).
     */
    public final int id;

    /**
     * Vùng không gian phòng (pixel, Y-up).
     */
    public final Rectangle bounds;

    /**
     * Kiểu phòng — được gán sau khi sắp xếp BFS.
     */
    public Type type;

    /**
     * Thứ tự khám phá tính từ phòng spawn bằng BFS.
     * 0 = phòng spawn, 1, 2, ... = các phòng tiếp theo.
     */
    public int orderIndex;

    /**
     * Danh sách vị trí spawn quái (chỉ có ở phòng ENEMY).
     */
    public final List<Vector2> enemySpawnPoints = new ArrayList<>();

    /**
     * Vị trí spawn boss (chỉ có ở phòng BOSS).
     */
    public Vector2 bossSpawnPoint;

    /**
     * true khi tất cả địch trong phòng đã bị tiêu diệt.
     */
    public boolean cleared = false;

    /**
     * Danh sách khối cover (chướng ngại không phá hủy được) bên trong phòng.
     * Mỗi Rectangle là vùng world-coords (pixel) của 1 khối cover.
     * Chỉ sinh ở phòng ENEMY/BOSS, dùng texture cover.png khi vẽ.
     */
    public final List<Rectangle> coverBlocks = new ArrayList<>();

    public Room(int id, Rectangle bounds) {
        this.id = id;
        this.bounds = bounds;
        this.type = Type.SPAWN;
        this.orderIndex = id;
    }

    /**
     * @return tâm phòng (world coords).
     */
    public Vector2 getCenter() {
        return new Vector2(
            bounds.x + bounds.width / 2f,
            bounds.y + bounds.height / 2f
        );
    }

    @Override
    public String toString() {
        return "Room{id=" + id + ", order=" + orderIndex + ", type=" + type
            + ", bounds=" + bounds + "}";
    }
}
