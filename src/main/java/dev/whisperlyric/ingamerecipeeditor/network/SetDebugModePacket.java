package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.workspace.DebugSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 设置调试模式网络包
 * 服务器发送到客户端，同步调试开关状态
 */
public class SetDebugModePacket {
    private final boolean enabled;

    public SetDebugModePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(SetDebugModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
    }

    public static SetDebugModePacket decode(FriendlyByteBuf buffer) {
        return new SetDebugModePacket(buffer.readBoolean());
    }

    public static void handle(SetDebugModePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DebugSettings.setEnabled(packet.enabled));
        context.setPacketHandled(true);
    }
}
