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
import com.badlogic.gdx.utils.Array;
import com.mygame.tank.TankGame;
import com.mygame.tank.data.GameHistoryManager;
import com.mygame.tank.data.GameRecord;

/**
 * Màn hình lịch sử trò chơi.
 *
 * <p>Hiển thị tối đa 20 bản ghi gần nhất từ {@link GameHistoryManager}.
 * Mỗi dòng gồm: STT | Kết quả (WIN=xanh / LOSE=đỏ) | Thời gian chơi | Ngày giờ.
 * Nút "← Quay lại" ở góc trên trái để về {@link MenuScreen}.
 */
public class GameHistoryScreen implements Screen {

    // ─── Layout ───────────────────────────────────────────────────────────────

    private static final int   MAX_DISPLAY  = 20;
    private static final float ROW_HEIGHT   = 42f;
    private static final float TABLE_X      = 60f;
    private static final float TABLE_TOP_Y  = 0.82f; // fraction of screen height
    private static final float COL_NUM      = 0f;
    private static final float COL_RESULT   = 50f;
    private static final float COL_TIME     = 200f;
    private static final float COL_DATE     = 380f;

    // Back button
    private static final float BTN_W = 180f;
    private static final float BTN_H = 52f;
    private static final float BTN_X = 40f;
    private static final float BTN_Y_FRAC = 0.90f;

    // ─── Colors ───────────────────────────────────────────────────────────────

    private static final Color C_WIN      = new Color(0.20f, 0.90f, 0.35f, 1f);
    private static final Color C_LOSE     = new Color(1.00f, 0.22f, 0.22f, 1f);
    private static final Color C_HEADER   = new Color(0.55f, 0.85f, 1.00f, 1f);
    private static final Color C_ROW_ODD  = new Color(0.08f, 0.10f, 0.16f, 0.80f);
    private static final Color C_ROW_EVEN = new Color(0.06f, 0.08f, 0.13f, 0.80f);
    private static final Color C_BTN_FILL = new Color(0.10f, 0.28f, 0.55f, 0.90f);
    private static final Color C_BTN_HOV  = new Color(0.20f, 0.50f, 0.90f, 0.95f);
    private static final Color C_BORDER   = new Color(0.30f, 0.60f, 1.00f, 0.75f);
    private static final Color C_TEXT     = Color.WHITE;
    private static final Color C_OVERLAY  = new Color(0f, 0f, 0f, 0.50f);

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final TankGame game;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private MenuFontCache.Fonts fonts;
    private BitmapFont         fontTitle;
    private BitmapFont         fontBody;
    private GlyphLayout        glyph;

    private Array<GameRecord> records;

    private final Rectangle btnBack = new Rectangle();
    private final Vector3   mouse   = new Vector3();

    private float touchGuard = 0f;
    private static final float TOUCH_GUARD_SECS = 0.15f;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public GameHistoryScreen(TankGame game) {
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

        batch = new SpriteBatch();
        shape = new ShapeRenderer();
        glyph = new GlyphLayout();

        // Use MenuFontCache for consistent styling
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(sw, sh);
        fontTitle = fonts.title;
        fontBody  = fonts.body;

        records = new GameHistoryManager().loadAll();
        if (records.size > MAX_DISPLAY) records.truncate(MAX_DISPLAY);

        game.getMusicManager().playTrack(com.mygame.tank.audio.MusicManager.MusicTrack.MENU);

        layoutButton(sw, sh);
        touchGuard = TOUCH_GUARD_SECS;
    }

    @Override
    public void render(float delta) {
        game.getMusicManager().update(delta);
        if (touchGuard > 0f) touchGuard -= delta;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.04f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        shape.setProjectionMatrix(camera.combined);

        // ── Dark overlay base ──────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(C_OVERLAY);
        shape.rect(0, 0, sw, sh);

        // ── Mouse ─────────────────────────────────────────────────────────────
        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouse);
        boolean hBack = btnBack.contains(mouse.x, mouse.y);

        // ── Back button ───────────────────────────────────────────────────────
        shape.setColor(hBack ? C_BTN_HOV : C_BTN_FILL);
        shape.rect(btnBack.x, btnBack.y, btnBack.width, btnBack.height);

        // ── Table rows ────────────────────────────────────────────────────────
        float tableTopY = sh * TABLE_TOP_Y;
        for (int i = 0; i < records.size; i++) {
            float rowY = tableTopY - (i + 1) * ROW_HEIGHT;
            shape.setColor(i % 2 == 0 ? C_ROW_ODD : C_ROW_EVEN);
            shape.rect(TABLE_X - 10, rowY - 2, sw - TABLE_X * 2 + 20, ROW_HEIGHT - 4);
        }

