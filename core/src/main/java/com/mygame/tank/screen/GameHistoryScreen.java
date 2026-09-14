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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.mygame.tank.TankGame;
import com.mygame.tank.data.GameHistoryManager;
import com.mygame.tank.data.GameRecord;

/**
 * Game history screen.
 *
 * <p>Displays up to 20 most recent records from {@link GameHistoryManager}.
 * The table is centered based on screen proportions, each row has a colored
 * left stripe based on the result (green=WIN / red=LOSE) and a result "pill" label.
 * Above the table is a summary stats bar (total games / wins / losses / win rate).
 * A "Back" button in the top-left corner returns to {@link MenuScreen}.
 */
public class GameHistoryScreen implements Screen {

    // ─── Layout ───────────────────────────────────────────────────────────────

    private static final int   MAX_DISPLAY   = 20;
    private static final float ROW_HEIGHT     = 44f;
    private static final float HEADER_HEIGHT  = 46f;
    private static final float STATS_HEIGHT   = 40f;
    private static final float STRIPE_W       = 5f;
    private static final float ROW_GAP        = 3f;

    // Table sizing (relative to screen)
    private static final float MARGIN_FRAC    = 0.09f;
    private static final float MARGIN_MIN     = 48f;
    private static final float MARGIN_MAX     = 140f;
    private static final float TABLE_TOP_FRAC = 0.80f; // top of stats bar, fraction of screen height

    // Column fractions of table width (left edge of each column)
    private static final float COL_NUM_F      = 0.030f;
    private static final float COL_RESULT_F   = 0.110f;
    private static final float COL_TIME_F     = 0.400f;
    private static final float COL_DATE_F     = 0.620f;

    // Back button
    private static final float BTN_W = 170f;
    private static final float BTN_H = 52f;
    private static final float BTN_X = 36f;
    private static final float BTN_Y_FRAC = 0.91f;

    // ─── Colors ───────────────────────────────────────────────────────────────

    private static final Color C_WIN        = new Color(0.30f, 0.92f, 0.45f, 1f);
    private static final Color C_LOSE       = new Color(1.00f, 0.32f, 0.32f, 1f);
    private static final Color C_HEADER     = new Color(0.60f, 0.87f, 1.00f, 1f);
    private static final Color C_ROW_ODD    = new Color(0.09f, 0.11f, 0.17f, 0.82f);
    private static final Color C_ROW_EVEN   = new Color(0.07f, 0.09f, 0.14f, 0.82f);
    private static final Color C_ROW_HOVER  = new Color(0.16f, 0.20f, 0.32f, 0.92f);
    private static final Color C_HEADER_BG  = new Color(0.08f, 0.15f, 0.28f, 0.95f);
    private static final Color C_TABLE_BRD  = new Color(0.28f, 0.55f, 0.95f, 0.35f);
    private static final Color C_BTN_FILL   = new Color(0.10f, 0.28f, 0.55f, 0.90f);
    private static final Color C_BTN_HOV    = new Color(0.20f, 0.50f, 0.90f, 0.95f);
    private static final Color C_BORDER     = new Color(0.35f, 0.65f, 1.00f, 0.85f);
    private static final Color C_TEXT       = Color.WHITE;
    private static final Color C_SUBTEXT    = new Color(0.68f, 0.74f, 0.86f, 1f);
    private static final Color C_OVERLAY    = new Color(0f, 0f, 0f, 0.55f);
    private static final Color C_STATS_BG   = new Color(0.07f, 0.10f, 0.18f, 0.80f);
    private static final Color C_ACCENT     = new Color(0.55f, 0.85f, 1.00f, 1f);

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
    private int wins;
    private int losses;

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

        wins = 0;
        losses = 0;
        for (int i = 0; i < records.size; i++) {
            if ("WIN".equals(records.get(i).result)) wins++; else losses++;
        }

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

