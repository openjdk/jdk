/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8087223 8195650
 * @summary Adding constantTag to keep method call consistent with it.
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @compile -XDignore.symbol.file BadMethodHandles.java
 * @run main/othervm BadMethodHandles
 */

import java.io.FileOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

public class BadMethodHandles {

    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");
    private static final ClassDesc CD_MethodHandle = ClassDesc.of("java.lang.invoke.MethodHandle");

    static byte[] dumpBadInterfaceMethodref() {

        DirectMethodHandleDesc handle_1 = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
                ClassDesc.of("BadInterfaceMethodref"), "m", "()V");

        DirectMethodHandleDesc handle_2 = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
                ClassDesc.of("BadInterfaceMethodref"), "staticM", "()V");

        return ClassFile.of().build(ClassDesc.of("BadInterfaceMethodref"),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_PUBLIC | ACC_SUPER)
                            .withSuperclass(CD_Object)
                            .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .aload(0)
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .return_())
                            .withMethodBody("m", MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .getstatic(CD_System, "out", CD_PrintStream)
                                            .ldc("hello from m")
                                            .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String))
                                            .return_())
                            .withMethodBody("staticM", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .getstatic(CD_System, "out", CD_PrintStream)
                                            .ldc("hello from staticM")
                                            .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String))
                                            .return_())
                            .withMethodBody("runm", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .ldc(handle_1)
                                            .invokevirtual(CD_MethodHandle, "invoke", MethodTypeDesc.of(CD_void))
                                            .return_())
                            .withMethodBody("runStaticM", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .ldc(handle_2)
                                            .invokevirtual(CD_MethodHandle, "invoke", MethodTypeDesc.of(CD_void))
                                            .return_())

        );
    }

    static byte[] dumpIBad() {

        return ClassFile.of().build(ClassDesc.of("IBad"),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE)
                            .withSuperclass(CD_Object)
                            .withMethodBody("m", MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .getstatic(CD_System, "out", CD_PrintStream)
                                            .ldc("hello from m")
                                            .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String))
                                            .return_())
                            .withMethodBody("staticM", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .getstatic(CD_System, "out", CD_PrintStream)
                                            .ldc("hello from staticM")
                                            .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String))
                                            .return_())
        );
    }

    static byte[] dumpBadMethodref() {


        DirectMethodHandleDesc handle_1 = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
                ClassDesc.of("BadMethodref"), "m", "()V");
        DirectMethodHandleDesc handle_2 = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL,
                ClassDesc.of("BadMethodref"), "staticM", "()V");

        return ClassFile.of().build(ClassDesc.of("BadMethodref"),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_PUBLIC | ACC_SUPER)
                            .withSuperclass(CD_Object)
                            .withInterfaceSymbols(ClassDesc.of("IBad"))
                            .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .aload(0)
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .return_())
                            .withMethodBody("runm", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .ldc(handle_1)
                                            .invokevirtual(CD_MethodHandle, "invoke", MethodTypeDesc.of(CD_void))
                                            .return_())
                            .withMethodBody("runStaticM", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .ldc(handle_2)
                                            .invokevirtual(CD_MethodHandle, "invoke", MethodTypeDesc.of(CD_void))
                                            .return_())
        );
    }

    static byte[] dumpInvokeBasic() {

        DirectMethodHandleDesc handle = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.VIRTUAL,
                CD_MethodHandle, "invokeBasic", "([Ljava/lang/Object;)Ljava/lang/Object;");

        return ClassFile.of().build(ClassDesc.of("InvokeBasicref"),
                    clb -> clb
                            .withVersion(JAVA_8_VERSION, 0)
                            .withFlags(ACC_PUBLIC | ACC_SUPER)
                            .withSuperclass(CD_Object)
                            .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC,
                                    cob -> cob
                                            .aload(0)
                                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                            .return_())
                            .withMethodBody("runInvokeBasicM", MTD_void, ACC_PUBLIC | ACC_STATIC,
                                    cob -> cob
                                            .ldc(handle)
                                            .invokevirtual(CD_MethodHandle, "invoke", MethodTypeDesc.of(CD_void))
                                            .return_())
        );

    }

    static class CL extends ClassLoader {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] classBytes = null;
            switch (name) {
            case "BadInterfaceMethodref": classBytes = dumpBadInterfaceMethodref(); break;
            case "BadMethodref"         : classBytes = dumpBadMethodref(); break;
            case "IBad"                 : classBytes = dumpIBad(); break;
            case "InvokeBasicref"       : classBytes = dumpInvokeBasic(); break;
            default                     : throw new ClassNotFoundException(name);
            }
            return defineClass(name, classBytes, 0, classBytes.length);
        }
    }

    public static void main(String[] args) throws Throwable {
        try (FileOutputStream fos = new FileOutputStream("BadInterfaceMethodref.class")) {
          fos.write(dumpBadInterfaceMethodref());
        }
        try (FileOutputStream fos = new FileOutputStream("IBad.class")) {
          fos.write(dumpIBad());
        }
        try (FileOutputStream fos = new FileOutputStream("BadMethodref.class")) {
          fos.write(dumpBadMethodref());
        }
        try (FileOutputStream fos = new FileOutputStream("InvokeBasicref.class")) {
            fos.write(dumpInvokeBasic());
        }

        Class<?> cls = (new CL()).loadClass("BadInterfaceMethodref");
        String[] methods = {"runm", "runStaticM"};
        System.out.println("Test BadInterfaceMethodref:");
        int success = 0;
        for (String name : methods) {
            try {
                System.out.printf("invoke %s: \n", name);
                cls.getMethod(name).invoke(cls.newInstance());
                System.out.println("FAILED - ICCE should be thrown");
            } catch (Throwable e) {
                if (e instanceof InvocationTargetException && e.getCause() != null &&
                    e.getCause() instanceof IncompatibleClassChangeError) {
                    System.out.println("PASSED - expected ICCE thrown");
                    success++;
                    continue;
                } else {
                    System.out.println("FAILED with wrong exception" + e);
                    throw e;
                }
            }
        }
        if (success != methods.length) {
           throw new Exception("BadInterfaceMethodRef Failed to catch IncompatibleClassChangeError");
        }
        System.out.println("Test BadMethodref:");
        cls = (new CL()).loadClass("BadMethodref");
        success = 0;
        for (String name : methods) {
            try {
                System.out.printf("invoke %s: \n", name);
                cls.getMethod(name).invoke(cls.newInstance());
                System.out.println("FAILED - ICCE should be thrown");
            } catch (Throwable e) {
                if (e instanceof InvocationTargetException && e.getCause() != null &&
                    e.getCause() instanceof IncompatibleClassChangeError) {
                    System.out.println("PASSED - expected ICCE thrown");
                    success++;
                    continue;
                } else {
                    System.out.println("FAILED with wrong exception" + e);
                    throw e;
                }
            }
         }
         if (success != methods.length) {
            throw new Exception("BadMethodRef Failed to catch IncompatibleClassChangeError");
         }

        System.out.println("Test InvokeBasicref:");
        cls = (new CL()).loadClass("InvokeBasicref");
        success = 0;
        methods = new String[] {"runInvokeBasicM"};
        for (String name : methods) {
            try {
                System.out.printf("invoke %s: \n", name);
                cls.getMethod(name).invoke(cls.newInstance());
                System.out.println("FAILED - ICCE should be thrown");
            } catch (Throwable e) {
                e.printStackTrace();
                if (e instanceof InvocationTargetException && e.getCause() != null &&
                    e.getCause() instanceof IncompatibleClassChangeError) {
                    System.out.println("PASSED - expected ICCE thrown");
                    success++;
                    continue;
                } else {
                    System.out.println("FAILED with wrong exception" + e);
                    throw e;
                }
            }
        }
        if (success != methods.length) {
            throw new Exception("InvokeBasicref Failed to catch IncompatibleClassChangeError");
        }
    }
}
