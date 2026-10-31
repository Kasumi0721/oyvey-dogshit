package me.alpha432.oyvey.mixin.network;

import me.alpha432.oyvey.event.impl.entity.EntityRemoveEvent;
import me.alpha432.oyvey.event.impl.entity.TotemEvent;
import me.alpha432.oyvey.event.impl.entity.VelocityUpdateEvent;
import me.alpha432.oyvey.event.impl.network.ChatEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.alpha432.oyvey.util.traits.Util.EVENT_BUS;
import static me.alpha432.oyvey.util.traits.Util.mc;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void sendChat(String content, CallbackInfo ci) {
        if (EVENT_BUS.post(new ChatEvent(content))) {
            ci.cancel();
        }
    }

    /**
     * 拦截实体事件：
     *   eventId 35 = TOTEM_USE → TotemEvent（PopChams 用）
     *   eventId 3  = DEATH     → EntityRemoveEvent（KillEffect 用）
     */
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onHandleEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (mc.level == null) return;

        int eventId = packet.getEventId();

        // TOTEM_USE = 35
        if (eventId == 35) {
            Entity entity = packet.getEntity(mc.level);
            if (entity instanceof Player player) {
                EVENT_BUS.post(new TotemEvent(player));
            }
            return;
        }

        // DEATH = 3
        if (eventId == 3) {
            Entity entity = packet.getEntity(mc.level);
            if (entity != null && entity != mc.player) {
                EVENT_BUS.post(new EntityRemoveEvent(entity, true));
            }
        }
    }

    /**
     * 拦截服务端击退包（ClientboundSetEntityMotionPacket）
     * 用于：
     *   1. Velocity 模块通过 VelocityUpdateEvent 读取/修改/取消击退
     *   2. Speed 模块的受击加速
     */
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void onHandleSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(packet.getId());
        if (entity == null) return;

        var m = packet.getMovement();
        VelocityUpdateEvent event = new VelocityUpdateEvent(entity, m.x, m.y, m.z);
        EVENT_BUS.post(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.getX() != m.x || event.getY() != m.y || event.getZ() != m.z) {
            entity.setDeltaMovement(event.getX(), event.getY(), event.getZ());
            ci.cancel();
        }
    }
}