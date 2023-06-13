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

package jdk.internal.classfile;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.FieldRefEntry;
import jdk.internal.classfile.constantpool.InterfaceMethodRefEntry;
import jdk.internal.classfile.constantpool.InvokeDynamicEntry;
import jdk.internal.classfile.constantpool.LoadableConstantEntry;
import jdk.internal.classfile.constantpool.MemberRefEntry;
import jdk.internal.classfile.constantpool.MethodRefEntry;
import jdk.internal.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BlockCodeBuilderImpl;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.CatchBuilderImpl;
import jdk.internal.classfile.impl.ChainedCodeBuilder;
import jdk.internal.classfile.impl.LabelImpl;
import jdk.internal.classfile.impl.NonterminalCodeBuilder;
import jdk.internal.classfile.impl.TerminalCodeBuilder;
import jdk.internal.classfile.instruction.ArrayLoadInstruction;
import jdk.internal.classfile.instruction.ArrayStoreInstruction;
import jdk.internal.classfile.instruction.BranchInstruction;
import jdk.internal.classfile.instruction.CharacterRange;
import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.ConvertInstruction;
import jdk.internal.classfile.instruction.ExceptionCatch;
import jdk.internal.classfile.instruction.FieldInstruction;
import jdk.internal.classfile.instruction.IncrementInstruction;
import jdk.internal.classfile.instruction.InvokeDynamicInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;
import jdk.internal.classfile.instruction.LineNumber;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.LocalVariable;
import jdk.internal.classfile.instruction.LocalVariableType;
import jdk.internal.classfile.instruction.LookupSwitchInstruction;
import jdk.internal.classfile.instruction.MonitorInstruction;
import jdk.internal.classfile.instruction.NewMultiArrayInstruction;
import jdk.internal.classfile.instruction.NewObjectInstruction;
import jdk.internal.classfile.instruction.NewPrimitiveArrayInstruction;
import jdk.internal.classfile.instruction.NewReferenceArrayInstruction;
import jdk.internal.classfile.instruction.NopInstruction;
import jdk.internal.classfile.instruction.OperatorInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;
import jdk.internal.classfile.instruction.StackInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import jdk.internal.classfile.instruction.SwitchCase;
import jdk.internal.classfile.instruction.TableSwitchInstruction;
import jdk.internal.classfile.instruction.ThrowInstruction;
import jdk.internal.classfile.instruction.TypeCheckInstruction;

import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.BytecodeHelpers.handleDescToHandleInfo;
import jdk.internal.classfile.impl.TransformingCodeBuilder;

/**
 * A builder for code attributes (method bodies).  Builders are not created
 * directly; they are passed to handlers by methods such as {@link
 * MethodBuilder#withCode(Consumer)} or to code transforms.  The elements of a
 * code can be specified abstractly, by passing a {@link CodeElement} to {@link
 * #with(ClassfileElement)} or concretely by calling the various {@code withXxx}
 * methods.
 *
 * @see CodeTransform
 */
