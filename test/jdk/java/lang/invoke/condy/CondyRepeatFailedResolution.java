/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186211
 * @summary Test basic invocation of multiple ldc's of the same dynamic constant that fail resolution
 * @library /java/lang/invoke/common
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 * @run testng CondyRepeatFailedResolution
 * @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 CondyRepeatFailedResolution
 */

import jdk.internal.classfile.Classfile;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.constant.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Test
public class CondyRepeatFailedResolution {
    // Counter used to determine if a given BSM is invoked more than once
    static int bsm_called = 0;

    // Generated class with methods containing condy ldc
    Class<?> gc;

    // Bootstrap method used to represent primitive values
    // that cannot be represented directly in the constant pool,
    // such as byte, and for completeness of testing primitive values
    // that can be represented directly, such as double or long that
    // take two slots
    public static Object intConversion(MethodHandles.Lookup l,
                                       String constantName,
                                       Class<?> constantType,
                                       int value) throws Throwable {
        ++bsm_called;
        // replace constantName with a bogus value to trigger failed resolution
        constantName = "Foo";

        switch (constantName) {
            case "B":
                return (byte) value;
            case "C":
                return (char) value;
            case "D":
                return (double) value;
            case "F":
                return (float) value;
            case "I":
                return value;
            case "J":
                return (long) value;
            case "S":
                return (short) value;
            case "Z":
                return value > 0;
            case "nullRef":
                return null;
            case "string":
                return "string";
            case "stringArray":
                return new String[]{"string", "string"};
            default:
                throw new BootstrapMethodError("Failure to generate a dynamic constant");
        }
    }

