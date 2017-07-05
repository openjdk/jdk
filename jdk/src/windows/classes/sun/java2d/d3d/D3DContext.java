/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.d3d;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import sun.awt.Win32GraphicsDevice;
import sun.java2d.InvalidPipeException;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.Region;
import sun.java2d.windows.WindowsFlags;

public class D3DContext {

    public static final int NO_CONTEXT_FLAGS = 0;
    /**
     * Used in D3DBlitLoops: if the source surface is opaque
     * alpha blending can be turned off on the native level
     * (if there's no ea), thus improving performance.
     */
    public static final int SRC_IS_OPAQUE    = 1;

    /**
     * This is a list of capabilities supported by the device this
     * context is associated with.
     * @see getDeviceCaps
     */
    public static final int J2D_D3D_FAILURE                = (0 << 0);
    /**
     * Device supports depth buffer for d3d render targets
     */
    public static final int J2D_D3D_DEPTH_SURFACE_OK       = (1 << 0);
    /**
     * Device supports creation of plain d3d surfaces
     */
    public static final int J2D_D3D_PLAIN_SURFACE_OK       = (1 << 1);
    /**
     * Device supports creation of opaque textures
     */
    public static final int J2D_D3D_OP_TEXTURE_SURFACE_OK  = (1 << 2);
    /**
     * Device supports creation of bitmask textures
     */
    public static final int J2D_D3D_BM_TEXTURE_SURFACE_OK  = (1 << 3);
    /**
     * Device supports creation of translucent textures
     */
    public static final int J2D_D3D_TR_TEXTURE_SURFACE_OK  = (1 << 4);
    /**
     * Device supports creation of opaque render-to-textures
     */
    public static final int J2D_D3D_OP_RTT_SURFACE_OK      = (1 << 5);
    /**
     * Device can render lines correctly (no pixelization issues)
     */
    public static final int J2D_D3D_LINES_OK               = (1 << 6);
    /**
     * Device supports texture mapping (no pixelization issues)
     */
    public static final int J2D_D3D_TEXTURE_BLIT_OK        = (1 << 7);
    /**
     * Device supports texture mapping with transforms (no pixelization issues)
     */
    public static final int J2D_D3D_TEXTURE_TRANSFORM_OK   = (1 << 8);
    /**
     * Device can render clipped lines correctly.
     */
    public static final int J2D_D3D_LINE_CLIPPING_OK       = (1 << 9);
    /**
     * Device has all hw capabilities the d3d pipeline requires
     */
    public static final int J2D_D3D_DEVICE_OK              = (1 <<10);
    /**
     * Device supports all necessary texture formats required by d3d pipeline
     */
    public static final int J2D_D3D_PIXEL_FORMATS_OK       = (1 <<11);
    /**
     * Device supports geometry transformations
     */
    public static final int J2D_D3D_SET_TRANSFORM_OK       = (1 <<12);
    /**
     * The device is not from a list of known bad devices
     * (see D3DRuntimeTest.cpp)
     */
    public static final int J2D_D3D_HW_OK                  = (1 <<13);
    /**
     * Direct3D pipeline is enabled on this device
     */
    public static final int J2D_D3D_ENABLED_OK             = (1 <<14);

    /**
     * The lock object used to synchronize access to the native windowing
     * system layer.  Note that rendering methods should always synchronize on
     * D3DContext.LOCK before calling the D3DContext.getContext() method,
     * or any other method that invokes native D3d commands.
     * REMIND: in D3D case we should really be synchronizing on per-device
     * basis.
     */
    static Object LOCK;

    private Win32GraphicsDevice  gd;
    private boolean         valid;

    protected long          nativeContext;
    private SurfaceData     validatedDstData;
    private Region          validatedClip;
    private Composite       validatedComp;
    private int             validatedPixel;
    private int             validatedFlags;
    private boolean         xformInUse;
    // validated transform's data
    private double vScaleX, vScaleY, vShearX, vShearY, vTransX, vTransY;

    private int             deviceCaps;

    private native void setRenderTarget(long pCtx, long pDst);
    private native void setClip(long pCtx, long pDst, Region clip, boolean isRect,
                                int x1, int y1, int x2, int y2);
    private native void resetClip(long pCtx, long pDst);
    private native void resetComposite(long pCtx);
    private native void setAlphaComposite(long pCtx, int rule,
                                          float extraAlpha, int flags);
    private native void setTransform(long pCtx, long pDst,
                                     AffineTransform xform,
                                     double m00, double m10, double m01,
                                     double m11, double m02, double m12);
    private native void resetTransform(long pCtx, long pDst);
    private native void setColor(long pCtx, int pixel, int flags);
    private native long initNativeContext(int screen);
    private native int getNativeDeviceCaps(long pCtx);

