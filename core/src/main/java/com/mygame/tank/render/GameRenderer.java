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
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.VisualEffect;
import com.mygame.tank.entity.WorldEffect;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;
import com.mygame.tank.weapon.EquipmentType;
import com.mygame.tank.weapon.WeaponSystem;
import com.mygame.tank.weapon.WeaponType;
import com.mygame.tank.world.AreaManager;
import com.mygame.tank.world.GameWorld;
import com.mygame.tank.world.MapManager;

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

        // ── Pass 1: Shape-rendered elements (map, effects, enemies, boss) ────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderMapBackground(world);
        renderWorldEffects(world);
        renderEnemies(world);
        renderNonDefaultProjectiles(world);   // AoE, stun, homing, AP, enemy bullets
        renderLaserBeams(world);
        shapeRenderer.end();

        // ── Pass 2: Sprite-rendered elements (player body/turret, default bullets) ─
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        worldBatch.begin();
        renderPlayerSprites(world);
        renderBossSprites(world);
        renderDefaultBulletSprites(world);
        renderBossBulletSprites(world);
        worldBatch.end();

        // ── Pass 3: Visual effects (additive blending, above everything) ─────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        worldBatch.begin();
        renderMuzzleFlashSprites(world);
        worldBatch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderNonMuzzleVisualEffects(world);
        shapeRenderer.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ── Pass 4: Health bars (normal blending, top layer in world space) ──────
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderHealthBars(world);
        shapeRenderer.end();
    }

    private void renderMapBackground(GameWorld world) {
        MapManager map = world.getMapManager();
        AreaManager am = world.getAreaManager();

        shapeRenderer.setColor(COLOR_FLOOR);
        shapeRenderer.rect(0, 0, GameConfig.MAP_WIDTH, GameConfig.MAP_HEIGHT);

        shapeRenderer.setColor(COLOR_WALL);
        for (Rectangle r : map.getCollisionRects()) {
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        }

        for (Map.Entry<String, Rectangle> entry : map.getDoorRects().entrySet()) {
            boolean open = am.isDoorOpen(entry.getKey());
            shapeRenderer.setColor(open ? COLOR_DOOR_OPEN : COLOR_DOOR_CLOSE);
            Rectangle r = entry.getValue();
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
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
     * Renders the player tank body and turret using PNG sprites.
     * Both sprites face UP in their source image; we rotate by (angle - 90°)
     * to convert from the game's math-angle convention (90° = up) to LibGDX
     * SpriteBatch rotation (0° = no-rotation = facing up).
     */
    private void renderPlayerSprites(GameWorld world) {
        Tank player = world.getPlayer();
        if (!player.isAlive()) return;

        float alpha = player.isStunned()
            ? ((System.currentTimeMillis() % 300 < 150) ? 0.3f : 0.8f)
            : 1.0f;

        float px = player.getPosition().x;
        float py = player.getPosition().y;

        // ── Body ──────────────────────────────────────────────────────────────
        float bodyW = GameConfig.PLAYER_WIDTH * 1.6f;  // slightly wider than hitbox for visual fidelity
        float bodyH = GameConfig.PLAYER_HEIGHT * 2.0f;  // body sprite is taller than hitbox
        float bodyRot = player.getBodyAngle() - 90f;    // sprite faces up → subtract 90° offset
        worldBatch.setColor(1f, 1f, 1f, alpha);
        worldBatch.draw(assets.bodyRegion,
            px - bodyW / 2f, py - bodyH / 2f,  // bottom-left origin
            bodyW / 2f, bodyH / 2f,             // rotation origin (center)
            bodyW, bodyH,                       // size
            1f, 1f,                             // scale
            bodyRot);

        // ── Turret ────────────────────────────────────────────────────────────
        float turretSize = GameConfig.PLAYER_WIDTH * 1.4f;
        float turretRot = player.getTurretAngle() - 90f;
        worldBatch.draw(assets.defaultTurret,
            px - turretSize / 2f, py - turretSize / 2f,
            turretSize / 2f, turretSize / 2f,
            turretSize, turretSize,
            1f, 1f,
            turretRot);

        worldBatch.setColor(Color.WHITE);
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


    private void renderBossSprites(GameWorld world) {
        Tank boss = world.getBoss();
        if (boss == null || !boss.isAlive()) return;

        BossController bossCtrl = (BossController) boss.getController();
        boolean isPhase2 = bossCtrl.getPhase() == BossController.Phase.PHASE_2;
        float blink = (System.currentTimeMillis() % 300 < 150 && isPhase2) ? 0.6f : 1f;
        float alpha = boss.isStunned() ? 0.5f : blink;

        float px = boss.getPosition().x;
        float py = boss.getPosition().y;

        // Body
        float bodySize = GameConfig.BOSS_WIDTH * 1.6f;
        float bodyRot = boss.getBodyAngle() - 90f;
        worldBatch.setColor(1f, 1f, 1f, alpha);
        if (isPhase2) {
            worldBatch.setColor(1f, 0.5f, 0.5f, alpha); // Reddish tint for phase 2
        }
        worldBatch.draw(assets.bossBody,
            px - bodySize / 2f, py - bodySize / 2f,
            bodySize / 2f, bodySize / 2f,
            bodySize, bodySize,
            1f, 1f,
            bodyRot);

        // Turret
        float turretSize = GameConfig.BOSS_WIDTH * 1.6f;
        float turretRot = boss.getTurretAngle() - 90f;
        worldBatch.draw(assets.bossTurret,
            px - turretSize / 2f,
            py - turretSize / 2f,
            turretSize / 2f, turretSize / 2f,
            turretSize, turretSize,
            1f, 1f,
            turretRot);

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

            worldBatch.setColor(Color.WHITE);
            worldBatch.draw(assets.bossProjectile,
                bx - bulletSize / 2f, by - bulletSize / 2f,
                bulletSize / 2f, bulletSize / 2f,
                bulletSize, bulletSize,
                1f, 1f,
                spriteRot);
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
            // Stun bar
            if (enemy.isStunned()) {
                shapeRenderer.setColor(0.5f, 0.2f, 1f, 1f);
                shapeRenderer.rect(enemy.getPosition().x - 16f, cy + 6f,
                    32f * MathUtils.clamp(enemy.getHealth().getStunTimer() / GameConfig.STUN_DURATION, 0f, 1f), 3f);
            }
        }

        Tank boss = world.getBoss();
        if (boss != null && boss.isAlive()) {
            float cy = boss.getPosition().y + GameConfig.BOSS_HEIGHT * 0.85f;
            drawHealthBar(boss.getPosition().x, cy, 60f, 7f,
                boss.getHp() / boss.getMaxHp());
        }
    }

    // ─── Screen-space HUD ────────────────────────────────────────────────────

    private void renderHud(GameWorld world) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Tank player = world.getPlayer();
        WeaponSystem ws = ((PlayerTurretComponent) player.getTurret()).getWeaponSystem();

        renderHpBar(player, sh);
        renderWeaponSlots(ws, sw, sh);
        renderEquipmentSlots(ws, sw, sh);
        renderBossHpBar(world.getBoss(), sw, sh);
        renderGameStateOverlay(world.getGameState(), sw, sh);

        // Weapon hub overlay
        if (ws != null && ws.isHubOpen()) {
            renderWeaponHub(ws, sw, sh);
        }

        shapeRenderer.end();

        // ── Text + icon sprite pass ────────────────────────────────────────────
        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();
        font.setColor(Color.WHITE);

        renderHpText(player, sh);
        renderWeaponSlotText(ws, sw, sh);
        renderEquipmentSlotText(ws, sw, sh);
        renderBossText(world.getBoss(), sw, sh);
        renderAreaText(world.getAreaManager(), sw, sh);
        renderGameStateText(world.getGameState(), sw, sh);
        renderControlsHint(world.getGameState(), sh);

        // Draw weapon slot sprite icons on top of text (uses hudBatch which is already open)
        renderWeaponSlotIcons(ws, sw);

        if (ws != null && ws.isHubOpen()) {
            renderWeaponHubText(ws, sw, sh);
        }

        hudBatch.end();
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

    private static final int WEAPON_SLOT_SIZE = 52;
    private static final int WEAPON_SLOT_GAP = 8;

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

    /**
     * Draws weapon icon sprites on top of the HUD slot backgrounds.
     * Called from {@link #renderHud} after the ShapeRenderer pass.
     */
    private void renderWeaponSlotIcons(WeaponSystem ws, int sw) {
        if (ws == null) return;
        int slotY = 16;
        int padding = 6;
        int iconSize = WEAPON_SLOT_SIZE - padding * 2;

        for (int i = 0; i < GameConfig.DAMAGE_WEAPON_SLOTS; i++) {
            WeaponType type = ws.getWeaponSlots()[i];
            if (type != WeaponType.DEFAULT_BULLET) continue; // only sprite icon for default weapon

            int sx = weaponSlotX(sw, i);
            TextureRegion icon = assets.defaultWeaponIcon;
            hudBatch.setColor(1f, 1f, 1f, 1f);
            hudBatch.draw(icon, sx + padding, slotY + padding, iconSize, iconSize);
        }
        hudBatch.setColor(Color.WHITE);
    }

    // ─── Equipment slots (3 slots, bottom left) ───────────────────────────────

    private static final int EQUIP_SLOT_SIZE = 44;
    private static final int EQUIP_SLOT_GAP = 6;
    private static final int EQUIP_SLOT_X0 = 20;
    private static final int EQUIP_SLOT_Y = 16;

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

    private void renderBossHpBar(Tank boss, int sw, int sh) {
        if (boss == null || !boss.isAlive()) return;
        float ratio = boss.getHp() / boss.getMaxHp();
        int barW = 300, barH = 16;
        int bx = sw / 2 - barW / 2;
        int by = sh - 40;

        shapeRenderer.setColor(new Color(0.15f, 0.15f, 0.15f, 0.85f));
        shapeRenderer.rect(bx, by, barW, barH);

        BossController bossCtrl = (BossController) boss.getController();
        shapeRenderer.setColor(bossCtrl.getPhase() == BossController.Phase.PHASE_2
            ? new Color(0.95f, 0.1f, 0.6f, 1f)
            : new Color(0.85f, 0.4f, 0.05f, 1f));
        shapeRenderer.rect(bx + 1, by + 1, (barW - 2) * ratio, barH - 2);
    }

    // ─── Win/Lose overlay ────────────────────────────────────────────────────

    private void renderGameStateOverlay(GameWorld.GameState state, int sw, int sh) {
        if (state == GameWorld.GameState.WIN) {
            shapeRenderer.setColor(new Color(0f, 0.4f, 0.1f, 0.65f));
            shapeRenderer.rect(0, 0, sw, sh);
        } else if (state == GameWorld.GameState.LOSE) {
            shapeRenderer.setColor(new Color(0.4f, 0f, 0f, 0.65f));
            shapeRenderer.rect(0, 0, sw, sh);
        }
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
            font.draw(hudBatch, type.shortName, sx + 8, 16 + WEAPON_SLOT_SIZE - 8);

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
            font.draw(hudBatch, equip.shortName, sx + 5, EQUIP_SLOT_Y + EQUIP_SLOT_SIZE - 6);

            // Cooldown timer
            float cd = ws.getEquipCooldown()[i];
            if (cd > 0f) {
                font.setColor(Color.ORANGE);
                font.draw(hudBatch, String.format("%.0fs", cd), sx + 5, EQUIP_SLOT_Y + 20);
            }
        }
        font.setColor(Color.WHITE);
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
            font.draw(hudBatch, type.displayName, cx + 6, cardY + cardH - 28);

            font.setColor(Color.LIGHT_GRAY);
            // Stats
            if (type.maxAmmo < 0) {
                font.draw(hudBatch, "Ammo: ∞", cx + 6, cardY + cardH - 50);
            } else {
                int ammo = ws.getWeaponAmmo()[i];
                font.setColor(ammo > 0 ? Color.CYAN : Color.RED);
                font.draw(hudBatch, "Ammo: " + ammo + "/" + type.maxAmmo, cx + 6, cardY + cardH - 50);
            }
            font.setColor(Color.LIGHT_GRAY);
            float cd = ws.getWeaponCooldown()[i];
            if (cd > 0f) {
                font.setColor(Color.ORANGE);
                font.draw(hudBatch, String.format("CD: %.1fs", cd), cx + 6, cardY + cardH - 68);
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

    private void renderBossText(Tank boss, int sw, int sh) {
        if (boss == null || !boss.isAlive()) return;

        BossController bossCtrl = (BossController) boss.getController();
        boolean isPhase2 = bossCtrl.getPhase() == BossController.Phase.PHASE_2;
        font.setColor(isPhase2 ? Color.RED : Color.ORANGE);

        String text = String.format("IRON GUARD   %d / %d   Phase %d",
            (int) boss.getHp(), (int) boss.getMaxHp(), isPhase2 ? 2 : 1);
        layout.setText(font, text);
        font.draw(hudBatch, layout, sw / 2f - layout.width / 2f, sh - 22);
        font.setColor(Color.WHITE);
    }

    private void renderAreaText(AreaManager am, int sw, int sh) {
        StringBuilder sb = new StringBuilder("Areas: ");
        sb.append(am.isAreaCleared("A") ? "[A✓] " : "[A]  ");
        sb.append(am.isAreaCleared("B") ? "[B✓] " : "[B]  ");
        sb.append(am.isAreaCleared("C") ? "[C✓]" : "[C] ");
        font.setColor(new Color(0.7f, 0.9f, 0.7f, 1f));
        font.draw(hudBatch, sb.toString(), sw - 260f, sh - 10);
        font.setColor(Color.WHITE);
    }

    private void renderGameStateText(GameWorld.GameState state, int sw, int sh) {
        if (state == GameWorld.GameState.WIN) {
            font.getData().setScale(2.5f);
            font.setColor(Color.YELLOW);
            layout.setText(font, "VICTORY!");
            font.draw(hudBatch, layout, sw / 2f - layout.width / 2f, sh / 2f + 40);
            font.getData().setScale(1f);
            font.setColor(Color.WHITE);
            font.draw(hudBatch, "Press R to restart", sw / 2f - 80, sh / 2f);
        } else if (state == GameWorld.GameState.LOSE) {
            font.getData().setScale(2.5f);
            font.setColor(Color.RED);
            layout.setText(font, "GAME OVER");
            font.draw(hudBatch, layout, sw / 2f - layout.width / 2f, sh / 2f + 40);
            font.getData().setScale(1f);
            font.setColor(Color.WHITE);
            font.draw(hudBatch, "Press R to restart", sw / 2f - 80, sh / 2f);
        }
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
