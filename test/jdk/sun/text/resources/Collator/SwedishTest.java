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
 * @bug 8306927 8307547
 * @modules jdk.localedata
 * @summary Tests Swedish collation involving 'v' and 'w'.
 * @run junit SwedishTest
 */

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SwedishTest {
    private static final String[] src = {"wb", "va", "vc"};
    private static final String[] standard = {"va", "vc", "wb"};
    private static final String[] traditional = {"va", "wb", "vc"};

    @ParameterizedTest
    @MethodSource("swedishData")
    public void testSwedishCollation(Locale l, String[] expected) {
        Arrays.sort(src, Collator.getInstance(l));
        assertArrayEquals(expected, src);
    }

    private static Stream<Arguments> swedishData() {
        return Stream.of(
            Arguments.of(Locale.forLanguageTag("sv"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-standard"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-STANDARD"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-traditio"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-TRADITIO"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-traditional"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-TRADITIONAL"), standard),
            // the new standard used to be called "reformed"
            Arguments.of(Locale.forLanguageTag("sv-u-co-reformed"), standard),
            Arguments.of(Locale.forLanguageTag("sv-u-co-REFORMED"), standard),

            Arguments.of(Locale.forLanguageTag("sv-u-co-trad"), traditional),
            Arguments.of(Locale.forLanguageTag("sv-u-co-TRAD"), traditional)
        );
    }
}