        // ── Table geometry (centered, scales with screen width) ────────────────
        float margin   = MathUtils.clamp(sw * MARGIN_FRAC, MARGIN_MIN, MARGIN_MAX);
        float tableX   = margin;
        float tableW   = sw - margin * 2f;
        float statsTop = sh * TABLE_TOP_FRAC;
        float statsY   = statsTop - STATS_HEIGHT;
        float headerY  = statsY - 14f - HEADER_HEIGHT;
        float tableTopY = headerY; // top edge of header row

        float colNum    = tableX + tableW * COL_NUM_F;
        float colResult = tableX + tableW * COL_RESULT_F;
        float colTime   = tableX + tableW * COL_TIME_F;
        float colDate   = tableX + tableW * COL_DATE_F;

        float winRate = records.size > 0 ? (100f * wins / records.size) : 0f;

        // ── Mouse ─────────────────────────────────────────────────────────────
        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouse);
        boolean hBack = btnBack.contains(mouse.x, mouse.y);
        int hoveredRow = -1;
        for (int i = 0; i < records.size; i++) {
            float rowY = headerY - (i + 1) * (ROW_HEIGHT + ROW_GAP);
            if (mouse.x >= tableX && mouse.x <= tableX + tableW
                && mouse.y >= rowY && mouse.y <= rowY + ROW_HEIGHT) {
                hoveredRow = i;
                break;
            }
        }

        // ── Filled shapes pass ──────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(C_OVERLAY);
        shape.rect(0, 0, sw, sh);

        // Stats bar background
        shape.setColor(C_STATS_BG);
        shape.rect(tableX, statsY, tableW, STATS_HEIGHT);

        // Header background
        shape.setColor(C_HEADER_BG);
        shape.rect(tableX, headerY, tableW, HEADER_HEIGHT);

        // Data rows
        for (int i = 0; i < records.size; i++) {
            GameRecord rec = records.get(i);
            float rowY = headerY - (i + 1) * (ROW_HEIGHT + ROW_GAP);
            boolean isWin = "WIN".equals(rec.result);

            Color rowBg = (i == hoveredRow) ? C_ROW_HOVER : (i % 2 == 0 ? C_ROW_EVEN : C_ROW_ODD);
            shape.setColor(rowBg);
            shape.rect(tableX, rowY, tableW, ROW_HEIGHT);

            // Left accent stripe by result
            shape.setColor(isWin ? C_WIN : C_LOSE);
            shape.rect(tableX, rowY, STRIPE_W, ROW_HEIGHT);

            // Result "pill" background
            String resTxt = isWin ? "WIN" : "LOSE";
            glyph.setText(fontBody, resTxt);
            float pillPadX = 14f;
            float pillW = glyph.width + pillPadX * 2f;
            float pillH = ROW_HEIGHT * 0.62f;
            float pillX = colResult;
            float pillY = rowY + (ROW_HEIGHT - pillH) / 2f;
            shape.setColor(isWin ? C_WIN.r : C_LOSE.r, isWin ? C_WIN.g : C_LOSE.g, isWin ? C_WIN.b : C_LOSE.b, 0.16f);
            shape.rect(pillX, pillY, pillW, pillH);
        }

        // Table outer border
        shape.setColor(C_TABLE_BRD);
        float tableBottomY = records.size > 0
            ? headerY - records.size * (ROW_HEIGHT + ROW_GAP)
            : headerY - ROW_HEIGHT * 2f;
        drawBorderRect(tableX, tableBottomY, tableW, (headerY + HEADER_HEIGHT) - tableBottomY, 2f);

        // Back button
        shape.setColor(hBack ? C_BTN_HOV : C_BTN_FILL);
        shape.rect(btnBack.x, btnBack.y, btnBack.width, btnBack.height);
        shape.setColor(hBack ? C_BORDER : C_TABLE_BRD);
        drawBorderRect(btnBack.x, btnBack.y, btnBack.width, btnBack.height, hBack ? 2.5f : 2f);

        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text pass ──────────────────────────────────────────────────────────
        batch.begin();

        // Title
        fontTitle.setColor(C_HEADER);
        glyph.setText(fontTitle, "GAME HISTORY");
        float titleY = sh * 0.965f;
        fontTitle.draw(batch, glyph, sw / 2f - glyph.width / 2f, titleY);

        // Thin accent underline beneath the title
        // (drawn via batch-less shape would need a new begin/end; skip for a single pass —
        //  approximate with a subtitle line instead)
        fontBody.getData().setScale(0.95f);
        fontBody.setColor(C_SUBTEXT);
        String subtitle = records.size + " recent games";
        glyph.setText(fontBody, subtitle);
        fontBody.draw(batch, glyph, sw / 2f - glyph.width / 2f, titleY - 34f);
        fontBody.getData().setScale(1f);

        // Stats bar text
        fontBody.getData().setScale(1.05f);
        String statsLine = String.format("Total: %d      Wins: %d      Losses: %d      Win rate: %.0f%%",
            records.size, wins, losses, winRate);
        fontBody.setColor(C_TEXT);
        fontBody.draw(batch, statsLine, tableX + 18f, statsY + STATS_HEIGHT * 0.65f);
        fontBody.getData().setScale(1f);

        // Header row labels
        fontBody.getData().setScale(1.08f);
        fontBody.setColor(C_HEADER);
        float headerTextY = headerY + HEADER_HEIGHT * 0.62f;
        fontBody.draw(batch, "#",      colNum,    headerTextY);
        fontBody.draw(batch, "Result", colResult, headerTextY);
        fontBody.draw(batch, "Time",   colTime,   headerTextY);
        fontBody.draw(batch, "Date",   colDate,   headerTextY);
        fontBody.getData().setScale(1f);

        // Empty state
        if (records.size == 0) {
            fontBody.setColor(new Color(0.6f, 0.6f, 0.6f, 1f));
            glyph.setText(fontBody, "No game records found.");
            fontBody.draw(batch, glyph, sw / 2f - glyph.width / 2f,
                headerY - ROW_HEIGHT);
            fontBody.setColor(new Color(0.45f, 0.45f, 0.50f, 1f));
            glyph.setText(fontBody, "Play a match to start tracking your results!");
            fontBody.draw(batch, glyph, sw / 2f - glyph.width / 2f,
                headerY - ROW_HEIGHT * 1.8f);
        }

        // Data rows text
        for (int i = 0; i < records.size; i++) {
            GameRecord rec = records.get(i);
            float rowY  = headerY - (i + 1) * (ROW_HEIGHT + ROW_GAP);
            float textY = rowY + ROW_HEIGHT * 0.65f;
            boolean isWin = "WIN".equals(rec.result);

            // STT
            fontBody.setColor(C_SUBTEXT);
            fontBody.draw(batch, String.valueOf(i + 1), colNum, textY);

            // Result label centered in its pill
            String resTxt = isWin ? "WIN" : "LOSE";
            fontBody.setColor(isWin ? C_WIN : C_LOSE);
            fontBody.draw(batch, resTxt, colResult + 14f, textY);

            // Time
            fontBody.setColor(C_TEXT);
            fontBody.draw(batch, rec.formattedTime(), colTime, textY);

            // Date
            fontBody.setColor(C_SUBTEXT);
            fontBody.draw(batch, rec.formattedDate(), colDate, textY);
        }

        // Back button label
        fontBody.setColor(C_TEXT);
        glyph.setText(fontBody, "Back");
        fontBody.draw(batch, glyph,
            btnBack.x + (btnBack.width - glyph.width) / 2f,
            btnBack.y + (btnBack.height + glyph.height) / 2f);

        // Footer hint
        fontBody.getData().setScale(0.85f);
        fontBody.setColor(new Color(0.45f, 0.48f, 0.55f, 1f));
        String footer = "Showing up to " + MAX_DISPLAY + " most recent games";
        glyph.setText(fontBody, footer);
        fontBody.draw(batch, glyph, sw / 2f - glyph.width / 2f, sh * 0.03f);
        fontBody.getData().setScale(1f);

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
