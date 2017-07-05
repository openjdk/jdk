/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import sun.util.locale.provider.LocaleProviderAdapter;
import static sun.util.locale.provider.LocaleProviderAdapter.Type.JRE;
import sun.util.locale.provider.LocaleDataMetaInfo;

/**
 * Provides information about and access to resource bundles in the
 * sun.text.resources and sun.util.resources packages or in their corresponding
 * packages for CLDR.
 *
 * @author Asmus Freytag
 * @author Mark Davis
 */

public class LocaleData {
    private final LocaleProviderAdapter.Type type;

    public LocaleData(LocaleProviderAdapter.Type type) {
        this.type = type;
    }

    /**
     * Gets a calendar data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public ResourceBundle getCalendarData(Locale locale) {
        return getBundle(type.getUtilResourcesPackage() + ".CalendarData", locale);
    }

    /**
     * Gets a currency names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public OpenListResourceBundle getCurrencyNames(Locale locale) {
        return (OpenListResourceBundle) getBundle(type.getUtilResourcesPackage() + ".CurrencyNames", locale);
    }

    /**
     * Gets a locale names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public OpenListResourceBundle getLocaleNames(Locale locale) {
        return (OpenListResourceBundle) getBundle(type.getUtilResourcesPackage() + ".LocaleNames", locale);
    }

    /**
     * Gets a time zone names resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public OpenListResourceBundle getTimeZoneNames(Locale locale) {
        return (OpenListResourceBundle) getBundle(type.getUtilResourcesPackage() + ".TimeZoneNames", locale);
    }

    /**
     * Gets a collation data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public ResourceBundle getCollationData(Locale locale) {
        return getBundle(type.getTextResourcesPackage() + ".CollationData", locale);
    }

    /**
     * Gets a date format data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public ResourceBundle getDateFormatData(Locale locale) {
        return getBundle(type.getTextResourcesPackage() + ".FormatData", locale);
    }

    /**
     * Gets a number format data resource bundle, using privileges
     * to allow accessing a sun.* package.
     */
    public ResourceBundle getNumberFormatData(Locale locale) {
        return getBundle(type.getTextResourcesPackage() + ".FormatData", locale);
    }

    public static ResourceBundle getBundle(final String baseName, final Locale locale) {
        return AccessController.doPrivileged(new PrivilegedAction<ResourceBundle>() {
                @Override
                public ResourceBundle run() {
                    return ResourceBundle.
                        getBundle(baseName, locale,
                                  LocaleDataResourceBundleControl.getRBControlInstance());
                }
            });
    }

    private static class LocaleDataResourceBundleControl extends ResourceBundle.Control {
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

            if (localeString == null || localeString.length() == 0) {
                return candidates;
            }

            for (Iterator<Locale> l = candidates.iterator(); l.hasNext(); ) {
                Locale loc = l.next();
                String lstr;
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

        private static final String CLDR      = ".cldr";

        /**
         * Changes baseName to its per-language package name and
         * calls the super class implementation. For example,
         * if the baseName is "sun.text.resources.FormatData" and locale is ja_JP,
         * the baseName is changed to "sun.text.resources.ja.FormatData". If
         * baseName contains "cldr", such as "sun.text.resources.cldr.FormatData",
         * the name is changed to "sun.text.resources.cldr.jp.FormatData".
         */
        @Override
        public String toBundleName(String baseName, Locale locale) {
            String newBaseName = baseName;
            String lang = locale.getLanguage();
            if (lang.length() > 0) {
                if (baseName.startsWith(JRE.getUtilResourcesPackage())
                        || baseName.startsWith(JRE.getTextResourcesPackage())) {
                    // Assume the lengths are the same.
                    assert JRE.getUtilResourcesPackage().length()
                        == JRE.getTextResourcesPackage().length();
                    int index = JRE.getUtilResourcesPackage().length();
                    if (baseName.indexOf(CLDR, index) > 0) {
                        index += CLDR.length();
                    }
                    newBaseName = baseName.substring(0, index + 1) + lang
                                      + baseName.substring(index);
                }
            }
            return super.toBundleName(newBaseName, locale);
        }

    }
}
