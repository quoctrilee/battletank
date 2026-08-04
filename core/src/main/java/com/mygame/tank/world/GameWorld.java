package com.mygame.tank.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.controller.player.PlayerInputHandler;
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.TankFactory;
import com.mygame.tank.entity.VisualEffect;
import com.mygame.tank.entity.WorldEffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GameWorld {

    public enum GameState {PLAYING, WIN, LOSE}

    // ─── Core entities ────────────────────────────────────────────────────────
    private final Tank player;
    private final List<Tank> enemies;
    private Tank boss;
    private final List<Projectile> projectiles;
    private final List<LaserBeam> laserBeams;
    private final List<WorldEffect> worldEffects;
    private final List<VisualEffect> visualEffects;

    // ─── Systems ─────────────────────────────────────────────────────────────
    private final MapManager mapManager;
    private final AreaManager areaManager;
    private final CollisionSystem collision;
    private GameState gameState;

    // ─── Reference to InputHandler for hub-sync ───────────────────────────────
    private PlayerInputHandler inputHandler;

    // ─── Constructor ─────────────────────────────────────────────────────────

    public GameWorld(MapManager mapManager) {
        this.mapManager = mapManager;
        this.areaManager = new AreaManager();
        this.collision = new CollisionSystem();
        this.projectiles = new ArrayList<>();
        this.laserBeams = new ArrayList<>();
        this.worldEffects = new ArrayList<>();
        this.visualEffects = new ArrayList<>();
        this.enemies = new ArrayList<>();
        this.boss = null;
        this.gameState = GameState.PLAYING;

        Vector2 spawn = mapManager.getPlayerSpawn();
        this.player = TankFactory.createPlayer(spawn.x, spawn.y);

        spawnAllEnemies();
    }

    /**
     * Inject the InputHandler so GameWorld can sync hub state to it.
     */
    public void setInputHandler(PlayerInputHandler handler) {
        this.inputHandler = handler;
    }

    private void spawnAllEnemies() {
        Map<String, List<Vector2>> spawnMap = mapManager.getEnemySpawns();
        for (Map.Entry<String, List<Vector2>> entry : spawnMap.entrySet()) {
            String areaId = entry.getKey();
            List<Tank> areaEnemies = new ArrayList<>();
            for (Vector2 pos : entry.getValue()) {
                areaEnemies.add(TankFactory.createEnemy(pos.x, pos.y, areaId));
            }
            enemies.addAll(areaEnemies);
            areaManager.registerEnemies(areaId, areaEnemies);
        }
    }

    // ─── Main Update ─────────────────────────────────────────────────────────

    public void update(float delta, PlayerInput input) {
        if (gameState != GameState.PLAYING) return;

        step1_updatePlayer(delta, input);
        step2_updateEnemies(delta);
        step3_updateBoss(delta);
        step4_resolveTankVsTank();
        step5_updateProjectiles(delta);
        step6_updateLaserBeams(delta);
        step7_updateWorldEffects(delta);
        step8_updateVisualEffects(delta);
        step9_areaProgression();
        step10_checkWinLose();
    }

    // ─── Update Steps ─────────────────────────────────────────────────────────

    private void step1_updatePlayer(float delta, PlayerInput input) {
        // Collect beams, effects, and vfx via mutable output lists in UpdateContext
        List<LaserBeam> pendingBeams = new ArrayList<>();
        List<WorldEffect> pendingEffects = new ArrayList<>();
        List<VisualEffect> pendingVfx = new ArrayList<>();

        List<Projectile> shots = player.update(delta,
            new UpdateContext(input, pendingBeams, pendingEffects, pendingVfx));

        projectiles.addAll(shots);
        laserBeams.addAll(pendingBeams);
        worldEffects.addAll(pendingEffects);
        visualEffects.addAll(pendingVfx);

        // Sync hub state to InputHandler for 1/2/3 disambiguation
        if (inputHandler != null) {
            boolean hubOpen = player.getTurret().getWeaponSystem() != null
                && player.getTurret().getWeaponSystem().isHubOpen();
            inputHandler.setHubOpen(hubOpen);
        }

        // Assign homing targets for newly fired missiles
        for (Projectile p : shots) {
            if (p.getType() == Projectile.ProjectileType.HOMING) {
                Tank target = findNearestAliveEnemy(p.getPosition());
                p.setHomingTarget(target);
            }
        }

        // Clamp and wall-resolve
        clampTankToMap(player, GameConfig.PLAYER_WIDTH / 2f, GameConfig.PLAYER_HEIGHT / 2f);
        collision.resolveEntityVsWalls(player, mapManager.getCollisionRects());

        for (Map.Entry<String, Rectangle> entry : mapManager.getDoorRects().entrySet()) {
            collision.resolveTankVsDoor(player, entry.getValue(),
                areaManager.isDoorOpen(entry.getKey()));
        }
    }

    private void step2_updateEnemies(float delta) {
        Vector2 playerPos = player.getPosition();
        boolean playerAlive = player.isAlive();
        boolean playerInSmoke = isInSmoke(playerPos);

        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;

            boolean areaActive = areaManager.isAreaActive(enemy.getAreaId());
            List<Projectile> shots = enemy.update(delta,
                new UpdateContext(playerPos, playerAlive, areaActive, playerInSmoke));
            projectiles.addAll(shots);

            collision.resolveEntityVsWalls(enemy, mapManager.getCollisionRects());
        }
    }

    private void step3_updateBoss(float delta) {
        if (boss == null || !boss.isAlive()) return;

        Vector2 playerPos = player.getPosition();
        boolean playerAlive = player.isAlive();
        boolean playerInSmoke = isInSmoke(playerPos);

        List<Projectile> shots = boss.update(delta,
            new UpdateContext(playerPos, playerAlive, true, playerInSmoke));
        projectiles.addAll(shots);

        Vector2 wallPush = collision.resolveEntityVsWalls(boss, mapManager.getCollisionRects());

        BossController bossCtrl = (BossController) boss.getController();
        if (!wallPush.isZero(0.01f)) bossCtrl.onWallCollision(boss, wallPush);
        if (bossCtrl.consumePendingMiniSpawn()) spawnMiniTanks();
    }

    private void step4_resolveTankVsTank() {
        List<Tank> allTanks = new ArrayList<>(enemies.size() + 2);
        allTanks.add(player);
        allTanks.addAll(enemies);
        if (boss != null && boss.isAlive()) allTanks.add(boss);
        collision.resolveTankVsTank(allTanks);
    }

    private void step5_updateProjectiles(float delta) {
        Iterator<Projectile> iter = projectiles.iterator();
        while (iter.hasNext()) {
            Projectile proj = iter.next();
            proj.update(delta);
            if (!proj.isAlive()) {
                // AoE / Stun projectiles that expired → trigger explosion
                if (proj.hasExploded()) {
                    applyAoeExplosion(proj);
                }
                iter.remove();
                continue;
            }
            if (resolveProjectileCollisions(proj)) {
                if (proj.hasExploded()) applyAoeExplosion(proj);
                iter.remove();
            }
        }
    }

    private void step6_updateLaserBeams(float delta) {
        Iterator<LaserBeam> iter = laserBeams.iterator();
        while (iter.hasNext()) {
            LaserBeam beam = iter.next();
            beam.update(delta);
            if (!beam.isAlive()) {
                iter.remove();
                continue;
            }
            // Apply damage during BEAM phase (once per activation)
            if (beam.isBeamActive() && !beam.hasDamageBeenApplied()) {
                applyLaserDamage(beam);
                beam.markDamageApplied();
            }
        }
    }

    private void step7_updateWorldEffects(float delta) {
        Iterator<WorldEffect> iter = worldEffects.iterator();
        while (iter.hasNext()) {
            WorldEffect effect = iter.next();
            effect.update(delta);
            if (!effect.isAlive()) {
                iter.remove();
                continue;
            }

            if (effect instanceof WorldEffect.Mine mine) {
                // Check all enemies & boss for mine triggers
                processMine(mine);
            } else if (effect instanceof WorldEffect.EmpField emp) {
                // Apply EMP suppression to enemies in range each frame
                applyEmpToEnemies(emp, delta);
            }
        }
    }

    private void step8_updateVisualEffects(float delta) {
        Iterator<VisualEffect> iter = visualEffects.iterator();
        while (iter.hasNext()) {
            VisualEffect vfx = iter.next();
            vfx.update(delta);
            if (!vfx.isAlive()) {
                iter.remove();
            }
        }
    }

    private void step9_areaProgression() {
        areaManager.update();
        if (areaManager.shouldSpawnBoss()) spawnBoss();
    }

    private void step10_checkWinLose() {
        if (!player.isAlive()) gameState = GameState.LOSE;
        if (boss != null && !boss.isAlive() && !areaManager.isBossDefeated()) {
            areaManager.setBossDefeated(true);
            gameState = GameState.WIN;
        }
    }

    // ─── Projectile collision resolution ─────────────────────────────────────

    private boolean resolveProjectileCollisions(Projectile proj) {
        // 1. Kiểm tra va chạm với Tường/Vật cản (CollisionRects)
        for (Rectangle wall : mapManager.getCollisionRects()) {
            if (proj.getBounds().overlaps(wall)) {
                if (proj.getType() == Projectile.ProjectileType.AOE
                    || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
                    proj.triggerExplosion();
                } else {
                    proj.destroy();
                }
                visualEffects.add(VisualEffect.smallHit(proj.getPosition().x, proj.getPosition().y, Color.GRAY));
                return true;
            }
        }

        // 2. Kiểm tra va chạm với Cửa (DoorRects)
        for (Map.Entry<String, Rectangle> entry : mapManager.getDoorRects().entrySet()) {
            if (collision.checkProjectileVsDoor(proj, entry.getValue(),
                areaManager.isDoorOpen(entry.getKey()))) {
                if (proj.getType() == Projectile.ProjectileType.AOE
                    || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
                    proj.triggerExplosion();
                } else {
                    proj.destroy();
                }
                visualEffects.add(VisualEffect.smallHit(proj.getPosition().x, proj.getPosition().y, Color.GRAY));
                return true;
            }
        }

        // 3. Kiểm tra va chạm với Tăng (Xe tăng địch hoặc Người chơi)
        if (proj.getOwner() == Projectile.Owner.PLAYER) {
            return resolvePlayerProjectile(proj);
        } else {
            return resolveEnemyProjectile(proj);
        }
    }

    private boolean resolvePlayerProjectile(Projectile proj) {
        // Piercing hits — pass through multiple tanks
        if (proj.getType() == Projectile.ProjectileType.PIERCING) {
            boolean hitAny = false;
            for (Tank enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (proj.getBounds().overlaps(enemy.getBounds())) {
                    float dmg = proj.onPierceHit();
                    enemy.takeDamage(dmg);
                    visualEffects.add(VisualEffect.smallHit(enemy.getPosition().x, enemy.getPosition().y, Color.CYAN));
                    hitAny = true;
                    if (!proj.isAlive()) return true;
                }
            }
            if (boss != null && boss.isAlive() && proj.getBounds().overlaps(boss.getBounds())) {
                boss.takeDamage(proj.onPierceHit());
                visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.CYAN));
                if (!proj.isAlive()) return true;
            }
            return false; // piercing stays alive until pierceCount exhausted
        }

        // AoE / StunAoe — trigger explosion on first tank hit, not wall-kill
        if (proj.getType() == Projectile.ProjectileType.AOE
            || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
            Tank hit = collision.checkProjectileVsEnemies(proj, enemies);
            if (hit != null) {
                proj.triggerExplosion();
                return true;
            }
            if (boss != null && boss.isAlive() && proj.getBounds().overlaps(boss.getBounds())) {
                proj.triggerExplosion();
                return true;
            }
            return false;
        }

        // Normal / Homing — destroy on first hit
        Tank hit = collision.checkProjectileVsEnemies(proj, enemies);
        if (hit != null) {
            hit.takeDamage(proj.getDamage());
            proj.destroy();
            visualEffects.add(VisualEffect.smallHit(hit.getPosition().x, hit.getPosition().y, Color.ORANGE));
            return true;
        }
        if (boss != null && boss.isAlive() && proj.getBounds().overlaps(boss.getBounds())) {
            boss.takeDamage(proj.getDamage());
            proj.destroy();
            visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.ORANGE));
            return true;
        }
        return false;
    }

    private boolean resolveEnemyProjectile(Projectile proj) {
        if (!collision.checkProjectileVsTank(proj, player)) return false;

        // Check Energy Shield interception
        for (WorldEffect effect : worldEffects) {
            if (effect instanceof WorldEffect.EnergyShield shield
                && shield.getOwner() == player) {
                if (shield.interceptHit()) {
                    proj.destroy();
                    return true;
                }
            }
        }

        player.takeDamage(proj.getDamage());
        proj.destroy();
        visualEffects.add(VisualEffect.smallHit(player.getPosition().x, player.getPosition().y, Color.RED));
        return true;
    }

    // ─── AoE explosion ────────────────────────────────────────────────────────

    private void applyAoeExplosion(Projectile proj) {
        float cx = proj.getPosition().x;
        float cy = proj.getPosition().y;
        float radius = proj.getAoeRadius();
        float baseDmg = (proj.getType() == Projectile.ProjectileType.AOE)
            ? GameConfig.CANNON_AOE_DAMAGE
            : GameConfig.STUN_DAMAGE;

        Color vfxColor = (proj.getType() == Projectile.ProjectileType.AOE) ? Color.ORANGE : Color.PURPLE;
        visualEffects.add(VisualEffect.aoeExplosion(cx, cy, radius, vfxColor));

        // Determine target lists by owner
        if (proj.getOwner() == Projectile.Owner.PLAYER) {
            for (Tank enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (enemy.getPosition().dst(cx, cy) <= radius) {
                    enemy.takeDamage(baseDmg);
                    if (proj.getType() == Projectile.ProjectileType.STUN_AOE) {
                        enemy.applyStun(proj.getStunDuration());
                    }
                }
            }
            if (boss != null && boss.isAlive()
                && boss.getPosition().dst(cx, cy) <= radius) {
                boss.takeDamage(baseDmg);
                if (proj.getType() == Projectile.ProjectileType.STUN_AOE) {
                    boss.applyStun(proj.getStunDuration());
                }
            }
        } else {
            // Enemy AoE vs player
            if (player.isAlive() && player.getPosition().dst(cx, cy) <= radius) {
                player.takeDamage(baseDmg);
            }
        }
    }

    // ─── Laser damage ────────────────────────────────────────────────────────

    private void applyLaserDamage(LaserBeam beam) {
        float tankRadius = GameConfig.PLAYER_WIDTH / 2f;

        if (beam.getOwner() == Projectile.Owner.PLAYER) {
            for (Tank enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (beam.distanceToBeam(enemy.getPosition()) <= tankRadius + GameConfig.ENEMY_WIDTH / 2f) {
                    enemy.takeDamage(beam.getDamage());
                    visualEffects.add(VisualEffect.smallHit(enemy.getPosition().x, enemy.getPosition().y, Color.YELLOW));
                }
            }
            if (boss != null && boss.isAlive()
                && beam.distanceToBeam(boss.getPosition()) <= tankRadius + GameConfig.BOSS_WIDTH / 2f) {
                boss.takeDamage(beam.getDamage());
                visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.YELLOW));
            }
        } else {
            if (player.isAlive() && beam.distanceToBeam(player.getPosition()) <= tankRadius) {
                player.takeDamage(beam.getDamage());
                visualEffects.add(VisualEffect.smallHit(player.getPosition().x, player.getPosition().y, Color.YELLOW));
            }
        }
    }

    // ─── Mine processing ─────────────────────────────────────────────────────

    private void processMine(WorldEffect.Mine mine) {
        // Check player first — mines are not safe for the player who placed them
        if (player != null && player.isAlive()) {
            if (mine.tryTrigger(player.getPosition())) {
                applyMineExplosion(mine.getPosition());
                return;
            }
        }

        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (mine.tryTrigger(enemy.getPosition())) {
                applyMineExplosion(mine.getPosition());
                return;
            }
        }

        if (boss != null && boss.isAlive()) {
            if (mine.tryTrigger(boss.getPosition())) {
                applyMineExplosion(mine.getPosition());
            }
        }
    }

    private void applyMineExplosion(Vector2 center) {
        visualEffects.add(VisualEffect.aoeExplosion(center.x, center.y, GameConfig.MINE_AOE_RADIUS, Color.RED));

        // Damage player if caught in blast radius
        if (player != null && player.isAlive()
            && player.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS) {
            player.takeDamage(GameConfig.MINE_DAMAGE);
        }

        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (enemy.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS) {
                enemy.takeDamage(GameConfig.MINE_DAMAGE);
            }
        }

        if (boss != null && boss.isAlive()
            && boss.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS) {
            boss.takeDamage(GameConfig.MINE_DAMAGE);
        }
    }

    // ─── EMP processing ──────────────────────────────────────────────────────

    private void applyEmpToEnemies(WorldEffect.EmpField emp, float delta) {
        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (emp.contains(enemy.getPosition())) {
                enemy.applyEmp(GameConfig.EMP_DURATION);
            }
        }
        if (boss != null && boss.isAlive() && emp.contains(boss.getPosition())) {
            boss.applyEmp(GameConfig.EMP_DURATION);
        }
    }

    // ─── Smoke query (for renderer / homing) ─────────────────────────────────

    /**
     * Returns true if {@code point} is inside any active smoke zone.
     */
    public boolean isInSmoke(Vector2 point) {
        for (WorldEffect e : worldEffects) {
            if (e instanceof WorldEffect.SmokeBomb smoke && smoke.contains(point)) return true;
        }
        return false;
    }

    // ─── Homing target resolution ─────────────────────────────────────────────

    private Tank findNearestAliveEnemy(Vector2 from) {
        Tank nearest = null;
        float bestDst2 = GameConfig.HOMING_LOCK_RANGE * GameConfig.HOMING_LOCK_RANGE;

        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            // Don't lock onto targets in smoke
            if (isInSmoke(enemy.getPosition())) continue;
            float d2 = from.dst2(enemy.getPosition());
            if (d2 < bestDst2) {
                bestDst2 = d2;
                nearest = enemy;
            }
        }
        if (boss != null && boss.isAlive() && !isInSmoke(boss.getPosition())) {
            float d2 = from.dst2(boss.getPosition());
            if (d2 < bestDst2) nearest = boss;
        }
        return nearest;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void clampTankToMap(Tank tank, float hw, float hh) {
        float x = Math.max(hw, Math.min(GameConfig.MAP_WIDTH - hw, tank.getPosition().x));
        float y = Math.max(hh, Math.min(GameConfig.MAP_HEIGHT - hh, tank.getPosition().y));
        tank.resolvePosition(x, y);
    }

    private void spawnBoss() {
        Vector2 spawnPos = mapManager.getBossSpawn();
        boss = TankFactory.createBoss(spawnPos.x, spawnPos.y);
        areaManager.setBossActive(true);
    }

    private void spawnMiniTanks() {
        if (boss == null) return;
        float bx = boss.getPosition().x;
        float by = boss.getPosition().y;
        for (int i = 0; i < GameConfig.BOSS_MINI_TANK_COUNT; i++) {
            float offsetX = (i == 0) ? -80f : 80f;
            enemies.add(TankFactory.createEnemy(bx + offsetX, by - 80f, "BOSS"));
        }
    }

    // ─── Getters ─────────────────────────────────────────────────────────────
    public Tank getPlayer() {
        return player;
    }

    public List<Tank> getEnemies() {
        return enemies;
    }

    public Tank getBoss() {
        return boss;
    }

    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    public List<LaserBeam> getLaserBeams() {
        return laserBeams;
    }

    public List<WorldEffect> getWorldEffects() {
        return worldEffects;
    }

    public List<VisualEffect> getVisualEffects() {
        return visualEffects;
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public AreaManager getAreaManager() {
        return areaManager;
    }

    public GameState getGameState() {
        return gameState;
    }
}
