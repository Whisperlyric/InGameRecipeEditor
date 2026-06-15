package dev.whisperlyric.ingamerecipeeditor.init;

import com.mojang.blaze3d.platform.InputConstants;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
    
    public static final KeyMapping OPEN_TAGS_OVERVIEW = new KeyMapping(
            "key." + InGameRecipeEditor.MOD_ID + ".open_tags_overview",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_APOSTROPHE),
            "key.categories." + InGameRecipeEditor.MOD_ID
    );
    
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TAGS_OVERVIEW);
    }
}