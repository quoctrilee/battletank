package com.mygame.tank.dungeon.pathfinding;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.dungeon.DungeonTileMap;

import java.util.*;

/**
 * A* pathfinding trên tile grid của dungeon.
 *
 * <h3>Mục đích:</h3>
 * Enemy di chuyển qua hành lang và phòng mà không đi xuyên tường.
 *
 * <h3>Cách dùng:</h3>
 * <pre>
 *   AStarPathfinder pf = new AStarPathfinder(dungeonTileMap);
 *   List&lt;Vector2&gt; path = pf.findPath(startWorldPos, goalWorldPos);
 *   // path = list of waypoints (world coords, center of each tile)
 *   // null nếu không tìm được đường
 * </pre>
 *
 * <h3>Thuật toán:</h3>
 * <ul>
 *   <li>Heuristic: Manhattan distance</li>
 *   <li>Di chuyển 4 hướng (không chéo để tránh đi qua góc tường)</li>
 *   <li>Kết quả là danh sách waypoints tâm tile (world coords)</li>
 * </ul>
 */
public class AStarPathfinder {

    private static final int TILE = GameConfig.MAP_TILE_SIZE;

    // ─── Tile node ────────────────────────────────────────────────────────────

    private static final class Node {
        final int col, row;
        float g; // chi phí từ start
        float h; // heuristic đến goal
        Node parent;

        Node(int col, int row) {
            this.col = col;
            this.row = row;
        }

        float f() { return g + h; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node)) return false;
            Node n = (Node) o;
            return col == n.col && row == n.row;
        }

        @Override
        public int hashCode() {
            return col * 10000 + row;
        }
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private final DungeonTileMap tileMap;

    public AStarPathfinder(DungeonTileMap tileMap) {
        this.tileMap = tileMap;
    }

    // ─── Pathfinding ──────────────────────────────────────────────────────────

    /**
     * Tìm đường từ {@code start} đến {@code goal} (world coords).
     *
     * @return danh sách waypoints (world coords, center of tile),
     *         hoặc {@code null} nếu không tìm được đường.
     */
    public List<Vector2> findPath(Vector2 start, Vector2 goal) {
        int startCol = (int)(start.x / TILE);
        int startRow = (int)(start.y / TILE);
        int goalCol  = (int)(goal.x  / TILE);
        int goalRow  = (int)(goal.y  / TILE);

        // Nếu cùng tile → không cần đường
        if (startCol == goalCol && startRow == goalRow) {
            return Collections.emptyList();
        }

        // Nếu goal là tường → tìm tile sàn gần nhất
        if (tileMap.getTile(goalCol, goalRow) != DungeonTileMap.FLOOR) {
            int[] nearest = findNearestFloor(goalCol, goalRow);
            if (nearest == null) return null;
            goalCol = nearest[0];
            goalRow = nearest[1];
        }

        // Nếu start là tường → fallback đến goal trực tiếp
        if (tileMap.getTile(startCol, startRow) != DungeonTileMap.FLOOR) {
            return Collections.emptyList();
        }

        return runAStar(startCol, startRow, goalCol, goalRow);
    }

    private List<Vector2> runAStar(int sc, int sr, int gc, int gr) {
        // Open set: priority queue theo f = g + h
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Integer, Node>  openMap  = new HashMap<>();
        Set<Integer>        closedSet = new HashSet<>();

        Node startNode = new Node(sc, sr);
        startNode.g = 0;
        startNode.h = manhattan(sc, sr, gc, gr);
        open.add(startNode);
        openMap.put(key(sc, sr), startNode);

        int maxIterations = 2000; // giới hạn để tránh freeze khi dungeon lớn

        while (!open.isEmpty() && maxIterations-- > 0) {
            Node current = open.poll();
            openMap.remove(key(current.col, current.row));

            if (current.col == gc && current.row == gr) {
                return reconstructPath(current);
            }

            closedSet.add(key(current.col, current.row));

            // 4 hướng di chuyển
            int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] d : dirs) {
                int nc = current.col + d[0];
                int nr = current.row + d[1];
                int nk = key(nc, nr);

                if (closedSet.contains(nk)) continue;
                if (tileMap.getTile(nc, nr) != DungeonTileMap.FLOOR) continue;

                float ng = current.g + 1f;

                Node neighbor = openMap.get(nk);
                if (neighbor == null) {
                    neighbor = new Node(nc, nr);
                    neighbor.g = ng;
                    neighbor.h = manhattan(nc, nr, gc, gr);
                    neighbor.parent = current;
                    open.add(neighbor);
                    openMap.put(nk, neighbor);
                } else if (ng < neighbor.g) {
                    // Tìm được đường ngắn hơn → cập nhật
                    open.remove(neighbor);
                    neighbor.g = ng;
                    neighbor.parent = current;
                    open.add(neighbor);
                }
            }
        }

        return null; // không tìm được đường
    }

    /** Dựng lại đường đi từ node đích về node start bằng cách đi ngược parent. */
    private List<Vector2> reconstructPath(Node goal) {
        List<Vector2> path = new ArrayList<>();
        Node curr = goal;
        while (curr != null) {
            // Tâm tile (world coords)
            path.add(new Vector2(curr.col * TILE + TILE / 2f, curr.row * TILE + TILE / 2f));
            curr = curr.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /** Tìm tile FLOOR gần nhất quanh (col, row) bằng BFS nhỏ. */
    private int[] findNearestFloor(int col, int row) {
        Queue<int[]> q = new LinkedList<>();
        Set<Integer> seen = new HashSet<>();
        q.add(new int[]{col, row});
        seen.add(key(col, row));
        int maxSearch = 20;
        while (!q.isEmpty() && maxSearch-- > 0) {
            int[] curr = q.poll();
            if (tileMap.getTile(curr[0], curr[1]) == DungeonTileMap.FLOOR) {
                return curr;
            }
            int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
            for (int[] d : dirs) {
                int nc = curr[0] + d[0], nr = curr[1] + d[1];
                int k = key(nc, nr);
                if (!seen.contains(k)) {
                    seen.add(k);
                    q.add(new int[]{nc, nr});
                }
            }
        }
        return null;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static float manhattan(int c1, int r1, int c2, int r2) {
        return Math.abs(c2 - c1) + Math.abs(r2 - r1);
    }

    private static int key(int col, int row) {
        return col * 10000 + row;
    }
}
