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

public class CollatorProviderTest extends ProviderTest {

    com.foo.CollatorProviderImpl cp = new com.foo.CollatorProviderImpl();
    List<Locale> availloc = Arrays.asList(Collator.getAvailableLocales());
    List<Locale> providerloc = Arrays.asList(cp.getAvailableLocales());
    List<Locale> jreloc = Arrays.asList(LocaleData.getAvailableLocales());

    public static void main(String[] s) {
        new CollatorProviderTest();
    }

    CollatorProviderTest() {
        availableLocalesTest();
        objectValidityTest();
    }

    void availableLocalesTest() {
        Set<Locale> localesFromAPI = new HashSet<Locale>(availloc);
        Set<Locale> localesExpected = new HashSet<Locale>(jreloc);
        localesExpected.addAll(providerloc);
        if (localesFromAPI.equals(localesExpected)) {
            System.out.println("availableLocalesTest passed.");
        } else {
            throw new RuntimeException("availableLocalesTest failed");
        }
    }

    void objectValidityTest() {
        Collator def = Collator.getInstance(new Locale(""));
        String defrules = ((RuleBasedCollator)def).getRules();

        for (Locale target: availloc) {
            // pure JRE implementation
            ResourceBundle rb = LocaleData.getCollationData(target);
            boolean jreSupportsLocale = jreloc.contains(target);

            // result object
            Collator result = Collator.getInstance(target);

            // provider's object (if any)
            Collator providersResult = null;
            if (providerloc.contains(target)) {
                providersResult = cp.getInstance(target);
            }

            // JRE rule
            Collator jresResult = null;
            if (jreSupportsLocale) {
                try {
                    String rules = rb.getString("Rule");
                    jresResult = new RuleBasedCollator(defrules+rules);
                    jresResult.setDecomposition(Collator.NO_DECOMPOSITION);
                } catch (MissingResourceException mre) {
                } catch (ParseException pe) {
                }
            }

            checkValidity(target, jresResult, providersResult, result, jreSupportsLocale);
        }
    }
}
