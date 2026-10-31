package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

public class AntiParticle extends Module {
    public static AntiParticle INSTANCE;

    public AntiParticle() {
        super("AntiParticle", "拦截服务器粒子包避免卡顿", Category.RENDER);
        INSTANCE = this;
    }

    @Subscribe
    public void onReceivePacket(PacketEvent.Receive event) {
        if (!isEnabled()) return;

        // 拦截所有粒子数据包
        if (event.getPacket() instanceof ClientboundLevelParticlesPacket) {
            event.cancel();
        }
    }
}