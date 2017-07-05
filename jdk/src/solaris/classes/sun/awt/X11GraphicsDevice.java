/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt;

import java.awt.AWTPermission;
import java.awt.DisplayMode;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Window;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

import sun.java2d.opengl.GLXGraphicsConfig;
import sun.java2d.loops.SurfaceType;

/**
 * This is an implementation of a GraphicsDevice object for a single
 * X11 screen.
 *
 * @see GraphicsEnvironment
 * @see GraphicsConfiguration
 */
public class X11GraphicsDevice
    extends GraphicsDevice
    implements DisplayChangedListener
{
    int screen;
    HashMap x11ProxyKeyMap = new HashMap();

    private static AWTPermission fullScreenExclusivePermission;
    private static Boolean xrandrExtSupported;
    private final Object configLock = new Object();
    private SunDisplayChanger topLevels = new SunDisplayChanger();
    private DisplayMode origDisplayMode;
    private boolean shutdownHookRegistered;

    public X11GraphicsDevice(int screennum) {
        this.screen = screennum;
    }

    /*
     * Initialize JNI field and method IDs for fields that may be
     * accessed from C.
     */
    private static native void initIDs();

    static {
        if (!GraphicsEnvironment.isHeadless()) {
            initIDs();
        }
    }

    /**
     * Returns the X11 screen of the device.
     */
    public int getScreen() {
        return screen;
    }

    public Object getProxyKeyFor(SurfaceType st) {
        synchronized (x11ProxyKeyMap) {
            Object o = x11ProxyKeyMap.get(st);
            if (o == null) {
                o = new Object();
                x11ProxyKeyMap.put(st, o);
            }
            return o;
        }
    }

    /**
     * Returns the X11 Display of this device.
     * This method is also in MDrawingSurfaceInfo but need it here
     * to be able to allow a GraphicsConfigTemplate to get the Display.
     */
    public native long getDisplay();

    /**
     * Returns the type of the graphics device.
     * @see #TYPE_RASTER_SCREEN
     * @see #TYPE_PRINTER
     * @see #TYPE_IMAGE_BUFFER
     */
    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    /**
     * Returns the identification string associated with this graphics
     * device.
     */
    public String getIDstring() {
        return ":0."+screen;
    }


    GraphicsConfiguration[] configs;
    GraphicsConfiguration defaultConfig;
    HashSet doubleBufferVisuals;

    /**
     * Returns all of the graphics
     * configurations associated with this graphics device.
     */
    public GraphicsConfiguration[] getConfigurations() {
        if (configs == null) {
            synchronized (configLock) {
                makeConfigurations();
            }
        }
        return configs.clone();
    }

    private void makeConfigurations() {
        if (configs == null) {
            int i = 1;  // Index 0 is always the default config
            int num = getNumConfigs(screen);
            GraphicsConfiguration[] ret = new GraphicsConfiguration[num];
            if (defaultConfig == null) {
                ret [0] = getDefaultConfiguration();
            }
            else {
                ret [0] = defaultConfig;
            }

            boolean glxSupported = X11GraphicsEnvironment.isGLXAvailable();
            boolean dbeSupported = isDBESupported();
            if (dbeSupported && doubleBufferVisuals == null) {
                doubleBufferVisuals = new HashSet();
                getDoubleBufferVisuals(screen);
            }
            for ( ; i < num; i++) {
                int visNum = getConfigVisualId(i, screen);
                int depth = getConfigDepth (i, screen);
                if (glxSupported) {
                    ret[i] = GLXGraphicsConfig.getConfig(this, visNum);
                }
                if (ret[i] == null) {
                    boolean doubleBuffer =
                        (dbeSupported &&
                         doubleBufferVisuals.contains(Integer.valueOf(visNum)));
                    ret[i] = X11GraphicsConfig.getConfig(this, visNum, depth,
                            getConfigColormap(i, screen),
                            doubleBuffer);
                }
            }
            configs = ret;
        }
    }

    /*
     * Returns the number of X11 visuals representable as an
     * X11GraphicsConfig object.
     */
    public native int getNumConfigs(int screen);

    /*
     * Returns the visualid for the given index of graphics configurations.
     */
    public native int getConfigVisualId (int index, int screen);
    /*
     * Returns the depth for the given index of graphics configurations.
     */
    public native int getConfigDepth (int index, int screen);

    /*
     * Returns the colormap for the given index of graphics configurations.
     */
    public native int getConfigColormap (int index, int screen);


    // Whether or not double-buffering extension is supported
    public static native boolean isDBESupported();
    // Callback for adding a new double buffer visual into our set
    private void addDoubleBufferVisual(int visNum) {
        doubleBufferVisuals.add(Integer.valueOf(visNum));
    }
    // Enumerates all visuals that support double buffering
    private native void getDoubleBufferVisuals(int screen);

    /**
     * Returns the default graphics configuration
     * associated with this graphics device.
     */
    public GraphicsConfiguration getDefaultConfiguration() {
        if (defaultConfig == null) {
            synchronized (configLock) {
                makeDefaultConfiguration();
            }
        }
        return defaultConfig;
    }

    private void makeDefaultConfiguration() {
        if (defaultConfig == null) {
            int visNum = getConfigVisualId(0, screen);
            if (X11GraphicsEnvironment.isGLXAvailable()) {
                defaultConfig = GLXGraphicsConfig.getConfig(this, visNum);
                if (X11GraphicsEnvironment.isGLXVerbose()) {
                    if (defaultConfig != null) {
                        System.out.print("OpenGL pipeline enabled");
                    } else {
                        System.out.print("Could not enable OpenGL pipeline");
                    }
                    System.out.println(" for default config on screen " +
                                       screen);
                }
            }
            if (defaultConfig == null) {
                int depth = getConfigDepth(0, screen);
                boolean doubleBuffer = false;
                if (isDBESupported() && doubleBufferVisuals == null) {
                    doubleBufferVisuals = new HashSet();
                    getDoubleBufferVisuals(screen);
                    doubleBuffer =
                        doubleBufferVisuals.contains(Integer.valueOf(visNum));
                }
                defaultConfig = X11GraphicsConfig.getConfig(this, visNum,
                                                            depth, getConfigColormap(0, screen),
                                                            doubleBuffer);
            }
        }
    }

    private static native void enterFullScreenExclusive(long window);
    private static native void exitFullScreenExclusive(long window);
    private static native boolean initXrandrExtension();
    private static native DisplayMode getCurrentDisplayMode(int screen);
    private static native void enumDisplayModes(int screen,
                                                ArrayList<DisplayMode> modes);
    private static native void configDisplayMode(int screen,
                                                 int width, int height,
                                                 int displayMode);
    private static native void resetNativeData(int screen);

    /**
     * Returns true only if:
     *   - the Xrandr extension is present
     *   - the necessary Xrandr functions were loaded successfully
     *   - XINERAMA is not enabled
     */
    private static synchronized boolean isXrandrExtensionSupported() {
        if (xrandrExtSupported == null) {
            xrandrExtSupported =
                Boolean.valueOf(initXrandrExtension());
        }
        return xrandrExtSupported.booleanValue();
    }

    @Override
    public boolean isFullScreenSupported() {
        // REMIND: for now we will only allow fullscreen exclusive mode
        // on the primary screen; we could change this behavior slightly
        // in the future by allowing only one screen to be in fullscreen
        // exclusive mode at any given time...
        boolean fsAvailable = (screen == 0) && isXrandrExtensionSupported();
        if (fsAvailable) {
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
        }
        return fsAvailable;
    }

    @Override
    public boolean isDisplayChangeSupported() {
        return (isFullScreenSupported() && (getFullScreenWindow() != null));
    }

    private static void enterFullScreenExclusive(Window w) {
        X11ComponentPeer peer = (X11ComponentPeer)w.getPeer();
        if (peer != null) {
            enterFullScreenExclusive(peer.getContentWindow());
            peer.setFullScreenExclusiveModeState(true);
        }
    }

    private static void exitFullScreenExclusive(Window w) {
        X11ComponentPeer peer = (X11ComponentPeer)w.getPeer();
        if (peer != null) {
            peer.setFullScreenExclusiveModeState(false);
            exitFullScreenExclusive(peer.getContentWindow());
        }
    }

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
            setDisplayMode(origDisplayMode);
        }

        super.setFullScreenWindow(w);

        if (fsSupported && w != null) {
            // save original display mode
            if (origDisplayMode == null) {
                origDisplayMode = getDisplayMode();
            }

            // enter fullscreen mode
            enterFullScreenExclusive(w);
        }
    }

    private DisplayMode getDefaultDisplayMode() {
        GraphicsConfiguration gc = getDefaultConfiguration();
        Rectangle r = gc.getBounds();
        return new DisplayMode(r.width, r.height,
                               DisplayMode.BIT_DEPTH_MULTI,
                               DisplayMode.REFRESH_RATE_UNKNOWN);
    }

    @Override
    public synchronized DisplayMode getDisplayMode() {
        if (isFullScreenSupported()) {
            return getCurrentDisplayMode(screen);
        } else {
            if (origDisplayMode == null) {
                origDisplayMode = getDefaultDisplayMode();
            }
            return origDisplayMode;
        }
    }

    @Override
    public synchronized DisplayMode[] getDisplayModes() {
        if (!isFullScreenSupported()) {
            return super.getDisplayModes();
        }
        ArrayList<DisplayMode> modes = new ArrayList<DisplayMode>();
        enumDisplayModes(screen, modes);
        DisplayMode[] retArray = new DisplayMode[modes.size()];
        return modes.toArray(retArray);
    }

    @Override
    public synchronized void setDisplayMode(DisplayMode dm) {
        if (!isDisplayChangeSupported()) {
            super.setDisplayMode(dm);
            return;
        }
        Window w = getFullScreenWindow();
        if (w == null) {
            throw new IllegalStateException("Must be in fullscreen mode " +
                                            "in order to set display mode");
        }
        if (getDisplayMode().equals(dm)) {
            return;
        }
        if (dm == null ||
            (dm = getMatchingDisplayMode(dm)) == null)
        {
            throw new IllegalArgumentException("Invalid display mode");
        }

        if (!shutdownHookRegistered) {
            // register a shutdown hook so that we return to the
            // original DisplayMode when the VM exits (if the application
            // is already in the original DisplayMode at that time, this
            // hook will have no effect)
            shutdownHookRegistered = true;
            PrivilegedAction<Void> a = new PrivilegedAction<Void>() {
                public Void run() {
                    ThreadGroup mainTG = Thread.currentThread().getThreadGroup();
                    ThreadGroup parentTG = mainTG.getParent();
                    while (parentTG != null) {
                        mainTG = parentTG;
                        parentTG = mainTG.getParent();
                    }
                    Runnable r = new Runnable() {
                            public void run() {
                                Window old = getFullScreenWindow();
                                if (old != null) {
                                    exitFullScreenExclusive(old);
                                    setDisplayMode(origDisplayMode);
                                }
                            }
                        };
                    Thread t = new Thread(mainTG, r,"Display-Change-Shutdown-Thread-"+screen);
                    t.setContextClassLoader(null);
                    Runtime.getRuntime().addShutdownHook(t);
                    return null;
                }
            };
            AccessController.doPrivileged(a);
        }

        // switch to the new DisplayMode
        configDisplayMode(screen,
                          dm.getWidth(), dm.getHeight(),
                          dm.getRefreshRate());

        // update bounds of the fullscreen window
        w.setBounds(0, 0, dm.getWidth(), dm.getHeight());

        // configDisplayMode() is synchronous, so the display change will be
        // complete by the time we get here (and it is therefore safe to call
        // displayChanged() now)
        ((X11GraphicsEnvironment)
         GraphicsEnvironment.getLocalGraphicsEnvironment()).displayChanged();
    }

    private synchronized DisplayMode getMatchingDisplayMode(DisplayMode dm) {
        if (!isDisplayChangeSupported()) {
            return null;
        }
        DisplayMode[] modes = getDisplayModes();
        for (DisplayMode mode : modes) {
            if (dm.equals(mode) ||
                (dm.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN &&
                 dm.getWidth() == mode.getWidth() &&
                 dm.getHeight() == mode.getHeight() &&
                 dm.getBitDepth() == mode.getBitDepth()))
            {
                return mode;
            }
        }
        return null;
    }

    /**
     * From the DisplayChangedListener interface; called from
     * X11GraphicsEnvironment when the display mode has been changed.
     */
    public synchronized void displayChanged() {
        // reset the list of configs (and default config)
        defaultConfig = null;
        configs = null;
        doubleBufferVisuals = null;

        // reset the native data structures associated with this device (they
        // will be reinitialized when the GraphicsConfigs are configured)
        resetNativeData(screen);

        // pass on to all top-level windows on this screen
        topLevels.notifyListeners();
    }

    /**
     * From the DisplayChangedListener interface; devices do not need
     * to react to this event.
     */
    public void paletteChanged() {
    }

    /**
     * Add a DisplayChangeListener to be notified when the display settings
     * are changed.  Typically, only top-level containers need to be added
     * to X11GraphicsDevice.
     */
    public void addDisplayChangedListener(DisplayChangedListener client) {
        topLevels.add(client);
    }

    /**
     * Remove a DisplayChangeListener from this X11GraphicsDevice.
     */
    public void removeDisplayChangedListener(DisplayChangedListener client) {
        topLevels.remove(client);
    }

    public String toString() {
        return ("X11GraphicsDevice[screen="+screen+"]");
    }
}
