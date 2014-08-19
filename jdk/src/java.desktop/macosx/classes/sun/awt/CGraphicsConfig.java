/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import sun.lwawt.LWGraphicsConfig;
import sun.lwawt.macosx.CPlatformView;

public abstract class CGraphicsConfig extends GraphicsConfiguration
        implements LWGraphicsConfig {

    private final CGraphicsDevice device;
    private ColorModel colorModel;

    protected CGraphicsConfig(CGraphicsDevice device) {
        this.device = device;
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        throw new UnsupportedOperationException("not implemented");
    }

    private static native Rectangle2D nativeGetBounds(int screen);

    @Override
    public Rectangle getBounds() {
        final Rectangle2D nativeBounds = nativeGetBounds(device.getCGDisplayID());
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
     * Creates a new SurfaceData that will be associated with the given
     * LWWindowPeer.
     */
    public abstract SurfaceData createSurfaceData(CPlatformView pView);

    /**
     * Creates a new SurfaceData that will be associated with the given
     * CGLLayer.
     */
    public abstract SurfaceData createSurfaceData(CGLLayer layer);

    @Override
    public final boolean isTranslucencyCapable() {
        //we know for sure we have capable config :)
        return true;
    }
}
