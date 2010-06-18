/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.xr;

import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.image.ColorModel;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.VolatileSurfaceManager;
import sun.java2d.SurfaceData;

/**
 * XRender platform implementation of the VolatileSurfaceManager class.
 */
public class XRVolatileSurfaceManager extends VolatileSurfaceManager {

    public XRVolatileSurfaceManager(SunVolatileImage vImg, Object context) {
        super(vImg, context);
    }

    protected boolean isAccelerationEnabled() {
        return true;
    }

    /**
     * Create a pixmap-based SurfaceData object
     */
    protected SurfaceData initAcceleratedSurface() {
        SurfaceData sData;

        try {
            XRGraphicsConfig gc = (XRGraphicsConfig) vImg.getGraphicsConfig();
            ColorModel cm = gc.getColorModel();
            long drawable = 0;
            if (context instanceof Long) {
                drawable = ((Long)context).longValue();
            }
            sData = XRSurfaceData.createData(gc,
                                              vImg.getWidth(),
                                              vImg.getHeight(),
                                              cm, vImg, drawable,
                                              vImg.getTransparency());
        } catch (NullPointerException ex) {
            sData = null;
        } catch (OutOfMemoryError er) {
            sData = null;
        }

        return sData;
    }

   /**
    * XRender should allow copies between different formats and depths.
    * TODO: verify that this assumption is correct.
    */
    protected boolean isConfigValid(GraphicsConfiguration gc) {
        return true;
    }

    /**
     * Need to override the default behavior because Pixmaps-based
     * images are accelerated but not volatile.
     */
    @Override
    public ImageCapabilities getCapabilities(GraphicsConfiguration gc) {
        if (isConfigValid(gc) && isAccelerationEnabled()) {
            return new ImageCapabilities(true);
        }
        return new ImageCapabilities(false);
    }
}
