package dev.whisperlyric.ingamerecipeeditor.button;

import com.google.gson.JsonObject;
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
 * 配方新建按钮 - 点击新建空工作区（从此配方类型新建）
 * 使用 JEI 的按钮背景渲染
 */
public class RecipeAddButton extends AbstractWidget {
    private static final ResourceLocation ADD_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_add.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;
    private final boolean canCreateNew;

    public RecipeAddButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 8, 8, Component.empty());
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;

        // 检测：非 minecraft 命名空间、recipeId == 结果物品 ID、有 pattern+key → 禁止新建
        // 此类配方的 ID 由模组按结果物品自动分配，新建会导致 ID 冲突或结构不兼容
        boolean isThirdPartyShaped = false;
        if (this.recipeId != null) {
            ResourceLocation recipeIdLoc = ResourceLocation.tryParse(this.recipeId);
            if (recipeIdLoc != null && !recipeIdLoc.getNamespace().equals("minecraft")) {
                String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);
                var json = JeiRecipeHelper.loadRecipeJson(this.recipeId, recipeType).orElse(null);
                if (json != null && json.has("pattern") && json.has("key")) {
                    // 检查 recipeId.path == result.item 的路径部分
                    String resultId = extractResultItemId(json);
                    if (resultId != null) {
                        ResourceLocation resultLoc = ResourceLocation.tryParse(resultId);
                        if (resultLoc != null && recipeIdLoc.getPath().equals(resultLoc.getPath())
                                && recipeIdLoc.getNamespace().equals(resultLoc.getNamespace())) {
                            isThirdPartyShaped = true;
                        }
                    }
                }
            }
        }
        this.canCreateNew = !isThirdPartyShaped;

        if (!this.canCreateNew) {
            this.active = false;
        }
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

        // 绘制图标
        int iconSize = 8;
        double xOffset = getX() + (width - iconSize) / 2.0;
        double yOffset = getY() + (height - iconSize) / 2.0;

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);
        guiGraphics.blit(ADD_TEXTURE, 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
        poseStack.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (hovered) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltipLines(), mouseX, mouseY);
        }
    }

    private List<Component> getTooltipLines() {
        if (!this.canCreateNew) {
            return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_add.unsupported"));
        }
        return List.of(Component.translatable("ingamerecipeeditor.tooltip.recipe_add.new_workspace"));
    }

    /**
     * 从配方 JSON 中提取结果物品 ID
     */
    private static String extractResultItemId(JsonObject json) {
        // result.item / result.id
        if (json.has("result") && json.get("result").isJsonObject()) {
            JsonObject result = json.getAsJsonObject("result");
            if (result.has("item")) return result.get("item").getAsString();
            if (result.has("id")) return result.get("id").getAsString();
        }
        // result 字段直接是字符串
        if (json.has("result") && json.get("result").isJsonPrimitive()) {
            return json.get("result").getAsString();
        }
        return null;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.recipeId != null && !this.recipeId.isEmpty()) {
            // 播放点击声音
            playDownSound(Minecraft.getInstance().getSoundManager());

            // 生成模板 JSON 并打开工作区
            com.google.gson.JsonObject templateJson = dev.whisperlyric.ingamerecipeeditor.workspace.RecipeEditManager.createTemplateFromRecipe(recipeLayout);
            Screen currentScreen = Minecraft.getInstance().screen;
            RecipeWorkspaceManager.getInstance().openEmptyWorkspace(currentScreen, recipeLayout, templateJson);

            InGameRecipeEditor.LOGGER.info("新建空工作区，配方类型: {}", recipeId);
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }

    public String getRecipeId() {
        return recipeId;
    }
}