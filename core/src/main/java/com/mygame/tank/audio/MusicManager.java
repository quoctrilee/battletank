package com.mygame.tank.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public class MusicManager {
    public enum MusicTrack { NONE, BACKGROUND, BOSS_PHASE1, BOSS_PHASE2 }

    private static final float FADE_DURATION = 1.5f; // 1.5 seconds for crossfade
    private static final float MAX_VOLUME = 0.5f; // Adjust as needed

    private Music backgroundMusic;
    private Music bossMusic1;
    private Music bossMusic2;
    private Sound victorySound;
    private Sound loseSound;

    private final SfxManager sfxManager;

    private MusicTrack currentTrackEnum = MusicTrack.NONE;
    private Music currentTrack;
    private Music fadingTrack;

    private float fadeTimer = 0f;
    private boolean isFading = false;

    public MusicManager() {
        // Note: The typo in 'background.wav' is intentional based on the actual file name
        backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("music/background/background.wav"));
        backgroundMusic.setLooping(true);

        bossMusic1 = Gdx.audio.newMusic(Gdx.files.internal("music/background/boss_music_1.wav"));
        bossMusic1.setLooping(true);

        bossMusic2 = Gdx.audio.newMusic(Gdx.files.internal("music/background/boss_music_2.wav"));
        bossMusic2.setLooping(true);

        victorySound = Gdx.audio.newSound(Gdx.files.internal("music/sfx/victory.wav"));
        loseSound = Gdx.audio.newSound(Gdx.files.internal("music/sfx/lose.wav"));

        sfxManager = new SfxManager();
    }

    // ─── SFX ─────────────────────────────────────────────────────────────────

    /** Returns the weapon SFX manager. Use this to play per-weapon sounds. */
    public SfxManager getSfxManager() {
        return sfxManager;
    }

    public void playTrack(MusicTrack trackEnum) {
        if (this.currentTrackEnum == trackEnum) {
            return;
        }

        Music nextTrack = getMusicForTrack(trackEnum);
        this.currentTrackEnum = trackEnum;

        if (nextTrack == null) {
            // Fading out current track to nothing
            if (currentTrack != null) {
                fadingTrack = currentTrack;
                currentTrack = null;
                isFading = true;
                fadeTimer = FADE_DURATION;
            }
            return;
        }

        if (currentTrack != null) {
            // Stop any previous fading track immediately
            if (fadingTrack != null && fadingTrack.isPlaying()) {
                fadingTrack.stop();
            }

            // Current track becomes the fading track
            fadingTrack = currentTrack;
        }

        currentTrack = nextTrack;
        currentTrack.setVolume(0f);
        currentTrack.play();

        isFading = true;
        fadeTimer = FADE_DURATION;
    }

    private Music getMusicForTrack(MusicTrack track) {
        switch (track) {
            case BACKGROUND: return backgroundMusic;
            case BOSS_PHASE1: return bossMusic1;
            case BOSS_PHASE2: return bossMusic2;
            default: return null;
        }
    }

    public void update(float delta) {
        if (!isFading) return;

        fadeTimer -= delta;
        float progress = 1f - (Math.max(fadeTimer, 0f) / FADE_DURATION);

        if (currentTrack != null) {
            currentTrack.setVolume(progress * MAX_VOLUME);
        }

        if (fadingTrack != null) {
            fadingTrack.setVolume((1f - progress) * MAX_VOLUME);
        }

        if (fadeTimer <= 0f) {
            isFading = false;
            if (fadingTrack != null) {
                fadingTrack.stop();
                fadingTrack = null;
            }
            if (currentTrack != null) {
                currentTrack.setVolume(MAX_VOLUME);
            }
        }
    }

    public void playVictorySound() {
        stopAll();
        victorySound.play(1.0f);
    }

    public void playLoseSound() {
        stopAll();
        loseSound.play(1.0f);
    }

    public void stopAll() {
        if (backgroundMusic.isPlaying()) backgroundMusic.stop();
        if (bossMusic1.isPlaying()) bossMusic1.stop();
        if (bossMusic2.isPlaying()) bossMusic2.stop();

        currentTrack = null;
        fadingTrack = null;
        currentTrackEnum = MusicTrack.NONE;
        isFading = false;
    }

    public void dispose() {
        stopAll();
        backgroundMusic.dispose();
        bossMusic1.dispose();
        bossMusic2.dispose();
        victorySound.dispose();
        loseSound.dispose();
        sfxManager.dispose();
    }
}
