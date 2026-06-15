package dev.whisperlyric.ingamerecipeeditor.network;

import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 同步禁用配方网络包
 * 服务器发送到客户端，同步禁用配方列表
 */
public class SyncDisabledRecipesPacket {
    private final List<DisabledRecipesManager.DisabledRecipeEntry> recipes;

    public SyncDisabledRecipesPacket(List<DisabledRecipesManager.DisabledRecipeEntry> recipes) {
        this.recipes = recipes;
    }

    /**
     * 编码网络包
     */
    public static void encode(SyncDisabledRecipesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.recipes.size());
        for (DisabledRecipesManager.DisabledRecipeEntry entry : packet.recipes) {
            buffer.writeUtf(entry.recipeId());
            buffer.writeUtf(entry.recipeJson());
        }
    }

    /**
     * 解码网络包
     */
    public static SyncDisabledRecipesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<DisabledRecipesManager.DisabledRecipeEntry> recipes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String recipeId = buffer.readUtf();
            String recipeJson = buffer.readUtf();
            recipes.add(new DisabledRecipesManager.DisabledRecipeEntry(recipeId, recipeJson));
        }
        return new SyncDisabledRecipesPacket(recipes);
    }

    /**
     * 处理网络包（客户端）
     */
    public static void handle(SyncDisabledRecipesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 解析禁用配方列表
            List<String> recipeIds = new ArrayList<>();
            Map<String, String> recipeJsonMap = new HashMap<>();
            for (DisabledRecipesManager.DisabledRecipeEntry entry : packet.recipes) {
                recipeIds.add(entry.recipeId());
                if (!entry.recipeJson().isEmpty()) {
                    recipeJsonMap.put(entry.recipeId(), entry.recipeJson());
                }
            }

            // 更新客户端状态
            DisabledRecipesManager.clientUpdateDisabledRecipes(recipeIds, recipeJsonMap);
        });
        context.setPacketHandled(true);
    }

    public List<DisabledRecipesManager.DisabledRecipeEntry> recipes() {
        return recipes;
    }
}