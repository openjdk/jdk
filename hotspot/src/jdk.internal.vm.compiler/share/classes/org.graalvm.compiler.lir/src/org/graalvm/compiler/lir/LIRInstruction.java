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
package org.graalvm.compiler.lir;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.OUTGOING;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static org.graalvm.compiler.lir.LIRInstruction.OperandMode.ALIVE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandMode.DEF;
import static org.graalvm.compiler.lir.LIRInstruction.OperandMode.TEMP;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * The base class for an {@code LIRInstruction}.
 */
public abstract class LIRInstruction {
    /**
     * Constants denoting how a LIR instruction uses an operand.
     */
    public enum OperandMode {
        /**
         * The value must have been defined before. It is alive before the instruction until the
         * beginning of the instruction, but not necessarily throughout the instruction. A register
         * assigned to it can also be assigned to a {@link #TEMP} or {@link #DEF} operand. The value
         * can be used again after the instruction, so the instruction must not modify the register.
         */
        USE,

        /**
         * The value must have been defined before. It is alive before the instruction and
         * throughout the instruction. A register assigned to it cannot be assigned to a
         * {@link #TEMP} or {@link #DEF} operand. The value can be used again after the instruction,
         * so the instruction must not modify the register.
         */
        ALIVE,

        /**
         * The value must not have been defined before, and must not be used after the instruction.
         * The instruction can do whatever it wants with the register assigned to it (or not use it
         * at all).
         */
        TEMP,

