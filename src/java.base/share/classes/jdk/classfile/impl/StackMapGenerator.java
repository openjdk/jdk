/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package jdk.classfile.impl;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import jdk.classfile.Classfile;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.ConstantDynamicEntry;
import jdk.classfile.constantpool.DynamicConstantPoolEntry;
import jdk.classfile.constantpool.MemberRefEntry;
import jdk.classfile.constantpool.ConstantPoolBuilder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import jdk.classfile.Attribute;

import static jdk.classfile.Classfile.*;
import jdk.classfile.BufWriter;
import jdk.classfile.Label;
import jdk.classfile.Opcode;
import jdk.classfile.attribute.StackMapTableAttribute;
import jdk.classfile.Attributes;

/**
 * StackMapGenerator is responsible for stack map frames generation.
 * <p>
 * Stack map frames are computed from serialized bytecode similar way they are verified during class loading process.
 * <p>
 * The {@linkplain #generate() frames computation} consists of following steps:
 * <ol>
 * <li>{@linkplain #detectFrameOffsets() Detection} of mandatory stack map frames offsets:<ul>
 *      <li>Mandatory stack map frame offsets include all jump and switch instructions targets,
 *          offsets immediately following {@linkplain #noControlFlow(int) "no control flow"}
 *          and all exception table handlers.
 *      <li>Detection is performed in a single fast pass through the bytecode,
 *          with no auxiliary structures construction nor further instructions processing.
 * </ul>
 * <li>Generator loop {@linkplain #processMethod() processing bytecode instructions}:<ul>
 *      <li>Generator loop simulates sequence instructions {@linkplain #processBlock(RawBytecodeHelper) processing effect on the actual stack and locals}.
 *      <li>All mandatory {@linkplain Frame frames} detected in the step #1 are {@linkplain Frame#checkAssignableTo(Frame) retro-filled}
 *          (or {@linkplain Frame#merge(Type, Type[], int, Frame) reverse-merged} in subsequent processing)
 *          with the actual stack and locals for all matching jump, switch and exception handler targets.
 *      <li>All frames modified by reverse merges are marked as {@linkplain Frame#dirty dirty} for further processing.
 *      <li>Code blocks with not yet known entry frame content are skipped and related frames are also marked as dirty.
 *      <li>Generator loop process is repeated until all mandatory frames are cleared or until an error state is reached.
 *      <li>Generator loop always passes all instructions at least once to calculate {@linkplain #maxStack max stack}
 *          and {@linkplain #maxLocals max locals} code attributes.
 *      <li>More than one pass is usually not necessary, except for more complex bytecode sequences.<br>
 *          <i>(Note: experimental measurements showed that more than 99% of the cases required only single pass to clear all frames,
 *          less than 1% of the cases required second pass and remaining 0,01% of the cases required third pass to clear all frames.)</i>.
 * </ul>
 * <li>Dead code patching to pass class loading verification:<ul>
 *      <li>Dead code blocks are indicated by frames remaining without content after leaving the Generator loop.
 *      <li>Each dead code block is filled with <code>NOP</code> instructions, terminated with
 *          <code>ATHROW</code> instruction, and removed from exception handlers table.
 *      <li>Dead code block entry frame is set to <code>java.lang.Throwable</code> single stack item and no locals.
 * </ul>
 * </ol>
 * <p>
 * {@linkplain Frame#merge(Type, Type[], int, Frame) Reverse-merge} of the stack map frames
 * may in some situations require to determine {@linkplain ClassHierarchyImpl class hierarchy} relations.
 * <p>
 * Reverse-merge of individual {@linkplain Type types} is performed when a target frame has already been retro-filled
 * and it is necessary to adjust its existing stack entries and locals to also match actual stack map frame conditions.
 * Following tables describe how new target stack entry or local type is calculated, based on the actual frame stack entry or local ("from")
 * and actual value of the target stack entry or local ("to").
 *
 * <table border="1">
 * <caption>Reverse-merge of general type categories</caption>
 * <tr><th>to \ from<th>TOP<th>PRIMITIVE<th>UNINITIALIZED<th>REFERENCE
 * <tr><th>TOP<td>TOP<td>TOP<td>TOP<td>TOP
 * <tr><th>PRIMITIVE<td>TOP<td><a href="#primitives">Reverse-merge of primitive types</a><td>TOP<td>TOP
 * <tr><th>UNINITIALIZED<td>TOP<td>TOP<td>Is NEW offset matching ? UNINITIALIZED : TOP<td>TOP
 * <tr><th>REFERENCE<td>TOP<td>TOP<td>TOP<td><a href="#references">Reverse-merge of reference types</a>
 * </table>
 * <p>
 * <table id="primitives" border="1">
 * <caption>Reverse-merge of primitive types</caption>
 * <tr><th>to \ from<th>SHORT<th>BYTE<th>BOOLEAN<th>LONG<th>DOUBLE<th>FLOAT<th>INTEGER
 * <tr><th>SHORT<td>SHORT<td>TOP<td>TOP<td>TOP<td>TOP<td>TOP<td>SHORT
 * <tr><th>BYTE<td>TOP<td>BYTE<td>TOP<td>TOP<td>TOP<td>TOP<td>BYTE
 * <tr><th>BOOLEAN<td>TOP<td>TOP<td>BOOLEAN<td>TOP<td>TOP<td>TOP<td>BOOLEAN
 * <tr><th>LONG<td>TOP<td>TOP<td>TOP<td>LONG<td>TOP<td>TOP<td>TOP
 * <tr><th>DOUBLE<td>TOP<td>TOP<td>TOP<td>TOP<td>DOUBLE<td>TOP<td>TOP
 * <tr><th>FLOAT<td>TOP<td>TOP<td>TOP<td>TOP<td>TOP<td>FLOAT<td>TOP
 * <tr><th>INTEGER<td>TOP<td>TOP<td>TOP<td>TOP<td>TOP<td>TOP<td>INTEGER
 * </table>
 * <p>
 * <table id="references" border="1">
 * <caption>Reverse merge of reference types</caption>
 * <tr><th>to \ from<th>NULL<th>j.l.Object<th>j.l.Cloneable<th>j.i.Serializable<th>ARRAY<th>INTERFACE*<th>OBJECT**
 * <tr><th>NULL<td>NULL<td>j.l.Object<td>j.l.Cloneable<td>j.i.Serializable<td>ARRAY<td>INTERFACE<td>OBJECT
 * <tr><th>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object
 * <tr><th>j.l.Cloneable<td>j.l.Cloneable<td>j.l.Cloneable<td>j.l.Cloneable<td>j.l.Cloneable<td>j.l.Object<td>j.l.Cloneable<td>j.l.Cloneable
 * <tr><th>j.i.Serializable<td>j.i.Serializable<td>j.i.Serializable<td>j.i.Serializable<td>j.i.Serializable<td>j.l.Object<td>j.i.Serializable<td>j.i.Serializable
 * <tr><th>ARRAY<td>ARRAY<td>j.l.Object<td>j.l.Object<td>j.l.Object<td><a href="#arrays">Reverse merge of arrays</a><td>j.l.Object<td>j.l.Object
 * <tr><th>INTERFACE*<td>INTERFACE<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object
 * <tr><th>OBJECT**<td>OBJECT<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>j.l.Object<td>Resolved common ancestor
 * <tr><td colspan="8">*any interface reference except for j.l.Cloneable and j.i.Serializable<br>**any object reference except for j.l.Object
 * </table>
 * <p id="arrays">
 * Array types are reverse-merged as reference to array type constructed from reverse-merged components.
 * Reference to j.l.Object is an alternate result when construction of the array type is not possible (when reverse-merge of components returned TOP or other non-reference and non-primitive type).
 * <p>
 * Custom class hierarchy resolver has been implemented as a part of the library to avoid heavy class loading
 * and to allow stack maps generation even for code with incomplete dependency classpath.
 * However stack maps generated with {@linkplain ClassHierarchyImpl#resolve(java.lang.constant.ClassDesc) warnings of unresolved dependencies} may later fail to verify during class loading process.
 * <p>
 * Focus of the whole algorithm is on high performance and low memory footprint:<ul>
 *      <li>It does not produce, collect nor visit any complex intermediate structures
 *          <i>(beside {@linkplain RawBytecodeHelper traversing} the {@linkplain #bytecode bytecode in binary form}).</i>
 *      <li>It works with only minimal mandatory stack map frames.
 *      <li>It does not spend time on any non-essential verifications.
 * </ul>
 * <p>
 * In case of an exception during the Generator loop there is just minimal information available in the exception message.
 * <p>
 * To determine root cause of the exception it is recommended to enable debug logging of the Generator in one of the two modes
 * using following <code>java.lang.System</code> properties:<dl>
 * <dt><code>-Djdk.classfile.impl.StackMapGenerator.DEBUG=true</code>
 *      <dd>Activates debug logging with basic information + generated stack map frames in case of success.
 *          It also re-runs with enabled full trace logging in case of an error or exception.
 * <dt><code>-Djdk.classfile.impl.StackMapGenerator.TRACE=true</code>
 *      <dd>Activates full detailed tracing of the generator process for all invocations.
 * </dl>
 */

