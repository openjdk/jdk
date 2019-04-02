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


package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.function.Function;

import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ExtendType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult.JumpTable;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.SwitchStrategy.BaseSwitchClosure;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AArch64ControlFlow {
    public static final class ReturnOp extends AArch64BlockEndOp implements BlockEndOp {
        public static final LIRInstructionClass<ReturnOp> TYPE = LIRInstructionClass.create(ReturnOp.class);
        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            super(TYPE);
            this.x = x;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            crb.frameContext.leave(crb);
            masm.ret(lr);
        }
    }

    public abstract static class AbstractBranchOp extends AArch64BlockEndOp implements StandardOp.BranchOp {
        private final LabelRef trueDestination;
        private final LabelRef falseDestination;

        private final double trueDestinationProbability;

        private AbstractBranchOp(LIRInstructionClass<? extends AbstractBranchOp> c, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(c);
            this.trueDestination = trueDestination;
            this.falseDestination = falseDestination;
            this.trueDestinationProbability = trueDestinationProbability;
        }

        protected abstract void emitBranch(CompilationResultBuilder crb, AArch64MacroAssembler masm, LabelRef target, boolean negate);

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /*
             * Explanation: Depending on what the successor edge is, we can use the fall-through to
             * optimize the generated code. If neither is a successor edge, use the branch
             * probability to try to take the conditional jump as often as possible to avoid
             * executing two instructions instead of one.
             */
            if (crb.isSuccessorEdge(trueDestination)) {
                emitBranch(crb, masm, falseDestination, true);
            } else if (crb.isSuccessorEdge(falseDestination)) {
                emitBranch(crb, masm, trueDestination, false);
            } else if (trueDestinationProbability < 0.5) {
                emitBranch(crb, masm, falseDestination, true);
                masm.jmp(trueDestination.label());
            } else {
                emitBranch(crb, masm, trueDestination, false);
                masm.jmp(falseDestination.label());
            }
        }
    }

    public static class BranchOp extends AbstractBranchOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BranchOp> TYPE = LIRInstructionClass.create(BranchOp.class);

        private final AArch64Assembler.ConditionFlag condition;

        public BranchOp(AArch64Assembler.ConditionFlag condition, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, trueDestination, falseDestination, trueDestinationProbability);
            this.condition = condition;
        }

        @Override
        protected void emitBranch(CompilationResultBuilder crb, AArch64MacroAssembler masm, LabelRef target, boolean negate) {
            AArch64Assembler.ConditionFlag finalCond = negate ? condition.negate() : condition;
            masm.branchConditionally(finalCond, target.label());
        }
    }

    public static class CompareBranchZeroOp extends AbstractBranchOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<CompareBranchZeroOp> TYPE = LIRInstructionClass.create(CompareBranchZeroOp.class);

        @Use(REG) private AllocatableValue value;

        public CompareBranchZeroOp(AllocatableValue value, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
            super(TYPE, trueDestination, falseDestination, trueDestinationProbability);
            this.value = value;
        }

        @Override
        protected void emitBranch(CompilationResultBuilder crb, AArch64MacroAssembler masm, LabelRef target, boolean negate) {
            AArch64Kind kind = (AArch64Kind) this.value.getPlatformKind();
            assert kind.isInteger();
            int size = kind.getSizeInBytes() * Byte.SIZE;

            if (negate) {
                masm.cbnz(size, asRegister(this.value), target.label());
            } else {
                masm.cbz(size, asRegister(this.value), target.label());
            }
        }
    }

    public static class BitTestAndBranchOp extends AbstractBranchOp implements StandardOp.BranchOp {
        public static final LIRInstructionClass<BitTestAndBranchOp> TYPE = LIRInstructionClass.create(BitTestAndBranchOp.class);

        @Use protected AllocatableValue value;
        private final int index;

        public BitTestAndBranchOp(LabelRef trueDestination, LabelRef falseDestination, AllocatableValue value, double trueDestinationProbability, int index) {
            super(TYPE, trueDestination, falseDestination, trueDestinationProbability);
            this.value = value;
            this.index = index;
        }

        @Override
        protected void emitBranch(CompilationResultBuilder crb, AArch64MacroAssembler masm, LabelRef target, boolean negate) {
            ConditionFlag cond = negate ? ConditionFlag.NE : ConditionFlag.EQ;
            Label label = target.label();
            boolean isFarBranch;

            if (label.isBound()) {
                isFarBranch = NumUtil.isSignedNbit(18, masm.position() - label.position());
            } else {
                // Max range of tbz is +-2^13 instructions. We estimate that each LIR instruction
                // emits 2 AArch64 instructions on average. Thus we test for maximum 2^12 LIR
                // instruction offset.
                int maxLIRDistance = (1 << 12);
                isFarBranch = !crb.labelWithinRange(this, label, maxLIRDistance);
            }

            if (isFarBranch) {
                cond = cond.negate();
                label = new Label();
            }

            if (cond == ConditionFlag.EQ) {
                masm.tbz(asRegister(value), index, label);
            } else {
                masm.tbnz(asRegister(value), index, label);
            }

            if (isFarBranch) {
                masm.jmp(target.label());
                masm.bind(label);
            }
        }
    }

    @Opcode("CMOVE")
    public static class CondMoveOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CondMoveOp> TYPE = LIRInstructionClass.create(CondMoveOp.class);

        @Def protected Value result;
        @Use protected Value trueValue;
        @Use protected Value falseValue;
        private final AArch64Assembler.ConditionFlag condition;

        public CondMoveOp(Variable result, AArch64Assembler.ConditionFlag condition, Value trueValue, Value falseValue) {
            super(TYPE);
            assert trueValue.getPlatformKind() == falseValue.getPlatformKind() && trueValue.getPlatformKind() == result.getPlatformKind();
            this.result = result;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind kind = (AArch64Kind) trueValue.getPlatformKind();
            int size = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.cmov(size, asRegister(result), asRegister(trueValue), asRegister(falseValue), condition);
            } else {
                masm.fcmov(size, asRegister(result), asRegister(trueValue), asRegister(falseValue), condition);
            }
        }
    }

    public static class CondSetOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CondSetOp> TYPE = LIRInstructionClass.create(CondSetOp.class);

        @Def protected Value result;
        private final AArch64Assembler.ConditionFlag condition;

        public CondSetOp(Variable result, AArch64Assembler.ConditionFlag condition) {
            super(TYPE);
            this.result = result;
            this.condition = condition;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            masm.cset(size, asRegister(result), condition);
        }
    }

    public static class StrategySwitchOp extends AArch64BlockEndOp implements StandardOp.BlockEndOp {
        public static final LIRInstructionClass<StrategySwitchOp> TYPE = LIRInstructionClass.create(StrategySwitchOp.class);

        private final Constant[] keyConstants;
        protected final SwitchStrategy strategy;
        private final Function<Condition, ConditionFlag> converter;
        private final LabelRef[] keyTargets;
        private final LabelRef defaultTarget;
        @Alive protected Value key;
        // TODO (das) This could be optimized: We only need the scratch register in case of a
        // datapatch, or too large immediates.
        @Temp protected Value scratch;

        public StrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch,
                        Function<Condition, ConditionFlag> converter) {
            this(TYPE, strategy, keyTargets, defaultTarget, key, scratch, converter);
        }

        protected StrategySwitchOp(LIRInstructionClass<? extends StrategySwitchOp> c, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch,
                        Function<Condition, ConditionFlag> converter) {
            super(c);
            this.strategy = strategy;
            this.converter = converter;
            this.keyConstants = strategy.getKeyConstants();
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
            assert keyConstants.length == keyTargets.length;
            assert keyConstants.length == strategy.keyProbabilities.length;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            strategy.run(new SwitchClosure(asRegister(key), crb, masm));
        }

        public class SwitchClosure extends BaseSwitchClosure {

            protected final Register keyRegister;
            protected final CompilationResultBuilder crb;
            protected final AArch64MacroAssembler masm;

            protected SwitchClosure(Register keyRegister, CompilationResultBuilder crb, AArch64MacroAssembler masm) {
                super(crb, masm, keyTargets, defaultTarget);
                this.keyRegister = keyRegister;
                this.crb = crb;
                this.masm = masm;
            }

            protected void emitComparison(Constant c) {
                JavaConstant jc = (JavaConstant) c;
                ConstantValue constVal = new ConstantValue(LIRKind.value(key.getPlatformKind()), c);
                switch (jc.getJavaKind()) {
                    case Int:
                        long lc = jc.asLong();
                        assert NumUtil.isInt(lc);
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    case Long:
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    case Object:
                        emitCompare(crb, masm, key, scratch, constVal);
                        break;
                    default:
                        throw new GraalError("switch only supported for int, long and object");
                }
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                emitComparison(keyConstants[index]);
                masm.branchConditionally(converter.apply(condition), target);
            }
        }
    }

    public static final class TableSwitchOp extends AArch64BlockEndOp {
        public static final LIRInstructionClass<TableSwitchOp> TYPE = LIRInstructionClass.create(TableSwitchOp.class);
        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Use protected Value index;
        @Temp({REG, HINT}) protected Value idxScratch;
        @Temp protected Value scratch;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, Value index, Variable scratch, Variable idxScratch) {
            super(TYPE);
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
            this.idxScratch = idxScratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register indexReg = asRegister(index, AArch64Kind.DWORD);
            Register idxScratchReg = asRegister(idxScratch, AArch64Kind.DWORD);
            Register scratchReg = asRegister(scratch, AArch64Kind.QWORD);

            // Compare index against jump table bounds
            int highKey = lowKey + targets.length - 1;
            masm.sub(32, idxScratchReg, indexReg, lowKey);
            masm.cmp(32, idxScratchReg, highKey - lowKey);

            // Jump to default target if index is not within the jump table
            if (defaultTarget != null) {
                masm.branchConditionally(ConditionFlag.HI, defaultTarget.label());
            }

            Label jumpTable = new Label();
            masm.adr(scratchReg, jumpTable);
            masm.add(64, scratchReg, scratchReg, idxScratchReg, ExtendType.UXTW, 2);
            masm.jmp(scratchReg);
            masm.bind(jumpTable);
            // emit jump table entries
            for (LabelRef target : targets) {
                masm.jmp(target.label());
            }
            JumpTable jt = new JumpTable(jumpTable.position(), lowKey, highKey - 1, 4);
            crb.compilationResult.addAnnotation(jt);
        }
    }

    private static void emitCompare(CompilationResultBuilder crb, AArch64MacroAssembler masm, Value key, Value scratchValue, ConstantValue c) {
        long imm = c.getJavaConstant().asLong();
        final int size = key.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        if (AArch64MacroAssembler.isComparisonImmediate(imm)) {
            masm.cmp(size, asRegister(key), (int) imm);
        } else {
            AArch64Move.move(crb, masm, asAllocatableValue(scratchValue), c);
            masm.cmp(size, asRegister(key), asRegister(scratchValue));
        }
    }

}
