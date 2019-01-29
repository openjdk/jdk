/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTPermission;
import java.awt.DisplayMode;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import sun.java2d.SunGraphicsEnvironment;
import sun.java2d.opengl.CGLGraphicsConfig;

public final class CGraphicsDevice extends GraphicsDevice
        implements DisplayChangedListener {

    /**
     * CoreGraphics display ID. This identifier can become non-valid at any time
     * therefore methods, which is using this id should be ready to it.
     */
    private volatile int displayID;
    private volatile double xResolution;
    private volatile double yResolution;
    private volatile Rectangle bounds;
    private volatile int scale;

    private final GraphicsConfiguration config;

    private static AWTPermission fullScreenExclusivePermission;

    // Save/restore DisplayMode for the Full Screen mode
    private DisplayMode originalMode;

    public CGraphicsDevice(final int displayID) {
        this.displayID = displayID;
        config = CGLGraphicsConfig.getConfig(this, displayID, 0);
    }

    /**
     * Return a list of all configurations.
     */
    @Override
    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[]{config};
    }

    /**
     * Return the default configuration.
     */
    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        return config;
    }

    /**
     * Return a human-readable screen description.
     */
    @Override
    public String getIDstring() {
        return "Display " + displayID;
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
        return xResolution;
    }

    public double getYResolution() {
        return yResolution;
    }

    Rectangle getBounds() {
        return bounds.getBounds();
    }

    public Insets getScreenInsets() {
        // the insets are queried synchronously and are not cached
        // since there are no Quartz or Cocoa means to receive notifications
        // on insets changes (e.g. when the Dock is resized):
        // the existing CGDisplayReconfigurationCallBack is not notified
        // as well as the NSApplicationDidChangeScreenParametersNotification
        // is fired on the Dock location changes only
        return nativeGetScreenInsets(displayID);
    }

    public int getScaleFactor() {
        return scale;
    }

    public void invalidate(final int defaultDisplayID) {
        displayID = defaultDisplayID;
    }

    @Override
    public void displayChanged() {
        xResolution = nativeGetXResolution(displayID);
        yResolution = nativeGetYResolution(displayID);
        bounds = nativeGetBounds(displayID).getBounds(); //does integer rounding
        initScaleFactor();
        //TODO configs/fullscreenWindow/modes?
    }

    @Override
    public void paletteChanged() {
        // devices do not need to react to this event.
    }

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
            // enter windowed mode and restore original display mode
            exitFullScreenExclusive(old);
            if (originalMode != null) {
                setDisplayMode(originalMode);
                originalMode = null;
            }
        }

        super.setFullScreenWindow(w);

        if (fsSupported && w != null) {
            if (isDisplayChangeSupported()) {
                originalMode = getDisplayMode();
            }
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
        FullScreenCapable peer = AWTAccessor.getComponentAccessor().getPeer(w);
        if (peer != null) {
            peer.enterFullScreenMode();
        }
    }

    private static void exitFullScreenExclusive(Window w) {
        FullScreenCapable peer = AWTAccessor.getComponentAccessor().getPeer(w);
        if (peer != null) {
            peer.exitFullScreenMode();
        }
    }

    @Override
    public boolean isDisplayChangeSupported() {
        return true;
    }

    @Override
    public void setDisplayMode(final DisplayMode dm) {
        if (dm == null) {
            throw new IllegalArgumentException("Invalid display mode");
        }
        if (!Objects.equals(dm, getDisplayMode())) {
            nativeSetDisplayMode(displayID, dm.getWidth(), dm.getHeight(),
                    dm.getBitDepth(), dm.getRefreshRate());
            if (isFullScreenSupported() && getFullScreenWindow() != null) {
                getFullScreenWindow().setSize(dm.getWidth(), dm.getHeight());
            }
        }
    }

    @Override
    public DisplayMode getDisplayMode() {
        return nativeGetDisplayMode(displayID);
    }

    @Override
    public DisplayMode[] getDisplayModes() {
        return nativeGetDisplayModes(displayID);
    }

    private void initScaleFactor() {
        if (SunGraphicsEnvironment.isUIScaleEnabled()) {
            double debugScale = SunGraphicsEnvironment.getDebugScale();
            scale = (int) (debugScale >= 1
                    ? Math.round(debugScale)
                    : nativeGetScaleFactor(displayID));
        } else {
            scale = 1;
        }
    }

    private static native double nativeGetScaleFactor(int displayID);

    private static native void nativeSetDisplayMode(int displayID, int w, int h, int bpp, int refrate);

    private static native DisplayMode nativeGetDisplayMode(int displayID);

    private static native DisplayMode[] nativeGetDisplayModes(int displayID);

    private static native double nativeGetXResolution(int displayID);

    private static native double nativeGetYResolution(int displayID);

    private static native Insets nativeGetScreenInsets(int displayID);

    private static native Rectangle2D nativeGetBounds(int displayID);
}
