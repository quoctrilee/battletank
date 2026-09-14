package com.mygame.tank.screen;

import com.badlogic.gdx.Application;
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
 * "How to Play" screen — 4-tab layout:
 *   TAB 0  Overview  — mini dungeon map + legend (room types, entities, doors)
 *   TAB 1  Controls  — full control reference (touch controls on mobile, keyboard/mouse on desktop)
 *   TAB 2  HUD       — annotated HUD mock-up diagram
 *   TAB 3  Weapons   — weapon & equipment list with colour samples
 *
 * <p>All colours and layout constants mirror {@link com.mygame.tank.render.GameRenderer}
 * so players immediately recognise what they see in-game.
 */
public class HowToPlayScreen implements Screen {

    // ─── Pages ────────────────────────────────────────────────────────────────

    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_CONTROLS = 1;
    private static final int PAGE_HUD      = 2;
    private static final int PAGE_WEAPONS  = 3;
    private static final int PAGE_COUNT    = 4;

    private static final String[] PAGE_LABELS = {
        "Overview", "Controls", "HUD", "Weapons"
    };

    // ─── Layout ───────────────────────────────────────────────────────────────

    private static final float BTN_WIDTH_DESKTOP  = 210f;
    private static final float BTN_HEIGHT_DESKTOP = 56f;
    private static final float TAB_HEIGHT_DESKTOP = 46f;

    // Mobile touch targets are enlarged so they meet a comfortable ~48dp+
    // minimum tap area regardless of screen density.
    private static final float BTN_WIDTH_MOBILE  = 260f;
    private static final float BTN_HEIGHT_MOBILE = 84f;
    private static final float TAB_HEIGHT_MOBILE = 72f;

    private static final float CORNER_RADIUS = 10f;

    // ─── UI colours ───────────────────────────────────────────────────────────

    private static final Color C_BG         = new Color(0.07f, 0.09f, 0.13f, 1f);
    private static final Color C_BTN_FILL   = new Color(0.10f, 0.14f, 0.22f, 0.90f);
    private static final Color C_BTN_HOVER  = new Color(0.14f, 0.48f, 0.88f, 0.95f);
    private static final Color C_TAB_ACTIVE = new Color(0.12f, 0.38f, 0.72f, 0.95f);
    private static final Color C_TAB_IDLE   = new Color(0.08f, 0.11f, 0.18f, 0.85f);
    private static final Color C_BORDER     = new Color(0.38f, 0.72f, 1.00f, 1.00f);
    private static final Color C_GLOW       = new Color(0.25f, 0.55f, 1.00f, 0.22f);
    private static final Color C_TITLE      = new Color(0.85f, 0.92f, 1.00f, 1f);
    private static final Color C_BODY       = new Color(0.75f, 0.82f, 0.95f, 1f);
    private static final Color C_KEY        = new Color(0.45f, 0.80f, 1.00f, 1f);
    private static final Color C_SECTION    = new Color(0.50f, 0.90f, 0.55f, 1f);
    private static final Color C_WARN       = new Color(1.00f, 0.75f, 0.25f, 1f);
    private static final Color C_CTX        = new Color(0.70f, 0.55f, 1.00f, 1f);

    // Entity / map colours — must match GameRenderer constants exactly
    private static final Color C_FLOOR   = new Color(0.13f, 0.16f, 0.11f, 1f);
    private static final Color C_WALL    = new Color(0.35f, 0.30f, 0.22f, 1f);
    private static final Color C_DOOR_O  = new Color(0.10f, 0.50f, 0.15f, 1f);
    private static final Color C_DOOR_C  = new Color(0.55f, 0.08f, 0.08f, 1f);
    private static final Color C_PLAYER  = new Color(0.25f, 0.75f, 0.30f, 1f);
    private static final Color C_ENEMY   = new Color(0.80f, 0.15f, 0.15f, 1f);
    private static final Color C_BOSS    = new Color(0.90f, 0.45f, 0.05f, 1f);

    // Bullet / effect colours — match GameRenderer
    private static final Color C_BULLET_DEFAULT = new Color(1.00f, 0.95f, 0.20f, 1f);
    private static final Color C_BULLET_SMG     = new Color(1.00f, 0.85f, 0.10f, 1f);
    private static final Color C_BULLET_AP      = new Color(0.60f, 0.90f, 1.00f, 1f);
    private static final Color C_BULLET_CANNON  = new Color(1.00f, 0.50f, 0.10f, 1f);
    private static final Color C_BULLET_STUN    = new Color(0.50f, 0.20f, 1.00f, 1f);
    private static final Color C_BULLET_HOMING  = new Color(1.00f, 0.20f, 0.80f, 1f);
    private static final Color C_LASER          = new Color(1.00f, 1.00f, 0.00f, 1f);

    // Equipment effect colours
    private static final Color C_SMOKE   = new Color(0.60f, 0.65f, 0.75f, 1f);
    private static final Color C_MINE    = new Color(1.00f, 0.20f, 0.10f, 1f);
    private static final Color C_EMP     = new Color(0.40f, 0.10f, 1.00f, 1f);
    private static final Color C_SHIELD  = new Color(0.30f, 0.80f, 1.00f, 1f);

    // ─── Static data tables ───────────────────────────────────────────────────

    /**
     * Desktop controls table: { context-tag (empty = always), key label, description }
     */
    private static final String[][] CONTROLS_DATA = {
        { "",          "WASD",       "Move the tank in the pressed direction" },
        { "",          "Mouse",      "Aim turret - it rotates to follow the cursor" },
        { "",          "LMB",        "Fire the currently selected weapon" },
        { "",          "Tab  (hold)","Open the Weapon Hub to view / switch weapons" },
        { "Outside Hub","1 / 2 / 3", "Activate the matching equipment" },
        { "Inside Hub", "1 - 7",     "Select a weapon slot; release Tab to close the Hub" },
        { "",          "R",          "Restart the level immediately" },
        { "",          "F1",         "Toggle Debug mode" },
    };

