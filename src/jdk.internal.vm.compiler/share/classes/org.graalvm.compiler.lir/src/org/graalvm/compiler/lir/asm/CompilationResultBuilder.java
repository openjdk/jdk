/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.asm;

import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.code.DataSection.RawData;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;

/**
 * Fills in a {@link CompilationResult} as its code is being assembled.
 *
 * @see CompilationResultBuilderFactory
 */
public class CompilationResultBuilder {

    public static class Options {
        @Option(help = "Include the LIR as comments with the final assembly.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> PrintLIRWithAssembly = new OptionKey<>(false);
    }

    private static class ExceptionInfo {

        public final int codeOffset;
        public final LabelRef exceptionEdge;

        ExceptionInfo(int pcOffset, LabelRef exceptionEdge) {
            this.codeOffset = pcOffset;
            this.exceptionEdge = exceptionEdge;
        }
    }

    /**
     * Wrapper for a code annotation that was produced by the {@link Assembler}.
     */
    public static final class AssemblerAnnotation extends CodeAnnotation {

        public final Assembler.CodeAnnotation assemblerCodeAnnotation;

        public AssemblerAnnotation(Assembler.CodeAnnotation assemblerCodeAnnotation) {
            super(assemblerCodeAnnotation.instructionPosition);
            this.assemblerCodeAnnotation = assemblerCodeAnnotation;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return assemblerCodeAnnotation.toString();
        }
    }

    public final Assembler asm;
    public final DataBuilder dataBuilder;
    public final CompilationResult compilationResult;
    public final Register nullRegister;
    public final TargetDescription target;
    public final CodeCacheProvider codeCache;
    public final ForeignCallsProvider foreignCalls;
    public final FrameMap frameMap;

    /**
     * The LIR for which code is being generated.
     */
    protected LIR lir;

    /**
     * The index of the block currently being emitted.
     */
    protected int currentBlockIndex;

    /**
     * The object that emits code for managing a method's frame.
     */
    public final FrameContext frameContext;

    private List<ExceptionInfo> exceptionInfoList;

    private final OptionValues options;
    private final DebugContext debug;
    private final EconomicMap<Constant, Data> dataCache;

    private Consumer<LIRInstruction> beforeOp;
    private Consumer<LIRInstruction> afterOp;

    public final boolean mustReplaceWithNullRegister(JavaConstant nullConstant) {
        return !nullRegister.equals(Register.None) && JavaConstant.NULL_POINTER.equals(nullConstant);
    }

    public CompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, DebugContext debug, CompilationResult compilationResult, Register nullRegister) {
        this(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, nullRegister, EconomicMap.create(Equivalence.DEFAULT));
    }

