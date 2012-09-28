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

package sun.font;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.plaf.FontUIResource;

import sun.awt.FontConfiguration;
import sun.awt.HeadlessToolkit;
import sun.lwawt.macosx.*;

public class CFontManager extends SunFontManager {
    private FontConfigManager fcManager = null;
    private static Hashtable<String, Font2D> genericFonts = new Hashtable<String, Font2D>();

    @Override
    protected FontConfiguration createFontConfiguration() {
        FontConfiguration fc = new CFontConfiguration(this);
        fc.init();
        return fc;
    }

    @Override
    public FontConfiguration createFontConfiguration(boolean preferLocaleFonts,
                                                     boolean preferPropFonts)
    {
        return new CFontConfiguration(this, preferLocaleFonts, preferPropFonts);
    }

    private static String[] defaultPlatformFont = null;

    /*
     * Returns an array of two strings. The first element is the
     * name of the font. The second element is the file name.
     */
    @Override
    public synchronized String[] getDefaultPlatformFont() {
        if (defaultPlatformFont == null) {
            defaultPlatformFont = new String[2];
            defaultPlatformFont[0] = "Lucida Grande";
            defaultPlatformFont[1] = "/System/Library/Fonts/LucidaGrande.ttc";
        }
        return defaultPlatformFont;
    }

    // This is a way to register any kind of Font2D, not just files and composites.
    public static Font2D[] getGenericFonts() {
        return (Font2D[])genericFonts.values().toArray(new Font2D[0]);
    }

    public Font2D registerGenericFont(Font2D f)
    {
        return registerGenericFont(f, false);
    }
    public Font2D registerGenericFont(Font2D f, boolean logicalFont)
    {
        int rank = 4;

        String fontName = f.fullName;
        String familyName = f.familyName;

        if (fontName == null || "".equals(fontName)) {
            return null;
        }

        // logical fonts always need to be added to the family
        // plus they never need to be added to the generic font list
        // or the fullNameToFont table since they are covers for
        // already existing fonts in this list
        if (logicalFont || !genericFonts.containsKey(fontName)) {
            if (FontUtilities.debugFonts()) {
                FontUtilities.getLogger().info("Add to Family "+familyName +
                    ", Font " + fontName + " rank="+rank);
            }
            FontFamily family = FontFamily.getFamily(familyName);
            if (family == null) {
                family = new FontFamily(familyName, false, rank);
                family.setFont(f, f.style);
            } else if (family.getRank() >= rank) {
                family.setFont(f, f.style);
            }
            if (!logicalFont)
            {
                genericFonts.put(fontName, f);
                fullNameToFont.put(fontName.toLowerCase(Locale.ENGLISH), f);
            }
            return f;
        } else {
            return (Font2D)genericFonts.get(fontName);
        }
    }

    @Override
    public Font2D[] getRegisteredFonts() {
        Font2D[] regFonts = super.getRegisteredFonts();

        // Add in the Mac OS X native fonts
        Font2D[] genericFonts = getGenericFonts();
        Font2D[] allFonts = new Font2D[regFonts.length+genericFonts.length];
        System.arraycopy(regFonts, 0, allFonts, 0, regFonts.length);
        System.arraycopy(genericFonts, 0, allFonts, regFonts.length, genericFonts.length);

        return allFonts;
    }

    @Override
    protected void addNativeFontFamilyNames(TreeMap<String, String> familyNames, Locale requestedLocale) {
        Font2D[] genericfonts = getGenericFonts();
        for (int i=0; i < genericfonts.length; i++) {
            if (!(genericfonts[i] instanceof NativeFont)) {
                String name = genericfonts[i].getFamilyName(requestedLocale);
                familyNames.put(name.toLowerCase(requestedLocale), name);
            }
        }
    }

