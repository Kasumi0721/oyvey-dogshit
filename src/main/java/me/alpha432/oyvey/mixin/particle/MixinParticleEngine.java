package me.alpha432.oyvey.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.alpha432.oyvey.event.impl.entity.TotemParticleEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @WrapMethod(method = "createParticle")
    private Particle oyvey$onCreateParticle(ParticleOptions options,
                                            double x, double y, double z,
                                            double vx, double vy, double vz,
                                            Operation<Particle> original) {

        if (options.getType() != ParticleTypes.TOTEM_OF_UNDYING) {
            return original.call(options, x, y, z, vx, vy, vz);
        }

        TotemParticleEvent event = TotemParticleEvent.get(vx, vy, vz);
        EVENT_BUS.post(event);

        if (event.isCancelled()) {
            return original.call(options, x, y, z, vx, vy, vz);
        }

        Particle particle = original.call(options, x, y, z,
                event.velocityX, event.velocityY, event.velocityZ);

        if (particle instanceof SingleQuadParticle && event.color != null) {
            MixinSingleQuadParticle accessor = (MixinSingleQuadParticle) particle;
            accessor.oyvey$setRed(event.color.getRed() / 255f);
            accessor.oyvey$setGreen(event.color.getGreen() / 255f);
            accessor.oyvey$setBlue(event.color.getBlue() / 255f);
            accessor.oyvey$setAlpha(event.color.getAlpha() / 255f);
        }

        return particle;
    }
}