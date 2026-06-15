package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.schema.SlotDefinition;
import net.minecraft.world.item.ItemStack;

/**
 * 工作区槽位组件
 * 表示可视化界面中的一个槽位，处理渲染和用户交互
 */
public class WorkspaceSlot {
    
    private final SlotDefinition definition;
    private final WorkspaceEditor editor;
    private final boolean renderOnly;  // 是否仅渲染（不参与数据收集）
    
    private Object currentData;        // 当前槽位数据
    private boolean hovered;           // 是否被鼠标悬停
    private boolean selected;          // 是否被选中
    
    /**
     * 构造方法
     */
    public WorkspaceSlot(SlotDefinition definition, WorkspaceEditor editor) {
        this(definition, editor, false);
    }
    
    /**
     * 构造方法（指定是否仅渲染）
     */
    public WorkspaceSlot(SlotDefinition definition, WorkspaceEditor editor, boolean renderOnly) {
        this.definition = definition;
        this.editor = editor;
        this.renderOnly = renderOnly;
    }
    
    // ========== 数据管理 ==========
    
    /**
     * 设置槽位数据
     */
    public void setData(Object data) {
        this.currentData = data;
        if (!renderOnly) {
            editor.setSlotData(definition.getId(), data);
        }
    }
    
    /**
     * 设置物品数据
     */
    public void setItemData(ItemStack stack) {
        setData(stack);
    }
    
    /**
     * 清除数据
     */
    public void clearData() {
        this.currentData = null;
        if (!renderOnly) {
            editor.clearSlotData(definition.getId());
        }
    }
    
    /**
     * 获取当前数据
     */
    public Object getData() {
        return currentData;
    }
    
    /**
     * 是否有数据
     */
    public boolean hasData() {
        return currentData != null;
    }
    
    // ========== 交互处理 ==========
    
    /**
     * 处理点击事件
     */
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            selected = true;
            return true;
        }
        return false;
    }
    
    /**
     * 处理拖拽事件
     */
    public boolean onDrag(int mouseX, int mouseY, ItemStack draggedItem) {
        if (isMouseOver(mouseX, mouseY) && !renderOnly) {
            if (definition.isItemSlot()) {
                setItemData(draggedItem.copy());
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理释放事件
     */
    public boolean onRelease(int mouseX, int mouseY) {
        if (selected) {
            selected = false;
            return true;
        }
        return false;
    }
    
    /**
     * 处理鼠标移动事件
     */
    public void onMouseMove(int mouseX, int mouseY) {
        hovered = isMouseOver(mouseX, mouseY);
    }
    
    /**
     * 检查鼠标是否在槽位上
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        int x = definition.getX();
        int y = definition.getY();
        int width = definition.getWidth();
        int height = definition.getHeight();
        
        return mouseX >= x && mouseX < x + width &&
               mouseY >= y && mouseY < y + height;
    }
    
    // ========== Getter方法 ==========
    
    public SlotDefinition getDefinition() { return definition; }
    public boolean isRenderOnly() { return renderOnly; }
    public boolean isHovered() { return hovered; }
    public boolean isSelected() { return selected; }
    
    public String getId() { return definition.getId(); }
    public int getX() { return definition.getX(); }
    public int getY() { return definition.getY(); }
    public int getWidth() { return definition.getWidth(); }
    public int getHeight() { return definition.getHeight(); }
    
    public SlotDefinition.SlotRole getRole() { return definition.getRole(); }
    public SlotDefinition.IngredientType getType() { return definition.getType(); }
    public boolean isRequired() { return definition.isRequired(); }
    
    // ========== 类型判断方法 ==========
    
    public boolean isInputSlot() { return definition.isInputSlot(); }
    public boolean isOutputSlot() { return definition.isOutputSlot(); }
    public boolean isInJson() { return definition.isInJson(); }
    
    public boolean isItemSlot() { return definition.isItemSlot(); }
    public boolean isFluidSlot() { return definition.isFluidSlot(); }
    public boolean isGasSlot() { return definition.isGasSlot(); }
    public boolean isChemicalSlot() { return definition.isChemicalSlot(); }
}