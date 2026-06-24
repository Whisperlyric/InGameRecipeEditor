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
 * 保存路径规则：
 * - 编辑现有配方（minecraft:type）: config/.../recipes/<namespace>/<type_path>/<path>.json（覆写）
 * - 新建自定义配方（minecraft:type）: config/.../recipes/<namespace>/<type_path>/custom/<name>_<counter>.json
 * - 非minecraft:type配方: config/.../recipes/<namespace>/<path>.json
 */
public class RecipeExportPacket {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String recipeId;
    private final String recipeJson;
    private final boolean isNewRecipe;

    public RecipeExportPacket(String recipeId, String recipeJson, boolean isNewRecipe) {
        this.recipeId = recipeId;
        this.recipeJson = recipeJson;
        this.isNewRecipe = isNewRecipe;
    }

    public static void encode(RecipeExportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.recipeId);
        buffer.writeUtf(packet.recipeJson);
        buffer.writeBoolean(packet.isNewRecipe);
    }

    public static RecipeExportPacket decode(FriendlyByteBuf buffer) {
        String recipeId = buffer.readUtf(32767);
        String recipeJson = buffer.readUtf(32767);
        boolean isNewRecipe = buffer.readBoolean();
        return new RecipeExportPacket(recipeId, recipeJson, isNewRecipe);
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

                Path savedPath = saveRecipeFile(recipeIdLoc, recipeObj, packet.isNewRecipe);
                if (savedPath != null) {
                    // 显示相对配置目录的路径
                    Path configDir = FMLPaths.CONFIGDIR.get().resolve("ingamerecipeeditor/recipes");
                    String relativePath = configDir.relativize(savedPath).toString();
                    player.sendSystemMessage(Component.translatable(
                        "ingamerecipeeditor.message.recipe_export_success", relativePath));
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

    /**
     * 保存配方文件，返回实际写入路径，失败返回 null。
     * 路径规则：
     * - 有 type 字段：recipes/<namespace>/<type_path>/<recipe_path>.json
     *   - 新建模式：recipes/<namespace>/<type_path>/custom/custom_{type}(_counter).json
     * - 无 type 字段：recipes/<namespace>/<recipe_path>.json
     */
    private static Path saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson, boolean isNewRecipe) {
        try {
            String recipePath = recipeId.getPath();
            String recipeNamespace = recipeId.getNamespace();

            String type = recipeJson.has("type") ? recipeJson.get("type").getAsString() : null;

            if (type != null) {
                // 有 type：按 type 组织目录
                ResourceLocation typeLoc = ResourceLocation.tryParse(type);
                String typePath = typeLoc != null ? typeLoc.getPath() : type.replace(':', '_');

                Path baseDir = FMLPaths.CONFIGDIR.get()
                    .resolve("ingamerecipeeditor/recipes")
                    .resolve(recipeNamespace)
                    .resolve(typePath);

                Path filePath;
                if (isNewRecipe) {
                    baseDir = baseDir.resolve("custom");
                    Files.createDirectories(baseDir);
                    String fileName = "custom_" + typePath;
                    filePath = baseDir.resolve(fileName + ".json");
                    int counter = 1;
                    while (Files.exists(filePath)) {
                        filePath = baseDir.resolve(fileName + "_" + counter + ".json");
                        counter++;
                    }
                } else {
                    Files.createDirectories(baseDir);
                    String fileName = recipePath.replace('/', '_');
                    filePath = baseDir.resolve(fileName + ".json");
                }

                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    GSON.toJson(recipeJson, writer);
                }
                InGameRecipeEditor.LOGGER.info("配方已导出: {} -> {}", recipeId, filePath);
                return filePath;

            } else {
                // 无 type：扁平目录
                Path baseDir = FMLPaths.CONFIGDIR.get()
                    .resolve("ingamerecipeeditor/recipes")
                    .resolve(recipeNamespace);
                Files.createDirectories(baseDir);

                String fileName = recipePath.replace('/', '_');
                Path filePath = baseDir.resolve(fileName + ".json");
                if (isNewRecipe) {
                    int counter = 1;
                    while (Files.exists(filePath)) {
                        filePath = baseDir.resolve(fileName + "_" + counter + ".json");
                        counter++;
                    }
                }
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    GSON.toJson(recipeJson, writer);
                }
                InGameRecipeEditor.LOGGER.info("配方已导出: {} -> {}", recipeId, filePath);
                return filePath;
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return null;
        }
    }
}
