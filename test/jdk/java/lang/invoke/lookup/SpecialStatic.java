/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8032400
 * @summary JSR292: invokeSpecial: InternalError attempting to lookup a method
 * @modules java.base/jdk.internal.classfile
 * @compile -XDignore.symbol.file SpecialStatic.java
 * @run testng test.java.lang.invoke.lookup.SpecialStatic
 */
package test.java.lang.invoke.lookup;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.util.Objects;

import jdk.internal.classfile.Classfile;
import org.testng.annotations.*;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.SPECIAL;
import static jdk.internal.classfile.Classfile.ACC_PUBLIC;
import static jdk.internal.classfile.Classfile.ACC_STATIC;
import static org.testng.Assert.*;

/**
 * Test case:
 *   class T1            {        int m() { return 1; }}
 *   class T2 extends T1 { static int m() { return 2; }}
 *   class T3 extends T2 {        int m() { return 3; }}
 *
 *   T3::test { invokespecial T1.m() T3 } ==> T1::m
 */
public class SpecialStatic {
    static class CustomClassLoader extends ClassLoader {
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (findLoadedClass(name) != null) {
                return findLoadedClass(name);
            }

            if ("T1".equals(name)) {
                byte[] classFile = dumpT1();
                return defineClass("T1", classFile, 0, classFile.length);
            }
            if ("T2".equals(name)) {
                byte[] classFile = dumpT2();
                return defineClass("T2", classFile, 0, classFile.length);
            }
            if ("T3".equals(name)) {
                byte[] classFile = dumpT3();
                return defineClass("T3", classFile, 0, classFile.length);
            }

            return super.loadClass(name);
        }
    }

    private static ClassLoader cl = new CustomClassLoader();
    private static Class t1, t3;
    private static final MethodTypeDesc MD_void = MethodTypeDesc.of(CD_void);
    private static final MethodTypeDesc MD_int = MethodTypeDesc.of(CD_int);
    private static final MethodTypeDesc MD_Lookup = MethodTypeDesc.of(CD_MethodHandles_Lookup);
    private static final String CTOR_NAME = "<init>";
    private static final String METHOD_NAME = "m";
    private static final ClassDesc CD_T1 = ClassDesc.of("T1");
    private static final ClassDesc CD_T2 = ClassDesc.of("T2");
    private static final ClassDesc CD_T3 = ClassDesc.of("T3");
    static {
        try {
            t1 = cl.loadClass("T1");
            t3 = cl.loadClass("T3");
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) throws Throwable {
        SpecialStatic test = new SpecialStatic();
        test.testConstant();
        test.testFindSpecial();
    }

    @Test
    public void testConstant() throws Throwable {
        MethodHandle mh = (MethodHandle)t3.getDeclaredMethod("getMethodHandle").invoke(null);
        int result = (int)mh.invoke(t3.newInstance());
        assertEquals(result, 1); // T1.m should be invoked.
    }

    @Test
    public void testFindSpecial() throws Throwable {
        MethodHandles.Lookup lookup = (MethodHandles.Lookup)t3.getDeclaredMethod("getLookup").invoke(null);
        MethodHandle mh = lookup.findSpecial(t1, "m", MethodType.methodType(int.class), t3);
        int result = (int)mh.invoke(t3.newInstance());
        assertEquals(result, 1); // T1.m should be invoked.
    }

    public static byte[] dumpT1() {
        return Classfile.build(CD_T1, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withMethodBody(CTOR_NAME, MD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, CTOR_NAME, MD_void);
                cob.return_();
            });
            clb.withMethodBody(METHOD_NAME, MD_int, ACC_PUBLIC, cob -> {
                cob.bipush(1);
                cob.ireturn();
            });
        });
    }

    public static byte[] dumpT2() {
        return Classfile.build(CD_T2, clb -> {
            clb.withSuperclass(CD_T1);
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withMethodBody(CTOR_NAME, MD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_T1, CTOR_NAME, MD_void);
                cob.return_();
            });
            clb.withMethodBody(METHOD_NAME, MD_int, ACC_PUBLIC | ACC_STATIC, cob -> {
                cob.bipush(2);
                cob.ireturn();
            });
        });
    }

    public static byte[] dumpT3() {
        return Classfile.build(CD_T3, clb -> {
            clb.withSuperclass(CD_T2);
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withMethodBody(CTOR_NAME, MD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_T2, CTOR_NAME, MD_void);
                cob.return_();
            });
            clb.withMethodBody(METHOD_NAME, MD_int, ACC_PUBLIC, cob -> {
                cob.bipush(3);
                cob.ireturn();
            });
            clb.withMethodBody("getMethodHandle", MethodTypeDesc.of(CD_MethodHandle),
                    ACC_PUBLIC | ACC_STATIC, cob -> {
                cob.constantInstruction(MethodHandleDesc.ofMethod(SPECIAL, CD_T1, METHOD_NAME, MD_int));
                cob.areturn();
            });
            clb.withMethodBody("getLookup", MD_Lookup,
                    ACC_PUBLIC | ACC_STATIC, cob -> {
                cob.invokestatic(CD_MethodHandles, "lookup", MD_Lookup);
                cob.areturn();
            });
        });
    }
}
