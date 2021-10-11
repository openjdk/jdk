/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274848
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @library /test/lib
 * @run main/othervm InvokeSpecialMethodTest
 * @summary ensure REF_invokeSpecial on a non-private implementation method
 *          behaves as if `super::m` is invoked regardless of its access flag
 */

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.TypePath;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.compiler.CompilerUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class InvokeSpecialMethodTest {
    private static final Path CLASSES = Path.of("classes");
    private static final String IMPL_CLASSNAME = "InvokeSpecialMethodImpl";
    private static final String IMPL_SUBCLASS = "InvokeSpecialMethodImpl$SubClass";
    private static final String IMPL_METHODNAME = "testPrivate";

    public static void main(String... args) throws Throwable {
        Path src = Path.of(System.getProperty("test.src"));

        // compile with --release 10 as pre-nestmate class
        // The implementation method handle should be REF_invokeSpecial
        // on InvokeSpecialMethodImpl::testPrivate
        CompilerUtils.compile(src.resolve(IMPL_CLASSNAME + ".java"), CLASSES, "--release", "10");

        // validate InvokeSpecialMethodImpl::testPrivate is executed
        // not the public SubClass::testPrivate method
        ProcessTools.executeTestJava("-cp", CLASSES.toString(), IMPL_CLASSNAME)
                    .shouldHaveExitValue(0);

        // test if InvokeSpecialMethodImpl::testPrivate is public or protected
        runTest(Opcodes.ACC_PUBLIC);
        runTest(Opcodes.ACC_PROTECTED);
    }

    /**
     * Patch InvokeSpecialMethodImpl::testPrivate to the given access.
     * Then run InvokeSpecialMethodImpl to ensure that REF_invokeSpecial method
     * handle does not invoke the overridden testPrivate method in the subclass.
     */
    public static void runTest(int access) throws Throwable {
        String dir = "test-" + access;
        Path dest = Path.of(dir);
        try (InputStream inputStream = Files.newInputStream(CLASSES.resolve(IMPL_CLASSNAME + ".class"))) {
            ClassReader reader = new ClassReader(inputStream);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = make(writer, access);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            Files.createDirectories(dest);
            Files.write(dest.resolve(IMPL_CLASSNAME + ".class"), writer.toByteArray());
        }
        Files.copy(CLASSES.resolve(IMPL_SUBCLASS + ".class"), dest.resolve(IMPL_SUBCLASS + ".class"));
        ProcessTools.executeTestJava("-cp", dir, IMPL_CLASSNAME)
                    .shouldHaveExitValue(0);
    }

    /*
     * Patch the testPrivate method with the given new access.
     */
    public static ClassVisitor make(ClassVisitor cv, int newAccess) {
        return new ClassVisitor(Opcodes.ASM8, cv) {
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if (name.equals(IMPL_METHODNAME))
                    access = newAccess;
                return new CheckMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
            }
        };
    }

    /*
     * A method visitor to check BSM on LambdaMetafactory.metafactroy
     * with REF_invokeSpecial on InvokeSpecialMethodImpl::testPrivate
     */
    static class CheckMethodVisitor extends MethodVisitor {
        final MethodVisitor mv;
        public CheckMethodVisitor(final MethodVisitor methodVisitor) {
            super(Opcodes.ASM8, methodVisitor);
            this.mv = methodVisitor;
        }

        @Override
        public void visitParameter(final String name, final int access) {
            super.visitParameter(name, access);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
             return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitAttribute(final Attribute attribute) {
            super.visitAttribute(attribute);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return super.visitAnnotationDefault();
        }

        @Override
        public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            super.visitAnnotableParameterCount(parameterCount, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(
                final int parameter, final String descriptor, final boolean visible) {
            return super.visitParameterAnnotation(parameter, descriptor, visible);
        }

        @Override
        public void visitCode() {
            super.visitCode();
        }

        @Override
        public void visitFrame(
                final int type,
                final int numLocal,
                final Object[] local,
                final int numStack,
                final Object[] stack) {
            super.visitFrame(type, numLocal, local, numStack, stack);
        }

        @Override
        public void visitInsn(final int opcode) {
            super.visitInsn(opcode);
        }

        @Override
        public void visitIntInsn(final int opcode, final int operand) {
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitVarInsn(final int opcode, final int var) {
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitTypeInsn(final int opcode, final String type) {
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitFieldInsn(
                final int opcode, final String owner, final String name, final String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(
                final int opcode,
                final String owner,
                final String name,
                final String descriptor,
                final boolean isInterface) {
            mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(
                final String name,
                final String descriptor,
                final Handle bootstrapMethodHandle,
                final Object... bootstrapMethodArguments) {
            if (bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                    && bootstrapMethodHandle.getName().equals("metafactory")) {
                Handle handle = (Handle) bootstrapMethodArguments[1];
                if (handle.getOwner().equals(IMPL_CLASSNAME) && handle.getName().equals(IMPL_METHODNAME)) {
                    if (handle.getTag() != Opcodes.H_INVOKESPECIAL) {
                        throw new RuntimeException("Must be REF_invokeSpecial: " + handle);
                    }
                }
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        @Override
        public void visitJumpInsn(final int opcode, final Label label) {
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLabel(final Label label) {
            super.visitLabel(label);
        }

        @Override
        public void visitLdcInsn(final Object value) {
            super.visitLdcInsn(value);
        }

        @Override
        public void visitIincInsn(final int var, final int increment) {
            super.visitIincInsn(var, increment);
        }

        @Override
        public void visitTableSwitchInsn(
                final int min, final int max, final Label dflt, final Label... labels) {
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(
                final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitTryCatchBlock(
                final Label start, final Label end, final Label handler, final String type) {
            super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(
                final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
        }

        @Override
        public void visitLocalVariable(
                final String name,
                final String descriptor,
                final String signature,
                final Label start,
                final Label end,
                final int index) {
            super.visitLocalVariable(name, descriptor, signature, start, end, index);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(
                final int typeRef,
                final TypePath typePath,
                final Label[] start,
                final Label[] end,
                final int[] index,
                final String descriptor,
                final boolean visible) {
            return super.visitLocalVariableAnnotation(
                            typeRef, typePath, start, end, index, descriptor, visible);
        }

        @Override
        public void visitLineNumber(final int line, final Label start) {
            super.visitLineNumber(line, start);
        }

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals) {
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }
}
