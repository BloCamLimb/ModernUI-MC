/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.forge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import icyllis.arc3d.engine.DirectContext;
import icyllis.arc3d.engine.DrawableInfo;
import icyllis.arc3d.opengl.GLCore;
import icyllis.modernui.graphics.*;
import icyllis.modernui.util.Pools;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import javax.annotation.Nonnull;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import static icyllis.arc3d.opengl.GLCore.*;

/**
 * CanvasForge is an extension to {@link Canvas}, which provides more drawing
 * methods used in Minecraft on UI thread.
 *
 * @author BloCamLimb
 */
public final class ContainerDrawHelper {

    static {
        assert (FMLEnvironment.dist.isClient());
    }

    private static final Pools.Pool<DrawItem> sDrawItemPool = Pools.newSimplePool(60);

    //private final BufferBuilder mBufferBuilder = new BufferBuilder(256);
    //private final BufferSource mBufferSource = new BufferSource();

    private final Queue<DrawItem> mDrawItems = new ArrayDeque<>();
    private final FloatBuffer mMatBuf = BufferUtils.createFloatBuffer(16);

    private final Matrix4f mProjection = new Matrix4f();

    private final Object2IntMap<String> mSamplerUnits = new Object2IntArrayMap<>();

    //private final Runnable mDrawItem = this::drawItem;

    private final ItemRenderer mRenderer = Minecraft.getInstance().getItemRenderer();
    private final TextureManager mTextureManager = Minecraft.getInstance().getTextureManager();

    private volatile GLSurfaceCanvas mCanvas;

    private ContainerDrawHelper() {
        for (int i = 0; i < 8; i++) {
            mSamplerUnits.put("Sampler" + i, i);
        }
        mSamplerUnits.defaultReturnValue(-1);
    }

    /**
     * Draw an item in the player's container.
     *
     * @param item the item stack to draw
     * @param x    the center x pos
     * @param y    the center y pos
     * @param z    the center z pos
     * @param size the size in pixels, it's generally 32 dp
     */
    public static void drawItem(@Nonnull Canvas canvas, @Nonnull ItemStack item,
                                float x, float y, float z, float size, int seed) {
        if (item.isEmpty()) {
            return;
        }

        canvas.drawCustomDrawable(new ItemDrawable(item, x, y, z, size, seed));
    }

    private record ItemDrawable(ItemStack item, float x, float y, float z, float size, int seed)
            implements CustomDrawable {

        @Override
        public DrawHandler snapDrawHandler(int backendApi,
                                           Matrix4 viewMatrix,
                                           Rect clipBounds,
                                           ImageInfo targetInfo) {
            viewMatrix.preTranslate(x, y, z + 3000);
            viewMatrix.preScale(size, -size, size);
            return new DrawItem(targetInfo, viewMatrix, item, seed);
        }

        @Override
        public RectF getBounds() {
            return new RectF(-size, -size, size, size);
        }
    }

    private static class DrawItem implements CustomDrawable.DrawHandler {

        private final ItemStack item;
        private final Matrix4f projection;
        private final Matrix4f pose;
        private final int seed;

        private DrawItem(ImageInfo ii, Matrix4 mv, ItemStack is, int seed) {
            item = is;
            projection = new Matrix4f();
            projection.setOrtho(0, ii.width(), ii.height(), 0,
                    1000, 11000);
            pose = new Matrix4f(
                    mv.m11, mv.m12, mv.m13, mv.m14,
                    mv.m21, mv.m22, mv.m23, mv.m24,
                    mv.m31, mv.m32, mv.m33, mv.m34,
                    mv.m41, mv.m42, mv.m43, mv.m44
            );
            this.seed = seed;
        }

        @Override
        public void draw(DirectContext dContext, DrawableInfo info) {
            Minecraft minecraft = Minecraft.getInstance();
            Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
            BufferUploader.invalidate();
            BakedModel model = minecraft.getItemRenderer().getModel(item, minecraft.level, minecraft.player, seed);
            boolean light2D = !model.usesBlockLight();
            if (light2D) {
                Lighting.setupForFlatItems();
            }
            GLCore.glBindSampler(0, 0);
            RenderSystem.bindTexture(0);
            RenderSystem.enableCull();
            PoseStack localTransform = new PoseStack();
            localTransform.mulPoseMatrix(pose);
            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
            minecraft.getItemRenderer().render(item, ItemDisplayContext.GUI, false, localTransform, bufferSource,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            bufferSource.endBatch();
            if (light2D) {
                Lighting.setupFor3DItems();
            }
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.activeTexture(GL_TEXTURE0);
            RenderSystem.setProjectionMatrix(oldProjection, VertexSorting.ORTHOGRAPHIC_Z);
        }
    }

