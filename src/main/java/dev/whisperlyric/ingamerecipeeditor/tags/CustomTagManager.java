package dev.whisperlyric.ingamerecipeeditor.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义标签管理器（简化版）
 * 只负责生成标准的 Minecraft 标签文件
 */
public class CustomTagManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path TAGS_DIR = FMLPaths.GAMEDIR.get()
            .resolve("igredata/custom_tags");

    static {
        try {
            Files.createDirectories(TAGS_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    // 缓存：避免每次读取标签文件时重新解析磁盘
    private static final java.util.concurrent.ConcurrentMap<ResourceLocation, java.util.List<String>> itemsCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<ResourceLocation, java.util.List<String>> fluidsCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 注册自定义标签
     * @param tagId 标签ID (例如: minecraft:planks)
     * @param items 物品列表（只使用物品类型，忽略数量和NBT）
     */
    public static boolean registerTag(ResourceLocation tagId, List<ItemStack> items) {
        if (tagId == null || items == null || items.isEmpty()) {
            return false;
        }
        
        try {
            List<Item> uniqueItems = items.stream()
                    .map(ItemStack::getItem)
                    .distinct()
                    .collect(Collectors.toList());
            
            saveTagFile(tagId, uniqueItems);
            // invalidate cache for this tag
            itemsCache.remove(tagId);
            LOGGER.info("已创建自定义物品标签: {} (包含 {} 个物品)", tagId, uniqueItems.size());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("注册自定义物品标签失败: {}", tagId, e);
            return false;
        }
    }
    
    /**
     * 注册自定义流体标签
     * @param tagId 标签ID (例如: minecraft:water)
     * @param fluidIds 流体ID列表
     */
    public static boolean registerFluidTag(ResourceLocation tagId, List<ResourceLocation> fluidIds) {
        if (tagId == null || fluidIds == null || fluidIds.isEmpty()) {
            return false;
        }
        
        try {
            List<ResourceLocation> uniqueFluids = fluidIds.stream()
                    .distinct()
                    .collect(Collectors.toList());
            
            saveFluidTagFile(tagId, uniqueFluids);
            // invalidate cache for this tag
            fluidsCache.remove(tagId);
            LOGGER.info("已创建自定义流体标签: {} (包含 {} 个流体)", tagId, uniqueFluids.size());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("注册自定义流体标签失败: {}", tagId, e);
            return false;
        }
    }
    
    /**
     * 删除自定义标签
     */
    public static boolean removeTag(ResourceLocation tagId) {
        try {
            boolean removed = false;
            
            File itemTagFile = getTagFile(tagId, "items");
            if (itemTagFile.exists() && itemTagFile.delete()) {
                LOGGER.info("已删除自定义物品标签: {}", tagId);
                removed = true;
            }
            
            File fluidTagFile = getTagFile(tagId, "fluids");
            if (fluidTagFile.exists() && fluidTagFile.delete()) {
                LOGGER.info("已删除自定义流体标签: {}", tagId);
                removed = true;
            }
            
            // 清理缓存
            itemsCache.remove(tagId);
            fluidsCache.remove(tagId);

            return removed;
            
        } catch (Exception e) {
            LOGGER.error("删除自定义标签失败: {}", tagId, e);
            return false;
        }
    }
    
    /**
     * 检查标签是否存在
     */
    public static boolean hasTag(ResourceLocation tagId) {
        return getTagFile(tagId, "items").exists() || getTagFile(tagId, "fluids").exists();
    }
    
    /**
     * 检查是否为自定义标签（通过命名空间判断）
     */
    public static boolean isCustomTag(ResourceLocation tagId) {
        // 检查标签文件是否存在于自定义标签目录
        return hasTag(tagId);
    }
    
    /**
     * 获取所有自定义标签ID
     */
    public static Set<ResourceLocation> getAllTags() {
        Set<ResourceLocation> tags = new HashSet<>();
        
        try {
            File tagsDir = TAGS_DIR.toFile();
            if (!tagsDir.exists()) {
                return tags;
            }
            
            // 遍历命名空间目录
            File[] namespaceDirs = tagsDir.listFiles(File::isDirectory);
            if (namespaceDirs == null) {
                return tags;
            }
            
            for (File namespaceDir : namespaceDirs) {
                String namespace = namespaceDir.getName();
                
                // 遍历items目录
                File itemsDir = new File(namespaceDir, "items");
                if (itemsDir.exists()) {
                    scanTagsInDirectory(namespace, itemsDir, itemsDir, tags);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("获取标签列表失败", e);
        }
        
        return tags;
    }
    
    /**
     * 扫描目录中的标签
     */
    private static void scanTagsInDirectory(String namespace, File baseDir, File currentDir, Set<ResourceLocation> tags) {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanTagsInDirectory(namespace, baseDir, file, tags);
            } else if (file.getName().endsWith(".json")) {
                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.getAbsolutePath().length() + 1)
                        .replace(File.separatorChar, '/')
                        .replace(".json", "");
                tags.add(ResourceLocation.parse(namespace + ":" + relativePath));
            }
        }
    }
    
    /**
     * 保存标签文件（标准 Minecraft 标签格式）
     */
    private static void saveTagFile(ResourceLocation tagId, List<Item> items) throws Exception {
        JsonObject tagJson = new JsonObject();
        tagJson.addProperty("replace", false);
        
        JsonArray valuesArray = new JsonArray();
        for (Item item : items) {
            String itemId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)).toString();
            valuesArray.add(itemId);
        }
        tagJson.add("values", valuesArray);
        
        File tagFile = getTagFile(tagId, "items");
        Files.createDirectories(tagFile.getParentFile().toPath());
        
        try (FileWriter writer = new FileWriter(tagFile)) {
            GSON.toJson(tagJson, writer);
        }
        
        LOGGER.info("已保存标签文件: {}", tagFile.getPath());
        // 写盘后刷新缓存
        itemsCache.remove(tagId);
    }
    
    /**
     * 保存流体标签文件
     */
    private static void saveFluidTagFile(ResourceLocation tagId, List<ResourceLocation> fluidIds) throws Exception {
        JsonObject tagJson = new JsonObject();
        tagJson.addProperty("replace", false);
        
        JsonArray valuesArray = new JsonArray();
        for (ResourceLocation fluidId : fluidIds) {
            valuesArray.add(fluidId.toString());
        }
        tagJson.add("values", valuesArray);
        
        File tagFile = getTagFile(tagId, "fluids");
        Files.createDirectories(tagFile.getParentFile().toPath());
        
        try (FileWriter writer = new FileWriter(tagFile)) {
            GSON.toJson(tagJson, writer);
        }
        
        LOGGER.info("已保存流体标签文件: {}", tagFile.getPath());
        // 写盘后刷新缓存
        fluidsCache.remove(tagId);
    }
    
    /**
     * 获取标签文件路径
     */
    private static File getTagFile(ResourceLocation tagId, String tagType) {
        Path tagPath = TAGS_DIR
                .resolve(tagId.getNamespace())
                .resolve(tagType)
                .resolve(tagId.getPath() + ".json");
        return tagPath.toFile();
    }
    
    /**
     * 读取标签内容
     */
    public static List<String> readTagItems(ResourceLocation tagId) {
        return readTagValues(tagId, "items");
    }
    
    /**
     * 读取流体标签内容
     */
    public static List<String> readTagFluids(ResourceLocation tagId) {
        return readTagValues(tagId, "fluids");
    }
    
    /**
     * 读取标签值
     */
    private static List<String> readTagValues(ResourceLocation tagId, String tagType) {
        List<String> values = new ArrayList<>();
        // 尝试从缓存读取
        try {
            if ("items".equals(tagType)) {
                var cached = itemsCache.get(tagId);
                if (cached != null) return new ArrayList<>(cached);
            } else if ("fluids".equals(tagType)) {
                var cached = fluidsCache.get(tagId);
                if (cached != null) return new ArrayList<>(cached);
            }
        } catch (Exception ignored) {}

        try {
            File tagFile = getTagFile(tagId, tagType);
            if (!tagFile.exists()) {
                return values;
            }

            try (FileReader reader = new FileReader(tagFile)) {
                JsonObject tagJson = GSON.fromJson(reader, JsonObject.class);
                if (tagJson != null && tagJson.has("values")) {
                    JsonArray jsonArray = tagJson.getAsJsonArray("values");
                    for (int i = 0; i < jsonArray.size(); i++) {
                        values.add(jsonArray.get(i).getAsString());
                    }
                }
            }

            // 写入缓存
            if (!values.isEmpty()) {
                if ("items".equals(tagType)) itemsCache.put(tagId, new ArrayList<>(values));
                else if ("fluids".equals(tagType)) fluidsCache.put(tagId, new ArrayList<>(values));
            }

        } catch (Exception e) {
            LOGGER.error("读取标签文件失败: {}", tagId, e);
        }

        return values;
    }
    
    /**
     * 读取标签文件的完整信息
     */
    public static @Nullable TagFileInfo readTagFileInfo(ResourceLocation tagId, String tagType) {
        try {
            File tagFile = getTagFile(tagId, tagType);
            if (!tagFile.exists()) {
                return null;
            }
            
            try (FileReader reader = new FileReader(tagFile)) {
                JsonObject tagJson = GSON.fromJson(reader, JsonObject.class);
                if (tagJson == null) {
                    return null;
                }
                
                boolean replace = tagJson.has("replace") && tagJson.get("replace").getAsBoolean();
                List<String> values = new ArrayList<>();
                List<String> removedValues = new ArrayList<>();
                
                if (tagJson.has("values")) {
                    JsonArray valuesArray = tagJson.getAsJsonArray("values");
                    for (int i = 0; i < valuesArray.size(); i++) {
                        String value = valuesArray.get(i).getAsString();
                        if (value.startsWith("!")) {
                            removedValues.add(value.substring(1));
                        } else {
                            values.add(value);
                        }
                    }
                }
                
                return new TagFileInfo(replace, values, removedValues);
            }
            
        } catch (Exception e) {
            LOGGER.error("读取标签文件信息失败: {}", tagId, e);
            return null;
        }
    }
    
    /**
     * 保存标签文件
     */
    public static boolean saveTagFileInfo(ResourceLocation tagId, String tagType, boolean replace, 
                                          List<String> values, List<String> removedValues) {
        try {
            JsonObject tagJson = new JsonObject();
            tagJson.addProperty("replace", replace);
            
            JsonArray valuesArray = new JsonArray();
            for (String value : values) {
                valuesArray.add(value);
            }
            for (String removed : removedValues) {
                valuesArray.add("!" + removed);
            }
            tagJson.add("values", valuesArray);
            
            File tagFile = getTagFile(tagId, tagType);
            Files.createDirectories(tagFile.getParentFile().toPath());
            
            try (FileWriter writer = new FileWriter(tagFile)) {
                GSON.toJson(tagJson, writer);
            }
            
            LOGGER.info("已保存标签文件: {}", tagFile.getPath());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("保存标签文件失败: {}", tagId, e);
            return false;
        }
    }
    
    /**
     * 手动使缓存失效（外部调用）
     */
    public static void invalidateTagCache(ResourceLocation tagId) {
        if (tagId == null) return;
        itemsCache.remove(tagId);
        fluidsCache.remove(tagId);
    }
    /**
     * 标签文件信息类
     */
    public static class TagFileInfo {
        public final boolean replace;
        public final List<String> values;
        public final List<String> removedValues;
        
        public TagFileInfo(boolean replace, List<String> values, List<String> removedValues) {
            this.replace = replace;
            this.values = values;
            this.removedValues = removedValues;
        }
    }
}