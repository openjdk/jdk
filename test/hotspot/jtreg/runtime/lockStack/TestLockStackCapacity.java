/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test TestLockStackCapacity
 * @summary Tests the interaction between recursive lightweight locking and
 *          when the lock stack capacity is exceeded.
 * @requires vm.flagless
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xint TestLockStackCapacity
 */

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestLockStackCapacity {
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static class SynchronizedObject {
        static final SynchronizedObject OUTER = new SynchronizedObject();
        static final SynchronizedObject INNER = new SynchronizedObject();
        static final int LockStackCapacity = WB.getLockStackCapacity();

        synchronized void runInner(int depth) {
            assertNotInflated();
            if (depth == 1) {
                return;
            } else {
                runInner(depth - 1);
            }
            assertNotInflated();
        }

        synchronized void runOuter(int depth, SynchronizedObject inner) {
            assertNotInflated();
            if (depth == 1) {
                inner.runInner(LockStackCapacity);
            } else {
                runOuter(depth - 1, inner);
            }
            assertInflated();
        }

        public static void runTest() {
            // Test Requires a capacity of at least 2.
            Asserts.assertGTE(LockStackCapacity, 2);

            // Just checking
            OUTER.assertNotInflated();
            INNER.assertNotInflated();

            synchronized(OUTER) {
                OUTER.assertNotInflated();
                INNER.assertNotInflated();
                OUTER.runOuter(LockStackCapacity - 1, INNER);

                OUTER.assertInflated();
                INNER.assertNotInflated();
            }
        }

        void assertNotInflated() {
            Asserts.assertFalse(WB.isMonitorInflated(this));
        }

        void assertInflated() {
            Asserts.assertTrue(WB.isMonitorInflated(this));
        }
    }

    public static void main(String... args) throws Exception {
        if (!WB.supportsRecursiveLightweightLocking()) {
            throw new SkippedException("Test only valid if lightweight locking supports recursion");
        }

        SynchronizedObject.runTest();
    }
}
