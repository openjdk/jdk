/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import sun.awt.Win32GraphicsDevice;
import sun.java2d.loops.SurfaceType;
import sun.java2d.windows.Win32SurfaceData;

public class D3DBackBufferSurfaceData extends D3DSurfaceData {

    private Win32SurfaceData parentData;

    /**
     * Private constructor.  Use createData() to create an object.
     */
    private D3DBackBufferSurfaceData(int width, int height,
                                     SurfaceType sType, ColorModel cm,
                                     GraphicsConfiguration gc,
                                     Image image, int screen,
                                     Win32SurfaceData parentData)
    {
        super(width, height, D3DSurfaceData.D3D_ATTACHED_SURFACE,
              sType, cm, gc, image, Transparency.OPAQUE);
        this.parentData = parentData;
        initSurface(width, height, screen, parentData);
    }

    private native void restoreDepthBuffer();

    @Override
    public void restoreSurface() {
        parentData.restoreSurface();
        // The above call restores the primary surface
        // to which this backbuffer is attached. But
        // we need to explicitly restore the depth buffer
        // associated with this backbuffer surface, because it's not
        // part of a 'complex' primary surface, and thus will not be
        // restored as part of the primary surface restoration.
        restoreDepthBuffer();
    }

    public static D3DBackBufferSurfaceData
        createData(int width, int height,
                   ColorModel cm, GraphicsConfiguration gc,
                   Image image,
                   Win32SurfaceData parentData)
    {
        Win32GraphicsDevice gd = (Win32GraphicsDevice)gc.getDevice();
        if (!gd.isD3DEnabledOnDevice()) {
            return null;
        }
        SurfaceType sType = getSurfaceType(cm, Transparency.OPAQUE);
        return new
            D3DBackBufferSurfaceData(width, height,
                 getSurfaceType(gc, cm,
                                D3DSurfaceData.D3D_ATTACHED_SURFACE),
                 cm, gc, image,
                 gd.getScreen(), parentData);
    }
}
