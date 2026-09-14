package com.mygame.tank.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

/**
 * Centralised music & SFX manager shared by all screens via {@link com.mygame.tank.TankGame}.
 *
 * <h3>Tracks</h3>
 * <ul>
 *   <li>{@link MusicTrack#MENU}        — main-menu background music</li>
 *   <li>{@link MusicTrack#BACKGROUND}  — in-game exploration</li>
 *   <li>{@link MusicTrack#BOSS_PHASE1} — boss phase 1</li>
 *   <li>{@link MusicTrack#BOSS_PHASE2} — boss phase 2 / enraged</li>
 *   <li>{@link MusicTrack#NONE}        — silence (fade out current track)</li>
 * </ul>
 *
 * <h3>Mute</h3>
 * Call {@link #setMuted(boolean)} to silence all music without stopping playback.
 * SFX volume is also zeroed when muted.
 *
 * <h3>Android lifecycle</h3>
 * Call {@link #pauseAll()} / {@link #resumeAll()} from your screen's
 * {@code pause()} / {@code resume()} to maintain correct behaviour when the
 * app goes to the background.
 */
public class MusicManager {

    // ─── Track enum ───────────────────────────────────────────────────────────

    public enum MusicTrack { NONE, MENU, BACKGROUND, BOSS_PHASE1, BOSS_PHASE2 }

    // ─── Constants ────────────────────────────────────────────────────────────

    private static final float FADE_DURATION = 1.5f;
    private static final float MAX_VOLUME    = 0.5f;

    // ─── Music assets ─────────────────────────────────────────────────────────

    private Music menuMusic;
    private Music backgroundMusic;
    private Music bossMusic1;
    private Music bossMusic2;
    private Sound victorySound;
    private Sound loseSound;

    // ─── Playback state ───────────────────────────────────────────────────────

    private final SfxManager sfxManager;

    private MusicTrack currentTrackEnum = MusicTrack.NONE;
    private Music      currentTrack;
    private Music      fadingTrack;

    private float   fadeTimer = 0f;
    private boolean isFading  = false;
    private boolean muted     = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public MusicManager() {
        menuMusic = Gdx.audio.newMusic(Gdx.files.internal("music/menu/menu.mp3"));
        menuMusic.setLooping(true);

        backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("music/background/background.wav"));
        backgroundMusic.setLooping(true);

        bossMusic1 = Gdx.audio.newMusic(Gdx.files.internal("music/background/boss_music_1.wav"));
        bossMusic1.setLooping(true);

        bossMusic2 = Gdx.audio.newMusic(Gdx.files.internal("music/background/boss_music_2.wav"));
        bossMusic2.setLooping(true);

        victorySound = Gdx.audio.newSound(Gdx.files.internal("music/sfx/victory.wav"));
        loseSound    = Gdx.audio.newSound(Gdx.files.internal("music/sfx/lose.wav"));

        sfxManager = new SfxManager();
    }

    // ─── SFX ──────────────────────────────────────────────────────────────────

    /** Returns the weapon SFX manager. */
    public SfxManager getSfxManager() { return sfxManager; }

    // ─── Track switching ──────────────────────────────────────────────────────

    public void playTrack(MusicTrack trackEnum) {
        if (this.currentTrackEnum == trackEnum) return;

        Music nextTrack = getMusicForTrack(trackEnum);
        this.currentTrackEnum = trackEnum;

        if (nextTrack == null) {
            // Fade out current to silence
            if (currentTrack != null) {
                fadingTrack  = currentTrack;
                currentTrack = null;
                isFading     = true;
                fadeTimer    = FADE_DURATION;
            }
            return;
        }

        if (currentTrack != null) {
            if (fadingTrack != null && fadingTrack.isPlaying()) fadingTrack.stop();
            fadingTrack = currentTrack;
        }

        currentTrack = nextTrack;
        currentTrack.setVolume(0f);
        currentTrack.play();

        isFading  = true;
        fadeTimer = FADE_DURATION;
    }

    private Music getMusicForTrack(MusicTrack track) {
        switch (track) {
            case MENU:        return menuMusic;
            case BACKGROUND:  return backgroundMusic;
            case BOSS_PHASE1: return bossMusic1;
            case BOSS_PHASE2: return bossMusic2;
            default:          return null;
        }
    }

    // ─── Update (call every frame) ────────────────────────────────────────────

    public void update(float delta) {
        if (!isFading) return;

        fadeTimer -= delta;
        float progress = 1f - (Math.max(fadeTimer, 0f) / FADE_DURATION);

        if (currentTrack != null) {
            currentTrack.setVolume(muted ? 0f : progress * MAX_VOLUME);
        }
        if (fadingTrack != null) {
            fadingTrack.setVolume(muted ? 0f : (1f - progress) * MAX_VOLUME);
        }

        if (fadeTimer <= 0f) {
            isFading = false;
            if (fadingTrack != null) {
                fadingTrack.stop();
                fadingTrack = null;
            }
            if (currentTrack != null) {
                currentTrack.setVolume(muted ? 0f : MAX_VOLUME);
            }
        }
    }

    // ─── One-shot sounds ──────────────────────────────────────────────────────

    public void playVictorySound() {
        stopAll();
        if (!muted) victorySound.play(1.0f);
    }

    public void playLoseSound() {
        stopAll();
        if (!muted) loseSound.play(1.0f);
    }

    // ─── Stop / pause / resume ────────────────────────────────────────────────

    public void stopAll() {
        if (menuMusic       .isPlaying()) menuMusic       .stop();
        if (backgroundMusic .isPlaying()) backgroundMusic .stop();
        if (bossMusic1      .isPlaying()) bossMusic1      .stop();
        if (bossMusic2      .isPlaying()) bossMusic2      .stop();

        currentTrack     = null;
        fadingTrack      = null;
        currentTrackEnum = MusicTrack.NONE;
        isFading         = false;
    }

    /** Pause active tracks — call from screen's {@code pause()} on Android. */
    public void pauseAll() {
        if (currentTrack != null && currentTrack.isPlaying()) currentTrack.pause();
        if (fadingTrack  != null && fadingTrack .isPlaying()) fadingTrack .pause();
    }

    /** Resume active tracks — call from screen's {@code resume()} on Android. */
    public void resumeAll() {
        if (!muted && currentTrack != null && !currentTrack.isPlaying()) currentTrack.play();
    }

    // ─── Mute ─────────────────────────────────────────────────────────────────

    /**
     * Mutes or unmutes all music and SFX.
     * Tracks remain in their current play/pause state; only volume is adjusted.
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
        float vol = muted ? 0f : MAX_VOLUME;
        if (currentTrack != null) currentTrack.setVolume(vol);
        if (fadingTrack  != null) fadingTrack .setVolume(muted ? 0f : fadingTrack.getVolume());
        sfxManager.setSfxVolume(muted ? 0f : SfxManager.DEFAULT_VOLUME);
    }

    public boolean isMuted() { return muted; }

    // ─── Dispose ──────────────────────────────────────────────────────────────

    public void dispose() {
        stopAll();
        menuMusic      .dispose();
        backgroundMusic.dispose();
        bossMusic1     .dispose();
        bossMusic2     .dispose();
        victorySound   .dispose();
        loseSound      .dispose();
        sfxManager     .dispose();
    }
}
