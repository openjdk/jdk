/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.bytecode.Bytecodes.ACONST_NULL;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.ARETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ARRAYLENGTH;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ATHROW;
import static org.graalvm.compiler.bytecode.Bytecodes.BALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.BASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.BIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.BREAKPOINT;
import static org.graalvm.compiler.bytecode.Bytecodes.CALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.CASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.CHECKCAST;
import static org.graalvm.compiler.bytecode.Bytecodes.D2F;
import static org.graalvm.compiler.bytecode.Bytecodes.D2I;
import static org.graalvm.compiler.bytecode.Bytecodes.D2L;
import static org.graalvm.compiler.bytecode.Bytecodes.DADD;
import static org.graalvm.compiler.bytecode.Bytecodes.DALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.DCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.DNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.DREM;
import static org.graalvm.compiler.bytecode.Bytecodes.DRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP2_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X1;
import static org.graalvm.compiler.bytecode.Bytecodes.DUP_X2;
import static org.graalvm.compiler.bytecode.Bytecodes.F2D;
import static org.graalvm.compiler.bytecode.Bytecodes.F2I;
import static org.graalvm.compiler.bytecode.Bytecodes.F2L;
import static org.graalvm.compiler.bytecode.Bytecodes.FADD;
import static org.graalvm.compiler.bytecode.Bytecodes.FALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPG;
import static org.graalvm.compiler.bytecode.Bytecodes.FCMPL;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FCONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.FNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.FREM;
import static org.graalvm.compiler.bytecode.Bytecodes.FRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.GETFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.GETSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO;
import static org.graalvm.compiler.bytecode.Bytecodes.GOTO_W;
import static org.graalvm.compiler.bytecode.Bytecodes.I2B;
import static org.graalvm.compiler.bytecode.Bytecodes.I2C;
import static org.graalvm.compiler.bytecode.Bytecodes.I2D;
import static org.graalvm.compiler.bytecode.Bytecodes.I2F;
import static org.graalvm.compiler.bytecode.Bytecodes.I2L;
import static org.graalvm.compiler.bytecode.Bytecodes.I2S;
import static org.graalvm.compiler.bytecode.Bytecodes.IADD;
import static org.graalvm.compiler.bytecode.Bytecodes.IALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.IAND;
import static org.graalvm.compiler.bytecode.Bytecodes.IASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_4;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_5;
import static org.graalvm.compiler.bytecode.Bytecodes.ICONST_M1;
import static org.graalvm.compiler.bytecode.Bytecodes.IDIV;
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
import static org.graalvm.compiler.bytecode.Bytecodes.IINC;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.IMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.INEG;
import static org.graalvm.compiler.bytecode.Bytecodes.INSTANCEOF;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static org.graalvm.compiler.bytecode.Bytecodes.IOR;
import static org.graalvm.compiler.bytecode.Bytecodes.IREM;
import static org.graalvm.compiler.bytecode.Bytecodes.IRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHL;
import static org.graalvm.compiler.bytecode.Bytecodes.ISHR;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ISUB;
import static org.graalvm.compiler.bytecode.Bytecodes.IUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.IXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR;
import static org.graalvm.compiler.bytecode.Bytecodes.JSR_W;
import static org.graalvm.compiler.bytecode.Bytecodes.L2D;
import static org.graalvm.compiler.bytecode.Bytecodes.L2F;
import static org.graalvm.compiler.bytecode.Bytecodes.L2I;
import static org.graalvm.compiler.bytecode.Bytecodes.LADD;
import static org.graalvm.compiler.bytecode.Bytecodes.LALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LAND;
import static org.graalvm.compiler.bytecode.Bytecodes.LASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LCMP;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LCONST_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC2_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDC_W;
import static org.graalvm.compiler.bytecode.Bytecodes.LDIV;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LMUL;
import static org.graalvm.compiler.bytecode.Bytecodes.LNEG;
import static org.graalvm.compiler.bytecode.Bytecodes.LOOKUPSWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.LOR;
import static org.graalvm.compiler.bytecode.Bytecodes.LREM;
import static org.graalvm.compiler.bytecode.Bytecodes.LRETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHL;
import static org.graalvm.compiler.bytecode.Bytecodes.LSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LSUB;
import static org.graalvm.compiler.bytecode.Bytecodes.LUSHR;
import static org.graalvm.compiler.bytecode.Bytecodes.LXOR;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITORENTER;
import static org.graalvm.compiler.bytecode.Bytecodes.MONITOREXIT;
import static org.graalvm.compiler.bytecode.Bytecodes.MULTIANEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NEW;
import static org.graalvm.compiler.bytecode.Bytecodes.NEWARRAY;
import static org.graalvm.compiler.bytecode.Bytecodes.NOP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP;
import static org.graalvm.compiler.bytecode.Bytecodes.POP2;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTFIELD;
import static org.graalvm.compiler.bytecode.Bytecodes.PUTSTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.RET;
import static org.graalvm.compiler.bytecode.Bytecodes.RETURN;
import static org.graalvm.compiler.bytecode.Bytecodes.SALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.SASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.SIPUSH;
import static org.graalvm.compiler.bytecode.Bytecodes.SWAP;
import static org.graalvm.compiler.bytecode.Bytecodes.TABLESWITCH;
import static org.graalvm.compiler.bytecode.Bytecodes.WIDE;
import static org.graalvm.compiler.core.common.GraalOptions.SupportJsrBytecodes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeLookupSwitch;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.bytecode.BytecodeSwitch;
import org.graalvm.compiler.bytecode.BytecodeTableSwitch;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow graph
 * (CFG). It makes one linear pass over the bytecodes to build the CFG where it detects block
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
 * than one entry), a bailout is thrown or block duplication is attempted to make the loop
 * reducible. This simplifies the compiler later on since only structured loops need to be
 * supported.
 * <p>
 * A data flow analysis computes the live local variables from the point of view of the interpreter.
 * The result is used later to prune frame states, i.e., remove local variable entries that are
 * guaranteed to be never used again (even in the case of deoptimization).
 * <p>
 * The algorithms and analysis in this class are conservative and do not use any assumptions or
 * profiling information.
 */
