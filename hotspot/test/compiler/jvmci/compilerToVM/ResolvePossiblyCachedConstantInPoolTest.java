/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8138708
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library /testlibrary /test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox
 *        compiler.jvmci.compilerToVM.ResolvePossiblyCachedConstantInPoolTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.ResolvePossiblyCachedConstantInPoolTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.compilerToVM.ConstantPoolTestsHelper.DummyClasses;
import compiler.jvmci.compilerToVM.ConstantPoolTestCase.ConstantTypes;
import static compiler.jvmci.compilerToVM.ConstantPoolTestCase.ConstantTypes.*;
import compiler.jvmci.compilerToVM.ConstantPoolTestCase.TestedCPEntry;
import compiler.jvmci.compilerToVM.ConstantPoolTestCase.Validator;
import java.util.HashMap;
import java.util.Map;
import jdk.test.lib.Asserts;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.meta.ConstantPool;

/**
 * Test for {@code jdk.vm.ci.hotspot.CompilerToVM.resolvePossiblyCachedConstantInPool} method
 */
public class ResolvePossiblyCachedConstantInPoolTest {

    public static void main(String[] args) throws Exception {
        Map<ConstantTypes, Validator> typeTests = new HashMap<>();
        typeTests.put(CONSTANT_STRING, ResolvePossiblyCachedConstantInPoolTest::validateString);
        ConstantPoolTestCase testCase = new ConstantPoolTestCase(typeTests);
        // The next "Class.forName" is here for the following reason.
        // When class is initialized, constant pool cache is available.
        // This method works only with cached constant pool.
        for (DummyClasses dummy : DummyClasses.values()) {
            Class.forName(dummy.klass.getName());
        }
        testCase.test();
    }

    private static void validateString(ConstantPool constantPoolCTVM,
                                       ConstantTypes cpType,
                                       DummyClasses dummyClass,
                                       int cpi) {
        TestedCPEntry entry = cpType.getTestedCPEntry(dummyClass, cpi);
        if (entry == null) {
            return;
        }
        int index = cpi;
        String cached = "";
        int cpci = dummyClass.getCPCacheIndex(cpi);
        if (cpci != ConstantPoolTestsHelper.NO_CP_CACHE_PRESENT) {
            index = cpci;
            cached = "cached ";
        }
        Object constantInPool = CompilerToVMHelper.resolvePossiblyCachedConstantInPool(constantPoolCTVM, index);
        String stringToVerify = (String) constantInPool;
        String stringToRefer = entry.name;
        if (stringToRefer.equals("") && cpci != ConstantPoolTestsHelper.NO_CP_CACHE_PRESENT) {
            stringToRefer = null; // tested method returns null for cached empty strings
        }
        String msg = String.format("Wrong string accessed by %sconstant pool index %d", cached, index);
        Asserts.assertEQ(stringToRefer, stringToVerify, msg);
    }
}
