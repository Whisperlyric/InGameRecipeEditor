package dev.whisperlyric_fork.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TagGroupSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<SlotSelectionScreen.SlotSelectionResult> callback;
    
    private EditBox searchBox;
    private List<TagKey<Item>> availableTags = new ArrayList<>();
    private List<TagKey<Item>> filteredTags = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int itemsPerPage = 10;
    
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnScrollUp;
    private Button btnScrollDown;
    
    private int listStartX;
    private int listStartY;
    private int itemHeight = 20;
    
    public TagGroupSelectionScreen(Screen parent, Consumer<SlotSelectionScreen.SlotSelectionResult> callback) {
        super(Component.literal("添加物品标签组"));
        this.parent = parent;
        this.callback = callback;
    }
    
    @Override
    protected void init() {
        super.init();
        
        loadTags();
        
        int centerX = this.width / 2;
        int startY = 40;
        
        searchBox = new EditBox(
            Minecraft.getInstance().font,
            centerX - 100,
            startY,
            200,
            20,
            Component.literal("搜索标签")
        );
        searchBox.setResponder(this::filterTags);
        addRenderableWidget(searchBox);
        
        listStartX = centerX - 150;
        listStartY = startY + 30;
        
        btnScrollUp = Button.builder(
            Component.literal("↑"),
            button -> scrollUp()
        ).bounds(listStartX - 25, listStartY + 40, 20, 20).build();
        
        btnScrollDown = Button.builder(
            Component.literal("↓"),
            button -> scrollDown()
        ).bounds(listStartX - 25, listStartY + 80, 20, 20).build();
        
        int buttonY = listStartY + itemsPerPage * itemHeight + 20;
        
        btnConfirm = Button.builder(
            Component.literal("确认选择"),
            button -> confirmSelection()
        ).bounds(centerX - 105, buttonY, 100, 20).build();
        
        btnCancel = Button.builder(
            Component.literal("取消"),
            button -> Minecraft.getInstance().setScreen(parent)
        ).bounds(centerX + 5, buttonY, 100, 20).build();
        
        addRenderableWidget(btnScrollUp);
        addRenderableWidget(btnScrollDown);
        addRenderableWidget(btnConfirm);
        addRenderableWidget(btnCancel);
    }
    
    private void loadTags() {
        availableTags.clear();
        
        try {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var registryAccess = level.registryAccess();
                var itemRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.ITEM);
                
                var tagManager = itemRegistry.getTagNames();
                if (tagManager != null) {
                    tagManager.forEach(tagKey -> availableTags.add(tagKey));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        filteredTags = new ArrayList<>(availableTags);
    }
    
    private void filterTags(String searchText) {
        filteredTags.clear();
        
        if (searchText.isEmpty()) {
            filteredTags.addAll(availableTags);
        } else {
            String lowerSearch = searchText.toLowerCase();
            for (TagKey<Item> tag : availableTags) {
                if (tag.location().toString().toLowerCase().contains(lowerSearch)) {
                    filteredTags.add(tag);
                }
            }
        }
        
        scrollOffset = 0;
        selectedIndex = -1;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            Component.literal("添加物品标签组"),
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        renderList(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= filteredTags.size()) break;
            
            int y = listStartY + i * itemHeight;
            renderTagItem(guiGraphics, y, itemIndex, mouseX, mouseY);
        }
    }
    
    private void renderTagItem(GuiGraphics guiGraphics, int y, int itemIndex, int mouseX, int mouseY) {
        TagKey<Item> tag = filteredTags.get(itemIndex);
        boolean isSelected = itemIndex == selectedIndex;
        boolean isMouseOver = mouseX >= listStartX && mouseX < listStartX + 300 &&
                            mouseY >= y && mouseY < y + itemHeight;
        
        int bgColor = isSelected ? 0x80FF8000 : (isMouseOver ? 0x80FFFFFF : 0xFF373737);
        guiGraphics.fill(listStartX, y, listStartX + 300, y + itemHeight, bgColor);
        
        guiGraphics.fill(listStartX - 1, y - 1, listStartX + 301, y, 0xFF000000);
        guiGraphics.fill(listStartX - 1, y + itemHeight, listStartX + 301, y + itemHeight + 1, 0xFF000000);
        guiGraphics.fill(listStartX - 1, y, listStartX, y + itemHeight, 0xFF000000);
        guiGraphics.fill(listStartX + 300, y, listStartX + 301, y + itemHeight, 0xFF000000);
        
        String tagString = "#" + tag.location().toString();
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.literal(tagString),
            listStartX + 5,
            y + 5,
            0xFFFFFF
        );
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedIndex = getTagAtPosition((int)mouseX, (int)mouseY);
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private int getTagAtPosition(int mouseX, int mouseY) {
        for (int i = 0; i < itemsPerPage; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= filteredTags.size()) break;
            
            int y = listStartY + i * itemHeight;
            
            if (mouseX >= listStartX && mouseX < listStartX + 300 &&
                mouseY >= y && mouseY < y + itemHeight) {
                return itemIndex;
            }
        }
        return -1;
    }
    
    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
        }
    }
    
    private void scrollDown() {
        int maxOffset = Math.max(0, filteredTags.size() - itemsPerPage);
        if (scrollOffset < maxOffset) {
            scrollOffset++;
        }
    }
    
    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < filteredTags.size()) {
            TagKey<Item> tag = filteredTags.get(selectedIndex);
            callback.accept(new SlotSelectionScreen.SlotSelectionResult(
                SlotSelectionScreen.SlotSelectionResult.SelectionType.TAG_GROUP_SELECTED,
                tag,
                1
            ));
        }
        Minecraft.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
