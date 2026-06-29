package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 设置禁用配方JEI可见性网络包
 * 服务器发送到客户端，通知客户端更新JEI可见性设置
 */
public class SetJeiVisibilityPacket {
    private final boolean showDisabled;

    public SetJeiVisibilityPacket(boolean showDisabled) {
        this.showDisabled = showDisabled;
    }

    public static void encode(SetJeiVisibilityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.showDisabled);
    }

    public static SetJeiVisibilityPacket decode(FriendlyByteBuf buffer) {
        return new SetJeiVisibilityPacket(buffer.readBoolean());
    }

    public static void handle(SetJeiVisibilityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> JeiRecipeVisibility.setShowDisabledInJei(packet.showDisabled));
        context.setPacketHandled(true);
    }
}
