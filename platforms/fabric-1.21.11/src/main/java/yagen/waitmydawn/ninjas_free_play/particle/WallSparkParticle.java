package yagen.waitmydawn.ninjas_free_play.particle;

import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WallSparkParticle extends SingleQuadParticle {
    private static SpriteSet emitterSprites;
    private final float initialSize;
    private final int owner;

    private WallSparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, int owner) {
        super(level, x, y, z, sprites.first());
        this.owner = owner;
        xd = vx;
        yd = vy;
        zd = vz;
        gravity = 0;
        friction = 1;
        hasPhysics = true;
        lifetime = 8 + random.nextInt(5);
        initialSize = 0.1f + random.nextFloat() * 0.035f;
        setColor(.91f, .95f, 1);
        setSprite(sprites.get(random));
        setSize(.01f, .01f);
    }

    @Override
    public void move(double x, double y, double z) {
        double oldX = this.x, oldY = this.y, oldZ = this.z;
        super.move(x, y, z);
        if (Math.abs(this.x - oldX - x) > 1e-6 || Math.abs(this.y - oldY - y) > 1e-6 || Math.abs(this.z - oldZ - z) > 1e-6)
            remove();
        if (!level.getEntities((Entity) null, getBoundingBox(), entity -> entity.getId() != owner && entity.isAlive() && !entity.isSpectator()).isEmpty())
            remove();
    }

    @Override
    public float getQuadSize(float partial) {
        return initialSize * Math.max(0, 1 - (age + partial) / lifetime);
    }

    @Override
    public int getLightColor(float partial) {
        return 0xF000F0;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partial) {
        Vector3f screen = new Vector3f((float) xd, (float) yd, (float) zd).rotate(new Quaternionf(camera.rotation()).conjugate());
        roll = (float) Math.atan2(screen.y, screen.x) - (float) Math.PI / 2;
        oRoll = roll;
        super.extract(state, camera, partial);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        public Provider {
            emitterSprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
            return new WallSparkParticle(level, x, y, z, vx, vy, vz, sprites, -1);
        }
    }

    public static void emit(ClientLevel level, int owner, double x, double y, double z, double vx, double vy, double vz) {
        if (emitterSprites != null)
            Minecraft.getInstance().particleEngine.add(new WallSparkParticle(level, x, y, z, vx, vy, vz, emitterSprites, owner));
    }
}
