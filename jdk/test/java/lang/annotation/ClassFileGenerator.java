/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Create class file using ASM, slightly modified the ASMifier output
 */



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import jdk.internal.org.objectweb.asm.*;


public class ClassFileGenerator {

    public static void main(String... args) throws Exception {
        classFileWriter("AnnotationWithVoidReturn.class", AnnoationWithVoidReturnDump.dump());
        classFileWriter("AnnotationWithParameter.class", AnnoationWithParameterDump.dump());
    }

    private static void classFileWriter(String name, byte[] contents) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(new File(System.getProperty("test.classes"),
                name))) {
            fos.write(contents);
        }
    }

    /*
    Following code create equivalent classfile,
    which is not allowed by javac.

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationWithVoidReturn {
    void m() default 1;
    }
    */

    private static class AnnoationWithVoidReturnDump implements Opcodes {
        public static byte[] dump() throws Exception {
            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(52, ACC_PUBLIC + ACC_ANNOTATION + ACC_ABSTRACT + +ACC_INTERFACE,
                    "AnnotationWithVoidReturn", null,
                    "java/lang/Object", new String[]{"java/lang/annotation/Annotation"});

            {
                av0 = cw.visitAnnotation("Ljava/lang/annotation/Retention;", true);
                av0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;",
                        "RUNTIME");
                av0.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT, "m", "()V", null, null);
                mv.visitEnd();
            }
            {
                av0 = mv.visitAnnotationDefault();
                av0.visit(null, new Integer(1));
                av0.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();

        }
    }

    /*
    Following code create equivalent classfile,
    which is not allowed by javac.

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationWithParameter {
       int m(int x);
    }
    */

    private static class AnnoationWithParameterDump implements Opcodes {
        public static byte[] dump() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(52, ACC_PUBLIC + ACC_ANNOTATION + ACC_ABSTRACT + ACC_INTERFACE,
                    "AnnotationWithParameter", null,
                    "java/lang/Object", new String[]{"java/lang/annotation/Annotation"});

            {
                av0 = cw.visitAnnotation("Ljava/lang/annotation/Retention;", true);
                av0.visitEnum("value", "Ljava/lang/annotation/RetentionPolicy;",
                        "RUNTIME");
                av0.visitEnd();
            }
            {
                mv = cw.visitMethod(ACC_PUBLIC + ACC_ABSTRACT,
                        "badValue",
                        "(I)I", // Bad method with a parameter
                        null, null);
                mv.visitEnd();
            }
            {
                av0 = mv.visitAnnotationDefault();
                av0.visit(null, new Integer(-1));
                av0.visitEnd();
            }
            cw.visitEnd();

            return cw.toByteArray();
        }
    }

}
