package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 纯诊断模块 —— 不攻击，只打印玩家实体信息。
 * 用途：确认 mc.level.players() 里到底有没有目标。
 */
public class TestMace extends Module {
    public static TestMace INSTANCE;

    private final Setting<Integer> range = num("Range", 30, 1, 100);
    private final Setting<Boolean> verbose = bool("Verbose", true);

    public TestMace() {
        super("TestMace", "重锤诊断（不攻击）", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        System.out.println("========== TestMace 开启 ==========");
        dumpAll();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        dumpAll();
    }

    private void dumpAll() {
        System.out.println("---- TestMace tick ----");
        System.out.println("[TestMace] mc.player="
                + (mc.player == null ? "null" : mc.player.getName().getString())
                + " pos=" + fmt(mc.player.position()));

        // ===== 1. mc.level.players() =====
        int playerCount = 0;
        double maxSq = range.getValue() * range.getValue();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            playerCount++;
            double d2 = mc.player.distanceToSqr(p);
            System.out.println("[TestMace][PLAYERS] "
                    + pad(p.getName().getString(), 20)
                    + " dist=" + pad(String.format("%.2f", Math.sqrt(d2)), 8)
                    + " alive=" + p.isAlive()
                    + " spec=" + p.isSpectator()
                    + " hp=" + p.getHealth()
                    + " iframe=" + p.invulnerableTime
                    + " id=" + p.getId()
                    + " pos=" + fmt(p.position())
                    + " inRange=" + (d2 <= maxSq));
        }
        System.out.println("[TestMace] players in level (excl. self) = " + playerCount);

        // ===== 2. entitiesForRendering() 里所有 LivingEntity =====
        int livingCount = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity)) continue;
            if (e == mc.player) continue;
            double d2 = mc.player.distanceToSqr(e);
            if (d2 > maxSq) continue;
            livingCount++;
            if (verbose.getValue()) {
                System.out.println("[TestMace][LIVING] "
                        + pad(e.getClass().getSimpleName(), 30)
                        + " name='" + e.getName().getString() + "'"
                        + " dist=" + pad(String.format("%.2f", Math.sqrt(d2)), 8)
                        + " id=" + e.getId()
                        + " pos=" + fmt(e.position()));
            }
        }
        System.out.println("[TestMace] LivingEntity in range = " + livingCount);

        // ===== 3. 玩家 Tab 列表 =====
        try {
            var conn = mc.getConnection();
            if (conn != null) {
                var infos = conn.getOnlinePlayers();
                System.out.println("[TestMace] Tab list size = " + infos.size());
                if (verbose.getValue()) {
                    for (var info : infos) {
                        System.out.println("[TestMace][TAB]    "
                                + info.getProfile().name()
                                + " uuid=" + info.getProfile().id());
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[TestMace] Tab list error: " + t);
        }

        // ===== 4. 你手持物品 =====
        System.out.println("[TestMace] mainHand="
                + mc.player.getMainHandItem().getItem()
                + " offHand=" + mc.player.getOffhandItem().getItem());

        // ===== 5. 你自己 AABB =====
        AABB self = mc.player.getBoundingBox();
        System.out.println("[TestMace] self AABB=" + self);

        System.out.println("---- TestMace end ----");
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static String pad(String s, int n) {
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }
}