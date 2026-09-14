package com.mygame.tank.config;

/**
 * Central configuration constants. Avoids magic numbers throughout the codebase.
 */
public final class GameConfig {

    private GameConfig() {
    }

    // ─── Map ──────────────────────────────────────────────────────────────────
    public static final int MAP_TILE_SIZE = 48;
    public static final int MAP_COLS = 60;
    public static final int MAP_ROWS = 60;
    public static final float MAP_WIDTH = MAP_COLS * MAP_TILE_SIZE;   // 2880 px
    public static final float MAP_HEIGHT = MAP_ROWS * MAP_TILE_SIZE;   // 2880 px

    // ─── Dungeon Generation (BSP) ─────────────────────────────────────────────
    /**
     * Số cấp độ chia BSP (3 cấp = 8 leaf node = 8 phòng tối đa).
     */
    public static final int BSP_MAX_DEPTH = 3;
    /**
     * Kích thước phòng tối thiểu (tile).
     */
    public static final int ROOM_MIN_TILES = 10;
    /**
     * Kích thước phòng tối đa (tile).
     */
    public static final int ROOM_MAX_TILES = 15;
    /**
     * Margin tối thiểu giữa cạnh phòng và cạnh vùng BSP (tile).
     */
    public static final int ROOM_MARGIN_TILES = 1;
    /**
     * Độ rộng hành lang nối phòng (tile).
     */
    public static final int CORRIDOR_WIDTH_TILES = 2;
    /**
     * Độ dài tối đa cho mỗi đoạn hành lang thẳng (tile).
     * Nếu đoạn ngang hoặc dọc của hành lang chữ L vượt ngưỡng này, hành lang
     * sẽ được chia đệ quy qua điểm trung gian cho đến khi mỗi đoạn ≤ giá trị này.
     */
    public static final int MAX_CORRIDOR_LENGTH_TILES = 6;
    /**
     * Kích thước 1 khối cover (tile) — vuông NxN.
     */
    public static final int COVER_SIZE_TILES = 1;
    /**
     * Số khối cover tối thiểu sinh trong 1 phòng ENEMY/BOSS.
     */
    public static final int COVER_MIN_PER_ROOM = 0;
    /**
     * Số khối cover tối đa sinh trong 1 phòng ENEMY/BOSS.
     */
    public static final int COVER_MAX_PER_ROOM = 3;
    /**
     * Khoảng đệm tối thiểu (tile) giữa cover và tường phòng.
     */
    public static final int COVER_WALL_PADDING_TILES = 2;
    /**
     * Khoảng đệm tối thiểu (tile) giữa tâm phòng (spawn boss) và cover.
     */
    public static final int COVER_CENTER_CLEARANCE_TILES = 1;

    // ─── Enemy spawn spacing ──────────────────────────────────────────────────
    /**
     * Khoảng cách tối thiểu (pixel) giữa 2 điểm spawn quái trong cùng 1 phòng.
     */
    public static final float ENEMY_SPAWN_MIN_DISTANCE = 140f;

    // ─── Fog of War ───────────────────────────────────────────────────────────
    /**
     * Bán trục X của ellipse nhìn thấy quanh player (pixel world) — phủ hết nửa viewport.
     */
    public static final float FOG_RADIUS_X = 540f;
    /**
     * Bán trục Y của ellipse nhìn thấy quanh player (pixel world) — phủ hết nửa viewport.
     */
    public static final float FOG_RADIUS_Y = 360f;
    /**
     * Tỉ lệ (0-1) bán kính trong cùng luôn sáng 100% (không bị mờ dần).
     * Vùng còn lại (từ tỉ lệ này ra tới viền) sẽ mờ dần theo smoothstep.
     */
    public static final float FOG_INNER_SOLID_RATIO = 0.4f;
    /**
     * Độ sáng tối thiểu ở rìa/ngoài fog mask (0 = đen hoàn toàn, 1 = không
     * tối). Tăng giá trị này = toàn bộ vùng fog (kể cả rìa) sáng hơn.
     */
    public static final float FOG_EDGE_MIN_BRIGHTNESS = 0.1f;

    // ─── Camera smooth follow ─────────────────────────────────────────────────
    /**
     * Hệ số lerp camera (0=không theo, 1=snap ngay).
     */
    public static final float CAMERA_LERP = 1f;

    // ─── A* Pathfinding ───────────────────────────────────────────────────────
    /**
     * Khoảng thời gian (giây) giữa 2 lần tính lại đường đi A* cho mỗi enemy.
     */
    public static final float ENEMY_PATHFIND_INTERVAL = 0.5f;

    // ─── Camera / Viewport ────────────────────────────────────────────────────
    public static final float VIEWPORT_WIDTH = 1080f;
    public static final float VIEWPORT_HEIGHT = 540f;

