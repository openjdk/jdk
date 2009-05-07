/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.font;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.FontFormatException;
import java.io.File;
import java.io.FilenameFilter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.plaf.FontUIResource;

import sun.awt.AppContext;
import sun.awt.FontConfiguration;
import sun.awt.SunHints;
import sun.awt.SunToolkit;
import sun.java2d.HeadlessGraphicsEnvironment;
import sun.java2d.SunGraphicsEnvironment;

import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import java.lang.reflect.Constructor;

import sun.java2d.Disposer;

/*
 * Interface between Java Fonts (java.awt.Font) and the underlying
 * font files/native font resources and the Java and native font scalers.
 */
public final class FontManager {

    public static final int FONTFORMAT_NONE      = -1;
    public static final int FONTFORMAT_TRUETYPE  = 0;
    public static final int FONTFORMAT_TYPE1     = 1;
    public static final int FONTFORMAT_T2K       = 2;
    public static final int FONTFORMAT_TTC       = 3;
    public static final int FONTFORMAT_COMPOSITE = 4;
    public static final int FONTFORMAT_NATIVE    = 5;

    public static final int NO_FALLBACK         = 0;
    public static final int PHYSICAL_FALLBACK   = 1;
    public static final int LOGICAL_FALLBACK    = 2;

    public static final int QUADPATHTYPE = 1;
    public static final int CUBICPATHTYPE = 2;

    /* Pool of 20 font file channels chosen because some UTF-8 locale
     * composite fonts can use up to 16 platform fonts (including the
     * Lucida fall back). This should prevent channel thrashing when
     * dealing with one of these fonts.
     * The pool array stores the fonts, rather than directly referencing
     * the channels, as the font needs to do the open/close work.
     */
    private static final int CHANNELPOOLSIZE = 20;
    private static int lastPoolIndex = 0;
    private static FileFont fontFileCache[] = new FileFont[CHANNELPOOLSIZE];

    /* Need to implement a simple linked list scheme for fast
     * traversal and lookup.
     * Also want to "fast path" dialog so there's minimal overhead.
     */
    /* There are at exactly 20 composite fonts: 5 faces (but some are not
     * usually different), in 4 styles. The array may be auto-expanded
     * later if more are needed, eg for user-defined composites or locale
     * variants.
     */
    private static int maxCompFont = 0;
    private static CompositeFont [] compFonts = new CompositeFont[20];
    private static ConcurrentHashMap<String, CompositeFont>
        compositeFonts = new ConcurrentHashMap<String, CompositeFont>();
    private static ConcurrentHashMap<String, PhysicalFont>
        physicalFonts = new ConcurrentHashMap<String, PhysicalFont>();
    private static ConcurrentHashMap<String, PhysicalFont>
        registeredFontFiles = new ConcurrentHashMap<String, PhysicalFont>();

    /* given a full name find the Font. Remind: there's duplication
     * here in that this contains the content of compositeFonts +
     * physicalFonts.
     */
    private static ConcurrentHashMap<String, Font2D>
        fullNameToFont = new ConcurrentHashMap<String, Font2D>();

    /* TrueType fonts have localised names. Support searching all
     * of these before giving up on a name.
     */
    private static HashMap<String, TrueTypeFont> localeFullNamesToFont;

    private static PhysicalFont defaultPhysicalFont;

    /* deprecated, unsupported hack - actually invokes a bug! */
    private static boolean usePlatformFontMetrics = false;

    public static Logger logger = null;
    public static boolean logging;
    static boolean longAddresses;
    static String osName;
    static boolean useT2K;
    static boolean isWindows;
    static boolean isSolaris;
    public static boolean isSolaris8; // needed to check for JA wavedash fix.
    public static boolean isSolaris9; // needed to check for songti font usage.
    private static boolean loaded1dot0Fonts = false;
    static SunGraphicsEnvironment sgEnv;
    static boolean loadedAllFonts = false;
    static boolean loadedAllFontFiles = false;
    static TrueTypeFont eudcFont;
    static HashMap<String,String> jreFontMap;
    static HashSet<String> jreLucidaFontFiles;
    static String[] jreOtherFontFiles;
    static boolean noOtherJREFontFiles = false; // initial assumption.
    static boolean fontConfigFailed = false;

    /* Used to indicate required return type from toArray(..); */
    private static String[] STR_ARRAY = new String[0];

    private static void initJREFontMap() {

        /* Key is familyname+style value as an int.
         * Value is filename containing the font.
         * If no mapping exists, it means there is no font file for the style
         * If the mapping exists but the file doesn't exist in the deferred
         * list then it means its not installed.
         * This looks like a lot of code and strings but if it saves even
         * a single file being opened at JRE start-up there's a big payoff.
         * Lucida Sans is probably the only important case as the others
         * are rarely used. Consider removing the other mappings if there's
         * no evidence they are useful in practice.
         */
        jreFontMap = new HashMap<String,String>();
        jreLucidaFontFiles = new HashSet<String>();
        if (SunGraphicsEnvironment.isOpenJDK()) {
            return;
        }
        /* Lucida Sans Family */
        jreFontMap.put("lucida sans0",   "LucidaSansRegular.ttf");
        jreFontMap.put("lucida sans1",   "LucidaSansDemiBold.ttf");
        /* Lucida Sans full names (map Bold and DemiBold to same file) */
        jreFontMap.put("lucida sans regular0", "LucidaSansRegular.ttf");
        jreFontMap.put("lucida sans regular1", "LucidaSansDemiBold.ttf");
        jreFontMap.put("lucida sans bold1", "LucidaSansDemiBold.ttf");
        jreFontMap.put("lucida sans demibold1", "LucidaSansDemiBold.ttf");

        /* Lucida Sans Typewriter Family */
        jreFontMap.put("lucida sans typewriter0",
                       "LucidaTypewriterRegular.ttf");
        jreFontMap.put("lucida sans typewriter1", "LucidaTypewriterBold.ttf");
        /* Typewriter full names (map Bold and DemiBold to same file) */
        jreFontMap.put("lucida sans typewriter regular0",
                       "LucidaTypewriter.ttf");
        jreFontMap.put("lucida sans typewriter regular1",
                       "LucidaTypewriterBold.ttf");
        jreFontMap.put("lucida sans typewriter bold1",
                       "LucidaTypewriterBold.ttf");
        jreFontMap.put("lucida sans typewriter demibold1",
                       "LucidaTypewriterBold.ttf");

        /* Lucida Bright Family */
        jreFontMap.put("lucida bright0", "LucidaBrightRegular.ttf");
        jreFontMap.put("lucida bright1", "LucidaBrightDemiBold.ttf");
        jreFontMap.put("lucida bright2", "LucidaBrightItalic.ttf");
        jreFontMap.put("lucida bright3", "LucidaBrightDemiItalic.ttf");
        /* Lucida Bright full names (map Bold and DemiBold to same file) */
        jreFontMap.put("lucida bright regular0", "LucidaBrightRegular.ttf");
        jreFontMap.put("lucida bright regular1", "LucidaBrightDemiBold.ttf");
        jreFontMap.put("lucida bright regular2", "LucidaBrightItalic.ttf");
        jreFontMap.put("lucida bright regular3", "LucidaBrightDemiItalic.ttf");
        jreFontMap.put("lucida bright bold1", "LucidaBrightDemiBold.ttf");
        jreFontMap.put("lucida bright bold3", "LucidaBrightDemiItalic.ttf");
        jreFontMap.put("lucida bright demibold1", "LucidaBrightDemiBold.ttf");
        jreFontMap.put("lucida bright demibold3","LucidaBrightDemiItalic.ttf");
        jreFontMap.put("lucida bright italic2", "LucidaBrightItalic.ttf");
        jreFontMap.put("lucida bright italic3", "LucidaBrightDemiItalic.ttf");
        jreFontMap.put("lucida bright bold italic3",
                       "LucidaBrightDemiItalic.ttf");
        jreFontMap.put("lucida bright demibold italic3",
                       "LucidaBrightDemiItalic.ttf");
        for (String ffile : jreFontMap.values()) {
            jreLucidaFontFiles.add(ffile);
        }
    }

    static {

        if (SunGraphicsEnvironment.debugFonts) {
            logger = Logger.getLogger("sun.java2d", null);
            logging = logger.getLevel() != Level.OFF;
        }
        initJREFontMap();

        java.security.AccessController.doPrivileged(
                                    new java.security.PrivilegedAction() {
           public Object run() {
               FontManagerNativeLibrary.load();

               // JNI throws an exception if a class/method/field is not found,
               // so there's no need to do anything explicit here.
               initIDs();

               switch (StrikeCache.nativeAddressSize) {
               case 8: longAddresses = true; break;
               case 4: longAddresses = false; break;
               default: throw new RuntimeException("Unexpected address size");
               }

               osName = System.getProperty("os.name", "unknownOS");
               isSolaris = osName.startsWith("SunOS");

               String t2kStr = System.getProperty("sun.java2d.font.scaler");
               if (t2kStr != null) {
                   useT2K = "t2k".equals(t2kStr);
               }
               if (isSolaris) {
                   String version = System.getProperty("os.version", "unk");
                   isSolaris8 = version.equals("5.8");
                   isSolaris9 = version.equals("5.9");
               } else {
                   isWindows = osName.startsWith("Windows");
                   if (isWindows) {
                       String eudcFile =
                           SunGraphicsEnvironment.eudcFontFileName;
                       if (eudcFile != null) {
                           try {
                               eudcFont = new TrueTypeFont(eudcFile, null, 0,
                                                           true);
                           } catch (FontFormatException e) {
                           }
                       }
                       String prop =
                           System.getProperty("java2d.font.usePlatformFont");
                       if (("true".equals(prop) || getPlatformFontVar())) {
                           usePlatformFontMetrics = true;
                           System.out.println("Enabling platform font metrics for win32. This is an unsupported option.");
                           System.out.println("This yields incorrect composite font metrics as reported by 1.1.x releases.");
                           System.out.println("It is appropriate only for use by applications which do not use any Java 2");
                           System.out.println("functionality. This property will be removed in a later release.");
                       }
                   }
               }
               return null;
           }
        });
    }

    /* Initialise ptrs used by JNI methods */
    private static native void initIDs();

    public static void addToPool(FileFont font) {

        FileFont fontFileToClose = null;
        int freeSlot = -1;

        synchronized (fontFileCache) {
            /* Avoid duplicate entries in the pool, and don't close() it,
             * since this method is called only from within open().
             * Seeing a duplicate is most likely to happen if the thread
             * was interrupted during a read, forcing perhaps repeated
             * close and open calls and it eventually it ends up pointing
             * at the same slot.
             */
            for (int i=0;i<CHANNELPOOLSIZE;i++) {
                if (fontFileCache[i] == font) {
                    return;
                }
                if (fontFileCache[i] == null && freeSlot < 0) {
                    freeSlot = i;
                }
            }
            if (freeSlot >= 0) {
                fontFileCache[freeSlot] = font;
                return;
            } else {
                /* replace with new font. */
                fontFileToClose = fontFileCache[lastPoolIndex];
                fontFileCache[lastPoolIndex] = font;
                /* lastPoolIndex is updated so that the least recently opened
                 * file will be closed next.
                 */
                lastPoolIndex = (lastPoolIndex+1) % CHANNELPOOLSIZE;
            }
        }
        /* Need to close the font file outside of the synchronized block,
         * since its possible some other thread is in an open() call on
         * this font file, and could be holding its lock and the pool lock.
         * Releasing the pool lock allows that thread to continue, so it can
         * then release the lock on this font, allowing the close() call
         * below to proceed.
         * Also, calling close() is safe because any other thread using
         * the font we are closing() synchronizes all reading, so we
         * will not close the file while its in use.
         */
        if (fontFileToClose != null) {
            fontFileToClose.close();
        }
    }

    /*
     * In the normal course of events, the pool of fonts can remain open
     * ready for quick access to their contents. The pool is sized so
     * that it is not an excessive consumer of system resources whilst
     * facilitating performance by providing ready access to the most
     * recently used set of font files.
     * The only reason to call removeFromPool(..) is for a Font that
     * you want to to have GC'd. Currently this would apply only to fonts
     * created with java.awt.Font.createFont(..).
     * In this case, the caller is expected to have arranged for the file
     * to be closed.
     * REMIND: consider how to know when a createFont created font should
     * be closed.
     */
    public static void removeFromPool(FileFont font) {
        synchronized (fontFileCache) {
            for (int i=0; i<CHANNELPOOLSIZE; i++) {
                if (fontFileCache[i] == font) {
                    fontFileCache[i] = null;
                }
            }
        }
    }

    /**
     * This method is provided for internal and exclusive use by Swing.
     *
     * @param font representing a physical font.
     * @return true if the underlying font is a TrueType or OpenType font
     * that claims to support the Microsoft Windows encoding corresponding to
     * the default file.encoding property of this JRE instance.
     * This narrow value is useful for Swing to decide if the font is useful
     * for the the Windows Look and Feel, or, if a  composite font should be
     * used instead.
     * The information used to make the decision is obtained from
     * the ulCodePageRange fields in the font.
     * A caller can use isLogicalFont(Font) in this class before calling
     * this method and would not need to call this method if that
     * returns true.
     */
//     static boolean fontSupportsDefaultEncoding(Font font) {
//      String encoding =
//          (String) java.security.AccessController.doPrivileged(
//                new sun.security.action.GetPropertyAction("file.encoding"));

//      if (encoding == null || font == null) {
//          return false;
//      }

//      encoding = encoding.toLowerCase(Locale.ENGLISH);

//      return FontManager.fontSupportsEncoding(font, encoding);
//     }

    /* Revise the implementation to in fact mean "font is a composite font.
     * This ensures that Swing components will always benefit from the
     * fall back fonts
     */
    public static boolean fontSupportsDefaultEncoding(Font font) {
        return getFont2D(font) instanceof CompositeFont;
    }

    /**
     * This method is provided for internal and exclusive use by Swing.
     *
     * It may be used in conjunction with fontSupportsDefaultEncoding(Font)
     * In the event that a desktop properties font doesn't directly
     * support the default encoding, (ie because the host OS supports
     * adding support for the current locale automatically for native apps),
     * then Swing calls this method to get a font which  uses the specified
     * font for the code points it covers, but also supports this locale
     * just as the standard composite fonts do.
     * Note: this will over-ride any setting where an application
     * specifies it prefers locale specific composite fonts.
     * The logic for this, is that this method is used only where the user or
     * application has specified that the native L&F be used, and that
     * we should honour that request to use the same font as native apps use.
     *
     * The behaviour of this method is to construct a new composite
     * Font object that uses the specified physical font as its first
     * component, and adds all the components of "dialog" as fall back
     * components.
     * The method currently assumes that only the size and style attributes
     * are set on the specified font. It doesn't copy the font transform or
     * other attributes because they aren't set on a font created from
     * the desktop. This will need to be fixed if use is broadened.
     *
     * Operations such as Font.deriveFont will work properly on the
     * font returned by this method for deriving a different point size.
     * Additionally it tries to support a different style by calling
     * getNewComposite() below. That also supports replacing slot zero
     * with a different physical font but that is expected to be "rare".
     * Deriving with a different style is needed because its been shown
     * that some applications try to do this for Swing FontUIResources.
     * Also operations such as new Font(font.getFontName(..), Font.PLAIN, 14);
     * will NOT yield the same result, as the new underlying CompositeFont
     * cannot be "looked up" in the font registry.
     * This returns a FontUIResource as that is the Font sub-class needed
     * by Swing.
     * Suggested usage is something like :
     * FontUIResource fuir;
     * Font desktopFont = getDesktopFont(..);
     * // NOTE even if fontSupportsDefaultEncoding returns true because
     * // you get Tahoma and are running in an English locale, you may
     * // still want to just call getCompositeFontUIResource() anyway
     * // as only then will you get fallback fonts - eg for CJK.
     * if (FontManager.fontSupportsDefaultEncoding(desktopFont)) {
     *   fuir = new FontUIResource(..);
     * } else {
     *   fuir = FontManager.getCompositeFontUIResource(desktopFont);
     * }
     * return fuir;
     */
    public static FontUIResource getCompositeFontUIResource(Font font) {

        FontUIResource fuir =
            new FontUIResource(font.getName(),font.getStyle(),font.getSize());
        Font2D font2D = getFont2D(font);

        if (!(font2D instanceof PhysicalFont)) {
            /* Swing should only be calling this when a font is obtained
             * from desktop properties, so should generally be a physical font,
             * an exception might be for names like "MS Serif" which are
             * automatically mapped to "Serif", so there's no need to do
             * anything special in that case. But note that suggested usage
             * is first to call fontSupportsDefaultEncoding(Font) and this
             * method should not be called if that were to return true.
             */
             return fuir;
        }

        CompositeFont dialog2D =
          (CompositeFont)findFont2D("dialog", font.getStyle(), NO_FALLBACK);
        if (dialog2D == null) { /* shouldn't happen */
            return fuir;
        }
        PhysicalFont physicalFont = (PhysicalFont)font2D;
        CompositeFont compFont = new CompositeFont(physicalFont, dialog2D);
        setFont2D(fuir, compFont.handle);
        /* marking this as a created font is needed as only created fonts
         * copy their creator's handles.
         */
        setCreatedFont(fuir);
        return fuir;
    }

