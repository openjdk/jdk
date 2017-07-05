/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library / /testlibrary /../../test/lib/
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.GetMaxCallTargetOffsetTest
 * @run main ClassFileInstaller
 *     jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *     -XX:+EnableJVMCI compiler.jvmci.compilerToVM.GetMaxCallTargetOffsetTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

public class GetMaxCallTargetOffsetTest {
    public static void main(String args[]) {
        new GetMaxCallTargetOffsetTest().runTest();
    }

    private void runTest() {
        long offset1 = CompilerToVMHelper.getMaxCallTargetOffset(0L);
        Asserts.assertNE(offset1, 0L,
                "Unexpected maxCallTargetOffset for 0L");
        long offset2 = CompilerToVMHelper.getMaxCallTargetOffset(100L);
        Asserts.assertNE(offset2, 0L,
                "Unexpected maxCallTargetOffset for 100L");
        long offset3 = CompilerToVMHelper.getMaxCallTargetOffset(1000000L);
        Asserts.assertNE(offset3, 0L,
                "Unexpected maxCallTargetOffset for 1000000L");
        // there can be 2 same offsets, but not 3
        Asserts.assertFalse(offset1 == offset2 && offset2 == offset3,
                "All 3 offsets are unexpectedly equal: " + offset1);
    }
}
