package dev.whisperlyric.ingamerecipeeditor.event;

import dev.whisperlyric.ingamerecipeeditor.gui.DisabledRecipesListScreen;
import dev.whisperlyric.ingamerecipeeditor.gui.TagSelectorScreen;
import dev.whisperlyric.ingamerecipeeditor.init.ModKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.Key;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import static dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class KeyInputHandler {

    @SubscribeEvent
    public static void onKeyInput(Key event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.screen != null) {
            return;
        }

        if (ModKeyMappings.OPEN_DISABLED_RECIPES_LIST.consumeClick()) {
            mc.setScreen(new DisabledRecipesListScreen(null));
        }

        if (ModKeyMappings.OPEN_TAGS_OVERVIEW.consumeClick()) {
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