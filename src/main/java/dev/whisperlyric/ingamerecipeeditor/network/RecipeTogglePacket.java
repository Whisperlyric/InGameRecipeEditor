package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 配方禁用/启用网络包
 * 客户端发送到服务器，请求禁用或启用配方
 * 服务器处理后发送聊天栏提示
 */
public class RecipeTogglePacket {
    private final String recipeId;
    private final boolean disable;

    public RecipeTogglePacket(String recipeId, boolean disable) {
        this.recipeId = recipeId;
        this.disable = disable;
    }

    public static void encode(RecipeTogglePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId);
        buffer.writeBoolean(packet.disable);
    }

    public static RecipeTogglePacket decode(FriendlyByteBuf buffer) {
        String recipeId = buffer.readUtf();
        boolean disable = buffer.readBoolean();
        return new RecipeTogglePacket(recipeId, disable);
    }

    public static void handle(RecipeTogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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

            if (packet.disable) {
                DisabledRecipesManager.serverDisableRecipe(packet.recipeId);
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.recipe_disabled", packet.recipeId));
            } else {
                DisabledRecipesManager.serverEnableRecipe(packet.recipeId);
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.recipe_enabled", packet.recipeId));
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.reload_required_for_enable"));
            }
        });
        context.setPacketHandled(true);
    }

    public String recipeId() {
        return recipeId;
    }

    public boolean disable() {
        return disable;
    }
}