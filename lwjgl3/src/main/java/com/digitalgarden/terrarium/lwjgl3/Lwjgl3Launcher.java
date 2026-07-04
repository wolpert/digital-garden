package com.digitalgarden.terrarium.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.digitalgarden.terrarium.Config;
import com.digitalgarden.terrarium.Terrarium;

/** Desktop entry point. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Terrarium");
        // open at 3x the logical resolution (1440x810)
        config.setWindowedMode(Config.VIEW_W * 3, Config.VIEW_H * 3);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new Terrarium(), config);
    }
}
