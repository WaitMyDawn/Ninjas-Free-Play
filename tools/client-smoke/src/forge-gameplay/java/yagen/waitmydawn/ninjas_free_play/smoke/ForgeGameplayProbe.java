package yagen.waitmydawn.ninjas_free_play.smoke;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.player.ClientMovementEvents;
import yagen.waitmydawn.ninjas_free_play.player.KeyMappings;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class ForgeGameplayProbe {
    private static boolean connecting, finished, crouch, bullet, roll, glide, clientGlide;
    private static boolean autoReticle, fixedReticle, serverSlide, serverKick, kickLandedSlide;
    private static int corrections, ticks, phase, phaseTick;
    private static double slideStartZ;
    private static long acknowledged = -1;
    private static double startY, startZ, rollSpeed;
    private static int connectionAttempts, landedAt = -1;

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished || !ShoulderCamera.active()) return;
        if (phase >= 4) fixedReticle |= ShoulderCamera.reticle().visible();
        else autoReticle |= ShoulderCamera.reticle().visible();
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        minecraft.setWindowActive(true);
        minecraft.options.pauseOnLostFocus = false;
        ticks++;
        try {
            if (ticks > 2400 || connecting && ticks - phaseTick > (phase == 0 ? 1200 : 250))
                throw new IllegalStateException("Gameplay phase timed out: " + phase);
            if (!connecting) {
                if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen
                        || minecraft.screen instanceof AccessibilityOnboardingScreen)) return;
                connecting = true;
                phaseTick = ticks;
                NetworkHandler.installClientReceiver(payload -> {
                    ClientMovementEvents.receive(payload);
                    if (minecraft.player == null || !payload.player().equals(minecraft.player.getUUID())) return;
                    var state = payload.state();
                    if (phase >= 7) {
                        serverSlide |= state.parkour().mode() == ParkourMode.SLIDE;
                        if (payload.correctMotion()) corrections++;
                    }
                    if (phase >= 11) {
                        serverKick |= state.parkour().mode() == ParkourMode.FLYING_KICK;
                        kickLandedSlide |= serverKick && state.parkour().mode() == ParkourMode.SLIDE;
                        NinjasFreePlay.LOGGER.info("KICK_LANDING_SERVER mode={} intent={} grounded={} ack={}",
                                state.parkour().mode(),state.parkour().landingSlide(),state.grounded(),state.acknowledgedSequence());
                    }
                    acknowledged = Math.max(acknowledged, state.acknowledgedSequence());
                    crouch |= state.posture().crouching();
                    bullet |= state.action() == JumpKind.BULLET;
                    roll |= state.posture().rollRemaining() > 0;
                    glide |= state.parkour().glide().active();
                });
                connect(minecraft);
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) {
                if (phase == 0 && minecraft.screen instanceof DisconnectedScreen && connectionAttempts < 3) {
                    phaseTick = ticks;
                    connect(minecraft);
                }
                if (ticks % 100 == 0) NinjasFreePlay.LOGGER.info("FORGE_GAMEPLAY_CONNECTING: screen={}",
                        minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
                return;
            }
            clientGlide |= MovementPhysics.controller(player).parkour().gliding();
            int age = ticks - phaseTick;
            switch (phase) {
                case 0 -> {
                    if (player.tickCount < 20 || !player.onGround() || minecraft.screen != null) return;
                    player.setYRot(0);
                    player.setXRot(0);
                    minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera.cycle(
                            net.minecraft.client.CameraType.THIRD_PERSON_BACK, net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    KeyMappings.CROUCH.setDown(true);
                    next();
                }
                case 1 -> {
                    if (age < 12 || !autoReticle || !crouch || !MovementPhysics.low(player)) return;
                    startY = player.getY();
                    startZ = player.getZ();
                    minecraft.options.keyJump.setDown(true);
                    next();
                }
                case 2 -> {
                    if (age < 5 || !bullet || player.getY() < startY + .1) return;
                    minecraft.options.keyJump.setDown(false);
                    KeyMappings.CROUCH.setDown(false);
                    rollSpeed = player.getDeltaMovement().horizontalDistance();
                    KeyMappings.ROLL.setDown(true);
                    next();
                }
                case 3 -> {
                    if (age < 4 || !roll) return;
                    if (player.getDeltaMovement().horizontalDistance() < rollSpeed * .75)
                        throw new IllegalStateException("The air roll lost carried momentum");
                    KeyMappings.ROLL.setDown(false);
                    yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera.cycle(
                            net.minecraft.client.CameraType.THIRD_PERSON_BACK, net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    minecraft.options.keyUse.setDown(true);
                    next();
                }
                case 4 -> {
                    if (age < 5 || !glide || !clientGlide) return;
                    minecraft.options.keyUse.setDown(false);
                    next();
                }
                case 5 -> {
                    if (age < 20 || !player.onGround()) return;
                    if (acknowledged < 4 || player.getZ() - startZ < 2)
                        throw new IllegalStateException("Authoritative input acknowledgements or real travel are missing");
                    minecraft.options.keyUp.setDown(true);
                    next();
                }
                case 6 -> {
                    if (age < 35) return;
                    if (!player.onGround() || yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics.frame(player,false).speed() < .2157)
                        throw new IllegalStateException("Walking did not reach the slide entry speed");
                    slideStartZ = player.getZ();
                    KeyMappings.CROUCH.setDown(true);
                    next();
                }
                case 7 -> {
                    if (age >= 2 && MovementPhysics.controller(player).parkour().mode() != ParkourMode.SLIDE)
                        throw new IllegalStateException("Walking-to-slide lost its action at age " + age);
                    if (age < 12 || !serverSlide) return;
                    if (player.getZ()-slideStartZ < 2 || corrections > 1)
                        throw new IllegalStateException("Slide travel stalled or required repeated corrections: " + corrections);
                    KeyMappings.CROUCH.setDown(false);
                    minecraft.options.keyUp.setDown(false);
                    next();
                }
                case 8 -> {
                    if (age < 5) return;
                    if (MovementPhysics.controller(player).parkour().mode() == ParkourMode.SLIDE || !autoReticle || !fixedReticle)
                        throw new IllegalStateException("Slide release or actual camera reticle rendering failed");
                    minecraft.options.keyUp.setDown(true);
                    minecraft.options.keySprint.setDown(true);
                    next();
                }
                case 9 -> {
                    if (age < 25 || !player.onGround()) return;
                    minecraft.options.keyJump.setDown(true);
                    next();
                }
                case 10 -> {
                    if (age < 2 || player.onGround()) return;
                    minecraft.options.keyJump.setDown(false);
                    KeyMappings.CROUCH.setDown(true);
                    next();
                }
                case 11 -> {
                    NinjasFreePlay.LOGGER.info("KICK_LANDING_CLIENT age={} mode={} intent={} grounded={} speed={}",
                            age,MovementPhysics.controller(player).parkour().mode(),
                            MovementPhysics.controller(player).snapshot(player.tickCount).parkour().landingSlide(),
                            player.onGround(),yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics.frame(player,false).speed());
                    if (!serverKick || !player.onGround()) return;
                    if (landedAt < 0) landedAt = ticks;
                    if (ticks == landedAt) return;
                    if (MovementPhysics.controller(player).parkour().mode() != ParkourMode.SLIDE)
                        throw new IllegalStateException("Held-C flying kick did not become a slide on landing");
                    next();
                }
                case 12 -> {
                    if (MovementPhysics.controller(player).parkour().mode() != ParkourMode.SLIDE)
                        throw new IllegalStateException("The flying-kick landing slide was interrupted");
                    if (age < 10 || !kickLandedSlide) return;
                    KeyMappings.CROUCH.setDown(false);
                    minecraft.options.keyUp.setDown(false);
                    next();
                }
                case 13 -> {
                    if (age < 5) return;
                    if (MovementPhysics.controller(player).parkour().mode() == ParkourMode.SLIDE)
                        throw new IllegalStateException("The landing slide ignored C release");
                    finish(true, "Authoritative action chain, walking-to-slide, kick-to-slide, releases, and both rendered reticles passed");
                }
                default -> throw new IllegalStateException("Unknown phase");
            }
        } catch (Throwable failure) {
            NinjasFreePlay.LOGGER.error("FORGE_GAMEPLAY_SMOKE_FAILURE", failure);
            finish(false, failure.toString());
        }
    }

    private static void connect(Minecraft minecraft) {
        connectionAttempts++;
        String address = "127.0.0.1:" + System.getProperty("nfp.loopback.port");
        ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(address),
                new ServerData("NFP loopback", address, false), false);
    }

    private static void next() {
        phase++;
        phaseTick = ticks;
        NinjasFreePlay.LOGGER.info("FORGE_GAMEPLAY_PHASE: {} ack={}", phase, acknowledged);
    }

    private static void finish(boolean passed, String reason) {
        finished = true;
        var minecraft = Minecraft.getInstance();
        KeyMappings.CROUCH.setDown(false);
        KeyMappings.ROLL.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keySprint.setDown(false);
        try {
            var report = new LinkedHashMap<String,Object>();
            report.put("passed",passed);report.put("phase",phase);report.put("acknowledged",acknowledged);
            report.put("reason",reason);report.put("serverCrouch",crouch);report.put("serverBullet",bullet);
            report.put("serverRoll",roll);report.put("serverGlide",glide);report.put("clientGlide",clientGlide);
            report.put("autoReticle",autoReticle);report.put("fixedReticle",fixedReticle);
            report.put("serverSlide",serverSlide);report.put("slideCorrections",corrections);
            report.put("serverKick",serverKick);report.put("kickLandedSlide",kickLandedSlide);
            report.put("connectionAttempts",connectionAttempts);
            Files.writeString(Path.of(System.getProperty("nfp.gameplay.result")), new Gson().toJson(report));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        } finally {
            if (passed) NinjasFreePlay.LOGGER.info("FORGE_GAMEPLAY_SMOKE_PASS: {}", reason);
            minecraft.stop();
        }
    }
}
