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
 *      -XX:-BackgroundCompilation
        -XX:+LogCompilation
 *      compiler.jvmci.compilerToVM.AllocateCompileIdTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

import compiler.jvmci.common.testcases.TestCase;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.test.lib.Asserts;
import jdk.test.lib.Pair;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.NMethod;

public class AllocateCompileIdTest {

    private final HashSet<Integer> ids = new HashSet<>();

    public static void main(String[] args) {
        AllocateCompileIdTest test = new AllocateCompileIdTest();
        createTestCasesCorrectBci().forEach(test::runSanityCorrectTest);
        createTestCasesIncorrectBci().forEach(test::runSanityIncorrectTest);
    }


    private static List<CompileCodeTestCase> createTestCasesCorrectBci() {
        List<CompileCodeTestCase> result = new ArrayList<>();
        try {
            Class<?> aClass = DummyClass.class;
            Method method = aClass.getMethod("withLoop");
            Object receiver = new DummyClass();
            result.add(new CompileCodeTestCase(receiver, method, 17));
            result.add(new CompileCodeTestCase(receiver, method, -1));
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG : " + e, e);
        }
        return result;
    }


    private static List<Pair<CompileCodeTestCase, Class<? extends Throwable>>>
            createTestCasesIncorrectBci() {
        List<Pair<CompileCodeTestCase, Class<? extends Throwable>>> result
                = new ArrayList<>();

        try {
            Class<?> aClass = DummyClass.class;
            Object receiver = new DummyClass();
            Method method = aClass.getMethod("dummyInstanceFunction");
            // greater than bytecode.length
            int[] bcis = new int[] {30, 50, 200};
            for (int bci : bcis) {
                result.add(new Pair<>(
                        new CompileCodeTestCase(receiver, method, bci),
                        IllegalArgumentException.class));
            }
            bcis = new int[] {-4, -50, -200};
            for (int bci : bcis) {
                result.add(new Pair<>(
                        new CompileCodeTestCase(receiver, method, bci),
                        IllegalArgumentException.class));
            }
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG : " + e.getMessage(), e);
        }
        return result;
    }

    private void runSanityCorrectTest(CompileCodeTestCase testCase) {
        System.out.println(testCase);
        Executable aMethod = testCase.executable;
        // to generate ciTypeFlow
        System.out.println(testCase.invoke(Utils.getNullValues(aMethod.getParameterTypes())));
        int bci = testCase.bci;
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(aMethod);
        int wbCompileID = getWBCompileID(testCase);
        int id = CompilerToVMHelper.allocateCompileId(method, bci);
        Asserts.assertNE(id, 0, testCase + " : zero compile id");

        if (wbCompileID > 0) {
            Asserts.assertGT(id, wbCompileID, testCase
                    + " : allocated 'compile id' not  greater than existed");
            if (!ids.add(wbCompileID)) {
                throw new AssertionError(String.format(
                        "%s : vm compilation allocated existed id -- %d",
                        testCase, id));
            }
        }
        if (!ids.add(id)) {
            throw new AssertionError(String.format(
                    "%s : allocateCompileId returned existed id %d",
                    testCase, id));
        }
    }

    private void runSanityIncorrectTest(
            Pair<CompileCodeTestCase, Class<? extends Throwable>> testCase) {
        System.out.println(testCase);
        Class<? extends Throwable> exception = testCase.second;
        Executable aMethod = testCase.first.executable;
        int bci = testCase.first.bci;
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(aMethod);
        Utils.runAndCheckException(
                () -> CompilerToVMHelper.allocateCompileId(method, bci),
                exception);
    }

    private int getWBCompileID(CompileCodeTestCase testCase) {
        NMethod nm = testCase.deoptimizeAndCompile();
        if (nm == null) {
            throw new Error("[TEST BUG] cannot compile method " + testCase);
        }
        return nm.compile_id;
    }
}
