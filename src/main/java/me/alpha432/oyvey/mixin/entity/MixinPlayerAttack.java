package me.alpha432.oyvey.mixin.entity;

import me.alpha432.oyvey.features.modules.render.SweepAttackVFX;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.mc;

@Mixin(Player.class)
public class MixinPlayerAttack {

    /**
     * 拦截 Player.attack(Entity) —— 玩家攻击实体时直接触发特效。
     * 不检测主手物品，任何攻击都触发。
     * 特效生成在目标位置。
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        if ((Object) this != mc.player) return;
        if (SweepAttackVFX.INSTANCE == null || !SweepAttackVFX.INSTANCE.isEnabled()) return;
        if (target == null) return;

        SweepAttackVFX.INSTANCE.spawnSweepEffect(
                mc.player,
                target.getX(),
                target.getY(),
                target.getZ()
        );
    }
}