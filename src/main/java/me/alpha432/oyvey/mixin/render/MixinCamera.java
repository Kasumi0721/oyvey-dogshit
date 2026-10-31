package me.alpha432.oyvey.mixin.render;

import me.alpha432.oyvey.features.modules.render.KillEffect;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinCamera {

    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow
    protected void setRotation(float yaw, float pitch) {}

    /**
     * 拦截 Camera.setup 的尾部，在相机旋转算完后追加抖动。
     * 通过 setRotation 重设，会自动同步 rotation 四元数和 forwards/up/left 向量。
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void onSetup(Level level, Entity entity, boolean detached,
                         boolean reversed, float partialTick, CallbackInfo ci) {
        KillEffect kfx = KillEffect.INSTANCE;
        if (kfx == null || !kfx.isEnabled()) return;
        float shake = kfx.getShakeIntensity();
        if (shake < 0.01f) return;

        float amp = Math.min(shake, 25.5f) * 0.45f;
        float yawOffset = (float) ((Math.random() - 0.5) * 2.0) * amp;
        float pitchOffset = (float) ((Math.random() - 0.5) * 2.0) * amp * 0.6f;

        setRotation(this.yRot + yawOffset, this.xRot + pitchOffset);
    }
}