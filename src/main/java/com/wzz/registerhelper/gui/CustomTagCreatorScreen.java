package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric_fork.gui.JEISelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 自定义标签创建界面（支持翻页，防止重复添加）
 */
@OnlyIn(Dist.CLIENT)
public class CustomTagCreatorScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 300;
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int SLOTS_PER_PAGE = 27; // 每页3行

    public enum Mode {
        ITEM("物品模式"),
        FLUID("流体模式");
        
        private final String displayName;
        
        Mode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    public static class TagCreationResult {
        public final Mode mode;
        public final ResourceLocation tagId;
        public final List<ItemStack> items;
        public final List<ResourceLocation> fluidIds;
        
        public TagCreationResult(Mode mode, ResourceLocation tagId, List<ItemStack> items, List<ResourceLocation> fluidIds) {
            this.mode = mode;
            this.tagId = tagId;
            this.items = items;
            this.fluidIds = fluidIds;
        }
        
        public static TagCreationResult forItems(ResourceLocation tagId, List<ItemStack> items) {
            return new TagCreationResult(Mode.ITEM, tagId, items, Collections.emptyList());
        }
        
        public static TagCreationResult forFluids(ResourceLocation tagId, List<ResourceLocation> fluidIds) {
            return new TagCreationResult(Mode.FLUID, tagId, Collections.emptyList(), fluidIds);
        }
    }

    private final Screen parentScreen;
    private final Consumer<TagCreationResult> onTagCreated;

    private EditBox namespaceBox;
    private EditBox pathBox;
    private Button modeButton;
    private Button addItemButton;
    private Button clearAllButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button createButton;
    private Button cancelButton;

    private Mode currentMode = Mode.ITEM;
    private final LinkedHashSet<Item> tagItems = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> tagFluids = new LinkedHashSet<>();
    private final List<Item> displayList = new ArrayList<>();
    private final List<ResourceLocation> fluidDisplayList = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;

    private int leftPos, topPos;
    private boolean isCreating = false;

    private String savedNamespace = "custom";
    private String savedPath = "";

    public CustomTagCreatorScreen(Screen parentScreen, Consumer<TagCreationResult> onTagCreated) {
        super(Component.literal("创建自定义标签"));
        this.parentScreen = parentScreen;
        this.onTagCreated = onTagCreated;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        namespaceBox = new EditBox(this.font, leftPos + 80, topPos + 30, 100, 20, Component.literal("命名空间"));
        namespaceBox.setHint(Component.literal("mymod"));
        namespaceBox.setValue(savedNamespace);
        namespaceBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        addRenderableWidget(namespaceBox);

        pathBox = new EditBox(this.font, leftPos + 80, topPos + 55, 180, 20, Component.literal("路径"));
        pathBox.setHint(Component.literal("my_materials"));
        pathBox.setValue(savedPath);
        pathBox.setFilter(text -> text.matches("[a-z0-9_/]*"));
        addRenderableWidget(pathBox);

        modeButton = addRenderableWidget(Button.builder(
                        Component.literal(currentMode.getDisplayName()),
                        button -> toggleMode())
                .bounds(leftPos + 10, topPos + 85, 80, 20)
                .build());

        String buttonText = currentMode == Mode.ITEM ? "添加物品" : "从JEI选择流体";
        addItemButton = addRenderableWidget(Button.builder(
                        Component.literal(buttonText),
                        button -> openSelector())
                .bounds(leftPos + 95, topPos + 85, 100, 20)
                .build());

        clearAllButton = addRenderableWidget(Button.builder(
                        Component.literal("清空"),
                        button -> clearAllItems())
                .bounds(leftPos + 200, topPos + 85, 50, 20)
                .build());

        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 10, topPos + 220, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + 35, topPos + 220, 20, 20)
                .build());

        createButton = addRenderableWidget(Button.builder(
                        Component.literal("创建标签"),
                        button -> createTag())
                .bounds(leftPos + GUI_WIDTH - 180, topPos + GUI_HEIGHT - 30, 80, 20)
                .build());

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + GUI_WIDTH - 90, topPos + GUI_HEIGHT - 30, 80, 20)
                .build());

        updateButtons();
    }

    @Override
    public void tick() {
        if (namespaceBox != null) {
            namespaceBox.tick();
        }
        if (pathBox != null) {
            pathBox.tick();
        }
    }

    private void updateButtons() {
        if (currentMode == Mode.ITEM) {
            displayList.clear();
            displayList.addAll(tagItems);
            maxPage = Math.max(0, (displayList.size() - 1) / SLOTS_PER_PAGE);
        } else {
            fluidDisplayList.clear();
            fluidDisplayList.addAll(tagFluids);
            maxPage = Math.max(0, (fluidDisplayList.size() - 1) / SLOTS_PER_PAGE);
        }
        
        currentPage = Math.min(currentPage, maxPage);

        if (prevPageButton != null) {
            prevPageButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < maxPage;
        }
    }

    private void toggleMode() {
        currentMode = currentMode == Mode.ITEM ? Mode.FLUID : Mode.ITEM;
        currentPage = 0;
        
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(currentMode.getDisplayName()));
        }
        if (addItemButton != null) {
            String buttonText = currentMode == Mode.ITEM ? "添加物品" : "从JEI选择流体";
            addItemButton.setMessage(Component.literal(buttonText));
        }
        
        updateButtons();
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateButtons();
        }
    }

    private void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updateButtons();
        }
    }

    private void openSelector() {
        if (minecraft == null) return;
        
        savedNamespace = namespaceBox.getValue();
        savedPath = pathBox.getValue();
        
        if (currentMode == Mode.ITEM) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                if (!item.isEmpty()) {
                    Item itemType = item.getItem();

                    if (tagItems.contains(itemType)) {
                        displayMessage("§e该物品已在标签中，无法重复添加");
                        return;
                    }

                    tagItems.add(itemType);
                    updateButtons();
                    displayMessage("§a已添加: " + ForgeRegistries.ITEMS.getKey(itemType));
                }
            }));
        } else {
            minecraft.setScreen(new JEISelectionScreen(this, dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotType.FLUID, result -> {
                if (result != null && result.type == dev.whisperlyric_fork.gui.SlotSelectionScreen.SlotSelectionResult.SelectionType.FLUID_SELECTED) {
                    if (result.value instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
                        
                        if (fluidId == null) {
                            displayMessage("§c无法获取流体ID");
                            return;
                        }
                        
                        if (tagFluids.contains(fluidId)) {
                            displayMessage("§e该流体已在标签中，无法重复添加");
                            return;
                        }
                        
                        tagFluids.add(fluidId);
                        updateButtons();
                        displayMessage("§a已添加: " + fluidId);
                    }
                }
            }));
        }
    }

    private void clearAllItems() {
        if (currentMode == Mode.ITEM) {
            tagItems.clear();
        } else {
            tagFluids.clear();
        }
        currentPage = 0;
        updateButtons();
    }

    private void createTag() {
        if (isCreating) {
            return;
        }

        savedNamespace = namespaceBox.getValue();
        savedPath = pathBox.getValue();

        String namespace = savedNamespace.trim();
        String path = savedPath.trim();

        if (namespace.isEmpty() || path.isEmpty()) {
            displayMessage("§c请输入标签ID的命名空间和路径");
            return;
        }

        boolean isEmpty = currentMode == Mode.ITEM ? tagItems.isEmpty() : tagFluids.isEmpty();
        if (isEmpty) {
            String type = currentMode == Mode.ITEM ? "物品" : "流体";
            displayMessage("§c请至少添加一个" + type + "到标签中");
            return;
        }

        isCreating = true;

        try {
            ResourceLocation tagId = new ResourceLocation(namespace, path);

            if (onTagCreated != null) {
                if (currentMode == Mode.ITEM) {
                    List<ItemStack> stackList = new ArrayList<>();
                    for (Item item : tagItems) {
                        stackList.add(new ItemStack(item));
                    }
                    onTagCreated.accept(TagCreationResult.forItems(tagId, stackList));
                } else {
                    List<ResourceLocation> fluidIdList = new ArrayList<>(tagFluids);
                    onTagCreated.accept(TagCreationResult.forFluids(tagId, fluidIdList));
                }
            }

            if (minecraft != null && minecraft.player != null) {
                String type = currentMode == Mode.ITEM ? "物品" : "流体";
                int count = currentMode == Mode.ITEM ? tagItems.size() : tagFluids.size();
                minecraft.player.sendSystemMessage(
                        Component.literal("§a自定义" + type + "标签已创建: #" + tagId + " (包含 " + count + " 个" + type + ")")
                );
                minecraft.player.sendSystemMessage(
                        Component.literal("§a使用/reload指令加载标签！")
                );
            }

            if (minecraft != null) {
                minecraft.execute(() -> {
                    minecraft.setScreen(parentScreen);
                });
            }

        } catch (Exception e) {
            isCreating = false;
            displayMessage("§c创建标签失败: " + e.getMessage());
        }
    }

    private void displayMessage(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);

        // 标签
        guiGraphics.drawString(this.font, "命名空间:", leftPos + 10, topPos + 35, 0x404040, false);
        guiGraphics.drawString(this.font, "路径:", leftPos + 10, topPos + 60, 0x404040, false);

        String previewId = "#" + namespaceBox.getValue() + ":" + pathBox.getValue();
        guiGraphics.drawString(this.font, "预览: " + previewId, leftPos + 10, topPos + 110, 0x666666, false);

        String type = currentMode == Mode.ITEM ? "物品" : "流体";
        int count = currentMode == Mode.ITEM ? displayList.size() : fluidDisplayList.size();
        String hint = String.format("§7%s列表 (共%d个，已去重) - 第%d/%d页",
                type, count, currentPage + 1, maxPage + 1);
        guiGraphics.drawString(this.font, hint, leftPos + 10, topPos + 125, 0x666666, false);

        // 渲染物品槽位
        renderItemSlots(guiGraphics, mouseX, mouseY);

        namespaceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        pathBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderItemSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;
        int endIndex = currentMode == Mode.ITEM ? 
            Math.min(startIndex + SLOTS_PER_PAGE, displayList.size()) :
            Math.min(startIndex + SLOTS_PER_PAGE, fluidDisplayList.size());

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY, borderColor);
            guiGraphics.fill(slotX - 1, slotY + SLOT_SIZE, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, borderColor);
            guiGraphics.fill(slotX - 1, slotY, slotX, slotY + SLOT_SIZE, borderColor);
            guiGraphics.fill(slotX + SLOT_SIZE, slotY, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE, borderColor);

            int itemIndex = startIndex + i;
            if (itemIndex < endIndex) {
                if (currentMode == Mode.ITEM) {
                    Item item = displayList.get(itemIndex);
                    ItemStack stack = new ItemStack(item);
                    RenderSystem.enableDepthTest();
                    guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                    RenderSystem.disableDepthTest();
                } else {
                    ResourceLocation fluidId = fluidDisplayList.get(itemIndex);
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                    if (fluid != null) {
                        renderFluid(guiGraphics, fluid, slotX + 1, slotY + 1, 16, 16);
                    }
                }
            }
        }
    }

    private void renderFluid(GuiGraphics guiGraphics, Fluid fluid, int x, int y, int width, int height) {
        if (fluid == null) return;
        
        FluidStack fluidStack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        if (stillTexture == null) return;
        
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
        
        int tintColor = fluidExtensions.getTintColor(fluidStack);
        float r = ((tintColor >> 16) & 0xFF) / 255.0f;
        float g = ((tintColor >> 8) & 0xFF) / 255.0f;
        float b = (tintColor & 0xFF) / 255.0f;
        
        RenderSystem.setShaderColor(r, g, b, 1.0f);
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        
        guiGraphics.blit(x, y, 0, width, height, sprite);
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                int itemIndex = startIndex + i;
                boolean hasItem = currentMode == Mode.ITEM ? 
                    itemIndex < displayList.size() : 
                    itemIndex < fluidDisplayList.size();
                    
                if (hasItem) {
                    List<Component> tooltip = new ArrayList<>();
                    
                    if (currentMode == Mode.ITEM) {
                        Item item = displayList.get(itemIndex);
                        ItemStack stack = new ItemStack(item);
                        tooltip.add(stack.getHoverName());
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                        tooltip.add(Component.literal("§7" + itemId));
                    } else {
                        ResourceLocation fluidId = fluidDisplayList.get(itemIndex);
                        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                        if (fluid != null) {
                            FluidStack fluidStack = new FluidStack(fluid, 1000);
                            tooltip.add(fluidStack.getDisplayName());
                        }
                        tooltip.add(Component.literal("§7" + fluidId));
                    }
                    
                    tooltip.add(Component.literal("§8右键删除"));
                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                int itemIndex = startIndex + i;
                boolean hasItem = currentMode == Mode.ITEM ? 
                    itemIndex < displayList.size() : 
                    itemIndex < fluidDisplayList.size();
                    
                if (hasItem && button == 1) {
                    if (currentMode == Mode.ITEM) {
                        Item item = displayList.get(itemIndex);
                        tagItems.remove(item);
                        displayMessage("§c已移除: " + ForgeRegistries.ITEMS.getKey(item));
                    } else {
                        ResourceLocation fluidId = fluidDisplayList.get(itemIndex);
                        tagFluids.remove(fluidId);
                        displayMessage("§c已移除: " + fluidId);
                    }
                    updateButtons();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}