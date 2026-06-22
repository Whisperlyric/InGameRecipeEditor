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

                boolean success = saveRecipeFile(recipeIdLoc, recipeObj, packet.isNewRecipe);
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

    /**
     * 保存配方文件
     * 路径规则：
     * - 编辑现有配方（type为minecraft:开头）: recipes/<namespace>/<type_path>/<path>.json（覆写）
     * - 新建自定义配方（type为minecraft:开头）: recipes/<namespace>/<type_path>/custom/<name>_<counter>.json
     * - 其他配方: recipes/<namespace>/<path>.json
     */
    private static boolean saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson, boolean isNewRecipe) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();

            Path baseDir = FMLPaths.CONFIGDIR.get()
                .resolve("ingamerecipeeditor/recipes")
                .resolve(namespace);

            // 判断type是否为minecraft:开头
            String type = recipeJson.has("type") ? recipeJson.get("type").getAsString() : null;
            boolean isMinecraftType = type != null && type.startsWith("minecraft:");

            if (isMinecraftType) {
                // minecraft:type 配方：使用 type_path 作为子目录
                String typePath = type.substring("minecraft:".length());
                baseDir = baseDir.resolve(typePath);

                if (isNewRecipe) {
                    // 新建自定义配方：custom/<name>_<counter>.json
                    baseDir = baseDir.resolve("custom");
                    Files.createDirectories(baseDir);
                    String fileName = path.replace('/', '_');
                    Path recipePath = baseDir.resolve(fileName + ".json");
                    int counter = 1;
                    while (Files.exists(recipePath)) {
                        recipePath = baseDir.resolve(fileName + "_" + counter + ".json");
                        counter++;
                    }
                    try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                        GSON.toJson(recipeJson, writer);
                    }
                    InGameRecipeEditor.LOGGER.info("新建配方已导出: {} -> {}", recipeId, recipePath);
                } else {
                    // 编辑现有配方：覆写
                    Files.createDirectories(baseDir);
                    String fileName = path.replace('/', '_');
                    Path recipePath = baseDir.resolve(fileName + ".json");
                    try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                        GSON.toJson(recipeJson, writer);
                    }
                    InGameRecipeEditor.LOGGER.info("配方已导出: {} -> {}", recipeId, recipePath);
                }
            } else {
                // 非minecraft:type配方：保持 recipes/<namespace>/<path>.json
                Files.createDirectories(baseDir);
                String fileName = path.replace('/', '_');
                Path recipePath = baseDir.resolve(fileName + ".json");
                if (isNewRecipe) {
                    int counter = 1;
                    while (Files.exists(recipePath)) {
                        recipePath = baseDir.resolve(fileName + "_" + counter + ".json");
                        counter++;
                    }
                }
                try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                    GSON.toJson(recipeJson, writer);
                }
                InGameRecipeEditor.LOGGER.info("配方已导出: {} -> {}", recipeId, recipePath);
            }

            return true;
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return false;
        }
    }
}
