package yagen.waitmydawn.ninjas_free_play.api.movement;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.ninjas_free_play.player.ParkourCombat;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;

/**
 * A causal collision scope, not proximity-based invulnerability. Scopes must close in a finally block.
 */
public final class ContactDamageContext implements AutoCloseable {
    private static final ThreadLocal<ContactDamageContext> CURRENT = new ThreadLocal<>();
    private final ContactDamageContext previous;
    private final Player player;
    private final LivingEntity source;

    private ContactDamageContext(Player player, LivingEntity source) {
        this.previous = CURRENT.get();
        this.player = player;
        this.source = source;
        CURRENT.set(this);
    }

    public static ContactDamageContext collision(Entity first, Entity second) {
        Player player = first instanceof Player p && MovementPhysics.controller(p).parkour().mode() == ParkourMode.FLYING_KICK ? p
                : second instanceof Player p && MovementPhysics.controller(p).parkour().mode() == ParkourMode.FLYING_KICK ? p : null;
        Entity other = player == first ? second : first;
        LivingEntity source = other instanceof LivingEntity living ? living : null;
        boolean struck = player != null && source != null && ParkourCombat.hit(player, source);
        return new ContactDamageContext(struck ? player : null, struck ? source : null);
    }

    public static boolean suppresses(LivingEntity victim, DamageSource damage) {
        var scope = CURRENT.get();
        boolean directMelee = damage.is(MovementDamageTags.CONTACT_MELEE);
        return scope != null && scope.player == victim && scope.source != null && damage.getDirectEntity() == scope.source && directMelee;
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
