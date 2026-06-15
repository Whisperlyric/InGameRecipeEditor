package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * 化学物质槽位渲染器
 * 用于渲染Mekanism的化学品（Gas、InfuseType、Pigment、Slurry）
 */
public class ChemicalSlotRenderer {
    
    private static final int TEXTURE_SIZE = 16;
    private static final int MIN_CHEMICAL_HEIGHT = 1;
    
    /**
     * 渲染化学品槽位
     * @param guiGraphics GUI图形上下文
     * @param chemicalData 化学品数据（可以是ID字符串或化学品对象）
     * @param x X坐标
     * @param y Y坐标
     * @param width 宽度
     * @param height 高度
     */
    public static void renderChemical(GuiGraphics guiGraphics, Object chemicalData, int x, int y, int width, int height) {
        if (chemicalData == null) {
            return;
        }
        
        // 尝试获取化学品信息
        ChemicalInfo info = getChemicalInfo(chemicalData);
        if (info == null) {
            return;
        }
        
        renderChemicalFromInfo(guiGraphics, info, x, y, width, height);
    }
    
    /**
     * 渲染化学品槽位（带数量和容量）
     */
    public static void renderChemical(GuiGraphics guiGraphics, String chemicalId, long amount, long capacity, int x, int y, int width, int height) {
        if (chemicalId == null || chemicalId.isEmpty() || amount <= 0) {
            return;
        }
        
        ChemicalInfo info = getChemicalInfoById(chemicalId);
        if (info == null) {
            return;
        }
        
        info.amount = amount;
        info.capacity = capacity;
        renderChemicalFromInfo(guiGraphics, info, x, y, width, height);
    }
    
