package dev.whisperlyric.ingamerecipeeditor.workspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 简易的属性编辑界面（用于编辑 mod/recipe 层的补丁/本地属性）
 * 当前版本：暂时禁用编辑功能，仅保留标题和返回按钮
 */
public class PropertiesEditScreen extends Screen {
    private final Screen parent;
    private final String recipeId;

    private Button saveButton;
    private Button cancelButton;

    protected PropertiesEditScreen(Screen parent, String recipeId) {
        super(Component.literal("编辑其他属性"));
        this.parent = parent;
        this.recipeId = recipeId;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 30;

        saveButton = this.addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(cx - 110, y, 100, 20).build());

        cancelButton = this.addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
            Minecraft.getInstance().setScreen(parent);
        }).bounds(cx + 10, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

