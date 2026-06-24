package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import dev.whisperlyric.ingamerecipeeditor.schema.PatchRegistry;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 配方工作区管理器
 * 管理多个配方编辑草稿，提供草稿创建、切换、提交等功能
 */
public class RecipeWorkspaceManager {
    
    private static final RecipeWorkspaceManager INSTANCE = new RecipeWorkspaceManager();
    
    // 所有配方草稿（使用RecipeEditManager管理）
    private final Map<String, DraftInfo> drafts = new LinkedHashMap<>();
    
    // 当前活跃的配方ID
    private String activeRecipeId;
    
    private RecipeWorkspaceManager() {}
    
    public static RecipeWorkspaceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 创建新配方草稿
     */
    public void createNewDraft(String recipeType) {
        RecipeSchema schema = SchemaRegistry.getSchema(recipeType).orElse(null);
        if (schema == null) {
            InGameRecipeEditor.LOGGER.warn("未知的配方类型: {}", recipeType);
            return;
        }
        
        String draftId = generateDraftId(recipeType);
        
        drafts.put(draftId, new DraftInfo(draftId, recipeType, null, false));
        activeRecipeId = draftId;
        
        InGameRecipeEditor.LOGGER.info("创建新配方草稿: {}", draftId);
    }
    
    /**
     * 编辑现有配方
     */
    public void editExistingRecipe(String recipeId, JsonObject recipeJson) {
        String recipeType = getRecipeTypeFromJson(recipeJson);
        if (recipeType == null) {
            InGameRecipeEditor.LOGGER.warn("无法确定配方类型: {}", recipeId);
            return;
        }
        
        drafts.put(recipeId, new DraftInfo(recipeId, recipeType, recipeJson, false));
        activeRecipeId = recipeId;
        
        InGameRecipeEditor.LOGGER.info("开始编辑配方: {}", recipeId);
    }
    
    /**
     * 使用指定的recipeId和recipeType创建草稿
     * 用于无法获取配方JSON或recipe不是Recipe<?>类型的情况
     */
    public void createDraftWithType(String recipeId, String recipeType) {
        drafts.put(recipeId, new DraftInfo(recipeId, recipeType, null, false));
        activeRecipeId = recipeId;
        InGameRecipeEditor.LOGGER.info("创建草稿: id={}, type={}", recipeId, recipeType);
    }

    /**
     * 复制现有配方（基于原始JSON创建新配方）
     */
    public void copyExistingRecipe(String recipeId, JsonObject recipeJson) {
        String recipeType = getRecipeTypeFromJson(recipeJson);
        if (recipeType == null) {
            InGameRecipeEditor.LOGGER.warn("无法确定配方类型: {}", recipeId);
            return;
        }

        drafts.put(recipeId, new DraftInfo(recipeId, recipeType, recipeJson, true));
        activeRecipeId = recipeId;

        InGameRecipeEditor.LOGGER.info("复制配方: {}", recipeId);
    }
    
    /**
     * 获取当前活跃的配方ID
     */
    public String getActiveRecipeId() {
        return activeRecipeId;
    }
    
    /**
     * 切换到指定草稿
     */
    public boolean switchToDraft(String draftId) {
        DraftInfo draft = drafts.get(draftId);
        if (draft == null) {
            return false;
        }
        
        activeRecipeId = draftId;
        return true;
    }
    
    /**
     * 是否有草稿
     */
    public boolean hasDraft(String recipeId) {
        return RecipeEditManager.hasDraft(recipeId);
    }
    
    /**
     * 获取指定草稿
     */
    public Optional<DraftInfo> getDraft(String recipeId) {
        DraftInfo draft = drafts.get(recipeId);
        return Optional.ofNullable(draft);
    }
    
    /**
     * 是否正在编辑
     */
    public boolean isEditing(String recipeId) {
        return activeRecipeId != null && activeRecipeId.equals(recipeId);
    }
    
    /**
     * 获取所有草稿ID
     */
    public Map<String, DraftInfo> getAllDrafts() {
        return new HashMap<>(drafts);
    }
    
    /**
     * 获取草稿数量
     */
    public int getDraftCount() {
        return drafts.size();
    }
    
