/*
 * Copyright 1997-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.awt.AppContext;
import sun.awt.DisplayChangedListener;
import sun.awt.FontConfiguration;
import sun.awt.SunDisplayChanger;
import sun.font.CompositeFontDescriptor;
import sun.font.Font2D;
import sun.font.FontManager;
import sun.font.NativeFont;

/**
 * This is an implementation of a GraphicsEnvironment object for the
 * default local GraphicsEnvironment.
 *
 * @see GraphicsDevice
 * @see GraphicsConfiguration
 */

public abstract class SunGraphicsEnvironment extends GraphicsEnvironment
    implements FontSupport, DisplayChangedListener {

    public static boolean isLinux;
    public static boolean isSolaris;
    public static boolean isOpenSolaris;
    public static boolean isWindows;
    public static boolean noType1Font;
    private static Font defaultFont;
    private static String defaultFontFileName;
    private static String defaultFontName;
    public static final String lucidaFontName = "Lucida Sans Regular";
    public static final String lucidaFileName = "LucidaSansRegular.ttf";
    public static boolean debugFonts = false;
    protected static Logger logger = null;
    private static ArrayList badFonts;
    public static String jreLibDirName;
    public static String jreFontDirName;
    private static HashSet<String> missingFontFiles = null;

    private FontConfiguration fontConfig;

    /* fontPath is the location of all fonts on the system, excluding the
     * JRE's own font directory but including any path specified using the
     * sun.java2d.fontpath property. Together with that property,  it is
     * initialised by the getPlatformFontPath() method
     * This call must be followed by a call to registerFontDirs(fontPath)
     * once any extra debugging path has been appended.
     */
    protected String fontPath;

    /* discoveredAllFonts is set to true when all fonts on the font path are
     * discovered. This usually also implies opening, validating and
     * registering, but an implementation may be optimized to avold this.
     * So see also "loadedAllFontFiles"
     */
    private boolean discoveredAllFonts = false;

    /* loadedAllFontFiles is set to true when all fonts on the font path are
     * actually opened, validated and registered. This always implies
     * discoveredAllFonts is true.
     */
    private boolean loadedAllFontFiles = false;

    protected HashSet registeredFontFiles = new HashSet();
    public static String eudcFontFileName; /* Initialised only on windows */

    private static boolean isOpenJDK;
    /**
     * A few things in Java 2D code are different in OpenJDK,
     * so need a way to tell which implementation this is.
     * The absence of Lucida Sans Regular is the simplest way for now.
     */
    public static boolean isOpenJDK() {
        return isOpenJDK;
    }

    static {
        java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
            public Object run() {

                jreLibDirName = System.getProperty("java.home","") +
                    File.separator + "lib";
                jreFontDirName = jreLibDirName + File.separator + "fonts";
                File lucidaFile =
                    new File(jreFontDirName + File.separator + lucidaFileName);
                isOpenJDK = !lucidaFile.exists();

                String debugLevel =
                    System.getProperty("sun.java2d.debugfonts");

                if (debugLevel != null && !debugLevel.equals("false")) {
                    debugFonts = true;
                    logger = Logger.getLogger("sun.java2d");
                    if (debugLevel.equals("warning")) {
                        logger.setLevel(Level.WARNING);
                    } else if (debugLevel.equals("severe")) {
                        logger.setLevel(Level.SEVERE);
                    }
                }
                return null;
            }
        });
    };

    public SunGraphicsEnvironment() {
        java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
            public Object run() {
                String osName = System.getProperty("os.name");
                if ("Linux".equals(osName)) {
                    isLinux = true;
                } else if ("SunOS".equals(osName)) {
                    isSolaris = true;
                    String version = System.getProperty("os.version", "0.0");
                    try {
                        float ver = Float.parseFloat(version);
                        if (ver > 5.10f) {
                            File f = new File("/etc/release");
                            FileInputStream fis = new FileInputStream(f);
                            InputStreamReader isr
                                = new InputStreamReader(fis, "ISO-8859-1");
                            BufferedReader br = new BufferedReader(isr);
                            String line = br.readLine();
                            if (line.indexOf("OpenSolaris") >= 0) {
                                isOpenSolaris = true;
                            }
                            fis.close();
                        }
                    } catch (Exception e) {
                    }
                } else if ("Windows".equals(osName)) {
                    isWindows = true;
                }

                noType1Font = "true".
                    equals(System.getProperty("sun.java2d.noType1Font"));

                if (!isOpenJDK()) {
                    defaultFontName = lucidaFontName;
                    if (useAbsoluteFontFileNames()) {
                        defaultFontFileName =
                            jreFontDirName + File.separator + lucidaFileName;
                    } else {
                        defaultFontFileName = lucidaFileName;
                    }
                }

                File badFontFile =
                    new File(jreFontDirName + File.separator + "badfonts.txt");
                if (badFontFile.exists()) {
                    FileInputStream fis = null;
                    try {
                        badFonts = new ArrayList();
                        fis = new FileInputStream(badFontFile);
                        InputStreamReader isr = new InputStreamReader(fis);
                        BufferedReader br = new BufferedReader(isr);
                        while (true) {
                            String name = br.readLine();
                            if (name == null) {
                                break;
                            } else {
                                if (debugFonts) {
                                    logger.warning("read bad font: " + name);
                                }
                                badFonts.add(name);
                            }
                        }
                    } catch (IOException e) {
                        try {
                            if (fis != null) {
                                fis.close();
                            }
                        } catch (IOException ioe) {
                        }
                    }
                }

                /* Here we get the fonts in jre/lib/fonts and register them
                 * so they are always available and preferred over other fonts.
                 * This needs to be registered before the composite fonts as
                 * otherwise some native font that corresponds may be found
                 * as we don't have a way to handle two fonts of the same
                 * name, so the JRE one must be the first one registered.
                 * Pass "true" to registerFonts method as on-screen these
                 * JRE fonts always go through the T2K rasteriser.
                 */
                if (isLinux) {
                    /* Linux font configuration uses these fonts */
                    registerFontDir(jreFontDirName);
                }
                registerFontsInDir(jreFontDirName, true, Font2D.JRE_RANK,
                                   true, false);

                /* Register the JRE fonts so that the native platform can
                 * access them. This is used only on Windows so that when
                 * printing the printer driver can access the fonts.
                 */
                registerJREFontsWithPlatform(jreFontDirName);

                /* Create the font configuration and get any font path
                 * that might be specified.
                 */
                fontConfig = createFontConfiguration();
                if (isOpenJDK()) {
                    String[] fontInfo = FontManager.getDefaultPlatformFont();
                    defaultFontName = fontInfo[0];
                    defaultFontFileName = fontInfo[1];
                }
                getPlatformFontPathFromFontConfig();

                String extraFontPath = fontConfig.getExtraFontPath();

                /* In prior releases the debugging font path replaced
                 * all normally located font directories except for the
                 * JRE fonts dir. This directory is still always located and
                 * placed at the head of the path but as an augmentation
                 * to the previous behaviour the
                 * changes below allow you to additionally append to
                 * the font path by starting with append: or prepend by
                 * starting with a prepend: sign. Eg: to append
                 * -Dsun.java2d.fontpath=append:/usr/local/myfonts
                 * and to prepend
                 * -Dsun.java2d.fontpath=prepend:/usr/local/myfonts Disp
                 *
                 * If there is an appendedfontpath it in the font configuration
                 * it is used instead of searching the system for dirs.
                 * The behaviour of append and prepend is then similar
                 * to the normal case. ie it goes after what
                 * you prepend and * before what you append. If the
                 * sun.java2d.fontpath property is used, but it
                 * neither the append or prepend syntaxes is used then as
                 * except for the JRE dir the path is replaced and it is
                 * up to you to make sure that all the right directories
                 * are located. This is platform and locale-specific so
                 * its almost impossible to get right, so it should be
                 * used with caution.
                 */
                boolean prependToPath = false;
                boolean appendToPath = false;
                String dbgFontPath = System.getProperty("sun.java2d.fontpath");

                if (dbgFontPath != null) {
                    if (dbgFontPath.startsWith("prepend:")) {
                        prependToPath = true;
                        dbgFontPath =
                            dbgFontPath.substring("prepend:".length());
                    } else if (dbgFontPath.startsWith("append:")) {
                        appendToPath = true;
                        dbgFontPath =
                            dbgFontPath.substring("append:".length());
                    }
                }

                if (debugFonts) {
                    logger.info("JRE font directory: " + jreFontDirName);
                    logger.info("Extra font path: " + extraFontPath);
                    logger.info("Debug font path: " + dbgFontPath);
                }

                if (dbgFontPath != null) {
                    /* In debugging mode we register all the paths
                     * Caution: this is a very expensive call on Solaris:-
                     */
                    fontPath = getPlatformFontPath(noType1Font);

                    if (extraFontPath != null) {
                        fontPath =
                            extraFontPath + File.pathSeparator + fontPath;
                    }
                    if (appendToPath) {
                        fontPath = fontPath + File.pathSeparator + dbgFontPath;
                    } else if (prependToPath) {
                        fontPath = dbgFontPath + File.pathSeparator + fontPath;
                    } else {
                        fontPath = dbgFontPath;
                    }
                    registerFontDirs(fontPath);
                } else if (extraFontPath != null) {
                    /* If the font configuration contains an "appendedfontpath"
                     * entry, it is interpreted as a set of locations that
                     * should always be registered.
                     * It may be additional to locations normally found for
                     * that place, or it may be locations that need to have
                     * all their paths registered to locate all the needed
                     * platform names.
                     * This is typically when the same .TTF file is referenced
                     * from multiple font.dir files and all of these must be
                     * read to find all the native (XLFD) names for the font,
                     * so that X11 font APIs can be used for as many code
                     * points as possible.
                     */
                    registerFontDirs(extraFontPath);
                }

                /* On Solaris, we need to register the Japanese TrueType
                 * directory so that we can find the corresponding bitmap
                 * fonts. This could be done by listing the directory in
                 * the font configuration file, but we don't want to
                 * confuse users with this quirk. There are no bitmap fonts
                 * for other writing systems that correspond to TrueType
                 * fonts and have matching XLFDs. We need to register the
                 * bitmap fonts only in environments where they're on the
                 * X font path, i.e., in the Japanese locale.
                 * Note that if the X Toolkit is in use the font path isn't
                 * set up by JDK, but users of a JA locale should have it
                 * set up already by their login environment.
                 */
                if (isSolaris && Locale.JAPAN.equals(Locale.getDefault())) {
                    registerFontDir("/usr/openwin/lib/locale/ja/X11/fonts/TT");
                }

                initCompositeFonts(fontConfig, null);

                /* Establish the default font to be used by SG2D etc */
                defaultFont = new Font(Font.DIALOG, Font.PLAIN, 12);

                return null;
            }
        });
    }

    protected GraphicsDevice[] screens;

    /**
     * Returns an array of all of the screen devices.
     */
    public synchronized GraphicsDevice[] getScreenDevices() {
        GraphicsDevice[] ret = screens;
        if (ret == null) {
            int num = getNumScreens();
            ret = new GraphicsDevice[num];
            for (int i = 0; i < num; i++) {
                ret[i] = makeScreenDevice(i);
            }
            screens = ret;
        }
        return ret;
    }

    protected abstract int getNumScreens();
    protected abstract GraphicsDevice makeScreenDevice(int screennum);

    /**
     * Returns the default screen graphics device.
     */
    public GraphicsDevice getDefaultScreenDevice() {
        return getScreenDevices()[0];
    }

    /**
     * Returns a Graphics2D object for rendering into the
     * given BufferedImage.
     * @throws NullPointerException if BufferedImage argument is null
     */
    public Graphics2D createGraphics(BufferedImage img) {
        if (img == null) {
            throw new NullPointerException("BufferedImage cannot be null");
        }
        SurfaceData sd = SurfaceData.getPrimarySurfaceData(img);
        return new SunGraphics2D(sd, Color.white, Color.black, defaultFont);
    }

    /* A call to this method should be followed by a call to
     * registerFontDirs(..)
     */
    protected String getPlatformFontPath(boolean noType1Font) {
        if (fontPath == null) {
            fontPath = FontManager.getFontPath(noType1Font);
        }
        return fontPath;
    }

    private String[] platformFontDirs;
    /**
     * Get all directories which contain installed fonts.
     */
    public String[] getPlatformFontDirs() {
        if (platformFontDirs == null) {
            String path = getPlatformFontPath(noType1Font);
            StringTokenizer parser =
                new StringTokenizer(path, File.pathSeparator);;
            ArrayList<String> pathList = new ArrayList<String>();
            try {
                while (parser.hasMoreTokens()) {
                    pathList.add(parser.nextToken());
                }
            } catch (NoSuchElementException e) {
            }
            platformFontDirs = pathList.toArray(new String[0]);
        }
        return platformFontDirs;
    }

    /**
     * Whether registerFontFile expects absolute or relative
     * font file names.
     */
    protected boolean useAbsoluteFontFileNames() {
        return true;
    }

    /**
     * Returns file name for default font, either absolute
     * or relative as needed by registerFontFile.
     */
    public String getDefaultFontFile() {
        return defaultFontFileName;
    }

    /**
     * Returns face name for default font, or null if
     * no face names are used for CompositeFontDescriptors
     * for this platform.
     */
    public String getDefaultFontFaceName() {
        return defaultFontName;
    }

    public void loadFonts() {
        if (discoveredAllFonts) {
            return;
        }
        /* Use lock specific to the font system */
        synchronized (lucidaFontName) {
            if (debugFonts) {
                Thread.dumpStack();
                logger.info("SunGraphicsEnvironment.loadFonts() called");
            }
            FontManager.initialiseDeferredFonts();

            java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
                public Object run() {
                    if (fontPath == null) {
                        fontPath = getPlatformFontPath(noType1Font);
                        registerFontDirs(fontPath);
                    }
                    if (fontPath != null) {
                        // this will find all fonts including those already
                        // registered. But we have checks in place to prevent
                        // double registration.
                        if (!FontManager.gotFontsFromPlatform()) {
                            registerFontsOnPath(fontPath, false,
                                                Font2D.UNKNOWN_RANK,
                                                false, true);
                            loadedAllFontFiles = true;
                        }
                    }
                    FontManager.registerOtherFontFiles(registeredFontFiles);
                    discoveredAllFonts = true;
                    return null;
                }
            });
        }
    }


    public void loadFontFiles() {
        loadFonts();
        if (loadedAllFontFiles) {
            return;
        }
        /* Use lock specific to the font system */
        synchronized (lucidaFontName) {
            if (debugFonts) {
                Thread.dumpStack();
                logger.info("loadAllFontFiles() called");
            }
            java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
                public Object run() {
                    if (fontPath == null) {
                        fontPath = getPlatformFontPath(noType1Font);
                    }
                    if (fontPath != null) {
                        // this will find all fonts including those already
                        // registered. But we have checks in place to prevent
                        // double registration.
                        registerFontsOnPath(fontPath, false,
                                            Font2D.UNKNOWN_RANK,
                                            false, true);
                    }
                    loadedAllFontFiles = true;
                    return null;
                }
            });
        }
    }

    /*
     * This is for use only within getAllFonts().
     * Fonts listed in the fontconfig files for windows were all
     * on the "deferred" initialisation list. They were registered
     * either in the course of the application, or in the call to
     * loadFonts() within getAllFonts(). The fontconfig file specifies
     * the names of the fonts using the English names. If there's a
     * different name in the execution locale, then the platform will
     * report that, and we will construct the font with both names, and
     * thereby enumerate it twice. This happens for Japanese fonts listed
     * in the windows fontconfig, when run in the JA locale. The solution
     * is to rely (in this case) on the platform's font->file mapping to
     * determine that this name corresponds to a file we already registered.
     * This works because
     * - we know when we get here all deferred fonts are already initialised
     * - when we register a font file, we register all fonts in it.
     * - we know the fontconfig fonts are all in the windows registry
     */
    private boolean isNameForRegisteredFile(String fontName) {
        String fileName = FontManager.getFileNameForFontName(fontName);
        if (fileName == null) {
            return false;
        }
        return registeredFontFiles.contains(fileName);
    }

    private Font[] allFonts;

    /**
     * Returns all fonts installed in this environment.
     */
    public Font[] getAllInstalledFonts() {
        if (allFonts == null) {
            loadFonts();
            TreeMap fontMapNames = new TreeMap();
            /* warning: the number of composite fonts could change dynamically
             * if applications are allowed to create them. "allfonts" could
             * then be stale.
             */

            Font2D[] allfonts = FontManager.getRegisteredFonts();
            for (int i=0; i < allfonts.length; i++) {
                if (!(allfonts[i] instanceof NativeFont)) {
                    fontMapNames.put(allfonts[i].getFontName(null),
                                     allfonts[i]);
                }
            }

            String[] platformNames =  FontManager.getFontNamesFromPlatform();
            if (platformNames != null) {
                for (int i=0; i<platformNames.length; i++) {
                    if (!isNameForRegisteredFile(platformNames[i])) {
                        fontMapNames.put(platformNames[i], null);
                    }
                }
            }

            String[] fontNames = null;
            if (fontMapNames.size() > 0) {
                fontNames = new String[fontMapNames.size()];
                Object [] keyNames = fontMapNames.keySet().toArray();
                for (int i=0; i < keyNames.length; i++) {
                    fontNames[i] = (String)keyNames[i];
                }
            }
            Font[] fonts = new Font[fontNames.length];
            for (int i=0; i < fontNames.length; i++) {
                fonts[i] = new Font(fontNames[i], Font.PLAIN, 1);
                Font2D f2d = (Font2D)fontMapNames.get(fontNames[i]);
                if (f2d  != null) {
                    FontManager.setFont2D(fonts[i], f2d.handle);
                }
            }
            allFonts = fonts;
        }

        Font []copyFonts = new Font[allFonts.length];
        System.arraycopy(allFonts, 0, copyFonts, 0, allFonts.length);
        return copyFonts;
    }

     /**
     * Returns all fonts available in this environment.
     */
    public Font[] getAllFonts() {
        Font[] installedFonts = getAllInstalledFonts();
        Font[] created = FontManager.getCreatedFonts();
        if (created == null || created.length == 0) {
            return installedFonts;
        } else {
            int newlen = installedFonts.length + created.length;
            Font [] fonts = java.util.Arrays.copyOf(installedFonts, newlen);
            System.arraycopy(created, 0, fonts,
                             installedFonts.length, created.length);
            return fonts;
        }
    }

    /**
     * Default locale can be changed but we need to know the initial locale
     * as that is what is used by native code. Changing Java default locale
     * doesn't affect that.
     * Returns the locale in use when using native code to communicate
     * with platform APIs. On windows this is known as the "system" locale,
     * and it is usually the same as the platform locale, but not always,
     * so this method also checks an implementation property used only
     * on windows and uses that if set.
     */
    private static Locale systemLocale = null;
    public static Locale getSystemStartupLocale() {
        if (systemLocale == null) {
            systemLocale = (Locale)
                java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
            public Object run() {
                /* On windows the system locale may be different than the
                 * user locale. This is an unsupported configuration, but
                 * in that case we want to return a dummy locale that will
                 * never cause a match in the usage of this API. This is
                 * important because Windows documents that the family
                 * names of fonts are enumerated using the language of
                 * the system locale. BY returning a dummy locale in that
                 * case we do not use the platform API which would not
                 * return us the names we want.
                 */
                String fileEncoding = System.getProperty("file.encoding", "");
                String sysEncoding = System.getProperty("sun.jnu.encoding");
                if (sysEncoding != null && !sysEncoding.equals(fileEncoding)) {
                    return Locale.ROOT;
                }

                String language = System.getProperty("user.language", "en");
                String country  = System.getProperty("user.country","");
                String variant  = System.getProperty("user.variant","");
                return new Locale(language, country, variant);
            }
        });
        }
        return systemLocale;
    }

    /* Really we need only the JRE fonts family names, but there's little
     * overhead in doing this the easy way by adding all the currently
     * known fonts.
     */
    protected void getJREFontFamilyNames(TreeMap<String,String> familyNames,
                                         Locale requestedLocale) {
        FontManager.registerDeferredJREFonts(jreFontDirName);
        Font2D[] physicalfonts = FontManager.getPhysicalFonts();
        for (int i=0; i < physicalfonts.length; i++) {
            if (!(physicalfonts[i] instanceof NativeFont)) {
                String name =
                    physicalfonts[i].getFamilyName(requestedLocale);
                familyNames.put(name.toLowerCase(requestedLocale), name);
            }
        }
    }

    private String[] allFamilies; // cache for default locale only
    private Locale lastDefaultLocale;

    public String[] getInstalledFontFamilyNames(Locale requestedLocale) {
        if (requestedLocale == null) {
            requestedLocale = Locale.getDefault();
        }
        if (allFamilies != null && lastDefaultLocale != null &&
            requestedLocale.equals(lastDefaultLocale)) {
                String[] copyFamilies = new String[allFamilies.length];
                System.arraycopy(allFamilies, 0, copyFamilies,
                                 0, allFamilies.length);
                return copyFamilies;
        }

        TreeMap<String,String> familyNames = new TreeMap<String,String>();
        //  these names are always there and aren't localised
        String str;
        str = Font.SERIF;         familyNames.put(str.toLowerCase(), str);
        str = Font.SANS_SERIF;    familyNames.put(str.toLowerCase(), str);
        str = Font.MONOSPACED;    familyNames.put(str.toLowerCase(), str);
        str = Font.DIALOG;        familyNames.put(str.toLowerCase(), str);
        str = Font.DIALOG_INPUT;  familyNames.put(str.toLowerCase(), str);

        /* Platform APIs may be used to get the set of available family
         * names for the current default locale so long as it is the same
         * as the start-up system locale, rather than loading all fonts.
         */
        if (requestedLocale.equals(getSystemStartupLocale()) &&
            FontManager.getFamilyNamesFromPlatform(familyNames,
                                                    requestedLocale)) {
            /* Augment platform names with JRE font family names */
            getJREFontFamilyNames(familyNames, requestedLocale);
        } else {
            loadFontFiles();
            Font2D[] physicalfonts = FontManager.getPhysicalFonts();
            for (int i=0; i < physicalfonts.length; i++) {
                if (!(physicalfonts[i] instanceof NativeFont)) {
                    String name =
                        physicalfonts[i].getFamilyName(requestedLocale);
                    familyNames.put(name.toLowerCase(requestedLocale), name);
                }
            }
        }

        String[] retval =  new String[familyNames.size()];
        Object [] keyNames = familyNames.keySet().toArray();
        for (int i=0; i < keyNames.length; i++) {
            retval[i] = (String)familyNames.get(keyNames[i]);
        }
        if (requestedLocale.equals(Locale.getDefault())) {
            lastDefaultLocale = requestedLocale;
            allFamilies = new String[retval.length];
            System.arraycopy(retval, 0, allFamilies, 0, allFamilies.length);
        }
        return retval;
    }

    public String[] getAvailableFontFamilyNames(Locale requestedLocale) {
        String[] installed = getInstalledFontFamilyNames(requestedLocale);
        /* Use a new TreeMap as used in getInstalledFontFamilyNames
         * and insert all the keys in lower case, so that the sort order
         * is the same as the installed families. This preserves historical
         * behaviour and inserts new families in the right place.
         * It would have been marginally more efficient to directly obtain
         * the tree map and just insert new entries, but not so much as
         * to justify the extra internal interface.
         */
        TreeMap<String, String> map = FontManager.getCreatedFontFamilyNames();
        if (map == null || map.size() == 0) {
            return installed;
        } else {
            for (int i=0; i<installed.length; i++) {
                map.put(installed[i].toLowerCase(requestedLocale),
                        installed[i]);
            }
            String[] retval =  new String[map.size()];
            Object [] keyNames = map.keySet().toArray();
            for (int i=0; i < keyNames.length; i++) {
                retval[i] = (String)map.get(keyNames[i]);
            }
            return retval;
        }
    }

    public String[] getAvailableFontFamilyNames() {
        return getAvailableFontFamilyNames(Locale.getDefault());
    }

    /**
     * Returns a file name for the physical font represented by this platform
     * font name. The default implementation tries to obtain the file name
     * from the font configuration.
     * Subclasses may override to provide information from other sources.
     */
    protected String getFileNameFromPlatformName(String platformFontName) {
        return fontConfig.getFileNameFromPlatformName(platformFontName);
    }

    public static class TTFilter implements FilenameFilter {
        public boolean accept(File dir,String name) {
            /* all conveniently have the same suffix length */
            int offset = name.length()-4;
            if (offset <= 0) { /* must be at least A.ttf */
                return false;
            } else {
                return(name.startsWith(".ttf", offset) ||
                       name.startsWith(".TTF", offset) ||
                       name.startsWith(".ttc", offset) ||
                       name.startsWith(".TTC", offset));
            }
        }
    }

    public static class T1Filter implements FilenameFilter {
        public boolean accept(File dir,String name) {
            if (noType1Font) {
                return false;
            }
            /* all conveniently have the same suffix length */
            int offset = name.length()-4;
            if (offset <= 0) { /* must be at least A.pfa */
                return false;
            } else {
                return(name.startsWith(".pfa", offset) ||
                       name.startsWith(".pfb", offset) ||
                       name.startsWith(".PFA", offset) ||
                       name.startsWith(".PFB", offset));
            }
        }
    }

     public static class TTorT1Filter implements FilenameFilter {
        public boolean accept(File dir, String name) {

            /* all conveniently have the same suffix length */
            int offset = name.length()-4;
            if (offset <= 0) { /* must be at least A.ttf or A.pfa */
                return false;
            } else {
                boolean isTT =
                    name.startsWith(".ttf", offset) ||
                    name.startsWith(".TTF", offset) ||
                    name.startsWith(".ttc", offset) ||
                    name.startsWith(".TTC", offset);
                if (isTT) {
                    return true;
                } else if (noType1Font) {
                    return false;
                } else {
                    return(name.startsWith(".pfa", offset) ||
                           name.startsWith(".pfb", offset) ||
                           name.startsWith(".PFA", offset) ||
                           name.startsWith(".PFB", offset));
                }
            }
        }
    }

    /* No need to keep consing up new instances - reuse a singleton.
     * The trade-off is that these objects don't get GC'd.
     */
    public static final TTFilter ttFilter = new TTFilter();
    public static final T1Filter t1Filter = new T1Filter();

    /* The majority of the register functions in this class are
     * registering platform fonts in the JRE's font maps.
     * The next one is opposite in function as it registers the JRE
     * fonts as platform fonts. If subsequent to calling this
     * your implementation enumerates platform fonts in a way that
     * would return these fonts too you may get duplicates.
     * This function is primarily used to install the JRE fonts
     * so that the native platform can access them.
     * It is intended to be overridden by platform subclasses
     * Currently minimal use is made of this as generally
     * Java 2D doesn't need the platform to be able to
     * use its fonts and platforms which already have matching
     * fonts registered (possibly even from other different JRE
     * versions) generally can't be guaranteed to use the
     * one registered by this JRE version in response to
     * requests from this JRE.
     */
    protected void registerJREFontsWithPlatform(String pathName) {
        return;
    }

    /* Called from FontManager - has Solaris specific implementation */
    public void register1dot0Fonts() {
        java.security.AccessController.doPrivileged(
                            new java.security.PrivilegedAction() {
            public Object run() {
                String type1Dir = "/usr/openwin/lib/X11/fonts/Type1";
                registerFontsInDir(type1Dir, true, Font2D.TYPE1_RANK,
                                   false, false);
                return null;
            }
        });
    }

    protected void registerFontDirs(String pathName) {
        return;
    }

    /* Called to register fall back fonts */
    public void registerFontsInDir(String dirName) {
        registerFontsInDir(dirName, true, Font2D.JRE_RANK, true, false);
    }

    private void registerFontsInDir(String dirName, boolean useJavaRasterizer,
                                    int fontRank,
                                    boolean defer, boolean resolveSymLinks) {
        File pathFile = new File(dirName);
        addDirFonts(dirName, pathFile, ttFilter,
                    FontManager.FONTFORMAT_TRUETYPE, useJavaRasterizer,
                    fontRank==Font2D.UNKNOWN_RANK ?
                    Font2D.TTF_RANK : fontRank,
                    defer, resolveSymLinks);
        addDirFonts(dirName, pathFile, t1Filter,
                    FontManager.FONTFORMAT_TYPE1, useJavaRasterizer,
                    fontRank==Font2D.UNKNOWN_RANK ?
                    Font2D.TYPE1_RANK : fontRank,
                    defer, resolveSymLinks);
    }

    private void registerFontsOnPath(String pathName,
                                     boolean useJavaRasterizer, int fontRank,
                                     boolean defer, boolean resolveSymLinks) {

        StringTokenizer parser = new StringTokenizer(pathName,
                                                     File.pathSeparator);
        try {
            while (parser.hasMoreTokens()) {
                registerFontsInDir(parser.nextToken(),
                                   useJavaRasterizer, fontRank,
                                   defer, resolveSymLinks);
            }
        } catch (NoSuchElementException e) {
        }
    }

    protected void registerFontFile(String fontFileName, String[] nativeNames,
                                    int fontRank, boolean defer) {
        // REMIND: case compare depends on platform
        if (registeredFontFiles.contains(fontFileName)) {
            return;
        }
        int fontFormat;
        if (ttFilter.accept(null, fontFileName)) {
            fontFormat = FontManager.FONTFORMAT_TRUETYPE;
        } else if (t1Filter.accept(null, fontFileName)) {
            fontFormat = FontManager.FONTFORMAT_TYPE1;
        } else {
            fontFormat = FontManager.FONTFORMAT_NATIVE;
        }
        registeredFontFiles.add(fontFileName);
        if (defer) {
            FontManager.registerDeferredFont(fontFileName,
                                             fontFileName, nativeNames,
                                             fontFormat, false, fontRank);
        } else {
            FontManager.registerFontFile(fontFileName, nativeNames,
                                         fontFormat, false, fontRank);
        }
    }

    protected void registerFontDir(String path) {
    }

    protected String[] getNativeNames(String fontFileName,
                                      String platformName) {
        return null;
    }

    /*
     * helper function for registerFonts
     */
    private void addDirFonts(String dirName, File dirFile,
                             FilenameFilter filter,
                             int fontFormat, boolean useJavaRasterizer,
                             int fontRank,
                             boolean defer, boolean resolveSymLinks) {
        String[] ls = dirFile.list(filter);
        if (ls == null || ls.length == 0) {
            return;
        }
        String[] fontNames = new String[ls.length];
        String[][] nativeNames = new String[ls.length][];
        int fontCount = 0;

        for (int i=0; i < ls.length; i++ ) {
            File theFile = new File(dirFile, ls[i]);
            String fullName = null;
            if (resolveSymLinks) {
                try {
                    fullName = theFile.getCanonicalPath();
                } catch (IOException e) {
                }
            }
            if (fullName == null) {
                fullName = dirName + File.separator + ls[i];
            }

            // REMIND: case compare depends on platform
            if (registeredFontFiles.contains(fullName)) {
                continue;
            }

            if (badFonts != null && badFonts.contains(fullName)) {
                if (debugFonts) {
                    logger.warning("skip bad font " + fullName);
                }
                continue; // skip this font file.
            }

            registeredFontFiles.add(fullName);

            if (debugFonts && logger.isLoggable(Level.INFO)) {
                String message = "Registering font " + fullName;
                String[] natNames = getNativeNames(fullName, null);
                if (natNames == null) {
                    message += " with no native name";
                } else {
                    message += " with native name(s) " + natNames[0];
                    for (int nn = 1; nn < natNames.length; nn++) {
                        message += ", " + natNames[nn];
                    }
                }
                logger.info(message);
            }
            fontNames[fontCount] = fullName;
            nativeNames[fontCount++] = getNativeNames(fullName, null);
        }
        FontManager.registerFonts(fontNames, nativeNames, fontCount,
                                  fontFormat, useJavaRasterizer, fontRank,
                                  defer);
        return;
    }

    /*
     * A GE may verify whether a font file used in a fontconfiguration
     * exists. If it doesn't then either we may substitute the default
     * font, or perhaps elide it altogether from the composite font.
     * This makes some sense on windows where the font file is only
     * likely to be in one place. But on other OSes, eg Linux, the file
     * can move around depending. So there we probably don't want to assume
     * its missing and so won't add it to this list.
     * If this list - missingFontFiles - is non-null then the composite
     * font initialisation logic tests to see if a font file is in that
     * set.
     * Only one thread should be able to add to this set so we don't
     * synchronize.
     */
    protected void addToMissingFontFileList(String fileName) {
        if (missingFontFiles == null) {
            missingFontFiles = new HashSet<String>();
        }
        missingFontFiles.add(fileName);
    }

    /**
     * Creates this environment's FontConfiguration.
     */
    protected abstract FontConfiguration createFontConfiguration();

    public abstract FontConfiguration
        createFontConfiguration(boolean preferLocaleFonts,
                                boolean preferPropFonts);

    /*
     * This method asks the font configuration API for all platform names
     * used as components of composite/logical fonts and iterates over these
     * looking up their corresponding file name and registers these fonts.
     * It also ensures that the fonts are accessible via platform APIs.
     * The composites themselves are then registered.
     */
    private void
        initCompositeFonts(FontConfiguration fontConfig,
                           ConcurrentHashMap<String, Font2D>  altNameCache) {

        int numCoreFonts = fontConfig.getNumberCoreFonts();
        String[] fcFonts = fontConfig.getPlatformFontNames();
        for (int f=0; f<fcFonts.length; f++) {
            String platformFontName = fcFonts[f];
            String fontFileName =
                getFileNameFromPlatformName(platformFontName);
            String[] nativeNames = null;
            if (fontFileName == null || fontFileName.equals(platformFontName)){
                /* No file located, so register using the platform name,
                 * i.e. as a native font.
                 */
                fontFileName = platformFontName;
            } else {
                if (f < numCoreFonts) {
                    /* If platform APIs also need to access the font, add it
                     * to a set to be registered with the platform too.
                     * This may be used to add the parent directory to the X11
                     * font path if its not already there. See the docs for the
                     * subclass implementation.
                     * This is now mainly for the benefit of X11-based AWT
                     * But for historical reasons, 2D initialisation code
                     * makes these calls.
                     * If the fontconfiguration file is properly set up
                     * so that all fonts are mapped to files and all their
                     * appropriate directories are specified, then this
                     * method will be low cost as it will return after
                     * a test that finds a null lookup map.
                     */
                    addFontToPlatformFontPath(platformFontName);
                }
                nativeNames = getNativeNames(fontFileName, platformFontName);
            }
            /* Uncomment these two lines to "generate" the XLFD->filename
             * mappings needed to speed start-up on Solaris.
             * Augment this with the appendedpathname and the mappings
             * for native (F3) fonts
             */
            //String platName = platformFontName.replaceAll(" ", "_");
            //System.out.println("filename."+platName+"="+fontFileName);
            registerFontFile(fontFileName, nativeNames,
                             Font2D.FONT_CONFIG_RANK, true);


        }
        /* This registers accumulated paths from the calls to
         * addFontToPlatformFontPath(..) and any specified by
         * the font configuration. Rather than registering
         * the fonts it puts them in a place and form suitable for
         * the Toolkit to pick up and use if a toolkit is initialised,
         * and if it uses X11 fonts.
         */
        registerPlatformFontsUsedByFontConfiguration();

        CompositeFontDescriptor[] compositeFontInfo
                = fontConfig.get2DCompositeFontInfo();
        for (int i = 0; i < compositeFontInfo.length; i++) {
            CompositeFontDescriptor descriptor = compositeFontInfo[i];
            String[] componentFileNames = descriptor.getComponentFileNames();
            String[] componentFaceNames = descriptor.getComponentFaceNames();

            /* It would be better eventually to handle this in the
             * FontConfiguration code which should also remove duplicate slots
             */
            if (missingFontFiles != null) {
                for (int ii=0; ii<componentFileNames.length; ii++) {
                    if (missingFontFiles.contains(componentFileNames[ii])) {
                        componentFileNames[ii] = getDefaultFontFile();
                        componentFaceNames[ii] = getDefaultFontFaceName();
                    }
                }
            }

            /* FontConfiguration needs to convey how many fonts it has added
             * as fallback component fonts which should not affect metrics.
             * The core component count will be the number of metrics slots.
             * This does not preclude other mechanisms for adding
             * fall back component fonts to the composite.
             */
            if (altNameCache != null) {
                FontManager.registerCompositeFont(
                    descriptor.getFaceName(),
                    componentFileNames, componentFaceNames,
                    descriptor.getCoreComponentCount(),
                    descriptor.getExclusionRanges(),
                    descriptor.getExclusionRangeLimits(),
                    true,
                    altNameCache);
            } else {
                FontManager.registerCompositeFont(
                    descriptor.getFaceName(),
                    componentFileNames, componentFaceNames,
                    descriptor.getCoreComponentCount(),
                    descriptor.getExclusionRanges(),
                    descriptor.getExclusionRangeLimits(),
                    true);
            }
            if (debugFonts) {
                logger.info("registered " + descriptor.getFaceName());
            }
        }
    }

    /**
     * Notifies graphics environment that the logical font configuration
     * uses the given platform font name. The graphics environment may
     * use this for platform specific initialization.
     */
    protected void addFontToPlatformFontPath(String platformFontName) {
    }

    protected void registerPlatformFontsUsedByFontConfiguration() {
    }

    /**
     * Determines whether the given font is a logical font.
     */
    public static boolean isLogicalFont(Font f) {
        return FontConfiguration.isLogicalFontFamilyName(f.getFamily());
    }

    /**
     * Return the default font configuration.
     */
    public FontConfiguration getFontConfiguration() {
        return fontConfig;
    }

    /**
     * Return the bounds of a GraphicsDevice, less its screen insets.
     * See also java.awt.GraphicsEnvironment.getUsableBounds();
     */
    public static Rectangle getUsableBounds(GraphicsDevice gd) {
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle usableBounds = gc.getBounds();

        usableBounds.x += insets.left;
        usableBounds.y += insets.top;
        usableBounds.width -= (insets.left + insets.right);
        usableBounds.height -= (insets.top + insets.bottom);

        return usableBounds;
    }

    /**
     * This method is provided for internal and exclusive use by Swing.
     * This method should no longer be called, instead directly call
     * FontManager.fontSupportsDefaultEncoding(Font).
     * This method will be removed once Swing is updated to no longer
     * call it.
     */
    public static boolean fontSupportsDefaultEncoding(Font font) {
        return FontManager.fontSupportsDefaultEncoding(font);
    }

    public static void useAlternateFontforJALocales() {
        FontManager.useAlternateFontforJALocales();
    }

    /*
     * This invocation is not in a privileged block because
     * all privileged operations (reading files and properties)
     * was conducted on the creation of the GE
     */
    public void
        createCompositeFonts(ConcurrentHashMap<String, Font2D> altNameCache,
                             boolean preferLocale,
                             boolean preferProportional) {

        FontConfiguration fontConfig =
            createFontConfiguration(preferLocale, preferProportional);
        initCompositeFonts(fontConfig, altNameCache);
    }

    /* If (as we do on X11) need to set a platform font path,
     * then the needed path may be specified by the platform
     * specific FontConfiguration class & data file. Such platforms
     * (ie X11) need to override this method to retrieve this information
     * into suitable data structures.
     */
    protected void getPlatformFontPathFromFontConfig() {
    }

    /**
     * From the DisplayChangedListener interface; called
     * when the display mode has been changed.
     */
    public void displayChanged() {
        // notify screens in device array to do display update stuff
        for (GraphicsDevice gd : getScreenDevices()) {
            if (gd instanceof DisplayChangedListener) {
                ((DisplayChangedListener) gd).displayChanged();
            }
        }

        // notify SunDisplayChanger list (e.g. VolatileSurfaceManagers and
        // SurfaceDataProxies) about the display change event
        displayChanger.notifyListeners();
    }

    /**
     * Part of the DisplayChangedListener interface:
     * propagate this event to listeners
     */
    public void paletteChanged() {
        displayChanger.notifyPaletteChanged();
    }

    /*
     * ----DISPLAY CHANGE SUPPORT----
     */

    protected SunDisplayChanger displayChanger = new SunDisplayChanger();

    /**
     * Add a DisplayChangeListener to be notified when the display settings
     * are changed.
     */
    public void addDisplayChangedListener(DisplayChangedListener client) {
        displayChanger.add(client);
    }

    /**
     * Remove a DisplayChangeListener from Win32GraphicsEnvironment
     */
    public void removeDisplayChangedListener(DisplayChangedListener client) {
        displayChanger.remove(client);
    }

    /*
     * ----END DISPLAY CHANGE SUPPORT----
     */
}
