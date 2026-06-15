package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 同步待删除生成配方网络包
 * 服务器发送到客户端，同步待删除生成配方列表
 */
public class SyncPendingDeletesPacket {
    private final List<String> pendingDeletes;

    public SyncPendingDeletesPacket(List<String> pendingDeletes) {
        this.pendingDeletes = pendingDeletes;
    }

    /**
     * 编码网络包
     */
    public static void encode(SyncPendingDeletesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.pendingDeletes.size());
        for (String recipeId : packet.pendingDeletes) {
            buffer.writeUtf(recipeId);
        }
    }

    /**
     * 解码网络包
     */
    public static SyncPendingDeletesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> pendingDeletes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            pendingDeletes.add(buffer.readUtf());
        }
        return new SyncPendingDeletesPacket(pendingDeletes);
    }

    /**
     * 处理网络包（客户端）
     */
    public static void handle(SyncPendingDeletesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 更新客户端待删除生成配方列表
            GeneratedRecipesManager.clientUpdatePendingGeneratedRecipeDeletes(packet.pendingDeletes);
        });
        context.setPacketHandled(true);
    }

    public List<String> pendingDeletes() {
        return pendingDeletes;
    }
}