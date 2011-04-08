/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package sun.util.resources;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import sun.util.LocaleDataMetaInfo;

/**
 * Provides information about and access to resource bundles in the
 * sun.text.resources and sun.util.resources package.
 *
 * @author Asmus Freytag
 * @author Mark Davis
 */

public class LocaleData {

    private static final String localeDataJarName = "localedata.jar";

    /**
     * Lazy load available locales.
     */
    private static class AvailableLocales {
         static final Locale[] localeList = createLocaleList();
    }

    /**
     * Returns a list of the installed locales. Currently, this simply returns
     * the list of locales for which a sun.text.resources.FormatData bundle
     * exists. This bundle family happens to be the one with the broadest
     * locale coverage in the JRE.
     */
    public static Locale[] getAvailableLocales() {
        return AvailableLocales.localeList.clone();
    }

    /**
     * Gets a calendar data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static ResourceBundle getCalendarData(Locale locale) {
        return getBundle("sun.util.resources.CalendarData", locale);
    }

    /**
     * Gets a currency names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static OpenListResourceBundle getCurrencyNames(Locale locale) {
        return (OpenListResourceBundle)getBundle("sun.util.resources.CurrencyNames", locale);
    }

    /**
     * Gets a locale names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static OpenListResourceBundle getLocaleNames(Locale locale) {
        return (OpenListResourceBundle)getBundle("sun.util.resources.LocaleNames", locale);
    }

    /**
     * Gets a time zone names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static OpenListResourceBundle getTimeZoneNames(Locale locale) {
        return (OpenListResourceBundle)getBundle("sun.util.resources.TimeZoneNames", locale);
    }

    /**
     * Gets a collation data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static ResourceBundle getCollationData(Locale locale) {
        return getBundle("sun.text.resources.CollationData", locale);
    }

    /**
     * Gets a date format data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static ResourceBundle getDateFormatData(Locale locale) {
        return getBundle("sun.text.resources.FormatData", locale);
    }

    /**
     * Gets a number format data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public static ResourceBundle getNumberFormatData(Locale locale) {
        return getBundle("sun.text.resources.FormatData", locale);
    }

    private static ResourceBundle getBundle(final String baseName, final Locale locale) {
        return (ResourceBundle) AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    return ResourceBundle.
                        getBundle(baseName, locale,
                                  LocaleDataResourceBundleControl.getRBControlInstance());
                }
            });
    }

    static class LocaleDataResourceBundleControl extends ResourceBundle.Control {
        /* Singlton instance of ResourceBundle.Control. */
        private static LocaleDataResourceBundleControl rbControlInstance =
            new LocaleDataResourceBundleControl();

        public static LocaleDataResourceBundleControl getRBControlInstance() {
            return rbControlInstance;
        }

        /*
         * This method overrides the default implementation to search
         * from a prebaked locale string list to determin the candidate
         * locale list.
         *
         * @param baseName the resource bundle base name.
         *        locale   the requested locale for the resource bundle.
         * @returns a list of candidate locales to search from.
         * @exception NullPointerException if baseName or locale is null.
         */
        @Override
         public List<Locale> getCandidateLocales(String baseName, Locale locale) {
            List<Locale> candidates = super.getCandidateLocales(baseName, locale);
            /* Get the locale string list from LocaleDataMetaInfo class. */
            String localeString = LocaleDataMetaInfo.getSupportedLocaleString(baseName);

      if (localeString.length() == 0) {
                return candidates;
            }

            for (Iterator<Locale> l = candidates.iterator(); l.hasNext(); ) {
                Locale loc = l.next();
                String lstr = null;
                if (loc.getScript().length() > 0) {
                    lstr = loc.toLanguageTag().replace('-', '_');
                } else {
                    lstr = loc.toString();
                    int idx = lstr.indexOf("_#");
                    if (idx >= 0) {
                        lstr = lstr.substring(0, idx);
                    }
                }
                /* Every locale string in the locale string list returned from
                   the above getSupportedLocaleString is enclosed
                   within two white spaces so that we could check some locale
                   such as "en".
                */
                if (lstr.length() != 0 && localeString.indexOf(" " + lstr + " ") == -1) {
                    l.remove();
                }
            }
            return candidates;
        }

        /*
         * Overrides "getFallbackLocale" to return null so
         * that the fallback locale will be null.
         * @param baseName the resource bundle base name.
         *        locale   the requested locale for the resource bundle.
         * @return null for the fallback locale.
         * @exception NullPointerException if baseName or locale is null.
         */
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            if (baseName == null || locale == null) {
                throw new NullPointerException();
            }
            return null;
        }
    }

    /*
     * Returns true if the non European resources jar file exists in jre
     * extension directory.
     * @returns true if the jar file is there. Otherwise, returns false.
     */
    private static boolean isNonEuroLangSupported() {
        final String sep = File.separator;
        String localeDataJar =
            java.security.AccessController.doPrivileged(
             new sun.security.action.GetPropertyAction("java.home")) +
            sep + "lib" + sep + "ext" + sep + localeDataJarName;

        /* Peek at the installed extension directory to see if
           localedata.jar is installed or not.
        */
        final File f = new File(localeDataJar);
        boolean isNonEuroResJarExist =
            AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                    public Boolean run() {
                        return Boolean.valueOf(f.exists());
                    }
                }).booleanValue();

        return isNonEuroResJarExist;
    }

    /*
     * This method gets the locale string list from LocaleDataMetaInfo class and
     * then contructs the Locale array based on the locale string returned above.
     * @returns the Locale array for the supported locale of JRE.
     *
     */
    private static Locale[] createLocaleList() {
        String supportedLocaleString = LocaleDataMetaInfo.
            getSupportedLocaleString("sun.text.resources.FormatData");

        if (supportedLocaleString.length() == 0) {
            return null;
        }

        /* Look for "|" and construct a new locale string list. */
        int barIndex = supportedLocaleString.indexOf("|");
        StringTokenizer localeStringTokenizer = null;
        if (isNonEuroLangSupported()) {
            localeStringTokenizer = new
                StringTokenizer(supportedLocaleString.substring(0, barIndex) +
                                supportedLocaleString.substring(barIndex + 1));
        } else {
            localeStringTokenizer = new
                StringTokenizer(supportedLocaleString.substring(0, barIndex));
        }

        Locale[] locales = new Locale[localeStringTokenizer.countTokens()];
        for (int i = 0; i < locales.length; i++) {
            String currentToken = localeStringTokenizer.nextToken().replace('_','-');
            if (currentToken.equals("ja-JP-JP")) {
                currentToken = "ja-JP-u-ca-japanese-x-lvariant-JP";
            } else if (currentToken.equals("th-TH-TH")) {
                currentToken = "th-TH-u-nu-thai-x-lvariant-TH";
            } else if (currentToken.equals("no-NO-NY")) {
                currentToken = "no-NO-x-lvariant-NY";
            }
            locales[i] = Locale.forLanguageTag(currentToken);
        }
        return locales;
    }

}
