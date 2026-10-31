package me.alpha432.oyvey.event.impl.entity;

import me.alpha432.oyvey.event.Event;
import net.minecraft.world.entity.Entity;

public class VelocityUpdateEvent extends Event {
    private final Entity entity;
    private double x, y, z;

    public VelocityUpdateEvent(Entity entity, double x, double y, double z) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Entity getEntity() { return entity; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
}