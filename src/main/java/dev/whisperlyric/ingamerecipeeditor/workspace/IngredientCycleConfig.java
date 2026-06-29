package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 简单的客户端配置管理器：读取 config/ingamerecipeeditor/client_config.json
 * 支持字段：cycle_ms (long, 毫秒), tick_interval (int, client ticks)
 */
public class IngredientCycleConfig {

    private static final Path CONFIG_PATH = Path.of("config", "ingamerecipeeditor", "client_config.json");
    private static volatile boolean loaded;
    private static long cycleMs = 400L; // 默认400ms（8tick）
    private static int tickInterval = 8; // 默认8tick

    private static synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                JsonElement el = JsonParser.parseString(json);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("cycle_ms")) {
                        try { cycleMs = obj.get("cycle_ms").getAsLong(); } catch (Exception ignored) {}
                    }
                    if (obj.has("tick_interval")) {
                        try { tickInterval = obj.get("tick_interval").getAsInt(); } catch (Exception ignored) {}
                    }
                }
            } else {
                // 尝试创建父目录和默认文件
                try {
                    Files.createDirectories(CONFIG_PATH.getParent());
                    JsonObject def = new JsonObject();
                    def.addProperty("cycle_ms", cycleMs);
                    def.addProperty("tick_interval", tickInterval);
                    Files.writeString(CONFIG_PATH, def.toString());
                } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("读取 IngredientCycleConfig 失败", e);
        }
        loaded = true;
    }

    public static long getCycleMs() {
        loadIfNeeded();
        return Math.max(100L, cycleMs);
    }

    public static int getTickInterval() {
        loadIfNeeded();
        return Math.max(1, tickInterval);
    }

    public static synchronized void setCycleMs(long ms) {
        cycleMs = Math.max(100L, ms);
        persist();
    }

    public static synchronized void setTickInterval(int t) {
        tickInterval = Math.max(1, t);
        persist();
    }

    private static void persist() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject def = new JsonObject();
            def.addProperty("cycle_ms", cycleMs);
            def.addProperty("tick_interval", tickInterval);
            Files.writeString(CONFIG_PATH, def.toString());
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.warn("保存 IngredientCycleConfig 失败", e);
        }
    }
}

