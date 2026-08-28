package com.mygame.tank.controller.player;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;

/**
 * Platform-agnostic source of {@link PlayerInput}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link DesktopInputSource} — keyboard + mouse</li>
 *   <li>{@link AndroidInputSource} — dual on-screen sticks + buttons</li>
 * </ul>
 *
 * Selected at runtime via {@link InputSourceFactory#create}.
 */
public interface PlayerInputSource {

    /**
     * @param camera  the game world camera, used to unproject aim coordinates
     * @param tankPos current tank world position — required to compute a virtual
     *                aim point on platforms without a real cursor (Android).
     *                Desktop implementations may ignore this parameter.
     */
    PlayerInput read(Camera camera, Vector2 tankPos);

    /** Call after hub state changes so 1-7 disambiguation (equip vs. select) stays correct. */
    void setHubOpen(boolean open);
}