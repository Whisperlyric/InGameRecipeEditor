package dev.whisperlyric.ingamerecipeeditor.button;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
import dev.whisperlyric.ingamerecipeeditor.util.JeiRecipeHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 配方禁用/启用/删除按钮 - 使用 JEI 的按钮背景渲染
 * 普通配方：显示启/禁用图标，点击切换禁用状态
 * 生成配方：显示删除图标，点击标记删除
 */
public class RecipeDisableButton extends AbstractWidget {
    private static final ResourceLocation ENABLE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_enable.png");
    private static final ResourceLocation DISABLE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_disable.png");
    private static final ResourceLocation DELETE_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_delete.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;
    private boolean enabled;
    private boolean pendingDelete;

    public RecipeDisableButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 9, 9, Component.empty());

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;
        this.generatedRecipe = GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);

        // 初始状态
        this.enabled = this.recipeId == null || this.recipeId.isEmpty() || !DisabledRecipesManager.isRecipeDisabled(this.recipeId);
        this.pendingDelete = GeneratedRecipesManager.isGeneratedRecipeDeletionPending(this.recipeId);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        // 更新状态
        if (generatedRecipe) {
            this.pendingDelete = GeneratedRecipesManager.isGeneratedRecipeDeletionPending(recipeId);
        } else {
            this.enabled = !DisabledRecipesManager.isRecipeDisabled(recipeId);
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
        // 生成配方待删除时显示为"按下"状态
        Textures textures = Internal.getTextures();
        boolean isPressed = generatedRecipe && pendingDelete;
        DrawableNineSliceTexture buttonTexture = textures.getButtonForState(isPressed, this.active, hovered);
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

        // 绘制图标
        int iconSize = 8;
        double xOffset = getX() + (width - iconSize) / 2.0;
        double yOffset = getY() + (height - iconSize) / 2.0;
        // 删除按钮按下时偏移图标
        if (generatedRecipe && pendingDelete) {
            xOffset += 0.5;
            yOffset += 0.5;
        }

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);

        // 根据配方类型选择图标
        ResourceLocation iconTexture;
        if (generatedRecipe) {
            iconTexture = DELETE_TEXTURE;
        } else if (enabled) {
            iconTexture = ENABLE_TEXTURE;
        } else {
            iconTexture = DISABLE_TEXTURE;
        }
        guiGraphics.blit(iconTexture, 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);

        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (hovered) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltipLines(), mouseX, mouseY);
        }
    }

    private List<Component> getTooltipLines() {
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

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
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
}