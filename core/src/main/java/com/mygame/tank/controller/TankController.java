package com.mygame.tank.controller;

import com.mygame.tank.entity.Projectile;
import com.mygame.tank.entity.Tank;

import java.util.List;

/**
 * Strategy interface — every tank variant provides its own controller.
 *
 * Controllers are the sole owners of behavioral logic.
 * They mutate tank components (health, movement, turret) but do not own the state themselves.
 */
public interface TankController {

    /**
     * Called every frame. Mutates the tank's components as needed.
     *
     * @param tank  the tank being controlled
     * @param delta seconds elapsed this frame
     * @param ctx   shared per-frame context (player position, input, area flags)
     * @return projectiles fired this frame — never null, may be empty
     */
    List<Projectile> update(Tank tank, float delta, UpdateContext ctx);
}
