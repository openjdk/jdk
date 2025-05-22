/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4052440 8000273 8062588 8210406 8174269 8356040
 * @summary LocaleNameProvider tests
 * @library providersrc/foobarutils
 *          providersrc/barprovider
 * @modules java.base/sun.util.locale.provider
 *          java.base/sun.util.resources
 * @build com.foobar.Utils
 *        com.bar.*
 * @run junit/othervm -Djava.locale.providers=CLDR,SPI LocaleNameProviderTest
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.bar.LocaleNameProviderImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;
import sun.util.resources.OpenListResourceBundle;

public class LocaleNameProviderTest extends ProviderTest {

    private static final LocaleNameProviderImpl LNP = new LocaleNameProviderImpl();

    /*
     * This is not an exhaustive test. Such a test would require iterating (1000x1000)+
     * inputs. Instead, we check against Japanese lang locales which guarantees
     * we will run into cases where the CLDR is not the preferred provider as the
     * SPI has defined variants of the Japanese locale (E.g. osaka).
     * See LocaleNameProviderImpl and LocaleNames ResourceBundle.
     */
    @ParameterizedTest
    @MethodSource
    void checkAvailLocValidityTest(Locale target, Locale test, ResourceBundle rb,
                                   boolean jreSupports, boolean spiSupports) {
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
        if (spiSupports) {
            providerslang = LNP.getDisplayLanguage(lang, target);
            providersctry = LNP.getDisplayCountry(ctry, target);
            providersvrnt = LNP.getDisplayVariant(vrnt, target);
        }

        // JRE's name
        String jreslang = null;
        String jresctry = null;
        String jresvrnt = null;
        if (!lang.isEmpty()) {
            try {
                jreslang = rb.getString(lang);
            } catch (MissingResourceException mre) {}
        }
        if (!ctry.isEmpty()) {
            try {
                jresctry = rb.getString(ctry);
            } catch (MissingResourceException mre) {}
        }
        if (!vrnt.isEmpty()) {
            try {
                jresvrnt = rb.getString("%%"+vrnt);
            } catch (MissingResourceException mre) {}
        }

        checkValidity(target, jreslang, providerslang, langresult,
            jreSupports && jreslang != null);
        checkValidity(target, jresctry, providersctry, ctryresult,
            jreSupports && jresctry != null);
        checkValidity(target, jresvrnt, providersvrnt, vrntresult,
            jreSupports && jresvrnt != null);
    }

    public static List<Arguments> checkAvailLocValidityTest() {
        var args = new ArrayList<Arguments>();
        Locale[] availloc = Locale.availableLocales()
                .filter(l -> l.getLanguage().equals("ja"))
                .toArray(Locale[]::new);
        List<Locale> jreimplloc = Arrays.stream(LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR)
                        .getLocaleNameProvider().getAvailableLocales())
                .filter(l -> l.getLanguage().equals("ja"))
                .toList();
        List<Locale> providerloc = Arrays.asList(LNP.getAvailableLocales());

        for (Locale target : availloc) {
            // pure JRE implementation
            OpenListResourceBundle rb = ((ResourceBundleBasedAdapter) LocaleProviderAdapter.forType(LocaleProviderAdapter.Type.CLDR)).getLocaleData().getLocaleNames(target);
            boolean jreSupportsTarget = jreimplloc.contains(target);
            boolean providerSupportsTarget = providerloc.contains(target);
            for (Locale test : availloc) {
                args.add(Arguments.of(target, test, rb, jreSupportsTarget, providerSupportsTarget));
            }
        }
        return args;
    }

    @Test
    void variantFallbackTest() {
        Locale YY = Locale.of("yy", "YY", "YYYY");
        Locale YY_suffix = Locale.of("yy", "YY", "YYYY_suffix");
        String retVrnt = null;
        String message = "variantFallbackTest() succeeded.";


        try {
            YY.getDisplayVariant(YY_suffix);
            message = "variantFallbackTest() failed. Either provider wasn't invoked, or invoked without suffix.";
        } catch (RuntimeException re) {
            retVrnt = re.getMessage();
            if (YY_suffix.getVariant().equals(retVrnt)) {
                System.out.println(message);
                return;
            }
            message = "variantFallbackTest() failed. Returned variant: "+retVrnt;
        }

        throw new RuntimeException(message);
    }
}
