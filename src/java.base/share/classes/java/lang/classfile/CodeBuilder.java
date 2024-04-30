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

package java.lang.classfile;

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

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BlockCodeBuilderImpl;
import jdk.internal.classfile.impl.BytecodeHelpers;
import jdk.internal.classfile.impl.CatchBuilderImpl;
import jdk.internal.classfile.impl.ChainedCodeBuilder;
import jdk.internal.classfile.impl.LabelImpl;
import jdk.internal.classfile.impl.NonterminalCodeBuilder;
import jdk.internal.classfile.impl.TerminalCodeBuilder;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
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
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;

import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.BytecodeHelpers.handleDescToHandleInfo;
import jdk.internal.classfile.impl.TransformingCodeBuilder;
import jdk.internal.javac.PreviewFeature;

/**
 * A builder for code attributes (method bodies).  Builders are not created
 * directly; they are passed to handlers by methods such as {@link
 * MethodBuilder#withCode(Consumer)} or to code transforms.  The elements of a
 * code can be specified abstractly, by passing a {@link CodeElement} to {@link
 * #with(ClassFileElement)} or concretely by calling the various {@code withXxx}
 * methods.
 *
 * @see CodeTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface CodeBuilder
        extends ClassFileBuilder<CodeElement, CodeBuilder>
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
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
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
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
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
         * @throws IllegalArgumentException if an existing catch block catches an exception of the given type
         *                                  or {@code exceptionType} represents a primitive type
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
     * Generate an instruction to load a value from a local variable
     * @param tk the load type
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder loadInstruction(TypeKind tk, int slot) {
        with(LoadInstruction.of(tk, slot));
        return this;
    }

    /**
     * Generate an instruction to store a value to a local variable
     * @param tk the store type
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder storeInstruction(TypeKind tk, int slot) {
        with(StoreInstruction.of(tk, slot));
        return this;
    }

    /**
     * Generate an instruction to increment a local variable by a constant
     * @param slot the local variable slot
     * @param val the increment value
     * @return this builder
     */
    default CodeBuilder incrementInstruction(int slot, int val) {
        with(IncrementInstruction.of(slot, val));
        return this;
    }

    /**
     * Generate a branch instruction
     * @see Opcode.Kind#BRANCH
     * @param op the branch opcode
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder branchInstruction(Opcode op, Label target) {
        with(BranchInstruction.of(op, target));
        return this;
    }

    /**
     * Generate an instruction to access a jump table by key match and jump
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     */
    default CodeBuilder lookupSwitchInstruction(Label defaultTarget, List<SwitchCase> cases) {
        with(LookupSwitchInstruction.of(defaultTarget, cases));
        return this;
    }

    /**
     * Generate an instruction to access a jump table by index and jump
     * @param lowValue the low key value
     * @param highValue the high key value
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     */
    default CodeBuilder tableSwitchInstruction(int lowValue, int highValue, Label defaultTarget, List<SwitchCase> cases) {
        with(TableSwitchInstruction.of(lowValue, highValue, defaultTarget, cases));
        return this;
    }

    /**
     * Generate return instruction
     * @param tk the return type
     * @return this builder
     */
    default CodeBuilder returnInstruction(TypeKind tk) {
        with(ReturnInstruction.of(tk));
        return this;
    }

    /**
     * Generate an instruction to throw an exception or error
     * @return this builder
     */
    default CodeBuilder throwInstruction() {
        with(ThrowInstruction.of());
        return this;
    }

    /**
     * Generate an instruction to access a field
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode the field access opcode
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder fieldInstruction(Opcode opcode, FieldRefEntry ref) {
        with(FieldInstruction.of(opcode, ref));
        return this;
    }

    /**
     * Generate an instruction to access a field
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode the field access opcode
     * @param owner the class
     * @param name the field name
     * @param type the field type
     * @return this builder
     */
    default CodeBuilder fieldInstruction(Opcode opcode, ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(opcode, constantPool().fieldRefEntry(owner, name, type));
    }

    /**
     * Generate an instruction to invoke a method or constructor
     * @see Opcode.Kind#INVOKE
     * @param opcode the invoke opcode
     * @param ref the interface method or method reference
     * @return this builder
     */
    default CodeBuilder invokeInstruction(Opcode opcode, MemberRefEntry ref) {
        return with(InvokeInstruction.of(opcode, ref));
    }

    /**
     * Generate an instruction to invoke a method or constructor
     * @see Opcode.Kind#INVOKE
     * @param opcode the invoke opcode
     * @param owner the class
     * @param name the method name
     * @param desc the method type
     * @param isInterface the interface method invocation indication
     * @return this builder
     */
    default CodeBuilder invokeInstruction(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
        return invokeInstruction(opcode,
                isInterface ? constantPool().interfaceMethodRefEntry(owner, name, desc)
                            : constantPool().methodRefEntry(owner, name, desc));
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param ref the dynamic call site
     * @return this builder
     */
    default CodeBuilder invokeDynamicInstruction(InvokeDynamicEntry ref) {
        with(InvokeDynamicInstruction.of(ref));
        return this;
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param desc the dynamic call site
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
     * Generate an instruction to create a new object
     * @param type the object type
     * @return this builder
     */
    default CodeBuilder newObjectInstruction(ClassEntry type) {
        with(NewObjectInstruction.of(type));
        return this;
    }

    /**
     * Generate an instruction to create a new object
     * @param type the object type
     * @return this builder
     * @throws IllegalArgumentException if {@code type} represents a primitive type
     */
    default CodeBuilder newObjectInstruction(ClassDesc type) {
        return newObjectInstruction(constantPool().classEntry(type));
    }

    /**
     * Generate an instruction to create a new array of a primitive type
     * @param typeKind the primitive component type
     * @return this builder
     */
    default CodeBuilder newPrimitiveArrayInstruction(TypeKind typeKind) {
        with(NewPrimitiveArrayInstruction.of(typeKind));
        return this;
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param type the component type
     * @return this builder
     */
    default CodeBuilder newReferenceArrayInstruction(ClassEntry type) {
        with(NewReferenceArrayInstruction.of(type));
        return this;
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param type the component type
     * @return this builder
     * @throws IllegalArgumentException if {@code type} represents a primitive type
     */
    default CodeBuilder newReferenceArrayInstruction(ClassDesc type) {
        return newReferenceArrayInstruction(constantPool().classEntry(type));
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param dimensions the number of dimensions
     * @param type the array type
     * @return this builder
     */
    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassEntry type) {
        with(NewMultiArrayInstruction.of(type, dimensions));
        return this;
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param dimensions the number of dimensions
     * @param type the array type
     * @return this builder
     */
    default CodeBuilder newMultidimensionalArrayInstruction(int dimensions,
                                                            ClassDesc type) {
        return newMultidimensionalArrayInstruction(dimensions, constantPool().classEntry(type));
    }

    /**
     * Generate an instruction to load from an array
     * @param tk the array element type
     * @return this builder
     */
    default CodeBuilder arrayLoadInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayLoadOpcode(tk);
        with(ArrayLoadInstruction.of(opcode));
        return this;
    }

    /**
     * Generate an instruction to store into an array
     * @param tk the array element type
     * @return this builder
     */
    default CodeBuilder arrayStoreInstruction(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayStoreOpcode(tk);
        with(ArrayStoreInstruction.of(opcode));
        return this;
    }

    /**
     * Generate a type checking instruction
     * @see Opcode.Kind#TYPE_CHECK
     * @param opcode the type check instruction opcode
     * @param type the type
     * @return this builder
     */
    default CodeBuilder typeCheckInstruction(Opcode opcode,
                                             ClassEntry type) {
        with(TypeCheckInstruction.of(opcode, type));
        return this;
    }

    /**
     * Generate a type checking instruction
     * @see Opcode.Kind#TYPE_CHECK
     * @param opcode the type check instruction opcode
     * @param type the type
     * @return this builder
     */
    default CodeBuilder typeCheckInstruction(Opcode opcode, ClassDesc type) {
        return typeCheckInstruction(opcode, constantPool().classEntry(type));
    }

    /**
     * Generate a type converting instruction
     * @param fromType the source type
     * @param toType the target type
     * @return this builder
     */
    default CodeBuilder convertInstruction(TypeKind fromType, TypeKind toType) {
        with(ConvertInstruction.of(fromType, toType));
        return this;
    }

    /**
     * Generate a stack manipulating instruction
     * @param opcode the stack instruction opcode
     * @see Opcode.Kind#STACK
     * @return this builder
     */
    default CodeBuilder stackInstruction(Opcode opcode) {
        with(StackInstruction.of(opcode));
        return this;
    }

    /**
     * Generate an operator instruction
     * @see Opcode.Kind#OPERATOR
     * @param opcode the operator instruction opcode
     * @return this builder
     */
    default CodeBuilder operatorInstruction(Opcode opcode) {
        with(OperatorInstruction.of(opcode));
        return this;
    }

    /**
     * Generate an instruction pushing a constant onto the operand stack
     * @see Opcode.Kind#CONSTANT
     * @param opcode the constant instruction opcode
     * @param value the constant value
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
     * Generate an instruction pushing a constant onto the operand stack
     * @param value the constant value
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
     * Generate a monitor instruction
     * @see Opcode.Kind#MONITOR
     * @param opcode the monitor instruction opcode
     * @return this builder
     */
    default CodeBuilder monitorInstruction(Opcode opcode) {
        with(MonitorInstruction.of(opcode));
        return null;
    }

    /**
     * Generate a do nothing instruction
     * @return this builder
     */
    default CodeBuilder nopInstruction() {
        with(NopInstruction.of());
        return this;
    }

    /**
     * Generate a do nothing instruction
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
     * @param label the label
     * @return this builder
     */
    default CodeBuilder labelBinding(Label label) {
        with((LabelImpl) label);
        return this;
    }

    /**
     * Declare a source line number of the current builder position
     * @param line the line number
     * @return this builder
     */
    default CodeBuilder lineNumber(int line) {
        with(LineNumber.of(line));
        return this;
    }

    /**
     * Declare an exception table entry
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the catch type or null to catch all exceptions and errors
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassEntry catchType) {
        with(ExceptionCatch.of(handler, start, end, Optional.of(catchType)));
        return this;
    }

    /**
     * Declare an exception table entry
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the optional catch type, empty to catch all exceptions and errors
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, Optional<ClassEntry> catchType) {
        with(ExceptionCatch.of(handler, start, end, catchType));
        return this;
    }

    /**
     * Declare an exception table entry
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the catch type
     * @return this builder
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassDesc catchType) {
        requireNonNull(catchType);
        return exceptionCatch(start, end, handler, constantPool().classEntry(catchType));
    }

    /**
     * Declare an exception table entry catching all exceptions and errors
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @return this builder
     */
    default CodeBuilder exceptionCatchAll(Label start, Label end, Label handler) {
        with(ExceptionCatch.of(handler, start, end));
        return this;
    }

    /**
     * Declare a character range entry
     * @param startScope the start scope of the character range
     * @param endScope the end scope of the character range
     * @param characterRangeStart the encoded start of the character range region (inclusive)
     * @param characterRangeEnd the encoded end of the character range region (exclusive)
     * @param flags the flags word, indicating the kind of range
     * @return this builder
     */
    default CodeBuilder characterRange(Label startScope, Label endScope, int characterRangeStart, int characterRangeEnd, int flags) {
        with(CharacterRange.of(startScope, endScope, characterRangeStart, characterRangeEnd, flags));
        return this;
    }

    /**
     * Declare a local variable entry
     * @param slot the local variable slot
     * @param nameEntry the variable name
     * @param descriptorEntry the variable descriptor
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariable(int slot, Utf8Entry nameEntry, Utf8Entry descriptorEntry, Label startScope, Label endScope) {
        with(LocalVariable.of(slot, nameEntry, descriptorEntry, startScope, endScope));
        return this;
    }

    /**
     * Declare a local variable entry
     * @param slot the local variable slot
     * @param name the variable name
     * @param descriptor the variable descriptor
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariable(int slot, String name, ClassDesc descriptor, Label startScope, Label endScope) {
        return localVariable(slot,
                             constantPool().utf8Entry(name),
                             constantPool().utf8Entry(descriptor.descriptorString()),
                             startScope, endScope);
    }

    /**
     * Declare a local variable type entry
     * @param slot the local variable slot
     * @param nameEntry the variable name
     * @param signatureEntry the variable signature
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     */
    default CodeBuilder localVariableType(int slot, Utf8Entry nameEntry, Utf8Entry signatureEntry, Label startScope, Label endScope) {
        with(LocalVariableType.of(slot, nameEntry, signatureEntry, startScope, endScope));
        return this;
    }

    /**
     * Declare a local variable type entry
     * @param slot the local variable slot
     * @param name the variable name
     * @param signature the variable signature
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
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
     * Generate an instruction pushing the null object reference onto the operand stack
     * @return this builder
     */
    default CodeBuilder aconst_null() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ACONST_NULL));
    }

    /**
     * Generate an instruction to load a reference from an array
     * @return this builder
     */
    default CodeBuilder aaload() {
        return arrayLoadInstruction(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to store into a reference array
     * @return this builder
     */
    default CodeBuilder aastore() {
        return arrayStoreInstruction(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to load a reference from a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder aload(int slot) {
        return loadInstruction(TypeKind.ReferenceType, slot);
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param classEntry the component type
     * @return this builder
     */
    default CodeBuilder anewarray(ClassEntry classEntry) {
        return newReferenceArrayInstruction(classEntry);
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param className the component type
     * @return this builder
     * @throws IllegalArgumentException if {@code className} represents a primitive type
     */
    default CodeBuilder anewarray(ClassDesc className) {
        return newReferenceArrayInstruction(constantPool().classEntry(className));
    }

    /**
     * Generate an instruction to return a reference from the method
     * @return this builder
     */
    default CodeBuilder areturn() {
        return returnInstruction(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to get length of an array
     * @return this builder
     */
    default CodeBuilder arraylength() {
        return operatorInstruction(Opcode.ARRAYLENGTH);
    }

    /**
     * Generate an instruction to store a reference into a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder astore(int slot) {
        return storeInstruction(TypeKind.ReferenceType, slot);
    }

    /**
     * Generate an instruction to throw an exception or error
     * @return this builder
     */
    default CodeBuilder athrow() {
        return throwInstruction();
    }

    /**
     * Generate an instruction to load a byte from a array
     * @return this builder
     */
    default CodeBuilder baload() {
        return arrayLoadInstruction(TypeKind.ByteType);
    }

    /**
     * Generate an instruction to store into a byte array
     * @return this builder
     */
    default CodeBuilder bastore() {
        return arrayStoreInstruction(TypeKind.ByteType);
    }

    /**
     * Generate an instruction pushing a byte onto the operand stack
     * @param b the byte
     * @return this builder
     */
    default CodeBuilder bipush(int b) {
        return constantInstruction(Opcode.BIPUSH, b);
    }

    /**
     * Generate an instruction to load a char from an array
     * @return this builder
     */
    default CodeBuilder caload() {
        return arrayLoadInstruction(TypeKind.CharType);
    }

    /**
     * Generate an instruction to store into a char array
     * @return this builder
     */
    default CodeBuilder castore() {
        return arrayStoreInstruction(TypeKind.CharType);
    }

    /**
     * Generate an instruction to check whether an object is of the given type
     * @param type the object type
     * @return this builder
     */
    default CodeBuilder checkcast(ClassEntry type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    /**
     * Generate an instruction to check whether an object is of the given type
     * @param type the object type
     * @return this builder
     * @throws IllegalArgumentException if {@code type} represents a primitive type
     */
    default CodeBuilder checkcast(ClassDesc type) {
        return typeCheckInstruction(Opcode.CHECKCAST, type);
    }

    /**
     * Generate an instruction to convert a double into a float
     * @return this builder
     */
    default CodeBuilder d2f() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.FloatType);
    }

    /**
     * Generate an instruction to convert a double into an int
     * @return this builder
     */
    default CodeBuilder d2i() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.IntType);
    }

    /**
     * Generate an instruction to convert a double into a long
     * @return this builder
     */
    default CodeBuilder d2l() {
        return convertInstruction(TypeKind.DoubleType, TypeKind.LongType);
    }

    /**
     * Generate an instruction to add a double
     * @return this builder
     */
    default CodeBuilder dadd() {
        return operatorInstruction(Opcode.DADD);
    }

    /**
     * Generate an instruction to load a double from an array
     * @return this builder
     */
    default CodeBuilder daload() {
        return arrayLoadInstruction(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to store into a double array
     * @return this builder
     */
    default CodeBuilder dastore() {
        return arrayStoreInstruction(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to add a double
     * @return this builder
     */
    default CodeBuilder dcmpg() {
        return operatorInstruction(Opcode.DCMPG);
    }

    /**
     * Generate an instruction to compare doubles
     * @return this builder
     */
    default CodeBuilder dcmpl() {
        return operatorInstruction(Opcode.DCMPL);
    }

    /**
     * Generate an instruction pushing double constant 0 onto the operand stack
     * @return this builder
     */
    default CodeBuilder dconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_0));
    }

    /**
     * Generate an instruction pushing double constant 1 onto the operand stack
     * @return this builder
     */
    default CodeBuilder dconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_1));
    }

    /**
     * Generate an instruction to divide doubles
     * @return this builder
     */
    default CodeBuilder ddiv() {
        return operatorInstruction(Opcode.DDIV);
    }

    /**
     * Generate an instruction to load a double from a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder dload(int slot) {
        return loadInstruction(TypeKind.DoubleType, slot);
    }

    /**
     * Generate an instruction to multiply doubles
     * @return this builder
     */
    default CodeBuilder dmul() {
        return operatorInstruction(Opcode.DMUL);
    }

    /**
     * Generate an instruction to negate a double
     * @return this builder
     */
    default CodeBuilder dneg() {
        return operatorInstruction(Opcode.DNEG);
    }

    /**
     * Generate an instruction to calculate double remainder
     * @return this builder
     */
    default CodeBuilder drem() {
        return operatorInstruction(Opcode.DREM);
    }

    /**
     * Generate an instruction to return a double from the method
     * @return this builder
     */
    default CodeBuilder dreturn() {
        return returnInstruction(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to store a double into a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder dstore(int slot) {
        return storeInstruction(TypeKind.DoubleType, slot);
    }

    /**
     * Generate an instruction to subtract doubles
     * @return this builder
     */
    default CodeBuilder dsub() {
        return operatorInstruction(Opcode.DSUB);
    }

    /**
     * Generate an instruction to duplicate the top operand stack value
     * @return this builder
     */
    default CodeBuilder dup() {
        return stackInstruction(Opcode.DUP);
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack value
     * @return this builder
     */
    default CodeBuilder dup2() {
        return stackInstruction(Opcode.DUP2);
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack values and insert two or three
     * values down
     * @return this builder
     */
    default CodeBuilder dup2_x1() {
        return stackInstruction(Opcode.DUP2_X1);
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack values and insert two, three,
     * or four values down
     * @return this builder
     */
    default CodeBuilder dup2_x2() {
        return stackInstruction(Opcode.DUP2_X2);
    }

    /**
     * Generate an instruction to duplicate the top operand stack value and insert two values down
     * @return this builder
     */
    default CodeBuilder dup_x1() {
        return stackInstruction(Opcode.DUP_X1);
    }

    /**
     * Generate an instruction to duplicate the top operand stack value and insert two or three values down
     * @return this builder
     */
    default CodeBuilder dup_x2() {
        return stackInstruction(Opcode.DUP_X2);
    }

    /**
     * Generate an instruction to convert a float into a double
     * @return this builder
     */
    default CodeBuilder f2d() {
        return convertInstruction(TypeKind.FloatType, TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to convert a float into an int
     * @return this builder
     */
    default CodeBuilder f2i() {
        return convertInstruction(TypeKind.FloatType, TypeKind.IntType);
    }

    /**
     * Generate an instruction to convert a float into a long
     * @return this builder
     */
    default CodeBuilder f2l() {
        return convertInstruction(TypeKind.FloatType, TypeKind.LongType);
    }

    /**
     * Generate an instruction to add a float
     * @return this builder
     */
    default CodeBuilder fadd() {
        return operatorInstruction(Opcode.FADD);
    }

    /**
     * Generate an instruction to load a float from an array
     * @return this builder
     */
    default CodeBuilder faload() {
        return arrayLoadInstruction(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to store into a float array
     * @return this builder
     */
    default CodeBuilder fastore() {
        return arrayStoreInstruction(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to compare floats
     * @return this builder
     */
    default CodeBuilder fcmpg() {
        return operatorInstruction(Opcode.FCMPG);
    }

    /**
     * Generate an instruction to compare floats
     * @return this builder
     */
    default CodeBuilder fcmpl() {
        return operatorInstruction(Opcode.FCMPL);
    }

    /**
     * Generate an instruction pushing float constant 0 onto the operand stack
     * @return this builder
     */
    default CodeBuilder fconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_0));
    }

    /**
     * Generate an instruction pushing float constant 1 onto the operand stack
     * @return this builder
     */
    default CodeBuilder fconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_1));
    }

    /**
     * Generate an instruction pushing float constant 2 onto the operand stack
     * @return this builder
     */
    default CodeBuilder fconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_2));
    }

    /**
     * Generate an instruction to divide floats
     * @return this builder
     */
    default CodeBuilder fdiv() {
        return operatorInstruction(Opcode.FDIV);
    }

    /**
     * Generate an instruction to load a float from a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder fload(int slot) {
        return loadInstruction(TypeKind.FloatType, slot);
    }

    /**
     * Generate an instruction to multiply floats
     * @return this builder
     */
    default CodeBuilder fmul() {
        return operatorInstruction(Opcode.FMUL);
    }

    /**
     * Generate an instruction to negate a float
     * @return this builder
     */
    default CodeBuilder fneg() {
        return operatorInstruction(Opcode.FNEG);
    }

    /**
     * Generate an instruction to calculate floats remainder
     * @return this builder
     */
    default CodeBuilder frem() {
        return operatorInstruction(Opcode.FREM);
    }

    /**
     * Generate an instruction to return a float from the method
     * @return this builder
     */
    default CodeBuilder freturn() {
        return returnInstruction(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to store a float into a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder fstore(int slot) {
        return storeInstruction(TypeKind.FloatType, slot);
    }

    /**
     * Generate an instruction to subtract floats
     * @return this builder
     */
    default CodeBuilder fsub() {
        return operatorInstruction(Opcode.FSUB);
    }

    /**
     * Generate an instruction to fetch field from an object
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder getfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETFIELD, ref);
    }

    /**
     * Generate an instruction to fetch field from an object
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder getfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETFIELD, owner, name, type);
    }

    /**
     * Generate an instruction to get static field from a class
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder getstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.GETSTATIC, ref);
    }

    /**
     * Generate an instruction to get static field from a class
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder getstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.GETSTATIC, owner, name, type);
    }

    /**
     * Generate an instruction to branch always
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder goto_(Label target) {
        return branchInstruction(Opcode.GOTO, target);
    }

    /**
     * Generate an instruction to branch always with wide index
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder goto_w(Label target) {
        return branchInstruction(Opcode.GOTO_W, target);
    }

    /**
     * Generate an instruction to convert an int into a byte
     * @return this builder
     */
    default CodeBuilder i2b() {
        return convertInstruction(TypeKind.IntType, TypeKind.ByteType);
    }

    /**
     * Generate an instruction to convert an int into a char
     * @return this builder
     */
    default CodeBuilder i2c() {
        return convertInstruction(TypeKind.IntType, TypeKind.CharType);
    }

    /**
     * Generate an instruction to convert an int into a double
     * @return this builder
     */
    default CodeBuilder i2d() {
        return convertInstruction(TypeKind.IntType, TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to convert an int into a float
     * @return this builder
     */
    default CodeBuilder i2f() {
        return convertInstruction(TypeKind.IntType, TypeKind.FloatType);
    }

    /**
     * Generate an instruction to convert an int into a long
     * @return this builder
     */
    default CodeBuilder i2l() {
        return convertInstruction(TypeKind.IntType, TypeKind.LongType);
    }

    /**
     * Generate an instruction to convert an int into a short
     * @return this builder
     */
    default CodeBuilder i2s() {
        return convertInstruction(TypeKind.IntType, TypeKind.ShortType);
    }

    /**
     * Generate an instruction to add an int
     * @return this builder
     */
    default CodeBuilder iadd() {
        return operatorInstruction(Opcode.IADD);
    }

    /**
     * Generate an instruction to load a int from an array
     * @return this builder
     */
    default CodeBuilder iaload() {
        return arrayLoadInstruction(TypeKind.IntType);
    }

    /**
     * Generate an instruction to calculate boolean AND of ints
     * @return this builder
     */
    default CodeBuilder iand() {
        return operatorInstruction(Opcode.IAND);
    }

    /**
     * Generate an instruction to store into an int array
     * @return this builder
     */
    default CodeBuilder iastore() {
        return arrayStoreInstruction(TypeKind.IntType);
    }

    /**
     * Generate an instruction pushing int constant 0 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_0));
    }

    /**
     * Generate an instruction pushing int constant 1 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_1));
    }

    /**
     * Generate an instruction pushing int constant 2 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_2));
    }

    /**
     * Generate an instruction pushing int constant 3 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_3() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_3));
    }

    /**
     * Generate an instruction pushing int constant 4 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_4() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_4));
    }

    /**
     * Generate an instruction pushing int constant 5 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_5() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_5));
    }

    /**
     * Generate an instruction pushing int constant -1 onto the operand stack
     * @return this builder
     */
    default CodeBuilder iconst_m1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_M1));
    }

    /**
     * Generate an instruction to divide ints
     * @return this builder
     */
    default CodeBuilder idiv() {
        return operatorInstruction(Opcode.IDIV);
    }

    /**
     * Generate an instruction to branch if reference comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_acmpeq(Label target) {
        return branchInstruction(Opcode.IF_ACMPEQ, target);
    }

    /**
     * Generate an instruction to branch if reference comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_acmpne(Label target) {
        return branchInstruction(Opcode.IF_ACMPNE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpeq(Label target) {
        return branchInstruction(Opcode.IF_ICMPEQ, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpge(Label target) {
        return branchInstruction(Opcode.IF_ICMPGE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpgt(Label target) {
        return branchInstruction(Opcode.IF_ICMPGT, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmple(Label target) {
        return branchInstruction(Opcode.IF_ICMPLE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmplt(Label target) {
        return branchInstruction(Opcode.IF_ICMPLT, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpne(Label target) {
        return branchInstruction(Opcode.IF_ICMPNE, target);
    }

    /**
     * Generate an instruction to branch if reference is not null
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_nonnull(Label target) {
        return branchInstruction(Opcode.IFNONNULL, target);
    }

    /**
     * Generate an instruction to branch if reference is null
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_null(Label target) {
        return branchInstruction(Opcode.IFNULL, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifeq(Label target) {
        return branchInstruction(Opcode.IFEQ, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifge(Label target) {
        return branchInstruction(Opcode.IFGE, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifgt(Label target) {
        return branchInstruction(Opcode.IFGT, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifle(Label target) {
        return branchInstruction(Opcode.IFLE, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder iflt(Label target) {
        return branchInstruction(Opcode.IFLT, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifne(Label target) {
        return branchInstruction(Opcode.IFNE, target);
    }

    /**
     * Generate an instruction to increment a local variable by a constant
     * @param slot the local variable slot
     * @param val the increment value
     * @return this builder
     */
    default CodeBuilder iinc(int slot, int val) {
        return incrementInstruction(slot, val);
    }

    /**
     * Generate an instruction to load an int from a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder iload(int slot) {
        return loadInstruction(TypeKind.IntType, slot);
    }

    /**
     * Generate an instruction to multiply ints
     * @return this builder
     */
    default CodeBuilder imul() {
        return operatorInstruction(Opcode.IMUL);
    }

    /**
     * Generate an instruction to negate an int
     * @return this builder
     */
    default CodeBuilder ineg() {
        return operatorInstruction(Opcode.INEG);
    }

    /**
     * Generate an instruction to determine if an object is of the given type
     * @param target the target type
     * @return this builder
     */
    default CodeBuilder instanceof_(ClassEntry target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, target);
    }

    /**
     * Generate an instruction to determine if an object is of the given type
     * @param target the target type
     * @return this builder
     * @throws IllegalArgumentException if {@code target} represents a primitive type
     */
    default CodeBuilder instanceof_(ClassDesc target) {
        return typeCheckInstruction(Opcode.INSTANCEOF, constantPool().classEntry(target));
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param ref the dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        return invokeDynamicInstruction(ref);
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param ref the dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(DynamicCallSiteDesc ref) {
        return invokeDynamicInstruction(ref);
    }

    /**
     * Generate an instruction to invoke an interface method
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, ref);
    }

    /**
     * Generate an instruction to invoke an interface method
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokeinterface(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEINTERFACE, constantPool().interfaceMethodRefEntry(owner, name, type));
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, false);
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @param isInterface the interface method invocation indication
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESPECIAL, owner, name, type, isInterface);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, false);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @param isInterface the interface method invocation indication
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invokeInstruction(Opcode.INVOKESTATIC, owner, name, type, isInterface);
    }

    /**
     * Generate an instruction to invoke an instance method; dispatch based on class
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokevirtual(MethodRefEntry ref) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, ref);
    }

    /**
     * Generate an instruction to invoke an instance method; dispatch based on class
     * @param owner the owner class
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder invokevirtual(ClassDesc owner, String name, MethodTypeDesc type) {
        return invokeInstruction(Opcode.INVOKEVIRTUAL, owner, name, type, false);
    }

    /**
     * Generate an instruction to calculate boolean OR of ints
     * @return this builder
     */
    default CodeBuilder ior() {
        return operatorInstruction(Opcode.IOR);
    }

    /**
     * Generate an instruction to calculate ints remainder
     * @return this builder
     */
    default CodeBuilder irem() {
        return operatorInstruction(Opcode.IREM);
    }

    /**
     * Generate an instruction to return an int from the method
     * @return this builder
     */
    default CodeBuilder ireturn() {
        return returnInstruction(TypeKind.IntType);
    }

    /**
     * Generate an instruction to shift an int left
     * @return this builder
     */
    default CodeBuilder ishl() {
        return operatorInstruction(Opcode.ISHL);
    }

    /**
     * Generate an instruction to shift an int right
     * @return this builder
     */
    default CodeBuilder ishr() {
        return operatorInstruction(Opcode.ISHR);
    }

    /**
     * Generate an instruction to store an int into a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder istore(int slot) {
        return storeInstruction(TypeKind.IntType, slot);
    }

    /**
     * Generate an instruction to subtract ints
     * @return this builder
     */
    default CodeBuilder isub() {
        return operatorInstruction(Opcode.ISUB);
    }

    /**
     * Generate an instruction to logical shift an int right
     * @return this builder
     */
    default CodeBuilder iushr() {
        return operatorInstruction(Opcode.IUSHR);
    }

    /**
     * Generate an instruction to calculate boolean XOR of ints
     * @return this builder
     */
    default CodeBuilder ixor() {
        return operatorInstruction(Opcode.IXOR);
    }

    /**
     * Generate an instruction to access a jump table by key match and jump
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     */
    default CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        return lookupSwitchInstruction(defaultTarget, cases);
    }

    /**
     * Generate an instruction to convert a long into a double
     * @return this builder
     */
    default CodeBuilder l2d() {
        return convertInstruction(TypeKind.LongType, TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to convert a long into a float
     * @return this builder
     */
    default CodeBuilder l2f() {
        return convertInstruction(TypeKind.LongType, TypeKind.FloatType);
    }

    /**
     * Generate an instruction to convert a long into an int
     * @return this builder
     */
    default CodeBuilder l2i() {
        return convertInstruction(TypeKind.LongType, TypeKind.IntType);
    }

    /**
     * Generate an instruction to add a long
     * @return this builder
     */
    default CodeBuilder ladd() {
        return operatorInstruction(Opcode.LADD);
    }

    /**
     * Generate an instruction to load a long from an array
     * @return this builder
     */
    default CodeBuilder laload() {
        return arrayLoadInstruction(TypeKind.LongType);
    }

    /**
     * Generate an instruction to calculate boolean AND of longs
     * @return this builder
     */
    default CodeBuilder land() {
        return operatorInstruction(Opcode.LAND);
    }

    /**
     * Generate an instruction to store into a long array
     * @return this builder
     */
    default CodeBuilder lastore() {
        return arrayStoreInstruction(TypeKind.LongType);
    }

    /**
     * Generate an instruction to compare longs
     * @return this builder
     */
    default CodeBuilder lcmp() {
        return operatorInstruction(Opcode.LCMP);
    }

    /**
     * Generate an instruction pushing long constant 0 onto the operand stack
     * @return this builder
     */
    default CodeBuilder lconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_0));
    }

    /**
     * Generate an instruction pushing long constant 1 onto the operand stack
     * @return this builder
     */
    default CodeBuilder lconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_1));
    }

    /**
     * Generate an instruction pushing an item from the run-time constant pool onto the operand stack
     * @param value the constant value
     * @return this builder
     */
    default CodeBuilder ldc(ConstantDesc value) {
        return ldc(BytecodeHelpers.constantEntry(constantPool(), value));
    }

    /**
     * Generate an instruction pushing an item from the run-time constant pool onto the operand stack
     * @param entry the constant value
     * @return this builder
     */
    default CodeBuilder ldc(LoadableConstantEntry entry) {
        return with(ConstantInstruction.ofLoad(
                entry.typeKind().slotSize() == 2 ? Opcode.LDC2_W
                : entry.index() > 0xff ? Opcode.LDC_W
                : Opcode.LDC, entry));
    }

    /**
     * Generate an instruction to divide longs
     * @return this builder
     */
    default CodeBuilder ldiv() {
        return operatorInstruction(Opcode.LDIV);
    }

    /**
     * Generate an instruction to load a long from a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder lload(int slot) {
        return loadInstruction(TypeKind.LongType, slot);
    }

    /**
     * Generate an instruction to multiply longs
     * @return this builder
     */
    default CodeBuilder lmul() {
        return operatorInstruction(Opcode.LMUL);
    }

    /**
     * Generate an instruction to negate a long
     * @return this builder
     */
    default CodeBuilder lneg() {
        return operatorInstruction(Opcode.LNEG);
    }

    /**
     * Generate an instruction to calculate boolean OR of longs
     * @return this builder
     */
    default CodeBuilder lor() {
        return operatorInstruction(Opcode.LOR);
    }

    /**
     * Generate an instruction to calculate longs remainder
     * @return this builder
     */
    default CodeBuilder lrem() {
        return operatorInstruction(Opcode.LREM);
    }

    /**
     * Generate an instruction to return a long from the method
     * @return this builder
     */
    default CodeBuilder lreturn() {
        return returnInstruction(TypeKind.LongType);
    }

    /**
     * Generate an instruction to shift a long left
     * @return this builder
     */
    default CodeBuilder lshl() {
        return operatorInstruction(Opcode.LSHL);
    }

    /**
     * Generate an instruction to shift a long right
     * @return this builder
     */
    default CodeBuilder lshr() {
        return operatorInstruction(Opcode.LSHR);
    }

    /**
     * Generate an instruction to store a long into a local variable
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder lstore(int slot) {
        return storeInstruction(TypeKind.LongType, slot);
    }

    /**
     * Generate an instruction to subtract longs
     * @return this builder
     */
    default CodeBuilder lsub() {
        return operatorInstruction(Opcode.LSUB);
    }

    /**
     * Generate an instruction to logical shift a long left
     * @return this builder
     */
    default CodeBuilder lushr() {
        return operatorInstruction(Opcode.LUSHR);
    }

    /**
     * Generate an instruction to calculate boolean XOR of longs
     * @return this builder
     */
    default CodeBuilder lxor() {
        return operatorInstruction(Opcode.LXOR);
    }

    /**
     * Generate an instruction to enter monitor for an object
     * @return this builder
     */
    default CodeBuilder monitorenter() {
        return monitorInstruction(Opcode.MONITORENTER);
    }

    /**
     * Generate an instruction to exit monitor for an object
     * @return this builder
     */
    default CodeBuilder monitorexit() {
        return monitorInstruction(Opcode.MONITOREXIT);
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     */
    default CodeBuilder multianewarray(ClassEntry array, int dims) {
        return newMultidimensionalArrayInstruction(dims, array);
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     * @throws IllegalArgumentException if {@code array} represents a primitive type
     */
    default CodeBuilder multianewarray(ClassDesc array, int dims) {
        return newMultidimensionalArrayInstruction(dims, constantPool().classEntry(array));
    }

    /**
     * Generate an instruction to create a new object
     * @param clazz the new class type
     * @return this builder
     */
    default CodeBuilder new_(ClassEntry clazz) {
        return newObjectInstruction(clazz);
    }

    /**
     * Generate an instruction to create a new object
     * @param clazz the new class type
     * @return this builder
     * @throws IllegalArgumentException if {@code clazz} represents a primitive type
     */
    default CodeBuilder new_(ClassDesc clazz) {
        return newObjectInstruction(constantPool().classEntry(clazz));
    }

    /**
     * Generate an instruction to create a new array of a primitive type
     * @param typeKind the primitive array type
     * @return this builder
     */
    default CodeBuilder newarray(TypeKind typeKind) {
        return newPrimitiveArrayInstruction(typeKind);
    }

    /**
     * Generate an instruction to pop the top operand stack value
     * @return this builder
     */
    default CodeBuilder pop() {
        return stackInstruction(Opcode.POP);
    }

    /**
     * Generate an instruction to pop the top one or two operand stack values
     * @return this builder
     */
    default CodeBuilder pop2() {
        return stackInstruction(Opcode.POP2);
    }

    /**
     * Generate an instruction to set field in an object
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder putfield(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTFIELD, ref);
    }

    /**
     * Generate an instruction to set field in an object
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder putfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTFIELD, owner, name, type);
    }

    /**
     * Generate an instruction to set static field in a class
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder putstatic(FieldRefEntry ref) {
        return fieldInstruction(Opcode.PUTSTATIC, ref);
    }

    /**
     * Generate an instruction to set static field in a class
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default CodeBuilder putstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldInstruction(Opcode.PUTSTATIC, owner, name, type);
    }

    /**
     * Generate an instruction to return void from the method
     * @return this builder
     */
    default CodeBuilder return_() {
        return returnInstruction(TypeKind.VoidType);
    }

    /**
     * Generate an instruction to load a short from an array
     * @return this builder
     */
    default CodeBuilder saload() {
        return arrayLoadInstruction(TypeKind.ShortType);
    }

    /**
     * Generate an instruction to store into a short array
     * @return this builder
     */
    default CodeBuilder sastore() {
        return arrayStoreInstruction(TypeKind.ShortType);
    }

    /**
     * Generate an instruction pushing a short onto the operand stack
     * @param s the short
     * @return this builder
     */
    default CodeBuilder sipush(int s) {
        return constantInstruction(Opcode.SIPUSH, s);
    }

    /**
     * Generate an instruction to swap the top two operand stack values
     * @return this builder
     */
    default CodeBuilder swap() {
        return stackInstruction(Opcode.SWAP);
    }

    /**
     * Generate an instruction to access a jump table by index and jump
     * @param low the low key value
     * @param high the high key value
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     */
    default CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        return tableSwitchInstruction(low, high, defaultTarget, cases);
    }

    /**
     * Generate an instruction to access a jump table by index and jump
     * @param defaultTarget the default jump target
     * @param cases the switch cases
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
}
