package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.Cube.class)
public interface CubeAccessor {
    @Accessor("polygons")
    ModelPart.Polygon[] ninjas$getPolygons();
}
