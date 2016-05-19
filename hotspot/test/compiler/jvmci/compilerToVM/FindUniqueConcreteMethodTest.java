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
 */

/*
 * @test
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.tree
 *          jdk.vm.ci/jdk.vm.ci.hotspot
 *          jdk.vm.ci/jdk.vm.ci.code
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @build compiler.jvmci.compilerToVM.FindUniqueConcreteMethodTest
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.FindUniqueConcreteMethodTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.testcases.MultipleImplementer1;
import compiler.jvmci.common.testcases.SingleImplementer;
import compiler.jvmci.common.testcases.SingleSubclass;
import compiler.jvmci.common.CTVMUtilities;
import compiler.jvmci.common.testcases.SingleImplementerInterface;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class FindUniqueConcreteMethodTest {
    public static void main(String args[]) {
        FindUniqueConcreteMethodTest test = new FindUniqueConcreteMethodTest();
        try {
            for (TestCase tcase : createTestCases()) {
                test.runTest(tcase);
            }
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: can't find method", e);
        }
    }

    private static Set<TestCase> createTestCases() {
        Set<TestCase> result = new HashSet<>();
        // a public method
        result.add(new TestCase(true, SingleSubclass.class,
                SingleSubclass.class, "usualMethod"));
        // overriden method
        result.add(new TestCase(true, SingleSubclass.class,
                SingleSubclass.class, "overridenMethod"));
        // private method
        result.add(new TestCase(true, SingleSubclass.class,
                SingleSubclass.class, "privateMethod"));
        // protected method
        result.add(new TestCase(true, SingleSubclass.class,
                SingleSubclass.class, "protectedMethod"));
        // default(package-private) method
        result.add(new TestCase(true, SingleSubclass.class,
                SingleSubclass.class, "defaultAccessMethod"));
        // default interface method redefined in implementer
        result.add(new TestCase(true, MultipleImplementer1.class,
                MultipleImplementer1.class, "defaultMethod"));
        // interface method
        result.add(new TestCase(true, MultipleImplementer1.class,
                MultipleImplementer1.class, "testMethod"));
        // default interface method not redefined in implementer
        result.add(new TestCase(true, SingleImplementer.class,
                SingleImplementerInterface.class, "defaultMethod"));
        // static method
        result.add(new TestCase(false, SingleSubclass.class,
                SingleSubclass.class, "staticMethod"));
        return result;
    }

    private void runTest(TestCase tcase) throws NoSuchMethodException {
        System.out.println(tcase);
        Method method = tcase.holder.getDeclaredMethod(tcase.methodName);
        HotSpotResolvedJavaMethod testMethod = CTVMUtilities
                .getResolvedMethod(tcase.receiver, method);
        HotSpotResolvedObjectType resolvedType = CompilerToVMHelper
                .lookupType(Utils.toJVMTypeSignature(tcase.receiver), getClass(),
                /* resolve = */ true);
        HotSpotResolvedJavaMethod concreteMethod = CompilerToVMHelper
                .findUniqueConcreteMethod(resolvedType, testMethod);
        Asserts.assertEQ(concreteMethod, tcase.isPositive ? testMethod : null,
                "Unexpected concrete method for " + tcase.methodName);
    }

    private static class TestCase {
        public final Class<?> receiver;
        public final Class<?> holder;
        public final String methodName;
        public final boolean isPositive;

        public TestCase(boolean isPositive, Class<?> clazz, Class<?> holder,
                String methodName) {
            this.receiver = clazz;
            this.methodName = methodName;
            this.isPositive = isPositive;
            this.holder = holder;
        }

        @Override
        public String toString() {
            return String.format("CASE: receiver=%s, holder=%s, method=%s,"
                    + " isPositive=%s", receiver.getName(),
                    holder.getName(), methodName, isPositive);
        }
    }
}
