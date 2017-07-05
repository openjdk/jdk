/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins.optim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;
import jdk.tools.jlink.internal.plugins.optim.ControlFlow.Block;

/**
 * Implement the reflection optimization.
 */
public class ReflectionOptimizer {

    public static class Data {

        private int removedInstructions;
        private final Map<String, Set<Block>> removedHandlers = new HashMap<>();

        private Data() {
        }

        public int removedInstructions() {
            return removedInstructions;
        }

        public Map<String, Set<Block>> removedHandlers() {
            return Collections.unmodifiableMap(removedHandlers);
        }
    }

    public interface TypeResolver {

        public ClassReader resolve(ClassNode cn, MethodNode m, String type);
    }

    public static Data replaceWithClassConstant(ClassNode cn, MethodNode m,
            TypeResolver cch)
            throws Exception {
        Iterator<AbstractInsnNode> it = m.instructions.iterator();
        LdcInsnNode insNode = null;
        Map<LdcInsnNode, LdcInsnNode> replacement = new IdentityHashMap<>();
        Data data = new Data();
        while (it.hasNext()) {
            AbstractInsnNode n = it.next();
            if (n instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) n;
                if (ldc.cst instanceof String) {
                    insNode = ldc;
                }
            } else {
                if (n instanceof MethodInsnNode && insNode != null) {
                    MethodInsnNode met = (MethodInsnNode) n;
                    if (met.name.equals("forName")
                            && met.owner.equals("java/lang/Class")
                            && met.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                        // Can we load the type?
                        Type type = null;
                        String binaryName = insNode.cst.toString().replaceAll("\\.", "/");
                        String unaryClassName = binaryName;
                        int arrayIndex = binaryName.lastIndexOf("[");
                        if (arrayIndex >= 0) {
                            int objIndex = unaryClassName.indexOf("L");
                            if (objIndex >= 0) {
                                unaryClassName = unaryClassName.substring(objIndex + 1);
                                unaryClassName = unaryClassName.substring(0,
                                        unaryClassName.length() - 1);
                            } else {
                                //primitive, this is just fine.
                                type = Type.getObjectType(binaryName);
                            }
                        }
                        if (type == null) {
                            if (cch.resolve(cn, m, unaryClassName) != null) {
                                type = Type.getObjectType(binaryName);
                            }
                        }
                        if (type != null) {
                            replacement.put(insNode, new LdcInsnNode(type));
                            it.remove();
                            data.removedInstructions += 1;
                        }
                    } else {
                        insNode = null;
                    }
                    // Virtual node, not taken into account
                } else if (!(n instanceof LabelNode) && !(n instanceof LineNumberNode)) {
                    insNode = null;
                }
            }
        }
        for (Map.Entry<LdcInsnNode, LdcInsnNode> entry : replacement.entrySet()) {
            m.instructions.set(entry.getKey(), entry.getValue());
        }
        if (!replacement.isEmpty()) {
            String[] types = {"java/lang/ClassNotFoundException"};
            data.removedInstructions += deleteExceptionHandlers(cch, data, cn, m, types);

        }
        return data;
    }

    private static int deleteExceptionHandlers(TypeResolver cch, Data data,
            ClassNode cn, MethodNode m, String[] exTypes)
            throws Exception {
        int instructionsRemoved = 0;
        for (String ex : exTypes) {
            ControlFlow f = ControlFlow.createControlFlow(cn.name, m);
            List<Integer> removed = new ArrayList<>();
            Set<ControlFlow.Block> blocksToRemove = new TreeSet<>();
            Iterator<TryCatchBlockNode> it = m.tryCatchBlocks.iterator();
            List<TryCatchBlockNode> tcbToRemove = new ArrayList<>();
            while (it.hasNext()) {
                TryCatchBlockNode bn = it.next();
                if (bn.type == null
                        || !bn.type.equals(ex) // An empty block
                        || tcbToRemove.contains(bn)) {
                    continue;
                }
                // Check that the handler is still required
                if (!Utils.canThrowCheckedException(cch, cn, m, bn)) {
                    // try to suppress it.
                    int block = m.instructions.indexOf(bn.handler);
                    ControlFlow.Block blockHandler = f.getBlock(block);
                    if (blockHandler == null) {
                        if (removed.contains(block)) {
                            continue;
                        } else {
                            throw new Exception(cn.name
                                    + ", no block for handler " + block);
                        }
                    }
                    tcbToRemove.add(bn);
                    // Don't delete block if shared (eg: ClassNotFoundException | NoSuchMethodException |
                    Iterator<TryCatchBlockNode> it2 = m.tryCatchBlocks.iterator();
                    boolean cont = false;
                    while (it2.hasNext()) {
                        TryCatchBlockNode bn2 = it2.next();
                        if (bn2 != bn) {
                            if (bn2.start.equals(bn.start)) {
                                cont = true;
                            }
                        }
                    }
                    if (cont) {
                        continue;
                    }
                    // An handler is a root, blocks that are only reachable by it
                    // can be removed.
                    Set<ControlFlow.Block> blocks = f.getClosure(blockHandler);
                    StringBuilder sb = new StringBuilder();
                    for (ControlFlow.Block b : blocks) {
                        sb.append(b).append("\n");
                        removed.add(b.getFirstInstruction().getIndex());
                        // Remove Exception handler if the associated block has been removed
                        for (TryCatchBlockNode tcb : m.tryCatchBlocks) {
                            if (tcb != bn) {
                                // An exception handler removed as a side effect.
                                if (b.isExceptionHandler()
                                        && b.getFirstInstruction().getInstr() == tcb.handler) {
                                    tcbToRemove.add(tcb);
                                }
                            }
                        }
                    }
                    blocksToRemove.addAll(blocks);

                    data.removedHandlers.put(ex, blocks);

                }
            }

            m.tryCatchBlocks.removeAll(tcbToRemove);

            if (!blocksToRemove.isEmpty()) {
                for (ControlFlow.Block b : blocksToRemove) {
                    for (ControlFlow.InstructionNode ins : b.getInstructions()) {
                        if (ins.getInstr().getOpcode() > 0) {
                            instructionsRemoved += 1;
                        }
                    }
                }
                Utils.suppressBlocks(m, blocksToRemove);
            }
        }
        return instructionsRemoved;
    }
}
