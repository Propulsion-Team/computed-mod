package dev.propulsionteam.computed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.ButtonWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ClockWidget;
import dev.propulsionteam.computed.content.monitors.widgets.ProgressBarWidget;
import dev.propulsionteam.computed.content.monitors.widgets.SliderWidget;
import dev.propulsionteam.computed.content.monitors.widgets.TextAlignment;
import dev.propulsionteam.computed.content.monitors.widgets.TextWidget;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.network.ComputedNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the widget overlay across the inset screen surface of a formed monitor multiblock.
 *
 * <p>Widget coordinates are in pixels where {@link ComputedNetworking#SCREEN_PX_PER_BLOCK} pixels equal
 * one block. So the screen surface of a {@code 3×2} monitor occupies the rectangle
 * {@code (0, 0) .. (3*64, 2*64)} in widget space.
 */
public class MonitorBlockEntityRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    /** Push the background this far forward of the block face so it doesn't z-fight with the block. */
    private static final float BACKGROUND_FORWARD_OFFSET = 0.0020f;
    private static final float WIDGET_UNDERFILL_FORWARD_OFFSET = 0.0025f;
    private static final float WIDGET_FILL_FORWARD_OFFSET = 0.0030f;
    private static final float WIDGET_EDGE_FORWARD_OFFSET = 0.0040f;
    private static final float WIDGET_DETAIL_FORWARD_OFFSET = 0.0045f;
    private static final float WIDGET_TEXT_FORWARD_OFFSET = 0.0050f;

    public MonitorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (be.getXIndex() != 0 || be.getYIndex() != 0) return;

        int blocksW = be.getWidth();
        int blocksH = be.getHeight();
        Direction facing = be.getFront();
        Direction right = be.getRight();
        Direction gridDown = be.getDown();

        // Multiblock geometric center in the origin-block's local space:
        //   origin block center (0.5, 0.5, 0.5)
        //   + half the additional cells in the right axis
        //   + half the additional cells along the grid-down axis (visual up for wall mounts)
        //   + half a block out along the screen normal
        float cx = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepX()
                + (blocksH - 1) * 0.5f * gridDown.getStepX()
                + 0.5f * facing.getStepX();
        float cy = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepY()
                + (blocksH - 1) * 0.5f * gridDown.getStepY()
                + 0.5f * facing.getStepY();
        float cz = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepZ()
                + (blocksH - 1) * 0.5f * gridDown.getStepZ()
                + 0.5f * facing.getStepZ();

        pose.pushPose();
        pose.translate(cx, cy, cz);

        // Rotation: map pose-local axes onto screen-space axes.
        //   pose +X → screenRight (right)
        //   pose +Y → screenDown  = -gridDown   (widget y grows visually downward)
        //   pose +Z → -facing     (into the screen)
        applyScreenRotation(pose, right, gridDown, facing);

        float inset = ComputedNetworking.BEZEL_MODEL_PIXELS / (float) ComputedNetworking.MODEL_PIXELS_PER_BLOCK;
        float usableW = Math.max(0.001f, blocksW - inset * 2f);
        float usableH = Math.max(0.001f, blocksH - inset * 2f);
        int logicalW = blocksW * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        int logicalH = blocksH * ComputedNetworking.SCREEN_PX_PER_BLOCK;

        pose.translate(-blocksW / 2f + inset, -blocksH / 2f + inset, 0f);
        Matrix4f screenMat = new Matrix4f(pose.last().pose());
        VertexConsumer bgConsumer = bufferSource.getBuffer(RenderType.gui());
        fillBlockSpace(layer(screenMat, BACKGROUND_FORWARD_OFFSET), bgConsumer, 0, 0, usableW, usableH, 0xFF000000);

        Matrix4f widgetMat = new Matrix4f(screenMat).scale(usableW / logicalW, usableH / logicalH, 1f);
        Matrix4f underfillMat = layer(widgetMat, WIDGET_UNDERFILL_FORWARD_OFFSET);
        Matrix4f fillMat = layer(widgetMat, WIDGET_FILL_FORWARD_OFFSET);
        Matrix4f edgeMat = layer(widgetMat, WIDGET_EDGE_FORWARD_OFFSET);
        Matrix4f detailMat = layer(widgetMat, WIDGET_DETAIL_FORWARD_OFFSET);
        Matrix4f textMat = layer(widgetMat, WIDGET_TEXT_FORWARD_OFFSET);

        Font font = Minecraft.getInstance().font;
        for (Widget w : be.getDrawList().widgets()) {
            renderWidget(w, underfillMat, fillMat, edgeMat, detailMat, textMat, bufferSource, font);
        }

        pose.popPose();
    }

    private static Matrix4f layer(Matrix4f mat, float forwardOffset) {
        return new Matrix4f(mat).translate(0f, 0f, -forwardOffset);
    }

    private static void applyScreenRotation(PoseStack pose, Direction right, Direction gridDown, Direction facing) {
        // Build a rotation matrix whose columns are the images of (1,0,0), (0,1,0), (0,0,1) in parent space.
        float rx = right.getStepX(),    ry = right.getStepY(),    rz = right.getStepZ();
        float dx = -gridDown.getStepX(), dy = -gridDown.getStepY(), dz = -gridDown.getStepZ();
        float fx = -facing.getStepX(),  fy = -facing.getStepY(),  fz = -facing.getStepZ();
        Matrix4f rot = new Matrix4f().set(new float[] {
                rx, ry, rz, 0f,   // col 0  → pose +X
                dx, dy, dz, 0f,   // col 1  → pose +Y
                fx, fy, fz, 0f,   // col 2  → pose +Z
                0f, 0f, 0f, 1f
        });
        pose.mulPose(rot);
    }

    private void renderWidget(Widget w, Matrix4f underfillMat, Matrix4f fillMat, Matrix4f edgeMat, Matrix4f detailMat,
                              Matrix4f textMat, MultiBufferSource bufferSource, Font font) {
        VertexConsumer gui = bufferSource.getBuffer(RenderType.gui());
        if (w instanceof TextWidget tw) {
            outline(edgeMat, gui, tw.x(), tw.y(), tw.x() + tw.w(), tw.y() + tw.h(), 0xFFFFFFFF);
            drawText(font, textMat, bufferSource, tw.text(), tw.x(), tw.y(), tw.w(), tw.h(),
                    tw.colorArgb(), tw.alignment());
        } else if (w instanceof ClockWidget cw) {
            long time = Minecraft.getInstance().level == null
                    ? 0L
                    : Minecraft.getInstance().level.getDayTime();
            String text = formatTime(time, cw.showSeconds());
            outline(edgeMat, gui, cw.x(), cw.y(), cw.x() + cw.w(), cw.y() + cw.h(), 0xFFFFFFFF);
            drawText(font, textMat, bufferSource, text, cw.x(), cw.y(), cw.w(), cw.h(),
                    cw.colorArgb(), cw.alignment());
        } else if (w instanceof ButtonWidget bw) {
            fill(fillMat, gui, bw.x(), bw.y(), bw.x() + bw.w(), bw.y() + bw.h(), bw.colorArgb());
            outline(edgeMat, gui, bw.x(), bw.y(), bw.x() + bw.w(), bw.y() + bw.h(), 0xFFFFFFFF);
            drawCenteredText(font, textMat, bufferSource, bw.label(), bw.x(), bw.y(), bw.w(), bw.h(), 0xFFFFFFFF);
        } else if (w instanceof SliderWidget sw) {
            int barY1 = sw.y() + sw.h() / 2 - 2;
            int barY2 = sw.y() + sw.h() / 2 + 2;
            fill(fillMat, gui, sw.x(), barY1, sw.x() + sw.w(), barY2, 0xFF202020);
            outline(edgeMat, gui, sw.x(), sw.y(), sw.x() + sw.w(), sw.y() + sw.h(), 0xFFFFFFFF);
            double range = sw.max() - sw.min();
            int handleX;
            if (sw.step() > 0 && range > 0) {
                int ticks = (int) Math.floor(range / sw.step() + 1.0e-6);
                if (ticks > 0 && ticks <= sw.w()) {
                    for (int i = 0; i <= ticks; i++) {
                        double t = Mth.clamp((i * sw.step()) / range, 0.0, 1.0);
                        int tx = sw.x() + (int) Math.round(t * sw.w());
                        fill(detailMat, gui, tx, barY1, tx + 1, barY2, 0xFFFFFFFF);
                    }
                }
                int snappedIdx = (int) Math.round((sw.value() - sw.min()) / sw.step());
                double t = Mth.clamp((snappedIdx * sw.step()) / range, 0.0, 1.0);
                handleX = sw.x() + (int) Math.round(t * sw.w());
            } else {
                double norm = range == 0 ? 0 : (sw.value() - sw.min()) / range;
                handleX = sw.x() + (int) (Mth.clamp(norm, 0.0, 1.0) * sw.w());
            }
            fill(detailMat, gui, handleX - 3, sw.y(), handleX + 3, sw.y() + sw.h(), sw.colorArgb());
        } else if (w instanceof ProgressBarWidget pb) {
            double frac = pb.max() <= 0 ? 0 : Mth.clamp(pb.value() / pb.max(), 0.0, 1.0);
            outline(edgeMat, gui, pb.x(), pb.y(), pb.x() + pb.w(), pb.y() + pb.h(), 0xFFFFFFFF);
            int segs = pb.segments();
            if (segs <= 0) {
                int fillEnd = pb.x() + (int) (frac * pb.w());
                fill(underfillMat, gui, pb.x(), pb.y(), fillEnd, pb.y() + pb.h(), pb.colorArgb());
            } else {
                int litSegs = (int) Math.floor(frac * segs + 1.0e-6);
                int innerY = pb.y() + 2;
                int innerH = Math.max(0, pb.h() - 4);
                int innerW = Math.max(0, pb.w() - 4);
                int gap = segs > 1 ? Math.min(2, Math.max(0, (innerW - segs) / Math.max(1, segs - 1))) : 0;
                int segW = segs > 0 ? Math.max(1, (innerW - gap * (segs - 1)) / segs) : 0;
                int contentW = segW * segs + gap * (segs - 1);
                int leftPad = Math.max(0, (innerW - contentW) / 2);
                int startX = pb.x() + 2 + leftPad;
                for (int i = 0; i < litSegs; i++) {
                    int sx = startX + i * (segW + gap);
                    fill(underfillMat, gui, sx, innerY, sx + segW, innerY + innerH, pb.colorArgb());
                }
            }
        }
    }

    private static String formatTime(long dayTime, boolean withSeconds) {
        long t = ((dayTime + 6000) % 24000 + 24000) % 24000;
        double hourF = t / 1000.0;
        int hours = (int) hourF;
        int minutes = (int) ((hourF - hours) * 60);
        if (!withSeconds) return String.format("%02d:%02d", hours, minutes);
        int seconds = (int) ((((hourF - hours) * 60) - minutes) * 60);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** Fills in block-space units (called for the background only). */
    private static void fillBlockSpace(Matrix4f mat, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
        emitQuad(mat, vc, x1, y1, x2, y2, argb);
    }

    private static void fill(Matrix4f mat, VertexConsumer vc, int x1, int y1, int x2, int y2, int argb) {
        emitQuad(mat, vc, x1, y1, x2, y2, argb);
    }

    private static void emitQuad(Matrix4f mat, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ((argb      ) & 0xFF) / 255f;
        if (a == 0f) return;
        vc.addVertex(mat, x1, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, 0).setColor(r, g, b, a);
    }

    private static void outline(Matrix4f mat, VertexConsumer vc, int x1, int y1, int x2, int y2, int argb) {
        fill(mat, vc, x1, y1, x2, y1 + 1, argb);
        fill(mat, vc, x1, y2 - 1, x2, y2, argb);
        fill(mat, vc, x1, y1, x1 + 1, y2, argb);
        fill(mat, vc, x2 - 1, y1, x2, y2, argb);
    }

    private static void drawText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                 int x, int y, int w, int h, int colorArgb) {
        drawText(font, mat, bufferSource, text, x, y, w, h, colorArgb, TextAlignment.LEFT);
    }

    private static void drawCenteredText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                         int x, int y, int w, int h, int colorArgb) {
        drawText(font, mat, bufferSource, text, x, y, w, h, colorArgb, TextAlignment.CENTER);
    }

    private static void drawText(Font font, Matrix4f mat, MultiBufferSource bufferSource, String text,
                                 int x, int y, int w, int h, int colorArgb, TextAlignment alignment) {
        if (text == null || text.isEmpty()) return;
        String shown = text;
        int pad = Math.min(2, Math.max(0, w / 8));
        int availableW = Math.max(1, w - pad * 2);
        int availableH = Math.max(1, h - pad * 2);
        float scale = Math.min(1.0f, Math.min(availableW / (float) Math.max(1, font.width(shown)),
                availableH / (float) font.lineHeight));
        int textW = Math.round(font.width(shown) * scale);
        int textH = Math.round(font.lineHeight * scale);
        float tx = switch (alignment == null ? TextAlignment.CENTER : alignment) {
            case LEFT -> x + pad;
            case CENTER -> x + (w - textW) / 2f;
            case RIGHT -> x + w - pad - textW;
        };
        float ty = y + (h - textH) / 2f;
        Matrix4f textMat = new Matrix4f(mat).translate(tx, ty, 0f).scale(scale, scale, 1f);
        font.drawInBatch(shown, 0, 0, colorArgb, false, textMat, bufferSource,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
