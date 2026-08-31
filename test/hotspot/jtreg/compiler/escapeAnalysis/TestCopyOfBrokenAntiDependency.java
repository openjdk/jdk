/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8238384 8391160
 * @summary Test that Arrays.copyOf with non-escaping allocations and distinct memory slices compiles without assertion failures
 * @run main/othervm -Xbatch ${test.main.class}
 * @run main/othervm -Xbatch -XX:-ReduceInitialCardMarks -XX:-ReduceBulkZeroing ${test.main.class}
 */

package compiler.escapeAnalysis;

import java.util.Arrays;

public class TestCopyOfBrokenAntiDependency {

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test(100);
        }
    }

    private static Object test(int length) {
        Object[] src  = new Object[length]; // non escaping
        final Object[] dst = Arrays.copyOf(src, 10); // can't be removed
        final Object[] dst2 = Arrays.copyOf(dst, 100);
        // load is control dependent on membar from previous copyOf
        // but has memory edge to first copyOf.
        final Object v = dst[0];
        return v;
    }
}
