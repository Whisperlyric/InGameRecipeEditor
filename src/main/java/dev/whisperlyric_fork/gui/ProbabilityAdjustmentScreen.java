package dev.whisperlyric_fork.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ProbabilityAdjustmentScreen extends Screen {
    private final Screen parent;
    private final double minValue;
    private final double maxValue;
    private double currentValue;
    private final Consumer<Double> callback;
    
    private EditBox valueInput;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnZero;
    private Button btnHalf;
    private Button btnOne;
    
    public ProbabilityAdjustmentScreen(Screen parent, double minValue, double maxValue, double currentValue, Consumer<Double> callback) {
        super(Component.literal("产出概率调整"));
        this.parent = parent;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = Math.max(minValue, Math.min(maxValue, currentValue));
        this.callback = callback;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        valueInput = new EditBox(
            Minecraft.getInstance().font,
            centerX - 50,
            centerY - 20,
            100,
            20,
            Component.literal("概率")
        );
        valueInput.setValue(String.format("%.2f", currentValue));
        valueInput.setFilter(s -> {
            try {
                double value = Double.parseDouble(s);
                return value >= minValue && value <= maxValue;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        addRenderableWidget(valueInput);
        
        btnZero = Button.builder(
            Component.literal("0.00"),
            button -> {
                currentValue = 0.0;
                valueInput.setValue("0.00");
            }
        ).bounds(centerX - 100, centerY + 10, 60, 20).build();
        
        btnHalf = Button.builder(
            Component.literal("0.50"),
            button -> {
                currentValue = 0.5;
                valueInput.setValue("0.50");
            }
        ).bounds(centerX - 30, centerY + 10, 60, 20).build();
        
        btnOne = Button.builder(
            Component.literal("1.00"),
            button -> {
                currentValue = 1.0;
                valueInput.setValue("1.00");
            }
        ).bounds(centerX + 40, centerY + 10, 60, 20).build();
        
        btnConfirm = Button.builder(
            Component.literal("确认"),
            button -> confirmValue()
        ).bounds(centerX - 60, centerY + 40, 50, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX + 10, centerY + 40, 50, 20).build();
        
        addRenderableWidget(btnZero);
        addRenderableWidget(btnHalf);
        addRenderableWidget(btnOne);
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("产出概率调整"),
            this.width / 2,
            this.height / 2 - 60,
            0xFFFFFF
        );
        
        String range = String.format("范围: %.2f - %.2f", minValue, maxValue);
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(range),
            this.width / 2,
            this.height / 2 - 45,
            0xAAAAAA
        );
        
        try {
            double value = Double.parseDouble(valueInput.getValue());
            int percentage = (int)(value * 100);
            String percentageText = String.format("= %d%%", percentage);
            guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                Component.literal(percentageText),
                this.width / 2,
                this.height / 2 + 5,
                0xFFFF00
            );
        } catch (NumberFormatException e) {
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void confirmValue() {
        try {
            double value = Double.parseDouble(valueInput.getValue());
            value = Math.max(minValue, Math.min(maxValue, value));
            callback.accept(value);
            Minecraft.getInstance().setScreen(parent);
        } catch (NumberFormatException e) {
            valueInput.setValue(String.format("%.2f", currentValue));
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
