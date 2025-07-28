/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.BytecodeHelpers.*;
import static jdk.internal.classfile.impl.RawBytecodeHelper.*;

public final class DirectCodeBuilder
        extends AbstractDirectBuilder<CodeModel>
        implements TerminalCodeBuilder {
    private static final CharacterRange[] EMPTY_CHARACTER_RANGE = {};
    private static final LocalVariable[] EMPTY_LOCAL_VARIABLE_ARRAY = {};
    private static final LocalVariableType[] EMPTY_LOCAL_VARIABLE_TYPE_ARRAY = {};
    private static final DeferredLabel[] EMPTY_DEFERRED_LABEL_ARRAY = {};

    final List<AbstractPseudoInstruction.ExceptionCatchImpl> handlers = new ArrayList<>();
    private CharacterRange[] characterRanges = EMPTY_CHARACTER_RANGE;
    private LocalVariable[] localVariables = EMPTY_LOCAL_VARIABLE_ARRAY;
    private LocalVariableType[] localVariableTypes = EMPTY_LOCAL_VARIABLE_TYPE_ARRAY;
    private int characterRangesCount = 0;
    private int localVariablesCount = 0;
    private int localVariableTypesCount = 0;
    private final boolean transformDeferredJumps, transformKnownJumps;
    private final Label startLabel, endLabel;
    final MethodInfo methodInfo;
    final BufWriterImpl bytecodesBufWriter;
    private CodeAttribute mruParent;
    private int[] mruParentTable;
    private Map<CodeAttribute, int[]> parentMap;
    private DedupLineNumberTableAttribute lineNumberWriter;
    private int topLocal;

    private DeferredLabel[] deferredLabels = EMPTY_DEFERRED_LABEL_ARRAY;
    private int deferredLabelsCount = 0;

    private int maxStackHint = -1;
    private int maxLocalsHint = -1;

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
            if (context.fixShortJumps()) {
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
                              boolean transformDeferredJumps) {
        super(constantPool, context);
        setOriginal(original);
        this.methodInfo = methodInfo;
        this.transformDeferredJumps = transformDeferredJumps;
        this.transformKnownJumps = context.fixShortJumps();
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
            writeAttribute((CustomAttribute<?>) requireNonNull(element));
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

    public static void withMaxs(CodeBuilder cob, int stacks, int locals) {
        var dcb = (DirectCodeBuilder) cob;
        dcb.maxStackHint = stacks;
        dcb.maxLocalsHint = locals;
    }

    private UnboundAttribute<CodeAttribute> content = null;

    private void writeExceptionHandlers(BufWriterImpl buf) {
        int pos = buf.size();
        int handlersSize = handlers.size();
        buf.writeU2(handlersSize);
        if (handlersSize > 0) {
            writeExceptionHandlers(buf, pos);
        }
    }

    private void writeExceptionHandlers(BufWriterImpl buf, int pos) {
        int handlersSize = handlers.size();
        for (AbstractPseudoInstruction.ExceptionCatchImpl h : handlers) {
            int startPc = labelToBci(h.tryStart());
            int endPc = labelToBci(h.tryEnd());
            int handlerPc = labelToBci(h.handler());
            if (startPc == -1 || endPc == -1 || handlerPc == -1) {
                if (context.dropDeadLabels()) {
                    handlersSize--;
                } else {
                    throw new IllegalArgumentException("Unbound label in exception handler");
                }
            } else {
                buf.writeU2U2U2(startPc, endPc, handlerPc);
                buf.writeIndexOrZero(h.catchTypeEntry());
            }
        }
        if (handlersSize < handlers.size())
            buf.patchU2(pos, handlersSize);
    }

    private void buildContent() {
        if (content != null) return;
        setLabelTarget(endLabel);

        // Backfill branches for which Label didn't have position yet
        processDeferredLabels();

        if (context.passDebugElements()) {
            if (characterRangesCount > 0) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.characterRangeTable()) {

                    @Override
                    public void writeBody(BufWriterImpl b) {
                        int pos = b.size();
                        int crSize = characterRangesCount;
                        b.writeU2(crSize);
                        for (int i = 0; i < characterRangesCount; i++) {
                            CharacterRange cr = characterRanges[i];
                            var start = labelToBci(cr.startScope());
                            var end = labelToBci(cr.endScope());
                            if (start == -1 || end == -1) {
                                if (context.dropDeadLabels()) {
                                    crSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in character range");
                                }
                            } else {
                                b.writeU2U2(start, end - 1);
                                b.writeIntInt(cr.characterRangeStart(), cr.characterRangeEnd());
                                b.writeU2(cr.flags());
                            }
                        }
                        if (crSize < characterRangesCount)
                            b.patchU2(pos, crSize);
                    }

                    @Override
                    public Utf8Entry attributeName() {
                        return constantPool.utf8Entry(Attributes.NAME_CHARACTER_RANGE_TABLE);
                    }
                };
                attributes.withAttribute(a);
            }

            if (localVariablesCount > 0) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTable()) {
                    @Override
                    public void writeBody(BufWriterImpl b) {
                        int pos = b.size();
                        int lvSize = localVariablesCount;
                        b.writeU2(lvSize);
                        for (int i = 0; i < localVariablesCount; i++) {
                            LocalVariable l = localVariables[i];
                            if (!Util.writeLocalVariable(b, l)) {
                                if (context.dropDeadLabels()) {
                                    lvSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in local variable type");
                                }
                            }
                        }
                        if (lvSize < localVariablesCount)
                            b.patchU2(pos, lvSize);
                    }

                    @Override
                    public Utf8Entry attributeName() {
                        return constantPool.utf8Entry(Attributes.NAME_LOCAL_VARIABLE_TABLE);
                    }
                };
                attributes.withAttribute(a);
            }

            if (localVariableTypesCount > 0) {
                Attribute<?> a = new UnboundAttribute.AdHocAttribute<>(Attributes.localVariableTypeTable()) {
                    @Override
                    public void writeBody(BufWriterImpl b) {
                        int pos = b.size();
                        int lvtSize = localVariableTypesCount;
                        b.writeU2(lvtSize);
                        for (int i = 0; i < localVariableTypesCount; i++) {
                            LocalVariableType l = localVariableTypes[i];
                            if (!Util.writeLocalVariable(b, l)) {
                                if (context.dropDeadLabels()) {
                                    lvtSize--;
                                } else {
                                    throw new IllegalArgumentException("Unbound label in local variable type");
                                }
                            }
                        }
                        if (lvtSize < localVariableTypesCount)
                            b.patchU2(pos, lvtSize);
                    }

                    @Override
                    public Utf8Entry attributeName() {
                        return constantPool.utf8Entry(Attributes.NAME_LOCAL_VARIABLE_TYPE_TABLE);
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
                    buf.writeU2U2(originalAttribute.maxStack(), originalAttribute.maxLocals());
                } else if (maxLocalsHint >= 0 && maxStackHint >= 0) {
                    buf.writeU2U2(maxStackHint, maxLocalsHint);
                } else {
                    StackCounter cntr = StackCounter.of(DirectCodeBuilder.this, buf);
                    buf.writeU2U2(cntr.maxStack(), cntr.maxLocals());
                }
            }

            private void generateStackMaps(BufWriterImpl buf) throws IllegalArgumentException {
                //new instance of generator immediately calculates maxStack, maxLocals, all frames,
                // patches dead bytecode blocks and removes them from exception table
                var dcb = DirectCodeBuilder.this;
                StackMapGenerator gen = StackMapGenerator.of(dcb, buf);
                dcb.attributes.withAttribute(gen.stackMapTableAttribute());
                buf.writeU2U2(gen.maxStack(), gen.maxLocals());
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
                DirectCodeBuilder dcb = DirectCodeBuilder.this;

                int codeLength = curPc();
                if (codeLength == 0 || codeLength >= 65536) {
                    throw new IllegalArgumentException(String.format(
                            "Code length %d is outside the allowed range in %s%s",
                            codeLength,
                            dcb.methodInfo.methodName().stringValue(),
                            dcb.methodInfo.methodTypeSymbol().displayDescriptor()));
                }

                boolean codeMatch = dcb.original != null && codeAndExceptionsMatch(codeLength);
                buf.setLabelContext(dcb, codeMatch);
                var context = dcb.context;
                if (context.stackMapsWhenRequired()) {
                    if (codeMatch) {
                        dcb.attributes.withAttribute(dcb.original.findAttribute(Attributes.stackMapTable()).orElse(null));
                        writeCounters(true, buf);
                    } else {
                        tryGenerateStackMaps(false, buf);
                    }
                } else if (context.generateStackMaps()) {
                    generateStackMaps(buf);
                } else if (context.dropStackMaps()) {
                    writeCounters(codeMatch, buf);
                }

                buf.writeInt(codeLength);
                buf.writeBytes(dcb.bytecodesBufWriter);
                dcb.writeExceptionHandlers(buf);
                dcb.attributes.writeTo(buf);
                buf.setLabelContext(null, false);
            }

            @Override
            public Utf8Entry attributeName() {
                return constantPool.utf8Entry(Attributes.NAME_CODE);
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
                buf.writeU2U2(lastPc, lastLine);
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

        @Override
        public Utf8Entry attributeName() {
            return buf.constantPool().utf8Entry(Attributes.NAME_LINE_NUMBER_TABLE);
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

    private void processDeferredLabels() {
        for (int i = 0; i < deferredLabelsCount; i++) {
            DeferredLabel dl = deferredLabels[i];
            int branchOffset = labelToBci(dl.label) - dl.instructionPc;
            if (dl.size == 2) {
                if ((short) branchOffset != branchOffset) throw new LabelOverflowException();
                bytecodesBufWriter.patchU2(dl.labelPc, branchOffset);
            } else {
                assert dl.size == 4;
                bytecodesBufWriter.patchInt(dl.labelPc, branchOffset);
            }
        }
    }

    // Instruction writing

    public void writeBytecode(Opcode opcode) {
        assert !opcode.isWide();
        bytecodesBufWriter.writeU1(opcode.bytecode());
    }

    // Instruction version, refer to opcode, trusted
    public void writeLocalVar(Opcode opcode, int slot) {
        if (opcode.isWide()) {
            bytecodesBufWriter.writeU2U2(opcode.bytecode(), slot);
        } else {
            bytecodesBufWriter.writeU1U1(opcode.bytecode(), slot);
        }
    }

    // local var access, not a trusted write method, needs slot validation
    private void localAccess(int bytecode, int slot) {
        if ((slot & ~0xFF) == 0) {
            bytecodesBufWriter.writeU1U1(bytecode, slot);
        } else {
            BytecodeHelpers.validateSlot(slot);
            bytecodesBufWriter.writeU1U1U2(WIDE, bytecode, slot);
        }
    }

    public void writeIncrement(boolean wide, int slot, int val) {
        if (wide) {
            bytecodesBufWriter.writeU2U2U2((WIDE << 8) | IINC, slot, val);
        } else {
            bytecodesBufWriter.writeU1U1U1(IINC, slot, val);
        }
    }

    public void writeBranch(Opcode op, Label target) {
        if (op.sizeIfFixed() == 3) {
            writeShortJump(op.bytecode(), target);
        } else {
            writeLongJump(op.bytecode(), target);
        }
    }

    private void writeLongLabelOffset(int instructionPc, Label label) {
        int targetBci = labelToBci(label);

        // algebraic union of jump | (instructionPc, target), distinguished by null == target.
        int jumpOrInstructionPc;
        Label nullOrTarget;
        if (targetBci == -1) {
            jumpOrInstructionPc = instructionPc;
            nullOrTarget = label;
        } else {
            jumpOrInstructionPc = targetBci - instructionPc;
            nullOrTarget = null;
        }

        writeParsedLongLabel(jumpOrInstructionPc, nullOrTarget);
    }

    private void writeShortJump(int bytecode, Label target) {
        int targetBci = labelToBci(target); // implicit null check
        int instructionPc = curPc();

        // algebraic union of jump | (instructionPc, target), distinguished by null == target.
        int jumpOrInstructionPc;
        Label nullOrTarget;
        if (targetBci == -1) {
            jumpOrInstructionPc = instructionPc;
            nullOrTarget = target;
        } else {
            jumpOrInstructionPc = targetBci - instructionPc;
            nullOrTarget = null;
        }

        //transform short-opcode forward jumps if enforced, and backward jumps if enabled and overflowing
        if (transformDeferredJumps || transformKnownJumps && nullOrTarget == null && jumpOrInstructionPc < Short.MIN_VALUE) {
            fixShortJump(bytecode, jumpOrInstructionPc, nullOrTarget);
        } else {
            bytecodesBufWriter.writeU1(bytecode);
            writeParsedShortLabel(jumpOrInstructionPc, nullOrTarget);
        }
    }

    private void writeLongJump(int bytecode, Label target) {
        Objects.requireNonNull(target); // before any write
        int instructionPc = curPc();
        bytecodesBufWriter.writeU1(bytecode);
        writeLongLabelOffset(instructionPc, target);
    }

    private void fixShortJump(int bytecode, int jumpOrInstructionPc, Label nullOrTarget) {
        if (bytecode == GOTO) {
            bytecodesBufWriter.writeU1(GOTO_W);
            writeParsedLongLabel(jumpOrInstructionPc, nullOrTarget);
        } else if (bytecode == JSR) {
            bytecodesBufWriter.writeU1(JSR_W);
            writeParsedLongLabel(jumpOrInstructionPc, nullOrTarget);
        } else {
            bytecodesBufWriter.writeU1U2(
                    BytecodeHelpers.reverseBranchOpcode(bytecode),   // u1
                    8); // u1 + s2 + u1 + s4                         // s2
            bytecodesBufWriter.writeU1(GOTO_W);                      // u1
            if (nullOrTarget == null) {
                jumpOrInstructionPc -= 3; // jump -= 3;
            } else {
                jumpOrInstructionPc += 3; // instructionPc += 3;
            }
            writeParsedLongLabel(jumpOrInstructionPc, nullOrTarget); // s4
        }
    }

    private void writeParsedShortLabel(int jumpOrInstructionPc, Label nullOrTarget) {
        if (nullOrTarget == null) {
            if ((short) jumpOrInstructionPc != jumpOrInstructionPc)
                throw new LabelOverflowException();
            bytecodesBufWriter.writeU2(jumpOrInstructionPc);
        } else {
            int pc = bytecodesBufWriter.skip(2);
            addLabel(new DeferredLabel(pc, 2, jumpOrInstructionPc, nullOrTarget));
        }
    }

    private void writeParsedLongLabel(int jumpOrInstructionPc, Label nullOrTarget) {
        if (nullOrTarget == null) {
            bytecodesBufWriter.writeInt(jumpOrInstructionPc);
        } else {
            int pc = bytecodesBufWriter.skip(4);
            addLabel(new DeferredLabel(pc, 4, jumpOrInstructionPc, nullOrTarget));
        }
    }

    public void writeLookupSwitch(Label defaultTarget, List<SwitchCase> cases) {
        cases = new ArrayList<>(cases); // cases may be untrusted
        for (var each : cases) {
            Objects.requireNonNull(each); // single null case may exist
        }
        cases.sort(new Comparator<>() {
            @Override
            public int compare(SwitchCase c1, SwitchCase c2) {
                return Integer.compare(c1.caseValue(), c2.caseValue());
            }
        });
        // validation end
        int instructionPc = curPc();
        bytecodesBufWriter.writeU1(LOOKUPSWITCH);
        int pad = 4 - (curPc() % 4);
        if (pad != 4)
            bytecodesBufWriter.skip(pad); // padding content can be anything
        writeLongLabelOffset(instructionPc, defaultTarget);
        bytecodesBufWriter.writeInt(cases.size());
        for (var c : cases) {
            bytecodesBufWriter.writeInt(c.caseValue());
            var target = c.target();
            writeLongLabelOffset(instructionPc, target);
        }
    }

    public void writeTableSwitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        var caseMap = new HashMap<Integer, Label>(cases.size()); // cases may be untrusted
        for (var c : cases) {
            caseMap.put(c.caseValue(), c.target());
        }
        // validation end
        int instructionPc = curPc();
        bytecodesBufWriter.writeU1(TABLESWITCH);
        int pad = 4 - (curPc() % 4);
        if (pad != 4)
            bytecodesBufWriter.skip(pad); // padding content can be anything
        writeLongLabelOffset(instructionPc, defaultTarget);
        bytecodesBufWriter.writeIntInt(low, high);
        for (long l = low; l<=high; l++) {
            var target = caseMap.getOrDefault((int)l, defaultTarget);
            writeLongLabelOffset(instructionPc, target);
        }
    }

    public void writeFieldAccess(Opcode opcode, FieldRefEntry ref) {
        bytecodesBufWriter.writeIndex(opcode.bytecode(), ref);
    }

    public void writeInvokeNormal(Opcode opcode, MemberRefEntry ref) {
        bytecodesBufWriter.writeIndex(opcode.bytecode(), ref);
    }

    public void writeInvokeInterface(Opcode opcode,
                                     InterfaceMethodRefEntry ref,
                                     int count) {
        bytecodesBufWriter.writeIndex(opcode.bytecode(), ref);
        bytecodesBufWriter.writeU1U1(count, 0);
    }

    public void writeInvokeDynamic(InvokeDynamicEntry ref) {
        bytecodesBufWriter.writeU1U2U2(INVOKEDYNAMIC, bytecodesBufWriter.cpIndex(ref), 0);
    }

    public void writeNewObject(ClassEntry type) {
        bytecodesBufWriter.writeIndex(NEW, type);
    }

    public void writeNewPrimitiveArray(int newArrayCode) {
        bytecodesBufWriter.writeU1U1(NEWARRAY, newArrayCode);
    }

    public void writeNewReferenceArray(ClassEntry type) {
        bytecodesBufWriter.writeIndex(ANEWARRAY, type);
    }

    public void writeNewMultidimensionalArray(int dimensions, ClassEntry type) {
        bytecodesBufWriter.writeIndex(MULTIANEWARRAY, type);
        bytecodesBufWriter.writeU1(dimensions);
    }

    public void writeTypeCheck(Opcode opcode, ClassEntry type) {
        bytecodesBufWriter.writeIndex(opcode.bytecode(), type);
    }

    public void writeArgumentConstant(Opcode opcode, int value) {
        if (opcode.sizeIfFixed() == 3) {
            bytecodesBufWriter.writeU1U2(opcode.bytecode(), value);
        } else {
            bytecodesBufWriter.writeU1U1(opcode.bytecode(), value);
        }
    }

    // value may not be writable to this constant pool
    public void writeAdaptLoadConstant(Opcode opcode, LoadableConstantEntry value) {
        var pe = AbstractPoolEntry.maybeClone(constantPool, value);
        int index = pe.index();
        if (pe != value && opcode != Opcode.LDC2_W) {
            // rewrite ldc/ldc_w if external entry; ldc2_w never needs rewrites
            opcode = index <= 0xFF ? Opcode.LDC : Opcode.LDC_W;
        }

        writeDirectLoadConstant(opcode, pe);
    }

    // the loadable entry is writable to this constant pool
    public void writeDirectLoadConstant(Opcode opcode, LoadableConstantEntry pe) {
        assert !opcode.isWide() && canWriteDirect(pe.constantPool());
        int index = pe.index();
        if (opcode.sizeIfFixed() == 3) {
            bytecodesBufWriter.writeU1U2(opcode.bytecode(), index);
        } else {
            bytecodesBufWriter.writeU1U1(opcode.bytecode(), index);
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
        return labelToBci(context, lab);
    }

    private int labelToBci(LabelContext context, LabelImpl lab) {
        if (context == mruParent) {
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
        if (lab.labelContext() == this) {
            if (lab.getBCI() != -1)
                throw new IllegalArgumentException("Setting label target for already-set label");
            lab.setBCI(bci);
        } else {
            setLabelTarget(lab, bci);
        }
    }

    private void setLabelTarget(LabelImpl lab, int bci) {
        LabelContext context = lab.labelContext();
        if (context == mruParent) {
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
            table[lab.getBCI()] = bci + 1;
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
        if (characterRangesCount >= characterRanges.length) {
            int newCapacity = characterRangesCount + 8;
            this.characterRanges = Arrays.copyOf(characterRanges, newCapacity);
        }
        characterRanges[characterRangesCount++] = element;
    }

    public void addLabel(DeferredLabel label) {
        if (deferredLabelsCount >= deferredLabels.length) {
            int newCapacity = deferredLabelsCount + 8;
            this.deferredLabels = Arrays.copyOf(deferredLabels, newCapacity);
        }
        deferredLabels[deferredLabelsCount++] = label;
    }

    public void addHandler(ExceptionCatch element) {
        AbstractPseudoInstruction.ExceptionCatchImpl el = (AbstractPseudoInstruction.ExceptionCatchImpl) element;
        ClassEntry type = el.catchTypeEntry();
        if (type != null && !constantPool.canWriteDirect(type.constantPool()))
            el = new AbstractPseudoInstruction.ExceptionCatchImpl(element.handler(), element.tryStart(), element.tryEnd(), AbstractPoolEntry.maybeClone(constantPool, type));
        handlers.add(el);
    }

    public void addLocalVariable(LocalVariable element) {
        if (localVariablesCount >= localVariables.length) {
            int newCapacity = localVariablesCount + 8;
            this.localVariables = Arrays.copyOf(localVariables, newCapacity);
        }
        localVariables[localVariablesCount++] = element;
    }

    public void addLocalVariableType(LocalVariableType element) {
        if (localVariableTypesCount >= localVariableTypes.length) {
            int newCapacity = localVariableTypesCount + 8;
            this.localVariableTypes = Arrays.copyOf(localVariableTypes, newCapacity);
        }
        localVariableTypes[localVariableTypesCount++] = element;
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
    public CodeBuilder return_() {
        bytecodesBufWriter.writeU1(RETURN);
        return this;
    }

    @Override
    public CodeBuilder return_(TypeKind tk) {
        bytecodesBufWriter.writeU1(returnBytecode(tk));
        return this;
    }

    @Override
    public CodeBuilder storeLocal(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN
                           -> istore(slot);
            case LONG      -> lstore(slot);
            case DOUBLE    -> dstore(slot);
            case FLOAT     -> fstore(slot);
            case REFERENCE -> astore(slot);
            case VOID      -> throw new IllegalArgumentException("void");
        };
    }

    @Override
    public CodeBuilder labelBinding(Label label) {
        setLabelTarget(label, curPc());
        return this;
    }

    @Override
    public CodeBuilder loadLocal(TypeKind tk, int slot) {
        return switch (tk) {
            case INT, SHORT, BYTE, CHAR, BOOLEAN
                           -> iload(slot);
            case LONG      -> lload(slot);
            case DOUBLE    -> dload(slot);
            case FLOAT     -> fload(slot);
            case REFERENCE -> aload(slot);
            case VOID      -> throw new IllegalArgumentException("void");
        };
    }

    @Override
    public CodeBuilder invoke(Opcode opcode, MemberRefEntry ref) {
        if (opcode == Opcode.INVOKEINTERFACE) {
            int slots = Util.parameterSlots(Util.methodTypeSymbol(ref.type())) + 1;
            writeInvokeInterface(opcode, (InterfaceMethodRefEntry) ref, slots);
        } else {
            Util.checkKind(opcode, Opcode.Kind.INVOKE);
            writeInvokeNormal(opcode, ref);
        }
        return this;
    }

    @Override
    public CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type) {
        bytecodesBufWriter.writeIndex(INVOKESPECIAL, constantPool().methodRefEntry(owner, name, type));
        return this;
    }

    @Override
    public CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type) {
        bytecodesBufWriter.writeIndex(INVOKESTATIC, constantPool().methodRefEntry(owner, name, type));
        return this;
    }

    @Override
    public CodeBuilder invokevirtual(ClassDesc owner, String name, MethodTypeDesc type) {
        bytecodesBufWriter.writeIndex(INVOKEVIRTUAL, constantPool().methodRefEntry(owner, name, type));
        return this;
    }

    @Override
    public CodeBuilder getfield(ClassDesc owner, String name, ClassDesc type) {
        bytecodesBufWriter.writeIndex(GETFIELD, constantPool().fieldRefEntry(owner, name, type));
        return this;
    }

    @Override
    public CodeBuilder fieldAccess(Opcode opcode, FieldRefEntry ref) {
        Util.checkKind(opcode, Opcode.Kind.FIELD_ACCESS);
        writeFieldAccess(opcode, ref);
        return this;
    }

    @Override
    public CodeBuilder arrayLoad(TypeKind tk) {
        bytecodesBufWriter.writeU1(BytecodeHelpers.arrayLoadBytecode(tk));
        return this;
    }

    @Override
    public CodeBuilder arrayStore(TypeKind tk) {
        bytecodesBufWriter.writeU1(BytecodeHelpers.arrayStoreBytecode(tk));
        return this;
    }

    @Override
    public CodeBuilder branch(Opcode op, Label target) {
        Util.checkKind(op, Opcode.Kind.BRANCH);
        writeBranch(op, target);
        return this;
    }

    @Override
    public CodeBuilder nop() {
        bytecodesBufWriter.writeU1(NOP);
        return this;
    }

    @Override
    public CodeBuilder aconst_null() {
        bytecodesBufWriter.writeU1(ACONST_NULL);
        return this;
    }

    @Override
    public CodeBuilder aload(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(ALOAD_0 + slot);
        } else {
            localAccess(ALOAD, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder anewarray(ClassEntry entry) {
        writeNewReferenceArray(entry);
        return this;
    }

    @Override
    public CodeBuilder arraylength() {
        bytecodesBufWriter.writeU1(ARRAYLENGTH);
        return this;
    }

    @Override
    public CodeBuilder areturn() {
        bytecodesBufWriter.writeU1(ARETURN);
        return this;
    }

    @Override
    public CodeBuilder astore(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(ASTORE_0 + slot);
        } else {
            localAccess(ASTORE, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder athrow() {
        bytecodesBufWriter.writeU1(ATHROW);
        return this;
    }

    @Override
    public CodeBuilder bipush(int b) {
        BytecodeHelpers.validateBipush(b);
        bytecodesBufWriter.writeU1U1(BIPUSH, b);
        return this;
    }

    @Override
    public CodeBuilder checkcast(ClassEntry type) {
        bytecodesBufWriter.writeIndex(CHECKCAST, type);
        return this;
    }

    @Override
    public CodeBuilder d2f() {
        bytecodesBufWriter.writeU1(D2F);
        return this;
    }

    @Override
    public CodeBuilder d2i() {
        bytecodesBufWriter.writeU1(D2I);
        return this;
    }

    @Override
    public CodeBuilder d2l() {
        bytecodesBufWriter.writeU1(D2L);
        return this;
    }

    @Override
    public CodeBuilder dadd() {
        bytecodesBufWriter.writeU1(DADD);
        return this;
    }

    @Override
    public CodeBuilder dcmpg() {
        bytecodesBufWriter.writeU1(DCMPG);
        return this;
    }

    @Override
    public CodeBuilder dcmpl() {
        bytecodesBufWriter.writeU1(DCMPL);
        return this;
    }

    @Override
    public CodeBuilder dconst_0() {
        bytecodesBufWriter.writeU1(DCONST_0);
        return this;
    }

    @Override
    public CodeBuilder dconst_1() {
        bytecodesBufWriter.writeU1(DCONST_1);
        return this;
    }

    @Override
    public CodeBuilder ddiv() {
        bytecodesBufWriter.writeU1(DDIV);
        return this;
    }

    @Override
    public CodeBuilder dload(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(DLOAD_0 + slot);
        } else {
            localAccess(DLOAD, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder dmul() {
        bytecodesBufWriter.writeU1(DMUL);
        return this;
    }

    @Override
    public CodeBuilder dneg() {
        bytecodesBufWriter.writeU1(DNEG);
        return this;
    }

    @Override
    public CodeBuilder drem() {
        bytecodesBufWriter.writeU1(DREM);
        return this;
    }

    @Override
    public CodeBuilder dreturn() {
        bytecodesBufWriter.writeU1(DRETURN);
        return this;
    }

    @Override
    public CodeBuilder dstore(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(DSTORE_0 + slot);
        } else {
            localAccess(DSTORE, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder dsub() {
        bytecodesBufWriter.writeU1(DSUB);
        return this;
    }

    @Override
    public CodeBuilder dup() {
        bytecodesBufWriter.writeU1(DUP);
        return this;
    }

    @Override
    public CodeBuilder dup2() {
        bytecodesBufWriter.writeU1(DUP2);
        return this;
    }

    @Override
    public CodeBuilder dup2_x1() {
        bytecodesBufWriter.writeU1(DUP2_X1);
        return this;
    }

    @Override
    public CodeBuilder dup2_x2() {
        bytecodesBufWriter.writeU1(DUP2_X2);
        return this;
    }

    @Override
    public CodeBuilder dup_x1() {
        bytecodesBufWriter.writeU1(DUP_X1);
        return this;
    }

    @Override
    public CodeBuilder dup_x2() {
        bytecodesBufWriter.writeU1(DUP_X2);
        return this;
    }

    @Override
    public CodeBuilder f2d() {
        bytecodesBufWriter.writeU1(F2D);
        return this;
    }

    @Override
    public CodeBuilder f2i() {
        bytecodesBufWriter.writeU1(F2I);
        return this;
    }

    @Override
    public CodeBuilder f2l() {
        bytecodesBufWriter.writeU1(F2L);
        return this;
    }

    @Override
    public CodeBuilder fadd() {
        bytecodesBufWriter.writeU1(FADD);
        return this;
    }

    @Override
    public CodeBuilder fcmpg() {
        bytecodesBufWriter.writeU1(FCMPG);
        return this;
    }

    @Override
    public CodeBuilder fcmpl() {
        bytecodesBufWriter.writeU1(FCMPL);
        return this;
    }

    @Override
    public CodeBuilder fconst_0() {
        bytecodesBufWriter.writeU1(FCONST_0);
        return this;
    }

    @Override
    public CodeBuilder fconst_1() {
        bytecodesBufWriter.writeU1(FCONST_1);
        return this;
    }

    @Override
    public CodeBuilder fconst_2() {
        bytecodesBufWriter.writeU1(FCONST_2);
        return this;
    }

    @Override
    public CodeBuilder fdiv() {
        bytecodesBufWriter.writeU1(FDIV);
        return this;
    }

    @Override
    public CodeBuilder fload(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(FLOAD_0 + slot);
        } else {
            localAccess(FLOAD, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder fmul() {
        bytecodesBufWriter.writeU1(FMUL);
        return this;
    }

    @Override
    public CodeBuilder fneg() {
        bytecodesBufWriter.writeU1(FNEG);
        return this;
    }

    @Override
    public CodeBuilder frem() {
        bytecodesBufWriter.writeU1(FREM);
        return this;
    }

    @Override
    public CodeBuilder freturn() {
        bytecodesBufWriter.writeU1(FRETURN);
        return this;
    }

    @Override
    public CodeBuilder fstore(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(FSTORE_0 + slot);
        } else {
            localAccess(FSTORE, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder fsub() {
        bytecodesBufWriter.writeU1(FSUB);
        return this;
    }

    @Override
    public CodeBuilder getstatic(ClassDesc owner, String name, ClassDesc type) {
        bytecodesBufWriter.writeIndex(GETSTATIC, constantPool().fieldRefEntry(owner, name, type));
        return this;
    }

    @Override
    public CodeBuilder goto_(Label target) {
        writeShortJump(GOTO, target);
        return this;
    }

    @Override
    public CodeBuilder i2b() {
        bytecodesBufWriter.writeU1(I2B);
        return this;
    }

    @Override
    public CodeBuilder i2c() {
        bytecodesBufWriter.writeU1(I2C);
        return this;
    }

    @Override
    public CodeBuilder i2d() {
        bytecodesBufWriter.writeU1(I2D);
        return this;
    }

    @Override
    public CodeBuilder i2f() {
        bytecodesBufWriter.writeU1(I2F);
        return this;
    }

    @Override
    public CodeBuilder i2l() {
        bytecodesBufWriter.writeU1(I2L);
        return this;
    }

    @Override
    public CodeBuilder i2s() {
        bytecodesBufWriter.writeU1(I2S);
        return this;
    }

    @Override
    public CodeBuilder iadd() {
        bytecodesBufWriter.writeU1(IADD);
        return this;
    }

    @Override
    public CodeBuilder iand() {
        bytecodesBufWriter.writeU1(IAND);
        return this;
    }

    @Override
    public CodeBuilder iconst_0() {
        bytecodesBufWriter.writeU1(ICONST_0);
        return this;
    }

    @Override
    public CodeBuilder iconst_1() {
        bytecodesBufWriter.writeU1(ICONST_1);
        return this;
    }

    @Override
    public CodeBuilder iconst_2() {
        bytecodesBufWriter.writeU1(ICONST_2);
        return this;
    }

    @Override
    public CodeBuilder iconst_3() {
        bytecodesBufWriter.writeU1(ICONST_3);
        return this;
    }

    @Override
    public CodeBuilder iconst_4() {
        bytecodesBufWriter.writeU1(ICONST_4);
        return this;
    }

    @Override
    public CodeBuilder iconst_5() {
        bytecodesBufWriter.writeU1(ICONST_5);
        return this;
    }

    @Override
    public CodeBuilder iconst_m1() {
        bytecodesBufWriter.writeU1(ICONST_M1);
        return this;
    }

    @Override
    public CodeBuilder idiv() {
        bytecodesBufWriter.writeU1(IDIV);
        return this;
    }

    @Override
    public CodeBuilder if_acmpeq(Label target) {
        writeShortJump(IF_ACMPEQ, target);
        return this;
    }

    @Override
    public CodeBuilder if_acmpne(Label target) {
        writeShortJump(IF_ACMPNE, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmpeq(Label target) {
        writeShortJump(IF_ICMPEQ, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmpge(Label target) {
        writeShortJump(IF_ICMPGE, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmpgt(Label target) {
        writeShortJump(IF_ICMPGT, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmple(Label target) {
        writeShortJump(IF_ICMPLE, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmplt(Label target) {
        writeShortJump(IF_ICMPLT, target);
        return this;
    }

    @Override
    public CodeBuilder if_icmpne(Label target) {
        writeShortJump(IF_ICMPNE, target);
        return this;
    }

    @Override
    public CodeBuilder ifnonnull(Label target) {
        writeShortJump(IFNONNULL, target);
        return this;
    }

    @Override
    public CodeBuilder ifnull(Label target) {
        writeShortJump(IFNULL, target);
        return this;
    }

    @Override
    public CodeBuilder ifeq(Label target) {
        writeShortJump(IFEQ, target);
        return this;
    }

    @Override
    public CodeBuilder ifge(Label target) {
        writeShortJump(IFGE, target);
        return this;
    }

    @Override
    public CodeBuilder ifgt(Label target) {
        writeShortJump(IFGT, target);
        return this;
    }

    @Override
    public CodeBuilder ifle(Label target) {
        writeShortJump(IFLE, target);
        return this;
    }

    @Override
    public CodeBuilder iflt(Label target) {
        writeShortJump(IFLT, target);
        return this;
    }

    @Override
    public CodeBuilder ifne(Label target) {
        writeShortJump(IFNE, target);
        return this;
    }

    @Override
    public CodeBuilder iinc(int slot, int val) {
        writeIncrement(validateAndIsWideIinc(slot, val), slot, val);
        return this;
    }

    @Override
    public CodeBuilder iload(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(ILOAD_0 + slot);
        } else {
            localAccess(ILOAD, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder imul() {
        bytecodesBufWriter.writeU1(IMUL);
        return this;
    }

    @Override
    public CodeBuilder ineg() {
        bytecodesBufWriter.writeU1(INEG);
        return this;
    }

    @Override
    public CodeBuilder instanceOf(ClassEntry target) {
        bytecodesBufWriter.writeIndex(INSTANCEOF, target);
        return this;
    }

    @Override
    public CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        writeInvokeDynamic(ref);
        return this;
    }

    @Override
    public CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        writeInvokeInterface(Opcode.INVOKEINTERFACE, ref, Util.parameterSlots(ref.typeSymbol()) + 1);
        return this;
    }

    @Override
    public CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        bytecodesBufWriter.writeIndex(INVOKESPECIAL, ref);
        return this;
    }

    @Override
    public CodeBuilder invokespecial(MethodRefEntry ref) {
        bytecodesBufWriter.writeIndex(INVOKESPECIAL, ref);
        return this;
    }

    @Override
    public CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        bytecodesBufWriter.writeIndex(INVOKESTATIC, ref);
        return this;
    }

    @Override
    public CodeBuilder invokestatic(MethodRefEntry ref) {
        bytecodesBufWriter.writeIndex(INVOKESTATIC, ref);
        return this;
    }

    @Override
    public CodeBuilder invokevirtual(MethodRefEntry ref) {
        bytecodesBufWriter.writeIndex(INVOKEVIRTUAL, ref);
        return this;
    }

    @Override
    public CodeBuilder ior() {
        bytecodesBufWriter.writeU1(IOR);
        return this;
    }

    @Override
    public CodeBuilder irem() {
        bytecodesBufWriter.writeU1(IREM);
        return this;
    }

    @Override
    public CodeBuilder ireturn() {
        bytecodesBufWriter.writeU1(IRETURN);
        return this;
    }

    @Override
    public CodeBuilder ishl() {
        bytecodesBufWriter.writeU1(ISHL);
        return this;
    }

    @Override
    public CodeBuilder ishr() {
        bytecodesBufWriter.writeU1(ISHR);
        return this;
    }

    @Override
    public CodeBuilder istore(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(ISTORE_0 + slot);
        } else {
            localAccess(ISTORE, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder isub() {
        bytecodesBufWriter.writeU1(ISUB);
        return this;
    }

    @Override
    public CodeBuilder iushr() {
        bytecodesBufWriter.writeU1(IUSHR);
        return this;
    }

    @Override
    public CodeBuilder ixor() {
        bytecodesBufWriter.writeU1(IXOR);
        return this;
    }

    @Override
    public CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        Objects.requireNonNull(defaultTarget);
        // check cases when we sort them
        writeLookupSwitch(defaultTarget, cases);
        return this;
    }

    @Override
    public CodeBuilder l2d() {
        bytecodesBufWriter.writeU1(L2D);
        return this;
    }

    @Override
    public CodeBuilder l2f() {
        bytecodesBufWriter.writeU1(L2F);
        return this;
    }

    @Override
    public CodeBuilder l2i() {
        bytecodesBufWriter.writeU1(L2I);
        return this;
    }

    @Override
    public CodeBuilder ladd() {
        bytecodesBufWriter.writeU1(LADD);
        return this;
    }

    @Override
    public CodeBuilder land() {
        bytecodesBufWriter.writeU1(LAND);
        return this;
    }

    @Override
    public CodeBuilder lcmp() {
        bytecodesBufWriter.writeU1(LCMP);
        return this;
    }

    @Override
    public CodeBuilder lconst_0() {
        bytecodesBufWriter.writeU1(LCONST_0);
        return this;
    }

    @Override
    public CodeBuilder lconst_1() {
        bytecodesBufWriter.writeU1(LCONST_1);
        return this;
    }

    @Override
    public CodeBuilder ldc(LoadableConstantEntry entry) {
        var direct = AbstractPoolEntry.maybeClone(constantPool, entry);
        writeDirectLoadConstant(BytecodeHelpers.ldcOpcode(direct), direct);
        return this;
    }

    @Override
    public CodeBuilder ldiv() {
        bytecodesBufWriter.writeU1(LDIV);
        return this;
    }

    @Override
    public CodeBuilder lload(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(LLOAD_0 + slot);
        } else {
            localAccess(LLOAD, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder lmul() {
        bytecodesBufWriter.writeU1(LMUL);
        return this;
    }

    @Override
    public CodeBuilder lneg() {
        bytecodesBufWriter.writeU1(LNEG);
        return this;
    }

    @Override
    public CodeBuilder lor() {
        bytecodesBufWriter.writeU1(LOR);
        return this;
    }

    @Override
    public CodeBuilder lrem() {
        bytecodesBufWriter.writeU1(LREM);
        return this;
    }

    @Override
    public CodeBuilder lreturn() {
        bytecodesBufWriter.writeU1(LRETURN);
        return this;
    }

    @Override
    public CodeBuilder lshl() {
        bytecodesBufWriter.writeU1(LSHL);
        return this;
    }

    @Override
    public CodeBuilder lshr() {
        bytecodesBufWriter.writeU1(LSHR);
        return this;
    }

    @Override
    public CodeBuilder lstore(int slot) {
        if (slot >= 0 && slot <= 3) {
            bytecodesBufWriter.writeU1(LSTORE_0 + slot);
        } else {
            localAccess(LSTORE, slot);
        }
        return this;
    }

    @Override
    public CodeBuilder lsub() {
        bytecodesBufWriter.writeU1(LSUB);
        return this;
    }

    @Override
    public CodeBuilder lushr() {
        bytecodesBufWriter.writeU1(LUSHR);
        return this;
    }

    @Override
    public CodeBuilder lxor() {
        bytecodesBufWriter.writeU1(LXOR);
        return this;
    }

    @Override
    public CodeBuilder monitorenter() {
        bytecodesBufWriter.writeU1(MONITORENTER);
        return this;
    }

    @Override
    public CodeBuilder monitorexit() {
        bytecodesBufWriter.writeU1(MONITOREXIT);
        return this;
    }

    @Override
    public CodeBuilder multianewarray(ClassEntry array, int dims) {
        BytecodeHelpers.validateMultiArrayDimensions(dims);
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
        bytecodesBufWriter.writeU1(POP);
        return this;
    }

    @Override
    public CodeBuilder pop2() {
        bytecodesBufWriter.writeU1(POP2);
        return this;
    }

    @Override
    public CodeBuilder sipush(int s) {
        BytecodeHelpers.validateSipush(s);
        bytecodesBufWriter.writeU1U2(SIPUSH, s);
        return this;
    }

    @Override
    public CodeBuilder swap() {
        bytecodesBufWriter.writeU1(SWAP);
        return this;
    }

    @Override
    public CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        Objects.requireNonNull(defaultTarget);
        // check cases when we write them
        writeTableSwitch(low, high, defaultTarget, cases);
        return this;
    }
}
