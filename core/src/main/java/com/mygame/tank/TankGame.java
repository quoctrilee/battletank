package com.mygame.tank;

import com.badlogic.gdx.Game;
import com.mygame.tank.audio.MusicManager;
import com.mygame.tank.config.ControlsConfig;
import com.mygame.tank.screen.MenuScreen;

/**
 * Application entry-point.
 *
 * <p>Holds a single {@link MusicManager} instance that is shared across all
 * screens so that crossfades work correctly when switching between Menu,
 * Game, and Settings screens without music duplication or abrupt cuts.
 */
public class TankGame extends Game {

    private MusicManager musicManager;

    @Override
    public void create() {
        // Load persisted controls settings before any screen is created.
        ControlsConfig.load();

        // Music manager is created once and shared — avoids duplicate audio tracks.
        musicManager = new MusicManager();

        setScreen(new MenuScreen(this));
    }

    /** Shared {@link MusicManager} — use this from every screen. */
    public MusicManager getMusicManager() { return musicManager; }

    // ─── Android lifecycle passthrough ────────────────────────────────────────

    @Override
    public void pause() {
        super.pause();
        if (musicManager != null) musicManager.pauseAll();
    }

    @Override
    public void resume() {
        super.resume();
        if (musicManager != null) musicManager.resumeAll();
    }

    @Override
    public void dispose() {
        super.dispose();
        if (musicManager != null) {
            musicManager.dispose();
            musicManager = null;
        }
    }
}
