package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 化学物质槽位渲染器 - 用于渲染Mekanism等mod的化学物质槽位
 */
public class ChemicalSlotRenderer {

    private static final ResourceLocation FLUID_TEXTURE = ResourceLocation.parse("minecraft:textures/block/water_still.png");

    /**
     * 渲染化学物质槽位
     */
    public static void renderChemical(GuiGraphics guiGraphics, String chemicalId, long amount, long capacity, 
                                       int x, int y, int width, int height) {
        // 渲染背景
        guiGraphics.fill(x, y, x + width, y + height, 0xFF404040);
        
        // 渲染化学物质填充
        if (amount > 0 && capacity > 0) {
            double fillRatio = Math.min(1.0, (double) amount / capacity);
            int fillHeight = (int) (height * fillRatio);
            int fillY = y + height - fillHeight;
            
            // 尝试获取化学物质颜色
            int color = getChemicalColor(chemicalId);
            
            // 渲染填充
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                0.8F
            );
            
            // 使用简单的矩形填充代替纹理
            guiGraphics.fill(x + 1, fillY, x + width - 1, y + height - 1, 0x80FFFFFF);
            
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        
        // 渲染边框
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF808080);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF808080);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF808080);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF808080);
        
        // 渲染数量文本
        if (amount > 0) {
            String amountText = formatAmount(amount);
            int textWidth = Minecraft.getInstance().font.width(amountText);
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                amountText,
                x + width - textWidth - 2,
                y + height - 10,
                0xFFFFFFFF,
                true
            );
        }
    }

    /**
     * 获取化学物质颜色
     */
    private static int getChemicalColor(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return 0xFFFFFF;
        }
        
        // 尝试通过反射获取Mekanism化学物质的颜色
        try {
            Class<?> chemicalTypeClass = Class.forName("mekanism.api.chemical.ChemicalType");
            // 这里可以添加更多逻辑来获取实际颜色
        } catch (ClassNotFoundException ignored) {
            // Mekanism未加载
        }
        
        // 根据化学物质类型返回默认颜色
        if (chemicalId.contains("gas")) {
            return 0xFFAAAA; // 浅红色
        } else if (chemicalId.contains("infusion")) {
            return 0xAAFFAA; // 浅绿色
        } else if (chemicalId.contains("pigment")) {
            return 0xFFAAFF; // 浅紫色
        } else if (chemicalId.contains("slurry")) {
            return 0xAAAAFF; // 浅蓝色
        }
        
        // 根据ID哈希生成颜色
        int hash = chemicalId.hashCode();
        int r = (hash & 0xFF) | 0x80;
        int g = ((hash >> 8) & 0xFF) | 0x80;
        int b = ((hash >> 16) & 0xFF) | 0x80;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 格式化数量显示
     */
    private static String formatAmount(long amount) {
        if (amount >= 1000000000) {
            return String.format("%.1fB", amount / 1000000000.0);
        } else if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }

    /**
     * 渲染化学物质tooltip
     */
    public static List<Component> getChemicalTooltip(String chemicalId, long amount, long capacity) {
        List<Component> tooltip = new ArrayList<>();
        
        // 化学物质名称
        String name = getChemicalName(chemicalId);
        tooltip.add(Component.literal(name));
        
        // 数量信息
        tooltip.add(Component.translatable(
            "ingamerecipeeditor.tooltip.chemical.amount",
            formatAmount(amount),
            formatAmount(capacity)
        ));
        
        // 化学物质ID
        tooltip.add(Component.literal(chemicalId).withStyle(style -> style.withColor(0x808080)));
        
        return tooltip;
    }

    /**
     * 获取化学物质名称
     */
    private static String getChemicalName(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return "Unknown";
        }
        
        // 尝试通过反射获取Mekanism化学物质的名称
        try {
            ResourceLocation rl = ResourceLocation.tryParse(chemicalId);
            if (rl != null) {
                // 尝试获取翻译键
                String path = rl.getPath();
                // 简单的名称转换
                return path.replace('_', ' ');
            }
        } catch (Exception ignored) {}
        
        return chemicalId;
    }
}