        /**
         * The value must not have been defined before. The instruction has to assign a value to the
         * register. The value can (and most likely will) be used after the instruction.
         */
        DEF,
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Use {

        OperandFlag[] value() default OperandFlag.REG;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Alive {

        OperandFlag[] value() default OperandFlag.REG;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Temp {

        OperandFlag[] value() default OperandFlag.REG;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Def {

        OperandFlag[] value() default OperandFlag.REG;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface State {
    }

    /**
     * Flags for an operand.
     */
    public enum OperandFlag {
        /**
         * The value can be a {@link RegisterValue}.
         */
        REG,

        /**
         * The value can be a {@link StackSlot}.
         */
        STACK,

        /**
         * The value can be a {@link CompositeValue}.
         */
        COMPOSITE,

        /**
         * The value can be a {@link JavaConstant}.
         */
        CONST,

        /**
         * The value can be {@link Value#ILLEGAL}.
         */
        ILLEGAL,

        /**
         * The register allocator should try to assign a certain register to improve code quality.
         * Use {@link LIRInstruction#forEachRegisterHint} to access the register hints.
         */
        HINT,

        /**
         * The value can be uninitialized, e.g., a stack slot that has not written to before. This
         * is only used to avoid false positives in verification code.
         */
        UNINITIALIZED,

        /** Outgoing block value. */
        OUTGOING,
    }

    /**
     * For validity checking of the operand flags defined by instruction subclasses.
     */
    protected static final EnumMap<OperandMode, EnumSet<OperandFlag>> ALLOWED_FLAGS;

    static {
        ALLOWED_FLAGS = new EnumMap<>(OperandMode.class);
        ALLOWED_FLAGS.put(OperandMode.USE, EnumSet.of(REG, STACK, COMPOSITE, CONST, ILLEGAL, HINT, UNINITIALIZED));
        ALLOWED_FLAGS.put(ALIVE, EnumSet.of(REG, STACK, COMPOSITE, CONST, ILLEGAL, HINT, UNINITIALIZED, OUTGOING));
        ALLOWED_FLAGS.put(TEMP, EnumSet.of(REG, STACK, COMPOSITE, ILLEGAL, HINT));
        ALLOWED_FLAGS.put(DEF, EnumSet.of(REG, STACK, COMPOSITE, ILLEGAL, HINT));
    }

    /**
     * The flags of the base and index value of an address.
     */
    protected static final EnumSet<OperandFlag> ADDRESS_FLAGS = EnumSet.of(REG, ILLEGAL);

    private final LIRInstructionClass<?> instructionClass;

    /**
     * Instruction id for register allocation.
     */
    private int id;

    /**
     * The source position of the code that generated this instruction.
     */
    private NodeSourcePosition position;

    private static final DebugCounter LIR_NODE_COUNT = Debug.counter("LIRNodes");

    /**
     * Constructs a new LIR instruction.
     */
    public LIRInstruction(LIRInstructionClass<? extends LIRInstruction> c) {
        LIR_NODE_COUNT.increment();
        instructionClass = c;
        assert c.getClazz() == this.getClass();
        id = -1;
    }

    public abstract void emitCode(CompilationResultBuilder crb);

    public final int id() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

    public final NodeSourcePosition getPosition() {
        return position;
    }

    public final void setPosition(NodeSourcePosition position) {
        this.position = position;
    }

    public final String name() {
        return instructionClass.getOpcode(this);
    }

    public final boolean hasOperands() {
        return instructionClass.hasOperands() || hasState() || destroysCallerSavedRegisters();
    }

    public final boolean hasState() {
        return instructionClass.hasState(this);
    }

    public boolean destroysCallerSavedRegisters() {
        return false;
    }

    // InstructionValueProcedures
    public final void forEachInput(InstructionValueProcedure proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void forEachAlive(InstructionValueProcedure proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void forEachTemp(InstructionValueProcedure proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void forEachOutput(InstructionValueProcedure proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void forEachState(InstructionValueProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // ValueProcedures
    public final void forEachInput(ValueProcedure proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void forEachAlive(ValueProcedure proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void forEachTemp(ValueProcedure proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void forEachOutput(ValueProcedure proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void forEachState(ValueProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // States
    public final void forEachState(InstructionStateProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    public final void forEachState(StateProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // InstructionValueConsumers
    public final void visitEachInput(InstructionValueConsumer proc) {
        instructionClass.visitEachUse(this, proc);
    }

    public final void visitEachAlive(InstructionValueConsumer proc) {
        instructionClass.visitEachAlive(this, proc);
    }

    public final void visitEachTemp(InstructionValueConsumer proc) {
        instructionClass.visitEachTemp(this, proc);
    }

    public final void visitEachOutput(InstructionValueConsumer proc) {
        instructionClass.visitEachDef(this, proc);
    }

    public final void visitEachState(InstructionValueConsumer proc) {
        instructionClass.visitEachState(this, proc);
    }

    // ValueConsumers
    public final void visitEachInput(ValueConsumer proc) {
        instructionClass.visitEachUse(this, proc);
    }

    public final void visitEachAlive(ValueConsumer proc) {
        instructionClass.visitEachAlive(this, proc);
    }

    public final void visitEachTemp(ValueConsumer proc) {
        instructionClass.visitEachTemp(this, proc);
    }

    public final void visitEachOutput(ValueConsumer proc) {
        instructionClass.visitEachDef(this, proc);
    }

    public final void visitEachState(ValueConsumer proc) {
        instructionClass.visitEachState(this, proc);
    }

    @SuppressWarnings("unused")
    public final Value forEachRegisterHint(Value value, OperandMode mode, InstructionValueProcedure proc) {
        return instructionClass.forEachRegisterHint(this, mode, proc);
    }

    @SuppressWarnings("unused")
    public final Value forEachRegisterHint(Value value, OperandMode mode, ValueProcedure proc) {
        return instructionClass.forEachRegisterHint(this, mode, proc);
    }

    /**
     * Utility method to add stack arguments to a list of temporaries. Useful for modeling calling
     * conventions that kill outgoing argument space.
     *
     * @return additional temporaries
     */
    protected static Value[] addStackSlotsToTemporaries(Value[] parameters, Value[] temporaries) {
        int extraTemps = 0;
        for (Value p : parameters) {
            if (isStackSlot(p)) {
                extraTemps++;
            }
            assert !isVirtualStackSlot(p) : "only real stack slots in calling convention";
        }
        if (extraTemps != 0) {
            int index = temporaries.length;
            Value[] newTemporaries = Arrays.copyOf(temporaries, temporaries.length + extraTemps);
            for (Value p : parameters) {
                if (isStackSlot(p)) {
                    newTemporaries[index++] = p;
                }
            }
            return newTemporaries;
        }
        return temporaries;
    }

    public void verify() {
    }

    public final String toStringWithIdPrefix() {
        if (id != -1) {
            return String.format("%4d %s", id, toString());
        }
        return "     " + toString();
    }

    @Override
    public String toString() {
        return instructionClass.toString(this);
    }

    public LIRInstructionClass<?> getLIRInstructionClass() {
        return instructionClass;
    }
}
