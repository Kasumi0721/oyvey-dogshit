package me.alpha432.oyvey.event.impl.entity;

import me.alpha432.oyvey.event.Event;
import net.minecraft.world.entity.MoverType;

public class MoveEvent extends Event {
    private int stage;
    private MoverType type;
    private double x;
    private double y;
    private double z;

    public MoveEvent(int stage, MoverType type, double x, double y, double z) {
        this.stage = stage;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }

    public MoverType getType() { return type; }
    public void setType(MoverType type) { this.type = type; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
}