    public static Font2DHandle getNewComposite(String family, int style,
                                               Font2DHandle handle) {

        if (!(handle.font2D instanceof CompositeFont)) {
            return handle;
        }

        CompositeFont oldComp = (CompositeFont)handle.font2D;
        PhysicalFont oldFont = oldComp.getSlotFont(0);

        if (family == null) {
            family = oldFont.getFamilyName(null);
        }
        if (style == -1) {
            style = oldComp.getStyle();
        }

        Font2D newFont = findFont2D(family, style, NO_FALLBACK);
        if (!(newFont instanceof PhysicalFont)) {
            newFont = oldFont;
        }
        PhysicalFont physicalFont = (PhysicalFont)newFont;
        CompositeFont dialog2D =
            (CompositeFont)findFont2D("dialog", style, NO_FALLBACK);
        if (dialog2D == null) { /* shouldn't happen */
            return handle;
        }
        CompositeFont compFont = new CompositeFont(physicalFont, dialog2D);
        Font2DHandle newHandle = new Font2DHandle(compFont);
        return newHandle;
    }

    public static native void setFont2D(Font font, Font2DHandle font2DHandle);

    private static native boolean isCreatedFont(Font font);
    private static native void setCreatedFont(Font font);

    public static void registerCompositeFont(String compositeName,
                                             String[] componentFileNames,
                                             String[] componentNames,
                                             int numMetricsSlots,
                                             int[] exclusionRanges,
                                             int[] exclusionMaxIndex,
                                             boolean defer) {

        CompositeFont cf = new CompositeFont(compositeName,
                                             componentFileNames,
                                             componentNames,
                                             numMetricsSlots,
                                             exclusionRanges,
                                             exclusionMaxIndex, defer);
        addCompositeToFontList(cf, Font2D.FONT_CONFIG_RANK);
        synchronized (compFonts) {
            compFonts[maxCompFont++] = cf;
        }
    }

    /* This variant is used only when the application specifies
     * a variant of composite fonts which prefers locale specific or
     * proportional fonts.
     */
    public static void registerCompositeFont(String compositeName,
                                             String[] componentFileNames,
                                             String[] componentNames,
                                             int numMetricsSlots,
                                             int[] exclusionRanges,
                                             int[] exclusionMaxIndex,
                                             boolean defer,
                                             ConcurrentHashMap<String, Font2D>
                                             altNameCache) {

        CompositeFont cf = new CompositeFont(compositeName,
                                             componentFileNames,
                                             componentNames,
                                             numMetricsSlots,
                                             exclusionRanges,
                                             exclusionMaxIndex, defer);
        /* if the cache has an existing composite for this case, make
         * its handle point to this new font.
         * This ensures that when the altNameCache that is passed in
         * is the global mapNameCache - ie we are running as an application -
         * that any statically created java.awt.Font instances which already
         * have a Font2D instance will have that re-directed to the new Font
         * on subsequent uses. This is particularly important for "the"
         * default font instance, or similar cases where a UI toolkit (eg
         * Swing) has cached a java.awt.Font. Note that if Swing is using
         * a custom composite APIs which update the standard composites have
         * no effect - this is typically the case only when using the Windows
         * L&F where these APIs would conflict with that L&F anyway.
         */
        Font2D oldFont = (Font2D)
            altNameCache.get(compositeName.toLowerCase(Locale.ENGLISH));
        if (oldFont instanceof CompositeFont) {
            oldFont.handle.font2D = cf;
        }
        altNameCache.put(compositeName.toLowerCase(Locale.ENGLISH), cf);
    }

    private static void addCompositeToFontList(CompositeFont f, int rank) {

        if (logging) {
            logger.info("Add to Family "+ f.familyName +
                        ", Font " + f.fullName + " rank="+rank);
        }
        f.setRank(rank);
        compositeFonts.put(f.fullName, f);
        fullNameToFont.put(f.fullName.toLowerCase(Locale.ENGLISH), f);

        FontFamily family = FontFamily.getFamily(f.familyName);
        if (family == null) {
            family = new FontFamily(f.familyName, true, rank);
        }
        family.setFont(f, f.style);
    }

    /*
     * Systems may have fonts with the same name.
     * We want to register only one of such fonts (at least until
     * such time as there might be APIs which can accommodate > 1).
     * Rank is 1) font configuration fonts, 2) JRE fonts, 3) OT/TT fonts,
     * 4) Type1 fonts, 5) native fonts.
     *
     * If the new font has the same name as the old font, the higher
     * ranked font gets added, replacing the lower ranked one.
     * If the fonts are of equal rank, then make a special case of
     * font configuration rank fonts, which are on closer inspection,
     * OT/TT fonts such that the larger font is registered. This is
     * a heuristic since a font may be "larger" in the sense of more
     * code points, or be a larger "file" because it has more bitmaps.
     * So it is possible that using filesize may lead to less glyphs, and
     * using glyphs may lead to lower quality display. Probably number
     * of glyphs is the ideal, but filesize is information we already
     * have and is good enough for the known cases.
     * Also don't want to register fonts that match JRE font families
     * but are coming from a source other than the JRE.
     * This will ensure that we will algorithmically style the JRE
     * plain font and get the same set of glyphs for all styles.
     *
     * Note that this method returns a value
     * if it returns the same object as its argument that means this
     * font was newly registered.
     * If it returns a different object it means this font already exists,
     * and you should use that one.
     * If it returns null means this font was not registered and none
     * in that name is registered. The caller must find a substitute
     */
    private static PhysicalFont addToFontList(PhysicalFont f, int rank) {

        String fontName = f.fullName;
        String familyName = f.familyName;
        if (fontName == null || "".equals(fontName)) {
            return null;
        }
        if (compositeFonts.containsKey(fontName)) {
            /* Don't register any font that has the same name as a composite */
            return null;
        }
        f.setRank(rank);
        if (!physicalFonts.containsKey(fontName)) {
            if (logging) {
                logger.info("Add to Family "+familyName +
                            ", Font " + fontName + " rank="+rank);
            }
            physicalFonts.put(fontName, f);
            FontFamily family = FontFamily.getFamily(familyName);
            if (family == null) {
                family = new FontFamily(familyName, false, rank);
                family.setFont(f, f.style);
            } else if (family.getRank() >= rank) {
                family.setFont(f, f.style);
            }
            fullNameToFont.put(fontName.toLowerCase(Locale.ENGLISH), f);
            return f;
        } else {
            PhysicalFont newFont = f;
            PhysicalFont oldFont = physicalFonts.get(fontName);
            if (oldFont == null) {
                return null;
            }
            /* If the new font is of an equal or higher rank, it is a
             * candidate to replace the current one, subject to further tests.
             */
            if (oldFont.getRank() >= rank) {

                /* All fonts initialise their mapper when first
                 * used. If the mapper is non-null then this font
                 * has been accessed at least once. In that case
                 * do not replace it. This may be overly stringent,
                 * but its probably better not to replace a font that
                 * someone is already using without a compelling reason.
                 * Additionally the primary case where it is known
                 * this behaviour is important is in certain composite
                 * fonts, and since all the components of a given
                 * composite are usually initialised together this
                 * is unlikely. For this to be a problem, there would
                 * have to be a case where two different composites used
                 * different versions of the same-named font, and they
                 * were initialised and used at separate times.
                 * In that case we continue on and allow the new font to
                 * be installed, but replaceFont will continue to allow
                 * the original font to be used in Composite fonts.
                 */
                if (oldFont.mapper != null && rank > Font2D.FONT_CONFIG_RANK) {
                    return oldFont;
                }

                /* Normally we require a higher rank to replace a font,
                 * but as a special case, if the two fonts are the same rank,
                 * and are instances of TrueTypeFont we want the
                 * more complete (larger) one.
                 */
                if (oldFont.getRank() == rank) {
                    if (oldFont instanceof TrueTypeFont &&
                        newFont instanceof TrueTypeFont) {
                        TrueTypeFont oldTTFont = (TrueTypeFont)oldFont;
                        TrueTypeFont newTTFont = (TrueTypeFont)newFont;
                        if (oldTTFont.fileSize >= newTTFont.fileSize) {
                            return oldFont;
                        }
                    } else {
                        return oldFont;
                    }
                }
                /* Don't replace ever JRE fonts.
                 * This test is in case a font configuration references
                 * a Lucida font, which has been mapped to a Lucida
                 * from the host O/S. The assumption here is that any
                 * such font configuration file is probably incorrect, or
                 * the host O/S version is for the use of AWT.
                 * In other words if we reach here, there's a possible
                 * problem with our choice of font configuration fonts.
                 */
                if (oldFont.platName.startsWith(
                           SunGraphicsEnvironment.jreFontDirName)) {
                    if (logging) {
                        logger.warning("Unexpected attempt to replace a JRE " +
                                       " font " + fontName + " from " +
                                        oldFont.platName +
                                       " with " + newFont.platName);
                    }
                    return oldFont;
                }

                if (logging) {
                    logger.info("Replace in Family " + familyName +
                                ",Font " + fontName + " new rank="+rank +
                                " from " + oldFont.platName +
                                " with " + newFont.platName);
                }
                replaceFont(oldFont, newFont);
                physicalFonts.put(fontName, newFont);
                fullNameToFont.put(fontName.toLowerCase(Locale.ENGLISH),
                                   newFont);

                FontFamily family = FontFamily.getFamily(familyName);
                if (family == null) {
                    family = new FontFamily(familyName, false, rank);
                    family.setFont(newFont, newFont.style);
                } else if (family.getRank() >= rank) {
                    family.setFont(newFont, newFont.style);
                }
                return newFont;
            } else {
                return oldFont;
            }
        }
    }

    public static Font2D[] getRegisteredFonts() {
        PhysicalFont[] physFonts = getPhysicalFonts();
        int mcf = maxCompFont; /* for MT-safety */
        Font2D[] regFonts = new Font2D[physFonts.length+mcf];
        System.arraycopy(compFonts, 0, regFonts, 0, mcf);
        System.arraycopy(physFonts, 0, regFonts, mcf, physFonts.length);
        return regFonts;
    }

    public static PhysicalFont[] getPhysicalFonts() {
        return physicalFonts.values().toArray(new PhysicalFont[0]);
    }


    /* The class FontRegistrationInfo is used when a client says not
     * to register a font immediately. This mechanism is used to defer
     * initialisation of all the components of composite fonts at JRE
     * start-up. The CompositeFont class is "aware" of this and when it
     * is first used it asks for the registration of its components.
     * Also in the event that any physical font is requested the
     * deferred fonts are initialised before triggering a search of the
     * system.
     * Two maps are used. One to track the deferred fonts. The
     * other to track the fonts that have been initialised through this
     * mechanism.
     */

    private static final class FontRegistrationInfo {

        String fontFilePath;
        String[] nativeNames;
        int fontFormat;
        boolean javaRasterizer;
        int fontRank;

        FontRegistrationInfo(String fontPath, String[] names, int format,
                             boolean useJavaRasterizer, int rank) {
            this.fontFilePath = fontPath;
            this.nativeNames = names;
            this.fontFormat = format;
            this.javaRasterizer = useJavaRasterizer;
            this.fontRank = rank;
        }
    }

    private static final ConcurrentHashMap<String, FontRegistrationInfo>
        deferredFontFiles =
        new ConcurrentHashMap<String, FontRegistrationInfo>();
    private static final ConcurrentHashMap<String, Font2DHandle>
        initialisedFonts = new ConcurrentHashMap<String, Font2DHandle>();

    /* Remind: possibly enhance initialiseDeferredFonts() to be
     * optionally given a name and a style and it could stop when it
     * finds that font - but this would be a problem if two of the
     * fonts reference the same font face name (cf the Solaris
     * euro fonts).
     */
    public static synchronized void initialiseDeferredFonts() {
        for (String fileName : deferredFontFiles.keySet()) {
            initialiseDeferredFont(fileName);
        }
    }

    public static synchronized void registerDeferredJREFonts(String jreDir) {
        for (FontRegistrationInfo info : deferredFontFiles.values()) {
            if (info.fontFilePath != null &&
                info.fontFilePath.startsWith(jreDir)) {
                initialiseDeferredFont(info.fontFilePath);
            }
        }
    }

    /* We keep a map of the files which contain the Lucida fonts so we
     * don't need to search for them.
     * But since we know what fonts these files contain, we can also avoid
     * opening them to look for a font name we don't recognise - see
     * findDeferredFont().
     * For typical cases where the font isn't a JRE one the overhead is
     * this method call, HashMap.get() and null reference test, then
     * a boolean test of noOtherJREFontFiles.
     */
    private static PhysicalFont findJREDeferredFont(String name, int style) {

        PhysicalFont physicalFont;
        String nameAndStyle = name.toLowerCase(Locale.ENGLISH) + style;
        String fileName = jreFontMap.get(nameAndStyle);
        if (fileName != null) {
            initSGEnv(); /* ensure jreFontDirName is initialised */
            fileName = SunGraphicsEnvironment.jreFontDirName +
                File.separator + fileName;
            if (deferredFontFiles.get(fileName) != null) {
                physicalFont = initialiseDeferredFont(fileName);
                if (physicalFont != null &&
                    (physicalFont.getFontName(null).equalsIgnoreCase(name) ||
                     physicalFont.getFamilyName(null).equalsIgnoreCase(name))
                    && physicalFont.style == style) {
                    return physicalFont;
                }
            }
        }

        /* Iterate over the deferred font files looking for any in the
         * jre directory that we didn't recognise, open each of these.
         * In almost all installations this will quickly fall through
         * because only the Lucidas will be present and jreOtherFontFiles
         * will be empty.
         * noOtherJREFontFiles is used so we can skip this block as soon
         * as its determined that its not needed - almost always after the
         * very first time through.
         */
        if (noOtherJREFontFiles) {
            return null;
        }
        synchronized (jreLucidaFontFiles) {
            if (jreOtherFontFiles == null) {
                HashSet<String> otherFontFiles = new HashSet<String>();
                for (String deferredFile : deferredFontFiles.keySet()) {
                    File file = new File(deferredFile);
                    String dir = file.getParent();
                    String fname = file.getName();
                    /* skip names which aren't absolute, aren't in the JRE
                     * directory, or are known Lucida fonts.
                     */
                    if (dir == null ||
                        !dir.equals(SunGraphicsEnvironment.jreFontDirName) ||
                        jreLucidaFontFiles.contains(fname)) {
                        continue;
                    }
                    otherFontFiles.add(deferredFile);
                }
                jreOtherFontFiles = otherFontFiles.toArray(STR_ARRAY);
                if (jreOtherFontFiles.length == 0) {
                    noOtherJREFontFiles = true;
                }
            }

            for (int i=0; i<jreOtherFontFiles.length;i++) {
                fileName = jreOtherFontFiles[i];
                if (fileName == null) {
                    continue;
                }
                jreOtherFontFiles[i] = null;
                physicalFont = initialiseDeferredFont(fileName);
                if (physicalFont != null &&
                    (physicalFont.getFontName(null).equalsIgnoreCase(name) ||
                     physicalFont.getFamilyName(null).equalsIgnoreCase(name))
                    && physicalFont.style == style) {
                    return physicalFont;
                }
            }
        }

        return null;
    }

    /* This skips JRE installed fonts. */
    private static PhysicalFont findOtherDeferredFont(String name, int style) {
        for (String fileName : deferredFontFiles.keySet()) {
            File file = new File(fileName);
            String dir = file.getParent();
            String fname = file.getName();
            if (dir != null &&
                dir.equals(SunGraphicsEnvironment.jreFontDirName) &&
                jreLucidaFontFiles.contains(fname)) {
                continue;
            }
            PhysicalFont physicalFont = initialiseDeferredFont(fileName);
            if (physicalFont != null &&
                (physicalFont.getFontName(null).equalsIgnoreCase(name) ||
                physicalFont.getFamilyName(null).equalsIgnoreCase(name)) &&
                physicalFont.style == style) {
                return physicalFont;
            }
        }
        return null;
    }

    private static PhysicalFont findDeferredFont(String name, int style) {

        PhysicalFont physicalFont = findJREDeferredFont(name, style);
        if (physicalFont != null) {
            return physicalFont;
        } else {
            return findOtherDeferredFont(name, style);
        }
    }

    public static void registerDeferredFont(String fileNameKey,
                                            String fullPathName,
                                            String[] nativeNames,
                                            int fontFormat,
                                            boolean useJavaRasterizer,
                                            int fontRank) {
        FontRegistrationInfo regInfo =
            new FontRegistrationInfo(fullPathName, nativeNames, fontFormat,
                                     useJavaRasterizer, fontRank);
        deferredFontFiles.put(fileNameKey, regInfo);
    }


