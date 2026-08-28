package com.mygame.tank.controller.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * Android split-screen touch controller using raw touch input and floating joysticks.
 *
 * <h3>Zone Layout</h3>
 * <pre>
 * ┌─────────────────────┬─────────────────────┐
 * │                     │     FIRE ZONE       │
 * │   MOVE JOYSTICK     │  (tap anywhere)     │
 * │   (floating)        ├─────────────────────┤
 * │                     │     AIM JOYSTICK    │
 * │                     │     (floating)      │
 * └─────────────────────┴─────────────────────┘
 * </pre>
 *
 * <p>Extend {@link InputAdapter} so the instance can be added to an
 * {@link com.badlogic.gdx.InputMultiplexer} alongside the UI {@link Stage}.
 * Stage is processed first (equip/hub buttons), then unhandled raw touches
 * fall through to this processor for zone handling.
 */
public class AndroidInputSource extends InputAdapter implements PlayerInputSource {

    // ── Joystick config ──────────────────────────────────────────────────────
    /** Outer radius of the move joystick (px). Larger = more comfortable. */
    public static final float MOVE_RADIUS = 140f;
    /** Outer radius of the aim joystick (px). */
    public static final float AIM_RADIUS  = 110f;
    /** Knob radius drawn inside outer circle (fraction of outer radius). */
    public static final float KNOB_FRAC   = 0.35f;
    /** Dead-zone: fraction of radius below which input is ignored. */
    private static final float DEADZONE   = 0.15f;
    /** World units in front of tank where the virtual aim point is projected. */
    private static final float AIM_WORLD_RADIUS = 8f;

    // ── Move joystick (left half of screen) ──────────────────────────────────
    private boolean moveActive  = false;
    private int     movePointer = -1;
    /** Anchor point in screen coords (Y=0 at top). */
    private float moveOriginX, moveOriginY;
    /** Current knob position in screen coords (clamped to MOVE_RADIUS). */
    private float moveKnobX, moveKnobY;

    // Snapped 8-direction output
    private boolean up, down, left, right;

    // ── Aim joystick (bottom-right quadrant) ─────────────────────────────────
    private boolean aimActive  = false;
    private int     aimPointer = -1;
    private float aimOriginX, aimOriginY;
    private float aimKnobX,   aimKnobY;
    /** Persists last aimed angle even when knob released. Default: facing up. */
    private float lastAimAngleRad = MathUtils.PI / 2f;

    // ── Fire zone (top-right quadrant) ────────────────────────────────────────
    /** True while a finger is held in the fire zone. */
    private boolean fireHeld    = false;
    private int     firePointer = -1;

    // ── Direct weapon select (tap on HUD slots) ──────────────────────────────
    private int directWeaponSlotPending = -1;
    private int directEquipSlotPending  = -1;

    // ── Equipment / hub-slot buttons (Scene2D) ────────────────────────────────
    private final Button[] equipButtons;
    private final Button[] hubSlotButtons;
    private boolean hubOpen = false;
    private final boolean[] equipPending        = new boolean[4];
    private final int[]     hubSlotPendingIndex = {-1};

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param stage Scene2D stage to place equipment/hub-slot buttons on.
     * @param skin  Skin with "slot" ButtonStyle (built by {@link AndroidUiSkin}).
     */
    public AndroidInputSource(Stage stage, Skin skin) {
        equipButtons   = buildButtonRow(skin, 4);
        hubSlotButtons = buildButtonRow(skin, 7);

        layoutEquipButtons(stage);

        for (Button b : equipButtons)   stage.addActor(b);
        for (Button b : hubSlotButtons) stage.addActor(b);

        wireEquipButtons();
        wireHubSlotButtons();
        setHubOpen(false);
    }

    // ─── PlayerInputSource ────────────────────────────────────────────────────

    @Override
    public void setHubOpen(boolean open) {
        this.hubOpen = open;
        for (Button b : equipButtons)   b.setVisible(!open);
        for (Button b : hubSlotButtons) b.setVisible(open);
    }

    @Override
    public PlayerInput read(Camera camera, Vector2 tankPos) {
        // Aim world point derived from last aim angle
        float mouseWorldX = tankPos.x + AIM_WORLD_RADIUS * MathUtils.cos(lastAimAngleRad);
        float mouseWorldY = tankPos.y + AIM_WORLD_RADIUS * MathUtils.sin(lastAimAngleRad);

        // Consume pending slot selections
        int directSlot  = directWeaponSlotPending;
        int directEquip = directEquipSlotPending;
        directWeaponSlotPending = -1;
        directEquipSlotPending  = -1;

        // Equipment / hub-slot (edge-triggered via listeners)
        int     hubSelectSlot = -1;
        boolean useEquip1 = false, useEquip2 = false, useEquip3 = false, useEquip4 = false;
        if (hubOpen) {
            hubSelectSlot = hubSlotPendingIndex[0];
            hubSlotPendingIndex[0] = -1;
        } else {
            useEquip1 = equipPending[0]; equipPending[0] = false;
            useEquip2 = equipPending[1]; equipPending[1] = false;
            useEquip3 = equipPending[2]; equipPending[2] = false;
            useEquip4 = equipPending[3]; equipPending[3] = false;
        }

        return new PlayerInput(
                up, down, left, right,
                fireHeld,           // continuous while finger is held
                false, hubSelectSlot,
                useEquip1, useEquip2, useEquip3, useEquip4,
                mouseWorldX, mouseWorldY,
                directSlot, directEquip);
    }

