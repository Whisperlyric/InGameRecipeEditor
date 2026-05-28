package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class NumberAdjustmentScreen extends Screen {
    private final Screen parent;
    private final long minValue;
    private final long maxValue;
    private long currentValue;
    private final Consumer<Long> callback;
    private final boolean isEnergyMode;
    
    private EditBox valueInput;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnMin;
    private Button btnMax;
    
    public NumberAdjustmentScreen(Screen parent, int minValue, int maxValue, int currentValue, Consumer<Integer> callback) {
        this(parent, (long)minValue, (long)maxValue, (long)currentValue, value -> callback.accept(value.intValue()), false);
    }
    
    public NumberAdjustmentScreen(Screen parent, long minValue, long maxValue, long currentValue, Consumer<Long> callback, boolean isEnergyMode) {
        super(Component.literal(isEnergyMode ? "能量调整" : "数量调整"));
        this.parent = parent;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = Math.max(minValue, Math.min(maxValue, currentValue));
        this.callback = callback;
        this.isEnergyMode = isEnergyMode;
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
            Component.literal(isEnergyMode ? "能量" : "数量")
        );
        valueInput.setValue(String.valueOf(currentValue));
        valueInput.setFilter(s -> {
            try {
                long value = Long.parseLong(s);
                return value >= minValue && value <= maxValue;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        addRenderableWidget(valueInput);
        
        btnMin = Button.builder(
            Component.literal(String.valueOf(minValue)),
            button -> {
                currentValue = minValue;
                valueInput.setValue(String.valueOf(currentValue));
            }
        ).bounds(centerX - 100, centerY + 10, 60, 20).build();
        
        btnMax = Button.builder(
            Component.literal(String.valueOf(maxValue)),
            button -> {
                currentValue = maxValue;
                valueInput.setValue(String.valueOf(currentValue));
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
        
        addRenderableWidget(btnMin);
        addRenderableWidget(btnMax);
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        String title = isEnergyMode ? "能量调整" : "数量调整";
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(title),
            this.width / 2,
            this.height / 2 - 60,
            0xFFFFFF
        );
        
        String range = String.format("范围: %,d - %,d", minValue, maxValue);
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(range),
            this.width / 2,
            this.height / 2 - 45,
            0xAAAAAA
        );
        
        if (isEnergyMode) {
            try {
                long value = Long.parseLong(valueInput.getValue());
                long fe = (long)(value * 0.4);
                String conversion = String.format("x 0.4 = %,d FE", fe);
                guiGraphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    Component.literal(conversion),
                    this.width / 2,
                    this.height / 2 + 5,
                    0xFFFF00
                );
            } catch (NumberFormatException e) {
                // 忽略无效输入
            }
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void confirmValue() {
        try {
            long value = Long.parseLong(valueInput.getValue());
            value = Math.max(minValue, Math.min(maxValue, value));
            callback.accept(value);
            Minecraft.getInstance().setScreen(parent);
        } catch (NumberFormatException e) {
            valueInput.setValue(String.valueOf(currentValue));
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
