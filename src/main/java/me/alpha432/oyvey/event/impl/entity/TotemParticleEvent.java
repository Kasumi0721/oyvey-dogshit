package me.alpha432.oyvey.event.impl.entity;

import me.alpha432.oyvey.event.Event;

import java.awt.Color;

public class TotemParticleEvent extends Event {
    private static final TotemParticleEvent INSTANCE = new TotemParticleEvent();

    public double velocityX;
    public double velocityY;
    public double velocityZ;
    public Color color;

    private TotemParticleEvent() {}

    public static TotemParticleEvent get(double vx, double vy, double vz) {
        INSTANCE.velocityX = vx;
        INSTANCE.velocityY = vy;
        INSTANCE.velocityZ = vz;
        INSTANCE.color = new Color(255, 255, 255, 255);
        return INSTANCE;
    }
}