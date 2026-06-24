package dev.whisperlyric.ingamerecipeeditor.button;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
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
 * 配方复制按钮 - 点击复制配方内容到新工作区（基于此配方创建新配方）
 * 使用 JEI 的按钮背景渲染
 */
public class RecipeCopyButton extends AbstractWidget {
    private static final ResourceLocation COPY_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_copy.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;

    public RecipeCopyButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 8, 8, Component.empty());
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
    }

    public boolean hasValidRecipeId() {
        return this.supportedRecipe && this.recipeId != null && !this.recipeId.isEmpty();
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
        int iconSize = 8;
        double xOffset = getX() + (width - iconSize) / 2.0;
        double yOffset = getY() + (height - iconSize) / 2.0;

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);
        guiGraphics.blit(COPY_TEXTURE, 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
        poseStack.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (hovered) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltipLines(), mouseX, mouseY);
        }
    }

    private List<Component> getTooltipLines() {
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

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }

    public String getRecipeId() {
        return recipeId;
    }
}