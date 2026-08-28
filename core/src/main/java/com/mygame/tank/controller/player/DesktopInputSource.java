package com.mygame.tank.controller.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.mygame.tank.config.GameConfig;
import com.mygame.tank.render.GameRenderer;

/**
 * Keyboard + mouse implementation of {@link PlayerInputSource}.
 *
 * <h3>Key bindings</h3>
 * <table>
 *   <tr><th>Key</th><th>Action</th></tr>
 *   <tr><td>W/A/S/D</td><td>Tank body movement</td></tr>
 *   <tr><td>LMB (held)</td><td>Fire active weapon</td></tr>
 *   <tr><td>LMB (click on weapon slot HUD)</td><td>Switch active weapon directly</td></tr>
 *   <tr><td>Tab</td><td>Toggle weapon hub</td></tr>
 *   <tr><td>1 / 2 / 3</td><td>When hub CLOSED: use equipment slot 1/2/3</td></tr>
 *   <tr><td>1 / 2 / 3</td><td>When hub OPEN: select weapon slot to modify</td></tr>
 *   <tr><td>F1</td><td>Debug overlay toggle (handled in GameScreen)</td></tr>
 * </table>
 */
public class DesktopInputSource implements PlayerInputSource {

    private final Vector3 mouseScreenPos = new Vector3();

    // ─── Edge-trigger state ────────────────────────────────────────────────────
    private boolean prevTab = false;
    private boolean prev1   = false;
    private boolean prev2   = false;
    private boolean prev3   = false;
    private boolean prev4   = false;
    private boolean prev5   = false;
    private boolean prev6   = false;
    private boolean prev7   = false;

    /** Previous frame LMB state — used for edge-detection on slot clicks. */
    private boolean prevLmb = false;

    /** Whether the hub is currently open — mirrored from WeaponSystem to disambiguate 1-7. */
    private boolean hubOpen = false;

    @Override
    public void setHubOpen(boolean open) {
        this.hubOpen = open;
    }

    @Override
    public PlayerInput read(Camera camera, Vector2 tankPos) {
        boolean up    = Gdx.input.isKeyPressed(Keys.W);
        boolean down  = Gdx.input.isKeyPressed(Keys.S);
        boolean left  = Gdx.input.isKeyPressed(Keys.A);
        boolean right = Gdx.input.isKeyPressed(Keys.D);
        boolean lmbNow = Gdx.input.isButtonPressed(Buttons.LEFT);
        boolean fire  = lmbNow;

        // Tab — edge-triggered toggle
        boolean tabNow    = Gdx.input.isKeyPressed(Keys.TAB);
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

        prev1 = num1Now; prev2 = num2Now; prev3 = num3Now; prev4 = num4Now;
        prev5 = num5Now; prev6 = num6Now; prev7 = num7Now;

        // While hub is open: 1-7 = select weapon slot; equipment inactive
        int     hubSelectSlot = -1;
        boolean useEquip1     = false;
        boolean useEquip2     = false;
        boolean useEquip3     = false;
        boolean useEquip4     = false;

        if (hubOpen) {
            if      (press1) hubSelectSlot = 0;
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

        // ── Direct weapon slot click (LMB just-pressed on HUD slot) ──────────
        boolean lmbJust = lmbNow && !prevLmb;
        int directWeaponSlot = -1;
        int directEquipSlot  = -1;
        if (lmbJust) {
            int mx = Gdx.input.getX();  // screen coords, Y=0 at top
            int my = Gdx.input.getY();
            int sw2 = Gdx.graphics.getWidth();
            int sh2 = Gdx.graphics.getHeight();
            directWeaponSlot = hitWeaponSlot(mx, my, sw2, sh2);
            if (directWeaponSlot < 0) {
                directEquipSlot = hitEquipSlot(mx, my, sh2);
            }
        }
        prevLmb = lmbNow;

        // Suppress fire when clicking on a slot (don't shoot while selecting)
        if (directWeaponSlot >= 0 || directEquipSlot >= 0) fire = false;

        mouseScreenPos.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouseScreenPos);

        return new PlayerInput(
                up, down, left, right,
                fire,
                toggleHub, hubSelectSlot,
                useEquip1, useEquip2, useEquip3, useEquip4,
                mouseScreenPos.x, mouseScreenPos.y,
                directWeaponSlot, directEquipSlot);
    }

    // ─── HUD weapon slot hit-test ──────────────────────────────────────────────

    /**
     * Returns equipment slot index (0-based) if the mouse click falls within an
     * equipment-slot HUD tile (bottom-left area), or -1 if missed.
     *
     * @param mx raw screen X (0 = left)
     * @param my raw screen Y (0 = top in libGDX screen coords)
     */
    private static int hitEquipSlot(int mx, int my, int sh) {
        int slotSize = GameRenderer.EQUIP_SLOT_SIZE;
        int slotGap  = GameRenderer.EQUIP_SLOT_GAP;
        int x0       = GameRenderer.EQUIP_SLOT_X0;
        int numSlots = GameConfig.EQUIPMENT_SLOTS;

        // Convert from screen Y (top=0) to HUD Y (bottom=0)
        int hudY = sh - my;
        if (hudY < GameRenderer.EQUIP_SLOT_Y ||
                hudY > GameRenderer.EQUIP_SLOT_Y + slotSize) return -1;

        for (int i = 0; i < numSlots; i++) {
            int sx = x0 + i * (slotSize + slotGap);
            if (mx >= sx && mx < sx + slotSize) return i;
        }
        return -1;
    }
    /**
     * Returns weapon slot index (0-based) if the mouse click falls within a
     * weapon-slot HUD tile (bottom-center area), or -1 if missed.
     *
     * @param mx raw screen X (0 = left)
     * @param my raw screen Y (0 = top in libGDX screen coords)
     */
    private static int hitWeaponSlot(int mx, int my, int sw, int sh) {
        int slotSize = GameRenderer.WEAPON_SLOT_SIZE;
        int slotGap  = GameRenderer.WEAPON_SLOT_GAP;
        int numSlots = GameConfig.DAMAGE_WEAPON_SLOTS;

        // Convert from screen Y (top=0) to HUD Y (bottom=0)
        int hudY = sh - my;

        // Slots occupy hudY: 16 → 16 + slotSize
        if (hudY < 16 || hudY > 16 + slotSize) return -1;

        int totalW = numSlots * slotSize + (numSlots - 1) * slotGap;
        int startX = sw / 2 - totalW / 2;

        for (int i = 0; i < numSlots; i++) {
            int sx = startX + i * (slotSize + slotGap);
            if (mx >= sx && mx < sx + slotSize) return i;
        }
        return -1;
    }
}