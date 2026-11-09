package yagen.waitmydawn.ninjas_free_play.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import yagen.waitmydawn.ninjas_free_play.animation.core.LimbDeformer;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.mixin.client.CubeAccessor;

import java.util.ArrayList;
import java.util.List;

public final class LimbMeshRenderer {
    private record Vertex(Vector3 position, double u, double v, Vector3 normal) {
        Vertex lerp(Vertex other, double amount) {
            return new Vertex(position.lerp(other.position, amount), u + (other.u - u) * amount,
                    v + (other.v - v) * amount, normal);
        }
    }

    private final List<Vertex> vertices;
    private final double maximumDepth;

    public LimbMeshRenderer(List<ModelPart.Cube> cubes) {
        ArrayList<Vertex> mesh = new ArrayList<>();
        double depth = 0;
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon polygon : ((CubeAccessor) cube).ninjas$getPolygons()) {
                Vertex[] corners = new Vertex[4];
                Vector3 normal = new Vector3(polygon.normal().x(), polygon.normal().y(), polygon.normal().z());
                for (int index = 0; index < 4; index++) {
                    ModelPart.Vertex source = polygon.vertices()[index];
                    corners[index] = new Vertex(new Vector3(source.x(), source.y(), source.z()), source.u(), source.v(), normal);
                    depth = Math.max(depth, Math.abs(source.z()));
                }
                boolean horizontalEdge = Math.abs(corners[0].position.y() - corners[1].position.y()) > 1e-6;
                if (horizontalEdge) corners = new Vertex[]{corners[1], corners[2], corners[3], corners[0]};
                int strips = Math.max(1, Math.min(64, (int) Math.ceil(Math.abs(corners[0].position.y() - corners[3].position.y()))));
                for (int strip = 0; strip < strips; strip++) {
                    double from = (double) strip / strips;
                    double to = (double) (strip + 1) / strips;
                    mesh.add(corners[0].lerp(corners[3], from));
                    mesh.add(corners[1].lerp(corners[2], from));
                    mesh.add(corners[1].lerp(corners[2], to));
                    mesh.add(corners[0].lerp(corners[3], to));
                }
            }
        }
        vertices = List.copyOf(mesh);
        maximumDepth = depth;
    }

    private LimbDeformer profile(double angle, double center) {
        // Keep the deformation Jacobian positive, including inflated armor and skin layers.
        return new LimbDeformer(angle, center, Math.max(10, 1.05 * Math.abs(angle) * maximumDepth));
    }

    public void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, double angle, double center) {
        LimbDeformer bend = profile(angle, center);
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (Vertex vertex : vertices) {
            Vector3 point = bend.position(vertex.position);
            Vector3 direction = bend.normal(vertex.position, vertex.normal);
            pose.pose().transformPosition((float) point.x() / 16, (float) point.y() / 16, (float) point.z() / 16, position);
            pose.transformNormal((float) direction.x(), (float) direction.y(), (float) direction.z(), normal);
            buffer.addVertex(position.x, position.y, position.z, color, (float) vertex.u, (float) vertex.v,
                    overlay, light, normal.x, normal.y, normal.z);
        }
    }

    public void translateToHand(PoseStack poseStack, double angle, double center) {
        LimbDeformer bend = profile(angle, center);
        Vector3 endpoint = bend.position(new Vector3(0, 10, 0));
        poseStack.translate(endpoint.x() / 16, endpoint.y() / 16, endpoint.z() / 16);
        poseStack.mulPose(Axis.XP.rotation((float) bend.angleAt(10)));
        poseStack.translate(0, -10.0 / 16, 0);
    }
}
