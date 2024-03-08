/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026213
 * @summary Reflection support for private methods in interfaces
 * @author  Robert Field
 * @enablePreview
 * @run main TestPrivateInterfaceMethodReflect
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.*;

import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

public class TestPrivateInterfaceMethodReflect {

    static final String INTERFACE_NAME = "PrivateInterfaceMethodReflectTest_Interface";
    static final String CLASS_NAME = "PrivateInterfaceMethodReflectTest_Class";
    static final int EXPECTED = 1234;

    static class TestClassLoader extends ClassLoader {

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] b;
            try {
                b = loadClassData(name);
            } catch (Throwable th) {
                // th.printStackTrace();
                throw new ClassNotFoundException("Loading error", th);
            }
            return defineClass(name, b, 0, b.length);
        }

        private byte[] loadClassData(String name) {
            return switch (name) {
                case INTERFACE_NAME -> ClassFile.of().build(ClassDesc.ofInternalName(INTERFACE_NAME), clb -> {
                    clb.withFlags(AccessFlag.ABSTRACT, AccessFlag.INTERFACE, AccessFlag.PUBLIC);
                    clb.withSuperclass(CD_Object);
                    clb.withMethodBody("privInstance", MethodTypeDesc.of(CD_int), ACC_PRIVATE, cob -> {
                        cob.constantInstruction(EXPECTED);
                        cob.ireturn();
                    });
                });
                case CLASS_NAME -> ClassFile.of().build(ClassDesc.of(CLASS_NAME), clb -> {
                    clb.withFlags(AccessFlag.PUBLIC);
                    clb.withSuperclass(CD_Object);
                    clb.withInterfaceSymbols(ClassDesc.ofInternalName(INTERFACE_NAME));
                    clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                        cob.aload(0);
                        cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                        cob.return_();
                    });
                });
                default -> throw new IllegalArgumentException();
            };
        }
    }

    public static void main(String[] args) throws Exception {
        TestClassLoader tcl = new TestClassLoader();
        Class<?> itf = tcl.loadClass(INTERFACE_NAME);
        Class<?> k = tcl.loadClass(CLASS_NAME);
        Object inst = k.getDeclaredConstructor().newInstance();
        Method[] meths = itf.getDeclaredMethods();
        if (meths.length != 1) {
            throw new Exception("Expected one method in " + INTERFACE_NAME + " instead " + meths.length);
        }

        Method m = meths[0];
        int mod = m.getModifiers();
        if ((mod & Modifier.PRIVATE) == 0) {
            throw new Exception("Expected " + m + " to be private");
        }
        if ((mod & Modifier.STATIC) != 0) {
            throw new Exception("Expected " + m + " to be instance method");
        }

        m.setAccessible(true);
        for (int i = 1; i < 200; i++) {
            if (!m.invoke(inst).equals(EXPECTED)) {
                throw new Exception("Expected " + EXPECTED + " from " + m);
            }
        }

        System.out.println("Passed.");
    }
}
