package me.alpha432.oyvey.event.impl.entity;


import me.alpha432.oyvey.event.Event;
import net.minecraft.world.entity.player.Player;

public class TotemEvent extends Event {
    private final Player player;

    public TotemEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}