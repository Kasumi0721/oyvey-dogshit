package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.CaptureMark;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TargetESP extends Module {
    public static TargetESP INSTANCE;

    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> skipFriends = bool("SkipFriends", true);
    private final Setting<Boolean> skipDead = bool("SkipDead", true);
    private final Setting<Boolean> autoTarget = bool("AutoTarget", true);
    private final Setting<Float> range = num("Range", 20f, 0f, 50f);
    private final Setting<Integer> targetCount = num("TargetCount", 1, 1, 10);
    private final Setting<Boolean> rainbow = bool("Rainbow", false);
    private final Setting<Color> color = color("Color", new Color(189, 153, 255, 255));

    private final List<Entity> targets = new ArrayList<>();

    public TargetESP() {
        super("TargetESP", "目标ESP", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        targets.clear();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (mc.level == null || mc.player == null) return;

        CaptureMark.tick();
        targets.clear();

        if (!autoTarget.getValue()) return;

        float r = range.getValue();
        List<Entity> valid = new ArrayList<>();

        if (players.getValue()) {
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                if (!p.isAlive()) continue;
                if (skipFriends.getValue()) {
                    try {
                        if (OyVey.friendManager.isFriend(p)) continue;
                    } catch (Throwable ignored) {}
                }
                if (skipDead.getValue() && p.getHealth() <= 0f) continue;
                if (r > 0f && mc.player.distanceTo(p) > r) continue;
                valid.add(p);
            }
        }

        valid.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));

        int count = Math.min(targetCount.getValue(), valid.size());
        for (int i = 0; i < count; i++) {
            targets.add(valid.get(i));
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (targets.isEmpty()) return;

        for (Entity e : targets) {
            if (e.isRemoved()) continue;
            CaptureMark.render(e, color.getValue(), rainbow.getValue(), event.getDelta());
        }
    }
}