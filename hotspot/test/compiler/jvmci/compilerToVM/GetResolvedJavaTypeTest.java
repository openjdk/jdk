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
 * @library / /testlibrary /test/lib
 * @compile ../common/CompilerToVMHelper.java
 *          ../common/PublicMetaspaceWrapperObject.java
 * @build compiler.jvmci.compilerToVM.GetResolvedJavaTypeTest
 * @run main ClassFileInstaller
 *      sun.hotspot.WhiteBox
 *      sun.hotspot.WhiteBox$WhiteBoxPermission
 *      jdk.vm.ci.hotspot.CompilerToVMHelper
 *      jdk.vm.ci.hotspot.PublicMetaspaceWrapperObject
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *      -XX:+EnableJVMCI -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:+UseCompressedOops
 *      compiler.jvmci.compilerToVM.GetResolvedJavaTypeTest
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockExperimentalVMOptions
 *      -XX:+EnableJVMCI -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:-UseCompressedOops
 *      compiler.jvmci.compilerToVM.GetResolvedJavaTypeTest
 */

package compiler.jvmci.compilerToVM;

import java.lang.reflect.Field;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.PublicMetaspaceWrapperObject;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import sun.misc.Unsafe;

public class GetResolvedJavaTypeTest {
    private static enum TestCase {
        NULL_BASE {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                return CompilerToVMHelper.getResolvedJavaType(
                        null, getPtrToKlass(), COMPRESSED);
            }
        },
        JAVA_METHOD_BASE {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                HotSpotResolvedJavaMethod methodInstance
                        = CompilerToVMHelper.getResolvedJavaMethodAtSlot(
                        TEST_CLASS, 0);
                Field field;
                try {
                    // jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl.metaspaceMethod
                    field = methodInstance.getClass()
                            .getDeclaredField("metaspaceMethod");
                    field.setAccessible(true);
                    field.set(methodInstance, getPtrToKlass());
                } catch (ReflectiveOperationException e) {
                    throw new Error("TEST BUG : " + e, e);
                }

                return CompilerToVMHelper.getResolvedJavaType(methodInstance,
                        0L, COMPRESSED);
            }
        },
        CONSTANT_POOL_BASE {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                ConstantPool cpInst;
                try {
                    cpInst = CompilerToVMHelper.getConstantPool(null,
                            getPtrToKlass());
                    // jdk.vm.ci.hotspot.HotSpotConstantPool.metaspaceConstantPool
                    Field field = cpInst.getClass()
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, getPtrToKlass());
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e, e);
                }
                return CompilerToVMHelper.getResolvedJavaType(cpInst,
                        0L, COMPRESSED);
            }
        },
        CONSTANT_POOL_BASE_IN_TWO {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                long ptr = getPtrToKlass();
                ConstantPool cpInst = HotSpotResolvedObjectType
                        .fromObjectClass(TEST_CLASS)
                        .getConstantPool();
                try {
                    Field field = cpInst.getClass()
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, ptr / 2L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e, e);
                }
                return CompilerToVMHelper.getResolvedJavaType(cpInst,
                        ptr - ptr / 2L, COMPRESSED);
            }
        },
        CONSTANT_POOL_BASE_ZERO {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                long ptr = getPtrToKlass();
                ConstantPool cpInst = HotSpotResolvedObjectType
                        .fromObjectClass(TEST_CLASS)
                        .getConstantPool();
                try {
                    Field field = cpInst.getClass()
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, 0L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e, e);
                }
                return CompilerToVMHelper.getResolvedJavaType(cpInst,
                        ptr, COMPRESSED);
            }
        },
        OBJECT_TYPE_BASE {
            @Override
            HotSpotResolvedObjectType getResolvedJavaType() {
                HotSpotResolvedObjectType type
                        = HotSpotResolvedObjectType.fromObjectClass(
                        OBJECT_TYPE_BASE.getClass());
                long ptrToClass = UNSAFE.getKlassPointer(OBJECT_TYPE_BASE);
                return CompilerToVMHelper.getResolvedJavaType(type,
                        getPtrToKlass() - ptrToClass, COMPRESSED);
            }
        },
        ;
        abstract HotSpotResolvedObjectType getResolvedJavaType();
    }

    private static final Unsafe UNSAFE = Utils.getUnsafe();
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final long PTR = UNSAFE.getKlassPointer(
            new GetResolvedJavaTypeTest());
    private static final Class TEST_CLASS = GetResolvedJavaTypeTest.class;
    /* a compressed parameter for tested method is set to false because
       unsafe.getKlassPointer always returns uncompressed pointer */
    private static final boolean COMPRESSED = false;
            // = WB.getBooleanVMFlag("UseCompressedClassPointers");

    private static long getPtrToKlass() {
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
        HotSpotResolvedObjectType type = testCase.getResolvedJavaType();
        Asserts.assertEQ(TEST_CLASS,
                CompilerToVMHelper.getMirror(type),
                testCase +  " : unexpected class returned");
    }

    public static void main(String[] args) {
        GetResolvedJavaTypeTest test = new GetResolvedJavaTypeTest();
        for (TestCase testCase : TestCase.values()) {
            test.test(testCase);
        }
        testObjectBase();
        testMetaspaceWrapperBase();
    }

    private static void testMetaspaceWrapperBase() {
        try {
            HotSpotResolvedObjectType type
                    = CompilerToVMHelper.getResolvedJavaType(
                            new PublicMetaspaceWrapperObject() {
                                @Override
                                public long getMetaspacePointer() {
                                    return getPtrToKlass();
                                }
                            }, 0L, COMPRESSED);
            throw new AssertionError("Test METASPACE_WRAPPER_BASE."
                    + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    private static void testObjectBase() {
        try {
            HotSpotResolvedObjectType type
                    = CompilerToVMHelper.getResolvedJavaType(new Object(), 0L,
                            COMPRESSED);
            throw new AssertionError("Test OBJECT_BASE."
                + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }
}
