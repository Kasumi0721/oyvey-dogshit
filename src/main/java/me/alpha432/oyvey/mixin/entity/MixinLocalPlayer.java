package me.alpha432.oyvey.mixin.entity;

import me.alpha432.oyvey.event.impl.entity.MotionEvent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {

    /**
     * Pre：tick 开头。Rebirth 的 MotionEvent(stage=0)
     * 让模块可以修改坐标 / yaw / pitch / onGround
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPre(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        MotionEvent event = new MotionEvent(0,
                self.getX(), self.getY(), self.getZ(),
                self.getYRot(), self.getXRot(),
                self.onGround());

        EVENT_BUS.post(event);

        // 应用模块的修改
        self.setPos(event.getX(), event.getY(), event.getZ());
        self.setYRot(event.getYaw());
        self.setXRot(event.getPitch());
        if (event.isOnGround() != self.onGround()) {
            self.setOnGround(event.isOnGround());
        }
    }

    /**
     * Post：tick 结尾。Rebirth 的 MotionEvent(stage=1)
     * 主要用于读数据，不修改
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickPost(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        MotionEvent event = new MotionEvent(1,
                self.getX(), self.getY(), self.getZ(),
                self.getYRot(), self.getXRot(),
                self.onGround());

        EVENT_BUS.post(event);
    }
}