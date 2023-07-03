/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4210525
 * @summary Locale variant should not be case folded
 * @run junit CaseCheckVariant
 */

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CaseCheckVariant {

    static final String LANG = "en";
    static final String COUNTRY = "US";

    /**
     * When a locale is created with a given variant, ensure
     * that the variant is not case normalized.
     */
    @ParameterizedTest
    @MethodSource("variants")
    public void variantCaseTest(String variant) {
        Locale aLocale = Locale.of(LANG, COUNTRY, variant);
        String localeVariant = aLocale.getVariant();
        assertEquals(localeVariant, variant);
    }

    private static Stream<String> variants() {
        return Stream.of(
                "socal",
                "Norcal"
        );
    }
}
