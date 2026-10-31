package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.event.impl.entity.TotemEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.ModelPlayer;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;

public class PopChams extends Module {
    public static PopChams INSTANCE;

    private final Setting<Boolean> noSelf = bool("NoSelf", true, () -> true);
    private final Setting<Float> fadeTime = num("FadeTime", 800f, 100f, 3000f, () -> true);
    private final Setting<Float> yOffset = num("YOffset", 0f, -10f, 10f, () -> true);
    private final Setting<Float> scale = num("Scale", 1f, 0.1f, 2f, () -> true);
    private final Setting<Float> yawSpin = num("Yaw", 0f, 0f, 720f, () -> true);
    private final Setting<Boolean> noLimb = bool("NoLimb", false, () -> true);
    private final Setting<Color> fillColor = color("Fill", new Color(255, 255, 255, 180), () -> true);
    private final Setting<Boolean> outline = bool("Outline", true, () -> true);
    private final Setting<Color> lineColor = color("Line", new Color(255, 255, 255, 220), () -> true);

    private final CopyOnWriteArrayList<Person> popList = new CopyOnWriteArrayList<>();

    public PopChams() {
        super("PopChams", "图腾爆炸渲染", Category.RENDER);
        INSTANCE = this;
    }

    @Subscribe
    public void onTotemPop(TotemEvent event) {
        Player player = event.getPlayer();
        if (noSelf.getValue() && player == mc.player) return;
        popList.add(new Person(player));
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (mc.level == null) return;

        PoseStack poseStack = event.getMatrix();
        long now = System.currentTimeMillis();

        popList.removeIf(p -> p.render(poseStack, now));
    }

    @Override
    public void onDisable() {
        popList.clear();
    }

    private class Person {
        public final ModelPlayer model;
        public final long popTime;

        public Person(Player original) {
            this.popTime = System.currentTimeMillis();
            this.model = new ModelPlayer(original);
        }

        public boolean render(PoseStack poseStack, long now) {
            float progress = (now - popTime) / fadeTime.getValue();
            if (progress >= 1.0f) return true;

            float alpha = 1.0f - progress;
            double yOff = yOffset.getValue() * progress;

            model.render(poseStack,
                    fillColor.getValue(),
                    lineColor.getValue(),
                    outline.getValue(),
                    alpha,
                    yawSpin.getValue() * progress,
                    scale.getValue(),
                    yOff,
                    noLimb.getValue());

            return false;
        }
    }
}