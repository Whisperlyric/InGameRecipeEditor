package com.wzz.registerhelper.gui.recipe.layout;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import java.awt.*;
import java.util.List;

/**
 * 配方布局接口 - 支持多种组件类型
 */
public interface RecipeLayout {
    /**
     * 生成配方组件列表
     * @param baseX 基础X坐标
     * @param baseY 基础Y坐标
     * @param tier 等级（如果支持）
     * @return 组件列表（槽位、输入框、标签等）
     */
    List<RecipeComponent> generateComponents(int baseX, int baseY, int tier);
    
    /**
     * 获取布局的边界框
     */
    Rectangle getBounds(int tier);
    
    /**
     * 是否支持等级缩放
     */
    boolean supportsTiers();
    
    /**
     * 获取布局名称
     */
    String getLayoutName();
    
    /**
     * 获取布局类型（用于GUI判断）
     */
    default LayoutType getLayoutType() {
        return LayoutType.GRID;
    }
    
    /**
     * 获取输出类型（用于创建输出槽位）
     * @return 输出类型：item, fluid, gas, energy, chemical, slurry, pigment, mixed等，null表示默认物品输出
     */
    default String getOutputType() {
        return null;
    }
    
    /**
     * 获取输出槽位的Y轴偏移量
     * @return Y轴偏移量
     */
    default int getOutputYOffset() {
        return 0;
    }
    
    /**
     * 生成输出组件列表（用于多输出槽的情况）
     * @param outputX 输出区域的基础X坐标
     * @param outputY 输出区域的基础Y坐标
     * @return 输出组件列表，如果返回null或空列表则使用getOutputType()创建默认输出槽
     */
    default List<RecipeComponent> generateOutputComponents(int outputX, int outputY) {
        return null;
    }
    
    enum LayoutType {
        GRID,       // 纯网格布局（只有槽位）
        MIXED,      // 混合布局（槽位+其他组件）
        CUSTOM      // 完全自定义
    }
}