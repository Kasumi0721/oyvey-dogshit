package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.TrailPoint;
import me.alpha432.oyvey.util.render.TrailRenderer;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Trail extends Module {
    public static Trail INSTANCE;

    public enum Style { Aurora, Arcane, Cyber, Inferno, Void }
    public enum ColorMode { Gradient, Rainbow, Pulse, Static }
    public enum Orientation { Billboard, Vertical, Horizontal }

    // 外观
    private final Setting<Style> style = mode("Style", Style.Aurora);
    private final Setting<ColorMode> colorMode = mode("Color Mode", ColorMode.Gradient);
    private final Setting<Color> primaryColor = color("Primary Color", new Color(0xFF79E8FF, true));
    private final Setting<Color> secondaryColor = color("Secondary Color", new Color(0xFF9B6CFF, true));
    private final Setting<Color> accentColor = color("Accent Color", new Color(0xFFFFF1B8, true));

    // 几何
    private final Setting<Orientation> orientation = mode("Orientation", Orientation.Billboard);
    private final Setting<Float> width = num("Width", 0.34f, 0.03f, 1.8f);
    private final Setting<Float> tailWidth = num("Tail Width", 0.08f, 0.0f, 1.0f);
    private final Setting<Float> heightOffset = num("Height Offset", 0.92f, 0.0f, 2.2f);

    // 时间 / 采集
    private final Setting<Float> life = num("Life", 1.15f, 0.1f, 5.0f);
    private final Setting<Integer> maxPoints = num("Max Points", 120, 8, 320);
    private final Setting<Integer> captureInterval = num("Capture Interval", 1, 1, 8);
    private final Setting<Boolean> trailWhileIdle = bool("Trail While Idle", false);
    private final Setting<Float> minMovement = num("Min Movement", 0.012f, 0.0f, 0.2f,
            () -> !trailWhileIdle.getValue());

    // 材质
    private final Setting<Float> opacity = num("Opacity", 0.88f, 0.02f, 1.0f);
    private final Setting<Float> brightness = num("Brightness", 1.35f, 0.1f, 3.0f);
    private final Setting<Float> glow = num("Glow", 1.4f, 0.0f, 2.5f);
    private final Setting<Float> coreWidth = num("Core Width", 0.34f, 0.04f, 1.0f);
    private final Setting<Float> edgeGlow = num("Edge Glow", 0.7f, 0.0f, 2.0f);
    private final Setting<Float> flowSpeed = num("Flow Speed", 1.0f, -3.0f, 3.0f);

    // 性能 / 渲染
    private final Setting<Boolean> throughWalls = bool("Through Walls", false);
    private final Setting<Float> renderDistance = num("Render Distance", 48.0f, 8.0f, 160.0f);

    private final List<TrailPoint> points = new ArrayList<>();
    private int tickCounter;

    public Trail() {
        super("Trail", "玩家能量拖尾", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        points.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        points.clear();
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        final float dt = 0.05f;
        for (TrailPoint p : points) p.tick(dt);
        points.removeIf(TrailPoint::expired);

        Vec3 velocity = mc.player.getDeltaMovement();
        double hSq = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean moving = hSq > minMovement.getValue() * minMovement.getValue();

        tickCounter++;
        if (tickCounter < captureInterval.getValue()) return;
        tickCounter = 0;

        if (!moving && !trailWhileIdle.getValue()) return;

        Vec3 position = mc.player.position().add(0.0, heightOffset.getValue(), 0.0);
        if (!points.isEmpty() && points.get(0).pos.distanceToSqr(position) < 1.0e-6) return;

        points.add(0, new TrailPoint(position, life.getValue()));
        while (points.size() > maxPoints.getValue()) points.remove(points.size() - 1);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || points.size() < 2) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        double maxD = renderDistance.getValue();
        if (camera.position().distanceToSqr(points.get(0).pos) > maxD * maxD) return;

        PoseStack poseStack = event.getMatrix();
        TrailRenderer.draw(poseStack, points, new TrailRenderer.TrailConfig(
                primaryColor.getValue().getRGB(),
                secondaryColor.getValue().getRGB(),
                accentColor.getValue().getRGB(),
                colorMode.getValue().ordinal(),
                orientation.getValue().ordinal(),
                width.getValue(),
                tailWidth.getValue(),
                opacity.getValue(),
                brightness.getValue(),
                glow.getValue(),
                coreWidth.getValue(),
                edgeGlow.getValue(),
                flowSpeed.getValue(),
                false,
                throughWalls.getValue()
        ));
    }
}