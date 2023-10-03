/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ConstantDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;

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
    final int size;

    @Override
    public Opcode opcode() {
        return op;
    }

    @Override
    public int sizeInBytes() {
        return size;
    }

    public AbstractInstruction(Opcode op, int size) {
        this.op = op;
        this.size = size;
    }

    @Override
    public abstract void writeTo(DirectCodeBuilder writer);

    public static abstract sealed class BoundInstruction extends AbstractInstruction {
        final CodeImpl code;
        final int pos;

        protected BoundInstruction(Opcode op, int size, CodeImpl code, int pos) {
            super(op, size);
            this.code = code;
            this.pos = pos;
        }

        protected Label offsetToLabel(int offset) {
            return code.getLabel(pos - code.codeStart + offset);
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            // Override this if the instruction has any CP references or labels!
            code.classReader.copyBytesTo(writer.bytecodesBufWriter, pos, size);
        }
    }

    public static final class BoundLoadInstruction
            extends BoundInstruction implements LoadInstruction {

        public BoundLoadInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }


        @Override
        public TypeKind typeKind() {
            return op.primaryTypeKind();
        }

        @Override
        public String toString() {
            return String.format(FMT_Load, this.opcode(), slot());
        }

        @Override
        public int slot() {
            return switch (size) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + op.sizeIfFixed() + " -- " + op);
            };
        }

    }

    public static final class BoundStoreInstruction
            extends BoundInstruction implements StoreInstruction {

        public BoundStoreInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public TypeKind typeKind() {
            return op.primaryTypeKind();
        }

        @Override
        public String toString() {
            return String.format(FMT_Store, this.opcode(), slot());
        }

        @Override
        public int slot() {
            return switch (size) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + size + " -- " + op);
            };
        }

    }

    public static final class BoundIncrementInstruction
            extends BoundInstruction implements IncrementInstruction {

        public BoundIncrementInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public int slot() {
            return size == 6 ? code.classReader.readU2(pos + 2) : code.classReader.readU1(pos + 1);
        }

        @Override
        public int constant() {
            return size == 6 ? code.classReader.readS2(pos + 4) : (byte) code.classReader.readS1(pos + 2);
        }

        @Override
        public String toString() {
            return String.format(FMT_Increment, this.opcode(), slot(), constant());
        }

    }

    public static final class BoundBranchInstruction
            extends BoundInstruction implements BranchInstruction {

        public BoundBranchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public Label target() {
            return offsetToLabel(branchByteOffset());
        }

        public int branchByteOffset() {
            return size == 3
                   ? (int) (short) code.classReader.readU2(pos + 1)
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
    }

    public static final class BoundLookupSwitchInstruction
            extends BoundInstruction implements LookupSwitchInstruction {

        // will always need size, cache everything to there
        private final int afterPad;
        private final int npairs;

        BoundLookupSwitchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, size(code, code.codeStart, pos), code, pos);

            this.afterPad = pos + 1 + ((4 - ((pos + 1 - code.codeStart) & 3)) & 3);
            this.npairs = code.classReader.readInt(afterPad + 4);
        }

        static int size(CodeImpl code, int codeStart, int pos) {
            int afterPad = pos + 1 + ((4 - ((pos + 1 - codeStart) & 3)) & 3);
            int pad = afterPad - (pos + 1);
            int npairs = code.classReader.readInt(afterPad + 4);
            return 1 + pad + 8 + npairs * 8;
        }

        private int defaultOffset() {
            return code.classReader.readInt(afterPad);
        }

        @Override
        public List<SwitchCase> cases() {
            var cases = new SwitchCase[npairs];
            for (int i = 0; i < npairs; ++i) {
                int z = afterPad + 8 + 8 * i;
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

        BoundTableSwitchInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, size(code, code.codeStart, pos), code, pos);
        }

        static int size(CodeImpl code, int codeStart, int pos) {
            int ap = pos + 1 + ((4 - ((pos + 1 - codeStart) & 3)) & 3);
            int pad = ap - (pos + 1);
            int low = code.classReader.readInt(ap + 4);
            int high = code.classReader.readInt(ap + 8);
            int cnt = high - low + 1;
            return 1 + pad + 12 + cnt * 4;
        }

        private int afterPadding() {
            int p = pos;
            return p + 1 + ((4 - ((p + 1 - code.codeStart) & 3)) & 3);
        }

        @Override
        public Label defaultTarget() {
            return offsetToLabel(defaultOffset());
        }

        @Override
        public int lowValue() {
            return code.classReader.readInt(afterPadding() + 4);
        }

        @Override
        public int highValue() {
            return code.classReader.readInt(afterPadding() + 8);
        }

        @Override
        public List<SwitchCase> cases() {
            int low = lowValue();
            int high = highValue();
            int defOff = defaultOffset();
            var cases = new ArrayList<SwitchCase>(high - low + 1);
            int z = afterPadding() + 12;
            for (int i = lowValue(); i <= high; ++i) {
                int off = code.classReader.readInt(z);
                if (defOff != off) {
                    cases.add(SwitchCase.of(i, offsetToLabel(off)));
                }
                z += 4;
            }
            return Collections.unmodifiableList(cases);
        }

        private int defaultOffset() {
            return code.classReader.readInt(afterPadding());
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
            super(op, op.sizeIfFixed(), code, pos);
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public MemberRefEntry method() {
            if (methodEntry == null)
                methodEntry = code.classReader.readEntry(pos + 1, MemberRefEntry.class);
            return methodEntry;
        }

        @Override
        public boolean isInterface() {
            return method().tag() == ClassFile.TAG_INTERFACEMETHODREF;
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
            super(op, op.sizeIfFixed(), code, pos);
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
            super(op, op.sizeIfFixed(), code, pos);
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
            super(Opcode.NEW, Opcode.NEW.sizeIfFixed(), code, pos);
        }

        @Override
        public ClassEntry className() {
            if (classEntry == null)
                classEntry = code.classReader.readClassEntry(pos + 1);
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public TypeKind typeKind() {
            return TypeKind.fromNewArrayCode(code.classReader.readU1(pos + 1));
        }

        @Override
        public String toString() {
            return String.format(FMT_NewPrimitiveArray, this.opcode(), typeKind());
        }

    }

    public static final class BoundNewReferenceArrayInstruction
            extends BoundInstruction implements NewReferenceArrayInstruction {

        public BoundNewReferenceArrayInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public ClassEntry componentType() {
            return code.classReader.readClassEntry(pos + 1);
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public int dimensions() {
            return code.classReader.readU1(pos + 3);
        }

        @Override
        public ClassEntry arrayType() {
            return code.classReader.readClassEntry(pos + 1);
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public ClassEntry type() {
            if (typeEntry == null)
                typeEntry = code.classReader.readClassEntry(pos + 1);
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public Integer constantValue() {
            return constantInt();
        }

        public int constantInt() {
            return size == 3 ? code.classReader.readS2(pos + 1) : code.classReader.readS1(pos + 1);
        }

        @Override
        public String toString() {
            return String.format(FMT_ArgumentConstant, this.opcode(), constantValue());
        }

    }

    public static final class BoundLoadConstantInstruction
            extends BoundInstruction implements ConstantInstruction.LoadConstantInstruction {

        public BoundLoadConstantInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public LoadableConstantEntry constantEntry() {
            return (LoadableConstantEntry)
                    code.classReader.entryByIndex(op == Opcode.LDC
                                                  ? code.classReader.readU1(pos + 1)
                                                  : code.classReader.readU2(pos + 1));
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
                writer.writeLoadConstant(op, constantEntry());
        }

        @Override
        public String toString() {
            return String.format(FMT_LoadConstant, this.opcode(), constantValue());
        }

    }

    public static final class BoundJsrInstruction
            extends BoundInstruction implements DiscontinuedInstruction.JsrInstruction {

        public BoundJsrInstruction(Opcode op, CodeImpl code, int pos) {
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public Label target() {
            return offsetToLabel(branchByteOffset());
        }

        public int branchByteOffset() {
            return size == 3
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
            super(op, op.sizeIfFixed(), code, pos);
        }

        @Override
        public String toString() {
            return String.format(FMT_Discontinued, this.opcode());
        }

        @Override
        public int slot() {
            return switch (size) {
                case 2 -> code.classReader.readU1(pos + 1);
                case 4 -> code.classReader.readU2(pos + 2);
                default -> throw new IllegalArgumentException("Unexpected op size: " + op.sizeIfFixed() + " -- " + op);
            };
        }

    }

    public static abstract sealed class UnboundInstruction extends AbstractInstruction {

        UnboundInstruction(Opcode op) {
            super(op, op.sizeIfFixed());
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
            return op.primaryTypeKind();
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeLocalVar(op, slot);
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
            return op.primaryTypeKind();
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeLocalVar(op, slot);
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
            super(slot <= 255 && constant < 128 && constant > -127
                  ? Opcode.IINC
                  : Opcode.IINC_W);
            this.slot = slot;
            this.constant = constant;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public int constant() {
            return 0;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeIncrement(slot, constant);
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
            this.target = target;
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
            this.defaultTarget = defaultTarget;
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
            this.defaultTarget = defaultTarget;
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
            return op.primaryTypeKind();
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
            this.fieldEntry = fieldEntry;
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
            this.methodEntry = methodEntry;
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
                   ? Util.parameterSlots(Util.methodTypeSymbol(methodEntry.nameAndType())) + 1
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
            this.indyEntry = indyEntry;
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
            this.classEntry = classEntry;
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
            this.typeKind = typeKind;
        }

        @Override
        public TypeKind typeKind() {
            return typeKind;
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            writer.writeNewPrimitiveArray(typeKind.newarraycode());
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
            this.componentTypeEntry = componentTypeEntry;
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
            this.arrayTypeEntry = arrayTypeEntry;
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
            return op.primaryTypeKind();
        }
    }

    public static final class UnboundArrayStoreInstruction
            extends UnboundInstruction implements ArrayStoreInstruction {

        public UnboundArrayStoreInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return op.primaryTypeKind();
        }
    }

    public static final class UnboundTypeCheckInstruction
            extends UnboundInstruction implements TypeCheckInstruction {
        final ClassEntry typeEntry;

        public UnboundTypeCheckInstruction(Opcode op, ClassEntry typeEntry) {
            super(op);
            this.typeEntry = typeEntry;
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
            return op.primaryTypeKind();
        }

        @Override
        public TypeKind toType() {
            return op.secondaryTypeKind();
        }
    }

    public static final class UnboundOperatorInstruction
            extends UnboundInstruction implements OperatorInstruction {

        public UnboundOperatorInstruction(Opcode op) {
            super(op);
        }

        @Override
        public TypeKind typeKind() {
            return op.primaryTypeKind();
        }
    }

    public static final class UnboundIntrinsicConstantInstruction
            extends UnboundInstruction implements ConstantInstruction.IntrinsicConstantInstruction {
        final ConstantDesc constant;

        public UnboundIntrinsicConstantInstruction(Opcode op) {
            super(op);
            constant = op.constantValue();
        }

        @Override
        public void writeTo(DirectCodeBuilder writer) {
            super.writeTo(writer);
        }

        @Override
        public ConstantDesc constantValue() {
            return constant;
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
            this.constant = constant;
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
            writer.writeLoadConstant(op, constant);
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
            this.target = target;
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
