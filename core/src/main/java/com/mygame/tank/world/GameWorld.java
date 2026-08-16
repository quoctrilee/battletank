package com.mygame.tank.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.controller.ai.BossController;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.dungeon.Room;
import com.mygame.tank.dungeon.RoomProgressionManager;
import com.mygame.tank.dungeon.pathfinding.AStarPathfinder;
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.TankFactory;
import com.mygame.tank.entity.VisualEffect;
import com.mygame.tank.entity.WorldEffect;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;

import java.util.*;

public class GameWorld {

    public enum GameState {PLAYING, WIN, LOSE}

    // ─── Core entities ────────────────────────────────────────────────────────
    private final Tank player;
    private final List<Tank> enemies;   // quái thường (tất cả phòng ENEMY)
    private final List<Tank> bosses;    // boss (tất cả phòng BOSS)
    private final List<Projectile> projectiles;
    private final List<LaserBeam> laserBeams;
    private final List<WorldEffect> worldEffects;
    private final List<VisualEffect> visualEffects;

    // ─── Systems ─────────────────────────────────────────────────────────────
    private final DungeonMap dungeonMap;
    private final RoomProgressionManager roomManager;
    private final CollisionSystem collision;
    private GameState gameState;

    // ─── A* Pathfinding ───────────────────────────────────────────────────────
    private final AStarPathfinder pathfinder;
    /**
     * Cache đường A* mỗi enemy, tính lại định kỳ.
     */
    private final Map<Tank, List<Vector2>> enemyPaths = new IdentityHashMap<>();
    /**
     * Bộ đếm thời gian recalculate path cho mỗi enemy.
     */
    private final Map<Tank, Float> pathTimers = new IdentityHashMap<>();

    // ─── Enemy-to-room mapping ────────────────────────────────────────────────
    /**
     * roomKey của mỗi enemy — để biết enemy thuộc phòng nào.
     */
    private final Map<Tank, String> enemyRoomKeys = new IdentityHashMap<>();
    /**
     * bossRoomKey của mỗi boss (BOSS_ROOM_N).
     */
    private final Map<Tank, String> bossRoomKeys = new IdentityHashMap<>();
    /**
     * Bounds (world coords) của phòng boss chứa mỗi boss. Dùng để clamp boss
     * bên trong phòng của nó — trước đây chỉ clamp theo toàn bản đồ nên boss
     * (đứng yên tại tâm, circle-strafe bán kính lớn) có thể bị đẩy lọt ra
     * ngoài tường phòng nhỏ khi va chạm dồn dập.
     */
    private final Map<Tank, Rectangle> bossRoomBounds = new IdentityHashMap<>();

    // ─── Constructor ─────────────────────────────────────────────────────────

    public GameWorld(DungeonMap dungeonMap) {
        this.dungeonMap = dungeonMap;
        this.roomManager = new RoomProgressionManager();
        this.collision = new CollisionSystem();
        this.projectiles = new ArrayList<>();
        this.laserBeams = new ArrayList<>();
        this.worldEffects = new ArrayList<>();
        this.visualEffects = new ArrayList<>();
        this.enemies = new ArrayList<>();
        this.bosses = new ArrayList<>();
        this.gameState = GameState.PLAYING;

        // Khởi tạo pathfinder
        pathfinder = new AStarPathfinder(dungeonMap.getTileMap());

        // Khởi tạo progression manager với danh sách phòng
        roomManager.init(dungeonMap.getRooms());

        // Spawn player tại phòng đầu tiên
        Vector2 spawnPos = dungeonMap.getPlayerSpawn();
        this.player = TankFactory.createPlayer(spawnPos.x, spawnPos.y);

        // Spawn quái + boss theo dungeon layout
        spawnAllEntities();
    }

