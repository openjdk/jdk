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
 * @bug 8305486
 * @summary Tests to exercise the split functionality added in the issue.
 * @run junit SplitWithDelimitersTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class SplitWithDelimitersTest {

    private static String[] dropOddIndexed(String[] a, int limit) {
        String[] r = new String[(a.length + 1) / 2];
        for (int i = 0; i < a.length; i += 2) {
            r[i / 2] = a[i];
        }
        int len = r.length;
        if (limit == 0) {
            /* Also drop trailing empty strings */
            for (; len > 0 && r[len - 1].isEmpty(); --len);  // empty body
        }
        return len < r.length ? Arrays.copyOf(r, len) : r;
    }

    static Arguments[] testSplit() {
        return new Arguments[] {
                arguments(new String[] {"b", "o", "", "o", ":::and::f", "o", "", "o", ""},
                        "boo:::and::foo", "o", 5),
                arguments(new String[] {"b", "o", "", "o", ":::and::f", "o", "o"},
                        "boo:::and::foo", "o", 4),
                arguments(new String[] {"b", "o", "", "o", ":::and::foo"},
                        "boo:::and::foo", "o", 3),
                arguments(new String[] {"b", "o", "o:::and::foo"},
                        "boo:::and::foo", "o", 2),
                arguments(new String[] {"boo:::and::foo"},
                        "boo:::and::foo", "o", 1),
                arguments(new String[] {"b", "o", "", "o", ":::and::f", "o", "", "o"},
                        "boo:::and::foo", "o", 0),
                arguments(new String[] {"b", "o", "", "o", ":::and::f", "o", "", "o", ""},
                        "boo:::and::foo", "o", -1),

                arguments(new String[] {"boo", ":::", "and", "::", "foo"},
                        "boo:::and::foo", ":+", 3),
                arguments(new String[] {"boo", ":::", "and::foo"},
                        "boo:::and::foo", ":+", 2),
                arguments(new String[] {"boo:::and::foo"},
                        "boo:::and::foo", ":+", 1),
                arguments(new String[] {"boo", ":::", "and", "::", "foo"},
                        "boo:::and::foo", ":+", 0),
                arguments(new String[] {"boo", ":::", "and", "::", "foo"},
                        "boo:::and::foo", ":+", -1),

                arguments(new String[] {"b", "", "b", "", ""},
                        "bb", "a*|b*", 3),
                arguments(new String[] {"b", "", "b"},
                        "bb", "a*|b*", 2),
                arguments(new String[] {"bb"},
                        "bb", "a*|b*", 1),
                arguments(new String[] {"b", "", "b"},
                        "bb", "a*|b*", 0),
                arguments(new String[] {"b", "", "b", "", ""},
                        "bb", "a*|b*", -1),

                arguments(new String[] {"", "bb", "", "", ""},
                        "bb", "b*|a*", 3),
                arguments(new String[] {"", "bb", ""},
                        "bb", "b*|a*", 2),
                arguments(new String[] {"bb"},
                        "bb", "b*|a*", 1),
                arguments(new String[] {"", "bb"},
                        "bb", "b*|a*", 0),
                arguments(new String[] {"", "bb", "", "", ""},
                        "bb", "b*|a*", -1),
        };
    }

    @ParameterizedTest
    @MethodSource
    void testSplit(String[] expected, String target, String regex, int limit) {
        String[] computedWith = target.splitWithDelimiters(regex, limit);
        assertArrayEquals(expected, computedWith);
        String[] patComputedWith = Pattern.compile(regex).splitWithDelimiters(target, limit);
        assertArrayEquals(computedWith, patComputedWith);

        String[] computedWithout = target.split(regex, limit);
        assertArrayEquals(dropOddIndexed(expected, limit), computedWithout);
        String[] patComputedWithout = Pattern.compile(regex).split(target, limit);
        assertArrayEquals(computedWithout, patComputedWithout);
    }

}
