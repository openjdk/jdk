/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.test.ExportingClassLoader;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.IincInsnNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;

/**
 * The {@code TinyInstrumentor} is a bytecode instrumentor using ASM bytecode manipulation
 * framework. It injects given code snippet into a target method and creates a temporary class as
 * the container. Because the target method is cloned into the temporary class, it is required that
 * the target method is public static. Any referred method/field in the target method or the
 * instrumentation snippet should be made public as well.
 */
public class TinyInstrumentor implements Opcodes {

    private InsnList instrumentationInstructions;
    private int instrumentationMaxLocal;

    /**
     * Create a instrumentor with a instrumentation snippet. The snippet is specified with the given
     * class {@code instrumentationClass} and the given method name {@code methodName}.
     */
    public TinyInstrumentor(Class<?> instrumentationClass, String methodName) throws IOException {
        MethodNode instrumentationMethod = getMethodNode(instrumentationClass, methodName);
        assert instrumentationMethod != null;
        assert (instrumentationMethod.access | ACC_STATIC) != 0;
        assert "()V".equals(instrumentationMethod.desc);
        instrumentationInstructions = cloneInstructions(instrumentationMethod.instructions);
        instrumentationMaxLocal = instrumentationMethod.maxLocals;
        // replace return instructions with a goto unless there is a single return at the end. In
        // that case, simply remove the return.
        List<AbstractInsnNode> returnInstructions = new ArrayList<>();
        for (AbstractInsnNode instruction : selectAll(instrumentationInstructions)) {
            if (instruction instanceof LineNumberNode) {
                instrumentationInstructions.remove(instruction);
            } else if (instruction.getOpcode() == RETURN) {
                returnInstructions.add(instruction);
            }
        }
        LabelNode exit = new LabelNode();
        if (returnInstructions.size() == 1) {
            AbstractInsnNode returnInstruction = returnInstructions.get(0);
            if (instrumentationInstructions.getLast() != returnInstruction) {
                instrumentationInstructions.insertBefore(returnInstruction, new JumpInsnNode(GOTO, exit));
            }
            instrumentationInstructions.remove(returnInstruction);
        } else {
            for (AbstractInsnNode returnInstruction : returnInstructions) {
                instrumentationInstructions.insertBefore(returnInstruction, new JumpInsnNode(GOTO, exit));
                instrumentationInstructions.remove(returnInstruction);
            }
        }
        instrumentationInstructions.add(exit);
    }

    /**
     * @return a {@link MethodNode} called {@code methodName} in the given class.
     */
    private static MethodNode getMethodNode(Class<?> clazz, String methodName) throws IOException {
        ClassReader classReader = new ClassReader(clazz.getName());
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(methodName)) {
                return methodNode;
            }
        }
        return null;
    }

    /**
     * Create a {@link ClassNode} with empty constructor.
     */
    private static ClassNode emptyClass(String name) {
        ClassNode classNode = new ClassNode();
        classNode.visit(52, ACC_SUPER | ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", new String[]{});

        MethodVisitor mv = classNode.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        return classNode;
    }

    /**
     * Helper method for iterating the given {@link InsnList}.
     */
    private static Iterable<AbstractInsnNode> selectAll(InsnList instructions) {
        return new Iterable<AbstractInsnNode>() {
            @Override
            public Iterator<AbstractInsnNode> iterator() {
                return instructions.iterator();
            }
        };
    }

    /**
     * Make a clone of the given {@link InsnList}.
     */
    private static InsnList cloneInstructions(InsnList instructions) {
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode instruction : selectAll(instructions)) {
            if (instruction instanceof LabelNode) {
                LabelNode clone = new LabelNode(new Label());
                LabelNode original = (LabelNode) instruction;
                labelMap.put(original, clone);
            }
        }
        InsnList clone = new InsnList();
        for (AbstractInsnNode insn : selectAll(instructions)) {
            clone.add(insn.clone(labelMap));
        }
        return clone;
    }

    /**
     * Shifts all local variable slot references by a specified amount.
     */
    private static void shiftLocalSlots(InsnList instructions, int offset) {
        for (AbstractInsnNode insn : selectAll(instructions)) {
            if (insn instanceof VarInsnNode) {
                VarInsnNode varInsn = (VarInsnNode) insn;
                varInsn.var += offset;

            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iincInsn = (IincInsnNode) insn;
                iincInsn.var += offset;
            }
        }
    }

    /**
     * Instrument the target method specified by the class {@code targetClass} and the method name
     * {@code methodName}. For each occurrence of the {@code opcode}, the instrumentor injects a
     * copy of the instrumentation snippet.
     */
    public Class<?> instrument(Class<?> targetClass, String methodName, int opcode) throws IOException, ClassNotFoundException {
        return instrument(targetClass, methodName, opcode, true);
    }

    public Class<?> instrument(Class<?> targetClass, String methodName, int opcode, boolean insertAfter) throws IOException, ClassNotFoundException {
        // create a container class
        String className = targetClass.getName() + "$$" + methodName;
        ClassNode classNode = emptyClass(className);
        // duplicate the target method and add to the container class
        MethodNode methodNode = getMethodNode(targetClass, methodName);
        MethodNode newMethodNode = new MethodNode(methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[methodNode.exceptions.size()]));
        methodNode.accept(newMethodNode);
        classNode.methods.add(newMethodNode);
        // perform bytecode instrumentation
        for (AbstractInsnNode instruction : selectAll(newMethodNode.instructions)) {
            if (instruction.getOpcode() == opcode) {
                InsnList instrumentation = cloneInstructions(instrumentationInstructions);
                shiftLocalSlots(instrumentation, newMethodNode.maxLocals);
                newMethodNode.maxLocals += instrumentationMaxLocal;
                if (insertAfter) {
                    newMethodNode.instructions.insert(instruction, instrumentation);
                } else {
                    newMethodNode.instructions.insertBefore(instruction, instrumentation);
                }
            }
        }
        // dump a byte array and load the class with a dedicated loader to separate the namespace
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);
        byte[] bytes = classWriter.toByteArray();
        return new Loader(className, bytes).findClass(className);
    }

    private static class Loader extends ExportingClassLoader {

        private String className;
        private byte[] bytes;

        Loader(String className, byte[] bytes) {
            super(TinyInstrumentor.class.getClassLoader());
            this.className = className;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, bytes, 0, bytes.length);
            } else {
                return super.findClass(name);
            }
        }
    }

}
