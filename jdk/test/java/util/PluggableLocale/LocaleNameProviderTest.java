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
 *
 */

import java.text.*;
import java.util.*;
import sun.util.*;
import sun.util.resources.*;

public class LocaleNameProviderTest extends ProviderTest {

    public static void main(String[] s) {
        new LocaleNameProviderTest();
    }

    LocaleNameProviderTest() {
        com.bar.LocaleNameProviderImpl lnp = new com.bar.LocaleNameProviderImpl();
        Locale[] availloc = Locale.getAvailableLocales();
        Locale[] testloc = availloc.clone();
        List<Locale> providerloc = Arrays.asList(lnp.getAvailableLocales());

        for (Locale target: availloc) {
            // pure JRE implementation
            OpenListResourceBundle rb = LocaleData.getLocaleNames(target);
            boolean jreHasBundle = rb.getLocale().equals(target);

            for (Locale test: testloc) {
                // codes
                String lang = test.getLanguage();
                String ctry = test.getCountry();
                String vrnt = test.getVariant();

                // the localized name
                String langresult = test.getDisplayLanguage(target);
                String ctryresult = test.getDisplayCountry(target);
                String vrntresult = test.getDisplayVariant(target);

                // provider's name (if any)
                String providerslang = null;
                String providersctry = null;
                String providersvrnt = null;
                if (providerloc.contains(target)) {
                    providerslang = lnp.getDisplayLanguage(lang, target);
                    providersctry = lnp.getDisplayCountry(ctry, target);
                    providersvrnt = lnp.getDisplayVariant(vrnt, target);
                }

                // JRE's name (if any)
                String jreslang = null;
                String jresctry = null;
                String jresvrnt = null;
                if (!lang.equals("")) {
                    try {
                        jreslang = rb.getString(lang);
                    } catch (MissingResourceException mre) {}
                }
                if (!ctry.equals("")) {
                    try {
                        jresctry = rb.getString(ctry);
                    } catch (MissingResourceException mre) {}
                }
                if (!vrnt.equals("")) {
                    try {
                        jresvrnt = rb.getString("%%"+vrnt);
                    } catch (MissingResourceException mre) {
                        jresvrnt = vrnt;
                    }
                }

                checkValidity(target, jreslang, providerslang, langresult,
                    jreHasBundle && rb.handleGetKeys().contains(lang));
                checkValidity(target, jresctry, providersctry, ctryresult,
                    jreHasBundle && rb.handleGetKeys().contains(ctry));
                checkValidity(target, jresvrnt, providersvrnt, vrntresult,
                    jreHasBundle && rb.handleGetKeys().contains("%%"+vrnt));
            }
        }
    }
}
