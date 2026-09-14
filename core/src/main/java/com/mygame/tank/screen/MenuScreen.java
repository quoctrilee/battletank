package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.mygame.tank.TankGame;

/**
 * Main menu screen.
 *
 * <p>Three vertically-stacked buttons centred on screen:
 * Start → {@link GameScreen}, Skin → {@link SkinScreen}, How to Play → {@link HowToPlayScreen}.
 *
 * <p>Uses {@link OrthographicCamera} (same as {@link GameScreen}) so the projection
 * is always in sync with the current window size. FreeType fonts via
 * {@link MenuFontCache} ensure crisp text at every resolution.
 */
public class MenuScreen implements Screen {

    // ─── Layout (world units, matched to camera viewport) ─────────────────────

    private static final float BUTTON_WIDTH  = 320f;
    private static final float BUTTON_HEIGHT = 72f;
    private static final float BUTTON_GAP    = 28f;
    private static final float CORNER_RADIUS = 14f;

    // ─── Colours ──────────────────────────────────────────────────────────────

    // Button normal state
    private static final Color C_BTN_FILL   = new Color(0.06f, 0.09f, 0.16f, 0.85f);
    // Button hover state — bright steel-blue
    private static final Color C_BTN_HOVER  = new Color(0.14f, 0.48f, 0.88f, 0.95f);
    // Glowing border
    private static final Color C_BORDER     = new Color(0.38f, 0.72f, 1.00f, 1.00f);
    // Outer glow (wider, more transparent)
    private static final Color C_GLOW       = new Color(0.25f, 0.55f, 1.00f, 0.22f);
    // Text
    private static final Color C_TEXT       = Color.WHITE;
    // Dim overlay on top of the background image
    private static final Color C_OVERLAY    = new Color(0f, 0f, 0f, 0.42f);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final TankGame game;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private Texture            background;

    private MenuFontCache.Fonts fonts;
    private GlyphLayout         glyph;

    private Music menuMusic;

    private final Rectangle btnStart      = new Rectangle();
    private final Rectangle btnSkin       = new Rectangle();
    private final Rectangle btnHowToPlay  = new Rectangle();
    private final Vector3   mouse         = new Vector3();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public MenuScreen(TankGame game) {
        this.game = game;
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        camera = new OrthographicCamera();
        camera.setToOrtho(false, sw, sh);
        camera.update();

        batch  = new SpriteBatch();
        shape  = new ShapeRenderer();
        glyph  = new GlyphLayout();

        background = new Texture(Gdx.files.internal("menu/background.png"));
        background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        fonts = MenuFontCache.create(sw, sh);
        fonts.body.setColor(C_TEXT);

        menuMusic = Gdx.audio.newMusic(Gdx.files.internal("music/menu/menu.mp3"));
        menuMusic.setLooping(true);
        menuMusic.setVolume(0.55f);
        menuMusic.play();

        layoutButtons();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        shape.setProjectionMatrix(camera.combined);

        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;

        // ── Background ────────────────────────────────────────────────────────
        batch.begin();
        float bgW = background.getWidth();
        float bgH = background.getHeight();
        float sc  = Math.max(vw / bgW, vh / bgH);
        batch.draw(background,
            (vw - bgW * sc) / 2f, (vh - bgH * sc) / 2f,
            bgW * sc, bgH * sc);
        batch.end();

        // ── Dim overlay ───────────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(C_OVERLAY);
        shape.rect(0, 0, vw, vh);
        shape.end();

        // ── Mouse → world ─────────────────────────────────────────────────────
        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouse);
        boolean hStart  = btnStart    .contains(mouse.x, mouse.y);
        boolean hSkin   = btnSkin     .contains(mouse.x, mouse.y);
        boolean hHowTo  = btnHowToPlay.contains(mouse.x, mouse.y);

        // ── Buttons ───────────────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        drawRoundedButton(btnStart,     hStart);
        drawRoundedButton(btnSkin,      hSkin);
        drawRoundedButton(btnHowToPlay, hHowTo);
        shape.end();

        // Glow border (wider rect drawn first, then inner border)
        shape.begin(ShapeRenderer.ShapeType.Line);
        drawGlowBorder(btnStart,     hStart);
        drawGlowBorder(btnSkin,      hSkin);
        drawGlowBorder(btnHowToPlay, hHowTo);
        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Labels ────────────────────────────────────────────────────────────
        batch.begin();
        drawLabel("Start",       btnStart);
        drawLabel("Skin",        btnSkin);
        drawLabel("How to Play", btnHowToPlay);
        batch.end();

        // ── Click handling ────────────────────────────────────────────────────
        if (Gdx.input.justTouched()) {
            if (hStart)       onStartClicked();
            else if (hSkin)   onSkinClicked();
            else if (hHowTo)  onHowToPlayClicked();
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        // Regenerate fonts for the new size, then re-layout
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(width, height);
        fonts.body.setColor(C_TEXT);
        layoutButtons();
    }

