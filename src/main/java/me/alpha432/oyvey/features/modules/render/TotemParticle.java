package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.entity.TotemParticleEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.ColorUtil;

import java.awt.Color;
import java.util.Random;

public class TotemParticle extends Module {
    public static TotemParticle INSTANCE;

    private final Setting<Float> velocityXZ = num("VelocityXZ", 100f, 0f, 500f);
    private final Setting<Float> velocityY = num("VelocityY", 100f, 0f, 500f);
    private final Setting<Color> color = color("Color", new Color(189, 153, 255, 255));
    private final Setting<Color> color2 = color("Color2", new Color(189, 153, 255, 255));

    private final Random random = new Random();

    public TotemParticle() {
        super("TotemParticle", "自定义图腾粒子", Category.RENDER);
        INSTANCE = this;
    }

    @Subscribe
    public void onTotemParticle(TotemParticleEvent event) {
        // 不要调用 event.cancel()！cancel 会保留原版，不修改任何东西

        event.velocityX *= velocityXZ.getValue() / 100.0;
        event.velocityY *= velocityY.getValue() / 100.0;
        event.velocityZ *= velocityXZ.getValue() / 100.0;

        event.color = ColorUtil.fadeColor(
                color.getValue(),
                color2.getValue(),
                random.nextDouble()
        );
    }
}