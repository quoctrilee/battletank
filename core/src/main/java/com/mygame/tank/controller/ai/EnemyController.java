package com.mygame.tank.controller.ai;

import com.mygame.tank.config.GameConfig;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.Collections;
import java.util.List;

/**
 * Basic enemy AI controller — 3-state FSM: IDLE → CHASE → ATTACK.
 * <p>
 * Dead state is not tracked here; it is reflected by HealthComponent.isAlive().
 * Each enemy tank owns its own EnemyController instance (no shared state).
 */
public class EnemyController implements TankController {

    public enum State {IDLE, CHASE, ATTACK}

    private State state;
    private float lostTimer;  // seconds spent out of detect range before returning IDLE

    public EnemyController() {
        this.state = State.IDLE;
        this.lostTimer = 0f;
    }

    @Override
    public List<Projectile> update(Tank tank, float delta, UpdateContext ctx) {
        if (!tank.getHealth().isAlive()) return Collections.emptyList();

        tank.getTurret().update(delta);

        if (tank.isStunned()) {
            return Collections.emptyList();
        }

        float dist = tank.getMovement().getPosition().dst(ctx.playerPos);

        switch (state) {
            case IDLE:
                // Smoke blocks initial detection even if the player is close enough otherwise.
                if (ctx.areaActive && ctx.playerAlive && !ctx.playerInSmoke
                    && dist < GameConfig.ENEMY_DETECT_RANGE) {
                    state = State.CHASE;
                    lostTimer = 0f;
                }
                break;

            case CHASE:
                if (!ctx.playerAlive) {
                    state = State.IDLE;
                    break;
                }

                // While the player is in smoke, treat them as lost regardless of raw distance.
                if (ctx.playerInSmoke || dist > GameConfig.ENEMY_DETECT_RANGE) {
                    lostTimer += delta;
                    if (lostTimer >= GameConfig.ENEMY_LOST_TIME) {
                        state = State.IDLE;
                        lostTimer = 0f;
                    }
                } else {
                    lostTimer = 0f;
                }

                if (ctx.playerInSmoke) {
                    // Can't close in on a target it can't see.
                    break;
                }

                if (dist <= GameConfig.ENEMY_ATTACK_RANGE) {
                    state = State.ATTACK;
                } else {
                    tank.getMovement().moveToward(ctx.playerPos, tank.getStats().maxSpeed, delta);
                }
                break;

            case ATTACK:
                if (!ctx.playerAlive) {
                    state = State.IDLE;
                    break;
                }

                if (ctx.playerInSmoke) {
                    // Player vanished into smoke — drop the lock and fall back to
                    // CHASE, where the lost-timer above will eventually return to IDLE.
                    state = State.CHASE;
                    lostTimer = 0f;
                    break;
                }

                // Hysteresis: switch back to chase when player moves just outside range
                if (dist > GameConfig.ENEMY_ATTACK_RANGE * 1.2f) {
                    state = State.CHASE;
                } else {
                    tank.getMovement().faceTarget(ctx.playerPos);
                    tank.getTurret().aimAt(tank.getMovement().getPosition(), ctx.playerPos);

                    // EMP-suppressed tanks keep tracking the player but can't fire.
                    if (tank.isEmpSuppressed()) {
                        return Collections.emptyList();
                    }

                    return tank.getTurret().tryFireAt(
                        tank.getMovement().getPosition(),
                        ctx.playerPos,
                        tank.getStats().height);
                }
                break;
        }

        return Collections.emptyList();
    }

    public State getState() {
        return state;
    }
}
