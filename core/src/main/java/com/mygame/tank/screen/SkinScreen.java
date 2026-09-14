package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.mygame.tank.TankGame;

/**
 * Placeholder skin-selection screen.
 * Uses the same camera + FreeType font pattern as {@link MenuScreen}.
 */
public class SkinScreen implements Screen {

    private static final float BTN_WIDTH     = 220f;
    private static final float BTN_HEIGHT    = 62f;
    private static final float CORNER_RADIUS = 12f;

    private static final Color C_BG         = new Color(0.07f, 0.09f, 0.13f, 1f);
    private static final Color C_BTN_FILL   = new Color(0.10f, 0.14f, 0.22f, 0.90f);
    private static final Color C_BTN_HOVER  = new Color(0.14f, 0.48f, 0.88f, 0.95f);
    private static final Color C_BORDER     = new Color(0.38f, 0.72f, 1.00f, 1.00f);
    private static final Color C_GLOW       = new Color(0.25f, 0.55f, 1.00f, 0.22f);
    private static final Color C_TITLE      = new Color(0.85f, 0.92f, 1.00f, 1f);
    private static final Color C_SUBTITLE   = new Color(0.60f, 0.68f, 0.80f, 1f);

    private final TankGame game;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private MenuFontCache.Fonts fonts;
    private GlyphLayout         glyph;

    private final Rectangle btnBack  = new Rectangle();
    private final Vector3   mouse    = new Vector3();

    public SkinScreen(TankGame game) {
        this.game = game;
    }

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

        layoutWidgets();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(C_BG.r, C_BG.g, C_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        shape.setProjectionMatrix(camera.combined);

        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;

        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouse);
        boolean hBack = btnBack.contains(mouse.x, mouse.y);

        // ── Button ────────────────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        drawRoundedButton(btnBack, hBack);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        drawGlowBorder(btnBack, hBack);
        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text ──────────────────────────────────────────────────────────────
        batch.begin();

        // Title
        fonts.title.setColor(C_TITLE);
        glyph.setText(fonts.title, "Skin Selection");
        fonts.title.draw(batch, glyph, (vw - glyph.width) / 2f, vh * 0.72f);

        // Subtitle
        fonts.body.setColor(C_SUBTITLE);
        glyph.setText(fonts.body, "(Coming soon)");
        fonts.body.draw(batch, glyph, (vw - glyph.width) / 2f, vh * 0.54f);

        // Back button label
        drawLabel("Back", btnBack);

        batch.end();

        if (Gdx.input.justTouched() && hBack) {
            game.setScreen(new MenuScreen(game));
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(width, height);
        layoutWidgets();
    }

    @Override
    public void hide() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (fonts != null) { fonts.dispose(); fonts = null; }
    }

    @Override public void pause()  { }
    @Override public void resume() { }
    @Override public void dispose() { hide(); }

    // ─── Layout ───────────────────────────────────────────────────────────────

    private void layoutWidgets() {
        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;
        btnBack.set((vw - BTN_WIDTH) / 2f, vh * 0.22f, BTN_WIDTH, BTN_HEIGHT);
    }

    // ─── Drawing helpers ──────────────────────────────────────────────────────

    private void drawRoundedButton(Rectangle r, boolean hover) {
        float cr = CORNER_RADIUS;
        if (hover) {
            float g = cr * 1.4f;
            shape.setColor(C_GLOW);
            shape.rect(r.x - g, r.y + cr, r.width + g * 2, r.height - cr * 2);
            shape.rect(r.x + cr, r.y - g, r.width - cr * 2, r.height + g * 2);
            shape.circle(r.x + cr,           r.y + cr,           cr + g, 18);
            shape.circle(r.x + r.width - cr, r.y + cr,           cr + g, 18);
            shape.circle(r.x + cr,           r.y + r.height - cr, cr + g, 18);
            shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr + g, 18);
        }
        shape.setColor(hover ? C_BTN_HOVER : C_BTN_FILL);
        shape.rect(r.x + cr, r.y,  r.width - cr * 2, r.height);
        shape.rect(r.x,  r.y + cr, r.width,           r.height - cr * 2);
        shape.circle(r.x + cr,           r.y + cr,           cr, 18);
        shape.circle(r.x + r.width - cr, r.y + cr,           cr, 18);
        shape.circle(r.x + cr,           r.y + r.height - cr, cr, 18);
        shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr, 18);
    }

    private void drawGlowBorder(Rectangle r, boolean hover) {
        shape.setColor(hover ? C_BORDER : new Color(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.55f));
        shape.rect(r.x, r.y, r.width, r.height);
        float in = 1.5f;
        shape.rect(r.x + in, r.y + in, r.width - in * 2, r.height - in * 2);
    }

    private void drawLabel(String text, Rectangle r) {
        BitmapFont font = fonts.body;
        font.setColor(Color.WHITE);
        glyph.setText(font, text);
        float tx = r.x + (r.width  - glyph.width)  / 2f;
        float ty = r.y + (r.height + glyph.height) / 2f;
        font.setColor(0f, 0f, 0f, 0.55f);
        font.draw(batch, glyph, tx + 1.5f, ty - 1.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, glyph, tx, ty);
    }
}
