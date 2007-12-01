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

import java.awt.Color;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import sun.awt.DisplayChangedListener;
import sun.awt.Win32GraphicsConfig;
import sun.awt.Win32GraphicsDevice;
import sun.awt.Win32GraphicsEnvironment;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.VolatileSurfaceManager;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.d3d.D3DSurfaceData;

/**
 * Windows platform implementation of the VolatileSurfaceManager class.
 * Th superclass implementation handles the case of surface loss due
 * to displayChange or other events.  This class attempts to create
 * and use a hardware-based SurfaceData object (Win32OffScreenSurfaceData).
 * If this object cannot be created or re-created as necessary, the
 * class falls back to a software-based SurfaceData object
 * (BufImgSurfaceData) that will be used until the hardware-based
 * SurfaceData can be restored.
 */
public class WinVolatileSurfaceManager
    extends VolatileSurfaceManager
{
    private boolean accelerationEnabled;

    /**
     * Controls whether the manager should attempt to create a
     * D3DSurfaceData to accelerate the image.
     *
     * The default is the value of accelerationEnabled, but the value could
     * change during the life of this SurfaceManager.
     */
    private boolean d3dAccelerationEnabled;

    public WinVolatileSurfaceManager(SunVolatileImage vImg, Object context) {
        super(vImg, context);

        /* We enable acceleration only if all of the following are true:
         * - ddraw is enabled
         * - ddraw offscreen surfaces are enabled
         * - Either:
         *    - the image is opaque OR
         *    - the image is translucent and translucency acceleration
         *      is enabled on this device
         * There is no acceleration for bitmask images yet because the
         * process to convert transparent pixels into ddraw colorkey
         * values is not worth the effort and time.  We should eventually
         * accelerate transparent images the same way we do translucent
         * ones; through translucent textures (transparent pixels would
         * simply have an alpha of 0).
         */
        Win32GraphicsDevice gd =
            (Win32GraphicsDevice)vImg.getGraphicsConfig().getDevice();
        accelerationEnabled =
            WindowsFlags.isDDEnabled() &&
            WindowsFlags.isDDOffscreenEnabled() &&
            (vImg.getTransparency() == Transparency.OPAQUE);
        // REMIND: we don't really accelerate non-opaque VIs yet,
        // since we'll need RTT for that
//          ||
//           ((vImg.getTransparency() == Transparency.TRANSLUCENT) &&
//            WindowsFlags.isTranslucentAccelerationEnabled() &&
//            gd.isD3DEnabledOnDevice()));

        d3dAccelerationEnabled = accelerationEnabled;
    }

    protected SurfaceData createAccelSurface() {
        int transparency = vImg.getTransparency();
        ColorModel cm;
        Win32GraphicsConfig gc = (Win32GraphicsConfig) vImg.getGraphicsConfig();
        if (transparency != Transparency.TRANSLUCENT) {
            // REMIND: This will change when we accelerate bitmask VImages.
            // Currently, we can only reach here if the image is either
            // opaque or translucent
            cm = getDeviceColorModel();
        } else {
            cm = gc.getColorModel(Transparency.TRANSLUCENT);
        }

        // createData will return null if the device doesnt support d3d surfaces
        SurfaceData ret = null;
        // avoid pulling in D3D classes unless d3d is enabled on the device
        if (d3dAccelerationEnabled &&
            ((Win32GraphicsDevice)gc.getDevice()).isD3DEnabledOnDevice())
        {
            try {
                ret =
                    D3DSurfaceData.createData(vImg.getWidth(), vImg.getHeight(),
                                              D3DSurfaceData.D3D_PLAIN_SURFACE,
                                              cm, gc, vImg);
            } catch (sun.java2d.InvalidPipeException e) {
                // exception is ignored, ret will be null so code
                // below will create a non-d3d surface
            }
        }

        if (ret == null) {
            ret =  Win32OffScreenSurfaceData.createData(vImg.getWidth(),
                                                        vImg.getHeight(),
                                                        cm, gc, vImg,
                                                        transparency);
        }
        return ret;
    }

    public boolean isAccelerationEnabled() {
        return accelerationEnabled;
    }

    /**
     *
     * @param enabled if true, enable both DirectDraw and Direct3D
     * acceleration for this surface manager, disable both if false
     */
    public void setAccelerationEnabled(boolean enabled) {
        if (enabled != accelerationEnabled) {
            sdCurrent = getBackupSurface();
            sdAccel = null;
            accelerationEnabled = enabled;
        }
        d3dAccelerationEnabled = enabled;
    }

    /**
     * Controls whether this surface manager should attempt to accelerate
     * the image using the Direct3D pipeline.
     *
     * If the state changes, sdCurrent will be reset to a backup surface,
     * and sdAccel will be nulled out so that a new surface is created
     * during the following validation.
     *
     * @param enabled if true, enable d3d acceleration for this SM,
     * disable otherwise.
     */
    public void setD3DAccelerationEnabled(boolean enabled) {
        if (enabled != d3dAccelerationEnabled) {
            sdCurrent = getBackupSurface();
            sdAccel = null;
            d3dAccelerationEnabled = enabled;
        }
    }


    /**
     * Create a vram-based SurfaceData object
     */
    public sun.java2d.SurfaceData initAcceleratedSurface() {
        SurfaceData sData;

        try {
            sData = createAccelSurface();
        } catch (sun.java2d.InvalidPipeException e) {
            // Problems during creation.  Don't propagate the exception, just
            // set the hardware surface data to null; the software surface
            // data will be used in the meantime
            sData = null;
        }
        return sData;
    }

    /**
     * Called from Win32OffScreenSurfaceData to notify us that our
     * accelerated surface has been lost.
     */
    public SurfaceData restoreContents() {
        acceleratedSurfaceLost();
        return super.restoreContents();
    }

    protected ColorModel getDeviceColorModel() {
        Win32GraphicsConfig gc = (Win32GraphicsConfig)vImg.getGraphicsConfig();
        return gc.getDeviceColorModel();
    }

    /**
     * Called from superclass to force restoration of this surface
     * during the validation process.  The method calls into the
     * hardware SurfaceData object to force the restore.
     */
    protected void restoreAcceleratedSurface() {
        ((Win32OffScreenSurfaceData)sdAccel).restoreSurface();
    }
}
