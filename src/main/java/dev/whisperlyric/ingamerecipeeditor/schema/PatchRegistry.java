package dev.whisperlyric.ingamerecipeeditor.schema;

import com.google.gson.*;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeEditManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 简单的补丁注册/应用器
 * 从 refs/mod_patches 和 refs/recipe_patches 读取 JSON 定义并将 path->value 填回目标 JSON
 */
public class PatchRegistry {
    // 载入并应用 mod/recipe 补丁到 base（不修改磁盘）
    public static JsonObject applyPatches(JsonObject base, String recipeId, String recipeType) {
        if (base == null) base = new JsonObject();

        try {
            if (recipeId == null) return base;
            String modid = recipeId.contains(":") ? recipeId.substring(0, recipeId.indexOf(':')) : recipeId;
            // mod-level patches
            Path modFile = Path.of("refs", "mod_patches", modid + ".json");
            JsonObject modDef = null;
            if (Files.exists(modFile)) {
                try (var is = Files.newInputStream(modFile)) {
                    modDef = JsonParser.parseReader(new java.io.InputStreamReader(is)).getAsJsonObject();
                }
            }

            // recipe-level control
            String recipeShort = recipeId.contains(":") ? recipeId.substring(recipeId.indexOf(':') + 1) : recipeId;
            recipeShort = recipeShort.replace('/', '_');
            Path recipeFile = Path.of("refs", "recipe_patches", modid + "_" + recipeShort + ".json");
            JsonObject recipeCtl = null;
            if (Files.exists(recipeFile)) {
                try (var is = Files.newInputStream(recipeFile)) {
                    recipeCtl = JsonParser.parseReader(new java.io.InputStreamReader(is)).getAsJsonObject();
                }
            }

            // apply mod-level enabled patches
            if (modDef != null && modDef.has("patchDefinitions")) {
                JsonObject defs = modDef.getAsJsonObject("patchDefinitions");
                JsonObject enabled = recipeCtl != null && recipeCtl.has("enabledPatches") ? recipeCtl.getAsJsonObject("enabledPatches") : new JsonObject();
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

        } catch (IOException ex) {
            InGameRecipeEditor.LOGGER.warn("读取补丁文件失败", ex);
        } catch (Exception ex) {
            InGameRecipeEditor.LOGGER.warn("应用补丁失败", ex);
        }

        return base;
    }
}


