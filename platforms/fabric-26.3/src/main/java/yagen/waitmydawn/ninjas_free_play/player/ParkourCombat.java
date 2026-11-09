package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.core.MovementAttribute;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;

public final class ParkourCombat {
    private static final ThreadLocal<LivingEntity> DAMAGE_TARGET = new ThreadLocal<>();

    private ParkourCombat() {
    }

    public static boolean ownsDamageKnockback(LivingEntity target) {
        return DAMAGE_TARGET.get() == target;
    }

    public static void tick(Player player) {
        var runtime = ParkourPhysics.runtime(player);
        Vec3 current = player.position();
        if (runtime.combatPosition == null) runtime.combatPosition = current;
        Vec3 delta = current.subtract(runtime.combatPosition);
        if (!player.level().isClientSide() && MovementPhysics.controller(player).parkour().mode() == ParkourMode.FLYING_KICK && delta.lengthSqr() < 64) {
            AABB currentBox = player.getBoundingBox();
            Vec3 direction = delta.lengthSqr() > 1e-8 ? delta.normalize() : player.getLookAngle();
            Vec3 reach = direction.scale(.45 * player.getScale());
            AABB strike = currentBox.expandTowards(reach);
            AABB swept = strike.expandTowards(delta.scale(-1)).inflate(0.15 * player.getScale());
            for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, swept, entity -> entity != player && entity.isAlive())) {
                if (target.getBoundingBox().inflate(player.getBbWidth() / 2).clip(runtime.combatPosition.add(0, player.getBbHeight() / 2, 0),
                        current.add(0, player.getBbHeight() / 2, 0).add(reach)).isPresent() || strike.inflate(0.15).intersects(target.getBoundingBox()))
                    hit(player, target);
            }
        }
        runtime.combatPosition = current;
        if (MovementPhysics.controller(player).parkour().mode() != ParkourMode.FLYING_KICK) {
            runtime.hitTargets.clear();
            runtime.strike = -1;
        }
    }

    public static boolean hit(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || target == player || !target.isAlive() || !target.isAttackable()
                || MovementPhysics.controller(player).parkour().mode() != ParkourMode.FLYING_KICK) return false;
        var state = MovementPhysics.controller(player).snapshot(player.tickCount).parkour();
        var runtime = ParkourPhysics.runtime(player);
        if (runtime.strike != state.instance()) {
            runtime.strike = state.instance();
            runtime.hitTargets.clear();
        }
        Vec3 start = player.getBoundingBox().getCenter();
        AABB box = target.getBoundingBox();
        Vec3 point = new Vec3(Math.max(box.minX, Math.min(box.maxX, start.x)), Math.max(box.minY, Math.min(box.maxY, start.y)), Math.max(box.minZ, Math.min(box.maxZ, start.z)));
        var obstruction = player.level().clip(new ClipContext(start, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (obstruction.getType() == HitResult.Type.BLOCK && obstruction.getLocation().distanceToSqr(start) < point.distanceToSqr(start) - 1e-7)
            return false;
        if (runtime.hitTargets.contains(target.getUUID())) return true;
        runtime.hitTargets.add(target.getUUID());
        var values = NAttributes.read(player);
        Vec3 velocity = player instanceof ServerPlayer ? player.getKnownMovement() : player.getDeltaMovement();
        LivingEntity previous = DAMAGE_TARGET.get();
        DAMAGE_TARGET.set(target);
        try {
            target.hurtServer(level, player.damageSources().playerAttack(player), (float) values.get(MovementAttribute.FLYING_KICK_DAMAGE));
        } finally {
            if (previous == null) DAMAGE_TARGET.remove();
            else DAMAGE_TARGET.set(previous);
        }
        applyKnockback(target, velocity, values.get(MovementAttribute.FLYING_KICK_KNOCKBACK_MULTIPLIER));
        return true;
    }

    private static void applyKnockback(LivingEntity target, Vec3 velocity, double multiplier) {
        double speed = velocity.length(), power = speed * multiplier;
        if (!Double.isFinite(power) || power <= 1e-10) return;
        // Preserve carried velocity and apply vanilla knockback resistance to the kick impulse.
        double resistance = Math.max(0, 1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (resistance == 0) return;
        target.setDeltaMovement(target.getDeltaMovement().add(velocity.scale(multiplier * resistance)));
        if (target instanceof ServerPlayer player) ServerMovementEvents.knockback(player);
        target.needsSync = true;
        if (target instanceof ServerPlayer remote) remote.connection.send(new ClientboundSetEntityMotionPacket(remote));
    }
}