    @Override
    public void hide() {
        if (menuMusic != null) { menuMusic.stop(); menuMusic.dispose(); menuMusic = null; }
        if (batch     != null) { batch.dispose();                       batch = null; }
        if (shape     != null) { shape.dispose();                       shape = null; }
        if (background!= null) { background.dispose();                  background = null; }
        if (fonts     != null) { fonts.dispose();                       fonts = null; }
    }

    @Override public void pause()  { }
    @Override public void resume() { }
    @Override public void dispose() { hide(); }

    // ─── Button actions ───────────────────────────────────────────────────────

    private void onStartClicked()     { game.setScreen(new GameScreen()); }
    private void onSkinClicked()      { game.setScreen(new SkinScreen(game)); }
    private void onHowToPlayClicked() { game.setScreen(new HowToPlayScreen(game)); }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private void layoutButtons() {
        float vw     = camera.viewportWidth;
        float vh     = camera.viewportHeight;
        float totalH = 3 * BUTTON_HEIGHT + 2 * BUTTON_GAP;
        float bx     = (vw - BUTTON_WIDTH) / 2f;
        float topY   = (vh + totalH) / 2f - BUTTON_HEIGHT;

        btnStart    .set(bx, topY,                                     BUTTON_WIDTH, BUTTON_HEIGHT);
        btnSkin     .set(bx, topY -  (BUTTON_HEIGHT + BUTTON_GAP),     BUTTON_WIDTH, BUTTON_HEIGHT);
        btnHowToPlay.set(bx, topY - 2 * (BUTTON_HEIGHT + BUTTON_GAP),  BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    // ─── Drawing helpers ──────────────────────────────────────────────────────

    /**
     * Draws a pill-shaped (rounded rectangle) button fill.
     *
     * <p>LibGDX ShapeRenderer has no built-in rounded rect, so we approximate one with:
     * <ul>
     *   <li>A wide centre rectangle</li>
     *   <li>A tall centre rectangle</li>
     *   <li>Four filled circles at each corner</li>
     * </ul>
     */
    private void drawRoundedButton(Rectangle r, boolean hover) {
        float cr = CORNER_RADIUS;
        Color fill = hover ? C_BTN_HOVER : C_BTN_FILL;

        // Optional outer glow on hover
        if (hover) {
            float g = cr * 1.5f;
            shape.setColor(C_GLOW);
            // glow rects
            shape.rect(r.x - g, r.y + cr, r.width + g * 2, r.height - cr * 2);
            shape.rect(r.x + cr, r.y - g, r.width - cr * 2, r.height + g * 2);
            // glow circles at corners
            shape.circle(r.x + cr,           r.y + cr,           cr + g, 20);
            shape.circle(r.x + r.width - cr, r.y + cr,           cr + g, 20);
            shape.circle(r.x + cr,           r.y + r.height - cr, cr + g, 20);
            shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr + g, 20);
        }

        shape.setColor(fill);
        // Centre fill
        shape.rect(r.x + cr, r.y,  r.width - cr * 2, r.height);
        shape.rect(r.x,  r.y + cr, r.width,           r.height - cr * 2);
        // Corner circles
        shape.circle(r.x + cr,           r.y + cr,           cr, 20);
        shape.circle(r.x + r.width - cr, r.y + cr,           cr, 20);
        shape.circle(r.x + cr,           r.y + r.height - cr, cr, 20);
        shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr, 20);
    }

    /**
     * Draws a crisp border around the rounded button using line segments + arc arcs.
     * We approximate it with a slightly inset line-mode rect for simplicity.
     */
    private void drawGlowBorder(Rectangle r, boolean hover) {
        shape.setColor(hover ? C_BORDER : new Color(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.55f));
        // Two-pixel thick feel: draw at r and slightly inset
        shape.rect(r.x, r.y, r.width, r.height);
        float in = 1.5f;
        shape.rect(r.x + in, r.y + in, r.width - in * 2, r.height - in * 2);
    }

    /** Draws centred, shadowed text inside button {@code r}. */
    private void drawLabel(String text, Rectangle r) {
    BitmapFont font = fonts.body;
    glyph.setText(font, text);
    float tx = r.x + (r.width  - glyph.width)  / 2f;
    float ty = r.y + (r.height + glyph.height) / 2f;

    // Offset nhỏ, tỉ lệ theo chiều cao chữ — tránh "tách đôi" ở màn nhỏ
    float shadowOffset = Math.max(1f, glyph.height * 0.04f);

    font.setColor(0f, 0f, 0f, 0.45f);
    font.draw(batch, glyph, tx + shadowOffset, ty - shadowOffset);

    font.setColor(C_TEXT);
    font.draw(batch, glyph, tx, ty);
}
}
