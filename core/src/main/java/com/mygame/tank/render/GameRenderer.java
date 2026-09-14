package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.dungeon.Room;
import com.mygame.tank.dungeon.RoomProgressionManager;
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.VisualEffect;
import com.mygame.tank.entity.WorldEffect;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;
import com.mygame.tank.weapon.EquipmentType;
import com.mygame.tank.weapon.WeaponSystem;
import com.mygame.tank.weapon.WeaponType;
import com.mygame.tank.world.GameWorld;

import java.util.Map;

/**
 * Renders all visual elements using colored shapes (no sprite assets required).
 *
 * <h3>Rendering order</h3>
 * <ol>
 *   <li>Map background (floor, walls, doors) — world space</li>
 *   <li>World effects (smoke, mines, EMP) — world space</li>
 *   <li>Entities (enemies, boss, player, projectiles, laser beams) — world space</li>
 *   <li>Health bars + stun/EMP indicators — world space</li>
 *   <li>HUD (HP bar, 3 weapon slots, 3 equipment slots, ammo/cooldown) — screen space</li>
 *   <li>Weapon Hub overlay (when Tab is held) — screen space</li>
 *   <li>Game-state overlay (WIN / LOSE) — screen space</li>
 * </ol>
 */
public class GameRenderer {

    // ─── World colors ─────────────────────────────────────────────────────────
    private static final Color COLOR_FLOOR = new Color(0.13f, 0.16f, 0.11f, 1f);
    private static final Color COLOR_WALL = new Color(0.35f, 0.30f, 0.22f, 1f);
    private static final Color COLOR_DOOR_OPEN = new Color(0.10f, 0.50f, 0.15f, 1f);
    private static final Color COLOR_DOOR_CLOSE = new Color(0.55f, 0.08f, 0.08f, 1f);

    private static final Color COLOR_PLAYER_BODY = new Color(0.25f, 0.75f, 0.30f, 1f);
    private static final Color COLOR_PLAYER_TURRET = new Color(0.15f, 0.55f, 0.20f, 1f);
    private static final Color COLOR_ENEMY_BODY = new Color(0.80f, 0.15f, 0.15f, 1f);
    private static final Color COLOR_BOSS_BODY = new Color(0.90f, 0.45f, 0.05f, 1f);
    private static final Color COLOR_BOSS_P2 = new Color(0.95f, 0.10f, 0.60f, 1f);

    // Projectile colors by type
    private static final Color COLOR_BULLET_DEFAULT = new Color(1.00f, 0.95f, 0.20f, 1f);
    private static final Color COLOR_BULLET_SMG = new Color(1.00f, 0.85f, 0.10f, 1f);
    private static final Color COLOR_BULLET_AP = new Color(0.60f, 0.90f, 1.00f, 1f);
    private static final Color COLOR_BULLET_CANNON = new Color(1.00f, 0.50f, 0.10f, 1f);
    private static final Color COLOR_BULLET_STUN = new Color(0.50f, 0.20f, 1.00f, 1f);
    private static final Color COLOR_BULLET_HOMING = new Color(1.00f, 0.20f, 0.80f, 1f);
    private static final Color COLOR_BULLET_ENEMY = new Color(1.00f, 0.40f, 0.10f, 1f);

    // Laser colors
    private static final Color COLOR_LASER_TELEGRAPH = new Color(1.00f, 1.00f, 0.20f, 0.40f);
    private static final Color COLOR_LASER_BEAM = new Color(1.00f, 1.00f, 0.00f, 1.00f);

    // Effect colors
    private static final Color COLOR_SMOKE = new Color(0.60f, 0.65f, 0.75f, 0.45f);
    private static final Color COLOR_MINE_ARMED = new Color(1.00f, 0.20f, 0.10f, 1.00f);
    private static final Color COLOR_MINE_ARMING = new Color(1.00f, 0.80f, 0.10f, 1.00f);
    private static final Color COLOR_EMP = new Color(0.40f, 0.10f, 1.00f, 0.50f);
    private static final Color COLOR_SHIELD = new Color(0.30f, 0.80f, 1.00f, 0.80f);

    // ─── Rendering infrastructure ─────────────────────────────────────────────
    private final ShapeRenderer shapeRenderer;
    /**
     * SpriteBatch for world-space sprite rendering (tank bodies, projectiles).
     */
    private final SpriteBatch worldBatch;
    private final SpriteBatch hudBatch;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final OrthographicCamera hudCamera;
    /**
     * Loaded sprite textures / regions.
     */
    private final SpriteAssets assets;

    /**
     * Optional Android input source — set via {@link #setAndroidInputSource}
     * to enable floating joystick + zone overlay rendering.
     */
    private com.mygame.tank.controller.player.AndroidInputSource androidInput = null;

    // ─── In-game pause / settings ──────────────────────────────────────────────

    /** Whether the pause overlay should be drawn this frame. */
    private boolean showPause = false;
    /** Whether the game is currently muted (reflected on the mute button label). */
    private boolean gameMuted = false;
    /** Accumulated play time in seconds — set by GameScreen each frame. */
    private float gameTimer  = 0f;

    // Settings button (HUD coords, Y=0 bottom) — updated each renderHud call.
    private final com.badlogic.gdx.math.Rectangle settingsBtnRect    = new com.badlogic.gdx.math.Rectangle();
    // Pause menu buttons (HUD coords) — updated each renderPauseMenu call.
    private final com.badlogic.gdx.math.Rectangle pauseBackRect      = new com.badlogic.gdx.math.Rectangle();
    private final com.badlogic.gdx.math.Rectangle pauseMuteRect      = new com.badlogic.gdx.math.Rectangle();
    private final com.badlogic.gdx.math.Rectangle pauseControlsRect  = new com.badlogic.gdx.math.Rectangle();

    /** Radius of the settings button touch target (px). */
    public static final int SETTINGS_BTN_RADIUS = 30;
    /** Distance from the top-right corner to the settings button centre (px). */
    public static final int SETTINGS_BTN_MARGIN = 48;

    public GameRenderer() {
        shapeRenderer = new ShapeRenderer();
        worldBatch = new SpriteBatch();
        hudBatch = new SpriteBatch();
        font = new BitmapFont();
        layout = new GlyphLayout();
        hudCamera = new OrthographicCamera();
        assets = new SpriteAssets();
    }

    // ─── Entry point ──────────────────────────────────────────────────────────

    public void render(OrthographicCamera worldCamera, GameWorld world) {
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hudCamera.update();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        renderWorld(worldCamera, world);
        renderHud(world);
    }

    // ─── World-space rendering ────────────────────────────────────────────────

