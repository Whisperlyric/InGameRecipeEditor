package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.recipe.component.ChemicalSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.EnergySlotComponent;
import com.wzz.registerhelper.gui.recipe.component.FluidSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.GasSlotComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.world.item.ItemStack;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlotManager {
    private int slotSpacing = 20;
    public static final int MIN_SLOT_SPACING = 13;
    public static final int DEFAULT_SLOT_SPACING = 20;

    private List<RecipeComponent> components = new ArrayList<>();
    private final List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private final List<IngredientData> ingredients = new ArrayList<>();
    private IngredientSlot resultSlot;
    private RecipeComponent outputComponent;
    private List<RecipeComponent> outputComponents = new ArrayList<>();
    private ItemStack resultItem = ItemStack.EMPTY;
    private Map<Integer, ItemStack> outputSlotItems = new HashMap<>();

    private RecipeTypeDefinition currentRecipeType;
    private int customTier = 1;

    private int baseX, baseY;
    private int rightPanelX;

    public record IngredientSlot(int x, int y, int index) {}

    public SlotManager(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeSlots();
    }

    public void setSlotSpacing(int spacing) {
        this.slotSpacing = Math.max(MIN_SLOT_SPACING, Math.min(DEFAULT_SLOT_SPACING, spacing));
    }

    public int getSlotSpacing() {
        return slotSpacing;
    }

    public void updateCoordinates(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        updateSlotPositions();
    }

    public void setRecipeType(RecipeTypeDefinition recipeType, int customTier, boolean preserveIngredients) {
        if (recipeType == null) {
            ModLogger.getLogger().error("setRecipeType: recipeType为null");
            return;
        }
        List<IngredientData> oldIngredients = preserveIngredients ? new ArrayList<>(ingredients) : new ArrayList<>();

        this.currentRecipeType = recipeType;
        this.customTier = customTier;
        initializeSlots();
        if (preserveIngredients && !oldIngredients.isEmpty()) {
            for (int i = 0; i < Math.min(ingredientSlots.size(), oldIngredients.size()); i++) {
                if (i < ingredients.size() && !oldIngredients.get(i).isEmpty()) {
                    ingredients.set(i, oldIngredients.get(i));
                }
            }
        }
    }

    private void updateSlotPositions() {
        if (currentRecipeType == null) return;

        String category = currentRecipeType.getProperty("category", String.class);

        if ("crafting".equals(category)) {
            updateCraftingSlotPositions();
        } else if ("avaritia".equals(category)) {
            updateAvaritiaSlotPositions();
        } else if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            updateCookingSlotPositions();
        } else {
            updateCustomSlotPositions();
        }

        initializeOutputSlot();
    }

    private void initializeOutputSlot() {
        String outputType = currentRecipeType.getProperty("outputType", String.class);
        int outputX = rightPanelX + 20;
        int outputY = baseY + 110;
        
        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            RecipeLayout layout = LayoutManager.getLayout(layoutId);
            if (layout != null) {
                outputY += layout.getOutputYOffset();
                
                List<RecipeComponent> layoutOutputs = layout.generateOutputComponents(outputX, outputY);
                if (layoutOutputs != null && !layoutOutputs.isEmpty()) {
                    for (int i = 0; i < layoutOutputs.size(); i++) {
                        RecipeComponent newComp = layoutOutputs.get(i);
                        if (i < outputComponents.size()) {
                            RecipeComponent oldComp = outputComponents.get(i);
                            
                            if (newComp instanceof GasSlotComponent newGas && oldComp instanceof GasSlotComponent oldGas) {
                                if (oldGas.getGasId() != null && !oldGas.getGasId().isEmpty()) {
                                    newGas.setGasId(oldGas.getGasId());
                                }
                                if (oldGas.getAmount() > 0) {
                                    newGas.setAmount(oldGas.getAmount());
                                }
                            } else if (newComp instanceof ChemicalSlotComponent newChem && oldComp instanceof ChemicalSlotComponent oldChem) {
                                if (oldChem.getChemicalId() != null && !oldChem.getChemicalId().isEmpty()) {
                                    newChem.setChemicalId(oldChem.getChemicalId());
                                }
                                if (oldChem.getAmount() > 0) {
                                    newChem.setAmount(oldChem.getAmount());
                                }
                            } else if (newComp instanceof FluidSlotComponent newFluid && oldComp instanceof FluidSlotComponent oldFluid) {
                                if (oldFluid.getFluidId() != null && !oldFluid.getFluidId().isEmpty()) {
                                    newFluid.setFluidId(oldFluid.getFluidId());
                                }
                                if (oldFluid.getAmount() > 0) {
                                    newFluid.setAmount(oldFluid.getAmount());
                                }
                            } else if (newComp instanceof SlotComponent newSlot && oldComp instanceof SlotComponent oldSlot) {
                                int slotIndex = newSlot.getSlotIndex();
                                ItemStack oldItem = outputSlotItems.get(slotIndex);
                                if (oldItem != null && !oldItem.isEmpty()) {
                                    outputSlotItems.put(newSlot.getSlotIndex(), oldItem.copy());
                                }
                            }
                        }
                    }
                    
                    outputComponents.clear();
                    outputComponents.addAll(layoutOutputs);
                    outputComponent = outputComponents.get(0);
                    resultSlot = null;
                    return;
                }
                
                String layoutOutputType = layout.getOutputType();
                if (layoutOutputType != null) {
                    outputType = layoutOutputType;
                }
            }
        }
        
        outputComponents.clear();
        
        if (outputType == null) {
            resultSlot = new IngredientSlot(outputX, outputY, -1);
            outputComponent = null;
            return;
        }
        
        switch (outputType) {
            case "energy" -> {
                long oldEnergy = 0;
                if (outputComponent instanceof EnergySlotComponent oldEnergyComp) {
                    oldEnergy = oldEnergyComp.getEnergy();
                }
                outputComponent = new EnergySlotComponent(outputX, outputY, "energy_output", 0, oldEnergy, 100000000L);
                resultSlot = null;
            }
            case "fluid" -> {
                String oldFluidId = null;
                long oldAmount = 0;
                if (outputComponent instanceof FluidSlotComponent oldFluidComp) {
                    oldFluidId = oldFluidComp.getFluidId();
                    oldAmount = oldFluidComp.getAmount();
                }
                FluidSlotComponent newFluidComp = new FluidSlotComponent(outputX, outputY, "fluid_output", 0);
                if (oldFluidId != null) {
                    newFluidComp.setFluidId(oldFluidId);
                }
                newFluidComp.setAmount(oldAmount);
                outputComponent = newFluidComp;
                resultSlot = null;
            }
            case "gas" -> {
                String oldGasId = null;
                int oldAmount = 0;
                if (outputComponent instanceof GasSlotComponent oldGasComp) {
                    oldGasId = oldGasComp.getGasId();
                    oldAmount = oldGasComp.getAmount();
                }
                GasSlotComponent newGasComp = new GasSlotComponent(outputX, outputY, "gas_output", 0);
                if (oldGasId != null) {
                    newGasComp.setGasId(oldGasId);
                }
                newGasComp.setAmount(oldAmount);
                outputComponent = newGasComp;
                resultSlot = null;
            }
            case "chemical" -> {
                String oldChemicalId = null;
                long oldAmount = 0;
                if (outputComponent instanceof ChemicalSlotComponent oldChemicalComp) {
                    oldChemicalId = oldChemicalComp.getChemicalId();
                    oldAmount = oldChemicalComp.getAmount();
                }
                ChemicalSlotComponent newChemicalComp = new ChemicalSlotComponent(outputX, outputY, "chemical_output", 0, ChemicalSlotComponent.ChemicalType.GAS);
                if (oldChemicalId != null) {
                    newChemicalComp.setChemicalId(oldChemicalId);
                }
                newChemicalComp.setAmount(oldAmount);
                outputComponent = newChemicalComp;
                resultSlot = null;
            }
            case "slurry" -> {
                String oldSlurryId = null;
                long oldAmount = 0;
                if (outputComponent instanceof ChemicalSlotComponent oldSlurryComp) {
                    oldSlurryId = oldSlurryComp.getChemicalId();
                    oldAmount = oldSlurryComp.getAmount();
                }
                ChemicalSlotComponent newSlurryComp = new ChemicalSlotComponent(outputX, outputY, "slurry_output", 0, ChemicalSlotComponent.ChemicalType.SLURRY);
                if (oldSlurryId != null) {
                    newSlurryComp.setChemicalId(oldSlurryId);
                }
                newSlurryComp.setAmount(oldAmount);
                outputComponent = newSlurryComp;
                resultSlot = null;
            }
            case "pigment" -> {
                String oldPigmentId = null;
                long oldAmount = 0;
                if (outputComponent instanceof ChemicalSlotComponent oldPigmentComp) {
                    oldPigmentId = oldPigmentComp.getChemicalId();
                    oldAmount = oldPigmentComp.getAmount();
                }
                ChemicalSlotComponent newPigmentComp = new ChemicalSlotComponent(outputX, outputY, "pigment_output", 0, ChemicalSlotComponent.ChemicalType.PIGMENT);
                if (oldPigmentId != null) {
                    newPigmentComp.setChemicalId(oldPigmentId);
                }
                newPigmentComp.setAmount(oldAmount);
                outputComponent = newPigmentComp;
                resultSlot = null;
            }
            default -> {
                resultSlot = new IngredientSlot(outputX, outputY, -1);
                outputComponent = null;
            }
        }
        
        if (outputComponent != null) {
            outputComponents.add(outputComponent);
        }
    }

    private void updateCraftingSlotPositions() {
        int gridWidth = Math.min(3, currentRecipeType.getMaxGridWidth());
        int gridHeight = Math.min(3, currentRecipeType.getMaxGridHeight());
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    private void updateAvaritiaSlotPositions() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = DynamicRecipeBuilder.getGridSizeForTier(actualTier);
        updateGridSlotPositions(gridSize, gridSize);
    }

    private void updateCookingSlotPositions() {
        if (!ingredientSlots.isEmpty()) {
            ingredientSlots.set(0, new IngredientSlot(baseX + slotSpacing, baseY + 170, 0));
        }
    }

    private void updateCustomSlotPositions() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    private void updateGridSlotPositions(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + 150;

        for (int i = 0; i < ingredientSlots.size() && i < gridWidth * gridHeight; i++) {
            int x = i % gridWidth;
            int y = i / gridWidth;
            int slotX = startX + x * slotSpacing;
            int slotY = startY + y * slotSpacing;
            ingredientSlots.set(i, new IngredientSlot(slotX, slotY, i));
        }
    }

    private void initializeSlots() {
        ingredientSlots.clear();
        ingredients.clear();

        if (currentRecipeType == null) return;

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            initializeCustomLayout(layoutId);
        } else {
            initializeTraditionalLayout();
        }

        resultSlot = new IngredientSlot(rightPanelX + 20, baseY + 130, -1);
    }

    private void initializeCustomLayout(String layoutId) {
        RecipeLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            ModLogger.getLogger().warn("找不到布局: {}，使用默认布局", layoutId);
            initializeTraditionalLayout();
            return;
        }

        Map<String, Object> oldInputData = new HashMap<>();
        for (RecipeComponent comp : components) {
            if (comp instanceof FluidSlotComponent fluidComp) {
                if (!fluidComp.getId().contains("output")) {
                    oldInputData.put(fluidComp.getId(), new Object[]{fluidComp.getFluidId(), fluidComp.getAmount()});
                }
            } else if (comp instanceof GasSlotComponent gasComp) {
                if (!gasComp.getId().contains("output")) {
                    oldInputData.put(gasComp.getId(), new Object[]{gasComp.getGasId(), gasComp.getAmount()});
                }
            } else if (comp instanceof ChemicalSlotComponent chemicalComp) {
                if (!chemicalComp.getId().contains("output")) {
                    oldInputData.put(chemicalComp.getId(), new Object[]{chemicalComp.getChemicalId(), chemicalComp.getAmount()});
                }
            }
        }

        components.clear();
        ingredientSlots.clear();
        ingredients.clear();

        components = layout.generateComponents(baseX, baseY + 150, customTier);
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                int index = slotComp.getSlotIndex();
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(),
                        slotComp.getY(),
                        index
                ));
                ingredients.add(IngredientData.empty());
            }
            
            if (component instanceof FluidSlotComponent fluidComp) {
                if (!fluidComp.getId().contains("output")) {
                    Object[] data = (Object[]) oldInputData.get(fluidComp.getId());
                    if (data != null) {
                        String oldFluidId = (String) data[0];
                        long oldAmount = (Long) data[1];
                        if (oldFluidId != null && !oldFluidId.isEmpty()) {
                            fluidComp.setFluidId(oldFluidId);
                            fluidComp.setAmount(oldAmount);
                            ModLogger.getLogger().info("Restored input fluid slot {}: fluidId={}, amount={}",
                                fluidComp.getId(), oldFluidId, oldAmount);
                        }
                    }
                }
            } else if (component instanceof GasSlotComponent gasComp) {
                if (!gasComp.getId().contains("output")) {
                    Object[] data = (Object[]) oldInputData.get(gasComp.getId());
                    if (data != null) {
                        String oldGasId = (String) data[0];
                        int oldAmount = (Integer) data[1];
                        if (oldGasId != null && !oldGasId.isEmpty()) {
                            gasComp.setGasId(oldGasId);
                            gasComp.setAmount(oldAmount);
                            ModLogger.getLogger().info("Restored input gas slot {}: gasId={}, amount={}",
                                gasComp.getId(), oldGasId, oldAmount);
                        }
                    }
                }
            } else if (component instanceof ChemicalSlotComponent chemicalComp) {
                if (!chemicalComp.getId().contains("output")) {
                    Object[] data = (Object[]) oldInputData.get(chemicalComp.getId());
                    if (data != null) {
                        String oldChemicalId = (String) data[0];
                        long oldAmount = (Long) data[1];
                        if (oldChemicalId != null && !oldChemicalId.isEmpty()) {
                            chemicalComp.setChemicalId(oldChemicalId);
                            chemicalComp.setAmount(oldAmount);
                            ModLogger.getLogger().info("Restored input chemical slot {}: chemicalId={}, amount={}",
                                chemicalComp.getId(), oldChemicalId, oldAmount);
                        }
                    }
                }
            }
        }
        
        initializeOutputSlot();
    }

    public int getBaseX() {
        return baseX;
    }

    public int getBaseY() {
        return baseY;
    }

    public List<RecipeComponent> getComponents() {
        return components;
    }

    public List<RecipeComponent> setComponents(List<RecipeComponent> components) {
        return this.components = components;
    }

    private void initializeTraditionalLayout() {
        String category = currentRecipeType.getProperty("category", String.class);
        if ("crafting".equals(category)) {
            initializeCraftingSlots();
        } else if ("avaritia".equals(category)) {
            initializeAvaritiaSlots();
        } else if ("cooking".equals(category)) {
            initializeCookingSlots();
        } else {
            initializeCustomSlots();
        }
    }

    private void initializeCraftingSlots() {
        initializeGridSlots(3, 3);
    }

    private void initializeAvaritiaSlots() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = DynamicRecipeBuilder.getGridSizeForTier(actualTier);
        initializeGridSlots(gridSize, gridSize);
    }

    private void initializeCookingSlots() {
        ingredientSlots.add(new IngredientSlot(baseX + slotSpacing, baseY + 170, 0));
        ingredients.add(IngredientData.empty());
    }

    private void initializeCustomSlots() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }

        initializeGridSlots(gridWidth, gridHeight);
    }

    private void initializeGridSlots(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + 150;
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int slotX = startX + x * slotSpacing;
                int slotY = startY + y * slotSpacing;
                ingredientSlots.add(new IngredientSlot(slotX, slotY, y * gridWidth + x));
                ingredients.add(IngredientData.empty());
            }
        }
    }
    
    /**
     * 设置材料数据（新方法）
     */
    public void setIngredientData(int slotIndex, IngredientData data) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, data.copy());
        }
    }
    
    /**
     * 根据 SlotComponent 的 slotIndex 找到对应的 ingredients 列表索引
     */
    public int findIngredientListIndex(int componentSlotIndex) {
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (ingredientSlots.get(i).index() == componentSlotIndex) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 获取材料数据（新方法）
     */
    public IngredientData getIngredientData(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex);
        }
        return IngredientData.empty();
    }
    
    /**
     * 获取所有材料数据（新方法）
     */
    public List<IngredientData> getIngredientsData() {
        return new ArrayList<>(ingredients);
    }

    /**
     * 设置材料列表（兼容旧代码，从ItemStack转换为IngredientData）
     */
    public void setIngredients(List<ItemStack> newIngredients) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < newIngredients.size()) {
                ItemStack stack = newIngredients.get(i);
                if (!stack.isEmpty()) {
                    ingredients.add(IngredientData.fromItem(stack));
                } else {
                    ingredients.add(IngredientData.empty());
                }
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    /**
     * 设置指定槽位的材料（兼容旧代码）
     */
    public boolean setIngredient(int slotIndex, ItemStack item) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            if (!item.isEmpty()) {
                ingredients.set(slotIndex, IngredientData.fromItem(item));
            } else {
                ingredients.set(slotIndex, IngredientData.empty());
            }
            return true;
        }
        return false;
    }

    /**
     * 获取指定槽位的材料（兼容旧代码，返回ItemStack）
     */
    public ItemStack getIngredient(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex).getItemStack();
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * 获取所有材料（兼容旧代码，转换为ItemStack列表）
     */
    public List<ItemStack> getIngredients() {
        List<ItemStack> result = new ArrayList<>();
        for (IngredientData data : ingredients) {
            result.add(data.getItemStack());
        }
        return result;
    }

    /**
     * 清空指定槽位
     */
    public void clearSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, IngredientData.empty());
        }
    }

    /**
     * 清空所有材料
     */
    public void clearAllIngredients() {
        ingredients.replaceAll(ignored -> IngredientData.empty());
        resultItem = ItemStack.EMPTY;
        
        for (RecipeComponent component : components) {
            if (component instanceof FluidSlotComponent fluidComp) {
                fluidComp.setFluidId(null);
                fluidComp.setAmount(0);
            } else if (component instanceof GasSlotComponent gasComp) {
                gasComp.setGasId(null);
                gasComp.setAmount(0);
            } else if (component instanceof ChemicalSlotComponent chemicalComp) {
                chemicalComp.setChemicalId(null);
                chemicalComp.setAmount(0);
            }
        }
        
        if (outputComponent instanceof EnergySlotComponent energyComp) {
            energyComp.setEnergy(0);
        } else if (outputComponent instanceof FluidSlotComponent fluidComp) {
            fluidComp.setFluidId(null);
            fluidComp.setAmount(0);
        } else if (outputComponent instanceof GasSlotComponent gasComp) {
            gasComp.setGasId(null);
            gasComp.setAmount(0);
        } else if (outputComponent instanceof ChemicalSlotComponent chemicalComp) {
            chemicalComp.setChemicalId(null);
            chemicalComp.setAmount(0);
        }
    }

    /**
     * 填充所有空槽位（兼容旧代码）
     */
    public void fillEmptySlots(ItemStack item) {
        IngredientData data = IngredientData.fromItem(item);
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                ingredients.set(i, data.copy());
            }
        }
    }

    /**
     * 用 IngredientData 列表直接设置槽位（保留 ignoreKeys 等信息）
     */
    public void setIngredientsData(List<IngredientData> newData) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < newData.size()) {
                ingredients.add(newData.get(i).copy());
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    /**
     * 获取当前网格大小
     */
    public GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new GridDimensions(3, 3, slotSpacing);
        }

        String category = currentRecipeType.getProperty("category", String.class);

        if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            return new GridDimensions(1, 1, slotSpacing);
        } else {
            return new GridDimensions(
                    currentRecipeType.getMaxGridWidth(),
                    currentRecipeType.getMaxGridHeight(),
                    slotSpacing
            );
        }
    }

    public record GridDimensions(int width, int height, int spacing) {
        /** 兼容旧调用：默认 20px 间距 */
        public GridDimensions(int width, int height) {
            this(width, height, DEFAULT_SLOT_SPACING);
        }

        public int getTotalSlots() {
            return width * height;
        }

        public int getPixelWidth() {
            return width * spacing;
        }

        public int getPixelHeight() {
            return height * spacing;
        }
    }

    // Getters
    public List<IngredientSlot> getIngredientSlots() { return ingredientSlots; }
    public IngredientSlot getResultSlot() { return resultSlot; }
    public ItemStack getResultItem() { return resultItem; }
    public RecipeTypeDefinition getCurrentRecipeType() { return currentRecipeType; }
    public int getCustomTier() { return customTier; }
    public RecipeComponent getOutputComponent() { return outputComponent; }
    
    public List<RecipeComponent> getOutputComponents() { return outputComponents; }
    
    public Map<Integer, ItemStack> getOutputSlotItems() { return outputSlotItems; }
    
    public void setOutputSlotItem(int slotIndex, ItemStack item) {
        outputSlotItems.put(slotIndex, item.copy());
    }
    
    public ItemStack getOutputSlotItem(int slotIndex) {
        return outputSlotItems.getOrDefault(slotIndex, ItemStack.EMPTY);
    }
    
    // Setters
    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem.copy();
    }
    
    public void setOutputComponent(RecipeComponent outputComponent) {
        this.outputComponent = outputComponent;
    }
}