package yagen.waitmydawn.ninjas_free_play.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.PostureSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.RollKind;
import yagen.waitmydawn.ninjas_free_play.core.ParkourSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.core.WallContact;
import yagen.waitmydawn.ninjas_free_play.core.GlideSnapshot;
import yagen.waitmydawn.ninjas_free_play.core.WallDashKind;
import yagen.waitmydawn.ninjas_free_play.core.WallDashPlan;
import yagen.waitmydawn.ninjas_free_play.core.SlideBoostSnapshot;

import java.util.UUID;

public record MovementStatePayload(UUID player, MovementSnapshot state, boolean correctMotion, Vec3 velocity)
        implements CustomPacketPayload {
    public static final Type<MovementStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NinjasFreePlay.MODID, "movement_state"));
    public static final StreamCodec<FriendlyByteBuf, MovementStatePayload> STREAM_CODEC = StreamCodec.of(
            MovementStatePayload::write, MovementStatePayload::read);

    public MovementStatePayload {
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            throw new IllegalArgumentException("Non-finite authoritative velocity");
        }
    }

    private static void write(FriendlyByteBuf buffer, MovementStatePayload payload) {
        MovementSnapshot state = payload.state();
        buffer.writeUUID(payload.player());
        buffer.writeVarLong(state.acknowledgedSequence());
        buffer.writeVarInt(state.jumpsUsed());
        buffer.writeBoolean(state.bulletUsed());
        buffer.writeBoolean(state.crouchHeld());
        buffer.writeBoolean(state.jumpHeld());
        buffer.writeBoolean(state.grounded());
        buffer.writeVarLong(state.actionSequence());
        buffer.writeEnum(state.action());
        buffer.writeFloat(state.yaw());
        buffer.writeFloat(state.pitch());
        buffer.writeVarInt(state.actionAge());
        var posture = state.posture();
        buffer.writeBoolean(posture.crouching());
        buffer.writeBoolean(posture.blockedLow());
        buffer.writeBoolean(posture.crouchCancelled());
        buffer.writeBoolean(posture.rollHeld());
        buffer.writeBoolean(posture.airRollUsed());
        buffer.writeVarLong(posture.rollSequence());
        buffer.writeFloat(posture.rollYaw());
        buffer.writeVarInt(posture.rollAge());
        buffer.writeVarInt(posture.rollRemaining());
        buffer.writeVarInt(posture.cooldownRemaining());
        buffer.writeVarInt(posture.rollDuration());
        buffer.writeEnum(posture.rollKind());
        buffer.writeFloat(posture.bodyYaw());
        buffer.writeDouble(posture.rollSpeed());
        var parkour = state.parkour();
        buffer.writeEnum(parkour.mode());
        buffer.writeVarLong(parkour.instance());
        buffer.writeVarInt(parkour.age());
        buffer.writeBoolean(parkour.aim());
        buffer.writeBoolean(parkour.crouch());
        buffer.writeInt(parkour.wall().x());
        buffer.writeInt(parkour.wall().z());
        buffer.writeDouble(parkour.wall().distance());
        buffer.writeBoolean(parkour.wall().latchable());
        buffer.writeVarInt(parkour.wallCooldown());
        buffer.writeDouble(parkour.assistedSeconds());
        buffer.writeEnum(parkour.wallDash().kind());
        buffer.writeByte(parkour.wallDash().normalX());
        buffer.writeByte(parkour.wallDash().normalZ());
        buffer.writeBoolean(parkour.glide().active());
        buffer.writeVarLong(parkour.glide().instance());
        buffer.writeVarInt(parkour.glide().age());
        buffer.writeBoolean(parkour.slideBoost().active());
        buffer.writeDouble(parkour.slideBoost().referenceSpeed());
        buffer.writeVarInt(parkour.slideBoost().recoveryTicks());
        buffer.writeBoolean(payload.correctMotion());
        buffer.writeDouble(payload.velocity().x);
        buffer.writeDouble(payload.velocity().y);
        buffer.writeDouble(payload.velocity().z);
    }

    private static MovementStatePayload read(FriendlyByteBuf buffer) {
        UUID player = buffer.readUUID();
        var state = new MovementSnapshot(buffer.readVarLong(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarLong(),
                buffer.readEnum(JumpKind.class), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt());
        state = state.withPosture(new PostureSnapshot(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readVarLong(), buffer.readFloat(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(RollKind.class), buffer.readFloat(), buffer.readDouble()));
        state = state.withParkour(new ParkourSnapshot(buffer.readEnum(ParkourMode.class), buffer.readVarLong(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), new WallContact(buffer.readInt(), buffer.readInt(), buffer.readDouble(), buffer.readBoolean()),
                buffer.readVarInt(), buffer.readDouble(), new WallDashPlan(buffer.readEnum(WallDashKind.class), buffer.readByte(), buffer.readByte()),
                new GlideSnapshot(buffer.readBoolean(), buffer.readVarLong(), buffer.readVarInt()),
                new SlideBoostSnapshot(buffer.readBoolean(), buffer.readDouble(), buffer.readVarInt())));
        return new MovementStatePayload(player, state, buffer.readBoolean(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    @Override
    public Type<MovementStatePayload> type() {
        return TYPE;
    }
}