public final class BciBlockMapping implements JavaMethodContext {
    public static class Options {
        @Option(help = "When enabled, some limited amount of duplication will be performed in order compile code containing irreducible loops.")//
        public static final OptionKey<Boolean> DuplicateIrreducibleLoops = new OptionKey<>(true);
        @Option(help = "How much duplication can happen because of irreducible loops before bailing out.", type = OptionType.Expert)//
        public static final OptionKey<Double> MaxDuplicationFactor = new OptionKey<>(2.0);
    }

    private static final int UNASSIGNED_ID = -1;

    public static class BciBlock implements Cloneable {

        int id = UNASSIGNED_ID;
        final int startBci;
        int endBci; // The bci of the last bytecode in the block
        private boolean isExceptionEntry;
        private boolean isLoopHeader;
        int loopId;
        List<BciBlock> successors;
        private int predecessorCount;

        private boolean visited;
        private boolean active;
        long loops;
        JSRData jsrData;
        List<TraversalStep> loopIdChain;
        boolean duplicate;

        public static class JSRData implements Cloneable {
            public EconomicMap<JsrScope, BciBlock> jsrAlternatives;
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

        BciBlock(int startBci) {
            this.startBci = startBci;
            this.successors = new ArrayList<>();
        }

        public int getStartBci() {
            return startBci;
        }

        public int getEndBci() {
            return endBci;
        }

