package com.mygame.tank.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.mygame.tank.weapon.WeaponType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Manages and plays one-shot weapon SFX.
 *
 * <p>LibGDX's {@link Sound} API (OpenAL) only supports WAV, MP3, and OGG.
 * FLAC files are loaded via the {@link Music} API instead and played as
 * one-shots (seek-to-start + play). This is transparent to callers.
 *
 * <p>Call {@link #playWeaponSfx(WeaponType)} whenever a weapon fires.
 * Call {@link #dispose()} when the screen is destroyed.
 */
public class SfxManager {

    // ─── Internal clip abstraction ────────────────────────────────────────────

    /**
     * Uniform interface over {@link Sound} (WAV/MP3/OGG) and {@link Music} (FLAC).
     */
    private interface SfxClip {
        void play(float volume);
        void dispose();
    }

    /** Wraps {@link Sound} — supports polyphony natively. */
    private static final class SoundClip implements SfxClip {
        private final Sound sound;
        SoundClip(Sound sound) { this.sound = sound; }
        @Override public void play(float volume) { sound.play(volume); }
        @Override public void dispose() { sound.dispose(); }
    }

    /**
     * Wraps {@link Music} as a one-shot clip.
     * Music does not support polyphony; for weapon SFX this is acceptable.
     */
    private static final class MusicClip implements SfxClip {
        private final Music music;
        MusicClip(Music music) {
            this.music = music;
            this.music.setLooping(false);
        }
        @Override public void play(float volume) {
            music.stop();
            music.setVolume(volume);
            music.setPosition(0f);
            music.play();
        }
        @Override public void dispose() { music.dispose(); }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    public static final float DEFAULT_VOLUME = 0.8f;

    private final Map<WeaponType, SfxClip> weaponClips = new EnumMap<>(WeaponType.class);
    private final List<SfxClip> allClips = new ArrayList<>();
    private float sfxVolume = DEFAULT_VOLUME;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SfxManager() {
        // LibGDX Sound supports: WAV, MP3, OGG
        // LibGDX Music supports: WAV, MP3, OGG, FLAC (via Java Sound / platform decoders)
        // → .flac files use MusicClip; everything else uses SoundClip.
        loadClip(WeaponType.DEFAULT_BULLET, "music/sfx/DEFAULT_BULLET.wav");
        loadClip(WeaponType.SUBMACHINE_GUN, "music/sfx/SUBMACHINE_GUN.wav");
        loadClip(WeaponType.ARMOR_PIERCE,   "music/sfx/ARMOR_PIERCE.wav");
        loadClip(WeaponType.CANNON,         "music/sfx/CANNON.wav");
        loadClip(WeaponType.STUN_SHELL,     "music/sfx/STUN_SHELL.wav");
        loadClip(WeaponType.LASER,          "music/sfx/LAZER.mp3");
        loadClip(WeaponType.HOMING_MISSILE, "music/sfx/HOMING_MISSILE.wav");
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    /**
     * Plays the SFX associated with the given weapon type.
     *
     * @param type the weapon being fired; ignored if null or has no mapped clip.
     */
    public void playWeaponSfx(WeaponType type) {
        if (type == null) return;
        SfxClip clip = weaponClips.get(type);
        if (clip != null) clip.play(sfxVolume);
    }

    // ─── Volume ───────────────────────────────────────────────────────────────

    /**
     * Sets the global SFX volume for all weapon sounds.
     *
     * @param volume value in [0.0, 1.0]
     */
    public void setSfxVolume(float volume) {
        this.sfxVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getSfxVolume() {
        return sfxVolume;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void dispose() {
        for (SfxClip clip : allClips) clip.dispose();
        allClips.clear();
        weaponClips.clear();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Loads the file as a {@link MusicClip} for FLAC, or {@link SoundClip} for
     * WAV/MP3/OGG. Falls back gracefully on error (logs but does not crash).
     */
    private void loadClip(WeaponType type, String internalPath) {
        try {
            SfxClip clip;
            if (internalPath.toLowerCase().endsWith(".flac")) {
                clip = new MusicClip(Gdx.audio.newMusic(Gdx.files.internal(internalPath)));
            } else {
                clip = new SoundClip(Gdx.audio.newSound(Gdx.files.internal(internalPath)));
            }
            weaponClips.put(type, clip);
            allClips.add(clip);
        } catch (Exception e) {
            Gdx.app.error("SfxManager", "Failed to load SFX: " + internalPath, e);
        }
    }
}
