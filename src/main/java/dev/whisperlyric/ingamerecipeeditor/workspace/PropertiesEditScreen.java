package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.gui.NumberAdjustmentScreen;
import dev.whisperlyric.ingamerecipeeditor.schema.PropertyDefinition;
import dev.whisperlyric.ingamerecipeeditor.schema.RecipeSchema;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 属性编辑界面
 * 从 Schema 的 PropertyDefinition 列表自动渲染属性条目
 * masa 风格：每个属性一行，左侧标签，右侧值（点击编辑）
 */
public class PropertiesEditScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int PANEL_WIDTH = 260;
    private static final int LABEL_WIDTH = 110;

    private final Screen parent;
    private final String recipeId;
    private final String recipeType;

    // 属性编辑值（本地草稿，保存时写入 RecipeEditManager）
    private final Map<String, Object> editedValues = new LinkedHashMap<>();

    // 字符串属性的 EditBox
    private final Map<String, EditBox> stringInputs = new LinkedHashMap<>();

    // 当前 Schema（可能为 null）
    private RecipeSchema schema;
    private List<PropertyDefinition> properties;
    private int scrollOffset = 0;
    private EditBox activeStringEdit = null;

    public PropertiesEditScreen(Screen parent, String recipeId, String recipeType) {
        super(Component.literal("编辑属性"));
        this.parent = parent;
        this.recipeId = recipeId;
        this.recipeType = recipeType;
    }

    @Override
    protected void init() {
        this.schema = SchemaRegistry.getSchema(recipeType).orElse(null);
        this.properties = schema != null ? schema.getProperties() : List.of();

        // 初始化编辑值：优先从草稿读取，其次从 JSON 读取，最后用默认值
        if (editedValues.isEmpty()) {
            for (PropertyDefinition prop : properties) {
                Object draftVal = RecipeEditManager.getPropertyDraft(recipeId, prop.getJsonField());
                if (draftVal != null) {
                    editedValues.put(prop.getJsonField(), draftVal);
                } else {
                    Object jsonVal = RecipeEditManager.readPropertyFromJson(
                        recipeId, prop.getJsonField(), prop.getDefaultValue());
                    editedValues.put(prop.getJsonField(), jsonVal);
                }
            }
        }

        this.clearWidgets();
        stringInputs.clear();
        activeStringEdit = null;

        int cx = this.width / 2;
        int panelLeft = cx - PANEL_WIDTH / 2;
        int panelTop = 30;
        int propAreaTop = panelTop + 18;

        // 创建字符串属性的 EditBox
        int y = propAreaTop;
        for (int i = 0; i < properties.size(); i++) {
            PropertyDefinition prop = properties.get(i);
            if (prop.getType() == PropertyDefinition.PropertyType.STRING) {
                EditBox editBox = new EditBox(
                    this.font,
                    panelLeft + LABEL_WIDTH + 4,
                    y - scrollOffset + 2,
                    PANEL_WIDTH - LABEL_WIDTH - 8,
                    16,
                    Component.literal(prop.getDisplayName())
                );
                editBox.setValue(String.valueOf(editedValues.getOrDefault(prop.getJsonField(), "")));
                editBox.setResponder(val -> editedValues.put(prop.getJsonField(), val));
                editBox.setVisible(isRowVisible(y - scrollOffset, propAreaTop));
                stringInputs.put(prop.getJsonField(), editBox);
                this.addRenderableWidget(editBox);
            }
            y += ROW_HEIGHT;
        }

        // 底部按钮
        int btnY = Math.min(propAreaTop + properties.size() * ROW_HEIGHT + 12, this.height - 30);

        this.addRenderableWidget(Button.builder(Component.literal("保存"), b -> save()).bounds(cx - 70, btnY, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("取消"), b -> cancel()).bounds(cx + 10, btnY, 60, 20).build());
    }

    private boolean isRowVisible(int rowY, int areaTop) {
        return rowY >= -ROW_HEIGHT && rowY < this.height - areaTop - 50;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int cx = this.width / 2;
        int panelLeft = cx - PANEL_WIDTH / 2;
        int panelTop = 30;
        int propAreaTop = panelTop + 18;
        int propAreaBottom = Math.min(propAreaTop + properties.size() * ROW_HEIGHT, this.height - 50);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);

        if (properties.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.literal("此配方类型无属性可编辑"), cx, propAreaTop + 20, 0xAAAAAA);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        // 面板背景
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, propAreaBottom, 0xC0101010);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 16, 0xFF303030);
        guiGraphics.drawString(this.font, "属性", panelLeft + 4, panelTop + 4, 0xCCCCCC, false);

        // 裁剪区域
        guiGraphics.enableScissor(panelLeft, propAreaTop, panelLeft + PANEL_WIDTH, propAreaBottom);

        int y = propAreaTop;
        for (int i = 0; i < properties.size(); i++) {
            PropertyDefinition prop = properties.get(i);
            int rowY = y - scrollOffset;
            if (rowY > propAreaBottom - propAreaTop + ROW_HEIGHT) break;

            if (rowY > -ROW_HEIGHT) {
                Object value = editedValues.get(prop.getJsonField());
                boolean hovering = mouseX >= panelLeft && mouseX < panelLeft + PANEL_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                // 行背景
                int bgColor = hovering ? 0x30FFFFFF : (i % 2 == 0 ? 0x10FFFFFF : 0x00000000);
                if (bgColor != 0) {
                    guiGraphics.fill(panelLeft, rowY, panelLeft + PANEL_WIDTH, rowY + ROW_HEIGHT, bgColor);
                }

                // 分隔线
                guiGraphics.fill(panelLeft, rowY + ROW_HEIGHT - 1, panelLeft + PANEL_WIDTH, rowY + ROW_HEIGHT, 0x20FFFFFF);

                // 标签
                String displayName = prop.getDisplayName() != null ? prop.getDisplayName() : prop.getId();
                guiGraphics.drawString(this.font, displayName, panelLeft + 4, rowY + 7, 0xCCCCCC, false);

                // 值显示
                int valueX = panelLeft + LABEL_WIDTH + 4;
                int valueY = rowY + 7;

                switch (prop.getType()) {
                    case INTEGER, FLOAT -> {
                        // 数值类型：显示值，点击打开 NumberAdjustmentScreen
                        String displayVal = formatValue(value, prop.getType());
                        boolean valHover = mouseX >= valueX && mouseX < panelLeft + PANEL_WIDTH - 4
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                        int valColor = valHover ? 0x55FF55 : 0xFFFF55;
                        guiGraphics.drawString(this.font, displayVal, valueX, valueY, valColor, false);
                        if (valHover) {
                            guiGraphics.fill(valueX - 2, rowY + 1, panelLeft + PANEL_WIDTH - 2, rowY + ROW_HEIGHT - 1, 0x2055FF55);
                        }
                    }
                    case BOOLEAN -> {
                        // 布尔类型：显示 是/否，点击切换
                        boolean boolVal = value instanceof Boolean b ? b : false;
                        String boolText = boolVal ? "是" : "否";
                        boolean valHover = mouseX >= valueX && mouseX < panelLeft + PANEL_WIDTH - 4
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                        int valColor = boolVal ? 0x55FF55 : 0xFF5555;
                        guiGraphics.drawString(this.font, boolText, valueX, valueY, valColor, false);
                        if (valHover) {
                            guiGraphics.fill(valueX - 2, rowY + 1, panelLeft + PANEL_WIDTH - 2, rowY + ROW_HEIGHT - 1, 0x2055FF55);
                        }
                    }
                    case STRING -> {
                        // 字符串类型：EditBox 已在 init 中创建，此处不额外渲染
                    }
                }
            }
            y += ROW_HEIGHT;
        }

        guiGraphics.disableScissor();

        // 滚动条
        if (properties.size() * ROW_HEIGHT > propAreaBottom - propAreaTop) {
            int totalHeight = properties.size() * ROW_HEIGHT;
            int visibleHeight = propAreaBottom - propAreaTop;
            int scrollBarHeight = Math.max(20, (int)((float)visibleHeight / totalHeight * visibleHeight));
            int scrollBarY = propAreaTop + (int)((float)scrollOffset / totalHeight * visibleHeight);
            guiGraphics.fill(panelLeft + PANEL_WIDTH - 4, propAreaTop, panelLeft + PANEL_WIDTH, propAreaBottom, 0x40FFFFFF);
            guiGraphics.fill(panelLeft + PANEL_WIDTH - 4, scrollBarY, panelLeft + PANEL_WIDTH, scrollBarY + scrollBarHeight, 0xFF888888);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String formatValue(Object value, PropertyDefinition.PropertyType type) {
        if (value == null) return "N/A";
        if (type == PropertyDefinition.PropertyType.FLOAT) {
            if (value instanceof Number n) return String.format("%.2f", n.floatValue());
        }
        return String.valueOf(value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && properties != null) {
            int cx = this.width / 2;
            int panelLeft = cx - PANEL_WIDTH / 2;
            int propAreaTop = 48;
            int propAreaBottom = Math.min(propAreaTop + properties.size() * ROW_HEIGHT, this.height - 50);

            if (mouseX >= panelLeft + LABEL_WIDTH && mouseX < panelLeft + PANEL_WIDTH - 4
                && mouseY >= propAreaTop && mouseY < propAreaBottom) {

                int y = propAreaTop;
                for (int i = 0; i < properties.size(); i++) {
                    PropertyDefinition prop = properties.get(i);
                    int rowY = y - scrollOffset;
                    if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        onPropertyClick(prop);
                        return true;
                    }
                    y += ROW_HEIGHT;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onPropertyClick(PropertyDefinition prop) {
        Object currentValue = editedValues.getOrDefault(prop.getJsonField(), prop.getDefaultValue());

        switch (prop.getType()) {
            case INTEGER -> {
                int min = prop.getMin();
                int max = prop.getMax() == Integer.MAX_VALUE ? 100000 : prop.getMax();
                int val = currentValue instanceof Number n ? n.intValue() : 0;
                Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(
                    this,
                    newValue -> editedValues.put(prop.getJsonField(), newValue.intValue()),
                    max, val, min
                ));
            }
            case FLOAT -> {
                // 用整数编辑（乘100），保存时除100
                float fVal = currentValue instanceof Number n ? n.floatValue() : 0f;
                int min = (int)(prop.getMin() * 100);
                int max = prop.getMax() == Integer.MAX_VALUE ? 100000 : (int)(prop.getMax() * 100);
                int intVal = (int)(fVal * 100);
                Minecraft.getInstance().setScreen(new NumberAdjustmentScreen(
                    this,
                    newValue -> editedValues.put(prop.getJsonField(), newValue.intValue() / 100f),
                    max, intVal, min
                ));
            }
            case BOOLEAN -> {
                boolean current = currentValue instanceof Boolean b ? b : false;
                editedValues.put(prop.getJsonField(), !current);
            }
            case STRING -> {
                // 字符串类型通过 EditBox 直接编辑，点击无额外操作
                EditBox editBox = stringInputs.get(prop.getJsonField());
                if (editBox != null) {
                    editBox.setFocused(true);
                    this.setFocused(editBox);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, properties.size() * ROW_HEIGHT - (this.height - 100));
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * ROW_HEIGHT)));
        return true;
    }

    private void save() {
        // 将本地编辑值写入 RecipeEditManager 属性草稿
        for (Map.Entry<String, Object> entry : editedValues.entrySet()) {
            RecipeEditManager.setPropertyDraft(recipeId, entry.getKey(), entry.getValue());
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void cancel() {
        // 丢弃本地编辑，不写入草稿
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
