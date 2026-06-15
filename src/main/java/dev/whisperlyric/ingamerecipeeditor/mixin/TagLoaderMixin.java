package dev.whisperlyric.ingamerecipeeditor.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileReader;
import java.util.*;

@Mixin(TagLoader.class)
public class TagLoaderMixin {
    @Shadow
    @Final
    private String directory;
    
    @Unique
    private static final Logger ingamerecipeeditor$LOGGER = LogUtils.getLogger();
    @Unique
    private static final Gson ingamerecipeeditor$GSON = new Gson();
    @Unique
    private static final String ingamerecipeeditor$CUSTOM_TAGS_DIR = getCustomTagsDir();

    @Inject(
            method = "load",
            at = @At("RETURN")
    )
    private void ingamerecipeeditor$injectCustomTags(ResourceManager resourceManager,
                                  CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> cir) {
        try {
            String tagType = ingamerecipeeditor$extractTagType();
            if (tagType == null) return;
            
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> originalTags = cir.getReturnValue();
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> customTags = ingamerecipeeditor$loadCustomTags(tagType);

            if (!customTags.isEmpty()) {
                for (Map.Entry<ResourceLocation, List<TagLoader.EntryWithSource>> entry : customTags.entrySet()) {
                    ResourceLocation tagId = entry.getKey();
                    List<TagLoader.EntryWithSource> customEntries = entry.getValue();
                    originalTags.merge(tagId, customEntries, (existing, custom) -> {
                        List<TagLoader.EntryWithSource> merged = new ArrayList<>(existing);
                        merged.addAll(custom);
                        return merged;
                    });
                }
            }

        } catch (Exception e) {
            ingamerecipeeditor$LOGGER.error("注入自定义标签失败", e);
        }
    }

    @Unique
    private String ingamerecipeeditor$extractTagType() {
        if (directory == null || !directory.startsWith("tags/")) return null;
        return directory.substring("tags/".length());
    }

    @Unique
    private Map<ResourceLocation, List<TagLoader.EntryWithSource>> ingamerecipeeditor$loadCustomTags(String tagType) {
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags = new HashMap<>();

        File tagsDir = new File(ingamerecipeeditor$CUSTOM_TAGS_DIR);
        if (!tagsDir.exists()) {
            ingamerecipeeditor$LOGGER.debug("自定义标签目录不存在: {}", ingamerecipeeditor$CUSTOM_TAGS_DIR);
            return tags;
        }

        File[] namespaceDirs = tagsDir.listFiles(File::isDirectory);
        if (namespaceDirs == null) return tags;

        for (File namespaceDir : namespaceDirs) {
            String namespace = namespaceDir.getName();
            ingamerecipeeditor$loadNamespaceTags(namespace, namespaceDir, tagType, tags);
        }

        return tags;
    }

    @Unique
    private void ingamerecipeeditor$loadNamespaceTags(String namespace, File namespaceDir, String tagType,
                                   Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        File typeDir = new File(namespaceDir, tagType);
        if (typeDir.exists() && typeDir.isDirectory()) {
            ingamerecipeeditor$loadTagFiles(namespace, tagType, typeDir, tags);
        }
    }

    @Unique
    private void ingamerecipeeditor$loadTagFiles(String namespace, String type, File directory,
                              Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        List<File> tagFiles = ingamerecipeeditor$scanTagFiles(directory);
        for (File tagFile : tagFiles) {
            try {
                String tagPath = ingamerecipeeditor$getTagPathFromFile(directory, tagFile);
                ResourceLocation tagId = ResourceLocation.tryParse(namespace + ":" + tagPath);
                String source = "ingamerecipeeditor:custom/" + namespace + "/" + type + "/" + tagPath;

                try (FileReader reader = new FileReader(tagFile)) {
                    JsonObject tagJson = ingamerecipeeditor$GSON.fromJson(reader, JsonObject.class);

                    if (tagJson != null && tagJson.has("values")) {
                        List<TagLoader.EntryWithSource> entries = ingamerecipeeditor$parseTagEntries(tagJson, source);
                        if (!entries.isEmpty()) tags.put(tagId, entries);
                    }
                }

            } catch (Exception e) {
                ingamerecipeeditor$LOGGER.error("加载标签文件失败: " + tagFile.getPath(), e);
            }
        }
    }

    @Unique
    private List<TagLoader.EntryWithSource> ingamerecipeeditor$parseTagEntries(JsonObject tagJson, String source) {
        List<TagLoader.EntryWithSource> entries = new ArrayList<>();
        JsonArray valuesArray = tagJson.getAsJsonArray("values");

        for (JsonElement element : valuesArray) {
            try {
                if (element.isJsonPrimitive()) {
                    String value = element.getAsString();
                    entries.add(ingamerecipeeditor$createTagEntry(value, source, false));
                } else if (element.isJsonObject()) {
                    JsonObject entryObj = element.getAsJsonObject();
                    String value = entryObj.get("id").getAsString();
                    boolean required = entryObj.has("required") ?
                            entryObj.get("required").getAsBoolean() : false;
                    entries.add(ingamerecipeeditor$createTagEntry(value, source, required));
                }
            } catch (Exception e) {
                ingamerecipeeditor$LOGGER.error("解析标签条目失败: " + element, e);
            }
        }

        return entries;
    }

    @Unique
    private TagLoader.EntryWithSource ingamerecipeeditor$createTagEntry(String value, String source, boolean required) {
        TagEntry entry;

        if (value.startsWith("#")) {
            ResourceLocation tagRef = ResourceLocation.parse(value.substring(1));
            entry = required ? TagEntry.tag(tagRef) : TagEntry.optionalTag(tagRef);
        } else {
            ResourceLocation itemId = ResourceLocation.parse(value);
            entry = required ? TagEntry.element(itemId) : TagEntry.optionalElement(itemId);
        }

        return new TagLoader.EntryWithSource(entry, source);
    }

    @Unique
    private static String getCustomTagsDir() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("ingamerecipeeditor/custom_tags")
                .toAbsolutePath()
                .toString();
    }

    @Unique
    private List<File> ingamerecipeeditor$scanTagFiles(File directory) {
        List<File> files = new ArrayList<>();
        File[] children = directory.listFiles();
        if (children == null) return files;

        for (File child : children) {
            if (child.isDirectory()) files.addAll(ingamerecipeeditor$scanTagFiles(child));
            else if (child.getName().endsWith(".json")) files.add(child);
        }

        return files;
    }

    @Unique
    private String ingamerecipeeditor$getTagPathFromFile(File baseDir, File tagFile) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = tagFile.getAbsolutePath();
        String relativePath = filePath.substring(basePath.length() + 1);
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }
        return relativePath.replace(File.separatorChar, '/');
    }
}