package yagen.waitmydawn.ninjas_free_play.player;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.render.AnimationPreviewController;
import yagen.waitmydawn.ninjas_free_play.render.AnimationResources;

public final class ClientAnimationEvents {
    private ClientAnimationEvents() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var clip = ClientCommandManager.argument("clip", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder));
        dispatcher.register(ClientCommandManager.literal("nfp").then(ClientCommandManager.literal("animation")
                .then(ClientCommandManager.literal("play").then(clip.executes(context -> start(
                        context.getArgument("clip", ResourceLocation.class), null))))
                .then(ClientCommandManager.literal("sample").then(ClientCommandManager.argument("clip", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                        .then(ClientCommandManager.argument("seconds", DoubleArgumentType.doubleArg(0, 600))
                                .executes(context -> start(context.getArgument("clip", ResourceLocation.class),
                                        DoubleArgumentType.getDouble(context, "seconds"))))))
                .then(ClientCommandManager.literal("chain").then(ClientCommandManager.argument("first", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                        .then(ClientCommandManager.argument("second", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                                .then(ClientCommandManager.argument("interval", DoubleArgumentType.doubleArg(0.05, 600))
                                        .executes(context -> chain(context.getArgument("first", ResourceLocation.class),
                                                context.getArgument("second", ResourceLocation.class),
                                                DoubleArgumentType.getDouble(context, "interval")))))))
                .then(ClientCommandManager.literal("view").then(ClientCommandManager.argument("degrees", DoubleArgumentType.doubleArg(-360, 360))
                        .executes(context -> {
                            double degrees = DoubleArgumentType.getDouble(context, "degrees");
                            AnimationPreviewController.setViewDegrees(degrees);
                            context.getSource().sendFeedback(Component.translatable("command.ninjas_free_play.animation.view", degrees));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("aim")
                        .then(ClientCommandManager.literal("reset").executes(context -> {
                            AnimationPreviewController.setLaunchPitch(null);
                            return 1;
                        }))
                        .then(ClientCommandManager.argument("pitch", DoubleArgumentType.doubleArg(-90, 90)).executes(context -> {
                            double pitch = DoubleArgumentType.getDouble(context, "pitch");
                            AnimationPreviewController.setLaunchPitch(pitch);
                            context.getSource().sendFeedback(Component.translatable("command.ninjas_free_play.animation.aim", pitch));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("stop").executes(context -> {
                    AnimationPreviewController.stop();
                    context.getSource().sendFeedback(Component.translatable("command.ninjas_free_play.animation.stopped"));
                    return 1;
                }))));
    }

    private static int start(ResourceLocation id, Double seconds) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        var clip = AnimationResources.INSTANCE.get(id);
        if (!ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()) {
            player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.disabled"), false);
            return 0;
        }
        if (clip == null || seconds != null && seconds > clip.duration()) {
            player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.invalid", id.toString()), false);
            return 0;
        }
        if (seconds == null) AnimationPreviewController.play(player, clip);
        else AnimationPreviewController.freeze(player, clip, seconds);
        player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.started", id.toString()), false);
        return 1;
    }

    private static int chain(ResourceLocation first, ResourceLocation second, double interval) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        var firstClip = AnimationResources.INSTANCE.get(first);
        var secondClip = AnimationResources.INSTANCE.get(second);
        if (!ClientConfigs.ENABLE_PLAYER_ANIMATIONS.get()) {
            player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.disabled"), false);
            return 0;
        }
        if (firstClip == null || secondClip == null) {
            player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.invalid",
                    firstClip == null ? first.toString() : second.toString()), false);
            return 0;
        }
        AnimationPreviewController.chain(player, firstClip, secondClip, interval);
        player.displayClientMessage(Component.translatable("command.ninjas_free_play.animation.chain",
                first.toString(), second.toString(), interval), false);
        return 1;
    }

    public static void onClientTick() {
        AnimationPreviewController.cleanup();
    }

    public static void onLogout() {
        AnimationPreviewController.clear();
    }
}