    // ─── Raw touch input ──────────────────────────────────────────────────────

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        // ── Check weapon & equip HUD slot tap first (bottom area of screen) ─────
        int directSlot = hitWeaponSlot(screenX, screenY, sw, sh);
        if (directSlot >= 0) {
            directWeaponSlotPending = directSlot;
            return true;
        }
        int directEquip = hitEquipSlot(screenX, screenY, sh);
        if (directEquip >= 0) {
            directEquipSlotPending = directEquip;
            return true;
        }

        boolean leftHalf = screenX < sw / 2;
        // In screen coords Y=0 is top; top half of screen = smaller Y value
        boolean topHalf  = screenY < sh / 2;

        if (leftHalf) {
            // ── Move joystick zone ──────────────────────────────────────────
            if (!moveActive) {
                moveActive  = true;
                movePointer = pointer;
                moveOriginX = screenX;
                moveOriginY = screenY;
                moveKnobX   = screenX;
                moveKnobY   = screenY;
                // No directions yet — knob at origin = dead-zone
                return true;
            }
        } else {
            if (topHalf) {
                // ── Fire zone (top-right) ────────────────────────────────────
                if (firePointer < 0) {
                    firePointer = pointer;
                    fireHeld    = true;
                    return true;
                }
            } else {
                // ── Aim joystick zone (bottom-right) ─────────────────────────
                if (!aimActive) {
                    aimActive  = true;
                    aimPointer = pointer;
                    aimOriginX = screenX;
                    aimOriginY = screenY;
                    aimKnobX   = screenX;
                    aimKnobY   = screenY;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (moveActive && pointer == movePointer) {
            moveKnobX = screenX;
            moveKnobY = screenY;
            updateMoveDirections();
            return true;
        }
        if (aimActive && pointer == aimPointer) {
            aimKnobX = screenX;
            aimKnobY = screenY;
            updateAimAngle();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (moveActive && pointer == movePointer) {
            moveActive  = false;
            movePointer = -1;
            up = down = left = right = false;
            return true;
        }
        if (aimActive && pointer == aimPointer) {
            aimActive  = false;
            aimPointer = -1;
            // lastAimAngleRad persists — turret keeps pointing where it was
            return true;
        }
        if (pointer == firePointer) {
            firePointer = -1;
            fireHeld    = false;
            return true;
        }
        return false;
    }

    // ─── Joystick math ────────────────────────────────────────────────────────

    private void updateMoveDirections() {
        float dx =  (moveKnobX - moveOriginX);
        float dy = -(moveKnobY - moveOriginY); // screen Y inverted → game Y up
        float mag = (float) Math.sqrt(dx * dx + dy * dy);

        if (mag < MOVE_RADIUS * DEADZONE) {
            up = down = left = right = false;
            return;
        }
        // Clamp knob to outer circle
        if (mag > MOVE_RADIUS) {
            float nx = dx / mag * MOVE_RADIUS;
            float ny = dy / mag * MOVE_RADIUS;
            moveKnobX = moveOriginX + nx;
            moveKnobY = moveOriginY - ny; // un-invert for screen coord
        }

        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angleDeg < 0) angleDeg += 360f;
        int octant = Math.round(angleDeg / 45f) % 8;
        up = down = left = right = false;
        switch (octant) {
            case 0: right = true; break;
            case 1: right = true; up = true; break;
            case 2: up = true; break;
            case 3: left = true; up = true; break;
            case 4: left = true; break;
            case 5: left = true; down = true; break;
            case 6: down = true; break;
            case 7: right = true; down = true; break;
        }
    }

    private void updateAimAngle() {
        float dx =  (aimKnobX - aimOriginX);
        float dy = -(aimKnobY - aimOriginY); // screen Y → game Y
        float mag = (float) Math.sqrt(dx * dx + dy * dy);

        if (mag > AIM_RADIUS * DEADZONE) {
            lastAimAngleRad = (float) Math.atan2(dy, dx);
            // Clamp knob visually
            if (mag > AIM_RADIUS) {
                float nx = dx / mag * AIM_RADIUS;
                float ny = dy / mag * AIM_RADIUS;
                aimKnobX = aimOriginX + nx;
                aimKnobY = aimOriginY - ny;
            }
        }
    }

    // ─── HUD Weapon Slot hit detection ────────────────────────────────────────

    /**
     * Returns equipment slot index if the tap hits an equip-slot HUD tile
     * (bottom-left area), or -1 if missed.
     */
    private static int hitEquipSlot(int screenX, int screenY, int sh) {
        int slotSize = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_SIZE;
        int slotGap  = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_GAP;
        int x0       = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_X0;
        int numSlots = com.mygame.tank.config.GameConfig.EQUIPMENT_SLOTS;
        int slotY    = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_Y;
        int slotYTop    = sh - slotY - slotSize;
        int slotYBottom = sh - slotY;

        if (screenY < slotYTop || screenY > slotYBottom) return -1;

        for (int i = 0; i < numSlots; i++) {
            int sx = x0 + i * (slotSize + slotGap);
            if (screenX >= sx && screenX < sx + slotSize) return i;
        }
        return -1;
    }

    /**
     * Returns slot index (0-based) if the tap is inside a weapon-slot HUD tile,
     * or -1 if the tap missed all slots.
     *
     * <p>Mirrors the position formula in {@link com.mygame.tank.render.GameRenderer}.
     */
    private static int hitWeaponSlot(int screenX, int screenY, int sw, int sh) {
        // HUD slots are WEAPON_SLOT_SIZE tall, sitting at slotY=16 from screen bottom.
        // In raw screen coords (Y=0 at top): slot occupies
        //   top    = sh - 16 - WEAPON_SLOT_SIZE
        //   bottom = sh - 16
        int slotSize = com.mygame.tank.render.GameRenderer.WEAPON_SLOT_SIZE;
        int slotGap  = com.mygame.tank.render.GameRenderer.WEAPON_SLOT_GAP;
        int numSlots = com.mygame.tank.config.GameConfig.DAMAGE_WEAPON_SLOTS;
        int slotYTop    = sh - 16 - slotSize;
        int slotYBottom = sh - 16;

        if (screenY < slotYTop || screenY > slotYBottom) return -1;

        int totalW  = numSlots * slotSize + (numSlots - 1) * slotGap;
        int startX  = sw / 2 - totalW / 2;

        for (int i = 0; i < numSlots; i++) {
            int sx = startX + i * (slotSize + slotGap);
            if (screenX >= sx && screenX < sx + slotSize) return i;
        }
        return -1;
    }

    // ─── Joystick state exposed for GameRenderer overlay ──────────────────────

    public boolean isMoveActive()  { return moveActive; }
    /** Move joystick anchor X in screen coords (Y=0 at top). */
    public float getMoveOriginX()  { return moveOriginX; }
    public float getMoveOriginY()  { return moveOriginY; }
    /** Move joystick knob X in screen coords. */
    public float getMoveKnobX()    { return moveKnobX; }
    public float getMoveKnobY()    { return moveKnobY; }

    public boolean isAimActive()   { return aimActive; }
    /** Aim joystick anchor X in screen coords (Y=0 at top). */
    public float getAimOriginX()   { return aimOriginX; }
    public float getAimOriginY()   { return aimOriginY; }
    /** Aim joystick knob X in screen coords. */
    public float getAimKnobX()     { return aimKnobX; }
    public float getAimKnobY()     { return aimKnobY; }

    public boolean isFireHeld()    { return fireHeld; }

    // ─── Widget helpers ───────────────────────────────────────────────────────

    private Button[] buildButtonRow(Skin skin, int count) {
        Button[] buttons = new Button[count];
        for (int i = 0; i < count; i++) {
            Button b = skin.has("slot", Button.ButtonStyle.class)
                    ? new Button(skin, "slot")
                    : new Button(skin.has("default", Button.ButtonStyle.class)
                            ? skin.get("default", Button.ButtonStyle.class)
                            : fallbackButtonStyle());
            b.setSize(64f, 64f);
            buttons[i] = b;
        }
        return buttons;
    }

    /**
     * Equipment row at top-center — stays clear of both joystick zones.
     */
    private void layoutEquipButtons(Stage stage) {
        float stageW = stage.getWidth();
        float stageH = stage.getHeight();
        float rowY   = stageH - 80f; // near top

        float equipStart = stageW / 2f - (equipButtons.length * 72f) / 2f;
        for (int i = 0; i < equipButtons.length; i++) {
            equipButtons[i].setPosition(equipStart + i * 72f, rowY);
        }
        float hubStart = stageW / 2f - (hubSlotButtons.length * 72f) / 2f;
        for (int i = 0; i < hubSlotButtons.length; i++) {
            hubSlotButtons[i].setPosition(hubStart + i * 72f, rowY);
        }
    }

    private void wireEquipButtons() {
        for (int i = 0; i < equipButtons.length; i++) {
            final int slot = i;
            equipButtons[i].addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    equipPending[slot] = true;
                }
            });
        }
    }

    private void wireHubSlotButtons() {
        for (int i = 0; i < hubSlotButtons.length; i++) {
            final int slot = i;
            hubSlotButtons[i].addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    hubSlotPendingIndex[0] = slot;
                }
            });
        }
    }

    private static Button.ButtonStyle fallbackButtonStyle() {
        Button.ButtonStyle style = new Button.ButtonStyle();
        com.badlogic.gdx.graphics.Texture blank =
            new com.badlogic.gdx.graphics.Texture(1, 1,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        style.up = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
            new com.badlogic.gdx.graphics.g2d.TextureRegion(blank));
        return style;
    }
}