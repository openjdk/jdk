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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Window;
import java.awt.AWTPermission;

import sun.java2d.opengl.CGLGraphicsConfig;

import sun.awt.FullScreenCapable;

public class CGraphicsDevice extends GraphicsDevice {

    // CoreGraphics display ID
    private final int displayID;

    // Array of all GraphicsConfig instances for this device
    private final GraphicsConfiguration[] configs;

    // Default config (temporarily hard coded)
    private final int DEFAULT_CONFIG = 0;

    private static AWTPermission fullScreenExclusivePermission;

    public CGraphicsDevice(int displayID) {
        this.displayID = displayID;
        configs = new GraphicsConfiguration[] {
            CGLGraphicsConfig.getConfig(this, 0)
        };
    }

    /**
     * @return CoreGraphics display id.
     */
    public int getCoreGraphicsScreen() {
        return displayID;
    }

    /**
     * Return a list of all configurations.
     */
    @Override
    public GraphicsConfiguration[] getConfigurations() {
        return configs.clone();
    }

    /**
     * Return the default configuration.
     */
    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        return configs[DEFAULT_CONFIG];
    }

    /**
     * Return a human-readable screen description.
     */
    @Override
    public String getIDstring() {
        return "Display " + this.displayID;
    }

    /**
     * Returns the type of the graphics device.
     * @see #TYPE_RASTER_SCREEN
     * @see #TYPE_PRINTER
     * @see #TYPE_IMAGE_BUFFER
     */
    @Override
    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    public double getXResolution() {
        return nativeGetXResolution(displayID);
    }

    public double getYResolution() {
        return nativeGetYResolution(displayID);
    }

    public int getScreenResolution() {
        // TODO: report non-72 value when HiDPI is turned on
        return 72;
    }

    private static native double nativeGetXResolution(int displayID);
    private static native double nativeGetYResolution(int displayID);

    /**
     * Enters full-screen mode, or returns to windowed mode.
     */
    @Override
    public synchronized void setFullScreenWindow(Window w) {
        Window old = getFullScreenWindow();
        if (w == old) {
            return;
        }

        boolean fsSupported = isFullScreenSupported();
        if (fsSupported && old != null) {
            // enter windowed mode (and restore original display mode)
            exitFullScreenExclusive(old);

            // TODO: restore display mode
        }

        super.setFullScreenWindow(w);

        if (fsSupported && w != null) {
            // TODO: save current display mode

            // enter fullscreen mode
            enterFullScreenExclusive(w);
        }
    }

    /**
     * Returns true if this GraphicsDevice supports
     * full-screen exclusive mode and false otherwise.
     */
    @Override
    public boolean isFullScreenSupported() {
        return isFSExclusiveModeAllowed();
    }

    private static boolean isFSExclusiveModeAllowed() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            if (fullScreenExclusivePermission == null) {
                fullScreenExclusivePermission =
                    new AWTPermission("fullScreenExclusive");
            }
            try {
                security.checkPermission(fullScreenExclusivePermission);
            } catch (SecurityException e) {
                return false;
            }
        }
        return true;
    }

    private static void enterFullScreenExclusive(Window w) {
        FullScreenCapable peer = (FullScreenCapable)w.getPeer();
        if (peer != null) {
            peer.enterFullScreenMode();
        }
    }

    private static void exitFullScreenExclusive(Window w) {
        FullScreenCapable peer = (FullScreenCapable)w.getPeer();
        if (peer != null) {
            peer.exitFullScreenMode();
        }
    }
}
