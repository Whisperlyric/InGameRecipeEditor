package dev.whisperlyric.ingamerecipeeditor.button;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/**
 * 配方复制按钮 - 点击复制配方内容到新工作区（基于此配方创建新配方）
 */
public class RecipeCopyButton extends AbstractRecipeButton {
    private static final ResourceLocation COPY_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_copy.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;

    public RecipeCopyButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 8, 8);
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
    }

    public boolean hasValidRecipeId() {
        return this.supportedRecipe && this.recipeId != null && !this.recipeId.isEmpty();
    }

    @Override
    protected ResourceLocation getIconTexture() {
        return COPY_TEXTURE;
    }

    @Override
    protected List<Component> getTooltipLines() {
        // 显示复制配方到新工作区的提示
        return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_copy.copy_to_new"));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.recipeId != null && !this.recipeId.isEmpty()) {
            // 播放点击声音
            playDownSound(Minecraft.getInstance().getSoundManager());

            // 打开工作区界面，复制配方内容
            Screen currentScreen = Minecraft.getInstance().screen;
            RecipeWorkspaceManager.getInstance().openWorkspaceWithCopy(currentScreen, recipeLayout);

            InGameRecipeEditor.LOGGER.info("复制配方到新工作区: {}", recipeId);
        }
    }

    public String getRecipeId() {
        return recipeId;
    }
}