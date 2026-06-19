package dev.whisperlyric.ingamerecipeeditor.button;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 配方编辑按钮 - 点击编辑配方（包含内容物的工作区）
 * 对所有配方显示，但tooltip根据是否为生成配方显示不同提示
 * 使用 JEI 的按钮背景渲染
 */
public class RecipeEditButton extends AbstractWidget {
    private static final ResourceLocation EDIT_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_edit.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;

    public RecipeEditButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 9, 9, Component.empty());
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        String id = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.recipeId = id != null ? id : "";
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
        this.generatedRecipe = !this.recipeId.isEmpty() && GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        boolean hovered = isMouseOver(mouseX, mouseY);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // 使用 JEI 的按钮背景
        Textures textures = Internal.getTextures();
        DrawableNineSliceTexture buttonTexture = textures.getButtonForState(false, this.active, hovered);
        buttonTexture.draw(guiGraphics, getX(), getY(), this.width, this.height);

        // 设置图标颜色
        int color = 0xFFE0E0E0;
        if (!this.active) {
            color = 0xFFA0A0A0;
        } else if (hovered) {
            color = 0xFFFFFFFF;
        }

        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float alpha = (color >> 24 & 255) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, alpha);

        // 绘制图标（居中）
        int iconSize = 9;
        double xOffset = getX() + (width - iconSize) / 2.0;
        double yOffset = getY() + (height - iconSize) / 2.0;

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);
        guiGraphics.blit(EDIT_TEXTURE, 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
        poseStack.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (hovered) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltipLines(), mouseX, mouseY);
        }
    }

    private List<Component> getTooltipLines() {
        if (generatedRecipe) {
            // 生成配方：编辑此配方
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_edit.edit_generated"));
        } else {
            // 普通配方：编辑配方内容
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_edit.edit_recipe"));
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.recipeId != null && !this.recipeId.isEmpty()) {
            // 播放点击声音
            playDownSound(Minecraft.getInstance().getSoundManager());
            
            // 打开工作区界面，编辑配方内容
            Screen currentScreen = Minecraft.getInstance().screen;
            RecipeWorkspaceManager.getInstance().openWorkspace(currentScreen, recipeLayout);
            
            InGameRecipeEditor.LOGGER.info("编辑配方: {}", recipeId);
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }

    public String getRecipeId() {
        return recipeId;
    }

    public boolean isGeneratedRecipe() {
        return generatedRecipe;
    }
}