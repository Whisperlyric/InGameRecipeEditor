package dev.whisperlyric.ingamerecipeeditor.button;

import com.google.gson.JsonObject;
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
 * 配方新建按钮 - 点击新建空工作区（从此配方类型新建）
 */
public class RecipeAddButton extends AbstractRecipeButton {
    private static final ResourceLocation ADD_TEXTURE = ResourceLocation.parse("ingamerecipeeditor:textures/gui/button/recipe_add.png");

    private final String recipeId;
    private final boolean supportedRecipe;
    private final IRecipeLayoutDrawable<?> recipeLayout;
    private final boolean canCreateNew;

    public RecipeAddButton(int x, int y, IRecipeLayoutDrawable<?> recipeLayout) {
        super(x, y, 8, 8);
        this.recipeLayout = recipeLayout;

        // 使用JeiRecipeHelper获取正确的配方ID
        this.recipeId = JeiRecipeHelper.getRecipeId(recipeLayout);
        this.supportedRecipe = recipeLayout.getRecipe() instanceof Recipe<?>;

        boolean canCreateNewFlag = true;
        if (this.recipeId != null) {
            ResourceLocation recipeIdLoc = ResourceLocation.tryParse(this.recipeId);
            // if (recipeIdLoc != null && !recipeIdLoc.getNamespace().equals("minecraft")) {
            //     String recipeType = JeiRecipeHelper.getRecipeType(recipeLayout);
            //     var json = JeiRecipeHelper.loadRecipeJson(this.recipeId, recipeType).orElse(null);
            //     if (json != null && json.has("pattern") && json.has("key")) {
            //         String resultId = extractResultItemId(json);
            //         if (resultId != null) {
            //             ResourceLocation resultLoc = ResourceLocation.tryParse(resultId);
            //             if (resultLoc != null && recipeIdLoc.getPath().equals(resultLoc.getPath())
            //                     && recipeIdLoc.getNamespace().equals(resultLoc.getNamespace())) {
            //                 canCreateNewFlag = false;
            //             }
            //         }
            //     }
            // }

            if (recipeIdLoc != null) {
                String namespace = recipeIdLoc.getNamespace();
                String path = recipeIdLoc.getPath();
                if (namespace.equals("sophisticatedbackpacks") || namespace.equals("sophisticatedstorage")
                        || path.startsWith("sophisticated")) {
                    canCreateNewFlag = false;
                }
            }
        }
        this.canCreateNew = canCreateNewFlag;

        if (!this.canCreateNew) {
            this.active = false;
        }
    }

    public boolean hasValidRecipeId() {
        return this.supportedRecipe && this.recipeId != null && !this.recipeId.isEmpty();
    }

    @Override
    protected ResourceLocation getIconTexture() {
        return ADD_TEXTURE;
    }

    @Override
    protected List<Component> getTooltipLines() {
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
            JsonObject templateJson = dev.whisperlyric.ingamerecipeeditor.workspace.RecipeEditManager.createTemplateFromRecipe(recipeLayout);
            Screen currentScreen = Minecraft.getInstance().screen;
            RecipeWorkspaceManager.getInstance().openEmptyWorkspace(currentScreen, recipeLayout, templateJson);

            InGameRecipeEditor.LOGGER.info("新建空工作区，配方类型: {}", recipeId);
        }
    }

    public String getRecipeId() {
        return recipeId;
    }
}