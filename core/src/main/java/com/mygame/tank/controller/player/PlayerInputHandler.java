package com.mygame.tank.controller.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

/**
 * Translates raw libGDX input into an immutable {@link PlayerInput} snapshot.
 *
 * <h3>Key bindings</h3>
 * <table>
 *   <tr><th>Key</th><th>Action</th></tr>
 *   <tr><td>W/A/S/D</td><td>Tank body movement</td></tr>
 *   <tr><td>LMB</td><td>Fire active weapon</td></tr>
 *   <tr><td>Tab</td><td>Toggle weapon hub</td></tr>
 *   <tr><td>1 / 2 / 3</td><td>When hub CLOSED: use equipment slot 1/2/3</td></tr>
 *   <tr><td>1 / 2 / 3</td><td>When hub OPEN: select weapon slot to modify</td></tr>
 *   <tr><td>F1</td><td>Debug overlay toggle (handled in GameScreen)</td></tr>
 * </table>
 */
public class PlayerInputHandler {

    private final Vector3 mouseScreenPos = new Vector3();

    // Edge-trigger state
    private boolean prevTab    = false;
    private boolean prev1      = false;
    private boolean prev2      = false;
    private boolean prev3      = false;

    private boolean prev4      = false;
    private boolean prev5      = false;
    private boolean prev6      = false;
    private boolean prev7      = false;

    /** Whether the hub is currently open — mirrored from WeaponSystem to disambiguate 1-7. */
    private boolean hubOpen = false;

    /** Call from GameWorld/PlayerController after hub state changes to keep disambiguation correct. */
    public void setHubOpen(boolean open) { this.hubOpen = open; }

    public PlayerInput read(Camera camera) {
        boolean up    = Gdx.input.isKeyPressed(Keys.W);
        boolean down  = Gdx.input.isKeyPressed(Keys.S);
        boolean left  = Gdx.input.isKeyPressed(Keys.A);
        boolean right = Gdx.input.isKeyPressed(Keys.D);
        boolean fire  = Gdx.input.isButtonPressed(Buttons.LEFT);

        // Tab — edge-triggered toggle
        boolean tabNow   = Gdx.input.isKeyPressed(Keys.TAB);
        boolean toggleHub = tabNow && !prevTab;
        prevTab = tabNow;

        // 1-7 — edge-triggered
        boolean num1Now = Gdx.input.isKeyPressed(Keys.NUM_1);
        boolean num2Now = Gdx.input.isKeyPressed(Keys.NUM_2);
        boolean num3Now = Gdx.input.isKeyPressed(Keys.NUM_3);
        boolean num4Now = Gdx.input.isKeyPressed(Keys.NUM_4);
        boolean num5Now = Gdx.input.isKeyPressed(Keys.NUM_5);
        boolean num6Now = Gdx.input.isKeyPressed(Keys.NUM_6);
        boolean num7Now = Gdx.input.isKeyPressed(Keys.NUM_7);

        boolean press1 = num1Now && !prev1;
        boolean press2 = num2Now && !prev2;
        boolean press3 = num3Now && !prev3;
        boolean press4 = num4Now && !prev4;
        boolean press5 = num5Now && !prev5;
        boolean press6 = num6Now && !prev6;
        boolean press7 = num7Now && !prev7;

        prev1 = num1Now;
        prev2 = num2Now;
        prev3 = num3Now;
        prev4 = num4Now;
        prev5 = num5Now;
        prev6 = num6Now;
        prev7 = num7Now;

        // While hub is open: 1-7 = select weapon slot; equipment inactive
        int     hubSelectSlot = -1;
        boolean useEquip1     = false;
        boolean useEquip2     = false;
        boolean useEquip3     = false;
        boolean useEquip4     = false;

        if (hubOpen) {
            if (press1) hubSelectSlot = 0;
            else if (press2) hubSelectSlot = 1;
            else if (press3) hubSelectSlot = 2;
            else if (press4) hubSelectSlot = 3;
            else if (press5) hubSelectSlot = 4;
            else if (press6) hubSelectSlot = 5;
            else if (press7) hubSelectSlot = 6;
        } else {
            useEquip1 = press1;
            useEquip2 = press2;
            useEquip3 = press3;
            useEquip4 = press4;
        }

        mouseScreenPos.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouseScreenPos);

        return new PlayerInput(
                up, down, left, right,
                fire,
                toggleHub, hubSelectSlot,
                useEquip1, useEquip2, useEquip3, useEquip4,
                mouseScreenPos.x, mouseScreenPos.y);
    }
}
