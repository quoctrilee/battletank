package com.mygame.tank.controller.player;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.mygame.tank.controller.TankController;
import com.mygame.tank.controller.UpdateContext;
import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;
import com.mygame.tank.entity.component.turret.PlayerTurretComponent;
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
        PlayerTurretComponent turret = (PlayerTurretComponent) tank.getTurret();

        handleHubInput(turret, input);

        // Fix: Pass UpdateContext so equipment results are merged into pending lists
        handleEquipmentInput(turret, input, ctx, tank);

        // Only move if hub is closed
        if (!turret.isHubOpen()) {
            updateBody(tank, delta, input);
        }

        updateTurret(tank, turret, input);
        turret.update(delta);

        // Fire — returns FireResult; push beams & effects to context
        WeaponSystem.FireResult result = turret.tryFire(
            tank.getMovement().getPosition(),
            tank.getStats().height,
            input.fire,
            tank);

        ctx.addLaserBeams(result.laserBeams);
        ctx.addWorldEffects(result.worldEffects);
        ctx.addVisualEffects(result.vfx);

        return result.projectiles;
    }

    // ─── Hub ─────────────────────────────────────────────────────────────────

    private void handleHubInput(PlayerTurretComponent turret, PlayerInput input) {
        if (input.toggleHub) {
            turret.toggleHub();
        }
        if (input.hubSelectSlot >= 0 && turret.isHubOpen()) {
            WeaponSystem ws = turret.getWeaponSystem();
            if (ws != null) ws.selectWeaponInHub(input.hubSelectSlot);
        }
        // Direct weapon slot selection (click/tap on HUD slot) — works without hub open
        if (input.directWeaponSlot >= 0) {
            WeaponSystem ws = turret.getWeaponSystem();
            if (ws != null) ws.setActiveWeaponSlot(input.directWeaponSlot);
        }
    }

    // ─── Equipment ───────────────────────────────────────────────────────────

    private void handleEquipmentInput(PlayerTurretComponent turret, PlayerInput input,
                                      UpdateContext ctx, Tank tank) {
        WeaponSystem.FireResult result = WeaponSystem.FireResult.EMPTY;

        if (input.useEquip1) result = turret.useEquipment(0, tank);
        else if (input.useEquip2) result = turret.useEquipment(1, tank);
        else if (input.useEquip3) result = turret.useEquipment(2, tank);
        else if (input.useEquip4) result = turret.useEquipment(3, tank);
        // Direct equip slot tap/click — activates regardless of key state
        else if (input.directEquipSlot >= 0) result = turret.useEquipment(input.directEquipSlot, tank);

        // Push all equipment output entities to UpdateContext
        if (result != WeaponSystem.FireResult.EMPTY) {
            ctx.addWorldEffects(result.worldEffects);
            ctx.addVisualEffects(result.vfx);
            ctx.addLaserBeams(result.laserBeams);
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

    private void updateTurret(Tank tank, PlayerTurretComponent turret, PlayerInput input) {
        Vector2 pos = tank.getMovement().getPosition();
        float angle = MathUtils.atan2(input.mouseWorldY - pos.y,
            input.mouseWorldX - pos.x)
            * MathUtils.radiansToDegrees;
        turret.aimAtAngle(angle);
    }
}
