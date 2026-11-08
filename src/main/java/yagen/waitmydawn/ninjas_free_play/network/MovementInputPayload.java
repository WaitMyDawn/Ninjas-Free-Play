package yagen.waitmydawn.ninjas_free_play.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.JumpKind;
import yagen.waitmydawn.ninjas_free_play.core.MovementInput;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;

public record MovementInputPayload(MovementInput input, JumpKind predictedJump, double predictedPower,
                                   double predictedLift, boolean predictedRoll, double predictedRollPower,
                                   ParkourMode predictedMode,
                                   int predictedWallX, int predictedWallZ, boolean predictedSlide,
                                   double predictedSlideMultiplier,
                                   double predictedSlideSpeed) implements CustomPacketPayload {
    public MovementInputPayload(MovementInput input, JumpKind jump, double power, double lift, boolean roll, double rollPower,
                                ParkourMode mode, int wallX, int wallZ, boolean slide, double slideMultiplier) {
        this(input, jump, power, lift, roll, rollPower, mode, wallX, wallZ, slide, slideMultiplier, 0);
    }

    public MovementInputPayload(MovementInput input, JumpKind jump, double power, double lift, boolean roll, double rollPower,
                                ParkourMode mode, int wallX, int wallZ) {
        this(input, jump, power, lift, roll, rollPower, mode, wallX, wallZ, false, 0);
    }

    public MovementInputPayload(MovementInput input, JumpKind jump, double power, double lift, boolean roll, double rollPower, ParkourMode mode) {
        this(input, jump, power, lift, roll, rollPower, mode, 0, 0);
    }

    public MovementInputPayload(MovementInput input, JumpKind jump, double power, double lift, boolean roll, double rollPower) {
        this(input, jump, power, lift, roll, rollPower, ParkourMode.NONE);
    }

    public MovementInputPayload(MovementInput input, JumpKind predictedJump, double predictedPower) {
        this(input, predictedJump, predictedPower, 0, false, 0);
    }

    public static final Type<MovementInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NinjasFreePlay.MODID, "movement_input"));
    public static final StreamCodec<FriendlyByteBuf, MovementInputPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                MovementInput input = payload.input();
                buffer.writeVarLong(input.sequence());
                buffer.writeBoolean(input.crouch());
                buffer.writeBoolean(input.jump());
                buffer.writeFloat(input.yaw());
                buffer.writeFloat(input.pitch());
                buffer.writeBoolean(input.roll());
                buffer.writeBoolean(input.sprint());
                buffer.writeFloat(input.strafe());
                buffer.writeFloat(input.forward());
                buffer.writeBoolean(input.aim());
                buffer.writeEnum(payload.predictedJump());
                buffer.writeDouble(payload.predictedPower());
                buffer.writeDouble(payload.predictedLift());
                buffer.writeBoolean(payload.predictedRoll());
                buffer.writeDouble(payload.predictedRollPower());
                buffer.writeEnum(payload.predictedMode());
                buffer.writeByte(payload.predictedWallX());
                buffer.writeByte(payload.predictedWallZ());
                buffer.writeBoolean(payload.predictedSlide());
                buffer.writeDouble(payload.predictedSlideMultiplier());
                buffer.writeDouble(payload.predictedSlideSpeed());
            }, buffer -> new MovementInputPayload(new MovementInput(buffer.readVarLong(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readBoolean()), buffer.readEnum(JumpKind.class), buffer.readDouble(),
                    buffer.readDouble(), buffer.readBoolean(), buffer.readDouble(), buffer.readEnum(ParkourMode.class), buffer.readByte(), buffer.readByte(), buffer.readBoolean(), buffer.readDouble(), buffer.readDouble()));

    public MovementInputPayload {
        if (!Double.isFinite(predictedSlideSpeed) || predictedSlideSpeed < 0
                || !Double.isFinite(predictedSlideMultiplier) || predictedSlideMultiplier < 0 || predictedSlideMultiplier > 8
                || input == null || predictedJump == null || predictedMode == null || predictedWallX < -1 || predictedWallX > 1 || predictedWallZ < -1 || predictedWallZ > 1
                || Math.abs(predictedWallX) + Math.abs(predictedWallZ) > 1 || !Double.isFinite(predictedPower) || predictedPower < 0
                || !Double.isFinite(predictedLift) || predictedLift < 0 || !Double.isFinite(predictedRollPower) || predictedRollPower < 0) {
            throw new IllegalArgumentException("Invalid movement prediction");
        }
    }

    @Override
    public Type<MovementInputPayload> type() {
        return TYPE;
    }
}
