package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;

/**
 * 化学物质槽位渲染器 - 用于渲染Mekanism等mod的化学物质槽位（使用纹理渲染）
 */
public class ChemicalSlotRenderer {
    private static final int TEXTURE_SIZE = 16;
    private static final int MIN_CHEMICAL_HEIGHT = 1;

    /**
     * 渲染化学物质槽位（使用纹理渲染）
     */
    public static void renderChemical(GuiGraphics guiGraphics, String chemicalId, long amount, long capacity, 
                                       int x, int y, int width, int height) {
        if (chemicalId == null || chemicalId.isEmpty() || amount <= 0) {
            return;
        }

        try {
            ResourceLocation location = ResourceLocation.tryParse(chemicalId);
            if (location == null) {
                return;
            }

            // 获取化学物质
            Optional<? extends Chemical<?>> chemical = getChemical(location);
            if (chemical.isEmpty() || chemical.get().isEmptyType()) {
                return;
            }

            Chemical<?> chem = chemical.get();
            int color = chem.getTint();
            ResourceLocation icon = chem.getIcon();

            if (icon == null) {
                return;
            }

            // 计算填充高度
            int desiredHeight = (int) (height * (double) amount / capacity);
            if (desiredHeight < MIN_CHEMICAL_HEIGHT) {
                desiredHeight = MIN_CHEMICAL_HEIGHT;
            }
            if (desiredHeight > height) {
                desiredHeight = height;
            }

            // 设置颜色
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            // 获取精灵图
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(icon);
            if (sprite == null) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                return;
            }

            RenderSystem.setShaderTexture(0, sprite.atlasLocation());

            // 渲染平铺精灵
            renderTiledSprite(guiGraphics, x, y, height, width, desiredHeight, sprite);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("化学品渲染异常", e);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
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

        BufferBuilder vertexBuffer = Tesselator.getInstance().getBuilder();
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
     * 获取化学物质
     */
    private static Optional<? extends Chemical<?>> getChemical(ResourceLocation location) {
        Gas gas = MekanismAPI.gasRegistry().getValue(location);
        if (gas != null && !gas.isEmptyType()) {
            return Optional.of(gas);
        }

        InfuseType infuseType = MekanismAPI.infuseTypeRegistry().getValue(location);
        if (infuseType != null && !infuseType.isEmptyType()) {
            return Optional.of(infuseType);
        }

        Pigment pigment = MekanismAPI.pigmentRegistry().getValue(location);
        if (pigment != null && !pigment.isEmptyType()) {
            return Optional.of(pigment);
        }

        Slurry slurry = MekanismAPI.slurryRegistry().getValue(location);
        if (slurry != null && !slurry.isEmptyType()) {
            return Optional.of(slurry);
        }

        return Optional.empty();
    }

    /**
     * 渲染化学物质tooltip
     */
    public static List<Component> getChemicalTooltip(String chemicalId, long amount, long capacity) {
        List<Component> tooltip = new ArrayList<>();
        
        // 第一行：化学物质译名
        String name = getChemicalDisplayName(chemicalId);
        tooltip.add(Component.literal(name));
        
        // 第二行：如果是tag，显示tag信息（支持多个tag），处理包含花括号的情况
        if (chemicalId.startsWith("#")) {
            String tagRaw = chemicalId.substring(1).replaceAll("[{}\\s]", "");
            if (tagRaw.contains(",")) {
                String[] tags = tagRaw.split(",");
                for (String tag : tags) {
                    tooltip.add(Component.literal("tag: " + tag.trim())
                        .withStyle(s -> s.withColor(0x808080)));
                }
            } else if (!tagRaw.isEmpty()) {
                tooltip.add(Component.literal("tag: " + tagRaw)
                    .withStyle(s -> s.withColor(0x808080)));
            }
        }
        
        // 第三行：ID（去掉#前缀）
        String displayId = chemicalId.startsWith("#") ? chemicalId.substring(1) : chemicalId;
        tooltip.add(Component.literal("id: " + displayId).withStyle(style -> style.withColor(0x808080)));
        
        // 第四行：数量（统一格式：数字mB）
        tooltip.add(Component.literal(amount + "mB"));
        
        return tooltip;
    }

    /**
     * 获取化学物质显示名称（public方法，供外部调用）
     */
    public static String getChemicalDisplayName(String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty()) {
            return "空";
        }

        // 处理标签情况（以#开头），支持包含花括号或多个标签
        if (chemicalId.startsWith("#")) {
            String tagRaw = chemicalId.substring(1).replaceAll("[{}\\s]", "");
            if (tagRaw.contains(",")) {
                String[] tags = tagRaw.split(",");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tags.length; i++) {
                    String tag = tags[i].trim();
                    if (i > 0) sb.append(", ");
                    sb.append(formatTagDisplayName(tag));
                }
                return sb.toString();
            }
            return formatTagDisplayName(tagRaw);
        }

        try {
            ResourceLocation location = ResourceLocation.tryParse(chemicalId);
            if (location == null) {
                return chemicalId;
            }

            Optional<? extends Chemical<?>> chemical = getChemical(location);
            if (chemical.isPresent()) {
                Component textComponent = chemical.get().getTextComponent();
                if (textComponent != null) {
                    return textComponent.getString();
                }
            }
        } catch (Exception e) {
        }

        // 格式化ID作为备用名称
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

    /**
     * 格式化标签显示名称
     */
    private static String formatTagDisplayName(String tagId) {
        // 提取标签路径部分（去掉命名空间）
        String path = tagId.contains(":") ? tagId.substring(tagId.indexOf(":") + 1) : tagId;
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
}