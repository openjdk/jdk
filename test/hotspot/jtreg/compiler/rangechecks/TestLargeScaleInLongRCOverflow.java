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
 * @bug 8324121
 * @summary SIGFPE in PhaseIdealLoop::extract_long_range_checks
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,TestLargeScaleInLongRCOverflow::test* -XX:-TieredCompilation TestLargeScaleInLongRCOverflow
 *
 */

import java.util.Objects;

public class TestLargeScaleInLongRCOverflow {

    public static void main(String args[]) {
        Objects.checkIndex(0, 1);
        try {
            test1();
        } catch (java.lang.IndexOutOfBoundsException e) { }
        try {
            test2();
        } catch (java.lang.IndexOutOfBoundsException e) { }
    }

    // SIGFPE in PhaseIdealLoop::extract_long_range_checks
    public static void test1() {
        for (long i = 1; i < 100; i += 2) {
            Objects.checkIndex(Long.MIN_VALUE * i, 10);
        }
    }

    // "assert(static_cast<T1>(result) == thing) failed: must be" in PhaseIdealLoop::transform_long_range_checks
    public static void test2() {
        for (long i = 1; i < 100; i += 2) {
            Objects.checkIndex((Long.MIN_VALUE + 2) * i, 10);
        }
    }
}
