/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import sun.awt.windows.WToolkit;
import sun.java2d.opengl.WGLGraphicsConfig;

public class WindowsFlags {

    /**
     * Description of command-line flags.  All flags with [true|false]
     * values (where both have possible meanings, such as with ddlock)
     * have an associated variable that indicates whether this flag
     * was set by the user.  For example, d3d is on by default, but
     * may be disabled at runtime by internal settings unless the user
     * has forced it on with d3d=true.  These associated variables have
     * the same base (eg, d3d) but end in "Set" (eg, d3dEnabled and
     * d3dSet).
     *      ddEnabled: usage: "-Dsun.java2d.noddraw[=true]"
     *               turns off all usage of ddraw, including surface -> surface
     *               Blts (including onscreen->onscreen copyarea), offscreen
     *               surface creation, and surface locking via DDraw.
     *      ddOffscreenEnabled: usage: "-Dsun.java2d.ddoffscreen=false"
     *               disables the use of ddraw surfaces for offscreen
     *               images.  Effectively disables use of ddraw for most
     *               operations except onscreen scrolling and some locking.
     *      ddVramForced: usage: "-Dsun.java2d.ddforcevram=true"
     *               Disables punts of offscreen ddraw surfaces to ddraw
     *               system memory.  We use a punting mechanism when we detect
     *               a high proportion of expensive read operations on a ddraw
     *               surface; this flag disables that mechanism and forces the
     *               surfaces to remain in VRAM regardless.
     *      ddLockEnabled:  usage: "-Dsun.java2d.ddlock=[true|false]"
     *               forces on|off usage of DirectDraw for locking the
     *               screen.  This feature is usually enabled by default
     *               for pre-Win2k OS's and disabled by default for
     *               Win2k and future OS's (as of jdk1.4.1).
     *      gdiBlitEnabled: usage: "-Dsun.java2d.gdiblit=false"
     *               turns off Blit loops that use GDI for copying to
     *               the screen from certain image types.  Copies will,
     *               instead, happen via ddraw locking or temporary GDI DIB
     *               creation/copying (depending on OS and other flags)
     *      ddBlitEnabled: usage: "-Dsun.java2d.ddblit=false"
     *               turns off Blit loops that use DDraw for copying to
     *               the screen from other ddraw surfaces.  Copies will use
     *               fallback mechanisms of GDI blits or ddraw locks, as
     *               appropriate.  This flag is primarily for debugging
     *               purposes, to force our copies through a different code
     *               path.
     *      ddScaleEnabled: usage: "-Dsun.java2d.ddscale=true"
     *               Turns on hardware-accelerated iamge scaling via ddraw.
     *               This is off by default because we cannot guarantee the
     *               quality of the scaling; hardware may choose to do
     *               filtered or unfiltered scales, resulting in inconsistent
     *               scaling between ddraw-accelerated and java2D-rendered
     *               operations.  This flag and capability should go away
     *               someday as we eventually should use Direct3D for any
     *               scaling operation (where we can control the filtering
     *               used).
     *      d3dEnabled: usage: "-Dsun.java2d.d3d=[true|false]"
     *               Forces our use of Direct3D on or off.  Direct3D is on
     *               by default, but may be disabled in some situations, such
     *               as when running on Itanium, or on a card with bad d3d line
     *               quality, or on a video card that we have had bad experience
     *               with (e.g., Trident).  This flag can force us to use d3d
     *               anyway in these situations.  Or, this flag can force us to
     *               not use d3d in a situation where we would use it otherwise.
     *      translAccelEnabled: usage: "-Dsun.java2d.translaccel=true"
     *               Turns on hardware acceleration for some translucent
     *               image copying via Direct3D.  Images that are created with
     *               GraphicsConfiguration.createCompatibleImage(w, h, trans)
     *               may be acceleratable by use a Direct3D texture and
     *               performing copying operations to other DirectX-based
     *               image destinations via a textured quad.  This capability
     *               is disabled by default pending further testing and fixing
     *               some minor bugs (such as the ability to render Direct3D
     *               to the screen which completely ignores the desktop
     *               clip list).  When this capability is turned on by default,
     *               this flag should go away.  Note: currently, enabling this
     *               flag also enables the ddVramForced flag.  This is because
     *               d3d translucency acceleration can only happen to offscreen
     *               surfaces which have not been punted through the means that
     *               ddVramForced disables.
     *      offscreenSharingEnabled: usage: "-Dsun.java2d.offscreenSharing=true"
     *               Turns on the ability to share a hardware-accelerated
     *               offscreen surface through the JAWT interface.  See
     *               src/windows/native/sun/windows/awt_DrawingSurface.* for
     *               more information.  This capability is disabled by default
     *               pending more testing and time to work out the right
     *               solution; we do not want to expose more public JAWT api
     *               without being very sure that we will be willing to support
     *               that API in the future regardless of other native
     *               rendering pipeline changes.
     *      d3dTexBpp: usage: "-Dsun.java2d.d3dtexbpp=[16|32]
     *               When translucent image acceleration is enabled (see
     *               translAccelEnabled above), this flag specifies the bit
     *               depth of the software (BufferedImage) and hardware
     *               (textures) that we should use.  The default is to use
     *               32 bit images and textures, but specifying a value of 16
     *               for this flag will force us to use a color model of 4444
     *               and 16-bit textures.  This can be useful for applications
     *               with heavy requirements on constrained VRAM resources.
     *      accelReset: usage: "-Dsun.java2d.accelReset"
     *               This flag tells us to reset any persistent information
     *               the display device acceleration characteristics so that
     *               we are forced to retest these characteristics.  This flag
     *               is primarily used for debugging purposes (to allow testing
     *               of the persistent storage mechanisms) but may also be
     *               needed by some users if, for example, a driver upgrade
     *               may change the runtime characteristics and they want the
     *               tests to be re-run.
     *      checkRegistry: usage: "-Dsun.java2d.checkRegistry"
     *               This flag tells us to output the current registry settings
     *               (after our initialization) to the console.
     *      disableRegistry: usage: "-Dsun.java2d.disableRegistry"
     *               This flag tells us to disable all registry-related
     *               activities.  It is mainly here for debugging purposes,
     *               to allow us to see whether any runtime bugs are caused
     *               by or related to registry problems.
     *      magPresent: usage: "-Djavax.accessibility.screen_magnifier_present"
     *               This flag is set either on the command line or in the
     *               properties file.  It tells Swing whether the user is
     *               currently using a screen magnifying application.  These
     *               applications tend to conflict with ddraw (which assumes
     *               it owns the entire display), so the presence of these
     *               applications implies that we should disable ddraw.
     *               So if magPresent is true, we set ddEnabled and associated
     *               variables to false and do not initialize the native
     *               hardware acceleration for these properties.
     *      opengl: usage: "-Dsun.java2d.opengl=[true|True]"
     *               Enables the use of the OpenGL-pipeline.  If the
     *               OpenGL flag is specified and WGL initialization is
     *               successful, we implicitly disable the use of DirectDraw
     *               and Direct3D, as those pipelines may interfere with the
     *               OGL pipeline.  (If "True" is specified, a message will
     *               appear on the console stating whether or not the OGL
     *               was successfully initialized.)
     * setHighDPIAware: Property usage: "-Dsun.java2d.dpiaware=[true|false]"
     *               This property flag "sun.java2d.dpiaware" is used to
     *               override the default behavior, which is:
     *               On Windows Vista, if the java process is launched from a
     *               known launcher (java, javaw, javaws, etc) - which is
     *               determined by whether a -Dsun.java.launcher property is set
     *               to "SUN_STANDARD" - the "high-DPI aware" property will be
     *               set on the native level prior to initializing the display.
     *
     */

