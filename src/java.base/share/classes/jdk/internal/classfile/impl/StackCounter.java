/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package jdk.internal.classfile.impl;

import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.DynamicConstantPoolEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.constant.MethodTypeDesc;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Queue;

import static java.lang.classfile.ClassFile.*;
import java.lang.constant.ClassDesc;
import java.util.stream.Collectors;

public final class StackCounter {

    private record Target(int bci, int stack) {}

    static StackCounter of(DirectCodeBuilder dcb, BufWriterImpl buf) {
        return new StackCounter(
                dcb,
                buf.thisClass().asSymbol(),
                dcb.methodInfo.methodName().stringValue(),
                dcb.methodInfo.methodTypeSymbol(),
                (dcb.methodInfo.methodFlags() & ACC_STATIC) != 0,
                ((BufWriterImpl) dcb.bytecodesBufWriter).asByteBuffer(),
                dcb.constantPool,
                dcb.handlers);
    }

    private int stack, maxStack, maxLocals, rets;

    private final RawBytecodeHelper bcs;
    private final ClassDesc thisClass;
    private final String methodName;
    private final MethodTypeDesc methodDesc;
    private final boolean isStatic;
    private final ByteBuffer bytecode;
    private final SplitConstantPool cp;
    private final Queue<Target> targets;
    private final BitSet visited;

    private void jump(int targetBci) {
        if (!visited.get(targetBci)) {
            targets.add(new Target(targetBci, stack));
        }
    }

    private void addStackSlot(int delta) {
        stack += delta;
        if (stack > maxStack) maxStack = stack;
    }

    private void ensureLocalSlot(int index) {
        if (index >= maxLocals) maxLocals = index + 1;
    }

    private boolean next() {
        Target en;
        while ((en = targets.poll()) != null) {
            if (!visited.get(en.bci)) {
                bcs.nextBci = en.bci;
                stack = en.stack;
                return true;
            }
        }
        bcs.nextBci = bcs.endBci;
        return false;
    }

