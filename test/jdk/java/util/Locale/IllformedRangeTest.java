/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8349883
 * @summary Ensure IAE is thrown on ill-formed language ranges
 * @run junit IllformedRangeTest
 */

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class IllformedRangeTest {

    // Test the endpoints that accept a language range
    @ParameterizedTest
    @MethodSource("illegalRanges")
    public void illformedRangeTest(String range) {
        // static parses
        assertThrows(IllegalArgumentException.class,
                () -> Locale.LanguageRange.parse(range));
        assertThrows(IllegalArgumentException.class,
                () -> Locale.LanguageRange.parse(range, new HashMap<>()));
        // ctors
        assertThrows(IllegalArgumentException.class,
                () -> new Locale.LanguageRange(range));
        assertThrows(IllegalArgumentException.class,
                () -> new Locale.LanguageRange(range, Locale.LanguageRange.MIN_WEIGHT));
    }

    private static Stream<String> illegalRanges() {
        return Stream.of(
                // 8349883 offending range
                "-",
                // Other general ill-formed test cases
                "-foo",
                "foo-",
                "foo1",
                "foo-123456789",
                "*-*-",
                ""
        );
    }
}
