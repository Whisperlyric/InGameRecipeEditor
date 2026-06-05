package dev.whisperlyric_fork.gui;

import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ChemicalAmountAdjustmentScreen extends Screen {
    private final Screen parent;
    private final ChemicalSlotComponent.ChemicalType chemicalType;
    private final long minValue;
    private final long maxValue;
    private long currentValue;
    private final Consumer<Long> callback;
    
    private EditBox valueInput;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnMin;
    private Button btnMax;
    private Button btnZero;
    
    public ChemicalAmountAdjustmentScreen(Screen parent, ChemicalSlotComponent.ChemicalType chemicalType, long minValue, long maxValue, long currentValue, Consumer<Long> callback) {
        super(Component.literal(chemicalType.getDisplayName() + "数量调整"));
        this.parent = parent;
        this.chemicalType = chemicalType;
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
            Component.literal("数量")
        );
        valueInput.setValue(String.valueOf(currentValue));
        valueInput.setFilter(s -> {
            try {
                long value = Long.parseLong(s);
                return value >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        addRenderableWidget(valueInput);
        
        btnZero = Button.builder(
            Component.literal("归零"),
            button -> {
                currentValue = 0;
                valueInput.setValue("0");
            }
        ).bounds(centerX - 100, centerY + 10, 60, 20).build();
        
        btnMin = Button.builder(
            Component.literal(String.valueOf(minValue)),
            button -> {
                currentValue = minValue;
                valueInput.setValue(String.valueOf(currentValue));
            }
        ).bounds(centerX - 35, centerY + 10, 60, 20).build();
        
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
        
        addRenderableWidget(btnZero);
        addRenderableWidget(btnMin);
        addRenderableWidget(btnMax);
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        String title = chemicalType.getDisplayName() + "数量调整";
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(title),
            this.width / 2,
            this.height / 2 - 60,
            0xFFFFFF
        );
        
        String range = String.format("范围: %,d - %,d mB", minValue, maxValue);
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(range),
            this.width / 2,
            this.height / 2 - 45,
            0xAAAAAA
        );
        
        String unit = getUnitInfo();
        if (!unit.isEmpty()) {
            guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                Component.literal(unit),
                this.width / 2,
                this.height / 2 + 5,
                0xFFFF00
            );
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private String getUnitInfo() {
        return switch (chemicalType) {
            case GAS -> "单位: mB (毫桶)";
            case SLURRY -> "单位: mB (污泥单位)";
            case PIGMENT -> "单位: mB (颜料单位)";
            case INFUSE_TYPE -> "单位: mB (灌注单位)";
            case ANY -> "单位: mB";
        };
    }
    
    private void confirmValue() {
        try {
            long value = Long.parseLong(valueInput.getValue());
            value = Math.max(0, Math.min(maxValue, value));
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
