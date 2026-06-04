package com.wzz.registerhelper.gui.util;

import com.mojang.blaze3d.systems.RenderSystem;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RecipePreviewRenderer {
    
    public static final int SLOT_SIZE = 18;
    public static final int SMALL_SLOT_WIDTH = 16;
    public static final int SMALL_SLOT_HEIGHT = 28;
    
    private static final ResourceLocation ELEMENT_HOLDER = new ResourceLocation("registerhelper", "textures/gui/element_holder.png");
    private static final ResourceLocation GAUGE_SMALL = new ResourceLocation("registerhelper", "textures/gui/gauge/small.png");
    private static final ResourceLocation POWER_SLOT = new ResourceLocation("registerhelper", "textures/gui/slot/power.png");
    private static final ResourceLocation OVERLAY_POWER = new ResourceLocation("registerhelper", "textures/gui/slot/overlay_power.png");
    
    public enum ContentType {
        ITEM,
        FLUID,
        GAS,
        INFUSE_TYPE,
        PIGMENT,
        SLURRY,
        ENERGY
    }
    
    public static class PreviewSlot {
        public final int x;
        public final int y;
        public final ContentType type;
        public final Object content;
        public final long amount;
        
        public PreviewSlot(int x, int y, net.minecraft.world.item.ItemStack item) {
            this.x = x;
            this.y = y;
            this.type = ContentType.ITEM;
            this.content = item;
            this.amount = item.getCount();
        }
        
        public PreviewSlot(int x, int y, FluidStack fluidStack) {
            this.x = x;
            this.y = y;
            this.type = ContentType.FLUID;
            this.content = fluidStack;
            this.amount = fluidStack.getAmount();
        }
        
        public PreviewSlot(int x, int y, String chemicalId, ContentType chemicalType, long amount) {
            this.x = x;
            this.y = y;
            this.type = chemicalType;
            this.content = chemicalId;
            this.amount = amount;
        }
        
        public PreviewSlot(int x, int y, long energyAmount) {
            this.x = x;
            this.y = y;
            this.type = ContentType.ENERGY;
            this.content = energyAmount;
            this.amount = energyAmount;
        }
        
        public boolean isEmpty() {
            if (type == ContentType.ITEM) {
                return ((net.minecraft.world.item.ItemStack) content).isEmpty();
            } else if (type == ContentType.FLUID) {
                return ((FluidStack) content).isEmpty();
            } else if (type == ContentType.ENERGY) {
                return amount <= 0;
            } else {
                return content == null || ((String) content).isEmpty();
            }
        }
    }
    
    public static void renderSlot(GuiGraphics guiGraphics, PreviewSlot slot, boolean isResult) {
        int x = slot.x;
        int y = slot.y;
        
        if (slot.type == ContentType.ITEM) {
            int bgColor = isResult ? 0xFF4A4A4A : 0xFF373737;
            guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bgColor);
            
            int borderColor = isResult ? 0xFF7777FF : 0xFF888888;
            guiGraphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y, borderColor);
            guiGraphics.fill(x - 1, y + SLOT_SIZE, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, borderColor);
            guiGraphics.fill(x - 1, y, x, y + SLOT_SIZE, borderColor);
            guiGraphics.fill(x + SLOT_SIZE, y, x + SLOT_SIZE + 1, y + SLOT_SIZE, borderColor);
        } else if (slot.type == ContentType.ENERGY) {
            com.mojang.logging.LogUtils.getLogger().info("renderSlot: Rendering ENERGY slot at x={}, y={}, amount={}", x, y, slot.amount);
            RenderSystem.setShaderTexture(0, POWER_SLOT);
            guiGraphics.blit(POWER_SLOT, x, y, 0, 0, 18, 18, 18, 18);
        } else {
            RenderSystem.setShaderTexture(0, ELEMENT_HOLDER);
            guiGraphics.blit(ELEMENT_HOLDER, x, y, 0, 0, SMALL_SLOT_WIDTH, SMALL_SLOT_HEIGHT, SMALL_SLOT_WIDTH, SMALL_SLOT_HEIGHT);
            
            RenderSystem.setShaderTexture(0, GAUGE_SMALL);
            guiGraphics.blit(GAUGE_SMALL, x, y, 0, 0, SMALL_SLOT_WIDTH, SMALL_SLOT_HEIGHT, SMALL_SLOT_WIDTH, SMALL_SLOT_HEIGHT);
        }
        
        if (slot.isEmpty()) {
            return;
        }
        
        switch (slot.type) {
            case ITEM -> renderItemSlot(guiGraphics, slot);
            case FLUID -> renderFluidSlot(guiGraphics, slot);
            case GAS, INFUSE_TYPE, PIGMENT, SLURRY -> renderChemicalSlot(guiGraphics, slot);
            case ENERGY -> renderEnergySlot(guiGraphics, slot);
        }
    }
    
    private static void renderItemSlot(GuiGraphics guiGraphics, PreviewSlot slot) {
        net.minecraft.world.item.ItemStack item = (net.minecraft.world.item.ItemStack) slot.content;
        
        try {
            guiGraphics.renderItem(item, slot.x + 1, slot.y + 1);
            
            if (item.getCount() > 1) {
                guiGraphics.renderItemDecorations(Minecraft.getInstance().font, item, slot.x + 1, slot.y + 1);
            }
        } catch (Exception e) {
            guiGraphics.fill(slot.x + 1, slot.y + 1, slot.x + SLOT_SIZE - 1, slot.y + SLOT_SIZE - 1, 0xFFAA3333);
        }
    }
    
    private static void renderFluidSlot(GuiGraphics guiGraphics, PreviewSlot slot) {
        FluidStack fluidStack = (FluidStack) slot.content;
        Fluid fluid = fluidStack.getFluid();
        
        if (fluid == Fluids.EMPTY) {
            return;
        }
        
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        
        int tintColor = fluidExtensions.getTintColor(fluidStack);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        // 获取静止纹理（参考JEI实现）
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        TextureAtlasSprite sprite = null;
        
        if (stillTexture != null) {
            try {
                sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
                // 检查是否为缺失纹理
                if (sprite != null && sprite.contents().name().toString().contains("missingno")) {
                    sprite = null;
                }
            } catch (Exception e) {
                // 纹理加载失败
                sprite = null;
            }
        }
        
        int x = slot.x;
        int y = slot.y;
        int width = SMALL_SLOT_WIDTH;
        int fullHeight = SMALL_SLOT_HEIGHT;
        int height = (int) (fullHeight * 0.75);
        int yOffset = fullHeight - height;
        
        if (sprite != null) {
            // 正常渲染流体纹理（JEI方式）
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            int textureWidth = 16;
            int textureHeight = 16;
            
            int xTileCount = width / textureWidth;
            int xRemainder = width - (xTileCount * textureWidth);
            int yTileCount = height / textureHeight;
            int yRemainder = height - (yTileCount * textureHeight);
            int yStart = y + yOffset + height;
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float uDif = uMax - uMin;
            float vDif = vMax - vMin;
            
            RenderSystem.enableBlend();
            
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            for (int xTile = 0; xTile <= xTileCount; xTile++) {
                int tileWidth = (xTile == xTileCount) ? xRemainder : textureWidth;
                if (tileWidth == 0) {
                    break;
                }
                int tileX = x + (xTile * textureWidth);
                int maskRight = textureWidth - tileWidth;
                int shiftedX = tileX + textureWidth - maskRight;
                float uLocalDif = uDif * maskRight / textureWidth;
                float uLocalMin = uMin;
                float uLocalMax = uMax - uLocalDif;
                
                for (int yTile = 0; yTile <= yTileCount; yTile++) {
                    int tileHeight = (yTile == yTileCount) ? yRemainder : textureHeight;
                    if (tileHeight == 0) {
                        break;
                    }
                    int tileY = yStart - ((yTile + 1) * textureHeight);
                    int maskTop = textureHeight - tileHeight;
                    float vLocalDif = vDif * maskTop / textureHeight;
                    float vLocalMin = vMin + vLocalDif;
                    float vLocalMax = vMax;
                    
                    vertexBuffer.vertex(matrix4f, tileX, tileY + textureHeight, 0).uv(uLocalMin, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureHeight, 0).uv(uLocalMax, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                    vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
                }
            }
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
        } else {
            // 当纹理不可用时，使用颜色填充
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            guiGraphics.fill(x, y + yOffset, x + width, y + yOffset + height, 0xFFFFFFFF);
        }
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private static void renderChemicalSlot(GuiGraphics guiGraphics, PreviewSlot slot) {
        String chemicalId = (String) slot.content;
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            int color = getChemicalColor(chemicalId, slot.type);
            ResourceLocation texture = getChemicalTexture(chemicalId, slot.type);
            
            com.mojang.logging.LogUtils.getLogger().info("renderChemicalSlot: chemicalId={}, color={:#x}, texture={}, type={}", chemicalId, color, texture, slot.type);
            
            if (texture == null) {
                return;
            }
            
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            
            if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }
            
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            
            int x = slot.x;
            int y = slot.y;
            int width = SMALL_SLOT_WIDTH;
            int fullHeight = SMALL_SLOT_HEIGHT;
            int height = (int) (fullHeight * 0.75);
            int yOffset = fullHeight - height;
            
            int textureWidth = 16;
            int textureHeight = 16;
            
            int xTileCount = width / textureWidth;
            int xRemainder = width - (xTileCount * textureWidth);
            int yTileCount = height / textureHeight;
            int yRemainder = height - (yTileCount * textureHeight);
            int yStart = y + yOffset + height;
            
            float uMin = sprite.getU0();
            float uMax = sprite.getU1();
            float vMin = sprite.getV0();
            float vMax = sprite.getV1();
            float uDif = uMax - uMin;
            float vDif = vMax - vMin;
            
            RenderSystem.enableBlend();
            
            com.mojang.blaze3d.vertex.BufferBuilder vertexBuffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            vertexBuffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
            org.joml.Matrix4f matrix4f = guiGraphics.pose().last().pose();
            
            for (int xTile = 0; xTile <= xTileCount; xTile++) {
                int tileWidth = (xTile == xTileCount) ? xRemainder : textureWidth;
                if (tileWidth == 0) {
                    break;
                }
                int tileX = x + (xTile * textureWidth);
                int maskRight = textureWidth - tileWidth;
                int shiftedX = tileX + textureWidth - maskRight;
                float uLocalDif = uDif * maskRight / textureWidth;
                float uLocalMin = uMin;
                float uLocalMax = uMax - uLocalDif;
                
                for (int yTile = 0; yTile <= yTileCount; yTile++) {
                    int tileHeight = (yTile == yTileCount) ? yRemainder : textureHeight;
                    if (tileHeight == 0) {
                        break;
                    }
                    int tileY = yStart - ((yTile + 1) * textureHeight);
                    int maskTop = textureHeight - tileHeight;
                    float vLocalDif = vDif * maskTop / textureHeight;
                    float vLocalMin = vMin + vLocalDif;
                    float vLocalMax = vMax;
                    
                    vertexBuffer.vertex(matrix4f, tileX, tileY + textureHeight, 0).uv(uLocalMin, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + textureHeight, 0).uv(uLocalMax, vLocalMax).endVertex();
                    vertexBuffer.vertex(matrix4f, shiftedX, tileY + maskTop, 0).uv(uLocalMax, vLocalMin).endVertex();
                    vertexBuffer.vertex(matrix4f, tileX, tileY + maskTop, 0).uv(uLocalMin, vLocalMin).endVertex();
                }
            }
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
            
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            
        } catch (Exception e) {
            guiGraphics.fill(slot.x + 1, slot.y + 1, slot.x + SMALL_SLOT_WIDTH - 1, slot.y + SMALL_SLOT_HEIGHT - 1, colorFromType(slot.type));
        }
    }
    
    private static void renderEnergySlot(GuiGraphics guiGraphics, PreviewSlot slot) {
        int x = slot.x;
        int y = slot.y;
        
        com.mojang.logging.LogUtils.getLogger().info("renderEnergySlot: Rendering overlay at x={}, y={}", x, y);
        // 渲染满电状态（16像素高的能量条）
        RenderSystem.setShaderTexture(0, OVERLAY_POWER);
        guiGraphics.blit(OVERLAY_POWER, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
    }
    
    private static int colorFromType(ContentType type) {
        return switch (type) {
            case GAS -> 0xFF00DDFF;
            case INFUSE_TYPE -> 0xFFDDDD00;
            case PIGMENT -> 0xFFFF00FF;
            case SLURRY -> 0xFF00FF00;
            default -> 0xFF888888;
        };
    }
    
    private static int getChemicalColor(String chemicalId, ContentType type) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return colorFromType(type);
        }
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            
            if (type == ContentType.GAS) {
                Gas gas = MekanismAPI.gasRegistry().getValue(location);
                if (gas != null && !gas.isEmptyType()) {
                    return 0xFF000000 | gas.getTint();
                }
            } else if (type == ContentType.INFUSE_TYPE) {
                InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
                if (infuseType != null && !infuseType.isEmptyType()) {
                    return 0xFF000000 | infuseType.getTint();
                }
            } else if (type == ContentType.PIGMENT) {
                Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
                if (pigment != null && !pigment.isEmptyType()) {
                    return 0xFF000000 | pigment.getTint();
                }
            } else if (type == ContentType.SLURRY) {
                Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
                if (slurry != null && !slurry.isEmptyType()) {
                    return 0xFF000000 | slurry.getTint();
                }
            }
        } catch (Exception e) {
        }
        
        return colorFromType(type);
    }
    
    private static ResourceLocation getChemicalTexture(String chemicalId, ContentType type) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return null;
        }
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            
            if (type == ContentType.GAS) {
                Gas gas = MekanismAPI.gasRegistry().getValue(location);
                if (gas != null && !gas.isEmptyType()) {
                    return gas.getIcon();
                }
            } else if (type == ContentType.INFUSE_TYPE) {
                InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
                if (infuseType != null && !infuseType.isEmptyType()) {
                    return infuseType.getIcon();
                }
            } else if (type == ContentType.PIGMENT) {
                Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
                if (pigment != null && !pigment.isEmptyType()) {
                    return pigment.getIcon();
                }
            } else if (type == ContentType.SLURRY) {
                Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
                if (slurry != null && !slurry.isEmptyType()) {
                    return slurry.getIcon();
                }
            }
        } catch (Exception e) {
        }
        
        return null;
    }
    
    public static List<Component> getTooltip(PreviewSlot slot) {
        List<Component> tooltip = new ArrayList<>();
        
        if (slot.isEmpty()) {
            tooltip.add(Component.literal("§7空"));
            return tooltip;
        }
        
        switch (slot.type) {
            case ITEM -> {
                net.minecraft.world.item.ItemStack item = (net.minecraft.world.item.ItemStack) slot.content;
                tooltip.add(item.getHoverName());
                if (item.getCount() > 1) {
                    tooltip.add(Component.literal("§7数量: " + item.getCount()));
                }
            }
            case FLUID -> {
                FluidStack fluidStack = (FluidStack) slot.content;
                tooltip.add(fluidStack.getDisplayName());
                tooltip.add(Component.literal("§7" + ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid())));
                tooltip.add(Component.literal("§7数量: " + fluidStack.getAmount() + " mB"));
            }
            case GAS, INFUSE_TYPE, PIGMENT, SLURRY -> {
                String chemicalId = (String) slot.content;
                String name = getChemicalDisplayName(chemicalId, slot.type);
                tooltip.add(Component.literal("§6" + name));
                tooltip.add(Component.literal("§7" + chemicalId));
                tooltip.add(Component.literal("§7数量: " + formatAmount(slot.amount)));
            }
        }
        
        return tooltip;
    }
    
    private static String getChemicalDisplayName(String chemicalId, ContentType type) {
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            
            if (type == ContentType.GAS) {
                Gas gas = MekanismAPI.gasRegistry().getValue(location);
                if (gas != null && !gas.isEmptyType()) {
                    return gas.getTextComponent().getString();
                }
            } else if (type == ContentType.INFUSE_TYPE) {
                InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
                if (infuseType != null && !infuseType.isEmptyType()) {
                    return infuseType.getTextComponent().getString();
                }
            } else if (type == ContentType.PIGMENT) {
                Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
                if (pigment != null && !pigment.isEmptyType()) {
                    return pigment.getTextComponent().getString();
                }
            } else if (type == ContentType.SLURRY) {
                Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
                if (slurry != null && !slurry.isEmptyType()) {
                    return slurry.getTextComponent().getString();
                }
            }
        } catch (Exception e) {
        }
        
        String path = chemicalId.contains(":") ? chemicalId.substring(chemicalId.indexOf(":") + 1) : chemicalId;
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
    
    private static String formatAmount(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }
}
