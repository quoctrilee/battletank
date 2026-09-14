package com.mygame.tank.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.mygame.tank.TankGame;
import com.mygame.tank.audio.MusicManager;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.player.AndroidInputSource;
import com.mygame.tank.controller.player.AndroidUiSkin;
import com.mygame.tank.controller.player.InputSourceFactory;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.controller.player.PlayerInputSource;
import com.mygame.tank.data.GameHistoryManager;
import com.mygame.tank.data.GameRecord;
import com.mygame.tank.dungeon.DungeonMap;
import com.mygame.tank.render.DebugRenderer;
import com.mygame.tank.render.GameRenderer;
import com.mygame.tank.world.GameWorld;

/**
 * Main game screen.
 *
 * <p>Sinh dungeon mỗi khi bắt đầu/restart màn chơi.
 *
 * <h3>Input priority</h3>
 * {@link GameInputAdapter} is placed FIRST in the {@link InputMultiplexer}:
 * <ol>
 *   <li>Settings button (top-right ☰) — tap to toggle pause overlay</li>
 *   <li>Pause menu buttons (Back / Mute / Controls) — consume all touches while paused</li>
 *   <li>Click outside pause panel → close pause without triggering game input</li>
 * </ol>
 * The adapter is stored as a field and re-registered in {@link #show()} so returning
 * from {@link ControlsSettingsScreen} or any other sub-screen always restores correct input.
 */
public class GameScreen implements Screen {

    private final TankGame           game;
    private final OrthographicCamera camera;
    private final PlayerInputSource  inputSource;
    private final Stage              uiStage;
    private final AndroidUiSkin      androidUiSkin; // null on Desktop
    private final GameRenderer       gameRenderer;
    private final DebugRenderer      debugRenderer;
    /** Stored so show() can re-register without creating a new instance. */
    private final GameInputAdapter   gameInputAdapter;
    private final boolean            isAndroid;

    private DungeonMap dungeonMap;
    private GameWorld  world;
    private boolean    hasPlayedEndSound = false;
    private boolean    hasRecordedResult = false;

    /**
     * Accumulated play time in seconds — only incremented while state == PLAYING
     * and not paused.
     */
    private float gameTimer = 0f;

    /** Whether the in-game pause overlay is currently shown. */
    private boolean isPaused = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public GameScreen(TankGame game) {
        this.game     = game;
        this.isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        camera = new OrthographicCamera();
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);

        gameRenderer     = new GameRenderer();
        debugRenderer    = new DebugRenderer(GameConfig.DEBUG_DEFAULT);
        gameInputAdapter = new GameInputAdapter();

        uiStage       = new Stage(new ScreenViewport());
        androidUiSkin = isAndroid ? new AndroidUiSkin() : null;

        inputSource = InputSourceFactory.create(
                uiStage,
                androidUiSkin != null ? androidUiSkin.getSkin() : null);

        if (isAndroid && inputSource instanceof AndroidInputSource) {
            gameRenderer.setAndroidInputSource((AndroidInputSource) inputSource);
        }

