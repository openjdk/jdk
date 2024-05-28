/*
 * Copyright (c) 2016, 2024 Oracle and/or its affiliates. All rights reserved.
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
 * $bug 8087223
 * @enablePreview
 * @summary Adding constantTag to keep method call consistent with it.
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @compile -XDignore.symbol.file IntfMethod.java
 * @run main/othervm IntfMethod
 * @run main/othervm -Xint IntfMethod
 * @run main/othervm -Xcomp IntfMethod
 */


import java.io.FileOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.reflect.InvocationTargetException;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

public class IntfMethod {
    static byte[] dumpC() {
        return ClassFile.of(StackMapsOption.DROP_STACK_MAPS).build(ClassDesc.of("C"),
                clb -> clb
                        .withVersion(JAVA_8_VERSION, 0)
                        .withFlags(ACC_PUBLIC | ACC_SUPER)
                        .withSuperclass(CD_Object)
                        .withInterfaceSymbols(ClassDesc.of("I"))
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("testSpecialIntf", MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .aload(0)
                                        .invokespecial(ClassDesc.of("I"), "f1", MTD_void)
                                        .return_())
                        .withMethodBody("testStaticIntf", MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .invokestatic(ClassDesc.of("I"), "f2", MTD_void)
                                        .return_())
                        .withMethodBody("testSpecialClass", MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .aload(0)
                                        .invokespecial(ClassDesc.of("C"), "f1", MTD_void, true)
                                        .return_())
                        .withMethodBody("f2", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                CodeBuilder::return_)
                        .withMethodBody("testStaticClass", MTD_void, ACC_PUBLIC,
                                cob -> cob
                                        .invokestatic(ClassDesc.of("C"), "f2", MTD_void, true)
                                        .return_())

                );
    }

    static byte[] dumpI() {
        return ClassFile.of().build(ClassDesc.of("I"),
                clb -> clb
                        .withVersion(JAVA_8_VERSION, 0)
                        .withSuperclass(CD_Object)
                        .withFlags(ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE)
                        .withMethodBody("f1", MTD_void, ACC_PUBLIC,
                                CodeBuilder::return_)
                        .withMethodBody("f2", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                CodeBuilder::return_)
        );
    }

    static class CL extends ClassLoader {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] classFile;
            switch (name) {
                case "I": classFile = dumpI(); break;
                case "C": classFile = dumpC(); break;
                default:
                    throw new ClassNotFoundException(name);
            }
            return defineClass(name, classFile, 0, classFile.length);
        }
    }

    public static void main(String[] args) throws Throwable {
        Class<?> cls = (new CL()).loadClass("C");
        try (FileOutputStream fos = new FileOutputStream("I.class")) { fos.write(dumpI()); }
        try (FileOutputStream fos = new FileOutputStream("C.class")) { fos.write(dumpC()); }

        int success = 0;
        for (String name : new String[] { "testSpecialIntf", "testStaticIntf", "testSpecialClass", "testStaticClass"}) {
            System.out.printf("%s: ", name);
            try {
                cls.getMethod(name).invoke(cls.newInstance());
                System.out.println("FAILED - ICCE not thrown");
            } catch (Throwable e) {
                if (e instanceof InvocationTargetException &&
                    e.getCause() != null && e.getCause() instanceof IncompatibleClassChangeError) {
                    System.out.println("PASSED - expected ICCE thrown");
                    success++;
                }
            }
        }
        if (success != 4) throw new Exception("Failed to catch ICCE");
    }
}
