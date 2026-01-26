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
package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class CommandOutputControlTestUtils {

    @ParameterizedTest
    @MethodSource
    public void test_isInterleave(TestSpec test) {
        test.run();
    }

    private static Stream<TestSpec> test_isInterleave() {
        var data = new ArrayList<TestSpec>();

        data.addAll(List.of(
                interleaved("Toaday", "Today", "a"),
                interleaved("Todanaay", "Today", "ana"),
                interleaved("aaaababaaa", "aaaba", "aabaa"),
                interleaved("xxxxxxxxxxxyxxyx", "xxxxxxxy", "xxxxxyxx"),
                interleaved("xyxxxxyxxxxxxxxx", "yxxxxxxx", "xxxyxxxx"),
                interleaved("xxxxxxxyxxxxyxxx", "xxxyxxxx", "xxxxxxyx"),
                interleaved("cbdddcdaadacdbddbdcdddccdabbadba", "cdddaaddbcdcdbab", "bdcadcbddddcabda"),
                interleaved("ddbdcacddddbddbdbddadcaaccdcabab", "dbccdddbbddacdaa", "ddaddbdddacaccbb"),
                interleaved("adccbacbacaacddadddcdbbddbbddddd", "acbcaacddddbdbdd", "dcabcadadcbdbddd"),
                interleaved("abdbdabdaacdcdbddddadbbccddcddac", "addbaccbdddbcdda", "bbadaddddabcdcdc"),
                interleaved("cdaacbddaabdddbddbddbddadbacccdc", "dabdadddbddabccc", "cacdabdbddbddacd"),
                notInterleaved("Toady", "Today", "a"),
                notInterleaved("", "Today", "a")
        ));

        data.addAll(generateTestData("abcdefghijklmnopqrstuvwxyz", 10));
        data.addAll(generateTestData("xxxxxxxy", 8));
        data.addAll(generateTestData("aaabbbcccddddddd", 50));

        return data.stream().flatMap(test -> {
            return Stream.of(test, test.flip());
        });
    }

    private static List<TestSpec> generateTestData(String src, int iteration) {

        var srcCodePoints = new ArrayList<Integer>();
        src.codePoints().mapToObj(Integer::valueOf).forEach(srcCodePoints::add);

        var data = new ArrayList<TestSpec>();

        Function<List<Integer>, String> toString = codePoints -> {
            var arr = codePoints.stream().mapToInt(Integer::intValue).toArray();
            return new String(arr, 0, arr.length);
        };

        for (int i = 0; i < 10; i++) {
            Collections.shuffle(srcCodePoints);
            var a = List.copyOf(srcCodePoints);

            Collections.shuffle(srcCodePoints);
            var b = List.copyOf(srcCodePoints);

            var zip = new int[srcCodePoints.size() * 2];
            for (int codePointIdx = 0; codePointIdx != a.size(); codePointIdx++) {
                var dstIdx = codePointIdx * 2;
                zip[dstIdx] = a.get(codePointIdx);
                zip[dstIdx + 1] = b.get(codePointIdx);
            }

            data.add(interleaved(toString.apply(Arrays.stream(zip).boxed().toList()), toString.apply(a), toString.apply(b)));
        }

        return data;
    }

    public record TestSpec(String combined, String a, String b, boolean expected) {

        public TestSpec {
            Objects.requireNonNull(combined);
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);
        }

        TestSpec flip() {
            return new TestSpec(combined, b, a, expected);
        }

        void run() {
            assertEquals(expected, isInterleave(
                    combined.chars().mapToObj(Integer::valueOf).toList(),
                    a.chars().mapToObj(Integer::valueOf).toList(),
                    b.chars().mapToObj(Integer::valueOf).toList()),
                    String.format("combined: %s; a=%s; b=%s", combined, a, b));
        }
    }

    private static TestSpec interleaved(String combined, String a, String b) {
        return new TestSpec(combined, a, b, true);
    }

    private static TestSpec notInterleaved(String combined, String a, String b) {
        return new TestSpec(combined, a, b, false);
    }

    // Solves the standard "Find if a string C is an interleave of strings A and B."
    // problem but use containers instead of strings.
    static <T> boolean isInterleave(List<T> combined, List<T> a, List<T> b) {

        if (a.size() + b.size() != combined.size()) {
            return false;
        }

        final var n = a.size();
        final var m = b.size();

        var prev = new boolean[m + 1];
        final var cur = new boolean[m + 1];

        prev[0] = true;

        for (int j = 1; j <= m; j++) {
            prev[j] = prev[j - 1] && Objects.equals(b.get(j - 1), combined.get(j - 1));
        }

        for (int i = 1; i <= n; i++) {
            cur[0] = prev[0] && Objects.equals(a.get(i - 1), combined.get(i - 1));

            for (int j = 1; j <= m; j++) {
                int k = i + j;
                cur[j] = (prev[j] && Objects.equals(a.get(i - 1), combined.get(k - 1)))
                        || (cur[j - 1] && Objects.equals(b.get(j - 1), combined.get(k - 1)));
            }

            prev = cur.clone();
        }

        return prev[m];
    }
}
