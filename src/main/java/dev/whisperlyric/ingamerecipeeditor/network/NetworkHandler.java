package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 网络通信处理器
 * 使用 Forge 1.20.1 的 SimpleNetworkWrapper
 */
public class NetworkHandler {
    private static final ResourceLocation CHANNEL_ID = ResourceLocation.parse("ingamerecipeeditor:main");
    private static SimpleChannel network;
    private static int packetId = 0;

    /**
     * 初始化网络通道
     */
    public static void init() {
        network = NetworkRegistry.ChannelBuilder.named(CHANNEL_ID)
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions("1"::equals)
            .serverAcceptedVersions("1"::equals)
            .simpleChannel();

        // 注册配方禁用/启用网络包
        network.messageBuilder(RecipeTogglePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RecipeTogglePacket::encode)
            .decoder(RecipeTogglePacket::decode)
            .consumerMainThread(RecipeTogglePacket::handle)
            .add();

        // 注册同步禁用配方网络包
        network.messageBuilder(SyncDisabledRecipesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncDisabledRecipesPacket::encode)
            .decoder(SyncDisabledRecipesPacket::decode)
            .consumerMainThread(SyncDisabledRecipesPacket::handle)
            .add();

        // 注册配方删除网络包
        network.messageBuilder(RecipeDeletePacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(RecipeDeletePacket::encode)
            .decoder(RecipeDeletePacket::decode)
            .consumerMainThread(RecipeDeletePacket::handle)
            .add();

        // 注册同步待删除生成配方网络包
        network.messageBuilder(SyncPendingDeletesPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SyncPendingDeletesPacket::encode)
            .decoder(SyncPendingDeletesPacket::decode)
            .consumerMainThread(SyncPendingDeletesPacket::handle)
            .add();

        InGameRecipeEditor.LOGGER.info("网络处理器初始化完成，共 {} 种网络包", packetId);
    }

    private static int nextId() {
        return packetId++;
    }

    /**
     * 发送配方禁用/启用请求到服务器
     */
    public static void sendRecipeToggle(String recipeId, boolean disable) {
        if (network != null) {
            network.sendToServer(new RecipeTogglePacket(recipeId, disable));
        }
    }

    /**
     * 发送配方删除请求到服务器
     */
    public static void sendRecipeDelete(String recipeId, boolean pending) {
        if (network != null) {
            network.sendToServer(new RecipeDeletePacket(recipeId, pending));
        }
    }

    /**
     * 同步禁用配方状态到客户端
     */
    public static void syncToClient(ServerPlayer player) {
        if (network == null) {
            return;
        }

        // 构建禁用配方列表
        List<String> recipeIds = new ArrayList<>(DisabledRecipesManager.getDisabledRecipes());
        Map<String, String> recipeJsonMap = DisabledRecipesManager.getClientRecipeJsonCache();
        
        List<DisabledRecipesManager.DisabledRecipeEntry> entries = new ArrayList<>();
        for (String id : recipeIds) {
            String json = recipeJsonMap.getOrDefault(id, "");
            entries.add(new DisabledRecipesManager.DisabledRecipeEntry(id, json));
        }
        
        network.sendTo(new SyncDisabledRecipesPacket(entries), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        
        // 同步待删除生成配方
        List<String> pendingDeletes = new ArrayList<>(GeneratedRecipesManager.getPendingGeneratedRecipeDeletes());
        network.sendTo(new SyncPendingDeletesPacket(pendingDeletes), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        
        InGameRecipeEditor.LOGGER.debug("同步配方状态到客户端: {}", player.getName().getString());
    }

    /**
     * 同步禁用配方状态到所有客户端
     */
    public static void syncToAllClients(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            syncToClient(player);
        }
    }
}