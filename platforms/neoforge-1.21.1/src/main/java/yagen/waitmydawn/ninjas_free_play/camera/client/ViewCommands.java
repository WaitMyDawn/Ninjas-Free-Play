package yagen.waitmydawn.ninjas_free_play.camera.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.camera.core.ViewCycle;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;

import java.util.stream.IntStream;

/**
 * Client-only preferences remain available without operator permissions or server command traffic.
 */
@EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class ViewCommands {
    private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
            value -> Component.translatable("command.ninjas_free_play.view.invalid", value));

    private ViewCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nfp").then(Commands.literal("view")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable("command.ninjas_free_play.view.current",
                                    ClientConfigs.CAMERA_VIEW_CYCLE.get()), false);
                            return 1;
                        })
                        .then(Commands.argument("mask", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(IntStream.range(1, 1 << ViewCycle.ORDER.size())
                                        .mapToObj(bits -> String.format("%5s", Integer.toBinaryString(bits)).replace(' ', '0')), builder))
                                .executes(context -> {
                                    String mask = StringArgumentType.getString(context, "mask");
                                    if (!ViewCycle.valid(mask)) throw INVALID.create(mask);
                                    ClientConfigs.CAMERA_VIEW_CYCLE.set(mask);
                                    ClientConfigs.SPEC.save();
                                    ShoulderCamera.refreshCycle();
                                    context.getSource().sendSuccess(() -> Component.translatable("command.ninjas_free_play.view.updated", mask), false);
                                    return 1;
                                })))
                .then(Commands.literal("reverse")
                        .executes(context -> reverse(context.getSource(), true, true))
                        .then(Commands.literal("auto").executes(context -> reverse(context.getSource(), true, false)))
                        .then(Commands.literal("fix").executes(context -> reverse(context.getSource(), false, true)))));
    }

    private static int reverse(CommandSourceStack source, boolean automatic, boolean fixed) {
        if (automatic) ClientConfigs.CAMERA_HORIZONTAL_OFFSET.set(-ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get());
        if (fixed)
            ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.set(-ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get());
        ClientConfigs.SPEC.save();
        source.sendSuccess(() -> Component.translatable("command.ninjas_free_play.reverse.updated",
                Component.translatable("camera.ninjas_free_play.shoulder"), side(ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get()),
                Component.translatable("camera.ninjas_free_play.fixed"), side(ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get())), false);
        return 1;
    }

    private static Component side(double horizontal) {
        return Component.translatable("command.ninjas_free_play.reverse." + (horizontal > 0 ? "left" : horizontal < 0 ? "right" : "center"));
    }
}
