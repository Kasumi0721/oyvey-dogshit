package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.mixin.network.MixinServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

/**
 * MaceSpoof —— 重锤假下落。
 *
 * 两种模式：
 *   Vanilla —— 攻击瞬间发几个假位置包（Y 抖动）
 *   NCP     —— 拦截所有移动包，把 onGround 改成 false（隐蔽）
 */
public class MaceSpoof extends Module {
    public static MaceSpoof INSTANCE;

    public enum Mode { Vanilla, NCP }

    private final Setting<Mode> mode = mode("Mode", Mode.Vanilla);

    // Vanilla 模式设置
    private final Setting<Float> height = num("Height", 25f, 0f, 2000f,
            () -> mode.getValue() == Mode.Vanilla);
    private final Setting<Boolean> onlyGround = bool("OnlyGround", true,
            () -> mode.getValue() == Mode.Vanilla);
    private final Setting<Integer> fakeCount = num("Fake Count", 4, 1, 8,
            () -> mode.getValue() == Mode.Vanilla);

    // 通用设置
    private final Setting<Boolean> avoidLiquid = bool("AvoidLiquid", true);

    private boolean spoofing = false;

    public MaceSpoof() {
        super("MaceSpoof", "重锤假下落加伤", Category.COMBAT);
        INSTANCE = this;
    }

    @Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (spoofing) return;
        if (nullCheck()) return;

        // 主手必须是重锤
        if (!mc.player.getMainHandItem().is(Items.MACE)) return;

        // 液体中不触发
        if (avoidLiquid.getValue()
                && (mc.player.isInLava() || mc.player.isUnderWater())) return;

        switch (mode.getValue()) {
            case Vanilla -> handleVanilla(event);
            case NCP -> handleNcp(event);
        }
    }

    // ==================== Vanilla 模式 ====================

    private void handleVanilla(PacketEvent.Send event) {
        // 只在地面时触发
        if (onlyGround.getValue() && !mc.player.onGround()) return;

        // 只处理实体交互包
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet)) return;
        if (!isAttackAction(packet)) return;

        spoofing = true;
        try {
            int n = fakeCount.getValue();
            for (int i = 0; i < n; i++) sendFakeY(0);
            sendFakeY(height.getValue());
            sendFakeY(0);
        } finally {
            spoofing = false;
        }
    }

    private void sendFakeY(double offset) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(),
                mc.player.getY() + offset,
                mc.player.getZ(),
                false,
                false));
    }

    private static boolean isAttackAction(ServerboundInteractPacket packet) {
        try {
            for (Field f : packet.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(packet);
                if (val instanceof Enum<?> e) {
                    if (e.name().equals("ATTACK")) return true;
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    // ==================== NCP 模式 ====================

    private void handleNcp(PacketEvent.Send event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        // 已经在空中的包不用改
        if (!packet.isOnGround()) return;

        try {
            ((MixinServerboundMovePlayerPacket) (Object) packet).setOnGround(false);
        } catch (Throwable ignored) {}
    }
}