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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @summary Testing compiler.jvmci.CompilerToVM.lookupKlassInPool method
 * @library /testlibrary /test/lib /
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.common.testcases.MultipleImplementersInterface
 *        compiler.jvmci.common.testcases.MultipleImplementer2
 *        compiler.jvmci.compilerToVM.ConstantPoolTestsHelper
 *        compiler.jvmci.compilerToVM.ConstantPoolTestCase
 *        compiler.jvmci.compilerToVM.LookupKlassInPoolTest
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *                   -XX:+EnableJVMCI compiler.jvmci.compilerToVM.LookupKlassInPoolTest
 */

package compiler.jvmci.compilerToVM;

import java.util.HashMap;
import java.util.Map;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import sun.reflect.ConstantPool;

/**
 * Test for {@code compiler.jvmci.CompilerToVM.lookupKlassInPool} method
 */
public class LookupKlassInPoolTest {

    public static void main(String[] args)  {
        Map<ConstantPoolTestsHelper.ConstantTypes,
                ConstantPoolTestCase.Validator> typeTests = new HashMap<>(1);
        typeTests.put(ConstantPoolTestsHelper.ConstantTypes.CONSTANT_CLASS,
                LookupKlassInPoolTest::validate);
        ConstantPoolTestCase testCase = new ConstantPoolTestCase(typeTests);
        testCase.test();
    }

    public static void validate(jdk.vm.ci.meta.ConstantPool constantPoolCTVM,
            ConstantPool constantPoolSS,
            ConstantPoolTestsHelper.DummyClasses dummyClass, int i) {
        Object classToVerify = CompilerToVMHelper
                .lookupKlassInPool(constantPoolCTVM, i);
        if (!(classToVerify instanceof HotSpotResolvedObjectType)
                && !(classToVerify instanceof String)) {
            String msg = String.format("Output of method"
                    + " CTVM.lookupKlassInPool is neither"
                    + " a HotSpotResolvedObjectType, nor a String");
            throw new AssertionError(msg);
        }
        int classNameIndex = (int) dummyClass.cp.get(i).value;
        String classNameToRefer
                = constantPoolSS.getUTF8At(classNameIndex);
        String outputToVerify = classToVerify.toString();
        if (!outputToVerify.contains(classNameToRefer)) {
            String msg = String.format("Wrong class accessed by constant"
                    + " pool index %d: %s, but should be %s",
                    i, outputToVerify, classNameToRefer);
            throw new AssertionError(msg);
        }
    }
}
