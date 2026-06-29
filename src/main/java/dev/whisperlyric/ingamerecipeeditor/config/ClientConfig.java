package dev.whisperlyric.ingamerecipeeditor.config;

import com.google.gson.annotations.SerializedName;

/**
 * 客户端配置 POJO - 由 ConfigManager 加载/保存到 config/ingamerecipeeditor/client_config.json
 * 包含所有可配置的客户端设置
 */
public class ClientConfig {

    /** 禁用配方在JEI中是否可见 */
    @SerializedName("show_disabled_in_jei")
    public boolean showDisabledInJei = false;

    /** 调试转储 - 在聊天栏打印配方编辑器的草稿状态和生成的 JSON */
    @SerializedName("debug_dump")
    public boolean debugDump = false;

    /** Schema 导出 - 打开配方工作区时导出 schema 文件 */
    @SerializedName("schema_export")
    public boolean schemaExport = false;

    /** 原料轮换周期（毫秒），最小 100 */
    @SerializedName("cycle_ms")
    public long cycleMs = 400L;

    /** 原料轮换 tick 间隔，最小 1 */
    @SerializedName("tick_interval")
    public int tickInterval = 8;
}
