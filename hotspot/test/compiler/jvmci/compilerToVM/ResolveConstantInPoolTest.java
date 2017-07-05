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

/*
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library /testlibrary /test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.ResolveConstantInPoolTest
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.ResolveConstantInPoolTest
 */

package compiler.jvmci.compilerToVM;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.test.lib.Asserts;
import sun.reflect.ConstantPool;

/**
 * Test for {@code compiler.jvmci.CompilerToVM.resolveConstantInPool} method
 */
public class ResolveConstantInPoolTest {

    public static void main(String[] args) throws Exception {
        Map<ConstantPoolTestsHelper.ConstantTypes,
                ConstantPoolTestCase.Validator> typeTests = new HashMap<>(2);
        typeTests.put(ConstantPoolTestsHelper.ConstantTypes.CONSTANT_METHODHANDLE,
                ResolveConstantInPoolTest::validateMethodHandle);
        typeTests.put(ConstantPoolTestsHelper.ConstantTypes.CONSTANT_METHODTYPE,
                ResolveConstantInPoolTest::validateMethodType);
        ConstantPoolTestCase testCase = new ConstantPoolTestCase(typeTests);
        testCase.test();
    }

    private static void validateMethodHandle(
            jdk.vm.ci.meta.ConstantPool constantPoolCTVM,
            ConstantPool constantPoolSS,
            ConstantPoolTestsHelper.DummyClasses dummyClass, int index) {
        Object constantInPool = CompilerToVMHelper
                .resolveConstantInPool(constantPoolCTVM, index);
        if (!(constantInPool instanceof MethodHandle)) {
            String msg = String.format(
                    "Wrong constant pool entry accessed by index"
                            + " %d: %s, but should be subclass of %s",
                    index + 1, constantInPool.getClass(),
                    MethodHandle.class.getName());
            throw new AssertionError(msg);
        }
    }

    private static void validateMethodType(
            jdk.vm.ci.meta.ConstantPool constantPoolCTVM,
            ConstantPool constantPoolSS,
            ConstantPoolTestsHelper.DummyClasses dummyClass, int index) {
        Object constantInPool = CompilerToVMHelper
                .resolveConstantInPool(constantPoolCTVM, index);
        Class mtToVerify = constantInPool.getClass();
        Class mtToRefer = MethodType.class;
        String msg = String.format("Wrong %s accessed by constant pool index"
                            + " %d: %s, but should be %s", "method type class",
                            index, mtToVerify, mtToRefer);
        Asserts.assertEQ(mtToRefer, mtToVerify, msg);
    }
}
