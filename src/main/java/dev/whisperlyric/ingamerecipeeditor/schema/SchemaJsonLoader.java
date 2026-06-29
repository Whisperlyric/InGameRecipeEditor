package dev.whisperlyric.ingamerecipeeditor.schema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Schema JSON加载器
 * 从资源文件加载配方Schema定义
 * 新格式：每个配方类型一个JSON文件，位于schemas目录下
 */
public class SchemaJsonLoader extends SimplePreparableReloadListener<Map<String, JsonObject>> {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SCHEMAS_PATH = "schemas";
    private static final String DEFAULTS_PATH = "schemas/defaults";
    
    private Map<String, JsonObject> loadedSchemas = new HashMap<>();
    private JsonObject globalDefaults = new JsonObject();
    
    @Override
    protected @NotNull Map<String, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, JsonObject> schemas = new HashMap<>();
        
        // 1. 加载全局默认值
        profiler.push("load_global_defaults");
        try {
            Optional<Resource> globalResourceOpt = resourceManager.getResource(
                ResourceLocation.parse(InGameRecipeEditor.MOD_ID + ":" + DEFAULTS_PATH + "/global_defaults.json")
            );
            if (globalResourceOpt.isPresent()) {
                Resource globalResource = globalResourceOpt.get();
                globalDefaults = GSON.fromJson(new InputStreamReader(globalResource.open()), JsonObject.class);
                InGameRecipeEditor.LOGGER.info("已加载全局默认值文件");
            }
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.warn("无法加载全局默认值文件: {}", e.getMessage());
        }
        profiler.pop();
        
        // 2. 加载所有配方Schema文件（扫描schemas目录）
        profiler.push("load_recipe_schemas");
        loadAllSchemaFiles(resourceManager, schemas);
        profiler.pop();
        