    // ─── Player ───────────────────────────────────────────────────────────────
    public static final float PLAYER_MAX_SPEED = 160f;   // px/s
    public static final float PLAYER_ACCELERATION = 450f;   // px/s^2
    public static final float PLAYER_DECELERATION = 500f;   // px/s^2
    public static final float PLAYER_MAX_HP = 600f;
    public static final float PLAYER_WIDTH = 40f;
    public static final float PLAYER_HEIGHT = 40f;

    // ─── Weapon & Equipment Loadout ──────────────────────────────────────────
    public static final int DAMAGE_WEAPON_SLOTS = 7;   // Weapon hub slots
    public static final int EQUIPMENT_SLOTS = 4;   // Tactical equipment slots

    // ─── Weapon Hub ──────────────────────────────────────────────────────────
    /**
     * Time game continues but firing is blocked while hub is open (seconds).
     */
    public static final float HUB_OPEN_LOCK_FIRE = 0f;   // Hub does NOT pause game

    // ─── Group A: Direct DPS (Unlimited Ammo) ────────────────────────────────

    // DEFAULT_BULLET — Basic single-shot cannon (Mid-range combat)
    public static final float DEFAULT_DAMAGE = 40f;
    public static final float DEFAULT_FIRE_RATE = 1.1f;  // Seconds between shots
    public static final float DEFAULT_SPEED = 300f;   // px/s
    public static final float DEFAULT_LIFETIME = 0.8f;   // Effective Range = 420 * 0.8 = 336px (~7 tiles)
    public static final int DEFAULT_MAX_AMMO = -1;     // -1 = unlimited

    // SUBMACHINE_GUN — High fire rate, close-range bloom/spread
    public static final float SMG_DAMAGE = 7f;
    public static final float SMG_FIRE_RATE = 0.08f;
    public static final float SMG_SPEED = 380f;   // px/s
    public static final float SMG_BASE_SPREAD_DEG = 3f;     // Initial spread angle
    public static final float SMG_MAX_SPREAD_DEG = 16f;    // Max spread during sustained fire
    public static final float SMG_SPREAD_BUILD_RATE = 5f;    // Spread increase per second
    public static final float SMG_SPREAD_DECAY_RATE = 10f;   // Spread decay per second on release
    public static final float SMG_LIFETIME = 0.6f;   // Effective Range = 380 * 0.6 = 228px (~4.7 tiles)
    public static final int SMG_MAX_AMMO = 40;     // Unlimited

    // ARMOR_PIERCE — High speed, long-range sniper projectile piercing multiple targets
    public static final float AP_DAMAGE = 40f;    // Base damage per hit
    public static final float AP_DAMAGE_FALLOFF = 0.75f;  // Damage multiplier on subsequent pierces
    public static final int AP_MAX_PIERCE = 3;      // Max number of pierces
    public static final float AP_COOLDOWN = 0.75f;   // Cooldown between shots (now ammo-limited, cooldown-paced)
    public static final float AP_SPEED = 550f;   // Fast projectile speed
    public static final float AP_LIFETIME = 1.1f;   // Effective Range = 550 * 1.1 = 605px (~12.6 tiles)
    public static final int AP_MAX_AMMO = 10;      // Limited charges

    // ─── Group B: Area of Effect (AoE) ───────────────────────────────────────

    // CANNON — Heavy explosive shell with impact splash damage
    public static final float CANNON_DIRECT_DAMAGE = 50f;
    public static final float CANNON_AOE_DAMAGE = 30f;
    public static final float CANNON_AOE_RADIUS = 90f;    // Splash radius
    public static final float CANNON_FIRE_RATE = 0f;     // Cooldown driven
    public static final float CANNON_COOLDOWN = 4.0f;   // Seconds
    public static final float CANNON_SPEED = 320f;
    public static final float CANNON_LIFETIME = 1.3f;   // Effective Range = 320 * 1.3 = 416px (~8.6 tiles)
    public static final int CANNON_MAX_AMMO = 8;      // Limited charges

    // STUN_SHELL — AoE disabling shell
    public static final float STUN_DAMAGE = 15f;
    public static final float STUN_AOE_RADIUS = 80f;
    public static final float STUN_DURATION = 2.0f;   // Stun duration in seconds
    public static final float STUN_COOLDOWN = 5.0f;
    public static final float STUN_SPEED = 300f;
    public static final float STUN_LIFETIME = 1.2f;   // Effective Range = 300 * 1.2 = 360px (~7.5 tiles)
    public static final int STUN_MAX_AMMO = 8;      // Limited charges

    // ─── Group C: Special / Control ──────────────────────────────────────────

    // LASER — High-damage directional beam
    public static final float LASER_TELEGRAPH_TIME = 0.5f;   // Charge-up delay
    public static final float LASER_BEAM_TIME = 0.35f;  // Active beam duration
    public static final float LASER_DAMAGE = 95f;    // Total damage over beam phase
    public static final float LASER_COOLDOWN = 6.0f;   // Cooldown after firing
    public static final float LASER_RANGE = 550f;   // Maximum beam length in pixels (~11.4 tiles)
    public static final int LASER_MAX_AMMO = 8;      // Limited charges

