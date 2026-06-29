package dev.whisperlyric.ingamerecipeeditor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 通用数值调整界面。
 * <p>
 * 构造参数中 {@code max}/{@code current}/{@code min} 均为<strong>原始值（raw）</strong>，
 * 即最终写入 JSON 的值。回调 {@code callback} 传出的也是 raw 值。
 * </p>
 * <p>滑条和输入框显示<strong>display 值</strong>：<br>
 * &nbsp;&nbsp;display = raw * multiply / step（整数除法）<br>
 * 示例：raw=1000J, multiply=2, step=5 → display=400FE。</p>
 * <p>构造参数从 {@code min} 开始（含）逐步可选：<br>
 * {@code (parent, callback, max, current)}<br>
 * {@code (parent, callback, max, current, min)}<br>
 * {@code (parent, callback, max, current, min, unit)}<br>
 * {@code (parent, callback, max, current, min, unit, multiply, step)}</p>
 */
public class NumberAdjustmentScreen extends Screen {
    private final Screen parent;
    private final Consumer<Long> callback;

    /** 原始值（raw）范围 */
    private final long minRaw;
    private final long maxRaw;
    private long currentRaw;

    /** 整数乘数和步进，display = raw * multiply / step */
    private final int multiply;
    private final int step;

    /** 显示单位（如"FE"、"mB"），null 则不显示 */
    private final String unit;

    /** display 值范围 */
    private final long minDisplay;
    private final long maxDisplay;
    private long currentDisplay;

    private EditBox valueInput;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnMin;
    private Button btnMax;

    private int sliderX;
    private int sliderY;
    private int sliderWidth = 200;
    private int sliderHeight = 20;
    private boolean isDraggingSlider = false;

    // ===================== 构造函数：从 min 开始逐步可选 =====================

    /**
     * 最简构造：(parent, callback, max, current)
     * min=0, unit=null, multiply=1, step=1
     */
    public NumberAdjustmentScreen(Screen parent, Consumer<Integer> callback, int max, int current) {
        this(parent, v -> callback.accept(v.intValue()), (long) max, (long) current, 0L, null, 1, 1);
    }

    /**
     * (parent, callback, max, current, min)
     * unit=null, multiply=1, step=1
     */
    public NumberAdjustmentScreen(Screen parent, Consumer<Integer> callback, int max, int current, int min) {
        this(parent, v -> callback.accept(v.intValue()), (long) max, (long) current, (long) min, null, 1, 1);
    }

    /**
     * (parent, callback, max, current, min, unit)
     * multiply=1, step=1
     */
    public NumberAdjustmentScreen(Screen parent, Consumer<Integer> callback, int max, int current, int min, String unit) {
        this(parent, v -> callback.accept(v.intValue()), (long) max, (long) current, (long) min, unit, 1, 1);
    }

    /**
     * (parent, callback, max, current, min, unit, multiply, step)
     * 完整构造（int 版）
     */
    public NumberAdjustmentScreen(Screen parent, Consumer<Integer> callback, int max, int current, int min, String unit, int multiply, int step) {
        this(parent, v -> callback.accept(v.intValue()), (long) max, (long) current, (long) min, unit, multiply, step);
    }

    /**
     * (parent, callback, max, current, min, unit, multiply, step)
     * 完整构造（long 版）
     *
     * <p>显示换算公式：display = raw * multiply / step（整数除法）。</p>
     * <p>示例：若 FE=J*2/5，则调用 (..., min=0, unit="FE", multiply=2, step=5)。<br>
     * 滑条和输入框范围：0 ~ max*2/5 FE，回调传出 raw 值（J）。</p>
     */
    public NumberAdjustmentScreen(Screen parent, Consumer<Long> callback, long max, long current, long min, String unit, int multiply, int step) {
        super(Component.literal("数量调整"));
        this.parent = parent;
        this.callback = callback;
        this.minRaw = min;
        this.maxRaw = max;
        this.currentRaw = Math.max(min, Math.min(max, current));
        this.multiply = Math.max(1, multiply);
        this.step = Math.max(1, step);
        this.unit = unit;

        // 计算 display 值范围
        this.minDisplay = rawToDisplay(this.minRaw);
        this.maxDisplay = rawToDisplay(this.maxRaw);
        this.currentDisplay = rawToDisplay(this.currentRaw);
        // 对齐 display 值到 step
        this.currentDisplay = alignDisplayToStep(this.currentDisplay);
        // 反算 raw 值
        this.currentRaw = displayToRaw(this.currentDisplay);
    }

    /**
     * 将 display 值对齐到 step 的倍数
     */
    private long alignDisplayToStep(long displayValue) {
        if (step <= 1) return displayValue;
        long aligned = (displayValue / step) * step;
        return Math.max(minDisplay, Math.min(maxDisplay, aligned));
    }

