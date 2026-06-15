package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 配方删除网络包
 * 客户端发送到服务器，请求标记/取消标记生成配方为待删除
 * 服务器处理后发送聊天栏提示
 */
public class RecipeDeletePacket {
    private final String recipeId;
    private final boolean pending;

    public RecipeDeletePacket(String recipeId, boolean pending) {
        this.recipeId = recipeId;
        this.pending = pending;
    }

    /**
     * 编码网络包
     */
    public static void encode(RecipeDeletePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId);
        buffer.writeBoolean(packet.pending);
    }

    /**
     * 解码网络包
     */
    public static RecipeDeletePacket decode(FriendlyByteBuf buffer) {
        String recipeId = buffer.readUtf();
        boolean pending = buffer.readBoolean();
        return new RecipeDeletePacket(recipeId, pending);
    }

    /**
     * 处理网络包（服务器端）
     */
    public static void handle(RecipeDeletePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            // 检查玩家权限（需要管理员权限）
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.no_permission"));
                return;
            }

            // 初始化服务器端管理器
            GeneratedRecipesManager.serverInit();

            // 设置生成配方待删除状态
            GeneratedRecipesManager.serverSetGeneratedRecipeDeletionPending(packet.recipeId, packet.pending);

            // 发送聊天栏提示
            if (packet.pending) {
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.recipe_delete_marked", packet.recipeId));
            } else {
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.recipe_delete_unmarked", packet.recipeId));
            }

            // 同步到所有客户端
            if (player.getServer() != null) {
                for (ServerPlayer otherPlayer : player.getServer().getPlayerList().getPlayers()) {
                    NetworkHandler.syncToClient(otherPlayer);
                }
            }
        });
        context.setPacketHandled(true);
    }

    public String recipeId() {
        return recipeId;
    }

    public boolean pending() {
        return pending;
    }
}