    // HOMING_MISSILE — Guided tracking missile
    public static final float HOMING_DAMAGE = 45f;
    public static final float HOMING_COOLDOWN = 4.5f;
    public static final float HOMING_SPEED = 240f;
    public static final float HOMING_TURN_RATE_DEG = 140f;   // Steering rotation speed in deg/s
    public static final float HOMING_LIFETIME = 2.2f;   // Maximum flight duration (Max distance ~528px)
    public static final float HOMING_LOCK_RANGE = 450f;   // Target acquisition range in pixels
    public static final int HOMING_MAX_AMMO = 20;      // Limited charges

    // ─── Group D: Tactical Equipment ─────────────────────────────────────────

    // SMOKE_BOMB
    public static final float SMOKE_DURATION = 7.0f;   // Cloud persistence time (seconds)
    public static final float SMOKE_RADIUS = 85f;    // Effect radius in pixels
    public static final float SMOKE_COOLDOWN = 10.0f;
    public static final float SMOKE_THROW_RANGE = 150f;   // Deployment distance from tank

    // ENERGY_SHIELD
    public static final float SHIELD_BLOCK_TIME = 2.5f;   // Invincibility/Blocking duration
    public static final float SHIELD_COOLDOWN = 14.0f;

    // ANTI_TANK_MINE
    public static final float MINE_ARM_DELAY = 1.0f;   // Arming delay before active
    public static final float MINE_TRIGGER_RADIUS = 32f;    // Detonation trigger radius
    public static final float MINE_AOE_RADIUS = 90f;    // Explosion radius
    public static final float MINE_DAMAGE = 85f;
    public static final float MINE_COOLDOWN = 15.0f;
    public static final float MINE_LIFETIME = 45.0f;  // Self-destruct delay if untriggered

    // EMP_FIELD
    public static final float EMP_RADIUS = 130f;   // Pulse impact radius
    public static final float EMP_DURATION = 3.5f;   // Disables enemy actions/weapons
    public static final float EMP_COOLDOWN = 20.0f;
    public static final float EMP_EFFECT_LIFETIME = 3.5f;   // Visual pulse lifetime

    // ─── Projectile Fallbacks ────────────────────────────────────────────────
    public static final float BULLET_SIZE = 8f;
    public static final float BULLET_LIFETIME = 0.8f;   // Fallback lifetime default

    // ─── Basic Enemy Tank ────────────────────────────────────────────────────
    public static final float ENEMY_BASIC_HP = 65f;
    public static final float ENEMY_BASIC_SPEED = 75f;
    public static final float ENEMY_BASIC_DAMAGE = 12f;
    public static final float ENEMY_BASIC_FIRE_RATE = 1.8f;
    public static final float ENEMY_BASIC_BULLET_SPEED = 280f;
    public static final float ENEMY_WIDTH = 38f;
    public static final float ENEMY_HEIGHT = 38f;

    // Enemy AI logic parameters
    public static final float ENEMY_DETECT_RANGE = 320f;
    public static final float ENEMY_ATTACK_RANGE = 260f;
    public static final float ENEMY_LOST_TIME = 3f;

    // ─── Boss — Iron Guard ───────────────────────────────────────────────────
    public static final float BOSS_HP = 600f;
    public static final float BOSS_SPEED = 0f;
    public static final float BOSS_WIDTH = 56f;
    public static final float BOSS_HEIGHT = 56f;
    public static final float BOSS_PHASE2_THRESHOLD = 0.5f;
    public static final float BOSS_SPREAD_FIRE_RATE = 1.8f;
    public static final float BOSS_SPREAD_ANGLE_DEG = 30f;
    public static final float BOSS_BULLET_SPEED = 260f;
    public static final float BOSS_BULLET_DAMAGE = 18f;
    public static final float BOSS_CIRCLE_RADIUS = 110f;
    public static final float BOSS_CIRCLE_SPEED_DEG = 60f;
    public static final float BOSS_MINI_SPAWN_INTERVAL = 12f;
    public static final int BOSS_MINI_TANK_COUNT = 2;

    // ─── Boss Phase 3 ────────────────────────────────────────────────────────
    /**
     * HP threshold để vào Phase 3 (25% HP còn lại).
     */
    public static final float BOSS_PHASE3_THRESHOLD = 0.25f;
    /**
     * Interval spawn mini-tank ở Phase 3 (nhanh hơn Phase 2).
     */
    public static final float BOSS_PHASE3_MINI_SPAWN_INTERVAL = 7f;
    /**
     * Số mini-tank mỗi lần spawn ở Phase 3.
     */
    public static final int BOSS_PHASE3_MINI_TANK_COUNT = 3;

    // ─── Debug Settings ──────────────────────────────────────────────────────
    public static final boolean DEBUG_DEFAULT = false;
}
