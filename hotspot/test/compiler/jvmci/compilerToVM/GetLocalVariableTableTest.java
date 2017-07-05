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
 * @library /testlibrary /test/lib /
 * @clean compiler.jvmci.compilerToVM.*
 * @compile -g DummyInterface.java
 * @compile -g DummyAbstractClass.java
 * @compile -g DummyClass.java
 * @compile ../common/CompilerToVMHelper.java
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      -Xbootclasspath/a:.
 *      compiler.jvmci.compilerToVM.GetLocalVariableTableTest
 * @clean compiler.jvmci.compilerToVM.*
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.CTVMUtilities;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;

import java.lang.reflect.Executable;
import java.util.HashMap;
import java.util.Map;

public class GetLocalVariableTableTest {

    public static final int MAIN_LOCALS_COUNT = 0;
    public static final int INSTANCE_LOCALS_COUNT = 4;
    public static final int EMPTY_INSTANCE_COUNT = 1;
    public static final int EMPTY_STATIC_COUNT = 0;
    public static final int ABSTRACT_INHERIT_LOCALS_COUNT = 2;
    public static final int DEFAULTFUNC_LOCALS_COUNT = 4;

    public static void main(String[] args) {
        Map<Executable, Integer> testCases = createTestCases();
        testCases.forEach(GetLocalVariableTableTest::runSanityTest);
    }

    private static Map<Executable, Integer> createTestCases() {
        HashMap<Executable, Integer> methods = new HashMap<>();
        try {
            Class<?> aClass;

            aClass = GetLocalVariableTableTest.class;
            methods.put(aClass.getDeclaredMethod("main", String[].class),
                    MAIN_LOCALS_COUNT);

            aClass = DummyClass.class;
            methods.put(aClass.getMethod("dummyInstanceFunction"),
                    INSTANCE_LOCALS_COUNT);
            methods.put(aClass.getMethod("dummyEmptyInstanceFunction"),
                    EMPTY_INSTANCE_COUNT);
            methods.put(aClass.getMethod("dummyEmptyStaticFunction"),
                    EMPTY_STATIC_COUNT);
            methods.put(aClass.getMethod("dummyFunction"),
                    EMPTY_INSTANCE_COUNT);
            methods.put(aClass.getMethod("dummyAbstractFunction"),
                    ABSTRACT_INHERIT_LOCALS_COUNT);

            aClass = DummyInterface.class;
            methods.put(aClass.getMethod("dummyFunction"), EMPTY_STATIC_COUNT);
            methods.put(aClass.getMethod("dummyDefaultFunction", int.class,
                    int.class), DEFAULTFUNC_LOCALS_COUNT);

            aClass = DummyAbstractClass.class;
            methods.put(aClass.getMethod("dummyAbstractFunction"), 0);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG", e);
        }
        return methods;
    }

    private static void runSanityTest(Executable aMethod,
                                      Integer expectedTableLength) {
        HotSpotResolvedJavaMethod method = CTVMUtilities
                .getResolvedMethod(aMethod);

        int tblLength = CompilerToVMHelper.getLocalVariableTableLength(method);
        Asserts.assertEQ(tblLength, expectedTableLength, aMethod + " : incorrect "
                + "local variable table length.");

        long tblStart = CompilerToVMHelper.getLocalVariableTableStart(method);
        if (tblLength > 0) {
            Asserts.assertNE(tblStart, 0L, aMethod + " : local variable table starts"
                    + " at 0 with length " + tblLength);
        }
    }
}