    /**
     * 从ChemicalInfo渲染化学品
     */
    private static void renderChemicalFromInfo(GuiGraphics guiGraphics, ChemicalInfo info, int x, int y, int width, int height) {
        if (info.icon == null) {
            return;
        }
        
        // 计算填充高度
        int desiredHeight = height - 2;
        if (info.capacity > 0) {
            desiredHeight = (int) ((height - 2) * (double) info.amount / info.capacity);
        }
        if (desiredHeight < MIN_CHEMICAL_HEIGHT) {
            desiredHeight = MIN_CHEMICAL_HEIGHT;
        }
        if (desiredHeight > height - 2) {
            desiredHeight = height - 2;
        }
        
        // 设置颜色
        float r = ((info.color >> 16) & 0xFF) / 255.0f;
        float g = ((info.color >> 8) & 0xFF) / 255.0f;
        float b = (info.color & 0xFF) / 255.0f;
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        
        // 获取精灵图
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(info.icon);
        
        if (sprite == null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }
        
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        
        // 渲染平铺精灵（从底部向上填充）
        renderTiledSprite(guiGraphics, x + 1, y + 1, height - 2, width - 2, desiredHeight, sprite);
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // 渲染数量文本
        if (info.amount > 0) {
            String amountText = formatAmount(info.amount);
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                amountText,
                x + 2, y + height - 10,
                0xFFFFFF, true
            );
        }
    }
    
    /**
     * 渲染平铺精灵
     */
    private static void renderTiledSprite(GuiGraphics guiGraphics, int xPosition, int yPosition, int yOffset, int desiredWidth, int desiredHeight, TextureAtlasSprite sprite) {
        if (desiredWidth == 0 || desiredHeight == 0) {
            return;
        }
        
        int xTileCount = desiredWidth / TEXTURE_SIZE;
        int xRemainder = desiredWidth - (xTileCount * TEXTURE_SIZE);
        int yTileCount = desiredHeight / TEXTURE_SIZE;
        int yRemainder = desiredHeight - (yTileCount * TEXTURE_SIZE);
        int yStart = yPosition + yOffset;
        
        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();
        float uDif = uMax - uMin;
        float vDif = vMax - vMin;
        
        RenderSystem.enableBlend();
        
        BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        vertexBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix4f = guiGraphics.pose().last().pose();
        
        for (int xTile = 0; xTile <= xTileCount; xTile++) {
            int tileWidth = (xTile == xTileCount) ? xRemainder : TEXTURE_SIZE;
            if (tileWidth == 0) {
                break;
            }
            int tileX = xPosition + (xTile * TEXTURE_SIZE);
            int maskRight = TEXTURE_SIZE - tileWidth;
            int shiftedX = tileX + TEXTURE_SIZE - maskRight;
            float uLocalDif = uDif * maskRight / TEXTURE_SIZE;
            float uLocalMin = uMin;
            float uLocalMax = uMax - uLocalDif;
            
            for (int yTile = 0; yTile <= yTileCount; yTile++) {
                int tileHeight = (yTile == yTileCount) ? yRemainder : TEXTURE_SIZE;
                if (tileHeight == 0) {
                    break;
                }
                int tileY = yStart - ((yTile + 1) * TEXTURE_SIZE);
                int maskTop = TEXTURE_SIZE - tileHeight;
                float vLocalDif = vDif * maskTop / TEXTURE_SIZE;
                float vLocalMin = vMin + vLocalDif;
                float vLocalMax = vMax;
                
                vertexBuffer.vertex(matrix4f, tileX, tileY + TEXTURE_SIZE, 0).uv(uLocalMin, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + TEXTURE_SIZE, 0).uv(uLocalMax, vLocalMax).endVertex();
                vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
            }
        }
        
        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
    }
    
    /**
     * 获取化学品信息
     */
    private static ChemicalInfo getChemicalInfo(Object chemicalData) {
        if (chemicalData instanceof String chemicalId) {
            return getChemicalInfoById(chemicalId);
        }
        
        // 尝试通过反射获取Mekanism化学品信息
        return getChemicalInfoFromMekanismObject(chemicalData);
    }
    
    /**
     * 通过ID获取化学品信息
     */
    private static ChemicalInfo getChemicalInfoById(String chemicalId) {
        ResourceLocation location = ResourceLocation.tryParse(chemicalId);
        if (location == null) {
            return null;
        }
        
        // 尝试通过反射访问Mekanism API
        try {
            Class<?> mekanismApiClass = Class.forName("mekanism.api.MekanismAPI");
            
            // 尝试从各个化学品注册表获取
            ChemicalInfo info = tryGetFromRegistry(mekanismApiClass, "gasRegistry", location);
            if (info != null) return info;
            
            info = tryGetFromRegistry(mekanismApiClass, "infuseTypeRegistry", location);
            if (info != null) return info;
            
            info = tryGetFromRegistry(mekanismApiClass, "pigmentRegistry", location);
            if (info != null) return info;
            
            info = tryGetFromRegistry(mekanismApiClass, "slurryRegistry", location);
            if (info != null) return info;
            
        } catch (ClassNotFoundException e) {
            InGameRecipeEditor.LOGGER.debug("Mekanism API未加载");
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.debug("获取化学品信息失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 尝试从注册表获取化学品信息
     */
    private static ChemicalInfo tryGetFromRegistry(Class<?> mekanismApiClass, String registryName, ResourceLocation location) {
        try {
            Object registry = mekanismApiClass.getField(registryName).get(null);
            Object chemical = registry.getClass().getMethod("getValue", ResourceLocation.class)
                .invoke(registry, location);
            
            if (chemical != null) {
                return extractChemicalInfo(chemical);
            }
        } catch (Exception e) {
            // 静默处理
        }
        return null;
    }
    
    /**
     * 从Mekanism化学品对象提取信息
     */
    private static ChemicalInfo getChemicalInfoFromMekanismObject(Object chemicalData) {
        if (!isChemicalStack(chemicalData)) {
            return null;
        }
        
        try {
            // 获取化学品类型
            Object type = chemicalData.getClass().getMethod("getType").invoke(chemicalData);
            if (type == null) return null;
            
            ChemicalInfo info = extractChemicalInfo(type);
            
            // 获取数量
            Object amount = chemicalData.getClass().getMethod("getAmount").invoke(chemicalData);
            if (amount instanceof Number number) {
                info.amount = number.longValue();
            }
            
            return info;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从化学品类型对象提取信息
     */
    private static ChemicalInfo extractChemicalInfo(Object chemical) {
        try {
            ChemicalInfo info = new ChemicalInfo();
            
            // 获取颜色
            Object tint = chemical.getClass().getMethod("getTint").invoke(chemical);
            if (tint instanceof Integer) {
                info.color = (Integer) tint;
            }
            
            // 获取图标
            Object icon = chemical.getClass().getMethod("getIcon").invoke(chemical);
            if (icon instanceof ResourceLocation) {
                info.icon = (ResourceLocation) icon;
            }
            
            // 检查是否为空类型
            Object isEmpty = chemical.getClass().getMethod("isEmptyType").invoke(chemical);
            if (isEmpty instanceof Boolean && (Boolean) isEmpty) {
                return null;
            }
            
            return info;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查是否为化学品堆
     */
    public static boolean isChemicalStack(Object ingredient) {
        if (ingredient == null) return false;
        String className = ingredient.getClass().getName();
        return className.startsWith("mekanism.api.chemical.") && className.endsWith("Stack");
    }
    
    /**
     * 获取化学品显示名称
     */
    public static String getChemicalDisplayName(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return "空";
        }
        
        try {
            ResourceLocation location = ResourceLocation.tryParse(chemicalId);
            if (location == null) return chemicalId;
            
            // 尝试通过反射获取显示名称
            Class<?> mekanismApiClass = Class.forName("mekanism.api.MekanismAPI");
            
            String[] registries = {"gasRegistry", "infuseTypeRegistry", "pigmentRegistry", "slurryRegistry"};
            for (String registryName : registries) {
                try {
                    Object registry = mekanismApiClass.getField(registryName).get(null);
                    Object chemical = registry.getClass().getMethod("getValue", ResourceLocation.class)
                        .invoke(registry, location);
                    
                    if (chemical != null) {
                        Object textComponent = chemical.getClass().getMethod("getTextComponent").invoke(chemical);
                        if (textComponent instanceof Component component) {
                            return component.getString();
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (ClassNotFoundException e) {
            // Mekanism未加载
        }
        
        // 格式化ID作为备用名称
        return formatIdAsName(chemicalId);
    }
    
    /**
     * 格式化ID为显示名称
     */
    private static String formatIdAsName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(":") + 1) : id;
        path = path.replace("_", " ");
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : path.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    /**
     * 格式化数量显示
     */
    public static String formatAmount(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fB", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }
    
    /**
     * 格式化详细数量显示
     */
    public static String formatAmountDetailed(long amount) {
        return String.format("%,d mB", amount);
    }
    
    /**
     * 化学品信息内部类
     */
    private static class ChemicalInfo {
        ResourceLocation icon;
        int color = 0xFFFFFF;
        long amount = 1000;
        long capacity = 1000;
    }
}