package dev.whisperlyric_fork.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SpecialPropertiesEditScreen extends Screen {
    private final Screen parent;
    private final String machineType;
    private final Consumer<Map<String, Object>> callback;
    private final Map<String, Object> currentValues;
    
    private static class PropertyDefinition {
        String key;
        String displayName;
        double minValue;
        double maxValue;
        double defaultValue;
        boolean isInteger;
        
        PropertyDefinition(String key, String displayName, double minValue, double maxValue, double defaultValue, boolean isInteger) {
            this.key = key;
            this.displayName = displayName;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.defaultValue = defaultValue;
            this.isInteger = isInteger;
        }
    }
    
    private final List<PropertyDefinition> properties = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();
    private Button btnConfirm;
    private Button btnCancel;
    
    public SpecialPropertiesEditScreen(Screen parent, String machineType, Map<String, Object> currentValues, Consumer<Map<String, Object>> callback) {
        super(Component.literal("编辑特殊配方属性"));
        this.parent = parent;
        this.machineType = machineType;
        this.currentValues = new HashMap<>(currentValues);
        this.callback = callback;
        
        initializeProperties();
    }
    
    private void initializeProperties() {
        switch (machineType) {
            case "mekanism:sawing" -> {
                properties.add(new PropertyDefinition("secondaryChance", "副产物概率", 0.0, 1.0, 1.0, false));
            }
            case "mekanism:separating" -> {
                properties.add(new PropertyDefinition("energyMultiplier", "耗电倍率", 0.0, 1000.0, 1.0, false));
            }
            case "mekanism:reaction" -> {
                properties.add(new PropertyDefinition("duration", "配方耗时", 1, Integer.MAX_VALUE, 100, true));
                properties.add(new PropertyDefinition("energyRequired", "配方耗能", 0, Long.MAX_VALUE, 100000, true));
            }
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = this.height / 2 - properties.size() * 30 / 2;
        
        editBoxes.clear();
        
        for (int i = 0; i < properties.size(); i++) {
            PropertyDefinition prop = properties.get(i);
            int rowY = startY + i * 30;
            
            Object currentValue = currentValues.get(prop.key);
            String valueStr;
            if (currentValue == null) {
                valueStr = prop.isInteger ? String.valueOf((int)prop.defaultValue) : String.valueOf(prop.defaultValue);
            } else {
                valueStr = String.valueOf(currentValue);
            }
            
            EditBox editBox = new EditBox(
                Minecraft.getInstance().font,
                centerX - 50,
                rowY,
                100,
                20,
                Component.literal(prop.displayName)
            );
            editBox.setValue(valueStr);
            editBox.setFilter(s -> {
                try {
                    if (prop.isInteger) {
                        long value = Long.parseLong(s);
                        return value >= prop.minValue && value <= prop.maxValue;
                    } else {
                        double value = Double.parseDouble(s);
                        return value >= prop.minValue && value <= prop.maxValue;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            });
            addRenderableWidget(editBox);
            editBoxes.add(editBox);
        }
        
        int buttonY = startY + properties.size() * 30 + 20;
        
        btnConfirm = Button.builder(
            Component.literal("确认"),
            button -> confirmValues()
        ).bounds(centerX - 60, buttonY, 50, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX + 10, buttonY, 50, 20).build();
        
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    private void confirmValues() {
        Map<String, Object> result = new HashMap<>();
        
        for (int i = 0; i < properties.size(); i++) {
            PropertyDefinition prop = properties.get(i);
            EditBox editBox = editBoxes.get(i);
            
            try {
                if (prop.isInteger) {
                    result.put(prop.key, Long.parseLong(editBox.getValue()));
                } else {
                    result.put(prop.key, Double.parseDouble(editBox.getValue()));
                }
            } catch (NumberFormatException e) {
                result.put(prop.key, prop.defaultValue);
            }
        }
        
        callback.accept(result);
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("编辑特殊配方属性"),
            this.width / 2,
            this.height / 2 - properties.size() * 30 / 2 - 30,
            0xFFFFFF
        );
        
        int centerX = this.width / 2;
        int startY = this.height / 2 - properties.size() * 30 / 2;
        
        for (int i = 0; i < properties.size(); i++) {
            PropertyDefinition prop = properties.get(i);
            int rowY = startY + i * 30;
            
            String rangeText = String.format("[%s - %s]", 
                prop.isInteger ? String.valueOf((long)prop.minValue) : String.valueOf(prop.minValue),
                prop.isInteger ? String.valueOf((long)prop.maxValue) : String.valueOf(prop.maxValue));
            
            String labelText = prop.displayName + " " + rangeText;
            
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                labelText,
                centerX - 150,
                rowY + 6,
                0xAAAAAA,
                false
            );
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