    public StackCounter(LabelContext labelContext,
                     ClassDesc thisClass,
                     String methodName,
                     MethodTypeDesc methodDesc,
                     boolean isStatic,
                     ByteBuffer bytecode,
                     SplitConstantPool cp,
                     List<AbstractPseudoInstruction.ExceptionCatchImpl> handlers) {
        this.thisClass = thisClass;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.isStatic = isStatic;
        this.bytecode = bytecode;
        this.cp = cp;
        targets = new ArrayDeque<>();
        maxStack = stack = rets = 0;
        for (var h : handlers) targets.add(new Target(labelContext.labelToBci(h.handler), 1));
        maxLocals = isStatic ? 0 : 1;
        maxLocals += Util.parameterSlots(methodDesc);
        bcs = new RawBytecodeHelper(bytecode);
        visited = new BitSet(bcs.endBci);
        targets.add(new Target(0, 0));
        while (next()) {
            while (!bcs.isLastBytecode()) {
                bcs.rawNext();
                int opcode = bcs.rawCode;
                int bci = bcs.bci;
                visited.set(bci);
                switch (opcode) {
                    case NOP, LALOAD, DALOAD, SWAP, INEG, ARRAYLENGTH, INSTANCEOF, LNEG, FNEG, DNEG, I2F, L2D, F2I, D2L, I2B, I2C, I2S,
                         NEWARRAY, CHECKCAST, ANEWARRAY -> {}
                    case RETURN ->
                        next();
                    case ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, SIPUSH, BIPUSH,
                         FCONST_0, FCONST_1, FCONST_2, DUP, DUP_X1, DUP_X2, I2L, I2D, F2L, F2D, NEW ->
                        addStackSlot(+1);
                    case LCONST_0, LCONST_1, DCONST_0, DCONST_1, DUP2, DUP2_X1, DUP2_X2 ->
                        addStackSlot(+2);
                    case POP, MONITORENTER, MONITOREXIT, IADD, ISUB, IMUL, IDIV, IREM, ISHL, ISHR, IUSHR, IOR, IXOR, IAND,
                         LSHL, LSHR, LUSHR, FADD, FSUB, FMUL, FDIV, FREM, L2I, L2F, D2F, FCMPL, FCMPG, D2I ->
                        addStackSlot(-1);
                    case POP2, LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR, DADD, DSUB, DMUL, DDIV, DREM ->
                        addStackSlot(-2);
                    case IASTORE, BASTORE, CASTORE, SASTORE, FASTORE, AASTORE, LCMP, DCMPL, DCMPG ->
                        addStackSlot(-3);
                    case LASTORE, DASTORE ->
                        addStackSlot(-4);
                    case LDC ->
                        processLdc(bcs.getIndexU1());
                    case LDC_W, LDC2_W ->
                        processLdc(bcs.getIndexU2());
                    case ILOAD, FLOAD, ALOAD -> {
                        ensureLocalSlot(bcs.getIndex());
                        addStackSlot(+1);
                    }
                    case LLOAD, DLOAD -> {
                        ensureLocalSlot(bcs.getIndex() + 1);
                        addStackSlot(+2);
                    }
                    case ILOAD_0, FLOAD_0, ALOAD_0 -> {
                        ensureLocalSlot(0);
                        addStackSlot(+1);
                    }
                    case ILOAD_1, FLOAD_1, ALOAD_1 -> {
                        ensureLocalSlot(1);
                        addStackSlot(+1);
                    }
                    case ILOAD_2, FLOAD_2, ALOAD_2 -> {
                        ensureLocalSlot(2);
                        addStackSlot(+1);
                    }
                    case ILOAD_3, FLOAD_3, ALOAD_3 -> {
                        ensureLocalSlot(3);
                        addStackSlot(+1);
                    }
                    case LLOAD_0, DLOAD_0 -> {
                        ensureLocalSlot(1);
                        addStackSlot(+2);
                    }
                    case LLOAD_1, DLOAD_1 -> {
                        ensureLocalSlot(2);
                        addStackSlot(+2);
                    }
                    case LLOAD_2, DLOAD_2 -> {
                        ensureLocalSlot(3);
                        addStackSlot(+2);
                    }
                    case LLOAD_3, DLOAD_3 -> {
                        ensureLocalSlot(4);
                        addStackSlot(+2);
                    }
                    case IALOAD, BALOAD, CALOAD, SALOAD, FALOAD, AALOAD ->  {
                        addStackSlot(-1);
                    }
                    case ISTORE, FSTORE, ASTORE -> {
                        ensureLocalSlot(bcs.getIndex());
                        addStackSlot(-1);
                    }
                    case LSTORE, DSTORE -> {
                        ensureLocalSlot(bcs.getIndex() + 1);
                        addStackSlot(-2);
                    }
                    case ISTORE_0, FSTORE_0, ASTORE_0 -> {
                        ensureLocalSlot(0);
                        addStackSlot(-1);
                    }
                    case ISTORE_1, FSTORE_1, ASTORE_1 -> {
                        ensureLocalSlot(1);
                        addStackSlot(-1);
                    }
                    case ISTORE_2, FSTORE_2, ASTORE_2 -> {
                        ensureLocalSlot(2);
                        addStackSlot(-1);
                    }
                    case ISTORE_3, FSTORE_3, ASTORE_3 -> {
                        ensureLocalSlot(3);
                        addStackSlot(-1);
                    }
                    case LSTORE_0, DSTORE_0 -> {
                        ensureLocalSlot(1);
                        addStackSlot(-2);
                    }
                    case LSTORE_1, DSTORE_1 -> {
                        ensureLocalSlot(2);
                        addStackSlot(-2);
                    }
                    case LSTORE_2, DSTORE_2 -> {
                        ensureLocalSlot(3);
                        addStackSlot(-2);
                    }
                    case LSTORE_3, DSTORE_3 -> {
                        ensureLocalSlot(4);
                        addStackSlot(-2);
                    }
                    case IINC ->
                        ensureLocalSlot(bcs.getIndex());
                    case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> {
                        addStackSlot(-2);
                        jump(bcs.dest());
                    }
                    case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> {
                        addStackSlot(-1);
                        jump(bcs.dest());
                    }
                    case GOTO -> {
                        jump(bcs.dest());
                        next();
                    }
                    case GOTO_W -> {
                        jump(bcs.destW());
                        next();
                    }
                    case TABLESWITCH, LOOKUPSWITCH -> {
                        int alignedBci = RawBytecodeHelper.align(bci + 1);
                        int defaultOfset = bcs.getInt(alignedBci);
                        int keys, delta;
                        addStackSlot(-1);
                        if (bcs.rawCode == TABLESWITCH) {
                            int low = bcs.getInt(alignedBci + 4);
                            int high = bcs.getInt(alignedBci + 2 * 4);
                            if (low > high) {
                                throw error("low must be less than or equal to high in tableswitch");
                            }
                            keys = high - low + 1;
                            if (keys < 0) {
                                throw error("too many keys in tableswitch");
                            }
                            delta = 1;
                        } else {
                            keys = bcs.getInt(alignedBci + 4);
                            if (keys < 0) {
                                throw error("number of keys in lookupswitch less than 0");
                            }
                            delta = 2;
                            for (int i = 0; i < (keys - 1); i++) {
                                int this_key = bcs.getInt(alignedBci + (2 + 2 * i) * 4);
                                int next_key = bcs.getInt(alignedBci + (2 + 2 * i + 2) * 4);
                                if (this_key >= next_key) {
                                    throw error("Bad lookupswitch instruction");
                                }
                            }
                        }
                        int target = bci + defaultOfset;
                        jump(target);
                        for (int i = 0; i < keys; i++) {
                            alignedBci = RawBytecodeHelper.align(bcs.bci + 1);
                            target = bci + bcs.getInt(alignedBci + (3 + i * delta) * 4);
                            jump(target);
                        }
                        next();
                    }
                    case LRETURN, DRETURN -> {
                        addStackSlot(-2);
                        next();
                    }
                    case IRETURN, FRETURN, ARETURN, ATHROW -> {
                        addStackSlot(-1);
                        next();
                    }
                    case GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD -> {
                        var tk = TypeKind.fromDescriptor(cp.entryByIndex(bcs.getIndexU2(), MemberRefEntry.class).nameAndType().type());
                        switch (bcs.rawCode) {
                            case GETSTATIC ->
                                addStackSlot(tk.slotSize());
                            case PUTSTATIC ->
                                addStackSlot(-tk.slotSize());
                            case GETFIELD ->
                                addStackSlot(tk.slotSize() - 1);
                            case PUTFIELD ->
                                addStackSlot(-tk.slotSize() - 1);
                            default -> throw new AssertionError("Should not reach here");
                        }
                    }
                    case INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE, INVOKEDYNAMIC -> {
                        var cpe = cp.entryByIndex(bcs.getIndexU2());
                        var nameAndType = opcode == INVOKEDYNAMIC ? ((DynamicConstantPoolEntry)cpe).nameAndType() : ((MemberRefEntry)cpe).nameAndType();
                        var mtd = Util.methodTypeSymbol(nameAndType);
                        addStackSlot(Util.slotSize(mtd.returnType()) - Util.parameterSlots(mtd));
                        if (opcode != INVOKESTATIC && opcode != INVOKEDYNAMIC) {
                            addStackSlot(-1);
                        }
                    }
                    case MULTIANEWARRAY ->
                        addStackSlot (1 - bcs.getU1(bcs.bci + 3));
                    case JSR -> {
                        addStackSlot(+1);
                        jump(bcs.dest()); //here we lost track of the exact stack size after return from subroutine
                        addStackSlot(-1);
                    }
                    case JSR_W -> {
                        addStackSlot(+1);
                        jump(bcs.destW()); //here we lost track of the exact stack size after return from subroutine
                        addStackSlot(-1);
                    }
                    case RET -> {
                        ensureLocalSlot(bcs.getIndex());
                        rets++; //subroutines must be counted for later maxStack correction
                        next();
                    }
                    default ->
                        throw error(String.format("Bad instruction: %02x", opcode));
                }
            }
        }
        //correction of maxStack when subroutines are present by calculation of upper bounds
        //the worst scenario is that all subroutines are chained and each subroutine also requires maxStack for its own code
        maxStack += rets * maxStack;
    }

