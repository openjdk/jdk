/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.xr;

import sun.awt.X11ComponentPeer;
import sun.awt.X11GraphicsConfig;
import sun.awt.X11GraphicsDevice;
import sun.awt.X11GraphicsEnvironment;
import sun.awt.image.SurfaceManager;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.VolatileSurfaceManager;
import sun.java2d.SurfaceData;

public final class XRGraphicsConfig extends X11GraphicsConfig implements
        SurfaceManager.ProxiedGraphicsConfig {
    private final SurfaceManager.ProxyCache surfaceDataProxyCache =
            new SurfaceManager.ProxyCache();

    private XRGraphicsConfig(X11GraphicsDevice device, int visualnum,
            int depth, int colormap, boolean doubleBuffer) {
        super(device, visualnum, depth, colormap, doubleBuffer);
    }

    @Override
    public SurfaceData createSurfaceData(X11ComponentPeer peer) {
        return XRSurfaceData.createData(peer);
    }

    public static XRGraphicsConfig getConfig(X11GraphicsDevice device,
            int visualnum, int depth, int colormap, boolean doubleBuffer) {
        if (!X11GraphicsEnvironment.isXRenderAvailable()) {
            return null;
        }

        return new XRGraphicsConfig(device, visualnum, depth, colormap,
                doubleBuffer);
    }

    @Override
    public SurfaceManager.ProxyCache getSurfaceDataProxyCache() {
        return surfaceDataProxyCache;
    }

    @Override
    public VolatileSurfaceManager createVolatileManager(SunVolatileImage image,
                                                        Object context) {
        return new XRVolatileSurfaceManager(image, context);
    }
}
