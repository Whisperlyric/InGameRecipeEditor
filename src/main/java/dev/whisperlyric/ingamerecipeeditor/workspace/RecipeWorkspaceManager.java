package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

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
    
    // 所有配方草稿
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    
    // 当前活跃的配方ID
    private String activeRecipeId;
    
    // 当前工作区编辑器
    private WorkspaceEditor currentEditor;
    
    private RecipeWorkspaceManager() {}
    
    public static RecipeWorkspaceManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 创建新配方草稿
     */
    public WorkspaceEditor createNewDraft(String recipeType) {
        RecipeSchema schema = SchemaRegistry.getInstance().get(recipeType);
        if (schema == null) {
            InGameRecipeEditor.LOGGER.warn("未知的配方类型: {}", recipeType);
            return null;
        }
        
        String draftId = generateDraftId(recipeType);
        WorkspaceEditor editor = new WorkspaceEditor(recipeType);
        
        drafts.put(draftId, new Draft(draftId, recipeType, editor, null));
        activeRecipeId = draftId;
        currentEditor = editor;
        
        InGameRecipeEditor.LOGGER.info("创建新配方草稿: {}", draftId);
        return editor;
    }
    
    /**
     * 编辑现有配方
     */
    public WorkspaceEditor editExistingRecipe(String recipeId, Recipe<?> recipe, JsonObject recipeJson) {
        String recipeType = getRecipeType(recipe, recipeJson);
        if (recipeType == null) {
            InGameRecipeEditor.LOGGER.warn("无法确定配方类型: {}", recipeId);
            return null;
        }
        
        WorkspaceEditor editor = new WorkspaceEditor(recipeType, recipeJson);
        
        drafts.put(recipeId, new Draft(recipeId, recipeType, editor, recipeJson));
        activeRecipeId = recipeId;
        currentEditor = editor;
        
        InGameRecipeEditor.LOGGER.info("开始编辑配方: {}", recipeId);
        return editor;
    }
    
    /**
     * 获取当前活跃的工作区编辑器
     */
    public WorkspaceEditor getCurrentEditor() {
        return currentEditor;
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
    public Optional<WorkspaceEditor> switchToDraft(String draftId) {
        Draft draft = drafts.get(draftId);
        if (draft == null) {
            return Optional.empty();
        }
        
        activeRecipeId = draftId;
        currentEditor = draft.editor();
        return Optional.of(currentEditor);
    }
    
    /**
     * 是否有草稿
     */
    public boolean hasDraft(String recipeId) {
        Draft draft = drafts.get(recipeId);
        return draft != null && draft.hasModifications();
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
    public Map<String, Draft> getAllDrafts() {
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
        Draft draft = drafts.get(draftId);
        if (draft == null) {
            return Optional.empty();
        }
        
        try {
            JsonObject json = draft.editor().generateJson();
            InGameRecipeEditor.LOGGER.info("提交配方草稿: {}", draftId);
            
            // 提交后移除草稿
            if (draftId.equals(activeRecipeId)) {
                activeRecipeId = null;
                currentEditor = null;
            }
            drafts.remove(draftId);
            
            return Optional.of(json);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("提交配方失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 清除草稿
     */
    public void clearDraft(String draftId) {
        Draft draft = drafts.remove(draftId);
        if (draft != null) {
            InGameRecipeEditor.LOGGER.info("清除配方草稿: {}", draftId);
        }
        
        if (draftId.equals(activeRecipeId)) {
            activeRecipeId = null;
            currentEditor = null;
        }
    }
    
    /**
     * 清除所有草稿
     */
    public void clearAllDrafts() {
        drafts.clear();
        activeRecipeId = null;
        currentEditor = null;
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
        currentEditor = drafts.get(recipeId).editor();
    }
    
    /**
     * 停止编辑
     */
    public void stopEditing() {
        activeRecipeId = null;
        currentEditor = null;
    }
    
    /**
     * 生成草稿ID
     */
    private String generateDraftId(String recipeType) {
        int counter = drafts.size() + 1;
        return "draft_" + recipeType.replace(":", "_") + "_" + counter;
    }
    
    /**
     * 获取配方类型
     */
    private String getRecipeType(Recipe<?> recipe, JsonObject recipeJson) {
        if (recipeJson != null && recipeJson.has("type")) {
            return recipeJson.get("type").getAsString();
        }
        if (recipe != null) {
            return recipe.getType().toString();
        }
        return null;
    }
    
    /**
     * 草稿记录
     */
    public record Draft(
        String id,               // 草稿ID
        String recipeType,       // 配方类型
        WorkspaceEditor editor,  // 工作区编辑器
        JsonObject originalJson  // 原始JSON（编辑现有配方时）
    ) {
        /**
         * 是否有修改
         */
        public boolean hasModifications() {
            return editor != null && !editor.getSlotData().isEmpty();
        }
        
        /**
         * 是否是新配方
         */
        public boolean isNewRecipe() {
            return originalJson == null;
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

        // 使用泛型方法获取配方ID
        String recipeIdStr = getRecipeId(recipeLayout);
        if (recipeIdStr == null) {
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

        // 打开工作区界面（编辑模式）
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeIdStr, recipeLayout, true);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);
        
        InGameRecipeEditor.LOGGER.info("打开工作区编辑配方: {}", recipeIdStr);
    }

    /**
     * 打开空工作区（新建配方，从此配方类型新建）
     */
    @SuppressWarnings("unchecked")
    public void openEmptyWorkspace(Screen parent, IRecipeLayoutDrawable<?> recipeLayout) {
        if (recipeLayout == null) {
            InGameRecipeEditor.LOGGER.warn("无法打开空工作区：配方布局为空");
            return;
        }

        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        String recipeType = category.getRecipeType().getUid().toString();

        // 创建空工作区界面（新建模式）
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeType, recipeLayout, false);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);
        
        InGameRecipeEditor.LOGGER.info("打开空工作区，配方类型: {}", recipeType);
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

        // 使用泛型方法获取配方ID
        String recipeIdStr = getRecipeId(recipeLayout);
        if (recipeIdStr == null) {
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

        // 打开工作区界面（复制模式）
        RecipeWorkspaceScreen screen = new RecipeWorkspaceScreen(parent, recipeIdStr, recipeLayout, false);
        currentScreen = screen;
        Minecraft.getInstance().setScreen(screen);
        
        InGameRecipeEditor.LOGGER.info("打开复制工作区，基于配方: {}", recipeIdStr);
    }

    /**
     * 获取配方ID（泛型方法）
     */
    @SuppressWarnings("unchecked")
    private <T> String getRecipeId(IRecipeLayoutDrawable<T> layout) {
        T recipe = layout.getRecipe();
        ResourceLocation id = layout.getRecipeCategory().getRegistryName(recipe);
        return id == null ? null : id.toString();
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