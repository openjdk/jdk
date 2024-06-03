/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333456
 * @summary Ensure that integer only parsing against a string with no suffix
 *          does not unexpectedly fail and throw a StringIndexOutOfBoundsException
 * @run junit IntegerOnlyParseWithNoSuffixTest
 */

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class IntegerOnlyParseWithNoSuffixTest {

    // basic compact number format
    private static final NumberFormat fmt =
            NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

    static {
        fmt.setParseIntegerOnly(true);
        fmt.setGroupingUsed(true);
    }

    // Parse values with no compact suffix -> which allows parsing to iterate
    // position to the same value as string length which throws
    // StringIndexOutOfBoundsException upon charAt invocation
    @ParameterizedTest
    @MethodSource
    public void intOnlyNoSuffixParseTest(String toParse) throws ParseException {
        // Test both public API parse methods
        fmt.parse(toParse);
        fmt.parse(toParse, new ParsePosition(0));
    }

    // No compact suffixes
    private static Stream<String> intOnlyNoSuffixParseTest() {
        return Stream.of("5", "50", "50.", "5,000", // fail before change
                "5,000.", "5,000.00"); // Sanity check -> Do not fail before change
    }
}
