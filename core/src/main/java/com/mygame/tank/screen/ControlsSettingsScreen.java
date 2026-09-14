package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.mygame.tank.TankGame;
import com.mygame.tank.config.ControlsConfig;

/**
 * Controls Settings screen — lets the player adjust the mobile touch-zone split ratio.
 *
 * <h3>Layout preview</h3>
 * A phone-shaped rectangle shows a live preview of the FIRE zone (top, orange)
 * and the MOVE+AIM joystick zone (bottom, split left/right).
 * The player can <em>drag the horizontal divider</em> up or down to resize the zones.
 *
 * <h3>Constraints</h3>
 * The joystick zone is always at least 50 % of the screen ({@link ControlsConfig#MIN_SPLIT})
 * so the divider cannot be dragged below the screen midpoint.
 *
 * <h3>Navigation</h3>
 * <ul>
 *   <li><b>SAVE &amp; BACK</b> — persists the current ratio and returns to {@code returnTo}</li>
 *   <li><b>RESET</b>          — resets ratio to {@link ControlsConfig#DEFAULT_SPLIT}</li>
 * </ul>
 */
public class ControlsSettingsScreen implements Screen {

    // ─── Preview dimensions (HUD units) ──────────────────────────────────────
    private static final float PREVIEW_W      = 240f;
    private static final float PREVIEW_H      = 400f;
    private static final float DIVIDER_GRAB   = 22f;  // px tolerance for grabbing divider

    // ─── Button geometry ──────────────────────────────────────────────────────
    private static final float BTN_W          = 260f;
    private static final float BTN_H          = 60f;
    private static final float BTN_CORNER     = 12f;

    // ─── Colours ──────────────────────────────────────────────────────────────
    private static final Color C_BG           = new Color(0.04f, 0.06f, 0.12f, 1f);
    private static final Color C_FIRE_ZONE    = new Color(0.90f, 0.40f, 0.10f, 0.55f);
    private static final Color C_MOVE_ZONE    = new Color(0.20f, 0.75f, 0.30f, 0.50f);
    private static final Color C_AIM_ZONE     = new Color(0.20f, 0.50f, 0.90f, 0.50f);
    private static final Color C_DIVIDER      = new Color(1.00f, 1.00f, 1.00f, 0.80f);
    private static final Color C_PREVIEW_BORD = new Color(0.35f, 0.70f, 1.00f, 0.90f);
    private static final Color C_SAVE         = new Color(0.10f, 0.50f, 0.20f, 0.90f);
    private static final Color C_RESET        = new Color(0.50f, 0.25f, 0.05f, 0.90f);
    private static final Color C_TEXT         = Color.WHITE;

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final TankGame game;
    private final Screen   returnTo;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private BitmapFont         font;
    private GlyphLayout        glyph;
    private MenuFontCache.Fonts fonts;

    // Preview rect in HUD coords (Y=0 at bottom) — recomputed each resize
    private float previewX, previewY;

    // Drag state
    private boolean draggingDivider = false;

    // Button rectangles (HUD coords)
    private final Rectangle btnSave  = new Rectangle();
    private final Rectangle btnReset = new Rectangle();

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param game     the application entry-point (for screen transitions)
     * @param returnTo the screen to return to when the player presses BACK
     */
    public ControlsSettingsScreen(TankGame game, Screen returnTo) {
        this.game     = game;
        this.returnTo = returnTo;
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, sw, sh);
        camera.update();

        batch = new SpriteBatch();
        shape = new ShapeRenderer();
        glyph = new GlyphLayout();
        fonts = MenuFontCache.create(sw, sh);
        font  = fonts.body;

        recomputeLayout(sw, sh);

