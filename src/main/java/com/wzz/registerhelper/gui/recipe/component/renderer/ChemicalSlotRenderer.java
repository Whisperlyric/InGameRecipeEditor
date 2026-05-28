package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import dev.whisperlyric_fork.gui.ChemicalSelectionScreen;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.function.Consumer;

public class ChemicalSlotRenderer implements ComponentRenderer {
    private final ChemicalSlotComponent component;
    private Consumer<ChemicalSlotComponent> onClick;
    private boolean active = true;
    private boolean isOutput = false;
    
    private static final ResourceLocation ELEMENT_HOLDER = new ResourceLocation("registerhelper", "textures/gui/element_holder.png");
    private static final ResourceLocation GAUGE_STANDARD = new ResourceLocation("registerhelper", "textures/gui/gauge/standard.png");
    private static final ResourceLocation GAS_TEXTURE = new ResourceLocation("registerhelper", "liquid/liquid");
    private static final ResourceLocation INFUSE_TYPE_BASE = new ResourceLocation("registerhelper", "infuse_type/base");
    private static final ResourceLocation INFUSE_TYPE_BIO = new ResourceLocation("registerhelper", "infuse_type/bio");
    private static final ResourceLocation INFUSE_TYPE_FUNGI = new ResourceLocation("registerhelper", "infuse_type/fungi");
    private static final ResourceLocation PIGMENT_BASE = new ResourceLocation("registerhelper", "pigment/base");
    private static final ResourceLocation SLURRY_BASE = new ResourceLocation("registerhelper", "slurry/base");
    
    public ChemicalSlotRenderer(ChemicalSlotComponent component, Consumer<ChemicalSlotComponent> onClick) {
        this.component = component;
        this.onClick = onClick;
    }
    
    public void setOutput(boolean output) {
        this.isOutput = output;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        
        int x = component.getX();
        int y = component.getY();
        int width = 16;
        int height = 58;
        
        boolean isMouseOver = mouseX >= x && mouseX < x + width &&
                             mouseY >= y && mouseY < y + height;
        
        RenderSystem.setShaderTexture(0, ELEMENT_HOLDER);
        guiGraphics.blit(ELEMENT_HOLDER, x, y, 0, 0, width, height, width, height);
        
        RenderSystem.setShaderTexture(0, GAUGE_STANDARD);
        guiGraphics.blit(GAUGE_STANDARD, x, y, 0, 0, width, height, width, height);
        
        String chemicalId = component.getChemicalId();
        long amount = component.getAmount();
        long maxAmount = component.getMaxAmount();
        
        if (chemicalId != null && !chemicalId.isEmpty() && amount > 0) {
            try {
                int color = getChemicalColor(chemicalId);
                ResourceLocation texture = getChemicalTexture(chemicalId);
                ResourceLocation fallbackTexture = getFallbackTexture(chemicalId);
                
                double fillRatio = (double) amount / maxAmount;
                int fillHeight = (int) (fillRatio * height);
                
                if (fillHeight > 0) {
                    renderChemical(guiGraphics, color, x, y + height - fillHeight, width, fillHeight, texture, fallbackTexture);
                }
                
                String amountText = formatAmount(amount);
                int textWidth = font.width(amountText);
                if (textWidth < width - 4) {
                    guiGraphics.drawString(font, amountText, x + width / 2 - textWidth / 2, y + height / 2 - 4, 0xFFFFFF, true);
                }
                
            } catch (Exception e) {
                guiGraphics.drawString(font, "?", x + width / 2 - 3, y + height / 2 - 4, 0xFFFFFF);
            }
        }
        
        if (isMouseOver) {
            renderTooltip(guiGraphics, font, mouseX, mouseY, chemicalId, amount, maxAmount);
        }
    }
    
    private void renderChemical(GuiGraphics guiGraphics, int color, int x, int y, int width, int height, ResourceLocation textureLocation, ResourceLocation fallbackTexture) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(textureLocation);
        
