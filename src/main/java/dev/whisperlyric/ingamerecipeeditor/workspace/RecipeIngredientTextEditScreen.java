package dev.whisperlyric.ingamerecipeeditor.workspace;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 配方原料文本编辑界面 - 用于手动输入原料ID和数量
 */
public class RecipeIngredientTextEditScreen extends Screen {
    private static final int PADDING = 10;
    private static final int EDIT_BOX_WIDTH = 200;
    private static final int EDIT_BOX_HEIGHT = 20;

    private final Screen parent;
    private final String recipeId;
    private final List<IRecipeSlotView> slots;
    private final IRecipeSlotDrawable slot;
    private final RecipeEditManager.IngredientEditValue initialValue;

    private EditBox ingredientIdBox;
    private EditBox amountBox;
    private Button submitButton;
    private Button cancelButton;

    private int centerX;
    private int centerY;

    public RecipeIngredientTextEditScreen(Screen parent, String recipeId, List<IRecipeSlotView> slots,
                                           IRecipeSlotDrawable slot, RecipeEditManager.IngredientEditValue initialValue) {
        super(Component.translatable("ingamerecipeeditor.screen.ingredient_edit.title"));
        this.parent = parent;
        this.recipeId = recipeId;
        this.slots = slots;
        this.slot = slot;
        this.initialValue = initialValue;
    }

    @Override
    protected void init() {
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;

        // 原料ID输入框
        this.ingredientIdBox = new EditBox(
            this.font,
            centerX - EDIT_BOX_WIDTH / 2,
            centerY - 40,
            EDIT_BOX_WIDTH,
            EDIT_BOX_HEIGHT,
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.ingredient_id")
        );
        this.ingredientIdBox.setValue(initialValue.ingredientId());
        this.ingredientIdBox.setMaxLength(256);
        this.addRenderableWidget(this.ingredientIdBox);

        // 数量输入框
        this.amountBox = new EditBox(
            this.font,
            centerX - EDIT_BOX_WIDTH / 2,
            centerY - 10,
            EDIT_BOX_WIDTH,
            EDIT_BOX_HEIGHT,
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.amount")
        );
        this.amountBox.setValue(String.valueOf(initialValue.amount()));
        this.amountBox.setMaxLength(10);
        this.amountBox.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.amountBox);

        // 提交按钮
        this.submitButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.submit"),
            button -> submit()
        ).bounds(centerX - 105, centerY + 30, 100, 20).build();
        this.addRenderableWidget(this.submitButton);

        // 取消按钮
        this.cancelButton = Button.builder(
            Component.translatable("ingamerecipeeditor.screen.ingredient_edit.cancel"),
            button -> cancel()
        ).bounds(centerX + 5, centerY + 30, 100, 20).build();
        this.addRenderableWidget(this.cancelButton);
    }

    private void submit() {
        String ingredientId = ingredientIdBox.getValue();
        long amount = 1;
        try {
            amount = Long.parseLong(amountBox.getValue());
        } catch (NumberFormatException e) {
            amount = 1;
        }

        if (!ingredientId.isEmpty()) {
            RecipeEditManager.setSlotEditValue(
                recipeId,
                slots,
                slot,
                new RecipeEditManager.IngredientEditValue(initialValue.kind(), ingredientId, amount)
            );
        }

        close();
    }

    private void cancel() {
        close();
    }

    private void close() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        this.renderBackground(guiGraphics);

        // 渲染面板背景
        int panelWidth = EDIT_BOX_WIDTH + PADDING * 2;
        int panelHeight = 100 + PADDING * 2;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0100010);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF707070);
        guiGraphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF707070);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF707070);
        guiGraphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF707070);

        // 渲染标题
        Component title = Component.translatable("ingamerecipeeditor.screen.ingredient_edit.title");
        guiGraphics.drawString(this.font, title, centerX - this.font.width(title) / 2, panelY + 5, 0xFFFFFF, false);

        // 渲染原料类型
        Component typeLabel = Component.translatable(
            "ingamerecipeeditor.screen.ingredient_edit.type",
            initialValue.kind().name()
        );
        guiGraphics.drawString(this.font, typeLabel, centerX - this.font.width(typeLabel) / 2, centerY - 60, 0xA0A0A0, false);

        // 渲染标签
        Component idLabel = Component.translatable("ingamerecipeeditor.screen.ingredient_edit.ingredient_id");
        guiGraphics.drawString(this.font, idLabel, centerX - EDIT_BOX_WIDTH / 2, centerY - 52, 0xA0A0A0, false);

        Component amountLabel = Component.translatable("ingamerecipeeditor.screen.ingredient_edit.amount");
        guiGraphics.drawString(this.font, amountLabel, centerX - EDIT_BOX_WIDTH / 2, centerY - 22, 0xA0A0A0, false);

        // 渲染子组件
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}