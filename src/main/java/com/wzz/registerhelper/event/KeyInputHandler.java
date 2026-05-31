package com.wzz.registerhelper.event;

import com.wzz.registerhelper.gui.RecipeCreatorScreen;
import com.wzz.registerhelper.gui.TagSelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wzz.registerhelper.RecipeHelper.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class KeyInputHandler {
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null || mc.screen != null) {
            return;
        }
        
        if (com.wzz.registerhelper.init.ModKeyMappings.OPEN_RECIPE_HELPER.consumeClick()) {
            mc.setScreen(new RecipeCreatorScreen());
        }
        
        if (com.wzz.registerhelper.init.ModKeyMappings.OPEN_TAGS_OVERVIEW.consumeClick()) {
            mc.setScreen(new TagSelectorScreen(
                null,
                tagId -> {
                    player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§a已选择标签: §f#" + tagId)
                    );
                }
            ));
        }
    }
}
