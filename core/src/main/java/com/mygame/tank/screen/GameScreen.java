package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.controller.player.PlayerInputHandler;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.render.DebugRenderer;
import com.mygame.tank.render.GameRenderer;
import com.mygame.tank.world.GameWorld;

/**
 * Main game screen.
 *
 * <p>Sinh dungeon mỗi khi bắt đầu/restart màn chơi.
 * Camera smooth-follow player bằng lerp.
 */
public class GameScreen implements Screen {

    private final OrthographicCamera  camera;
    private final PlayerInputHandler  inputHandler;
    private final GameRenderer        gameRenderer;
    private final DebugRenderer       debugRenderer;
    private DungeonMap dungeonMap;
    private GameWorld  world;

    public GameScreen() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);

        gameRenderer  = new GameRenderer();
        debugRenderer = new DebugRenderer(GameConfig.DEBUG_DEFAULT);
        inputHandler  = new PlayerInputHandler();
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        ensureWorld();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        PlayerInput input = inputHandler.read(camera);

        if (Gdx.input.isKeyJustPressed(Keys.R)) restartGame();
        if (Gdx.input.isKeyJustPressed(Keys.F1)) debugRenderer.toggle();

        world.update(delta, input);
        syncInputHandlerState();

        updateCamera(delta);

        gameRenderer.render(camera, world);
        debugRenderer.render(camera, world);
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
    }

    @Override
    public void dispose() {
        if (dungeonMap != null) dungeonMap.dispose();
        gameRenderer.dispose();
        debugRenderer.dispose();
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
        syncInputHandlerState();

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

    private void syncInputHandlerState() {
        if (world != null) inputHandler.setHubOpen(world.isPlayerHubOpen());
    }
}
