/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

import static org.graalvm.compiler.bytecode.Bytecodes.AALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.AASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ARETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ARRAYLENGTH;
import static org.graalvm.compiler.bytecode.Bytecodes.ATHROW;
import static org.graalvm.compiler.bytecode.Bytecodes.BALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.BASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.CALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.CASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.FALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.GETFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO_W;
import static org.graalvm.compiler.bytecode.Bytecodes.IALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.IASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNONNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IFNULL;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ACMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPEQ;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPGT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLE;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPLT;
import static org.graalvm.compiler.bytecode.Bytecodes.IF_ICMPNE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static org.graalvm.compiler.bytecode.Bytecodes.IRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.LRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.RET;
import static org.graalvm.compiler.bytecode.Bytecodes.RETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.SALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.SASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.TABLESWITCH;
import static org.graalvm.compiler.core.common.GraalOptions.SupportJsrBytecodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeLookupSwitch;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.BytecodeSwitch;
import org.graalvm.compiler.bytecode.BytecodeTableSwitch;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.debug.Debug;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ExceptionHandler;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow graph
 * (CFG). It makes one linear passes over the bytecodes to build the CFG where it detects block
 * headers and connects them.
 * <p>
 * It also creates exception dispatch blocks for exception handling. These blocks are between a
 * bytecode that might throw an exception, and the actual exception handler entries, and are later
 * used to create the type checks with the exception handler catch types. If a bytecode is covered
 * by an exception handler, this bytecode ends the basic block. This guarantees that a) control flow
 * cannot be transferred to an exception dispatch block in the middle of a block, and b) that every
 * block has at most one exception dispatch block (which is always the last entry in the successor
 * list).
 * <p>
 * If a bytecode is covered by multiple exception handlers, a chain of exception dispatch blocks is
 * created so that multiple exception handler types can be checked. The chains are re-used if
 * multiple bytecodes are covered by the same exception handlers.
 * <p>
 * Note that exception unwinds, i.e., bytecodes that can throw an exception but the exception is not
 * handled in this method, do not end a basic block. Not modeling the exception unwind block reduces
 * the complexity of the CFG, and there is no algorithm yet where the exception unwind block would
 * matter.
 * <p>
 * The class also handles subroutines (jsr and ret bytecodes): subroutines are inlined by
 * duplicating the subroutine blocks. This is limited to simple, structured subroutines with a
 * maximum subroutine nesting of 4. Otherwise, a bailout is thrown.
 * <p>
 * Loops in the methods are detected. If a method contains an irreducible loop (a loop with more
 * than one entry), a bailout is thrown. This simplifies the compiler later on since only structured
 * loops need to be supported.
 * <p>
 * A data flow analysis computes the live local variables from the point of view of the interpreter.
 * The result is used later to prune frame states, i.e., remove local variable entries that are
 * guaranteed to be never used again (even in the case of deoptimization).
 * <p>
 * The algorithms and analysis in this class are conservative and do not use any assumptions or
 * profiling information.
 */
public final class BciBlockMapping {

    public static class BciBlock implements Cloneable {

        protected int id;
        public int startBci;
        public int endBci;
        public boolean isExceptionEntry;
        public boolean isLoopHeader;
        public int loopId;
        public int loopEnd;
        protected List<BciBlock> successors;
        private int predecessorCount;

        private boolean visited;
        private boolean active;
        public long loops;
        public JSRData jsrData;

        public static class JSRData implements Cloneable {
            public HashMap<JsrScope, BciBlock> jsrAlternatives;
            public JsrScope jsrScope = JsrScope.EMPTY_SCOPE;
            public BciBlock jsrSuccessor;
            public int jsrReturnBci;
            public BciBlock retSuccessor;
            public boolean endsWithRet = false;

            public JSRData copy() {
                try {
                    return (JSRData) this.clone();
                } catch (CloneNotSupportedException e) {
                    return null;
                }
            }
        }

        public BciBlock() {
            this.successors = new ArrayList<>(4);
        }

        public BciBlock exceptionDispatchBlock() {
            if (successors.size() > 0 && successors.get(successors.size() - 1) instanceof ExceptionDispatchBlock) {
                return successors.get(successors.size() - 1);
            }
            return null;
        }

        public int getId() {
            return id;
        }

