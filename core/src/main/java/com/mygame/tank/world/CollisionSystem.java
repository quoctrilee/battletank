package com.mygame.tank.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.List;

/**
 * Centralized AABB collision detection and resolution.
 * <p>
 * All collision logic lives here — entities do not check collisions themselves.
 * This prevents duplicate logic and makes it easy to change collision behaviour globally.
 * <p>
 * Now uses the unified {@link Tank} type for both player and enemy resolution.
 */
public class CollisionSystem {

    /**
     * Max passes per frame when resolving a tank against static geometry (walls/doors).
     */
    private static final int MAX_RESOLVE_ITERATIONS = 3;

    // ─── Tank vs walls ───────────────────────────────────────────────────────

    /**
     * Resolve any tank (player or enemy) against all wall rectangles.
     * Runs multiple passes so a push-out from one wall that creates a new
     * overlap with another wall (e.g. in a corner) gets cleaned up in the
     * same frame instead of jittering across frames.
     *
     * @return the accumulated push-out vector applied this call (zero vector
     * if no wall was hit). Callers that care about wall collisions
     * (e.g. a bouncing boss) can use this as a rough collision normal
     * without CollisionSystem exposing wall geometry to them.
     */
    public Vector2 resolveEntityVsWalls(Tank tank, List<Rectangle> walls) {
        Vector2 totalPush = new Vector2();
        for (int iter = 0; iter < MAX_RESOLVE_ITERATIONS; iter++) {
            boolean anyOverlap = false;
            for (Rectangle wall : walls) {
                Vector2 push = resolveAgainstRect(tank, wall);
                if (push != null) {
                    totalPush.add(push);
                    anyOverlap = true;
                }
            }
            if (!anyOverlap) break;
        }
        return totalPush;
    }

    // ─── Player tank vs door ─────────────────────────────────────────────────

    /**
     * Check a closed door for player collision and push the player back.
     *
     * @param door   the door rectangle (world coords)
     * @param isOpen if true, skip — player can pass through
     */
    public void resolveTankVsDoor(Tank tank, Rectangle door, boolean isOpen) {
        if (isOpen) return;
        resolveAgainstRect(tank, door);
    }

    // ─── Tank vs tank ────────────────────────────────────────────────────────

    /**
     * Resolve every pair of tanks in the list against each other so two tanks
     * (player or enemy, alive) don't overlap. Each tank in a colliding pair is
     * pushed out by half the overlap, so both sides yield equally instead of
     * one tank "winning" and shoving the other around.
     * <p>
     * Call this once per frame after movement, alongside resolveEntityVsWalls.
     */
    public void resolveTankVsTank(List<Tank> tanks) {
        for (int iter = 0; iter < MAX_RESOLVE_ITERATIONS; iter++) {
            boolean anyOverlap = false;
            for (int i = 0; i < tanks.size(); i++) {
                Tank a = tanks.get(i);
                if (!a.isAlive()) continue;
                for (int j = i + 1; j < tanks.size(); j++) {
                    Tank b = tanks.get(j);
                    if (!b.isAlive()) continue;
                    if (a.getBounds().overlaps(b.getBounds())) {
                        splitPushout(a, b);
                        anyOverlap = true;
                    }
                }
            }
            if (!anyOverlap) break;
        }
    }

    /**
     * Push two overlapping tanks apart by half the overlap each.
     */
    private void splitPushout(Tank a, Tank b) {
        Vector2 push = calcPushout(a.getBounds(), b.getBounds());
        Vector2 half = new Vector2(push.x * 0.5f, push.y * 0.5f);

        a.resolvePosition(
            a.getPosition().x + half.x,
            a.getPosition().y + half.y);
        b.resolvePosition(
            b.getPosition().x - half.x,
            b.getPosition().y - half.y);
    }

    // ─── Projectile vs walls / doors ─────────────────────────────────────────

    /**
     * Check whether a projectile overlaps any wall.
     *
     * @return true if it hit a wall (caller should destroy the projectile)
     */
    public boolean checkProjectileVsWalls(Projectile proj, List<Rectangle> walls) {
        Rectangle pb = proj.getBounds();
        for (Rectangle wall : walls) {
            if (pb.overlaps(wall)) return true;
        }
        return false;
    }

    /**
     * Check whether a projectile overlaps a closed door.
     *
     * @return true if hit (caller should destroy the projectile)
     */
    public boolean checkProjectileVsDoor(Projectile proj, Rectangle door, boolean isOpen) {
        return !isOpen && proj.getBounds().overlaps(door);
    }

    // ─── Projectile vs tank ──────────────────────────────────────────────────

    /**
     * Check projectile against a list of enemy tanks; return the first alive hit (or null).
     */
    public Tank checkProjectileVsEnemies(Projectile proj, List<Tank> enemies) {
        Rectangle pb = proj.getBounds();
        for (Tank enemy : enemies) {
            if (enemy.isAlive() && pb.overlaps(enemy.getBounds())) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * Check an enemy projectile against the player tank.
     *
     * @return true if hit
     */
    public boolean checkProjectileVsTank(Projectile proj, Tank tank) {
        return tank.isAlive() && proj.getBounds().overlaps(tank.getBounds());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Shared resolve step used by both walls and doors: if the tank overlaps
     * the given rect, push it out along the minimum-overlap axis.
     *
     * @return the push-out vector applied, or null if there was no overlap
     */
    private Vector2 resolveAgainstRect(Tank tank, Rectangle rect) {
        if (!tank.getBounds().overlaps(rect)) return null;
        Vector2 push = calcPushout(tank.getBounds(), rect);
        tank.resolvePosition(
            tank.getPosition().x + push.x,
            tank.getPosition().y + push.y);
        return push;
    }

    /**
     * Minimum-overlap axis-aligned pushout vector.
     * Returns the vector to apply to r1 so it no longer overlaps r2.
     */
    private Vector2 calcPushout(Rectangle r1, Rectangle r2) {
        float overlapLeft = (r1.x + r1.width) - r2.x;
        float overlapRight = (r2.x + r2.width) - r1.x;
        float overlapBottom = (r1.y + r1.height) - r2.y;
        float overlapTop = (r2.y + r2.height) - r1.y;

        float minX = (overlapLeft < overlapRight) ? -overlapLeft : overlapRight;
        float minY = (overlapBottom < overlapTop) ? -overlapBottom : overlapTop;

        return Math.abs(minX) <= Math.abs(minY)
            ? new Vector2(minX, 0f)
            : new Vector2(0f, minY);
    }
}
