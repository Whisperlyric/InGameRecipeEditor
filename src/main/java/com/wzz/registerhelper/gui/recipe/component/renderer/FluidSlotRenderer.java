package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import dev.whisperlyric_fork.gui.FluidSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.awt.*;
import java.util.function.Consumer;

public class FluidSlotRenderer implements ComponentRenderer {
    private final FluidSlotComponent component;
    private Consumer<FluidSlotComponent> onClick;
    private boolean active = true;
    private boolean isOutput = false;
    
    private static final ResourceLocation ELEMENT_HOLDER = new ResourceLocation("registerhelper", "textures/gui/element_holder.png");
    private static final ResourceLocation GAUGE_STANDARD = new ResourceLocation("registerhelper", "textures/gui/gauge/standard.png");
    
    public FluidSlotRenderer(FluidSlotComponent component, Consumer<FluidSlotComponent> onClick) {
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
        
        String fluidId = component.getFluidId();
        long amount = component.getAmount();
        long maxAmount = component.getMaxAmount();
        
        if (fluidId != null && !fluidId.isEmpty() && amount > 0) {
            try {
                ResourceLocation fluidLoc = ResourceLocation.tryParse(fluidId);
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidLoc);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    FluidStack fluidStack = new FluidStack(fluid, (int)amount);
                    
                    double fillRatio = (double) amount / maxAmount;
                    int fillHeight = (int) (fillRatio * height);
                    
                    if (fillHeight > 0) {
                        renderFluidWithFlow(guiGraphics, fluidStack, x, y + height - fillHeight, width, fillHeight);
                    }
                    
                    String amountText = formatAmount(amount);
                    int textWidth = font.width(amountText);
                    if (textWidth < width - 4) {
                        guiGraphics.drawString(font, amountText, x + width / 2 - textWidth / 2, y + height / 2 - 4, 0xFFFFFF, true);
                    }
                }
            } catch (Exception e) {
                guiGraphics.drawString(font, "?", x + width / 2 - 3, y + height / 2 - 4, 0xFFFFFF);
            }
        }
    }
    
    public void renderTooltip(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        
        int x = component.getX();
        int y = component.getY();
        int width = 16;
        int height = 58;
        
        boolean isMouseOver = mouseX >= x && mouseX < x + width &&
                             mouseY >= y && mouseY < y + height;
        
        if (isMouseOver) {
            String fluidId = component.getFluidId();
            long amount = component.getAmount();
            long maxAmount = component.getMaxAmount();
            renderTooltipContent(guiGraphics, font, mouseX, mouseY, fluidId, amount, maxAmount);
        }
    }
    
    private void renderTooltipContent(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, String fluidId, long amount, long maxAmount) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        
        if (fluidId == null || fluidId.isEmpty() || amount <= 0) {
            tooltip.add(Component.literal("§7流体: 空"));
            tooltip.add(Component.literal("§e点击选择流体"));
        } else {
            try {
                ResourceLocation fluidLoc = new ResourceLocation(fluidId);
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidLoc);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    String fluidName = fluid.defaultFluidState().createLegacyBlock().getBlock().getName().getString();
                    tooltip.add(Component.literal("§6" + fluidName));
                    tooltip.add(Component.literal("§7" + fluidId));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§f存储量: §e" + formatAmountDetailed(amount)));
                    tooltip.add(Component.literal("§f最大容量: §e" + formatAmountDetailed(maxAmount)));
                    tooltip.add(Component.literal("§f填充度: §b" + String.format("%.1f%%", (double) amount / maxAmount * 100)));
                }
            } catch (Exception e) {
                tooltip.add(Component.literal("§c错误: " + fluidId));
            }
        }
        
        java.util.List<net.minecraft.util.FormattedCharSequence> formattedTooltip = new java.util.ArrayList<>();
        for (Component comp : tooltip) {
            formattedTooltip.add(comp.getVisualOrderText());
        }
        guiGraphics.renderTooltip(font, formattedTooltip, mouseX, mouseY);
    }
    
    private void renderFluidWithFlow(GuiGraphics guiGraphics, FluidStack fluidStack, int x, int y, int width, int height) {
        Fluid fluid = fluidStack.getFluid();
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
                sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
                // 检查是否为缺失纹理
                if (sprite != null && sprite.atlasLocation().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                    sprite = null;
                }
            } catch (Exception e) {
                // 纹理加载失败
                sprite = null;
            }
        }
        
        if (sprite != null) {
            // 正常渲染流体纹理（JEI方式）
            RenderSystem.setShaderTexture(0, net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
            
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
            
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertexBuffer.end());
            RenderSystem.disableBlend();
        } else {
            // 当纹理不可用时，使用颜色填充
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            guiGraphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
        }
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    private int getFluidColor(Fluid fluid) {
        ResourceLocation fluidLoc = fluid.builtInRegistryHolder().key().location();
        
        if (fluidLoc.equals(new ResourceLocation("minecraft", "water"))) {
            return 0x3F76E4;
        } else if (fluidLoc.equals(new ResourceLocation("minecraft", "lava"))) {
            return 0xFF4500;
        } else if (fluidLoc.getNamespace().equals("mekanism")) {
            String path = fluidLoc.getPath();
            if (path.contains("sulfuric")) return 0xFFDD00;
            if (path.contains("hydrogen")) return 0x00DDFF;
            if (path.contains("oxygen")) return 0x88CCFF;
            if (path.contains("chlorine")) return 0xAAFF00;
            if (path.contains("heavy")) return 0x0088FF;
        }
        
        try {
            int color = fluid.defaultFluidState().createLegacyBlock().getBlock().defaultBlockState().getFluidState().getType().defaultFluidState().createLegacyBlock().getBlock().defaultBlockState().getFluidState().getType().builtInRegistryHolder().key().location().hashCode();
            return 0xFF000000 | (color & 0x00FFFFFF);
        } catch (Exception e) {
            return 0xFFFFFF;
        }
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
    
    private String formatAmountDetailed(long amount) {
        if (amount >= 1000000) {
            return String.format("%,.0f mB (%.1f B)", (double) amount, amount / 1000.0);
        } else if (amount >= 1000) {
            return String.format("%,d mB (%.1f B)", amount, amount / 1000.0);
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
