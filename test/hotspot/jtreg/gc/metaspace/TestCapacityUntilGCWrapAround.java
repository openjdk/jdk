/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

package gc.metaspace;

/*
 * @test
 * @bug 8049831 8226236
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.metaspace.TestCapacityUntilGCWrapAround
 */

import sun.hotspot.WhiteBox;

import jdk.test.lib.Asserts;

public class TestCapacityUntilGCWrapAround {
    private static long MB = 1024 * 1024;

    private static void incMetaspaceCapacityUntilGCTest(WhiteBox wb) {
        long before = wb.metaspaceCapacityUntilGC();
        long after = wb.incMetaspaceCapacityUntilGC(100 * MB);

        Asserts.assertGTE(after, before,
                          "Increasing with INC should not cause wrap around: " + after + " < " + before);
    }

    private static void incMetaspaceCapacityUntilGCWithOverflowTest(WhiteBox wb) {
        // Force possible overflow of capacity_until_GC.
        long maxMetaspaceSize = wb.getSizeTVMFlag("MaxMetaspaceSize").longValue();
        try {
            long after = wb.incMetaspaceCapacityUntilGC(maxMetaspaceSize);
            throw new RuntimeException("Metaspace capacity should not be increased: after: " + Long.toUnsignedString(maxMetaspaceSize));
        } catch (IllegalStateException e) {
            // expected
        }
    }

    public static void main(String[] args) {
        WhiteBox wb = WhiteBox.getWhiteBox();

        incMetaspaceCapacityUntilGCTest(wb);
        incMetaspaceCapacityUntilGCWithOverflowTest(wb);
    }
}
