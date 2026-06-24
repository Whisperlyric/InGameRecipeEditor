package dev.whisperlyric.ingamerecipeeditor.schema;

import com.google.gson.*;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeEditManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;

/**
 * 补丁注册/应用器
 * 从 data/ingamerecipeeditor/mod_patches 和 data/ingamerecipeeditor/recipe_patches 读取 JSON 定义
 * 将 path->value 填回目标 JSON
 */
public class PatchRegistry {
    private static final String MOD_PATCHES_PATH = "mod_patches";
    private static final String RECIPE_PATCHES_PATH = "recipe_patches";
    private static final Gson GSON = new GsonBuilder().create();

    private static ResourceManager resourceManager;

    /**
     * 由资源重载监听器调用，注入当前 ResourceManager
     */
    public static void setResourceManager(ResourceManager rm) {
        resourceManager = rm;
    }

    /**
     * 载入并应用 mod/recipe 补丁到 base（不修改磁盘）
     */
    public static JsonObject applyPatches(JsonObject base, String recipeId, String recipeType) {
        if (base == null) base = new JsonObject();
        if (resourceManager == null) return base;

        try {
            if (recipeId == null) return base;
            String modid = recipeId.contains(":") ? recipeId.substring(0, recipeId.indexOf(':')) : recipeId;

            // mod-level patches
            JsonObject modDef = loadJsonResource(MOD_PATCHES_PATH + "/" + modid + ".json");

            // recipe-level control
            String recipeShort = recipeId.contains(":") ? recipeId.substring(recipeId.indexOf(':') + 1) : recipeId;
            recipeShort = recipeShort.replace('/', '_');
            JsonObject recipeCtl = loadJsonResource(RECIPE_PATCHES_PATH + "/" + modid + "_" + recipeShort + ".json");

            // apply mod-level enabled patches
            if (modDef != null && modDef.has("patchDefinitions")) {
                JsonObject defs = modDef.getAsJsonObject("patchDefinitions");
                JsonObject enabled = recipeCtl != null && recipeCtl.has("enabledPatches")
                    ? recipeCtl.getAsJsonObject("enabledPatches") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : defs.entrySet()) {
                    String pid = e.getKey();
                    JsonObject def = e.getValue().getAsJsonObject();
                    boolean en = false;
                    if (enabled.has(pid)) {
                        try { en = enabled.get(pid).getAsBoolean(); } catch (Exception ignored) {}
                    }
                    if (!en) continue;
                    if (def.has("patch")) {
                        JsonObject patch = def.getAsJsonObject("patch");
                        for (Map.Entry<String, JsonElement> p : patch.entrySet()) {
                            String path = p.getKey();
                            JsonElement val = p.getValue();
                            RecipeEditManager.setJsonAtPath(base, path, val);
                        }
                    }
                }
            }

            // apply recipe local patches
            if (recipeCtl != null && recipeCtl.has("localPatches")) {
                JsonObject local = recipeCtl.getAsJsonObject("localPatches");
                for (Map.Entry<String, JsonElement> p : local.entrySet()) {
                    String path = p.getKey();
                    JsonElement val = p.getValue();
                    RecipeEditManager.setJsonAtPath(base, path, val);
                }
            }

        } catch (Exception ex) {
            InGameRecipeEditor.LOGGER.warn("应用补丁失败", ex);
        }

        return base;
    }

    /**
     * 从资源包加载 JSON 文件，返回 null 如果不存在
     */
    private static JsonObject loadJsonResource(String path) {
        ResourceLocation loc = ResourceLocation.parse(InGameRecipeEditor.MOD_ID + ":" + path);
        Optional<Resource> res = resourceManager.getResource(loc);
        if (res.isPresent()) {
            try (var reader = new InputStreamReader(res.get().open())) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                InGameRecipeEditor.LOGGER.warn("读取补丁文件失败: {}", path, e);
            }
        }
        return null;
    }
}
