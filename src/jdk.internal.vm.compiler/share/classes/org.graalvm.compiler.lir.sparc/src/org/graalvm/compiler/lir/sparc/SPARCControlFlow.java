/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.sparc;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARC.CPU;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CBCOND;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.FBPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.INSTRUCTION_SIZE;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm10;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm11;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm13;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm5;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Fcc0;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Icc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Always;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_Greater;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_GreaterOrEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_Less;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_LessOrEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_UnorderedGreaterOrEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_UnorderedOrEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_UnorderedOrGreater;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_UnorderedOrLess;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_UnorderedOrLessOrEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Greater;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.GreaterEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.GreaterEqualUnsigned;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.GreaterUnsigned;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Less;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.LessEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.LessEqualUnsigned;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.LessUnsigned;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.NotEqual;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.sparc.SPARCMove.const2reg;
import static org.graalvm.compiler.lir.sparc.SPARCOP3Op.emitOp3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Assembler.LabelHint;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.CC;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.CMOV;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.SwitchStrategy.BaseSwitchClosure;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC.CPUFeature;
import jdk.vm.ci.sparc.SPARCKind;

public class SPARCControlFlow {
    // This describes the maximum offset between the first emitted (load constant in to scratch,
    // if does not fit into simm5 of cbcond) instruction and the final branch instruction
    private static final int maximumSelfOffsetInstructions = 2;

    public static final class ReturnOp extends SPARCBlockEndOp {
        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass.create(ReturnOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            super(TYPE, SIZE);
            this.x = x;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitCodeHelper(crb, masm);
        }

        public static void emitCodeHelper(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            masm.ret();
            // On SPARC we always leave the frame (in the delay slot).
            crb.frameContext.leave(crb);
        }
    }

    public static final class CompareBranchOp extends SPARCBlockEndOp implements SPARCDelayedControlTransfer {
        public static final LIRInstructionClass<CompareBranchOp> TYPE = LIRInstructionClass.create(CompareBranchOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(3);
        static final EnumSet<SPARCKind> SUPPORTED_KINDS = EnumSet.of(XWORD, WORD);

        @Use({REG}) protected AllocatableValue x;
        @Use({REG, CONST}) protected Value y;
        private ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected LabelHint trueDestinationHint;
        protected final LabelRef falseDestination;
        protected LabelHint falseDestinationHint;
        protected final SPARCKind kind;
        protected final boolean unorderedIsTrue;
        private boolean emitted = false;
        private int delaySlotPosition = -1;
        private double trueDestinationProbability;

        public CompareBranchOp(AllocatableValue x, Value y, Condition condition, LabelRef trueDestination, LabelRef falseDestination, SPARCKind kind, boolean unorderedIsTrue,
                        double trueDestinationProbability) {
            super(TYPE, SIZE);
            assert x.getPlatformKind() == y.getPlatformKind() : String.format("PlatformKind of x must match PlatformKind of y. %s!=%s", x.getPlatformKind(), y.getPlatformKind());
            this.x = x;
            this.y = y;
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.unorderedIsTrue = unorderedIsTrue;
            this.trueDestinationProbability = trueDestinationProbability;
            conditionFlag = fromCondition(kind.isInteger(), condition, unorderedIsTrue);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (emitted) { // Only if delayed control transfer is used we must check this
                assert masm.position() - delaySlotPosition == 4 : "Only one instruction can be stuffed into the delay slot";
            }
            if (!emitted) {
                requestHints(masm);
                int targetPosition = getTargetPosition(masm);
                if (canUseShortBranch(crb, masm, targetPosition)) {
                    emitted = emitShortCompareBranch(crb, masm);
                }
                if (!emitted) { // No short compare/branch was used, so we go into fallback
                    emitted = emitLongCompareBranch(crb, masm, true);
                    emitted = true;
                }
            }
            assert emitted;
        }

        private boolean emitLongCompareBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm, boolean withDelayedNop) {
            emitOp3(masm, Subcc, x, y);
            return emitBranch(crb, masm, kind, conditionFlag, trueDestination, falseDestination, withDelayedNop, trueDestinationProbability);
        }

