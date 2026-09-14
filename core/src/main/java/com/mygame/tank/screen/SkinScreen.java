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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.mygame.tank.SkinManager;
import com.mygame.tank.TankGame;

/**
 * Màn hình chọn Skin/Tank body — Tối ưu cho mobile & desktop.
 *
 * <p>Hiển thị lưới 1 hàng x 3 cột:
 * <ul>
 *   <li>Ảnh preview rõ ràng, sắc nét cho cả 3 tank (không bị che tối đen)</li>
 *   <li>Nút "Select" / "Selected" dưới mỗi ô</li>
 *   <li>Hiệu ứng chuyển skin mượt mà với pop/bounce animation và lerp độc lập fps</li>
 *   <li>Tank được chọn có viền vàng kim loại sáng chói, hào quang phát sáng (pulsing glow)</li>
 *   <li>Touch target lớn phù hợp thao tác cảm ứng trên điện thoại</li>
 *   <li>Tích hợp SkinManager lưu trữ skin đã chọn xuyên suốt vào game</li>
 * </ul>
 */
public class SkinScreen implements Screen {

    private static final int TANK_COUNT = SkinManager.BODY_PATHS.length;

    // ─── Bảng màu UI hiện đại ──────────────────────────────────────────────────
    private static final Color C_BG_TOP       = new Color(0.06f, 0.08f, 0.14f, 1f);
    private static final Color C_BG_BOTTOM    = new Color(0.03f, 0.04f, 0.08f, 1f);

    // Card bình thường (chưa chọn)
    private static final Color C_CARD_NORMAL  = new Color(0.09f, 0.13f, 0.22f, 0.92f);
    private static final Color C_CARD_HOVER   = new Color(0.13f, 0.18f, 0.28f, 0.96f);
    private static final Color C_BORDER_DIM   = new Color(0.24f, 0.38f, 0.56f, 0.65f);

    // Card được chọn
    private static final Color C_CARD_SEL     = new Color(0.12f, 0.16f, 0.27f, 0.98f);
    private static final Color C_GOLD_BORDER  = new Color(1.00f, 0.86f, 0.24f, 1.00f);
    private static final Color C_GOLD_GLOW    = new Color(1.00f, 0.76f, 0.12f, 0.40f);

    // Nút Select (chưa chọn)
    private static final Color C_BTN_NORMAL   = new Color(0.13f, 0.38f, 0.72f, 0.95f);
    private static final Color C_BTN_HOVER    = new Color(0.18f, 0.52f, 0.95f, 1.00f);
    private static final Color C_BTN_BORDER   = new Color(0.35f, 0.65f, 0.95f, 0.80f);

    // Nút Selected (đã chọn)
    private static final Color C_BTN_SEL_FILL = new Color(0.95f, 0.75f, 0.10f, 1.00f);
    private static final Color C_BTN_SEL_GLOW = new Color(1.00f, 0.88f, 0.35f, 0.60f);

    // Nút Back
    private static final Color C_BACK_FILL    = new Color(0.10f, 0.14f, 0.24f, 0.90f);
    private static final Color C_BACK_HOVER   = new Color(0.16f, 0.44f, 0.82f, 0.98f);
    private static final Color C_BACK_BORDER  = new Color(0.32f, 0.62f, 0.92f, 0.90f);

    // Typography
    private static final Color C_TITLE        = new Color(0.95f, 0.98f, 1.00f, 1f);
    private static final Color C_NAME_SEL     = new Color(1.00f, 0.88f, 0.32f, 1f);
    private static final Color C_NAME_NORMAL  = new Color(0.72f, 0.82f, 0.95f, 0.90f);

    // ─── Fields ───────────────────────────────────────────────────────────────
    private final TankGame game;

    private OrthographicCamera camera;
    private SpriteBatch        batch;
    private ShapeRenderer      shape;
    private MenuFontCache.Fonts fonts;
    private GlyphLayout        glyph;

    private final Texture[]   bodyTextures = new Texture[TANK_COUNT];
    private final Rectangle[] cardRects    = new Rectangle[TANK_COUNT];
    private final Rectangle[] selectBtns   = new Rectangle[TANK_COUNT];
    private final Rectangle   btnBack      = new Rectangle();
    private final Vector3     touchPoint   = new Vector3();

    // ─── Layout metrics ───────────────────────────────────────────────────────
    private float cardBaseW, cardBaseH, imgBaseSize, btnBaseW, btnBaseH, cornerRadius;

    // ─── Animation States ─────────────────────────────────────────────────────
    /** Animated scale factor for each card (1.05 for selected, 0.96 for unselected) */
    private final float[] animScale   = new float[TANK_COUNT];
    /** Selection factor [0 = unselected, 1 = fully selected] */
    private final float[] selectT     = new float[TANK_COUNT];
    /** Image brightness/alpha factor [0.85 = unselected crisp, 1.0 = selected] */
    private final float[] imgAlpha    = new float[TANK_COUNT];

