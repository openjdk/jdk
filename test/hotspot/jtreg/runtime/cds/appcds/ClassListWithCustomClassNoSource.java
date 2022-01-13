/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class ClassListWithCustomClassNoSource {

    static byte[] getClassBytes() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(56, ACC_PUBLIC | ACC_SUPER, "UserDefKlass", null, "java/lang/Object", null);
        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    static class CL extends ClassLoader {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] classBytes = getClassBytes();
            // both codeSource and permission set to null.
            ProtectionDomain pd = new ProtectionDomain(null, null);
            return defineClass(name, classBytes, 0, classBytes.length, pd);
        }
    }

    static class DL extends ClassLoader {
        ProtectionDomain pd;
        public DL(ProtectionDomain p) {
            pd = p;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] classBytes = getClassBytes();
            // code source is same as main class
            // started with "file: " so it will be logged in class list file.
            return defineClass(name, classBytes, 0, classBytes.length, pd);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Invalid arg, Use 1, 2, or 3");
        }

        switch(args[0]) {
        case "1":
            Class<?> cls1 = (new CL()).loadClass("UserDefKlass");
            System.out.println("CL Successfully loaded class: UserDefKlass");
            break;
        case "2":
            ProtectionDomain p = ClassListWithCustomClassNoSource.class.getProtectionDomain();
            Class<?> cls2 = (new DL(p)).loadClass("UserDefKlass");
            System.out.println("DL Successfully loaded class: UserDefKlass");
            break;
        case "3":
            URL url = ClassListWithCustomClassNoSource.class.getProtectionDomain().getCodeSource().getLocation();
            URLClassLoader urlLoader = new URLClassLoader("HelloClassLoader", new URL[] {url}, null);
            Class<?> cls = urlLoader.loadClass("Hello");
           if (cls != null) {
               System.out.println("Hello loaded by " + cls.getClassLoader().getName());
               if (urlLoader != cls.getClassLoader()) {
                   System.out.println("Hello is not loaded by " + urlLoader.getName());
               }
           } else {
               System.out.println("Hello is not loaded");
           }
           break;
        default:
            throw new RuntimeException("Should have one argument,  1, 2 or 3");
        }
    }
}
