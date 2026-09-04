package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý tất cả sprite texture dùng bởi {@link GameRenderer}.
 *
 * <h3>Dynamic texture registry</h3>
 * All textures are also registered in {@link #textureRegistry} under a string key.
 * {@link GameRenderer} looks up textures via {@link #getTexture(String)} using the
 * key stored in {@link com.mygame.tank.entity.component.VisualComponent}, so adding
 * a new boss only requires placing PNG files in assets and referencing the key in JSON.
 *
 * <h3>Floor tiles:</h3>
 * <p>background.jpg 1024×1024 gồm 4 ô 512×512:
 * <ul>
 *   <li>floorTiles[0] — top-left</li>
 *   <li>floorTiles[1] — top-right</li>
 *   <li>floorTiles[2] — bottom-left</li>
 *   <li>floorTiles[3] — bottom-right</li>
 * </ul>
 * Khi vẽ floor, mỗi tile 48×48 sẽ lấy ngẫu nhiên 1 trong 4 sub-tiles,
 * nhưng không trùng lặp liên tiếp (last-tile tracking).
 *
 * <h3>Wall & Cover:</h3>
 * <ul>
 *   <li>wallTexture (wall.png) — tường biên dungeon, REPEAT wrap</li>
 *   <li>coverTexture (cover.png) — cover obstacle bên trong phòng, không phá hủy được</li>
 * </ul>
 */
public class SpriteAssets {

    // ─── Tank body ────────────────────────────────────────────────────────────
    public final Texture bodyTexture;
    public final TextureRegion bodyRegion;

    // ─── Default weapon sheet ────────────────────────────────────────────────
    public final Texture defaultWeaponSheet;
    public final TextureRegion defaultTurret;
    public final TextureRegion defaultBullet;
    public final TextureRegion defaultMuzzleFlash;
    public final TextureRegion defaultWeaponIcon;

    // ─── Boss sheet — Iron Guard (boss.png, 768×256, 3×256 columns) ───────────
    public final Texture bossSheet;
    public final TextureRegion bossBody;
    public final TextureRegion bossTurret;
    public final TextureRegion bossProjectile;

    // ─── Viper boss sheet (boss_viper.png, 677×369, 3 equal columns) ─────────
    public final Texture viperSheet;
    public final TextureRegion viperBody;
    public final TextureRegion viperTurret;
    public final TextureRegion viperProjectile;

    // ─── Siege boss sheet (boss_siege.png, 869×287, 3 equal columns) ─────────
    public final Texture siegeSheet;
    public final TextureRegion siegeBody;
    public final TextureRegion siegeTurret;
    public final TextureRegion siegeProjectile;

    // ─── Floor tiles: 4 sub-regions cắt từ background.jpg (1024×1024) ────────
    /**
     * 4 floor tile sub-textures từ background.jpg:
     * [0]=top-left, [1]=top-right, [2]=bottom-left, [3]=bottom-right.
     */
    public final TextureRegion[] floorTiles;
    private final Texture backgroundSheet;

    // ─── Wall texture ─────────────────────────────────────────────────────────
    /**
     * Tường biên dungeon — wall.png.
     * Vẽ theo từng ô tile×tile (cùng kích thước với floor background tile).
     */
    public final Texture wallTexture;

    // ─── Cover texture ────────────────────────────────────────────────────────
    /**
     * Khối cover bên trong phòng — cover.png.
     */
    public final Texture coverTexture;

    // ─── Fog of war mask ─────────────────────────────────────────────────────
    /**
     * Fog mask: vòng tròn trắng gradient trên nền đen.
     * Dùng multiplicative blend (GL_ZERO, GL_SRC_COLOR).
     */
    public final Texture fogMask;

    // ─── Weapon & Equipment Icons ────────────────────────────────────────────
    public final java.util.Map<com.mygame.tank.weapon.WeaponType, Texture> weaponIcons;
    public final java.util.Map<com.mygame.tank.weapon.EquipmentType, Texture> equipmentIcons;

    // ─── Dynamic texture registry ──────────────────────────────────────────────
    /**
     * All named textures indexed by string key.
     * VisualComponent stores keys; GameRenderer resolves them here at render time.
     * Falls back to a 1×1 magenta "missing" texture when a key is not found.
     */
    private final Map<String, TextureRegion> textureRegistry = new HashMap<>();
    private TextureRegion missingTexture;

    // ─── Constructor ─────────────────────────────────────────────────────────

    public SpriteAssets() {
        // Body
        bodyTexture = new Texture(Gdx.files.internal("body/body.png"));
        bodyTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        bodyRegion = new TextureRegion(bodyTexture);

        // Default weapon sheet
        defaultWeaponSheet = new Texture(Gdx.files.internal("weapon/default.png"));
        defaultWeaponSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        final int CELL = 256, PAD = 8, SIZE = CELL - PAD * 2;
        defaultTurret = new TextureRegion(defaultWeaponSheet, PAD, PAD, SIZE, SIZE);
        defaultBullet = new TextureRegion(defaultWeaponSheet, CELL + PAD, PAD, SIZE, SIZE);
        defaultMuzzleFlash = new TextureRegion(defaultWeaponSheet, PAD, CELL + PAD, SIZE, SIZE);
        defaultWeaponIcon = new TextureRegion(defaultWeaponSheet, CELL + PAD, CELL + PAD, SIZE, SIZE);

        // Boss sheet — Iron Guard (boss.png, 768×256)
        bossSheet = new Texture(Gdx.files.internal("boss/boss.png"));
        bossSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        bossBody       = new TextureRegion(bossSheet, 0,   0, 256, 256);
        bossTurret     = new TextureRegion(bossSheet, 256, 0, 256, 256);
        bossProjectile = new TextureRegion(bossSheet, 512, 0, 256, 256);

        // Viper boss sheet (boss_viper_677x369.png, 677×369 — 3 equal columns ~225px wide)
        viperSheet = new Texture(Gdx.files.internal("boss/boss_viper_677x369.png"));
        viperSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        {
            int vW = viperSheet.getWidth();   // 677
            int vH = viperSheet.getHeight();  // 369
            int vCol = vW / 3;               // 225 (integer division; last col gets remainder)
            viperBody       = new TextureRegion(viperSheet,       0, 0, vCol, vH);
            viperTurret     = new TextureRegion(viperSheet,   vCol, 0, vCol, vH);
            viperProjectile = new TextureRegion(viperSheet, 2*vCol, 0, vW - 2*vCol, vH);
        }

        // Siege boss sheet (boss_siege_869x287.png, 869×287 — 3 equal columns ~289px wide)
        siegeSheet = new Texture(Gdx.files.internal("boss/boss_siege_869x287.png"));
        siegeSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        {
            int sW = siegeSheet.getWidth();   // 869
            int sH = siegeSheet.getHeight();  // 287
            int sCol = sW / 3;               // 289
            siegeBody       = new TextureRegion(siegeSheet,       0, 0, sCol, sH);
            siegeTurret     = new TextureRegion(siegeSheet,   sCol, 0, sCol, sH);
            siegeProjectile = new TextureRegion(siegeSheet, 2*sCol, 0, sW - 2*sCol, sH);
        }

        // Build missing-texture (1×1 magenta)
        Pixmap mp = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        mp.setColor(1f, 0f, 1f, 1f); mp.fill();
        missingTexture = new TextureRegion(new Texture(mp));
        mp.dispose();

        // Register all known textures under their VisualComponent keys
        register("player_body",       bodyRegion);
        register("player_turret",     defaultTurret);
        register("player_bullet",     defaultBullet);
        register("enemy_body",        bodyRegion);   // enemies share the body sprite for now
        register("enemy_turret",      defaultTurret);
        register("enemy_bullet",      defaultBullet);
        register("boss_iron_body",    bossBody);
        register("boss_iron_turret",  bossTurret);
        register("boss_iron_bullet",  bossProjectile);
        // Viper — dedicated art from boss_viper.png
        register("boss_viper_body",   viperBody);
        register("boss_viper_turret", viperTurret);
        register("boss_viper_bullet", viperProjectile);
        // Siege — dedicated art from boss_siege.png
        register("boss_siege_body",   siegeBody);
        register("boss_siege_turret", siegeTurret);
        register("boss_siege_bullet", siegeProjectile);

        // ── Floor tiles từ background.jpg (1024×1024 = 4 ô 512×512) ─────────
        backgroundSheet = new Texture(Gdx.files.internal("maps/background.jpg"));
        backgroundSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

// Lấy kích thước thực tế (972x972) và chia đều thành 4 ô
        int sheetW = backgroundSheet.getWidth();  // = 972
        int sheetH = backgroundSheet.getHeight(); // = 972
        int tileW = sheetW / 2; // = 486
        int tileH = sheetH / 2; // = 486

        floorTiles = new TextureRegion[4];
        floorTiles[0] = new TextureRegion(backgroundSheet, 0, 0, tileW, tileH); // Trên-Trái
        floorTiles[1] = new TextureRegion(backgroundSheet, tileW, 0, tileW, tileH); // Trên-Phải
        floorTiles[2] = new TextureRegion(backgroundSheet, 0, tileH, tileW, tileH); // Dưới-Trái
        floorTiles[3] = new TextureRegion(backgroundSheet, tileW, tileH, tileW, tileH); // Dưới-Phải

        // ── Wall texture ──────────────────────────────────────────────────────
        // Wall: 500x500, không lặp (chỉ resize theo kích thước vẽ)
        wallTexture = new Texture(Gdx.files.internal("maps/wall.png"));
        wallTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // ── Cover texture ─────────────────────────────────────────────────────
        coverTexture = new Texture(Gdx.files.internal("maps/cover.png"));
        coverTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // ── Fog of war mask (generated by code) ───────────────────────────────
        fogMask = buildFogMask(512, 512);

        // ── Weapon & Equipment Icons ──────────────────────────────────────────
        weaponIcons = new java.util.EnumMap<>(com.mygame.tank.weapon.WeaponType.class);
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.DEFAULT_BULLET, new Texture(Gdx.files.internal("weapon/DEFAULT_BULLET.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.SUBMACHINE_GUN, new Texture(Gdx.files.internal("weapon/SUBMACHINE_GUN.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.ARMOR_PIERCE, new Texture(Gdx.files.internal("weapon/ARMOR_PIERCE.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.CANNON, new Texture(Gdx.files.internal("weapon/CANNON.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.STUN_SHELL, new Texture(Gdx.files.internal("weapon/STUN_SHELL.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.LASER, new Texture(Gdx.files.internal("weapon/LASER.jpg")));
        weaponIcons.put(com.mygame.tank.weapon.WeaponType.HOMING_MISSILE, new Texture(Gdx.files.internal("weapon/HOMING_MISSILE.jpg")));
        for (Texture t : weaponIcons.values()) {
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }

        equipmentIcons = new java.util.EnumMap<>(com.mygame.tank.weapon.EquipmentType.class);
        equipmentIcons.put(com.mygame.tank.weapon.EquipmentType.SMOKE_BOMB, new Texture(Gdx.files.internal("weapon/SMOCK.jpg")));
        equipmentIcons.put(com.mygame.tank.weapon.EquipmentType.ENERGY_SHIELD, new Texture(Gdx.files.internal("weapon/SHIELD.jpg")));
        equipmentIcons.put(com.mygame.tank.weapon.EquipmentType.ANTI_TANK_MINE, new Texture(Gdx.files.internal("weapon/MINE.jpg")));
        equipmentIcons.put(com.mygame.tank.weapon.EquipmentType.EMP_FIELD, new Texture(Gdx.files.internal("weapon/EMP Field.jpg")));
        for (Texture t : equipmentIcons.values()) {
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
    }

    // ─── Dynamic registry helpers ─────────────────────────────────────────────

    /** Registers (or replaces) a TextureRegion under the given key. */
    public void register(String key, TextureRegion region) {
        textureRegistry.put(key, region);
    }

    /**
     * Returns the TextureRegion registered under {@code key}.
     * If the key is not found, returns a 1×1 magenta placeholder so rendering
     * never crashes — the magenta colour makes missing assets immediately visible.
     */
    public TextureRegion getTexture(String key) {
        TextureRegion r = textureRegistry.get(key);
        return (r != null) ? r : missingTexture;
    }

    // ─── Fog mask builder ────────────────────────────────────────────────────

    /**
     * Sinh fog mask: nền TRẮNG (không làm tối gì thêm khi nhân), vòng tròn
     * gradient tối dần ra rìa ở giữa, và vùng ngoài vòng tròn (4 góc texture)
     * giữ nguyên độ tối tối thiểu FOG_EDGE_MIN_BRIGHTNESS thay vì trắng —
     * tránh viền cứng "tối→sáng đột ngột" khi mask bị kéo giãn ở FOG_RADIUS lớn.
     *
     * <h3>Lịch sử lỗi đã sửa:</h3>
     * <p>Bản đầu: nền đen (RGB=0) ở góc → khi FOG_RADIUS tăng và mask bị kéo
     * giãn không đều (fogW≠fogH), góc đen phủ đen gần hết màn hình.
     * <p>Bản sửa lần 1: đổi nền góc thành trắng → hết đen toàn màn hình,
     * nhưng lộ viền cứng dạng elip (chỗ dist=1 nhảy đột ngột edgeMin→1).
     * <p>Bản hiện tại: góc giữ nguyên edgeMin (không nhảy về trắng), liền
     * mạch với gradient rìa vòng tròn — không còn viền cứng ở bất kỳ hướng nào.
     */
    private static Texture buildFogMask(int w, int h) {
        Pixmap pix = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pix.setColor(1f, 1f, 1f, 1f); // nền trắng = trong suốt khi nhân (multiply)
        pix.fill();

        float cx = w / 2f, cy = h / 2f;
        float maxR = Math.min(cx, cy);
        float innerRatio = com.mygame.tank.config.GameConfig.FOG_INNER_SOLID_RATIO;
        float edgeMin = com.mygame.tank.config.GameConfig.FOG_EDGE_MIN_BRIGHTNESS;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float dx = (x - cx) / maxR;
                float dy = (y - cy) / maxR;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                float bright;
                if (dist <= innerRatio) {
                    bright = 1f; // vùng lõi luôn sáng 100% (không tối)
                } else if (dist < 1f) {
                    // Map [innerRatio, 1] -> [1, 0] rồi smoothstep, sau đó
                    // rescale vào [edgeMin, 1] để rìa không tối tuyệt đối,
                    // mờ dần mượt thay vì cắt cứng.
                    float t = 1f - (dist - innerRatio) / (1f - innerRatio);
                    float smooth = t * t * (3f - 2f * t);
                    bright = edgeMin + smooth * (1f - edgeMin);
                } else {
                    // Ngoài vòng tròn (góc texture): giữ nguyên edgeMin, liền
                    // mạch với gradient bên trong — không nhảy đột ngột lên
                    // trắng, tránh lộ viền elip cứng ở 4 góc màn hình.
                    bright = edgeMin;
                }

                pix.setColor(bright, bright, bright, 1f);
                pix.drawPixel(x, y);
            }
        }

        Texture tex = new Texture(pix);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pix.dispose();
        return tex;
    }

    // ─── Dispose ─────────────────────────────────────────────────────────────

    public void dispose() {
        bodyTexture.dispose();
        defaultWeaponSheet.dispose();
        bossSheet.dispose();
        viperSheet.dispose();
        siegeSheet.dispose();
        backgroundSheet.dispose();
        wallTexture.dispose();
        coverTexture.dispose();
        fogMask.dispose();
        for (Texture t : weaponIcons.values()) t.dispose();
        for (Texture t : equipmentIcons.values()) t.dispose();
    }
}