    private static boolean ddEnabled;
    private static boolean ddSet;
    private static boolean ddOffscreenEnabled;
    private static boolean ddVramForced;
    private static boolean ddLockEnabled;
    private static boolean ddLockSet;
    private static boolean gdiBlitEnabled;
    private static boolean ddBlitEnabled;
    private static boolean ddScaleEnabled;
    private static boolean d3dEnabled;
    private static boolean d3dVerbose;
    private static boolean d3dSet;
    private static boolean oglEnabled;
    private static boolean oglVerbose;
    private static boolean translAccelEnabled;
    private static boolean offscreenSharingEnabled;
    private static boolean accelReset;
    private static boolean checkRegistry;
    private static boolean disableRegistry;
    private static boolean magPresent;
    private static boolean setHighDPIAware;
    private static int d3dTexBpp;
    private static String javaVersion;
    // TODO: other flags, including nopixfmt

    static {
        // Ensure awt is loaded already.  Also, this forces static init
        // of WToolkit and Toolkit, which we depend upon.
        WToolkit.loadLibraries();
        // First, init all Java level flags
        initJavaFlags();
        // Now, init things on the native side.  This may call up through
        // JNI to get/set the Java level flags based on native capabilities
        // and environment variables
        initNativeFlags();
    }

    private static native boolean initNativeFlags();

