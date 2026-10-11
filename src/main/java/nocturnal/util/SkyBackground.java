package nocturnal.util;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A looping video wrapped around the sky. Each video is an MJPEG file (JPEG frames back to back) in
 * assets/nocturnal/sky, made from an MP4 with ffmpeg. Frames are decoded one ahead on a worker thread, so only one
 * frame is ever on the GPU. Create, draw and close it on the render thread.
 */
public final class SkyBackground implements AutoCloseable {
    private static final int FPS = 15;
    private static final float RADIUS = 100;
    private static final int SEGMENTS_AROUND = 32, SEGMENTS_UP = 16;
    private static final int INDEX_COUNT = SEGMENTS_AROUND * SEGMENTS_UP * 6;

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nocturnal", "pipeline/sky_background"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withoutBlend()
            .withCull(false)
            .withDepthWrite(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private final ByteBuffer data;
    private final int[] frameStart, frameLength;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Nocturnal sky decoder");
        t.setDaemon(true);
        return t;
    });
    private final GpuTexture texture;
    private final GpuTextureView view;
    private final GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    private final GpuBuffer dome = buildDome();
    private final long startNanos = System.nanoTime();
    private CompletableFuture<NativeImage> next;
    private int shown;

    public SkyBackground(String name) throws IOException {
        byte[] bytes;
        try (InputStream in = SkyBackground.class.getResourceAsStream("/assets/nocturnal/sky/" + name + ".mjpeg")) {
            if (in == null) throw new IOException("Missing sky background " + name);
            bytes = in.readAllBytes();
        }
        List<int[]> frames = splitFrames(bytes);
        if (frames.isEmpty()) throw new IOException("No frames in sky background " + name);
        frameStart = frames.stream().mapToInt(f -> f[0]).toArray();
        frameLength = frames.stream().mapToInt(f -> f[1]).toArray();
        data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();

        NativeImage first = decode(0);
        if (first == null) throw new IOException("Can't decode sky background " + name);
        texture = RenderSystem.getDevice().createTexture(() -> "Nocturnal sky " + name,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, first.getWidth(), first.getHeight(), 1, 1);
        view = RenderSystem.getDevice().createTextureView(texture);
        upload(first);
        queue(1);
    }

    public void draw(float brightness) {
        advance();
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                new Vector4f(brightness, brightness, brightness, 1), new Vector3f(), new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nocturnal sky",
                target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.bindTexture("Sampler0", view, sampler);
            pass.setVertexBuffer(0, dome);
            pass.setIndexBuffer(indices.getBuffer(INDEX_COUNT), indices.type());
            pass.drawIndexed(0, 0, INDEX_COUNT, 1);
        }
    }

    /** Shows the next frame once it's due and decoded. A slow decoder slows the video down instead of skipping. */
    private void advance() {
        long due = (System.nanoTime() - startNanos) * FPS / 1_000_000_000L % frameStart.length;
        if (due == shown || !next.isDone()) return;
        NativeImage image = next.join();
        shown = (shown + 1) % frameStart.length;
        if (image != null) upload(image);
        queue(shown + 1);
    }

    private void upload(NativeImage image) {
        try (image) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
        }
    }

    private void queue(int frame) {
        int f = frame % frameStart.length;
        next = CompletableFuture.supplyAsync(() -> decode(f), decoder);
    }

    private NativeImage decode(int frame) {
        ByteBuffer jpeg = MemoryUtil.memSlice(data, frameStart[frame], frameLength[frame]);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(jpeg, w, h, channels, 4);
            if (pixels == null) return null;
            return new NativeImage(NativeImage.Format.RGBA, w.get(0), h.get(0), true, MemoryUtil.memAddress(pixels));
        }
    }

    /** JPEG frames run from an FFD8 marker to an FFD9 marker; the compressed data in between never contains FFD9. */
    private static List<int[]> splitFrames(byte[] b) {
        List<int[]> frames = new ArrayList<>();
        int start = -1;
        for (int i = 0; i + 1 < b.length; i++) {
            if (b[i] != (byte) 0xFF) continue;
            if (start < 0 && b[i + 1] == (byte) 0xD8) start = i;
            else if (start >= 0 && b[i + 1] == (byte) 0xD9) {
                frames.add(new int[]{start, i + 2 - start});
                start = -1;
            }
        }
        return frames;
    }

    private static GpuBuffer buildDome() {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(SEGMENTS_AROUND * SEGMENTS_UP * 4 * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int i = 0; i < SEGMENTS_AROUND; i++) {
                for (int j = 0; j < SEGMENTS_UP; j++) {
                    domeVertex(builder, i, j);
                    domeVertex(builder, i + 1, j);
                    domeVertex(builder, i + 1, j + 1);
                    domeVertex(builder, i, j + 1);
                }
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Nocturnal sky dome", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
    }

    /** The picture is mirrored halfway around and again at the horizon, so its edges only ever meet themselves. */
    private static void domeVertex(BufferBuilder builder, int around, int up) {
        float yaw = around * Mth.TWO_PI / SEGMENTS_AROUND;
        float pitch = Mth.HALF_PI - up * Mth.PI / SEGMENTS_UP;
        float ring = Mth.cos(pitch) * RADIUS;
        float u = 1 - Math.abs(2f * around / SEGMENTS_AROUND - 1);
        float v = 1 - Math.abs(2f * up / SEGMENTS_UP - 1);
        builder.addVertex(ring * Mth.cos(yaw), Mth.sin(pitch) * RADIUS, ring * Mth.sin(yaw)).setUv(u, v).setColor(-1);
    }

    @Override
    public void close() {
        decoder.shutdownNow();
        try {
            decoder.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        NativeImage pending = next.getNow(null);
        if (pending != null) pending.close();
        view.close();
        texture.close();
        dome.close();
        MemoryUtil.memFree(data);
    }
}