public final class StackMapGenerator {

    private static final boolean TRACE, DEBUG;
    private static final Map<Integer, String> OPCODE_NAMES;
    static {
        TRACE = Boolean.getBoolean(StackMapGenerator.class.getName() + ".TRACE");
        DEBUG = TRACE || Boolean.getBoolean(StackMapGenerator.class.getName() + ".DEBUG");
        if (DEBUG) {
            OPCODE_NAMES = new HashMap<>();
            for (var o : Opcode.values())
                OPCODE_NAMES.put(o.bytecode(), o.name());
        } else
            OPCODE_NAMES = null;
    }

    private static final String OBJECT_INITIALIZER_NAME = "<init>";
    private static final int FLAG_THIS_UNINIT = 0x01;
    private static final int FRAME_DEFAULT_CAPACITY = 10;
    private static final int BITS_PER_BYTE = 8;
    private static final int T_BOOLEAN = 4, T_LONG = 11;

    private static final int ITEM_TOP = 0,
            ITEM_INTEGER = 1,
            ITEM_FLOAT = 2,
            ITEM_DOUBLE = 3,
            ITEM_LONG = 4,
            ITEM_NULL = 5,
            ITEM_UNINITIALIZED_THIS = 6,
            ITEM_OBJECT = 7,
            ITEM_UNINITIALIZED = 8,
            ITEM_BOOLEAN = 9,
            ITEM_BYTE = 10,
            ITEM_SHORT = 11,
            ITEM_CHAR = 12,
            ITEM_LONG_2ND = 13,
            ITEM_DOUBLE_2ND = 14;

    private static final ClassDesc[] ARRAY_FROM_BASIC_TYPE = new ClassDesc[]{null, null, null, null,
        CD_boolean.arrayType(), CD_char.arrayType(), CD_float.arrayType(), CD_double.arrayType(),
        CD_byte.arrayType(), CD_short.arrayType(), CD_int.arrayType(), CD_long.arrayType()};

    private final Type thisType;
    private final String methodName;
    private final MethodTypeDesc methodDesc;
    private final ByteBuffer bytecode;
    private final ConstantPoolBuilder cp;
    private final boolean isStatic;
    private final LabelContext labelContext;
    private final List<AbstractInstruction.ExceptionCatchImpl> exceptionTable;
    private final ClassHierarchyImpl classHierarchy;
    private final boolean patchDeadCode;
    private List<Frame> frames;
    private final Frame currentFrame;
    private int maxStack, maxLocals;
    private boolean trace = TRACE;

