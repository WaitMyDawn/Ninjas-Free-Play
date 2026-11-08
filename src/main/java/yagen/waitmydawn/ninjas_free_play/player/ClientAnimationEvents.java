package yagen.waitmydawn.ninjas_free_play.player;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.render.AnimationPreviewController;
import yagen.waitmydawn.ninjas_free_play.render.AnimationResources;

@EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class ClientAnimationEvents {
    private ClientAnimationEvents() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        var clip = Commands.argument("clip", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder));
        event.getDispatcher().register(Commands.literal("nfp").then(Commands.literal("animation")
                .then(Commands.literal("play").then(clip.executes(context -> start(
                        ResourceLocationArgument.getId(context, "clip"), null))))
                .then(Commands.literal("sample").then(Commands.argument("clip", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 600))
                                .executes(context -> start(ResourceLocationArgument.getId(context, "clip"),
                                        DoubleArgumentType.getDouble(context, "seconds"))))))
                .then(Commands.literal("chain").then(Commands.argument("first", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                        .then(Commands.argument("second", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        AnimationResources.INSTANCE.ids().stream().map(ResourceLocation::toString), builder))
                                .then(Commands.argument("interval", DoubleArgumentType.doubleArg(0.05, 600))
                                        .executes(context -> chain(ResourceLocationArgument.getId(context, "first"),
                                                ResourceLocationArgument.getId(context, "second"),
                                                DoubleArgumentType.getDouble(context, "interval")))))))
                .then(Commands.literal("view").then(Commands.argument("degrees", DoubleArgumentType.doubleArg(-360, 360))
                        .executes(context -> {
                            double degrees = DoubleArgumentType.getDouble(context, "degrees");
                            AnimationPreviewController.setViewDegrees(degrees);
                            context.getSource().sendSuccess(() -> Component.translatable("command.ninjas_free_play.animation.view", degrees), false);
                            return 1;
                        })))
                .then(Commands.literal("aim")
                        .then(Commands.literal("reset").executes(context -> {
                            AnimationPreviewController.setLaunchPitch(null);
                            return 1;
                        }))
                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg(-90, 90)).executes(context -> {
                            double pitch = DoubleArgumentType.getDouble(context, "pitch");
                            AnimationPreviewController.setLaunchPitch(pitch);
                            context.getSource().sendSuccess(() -> Component.translatable("command.ninjas_free_play.animation.aim", pitch), false);
                            return 1;
                        })))
                .then(Commands.literal("stop").executes(context -> {
                    AnimationPreviewController.stop();
                    context.getSource().sendSuccess(() -> Component.translatable("command.ninjas_free_play.animation.stopped"), false);
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        AnimationPreviewController.cleanup();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AnimationPreviewController.clear();
    }
}