    // Noop: this method is just here as a convenient calling place when
    // we are initialized by Win32GraphicsEnv.  Calling this will force
    // us to run through the static block below, which is where the
    // real work occurs.
    public static void initFlags() {}

    private static boolean getBooleanProp(String p, boolean defaultVal) {
        String propString = System.getProperty(p);
        boolean returnVal = defaultVal;
        if (propString != null) {
            if (propString.equals("true") ||
                propString.equals("t") ||
                propString.equals("True") ||
                propString.equals("T") ||
                propString.equals("")) // having the prop name alone
            {                          // is equivalent to true
                returnVal = true;
            } else if (propString.equals("false") ||
                       propString.equals("f") ||
                       propString.equals("False") ||
                       propString.equals("F"))
            {
                returnVal = false;
            }
        }
        return returnVal;
    }

    private static boolean isBooleanPropTrueVerbose(String p) {
        String propString = System.getProperty(p);
        if (propString != null) {
            if (propString.equals("True") ||
                propString.equals("T"))
            {
                return true;
            }
        }
        return false;
    }

    private static int getIntProp(String p, int defaultVal) {
        String propString = System.getProperty(p);
        int returnVal = defaultVal;
        if (propString != null) {
            try {
                returnVal = Integer.parseInt(propString);
            } catch (NumberFormatException e) {}
        }
        return returnVal;
    }

    private static boolean getPropertySet(String p) {
        String propString = System.getProperty(p);
        return (propString != null) ? true : false;
    }

