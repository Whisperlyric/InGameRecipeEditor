package dev.whisperlyric.ingamerecipeeditor.workspace;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;

/**
 * 工作区界面属性 - 用于JEI叠加层显示
 * 告诉JEI如何在工作区旁边布局物品列表
 */
public class RecipeWorkspaceGuiProperties implements IGuiProperties {
    
    private final RecipeWorkspaceScreen screen;
    private final int guiLeft;
    private final int guiTop;
    private final int guiXSize;
    private final int guiYSize;
    
    public RecipeWorkspaceGuiProperties(RecipeWorkspaceScreen screen) {
        this.screen = screen;
        
        // 使用配方布局的位置和大小作为GUI区域
        // 这样JEI会在工作区旁边正确显示物品列表
        this.guiLeft = screen.getLayoutX() - 10;
        this.guiTop = screen.getLayoutY() - 10;
        
        // 获取配方布局的大小
        var layoutRect = screen.getRecipeLayout().getRect();
        this.guiXSize = layoutRect.getWidth() + 20;
        this.guiYSize = layoutRect.getHeight() + 20 + 20 + 10;
    }
    
    @Override
    public @NotNull Class<? extends Screen> getScreenClass() {
        return screen.getClass();
    }
    
    @Override
    public int getGuiLeft() {
        return guiLeft;
    }
    
    @Override
    public int getGuiTop() {
        return guiTop;
    }
    
    @Override
    public int getGuiXSize() {
        return guiXSize;
    }
    
    @Override
    public int getGuiYSize() {
        return guiYSize;
    }
    
    @Override
    public int getScreenWidth() {
        return screen.width;
    }
    
    @Override
    public int getScreenHeight() {
        return screen.height;
    }
}