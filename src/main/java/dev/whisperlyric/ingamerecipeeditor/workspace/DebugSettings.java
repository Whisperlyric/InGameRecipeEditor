package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 调试开关设置，通过客户端配置文件控制
 * 聊天栏打印配方编辑器的草稿状态和生成的 JSON
 */
public class DebugSettings {

    public static boolean isEnabled() {
        return ConfigManager.get().debugDump;
    }

    public static boolean isSchemaExportEnabled() {
        return ConfigManager.get().schemaExport;
    }

    /**
     * 向聊天栏发送调试消息（仅客户端、且开关开启时）
     */
    public static void sendChat(String message) {
        if (!isEnabled()) return;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[IGRE Debug] " + message), false);
        }
    }
}