    public static synchronized
         PhysicalFont initialiseDeferredFont(String fileNameKey) {

        if (fileNameKey == null) {
            return null;
        }
        if (logging) {
            logger.info("Opening deferred font file " + fileNameKey);
        }

        PhysicalFont physicalFont;
        FontRegistrationInfo regInfo = deferredFontFiles.get(fileNameKey);
        if (regInfo != null) {
            deferredFontFiles.remove(fileNameKey);
            physicalFont = registerFontFile(regInfo.fontFilePath,
                                            regInfo.nativeNames,
                                            regInfo.fontFormat,
                                            regInfo.javaRasterizer,
                                            regInfo.fontRank);


            if (physicalFont != null) {
                /* Store the handle, so that if a font is bad, we
                 * retrieve the substituted font.
                 */
                initialisedFonts.put(fileNameKey, physicalFont.handle);
            } else {
                initialisedFonts.put(fileNameKey,
                                     getDefaultPhysicalFont().handle);
            }
        } else {
            Font2DHandle handle = initialisedFonts.get(fileNameKey);
            if (handle == null) {
                /* Probably shouldn't happen, but just in case */
                physicalFont = getDefaultPhysicalFont();
            } else {
                physicalFont = (PhysicalFont)(handle.font2D);
            }
        }
        return physicalFont;
    }

    /* Note that the return value from this method is not always
     * derived from this file, and may be null. See addToFontList for
     * some explanation of this.
     */
    public static PhysicalFont registerFontFile(String fileName,
                                                String[] nativeNames,
                                                int fontFormat,
                                                boolean useJavaRasterizer,
                                                int fontRank) {

        PhysicalFont regFont = registeredFontFiles.get(fileName);
        if (regFont != null) {
            return regFont;
        }

        PhysicalFont physicalFont = null;
        try {
            String name;

            switch (fontFormat) {

            case FontManager.FONTFORMAT_TRUETYPE:
                int fn = 0;
                TrueTypeFont ttf;
                do {
                    ttf = new TrueTypeFont(fileName, nativeNames, fn++,
                                           useJavaRasterizer);
                    PhysicalFont pf = addToFontList(ttf, fontRank);
                    if (physicalFont == null) {
                        physicalFont = pf;
                    }
                }
                while (fn < ttf.getFontCount());
                break;

            case FontManager.FONTFORMAT_TYPE1:
                Type1Font t1f = new Type1Font(fileName, nativeNames);
                physicalFont = addToFontList(t1f, fontRank);
                break;

            case FontManager.FONTFORMAT_NATIVE:
                NativeFont nf = new NativeFont(fileName, false);
                physicalFont = addToFontList(nf, fontRank);
            default:

            }
            if (logging) {
                logger.info("Registered file " + fileName + " as font " +
                            physicalFont + " rank="  + fontRank);
            }
        } catch (FontFormatException ffe) {
            if (logging) {
                logger.warning("Unusable font: " +
                               fileName + " " + ffe.toString());
            }
        }
        if (physicalFont != null &&
            fontFormat != FontManager.FONTFORMAT_NATIVE) {
            registeredFontFiles.put(fileName, physicalFont);
        }
        return physicalFont;
    }

    public static void registerFonts(String[] fileNames,
                                     String[][] nativeNames,
                                     int fontCount,
                                     int fontFormat,
                                     boolean useJavaRasterizer,
                                     int fontRank, boolean defer) {

        for (int i=0; i < fontCount; i++) {
            if (defer) {
                registerDeferredFont(fileNames[i],fileNames[i], nativeNames[i],
                                     fontFormat, useJavaRasterizer, fontRank);
            } else {
                registerFontFile(fileNames[i], nativeNames[i],
                                 fontFormat, useJavaRasterizer, fontRank);
            }
        }
    }

    /*
     * This is the Physical font used when some other font on the system
     * can't be located. There has to be at least one font or the font
     * system is not useful and the graphics environment cannot sustain
     * the Java platform.
     */
    public static PhysicalFont getDefaultPhysicalFont() {
        if (defaultPhysicalFont == null) {
            /* findFont2D will load all fonts before giving up the search.
             * If the JRE Lucida isn't found (eg because the JRE fonts
             * directory is missing), it could find another version of Lucida
             * from the host system. This is OK because at that point we are
             * trying to gracefully handle/recover from a system
             * misconfiguration and this is probably a reasonable substitution.
             */
            defaultPhysicalFont = (PhysicalFont)
                findFont2D("Lucida Sans Regular", Font.PLAIN, NO_FALLBACK);
            if (defaultPhysicalFont == null) {
                defaultPhysicalFont = (PhysicalFont)
                    findFont2D("Arial", Font.PLAIN, NO_FALLBACK);
            }
            if (defaultPhysicalFont == null) {
                /* Because of the findFont2D call above, if we reach here, we
                 * know all fonts have already been loaded, just accept any
                 * match at this point. If this fails we are in real trouble
                 * and I don't know how to recover from there being absolutely
                 * no fonts anywhere on the system.
                 */
                Iterator i = physicalFonts.values().iterator();
                if (i.hasNext()) {
                    defaultPhysicalFont = (PhysicalFont)i.next();
                } else {
                    throw new Error("Probable fatal error:No fonts found.");
                }
            }
        }
        return defaultPhysicalFont;
    }

    public static CompositeFont getDefaultLogicalFont(int style) {
        return (CompositeFont)findFont2D("dialog", style, NO_FALLBACK);
    }

    /*
     * return String representation of style prepended with "."
     * This is useful for performance to avoid unnecessary string operations.
     */
    private static String dotStyleStr(int num) {
        switch(num){
          case Font.BOLD:
            return ".bold";
          case Font.ITALIC:
            return ".italic";
          case Font.ITALIC | Font.BOLD:
            return ".bolditalic";
          default:
            return ".plain";
        }
    }

    static void initSGEnv() {
        if (sgEnv == null) {
            GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
            if (ge instanceof HeadlessGraphicsEnvironment) {
                HeadlessGraphicsEnvironment hgEnv =
                    (HeadlessGraphicsEnvironment)ge;
                sgEnv = (SunGraphicsEnvironment)
                    hgEnv.getSunGraphicsEnvironment();
            } else {
                sgEnv = (SunGraphicsEnvironment)ge;
            }
        }
    }

    /* This is implemented only on windows and is called from code that
     * executes only on windows. This isn't pretty but its not a precedent
     * in this file. This very probably should be cleaned up at some point.
     */
    private static native void
        populateFontFileNameMap(HashMap<String,String> fontToFileMap,
                                HashMap<String,String> fontToFamilyNameMap,
                                HashMap<String,ArrayList<String>>
                                familyToFontListMap,
                                Locale locale);

    /* Obtained from Platform APIs (windows only)
     * Map from lower-case font full name to basename of font file.
     * Eg "arial bold" -> ARIALBD.TTF.
     * For TTC files, there is a mapping for each font in the file.
     */
    private static HashMap<String,String> fontToFileMap = null;

    /* Obtained from Platform APIs (windows only)
     * Map from lower-case font full name to the name of its font family
     * Eg "arial bold" -> "Arial"
     */
    private static HashMap<String,String> fontToFamilyNameMap = null;

    /* Obtained from Platform APIs (windows only)
     * Map from a lower-case family name to a list of full names of
     * the member fonts, eg:
     * "arial" -> ["Arial", "Arial Bold", "Arial Italic","Arial Bold Italic"]
     */
    private static HashMap<String,ArrayList<String>> familyToFontListMap= null;

    /* The directories which contain platform fonts */
    private static String[] pathDirs = null;

    private static boolean haveCheckedUnreferencedFontFiles;

