package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import yagen.waitmydawn.ninjas_free_play.core.WallContact;
import yagen.waitmydawn.ninjas_free_play.core.WallCoverage;

import java.util.ArrayList;
import java.util.List;

public final class WallProbe {
    private WallProbe() {
    }

    public static WallContact find(Player player) {
        double scale = player.getScale(), reach = 0.45 * scale;
        double half = player.getBbWidth() / 2, height = player.getDimensions(Pose.STANDING).height;
        AABB area = new AABB(player.getX() - half - reach, player.getY() + 0.02 * scale, player.getZ() - half - reach,
                player.getX() + half + reach, player.getY() + height * 0.76, player.getZ() + half + reach);
        List<AABB> boxes = new ArrayList<>();
        for (var shape : player.level().getBlockCollisions(player, area)) {
            boxes.addAll(shape.toAabbs());
            if (boxes.size() > 128) return WallContact.NONE;
        }
        WallContact best = WallContact.NONE;
        double score = Double.POSITIVE_INFINITY;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int dx = d[0], dz = d[1];
            double center = dx != 0 ? player.getX() : player.getZ();
            double tangent = dx != 0 ? player.getZ() : player.getX();
            int sign = dx + dz;
            for (AABB candidate : boxes) {
                double plane = dx != 0 ? (dx > 0 ? candidate.minX : candidate.maxX) : (dz > 0 ? candidate.minZ : candidate.maxZ);
                double distance = (plane - center) * sign - half;
                if (distance < -1e-5 || distance > reach) continue;
                List<WallCoverage.Rectangle> faces = new ArrayList<>();
                boolean waist = false;
                for (AABB box : boxes) {
                    double face = dx != 0 ? (dx > 0 ? box.minX : box.maxX) : (dz > 0 ? box.minZ : box.maxZ);
                    if (Math.abs(face - plane) > 1e-6) continue;
                    double left = dx != 0 ? box.minZ : box.minX, right = dx != 0 ? box.maxZ : box.maxX;
                    faces.add(new WallCoverage.Rectangle(left, right, box.minY, box.maxY));
                    waist |= left <= tangent && right >= tangent && box.minY <= player.getY() + height * 0.45 && box.maxY >= player.getY() + height * 0.45;
                }
                boolean latch = WallCoverage.covers(faces, tangent - half * 0.8, tangent + half * 0.8,
                        player.getY() + 0.02 * scale, player.getY() + height * 0.75);
                if (!waist && !latch) continue;
                double preference = distance + 0.01 * scale * (1 + player.getLookAngle().x * (-dx) + player.getLookAngle().z * (-dz));
                if (preference < score) {
                    score = preference;
                    best = new WallContact(-dx, -dz, Math.max(0, distance), latch);
                }
            }
        }
        return best;
    }
}
