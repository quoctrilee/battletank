package com.mygame.tank.controller;

import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.player.PlayerInput;
import com.mygame.tank.entity.LaserBeam;
import com.mygame.tank.entity.VisualEffect;
import com.mygame.tank.entity.WorldEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame context passed to every {@link TankController}.
 *
 * <p>Player controllers receive a {@link PlayerInput} snapshot.
 * Enemy / boss controllers receive the player's world position and alive flag.
 *
 * <p>The mutable {@code pending*} lists allow the PlayerController to propagate
 * laser beams and world effects back to GameWorld without breaking the
 * {@link TankController} return type (which is fixed as {@code List<Projectile>}).
 */
public final class UpdateContext {

    /**
     * World position of the player tank (null for player-controller context).
     */
    public final Vector2 playerPos;

    /**
     * Whether the player is currently alive.
     */
    public final boolean playerAlive;

    /**
     * Whether this enemy's area door is open.
     * Prevents enemies in locked rooms from detecting the player through walls.
     */
    public final boolean areaActive;

    /**
     * Whether the player is currently inside an active SmokeBomb cloud.
     * When true, enemies in IDLE cannot initiate detection, enemies in CHASE/ATTACK
     * treat the player as lost and begin the {@code ENEMY_LOST_TIME} countdown.
     * Always false for the player-controller context.
     */
    public final boolean playerInSmoke;

    /**
     * Raw input snapshot — non-null only for the player controller.
     */
    public final PlayerInput playerInput;

    private final List<LaserBeam> pendingBeams;
    private final List<WorldEffect> pendingEffects;
    private final List<VisualEffect> pendingVfx;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Context for enemy / boss controllers (no pending output lists).
     * {@code playerInSmoke} defaults to false — use the overload below when
     * GameWorld has determined the player is inside a SmokeBomb cloud.
     */
    public UpdateContext(Vector2 playerPos, boolean playerAlive, boolean areaActive) {
        this(playerPos, playerAlive, areaActive, false);
    }

    /**
     * Context for enemy / boss controllers with smoke state.
     * <p>
     * GameWorld should pass {@code playerInSmoke = true} whenever the player
     * overlaps any active {@link com.mygame.tank.entity.WorldEffect.SmokeBomb}.
     * This lets {@link com.mygame.tank.controller.ai.EnemyController} suppress
     * detection and force the lost-timer to run, without querying world state
     * directly.
     */
    public UpdateContext(Vector2 playerPos, boolean playerAlive, boolean areaActive,
                         boolean playerInSmoke) {
        this.playerPos = playerPos;
        this.playerAlive = playerAlive;
        this.areaActive = areaActive;
        this.playerInSmoke = playerInSmoke;
        this.playerInput = null;
        this.pendingBeams = new ArrayList<>();
        this.pendingEffects = new ArrayList<>();
        this.pendingVfx = new ArrayList<>();
    }

    /**
     * Context for the player controller — includes output lists for beams/effects/vfx.
     */
    public UpdateContext(PlayerInput playerInput,
                         List<LaserBeam> pendingBeams,
                         List<WorldEffect> pendingEffects,
                         List<VisualEffect> pendingVfx) {
        this.playerPos = null;
        this.playerAlive = true;
        this.areaActive = false;
        this.playerInSmoke = false;   // irrelevant for the player's own controller
        this.playerInput = playerInput;
        this.pendingBeams = pendingBeams;
        this.pendingEffects = pendingEffects;
        this.pendingVfx = pendingVfx;
    }

    public void addLaserBeams(List<LaserBeam> beams) {
        pendingBeams.addAll(beams);
    }

    public void addWorldEffects(List<WorldEffect> effects) {
        pendingEffects.addAll(effects);
    }

    public void addVisualEffects(List<VisualEffect> effects) {
        pendingVfx.addAll(effects);
    }

    public List<LaserBeam> getPendingBeams() {
        return pendingBeams;
    }

    public List<WorldEffect> getPendingEffects() {
        return pendingEffects;
    }

    public List<VisualEffect> getPendingVfx() {
        return pendingVfx;
    }
}
