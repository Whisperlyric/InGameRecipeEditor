package com.wzz.registerhelper.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

/**
 * Mod配置管理类
 * 用于管理NBT匹配模式等配置
 */
public class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new CommonConfig(commonBuilder);
        COMMON_SPEC = commonBuilder.build();
    }

    /**
     * 注册配置
     */
    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC);
    }

    /**
     * 通用配置
     */
    public static class CommonConfig {

        public final ForgeConfigSpec.BooleanValue enablePerSlotNBT;
        public final ForgeConfigSpec.BooleanValue enableDebugLogging;
        public final ForgeConfigSpec.BooleanValue defaultIncludeNBT;
        public final ForgeConfigSpec.BooleanValue autoGenerateRecipeId;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("nbt_matching");

            // NBT复选框默认状态
            defaultIncludeNBT = builder
                    .comment("配方创建器中NBT复选框的默认状态",
                            "当启用时，创建配方时输入物品默认包含NBT",
                            "当禁用时，创建配方时输入物品默认不包含NBT",
                            "",
                            "Default state of NBT checkbox in recipe creator",
                            "When enabled, input items will include NBT by default",
                            "When disabled, input items will not include NBT by default")
                    .define("defaultIncludeNBT", true);

            // per-slot NBT 控制开关
            enablePerSlotNBT = builder
                    .comment("启用配方创建器中每个槽位独立的NBT匹配控制",
                            "启用后：槽位底部显示颜色条，中键点击可单独开关每个槽位的NBT匹配",
                            "禁用后：所有槽位统一由 defaultIncludeNBT 决定，不显示颜色条",
                            "",
                            "Enable per-slot NBT matching control in recipe creator",
                            "When enabled: color bar shown at slot bottom, middle-click to toggle per slot",
                            "When disabled: all slots follow defaultIncludeNBT, no color bar shown")
                    .define("enablePerSlotNBT", true);

            builder.pop();

            builder.push("kubejs_export");

            autoGenerateRecipeId = builder
                    .comment("自动为导出的KubeJS配方生成ID",
                            "当启用时，会为每个配方自动生成ID，格式为: recipehelper/modid/recipe_type/output_id",
                            "当禁用时，不生成配方ID，使用KubeJS默认行为",
                            "",
                            "Auto-generate recipe IDs for exported KubeJS recipes",
                            "When enabled, generates ID for each recipe: recipehelper/modid/recipe_type/output_id",
                            "When disabled, no recipe ID is generated, uses KubeJS default behavior")
                    .define("autoGenerateRecipeId", false);

            builder.pop();

            builder.push("debug");

            // 调试日志开关
            enableDebugLogging = builder
                    .comment("启用调试日志输出",
                            "当启用时，会输出NBT匹配的详细信息",
                            "",
                            "Enable debug logging",
                            "When enabled, detailed NBT matching information will be logged")
                    .define("enableDebugLogging", false);

            builder.pop();
        }
    }

    /**
     * 检查是否启用调试日志
     */
    public static boolean isDebugLoggingEnabled() {
        return COMMON.enableDebugLogging.get();
    }

    /**
     * 检查是否启用了 per-slot NBT 控制
     */
    public static boolean isPerSlotNBTEnabled() {
        return COMMON.enablePerSlotNBT.get();
    }

    /**
     * 获取NBT复选框默认状态
     */
    public static boolean getDefaultIncludeNBT() {
        return COMMON.defaultIncludeNBT.get();
    }

    /**
     * 检查是否启用了自动生成配方ID
     */
    public static boolean isAutoGenerateRecipeIdEnabled() {
        return COMMON.autoGenerateRecipeId.get();
    }
}