        // ── Table header background ────────────────────────────────────────────
        shape.setColor(0.10f, 0.18f, 0.35f, 0.90f);
        shape.rect(TABLE_X - 10, tableTopY - 2, sw - TABLE_X * 2 + 20, ROW_HEIGHT - 4);

        // ── Border around back button ──────────────────────────────────────────
        shape.setColor(C_BORDER);
        drawBorderRect(btnBack.x, btnBack.y, btnBack.width, btnBack.height, 2f);

        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text pass ──────────────────────────────────────────────────────────
        batch.begin();

        // Title
        fontTitle.setColor(C_HEADER);
        glyph.setText(fontTitle, "GAME HISTORY");
        fontTitle.draw(batch, glyph, sw / 2f - glyph.width / 2f, sh * 0.95f);

        // Header row
        fontBody.getData().setScale(1.1f);
        fontBody.setColor(C_HEADER);
        fontBody.draw(batch, "#",       TABLE_X + COL_NUM,    tableTopY + ROW_HEIGHT * 0.55f);
        fontBody.draw(batch, "Result",  TABLE_X + COL_RESULT, tableTopY + ROW_HEIGHT * 0.55f);
        fontBody.draw(batch, "Time",    TABLE_X + COL_TIME,   tableTopY + ROW_HEIGHT * 0.55f);
        fontBody.draw(batch, "Date",    TABLE_X + COL_DATE,   tableTopY + ROW_HEIGHT * 0.55f);
        fontBody.getData().setScale(1f);

        // Empty state
        if (records.size == 0) {
            fontBody.setColor(new Color(0.6f, 0.6f, 0.6f, 1f));
            glyph.setText(fontBody, "No game records found.");
            fontBody.draw(batch, glyph, sw / 2f - glyph.width / 2f,
                tableTopY - ROW_HEIGHT * 1.5f);
        }

        // Data rows
        for (int i = 0; i < records.size; i++) {
            GameRecord rec = records.get(i);
            float rowY    = tableTopY - (i + 1) * ROW_HEIGHT;
            float textY   = rowY + ROW_HEIGHT * 0.62f;
            boolean isWin = "WIN".equals(rec.result);

            // STT
            fontBody.setColor(C_TEXT);
            fontBody.draw(batch, String.valueOf(i + 1), TABLE_X + COL_NUM, textY);

            // Result — WIN=green, LOSE=red (clean text without icons)
            fontBody.setColor(isWin ? C_WIN : C_LOSE);
            fontBody.draw(batch, isWin ? "WIN" : "LOSE", TABLE_X + COL_RESULT, textY);

            // Time
            fontBody.setColor(C_TEXT);
            fontBody.draw(batch, rec.formattedTime(), TABLE_X + COL_TIME, textY);

            // Date
            fontBody.setColor(new Color(0.75f, 0.80f, 0.90f, 1f));
            fontBody.draw(batch, rec.formattedDate(), TABLE_X + COL_DATE, textY);
        }

        // Back button label
        fontBody.setColor(C_TEXT);
        glyph.setText(fontBody, "Back");
        fontBody.draw(batch, glyph,
            btnBack.x + (btnBack.width - glyph.width) / 2f,
            btnBack.y + (btnBack.height + glyph.height) / 2f);

        batch.end();

        // ── Click handling ────────────────────────────────────────────────────
        if (Gdx.input.justTouched() && touchGuard <= 0f && hBack) {
            game.setScreen(new MenuScreen(game));
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        if (fonts != null) fonts.dispose();
        fonts = MenuFontCache.create(width, height);
        fontTitle = fonts.title;
        fontBody  = fonts.body;
        layoutButton(width, height);
    }

    @Override
    public void hide() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (fonts != null) { fonts.dispose(); fonts = null; }
        fontTitle = null;
        fontBody  = null;
    }

    @Override public void pause()   { game.getMusicManager().pauseAll(); }
    @Override public void resume()  { game.getMusicManager().resumeAll(); }
    @Override public void dispose() { hide(); }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void layoutButton(int sw, int sh) {
        btnBack.set(BTN_X, sh * BTN_Y_FRAC - BTN_H / 2f, BTN_W, BTN_H);
    }

    private void drawBorderRect(float x, float y, float w, float h, float t) {
        shape.rect(x,         y,         w, t);
        shape.rect(x,         y + h - t, w, t);
        shape.rect(x,         y,         t, h);
        shape.rect(x + w - t, y,         t, h);
    }
}
