package com.mygame.tank.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Manages all sprite texture assets used by {@link GameRenderer}.
 *
 * <h3>Assets</h3>
 * <ul>
 *   <li>{@code body/body.png}     — Player tank body, top-down view facing UP (512×512).</li>
 *   <li>{@code weapon/defualt.png} — Default weapon sprite sheet, 2×2 grid (512×512 total,
 *       each cell 256×256):
 *       <pre>
 *       ┌──────────┬──────────┐
 *       │  Turret  │  Bullet  │   ← row 0 (y=0..255)
 *       ├──────────┼──────────┤
 *       │  Muzzle  │  UI Icon │   ← row 1 (y=256..511)
 *       └──────────┴──────────┘
 *       </pre>
 *   </li>
 *   <li>{@code boss/boss.png}     — Boss sprite sheet, 1×3 grid (768×256 total,
 *       each cell 256×256):
 *       <pre>
 *       ┌──────────┬──────────┬──────────┐
 *       │   Body   │  Turret  │  Bullet  │
 *       └──────────┴──────────┴──────────┘
 *       </pre>
 *   </li>
 * </ul>
 *
 * <p>Call {@link #dispose()} when the renderer is disposed to free GPU memory.</p>
 */
public class SpriteAssets {

    // ─── Tank body ────────────────────────────────────────────────────────────

    /** Full tank body sprite, facing UP. */
    public final Texture bodyTexture;
    /** TextureRegion wrapper for bodyTexture (required by SpriteBatch.draw with rotation). */
    public final TextureRegion bodyRegion;

    // ─── Default weapon sheet ────────────────────────────────────────────────

    /** Raw 512×512 sprite sheet for the default bullet weapon. */
    public final Texture defaultWeaponSheet;

    /**
     * Turret + short cannon barrel, dark olive green, facing UP.
     * Source: top-left cell (0, 0, 256, 256) of the sheet.
     */
    public final TextureRegion defaultTurret;

    /**
     * Small bullet projectile (white body, grey tip) facing UP.
     * Source: top-right cell (256, 0, 256, 256) of the sheet.
     */
    public final TextureRegion defaultBullet;

    /**
     * Muzzle flash / orange burst, for MUZZLE_FLASH visual effects.
     * Source: bottom-left cell (0, 256, 256, 256) of the sheet.
     */
    public final TextureRegion defaultMuzzleFlash;

    /**
     * Compact UI icon (bullet in circular green background), for HUD weapon slots.
     * Source: bottom-right cell (256, 256, 256, 256) of the sheet.
     */
    public final TextureRegion defaultWeaponIcon;

    // ─── Boss sheet ──────────────────────────────────────────────────────────

    /** Raw 768x256 sprite sheet for the boss. */
    public final Texture bossSheet;
    
    /** Boss tank body facing UP. Source: left cell (0, 0, 256, 256). */
    public final TextureRegion bossBody;
    
    /** Boss tank turret facing UP. Source: middle cell (256, 0, 256, 256). */
    public final TextureRegion bossTurret;
    
    /** Boss projectile facing UP. Source: right cell (512, 0, 256, 256). */
    public final TextureRegion bossProjectile;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SpriteAssets() {
        // ── Body ──────────────────────────────────────────────────────────────
        bodyTexture = new Texture(Gdx.files.internal("body/body.png"));
        bodyTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        bodyRegion = new TextureRegion(bodyTexture);

        // ── Default weapon sheet ───────────────────────────────────────────────
        // NOTE: filename intentionally matches the provided asset: "defualt.png"
        defaultWeaponSheet = new Texture(Gdx.files.internal("weapon/default.png"));
        defaultWeaponSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // TextureRegion(texture, x, y, width, height) — y=0 is the TOP of the image file.
        // We use padding to avoid the black grid lines and borders.
        final int CELL = 256;
        final int PAD = 8;
        final int SIZE = CELL - PAD * 2;
        defaultTurret      = new TextureRegion(defaultWeaponSheet,        PAD,        PAD, SIZE, SIZE);
        defaultBullet      = new TextureRegion(defaultWeaponSheet, CELL + PAD,        PAD, SIZE, SIZE);
        defaultMuzzleFlash = new TextureRegion(defaultWeaponSheet,        PAD, CELL + PAD, SIZE, SIZE);
        defaultWeaponIcon  = new TextureRegion(defaultWeaponSheet, CELL + PAD, CELL + PAD, SIZE, SIZE);

        // ── Boss sheet ───────────────────────────────────────────────
        bossSheet = new Texture(Gdx.files.internal("boss/boss.png"));
        bossSheet.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        
        bossBody = new TextureRegion(bossSheet, 0, 0, 256, 256);
        bossTurret = new TextureRegion(bossSheet, 256, 0, 256, 256);
        bossProjectile = new TextureRegion(bossSheet, 512, 0, 256, 256);
    }

    // ─── Dispose ─────────────────────────────────────────────────────────────

    /**
     * Releases all GPU texture memory. Must be called when the renderer is disposed.
     */
    public void dispose() {
        bodyTexture.dispose();
        defaultWeaponSheet.dispose();
        bossSheet.dispose();
    }
}
