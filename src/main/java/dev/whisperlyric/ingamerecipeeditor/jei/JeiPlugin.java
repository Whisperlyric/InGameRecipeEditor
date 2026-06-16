package dev.whisperlyric.ingamerecipeeditor.jei;

import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceGhostIngredientHandler;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceGuiProperties;
import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

//JEI插件 - 注册工作区界面的JEI支持
@mezz.jei.api.JeiPlugin
public class JeiPlugin implements IModPlugin {
    
    private static final ResourceLocation PLUGIN_ID = ResourceLocation.parse("ingamerecipeeditor:jei_plugin");
    
    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }
    
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 注册工作区界面的GuiProperties，使用lambda方式（与src-old一致）
        registration.addGuiScreenHandler(RecipeWorkspaceScreen.class, RecipeWorkspaceGuiProperties::new);
        
        // 注册Ghost Ingredient Handler
        registration.addGhostIngredientHandler(
            RecipeWorkspaceScreen.class,
            new RecipeWorkspaceGhostIngredientHandler()
        );
        
        InGameRecipeEditor.LOGGER.info("JEI插件已注册工作区界面支持");
    }
}