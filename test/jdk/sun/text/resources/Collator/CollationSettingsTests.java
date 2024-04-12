/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308108
 * @summary Tests for BCP 47 collation settings
 * @run junit CollationSettingsTests
 */

import java.text.Collator;
import java.util.Locale;
import java.util.stream.Stream;
import static java.text.Collator.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CollationSettingsTests {
    private static final Collator ENG_DEF = Collator.getInstance(Locale.ENGLISH);

    private static Stream<Arguments> strengthData() {
        return Stream.of(
            Arguments.of(Locale.forLanguageTag("en-u-ks-level1"), PRIMARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-level2"), SECONDARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-level3"), TERTIARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-identic"), IDENTICAL),
            Arguments.of(Locale.forLanguageTag("en-u-ks-LEVEL1"), PRIMARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-LEVEL2"), SECONDARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-LEVEL3"), TERTIARY),
            Arguments.of(Locale.forLanguageTag("en-u-ks-IDENTIC"), IDENTICAL),
            // unrecognized setting value
            Arguments.of(Locale.forLanguageTag("en-u-ks-foo"), ENG_DEF.getStrength()),
            Arguments.of(Locale.forLanguageTag("en-u-ks-level4"), ENG_DEF.getStrength()),
            Arguments.of(Locale.forLanguageTag("en-u-ks-identical"), ENG_DEF.getStrength())
        );
    }

    private static Stream<Arguments> decompData() {
        return Stream.of(
            Arguments.of(Locale.forLanguageTag("en-u-kk-true"), CANONICAL_DECOMPOSITION),
            Arguments.of(Locale.forLanguageTag("en-u-kk-false"), NO_DECOMPOSITION),
            Arguments.of(Locale.forLanguageTag("en-u-kk-TRUE"), CANONICAL_DECOMPOSITION),
            Arguments.of(Locale.forLanguageTag("en-u-kk-FALSE"), NO_DECOMPOSITION),
            // unrecognized setting value
            Arguments.of(Locale.forLanguageTag("en-u-kk-foo"), ENG_DEF.getDecomposition()),
            Arguments.of(Locale.forLanguageTag("en-u-kk-truetrue"), ENG_DEF.getDecomposition())
        );
    }

    @ParameterizedTest
    @MethodSource("strengthData")
    public void testStrength(Locale l, int expected) {
        assertEquals(expected, Collator.getInstance(l).getStrength());
    }

    @ParameterizedTest
    @MethodSource("decompData")
    public void testDecomposition(Locale l, int expected) {
        assertEquals(expected, Collator.getInstance(l).getDecomposition());
    }
}