    static {
        if (!GraphicsEnvironment.isHeadless()) {
            LOCK = D3DContext.class;
        }
    }

    public D3DContext(Win32GraphicsDevice gd) {
        this.gd = gd;
        reinitNativeContext();
    }

    /**
     * Reinitializes the context by retrieving a pointer to the native
     * D3DContext object, and resetting the device caps.
     */
    void reinitNativeContext() {
        nativeContext = initNativeContext(gd.getScreen());
        deviceCaps = nativeContext != 0L ?
            getNativeDeviceCaps(nativeContext) : J2D_D3D_FAILURE;
        valid = ((deviceCaps & J2D_D3D_ENABLED_OK) != 0);
        if (WindowsFlags.isD3DVerbose()) {
            if (valid) {
                System.out.println("Direct3D pipeline enabled on screen " +
                                   gd.getScreen());
            } else {
                System.out.println("Could not enable Direct3D pipeline on " +
                                   "screen " + gd.getScreen() +
                                   ". Device Caps: " +
                                   Integer.toHexString(deviceCaps));
            }
        }
    }

    /**
     * Invalidates this context by resetting its status: the validated
     * destination surface, and a pointer to the native context.
     * This method is called in the following cases:
     *  - if a surface loss situation is detected at the native level
     *    during any of the validation methods (setClip, setRenderTarget etc)
     *    and an InvalidPipeException is thrown.
     *    This situation happens when there was a surface loss, but
     *    there were no display change event (like in case of command prompt
     *    going fullscreen).
     *  - as part of surface restoration when a surface is the current
     *    target surface for this context. Since surface restoration
     *    resets the depth buffer contents, we need to make sure the clip
     *    is reset, and since the target surface is reset, we'll set a new
     *    clip the next time we attempt to render to the target surface.
     *  - when a display change occurs, the native D3DContext object is
     *    released and recreated as part of primary surface recreation.
     *    At the time of the release, the java D3DContext object need to be
     *    invalidated because a new D3D device is created and the target
     *    surface will need to be reset.
     *
     *  Invalidation of the context causes its revalidation the next time
     *  someone tries to get the D3DContext for rendering or creating a new
     *  surface.
     *
     *  @see #reinitNativeContext
     */
    private void invalidateContext() {
        valid = false;
        nativeContext = 0L;
        validatedDstData = null;
        // We don't set deviceCaps to J2D_D3D_FAILURE here because
        // it will prevent from creating d3d surfaces, which means that
        // we'll never get a chance to continue using d3d after a single
        // invalidation event (for example, a display change).
    }

    /**
     * Fetches the D3DContext associated with the current
     * thread/GraphicsConfig pair, validates the context using the given
     * parameters, then returns the handle to the native context object.
     * Most rendering operations will call this method first in order to
     * prepare the native D3d layer before issuing rendering commands.
     */
    static long getContext(SurfaceData srcData,
                           SurfaceData dstData,
                            Region clip, Composite comp,
                           AffineTransform xform,
                           int pixel, int flags)
    {
        if (dstData instanceof D3DSurfaceData == false) {
            throw new InvalidPipeException("Incorrect destination surface");
        }

        D3DContext d3dc = ((D3DSurfaceData)dstData).getContext();
        try {
            d3dc.validate(srcData, dstData, clip, comp, xform, pixel, flags);
        } catch (InvalidPipeException e) {
            d3dc.invalidateContext();
            // note that we do not propagate the exception. Once the context
            // is invalidated, any d3d rendering operations are noops, and
            // we are waiting for the primary surface restoration, which
            // happens when VolatileImage is validated. At this point
            // the native D3DContext will be reinitialized, and the next
            // time around validation of the context will succeed.
            // Throwing the exception here will do no good, since the
            // destination surface (which is associated with a VolatileImage
            // or a BufferStrategy) will not be restored until VI.validate()
            // is called by the rendering thread.
        }
        return d3dc.getNativeContext();
    }

    public int getDeviceCaps() {
        return deviceCaps;
    }

    boolean isRTTSupported() {
        return ((deviceCaps & J2D_D3D_OP_RTT_SURFACE_OK) != 0);
    }

    /**
     * Returns a handle to the native D3DContext structure associated with
     * this object.
     */
    long getNativeContext() {
        return nativeContext;
    }

