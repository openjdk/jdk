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
 *
 */

/**
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library /testlibrary /../../test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.jvmci.compilerToVM.ShouldInlineMethodTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

public class ShouldInlineMethodTest {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        List<Executable> testCases = createTestCases();
        testCases.forEach(ShouldInlineMethodTest::runSanityTest);
    }

    private static void runSanityTest(Executable aMethod) {
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(aMethod);
        boolean shouldInline = CompilerToVMHelper.shouldInlineMethod(method);
        boolean expectedShouldInline = WB.testSetForceInlineMethod(aMethod,
                true);
        Asserts.assertEQ(shouldInline, expectedShouldInline,
                "Unexpected value of property 'should inline'");

        shouldInline = CompilerToVMHelper.shouldInlineMethod(method);
        Asserts.assertTrue(shouldInline, "Unexpected value of property "
                + "'should inline' after setting 'force inline' to true");
        WB.testSetForceInlineMethod(aMethod, false);
        shouldInline = CompilerToVMHelper.shouldInlineMethod(method);
        Asserts.assertFalse(shouldInline, "Unexpected value of property "
                + "'should inline' after setting 'force inline' to false");
    }

    private static List<Executable> createTestCases() {
        List<Executable> testCases = new ArrayList<>();

        Class<?> aClass = DummyClass.class;
        testCases.addAll(Arrays.asList(aClass.getDeclaredMethods()));
        testCases.addAll(Arrays.asList(aClass.getDeclaredConstructors()));
        return testCases;
    }
}
