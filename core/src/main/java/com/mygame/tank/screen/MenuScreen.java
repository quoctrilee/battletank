package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.mygame.tank.TankGame;
import com.mygame.tank.audio.MusicManager;

/**
 * Main menu screen.
 *
 * <p>Five vertically-stacked buttons centred on screen:
 * Start → {@link GameScreen},
 * Skin → {@link SkinScreen},
 * Controls → {@link ControlsSettingsScreen},
 * How to Play → {@link HowToPlayScreen},
 * History → {@link GameHistoryScreen}.
 *
 * <p>Music is played through the shared {@link MusicManager} so crossfading
 * to game music on transition works seamlessly.
 */
public class MenuScreen implements Screen {

    // ─── Layout ───────────────────────────────────────────────────────────────

    private static final float BUTTON_WIDTH  = 320f;
    private static final float BUTTON_HEIGHT = 72f;
    private static final float BUTTON_GAP    = 28f;
    private static final float CORNER_RADIUS = 14f;

    // ─── Colours ──────────────────────────────────────────────────────────────

    private static final Color C_BTN_FILL  = new Color(0.06f, 0.09f, 0.16f, 0.85f);
    private static final Color C_BTN_HOVER = new Color(0.14f, 0.48f, 0.88f, 0.95f);
    private static final Color C_BORDER    = new Color(0.38f, 0.72f, 1.00f, 1.00f);
    private static final Color C_GLOW      = new Color(0.25f, 0.55f, 1.00f, 0.22f);
    private static final Color C_TEXT      = Color.WHITE;
    private static final Color C_OVERLAY   = new Color(0f, 0f, 0f, 0.42f);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final TankGame game;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private Texture            background;

    private MenuFontCache.Fonts fonts;
    private GlyphLayout         glyph;

    private final Rectangle btnStart      = new Rectangle();
    private final Rectangle btnSkin       = new Rectangle();
    private final Rectangle btnControls   = new Rectangle();
    private final Rectangle btnHowToPlay  = new Rectangle();
    private final Rectangle btnHistory    = new Rectangle();
    private final Vector3   mouse         = new Vector3();

    /**
     * Short delay (seconds) after {@link #show()} during which touch input is ignored.
     * Prevents the same touch that caused a screen-transition INTO MenuScreen from
     * immediately activating a menu button (e.g. pressing "Back to Menu" in the
     * pause overlay then accidentally clicking "Skin" in the menu on the same tap).
     */
    private float touchGuard = 0f;
    private static final float TOUCH_GUARD_SECS = 0.15f;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public MenuScreen(TankGame game) {
        this.game = game;
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        Gdx.input.setInputProcessor(null);

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

        // Play menu music through the shared MusicManager (crossfade from game music if needed)
        game.getMusicManager().playTrack(MusicManager.MusicTrack.MENU);

        layoutButtons();

        // Ignore the first TOUCH_GUARD_SECS of touch input to prevent the tap that
        // triggered navigation TO this screen from immediately clicking a menu button.
        touchGuard = TOUCH_GUARD_SECS;
    }

