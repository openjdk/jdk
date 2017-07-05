/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.motif;

import sun.awt.FontConfiguration;
import sun.awt.X11FontManager;
import sun.font.FontUtilities;
import sun.font.SunFontManager;
import sun.util.logging.PlatformLogger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Scanner;

public class MFontConfiguration extends FontConfiguration {

    private static FontConfiguration fontConfig = null;
    private static PlatformLogger logger;

    public MFontConfiguration(SunFontManager fm) {
        super(fm);
        if (FontUtilities.debugFonts()) {
            logger = PlatformLogger.getLogger("sun.awt.FontConfiguration");
        }
        initTables();
    }


    public MFontConfiguration(SunFontManager fm,
                              boolean preferLocaleFonts,
                              boolean preferPropFonts) {
        super(fm, preferLocaleFonts, preferPropFonts);
        if (FontUtilities.debugFonts()) {
            logger = PlatformLogger.getLogger("sun.awt.FontConfiguration");
        }
        initTables();
    }

    /* Needs to be kept in sync with updates in the languages used in
     * the fontconfig files.
     */
    protected void initReorderMap() {
        reorderMap = new HashMap<>();
        if (osName == null) {  /* null means SunOS */
            initReorderMapForSolaris();
        } else {
            initReorderMapForLinux();
        }
    }

    private void initReorderMapForSolaris() {
        /* Don't create a no-op entry, so we can optimize this case
         * i.e. we don't need to do anything so can avoid slower paths in
         * the code.
         */
//      reorderMap.put("UTF-8", "latin-1");
        reorderMap.put("UTF-8.hi", "devanagari"); // NB is in Lucida.
        reorderMap.put("UTF-8.ja",
                       split("japanese-x0201,japanese-x0208,japanese-x0212"));
        reorderMap.put("UTF-8.ko", "korean-johab");
        reorderMap.put("UTF-8.th", "thai");
        reorderMap.put("UTF-8.zh.TW", "chinese-big5");
        reorderMap.put("UTF-8.zh.HK", split("chinese-big5,chinese-hkscs"));
        if (FontUtilities.isSolaris8) {
            reorderMap.put("UTF-8.zh.CN", split("chinese-gb2312,chinese-big5"));
        } else {
            reorderMap.put("UTF-8.zh.CN",
                           split("chinese-gb18030-0,chinese-gb18030-1"));
        }
        reorderMap.put("UTF-8.zh",
                       split("chinese-big5,chinese-hkscs,chinese-gb18030-0,chinese-gb18030-1"));
        reorderMap.put("Big5", "chinese-big5");
        reorderMap.put("Big5-HKSCS", split("chinese-big5,chinese-hkscs"));
        if (! FontUtilities.isSolaris8 && ! FontUtilities.isSolaris9) {
            reorderMap.put("GB2312", split("chinese-gbk,chinese-gb2312"));
        } else {
            reorderMap.put("GB2312","chinese-gb2312");
        }
        reorderMap.put("x-EUC-TW",
            split("chinese-cns11643-1,chinese-cns11643-2,chinese-cns11643-3"));
        reorderMap.put("GBK", "chinese-gbk");
        reorderMap.put("GB18030",split("chinese-gb18030-0,chinese-gb18030-1"));

        reorderMap.put("TIS-620", "thai");
        reorderMap.put("x-PCK",
                       split("japanese-x0201,japanese-x0208,japanese-x0212"));
        reorderMap.put("x-eucJP-Open",
                       split("japanese-x0201,japanese-x0208,japanese-x0212"));
        reorderMap.put("EUC-KR", "korean");
        /* Don't create a no-op entry, so we can optimize this case */
//      reorderMap.put("ISO-8859-1", "latin-1");
        reorderMap.put("ISO-8859-2", "latin-2");
        reorderMap.put("ISO-8859-5", "cyrillic-iso8859-5");
        reorderMap.put("windows-1251", "cyrillic-cp1251");
        reorderMap.put("KOI8-R", "cyrillic-koi8-r");
        reorderMap.put("ISO-8859-6", "arabic");
        reorderMap.put("ISO-8859-7", "greek");
        reorderMap.put("ISO-8859-8", "hebrew");
        reorderMap.put("ISO-8859-9", "latin-5");
        reorderMap.put("ISO-8859-13", "latin-7");
        reorderMap.put("ISO-8859-15", "latin-9");
    }

