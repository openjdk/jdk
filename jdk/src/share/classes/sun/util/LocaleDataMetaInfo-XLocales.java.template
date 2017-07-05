/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#warn This file is preprocessed before being compiled

/*
 * This class contains a map which records the locale list string for
 * each resource in sun.util.resources & sun.text.resources.
 * It is used to avoid loading non-existent localized resources so that
 * jar files won't be opened unnecessary to look up them.
 *
 * @since 1.6
 */
package sun.util;

import java.util.HashMap;


public class LocaleDataMetaInfo {

    private static final HashMap<String, String> resourceNameToLocales =
        new HashMap<String, String>(6);


    static {
        /* During JDK build time, #XXX_YYY# will be replaced by a string contain all the locales
           supported by the resource.

           Don't remove the space character between " and #. That is put there purposely so that
           look up locale string such as "en" could be based on if it contains " en ".
        */
        resourceNameToLocales.put("sun.text.resources.FormatData",
                                  " #FormatData_EuroLocales# | #FormatData_NonEuroLocales# ");

        resourceNameToLocales.put("sun.text.resources.CollationData",
                                  " #CollationData_EuroLocales# | #CollationData_NonEuroLocales# ");

        resourceNameToLocales.put("sun.util.resources.TimeZoneNames",
                                  " #TimeZoneNames_EuroLocales# | #TimeZoneNames_NonEuroLocales# ");

        resourceNameToLocales.put("sun.util.resources.LocaleNames",
                                  " #LocaleNames_EuroLocales# | #LocaleNames_NonEuroLocales# ");

        resourceNameToLocales.put("sun.util.resources.CurrencyNames",
                                  " #CurrencyNames_EuroLocales# | #CurrencyNames_NonEuroLocales# ");

        resourceNameToLocales.put("sun.util.resources.CalendarData",
                                  " #CalendarData_EuroLocales# | #CalendarData_NonEuroLocales# ");
    }

    /*
     * @param resourceName the resource name
     * @return the supported locale string for the passed in resource.
     */
    public static String getSupportedLocaleString(String resourceName) {

        return resourceNameToLocales.get(resourceName);
    }

}