        public int getPredecessorCount() {
            return this.predecessorCount;
        }

        public int numNormalSuccessors() {
            if (exceptionDispatchBlock() != null) {
                return successors.size() - 1;
            }
            return successors.size();
        }

        public BciBlock copy() {
            try {
                BciBlock block = (BciBlock) super.clone();
                if (block.jsrData != null) {
                    block.jsrData = block.jsrData.copy();
                }
                block.successors = new ArrayList<>(successors);
                return block;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("B").append(getId());
            sb.append('[').append(startBci).append("->").append(endBci);
            if (isLoopHeader || isExceptionEntry) {
                sb.append(' ');
                if (isLoopHeader) {
                    sb.append('L');
                }
                if (isExceptionEntry) {
                    sb.append('!');
                }
            }
            sb.append(']');
            return sb.toString();
        }

        public int getLoopDepth() {
            return Long.bitCount(loops);
        }

        public boolean isLoopHeader() {
            return isLoopHeader;
        }

        public boolean isExceptionEntry() {
            return isExceptionEntry;
        }

        public BciBlock getSuccessor(int index) {
            return successors.get(index);
        }

        /**
         * Get the loop id of the inner most loop.
         *
         * @return the loop id of the most inner loop or -1 if not part of any loop
         */
        public int getLoopId() {
            long l = loops;
            if (l == 0) {
                return -1;
            }
            int pos = 0;
            for (int lMask = 1; (l & lMask) == 0; lMask = lMask << 1) {
                pos++;
            }
            return pos;
        }

        /**
         * Iterate over loop ids.
         */
        public Iterable<Integer> loopIdIterable() {
            return new Iterable<Integer>() {
                @Override
                public Iterator<Integer> iterator() {
                    return idIterator(loops);
                }
            };
        }

        private static Iterator<Integer> idIterator(long field) {
            return new Iterator<Integer>() {

                long l = field;
                int pos = 0;
                int lMask = 1;

                @Override
                public Integer next() {
                    for (; (l & lMask) == 0; lMask = lMask << 1) {
                        pos++;
                    }
                    l &= ~lMask;
                    return pos;
                }

                @Override
                public boolean hasNext() {
                    return l != 0;
                }
            };

        }

        public double probability() {
            return 1D;
        }

        public BciBlock getPostdominator() {
            return null;
        }

        private JSRData getOrCreateJSRData() {
            if (jsrData == null) {
                jsrData = new JSRData();
            }
            return jsrData;
        }

        void setEndsWithRet() {
            getOrCreateJSRData().endsWithRet = true;
        }

        public JsrScope getJsrScope() {
            if (this.jsrData == null) {
                return JsrScope.EMPTY_SCOPE;
            } else {
                return jsrData.jsrScope;
            }
        }

        public boolean endsWithRet() {
            if (this.jsrData == null) {
                return false;
            } else {
                return jsrData.endsWithRet;
            }
        }

        void setRetSuccessor(BciBlock bciBlock) {
            this.getOrCreateJSRData().retSuccessor = bciBlock;
        }

        public BciBlock getRetSuccessor() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.retSuccessor;
            }
        }

