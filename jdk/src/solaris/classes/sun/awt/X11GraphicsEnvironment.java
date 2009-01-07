/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.util.*;
import java.util.logging.*;

import sun.awt.motif.MFontConfiguration;
import sun.font.FcFontConfiguration;
import sun.font.Font2D;
import sun.font.FontManager;
import sun.font.NativeFont;
import sun.java2d.SunGraphicsEnvironment;
import sun.java2d.SurfaceManagerFactory;
import sun.java2d.UnixSurfaceManagerFactory;

/**
 * This is an implementation of a GraphicsEnvironment object for the
 * default local GraphicsEnvironment used by the Java Runtime Environment
 * for X11 environments.
 *
 * @see GraphicsDevice
 * @see GraphicsConfiguration
 */
public class X11GraphicsEnvironment
    extends SunGraphicsEnvironment
{
    private static final Logger log = Logger.getLogger("sun.awt.X11GraphicsEnvironment");
    private static final Logger screenLog = Logger.getLogger("sun.awt.screen.X11GraphicsEnvironment");

    private static Boolean xinerState;

    /*
     * This is the set of font directories needed to be on the X font path
     * to enable AWT heavyweights to find all of the font configuration fonts.
     * It is populated by :
     * - awtfontpath entries in the fontconfig.properties
     * - parent directories of "core" fonts used in the fontconfig.properties
     * - looking up font dirs in the xFontDirsMap where the key is a fontID
     *   (cut down version of the XLFD read from the font configuration file).
     * This set is nulled out after use to free heap space.
     */
    private static HashSet<String> fontConfigDirs = null;

   /*
    * fontNameMap is a map from a fontID (which is a substring of an XLFD like
    * "-monotype-arial-bold-r-normal-iso8859-7")
    * to font file path like
    * /usr/openwin/lib/locale/iso_8859_7/X11/fonts/TrueType/ArialBoldItalic.ttf
    * It's used in a couple of methods like
    * getFileNameFomPlatformName(..) to help locate the font file.
    * We use this substring of a full XLFD because the font configuration files
    * define the XLFDs in a way that's easier to make into a request.
    * E.g., the -0-0-0-0-p-0- reported by X is -*-%d-*-*-p-*- in the font
    * configuration files. We need to remove that part for comparisons.
    */
    private static Map fontNameMap = new HashMap();

    /* xFontDirsMap is also a map from a font ID to a font filepath.
     * The difference from fontNameMap is just that it does not have
     * resolved symbolic links. Normally this is not interesting except
     * that we need to know the directory in which a font was found to
     * add it to the X font server path, since although the files may
     * be linked, the fonts.dir is different and specific to the encoding
     * handled by that directory. This map is nulled out after use to free
     * heap space. If the optimal path is taken, such that all fonts in
     * font configuration files are referenced by filename, then the font
     * dir can be directly derived as its parent directory.
     * If a font is used by two XLFDs, each corresponding to a different
     * X11 font directory, then precautions must be taken to include both
     * directories.
     */
     private static Map xFontDirsMap;

    /*
     * xlfdMap is a map from a platform path like
     * /usr/openwin/lib/locale/ja/X11/fonts/TT/HG-GothicB.ttf to an XLFD like
     * "-ricoh-hg gothic b-medium-r-normal--0-0-0-0-m-0-jisx0201.1976-0"
     * Because there may be multiple native names, because the font is used
     * to support multiple X encodings for example, the value of an entry in
     * this map is always a vector where we store all the native names.
     * For fonts which we don't understand the key isn't a pathname, its
     * the full XLFD string like :-
     * "-ricoh-hg gothic b-medium-r-normal--0-0-0-0-m-0-jisx0201.1976-0"
     */
     private static Map xlfdMap = new HashMap();

    /*
     * Used to eliminate redundant work. When a font directory is
     * registered it added to this list. Subsequent registrations for the
     * same directory can then be skipped by checking this Map.
     * Access to this map is not synchronised here since creation
     * of the singleton GE instance is already synchronised and that is
     * the only code path that accesses this map.
     */
     private static HashMap registeredDirs = new HashMap();

    /* Array of directories to be added to the X11 font path.
     * Used by static method called from Toolkits which use X11 fonts.
     * Specifically this means MToolkit
     */
    private static String[] fontdirs = null;

    static {
        java.security.AccessController.doPrivileged(
                          new java.security.PrivilegedAction() {
            public Object run() {
                System.loadLibrary("awt");

                /*
                 * Note: The MToolkit object depends on the static initializer
                 * of X11GraphicsEnvironment to initialize the connection to
                 * the X11 server.
                 */
                if (!isHeadless()) {
                    // first check the OGL system property
                    boolean glxRequested = false;
                    String prop = System.getProperty("sun.java2d.opengl");
                    if (prop != null) {
                        if (prop.equals("true") || prop.equals("t")) {
                            glxRequested = true;
                        } else if (prop.equals("True") || prop.equals("T")) {
                            glxRequested = true;
                            glxVerbose = true;
                        }
                    }

                    // initialize the X11 display connection
                    initDisplay(glxRequested);

                    // only attempt to initialize GLX if it was requested
                    if (glxRequested) {
                        glxAvailable = initGLX();
                        if (glxVerbose && !glxAvailable) {
                            System.out.println(
                                "Could not enable OpenGL " +
                                "pipeline (GLX 1.3 not available)");
                        }
                    }
                }

                return null;
            }
         });

        // Install the correct surface manager factory.
        SurfaceManagerFactory.setInstance(new UnixSurfaceManagerFactory());

    }

    private static boolean glxAvailable;
    private static boolean glxVerbose;

    private static native boolean initGLX();

    public static boolean isGLXAvailable() {
        return glxAvailable;
    }

    public static boolean isGLXVerbose() {
        return glxVerbose;
    }

    /**
     * Checks if Shared Memory extension can be used.
     * Returns:
     *   -1 if server doesn't support MITShm
     *    1 if server supports it and it can be used
     *    0 otherwise
     */
    private static native int checkShmExt();

    private static  native String getDisplayString();
    private Boolean isDisplayLocal;

    /**
     * This should only be called from the static initializer, so no need for
     * the synchronized keyword.
     */
    private static native void initDisplay(boolean glxRequested);

    public X11GraphicsEnvironment() {
    }

    protected native int getNumScreens();

    protected GraphicsDevice makeScreenDevice(int screennum) {
        return new X11GraphicsDevice(screennum);
    }

    protected native int getDefaultScreenNum();
    /**
     * Returns the default screen graphics device.
     */
    public GraphicsDevice getDefaultScreenDevice() {
        return getScreenDevices()[getDefaultScreenNum()];
    }

    @Override
    public boolean isDisplayLocal() {
        if (isDisplayLocal == null) {
            SunToolkit.awtLock();
            try {
                if (isDisplayLocal == null) {
                    isDisplayLocal = Boolean.valueOf(_isDisplayLocal());
                }
            } finally {
                SunToolkit.awtUnlock();
            }
        }
        return isDisplayLocal.booleanValue();
    }

    private static boolean _isDisplayLocal() {
        if (isHeadless()) {
            return true;
        }

        String isRemote = (String)java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.java2d.remote"));
        if (isRemote != null) {
            return isRemote.equals("false");
        }

        int shm = checkShmExt();
        if (shm != -1) {
            return (shm == 1);
        }

        // If XServer doesn't support ShMem extension,
        // try the other way

        String display = getDisplayString();
        int ind = display.indexOf(':');
        final String hostName = display.substring(0, ind);
        if (ind <= 0) {
            // ':0' case
            return true;
        }

        Boolean result = (Boolean)java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction() {
            public Object run() {
                InetAddress remAddr[] = null;
                Enumeration locals = null;
                Enumeration interfaces = null;
                try {
                    interfaces = NetworkInterface.getNetworkInterfaces();
                    remAddr = InetAddress.getAllByName(hostName);
                    if (remAddr == null) {
                        return Boolean.FALSE;
                    }
                } catch (UnknownHostException e) {
                    System.err.println("Unknown host: " + hostName);
                    return Boolean.FALSE;
                } catch (SocketException e1) {
                    System.err.println(e1.getMessage());
                    return Boolean.FALSE;
                }

                for (; interfaces.hasMoreElements();) {
                    locals = ((NetworkInterface)interfaces.nextElement()).getInetAddresses();
                    for (; locals.hasMoreElements();) {
                        for (int i = 0; i < remAddr.length; i++) {
                            if (locals.nextElement().equals(remAddr[i])) {
                                return Boolean.TRUE;
                            }
                        }
                    }
                }
                return Boolean.FALSE;
            }});
        return result.booleanValue();
    }

    /* These maps are used on Linux where we reference the Lucida oblique
     * fonts in fontconfig files even though they aren't in the standard
     * font directory. This explicitly remaps the XLFDs for these to the
     * correct base font. This is needed to prevent composite fonts from
     * defaulting to the Lucida Sans which is a bad substitute for the
     * monospaced Lucida Sans Typewriter. Also these maps prevent the
     * JRE from doing wasted work at start up.
     */
    HashMap<String, String> oblmap = null;

    private String getObliqueLucidaFontID(String fontID) {
        if (fontID.startsWith("-lucidasans-medium-i-normal") ||
            fontID.startsWith("-lucidasans-bold-i-normal") ||
            fontID.startsWith("-lucidatypewriter-medium-i-normal") ||
            fontID.startsWith("-lucidatypewriter-bold-i-normal")) {
            return fontID.substring(0, fontID.indexOf("-i-"));
        } else {
            return null;
        }
    }

   private void initObliqueLucidaFontMap() {
       oblmap = new HashMap<String, String>();
       oblmap.put("-lucidasans-medium",
                  jreLibDirName+"/fonts/LucidaSansRegular.ttf");
       oblmap.put("-lucidasans-bold",
                  jreLibDirName+"/fonts/LucidaSansDemiBold.ttf");
       oblmap.put("-lucidatypewriter-medium",
                  jreLibDirName+"/fonts/LucidaTypewriterRegular.ttf");
       oblmap.put("-lucidatypewriter-bold",
                  jreLibDirName+"/fonts/LucidaTypewriterBold.ttf");
   }

    /**
     * Takes family name property in the following format:
     * "-linotype-helvetica-medium-r-normal-sans-*-%d-*-*-p-*-iso8859-1"
     * and returns the name of the corresponding physical font.
     * This code is used to resolve font configuration fonts, and expects
     * only to get called for these fonts.
     */
    public String getFileNameFromPlatformName(String platName) {

        /* If the FontConfig file doesn't use xlfds, or its
         * FcFontConfiguration, this may be already a file name.
         */
        if (platName.startsWith("/")) {
            return platName;
        }

        String fileName = null;
        String fontID = specificFontIDForName(platName);

        /* If the font filename has been explicitly assigned in the
         * font configuration file, use it. This avoids accessing
         * the wrong fonts on Linux, where different fonts (some
         * of which may not be usable by 2D) may share the same
         * specific font ID. It may also speed up the lookup.
         */
        fileName = super.getFileNameFromPlatformName(platName);
        if (fileName != null) {
            if (isHeadless() && fileName.startsWith("-")) {
                /* if it's headless, no xlfd should be used */
                    return null;
            }
            if (fileName.startsWith("/")) {
                /* If a path is assigned in the font configuration file,
                 * it is required that the config file also specify using the
                 * new awtfontpath key the X11 font directories
                 * which must be added to the X11 font path to support
                 * AWT access to that font. For that reason we no longer
                 * have code here to add the parent directory to the list
                 * of font config dirs, since the parent directory may not
                 * be sufficient if fonts are symbolically linked to a
                 * different directory.
                 *
                 * Add this XLFD (platform name) to the list of known
                 * ones for this file.
                 */
                Vector xVal = (Vector) xlfdMap.get(fileName);
                if (xVal == null) {
                    /* Try to be robust on Linux distros which move fonts
                     * around by verifying that the fileName represents a
                     * file that exists.  If it doesn't, set it to null
                     * to trigger a search.
                     */
                    if (getFontConfiguration().needToSearchForFile(fileName)) {
                        fileName = null;
                    }
                    if (fileName != null) {
                        xVal = new Vector();
                        xVal.add(platName);
                        xlfdMap.put(fileName, xVal);
                    }
                } else {
                    if (!xVal.contains(platName)) {
                        xVal.add(platName);
                    }
                }
            }
            if (fileName != null) {
                fontNameMap.put(fontID, fileName);
                return fileName;
            }
        }

        if (fontID != null) {
            fileName = (String)fontNameMap.get(fontID);
            /* On Linux check for the Lucida Oblique fonts */
            if (fileName == null && isLinux && !isOpenJDK()) {
                if (oblmap == null) {
                    initObliqueLucidaFontMap();
                }
                String oblkey = getObliqueLucidaFontID(fontID);
                if (oblkey != null) {
                    fileName = oblmap.get(oblkey);
                }
            }
            if (fontPath == null &&
                (fileName == null || !fileName.startsWith("/"))) {
                if (debugFonts) {
                    logger.warning("** Registering all font paths because " +
                                   "can't find file for " + platName);
                }
                fontPath = getPlatformFontPath(noType1Font);
                registerFontDirs(fontPath);
                if (debugFonts) {
                    logger.warning("** Finished registering all font paths");
                }
                fileName = (String)fontNameMap.get(fontID);
            }
            if (fileName == null && !isHeadless()) {
                /* Query X11 directly to see if this font is available
                 * as a native font.
                 */
                fileName = getX11FontName(platName);
            }
            if (fileName == null) {
                fontID = switchFontIDForName(platName);
                fileName = (String)fontNameMap.get(fontID);
            }
            if (fileName != null) {
                fontNameMap.put(fontID, fileName);
            }
        }
        return fileName;
    }

    private static String getX11FontName(String platName) {
        String xlfd = platName.replaceAll("%d", "*");
        if (NativeFont.fontExists(xlfd)) {
            return xlfd;
        } else {
            return null;
        }
    }

    /**
     * Returns the face name for the given XLFD.
     */
    public String getFileNameFromXLFD(String name) {
        String fileName = null;
        String fontID = specificFontIDForName(name);
        if (fontID != null) {
            fileName = (String)fontNameMap.get(fontID);
            if (fileName == null) {
                fontID = switchFontIDForName(name);
                fileName = (String)fontNameMap.get(fontID);
            }
            if (fileName == null) {
                fileName = getDefaultFontFile();
            }
        }
        return fileName;
    }

    // constants identifying XLFD and font ID fields
    private static final int FOUNDRY_FIELD = 1;
    private static final int FAMILY_NAME_FIELD = 2;
    private static final int WEIGHT_NAME_FIELD = 3;
    private static final int SLANT_FIELD = 4;
    private static final int SETWIDTH_NAME_FIELD = 5;
    private static final int ADD_STYLE_NAME_FIELD = 6;
    private static final int PIXEL_SIZE_FIELD = 7;
    private static final int POINT_SIZE_FIELD = 8;
    private static final int RESOLUTION_X_FIELD = 9;
    private static final int RESOLUTION_Y_FIELD = 10;
    private static final int SPACING_FIELD = 11;
    private static final int AVERAGE_WIDTH_FIELD = 12;
    private static final int CHARSET_REGISTRY_FIELD = 13;
    private static final int CHARSET_ENCODING_FIELD = 14;

    private String switchFontIDForName(String name) {

        int[] hPos = new int[14];
        int hyphenCnt = 1;
        int pos = 1;

        while (pos != -1 && hyphenCnt < 14) {
            pos = name.indexOf('-', pos);
            if (pos != -1) {
                hPos[hyphenCnt++] = pos;
                    pos++;
            }
        }

        if (hyphenCnt != 14) {
            if (debugFonts) {
                logger.severe("Font Configuration Font ID is malformed:" + name);
            }
            return name; // what else can we do?
        }

        String slant = name.substring(hPos[SLANT_FIELD-1]+1,
                                           hPos[SLANT_FIELD]);
        String family = name.substring(hPos[FAMILY_NAME_FIELD-1]+1,
                                           hPos[FAMILY_NAME_FIELD]);
        String registry = name.substring(hPos[CHARSET_REGISTRY_FIELD-1]+1,
                                           hPos[CHARSET_REGISTRY_FIELD]);
        String encoding = name.substring(hPos[CHARSET_ENCODING_FIELD-1]+1);

        if (slant.equals("i")) {
            slant = "o";
        } else if (slant.equals("o")) {
            slant = "i";
        }
        // workaround for #4471000
        if (family.equals("itc zapfdingbats")
            && registry.equals("sun")
            && encoding.equals("fontspecific")){
            registry = "adobe";
        }
        StringBuffer sb =
            new StringBuffer(name.substring(hPos[FAMILY_NAME_FIELD-1],
                                            hPos[SLANT_FIELD-1]+1));
        sb.append(slant);
        sb.append(name.substring(hPos[SLANT_FIELD],
                                 hPos[SETWIDTH_NAME_FIELD]+1));
        sb.append(registry);
        sb.append(name.substring(hPos[CHARSET_ENCODING_FIELD-1]));
        String retval = sb.toString().toLowerCase (Locale.ENGLISH);
        return retval;
    }


    private String specificFontIDForName(String name) {

        int[] hPos = new int[14];
        int hyphenCnt = 1;
        int pos = 1;

        while (pos != -1 && hyphenCnt < 14) {
            pos = name.indexOf('-', pos);
            if (pos != -1) {
                hPos[hyphenCnt++] = pos;
                    pos++;
            }
        }

        if (hyphenCnt != 14) {
            if (debugFonts) {
                logger.severe("Font Configuration Font ID is malformed:" + name);
            }
            return name; // what else can we do?
        }

        StringBuffer sb =
            new StringBuffer(name.substring(hPos[FAMILY_NAME_FIELD-1],
                                            hPos[SETWIDTH_NAME_FIELD]));
        sb.append(name.substring(hPos[CHARSET_REGISTRY_FIELD-1]));
        String retval = sb.toString().toLowerCase (Locale.ENGLISH);
        return retval;
    }

    protected String[] getNativeNames(String fontFileName,
                                      String platformName) {
        Vector nativeNames;
        if ((nativeNames=(Vector)xlfdMap.get(fontFileName))==null) {
            if (platformName == null) {
                return null;
            } else {
                /* back-stop so that at least the name used in the
                 * font configuration file is known as a native name
                 */
                String []natNames = new String[1];
                natNames[0] = platformName;
                return natNames;
            }
        } else {
            int len = nativeNames.size();
            return (String[])nativeNames.toArray(new String[len]);
        }
    }


    // An X font spec (xlfd) includes an encoding. The same TrueType font file
    // may be referenced from different X font directories in font.dir files
    // to support use in multiple encodings by X apps.
    // So for the purposes of font configuration logical fonts where AWT
    // heavyweights need to access the font via X APIs we need to ensure that
    // the directory for precisely the encodings needed by this are added to
    // the x font path. This requires that we note the platform names
    // specified in font configuration files and use that to identify the
    // X font directory that contains a font.dir file for that platform name
    // and add it to the X font path (if display is local)
    // Here we make use of an already built map of xlfds to font locations
    // to add the font location to the set of those required to build the
    // x font path needed by AWT.
    // These are added to the x font path later.
    // All this is necessary because on Solaris the font.dir directories
    // may contain not real font files, but symbolic links to the actual
    // location but that location is not suitable for the x font path, since
    // it probably doesn't have a font.dir at all and certainly not one
    // with the required encodings
    // If the fontconfiguration file is properly set up so that all fonts
    // are mapped to files then we will never trigger initialising
    // xFontDirsMap (it will be null). In this case the awtfontpath entries
    // must specify all the X11 directories needed by AWT.
    protected void addFontToPlatformFontPath(String platformName) {
        if (xFontDirsMap != null) {
            String fontID = specificFontIDForName(platformName);
            String dirName = (String)xFontDirsMap.get(fontID);
            if (dirName != null) {
                fontConfigDirs.add(dirName);
            }
        }
        return;
    }

    protected void getPlatformFontPathFromFontConfig() {
        if (fontConfigDirs == null) {
            fontConfigDirs = getFontConfiguration().getAWTFontPathSet();
            if (debugFonts && fontConfigDirs != null) {
                String[] names = fontConfigDirs.toArray(new String[0]);
                for (int i=0;i<names.length;i++) {
                    logger.info("awtfontpath : " + names[i]);
                }
            }
        }
    }

    protected void registerPlatformFontsUsedByFontConfiguration() {
        if (fontConfigDirs == null) {
            return;
        }
        if (isLinux) {
            fontConfigDirs.add(jreLibDirName+File.separator+"oblique-fonts");
        }
        fontdirs = (String[])fontConfigDirs.toArray(new String[0]);
    }

    /* Called by MToolkit to set the X11 font path */
    public static void setNativeFontPath() {
        if (fontdirs == null) {
            return;
        }

        // need to register these individually rather than by one call
        // to ensure that one bad directory doesn't cause all to be rejected
        for (int i=0; i<fontdirs.length; i++) {
            if (debugFonts) {
                logger.info("Add " + fontdirs[i] + " to X11 fontpath");
            }
            FontManager.setNativeFontPath(fontdirs[i]);
        }
    }

    /* Register just the paths, (it doesn't register the fonts).
     * If a font configuration file has specified a baseFontPath
     * fontPath is just those directories, unless on usage we
     * find it doesn't contain what we need for the logical fonts.
     * Otherwise, we register all the paths on Solaris, because
     * the fontPath we have here is the complete one from
     * parsing /var/sadm/install/contents, not just
     * what's on the X font path (may be this should be
     * changed).
     * But for now what it means is that if we didn't do
     * this then if the font weren't listed anywhere on the
     * less complete font path we'd trigger loadFonts which
     * actually registers the fonts. This may actually be
     * the right thing tho' since that would also set up
     * the X font path without which we wouldn't be able to
     * display some "native" fonts.
     * So something to revisit is that probably fontPath
     * here ought to be only the X font path + jre font dir.
     * loadFonts should have a separate native call to
     * get the rest of the platform font path.
     *
     * Registering the directories can now be avoided in the
     * font configuration initialisation when filename entries
     * exist in the font configuration file for all fonts.
     * (Perhaps a little confusingly a filename entry is
     * actually keyed using the XLFD used in the font entries,
     * and it maps *to* a real filename).
     * In the event any are missing, registration of all
     * directories will be invoked to find the real files.
     *
     * But registering the directory performed other
     * functions such as filling in the map of all native names
     * for the font. So when this method isn't invoked, they still
     * must be found. This is mitigated by getNativeNames now
     * being able to return at least the platform name, but mostly
     * by ensuring that when a filename key is found, that
     * xlfd key is stored as one of the set of platform names
     * for the font. Its a set because typical font configuration
     * files reference the same CJK font files using multiple
     * X11 encodings. For the code that adds this to the map
     * see X11GE.getFileNameFromPlatformName(..)
     * If you don't get all of these then some code points may
     * not use the Xserver, and will not get the PCF bitmaps
     * that are available for some point sizes.
     * So, in the event that there is such a problem,
     * unconditionally making this call may be necessary, at
     * some cost to JRE start-up
     */
    protected void registerFontDirs(String pathName) {

        StringTokenizer parser = new StringTokenizer(pathName,
                                                     File.pathSeparator);
        try {
            while (parser.hasMoreTokens()) {
                String dirPath = parser.nextToken();
                if (dirPath != null && !registeredDirs.containsKey(dirPath)) {
                    registeredDirs.put(dirPath, null);
                    registerFontDir(dirPath);
                }
            }
        } catch (NoSuchElementException e) {
        }
    }

    /* NOTE: this method needs to be executed in a privileged context.
     * The superclass constructor which is the primary caller of
     * this method executes entirely in such a context. Additionally
     * the loadFonts() method does too. So all should be well.

     */
    protected void registerFontDir(String path) {
        /* fonts.dir file format looks like :-
         * 47
         * Arial.ttf -monotype-arial-regular-r-normal--0-0-0-0-p-0-iso8859-1
         * Arial-Bold.ttf -monotype-arial-bold-r-normal--0-0-0-0-p-0-iso8859-1
         * ...
         */
        if (debugFonts) {
            logger.info("ParseFontDir " + path);
        }
        File fontsDotDir = new File(path + File.separator + "fonts.dir");
        FileReader fr = null;
        try {
            if (fontsDotDir.canRead()) {
                fr = new FileReader(fontsDotDir);
                BufferedReader br = new BufferedReader(fr, 8192);
                StreamTokenizer st = new StreamTokenizer(br);
                st.eolIsSignificant(true);
                int ttype = st.nextToken();
                if (ttype == StreamTokenizer.TT_NUMBER) {
                    int numEntries = (int)st.nval;
                    ttype = st.nextToken();
                    if (ttype == StreamTokenizer.TT_EOL) {
                        st.resetSyntax();
                        st.wordChars(32, 127);
                        st.wordChars(128 + 32, 255);
                        st.whitespaceChars(0, 31);

                        for (int i=0; i < numEntries; i++) {
                            ttype = st.nextToken();
                            if (ttype == StreamTokenizer.TT_EOF) {
                                break;
                            }
                            if (ttype != StreamTokenizer.TT_WORD) {
                                break;
                            }
                            int breakPos = st.sval.indexOf(' ');
                            if (breakPos <= 0) {
                                /* On TurboLinux 8.0 a fonts.dir file had
                                 * a line with integer value "24" which
                                 * appeared to be the number of remaining
                                 * entries in the file. This didn't add to
                                 * the value on the first line of the file.
                                 * Seemed like XFree86 didn't like this line
                                 * much either. It failed to parse the file.
                                 * Ignore lines like this completely, and
                                 * don't let them count as an entry.
                                 */
                                numEntries++;
                                ttype = st.nextToken();
                                if (ttype != StreamTokenizer.TT_EOL) {
                                    break;
                                }

                                continue;
                            }
                            if (st.sval.charAt(0) == '!') {
                                /* TurboLinux 8.0 comment line: ignore.
                                 * can't use st.commentChar('!') to just
                                 * skip because this line mustn't count
                                 * against numEntries.
                                 */
                                numEntries++;
                                ttype = st.nextToken();
                                if (ttype != StreamTokenizer.TT_EOL) {
                                    break;
                                }
                                continue;
                            }
                            String fileName = st.sval.substring(0, breakPos);
                            /* TurboLinux 8.0 uses some additional syntax to
                             * indicate algorithmic styling values.
                             * Ignore ':' separated files at the beginning
                             * of the fileName
                             */
                            int lastColon = fileName.lastIndexOf(':');
                            if (lastColon > 0) {
                                if (lastColon+1 >= fileName.length()) {
                                    continue;
                                }
                                fileName = fileName.substring(lastColon+1);
                            }
                            String fontPart = st.sval.substring(breakPos+1);
                            String fontID = specificFontIDForName(fontPart);
                            String sVal = (String) fontNameMap.get(fontID);

                            if (debugFonts) {
                                logger.info("file=" + fileName +
                                            " xlfd=" + fontPart);
                                logger.info("fontID=" + fontID +
                                            " sVal=" + sVal);
                            }
                            String fullPath = null;
                            try {
                                File file = new File(path,fileName);
                                /* we may have a resolved symbolic link
                                 * this becomes important for an xlfd we
                                 * still need to know the location it was
                                 * found to update the X server font path
                                 * for use by AWT heavyweights - and when 2D
                                 * wants to use the native rasteriser.
                                 */
                                if (xFontDirsMap == null) {
                                    xFontDirsMap = new HashMap();
                                }
                                xFontDirsMap.put(fontID, path);
                                fullPath = file.getCanonicalPath();
                            } catch (IOException e) {
                                fullPath = path + File.separator + fileName;
                            }
                            Vector xVal = (Vector) xlfdMap.get(fullPath);
                            if (debugFonts) {
                                logger.info("fullPath=" + fullPath +
                                            " xVal=" + xVal);
                            }
                            if ((xVal == null || !xVal.contains(fontPart)) &&
                                (sVal == null) || !sVal.startsWith("/")) {
                                if (debugFonts) {
                                    logger.info("Map fontID:"+fontID +
                                                "to file:" + fullPath);
                                }
                                fontNameMap.put(fontID, fullPath);
                                if (xVal == null) {
                                    xVal = new Vector();
                                    xlfdMap.put (fullPath, xVal);
                                }
                                xVal.add(fontPart);
                            }

                            ttype = st.nextToken();
                            if (ttype != StreamTokenizer.TT_EOL) {
                                break;
                            }
                        }
                    }
                }
                fr.close();
            }
        } catch (IOException ioe1) {
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                }  catch (IOException ioe2) {
                }
            }
        }
    }

    @Override
    public void loadFonts() {
        super.loadFonts();
        /* These maps are greatly expanded during a loadFonts but
         * can be reset to their initial state afterwards.
         * Since preferLocaleFonts() and preferProportionalFonts() will
         * trigger a partial repopulating from the FontConfiguration
         * it has to be the inital (empty) state for the latter two, not
         * simply nulling out.
         * xFontDirsMap is a special case in that the implementation
         * will typically not ever need to initialise it so it can be null.
         */
        xFontDirsMap = null;
        xlfdMap = new HashMap(1);
        fontNameMap = new HashMap(1);
    }

    // Implements SunGraphicsEnvironment.createFontConfiguration.
    protected FontConfiguration createFontConfiguration() {

        /* The logic here decides whether to use a preconfigured
         * fontconfig.properties file, or synthesise one using platform APIs.
         * On Solaris (as opposed to OpenSolaris) we try to use the
         * pre-configured ones, but if the files it specifies are missing
         * we fail-safe to synthesising one. This might happen if Solaris
         * changes its fonts.
         * For OpenSolaris I don't expect us to ever create fontconfig files,
         * so it will always synthesise. Note that if we misidentify
         * OpenSolaris as Solaris, then the test for the presence of
         * Solaris-only font files will correct this.
         * For Linux we require an exact match of distro and version to
         * use the preconfigured file, and also that it points to
         * existent fonts.
         * If synthesising fails, we fall back to any preconfigured file
         * and do the best we can. For the commercial JDK this will be
         * fine as it includes the Lucida fonts. OpenJDK should not hit
         * this as the synthesis should always work on its platforms.
         */
        FontConfiguration mFontConfig = new MFontConfiguration(this);
        if (isOpenSolaris ||
            (isLinux &&
             (!mFontConfig.foundOsSpecificFile() ||
              !mFontConfig.fontFilesArePresent()) ||
             (isSolaris && !mFontConfig.fontFilesArePresent()))) {
            FcFontConfiguration fcFontConfig =
                new FcFontConfiguration(this);
            if (fcFontConfig.init()) {
                return fcFontConfig;
            }
        }
        mFontConfig.init();
        return mFontConfig;
    }
    public FontConfiguration
        createFontConfiguration(boolean preferLocaleFonts,
                                boolean preferPropFonts) {

        FontConfiguration config = getFontConfiguration();
        if (config instanceof FcFontConfiguration) {
            // Doesn't need to implement the alternate support.
            return config;
        }

        return new MFontConfiguration(this,
                                      preferLocaleFonts, preferPropFonts);
    }

    /**
     * Returns face name for default font, or null if
     * no face names are used for CompositeFontDescriptors
     * for this platform.
     */
    public String getDefaultFontFaceName() {

        return null;
    }

    private static native boolean pRunningXinerama();
    private static native Point getXineramaCenterPoint();

    /**
     * Override for Xinerama case: call new Solaris API for getting the correct
     * centering point from the windowing system.
     */
    public Point getCenterPoint() {
        if (runningXinerama()) {
            Point p = getXineramaCenterPoint();
            if (p != null) {
                return p;
            }
        }
        return super.getCenterPoint();
    }

    /**
     * Override for Xinerama case
     */
    public Rectangle getMaximumWindowBounds() {
        if (runningXinerama()) {
            return getXineramaWindowBounds();
        } else {
            return super.getMaximumWindowBounds();
        }
    }

    public boolean runningXinerama() {
        if (xinerState == null) {
            // pRunningXinerama() simply returns a global boolean variable,
            // so there is no need to synchronize here
            xinerState = Boolean.valueOf(pRunningXinerama());
            if (screenLog.isLoggable(Level.FINER)) {
                screenLog.log(Level.FINER, "Running Xinerama: " + xinerState);
            }
        }
        return xinerState.booleanValue();
    }

    /**
     * Return the bounds for a centered Window on a system running in Xinerama
     * mode.
     *
     * Calculations are based on the assumption of a perfectly rectangular
     * display area (display edges line up with one another, and displays
     * have consistent width and/or height).
     *
     * The bounds to return depend on the arrangement of displays and on where
     * Windows are to be centered.  There are two common situations:
     *
     * 1) The center point lies at the center of the combined area of all the
     *    displays.  In this case, the combined area of all displays is
     *    returned.
     *
     * 2) The center point lies at the center of a single display.  In this case
     *    the user most likely wants centered Windows to be constrained to that
     *    single display.  The boundaries of the one display are returned.
     *
     * It is possible for the center point to be at both the center of the
     * entire display space AND at the center of a single monitor (a square of
     * 9 monitors, for instance).  In this case, the entire display area is
     * returned.
     *
     * Because the center point is arbitrarily settable by the user, it could
     * fit neither of the cases above.  The fallback case is to simply return
     * the combined area for all screens.
     */
    protected Rectangle getXineramaWindowBounds() {
        Point center = getCenterPoint();
        Rectangle unionRect, tempRect;
        GraphicsDevice[] gds = getScreenDevices();
        Rectangle centerMonitorRect = null;
        int i;

        // if center point is at the center of all monitors
        // return union of all bounds
        //
        //  MM*MM     MMM       M
        //            M*M       *
        //            MMM       M

        // if center point is at center of a single monitor (but not of all
        // monitors)
        // return bounds of single monitor
        //
        // MMM         MM
        // MM*         *M

        // else, center is in some strange spot (such as on the border between
        // monitors), and we should just return the union of all monitors
        //
        // MM          MMM
        // MM          MMM

        unionRect = getUsableBounds(gds[0]);

        for (i = 0; i < gds.length; i++) {
            tempRect = getUsableBounds(gds[i]);
            if (centerMonitorRect == null &&
                // add a pixel or two for fudge-factor
                (tempRect.width / 2) + tempRect.x > center.x - 1 &&
                (tempRect.height / 2) + tempRect.y > center.y - 1 &&
                (tempRect.width / 2) + tempRect.x < center.x + 1 &&
                (tempRect.height / 2) + tempRect.y < center.y + 1) {
                centerMonitorRect = tempRect;
            }
            unionRect = unionRect.union(tempRect);
        }

        // first: check for center of all monitors (video wall)
        // add a pixel or two for fudge-factor
        if ((unionRect.width / 2) + unionRect.x > center.x - 1 &&
            (unionRect.height / 2) + unionRect.y > center.y - 1 &&
            (unionRect.width / 2) + unionRect.x < center.x + 1 &&
            (unionRect.height / 2) + unionRect.y < center.y + 1) {

            if (screenLog.isLoggable(Level.FINER)) {
                screenLog.log(Level.FINER, "Video Wall: center point is at center of all displays.");
            }
            return unionRect;
        }

        // next, check if at center of one monitor
        if (centerMonitorRect != null) {
            if (screenLog.isLoggable(Level.FINER)) {
                screenLog.log(Level.FINER, "Center point at center of a particular " +
                                           "monitor, but not of the entire virtual display.");
            }
            return centerMonitorRect;
        }

        // otherwise, the center is at some weird spot: return unionRect
        if (screenLog.isLoggable(Level.FINER)) {
            screenLog.log(Level.FINER, "Center point is somewhere strange - return union of all bounds.");
        }
        return unionRect;
    }

    /**
     * From the DisplayChangedListener interface; devices do not need
     * to react to this event.
     */
    @Override
    public void paletteChanged() {
    }
}
