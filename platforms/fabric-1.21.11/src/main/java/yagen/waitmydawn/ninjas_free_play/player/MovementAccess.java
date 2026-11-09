package yagen.waitmydawn.ninjas_free_play.player;

import yagen.waitmydawn.ninjas_free_play.core.MovementSession;
import yagen.waitmydawn.ninjas_free_play.core.MovementActionTracker;

public interface MovementAccess {
    net.minecraft.nbt.CompoundTag ninjas$attributeDefaults();
    void ninjas$setAttributeDefaults(net.minecraft.nbt.CompoundTag defaults);
    MovementSession ninjas$movement();

    MovementActionTracker ninjas$actions();

    ParkourRuntime ninjas$parkourRuntime();

    float ninjas$lowHeightRatio();

    void ninjas$lowHeightRatio(float ratio);
}