        public BciBlock getJsrSuccessor() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.jsrSuccessor;
            }
        }

        public int getJsrReturnBci() {
            if (this.jsrData == null) {
                return -1;
            } else {
                return jsrData.jsrReturnBci;
            }
        }

        public HashMap<JsrScope, BciBlock> getJsrAlternatives() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.jsrAlternatives;
            }
        }

        public void initJsrAlternatives() {
            JSRData data = this.getOrCreateJSRData();
            if (data.jsrAlternatives == null) {
                data.jsrAlternatives = new HashMap<>();
            }
        }

        void setJsrScope(JsrScope nextScope) {
            this.getOrCreateJSRData().jsrScope = nextScope;
        }

        void setJsrSuccessor(BciBlock clone) {
            this.getOrCreateJSRData().jsrSuccessor = clone;
        }

        void setJsrReturnBci(int bci) {
            this.getOrCreateJSRData().jsrReturnBci = bci;
        }

        public int getSuccessorCount() {
            return successors.size();
        }

        public List<BciBlock> getSuccessors() {
            return successors;
        }

        void setId(int i) {
            this.id = i;
        }

        public void addSuccessor(BciBlock sux) {
            successors.add(sux);
            sux.predecessorCount++;
        }

        public void clearSucccessors() {
            for (BciBlock sux : successors) {
                sux.predecessorCount--;
            }
            successors.clear();
        }
    }

    public static class ExceptionDispatchBlock extends BciBlock {

        private HashMap<ExceptionHandler, ExceptionDispatchBlock> exceptionDispatch = new HashMap<>();

        public ExceptionHandler handler;
        public int deoptBci;
    }

    /**
     * The blocks found in this method, in reverse postorder.
     */
    private BciBlock[] blocks;
    public final Bytecode code;
    public boolean hasJsrBytecodes;

    private final ExceptionHandler[] exceptionHandlers;
    private BciBlock startBlock;
    private BciBlock[] loopHeaders;

    private static final int LOOP_HEADER_MAX_CAPACITY = Long.SIZE;
    private static final int LOOP_HEADER_INITIAL_CAPACITY = 4;

    private int blocksNotYetAssignedId;
    public int returnCount;
    private int returnBci;

    /**
     * Creates a new BlockMap instance from {@code code}.
     */
    private BciBlockMapping(Bytecode code) {
        this.code = code;
        this.exceptionHandlers = code.getExceptionHandlers();
    }

    public BciBlock[] getBlocks() {
        return this.blocks;
    }

    public int getReturnCount() {
        return this.returnCount;
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     */
    public void build(BytecodeStream stream) {
        int codeSize = code.getCodeSize();
        BciBlock[] blockMap = new BciBlock[codeSize];
        makeExceptionEntries(blockMap);
        iterateOverBytecodes(blockMap, stream);
        if (hasJsrBytecodes) {
            if (!SupportJsrBytecodes.getValue()) {
                throw new JsrNotSupportedBailout("jsr/ret parsing disabled");
            }
            createJsrAlternatives(blockMap, blockMap[0]);
        }
        if (Debug.isLogEnabled()) {
            this.log(blockMap, "Before BlockOrder");
        }
        computeBlockOrder(blockMap);
        fixLoopBits(blockMap);

        assert verify();

        startBlock = blockMap[0];
        if (Debug.isLogEnabled()) {
            this.log(blockMap, "Before LivenessAnalysis");
        }
    }

    private boolean verify() {
        for (BciBlock block : blocks) {
            assert blocks[block.getId()] == block;

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock sux = block.getSuccessor(i);
                if (sux instanceof ExceptionDispatchBlock) {
                    assert i == block.getSuccessorCount() - 1 : "Only one exception handler allowed, and it must be last in successors list";
                }
            }
        }

        return true;
    }

    private void makeExceptionEntries(BciBlock[] blockMap) {
        // start basic blocks at all exception handler blocks and mark them as exception entries
        for (ExceptionHandler h : this.exceptionHandlers) {
            BciBlock xhandler = makeBlock(blockMap, h.getHandlerBCI());
            xhandler.isExceptionEntry = true;
        }
    }

    private void iterateOverBytecodes(BciBlock[] blockMap, BytecodeStream stream) {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        BciBlock current = null;
        stream.setBCI(0);
        while (stream.currentBC() != Bytecodes.END) {
            int bci = stream.currentBCI();

            if (current == null || blockMap[bci] != null) {
                BciBlock b = makeBlock(blockMap, bci);
                if (current != null) {
                    addSuccessor(blockMap, current.endBci, b);
                }
                current = b;
            }
            blockMap[bci] = current;
            current.endBci = bci;

            switch (stream.currentBC()) {
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case RETURN: {
                    returnCount++;
                    current = null;
                    returnBci = bci;
                    break;
                }
                case ATHROW: {
                    current = null;
                    ExceptionDispatchBlock handler = handleExceptions(blockMap, bci);
                    if (handler != null) {
                        addSuccessor(blockMap, bci, handler);
                    }
                    break;
                }
                case IFEQ:      // fall through
                case IFNE:      // fall through
                case IFLT:      // fall through
                case IFGE:      // fall through
                case IFGT:      // fall through
                case IFLE:      // fall through
                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: // fall through
                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: // fall through
                case IFNULL:    // fall through
                case IFNONNULL: {
                    current = null;
                    addSuccessor(blockMap, bci, makeBlock(blockMap, stream.readBranchDest()));
                    addSuccessor(blockMap, bci, makeBlock(blockMap, stream.nextBCI()));
                    break;
                }
                case GOTO:
                case GOTO_W: {
                    current = null;
                    addSuccessor(blockMap, bci, makeBlock(blockMap, stream.readBranchDest()));
                    break;
                }
                case TABLESWITCH: {
                    current = null;
                    addSwitchSuccessors(blockMap, bci, new BytecodeTableSwitch(stream, bci));
                    break;
                }
                case LOOKUPSWITCH: {
                    current = null;
                    addSwitchSuccessors(blockMap, bci, new BytecodeLookupSwitch(stream, bci));
                    break;
                }
                case JSR:
                case JSR_W: {
                    hasJsrBytecodes = true;
                    int target = stream.readBranchDest();
                    if (target == 0) {
                        throw new JsrNotSupportedBailout("jsr target bci 0 not allowed");
                    }
                    BciBlock b1 = makeBlock(blockMap, target);
                    current.setJsrSuccessor(b1);
                    current.setJsrReturnBci(stream.nextBCI());
                    current = null;
                    addSuccessor(blockMap, bci, b1);
                    break;
                }
                case RET: {
                    current.setEndsWithRet();
                    current = null;
                    break;
                }
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEVIRTUAL:
                case INVOKEDYNAMIC: {
                    current = null;
                    addSuccessor(blockMap, bci, makeBlock(blockMap, stream.nextBCI()));
                    ExceptionDispatchBlock handler = handleExceptions(blockMap, bci);
                    if (handler != null) {
                        addSuccessor(blockMap, bci, handler);
                    }
                    break;
                }
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case ARRAYLENGTH:
                case PUTFIELD:
                case GETFIELD: {
                    ExceptionDispatchBlock handler = handleExceptions(blockMap, bci);
                    if (handler != null) {
                        current = null;
                        addSuccessor(blockMap, bci, makeBlock(blockMap, stream.nextBCI()));
                        addSuccessor(blockMap, bci, handler);
                    }
                }
            }
            stream.next();
        }
    }

    private BciBlock makeBlock(BciBlock[] blockMap, int startBci) {
        BciBlock oldBlock = blockMap[startBci];
        if (oldBlock == null) {
            BciBlock newBlock = new BciBlock();
            blocksNotYetAssignedId++;
            newBlock.startBci = startBci;
            blockMap[startBci] = newBlock;
            return newBlock;

        } else if (oldBlock.startBci != startBci) {
            // Backward branch into the middle of an already processed block.
            // Add the correct fall-through successor.
            BciBlock newBlock = new BciBlock();
            blocksNotYetAssignedId++;
            newBlock.startBci = startBci;
            newBlock.endBci = oldBlock.endBci;
            for (BciBlock oldSuccessor : oldBlock.getSuccessors()) {
                newBlock.addSuccessor(oldSuccessor);
            }

            oldBlock.endBci = startBci - 1;
            oldBlock.clearSucccessors();
            oldBlock.addSuccessor(newBlock);

            for (int i = startBci; i <= newBlock.endBci; i++) {
                blockMap[i] = newBlock;
            }
            return newBlock;

        } else {
            return oldBlock;
        }
    }

    private void addSwitchSuccessors(BciBlock[] blockMap, int predBci, BytecodeSwitch bswitch) {
        // adds distinct targets to the successor list
        Collection<Integer> targets = new TreeSet<>();
        for (int i = 0; i < bswitch.numberOfCases(); i++) {
            targets.add(bswitch.targetAt(i));
        }
        targets.add(bswitch.defaultTarget());
        for (int targetBci : targets) {
            addSuccessor(blockMap, predBci, makeBlock(blockMap, targetBci));
        }
    }

    private static void addSuccessor(BciBlock[] blockMap, int predBci, BciBlock sux) {
        BciBlock predecessor = blockMap[predBci];
        if (sux.isExceptionEntry) {
            throw new PermanentBailoutException("Exception handler can be reached by both normal and exceptional control flow");
        }
        predecessor.addSuccessor(sux);
    }

    private final ArrayList<BciBlock> jsrVisited = new ArrayList<>();

    private void createJsrAlternatives(BciBlock[] blockMap, BciBlock block) {
        jsrVisited.add(block);
        JsrScope scope = block.getJsrScope();

        if (block.endsWithRet()) {
            block.setRetSuccessor(blockMap[scope.nextReturnAddress()]);
            block.addSuccessor(block.getRetSuccessor());
            assert block.getRetSuccessor() != block.getJsrSuccessor();
        }
        Debug.log("JSR alternatives block %s  sux %s  jsrSux %s  retSux %s  jsrScope %s", block, block.getSuccessors(), block.getJsrSuccessor(), block.getRetSuccessor(), block.getJsrScope());

        if (block.getJsrSuccessor() != null || !scope.isEmpty()) {
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock successor = block.getSuccessor(i);
                JsrScope nextScope = scope;
                if (successor == block.getJsrSuccessor()) {
                    nextScope = scope.push(block.getJsrReturnBci());
                }
                if (successor == block.getRetSuccessor()) {
                    nextScope = scope.pop();
                }
                if (!successor.getJsrScope().isPrefixOf(nextScope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow  (" + successor.getJsrScope() + " " + nextScope + ")");
                }
                if (!nextScope.isEmpty()) {
                    BciBlock clone;
                    if (successor.getJsrAlternatives() != null && successor.getJsrAlternatives().containsKey(nextScope)) {
                        clone = successor.getJsrAlternatives().get(nextScope);
                    } else {
                        successor.initJsrAlternatives();
                        clone = successor.copy();
                        blocksNotYetAssignedId++;
                        clone.setJsrScope(nextScope);
                        successor.getJsrAlternatives().put(nextScope, clone);
                    }
                    block.getSuccessors().set(i, clone);
                    if (successor == block.getJsrSuccessor()) {
                        block.setJsrSuccessor(clone);
                    }
                    if (successor == block.getRetSuccessor()) {
                        block.setRetSuccessor(clone);
                    }
                }
            }
        }
        for (BciBlock successor : block.getSuccessors()) {
            if (!jsrVisited.contains(successor)) {
                createJsrAlternatives(blockMap, successor);
            }
        }
    }

    private HashMap<ExceptionHandler, ExceptionDispatchBlock> initialExceptionDispatch = CollectionsFactory.newMap();

    private ExceptionDispatchBlock handleExceptions(BciBlock[] blockMap, int bci) {
        ExceptionDispatchBlock lastHandler = null;

        for (int i = exceptionHandlers.length - 1; i >= 0; i--) {
            ExceptionHandler h = exceptionHandlers[i];
            if (h.getStartBCI() <= bci && bci < h.getEndBCI()) {
                if (h.isCatchAll()) {
                    // Discard all information about succeeding exception handlers, since they can
                    // never be reached.
                    lastHandler = null;
                }

                HashMap<ExceptionHandler, ExceptionDispatchBlock> exceptionDispatch = lastHandler != null ? lastHandler.exceptionDispatch : initialExceptionDispatch;
                ExceptionDispatchBlock curHandler = exceptionDispatch.get(h);
                if (curHandler == null) {
                    curHandler = new ExceptionDispatchBlock();
                    blocksNotYetAssignedId++;
                    curHandler.startBci = -1;
                    curHandler.endBci = -1;
                    curHandler.deoptBci = bci;
                    curHandler.handler = h;
                    curHandler.addSuccessor(blockMap[h.getHandlerBCI()]);
                    if (lastHandler != null) {
                        curHandler.addSuccessor(lastHandler);
                    }
                    exceptionDispatch.put(h, curHandler);
                }
                lastHandler = curHandler;
            }
        }
        return lastHandler;
    }

    private boolean loopChanges;

    private void fixLoopBits(BciBlock[] blockMap) {
        do {
            loopChanges = false;
            for (BciBlock b : blocks) {
                b.visited = false;
            }

            long loop = fixLoopBits(blockMap, blockMap[0]);

            if (loop != 0) {
                // There is a path from a loop end to the method entry that does not pass the loop
                // header.
                // Therefore, the loop is non reducible (has more than one entry).
                // We don't want to compile such methods because the IR only supports structured
                // loops.
                throw new PermanentBailoutException("Non-reducible loop: %016x", loop);
            }
        } while (loopChanges);
    }

    private void computeBlockOrder(BciBlock[] blockMap) {
        int maxBlocks = blocksNotYetAssignedId;
        this.blocks = new BciBlock[blocksNotYetAssignedId];
        long loop = computeBlockOrder(blockMap[0]);

        if (loop != 0) {
            // There is a path from a loop end to the method entry that does not pass the loop
            // header. Therefore, the loop is non reducible (has more than one entry).
            // We don't want to compile such methods because the IR only supports structured loops.
            throw new PermanentBailoutException("Non-reducible loop");
        }

        // Purge null entries for unreached blocks and sort blocks such that loop bodies are always
        // consecutively in the array.
        int blockCount = maxBlocks - blocksNotYetAssignedId + 2;
        BciBlock[] newBlocks = new BciBlock[blockCount];
        int next = 0;
        for (int i = 0; i < blocks.length; ++i) {
            BciBlock b = blocks[i];
            if (b != null) {
                b.setId(next);
                newBlocks[next++] = b;
                if (b.isLoopHeader) {
                    next = handleLoopHeader(newBlocks, next, i, b);
                }
            }
        }

        // Add return block.
        BciBlock returnBlock = new BciBlock();
        returnBlock.startBci = returnBci;
        returnBlock.endBci = returnBci;
        returnBlock.setId(newBlocks.length - 2);
        newBlocks[newBlocks.length - 2] = returnBlock;

        // Add unwind block.
        ExceptionDispatchBlock unwindBlock = new ExceptionDispatchBlock();
        unwindBlock.startBci = -1;
        unwindBlock.endBci = -1;
        unwindBlock.deoptBci = code.getMethod().isSynchronized() ? BytecodeFrame.UNWIND_BCI : BytecodeFrame.AFTER_EXCEPTION_BCI;
        unwindBlock.setId(newBlocks.length - 1);
        newBlocks[newBlocks.length - 1] = unwindBlock;

        blocks = newBlocks;
    }

    private int handleLoopHeader(BciBlock[] newBlocks, int nextStart, int i, BciBlock loopHeader) {
        int next = nextStart;
        int endOfLoop = nextStart - 1;
        for (int j = i + 1; j < blocks.length; ++j) {
            BciBlock other = blocks[j];
            if (other != null && (other.loops & (1L << loopHeader.loopId)) != 0) {
                other.setId(next);
                endOfLoop = next;
                newBlocks[next++] = other;
                blocks[j] = null;
                if (other.isLoopHeader) {
                    next = handleLoopHeader(newBlocks, next, j, other);
                }
            }
        }
        loopHeader.loopEnd = endOfLoop;
        return next;
    }

    public void log(BciBlock[] blockMap, String name) {
        if (Debug.isLogEnabled()) {
            String n = System.lineSeparator();
            StringBuilder sb = new StringBuilder(Debug.currentScope()).append("BlockMap ").append(name).append(" :");
            sb.append(n);
            Iterable<BciBlock> it;
            if (blocks == null) {
                it = new HashSet<>(Arrays.asList(blockMap));
            } else {
                it = Arrays.asList(blocks);
            }
            for (BciBlock b : it) {
                if (b == null) {
                    continue;
                }
                sb.append("B").append(b.getId()).append(" (").append(b.startBci).append(" -> ").append(b.endBci).append(")");
                if (b.isLoopHeader) {
                    sb.append(" LoopHeader");
                }
                if (b.isExceptionEntry) {
                    sb.append(" ExceptionEntry");
                }
                sb.append(n).append("  Sux : ");
                for (BciBlock s : b.getSuccessors()) {
                    sb.append("B").append(s.getId()).append(" (").append(s.startBci).append(" -> ").append(s.endBci).append(")");
                    if (s.isExceptionEntry) {
                        sb.append("!");
                    }
                    sb.append(" ");
                }
                sb.append(n).append("  Loop : ");
                for (int pos : b.loopIdIterable()) {
                    sb.append("B").append(loopHeaders[pos].getId()).append(" ");
                }
                sb.append(n);
            }
            Debug.log("%s", sb);
        }
    }

    /**
     * Get the header block for a loop index.
     */
    public BciBlock getLoopHeader(int index) {
        return loopHeaders[index];
    }

    /**
     * The next available loop number.
     */
    private int nextLoop;

    /**
     * Mark the block as a loop header, using the next available loop number. Also checks for corner
     * cases that we don't want to compile.
     */
    private void makeLoopHeader(BciBlock block) {
        if (!block.isLoopHeader) {
            block.isLoopHeader = true;

            if (block.isExceptionEntry) {
                // Loops that are implicitly formed by an exception handler lead to all sorts of
                // corner cases.
                // Don't compile such methods for now, until we see a concrete case that allows
                // checking for correctness.
                throw new PermanentBailoutException("Loop formed by an exception handler");
            }
            if (nextLoop >= LOOP_HEADER_MAX_CAPACITY) {
                // This restriction can be removed by using a fall-back to a BitSet in case we have
                // more than 64 loops
                // Don't compile such methods for now, until we see a concrete case that allows
                // checking for correctness.
                throw new PermanentBailoutException("Too many loops in method");
            }

            assert block.loops == 0;
            block.loops = 1L << nextLoop;
            Debug.log("makeLoopHeader(%s) -> %x", block, block.loops);
            if (loopHeaders == null) {
                loopHeaders = new BciBlock[LOOP_HEADER_INITIAL_CAPACITY];
            } else if (nextLoop >= loopHeaders.length) {
                loopHeaders = Arrays.copyOf(loopHeaders, LOOP_HEADER_MAX_CAPACITY);
            }
            loopHeaders[nextLoop] = block;
            block.loopId = nextLoop;
            nextLoop++;
        }
        assert Long.bitCount(block.loops) == 1;
    }

    /**
     * Depth-first traversal of the control flow graph. The flag {@linkplain BciBlock#visited} is
     * used to visit every block only once. The flag {@linkplain BciBlock#active} is used to detect
     * cycles (backward edges).
     */
    private long computeBlockOrder(BciBlock block) {
        if (block.visited) {
            if (block.active) {
                // Reached block via backward branch.
                makeLoopHeader(block);
                // Return cached loop information for this block.
                return block.loops;
            } else if (block.isLoopHeader) {
                return block.loops & ~(1L << block.loopId);
            } else {
                return block.loops;
            }
        }

        block.visited = true;
        block.active = true;

        long loops = 0;
        for (BciBlock successor : block.getSuccessors()) {
            // Recursively process successors.
            loops |= computeBlockOrder(successor);
            if (successor.active) {
                // Reached block via backward branch.
                loops |= (1L << successor.loopId);
            }
        }

        block.loops = loops;
        Debug.log("computeBlockOrder(%s) -> %x", block, block.loops);

        if (block.isLoopHeader) {
            loops &= ~(1L << block.loopId);
        }

        block.active = false;
        blocksNotYetAssignedId--;
        blocks[blocksNotYetAssignedId] = block;

        return loops;
    }

    private long fixLoopBits(BciBlock[] blockMap, BciBlock block) {
        if (block.visited) {
            // Return cached loop information for this block.
            if (block.isLoopHeader) {
                return block.loops & ~(1L << block.loopId);
            } else {
                return block.loops;
            }
        }

        block.visited = true;
        long loops = block.loops;
        for (BciBlock successor : block.getSuccessors()) {
            // Recursively process successors.
            loops |= fixLoopBits(blockMap, successor);
        }
        if (block.loops != loops) {
            loopChanges = true;
            block.loops = loops;
            Debug.log("fixLoopBits0(%s) -> %x", block, block.loops);
        }

        if (block.isLoopHeader) {
            loops &= ~(1L << block.loopId);
        }

        return loops;
    }

    public static BciBlockMapping create(BytecodeStream stream, Bytecode code) {
        BciBlockMapping map = new BciBlockMapping(code);
        map.build(stream);
        if (Debug.isDumpEnabled(Debug.INFO_LOG_LEVEL)) {
            Debug.dump(Debug.INFO_LOG_LEVEL, map, code.getMethod().format("After block building %f %R %H.%n(%P)"));
        }

        return map;
    }

    public BciBlock[] getLoopHeaders() {
        return loopHeaders;
    }

    public BciBlock getStartBlock() {
        return startBlock;
    }

    public BciBlock getReturnBlock() {
        return blocks[blocks.length - 2];
    }

    public ExceptionDispatchBlock getUnwindBlock() {
        return (ExceptionDispatchBlock) blocks[blocks.length - 1];
    }

    public int getLoopCount() {
        return nextLoop;
    }

    public int getBlockCount() {
        return blocks.length;
    }
}
