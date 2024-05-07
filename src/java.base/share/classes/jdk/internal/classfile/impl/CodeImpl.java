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

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.*;

import static java.lang.classfile.ClassFile.*;

public final class CodeImpl
        extends BoundAttribute.BoundCodeAttribute
        implements CodeModel, LabelContext {

    static final Instruction[] SINGLETON_INSTRUCTIONS = new Instruction[256];

    static {
        for (var o : Opcode.values()) {
            if (o.sizeIfFixed() == 1) {
                SINGLETON_INSTRUCTIONS[o.bytecode()] = switch (o.kind()) {
                    case ARRAY_LOAD -> ArrayLoadInstruction.of(o);
                    case ARRAY_STORE -> ArrayStoreInstruction.of(o);
                    case CONSTANT -> ConstantInstruction.ofIntrinsic(o);
                    case CONVERT -> ConvertInstruction.of(o);
                    case LOAD -> LoadInstruction.of(o, o.slot());
                    case MONITOR -> MonitorInstruction.of(o);
                    case NOP -> NopInstruction.of();
                    case OPERATOR -> OperatorInstruction.of(o);
                    case RETURN -> ReturnInstruction.of(o);
                    case STACK -> StackInstruction.of(o);
                    case STORE -> StoreInstruction.of(o, o.slot());
                    case THROW_EXCEPTION -> ThrowInstruction.of();
                    default -> throw new AssertionError("invalid opcode: " + o);
                };
            }
        }
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
        if (bci < 0 || bci > codeLength)
            throw new IllegalArgumentException(String.format("Bytecode offset out of range; bci=%d, codeLength=%d",
                                                             bci, codeLength));
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
            if (((ClassReaderImpl)classReader).context().lineNumbersOption() == ClassFile.LineNumbersOption.PASS_LINE_NUMBERS)
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
                                    ((BufWriterImpl)buf).context(),
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
        if (((ClassReaderImpl)classReader).context().debugElementsOption() == ClassFile.DebugElementsOption.PASS_DEBUG)
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
        if (a.isEmpty()) {
            if (classReader.readU2(6) <= ClassFile.JAVA_6_VERSION) {
                //fallback to jump targets inflation without StackMapTableAttribute
                for (int pos=codeStart; pos<codeEnd; ) {
                    var i = bcToInstruction(classReader.readU1(pos), pos);
                    switch (i) {
                        case BranchInstruction br -> br.target();
                        case DiscontinuedInstruction.JsrInstruction jsr -> jsr.target();
                        default -> {}
                    }
                    pos += i.sizeInBytes();
                }
            }
            return;
        }
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
                    case RET ->  new AbstractInstruction.BoundRetInstruction(Opcode.RET_W, this, pos);
                    default -> throw new IllegalArgumentException("unknown wide instruction: " + bclow);
                };
            }

            case MULTIANEWARRAY -> new AbstractInstruction.BoundNewMultidimensionalArrayInstruction(Opcode.MULTIANEWARRAY, CodeImpl.this, pos);
            case IFNULL -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFNULL, CodeImpl.this, pos);
            case IFNONNULL -> new AbstractInstruction.BoundBranchInstruction(Opcode.IFNONNULL, CodeImpl.this, pos);
            case GOTO_W -> new AbstractInstruction.BoundBranchInstruction(Opcode.GOTO_W, CodeImpl.this, pos);

            case JSR -> new AbstractInstruction.BoundJsrInstruction(Opcode.JSR, CodeImpl.this, pos);
            case RET ->  new AbstractInstruction.BoundRetInstruction(Opcode.RET, this, pos);
            case JSR_W -> new AbstractInstruction.BoundJsrInstruction(Opcode.JSR_W, CodeImpl.this, pos);
            default -> {
                Instruction instr = SINGLETON_INSTRUCTIONS[bc];
                if (instr == null)
                    throw new IllegalArgumentException("unknown instruction: " + bc);
                yield instr;
            }
        };
    }

    @Override
    public String toString() {
        return String.format("CodeModel[id=%d]", System.identityHashCode(this));
    }
}
