package com.wzz.registerhelper.init;

import com.mojang.blaze3d.platform.InputConstants;
import com.wzz.registerhelper.RecipeHelper;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
    
    public static final KeyMapping OPEN_RECIPE_HELPER = new KeyMapping(
            "key." + RecipeHelper.MODID + ".open_recipe_helper",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_SEMICOLON),
            "key.categories." + RecipeHelper.MODID
    );
    
    public static final KeyMapping OPEN_TAGS_OVERVIEW = new KeyMapping(
            "key." + RecipeHelper.MODID + ".open_tags_overview",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_APOSTROPHE),
            "key.categories." + RecipeHelper.MODID
    );
    
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_RECIPE_HELPER);
        event.register(OPEN_TAGS_OVERVIEW);
    }
}