    private static void initJavaFlags() {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction()
        {
            public Object run() {
                magPresent = getBooleanProp(
                    "javax.accessibility.screen_magnifier_present", false);
                ddEnabled = !getBooleanProp("sun.java2d.noddraw", magPresent);
                ddSet = getPropertySet("sun.java2d.noddraw");
                ddOffscreenEnabled = getBooleanProp("sun.java2d.ddoffscreen",
                    !magPresent);
                ddVramForced = getBooleanProp("sun.java2d.ddforcevram", false);
                ddLockEnabled = getBooleanProp("sun.java2d.ddlock", false);
                ddBlitEnabled = getBooleanProp("sun.java2d.ddblit", !magPresent);
                ddScaleEnabled = getBooleanProp("sun.java2d.ddscale", false);
                d3dEnabled = getBooleanProp("sun.java2d.d3d", !magPresent);
                oglEnabled = getBooleanProp("sun.java2d.opengl", false);
                if (oglEnabled) {
                    oglVerbose = isBooleanPropTrueVerbose("sun.java2d.opengl");
                    if (WGLGraphicsConfig.isWGLAvailable()) {
                        ddEnabled = false;
                        d3dEnabled = false;
                    } else {
                        if (oglVerbose) {
                            System.out.println(
                                "Could not enable OpenGL pipeline " +
                                "(WGL not available)");
                        }
                        oglEnabled = false;
                    }
                }
                gdiBlitEnabled = getBooleanProp("sun.java2d.gdiBlit", true);
                d3dSet = getPropertySet("sun.java2d.d3d");
                if (d3dSet) {
                    d3dVerbose = isBooleanPropTrueVerbose("sun.java2d.d3d");
                }
                translAccelEnabled =
                    getBooleanProp("sun.java2d.translaccel", false);
                if (translAccelEnabled) {
                    // translucency only accelerated to un-punted buffers
                    ddVramForced = true;
                    // since they've requested translucency acceleration,
                    // we assume they'll be happy with d3d quality
                    if (!d3dSet && !magPresent) {
                        d3dEnabled = true;
                        d3dSet = true;
                    }
                }
                offscreenSharingEnabled =
                    getBooleanProp("sun.java2d.offscreenSharing", false);
                accelReset = getBooleanProp("sun.java2d.accelReset", false);
                checkRegistry =
                    getBooleanProp("sun.java2d.checkRegistry", false);
                disableRegistry =
                    getBooleanProp("sun.java2d.disableRegistry", false);
                javaVersion = System.getProperty("java.version");
                if (javaVersion == null) {
                    // Cannot be true, nonetheless...
                    javaVersion = "default";
                } else {
                    int dashIndex = javaVersion.indexOf('-');
                    if (dashIndex >= 0) {
                        // an interim release; use only the part preceding the -
                        javaVersion = javaVersion.substring(0, dashIndex);
                    }
                }
                d3dTexBpp = getIntProp("sun.java2d.d3dtexbpp", 32);
                ddLockSet = getPropertySet("sun.java2d.ddlock");

                String dpiOverride = System.getProperty("sun.java2d.dpiaware");
                if (dpiOverride != null) {
                    setHighDPIAware = dpiOverride.equalsIgnoreCase("true");
                } else {
                    String sunLauncherProperty =
                        System.getProperty("sun.java.launcher", "unknown");
                    setHighDPIAware =
                        sunLauncherProperty.equalsIgnoreCase("SUN_STANDARD");
                }
                /*
                // Output info based on some non-default flags:
                if (offscreenSharingEnabled) {
                    System.out.println(
                        "Warning: offscreenSharing has been enabled. " +
                        "The use of this capability will change in future " +
                        "releases and applications that depend on it " +
                        "may not work correctly");
                }
                if (translAccelEnabled) {
                    System.out.println(
                        "Acceleration for translucent images is enabled.");
                }
                if (!ddBlitEnabled) {
                    System.out.println("DirectDraw Blits disabled");
                }
                if (ddScaleEnabled) {
                    System.out.println("DirectDraw Scaling enabled");
                }
                */
                return null;
            }
        });
        /*
        System.out.println("WindowsFlags (Java):");
        System.out.println("  ddEnabled: " + ddEnabled + "\n" +
                           "  ddOffscreenEnabled: " + ddOffscreenEnabled + "\n" +
                           "  ddVramForced: " + ddVramForced + "\n" +
                           "  ddLockEnabled: " + ddLockEnabled + "\n" +
                           "  ddLockSet: " + ddLockSet + "\n" +
                           "  ddBlitEnabled: " + ddBlitEnabled + "\n" +
                           "  ddScaleEnabled: " + ddScaleEnabled + "\n" +
                           "  d3dEnabled: " + d3dEnabled + "\n" +
                           "  d3dSet: " + d3dSet + "\n" +
                           "  oglEnabled: " + oglEnabled + "\n" +
                           "  oglVerbose: " + oglVerbose + "\n" +
                           "  gdiBlitEnabled: " + gdiBlitEnabled + "\n" +
                           "  translAccelEnabled: " + translAccelEnabled + "\n" +
                           "  offscreenSharingEnabled: " + offscreenSharingEnabled + "\n" +
                           "  accelReset: " + accelReset + "\n" +
                           "  checkRegistry: " + checkRegistry + "\n" +
                           "  disableRegistry: " + disableRegistry + "\n" +
                           "  d3dTexBPP: " + d3dTexBpp);
        */
    }

    public static boolean isDDEnabled() {
        return ddEnabled;
    }

    public static boolean isDDOffscreenEnabled() {
        return ddOffscreenEnabled;
    }

    public static boolean isDDVramForced() {
        return ddVramForced;
    }

    public static boolean isDDLockEnabled() {
        return ddLockEnabled;
    }

    public static boolean isDDLockSet() {
        return ddLockSet;
    }

    public static boolean isDDBlitEnabled() {
        return ddBlitEnabled;
    }

    public static boolean isDDScaleEnabled() {
        return ddScaleEnabled;
    }

    public static boolean isD3DEnabled() {
        return d3dEnabled;
    }

    public static boolean isD3DSet() {
        return d3dSet;
    }

    public static boolean isD3DVerbose() {
        return d3dVerbose;
    }

    public static int getD3DTexBpp() {
        return d3dTexBpp;
    }

    public static boolean isGdiBlitEnabled() {
        return gdiBlitEnabled;
    }

    public static boolean isTranslucentAccelerationEnabled() {
        return d3dEnabled;
    }

    public static boolean isOffscreenSharingEnabled() {
        return offscreenSharingEnabled;
    }

    public static boolean isMagPresent() {
        return magPresent;
    }

    public static boolean isOGLEnabled() {
        return oglEnabled;
    }

    public static boolean isOGLVerbose() {
        return oglVerbose;
    }
}
