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
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.reflect
 *          java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.MethodIsIgnoredBySecurityStackWalkTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.MethodIsIgnoredBySecurityStackWalkTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import java.lang.reflect.Method;
import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

public class MethodIsIgnoredBySecurityStackWalkTest {

    public static void main(String[] args) {
        Map<Executable, Boolean> testCases = createTestCases();
        testCases.forEach(
                MethodIsIgnoredBySecurityStackWalkTest::runSanityTest);
    }

    private static void runSanityTest(Executable aMethod, Boolean expected) {
        HotSpotResolvedJavaMethod method
                = CTVMUtilities.getResolvedMethod(aMethod);
        boolean isIgnored = CompilerToVMHelper
                .methodIsIgnoredBySecurityStackWalk(method);
        String msg = String.format("%s is%s ignored but must%s", aMethod,
                isIgnored ? "" : " not",
                expected ? "" : " not");
        Asserts.assertEQ(isIgnored, expected, msg);
    }

    private static Map<Executable, Boolean> createTestCases() {
        Map<Executable, Boolean> testCases = new HashMap<>();

        try {
            Class<?> aClass = Method.class;
            testCases.put(aClass.getMethod("invoke", Object.class,
                    Object[].class), true);

            aClass = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl");
            testCases.put(aClass.getMethod("invoke", Object.class,
                    Object[].class), true);
            testCases.put(aClass.getDeclaredMethod("invoke0", Method.class,
                    Object.class, Object[].class), true);

            aClass = MethodIsIgnoredBySecurityStackWalkTest.class;
            for (Executable method : aClass.getMethods()) {
                testCases.put(method, false);
            }
            for (Executable method : aClass.getDeclaredMethods()) {
                testCases.put(method, false);
            }
            for (Executable method : aClass.getConstructors()) {
                testCases.put(method, false);
            }
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new Error("TEST BUG " + e.getMessage(), e);
        }
        return testCases;
    }
}