    private static String[] getFontFilesFromPath(boolean noType1) {
        final FilenameFilter filter;
        if (noType1) {
            filter = SunGraphicsEnvironment.ttFilter;
        } else {
            filter = new SunGraphicsEnvironment.TTorT1Filter();
        }
        return (String[])AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                if (pathDirs.length == 1) {
                    File dir = new File(pathDirs[0]);
                    String[] files = dir.list(filter);
                    if (files == null) {
                        return new String[0];
                    }
                    for (int f=0; f<files.length; f++) {
                        files[f] = files[f].toLowerCase();
                    }
                    return files;
                } else {
                    ArrayList<String> fileList = new ArrayList<String>();
                    for (int i = 0; i< pathDirs.length; i++) {
                        File dir = new File(pathDirs[i]);
                        String[] files = dir.list(filter);
                        if (files == null) {
                            continue;
                        }
                        for (int f=0; f<files.length ; f++) {
                            fileList.add(files[f].toLowerCase());
                        }
                    }
                    return fileList.toArray(STR_ARRAY);
                }
            }
        });
    }

    /* This is needed since some windows registry names don't match
     * the font names.
     * - UPC styled font names have a double space, but the
     * registry entry mapping to a file doesn't.
     * - Marlett is in a hidden file not listed in the registry
     * - The registry advertises that the file david.ttf contains a
     * font with the full name "David Regular" when in fact its
     * just "David".
     * Directly fix up these known cases as this is faster.
     * If a font which doesn't match these known cases has no file,
     * it may be a font that has been temporarily added to the known set
     * or it may be an installed font with a missing registry entry.
     * Installed fonts are those in the windows font directories.
     * Make a best effort attempt to locate these.
     * We obtain the list of TrueType fonts in these directories and
     * filter out all the font files we already know about from the registry.
     * What remains may be "bad" fonts, duplicate fonts, or perhaps the
     * missing font(s) we are looking for.
     * Open each of these files to find out.
     */
    private static void resolveWindowsFonts() {

        ArrayList<String> unmappedFontNames = null;
        for (String font : fontToFamilyNameMap.keySet()) {
            String file = fontToFileMap.get(font);
            if (file == null) {
                if (font.indexOf("  ") > 0) {
                    String newName = font.replaceFirst("  ", " ");
                    file = fontToFileMap.get(newName);
                    /* If this name exists and isn't for a valid name
                     * replace the mapping to the file with this font
                     */
                    if (file != null &&
                        !fontToFamilyNameMap.containsKey(newName)) {
                        fontToFileMap.remove(newName);
                        fontToFileMap.put(font, file);
                    }
                } else if (font.equals("marlett")) {
                    fontToFileMap.put(font, "marlett.ttf");
                } else if (font.equals("david")) {
                    file = fontToFileMap.get("david regular");
                    if (file != null) {
                        fontToFileMap.remove("david regular");
                        fontToFileMap.put("david", file);
                    }
                } else {
                    if (unmappedFontNames == null) {
                        unmappedFontNames = new ArrayList<String>();
                    }
                    unmappedFontNames.add(font);
                }
            }
        }

        if (unmappedFontNames != null) {
            HashSet<String> unmappedFontFiles = new HashSet<String>();

            /* Every font key in fontToFileMap ought to correspond to a
             * font key in fontToFamilyNameMap. Entries that don't seem
             * to correspond are likely fonts that were named differently
             * by GDI than in the registry. One known cause of this is when
             * Windows has had its regional settings changed so that from
             * GDI we get a localised (eg Chinese or Japanese) name for the
             * font, but the registry retains the English version of the name
             * that corresponded to the "install" locale for windows.
             * Since we are in this code block because there are unmapped
             * font names, we can look to find unused font->file mappings
             * and then open the files to read the names. We don't generally
             * want to open font files, as its a performance hit, but this
             * occurs only for a small number of fonts on specific system
             * configs - ie is believed that a "true" Japanese windows would
             * have JA names in the registry too.
             * Clone fontToFileMap and remove from the clone all keys which
             * match a fontToFamilyNameMap key. What remains maps to the
             * files we want to open to find the fonts GDI returned.
             * A font in such a file is added to the fontToFileMap after
             * checking its one of the unmappedFontNames we are looking for.
             * The original name that didn't map is removed from fontToFileMap
             * so essentially this "fixes up" fontToFileMap to use the same
             * name as GDI.
             * Also note that typically the fonts for which this occurs in
             * CJK locales are TTC fonts and not all fonts in a TTC may have
             * localised names. Eg MSGOTHIC.TTC contains 3 fonts and one of
             * them "MS UI Gothic" has no JA name whereas the other two do.
             * So not every font in these files is unmapped or new.
             */
            HashMap<String,String> ffmapCopy =
                (HashMap<String,String>)(fontToFileMap.clone());
            for (String key : fontToFamilyNameMap.keySet()) {
                ffmapCopy.remove(key);
            }
            for (String key : ffmapCopy.keySet()) {
                unmappedFontFiles.add(ffmapCopy.get(key));
                fontToFileMap.remove(key);
            }

            resolveFontFiles(unmappedFontFiles, unmappedFontNames);

            /* If there are still unmapped font names, this means there's
             * something that wasn't in the registry. We need to get all
             * the font files directly and look at the ones that weren't
             * found in the registry.
             */
            if (unmappedFontNames.size() > 0) {

                /* getFontFilesFromPath() returns all lower case names.
                 * To compare we also need lower case
                 * versions of the names from the registry.
                 */
                ArrayList<String> registryFiles = new ArrayList<String>();

                for (String regFile : fontToFileMap.values()) {
                    registryFiles.add(regFile.toLowerCase());
                }
                /* We don't look for Type1 files here as windows will
                 * not enumerate these, so aren't useful in reconciling
                 * GDI's unmapped files. We do find these later when
                 * we enumerate all fonts.
                 */
                for (String pathFile : getFontFilesFromPath(true)) {
                    if (!registryFiles.contains(pathFile)) {
                        unmappedFontFiles.add(pathFile);
                    }
                }

                resolveFontFiles(unmappedFontFiles, unmappedFontNames);
            }

            /* remove from the set of names that will be returned to the
             * user any fonts that can't be mapped to files.
             */
            if (unmappedFontNames.size() > 0) {
                int sz = unmappedFontNames.size();
                for (int i=0; i<sz; i++) {
                    String name = unmappedFontNames.get(i);
                    String familyName = fontToFamilyNameMap.get(name);
                    if (familyName != null) {
                        ArrayList family = familyToFontListMap.get(familyName);
                        if (family != null) {
                            if (family.size() <= 1) {
                                familyToFontListMap.remove(familyName);
                            }
                        }
                    }
                    fontToFamilyNameMap.remove(name);
                    if (logging) {
                        logger.info("No file for font:" + name);
                    }
                }
            }
        }
    }

    /**
     * In some cases windows may have fonts in the fonts folder that
     * don't show up in the registry or in the GDI calls to enumerate fonts.
     * The only way to find these is to list the directory. We invoke this
     * only in getAllFonts/Families, so most searches for a specific
     * font that is satisfied by the GDI/registry calls don't take the
     * additional hit of listing the directory. This hit is small enough
     * that its not significant in these 'enumerate all the fonts' cases.
     * The basic approach is to cross-reference the files windows found
     * with the ones in the directory listing approach, and for each
     * in the latter list that is missing from the former list, register it.
     */
    private static synchronized void checkForUnreferencedFontFiles() {
        if (haveCheckedUnreferencedFontFiles) {
            return;
        }
        haveCheckedUnreferencedFontFiles = true;
        if (!isWindows) {
            return;
        }
        /* getFontFilesFromPath() returns all lower case names.
         * To compare we also need lower case
         * versions of the names from the registry.
         */
        ArrayList<String> registryFiles = new ArrayList<String>();
        for (String regFile : fontToFileMap.values()) {
            registryFiles.add(regFile.toLowerCase());
        }

        /* To avoid any issues with concurrent modification, create
         * copies of the existing maps, add the new fonts into these
         * and then replace the references to the old ones with the
         * new maps. ConcurrentHashmap is another option but its a lot
         * more changes and with this exception, these maps are intended
         * to be static.
         */
        HashMap<String,String> fontToFileMap2 = null;
        HashMap<String,String> fontToFamilyNameMap2 = null;
        HashMap<String,ArrayList<String>> familyToFontListMap2 = null;;

        for (String pathFile : getFontFilesFromPath(false)) {
            if (!registryFiles.contains(pathFile)) {
                if (logging) {
                    logger.info("Found non-registry file : " + pathFile);
                }
                PhysicalFont f = registerFontFile(getPathName(pathFile));
                if (f == null) {
                    continue;
                }
                if (fontToFileMap2 == null) {
                    fontToFileMap2 = new HashMap<String,String>(fontToFileMap);
                    fontToFamilyNameMap2 =
                        new HashMap<String,String>(fontToFamilyNameMap);
                    familyToFontListMap2 = new
                        HashMap<String,ArrayList<String>>(familyToFontListMap);
                }
                String fontName = f.getFontName(null);
                String family = f.getFamilyName(null);
                String familyLC = family.toLowerCase();
                fontToFamilyNameMap2.put(fontName, family);
                fontToFileMap2.put(fontName, pathFile);
                ArrayList<String> fonts = familyToFontListMap2.get(familyLC);
                if (fonts == null) {
                    fonts = new ArrayList<String>();
                } else {
                    fonts = new ArrayList<String>(fonts);
                }
                fonts.add(fontName);
                familyToFontListMap2.put(familyLC, fonts);
            }
        }
        if (fontToFileMap2 != null) {
            fontToFileMap = fontToFileMap2;
            familyToFontListMap = familyToFontListMap2;
            fontToFamilyNameMap = fontToFamilyNameMap2;
        }
    }

    private static void resolveFontFiles(HashSet<String> unmappedFiles,
                                         ArrayList<String> unmappedFonts) {

        Locale l = SunToolkit.getStartupLocale();

        for (String file : unmappedFiles) {
            try {
                int fn = 0;
                TrueTypeFont ttf;
                String fullPath = getPathName(file);
                if (logging) {
                    logger.info("Trying to resolve file " + fullPath);
                }
                do {
                    ttf = new TrueTypeFont(fullPath, null, fn++, true);
                    //  prefer the font's locale name.
                    String fontName = ttf.getFontName(l).toLowerCase();
                    if (unmappedFonts.contains(fontName)) {
                        fontToFileMap.put(fontName, file);
                        unmappedFonts.remove(fontName);
                        if (logging) {
                            logger.info("Resolved absent registry entry for " +
                                        fontName + " located in " + fullPath);
                        }
                    }
                }
                while (fn < ttf.getFontCount());
            } catch (Exception e) {
            }
        }
    }

    private static synchronized HashMap<String,String> getFullNameToFileMap() {
        if (fontToFileMap == null) {

            initSGEnv();
            pathDirs = sgEnv.getPlatformFontDirs();

            fontToFileMap = new HashMap<String,String>(100);
            fontToFamilyNameMap = new HashMap<String,String>(100);
            familyToFontListMap = new HashMap<String,ArrayList<String>>(50);
            populateFontFileNameMap(fontToFileMap,
                                    fontToFamilyNameMap,
                                    familyToFontListMap,
                                    Locale.ENGLISH);
            if (isWindows) {
                resolveWindowsFonts();
            }
            if (logging) {
                logPlatformFontInfo();
            }
        }
        return fontToFileMap;
    }

    private static void logPlatformFontInfo() {
        for (int i=0; i< pathDirs.length;i++) {
            logger.info("fontdir="+pathDirs[i]);
        }
        for (String keyName : fontToFileMap.keySet()) {
            logger.info("font="+keyName+" file="+ fontToFileMap.get(keyName));
        }
        for (String keyName : fontToFamilyNameMap.keySet()) {
            logger.info("font="+keyName+" family="+
                        fontToFamilyNameMap.get(keyName));
        }
        for (String keyName : familyToFontListMap.keySet()) {
            logger.info("family="+keyName+ " fonts="+
                        familyToFontListMap.get(keyName));
        }
    }

    /* Note this return list excludes logical fonts and JRE fonts */
    public static String[] getFontNamesFromPlatform() {
        if (getFullNameToFileMap().size() == 0) {
            return null;
        }
        checkForUnreferencedFontFiles();
        /* This odd code with TreeMap is used to preserve a historical
         * behaviour wrt the sorting order .. */
        ArrayList<String> fontNames = new ArrayList<String>();
        for (ArrayList<String> a : familyToFontListMap.values()) {
            for (String s : a) {
                fontNames.add(s);
            }
        }
        return fontNames.toArray(STR_ARRAY);
    }

    public static boolean gotFontsFromPlatform() {
        return getFullNameToFileMap().size() != 0;
    }

    public static String getFileNameForFontName(String fontName) {
        String fontNameLC = fontName.toLowerCase(Locale.ENGLISH);
        return fontToFileMap.get(fontNameLC);
    }

    private static PhysicalFont registerFontFile(String file) {
        if (new File(file).isAbsolute() &&
            !registeredFontFiles.contains(file)) {
            int fontFormat = FONTFORMAT_NONE;
            int fontRank = Font2D.UNKNOWN_RANK;
            if (SunGraphicsEnvironment.ttFilter.accept(null, file)) {
                fontFormat = FONTFORMAT_TRUETYPE;
                fontRank = Font2D.TTF_RANK;
            } else if
                (SunGraphicsEnvironment.t1Filter.accept(null, file)) {
                fontFormat = FONTFORMAT_TYPE1;
                fontRank = Font2D.TYPE1_RANK;
            }
            if (fontFormat == FONTFORMAT_NONE) {
                return null;
            }
            return registerFontFile(file, null, fontFormat, false, fontRank);
        }
        return null;
    }

    /* Used to register any font files that are found by platform APIs
     * that weren't previously found in the standard font locations.
     * the isAbsolute() check is needed since that's whats stored in the
     * set, and on windows, the fonts in the system font directory that
     * are in the fontToFileMap are just basenames. We don't want to try
     * to register those again, but we do want to register other registry
     * installed fonts.
     */
    public static void registerOtherFontFiles(HashSet registeredFontFiles) {
        if (getFullNameToFileMap().size() == 0) {
            return;
        }
        for (String file : fontToFileMap.values()) {
            registerFontFile(file);
        }
    }

    public static boolean
        getFamilyNamesFromPlatform(TreeMap<String,String> familyNames,
                                   Locale requestedLocale) {
        if (getFullNameToFileMap().size() == 0) {
            return false;
        }
        checkForUnreferencedFontFiles();
        for (String name : fontToFamilyNameMap.values()) {
            familyNames.put(name.toLowerCase(requestedLocale), name);
        }
        return true;
    }

    /* Path may be absolute or a base file name relative to one of
     * the platform font directories
     */
    private static String getPathName(final String s) {
        File f = new File(s);
        if (f.isAbsolute()) {
            return s;
        } else if (pathDirs.length==1) {
            return pathDirs[0] + File.separator + s;
        } else {
            String path = java.security.AccessController.doPrivileged(
                 new java.security.PrivilegedAction<String>() {
                     public String run() {
                         for (int p=0; p<pathDirs.length; p++) {
                             File f = new File(pathDirs[p] +File.separator+ s);
                             if (f.exists()) {
                                 return f.getAbsolutePath();
                             }
                         }
                         return null;
                     }
                });
            if (path != null) {
                return path;
            }
        }
        return s; // shouldn't happen, but harmless
    }

    /* lcName is required to be lower case for use as a key.
     * lcName may be a full name, or a family name, and style may
     * be specified in addition to either of these. So be sure to
     * get the right one. Since an app *could* ask for "Foo Regular"
     * and later ask for "Foo Italic", if we don't register all the
     * styles, then logic in findFont2D may try to style the original
     * so we register the entire family if we get a match here.
     * This is still a big win because this code is invoked where
     * otherwise we would register all fonts.
     * It's also useful for the case where "Foo Bold" was specified with
     * style Font.ITALIC, as we would want in that case to try to return
     * "Foo Bold Italic" if it exists, and it is only by locating "Foo Bold"
     * and opening it that we really "know" it's Bold, and can look for
     * a font that supports that and the italic style.
     * The code in here is not overtly windows-specific but in fact it
     * is unlikely to be useful as is on other platforms. It is maintained
     * in this shared source file to be close to its sole client and
     * because so much of the logic is intertwined with the logic in
     * findFont2D.
     */
    private static Font2D findFontFromPlatform(String lcName, int style) {
        if (getFullNameToFileMap().size() == 0) {
            return null;
        }

        ArrayList<String> family = null;
        String fontFile = null;
        String familyName = fontToFamilyNameMap.get(lcName);
        if (familyName != null) {
            fontFile = fontToFileMap.get(lcName);
            family = familyToFontListMap.get
                (familyName.toLowerCase(Locale.ENGLISH));
        } else {
            family = familyToFontListMap.get(lcName); // is lcName is a family?
            if (family != null && family.size() > 0) {
                String lcFontName = family.get(0).toLowerCase(Locale.ENGLISH);
                if (lcFontName != null) {
                    familyName = fontToFamilyNameMap.get(lcFontName);
                }
            }
        }
        if (family == null || familyName == null) {
            return null;
        }
        String [] fontList = (String[])family.toArray(STR_ARRAY);
        if (fontList.length == 0) {
            return null;
        }

        /* first check that for every font in this family we can find
         * a font file. The specific reason for doing this is that
         * in at least one case on Windows a font has the face name "David"
         * but the registry entry is "David Regular". That is the "unique"
         * name of the font but in other cases the registry contains the
         * "full" name. See the specifications of name ids 3 and 4 in the
         * TrueType 'name' table.
         * In general this could cause a problem that we fail to register
         * if we all members of a family that we may end up mapping to
         * the wrong font member: eg return Bold when Plain is needed.
         */
        for (int f=0;f<fontList.length;f++) {
            String fontNameLC = fontList[f].toLowerCase(Locale.ENGLISH);
            String fileName = fontToFileMap.get(fontNameLC);
            if (fileName == null) {
                if (logging) {
                    logger.info("Platform lookup : No file for font " +
                                fontList[f] + " in family " +familyName);
                }
                return null;
            }
        }

        /* Currently this code only looks for TrueType fonts, so format
         * and rank can be specified without looking at the filename.
         */
        PhysicalFont physicalFont = null;
        if (fontFile != null) {
            physicalFont = registerFontFile(getPathName(fontFile), null,
                                            FONTFORMAT_TRUETYPE, false,
                                            Font2D.TTF_RANK);
        }
        /* Register all fonts in this family. */
        for (int f=0;f<fontList.length;f++) {
            String fontNameLC = fontList[f].toLowerCase(Locale.ENGLISH);
            String fileName = fontToFileMap.get(fontNameLC);
            if (fontFile != null && fontFile.equals(fileName)) {
                continue;
            }
            /* Currently this code only looks for TrueType fonts, so format
             * and rank can be specified without looking at the filename.
             */
            registerFontFile(getPathName(fileName), null,
                             FONTFORMAT_TRUETYPE, false, Font2D.TTF_RANK);
        }

        Font2D font = null;
        FontFamily fontFamily = FontFamily.getFamily(familyName);
        /* Handle case where request "MyFont Bold", style=Font.ITALIC */
        if (physicalFont != null) {
            style |= physicalFont.style;
        }
        if (fontFamily != null) {
            font = fontFamily.getFont(style);
            if (font == null) {
                font = fontFamily.getClosestStyle(style);
            }
        }
        return font;
    }

    private static ConcurrentHashMap<String, Font2D> fontNameCache =
        new ConcurrentHashMap<String, Font2D>();

    /*
     * The client supplies a name and a style.
     * The name could be a family name, or a full name.
     * A font may exist with the specified style, or it may
     * exist only in some other style. For non-native fonts the scaler
     * may be able to emulate the required style.
     */
    public static Font2D findFont2D(String name, int style, int fallback) {
        String lowerCaseName = name.toLowerCase(Locale.ENGLISH);
        String mapName = lowerCaseName + dotStyleStr(style);
        Font2D font;

        /* If preferLocaleFonts() or preferProportionalFonts() has been
         * called we may be using an alternate set of composite fonts in this
         * app context. The presence of a pre-built name map indicates whether
         * this is so, and gives access to the alternate composite for the
         * name.
         */
        if (usingPerAppContextComposites) {
            ConcurrentHashMap<String, Font2D> altNameCache =
                (ConcurrentHashMap<String, Font2D>)
                AppContext.getAppContext().get(CompositeFont.class);
            if (altNameCache != null) {
                font = (Font2D)altNameCache.get(mapName);
            } else {
                font = null;
            }
        } else {
            font = fontNameCache.get(mapName);
        }
        if (font != null) {
            return font;
        }

        if (logging) {
            logger.info("Search for font: " + name);
        }

        // The check below is just so that the bitmap fonts being set by
        // AWT and Swing thru the desktop properties do not trigger the
        // the load fonts case. The two bitmap fonts are now mapped to
        // appropriate equivalents for serif and sansserif.
        // Note that the cost of this comparison is only for the first
        // call until the map is filled.
        if (isWindows) {
            if (lowerCaseName.equals("ms sans serif")) {
                name = "sansserif";
            } else if (lowerCaseName.equals("ms serif")) {
                name = "serif";
            }
        }

        /* This isn't intended to support a client passing in the
         * string default, but if a client passes in null for the name
         * the java.awt.Font class internally substitutes this name.
         * So we need to recognise it here to prevent a loadFonts
         * on the unrecognised name. The only potential problem with
         * this is it would hide any real font called "default"!
         * But that seems like a potential problem we can ignore for now.
         */
        if (lowerCaseName.equals("default")) {
            name = "dialog";
        }

        /* First see if its a family name. */
        FontFamily family = FontFamily.getFamily(name);
        if (family != null) {
            font = family.getFontWithExactStyleMatch(style);
            if (font == null) {
                font = findDeferredFont(name, style);
            }
            if (font == null) {
                font = family.getFont(style);
            }
            if (font == null) {
                font = family.getClosestStyle(style);
            }
            if (font != null) {
                fontNameCache.put(mapName, font);
                return font;
            }
        }

        /* If it wasn't a family name, it should be a full name of
         * either a composite, or a physical font
         */
        font = fullNameToFont.get(lowerCaseName);
        if (font != null) {
            /* Check that the requested style matches the matched font's style.
             * But also match style automatically if the requested style is
             * "plain". This because the existing behaviour is that the fonts
             * listed via getAllFonts etc always list their style as PLAIN.
             * This does lead to non-commutative behaviours where you might
             * start with "Lucida Sans Regular" and ask for a BOLD version
             * and get "Lucida Sans DemiBold" but if you ask for the PLAIN
             * style of "Lucida Sans DemiBold" you get "Lucida Sans DemiBold".
             * This consistent however with what happens if you have a bold
             * version of a font and no plain version exists - alg. styling
             * doesn't "unbolden" the font.
             */
            if (font.style == style || style == Font.PLAIN) {
                fontNameCache.put(mapName, font);
                return font;
            } else {
                /* If it was a full name like "Lucida Sans Regular", but
                 * the style requested is "bold", then we want to see if
                 * there's the appropriate match against another font in
                 * that family before trying to load all fonts, or applying a
                 * algorithmic styling
                 */
                family = FontFamily.getFamily(font.getFamilyName(null));
                if (family != null) {
                    Font2D familyFont = family.getFont(style|font.style);
                    /* We exactly matched the requested style, use it! */
                    if (familyFont != null) {
                        fontNameCache.put(mapName, familyFont);
                        return familyFont;
                    } else {
                        /* This next call is designed to support the case
                         * where bold italic is requested, and if we must
                         * style, then base it on either bold or italic -
                         * not on plain!
                         */
                        familyFont = family.getClosestStyle(style|font.style);
                        if (familyFont != null) {
                            /* The next check is perhaps one
                             * that shouldn't be done. ie if we get this
                             * far we have probably as close a match as we
                             * are going to get. We could load all fonts to
                             * see if somehow some parts of the family are
                             * loaded but not all of it.
                             */
                            if (familyFont.canDoStyle(style|font.style)) {
                                fontNameCache.put(mapName, familyFont);
                                return familyFont;
                            }
                        }
                    }
                }
            }
        }

        /* If reach here its possible that this is in a client which never
         * loaded the GraphicsEnvironment, so we haven't even loaded ANY of
         * the fonts from the environment. Do so now and recurse.
         */
        if (sgEnv == null) {
            initSGEnv();
            return findFont2D(name, style, fallback);
        }

        if (isWindows) {
            /* Don't want Windows to return a Lucida Sans font from
             * C:\Windows\Fonts
             */
            if (deferredFontFiles.size() > 0) {
                font = findJREDeferredFont(lowerCaseName, style);
                if (font != null) {
                    fontNameCache.put(mapName, font);
                    return font;
                }
            }
            font = findFontFromPlatform(lowerCaseName, style);
            if (font != null) {
                if (logging) {
                    logger.info("Found font via platform API for request:\"" +
                                name + "\":, style="+style+
                                " found font: " + font);
                }
                fontNameCache.put(mapName, font);
                return font;
            }
        }

        /* If reach here and no match has been located, then if there are
         * uninitialised deferred fonts, load as many of those as needed
         * to find the deferred font. If none is found through that
         * search continue on.
         * There is possibly a minor issue when more than one
         * deferred font implements the same font face. Since deferred
         * fonts are only those in font configuration files, this is a
         * controlled situation, the known case being Solaris euro_fonts
         * versions of Arial, Times New Roman, Courier New. However
         * the larger font will transparently replace the smaller one
         *  - see addToFontList() - when it is needed by the composite font.
         */
        if (deferredFontFiles.size() > 0) {
            font = findDeferredFont(name, style);
            if (font != null) {
                fontNameCache.put(mapName, font);
                return font;
            }
        }

        /* Some apps use deprecated 1.0 names such as helvetica and courier. On
         * Solaris these are Type1 fonts in /usr/openwin/lib/X11/fonts/Type1.
         * If running on Solaris will register all the fonts in this
         * directory.
         * May as well register the whole directory without actually testing
         * the font name is one of the deprecated names as the next step would
         * load all fonts which are in this directory anyway.
         * In the event that this lookup is successful it potentially "hides"
         * TrueType versions of such fonts that are elsewhere but since they
         * do not exist on Solaris this is not a problem.
         * Set a flag to indicate we've done this registration to avoid
         * repetition and more seriously, to avoid recursion.
         */
        if (isSolaris&&!loaded1dot0Fonts) {
            /* "timesroman" is a special case since that's not the
             * name of any known font on Solaris or elsewhere.
             */
            if (lowerCaseName.equals("timesroman")) {
                font = findFont2D("serif", style, fallback);
                fontNameCache.put(mapName, font);
            }
            sgEnv.register1dot0Fonts();
            loaded1dot0Fonts = true;
            Font2D ff = findFont2D(name, style, fallback);
            return ff;
        }

        /* We check for application registered fonts before
         * explicitly loading all fonts as if necessary the registration
         * code will have done so anyway. And we don't want to needlessly
         * load the actual files for all fonts.
         * Just as for installed fonts we check for family before fullname.
         * We do not add these fonts to fontNameCache for the
         * app context case which eliminates the overhead of a per context
         * cache for these.
         */

        if (fontsAreRegistered || fontsAreRegisteredPerAppContext) {
            Hashtable<String, FontFamily> familyTable = null;
            Hashtable<String, Font2D> nameTable;

            if (fontsAreRegistered) {
                familyTable = createdByFamilyName;
                nameTable = createdByFullName;
            } else {
                AppContext appContext = AppContext.getAppContext();
                familyTable =
                    (Hashtable<String,FontFamily>)appContext.get(regFamilyKey);
                nameTable =
                    (Hashtable<String,Font2D>)appContext.get(regFullNameKey);
            }

            family = familyTable.get(lowerCaseName);
            if (family != null) {
                font = family.getFontWithExactStyleMatch(style);
                if (font == null) {
                    font = family.getFont(style);
                }
                if (font == null) {
                    font = family.getClosestStyle(style);
                }
                if (font != null) {
                    if (fontsAreRegistered) {
                        fontNameCache.put(mapName, font);
                    }
                    return font;
                }
            }
            font = nameTable.get(lowerCaseName);
            if (font != null) {
                if (fontsAreRegistered) {
                    fontNameCache.put(mapName, font);
                }
                return font;
            }
        }

        /* If reach here and no match has been located, then if all fonts
         * are not yet loaded, do so, and then recurse.
         */
        if (!loadedAllFonts) {
            if (logging) {
                logger.info("Load fonts looking for:" + name);
            }
            sgEnv.loadFonts();
            loadedAllFonts = true;
            return findFont2D(name, style, fallback);
        }

        if (!loadedAllFontFiles) {
            if (logging) {
                logger.info("Load font files looking for:" + name);
            }
            sgEnv.loadFontFiles();
            loadedAllFontFiles = true;
            return findFont2D(name, style, fallback);
        }

        /* The primary name is the locale default - ie not US/English but
         * whatever is the default in this locale. This is the way it always
         * has been but may be surprising to some developers if "Arial Regular"
         * were hard-coded in their app and yet "Arial Regular" was not the
         * default name. Fortunately for them, as a consequence of the JDK
         * supporting returning names and family names for arbitrary locales,
         * we also need to support searching all localised names for a match.
         * But because this case of the name used to reference a font is not
         * the same as the default for this locale is rare, it makes sense to
         * search a much shorter list of default locale names and only go to
         * a longer list of names in the event that no match was found.
         * So add here code which searches localised names too.
         * As in 1.4.x this happens only after loading all fonts, which
         * is probably the right order.
         */
        if ((font = findFont2DAllLocales(name, style)) != null) {
            fontNameCache.put(mapName, font);
            return font;
        }

        /* Perhaps its a "compatibility" name - timesroman, helvetica,
         * or courier, which 1.0 apps used for logical fonts.
         * We look for these "late" after a loadFonts as we must not
         * hide real fonts of these names.
         * Map these appropriately:
         * On windows this means according to the rules specified by the
         * FontConfiguration : do it only for encoding==Cp1252
         *
         * REMIND: this is something we plan to remove.
         */
        if (isWindows) {
            String compatName =
                sgEnv.getFontConfiguration().getFallbackFamilyName(name, null);
            if (compatName != null) {
                font = findFont2D(compatName, style, fallback);
                fontNameCache.put(mapName, font);
                return font;
            }
        } else if (lowerCaseName.equals("timesroman")) {
            font = findFont2D("serif", style, fallback);
            fontNameCache.put(mapName, font);
            return font;
        } else if (lowerCaseName.equals("helvetica")) {
            font = findFont2D("sansserif", style, fallback);
            fontNameCache.put(mapName, font);
            return font;
        } else if (lowerCaseName.equals("courier")) {
            font = findFont2D("monospaced", style, fallback);
            fontNameCache.put(mapName, font);
            return font;
        }

        if (logging) {
            logger.info("No font found for:" + name);
        }

        switch (fallback) {
        case PHYSICAL_FALLBACK: return getDefaultPhysicalFont();
        case LOGICAL_FALLBACK: return getDefaultLogicalFont(style);
        default: return null;
        }
    }

    /* This method can be more efficient as it will only need to
     * do the lookup once, and subsequent calls on the java.awt.Font
     * instance can utilise the cached Font2D on that object.
     * Its unfortunate it needs to be a native method, but the font2D
     * variable has to be private.
     */
    public static native Font2D getFont2D(Font font);

    /* Stuff below was in NativeFontWrapper and needed a new home */

    /*
     * Workaround for apps which are dependent on a font metrics bug
     * in JDK 1.1. This is an unsupported win32 private setting.
     */
    public static boolean usePlatformFontMetrics() {
        return usePlatformFontMetrics;
    }

    static native boolean getPlatformFontVar();

    private static final short US_LCID = 0x0409;  // US English - default
    private static Map<String, Short> lcidMap;

    // Return a Microsoft LCID from the given Locale.
    // Used when getting localized font data.

    public static short getLCIDFromLocale(Locale locale) {
        // optimize for common case
        if (locale.equals(Locale.US)) {
            return US_LCID;
        }

        if (lcidMap == null) {
            createLCIDMap();
        }

        String key = locale.toString();
        while (!"".equals(key)) {
            Short lcidObject = (Short) lcidMap.get(key);
            if (lcidObject != null) {
                return lcidObject.shortValue();
            }
            int pos = key.lastIndexOf('_');
            if (pos < 1) {
                return US_LCID;
            }
            key = key.substring(0, pos);
        }

        return US_LCID;
    }


    private static void addLCIDMapEntry(Map<String, Short> map,
                                        String key, short value) {
        map.put(key, Short.valueOf(value));
    }

    private static synchronized void createLCIDMap() {
        if (lcidMap != null) {
            return;
        }

        Map<String, Short> map = new HashMap<String, Short>(200);

        // the following statements are derived from the langIDMap
        // in src/windows/native/java/lang/java_props_md.c using the following
        // awk script:
        //    $1~/\/\*/   { next}
        //    $3~/\?\?/   { next }
        //    $3!~/_/     { next }
        //    $1~/0x0409/ { next }
        //    $1~/0x0c0a/ { next }
        //    $1~/0x042c/ { next }
        //    $1~/0x0443/ { next }
        //    $1~/0x0812/ { next }
        //    $1~/0x04/   { print "        addLCIDMapEntry(map, " substr($3, 0, 3) "\", (short) " substr($1, 0, 6) ");" ; next }
        //    $3~/,/      { print "        addLCIDMapEntry(map, " $3  " (short) " substr($1, 0, 6) ");" ; next }
        //                { print "        addLCIDMapEntry(map, " $3 ", (short) " substr($1, 0, 6) ");" ; next }
        // The lines of this script:
        // - eliminate comments
        // - eliminate questionable locales
        // - eliminate language-only locales
        // - eliminate the default LCID value
        // - eliminate a few other unneeded LCID values
        // - print language-only locale entries for x04* LCID values
        //   (apparently Microsoft doesn't use language-only LCID values -
        //   see http://www.microsoft.com/OpenType/otspec/name.htm
        // - print complete entries for all other LCID values
        // Run
        //     awk -f awk-script langIDMap > statements
        addLCIDMapEntry(map, "ar", (short) 0x0401);
        addLCIDMapEntry(map, "bg", (short) 0x0402);
        addLCIDMapEntry(map, "ca", (short) 0x0403);
        addLCIDMapEntry(map, "zh", (short) 0x0404);
        addLCIDMapEntry(map, "cs", (short) 0x0405);
        addLCIDMapEntry(map, "da", (short) 0x0406);
        addLCIDMapEntry(map, "de", (short) 0x0407);
        addLCIDMapEntry(map, "el", (short) 0x0408);
        addLCIDMapEntry(map, "es", (short) 0x040a);
        addLCIDMapEntry(map, "fi", (short) 0x040b);
        addLCIDMapEntry(map, "fr", (short) 0x040c);
        addLCIDMapEntry(map, "iw", (short) 0x040d);
        addLCIDMapEntry(map, "hu", (short) 0x040e);
        addLCIDMapEntry(map, "is", (short) 0x040f);
        addLCIDMapEntry(map, "it", (short) 0x0410);
        addLCIDMapEntry(map, "ja", (short) 0x0411);
        addLCIDMapEntry(map, "ko", (short) 0x0412);
        addLCIDMapEntry(map, "nl", (short) 0x0413);
        addLCIDMapEntry(map, "no", (short) 0x0414);
        addLCIDMapEntry(map, "pl", (short) 0x0415);
        addLCIDMapEntry(map, "pt", (short) 0x0416);
        addLCIDMapEntry(map, "rm", (short) 0x0417);
        addLCIDMapEntry(map, "ro", (short) 0x0418);
        addLCIDMapEntry(map, "ru", (short) 0x0419);
        addLCIDMapEntry(map, "hr", (short) 0x041a);
        addLCIDMapEntry(map, "sk", (short) 0x041b);
        addLCIDMapEntry(map, "sq", (short) 0x041c);
        addLCIDMapEntry(map, "sv", (short) 0x041d);
        addLCIDMapEntry(map, "th", (short) 0x041e);
        addLCIDMapEntry(map, "tr", (short) 0x041f);
        addLCIDMapEntry(map, "ur", (short) 0x0420);
        addLCIDMapEntry(map, "in", (short) 0x0421);
        addLCIDMapEntry(map, "uk", (short) 0x0422);
        addLCIDMapEntry(map, "be", (short) 0x0423);
        addLCIDMapEntry(map, "sl", (short) 0x0424);
        addLCIDMapEntry(map, "et", (short) 0x0425);
        addLCIDMapEntry(map, "lv", (short) 0x0426);
        addLCIDMapEntry(map, "lt", (short) 0x0427);
        addLCIDMapEntry(map, "fa", (short) 0x0429);
        addLCIDMapEntry(map, "vi", (short) 0x042a);
        addLCIDMapEntry(map, "hy", (short) 0x042b);
        addLCIDMapEntry(map, "eu", (short) 0x042d);
        addLCIDMapEntry(map, "mk", (short) 0x042f);
        addLCIDMapEntry(map, "tn", (short) 0x0432);
        addLCIDMapEntry(map, "xh", (short) 0x0434);
        addLCIDMapEntry(map, "zu", (short) 0x0435);
        addLCIDMapEntry(map, "af", (short) 0x0436);
        addLCIDMapEntry(map, "ka", (short) 0x0437);
        addLCIDMapEntry(map, "fo", (short) 0x0438);
        addLCIDMapEntry(map, "hi", (short) 0x0439);
        addLCIDMapEntry(map, "mt", (short) 0x043a);
        addLCIDMapEntry(map, "se", (short) 0x043b);
        addLCIDMapEntry(map, "gd", (short) 0x043c);
        addLCIDMapEntry(map, "ms", (short) 0x043e);
        addLCIDMapEntry(map, "kk", (short) 0x043f);
        addLCIDMapEntry(map, "ky", (short) 0x0440);
        addLCIDMapEntry(map, "sw", (short) 0x0441);
        addLCIDMapEntry(map, "tt", (short) 0x0444);
        addLCIDMapEntry(map, "bn", (short) 0x0445);
        addLCIDMapEntry(map, "pa", (short) 0x0446);
        addLCIDMapEntry(map, "gu", (short) 0x0447);
        addLCIDMapEntry(map, "ta", (short) 0x0449);
        addLCIDMapEntry(map, "te", (short) 0x044a);
        addLCIDMapEntry(map, "kn", (short) 0x044b);
        addLCIDMapEntry(map, "ml", (short) 0x044c);
        addLCIDMapEntry(map, "mr", (short) 0x044e);
        addLCIDMapEntry(map, "sa", (short) 0x044f);
        addLCIDMapEntry(map, "mn", (short) 0x0450);
        addLCIDMapEntry(map, "cy", (short) 0x0452);
        addLCIDMapEntry(map, "gl", (short) 0x0456);
        addLCIDMapEntry(map, "dv", (short) 0x0465);
        addLCIDMapEntry(map, "qu", (short) 0x046b);
        addLCIDMapEntry(map, "mi", (short) 0x0481);
        addLCIDMapEntry(map, "ar_IQ", (short) 0x0801);
        addLCIDMapEntry(map, "zh_CN", (short) 0x0804);
        addLCIDMapEntry(map, "de_CH", (short) 0x0807);
        addLCIDMapEntry(map, "en_GB", (short) 0x0809);
        addLCIDMapEntry(map, "es_MX", (short) 0x080a);
        addLCIDMapEntry(map, "fr_BE", (short) 0x080c);
        addLCIDMapEntry(map, "it_CH", (short) 0x0810);
        addLCIDMapEntry(map, "nl_BE", (short) 0x0813);
        addLCIDMapEntry(map, "no_NO_NY", (short) 0x0814);
        addLCIDMapEntry(map, "pt_PT", (short) 0x0816);
        addLCIDMapEntry(map, "ro_MD", (short) 0x0818);
        addLCIDMapEntry(map, "ru_MD", (short) 0x0819);
        addLCIDMapEntry(map, "sr_CS", (short) 0x081a);
        addLCIDMapEntry(map, "sv_FI", (short) 0x081d);
        addLCIDMapEntry(map, "az_AZ", (short) 0x082c);
        addLCIDMapEntry(map, "se_SE", (short) 0x083b);
        addLCIDMapEntry(map, "ga_IE", (short) 0x083c);
        addLCIDMapEntry(map, "ms_BN", (short) 0x083e);
        addLCIDMapEntry(map, "uz_UZ", (short) 0x0843);
        addLCIDMapEntry(map, "qu_EC", (short) 0x086b);
        addLCIDMapEntry(map, "ar_EG", (short) 0x0c01);
        addLCIDMapEntry(map, "zh_HK", (short) 0x0c04);
        addLCIDMapEntry(map, "de_AT", (short) 0x0c07);
        addLCIDMapEntry(map, "en_AU", (short) 0x0c09);
        addLCIDMapEntry(map, "fr_CA", (short) 0x0c0c);
        addLCIDMapEntry(map, "sr_CS", (short) 0x0c1a);
        addLCIDMapEntry(map, "se_FI", (short) 0x0c3b);
        addLCIDMapEntry(map, "qu_PE", (short) 0x0c6b);
        addLCIDMapEntry(map, "ar_LY", (short) 0x1001);
        addLCIDMapEntry(map, "zh_SG", (short) 0x1004);
        addLCIDMapEntry(map, "de_LU", (short) 0x1007);
        addLCIDMapEntry(map, "en_CA", (short) 0x1009);
        addLCIDMapEntry(map, "es_GT", (short) 0x100a);
        addLCIDMapEntry(map, "fr_CH", (short) 0x100c);
        addLCIDMapEntry(map, "hr_BA", (short) 0x101a);
        addLCIDMapEntry(map, "ar_DZ", (short) 0x1401);
        addLCIDMapEntry(map, "zh_MO", (short) 0x1404);
        addLCIDMapEntry(map, "de_LI", (short) 0x1407);
        addLCIDMapEntry(map, "en_NZ", (short) 0x1409);
        addLCIDMapEntry(map, "es_CR", (short) 0x140a);
        addLCIDMapEntry(map, "fr_LU", (short) 0x140c);
        addLCIDMapEntry(map, "bs_BA", (short) 0x141a);
        addLCIDMapEntry(map, "ar_MA", (short) 0x1801);
        addLCIDMapEntry(map, "en_IE", (short) 0x1809);
        addLCIDMapEntry(map, "es_PA", (short) 0x180a);
        addLCIDMapEntry(map, "fr_MC", (short) 0x180c);
        addLCIDMapEntry(map, "sr_BA", (short) 0x181a);
        addLCIDMapEntry(map, "ar_TN", (short) 0x1c01);
        addLCIDMapEntry(map, "en_ZA", (short) 0x1c09);
        addLCIDMapEntry(map, "es_DO", (short) 0x1c0a);
        addLCIDMapEntry(map, "sr_BA", (short) 0x1c1a);
        addLCIDMapEntry(map, "ar_OM", (short) 0x2001);
        addLCIDMapEntry(map, "en_JM", (short) 0x2009);
        addLCIDMapEntry(map, "es_VE", (short) 0x200a);
        addLCIDMapEntry(map, "ar_YE", (short) 0x2401);
        addLCIDMapEntry(map, "es_CO", (short) 0x240a);
        addLCIDMapEntry(map, "ar_SY", (short) 0x2801);
        addLCIDMapEntry(map, "en_BZ", (short) 0x2809);
        addLCIDMapEntry(map, "es_PE", (short) 0x280a);
        addLCIDMapEntry(map, "ar_JO", (short) 0x2c01);
        addLCIDMapEntry(map, "en_TT", (short) 0x2c09);
        addLCIDMapEntry(map, "es_AR", (short) 0x2c0a);
        addLCIDMapEntry(map, "ar_LB", (short) 0x3001);
        addLCIDMapEntry(map, "en_ZW", (short) 0x3009);
        addLCIDMapEntry(map, "es_EC", (short) 0x300a);
        addLCIDMapEntry(map, "ar_KW", (short) 0x3401);
        addLCIDMapEntry(map, "en_PH", (short) 0x3409);
        addLCIDMapEntry(map, "es_CL", (short) 0x340a);
        addLCIDMapEntry(map, "ar_AE", (short) 0x3801);
        addLCIDMapEntry(map, "es_UY", (short) 0x380a);
        addLCIDMapEntry(map, "ar_BH", (short) 0x3c01);
        addLCIDMapEntry(map, "es_PY", (short) 0x3c0a);
        addLCIDMapEntry(map, "ar_QA", (short) 0x4001);
        addLCIDMapEntry(map, "es_BO", (short) 0x400a);
        addLCIDMapEntry(map, "es_SV", (short) 0x440a);
        addLCIDMapEntry(map, "es_HN", (short) 0x480a);
        addLCIDMapEntry(map, "es_NI", (short) 0x4c0a);
        addLCIDMapEntry(map, "es_PR", (short) 0x500a);

        lcidMap = map;
    }

    public static int getNumFonts() {
        return physicalFonts.size()+maxCompFont;
    }

    private static boolean fontSupportsEncoding(Font font, String encoding) {
        return getFont2D(font).supportsEncoding(encoding);
    }

    public synchronized static native String getFontPath(boolean noType1Fonts);
    public synchronized static native void setNativeFontPath(String fontPath);


    private static Thread fileCloser = null;
    static Vector<File> tmpFontFiles = null;

    public static Font2D createFont2D(File fontFile, int fontFormat,
                                      boolean isCopy,
                                      CreatedFontTracker tracker)
        throws FontFormatException {

        String fontFilePath = fontFile.getPath();
        FileFont font2D = null;
        final File fFile = fontFile;
        final CreatedFontTracker _tracker = tracker;
        try {
            switch (fontFormat) {
            case Font.TRUETYPE_FONT:
                font2D = new TrueTypeFont(fontFilePath, null, 0, true);
                break;
            case Font.TYPE1_FONT:
                font2D = new Type1Font(fontFilePath, null, isCopy);
                break;
            default:
                throw new FontFormatException("Unrecognised Font Format");
            }
        } catch (FontFormatException e) {
            if (isCopy) {
                java.security.AccessController.doPrivileged(
                     new java.security.PrivilegedAction() {
                          public Object run() {
                              if (_tracker != null) {
                                  _tracker.subBytes((int)fFile.length());
                              }
                              fFile.delete();
                              return null;
                          }
                });
            }
            throw(e);
        }
        if (isCopy) {
            font2D.setFileToRemove(fontFile, tracker);
            synchronized (FontManager.class) {

                if (tmpFontFiles == null) {
                    tmpFontFiles = new Vector<File>();
                }
                tmpFontFiles.add(fontFile);

                if (fileCloser == null) {
                    final Runnable fileCloserRunnable = new Runnable() {
                      public void run() {
                         java.security.AccessController.doPrivileged(
                         new java.security.PrivilegedAction() {
                         public Object run() {

                            for (int i=0;i<CHANNELPOOLSIZE;i++) {
                                if (fontFileCache[i] != null) {
                                    try {
                                        fontFileCache[i].close();
                                    } catch (Exception e) {
                                    }
                                }
                            }
                            if (tmpFontFiles != null) {
                                File[] files = new File[tmpFontFiles.size()];
                                files = tmpFontFiles.toArray(files);
                                for (int f=0; f<files.length;f++) {
                                    try {
                                        files[f].delete();
                                    } catch (Exception e) {
                                    }
                                }
                            }

                            return null;
                          }

                          });
                      }
                    };
                    java.security.AccessController.doPrivileged(
                       new java.security.PrivilegedAction() {
                          public Object run() {
                              /* The thread must be a member of a thread group
                               * which will not get GCed before VM exit.
                               * Make its parent the top-level thread group.
                               */
                              ThreadGroup tg =
                                  Thread.currentThread().getThreadGroup();
                              for (ThreadGroup tgn = tg;
                                   tgn != null;
                                   tg = tgn, tgn = tg.getParent());
                              fileCloser = new Thread(tg, fileCloserRunnable);
                              Runtime.getRuntime().addShutdownHook(fileCloser);
                              return null;
                          }
                    });
                }
            }
        }
        return font2D;
    }

    /* remind: used in X11GraphicsEnvironment and called often enough
     * that we ought to obsolete this code
     */
    public synchronized static String getFullNameByFileName(String fileName) {
        PhysicalFont[] physFonts = getPhysicalFonts();
        for (int i=0;i<physFonts.length;i++) {
            if (physFonts[i].platName.equals(fileName)) {
                return (physFonts[i].getFontName(null));
            }
        }
        return null;
    }

    /*
     * This is called when font is determined to be invalid/bad.
     * It designed to be called (for example) by the font scaler
     * when in processing a font file it is discovered to be incorrect.
     * This is different than the case where fonts are discovered to
     * be incorrect during initial verification, as such fonts are
     * never registered.
     * Handles to this font held are re-directed to a default font.
     * This default may not be an ideal substitute buts it better than
     * crashing This code assumes a PhysicalFont parameter as it doesn't
     * make sense for a Composite to be "bad".
     */
    public static synchronized void deRegisterBadFont(Font2D font2D) {
        if (!(font2D instanceof PhysicalFont)) {
            /* We should never reach here, but just in case */
            return;
        } else {
            if (logging) {
                logger.severe("Deregister bad font: " + font2D);
            }
            replaceFont((PhysicalFont)font2D, getDefaultPhysicalFont());
        }
    }

    /*
     * This encapsulates all the work that needs to be done when a
     * Font2D is replaced by a different Font2D.
     */
    public static synchronized void replaceFont(PhysicalFont oldFont,
                                                PhysicalFont newFont) {

        if (oldFont.handle.font2D != oldFont) {
            /* already done */
            return;
        }

        /* If we try to replace the font with itself, that won't work,
         * so pick any alternative physical font
         */
        if (oldFont == newFont) {
            if (logging) {
                logger.severe("Can't replace bad font with itself " + oldFont);
            }
            PhysicalFont[] physFonts = getPhysicalFonts();
            for (int i=0; i<physFonts.length;i++) {
                if (physFonts[i] != newFont) {
                    newFont = physFonts[i];
                    break;
                }
            }
            if (oldFont == newFont) {
                if (logging) {
                    logger.severe("This is bad. No good physicalFonts found.");
                }
                return;
            }
        }

        /* eliminate references to this font, so it won't be located
         * by future callers, and will be eligible for GC when all
         * references are removed
         */
        oldFont.handle.font2D = newFont;
        physicalFonts.remove(oldFont.fullName);
        fullNameToFont.remove(oldFont.fullName.toLowerCase(Locale.ENGLISH));
        FontFamily.remove(oldFont);

        if (localeFullNamesToFont != null) {
            Map.Entry[] mapEntries =
                (Map.Entry[])localeFullNamesToFont.entrySet().
                toArray(new Map.Entry[0]);
            /* Should I be replacing these, or just I just remove
             * the names from the map?
             */
            for (int i=0; i<mapEntries.length;i++) {
                if (mapEntries[i].getValue() == oldFont) {
                    try {
                        mapEntries[i].setValue(newFont);
                    } catch (Exception e) {
                        /* some maps don't support this operation.
                         * In this case just give up and remove the entry.
                         */
                        localeFullNamesToFont.remove(mapEntries[i].getKey());
                    }
                }
            }
        }

        for (int i=0; i<maxCompFont; i++) {
            /* Deferred initialization of composites shouldn't be
             * a problem for this case, since a font must have been
             * initialised to be discovered to be bad.
             * Some JRE composites on Solaris use two versions of the same
             * font. The replaced font isn't bad, just "smaller" so there's
             * no need to make the slot point to the new font.
             * Since composites have a direct reference to the Font2D (not
             * via a handle) making this substitution is not safe and could
             * cause an additional problem and so this substitution is
             * warranted only when a font is truly "bad" and could cause
             * a crash. So we now replace it only if its being substituted
             * with some font other than a fontconfig rank font
             * Since in practice a substitution will have the same rank
             * this may never happen, but the code is safer even if its
             * also now a no-op.
             * The only obvious "glitch" from this stems from the current
             * implementation that when asked for the number of glyphs in a
             * composite it lies and returns the number in slot 0 because
             * composite glyphs aren't contiguous. Since we live with that
             * we can live with the glitch that depending on how it was
             * initialised a composite may return different values for this.
             * Fixing the issues with composite glyph ids is tricky as
             * there are exclusion ranges and unlike other fonts even the
             * true "numGlyphs" isn't a contiguous range. Likely the only
             * solution is an API that returns an array of glyph ranges
             * which takes precedence over the existing API. That might
             * also need to address excluding ranges which represent a
             * code point supported by an earlier component.
             */
            if (newFont.getRank() > Font2D.FONT_CONFIG_RANK) {
                compFonts[i].replaceComponentFont(oldFont, newFont);
            }
        }
    }

    private static synchronized void loadLocaleNames() {
        if (localeFullNamesToFont != null) {
            return;
        }
        localeFullNamesToFont = new HashMap<String, TrueTypeFont>();
        Font2D[] fonts = getRegisteredFonts();
        for (int i=0; i<fonts.length; i++) {
            if (fonts[i] instanceof TrueTypeFont) {
                TrueTypeFont ttf = (TrueTypeFont)fonts[i];
                String[] fullNames = ttf.getAllFullNames();
                for (int n=0; n<fullNames.length; n++) {
                    localeFullNamesToFont.put(fullNames[n], ttf);
                }
                FontFamily family = FontFamily.getFamily(ttf.familyName);
                if (family != null) {
                    FontFamily.addLocaleNames(family, ttf.getAllFamilyNames());
                }
            }
        }
    }

    /* This replicate the core logic of findFont2D but operates on
     * all the locale names. This hasn't been merged into findFont2D to
     * keep the logic simpler and reduce overhead, since this case is
     * almost never used. The main case in which it is called is when
     * a bogus font name is used and we need to check all possible names
     * before returning the default case.
     */
    private static Font2D findFont2DAllLocales(String name, int style) {

        if (logging) {
            logger.info("Searching localised font names for:" + name);
        }

        /* If reach here and no match has been located, then if we have
         * not yet built the map of localeFullNamesToFont for TT fonts, do so
         * now. This method must be called after all fonts have been loaded.
         */
        if (localeFullNamesToFont == null) {
            loadLocaleNames();
        }
        String lowerCaseName = name.toLowerCase();
        Font2D font = null;

        /* First see if its a family name. */
        FontFamily family = FontFamily.getLocaleFamily(lowerCaseName);
        if (family != null) {
          font = family.getFont(style);
          if (font == null) {
            font = family.getClosestStyle(style);
          }
          if (font != null) {
              return font;
          }
        }

        /* If it wasn't a family name, it should be a full name. */
        synchronized (FontManager.class) {
            font = localeFullNamesToFont.get(name);
        }
        if (font != null) {
            if (font.style == style || style == Font.PLAIN) {
                return font;
            } else {
                family = FontFamily.getFamily(font.getFamilyName(null));
                if (family != null) {
                    Font2D familyFont = family.getFont(style);
                    /* We exactly matched the requested style, use it! */
                    if (familyFont != null) {
                        return familyFont;
                    } else {
                        familyFont = family.getClosestStyle(style);
                        if (familyFont != null) {
                            /* The next check is perhaps one
                             * that shouldn't be done. ie if we get this
                             * far we have probably as close a match as we
                             * are going to get. We could load all fonts to
                             * see if somehow some parts of the family are
                             * loaded but not all of it.
                             * This check is commented out for now.
                             */
                            if (!familyFont.canDoStyle(style)) {
                                familyFont = null;
                            }
                            return familyFont;
                        }
                    }
                }
            }
        }
        return font;
    }

    /* Supporting "alternate" composite fonts on 2D graphics objects
     * is accessed by the application by calling methods on the local
     * GraphicsEnvironment. The overall implementation is described
     * in one place, here, since otherwise the implementation is spread
     * around it may be difficult to track.
     * The methods below call into SunGraphicsEnvironment which creates a
     * new FontConfiguration instance. The FontConfiguration class,
     * and its platform sub-classes are updated to take parameters requesting
     * these behaviours. This is then used to create new composite font
     * instances. Since this calls the initCompositeFont method in
     * SunGraphicsEnvironment it performs the same initialization as is
     * performed normally. There may be some duplication of effort, but
     * that code is already written to be able to perform properly if called
     * to duplicate work. The main difference is that if we detect we are
     * running in an applet/browser/Java plugin environment these new fonts
     * are not placed in the "default" maps but into an AppContext instance.
     * The font lookup mechanism in java.awt.Font.getFont2D() is also updated
     * so that look-up for composite fonts will in that case always
     * do a lookup rather than returning a cached result.
     * This is inefficient but necessary else singleton java.awt.Font
     * instances would not retrieve the correct Font2D for the appcontext.
     * sun.font.FontManager.findFont2D is also updated to that it uses
     * a name map cache specific to that appcontext.
     *
     * Getting an AppContext is expensive, so there is a global variable
     * that records whether these methods have ever been called and can
     * avoid the expense for almost all applications. Once the correct
     * CompositeFont is associated with the Font, everything should work
     * through existing mechanisms.
     * A special case is that GraphicsEnvironment.getAllFonts() must
     * return an AppContext specific list.
     *
     * Calling the methods below is "heavyweight" but it is expected that
     * these methods will be called very rarely.
     *
     * If usingPerAppContextComposites is true, we are in "applet"
     * (eg browser) enviroment and at least one context has selected
     * an alternate composite font behaviour.
     * If usingAlternateComposites is true, we are not in an "applet"
     * environment and the (single) application has selected
     * an alternate composite font behaviour.
     *
     * - Printing: The implementation delegates logical fonts to an AWT
     * mechanism which cannot use these alternate configurations.
     * We can detect that alternate fonts are in use and back-off to 2D, but
     * that uses outlines. Much of this can be fixed with additional work
     * but that may have to wait. The results should be correct, just not
     * optimal.
     */
    private static final Object altJAFontKey       = new Object();
    private static final Object localeFontKey       = new Object();
    private static final Object proportionalFontKey = new Object();
    public static boolean usingPerAppContextComposites = false;
    private static boolean usingAlternateComposites = false;

    /* These values are used only if we are running as a standalone
     * application, as determined by maybeMultiAppContext();
     */
    private static boolean gAltJAFont = false;
    private static boolean gLocalePref = false;
    private static boolean gPropPref = false;

    /* This method doesn't check if alternates are selected in this app
     * context. Its used by the FontMetrics caching code which in such
     * a case cannot retrieve a cached metrics solely on the basis of
     * the Font.equals() method since it needs to also check if the Font2D
     * is the same.
     * We also use non-standard composites for Swing native L&F fonts on
     * Windows. In that case the policy is that the metrics reported are
     * based solely on the physical font in the first slot which is the
     * visible java.awt.Font. So in that case the metrics cache which tests
     * the Font does what we want. In the near future when we expand the GTK
     * logical font definitions we may need to revisit this if GTK reports
     * combined metrics instead. For now though this test can be simple.
     */
    static boolean maybeUsingAlternateCompositeFonts() {
       return usingAlternateComposites || usingPerAppContextComposites;
    }

    public static boolean usingAlternateCompositeFonts() {
        return (usingAlternateComposites ||
                (usingPerAppContextComposites &&
                AppContext.getAppContext().get(CompositeFont.class) != null));
    }

    private static boolean maybeMultiAppContext() {
        Boolean appletSM = (Boolean)
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction() {
                        public Object run() {
                            SecurityManager sm = System.getSecurityManager();
                            return new Boolean
                                (sm instanceof sun.applet.AppletSecurity);
                        }
                    });
        return appletSM.booleanValue();
    }

    /* Modifies the behaviour of a subsequent call to preferLocaleFonts()
     * to use Mincho instead of Gothic for dialoginput in JA locales
     * on windows. Not needed on other platforms.
     */
    public static synchronized void useAlternateFontforJALocales() {

        if (!isWindows) {
            return;
        }

        initSGEnv();
        if (!maybeMultiAppContext()) {
            gAltJAFont = true;
        } else {
            AppContext appContext = AppContext.getAppContext();
            appContext.put(altJAFontKey, altJAFontKey);
        }
    }

    public static boolean usingAlternateFontforJALocales() {
        if (!maybeMultiAppContext()) {
            return gAltJAFont;
        } else {
            AppContext appContext = AppContext.getAppContext();
            return appContext.get(altJAFontKey) == altJAFontKey;
        }
    }

    public static synchronized void preferLocaleFonts() {

        initSGEnv();

        /* Test if re-ordering will have any effect */
        if (!FontConfiguration.willReorderForStartupLocale()) {
            return;
        }

        if (!maybeMultiAppContext()) {
            if (gLocalePref == true) {
                return;
            }
            gLocalePref = true;
            sgEnv.createCompositeFonts(fontNameCache, gLocalePref, gPropPref);
            usingAlternateComposites = true;
        } else {
            AppContext appContext = AppContext.getAppContext();
            if (appContext.get(localeFontKey) == localeFontKey) {
                return;
            }
            appContext.put(localeFontKey, localeFontKey);
            boolean acPropPref =
                appContext.get(proportionalFontKey) == proportionalFontKey;
            ConcurrentHashMap<String, Font2D>
                altNameCache = new ConcurrentHashMap<String, Font2D> ();
            /* If there is an existing hashtable, we can drop it. */
            appContext.put(CompositeFont.class, altNameCache);
            usingPerAppContextComposites = true;
            sgEnv.createCompositeFonts(altNameCache, true, acPropPref);
        }
    }

    public static synchronized void preferProportionalFonts() {

        /* If no proportional fonts are configured, there's no need
         * to take any action.
         */
        if (!FontConfiguration.hasMonoToPropMap()) {
            return;
        }

        initSGEnv();

        if (!maybeMultiAppContext()) {
            if (gPropPref == true) {
                return;
            }
            gPropPref = true;
            sgEnv.createCompositeFonts(fontNameCache, gLocalePref, gPropPref);
            usingAlternateComposites = true;
        } else {
            AppContext appContext = AppContext.getAppContext();
            if (appContext.get(proportionalFontKey) == proportionalFontKey) {
                return;
            }
            appContext.put(proportionalFontKey, proportionalFontKey);
            boolean acLocalePref =
                appContext.get(localeFontKey) == localeFontKey;
            ConcurrentHashMap<String, Font2D>
                altNameCache = new ConcurrentHashMap<String, Font2D> ();
            /* If there is an existing hashtable, we can drop it. */
            appContext.put(CompositeFont.class, altNameCache);
            usingPerAppContextComposites = true;
            sgEnv.createCompositeFonts(altNameCache, acLocalePref, true);
        }
    }

    private static HashSet<String> installedNames = null;
    private static HashSet<String> getInstalledNames() {
        if (installedNames == null) {
           Locale l = sgEnv.getSystemStartupLocale();
           String[] installedFamilies = sgEnv.getInstalledFontFamilyNames(l);
           Font[] installedFonts = sgEnv.getAllInstalledFonts();
           HashSet<String> names = new HashSet<String>();
           for (int i=0; i<installedFamilies.length; i++) {
               names.add(installedFamilies[i].toLowerCase(l));
           }
           for (int i=0; i<installedFonts.length; i++) {
               names.add(installedFonts[i].getFontName(l).toLowerCase(l));
           }
           installedNames = names;
        }
        return installedNames;
    }

    /* Keys are used to lookup per-AppContext Hashtables */
    private static final Object regFamilyKey  = new Object();
    private static final Object regFullNameKey = new Object();
    private static Hashtable<String,FontFamily> createdByFamilyName;
    private static Hashtable<String,Font2D>     createdByFullName;
    private static boolean fontsAreRegistered = false;
    private static boolean fontsAreRegisteredPerAppContext = false;

    public static boolean registerFont(Font font) {
        /* This method should not be called with "null".
         * It is the caller's responsibility to ensure that.
         */
        if (font == null) {
            return false;
        }

        /* Initialise these objects only once we start to use this API */
        synchronized (regFamilyKey) {
            if (createdByFamilyName == null) {
                createdByFamilyName = new Hashtable<String,FontFamily>();
                createdByFullName = new Hashtable<String,Font2D>();
            }
        }

        if (!isCreatedFont(font)) {
            return false;
        }
        if (sgEnv == null) {
            initSGEnv();
        }
        /* We want to ensure that this font cannot override existing
         * installed fonts. Check these conditions :
         * - family name is not that of an installed font
         * - full name is not that of an installed font
         * - family name is not the same as the full name of an installed font
         * - full name is not the same as the family name of an installed font
         * The last two of these may initially look odd but the reason is
         * that (unfortunately) Font constructors do not distinuguish these.
         * An extreme example of such a problem would be a font which has
         * family name "Dialog.Plain" and full name of "Dialog".
         * The one arguably overly stringent restriction here is that if an
         * application wants to supply a new member of an existing family
         * It will get rejected. But since the JRE can perform synthetic
         * styling in many cases its not necessary.
         * We don't apply the same logic to registered fonts. If apps want
         * to do this lets assume they have a reason. It won't cause problems
         * except for themselves.
         */
        HashSet<String> names = getInstalledNames();
        Locale l = sgEnv.getSystemStartupLocale();
        String familyName = font.getFamily(l).toLowerCase();
        String fullName = font.getFontName(l).toLowerCase();
        if (names.contains(familyName) || names.contains(fullName)) {
            return false;
        }

        /* Checks passed, now register the font */
        Hashtable<String,FontFamily> familyTable;
        Hashtable<String,Font2D> fullNameTable;
        if (!maybeMultiAppContext()) {
            familyTable = createdByFamilyName;
            fullNameTable = createdByFullName;
            fontsAreRegistered = true;
        } else {
            AppContext appContext = AppContext.getAppContext();
            familyTable =
                (Hashtable<String,FontFamily>)appContext.get(regFamilyKey);
            fullNameTable =
                (Hashtable<String,Font2D>)appContext.get(regFullNameKey);
            if (familyTable == null) {
                familyTable = new Hashtable<String,FontFamily>();
                fullNameTable = new Hashtable<String,Font2D>();
                appContext.put(regFamilyKey, familyTable);
                appContext.put(regFullNameKey, fullNameTable);
            }
            fontsAreRegisteredPerAppContext = true;
        }
        /* Create the FontFamily and add font to the tables */
        Font2D font2D = getFont2D(font);
        int style = font2D.getStyle();
        FontFamily family = familyTable.get(familyName);
        if (family == null) {
            family = new FontFamily(font.getFamily(l));
            familyTable.put(familyName, family);
        }
        /* Remove name cache entries if not using app contexts.
         * To accommodate a case where code may have registered first a plain
         * family member and then used it and is now registering a bold family
         * member, we need to remove all members of the family, so that the
         * new style can get picked up rather than continuing to synthesise.
         */
        if (fontsAreRegistered) {
            removeFromCache(family.getFont(Font.PLAIN));
            removeFromCache(family.getFont(Font.BOLD));
            removeFromCache(family.getFont(Font.ITALIC));
            removeFromCache(family.getFont(Font.BOLD|Font.ITALIC));
            removeFromCache(fullNameTable.get(fullName));
        }
        family.setFont(font2D, style);
        fullNameTable.put(fullName, font2D);
        return true;
    }

    /* Remove from the name cache all references to the Font2D */
    private static void removeFromCache(Font2D font) {
        if (font == null) {
            return;
        }
        String[] keys = (String[])(fontNameCache.keySet().toArray(STR_ARRAY));
        for (int k=0; k<keys.length;k++) {
            if (fontNameCache.get(keys[k]) == font) {
                fontNameCache.remove(keys[k]);
            }
        }
    }

    // It may look odd to use TreeMap but its more convenient to the caller.
    public static TreeMap<String, String> getCreatedFontFamilyNames() {

        Hashtable<String,FontFamily> familyTable;
        if (fontsAreRegistered) {
            familyTable = createdByFamilyName;
        } else if (fontsAreRegisteredPerAppContext) {
            AppContext appContext = AppContext.getAppContext();
            familyTable =
                (Hashtable<String,FontFamily>)appContext.get(regFamilyKey);
        } else {
            return null;
        }

        Locale l = sgEnv.getSystemStartupLocale();
        synchronized (familyTable) {
            TreeMap<String, String> map = new TreeMap<String, String>();
            for (FontFamily f : familyTable.values()) {
                Font2D font2D = f.getFont(Font.PLAIN);
                if (font2D == null) {
                    font2D = f.getClosestStyle(Font.PLAIN);
                }
                String name = font2D.getFamilyName(l);
                map.put(name.toLowerCase(l), name);
            }
            return map;
        }
    }

    public static Font[] getCreatedFonts() {

        Hashtable<String,Font2D> nameTable;
        if (fontsAreRegistered) {
            nameTable = createdByFullName;
        } else if (fontsAreRegisteredPerAppContext) {
            AppContext appContext = AppContext.getAppContext();
            nameTable =
                (Hashtable<String,Font2D>)appContext.get(regFullNameKey);
        } else {
            return null;
        }

        Locale l = sgEnv.getSystemStartupLocale();
        synchronized (nameTable) {
            Font[] fonts = new Font[nameTable.size()];
            int i=0;
            for (Font2D font2D : nameTable.values()) {
                fonts[i++] = new Font(font2D.getFontName(l), Font.PLAIN, 1);
            }
            return fonts;
        }
    }

    /* Begin support for GTK Look and Feel - query libfontconfig and
     * return a composite Font to Swing that uses the desktop font(s).
     */

    /* A small "map" from GTK/fontconfig names to the equivalent JDK
     * logical font name.
    */
    private static final String[][] nameMap = {
        {"sans",       "sansserif"},
        {"sans-serif", "sansserif"},
        {"serif",      "serif"},
        {"monospace",  "monospaced"}
    };

    public static String mapFcName(String name) {
        for (int i = 0; i < nameMap.length; i++) {
            if (name.equals(nameMap[i][0])) {
                return nameMap[i][1];
            }
        }
        return null;
    }

    /* fontconfig recognises slants roman, italic, as well as oblique,
     * and a slew of weights, where the ones that matter here are
     * regular and bold.
     * To fully qualify what we want, we can for example ask for (eg)
     * Font.PLAIN             : "serif:regular:roman"
     * Font.BOLD              : "serif:bold:roman"
     * Font.ITALIC            : "serif:regular:italic"
     * Font.BOLD|Font.ITALIC  : "serif:bold:italic"
     */
    private static String[] fontConfigNames = {
        "sans:regular:roman",
        "sans:bold:roman",
        "sans:regular:italic",
        "sans:bold:italic",

        "serif:regular:roman",
        "serif:bold:roman",
        "serif:regular:italic",
        "serif:bold:italic",

        "monospace:regular:roman",
        "monospace:bold:roman",
        "monospace:regular:italic",
        "monospace:bold:italic",
    };

    /* These next three classes are just data structures.
     */
    static class FontConfigFont {
        String familyName;        // eg Bitstream Vera Sans
        String styleStr;          // eg Bold
        String fullName;          // eg Bitstream Vera Sans Bold
        String fontFile;          // eg /usr/X11/lib/fonts/foo.ttf
    }

    static class FcCompFont {
        String fcName;            // eg sans
        String fcFamily;          // eg sans
        String jdkName;           // eg sansserif
        int style;                // eg 0=PLAIN
        FontConfigFont firstFont;
        FontConfigFont[] allFonts;
        //boolean preferBitmaps;    // if embedded bitmaps preferred over AA
        CompositeFont compFont;   // null if not yet created/known.
    }

    static class FontConfigInfo {
        int fcVersion;
        String[] cacheDirs = new String[4];
    }

    private static String getFCLocaleStr() {
        Locale l = SunToolkit.getStartupLocale();
        String localeStr = l.getLanguage();
        String country = l.getCountry();
        if (!country.equals("")) {
            localeStr = localeStr + "-" + country;
        }
        return localeStr;
    }

    /* This does cause the native libfontconfig to be loaded and unloaded,
     * but it does not incur the overhead of initialisation of its
     * data structures, so shouldn't have a measurable impact.
     */
    public static native int getFontConfigVersion();

    private static native int
        getFontConfigAASettings(String locale, String fcFamily);

    /* This is public solely so that for debugging purposes it can be called
     * with other names, which might (eg) include a size, eg "sans-24"
     * The return value is a text aa rendering hint value.
     * Normally we should call the no-args version.
     */
    public static Object getFontConfigAAHint(String fcFamily) {
        if (isWindows) {
            return null;
        } else {
            int hint = getFontConfigAASettings(getFCLocaleStr(), fcFamily);
            if (hint < 0) {
                return null;
            } else {
                return SunHints.Value.get(SunHints.INTKEY_TEXT_ANTIALIASING,
                                          hint);
            }
        }
    }

    /* Called from code that needs to know what are the AA settings
     * that apps using FC would pick up for the default desktop font.
     * Note apps can change the default desktop font. etc, so this
     * isn't certain to be right but its going to correct for most cases.
     * Native return values map to the text aa values in sun.awt.SunHints.
     * which is used to look up the renderinghint value object.
     */
    public static Object getFontConfigAAHint() {
        return getFontConfigAAHint("sans");
    }

    /* This is populated by native */
    private static final FontConfigInfo fcInfo = new FontConfigInfo();

    /* This array has the array elements created in Java code and is
     * passed down to native to be filled in.
     */
    private static FcCompFont[] fontConfigFonts;

    /* Return an array of FcCompFont structs describing the primary
     * font located for each of fontconfig/GTK/Pango's logical font names.
     */
    private static native void getFontConfig(String locale,
                                             FontConfigInfo fcInfo,
                                             FcCompFont[] fonts,
                                             boolean includeFallbacks);

    static void populateFontConfig(FcCompFont[] fcInfo) {
        fontConfigFonts = fcInfo;
    }

    static FcCompFont[] loadFontConfig() {
        initFontConfigFonts(true);
        return fontConfigFonts;
    }

    static FontConfigInfo getFontConfigInfo() {
        initFontConfigFonts(true);
        return fcInfo;
    }

    /* This can be made public if it's needed to force a re-read
     * rather than using the cached values. The re-read would be needed
     * only if some event signalled that the fontconfig has changed.
     * In that event this method would need to return directly the array
     * to be used by the caller in case it subsequently changed.
     */
    private static synchronized void
        initFontConfigFonts(boolean includeFallbacks) {

        if (fontConfigFonts != null) {
            if (!includeFallbacks || (fontConfigFonts[0].allFonts != null)) {
                return;
            }
        }

        if (isWindows || fontConfigFailed) {
            return;
        }

        long t0 = 0;
        if (logging) {
            t0 = System.nanoTime();
        }

        FcCompFont[] fontArr = new FcCompFont[fontConfigNames.length];
        for (int i = 0; i< fontArr.length; i++) {
            fontArr[i] = new FcCompFont();
            fontArr[i].fcName = fontConfigNames[i];
            int colonPos = fontArr[i].fcName.indexOf(':');
            fontArr[i].fcFamily = fontArr[i].fcName.substring(0, colonPos);
            fontArr[i].jdkName = mapFcName(fontArr[i].fcFamily);
            fontArr[i].style = i % 4; // depends on array order.
        }
        getFontConfig(getFCLocaleStr(), fcInfo, fontArr, includeFallbacks);
        /* If don't find anything (eg no libfontconfig), then just return */
        for (int i = 0; i< fontArr.length; i++) {
            FcCompFont fci = fontArr[i];
            if (fci.firstFont == null) {
                if (logging) {
                    logger.info("Fontconfig returned no fonts.");
                }
                fontConfigFailed = true;
                return;
            }
        }
        fontConfigFonts = fontArr;

        if (logging) {
            long t1 = System.nanoTime();
            logger.info("Time spent accessing fontconfig="+
                        (t1-t0)/1000000+"ms.");

            for (int i = 0; i< fontConfigFonts.length; i++) {
                FcCompFont fci = fontConfigFonts[i];
                logger.info("FC font " + fci.fcName+" maps to family " +
                            fci.firstFont.familyName +
                            " in file " + fci.firstFont.fontFile);
                if (fci.allFonts != null) {
                    for (int f=0;f<fci.allFonts.length;f++) {
                        FontConfigFont fcf = fci.allFonts[f];
                        logger.info("Family=" + fcf.familyName +
                                    " Style="+ fcf.styleStr +
                                    " Fullname="+fcf.fullName +
                                    " File="+fcf.fontFile);
                    }
                }
            }
        }
    }

    private static PhysicalFont registerFromFcInfo(FcCompFont fcInfo) {

        /* If it's a TTC file we need to know that as we will need to
         * make sure we return the right font */
        String fontFile = fcInfo.firstFont.fontFile;
        int offset = fontFile.length()-4;
        if (offset <= 0) {
            return null;
        }
        String ext = fontFile.substring(offset).toLowerCase();
        boolean isTTC = ext.equals(".ttc");

        /* If this file is already registered, can just return its font.
         * However we do need to check in case it's a TTC as we need
         * a specific font, so rather than directly returning it, let
         * findFont2D resolve that.
         */
        PhysicalFont physFont = registeredFontFiles.get(fontFile);
        if (physFont != null) {
            if (isTTC) {
                Font2D f2d = findFont2D(fcInfo.firstFont.familyName,
                                        fcInfo.style, NO_FALLBACK);
                if (f2d instanceof PhysicalFont) { /* paranoia */
                    return (PhysicalFont)f2d;
                } else {
                    return null;
                }
            } else {
                return physFont;
            }
        }

        /* If the font may hide a JRE font (eg fontconfig says it is
         * Lucida Sans), we want to use the JRE version, so make it
         * point to the JRE font.
         */
        physFont = findJREDeferredFont(fcInfo.firstFont.familyName,
                                       fcInfo.style);

        /* It is also possible the font file is on the "deferred" list,
         * in which case we can just initialise it now.
         */
        if (physFont == null &&
            deferredFontFiles.get(fontFile) != null)
        {
            physFont = initialiseDeferredFont(fcInfo.firstFont.fontFile);
            /* use findFont2D to get the right font from TTC's */
            if (physFont != null) {
                if (isTTC) {
                    Font2D f2d = findFont2D(fcInfo.firstFont.familyName,
                                            fcInfo.style, NO_FALLBACK);
                    if (f2d instanceof PhysicalFont) { /* paranoia */
                        return (PhysicalFont)f2d;
                    } else {
                        return null;
                    }
                } else {
                    return physFont;
                }
            }
        }

        /* In the majority of cases we reach here, and need to determine
         * the type and rank to register the font.
         */
        if (physFont == null) {
            int fontFormat = FONTFORMAT_NONE;
            int fontRank = Font2D.UNKNOWN_RANK;

            if (ext.equals(".ttf") || ext.equals(".otf") || isTTC) {
                fontFormat = FONTFORMAT_TRUETYPE;
                fontRank = Font2D.TTF_RANK;
            } else if (ext.equals(".pfa") || ext.equals(".pfb")) {
                fontFormat = FONTFORMAT_TYPE1;
                fontRank = Font2D.TYPE1_RANK;
            }
            physFont = registerFontFile(fcInfo.firstFont.fontFile, null,
                                      fontFormat, true, fontRank);
        }
        return physFont;
    }

    private static String[] getPlatformFontDirs() {
        String path = getFontPath(true);
        StringTokenizer parser =
            new StringTokenizer(path, File.pathSeparator);
        ArrayList<String> pathList = new ArrayList<String>();
        try {
            while (parser.hasMoreTokens()) {
                pathList.add(parser.nextToken());
            }
        } catch (NoSuchElementException e) {
        }
        return pathList.toArray(new String[0]);
    }

    /** returns an array of two strings. The first element is the
     * name of the font. The second element is the file name.
     */
    private static String[] defaultPlatformFont = null;
    public static String[] getDefaultPlatformFont() {

        if (defaultPlatformFont != null) {
            return defaultPlatformFont;
        }

        String[] info = new String[2];
        if (isWindows) {
            info[0] = "Arial";
            info[1] = "c:\\windows\\fonts";
            final String[] dirs = getPlatformFontDirs();
            if (dirs.length > 1) {
                String dir = (String)
                    AccessController.doPrivileged(new PrivilegedAction() {
                        public Object run() {
                            for (int i=0; i<dirs.length; i++) {
                                String path =
                                    dirs[i] + File.separator + "arial.ttf";
                                File file = new File(path);
                                if (file.exists()) {
                                    return dirs[i];
                                }
                            }
                            return null;
                        }
                });
                if (dir != null) {
                    info[1] = dir;
                }
            } else {
                info[1] = dirs[0];
            }
            info[1] = info[1] + File.separator + "arial.ttf";
        } else {
            initFontConfigFonts(false);
            for (int i=0; i<fontConfigFonts.length; i++) {
                if ("sans".equals(fontConfigFonts[i].fcFamily) &&
                    0 == fontConfigFonts[i].style) {
                    info[0] = fontConfigFonts[i].firstFont.familyName;
                    info[1] = fontConfigFonts[i].firstFont.fontFile;
                    break;
                }
            }
            /* Absolute last ditch attempt in the face of fontconfig problems.
             * If we didn't match, pick the first, or just make something
             * up so we don't NPE.
             */
            if (info[0] == null) {
                 if (fontConfigFonts.length > 0 &&
                     fontConfigFonts[0].firstFont.fontFile != null) {
                     info[0] = fontConfigFonts[0].firstFont.familyName;
                     info[1] = fontConfigFonts[0].firstFont.fontFile;
                 } else {
                     info[0] = "Dialog";
                     info[1] = "/dialog.ttf";
                 }
            }
        }
        defaultPlatformFont = info;
        return defaultPlatformFont;
    }

    private FcCompFont getFcCompFont() {
         initFontConfigFonts(false);
         for (int i=0; i<fontConfigFonts.length; i++) {
             if ("sans".equals(fontConfigFonts[i].fcFamily) &&
                 0 == fontConfigFonts[i].style) {
                 return fontConfigFonts[i];
             }
         }
         return null;
    }
    /*
     * We need to return a Composite font which has as the font in
     * its first slot one obtained from fontconfig.
     */
    private static CompositeFont getFontConfigFont(String name, int style) {

        name = name.toLowerCase();

        initFontConfigFonts(false);

        FcCompFont fcInfo = null;
        for (int i=0; i<fontConfigFonts.length; i++) {
            if (name.equals(fontConfigFonts[i].fcFamily) &&
                style == fontConfigFonts[i].style) {
                fcInfo = fontConfigFonts[i];
                break;
            }
        }
        if (fcInfo == null) {
            fcInfo = fontConfigFonts[0];
        }

        if (logging) {
            logger.info("FC name=" + name + " style=" + style + " uses " +
                        fcInfo.firstFont.familyName +
                        " in file: " + fcInfo.firstFont.fontFile);
        }

        if (fcInfo.compFont != null) {
            return fcInfo.compFont;
        }

        /* jdkFont is going to be used for slots 1..N and as a fallback.
         * Slot 0 will be the physical font from fontconfig.
         */
        CompositeFont jdkFont = (CompositeFont)
            findFont2D(fcInfo.jdkName, style, LOGICAL_FALLBACK);

        if (fcInfo.firstFont.familyName == null ||
            fcInfo.firstFont.fontFile == null) {
            return (fcInfo.compFont = jdkFont);
        }

        /* First, see if the family and exact style is already registered.
         * If it is, use it. If it's not, then try to register it.
         * If that registration fails (signalled by null) just return the
         * regular JDK composite.
         * Algorithmically styled fonts won't match on exact style, so
         * will fall through this code, but the regisration code will
         * find that file already registered and return its font.
         */
        FontFamily family = FontFamily.getFamily(fcInfo.firstFont.familyName);
        PhysicalFont physFont = null;
        if (family != null) {
            Font2D f2D = family.getFontWithExactStyleMatch(fcInfo.style);
            if (f2D instanceof PhysicalFont) {
                physFont = (PhysicalFont)f2D;
            }
        }

        if (physFont == null ||
            !fcInfo.firstFont.fontFile.equals(physFont.platName)) {
            physFont = registerFromFcInfo(fcInfo);
            if (physFont == null) {
                return (fcInfo.compFont = jdkFont);
            }
            family = FontFamily.getFamily(physFont.getFamilyName(null));
        }

        /* Now register the fonts in the family (the other styles) after
         * checking that they aren't already registered and are actually in
         * a different file. They may be the same file in CJK cases.
         * For cases where they are different font files - eg as is common for
         * Latin fonts, then we rely on fontconfig to report these correctly.
         * Assume that all styles of this font are found by fontconfig,
         * so we can find all the family members which must be registered
         * together to prevent synthetic styling.
         */
        for (int i=0; i<fontConfigFonts.length; i++) {
            FcCompFont fc = fontConfigFonts[i];
            if (fc != fcInfo &&
                physFont.getFamilyName(null).equals(fc.firstFont.familyName) &&
                !fc.firstFont.fontFile.equals(physFont.platName) &&
                family.getFontWithExactStyleMatch(fc.style) == null) {

                registerFromFcInfo(fontConfigFonts[i]);
            }
        }

        /* Now we have a physical font. We will back this up with the JDK
         * logical font (sansserif, serif, or monospaced) that corresponds
         * to the Pango/GTK/FC logical font name.
         */
        return (fcInfo.compFont = new CompositeFont(physFont, jdkFont));
    }

    /* This is called by Swing passing in a fontconfig family name
     * such as "sans". In return Swing gets a FontUIResource instance
     * that has queried fontconfig to resolve the font(s) used for this.
     * Fontconfig will if asked return a list of fonts to give the largest
     * possible code point coverage.
     * For now we use only the first font returned by fontconfig, and
     * back it up with the most closely matching JDK logical font.
     * Essentially this means pre-pending what we return now with fontconfig's
     * preferred physical font. This could lead to some duplication in cases,
     * if we already included that font later. We probably should remove such
     * duplicates, but it is not a significant problem. It can be addressed
     * later as part of creating a Composite which uses more of the
     * same fonts as fontconfig. At that time we also should pay more
     * attention to the special rendering instructions fontconfig returns,
     * such as whether we should prefer embedded bitmaps over antialiasing.
     * There's no way to express that via a Font at present.
     */
    public static FontUIResource getFontConfigFUIR(String fcFamily,
                                                   int style, int size) {

        String mappedName = mapFcName(fcFamily);
        if (mappedName == null) {
            mappedName = "sansserif";
        }

        /* If GTK L&F were to be used on windows, we need to return
         * something. Since on windows Swing won't have the code to
         * call fontconfig, even if it is present, fcFamily and mapped
         * name will default to sans and therefore sansserif so this
         * should be fine.
         */
        if (isWindows) {
            return new FontUIResource(mappedName, style, size);
        }

        CompositeFont font2D = getFontConfigFont(fcFamily, style);
        if (font2D == null) { // Not expected, just a precaution.
           return new FontUIResource(mappedName, style, size);
        }

        /* The name of the font will be that of the physical font in slot,
         * but by setting the handle to that of the CompositeFont it
         * renders as that CompositeFont.
         * It also needs to be marked as a created font which is the
         * current mechanism to signal that deriveFont etc must copy
         * the handle from the original font.
         */
        FontUIResource fuir =
            new FontUIResource(font2D.getFamilyName(null), style, size);
        setFont2D(fuir, font2D.handle);
        setCreatedFont(fuir);
        return fuir;
    }

    /* The following fields and methods which relate to layout
     * perhaps belong in some other class but FontManager is already
     * widely used as an entry point for other JDK code that needs
     * access to the font system internals.
     */

    /**
     * Referenced by code in the JDK which wants to test for the
     * minimum char code for which layout may be required.
     * Note that even basic latin text can benefit from ligatures,
     * eg "ffi" but we presently apply those only if explicitly
     * requested with TextAttribute.LIGATURES_ON.
     * The value here indicates the lowest char code for which failing
     * to invoke layout would prevent acceptable rendering.
     */
    public static final int MIN_LAYOUT_CHARCODE = 0x0300;

    /**
     * Referenced by code in the JDK which wants to test for the
     * maximum char code for which layout may be required.
     * Note this does not account for supplementary characters
     * where the caller interprets 'layout' to mean any case where
     * one 'char' (ie the java type char) does not map to one glyph
     */
    public static final int MAX_LAYOUT_CHARCODE = 0x206F;

    /* If the character code falls into any of a number of unicode ranges
     * where we know that simple left->right layout mapping chars to glyphs
     * 1:1 and accumulating advances is going to produce incorrect results,
     * we want to know this so the caller can use a more intelligent layout
     * approach. A caller who cares about optimum performance may want to
     * check the first case and skip the method call if its in that range.
     * Although there's a lot of tests in here, knowing you can skip
     * CTL saves a great deal more. The rest of the checks are ordered
     * so that rather than checking explicitly if (>= start & <= end)
     * which would mean all ranges would need to be checked so be sure
     * CTL is not needed, the method returns as soon as it recognises
     * the code point is outside of a CTL ranges.
     * NOTE: Since this method accepts an 'int' it is asssumed to properly
     * represent a CHARACTER. ie it assumes the caller has already
     * converted surrogate pairs into supplementary characters, and so
     * can handle this case and doesn't need to be told such a case is
     * 'complex'.
     */
    static boolean isComplexCharCode(int code) {

        if (code < MIN_LAYOUT_CHARCODE || code > MAX_LAYOUT_CHARCODE) {
            return false;
        }
        else if (code <= 0x036f) {
            // Trigger layout for combining diacriticals 0x0300->0x036f
            return true;
        }
        else if (code < 0x0590) {
            // No automatic layout for Greek, Cyrillic, Armenian.
             return false;
        }
        else if (code <= 0x06ff) {
            // Hebrew 0590 - 05ff
            // Arabic 0600 - 06ff
            return true;
        }
        else if (code < 0x0900) {
            return false; // Syriac and Thaana
        }
        else if (code <= 0x0e7f) {
            // if Indic, assume shaping for conjuncts, reordering:
            // 0900 - 097F Devanagari
            // 0980 - 09FF Bengali
            // 0A00 - 0A7F Gurmukhi
            // 0A80 - 0AFF Gujarati
            // 0B00 - 0B7F Oriya
            // 0B80 - 0BFF Tamil
            // 0C00 - 0C7F Telugu
            // 0C80 - 0CFF Kannada
            // 0D00 - 0D7F Malayalam
            // 0D80 - 0DFF Sinhala
            // 0E00 - 0E7F if Thai, assume shaping for vowel, tone marks
            return true;
        }
        else if (code < 0x1780) {
            return false;
        }
        else if (code <= 0x17ff) { // 1780 - 17FF Khmer
            return true;
        }
        else if (code < 0x200c) {
            return false;
        }
        else if (code <= 0x200d) { //  zwj or zwnj
            return true;
        }
        else if (code >= 0x202a && code <= 0x202e) { // directional control
            return true;
        }
        else if (code >= 0x206a && code <= 0x206f) { // directional control
            return true;
        }
        return false;
    }

    /* This is almost the same as the method above, except it takes a
     * char which means it may include undecoded surrogate pairs.
     * The distinction is made so that code which needs to identify all
     * cases in which we do not have a simple mapping from
     * char->unicode character->glyph can be be identified.
     * For example measurement cannot simply sum advances of 'chars',
     * the caret in editable text cannot advance one 'char' at a time, etc.
     * These callers really are asking for more than whether 'layout'
     * needs to be run, they need to know if they can assume 1->1
     * char->glyph mapping.
     */
    static boolean isNonSimpleChar(char ch) {
        return
            isComplexCharCode(ch) ||
            (ch >= CharToGlyphMapper.HI_SURROGATE_START &&
             ch <= CharToGlyphMapper.LO_SURROGATE_END);
    }

    /**
     * If there is anything in the text which triggers a case
     * where char->glyph does not map 1:1 in straightforward
     * left->right ordering, then this method returns true.
     * Scripts which might require it but are not treated as such
     * due to JDK implementations will not return true.
     * ie a 'true' return is an indication of the treatment by
     * the implementation.
     * Whether supplementary characters should be considered is dependent
     * on the needs of the caller. Since this method accepts the 'char' type
     * then such chars are always represented by a pair. From a rendering
     * perspective these will all (in the cases I know of) still be one
     * unicode character -> one glyph. But if a caller is using this to
     * discover any case where it cannot make naive assumptions about
     * the number of chars, and how to index through them, then it may
     * need the option to have a 'true' return in such a case.
     */
    public static boolean isComplexText(char [] chs, int start, int limit) {

        for (int i = start; i < limit; i++) {
            if (chs[i] < MIN_LAYOUT_CHARCODE) {
                continue;
            }
            else if (isNonSimpleChar(chs[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Used by windows printing to assess if a font is likely to
     * be layout compatible with JDK
     * TrueType fonts should be, but if they have no GPOS table,
     * but do have a GSUB table, then they are probably older
     * fonts GDI handles differently.
     */
    public static boolean textLayoutIsCompatible(Font font) {

        Font2D font2D = FontManager.getFont2D(font);
        if (font2D instanceof TrueTypeFont) {
            TrueTypeFont ttf = (TrueTypeFont)font2D;
            return
                ttf.getDirectoryEntry(TrueTypeFont.GSUBTag) == null ||
                ttf.getDirectoryEntry(TrueTypeFont.GPOSTag) != null;
        } else {
            return false;
        }
    }

    private static FontScaler nullScaler = null;
    private static Constructor<FontScaler> scalerConstructor = null;

    //Find preferred font scaler
    //
    //NB: we can allow property based preferences
    //   (theoretically logic can be font type specific)
    static {
        Class scalerClass = null;
        Class arglst[] = new Class[] {Font2D.class, int.class,
        boolean.class, int.class};

        try {
            if (SunGraphicsEnvironment.isOpenJDK()) {
                scalerClass = Class.forName("sun.font.FreetypeFontScaler");
            } else {
                scalerClass = Class.forName("sun.font.T2KFontScaler");
            }
        } catch (ClassNotFoundException e) {
                scalerClass = NullFontScaler.class;
        }

        //NB: rewrite using factory? constructor is ugly way
        try {
            scalerConstructor = scalerClass.getConstructor(arglst);
        } catch (NoSuchMethodException e) {
            //should not happen
        }
    }

    /* At the moment it is harmless to create 2 null scalers
       so, technically, syncronized keyword is not needed.

       But it is safer to keep it to avoid subtle problems if we will be
       adding checks like whether scaler is null scaler. */
    public synchronized static FontScaler getNullScaler() {
        if (nullScaler == null) {
            nullScaler = new NullFontScaler();
        }
        return nullScaler;
    }

    /* This is the only place to instantiate new FontScaler.
     * Therefore this is very convinient place to register
     * scaler with Disposer as well as trigger deregistring bad font
     * in case when scaler reports this.
     */

    public static FontScaler getScaler(Font2D font,
                                       int indexInCollection,
                                       boolean supportsCJK,
                                       int filesize) {
        FontScaler scaler = null;

        try {
            Object args[] = new Object[] {font, indexInCollection,
                                          supportsCJK, filesize};
            scaler = scalerConstructor.newInstance(args);
            Disposer.addObjectRecord(font, scaler);
        } catch (Throwable e) {
            scaler = nullScaler;

            //if we can not instantiate scaler assume bad font
            //NB: technically it could be also because of internal scaler
            //    error but here we are assuming scaler is ok.
            deRegisterBadFont(font);
        }
        return scaler;
    }
}
