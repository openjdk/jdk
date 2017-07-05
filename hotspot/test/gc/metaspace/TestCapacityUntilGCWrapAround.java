/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @key gc
 * @bug 8049831
 * @library /testlibrary /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build TestCapacityUntilGCWrapAround
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestCapacityUntilGCWrapAround
 */

import sun.hotspot.WhiteBox;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class TestCapacityUntilGCWrapAround {
    private static long MB = 1024 * 1024;
    private static long GB = 1024 * MB;
    private static long MAX_UINT = 4 * GB - 1; // On 32-bit platforms

    public static void main(String[] args) {
        if (Platform.is32bit()) {
            WhiteBox wb = WhiteBox.getWhiteBox();

            long before = wb.metaspaceCapacityUntilGC();
            // Now force possible overflow of capacity_until_GC.
            long after = wb.incMetaspaceCapacityUntilGC(MAX_UINT);

            Asserts.assertGTE(after, before,
                              "Increasing with MAX_UINT should not cause wrap around: " + after + " < " + before);
            Asserts.assertLTE(after, MAX_UINT,
                              "Increasing with MAX_UINT should not cause value larger than MAX_UINT:" + after);
        }
    }
}
