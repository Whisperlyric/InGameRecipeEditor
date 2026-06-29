package dev.whisperlyric.ingamerecipeeditor.workspace;

import dev.whisperlyric.ingamerecipeeditor.config.ConfigManager;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理那些需要在JEI中轮换显示的槽位候选列表。实现周期性切换并在按住Shift时暂停（仿照JEI行为）。
 */
@Mod.EventBusSubscriber(modid = dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class IngredientCycleManager {

    private static final Map<IRecipeSlotDrawable, List<Candidate>> slotCandidates = Collections.synchronizedMap(new WeakHashMap<>());
    // 记录上次为每个槽位显示的索引，避免重复刷新
    private static final Map<IRecipeSlotDrawable, Integer> lastIndex = Collections.synchronizedMap(new WeakHashMap<>());
    // 标记被清除的槽位，不应该被重新注册轮换
    private static final Set<IRecipeSlotDrawable> clearedSlots = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    // 默认轮换周期和tick间隔由配置提供
    // tickInterval: 以客户端tick为单位，控制更新频率以降低开销（例如每5 tick更新一次）
    private static long getCycleMs() { return Math.max(100L, ConfigManager.get().cycleMs); }
    private static int getTickInterval() { return Math.max(1, ConfigManager.get().tickInterval); }

    private static int tickCounter;

    private record Candidate(IIngredientType<Object> type, Object value) {}

    /**
     * 注册某个槽位的候选列表并立即把当前时间对应的候选显示出来。
     */
    public static void registerSlotCandidates(IRecipeSlotDrawable slot, List<IIngredientType<?>> types, List<Object> values) {
        if (slot == null || types == null || values == null) return;
        if (types.size() != values.size()) return;
        if (values.size() <= 1) return;
        
        // 如果槽位被标记为清除，不重新注册轮换
        if (clearedSlots.contains(slot)) {
            return;
        }

        List<Candidate> list = new CopyOnWriteArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            @SuppressWarnings("unchecked") IIngredientType<Object> t = (IIngredientType<Object>) types.get(i);
            list.add(new Candidate(t, values.get(i)));
        }

        // 如果已经注册并且候选集合未发生变化，则跳过重新注册，避免每帧重置
        synchronized (slotCandidates) {
            List<Candidate> existing = slotCandidates.get(slot);
            if (existing != null && existing.size() == list.size()) {
                boolean same = true;
                for (int i = 0; i < list.size(); i++) {
                    Candidate a = existing.get(i);
                    Candidate b = list.get(i);
                    Object va = a.value();
                    Object vb = b.value();
                    IIngredientType<?> ta = a.type();
                    IIngredientType<?> tb = b.type();
                    if (!Objects.equals(ta, tb) || !Objects.equals(va, vb)) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    // 已经是相同的候选集合，跳过重新注册
                    return;
                }
            }

            slotCandidates.put(slot, list);

            // 使用当前时间计算索引，与forceUpdateAllNow一致，避免首次显示第一个候选导致闪烁
            long now = System.currentTimeMillis();
            long idxBase = now / Math.max(1, getCycleMs());
            int n = list.size();
            int idx = (int) ((idxBase + (slot.hashCode() & 0xffff)) % n);
            int i = Math.floorMod(idx, n);
            try {
                updateSlotDisplayImmediate(slot, i);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * 在渲染路径强制立即更新所有注册槽位显示（用于在render阶段覆盖JEI自身的重置）
     * 但跳过被标记为清除的槽位
     */
    public static void forceUpdateAllNow() {
        if (isShiftKeyDown()) return;
        long now = System.currentTimeMillis();
        long idxBase = now / Math.max(1, getCycleMs());
        synchronized (slotCandidates) {
            for (var e : slotCandidates.entrySet()) {
                IRecipeSlotDrawable slot = e.getKey();
                List<Candidate> list = e.getValue();
                if (slot == null || list == null || list.isEmpty()) continue;
                
                // 如果槽位被标记为清除，跳过
                if (clearedSlots.contains(slot)) {
                    continue;
                }
                
                int n = list.size();
                int idx = (int) ((idxBase + (slot.hashCode() & 0xffff)) % n);
                int i = Math.floorMod(idx, n);
                Candidate c = list.get(i);
                try {
                    slot.clearDisplayOverrides();
                    var disp = slot.createDisplayOverrides();
                    disp.addIngredient(c.type, c.value);
                    lastIndex.put(slot, i);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void updateSlotDisplayImmediate(IRecipeSlotDrawable slot, int idx) {
        // 如果槽位被标记为清除，不更新显示
        if (clearedSlots.contains(slot)) {
            return;
        }
        
        List<Candidate> list = slotCandidates.get(slot);
        if (list == null || list.isEmpty()) return;
        int n = list.size();
        int i = Math.floorMod(idx, n);
        Candidate c = list.get(i);
        try {
            slot.clearDisplayOverrides();
            var disp = slot.createDisplayOverrides();
            disp.addIngredient(c.type, c.value);
            lastIndex.put(slot, i);
        } catch (Exception e) {
            // ignore
        }
    }

    public static void unregisterSlot(IRecipeSlotDrawable slot) {
        if (slot == null) return;
        slotCandidates.remove(slot);
        lastIndex.remove(slot);
        clearedSlots.remove(slot); // 移除清除标记
    }
    
    /**
     * 标记槽位为已清除，阻止重新注册轮换
     */
    public static void markSlotCleared(IRecipeSlotDrawable slot) {
        if (slot == null) return;
        clearedSlots.add(slot);
        slotCandidates.remove(slot);
        lastIndex.remove(slot);
    }
    
    /**
     * 取消槽位的清除标记，允许重新注册轮换
     */
    public static void unmarkSlotCleared(IRecipeSlotDrawable slot) {
        if (slot == null) return;
        clearedSlots.remove(slot);
    }
    
    /**
     * 清除所有轮换注册
     */
    public static void clearAllCycles() {
        synchronized (slotCandidates) {
            slotCandidates.clear();
            lastIndex.clear();
        }
        clearedSlots.clear();
    }
    
    /**
     * 检查槽位是否有轮换
     */
    public static boolean hasCycle(IRecipeSlotDrawable slot) {
        if (slot == null) return false;
        List<Candidate> list = slotCandidates.get(slot);
        return list != null && list.size() > 1;
    }
    
    /**
     * 检查槽位是否被标记为清除
     */
    public static boolean isSlotCleared(IRecipeSlotDrawable slot) {
        if (slot == null) return false;
        return clearedSlots.contains(slot);
    }

    /**
     * 获取当前槽位显示的候选值
     */
    public static Optional<Object> getCurrentDisplayedValue(IRecipeSlotDrawable slot) {
        if (slot == null) return Optional.empty();
        List<Candidate> list = slotCandidates.get(slot);
        if (list == null || list.isEmpty()) return Optional.empty();
        Integer idx = lastIndex.get(slot);
        if (idx == null) idx = 0;
        int i = Math.floorMod(idx, list.size());
        return Optional.of(list.get(i).value());
    }

    /**
     * 显式初始化调用点（由mod的client setup触发），用于确保类被类加载器加载并在日志中确认
     */
    public static void init() {
        // 事件订阅在类注解上
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // 处理JEI可见性延迟更新
        dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility.clientTick();

        // 如果按住Shift则暂停轮换
        if (Screen.hasShiftDown()) return;

        // 节省开销：按配置的 tickInterval 限制更新频率
        tickCounter++;
        int interval = Math.max(1, getTickInterval());
        if ((tickCounter % interval) != 0) return;

        long now = System.currentTimeMillis();
        long idxBase = now / Math.max(1, getCycleMs());

        // 遍历所有注册的槽位并更新其显示
        try {
            synchronized (slotCandidates) {
                var it = slotCandidates.entrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    IRecipeSlotDrawable slot = e.getKey();
                    List<Candidate> list = e.getValue();
                    if (slot == null || list == null || list.isEmpty()) {
                        it.remove();
                        continue;
                    }

                    int n = list.size();
                    int idx = (int) ((idxBase + (slot.hashCode() & 0xffff)) % n);
                    Candidate c = list.get(Math.abs(idx % n));

                    try {
                        Integer prev = lastIndex.get(slot);
                        if (prev != null && prev == idx) {
                            // index 未变，跳过刷新
                            continue;
                        }
                        slot.clearDisplayOverrides();
                        var disp = slot.createDisplayOverrides();
                        disp.addIngredient(c.type, c.value);
                        lastIndex.put(slot, idx);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 检测Shift键是否按下（使用GLFW直接检测）
     */
    private static boolean isShiftKeyDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            long window = mc.getWindow().getWindow();
            // GLFW_KEY_LEFT_SHIFT = 340, GLFW_KEY_RIGHT_SHIFT = 344
            return org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }
}


