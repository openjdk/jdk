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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

/**
 * Split Java method onto a control flow.
 *
 */
public final class ControlFlow {

    /**
     * A block of control
     */
    public static final class Block implements Comparable<Block> {

        private final InstructionNode firstInstruction;
        private final List<InstructionNode> instr = new ArrayList<>();
        private final List<Block> reachable = new ArrayList<>();
        private final List<Block> exceptionHandlers = new ArrayList<>();
        private boolean isExceptionHandler;

        private Block(InstructionNode firstInstruction) {
            this.firstInstruction = firstInstruction;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Block)) {
                return false;
            }
            Block b = (Block) other;
            return firstInstruction.equals(b.firstInstruction);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.firstInstruction);
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (InstructionNode in : instr) {
                builder.append(in).append(" ");
            }
            builder.append(" reachables: ");
            for (Block r : reachable) {
                builder.append(r.getFirstInstruction()).append(" ");
            }
            builder.append(" exception handlers: ");
            for (Block r : exceptionHandlers) {
                builder.append(r.getFirstInstruction()).append(" ");
            }

            return "block[" + getFirstInstruction() + "],ex:"
                    + isExceptionHandler + ",  " + builder.toString();
        }

        /**
         * @return the firstInstruction
         */
        public InstructionNode getFirstInstruction() {
            return firstInstruction;
        }

        /**
         * @return the instr
         */
        public List<InstructionNode> getInstructions() {
            return Collections.unmodifiableList(instr);
        }

        /**
         * @return the reachable
         */
        public List<Block> getReachableBlocks() {
            return Collections.unmodifiableList(reachable);
        }

        /**
         * @return the exceptionHandlers
         */
        public List<Block> getExceptionHandlerBlocks() {
            return Collections.unmodifiableList(exceptionHandlers);
        }

        @Override
        public int compareTo(Block t) {
            return this.firstInstruction.index - t.firstInstruction.index;
        }

        public boolean isExceptionHandler() {
            return isExceptionHandler;
        }

    }

    private class ClosureBuilder {

        private final Block root;

        private ClosureBuilder(Block root) {
            Objects.requireNonNull(root);
            this.root = root;
        }

        private Set<Block> build() {
            Set<Block> allReachable = new TreeSet<>();
            addAll(root, allReachable);
            // filter out the reachable from outside this graph
            Iterator<Block> it = allReachable.iterator();
            Set<Block> toExclude = new HashSet<>();
            while (it.hasNext()) {
                Block b = it.next();
                for (Block ref : blocks) {
                    if (!allReachable.contains(ref) && ref.reachable.contains(b)) {
                        addAll(b, toExclude);
                        break;
                    }
                }
            }
            //System.err.println("TO EXCLUDE:\n " + toExclude);
            allReachable.removeAll(toExclude);
            //System.err.println("CLOSURE:\n " + allReachable);
            return Collections.unmodifiableSet(allReachable);
        }

        // Compute the set of blocks reachable from the current block
        private void addAll(Block current, Set<Block> closure) {
            Objects.requireNonNull(current);
            closure.add(current);
            for (Block ex : current.exceptionHandlers) {
                Objects.requireNonNull(ex);
                if (!closure.contains(ex)) {
                    addAll(ex, closure);
                }
            }
            for (Block r : current.reachable) {
                Objects.requireNonNull(r);
                if (!closure.contains(r)) {
                    addAll(r, closure);
                }
            }

        }
    }

    /**
     * An instruction
     */
    public static final class InstructionNode {

        private final int index;
        private final List<InstructionNode> next = new ArrayList<>();
        private final AbstractInsnNode instr;

        private InstructionNode(int index, AbstractInsnNode instr) {
            this.index = index;
            this.instr = instr;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof InstructionNode)) {
                return false;
            }
            final InstructionNode other = (InstructionNode) obj;
            return this.getIndex() == other.getIndex();
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 89 * hash + this.getIndex();
            return hash;
        }

        @Override
        public String toString() {
            return getIndex() + "(" + (getInstr().getOpcode() == - 1 ? -1
                    : Integer.toHexString(getInstr().getOpcode())) + ")";
        }

        /**
         * @return the index
         */
        public int getIndex() {
            return index;
        }

        /**
         * @return the instr
         */
        public AbstractInsnNode getInstr() {
            return instr;
        }

    }

    private final Map<Integer, Block> allBlocks;
    private final List<Block> blocks = new ArrayList<>();

    private ControlFlow(Map<Integer, Block> allBlocks) {
        this.allBlocks = allBlocks;
        for (Block b : allBlocks.values()) {
            blocks.add(b);
        }
        Collections.sort(blocks);
    }

    public List<Block> getBlocks() {

        return Collections.unmodifiableList(blocks);
    }

    public Block getBlock(int firstInstr) {
        return allBlocks.get(firstInstr);
    }

    public static ControlFlow createControlFlow(String owner,
            MethodNode method) throws Exception {

        BlockBuilder bb = new BlockBuilder(owner, method);
        return bb.build();
    }

    /**
     * Return the set of blocks that are only reachable from this block For
     * example, if b is an Exception handler, returns all the blocks reachable
     * only from this handler
     *
     * @param b
     * @return
     */
    public Set<Block> getClosure(Block b) {
        return new ClosureBuilder(b).build();
    }

    private static final class BlockBuilder {

        private InstructionNode root;
        private final Map<Integer, InstructionNode> instructions = new HashMap<>();
        private final Map<Integer, List<Integer>> handlers = new HashMap<>();
        private final Map<Integer, Block> allBlocks = new HashMap<>();

        private final String owner;
        private final MethodNode method;

        private BlockBuilder(String owner, MethodNode method) {
            this.owner = owner;
            this.method = method;
        }

        private void analyze() throws AnalyzerException {
            Analyzer<BasicValue> analyzer = new Analyzer<BasicValue>(new BasicInterpreter()) {

                @Override
                protected boolean newControlFlowExceptionEdge(int insn,
                        int successor) {
                    List<Integer> lst = handlers.get(successor);
                    if (lst == null) {
                        lst = new ArrayList<>();
                        handlers.put(successor, lst);
                    }
                    lst.add(insn);
                    return true;
                }

                @Override
                protected void newControlFlowEdge(int from,
                        int to) {
                    if (root == null) {
                        root = new InstructionNode(from, method.instructions.get(from));
                        instructions.put(from, root);
                    }
                    InstructionNode fromNode = instructions.get(from);
                    if (fromNode == null) {
                        fromNode = new InstructionNode(from, method.instructions.get(from));
                        instructions.put(from, fromNode);
                    }
                    InstructionNode toNode = instructions.get(to);
                    if (toNode == null) {
                        toNode = new InstructionNode(to, method.instructions.get(to));
                        instructions.put(to, toNode);
                    }
                    if (!fromNode.next.contains(toNode)) {
                        fromNode.next.add(toNode);
                    }

                }
            };
            analyzer.analyze(owner, method);
        }

        private Block newBlock(InstructionNode firstInstruction) {
            Objects.requireNonNull(firstInstruction);
            Block b = new Block(firstInstruction);
            allBlocks.put(firstInstruction.getIndex(), b);
            return b;
        }

        private ControlFlow build() throws AnalyzerException {
            analyze();
            buildBlocks();
            return new ControlFlow(allBlocks);
        }

        private void buildBlocks() {
            List<Block> reachableBlocks = new ArrayList<>();
            createBlocks(root, reachableBlocks);
            List<Block> handlersBlocks = new ArrayList<>();
            for (Entry<Integer, List<Integer>> entry : handlers.entrySet()) {
                InstructionNode node = instructions.get(entry.getKey());
                createBlocks(node, handlersBlocks);
            }

            // attach handler to try blocks
            for (Entry<Integer, List<Integer>> entry : handlers.entrySet()) {
                Block handlerBlock = allBlocks.get(entry.getKey());
                handlerBlock.isExceptionHandler = true;
                int startTry = entry.getValue().get(0);
                Block tryBlock = allBlocks.get(startTry);
                if (tryBlock == null) {
                    // Need to find the block that contains the instruction and
                    // make a new block
                    Block split = null;
                    for (Block b : allBlocks.values()) {
                        Iterator<InstructionNode> it = b.instr.iterator();
                        while (it.hasNext()) {
                            InstructionNode in = it.next();
                            if (split == null) {
                                if (in.index == startTry) {
                                    split = newBlock(in);
                                    split.instr.add(in);
                                    it.remove();
                                }
                            } else {
                                split.instr.add(in);
                                it.remove();
                            }
                        }
                        if (split != null) {
                            Iterator<Block> reachables = b.reachable.iterator();
                            while (reachables.hasNext()) {
                                Block r = reachables.next();
                                split.reachable.add(r);
                                reachables.remove();
                            }
                            b.reachable.add(split);
                            break;
                        }
                    }
                    if (split == null) {
                        throw new RuntimeException("No try block for handler " + handlerBlock);
                    }
                    split.exceptionHandlers.add(handlerBlock);
                } else {
                    tryBlock.exceptionHandlers.add(handlerBlock);
                }
            }

//            System.err.println("ALL BLOCKS FOUND");
//            Iterator<Entry<Integer, Block>> blockIt0 = allBlocks.entrySet().iterator();
//            while (blockIt0.hasNext()) {
//                Block b = blockIt0.next().getValue();
//                System.err.println(b);
//            }
            //compute real exception blocks, if an instruction is in another block, stop.
            Iterator<Entry<Integer, Block>> blockIt = allBlocks.entrySet().iterator();
            while (blockIt.hasNext()) {
                Block b = blockIt.next().getValue();
                Iterator<InstructionNode> in = b.instr.iterator();
                boolean found = false;
                while (in.hasNext()) {
                    int i = in.next().getIndex();
                    if (found) {
                        in.remove();
                    } else {
                        if (startsWith(b, i, allBlocks.values())) {
                            // Move it to reachable
                            Block r = allBlocks.get(i);
                            b.reachable.add(r);
                            found = true;
                            in.remove();
                        } else {
                        }
                    }
                }
            }

//            System.err.println("Reduced blocks");
//            Iterator<Entry<Integer, Block>> blockIt1 = allBlocks.entrySet().iterator();
//            while (blockIt1.hasNext()) {
//                Block b = blockIt1.next().getValue();
//                System.err.println(b);
//            }
        }

        private boolean startsWith(Block block, int index, Collection<Block> reachableBlocks) {
            for (Block b : reachableBlocks) {
                if (b != block && !b.instr.isEmpty() && b.instr.get(0).getIndex() == index) {
                    return true;
                }
            }
            return false;
        }

        private static final class StackItem {

            private final InstructionNode instr;
            private final Block currentBlock;

            private StackItem(InstructionNode instr, Block currentBlock) {
                Objects.requireNonNull(instr);
                Objects.requireNonNull(currentBlock);
                this.instr = instr;
                this.currentBlock = currentBlock;
            }
        }

        /**
         * This algorithm can't be recursive, possibly too much instructions in
         * methods.
         */
        private void createBlocks(InstructionNode root, List<Block> blocks) {
            final Stack<StackItem> stack = new Stack<>();
            stack.push(new StackItem(root, newBlock(root)));
            while (!stack.isEmpty()) {
                final StackItem item = stack.pop();
                final Block currentBlock = item.currentBlock;
                final InstructionNode current = item.instr;
                // loop
                if (currentBlock.instr.contains(current)) {
                    currentBlock.reachable.add(currentBlock);
                    continue;
                }
                Block existing = allBlocks.get(current.index);
                if (existing != null && existing != currentBlock) {
                    currentBlock.reachable.add(existing);
                    continue;
                }
                int previous = currentBlock.instr.size() > 0
                        ? currentBlock.instr.get(currentBlock.instr.size() - 1).getIndex() : -1;
                if (previous == -1 || current.getIndex() == previous + 1) {
                    currentBlock.instr.add(current);
                    if (current.next.isEmpty()) {
                        blocks.add(currentBlock);
                    } else {
                        if (current.next.size() > 1) {
                            blocks.add(currentBlock);
                            for (InstructionNode n : current.next) {
                                Block loop = allBlocks.get(n.index);
                                if (loop == null) {
                                    Block newBlock = newBlock(n);
                                    currentBlock.reachable.add(newBlock);
                                    stack.push(new StackItem(n, newBlock));
                                } else { // loop
                                    currentBlock.reachable.add(loop);
                                }
                            }
                        } else {
                            stack.push(new StackItem(current.next.get(0),
                                    currentBlock));
                        }
                    }
                } else { // to a new block...
                    // Do nothing...
                    blocks.add(currentBlock);
                    Block newBlock = newBlock(current);
                    currentBlock.reachable.add(newBlock);
                    stack.push(new StackItem(current, newBlock));
                }
            }
        }
    }
}
