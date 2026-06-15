package dev.whisperlyric.ingamerecipeeditor.util;

import java.util.*;
import java.util.function.Function;

/**
 * 拼音搜索助手 - 通用的拼音搜索工具类
 * 支持中文、拼音、首字母、mod过滤等多种搜索方式
 */
public class PinyinSearchHelper<T> {
    
    private static boolean pinyinAvailable = false;
    private static boolean pinyinChecked = false;
    
    static {
        checkPinyinAvailability();
    }
    
    private static void checkPinyinAvailability() {
        if (pinyinChecked) return;
        pinyinChecked = true;
        try {
            Class.forName("com.github.promeg.pinyinhelper_fork.Pinyin");
            pinyinAvailable = true;
        } catch (ClassNotFoundException e) {
            pinyinAvailable = false;
        }
    }
    
    private final Map<T, PinyinInfo> pinyinCache = new HashMap<>();
    private final Function<T, String> displayNameGetter;
    private final Function<T, String> idGetter;
    
    /**
     * 拼音信息类
     */
    public static class PinyinInfo {
        public final String fullPinyin;
        public final String initials;
        public final String nospace;
        
        public PinyinInfo(String fullPinyin, String initials, String nospace) {
            this.fullPinyin = fullPinyin;
            this.initials = initials;
            this.nospace = nospace;
        }
    }
    
    /**
     * 搜索过滤器类
     */
    public static class SearchFilter {
        public final String searchText;
        public final String modFilter;
        
        public SearchFilter(String searchText, String modFilter) {
            this.searchText = searchText;
            this.modFilter = modFilter;
        }
    }
    
    /**
     * 构造函数
     * @param displayNameGetter 获取显示名称的函数
     * @param idGetter 获取ID的函数（用于mod过滤）
     */
    public PinyinSearchHelper(Function<T, String> displayNameGetter, Function<T, String> idGetter) {
        this.displayNameGetter = displayNameGetter;
        this.idGetter = idGetter;
    }
    
    /**
     * 构建拼音缓存
     */
    public void buildCache(Collection<T> items) {
        pinyinCache.clear();
        for (T item : items) {
            String displayName = displayNameGetter.apply(item);
            PinyinInfo pinyinInfo = convertToPinyinInfo(displayName);
            pinyinCache.put(item, pinyinInfo);
        }
    }
    
    /**
     * 获取缓存的拼音信息
     */
    public PinyinInfo getPinyinInfo(T item) {
        return pinyinCache.get(item);
    }
    
    /**
     * 将字符串转换为拼音信息
     */
    public static PinyinInfo convertToPinyinInfo(String text) {
        if (text == null || text.isEmpty()) {
            return new PinyinInfo("", "", "");
        }
        
        if (!pinyinAvailable) {
            return new PinyinInfo(text.toLowerCase(), text.toLowerCase(), text.toLowerCase());
        }
        
        try {
            StringBuilder fullPinyinBuilder = new StringBuilder();
            StringBuilder initials = new StringBuilder();
            StringBuilder nospace = new StringBuilder();
            
            for (char c : text.toCharArray()) {
                if (com.github.promeg.pinyinhelper_fork.Pinyin.isChinese(c)) {
                    String pinyin = com.github.promeg.pinyinhelper_fork.Pinyin.toPinyin(c).toLowerCase();
                    if (!pinyin.isEmpty()) {
                        if (fullPinyinBuilder.length() > 0) {
                            fullPinyinBuilder.append(' ');
                        }
                        fullPinyinBuilder.append(pinyin);
                        initials.append(pinyin.charAt(0));
                        nospace.append(pinyin);
                    }
                } else if (c == ' ' || c == '_' || c == '-' || c == '.') {
                    if (fullPinyinBuilder.length() > 0) {
                        fullPinyinBuilder.append(' ');
                    }
                } else if (Character.isLetterOrDigit(c)) {
                    // 普通字母数字：添加到所有结果
                    char lowerC = Character.toLowerCase(c);
                    if (fullPinyinBuilder.length() > 0) {
                        fullPinyinBuilder.append(' ');
                    }
                    fullPinyinBuilder.append(lowerC);
                    initials.append(lowerC);
                    nospace.append(lowerC);
                }
                // 其他特殊字符（如括号、符号）忽略
            }
            
            return new PinyinInfo(fullPinyinBuilder.toString(), initials.toString(), nospace.toString());
            
        } catch (Exception e) {
            return new PinyinInfo(text.toLowerCase(), text.toLowerCase(), text.toLowerCase());
        }
    }
    
