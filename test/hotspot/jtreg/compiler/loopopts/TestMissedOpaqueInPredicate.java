/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8289127
 * @summary Apache Lucene triggers: DEBUG MESSAGE: duplicated predicate failed which is impossible
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation TestMissedOpaqueInPredicate
 */

public class TestMissedOpaqueInPredicate {
    public static void main(String[] args) {
        long[] tmp = new long[28];
        long[] longs = new long[32];
        for (int i = 0; i < 20_000; i++) {
            test(tmp, longs);
        }
    }

    private static void test(long[] tmp, long[] longs) {
        for (int iter = 0, tmpIdx = 0, longsIdx = 28; iter < 4; ++iter, tmpIdx += 7, longsIdx += 1) {
            long l0 = tmp[tmpIdx + 0] << 12;
            l0 |= tmp[tmpIdx + 1] << 10;
            l0 |= tmp[tmpIdx + 2] << 8;
            l0 |= tmp[tmpIdx + 3] << 6;
            l0 |= tmp[tmpIdx + 4] << 4;
            l0 |= tmp[tmpIdx + 5] << 2;
            l0 |= tmp[tmpIdx + 6] << 0;
            longs[longsIdx + 0] = l0;
        }
    }
}
