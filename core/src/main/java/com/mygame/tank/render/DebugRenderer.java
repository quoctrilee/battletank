package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.dungeon.Room;
import com.mygame.tank.world.GameWorld;

import java.util.Map;

/**
 * Debug overlay rendered on F1 toggle.
 * Shows: hitboxes, collision rects, door states, area boundaries, spawn points.
 * Zero performance impact when disabled (early return).
 */
public class DebugRenderer {

    private final ShapeRenderer shapeRenderer;
    private boolean enabled;

    public DebugRenderer(boolean enabledDefault) {
        shapeRenderer = new ShapeRenderer();
        this.enabled = enabledDefault;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void render(OrthographicCamera camera, GameWorld world) {
        if (!enabled) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        DungeonMap map = world.getDungeonMap();

        // Collision rects (white, semi-transparent)
        shapeRenderer.setColor(1f, 1f, 1f, 0.5f);
        for (Rectangle r : map.getCollisionRects()) {
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        }

        // Door rects (boss + entrance) — green = open, red/brown = closed
        for (Map.Entry<String, Rectangle> entry : map.getDoorRects().entrySet()) {
            boolean open = world.getRoomManager().isDoorOpen(entry.getKey());
            shapeRenderer.setColor(open ? new Color(0f, 1f, 0f, 0.9f)
                : new Color(1f, 0f, 0f, 0.9f));
            Rectangle r = entry.getValue();
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        }
        for (Map.Entry<String, Rectangle> entry : map.getEntranceDoorRects().entrySet()) {
            boolean open = world.getRoomManager().isDoorOpen(entry.getKey());
            shapeRenderer.setColor(open ? new Color(0.2f, 0.8f, 0.2f, 0.7f)
                : new Color(0.8f, 0.4f, 0.1f, 0.9f));
            Rectangle r = entry.getValue();
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        }

        // Area boundaries (blue) - nay là Room bounds
        shapeRenderer.setColor(0.2f, 0.5f, 1f, 0.4f);
        for (Room room : map.getRooms()) {
            Rectangle r = room.bounds;
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        }

        // Spawn points
        shapeRenderer.setColor(1f, 1f, 0f, 0.8f);
        if (map.getPlayerSpawn() != null) {
            shapeRenderer.circle(map.getPlayerSpawn().x, map.getPlayerSpawn().y, 10f);
        }
        shapeRenderer.setColor(1f, 0f, 0f, 0.8f);
        for (java.util.List<Vector2> spawns : map.getEnemySpawns().values()) {
            for (Vector2 p : spawns) {
                shapeRenderer.circle(p.x, p.y, 8f);
            }
        }

        // Player hitbox (cyan)
        shapeRenderer.setColor(Color.CYAN);
        Rectangle pb = world.getPlayer().getBounds();
        shapeRenderer.rect(pb.x, pb.y, pb.width, pb.height);

        // Enemy hitboxes (orange)
        shapeRenderer.setColor(Color.ORANGE);
        for (Tank enemy : world.getEnemies()) {
            if (!enemy.isAlive()) continue;
            Rectangle eb = enemy.getBounds();
            shapeRenderer.rect(eb.x, eb.y, eb.width, eb.height);
        }

        // Boss hitbox (magenta)
        if (world.getBoss() != null && world.getBoss().isAlive()) {
            shapeRenderer.setColor(Color.MAGENTA);
            Rectangle bb = world.getBoss().getBounds();
            shapeRenderer.rect(bb.x, bb.y, bb.width, bb.height);
        }

        // Projectile hitboxes (yellow)
        shapeRenderer.setColor(Color.YELLOW);
        for (Projectile proj : world.getProjectiles()) {
            if (!proj.isAlive()) continue;
            Rectangle rb = proj.getBounds();
            shapeRenderer.rect(rb.x, rb.y, rb.width, rb.height);
        }

        // Boss AI circle center
        for (Tank boss : world.getBosses()) {
            if (!boss.isAlive()) continue;
            BossController bCtrl = (BossController) boss.getController();
            shapeRenderer.setColor(1f, 0f, 1f, 0.5f);
            Vector2 cc = bCtrl.getCircleCenter();
            shapeRenderer.circle(cc.x, cc.y, 200f, 24);
        }

        shapeRenderer.end();
    }

    private void drawCross(float x, float y, float size) {
        shapeRenderer.line(x - size, y, x + size, y);
        shapeRenderer.line(x, y - size, x, y + size);
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