    @Override
    public Font2D createFont2D(File fontFile, int fontFormat, boolean isCopy, CreatedFontTracker tracker) throws FontFormatException {

    String fontFilePath = fontFile.getPath();
    Font2D font2D = null;
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
                    new java.security.PrivilegedAction<Object>() {
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
        FileFont.setFileToRemove(font2D, fontFile, tracker);
        synchronized (FontManager.class) {

            if (tmpFontFiles == null) {
                tmpFontFiles = new Vector<File>();
            }
            tmpFontFiles.add(fontFile);

            if (fileCloser == null) {
                final Runnable fileCloserRunnable = new Runnable() {
                    public void run() {
                        java.security.AccessController.doPrivileged(
                                new java.security.PrivilegedAction<Object>() {
                                    public Object run() {

                                        for (int i=0;i<CHANNELPOOLSIZE;i++) {
                                            if (fontFileCache[i] != null) {
                                                try {
                                                    fontFileCache[i].close();
                                                } catch (Exception e) {}
                                            }
                                        }
                                        if (tmpFontFiles != null) {
                                            File[] files = new File[tmpFontFiles.size()];
                                            files = tmpFontFiles.toArray(files);
                                            for (int f=0; f<files.length;f++) {
                                                try {
                                                    files[f].delete();
                                                } catch (Exception e) {}
                                            }
                                        }
                                        return null;
                                    }
                                });
                    }
                };
                java.security.AccessController.doPrivileged(
                        new java.security.PrivilegedAction<Object>() {
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
                                fileCloser.setContextClassLoader(null);
                                Runtime.getRuntime().addShutdownHook(fileCloser);
                                return null;
                            }
                        });
                }
            }
        }
        return font2D;
    }

    /*
    public synchronized FontConfigManager getFontConfigManager() {
        if (fcManager  == null) {
            fcManager = new FontConfigManager();
        }
        return fcManager;
    }
    */

    protected void registerFontsInDir(String dirName, boolean useJavaRasterizer, int fontRank, boolean defer, boolean resolveSymLinks) {
        loadNativeDirFonts(dirName);
        super.registerFontsInDir(dirName, useJavaRasterizer, fontRank, defer, resolveSymLinks);
    }

    private native void loadNativeDirFonts(String dirName);
    private native void loadNativeFonts();

    void registerFont(String fontName, String fontFamilyName) {
        final CFont font = new CFont(fontName, fontFamilyName);

        registerGenericFont(font);

        if ((font.getStyle() & Font.ITALIC) == 0) {
            registerGenericFont(font.createItalicVariant(), true);
        }
    }

    Object waitForFontsToBeLoaded  = new Object();
    public void loadFonts()
    {
        synchronized(waitForFontsToBeLoaded)
        {
            super.loadFonts();
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<Object>() {
                    public Object run() {
                        loadNativeFonts();
                        return null;
                    }
                }
            );

            String defaultFont = "Lucida Grande";
            String defaultFallback = "Lucida Sans";

            setupLogicalFonts("Dialog", defaultFont, defaultFallback);
            setupLogicalFonts("Serif", "Times", "Lucida Bright");
            setupLogicalFonts("SansSerif", defaultFont, defaultFallback);
            setupLogicalFonts("Monospaced", "Menlo", "Lucida Sans Typewriter");
            setupLogicalFonts("DialogInput", defaultFont, defaultFallback);
        }
    }

    protected void setupLogicalFonts(String logicalName, String realName, String fallbackName) {
        FontFamily realFamily = getFontFamilyWithExtraTry(logicalName, realName, fallbackName);

        cloneStyledFont(realFamily, logicalName, Font.PLAIN);
        cloneStyledFont(realFamily, logicalName, Font.BOLD);
        cloneStyledFont(realFamily, logicalName, Font.ITALIC);
        cloneStyledFont(realFamily, logicalName, Font.BOLD | Font.ITALIC);
    }

    protected FontFamily getFontFamilyWithExtraTry(String logicalName, String realName, String fallbackName){
        FontFamily family = getFontFamily(realName, fallbackName);
        if (family != null) return family;

        // at this point, we recognize that we probably needed a fallback font
        super.loadFonts();

        family = getFontFamily(realName, fallbackName);
        if (family != null) return family;

        System.err.println("Warning: the fonts \"" + realName + "\" and \"" + fallbackName + "\" are not available for the Java logical font \"" + logicalName + "\", which may have unexpected appearance or behavior. Re-enable the \""+ realName +"\" font to remove this warning.");
        return null;
    }

    protected FontFamily getFontFamily(String realName, String fallbackName){
        FontFamily family = FontFamily.getFamily(realName);
        if (family != null) return family;

        family = FontFamily.getFamily(fallbackName);
        if (family != null){
            System.err.println("Warning: the font \"" + realName + "\" is not available, so \"" + fallbackName + "\" has been substituted, but may have unexpected appearance or behavor. Re-enable the \""+ realName +"\" font to remove this warning.");
            return family;
        }

        return null;
    }

    protected boolean cloneStyledFont(FontFamily realFamily, String logicalFamilyName, int style) {
        if (realFamily == null) return false;

        Font2D realFont = realFamily.getFontWithExactStyleMatch(style);
        if (realFont == null || !(realFont instanceof CFont)) return false;

        CFont newFont = new CFont((CFont)realFont, logicalFamilyName);
        registerGenericFont(newFont, true);

        return true;
    }

    @Override
    public String getFontPath(boolean noType1Fonts) {
        // In the case of the Cocoa toolkit, since we go through NSFont, we dont need to register /Library/Fonts
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (tk instanceof HeadlessToolkit) {
            tk = ((HeadlessToolkit)tk).getUnderlyingToolkit();
        }
        if (tk instanceof LWCToolkit) {
            return "";
        }

        // X11 case
        return "/Library/Fonts";
    }

    @Override
    protected FontUIResource getFontConfigFUIR(
            String family, int style, int size)
    {
        String mappedName = FontUtilities.mapFcName(family);
        if (mappedName == null) {
            mappedName = "sansserif";
        }
        return new FontUIResource(mappedName, style, size);
    }

    // Only implemented on Windows
    @Override
    protected void populateFontFileNameMap(HashMap<String, String> fontToFileMap, HashMap<String, String> fontToFamilyNameMap,
            HashMap<String, ArrayList<String>> familyToFontListMap, Locale locale) {}
}
