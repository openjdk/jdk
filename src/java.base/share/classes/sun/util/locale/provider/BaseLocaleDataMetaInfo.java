/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This class contains a map which records the locale list string for
 * each resource in sun.util.resources & sun.text.resources.
 * It is used to avoid loading non-existent localized resources so that
 * jar files won't be opened unnecessarily to look up them.
 */
package sun.util.locale.provider;

import java.util.HashMap;
import java.util.Map;
import static sun.util.locale.provider.LocaleProviderAdapter.Type;

public class BaseLocaleDataMetaInfo implements LocaleDataMetaInfo {

    private static final Map<String, String> resourceNameToLocales = HashMap.newHashMap(9);

    static {
        resourceNameToLocales.put("FormatData",
                                  "  en en-US ");

        resourceNameToLocales.put("CollationData",
                                  "  ");

        resourceNameToLocales.put("BreakIteratorInfo",
                                  "  ");

        resourceNameToLocales.put("BreakIteratorRules",
                                  "  ");

        resourceNameToLocales.put("TimeZoneNames",
                                  "  en ");

        resourceNameToLocales.put("LocaleNames",
                                  "  en ");

        resourceNameToLocales.put("CurrencyNames",
                                  "  en-US ");

        resourceNameToLocales.put("CalendarData",
                                  "  en ");

        resourceNameToLocales.put("AvailableLocales",
                                  " en en-US ");
    }

    /*
     * Gets the supported locales string based on the availability of
     * locale data resource bundles for each resource name.
     *
     * @param resourceName the resource name
     * @return the supported locale string for the passed in resource.
     */
    public static String getSupportedLocaleString(String resourceName) {
        return resourceNameToLocales.getOrDefault(resourceName, "");
    }

    @Override
    public Type getType() {
        return Type.JRE;
    }

    @Override
    public String availableLanguageTags(String category) {
        return getSupportedLocaleString(category);
    }
}