    private void initReorderMapForLinux() {
        reorderMap.put("UTF-8.ja.JP", "japanese-iso10646");
        reorderMap.put("UTF-8.ko.KR", "korean-iso10646");
        reorderMap.put("UTF-8.zh.TW", "chinese-tw-iso10646");
        reorderMap.put("UTF-8.zh.HK", "chinese-tw-iso10646");
        reorderMap.put("UTF-8.zh.CN", "chinese-cn-iso10646");
        reorderMap.put("x-euc-jp-linux",
                        split("japanese-x0201,japanese-x0208"));
        reorderMap.put("GB2312", "chinese-gb18030");
        reorderMap.put("Big5", "chinese-big5");
        reorderMap.put("EUC-KR", "korean");
        if (osName.equals("Sun")){
            reorderMap.put("GB18030", "chinese-cn-iso10646");
        }
        else {
            reorderMap.put("GB18030", "chinese-gb18030");
        }
    }

    /**
     * Sets the OS name and version from environment information.
     */
    protected void setOsNameAndVersion(){
        super.setOsNameAndVersion();

        if (osName.equals("SunOS")) {
            //don't care os name on Solaris
            osName = null;
        } else if (osName.equals("Linux")) {
            try {
                File f;
                if ((f = new File("/etc/fedora-release")).canRead()) {
                    osName = "Fedora";
                    osVersion = getVersionString(f);
                } else if ((f = new File("/etc/redhat-release")).canRead()) {
                    osName = "RedHat";
                    osVersion = getVersionString(f);
                } else if ((f = new File("/etc/turbolinux-release")).canRead()) {
                    osName = "Turbo";
                    osVersion = getVersionString(f);
                } else if ((f = new File("/etc/SuSE-release")).canRead()) {
                    osName = "SuSE";
                    osVersion = getVersionString(f);
                } else if ((f = new File("/etc/lsb-release")).canRead()) {
                    /* Ubuntu and (perhaps others) use only lsb-release.
                     * Syntax and encoding is compatible with java properties.
                     * For Ubuntu the ID is "Ubuntu".
                     */
                    Properties props = new Properties();
                    props.load(new FileInputStream(f));
                    osName = props.getProperty("DISTRIB_ID");
                    osVersion =  props.getProperty("DISTRIB_RELEASE");
                }
            } catch (Exception e) {
            }
        }
        return;
    }

    /**
     * Gets the OS version string from a Linux release-specific file.
     */
    private String getVersionString(File f){
        try {
            Scanner sc  = new Scanner(f);
            return sc.findInLine("(\\d)+((\\.)(\\d)+)*");
        }
        catch (Exception e){
        }
        return null;
    }

    private static final String fontsDirPrefix = "$JRE_LIB_FONTS";

    protected String mapFileName(String fileName) {
        if (fileName != null && fileName.startsWith(fontsDirPrefix)) {
            return SunFontManager.jreFontDirName
                    + fileName.substring(fontsDirPrefix.length());
        }
        return fileName;
    }

    // overrides FontConfiguration.getFallbackFamilyName
    public String getFallbackFamilyName(String fontName, String defaultFallback) {
        // maintain compatibility with old font.properties files, which
        // either had aliases for TimesRoman & Co. or defined mappings for them.
        String compatibilityName = getCompatibilityFamilyName(fontName);
        if (compatibilityName != null) {
            return compatibilityName;
        }
        return defaultFallback;
    }

    protected String getEncoding(String awtFontName,
            String characterSubsetName) {
        // extract encoding field from XLFD
        int beginIndex = 0;
        int fieldNum = 13; // charset registry field
        while (fieldNum-- > 0 && beginIndex >= 0) {
            beginIndex = awtFontName.indexOf("-", beginIndex) + 1;
        }
        if (beginIndex == -1) {
            return "default";
        }
        String xlfdEncoding = awtFontName.substring(beginIndex);
        if (xlfdEncoding.indexOf("fontspecific") > 0) {
            if (awtFontName.indexOf("dingbats") > 0) {
                return "sun.awt.motif.X11Dingbats";
            } else if (awtFontName.indexOf("symbol") > 0) {
                return "sun.awt.Symbol";
            }
        }
        String encoding = encodingMap.get(xlfdEncoding);
        if (encoding == null) {
            encoding = "default";
        }
        return encoding;
    }

