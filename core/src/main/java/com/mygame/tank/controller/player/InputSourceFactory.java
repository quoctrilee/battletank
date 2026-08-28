package com.mygame.tank.controller.player;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * Selects the correct {@link PlayerInputSource} implementation for the
 * platform the game is currently running on. Call once during screen/game
 * setup — no platform-specific code needed anywhere else.
 *
 * <pre>{@code
 * PlayerInputSource inputSource = InputSourceFactory.create(uiStage, uiSkin);
 * ...
 * PlayerInput input = inputSource.read(camera, tank.getPosition());
 * }</pre>
 */
public final class InputSourceFactory {

    private InputSourceFactory() { }

    /**
     * @param stage UI stage to attach on-screen controls to (only used on Android).
     * @param skin  Skin providing touchpad/button styles (only used on Android).
     */
    public static PlayerInputSource create(Stage stage, Skin skin) {
        if (Gdx.app.getType() == ApplicationType.Android) {
            return new AndroidInputSource(stage, skin);
        }
        return new DesktopInputSource();
    }
}