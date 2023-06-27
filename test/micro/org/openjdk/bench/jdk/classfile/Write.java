/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.classfile;

import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import jdk.internal.org.objectweb.asm.*;
import org.openjdk.jmh.annotations.*;

import java.io.FileOutputStream;

import static java.lang.constant.ConstantDescs.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import static jdk.internal.classfile.Classfile.*;
import static jdk.internal.classfile.Opcode.BIPUSH;
import static jdk.internal.classfile.Opcode.GETSTATIC;
import static jdk.internal.classfile.Opcode.GOTO;
import static jdk.internal.classfile.Opcode.ICONST_1;
import static jdk.internal.classfile.Opcode.IF_ICMPGE;
import static jdk.internal.classfile.Opcode.IMUL;
import static jdk.internal.classfile.Opcode.INVOKEVIRTUAL;
import static org.openjdk.bench.jdk.classfile.TestConstants.CD_PrintStream;
import static org.openjdk.bench.jdk.classfile.TestConstants.CD_System;
import static org.openjdk.bench.jdk.classfile.TestConstants.MTD_void_int;
import static jdk.internal.classfile.TypeKind.*;

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
        "--add-exports", "java.base/jdk.internal.classfile=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.attribute=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.constantpool=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.instruction=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.components=ALL-UNNAMED",
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
            MethodVisitor mv = cw.visitMethod(0, "<init>", "()V", null, null);
            mv.visitCode();
            Label startLabel = new Label();
            Label endLabel = new Label();
            mv.visitLabel(startLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
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

        byte[] bytes = Classfile.of().build(TestConstants.CD_MyClass, cb -> {
            cb.withFlags(AccessFlag.PUBLIC);
            cb.withVersion(JAVA_12_VERSION, 0);
            cb.with(SourceFileAttribute.of(cb.constantPool().utf8Entry(("MyClass.java"))))
              .withMethodBody(INIT_NAME, MTD_void, 0, codeb -> codeb
                      .aload(0)
                      .invokespecial(CD_Object, INIT_NAME, MTD_void)
                      .return_()
              );
            for (int xi = 0; xi < 40; ++xi) {
                cb.withMethodBody("main" + ((xi == 0) ? "" : String.valueOf(xi)), TestConstants.MTD_void_StringArray,
                              ACC_PUBLIC | ACC_STATIC, c0 -> {
                                  var loopTop = c0.newLabel();
                                  var loopEnd = c0.newLabel();
                                  var startLabel = c0.startLabel();
                                  var endLabel = c0.endLabel();
                                  var iStart = c0.newLabel();
                                  int vFac = 1;
                                  int vI = 2;
                                  c0.constantInstruction(ICONST_1, 1)         // 0
                                    .storeInstruction(IntType, vFac)        // 1
                                    .labelBinding(iStart)
                                    .constantInstruction(ICONST_1, 1)         // 2
                                    .storeInstruction(IntType, vI)          // 3
                                    .labelBinding(loopTop)
                                    .loadInstruction(IntType, vI)           // 4
                                    .constantInstruction(BIPUSH, 10)         // 5
                                    .branchInstruction(IF_ICMPGE, loopEnd) // 6
                                    .loadInstruction(IntType, vFac)         // 7
                                    .loadInstruction(IntType, vI)           // 8
                                    .operatorInstruction(IMUL)             // 9
                                    .storeInstruction(IntType, vFac)        // 10
                                    .incrementInstruction(vI, 1)    // 11
                                    .branchInstruction(GOTO, loopTop)     // 12
                                    .labelBinding(loopEnd)
                                    .fieldInstruction(GETSTATIC, CD_System, "out", CD_PrintStream)   // 13
                                    .loadInstruction(IntType, vFac)
                                    .invokeInstruction(INVOKEVIRTUAL, CD_PrintStream, "println", MTD_void_int, false)  // 15
                                    .returnInstruction(VoidType)
                                    .localVariable(vFac, "fac", CD_int, startLabel, endLabel)
                                    .localVariable(vI, "i", CD_int, iStart, loopEnd);
                        });
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

