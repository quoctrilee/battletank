package com.mygame.tank.controller.player;

/**
 * Immutable snapshot of all player input for one frame.
 *
 * <p>New fields vs. previous version:
 * <ul>
 *   <li>{@link #toggleHub}    — Tab: open/close weapon selection hub</li>
 *   <li>{@link #useEquip1/2/3} — keys 1/2/3: activate tactical equipment slot</li>
 *   <li>{@link #hubSelectSlot} — while hub open, press 1/2/3 to select weapon slot</li>
 * </ul>
 */
public final class PlayerInput {

    // ─── Movement ─────────────────────────────────────────────────────────────
    public final boolean up;
    public final boolean down;
    public final boolean left;
    public final boolean right;
    public final boolean hasMoveInput;
    public final float   moveAngleDeg;

    // ─── Combat ───────────────────────────────────────────────────────────────
    public final boolean fire;

    // ─── Weapon Hub (Tab) ─────────────────────────────────────────────────────
    /** Toggle weapon hub open/close (Tab — edge-triggered). */
    public final boolean toggleHub;

    /**
     * While the hub is open, 1/2/3 selects which weapon slot to modify.
     * -1 = no selection this frame.
     */
    public final int hubSelectSlot;

    // ─── Tactical Equipment (1 / 2 / 3) ──────────────────────────────────────
    /** Activate equipment slot 0 (key 1) — edge-triggered. */
    public final boolean useEquip1;
    /** Activate equipment slot 1 (key 2) — edge-triggered. */
    public final boolean useEquip2;
    /** Activate equipment slot 2 (key 3) — edge-triggered. */
    public final boolean useEquip3;
    /** Activate equipment slot 3 (key 4) — edge-triggered. */
    public final boolean useEquip4;

    // ─── Aim ─────────────────────────────────────────────────────────────────
    public final float mouseWorldX;
    public final float mouseWorldY;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public PlayerInput(boolean up, boolean down, boolean left, boolean right,
                       boolean fire,
                       boolean toggleHub, int hubSelectSlot,
                       boolean useEquip1, boolean useEquip2, boolean useEquip3, boolean useEquip4,
                       float mouseWorldX, float mouseWorldY) {
        this.up           = up;
        this.down         = down;
        this.left         = left;
        this.right        = right;
        this.fire         = fire;
        this.toggleHub    = toggleHub;
        this.hubSelectSlot = hubSelectSlot;
        this.useEquip1    = useEquip1;
        this.useEquip2    = useEquip2;
        this.useEquip3    = useEquip3;
        this.useEquip4    = useEquip4;
        this.mouseWorldX  = mouseWorldX;
        this.mouseWorldY  = mouseWorldY;

        this.hasMoveInput = up || down || left || right;
        this.moveAngleDeg = hasMoveInput ? computeAngle(up, down, left, right) : 0f;
    }

    private static float computeAngle(boolean up, boolean down, boolean left, boolean right) {
        int x = (right ? 1 : 0) - (left ? 1 : 0);
        int y = (up    ? 1 : 0) - (down ? 1 : 0);
        if (x == 0 && y == 0) return 0f;
        return (float) Math.toDegrees(Math.atan2(y, x));
    }

    public static final PlayerInput NONE =
        new PlayerInput(false, false, false, false, false,
                        false, -1, false, false, false, false, 0f, 0f);
}