        public long getLoops() {
            return loops;
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

        public BciBlock duplicate() {
            try {
                BciBlock block = (BciBlock) super.clone();
                if (block.jsrData != null) {
                    throw new PermanentBailoutException("Can not duplicate block with JSR data");
                }
                block.successors = new ArrayList<>(successors);
                block.loops = 0;
                block.loopId = 0;
                block.id = UNASSIGNED_ID;
                block.isLoopHeader = false;
                block.visited = false;
                block.active = false;
                block.predecessorCount = 0;
                block.loopIdChain = null;
                block.duplicate = true;
                return block;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("B").append(getId());
            sb.append('[').append(startBci).append("..").append(endBci);
            if (isLoopHeader || isExceptionEntry || this instanceof ExceptionDispatchBlock) {
                sb.append(' ');
                if (isLoopHeader) {
                    sb.append('L');
                }
                if (isExceptionEntry) {
                    sb.append('!');
                } else if (this instanceof ExceptionDispatchBlock) {
                    sb.append("<!>");
                }
            }
            sb.append(']');
            if (duplicate) {
                sb.append(" (duplicate)");
            }
            return sb.toString();
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

        public int getLoopId() {
            return loopId;
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

        public EconomicMap<JsrScope, BciBlock> getJsrAlternatives() {
            if (this.jsrData == null) {
                return null;
            } else {
                return jsrData.jsrAlternatives;
            }
        }

        public void initJsrAlternatives() {
            JSRData data = this.getOrCreateJSRData();
            if (data.jsrAlternatives == null) {
                data.jsrAlternatives = EconomicMap.create(Equivalence.DEFAULT);
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

        public boolean isExceptionDispatch() {
            return false;
        }

        public void getDebugProperties(Map<String, ? super Object> properties) {
            properties.put("assignedId", this.getId());
            properties.put("startBci", this.getStartBci());
            properties.put("endBci", this.getEndBci());
            properties.put("isExceptionEntry", this.isExceptionEntry());
            properties.put("isLoopHeader", this.isLoopHeader());
            properties.put("loopId", this.getLoopId());
            properties.put("loops", Long.toBinaryString(this.getLoops()));
            properties.put("predecessorCount", this.getPredecessorCount());
            properties.put("active", this.active);
            properties.put("visited", this.visited);
            properties.put("duplicate", this.duplicate);
            // JSRData?
        }
    }

    public static class ExceptionDispatchBlock extends BciBlock {
        public final ExceptionHandler handler;
        public final int deoptBci;

        /**
         * Constructor for a normal dispatcher.
         */
        ExceptionDispatchBlock(ExceptionHandler handler, int deoptBci) {
            super(handler.getHandlerBCI());
            this.endBci = startBci;
            this.deoptBci = deoptBci;
            this.handler = handler;
        }

        /**
         * Constructor for the method unwind dispatcher.
         */
        ExceptionDispatchBlock(int deoptBci) {
            super(deoptBci);
            this.endBci = deoptBci;
            this.deoptBci = deoptBci;
            this.handler = null;
        }

        @Override
        public boolean isExceptionDispatch() {
            return true;
        }

        @Override
        public void getDebugProperties(Map<String, ? super Object> properties) {
            super.getDebugProperties(properties);
            properties.put("deoptBci", this.deoptBci);
            if (this.handler != null) {
                properties.put("catch type", this.handler.getCatchType());
            }
        }
    }

    private static class TraversalStep {
        private final TraversalStep pred;
        private final BciBlock block;
        private int currentSuccessorIndex;
        private long loops;

        TraversalStep(TraversalStep pred, BciBlock block) {
            this.pred = pred;
            this.block = block;
            this.currentSuccessorIndex = 0;
            this.loops = 0;
        }

        TraversalStep(BciBlock block) {
            this(null, block);
        }

        @Override
        public String toString() {
            if (pred == null) {
                return "TraversalStep{block=" + block +
                                ", currentSuccessorIndex=" + currentSuccessorIndex +
                                ", loops=" + Long.toBinaryString(loops) +
                                '}';
            }
            return "TraversalStep{" +
                            "pred=" + pred +
                            ", block=" + block +
                            ", currentSuccessorIndex=" + currentSuccessorIndex +
                            ", loops=" + Long.toBinaryString(loops) +
                            '}';
        }
    }

    private static final class DuplicationTraversalStep extends TraversalStep {
        private final BciBlock loopHeader;
        private final EconomicMap<BciBlock, BciBlock> duplicationMap;

        DuplicationTraversalStep(TraversalStep pred, BciBlock block, BciBlock loopHeader) {
            super(pred, block);
            this.loopHeader = loopHeader;
            this.duplicationMap = EconomicMap.create();
        }

        DuplicationTraversalStep(DuplicationTraversalStep pred, BciBlock block) {
            super(pred, block);
            this.loopHeader = pred.loopHeader;
            this.duplicationMap = pred.duplicationMap;
        }

        @Override
        public String toString() {
            return super.toString() + " (duplicating " + loopHeader + ")";
        }
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
    private final DebugContext debug;
    private int postJsrBlockCount;
    private int newDuplicateBlocks;
    private int duplicateBlocks;

    /**
     * Creates a new BlockMap instance from {@code code}.
     */
    private BciBlockMapping(Bytecode code, DebugContext debug) {
        this.code = code;
        this.debug = debug;
        this.exceptionHandlers = code.getExceptionHandlers();
    }

    public BciBlock[] getBlocks() {
        return this.blocks;
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     */
    public void build(BytecodeStream stream, OptionValues options) {
        int codeSize = code.getCodeSize();
        BciBlock[] blockMap = new BciBlock[codeSize];
        makeExceptionEntries(blockMap);
        iterateOverBytecodes(blockMap, stream);
        startBlock = blockMap[0];
        if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After iterateOverBytecodes %f %R %H.%n(%P)"));
        }
        if (hasJsrBytecodes) {
            if (!SupportJsrBytecodes.getValue(options)) {
                throw new JsrNotSupportedBailout("jsr/ret parsing disabled");
            }
            createJsrAlternatives(blockMap, startBlock);
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After createJsrAlternatives %f %R %H.%n(%P)"));
            }
        }
        postJsrBlockCount = blocksNotYetAssignedId;
        if (debug.isLogEnabled()) {
            this.log(blockMap, "Before BlockOrder");
        }
        computeBlockOrder(blockMap);
        if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            debug.dump(DebugContext.INFO_LEVEL, this, code.getMethod().format("After computeBlockOrder %f %R %H.%n(%P)"));
        }

        assert verify();

        if (debug.isLogEnabled()) {
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
                    current = null;
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
                case IDIV:
                case IREM:
                case LDIV:
                case LREM:
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
                case CHECKCAST:
                case INSTANCEOF:
                case NEW:
                case NEWARRAY:
                case ANEWARRAY:
                case MULTIANEWARRAY:
                case PUTSTATIC:
                case GETSTATIC:
                case PUTFIELD:
                case GETFIELD:
                case LDC:
                case LDC_W:
                case LDC2_W:
                case MONITORENTER: {
                    /*
                     * All bytecodes that can trigger lazy class initialization via a
                     * ClassInitializationPlugin (allocations, static field access) must be listed
                     * because the class initializer is allowed to throw an exception, which
                     * requires proper exception handling.
                     */
                    ExceptionDispatchBlock handler = handleExceptions(blockMap, bci);
                    if (handler != null) {
                        current = null;
                        addSuccessor(blockMap, bci, makeBlock(blockMap, stream.nextBCI()));
                        addSuccessor(blockMap, bci, handler);
                    }
                    break;
                }

                case NOP:
                case ACONST_NULL:
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case LCONST_0:
                case LCONST_1:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                case DCONST_0:
                case DCONST_1:
                case BIPUSH:
                case SIPUSH:
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD:
                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3:
                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3:
                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3:
                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3:
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3:
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3:
                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3:
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3:
                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                case POP:
                case POP2:
                case DUP:
                case DUP_X1:
                case DUP_X2:
                case DUP2:
                case DUP2_X1:
                case DUP2_X2:
                case SWAP:
                case IADD:
                case LADD:
                case FADD:
                case DADD:
                case ISUB:
                case LSUB:
                case FSUB:
                case DSUB:
                case IMUL:
                case LMUL:
                case FMUL:
                case DMUL:
                case FDIV:
                case DDIV:
                case FREM:
                case DREM:
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case ISHL:
                case LSHL:
                case ISHR:
                case LSHR:
                case IUSHR:
                case LUSHR:
                case IAND:
                case LAND:
                case IOR:
                case LOR:
                case IXOR:
                case LXOR:
                case IINC:
                case I2L:
                case I2F:
                case I2D:
                case L2I:
                case L2F:
                case L2D:
                case F2I:
                case F2L:
                case F2D:
                case D2I:
                case D2L:
                case D2F:
                case I2B:
                case I2C:
                case I2S:
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                case MONITOREXIT:
                    // All stack manipulation, comparison, conversion and arithmetic operators
                    // except for idiv and irem can't throw exceptions so the don't need to connect
                    // exception edges. MONITOREXIT can't throw exceptions in the context of
                    // compiled code because of the structured locking requirement in the parser.
                    break;

                case WIDE:
                case BREAKPOINT:
                default:
                    throw new GraalError("Unhandled bytecode");
            }
            stream.next();
        }
    }

    private BciBlock makeBlock(BciBlock[] blockMap, int startBci) {
        BciBlock oldBlock = blockMap[startBci];
        if (oldBlock == null) {
            BciBlock newBlock = new BciBlock(startBci);
            blocksNotYetAssignedId++;
            blockMap[startBci] = newBlock;
            return newBlock;

        } else if (oldBlock.startBci != startBci) {
            // Backward branch into the middle of an already processed block.
            // Add the correct fall-through successor.
            BciBlock newBlock = new BciBlock(startBci);
            blocksNotYetAssignedId++;
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
        debug.log("JSR alternatives block %s  sux %s  jsrSux %s  retSux %s  jsrScope %s", block, block.getSuccessors(), block.getJsrSuccessor(), block.getRetSuccessor(), block.getJsrScope());

        if (block.getJsrSuccessor() != null || !scope.isEmpty()) {
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock successor = block.getSuccessor(i);
                JsrScope nextScope = scope;
                if (successor == block.getJsrSuccessor()) {
                    nextScope = scope.push(block.getJsrReturnBci(), successor);
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
            if (!jsrVisited.contains(successor) && shouldFollowEdge(successor, scope)) {
                createJsrAlternatives(blockMap, successor);
            }
        }
    }

    private static boolean shouldFollowEdge(BciBlock successor, JsrScope scope) {
        if (successor instanceof ExceptionDispatchBlock && scope.getJsrEntryBlock() != null) {
            ExceptionDispatchBlock exceptionDispatchBlock = (ExceptionDispatchBlock) successor;
            int bci = scope.getJsrEntryBlock().startBci;
            if (exceptionDispatchBlock.handler.getStartBCI() < bci && bci < exceptionDispatchBlock.handler.getEndBCI()) {
                // Handler covers start of JSR block and the bci before that => don't follow edge.
                return false;
            }
        }

        return true;
    }

    private ExceptionDispatchBlock handleExceptions(BciBlock[] blockMap, int bci) {
        ExceptionDispatchBlock lastHandler = null;
        int dispatchBlocks = 0;

        for (int i = exceptionHandlers.length - 1; i >= 0; i--) {
            ExceptionHandler h = exceptionHandlers[i];
            if (h.getStartBCI() <= bci && bci < h.getEndBCI()) {
                if (h.isCatchAll()) {
                    // Discard all information about succeeding exception handlers, since they can
                    // never be reached.
                    dispatchBlocks = 0;
                    lastHandler = null;
                }

                // We do not reuse exception dispatch blocks, because nested exception handlers
                // might have problems reasoning about the correct frame state.
                ExceptionDispatchBlock curHandler = new ExceptionDispatchBlock(h, bci);
                dispatchBlocks++;
                curHandler.addSuccessor(blockMap[h.getHandlerBCI()]);
                if (lastHandler != null) {
                    curHandler.addSuccessor(lastHandler);
                }
                lastHandler = curHandler;
            }
        }
        blocksNotYetAssignedId += dispatchBlocks;
        return lastHandler;
    }

    private void computeBlockOrder(BciBlock[] blockMap) {
        int maxBlocks = blocksNotYetAssignedId;
        this.blocks = new BciBlock[blocksNotYetAssignedId];
        computeBlockOrder(blockMap[0]);
        int duplicatedBlocks = newDuplicateBlocks + duplicateBlocks;
        if (duplicatedBlocks > 0) {
            debug.log(DebugContext.INFO_LEVEL, "Duplicated %d blocks. Original block count: %d", duplicatedBlocks, postJsrBlockCount);
        }

        // Purge null entries for unreached blocks and sort blocks such that loop bodies are always
        // consecutively in the array.
        int blockCount = maxBlocks - blocksNotYetAssignedId + 1 + duplicateBlocks;
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
        assert next == newBlocks.length - 1;

        // Add unwind block.
        ExceptionDispatchBlock unwindBlock = new ExceptionDispatchBlock(BytecodeFrame.AFTER_EXCEPTION_BCI);
        unwindBlock.setId(newBlocks.length - 1);
        newBlocks[newBlocks.length - 1] = unwindBlock;

        blocks = newBlocks;
    }

    private int handleLoopHeader(BciBlock[] newBlocks, int nextStart, int i, BciBlock loopHeader) {
        int next = nextStart;
        for (int j = i + 1; j < blocks.length; ++j) {
            BciBlock other = blocks[j];
            if (other != null && (other.loops & (1L << loopHeader.loopId)) != 0) {
                other.setId(next);
                newBlocks[next++] = other;
                blocks[j] = null;
                if (other.isLoopHeader) {
                    next = handleLoopHeader(newBlocks, next, j, other);
                }
            }
        }
        return next;
    }

    public void log(BciBlock[] blockMap, String name) {
        if (debug.isLogEnabled()) {
            debug.log("%sBlockMap %s: %n%s", debug.getCurrentScopeName(), name, toString(blockMap, loopHeaders));
        }
    }

    public static String toString(BciBlock[] blockMap, BciBlock[] loopHeadersMap) {
        if (blockMap == null) {
            return "no blockmap";
        }
        StringBuilder sb = new StringBuilder();
        Map<BciBlock, Integer> debugIds = new HashMap<>();
        int[] nextDebugId = new int[]{-2};
        ToIntFunction<BciBlock> getId = b -> {
            int id = b.getId();
            if (id < 0) {
                id = debugIds.computeIfAbsent(b, bb -> nextDebugId[0]--);
            }
            return id;
        };
        for (BciBlock b : blockMap) {
            if (b == null) {
                continue;
            }
            sb.append("B").append(getId.applyAsInt(b)).append("[").append(b.startBci).append("..").append(b.endBci).append("]");
            if (b.isLoopHeader) {
                sb.append(" LoopHeader");
            }
            if (b.isExceptionEntry) {
                sb.append(" ExceptionEntry");
            }
            if (b instanceof ExceptionDispatchBlock) {
                sb.append(" ExceptionDispatch");
            }
            if (!b.successors.isEmpty()) {
                sb.append(" Successors=[");
                for (BciBlock s : b.getSuccessors()) {
                    if (sb.charAt(sb.length() - 1) != '[') {
                        sb.append(", ");
                    }
                    sb.append("B").append(getId.applyAsInt(s));
                }
                sb.append("]");
            }
            if (b.loops != 0L && loopHeadersMap != null) {
                sb.append(" Loops=[");
                long loops = b.loops;
                do {
                    int pos = Long.numberOfTrailingZeros(loops);
                    if (sb.charAt(sb.length() - 1) != '[') {
                        sb.append(", ");
                    }
                    sb.append("B").append(getId.applyAsInt(loopHeadersMap[pos]));
                    loops ^= loops & -loops;
                } while (loops != 0);
                sb.append("]");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toString(blocks, loopHeaders);
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

    private void makeLoopHeader(BciBlock block) {
        assert !block.isLoopHeader;
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
        block.loops |= 1L << nextLoop;
        debug.log("makeLoopHeader(%s) -> %x", block, block.loops);
        if (loopHeaders == null) {
            loopHeaders = new BciBlock[LOOP_HEADER_INITIAL_CAPACITY];
        } else if (nextLoop >= loopHeaders.length) {
            loopHeaders = Arrays.copyOf(loopHeaders, LOOP_HEADER_MAX_CAPACITY);
        }
        loopHeaders[nextLoop] = block;
        block.loopId = nextLoop;
        nextLoop++;
    }

    private void propagateLoopBits(TraversalStep step, long loopBits) {
        TraversalStep s = step;
        while (s != null && (s.block.loops & loopBits) != loopBits) {
            s.block.loops |= loopBits;
            if (s.block.loopIdChain != null) {
                for (TraversalStep chain : s.block.loopIdChain) {
                    propagateLoopBits(chain, loopBits);
                }
            }
            s = s.pred;
        }
    }

    /**
     * Computes a block pre-order, finds and marks loops.
     *
     * <p>
     * This uses a depth-first traversal of the blocks. In order to detect irreducible loops, blocks
     * are marked as belonging to a block as soon as that is known. This is done by keeping a linked
     * list of the currently "active" blocks (the path from entry to the current block). To be able
     * to do this marking correctly when in the case of nested loops, merge points (including loop
     * headers) remember the path from their predecessor (see
     * {@link #propagateLoopBits(TraversalStep, long)}).
     * <p>
     * Since loops are marked eagerly, forward entries into an existing loop without going through
     * the loop header (i.e., irreducible loops) can be detected easily. In this case, if
     * {@link Options#DuplicateIrreducibleLoops} is enabled, the traversal starts to duplicate
     * blocks until it either exits the loop or reaches the header. Since this is a depth-first
     * traversal and the loop header is not active, we know that the loop and its inner-loops were
     * until then reducible.
     * <p>
     * This is not recursive to avoid stack overflow issues.
     */
    private void computeBlockOrder(BciBlock initialBlock) {
        ArrayDeque<TraversalStep> workStack = new ArrayDeque<>();
        workStack.push(new TraversalStep(initialBlock));
        while (!workStack.isEmpty()) {
            TraversalStep step = workStack.peek();
            BciBlock block = step.block;
            if (step.currentSuccessorIndex == 0) {
                block.visited = true;
                block.active = true;
            }
            if (step.currentSuccessorIndex < block.successors.size()) {
                BciBlock successor = block.getSuccessors().get(step.currentSuccessorIndex);
                if (step instanceof DuplicationTraversalStep) {
                    DuplicationTraversalStep duplicationStep = (DuplicationTraversalStep) step;
                    BciBlock targetHeader = duplicationStep.loopHeader;
                    if (successor != targetHeader && (successor.loops & 1 << targetHeader.loopId) != 0) {
                        // neither the target header nor an exit: duplicate or merge with duplicate
                        BciBlock duplicate = duplicationStep.duplicationMap.get(successor);
                        if (duplicate == null) {
                            duplicate = successor.duplicate();
                            newDuplicateBlocks++;
                            duplicationStep.duplicationMap.put(successor, duplicate);
                        }
                        successor = duplicate;
                        successor.predecessorCount++;
                        block.successors.set(step.currentSuccessorIndex, successor);
                    } else {
                        debug.dump(DebugContext.DETAILED_LEVEL, this, "Exiting duplication @ %s", successor);
                        debug.log("Exiting duplication @ %s", successor);
                        successor.predecessorCount++;
                    }
                }
                if (successor.visited) {
                    long loopBits;
                    boolean duplicationStarted = false;
                    if (successor.active) {
                        // Reached block via backward branch.
                        if (!successor.isLoopHeader) {
                            makeLoopHeader(successor);
                        }
                        loopBits = successor.loops;
                    } else {
                        // re-reaching control-flow through new path.
                        // Find loop bits
                        loopBits = successor.loops;
                        if (successor.isLoopHeader) {
                            // this is a forward edge
                            loopBits &= ~(1L << successor.loopId);
                        }
                        // Check if we are re-entering a loop in an irreducible way
                        long checkBits = loopBits;
                        int outermostInactiveLoopId = -1;
                        while (checkBits != 0) {
                            int id = Long.numberOfTrailingZeros(checkBits);
                            if (!loopHeaders[id].active) {
                                if (!Options.DuplicateIrreducibleLoops.getValue(debug.getOptions())) {
                                    throw new PermanentBailoutException("Irreducible");
                                } else if (outermostInactiveLoopId == -1 || (loopHeaders[id].loops & 1L << outermostInactiveLoopId) == 0) {
                                    outermostInactiveLoopId = id;
                                }
                            }
                            checkBits &= ~(1L << id);
                        }
                        if (outermostInactiveLoopId != -1) {
                            assert !(step instanceof DuplicationTraversalStep);
                            // we need to duplicate until we can merge with this loop's header
                            successor.predecessorCount--;
                            BciBlock duplicate = successor.duplicate();
                            duplicate.predecessorCount++;
                            block.successors.set(step.currentSuccessorIndex, duplicate);
                            DuplicationTraversalStep duplicationStep = new DuplicationTraversalStep(step, duplicate, loopHeaders[outermostInactiveLoopId]);
                            workStack.push(duplicationStep);
                            debug.log("Starting duplication @ %s", duplicate);
                            debug.dump(DebugContext.DETAILED_LEVEL, this, "Starting duplication @ %s", duplicate);
                            duplicationStep.duplicationMap.put(successor, duplicate);
                            newDuplicateBlocks++;
                            duplicationStarted = true;
                        }
                    }
                    if (!duplicationStarted) {
                        propagateLoopBits(step, loopBits);
                        if (successor.loopIdChain == null) {
                            successor.loopIdChain = new ArrayList<>(2);
                        }
                        successor.loopIdChain.add(step);
                        debug.dump(DebugContext.DETAILED_LEVEL, this, "After re-reaching %s", successor);
                    }
                } else if (step instanceof DuplicationTraversalStep) {
                    workStack.push(new DuplicationTraversalStep((DuplicationTraversalStep) step, successor));
                } else {
                    workStack.push(new TraversalStep(step, successor));
                }
                step.currentSuccessorIndex++;
            } else {
                // We processed all the successors of this block.
                block.active = false;
                assert checkBlocks(blocksNotYetAssignedId, block);
                blocksNotYetAssignedId--;
                if (blocksNotYetAssignedId < 0) {
                    // this should only happen if duplication is active
                    assert Options.DuplicateIrreducibleLoops.getValue(debug.getOptions());
                    duplicateBlocks += newDuplicateBlocks;
                    if (duplicateBlocks > postJsrBlockCount * Options.MaxDuplicationFactor.getValue(debug.getOptions())) {
                        throw new PermanentBailoutException("Non-reducible loop requires too much duplication");
                    }
                    // there are new duplicate blocks, re-number
                    debug.log(DebugContext.INFO_LEVEL, "Re-numbering blocks to make room for duplicates (old length: %d; new blocks: %d)", blocks.length, newDuplicateBlocks);
                    BciBlock[] newBlocks = new BciBlock[blocks.length + newDuplicateBlocks];
                    for (int i = 0; i < blocks.length; i++) {
                        newBlocks[i + newDuplicateBlocks] = blocks[i];
                        assert blocks[i].id == UNASSIGNED_ID;
                    }
                    blocksNotYetAssignedId += newDuplicateBlocks;
                    assert blocksNotYetAssignedId >= 0;
                    newDuplicateBlocks = 0;
                    blocks = newBlocks;
                }
                blocks[blocksNotYetAssignedId] = block;
                debug.log("computeBlockOrder(%s) -> %x", block, block.loops);
                debug.dump(DebugContext.DETAILED_LEVEL, this, "After adding %s", block);
                workStack.pop();
            }
        }
        long loops = initialBlock.loops;
        if (initialBlock.isLoopHeader) {
            loops &= ~(1L << initialBlock.loopId);
        }
        GraalError.guarantee(loops == 0, "Irreducible loops should already have been detected to duplicated");
    }

    private boolean checkBlocks(int start, BciBlock inserting) {
        for (int i = 0; i < start; i++) {
            assert blocks[i] == null;
        }
        EconomicSet<BciBlock> seen = EconomicSet.create(blocks.length - start);
        for (int i = start; i < blocks.length; i++) {
            assert blocks[i] != null;
            assert seen.add(blocks[i]);
        }
        assert !seen.contains(inserting) : "Trying to add " + inserting + " again";
        return true;
    }

    @SuppressWarnings("try")
    public static BciBlockMapping create(BytecodeStream stream, Bytecode code, OptionValues options, DebugContext debug) {
        BciBlockMapping map = new BciBlockMapping(code, debug);
        try (Scope scope = debug.scope("BciBlockMapping", map)) {
            map.build(stream, options);
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                debug.dump(DebugContext.INFO_LEVEL, map, code.getMethod().format("After block building %f %R %H.%n(%P)"));
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        return map;
    }

    public BciBlock[] getLoopHeaders() {
        return loopHeaders;
    }

    public BciBlock getStartBlock() {
        return startBlock;
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

    @Override
    public JavaMethod asJavaMethod() {
        return code.getMethod();
    }
}