    protected Charset getDefaultFontCharset(String fontName) {
        return Charset.forName("ISO8859_1");
    }

    protected String getFaceNameFromComponentFontName(String componentFontName) {
        return null;
    }

    protected String getFileNameFromComponentFontName(String componentFontName) {
        // for X11, component font name is XLFD
        // if we have a file name already, just use it; otherwise let's see
        // what the graphics environment can provide
        String fileName = getFileNameFromPlatformName(componentFontName);
        if (fileName != null && fileName.charAt(0) == '/' &&
            !needToSearchForFile(fileName)) {
            return fileName;
        }
        return ((X11FontManager) fontManager).getFileNameFromXLFD(componentFontName);
    }

    public HashSet<String> getAWTFontPathSet() {
        HashSet<String> fontDirs = new HashSet<String>();
        short[] scripts = getCoreScripts(0);
        for (int i = 0; i< scripts.length; i++) {
            String path = getString(table_awtfontpaths[scripts[i]]);
            if (path != null) {
                int start = 0;
                int colon = path.indexOf(':');
                while (colon >= 0) {
                    fontDirs.add(path.substring(start, colon));
                    start = colon + 1;
                    colon = path.indexOf(':', start);
                }
                fontDirs.add((start == 0) ? path : path.substring(start));
            }
        }
        return fontDirs;
    }

    /* methods for table setup ***********************************************/

    private static HashMap<String, String> encodingMap = new HashMap<>();

    private void initTables() {
        // encodingMap maps XLFD encoding component to
        // name of corresponding java.nio charset
        encodingMap.put("iso8859-1", "ISO-8859-1");
        encodingMap.put("iso8859-2", "ISO-8859-2");
        encodingMap.put("iso8859-4", "ISO-8859-4");
        encodingMap.put("iso8859-5", "ISO-8859-5");
        encodingMap.put("iso8859-6", "ISO-8859-6");
        encodingMap.put("iso8859-7", "ISO-8859-7");
        encodingMap.put("iso8859-8", "ISO-8859-8");
        encodingMap.put("iso8859-9", "ISO-8859-9");
        encodingMap.put("iso8859-13", "ISO-8859-13");
        encodingMap.put("iso8859-15", "ISO-8859-15");
        encodingMap.put("gb2312.1980-0", "sun.awt.motif.X11GB2312");
        if (osName == null) {
            // use standard converter on Solaris
            encodingMap.put("gbk-0", "GBK");
        } else {
            encodingMap.put("gbk-0", "sun.awt.motif.X11GBK");
        }
        encodingMap.put("gb18030.2000-0", "sun.awt.motif.X11GB18030_0");
        encodingMap.put("gb18030.2000-1", "sun.awt.motif.X11GB18030_1");
        encodingMap.put("cns11643-1", "sun.awt.motif.X11CNS11643P1");
        encodingMap.put("cns11643-2", "sun.awt.motif.X11CNS11643P2");
        encodingMap.put("cns11643-3", "sun.awt.motif.X11CNS11643P3");
        encodingMap.put("big5-1", "Big5");
        encodingMap.put("big5-0", "Big5");
        encodingMap.put("hkscs-1", "Big5-HKSCS");
        encodingMap.put("ansi-1251", "windows-1251");
        encodingMap.put("koi8-r", "KOI8-R");
        encodingMap.put("jisx0201.1976-0", "sun.awt.motif.X11JIS0201");
        encodingMap.put("jisx0208.1983-0", "sun.awt.motif.X11JIS0208");
        encodingMap.put("jisx0212.1990-0", "sun.awt.motif.X11JIS0212");
        encodingMap.put("ksc5601.1987-0", "sun.awt.motif.X11KSC5601");
        encodingMap.put("ksc5601.1992-3", "sun.awt.motif.X11Johab");
        encodingMap.put("tis620.2533-0", "TIS-620");
        encodingMap.put("iso10646-1", "UTF-16BE");
    }

}