        if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
            sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(fallbackTexture != null ? fallbackTexture : GAS_TEXTURE);
        }
        
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        
        int textureWidth = 16;
        int textureHeight = 16;
        
        int xTileCount = width / textureWidth;
        int xRemainder = width - (xTileCount * textureWidth);
        int yTileCount = height / textureHeight;
        int yRemainder = height - (yTileCount * textureHeight);
        int yStart = y + height;
        
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
        
        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private int getChemicalColor(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return 0xFFFFFFFF;
        }
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            
            Gas gas = MekanismAPI.gasRegistry().getValue(location);
            if (gas != null && !gas.isEmptyType()) {
                return 0xFF000000 | gas.getTint();
            }
            
            InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
            if (infuseType != null && !infuseType.isEmptyType()) {
                return 0xFF000000 | infuseType.getTint();
            }
            
            Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
            if (pigment != null && !pigment.isEmptyType()) {
                return 0xFF000000 | pigment.getTint();
            }
            
            Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
            if (slurry != null && !slurry.isEmptyType()) {
                return 0xFF000000 | slurry.getTint();
            }
        } catch (Exception e) {
        }
        
        return 0xFFFFFFFF;
    }
    
    private ResourceLocation getChemicalTexture(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return GAS_TEXTURE;
        }
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            String path = location.getPath();
            
            Gas gas = MekanismAPI.gasRegistry().getValue(location);
            if (gas != null && !gas.isEmptyType()) {
                return GAS_TEXTURE;
            }
            
            InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
            if (infuseType != null && !infuseType.isEmptyType()) {
                return new ResourceLocation("registerhelper", "infuse_type/" + path);
            }
            
            Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
            if (pigment != null && !pigment.isEmptyType()) {
                return new ResourceLocation("registerhelper", "pigment/" + path);
            }
            
            Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
            if (slurry != null && !slurry.isEmptyType()) {
                return new ResourceLocation("registerhelper", "slurry/" + path);
            }
        } catch (Exception e) {
        }
        
        return GAS_TEXTURE;
    }
    
    private ResourceLocation getFallbackTexture(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return null;
        }
        
        try {
            ResourceLocation location = new ResourceLocation(chemicalId);
            
            InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
            if (infuseType != null && !infuseType.isEmptyType()) {
                return INFUSE_TYPE_BASE;
            }
            
            Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
            if (pigment != null && !pigment.isEmptyType()) {
                return PIGMENT_BASE;
            }
            
            Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
            if (slurry != null && !slurry.isEmptyType()) {
                return SLURRY_BASE;
            }
        } catch (Exception e) {
        }
        
        return null;
    }
    
    private String formatAmount(long amount) {
        if (amount >= 1000000) {
            return String.format("%.1fB", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        } else {
            return String.valueOf(amount);
        }
    }
    
    private void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, String chemicalId, long amount, long maxAmount) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        
        String typeName = component.getChemicalType().getDisplayName();
        
        if (chemicalId == null || chemicalId.isEmpty() || amount <= 0) {
            tooltip.add(Component.literal("§7" + typeName + ": 空"));
            tooltip.add(Component.literal("§e点击选择" + typeName));
        } else {
            String chemicalName = getChemicalDisplayName(chemicalId);
            tooltip.add(Component.literal("§6" + chemicalName));
            tooltip.add(Component.literal("§7" + chemicalId));
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§f" + typeName + "量: §e" + formatAmountDetailed(amount)));
            tooltip.add(Component.literal("§f最大容量: §e" + formatAmountDetailed(maxAmount)));
            tooltip.add(Component.literal("§f填充度: §b" + String.format("%.1f%%", (double) amount / maxAmount * 100)));
        }
        
        java.util.List<net.minecraft.util.FormattedCharSequence> formattedTooltip = new java.util.ArrayList<>();
        for (Component comp : tooltip) {
            formattedTooltip.add(comp.getVisualOrderText());
        }
        guiGraphics.renderTooltip(font, formattedTooltip, mouseX, mouseY);
    }
    
    private String getChemicalDisplayName(String chemicalId) {
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
    
    private String formatAmountDetailed(long amount) {
        if (amount >= 1000000) {
            return String.format("%,.0f mB", (double) amount);
        } else if (amount >= 1000) {
            return String.format("%,d mB", amount);
        } else {
            return String.format("%,d mB", amount);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        
        Rectangle bounds = getBounds();
        if (bounds.contains((int)mouseX, (int)mouseY)) {
            if (button == 0 && onClick != null) {
                onClick.accept(component);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    
    @Override
    public Rectangle getBounds() {
        return component.getBounds();
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
