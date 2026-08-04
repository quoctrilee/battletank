package com.mygame.tank.controller.player;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.weapon.WeaponSystem;

import java.util.Collections;
import java.util.List;

/**
 * Player controller — reads {@link PlayerInput} and drives the tank.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Body movement: WASD → 8-direction snap + acceleration/deceleration</li>
 *   <li>Turret aim: instant atan2 to mouse world-position</li>
 *   <li>Firing: delegated to TurretComponent → WeaponSystem → FireResult</li>
 *   <li>Hub: toggle weapon-selection hub (Tab)</li>
 *   <li>Equipment: activate slots 1/2/3/4 (outside hub)</li>
 * </ul>
 *
 * <p>FireResult handling: projectiles, laser beams, world effects, and visual effects
 * are collected into {@link UpdateContext} so GameWorld can process them.
 * This controller returns only the main firing projectiles list (legacy interface);
 * beams and effects are pushed via the context.
 */
public class PlayerController implements TankController {

    @Override
    public List<Projectile> update(Tank tank, float delta, UpdateContext ctx) {
        if (!tank.getHealth().isAlive()) return Collections.emptyList();
        if (tank.isStunned()) return Collections.emptyList();

        PlayerInput input = ctx.playerInput;

        handleHubInput(tank, input);

        // Fix: Pass UpdateContext so equipment results are merged into pending lists
        handleEquipmentInput(tank, input, ctx);

        // Only move if hub is closed
        if (!tank.getTurret().isHubOpen()) {
            updateBody(tank, delta, input);
        }

        updateTurret(tank, input);
        tank.getTurret().update(delta);

        // Fire — returns FireResult; push beams & effects to context
        WeaponSystem.FireResult result = tank.getTurret().tryFirePlayer(
            tank.getMovement().getPosition(),
            tank.getStats().height,
            input.fire,
            tank);

        if (ctx.pendingBeams != null) ctx.pendingBeams.addAll(result.laserBeams);
        if (ctx.pendingEffects != null) ctx.pendingEffects.addAll(result.worldEffects);
        if (ctx.pendingVfx != null) ctx.pendingVfx.addAll(result.vfx);

        return result.projectiles;
    }

    // ─── Hub ─────────────────────────────────────────────────────────────────

    private void handleHubInput(Tank tank, PlayerInput input) {
        if (input.toggleHub) {
            tank.getTurret().toggleHub();
        }
        if (input.hubSelectSlot >= 0 && tank.getTurret().isHubOpen()) {
            WeaponSystem ws = tank.getTurret().getWeaponSystem();
            if (ws != null) ws.selectWeaponInHub(input.hubSelectSlot);
        }
    }

    // ─── Equipment ───────────────────────────────────────────────────────────

    private void handleEquipmentInput(Tank tank, PlayerInput input, UpdateContext ctx) {
        WeaponSystem.FireResult result = WeaponSystem.FireResult.EMPTY;

        if (input.useEquip1) result = tank.getTurret().useEquipment(0, tank);
        else if (input.useEquip2) result = tank.getTurret().useEquipment(1, tank);
        else if (input.useEquip3) result = tank.getTurret().useEquipment(2, tank);
        else if (input.useEquip4) result = tank.getTurret().useEquipment(3, tank);

        // Push all equipment output entities to UpdateContext
        if (result != WeaponSystem.FireResult.EMPTY) {
            if (ctx.pendingEffects != null && !result.worldEffects.isEmpty()) {
                ctx.pendingEffects.addAll(result.worldEffects);
            }
            if (ctx.pendingVfx != null && !result.vfx.isEmpty()) {
                ctx.pendingVfx.addAll(result.vfx);
            }
            if (ctx.pendingBeams != null && !result.laserBeams.isEmpty()) {
                ctx.pendingBeams.addAll(result.laserBeams);
            }
        }
    }

    // ─── Body Movement ────────────────────────────────────────────────────────

    private void updateBody(Tank tank, float delta, PlayerInput input) {
        if (input.hasMoveInput) {
            tank.getMovement().setBodyAngle(input.moveAngleDeg);
            tank.getMovement().driveForward(true, delta);
        } else {
            tank.getMovement().driveForward(false, delta);
        }
    }

    // ─── Turret Aim ───────────────────────────────────────────────────────────

    private void updateTurret(Tank tank, PlayerInput input) {
        Vector2 pos = tank.getMovement().getPosition();
        float angle = MathUtils.atan2(input.mouseWorldY - pos.y,
            input.mouseWorldX - pos.x)
            * MathUtils.radiansToDegrees;
        tank.getTurret().aimAtAngle(angle);
    }
}
