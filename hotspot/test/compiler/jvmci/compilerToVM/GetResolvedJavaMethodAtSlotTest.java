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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library /testlibrary /test/lib /
 * @library ../common/patches
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 *          jdk.vm.ci/jdk.vm.ci.meta
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.GetNextStackFrameTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.GetResolvedJavaMethodAtSlotTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import java.util.HashMap;
import java.util.Map;

public class GetResolvedJavaMethodAtSlotTest {

    private static class A {
        {
            System.out.println("Dummy");
        }
        public void f1() {}
        public int f2() { return 0; }
        public String f3() { return ""; }
    }


    private static class S {
        static {
            System.out.println("Dummy static");
        }
        public S() {}
        public void f1() {}
        public int f2() { return 0; }
        public String f3() { return ""; }
    }

    private class B extends A {
        public void f4() {}
    }

    private interface I {
        void f1();
        int f2();
        String f3();
    }

    public static void main(String[] args) {
        Map<Class<?>, Integer> testCases = getTestCases();
        testCases.forEach(GetResolvedJavaMethodAtSlotTest::test);
    }

    private static Map<Class<?>, Integer> getTestCases() {
        Map<Class<?>, Integer> testCases = new HashMap<>();
        testCases.put(A.class, 5); // ctor, init, f1, f2, f3
        testCases.put(S.class, 5); // ctor, cinit, f1, f2, f3
        testCases.put(I.class, 3); // f1, f2, f3
        testCases.put(B.class, 2); // ctor, f4
        return testCases;
    }

    private static void test(Class<?> aClass, int methodNumber) {
        testSlotBigger(aClass);
        testCorrectMethods(aClass, methodNumber);
    }

    private static void testSlotBigger(Class<?> holder) {
        HotSpotResolvedJavaMethod method
                = CompilerToVMHelper.getResolvedJavaMethodAtSlot(holder, 50);
        Asserts.assertNull(method, "Got method for non existing slot 50 in "
                + holder);
    }

    private static void testCorrectMethods(Class<?> holder, int methodsNumber) {
        for (int i = 0; i < methodsNumber; i++) {
            String caseName = String.format("slot %d in %s",
                    i, holder.getCanonicalName());
            HotSpotResolvedJavaMethod method = CompilerToVMHelper
                    .getResolvedJavaMethodAtSlot(holder, i);
            Asserts.assertNotNull(method, caseName + " did not got method");
            Asserts.assertEQ(holder,
                    CompilerToVMHelper.getMirror(method.getDeclaringClass()),
                    caseName + " : unexpected declaring class");
        }
    }
}
