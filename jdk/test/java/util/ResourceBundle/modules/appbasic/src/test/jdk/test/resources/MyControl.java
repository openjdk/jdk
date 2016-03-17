/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.resources;

import java.util.*;

public class MyControl extends ResourceBundle.Control {
    private static final Set<Locale> euLocales, asiaLocales;

    static {
        euLocales = new HashSet<>(Arrays.asList(Locale.GERMAN, Locale.FRENCH));
        asiaLocales = new HashSet<>(Arrays.asList(Locale.JAPANESE, Locale.CHINESE, Locale.TAIWAN));
    }

    @Override
    public String toBundleName(String baseName, Locale locale) {
        String bundleName = baseName;
        if (euLocales.contains(locale)) {
            bundleName = addRegion(baseName, "eu");
        } else if (asiaLocales.contains(locale)) {
            bundleName = addRegion(baseName, "asia");
        }
        return super.toBundleName(bundleName, locale);
    }

    private String addRegion(String baseName, String region) {
        int index = baseName.lastIndexOf('.');
        return baseName.substring(0, index + 1) + region + baseName.substring(index);
    }

    protected static boolean isEULocale(Locale locale) {
        return euLocales.contains(locale);
    }

    protected static boolean isAsiaLocale(Locale locale) {
        return asiaLocales.contains(locale);
    }
}