    @Override
    public void render(float delta) {
        // Keep music fades ticking
        game.getMusicManager().update(delta);
        if (touchGuard > 0f) touchGuard -= delta;

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
        boolean hStart    = btnStart    .contains(mouse.x, mouse.y);
        boolean hSkin     = btnSkin     .contains(mouse.x, mouse.y);
        boolean hControls = btnControls .contains(mouse.x, mouse.y);
        boolean hHowTo    = btnHowToPlay.contains(mouse.x, mouse.y);
        boolean hHistory  = btnHistory  .contains(mouse.x, mouse.y);

        // ── Buttons ───────────────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        drawRoundedButton(btnStart,     hStart);
        drawRoundedButton(btnSkin,      hSkin);
        drawRoundedButton(btnControls,  hControls);
        drawRoundedButton(btnHowToPlay, hHowTo);
        drawRoundedButton(btnHistory,   hHistory);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        drawGlowBorder(btnStart,     hStart);
        drawGlowBorder(btnSkin,      hSkin);
        drawGlowBorder(btnControls,  hControls);
        drawGlowBorder(btnHowToPlay, hHowTo);
        drawGlowBorder(btnHistory,   hHistory);
        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Labels ────────────────────────────────────────────────────────────
        batch.begin();
        drawLabel("Start",       btnStart);
        drawLabel("Skin",        btnSkin);
        drawLabel("Controls",    btnControls);
        drawLabel("How to Play", btnHowToPlay);
        drawLabel("History",     btnHistory);
        batch.end();

        // ── Click handling ────────────────────────────────────────────────────
        if (Gdx.input.justTouched() && touchGuard <= 0f) {
            if      (hStart)    onStartClicked();
            else if (hSkin)     onSkinClicked();
            else if (hControls) onControlsClicked();
            else if (hHowTo)    onHowToPlayClicked();
            else if (hHistory)  onHistoryClicked();
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(width, height);
        fonts.body.setColor(C_TEXT);
        layoutButtons();
    }

    @Override
    public void hide() {
        // Music is managed by MusicManager — no local music to stop.
        if (batch      != null) { batch.dispose();      batch = null; }
        if (shape      != null) { shape.dispose();      shape = null; }
        if (background != null) { background.dispose(); background = null; }
        if (fonts      != null) { fonts.dispose();      fonts = null; }
    }

    @Override public void pause()  { game.getMusicManager().pauseAll(); }
    @Override public void resume() { game.getMusicManager().resumeAll(); }
    @Override public void dispose() { hide(); }

    // ─── Button actions ───────────────────────────────────────────────────────

    private void onStartClicked()    { game.setScreen(new GameScreen(game)); }
    private void onSkinClicked()     { game.setScreen(new SkinScreen(game)); }
    private void onControlsClicked() { game.setScreen(new ControlsSettingsScreen(game, this)); }
    private void onHowToPlayClicked(){ game.setScreen(new HowToPlayScreen(game)); }
    private void onHistoryClicked()  { game.setScreen(new GameHistoryScreen(game)); }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private void layoutButtons() {
        float vw     = camera.viewportWidth;
        float vh     = camera.viewportHeight;
        float totalH = 5 * BUTTON_HEIGHT + 4 * BUTTON_GAP;
        float bx     = (vw - BUTTON_WIDTH) / 2f;
        float topY   = (vh + totalH) / 2f - BUTTON_HEIGHT;

        btnStart    .set(bx, topY,                                      BUTTON_WIDTH, BUTTON_HEIGHT);
        btnSkin     .set(bx, topY -  (BUTTON_HEIGHT + BUTTON_GAP),      BUTTON_WIDTH, BUTTON_HEIGHT);
        btnControls .set(bx, topY - 2 * (BUTTON_HEIGHT + BUTTON_GAP),   BUTTON_WIDTH, BUTTON_HEIGHT);
        btnHowToPlay.set(bx, topY - 3 * (BUTTON_HEIGHT + BUTTON_GAP),   BUTTON_WIDTH, BUTTON_HEIGHT);
        btnHistory  .set(bx, topY - 4 * (BUTTON_HEIGHT + BUTTON_GAP),   BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    // ─── Drawing helpers ──────────────────────────────────────────────────────

    private void drawRoundedButton(Rectangle r, boolean hover) {
        float cr = CORNER_RADIUS;
        Color fill = hover ? C_BTN_HOVER : C_BTN_FILL;

        if (hover) {
            float g = cr * 1.5f;
            shape.setColor(C_GLOW);
            shape.rect(r.x - g, r.y + cr, r.width + g * 2, r.height - cr * 2);
            shape.rect(r.x + cr, r.y - g, r.width - cr * 2, r.height + g * 2);
            shape.circle(r.x + cr,           r.y + cr,            cr + g, 20);
            shape.circle(r.x + r.width - cr, r.y + cr,            cr + g, 20);
            shape.circle(r.x + cr,           r.y + r.height - cr, cr + g, 20);
            shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr + g, 20);
        }

        shape.setColor(fill);
        shape.rect(r.x + cr, r.y,       r.width - cr * 2, r.height);
        shape.rect(r.x,      r.y + cr,  r.width,          r.height - cr * 2);
        shape.circle(r.x + cr,           r.y + cr,            cr, 20);
        shape.circle(r.x + r.width - cr, r.y + cr,            cr, 20);
        shape.circle(r.x + cr,           r.y + r.height - cr,  cr, 20);
        shape.circle(r.x + r.width - cr, r.y + r.height - cr,  cr, 20);
    }

    private void drawGlowBorder(Rectangle r, boolean hover) {
        shape.setColor(hover ? C_BORDER : new Color(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.55f));
        shape.rect(r.x, r.y, r.width, r.height);
        float in = 1.5f;
        shape.rect(r.x + in, r.y + in, r.width - in * 2, r.height - in * 2);
    }

    private void drawLabel(String text, Rectangle r) {
        BitmapFont font = fonts.body;
        glyph.setText(font, text);
        float tx = r.x + (r.width  - glyph.width)  / 2f;
        float ty = r.y + (r.height + glyph.height) / 2f;

        float shadowOffset = Math.max(1f, glyph.height * 0.04f);
        font.setColor(0f, 0f, 0f, 0.45f);
        font.draw(batch, glyph, tx + shadowOffset, ty - shadowOffset);
        font.setColor(C_TEXT);
        font.draw(batch, glyph, tx, ty);
    }
}