    private float pulseTime  = 0f;
    private float backHoverT = 0f;
    private int   selectedIndex = 0;

    public SkinScreen(TankGame game) {
        this.game = game;
        this.selectedIndex = SkinManager.getSelectedIndex();
        for (int i = 0; i < TANK_COUNT; i++) {
            cardRects[i]  = new Rectangle();
            selectBtns[i] = new Rectangle();
            boolean isSel = (i == selectedIndex);
            animScale[i]  = isSel ? 1.05f : 0.96f;
            selectT[i]    = isSel ? 1.0f  : 0.0f;
            imgAlpha[i]   = isSel ? 1.0f  : 0.85f;
        }
    }

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
        fonts = MenuFontCache.create(sw, sh);

        for (int i = 0; i < TANK_COUNT; i++) {
            Texture tex = new Texture(Gdx.files.internal(SkinManager.getBodyPath(i)));
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            bodyTextures[i] = tex;
        }

        layoutWidgets();
    }

    @Override
    public void render(float delta) {
        // Cập nhật thời gian pulse
        pulseTime += delta;
        float pulse = 0.5f + 0.5f * MathUtils.sin(pulseTime * MathUtils.PI2 * 1.5f);

        // Smooth spring/lerp độc lập framerate
        float lerpFactor = 1f - (float) Math.pow(0.0001f, delta * 4f);
        for (int i = 0; i < TANK_COUNT; i++) {
            boolean isSel = (i == selectedIndex);
            float targetScale = isSel ? 1.04f : 0.96f;
            float targetSelT  = isSel ? 1.0f  : 0.0f;
            float targetAlpha = isSel ? 1.0f  : 0.85f;

            animScale[i] = lerp(animScale[i], targetScale, lerpFactor);
            selectT[i]   = lerp(selectT[i],   targetSelT,  lerpFactor);
            imgAlpha[i]  = lerp(imgAlpha[i],  targetAlpha, lerpFactor);
        }

        // Đọc toạ độ chạm / chuột
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(touchPoint);
        float mx = touchPoint.x;
        float my = touchPoint.y;

        boolean hBack = btnBack.contains(mx, my);
        backHoverT = lerp(backHoverT, hBack ? 1f : 0f, lerpFactor);

        boolean[] hCard   = new boolean[TANK_COUNT];
        boolean[] hSelect = new boolean[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            hCard[i]   = cardRects[i].contains(mx, my);
            hSelect[i] = selectBtns[i].contains(mx, my);
        }

        // Xoá màn hình
        Gdx.gl.glClearColor(C_BG_TOP.r, C_BG_TOP.g, C_BG_TOP.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;

        batch.setProjectionMatrix(camera.combined);
        shape.setProjectionMatrix(camera.combined);

        // ── 1. VẼ NỀN GRADIENT CỦA MÀN HÌNH ──────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.rect(0, 0, vw, vh, C_BG_BOTTOM, C_BG_BOTTOM, C_BG_TOP, C_BG_TOP);

        // ── 2. VẼ CARD BACKGROUND VÀ NÚT BẤM ─────────────────────────────────────
        for (int i = 0; i < TANK_COUNT; i++) {
            Rectangle r = cardRects[i];
            float sT = selectT[i]; // [0..1]

            // Hiệu ứng hào quang vàng (Aura Glow) cho tank đang chọn
            if (sT > 0.01f) {
                float glowAlpha = C_GOLD_GLOW.a * sT * (0.8f + 0.2f * pulse);
                float glowSpread = cornerRadius * (1.2f + 0.35f * pulse);
                shape.setColor(C_GOLD_GLOW.r, C_GOLD_GLOW.g, C_GOLD_GLOW.b, glowAlpha);
                drawRoundedRect(shape, r.x - glowSpread, r.y - glowSpread,
                                r.width + glowSpread * 2, r.height + glowSpread * 2,
                                cornerRadius + glowSpread);
            }

            // Nền thẻ card
            Color cardColor = lerpColor(hCard[i] && sT < 0.5f ? C_CARD_HOVER : C_CARD_NORMAL, C_CARD_SEL, sT);
            shape.setColor(cardColor);
            drawRoundedRect(shape, r.x, r.y, r.width, r.height, cornerRadius);

            // Nền nút Select/Selected
            Rectangle b = selectBtns[i];
            Color btnNormalColor = (hSelect[i] || hCard[i]) ? C_BTN_HOVER : C_BTN_NORMAL;
            Color btnColor = lerpColor(btnNormalColor, C_BTN_SEL_FILL, sT);
            shape.setColor(btnColor);
            drawRoundedRect(shape, b.x, b.y, b.width, b.height, cornerRadius * 0.7f);
        }

        // Nền nút Back
        Color backFill = lerpColor(C_BACK_FILL, C_BACK_HOVER, backHoverT);
        shape.setColor(backFill);
        drawRoundedRect(shape, btnBack.x, btnBack.y, btnBack.width, btnBack.height, cornerRadius * 0.8f);

        shape.end();

        // ── 3. VẼ ĐƯỜNG VIỀN CARD VÀ NÚT (LINE MODE) ────────────────────────────
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < TANK_COUNT; i++) {
            Rectangle r = cardRects[i];
            float sT = selectT[i];

            if (sT > 0.05f) {
                // Viền vàng rực rỡ dày 3 lớp khi được chọn
                float borderAlpha = (0.85f + 0.15f * pulse) * sT;
                shape.setColor(C_GOLD_BORDER.r, C_GOLD_BORDER.g, C_GOLD_BORDER.b, borderAlpha);
                for (float off = 0f; off <= 2.5f; off += 1f) {
                    shape.rect(r.x - off, r.y - off, r.width + off * 2, r.height + off * 2);
                }
            } else {
                // Viền xanh mờ nhẹ nhàng, tinh tế
                shape.setColor(C_BORDER_DIM);
                shape.rect(r.x, r.y, r.width, r.height);
            }

            // Viền nút
            Rectangle b = selectBtns[i];
            if (sT > 0.5f) {
                shape.setColor(C_BTN_SEL_GLOW.r, C_BTN_SEL_GLOW.g, C_BTN_SEL_GLOW.b, 0.8f);
            } else {
                shape.setColor(C_BTN_BORDER);
            }
            shape.rect(b.x, b.y, b.width, b.height);
        }

        // Viền nút Back
        float backBorderA = lerp(0.60f, 1.00f, backHoverT);
        shape.setColor(C_BACK_BORDER.r, C_BACK_BORDER.g, C_BACK_BORDER.b, backBorderA);
        shape.rect(btnBack.x, btnBack.y, btnBack.width, btnBack.height);
        shape.rect(btnBack.x + 1f, btnBack.y + 1f, btnBack.width - 2f, btnBack.height - 2f);

        shape.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── 4. VẼ ẢNH TANK PREVIEW (SPRITEBATCH) ──────────────────────────────────
        // LƯU Ý QUAN TRỌNG: KHÔNG vẽ bất kỳ ô vuông đen nào đè lên ảnh!
        // Cả 3 tank đều sáng rõ, sắc nét, tank chưa chọn chỉ giảm nhẹ saturation/alpha.
        batch.begin();
        for (int i = 0; i < TANK_COUNT; i++) {
            Rectangle card = cardRects[i];
            float sT = selectT[i];
            float curImgSize = imgBaseSize * (0.95f + 0.05f * sT);
            float imgX = card.x + (card.width - curImgSize) / 2f;
            float imgY = card.y + card.height - curImgSize - card.height * 0.07f;

            if (i == selectedIndex) {
                // Tank đang chọn: 100% rực rỡ, sắc nét
                batch.setColor(1f, 1f, 1f, 1f);
            } else {
                // Tank chưa chọn: màu sắc rõ ràng (85% sáng), hoàn toàn thấy rõ chi tiết
                float a = imgAlpha[i];
                batch.setColor(0.85f, 0.88f, 0.94f, a);
            }
            batch.draw(bodyTextures[i], imgX, imgY, curImgSize, curImgSize);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        // ── 5. VẼ TEXT & LABELS ──────────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();

        // Tiêu đề trang
        fonts.title.setColor(C_TITLE);
        glyph.setText(fonts.title, "SELECT TANK");
        fonts.title.draw(batch, glyph, (vw - glyph.width) / 2f, vh * 0.93f);

        // Tên Tank và Nút Select
        for (int i = 0; i < TANK_COUNT; i++) {
            Rectangle card = cardRects[i];
            Rectangle btn  = selectBtns[i];
            float sT = selectT[i];

            // Tên tank (Vàng kim khi chọn, xanh bạc dịu khi chưa chọn)
            Color nameColor = lerpColor(C_NAME_NORMAL, C_NAME_SEL, sT);
            fonts.body.setColor(nameColor);
            glyph.setText(fonts.body, SkinManager.getBodyName(i));
            float tx = card.x + (card.width - glyph.width) / 2f;
            float ty = btn.y + btn.height + card.height * 0.055f + glyph.height;
            fonts.body.draw(batch, glyph, tx, ty);

            // Chữ trong nút
            String btnText = (i == selectedIndex) ? "SELECTED" : "SELECT";
            Color textColor = (i == selectedIndex) ? new Color(0.12f, 0.10f, 0.02f, 1f) : Color.WHITE;
            drawCentredLabel(btnText, btn, textColor);
        }

        // Chữ nút Back
        drawCentredLabel("< BACK", btnBack, Color.WHITE);

        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── 6. XỬ LÝ TOUCH / CLICK ───────────────────────────────────────────────
        if (Gdx.input.justTouched()) {
            if (hBack) {
                game.setScreen(new MenuScreen(game));
                return;
            }

            for (int i = 0; i < TANK_COUNT; i++) {
                // Nhấn vào nút hoặc bất cứ đâu trên thẻ card đều kích hoạt chọn skin
                if (hSelect[i] || hCard[i]) {
                    if (selectedIndex != i) {
                        selectedIndex = i;
                        // Lưu skin đã chọn ngay lập tức vào SkinManager
                        SkinManager.setSelectedIndex(i);
                        // Pop effect: scale tức thì nhẹ để tạo độ nảy
                        animScale[i] = 1.10f;
                    }
                    break;
                }
            }
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
        for (int i = 0; i < TANK_COUNT; i++) {
            if (bodyTextures[i] != null) {
                bodyTextures[i].dispose();
                bodyTextures[i] = null;
            }
        }
        if (batch != null) { batch.dispose(); batch = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (fonts != null) { fonts.dispose(); fonts = null; }
    }

    @Override public void pause()   { }
    @Override public void resume()  { }
    @Override public void dispose() { hide(); }

    // ─── TÍNH TOÁN BỐ CỤC (LAYOUT) ────────────────────────────────────────────
    private void layoutWidgets() {
        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;

        // Tối ưu cho mobile landscape & desktop:
        // Đảm bảo 3 card luôn hiển thị cân đối giữa màn hình
        float maxAllowedW = vw * 0.90f;
        float totalGapFraction = 0.08f; // khoảng cách giữa các card
        float gap = vw * totalGapFraction / (TANK_COUNT - 1);

        cardBaseW = Math.min((maxAllowedW - gap * (TANK_COUNT - 1)) / TANK_COUNT, vh * 0.46f);
        cardBaseH = cardBaseW * 1.34f;

        imgBaseSize  = cardBaseW * 0.68f;
        btnBaseW     = cardBaseW * 0.82f;
        btnBaseH     = Math.max(44f, cardBaseH * 0.17f); // Tối thiểu 44dp chuẩn touch mobile
        cornerRadius = Math.max(8f, cardBaseW * 0.06f);

        float totalGridW = TANK_COUNT * cardBaseW + (TANK_COUNT - 1) * gap;
        float startX = (vw - totalGridW) / 2f;
        float startY = (vh - cardBaseH) / 2f + vh * 0.02f;

        for (int i = 0; i < TANK_COUNT; i++) {
            float cx = startX + i * (cardBaseW + gap);
            cardRects[i].set(cx, startY, cardBaseW, cardBaseH);

            float bx = cx + (cardBaseW - btnBaseW) / 2f;
            float by = startY + cardBaseH * 0.05f;
            selectBtns[i].set(bx, by, btnBaseW, btnBaseH);
        }

        // Nút Back nằm ở phía dưới cân đối, hit-box lớn dễ bấm trên mobile
        float backW = Math.max(160f, vw * 0.22f);
        float backH = Math.max(48f, vh * 0.08f);
        btnBack.set((vw - backW) / 2f, vh * 0.04f, backW, backH);
    }

    // ─── HELPER METHODS ───────────────────────────────────────────────────────
    private static void drawRoundedRect(ShapeRenderer shape, float x, float y, float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shape.rect(x + r, y, width - r * 2, height);
        shape.rect(x, y + r, width, height - r * 2);
        shape.circle(x + r, y + r, r, 16);
        shape.circle(x + width - r, y + r, r, 16);
        shape.circle(x + r, y + height - r, r, 16);
        shape.circle(x + width - r, y + height - r, r, 16);
    }

    private void drawCentredLabel(String text, Rectangle r, Color textColor) {
        BitmapFont font = fonts.body;
        glyph.setText(font, text);
        float tx = r.x + (r.width - glyph.width) / 2f;
        float ty = r.y + (r.height + glyph.height) / 2f;

        // Shadow
        float sh = Math.max(1f, glyph.height * 0.05f);
        font.setColor(0f, 0f, 0f, 0.45f);
        font.draw(batch, glyph, tx + sh, ty - sh);

        // Foreground
        font.setColor(textColor);
        font.draw(batch, glyph, tx, ty);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * MathUtils.clamp(t, 0f, 1f);
    }

    private static Color lerpColor(Color c1, Color c2, float t) {
        float factor = MathUtils.clamp(t, 0f, 1f);
        return new Color(
            lerp(c1.r, c2.r, factor),
            lerp(c1.g, c2.g, factor),
            lerp(c1.b, c2.b, factor),
            lerp(c1.a, c2.a, factor)
        );
    }
}
