/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.awt;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;

import sun.java2d.SurfaceData;
import sun.java2d.opengl.CGLLayer;
import sun.lwawt.macosx.CPlatformView;

public class CGraphicsConfig extends GraphicsConfiguration {
    private final CGraphicsDevice device;
    private ColorModel colorModel;

    public CGraphicsConfig(CGraphicsDevice device) {
        this.device = device;
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static native Rectangle2D nativeGetBounds(int screen);

    @Override
    public Rectangle getBounds() {
        final Rectangle2D nativeBounds = nativeGetBounds(device.getCoreGraphicsScreen());
        return nativeBounds.getBounds(); // does integer rounding
    }

    @Override
    public ColorModel getColorModel() {
        if (colorModel == null) {
            colorModel = getColorModel(Transparency.OPAQUE);
        }
        return colorModel;
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public AffineTransform getDefaultTransform() {
        return new AffineTransform();
    }

    @Override
    public CGraphicsDevice getDevice() {
        return device;
    }

    @Override
    public AffineTransform getNormalizingTransform() {
        double xscale = device.getXResolution() / 72.0;
        double yscale = device.getYResolution() / 72.0;
        return new AffineTransform(xscale, 0.0, 0.0, yscale, 0.0, 0.0);
    }


    /**
     * The following methods are invoked from CToolkit.java and
     * LWWindowPeer.java rather than having the native
     * implementations hardcoded in those classes.  This way the appropriate
     * actions are taken based on the peer's GraphicsConfig, whether it is
     * an CGLGraphicsConfig or something else.
     */

    /**
     * Creates a new SurfaceData that will be associated with the given
     * LWWindowPeer.
     */
    public SurfaceData createSurfaceData(CPlatformView pView) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Creates a new SurfaceData that will be associated with the given
     * CGLLayer.
     */
    public SurfaceData createSurfaceData(CGLLayer layer) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Creates a new hidden-acceleration image of the given width and height
     * that is associated with the target Component.
     */
    public Image createAcceleratedImage(Component target,
                                        int width, int height)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * The following methods correspond to the multibuffering methods in
     * LWWindowPeer.java...
     */

    /**
     * Attempts to create a native backbuffer for the given peer.  If
     * the requested configuration is not natively supported, an AWTException
     * is thrown.  Otherwise, if the backbuffer creation is successful, a
     * handle to the native backbuffer is returned.
     */
    public long createBackBuffer(CPlatformView pView,
                                 int numBuffers, BufferCapabilities caps)
        throws AWTException
    {
        throw new UnsupportedOperationException("not implemented");
    }

    public void destroyBackBuffer(long backBuffer)
        throws AWTException
    {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Creates a VolatileImage that essentially wraps the target Component's
     * backbuffer, using the provided backbuffer handle.
     */
    public VolatileImage createBackBufferImage(Component target,
                                               long backBuffer)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Performs the native flip operation for the given target Component.
     */
    public void flip(CPlatformView delegate,
                     Component target, VolatileImage xBackBuffer,
                     int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction)
    {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean isTranslucencyCapable() {
        //we know for sure we have capable config :)
        return true;
    }
}
