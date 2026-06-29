package dev.whisperlyric.ingamerecipeeditor.button;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * 配方禁用/启用/删除按钮
 * 普通配方：显示启/禁用图标，点击切换禁用状态
 * 生成配方：显示删除图标，点击标记删除
 */
public class RecipeDisableButton extends AbstractRecipeButton {
    private static final ResourceLocation ENABLE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_enable.png");
    private static final ResourceLocation DISABLE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_disable.png");
    private static final ResourceLocation DELETE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_delete.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;
    private boolean enabled;
    private boolean pendingDelete;

    public RecipeDisableButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 9, 9);

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
        this.generatedRecipe = GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);

        // 初始状态
        this.enabled = this.recipeId == null || this.recipeId.isEmpty() || !DisabledRecipesManager.isRecipeDisabled(this.recipeId);
        this.pendingDelete = GeneratedRecipesManager.isGeneratedRecipeDeletionPending(this.recipeId);
    }

    @Override
    protected void updateState() {
        // 更新状态
        if (generatedRecipe) {
            this.pendingDelete = GeneratedRecipesManager.isGeneratedRecipeDeletionPending(recipeId);
        } else {
            this.enabled = !DisabledRecipesManager.isRecipeDisabled(recipeId);
        }
    }

    @Override
    protected boolean isPressed() {
        // 生成配方待删除时显示为"按下"状态
        return generatedRecipe && pendingDelete;
    }

    @Override
    protected ResourceLocation getIconTexture() {
        // 根据配方类型选择图标
        if (generatedRecipe) {
            return DELETE_TEXTURE;
        } else if (enabled) {
            return ENABLE_TEXTURE;
        } else {
            return DISABLE_TEXTURE;
        }
    }

    @Override
    protected IconOffset getIconOffset(boolean hovered) {
        // 删除按钮按下时偏移图标
        if (generatedRecipe && pendingDelete) {
            return new IconOffset(0.5, 0.5);
        }
        return IconOffset.ZERO;
    }

    @Override
    protected List<Component> getTooltipLines() {
        if (generatedRecipe) {
            return List.of(Component.translatable(pendingDelete
                ? "ingamerecipeeditor.tooltip.recipe_delete.pending"
                : "ingamerecipeeditor.tooltip.recipe_delete.generated"));
        }
        if (enabled) {
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe.enabled"));
        } else {
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe.disabled"));
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.recipeId != null && !this.recipeId.isEmpty()) {
            // 播放点击声音
            playDownSound(Minecraft.getInstance().getSoundManager());

            if (generatedRecipe) {
                // 生成配方：切换删除状态
                pendingDelete = !pendingDelete;
                GeneratedRecipesManager.clientSetGeneratedRecipeDeletionPending(recipeId, pendingDelete);
                NetworkHandler.sendRecipeDelete(recipeId, pendingDelete);
                InGameRecipeEditor.LOGGER.info("{}生成配方: {}", pendingDelete ? "标记删除" : "取消删除", recipeId);
            } else {
                // 普通配方：切换禁用状态
                enabled = !enabled;
                NetworkHandler.sendRecipeToggle(recipeId, !enabled);
                // 立即更新JEI可见性（无需等待服务器同步）
                if (enabled) {
                    JeiRecipeVisibility.unhideRecipe(recipeId);
                } else {
                    JeiRecipeVisibility.hideRecipe(recipeId);
                }
                InGameRecipeEditor.LOGGER.info("切换配方状态: {} -> {}", recipeId, enabled ? "启用" : "禁用");
            }
        }
    }

    public String getRecipeId() {
        return recipeId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPendingDelete() {
        return pendingDelete;
    }

    public boolean isGeneratedRecipe() {
        return generatedRecipe;
    }

    public boolean isSupportedRecipe() {
        return supportedRecipe;
    }
}