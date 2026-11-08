package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.InputGate;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.core.MovementValues;
import yagen.waitmydawn.ninjas_free_play.core.ParkourFrame;
import yagen.waitmydawn.ninjas_free_play.network.MovementInputPayload;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;

import java.util.ArrayDeque;
import java.util.Deque;

@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class ClientMovementEvents {
    private record Pending(MovementInput input, boolean eligible, boolean grounded, boolean canStand, boolean canRoll,
                           long tick, MovementValues values, ParkourFrame frame) {
    }

    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static LocalPlayer target;
    private static InputGate gate = new InputGate();
    private static long sequence;
    private static long acknowledged = -1;
    private static long lastSentTick = -20;
    private static InputGate.Buttons lastSent = new InputGate.Buttons(false, false);

    private ClientMovementEvents() {
    }

    /**
     * Called after vanilla flight/elytra decisions and before LivingEntity's jump and travel sections.
     */
    public static boolean beforeTravel(LocalPlayer player) {
        ensureTarget(player);
        Minecraft minecraft = Minecraft.getInstance();
        boolean eligible = MovementPhysics.eligible(player);
        boolean active = minecraft.screen == null && minecraft.isWindowActive() && !minecraft.isPaused();
        boolean crouch = KeyMappings.CROUCH.isDown();
        boolean grounded = MovementPhysics.grounded(player);
        boolean compactAction = crouch || MovementPhysics.low(player)
                || MovementPhysics.controller(player).posture().rolling(player.tickCount);
        // Airborne and compact actions require the bound key; ordinary ground auto-jump remains available.
        boolean jump = compactAction || !grounded ? minecraft.options.keyJump.isDown() : player.input.jumping;
        var buttons = gate.sample(active && eligible, crouch, jump,
                KeyMappings.ROLL.isDown(), minecraft.options.keySprint.isDown() || player.isSprinting(),
                (player.input.left ? 1 : 0) - (player.input.right ? 1 : 0),
                (player.input.up ? 1 : 0) - (player.input.down ? 1 : 0), minecraft.options.keyUse.isDown());
        var controller = MovementPhysics.controller(player);
        MovementPhysics.observe(player);
        ParkourPhysics.update(player);
        boolean wallRepeat = buttons.jump() && !buttons.crouch() && !grounded && controller.canRepeatWallJump()
                && controller.parkour().canWallJump(player.tickCount)
                && ParkourPhysics.frame(player, true).wall().present();
        boolean changed = !buttons.equals(lastSent);
        boolean heartbeat = (buttons.crouch() || buttons.jump() || buttons.roll() || buttons.aim()) && player.tickCount - lastSentTick >= 20;
        if ((changed || heartbeat || wallRepeat || lastSentTick < 0) && PENDING.size() < 256) {
            submit(player, buttons, eligible, grounded);
        }
        MovementPhysics.applyCrouch(player);
        MovementActionHooks.reconcile(player);
        // Sprint checks consume ordinary input earlier in aiStep; sliding travel remains momentum-driven.
        if (controller.parkour().mode() == yagen.waitmydawn.ninjas_free_play.core.ParkourMode.SLIDE) {
            player.xxa = 0;
            player.zza = 0;
        }
        return eligible;
    }

    private static void submit(LocalPlayer player, InputGate.Buttons buttons, boolean eligible, boolean grounded) {
        var values = NAttributes.read(player);
        var input = new MovementInput(sequence++, buttons.crouch(), buttons.jump(), player.getYRot(), player.getXRot(),
                buttons.roll(), buttons.sprint(), buttons.strafe(), buttons.forward(), buttons.aim());
        boolean canStand = MovementPhysics.canStand(player);
        boolean canRoll = MovementPhysics.canRoll(player, input);
        var frame = ParkourPhysics.frame(player, input.aim() || input.jump());
        var decision = MovementPhysics.controller(player).accept(input, eligible, grounded, canStand, canRoll, player.tickCount, values, frame);
        PENDING.addLast(new Pending(input, eligible, grounded, canStand, canRoll, player.tickCount, values, frame));
        MovementPhysics.applyCrouch(player);
        MovementPhysics.applyJump(player, decision.jump(), input, values);
        if (decision.rollStarted()) MovementPhysics.applyRoll(player);
        if (decision.slideStarted()) ParkourPhysics.applySlide(player, frame, values);
        MovementActionHooks.reconcile(player);
        NetworkHandler.sendToServer(new MovementInputPayload(input, decision.jump(), MovementPhysics.customJumpPower(decision.jump(), values),
                decision.jump() == JumpKind.BULLET ? values.bulletLiftPerTick() : 0, decision.rollStarted(),
                decision.rollStarted() ? values.rollPerTick() : 0, MovementPhysics.controller(player).parkour().mode(),
                frame.wall().x(), frame.wall().z(), decision.slideStarted(),
                decision.slideStarted() ? values.get(MovementAttribute.SLIDE_SPEED_MULTIPLIER) : 0,
                decision.slideStarted() ? player.getDeltaMovement().horizontalDistance() : 0));
        lastSent = buttons;
        lastSentTick = player.tickCount;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        if (minecraft.screen == null && minecraft.isWindowActive() && !minecraft.isPaused()) return;
        LocalPlayer player = minecraft.player;
        ensureTarget(player);
        var released = gate.sample(false, false, minecraft.options.keyJump.isDown());
        if (!released.equals(lastSent) && PENDING.size() < 256) {
            submit(player, released, MovementPhysics.eligible(player), MovementPhysics.grounded(player));
        }
    }

    public static void receive(MovementStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Player player = minecraft.level.getPlayerByUUID(payload.player());
        if (player == null) return;
        var controller = MovementPhysics.controller(player);
        if (player != minecraft.player) {
            controller.restore(payload.state(), player.tickCount);
            MovementPhysics.applyCrouch(player);
            MovementActionHooks.reconcile(player);
            return;
        }
        ensureTarget(minecraft.player);
        if (payload.state().acknowledgedSequence() < acknowledged) return;
        acknowledged = payload.state().acknowledgedSequence();
        while (!PENDING.isEmpty() && PENDING.getFirst().input().sequence() <= acknowledged) PENDING.removeFirst();
        controller.restore(payload.state(), player.tickCount);
        if (payload.correctMotion()) {
            player.setDeltaMovement(payload.velocity());
        }
        // Reconcile resources without reapplying already predicted impulses on ordinary acknowledgements.
        for (Pending pending : PENDING) {
            var decision = controller.accept(pending.input(), pending.eligible(), pending.grounded(), pending.canStand(), pending.canRoll(), pending.tick(), pending.values(), pending.frame());
            if (payload.correctMotion() && decision.jump() != JumpKind.NONE) {
                MovementPhysics.applyJump(player, decision.jump(), pending.input(), pending.values());
            }
            if (payload.correctMotion() && decision.rollStarted()) MovementPhysics.applyRoll(player);
            // Reference reservations replay even when an ordinary acknowledgement must not reapply motion.
            if (decision.slideStarted())
                ParkourPhysics.applySlide(player, pending.frame(), pending.values(), payload.correctMotion());
        }
        MovementPhysics.observe(player);
        MovementPhysics.applyCrouch(player);
        MovementActionHooks.reconcile(player);
    }

    @SubscribeEvent
    public static void actionTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        for (Player player : minecraft.level.players()) MovementActionHooks.tick(player);
    }

    @SubscribeEvent
    public static void movementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!MovementPhysics.low(player) && !MovementPhysics.controller(player).posture().rolling(player.tickCount)
                && MovementPhysics.controller(player).parkour().mode() == yagen.waitmydawn.ninjas_free_play.core.ParkourMode.NONE)
            return;
        var input = event.getInput();
        double multiplier = NAttributes.read(player).get(MovementAttribute.CROUCH_SPEED_MULTIPLIER);
        if (MovementPhysics.controller(player).posture().rolling(player.tickCount)) multiplier = 0;
        else if (MovementPhysics.controller(player).parkour().mode() == yagen.waitmydawn.ninjas_free_play.core.ParkourMode.SLIDE)
            multiplier = 1;
        else if (MovementPhysics.controller(player).parkour().mode() == yagen.waitmydawn.ninjas_free_play.core.ParkourMode.AIM_GLIDE)
            multiplier = NAttributes.read(player).get(MovementAttribute.GLIDE_CONTROL_MULTIPLIER);
        else if (MovementPhysics.controller(player).parkour().mode() != yagen.waitmydawn.ninjas_free_play.core.ParkourMode.NONE)
            multiplier = 0;
        else if (MovementPhysics.controller(player).bulletJumping() && !MovementPhysics.controller(player).crouchHeld())
            multiplier = 1;
        else if ((Minecraft.getInstance().options.keySprint.isDown() || player.isSprinting()) && MovementPhysics.canStand(player))
            multiplier = 1;
        if (input.shiftKeyDown) multiplier = Math.min(multiplier, VanillaMovement.sneakMultiplier(player));
        input.leftImpulse = (float) (((input.left ? 1 : 0) - (input.right ? 1 : 0)) * multiplier);
        input.forwardImpulse = (float) (((input.up ? 1 : 0) - (input.down ? 1 : 0)) * multiplier);
    }

    private static void ensureTarget(LocalPlayer player) {
        if (target == player) return;
        target = player;
        gate = new InputGate();
        gate.sample(false, false, false);
        PENDING.clear();
        acknowledged = -1;
        lastSentTick = -20;
        lastSent = new InputGate.Buttons(false, false);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        target = null;
        sequence = 0;
        PENDING.clear();
    }
}
