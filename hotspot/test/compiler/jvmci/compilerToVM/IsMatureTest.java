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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox IsMatureTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *     -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     compiler.jvmci.compilerToVM.IsMatureTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.testcases.SimpleClass;
import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

import java.lang.reflect.Executable;

public class IsMatureTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final boolean IS_XCOMP
            = System.getProperty("java.vm.info").contains("compiled mode");
    private static final boolean TIERED
            = WB.getBooleanVMFlag("TieredCompilation");

    public static void main(String[] args) throws Exception {
        new IsMatureTest().test();
    }

    public void test() throws Exception {
        SimpleClass sclass = new SimpleClass();
        Executable method = SimpleClass.class.getDeclaredMethod("testMethod");
        long methodData = WB.getMethodData(method);
        boolean isMature = CompilerToVMHelper.isMature(methodData);
        Asserts.assertEQ(methodData, 0L,
                "Never invoked method can't have method data");
        Asserts.assertFalse(isMature, "Never invoked method can't be mature");
        for (int i = 0; i < CompilerWhiteBoxTest.THRESHOLD; i++) {
            sclass.testMethod();
        }
        methodData = WB.getMethodData(method);
        isMature = CompilerToVMHelper.isMature(methodData);
        Asserts.assertNE(methodData, 0L,
                "Multiple times invoked method should have method data");
        /* a method is not mature for -Xcomp and -Tiered,
           see NonTieredCompPolicy::is_mature */
        Asserts.assertEQ(isMature, !(IS_XCOMP && !TIERED),
                "Unexpected isMature state for multiple times invoked method");
    }
}
