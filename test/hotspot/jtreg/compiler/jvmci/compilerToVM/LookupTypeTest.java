/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.jvmci
 * @library / /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.access
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 * @build jdk.internal.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   -XX:-UseJVMCICompiler
 *                   compiler.jvmci.compilerToVM.LookupTypeTest
 */

package compiler.jvmci.compilerToVM;

import compiler.jvmci.common.testcases.DoNotExtendClass;
import compiler.jvmci.common.testcases.MultiSubclassedClass;
import compiler.jvmci.common.testcases.SingleSubclass;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class LookupTypeTest {

    /**
     * Abstracts which lookup method is being tested.
     */
    public interface Lookup {
        ResolvedJavaType lookupType(String name, Class<?> accessingClass, boolean resolve);
    }

    public static void main(String args[]) {
        LookupTypeTest test = new LookupTypeTest();

        // Test CompilerToVM.lookupType
        for (TestCase tcase : createTestCases(false, true)) {
            test.runTest(tcase, CompilerToVMHelper::lookupType);
        }

        // Test HotSpotJVMCIRuntime.lookupType
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
        for (TestCase tcase : createTestCases(true, false)) {
            test.runTest(tcase, (name, accessingClass, resolve) -> (ResolvedJavaType) runtime.lookupType(name,
                (HotSpotResolvedObjectType) metaAccess.lookupJavaType(accessingClass), resolve));
        }
    }

    private static List<TestCase> createTestCases(boolean allowPrimitive, boolean allowNullAccessingClass) {
        List<TestCase> result = new ArrayList<>();
        // a primitive class
        if (allowPrimitive) {
            result.add(new TestCase(Utils.toJVMTypeSignature(int.class),
                LookupTypeTest.class, true, true));
        } else {
            result.add(new TestCase(Utils.toJVMTypeSignature(int.class),
                LookupTypeTest.class, true, false, InternalError.class));
        }
        // lookup not existing class
        result.add(new TestCase("Lsome_not_existing;", LookupTypeTest.class,
                true, false, NoClassDefFoundError.class));
        // lookup invalid classname
        result.add(new TestCase("L!@#$%^&**()[]{}?;", LookupTypeTest.class,
                true, false, NoClassDefFoundError.class));
        // lookup package private class
        result.add(new TestCase(
                "Lcompiler/jvmci/compilerToVM/testcases/PackagePrivateClass;",
                LookupTypeTest.class, true, false,
                NoClassDefFoundError.class));
        // lookup usual class with resolve=true
        result.add(new TestCase(Utils.toJVMTypeSignature(SingleSubclass.class),
                LookupTypeTest.class, true, true));
        // lookup usual class with resolve=false
        result.add(new TestCase(
                Utils.toJVMTypeSignature(DoNotExtendClass.class),
                LookupTypeTest.class, false, true));
        // lookup usual class with null accessor
        if (allowNullAccessingClass) {
            result.add(new TestCase(
                Utils.toJVMTypeSignature(MultiSubclassedClass.class), null,
                false, false, NullPointerException.class));
        }
        return result;
    }

    private void runTest(TestCase tcase, Lookup lookup) {
        System.out.println(tcase);
        ResolvedJavaType metaspaceKlass;
        try {
            metaspaceKlass = lookup.lookupType(tcase.className,
                    tcase.accessing, tcase.resolve);
        } catch (Throwable t) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(bos));
            String tString = bos.toString();
            Asserts.assertNotNull(tcase.expectedException,
                    "Assumed no exception, but got " + tString);
            Asserts.assertFalse(tcase.isPositive,
                    "Got unexpected exception " + tString);
            Asserts.assertEQ(t.getClass(), tcase.expectedException,
                    "Unexpected exception: " + tString);
            // passed
            return;
        }
        if (tcase.expectedException != null) {
            throw new AssertionError("Expected exception was not thrown: "
                    + tcase.expectedException.getName());
        }
        if (tcase.isPositive) {
            Asserts.assertNotNull(metaspaceKlass,
                    "Unexpected null metaspace klass");
            Asserts.assertEQ(metaspaceKlass.getName(), tcase.className,
                    "Got unexpected resolved class name");
        } else {
            Asserts.assertNull(metaspaceKlass, "Unexpected metaspace klass");
        }
    }

    private static class TestCase {
        public final String className;
        public final Class<?> accessing;
        public final boolean resolve;
        public final boolean isPositive;
        public final Class<? extends Throwable> expectedException;

        public TestCase(String className, Class<?> accessing, boolean resolve,
                boolean isPositive,
                Class<? extends Throwable> expectedException) {
            this.className = className;
            this.accessing = accessing;
            this.resolve = resolve;
            this.isPositive = isPositive;
            this.expectedException = expectedException;
        }

        public TestCase(String className, Class<?> accessing, boolean resolve,
                boolean isPositive) {
            this.className = className;
            this.accessing = accessing;
            this.resolve = resolve;
            this.isPositive = isPositive;
            this.expectedException = null;
        }

        @Override
        public String toString() {
            return String.format("CASE: class=%s, accessing=%s,"
                + " resolve=%s, positive=%s, expectedException=%s", className,
                accessing, resolve, isPositive, expectedException);
        }
    }
}