    /*private void drawItem() {
        DrawItem t = mDrawItems.element();
        BakedModel model = mRenderer.getModel(t.mStack, null, Minecraft.getInstance().player, 0);
        AbstractTexture texture = mTextureManager.getTexture(InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderTexture(0, texture.getId());

        BufferSource bufferSource = mBufferSource;
        boolean light2D = !model.usesBlockLight();
        if (light2D) {
            Lighting.setupForFlatItems();
        }
        PoseStack localTransform = new PoseStack();
        *//*mRenderer.render(t.mStack, ItemTransforms.TransformType.GUI, false, localTransform, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);*//*
        bufferSource.endBatch();
        if (light2D) {
            Lighting.setupFor3DItems();
        }

        mDrawItems.remove().recycle();

        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }*/

    //TODO
    /*@RenderThread
    private void end(@Nonnull ByteBuffer buffer, @Nonnull VertexFormat.Mode mode, @Nonnull VertexFormat format,
                     @Nonnull VertexFormat.IndexType indexType, int indexCount, boolean sequentialIndex) {
        final GLSurfaceCanvas canvas = mCanvas;

        *//*if (canvas.bindVertexArray(format.getOrCreateVertexArrayObject())) {
            // minecraft is stupid so that it clears these bindings after a draw call
            glBindBuffer(GL_ARRAY_BUFFER, format.getOrCreateVertexBufferObject());
            format.setupBufferState();
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        } else {
            glNamedBufferData(format.getOrCreateVertexBufferObject(), buffer, GL_DYNAMIC_DRAW);
        }

        final int indexBufferType;
        if (sequentialIndex) {
            RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(mode, indexCount);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer.name());
            indexBufferType = indexBuffer.type().asGLType;
        } else {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, format.getOrCreateIndexBufferObject());
            int pos = buffer.limit();
            buffer.position(pos);
            buffer.limit(pos + indexCount * indexType.bytes);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            indexBufferType = indexType.asGLType;
        }*//*

        final ShaderInstance shader = RenderSystem.getShader();
        assert shader != null;

        final DrawItem t = mDrawItems.element();
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(t.mModelView);
        }

        if (shader.PROJECTION_MATRIX != null) {
            mProjection.set(canvas.getProjection().limit(16));
            shader.PROJECTION_MATRIX.set(mProjection);
        }

        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(t.mR, t.mG, t.mB, t.mA);
        }

        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }

        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }

        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }

        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }

        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        if (shader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set(window.getWidth(), window.getHeight());
        }

        if (shader.LINE_WIDTH != null && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP)) {
            shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        }

        RenderSystem.setupShaderLights(shader);
        ((FastShader) shader).fastApply(canvas, mSamplerUnits);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        //glDrawElements(mode.asGLMode, indexCount, indexBufferType, MemoryUtil.NULL);
    }*/

    /*private class BufferSource implements MultiBufferSource {

        @Nullable
        private RenderType mLastType;

        public BufferSource() {
        }

        @Nonnull
        @Override
        public VertexConsumer getBuffer(@Nonnull RenderType type) {
            BufferBuilder builder = mBufferBuilder;
            if (!Objects.equals(type, mLastType)) {
                endBatch();
                builder.begin(type.mode(), type.format());
                mLastType = type;
            }
            return builder;
        }

        public void endBatch() {
            BufferBuilder builder = mBufferBuilder;
            if (mLastType != null && builder.building()) {
                *//*if (((AccessRenderType) mLastType).isSortOnUpload()) {
                    builder.setQuadSortOrigin(0, 0, 0);
                }*//*

                BufferBuilder.RenderedBuffer renderedBuffer = builder.end();
                mLastType.setupRenderState();

                BufferBuilder.DrawState state = renderedBuffer.drawState();
                ByteBuffer buffer = renderedBuffer.vertexBuffer();

                if (state.vertexCount() > 0) {
                    buffer.position(0)
                            .limit(state.vertexBufferSize());
                    end(buffer, state.mode(), state.format(), state.indexType(),
                            state.indexCount(), state.sequentialIndex());
                }
                buffer.clear();
            }
            mLastType = null;
        }
    }*/

    // fast rotate
    public interface FastShader {

        void fastApply(@Nonnull GLSurfaceCanvas canvas, @Nonnull Object2IntMap<String> units);
    }
}
