package me.alpha432.oyvey.event.impl.entity;

import me.alpha432.oyvey.event.Event;

public class MotionEvent extends Event {
    public static MotionEvent INSTANCE;

    protected int stage;      // 0 = pre, 1 = post
    protected float yaw;
    protected float pitch;
    protected double x;
    protected double y;
    protected double z;
    protected boolean onGround;

    public MotionEvent(int stage, double posX, double posY, double posZ,
                       float y, float p, boolean pOnGround) {
        INSTANCE = this;
        this.stage = stage;
        this.x = posX;
        this.y = posY;
        this.z = posZ;
        this.yaw = y;
        this.pitch = p;
        this.onGround = pOnGround;
    }

    public MotionEvent(int stage, MotionEvent event) {
        this(stage, event.x, event.y, event.z, event.yaw, event.pitch, event.onGround);
    }

    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }

    public void setRotation(float yaw, float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    public void setPosition(double x, double y, double z, boolean onGround) {
        this.setX(x);
        this.setY(y);
        this.setZ(z);
        this.setOnGround(onGround);
    }

    public void setPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.setX(x);
        this.setY(y);
        this.setZ(z);
        this.setYaw(yaw);
        this.setPitch(pitch);
        this.setOnGround(onGround);
    }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setYaw(double yaw) { this.yaw = (float) yaw; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }
    public void setPitch(double pitch) { this.pitch = (float) pitch; }

    public double getX() { return x; }
    public void setX(double posX) { this.x = posX; }

    public double getY() { return y; }
    public void setY(double d) { this.y = d; }

    public double getZ() { return z; }
    public void setZ(double posZ) { this.z = posZ; }

    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean b) { this.onGround = b; }
}