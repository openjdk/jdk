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

/*
 * @test IsLockedVMFlagTest
 * @bug 8392477
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI IsLockedVMFlagTest
 * @summary Test that WhiteBox.isLockedVMFlag returns correct results
 */

import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.Asserts;

public class IsLockedVMFlagTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        // The unlocker flag itself is never locked.
        Asserts.assertFalse(WB.isLockedVMFlag("UnlockDiagnosticVMOptions"),
            "Unlocker flag 'UnlockDiagnosticVMOptions' should not be locked");
        Asserts.assertFalse(WB.isLockedVMFlag("UnlockExperimentalVMOptions"),
            "Unlocker flag 'UnlockExperimentalVMOptions' should not be locked");

        // A nonexistent flag should not be reported as locked.
        Asserts.assertFalse(WB.isLockedVMFlag("FakeFlag"),
            "Nonexistent flag should not be locked");
    }
}
