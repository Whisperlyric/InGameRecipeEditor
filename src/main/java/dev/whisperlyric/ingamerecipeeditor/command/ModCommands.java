package dev.whisperlyric.ingamerecipeeditor.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.whisperlyric.ingamerecipeeditor.jei.JeiRecipeVisibility;
import dev.whisperlyric.ingamerecipeeditor.network.NetworkHandler;
import dev.whisperlyric.ingamerecipeeditor.workspace.DebugSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor.MOD_ID;

@Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /ingamerecipeeditor DisabledRecipesJEIVisibility [true|false]
        dispatcher.register(
            Commands.literal("ingamerecipeeditor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("DisabledRecipesJEIVisibility")
                    .executes(ModCommands::queryDisabledRecipesJEIVisibility)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setDisabledRecipesJEIVisibility)
                    )
                )
                .then(Commands.literal("DebugDump")
                    .executes(ModCommands::queryDebugDump)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setDebugDump)
                    )
                )
                .then(Commands.literal("SchemaExport")
                    .executes(ModCommands::querySchemaExport)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setSchemaExport)
                    )
                )
        );

        // /igre DisabledRecipesJEIVisibility [true|false]
        dispatcher.register(
            Commands.literal("igre")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("DisabledRecipesJEIVisibility")
                    .executes(ModCommands::queryDisabledRecipesJEIVisibility)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setDisabledRecipesJEIVisibility)
                    )
                )
                .then(Commands.literal("DebugDump")
                    .executes(ModCommands::queryDebugDump)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setDebugDump)
                    )
                )
                .then(Commands.literal("SchemaExport")
                    .executes(ModCommands::querySchemaExport)
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ModCommands::setSchemaExport)
                    )
                )
        );
    }

    private static int setDisabledRecipesJEIVisibility(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        CommandSourceStack source = context.getSource();

        // 通过网络包同步到客户端（JEI是客户端模组）
        if (source.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendJeiVisibility(player, value);
        }

        if (value) {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.disabled_recipes_jei_visible_true"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.disabled_recipes_jei_visible_false"), true);
        }

        return 1;
    }

    private static int queryDisabledRecipesJEIVisibility(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean current = JeiRecipeVisibility.isShowDisabledInJei();
        source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.disabled_recipes_jei_visible_query", current), false);
        return 1;
    }

    private static int setDebugDump(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendDebugMode(player, value);
        }

        if (value) {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.debug_dump_true"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.debug_dump_false"), true);
        }
        return 1;
    }

    private static int queryDebugDump(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean current = DebugSettings.isEnabled();
        source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.debug_dump_query", current), false);
        return 1;
    }

    private static int setSchemaExport(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendSchemaExportMode(player, value);
        }

        if (value) {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.schema_export_true"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.schema_export_false"), true);
        }
        return 1;
    }

    private static int querySchemaExport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean current = DebugSettings.isSchemaExportEnabled();
        source.sendSuccess(() -> Component.translatable("ingamerecipeeditor.command.schema_export_query", current), false);
        return 1;
    }
}
