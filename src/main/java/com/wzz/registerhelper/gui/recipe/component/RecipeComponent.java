package com.wzz.registerhelper.gui.recipe.component;

import java.awt.*;

/**
 * 配方组件基类
 */
public abstract class RecipeComponent {
    protected int x, y, width, height;
    protected String id;
    
    public RecipeComponent(int x, int y, int width, int height, String id) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.id = id;
    }
    
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
    
    public abstract ComponentType getType();
    
    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getId() { return id; }
    
    // Setters (用于坐标更新)
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public enum ComponentType {
        SLOT,           // 物品槽位
        FLUID_SLOT,     // 流体槽位
        GAS_SLOT,       // 气体槽位
        SLURRY_SLOT,    // 污泥槽位
        PIGMENT_SLOT,   // 颜料槽位
        INFUSE_TYPE_SLOT, // 灌注类型槽位
        CHEMICAL_SLOT,  // 通用化学品槽位
        ENERGY_SLOT,    // 能量槽位
        NUMBER_INPUT,   // 数值输入框
        STRING_INPUT,   // 文本输入框
        LABEL           // 文本标签
    }
}