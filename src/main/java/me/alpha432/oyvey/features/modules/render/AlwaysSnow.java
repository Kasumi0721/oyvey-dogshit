package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

public class AlwaysSnow extends Module {
    public static AlwaysSnow INSTANCE;

    private final Setting<Boolean> thunder = bool("Thunder", false, () -> true);
    private final Setting<Boolean> restoreOnDisable = bool("RestoreOnDisable", true, () -> true);

    private float savedRain = 0f;
    private float savedThunder = 0f;

    public AlwaysSnow() {
        super("AlwaysSnow", "一直下雪", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        savedRain = mc.level.getRainLevel(1.0f);
        savedThunder = mc.level.getThunderLevel(1.0f);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        mc.level.setRainLevel(1.0f);
        mc.level.setThunderLevel(thunder.getValue() ? 1.0f : 0.0f);
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        if (restoreOnDisable.getValue()) {
            mc.level.setRainLevel(savedRain);
            mc.level.setThunderLevel(savedThunder);
        }
    }
}