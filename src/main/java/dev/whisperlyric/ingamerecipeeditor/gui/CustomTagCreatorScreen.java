package dev.whisperlyric.ingamerecipeeditor.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whisperlyric.ingamerecipeeditor.tags.CustomTagManager;
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
import net.minecraft.tags.TagKey;
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
import java.util.stream.Collectors;

/**
 * 自定义标签创建界面
 */
@OnlyIn(Dist.CLIENT)
public class CustomTagCreatorScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 300;
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int SLOTS_PER_PAGE = 27;

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
    private Button replaceButton;
    private Button displayModeButton;

    private Mode currentMode = Mode.ITEM;
    private final LinkedHashSet<Item> tagItems = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> tagFluids = new LinkedHashSet<>();
    private final LinkedHashSet<Item> originalItems = new LinkedHashSet<>(); // 原有标签包含的物品
    private final LinkedHashSet<ResourceLocation> originalFluids = new LinkedHashSet<>(); // 原有标签包含的流体
    private final LinkedHashSet<ResourceLocation> removedItems = new LinkedHashSet<>();
    private final LinkedHashSet<ResourceLocation> removedFluids = new LinkedHashSet<>();
    private final List<Item> displayList = new ArrayList<>();
    private final List<ResourceLocation> fluidDisplayList = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;

    private int leftPos, topPos;
    private boolean isCreating = false;

    private String savedNamespace = "custom";
    private String savedPath = "";
    
    // 编辑模式
    private boolean isEditMode = false;
    private boolean isReplaceMode = false;
    private DisplayMode displayMode = DisplayMode.ALL;
    private ResourceLocation editingTagId;
    private boolean isLocked = false;
    private boolean isOriginalTag = false;
    
    public enum DisplayMode {
        ALL("显示所有"),
        ADDED("已添加"),
        REMOVED("已移除");
        
        private final String displayName;
        
        DisplayMode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    public CustomTagCreatorScreen(Screen parentScreen, Consumer<TagCreationResult> onTagCreated) {
        super(Component.literal("创建自定义标签"));
        this.parentScreen = parentScreen;
        this.onTagCreated = onTagCreated;
    }
    
    /**
     * 编辑模式构造函数
     */
    public CustomTagCreatorScreen(Screen parentScreen, Consumer<TagCreationResult> onTagCreated, 
                                  ResourceLocation tagId, Mode mode, boolean isOriginalTag) {
        super(Component.literal(isOriginalTag ? "编辑标签" : "编辑自定义标签"));
        this.parentScreen = parentScreen;
        this.onTagCreated = onTagCreated;
        this.isEditMode = true;
        this.editingTagId = tagId;
        this.currentMode = mode;
        this.isLocked = true; // 锁定命名空间、路径和模式
        this.savedNamespace = tagId.getNamespace();
        this.savedPath = tagId.getPath();
        this.isOriginalTag = isOriginalTag;
        
        if (isOriginalTag) {
            loadOriginalTagData(tagId, mode);
        } else {
            loadCustomTagData(tagId, mode);
        }
    }
    
    /**
     * 加载自定义标签数据（从文件）
     */
    private void loadCustomTagData(ResourceLocation tagId, Mode mode) {
        String tagType = mode == Mode.ITEM ? "items" : "fluids";
        CustomTagManager.TagFileInfo info = CustomTagManager.readTagFileInfo(tagId, tagType);
        
        if (info != null) {
            this.isReplaceMode = info.replace;
            
            if (mode == Mode.ITEM) {
                for (String itemId : info.values) {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
                    if (item != null) {
                        tagItems.add(item);
                    }
                }
                for (String removedId : info.removedValues) {
                    removedItems.add(ResourceLocation.parse(removedId));
                }
            } else {
                for (String fluidId : info.values) {
                    tagFluids.add(ResourceLocation.parse(fluidId));
                }
                for (String removedId : info.removedValues) {
                    removedFluids.add(ResourceLocation.parse(removedId));
                }
            }
        }
    }
    
    /**
     * 加载原有模组标签数据（从游戏注册表）
     */
    @SuppressWarnings("null")
    private void loadOriginalTagData(ResourceLocation tagId, Mode mode) {
        if (mode == Mode.ITEM) {
            // 从物品注册表加载标签包含的所有物品，保存到 originalItems
            TagKey<Item> tag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item.builtInRegistryHolder().is(tag)) {
                    originalItems.add(item);
                }
            }
        } else {
            // 从流体注册表加载标签包含的所有流体，保存到 originalFluids
            TagKey<Fluid> tag = TagKey.create(ForgeRegistries.FLUIDS.getRegistryKey(), tagId);
            for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
                if (fluid.builtInRegistryHolder().is(tag)) {
                    ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                    if (fluidId != null) {
                        originalFluids.add(fluidId);
                    }
                }
            }
        }
        
        // 检查是否已有自定义标签文件覆盖此标签
        String tagType = mode == Mode.ITEM ? "items" : "fluids";
        CustomTagManager.TagFileInfo info = CustomTagManager.readTagFileInfo(tagId, tagType);
        if (info != null) {
            this.isReplaceMode = info.replace;
            
            // 如果有自定义文件，加载其中的添加和移除列表
            if (mode == Mode.ITEM) {
                for (String itemId : info.values) {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
                    if (item != null) {
                        tagItems.add(item);
                    }
                }
                for (String removedId : info.removedValues) {
                    removedItems.add(ResourceLocation.parse(removedId));
                }
            } else {
                for (String fluidId : info.values) {
                    tagFluids.add(ResourceLocation.parse(fluidId));
                }
                for (String removedId : info.removedValues) {
                    removedFluids.add(ResourceLocation.parse(removedId));
                }
            }
        }
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        namespaceBox = new EditBox(this.font, leftPos + 80, topPos + 30, 100, 20, Component.literal("命名空间"));
        namespaceBox.setHint(Component.literal("mymod"));
        namespaceBox.setValue(savedNamespace);
        namespaceBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        namespaceBox.setEditable(!isLocked);
        addRenderableWidget(namespaceBox);

        pathBox = new EditBox(this.font, leftPos + 80, topPos + 55, 180, 20, Component.literal("路径"));
        pathBox.setHint(Component.literal("my_materials"));
        pathBox.setValue(savedPath);
        pathBox.setFilter(text -> text.matches("[a-z0-9_/]*"));
        pathBox.setEditable(!isLocked);
        addRenderableWidget(pathBox);

        modeButton = addRenderableWidget(Button.builder(
                        Component.literal(currentMode.getDisplayName()),
                        button -> toggleMode())
                .bounds(leftPos + 10, topPos + 85, 80, 20)
                .build());
        modeButton.active = !isLocked;

        String buttonText = currentMode == Mode.ITEM ? "添加物品" : "添加流体";
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
        
        // 覆盖模式
        if (isEditMode) {
            replaceButton = addRenderableWidget(Button.builder(
                            Component.literal(isReplaceMode ? "覆盖模式: 开" : "覆盖模式: 关"),
                            button -> toggleReplaceMode())
                    .bounds(leftPos + 10, topPos + 110, 100, 20)
                    .build());
            
            // 显示模式按钮
            if (isOriginalTag) {
                displayModeButton = addRenderableWidget(Button.builder(
                                Component.literal(displayMode.getDisplayName()),
                                button -> toggleDisplayMode())
                        .bounds(leftPos + 115, topPos + 110, 100, 20)
                        .build());
                displayModeButton.active = !isReplaceMode;
            }
        }

        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 10, topPos + 235, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + 35, topPos + 235, 20, 20)
                .build());

        String createButtonText = isEditMode ? "保存更改" : "创建标签";
        createButton = addRenderableWidget(Button.builder(
                        Component.literal(createButtonText),
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
        if (namespaceBox != null) namespaceBox.tick();
        if (pathBox != null) pathBox.tick();
    }

    private void updateButtons() {
        if (currentMode == Mode.ITEM) {
            displayList.clear();
            if (isEditMode && isOriginalTag) {
                if (displayMode == DisplayMode.ALL) {
                    for (Item item : originalItems) {
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                        if (itemId != null && !removedItems.contains(itemId)) {
                            displayList.add(item);
                        }
                    }
                    for (Item addedItem : tagItems) {
                        if (!displayList.contains(addedItem)) {
                            displayList.add(addedItem);
                        }
                    }
                } else if (displayMode == DisplayMode.ADDED) {
                    // 显示已添加：用户添加的对象
                    displayList.addAll(tagItems);
                } else if (displayMode == DisplayMode.REMOVED) {
                    // 显示已移除：用户移除的对象
                    for (ResourceLocation removedId : removedItems) {
                        Item item = ForgeRegistries.ITEMS.getValue(removedId);
                        if (item != null) {
                            displayList.add(item);
                        }
                    }
                }
            } else if (isEditMode && !isOriginalTag) {
                // 自定义标签的显示逻辑（不可切换显示模式，直接显示所有）
                displayList.addAll(tagItems);
            } else {
                // 创建模式：显示所有添加的对象
                displayList.addAll(tagItems);
            }
            maxPage = Math.max(0, (displayList.size() - 1) / SLOTS_PER_PAGE);
        } else {
            fluidDisplayList.clear();
            if (isEditMode && isOriginalTag) {
                if (displayMode == DisplayMode.ALL) {
                    for (ResourceLocation fluidId : originalFluids) {
                        if (!removedFluids.contains(fluidId)) {
                            fluidDisplayList.add(fluidId);
                        }
                    }
                    for (ResourceLocation addedFluidId : tagFluids) {
                        if (!fluidDisplayList.contains(addedFluidId)) {
                            fluidDisplayList.add(addedFluidId);
                        }
                    }
                } else if (displayMode == DisplayMode.ADDED) {
                    // 显示已添加：用户添加的对象
                    fluidDisplayList.addAll(tagFluids);
                } else if (displayMode == DisplayMode.REMOVED) {
                    // 显示已移除：用户移除的对象
                    fluidDisplayList.addAll(removedFluids);
                }
            } else if (isEditMode && !isOriginalTag) {
                // 自定义标签的显示逻辑（不可切换显示模式，直接显示所有）
                fluidDisplayList.addAll(tagFluids);
            } else {
                // 创建模式：显示所有添加的对象
                fluidDisplayList.addAll(tagFluids);
            }
            maxPage = Math.max(0, (fluidDisplayList.size() - 1) / SLOTS_PER_PAGE);
        }
        
        currentPage = Math.min(currentPage, maxPage);

        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
    }
    
    private void toggleReplaceMode() {
        isReplaceMode = !isReplaceMode;
        if (replaceButton != null) {
            replaceButton.setMessage(Component.literal(isReplaceMode ? "覆盖模式: 开" : "覆盖模式: 关"));
        }
        // 覆盖模式下禁用显示模式切换（仅原有标签）
        if (displayModeButton != null) {
            displayModeButton.active = !isReplaceMode;
            if (isReplaceMode) {
                displayMode = DisplayMode.ALL;
                displayModeButton.setMessage(Component.literal(displayMode.getDisplayName()));
                updateButtons();
            }
        }
    }
    
    private void toggleDisplayMode() {
        DisplayMode[] modes = DisplayMode.values();
        int currentIndex = displayMode.ordinal();
        displayMode = modes[(currentIndex + 1) % modes.length];
        if (displayModeButton != null) {
            displayModeButton.setMessage(Component.literal(displayMode.getDisplayName()));
        }
        currentPage = 0;
        updateButtons();
    }

    private void toggleMode() {
        currentMode = currentMode == Mode.ITEM ? Mode.FLUID : Mode.ITEM;
        currentPage = 0;
        
        if (modeButton != null) modeButton.setMessage(Component.literal(currentMode.getDisplayName()));
        if (addItemButton != null) {
            String buttonText = currentMode == Mode.ITEM ? "添加物品" : "添加流体";
            addItemButton.setMessage(Component.literal(buttonText));
        }
        
        updateButtons();
    }

    private void previousPage() {
        if (currentPage > 0) { currentPage--; updateButtons(); }
    }

    private void nextPage() {
        if (currentPage < maxPage) { currentPage++; updateButtons(); }
    }

    private void openSelector() {
        if (minecraft == null) return;
        
        savedNamespace = namespaceBox.getValue();
        savedPath = pathBox.getValue();
        
        if (currentMode == Mode.ITEM) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                if (!item.isEmpty()) {
                    Item itemType = item.getItem();
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemType);
                    
                    if (isEditMode && displayMode == DisplayMode.REMOVED) {
                        if (removedItems.contains(itemId)) {
                            displayMessage("§e该物品已在移除列表中，无法重复添加");
                            return;
                        }
                        removedItems.add(itemId);
                        updateButtons();
                        displayMessage("§a已添加到移除列表: " + itemId);
                    } else {
                        if (tagItems.contains(itemType)) {
                            displayMessage("§e该物品已在标签中，无法重复添加");
                            return;
                        }
                        tagItems.add(itemType);
                        updateButtons();
                        displayMessage("§a已添加: " + itemId);
                    }
                }
            }));
        } else {
            minecraft.setScreen(new FluidSelectorScreen(this, fluidId -> {
                if (fluidId != null) {
                    if (isEditMode && displayMode == DisplayMode.REMOVED) {
                        if (removedFluids.contains(fluidId)) {
                            displayMessage("§e该流体已在移除列表中，无法重复添加");
                            return;
                        }
                        removedFluids.add(fluidId);
                        updateButtons();
                        displayMessage("§a已添加到移除列表: " + fluidId);
                    } else {
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
        if (isEditMode) {
            if (displayMode == DisplayMode.REMOVED) {
                if (currentMode == Mode.ITEM) removedItems.clear();
                else removedFluids.clear();
            } else {
                if (currentMode == Mode.ITEM) tagItems.clear();
                else tagFluids.clear();
            }
        } else {
            if (currentMode == Mode.ITEM) tagItems.clear();
            else tagFluids.clear();
        }
        currentPage = 0;
        updateButtons();
    }

    private void createTag() {
        if (isCreating) return;

        savedNamespace = namespaceBox.getValue();
        savedPath = pathBox.getValue();

        String namespace = savedNamespace.trim();
        String path = savedPath.trim();

        if (namespace.isEmpty() || path.isEmpty()) {
            displayMessage("§c请输入标签ID的命名空间和路径");
            return;
        }

        boolean isEmpty = currentMode == Mode.ITEM ? tagItems.isEmpty() : tagFluids.isEmpty();
        if (isEmpty && !isEditMode) {
            String type = currentMode == Mode.ITEM ? "物品" : "流体";
            displayMessage("§c请至少添加一个" + type + "到标签中");
            return;
        }

        isCreating = true;

        try {
            ResourceLocation tagId = isEditMode ? editingTagId : ResourceLocation.parse(namespace + ":" + path);

            if (isEditMode) {
                // 编辑模式：保存或删除标签文件
                if (currentMode == Mode.ITEM) {
                    List<String> itemIds = new ArrayList<>();
                    for (Item item : tagItems) {
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                        if (itemId != null) {
                            itemIds.add(itemId.toString());
                        }
                    }
                    List<String> removedIds = new ArrayList<>(removedItems.stream()
                            .map(ResourceLocation::toString)
                            .collect(Collectors.toList()));
                    
                    // 检查是否应该删除空文件
                    if (!isReplaceMode && itemIds.isEmpty() && removedIds.isEmpty()) {
                        // replace=false 且内容为空，删除文件
                        CustomTagManager.removeTag(tagId);
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.literal("§a已移除空的标签文件: #" + tagId));
                            minecraft.player.sendSystemMessage(Component.literal("§a使用/reload指令恢复原有标签！"));
                        }
                    } else {
                        // 保存标签文件
                        CustomTagManager.saveTagFileInfo(tagId, "items", isReplaceMode, itemIds, removedIds);
                        if (minecraft != null && minecraft.player != null) {
                            int count = itemIds.size();
                            int removedCount = removedIds.size();
                            minecraft.player.sendSystemMessage(
                                    Component.literal("§a物品标签已更新: #" + tagId + 
                                            " (包含 " + count + " 个物品" + 
                                            (removedCount > 0 ? ", 移除 " + removedCount + " 个物品" : "") + ")")
                            );
                            minecraft.player.sendSystemMessage(Component.literal("§a使用/reload指令加载标签！"));
                        }
                    }
                } else {
                    List<String> fluidIds = new ArrayList<>(tagFluids.stream()
                            .map(ResourceLocation::toString)
                            .collect(Collectors.toList()));
                    List<String> removedIds = new ArrayList<>(removedFluids.stream()
                            .map(ResourceLocation::toString)
                            .collect(Collectors.toList()));
                    
                    // 检查是否应该删除空文件
                    if (!isReplaceMode && fluidIds.isEmpty() && removedIds.isEmpty()) {
                        // replace=false 且内容为空，删除文件
                        CustomTagManager.removeTag(tagId);
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.literal("§a已移除空的标签文件: #" + tagId));
                            minecraft.player.sendSystemMessage(Component.literal("§a使用/reload指令恢复原有标签！"));
                        }
                    } else {
                        // 保存标签文件
                        CustomTagManager.saveTagFileInfo(tagId, "fluids", isReplaceMode, fluidIds, removedIds);
                        if (minecraft != null && minecraft.player != null) {
                            int count = fluidIds.size();
                            int removedCount = removedIds.size();
                            minecraft.player.sendSystemMessage(
                                    Component.literal("§a流体标签已更新: #" + tagId + 
                                            " (包含 " + count + " 个流体" + 
                                            (removedCount > 0 ? ", 移除 " + removedCount + " 个流体" : "") + ")")
                            );
                            minecraft.player.sendSystemMessage(Component.literal("§a使用/reload指令加载标签！"));
                        }
                    }
                }
            } else {
                if (currentMode == Mode.ITEM) {
                    List<ItemStack> stackList = new ArrayList<>();
                    for (Item item : tagItems) stackList.add(new ItemStack(item));
                    CustomTagManager.registerTag(tagId, stackList);
                } else {
                    List<ResourceLocation> fluidIdList = new ArrayList<>(tagFluids);
                    CustomTagManager.registerFluidTag(tagId, fluidIdList);
                }

                if (onTagCreated != null) {
                    if (currentMode == Mode.ITEM) {
                        List<ItemStack> stackList = new ArrayList<>();
                        for (Item item : tagItems) stackList.add(new ItemStack(item));
                        onTagCreated.accept(TagCreationResult.forItems(tagId, stackList));
                    } else {
                        onTagCreated.accept(TagCreationResult.forFluids(tagId, new ArrayList<>(tagFluids)));
                    }
                }

                if (minecraft != null && minecraft.player != null) {
                    String type = currentMode == Mode.ITEM ? "物品" : "流体";
                    int count = currentMode == Mode.ITEM ? tagItems.size() : tagFluids.size();
                    minecraft.player.sendSystemMessage(
                            Component.literal("§a自定义" + type + "标签已创建: #" + tagId + " (包含 " + count + " 个" + type + ")")
                    );
                    minecraft.player.sendSystemMessage(Component.literal("§a使用/reload指令加载标签！"));
                }
            }

            if (minecraft != null) minecraft.execute(() -> minecraft.setScreen(parentScreen));

        } catch (Exception e) {
            isCreating = false;
            displayMessage("§c" + (isEditMode ? "保存标签失败" : "创建标签失败") + ": " + e.getMessage());
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

        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);

        guiGraphics.drawString(this.font, "命名空间:", leftPos + 10, topPos + 35, 0x404040, false);
        guiGraphics.drawString(this.font, "路径:", leftPos + 10, topPos + 60, 0x404040, false);

        int previewY = isEditMode ? topPos + 135 : topPos + 110;
        String previewId = "#" + namespaceBox.getValue() + ":" + pathBox.getValue();
        guiGraphics.drawString(this.font, "预览: " + previewId, leftPos + 10, previewY, 0x666666, false);

        String type = currentMode == Mode.ITEM ? "物品" : "流体";
        int count = currentMode == Mode.ITEM ? displayList.size() : fluidDisplayList.size();
        String displayModeText = isEditMode ? " (" + displayMode.getDisplayName() + ")" : "";
        String hint = String.format("§7%s列表 (共%d个，已去重)%s - 第%d/%d页",
                type, count, displayModeText, currentPage + 1, maxPage + 1);
        int hintY = isEditMode ? topPos + 150 : topPos + 125;
        guiGraphics.drawString(this.font, hint, leftPos + 10, hintY, 0x666666, false);

        renderItemSlots(guiGraphics, mouseX, mouseY);

        namespaceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        pathBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderItemSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int startY = isEditMode ? topPos + 165 : topPos + 140;

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
                    if (fluid != null) renderFluid(guiGraphics, fluid, slotX + 1, slotY + 1, 16, 16);
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
        int startY = isEditMode ? topPos + 165 : topPos + 140;

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
        if (button == 1) {
            int startX = leftPos + 10;
            int startY = topPos + (isEditMode ? 150 : 140);
            int startIndex = currentPage * SLOTS_PER_PAGE;

            for (int i = 0; i < SLOTS_PER_PAGE; i++) {
                int row = i / SLOTS_PER_ROW;
                int col = i % SLOTS_PER_ROW;

                int slotX = startX + col * SLOT_SIZE;
                int slotY = startY + row * SLOT_SIZE;

                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                        mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                    int itemIndex = startIndex + i;
                    if (currentMode == Mode.ITEM && itemIndex < displayList.size()) {
                        Item removed = displayList.remove(itemIndex);
                        tagItems.remove(removed);
                        displayMessage("§e已移除: " + ForgeRegistries.ITEMS.getKey(removed));
                        updateButtons();
                        return true;
                    } else if (currentMode == Mode.FLUID && itemIndex < fluidDisplayList.size()) {
                        ResourceLocation removed = fluidDisplayList.remove(itemIndex);
                        tagFluids.remove(removed);
                        displayMessage("§e已移除: " + removed);
                        updateButtons();
                        return true;
                    }
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}