    /**
     * Spawn tất cả quái và boss từ DungeonMap.
     */
    private void spawnAllEntities() {
        // ── Quái thường ──────────────────────────────────────────────────────
        Map<String, List<Vector2>> enemySpawnMap = dungeonMap.getEnemySpawns();
        for (Map.Entry<String, List<Vector2>> entry : enemySpawnMap.entrySet()) {
            String roomKey = entry.getKey();
            List<Tank> roomEnemies = new ArrayList<>();
            for (Vector2 pos : entry.getValue()) {
                Tank enemy = TankFactory.createEnemy(pos.x, pos.y, roomKey);
                roomEnemies.add(enemy);
                enemies.add(enemy);
                enemyRoomKeys.put(enemy, roomKey);
                pathTimers.put(enemy, 0f); // recalculate ngay lần đầu
            }
            roomManager.registerEnemies(roomKey, roomEnemies);
        }

        // ── Boss ─────────────────────────────────────────────────────────────
        // Duyệt trực tiếp từng phòng BOSS (KHÔNG zip theo index với
        // dungeonMap.getBossSpawns() như trước) — mỗi Room BOSS tự mang theo
        // bossSpawnPoint của chính nó, nên không thể lệch thứ tự hay lệch độ
        // dài giữa 2 danh sách. Trước đây nếu 2 list lệch độ dài vì bất kỳ lý
        // do gì (ví dụ 1 phòng BOSS thiếu bossSpawnPoint), boss cuối cùng
        // trong chuỗi sẽ không được spawn/registerBoss — khiến
        // RoomProgressionManager coi phòng đó là "trống" và tự động clear nó,
        // làm game kết thúc sớm ngay sau khi hạ xong boss áp chót.
        for (Room bossRoom : dungeonMap.getRooms()) {
            if (bossRoom.type != Room.Type.BOSS) continue;

            Vector2 pos = bossRoom.bossSpawnPoint != null
                ? bossRoom.bossSpawnPoint
                : bossRoom.getCenter(); // fallback an toàn, không bao giờ bỏ sót spawn

            Tank boss = TankFactory.createBoss(pos.x, pos.y);
            String doorKey = DungeonMap.doorKey(bossRoom);
            String roomKey = DungeonMap.roomKey(bossRoom);
            bosses.add(boss);
            bossRoomKeys.put(boss, doorKey);
            bossRoomBounds.put(boss, bossRoom.bounds);
            pathTimers.put(boss, 0f);
            roomManager.registerBoss(doorKey, boss);
            roomManager.registerEnemies(roomKey, Collections.emptyList()); // boss room không có quái thường
        }
    }

    // ─── Main Update ─────────────────────────────────────────────────────────

    public void update(float delta, PlayerInput input) {
        if (gameState != GameState.PLAYING) return;

        step1_updatePlayer(delta, input);
        step2_updateEnemyPaths(delta);
        step3_updateEnemies(delta);
        step4_updateBosses(delta);
        step5_resolveTankVsTank();
        step6_updateProjectiles(delta);
        step7_updateLaserBeams(delta);
        step8_updateWorldEffects(delta);
        step9_updateVisualEffects(delta);
        step10_roomProgression();
        step11_checkWinLose();
    }

    // ─── Update Steps ─────────────────────────────────────────────────────────

    private void step1_updatePlayer(float delta, PlayerInput input) {
        List<LaserBeam> pendingBeams = new ArrayList<>();
        List<WorldEffect> pendingEffects = new ArrayList<>();
        List<VisualEffect> pendingVfx = new ArrayList<>();

        List<Projectile> shots = player.update(delta,
            new UpdateContext(input, pendingBeams, pendingEffects, pendingVfx));

        projectiles.addAll(shots);
        laserBeams.addAll(pendingBeams);
        worldEffects.addAll(pendingEffects);
        visualEffects.addAll(pendingVfx);

        // Gán homing target cho tên lửa
        for (Projectile p : shots) {
            if (p.getType() == Projectile.ProjectileType.HOMING) {
                Tank target = findNearestAliveEnemy(p.getPosition());
                p.setHomingTarget(target);
            }
        }

        // Clamp + wall resolve
        clampTankToMap(player, GameConfig.PLAYER_WIDTH / 2f, GameConfig.PLAYER_HEIGHT / 2f);
        collision.resolveEntityVsWalls(player, dungeonMap.getCollisionRects());

        // Xử lý cửa boss
        for (Map.Entry<String, Rectangle> entry : dungeonMap.getDoorRects().entrySet()) {
            collision.resolveTankVsDoor(player, entry.getValue(),
                roomManager.isDoorOpen(entry.getKey()));
        }

        // Xử lý entrance door (khóa lối ra khi đang trong phòng chưa clear)
        for (Map.Entry<String, Rectangle> entry : dungeonMap.getEntranceDoorRects().entrySet()) {
            collision.resolveTankVsDoor(player, entry.getValue(),
                roomManager.isDoorOpen(entry.getKey()));
        }
    }

