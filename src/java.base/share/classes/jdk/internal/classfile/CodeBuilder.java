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
     * @return this builder
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

    /**
     * Load instruction
     * @param tk load type
     * @param slot local variable slot
     * @return this builder
     */

    default CodeBuilder loadInstruction(TypeKind tk, int slot) {
        with(LoadInstruction.of(tk, slot));
        return this;
    }

    /**
     * Store instruction
     * @param tk store type
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder storeInstruction(TypeKind tk, int slot) {
        with(StoreInstruction.of(tk, slot));
        return this;
    }

    /**
     * Increment local variable by constant
     * @param slot local variable slot
     * @param val increment value
     * @return this builder
     */
    default CodeBuilder incrementInstruction(int slot, int val) {
        with(IncrementInstruction.of(slot, val));
        return this;
    }

    /**
     * Branch instruction
     * @see Opcode.Kind#BRANCH
     * @param op branch opcode
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder branchInstruction(Opcode op, Label target) {
        with(BranchInstruction.of(op, target));
        return this;
    }

    /**
     * Access jump table by key match and jump
     * @param defaultTarget default jump target
     * @param cases switch cases
     * @return this builder
     */
    default CodeBuilder lookupSwitchInstruction(Label defaultTarget, List<SwitchCase> cases) {
        with(LookupSwitchInstruction.of(defaultTarget, cases));
        return this;
    }

    /**
     * Access jump table by index and jump
     * @param lowValue low key value
     * @param highValue high key value
     * @param defaultTarget default jump target
     * @param cases switch cases
     * @return this builder
     */
    default CodeBuilder tableSwitchInstruction(int lowValue, int highValue, Label defaultTarget, List<SwitchCase> cases) {
        with(TableSwitchInstruction.of(lowValue, highValue, defaultTarget, cases));
        return this;
    }

    /**
     * Return instruction
     * @param tk return type
     * @return this builder
     */
    default CodeBuilder returnInstruction(TypeKind tk) {
        with(ReturnInstruction.of(tk));
        return this;
    }

    /**
     * Throw exception or error
     * @return this builder
     */
    default CodeBuilder throwInstruction() {
        with(ThrowInstruction.of());
        return this;
    }

    /**
     * Field access instruction
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode field access opcode
     * @param ref field reference
     * @return this builder
     */
    default CodeBuilder fieldInstruction(Opcode opcode, FieldRefEntry ref) {
        with(FieldInstruction.of(opcode, ref));
        return this;
    }

    /**
     * Field access instruction
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode field access opcode
     * @param owner class
     * @param name field name
     * @param type field type
     * @return this builder
     */
    default CodeBuilder fieldInstruction(Opcode opcode, ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(opcode, constantPool().fieldRefEntry(owner, name, type));
    }

    /**
     * Invoke a method or constructor
     * @see Opcode.Kind#INVOKE
     * @param opcode invoke opcode
     * @param ref interface method or method reference
     * @return this builder
     */
    default CodeBuilder invokeInstruction(Opcode opcode, MemberRefEntry ref) {
        return with(InvokeInstruction.of(opcode, ref));
    }

    /**
     * Invoke a method or constructor
     * @see Opcode.Kind#INVOKE
     * @param opcode invoke opcode
     * @param owner class
     * @param name method name
     * @param desc method type
     * @param isInterface interface method invocation indication
     * @return this builder
     */
    default CodeBuilder invokeInstruction(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
        return invokeInstruction(opcode,
                isInterface ? constantPool().interfaceMethodRefEntry(owner, name, desc)
                            : constantPool().methodRefEntry(owner, name, desc));
    }

    /**
     * Invoke a dynamically-computed call site
     * @param ref dynamic call site
     * @return this builder
     */
    default CodeBuilder invokeDynamicInstruction(InvokeDynamicEntry ref) {
        with(InvokeDynamicInstruction.of(ref));
        return this;
    }

    /**
     *
     * @param desc
     * @return this builder
     */
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

    /**
     * Create new object
     * @param type class
     * @return this builder
     */
    default CodeBuilder newObjectInstruction(ClassEntry type) {
        with(NewObjectInstruction.of(type));
        return this;
    }

    /**
     * Create new object
     * @param type class
     * @return this builder
     */
    default CodeBuilder newObjectInstruction(ClassDesc type) {
        return newObjectInstruction(constantPool().classEntry(type));
    }

    /**
     * Create new array
     * @param typeKind primitive component type
     * @return this builder
     */
    default CodeBuilder newPrimitiveArrayInstruction(TypeKind typeKind) {
        with(NewPrimitiveArrayInstruction.of(typeKind));
        return this;
    }

    /**
     * Create new array of reference
     * @param type component type
     * @return this builder
     */
    default CodeBuilder newReferenceArrayInstruction(ClassEntry type) {
        with(NewReferenceArrayInstruction.of(type));
        return this;
    }

    /**
     * Create new array of reference
     * @param type component type
     * @return this builder
     */
    default CodeBuilder newReferenceArrayInstruction(ClassDesc type) {
        return newReferenceArrayInstruction(constantPool().classEntry(type));
    }

    /**
     * Create new multidimensional array
     * @param dimensions number of dimensions
     * @param type array type
     * @return this builder
     */
    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassEntry type) {
        with(NewMultiArrayInstruction.of(type, dimensions));
        return this;
    }

    /**
     * Create new multidimensional array
     * @param dimensions number of dimensions
     * @param type array type
     * @return this builder
     */
    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassDesc type) {
        return newMultidimensionalArrayInstruction(dimensions, constantPool().classEntry(type));
    }

    /**
     * Array load instruction
     * @param tk array element type
     * @return this builder
     */
    default CodeBuilder arrayLoadInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayLoadOpcode(tk);
        with(ArrayLoadInstruction.of(opcode));
        return this;
    }

    /**
     * Array store instruction
     * @param tk array element type
     * @return this builder
     */
    default CodeBuilder arrayStoreInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayStoreOpcode(tk);
        with(ArrayStoreInstruction.of(opcode));
        return this;
    }

    /**
     * Type check instruction
     * @see Opcode.Kind#TYPE_CHECK
     * @param opcode type check instruction opcode
     * @param type type
     * @return this builder
     */
    default CodeBuilder typeCheckInstruction(Opcode opcode,
                                             ClassEntry type) {
        with(TypeCheckInstruction.of(opcode, type));
        return this;
    }

    /**
     * Type check instruction
     * @see Opcode.Kind#TYPE_CHECK
     * @param opcode type check instruction opcode
     * @param type type
     * @return this builder
     */
    default CodeBuilder typeCheckInstruction(Opcode opcode, ClassDesc type) {
        return typeCheckInstruction(opcode, constantPool().classEntry(type));
    }

    /**
     * Convert instruction
     * @param fromType source type
     * @param toType target type
     * @return this builder
     */
    default CodeBuilder convertInstruction(TypeKind fromType, TypeKind toType) {
        with(ConvertInstruction.of(fromType, toType));
        return this;
    }

    /**
     * Stack instruction
     * @param opcode stack instruction opcode
     * @see Opcode.Kind#STACK
     * @return this builder
     */
    default CodeBuilder stackInstruction(Opcode opcode) {
        with(StackInstruction.of(opcode));
        return this;
    }

    /**
     * Operator instruction
     * @see Opcode.Kind#OPERATOR
     * @param opcode operator instruction opcode
     * @return this builder
     */
    default CodeBuilder operatorInstruction(Opcode opcode) {
        with(OperatorInstruction.of(opcode));
        return this;
    }

    /**
     * Constant instruction
     * @see Opcode.Kind#CONSTANT
     * @param opcode constant instruction opcode
     * @param value constant value
     * @return this builder
     */
    default CodeBuilder constantInstruction(Opcode opcode, ConstantDesc value) {
        BytecodeHelpers.validateValue(opcode, value);
        return with(switch (opcode) {
            case SIPUSH, BIPUSH -> ConstantInstruction.ofArgument(opcode, ((Number)value).intValue());
            case LDC, LDC_W, LDC2_W -> ConstantInstruction.ofLoad(opcode, BytecodeHelpers.constantEntry(constantPool(), value));
            default -> ConstantInstruction.ofIntrinsic(opcode);
        });
    }

    /**
     * Constant instruction
     * @param value constant value
     * @return this builder
     */
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
        return ldc(value);
    }

    /**
     * Monitor instruction
     * @see Opcode.Kind#MONITOR
     * @param opcode monitor instruction opcode
     * @return this builder
     */
    default CodeBuilder monitorInstruction(Opcode opcode) {
        with(MonitorInstruction.of(opcode));
        return null;
    }

    /**
     * Do nothing instruction
     * @return this builder
     */
    default CodeBuilder nopInstruction() {
        with(NopInstruction.of());
        return this;
    }

    /**
     * Do nothing instruction
     * @return this builder
     */
    default CodeBuilder nop() {
        return nopInstruction();
    }

    // Base pseudo-instruction builder methods

    /**
     * Create new label bound with current position
     * @return this builder
     */

    default Label newBoundLabel() {
        var label = newLabel();
        labelBinding(label);
        return label;
    }

    /**
     * Bind label with current position
     * @param label label
     * @return this builder
     */
    default CodeBuilder labelBinding(Label label) {
        with((LabelImpl) label);
        return this;
    }

    /**
     * Declare source line number of current position
     * @param line line number
     * @return this builder
     */
    default CodeBuilder lineNumber(int line) {
        with(LineNumber.of(line));
        return this;
    }

    /**
     * Exception table entry
     * @param start try block start
     * @param end try block end
     * @param handler exception handler start
     * @param catchType catch type or null to catch all exceptions and errors
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassEntry catchType) {
        with(ExceptionCatch.of(handler, start, end, Optional.of(catchType)));
        return this;
    }

    /**
     * Exception table entry
     * @param start try block start
     * @param end try block end
     * @param handler exception handler start
     * @param catchType optional catch type, empty to catch all exceptions and errors
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, Optional<ClassEntry> catchType) {
        with(ExceptionCatch.of(handler, start, end, catchType));
        return this;
    }

    /**
     * Exception table entry
     * @param start try block start
     * @param end try block end
     * @param handler exception handler start
     * @param catchType catch type
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassDesc catchType) {
        requireNonNull(catchType);
        return exceptionCatch(start, end, handler, constantPool().classEntry(catchType));
    }

    /**
     * Exception table entry catching all exceptions and errors
     * @param start try block start
     * @param end try block end
     * @param handler exception handler start
     * @return this builder
     */
    default CodeBuilder exceptionCatchAll(Label start, Label end, Label handler) {
        with(ExceptionCatch.of(handler, start, end));
        return this;
    }

    /**
     * Character range entry
     * @param startScope start scope of the character range
     * @param endScope end scope of the character range
     * @param characterRangeStart the encoded start of the character range region (inclusive)
     * @param characterRangeEnd the encoded end of the character range region (exclusive)
     * @param flags flags word, indicating the kind of range
     * @return this builder
     */
    default CodeBuilder characterRange(Label startScope, Label endScope, int characterRangeStart, int characterRangeEnd, int flags) {
        with(CharacterRange.of(startScope, endScope, characterRangeStart, characterRangeEnd, flags));
        return this;
    }

    /**
     * Local variable entry
     * @param slot local variable slot
     * @param nameEntry variable name
     * @param descriptorEntry variable descriptor
     * @param startScope start scope of the variable
     * @param endScope end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariable(int slot, Utf8Entry nameEntry, Utf8Entry descriptorEntry, Label startScope, Label endScope) {
        with(LocalVariable.of(slot, nameEntry, descriptorEntry, startScope, endScope));
        return this;
    }

    /**
     * Local variable entry
     * @param slot local variable slot
     * @param name variable name
     * @param descriptor variable descriptor
     * @param startScope start scope of the variable
     * @param endScope end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariable(int slot, String name, ClassDesc descriptor, Label startScope, Label endScope) {
        return localVariable(slot,
                             constantPool().utf8Entry(name),
                             constantPool().utf8Entry(descriptor.descriptorString()),
                             startScope, endScope);
    }

    /**
     * Local variable type entry
     * @param slot local variable slot
     * @param nameEntry variable name
     * @param signatureEntry variable signature
     * @param startScope start scope of the variable
     * @param endScope end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariableType(int slot, Utf8Entry nameEntry, Utf8Entry signatureEntry, Label startScope, Label endScope) {
        with(LocalVariableType.of(slot, nameEntry, signatureEntry, startScope, endScope));
        return this;
    }

    /**
     * Local variable type entry
     * @param slot local variable slot
     * @param name variable name
     * @param signature variable signature
     * @param startScope start scope of the variable
     * @param endScope end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariableType(int slot, String name, Signature signature, Label startScope, Label endScope) {
        return localVariableType(slot,
                                 constantPool().utf8Entry(name),
                                 constantPool().utf8Entry(signature.signatureString()),
                                 startScope, endScope);
    }

    // Bytecode conveniences

    /**
     * Push null
     * @return this builder
     */

    default CodeBuilder aconst_null() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ACONST_NULL));
    }

    /**
     * Load reference from array
     * @return this builder
     */
    default CodeBuilder aaload() {
        return arrayLoadInstruction(TypeKind.ReferenceType);
    }

    /**
     * Store into reference array
     * @return this builder
     */
    default CodeBuilder aastore() {
        return arrayStoreInstruction(TypeKind.ReferenceType);
    }

    /**
     * Load reference from local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder aload(int slot) {
        return loadInstruction(TypeKind.ReferenceType, slot);
    }

    /**
     * Create new array of reference
     * @param classEntry component type
     * @return this builder
     */
    default CodeBuilder anewarray(ClassEntry classEntry) {
        return newReferenceArrayInstruction(classEntry);
    }

    /**
     * Create new array of reference
     * @param className component type
     * @return this builder
     */
    default CodeBuilder anewarray(ClassDesc className) {
        return newReferenceArrayInstruction(constantPool().classEntry(className));
    }

    /**
     * Return reference from method
     * @return this builder
     */
    default CodeBuilder areturn() {
        return returnInstruction(TypeKind.ReferenceType);
    }

    /**
     * Get length of array
     * @return this builder
     */
    default CodeBuilder arraylength() {
        return operatorInstruction(Opcode.ARRAYLENGTH);
    }

    /**
     * Store reference into local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder astore(int slot) {
        return storeInstruction(TypeKind.ReferenceType, slot);
    }

    /**
     * Throw exception or error
     * @return this builder
     */
    default CodeBuilder athrow() {
        return throwInstruction();
    }

    /**
     * Load byte from array
     * @return this builder
     */
    default CodeBuilder baload() {
        return arrayLoadInstruction(TypeKind.ByteType);
    }

    /**
     * Store into byte array
     * @return this builder
     */
    default CodeBuilder bastore() {
        return arrayStoreInstruction(TypeKind.ByteType);
    }

    /**
     * Push byte
     * @param b byte
     * @return this builder
     */
    default CodeBuilder bipush(int b) {
        return constantInstruction(Opcode.BIPUSH, b);
    }

    /**
     * Load char from array
     * @return this builder
     */
    default CodeBuilder caload() {
        return arrayLoadInstruction(TypeKind.CharType);
    }

    /**
     * Store into char array
     * @return this builder
     */
    default CodeBuilder castore() {
        return arrayStoreInstruction(TypeKind.CharType);
    }

    /**
     * Check whether object is of given type
     * @param type object type
     * @return this builder
     */
    default CodeBuilder checkcast(ClassEntry type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    /**
     * Check whether object is of given type
     * @param type object type
     * @return this builder
     */
    default CodeBuilder checkcast(ClassDesc type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    /**
     * Convert double to float
     * @return this builder
     */
    default CodeBuilder d2f() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.FloatType);
    }

    /**
     * Convert double to int
     * @return this builder
     */
    default CodeBuilder d2i() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.IntType);
    }

    /**
     * Convert double to long
     * @return this builder
     */
    default CodeBuilder d2l() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.LongType);
    }

    /**
     * Add double
     * @return this builder
     */
    default CodeBuilder dadd() {
        return operatorInstruction(Opcode.DADD);
    }

    /**
     * Load double from array
     * @return this builder
     */
    default CodeBuilder daload() {
        return arrayLoadInstruction(TypeKind.DoubleType);
    }

    /**
     * Store into double array
     * @return this builder
     */
    default CodeBuilder dastore() {
        return arrayStoreInstruction(TypeKind.DoubleType);
    }

    /**
     * Compare double
     * @return this builder
     */
    default CodeBuilder dcmpg() {
        return operatorInstruction(Opcode.DCMPG);
    }

    /**
     * Compare double
     * @return this builder
     */
    default CodeBuilder dcmpl() {
        return operatorInstruction(Opcode.DCMPL);
    }

    /**
     * Push double constant 0
     * @return this builder
     */
    default CodeBuilder dconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_0));
    }

    /**
     * Push double constant 1
     * @return this builder
     */
    default CodeBuilder dconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_1));
    }

    /**
     * Divide double
     * @return this builder
     */
    default CodeBuilder ddiv() {
        return operatorInstruction(Opcode.DDIV);
    }

    /**
     * Load double from local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder dload(int slot) {
        return loadInstruction(TypeKind.DoubleType, slot);
    }

    /**
     * Multiply double
     * @return this builder
     */
    default CodeBuilder dmul() {
        return operatorInstruction(Opcode.DMUL);
    }

    /**
     * Negate double
     * @return this builder
     */
    default CodeBuilder dneg() {
        return operatorInstruction(Opcode.DNEG);
    }

    /**
     * Remainder double
     * @return this builder
     */
    default CodeBuilder drem() {
        return operatorInstruction(Opcode.DREM);
    }

    /**
     * Return double from method
     * @return this builder
     */
    default CodeBuilder dreturn() {
        return returnInstruction(TypeKind.DoubleType);
    }

    /**
     * Store double into local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder dstore(int slot) {
        return storeInstruction(TypeKind.DoubleType, slot);
    }

    /**
     * Subtract double
     * @return this builder
     */
    default CodeBuilder dsub() {
        return operatorInstruction(Opcode.DSUB);
    }

    /**
     * Duplicate the top operand stack value
     * @return this builder
     */
    default CodeBuilder dup() {
        return stackInstruction(Opcode.DUP);
    }

    /**
     * Duplicate the top one or two operand stack value
     * @return this builder
     */
    default CodeBuilder dup2() {
        return stackInstruction(Opcode.DUP2);
    }

    /**
     * Duplicate the top one or two operand stack values and insert two or three
     * values down
     * @return this builder
     */
    default CodeBuilder dup2_x1() {
        return stackInstruction(Opcode.DUP2_X1);
    }

    /**
     * Duplicate the top one or two operand stack values and insert two, three,
     * or four values down
     * @return this builder
     */
    default CodeBuilder dup2_x2() {
        return stackInstruction(Opcode.DUP2_X2);
    }

    /**
     * Duplicate the top operand stack value and insert two values down
     * @return this builder
     */
    default CodeBuilder dup_x1() {
        return stackInstruction(Opcode.DUP_X1);
    }

    /**
     * Duplicate the top operand stack value and insert two or three values down
     * @return this builder
     */
    default CodeBuilder dup_x2() {
        return stackInstruction(Opcode.DUP_X2);
    }

    /**
     * Convert float to double
     * @return this builder
     */
    default CodeBuilder f2d() {
        return convertInstruction(TypeKind.FloatType, TypeKind.DoubleType);
    }

    /**
     * Convert float to int
     * @return this builder
     */
    default CodeBuilder f2i() {
        return convertInstruction(TypeKind.FloatType, TypeKind.IntType);
    }

    /**
     * Convert float to long
     * @return this builder
     */
    default CodeBuilder f2l() {
        return convertInstruction(TypeKind.FloatType, TypeKind.LongType);
    }

    /**
     * Add float
     * @return this builder
     */
    default CodeBuilder fadd() {
        return operatorInstruction(Opcode.FADD);
    }

    /**
     * Load float from array
     * @return this builder
     */
    default CodeBuilder faload() {
        return arrayLoadInstruction(TypeKind.FloatType);
    }

    /**
     * Store into float array
     * @return this builder
     */
    default CodeBuilder fastore() {
        return arrayStoreInstruction(TypeKind.FloatType);
    }

    /**
     * Compare float
     * @return this builder
     */
    default CodeBuilder fcmpg() {
        return operatorInstruction(Opcode.FCMPG);
    }

    /**
     * Compare float
     * @return this builder
     */
    default CodeBuilder fcmpl() {
        return operatorInstruction(Opcode.FCMPL);
    }

    /**
     * Push float constant 0
     * @return this builder
     */
    default CodeBuilder fconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_0));
    }

    /**
     * Push float constant 1
     * @return this builder
     */
    default CodeBuilder fconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_1));
    }

    /**
     * Push float constant 2
     * @return this builder
     */
    default CodeBuilder fconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_2));
    }

    /**
     * Divide float
     * @return this builder
     */
    default CodeBuilder fdiv() {
        return operatorInstruction(Opcode.FDIV);
    }

    /**
     * Load float from local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder fload(int slot) {
        return loadInstruction(TypeKind.FloatType, slot);
    }

    /**
     * Multiply float
     * @return this builder
     */
    default CodeBuilder fmul() {
        return operatorInstruction(Opcode.FMUL);
    }

    /**
     * Negate float
     * @return this builder
     */
    default CodeBuilder fneg() {
        return operatorInstruction(Opcode.FNEG);
    }

    /**
     * Remainder float
     * @return this builder
     */
    default CodeBuilder frem() {
        return operatorInstruction(Opcode.FREM);
    }

    /**
     * Return float from method
     * @return this builder
     */
    default CodeBuilder freturn() {
        return returnInstruction(TypeKind.FloatType);
    }

    /**
     * Store float into local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder fstore(int slot) {
        return storeInstruction(TypeKind.FloatType, slot);
    }

    /**
     * Subtract float
     * @return this builder
     */
    default CodeBuilder fsub() {
        return operatorInstruction(Opcode.FSUB);
    }

    /**
     * Fetch field from object
     * @param ref field reference
     * @return this builder
     */
    default CodeBuilder getfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETFIELD, ref);
    }

    /**
     * Fetch field from object
     * @param owner class
     * @param name field name
     * @param type field type
     * @return this builder
     */
    default CodeBuilder getfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETFIELD, owner, name, type);
    }

    /**
     * Get static field from class
     * @param ref field reference
     * @return this builder
     */
    default CodeBuilder getstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETSTATIC, ref);
    }

    /**
     * Get static field from class
     * @param owner class
     * @param name field name
     * @param type field type
     * @return this builder
     */
    default CodeBuilder getstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETSTATIC, owner, name, type);
    }

    /**
     * Branch always
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder goto_(Label target) {
        return branchInstruction(Opcode.GOTO, target);
    }

    /**
     * Branch always (wide index)
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder goto_w(Label target) {
        return branchInstruction(Opcode.GOTO_W, target);
    }

    /**
     * Convert int to byte
     * @return this builder
     */
    default CodeBuilder i2b() {
        return convertInstruction(TypeKind.IntType, TypeKind.ByteType);
    }

    /**
     * Convert int to char
     * @return this builder
     */
    default CodeBuilder i2c() {
        return convertInstruction(TypeKind.IntType, TypeKind.CharType);
    }

    /**
     * Convert int to double
     * @return this builder
     */
    default CodeBuilder i2d() {
        return convertInstruction(TypeKind.IntType, TypeKind.DoubleType);
    }

    /**
     * Convert int to float
     * @return this builder
     */
    default CodeBuilder i2f() {
        return convertInstruction(TypeKind.IntType, TypeKind.FloatType);
    }

    /**
     * Convert int to long
     * @return this builder
     */
    default CodeBuilder i2l() {
        return convertInstruction(TypeKind.IntType, TypeKind.LongType);
    }

    /**
     * Convert int to short
     * @return this builder
     */
    default CodeBuilder i2s() {
        return convertInstruction(TypeKind.IntType, TypeKind.ShortType);
    }

    /**
     * Add int
     * @return this builder
     */
    default CodeBuilder iadd() {
        return operatorInstruction(Opcode.IADD);
    }

    /**
     * Load int from array
     * @return this builder
     */
    default CodeBuilder iaload() {
        return arrayLoadInstruction(TypeKind.IntType);
    }

    /**
     * Boolean AND int
     * @return this builder
     */
    default CodeBuilder iand() {
        return operatorInstruction(Opcode.IAND);
    }

    /**
     * Store into int array
     * @return this builder
     */
    default CodeBuilder iastore() {
        return arrayStoreInstruction(TypeKind.IntType);
    }

    /**
     * Push int constant 0
     * @return this builder
     */
    default CodeBuilder iconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_0));
    }

    /**
     * Push int constant 1
     * @return this builder
     */
    default CodeBuilder iconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_1));
    }

    /**
     * Push int constant 2
     * @return this builder
     */
    default CodeBuilder iconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_2));
    }

    /**
     * Push int constant 3
     * @return this builder
     */
    default CodeBuilder iconst_3() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_3));
    }

    /**
     * Push int constant 4
     * @return this builder
     */
    default CodeBuilder iconst_4() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_4));
    }

    /**
     * Push int constant 5
     * @return this builder
     */
    default CodeBuilder iconst_5() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_5));
    }

    /**
     * Push int constant -1
     * @return this builder
     */
    default CodeBuilder iconst_m1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_M1));
    }

    /**
     * Divide int
     * @return this builder
     */
    default CodeBuilder idiv() {
        return operatorInstruction(Opcode.IDIV);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_acmpeq(Label target) {
        return branchInstruction(Opcode.IF_ACMPEQ, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_acmpne(Label target) {
        return branchInstruction(Opcode.IF_ACMPNE, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmpeq(Label target) {
        return branchInstruction(Opcode.IF_ICMPEQ, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmpge(Label target) {
        return branchInstruction(Opcode.IF_ICMPGE, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmpgt(Label target) {
        return branchInstruction(Opcode.IF_ICMPGT, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmple(Label target) {
        return branchInstruction(Opcode.IF_ICMPLE, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmplt(Label target) {
        return branchInstruction(Opcode.IF_ICMPLT, target);
    }

    /**
     * Branch if int comparison succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_icmpne(Label target) {
        return branchInstruction(Opcode.IF_ICMPNE, target);
    }

    /**
     * Branch if reference is not null
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_nonnull(Label target) {
        return branchInstruction(Opcode.IFNONNULL, target);
    }

    /**
     * Branch if reference is null
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder if_null(Label target) {
        return branchInstruction(Opcode.IFNULL, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder ifeq(Label target) {
        return branchInstruction(Opcode.IFEQ, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder ifge(Label target) {
        return branchInstruction(Opcode.IFGE, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder ifgt(Label target) {
        return branchInstruction(Opcode.IFGT, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder ifle(Label target) {
        return branchInstruction(Opcode.IFLE, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder iflt(Label target) {
        return branchInstruction(Opcode.IFLT, target);
    }

    /**
     * Branch if int comparison with zero succeeds
     * @param target branch target
     * @return this builder
     */
    default CodeBuilder ifne(Label target) {
        return branchInstruction(Opcode.IFNE, target);
    }

    /**
     * Increment local variable by constant
     * @param slot local variable slot
     * @param val increment value
     * @return this builder
     */
    default CodeBuilder iinc(int slot, int val) {
        return incrementInstruction(slot, val);
    }

    /**
     * Load int from local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder iload(int slot) {
        return loadInstruction(TypeKind.IntType, slot);
    }

    /**
     * Multiply int
     * @return this builder
     */
    default CodeBuilder imul() {
        return operatorInstruction(Opcode.IMUL);
    }

    /**
     * Negate int
     * @return this builder
     */
    default CodeBuilder ineg() {
        return operatorInstruction(Opcode.INEG);
    }

    /**
     * Determine if object is of given type
     * @param target target type
     * @return this builder
     */
    default CodeBuilder instanceof_(ClassEntry target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, target);
    }

    /**
     * Determine if object is of given type
     * @param target target type
     * @return this builder
     */
    default CodeBuilder instanceof_(ClassDesc target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, constantPool().classEntry(target));
    }

    /**
     * Invoke a dynamically-computed call site
     * @param ref dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        return invokeDynamicInstruction(ref);
    }

    /**
     * Invoke a dynamically-computed call site
     * @param ref dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(DynamicCallSiteDesc ref) {
        return invokeDynamicInstruction(ref);
    }

    /**
     * Invoke interface method
     * @param ref interface method reference
     * @return this builder
     */
    default CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, ref);
    }

    /**
     * Invoke interface method
     * @param owner class
     * @param name method name
     * @param type method type
     * @return this builder
     */
    default CodeBuilder invokeinterface(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, constantPool().interfaceMethodRefEntry(owner, name, type));
    }

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref interface method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param owner class
     * @param name method name
     * @param type method type
     * @return this builder
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, false);
    }

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param owner class
     * @param name method name
     * @param type method type
     * @param isInterface interface method invocation indication
     * @return this builder
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, isInterface);
    }

    /**
     * Invoke a class (static) method
     * @param ref interface method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Invoke a class (static) method
     * @param ref method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Invoke a class (static) method
     * @param owner class
     * @param name method name
     * @param type method type
     * @return this builder
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, false);
    }

    /**
     * Invoke a class (static) method
     * @param owner class
     * @param name method name
     * @param type method type
     * @param isInterface
     * @return this builder
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, isInterface);
    }

    /**
     * Invoke instance method; dispatch based on class
     * @param ref method reference
     * @return this builder
     */
    default CodeBuilder invokevirtual(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, ref);
    }

    /**
     * Invoke instance method; dispatch based on class
     * @param owner class
     * @param name method name
     * @param type method type
     * @return this builder
     */
    default CodeBuilder invokevirtual(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, owner, name, type, false);
    }

    /**
     * Boolean OR int
     * @return this builder
     */
    default CodeBuilder ior() {
        return operatorInstruction(Opcode.IOR);
    }

    /**
     * Remainder int
     * @return this builder
     */
    default CodeBuilder irem() {
        return operatorInstruction(Opcode.IREM);
    }

    /**
     * Return int from method
     * @return this builder
     */
    default CodeBuilder ireturn() {
        return returnInstruction(TypeKind.IntType);
    }

    /**
     * Shift left int
     * @return this builder
     */
    default CodeBuilder ishl() {
        return operatorInstruction(Opcode.ISHL);
    }

    /**
     * Shift right int
     * @return this builder
     */
    default CodeBuilder ishr() {
        return operatorInstruction(Opcode.ISHR);
    }

    /**
     * Store int into local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder istore(int slot) {
        return storeInstruction(TypeKind.IntType, slot);
    }

    /**
     * Subtract int
     * @return this builder
     */
    default CodeBuilder isub() {
        return operatorInstruction(Opcode.ISUB);
    }

    /**
     * Logical shift right int
     * @return this builder
     */
    default CodeBuilder iushr() {
        return operatorInstruction(Opcode.IUSHR);
    }

    /**
     * Boolean XOR int
     * @return this builder
     */
    default CodeBuilder ixor() {
        return operatorInstruction(Opcode.IXOR);
    }

    /**
     * Access jump table by key match and jump
     * @param defaultTarget default jump target
     * @param cases switch cases
     * @return this builder
     */
    default CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        return lookupSwitchInstruction(defaultTarget, cases);
    }

    /**
     * Convert long to double
     * @return this builder
     */
    default CodeBuilder l2d() {
        return convertInstruction(TypeKind.LongType, TypeKind.DoubleType);
    }

    /**
     * Convert long to float
     * @return this builder
     */
    default CodeBuilder l2f() {
        return convertInstruction(TypeKind.LongType, TypeKind.FloatType);
    }

    /**
     * Convert long to int
     * @return this builder
     */
    default CodeBuilder l2i() {
        return convertInstruction(TypeKind.LongType, TypeKind.IntType);
    }

    /**
     * Add long
     * @return this builder
     */
    default CodeBuilder ladd() {
        return operatorInstruction(Opcode.LADD);
    }

    /**
     * Load long from array
     * @return this builder
     */
    default CodeBuilder laload() {
        return arrayLoadInstruction(TypeKind.LongType);
    }

    /**
     * Boolean AND long
     * @return this builder
     */
    default CodeBuilder land() {
        return operatorInstruction(Opcode.LAND);
    }

    /**
     * Store into long array
     * @return this builder
     */
    default CodeBuilder lastore() {
        return arrayStoreInstruction(TypeKind.LongType);
    }

    /**
     * Compare long
     * @return this builder
     */
    default CodeBuilder lcmp() {
        return operatorInstruction(Opcode.LCMP);
    }

    /**
     * Push long constant 0
     * @return this builder
     */
    default CodeBuilder lconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_0));
    }

    /**
     * Push long constant 1
     * @return this builder
     */
    default CodeBuilder lconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_1));
    }

    /**
     * Push item from run-time constant pool
     * @param value
     * @return this builder
     */
    default CodeBuilder ldc(ConstantDesc value) {
        return ldc(BytecodeHelpers.constantEntry(constantPool(), value));
    }

    /**
     * Push item from run-time constant pool
     * @param entry
     * @return this builder
     */
    default CodeBuilder ldc(LoadableConstantEntry entry) {
        return with(ConstantInstruction.ofLoad(
                entry.typeKind().slotSize() == 2 ? Opcode.LDC2_W
                : entry.index() > 0xff ? Opcode.LDC_W
                : Opcode.LDC, entry));
    }

    /**
     * Divide long
     * @return this builder
     */
    default CodeBuilder ldiv() {
        return operatorInstruction(Opcode.LDIV);
    }

    /**
     * Load long from local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder lload(int slot) {
        return loadInstruction(TypeKind.LongType, slot);
    }

    /**
     * Multiply long
     * @return this builder
     */
    default CodeBuilder lmul() {
        return operatorInstruction(Opcode.LMUL);
    }

    /**
     * Negate long
     * @return this builder
     */
    default CodeBuilder lneg() {
        return operatorInstruction(Opcode.LNEG);
    }

    /**
     * Boolean OR long
     * @return this builder
     */
    default CodeBuilder lor() {
        return operatorInstruction(Opcode.LOR);
    }

    /**
     * Remainder long
     * @return this builder
     */
    default CodeBuilder lrem() {
        return operatorInstruction(Opcode.LREM);
    }

    /**
     * Return long from method
     * @return this builder
     */
    default CodeBuilder lreturn() {
        return returnInstruction(TypeKind.LongType);
    }

    /**
     * Shift left long
     * @return this builder
     */
    default CodeBuilder lshl() {
        return operatorInstruction(Opcode.LSHL);
    }

    /**
     * Shift right long
     * @return this builder
     */
    default CodeBuilder lshr() {
        return operatorInstruction(Opcode.LSHR);
    }

    /**
     * Store long into local variable
     * @param slot local variable slot
     * @return this builder
     */
    default CodeBuilder lstore(int slot) {
        return storeInstruction(TypeKind.LongType, slot);
    }

    /**
     * Subtract long
     * @return this builder
     */
    default CodeBuilder lsub() {
        return operatorInstruction(Opcode.LSUB);
    }

    /**
     * Logical shift right long
     * @return this builder
     */
    default CodeBuilder lushr() {
        return operatorInstruction(Opcode.LUSHR);
    }

    /**
     * Boolean XOR long
     * @return this builder
     */
    default CodeBuilder lxor() {
        return operatorInstruction(Opcode.LXOR);
    }

    /**
     * Enter monitor for object
     * @return this builder
     */
    default CodeBuilder monitorenter() {
        return monitorInstruction(Opcode.MONITORENTER);
    }

    /**
     * Exit monitor for object
     * @return this builder
     */
    default CodeBuilder monitorexit() {
        return monitorInstruction(Opcode.MONITOREXIT);
    }

    /**
     * Create new multidimensional array
     * @param array array type
     * @param dims number of dimensions
     * @return this builder
     */
    default CodeBuilder multianewarray(ClassEntry array, int dims) {
        return newMultidimensionalArrayInstruction(dims, array);
    }

    /**
     * Create new multidimensional array
     * @param array array type
     * @param dims number of dimensions
     * @return this builder
     */
    default CodeBuilder multianewarray(ClassDesc array, int dims) {
        return newMultidimensionalArrayInstruction(dims, constantPool().classEntry(array));
    }

    /**
     * Create new object
     * @param clazz class
     * @return this builder
     */
    default CodeBuilder new_(ClassEntry clazz) {
        return newObjectInstruction(clazz);
    }

    /**
     * Create new object
     * @param clazz class
     * @return this builder
     */
    default CodeBuilder new_(ClassDesc clazz) {
        return newObjectInstruction(constantPool().classEntry(clazz));
    }

    /**
     * Create new array
     * @param typeKind primitive array type
     * @return this builder
     */
    default CodeBuilder newarray(TypeKind typeKind) {
        return newPrimitiveArrayInstruction(typeKind);
    }

    /**
     * Pop the top operand stack value
     * @return this builder
     */
    default CodeBuilder pop() {
        return stackInstruction(Opcode.POP);
    }

    /**
     * Pop the top one or two operand stack values
     * @return this builder
     */
    default CodeBuilder pop2() {
        return stackInstruction(Opcode.POP2);
    }

    /**
     * Set field in object
     * @param ref field reference
     * @return this builder
     */
    default CodeBuilder putfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTFIELD, ref);
    }

    /**
     * Set field in object
     * @param owner class
     * @param name field name
     * @param type field type
     * @return this builder
     */
    default CodeBuilder putfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTFIELD, owner, name, type);
    }

    /**
     * Set static field in class
     * @param ref field reference
     * @return this builder
     */
    default CodeBuilder putstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTSTATIC, ref);
    }

    /**
     * Set static field in class
     * @param owner class
     * @param name field name
     * @param type field type
     * @return this builder
     */
    default CodeBuilder putstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTSTATIC, owner, name, type);
    }

    /**
     * Return void from method
     * @return this builder
     */
    default CodeBuilder return_() {
        return returnInstruction(TypeKind.VoidType);
    }

    /**
     * Load short from array
     * @return this builder
     */
    default CodeBuilder saload() {
        return arrayLoadInstruction(TypeKind.ShortType);
    }

    /**
     * Store into short array
     * @return this builder
     */
    default CodeBuilder sastore() {
        return arrayStoreInstruction(TypeKind.ShortType);
    }

    /**
     * Push short
     * @param s short
     * @return this builder
     */
    default CodeBuilder sipush(int s) {
        return constantInstruction(Opcode.SIPUSH, s);
    }

    /**
     * Swap the top two operand stack values
     * @return this builder
     */
    default CodeBuilder swap() {
        return stackInstruction(Opcode.SWAP);
    }

    /**
     * Access jump table by index and jump
     * @param low low key value
     * @param high high key value
     * @param defaultTarget default jump target
     * @param cases switch cases
     * @return this builder
     */
    default CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        return tableSwitchInstruction(low, high, defaultTarget, cases);
    }

    /**
     * Access jump table by index and jump
     * @param defaultTarget default jump target
     * @param cases switch cases
     * @return this builder
     */
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
