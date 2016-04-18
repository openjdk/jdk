/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.reflect
 *          java.base/jdk.internal.org.objectweb.asm
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.meta
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build sun.hotspot.WhiteBox
 *        compiler.jvmci.compilerToVM.ResolveConstantInPoolTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.jvmci.compilerToVM.ResolveConstantInPoolTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.compilerToVM.ConstantPoolTestsHelper.DummyClasses;
import compiler.jvmci.compilerToVM.ConstantPoolTestCase.ConstantTypes;
import static compiler.jvmci.compilerToVM.ConstantPoolTestCase.ConstantTypes.*;
import compiler.jvmci.compilerToVM.ConstantPoolTestCase.Validator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import jdk.test.lib.Asserts;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.meta.ConstantPool;

/**
 * Test for {@code jdk.vm.ci.hotspot.CompilerToVM.resolveConstantInPool} method
 */
public class ResolveConstantInPoolTest {

    private static final String NOT_NULL_MSG
            = "Object returned by resolveConstantInPool method should not be null";

    public static void main(String[] args) throws Exception {
        Map<ConstantTypes, Validator> typeTests = new HashMap<>();
        typeTests.put(CONSTANT_METHODHANDLE, ResolveConstantInPoolTest::validateMethodHandle);
        typeTests.put(CONSTANT_METHODTYPE, ResolveConstantInPoolTest::validateMethodType);
        ConstantPoolTestCase testCase = new ConstantPoolTestCase(typeTests);
        testCase.test();
        // The next "Class.forName" and repeating "testCase.test()"
        // are here for the following reason.
        // The first test run is without dummy class initialization,
        // which means no constant pool cache exists.
        // The second run is with initialized class (with constant pool cache available).
        // Some CompilerToVM methods require different input
        // depending on whether CP cache exists or not.
        for (DummyClasses dummy : DummyClasses.values()) {
            Class.forName(dummy.klass.getName());
        }
        testCase.test();
    }

    private static void validateMethodHandle(ConstantPool constantPoolCTVM,
                                             ConstantTypes cpType,
                                             DummyClasses dummyClass,
                                             int index) {
        Object constantInPool = CompilerToVMHelper.resolveConstantInPool(constantPoolCTVM, index);
        String msg = String.format("%s for index %d", NOT_NULL_MSG, index);
        Asserts.assertNotNull(constantInPool, msg);
        if (!(constantInPool instanceof MethodHandle)) {
            msg = String.format("Wrong constant pool entry accessed by index"
                                        + " %d: %s, but should be subclass of %s",
                                index,
                                constantInPool.getClass(),
                                MethodHandle.class.getName());
            throw new AssertionError(msg);
        }
    }

    private static void validateMethodType(ConstantPool constantPoolCTVM,
                                           ConstantTypes cpType,
                                           DummyClasses dummyClass,
                                           int index) {
        Object constantInPool = CompilerToVMHelper.resolveConstantInPool(constantPoolCTVM, index);
        String msg = String.format("%s for index %d", NOT_NULL_MSG, index);
        Asserts.assertNotNull(constantInPool, msg);
        Class mtToVerify = constantInPool.getClass();
        Class mtToRefer = MethodType.class;
        msg = String.format("Wrong method type class accessed by"
                                    + " constant pool index %d",
                            index);
        Asserts.assertEQ(mtToRefer, mtToVerify, msg);
    }
}
