/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.java2d.opengl;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import sun.java2d.SunGraphics2D;
import sun.java2d.pipe.BufferedContext;
import sun.java2d.pipe.Region;
import sun.java2d.pipe.RenderBuffer;
import sun.java2d.pipe.RenderQueue;
import static sun.java2d.pipe.BufferedOpCodes.*;

/**
 * Note that the RenderQueue lock must be acquired before calling any of
 * the methods in this class.
 */
class OGLContext extends BufferedContext {

    /** Indicates that the context has no capabilities. */
    static final int CAPS_EMPTY            = (0 << 0);
    /** Indicates that the context is doublebuffered. */
    static final int CAPS_DOUBLEBUFFERED   = (1 << 0);
    /** Indicates that the context supports a stored alpha channel. */
    static final int CAPS_STORED_ALPHA     = (1 << 1);
    /** Indicates the presence of the GL_ARB_multitexture extension. */
    static final int CAPS_EXT_MULTITEXTURE = (1 << 2);
    /** Indicates the presence of the GL_ARB_texture_non_power_of_two ext. */
    static final int CAPS_EXT_TEXNONPOW2   = (1 << 3);
    /**
     * Indicates the presence of the GL_EXT_framebuffer_object extension.
     * This cap will only be set if the fbobject system property has been
     * enabled and we are able to create an FBO with depth buffer.
     */
    static final int CAPS_EXT_FBOBJECT     = (1 << 4);
    /**
     * Indicates the presence of the GL_ARB_fragment_shader extension.
     * This cap will only be set if the lcdshader system property has been
     * enabled and the hardware supports the minimum number of texture units.
     */
    static final int CAPS_EXT_LCD_SHADER   = (1 << 5);
    /** Indicates the presence of the GL_ARB_texture_rectangle extension. */
    static final int CAPS_EXT_TEXRECT      = (1 << 6);
    /**
     * Indicates the presence of the GL_ARB_fragment_shader extension.
     * This cap will only be set if the biopshader system property has been
     * enabled and the hardware meets our minimum requirements.
     */
    static final int CAPS_EXT_BIOP_SHADER  = (1 << 7);
    /**
     * Indicates the presence of the GL_ARB_fragment_shader extension.
     * This cap will only be set if the gradshader system property has been
     * enabled and the hardware meets our minimum requirements.
     */
    static final int CAPS_EXT_GRAD_SHADER  = (1 << 8);

    OGLContext(RenderQueue rq) {
        super(rq);
    }

    /**
     * Fetches the OGLContext associated with the current GraphicsConfig
     * and validates the context using the given parameters.  Most rendering
     * operations will call this method first in order to set the necessary
     * state before issuing rendering commands.
     */
    static void validateContext(OGLSurfaceData srcData,
                                OGLSurfaceData dstData,
                                Region clip, Composite comp,
                                AffineTransform xform,
                                Paint paint, SunGraphics2D sg2d,
                                int flags)
    {
        // assert rq.lock.isHeldByCurrentThread();
        OGLContext oglc = dstData.getContext();
        oglc.validate(srcData, dstData,
                      clip, comp, xform, paint, sg2d, flags);
    }

    /**
     * Simplified version of validateContext() that disables all context
     * state settings.
     */
    static void validateContext(OGLSurfaceData dstData) {
        // assert rq.lock.isHeldByCurrentThread();
        validateContext(dstData, dstData,
                        null, null, null, null, null, NO_CONTEXT_FLAGS);
    }

    /**
     * Convenience method that delegates to setScratchSurface() below.
     */
    static void setScratchSurface(OGLGraphicsConfig gc) {
        setScratchSurface(gc.getNativeConfigInfo());
    }

    /**
     * Makes the given GraphicsConfig's context current to its associated
     * "scratch surface".  Each GraphicsConfig maintains a native context
     * (GLXContext on Unix, HGLRC on Windows) as well as a native pbuffer
     * known as the "scratch surface".  By making the context current to the
     * scratch surface, we are assured that we have a current context for
     * the relevant GraphicsConfig, and can therefore perform operations
     * depending on the capabilities of that GraphicsConfig.  For example,
     * if the GraphicsConfig supports the GL_ARB_texture_non_power_of_two
     * extension, then we should be able to make a non-pow2 texture for this
     * GraphicsConfig once we make the context current to the scratch surface.
     *
     * This method should be used for operations with an OpenGL texture
     * as the destination surface (e.g. a sw->texture blit loop), or in those
     * situations where we may not otherwise have a current context (e.g.
     * when disposing a texture-based surface).
     */
    static void setScratchSurface(long pConfigInfo) {
        // assert OGLRenderQueue.getInstance().lock.isHeldByCurrentThread();

        // invalidate the current context
        currentContext = null;

        // set the scratch context
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        RenderBuffer buf = rq.getBuffer();
        rq.ensureCapacityAndAlignment(12, 4);
        buf.putInt(SET_SCRATCH_SURFACE);
        buf.putLong(pConfigInfo);
    }

    /**
     * Invalidates the currentContext field to ensure that we properly
     * revalidate the OGLContext (make it current, etc.) next time through
     * the validate() method.  This is typically invoked from methods
     * that affect the current context state (e.g. disposing a context or
     * surface).
     */
    static void invalidateCurrentContext() {
        // assert OGLRenderQueue.getInstance().lock.isHeldByCurrentThread();

        // first invalidate the context reference at the native level, and
        // then flush the queue so that we have no pending operations
        // dependent on the current context
        OGLRenderQueue rq = OGLRenderQueue.getInstance();
        rq.ensureCapacity(4);
        rq.getBuffer().putInt(INVALIDATE_CONTEXT);
        rq.flushNow();

        // then invalidate the current Java-level context so that we
        // revalidate everything the next time around
        if (currentContext != null) {
            currentContext.invalidateSurfaces();
            currentContext = null;
        }
    }
}
