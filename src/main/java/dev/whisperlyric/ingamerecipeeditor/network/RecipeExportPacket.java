package dev.whisperlyric.ingamerecipeeditor.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 配方导出网络包
 * 客户端发送配方JSON到服务器，服务器保存为JSON文件
 */
public class RecipeExportPacket {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String recipeId;
    private final String recipeJson;

    public RecipeExportPacket(String recipeId, String recipeJson) {
        this.recipeId = recipeId;
        this.recipeJson = recipeJson;
    }

    public static void encode(RecipeExportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId);
        buffer.writeUtf(packet.recipeJson);
    }

    public static RecipeExportPacket decode(FriendlyByteBuf buffer) {
        String recipeId = buffer.readUtf(32767);
        String recipeJson = buffer.readUtf(32767);
        return new RecipeExportPacket(recipeId, recipeJson);
    }

    public static void handle(RecipeExportPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("ingamerecipeeditor.message.no_permission"));
                return;
            }

            try {
                JsonObject recipeObj = JsonParser.parseString(packet.recipeJson).getAsJsonObject();
                ResourceLocation recipeIdLoc = ResourceLocation.parse(packet.recipeId);

                boolean success = saveRecipeFile(recipeIdLoc, recipeObj);
                if (success) {
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.recipe_export_success", packet.recipeId));
                } else {
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.recipe_export_fail", packet.recipeId));
                }
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.error("导出配方失败: {}", packet.recipeId, e);
                player.sendSystemMessage(Component.translatable(
                    "ingamerecipeeditor.message.recipe_export_fail", packet.recipeId));
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();

            Path baseDir = FMLPaths.CONFIGDIR.get()
                .resolve("ingamerecipeeditor/recipes")
                .resolve(namespace);

            Files.createDirectories(baseDir);

            // 使用配方 path 作为文件名
            String fileName = path.replace('/', '_');
            Path recipePath = baseDir.resolve(fileName + ".json");
            int counter = 1;
            while (Files.exists(recipePath)) {
                recipePath = baseDir.resolve(fileName + "_" + counter + ".json");
                counter++;
            }

            // 直接覆写文件（覆盖已有配方时）
            try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                GSON.toJson(recipeJson, writer);
            }

            InGameRecipeEditor.LOGGER.info("配方已导出: {} -> {}", recipeId, recipePath);
            return true;
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return false;
        }
    }
}
