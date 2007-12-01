/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.windows;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;
import sun.awt.image.SurfaceManager;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.VolatileSurfaceManager;
import sun.java2d.SurfaceData;

import sun.java2d.d3d.D3DBackBufferSurfaceData;

public class WinBackBuffer extends SunVolatileImage {

    /**
     * Create an image for an attached surface
     */
    public WinBackBuffer(Component c, Win32SurfaceData parentData) {
        super(c, c.getWidth(), c.getHeight(), parentData);
    }

    @Override
    protected VolatileSurfaceManager createSurfaceManager(Object context,
                                                          ImageCapabilities caps)
    {
        return new WinBackBufferSurfaceManager(this, context);
    }

    public Win32OffScreenSurfaceData getHWSurfaceData() {
        SurfaceData sd = SurfaceData.getPrimarySurfaceData(this);
        return (sd instanceof Win32OffScreenSurfaceData) ?
            (Win32OffScreenSurfaceData)sd : null;
    }

    private class WinBackBufferSurfaceManager
        extends WinVolatileSurfaceManager
    {
        public WinBackBufferSurfaceManager(SunVolatileImage vImg,
                                           Object context)
        {
            super(vImg, context);
        }

        protected Win32OffScreenSurfaceData createAccelSurface() {
            GraphicsConfiguration gc = vImg.getGraphicsConfig();
            ColorModel cm = getDeviceColorModel();
            Win32SurfaceData parent = (Win32SurfaceData)context;

            Win32OffScreenSurfaceData ret =
                D3DBackBufferSurfaceData.createData(vImg.getWidth(),
                                                    vImg.getHeight(),
                                                    cm, gc, vImg, parent);
            if (ret == null) {
                ret = WinBackBufferSurfaceData.createData(vImg.getWidth(),
                                                          vImg.getHeight(),
                                                          cm, gc, vImg, parent);
            }
            return ret;
        }

        /**
         * Removes this surface manager from the display change listeners.
         * Since the user don't have access to the VolatileImage
         * representing the backbuffer, we know that nobody but us
         * can call it. And we do it when the backbuffer is replaced.
         */
        public void flush() {
            sun.awt.Win32GraphicsEnvironment ge =
                    (sun.awt.Win32GraphicsEnvironment)
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.removeDisplayChangedListener(this);
        }
    }
}
