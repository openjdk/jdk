/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393920
 * @summary Ensure that the plural processing in CompactNumberFormat can support its implementation
 *      limit of 2048 characters, even with a reduced stack size.
 * @run junit/othervm -Xss1m -Xint PluralProcessingTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.text.CompactNumberFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class PluralProcessingTest {

    // Plural rules that are excessively long
    private static final List<Arguments> LONG_PLURAL_RULES = List.of(
            Arguments.argumentSet("range_list",
                    "one:n=1" + ",1".repeat(1_000)),
            Arguments.argumentSet("and_condition",
                    "one:n=1" + "andn=1".repeat(340)),
            Arguments.argumentSet("expr",
                    "one:n" + "/1".repeat(1_000) + "=1")
    );

    @FieldSource("LONG_PLURAL_RULES")
    @ParameterizedTest
    void longPluralRulesTest(String rule) {
        assertDoesNotThrow(() ->
                new CompactNumberFormat("#", DecimalFormatSymbols.getInstance(Locale.ROOT),
                        new String[]{""}, rule));
    }
}