    /**
     * Primary constructor of the <code>Generator</code> class.
     * New <code>Generator</code> instance must be created for each individual class/method.
     * Instance contains only immutable results, all the calculations are processed during instance construction.
     *
     * @param labelContext <code>LableContext</code> instance used to resolve or patch <code>ExceptionHandler</code>
     * labels to bytecode offsets (or vice versa)
     * @param thisClass class to generate stack maps for
     * @param methodName method name to generate stack maps for
     * @param methodDesc method descriptor to generate stack maps for
     * @param isStatic information whether the method is static
     * @param bytecode R/W <code>ByteBuffer</code> wrapping method bytecode, the content is altered in case <code>Generator</code> detects  and patches dead code
     * @param cp R/W <code>ConstantPoolBuilder</code> instance used to resolve all involved CP entries and also generate new entries referenced from the generted stack maps
     * @param handlers R/W <code>ExceptionHandler</code> list used to detect mandatory frame offsets as well as to determine stack maps in exception handlers
     * and also to be altered when dead code is detected and must be excluded from exception handlers
     */
    public StackMapGenerator(LabelContext labelContext,
                     ClassDesc thisClass,
                     String methodName,
                     MethodTypeDesc methodDesc,
                     boolean isStatic,
                     ByteBuffer bytecode,
                     ConstantPoolBuilder cp,
                     List<AbstractInstruction.ExceptionCatchImpl> handlers) {
        this.thisType = Type.referenceType(thisClass);
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.isStatic = isStatic;
        this.bytecode = bytecode;
        this.cp = cp;
        this.labelContext = labelContext;
        this.exceptionTable = handlers;
        this.classHierarchy = new ClassHierarchyImpl(cp.optionValue(Classfile.Option.Key.HIERARCHY_RESOLVER));
        this.patchDeadCode = cp.optionValue(Classfile.Option.Key.PATCH_DEAD_CODE);
        this.currentFrame = new Frame(classHierarchy);
        if (DEBUG) System.out.println("Generating stack maps for class: " + thisClass.displayName() + " method: " + methodName + " with signature: " + methodDesc);
        try {
            generate();
        } catch (Error | Exception e) {
            if (DEBUG && !trace) {
                e.printStackTrace(System.out);
                trace = true;
                generate();
            }
            throw e;
        }
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

    private int getFrameIndexFromOffset(int offset) {
        int i = 0;
        for (; i < frames.size(); i++) {
            if (frames.get(i).offset == offset) {
                return i;
            }
        }
        return i;
    }

    private void checkJumpTarget(Frame frame, int target) {
        int index = getFrameIndexFromOffset(target);
        frame.checkAssignableTo(frames.get(index));
    }

    private int exMin, exMax;

    private boolean isAnyFrameDirty() {
        for (var f : frames) {
            if (f.dirty) return true;
        }
        return false;
    }

    private void generate() {
        exMin = bytecode.capacity();
        exMax = -1;
        for (var exhandler : exceptionTable) {
            int start_pc = labelContext.labelToBci(exhandler.tryStart());
            int end_pc = labelContext.labelToBci(exhandler.tryEnd());
            if (start_pc < exMin) exMin = start_pc;
            if (end_pc > exMax) exMax = end_pc;
        }
        BitSet frameOffsets = detectFrameOffsets();
        if (trace) System.out.println("  Detected mandatory frame bytecode offsets: " + frameOffsets);
        int framesCount = frameOffsets.cardinality();
        frames = new ArrayList<>(framesCount);
        int offset = -1;
        for (int i = 0; i < framesCount; i++) {
            offset = frameOffsets.nextSetBit(offset + 1);
            frames.add(new Frame(offset, classHierarchy));
        }
        do {
            if (trace) System.out.println("  Entering generator loop");
            processMethod();
        } while (isAnyFrameDirty());
        maxLocals = currentFrame.frameMaxLocals;
        maxStack = currentFrame.frameMaxStack;
        if (DEBUG) System.out.println("  Calculated maxLocals: " + maxLocals + " maxStack: " + maxStack);

        if (framesCount > 0) if (DEBUG) System.out.println("  Generated stack map frames:");
        //dead code patching
        for (int i = 0; i < framesCount; i++) {
            var frame = frames.get(i);
            if (DEBUG) System.out.println("    " + frame);
            if (frame.flags == -1) {
                if (!patchDeadCode) generatorError("Unable to generate stack map frame for dead code");
                //patch frame
                frame.pushStack(Type.THROWABLE_TYPE);
                if (maxStack < 1) maxStack = 1;
                int blockSize = (i < framesCount - 1 ? frames.get(i + 1).offset : bytecode.limit()) - frame.offset;
                //patch bytecode
                if (trace) System.out.println("      Patching dead code range <" + frame.offset + ",  " + (frame.offset + blockSize) + ")");
                bytecode.position(frame.offset);
                for (int n=1; n<blockSize; n++) {
                    bytecode.put((byte) Classfile.NOP);
                }
                bytecode.put((byte) Classfile.ATHROW);
                //patch handlers
                removeRangeFromExcTable(frame.offset, frame.offset + blockSize);
            }
        }
    }

    private void removeRangeFromExcTable(int rangeStart, int rangeEnd) {
        var it = exceptionTable.listIterator();
        while (it.hasNext()) {
            var e = it.next();
            int handlerStart = labelContext.labelToBci(e.tryStart());
            int handlerEnd = labelContext.labelToBci(e.tryEnd());
            if (rangeStart >= handlerEnd || rangeEnd <= handlerStart) {
                //out of range
                continue;
            }
            if (trace) System.out.println("      Removing dead code range from exception handler start: " + handlerStart + " end: " + handlerEnd);
            if (rangeStart <= handlerStart) {
              if (rangeEnd >= handlerEnd) {
                  //complete removal
                  it.remove();
              } else {
                  //cut from left
                  Label newStart = labelContext.newLabel();
                  labelContext.setLabelTarget(newStart, rangeEnd);
                  it.set(new AbstractInstruction.ExceptionCatchImpl(e.handler(), newStart, e.tryEnd(), e.catchType()));
              }
            } else if (rangeEnd >= handlerEnd) {
                //cut from right
                Label newEnd = labelContext.newLabel();
                labelContext.setLabelTarget(newEnd, rangeStart);
                it.set(new AbstractInstruction.ExceptionCatchImpl(e.handler(), e.tryStart(), newEnd, e.catchType()));
            } else {
                //split
                Label newStart = labelContext.newLabel();
                labelContext.setLabelTarget(newStart, rangeEnd);
                Label newEnd = labelContext.newLabel();
                labelContext.setLabelTarget(newEnd, rangeStart);
                it.set(new AbstractInstruction.ExceptionCatchImpl(e.handler(), e.tryStart(), newEnd, e.catchType()));
                it.add(new AbstractInstruction.ExceptionCatchImpl(e.handler(), newStart, e.tryEnd(), e.catchType()));
            }
        }
    }

    /**
     * Getter of the generated <code>StackMapTableAttribute</code> or null if stack map is empty
     * @return <code>StackMapTableAttribute</code> or null if stack map is empty
     */
    public Attribute<? extends StackMapTableAttribute> stackMapTableAttribute() {
        return frames.isEmpty() ? null : new UnboundAttribute.AdHocAttribute<>(Attributes.STACK_MAP_TABLE) {
            @Override
            public void writeBody(BufWriter b) {
                int start = b.size();
                b.writeU2(frames.size());
                Frame prevFrame =  new Frame(classHierarchy);
                prevFrame.setLocalsFromArg(methodName, methodDesc, isStatic, thisType);
                prevFrame.trimAndCompress();
                for (var fr : frames) {
                    fr.trimAndCompress();
                    fr.writeTo(b, prevFrame, cp);
                    prevFrame = fr;
                }
            }
        };
    }

    private static Type cpIndexToType(int index, ConstantPoolBuilder cp) {
        return Type.referenceType(((ClassEntry)cp.entryByIndex(index)).asSymbol());
    }

    private static int classDescToType(ClassDesc desc, Type inference_types[], int inference_type_index) {
        return classDescToType(desc, new BiConsumer<>() {
            @Override
            public void accept(Integer i, Type vt) {
                inference_types[i] = vt;
            }
        }, inference_type_index);
    }

    private static int classDescToType(ClassDesc desc, BiConsumer<Integer, Type> inference_types_consumer, int inference_type_index) {
        if (desc.isClassOrInterface() || desc.isArray()) {
            inference_types_consumer.accept(inference_type_index, Type.referenceType(desc));
            return 1;
        }
        switch (desc.descriptorString()) {
            case "J" -> {
                inference_types_consumer.accept(inference_type_index, Type.LONG_TYPE);
                inference_types_consumer.accept(++inference_type_index, Type.LONG2_TYPE);
                return 2;
            }
            case "D" -> {
                inference_types_consumer.accept(inference_type_index, Type.DOUBLE_TYPE);
                inference_types_consumer.accept(++inference_type_index, Type.DOUBLE2_TYPE);
                return 2;
            }
            case "I", "Z", "B", "C", "S" -> {
                inference_types_consumer.accept(inference_type_index, Type.INTEGER_TYPE);
                return 1;
            }
            case "F" -> {
                inference_types_consumer.accept(inference_type_index, Type.FLOAT_TYPE);
                return 1;
            }
            default -> throw new AssertionError("Should not reach here");
        }
    }

    private void processMethod() {
        currentFrame.setLocalsFromArg(methodName, methodDesc, isStatic, thisType);
        currentFrame.stackSize = 0;
        currentFrame.flags = 0;
        currentFrame.offset = -1;
        int stackmapIndex = 0;
        RawBytecodeHelper bcs = new RawBytecodeHelper(bytecode);
        boolean ncf = false;
        while (!bcs.isLastBytecode()) {
            bcs.rawNext();
            currentFrame.offset = bcs.bci;
            if (stackmapIndex < frames.size()) {
                int thisOffset = frames.get(stackmapIndex).offset;
                if (ncf && thisOffset > bcs.bci) {
                    generatorError("Expecting a stack map frame");
                }
                if (thisOffset == bcs.bci) {
                    if (!ncf) {
                        currentFrame.checkAssignableTo(frames.get(stackmapIndex));
                    }
                    Frame nextFrame = frames.get(stackmapIndex++);
                    while (!nextFrame.dirty) { //skip unmatched frames
                        if (stackmapIndex == frames.size()) return; //skip the rest of this round
                        nextFrame = frames.get(stackmapIndex++);
                    }
                    bcs.rawNext(nextFrame.offset); //skip code up-to the next frame
                    currentFrame.offset = bcs.bci;
                    if (trace) System.out.println("    " + currentFrame);
                    currentFrame.copyFrom(nextFrame);
                    nextFrame.dirty = false;
                } else if (thisOffset < bcs.bci) {
                    throw new ClassFormatError(String.format("Bad stack map offset %d", thisOffset));
                }
            } else if (ncf) {
                generatorError("Expecting a stack map frame");
            }
            processBlock(bcs);
            ncf = noControlFlow(bcs.rawCode);
        }
        if (trace) System.out.println("    " + currentFrame);
    }

    private void processBlock(RawBytecodeHelper bcs) {
        int opcode = bcs.rawCode;
        boolean this_uninit = false;
        boolean verified_exc_handlers = false;
        int bci = bcs.bci;
        if (trace) System.out.println("    " +currentFrame +"\n    @" + bci + " " + OPCODE_NAMES.get(opcode));
        Type type1, type2, type3, type4;
        if (RawBytecodeHelper.isStoreIntoLocal(opcode) && bci >= exMin && bci < exMax) {
            processExceptionHandlerTargets(bci, this_uninit);
            verified_exc_handlers = true;
        }
        switch (opcode) {
            case Classfile.NOP, Classfile.RETURN -> {}
            case Classfile.ACONST_NULL ->
                currentFrame.pushStack(Type.NULL_TYPE);
            case Classfile.ICONST_M1, Classfile.ICONST_0, Classfile.ICONST_1, Classfile.ICONST_2, Classfile.ICONST_3, Classfile.ICONST_4, Classfile.ICONST_5, Classfile.SIPUSH, Classfile.BIPUSH ->
                currentFrame.pushStack(Type.INTEGER_TYPE);
            case Classfile.LCONST_0, Classfile.LCONST_1 ->
                currentFrame.pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.FCONST_0, Classfile.FCONST_1, Classfile.FCONST_2 ->
                currentFrame.pushStack(Type.FLOAT_TYPE);
            case Classfile.DCONST_0, Classfile.DCONST_1 ->
                currentFrame.pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.LDC ->
                processLdc(bcs.getIndexU1());
            case Classfile.LDC_W, Classfile.LDC2_W ->
                processLdc(bcs.getIndexU2());
            case Classfile.ILOAD ->
                currentFrame.checkLocal(bcs.getIndex()).pushStack(Type.INTEGER_TYPE);
            case Classfile.ILOAD_0, Classfile.ILOAD_1, Classfile.ILOAD_2, Classfile.ILOAD_3 ->
                currentFrame.checkLocal(opcode - Classfile.ILOAD_0).pushStack(Type.INTEGER_TYPE);
            case Classfile.LLOAD ->
                currentFrame.checkLocal(bcs.getIndex() + 1).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.LLOAD_0, Classfile.LLOAD_1, Classfile.LLOAD_2, Classfile.LLOAD_3 ->
                currentFrame.checkLocal(opcode - Classfile.LLOAD_0 + 1).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.FLOAD ->
                currentFrame.checkLocal(bcs.getIndex()).pushStack(Type.FLOAT_TYPE);
            case Classfile.FLOAD_0, Classfile.FLOAD_1, Classfile.FLOAD_2, Classfile.FLOAD_3 ->
                currentFrame.checkLocal(opcode - Classfile.FLOAD_0).pushStack(Type.FLOAT_TYPE);
            case Classfile.DLOAD ->
                currentFrame.checkLocal(bcs.getIndex() + 1).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.DLOAD_0, Classfile.DLOAD_1, Classfile.DLOAD_2, Classfile.DLOAD_3 ->
                currentFrame.checkLocal(opcode - Classfile.DLOAD_0 + 1).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.ALOAD ->
                currentFrame.pushStack(currentFrame.getLocal(bcs.getIndex()));
            case Classfile.ALOAD_0, Classfile.ALOAD_1, Classfile.ALOAD_2, Classfile.ALOAD_3 ->
                currentFrame.pushStack(currentFrame.getLocal(opcode - Classfile.ALOAD_0));
            case Classfile.IALOAD, Classfile.BALOAD, Classfile.CALOAD, Classfile.SALOAD ->
                currentFrame.decStack(2).pushStack(Type.INTEGER_TYPE);
            case Classfile.LALOAD ->
                currentFrame.decStack(2).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.FALOAD ->
                currentFrame.decStack(2).pushStack(Type.FLOAT_TYPE);
            case Classfile.DALOAD ->
                currentFrame.decStack(2).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.AALOAD ->
                currentFrame.pushStack((type1 = currentFrame.decStack(1).popStack()).isNull() ? Type.NULL_TYPE : type1.getComponent());
            case Classfile.ISTORE ->
                currentFrame.decStack(1).setLocal(bcs.getIndex(), Type.INTEGER_TYPE);
            case Classfile.ISTORE_0, Classfile.ISTORE_1, Classfile.ISTORE_2, Classfile.ISTORE_3 ->
                currentFrame.decStack(1).setLocal(opcode - Classfile.ISTORE_0, Type.INTEGER_TYPE);
            case Classfile.LSTORE ->
                currentFrame.decStack(2).setLocal2(bcs.getIndex(), Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.LSTORE_0, Classfile.LSTORE_1, Classfile.LSTORE_2, Classfile.LSTORE_3 ->
                currentFrame.decStack(2).setLocal2(opcode - Classfile.LSTORE_0, Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.FSTORE ->
                currentFrame.decStack(1).setLocal(bcs.getIndex(), Type.FLOAT_TYPE);
            case Classfile.FSTORE_0, Classfile.FSTORE_1, Classfile.FSTORE_2, Classfile.FSTORE_3 ->
                currentFrame.decStack(1).setLocal(opcode - Classfile.FSTORE_0, Type.FLOAT_TYPE);
            case Classfile.DSTORE ->
                currentFrame.decStack(2).setLocal2(bcs.getIndex(), Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.DSTORE_0, Classfile.DSTORE_1, Classfile.DSTORE_2, Classfile.DSTORE_3 ->
                currentFrame.decStack(2).setLocal2(opcode - Classfile.DSTORE_0, Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.ASTORE ->
                currentFrame.setLocal(bcs.getIndex(), currentFrame.popStack());
            case Classfile.ASTORE_0, Classfile.ASTORE_1, Classfile.ASTORE_2, Classfile.ASTORE_3 ->
                currentFrame.setLocal(opcode - Classfile.ASTORE_0, currentFrame.popStack());
            case Classfile.LASTORE, Classfile.DASTORE ->
                currentFrame.decStack(4);
            case Classfile.IASTORE, Classfile.BASTORE, Classfile.CASTORE, Classfile.SASTORE, Classfile.FASTORE, Classfile.AASTORE ->
                currentFrame.decStack(3);
            case Classfile.POP, Classfile.MONITORENTER, Classfile.MONITOREXIT ->
                currentFrame.decStack(1);
            case Classfile.POP2 ->
                currentFrame.decStack(2);
            case Classfile.DUP ->
                currentFrame.pushStack(type1 = currentFrame.popStack()).pushStack(type1);
            case Classfile.DUP_X1 -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                currentFrame.pushStack(type1).pushStack(type2).pushStack(type1);
            }
            case Classfile.DUP_X2 -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                type3 = currentFrame.popStack();
                currentFrame.pushStack(type1).pushStack(type3).pushStack(type2).pushStack(type1);
            }
            case Classfile.DUP2 -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                currentFrame.pushStack(type2).pushStack(type1).pushStack(type2).pushStack(type1);
            }
            case Classfile.DUP2_X1 -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                type3 = currentFrame.popStack();
                currentFrame.pushStack(type2).pushStack(type1).pushStack(type3).pushStack(type2).pushStack(type1);
            }
            case Classfile.DUP2_X2 -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                type3 = currentFrame.popStack();
                type4 = currentFrame.popStack();
                currentFrame.pushStack(type2).pushStack(type1).pushStack(type4).pushStack(type3).pushStack(type2).pushStack(type1);
            }
            case Classfile.SWAP -> {
                type1 = currentFrame.popStack();
                type2 = currentFrame.popStack();
                currentFrame.pushStack(type1);
                currentFrame.pushStack(type2);
            }
            case Classfile.IADD, Classfile.ISUB, Classfile.IMUL, Classfile.IDIV, Classfile.IREM, Classfile.ISHL, Classfile.ISHR, Classfile.IUSHR, Classfile.IOR, Classfile.IXOR, Classfile.IAND ->
                currentFrame.decStack(2).pushStack(Type.INTEGER_TYPE);
            case Classfile.INEG, Classfile.ARRAYLENGTH, Classfile.INSTANCEOF ->
                currentFrame.decStack(1).pushStack(Type.INTEGER_TYPE);
            case Classfile.LADD, Classfile.LSUB, Classfile.LMUL, Classfile.LDIV, Classfile.LREM, Classfile.LAND, Classfile.LOR, Classfile.LXOR ->
                currentFrame.decStack(4).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.LNEG ->
                currentFrame.decStack(2).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.LSHL, Classfile.LSHR, Classfile.LUSHR ->
                currentFrame.decStack(3).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.FADD, Classfile.FSUB, Classfile.FMUL, Classfile.FDIV, Classfile.FREM ->
                currentFrame.decStack(2).pushStack(Type.FLOAT_TYPE);
            case Classfile.FNEG ->
                currentFrame.decStack(1).pushStack(Type.FLOAT_TYPE);
            case Classfile.DADD, Classfile.DSUB, Classfile.DMUL, Classfile.DDIV, Classfile.DREM ->
                currentFrame.decStack(4).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.DNEG ->
                currentFrame.decStack(2).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.IINC ->
                currentFrame.checkLocal(bcs.getIndex());
            case Classfile.I2L ->
                currentFrame.decStack(1).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.L2I ->
                currentFrame.decStack(2).pushStack(Type.INTEGER_TYPE);
            case Classfile.I2F ->
                currentFrame.decStack(1).pushStack(Type.FLOAT_TYPE);
            case Classfile.I2D ->
                currentFrame.decStack(1).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.L2F ->
                currentFrame.decStack(2).pushStack(Type.FLOAT_TYPE);
            case Classfile.L2D ->
                currentFrame.decStack(2).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.F2I ->
                currentFrame.decStack(1).pushStack(Type.INTEGER_TYPE);
            case Classfile.F2L ->
                currentFrame.decStack(1).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.F2D ->
                currentFrame.decStack(1).pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case Classfile.D2L ->
                currentFrame.decStack(2).pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case Classfile.D2F ->
                currentFrame.decStack(2).pushStack(Type.FLOAT_TYPE);
            case Classfile.I2B, Classfile.I2C, Classfile.I2S ->
                currentFrame.decStack(1).pushStack(Type.INTEGER_TYPE);
            case Classfile.LCMP, Classfile.DCMPL, Classfile.DCMPG ->
                currentFrame.decStack(4).pushStack(Type.INTEGER_TYPE);
            case Classfile.FCMPL, Classfile.FCMPG, Classfile.D2I ->
                currentFrame.decStack(2).pushStack(Type.INTEGER_TYPE);
            case Classfile.IF_ICMPEQ, Classfile.IF_ICMPNE, Classfile.IF_ICMPLT, Classfile.IF_ICMPGE, Classfile.IF_ICMPGT, Classfile.IF_ICMPLE, Classfile.IF_ACMPEQ, Classfile.IF_ACMPNE ->
                checkJumpTarget(currentFrame.decStack(2), bcs.dest());
            case Classfile.IFEQ, Classfile.IFNE, Classfile.IFLT, Classfile.IFGE, Classfile.IFGT, Classfile.IFLE, Classfile.IFNULL, Classfile.IFNONNULL ->
                checkJumpTarget(currentFrame.decStack(1), bcs.dest());
            case Classfile.GOTO ->
                checkJumpTarget(currentFrame, bcs.dest());
            case Classfile.GOTO_W ->
                checkJumpTarget(currentFrame, bcs.destW());
            case Classfile.TABLESWITCH, Classfile.LOOKUPSWITCH ->
                processSwitch(bcs);
            case Classfile.LRETURN, Classfile.DRETURN ->
                currentFrame.decStack(2);
            case Classfile.IRETURN, Classfile.FRETURN, Classfile.ARETURN, Classfile.ATHROW ->
                currentFrame.decStack(1);
            case Classfile.GETSTATIC, Classfile.PUTSTATIC, Classfile.GETFIELD, Classfile.PUTFIELD ->
                processFieldInstructions(bcs);
            case Classfile.INVOKEVIRTUAL, Classfile.INVOKESPECIAL, Classfile.INVOKESTATIC, Classfile.INVOKEINTERFACE, Classfile.INVOKEDYNAMIC ->
                this_uninit = processInvokeInstructions(bcs, (bci >= exMin && bci < exMax), this_uninit);
            case Classfile.NEW ->
                currentFrame.pushStack(Type.uninitializedType(bci));
            case Classfile.NEWARRAY ->
                currentFrame.decStack(1).pushStack(getNewarrayType(bcs.getIndex()));
            case Classfile.ANEWARRAY ->
                processAnewarray(bcs.getIndexU2());
            case Classfile.CHECKCAST ->
                currentFrame.decStack(1).pushStack(cpIndexToType(bcs.getIndexU2(), cp));
            case Classfile.MULTIANEWARRAY -> {
                type1 = cpIndexToType(bcs.getIndexU2(), cp);
                int dim = bcs.getU1(bcs.bci + 3);
                for (int i = 0; i < dim; i++) {
                    currentFrame.popStack();
                }
                currentFrame.pushStack(type1);
            }
            default ->
                generatorError(String.format("Bad instruction: %02x", opcode));
        }
        if (!verified_exc_handlers && bci >= exMin && bci < exMax) {
            processExceptionHandlerTargets(bci, this_uninit);
        }
    }

