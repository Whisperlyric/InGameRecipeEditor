package com.wzz.registerhelper.network;

import com.wzz.registerhelper.gui.TagSelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenTagsOverviewPacket {
    
    public OpenTagsOverviewPacket() {
    }
    
    public static void encode(OpenTagsOverviewPacket packet, FriendlyByteBuf buf) {
    }
    
    public static OpenTagsOverviewPacket decode(FriendlyByteBuf buf) {
        return new OpenTagsOverviewPacket();
    }
    
    public static void handle(OpenTagsOverviewPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (contextSupplier.get().getDirection().getReceptionSide().isClient()) {
                doing();
            }
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void doing() {
        Minecraft.getInstance().setScreen(new TagSelectorScreen(
            Minecraft.getInstance().screen,
            tagId -> {
                Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§a已选择标签: §f#" + tagId)
                );
            }
        ));
    }
}
