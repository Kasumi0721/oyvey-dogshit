package me.alpha432.oyvey.mixin.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface MixinSingleQuadParticle {

    @Accessor("rCol")
    void oyvey$setRed(float red);

    @Accessor("gCol")
    void oyvey$setGreen(float green);

    @Accessor("bCol")
    void oyvey$setBlue(float blue);

    @Accessor("alpha")
    void oyvey$setAlpha(float alpha);
}