/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import sun.awt.X11ComponentPeer;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.VolatileSurfaceManager;
import sun.java2d.SurfaceData;

public class GLXVolatileSurfaceManager extends VolatileSurfaceManager {

    private boolean accelerationEnabled;

    public GLXVolatileSurfaceManager(SunVolatileImage vImg, Object context) {
        super(vImg, context);

        /*
         * We will attempt to accelerate this image only under the
         * following conditions:
         *   - the image is opaque OR
         *   - the image is translucent AND
         *       - the GraphicsConfig supports the FBO extension OR
         *       - the GraphicsConfig has a stored alpha channel
         */
        int transparency = vImg.getTransparency();
        GLXGraphicsConfig gc = (GLXGraphicsConfig)vImg.getGraphicsConfig();
        accelerationEnabled =
            (transparency == Transparency.OPAQUE) ||
            ((transparency == Transparency.TRANSLUCENT) &&
             (gc.isCapPresent(OGLContext.CAPS_EXT_FBOBJECT) ||
              gc.isCapPresent(OGLContext.CAPS_STORED_ALPHA)));
    }

    protected boolean isAccelerationEnabled() {
        return accelerationEnabled;
    }

    /**
     * Create a pbuffer-based SurfaceData object (or init the backbuffer
     * of an existing window if this is a double buffered GraphicsConfig)
     */
    protected SurfaceData initAcceleratedSurface() {
        SurfaceData sData;
        Component comp = vImg.getComponent();
        X11ComponentPeer peer =
            (comp != null) ? (X11ComponentPeer)comp.getPeer() : null;

        try {
            boolean forceback = false;
            if (context instanceof Boolean) {
                forceback = ((Boolean)context).booleanValue();
            }

            if (forceback) {
                // peer must be non-null in this case
                sData = GLXSurfaceData.createData(peer, vImg);
            } else {
                GLXGraphicsConfig gc =
                    (GLXGraphicsConfig)vImg.getGraphicsConfig();
                ColorModel cm = gc.getColorModel(vImg.getTransparency());
                int type = gc.isCapPresent(OGLContext.CAPS_EXT_FBOBJECT) ?
                    OGLSurfaceData.FBOBJECT : OGLSurfaceData.PBUFFER;
                sData = GLXSurfaceData.createData(gc,
                                                  vImg.getWidth(),
                                                  vImg.getHeight(),
                                                  cm, vImg, type);
            }
        } catch (NullPointerException ex) {
            sData = null;
        } catch (OutOfMemoryError er) {
            sData = null;
        }

        return sData;
    }

    protected boolean isConfigValid(GraphicsConfiguration gc) {
        return ((gc == null) || (gc == vImg.getGraphicsConfig()));
    }
}
