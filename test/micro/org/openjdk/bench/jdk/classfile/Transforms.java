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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import java.lang.constant.ConstantDescs;
import java.util.stream.Stream;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.components.ClassRemapper;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.RecordComponentVisitor;
import jdk.internal.org.objectweb.asm.TypePath;
import jdk.internal.org.objectweb.asm.tree.ClassNode;

/**
 * Transforms
 */
public class Transforms {

    static int ASM9 = 9 << 16 | 0 << 8;

    public static final ClassTransform threeLevelNoop = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            cb.transformMethod(mm, (mb, me) -> {
                if (me instanceof CodeModel xm) {
                    mb.transformCode(xm, CodeTransform.ACCEPT_ALL);
                }
                else
                    mb.with(me);
            });
        }
        else
            cb.with(ce);
    };

    private static final ClassTransform threeLevelNoopPipedCMC_seed = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            MethodTransform transform = (mb, me) -> {
                if (me instanceof CodeModel xm) {
                    mb.transformCode(xm, CodeTransform.ACCEPT_ALL.andThen(CodeTransform.ACCEPT_ALL));
                }
                else
                    mb.with(me);
            };
            cb.transformMethod(mm, transform);
        }
        else
            cb.with(ce);
    };

    static final ClassTransform twoLevelNoop = (cb, ce) -> {
        if (ce instanceof MethodModel mm) {
            cb.transformMethod(mm, MethodTransform.ACCEPT_ALL);
        }
        else
            cb.with(ce);
    };

    static final ClassTransform oneLevelNoop = ClassTransform.ACCEPT_ALL;

    public static final List<ClassTransform> noops = List.of(threeLevelNoop, twoLevelNoop, oneLevelNoop);

    public enum NoOpTransform {
        ARRAYCOPY(bytes -> {
            byte[] bs = new byte[bytes.length];
            System.arraycopy(bytes, 0, bs, 0, bytes.length);
            return bs;
        }),
        SHARED_1(true, oneLevelNoop),
        SHARED_2(true, twoLevelNoop),
        SHARED_3(true, threeLevelNoop),
        SHARED_3P(true, threeLevelNoop.andThen(threeLevelNoop)),
        SHARED_3L(true, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)),
        SHARED_3Sx(true, threeLevelNoopPipedCMC_seed.andThen(ClassTransform.ACCEPT_ALL)),
        SHARED_3bc(true, ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL)
                .andThen(ClassTransform.ACCEPT_ALL)
                .andThen(ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL))),
        UNSHARED_1(false, oneLevelNoop),
        UNSHARED_2(false, twoLevelNoop),
        UNSHARED_3(false, threeLevelNoop),
        SHARED_3_NO_STACKMAP(true, threeLevelNoop, ClassFile.StackMapsOption.DROP_STACK_MAPS),
        SHARED_3_NO_DEBUG(true, threeLevelNoop, ClassFile.DebugElementsOption.DROP_DEBUG, ClassFile.LineNumbersOption.DROP_LINE_NUMBERS),
        ASM_1(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(cw, 0);
            return cw.toByteArray();
        }),
        ASM_UNSHARED_1(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(cw, 0);
            return cw.toByteArray();
        }),
        ASM_3(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(new CustomClassVisitor(cw), 0);
            return cw.toByteArray();
        }),
        ASM_UNSHARED_3(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(new CustomClassVisitor(cw), 0);
            return cw.toByteArray();
        }),
        ASM_TREE(bytes -> {
            ClassNode node = new ClassNode();
            ClassReader cr = new ClassReader(bytes);
            cr.accept(node, 0);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            return cw.toByteArray();
        }),
        CLASS_REMAPPER(bytes ->
                ClassRemapper.of(Map.of()).remapClass(ClassFile.of(), ClassFile.of().parse(bytes)));

        // Need ASM, LOW_UNSHARED

        public final UnaryOperator<byte[]> transform;
        public final boolean shared;
        public final ClassTransform classTransform;
        public final ClassFile cc;

        NoOpTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
            classTransform = null;
            shared = false;
            cc = ClassFile.of();
        }

        NoOpTransform(boolean shared,
                      ClassTransform classTransform,
                      ClassFile.Option... options) {
            this.shared = shared;
            this.classTransform = classTransform;
            this.cc = ClassFile.of(
                    shared
                    ? options
                    : Stream.concat(Stream.of(options), Stream.of(ClassFile.ConstantPoolSharingOption.NEW_POOL)).toArray(ClassFile.Option[]::new));
            this.transform = bytes -> cc.transform(cc.parse(bytes), classTransform);
        }
    }

    public enum InjectNopTransform {
        ASM_NOP_SHARED(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(new NopClassVisitor(cw), 0);
            return cw.toByteArray();
        }),
        NOP_SHARED(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transform(cm, (cb, ce) -> {
                if (ce instanceof MethodModel mm) {
                    cb.transformMethod(mm, (mb, me) -> {
                        if (me instanceof CodeModel xm) {
                            mb.withCode(xb -> {
                                xb.nopInstruction();
                                xm.forEachElement(new Consumer<>() {
                                    @Override
                                    public void accept(CodeElement e) {
                                        xb.with(e);
                                    }
                                });
                            });
                        }
                        else
                            mb.with(me);
                    });
                }
                else
                    cb.with(ce);
            });
        });

        public final UnaryOperator<byte[]> transform;

        InjectNopTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
        }
    }

    public enum SimpleTransform {
        ASM_ADD_FIELD(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            cr.accept(cw, 0);
            cw.visitField(0, "argleBargleWoogaWooga", "I", null, null);
            return cw.toByteArray();
        }),
        HIGH_SHARED_ADD_FIELD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transform(cm, new ClassTransform() {
                @Override
                public void accept(ClassBuilder builder, ClassElement element) {
                    builder.with(element);
                }

                @Override
                public void atEnd(ClassBuilder builder) {
                    builder.withField("argleBargleWoogaWooga", ConstantDescs.CD_int, b -> { });
                }
            });
        }),
        HIGH_UNSHARED_ADD_FIELD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.build(cm.thisClass().asSymbol(),
                                   cb -> {
                                       cm.forEachElement(cb);
                                       cb.withField("argleBargleWoogaWooga", ConstantDescs.CD_int, b -> { });
                                   });
        }),
        ASM_DEL_METHOD(bytes -> {
            ClassReader cr = new ClassReader(bytes);
            jdk.internal.org.objectweb.asm.ClassWriter cw = new jdk.internal.org.objectweb.asm.ClassWriter(cr, jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
            ClassVisitor v = new ClassVisitor(ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return (name.equals("hashCode") && descriptor.equals("()Z"))
                           ? null
                           : super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cw, 0);
            return cw.toByteArray();
        }),
        HIGH_SHARED_DEL_METHOD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.transform(cm, (builder, element) -> {
                if (!(element instanceof MethodModel mm))
                    builder.with(element);
            });
        }),
        HIGH_UNSHARED_DEL_METHOD(bytes -> {
            var cc = ClassFile.of();
            ClassModel cm = cc.parse(bytes);
            return cc.build(cm.thisClass().asSymbol(),
                                   cb -> {
                                       cm.forEachElement(element -> {
                                           if (element instanceof MethodModel mm
                                               && mm.methodName().stringValue().equals("hashCode")
                                               && mm.methodType().stringValue().equals("()Z")) {

                                           }
                                           else
                                               cb.with(element);
                                       });
                                   });
        });

        public final UnaryOperator<byte[]> transform;

        SimpleTransform(UnaryOperator<byte[]> transform) {
            this.transform = transform;
        }
    }

    static class CustomClassVisitor extends ClassVisitor {

        public CustomClassVisitor(ClassVisitor writer) {
            super(ASM9, writer);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitSource(String source, String debug) {
            super.visitSource(source, debug);
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            return super.visitModule(name, access, version);
        }

        @Override
        public void visitNestHost(String nestHost) {
            super.visitNestHost(nestHost);
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            super.visitOuterClass(owner, name, descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            super.visitAttribute(attribute);
        }

        @Override
        public void visitNestMember(String nestMember) {
            super.visitNestMember(nestMember);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            return super.visitRecordComponent(name, descriptor, signature);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new CustomMethodVisitor(mv);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    };


    static class CustomMethodVisitor extends MethodVisitor {

        public CustomMethodVisitor(MethodVisitor methodVisitor) {
            super(ASM9, methodVisitor);
        }

        @Override
        public void visitParameter(String name, int access) {
            super.visitParameter(name, access);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return super.visitAnnotationDefault();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            super.visitAnnotableParameterCount(parameterCount, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            return super.visitParameterAnnotation(parameter, descriptor, visible);
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            super.visitAttribute(attribute);
        }

        @Override
        public void visitCode() {
            super.visitCode();
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            super.visitFrame(type, numLocal, local, numStack, stack);
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
            super.visitMethodInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            super.visitIincInsn(var, increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            super.visitLocalVariable(name, descriptor, signature, start, end, index);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            super.visitLineNumber(line, start);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    };

    static class NopClassVisitor extends CustomClassVisitor {

        public NopClassVisitor(ClassVisitor writer) {
            super(writer);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new NopMethodVisitor(mv);
        }
    }

    static class NopMethodVisitor extends CustomMethodVisitor {

        public NopMethodVisitor(MethodVisitor methodVisitor) {
            super(methodVisitor);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitInsn(Opcodes.NOP);
        }
    }

}
