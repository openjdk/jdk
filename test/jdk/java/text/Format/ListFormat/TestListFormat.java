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
 * @bug 8041488
 * @summary Tests for ListFormat class
 * @run junit TestListFormat
 */

import java.text.ListFormat;
import java.text.ParseException;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TestListFormat {
    private static final Object[] SAMPLES = {"afo", "ika", "uni"};

    static Arguments[] getInstance_3Arg() {
        return new Arguments[] {
            arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL,
                    "afo, ika, and uni"),
        };
    }

//    @Test
//    void getInstance_noArg() {
//        assertEquals(ListFormat.getInstance(), ListFormat.getInstance(Locale.getDefault(Locale.Category.FORMAT), ListFormat.Type.STANDARD, ListFormat.Style.FULL));
//    }

    @ParameterizedTest
    @MethodSource
    void getInstance_3Arg(Locale l, ListFormat.Type type, ListFormat.Style style, String expected) throws ParseException {
        var f = ListFormat.getInstance(l, type, style);
        compareResult(f, expected);
    }

    private static void compareResult(ListFormat f, String expected) throws ParseException {
        var result = f.format(SAMPLES);
        assertEquals(expected, result);
        if (f.parseObject(result) instanceof Object[] ra) {
            assertArrayEquals(SAMPLES, ra);
        } else {
            fail();
        }
    }
}

