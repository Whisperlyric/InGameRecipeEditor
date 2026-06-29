package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.workspace.DebugSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 设置Schema导出模式网络包
 * 服务器发送到客户端，同步Schema导出开关状态
 */
public class SetSchemaExportPacket {
    private final boolean enabled;

    public SetSchemaExportPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(SetSchemaExportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
    }

    public static SetSchemaExportPacket decode(FriendlyByteBuf buffer) {
        return new SetSchemaExportPacket(buffer.readBoolean());
    }

    public static void handle(SetSchemaExportPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DebugSettings.setSchemaExportEnabled(packet.enabled));
        context.setPacketHandled(true);
    }
}