    @BeforeClass
    public void generateClass() throws Exception {
        String genClassName = CondyRepeatFailedResolution.class.getSimpleName() + "$Code";
        String bsmClassDesc = CondyRepeatFailedResolution.class.descriptorString();
        String bsmMethodName = "intConversion";
        String bsmDescriptor = MethodType.methodType(Object.class, MethodHandles.Lookup.class,
                String.class, Class.class, int.class).toMethodDescriptorString();
        DirectMethodHandleDesc bsmMhDesc = MethodHandleDesc.of(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.ofDescriptor(bsmClassDesc),
                bsmMethodName,
                bsmDescriptor
        );
        byte[] byteArray = Classfile.of().build(ClassDesc.of(genClassName), classBuilder -> classBuilder
                .withVersion(55, 0)
                .withSuperclass(ConstantDescs.CD_Object)
                .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, Classfile.ACC_PUBLIC,
                        methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .aload(0)
                                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                                ConstantDescs.MTD_void, false)
                                        .return_()
                                )
                )
                .withMethod("B", MethodTypeDesc.of(ConstantDescs.CD_byte),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "B",
                                                ConstantDescs.CD_byte,
                                                (int) Byte.MAX_VALUE))
                                        .ireturn()
                                )
                )
                .withMethod("C", MethodTypeDesc.of(ConstantDescs.CD_char),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "C",
                                                ConstantDescs.CD_char,
                                                (int) Character.MAX_VALUE))
                                        .ireturn()
                                )
                )
                .withMethod("D", MethodTypeDesc.of(ConstantDescs.CD_double),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "D",
                                                ConstantDescs.CD_double,
                                                Integer.MAX_VALUE))
                                        .dreturn()
                                )
                )
                .withMethod("D_AsType", MethodTypeDesc.of(ConstantDescs.CD_double),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "I",
                                                ConstantDescs.CD_double,
                                                Integer.MAX_VALUE))
                                        .dreturn()
                                )
                )
                .withMethod("F", MethodTypeDesc.of(ConstantDescs.CD_float),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "F",
                                                ConstantDescs.CD_float,
                                                Integer.MAX_VALUE))
                                        .freturn()
                                )
                )
                .withMethod("F_AsType", MethodTypeDesc.of(ConstantDescs.CD_float),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "I",
                                                ConstantDescs.CD_float,
                                                Integer.MAX_VALUE))
                                        .freturn()
                                )
                )
                .withMethod("I", MethodTypeDesc.of(ConstantDescs.CD_int),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "I",
                                                ConstantDescs.CD_int,
                                                Integer.MAX_VALUE))
                                        .ireturn()
                                )
                )
                .withMethod("J", MethodTypeDesc.of(ConstantDescs.CD_long),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "J",
                                                ConstantDescs.CD_long,
                                                Integer.MAX_VALUE))
                                        .lreturn()
                                )
                )
                .withMethod("J_AsType", MethodTypeDesc.of(ConstantDescs.CD_long),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "I",
                                                ConstantDescs.CD_long,
                                                Integer.MAX_VALUE))
                                        .lreturn()
                                )
                )
                .withMethod("S", MethodTypeDesc.of(ConstantDescs.CD_short),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "S",
                                                ConstantDescs.CD_short,
                                                ((int) Short.MAX_VALUE)))
                                        .ireturn()
                                )
                )
                .withMethod("Z_F", MethodTypeDesc.of(ConstantDescs.CD_boolean),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "Z",
                                                ConstantDescs.CD_boolean,
                                                0))
                                        .ireturn()
                                )
                )
                .withMethod("Z_T", MethodTypeDesc.of(ConstantDescs.CD_boolean),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "Z",
                                                ConstantDescs.CD_boolean,
                                                1))
                                        .ireturn()
                                )
                )
                .withMethod("null", MethodTypeDesc.of(ConstantDescs.CD_Object),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "nullRef",
                                                ConstantDescs.CD_Object,
                                                Integer.MAX_VALUE))
                                        .areturn()
                                )
                )
                .withMethod("string", MethodTypeDesc.of(ConstantDescs.CD_String),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "string",
                                                ConstantDescs.CD_String,
                                                Integer.MAX_VALUE))
                                        .areturn()
                                )
                )
                .withMethod("stringArray", MethodTypeDesc.of(ConstantDescs.CD_String.arrayType()),
                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
                                .withCode(codeBuilder -> codeBuilder
                                        .ldc(DynamicConstantDesc.ofNamed(
                                                bsmMhDesc,
                                                "stringArray",
                                                ConstantDescs.CD_String.arrayType(),
                                                Integer.MAX_VALUE))
                                        .areturn()
                                )
                )
        );

        gc = MethodHandles.lookup().defineClass(byteArray);
    }

    @Test
    public void testPrimitives() throws Exception {
        testConstants();
    }

    @Test
    public void testRefs() throws Exception {
        testConstant("string", "string");
        testConstant("stringArray", new String[]{"string", "string"});
    }

    void testConstants() throws Exception {
        // Note: for the _asType methods the BSM returns an int which is
        // then converted by an asType transformation

        testConstant("B", Byte.MAX_VALUE);
        testConstant("C", Character.MAX_VALUE);
        testConstant("D", (double) Integer.MAX_VALUE);
        testConstant("D_AsType", (double) Integer.MAX_VALUE);
        testConstant("F", (float) Integer.MAX_VALUE);
        testConstant("F_AsType", (float) Integer.MAX_VALUE);
        testConstant("I", Integer.MAX_VALUE);
        testConstant("J", (long) Integer.MAX_VALUE);
        testConstant("J_AsType", (long) Integer.MAX_VALUE);
        testConstant("S", Short.MAX_VALUE);
        testConstant("Z_F", false);
        testConstant("Z_T", true);
        testConstant("null", null);
    }

    void testConstant(String name, Object expected) throws Exception {
        Method m = gc.getDeclaredMethod(name);

        bsm_called = 0;
        try {
            Object r1 = m.invoke(null);
            Assert.fail("InvocationTargetException expected to be thrown after first invocation");
        } catch (InvocationTargetException e1) {
            // bsm_called should have been incremented prior to the exception
            Assert.assertEquals(bsm_called, 1);
            Assert.assertTrue(e1.getCause() instanceof BootstrapMethodError);
            // Try invoking method again to ensure that the bootstrap
            // method is not invoked twice and a resolution failure
            // results.
            try {
                Object r2 = m.invoke(null);
                Assert.fail("InvocationTargetException expected to be thrown after second invocation");
            } catch (InvocationTargetException e2) {
                // bsm_called should remain at 1 since the bootstrap
                // method should not have been invoked.
                Assert.assertEquals(bsm_called, 1);
                Assert.assertTrue(e2.getCause() instanceof BootstrapMethodError);
            } catch (Throwable t2) {
                Assert.fail("InvocationTargetException expected to be thrown");
            }
        } catch (Throwable t1) {
                Assert.fail("InvocationTargetException expected to be thrown");
        }
    }
}
