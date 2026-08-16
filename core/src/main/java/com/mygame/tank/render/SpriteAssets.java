package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Quản lý tất cả sprite texture dùng bởi {@link GameRenderer}.
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

    // ─── Boss sheet ──────────────────────────────────────────────────────────
    public final Texture bossSheet;
    public final TextureRegion bossBody;
    public final TextureRegion bossTurret;
    public final TextureRegion bossProjectile;

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

        // Boss sheet
        bossSheet = new Texture(Gdx.files.internal("boss/boss.png"));
        bossSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        bossBody = new TextureRegion(bossSheet, 0, 0, 256, 256);
        bossTurret = new TextureRegion(bossSheet, 256, 0, 256, 256);
        bossProjectile = new TextureRegion(bossSheet, 512, 0, 256, 256);

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
        backgroundSheet.dispose();
        wallTexture.dispose();
        coverTexture.dispose();
        fogMask.dispose();
    }
}