    /**
     * 检查字符串是否包含中文字符
     */
    public static boolean containsChinese(String text) {
        if (text == null) return false;
        if (!pinyinAvailable) return false;
        
        try {
            for (char c : text.toCharArray()) {
                if (com.github.promeg.pinyinhelper_fork.Pinyin.isChinese(c)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
    
    /**
     * 解析mod过滤器
     */
    public static SearchFilter parseModFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new SearchFilter("", null);
        }
        
        String trimmed = searchText.trim();
        
        int atIndex = trimmed.indexOf('@');
        if (atIndex != -1) {
            String modPart = trimmed.substring(atIndex + 1);
            String textPart = trimmed.substring(0, atIndex).trim();
            String modName = normalizeModName(modPart.toLowerCase());
            return new SearchFilter(textPart, modName);
        }
        
        return new SearchFilter(trimmed, null);
    }
    
    /**
     * 标准化mod名称，处理常见别名
     */
    private static String normalizeModName(String modName) {
        return switch (modName) {
            case "mc", "minec", "minecraft" -> "minecraft";
            case "forge" -> "forge";
            default -> modName;
        };
    }
    
    /**
     * 检查对象是否匹配搜索条件
     */
    public boolean matches(T item, String searchText) {
        SearchFilter filter = parseModFilter(searchText);
        
        // 检查mod过滤
        if (filter.modFilter != null) {
            String id = idGetter.apply(item);
            if (id == null || !id.toLowerCase().contains(filter.modFilter)) {
                return false;
            }
        }
        
        if (filter.searchText.isEmpty()) {
            return true;
        }
        
        String lowerSearch = filter.searchText.toLowerCase().trim();
        
        // 检查是否为截断搜索
        boolean isSuffixTruncate = lowerSearch.endsWith("%");
        if (isSuffixTruncate) {
            String searchPrefix = lowerSearch.substring(0, lowerSearch.length() - 1);
            if (searchPrefix.isEmpty()) {
                return true;
            }
            
            // 后截断：搜索词后面不能有新字开始
            PinyinInfo pinyinInfo = pinyinCache.get(item);
            if (pinyinInfo != null) {
                // 无空格拼音后截断匹配（精确结尾）
                if (pinyinInfo.nospace.endsWith(searchPrefix)) {
                    return true;
                }
                
                // 检查搜索词是否匹配到最后一个字的拼音范围内
                // 仅当搜索词长度小于 nospace 长度时才进行部分匹配
                if (searchPrefix.length() < pinyinInfo.nospace.length()) {
                    String[] pinyinWords = pinyinInfo.fullPinyin.split("\\s+");
                    if (pinyinWords.length > 0) {
                        // 计算最后一个字拼音在 nospace 中的起始位置
                        int lastCharStartIndex = 0;
                        for (int i = 0; i < pinyinWords.length - 1; i++) {
                            lastCharStartIndex += pinyinWords[i].length();
                        }
                        
                        // 如果搜索词长度 >= 最后一个字起始位置，则搜索词包含了最后一个字的拼音开头
                        // 后面剩余的部分属于最后一个字，不是新字
                        if (searchPrefix.length() >= lastCharStartIndex && 
                            pinyinInfo.nospace.startsWith(searchPrefix)) {
                            return true;
                        }
                        
                        // 完整拼音后截断匹配（最后一个拼音词匹配）
                        if (pinyinWords[pinyinWords.length - 1].equals(searchPrefix)) {
                            return true;
                        }
                    }
                }
                
                // 首字母后截断匹配
                if (pinyinInfo.initials.endsWith(searchPrefix)) {
                    return true;
                }
            }
            
            // 直接文本后截断匹配
            String displayName = displayNameGetter.apply(item);
            if (displayName != null && displayName.toLowerCase().endsWith(searchPrefix)) {
                return true;
            }
            
            String id = idGetter.apply(item);
            if (id != null && id.toLowerCase().endsWith(searchPrefix)) {
                return true;
            }
            
            return false;
        }
        
        // 检查ID匹配
        String id = idGetter.apply(item);
        if (id != null && id.toLowerCase().contains(lowerSearch)) {
            return true;
        }
        
        // 检查显示名称匹配
        String displayName = displayNameGetter.apply(item);
        if (displayName != null && displayName.toLowerCase().contains(lowerSearch)) {
            return true;
        }
        
        // 检查拼音匹配
        PinyinInfo pinyinInfo = pinyinCache.get(item);
        if (pinyinInfo != null) {
            // 完整拼音匹配
            if (pinyinInfo.fullPinyin.contains(lowerSearch)) {
                return true;
            }
            // 首字母匹配
            if (pinyinInfo.initials.contains(lowerSearch)) {
                return true;
            }
            // 无空格拼音匹配
            if (pinyinInfo.nospace.contains(lowerSearch)) {
                return true;
            }
            
            // 多词匹配（支持空格分隔的拼音搜索）
            String[] searchWords = lowerSearch.split("\\s+");
            String[] pinyinWords = pinyinInfo.fullPinyin.split("\\s+");
            
            if (searchWords.length > 1 && pinyinWords.length >= searchWords.length) {
                boolean allMatch = true;
                for (String searchWord : searchWords) {
                    boolean found = false;
                    for (String pinyinWord : pinyinWords) {
                        if (pinyinWord.startsWith(searchWord)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 过滤集合中的对象
     */
    public List<T> filter(Collection<T> items, String searchText) {
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (matches(item, searchText)) {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        pinyinCache.clear();
    }
    
    /**
     * 直接匹配文本（不依赖缓存的对象）
     * 用于搜索标签内包含的所有对象名称
     * 
     * 支持截断搜索：
     * - xiajie% 表示后截断：匹配以"xiajie"结尾的拼音，后面不能有其他字
     */
    public boolean matchesText(String text, String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return true;
        }
        
        String lowerSearch = searchText.toLowerCase().trim();
        String lowerText = text.toLowerCase();
        
        // 检查是否为截断搜索
        boolean isSuffixTruncate = lowerSearch.endsWith("%");
        if (isSuffixTruncate) {
            String searchPrefix = lowerSearch.substring(0, lowerSearch.length() - 1);
            if (searchPrefix.isEmpty()) {
                return true;
            }
            
            // 后截断：搜索词后面不能有新字开始
            PinyinInfo pinyinInfo = convertToPinyinInfo(text);
            
            // 无空格拼音后截断匹配（精确结尾）
            if (pinyinInfo.nospace.endsWith(searchPrefix)) {
                return true;
            }
            
            // 检查搜索词是否匹配到最后一个字的拼音范围内
            // 仅当搜索词长度小于 nospace 长度时才进行部分匹配
            if (searchPrefix.length() < pinyinInfo.nospace.length()) {
                String[] pinyinWords = pinyinInfo.fullPinyin.split("\\s+");
                if (pinyinWords.length > 0) {
                    // 计算最后一个字拼音在 nospace 中的起始位置
                    int lastCharStartIndex = 0;
                    for (int i = 0; i < pinyinWords.length - 1; i++) {
                        lastCharStartIndex += pinyinWords[i].length();
                    }
                    
                    // 如果搜索词长度 >= 最后一个字起始位置，则搜索词包含了最后一个字的拼音开头
                    if (searchPrefix.length() >= lastCharStartIndex && 
                        pinyinInfo.nospace.startsWith(searchPrefix)) {
                        return true;
                    }
                    
                    // 完整拼音后截断匹配（最后一个拼音词匹配）
                    if (pinyinWords[pinyinWords.length - 1].equals(searchPrefix)) {
                        return true;
                    }
                }
            }
            
            // 首字母后截断匹配
            if (pinyinInfo.initials.endsWith(searchPrefix)) {
                return true;
            }
            
            // 直接文本后截断匹配
            if (lowerText.endsWith(searchPrefix)) {
                return true;
            }
            
            return false;
        }
        
        // 直接文本匹配
        if (lowerText.contains(lowerSearch)) {
            return true;
        }
        
        // 拼音匹配
        PinyinInfo pinyinInfo = convertToPinyinInfo(text);
        
        // 完整拼音匹配
        if (pinyinInfo.fullPinyin.contains(lowerSearch)) {
            return true;
        }
        // 首字母匹配
        if (pinyinInfo.initials.contains(lowerSearch)) {
            return true;
        }
        // 无空格拼音匹配
        if (pinyinInfo.nospace.contains(lowerSearch)) {
            return true;
        }
        
        return false;
    }
}