    private void processExceptionHandlerTargets(int bci, boolean this_uninit) {
        for (var exhandler : exceptionTable) {
            if (bci >= labelContext.labelToBci(exhandler.tryStart()) && bci < labelContext.labelToBci(exhandler.tryEnd())) {
                int flags = currentFrame.flags;
                if (this_uninit) flags |= FLAG_THIS_UNINIT;
                Frame newFrame = currentFrame.frameInExceptionHandler(flags);
                var catchType = exhandler.catchType();
                newFrame.pushStack(catchType.isPresent() ? cpIndexToType(catchType.get().index(), cp) : Type.THROWABLE_TYPE);
                checkJumpTarget(newFrame, labelContext.labelToBci(exhandler.handler()));
            }
        }
    }

    private void processLdc(int index) {
        switch (cp.entryByIndex(index).tag()) {
            case TAG_UTF8 ->
                currentFrame.pushStack(Type.OBJECT_TYPE);
            case TAG_STRING ->
                currentFrame.pushStack(Type.referenceType(CD_String));
            case TAG_CLASS ->
                currentFrame.pushStack(Type.referenceType(CD_Class));
            case TAG_INTEGER ->
                currentFrame.pushStack(Type.INTEGER_TYPE);
            case TAG_FLOAT ->
                currentFrame.pushStack(Type.FLOAT_TYPE);
            case TAG_DOUBLE ->
                currentFrame.pushStack2(Type.DOUBLE_TYPE, Type.DOUBLE2_TYPE);
            case TAG_LONG ->
                currentFrame.pushStack2(Type.LONG_TYPE, Type.LONG2_TYPE);
            case TAG_METHODHANDLE ->
                currentFrame.pushStack(Type.referenceType(CD_MethodHandle));
            case TAG_METHODTYPE ->
                currentFrame.pushStack(Type.referenceType(CD_MethodType));
            case TAG_CONSTANTDYNAMIC -> {
                Type[] vConstantType = new Type[2];
                int n = classDescToType(((ConstantDynamicEntry)cp.entryByIndex(index)).asSymbol().constantType(), vConstantType, 0);
                for (int i = 0; i < n; i++) {
                    currentFrame.pushStack(vConstantType[i]);
                }
            }
            default ->
                generatorError("Invalid index in ldc");
        }
    }

