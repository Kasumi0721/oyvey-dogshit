package me.alpha432.oyvey.mixin.render;

import me.alpha432.oyvey.features.modules.render.AlwaysSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class MixinBiome {

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void onGetPrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (AlwaysSnow.INSTANCE != null && AlwaysSnow.INSTANCE.isEnabled()) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}