package me.alpha432.oyvey.event.impl.entity;

import me.alpha432.oyvey.event.Event;
import net.minecraft.world.entity.Entity;

/**
 * 实体从客户端世界移除时触发。
 * dead=true 表示这是真实死亡（而非区块卸载）。
 */
public class EntityRemoveEvent extends Event {
    private final Entity entity;
    private final boolean dead;

    public EntityRemoveEvent(Entity entity, boolean dead) {
        this.entity = entity;
        this.dead = dead;
    }

    public Entity entity() { return entity; }
    public boolean dead() { return dead; }
}