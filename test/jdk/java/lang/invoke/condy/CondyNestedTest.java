/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186046
 * @summary Test nested dynamic constant declarations that are recursive
 * @compile CondyNestedTest_Code.jcod
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 * @run testng CondyNestedTest
 * @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:UseBootstrapCallInfo=3 CondyNestedTest
 */

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CondyNestedTest {

    static final Class[] THROWABLES = {InvocationTargetException.class, StackOverflowError.class};
    private static final MethodHandles.Lookup L = MethodHandles.lookup();

    Class<?> c;

//    static final MethodHandles.Lookup L = MethodHandles.lookup();
//
//    /**
//     * Generate class file bytes for a class named CondyNestedTest_Code
//     * whose bytes are converted to a jcod file:
//     *
//     * java -jar asmtools.jar jdec CondyNestedTest_Code.class >
//     * CondyNestedTest_Code.jcod
//     *
//     * which was then edited so that dynamic constant declarations are
//     * recursive both for an ldc or invokedynamic (specifically declaring a
//     * BSM+attributes whose static argument is a dynamic constant
//     * that refers to the same BSM+attributes).
//     */
//    public static byte[] generator() throws Exception {
//        String genClassName = L.lookupClass().getSimpleName() + "_Code";
//        ClassDesc genClassDesc = ClassDesc.of(genClassName);
//        String bsmDescriptor = MethodType.methodType(Object.class, MethodHandles.Lookup.class, String.class, Object.class,
//                Object.class).toMethodDescriptorString();
//        String bsmIndyDescriptor = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
//                Object.class, Object.class).toMethodDescriptorString();
//        DirectMethodHandleDesc bsmMhDesc = MethodHandleDesc.of(
//                DirectMethodHandleDesc.Kind.STATIC,
//                genClassDesc,
//                "bsm",
//                bsmDescriptor
//        );
//        DirectMethodHandleDesc bsmIndyMhDesc = MethodHandleDesc.of(
//                DirectMethodHandleDesc.Kind.STATIC,
//                genClassDesc,
//                "bsmIndy",
//                bsmIndyDescriptor
//        );
//        byte[] byteArray = Classfile.of().build(ClassDesc.of(genClassName), classBuilder -> classBuilder
//                .withVersion(55, 0)
//                .withSuperclass(ConstantDescs.CD_Object)
//                .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, Classfile.ACC_PUBLIC, methodBuilder -> methodBuilder
//                        .withCode(codeBuilder -> codeBuilder
//                                .aload(0)
//                                .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
//                                .return_()
//                        )
//                )
//                .withMethod("main", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType()),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> {
//                                            codeBuilder
//                                                    .aload(0)
//                                                    .iconst_0()
//                                                    .aaload()
//                                                    .invokevirtual(ConstantDescs.CD_String, "intern",
//                                                            MethodTypeDesc.of(ConstantDescs.CD_String))
//                                                    .astore(1);
//                                            Label case1 = codeBuilder.newLabel();
//                                            codeBuilder
//                                                    .aload(1)
//                                                    .ldc("condy_bsm_condy_bsm")
//                                                    .if_acmpne(case1)
//                                                    .invokestatic(genClassDesc, "condy_bsm_condy_bsm",
//                                                            MethodTypeDesc.of(ConstantDescs.CD_Object))
//                                                    .return_();
//                                            Label case2 = codeBuilder.newLabel();
//                                            codeBuilder
//                                                    .labelBinding(case1)
//                                                    .aload(1)
//                                                    .ldc("indy_bsmIndy_condy_bsm")
//                                                    .if_acmpne(case2)
//                                                    .invokestatic(genClassDesc, "indy_bsmIndy_condy_bsm",
//                                                            MethodTypeDesc.of(ConstantDescs.CD_Object))
//                                                    .return_();
//                                            Label case3 = codeBuilder.newLabel();
//                                            codeBuilder
//                                                    .labelBinding(case2)
//                                                    .aload(1)
//                                                    .ldc("indy_bsm_condy_bsm")
//                                                    .if_acmpne(case3)
//                                                    .invokestatic(genClassDesc, "indy_bsm_condy_bsm",
//                                                            MethodTypeDesc.of(ConstantDescs.CD_Object))
//                                                    .return_();
//                                            codeBuilder
//                                                    .labelBinding(case3)
//                                                    .return_();
//                                        }
//                                )
//                )
//                // bsm that when used with indy returns a call site whose target is MethodHandles.constant(String.class, name), and
//                // when used with condy returns the name
//                .withMethod("bsm", MethodTypeDesc.ofDescriptor(bsmDescriptor),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> {
//                                            codeBuilder
//                                                    .aload(2)
//                                                    .instanceof_(ConstantDescs.CD_MethodType)
//                                                    .iconst_0();
//                                            Label condy = codeBuilder.newLabel();
//                                            codeBuilder
//                                                    .if_acmpeq(condy)
//                                                    .new_(ClassDesc.ofDescriptor(ConstantCallSite.class.descriptorString()))
//                                                    .dup()
//                                                    .ldc(ConstantDescs.CD_String)
//                                                    .aload(1)
//                                                    .invokestatic(ConstantDescs.CD_MethodHandles, "constant",
//                                                            MethodTypeDesc.of(ConstantDescs.CD_MethodHandle, ConstantDescs.CD_Class,
//                                                                    ConstantDescs.CD_Object))
//                                                    .invokespecial(ClassDesc.ofDescriptor(ConstantCallSite.class.descriptorString()),
//                                                            ConstantDescs.INIT_NAME,
//                                                            MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_MethodHandle))
//                                                    .areturn();
//                                            codeBuilder
//                                                    .labelBinding(condy)
//                                                    .aload(1)
//                                                    .areturn();
//                                        }
//
//                                )
//                )
//                // an indy bsm, that returns a call site whose target is MethodHandles.constant(String.class, methodName)
//                .withMethod("bsmIndy", MethodTypeDesc.ofDescriptor(bsmIndyDescriptor),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_PUBLIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> codeBuilder
//                                        .new_(ClassDesc.ofDescriptor(ConstantCallSite.class.descriptorString()))
//                                        .dup()
//                                        .ldc(ConstantDescs.CD_String)
//                                        .aload(1)
//                                        .invokestatic(ConstantDescs.CD_MethodHandles, "constant",
//                                                MethodTypeDesc.of(ConstantDescs.CD_MethodHandle, ConstantDescs.CD_Class,
//                                                        ConstantDescs.CD_Object))
//                                        .invokespecial(ClassDesc.ofDescriptor(ConstantCallSite.class.descriptorString()),
//                                                ConstantDescs.INIT_NAME,
//                                                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_MethodHandle))
//                                        .areturn()
//                                )
//                )
//                .withMethod("condy_bsm_condy_bsm", MethodTypeDesc.of(ConstantDescs.CD_Object),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> codeBuilder
//                                        .ldc(DynamicConstantDesc.ofNamed(
//                                                        bsmMhDesc,
//                                                        "name",
//                                                        ConstantDescs.CD_String,
//                                                        DynamicConstantDesc.ofNamed(
//                                                                bsmMhDesc,
//                                                                "name",
//                                                                ConstantDescs.CD_String,
//                                                                "DUMMY_ARG"
//                                                        )
//                                                )
//                                        )
//                                        .areturn()
//                                )
//                )
//                .withMethod("indy_bsmIndy_condy_bsm", MethodTypeDesc.of(ConstantDescs.CD_Object),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> codeBuilder
//                                        .invokedynamic(DynamicCallSiteDesc.of(
//                                                        bsmIndyMhDesc,
//                                                        "name",
//                                                        MethodTypeDesc.of(ConstantDescs.CD_String),
//                                                        DynamicConstantDesc.ofNamed(
//                                                                bsmMhDesc,
//                                                                "name",
//                                                                ConstantDescs.CD_String,
//                                                                "DUMMY_ARG"
//                                                        )
//                                                )
//                                        )
//                                        .areturn()
//                                )
//                )
//                .withMethod("indy_bsm_condy_bsm", MethodTypeDesc.of(ConstantDescs.CD_Object),
//                        Classfile.ACC_PUBLIC + Classfile.ACC_STATIC, methodBuilder -> methodBuilder
//                                .withCode(codeBuilder -> codeBuilder
//                                        .invokedynamic(DynamicCallSiteDesc.of(
//                                                        bsmMhDesc,
//                                                        "name",
//                                                        MethodTypeDesc.of(ConstantDescs.CD_String),
//                                                        DynamicConstantDesc.ofNamed(
//                                                                bsmMhDesc,
//                                                                "name",
//                                                                ConstantDescs.CD_String,
//                                                                "DUMMY_ARG"
//                                                        )
//                                                )
//                                        )
//                                        .areturn()
//                                )
//                )
//        );
//
//        File f = new File(genClassName + ".class");
//        if (f.getParentFile() != null) {
//            f.getParentFile().mkdirs();
//        }
//        new FileOutputStream(f).write(byteArray);
//        return byteArray;
//    }

    static void test(Method m, Class<? extends Throwable>... ts) {
        Throwable caught = null;
        try {
            m.invoke(null);
        } catch (Throwable t) {
            caught = t;
        }

        if (caught == null) {
            Assert.fail("Throwable expected");
        }

        String actualMessage = null;
        for (int i = 0; i < ts.length; i++) {
            actualMessage = caught.getMessage();
            Assert.assertNotNull(caught);
            Assert.assertTrue(ts[i].isAssignableFrom(caught.getClass()));
            caught = caught.getCause();
        }
    }

    @BeforeClass
    public void findClass() throws Exception {
        c = Class.forName("CondyNestedTest_Code");
    }

    /**
     * Testing an ldc of a dynamic constant, C say, with a BSM whose static
     * argument is C.
     */
    @Test
    public void testCondyBsmCondyBsm() throws Exception {
        test("condy_bsm_condy_bsm", THROWABLES);
    }

    /**
     * Testing an invokedynamic with a BSM whose static argument is a constant
     * dynamic, C say, with a BSM whose static argument is C.
     */
    @Test
    public void testIndyBsmIndyCondyBsm() throws Exception {
        test("indy_bsmIndy_condy_bsm", THROWABLES);
    }

    /**
     * Testing an invokedynamic with a BSM, B say, whose static argument is
     * a dynamic constant, C say, that uses BSM B.
     */
    @Test
    public void testIndyBsmCondyBsm() throws Exception {
        test("indy_bsm_condy_bsm", THROWABLES);
    }

    void test(String methodName, Class<? extends Throwable>... ts) throws Exception {
        Method m = c.getMethod(methodName);
        m.setAccessible(true);
        test(m, ts);
    }

}
