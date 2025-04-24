/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ConstantDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

public abstract sealed class AbstractInstruction
        extends AbstractElement
        implements Instruction {

    private static final String
            FMT_ArgumentConstant = "ArgumentConstant[OP=%s, val=%s]",
            FMT_Branch = "Branch[OP=%s]",
            FMT_Field = "Field[OP=%s, field=%s.%s:%s]",
            FMT_Increment = "Increment[OP=%s, slot=%d, val=%d]",
            FMT_Invoke = "Invoke[OP=%s, m=%s.%s%s]",
            FMT_InvokeDynamic = "InvokeDynamic[OP=%s, bsm=%s %s]",
            FMT_InvokeInterface = "InvokeInterface[OP=%s, m=%s.%s%s]",
            FMT_Load = "Load[OP=%s, slot=%d]",
            FMT_LoadConstant = "LoadConstant[OP=%s, val=%s]",
            FMT_LookupSwitch = "LookupSwitch[OP=%s]",
            FMT_NewMultiArray = "NewMultiArray[OP=%s, type=%s[%d]]",
            FMT_NewObj = "NewObj[OP=%s, type=%s]",
            FMT_NewPrimitiveArray = "NewPrimitiveArray[OP=%s, type=%s]",
            FMT_NewRefArray = "NewRefArray[OP=%s, type=%s]",
            FMT_Return = "Return[OP=%s]",
            FMT_Store = "Store[OP=%s, slot=%d]",
            FMT_TableSwitch = "TableSwitch[OP=%s]",
            FMT_Throw = "Throw[OP=%s]",
            FMT_TypeCheck = "TypeCheck[OP=%s, type=%s]",
            FMT_Unbound = "%s[op=%s]",
            FMT_Discontinued = "Discontinued[OP=%s]";

    final Opcode op;

    @Override
    public Opcode opcode() {
        return op;
    }

    @Override
    public int sizeInBytes() {
        // Note: only lookupswitch and tableswitch have variable sizes
        return op.sizeIfFixed();
    }

    AbstractInstruction(Opcode op) {
        this.op = op;
    }

    @Override
    public abstract void writeTo(DirectCodeBuilder writer);

    public abstract static sealed class BoundInstruction extends AbstractInstruction {
        final CodeImpl code;
        final int pos;

        protected BoundInstruction(Opcode op, CodeImpl code, int pos) {
            super(op);
            this.code = code;
            this.pos = pos;
        }

        protected Label offsetToLabel(int offset) {
            return code.getLabel(pos - code.codeStart + offset);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            // Override this if the instruction has any CP references or labels!
            code.classReader.copyBytesTo(writer.bytecodesBufWriter, pos, op.sizeIfFixed());
        }
    }

    public static final class BoundLoadInstruction
            extends BoundInstruction implements LoadInstruction {

        public BoundLoadInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }


        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.loadType(op);
        }

        @Override
        public String toString() {
            return String.format(FMT_Load, this.opcode(), slot());
        }

        @Override
        public int slot() {
            return switch (sizeInBytes()) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + op.sizeIfFixed() + " -- " + op);
            };
        }

    }

    public static final class BoundStoreInstruction
            extends BoundInstruction implements StoreInstruction {

        public BoundStoreInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.storeType(op);
        }

        @Override
        public String toString() {
            return String.format(FMT_Store, this.opcode(), slot());
        }

        @Override
        public int slot() {
            return switch (sizeInBytes()) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + sizeInBytes() + " -- " + op);
            };
        }

    }

    public static final class BoundIncrementInstruction
            extends BoundInstruction implements IncrementInstruction {

        public BoundIncrementInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public int slot() {
            return sizeInBytes() == 6 ? code.classReader.readU2(pos + 2) : code.classReader.readU1(pos + 1);
        }

        @Override
        public int constant() {
            return sizeInBytes() == 6 ? code.classReader.readS2(pos + 4) : code.classReader.readS1(pos + 2);
        }

        @Override
        public String toString() {
            return String.format(FMT_Increment, this.opcode(), slot(), constant());
        }

    }

    public static final class BoundBranchInstruction
            extends BoundInstruction implements BranchInstruction {

        public BoundBranchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public Label target() {
            return offsetToLabel(branchByteOffset());
        }

        public int branchByteOffset() {
            return sizeInBytes() == 3
                   ? code.classReader.readS2(pos + 1)
                   : code.classReader.readInt(pos + 1);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeBranch(opcode(), target());
        }

        @Override
        public String toString() {
            return String.format(FMT_Branch, this.opcode());
        }

    }

    public record SwitchCaseImpl(int caseValue, Label target)
            implements SwitchCase {
        public SwitchCaseImpl {
            requireNonNull(target);
        }
    }

    public static final class BoundLookupSwitchInstruction
            extends BoundInstruction implements LookupSwitchInstruction {

        // will always need size, cache everything to there
        private final int afterPad;
        private final int npairs;
        private final int size;

        BoundLookupSwitchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
            this.afterPad = code.codeStart + RawBytecodeHelper.align(pos + 1 - code.codeStart);
            this.npairs = code.classReader.readInt(afterPad + 4);
            if (npairs < 0 || npairs > code.codeLength >> 3) {
                throw new IllegalArgumentException("Invalid lookupswitch npairs value: " + npairs);
            }
            this.size = afterPad + 8 + npairs * 8 - pos;
        }

        private int defaultOffset() {
            return code.classReader.readInt(afterPad);
        }

        @Override
        public int sizeInBytes() {
            return size;
        }

        @Override
        public List<SwitchCase> cases() {
            var cases = new SwitchCase[npairs];
            for (int i = 0, z = afterPad + 8; i < npairs; ++i, z += 8) {
                cases[i] = SwitchCase.of(code.classReader.readInt(z), offsetToLabel(code.classReader.readInt(z + 4)));
            }
            return List.of(cases);
        }

        @Override
        public Label defaultTarget() {
            return offsetToLabel(defaultOffset());
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeLookupSwitch(defaultTarget(), cases());
        }

        @Override
        public String toString() {
            return String.format(FMT_LookupSwitch, this.opcode());
        }

    }

    public static final class BoundTableSwitchInstruction
            extends BoundInstruction implements TableSwitchInstruction {

        private final int afterPad;
        private final int low;
        private final int high;
        private final int size;

        BoundTableSwitchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
            afterPad = code.codeStart + RawBytecodeHelper.align(pos + 1 - code.codeStart);
            low = code.classReader.readInt(afterPad + 4);
            high = code.classReader.readInt(afterPad + 8);
            if (high < low || (long)high - low > code.codeLength >> 2) {
                throw new IllegalArgumentException("Invalid tableswitch values low: " + low + " high: " + high);
            }
            int cnt = high - low + 1;
            size = afterPad + 12 + cnt * 4 - pos;
        }

        @Override
        public int sizeInBytes() {
            return size;
        }

        @Override
        public Label defaultTarget() {
            return offsetToLabel(defaultOffset());
        }

        @Override
        public int lowValue() {
            return low;
        }

        @Override
        public int highValue() {
            return high;
        }

        @Override
        public List<SwitchCase> cases() {
            int low = lowValue();
            int high = highValue();
            int defOff = defaultOffset();
            var cases = new ArrayList<SwitchCase>(high - low + 1);
            for (int i = low, z = afterPad + 12; i <= high; ++i, z += 4) {
                int off = code.classReader.readInt(z);
                if (defOff != off) {
                    cases.add(SwitchCase.of(i, offsetToLabel(off)));
                }
            }
            return Collections.unmodifiableList(cases);
        }

        private int defaultOffset() {
            return code.classReader.readInt(afterPad);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeTableSwitch(lowValue(), highValue(), defaultTarget(), cases());
        }

        @Override
        public String toString() {
            return String.format(FMT_TableSwitch, this.opcode());
        }

    }

    public static final class BoundFieldInstruction
            extends BoundInstruction implements FieldInstruction {

        private FieldRefEntry fieldEntry;

        public BoundFieldInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public FieldRefEntry field() {
            if (fieldEntry == null)
                fieldEntry = code.classReader.readEntry(pos + 1, FieldRefEntry.class);
            return fieldEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeFieldAccess(op, field());
        }

        @Override
        public String toString() {
            return String.format(FMT_Field, this.opcode(), owner().asInternalName(), name().stringValue(), type().stringValue());
        }

    }

    public static final class BoundInvokeInstruction
            extends BoundInstruction implements InvokeInstruction {
        MemberRefEntry methodEntry;

        public BoundInvokeInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public MemberRefEntry method() {
            if (methodEntry == null)
                methodEntry = code.classReader.readEntry(pos + 1, MemberRefEntry.class);
            return methodEntry;
        }

        @Override
        public boolean isInterface() {
            return method().tag() == PoolEntry.TAG_INTERFACE_METHODREF;
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeInvokeNormal(op, method());
        }

        @Override
        public String toString() {
            return String.format(FMT_Invoke, this.opcode(), owner().asInternalName(), name().stringValue(), type().stringValue());
        }

    }

    public static final class BoundInvokeInterfaceInstruction
            extends BoundInstruction implements InvokeInstruction {
        InterfaceMethodRefEntry methodEntry;

        public BoundInvokeInterfaceInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public MemberRefEntry method() {
            if (methodEntry == null)
                methodEntry = code.classReader.readEntry(pos + 1, InterfaceMethodRefEntry.class);
            return methodEntry;
        }

        @Override
        public int count() {
            return code.classReader.readU1(pos + 3);
        }

        @Override
        public boolean isInterface() {
            return true;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeInvokeInterface(op, (InterfaceMethodRefEntry) method(), count());
        }

        @Override
        public String toString() {
            return String.format(FMT_InvokeInterface, this.opcode(), owner().asInternalName(), name().stringValue(), type().stringValue());
        }

    }

    public static final class BoundInvokeDynamicInstruction
            extends BoundInstruction implements InvokeDynamicInstruction {
        InvokeDynamicEntry indyEntry;

        BoundInvokeDynamicInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public InvokeDynamicEntry invokedynamic() {
            if (indyEntry == null)
                indyEntry = code.classReader.readEntry(pos + 1, InvokeDynamicEntry.class);
            return indyEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeInvokeDynamic(invokedynamic());
        }

        @Override
        public String toString() {
            return String.format(FMT_InvokeDynamic, this.opcode(), bootstrapMethod(), bootstrapArgs());
        }

    }

    public static final class BoundNewObjectInstruction
            extends BoundInstruction implements NewObjectInstruction {
        ClassEntry classEntry;

        BoundNewObjectInstruction(CodeImpl code, int pos) {
            super(Opcode.NEW, code, pos);
        }

        @Override
        public ClassEntry className() {
            if (classEntry == null)
                classEntry = code.classReader.readEntry(pos + 1, ClassEntry.class);
            return classEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeNewObject(className());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewObj, this.opcode(), className().asInternalName());
        }

    }

    public static final class BoundNewPrimitiveArrayInstruction
            extends BoundInstruction implements NewPrimitiveArrayInstruction {

        public BoundNewPrimitiveArrayInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public TypeKind typeKind() {
            return TypeKind.fromNewarrayCode(code.classReader.readU1(pos + 1));
        }

        @Override
        public String toString() {
            return String.format(FMT_NewPrimitiveArray, this.opcode(), typeKind());
        }

    }

    public static final class BoundNewReferenceArrayInstruction
            extends BoundInstruction implements NewReferenceArrayInstruction {

        public BoundNewReferenceArrayInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public ClassEntry componentType() {
            return code.classReader.readEntry(pos + 1, ClassEntry.class);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeNewReferenceArray(componentType());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewRefArray, this.opcode(), componentType().asInternalName());
        }
    }

    public static final class BoundNewMultidimensionalArrayInstruction
            extends BoundInstruction implements NewMultiArrayInstruction {

        public BoundNewMultidimensionalArrayInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public int dimensions() {
            return code.classReader.readU1(pos + 3);
        }

        @Override
        public ClassEntry arrayType() {
            return code.classReader.readEntry(pos + 1, ClassEntry.class);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeNewMultidimensionalArray(dimensions(), arrayType());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewMultiArray, this.opcode(), arrayType().asInternalName(), dimensions());
        }

    }

    public static final class BoundTypeCheckInstruction
            extends BoundInstruction implements TypeCheckInstruction {
        ClassEntry typeEntry;

        public BoundTypeCheckInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public ClassEntry type() {
            if (typeEntry == null)
                typeEntry = code.classReader.readEntry(pos + 1, ClassEntry.class);
            return typeEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                writer.writeTypeCheck(op, type());
        }

        @Override
        public String toString() {
            return String.format(FMT_TypeCheck, this.opcode(), type().asInternalName());
        }

    }

    public static final class BoundArgumentConstantInstruction
            extends BoundInstruction implements ConstantInstruction.ArgumentConstantInstruction {

        public BoundArgumentConstantInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public Integer constantValue() {
            return constantInt();
        }

        public int constantInt() {
            return sizeInBytes() == 3 ? code.classReader.readS2(pos + 1) : code.classReader.readS1(pos + 1);
        }

        @Override
        public String toString() {
            return String.format(FMT_ArgumentConstant, this.opcode(), constantValue());
        }

    }

    public static final class BoundLoadConstantInstruction
            extends BoundInstruction implements ConstantInstruction.LoadConstantInstruction {

        public BoundLoadConstantInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public LoadableConstantEntry constantEntry() {
            return code.classReader.entryByIndex(op == Opcode.LDC
                                                  ? code.classReader.readU1(pos + 1)
                                                  : code.classReader.readU2(pos + 1),
                            LoadableConstantEntry.class);
        }

        @Override
        public ConstantDesc constantValue() {
            return constantEntry().constantValue();
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (writer.canWriteDirect(code.constantPool()))
                super.writeTo(writer);
            else
                // We have writer.canWriteDirect(constantEntry().constantPool()) == false
                writer.writeAdaptLoadConstant(op, constantEntry());
        }

        @Override
        public String toString() {
            return String.format(FMT_LoadConstant, this.opcode(), constantValue());
        }

    }

    public static final class BoundJsrInstruction
            extends BoundInstruction implements DiscontinuedInstruction.JsrInstruction {

        public BoundJsrInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public Label target() {
            return offsetToLabel(branchByteOffset());
        }

        public int branchByteOffset() {
            return sizeInBytes() == 3
                   ? code.classReader.readS2(pos + 1)
                   : code.classReader.readInt(pos + 1);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeBranch(opcode(), target());
        }

        @Override
        public String toString() {
            return String.format(FMT_Discontinued, this.opcode());
        }

    }

    public static final class BoundRetInstruction
            extends BoundInstruction implements DiscontinuedInstruction.RetInstruction {

        public BoundRetInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, code, pos);
        }

        @Override
        public String toString() {
            return String.format(FMT_Discontinued, this.opcode());
        }

        @Override
        public int slot() {
            return switch (sizeInBytes()) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + op.sizeIfFixed() + " -- " + op);
            };
        }

    }

    public abstract static sealed class UnboundInstruction extends AbstractInstruction {

        UnboundInstruction(Opcode op) {
            super(op);
        }

        @Override
        // Override this if there is anything more that just the bytecode
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeBytecode(op);
        }

        @Override
        public String toString() {
            return String.format(FMT_Unbound, this.getClass().getSimpleName(), op);
        }
    }

    public static final class UnboundLoadInstruction
            extends UnboundInstruction implements LoadInstruction {
        final int slot;

        public UnboundLoadInstruction(Opcode op, int slot) {
            super(op);
            this.slot = slot;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.loadType(op);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            var op = this.op;
            if (op.sizeIfFixed() == 1) {
                writer.writeBytecode(op);
            } else {
                writer.writeLocalVar(op, slot);
            }
        }

        @Override
        public String toString() {
            return String.format(FMT_Load, this.opcode(), slot());
        }

    }

    public static final class UnboundStoreInstruction
            extends UnboundInstruction implements StoreInstruction {
        final int slot;

        public UnboundStoreInstruction(Opcode op, int slot) {
            super(op);
            this.slot = slot;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.storeType(op);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            var op = this.op;
            if (op.sizeIfFixed() == 1) {
                writer.writeBytecode(op);
            } else {
                writer.writeLocalVar(op, slot);
            }
        }

        @Override
        public String toString() {
            return String.format(FMT_Store, this.opcode(), slot());
        }

    }

    public static final class UnboundIncrementInstruction
            extends UnboundInstruction implements IncrementInstruction {
        final int slot;
        final int constant;

        public UnboundIncrementInstruction(int slot, int constant) {
            super(BytecodeHelpers.validateAndIsWideIinc(slot, constant)
                  ? Opcode.IINC_W
                  : Opcode.IINC);
            this.slot = slot;
            this.constant = constant;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public int constant() {
            return constant;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeIncrement(op == Opcode.IINC_W, slot, constant);
        }

        @Override
        public String toString() {
            return String.format(FMT_Increment, this.opcode(), slot(), constant());
        }
    }

    public static final class UnboundBranchInstruction
            extends UnboundInstruction implements BranchInstruction {
        final Label target;

        public UnboundBranchInstruction(Opcode op, Label target) {
            super(op);
            this.target = requireNonNull(target);
        }

        @Override
        public Label target() {
            return target;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeBranch(op, target);
        }

        @Override
        public String toString() {
            return String.format(FMT_Branch, this.opcode());
        }
    }

    public static final class UnboundLookupSwitchInstruction
            extends UnboundInstruction implements LookupSwitchInstruction {

        private final Label defaultTarget;
        private final List<SwitchCase> cases;

        public UnboundLookupSwitchInstruction(Label defaultTarget, List<SwitchCase> cases) {
            super(Opcode.LOOKUPSWITCH);
            this.defaultTarget = requireNonNull(defaultTarget);
            this.cases = List.copyOf(cases);
        }

        @Override
        public List<SwitchCase> cases() {
            return cases;
        }

        @Override
        public Label defaultTarget() {
            return defaultTarget;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeLookupSwitch(defaultTarget, cases);
        }

        @Override
        public String toString() {
            return String.format(FMT_LookupSwitch, this.opcode());
        }
    }

    public static final class UnboundTableSwitchInstruction
            extends UnboundInstruction implements TableSwitchInstruction {

        private final int lowValue, highValue;
        private final Label defaultTarget;
        private final List<SwitchCase> cases;

        public UnboundTableSwitchInstruction(int lowValue, int highValue, Label defaultTarget, List<SwitchCase> cases) {
            super(Opcode.TABLESWITCH);
            this.lowValue = lowValue;
            this.highValue = highValue;
            this.defaultTarget = requireNonNull(defaultTarget);
            this.cases = List.copyOf(cases);
        }

        @Override
        public int lowValue() {
            return lowValue;
        }

        @Override
        public int highValue() {
            return highValue;
        }

        @Override
        public Label defaultTarget() {
            return defaultTarget;
        }

        @Override
        public List<SwitchCase> cases() {
            return cases;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeTableSwitch(lowValue, highValue, defaultTarget, cases);
        }

        @Override
        public String toString() {
            return String.format(FMT_TableSwitch, this.opcode());
        }
    }

    public static final class UnboundReturnInstruction
            extends UnboundInstruction implements ReturnInstruction {

        public UnboundReturnInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.returnType(op);
        }

        @Override
        public String toString() {
            return String.format(FMT_Return, this.opcode());
        }

    }

    public static final class UnboundThrowInstruction
            extends UnboundInstruction implements ThrowInstruction {

        public UnboundThrowInstruction() {
            super(Opcode.ATHROW);
        }

        @Override
        public String toString() {
            return String.format(FMT_Throw, this.opcode());
        }

    }

    public static final class UnboundFieldInstruction
            extends UnboundInstruction implements FieldInstruction {
        final FieldRefEntry fieldEntry;

        public UnboundFieldInstruction(Opcode op,
                                       FieldRefEntry fieldEntry) {
            super(op);
            this.fieldEntry = requireNonNull(fieldEntry);
        }

        @Override
        public FieldRefEntry field() {
            return fieldEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeFieldAccess(op, fieldEntry);
        }

        @Override
        public String toString() {
            return String.format(FMT_Field, this.opcode(), this.owner().asInternalName(), this.name().stringValue(), this.type().stringValue());
        }
    }

    public static final class UnboundInvokeInstruction
            extends UnboundInstruction implements InvokeInstruction {
        final MemberRefEntry methodEntry;

        public UnboundInvokeInstruction(Opcode op, MemberRefEntry methodEntry) {
            super(op);
            this.methodEntry = requireNonNull(methodEntry);
        }

        @Override
        public MemberRefEntry method() {
            return methodEntry;
        }

        @Override
        public boolean isInterface() {
            return op == Opcode.INVOKEINTERFACE || methodEntry instanceof InterfaceMethodRefEntry;
        }

        @Override
        public int count() {
            return op == Opcode.INVOKEINTERFACE
                   ? Util.parameterSlots(Util.methodTypeSymbol(methodEntry.type())) + 1
                   : 0;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            if (op == Opcode.INVOKEINTERFACE)
                writer.writeInvokeInterface(op, (InterfaceMethodRefEntry) method(), count());
            else
                writer.writeInvokeNormal(op, method());
        }

        @Override
        public String toString() {
            return String.format(FMT_Invoke, this.opcode(), owner().asInternalName(), name().stringValue(), type().stringValue());
        }
    }

    public static final class UnboundInvokeDynamicInstruction
            extends UnboundInstruction implements InvokeDynamicInstruction {
        final InvokeDynamicEntry indyEntry;

        public UnboundInvokeDynamicInstruction(InvokeDynamicEntry indyEntry) {
            super(Opcode.INVOKEDYNAMIC);
            this.indyEntry = requireNonNull(indyEntry);
        }

        @Override
        public InvokeDynamicEntry invokedynamic() {
            return indyEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeInvokeDynamic(invokedynamic());
        }

        @Override
        public String toString() {
            return String.format(FMT_InvokeDynamic, this.opcode(), bootstrapMethod(), bootstrapArgs());
        }
    }

    public static final class UnboundNewObjectInstruction
            extends UnboundInstruction implements NewObjectInstruction {
        final ClassEntry classEntry;

        public UnboundNewObjectInstruction(ClassEntry classEntry) {
            super(Opcode.NEW);
            this.classEntry = requireNonNull(classEntry);
        }

        @Override
        public ClassEntry className() {
            return classEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeNewObject(className());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewObj, this.opcode(), className().asInternalName());
        }
    }

    public static final class UnboundNewPrimitiveArrayInstruction
            extends UnboundInstruction implements NewPrimitiveArrayInstruction {
        final TypeKind typeKind;

        public UnboundNewPrimitiveArrayInstruction(TypeKind typeKind) {
            super(Opcode.NEWARRAY);
            this.typeKind = requireNonNull(typeKind);
        }

        @Override
        public TypeKind typeKind() {
            return typeKind;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeNewPrimitiveArray(typeKind.newarrayCode());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewPrimitiveArray, this.opcode(), typeKind());
        }
    }

    public static final class UnboundNewReferenceArrayInstruction
            extends UnboundInstruction implements NewReferenceArrayInstruction {
        final ClassEntry componentTypeEntry;

        public UnboundNewReferenceArrayInstruction(ClassEntry componentTypeEntry) {
            super(Opcode.ANEWARRAY);
            this.componentTypeEntry = requireNonNull(componentTypeEntry);
        }

        @Override
        public ClassEntry componentType() {
            return componentTypeEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeNewReferenceArray(componentType());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewRefArray, this.opcode(), componentType().asInternalName());
        }
    }

    public static final class UnboundNewMultidimensionalArrayInstruction
            extends UnboundInstruction implements NewMultiArrayInstruction {
        final ClassEntry arrayTypeEntry;
        final int dimensions;

        public UnboundNewMultidimensionalArrayInstruction(ClassEntry arrayTypeEntry,
                                                          int dimensions) {
            super(Opcode.MULTIANEWARRAY);
            this.arrayTypeEntry = requireNonNull(arrayTypeEntry);
            this.dimensions = dimensions;
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public ClassEntry arrayType() {
            return arrayTypeEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeNewMultidimensionalArray(dimensions(), arrayType());
        }

        @Override
        public String toString() {
            return String.format(FMT_NewMultiArray, this.opcode(), arrayType().asInternalName(), dimensions());
        }

    }

    public static final class UnboundArrayLoadInstruction
            extends UnboundInstruction implements ArrayLoadInstruction {

        public UnboundArrayLoadInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.arrayLoadType(op);
        }
    }

    public static final class UnboundArrayStoreInstruction
            extends UnboundInstruction implements ArrayStoreInstruction {

        public UnboundArrayStoreInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.arrayStoreType(op);
        }
    }

    public static final class UnboundTypeCheckInstruction
            extends UnboundInstruction implements TypeCheckInstruction {
        final ClassEntry typeEntry;

        public UnboundTypeCheckInstruction(Opcode op, ClassEntry typeEntry) {
            super(op);
            this.typeEntry = requireNonNull(typeEntry);
        }

        @Override
        public ClassEntry type() {
            return typeEntry;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeTypeCheck(op, type());
        }

        @Override
        public String toString() {
            return String.format(FMT_TypeCheck, this.opcode(), type().asInternalName());
        }
    }

    public static final class UnboundStackInstruction
            extends UnboundInstruction implements StackInstruction {

        public UnboundStackInstruction(Opcode op) {
            super(op);
        }

    }

    public static final class UnboundConvertInstruction
            extends UnboundInstruction implements ConvertInstruction {

        public UnboundConvertInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind fromType() {
            return BytecodeHelpers.convertFromType(op);
        }

        @Override
        public TypeKind toType() {
            return BytecodeHelpers.convertToType(op);
        }
    }

    public static final class UnboundOperatorInstruction
            extends UnboundInstruction implements OperatorInstruction {

        public UnboundOperatorInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return BytecodeHelpers.operatorOperandType(op);
        }
    }

    public static final class UnboundIntrinsicConstantInstruction
            extends UnboundInstruction implements ConstantInstruction.IntrinsicConstantInstruction {
        public UnboundIntrinsicConstantInstruction(Opcode op) {
            super(op);
        }

        @Override
        public ConstantDesc constantValue() {
            return BytecodeHelpers.intrinsicConstantValue(op);
        }
    }

    public static final class UnboundArgumentConstantInstruction
            extends UnboundInstruction implements ConstantInstruction.ArgumentConstantInstruction {
        final int value;

        public UnboundArgumentConstantInstruction(Opcode op, int value) {
            super(op);
            this.value = value;
        }

        @Override
        public Integer constantValue() {
            return value;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeArgumentConstant(op, value);
        }

        @Override
        public String toString() {
            return String.format(FMT_ArgumentConstant, this.opcode(), constantValue());
        }
    }

    public static final class UnboundLoadConstantInstruction
            extends UnboundInstruction implements ConstantInstruction.LoadConstantInstruction {
        final LoadableConstantEntry constant;

        public UnboundLoadConstantInstruction(Opcode op, LoadableConstantEntry constant) {
            super(op);
            this.constant = requireNonNull(constant);
        }

        @Override
        public LoadableConstantEntry constantEntry() {
            return constant;
        }

        @Override
        public ConstantDesc constantValue() {
            return constant.constantValue();
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            var constant = this.constant;
            if (writer.canWriteDirect(constant.constantPool()))
                // Allows writing ldc_w small index constants upon user request
                writer.writeDirectLoadConstant(op, constant);
            else
                writer.writeAdaptLoadConstant(op, constant);
        }

        @Override
        public String toString() {
            return String.format(FMT_LoadConstant, this.opcode(), constantValue());
        }
    }

    public static final class UnboundMonitorInstruction
            extends UnboundInstruction implements MonitorInstruction {

        public UnboundMonitorInstruction(Opcode op) {
            super(op);
        }

    }

    public static final class UnboundNopInstruction
            extends UnboundInstruction implements NopInstruction {

        public UnboundNopInstruction() {
            super(Opcode.NOP);
        }

    }

    public static final class UnboundJsrInstruction
            extends UnboundInstruction implements DiscontinuedInstruction.JsrInstruction {
        final Label target;

        public UnboundJsrInstruction(Opcode op, Label target) {
            super(op);
            this.target = requireNonNull(target);
        }

        @Override
        public Label target() {
            return target;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeBranch(op, target);
        }

        @Override
        public String toString() {
            return String.format(FMT_Discontinued, this.opcode());
        }
    }

    public static final class UnboundRetInstruction
            extends UnboundInstruction implements DiscontinuedInstruction.RetInstruction {
        final int slot;

        public UnboundRetInstruction(Opcode op, int slot) {
            super(op);
            this.slot = slot;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeLocalVar(op, slot);
        }

        @Override
        public String toString() {
            return String.format(FMT_Discontinued, this.opcode());
        }
    }
}