    private void processSwitch(RawBytecodeHelper bcs) {
        int bci = bcs.bci;
        int alignedBci = RawBytecodeHelper.align(bci + 1);
        int defaultOfset = bcs.getInt(alignedBci);
        int keys, delta;
        currentFrame.popStack();
        if (bcs.rawCode == Classfile.TABLESWITCH) {
            int low = bcs.getInt(alignedBci + 4);
            int high = bcs.getInt(alignedBci + 2 * 4);
            if (low > high) {
                generatorError("low must be less than or equal to high in tableswitch");
            }
            keys = high - low + 1;
            if (keys < 0) {
                generatorError("too many keys in tableswitch");
            }
            delta = 1;
        } else {
            keys = bcs.getInt(alignedBci + 4);
            if (keys < 0) {
                generatorError("number of keys in lookupswitch less than 0");
            }
            delta = 2;
            for (int i = 0; i < (keys - 1); i++) {
                int this_key = bcs.getInt(alignedBci + (2 + 2 * i) * 4);
                int next_key = bcs.getInt(alignedBci + (2 + 2 * i + 2) * 4);
                if (this_key >= next_key) {
                    generatorError("Bad lookupswitch instruction");
                }
            }
        }
        int target = bci + defaultOfset;
        checkJumpTarget(currentFrame, target);
        for (int i = 0; i < keys; i++) {
            alignedBci = RawBytecodeHelper.align(bcs.bci + 1);
            target = bci + bcs.getInt(alignedBci + (3 + i * delta) * 4);
            checkJumpTarget(currentFrame, target);
        }
    }

    private void processFieldInstructions(RawBytecodeHelper bcs) {
        int index = bcs.getIndexU2();
        Type[] fieldType = new Type[2];
        int n = classDescToType(ClassDesc.ofDescriptor(((MemberRefEntry)cp.entryByIndex(index)).nameAndType().type().stringValue()), fieldType, 0);
        switch (bcs.rawCode) {
            case Classfile.GETSTATIC -> {
                for (int i = 0; i < n; i++) {
                    currentFrame.pushStack(fieldType[i]);
                }
            }
            case Classfile.PUTSTATIC -> {
                for (int i = n - 1; i >= 0; i--) {
                    currentFrame.popStack();
                }
            }
            case Classfile.GETFIELD -> {
                currentFrame.popStack();
                for (int i = 0; i < n; i++) {
                    currentFrame.pushStack(fieldType[i]);
                }
            }
            case Classfile.PUTFIELD -> {
                for (int i = n - 1; i >= 0; i--) {
                    currentFrame.popStack();
                }
                currentFrame.popStack();
            }
            default -> throw new AssertionError("Should not reach here");
        }
    }

