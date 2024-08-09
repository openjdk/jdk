/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @bug 8028553
 * @summary Test that VerifyError is not thrown when 'overriding' a static method.
 * @run main FinalStatic
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.*;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

/*
 *  class A { static final int m() {return FAIL; } }
 *  class B extends A { int m() { return PASS; } }
 *  class FinalStatic {
 *      public static void main () {
 *          Object b = new B();
 *          b.m();
 *      }
 *  }
 */
public class FinalStatic {

    static final String CLASS_NAME_A = "A";
    static final String CLASS_NAME_B = "B";
    static final int FAILED = 0;
    static final int EXPECTED = 1234;

    static class TestClassLoader extends ClassLoader{

        @Override
        public Class findClass(String name) throws ClassNotFoundException {
            byte[] b;
            try {
                b = loadClassData(name);
            } catch (Throwable th) {
                // th.printStackTrace();
                throw new ClassNotFoundException("Loading error", th);
            }
            return defineClass(name, b, 0, b.length);
        }

        private byte[] loadClassData(String name) throws Exception {

            switch (name) {
                case CLASS_NAME_A:
                    return ClassFile.of().build(ClassDesc.of(CLASS_NAME_A),
                            clb -> clb.withVersion(JAVA_8_VERSION, 0)
                                    .withFlags(ACC_PUBLIC | ACC_SUPER)
                                    .withSuperclass(CD_Object)
                                    .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                            cob -> cob
                                                    .aload(0)
                                                    .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                                    .return_())
                                    .withMethodBody("m", MethodTypeDesc.of(CD_int), ACC_STATIC | ACC_FINAL,
                                            cob -> cob.ldc(FAILED).ireturn())
                    );

                case CLASS_NAME_B:
                    return ClassFile.of().build(ClassDesc.ofInternalName(CLASS_NAME_B),
                            clb -> clb.withVersion(JAVA_8_VERSION, 0)
                                    .withFlags(ACC_PUBLIC | ACC_SUPER)
                                    .withSuperclass(ClassDesc.ofInternalName(CLASS_NAME_A))
                                    .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                            cob -> cob
                                                    .aload(0)
                                                    .invokespecial(ClassDesc.ofInternalName(CLASS_NAME_A), INIT_NAME, MTD_void)
                                                    .return_())
                                    .withMethodBody("m", MethodTypeDesc.of(CD_int), ACC_PUBLIC,
                                            cob -> cob.ldc(EXPECTED).ireturn())
                    );
                default:
                    break;
            }
            throw new AssertionError("Unknown class " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        TestClassLoader tcl = new TestClassLoader();
        Class<?> a = tcl.loadClass(CLASS_NAME_A);
        Class<?> b = tcl.loadClass(CLASS_NAME_B);
        Object inst = b.newInstance();
        Method[] meths = b.getDeclaredMethods();

        Method m = meths[0];
        int mod = m.getModifiers();
        if ((mod & Modifier.FINAL) != 0) {
            throw new Exception("FAILED: " + m + " is FINAL");
        }
        if ((mod & Modifier.STATIC) != 0) {
            throw new Exception("FAILED: " + m + " is STATIC");
        }

        m.setAccessible(true);
        if (!m.invoke(inst).equals(EXPECTED)) {
              throw new Exception("FAILED: " + EXPECTED + " from " + m);
        }

        System.out.println("Passed.");
    }
}