    private void renderWorld(OrthographicCamera camera, GameWorld world) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        worldBatch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ── Pass 1: Vẽ void đen khắp map — bất kỳ vùng nào chưa có floor sẽ hiện đen ──
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.06f, 0.06f, 0.07f, 1f);
        shapeRenderer.rect(0, 0, GameConfig.MAP_WIDTH, GameConfig.MAP_HEIGHT);
        shapeRenderer.end();

        // ── Pass 2: Floor texture (background tiles, ghép ngẫu nhiên không trùng liên tiếp)
        worldBatch.begin();
        renderFloorTiles(world);
        worldBatch.end();

        // ── Pass 3: Wall + cover + doors (shape + sprite)
        worldBatch.begin();
        renderWallSprites(world);
        worldBatch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderDoors(world);
        shapeRenderer.end();

        // ── Pass 4: World effects + enemies (shapes)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderWorldEffects(world);
        renderEnemies(world);
        renderNonDefaultProjectiles(world);
        renderLaserBeams(world);
        shapeRenderer.end();

        // ── Pass 5: Player + boss sprites
        worldBatch.begin();
        renderPlayerSprites(world);
        renderBossSprites(world);
        renderDefaultBulletSprites(world);
        renderBossBulletSprites(world);
        worldBatch.end();

        // ── Pass 6: Additive VFX
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        worldBatch.begin();
        renderMuzzleFlashSprites(world);
        worldBatch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderNonMuzzleVisualEffects(world);
        shapeRenderer.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ── Pass 7: Health bars (world space)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderHealthBars(world);
        shapeRenderer.end();

        // ── Pass 8: Fog of War
        renderFogOfWar(camera, world);
    }

    // ─── Floor tile rendering (background.jpg ─ 4 sub-tiles ngẫu nhiên không trùng liên tiếp)

    /**
     * Vẽ floor theo từng tile 48×48:
     * - Mỗi tile chọn 1 trong 4 sub-tiles từ background.jpg.
     * - Không trùng sub-tile với tile liền trước (lastIdx tracking).
     * - Phòng và hành lang đều dùng chung 4 sub-tiles, hành lang hơi tối hơn (tint).
     */
    private void renderFloorTiles(GameWorld world) {
        DungeonMap dm = world.getDungeonMap();
        int tile = GameConfig.MAP_TILE_SIZE;
        // Sử dụng hash tạo giá trị pseudo-random n hưng cố định theo tọa độ tile
        // để map giống nhau mỗi frame, nhưng khác nhau mỗi tile
        java.util.function.BiFunction<Integer, Integer, Integer> tileHash = (col, row) -> {
            // Wang hash-like: dùng XOR + prime
            int h = col * 1619 ^ row * 31337;
            h = (h ^ (h >>> 16)) * 0x45d9f3b;
            h = (h ^ (h >>> 16));
            return Math.abs(h) % 4;
        };

        // Vẽ floor phòng
        for (Room room : dm.getRooms()) {
            int c0 = (int) (room.bounds.x / tile);
            int c1 = (int) ((room.bounds.x + room.bounds.width) / tile);
            int r0 = (int) (room.bounds.y / tile);
            int r1 = (int) ((room.bounds.y + room.bounds.height) / tile);

            for (int row = r0; row < r1; row++) {
                int lastIdx = -1;
                for (int col = c0; col < c1; col++) {
                    int idx = tileHash.apply(col, row);
                    // Không trùng liên tiếp
                    if (idx == lastIdx) idx = (idx + 1) % 4;
                    lastIdx = idx;

                    worldBatch.setColor(1f, 1f, 1f, 1f);
                    worldBatch.draw(assets.floorTiles[idx],
                        col * tile, row * tile, tile, tile);
                }
            }
        }

        // Vẽ floor hành lang (hơi tối để phân biệt với phòng)
        for (com.mygame.tank.dungeon.Corridor corr : dm.getCorridors()) {
            for (Rectangle seg : corr.segments) {
                int c0 = (int) (seg.x / tile);
                int c1 = (int) ((seg.x + seg.width) / tile);
                int r0 = (int) (seg.y / tile);
                int r1 = (int) ((seg.y + seg.height) / tile);
                for (int row = r0; row < r1; row++) {
                    int lastIdx = -1;
                    for (int col = c0; col < c1; col++) {
                        int idx = tileHash.apply(col, row);
                        if (idx == lastIdx) idx = (idx + 1) % 4;
                        lastIdx = idx;
                        worldBatch.setColor(0.75f, 0.75f, 0.75f, 1f); // tối nhẹ
                        worldBatch.draw(assets.floorTiles[idx],
                            col * tile, row * tile, tile, tile);
                    }
                }
            }
        }
        worldBatch.setColor(1f, 1f, 1f, 1f);
    }

    // ─── Wall sprite rendering (wall.png + cover.png) ─────────────────────────

    /**
     * Vẽ tường bằng wall.png texture (tiled qua UV scale).
     * Gọi khi worldBatch đang mở.
     */
    private void renderWallSprites(GameWorld world) {
        DungeonMap dm = world.getDungeonMap();
        int tile = GameConfig.MAP_TILE_SIZE;

        worldBatch.setColor(1f, 1f, 1f, 1f);
        for (Rectangle r : dm.getCollisionRects()) {
            // Vẽ từng ô tile×tile — cùng kích thước với floor background tile
            int c0 = (int) (r.x / tile);
            int c1 = (int) ((r.x + r.width) / tile);
            int r0 = (int) (r.y / tile);
            int r1 = (int) ((r.y + r.height) / tile);
            for (int row = r0; row < r1; row++) {
                for (int col = c0; col < c1; col++) {
                    worldBatch.draw(assets.wallTexture,
                        col * tile, row * tile, tile, tile);
                }
            }
        }
    }

    /**
     * Vẽ cửa boss/entrance bằng màu shape.
     * Gọi khi shapeRenderer đang mở FILLED.
     */
    private void renderDoors(GameWorld world) {
        DungeonMap dm = world.getDungeonMap();
        RoomProgressionManager rm = world.getRoomManager();

        // Boss doors
        for (Map.Entry<String, Rectangle> entry : dm.getDoorRects().entrySet()) {
            boolean open = rm.isDoorOpen(entry.getKey());
            if (!open) {
                shapeRenderer.setColor(COLOR_DOOR_CLOSE);
                Rectangle r = entry.getValue();
                shapeRenderer.rect(r.x, r.y, r.width, r.height);
            }
        }

        // Entrance doors
        for (Map.Entry<String, Rectangle> entry : dm.getEntranceDoorRects().entrySet()) {
            boolean open = rm.isDoorOpen(entry.getKey());
            if (!open) {
                shapeRenderer.setColor(new Color(0.6f, 0.3f, 0.05f, 1f)); // tối hơn boss door
                Rectangle r = entry.getValue();
                shapeRenderer.rect(r.x, r.y, r.width, r.height);
            }
        }
    }

    private void renderWorldEffects(GameWorld world) {
        for (WorldEffect effect : world.getWorldEffects()) {
            if (!effect.isAlive()) continue;

            if (effect instanceof WorldEffect.SmokeBomb smoke) {
                float r = smoke.getRadius();
                shapeRenderer.setColor(COLOR_SMOKE);
                shapeRenderer.circle(smoke.getPosition().x, smoke.getPosition().y, r, 24);

            } else if (effect instanceof WorldEffect.Mine mine) {
                float size = 8f;
                Color c = mine.isArmed() ? COLOR_MINE_ARMED : COLOR_MINE_ARMING;
                // Blink when armed
                float alpha = mine.isArmed() ? ((System.currentTimeMillis() % 500 < 250) ? 1f : 0.3f) : 1f;
                shapeRenderer.setColor(c.r, c.g, c.b, alpha);
                // Draw diamond shape
                float mx = mine.getPosition().x, my = mine.getPosition().y;
                shapeRenderer.triangle(mx, my + size, mx + size, my, mx, my - size);
                shapeRenderer.triangle(mx, my + size, mx - size, my, mx, my - size);

            } else if (effect instanceof WorldEffect.EmpField emp) {
                float alpha = emp.getPulseAlpha();
                shapeRenderer.setColor(COLOR_EMP.r, COLOR_EMP.g, COLOR_EMP.b, alpha);
                shapeRenderer.circle(emp.getPosition().x, emp.getPosition().y, emp.getRadius(), 32);

            } else if (effect instanceof WorldEffect.EnergyShield shield) {
                float alpha = shield.getRingAlpha();
                shapeRenderer.setColor(COLOR_SHIELD.r, COLOR_SHIELD.g, COLOR_SHIELD.b, alpha);
                // Draw ring as thick circle
                float cx = shield.getPosition().x, cy = shield.getPosition().y;
                float r = GameConfig.PLAYER_WIDTH * 0.75f;
                for (int i = 0; i < 3; i++) {
                    shapeRenderer.circle(cx, cy, r + i, 24);
                }
            }
        }
    }

    private void renderEnemies(GameWorld world) {
        for (Tank enemy : world.getEnemies()) {
            if (!enemy.isAlive()) continue;
            float alpha = enemy.isStunned() ? 0.5f : 1f;
            drawTankBody(enemy.getPosition().x, enemy.getPosition().y,
                enemy.getBodyAngle(), GameConfig.ENEMY_WIDTH,
                COLOR_ENEMY_BODY, alpha);
            drawBarrel(enemy.getPosition().x, enemy.getPosition().y,
                enemy.getBodyAngle(), GameConfig.ENEMY_WIDTH * 0.55f,
                GameConfig.ENEMY_WIDTH * 0.18f,
                COLOR_ENEMY_BODY.cpy().mul(0.7f, 0.7f, 0.7f, alpha));
            // EMP indicator: purple ring
            if (enemy.isEmpSuppressed()) {
                shapeRenderer.setColor(0.6f, 0.1f, 1.0f, 0.6f);
                shapeRenderer.circle(enemy.getPosition().x, enemy.getPosition().y,
                    GameConfig.ENEMY_WIDTH * 0.65f, 16);
            }
        }
    }

    // ─── Sprite-based player rendering ───────────────────────────────────────

    /**
     * Renders the player tank body and turret using VisualComponent texture keys.
     * The sprite convention is: faces UP in the source image; we rotate by (angle - 90°).
     */
    private void renderPlayerSprites(GameWorld world) {
        Tank player = world.getPlayer();
        if (!player.isAlive()) return;

        com.mygame.tank.entity.component.VisualComponent visual = player.getVisual();
        if (visual == null) return;

        float alpha = player.isStunned()
            ? ((System.currentTimeMillis() % 300 < 150) ? 0.3f : 0.8f)
            : 1.0f;

        float px = player.getPosition().x;
        float py = player.getPosition().y;

        // ── Body ──────────────────────────────────────────────────────────────
        float bodyW   = GameConfig.PLAYER_WIDTH  * visual.getBodyScale();
        float bodyH   = GameConfig.PLAYER_HEIGHT * visual.getBodyScale();
        float bodyRot = player.getBodyAngle() - 90f;
        com.badlogic.gdx.graphics.Color tint = visual.getCurrentTint();
        worldBatch.setColor(tint.r, tint.g, tint.b, alpha);
        worldBatch.draw(assets.getTexture(visual.getBodyTextureId()),
            px - bodyW / 2f, py - bodyH / 2f,
            bodyW / 2f, bodyH / 2f,
            bodyW, bodyH,
            1f, 1f, bodyRot);

        // ── Turret ────────────────────────────────────────────────────────────
        float turretSize = GameConfig.PLAYER_WIDTH * visual.getTurretScale();
        float turretRot  = player.getTurretAngle() - 90f;
        worldBatch.setColor(tint.r, tint.g, tint.b, alpha);
        worldBatch.draw(assets.getTexture(visual.getTurretTextureId()),
            px - turretSize / 2f, py - turretSize / 2f,
            turretSize / 2f, turretSize / 2f,
            turretSize, turretSize,
            1f, 1f, turretRot);

        worldBatch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /**
     * Renders default (NORMAL) player bullets as the bullet sprite.
     * The sprite faces UP; rotation = atan2(dy, dx) - 90° converts to game convention.
     */
    private void renderDefaultBulletSprites(GameWorld world) {
        for (Projectile proj : world.getProjectiles()) {
            if (!proj.isAlive()) continue;
            if (proj.getOwner() != Projectile.Owner.PLAYER) continue;
            if (proj.getType() != Projectile.ProjectileType.NORMAL) continue;

            float bx = proj.getPosition().x;
            float by = proj.getPosition().y;
            float bulletSize = GameConfig.PLAYER_WIDTH * 1.4f;

            // Direction angle → sprite rotation
            float angleDeg = MathUtils.atan2(proj.getDirection().y, proj.getDirection().x)
                * MathUtils.radiansToDegrees;
            float spriteRot = angleDeg - 90f;

            worldBatch.setColor(Color.WHITE);
            worldBatch.draw(assets.defaultBullet,
                bx - bulletSize / 2f, by - bulletSize / 2f,
                bulletSize / 2f, bulletSize / 2f,
                bulletSize, bulletSize,
                1f, 1f,
                spriteRot);
        }
    }

    /**
     * Renders MUZZLE_FLASH visual effects using the muzzle flash sprite
     * with additive blending (called from Pass 3).
     */
    private void renderMuzzleFlashSprites(GameWorld world) {
        for (VisualEffect vfx : world.getVisualEffects()) {
            if (!vfx.isAlive()) continue;
            if (vfx.getType() != VisualEffect.Type.MUZZLE_FLASH) continue;

            float progress = vfx.getProgress();
            float alpha = 1f - progress;
            float size = vfx.getRadius() * 2.5f * (1f - progress * 0.4f);
            float px = vfx.getPosition().x;
            float py = vfx.getPosition().y;
            float spriteRot = vfx.getAngle() - 90f;

            worldBatch.setColor(1f, 1f, 1f, alpha);
            worldBatch.draw(assets.defaultMuzzleFlash,
                px - size / 2f, py - size / 2f,
                size / 2f, size / 2f,
                size, size,
                1f, 1f,
                spriteRot);
        }
        worldBatch.setColor(Color.WHITE);
    }

    /**
     * Renders non-muzzle-flash visual effects using ShapeRenderer with additive blending.
     * MUZZLE_FLASH is handled separately by {@link #renderMuzzleFlashSprites} using a sprite.
     */
    private void renderNonMuzzleVisualEffects(GameWorld world) {
        for (VisualEffect vfx : world.getVisualEffects()) {
            if (!vfx.isAlive()) continue;
            if (vfx.getType() == VisualEffect.Type.MUZZLE_FLASH) continue; // handled by sprite pass

            float progress = vfx.getProgress(); // 0 to 1
            float px = vfx.getPosition().x;
            float py = vfx.getPosition().y;
            Color c = vfx.getColor();

            switch (vfx.getType()) {
                case EXPLOSION_AOE: {
                    // Expanding ring, fading out
                    float r = vfx.getRadius() * (0.2f + 0.8f * progress);
                    shapeRenderer.setColor(c.r, c.g, c.b, 1f - progress);
                    shapeRenderer.circle(px, py, r, 16);
                    // Inner bright flash
                    shapeRenderer.setColor(1f, 1f, 1f, (1f - progress) * 0.5f);
                    shapeRenderer.circle(px, py, r * 0.5f, 12);
                    break;
                }
                case EXPLOSION_SMALL: {
                    // Small puff
                    float r = vfx.getRadius() * (0.5f + 0.5f * progress);
                    shapeRenderer.setColor(c.r, c.g, c.b, 1f - progress);
                    shapeRenderer.circle(px, py, r, 8);
                    break;
                }
                case LASER_HIT: {
                    // Sparks
                    shapeRenderer.setColor(c.r, c.g, c.b, 1f - progress);
                    shapeRenderer.circle(px, py, vfx.getRadius() * (1f - progress), 6);
                    break;
                }
                default:
                    break;
            }
        }
    }


    /**
     * Renders all boss tanks using their {@link com.mygame.tank.entity.component.VisualComponent}.
     * Tint and scale are driven by the phase strategy — no hardcoded colours here.
     */
    private void renderBossSprites(GameWorld world) {
        for (Tank boss : world.getBosses()) {
            if (!boss.isAlive()) continue;

            com.mygame.tank.entity.component.VisualComponent visual = boss.getVisual();
            if (visual == null) continue;

            float alpha = boss.isStunned() ? 0.5f : 1f;
            com.badlogic.gdx.graphics.Color tint = visual.getCurrentTint();

            float px = boss.getPosition().x;
            float py = boss.getPosition().y;

            // Body
            float bodySize = boss.getStats().width * visual.getBodyScale();
            float bodyRot  = boss.getBodyAngle() - 90f;
            worldBatch.setColor(tint.r, tint.g, tint.b, alpha);
            worldBatch.draw(assets.getTexture(visual.getBodyTextureId()),
                px - bodySize / 2f, py - bodySize / 2f,
                bodySize / 2f, bodySize / 2f,
                bodySize, bodySize,
                1f, 1f, bodyRot);

            // Turret
            float turretSize = boss.getStats().width * visual.getTurretScale();
            float turretRot  = boss.getTurretAngle() - 90f;
            worldBatch.setColor(tint.r, tint.g, tint.b, alpha);
            worldBatch.draw(assets.getTexture(visual.getTurretTextureId()),
                px - turretSize / 2f, py - turretSize / 2f,
                turretSize / 2f, turretSize / 2f,
                turretSize, turretSize,
                1f, 1f, turretRot);
        }

        worldBatch.setColor(Color.WHITE);
    }

    private void renderBossBulletSprites(GameWorld world) {
        for (Projectile proj : world.getProjectiles()) {
            if (!proj.isAlive()) continue;
            if (proj.getOwner() != Projectile.Owner.BOSS) continue;

            float bx = proj.getPosition().x;
            float by = proj.getPosition().y;
            float bulletSize = GameConfig.PLAYER_WIDTH * 1.5f;

            float angleDeg = MathUtils.atan2(proj.getDirection().y, proj.getDirection().x)
                * MathUtils.radiansToDegrees;
            float spriteRot = angleDeg - 90f;

            // Use a default boss bullet key; per-boss bullets would require linking
            // the projectile back to its firing boss visual (future enhancement).
            worldBatch.setColor(Color.WHITE);
            worldBatch.draw(assets.getTexture("boss_iron_bullet"),
                bx - bulletSize / 2f, by - bulletSize / 2f,
                bulletSize / 2f, bulletSize / 2f,
                bulletSize, bulletSize,
                1f, 1f, spriteRot);
        }
    }

    // renderPlayer() removed — player is now drawn via renderPlayerSprites() in Pass 2.

    /**
     * Renders all projectiles EXCEPT player NORMAL bullets (those use a sprite in Pass 2).
     * Enemy bullets and all special weapon types are rendered as colored circles/shapes.
     */
    private void renderNonDefaultProjectiles(GameWorld world) {
        for (Projectile proj : world.getProjectiles()) {
            if (!proj.isAlive()) continue;

            // Player NORMAL bullets and BOSS bullets are handled by sprite passes
            if (proj.getOwner() == Projectile.Owner.PLAYER
                && proj.getType() == Projectile.ProjectileType.NORMAL) continue;
            if (proj.getOwner() == Projectile.Owner.BOSS) continue;

            Color c;
            float size = GameConfig.BULLET_SIZE / 2f;
            if (proj.getOwner() == Projectile.Owner.ENEMY) {
                c = COLOR_BULLET_ENEMY;
            } else {
                c = switch (proj.getType()) {
                    case PIERCING -> COLOR_BULLET_AP;
                    case AOE -> COLOR_BULLET_CANNON;
                    case STUN_AOE -> COLOR_BULLET_STUN;
                    case HOMING -> COLOR_BULLET_HOMING;
                    default -> COLOR_BULLET_DEFAULT;
                };
                // Bigger visual for AoE projectiles
                if (proj.getType() == Projectile.ProjectileType.AOE
                    || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
                    size *= 1.8f;
                }
            }
            shapeRenderer.setColor(c);
            shapeRenderer.circle(proj.getPosition().x, proj.getPosition().y, size, 8);
        }
    }

    private void renderLaserBeams(GameWorld world) {
        for (LaserBeam beam : world.getLaserBeams()) {
            if (!beam.isAlive()) continue;

            float ox = beam.getOrigin().x, oy = beam.getOrigin().y;
            float ex = beam.getEndPoint().x, ey = beam.getEndPoint().y;

            if (beam.isTelegraph()) {
                // Dashed telegraph line — draw as series of short segments
                shapeRenderer.setColor(COLOR_LASER_TELEGRAPH);
                float totalLen = beam.getRange();
                float segLen = 20f, gap = 12f, period = segLen + gap;
                float progress = beam.getPhaseProgress();
                // Fade in as telegraph progresses
                shapeRenderer.setColor(COLOR_LASER_TELEGRAPH.r, COLOR_LASER_TELEGRAPH.g,
                    COLOR_LASER_TELEGRAPH.b, 0.2f + 0.5f * progress);
                float t = 0f;
                while (t < totalLen) {
                    float t2 = Math.min(t + segLen, totalLen);
                    float frac1 = t / totalLen, frac2 = t2 / totalLen;
                    shapeRenderer.rectLine(
                        ox + (ex - ox) * frac1, oy + (ey - oy) * frac1,
                        ox + (ex - ox) * frac2, oy + (ey - oy) * frac2,
                        2f);
                    t += period;
                }
            } else {
                // Solid bright beam
                float progress = beam.getPhaseProgress();
                float width = 6f * (1f - progress * 0.5f); // thins out at end
                shapeRenderer.setColor(COLOR_LASER_BEAM);
                shapeRenderer.rectLine(ox, oy, ex, ey, width);
                // Inner glow
                shapeRenderer.setColor(1f, 1f, 1f, 0.8f);
                shapeRenderer.rectLine(ox, oy, ex, ey, width * 0.35f);
            }
        }
    }

    private void renderHealthBars(GameWorld world) {
        for (Tank enemy : world.getEnemies()) {
            if (!enemy.isAlive()) continue;
            float cy = enemy.getPosition().y + GameConfig.ENEMY_HEIGHT * 0.7f;
            drawHealthBar(enemy.getPosition().x, cy, 32f, 4f,
                enemy.getHp() / enemy.getMaxHp());
            if (enemy.isStunned()) {
                shapeRenderer.setColor(0.5f, 0.2f, 1f, 1f);
                shapeRenderer.rect(enemy.getPosition().x - 16f, cy + 6f,
                    32f * MathUtils.clamp(enemy.getHealth().getStunTimer() / GameConfig.STUN_DURATION, 0f, 1f), 3f);
            }
        }
        // Multi-boss health bars (world space, above each boss)
        for (Tank boss : world.getBosses()) {
            if (!boss.isAlive()) continue;
            float cy = boss.getPosition().y + GameConfig.BOSS_HEIGHT * 0.85f;
            drawHealthBar(boss.getPosition().x, cy, 60f, 7f,
                boss.getHp() / boss.getMaxHp());
        }
    }

    // ─── Fog of War ───────────────────────────────────────────────────────────

    /**
     * Fog of War:
     * Dùng 4 dải hình chữ nhật đen che phủ phần ngoài "fog rect",
     * sau đó vẽ fogMask (nhân với scene) ở giữa.
     */
    private void renderFogOfWar(OrthographicCamera camera, GameWorld world) {
        Tank player = world.getPlayer();
        if (!player.isAlive()) return;

        float px = player.getPosition().x;
        float py = player.getPosition().y;
        float fogW = GameConfig.FOG_RADIUS_X * 2f;
        float fogH = GameConfig.FOG_RADIUS_Y * 2f;
        float fogX = px - fogW / 2f;
        float fogY = py - fogH / 2f;

        float camX = camera.position.x;
        float camY = camera.position.y;
        float halfVW = camera.viewportWidth / 2f;
        float halfVH = camera.viewportHeight / 2f;

        float left = camX - halfVW;
        float right = camX + halfVW;
        float bottom = camY - halfVH;
        float top = camY + halfVH;

        // ── Bước 1: Fill đen vùng ngoài fog mask (4 dải: Bottom, Top, Left, Right) ──
        shapeRenderer.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 1f);
        // Dải Bottom
        shapeRenderer.rect(left, bottom, camera.viewportWidth, fogY - bottom);
        // Dải Top
        shapeRenderer.rect(left, fogY + fogH, camera.viewportWidth, top - (fogY + fogH));
        // Dải Left (kẹp giữa Bottom và Top)
        shapeRenderer.rect(left, fogY, fogX - left, fogH);
        // Dải Right (kẹp giữa Bottom và Top)
        shapeRenderer.rect(fogX + fogW, fogY, right - (fogX + fogW), fogH);
        shapeRenderer.end();

        // ── Bước 2: Vẽ fog mask (mềm) ở khu vực fog rect bằng multiplicative blend ──
        worldBatch.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        worldBatch.begin();
        worldBatch.setBlendFunction(GL20.GL_ZERO, GL20.GL_SRC_COLOR);
        worldBatch.setColor(1f, 1f, 1f, 1f);
        worldBatch.draw(assets.fogMask, fogX, fogY, fogW, fogH);
        worldBatch.end();
        worldBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Reloads the player body texture from the currently selected skin.
     * Call before starting a new game world so skin-screen changes take effect.
     */
    public void reloadPlayerBody() {
        assets.reloadPlayerBody();
    }

    /**
     * Thông báo khi screen resize (hiện tại không cần action).
     */
    public void onResize() { /* reserved for future framebuffer resize */ }

    // ─── Screen-space HUD ────────────────────────────────────────────────────

    /**
     * Provide the Android input source so the renderer can draw the
     * floating joystick + zone overlay on top of the HUD.
     * Call once after creating {@link AndroidInputSource}; pass {@code null}
     * on Desktop.
     */
    public void setAndroidInputSource(
        com.mygame.tank.controller.player.AndroidInputSource src) {
        this.androidInput = src;
    }

    /**
     * Updates the pause-overlay rendering state. Call from {@link com.mygame.tank.screen.GameScreen}
     * before each {@link #render} call.
     *
     * @param paused whether the pause overlay should be shown
     * @param muted  whether the game audio is currently muted
     */
    public void setPauseState(boolean paused, boolean muted) {
        this.showPause = paused;
        this.gameMuted = muted;
    }

    /**
     * Updates the accumulated play timer shown on the HUD.
     * Call from {@link com.mygame.tank.screen.GameScreen} each frame before rendering.
     *
     * @param seconds total seconds played so far this session
     */
    public void setGameTimer(float seconds) {
        this.gameTimer = seconds;
    }

    // ─── Settings button hit-test (HUD coords, Y=0 at bottom) ─────────────────

    /** Returns true if the given HUD-space point hits the settings button. */
    public boolean isSettingsHit(float hudX, float hudY) {
        return settingsBtnRect.contains(hudX, hudY);
    }

    /** Returns the settings button bounding rect in HUD space (Y=0 at bottom). */
    public com.badlogic.gdx.math.Rectangle getSettingsBtnRect()   { return settingsBtnRect; }
    public com.badlogic.gdx.math.Rectangle getPauseBackRect()      { return pauseBackRect; }
    public com.badlogic.gdx.math.Rectangle getPauseMuteRect()      { return pauseMuteRect; }
    public com.badlogic.gdx.math.Rectangle getPauseControlsRect()  { return pauseControlsRect; }

    private void renderHud(GameWorld world) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Tank player = world.getPlayer();
        WeaponSystem ws = ((PlayerTurretComponent) player.getTurret()).getWeaponSystem();
        GameWorld.GameState state = world.getGameState();

        if (state == GameWorld.GameState.PLAYING) {
            renderHpBar(player, sh);
            renderWeaponSlots(ws, sw, sh);
            renderEquipmentSlots(ws, sw, sh);
            renderBossHpBar(world, sw, sh);  // multi-boss HUD

            // Weapon hub overlay
            if (ws != null && ws.isHubOpen()) {
                renderWeaponHub(ws, sw, sh);
            }

            // Android zone + joystick overlay
            if (androidInput != null) {
                renderFloatingJoystick(
                    androidInput.isMoveActive(),
                    androidInput.getMoveOriginX(), androidInput.getMoveOriginY(),
                    androidInput.getMoveKnobX(),   androidInput.getMoveKnobY(),
                    com.mygame.tank.controller.player.AndroidInputSource.MOVE_RADIUS,
                    new Color(0.25f, 0.75f, 0.30f, 0.85f));
                renderFloatingJoystick(
                    androidInput.isAimActive(),
                    androidInput.getAimOriginX(), androidInput.getAimOriginY(),
                    androidInput.getAimKnobX(),   androidInput.getAimKnobY(),
                    com.mygame.tank.controller.player.AndroidInputSource.AIM_RADIUS,
                    new Color(0.30f, 0.60f, 1.00f, 0.85f));
            }

            // Settings button (⚙) — only drawn while playing
            renderSettingsButton(sw, sh);

            // Pause overlay — drawn on top of everything when active
            if (showPause) renderPauseMenu(sw, sh);
        } else {
            // WIN or LOSE overlay — clean result panel without HUD clutter
            renderGameStateOverlay(state, sw, sh);
        }

        shapeRenderer.end();

        // ── Text + icon sprite pass ────────────────────────────────────────────
        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();
        font.setColor(Color.WHITE);

        if (state == GameWorld.GameState.PLAYING) {
            renderHpText(player, sh);
            renderWeaponSlotText(ws, sw, sh);
            renderEquipmentSlotText(ws, sw, sh);
            renderBossHudText(world, sw, sh);    // multi-boss text
            renderRoomProgressText(world.getRoomManager(), sw, sh);
            renderControlsHint(world.getGameState(), sh);

            // Draw weapon slot sprite icons on top of text (uses hudBatch which is already open)
            renderWeaponSlotIcons(ws, sw);
            renderEquipmentSlotIcons(ws, sw);

            if (ws != null && ws.isHubOpen()) {
                renderWeaponHubIcons(ws, sw, sh);
                renderWeaponHubText(ws, sw, sh);
            }

            // Android zone labels
            if (androidInput != null) {
                renderAndroidZoneLabels(sw, sh);
            }

            // Settings button label (⚙ / MENU text)
            renderSettingsButtonText(sw, sh);

            // Pause menu text
            if (showPause) renderPauseMenuText(sw, sh);
        } else {
            // End-game result text only — completely clean English display
            renderGameStateText(state, sw, sh);
        }

        hudBatch.end();
    }

    // ─── Settings button (⚙, top-right corner) ────────────────────────────────

    /**
     * Draws the in-game settings/menu button as a small hamburger-icon circle
     * in the top-right corner. Also updates {@link #settingsBtnRect} for hit-test.
     * Must be called inside an active shapeRenderer FILLED block.
     */
    private void renderSettingsButton(int sw, int sh) {
        float cx = sw - SETTINGS_BTN_MARGIN;
        float cy = sh - SETTINGS_BTN_MARGIN;
        float r  = SETTINGS_BTN_RADIUS;

        // Update hit-test rect (square around circle)
        settingsBtnRect.set(cx - r, cy - r, r * 2f, r * 2f);

        // Outer circle background
        shapeRenderer.setColor(showPause
            ? new Color(0.25f, 0.55f, 1.00f, 0.90f)
            : new Color(0.08f, 0.08f, 0.14f, 0.80f));
        shapeRenderer.circle(cx, cy, r, 28);

        // Three horizontal bars (hamburger icon)
        shapeRenderer.setColor(1f, 1f, 1f, showPause ? 1.0f : 0.85f);
        float bw = r * 1.0f, bh = 2.5f, bx = cx - bw / 2f;
        shapeRenderer.rect(bx, cy + 6,  bw, bh);
        shapeRenderer.rect(bx, cy,       bw, bh);
        shapeRenderer.rect(bx, cy - 8,  bw, bh);
    }

    /**
     * Draws the settings button label hint. Must be called while hudBatch is open.
     */
    private void renderSettingsButtonText(int sw, int sh) {
        // No additional text needed — the hamburger icon is self-explanatory
    }

    // ─── Pause overlay ────────────────────────────────────────────────────────

    private static final int PAUSE_PANEL_W  = 380;
    private static final int PAUSE_PANEL_H  = 280;
    private static final int PAUSE_BTN_W    = 300;
    private static final int PAUSE_BTN_H    = 58;
    private static final int PAUSE_BTN_GAP  = 16;
    private static final int PAUSE_CORNER_R = 12;

    /**
     * Renders the semi-transparent pause overlay with three action buttons.
     * Updates the pause button rectangles for hit-testing.
     * Must be called inside an active shapeRenderer FILLED block.
     */
    private void renderPauseMenu(int sw, int sh) {
        // Full-screen dim
        shapeRenderer.setColor(0f, 0f, 0f, 0.55f);
        shapeRenderer.rect(0, 0, sw, sh);

        // Panel background
        float px = sw / 2f - PAUSE_PANEL_W / 2f;
        float py = sh / 2f - PAUSE_PANEL_H / 2f;
        shapeRenderer.setColor(0.06f, 0.09f, 0.18f, 0.96f);
        shapeRenderer.rect(px, py, PAUSE_PANEL_W, PAUSE_PANEL_H);

        // Panel border
        shapeRenderer.setColor(0.25f, 0.55f, 1.00f, 0.80f);
        drawBorderRect(px, py, PAUSE_PANEL_W, PAUSE_PANEL_H, 2);

        // Three buttons: Controls (bottom), Mute (middle), Back (top)
        float bx   = sw / 2f - PAUSE_BTN_W / 2f;
        float btnY3 = py + 24;                          // Controls (bottom)
        float btnY2 = btnY3 + PAUSE_BTN_H + PAUSE_BTN_GAP;  // Mute (middle)
        float btnY1 = btnY2 + PAUSE_BTN_H + PAUSE_BTN_GAP;  // Back  (top)

        pauseControlsRect.set(bx, btnY3, PAUSE_BTN_W, PAUSE_BTN_H);
        pauseMuteRect    .set(bx, btnY2, PAUSE_BTN_W, PAUSE_BTN_H);
        pauseBackRect    .set(bx, btnY1, PAUSE_BTN_W, PAUSE_BTN_H);

        drawPauseBtn(pauseBackRect,     new Color(0.70f, 0.15f, 0.10f, 0.90f));
        drawPauseBtn(pauseMuteRect,     new Color(0.12f, 0.35f, 0.65f, 0.90f));
        drawPauseBtn(pauseControlsRect, new Color(0.10f, 0.28f, 0.14f, 0.90f));
    }

    private void drawPauseBtn(com.badlogic.gdx.math.Rectangle r, Color fill) {
        float cr = PAUSE_CORNER_R;
        shapeRenderer.setColor(fill);
        shapeRenderer.rect(r.x + cr, r.y, r.width - cr * 2, r.height);
        shapeRenderer.rect(r.x, r.y + cr, r.width, r.height - cr * 2);
        shapeRenderer.circle(r.x + cr,           r.y + cr,           cr, 16);
        shapeRenderer.circle(r.x + r.width - cr, r.y + cr,           cr, 16);
        shapeRenderer.circle(r.x + cr,           r.y + r.height - cr, cr, 16);
        shapeRenderer.circle(r.x + r.width - cr, r.y + r.height - cr, cr, 16);
        // Border
        shapeRenderer.setColor(1f, 1f, 1f, 0.25f);
        drawBorderRect(r.x, r.y, r.width, r.height, 1.5f);
    }

    /**
     * Draws text labels for the pause menu buttons. Must be called while hudBatch is open.
     */
    private void renderPauseMenuText(int sw, int sh) {
        float py = sh / 2f - PAUSE_PANEL_H / 2f;

        // Title
        font.getData().setScale(1.6f);
        font.setColor(new Color(0.55f, 0.85f, 1.00f, 1f));
        layout.setText(font, "PAUSED");
        font.draw(hudBatch, layout,
            sw / 2f - layout.width / 2f,
            py + PAUSE_PANEL_H - 14f);
        font.getData().setScale(1f);

        // Button labels
        font.setColor(Color.WHITE);
        drawPauseBtnLabel("BACK TO MENU",                 pauseBackRect);
        drawPauseBtnLabel(gameMuted ? "UNMUTE" : "MUTE",  pauseMuteRect);
        drawPauseBtnLabel("CONTROLS SETTINGS",            pauseControlsRect);
        font.setColor(Color.WHITE);
    }

    private void drawPauseBtnLabel(String text, com.badlogic.gdx.math.Rectangle r) {
        layout.setText(font, text);
        font.draw(hudBatch, layout,
            r.x + (r.width  - layout.width)  / 2f,
            r.y + (r.height + layout.height) / 2f);
    }

    // ─── Android overlay ──────────────────────────────────────────────────────

    /**
     * Draws zone labels (FIRE / MOVE / AIM) — called while hudBatch is open.
     */
    private void renderAndroidZoneLabels(int sw, int sh) {
        float split    = androidInput.getSplitRatio();
        float dividerY = sh * split; // HUD Y of fire/joystick boundary

        // "FIRE" — centred in the fire zone (above divider)
        font.setColor(new Color(1f, 0.55f, 0.15f, 0.20f));
        layout.setText(font, "FIRE");
        float fireCentreY = dividerY + (sh - dividerY) / 2f + layout.height / 2f;
        font.draw(hudBatch, layout, (sw - layout.width) / 2f, fireCentreY);

        // "MOVE" — centred in bottom-left joystick area
        font.setColor(new Color(0.35f, 1f, 0.45f, androidInput.isMoveActive() ? 0.45f : 0.18f));
        layout.setText(font, "MOVE");
        font.draw(hudBatch, layout,
            (sw / 2f - layout.width) / 2f,
            dividerY / 2f + layout.height / 2f);

        // "AIM" — centred in bottom-right joystick area
        font.setColor(new Color(0.35f, 0.65f, 1f, androidInput.isAimActive() ? 0.45f : 0.18f));
        layout.setText(font, "AIM");
        font.draw(hudBatch, layout,
            sw / 2f + (sw / 2f - layout.width) / 2f,
            dividerY / 2f + layout.height / 2f);

        font.setColor(Color.WHITE);
    }

    /**
     * Draws a floating joystick (outer ring + knob) in HUD screen space.
     * Coordinates are raw screen coords (Y=0 at top) and are converted to
     * HUD coords (Y=0 at bottom) before drawing.
     *
     * @param active   whether a finger is currently down
     * @param originX  anchor X in screen coords
     * @param originY  anchor Y in screen coords
     * @param knobX    knob X in screen coords
     * @param knobY    knob Y in screen coords
     * @param radius   outer ring radius (px)
     * @param color    tint color
     */
    private void renderFloatingJoystick(boolean active,
                                        float originX, float originY,
                                        float knobX,   float knobY,
                                        float radius,  Color color) {
        if (!active) return;

        int sh = Gdx.graphics.getHeight();
        // Convert screen-Y (top=0) → HUD-Y (bottom=0)
        float ox = originX,  oy = sh - originY;
        float kx = knobX,    ky = sh - knobY;

        // Outer ring — filled circle, low alpha
        shapeRenderer.setColor(color.r, color.g, color.b, 0.20f);
        shapeRenderer.circle(ox, oy, radius, 40);

        // Ring border — slightly brighter
        shapeRenderer.setColor(color.r, color.g, color.b, 0.55f);
        // Draw as thin filled annulus (two circles subtracted — approx by border only)
        // Use a 4px wide ring: draw full then subtract — not possible with ShapeRenderer,
        // so draw outline by drawing larger then smaller circle.
        shapeRenderer.circle(ox, oy, radius,       40);
        shapeRenderer.setColor(0f, 0f, 0f, 0.01f); // near-transparent punch-out
        shapeRenderer.circle(ox, oy, radius - 4f,  40);

        // Knob — solid circle
        float knobR = radius * com.mygame.tank.controller.player.AndroidInputSource.KNOB_FRAC;
        shapeRenderer.setColor(color.r, color.g, color.b, 0.85f);
        shapeRenderer.circle(kx, ky, knobR, 32);
    }

    // ─── HP bar ───────────────────────────────────────────────────────────────

    private void renderHpBar(Tank player, int sh) {
        float ratio = player.getHp() / player.getMaxHp();
        shapeRenderer.setColor(new Color(0.15f, 0.15f, 0.15f, 0.8f));
        shapeRenderer.rect(20, sh - 36, 200, 18);
        shapeRenderer.setColor(hpColor(ratio));
        shapeRenderer.rect(21, sh - 35, 198 * ratio, 16);

        // Stun bar above HP
        if (player.isStunned()) {
            float stunRatio = MathUtils.clamp(
                player.getHealth().getStunTimer() / GameConfig.STUN_DURATION, 0f, 1f);
            shapeRenderer.setColor(0.6f, 0.2f, 1.0f, 0.9f);
            shapeRenderer.rect(20, sh - 52, 200 * stunRatio, 6);
        }
    }

    // ─── Weapon slots (3 slots, bottom center) ────────────────────────────────

    public  static final int WEAPON_SLOT_SIZE = 100;
    public  static final int WEAPON_SLOT_GAP  = 10;

    private int weaponSlotX(int sw, int slot) {
        int totalW = GameConfig.DAMAGE_WEAPON_SLOTS * WEAPON_SLOT_SIZE
            + (GameConfig.DAMAGE_WEAPON_SLOTS - 1) * WEAPON_SLOT_GAP;
        int startX = sw / 2 - totalW / 2;
        return startX + slot * (WEAPON_SLOT_SIZE + WEAPON_SLOT_GAP);
    }

    private void renderWeaponSlots(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;
        int slotY = 16;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            int sx = weaponSlotX(sw, i);
            boolean active = ws.getActiveWeaponSlot() == i;
            WeaponType type = ws.getWeaponSlots()[i];

            // Background
            shapeRenderer.setColor(active
                ? new Color(0.20f, 0.55f, 0.22f, 0.85f)
                : new Color(0.12f, 0.12f, 0.12f, 0.80f));
            shapeRenderer.rect(sx, slotY, WEAPON_SLOT_SIZE, WEAPON_SLOT_SIZE);

            // Cooldown overlay (darkens slot)
            if (type != null && ws.getWeaponCooldown()[i] > 0f) {
                float max = type.isSpecial() ? type.cooldown : type.fireRate;
                if (max > 0f) {
                    float remaining = ws.getWeaponCooldown()[i] / max;
                    shapeRenderer.setColor(0f, 0f, 0f, 0.55f * remaining);
                    shapeRenderer.rect(sx, slotY, WEAPON_SLOT_SIZE, WEAPON_SLOT_SIZE * remaining);
                }
            }

            // Ammo indicator bar (bottom of slot for limited-ammo weapons)
            if (type != null && type.maxAmmo > 0) {
                int ammo = ws.getWeaponAmmo()[i];
                float ammoRatio = (float) ammo / type.maxAmmo;
                shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);
                shapeRenderer.rect(sx, slotY, WEAPON_SLOT_SIZE, 4f);
                shapeRenderer.setColor(ammoRatio > 0.4f ? Color.CYAN : Color.RED);
                shapeRenderer.rect(sx, slotY, WEAPON_SLOT_SIZE * ammoRatio, 4f);
            }

            // Border
            shapeRenderer.setColor(active ? Color.WHITE : Color.GRAY);
            drawBorderRect(sx, slotY, WEAPON_SLOT_SIZE, WEAPON_SLOT_SIZE, 2);
        }
    }

    private void renderWeaponSlotIcons(WeaponSystem ws, int sw) {
        if (ws == null) return;
        int slotY = 16;
        int padding = 6;
        int iconSize = WEAPON_SLOT_SIZE - padding * 2;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            WeaponType type = ws.getWeaponSlots()[i];
            if (type == null) continue;

            int sx = weaponSlotX(sw, i);
            com.badlogic.gdx.graphics.Texture icon = assets.weaponIcons.get(type);
            if (icon != null) {
                hudBatch.setColor(1f, 1f, 1f, 1f);
                hudBatch.draw(icon, sx + padding, slotY + padding, iconSize, iconSize);
            }
        }
        hudBatch.setColor(Color.WHITE);
    }

    /**
     * Draws equipment icon sprites on top of the HUD slot backgrounds.
     */
    private void renderEquipmentSlotIcons(WeaponSystem ws, int sw) {
        if (ws == null) return;
        int padding = 6;
        int iconSize = EQUIP_SLOT_SIZE - padding * 2;

        for (int i = 0; i < GameConfig.EQUIPMENT_SLOTS; i++) {
            EquipmentType equip = ws.getEquipSlots()[i];
            if (equip == null) continue;

            int sx = EQUIP_SLOT_X0 + i * (EQUIP_SLOT_SIZE + EQUIP_SLOT_GAP);
            com.badlogic.gdx.graphics.Texture icon = assets.equipmentIcons.get(equip);
            if (icon != null) {
                hudBatch.setColor(1f, 1f, 1f, 1f);
                hudBatch.draw(icon, sx + padding, EQUIP_SLOT_Y + padding, iconSize, iconSize);
            }
        }
        hudBatch.setColor(Color.WHITE);
    }

    // ─── Equipment slots (3 slots, bottom left) ───────────────────────────────

    public static final int EQUIP_SLOT_SIZE = 80;
    public static final int EQUIP_SLOT_GAP  = 8;
    public static final int EQUIP_SLOT_X0   = 20;
    public static final int EQUIP_SLOT_Y    = 16;

    private void renderEquipmentSlots(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;

        for (int i = 0; i < GameConfig.EQUIPMENT_SLOTS; i++) {
            int sx = EQUIP_SLOT_X0 + i * (EQUIP_SLOT_SIZE + EQUIP_SLOT_GAP);
            EquipmentType equip = ws.getEquipSlots()[i];

            // Background
            shapeRenderer.setColor(new Color(0.12f, 0.12f, 0.18f, 0.85f));
            shapeRenderer.rect(sx, EQUIP_SLOT_Y, EQUIP_SLOT_SIZE, EQUIP_SLOT_SIZE);

            // Cooldown arc (pie fill)
            float cooldown = ws.getEquipCooldown()[i];
            float maxCD = equip != null ? equip.cooldown : 1f;
            if (cooldown > 0f && maxCD > 0f) {
                float ratio = cooldown / maxCD;
                shapeRenderer.setColor(0f, 0f, 0f, 0.60f);
                // Draw filled arc as triangle fan
                float cx = sx + EQUIP_SLOT_SIZE / 2f;
                float cy = EQUIP_SLOT_Y + EQUIP_SLOT_SIZE / 2f;
                float rad = EQUIP_SLOT_SIZE / 2f;
                int segs = Math.max(1, (int) (ratio * 24));
                float startA = 90f; // start from top
                for (int s = 0; s < segs; s++) {
                    float a1 = MathUtils.degreesToRadians * (startA - (ratio * 360f) * s / segs);
                    float a2 = MathUtils.degreesToRadians * (startA - (ratio * 360f) * (s + 1) / segs);
                    shapeRenderer.triangle(cx, cy,
                        cx + MathUtils.cos(a1) * rad, cy + MathUtils.sin(a1) * rad,
                        cx + MathUtils.cos(a2) * rad, cy + MathUtils.sin(a2) * rad);
                }
            }

            // Border
            shapeRenderer.setColor(cooldown > 0f ? Color.DARK_GRAY : new Color(0.4f, 0.8f, 1f, 1f));
            drawBorderRect(sx, EQUIP_SLOT_Y, EQUIP_SLOT_SIZE, EQUIP_SLOT_SIZE, 2);
        }
    }

    // ─── Weapon Hub overlay ──────────────────────────────────────────────────

    private void renderWeaponHub(WeaponSystem ws, int sw, int sh) {
        // Dim background panel
        shapeRenderer.setColor(0f, 0f, 0f, 0.75f);
        int panelW = 900, panelH = 320;
        int px = sw / 2 - panelW / 2, py = sh / 2 - panelH / 2;
        shapeRenderer.rect(px, py, panelW, panelH);

        // Border
        shapeRenderer.setColor(new Color(0.3f, 0.6f, 0.3f, 1f));
        drawBorderRect(px, py, panelW, panelH, 2);

        // 7 weapon cards in a row
        int cardW = 110, cardH = 220, cardGap = 10;
        int totalCardsW = GameConfig.DAMAGE_WEAPON_SLOTS * cardW
            + (GameConfig.DAMAGE_WEAPON_SLOTS - 1) * cardGap;
        int cardStartX = sw / 2 - totalCardsW / 2;
        int cardY = sh / 2 - cardH / 2;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            int cx = cardStartX + i * (cardW + cardGap);
            boolean selected = ws.getActiveWeaponSlot() == i;
            WeaponType type = ws.getWeaponSlots()[i];

            shapeRenderer.setColor(selected
                ? new Color(0.18f, 0.48f, 0.20f, 0.95f)
                : new Color(0.14f, 0.14f, 0.14f, 0.90f));
            shapeRenderer.rect(cx, cardY, cardW, cardH);

            shapeRenderer.setColor(selected ? Color.WHITE : Color.GRAY);
            drawBorderRect(cx, cardY, cardW, cardH, selected ? 3 : 1);

            // Ammo bar for limited weapons
            if (type != null && type.maxAmmo > 0) {
                int ammo = ws.getWeaponAmmo()[i];
                float ammoRatio = (float) ammo / type.maxAmmo;
                shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1f);
                shapeRenderer.rect(cx + 8, cardY + 8, cardW - 16, 6f);
                shapeRenderer.setColor(ammoRatio > 0.4f ? Color.CYAN : Color.RED);
                shapeRenderer.rect(cx + 8, cardY + 8, (cardW - 16) * ammoRatio, 6f);
            }
        }
    }

    // ─── Boss HP bar (top center) ─────────────────────────────────────────────

    private void renderBossHpBar(GameWorld world, int sw, int sh) {
        DungeonMap dm = world.getDungeonMap();
        Tank player = world.getPlayer();
        com.mygame.tank.dungeon.Room playerRoom = dm.getRoomAt(player.getPosition().x, player.getPosition().y);

        int barW = 280, barH = 14, gap = 4;
        int bossIdx = 0;
        for (Tank boss : world.getBosses()) {
            if (!boss.isAlive()) continue;

            // Chỉ hiện nếu player đang trong phòng boss đó
            if (playerRoom == null || playerRoom.type != com.mygame.tank.dungeon.Room.Type.BOSS) continue;
            String bossRoomKey = world.getBossRoomKeyFor(boss); // "BOSS_ROOM_N"
            String expectedKey = DungeonMap.doorKey(playerRoom);  // "BOSS_ROOM_N"
            if (!bossRoomKey.equals(expectedKey)) continue;

            float ratio = boss.getHp() / boss.getMaxHp();
            int bx = sw / 2 - barW / 2;
            int by = sh - 40 - bossIdx * (barH + gap + 14);

            shapeRenderer.setColor(new Color(0.15f, 0.15f, 0.15f, 0.85f));
            shapeRenderer.rect(bx, by, barW, barH);

            BossController ctrl = (BossController) boss.getController();
            boolean isEnraged = ctrl.getPhaseIndex() >= 1;
            shapeRenderer.setColor(isEnraged
                ? new Color(0.95f, 0.1f, 0.6f, 1f)
                : new Color(0.85f, 0.4f, 0.05f, 1f));
            shapeRenderer.rect(bx + 1, by + 1, (barW - 2) * ratio, barH - 2);
            bossIdx++;
        }
    }

    // ─── Win/Lose overlay ────────────────────────────────────────────────────

    /** Width / Height of the end-game result panel (px). */
    private static final int RESULT_PANEL_W = 500;
    private static final int RESULT_PANEL_H = 260;

    private void renderGameStateOverlay(GameWorld.GameState state, int sw, int sh) {
        if (state == GameWorld.GameState.WIN) {
            // Full-screen green tint
            shapeRenderer.setColor(new Color(0f, 0.4f, 0.1f, 0.55f));
            shapeRenderer.rect(0, 0, sw, sh);
            // Result panel
            drawResultPanel(sw, sh, new Color(0.04f, 0.18f, 0.08f, 0.96f),
                new Color(0.20f, 0.90f, 0.35f, 0.85f));
        } else if (state == GameWorld.GameState.LOSE) {
            // Full-screen red tint
            shapeRenderer.setColor(new Color(0.4f, 0f, 0f, 0.55f));
            shapeRenderer.rect(0, 0, sw, sh);
            // Result panel
            drawResultPanel(sw, sh, new Color(0.18f, 0.04f, 0.04f, 0.96f),
                new Color(0.90f, 0.18f, 0.18f, 0.85f));
        }
    }

    /**
     * Draws a centered result panel background + border.
     * Must be called inside an active shapeRenderer FILLED block.
     */
    private void drawResultPanel(int sw, int sh, Color panelColor, Color borderColor) {
        float px = (sw - RESULT_PANEL_W) / 2f;
        float py = (sh - RESULT_PANEL_H) / 2f;
        // Panel fill
        shapeRenderer.setColor(panelColor);
        shapeRenderer.rect(px, py, RESULT_PANEL_W, RESULT_PANEL_H);
        // Border (2px)
        shapeRenderer.setColor(borderColor);
        drawBorderRect(px, py, RESULT_PANEL_W, RESULT_PANEL_H, 2f);
        // Inner highlight line at top
        shapeRenderer.setColor(borderColor.r, borderColor.g, borderColor.b, 0.35f);
        shapeRenderer.rect(px + 2, py + RESULT_PANEL_H - 4, RESULT_PANEL_W - 4, 2f);
    }

    // ─── Text HUD ─────────────────────────────────────────────────────────────

    private void renderHpText(Tank player, int sh) {
        font.draw(hudBatch, String.format("HP  %d / %d",
            (int) player.getHp(), (int) player.getMaxHp()), 25, sh - 40);
        if (player.isStunned()) {
            font.setColor(new Color(0.7f, 0.3f, 1.0f, 1f));
            font.draw(hudBatch, String.format("STUNNED  %.1fs",
                player.getHealth().getStunTimer()), 25, sh - 60);
            font.setColor(Color.WHITE);
        }
    }

    private void renderWeaponSlotText(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            int sx = weaponSlotX(sw, i);
            WeaponType type = ws.getWeaponSlots()[i];
            if (type == null) continue;

            // Slot number
            font.setColor(new Color(0.6f, 0.6f, 0.6f, 1f));
            font.draw(hudBatch, String.valueOf(i + 1), sx + 4, 16 + WEAPON_SLOT_SIZE + 2);

            // Weapon short name
            font.setColor(ws.getActiveWeaponSlot() == i ? Color.WHITE : Color.LIGHT_GRAY);
            font.draw(hudBatch, type.shortName, sx + 4, 12);

            // Ammo count
            if (type.maxAmmo > 0) {
                int ammo = ws.getWeaponAmmo()[i];
                font.setColor(ammo > 0 ? Color.CYAN : Color.RED);
                font.draw(hudBatch, ammo + "/" + type.maxAmmo,
                    sx + 4, 16 + 28);
            }

            // Cooldown timer
            float cd = ws.getWeaponCooldown()[i];
            if (cd > 0f) {
                font.setColor(Color.ORANGE);
                font.draw(hudBatch, String.format("%.1fs", cd), sx + 4, 16 + 16);
            }
        }
        font.setColor(Color.WHITE);
    }

    private void renderEquipmentSlotText(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;
        for (int i = 0; i < GameConfig.EQUIPMENT_SLOTS; i++) {
            int sx = EQUIP_SLOT_X0 + i * (EQUIP_SLOT_SIZE + EQUIP_SLOT_GAP);
            EquipmentType equip = ws.getEquipSlots()[i];
            if (equip == null) continue;

            // Key hint
            font.setColor(new Color(0.5f, 0.5f, 0.5f, 1f));
            font.draw(hudBatch, "[" + (i + 1) + "]", sx + 2, EQUIP_SLOT_Y + EQUIP_SLOT_SIZE + 2);

            // Short name
            font.setColor(ws.getEquipCooldown()[i] > 0f ? Color.GRAY : Color.WHITE);
            font.draw(hudBatch, equip.shortName, sx + 4, EQUIP_SLOT_Y - 2);

            // Cooldown timer
            float cd = ws.getEquipCooldown()[i];
            if (cd > 0f) {
                font.setColor(Color.ORANGE);
                font.draw(hudBatch, String.format("%.0fs", cd), sx + 5, EQUIP_SLOT_Y + 20);
            }
        }
        font.setColor(Color.WHITE);
    }

    private void renderWeaponHubIcons(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;
        int cardW = 110, cardH = 220, cardGap = 10;
        int totalCardsW = GameConfig.DAMAGE_WEAPON_SLOTS * cardW
            + (GameConfig.DAMAGE_WEAPON_SLOTS - 1) * cardGap;
        int cardStartX = sw / 2 - totalCardsW / 2;
        int cardY = sh / 2 - cardH / 2;
        int iconSize = 80;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            WeaponType type = ws.getWeaponSlots()[i];
            if (type == null) continue;

            int cx = cardStartX + i * (cardW + cardGap);
            com.badlogic.gdx.graphics.Texture icon = assets.weaponIcons.get(type);
            if (icon != null) {
                hudBatch.setColor(1f, 1f, 1f, 1f);
                hudBatch.draw(icon, cx + (cardW - iconSize) / 2f, cardY + cardH - 110, iconSize, iconSize);
            }
        }
        hudBatch.setColor(Color.WHITE);
    }

    private void renderWeaponHubText(WeaponSystem ws, int sw, int sh) {
        if (ws == null) return;

        int cardW = 110, cardH = 220, cardGap = 10;
        int totalCardsW = GameConfig.DAMAGE_WEAPON_SLOTS * cardW
            + (GameConfig.DAMAGE_WEAPON_SLOTS - 1) * cardGap;
        int cardStartX = sw / 2 - totalCardsW / 2;
        int cardY = sh / 2 - cardH / 2;

        // Panel title
        font.getData().setScale(1.2f);
        font.setColor(new Color(0.5f, 0.9f, 0.5f, 1f));
        layout.setText(font, "WEAPON LOADOUT (7 SLOTS)");
        font.draw(hudBatch, layout, sw / 2f - layout.width / 2f, sh / 2f + cardH / 2f + 36f);
        font.getData().setScale(1f);

        font.setColor(new Color(0.5f, 0.6f, 0.5f, 0.8f));
        layout.setText(font, "Press 1-7 to select slot");
        font.draw(hudBatch, layout, sw / 2f - layout.width / 2f, sh / 2f - cardH / 2f - 12f);

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            int cx = cardStartX + i * (cardW + cardGap);
            WeaponType type = ws.getWeaponSlots()[i];
            if (type == null) continue;

            boolean selected = ws.getActiveWeaponSlot() == i;

            font.setColor(selected ? Color.WHITE : Color.LIGHT_GRAY);
            font.getData().setScale(1.1f);
            font.draw(hudBatch, "Slot " + (i + 1), cx + 8, cardY + cardH - 10);
            font.getData().setScale(1f);

            font.setColor(selected ? Color.YELLOW : Color.LIGHT_GRAY);
            font.draw(hudBatch, type.displayName, cx + 6, cardY + cardH - 130);

            font.setColor(Color.LIGHT_GRAY);
            // Stats
            if (type.maxAmmo < 0) {
                font.draw(hudBatch, "Ammo: ∞", cx + 6, cardY + cardH - 150);
            } else {
                int ammo = ws.getWeaponAmmo()[i];
                font.setColor(ammo > 0 ? Color.CYAN : Color.RED);
                font.draw(hudBatch, "Ammo: " + ammo + "/" + type.maxAmmo, cx + 6, cardY + cardH - 150);
            }
            font.setColor(Color.LIGHT_GRAY);
            float cd = ws.getWeaponCooldown()[i];
            if (cd > 0f) {
                font.setColor(Color.ORANGE);
                font.draw(hudBatch, String.format("CD: %.1fs", cd), cx + 6, cardY + cardH - 168);
            }

            // Group label
            String group = switch (type) {
                case DEFAULT_BULLET, SUBMACHINE_GUN, ARMOR_PIERCE -> "Group A";
                case CANNON, STUN_SHELL -> "Group B";
                case LASER, HOMING_MISSILE -> "Group C";
            };
            font.setColor(new Color(0.5f, 0.7f, 0.5f, 0.8f));
            font.draw(hudBatch, group, cx + 6, cardY + 24);
        }
        font.setColor(Color.WHITE);
    }

    /**
     * Hiển thị tên + phase của boss chỉ khi player trong phòng boss đó.
     */
    private void renderBossHudText(GameWorld world, int sw, int sh) {
        DungeonMap dm = world.getDungeonMap();
        Tank player = world.getPlayer();
        Room playerRoom = dm.getRoomAt(player.getPosition().x, player.getPosition().y);
        if (playerRoom == null || playerRoom.type != Room.Type.BOSS) return;
        String expectedKey = DungeonMap.doorKey(playerRoom);

        int bossIdx = 0;
        int barH = 14, gap = 4;
        for (Tank boss : world.getBosses()) {
            if (!boss.isAlive()) continue;
            if (!world.getBossRoomKeyFor(boss).equals(expectedKey)) continue;

            BossController ctrl = (BossController) boss.getController();
            int phaseIndex = ctrl.getPhaseIndex();
            boolean isEnraged = phaseIndex >= 1;
            font.setColor(isEnraged ? Color.RED : Color.ORANGE);
            int by = sh - 40 - bossIdx * (barH + gap + 14);
            // Extract a display name from areaId (e.g. "BOSS_iron_guard" → "iron guard")
            String areaId = boss.getAreaId();
            String bossName = areaId != null
                ? areaId.replace("BOSS_", "").replace("_", " ").toUpperCase()
                : "BOSS";
            font.draw(hudBatch,
                String.format("%s  %d/%d  P%d",
                    bossName, (int) boss.getHp(), (int) boss.getMaxHp(), phaseIndex + 1),
                sw / 2f - 90f, by + barH + 13);
            bossIdx++;
        }
        font.setColor(Color.WHITE);
    }

    /**
     * Hiển thị timer đang chạy ở góc trên phải (chỉ khi PLAYING).
     * Hiển thị tiến trình dọn phòng bên trên timer.
     */
    private void renderRoomProgressText(RoomProgressionManager rm, int sw, int sh) {
        // Timer — top right, phía trên tiến trình
        if (!rm.isGameWon()) {
            int mm = (int) gameTimer / 60;
            int ss = (int) gameTimer % 60;
            font.getData().setScale(1.1f);
            font.setColor(new Color(0.85f, 0.95f, 0.75f, 1f));
            String timerStr = String.format("%02d:%02d", mm, ss);
            layout.setText(font, timerStr);
            font.draw(hudBatch, layout, sw - layout.width - 12f, sh - 10f);
            font.getData().setScale(1f);
        }

        // Room progress hint
        font.setColor(new Color(0.7f, 0.9f, 0.7f, 1f));
        font.draw(hudBatch, rm.isGameWon() ? "ALL BOSSES DEFEATED!" : "Survive & Defeat All Bosses",
            sw - 280f, sh - 30f);
        font.setColor(Color.WHITE);
    }

    /**
     * Hiển thị timer đang chạy ở góc trên phải khi state = PLAYING,
     * hoặc màn hình kết quả chi tiết khi WIN/LOSE.
     */
    private void renderGameStateText(GameWorld.GameState state, int sw, int sh) {
        int mm = (int) gameTimer / 60;
        int ss = (int) gameTimer % 60;
        String timeStr = String.format("%02d:%02d", mm, ss);

        if (state == GameWorld.GameState.WIN) {
            float panelCX = sw / 2f;
            float panelCY = sh / 2f;

            // Title
            font.getData().setScale(2.8f);
            font.setColor(new Color(0.25f, 1.00f, 0.45f, 1f));
            layout.setText(font, "VICTORY");
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY + RESULT_PANEL_H / 2f - 20f);
            font.getData().setScale(1f);

            // Time played
            font.getData().setScale(1.4f);
            font.setColor(Color.WHITE);
            layout.setText(font, "Time: " + timeStr);
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY + 28f);
            font.getData().setScale(1f);

            // Click hint
            font.setColor(new Color(0.70f, 0.90f, 0.70f, 0.90f));
            layout.setText(font, "Click anywhere to return to Main Menu");
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY - 20f);

        } else if (state == GameWorld.GameState.LOSE) {
            float panelCX = sw / 2f;
            float panelCY = sh / 2f;

            // Title
            font.getData().setScale(2.8f);
            font.setColor(new Color(1.00f, 0.25f, 0.25f, 1f));
            layout.setText(font, "GAME OVER");
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY + RESULT_PANEL_H / 2f - 20f);
            font.getData().setScale(1f);

            // Time played
            font.getData().setScale(1.4f);
            font.setColor(Color.WHITE);
            layout.setText(font, "Time: " + timeStr);
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY + 28f);
            font.getData().setScale(1f);

            // Click hint
            font.setColor(new Color(0.90f, 0.70f, 0.70f, 0.90f));
            layout.setText(font, "Click anywhere to return to Main Menu");
            font.draw(hudBatch, layout,
                panelCX - layout.width / 2f,
                panelCY - 20f);
        }

        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
    }

    private void renderControlsHint(GameWorld.GameState state, int sh) {
        if (state != GameWorld.GameState.PLAYING) return;
        font.setColor(new Color(0.55f, 0.55f, 0.55f, 1f));
        font.draw(hudBatch,
            "WASD:Move  Mouse:Aim  LMB:Fire  TAB:WeaponHub  1/2/3:Equipment  F1:Debug",
            10, 12);
        font.setColor(Color.WHITE);
    }

    // ─── Shape Helpers ────────────────────────────────────────────────────────

    private void drawTankBody(float cx, float cy, float angleDeg, float size,
                              Color color, float alpha) {
        shapeRenderer.setColor(color.r, color.g, color.b, alpha);
        drawRotatedRect(cx, cy, size, size * 0.8f, angleDeg);

        shapeRenderer.setColor(color.r * 0.6f, color.g * 0.6f, color.b * 0.6f, alpha);
        float rad = MathUtils.degreesToRadians * angleDeg;
        float perpX = -MathUtils.sin(rad) * size * 0.38f;
        float perpY = MathUtils.cos(rad) * size * 0.38f;
        drawRotatedRect(cx + perpX, cy + perpY, size * 0.9f, size * 0.12f, angleDeg);
        drawRotatedRect(cx - perpX, cy - perpY, size * 0.9f, size * 0.12f, angleDeg);
    }

    private void drawBarrel(float cx, float cy, float angleDeg,
                            float length, float width, Color color) {
        shapeRenderer.setColor(color);
        float rad = MathUtils.degreesToRadians * angleDeg;
        float bCenX = cx + MathUtils.cos(rad) * length * 0.45f;
        float bCenY = cy + MathUtils.sin(rad) * length * 0.45f;
        drawRotatedRect(bCenX, bCenY, length, width, angleDeg);
    }

    private void drawRotatedRect(float cx, float cy, float w, float h, float angleDeg) {
        float rad = MathUtils.degreesToRadians * angleDeg;
        float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);
        float hw = w / 2f, hh = h / 2f;

        float x0 = cx + (-hw * cos - (-hh) * sin), y0 = cy + (-hw * sin + (-hh) * cos);
        float x1 = cx + (hw * cos - (-hh) * sin), y1 = cy + (hw * sin + (-hh) * cos);
        float x2 = cx + (hw * cos - hh * sin), y2 = cy + (hw * sin + hh * cos);
        float x3 = cx + (-hw * cos - hh * sin), y3 = cy + (-hw * sin + hh * cos);

        shapeRenderer.triangle(x0, y0, x1, y1, x2, y2);
        shapeRenderer.triangle(x0, y0, x2, y2, x3, y3);
    }

    private void drawHealthBar(float cx, float cy, float w, float h, float ratio) {
        shapeRenderer.setColor(new Color(0.1f, 0.1f, 0.1f, 0.7f));
        shapeRenderer.rect(cx - w / 2f, cy, w, h);
        shapeRenderer.setColor(hpColor(ratio));
        shapeRenderer.rect(cx - w / 2f, cy, w * ratio, h);
    }

    private void drawBorderRect(float x, float y, float w, float h, float thickness) {
        shapeRenderer.rect(x, y, w, thickness);
        shapeRenderer.rect(x, y + h - thickness, w, thickness);
        shapeRenderer.rect(x, y, thickness, h);
        shapeRenderer.rect(x + w - thickness, y, thickness, h);
    }

    private static Color hpColor(float ratio) {
        if (ratio > 0.5f) return Color.GREEN;
        if (ratio > 0.25f) return Color.YELLOW;
        return Color.RED;
    }

    public void dispose() {
        shapeRenderer.dispose();
        worldBatch.dispose();
        hudBatch.dispose();
        font.dispose();
        assets.dispose();
    }
}
