package com.mygame.tank.dungeon;

import com.badlogic.gdx.math.Rectangle;

/**
 * Một nút trong cây BSP (Binary Space Partitioning).
 *
 * <p>Cây BSP chia đệ quy không gian dungeon thành các vùng nhỏ hơn.
 * Mỗi nút lá (leaf) sẽ chứa một phòng (Room).
 * Các nút trong (internal) chỉ chứa thông tin vùng và 2 nút con.
 */
public class BSPNode {

    /** Vùng không gian (pixel) mà nút này quản lý. */
    public final Rectangle area;

    /** Con trái (vùng sau khi chia — phía dưới hoặc phía trái). */
    public BSPNode left;

    /** Con phải (vùng sau khi chia — phía trên hoặc phía phải). */
    public BSPNode right;

    /**
     * Phòng được tạo trong vùng này.
     * Chỉ tồn tại ở nút lá; null ở nút trong.
     */
    public Room room;

    public BSPNode(Rectangle area) {
        this.area = area;
    }

    /** @return true nếu nút này là nút lá (không có con). */
    public boolean isLeaf() {
        return left == null && right == null;
    }
}
