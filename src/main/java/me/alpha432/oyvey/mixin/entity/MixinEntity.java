package me.alpha432.oyvey.mixin.entity;

import me.alpha432.oyvey.event.impl.entity.MoveEvent;
import me.alpha432.oyvey.event.impl.entity.TotemParticleEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;
import static me.alpha432.oyvey.util.traits.Util.mc;

@Mixin(Entity.class)
public class MixinEntity {

    /**
     * 拦截 Entity.move(MoverType, Vec3)，把移动向量交给模块处理
     */
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3 onMoveVec3(Vec3 movement, MoverType type) {
        if ((Object) this != mc.player) return movement;

        MoveEvent event = new MoveEvent(0, type, movement.x, movement.y, movement.z);
        EVENT_BUS.post(event);

        return new Vec3(event.getX(), event.getY(), event.getZ());
    }

    /**
     * 拦截 Entity.handleEntityEvent(byte)，图腾粒子事件 id = 35
     */
    
}