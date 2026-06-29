package dev.whisperlyric.ingamerecipeeditor.workspace;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 调试开关设置，通过命令控制
 * 聊天栏打印配方编辑器的草稿状态和生成的 JSON
 */
public class DebugSettings {
    private static boolean debugDumpEnabled;
    private static boolean schemaExportEnabled;

    public static boolean isEnabled() {
        return debugDumpEnabled;
    }

    public static void setEnabled(boolean enabled) {
        debugDumpEnabled = enabled;
    }

    public static boolean isSchemaExportEnabled() {
        return schemaExportEnabled;
    }

    public static void setSchemaExportEnabled(boolean enabled) {
        schemaExportEnabled = enabled;
    }

    /**
     * 向聊天栏发送调试消息（仅客户端、且开关开启时）
     */
    public static void sendChat(String message) {
        if (!debugDumpEnabled) return;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[IGRE Debug] " + message), false);
        }
    }
}