        private static int getTargetPosition(Assembler asm) {
            return asm.position() + maximumSelfOffsetInstructions * asm.target.wordSize;
        }

        @Override
        public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            requestHints(masm);
            // When we use short branches, no delay slot is available
            int targetPosition = getTargetPosition(masm);
            if (!canUseShortBranch(crb, masm, targetPosition)) {
                emitted = emitLongCompareBranch(crb, masm, false);
                if (emitted) {
                    delaySlotPosition = masm.position();
                }
            }
        }

        private void requestHints(SPARCMacroAssembler masm) {
            if (trueDestinationHint == null) {
                this.trueDestinationHint = masm.requestLabelHint(trueDestination.label());
            }
            if (falseDestinationHint == null) {
                this.falseDestinationHint = masm.requestLabelHint(falseDestination.label());
            }
        }

        /**
         * Tries to use the emit the compare/branch instruction.
         * <p>
         * CBcond has follwing limitations
         * <ul>
         * <li>Immediate field is only 5 bit and is on the right
         * <li>Jump offset is maximum of -+512 instruction
         *
         * <p>
         * We get from outside
         * <ul>
         * <li>at least one of trueDestination falseDestination is within reach of +-512
         * instructions
         * <li>two registers OR one register and a constant which fits simm13
         *
         * <p>
         * We do:
         * <ul>
         * <li>find out which target needs to be branched conditionally
         * <li>find out if fall-through is possible, if not, a unconditional branch is needed after
         * cbcond (needJump=true)
         * <li>if no fall through: we need to put the closer jump into the cbcond branch and the
         * farther into the jmp (unconditional branch)
         * <li>if constant on the left side, mirror to be on the right
         * <li>if constant on right does not fit into simm5, put it into a scratch register
         *
         * @param crb
         * @param masm
         * @return true if the branch could be emitted
         */
        private boolean emitShortCompareBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            boolean isLong = kind == SPARCKind.XWORD;
            ConditionFlag actualConditionFlag = conditionFlag;
            Label actualTrueTarget = trueDestination.label();
            Label actualFalseTarget = falseDestination.label();
            Label tmpTarget;
            boolean needJump;
            if (crb.isSuccessorEdge(trueDestination)) {
                actualConditionFlag = conditionFlag.negate();
                tmpTarget = actualTrueTarget;
                actualTrueTarget = actualFalseTarget;
                actualFalseTarget = tmpTarget;
                needJump = false;
            } else {
                needJump = !crb.isSuccessorEdge(falseDestination);
                int targetPosition = getTargetPosition(masm);
                if (needJump && !isShortBranch(masm, targetPosition, trueDestinationHint, actualTrueTarget)) {
                    // we have to jump in either way, so we must put the shorter
                    // branch into the actualTarget as only one of the two jump targets
                    // is guaranteed to be simm10
                    actualConditionFlag = actualConditionFlag.negate();
                    tmpTarget = actualTrueTarget;
                    actualTrueTarget = actualFalseTarget;
                    actualFalseTarget = tmpTarget;
                }
            }
            emitCBCond(masm, x, y, actualTrueTarget, actualConditionFlag, isLong);
            if (needJump) {
                masm.jmp(actualFalseTarget);
                masm.nop();
            }
            return true;
        }

        private static void emitCBCond(SPARCMacroAssembler masm, Value actualX, Value actualY, Label actualTrueTarget, ConditionFlag cFlag, boolean isLong) {
            PlatformKind xKind = actualX.getPlatformKind();
            Register rs1 = asRegister(actualX, xKind);
            if (isJavaConstant(actualY)) {
                JavaConstant c = asJavaConstant(actualY);
                long constantY = c.isNull() ? 0 : c.asLong();
                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    if (SPARCMacroAssembler.isSimm5(constantY)) {
                        CBCOND.emit(masm, cFlag, isLong, rs1, (int) constantY, actualTrueTarget);
                    } else { // !simm5
                        Register rs2 = scratch.getRegister();
                        masm.setx(constantY, rs2, false);
                        CBCOND.emit(masm, cFlag, isLong, rs1, rs2, actualTrueTarget);
                    }
                }
            } else {
                Register rs2 = asRegister(actualY, xKind);
                CBCOND.emit(masm, cFlag, isLong, rs1, rs2, actualTrueTarget);
            }
        }

        private boolean canUseShortBranch(CompilationResultBuilder crb, SPARCAssembler asm, int position) {
            if (!asm.hasFeature(CPUFeature.CBCOND)) {
                return false;
            }
            if (!((SPARCKind) x.getPlatformKind()).isInteger()) {
                return false;
            }
            // Do not use short branch, if the y value is a constant and does not fit into simm5 but
            // fits into simm13; this means the code with CBcond would be longer as the code without
            // CBcond.
            if (isJavaConstant(y) && !isSimm5(asJavaConstant(y)) && isSimm13(asJavaConstant(y))) {
                return false;
            }
            boolean hasShortJumpTarget = false;
            if (!crb.isSuccessorEdge(trueDestination)) {
                hasShortJumpTarget |= isShortBranch(asm, position, trueDestinationHint, trueDestination.label());
            }
            if (!crb.isSuccessorEdge(falseDestination)) {
                hasShortJumpTarget |= isShortBranch(asm, position, falseDestinationHint, falseDestination.label());
            }
            return hasShortJumpTarget;
        }

        @Override
        public void resetState() {
            emitted = false;
            delaySlotPosition = -1;
        }

        @Override
        public void verify() {
            super.verify();
            assert SUPPORTED_KINDS.contains(kind) : kind;
            assert !isConstantValue(x);
            assert x.getPlatformKind().equals(kind) && (isConstantValue(y) || y.getPlatformKind().equals(kind)) : x + " " + y;
        }
    }

    public static boolean isShortBranch(SPARCAssembler asm, int position, LabelHint hint, Label label) {
        int disp = 0;
        boolean dispValid = true;
        if (label.isBound()) {
            disp = label.position() - position;
        } else if (hint != null && hint.isValid()) {
            disp = hint.getTarget() - hint.getPosition();
        } else {
            dispValid = false;
        }
        if (dispValid) {
            if (disp < 0) {
                disp -= maximumSelfOffsetInstructions * asm.target.wordSize;
            } else {
                disp += maximumSelfOffsetInstructions * asm.target.wordSize;
            }
            return isSimm10(disp >> 2);
        } else if (hint == null) {
            asm.requestLabelHint(label);
        }
        return false;
    }

    public static final class BranchOp extends SPARCBlockEndOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BranchOp> TYPE = LIRInstructionClass.create(BranchOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);
        protected final ConditionFlag conditionFlag;
        protected final LabelRef trueDestination;
        protected final LabelRef falseDestination;
        protected final SPARCKind kind;
        protected final double trueDestinationProbability;

        public BranchOp(ConditionFlag conditionFlag, LabelRef trueDestination, LabelRef falseDestination, SPARCKind kind, double trueDestinationProbability) {
            super(TYPE, SIZE);
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.kind = kind;
            this.conditionFlag = conditionFlag;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitBranch(crb, masm, kind, conditionFlag, trueDestination, falseDestination, true, trueDestinationProbability);
        }
    }

    private static boolean emitBranch(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCKind kind, ConditionFlag conditionFlag, LabelRef trueDestination, LabelRef falseDestination,
                    boolean withDelayedNop, double trueDestinationProbability) {
        Label actualTarget;
        ConditionFlag actualConditionFlag;
        boolean needJump;
        BranchPredict predictTaken;
        if (falseDestination != null && crb.isSuccessorEdge(trueDestination)) {
            actualConditionFlag = conditionFlag != null ? conditionFlag.negate() : null;
            actualTarget = falseDestination.label();
            needJump = false;
            predictTaken = trueDestinationProbability < .5d ? PREDICT_TAKEN : PREDICT_NOT_TAKEN;
        } else {
            actualConditionFlag = conditionFlag;
            actualTarget = trueDestination.label();
            needJump = falseDestination != null && !crb.isSuccessorEdge(falseDestination);
            predictTaken = trueDestinationProbability > .5d ? PREDICT_TAKEN : PREDICT_NOT_TAKEN;
        }
        if (!withDelayedNop && needJump) {
            // We cannot make use of the delay slot when we jump in true-case and false-case
            return false;
        }
        if (kind.isFloat()) {
            FBPCC.emit(masm, Fcc0, actualConditionFlag, NOT_ANNUL, predictTaken, actualTarget);
        } else {
            assert kind.isInteger();
            CC cc = kind.equals(WORD) ? Icc : Xcc;
            BPCC.emit(masm, cc, actualConditionFlag, NOT_ANNUL, predictTaken, actualTarget);
        }
        if (withDelayedNop) {
            masm.nop();  // delay slot
        }
        if (needJump) {
            masm.jmp(falseDestination.label());
        }
        return true;
    }

    public static class StrategySwitchOp extends SPARCBlockEndOp {
        public static final LIRInstructionClass<StrategySwitchOp> TYPE = LIRInstructionClass.create(StrategySwitchOp.class);
        protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({REG}) protected Value key;
        @Alive({REG, ILLEGAL}) protected Value constantTableBase;
        @Temp({REG}) protected Value scratch;
        protected final SwitchStrategy strategy;
        private final EconomicMap<Label, LabelHint> labelHints;
        private final List<Label> conditionalLabels = new ArrayList<>();

        public StrategySwitchOp(Value constantTableBase, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, Variable scratch) {
            this(TYPE, constantTableBase, strategy, keyTargets, defaultTarget, key, scratch);
        }

        protected StrategySwitchOp(LIRInstructionClass<? extends StrategySwitchOp> c, Value constantTableBase, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget,
                        AllocatableValue key,
                        Variable scratch) {
            super(c);
            this.strategy = strategy;
            this.keyConstants = strategy.getKeyConstants();
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.constantTableBase = constantTableBase;
            this.key = key;
            this.scratch = scratch;
            this.labelHints = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
        }

        @Override
        public void emitCode(final CompilationResultBuilder crb, final SPARCMacroAssembler masm) {
            final Register keyRegister = asRegister(key);
            final Register constantBaseRegister = AllocatableValue.ILLEGAL.equals(constantTableBase) ? g0 : asRegister(constantTableBase);
            strategy.run(new SwitchClosure(keyRegister, constantBaseRegister, crb, masm));
        }

        public class SwitchClosure extends BaseSwitchClosure {
            private int conditionalLabelPointer = 0;

            protected final Register keyRegister;
            protected final Register constantBaseRegister;
            protected final CompilationResultBuilder crb;
            protected final SPARCMacroAssembler masm;

            protected SwitchClosure(Register keyRegister, Register constantBaseRegister, CompilationResultBuilder crb, SPARCMacroAssembler masm) {
                super(crb, masm, keyTargets, defaultTarget);
                this.keyRegister = keyRegister;
                this.constantBaseRegister = constantBaseRegister;
                this.crb = crb;
                this.masm = masm;
            }

            /**
             * This method caches the generated labels over two assembly passes to get information
             * about branch lengths.
             */
            @Override
            public Label conditionalJump(int index, Condition condition) {
                Label label;
                if (conditionalLabelPointer <= conditionalLabels.size()) {
                    label = new Label();
                    conditionalLabels.add(label);
                    conditionalLabelPointer = conditionalLabels.size();
                } else {
                    // TODO: (sa) We rely here on the order how the labels are generated during
                    // code generation; if the order is not stable ower two assembly passes, the
                    // result can be wrong
                    label = conditionalLabels.get(conditionalLabelPointer++);
                }
                conditionalJump(index, condition, label);
                return label;
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                JavaConstant constant = (JavaConstant) keyConstants[index];
                CC conditionCode;
                Long bits = constant.asLong();
                switch (constant.getJavaKind()) {
                    case Char:
                    case Byte:
                    case Short:
                    case Int:
                        conditionCode = CC.Icc;
                        break;
                    case Long:
                        conditionCode = CC.Xcc;
                        break;
                    default:
                        throw new GraalError("switch only supported for int, long and object");
                }
                ConditionFlag conditionFlag = fromCondition(keyRegister.getRegisterCategory().equals(CPU), condition, false);
                LabelHint hint = requestHint(masm, target);
                boolean isShortConstant = isSimm5(constant);
                int cbCondPosition = masm.position();
                if (!isShortConstant) { // Load constant takes one instruction
                    cbCondPosition += INSTRUCTION_SIZE;
                }
                boolean canUseShortBranch = masm.hasFeature(CPUFeature.CBCOND) && isShortBranch(masm, cbCondPosition, hint, target);
                if (bits != null && canUseShortBranch) {
                    if (isShortConstant) {
                        CBCOND.emit(masm, conditionFlag, conditionCode == Xcc, keyRegister, (int) (long) bits, target);
                    } else {
                        Register scratchRegister = asRegister(scratch);
                        const2reg(crb, masm, scratch, constantBaseRegister, (JavaConstant) keyConstants[index], SPARCDelayedControlTransfer.DUMMY);
                        CBCOND.emit(masm, conditionFlag, conditionCode == Xcc, keyRegister, scratchRegister, target);
                    }
                } else {
                    if (bits != null && isSimm13(constant)) {
                        masm.cmp(keyRegister, (int) (long) bits); // Cast is safe
                    } else {
                        Register scratchRegister = asRegister(scratch);
                        const2reg(crb, masm, scratch, constantBaseRegister, (JavaConstant) keyConstants[index], SPARCDelayedControlTransfer.DUMMY);
                        masm.cmp(keyRegister, scratchRegister);
                    }
                    BPCC.emit(masm, conditionCode, conditionFlag, ANNUL, PREDICT_TAKEN, target);
                    masm.nop();  // delay slot
                }
            }
        }

        protected LabelHint requestHint(SPARCMacroAssembler masm, Label label) {
            LabelHint hint = labelHints.get(label);
            if (hint == null) {
                hint = masm.requestLabelHint(label);
                labelHints.put(label, hint);
            }
            return hint;
        }

        protected int estimateEmbeddedSize(Constant c) {
            JavaConstant v = (JavaConstant) c;
            if (!SPARCAssembler.isSimm13(v)) {
                return v.getJavaKind().getByteCount();
            } else {
                return 0;
            }
        }

        @Override
        public SizeEstimate estimateSize() {
            int constantBytes = 0;
            for (Constant c : keyConstants) {
                constantBytes += estimateEmbeddedSize(c);
            }
            return new SizeEstimate(4 * keyTargets.length, constantBytes);
        }
    }

    public static final class TableSwitchOp extends SPARCBlockEndOp {
        public static final LIRInstructionClass<TableSwitchOp> TYPE = LIRInstructionClass.create(TableSwitchOp.class);

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive protected Value index;
        @Temp protected Value scratch;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Variable index, Variable scratch) {
            super(TYPE);
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Register value = asRegister(index, SPARCKind.WORD);
            Register scratchReg = asRegister(scratch, SPARCKind.XWORD);

            // Compare index against jump table bounds
            int highKey = lowKey + targets.length - 1;

            // subtract the low value from the switch value
            if (isSimm13(lowKey)) {
                masm.sub(value, lowKey, scratchReg);
            } else {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch2 = sc.getRegister();
                    masm.setx(lowKey, scratch2, false);
                    masm.sub(value, scratch2, scratchReg);
                }
            }
            int upperLimit = highKey - lowKey;
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch2 = sc.getRegister();
                if (isSimm13(upperLimit)) {
                    masm.cmp(scratchReg, upperLimit);
                } else {
                    masm.setx(upperLimit, scratch2, false);
                    masm.cmp(scratchReg, upperLimit);
                }

                // Jump to default target if index is not within the jump table
                if (defaultTarget != null) {
                    BPCC.emit(masm, Icc, GreaterUnsigned, NOT_ANNUL, PREDICT_TAKEN, defaultTarget.label());
                    masm.nop();  // delay slot
                }

                // Load jump table entry into scratch and jump to it
                masm.sll(scratchReg, 3, scratchReg); // Multiply by 8
                // Zero the left bits sll with shcnt>0 does not mask upper 32 bits
                masm.srl(scratchReg, 0, scratchReg);
                masm.rdpc(scratch2);

                // The jump table follows four instructions after rdpc
                masm.add(scratchReg, 4 * 4, scratchReg);
                masm.jmpl(scratch2, scratchReg, g0);
            }
            masm.nop();

            // Emit jump table entries
            for (LabelRef target : targets) {
                BPCC.emit(masm, Xcc, Always, NOT_ANNUL, PREDICT_TAKEN, target.label());
                masm.nop(); // delay slot
            }
        }

        @Override
        public SizeEstimate estimateSize() {
            return SizeEstimate.create(17 + targets.length * 2);
        }
    }

    @Opcode("CMOVE")
    public static final class CondMoveOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CondMoveOp> TYPE = LIRInstructionClass.create(CondMoveOp.class);

        @Def({REG, HINT}) protected Value result;
        @Use({REG, CONST}) protected Value trueValue;
        @Use({REG, CONST}) protected Value falseValue;

        private final ConditionFlag condition;
        private final CC cc;
        private final CMOV cmove;

        public CondMoveOp(CMOV cmove, CC cc, ConditionFlag condition, Value trueValue, Value falseValue, Value result) {
            super(TYPE);
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.cc = cc;
            this.cmove = cmove;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (result.equals(trueValue)) { // We have the true value in place, do he opposite
                cmove(masm, condition.negate(), falseValue);
            } else if (result.equals(falseValue)) {
                cmove(masm, condition, trueValue);
            } else { // We have to move one of the input values to the result
                ConditionFlag actualCondition = condition;
                Value actualTrueValue = trueValue;
                Value actualFalseValue = falseValue;
                if (isJavaConstant(falseValue) && isSimm11(asJavaConstant(falseValue))) {
                    actualCondition = condition.negate();
                    actualTrueValue = falseValue;
                    actualFalseValue = trueValue;
                }
                SPARCMove.move(crb, masm, result, actualFalseValue, SPARCDelayedControlTransfer.DUMMY);
                cmove(masm, actualCondition, actualTrueValue);
            }
        }

        private void cmove(SPARCMacroAssembler masm, ConditionFlag localCondition, Value value) {
            if (isConstantValue(value)) {
                cmove.emit(masm, localCondition, cc, asImmediate(asJavaConstant(value)), asRegister(result));
            } else {
                cmove.emit(masm, localCondition, cc, asRegister(value), asRegister(result));
            }
        }

        @Override
        public SizeEstimate estimateSize() {
            int constantSize = 0;
            if (isJavaConstant(trueValue) && !SPARCAssembler.isSimm13(asJavaConstant(trueValue))) {
                constantSize += trueValue.getPlatformKind().getSizeInBytes();
            }
            if (isJavaConstant(falseValue) && !SPARCAssembler.isSimm13(asJavaConstant(falseValue))) {
                constantSize += trueValue.getPlatformKind().getSizeInBytes();
            }
            return SizeEstimate.create(3, constantSize);
        }
    }

    public static ConditionFlag fromCondition(boolean integer, Condition cond, boolean unorderedIsTrue) {
        if (integer) {
            switch (cond) {
                case EQ:
                    return Equal;
                case NE:
                    return NotEqual;
                case BT:
                    return LessUnsigned;
                case LT:
                    return Less;
                case BE:
                    return LessEqualUnsigned;
                case LE:
                    return LessEqual;
                case AE:
                    return GreaterEqualUnsigned;
                case GE:
                    return GreaterEqual;
                case AT:
                    return GreaterUnsigned;
                case GT:
                    return Greater;
            }
            throw GraalError.shouldNotReachHere("Unimplemented for: " + cond);
        } else {
            switch (cond) {
                case EQ:
                    return unorderedIsTrue ? F_UnorderedOrEqual : F_Equal;
                case NE:
                    return ConditionFlag.F_NotEqual;
                case LT:
                    return unorderedIsTrue ? F_UnorderedOrLess : F_Less;
                case LE:
                    return unorderedIsTrue ? F_UnorderedOrLessOrEqual : F_LessOrEqual;
                case GE:
                    return unorderedIsTrue ? F_UnorderedGreaterOrEqual : F_GreaterOrEqual;
                case GT:
                    return unorderedIsTrue ? F_UnorderedOrGreater : F_Greater;
            }
            throw GraalError.shouldNotReachHere("Unkown condition: " + cond);
        }
    }
}
