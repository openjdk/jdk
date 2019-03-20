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


package org.graalvm.compiler.lir;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.OUTGOING;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.ArrayList;
import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterSaveLayout;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

/**
 * A collection of machine-independent LIR operations, as well as interfaces to be implemented for
 * specific kinds or LIR operations.
 */
public class StandardOp {

    /**
     * A block delimiter. Every well formed block must contain exactly one such operation and it
     * must be the last operation in the block.
     */
    public interface BlockEndOp {
    }

    public interface NullCheck {
        Value getCheckedValue();

        LIRFrameState getState();
    }

    public interface ImplicitNullCheck {
        boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit);
    }

    public interface LabelHoldingOp {
        Label getLabel();
    }

    /**
     * LIR operation that defines the position of a label.
     */
    public static final class LabelOp extends LIRInstruction implements LabelHoldingOp {
        public static final LIRInstructionClass<LabelOp> TYPE = LIRInstructionClass.create(LabelOp.class);
        public static final EnumSet<OperandFlag> incomingFlags = EnumSet.of(REG, STACK);

        /**
         * In the LIR, every register and variable must be defined before it is used. For method
         * parameters that are passed in fixed registers, exception objects passed to the exception
         * handler in a fixed register, or any other use of a fixed register not defined in this
         * method, an artificial definition is necessary. To avoid spill moves to be inserted
         * between the label at the beginning of a block an an actual definition in the second
         * instruction of a block, the registers are defined here in the label.
         */
        @Def({REG, STACK}) private Value[] incomingValues;
        private final Label label;
        private final boolean align;
        private int numbPhis;

        public LabelOp(Label label, boolean align) {
            super(TYPE);
            this.label = label;
            this.align = align;
            this.incomingValues = Value.NO_VALUES;
            this.numbPhis = 0;
        }

        public void setPhiValues(Value[] values) {
            setIncomingValues(values);
            setNumberOfPhis(values.length);
        }

        private void setNumberOfPhis(int numPhis) {
            assert numbPhis == 0;
            numbPhis = numPhis;
        }

        public int getPhiSize() {
            return numbPhis;
        }

        public void setIncomingValues(Value[] values) {
            assert this.incomingValues.length == 0;
            assert values != null;
            this.incomingValues = values;
        }

        public int getIncomingSize() {
            return incomingValues.length;
        }

        public Value getIncomingValue(int idx) {
            assert checkRange(idx);
            return incomingValues[idx];
        }

        public void clearIncomingValues() {
            incomingValues = Value.NO_VALUES;
        }

        public void addIncomingValues(Value[] values) {
            if (incomingValues.length == 0) {
                setIncomingValues(values);
                return;
            }
            int t = incomingValues.length + values.length;
            Value[] newArray = new Value[t];
            System.arraycopy(incomingValues, 0, newArray, 0, incomingValues.length);
            System.arraycopy(values, 0, newArray, incomingValues.length, values.length);
            incomingValues = newArray;
        }

        private boolean checkRange(int idx) {
            return idx < incomingValues.length;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (align) {
                crb.asm.align(crb.target.wordSize * 2);
            }
            crb.asm.bind(label);
        }

        @Override
        public Label getLabel() {
            return label;
        }

        /**
         * @return true if this label acts as a PhiIn.
         */
        public boolean isPhiIn() {
            return getPhiSize() > 0;
        }

        public void forEachIncomingValue(InstructionValueProcedure proc) {
            for (int i = 0; i < incomingValues.length; i++) {
                incomingValues[i] = proc.doValue(this, incomingValues[i], OperandMode.DEF, incomingFlags);
            }
        }
    }

    /**
     * LIR operation that is an unconditional jump to a {@link #destination()}.
     */
    public static class JumpOp extends LIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<JumpOp> TYPE = LIRInstructionClass.create(JumpOp.class);
        public static final EnumSet<OperandFlag> outgoingFlags = EnumSet.of(REG, STACK, CONST, OUTGOING);

        @Alive({REG, STACK, CONST, OUTGOING}) private Value[] outgoingValues;

        private final LabelRef destination;

        public JumpOp(LabelRef destination) {
            this(TYPE, destination);
        }

        protected JumpOp(LIRInstructionClass<? extends JumpOp> c, LabelRef destination) {
            super(c);
            this.destination = destination;
            this.outgoingValues = Value.NO_VALUES;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (!crb.isSuccessorEdge(destination)) {
                crb.asm.jmp(destination.label());
            }
        }

        public LabelRef destination() {
            return destination;
        }

        public void setPhiValues(Value[] values) {
            assert this.outgoingValues.length == 0;
            assert values != null;
            this.outgoingValues = values;
        }

        public int getPhiSize() {
            return outgoingValues.length;
        }

        public Value getOutgoingValue(int idx) {
            assert checkRange(idx);
            return outgoingValues[idx];
        }

        public void clearOutgoingValues() {
            outgoingValues = Value.NO_VALUES;
        }

        private boolean checkRange(int idx) {
            return idx < outgoingValues.length;
        }
    }

    /**
     * Marker interface for a LIR operation that is a conditional jump.
     */
    public interface BranchOp extends BlockEndOp {
    }

    /**
     * Marker interface for a LIR operation that moves a value to {@link #getResult()}.
     */
    public interface MoveOp {

        AllocatableValue getResult();

        // Checkstyle: stop
        static MoveOp asMoveOp(LIRInstruction op) {
            return (MoveOp) op;
        }
        // Checkstyle: resume

        static boolean isMoveOp(LIRInstruction op) {
            return op.isMoveOp();
        }
    }

    /**
     * Marker interface for a LIR operation that moves some non-constant value to another location.
     */
    public interface ValueMoveOp extends MoveOp {

        AllocatableValue getInput();

        // Checkstyle: stop
        static ValueMoveOp asValueMoveOp(LIRInstruction op) {
            return (ValueMoveOp) op;
        }
        // Checkstyle: resume

        static boolean isValueMoveOp(LIRInstruction op) {
            return op.isValueMoveOp();
        }
    }

    /**
     * Marker interface for a LIR operation that loads a {@link #getConstant()}.
     */
    public interface LoadConstantOp extends MoveOp {

        Constant getConstant();

        // Checkstyle: stop
        static LoadConstantOp asLoadConstantOp(LIRInstruction op) {
            return (LoadConstantOp) op;
        }
        // Checkstyle: resume

        static boolean isLoadConstantOp(LIRInstruction op) {
            return op.isLoadConstantOp();
        }
    }

    /**
     * An operation that saves registers to the stack. The set of saved registers can be
     * {@linkplain #remove(EconomicSet) pruned} and a mapping from registers to the frame slots in
     * which they are saved can be {@linkplain #getMap(FrameMap) retrieved}.
     */
    public interface SaveRegistersOp {

        /**
         * Determines if the {@link #remove(EconomicSet)} operation is supported for this object.
         */
        boolean supportsRemove();

        /**
         * Prunes {@code doNotSave} from the registers saved by this operation.
         *
         * @param doNotSave registers that should not be saved by this operation
         * @return the number of registers pruned
         * @throws UnsupportedOperationException if removal is not {@linkplain #supportsRemove()
         *             supported}
         */
        int remove(EconomicSet<Register> doNotSave);

        /**
         * Gets a map from the saved registers saved by this operation to the frame slots in which
         * they are saved.
         *
         * @param frameMap used to {@linkplain FrameMap#offsetForStackSlot(StackSlot) convert} a
         *            virtual slot to a frame slot index
         */
        RegisterSaveLayout getMap(FrameMap frameMap);

    }

    /**
     * A LIR operation that does nothing. If the operation records its position, it can be
     * subsequently {@linkplain #replace(LIR, LIRInstruction) replaced}.
     */
    public static final class NoOp extends LIRInstruction {
        public static final LIRInstructionClass<NoOp> TYPE = LIRInstructionClass.create(NoOp.class);

        /**
         * The block in which this instruction is located.
         */
        final AbstractBlockBase<?> block;

        /**
         * The block index of this instruction.
         */
        final int index;

        public NoOp(AbstractBlockBase<?> block, int index) {
            super(TYPE);
            this.block = block;
            this.index = index;
        }

        public void replace(LIR lir, LIRInstruction replacement) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.get(index).equals(this) : String.format("Replacing the wrong instruction: %s instead of %s", instructions.get(index), this);
            instructions.set(index, replacement);
        }

        public void remove(LIR lir) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.get(index).equals(this) : String.format("Removing the wrong instruction: %s instead of %s", instructions.get(index), this);
            instructions.remove(index);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (block != null) {
                throw new GraalError(this + " should have been replaced");
            }
        }
    }

    @Opcode("BLACKHOLE")
    public static final class BlackholeOp extends LIRInstruction {
        public static final LIRInstructionClass<BlackholeOp> TYPE = LIRInstructionClass.create(BlackholeOp.class);

        @Use({REG, STACK, CONST}) private Value value;

        public BlackholeOp(Value value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            // do nothing, just keep value alive until at least here
        }
    }

    public static final class BindToRegisterOp extends LIRInstruction {
        public static final LIRInstructionClass<BindToRegisterOp> TYPE = LIRInstructionClass.create(BindToRegisterOp.class);

        @Use({REG}) private Value value;

        public BindToRegisterOp(Value value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            // do nothing, just keep value alive until at least here
        }
    }

    @Opcode("SPILLREGISTERS")
    public static final class SpillRegistersOp extends LIRInstruction {
        public static final LIRInstructionClass<SpillRegistersOp> TYPE = LIRInstructionClass.create(SpillRegistersOp.class);

        public SpillRegistersOp() {
            super(TYPE);
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return true;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            // do nothing, just keep value alive until at least here
        }
    }

    public static final class StackMove extends LIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<StackMove> TYPE = LIRInstructionClass.create(StackMove.class);

        @Def({STACK, HINT}) protected AllocatableValue result;
        @Use({STACK}) protected AllocatableValue input;

        public StackMove(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            throw new GraalError(this + " should have been removed");
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

}
