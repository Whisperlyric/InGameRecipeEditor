package dev.whisperlyric.ingamerecipeeditor.button;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 配方布局按钮管理器 - 存储和管理配方布局的自定义按钮
 * 使用 WeakHashMap 避免内存泄漏
 * 
 * 按钮布局：2x2网格 + 删除按钮（可选）
 * 第一行：[启/禁用] [编辑]
 * 第二行：[添加]    [复制]
 * 删除按钮：仅对生成配方显示
 */
public class RecipeLayoutButtonManager {
    // 使用 WeakHashMap 存储，当 RecipeLayoutWithButtons 被 GC 回收时自动清理
    private static final Map<RecipeLayoutWithButtons<?>, List<AbstractWidget>> buttonsMap = new WeakHashMap<>();
    
    // 按钮尺寸和间距（和src-old一致）
    private static final int BUTTON_WIDTH = 9;
    private static final int BUTTON_HEIGHT = 9;
    private static final int BUTTON_SPACING = 1;

    /**
     * 为配方布局创建按钮
     */
    public static void createButtons(RecipeLayoutWithButtons<?> layoutWithButtons, IRecipeLayoutDrawable<?> recipeLayout) {
        if (buttonsMap.containsKey(layoutWithButtons)) {
            return;
        }

        List<AbstractWidget> buttons = new ArrayList<>();

        try {
            @SuppressWarnings("removal")
            net.minecraft.client.renderer.Rect2i rect = recipeLayout.getRect();
            
            // 按钮位置：在配方布局右侧
            int startX = rect.getX() + rect.getWidth() + 2;
            int startY = rect.getY();
            int row2Y = startY + BUTTON_HEIGHT + BUTTON_SPACING;

            // 第一行：启/禁用按钮和编辑按钮
            RecipeDisableButton disableButton = new RecipeDisableButton(startX, startY, recipeLayout);
            buttons.add(disableButton);

            RecipeEditButton editButton = new RecipeEditButton(startX + BUTTON_WIDTH + BUTTON_SPACING, startY, recipeLayout);
            buttons.add(editButton);

            // 第二行：添加按钮和复制按钮
            RecipeAddButton addButton = new RecipeAddButton(startX, row2Y, recipeLayout);
            buttons.add(addButton);

            RecipeCopyButton copyButton = new RecipeCopyButton(startX + BUTTON_WIDTH + BUTTON_SPACING, row2Y, recipeLayout);
            buttons.add(copyButton);
            
            InGameRecipeEditor.LOGGER.debug("为配方布局创建 {} 个按钮", buttons.size());
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("创建配方按钮失败", e);
        }

        buttonsMap.put(layoutWithButtons, buttons);
    }

    /**
     * 获取配方布局的按钮列表
     */
    public static List<AbstractWidget> getButtons(RecipeLayoutWithButtons<?> layoutWithButtons) {
        List<AbstractWidget> buttons = buttonsMap.get(layoutWithButtons);
        return buttons != null ? buttons : new ArrayList<>();
    }

    /**
     * 清除所有按钮缓存
     */
    public static void clearAll() {
        buttonsMap.clear();
    }

    /**
     * 检查配方布局是否有按钮
     */
    public static boolean hasButtons(RecipeLayoutWithButtons<?> layoutWithButtons) {
        List<AbstractWidget> buttons = buttonsMap.get(layoutWithButtons);
        return buttons != null && !buttons.isEmpty();
    }
}