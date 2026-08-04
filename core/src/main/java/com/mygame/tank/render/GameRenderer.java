package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
    private final SpriteBatch hudBatch;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final OrthographicCamera hudCamera;

    public GameRenderer() {
        shapeRenderer = new ShapeRenderer();
        hudBatch = new SpriteBatch();
        font = new BitmapFont();
        layout = new GlyphLayout();
        hudCamera = new OrthographicCamera();
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

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderMapBackground(world);
        renderWorldEffects(world);
        renderEnemies(world);
        renderBoss(world);
        renderPlayer(world);
        renderProjectiles(world);
        renderLaserBeams(world);
        shapeRenderer.end();

        // 6. Draw Visual Effects (additive / above projectiles)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive blending
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderVisualEffects(world);
        shapeRenderer.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // restore normal blending

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

    private void renderVisualEffects(GameWorld world) {
        for (VisualEffect vfx : world.getVisualEffects()) {
            if (!vfx.isAlive()) continue;
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
                case MUZZLE_FLASH: {
                    // Cone or diamond
                    float alpha = 1f - progress;
                    shapeRenderer.setColor(c.r, c.g, c.b, alpha);
                    float rad = MathUtils.degreesToRadians * vfx.getAngle();
                    float dx = MathUtils.cos(rad);
                    float dy = MathUtils.sin(rad);
                    float len = vfx.getRadius();
                    // Just draw a small line/rect as a simple flash
                    shapeRenderer.rectLine(px, py, px + dx * len, py + dy * len, len * 0.5f);
                    break;
                }
                case LASER_HIT: {
                    // Sparks
                    shapeRenderer.setColor(c.r, c.g, c.b, 1f - progress);
                    shapeRenderer.circle(px, py, vfx.getRadius() * (1f - progress), 6);
                    break;
                }
            }
        }
    }


    private void renderBoss(GameWorld world) {
        Tank boss = world.getBoss();
        if (boss == null || !boss.isAlive()) return;

        BossController bossCtrl = (BossController) boss.getController();
        boolean isPhase2 = bossCtrl.getPhase() == BossController.Phase.PHASE_2;
        Color bossColor = isPhase2 ? COLOR_BOSS_P2 : COLOR_BOSS_BODY;
        float blink = (System.currentTimeMillis() % 300 < 150 && isPhase2) ? 0.6f : 1f;
        float alpha = boss.isStunned() ? 0.5f : blink;

        drawTankBody(boss.getPosition().x, boss.getPosition().y,
            boss.getBodyAngle(), GameConfig.BOSS_WIDTH, bossColor, alpha);
        drawBarrel(boss.getPosition().x, boss.getPosition().y,
            boss.getBodyAngle(), GameConfig.BOSS_WIDTH * 0.65f,
            GameConfig.BOSS_WIDTH * 0.22f,
            bossColor.cpy().mul(0.7f, 0.7f, 0.7f, alpha));
    }

    private void renderPlayer(GameWorld world) {
        Tank player = world.getPlayer();
        if (!player.isAlive()) return;

        float alpha = player.isStunned()
            ? ((System.currentTimeMillis() % 300 < 150) ? 0.3f : 0.8f)
            : 1.0f;

        drawTankBody(player.getPosition().x, player.getPosition().y,
            player.getBodyAngle(), GameConfig.PLAYER_WIDTH,
            COLOR_PLAYER_BODY, alpha);
        drawBarrel(player.getPosition().x, player.getPosition().y,
            player.getTurretAngle(), GameConfig.PLAYER_WIDTH * 0.70f,
            GameConfig.PLAYER_WIDTH * 0.20f, COLOR_PLAYER_TURRET);
    }

    private void renderProjectiles(GameWorld world) {
        for (Projectile proj : world.getProjectiles()) {
            if (!proj.isAlive()) continue;
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
        WeaponSystem ws = player.getTurret().getWeaponSystem();

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

        // ── Text pass ─────────────────────────────────────────────────────────
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
        hudBatch.dispose();
        font.dispose();
    }
}
