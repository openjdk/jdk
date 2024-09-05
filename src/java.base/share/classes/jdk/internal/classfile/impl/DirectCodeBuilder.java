/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ConstantDesc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.classfile.Opcode.*;

import static jdk.internal.classfile.impl.BytecodeHelpers.*;

public final class DirectCodeBuilder
        extends AbstractDirectBuilder<CodeModel>
        implements TerminalCodeBuilder {
    private final List<CharacterRange> characterRanges = new ArrayList<>();
    final List<AbstractPseudoInstruction.ExceptionCatchImpl> handlers = new ArrayList<>();
    private final List<LocalVariable> localVariables = new ArrayList<>();
    private final List<LocalVariableType> localVariableTypes = new ArrayList<>();
    private final boolean transformFwdJumps, transformBackJumps;
    private final Label startLabel, endLabel;
    final MethodInfo methodInfo;
    final BufWriterImpl bytecodesBufWriter;
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

    public static UnboundAttribute<CodeAttribute> build(MethodInfo methodInfo,
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
        this.topLocal = TerminalCodeBuilder.setupTopLocal(methodInfo, original);
    }

    @Override
    public CodeBuilder with(CodeElement element) {
        if (element instanceof AbstractElement ae) {
            ae.writeTo(this);
        } else {
            writeAttribute((CustomAttribute<?>) element);
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

    private UnboundAttribute<CodeAttribute> content = null;

    private void writeExceptionHandlers(BufWriterImpl buf) {
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
                    public void writeBody(BufWriterImpl b) {
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
                    public void writeBody(BufWriterImpl b) {
                        int pos = b.size();
                        int lvSize = localVariables.size();
                        b.writeU2(lvSize);
                        for (LocalVariable l : localVariables) {
                            if (!Util.writeLocalVariable(b, l)) {
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
                    public void writeBody(BufWriterImpl b) {
                        int pos = b.size();
                        int lvtSize = localVariableTypes.size();
                        b.writeU2(localVariableTypes.size());
                        for (LocalVariableType l : localVariableTypes) {
                            if (!Util.writeLocalVariable(b, l)) {
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
                    var originalAttribute = (CodeImpl) original;
                    buf.writeU2(originalAttribute.maxStack());
                    buf.writeU2(originalAttribute.maxLocals());
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
            public void writeBody(BufWriterImpl buf) {
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
                writeExceptionHandlers(buf);
                attributes.writeTo(buf);
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
        public void writeBody(BufWriterImpl b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(BufWriterImpl b) {
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
                var bw = new BufWriterImpl(constantPool, context);
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
        writeBytecode(LOOKUPSWITCH);
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
        writeBytecode(TABLESWITCH);
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
        writeBytecode(INVOKEDYNAMIC);
        bytecodesBufWriter.writeIndex(ref);
        bytecodesBufWriter.writeU2(0);
    }

    public void writeNewObject(ClassEntry type) {
        writeBytecode(NEW);
        bytecodesBufWriter.writeIndex(type);
    }

    public void writeNewPrimitiveArray(int newArrayCode) {
        writeBytecode(NEWARRAY);
        bytecodesBufWriter.writeU1(newArrayCode);
    }

    public void writeNewReferenceArray(ClassEntry type) {
        writeBytecode(ANEWARRAY);
        bytecodesBufWriter.writeIndex(type);
    }

    public void writeNewMultidimensionalArray(int dimensions, ClassEntry type) {
        writeBytecode(MULTIANEWARRAY);
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

    // Fast overrides to avoid intermediate instructions
    // These are helpful for direct class building

    @Override
    public CodeBuilder return_(TypeKind tk) {
        writeBytecode(BytecodeHelpers.returnOpcode(tk));
        return this;
    }

    @Override
    public CodeBuilder storeLocal(TypeKind tk, int slot) {
        writeLocalVar(BytecodeHelpers.storeOpcode(tk, slot), slot);
        return this;
    }

    @Override
    public CodeBuilder loadLocal(TypeKind tk, int slot) {
        writeLocalVar(BytecodeHelpers.loadOpcode(tk, slot), slot);
        return this;
    }

    @Override
    public CodeBuilder invoke(Opcode opcode, MemberRefEntry ref) {
        if (opcode == INVOKEINTERFACE) {
            int slots = Util.parameterSlots(Util.methodTypeSymbol(ref.nameAndType())) + 1;
            writeInvokeInterface(opcode, (InterfaceMethodRefEntry) ref, slots);
        } else {
            writeInvokeNormal(opcode, ref);
        }
        return this;
    }

    @Override
    public CodeBuilder fieldAccess(Opcode opcode, FieldRefEntry ref) {
        writeFieldAccess(opcode, ref);
        return this;
    }

    @Override
    public CodeBuilder arrayLoad(TypeKind tk) {
        writeBytecode(BytecodeHelpers.arrayLoadOpcode(tk));
        return this;
    }

    @Override
    public CodeBuilder arrayStore(TypeKind tk) {
        writeBytecode(BytecodeHelpers.arrayStoreOpcode(tk));
        return this;
    }

    @Override
    public CodeBuilder branch(Opcode op, Label target) {
        writeBranch(op, target);
        return this;
    }

    @Override
    public CodeBuilder nop() {
        writeBytecode(NOP);
        return this;
    }

    @Override
    public CodeBuilder aconst_null() {
        writeBytecode(ACONST_NULL);
        return this;
    }

    @Override
    public CodeBuilder aload(int slot) {
        writeLocalVar(BytecodeHelpers.aload(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder anewarray(ClassEntry entry) {
        writeNewReferenceArray(entry);
        return this;
    }

    @Override
    public CodeBuilder arraylength() {
        writeBytecode(ARRAYLENGTH);
        return this;
    }

    @Override
    public CodeBuilder astore(int slot) {
        writeLocalVar(BytecodeHelpers.astore(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder athrow() {
        writeBytecode(ATHROW);
        return this;
    }

    @Override
    public CodeBuilder bipush(int b) {
        BytecodeHelpers.validateBipush(b);
        writeArgumentConstant(BIPUSH, b);
        return this;
    }

    @Override
    public CodeBuilder checkcast(ClassEntry type) {
        writeTypeCheck(CHECKCAST, type);
        return this;
    }

    @Override
    public CodeBuilder d2f() {
        writeBytecode(D2F);
        return this;
    }

    @Override
    public CodeBuilder d2i() {
        writeBytecode(D2I);
        return this;
    }

    @Override
    public CodeBuilder d2l() {
        writeBytecode(D2L);
        return this;
    }

    @Override
    public CodeBuilder dadd() {
        writeBytecode(DADD);
        return this;
    }

    @Override
    public CodeBuilder dcmpg() {
        writeBytecode(DCMPG);
        return this;
    }

    @Override
    public CodeBuilder dcmpl() {
        writeBytecode(DCMPL);
        return this;
    }

    @Override
    public CodeBuilder dconst_0() {
        writeBytecode(DCONST_0);
        return this;
    }

    @Override
    public CodeBuilder dconst_1() {
        writeBytecode(DCONST_1);
        return this;
    }

    @Override
    public CodeBuilder ddiv() {
        writeBytecode(DDIV);
        return this;
    }

    @Override
    public CodeBuilder dload(int slot) {
        writeLocalVar(BytecodeHelpers.dload(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder dmul() {
        writeBytecode(DMUL);
        return this;
    }

    @Override
    public CodeBuilder dneg() {
        writeBytecode(DNEG);
        return this;
    }

    @Override
    public CodeBuilder drem() {
        writeBytecode(DREM);
        return this;
    }

    @Override
    public CodeBuilder dstore(int slot) {
        writeLocalVar(BytecodeHelpers.dstore(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder dsub() {
        writeBytecode(DSUB);
        return this;
    }

    @Override
    public CodeBuilder dup() {
        writeBytecode(DUP);
        return this;
    }

    @Override
    public CodeBuilder dup2() {
        writeBytecode(DUP2);
        return this;
    }

    @Override
    public CodeBuilder dup2_x1() {
        writeBytecode(DUP2_X1);
        return this;
    }

    @Override
    public CodeBuilder dup2_x2() {
        writeBytecode(DUP2_X2);
        return this;
    }

    @Override
    public CodeBuilder dup_x1() {
        writeBytecode(DUP_X1);
        return this;
    }

    @Override
    public CodeBuilder dup_x2() {
        writeBytecode(DUP_X2);
        return this;
    }

    @Override
    public CodeBuilder f2d() {
        writeBytecode(F2D);
        return this;
    }

    @Override
    public CodeBuilder f2i() {
        writeBytecode(F2I);
        return this;
    }

    @Override
    public CodeBuilder f2l() {
        writeBytecode(F2L);
        return this;
    }

    @Override
    public CodeBuilder fadd() {
        writeBytecode(FADD);
        return this;
    }

    @Override
    public CodeBuilder fcmpg() {
        writeBytecode(FCMPG);
        return this;
    }

    @Override
    public CodeBuilder fcmpl() {
        writeBytecode(FCMPL);
        return this;
    }

    @Override
    public CodeBuilder fconst_0() {
        writeBytecode(FCONST_0);
        return this;
    }

    @Override
    public CodeBuilder fconst_1() {
        writeBytecode(FCONST_1);
        return this;
    }

    @Override
    public CodeBuilder fconst_2() {
        writeBytecode(FCONST_2);
        return this;
    }

    @Override
    public CodeBuilder fdiv() {
        writeBytecode(FDIV);
        return this;
    }

    @Override
    public CodeBuilder fload(int slot) {
        writeLocalVar(BytecodeHelpers.fload(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder fmul() {
        writeBytecode(FMUL);
        return this;
    }

    @Override
    public CodeBuilder fneg() {
        writeBytecode(FNEG);
        return this;
    }

    @Override
    public CodeBuilder frem() {
        writeBytecode(FREM);
        return this;
    }

    @Override
    public CodeBuilder fstore(int slot) {
        writeLocalVar(BytecodeHelpers.fstore(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder fsub() {
        writeBytecode(FSUB);
        return this;
    }

    @Override
    public CodeBuilder i2b() {
        writeBytecode(I2B);
        return this;
    }

    @Override
    public CodeBuilder i2c() {
        writeBytecode(I2C);
        return this;
    }

    @Override
    public CodeBuilder i2d() {
        writeBytecode(I2D);
        return this;
    }

    @Override
    public CodeBuilder i2f() {
        writeBytecode(I2F);
        return this;
    }

    @Override
    public CodeBuilder i2l() {
        writeBytecode(I2L);
        return this;
    }

    @Override
    public CodeBuilder i2s() {
        writeBytecode(I2S);
        return this;
    }

    @Override
    public CodeBuilder iadd() {
        writeBytecode(IADD);
        return this;
    }

    @Override
    public CodeBuilder iand() {
        writeBytecode(IAND);
        return this;
    }

    @Override
    public CodeBuilder iconst_0() {
        writeBytecode(ICONST_0);
        return this;
    }

    @Override
    public CodeBuilder iconst_1() {
        writeBytecode(ICONST_1);
        return this;
    }

    @Override
    public CodeBuilder iconst_2() {
        writeBytecode(ICONST_2);
        return this;
    }

    @Override
    public CodeBuilder iconst_3() {
        writeBytecode(ICONST_3);
        return this;
    }

    @Override
    public CodeBuilder iconst_4() {
        writeBytecode(ICONST_4);
        return this;
    }

    @Override
    public CodeBuilder iconst_5() {
        writeBytecode(ICONST_5);
        return this;
    }

    @Override
    public CodeBuilder iconst_m1() {
        writeBytecode(ICONST_M1);
        return this;
    }

    @Override
    public CodeBuilder idiv() {
        writeBytecode(IDIV);
        return this;
    }

    @Override
    public CodeBuilder iinc(int slot, int val) {
        writeIncrement(slot, val);
        return this;
    }

    @Override
    public CodeBuilder iload(int slot) {
        writeLocalVar(BytecodeHelpers.iload(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder imul() {
        writeBytecode(IMUL);
        return this;
    }

    @Override
    public CodeBuilder ineg() {
        writeBytecode(INEG);
        return this;
    }

    @Override
    public CodeBuilder instanceOf(ClassEntry target) {
        writeTypeCheck(INSTANCEOF, target);
        return this;
    }

    @Override
    public CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        writeInvokeDynamic(ref);
        return this;
    }

    @Override
    public CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        writeInvokeInterface(INVOKEINTERFACE, ref, Util.parameterSlots(ref.typeSymbol()) + 1);
        return this;
    }

    @Override
    public CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        writeInvokeNormal(INVOKESPECIAL, ref);
        return this;
    }

    @Override
    public CodeBuilder invokespecial(MethodRefEntry ref) {
        writeInvokeNormal(INVOKESPECIAL, ref);
        return this;
    }

    @Override
    public CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        writeInvokeNormal(INVOKESTATIC, ref);
        return this;
    }

    @Override
    public CodeBuilder invokestatic(MethodRefEntry ref) {
        writeInvokeNormal(INVOKESTATIC, ref);
        return this;
    }

    @Override
    public CodeBuilder invokevirtual(MethodRefEntry ref) {
        writeInvokeNormal(INVOKEVIRTUAL, ref);
        return this;
    }

    @Override
    public CodeBuilder ior() {
        writeBytecode(IOR);
        return this;
    }

    @Override
    public CodeBuilder irem() {
        writeBytecode(IREM);
        return this;
    }

    @Override
    public CodeBuilder ishl() {
        writeBytecode(ISHL);
        return this;
    }

    @Override
    public CodeBuilder ishr() {
        writeBytecode(ISHR);
        return this;
    }

    @Override
    public CodeBuilder istore(int slot) {
        writeLocalVar(BytecodeHelpers.istore(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder isub() {
        writeBytecode(ISUB);
        return this;
    }

    @Override
    public CodeBuilder iushr() {
        writeBytecode(IUSHR);
        return this;
    }

    @Override
    public CodeBuilder ixor() {
        writeBytecode(IXOR);
        return this;
    }

    @Override
    public CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        writeLookupSwitch(defaultTarget, cases);
        return this;
    }

    @Override
    public CodeBuilder l2d() {
        writeBytecode(L2D);
        return this;
    }

    @Override
    public CodeBuilder l2f() {
        writeBytecode(L2F);
        return this;
    }

    @Override
    public CodeBuilder l2i() {
        writeBytecode(L2I);
        return this;
    }

    @Override
    public CodeBuilder ladd() {
        writeBytecode(LADD);
        return this;
    }

    @Override
    public CodeBuilder land() {
        writeBytecode(LAND);
        return this;
    }

    @Override
    public CodeBuilder lcmp() {
        writeBytecode(LCMP);
        return this;
    }

    @Override
    public CodeBuilder lconst_0() {
        writeBytecode(LCONST_0);
        return this;
    }

    @Override
    public CodeBuilder lconst_1() {
        writeBytecode(LCONST_1);
        return this;
    }

    @Override
    public CodeBuilder ldc(LoadableConstantEntry entry) {
        writeLoadConstant(BytecodeHelpers.ldcOpcode(entry), entry);
        return this;
    }

    @Override
    public CodeBuilder ldiv() {
        writeBytecode(LDIV);
        return this;
    }

    @Override
    public CodeBuilder lload(int slot) {
        writeLocalVar(BytecodeHelpers.lload(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder lmul() {
        writeBytecode(LMUL);
        return this;
    }

    @Override
    public CodeBuilder lneg() {
        writeBytecode(LNEG);
        return this;
    }

    @Override
    public CodeBuilder lor() {
        writeBytecode(LOR);
        return this;
    }

    @Override
    public CodeBuilder lrem() {
        writeBytecode(LREM);
        return this;
    }

    @Override
    public CodeBuilder lshl() {
        writeBytecode(LSHL);
        return this;
    }

    @Override
    public CodeBuilder lshr() {
        writeBytecode(LSHR);
        return this;
    }

    @Override
    public CodeBuilder lstore(int slot) {
        writeLocalVar(BytecodeHelpers.lstore(slot), slot);
        return this;
    }

    @Override
    public CodeBuilder lsub() {
        writeBytecode(LSUB);
        return this;
    }

    @Override
    public CodeBuilder lushr() {
        writeBytecode(LUSHR);
        return this;
    }

    @Override
    public CodeBuilder lxor() {
        writeBytecode(LXOR);
        return this;
    }

    @Override
    public CodeBuilder monitorenter() {
        writeBytecode(MONITORENTER);
        return this;
    }

    @Override
    public CodeBuilder monitorexit() {
        writeBytecode(MONITOREXIT);
        return this;
    }

    @Override
    public CodeBuilder multianewarray(ClassEntry array, int dims) {
        writeNewMultidimensionalArray(dims, array);
        return this;
    }

    @Override
    public CodeBuilder new_(ClassEntry clazz) {
        writeNewObject(clazz);
        return this;
    }

    @Override
    public CodeBuilder newarray(TypeKind typeKind) {
        int atype = typeKind.newarrayCode(); // implicit null check
        if (atype < 0)
            throw new IllegalArgumentException("Illegal component type: ".concat(typeKind.upperBound().displayName()));
        writeNewPrimitiveArray(atype);
        return this;
    }

    @Override
    public CodeBuilder pop() {
        writeBytecode(POP);
        return this;
    }

    @Override
    public CodeBuilder pop2() {
        writeBytecode(POP2);
        return this;
    }

    @Override
    public CodeBuilder sipush(int s) {
        BytecodeHelpers.validateSipush(s);
        writeArgumentConstant(SIPUSH, s);
        return this;
    }

    @Override
    public CodeBuilder swap() {
        writeBytecode(SWAP);
        return this;
    }

    @Override
    public CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        writeTableSwitch(low, high, defaultTarget, cases);
        return this;
    }
}