    private boolean processInvokeInstructions(RawBytecodeHelper bcs, boolean inTryBlock, boolean thisUninit) {
        int index = bcs.getIndexU2();
        int opcode = bcs.rawCode;
        var cpe = cp.entryByIndex(index);
        var nameAndType = opcode == Classfile.INVOKEDYNAMIC ? ((DynamicConstantPoolEntry)cpe).nameAndType() : ((MemberRefEntry)cpe).nameAndType();
        String invokeMethodName = nameAndType.name().stringValue();
        var mDesc = MethodTypeDesc.ofDescriptor(nameAndType.type().stringValue());
        Type[] sig_type = new Type[2];
        int nargs = 0;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            nargs += classDescToType(mDesc.parameterType(i), sig_type, 0);
        }
        int bci = bcs.bci;
        currentFrame.decStack(nargs);
        if (opcode != Classfile.INVOKESTATIC && opcode != Classfile.INVOKEDYNAMIC) {
            if (OBJECT_INITIALIZER_NAME.equals(invokeMethodName)) {
                Type type = currentFrame.popStack();
                if (type.isUninitializedThis()) {
                    if (inTryBlock) {
                        processExceptionHandlerTargets(bci, true);
                    }
                    currentFrame.initializeObject(type, thisType);
                    thisUninit = true;
                } else if (type.isUninitialized()) {
                    int new_offset = type.bci();
                    int new_class_index = bcs.getIndexU2Raw(new_offset + 1);
                    Type new_class_type = cpIndexToType(new_class_index, cp);
                    if (inTryBlock) {
                        processExceptionHandlerTargets(bci, thisUninit);
                    }
                    currentFrame.initializeObject(type, new_class_type);
                } else {
                    generatorError("Bad operand type when invoking <init>");
                }
            } else {
                currentFrame.popStack();
            }
        }
        if (!mDesc.returnType().equals(CD_void)) {
            if (OBJECT_INITIALIZER_NAME.equals(invokeMethodName)) {
                generatorError("Return type must be void in <init> method");
            }
            int n = classDescToType(mDesc.returnType(), sig_type, 0);
            for (int y = 0; y < n; y++) {
                currentFrame.pushStack(sig_type[y]);
            }
        }
        return thisUninit;
    }

    private Type getNewarrayType(int index) {
        if (index < T_BOOLEAN || index > T_LONG) generatorError("Illegal newarray instruction");
        return Type.referenceType(ARRAY_FROM_BASIC_TYPE[index]);
    }

    private void processAnewarray(int index) {
        currentFrame.popStack();
        currentFrame.pushStack(Type.referenceType(cpIndexToType(index, cp).sym.arrayType()));
    }

    /**
     * Throws <code>java.lang.VerifyError</code> with given error message
     * @param msg error message
     */
    private void generatorError(String msg) {
        throw new VerifyError(String.format("%s at %s", msg, methodName));
    }

    /**
     * Simple check if the given opcode falls into "no control flow" instructions group
     * (<code>GOTO</code>, <code>GOTO_W</code>, <code>TABLESWITCH</code>,
     * <code>LOOKUPSWITCH</code>, <code>ATHROW</code>, all <code>xRETURN</code> instructions),
     * for which current stack map frame is not propagated to the next instruction
     * and a new frame must be provided for the following instruction (if any)
     * @param opcode bytecode instruction opcode
     * @return boolean true if the opcode is in the "no control flow" group
     */
    private static boolean noControlFlow(int opcode) {
        return switch(opcode) {
            case Classfile.GOTO, Classfile.GOTO_W, Classfile.TABLESWITCH, Classfile.LOOKUPSWITCH, Classfile.IRETURN, Classfile.LRETURN, Classfile.FRETURN, Classfile.DRETURN, Classfile.ARETURN, Classfile.RETURN, Classfile.ATHROW -> true;
            default -> false;
        };
    }

    /**
     * Performs detection of mandatory stack map frames offsets
     * in a single bytecode traversing pass
     * @return <code>java.lang.BitSet</code> of detected frames offsets
     */
    private BitSet detectFrameOffsets() {
        var offsets = new BitSet() {
            @Override
            public void set(int i) {
                if (i < 0 || i >= bytecode.capacity())
                    generatorError("Frame offset out of bytecode range");
                super.set(i);
            }
        };
        RawBytecodeHelper bcs = new RawBytecodeHelper(bytecode);
        boolean no_control_flow = false;
        int opcode, bci;
        while (!bcs.isLastBytecode()) {
            opcode = bcs.rawNext();
            bci = bcs.bci;
            if (no_control_flow) {
                offsets.set(bci);
            }
            no_control_flow = noControlFlow(opcode);
            switch (opcode) {
                case Classfile.IF_ICMPEQ, Classfile.IF_ICMPNE, Classfile.IF_ICMPLT, Classfile.IF_ICMPGE, Classfile.IF_ICMPGT, Classfile.IF_ICMPLE, Classfile.IFEQ, Classfile.IFNE, Classfile.IFLT, Classfile.IFGE, Classfile.IFGT, Classfile.IFLE, Classfile.IF_ACMPEQ , Classfile.IF_ACMPNE , Classfile.IFNULL , Classfile.IFNONNULL, Classfile.GOTO ->
                    offsets.set(bcs.dest());
                case Classfile.GOTO_W ->
                    offsets.set(bcs.destW());
                case Classfile.TABLESWITCH, Classfile.LOOKUPSWITCH -> {
                    int aligned_bci = RawBytecodeHelper.align(bci + 1);
                    int default_ofset = bcs.getInt(aligned_bci);
                    int keys, delta;
                    if (bcs.rawCode == Classfile.TABLESWITCH) {
                        int low = bcs.getInt(aligned_bci + 4);
                        int high = bcs.getInt(aligned_bci + 2 * 4);
                        keys = high - low + 1;
                        delta = 1;
                    } else {
                        keys = bcs.getInt(aligned_bci + 4);
                        delta = 2;
                    }
                    offsets.set(bci + default_ofset);
                    for (int i = 0; i < keys; i++) {
                        offsets.set(bci + bcs.getInt(aligned_bci + (3 + i * delta) * 4));
                    }
                }
            };
        }
        for (var exhandler : exceptionTable) {
            offsets.set(labelContext.labelToBci(exhandler.handler()));
        }
        return offsets;
    }

    private static final class Frame {

        int offset;
        int localsSize, stackSize;
        int flags;
        int frameMaxStack = 0, frameMaxLocals = 0;
        boolean dirty = false;

        private final ClassHierarchyImpl classHierarchy;

        private Type[] locals, stack;

        Frame(ClassHierarchyImpl classHierarchy) {
            this.offset = -1;
            this.localsSize = 0;
            this.stackSize = 0;
            this.flags = 0;
            this.classHierarchy = classHierarchy;
            this.locals = null;
            this.stack = null;
        }

        Frame(int offset, int flags, int locals_size, int stack_size, Type[] locals, Type[] stack, ClassHierarchyImpl context) {
            this.offset = offset;
            this.localsSize = locals_size;
            this.stackSize = stack_size;
            this.flags = flags;
            this.locals = locals;
            this.stack = stack;
            this.classHierarchy = context;
        }

        public Frame(int offset, ClassHierarchyImpl context) {
            this(offset, -1, 0, 0, null, null, context);
        }

        @Override
        public String toString() {
            return (dirty ? "frame* @" : "frame @") + offset + " with locals " + (locals == null ? "[]" : Arrays.asList(locals).subList(0, localsSize)) + " and stack " + (stack == null ? "[]" : Arrays.asList(stack).subList(0, stackSize));
        }

        Frame pushStack(Type type) {
            checkStack(stackSize);
            stack[stackSize++] = type;
            return this;
        }

        void pushStack2(Type type1, Type type2) {
            checkStack(stackSize + 1);
            stack[stackSize++] = type1;
            stack[stackSize++] = type2;
        }

        Type popStack() {
            if (stackSize < 1) throw new VerifyError("Operand stack underflow");
            return stack[--stackSize];
        }

        Frame decStack(int size) {
            stackSize -= size;
            if (stackSize < 0) throw new VerifyError("Operand stack underflow");
            return this;
        }

        Frame frameInExceptionHandler(int flags) {
            return  new Frame(offset, flags, localsSize, 0, locals, new Type[] {Type.TOP_TYPE}, classHierarchy);
        }

        void initializeObject(Type old_object, Type new_object) {
            int i;
            for (i = 0; i < localsSize; i++) {
                if (locals[i].equals(old_object)) {
                    locals[i] = new_object;
                }
            }
            for (i = 0; i < stackSize; i++) {
                if (stack[i].equals(old_object)) {
                    stack[i] = new_object;
                }
            }
            if (old_object.isUninitializedThis()) {
                flags = 0;
            }
        }

        Frame checkLocal(int index) {
            if (index >= frameMaxLocals) frameMaxLocals = index + 1;
            if (locals == null) {
                locals = new Type[index + FRAME_DEFAULT_CAPACITY];
                Arrays.fill(locals, Type.TOP_TYPE);
            } else if (index >= locals.length) {
                int current = locals.length;
                locals = Arrays.copyOf(locals, index + FRAME_DEFAULT_CAPACITY);
                Arrays.fill(locals, current, locals.length, Type.TOP_TYPE);
            }
            return this;
        }

        private void checkStack(int index) {
            if (index >= frameMaxStack) frameMaxStack = index + 1;
            if (stack == null) {
                stack = new Type[index + FRAME_DEFAULT_CAPACITY];
                Arrays.fill(stack, Type.TOP_TYPE);
            } else if (index >= stack.length) {
                int current = stack.length;
                stack = Arrays.copyOf(stack, index + FRAME_DEFAULT_CAPACITY);
                Arrays.fill(stack, current, stack.length, Type.TOP_TYPE);
            }
        }

        private void setLocalRawInternal(int index, Type type) {
            checkLocal(index);
            locals[index] = type;
        }

        Type setLocalsFromArg(String name, MethodTypeDesc methodDesc, boolean isStatic, Type thisKlass) {
            localsSize = 0;
            if (!isStatic) {
                localsSize++;
                if (OBJECT_INITIALIZER_NAME.equals(name) && !ConstantDescs.CD_Object.equals(thisKlass.sym)) {
                    setLocal(0, Type.uninitialized_this_type);
                    flags |= FLAG_THIS_UNINIT;
                } else {
                    setLocalRawInternal(0, thisKlass);
                }
            }
            for (int i = 0; i < methodDesc.parameterCount(); i++) {
                localsSize += StackMapGenerator.classDescToType(methodDesc.parameterType(i), new BiConsumer<>() {
                    @Override
                    public void accept(Integer i, Type vt) {
                        setLocalRawInternal(i, vt);
                    }
                }, localsSize);
            }
            var ret = methodDesc.returnType();
            if (ret.isClassOrInterface() || ret.isArray()) {
                return Type.referenceType(ret);
            }
            return switch (ret.descriptorString()) {
                case "I" -> Type.INTEGER_TYPE;
                case "B" -> Type.BYTE_TYPE;
                case "C" -> Type.CHAR_TYPE;
                case "S" -> Type.SHORT_TYPE;
                case "Z" -> Type.BOOLEAN_TYPE;
                case "F"-> Type.FLOAT_TYPE;
                case "D" -> Type.DOUBLE_TYPE;
                case "J" -> Type.LONG_TYPE;
                case "V" -> Type.TOP_TYPE;
                default -> throw new AssertionError("Should not reach here");
            };
        }

        void copyFrom(Frame src) {
            if (locals != null && src.localsSize < locals.length) Arrays.fill(locals, src.localsSize, locals.length, Type.TOP_TYPE);
            localsSize = src.localsSize;
            checkLocal(src.localsSize - 1);
            for (int i = 0; i < src.localsSize; i++) {
                locals[i] = src.locals[i];
            }
            if (stack != null) Arrays.fill(stack, src.stackSize, stack.length, Type.TOP_TYPE);
            stackSize = src.stackSize;
            checkStack(src.stackSize - 1);
            for (int i = 0; i < src.stackSize; i++) {
                stack[i] = src.stack[i];
            }
            flags = src.flags;
        }

        void checkAssignableTo(Frame target) {
            if (target.flags == -1) {
                target.locals = locals == null ? null : Arrays.copyOf(locals, localsSize);
                target.localsSize = localsSize;
                target.stack = stack == null ? null : Arrays.copyOf(stack, stackSize);
                target.stackSize = stackSize;
                target.flags = flags;
                target.dirty = true;
            } else {
                if (target.localsSize > localsSize) {
                    target.localsSize = localsSize;
                    target.dirty = true;
                }
                for (int i = 0; i < target.localsSize; i++) {
                    merge(locals[i], target.locals, i, target);
                }
                for (int i = 0; i < target.stackSize; i++) {
                    merge(stack[i], target.stack, i, target);
                }
            }
        }

        private Type getLocalRawInternal(int index) {
            checkLocal(index);
            return locals[index];
        }

        Type getLocal(int index) {
            Type ret = getLocalRawInternal(index);
            if (index >= localsSize) {
                localsSize = index + 1;
            }
            return ret;
        }

        void getLocal2(int index) {
            getLocalRawInternal(index);
            getLocalRawInternal(index + 1);
        }

        void setLocal(int index, Type type) {
            Type old = getLocalRawInternal(index);
            if (old.isDouble() || old.isLong()) {
                setLocalRawInternal(index + 1, Type.TOP_TYPE);
            }
            if (old.isDouble2() || old.isLong2()) {
                setLocalRawInternal(index - 1, Type.TOP_TYPE);
            }
            setLocalRawInternal(index, type);
            if (index >= localsSize) {
                localsSize = index + 1;
            }
        }

        void setLocal2(int index, Type type1, Type type2) {
            Type old = getLocalRawInternal(index + 1);
            if (old.isDouble() || old.isLong()) {
                setLocalRawInternal(index + 2, Type.TOP_TYPE);
            }
            old = getLocalRawInternal(index);
            if (old.isDouble2() || old.isLong2()) {
                setLocalRawInternal(index - 1, Type.TOP_TYPE);
            }
            setLocalRawInternal(index, type1);
            setLocalRawInternal(index + 1, type2);
            if (index >= localsSize - 1) {
                localsSize = index + 2;
            }
        }

        private void merge(Type me, Type[] toTypes, int i, Frame target) {
            var to = toTypes[i];
            var newTo = to.mergeFrom(me, classHierarchy);
            if (to != newTo && !to.equals(newTo)) {
                toTypes[i] = newTo;
                target.dirty = true;
            }
        }

        private static int trimAndCompress(Type[] types, int count) {
            while (count > 0 && types[count - 1].isTop()) count--;
            int compressed = 0;
            for (int i = 0; i < count; i++) {
                if (!types[i].isCategory2_2nd()) {
                    types[compressed++] = types[i];
                }
            }
            return compressed;
        }

        void trimAndCompress() {
            localsSize = trimAndCompress(locals, localsSize);
            stackSize = trimAndCompress(stack, stackSize);
        }

        private static boolean equals(Type[] l1, Type[] l2, int commonSize) {
            if (l1 == null || l2 == null) return commonSize == 0;
            return Arrays.equals(l1, 0, commonSize, l2, 0, commonSize);
        }

        void writeTo(BufWriter out, Frame prevFrame, ConstantPoolBuilder cp) {
            int offsetDelta = offset - prevFrame.offset - 1;
            if (stackSize == 0) {
                int commonLocalsSize = localsSize > prevFrame.localsSize ? prevFrame.localsSize : localsSize;
                int diffLocalsSize = localsSize - prevFrame.localsSize;
                if (-3 <= diffLocalsSize && diffLocalsSize <= 3 && equals(locals, prevFrame.locals, commonLocalsSize)) {
                    if (diffLocalsSize == 0 && offsetDelta < 64) { //same frame
                        out.writeU1(offsetDelta);
                    } else {   //chop, same extended or append frame
                        out.writeU1(251 + diffLocalsSize);
                        out.writeU2(offsetDelta);
                        for (int i=commonLocalsSize; i<localsSize; i++) locals[i].writeTo(out, cp);
                    }
                    return;
                }
            } else if (stackSize == 1 && localsSize == prevFrame.localsSize && equals(locals, prevFrame.locals, localsSize)) {
                if (offsetDelta < 64) {  //same locals 1 stack item frame
                    out.writeU1(64 + offsetDelta);
                } else {  //same locals 1 stack item extended frame
                    out.writeU1(247);
                    out.writeU2(offsetDelta);
                }
                stack[0].writeTo(out, cp);
                return;
            }
            //full frame
            out.writeU1(255);
            out.writeU2(offsetDelta);
            out.writeU2(localsSize);
            for (int i=0; i<localsSize; i++) locals[i].writeTo(out, cp);
            out.writeU2(stackSize);
            for (int i=0; i<stackSize; i++) stack[i].writeTo(out, cp);
        }
    }

    private static final class Type {

        private Type(ClassDesc sym) {
            this(0x100, sym);
        }
        private final int data;
        final ClassDesc sym;

        @Override
        public int hashCode() {
            return sym == null ? data : sym.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Type ? (data == ((Type) obj).data) && Objects.equals(sym, ((Type) obj).sym) : false;
        }

        private static final Map<Type, String> CONSTANTS_MAP = new IdentityHashMap<>(18);

        @Override
        public String toString() {
            if (CONSTANTS_MAP.isEmpty()) {
                for (Field f : Type.class.getDeclaredFields()) {
                    var name = f.getName();
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == Type.class && name.endsWith("_TYPE")) try {
                        CONSTANTS_MAP.put((Type) f.get(null), name.substring(0, name.length() - 5));
                    } catch (IllegalAccessException ignore) {
                    }
                }
            }
            if (sym != null) {
                return sym.displayName();
            }
            if ((data & 0xff) == UNINITIALIZED) {
                return "uninit@" + (data >> 8);
            }
            return CONSTANTS_MAP.getOrDefault(this, Integer.toHexString(data));
        }

        private static final int TYPE_MASK = 0x00000003,
                REFERENCE = 0x0,
                PRIMITIVE = 0x1,
                UNINITIALIZED = 0x2,
                TYPE_QUERY = 0x3,
                CATEGORY1_FLAG = 0x01,
                CATEGORY2_FLAG = 0x02,
                CATEGORY2_2ND_FLAG = 0x04,
                NULL = 0x00000000,
                CATEGORY1 = (CATEGORY1_FLAG << 1 * BITS_PER_BYTE) | PRIMITIVE,
                CATEGORY2 = (CATEGORY2_FLAG << 1 * BITS_PER_BYTE) | PRIMITIVE,
                CATEGORY2_2ND = (CATEGORY2_2ND_FLAG << 1 * BITS_PER_BYTE) | PRIMITIVE,
                TOP = PRIMITIVE,
                BOOLEAN = (ITEM_BOOLEAN << 2 * BITS_PER_BYTE) | CATEGORY1,
                BYTE = (ITEM_BYTE << 2 * BITS_PER_BYTE) | CATEGORY1,
                SHORT = (ITEM_SHORT << 2 * BITS_PER_BYTE) | CATEGORY1,
                CHAR = (ITEM_CHAR << 2 * BITS_PER_BYTE) | CATEGORY1,
                INTEGER = (ITEM_INTEGER << 2 * BITS_PER_BYTE) | CATEGORY1,
                FLOAT = (ITEM_FLOAT << 2 * BITS_PER_BYTE) | CATEGORY1,
                LONG = (ITEM_LONG << 2 * BITS_PER_BYTE) | CATEGORY2,
                DOUBLE = (ITEM_DOUBLE << 2 * BITS_PER_BYTE) | CATEGORY2,
                LONG_2ND = (ITEM_LONG_2ND << 2 * BITS_PER_BYTE) | CATEGORY2_2ND,
                DOUBLE_2ND = (ITEM_DOUBLE_2ND << 2 * BITS_PER_BYTE) | CATEGORY2_2ND,
                BCI_MASK = 0xffff << 1 * BITS_PER_BYTE,
                BCI_FOR_THIS = 0xffff;

        private Type(int raw_data) {
            this(raw_data, null);
        }

        private Type(int raw_data, ClassDesc sym) {
            this.data = raw_data;
            this.sym = sym;
        }

        static final Type TOP_TYPE = new Type(TOP),
                NULL_TYPE = new Type(NULL),
                INTEGER_TYPE = new Type(INTEGER),
                FLOAT_TYPE = new Type(FLOAT),
                LONG_TYPE = new Type(LONG),
                LONG2_TYPE = new Type(LONG_2ND),
                DOUBLE_TYPE = new Type(DOUBLE),
                BOOLEAN_TYPE = new Type(BOOLEAN),
                BYTE_TYPE = new Type(BYTE),
                CHAR_TYPE = new Type(CHAR),
                SHORT_TYPE = new Type(SHORT),
                DOUBLE2_TYPE = new Type(DOUBLE_2ND);

        static final Type OBJECT_TYPE = new Type(CD_Object),
            THROWABLE_TYPE = new Type(CD_Throwable),
            INT_ARRAY_TYPE = new Type(CD_int.arrayType()),
            BOOLEAN_ARRAY_TYPE = new Type(CD_boolean.arrayType()),
            BYTE_ARRAY_TYPE = new Type(CD_byte.arrayType()),
            CHAR_ARRAY_TYPE = new Type(CD_char.arrayType()),
            SHORT_ARRAY_TYPE = new Type(CD_short.arrayType()),
            LONG_ARRAY_TYPE = new Type(CD_long.arrayType()),
            DOUBLE_ARRAY_TYPE = new Type(CD_double.arrayType()),
            FLOAT_ARRAY_TYPE = new Type(CD_float.arrayType());

        static Type referenceType(ClassDesc sh) {
            return new Type(sh);
        }

        static Type uninitializedType(int bci) {
            return new Type(bci << 1 * BITS_PER_BYTE | UNINITIALIZED);
        }
        static final Type uninitialized_this_type = uninitializedType(BCI_FOR_THIS);

        boolean isTop() {
            return (data == TOP);
        }

        boolean isNull() {
            return (data == NULL);
        }

        boolean isNullOrArray() {
            return isNull() || isArray();// || (sub1 != null && sub1.isNullOrArray() && sub2.isNullOrArray());
        }

        boolean isBoolean() {
            return (data == BOOLEAN);
        }

        boolean isByte() {
            return (data == BYTE);
        }

        boolean isChar() {
            return (data == CHAR);
        }

        boolean isShort() {
            return (data == SHORT);
        }

        boolean isInteger() {
            return (data == INTEGER);
        }

        boolean isLong() {
            return (data == LONG);
        }

        boolean isFloat() {
            return (data == FLOAT);
        }

        boolean isDouble() {
            return (data == DOUBLE);
        }

        boolean isLong2() {
            return (data == LONG_2ND);
        }

        boolean isDouble2() {
            return (data == DOUBLE_2ND);
        }

        boolean isReference() {
            return ((data & TYPE_MASK) == REFERENCE);
        }

        boolean isCategory1() {
            return ((data & CATEGORY1) != PRIMITIVE);
        }

        boolean isCategory2() {
            return ((data & CATEGORY2) == CATEGORY2);
        }

        boolean isCategory2_2nd() {
            return ((data & CATEGORY2_2ND) == CATEGORY2_2ND);
        }

        boolean isCheck() {
            return (data & TYPE_QUERY) == TYPE_QUERY;
        }

        boolean isObject() {
            return (isReference() && !isNull() && sym.isClassOrInterface());
        }

        boolean isArray() {
            return (isReference() && !isNull() && sym.isArray());
        }

        boolean isUninitialized() {
            return ((data & UNINITIALIZED) == UNINITIALIZED);
        }

        boolean isUninitializedThis() {
            return isUninitialized() && bci() == BCI_FOR_THIS;
        }

        int bci() {
            return ((data & BCI_MASK) >> 1 * BITS_PER_BYTE);
        }

        Type mergeFrom(Type from, ClassHierarchyImpl context) {
            if (isTop() || this == from || equals(from)) {
                return this;
            } else {
                switch (data) {
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case SHORT:
                        return from.isInteger() ? this : Type.TOP_TYPE;
                    default:
                        if (isReference() && from.isReference()) {
                            return mergeReferenceFrom(from, context);
                        } else {
                            return Type.TOP_TYPE;
                        }
                }
            }
        }

        Type mergeComponentFrom(Type from, ClassHierarchyImpl context) {
            if (isTop() || this == from || equals(from)) {
                return this;
            } else {
                switch (data) {
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case SHORT:
                        return Type.TOP_TYPE;
                    default:
                        if (isReference() && from.isReference()) {
                            return mergeReferenceFrom(from, context);
                        } else {
                            return Type.TOP_TYPE;
                        }
                }
            }
        }

        int dimensions() {
            int index = 0;
            String desc = sym.descriptorString();
            while (desc.charAt(index) == '[') {
                index++;
            }
            return index;
        }

        private static final ClassDesc CD_Cloneable = ClassDesc.of("java.lang.Cloneable");
        private static final ClassDesc CD_Serializable = ClassDesc.of("java.io.Serializable");

        private Type mergeReferenceFrom(Type from, ClassHierarchyImpl context) {
            if (from.isNull()) {
                return this;
            } else if (isNull()) {
                return from;
            } else if (sym.equals(from.sym)) {
                return this;
            } else if (isObject()) {
                if (ConstantDescs.CD_Object.equals(sym)) {
                    return this;
                }
                if (context.isInterface(sym)) {
                    if (!from.isArray() || CD_Cloneable.equals(sym) || CD_Serializable.equals(sym)) {
                        return this;
                    }
                } else if (from.isObject()) {
                    var anc = context.commonAncestor(sym, from.sym);
                    return anc == null ? this : Type.referenceType(anc);
                }
                return Type.OBJECT_TYPE;
            } else if (isArray() && from.isArray()) {
                Type compThis = getComponent();
                Type compFrom = from.getComponent();
                if (!compThis.isTop() && !compFrom.isTop()) {
                    return  compThis.mergeComponentFrom(compFrom, context).toArray();
                }
            }
            return OBJECT_TYPE;
        }

        Type toArray() {
            if (isBoolean()) {
                return BOOLEAN_ARRAY_TYPE;
            } else if (isByte()) {
                return BYTE_ARRAY_TYPE;
            } else if (isChar()) {
                return CHAR_ARRAY_TYPE;
            } else if (isShort()) {
                return SHORT_ARRAY_TYPE;
            } else if (isInteger()) {
                return INT_ARRAY_TYPE;
            } else if (isLong()) {
                return LONG_ARRAY_TYPE;
            } else if (isFloat()) {
                return FLOAT_ARRAY_TYPE;
            } else if (isDouble()) {
                return DOUBLE_ARRAY_TYPE;
            } else if (isArray() || isObject()) {
                return Type.referenceType(sym.arrayType());
            } else {
                return OBJECT_TYPE;
            }
        }

        Type getComponent() {
            if (sym.isArray()) {
                var comp = sym.componentType();
                if (comp.isPrimitive()) {
                    return switch (comp.descriptorString().charAt(0)) {
                        case 'Z' -> Type.BOOLEAN_TYPE;
                        case 'B' -> Type.BYTE_TYPE;
                        case 'C' -> Type.CHAR_TYPE;
                        case 'S' -> Type.SHORT_TYPE;
                        case 'I' -> Type.INTEGER_TYPE;
                        case 'J' -> Type.LONG_TYPE;
                        case 'F' -> Type.FLOAT_TYPE;
                        case 'D' -> Type.DOUBLE_TYPE;
                        default -> Type.TOP_TYPE;
                    };
                }
                return Type.referenceType(comp);
            }
            return Type.TOP_TYPE;
        }

        void writeTo(BufWriter bw, ConstantPoolBuilder cp) {
            if (isTop()) {
                bw.writeU1(ITEM_TOP);
            } else if (isInteger()) {
                bw.writeU1(ITEM_INTEGER);
            } else if (isFloat()) {
                bw.writeU1(ITEM_FLOAT);
            } else if (isLong()) {
                bw.writeU1(ITEM_LONG);
            } else if (isDouble()) {
                bw.writeU1(ITEM_DOUBLE);
            } else if (isNull()) {
                bw.writeU1(ITEM_NULL);
            } else if (isUninitializedThis()) {
                bw.writeU1(ITEM_UNINITIALIZED_THIS);
            } else if (isReference()) {
                bw.writeU1(ITEM_OBJECT);
                bw.writeU2(cp.classEntry(cp.utf8Entry(Util.toInternalName(sym))).index());
            } else if (isUninitialized()) {
                bw.writeU1(ITEM_UNINITIALIZED);
                bw.writeU2(bci());
            }
        }
    }
}