    public CompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, DebugContext debug, CompilationResult compilationResult, Register nullRegister, EconomicMap<Constant, Data> dataCache) {
        this.target = codeCache.getTarget();
        this.codeCache = codeCache;
        this.foreignCalls = foreignCalls;
        this.frameMap = frameMap;
        this.asm = asm;
        this.dataBuilder = dataBuilder;
        this.compilationResult = compilationResult;
        this.nullRegister = nullRegister;
        this.frameContext = frameContext;
        this.options = options;
        this.debug = debug;
        assert frameContext != null;
        this.dataCache = dataCache;

        if (dataBuilder.needDetailedPatchingInformation() || Assertions.assertionsEnabled()) {
            /*
             * Always enabled in debug mode, even when the VM does not request detailed information,
             * to increase test coverage.
             */
            asm.setCodePatchingAnnotationConsumer(assemblerCodeAnnotation -> compilationResult.addAnnotation(new AssemblerAnnotation(assemblerCodeAnnotation)));
        }
    }

    public void setTotalFrameSize(int frameSize) {
        compilationResult.setTotalFrameSize(frameSize);
    }

    public void setMaxInterpreterFrameSize(int maxInterpreterFrameSize) {
        compilationResult.setMaxInterpreterFrameSize(maxInterpreterFrameSize);
    }

    public Mark recordMark(Object id) {
        return compilationResult.recordMark(asm.position(), id);
    }

    public void blockComment(String s) {
        compilationResult.addAnnotation(new CompilationResult.CodeComment(asm.position(), s));
    }

    /**
     * Sets the {@linkplain CompilationResult#setTargetCode(byte[], int) code} and
     * {@linkplain CompilationResult#recordExceptionHandler(int, int) exception handler} fields of
     * the compilation result and then {@linkplain #closeCompilationResult() closes} it.
     */
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(false), position);

        // Record exception handlers if they exist
        if (exceptionInfoList != null) {
            for (ExceptionInfo ei : exceptionInfoList) {
                int codeOffset = ei.codeOffset;
                compilationResult.recordExceptionHandler(codeOffset, ei.exceptionEdge.label().position());
            }
        }
        closeCompilationResult();
    }

    /**
     * Calls {@link CompilationResult#close()} on {@link #compilationResult}.
     */
    protected void closeCompilationResult() {
        compilationResult.close();
    }

    public void recordExceptionHandlers(int pcOffset, LIRFrameState info) {
        if (info != null) {
            if (info.exceptionEdge != null) {
                if (exceptionInfoList == null) {
                    exceptionInfoList = new ArrayList<>(4);
                }
                exceptionInfoList.add(new ExceptionInfo(pcOffset, info.exceptionEdge));
            }
        }
    }

    public void recordImplicitException(int pcOffset, LIRFrameState info) {
        compilationResult.recordInfopoint(pcOffset, info.debugInfo(), InfopointReason.IMPLICIT_EXCEPTION);
        assert info.exceptionEdge == null;
    }

    public void recordDirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, true);
    }

    public void recordIndirectCall(int posBefore, int posAfter, InvokeTarget callTarget, LIRFrameState info) {
        DebugInfo debugInfo = info != null ? info.debugInfo() : null;
        compilationResult.recordCall(posBefore, posAfter - posBefore, callTarget, debugInfo, false);
    }

    public void recordInfopoint(int pos, LIRFrameState info, InfopointReason reason) {
        // infopoints always need debug info
        DebugInfo debugInfo = info.debugInfo();
        recordInfopoint(pos, debugInfo, reason);
    }

    public void recordInfopoint(int pos, DebugInfo debugInfo, InfopointReason reason) {
        compilationResult.recordInfopoint(pos, debugInfo, reason);
    }

    public void recordSourceMapping(int pcOffset, int endPcOffset, NodeSourcePosition sourcePosition) {
        compilationResult.recordSourceMapping(pcOffset, endPcOffset, sourcePosition);
    }

    public void recordInlineDataInCode(Constant data) {
        assert data != null;
        int pos = asm.position();
        debug.log("Inline data in code: pos = %d, data = %s", pos, data);
        if (data instanceof VMConstant) {
            compilationResult.recordDataPatch(pos, new ConstantReference((VMConstant) data));
        }
    }

    public void recordInlineDataInCodeWithNote(Constant data, Object note) {
        assert data != null;
        int pos = asm.position();
        debug.log("Inline data in code: pos = %d, data = %s, note = %s", pos, data, note);
        if (data instanceof VMConstant) {
            compilationResult.recordDataPatchWithNote(pos, new ConstantReference((VMConstant) data), note);
        }
    }

    public AbstractAddress recordDataSectionReference(Data data) {
        assert data != null;
        DataSectionReference reference = compilationResult.getDataSection().insertData(data);
        int instructionStart = asm.position();
        compilationResult.recordDataPatch(instructionStart, reference);
        return asm.getPlaceholder(instructionStart);
    }

    public AbstractAddress recordDataReferenceInCode(DataPointerConstant constant) {
        return recordDataReferenceInCode(constant, constant.getAlignment());
    }

    public AbstractAddress recordDataReferenceInCode(Constant constant, int alignment) {
        assert constant != null;
        debug.log("Constant reference in code: pos = %d, data = %s", asm.position(), constant);
        Data data = createDataItem(constant);
        data.updateAlignment(alignment);
        return recordDataSectionReference(data);
    }

    public AbstractAddress recordDataReferenceInCode(Data data, int alignment) {
        assert data != null;
        data.updateAlignment(alignment);
        return recordDataSectionReference(data);
    }

    public Data createDataItem(Constant constant) {
        Data data = dataCache.get(constant);
        if (data == null) {
            data = dataBuilder.createDataItem(constant);
            dataCache.put(constant, data);
        }
        return data;
    }

    public AbstractAddress recordDataReferenceInCode(byte[] data, int alignment) {
        assert data != null;
        if (debug.isLogEnabled()) {
            debug.log("Data reference in code: pos = %d, data = %s", asm.position(), Arrays.toString(data));
        }
        return recordDataSectionReference(new RawData(data, alignment));
    }

    /**
     * Notifies this object of a branch instruction at offset {@code pcOffset} in the code.
     *
     * @param isNegated negation status of the branch's condition.
     */
    @SuppressWarnings("unused")
    public void recordBranch(int pcOffset, boolean isNegated) {
    }

    /**
     * Notifies this object of a call instruction belonging to an INVOKEVIRTUAL or INVOKEINTERFACE
     * at offset {@code pcOffset} in the code.
     *
     * @param nodeSourcePosition source position of the corresponding invoke.
     */
    @SuppressWarnings("unused")
    public void recordInvokeVirtualOrInterfaceCallOp(int pcOffset, NodeSourcePosition nodeSourcePosition) {
    }

    /**
     * Notifies this object of a call instruction belonging to an INLINE_INVOKE at offset
     * {@code pcOffset} in the code.
     *
     * @param nodeSourcePosition source position of the corresponding invoke.
     */
    @SuppressWarnings("unused")
    public void recordInlineInvokeCallOp(int pcOffset, NodeSourcePosition nodeSourcePosition) {
    }

    /**
     * Returns the integer value of any constant that can be represented by a 32-bit integer value,
     * including long constants that fit into the 32-bit range.
     */
    public int asIntConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind().isNumericInteger();
        JavaConstant constant = asJavaConstant(value);
        long c = constant.asLong();
        if (!NumUtil.isInt(c)) {
            throw GraalError.shouldNotReachHere();
        }
        return (int) c;
    }

    /**
     * Returns the float value of any constant that can be represented by a 32-bit float value.
     */
    public float asFloatConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Float;
        JavaConstant constant = asJavaConstant(value);
        return constant.asFloat();
    }

    /**
     * Returns the long value of any constant that can be represented by a 64-bit long value.
     */
    public long asLongConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Long;
        JavaConstant constant = asJavaConstant(value);
        return constant.asLong();
    }

    /**
     * Returns the double value of any constant that can be represented by a 64-bit float value.
     */
    public double asDoubleConst(Value value) {
        assert isJavaConstant(value) && asJavaConstant(value).getJavaKind() == JavaKind.Double;
        JavaConstant constant = asJavaConstant(value);
        return constant.asDouble();
    }

    /**
     * Returns the address of a float constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asFloatConstRef(JavaConstant value) {
        return asFloatConstRef(value, 4);
    }

    public AbstractAddress asFloatConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Float;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a double constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asDoubleConstRef(JavaConstant value) {
        return asDoubleConstRef(value, 8);
    }

    public AbstractAddress asDoubleConstRef(JavaConstant value, int alignment) {
        assert value.getJavaKind() == JavaKind.Double;
        return recordDataReferenceInCode(value, alignment);
    }

    /**
     * Returns the address of a long constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asLongConstRef(JavaConstant value) {
        assert value.getJavaKind() == JavaKind.Long;
        return recordDataReferenceInCode(value, 8);
    }

    /**
     * Returns the address of an object constant that is embedded as a data reference into the code.
     */
    public AbstractAddress asObjectConstRef(JavaConstant value) {
        assert value.getJavaKind() == JavaKind.Object;
        return recordDataReferenceInCode(value, 8);
    }

    public AbstractAddress asByteAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Byte.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asShortAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Short.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asIntAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Int.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asLongAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Long.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asFloatAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Float.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asDoubleAddr(Value value) {
        assert value.getPlatformKind().getSizeInBytes() >= JavaKind.Double.getByteCount();
        return asAddress(value);
    }

    public AbstractAddress asAddress(Value value) {
        assert isStackSlot(value);
        StackSlot slot = asStackSlot(value);
        return asm.makeAddress(frameMap.getRegisterConfig().getFrameRegister(), frameMap.offsetForStackSlot(slot));
    }

    /**
     * Determines if a given edge from the block currently being emitted goes to its lexical
     * successor.
     */
    public boolean isSuccessorEdge(LabelRef edge) {
        assert lir != null;
        AbstractBlockBase<?>[] order = lir.codeEmittingOrder();
        assert order[currentBlockIndex] == edge.getSourceBlock();
        AbstractBlockBase<?> nextBlock = LIR.getNextBlock(order, currentBlockIndex);
        return nextBlock == edge.getTargetBlock();
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder() code emitting order}.
     */
    public void emit(@SuppressWarnings("hiding") LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);
        for (AbstractBlockBase<?> b : lir.codeEmittingOrder()) {
            assert (b == null && lir.codeEmittingOrder()[currentBlockIndex] == null) || lir.codeEmittingOrder()[currentBlockIndex].equals(b);
            emitBlock(b);
            currentBlockIndex++;
        }
        this.lir = null;
        this.currentBlockIndex = 0;
    }

    private void emitBlock(AbstractBlockBase<?> block) {
        if (block == null) {
            return;
        }
        boolean emitComment = debug.isDumpEnabled(DebugContext.BASIC_LEVEL) || Options.PrintLIRWithAssembly.getValue(getOptions());
        if (emitComment) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (emitComment) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                if (beforeOp != null) {
                    beforeOp.accept(op);
                }
                emitOp(this, op);
                if (afterOp != null) {
                    afterOp.accept(op);
                }
            } catch (GraalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op.getClass().getName() + " " + op + "\n" + Arrays.toString(lir.codeEmittingOrder()));
            }
        }
    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            int start = crb.asm.position();
            op.emitCode(crb);
            if (op.getPosition() != null) {
                crb.recordSourceMapping(start, crb.asm.position(), op.getPosition());
            }
        } catch (AssertionError t) {
            throw new GraalError(t);
        } catch (RuntimeException t) {
            throw new GraalError(t);
        }
    }

    public void resetForEmittingCode() {
        asm.reset();
        compilationResult.resetForEmittingCode();
        if (exceptionInfoList != null) {
            exceptionInfoList.clear();
        }
        if (dataCache != null) {
            dataCache.clear();
        }
    }

    public void setOpCallback(Consumer<LIRInstruction> beforeOp, Consumer<LIRInstruction> afterOp) {
        this.beforeOp = beforeOp;
        this.afterOp = afterOp;
    }

    public OptionValues getOptions() {
        return options;
    }

}
