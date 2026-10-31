package me.alpha432.oyvey.util.render;

import net.minecraft.world.phys.Vec3;

public final class TrailPoint {
    public final Vec3 pos;
    private float age;
    private final float life;

    public TrailPoint(Vec3 pos, float life) {
        this.pos = pos;
        this.life = life;
        this.age = 0f;
    }

    public void tick(float dt) {
        age += dt;
    }

    public boolean expired() {
        return age >= life;
    }

    /** 0 = 过期，1 = 全新 */
    public float alpha() {
        return Math.max(0f, Math.min(1f, 1f - age / life));
    }
}