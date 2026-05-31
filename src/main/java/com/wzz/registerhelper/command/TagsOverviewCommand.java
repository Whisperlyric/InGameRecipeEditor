package com.wzz.registerhelper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.OpenTagsOverviewPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class TagsOverviewCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("tagsoverview")
                .requires(source -> source.hasPermission(0))
                .executes(TagsOverviewCommand::openTagsOverview));
    }
    
    private static int openTagsOverview(CommandContext<CommandSourceStack> context) {
        try {
            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTagsOverviewPacket());
                return 1;
            } else {
                context.getSource().sendFailure(Component.literal("只有玩家可以使用此命令"));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("打开标签概览失败: " + e.getMessage()));
            return 0;
        }
    }
}
