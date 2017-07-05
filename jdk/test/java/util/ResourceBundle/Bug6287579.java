/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
/*
 * @test
 * @bug 6287579
 * @summary Make sure that getContents() of ListResourceBundle subclasses is 'protected'
 *          and returns a different Object[]][] instance in each invocation.
 */

import java.lang.reflect.*;
import java.util.*;

public class Bug6287579 {
    static final Locale ROOT = new Locale("");

    static final String[] baseNames = {
        "sun.text.resources.BreakIteratorInfo",
        "sun.text.resources.FormatData",
        "sun.text.resources.CollationData",
        "sun.util.resources.LocaleNames",
        "sun.util.resources.TimeZoneNames",

        // Make sure the properties-to-class conversion tool generates
        // the proper getContents().
        "sun.awt.resources.awt",
    };

    public static void main(String[] args) throws Exception {
        int errors = 0;

        List<Locale> locales = new ArrayList<Locale>();
        locales.addAll(Arrays.asList(Locale.getAvailableLocales()));
        locales.add(ROOT);

        for (Locale locale : locales) {
            for (String base : baseNames) {
                String className = getResourceName(base, locale);
                errors += checkGetContents(className);
            }
        }
        if (errors > 0) {
            throw new RuntimeException(errors + " errors found");
        }
    }

    static int checkGetContents(String className) throws Exception {
        int err = 0;
        try {
            Class clazz = Class.forName(className);
            Method getContentsMethod = clazz.getDeclaredMethod("getContents",
                                                               (Class[]) null);
            if (!Modifier.isProtected(getContentsMethod.getModifiers())) {
                System.err.println(className + ": not protected");
                err++;
            }
            getContentsMethod.setAccessible(true);
            Object bundle = clazz.newInstance();
            Object o1 = getContentsMethod.invoke(bundle, (Object[]) null);
            Object o2 = getContentsMethod.invoke(bundle, (Object[]) null);
            if (o1 == o2) {
                System.err.println(className + ": same instance returned");
                err++;
            }
        } catch (ClassNotFoundException ce) {
            // Skip nonexistent classes
        } catch (NoSuchMethodException me) {
            System.out.println(className + ": no declared getContents()");
        }
        return err;
    }

    static String getResourceName(String base, Locale locale) {
        if (locale.equals(ROOT)) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append('_').append(locale.getLanguage());
        if (locale.getCountry().length() > 0
            || locale.getVariant().length() > 0) {
            sb.append('_').append(locale.getCountry());
        }
        if (locale.getVariant().length() > 0) {
            sb.append('_').append(locale.getVariant());
        }
        return sb.toString();
    }
}
