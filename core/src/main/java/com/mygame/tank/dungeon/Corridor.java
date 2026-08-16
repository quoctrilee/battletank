package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Hành lang nối 2 phòng, có dạng hình chữ L (2 đoạn thẳng vuông góc).
 *
 * <p>Mỗi Corridor chứa 2 đoạn {@link Rectangle} — một ngang, một dọc —
 * cùng chắp nối tại 1 điểm góc. Cả 2 đoạn đều có độ rộng cố định
 * ({@code CORRIDOR_WIDTH_TILES} tile).
 */
public class Corridor {

    /**
     * Phòng đầu (phòng A của kết nối).
     */
    public final Room roomA;

    /**
     * Phòng cuối (phòng B của kết nối).
     */
    public final Room roomB;

    /**
     * Danh sách các đoạn hành lang (thường 2 đoạn tạo thành chữ L).
     * Mỗi đoạn là một Rectangle trong world coords (pixel).
     */
    public final List<Rectangle> segments = new ArrayList<>();

    public Corridor(Room roomA, Room roomB) {
        this.roomA = roomA;
        this.roomB = roomB;
    }
}