public sealed interface CodeBuilder
        extends ClassfileBuilder<CodeElement, CodeBuilder>
        permits CodeBuilder.BlockCodeBuilder, ChainedCodeBuilder, TerminalCodeBuilder, NonterminalCodeBuilder {

    /**
     * {@return the {@link CodeModel} representing the method body being transformed,
     * if this code builder represents the transformation of some {@link CodeModel}}
     */
    Optional<CodeModel> original();

    /** {@return a fresh unbound label} */
    Label newLabel();

    /** {@return the label associated with the beginning of the current block}
     * If the current {@linkplain CodeBuilder} is not a "block" builder, such as
     * those provided by {@link #block(Consumer)} or {@link #ifThenElse(Consumer, Consumer)},
     * the current block will be the entire method body. */
    Label startLabel();

    /** {@return the label associated with the end of the current block}
     * If the current {@linkplain CodeBuilder} is not a "block" builder, such as
     * those provided by {@link #block(Consumer)} or {@link #ifThenElse(Consumer, Consumer)},
     * the current block will be the entire method body. */
    Label endLabel();

    /**
     * {@return the local variable slot associated with the receiver}.
     *
     * @throws IllegalStateException if this is not a static method
     */
    int receiverSlot();

    /**
     * {@return the local variable slot associated with the specified parameter}.
     * The returned value is adjusted for the receiver slot (if the method is
     * an instance method) and for the requirement that {@code long} and {@code double}
     * values require two slots.
     *
     * @param paramNo the index of the parameter
     */
    int parameterSlot(int paramNo);

    /**
     * {@return the local variable slot of a fresh local variable}  This method
     * makes reasonable efforts to determine which slots are in use and which
     * are not.  When transforming a method, fresh locals begin at the {@code maxLocals}
     * of the original method.  For a method being built directly, fresh locals
     * begin after the last parameter slot.
     *
     * <p>If the current code builder is a "block" code builder provided by
     * {@link #block(Consumer)}, {@link #ifThen(Consumer)}, or
     * {@link #ifThenElse(Consumer, Consumer)}, at the end of the block, locals
     * are reset to their value at the beginning of the block.
     *
     * @param typeKind the type of the local variable
     */
    int allocateLocal(TypeKind typeKind);

    /**
     * Apply a transform to the code built by a handler, directing results to this builder.
     *
     * @param transform the transform to apply to the code built by the handler
     * @param handler the handler that receives a {@linkplain CodeBuilder} to
     * build the code.
     * @return this builder
     */
    default CodeBuilder transforming(CodeTransform transform, Consumer<CodeBuilder> handler) {
        var resolved = transform.resolve(this);
        resolved.startHandler().run();
        handler.accept(new TransformingCodeBuilder(this, resolved.consumer()));
        resolved.endHandler().run();
        return this;
    }

    /**
     * A builder for blocks of code.
     */
    sealed interface BlockCodeBuilder extends CodeBuilder
            permits BlockCodeBuilderImpl {
        /**
         * {@return the label locating where control is passed back to the parent block.}
         * A branch to this label "break"'s out of the current block.
         * <p>
         * If an instruction occurring immediately after the built block's last instruction would
         * be reachable from that last instruction, then a {@linkplain #goto_ goto} instruction
         * targeting the "break" label is appended to the built block.
         */
        Label breakLabel();
    }

    /**
     * Add a lexical block to the method being built.
     * <p>
     * Within this block, the {@link #startLabel()} and {@link #endLabel()} correspond
     * to the start and end of the block, and the {@link BlockCodeBuilder#breakLabel()}
     * also corresponds to the end of the block.
     *
     * @param handler handler that receives a {@linkplain BlockCodeBuilder} to
     * generate the body of the lexical block.
     */
    default CodeBuilder block(Consumer<BlockCodeBuilder> handler) {
        Label breakLabel = newLabel();
        BlockCodeBuilderImpl child = new BlockCodeBuilderImpl(this, breakLabel);
        child.start();
        handler.accept(child);
        child.end();
        labelBinding(breakLabel);
        return this;
    }

    /**
     * Add an "if-then" block that is conditional on the boolean value
     * on top of the operand stack.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for the "then" block corresponds to the
     * end of that block.
     *
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @return this builder
     */
    default CodeBuilder ifThen(Consumer<BlockCodeBuilder> thenHandler) {
        return ifThen(Opcode.IFNE, thenHandler);
    }

    /**
     * Add an "if-then" block that is conditional on the value(s) on top of the operand stack
     * in accordance with the given opcode.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for the "then" block corresponds to the
     * end of that block.
     *
     * @param opcode the operation code for a branch instructions that accepts one or two operands on the stack
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @return this builder
     * @throws IllegalArgumentException if the operation code is not for a branch instruction that accepts
     * one or two operands
     */
    default CodeBuilder ifThen(Opcode opcode,
                               Consumer<BlockCodeBuilder> thenHandler) {
        if (opcode.kind() != Opcode.Kind.BRANCH || opcode.primaryTypeKind() == TypeKind.VoidType) {
            throw new IllegalArgumentException("Illegal branch opcode: " + opcode);
        }

        Label breakLabel = newLabel();
        BlockCodeBuilderImpl thenBlock = new BlockCodeBuilderImpl(this, breakLabel);
        branchInstruction(BytecodeHelpers.reverseBranchOpcode(opcode), thenBlock.endLabel());
        thenBlock.start();
        thenHandler.accept(thenBlock);
        thenBlock.end();
        labelBinding(breakLabel);
        return this;
    }

    /**
     * Add an "if-then-else" block that is conditional on the boolean value
     * on top of the operand stack.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for each block corresponds to the
     * end of the "else" block.
     *
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @param elseHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code else}
     * @return this builder
     */
    default CodeBuilder ifThenElse(Consumer<BlockCodeBuilder> thenHandler,
                                   Consumer<BlockCodeBuilder> elseHandler) {
        return ifThenElse(Opcode.IFNE, thenHandler, elseHandler);
    }

    /**
     * Add an "if-then-else" block that is conditional on the value(s) on top of the operand stack
     * in accordance with the given opcode.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for each block corresponds to the
     * end of the "else" block.
     *
     * @param opcode the operation code for a branch instructions that accepts one or two operands on the stack
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @param elseHandler handler that receives a {@linkplain BlockCodeBuilder} to
     *                    generate the body of the {@code else}
     * @return this builder
     * @throws IllegalArgumentException if the operation code is not for a branch instruction that accepts
     * one or two operands
     */
    default CodeBuilder ifThenElse(Opcode opcode,
                                   Consumer<BlockCodeBuilder> thenHandler,
                                   Consumer<BlockCodeBuilder> elseHandler) {
        if (opcode.kind() != Opcode.Kind.BRANCH || opcode.primaryTypeKind() == TypeKind.VoidType) {
            throw new IllegalArgumentException("Illegal branch opcode: " + opcode);
        }

        Label breakLabel = newLabel();
        BlockCodeBuilderImpl thenBlock = new BlockCodeBuilderImpl(this, breakLabel);
        BlockCodeBuilderImpl elseBlock = new BlockCodeBuilderImpl(this, breakLabel);
        branchInstruction(BytecodeHelpers.reverseBranchOpcode(opcode), elseBlock.startLabel());
        thenBlock.start();
        thenHandler.accept(thenBlock);
        if (thenBlock.reachable())
            thenBlock.branchInstruction(Opcode.GOTO, thenBlock.breakLabel());
        thenBlock.end();
        elseBlock.start();
        elseHandler.accept(elseBlock);
        elseBlock.end();
        labelBinding(breakLabel);
        return this;
    }

    /**
     * A builder to add catch blocks.
     *
     * @see #trying
     */
    sealed interface CatchBuilder permits CatchBuilderImpl {
        /**
         * Adds a catch block that catches an exception of the given type.
         * <p>
         * The caught exception will be on top of the operand stack when the catch block is entered.
         * <p>
         * If the type of exception is {@code null} then the catch block catches all exceptions.
         *
         * @param exceptionType the type of exception to catch.
         * @param catchHandler handler that receives a {@linkplain CodeBuilder} to
         *                     generate the body of the catch block.
         * @return this builder
         * @throws IllegalArgumentException if an existing catch block catches an exception of the given type.
         * @see #catchingMulti
         * @see #catchingAll
         */
        CatchBuilder catching(ClassDesc exceptionType, Consumer<BlockCodeBuilder> catchHandler);

        /**
         * Adds a catch block that catches exceptions of the given types.
         * <p>
         * The caught exception will be on top of the operand stack when the catch block is entered.
         * <p>
         * If the type of exception is {@code null} then the catch block catches all exceptions.
         *
         * @param exceptionTypes the types of exception to catch.
         * @param catchHandler handler that receives a {@linkplain CodeBuilder} to
         *                     generate the body of the catch block.
         * @return this builder
         * @throws IllegalArgumentException if an existing catch block catches one or more exceptions of the given types.
         * @see #catching
         * @see #catchingAll
         */
        CatchBuilder catchingMulti(List<ClassDesc> exceptionTypes, Consumer<BlockCodeBuilder> catchHandler);

        /**
         * Adds a "catch" block that catches all exceptions.
         * <p>
         * The caught exception will be on top of the operand stack when the catch block is entered.
         *
         * @param catchAllHandler handler that receives a {@linkplain CodeBuilder} to
         *                        generate the body of the catch block
         * @throws IllegalArgumentException if an existing catch block catches all exceptions.
         * @see #catching
         * @see #catchingMulti
         */
        void catchingAll(Consumer<BlockCodeBuilder> catchAllHandler);
    }

    /**
     * Adds a "try-catch" block comprising one try block and zero or more catch blocks.
     * Exceptions thrown by instructions in the try block may be caught by catch blocks.
     *
     * @param tryHandler handler that receives a {@linkplain CodeBuilder} to
     *                   generate the body of the try block.
     * @param catchesHandler a handler that receives a {@linkplain CatchBuilder}
     *                       to generate bodies of catch blocks.
     * @return this builder
     * @throws IllegalArgumentException if the try block is empty.
     * @see CatchBuilder
     */
    default CodeBuilder trying(Consumer<BlockCodeBuilder> tryHandler,
                               Consumer<CatchBuilder> catchesHandler) {
        Label tryCatchEnd = newLabel();

        BlockCodeBuilderImpl tryBlock = new BlockCodeBuilderImpl(this, tryCatchEnd);
        tryBlock.start();
        tryHandler.accept(tryBlock);
        tryBlock.end();

        // Check for empty try block
        if (tryBlock.isEmpty()) {
            throw new IllegalArgumentException("The body of the try block is empty");
        }

        var catchBuilder = new CatchBuilderImpl(this, tryBlock, tryCatchEnd);
        catchesHandler.accept(catchBuilder);
        catchBuilder.finish();

        return this;
    }

    // Base convenience methods

    default CodeBuilder loadInstruction(TypeKind tk, int slot) {
        with(LoadInstruction.of(tk, slot));
        return this;
    }

    default CodeBuilder storeInstruction(TypeKind tk, int slot) {
        with(StoreInstruction.of(tk, slot));
        return this;
    }

    default CodeBuilder incrementInstruction(int slot, int val) {
        with(IncrementInstruction.of(slot, val));
        return this;
    }

    default CodeBuilder branchInstruction(Opcode op, Label target) {
        with(BranchInstruction.of(op, target));
        return this;
    }

    default CodeBuilder lookupSwitchInstruction(Label defaultTarget, List<SwitchCase> cases) {
        with(LookupSwitchInstruction.of(defaultTarget, cases));
        return this;
    }

    default CodeBuilder tableSwitchInstruction(int lowValue, int highValue, Label defaultTarget, List<SwitchCase> cases) {
        with(TableSwitchInstruction.of(lowValue, highValue, defaultTarget, cases));
        return this;
    }

    default CodeBuilder returnInstruction(TypeKind tk) {
        with(ReturnInstruction.of(tk));
        return this;
    }

    default CodeBuilder throwInstruction() {
        with(ThrowInstruction.of());
        return this;
    }

    default CodeBuilder fieldInstruction(Opcode opcode, FieldRefEntry ref) {
        with(FieldInstruction.of(opcode, ref));
        return this;
    }

    default CodeBuilder fieldInstruction(Opcode opcode, ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(opcode, constantPool().fieldRefEntry(owner, name, type));
    }

    default CodeBuilder invokeInstruction(Opcode opcode, MemberRefEntry ref) {
        return with(InvokeInstruction.of(opcode, ref));
    }

    default CodeBuilder invokeInstruction(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
        return invokeInstruction(opcode,
                isInterface ? constantPool().interfaceMethodRefEntry(owner, name, desc)
                            : constantPool().methodRefEntry(owner, name, desc));
    }

    default CodeBuilder invokeDynamicInstruction(InvokeDynamicEntry ref) {
        with(InvokeDynamicInstruction.of(ref));
        return this;
    }

    default CodeBuilder invokeDynamicInstruction(DynamicCallSiteDesc desc) {
        MethodHandleEntry bsMethod = handleDescToHandleInfo(constantPool(), (DirectMethodHandleDesc) desc.bootstrapMethod());
        var cpArgs = desc.bootstrapArgs();
        List<LoadableConstantEntry> bsArguments = new ArrayList<>(cpArgs.length);
        for (var constantValue : cpArgs) {
            bsArguments.add(BytecodeHelpers.constantEntry(constantPool(), constantValue));
        }
        BootstrapMethodEntry bm = constantPool().bsmEntry(bsMethod, bsArguments);
        NameAndTypeEntry nameAndType = constantPool().nameAndTypeEntry(desc.invocationName(), desc.invocationType());
        invokeDynamicInstruction(constantPool().invokeDynamicEntry(bm, nameAndType));
        return this;
    }

    default CodeBuilder newObjectInstruction(ClassEntry type) {
        with(NewObjectInstruction.of(type));
        return this;
    }

    default CodeBuilder newObjectInstruction(ClassDesc type) {
        return newObjectInstruction(constantPool().classEntry(type));
    }

    default CodeBuilder newPrimitiveArrayInstruction(TypeKind typeKind) {
        with(NewPrimitiveArrayInstruction.of(typeKind));
        return this;
    }

    default CodeBuilder newReferenceArrayInstruction(ClassEntry type) {
        with(NewReferenceArrayInstruction.of(type));
        return this;
    }

    default CodeBuilder newReferenceArrayInstruction(ClassDesc type) {
        return newReferenceArrayInstruction(constantPool().classEntry(type));
    }

    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassEntry type) {
        with(NewMultiArrayInstruction.of(type, dimensions));
        return this;
    }

    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassDesc type) {
        return newMultidimensionalArrayInstruction(dimensions, constantPool().classEntry(type));
    }

    default CodeBuilder arrayLoadInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayLoadOpcode(tk);
        with(ArrayLoadInstruction.of(opcode));
        return this;
    }

    default CodeBuilder arrayStoreInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayStoreOpcode(tk);
        with(ArrayStoreInstruction.of(opcode));
        return this;
    }

    default CodeBuilder typeCheckInstruction(Opcode opcode,
                                             ClassEntry type) {
        with(TypeCheckInstruction.of(opcode, type));
        return this;
    }

    default CodeBuilder typeCheckInstruction(Opcode opcode, ClassDesc type) {
        return typeCheckInstruction(opcode, constantPool().classEntry(type));
    }

    default CodeBuilder convertInstruction(TypeKind fromType, TypeKind toType) {
        with(ConvertInstruction.of(fromType, toType));
        return this;
    }

    default CodeBuilder stackInstruction(Opcode opcode) {
        with(StackInstruction.of(opcode));
        return this;
    }

    default CodeBuilder operatorInstruction(Opcode opcode) {
        with(OperatorInstruction.of(opcode));
        return this;
    }

    default CodeBuilder constantInstruction(Opcode opcode, ConstantDesc value) {
        BytecodeHelpers.validateValue(opcode, value);
        return with(switch (opcode) {
            case SIPUSH, BIPUSH -> ConstantInstruction.ofArgument(opcode, ((Number)value).intValue());
            case LDC, LDC_W, LDC2_W -> ConstantInstruction.ofLoad(opcode, BytecodeHelpers.constantEntry(constantPool(), value));
            default -> ConstantInstruction.ofIntrinsic(opcode);
        });
    }

    default CodeBuilder constantInstruction(ConstantDesc value) {
        //avoid switch expressions here
        if (value == null || value == ConstantDescs.NULL)
            return aconst_null();
        if (value instanceof Integer iVal)
            return switch (iVal) {
                case -1 -> iconst_m1();
                case  0 -> iconst_0();
                case  1 -> iconst_1();
                case  2 -> iconst_2();
                case  3 -> iconst_3();
                case  4 -> iconst_4();
                case  5 -> iconst_5();
                default -> (iVal >= Byte.MIN_VALUE && iVal <= Byte.MAX_VALUE) ? bipush(iVal)
                         : (iVal >= Short.MIN_VALUE && iVal <= Short.MAX_VALUE) ? sipush(iVal)
                         : ldc(constantPool().intEntry(iVal));
            };
        if (value instanceof Long lVal)
            return lVal == 0l ? lconst_0()
                 : lVal == 1l ? lconst_1()
                 : ldc(constantPool().longEntry(lVal));
        if (value instanceof Float fVal)
            return Float.floatToRawIntBits(fVal) == 0 ? fconst_0()
                 : fVal == 1.0f ? fconst_1()
                 : fVal == 2.0f ? fconst_2()
                 : ldc(constantPool().floatEntry(fVal));
        if (value instanceof Double dVal)
            return Double.doubleToRawLongBits(dVal) == 0l ? dconst_0()
                 : dVal == 1.0d ? dconst_1()
                 : ldc(constantPool().doubleEntry(dVal));
        return ldc(BytecodeHelpers.constantEntry(constantPool(), value));
    }

    default CodeBuilder monitorInstruction(Opcode opcode) {
        with(MonitorInstruction.of(opcode));
        return null;
    }

    default CodeBuilder nopInstruction() {
        with(NopInstruction.of());
        return this;
    }


    default CodeBuilder nop() {
        return nopInstruction();
    }

    // Base pseudo-instruction builder methods

    default Label newBoundLabel() {
        var label = newLabel();
        labelBinding(label);
        return label;
    }

    default CodeBuilder labelBinding(Label label) {
        with((LabelImpl) label);
        return this;
    }

    default CodeBuilder lineNumber(int line) {
        with(LineNumber.of(line));
        return this;
    }

    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassEntry catchType) {
        with(ExceptionCatch.of(handler, start, end, Optional.of(catchType)));
        return this;
    }

    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, Optional<ClassEntry> catchType) {
        with(ExceptionCatch.of(handler, start, end, catchType));
        return this;
    }

    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassDesc catchType) {
        requireNonNull(catchType);
        return exceptionCatch(start, end, handler, constantPool().classEntry(catchType));
    }

    default CodeBuilder exceptionCatchAll(Label start, Label end, Label handler) {
        with(ExceptionCatch.of(handler, start, end));
        return this;
    }

    default CodeBuilder characterRange(Label startScope, Label endScope, int characterRangeStart, int characterRangeEnd, int flags) {
        with(CharacterRange.of(startScope, endScope, characterRangeStart, characterRangeEnd, flags));
        return this;
    }

    default CodeBuilder localVariable(int slot, Utf8Entry nameEntry, Utf8Entry descriptorEntry, Label startScope, Label endScope) {
        with(LocalVariable.of(slot, nameEntry, descriptorEntry, startScope, endScope));
        return this;
    }

    default CodeBuilder localVariable(int slot, String name, ClassDesc descriptor, Label startScope, Label endScope) {
        return localVariable(slot,
                             constantPool().utf8Entry(name),
                             constantPool().utf8Entry(descriptor.descriptorString()),
                             startScope, endScope);
    }

    default CodeBuilder localVariableType(int slot, Utf8Entry nameEntry, Utf8Entry signatureEntry, Label startScope, Label endScope) {
        with(LocalVariableType.of(slot, nameEntry, signatureEntry, startScope, endScope));
        return this;
    }

    default CodeBuilder localVariableType(int slot, String name, Signature signature, Label startScope, Label endScope) {
        return localVariableType(slot,
                                 constantPool().utf8Entry(name),
                                 constantPool().utf8Entry(signature.signatureString()),
                                 startScope, endScope);
    }

    // Bytecode conveniences

    default CodeBuilder aconst_null() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ACONST_NULL));
    }

    default CodeBuilder aaload() {
        return arrayLoadInstruction(TypeKind.ReferenceType);
    }

    default CodeBuilder aastore() {
        return arrayStoreInstruction(TypeKind.ReferenceType);
    }

    default CodeBuilder aload(int slot) {
        return loadInstruction(TypeKind.ReferenceType, slot);
    }

    default CodeBuilder anewarray(ClassEntry classEntry) {
        return newReferenceArrayInstruction(classEntry);
    }

    default CodeBuilder anewarray(ClassDesc className) {
        return newReferenceArrayInstruction(constantPool().classEntry(className));
    }

    default CodeBuilder areturn() {
        return returnInstruction(TypeKind.ReferenceType);
    }

    default CodeBuilder arraylength() {
        return operatorInstruction(Opcode.ARRAYLENGTH);
    }

    default CodeBuilder astore(int slot) {
        return storeInstruction(TypeKind.ReferenceType, slot);
    }

    default CodeBuilder athrow() {
        return throwInstruction();
    }

    default CodeBuilder baload() {
        return arrayLoadInstruction(TypeKind.ByteType);
    }

    default CodeBuilder bastore() {
        return arrayStoreInstruction(TypeKind.ByteType);
    }

    default CodeBuilder bipush(int b) {
        return constantInstruction(Opcode.BIPUSH, b);
    }

    default CodeBuilder caload() {
        return arrayLoadInstruction(TypeKind.CharType);
    }

    default CodeBuilder castore() {
        return arrayStoreInstruction(TypeKind.CharType);
    }

    default CodeBuilder checkcast(ClassEntry type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    default CodeBuilder checkcast(ClassDesc type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    default CodeBuilder d2f() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.FloatType);
    }

    default CodeBuilder d2i() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.IntType);
    }

    default CodeBuilder d2l() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.LongType);
    }

    default CodeBuilder dadd() {
        return operatorInstruction(Opcode.DADD);
    }

    default CodeBuilder daload() {
        return arrayLoadInstruction(TypeKind.DoubleType);
    }

    default CodeBuilder dastore() {
        return arrayStoreInstruction(TypeKind.DoubleType);
    }

    default CodeBuilder dcmpg() {
        return operatorInstruction(Opcode.DCMPG);
    }

    default CodeBuilder dcmpl() {
        return operatorInstruction(Opcode.DCMPL);
    }

    default CodeBuilder dconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_0));
    }

    default CodeBuilder dconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_1));
    }

    default CodeBuilder ddiv() {
        return operatorInstruction(Opcode.DDIV);
    }

    default CodeBuilder dload(int slot) {
        return loadInstruction(TypeKind.DoubleType, slot);
    }

    default CodeBuilder dmul() {
        return operatorInstruction(Opcode.DMUL);
    }

    default CodeBuilder dneg() {
        return operatorInstruction(Opcode.DNEG);
    }

    default CodeBuilder drem() {
        return operatorInstruction(Opcode.DREM);
    }

    default CodeBuilder dreturn() {
        return returnInstruction(TypeKind.DoubleType);
    }

    default CodeBuilder dstore(int slot) {
        return storeInstruction(TypeKind.DoubleType, slot);
    }

    default CodeBuilder dsub() {
        return operatorInstruction(Opcode.DSUB);
    }

    default CodeBuilder dup() {
        return stackInstruction(Opcode.DUP);
    }

    default CodeBuilder dup2() {
        return stackInstruction(Opcode.DUP2);
    }

    default CodeBuilder dup2_x1() {
        return stackInstruction(Opcode.DUP2_X1);
    }

    default CodeBuilder dup2_x2() {
        return stackInstruction(Opcode.DUP2_X2);
    }

    default CodeBuilder dup_x1() {
        return stackInstruction(Opcode.DUP_X1);
    }

    default CodeBuilder dup_x2() {
        return stackInstruction(Opcode.DUP_X2);
    }

    default CodeBuilder f2d() {
        return convertInstruction(TypeKind.FloatType, TypeKind.DoubleType);
    }

    default CodeBuilder f2i() {
        return convertInstruction(TypeKind.FloatType, TypeKind.IntType);
    }

    default CodeBuilder f2l() {
        return convertInstruction(TypeKind.FloatType, TypeKind.LongType);
    }

    default CodeBuilder fadd() {
        return operatorInstruction(Opcode.FADD);
    }

    default CodeBuilder faload() {
        return arrayLoadInstruction(TypeKind.FloatType);
    }

    default CodeBuilder fastore() {
        return arrayStoreInstruction(TypeKind.FloatType);
    }

    default CodeBuilder fcmpg() {
        return operatorInstruction(Opcode.FCMPG);
    }

    default CodeBuilder fcmpl() {
        return operatorInstruction(Opcode.FCMPL);
    }

    default CodeBuilder fconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_0));
    }

    default CodeBuilder fconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_1));
    }

    default CodeBuilder fconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_2));
    }

    default CodeBuilder fdiv() {
        return operatorInstruction(Opcode.FDIV);
    }

    default CodeBuilder fload(int slot) {
        return loadInstruction(TypeKind.FloatType, slot);
    }

    default CodeBuilder fmul() {
        return operatorInstruction(Opcode.FMUL);
    }

    default CodeBuilder fneg() {
        return operatorInstruction(Opcode.FNEG);
    }

    default CodeBuilder frem() {
        return operatorInstruction(Opcode.FREM);
    }

    default CodeBuilder freturn() {
        return returnInstruction(TypeKind.FloatType);
    }

    default CodeBuilder fstore(int slot) {
        return storeInstruction(TypeKind.FloatType, slot);
    }

    default CodeBuilder fsub() {
        return operatorInstruction(Opcode.FSUB);
    }

    default CodeBuilder getfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETFIELD, ref);
    }

    default CodeBuilder getfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETFIELD, owner, name, type);
    }

    default CodeBuilder getstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETSTATIC, ref);
    }

    default CodeBuilder getstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETSTATIC, owner, name, type);
    }

    default CodeBuilder goto_(Label target) {
        return branchInstruction(Opcode.GOTO, target);
    }

    default CodeBuilder goto_w(Label target) {
        return branchInstruction(Opcode.GOTO_W, target);
    }

    default CodeBuilder i2b() {
        return convertInstruction(TypeKind.IntType, TypeKind.ByteType);
    }

    default CodeBuilder i2c() {
        return convertInstruction(TypeKind.IntType, TypeKind.CharType);
    }

    default CodeBuilder i2d() {
        return convertInstruction(TypeKind.IntType, TypeKind.DoubleType);
    }

    default CodeBuilder i2f() {
        return convertInstruction(TypeKind.IntType, TypeKind.FloatType);
    }

    default CodeBuilder i2l() {
        return convertInstruction(TypeKind.IntType, TypeKind.LongType);
    }

    default CodeBuilder i2s() {
        return convertInstruction(TypeKind.IntType, TypeKind.ShortType);
    }

    default CodeBuilder iadd() {
        return operatorInstruction(Opcode.IADD);
    }

    default CodeBuilder iaload() {
        return arrayLoadInstruction(TypeKind.IntType);
    }

    default CodeBuilder iand() {
        return operatorInstruction(Opcode.IAND);
    }

    default CodeBuilder iastore() {
        return arrayStoreInstruction(TypeKind.IntType);
    }

    default CodeBuilder iconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_0));
    }

    default CodeBuilder iconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_1));
    }

    default CodeBuilder iconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_2));
    }

    default CodeBuilder iconst_3() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_3));
    }

    default CodeBuilder iconst_4() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_4));
    }

    default CodeBuilder iconst_5() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_5));
    }

    default CodeBuilder iconst_m1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_M1));
    }

    default CodeBuilder idiv() {
        return operatorInstruction(Opcode.IDIV);
    }

    default CodeBuilder if_acmpeq(Label target) {
        return branchInstruction(Opcode.IF_ACMPEQ, target);
    }

    default CodeBuilder if_acmpne(Label target) {
        return branchInstruction(Opcode.IF_ACMPNE, target);
    }

    default CodeBuilder if_icmpeq(Label target) {
        return branchInstruction(Opcode.IF_ICMPEQ, target);
    }

    default CodeBuilder if_icmpge(Label target) {
        return branchInstruction(Opcode.IF_ICMPGE, target);
    }

    default CodeBuilder if_icmpgt(Label target) {
        return branchInstruction(Opcode.IF_ICMPGT, target);
    }

    default CodeBuilder if_icmple(Label target) {
        return branchInstruction(Opcode.IF_ICMPLE, target);
    }

    default CodeBuilder if_icmplt(Label target) {
        return branchInstruction(Opcode.IF_ICMPLT, target);
    }

    default CodeBuilder if_icmpne(Label target) {
        return branchInstruction(Opcode.IF_ICMPNE, target);
    }

    default CodeBuilder if_nonnull(Label target) {
        return branchInstruction(Opcode.IFNONNULL, target);
    }

    default CodeBuilder if_null(Label target) {
        return branchInstruction(Opcode.IFNULL, target);
    }

    default CodeBuilder ifeq(Label target) {
        return branchInstruction(Opcode.IFEQ, target);
    }

    default CodeBuilder ifge(Label target) {
        return branchInstruction(Opcode.IFGE, target);
    }

    default CodeBuilder ifgt(Label target) {
        return branchInstruction(Opcode.IFGT, target);
    }

    default CodeBuilder ifle(Label target) {
        return branchInstruction(Opcode.IFLE, target);
    }

    default CodeBuilder iflt(Label target) {
        return branchInstruction(Opcode.IFLT, target);
    }

    default CodeBuilder ifne(Label target) {
        return branchInstruction(Opcode.IFNE, target);
    }

    default CodeBuilder iinc(int slot, int val) {
        return incrementInstruction(slot, val);
    }

    default CodeBuilder iload(int slot) {
        return loadInstruction(TypeKind.IntType, slot);
    }

    default CodeBuilder imul() {
        return operatorInstruction(Opcode.IMUL);
    }

    default CodeBuilder ineg() {
        return operatorInstruction(Opcode.INEG);
    }

    default CodeBuilder instanceof_(ClassEntry target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, target);
    }

    default CodeBuilder instanceof_(ClassDesc target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, constantPool().classEntry(target));
    }

    default CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        return invokeDynamicInstruction(ref);
    }

    default CodeBuilder invokedynamic(DynamicCallSiteDesc ref) {
        return invokeDynamicInstruction(ref);
    }

    default CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, ref);
    }

    default CodeBuilder invokeinterface(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, constantPool().interfaceMethodRefEntry(owner, name, type));
    }

    default CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    default CodeBuilder invokespecial(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, false);
    }

    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, isInterface);
    }

    default CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    default CodeBuilder invokestatic(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, false);
    }

    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, isInterface);
    }

    default CodeBuilder invokevirtual(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, ref);
    }

    default CodeBuilder invokevirtual(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, owner, name, type, false);
    }

    default CodeBuilder ior() {
        return operatorInstruction(Opcode.IOR);
    }

    default CodeBuilder irem() {
        return operatorInstruction(Opcode.IREM);
    }

    default CodeBuilder ireturn() {
        return returnInstruction(TypeKind.IntType);
    }

    default CodeBuilder ishl() {
        return operatorInstruction(Opcode.ISHL);
    }

    default CodeBuilder ishr() {
        return operatorInstruction(Opcode.ISHR);
    }

    default CodeBuilder istore(int slot) {
        return storeInstruction(TypeKind.IntType, slot);
    }

    default CodeBuilder isub() {
        return operatorInstruction(Opcode.ISUB);
    }

    default CodeBuilder iushr() {
        return operatorInstruction(Opcode.IUSHR);
    }

    default CodeBuilder ixor() {
        return operatorInstruction(Opcode.IXOR);
    }

    default CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        return lookupSwitchInstruction(defaultTarget, cases);
    }

    default CodeBuilder l2d() {
        return convertInstruction(TypeKind.LongType, TypeKind.DoubleType);
    }

    default CodeBuilder l2f() {
        return convertInstruction(TypeKind.LongType, TypeKind.FloatType);
    }

    default CodeBuilder l2i() {
        return convertInstruction(TypeKind.LongType, TypeKind.IntType);
    }

    default CodeBuilder ladd() {
        return operatorInstruction(Opcode.LADD);
    }

    default CodeBuilder laload() {
        return arrayLoadInstruction(TypeKind.LongType);
    }

    default CodeBuilder land() {
        return operatorInstruction(Opcode.LAND);
    }

    default CodeBuilder lastore() {
        return arrayStoreInstruction(TypeKind.LongType);
    }

    default CodeBuilder lcmp() {
        return operatorInstruction(Opcode.LCMP);
    }

    default CodeBuilder lconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_0));
    }

    default CodeBuilder lconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_1));
    }

    default CodeBuilder ldc(LoadableConstantEntry entry) {
        return with(ConstantInstruction.ofLoad(
                entry.typeKind().slotSize() == 2 ? Opcode.LDC2_W
                : entry.index() > 0xff ? Opcode.LDC_W
                : Opcode.LDC, entry));
    }

    default CodeBuilder ldiv() {
        return operatorInstruction(Opcode.LDIV);
    }

    default CodeBuilder lload(int slot) {
        return loadInstruction(TypeKind.LongType, slot);
    }

    default CodeBuilder lmul() {
        return operatorInstruction(Opcode.LMUL);
    }

    default CodeBuilder lneg() {
        return operatorInstruction(Opcode.LNEG);
    }

    default CodeBuilder lor() {
        return operatorInstruction(Opcode.LOR);
    }

    default CodeBuilder lrem() {
        return operatorInstruction(Opcode.LREM);
    }

    default CodeBuilder lreturn() {
        return returnInstruction(TypeKind.LongType);
    }

    default CodeBuilder lshl() {
        return operatorInstruction(Opcode.LSHL);
    }

    default CodeBuilder lshr() {
        return operatorInstruction(Opcode.LSHR);
    }

    default CodeBuilder lstore(int slot) {
        return storeInstruction(TypeKind.LongType, slot);
    }

    default CodeBuilder lsub() {
        return operatorInstruction(Opcode.LSUB);
    }

    default CodeBuilder lushr() {
        return operatorInstruction(Opcode.LUSHR);
    }

    default CodeBuilder lxor() {
        return operatorInstruction(Opcode.LXOR);
    }

    default CodeBuilder monitorenter() {
        return monitorInstruction(Opcode.MONITORENTER);
    }

    default CodeBuilder monitorexit() {
        return monitorInstruction(Opcode.MONITOREXIT);
    }

    default CodeBuilder multianewarray(ClassEntry array, int dims) {
        return newMultidimensionalArrayInstruction(dims, array);
    }

    default CodeBuilder multianewarray(ClassDesc array, int dims) {
        return newMultidimensionalArrayInstruction(dims, constantPool().classEntry(array));
    }

    default CodeBuilder new_(ClassEntry clazz) {
        return newObjectInstruction(clazz);
    }

    default CodeBuilder new_(ClassDesc clazz) {
        return newObjectInstruction(constantPool().classEntry(clazz));
    }

    default CodeBuilder newarray(TypeKind typeKind) {
        return newPrimitiveArrayInstruction(typeKind);
    }

    default CodeBuilder pop() {
        return stackInstruction(Opcode.POP);
    }

    default CodeBuilder pop2() {
        return stackInstruction(Opcode.POP2);
    }

    default CodeBuilder putfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTFIELD, ref);
    }

    default CodeBuilder putfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTFIELD, owner, name, type);
    }

    default CodeBuilder putstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTSTATIC, ref);
    }

    default CodeBuilder putstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTSTATIC, owner, name, type);
    }

    default CodeBuilder return_() {
        return returnInstruction(TypeKind.VoidType);
    }

    default CodeBuilder saload() {
        return arrayLoadInstruction(TypeKind.ShortType);
    }

    default CodeBuilder sastore() {
        return arrayStoreInstruction(TypeKind.ShortType);
    }

    default CodeBuilder sipush(int s) {
        return constantInstruction(Opcode.SIPUSH, s);
    }

    default CodeBuilder swap() {
        return stackInstruction(Opcode.SWAP);
    }

    default CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        return tableSwitchInstruction(low, high, defaultTarget, cases);
    }

    default CodeBuilder tableswitch(Label defaultTarget, List<SwitchCase> cases) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (var c : cases) {
            int i = c.caseValue();
            if (i < low) low = i;
            if (i > high) high = i;
        }
        return tableSwitchInstruction(low, high, defaultTarget, cases);
    }

    // Structured conveniences:

    //   allocLocal(type)
    //   returnFromMethod(inferred)
}
