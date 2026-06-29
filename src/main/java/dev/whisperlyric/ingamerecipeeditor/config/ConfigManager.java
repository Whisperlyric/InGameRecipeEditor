package dev.whisperlyric.ingamerecipeeditor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置管理器
 * 负责加载/保存配置文件，并构建 Cloth Config 配置界面
 */
public class ConfigManager {

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("ingamerecipeeditor").resolve("client_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ClientConfig config;

    /**
     * 初始化：加载配置文件
     */
    public static void init() {
        load();
    }

    /**
     * 获取当前配置（未加载时自动加载）
     */
    public static ClientConfig get() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * 从文件加载配置
     */
    public static synchronized void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                config = GSON.fromJson(Files.readString(CONFIG_PATH), ClientConfig.class);
                if (config == null) {
                    config = new ClientConfig();
                }
            } else {
                config = new ClientConfig();
                save();
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("加载客户端配置失败", e);
            config = new ClientConfig();
        }
    }

    /**
     * 保存配置到文件
     */
    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.warn("保存客户端配置失败", e);
        }
    }

    /**
     * 构建 Cloth Config 配置界面
     */
    public static Screen buildConfigScreen(Screen parent) {
        ClientConfig current = get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("ingamerecipeeditor.config.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // JEI 可见性分类
        ConfigCategory jeiCategory = builder.getOrCreateCategory(
                Component.translatable("ingamerecipeeditor.config.category.jei"));
        jeiCategory.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("ingamerecipeeditor.config.show_disabled_in_jei"),
                        current.showDisabledInJei)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("ingamerecipeeditor.config.show_disabled_in_jei.tooltip"))
                .setSaveConsumer(v -> current.showDisabledInJei = v)
                .build());

        // 调试分类
        ConfigCategory debugCategory = builder.getOrCreateCategory(
                Component.translatable("ingamerecipeeditor.config.category.debug"));
        debugCategory.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("ingamerecipeeditor.config.debug_dump"),
                        current.debugDump)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("ingamerecipeeditor.config.debug_dump.tooltip"))
                .setSaveConsumer(v -> current.debugDump = v)
                .build());
        debugCategory.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("ingamerecipeeditor.config.schema_export"),
                        current.schemaExport)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("ingamerecipeeditor.config.schema_export.tooltip"))
                .setSaveConsumer(v -> current.schemaExport = v)
                .build());

        // 原料轮换分类
        ConfigCategory cycleCategory = builder.getOrCreateCategory(
                Component.translatable("ingamerecipeeditor.config.category.cycle"));
        cycleCategory.addEntry(entryBuilder
                .startLongField(
                        Component.translatable("ingamerecipeeditor.config.cycle_ms"),
                        current.cycleMs)
                .setDefaultValue(400L)
                .setMin(100L)
                .setTooltip(Component.translatable("ingamerecipeeditor.config.cycle_ms.tooltip"))
                .setSaveConsumer(v -> current.cycleMs = v)
                .build());
        cycleCategory.addEntry(entryBuilder
                .startIntField(
                        Component.translatable("ingamerecipeeditor.config.tick_interval"),
                        current.tickInterval)
                .setDefaultValue(8)
                .setMin(1)
                .setTooltip(Component.translatable("ingamerecipeeditor.config.tick_interval.tooltip"))
                .setSaveConsumer(v -> current.tickInterval = v)
                .build());

        builder.setSavingRunnable(() -> {
            save();
            // 配置变更后更新 JEI 可见性
            JeiRecipeVisibility.updateVisibility();
        });

        return builder.build();
    }
}