    /**
     * 提交草稿（生成JSON）
     */
    public Optional<JsonObject> submitDraft(String draftId) {
        DraftInfo draft = drafts.get(draftId);
        if (draft == null) {
            return Optional.empty();
        }
        
        try {
            var result = RecipeEditManager.submit(draftId);
            if (result.isPresent()) {
                InGameRecipeEditor.LOGGER.info("提交配方草稿并生成JSON: {}", draftId);
                // 提交后移除草稿
                if (draftId.equals(activeRecipeId)) {
                    activeRecipeId = null;
                }
                drafts.remove(draftId);
                return result;
            } else {
                InGameRecipeEditor.LOGGER.info("提交配方草稿（无更改）: {}", draftId);
                if (draftId.equals(activeRecipeId)) {
                    activeRecipeId = null;
                }
                drafts.remove(draftId);
                return Optional.empty();
            }
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("提交配方失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 清除草稿
     */
    public void clearDraft(String draftId) {
        DraftInfo draft = drafts.remove(draftId);
        if (draft != null) {
            RecipeEditManager.clear(draftId);
            InGameRecipeEditor.LOGGER.info("清除配方草稿: {}", draftId);
        }
        
        if (draftId.equals(activeRecipeId)) {
            activeRecipeId = null;
        }
    }
    
    /**
     * 清除所有草稿
     */
    public void clearAllDrafts() {
        drafts.clear();
        activeRecipeId = null;
        InGameRecipeEditor.LOGGER.info("清除所有配方草稿");
    }
    
    /**
     * 开始编辑指定配方
     */
    public void startEditing(String recipeId) {
        if (!drafts.containsKey(recipeId)) {
            InGameRecipeEditor.LOGGER.warn("草稿不存在: {}", recipeId);
            return;
        }
        activeRecipeId = recipeId;
    }
    
    /**
     * 停止编辑
     */
    public void stopEditing() {
        activeRecipeId = null;
    }
    
    /**
     * 生成草稿ID
     */
    private String generateDraftId(String recipeType) {
        int counter = drafts.size() + 1;
        return "draft_" + recipeType.replace(":", "_") + "_" + counter;
    }
    
    /**
     * 从配方JSON获取配方类型
     */
    private String getRecipeTypeFromJson(JsonObject recipeJson) {
        if (recipeJson != null && recipeJson.has("type")) {
            return recipeJson.get("type").getAsString();
        }
        return null;
    }
    
    /**
     * 草稿信息记录
     */
    public record DraftInfo(
        String id,               // 草稿ID
        String recipeType,       // 配方类型
        JsonObject originalJson, // 原始JSON（编辑现有配方时）
        boolean isCopy           // 是否为复制配方（复制模式视为新建配方）
    ) {
        /**
         * 是否是新配方（无原始JSON 或 复制模式）
         */
        public boolean isNewRecipe() {
            return originalJson == null || isCopy;
        }
    }

    // ==================== JEI 配方布局相关方法 ====================

    // 存储工作区配方布局的副本
    private static final Map<String, WorkspaceRecipe> workspaceRecipes = new HashMap<>();

    // 当前打开的工作区界面
    private static RecipeWorkspaceScreen currentScreen;

    /**
     * 工作区配方记录
     */
    public record WorkspaceRecipe(
        String recipeId,
        Object recipe,
        IRecipeCategory<?> category
    ) {}

    /**
     * 从JEI配方布局创建工作区配方（编辑当前配方）
     */
    @SuppressWarnings("unchecked")
    public void openWorkspace(Screen parent, IRecipeLayoutDrawable<?> recipeLayout) {
        if (recipeLayout == null) {
            InGameRecipeEditor.LOGGER.warn("无法打开工作区：配方布局为空");
            return;
        }

        // 使用JeiRecipeHelper获取正确的配方ID
        String recipeIdStr = JeiRecipeHelper.getRecipeId(recipeLayout);
        if (recipeIdStr == null || recipeIdStr.isEmpty()) {
            InGameRecipeEditor.LOGGER.warn("无法打开工作区：配方ID为空");
            return;
        }

        Object recipe = recipeLayout.getRecipe();
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();

        // 存储工作区配方信息
        workspaceRecipes.put(recipeIdStr, new WorkspaceRecipe(
            recipeIdStr,
            recipe,
            category
        ));
        // 如果没有schema，则尝试应用refs下的mod/recipe补丁作为起始JSON
        String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);
        var schemaOpt = SchemaRegistry.getSchema(recipeType);
        if (schemaOpt.isEmpty()) {
            try {
                JsonObject merged = PatchRegistry.applyPatches(null, recipeIdStr, recipeType);
                // 注册草稿信息，使编辑器可以基于该JSON工作
                drafts.put(recipeIdStr, new DraftInfo(recipeIdStr, recipeType, merged, false));
                InGameRecipeEditor.LOGGER.info("为无编码配方应用补丁并创建草稿: {}", recipeIdStr);
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.warn("应用补丁失败: {}", e.getMessage());
            }
        }

        // 打开工作区界面（编辑模式）
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeIdStr, recipeLayout, true);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);

        InGameRecipeEditor.LOGGER.info("打开工作区编辑配方: {}", recipeIdStr);
        DebugSettings.sendChat("[Workspace] 编辑模式打开: id=" + recipeIdStr + ", type=" + recipeType);
    }