    /**
     * raw → display：display = raw * multiply / step（整数除法）
     */
    private long rawToDisplay(long raw) {
        return raw * (long) multiply / step;
    }

    /**
     * display → raw：raw = display * step / multiply（整数除法）
     */
    private long displayToRaw(long display) {
        if (multiply <= 0) return display;
        return display * step / multiply;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        sliderX = centerX - sliderWidth / 2;
        sliderY = centerY - 45;

        valueInput = new EditBox(
            Minecraft.getInstance().font,
            centerX - 50,
            centerY - 20,
            100,
            20,
            Component.literal("数量")
        );
        valueInput.setValue(String.valueOf(currentDisplay));
        valueInput.setFilter(s -> {
            try {
                long value = Long.parseLong(s);
                return value >= minDisplay && value <= maxDisplay;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        addRenderableWidget(valueInput);

        btnMin = Button.builder(
            Component.literal(String.valueOf(minDisplay)),
            button -> {
                currentDisplay = minDisplay;
                currentRaw = displayToRaw(currentDisplay);
                valueInput.setValue(String.valueOf(currentDisplay));
            }
        ).bounds(centerX - 100, centerY + 10, 60, 20).build();

        btnMax = Button.builder(
            Component.literal(String.valueOf(maxDisplay)),
            button -> {
                currentDisplay = maxDisplay;
                currentRaw = displayToRaw(currentDisplay);
                valueInput.setValue(String.valueOf(currentDisplay));
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
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 标题
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("数量调整"),
            this.width / 2,
            this.height / 2 - 70,
            0xFFFFFF
        );

        // display 值范围
        String range = String.format("范围: %,d - %,d", minDisplay, maxDisplay);
        if (unit != null) {
            range += " " + unit;
        }
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal(range),
            this.width / 2,
            this.height / 2 - 55,
            0xAAAAAA
        );

        renderSlider(guiGraphics, mouseX, mouseY);

        // 换算公式显示：display x step / multiply = raw
        if (multiply != 1 || step != 1 || unit != null) {
            try {
                long displayVal = Long.parseLong(valueInput.getValue());
                long rawVal = displayToRaw(displayVal);
                String conversion;
                if (unit != null) {
                    conversion = String.format("%,d %s x %d / %d = %,d", displayVal, unit, step, multiply, rawVal);
                } else {
                    conversion = String.format("%,d x %d / %d = %,d", displayVal, step, multiply, rawVal);
                }
                guiGraphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    Component.literal(conversion),
                    this.width / 2,
                    this.height / 2 + 5,
                    0xFFFF00
                );
            } catch (NumberFormatException ignored) {
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSlider(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, 0xFF555555);
        guiGraphics.fill(sliderX + 1, sliderY + 1, sliderX + sliderWidth - 1, sliderY + sliderHeight - 1, 0xFF000000);

        double ratio = (maxDisplay == minDisplay) ? 0.0
                : (double) (currentDisplay - minDisplay) / (double) (maxDisplay - minDisplay);
        int handleX = sliderX + (int) (ratio * (sliderWidth - 8));
        int handleY = sliderY + 2;

        boolean isHovering = mouseX >= handleX && mouseX < handleX + 8 &&
                mouseY >= handleY && mouseY < handleY + sliderHeight - 4;

        int handleColor = isHovering || isDraggingSlider ? 0xFFAAAAAA : 0xFF888888;
        guiGraphics.fill(handleX, handleY, handleX + 8, handleY + sliderHeight - 4, handleColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= sliderX && mouseX < sliderX + sliderWidth &&
                    mouseY >= sliderY && mouseY < sliderY + sliderHeight) {
                isDraggingSlider = true;
                updateValueFromSlider(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingSlider) {
            updateValueFromSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingSlider) {
            isDraggingSlider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateValueFromSlider(double mouseX) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / sliderWidth));
        currentDisplay = minDisplay + (long) (ratio * (maxDisplay - minDisplay));
        currentDisplay = alignDisplayToStep(currentDisplay);
        currentRaw = displayToRaw(currentDisplay);
        valueInput.setValue(String.valueOf(currentDisplay));
    }

    private void confirmValue() {
        try {
            long displayVal = Long.parseLong(valueInput.getValue());
            displayVal = Math.max(minDisplay, Math.min(maxDisplay, displayVal));
            displayVal = alignDisplayToStep(displayVal);

            currentRaw = displayToRaw(displayVal);
            callback.accept(currentRaw);
            Minecraft.getInstance().setScreen(parent);
        } catch (NumberFormatException e) {
            valueInput.setValue(String.valueOf(currentDisplay));
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirmValue();
            return true;
        }
        if (keyCode == 256) {
            confirmValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
