package com.mygame.tank;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public final class SkinManager {

    private static final String PREF_NAME = "tank_game_preferences";
    private static final String KEY_SELECTED_SKIN = "selected_skin_index";

    public static final String[] BODY_PATHS = {
        "body/body.png",
        "body/body1.png",
        "body/body2.png"
    };

    public static final String[] BODY_NAMES = {
        "Tank I",
        "Tank II",
        "Tank III"
    };

    private static int selectedIndex = 0;
    private static boolean initialized = false;

    private SkinManager() {
    }

    private static void ensureLoaded() {
        if (!initialized) {
            initialized = true;
            try {
                if (Gdx.app != null) {
                    Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
                    selectedIndex = prefs.getInteger(KEY_SELECTED_SKIN, 0);
                    if (selectedIndex < 0 || selectedIndex >= BODY_PATHS.length) {
                        selectedIndex = 0;
                    }
                }
            } catch (Exception ignored) {
                selectedIndex = 0;
            }
        }
    }

    public static int getSelectedIndex() {
        ensureLoaded();
        return selectedIndex;
    }

    public static void setSelectedIndex(int index) {
        ensureLoaded();
        if (index >= 0 && index < BODY_PATHS.length) {
            selectedIndex = index;
            try {
                if (Gdx.app != null) {
                    Preferences prefs = Gdx.app.getPreferences(PREF_NAME);
                    prefs.putInteger(KEY_SELECTED_SKIN, selectedIndex);
                    prefs.flush();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static String getSelectedBodyPath() {
        ensureLoaded();
        return BODY_PATHS[selectedIndex];
    }

    public static String getSelectedBodyName() {
        ensureLoaded();
        return BODY_NAMES[selectedIndex];
    }

    public static String getBodyPath(int index) {
        if (index >= 0 && index < BODY_PATHS.length) {
            return BODY_PATHS[index];
        }
        return BODY_PATHS[0];
    }

    public static String getBodyName(int index) {
        if (index >= 0 && index < BODY_NAMES.length) {
            return BODY_NAMES[index];
        }
        return BODY_NAMES[0];
    }

    public static int getTankCount() {
        return BODY_PATHS.length;
    }
}