    /**
     * Mobile (touch) controls table: { context-tag (empty = always), control label, description }
     *
     * <p>Layout assumed on-screen: a virtual movement stick anchored bottom-left,
     * a virtual aim stick anchored bottom-right, with the fire / weapon / equipment
     * buttons clustered around the aim stick so both thumbs stay in place during play.
     */
    private static final String[][] CONTROLS_DATA_MOBILE = {
        { "",           "Left stick (bottom-left)",  "Drag with your left thumb to move the tank" },
        { "",           "Right stick (bottom-right)","Drag with your right thumb to aim the turret" },
        { "",           "FIRE button",               "Tap to shoot once, hold to fire continuously" },
        { "",           "Weapon icon (hold)",        "Hold to open the Weapon Hub and see all weapons" },
        { "Outside Hub","Equip. 1 / 2 / 3 icons",    "Tap an equipment icon (bottom-left row) to use it" },
        { "Inside Hub", "Weapon slot icons 1-7",     "Tap a slot to select it, then lift your finger to close the Hub" },
        { "",           "Pause icon (top corner)",   "Open the pause menu, including Restart" },
    };

    /**
     * Weapon data: { group-label, display name, colour, short description }
     * Colours match GameRenderer projectile colours exactly.
     */
    private static final Object[][] WEAPON_DATA = {
        { "A", "Default Bullet", C_BULLET_DEFAULT, "Basic gun - unlimited ammo, steady fire rate" },
        { "A", "Submachine Gun", C_BULLET_SMG,     "SMG - very high fire rate, low damage per round" },
        { "A", "Armor Pierce",   C_BULLET_AP,      "Armor-piercing round - passes through multiple targets in a row" },
        { "B", "Cannon",         C_BULLET_CANNON,  "AoE cannon - wide blast radius, limited ammo, long cooldown" },
        { "B", "Stun Shell",     C_BULLET_STUN,    "Stun round - immobilises enemies for a few seconds" },
        { "C", "Laser",          C_LASER,          "Continuous laser beam - no projectiles, steady damage" },
        { "C", "Homing Missile", C_BULLET_HOMING,  "Homing missile - automatically tracks the nearest target" },
    };

    /**
     * Equipment data: { colour, name, description }
     * Colours match GameRenderer WorldEffect colours.
     */
    private static final Object[][] EQUIP_DATA = {
        { C_SMOKE,  "Smoke Bomb",    "Creates a smoke screen - enemies briefly lose their target" },
        { C_MINE,   "Mine",          "Plants a mine - deals AoE damage when an enemy enters the trigger zone" },
        { C_EMP,    "EMP Field",     "Emits an EMP pulse - disables enemies within a wide radius" },
        { C_SHIELD, "Energy Shield", "Energy shield - reduces incoming damage for a short time" },
    };

    // ─── State ────────────────────────────────────────────────────────────────

    private final TankGame game;
    private int currentPage = PAGE_OVERVIEW;

    private OrthographicCamera  camera;
    private SpriteBatch         batch;
    private ShapeRenderer       shape;
    private MenuFontCache.Fonts fonts;
    /** Smaller variant of {@link MenuFontCache.Fonts#body}, used only on the Weapons page
     *  where the weapon + equipment list is dense and needs a tighter font. */
    private BitmapFont          weaponsFont;
    private GlyphLayout         glyph;

    private final Rectangle   btnBack = new Rectangle();
    private final Rectangle[] tabs    = new Rectangle[PAGE_COUNT];
    private final Vector3     mouse   = new Vector3();

    // ─── Constructor ──────────────────────────────────────────────────────────

    public HowToPlayScreen(TankGame game) {
        this.game = game;
        for (int i = 0; i < PAGE_COUNT; i++) tabs[i] = new Rectangle();
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
        fonts = MenuFontCache.create(sw, sh);
        weaponsFont = createWeaponsFont();

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

        // Unproject mouse
        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouse);
        boolean hBack = btnBack.contains(mouse.x, mouse.y);
        int hTab = -1;
        for (int i = 0; i < PAGE_COUNT; i++) {
            if (tabs[i].contains(mouse.x, mouse.y)) { hTab = i; break; }
        }

        // ── Shape pass (Filled) ───────────────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.begin(ShapeRenderer.ShapeType.Filled);

        // Tab backgrounds
        for (int i = 0; i < PAGE_COUNT; i++) {
            Color fill = (i == currentPage) ? C_TAB_ACTIVE
                : (i == hTab)        ? C_BTN_HOVER
                : C_TAB_IDLE;
            shape.setColor(fill);
            shape.rect(tabs[i].x, tabs[i].y, tabs[i].width, tabs[i].height);
        }

        // Back button
        drawRoundedButton(btnBack, hBack);

        // Page-specific filled shapes
        switch (currentPage) {
            case PAGE_OVERVIEW -> renderOverviewShapes(vw, vh);
            case PAGE_HUD      -> renderHudShapes(vw, vh);
            case PAGE_WEAPONS  -> renderWeaponShapes(vw, vh);
        }

        shape.end();