        registerInputProcessor();
    }

    // ─── Input processor registration ─────────────────────────────────────────

    /**
     * Registers (or re-registers) the input multiplexer.
     * Called from the constructor AND from {@link #show()} so that returning
     * from sub-screens (ControlsSettingsScreen, etc.) always restores correct input.
     */
    private void registerInputProcessor() {
        if (isAndroid && inputSource instanceof AndroidInputSource) {
            Gdx.input.setInputProcessor(
                new InputMultiplexer(gameInputAdapter, uiStage, (AndroidInputSource) inputSource));
        } else {
            Gdx.input.setInputProcessor(
                new InputMultiplexer(gameInputAdapter, uiStage));
        }
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        ensureWorld();
        // CRITICAL: re-register input processor after returning from any sub-screen
        // (ControlsSettingsScreen sets its own InputProcessor in show(); without this
        //  the game input would remain broken after navigating back).
        registerInputProcessor();
        // Pick up any split-ratio change made in ControlsSettingsScreen
        if (inputSource instanceof AndroidInputSource) {
            ((AndroidInputSource) inputSource).refreshSplitRatio();
        }
    }

    @Override
    public void render(float delta) {
        ensureWorld();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        MusicManager mm = game.getMusicManager();

        // Keyboard shortcuts (desktop)
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) isPaused = !isPaused;
        if (Gdx.input.isKeyJustPressed(Keys.R) && !isPaused) restartGame();
        if (Gdx.input.isKeyJustPressed(Keys.F1)) debugRenderer.toggle();

        if (!isPaused) {
            PlayerInput input = inputSource.read(camera, world.getPlayer().getPosition());
            world.update(delta, input);
            syncInputSourceState();

            GameWorld.GameState state = world.getGameState();

            if (state == GameWorld.GameState.PLAYING) {
                mm.playTrack(world.getCurrentMusicTrack());
                gameTimer += delta; // only count time while actively playing
            } else {
                if (!hasPlayedEndSound) {
                    hasPlayedEndSound = true;
                    if      (state == GameWorld.GameState.WIN)  mm.playVictorySound();
                    else if (state == GameWorld.GameState.LOSE) mm.playLoseSound();
                }
                // Save result exactly once when the game ends
                if (!hasRecordedResult) {
                    hasRecordedResult = true;
                    String result = state == GameWorld.GameState.WIN ? "WIN" : "LOSE";
                    new GameHistoryManager().save(
                        new GameRecord(result, (int) gameTimer, System.currentTimeMillis()));
                }
            }

            updateCamera(delta);
        }

        // Music fades run even while paused
        mm.update(delta);

        // Push current pause/mute state so renderer draws the correct overlay & button style
        gameRenderer.setPauseState(isPaused, mm.isMuted());
        gameRenderer.setGameTimer(gameTimer);

        gameRenderer.render(camera, world);
        debugRenderer.render(camera, world);

        if (world != null && world.getGameState() == GameWorld.GameState.PLAYING) {
            uiStage.act(delta);
            uiStage.draw();
        }
    }

    @Override public void hide()   { /* world preserved for return-from-settings */ }
    @Override public void pause()  { game.getMusicManager().pauseAll(); }
    @Override public void resume() { game.getMusicManager().resumeAll(); }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, GameConfig.VIEWPORT_WIDTH, GameConfig.VIEWPORT_HEIGHT);
        camera.update();
        gameRenderer.onResize();
        uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        Gdx.input.setInputProcessor(null);
        if (dungeonMap != null) {
            dungeonMap.dispose();
            dungeonMap = null;
        }
        gameRenderer.dispose();
        debugRenderer.dispose();
        uiStage.dispose();
        if (androidUiSkin != null) androidUiSkin.dispose();
        // MusicManager is owned by TankGame — do NOT dispose here.
    }

    // ─── Camera ───────────────────────────────────────────────────────────────

    private void updateCamera(float delta) {
        float targetX = world.getPlayer().getPosition().x;
        float targetY = world.getPlayer().getPosition().y;

        float halfW = camera.viewportWidth  / 2f;
        float halfH = camera.viewportHeight / 2f;
        targetX = MathUtils.clamp(targetX, halfW, GameConfig.MAP_WIDTH  - halfW);
        targetY = MathUtils.clamp(targetY, halfH, GameConfig.MAP_HEIGHT - halfH);

        float lerp = 1f - (float) Math.exp(-GameConfig.CAMERA_LERP * delta);
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
        hasPlayedEndSound = false;
        hasRecordedResult = false;
        isPaused          = false;
        gameTimer         = 0f;
        game.getMusicManager().stopAll();
        createWorld();
    }

    private void createWorld() {
        gameRenderer.reloadPlayerBody();
        dungeonMap = new DungeonMap();
        dungeonMap.generate(System.currentTimeMillis());
        world = new GameWorld(dungeonMap, game.getMusicManager().getSfxManager());
        syncInputSourceState();

        float px = world.getPlayer().getPosition().x;
        float py = world.getPlayer().getPosition().y;
        camera.position.set(
            MathUtils.clamp(px, camera.viewportWidth  / 2f, GameConfig.MAP_WIDTH  - camera.viewportWidth  / 2f),
            MathUtils.clamp(py, camera.viewportHeight / 2f, GameConfig.MAP_HEIGHT - camera.viewportHeight / 2f),
            0f);
        camera.update();
    }

    private void syncInputSourceState() {
        if (world != null) inputSource.setHubOpen(world.isPlayerHubOpen());
    }

    // ─── Inner: GameInputAdapter ──────────────────────────────────────────────

    /**
     * Highest-priority input handler placed first in the InputMultiplexer.
     *
     * <ul>
     *   <li>Taps on the settings button (top-right) → toggle pause regardless of game state.</li>
     *   <li>When paused → handle pause-menu buttons; clicking outside the panel closes the menu.</li>
     *   <li>When paused → consume ALL touches so joysticks / fire zone do not activate.</li>
     * </ul>
     */
    public class GameInputAdapter extends InputAdapter {

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            // Convert raw screen Y (0=top) to HUD Y (0=bottom) for rect containment checks
            float hudX = screenX;
            float hudY = sh - screenY;

            // ── Game over / victory — any tap returns to main menu ────────────
            if (world != null) {
                GameWorld.GameState gs = world.getGameState();
                if (gs == GameWorld.GameState.WIN || gs == GameWorld.GameState.LOSE) {
                    game.getMusicManager().stopAll();
                    Gdx.input.setInputProcessor(null);
                    dispose();
                    game.setScreen(new MenuScreen(game));
                    return true;
                }
            }

            // ── Settings button — highest priority, always processed ───────────
            if (gameRenderer.isSettingsHit(hudX, hudY)) {
                isPaused = !isPaused;
                return true; // consumed — prevents fire zone from also triggering
            }

            // ── Pause menu handling ───────────────────────────────────────────
            if (isPaused) {
                MusicManager mm = game.getMusicManager();

                if (gameRenderer.getPauseBackRect().contains(hudX, hudY)) {
                    // Back to Main Menu
                    isPaused = false;
                    mm.stopAll();
                    Gdx.input.setInputProcessor(null);
                    dispose();
                    game.setScreen(new MenuScreen(game));
                    return true;
                }
                if (gameRenderer.getPauseMuteRect().contains(hudX, hudY)) {
                    mm.setMuted(!mm.isMuted());
                    return true;
                }
                if (gameRenderer.getPauseControlsRect().contains(hudX, hudY)) {
                    // Open controls settings — game is effectively paused by screen switch
                    isPaused = false; // reset so we return clean
                    game.setScreen(new ControlsSettingsScreen(game, GameScreen.this));
                    return true;
                }

                // Click outside all buttons → close pause menu
                isPaused = false;
                return true; // still consume to prevent joystick/fire activation
            }

            return false; // not paused, not settings → let other processors handle
        }
    }
}
