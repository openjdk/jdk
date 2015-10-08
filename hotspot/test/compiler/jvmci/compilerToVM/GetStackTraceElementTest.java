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
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -Xbootclasspath/a:. compiler.jvmci.compilerToVM.GetStackTraceElementTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

public class GetStackTraceElementTest {

    public static void main(String[] args) {
        Map<Executable, int[]> testCases = createTestCases();
        testCases.forEach(GetStackTraceElementTest::runSanityTest);
    }

    private static void runSanityTest(Executable aMethod, int[] bcis) {
        HotSpotResolvedJavaMethodImpl method = CTVMUtilities
                .getResolvedMethod(aMethod);
        String className = aMethod.getDeclaringClass().getName();
        int lastDot = className.lastIndexOf('.');
        int firstDol = className.contains("$")
                ? className.indexOf('$')
                : className.length();
        String fileName = className.substring(lastDot + 1, firstDol) + ".java";
        for (int bci : bcis) {
            StackTraceElement ste = CompilerToVMHelper
                    .getStackTraceElement(method, bci);
            Asserts.assertNotNull(ste);
            Asserts.assertEQ(ste.getClassName(), className);
            Asserts.assertEQ(ste.getFileName(), fileName);
            Asserts.assertEQ(ste.getMethodName(), aMethod.getName());
            Asserts.assertEQ(ste.isNativeMethod(), Modifier
                    .isNative(aMethod.getModifiers()));
        }

    }

    private static Map<Executable, int[]> createTestCases() {
        Map<Executable, int[]> testCases = new HashMap<>();

        try {
            Class<?> aClass = DummyClass.class;
            Method aMethod = aClass.getDeclaredMethod("dummyInstanceFunction");
            int[] bci = new int[] {0, 2, 3, 6, 7, 8, 11, 13, 15, 16, 17, 18};
            testCases.put(aMethod, bci);

            aMethod = aClass.getDeclaredMethod("dummyEmptyFunction");
            bci = new int[] {0};
            testCases.put(aMethod, bci);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG : test method not found", e);
        }
        return testCases;
    }

    private class DummyClass {
        public int dummyInstanceFunction() {
            String str1 = "123123123";
            double x = 3.14;
            int y = Integer.parseInt(str1);

            return y / (int)x;
        }

        public void dummyEmptyFunction() {}
    }
}
