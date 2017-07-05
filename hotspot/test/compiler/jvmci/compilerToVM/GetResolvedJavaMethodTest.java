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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library / /testlibrary /../../test/lib
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.GetResolvedJavaMethodTest
 * @run main ClassFileInstaller
 *      sun.hotspot.WhiteBox
 *      sun.hotspot.WhiteBox$WhiteBoxPermission
 *      jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *      -XX:+EnableJVMCI -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.jvmci.compilerToVM.GetResolvedJavaMethodTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;
import jdk.vm.ci.hotspot.MetaspaceWrapperObject;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class GetResolvedJavaMethodTest {
    private static enum TestCase {
        NULL_BASE {
            @Override
            HotSpotResolvedJavaMethodImpl getResolvedJavaMethod() {
                return CompilerToVMHelper.getResolvedJavaMethod(
                        null, getPtrToMethod());
            }
        },
        JAVA_METHOD_BASE {
            @Override
            HotSpotResolvedJavaMethodImpl getResolvedJavaMethod() {
                HotSpotResolvedJavaMethodImpl methodInstance
                        = CompilerToVMHelper.getResolvedJavaMethodAtSlot(
                       TEST_CLASS, 0);
                Field field;
                try {
                    field = HotSpotResolvedJavaMethodImpl
                            .class.getDeclaredField("metaspaceMethod");
                    field.setAccessible(true);
                    field.set(methodInstance, getPtrToMethod());
                } catch (ReflectiveOperationException e) {
                    throw new Error("TEST BUG : " + e, e);
                }
                return CompilerToVMHelper.getResolvedJavaMethod(
                        methodInstance, 0L);
            }
        },
        JAVA_METHOD_BASE_IN_TWO {
            @Override
            HotSpotResolvedJavaMethodImpl getResolvedJavaMethod() {
                long ptr = getPtrToMethod();
                HotSpotResolvedJavaMethodImpl methodInstance
                        = CompilerToVMHelper.getResolvedJavaMethodAtSlot(
                        TEST_CLASS, 0);
                Field field;
                try {
                    field = HotSpotResolvedJavaMethodImpl
                            .class.getDeclaredField("metaspaceMethod");
                    field.setAccessible(true);
                    field.set(methodInstance, ptr / 2L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }
                return CompilerToVMHelper.getResolvedJavaMethod(methodInstance,
                        ptr - ptr / 2L);
            }
        },
        JAVA_METHOD_BASE_ZERO {
            @Override
            HotSpotResolvedJavaMethodImpl getResolvedJavaMethod() {
                long ptr = getPtrToMethod();
                HotSpotResolvedJavaMethodImpl methodInstance
                        = CompilerToVMHelper.getResolvedJavaMethodAtSlot(
                        TEST_CLASS, 0);
                Field field;
                try {
                    field = HotSpotResolvedJavaMethodImpl
                            .class.getDeclaredField("metaspaceMethod");
                    field.setAccessible(true);
                    field.set(methodInstance, 0L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }
                return CompilerToVMHelper.getResolvedJavaMethod(methodInstance,
                        ptr);
            }
        }
        ;
        abstract HotSpotResolvedJavaMethodImpl getResolvedJavaMethod();
    }

    private static final Unsafe UNSAFE = Utils.getUnsafe();
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Class<?> TEST_CLASS = GetResolvedJavaMethodTest.class;
    private static final long PTR;
    static  {
        HotSpotResolvedJavaMethodImpl method
                = CompilerToVMHelper.getResolvedJavaMethodAtSlot(TEST_CLASS, 0);
        PTR = method.getMetaspacePointer();
    }

    private static long getPtrToMethod() {
        Field field;
        try {
            field = TEST_CLASS.getDeclaredField("PTR");
        } catch (NoSuchFieldException e) {
            throw new Error("TEST BUG : " + e, e);
        }
        Object base = UNSAFE.staticFieldBase(field);
        return WB.getObjectAddress(base) + UNSAFE.staticFieldOffset(field);
    }

    public void test(TestCase testCase) {
        System.out.println(testCase.name());
        HotSpotResolvedJavaMethodImpl result = testCase.getResolvedJavaMethod();
        Asserts.assertNotNull(result, testCase + " : got null");
        Asserts.assertEQ(result.getDeclaringClass().mirror(), TEST_CLASS,
                testCase + " : returned method has unexpected declaring class");
    }

    public static void main(String[] args) {
        GetResolvedJavaMethodTest test = new GetResolvedJavaMethodTest();
        for (TestCase testCase : TestCase.values()) {
            test.test(testCase);
        }
        testObjectBase();
        testMetaspaceWrapperBase();
    }

    private static void testMetaspaceWrapperBase() {
        try {
            HotSpotResolvedJavaMethodImpl method
                    = CompilerToVMHelper.getResolvedJavaMethod(
                            new MetaspaceWrapperObject() {
                                @Override
                                public long getMetaspacePointer() {
                                    return getPtrToMethod();
                                }
                            }, 0L);
            throw new AssertionError("Test METASPACE_WRAPPER_BASE."
                    + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    private static void testObjectBase() {
        try {
            HotSpotResolvedJavaMethodImpl method
                    = CompilerToVMHelper.getResolvedJavaMethod(new Object(), 0L);
            throw new AssertionError("Test OBJECT_BASE."
                + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }
}
