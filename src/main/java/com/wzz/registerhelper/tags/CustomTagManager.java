package com.wzz.registerhelper.tags;

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
    private static final Path TAGS_DIR = FMLPaths.CONFIGDIR.get()
            .resolve("registerhelper/custom_tags");

    static {
        try {
            Files.createDirectories(TAGS_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
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
            
            saveTagFile(tagId, uniqueItems, "items");
            
            LOGGER.info("已创建自定义物品标签: {} (包含 {} 个物品)", tagId, uniqueItems.size());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("注册自定义物品标签失败: " + tagId, e);
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
            
            LOGGER.info("已创建自定义流体标签: {} (包含 {} 个流体)", tagId, uniqueFluids.size());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("注册自定义流体标签失败: " + tagId, e);
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
            if (itemTagFile.exists()) {
                itemTagFile.delete();
                LOGGER.info("已删除自定义物品标签: {}", tagId);
                removed = true;
            }
            
            File fluidTagFile = getTagFile(tagId, "fluids");
            if (fluidTagFile.exists()) {
                fluidTagFile.delete();
                LOGGER.info("已删除自定义流体标签: {}", tagId);
                removed = true;
            }
            
            return removed;
            
        } catch (Exception e) {
            LOGGER.error("删除自定义标签失败: " + tagId, e);
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
    private static void saveTagFile(ResourceLocation tagId, List<Item> items, String tagType) throws Exception {
        JsonObject tagJson = new JsonObject();
        tagJson.addProperty("replace", false);
        
        JsonArray valuesArray = new JsonArray();
        for (Item item : items) {
            String itemId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item)).toString();
            valuesArray.add(itemId);
        }
        tagJson.add("values", valuesArray);
        
        File tagFile = getTagFile(tagId, tagType);
        Files.createDirectories(tagFile.getParentFile().toPath());
        
        try (FileWriter writer = new FileWriter(tagFile)) {
            GSON.toJson(tagJson, writer);
        }
        
        LOGGER.info("已保存标签文件: {}", tagFile.getPath());
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
            
        } catch (Exception e) {
            LOGGER.error("读取标签文件失败: " + tagId, e);
        }
        
        return values;
    }
}