    /**
     * 打开空工作区（新建配方，从此配方类型新建）
     */
    /**
     * 打开空工作区（新建配方模式）
     * @param parent 父界面
     * @param recipeLayout 配方布局（用于获取配方类型和槽位结构）
     * @param templateJson 模板 JSON（基于原配方结构生成的骨架，所有原料值已清空）
     */
    public void openEmptyWorkspace(Screen parent, IRecipeLayoutDrawable<?> recipeLayout, JsonObject templateJson) {
        if (recipeLayout == null) {
            InGameRecipeEditor.LOGGER.warn("无法打开空工作区：配方布局为空");
            return;
        }

        // 使用JeiRecipeHelper获取正确的配方类型
        String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);

        // 创建空工作区界面（新建模式），传入模板 JSON
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeType, recipeLayout, false, templateJson);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);

        InGameRecipeEditor.LOGGER.info("打开空工作区，配方类型: {}", recipeType);
        String templateStr = templateJson != null ? new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(templateJson) : "(null)";
        DebugSettings.sendChat("[Workspace] 新建模式打开: type=" + recipeType + ", template:\n" + templateStr);
    }

    /**
     * 打开工作区并复制配方内容（基于此配方创建新配方）
     */
    @SuppressWarnings("unchecked")
    public void openWorkspaceWithCopy(Screen parent, IRecipeLayoutDrawable<?> recipeLayout) {
        if (recipeLayout == null) {
            InGameRecipeEditor.LOGGER.warn("无法打开复制工作区：配方布局为空");
            return;
        }

        // 使用JeiRecipeHelper获取正确的配方ID
        String recipeIdStr = JeiRecipeHelper.getRecipeId(recipeLayout);
        if (recipeIdStr == null || recipeIdStr.isEmpty()) {
            InGameRecipeEditor.LOGGER.warn("无法打开复制工作区：配方ID为空");
            return;
        }

        Object recipe = recipeLayout.getRecipe();
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();

        // 存储工作区配方信息
        workspaceRecipes.put(recipeIdStr, new WorkspaceRecipe(
            recipeIdStr,
            recipe,
            category
        ));

        // 打开工作区界面（复制模式，与编辑模式行为一致，但提交时创建新配方）
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeIdStr, recipeLayout, true);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);
        
        InGameRecipeEditor.LOGGER.info("打开复制工作区，基于配方: {}", recipeIdStr);
    }

    /**
     * 关闭当前工作区
     */
    public void closeWorkspace() {
        if (currentScreen != null) {
            currentScreen = null;
        }
    }

    /**
     * 获取当前工作区界面
     */
    public Optional<RecipeWorkspaceScreen> getCurrentScreen() {
        return Optional.ofNullable(currentScreen);
    }

    /**
     * 检查是否有打开的工作区
     */
    public boolean hasOpenWorkspace() {
        return currentScreen != null;
    }

    /**
     * 获取工作区配方
     */
    public Optional<WorkspaceRecipe> getWorkspaceRecipe(String recipeId) {
        return Optional.ofNullable(workspaceRecipes.get(recipeId));
    }

    /**
     * 清除工作区配方
     */
    public void clearWorkspaceRecipe(String recipeId) {
        workspaceRecipes.remove(recipeId);
    }

    /**
     * 清除所有工作区配方
     */
    public void clearAllWorkspaceRecipes() {
        workspaceRecipes.clear();
        currentScreen = null;
    }

    /**
     * 检查屏幕是否为工作区界面
     */
    public boolean isWorkspaceScreen(Screen screen) {
        return screen instanceof RecipeWorkspaceScreen;
    }

    /**
     * 检查屏幕是否为JEI配方界面或工作区界面
     */
    public boolean isRecipeScreen(Screen screen) {
        return screen instanceof RecipesGui || screen instanceof RecipeWorkspaceScreen;
    }

    /**
     * 从屏幕获取配方ID（如果是工作区界面）
     */
    public Optional<String> getRecipeIdFromScreen(Screen screen) {
        if (screen instanceof RecipeWorkspaceScreen workspaceScreen) {
            return Optional.of(workspaceScreen.getRecipeId());
        }
        return Optional.empty();
    }
}