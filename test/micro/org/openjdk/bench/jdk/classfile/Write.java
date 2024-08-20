/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.classfile;

import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.SourceFileAttribute;
import jdk.internal.org.objectweb.asm.*;
import org.openjdk.jmh.annotations.*;
import java.io.FileOutputStream;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import static java.lang.classfile.Opcode.*;
import static java.lang.classfile.TypeKind.*;
import static org.openjdk.bench.jdk.classfile.TestConstants.*;

/**
 * Write
 *
 * Generates this program with 40 mains...
 *
 * class MyClass {
 *   public static void main(String[] args) {
 *     int fac = 1;
 *     for (int i = 1; i < 10; ++i) {
 *       fac = fac * i;
 *     }
 *     System.out.println(fac);
 *   }
 * }
 */
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
        "--enable-preview",
        "--add-exports", "java.base/jdk.internal.classfile.impl=ALL-UNNAMED"})
public class Write {
    static String checkFileAsm = "/tmp/asw/MyClass.class";
    static String checkFileBc = "/tmp/byw/MyClass.class";
    static boolean writeClassAsm = Files.exists(Paths.get(checkFileAsm).getParent());
    static boolean writeClassBc = Files.exists(Paths.get(checkFileBc).getParent());


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public byte[] asmStream() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V12, Opcodes.ACC_PUBLIC, "MyClass", null, "java/lang/Object", null);
        cw.visitSource("MyClass.java", null);

        {
            MethodVisitor mv = cw.visitMethod(0, INIT_NAME, "()V", null, null);
            mv.visitCode();
            Label startLabel = new Label();
            Label endLabel = new Label();
            mv.visitLabel(startLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", INIT_NAME, "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(endLabel);
            mv.visitLocalVariable("this", "LMyClass;", null, startLabel, endLabel, 1);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }

        for (int xi = 0; xi < 40; ++xi) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC+Opcodes.ACC_STATIC, "main"+ ((xi==0)? "" : ""+xi), "([Ljava/lang/String;)V", null, null);
            mv.visitCode();
            Label loopTop = new Label();
            Label loopEnd = new Label();
            Label startLabel = new Label();
            Label endLabel = new Label();
            Label iStart = new Label();
            mv.visitLabel(startLabel);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitLabel(iStart);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitLabel(loopTop);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitIntInsn(Opcodes.BIPUSH, 10);
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.IMUL);
            mv.visitVarInsn(Opcodes.ISTORE, 1);
            mv.visitIincInsn(2, 1);
            mv.visitJumpInsn(Opcodes.GOTO, loopTop);
            mv.visitLabel(loopEnd);
            mv.visitFieldInsn(Opcodes.GETSTATIC,"java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
            mv.visitLabel(endLabel);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLocalVariable("fac", "I", null, startLabel, endLabel, 1);
            mv.visitLocalVariable("i",   "I", null, iStart, loopEnd, 2);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        if (writeClassAsm) writeClass(bytes, checkFileAsm);
        return bytes;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public byte[] jdkTree() {

        byte[] bytes = ClassFile.of().build(CD_MyClass, cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod(INIT_NAME, MTD_void, 0, mb -> mb
                      .withCode(codeb -> codeb.loadLocal(TypeKind.ReferenceType, 0)
                                              .invoke(INVOKESPECIAL, CD_Object, INIT_NAME, MTD_void, false)
                                              .return_(VoidType)
                      )
              );
            for (int xi = 0; xi < 40; ++xi) {
                cb.withMethod("main" + ((xi == 0) ? "" : "" + xi), MTD_void_StringArray,
                              ACC_PUBLIC | ACC_STATIC,
                              mb -> mb.withCode(c0 -> {
                                  java.lang.classfile.Label loopTop = c0.newLabel();
                                  java.lang.classfile.Label loopEnd = c0.newLabel();
                                  int vFac = 1;
                                  int vI = 2;
                                  c0.iconst_1()         // 0
                                    .istore(vFac)       // 1
                                    .iconst_1()         // 2
                                    .istore(vI)         // 3
                                    .labelBinding(loopTop)
                                    .iload(vI)          // 4
                                    .bipush(10)         // 5
                                    .if_icmpge(loopEnd) // 6
                                    .iload(vFac)        // 7
                                    .iload(vI)          // 8
                                    .imul()             // 9
                                    .istore(vFac)       // 10
                                    .iinc(vI, 1)        // 11
                                    .goto_(loopTop)     // 12
                                    .labelBinding(loopEnd)
                                    .getstatic(CD_System, "out", CD_PrintStream) // 13
                                    .iload(vFac)
                                    .invokevirtual(CD_PrintStream, "println", MTD_void_int) // 15
                                    .return_();
                        }));
            }
        });
        if (writeClassBc) writeClass(bytes, checkFileBc);
        return bytes;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public byte[] jdkTreePrimitive() {

        byte[] bytes = ClassFile.of().build(CD_MyClass, cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(52, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethod(INIT_NAME, MTD_void, 0,
                          mb -> mb.withCode(codeb -> codeb.loadLocal(ReferenceType, 0)
                                                          .invokespecial(CD_Object, INIT_NAME, MTD_void, false)
                                                          .return_()
                          )
              );
            for (int xi = 0; xi < 40; ++xi) {
                cb.withMethod("main" + ((xi == 0) ? "" : "" + xi), MTD_void_StringArray,
                              ACC_PUBLIC | ACC_STATIC,
                              mb -> mb.withCode(c0 -> {
                                  java.lang.classfile.Label loopTop = c0.newLabel();
                                  java.lang.classfile.Label loopEnd = c0.newLabel();
                                  int vFac = 1;
                                  int vI = 2;
                                  c0.iconst_1()         // 0
                                    .istore(1)          // 1
                                    .iconst_1()         // 2
                                    .istore(2)          // 3
                                    .labelBinding(loopTop)
                                    .iload(2)           // 4
                                    .bipush(10)         // 5
                                    .if_icmpge(loopEnd) // 6
                                    .iload(1)           // 7
                                    .iload(2)           // 8
                                    .imul()             // 9
                                    .istore(1)          // 10
                                    .iinc(2, 1)         // 11
                                    .goto_(loopTop)     // 12
                                    .labelBinding(loopEnd)
                                    .getstatic(CD_System, "out", CD_PrintStream)  // 13
                                    .iload(1)
                                    .invokevirtual(CD_PrintStream, "println", MTD_void_int)  // 15
                                    .return_();
                        }));
            }
        });
        if (writeClassBc) writeClass(bytes, checkFileBc);
        return bytes;
    }

    private void writeClass(byte[] bytes, String fn) {
        try {
            FileOutputStream out = new FileOutputStream(fn);
            out.write(bytes);
            out.close();
        } catch (Exception ex) {
            throw new InternalError(ex);
        }
    }
}

