package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 批量配方禁用/启用网络包
 * 客户端发送到服务器，批量请求禁用或启用配方
 * 服务器处理后只发送一次合并的聊天栏提示
 */
public class RecipeBatchTogglePacket {
    private final List<String> recipeIds;
    private final boolean disable;

    public RecipeBatchTogglePacket(List<String> recipeIds, boolean disable) {
        this.recipeIds = recipeIds;
        this.disable = disable;
    }

    public static void encode(RecipeBatchTogglePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.recipeIds.size());
        for (String id : packet.recipeIds) {
            buffer.writeUtf(id);
        }
        buffer.writeBoolean(packet.disable);
    }

    public static RecipeBatchTogglePacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> recipeIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            recipeIds.add(buffer.readUtf());
        }
        boolean disable = buffer.readBoolean();
        return new RecipeBatchTogglePacket(recipeIds, disable);
    }

    public static void handle(RecipeBatchTogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.no_permission"));
                return;
            }

            DisabledRecipesManager.serverInit();

            int count = 0;
            for (String recipeId : packet.recipeIds) {
                if (packet.disable) {
                    DisabledRecipesManager.serverDisableRecipe(recipeId);
                } else {
                    DisabledRecipesManager.serverEnableRecipe(recipeId);
                }
                count++;
            }

            if (count > 0) {
                if (packet.disable) {
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.recipes_batch_disabled", count));
                } else {
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.recipes_batch_enabled", count));
                    // 启用配方需要reload才生效，只发送一次提示
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.reload_required_for_enable"));
                }
            }
        });
        context.setPacketHandled(true);
    }

    public List<String> recipeIds() {
        return recipeIds;
    }

    public boolean disable() {
        return disable;
    }
}
