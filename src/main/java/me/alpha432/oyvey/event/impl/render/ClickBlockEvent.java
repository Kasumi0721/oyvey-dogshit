package me.alpha432.oyvey.event.impl.render;

import me.alpha432.oyvey.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class ClickBlockEvent extends Event {
    private BlockPos pos;
    private Direction direction;
    private static final ClickBlockEvent INSTANCE = new ClickBlockEvent();

    private ClickBlockEvent() {
    }

    public Direction getDirection() {
        return this.direction;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public static ClickBlockEvent get(BlockPos pos, Direction direction) {
        INSTANCE.pos = pos;
        INSTANCE.direction = direction;
        INSTANCE.setCancelled(false);   // ✅ 重置为"未取消"
        return INSTANCE;
    }
}