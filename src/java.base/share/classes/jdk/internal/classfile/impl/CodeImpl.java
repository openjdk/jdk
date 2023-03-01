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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.StackMapTableAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.instruction.*;

import static jdk.internal.classfile.Classfile.*;

/**
 * CodeAttr
 */
public final class CodeImpl
        extends BoundAttribute.BoundCodeAttribute
        implements CodeModel, LabelContext {

    static final Instruction[] SINGLETON_INSTRUCTIONS = new Instruction[256];

    static {
        for (Opcode o : List.of(Opcode.NOP))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = NopInstruction.of();
        for (Opcode o : List.of(Opcode.ACONST_NULL,
                                Opcode.ICONST_M1,
                                Opcode.ICONST_0, Opcode.ICONST_1, Opcode.ICONST_2, Opcode.ICONST_3, Opcode.ICONST_4, Opcode.ICONST_5,
                                Opcode.LCONST_0, Opcode.LCONST_1,
                                Opcode.FCONST_0, Opcode.FCONST_1, Opcode.FCONST_2,
                                Opcode.DCONST_0, Opcode.DCONST_1))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ConstantInstruction.ofIntrinsic(o);
        for (Opcode o : List.of(Opcode.ILOAD_0, Opcode.ILOAD_1, Opcode.ILOAD_2, Opcode.ILOAD_3,
                                Opcode.LLOAD_0, Opcode.LLOAD_1, Opcode.LLOAD_2, Opcode.LLOAD_3,
                                Opcode.FLOAD_0, Opcode.FLOAD_1, Opcode.FLOAD_2, Opcode.FLOAD_3,
                                Opcode.DLOAD_0, Opcode.DLOAD_1, Opcode.DLOAD_2, Opcode.DLOAD_3,
                                Opcode.ALOAD_0, Opcode.ALOAD_1, Opcode.ALOAD_2, Opcode.ALOAD_3))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = LoadInstruction.of(o, o.slot());
        for (Opcode o : List.of(Opcode.ISTORE_0, Opcode.ISTORE_1, Opcode.ISTORE_2, Opcode.ISTORE_3,
                                Opcode.LSTORE_0, Opcode.LSTORE_1, Opcode.LSTORE_2, Opcode.LSTORE_3,
                                Opcode.FSTORE_0, Opcode.FSTORE_1, Opcode.FSTORE_2, Opcode.FSTORE_3,
                                Opcode.DSTORE_0, Opcode.DSTORE_1, Opcode.DSTORE_2, Opcode.DSTORE_3,
                                Opcode.ASTORE_0, Opcode.ASTORE_1, Opcode.ASTORE_2, Opcode.ASTORE_3))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = StoreInstruction.of(o, o.slot());
        for (Opcode o : List.of(Opcode.IALOAD, Opcode.LALOAD, Opcode.FALOAD, Opcode.DALOAD, Opcode.AALOAD, Opcode.BALOAD, Opcode.CALOAD, Opcode.SALOAD))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ArrayLoadInstruction.of(o);
        for (Opcode o : List.of(Opcode.IASTORE, Opcode.LASTORE, Opcode.FASTORE, Opcode.DASTORE, Opcode.AASTORE, Opcode.BASTORE, Opcode.CASTORE, Opcode.SASTORE))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ArrayStoreInstruction.of(o);
        for (Opcode o : List.of(Opcode.POP, Opcode.POP2, Opcode.DUP, Opcode.DUP_X1, Opcode.DUP_X2, Opcode.DUP2, Opcode.DUP2_X1, Opcode.DUP2_X2, Opcode.SWAP))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = StackInstruction.of(o);
        for (Opcode o : List.of(Opcode.IADD, Opcode.LADD, Opcode.FADD, Opcode.DADD, Opcode.ISUB,
                                Opcode.LSUB, Opcode.FSUB, Opcode.DSUB,
                                Opcode.IMUL, Opcode.LMUL, Opcode.FMUL, Opcode.DMUL,
                                Opcode.IDIV, Opcode.LDIV, Opcode.FDIV, Opcode.DDIV,
                                Opcode.IREM, Opcode.LREM, Opcode.FREM, Opcode.DREM,
                                Opcode.INEG, Opcode.LNEG, Opcode.FNEG, Opcode.DNEG,
                                Opcode.ISHL, Opcode.LSHL, Opcode.ISHR, Opcode.LSHR, Opcode.IUSHR, Opcode.LUSHR,
                                Opcode.IAND, Opcode.LAND, Opcode.IOR, Opcode.LOR, Opcode.IXOR, Opcode.LXOR,
                                Opcode.LCMP, Opcode.FCMPL, Opcode.FCMPG, Opcode.DCMPL, Opcode.DCMPG,
                                Opcode.ARRAYLENGTH))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = OperatorInstruction.of(o);

        for (Opcode o : List.of(Opcode.I2L, Opcode.I2F, Opcode.I2D,
                                Opcode.L2I, Opcode.L2F, Opcode.L2D,
                                Opcode.F2I, Opcode.F2L, Opcode.F2D,
                                Opcode.D2I, Opcode.D2L, Opcode.D2F,
                                Opcode.I2B, Opcode.I2C, Opcode.I2S))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ConvertInstruction.of(o);
        for (Opcode o : List.of(Opcode.IRETURN, Opcode.LRETURN, Opcode.FRETURN, Opcode.DRETURN, Opcode.ARETURN, Opcode.RETURN))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ReturnInstruction.of(o);
        for (Opcode o : List.of(Opcode.ATHROW))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = ThrowInstruction.of();
        for (Opcode o : List.of(Opcode.MONITORENTER, Opcode.MONITOREXIT))
            SINGLETON_INSTRUCTIONS[o.bytecode()] = MonitorInstruction.of(o);
    }

    List<ExceptionCatch> exceptionTable;
    List<Attribute<?>> attributes;

    // Inflated for iteration
    LabelImpl[] labels;
    int[] lineNumbers;
    boolean inflated;

    public CodeImpl(AttributedElement enclosing,
                    ClassReader reader,
                    AttributeMapper<CodeAttribute> mapper,
                    int payloadStart) {
        super(enclosing, reader, mapper, payloadStart);
    }

    // LabelContext

    @Override
    public Label newLabel() {
        throw new UnsupportedOperationException("CodeAttribute only supports fixed labels");
    }

    @Override
    public void setLabelTarget(Label label, int bci) {
        throw new UnsupportedOperationException("CodeAttribute only supports fixed labels");
    }

    @Override
    public Label getLabel(int bci) {
        if (labels == null)
            labels = new LabelImpl[codeLength + 1];
        LabelImpl l = labels[bci];
        if (l == null)
            l = labels[bci] = new LabelImpl(this, bci);
        return l;
    }

    @Override
    public int labelToBci(Label label) {
        LabelImpl lab = (LabelImpl) label;
        if (lab.labelContext() != this)
            throw new IllegalArgumentException(String.format("Illegal label reuse; context=%s, label=%s",
                                                             this, lab.labelContext()));
        return lab.getBCI();
    }

    private void inflateMetadata() {
        if (!inflated) {
            if (labels == null)
                labels = new LabelImpl[codeLength + 1];
            if (((ClassReaderImpl)classReader).options().processLineNumbers)
                inflateLineNumbers();
            inflateJumpTargets();
            inflateTypeAnnotations();
            inflated = true;
        }
    }

    // CodeAttribute

    @Override
    public List<Attribute<?>> attributes() {
        if (attributes == null) {
            attributes = BoundAttribute.readAttributes(this, classReader, attributePos, classReader.customAttributes());
        }
        return attributes;
    }

    @Override
    public void writeTo(BufWriter buf) {
        if (buf.canWriteDirect(classReader)) {
            super.writeTo(buf);
        }
        else {
            DirectCodeBuilder.build((MethodInfo) enclosingMethod,
                                    new Consumer<CodeBuilder>() {
                                        @Override
                                        public void accept(CodeBuilder cb) {
                                            forEachElement(cb);
                                        }
                                    },
                                    (SplitConstantPool)buf.constantPool(),
                                    null).writeTo(buf);
        }
    }

    // CodeModel

    @Override
    public Optional<MethodModel> parent() {
        return Optional.of(enclosingMethod);
    }

    @Override
    public void forEachElement(Consumer<CodeElement> consumer) {
        inflateMetadata();
        boolean doLineNumbers = (lineNumbers != null);
        generateCatchTargets(consumer);
        if (((ClassReaderImpl)classReader).options().processDebug)
            generateDebugElements(consumer);
        for (int pos=codeStart; pos<codeEnd; ) {
            if (labels[pos - codeStart] != null)
                consumer.accept(labels[pos - codeStart]);
            if (doLineNumbers && lineNumbers[pos - codeStart] != 0)
                consumer.accept(LineNumberImpl.of(lineNumbers[pos - codeStart]));
            int bc = classReader.readU1(pos);
            Instruction instr = bcToInstruction(bc, pos);
            consumer.accept(instr);
            pos += instr.sizeInBytes();
        }
        // There might be labels pointing to the bci at codeEnd
        if (labels[codeEnd-codeStart] != null)
            consumer.accept(labels[codeEnd - codeStart]);
        if (doLineNumbers && lineNumbers[codeEnd - codeStart] != 0)
            consumer.accept(LineNumberImpl.of(lineNumbers[codeEnd - codeStart]));
    }

    @Override
    public List<ExceptionCatch> exceptionHandlers() {
        if (exceptionTable == null) {
            inflateMetadata();
            exceptionTable = new ArrayList<>(exceptionHandlerCnt);
            iterateExceptionHandlers(new ExceptionHandlerAction() {
                @Override
                public void accept(int s, int e, int h, int c) {
                    ClassEntry catchTypeEntry = c == 0
                                                             ? null
                                                             : (ClassEntry) constantPool().entryByIndex(c);
                    exceptionTable.add(new AbstractPseudoInstruction.ExceptionCatchImpl(getLabel(h), getLabel(s), getLabel(e), catchTypeEntry));
                }
            });
            exceptionTable = Collections.unmodifiableList(exceptionTable);
        }
        return exceptionTable;
    }

    public boolean compareCodeBytes(BufWriter buf, int offset, int len) {
        return codeLength == len
               && classReader.compare(buf, offset, codeStart, codeLength);
    }

    private int adjustForObjectOrUninitialized(int bci) {
        int vt = classReader.readU1(bci);
        //inflate newTarget labels from Uninitialized VTIs
        if (vt == 8) inflateLabel(classReader.readU2(bci + 1));
        return (vt == 7 || vt == 8) ? bci + 3 : bci + 1;
    }

    private void inflateLabel(int bci) {
        if (labels[bci] == null)
            labels[bci] = new LabelImpl(this, bci);
    }

    private void inflateLineNumbers() {
        for (Attribute<?> a : attributes()) {
            if (a.attributeMapper() == Attributes.LINE_NUMBER_TABLE) {
                BoundLineNumberTableAttribute attr = (BoundLineNumberTableAttribute) a;
                if (lineNumbers == null)
                    lineNumbers = new int[codeLength + 1];

                int nLn = classReader.readU2(attr.payloadStart);
                int p = attr.payloadStart + 2;
                int pEnd = p + (nLn * 4);
                for (; p < pEnd; p += 4) {
                    int startPc = classReader.readU2(p);
                    int lineNumber = classReader.readU2(p + 2);
                    lineNumbers[startPc] = lineNumber;
                }
            }
        }
    }

    private void inflateJumpTargets() {
        Optional<StackMapTableAttribute> a = findAttribute(Attributes.STACK_MAP_TABLE);
        if (a.isEmpty())
            return;
        @SuppressWarnings("unchecked")
        int stackMapPos = ((BoundAttribute<StackMapTableAttribute>) a.get()).payloadStart;

        int bci = -1; //compensate for offsetDelta + 1
        int nEntries = classReader.readU2(stackMapPos);
        int p = stackMapPos + 2;
        for (int i = 0; i < nEntries; ++i) {
            int frameType = classReader.readU1(p);
            int offsetDelta;
            if (frameType < 64) {
                offsetDelta = frameType;
                ++p;
            }
            else if (frameType < 128) {
                offsetDelta = frameType & 0x3f;
                p = adjustForObjectOrUninitialized(p + 1);
            }
            else
                switch (frameType) {
                    case 247 -> {
                        offsetDelta = classReader.readU2(p + 1);
                        p = adjustForObjectOrUninitialized(p + 3);
                    }
                    case 248, 249, 250, 251 -> {
                        offsetDelta = classReader.readU2(p + 1);
                        p += 3;
                    }
                    case 252, 253, 254 -> {
                        offsetDelta = classReader.readU2(p + 1);
                        int k = frameType - 251;
                        p += 3;
                        for (int c = 0; c < k; ++c) {
                            p = adjustForObjectOrUninitialized(p);
                        }
                    }
                    case 255 -> {
                        offsetDelta = classReader.readU2(p + 1);
                        p += 3;
                        int k = classReader.readU2(p);
                        p += 2;
                        for (int c = 0; c < k; ++c) {
                            p = adjustForObjectOrUninitialized(p);
                        }
                        k = classReader.readU2(p);
                        p += 2;
                        for (int c = 0; c < k; ++c) {
                            p = adjustForObjectOrUninitialized(p);
                        }
                    }
                    default -> throw new IllegalArgumentException("Bad frame type: " + frameType);
                }
            bci += offsetDelta + 1;
            inflateLabel(bci);
        }
    }

    private void inflateTypeAnnotations() {
        findAttribute(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS).ifPresent(RuntimeVisibleTypeAnnotationsAttribute::annotations);
        findAttribute(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS).ifPresent(RuntimeInvisibleTypeAnnotationsAttribute::annotations);
    }

    private void generateCatchTargets(Consumer<CodeElement> consumer) {
        // We attach all catch targets to bci zero, because trying to attach them
        // to their range could subtly affect the order of exception processing
        iterateExceptionHandlers(new ExceptionHandlerAction() {
            @Override
            public void accept(int s, int e, int h, int c) {
                ClassEntry catchType = c == 0
                                                    ? null
                                                    : (ClassEntry) classReader.entryByIndex(c);
                consumer.accept(new AbstractPseudoInstruction.ExceptionCatchImpl(getLabel(h), getLabel(s), getLabel(e), catchType));
            }
        });
    }

    private void generateDebugElements(Consumer<CodeElement> consumer) {
        for (Attribute<?> a : attributes()) {
            if (a.attributeMapper() == Attributes.CHARACTER_RANGE_TABLE) {
                var attr = (BoundCharacterRangeTableAttribute) a;
                int cnt = classReader.readU2(attr.payloadStart);
                int p = attr.payloadStart + 2;
                int pEnd = p + (cnt * 14);
                for (; p < pEnd; p += 14) {
                    var instruction = new BoundCharacterRange(this, p);
                    inflateLabel(instruction.startPc());
                    inflateLabel(instruction.endPc() + 1);
                    consumer.accept(instruction);
                }
            }
            else if (a.attributeMapper() == Attributes.LOCAL_VARIABLE_TABLE) {
                var attr = (BoundLocalVariableTableAttribute) a;
                int cnt = classReader.readU2(attr.payloadStart);
                int p = attr.payloadStart + 2;
                int pEnd = p + (cnt * 10);
                for (; p < pEnd; p += 10) {
                    BoundLocalVariable instruction = new BoundLocalVariable(this, p);
                    inflateLabel(instruction.startPc());
                    inflateLabel(instruction.startPc() + instruction.length());
                    consumer.accept(instruction);
                }
            }
            else if (a.attributeMapper() == Attributes.LOCAL_VARIABLE_TYPE_TABLE) {
                var attr = (BoundLocalVariableTypeTableAttribute) a;
                int cnt = classReader.readU2(attr.payloadStart);
                int p = attr.payloadStart + 2;
                int pEnd = p + (cnt * 10);
                for (; p < pEnd; p += 10) {
                    BoundLocalVariableType instruction = new BoundLocalVariableType(this, p);
                    inflateLabel(instruction.startPc());
                    inflateLabel(instruction.startPc() + instruction.length());
                    consumer.accept(instruction);
                }
            }
            else if (a.attributeMapper() == Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS) {
                consumer.accept((BoundRuntimeVisibleTypeAnnotationsAttribute) a);
            }
            else if (a.attributeMapper() == Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS) {
                consumer.accept((BoundRuntimeInvisibleTypeAnnotationsAttribute) a);
            }
        }
    }

    public interface ExceptionHandlerAction {
        void accept(int start, int end, int handler, int catchTypeIndex);
    }

    public void iterateExceptionHandlers(ExceptionHandlerAction a) {
        int p = exceptionHandlerPos + 2;
        for (int i = 0; i < exceptionHandlerCnt; ++i) {
            a.accept(classReader.readU2(p), classReader.readU2(p + 2), classReader.readU2(p + 4), classReader.readU2(p + 6));
            p += 8;
        }
    }

    private Instruction bcToInstruction(int bc, int pos) {
        return switch (bc) {
            case BIPUSH -> new AbstractInstruction.BoundArgumentConstantInstruction(Opcode.BIPUSH, CodeImpl.this, pos);
            case SIPUSH -> new AbstractInstruction.BoundArgumentConstantInstruction(Opcode.SIPUSH, CodeImpl.this, pos);
            case LDC -> new AbstractInstruction.BoundLoadConstantInstruction(Opcode.LDC, CodeImpl.this, pos);
            case LDC_W -> new AbstractInstruction.BoundLoadConstantInstruction(Opcode.LDC_W, CodeImpl.this, pos);
            case LDC2_W -> new AbstractInstruction.BoundLoadConstantInstruction(Opcode.LDC2_W, CodeImpl.this, pos);
            case ILOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.ILOAD, CodeImpl.this, pos);
            case LLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.LLOAD, CodeImpl.this, pos);
            case FLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.FLOAD, CodeImpl.this, pos);
            case DLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.DLOAD, CodeImpl.this, pos);
            case ALOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.ALOAD, CodeImpl.this, pos);
            case ISTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.ISTORE, CodeImpl.this, pos);
            case LSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.LSTORE, CodeImpl.this, pos);
            case FSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.FSTORE, CodeImpl.this, pos);
            case DSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.DSTORE, CodeImpl.this, pos);
            case ASTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.ASTORE, CodeImpl.this, pos);
            case IINC -> new AbstractInstruction.BoundIncrementInstruction(Opcode.IINC, CodeImpl.this, pos);
            case IFEQ -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFEQ, CodeImpl.this, pos);
            case IFNE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFNE, CodeImpl.this, pos);
            case IFLT -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFLT, CodeImpl.this, pos);
            case IFGE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFGE, CodeImpl.this, pos);
            case IFGT -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFGT, CodeImpl.this, pos);
            case IFLE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFLE, CodeImpl.this, pos);
            case IF_ICMPEQ -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPEQ, CodeImpl.this, pos);
            case IF_ICMPNE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPNE, CodeImpl.this, pos);
            case IF_ICMPLT -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPLT, CodeImpl.this, pos);
            case IF_ICMPGE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPGE, CodeImpl.this, pos);
            case IF_ICMPGT -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPGT, CodeImpl.this, pos);
            case IF_ICMPLE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ICMPLE, CodeImpl.this, pos);
            case IF_ACMPEQ -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ACMPEQ, CodeImpl.this, pos);
            case IF_ACMPNE -> new AbstractInstruction.BoundBranchInstruction(Opcode.IF_ACMPNE, CodeImpl.this, pos);
            case GOTO -> new AbstractInstruction.BoundBranchInstruction(Opcode.GOTO, CodeImpl.this, pos);
            case TABLESWITCH -> new AbstractInstruction.BoundTableSwitchInstruction(Opcode.TABLESWITCH, CodeImpl.this, pos);
            case LOOKUPSWITCH -> new AbstractInstruction.BoundLookupSwitchInstruction(Opcode.LOOKUPSWITCH, CodeImpl.this, pos);
            case GETSTATIC -> new AbstractInstruction.BoundFieldInstruction(Opcode.GETSTATIC, CodeImpl.this, pos);
            case PUTSTATIC -> new AbstractInstruction.BoundFieldInstruction(Opcode.PUTSTATIC, CodeImpl.this, pos);
            case GETFIELD -> new AbstractInstruction.BoundFieldInstruction(Opcode.GETFIELD, CodeImpl.this, pos);
            case PUTFIELD -> new AbstractInstruction.BoundFieldInstruction(Opcode.PUTFIELD, CodeImpl.this, pos);
            case INVOKEVIRTUAL -> new AbstractInstruction.BoundInvokeInstruction(Opcode.INVOKEVIRTUAL, CodeImpl.this, pos);
            case INVOKESPECIAL -> new AbstractInstruction.BoundInvokeInstruction(Opcode.INVOKESPECIAL, CodeImpl.this, pos);
            case INVOKESTATIC -> new AbstractInstruction.BoundInvokeInstruction(Opcode.INVOKESTATIC, CodeImpl.this, pos);
            case INVOKEINTERFACE -> new AbstractInstruction.BoundInvokeInterfaceInstruction(Opcode.INVOKEINTERFACE, CodeImpl.this, pos);
            case INVOKEDYNAMIC -> new AbstractInstruction.BoundInvokeDynamicInstruction(Opcode.INVOKEDYNAMIC, CodeImpl.this, pos);
            case NEW -> new AbstractInstruction.BoundNewObjectInstruction(CodeImpl.this, pos);
            case NEWARRAY -> new AbstractInstruction.BoundNewPrimitiveArrayInstruction(Opcode.NEWARRAY, CodeImpl.this, pos);
            case ANEWARRAY -> new AbstractInstruction.BoundNewReferenceArrayInstruction(Opcode.ANEWARRAY, CodeImpl.this, pos);
            case CHECKCAST -> new AbstractInstruction.BoundTypeCheckInstruction(Opcode.CHECKCAST, CodeImpl.this, pos);
            case INSTANCEOF -> new AbstractInstruction.BoundTypeCheckInstruction(Opcode.INSTANCEOF, CodeImpl.this, pos);

            case WIDE -> {
                int bclow = classReader.readU1(pos + 1);
                yield switch (bclow) {
                    case ILOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.ILOAD_W, this, pos);
                    case LLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.LLOAD_W, this, pos);
                    case FLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.FLOAD_W, this, pos);
                    case DLOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.DLOAD_W, this, pos);
                    case ALOAD -> new AbstractInstruction.BoundLoadInstruction(Opcode.ALOAD_W, this, pos);
                    case ISTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.ISTORE_W, this, pos);
                    case LSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.LSTORE_W, this, pos);
                    case FSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.FSTORE_W, this, pos);
                    case DSTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.DSTORE_W, this, pos);
                    case ASTORE -> new AbstractInstruction.BoundStoreInstruction(Opcode.ASTORE_W, this, pos);
                    case IINC -> new AbstractInstruction.BoundIncrementInstruction(Opcode.IINC_W, this, pos);
                    case RET -> throw new UnsupportedOperationException("RET_W instruction not supported");
                    default -> throw new UnsupportedOperationException("unknown wide instruction: " + bclow);
                };
            }

            case MULTIANEWARRAY -> new AbstractInstruction.BoundNewMultidimensionalArrayInstruction(Opcode.MULTIANEWARRAY, CodeImpl.this, pos);
            case IFNULL -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFNULL, CodeImpl.this, pos);
            case IFNONNULL -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFNONNULL, CodeImpl.this, pos);
            case GOTO_W -> new AbstractInstruction.BoundBranchInstruction(Opcode.GOTO_W, CodeImpl.this, pos);

            case JSR -> throw new UnsupportedOperationException("JSR instruction not supported");
            case RET -> throw new UnsupportedOperationException("RET instruction not supported");
            case JSR_W -> throw new UnsupportedOperationException("JSR_W instruction not supported");
            default -> {
                Instruction instr = SINGLETON_INSTRUCTIONS[bc];
                if (instr == null)
                    throw new UnsupportedOperationException("unknown instruction: " + bc);
                yield instr;
            }
        };
    }

    @Override
    public String toString() {
        return String.format("CodeModel[id=%d]", System.identityHashCode(this));
    }
}