        // ── Shape pass (Line) — borders ───────────────────────────────────────
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < PAGE_COUNT; i++) {
            Color bc = (i == currentPage)
                ? C_BORDER
                : new Color(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.35f);
            shape.setColor(bc);
            shape.rect(tabs[i].x, tabs[i].y, tabs[i].width, tabs[i].height);
        }
        drawGlowBorder(btnBack, hBack);
        if (currentPage == PAGE_CONTROLS) renderControlsLines(vw, vh);
        shape.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── Text pass ─────────────────────────────────────────────────────────
        batch.begin();

        // Tab labels
        for (int i = 0; i < PAGE_COUNT; i++) {
            fonts.body.setColor(i == currentPage ? Color.WHITE : C_BODY);
            glyph.setText(fonts.body, PAGE_LABELS[i]);
            float tx = tabs[i].x + (tabs[i].width  - glyph.width)  / 2f;
            float ty = tabs[i].y + (tabs[i].height + glyph.height) / 2f;
            fonts.body.draw(batch, glyph, tx, ty);
        }

        // Page content
        switch (currentPage) {
            case PAGE_OVERVIEW -> renderOverviewText(vw, vh);
            case PAGE_CONTROLS -> renderControlsText(vw, vh);
            case PAGE_HUD      -> renderHudText(vw, vh);
            case PAGE_WEAPONS  -> renderWeaponsText(vw, vh);
        }

        // Back button label
        drawLabel("Back", btnBack);
        batch.end();

        // ── Input ─────────────────────────────────────────────────────────────
        if (Gdx.input.justTouched()) {
            if (hBack) {
                game.setScreen(new MenuScreen(game));
            } else if (hTab >= 0 && hTab != currentPage) {
                currentPage = hTab;
            }
        }
    }

    /**
     * Generates a smaller body-style font just for the Weapons page.
     *
     * <p>This does NOT reuse {@link MenuFontCache} (its FreeType path regenerates
     * a font at a target pixel size, and its fallback path clamps scale to a
     * minimum of 1x — both of which made an earlier attempt at this look
     * identical to {@link MenuFontCache.Fonts#body}). Instead this creates an
     * independent default {@link BitmapFont} and scales it so its rendered line
     * height is a fixed fraction of {@link #fonts}.body's actual on-screen line
     * height — measured directly in pixels, so this works correctly no matter
     * which MenuFontCache code path produced {@link #fonts}.body.
     */
    private static final float WEAPONS_FONT_SCALE = 0.62f;

    private BitmapFont createWeaponsFont() {
        BitmapFont f = new BitmapFont(); // default font, natural line height ~15px at scale 1
        f.getRegion().getTexture().setFilter(
            com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
            com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        float naturalLineHeight = f.getLineHeight(); // at scale 1 (~15px)
        float targetLineHeight  = fonts.body.getLineHeight() * WEAPONS_FONT_SCALE;
        float scale = Math.max(0.3f, targetLineHeight / naturalLineHeight);
        f.getData().setScale(scale);
        return f;
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        if (fonts != null) fonts.dispose();
        if (weaponsFont != null) weaponsFont.dispose();
        fonts = MenuFontCache.create(width, height);
        weaponsFont = createWeaponsFont();
        layoutWidgets();
    }

    @Override public void hide() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shape != null) { shape.dispose(); shape = null; }
        if (fonts != null) { fonts.dispose(); fonts = null; }
        if (weaponsFont != null) { weaponsFont.dispose(); weaponsFont = null; }
    }

    @Override public void pause()  { }
    @Override public void resume() { }
    @Override public void dispose() { hide(); }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGE 0 — OVERVIEW (mini dungeon map + legend)
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderOverviewShapes(float vw, float vh) {
        // ── Mini dungeon map ──────────────────────────────────────────────────
        float mapW = vw * 0.42f;
        float mapH = vh * 0.46f;
        float mapX = vw * 0.04f;
        float mapY = vh * 0.20f;

        // Void background
        shape.setColor(0.04f, 0.04f, 0.05f, 1f);
        shape.rect(mapX, mapY, mapW, mapH);

        // ── Rooms ─────────────────────────────────────────────────────────────
        // Spawn room (left-center)
        float rW = mapW * 0.26f, rH = mapH * 0.34f;
        float spX = mapX + mapW * 0.04f;
        float spY = mapY + (mapH - rH) / 2f;
        shape.setColor(C_FLOOR);
        shape.rect(spX, spY, rW, rH);
        shape.setColor(C_WALL);
        borderRect(spX, spY, rW, rH, 3f);

        // Combat room (center)
        float crW = mapW * 0.22f, crH = mapH * 0.28f;
        float crX = spX + rW + mapW * 0.06f;
        float crY = mapY + (mapH - crH) / 2f;
        shape.setColor(C_FLOOR);
        shape.rect(crX, crY, crW, crH);
        shape.setColor(C_WALL);
        borderRect(crX, crY, crW, crH, 3f);

        // Boss room (right)
        float brW = mapW * 0.22f, brH = mapH * 0.32f;
        float brX = mapX + mapW * 0.72f;
        float brY = mapY + (mapH - brH) / 2f;
        shape.setColor(new Color(0.15f, 0.09f, 0.09f, 1f));
        shape.rect(brX, brY, brW, brH);
        shape.setColor(C_WALL);
        borderRect(brX, brY, brW, brH, 3f);

        // ── Corridors ─────────────────────────────────────────────────────────
        float corrH = rH * 0.18f;
        shape.setColor(new Color(0.11f, 0.14f, 0.09f, 1f));
        // Spawn → Combat
        float corrY1 = spY + (rH - corrH) / 2f;
        shape.rect(spX + rW, corrY1, crX - (spX + rW), corrH);
        // Combat → Boss
        float corrY2 = crY + (crH - corrH) / 2f;
        shape.rect(crX + crW, corrY2, brX - (crX + crW), corrH);

        // ── Doors ─────────────────────────────────────────────────────────────
        float doorThick = 5f;
        // Open door: spawn → corridor exit
        shape.setColor(C_DOOR_O);
        shape.rect(spX + rW - doorThick, corrY1 + 2f, doorThick * 2f, corrH - 4f);
        // Closed (boss) door: before boss room
        shape.setColor(C_DOOR_C);
        shape.rect(brX - doorThick, corrY2 + 2f, doorThick * 2.5f, corrH - 4f);

        // ── Entity icons ──────────────────────────────────────────────────────
        // Player (spawn room)
        float px = spX + rW * 0.5f, py = spY + rH * 0.5f;
        drawTinyTank(px, py, C_PLAYER, 7f);

        // Enemies (combat room)
        float eSpacing = crW * 0.35f;
        for (int i = 0; i < 2; i++) {
            float ex = crX + crW * (0.25f + i * 0.5f);
            float ey = crY + crH * 0.5f;
            drawTinyTank(ex, ey, C_ENEMY, 5.5f);
        }

        // Boss (boss room — larger)
        float bx = brX + brW * 0.5f, by = brY + brH * 0.5f;
        drawTinyTank(bx, by, C_BOSS, 8.5f);

        // HP bars above entities
        drawMiniHpBar(px, py + 11f, 16f, 0.75f, C_PLAYER);
        drawMiniHpBar(crX + crW * 0.25f, crY + crH * 0.5f + 9f, 13f, 0.55f, C_ENEMY);
        drawMiniHpBar(crX + crW * 0.75f, crY + crH * 0.5f + 9f, 13f, 0.30f, C_ENEMY);
        drawMiniHpBar(bx, by + 13f, 20f, 0.65f, C_BOSS);

        // Map border
        shape.setColor(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.55f);
        borderRect(mapX, mapY, mapW, mapH, 2f);

        // ── Legend colour squares — shifted right and lower, roughly aligned
        //    with the upper half of the map instead of floating above it ───────
        float legX  = mapX + mapW + vw * 0.09f;
        float rowH  = fonts.body.getLineHeight() * 1.5f;
        float legHeadY = mapY + mapH * 0.86f;
        int   legCount = 7; // must match labels.length in renderOverviewText
        float legRowH  = Math.min(rowH, (legHeadY - mapY) / (legCount + 0.3f));
        float legY0 = legHeadY - legRowH * 1.3f;
        float sqSz  = 13f;

        Color[] legColours = { C_PLAYER, C_ENEMY, C_BOSS, C_FLOOR, C_WALL, C_DOOR_O, C_DOOR_C };
        for (int i = 0; i < legColours.length; i++) {
            shape.setColor(legColours[i]);
            shape.rect(legX, legY0 - i * legRowH - sqSz * 0.4f, sqSz, sqSz);
        }
    }

    private void renderOverviewText(float vw, float vh) {
        float mapW  = vw * 0.42f;
        float mapX  = vw * 0.04f;
        float mapY  = vh * 0.20f;
        float mapH  = vh * 0.46f;
        float rowH  = fonts.body.getLineHeight() * 1.5f;

        // ── Objective blurb — sits above the map ────────────────────────────────
        fonts.body.setColor(C_SECTION);
        fonts.body.draw(batch, "Objective", mapX, mapY + mapH + rowH * 1.4f);

        fonts.body.setColor(C_BODY);
        fonts.body.draw(batch,
            "Explore the dungeon, find and defeat every Boss to win.",
            mapX, mapY + mapH + rowH * 0.4f);

        // ── Map section label ─────────────────────────────────────────────────
        fonts.body.setColor(C_SECTION);
        fonts.body.draw(batch, "Dungeon map:", mapX, mapY - rowH * 0.25f);

        // ── Legend section heading — shifted right and lower (roughly level
        //    with the upper half of the map) so it reads as its own block ──────
        float legX  = mapX + mapW + vw * 0.09f;
        float legHeadY = mapY + mapH * 0.86f;
        fonts.body.setColor(C_SECTION);
        fonts.body.draw(batch, "Legend:", legX, legHeadY);

        // ── Legend labels ─────────────────────────────────────────────────────
        float legListX = legX + 20f;
        int   legCount = 7; // matches labels.length below
        float legRowH  = Math.min(rowH, (legHeadY - mapY) / (legCount + 0.3f));
        float legY0    = legHeadY - legRowH * 1.3f;

        String[] labels = {
            "Player tank",
            "Enemy",
            "Boss",
            "Room floor",
            "Wall",
            "Open door",
            "Closed door (Boss)"
        };
        for (int i = 0; i < labels.length; i++) {
            fonts.body.setColor(C_BODY);
            fonts.body.draw(batch, labels[i], legListX, legY0 - i * legRowH);
        }

        // ── Bottom tips ───────────────────────────────────────────────────────
        float tipY = mapY - rowH * 1.6f;
        fonts.body.setColor(C_WARN);
        fonts.body.draw(batch,
            "Boss rooms are locked behind red doors.",
            mapX, tipY);
        fonts.body.draw(batch,
            "Fog of War: you only see the area around your tank.",
            mapX, tipY - rowH);
        fonts.body.draw(batch,
            "Health bars: green > yellow > red as HP drops.",
            mapX, tipY - rowH * 2f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGE 1 — CONTROLS
    // ═══════════════════════════════════════════════════════════════════════════

    /** True when running on a touch-only platform (Android / iOS). */
    private static boolean isMobile() {
        Application.ApplicationType type = Gdx.app.getType();
        return type == Application.ApplicationType.Android
            || type == Application.ApplicationType.iOS;
    }

    /**
     * Draws the Controls page's separator lines.
     *
     * <p>Both desktop and mobile now share the same ruled-table style (one
     * solid rule under the header, faint rules under each row). Mobile just
     * drops the "Context" column — folding it into the "Control" cell — so
     * it only needs 2 columns to fit a narrow portrait screen; see
     * {@link #mobileControlsLayout(float, float, int)} for its geometry.
     */
    private void renderControlsLines(float vw, float vh) {
        boolean mobile = isMobile();
        String[][] data = mobile ? CONTROLS_DATA_MOBILE : CONTROLS_DATA;

        float startX      = vw * (mobile ? 0.05f : 0.06f);
        float startY      = vh * (mobile ? 0.86f : 0.84f);
        float tableRight  = vw * 0.95f;

        float tipLh        = fonts.body.getLineHeight();
        float bottomLimit  = btnBack.y + btnBack.height + tipLh * 0.8f;
        float reservedTips = mobile ? tipLh * 2.0f : tipLh * 3.8f;
        float headerH      = tipLh * 1.6f;
        float availableH   = startY - headerH - bottomLimit - reservedTips;
        float naturalRowH  = Math.min(fonts.body.getLineHeight() * 1.7f, vh * 0.065f);
        float rowH         = Math.min(naturalRowH, Math.max(availableH / data.length, fonts.body.getLineHeight() * 1.05f));
        float tableTop     = startY - headerH * 0.55f;

        // Header rule — solid
        shape.setColor(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.7f);
        shape.rectLine(startX, tableTop, tableRight, tableTop, 1.5f);

        // Per-row separators — faint
        shape.setColor(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.22f);
        for (int i = 0; i < data.length; i++) {
            float y = tableTop - rowH * (i + 1);
            shape.rectLine(startX, y, tableRight, y, 1f);
        }
    }

    /** Shared geometry for the mobile 2-column (Control / Effect) Controls table. */
    private static final class ControlsLayout {
        float left, topY, rowH;
    }

    private ControlsLayout mobileControlsLayout(float vw, float vh, int rowCount) {
        ControlsLayout l = new ControlsLayout();
        l.left  = vw * 0.05f;
        float startY       = vh * 0.86f;
        float lh           = fonts.body.getLineHeight();
        float bottomLimit  = btnBack.y + btnBack.height + lh * 0.8f;
        float reservedTip  = lh * 2.0f; // single tip line reserved at the bottom on mobile
        float headerH      = lh * 1.6f; // space reserved for header row + rule line
        float availableH   = startY - headerH - bottomLimit - reservedTip;
        float naturalRowH  = Math.min(fonts.body.getLineHeight() * 1.7f, vh * 0.065f);
        l.rowH = Math.min(naturalRowH, Math.max(availableH / rowCount, fonts.body.getLineHeight() * 1.05f));
        l.topY = startY - headerH * 0.55f; // == tableTop in renderControlsLines, for alignment
        return l;
    }

    private void renderControlsText(float vw, float vh) {
        boolean mobile = isMobile();
        String[][] data = mobile ? CONTROLS_DATA_MOBILE : CONTROLS_DATA;

        if (mobile) {
            renderControlsTextMobile(vw, vh, data);
            return;
        }

        float startX = vw * 0.06f;
        float startY = vh * 0.84f;
        float ctxW   = vw * 0.14f;   // context-tag column
        float keyW   = vw * 0.12f;   // key column (left-aligned)
        float descX  = startX + ctxW + keyW + vw * 0.02f;

        // Row height + tip spacing shrink together to fit: header + rule +
        // one row per control + two tip lines, all above the Back button.
        float tipLh        = fonts.body.getLineHeight();
        float bottomLimit  = btnBack.y + btnBack.height + tipLh * 0.8f;
        float reservedTips = tipLh * 3.8f; // space for the two TIP lines + their gap from the table
        float headerH      = tipLh * 1.6f; // space reserved for header row + rule line
        float availableH   = startY - headerH - bottomLimit - reservedTips;
        float naturalRowH  = Math.min(fonts.body.getLineHeight() * 1.7f, vh * 0.065f);
        float rowH         = Math.min(naturalRowH, Math.max(availableH / data.length, fonts.body.getLineHeight() * 1.05f));

        // Column headers
        fonts.body.setColor(C_SECTION);
        fonts.body.draw(batch, "Context", startX,        startY);
        fonts.body.draw(batch, "Key",     startX + ctxW, startY);
        fonts.body.draw(batch, "Effect",  descX,         startY);

        float tableTop = startY - headerH * 0.55f; // rule line y, drawn by renderControlsLines

        for (int i = 0; i < data.length; i++) {
            // Baseline sits near the TOP of the row (rows count down from tableTop),
            // so the glyph body — which draws downward from y in libGDX — stays
            // above this row's bottom rule line instead of overlapping it.
            float y = tableTop - rowH * i - rowH * 0.22f;

            // Context tag
            String ctx = data[i][0];
            if (!ctx.isEmpty()) {
                fonts.body.setColor(C_CTX);
                fonts.body.draw(batch, "[" + ctx + "]", startX, y);
            }

            // Key / control — left-aligned for a cleaner column edge
            fonts.body.setColor(C_KEY);
            fonts.body.draw(batch, data[i][1], startX + ctxW, y);

            // Description
            fonts.body.setColor(C_BODY);
            fonts.body.draw(batch, data[i][2], descX, y);
        }

        // ── Tips — anchored below the last data row, well above the Back button ──
        float lastRowY = tableTop - rowH * data.length;
        float tipY     = Math.max(lastRowY - tipLh * 1.6f, bottomLimit + tipLh * 1.6f);

        fonts.body.setColor(C_WARN);
        fonts.body.draw(batch,
            "TIP: Hold Tab to see weapon info, then press 1-7 to switch.",
            startX, tipY);
        fonts.body.setColor(new Color(0.55f, 0.75f, 0.55f, 1f));
        fonts.body.draw(batch,
            "TIP: Group A - sustained fire; Group B - AoE / control; Group C - specials.",
            startX, tipY - tipLh * 1.6f);
    }

    /**
     * Mobile Controls layout: same ruled-table style as desktop, just with 2
     * columns instead of 3 — "Control" and "Effect". The context tag (e.g.
     * "[Inside Hub]") isn't given its own column on a narrow portrait screen;
     * instead it's appended after the control name, matching how the tag
     * reads inline in {@link #CONTROLS_DATA_MOBILE}.
     */
    private void renderControlsTextMobile(float vw, float vh, String[][] data) {
        ControlsLayout l = mobileControlsLayout(vw, vh, data.length);
        float startX = l.left;
        float keyW   = vw * 0.42f;              // "Control" column width
        float descX  = startX + keyW + vw * 0.02f; // "Effect" column start
        float startY = vh * 0.86f;
        float lh     = fonts.body.getLineHeight();

        // Column headers
        fonts.body.setColor(C_SECTION);
        fonts.body.draw(batch, "Control", startX, startY);
        fonts.body.draw(batch, "Effect",  descX,  startY);

        for (int i = 0; i < data.length; i++) {
            // Baseline near the TOP of the row so the glyph body stays above
            // this row's bottom rule line, matching the desktop table.
            float y = l.topY - l.rowH * i - l.rowH * 0.22f;

            String ctx = data[i][0];
            fonts.body.setColor(C_KEY);
            String label = ctx.isEmpty() ? data[i][1] : data[i][1] + "  [" + ctx + "]";
            fonts.body.draw(batch, label, startX, y);

            fonts.body.setColor(C_BODY);
            fonts.body.draw(batch, data[i][2], descX, y);
        }

        // ── Single tip line, anchored above the Back button ─────────────────
        float bottomLimit = btnBack.y + btnBack.height + lh * 0.8f;
        float lastRowY    = l.topY - l.rowH * data.length;
        float tipY = Math.max(lastRowY - lh * 1.6f, bottomLimit);
        fonts.body.setColor(C_WARN);
        fonts.body.draw(batch,
            "TIP: hold the weapon icon, then tap a slot to switch weapons.",
            startX, tipY);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGE 2 — HUD
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the HUD mock-up rectangle (top-left origin in camera/HUD coords). */
    private Rectangle hudRect(float vw, float vh) {
        // Enlarged now that the callout notes below the diagram are gone —
        // the box can occupy more of the vertical space between the tabs and
        // the Back button.
        float w = vw * 0.62f;
        float h = w * (9f / 16f);
        float x = (vw - w) / 2f;
        float labelHeadroom = fonts.body.getLineHeight() * 1.4f;
        float tabBottom     = vh * (isMobile() ? 0.90f : 0.930f); // must match layoutWidgets() tabY
        float bottomLimit   = btnBack.y + btnBack.height + fonts.body.getLineHeight() * 1.2f;
        float availableH    = tabBottom - labelHeadroom - bottomLimit;
        if (h > availableH) h = availableH;
        w = h * (16f / 9f);
        x = (vw - w) / 2f;
        // Center the box in the available vertical space
        float y = bottomLimit + (availableH - h) / 2f;
        return new Rectangle(x, y, w, h);
    }

    private void renderHudShapes(float vw, float vh) {
        Rectangle r = hudRect(vw, vh);
        float dX = r.x, dY = r.y, dW = r.width, dH = r.height;

        // Screen background
        shape.setColor(0.04f, 0.05f, 0.07f, 0.96f);
        shape.rect(dX, dY, dW, dH);

        // World floor area
        shape.setColor(C_FLOOR.r, C_FLOOR.g, C_FLOOR.b, 0.55f);
        shape.rect(dX + 2, dY + 28, dW - 4, dH - 56);

        // ── HP bar (top-left) ─────────────────────────────────────────────────
        float hpW = dW * 0.18f, hpH = 7f;
        float hpX = dX + 8f,   hpY = dY + dH - 19f;
        shape.setColor(0.15f, 0.15f, 0.15f, 0.85f);
        shape.rect(hpX, hpY, hpW, hpH);
        shape.setColor(0.15f, 0.85f, 0.25f, 1f);
        shape.rect(hpX, hpY, hpW * 0.73f, hpH);

        // Stun bar (purple, above HP bar)
        shape.setColor(0.6f, 0.2f, 1.0f, 0.85f);
        shape.rect(hpX, hpY + hpH + 2f, hpW * 0.45f, 4f);

        // ── Boss HP bar (top-center) ───────────────────────────────────────────
        float bBW = dW * 0.24f, bBH = 6f;
        float bBX = dX + (dW - bBW) / 2f, bBY = dY + dH - 16f;
        shape.setColor(0.15f, 0.15f, 0.15f, 0.85f);
        shape.rect(bBX, bBY, bBW, bBH);
        shape.setColor(0.85f, 0.40f, 0.05f, 1f);
        shape.rect(bBX, bBY, bBW * 0.58f, bBH);

        // ── Mini player in world ───────────────────────────────────────────────
        float px = dX + dW * 0.45f, py = dY + dH * 0.52f;
        drawTinyTank(px, py, C_PLAYER, 7f);

        // ── Mini enemy in world ───────────────────────────────────────────────
        drawTinyTank(dX + dW * 0.65f, dY + dH * 0.48f, C_ENEMY, 5.5f);

        // ── Fog of war (black strips around visible area) ──────────────────────
        float fogW = dW * 0.38f, fogH = dH * 0.52f;
        float fogX = px - fogW / 2f, fogY = py - fogH / 2f;
        shape.setColor(0f, 0f, 0f, 0.90f);
        shape.rect(dX + 2,       dY + 28,    fogX - (dX + 2),          dH - 56f);
        shape.rect(fogX + fogW,  dY + 28,    (dX + dW - 2) - (fogX + fogW), dH - 56f);
        shape.rect(fogX,         dY + 28,    fogW,                     fogY - (dY + 28));
        shape.rect(fogX,         fogY + fogH,fogW,                     (dY + dH - 28) - (fogY + fogH));

        // ── Weapon slots × 3 (bottom-center) ─────────────────────────────────
        int   WN  = 3;
        float wsz = dW * 0.085f, wgap = 4f;
        float wsX0 = dX + (dW - (WN * wsz + (WN - 1) * wgap)) / 2f;
        float wsY  = dY + 5f;
        for (int i = 0; i < WN; i++) {
            float sx = wsX0 + i * (wsz + wgap);
            shape.setColor(i == 0
                ? new Color(0.20f, 0.55f, 0.22f, 0.85f)
                : new Color(0.12f, 0.12f, 0.12f, 0.82f));
            shape.rect(sx, wsY, wsz, wsz);
            // Cooldown overlay on slot 1
            if (i == 1) {
                shape.setColor(0f, 0f, 0f, 0.50f);
                shape.rect(sx, wsY, wsz, wsz * 0.45f);
            }
            // Ammo bar at bottom of slot 2
            if (i == 2) {
                shape.setColor(0.2f, 0.2f, 0.2f, 1f);
                shape.rect(sx, wsY, wsz, 3.5f);
                shape.setColor(Color.CYAN);
                shape.rect(sx, wsY, wsz * 0.62f, 3.5f);
            }
            shape.setColor(i == 0 ? Color.WHITE : Color.GRAY);
            borderRect(sx, wsY, wsz, wsz, 1.5f);
        }

        // ── Equipment slots × 3 (bottom-left) ────────────────────────────────
        int   EN  = 3;
        float esz = dW * 0.068f, egap = 3f;
        float esX0 = dX + 5f;
        float esY  = dY + 5f;
        for (int i = 0; i < EN; i++) {
            float sx = esX0 + i * (esz + egap);
            shape.setColor(0.12f, 0.12f, 0.18f, 0.85f);
            shape.rect(sx, esY, esz, esz);
            // Cooldown pie on slot 1
            if (i == 1) {
                shape.setColor(0f, 0f, 0f, 0.55f);
                shape.rect(sx, esY, esz, esz * 0.5f);
            }
            shape.setColor(i == 1 ? Color.DARK_GRAY : new Color(0.4f, 0.8f, 1f, 0.95f));
            borderRect(sx, esY, esz, esz, 1.5f);
        }

        // Screen border
        shape.setColor(C_BORDER.r, C_BORDER.g, C_BORDER.b, 0.55f);
        borderRect(dX, dY, dW, dH, 2f);
    }

    private void renderHudText(float vw, float vh) {
        Rectangle r  = hudRect(vw, vh);
        float dX = r.x, dY = r.y, dW = r.width, dH = r.height;
        float lh = fonts.body.getLineHeight();

        // ── In-diagram labels — placed above the HUD box so they never overlap
        //    the small bars/icons drawn inside it ──────────────────────────────
        float labelY = dY + dH + lh * 1.1f;

        fonts.body.setColor(C_KEY);
        fonts.body.draw(batch, "HP / STUN", dX + 8f, labelY);

        float bBW = dW * 0.24f;
        float bBX = dX + (dW - bBW) / 2f;
        fonts.body.setColor(new Color(0.85f, 0.5f, 0.1f, 1f));
        glyph.setText(fonts.body, "Boss HP");
        fonts.body.draw(batch, glyph, bBX + bBW / 2f - glyph.width / 2f, labelY);

        fonts.body.setColor(new Color(0.65f, 0.85f, 0.65f, 1f));
        glyph.setText(fonts.body, "Room progress");
        fonts.body.draw(batch, glyph, dX + dW - glyph.width - 4f, labelY);

        // Weapon slots label
        int   WN  = 3;
        float wsz = dW * 0.085f, wgap = 4f;
        float wsX0 = dX + (dW - (WN * wsz + (WN - 1) * wgap)) / 2f;
        boolean mobile = isMobile();

        fonts.body.setColor(C_BODY);
        fonts.body.draw(batch,
            mobile ? "Weapons (hold, then tap)" : "Weapons (Tab, 1-7)",
            wsX0, dY + 5f + wsz + lh * 0.9f);
        for (int i = 0; i < WN; i++) {
            float sx = wsX0 + i * (wsz + wgap);
            fonts.body.setColor(i == 0 ? Color.WHITE : Color.GRAY);
            fonts.body.draw(batch, String.valueOf(i + 1), sx + 3f, dY + 5f + wsz - 3f);
        }

        // Equipment slots label
        float esz = dW * 0.068f, egap = 3f;
        fonts.body.setColor(C_KEY);
        fonts.body.draw(batch,
            mobile ? "Equipment (tap)" : "Equipment (1/2/3)",
            dX + 5f, dY + 5f + esz + lh * 0.9f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGE 3 — WEAPONS & EQUIPMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /** Total number of visual rows the Weapons page needs (headers + items + equipment + gap). */
    private int weaponsPageRowCount() {
        String lastGroup = "";
        int row = 0;
        for (Object[] w : WEAPON_DATA) {
            String group = (String) w[0];
            if (!group.equals(lastGroup)) { lastGroup = group; row++; }
            row++;
        }
        row += 1;                       // gap row before equipment section
        row += EQUIP_DATA.length;
        return row;
    }

    private void renderWeaponShapes(float vw, float vh) {
        float startX = vw * 0.05f;
        float startY = vh * 0.82f;
        float rowH   = weaponsRowHeight(vh);
        float dotR   = Math.min(6f, rowH * 0.28f);

        // Weapon dots (skip group-header rows)
        String lastGroup = "";
        int row = 0;
        for (Object[] w : WEAPON_DATA) {
            String group = (String) w[0];
            if (!group.equals(lastGroup)) { lastGroup = group; row++; } // header row
            float y = startY - row * rowH;
            shape.setColor((Color) w[2]);
            shape.circle(startX + dotR, y - dotR * 0.45f, dotR, 14);
            row++;
        }

        // Equipment dots — start right after the last weapon row, plus a gap
        float eStartY = startY - (row + 1.5f) * rowH;
        for (int i = 0; i < EQUIP_DATA.length; i++) {
            float y = eStartY - i * rowH;
            shape.setColor((Color) EQUIP_DATA[i][0]);
            shape.circle(startX + dotR, y - dotR * 0.45f, dotR, 14);
        }
    }

    /**
     * Row height for the Weapons page, shrunk to fit all rows (weapons + equipment + note)
     * between the tabs and the Back button without overlap.
     */
    private float weaponsRowHeight(float vh) {
        float startY      = vh * 0.82f;
        float bottomLimit = btnBack.y + btnBack.height + weaponsFont.getLineHeight() * 1.8f; // reserve room for the note line
        float availableH  = startY - bottomLimit;
        int   rows         = weaponsPageRowCount();
        float natural      = weaponsFont.getLineHeight() * 1.35f;
        float fitted       = availableH / (rows + 1.5f); // +1.5f matches the gap before equipment section
        return Math.min(natural, Math.max(fitted, weaponsFont.getLineHeight() * 0.75f));
    }

    private void renderWeaponsText(float vw, float vh) {
        float startX = vw * 0.05f;
        float startY = vh * 0.82f;
        float rowH   = weaponsRowHeight(vh);
        float dotR   = Math.min(6f, rowH * 0.28f);
        float nameX  = startX + dotR * 2.6f + 8f;

        // Description column starts right after the longest name (weapon or
        // equipment), so the text never overlaps regardless of font size.
        float maxNameW = 0f;
        for (Object[] w : WEAPON_DATA) {
            glyph.setText(weaponsFont, (String) w[1]);
            maxNameW = Math.max(maxNameW, glyph.width);
        }
        for (Object[] e : EQUIP_DATA) {
            glyph.setText(weaponsFont, (String) e[1]);
            maxNameW = Math.max(maxNameW, glyph.width);
        }
        float descX = nameX + maxNameW + vw * 0.02f;

        boolean mobile = isMobile();

        // ── Weapon section ────────────────────────────────────────────────────
        weaponsFont.setColor(C_SECTION);
        weaponsFont.draw(batch,
            mobile
                ? "Weapons - hold the weapon icon, then tap a slot to choose"
                : "Weapons - open the Weapon Hub with Tab, choose a slot with 1-7",
            startX, startY + rowH * 0.85f);

        String lastGroup = "";
        int row = 0;
        for (Object[] w : WEAPON_DATA) {
            String group = (String) w[0];
            if (!group.equals(lastGroup)) {
                lastGroup = group;
                weaponsFont.setColor(C_WARN);
                weaponsFont.draw(batch, "Group " + group + ":", nameX, startY - row * rowH);
                row++;
            }
            float y = startY - row * rowH;

            // Name
            weaponsFont.setColor(Color.WHITE);
            glyph.setText(weaponsFont, (String) w[1]);
            weaponsFont.draw(batch, glyph, nameX, y);

            // Description
            weaponsFont.setColor(C_BODY);
            weaponsFont.draw(batch, (String) w[3], descX, y);

            row++;
        }

        // ── Equipment section — starts right after the weapon list ────────────
        float eStartY = startY - (row + 1.5f) * rowH;

        weaponsFont.setColor(C_SECTION);
        weaponsFont.draw(batch,
            mobile
                ? "Equipment - tap an equipment slot to activate (outside the Weapon Hub)"
                : "Equipment - activate with keys 1 / 2 / 3 (outside the Weapon Hub)",
            startX, eStartY + rowH * 0.85f);

        for (int i = 0; i < EQUIP_DATA.length; i++) {
            float y = eStartY - i * rowH;

            // Name
            weaponsFont.setColor(Color.WHITE);
            glyph.setText(weaponsFont, (String) EQUIP_DATA[i][1]);
            weaponsFont.draw(batch, glyph, nameX, y);

            // Description
            weaponsFont.setColor(C_BODY);
            weaponsFont.draw(batch, (String) EQUIP_DATA[i][2], descX, y);
        }

        // ── Bottom note — pinned safely above the Back button ───────────────────
        float noteY = Math.min(eStartY - EQUIP_DATA.length * rowH - rowH * 0.5f,
            btnBack.y + btnBack.height + weaponsFont.getLineHeight() * 1.1f);
        weaponsFont.setColor(new Color(0.55f, 0.55f, 0.55f, 1f));
        weaponsFont.draw(batch,
            "Note: dot colours match the projectile / effect colours you see in-game.",
            startX, noteY);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Layout
    // ═══════════════════════════════════════════════════════════════════════════

    private void layoutWidgets() {
        float vw = camera.viewportWidth;
        float vh = camera.viewportHeight;
        boolean mobile = isMobile();

        float btnW    = mobile ? BTN_WIDTH_MOBILE  : BTN_WIDTH_DESKTOP;
        float btnH    = mobile ? BTN_HEIGHT_MOBILE : BTN_HEIGHT_DESKTOP;
        float tabH    = mobile ? TAB_HEIGHT_MOBILE : TAB_HEIGHT_DESKTOP;

        // Back button — bottom-center. Raised slightly on mobile so it clears
        // the on-screen gesture-navigation bar found on most phones.
        float btnY = vh * (mobile ? 0.045f : 0.03f);
        btnBack.set((vw - btnW) / 2f, btnY, btnW, btnH);

        // Tabs — evenly spread across the screen width, near the top.
        // On a narrow portrait phone screen, 4 tabs across 90% of the width
        // leaves very little room per label, so mobile trims the side margin
        // instead of shrinking tab height (tabs stay tall enough to tap).
        float tabW    = mobile ? vw * 0.235f : vw * 0.21f;
        float tabGap  = vw * 0.012f;
        float totalTW = PAGE_COUNT * tabW + (PAGE_COUNT - 1) * tabGap;
        float tabX0   = (vw - totalTW) / 2f;
        float tabY    = vh * (mobile ? 0.90f : 0.930f);
        for (int i = 0; i < PAGE_COUNT; i++) {
            tabs[i].set(tabX0 + i * (tabW + tabGap), tabY, tabW, tabH);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shape helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** 4-pixel-thick border rectangle using filled strips. */
    private void borderRect(float x, float y, float w, float h, float t) {
        shape.rect(x,         y,         w, t);
        shape.rect(x,         y + h - t, w, t);
        shape.rect(x,         y,         t, h);
        shape.rect(x + w - t, y,         t, h);
    }

    /**
     * Tiny top-down tank icon: body (rectangle) + barrel (short line stub).
     *
     * @param cx     world-space centre X
     * @param cy     world-space centre Y
     * @param colour hull colour
     * @param half   half-size of the body rectangle
     */
    private void drawTinyTank(float cx, float cy, Color colour, float half) {
        shape.setColor(colour);
        shape.rect(cx - half, cy - half * 0.75f, half * 2f, half * 1.5f);
        // Barrel stub (darker)
        shape.setColor(colour.r * 0.65f, colour.g * 0.65f, colour.b * 0.65f, 1f);
        shape.rect(cx, cy - half * 0.12f, half * 1.35f, half * 0.28f);
    }

    /** Minimal HP bar (dark background + coloured fill). */
    private void drawMiniHpBar(float cx, float top, float width, float ratio, Color colour) {
        shape.setColor(0.1f, 0.1f, 0.1f, 0.7f);
        shape.rect(cx - width / 2f, top, width, 3.5f);
        shape.setColor(colour.r, colour.g, colour.b, 0.9f);
        shape.rect(cx - width / 2f, top, width * ratio, 3.5f);
    }

    // ── Button / border helpers (same style as MenuScreen) ────────────────────

    private void drawRoundedButton(Rectangle r, boolean hover) {
        float cr = CORNER_RADIUS;
        if (hover) {
            float g = cr * 1.4f;
            shape.setColor(C_GLOW);
            shape.rect(r.x - g,  r.y + cr, r.width + g * 2, r.height - cr * 2);
            shape.rect(r.x + cr, r.y - g,  r.width - cr * 2, r.height + g * 2);
            shape.circle(r.x + cr,           r.y + cr,            cr + g, 18);
            shape.circle(r.x + r.width - cr, r.y + cr,            cr + g, 18);
            shape.circle(r.x + cr,           r.y + r.height - cr, cr + g, 18);
            shape.circle(r.x + r.width - cr, r.y + r.height - cr, cr + g, 18);
        }
        shape.setColor(hover ? C_BTN_HOVER : C_BTN_FILL);
        shape.rect(r.x + cr, r.y,       r.width - cr * 2, r.height);
        shape.rect(r.x,      r.y + cr,  r.width,          r.height - cr * 2);
        shape.circle(r.x + cr,           r.y + cr,            cr, 18);
        shape.circle(r.x + r.width - cr, r.y + cr,            cr, 18);
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
        glyph.setText(font, text);
        float tx = r.x + (r.width  - glyph.width)  / 2f;
        float ty = r.y + (r.height + glyph.height) / 2f;
        font.setColor(0f, 0f, 0f, 0.55f);
        font.draw(batch, glyph, tx + 1.5f, ty - 1.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, glyph, tx, ty);
    }
}
