package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.player.AndroidInputSource;
import com.mygame.tank.controller.player.AndroidUiSkin;
import com.mygame.tank.controller.player.InputSourceFactory;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.controller.player.PlayerInputSource;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.render.DebugRenderer;
import com.mygame.tank.render.GameRenderer;
import com.mygame.tank.world.GameWorld;

/**
 * Main game screen.
 *
 * <p>Sinh dungeon mỗi khi bắt đầu/restart màn chơi.
 * Camera smooth-follow player bằng lerp.
 *
 * <h3>Input</h3>
 * <p>Uses {@link PlayerInputSource} (via {@link InputSourceFactory}) so the same
 * screen works on both Desktop (WASD + mouse) and Android (on-screen dual sticks).
 * On Android, the {@link Stage} hosts the touchpad/button widgets rendered each frame.
 */
public class GameScreen implements Screen {

    private final OrthographicCamera  camera;
    private final PlayerInputSource   inputSource;
    private final Stage               uiStage;
    private final AndroidUiSkin       androidUiSkin; // null on Desktop
    private final GameRenderer        gameRenderer;
    private final DebugRenderer       debugRenderer;
    private DungeonMap dungeonMap;
    private GameWorld  world;

    public GameScreen() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);

        gameRenderer  = new GameRenderer();
        debugRenderer = new DebugRenderer(GameConfig.DEBUG_DEFAULT);

        // ── UI Stage (needed for Android on-screen controls; harmless on Desktop) ──
        uiStage = new Stage(new ScreenViewport());

        // ── Build skin only on Android to avoid unnecessary Pixmap work on Desktop ──
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        androidUiSkin = isAndroid ? new AndroidUiSkin() : null;

        // ── Platform-aware input source ────────────────────────────────────────
        inputSource = InputSourceFactory.create(
                uiStage,
                androidUiSkin != null ? androidUiSkin.getSkin() : null);

        // On Android: use an InputMultiplexer so Stage handles equip/hub buttons
        // first, and AndroidInputSource receives all unhandled raw touch events
        // for the floating joystick + fire zones.
        if (isAndroid && inputSource instanceof AndroidInputSource) {
            AndroidInputSource androidSrc = (AndroidInputSource) inputSource;
            // Tell GameRenderer to draw the joystick overlay
            gameRenderer.setAndroidInputSource(androidSrc);
            // Multiplexer: Stage first (buttons), then raw touch handler
            Gdx.input.setInputProcessor(new InputMultiplexer(uiStage, androidSrc));
        } else {
            // Desktop: Stage is empty — just needs to receive events for safety
            Gdx.input.setInputProcessor(uiStage);
        }
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        ensureWorld();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Read input — always supply tank position so Android can compute aim point.
        PlayerInput input = inputSource.read(camera, world.getPlayer().getPosition());

        if (Gdx.input.isKeyJustPressed(Keys.R)) restartGame();
        if (Gdx.input.isKeyJustPressed(Keys.F1)) debugRenderer.toggle();

        world.update(delta, input);
        syncInputSourceState();

        updateCamera(delta);

        gameRenderer.render(camera, world);
        debugRenderer.render(camera, world);

        // Draw on-screen UI (touchpads, buttons) on top of everything else.
        uiStage.act(delta);
        uiStage.draw();
    }

    @Override public void show()   { ensureWorld(); }
    @Override public void hide()   { }
    @Override public void pause()  { }
    @Override public void resume() { }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        camera.update();
        gameRenderer.onResize();
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (dungeonMap != null) dungeonMap.dispose();
        gameRenderer.dispose();
        debugRenderer.dispose();
        uiStage.dispose();
        if (androidUiSkin != null) androidUiSkin.dispose();
    }

    // ─── Camera: smooth follow bằng lerp ─────────────────────────────────────

    private void updateCamera(float delta) {
        float targetX = world.getPlayer().getPosition().x;
        float targetY = world.getPlayer().getPosition().y;

        // Clamp target trong biên map
        float halfW = camera.viewportWidth  / 2f;
        float halfH = camera.viewportHeight / 2f;
        targetX = MathUtils.clamp(targetX, halfW, GameConfig.MAP_WIDTH  - halfW);
        targetY = MathUtils.clamp(targetY, halfH, GameConfig.MAP_HEIGHT - halfH);

        // Lerp mượt: hệ số CAMERA_LERP cao = theo nhanh hơn
        float lerp = 1f - (float)Math.exp(-GameConfig.CAMERA_LERP * delta);
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;

        camera.update();
    }

    // ─── World lifecycle ──────────────────────────────────────────────────────

    private void ensureWorld() {
        if (world == null) createWorld();
    }

    private void restartGame() {
        if (dungeonMap != null) dungeonMap.dispose();
        createWorld();
    }

    private void createWorld() {
        // Sinh dungeon mới với seed ngẫu nhiên mỗi lần chơi
        dungeonMap = new DungeonMap();
        dungeonMap.generate(System.currentTimeMillis());

        world = new GameWorld(dungeonMap);
        syncInputSourceState();

        // Snap camera ngay đến player khi bắt đầu
        float px = world.getPlayer().getPosition().x;
        float py = world.getPlayer().getPosition().y;
        camera.position.set(
            MathUtils.clamp(px, camera.viewportWidth / 2f, GameConfig.MAP_WIDTH  - camera.viewportWidth / 2f),
            MathUtils.clamp(py, camera.viewportHeight / 2f, GameConfig.MAP_HEIGHT - camera.viewportHeight / 2f),
            0f
        );
        camera.update();
    }

    /**
     * Mirrors hub-open state from {@link GameWorld} back to the input source so that
     * {@link com.mygame.tank.controller.player.DesktopInputSource} can disambiguate
     * keys 1-7 (equip vs. hub-slot), and
     * {@link com.mygame.tank.controller.player.AndroidInputSource} can swap the
     * equip/hub-slot button row.
     */
    private void syncInputSourceState() {
        if (world != null) inputSource.setHubOpen(world.isPlayerHubOpen());
    }
}
