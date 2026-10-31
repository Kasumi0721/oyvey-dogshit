package me.alpha432.oyvey.mixin.network;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface MixinServerboundMovePlayerPacket {

    @Mutable
    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Accessor("onGround")
    boolean getOnGround();
}