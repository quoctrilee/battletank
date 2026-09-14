package com.mygame.tank;

import com.badlogic.gdx.Game;
import com.mygame.tank.screen.MenuScreen;

public class TankGame extends Game {

    @Override
    public void create() {
        setScreen(new MenuScreen(this));
    }
}