        return schemas;
    }
    
    /**
     * 加载所有Schema文件
     */
    private void loadAllSchemaFiles(ResourceManager resourceManager, Map<String, JsonObject> schemas) {
        // 获取schemas目录下所有资源（返回Map）
        Map<ResourceLocation, Resource> allResources = resourceManager.listResources(
            SCHEMAS_PATH,
            location -> location.getPath().endsWith(".json")
        );
        
        for (Map.Entry<ResourceLocation, Resource> entry : allResources.entrySet()) {
            ResourceLocation location = entry.getKey();
            
            // 排除defaults子目录
            String path = location.getPath();
            if (path.startsWith(DEFAULTS_PATH + "/")) {
                continue;
            }
            
            // 只处理当前模组的资源
            if (!location.getNamespace().equals(InGameRecipeEditor.MOD_ID)) {
                continue;
            }
            
            try {
                Resource resource = entry.getValue();
                JsonObject schemaJson = GSON.fromJson(new InputStreamReader(resource.open()), JsonObject.class);
                
                if (schemaJson.has("recipe_type")) {
                    String recipeType = schemaJson.get("recipe_type").getAsString();
                    schemas.put(recipeType, schemaJson);
                    InGameRecipeEditor.LOGGER.info("已加载配方Schema: {}", recipeType);
                }
            } catch (IOException e) {
                InGameRecipeEditor.LOGGER.warn("无法加载Schema文件 {}: {}", location, e.getMessage());
            }
        }
    }
    
    @Override
    protected void apply(Map<String, JsonObject> preparedSchemas, @NotNull ResourceManager resourceManager, ProfilerFiller profiler) {
        this.loadedSchemas = preparedSchemas;
        
        // 注入ResourceManager到PatchRegistry，使其从资源包读取补丁文件
        PatchRegistry.setResourceManager(resourceManager);

        // 注册所有Schema到SchemaRegistry
        profiler.push("register_schemas");
        SchemaRegistry registry = SchemaRegistry.getInstance();
        
        for (Map.Entry<String, JsonObject> entry : preparedSchemas.entrySet()) {
            String recipeType = entry.getKey();
            JsonObject schemaJson = entry.getValue();
            
            RecipeSchema schema = buildSchemaFromJson(recipeType, schemaJson);
            registry.register(schema);
        }
        profiler.pop();
        
        InGameRecipeEditor.LOGGER.info("已注册 {} 个配方Schema", preparedSchemas.size());
    }
    
    /**
     * 从JSON构建RecipeSchema（新格式）
     */
    private RecipeSchema buildSchemaFromJson(String recipeType, JsonObject schemaJson) {
        RecipeSchema.Builder builder = RecipeSchema.builder();
        
        builder.recipeType(recipeType);
        
        // 设置显示名称（从配方类型推断）
        String displayName = generateDisplayName(recipeType);
        builder.displayName(displayName);
        
        // 设置尺寸（默认值）
        builder.size(150, 60);
        
        // 添加槽位（新格式：slots数组）
        if (schemaJson.has("slots")) {
            JsonArray slotsArray = schemaJson.getAsJsonArray("slots");
            for (int i = 0; i < slotsArray.size(); i++) {
                JsonObject slotDef = slotsArray.get(i).getAsJsonObject();
                addSlotFromJson(builder, slotDef, i);
            }
        }
        
        // 添加属性（新格式：properties对象）
        if (schemaJson.has("properties")) {
            JsonObject propertiesObj = schemaJson.getAsJsonObject("properties");
            for (String propId : propertiesObj.keySet()) {
                JsonObject propDef = propertiesObj.getAsJsonObject(propId);
                addPropertyFromJson(builder, propId, propDef);
            }
        }
        
        return builder.build();
    }
    
    /**
     * 从JSON添加槽位（新格式）
     */
    private void addSlotFromJson(RecipeSchema.Builder builder, JsonObject slotDef, int slotIndex) {
        // 解析角色
        SlotDefinition.SlotRole role = parseRole(slotDef.get("role").getAsString());
        int index = slotDef.has("index") ? slotDef.get("index").getAsInt() : slotIndex;
        
        // 解析JSON路径（支持path和paths两种格式）
        String jsonPath = null;
        String[] jsonPaths = null;
        if (slotDef.has("path")) {
            jsonPath = slotDef.get("path").getAsString();
        } else if (slotDef.has("paths")) {
            JsonArray pathsArray = slotDef.getAsJsonArray("paths");
            jsonPaths = new String[pathsArray.size()];
            for (int i = 0; i < pathsArray.size(); i++) {
                jsonPaths[i] = pathsArray.get(i).getAsString();
            }
        }
        
        // 解析value_kind
        String valueKind = slotDef.has("value_kind") ? slotDef.get("value_kind").getAsString() : "item_ingredient";
        SlotDefinition.IngredientType type = parseIngredientType(valueKind);
        
        // 解析amount_path
        String amountPath = slotDef.has("amount_path") ? slotDef.get("amount_path").getAsString() : null;
        
        // 解析amount_scale（显示数量与JSON数量的缩放比例）
        int amountScale = slotDef.has("amount_scale") ? slotDef.get("amount_scale").getAsInt() : 1;
        
        // 解析multiply（数量编辑乘数，默认1，如能量FE=J*2/5则multiply=2）
        int multiply = slotDef.has("multiply") ? slotDef.get("multiply").getAsInt() : 1;
        
        // 解析step（数量编辑步进，默认1，如能量FE=J*2/5则step=5）
        int step = slotDef.has("step") ? slotDef.get("step").getAsInt() : 1;
        
        // 解析unit（数量单位显示文本，如"FE"、"mB"）
        String unit = slotDef.has("unit") ? slotDef.get("unit").getAsString() : null;
        
        // 解析chemical_type_path（用于merged_chemical类型）
        String chemicalTypePath = slotDef.has("chemical_type_path") ? slotDef.get("chemical_type_path").getAsString() : null;
        
        // 生成槽位ID
        String slotId = generateSlotId(role, index);
        
        // 位置信息（从角色和索引计算默认值）
        int x = getDefaultX(role, index);
        int y = getDefaultY(role, index);
        
        // 构建SlotDefinition
        SlotDefinition.Builder slotBuilder = SlotDefinition.builder()
            .id(slotId)
            .index(index)
            .role(role)
            .type(type)
            .required(role != SlotDefinition.SlotRole.RENDER_ONLY && role != SlotDefinition.SlotRole.CATALYST)
            .position(x, y);
        
        // 设置JSON路径
        if (jsonPath != null) {
            slotBuilder.jsonField(jsonPath);
        } else if (jsonPaths != null) {
            slotBuilder.jsonPaths(jsonPaths);
        }
        
        // 设置amount路径
        if (amountPath != null) {
            slotBuilder.amountPath(amountPath);
        }
        
        // 设置chemical type路径
        if (chemicalTypePath != null) {
            slotBuilder.chemicalTypePath(chemicalTypePath);
        }
        
        // 设置数量缩放比例
        if (amountScale > 1) {
            slotBuilder.amountScale(amountScale);
        }
        
        // 设置乘数
        if (multiply > 1) {
            slotBuilder.multiply(multiply);
        }
        
        // 设置步进值
        if (step > 1) {
            slotBuilder.step(step);
        }
        
        // 设置单位
        if (unit != null && !unit.isEmpty()) {
            slotBuilder.unit(unit);
        }
        
        SlotDefinition slot = slotBuilder.build();
        
        // 根据角色添加到builder
        switch (role) {
            case INPUT, EXTRA -> builder.addInputSlot(slot);
            case OUTPUT -> builder.addOutputSlot(slot);
            case RENDER_ONLY, CATALYST -> builder.addRenderOnlySlot(slot);
        }
    }
    
    /**
     * 从JSON添加属性（新格式）
     */
    private void addPropertyFromJson(RecipeSchema.Builder builder, String propId, JsonObject propDef) {
        // 解析JSON路径
        String jsonPath = propDef.has("path") ? propDef.get("path").getAsString() : "/" + propId;
        
        // 解析默认值
        Object defaultValue = null;
        if (propDef.has("default_value")) {
            JsonElement valueElement = propDef.get("default_value");
            if (valueElement.isJsonPrimitive()) {
                JsonPrimitive primitive = valueElement.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    if (primitive.getAsDouble() != primitive.getAsInt()) {
                        defaultValue = primitive.getAsFloat();
                    } else {
                        defaultValue = primitive.getAsInt();
                    }
                } else if (primitive.isBoolean()) {
                    defaultValue = primitive.getAsBoolean();
                } else if (primitive.isString()) {
                    defaultValue = primitive.getAsString();
                }
            }
        }
        
        // 推断属性类型
        PropertyDefinition.PropertyType propType = PropertyDefinition.PropertyType.INTEGER;
        if (defaultValue instanceof Float) {
            propType = PropertyDefinition.PropertyType.FLOAT;
        } else if (defaultValue instanceof Boolean) {
            propType = PropertyDefinition.PropertyType.BOOLEAN;
        } else if (defaultValue instanceof String) {
            propType = PropertyDefinition.PropertyType.STRING;
        }
        
        builder.addProperty(PropertyDefinition.builder()
            .id(propId)
            .jsonField(jsonPath)
            .type(propType)
            .defaultValue(defaultValue)
            .required(false)
            .build()
        );
    }
    
    /**
     * 生成槽位ID
     */
    private String generateSlotId(SlotDefinition.SlotRole role, int index) {
        return role.name().toLowerCase() + "_" + index;
    }
    
    /**
     * 从配方类型生成显示名称
     */
    private String generateDisplayName(String recipeType) {
        String[] parts = recipeType.split(":");
        if (parts.length == 2) {
            String name = parts[1];
            // 将下划线分隔的名称转换为更友好的格式
            return name.replace("_", " ").toUpperCase();
        }
        return recipeType;
    }
    
    // ========== 辅助解析方法 ==========
    
    private SlotDefinition.SlotRole parseRole(String roleStr) {
        return switch (roleStr.toUpperCase()) {
            case "INPUT" -> SlotDefinition.SlotRole.INPUT;
            case "OUTPUT" -> SlotDefinition.SlotRole.OUTPUT;
            case "EXTRA" -> SlotDefinition.SlotRole.EXTRA;
            case "CATALYST" -> SlotDefinition.SlotRole.CATALYST;
            case "RENDER_ONLY" -> SlotDefinition.SlotRole.RENDER_ONLY;
            default -> SlotDefinition.SlotRole.INPUT;
        };
    }
    
    private SlotDefinition.IngredientType parseIngredientType(String valueKind) {
        return switch (valueKind.toLowerCase()) {
            case "item_ingredient", "item_stack", "item_ingredient_array" -> SlotDefinition.IngredientType.ITEM;
            case "fluid_ingredient", "fluid_stack" -> SlotDefinition.IngredientType.FLUID;
            case "gas_ingredient", "gas_stack" -> SlotDefinition.IngredientType.GAS;
            case "infuse_type_ingredient", "infuse_type_stack" -> SlotDefinition.IngredientType.INFUSE_TYPE;
            case "pigment_ingredient", "pigment_stack" -> SlotDefinition.IngredientType.PIGMENT;
            case "slurry_ingredient", "slurry_stack" -> SlotDefinition.IngredientType.SLURRY;
            case "merged_chemical_input", "merged_chemical_output" -> SlotDefinition.IngredientType.ANY;
            case "energy" -> SlotDefinition.IngredientType.ENERGY;
            default -> SlotDefinition.IngredientType.ITEM;
        };
    }
    
    private int getDefaultX(SlotDefinition.SlotRole role, int index) {
        return switch (role) {
            case INPUT -> 34 + (index % 3) * 18;
            case OUTPUT -> 97 + (index % 2) * 18;
            default -> 34;
        };
    }
    
    private int getDefaultY(SlotDefinition.SlotRole role, int index) {
        return switch (role) {
            case INPUT -> 6 + (index / 3) * 18;
            case OUTPUT -> 15 + (index / 2) * 18;
            default -> 6;
        };
    }
    
    /**
     * 获取全局默认值
     */
    public JsonObject getGlobalDefaults() {
        return globalDefaults;
    }
    
    /**
     * 获取配方Schema JSON
     */
    public JsonObject getSchemaJson(String recipeType) {
        return loadedSchemas.get(recipeType);
    }
    
    /**
     * 获取所有已加载的配方类型
     */
    public Set<String> getLoadedRecipeTypes() {
        return loadedSchemas.keySet();
    }
}