    /**
     * Calculated maximum number of the locals required
     * @return maximum number of the locals required
     */
    public int maxLocals() {
        return maxLocals;
    }

    /**
     * Calculated maximum stack size required
     * @return maximum stack size required
     */
    public int maxStack() {
        return maxStack;
    }

    private void processLdc(int index) {
        switch (cp.entryByIndex(index).tag()) {
            case TAG_UTF8, TAG_STRING, TAG_CLASS, TAG_INTEGER, TAG_FLOAT, TAG_METHODHANDLE, TAG_METHODTYPE ->
                addStackSlot(+1);
            case TAG_DOUBLE, TAG_LONG ->
                addStackSlot(+2);
            case TAG_CONSTANTDYNAMIC ->
                addStackSlot(cp.entryByIndex(index, ConstantDynamicEntry.class).typeKind().slotSize());
            default ->
                throw error("CP entry #%d %s is not loadable constant".formatted(index, cp.entryByIndex(index).tag()));
        }
    }

    private IllegalArgumentException error(String msg) {
        var sb = new StringBuilder("%s at bytecode offset %d of method %s(%s)".formatted(
                msg,
                bcs.bci,
                methodName,
                methodDesc.parameterList().stream().map(ClassDesc::displayName).collect(Collectors.joining(","))));
        Util.dumpMethod(cp, thisClass, methodName, methodDesc, isStatic ? ACC_STATIC : 0, bytecode, sb::append);
        return new IllegalArgumentException(sb.toString());
    }
}
