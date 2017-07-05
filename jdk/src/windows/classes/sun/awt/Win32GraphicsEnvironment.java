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

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.peer.ComponentPeer;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import sun.awt.DisplayChangedListener;
import sun.awt.SunDisplayChanger;
import sun.awt.windows.WFontConfiguration;
import sun.awt.windows.WPrinterJob;
import sun.awt.windows.WToolkit;
import sun.font.FontManager;
import sun.java2d.SunGraphicsEnvironment;
import sun.java2d.SurfaceManagerFactory;
import sun.java2d.WindowsSurfaceManagerFactory;
import sun.java2d.d3d.D3DGraphicsDevice;
import sun.java2d.windows.WindowsFlags;

/**
 * This is an implementation of a GraphicsEnvironment object for the
 * default local GraphicsEnvironment used by the Java Runtime Environment
 * for Windows.
 *
 * @see GraphicsDevice
 * @see GraphicsConfiguration
 */

public class Win32GraphicsEnvironment
    extends SunGraphicsEnvironment
{
    static {
        // Ensure awt is loaded already.  Also, this forces static init
        // of WToolkit and Toolkit, which we depend upon
        WToolkit.loadLibraries();
        // setup flags before initializing native layer
        WindowsFlags.initFlags();
        initDisplayWrapper();
        eudcFontFileName = getEUDCFontFile();

        // Install correct surface manager factory.
        SurfaceManagerFactory.setInstance(new WindowsSurfaceManagerFactory());
    }

    /**
     * Noop function that just acts as an entry point for someone to force
     * a static initialization of this class.
     */
    public static void init() {}

    /**
     * Initializes native components of the graphics environment.  This
     * includes everything from the native GraphicsDevice elements to
     * the DirectX rendering layer.
     */
    private static native void initDisplay();

    private static boolean displayInitialized;      // = false;
    public static void initDisplayWrapper() {
        if (!displayInitialized) {
            displayInitialized = true;
            initDisplay();
        }
    }

    public Win32GraphicsEnvironment() {
    }

    protected native int getNumScreens();
    protected native int getDefaultScreen();

    public GraphicsDevice getDefaultScreenDevice() {
        return getScreenDevices()[getDefaultScreen()];
    }

    /**
     * Returns the number of pixels per logical inch along the screen width.
     * In a system with multiple display monitors, this value is the same for
     * all monitors.
     * @returns number of pixels per logical inch in X direction
     */
    public native int getXResolution();
    /**
     * Returns the number of pixels per logical inch along the screen height.
     * In a system with multiple display monitors, this value is the same for
     * all monitors.
     * @returns number of pixels per logical inch in Y direction
     */
    public native int getYResolution();


/*
 * ----DISPLAY CHANGE SUPPORT----
 */

    // list of invalidated graphics devices (those which were removed)
    private ArrayList<WeakReference<Win32GraphicsDevice>> oldDevices;
    /*
     * From DisplayChangeListener interface.
     * Called from WToolkit and executed on the event thread when the
     * display settings are changed.
     */
    @Override
    public void displayChanged() {
        // getNumScreens() will return the correct current number of screens
        GraphicsDevice newDevices[] = new GraphicsDevice[getNumScreens()];
        GraphicsDevice oldScreens[] = screens;
        // go through the list of current devices and determine if they
        // could be reused, or will have to be replaced
        if (oldScreens != null) {
            for (int i = 0; i < oldScreens.length; i++) {
                if (!(screens[i] instanceof Win32GraphicsDevice)) {
                    // REMIND: can we ever have anything other than Win32GD?
                    assert (false) : oldScreens[i];
                    continue;
                }
                Win32GraphicsDevice gd = (Win32GraphicsDevice)oldScreens[i];
                // devices may be invalidated from the native code when the
                // display change happens (device add/removal also causes a
                // display change)
                if (!gd.isValid()) {
                    if (oldDevices == null) {
                        oldDevices =
                            new ArrayList<WeakReference<Win32GraphicsDevice>>();
                    }
                    oldDevices.add(new WeakReference<Win32GraphicsDevice>(gd));
                } else if (i < newDevices.length) {
                    // reuse the device
                    newDevices[i] = gd;
                }
            }
            oldScreens = null;
        }
        // create the new devices (those that weren't reused)
        for (int i = 0; i < newDevices.length; i++) {
            if (newDevices[i] == null) {
                newDevices[i] = makeScreenDevice(i);
            }
        }
        // install the new array of devices
        // Note: no synchronization here, it doesn't matter if a thread gets
        // a new or an old array this time around
        screens = newDevices;
        for (GraphicsDevice gd : screens) {
            if (gd instanceof DisplayChangedListener) {
                ((DisplayChangedListener)gd).displayChanged();
            }
        }
        // re-invalidate all old devices. It's needed because those in the list
        // may become "invalid" again - if the current default device is removed,
        // for example. Also, they need to be notified about display
        // changes as well.
        if (oldDevices != null) {
            int defScreen = getDefaultScreen();
            for (ListIterator<WeakReference<Win32GraphicsDevice>> it =
                    oldDevices.listIterator(); it.hasNext();)
            {
                Win32GraphicsDevice gd = it.next().get();
                if (gd != null) {
                    gd.invalidate(defScreen);
                    gd.displayChanged();
                } else {
                    // no more references to this device, remove it
                    it.remove();
                }
            }
        }
        // Reset the static GC for the (possibly new) default screen
        WToolkit.resetGC();

        // notify SunDisplayChanger list (e.g. VolatileSurfaceManagers and
        // CachingSurfaceManagers) about the display change event
        displayChanger.notifyListeners();
        // note: do not call super.displayChanged, we've already done everything
    }


/*
 * ----END DISPLAY CHANGE SUPPORT----
 */

    /* Used on Windows to obtain from the windows registry the name
     * of a file containing the system EUFC font. If running in one of
     * the locales for which this applies, and one is defined, the font
     * defined by this file is appended to all composite fonts as a
     * fallback component.
     */
    private static native String getEUDCFontFile();

    /**
     * Whether registerFontFile expects absolute or relative
     * font file names.
     */
    protected boolean useAbsoluteFontFileNames() {
        return false;
    }

    /* Unlike the shared code version, this expects a base file name -
     * not a full path name.
     * The font configuration file has base file names and the FontConfiguration
     * class reports these back to the GraphicsEnvironment, so these
     * are the componentFileNames of CompositeFonts.
     */
    protected void registerFontFile(String fontFileName, String[] nativeNames,
                                    int fontRank, boolean defer) {

        // REMIND: case compare depends on platform
        if (registeredFontFiles.contains(fontFileName)) {
            return;
        }
        registeredFontFiles.add(fontFileName);

        int fontFormat;
        if (ttFilter.accept(null, fontFileName)) {
            fontFormat = FontManager.FONTFORMAT_TRUETYPE;
        } else if (t1Filter.accept(null, fontFileName)) {
            fontFormat = FontManager.FONTFORMAT_TYPE1;
        } else {
            /* on windows we don't use/register native fonts */
            return;
        }

        if (fontPath == null) {
            fontPath = getPlatformFontPath(noType1Font);
        }

        /* Look in the JRE font directory first.
         * This is playing it safe as we would want to find fonts in the
         * JRE font directory ahead of those in the system directory
         */
        String tmpFontPath = jreFontDirName+File.pathSeparator+fontPath;
        StringTokenizer parser = new StringTokenizer(tmpFontPath,
                                                     File.pathSeparator);

        boolean found = false;
        try {
            while (!found && parser.hasMoreTokens()) {
                String newPath = parser.nextToken();
                boolean ujr = newPath.equals(jreFontDirName);
                File theFile = new File(newPath, fontFileName);
                if (theFile.canRead()) {
                    found = true;
                    String path = theFile.getAbsolutePath();
                    if (defer) {
                        FontManager.registerDeferredFont(fontFileName, path,
                                                         nativeNames,
                                                         fontFormat, ujr,
                                                         fontRank);
                    } else {
                        FontManager.registerFontFile(path, nativeNames,
                                                     fontFormat, ujr,
                                                     fontRank);
                    }
                    break;
                }
            }
        } catch (NoSuchElementException e) {
            System.err.println(e);
        }
        if (!found) {
            addToMissingFontFileList(fontFileName);
        }
    }

    /* register only TrueType/OpenType fonts
     * Because these need to be registed just for use when printing,
     * we defer the actual registration and the static initialiser
     * for the printing class makes the call to registerJREFontsForPrinting()
     */
    static String fontsForPrinting = null;
    protected void registerJREFontsWithPlatform(String pathName) {
        fontsForPrinting = pathName;
    }

    public static void registerJREFontsForPrinting() {
        final String pathName;
        synchronized (Win32GraphicsEnvironment.class) {
            GraphicsEnvironment.getLocalGraphicsEnvironment();
            if (fontsForPrinting == null) {
                return;
            }
            pathName = fontsForPrinting;
            fontsForPrinting = null;
        }
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction() {
                public Object run() {
                    File f1 = new File(pathName);
                    String[] ls = f1.list(new TTFilter());
                    if (ls == null) {
                        return null;
                    }
                    for (int i=0; i <ls.length; i++ ) {
                        File fontFile = new File(f1, ls[i]);
                        registerFontWithPlatform(fontFile.getAbsolutePath());
                    }
                    return null;
                }
         });
    }

    protected static native void registerFontWithPlatform(String fontName);

    protected static native void deRegisterFontWithPlatform(String fontName);

    protected GraphicsDevice makeScreenDevice(int screennum) {
        GraphicsDevice device = null;
        if (WindowsFlags.isD3DEnabled()) {
            device = D3DGraphicsDevice.createDevice(screennum);
        }
        if (device == null) {
            device = new Win32GraphicsDevice(screennum);
        }
        return device;
    }

    // Implements SunGraphicsEnvironment.createFontConfiguration.
    protected FontConfiguration createFontConfiguration() {
       FontConfiguration fc = new WFontConfiguration(this);
       fc.init();
       return fc;
    }

    public FontConfiguration createFontConfiguration(boolean preferLocaleFonts,
                                                     boolean preferPropFonts) {

        return new WFontConfiguration(this, preferLocaleFonts,preferPropFonts);
    }

    @Override
    public boolean isFlipStrategyPreferred(ComponentPeer peer) {
        GraphicsConfiguration gc;
        if (peer != null && (gc = peer.getGraphicsConfiguration()) != null) {
            GraphicsDevice gd = gc.getDevice();
            if (gd instanceof D3DGraphicsDevice) {
                return ((D3DGraphicsDevice)gd).isD3DEnabledOnDevice();
            }
        }
        return false;
    }

    private static volatile boolean isDWMCompositionEnabled;
    /**
     * Returns true if dwm composition is currently enabled, false otherwise.
     *
     * @return true if dwm composition is enabled, false otherwise
     */
    public static boolean isDWMCompositionEnabled() {
        return isDWMCompositionEnabled;
    }

    /**
     * Called from the native code when DWM composition state changed.
     * May be called multiple times during the lifetime of the application.
     * REMIND: we may want to create a listener mechanism for this.
     *
     * Note: called on the Toolkit thread, no user code or locks are allowed.
     *
     * @param enabled indicates the state of dwm composition
     */
    private static void dwmCompositionChanged(boolean enabled) {
        isDWMCompositionEnabled = enabled;
    }

    @Override
    public boolean isDisplayLocal() {
        return true;
    }

    /**
     * Used to find out if the OS is Windows Vista or later.
     *
     * @return {@code true} if the OS is Vista or later, {@code false} otherwise
     */
    public static native boolean isVistaOS();
}