    /**
     * Tính lại đường A* định kỳ cho từng enemy/boss.
     * Không tính mỗi frame để tiết kiệm CPU.
     */
    private void step2_updateEnemyPaths(float delta) {
        Vector2 playerPos = player.getPosition();
        // Cập nhật timer và recalculate nếu cần
        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            updatePathForTank(enemy, playerPos, delta);
        }
        for (Tank boss : bosses) {
            if (!boss.isAlive()) continue;
            updatePathForTank(boss, playerPos, delta);
        }
    }

    private void updatePathForTank(Tank tank, Vector2 playerPos, float delta) {
        float timer = pathTimers.getOrDefault(tank, 0f) - delta;
        if (timer <= 0f) {
            // Recalculate A*
            List<Vector2> path = pathfinder.findPath(tank.getPosition(), playerPos);
            enemyPaths.put(tank, path);
            timer = GameConfig.ENEMY_PATHFIND_INTERVAL;
        }
        pathTimers.put(tank, timer);
    }

    /**
     * Lấy waypoint tiếp theo trong path của tank.
     * Bỏ qua các waypoint đã đủ gần, trả về null nếu không có path.
     */
    private Vector2 getNextWaypoint(Tank tank) {
        List<Vector2> path = enemyPaths.get(tank);
        if (path == null || path.isEmpty()) return null;

        Vector2 tankPos = tank.getPosition();
        // Bỏ qua các waypoint đã đến gần (trong vòng 1.5 tile)
        float threshold = GameConfig.MAP_TILE_SIZE * 1.5f;
        while (path.size() > 1 && tankPos.dst(path.get(0)) < threshold) {
            path.remove(0);
        }
        return path.isEmpty() ? null : path.get(0);
    }

    private void step3_updateEnemies(float delta) {
        Vector2 playerPos = player.getPosition();
        boolean playerAlive = player.isAlive();
        boolean playerInSmoke = isInSmoke(playerPos);

        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;

            String roomKey = enemyRoomKeys.get(enemy);
            boolean active = roomManager.isRoomActive(roomKey);
            Vector2 waypoint = getNextWaypoint(enemy);

            List<Projectile> shots = enemy.update(delta,
                new UpdateContext(playerPos, playerAlive, active, playerInSmoke, waypoint));
            projectiles.addAll(shots);

            collision.resolveEntityVsWalls(enemy, dungeonMap.getCollisionRects());
        }
    }

    private void step4_updateBosses(float delta) {
        Vector2 playerPos = player.getPosition();
        boolean playerAlive = player.isAlive();
        boolean playerInSmoke = isInSmoke(playerPos);

        for (Tank boss : bosses) {
            if (!boss.isAlive()) continue;

            String doorKey = bossRoomKeys.get(boss);
            boolean active = roomManager.isDoorOpen(doorKey); // boss active khi cửa mở

            Vector2 waypoint = getNextWaypoint(boss);

            List<Projectile> shots = boss.update(delta,
                new UpdateContext(playerPos, playerAlive, active, playerInSmoke, waypoint));
            projectiles.addAll(shots);

            Vector2 wallPush = collision.resolveEntityVsWalls(boss, dungeonMap.getCollisionRects());

            // Clamp boss trong bounds phòng boss của chính nó (không phải toàn
            // map) — chống trường hợp circle-strafe / pushout dồn boss lọt ra
            // ngoài tường phòng nhỏ hoặc kẹt giữa 2 mặt tường.
            clampTankToRoom(boss, bossRoomBounds.get(boss),
                GameConfig.BOSS_WIDTH / 2f, GameConfig.BOSS_HEIGHT / 2f);

            BossController bossCtrl = (BossController) boss.getController();
            if (!wallPush.isZero(0.01f)) bossCtrl.onWallCollision(boss, wallPush);
            int miniCount = bossCtrl.consumePendingMiniSpawn();
            if (miniCount > 0) spawnMiniTanks(boss, miniCount);
        }
    }

    private void step5_resolveTankVsTank() {
        List<Tank> all = new ArrayList<>(enemies.size() + bosses.size() + 1);
        all.add(player);
        all.addAll(enemies);
        all.addAll(bosses);
        collision.resolveTankVsTank(all);
    }

    private void step6_updateProjectiles(float delta) {
        Iterator<Projectile> iter = projectiles.iterator();
        while (iter.hasNext()) {
            Projectile proj = iter.next();
            proj.update(delta);
            if (!proj.isAlive()) {
                if (proj.hasExploded()) applyAoeExplosion(proj);
                iter.remove();
                continue;
            }
            if (resolveProjectileCollisions(proj)) {
                if (proj.hasExploded()) applyAoeExplosion(proj);
                iter.remove();
            }
        }
    }

    private void step7_updateLaserBeams(float delta) {
        Iterator<LaserBeam> iter = laserBeams.iterator();
        while (iter.hasNext()) {
            LaserBeam beam = iter.next();
            beam.update(delta);
            if (!beam.isAlive()) {
                iter.remove();
                continue;
            }
            if (beam.isBeamActive() && !beam.hasDamageBeenApplied()) {
                applyLaserDamage(beam);
                beam.markDamageApplied();
            }
        }
    }

    private void step8_updateWorldEffects(float delta) {
        Iterator<WorldEffect> iter = worldEffects.iterator();
        while (iter.hasNext()) {
            WorldEffect effect = iter.next();
            effect.update(delta);
            if (!effect.isAlive()) {
                iter.remove();
                continue;
            }
            if (effect instanceof WorldEffect.Mine mine) {
                processMine(mine);
            } else if (effect instanceof WorldEffect.EmpField emp) {
                applyEmpToEnemies(emp, delta);
            }
        }
    }

    private void step9_updateVisualEffects(float delta) {
        visualEffects.removeIf(vfx -> {
            vfx.update(delta);
            return !vfx.isAlive();
        });
    }

    private void step10_roomProgression() {
        // Entrance door của phòng chưa clear giờ mặc định luôn đóng (xem
        // RoomProgressionManager.isDoorOpen) nên không còn cần "khóa khi player
        // bước vào" — collision ở step1_updatePlayer đã tự chặn việc đi qua.
        roomManager.update();
    }

    private void step11_checkWinLose() {
        if (!player.isAlive()) {
            gameState = GameState.LOSE;
            return;
        }
        if (roomManager.isGameWon()) {
            gameState = GameState.WIN;
        }
    }

    // ─── Projectile collision ─────────────────────────────────────────────────

    private boolean resolveProjectileCollisions(Projectile proj) {
        // 1. Tường
        for (Rectangle wall : dungeonMap.getCollisionRects()) {
            if (proj.getBounds().overlaps(wall)) {
                triggerOrDestroyProj(proj);
                visualEffects.add(VisualEffect.smallHit(proj.getPosition().x, proj.getPosition().y, Color.GRAY));
                return true;
            }
        }
        // 2. Cửa boss & entrance door (closed)
        for (Map.Entry<String, Rectangle> entry : dungeonMap.getDoorRects().entrySet()) {
            if (collision.checkProjectileVsDoor(proj, entry.getValue(),
                roomManager.isDoorOpen(entry.getKey()))) {
                triggerOrDestroyProj(proj);
                visualEffects.add(VisualEffect.smallHit(proj.getPosition().x, proj.getPosition().y, Color.GRAY));
                return true;
            }
        }
        for (Map.Entry<String, Rectangle> entry : dungeonMap.getEntranceDoorRects().entrySet()) {
            if (collision.checkProjectileVsDoor(proj, entry.getValue(),
                roomManager.isDoorOpen(entry.getKey()))) {
                triggerOrDestroyProj(proj);
                visualEffects.add(VisualEffect.smallHit(proj.getPosition().x, proj.getPosition().y, Color.GRAY));
                return true;
            }
        }
        // 3. Tank
        if (proj.getOwner() == Projectile.Owner.PLAYER) {
            return resolvePlayerProjectile(proj);
        } else {
            return resolveEnemyProjectile(proj);
        }
    }

    private void triggerOrDestroyProj(Projectile proj) {
        if (proj.getType() == Projectile.ProjectileType.AOE
            || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
            proj.triggerExplosion();
        } else {
            proj.destroy();
        }
    }

    private boolean resolvePlayerProjectile(Projectile proj) {
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
            for (Tank boss : bosses) {
                if (!boss.isAlive()) continue;
                if (proj.getBounds().overlaps(boss.getBounds())) {
                    boss.takeDamage(proj.onPierceHit());
                    visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.CYAN));
                    if (!proj.isAlive()) return true;
                }
            }
            return false;
        }

        if (proj.getType() == Projectile.ProjectileType.AOE
            || proj.getType() == Projectile.ProjectileType.STUN_AOE) {
            Tank hit = collision.checkProjectileVsEnemies(proj, enemies);
            if (hit != null) {
                proj.triggerExplosion();
                return true;
            }
            for (Tank boss : bosses) {
                if (boss.isAlive() && proj.getBounds().overlaps(boss.getBounds())) {
                    proj.triggerExplosion();
                    return true;
                }
            }
            return false;
        }

        // Normal / Homing
        Tank hit = collision.checkProjectileVsEnemies(proj, enemies);
        if (hit != null) {
            hit.takeDamage(proj.getDamage());
            proj.destroy();
            visualEffects.add(VisualEffect.smallHit(hit.getPosition().x, hit.getPosition().y, Color.ORANGE));
            return true;
        }
        for (Tank boss : bosses) {
            if (boss.isAlive() && proj.getBounds().overlaps(boss.getBounds())) {
                boss.takeDamage(proj.getDamage());
                proj.destroy();
                visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.ORANGE));
                return true;
            }
        }
        return false;
    }

    private boolean resolveEnemyProjectile(Projectile proj) {
        if (!collision.checkProjectileVsTank(proj, player)) return false;
        for (WorldEffect effect : worldEffects) {
            if (effect instanceof WorldEffect.EnergyShield shield && shield.getOwner() == player) {
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
        float cx = proj.getPosition().x, cy = proj.getPosition().y;
        float radius = proj.getAoeRadius();
        float baseDmg = (proj.getType() == Projectile.ProjectileType.AOE)
            ? GameConfig.CANNON_AOE_DAMAGE : GameConfig.STUN_DAMAGE;
        Color vfxColor = (proj.getType() == Projectile.ProjectileType.AOE)
            ? Color.ORANGE : Color.PURPLE;
        visualEffects.add(VisualEffect.aoeExplosion(cx, cy, radius, vfxColor));

        if (proj.getOwner() == Projectile.Owner.PLAYER) {
            for (Tank enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (enemy.getPosition().dst(cx, cy) <= radius) {
                    enemy.takeDamage(baseDmg);
                    if (proj.getType() == Projectile.ProjectileType.STUN_AOE)
                        enemy.applyStun(proj.getStunDuration());
                }
            }
            for (Tank boss : bosses) {
                if (boss.isAlive() && boss.getPosition().dst(cx, cy) <= radius) {
                    boss.takeDamage(baseDmg);
                    if (proj.getType() == Projectile.ProjectileType.STUN_AOE)
                        boss.applyStun(proj.getStunDuration());
                }
            }
        } else {
            if (player.isAlive() && player.getPosition().dst(cx, cy) <= radius)
                player.takeDamage(baseDmg);
        }
    }

    // ─── Laser ────────────────────────────────────────────────────────────────

    private void applyLaserDamage(LaserBeam beam) {
        float tankR = GameConfig.PLAYER_WIDTH / 2f;
        if (beam.getOwner() == Projectile.Owner.PLAYER) {
            for (Tank enemy : enemies) {
                if (!enemy.isAlive()) continue;
                if (beam.distanceToBeam(enemy.getPosition()) <= tankR + GameConfig.ENEMY_WIDTH / 2f) {
                    enemy.takeDamage(beam.getDamage());
                    visualEffects.add(VisualEffect.smallHit(enemy.getPosition().x, enemy.getPosition().y, Color.YELLOW));
                }
            }
            for (Tank boss : bosses) {
                if (boss.isAlive() && beam.distanceToBeam(boss.getPosition()) <= tankR + GameConfig.BOSS_WIDTH / 2f) {
                    boss.takeDamage(beam.getDamage());
                    visualEffects.add(VisualEffect.smallHit(boss.getPosition().x, boss.getPosition().y, Color.YELLOW));
                }
            }
        } else {
            if (player.isAlive() && beam.distanceToBeam(player.getPosition()) <= tankR)
                player.takeDamage(beam.getDamage());
        }
    }

    // ─── Mine ─────────────────────────────────────────────────────────────────

    private void processMine(WorldEffect.Mine mine) {
        if (player.isAlive() && mine.tryTrigger(player.getPosition())) {
            applyMineExplosion(mine.getPosition());
            return;
        }
        for (Tank enemy : enemies) {
            if (!enemy.isAlive()) continue;
            if (mine.tryTrigger(enemy.getPosition())) {
                applyMineExplosion(mine.getPosition());
                return;
            }
        }
        for (Tank boss : bosses) {
            if (boss.isAlive() && mine.tryTrigger(boss.getPosition())) {
                applyMineExplosion(mine.getPosition());
                return;
            }
        }
    }

    private void applyMineExplosion(Vector2 center) {
        visualEffects.add(VisualEffect.aoeExplosion(center.x, center.y, GameConfig.MINE_AOE_RADIUS, Color.RED));
        if (player.isAlive() && player.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS)
            player.takeDamage(GameConfig.MINE_DAMAGE);
        for (Tank e : enemies) {
            if (e.isAlive() && e.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS)
                e.takeDamage(GameConfig.MINE_DAMAGE);
        }
        for (Tank b : bosses) {
            if (b.isAlive() && b.getPosition().dst(center) <= GameConfig.MINE_AOE_RADIUS)
                b.takeDamage(GameConfig.MINE_DAMAGE);
        }
    }

    // ─── EMP ──────────────────────────────────────────────────────────────────

    private void applyEmpToEnemies(WorldEffect.EmpField emp, float delta) {
        for (Tank e : enemies) {
            if (e.isAlive() && emp.contains(e.getPosition())) e.applyEmp(GameConfig.EMP_DURATION);
        }
        for (Tank b : bosses) {
            if (b.isAlive() && emp.contains(b.getPosition())) b.applyEmp(GameConfig.EMP_DURATION);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public boolean isInSmoke(Vector2 point) {
        for (WorldEffect e : worldEffects) {
            if (e instanceof WorldEffect.SmokeBomb smoke && smoke.contains(point)) return true;
        }
        return false;
    }

    private Tank findNearestAliveEnemy(Vector2 from) {
        Tank nearest = null;
        float bestDst2 = GameConfig.HOMING_LOCK_RANGE * GameConfig.HOMING_LOCK_RANGE;
        for (Tank e : enemies) {
            if (!e.isAlive() || isInSmoke(e.getPosition())) continue;
            float d2 = from.dst2(e.getPosition());
            if (d2 < bestDst2) {
                bestDst2 = d2;
                nearest = e;
            }
        }
        for (Tank b : bosses) {
            if (!b.isAlive() || isInSmoke(b.getPosition())) continue;
            float d2 = from.dst2(b.getPosition());
            if (d2 < bestDst2) {
                bestDst2 = d2;
                nearest = b;
            }
        }
        return nearest;
    }

    private void clampTankToMap(Tank tank, float hw, float hh) {
        float x = Math.max(hw, Math.min(GameConfig.MAP_WIDTH - hw, tank.getPosition().x));
        float y = Math.max(hh, Math.min(GameConfig.MAP_HEIGHT - hh, tank.getPosition().y));
        tank.resolvePosition(x, y);
    }

    /**
     * Clamp tank (dùng cho boss) bên trong bounds của 1 phòng cụ thể, thay vì
     * toàn bản đồ. Nếu bounds null (phòng không xác định — không nên xảy ra
     * với dữ liệu hợp lệ), fallback về clamp toàn map để không NPE.
     */
    private void clampTankToRoom(Tank tank, Rectangle roomBounds, float hw, float hh) {
        if (roomBounds == null) {
            clampTankToMap(tank, hw, hh);
            return;
        }
        float minX = roomBounds.x + hw;
        float maxX = roomBounds.x + roomBounds.width - hw;
        float minY = roomBounds.y + hh;
        float maxY = roomBounds.y + roomBounds.height - hh;
        // Nếu phòng nhỏ hơn kích thước tank (không nên xảy ra vì ROOM_MIN_TILES
        // đủ lớn), clamp về tâm phòng để tránh min > max gây kết quả sai.
        float x = (minX <= maxX)
            ? Math.max(minX, Math.min(maxX, tank.getPosition().x))
            : roomBounds.x + roomBounds.width / 2f;
        float y = (minY <= maxY)
            ? Math.max(minY, Math.min(maxY, tank.getPosition().y))
            : roomBounds.y + roomBounds.height / 2f;
        tank.resolvePosition(x, y);
    }

    /**
     * Spawn {@code count} mini-tank quanh boss và đăng ký chúng vào RoomProgressionManager.
     * Phòng boss chỉ cleared sau khi boss chết VÀ tất cả mini-tank cũng chết.
     */
    private void spawnMiniTanks(Tank boss, int count) {
        float bx = boss.getPosition().x, by = boss.getPosition().y;
        String bossRoomKey = bossRoomKeys.getOrDefault(boss, "BOSS");
        // Tìm roomKey thường tương ứng với door key ("BOSS_ROOM_N" → "ROOM_N")
        String roomKey = bossRoomKey.replace("BOSS_", "");
        Rectangle bounds = bossRoomBounds.get(boss);
        List<Tank> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Phân bố các mini quanh boss theo hình tia
            float angle = (360f / count) * i;
            float rad = com.badlogic.gdx.math.MathUtils.degreesToRadians * angle;
            float spawnX = bx + com.badlogic.gdx.math.MathUtils.cos(rad) * 60f;
            float spawnY = by + com.badlogic.gdx.math.MathUtils.sin(rad) * 60f;
            if (bounds != null) {
                float pad = GameConfig.ENEMY_WIDTH / 2f + 10f;
                spawnX = com.badlogic.gdx.math.MathUtils.clamp(spawnX, bounds.x + pad, bounds.x + bounds.width - pad);
                spawnY = com.badlogic.gdx.math.MathUtils.clamp(spawnY, bounds.y + pad, bounds.y + bounds.height - pad);
            }
            Tank mini = TankFactory.createEnemy(spawnX, spawnY, roomKey);
            enemies.add(mini);
            enemyRoomKeys.put(mini, roomKey);
            pathTimers.put(mini, 0f);
            spawned.add(mini);
        }
        // Đăng ký mini-tank vào RoomProgressionManager —
        // phòng boss chỉ cleared khi boss + tất cả mini-tank đều đã chết.
        roomManager.registerMiniTanks(roomKey, spawned);
    }

    // ─── Getters (dùng bởi GameRenderer) ────────────────────────────────────

    public Tank getPlayer() {
        return player;
    }

    public List<Tank> getEnemies() {
        return enemies;
    }

    public List<Tank> getBosses() {
        return bosses;
    }

    /**
     * @deprecated Dùng getBosses() thay thế.
     */
    @Deprecated
    public Tank getBoss() {
        return bosses.isEmpty() ? null : bosses.get(0);
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

    public DungeonMap getDungeonMap() {
        return dungeonMap;
    }

    public RoomProgressionManager getRoomManager() {
        return roomManager;
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isPlayerHubOpen() {
        return ((PlayerTurretComponent) player.getTurret()).isHubOpen();
    }

    /**
     * Trả về doorKey của phòng boss mà boss này thuộc về.
     * Dùng bởi GameRenderer để lọc boss HP bar chỉ hiện đúng phòng.
     */
    public String getBossRoomKeyFor(Tank boss) {
        return bossRoomKeys.getOrDefault(boss, "");
    }
}
