/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.gen;

import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public interface LIRGeneratorTool extends DiagnosticLIRGeneratorTool, ValueKindFactory<LIRKind> {

    /**
     * Factory for creating moves.
     */
    interface MoveFactory {

        /**
         * Checks whether the supplied constant can be used without loading it into a register for
         * most operations, i.e., for commonly used arithmetic, logical, and comparison operations.
         *
         * @param c The constant to check.
         * @return True if the constant can be used directly, false if the constant needs to be in a
         *         register.
         */
        boolean canInlineConstant(Constant c);

        /**
         * @param constant The constant that might be moved to a stack slot.
         * @return {@code true} if constant to stack moves are supported for this constant.
         */
        boolean allowConstantToStackMove(Constant constant);

        LIRInstruction createMove(AllocatableValue result, Value input);

        LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input);

        LIRInstruction createLoad(AllocatableValue result, Constant input);

        LIRInstruction createStackLoad(AllocatableValue result, Constant input);
    }

    abstract class BlockScope implements AutoCloseable {

        public abstract AbstractBlockBase<?> getCurrentBlock();

        @Override
        public abstract void close();

    }

    ArithmeticLIRGeneratorTool getArithmetic();

    CodeGenProviders getProviders();

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    AbstractBlockBase<?> getCurrentBlock();

    LIRGenerationResult getResult();

    RegisterConfig getRegisterConfig();

    boolean hasBlockEnd(AbstractBlockBase<?> block);

    MoveFactory getMoveFactory();

    /**
     * Get a special {@link MoveFactory} for spill moves.
     *
     * The instructions returned by this factory must only depend on the input values. References to
     * values that require interaction with register allocation are strictly forbidden.
     */
    MoveFactory getSpillMoveFactory();

    BlockScope getBlockScope(AbstractBlockBase<?> block);

    Value emitConstant(LIRKind kind, Constant constant);

    Value emitJavaConstant(JavaConstant constant);

    /**
     * Some backends need to convert sub-word kinds to a larger kind in
     * {@link ArithmeticLIRGeneratorTool#emitLoad} and {@link #emitLoadConstant} because sub-word
     * registers can't be accessed. This method converts the {@link LIRKind} of a memory location or
     * constant to the {@link LIRKind} that will be used when it is loaded into a register.
     */
    <K extends ValueKind<K>> K toRegisterKind(K kind);

    AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant);

    void emitNullCheck(Value address, LIRFrameState state);

    Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue);

    Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue);

    /**
     * Emit an atomic read-and-add instruction.
     *
     * @param address address of the value to be read and written
     * @param valueKind the access kind for the value to be written
     * @param delta the value to be added
     */
    default Value emitAtomicReadAndAdd(Value address, ValueKind<?> valueKind, Value delta) {
        throw GraalError.unimplemented();
    }

    /**
     * Emit an atomic read-and-write instruction.
     *
     * @param address address of the value to be read and written
     * @param valueKind the access kind for the value to be written
     * @param newValue the new value to be written
     */
    default Value emitAtomicReadAndWrite(Value address, ValueKind<?> valueKind, Value newValue) {
        throw GraalError.unimplemented();
    }

    void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state);

    Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args);

    RegisterAttributes attributes(Register register);

    /**
     * Create a new {@link Variable}.
     *
     * @param kind The type of the value that will be stored in this {@link Variable}. See
     *            {@link LIRKind} for documentation on what to pass here. Note that in most cases,
     *            simply passing {@link Value#getValueKind()} is wrong.
     * @return A new {@link Variable}.
     */
    Variable newVariable(ValueKind<?> kind);

    Variable emitMove(Value input);

    void emitMove(AllocatableValue dst, Value src);

    void emitMoveConstant(AllocatableValue dst, Constant src);

    Variable emitAddress(AllocatableValue stackslot);

    void emitMembar(int barriers);

    void emitUnwind(Value operand);

    /**
     * Called just before register allocation is performed on the LIR owned by this generator.
     * Overriding implementations of this method must call the overridden method.
     */
    void beforeRegisterAllocation();

    void emitIncomingValues(Value[] params);

    /**
     * Emits a return instruction. Implementations need to insert a move if the input is not in the
     * correct location.
     */
    void emitReturn(JavaKind javaKind, Value input);

    AllocatableValue asAllocatable(Value value);

    Variable load(Value value);

    Value loadNonConst(Value value);

    /**
     * Determines if only oop maps are required for the code generated from the LIR.
     */
    boolean needOnlyOopMaps();

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     *
     * @param javaKind the {@link JavaKind} of value being returned
     * @param valueKind the backend type of the value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    AllocatableValue resultOperandFor(JavaKind javaKind, ValueKind<?> valueKind);

    <I extends LIRInstruction> I append(I op);

    void setSourcePosition(NodeSourcePosition position);

    void emitJump(LabelRef label);

    void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability);

    void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability);

    void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    void emitStrategySwitch(JavaConstant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value);

    void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget);

    Variable emitByteSwap(Value operand);

    @SuppressWarnings("unused")
    default Variable emitArrayCompareTo(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length1, Value length2) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture");
    }

    Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length, int constantLength, boolean directPointers);

    @SuppressWarnings("unused")
    default Variable emitArrayEquals(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length, int constantLength, boolean directPointers) {
        throw GraalError.unimplemented("Array.equals with different types substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayIndexOf(JavaKind kind, boolean findTwoConsecutive, Value sourcePointer, Value sourceCount, Value... searchValues) {
        throw GraalError.unimplemented("String.indexOf substitution is not implemented on this architecture");
    }

    /*
     * The routines emitStringLatin1Inflate/3 and emitStringUTF16Compress/3 models a simplified
     * version of
     *
     * emitStringLatin1Inflate(Value src, Value src_ndx, Value dst, Value dst_ndx, Value len) and
     * emitStringUTF16Compress(Value src, Value src_ndx, Value dst, Value dst_ndx, Value len)
     *
     * respectively, where we have hoisted the offset address computations in a method replacement
     * snippet.
     */
    @SuppressWarnings("unused")
    default void emitStringLatin1Inflate(Value src, Value dst, Value len) {
        throw GraalError.unimplemented("StringLatin1.inflate substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitStringUTF16Compress(Value src, Value dst, Value len) {
        throw GraalError.unimplemented("StringUTF16.compress substitution is not implemented on this architecture");
    }

    void emitBlackhole(Value operand);

    LIRKind getLIRKind(Stamp stamp);

    void emitPause();

    void emitPrefetchAllocate(Value address);

    Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    default void emitConvertNullToZero(AllocatableValue result, Value input) {
        emitMove(result, input);
    }

    default void emitConvertZeroToNull(AllocatableValue result, Value input) {
        emitMove(result, input);
    }

    /**
     * Emits an instruction that prevents speculative execution from proceeding: no instruction
     * after this fence will execute until all previous instructions have retired.
     */
    void emitSpeculationFence();

    default VirtualStackSlot allocateStackSlots(int slots, BitSet objects, List<VirtualStackSlot> outObjectStackSlots) {
        return getResult().getFrameMapBuilder().allocateStackSlots(slots, objects, outObjectStackSlots);
    }

    default Value emitReadCallerStackPointer(Stamp wordStamp) {
        /*
         * We do not know the frame size yet. So we load the address of the first spill slot
         * relative to the beginning of the frame, which is equivalent to the stack pointer of the
         * caller.
         */
        return emitAddress(StackSlot.get(getLIRKind(wordStamp), 0, true));
    }

    default Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        return emitMove(StackSlot.get(getLIRKind(wordStamp), -returnAddressSize, true));
    }
}
