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
 * @bug 8365077 8370160
 * @summary confirm that an instance which is created with Enum ranges is
 * equal to another instance which is created with equivalent traditional
 * ranges, and that in such a case the hashCodes are also equal.
 */

import java.awt.font.NumericShaper;
import java.awt.font.NumericShaper.Range;
import static java.awt.font.NumericShaper.Range.*;
import java.util.EnumSet;

public class NSEqualsTest {

    public static void main(String[] args) {

        // Invalid ranges should be discarded
        NumericShaper cs1 =
            NumericShaper.getContextualShaper(NumericShaper.ALL_RANGES);
        NumericShaper cs2 = NumericShaper.getContextualShaper(-1);
        printAndCompare(cs1, cs2);

        for (Range r1 : Range.values()) {
           test(r1);
           for (Range r2 : Range.values()) {
              test(r1, r2);
           }
        }
    }

    static void test(Range r) {
        if (r.ordinal() > MONGOLIAN.ordinal()) {
            return;
        }
        int o = 1 << r.ordinal();
        NumericShaper nsr = NumericShaper.getContextualShaper(EnumSet.of(r));
        NumericShaper nso = NumericShaper.getContextualShaper(o);
        printAndCompare(nsr, nso);
    }

    static void test(Range r1, Range r2) {
        if (r1.ordinal() > MONGOLIAN.ordinal() || r2.ordinal() > MONGOLIAN.ordinal()) {
            return;
        }
        int o1 = 1 << r1.ordinal();
        int o2 = 1 << r2.ordinal();

        NumericShaper nsr = NumericShaper.getContextualShaper(EnumSet.of(r1, r2));
        NumericShaper nso = NumericShaper.getContextualShaper(o1 | o2);
        printAndCompare(nsr, nso);
    }

    static void printAndCompare(NumericShaper nsr, NumericShaper nso) {
        System.err.println(nsr);
        System.err.println(nso);
        System.err.println(nsr.hashCode() + " vs " + nso.hashCode() +
                           " equal: " + nsr.equals(nso));
        if (!nsr.equals(nso)) {
            throw new RuntimeException("Expected equal");
        }
        if (nsr.hashCode() != nso.hashCode()) {
            throw new RuntimeException("Different hash codes:");
        }
    }
}

