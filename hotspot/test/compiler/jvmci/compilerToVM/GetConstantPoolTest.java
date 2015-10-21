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
 * @library /testlibrary /../../test/lib /
 * @ignore 8139385
 * @compile ../common/CompilerToVMHelper.java
 * @build sun.hotspot.WhiteBox
 *        compiler.jvmci.compilerToVM.GetConstantPoolTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions
 *                   -XX:+EnableJVMCI compiler.jvmci.compilerToVM.GetConstantPoolTest
 */
package compiler.jvmci.compilerToVM;

import java.lang.reflect.Field;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotConstantPool;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethodImpl;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl;
import jdk.vm.ci.hotspot.MetaspaceWrapperObject;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import sun.misc.Unsafe;

/**
 * Tests for jdk.vm.ci.hotspot.CompilerToVM::getConstantPool method
 */
public class GetConstantPoolTest {
    private static enum TestCase {
        NULL_BASE {
            @Override
            HotSpotConstantPool getConstantPool() {
                return CompilerToVMHelper.getConstantPool(null,
                        getPtrToCpAddress());
            }
        },
        JAVA_METHOD_BASE {
            @Override
            HotSpotConstantPool getConstantPool() {
                HotSpotResolvedJavaMethodImpl methodInstance
                        = CompilerToVMHelper.getResolvedJavaMethodAtSlot(
                                TEST_CLASS, 0);
                Field field;
                try {
                    field = HotSpotResolvedJavaMethodImpl
                            .class.getDeclaredField("metaspaceMethod");
                    field.setAccessible(true);
                    field.set(methodInstance, getPtrToCpAddress());
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }

                return CompilerToVMHelper.getConstantPool(methodInstance, 0L);
            }
        },
        CONSTANT_POOL_BASE {
            @Override
            HotSpotConstantPool getConstantPool() {
                HotSpotConstantPool cpInst;
                try {
                    cpInst = CompilerToVMHelper.getConstantPool(null,
                            getPtrToCpAddress());
                    Field field = HotSpotConstantPool.class
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, getPtrToCpAddress());
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }
                return CompilerToVMHelper.getConstantPool(cpInst, 0L);
            }
        },
        CONSTANT_POOL_BASE_IN_TWO {
            @Override
            HotSpotConstantPool getConstantPool() {
                long ptr = getPtrToCpAddress();
                HotSpotConstantPool cpInst;
                try {
                    cpInst = CompilerToVMHelper.getConstantPool(null, ptr);
                    Field field = HotSpotConstantPool.class
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, ptr / 2L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }
                return CompilerToVMHelper.getConstantPool(cpInst,
                        ptr - ptr / 2L);
            }
        },
        CONSTANT_POOL_BASE_ZERO {
            @Override
            HotSpotConstantPool getConstantPool() {
                long ptr = getPtrToCpAddress();
                HotSpotConstantPool cpInst;
                try {
                    cpInst = CompilerToVMHelper.getConstantPool(null, ptr);
                    Field field = HotSpotConstantPool.class
                            .getDeclaredField("metaspaceConstantPool");
                    field.setAccessible(true);
                    field.set(cpInst, 0L);
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG : " + e.getMessage(), e);
                }
                return CompilerToVMHelper.getConstantPool(cpInst, ptr);
            }
        },
        OBJECT_TYPE_BASE {
            @Override
            HotSpotConstantPool getConstantPool() {
                HotSpotResolvedObjectTypeImpl type
                        = HotSpotResolvedObjectTypeImpl.fromObjectClass(
                                OBJECT_TYPE_BASE.getClass());
                long ptrToClass = UNSAFE.getKlassPointer(OBJECT_TYPE_BASE);
                return CompilerToVMHelper.getConstantPool(type,
                        getPtrToCpAddress() - ptrToClass);
            }
        },
        ;
        abstract HotSpotConstantPool getConstantPool();
    }

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final Unsafe UNSAFE = Utils.getUnsafe();
    private static final Class TEST_CLASS = GetConstantPoolTest.class;
    private static final long CP_ADDRESS
            = WB.getConstantPool(GetConstantPoolTest.class);

    public void test(TestCase testCase) {
        System.out.println(testCase.name());
        HotSpotConstantPool cp = testCase.getConstantPool();
        String cpStringRep = cp.toString();
        if (!cpStringRep.contains(HotSpotConstantPool.class.getSimpleName())
                || !cpStringRep.contains(TEST_CLASS.getName())) {
            String msg = String.format("%s : "
                    + " Constant pool is not valid."
                    + " String representation should contain \"%s\" and \"%s\"",
                    testCase.name(),
                    HotSpotConstantPool.class.getSimpleName(),
                    TEST_CLASS.getName());
            throw new AssertionError(msg);
        }
    }

    public static void main(String[] args) {
        GetConstantPoolTest test = new GetConstantPoolTest();
        for (TestCase testCase : TestCase.values()) {
            test.test(testCase);
        }
        testObjectBase();
        testMetaspaceWrapperBase();
    }

    private static void testObjectBase() {
        try {
            HotSpotConstantPool cp
                    = CompilerToVMHelper.getConstantPool(new Object(), 0L);
            throw new AssertionError("Test OBJECT_BASE."
                + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }
    private static void testMetaspaceWrapperBase() {
        try {
            HotSpotConstantPool cp = CompilerToVMHelper.getConstantPool(
                    new MetaspaceWrapperObject() {
                        @Override
                        public long getMetaspacePointer() {
                            return getPtrToCpAddress();
                        }
                    }, 0L);
            throw new AssertionError("Test METASPACE_WRAPPER_BASE."
                + " Expected IllegalArgumentException has not been caught");
        } catch (IllegalArgumentException iae) {
            // expected
        }
    }

    private static long getPtrToCpAddress() {
        Field field;
        try {
            field = TEST_CLASS.getDeclaredField("CP_ADDRESS");
        } catch (NoSuchFieldException nsfe) {
            throw new Error("TESTBUG : cannot find field \"CP_ADDRESS\" : "
                    + nsfe.getMessage(), nsfe);
        }
        Object base = UNSAFE.staticFieldBase(field);
        return WB.getObjectAddress(base) + UNSAFE.staticFieldOffset(field);
    }
}
