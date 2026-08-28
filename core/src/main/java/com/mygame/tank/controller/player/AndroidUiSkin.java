package com.mygame.tank.controller.player;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Builds a minimal {@link Skin} for on-screen Android controls entirely from
 * procedurally generated {@link Pixmap}s — no atlas or external asset file needed.
 *
 * <h3>Style names</h3>
 * <ul>
 *   <li>{@code "default"} — {@link TouchpadStyle} (background + knob circles)</li>
 *   <li>{@code "fire"}    — {@link ButtonStyle} for the fire button (red circle)</li>
 *   <li>{@code "slot"}    — {@link ButtonStyle} for equip/hub-slot buttons (grey square)</li>
 * </ul>
 *
 * <p>Dispose this object (not the Skin itself) when the screen is disposed —
 * it owns all the generated {@link Texture}s.
 */
public final class AndroidUiSkin implements Disposable {

    private final Skin skin;
    private final Array<Texture> ownedTextures = new Array<>();

    public AndroidUiSkin() {
        skin = new Skin();
        buildTouchpadStyle();
        buildFireButtonStyle();
        buildSlotButtonStyle();
    }

    /** @return the fully populated {@link Skin}. Do NOT dispose the Skin directly. */
    public Skin getSkin() {
        return skin;
    }

    // ─── Style builders ───────────────────────────────────────────────────────

    private void buildTouchpadStyle() {
        // Background: semi-transparent dark circle
        Texture bg   = circle(160, new Color(0f, 0f, 0f, 0.45f), new Color(0.6f, 0.6f, 0.6f, 0.7f));
        // Knob: brighter circle
        Texture knob = circle(64,  new Color(0.9f, 0.9f, 0.9f, 0.85f), new Color(0.5f, 0.5f, 0.5f, 0.85f));
        ownedTextures.add(bg);
        ownedTextures.add(knob);

        TouchpadStyle style = new TouchpadStyle();
        style.background = new TextureRegionDrawable(new TextureRegion(bg));
        style.knob       = new TextureRegionDrawable(new TextureRegion(knob));
        skin.add("default", style, TouchpadStyle.class);
    }

    private void buildFireButtonStyle() {
        // Up: red circle; Down: darker red
        Texture up   = circle(100, new Color(0.85f, 0.15f, 0.15f, 0.90f), new Color(0.6f, 0.1f, 0.1f, 0.90f));
        Texture down = circle(100, new Color(0.55f, 0.05f, 0.05f, 0.90f), new Color(0.4f, 0.05f, 0.05f, 0.90f));
        ownedTextures.add(up);
        ownedTextures.add(down);

        ButtonStyle style = new ButtonStyle();
        style.up   = new TextureRegionDrawable(new TextureRegion(up));
        style.down = new TextureRegionDrawable(new TextureRegion(down));
        skin.add("fire", style, ButtonStyle.class);
    }

    private void buildSlotButtonStyle() {
        // Equipment / hub slot: rounded grey square
        Texture up   = roundRect(64, 64, new Color(0.25f, 0.25f, 0.30f, 0.85f), new Color(0.55f, 0.55f, 0.60f, 0.85f));
        Texture down = roundRect(64, 64, new Color(0.45f, 0.55f, 0.65f, 0.90f), new Color(0.35f, 0.45f, 0.55f, 0.90f));
        ownedTextures.add(up);
        ownedTextures.add(down);

        ButtonStyle style = new ButtonStyle();
        style.up   = new TextureRegionDrawable(new TextureRegion(up));
        style.down = new TextureRegionDrawable(new TextureRegion(down));
        skin.add("slot", style, ButtonStyle.class);
    }

    // ─── Pixmap helpers ───────────────────────────────────────────────────────

    /**
     * Creates a Texture with a filled anti-aliased circle.
     *
     * @param size      width and height in pixels (circle is inscribed)
     * @param fill      inner fill color
     * @param border    1-px border color
     */
    private Texture circle(int size, Color fill, Color border) {
        Pixmap pix = new Pixmap(size, size, Format.RGBA8888);
        pix.setColor(0, 0, 0, 0);
        pix.fill();

        float cx = size / 2f;
        float cy = size / 2f;
        float r  = cx - 1f;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float dx = x - cx + 0.5f;
                float dy = y - cy + 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist <= r - 1.5f) {
                    pix.setColor(fill);
                    pix.drawPixel(x, y);
                } else if (dist <= r) {
                    // Anti-alias edge: blend border with fill
                    float t = (r - dist) / 1.5f;
                    pix.setColor(
                        lerp(border.r, fill.r, t),
                        lerp(border.g, fill.g, t),
                        lerp(border.b, fill.b, t),
                        lerp(border.a, fill.a, t));
                    pix.drawPixel(x, y);
                }
                // outside r: transparent (already 0)
            }
        }

        Texture tex = new Texture(pix);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pix.dispose();
        return tex;
    }

    /**
     * Creates a Texture with a filled rounded rectangle (corner radius = 10px).
     */
    private Texture roundRect(int w, int h, Color fill, Color border) {
        Pixmap pix = new Pixmap(w, h, Format.RGBA8888);
        pix.setColor(0, 0, 0, 0);
        pix.fill();

        int r = 10; // corner radius
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float dist = cornerDist(x, y, w, h, r);
                if (dist <= 0f) {
                    pix.setColor(fill);
                    pix.drawPixel(x, y);
                } else if (dist <= 1.5f) {
                    float t = 1f - dist / 1.5f;
                    pix.setColor(
                        lerp(border.r, fill.r, t),
                        lerp(border.g, fill.g, t),
                        lerp(border.b, fill.b, t),
                        lerp(border.a, fill.a, t));
                    pix.drawPixel(x, y);
                }
            }
        }

        Texture tex = new Texture(pix);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pix.dispose();
        return tex;
    }

    /** Signed distance from pixel to the rounded-rect interior (negative = inside). */
    private float cornerDist(int px, int py, int w, int h, int r) {
        // Map to closest corner quadrant
        float x = Math.max(r - px - 0.5f, Math.max(px + 0.5f - (w - r), 0f));
        float y = Math.max(r - py - 0.5f, Math.max(py + 0.5f - (h - r), 0f));
        return (float) Math.sqrt(x * x + y * y) - r;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ─── Dispose ──────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        for (Texture t : ownedTextures) t.dispose();
        ownedTextures.clear();
        skin.dispose();
    }
}
