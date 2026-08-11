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
import com.mygame.tank.render.DebugRenderer;
import com.mygame.tank.render.GameRenderer;
import com.mygame.tank.world.GameWorld;
import com.mygame.tank.world.MapManager;

/**
 * The main game screen.
 * <p>
 * Responsibilities:
 * - Manage the OrthographicCamera (follow player, clamp to map)
 * - Read input each frame via PlayerInputHandler
 * - Delegate all gameplay logic to GameWorld
 * - Delegate all rendering to GameRenderer / DebugRenderer
 * <p>
 * No gameplay logic lives here.
 */
public class GameScreen implements Screen {

    private final OrthographicCamera camera;
    private final PlayerInputHandler inputHandler;
    private final MapManager mapManager;
    private final GameRenderer gameRenderer;
    private final DebugRenderer debugRenderer;
    private GameWorld world;

    public GameScreen() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);

        mapManager = new MapManager();
        mapManager.load("maps/map1.tmx");

        gameRenderer = new GameRenderer();
        debugRenderer = new DebugRenderer(GameConfig.DEBUG_DEFAULT);
        inputHandler = new PlayerInputHandler();
    }

    @Override
    public void render(float delta) {
        ensureWorld();

        // Clear screen
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Read input
        PlayerInput input = inputHandler.read(camera);

        // Handle global keys
        if (Gdx.input.isKeyJustPressed(Keys.R)) restartGame();
        if (Gdx.input.isKeyJustPressed(Keys.F1)) debugRenderer.toggle();

        // Update gameplay
        world.update(delta, input);
        syncInputHandlerState();

        // Camera follows player
        updateCamera();

        // Render
        gameRenderer.render(camera, world);
        debugRenderer.render(camera, world);
    }

    private void updateCamera() {
        float cx = world.getPlayer().getPosition().x;
        float cy = world.getPlayer().getPosition().y;

        // Clamp camera so we never show outside the map
        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;
        cx = MathUtils.clamp(cx, halfW, GameConfig.MAP_WIDTH - halfW);
        cy = MathUtils.clamp(cy, halfH, GameConfig.MAP_HEIGHT - halfH);

        camera.position.set(cx, cy, 0f);
        camera.update();
    }

    private void restartGame() {
        createWorld();
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        camera.update();
    }

    @Override
    public void show() {
        ensureWorld();
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        mapManager.dispose();
        gameRenderer.dispose();
        debugRenderer.dispose();
    }

    private void ensureWorld() {
        if (world == null) {
            createWorld();
        }
    }

    private void createWorld() {
        world = new GameWorld(mapManager);
        syncInputHandlerState();
    }

    private void syncInputHandlerState() {
        if (world != null) {
            inputHandler.setHubOpen(world.isPlayerHubOpen());
        }
    }
}
