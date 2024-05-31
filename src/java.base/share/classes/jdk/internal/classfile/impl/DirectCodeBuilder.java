/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;

import static java.lang.classfile.Opcode.GOTO;
import static java.lang.classfile.Opcode.GOTO_W;
import static java.lang.classfile.Opcode.IINC;
import static java.lang.classfile.Opcode.IINC_W;
import static java.lang.classfile.Opcode.JSR;
import static java.lang.classfile.Opcode.JSR_W;
import static java.lang.classfile.Opcode.LDC2_W;
import static java.lang.classfile.Opcode.LDC_W;

public final class DirectCodeBuilder
        extends AbstractDirectBuilder<CodeModel>
        implements TerminalCodeBuilder, LabelContext {
    private final List<CharacterRange> characterRanges = new ArrayList<>();
    final List<AbstractPseudoInstruction.ExceptionCatchImpl> handlers = new ArrayList<>();
    private final List<LocalVariable> localVariables = new ArrayList<>();
    private final List<LocalVariableType> localVariableTypes = new ArrayList<>();
    private final boolean transformFwdJumps, transformBackJumps;
    private final Label startLabel, endLabel;
    final MethodInfo methodInfo;
    final BufWriter bytecodesBufWriter;
    private CodeAttribute mruParent;
    private int[] mruParentTable;
    private Map<CodeAttribute, int[]> parentMap;
    private DedupLineNumberTableAttribute lineNumberWriter;
    private int topLocal;

    List<DeferredLabel> deferredLabels;

    /* Locals management
       lazily computed maxLocal = -1
       first time: derive count from methodType descriptor (for new methods) & ACC_STATIC,
       or model maxLocals (for transformation)
       block builders inherit parent count
       allocLocal(TypeKind) bumps by nSlots
     */

    public static Attribute<CodeAttribute> build(MethodInfo methodInfo,
                                                 Consumer<? super CodeBuilder> handler,
                                                 SplitConstantPool constantPool,
                                                 ClassFileImpl context,
                                                 CodeModel original) {
        DirectCodeBuilder cb;
        try {
            handler.accept(cb = new DirectCodeBuilder(methodInfo, constantPool, context, original, false));
            cb.buildContent();
        } catch (LabelOverflowException loe) {
            if (context.shortJumpsOption() == ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS) {
                handler.accept(cb = new DirectCodeBuilder(methodInfo, constantPool, context, original, true));
                cb.buildContent();
            }
            else
                throw loe;
        }
        return cb.content;
    }

    private DirectCodeBuilder(MethodInfo methodInfo,
                              SplitConstantPool constantPool,
                              ClassFileImpl context,
                              CodeModel original,
                              boolean transformFwdJumps) {
        super(constantPool, context);
        setOriginal(original);
        this.methodInfo = methodInfo;
        this.transformFwdJumps = transformFwdJumps;
        this.transformBackJumps = context.shortJumpsOption() == ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS;
        bytecodesBufWriter = (original instanceof CodeImpl cai) ? new BufWriterImpl(constantPool, context, cai.codeLength())
                                                               : new BufWriterImpl(constantPool, context);
        this.startLabel = new LabelImpl(this, 0);
        this.endLabel = new LabelImpl(this, -1);
        this.topLocal = Util.maxLocals(methodInfo.methodFlags(), methodInfo.methodTypeSymbol());
        if (original != null)
            this.topLocal = Math.max(this.topLocal, original.maxLocals());
    }

    @Override
    public CodeBuilder with(CodeElement element) {
        if (element instanceof AbstractElement ae) {
            ae.writeTo(this);
        } else {
            writeAttribute((CustomAttribute)element);
        }
        return this;
    }

    @Override
    public Label newLabel() {
        return new LabelImpl(this, -1);
    }

    @Override
    public Label startLabel() {
        return startLabel;
    }

    @Override
    public Label endLabel() {
        return endLabel;
    }

    @Override
    public int receiverSlot() {
        return methodInfo.receiverSlot();
    }

    @Override
    public int parameterSlot(int paramNo) {
        return methodInfo.parameterSlot(paramNo);
    }

    public int curTopLocal() {
        return topLocal;
    }

    @Override
    public int allocateLocal(TypeKind typeKind) {
        int retVal = topLocal;
        topLocal += typeKind.slotSize();
        return retVal;
    }

    public int curPc() {
        return bytecodesBufWriter.size();
    }

    public MethodInfo methodInfo() {
        return methodInfo;
    }

    private Attribute<CodeAttribute> content = null;

    private void writeExceptionHandlers(BufWriter buf) {
        int pos = buf.size();
        int handlersSize = handlers.size();
        buf.writeU2(handlersSize);
        for (AbstractPseudoInstruction.ExceptionCatchImpl h : handlers) {
            int startPc = labelToBci(h.tryStart());
            int endPc = labelToBci(h.tryEnd());
            int handlerPc = labelToBci(h.handler());
            if (startPc == -1 || endPc == -1 || handlerPc == -1) {
                if (context.deadLabelsOption() == ClassFile.DeadLabelsOption.DROP_DEAD_LABELS) {
                    handlersSize--;
                } else {
                    throw new IllegalArgumentException("Unbound label in exception handler");
                }
            } else {
                buf.writeU2(startPc);
                buf.writeU2(endPc);
                buf.writeU2(handlerPc);
                buf.writeIndexOrZero(h.catchTypeEntry());
                handlersSize++;
            }
        }
        if (handlersSize < handlers.size())
            buf.patchInt(pos, 2, handlersSize);
    }

    private void buildContent() {
        if (content != null) return;
        setLabelTarget(endLabel);

        // Backfill branches for which Label didn't have position yet
        processDeferredLabels();

        if (context.debugElementsOption() == ClassFile.DebugElementsOption.PASS_DEBUG) {
            if (!characterRanges.isEmpty()) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.characterRangeTable()) {

                    @Override
                    public void writeBody(BufWriter b) {
                        int pos = b.size();
                        int crSize = characterRanges.size();
                        b.writeU2(crSize);
                        for (CharacterRange cr : characterRanges) {
                            var start = labelToBci(cr.startScope());
                            var end = labelToBci(cr.endScope());
                            if (start == -1 || end == -1) {
                                if (context.deadLabelsOption() == ClassFile.DeadLabelsOption.DROP_DEAD_LABELS) {
                                    crSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in character range");
                                }
                            } else {
                                b.writeU2(start);
                                b.writeU2(end - 1);
                                b.writeInt(cr.characterRangeStart());
                                b.writeInt(cr.characterRangeEnd());
                                b.writeU2(cr.flags());
                            }
                        }
                        if (crSize < characterRanges.size())
                            b.patchInt(pos, 2, crSize);
                    }
                };
                attributes.withAttribute(a);
            }

            if (!localVariables.isEmpty()) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTable()) {
                    @Override
                    public void writeBody(BufWriter b) {
                        int pos = b.size();
                        int lvSize = localVariables.size();
                        b.writeU2(lvSize);
                        for (LocalVariable l : localVariables) {
                            if (!l.writeTo(b)) {
                                if (context.deadLabelsOption() == ClassFile.DeadLabelsOption.DROP_DEAD_LABELS) {
                                    lvSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in local variable type");
                                }
                            }
                        }
                        if (lvSize < localVariables.size())
                            b.patchInt(pos, 2, lvSize);
                    }
                };
                attributes.withAttribute(a);
            }

            if (!localVariableTypes.isEmpty()) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTypeTable()) {
                    @Override
                    public void writeBody(BufWriter b) {
                        int pos = b.size();
                        int lvtSize = localVariableTypes.size();
                        b.writeU2(localVariableTypes.size());
                        for (LocalVariableType l : localVariableTypes) {
                            if (!l.writeTo(b)) {
                                if (context.deadLabelsOption() == ClassFile.DeadLabelsOption.DROP_DEAD_LABELS) {
                                    lvtSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in local variable type");
                                }
                            }
                        }
                        if (lvtSize < localVariableTypes.size())
                            b.patchInt(pos, 2, lvtSize);
                    }
                };
                attributes.withAttribute(a);
            }
        }

        if (lineNumberWriter != null) {
            attributes.withAttribute(lineNumberWriter);
        }

        content = new UnboundAttribute.AdHocAttribute<>(Attributes.code()) {

            private void writeCounters(boolean codeMatch, BufWriterImpl buf) {
                if (codeMatch) {
                    buf.writeU2(original.maxStack());
                    buf.writeU2(original.maxLocals());
                } else {
                    StackCounter cntr = StackCounter.of(DirectCodeBuilder.this, buf);
                    buf.writeU2(cntr.maxStack());
                    buf.writeU2(cntr.maxLocals());
                }
            }

            private void generateStackMaps(BufWriterImpl buf) throws IllegalArgumentException {
                //new instance of generator immediately calculates maxStack, maxLocals, all frames,
                // patches dead bytecode blocks and removes them from exception table
                StackMapGenerator gen = StackMapGenerator.of(DirectCodeBuilder.this, buf);
                attributes.withAttribute(gen.stackMapTableAttribute());
                buf.writeU2(gen.maxStack());
                buf.writeU2(gen.maxLocals());
            }

            private void tryGenerateStackMaps(boolean codeMatch, BufWriterImpl buf) {
                if (buf.getMajorVersion() >= ClassFile.JAVA_6_VERSION) {
                    try {
                        generateStackMaps(buf);
                    } catch (IllegalArgumentException e) {
                        //failover following JVMS-4.10
                        if (buf.getMajorVersion() == ClassFile.JAVA_6_VERSION) {
                            writeCounters(codeMatch, buf);
                        } else {
                            throw e;
                        }
                    }
                } else {
                    writeCounters(codeMatch, buf);
                }
            }

            @Override
            public void writeBody(BufWriter b) {
                BufWriterImpl buf = (BufWriterImpl) b;
                buf.setLabelContext(DirectCodeBuilder.this);

                int codeLength = curPc();
                if (codeLength == 0 || codeLength >= 65536) {
                    throw new IllegalArgumentException(String.format(
                            "Code length %d is outside the allowed range in %s%s",
                            codeLength,
                            methodInfo.methodName().stringValue(),
                            methodInfo.methodTypeSymbol().displayDescriptor()));
                }

                if (codeAndExceptionsMatch(codeLength)) {
                    switch (context.stackMapsOption()) {
                        case STACK_MAPS_WHEN_REQUIRED -> {
                            attributes.withAttribute(original.findAttribute(Attributes.stackMapTable()).orElse(null));
                            writeCounters(true, buf);
                        }
                        case GENERATE_STACK_MAPS ->
                            generateStackMaps(buf);
                        case DROP_STACK_MAPS ->
                            writeCounters(true, buf);
                    }
                } else {
                    switch (context.stackMapsOption()) {
                        case STACK_MAPS_WHEN_REQUIRED ->
                            tryGenerateStackMaps(false, buf);
                        case GENERATE_STACK_MAPS ->
                            generateStackMaps(buf);
                        case DROP_STACK_MAPS ->
                            writeCounters(false, buf);
                    }
                }

                buf.writeInt(codeLength);
                buf.writeBytes(bytecodesBufWriter);
                writeExceptionHandlers(b);
                attributes.writeTo(b);
                buf.setLabelContext(null);
            }
        };
    }

    private static class DedupLineNumberTableAttribute extends UnboundAttribute.AdHocAttribute<LineNumberTableAttribute> {
        private final BufWriterImpl buf;
        private int lastPc, lastLine, writtenLine;

        public DedupLineNumberTableAttribute(ConstantPoolBuilder constantPool, ClassFileImpl context) {
            super(Attributes.lineNumberTable());
            buf = new BufWriterImpl(constantPool, context);
            lastPc = -1;
            writtenLine = -1;
        }

        private void push() {
            //subsequent identical line numbers are skipped
            if (lastPc >= 0 && lastLine != writtenLine) {
                buf.writeU2(lastPc);
                buf.writeU2(lastLine);
                writtenLine = lastLine;
            }
        }

        //writes are expected ordered by pc in ascending sequence
        public void writeLineNumber(int pc, int lineNo) {
            //for each pc only the latest line number is written
            if (lastPc != pc && lastLine != lineNo) {
                push();
                lastPc = pc;
            }
            lastLine = lineNo;
        }

        @Override
        public void writeBody(BufWriter b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(BufWriter b) {
            b.writeIndex(b.constantPool().utf8Entry(Attributes.NAME_LINE_NUMBER_TABLE));
            push();
            b.writeInt(buf.size() + 2);
            b.writeU2(buf.size() / 4);
            b.writeBytes(buf);
        }
    }

    private boolean codeAndExceptionsMatch(int codeLength) {
        boolean codeAttributesMatch;
        if (original instanceof CodeImpl cai && canWriteDirect(cai.constantPool())) {
            codeAttributesMatch = cai.codeLength == curPc()
                                  && cai.compareCodeBytes(bytecodesBufWriter, 0, codeLength);
            if (codeAttributesMatch) {
                BufWriter bw = new BufWriterImpl(constantPool, context);
                writeExceptionHandlers(bw);
                codeAttributesMatch = cai.classReader.compare(bw, 0, cai.exceptionHandlerPos, bw.size());
            }
        }
        else
            codeAttributesMatch = false;
        return codeAttributesMatch;
    }

    // Writing support

    private record DeferredLabel(int labelPc, int size, int instructionPc, Label label) { }

    private void writeLabelOffset(int nBytes, int instructionPc, Label label) {
        int targetBci = labelToBci(label);
        if (targetBci == -1) {
            int pc = curPc();
            bytecodesBufWriter.writeIntBytes(nBytes, 0);
            if (deferredLabels == null)
                deferredLabels = new ArrayList<>();
            deferredLabels.add(new DeferredLabel(pc, nBytes, instructionPc, label));
        }
        else {
            int branchOffset = targetBci - instructionPc;
            if (nBytes == 2 && (short)branchOffset != branchOffset) throw new LabelOverflowException();
            bytecodesBufWriter.writeIntBytes(nBytes, branchOffset);
        }
    }

    private void processDeferredLabels() {
        if (deferredLabels != null) {
            for (DeferredLabel dl : deferredLabels) {
                int branchOffset = labelToBci(dl.label) - dl.instructionPc;
                if (dl.size == 2 && (short)branchOffset != branchOffset) throw new LabelOverflowException();
                bytecodesBufWriter.patchInt(dl.labelPc, dl.size, branchOffset);
            }
        }
    }

    // Instruction writing

    public void writeBytecode(Opcode opcode) {
        if (opcode.isWide())
            bytecodesBufWriter.writeU1(ClassFile.WIDE);
        bytecodesBufWriter.writeU1(opcode.bytecode() & 0xFF);
    }

    public void writeLocalVar(Opcode opcode, int localVar) {
        writeBytecode(opcode);
        switch (opcode.sizeIfFixed()) {
            case 1 -> { }
            case 2 -> bytecodesBufWriter.writeU1(localVar);
            case 4 -> bytecodesBufWriter.writeU2(localVar);
            default -> throw new IllegalArgumentException("Unexpected instruction size: " + opcode);
        }
    }

    public void writeIncrement(int slot, int val) {
        Opcode opcode = (slot < 256 && val < 128 && val > -127)
                        ? IINC
                        : IINC_W;
        writeBytecode(opcode);
        if (opcode.isWide()) {
            bytecodesBufWriter.writeU2(slot);
            bytecodesBufWriter.writeU2(val);
        } else {
            bytecodesBufWriter.writeU1(slot);
            bytecodesBufWriter.writeU1(val);
        }
    }

    public void writeBranch(Opcode op, Label target) {
        int instructionPc = curPc();
        int targetBci = labelToBci(target);
        //transform short-opcode forward jumps if enforced, and backward jumps if enabled and overflowing
        if (op.sizeIfFixed() == 3 && (targetBci == -1
                                      ? transformFwdJumps
                                      : (transformBackJumps
                                         && targetBci - instructionPc < Short.MIN_VALUE))) {
            if (op == GOTO) {
                writeBytecode(GOTO_W);
                writeLabelOffset(4, instructionPc, target);
            } else if (op == JSR) {
                writeBytecode(JSR_W);
                writeLabelOffset(4, instructionPc, target);
            } else {
                writeBytecode(BytecodeHelpers.reverseBranchOpcode(op));
                Label bypassJump = newLabel();
                writeLabelOffset(2, instructionPc, bypassJump);
                writeBytecode(GOTO_W);
                writeLabelOffset(4, instructionPc + 3, target);
                labelBinding(bypassJump);
            }
        } else {
            writeBytecode(op);
            writeLabelOffset(op.sizeIfFixed() == 3 ? 2 : 4, instructionPc, target);
        }
    }

    public void writeLookupSwitch(Label defaultTarget, List<SwitchCase> cases) {
        int instructionPc = curPc();
        writeBytecode(Opcode.LOOKUPSWITCH);
        int pad = 4 - (curPc() % 4);
        if (pad != 4)
            bytecodesBufWriter.writeIntBytes(pad, 0);
        writeLabelOffset(4, instructionPc, defaultTarget);
        bytecodesBufWriter.writeInt(cases.size());
        cases = new ArrayList<>(cases);
        cases.sort(new Comparator<SwitchCase>() {
            @Override
            public int compare(SwitchCase c1, SwitchCase c2) {
                return Integer.compare(c1.caseValue(), c2.caseValue());
            }
        });
        for (var c : cases) {
            bytecodesBufWriter.writeInt(c.caseValue());
            writeLabelOffset(4, instructionPc, c.target());
        }
    }

    public void writeTableSwitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        int instructionPc = curPc();
        writeBytecode(Opcode.TABLESWITCH);
        int pad = 4 - (curPc() % 4);
        if (pad != 4)
            bytecodesBufWriter.writeIntBytes(pad, 0);
        writeLabelOffset(4, instructionPc, defaultTarget);
        bytecodesBufWriter.writeInt(low);
        bytecodesBufWriter.writeInt(high);
        var caseMap = new HashMap<Integer, Label>(cases.size());
        for (var c : cases) {
            caseMap.put(c.caseValue(), c.target());
        }
        for (long l = low; l<=high; l++) {
            writeLabelOffset(4, instructionPc, caseMap.getOrDefault((int)l, defaultTarget));
        }
    }

    public void writeFieldAccess(Opcode opcode, FieldRefEntry ref) {
        writeBytecode(opcode);
        bytecodesBufWriter.writeIndex(ref);
    }

    public void writeInvokeNormal(Opcode opcode, MemberRefEntry ref) {
        writeBytecode(opcode);
        bytecodesBufWriter.writeIndex(ref);
    }

    public void writeInvokeInterface(Opcode opcode,
                                     InterfaceMethodRefEntry ref,
                                     int count) {
        writeBytecode(opcode);
        bytecodesBufWriter.writeIndex(ref);
        bytecodesBufWriter.writeU1(count);
        bytecodesBufWriter.writeU1(0);
    }

    public void writeInvokeDynamic(InvokeDynamicEntry ref) {
        writeBytecode(Opcode.INVOKEDYNAMIC);
        bytecodesBufWriter.writeIndex(ref);
        bytecodesBufWriter.writeU2(0);
    }

    public void writeNewObject(ClassEntry type) {
        writeBytecode(Opcode.NEW);
        bytecodesBufWriter.writeIndex(type);
    }

    public void writeNewPrimitiveArray(int newArrayCode) {
        writeBytecode(Opcode.NEWARRAY);
        bytecodesBufWriter.writeU1(newArrayCode);
    }

    public void writeNewReferenceArray(ClassEntry type) {
        writeBytecode(Opcode.ANEWARRAY);
        bytecodesBufWriter.writeIndex(type);
    }

    public void writeNewMultidimensionalArray(int dimensions, ClassEntry type) {
        writeBytecode(Opcode.MULTIANEWARRAY);
        bytecodesBufWriter.writeIndex(type);
        bytecodesBufWriter.writeU1(dimensions);
    }

    public void writeTypeCheck(Opcode opcode, ClassEntry type) {
        writeBytecode(opcode);
        bytecodesBufWriter.writeIndex(type);
    }

    public void writeArgumentConstant(Opcode opcode, int value) {
        writeBytecode(opcode);
        if (opcode.sizeIfFixed() == 3) {
            bytecodesBufWriter.writeU2(value);
        } else {
            bytecodesBufWriter.writeU1(value);
        }
    }

    public void writeLoadConstant(Opcode opcode, LoadableConstantEntry value) {
        // Make sure Long and Double have LDC2_W and
        // rewrite to _W if index is > 256
        int index = AbstractPoolEntry.maybeClone(constantPool, value).index();
        Opcode op = opcode;
        if (value instanceof LongEntry || value instanceof DoubleEntry) {
            op = LDC2_W;
        } else if (index >= 256)
            op = LDC_W;

        writeBytecode(op);
        if (op.sizeIfFixed() == 3) {
            bytecodesBufWriter.writeU2(index);
        } else {
            bytecodesBufWriter.writeU1(index);
        }
    }

    @Override
    public Label getLabel(int bci) {
        throw new UnsupportedOperationException("Lookup by BCI not supported by CodeBuilder");
    }

    @Override
    public int labelToBci(Label label) {
        LabelImpl lab = (LabelImpl) label;
        LabelContext context = lab.labelContext();
        if (context == this) {
            return lab.getBCI();
        }
        else if (context == mruParent) {
            return mruParentTable[lab.getBCI()] - 1;
        }
        else if (context instanceof CodeAttribute parent) {
            if (parentMap == null)
                parentMap = new IdentityHashMap<>();
            //critical JDK bootstrap path, cannot use lambda here
            int[] table = parentMap.computeIfAbsent(parent, new Function<CodeAttribute, int[]>() {
                @Override
                public int[] apply(CodeAttribute x) {
                    return new int[parent.codeLength() + 1];
                }
            });

            mruParent = parent;
            mruParentTable = table;
            return mruParentTable[lab.getBCI()] - 1;
        }
        else if (context instanceof BufferedCodeBuilder) {
            // Hijack the label
            return lab.getBCI();
        }
        else {
            throw new IllegalStateException(String.format("Unexpected label context %s in =%s", context, this));
        }
    }

    public void setLineNumber(int lineNo) {
        if (lineNumberWriter == null)
            lineNumberWriter = new DedupLineNumberTableAttribute(constantPool, context);
        lineNumberWriter.writeLineNumber(curPc(), lineNo);
    }

    public void setLabelTarget(Label label) {
        setLabelTarget(label, curPc());
    }

    @Override
    public void setLabelTarget(Label label, int bci) {
        LabelImpl lab = (LabelImpl) label;
        LabelContext context = lab.labelContext();

        if (context == this) {
            if (lab.getBCI() != -1)
                throw new IllegalArgumentException("Setting label target for already-set label");
            lab.setBCI(bci);
        }
        else if (context == mruParent) {
            mruParentTable[lab.getBCI()] = bci + 1;
        }
        else if (context instanceof CodeAttribute parent) {
            if (parentMap == null)
                parentMap = new IdentityHashMap<>();
            int[] table = parentMap.computeIfAbsent(parent, new Function<CodeAttribute, int[]>() {
                @Override
                public int[] apply(CodeAttribute x) {
                    return new int[parent.codeLength() + 1];
                }
            });

            mruParent = parent;
            mruParentTable = table;
            mruParentTable[lab.getBCI()] = bci + 1;
        }
        else if (context instanceof BufferedCodeBuilder) {
            // Hijack the label
            lab.setBCI(bci);
        }
        else {
            throw new IllegalStateException(String.format("Unexpected label context %s in =%s", context, this));
        }
    }

    public void addCharacterRange(CharacterRange element) {
        characterRanges.add(element);
    }

    public void addHandler(ExceptionCatch element) {
        AbstractPseudoInstruction.ExceptionCatchImpl el = (AbstractPseudoInstruction.ExceptionCatchImpl) element;
        ClassEntry type = el.catchTypeEntry();
        if (type != null && !constantPool.canWriteDirect(type.constantPool()))
            el = new AbstractPseudoInstruction.ExceptionCatchImpl(element.handler(), element.tryStart(), element.tryEnd(), AbstractPoolEntry.maybeClone(constantPool, type));
        handlers.add(el);
    }

    public void addLocalVariable(LocalVariable element) {
        localVariables.add(element);
    }

    public void addLocalVariableType(LocalVariableType element) {
        localVariableTypes.add(element);
    }

    @Override
    public String toString() {
        return String.format("CodeBuilder[id=%d]", System.identityHashCode(this));
    }

    //ToDo: consolidate and open all exceptions
    private static final class LabelOverflowException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public LabelOverflowException() {
            super("Label target offset overflow");
        }
    }
}