        Gdx.input.setInputProcessor(new TouchHandler());
    }

    @Override
    public void render(float delta) {
        // Keep music fades running while in this screen
        game.getMusicManager().update(delta);

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(C_BG.r, C_BG.g, C_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        float split    = ControlsConfig.getSplitRatio();
        float dividerY = previewY + split * PREVIEW_H;  // HUD Y of divider inside preview

        // ── Draw preview phone frame ──────────────────────────────────────────
        shape.begin(ShapeRenderer.ShapeType.Filled);

        // Fire zone (top of preview)
        shape.setColor(C_FIRE_ZONE);
        shape.rect(previewX, dividerY, PREVIEW_W, PREVIEW_H - (dividerY - previewY));

        // MOVE zone (bottom-left)
        shape.setColor(C_MOVE_ZONE);
        shape.rect(previewX, previewY, PREVIEW_W / 2f, dividerY - previewY);

        // AIM zone (bottom-right)
        shape.setColor(C_AIM_ZONE);
        shape.rect(previewX + PREVIEW_W / 2f, previewY, PREVIEW_W / 2f, dividerY - previewY);

        // Vertical centre divider in joystick zone
        shape.setColor(1f, 1f, 1f, 0.18f);
        shape.rect(previewX + PREVIEW_W / 2f - 1f, previewY, 2f, dividerY - previewY);

        // Horizontal zone divider line
        shape.setColor(C_DIVIDER);
        shape.rect(previewX, dividerY - 2f, PREVIEW_W, 4f);

        // Preview border
        shape.setColor(C_PREVIEW_BORD);
        drawBorderRect(previewX, previewY, PREVIEW_W, PREVIEW_H, 2f);

        // Buttons
        drawRoundedBtn(btnSave,  C_SAVE);
        drawRoundedBtn(btnReset, C_RESET);

        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text ──────────────────────────────────────────────────────────────
        batch.begin();

        // Title
        font.getData().setScale(1.5f);
        font.setColor(new Color(0.55f, 0.85f, 1.00f, 1f));
        glyph.setText(font, "CONTROLS SETTINGS");
        font.draw(batch, glyph, sw / 2f - glyph.width / 2f, sh - 20f);
        font.getData().setScale(1f);

        // Zone percentage info
        int joystickPct = Math.round(split * 100);
        int firePct     = 100 - joystickPct;
        font.setColor(new Color(0.80f, 0.80f, 0.80f, 1f));
        String info = "Fire zone: " + firePct + "%   |   Joystick zone: " + joystickPct + "%";
        glyph.setText(font, info);
        font.draw(batch, glyph,
            sw / 2f - glyph.width / 2f,
            previewY + PREVIEW_H + 40f);

        // Zone labels inside preview
        font.setColor(new Color(1f, 0.85f, 0.65f, 0.90f));
        glyph.setText(font, "FIRE");
        font.draw(batch, glyph,
            previewX + (PREVIEW_W - glyph.width) / 2f,
            dividerY + (PREVIEW_H - (dividerY - previewY)) / 2f + glyph.height / 2f);

        font.setColor(new Color(0.6f, 1f, 0.65f, 0.85f));
        glyph.setText(font, "MOVE");
        font.draw(batch, glyph,
            previewX + (PREVIEW_W / 2f - glyph.width) / 2f,
            previewY + (dividerY - previewY) / 2f + glyph.height / 2f);

        font.setColor(new Color(0.5f, 0.75f, 1f, 0.85f));
        glyph.setText(font, "AIM");
        font.draw(batch, glyph,
            previewX + PREVIEW_W / 2f + (PREVIEW_W / 2f - glyph.width) / 2f,
            previewY + (dividerY - previewY) / 2f + glyph.height / 2f);

        // Drag hint
        font.setColor(new Color(0.55f, 0.55f, 0.55f, 0.85f));
        glyph.setText(font, "Drag the divider line to resize zones");
        font.draw(batch, glyph,
            sw / 2f - glyph.width / 2f,
            previewY - 16f);

        // Button labels
        font.setColor(C_TEXT);
        drawBtnLabel("SAVE & BACK", btnSave);
        drawBtnLabel("RESET",       btnReset);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(width, height);
        font  = fonts.body;
        recomputeLayout(width, height);
    }

    @Override
    public void hide() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (fonts != null) { fonts.dispose(); fonts = null; font = null; }
    }

    @Override public void pause()  { game.getMusicManager().pauseAll(); }
    @Override public void resume() { game.getMusicManager().resumeAll(); }
    @Override public void dispose() { hide(); }

    // ─── Layout helpers ───────────────────────────────────────────────────────

    private void recomputeLayout(int sw, int sh) {
        // Centre preview horizontally; leave room above for title and below for buttons
        previewX = sw / 2f - PREVIEW_W / 2f;
        previewY = sh / 2f - PREVIEW_H / 2f + 20f;

        // Buttons side-by-side below preview
        float gap    = 24f;
        float totalW = BTN_W * 2 + gap;
        float bx0    = sw / 2f - totalW / 2f;
        float btnY   = previewY - BTN_H - 36f;

        btnSave .set(bx0,              btnY, BTN_W, BTN_H);
        btnReset.set(bx0 + BTN_W + gap, btnY, BTN_W, BTN_H);
    }

    // ─── Draw helpers ─────────────────────────────────────────────────────────

    private void drawRoundedBtn(Rectangle r, Color fill) {
        float cr = BTN_CORNER;
        shape.setColor(fill);
        shape.rect(r.x + cr, r.y,       r.width - cr * 2, r.height);
        shape.rect(r.x,      r.y + cr,  r.width,           r.height - cr * 2);
        shape.circle(r.x + cr,           r.y + cr,            cr, 16);
        shape.circle(r.x + r.width - cr, r.y + cr,            cr, 16);
        shape.circle(r.x + cr,           r.y + r.height - cr,  cr, 16);
        shape.circle(r.x + r.width - cr, r.y + r.height - cr,  cr, 16);
        shape.setColor(1f, 1f, 1f, 0.28f);
        drawBorderRect(r.x, r.y, r.width, r.height, 1.5f);
    }

    private void drawBtnLabel(String text, Rectangle r) {
        glyph.setText(font, text);
        font.draw(batch, glyph,
            r.x + (r.width  - glyph.width)  / 2f,
            r.y + (r.height + glyph.height) / 2f);
    }

    private void drawBorderRect(float x, float y, float w, float h, float t) {
        shape.rect(x,         y,         w, t);
        shape.rect(x,         y + h - t, w, t);
        shape.rect(x,         y,         t, h);
        shape.rect(x + w - t, y,         t, h);
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void saveAndReturn() {
        ControlsConfig.save();
        game.setScreen(returnTo);
    }

    private void resetRatio() {
        ControlsConfig.resetToDefault();
    }

    // ─── Inner: touch handler ─────────────────────────────────────────────────

    private class TouchHandler extends InputAdapter {

        /** Whether the initial touchDown was on the divider. */
        private boolean onDivider = false;

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            int sh = Gdx.graphics.getHeight();
            // Convert screen Y (0=top) → HUD Y (0=bottom)
            float hudY = sh - screenY;
            float hudX = screenX;

            // ── Divider drag ──────────────────────────────────────────────────
            float dividerY = previewY + ControlsConfig.getSplitRatio() * PREVIEW_H;
            boolean onPreviewX = hudX >= previewX && hudX <= previewX + PREVIEW_W;
            if (onPreviewX && Math.abs(hudY - dividerY) <= DIVIDER_GRAB) {
                onDivider      = true;
                draggingDivider = true;
                return true;
            }

            // ── Button taps ───────────────────────────────────────────────────
            if (btnSave .contains(hudX, hudY)) { saveAndReturn(); return true; }
            if (btnReset.contains(hudX, hudY)) { resetRatio();    return true; }

            onDivider = false;
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!draggingDivider) return false;

            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            float hudY = sh - screenY;

            // Compute new split ratio from finger position
            float newSplit = (hudY - previewY) / PREVIEW_H;
            ControlsConfig.setSplitRatio(newSplit); // clamped internally
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (draggingDivider) {
                draggingDivider = false;
                onDivider       = false;
                return true;
            }
            return false;
        }
    }
}
