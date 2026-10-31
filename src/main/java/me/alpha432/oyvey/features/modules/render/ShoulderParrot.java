package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.world.entity.animal.parrot.Parrot;

import java.util.Optional;

/**
 * 肩膀上的鹦鹉 —— 直接调用 1.21.11 Player 的原生肩膀鹦鹉 API。
 * 原版渲染、原版动画、原版位置。
 */
public class ShoulderParrot extends Module {
    public static ShoulderParrot INSTANCE;

    /** 鹦鹉变种：0=红蓝 1=蓝 2=绿 3=黄蓝 4=灰 */
    private final Setting<Integer> variant = num("Variant", 0, 0, 4);
    private final Setting<Boolean> bothShoulders = bool("Both Shoulders", false);
    private final Setting<Boolean> leftShoulder = bool("Left Shoulder", false,
            () -> !bothShoulders.getValue());

    public ShoulderParrot() {
        super("ShoulderParrot", "肩膀上的鹦鹉", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        apply();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        try {
            mc.player.setShoulderParrotLeft(Optional.empty());
            mc.player.setShoulderParrotRight(Optional.empty());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        apply();
    }

    private void apply() {
        int id = Math.max(0, Math.min(4, variant.getValue()));
        Parrot.Variant v = Parrot.Variant.byId(id);
        Optional<Parrot.Variant> opt = Optional.of(v);

        try {
            if (bothShoulders.getValue()) {
                mc.player.setShoulderParrotLeft(opt);
                mc.player.setShoulderParrotRight(opt);
            } else if (leftShoulder.getValue()) {
                mc.player.setShoulderParrotLeft(opt);
                mc.player.setShoulderParrotRight(Optional.empty());
            } else {
                mc.player.setShoulderParrotRight(opt);
                mc.player.setShoulderParrotLeft(Optional.empty());
            }
        } catch (Throwable ignored) {}
    }
}