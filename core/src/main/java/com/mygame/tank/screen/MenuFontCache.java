package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * Generates and caches crisp FreeType bitmap fonts for the menu screens.
 *
 * <p>LibGDX's default {@link BitmapFont} is rasterised at ~15 px; scaling it up
 * with {@code setScale()} causes pixelation. This helper generates fonts at the
 * exact pixel size needed so they are always sharp at any resolution.
 *
 * <p>LibGDX ships with a bundled TTF ({@code com/badlogic/gdx/utils/lsans-15.ttf})
 * accessible via {@link FreeTypeFontGenerator} — no extra asset file needed.
 *
 * <p>Usage: call {@link #create(int, int)} once in {@code show()}, then
 * {@link #dispose()} in {@code hide()}.
 */
final class MenuFontCache {

    // Prevent instantiation — use the factory method
    private MenuFontCache() { }

    /** Pre-rasterised fonts generated for the current window size. */
    static final class Fonts {
        /** Large title / heading font. */
        public final BitmapFont title;
        /** Medium body / button-label font. */
        public final BitmapFont body;

        private Fonts(BitmapFont title, BitmapFont body) {
            this.title = title;
            this.body  = body;
        }

        public void dispose() {
            title.dispose();
            body .dispose();
        }
    }

    /**
     * Creates crisp fonts scaled for the given window dimensions.
     *
     * @param screenW current window width  in pixels
     * @param screenH current window height in pixels
     */
    static Fonts create(int screenW, int screenH) {
        // Scale font sizes proportionally: 1280×720 is the design baseline
        float scale    = Math.min(screenW / 1280f, screenH / 720f);
        int titlePx    = Math.max(10, Math.round(52 * scale));
        int bodyPx     = Math.max(8,  Math.round(28 * scale));

        FreeTypeFontGenerator gen = null;
        try {
            if (Gdx.files.internal("fonts/menu_font.ttf").exists()) {
                gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/menu_font.ttf"));
            }
        } catch (Exception ignored) {
        }

        if (gen != null) {
            try {
                FreeTypeFontGenerator.FreeTypeFontParameter params =
                    new FreeTypeFontGenerator.FreeTypeFontParameter();
                params.minFilter = Texture.TextureFilter.Linear;
                params.magFilter = Texture.TextureFilter.Linear;

                params.size = titlePx;
                BitmapFont title = gen.generateFont(params);

                params.size = bodyPx;
                BitmapFont body  = gen.generateFont(params);

                gen.dispose();
                return new Fonts(title, body);
            } catch (Exception e) {
                gen.dispose();
            }
        }

        // Fallback if TTF loading fails
        BitmapFont title = new BitmapFont();
        title.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        title.getData().setScale(Math.max(1f, titlePx / 15f));

        BitmapFont body = new BitmapFont();
        body.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        body.getData().setScale(Math.max(1f, bodyPx / 15f));

        return new Fonts(title, body);
    }
}
