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
 * <h3>Zone Layout (new)</h3>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │                                             │
 * │          FIRE ZONE  (top portion)           │
 * │       tap / hold anywhere = fire            │
 * │                                             │
 * ├──────────────────┬──────────────────────────┤  ← splitRatio boundary
 * │  MOVE JOYSTICK   │   AIM JOYSTICK           │
 * │  (bottom-left)   │   (bottom-right)         │
 * └──────────────────┴──────────────────────────┘
 * </pre>
 *
 * <p>The boundary position is controlled by
 * {@link com.mygame.tank.config.ControlsConfig#getSplitRatio()}.
 * Default = 55 % of screen height for joystick zone.
 *
 * <p>The in-game settings button (top-right corner) is handled by
 * {@link com.mygame.tank.screen.GameScreen.GameInputAdapter} with higher priority,
 * so taps on that button are never forwarded here.
 */
public class AndroidInputSource extends InputAdapter implements PlayerInputSource {

    // ── Joystick config ──────────────────────────────────────────────────────
    /** Outer radius of the move joystick (px). */
    public static final float MOVE_RADIUS = 140f;
    /** Outer radius of the aim joystick (px). */
    public static final float AIM_RADIUS  = 110f;
    /** Knob radius as fraction of outer radius. */
    public static final float KNOB_FRAC   = 0.35f;
    /** Dead-zone: fraction of radius below which input is ignored. */
    private static final float DEADZONE   = 0.15f;
    /** World units in front of tank where the virtual aim point is projected. */
    private static final float AIM_WORLD_RADIUS = 8f;

    // ── Zone split ratio ─────────────────────────────────────────────────────
    /** Cached split ratio — refresh via {@link #refreshSplitRatio()}. */
    private float splitRatio;

    // ── Move joystick (bottom-left zone) ─────────────────────────────────────
    private boolean moveActive  = false;
    private int     movePointer = -1;
    private float   moveOriginX, moveOriginY;
    private float   moveKnobX,   moveKnobY;

    private boolean up, down, left, right;

    // ── Aim joystick (bottom-right zone) ─────────────────────────────────────
    private boolean aimActive  = false;
    private int     aimPointer = -1;
    private float   aimOriginX, aimOriginY;
    private float   aimKnobX,   aimKnobY;
    /** Persists last aimed angle even when knob is released. Default: facing up. */
    private float lastAimAngleRad = MathUtils.PI / 2f;

    // ── Fire zone (top portion) ───────────────────────────────────────────────
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
     * @param stage Scene2D stage for equipment/hub-slot buttons.
     * @param skin  Skin with "slot" ButtonStyle.
     */
    public AndroidInputSource(Stage stage, Skin skin) {
        // Load current split ratio from preferences.
        splitRatio = com.mygame.tank.config.ControlsConfig.getSplitRatio();

        equipButtons   = buildButtonRow(skin, 4);
        hubSlotButtons = buildButtonRow(skin, 7);

        layoutEquipButtons(stage);

        for (Button b : equipButtons)   stage.addActor(b);
        for (Button b : hubSlotButtons) stage.addActor(b);

        wireEquipButtons();
        wireHubSlotButtons();
        setHubOpen(false);
    }

    /** Call after returning from the Controls Settings screen to pick up changes. */
    public void refreshSplitRatio() {
        splitRatio = com.mygame.tank.config.ControlsConfig.getSplitRatio();
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
        float mouseWorldX = tankPos.x + AIM_WORLD_RADIUS * MathUtils.cos(lastAimAngleRad);
        float mouseWorldY = tankPos.y + AIM_WORLD_RADIUS * MathUtils.sin(lastAimAngleRad);

        int directSlot  = directWeaponSlotPending;
        int directEquip = directEquipSlotPending;
        directWeaponSlotPending = -1;
        directEquipSlotPending  = -1;

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
                fireHeld,
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

        // ── HUD slot tap (weapon / equip) — highest priority ─────────────────
        int directSlot = hitWeaponSlot(screenX, screenY, sw, sh);
        if (directSlot >= 0) { directWeaponSlotPending = directSlot; return true; }

        int directEquip = hitEquipSlot(screenX, screenY, sh);
        if (directEquip >= 0) { directEquipSlotPending = directEquip; return true; }

        // ── Zone dispatch using splitRatio ────────────────────────────────────
        // In screen coords Y=0 is at the TOP of the screen.
        // The bottom (splitRatio * sh) pixels = joystick zone.
        // The top ((1 - splitRatio) * sh) pixels = fire zone.
        float zoneBoundaryScreenY = sh * (1f - splitRatio); // screen-Y of boundary

        boolean inFireZone = screenY < zoneBoundaryScreenY; // smaller Y = higher on screen

        if (inFireZone) {
            // ── Fire zone — tap/hold anywhere to fire ─────────────────────────
            if (firePointer < 0) {
                firePointer = pointer;
                fireHeld    = true;
                return true;
            }
        } else {
            // ── Joystick zone — left half = MOVE, right half = AIM ─────────
            boolean leftHalf = screenX < sw / 2;
            if (leftHalf) {
                if (!moveActive) {
                    moveActive  = true;
                    movePointer = pointer;
                    moveOriginX = screenX;
                    moveOriginY = screenY;
                    moveKnobX   = screenX;
                    moveKnobY   = screenY;
                    return true;
                }
            } else {
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
        float dy = -(moveKnobY - moveOriginY);
        float mag = (float) Math.sqrt(dx * dx + dy * dy);

        if (mag < MOVE_RADIUS * DEADZONE) {
            up = down = left = right = false;
            return;
        }
        if (mag > MOVE_RADIUS) {
            float nx = dx / mag * MOVE_RADIUS;
            float ny = dy / mag * MOVE_RADIUS;
            moveKnobX = moveOriginX + nx;
            moveKnobY = moveOriginY - ny;
        }

        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angleDeg < 0) angleDeg += 360f;
        int octant = Math.round(angleDeg / 45f) % 8;
        up = down = left = right = false;
        switch (octant) {
            case 0: right = true; break;
            case 1: right = true; up   = true; break;
            case 2: up    = true; break;
            case 3: left  = true; up   = true; break;
            case 4: left  = true; break;
            case 5: left  = true; down = true; break;
            case 6: down  = true; break;
            case 7: right = true; down = true; break;
        }
    }

    private void updateAimAngle() {
        float dx =  (aimKnobX - aimOriginX);
        float dy = -(aimKnobY - aimOriginY);
        float mag = (float) Math.sqrt(dx * dx + dy * dy);

        if (mag > AIM_RADIUS * DEADZONE) {
            lastAimAngleRad = (float) Math.atan2(dy, dx);
            if (mag > AIM_RADIUS) {
                float nx = dx / mag * AIM_RADIUS;
                float ny = dy / mag * AIM_RADIUS;
                aimKnobX = aimOriginX + nx;
                aimKnobY = aimOriginY - ny;
            }
        }
    }

    // ─── HUD slot hit detection ───────────────────────────────────────────────

    private static int hitEquipSlot(int screenX, int screenY, int sh) {
        int slotSize    = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_SIZE;
        int slotGap     = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_GAP;
        int x0          = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_X0;
        int numSlots    = com.mygame.tank.config.GameConfig.EQUIPMENT_SLOTS;
        int slotY       = com.mygame.tank.render.GameRenderer.EQUIP_SLOT_Y;
        int slotYTop    = sh - slotY - slotSize;
        int slotYBottom = sh - slotY;

        if (screenY < slotYTop || screenY > slotYBottom) return -1;

        for (int i = 0; i < numSlots; i++) {
            int sx = x0 + i * (slotSize + slotGap);
            if (screenX >= sx && screenX < sx + slotSize) return i;
        }
        return -1;
    }

    private static int hitWeaponSlot(int screenX, int screenY, int sw, int sh) {
        int slotSize    = com.mygame.tank.render.GameRenderer.WEAPON_SLOT_SIZE;
        int slotGap     = com.mygame.tank.render.GameRenderer.WEAPON_SLOT_GAP;
        int numSlots    = com.mygame.tank.config.GameConfig.DAMAGE_WEAPON_SLOTS;
        int slotYTop    = sh - 16 - slotSize;
        int slotYBottom = sh - 16;

        if (screenY < slotYTop || screenY > slotYBottom) return -1;

        int totalW = numSlots * slotSize + (numSlots - 1) * slotGap;
        int startX = sw / 2 - totalW / 2;

        for (int i = 0; i < numSlots; i++) {
            int sx = startX + i * (slotSize + slotGap);
            if (screenX >= sx && screenX < sx + slotSize) return i;
        }
        return -1;
    }

    // ─── State exposed for GameRenderer overlay ───────────────────────────────

    public boolean isMoveActive()  { return moveActive; }
    public float getMoveOriginX()  { return moveOriginX; }
    public float getMoveOriginY()  { return moveOriginY; }
    public float getMoveKnobX()    { return moveKnobX; }
    public float getMoveKnobY()    { return moveKnobY; }

    public boolean isAimActive()   { return aimActive; }
    public float getAimOriginX()   { return aimOriginX; }
    public float getAimOriginY()   { return aimOriginY; }
    public float getAimKnobX()     { return aimKnobX; }
    public float getAimKnobY()     { return aimKnobY; }

    public boolean isFireHeld()    { return fireHeld; }

    /** Current split ratio in use (may differ from ControlsConfig if not yet refreshed). */
    public float getSplitRatio()   { return splitRatio; }

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

    private void layoutEquipButtons(Stage stage) {
        float stageW = stage.getWidth();
        float stageH = stage.getHeight();
        float rowY   = stageH - 80f;

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