    /**
     * Validates the given parameters against the current state for this
     * context.  If this context is not current, it will be made current
     * for the given source and destination surfaces, and the viewport will
     * be updated.  Then each part of the context state (clip, composite,
     * etc.) is checked against the previous value.  If the value has changed
     * since the last call to validate(), it will be updated accordingly.
     */
    private void validate(SurfaceData srcData, SurfaceData dstData,
                          Region clip, Composite comp, AffineTransform xform,
                          int pixel, int flags)
    {
        boolean updateClip = false;

        if ((srcData != null && !srcData.isValid()) || !dstData.isValid() ||
            dstData.getNativeOps() == 0L || dstData.isSurfaceLost())
        {
            throw new InvalidPipeException("Invalid surface");
        }

        if (!valid) {
            // attempt to reinitialize the context. If the device has been
            // reset, the following calls to setRenderTarget/setClip will
            // succeed and not throw InvalidPipeException.
            reinitNativeContext();
        }

        if (dstData != validatedDstData) {
            // invalidate pixel and clip (so they will be updated below)
            validatedPixel = ~pixel;
            updateClip = true;

            // update the viewport
            long pDst = dstData.getNativeOps();
            setRenderTarget(nativeContext, pDst);

            // keep the reference to the old data until we set the
            // new one on the native level, preventing it from being disposed
            SurfaceData tmpData = dstData;
            validatedDstData = dstData;
            tmpData = null;
        }
        // it's better to use dstData instead of validatedDstData because
        // the latter may be set to null via invalidateContext at any moment.
        long pDest = dstData.getNativeOps();

        // validate clip
        if ((clip != validatedClip) || updateClip) {
            if (clip != null) {
                /**
                 * It's cheaper to make this check than set clip every time.
                 *
                 * Set the new clip only if:
                 *  - we were asked to do it (updateClip == true)
                 *  - no clip was set before
                 *  - if both the old and the new clip are shapes
                 *  - if they're both rectangular but don't represent
                 *    the same rectangle
                 */
                if (updateClip ||
                    validatedClip == null ||
                    !(validatedClip.isRectangular() && clip.isRectangular()) ||
                    ((clip.getLoX() != validatedClip.getLoX() ||
                      clip.getLoY() != validatedClip.getLoY() ||
                      clip.getHiX() != validatedClip.getHiX() ||
                      clip.getHiY() != validatedClip.getHiY())))
                {
                    setClip(nativeContext, pDest,
                            clip, clip.isRectangular(),
                            clip.getLoX(), clip.getLoY(),
                            clip.getHiX(), clip.getHiY());
                }
            } else {
                resetClip(nativeContext, pDest);
            }
            validatedClip = clip;
        }

        if ((comp != validatedComp) || (flags != validatedFlags)) {
            // invalidate pixel
            validatedPixel = ~pixel;
            validatedComp = comp;
            if (comp != null) {
                AlphaComposite ac = (AlphaComposite)comp;
                setAlphaComposite(nativeContext, ac.getRule(),
                                  ac.getAlpha(), flags);
            } else {
                resetComposite(nativeContext);
            }
        }

        // validate transform
        if (xform == null) {
            if (xformInUse) {
                resetTransform(nativeContext, pDest);
                xformInUse = false;
                vScaleX = vScaleY = 1.0;
                vShearX = vShearY = vTransX = vTransY = 0.0;
            }
        } else {
            double nScaleX = xform.getScaleX();
            double nScaleY = xform.getScaleY();
            double nShearX = xform.getShearX();
            double nShearY = xform.getShearY();
            double nTransX = xform.getTranslateX();
            double nTransY = xform.getTranslateY();

            if (nTransX != vTransX || nTransY != vTransY ||
                nScaleX != vScaleX || nScaleY != vScaleY ||
                nShearX != vShearX || nShearY != vShearY)
            {
                setTransform(nativeContext, pDest,
                             xform,
                             nScaleX, nShearY, nShearX, nScaleY,
                             nTransX, nTransY);
                vScaleX = nScaleX;
                vScaleY = nScaleY;
                vShearX = nShearX;
                vShearY = nShearY;
                vTransX = nTransY;
                vTransY = nTransY;
                xformInUse = true;
            }
        }

        // validate pixel
        if (pixel != validatedPixel) {
            validatedPixel = pixel;
            setColor(nativeContext, pixel, flags);
        }

        // save flags for later comparison
        validatedFlags = flags;

        // mark dstData dirty
        dstData.markDirty();
    }
}
