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
 */
package org.openjdk.bench.jdk.classfile;

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.LoadInstruction;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;

/**
 * ReadCode
 */
public class ReadDeep extends AbstractCorpusBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void asmStreamCountLoads(Blackhole bh) {
        for (byte[] bytes : classes) {
            ClassReader cr = new ClassReader(bytes);

            var mv = new MethodVisitor(Opcodes.ASM9) {
                int count = 0;

                @Override
                public void visitVarInsn(int opcode, int var) {
                    ++count;
                }
            };

            var visitor = new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return mv;
                }
            };
            cr.accept(visitor, 0);
            bh.consume(mv.count);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void asmTreeCountLoads(Blackhole bh) {
        for (byte[] bytes : classes) {
            var mv = new MethodVisitor(Opcodes.ASM9) {
                int count = 0;

                @Override
                public void visitVarInsn(int opcode, int var) {
                    ++count;
                }
            };

            ClassNode node = new ClassNode();
            ClassReader cr = new ClassReader(bytes);
            cr.accept(node, 0);
            for (MethodNode mn : node.methods) {
                mn.accept(mv);
            }
            bh.consume(mv.count);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkElementsCountLoads(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            int[] count = new int[1];
            ClassModel cm = cc.parse(bytes);
            cm.forEachElement(ce -> {
                if (ce instanceof MethodModel mm) {
                    mm.forEachElement(me -> {
                        if (me instanceof CodeModel xm) {
                            xm.forEachElement(xe -> {
                                if (xe instanceof LoadInstruction) {
                                    ++count[0];
                                }
                            });
                        }
                    });
                };
            });
            bh.consume(count[0]);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkElementsDeepIterate(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            ClassModel cm = cc.parse(bytes);
            bh.consume(iterateAll(cm));
        }
    }

    private static ClassFileElement iterateAll(CompoundElement<?> model) {
        ClassFileElement last = null;
        for (var e : model) {
            if (e instanceof CompoundElement<?> cm) {
                last = iterateAll(cm);
            } else {
                last = e;
            }
        }
        return last; // provide some kind of result that the benchmark can feed to the black hole
    }

}
