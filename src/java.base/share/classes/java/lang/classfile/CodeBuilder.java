/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.impl.TransformImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A builder for code attributes (method bodies).  Builders are not created
 * directly; they are passed to handlers by methods such as {@link
 * MethodBuilder#withCode(Consumer)} or to code transforms.  The elements of a
 * code can be specified abstractly, by passing a {@link CodeElement} to {@link
 * #with(ClassFileElement)} or concretely by calling the various {@code withXxx}
 * methods.
 *
 * <h2>Instruction Factories</h2>
 * {@code CodeBuilder} provides convenience methods to create instructions (See
 * JVMS {@jvms 6.5} Instructions) by their mnemonic, taking necessary operands.
 * <ul>
 * <li>Instructions that encode their operands in their opcode, such as {@code
 * aload_<n>}, share their factories with their generic version like {@link
 * #aload aload}. Note that some constant instructions, such as {@link #iconst_1
 * iconst_1}, do not have generic versions, and thus have their own factories.
 * <li>Instructions that accept wide operands, such as {@code ldc2_w} or {@code
 * wide}, share their factories with their regular version like {@link #ldc}. Note
 * that {@link #goto_w goto_w} has its own factory to avoid {@linkplain
 * ClassFile.ShortJumpsOption short jumps}.
 * <li>The {@code goto}, {@code instanceof}, {@code new}, and {@code return}
 * instructions' factories are named {@link #goto_ goto_}, {@link #instanceOf
 * instanceOf}, {@link #new_ new_}, and {@link #return_() return_} respectively,
 * due to clashes with keywords in the Java programming language.
 * <li>Factories are not provided for instructions {@code jsr}, {@code jsr_w},
 * {@code ret}, and {@code wide ret}, which cannot appear in class files with
 * major version {@value ClassFile#JAVA_7_VERSION} or higher. (JVMS {@jvms 4.9.1})
 * </ul>
 *
 * @see CodeTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface CodeBuilder
        extends ClassFileBuilder<CodeElement, CodeBuilder>
        permits CodeBuilder.BlockCodeBuilder, ChainedCodeBuilder, TerminalCodeBuilder, NonterminalCodeBuilder {

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
     * @throws IllegalStateException if this is a static method
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
        var resolved = TransformImpl.resolve(transform, this);
        resolved.startHandler().run();
        handler.accept(new ChainedCodeBuilder(this, resolved.consumer()));
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
        return labelBinding(breakLabel);
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
        branch(BytecodeHelpers.reverseBranchOpcode(opcode), thenBlock.endLabel());
        thenBlock.start();
        thenHandler.accept(thenBlock);
        thenBlock.end();
        return labelBinding(breakLabel);
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
        branch(BytecodeHelpers.reverseBranchOpcode(opcode), elseBlock.startLabel());
        thenBlock.start();
        thenHandler.accept(thenBlock);
        if (thenBlock.reachable())
            thenBlock.branch(Opcode.GOTO, thenBlock.breakLabel());
        thenBlock.end();
        elseBlock.start();
        elseHandler.accept(elseBlock);
        elseBlock.end();
        return labelBinding(breakLabel);
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
     * @since 23
     */
    default CodeBuilder loadLocal(TypeKind tk, int slot) {
        return with(LoadInstruction.of(tk, slot));
    }

    /**
     * Generate an instruction to store a value to a local variable
     * @param tk the store type
     * @param slot the local variable slot
     * @return this builder
     * @since 23
     */
    default CodeBuilder storeLocal(TypeKind tk, int slot) {
        return with(StoreInstruction.of(tk, slot));
    }

    /**
     * Generate a branch instruction
     * @see Opcode.Kind#BRANCH
     * @param op the branch opcode
     * @param target the branch target
     * @return this builder
     * @since 23
     */
    default CodeBuilder branch(Opcode op, Label target) {
        return with(BranchInstruction.of(op, target));
    }

    /**
     * Generate return instruction
     * @param tk the return type
     * @return this builder
     * @since 23
     */
    default CodeBuilder return_(TypeKind tk) {
        return with(ReturnInstruction.of(tk));
    }

    /**
     * Generate an instruction to access a field
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode the field access opcode
     * @param ref the field reference
     * @return this builder
     * @since 23
     */
    default CodeBuilder fieldAccess(Opcode opcode, FieldRefEntry ref) {
        return with(FieldInstruction.of(opcode, ref));
    }

    /**
     * Generate an instruction to access a field
     * @see Opcode.Kind#FIELD_ACCESS
     * @param opcode the field access opcode
     * @param owner the class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @since 23
     */
    default CodeBuilder fieldAccess(Opcode opcode, ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(opcode, constantPool().fieldRefEntry(owner, name, type));
    }

    /**
     * Generate an instruction to invoke a method or constructor
     * @see Opcode.Kind#INVOKE
     * @param opcode the invoke opcode
     * @param ref the interface method or method reference
     * @return this builder
     * @since 23
     */
    default CodeBuilder invoke(Opcode opcode, MemberRefEntry ref) {
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
     * @since 23
     */
    default CodeBuilder invoke(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
        return invoke(opcode,
                isInterface ? constantPool().interfaceMethodRefEntry(owner, name, desc)
                            : constantPool().methodRefEntry(owner, name, desc));
    }

    /**
     * Generate an instruction to load from an array
     * @param tk the array element type
     * @return this builder
     * @since 23
     */
    default CodeBuilder arrayLoad(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayLoadOpcode(tk);
        return with(ArrayLoadInstruction.of(opcode));
    }

    /**
     * Generate an instruction to store into an array
     * @param tk the array element type
     * @return this builder
     * @since 23
     */
    default CodeBuilder arrayStore(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayStoreOpcode(tk);
        return with(ArrayStoreInstruction.of(opcode));
    }

    /**
     * Generate instruction(s) to convert {@code fromType} to {@code toType}
     * @param fromType the source type
     * @param toType the target type
     * @return this builder
     * @throws IllegalArgumentException for conversions of {@code VoidType} or {@code ReferenceType}
     * @since 23
     */
    default CodeBuilder conversion(TypeKind fromType, TypeKind toType) {
        return switch (fromType) {
            case IntType, ByteType, CharType, ShortType, BooleanType ->
                    switch (toType) {
                        case IntType -> this;
                        case LongType -> i2l();
                        case DoubleType -> i2d();
                        case FloatType -> i2f();
                        case ByteType -> i2b();
                        case CharType -> i2c();
                        case ShortType -> i2s();
                        case BooleanType -> iconst_1().iand();
                        case VoidType, ReferenceType ->
                            throw new IllegalArgumentException(String.format("convert %s -> %s", fromType, toType));
                    };
            case LongType ->
                    switch (toType) {
                        case IntType -> l2i();
                        case LongType -> this;
                        case DoubleType -> l2d();
                        case FloatType -> l2f();
                        case ByteType -> l2i().i2b();
                        case CharType -> l2i().i2c();
                        case ShortType -> l2i().i2s();
                        case BooleanType -> l2i().iconst_1().iand();
                        case VoidType, ReferenceType ->
                            throw new IllegalArgumentException(String.format("convert %s -> %s", fromType, toType));
                    };
            case DoubleType ->
                    switch (toType) {
                        case IntType -> d2i();
                        case LongType -> d2l();
                        case DoubleType -> this;
                        case FloatType -> d2f();
                        case ByteType -> d2i().i2b();
                        case CharType -> d2i().i2c();
                        case ShortType -> d2i().i2s();
                        case BooleanType -> d2i().iconst_1().iand();
                        case VoidType, ReferenceType ->
                            throw new IllegalArgumentException(String.format("convert %s -> %s", fromType, toType));
                    };
            case FloatType ->
                    switch (toType) {
                        case IntType -> f2i();
                        case LongType -> f2l();
                        case DoubleType -> f2d();
                        case FloatType -> this;
                        case ByteType -> f2i().i2b();
                        case CharType -> f2i().i2c();
                        case ShortType -> f2i().i2s();
                        case BooleanType -> f2i().iconst_1().iand();
                        case VoidType, ReferenceType ->
                            throw new IllegalArgumentException(String.format("convert %s -> %s", fromType, toType));
                    };
            case VoidType, ReferenceType ->
                throw new IllegalArgumentException(String.format("convert %s -> %s", fromType, toType));
        };
    }

    /**
     * Generate an instruction pushing a constant onto the operand stack
     * @see Opcode.Kind#CONSTANT
     * @param opcode the constant instruction opcode
     * @param value the constant value
     * @return this builder
     * @since 23
     */
    default CodeBuilder loadConstant(Opcode opcode, ConstantDesc value) {
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
     * @since 23
     */
    default CodeBuilder loadConstant(ConstantDesc value) {
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
     * Generate a do nothing instruction
     * @return this builder
     */
    default CodeBuilder nop() {
        return with(NopInstruction.of());
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
        return with((LabelImpl) label);
    }

    /**
     * Declare a source line number of the current builder position
     * @param line the line number
     * @return this builder
     */
    default CodeBuilder lineNumber(int line) {
        return with(LineNumber.of(line));
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
        return with(ExceptionCatch.of(handler, start, end, Optional.ofNullable(catchType)));
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
        return with(ExceptionCatch.of(handler, start, end, catchType));
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
        return with(ExceptionCatch.of(handler, start, end));
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
        return with(CharacterRange.of(startScope, endScope, characterRangeStart, characterRangeEnd, flags));
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
        return with(LocalVariable.of(slot, nameEntry, descriptorEntry, startScope, endScope));
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
        return with(LocalVariableType.of(slot, nameEntry, signatureEntry, startScope, endScope));
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
        return arrayLoad(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to store into a reference array
     * @return this builder
     */
    default CodeBuilder aastore() {
        return arrayStore(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to load a reference from a local variable
     *
     * <p>This may also generate {@code aload_<N>} and
     * {@code wide aload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder aload(int slot) {
        return loadLocal(TypeKind.ReferenceType, slot);
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param classEntry the component type
     * @return this builder
     */
    default CodeBuilder anewarray(ClassEntry classEntry) {
        return with(NewReferenceArrayInstruction.of(classEntry));
    }

    /**
     * Generate an instruction to create a new array of reference
     * @param className the component type
     * @return this builder
     * @throws IllegalArgumentException if {@code className} represents a primitive type
     */
    default CodeBuilder anewarray(ClassDesc className) {
        return anewarray(constantPool().classEntry(className));
    }

    /**
     * Generate an instruction to return a reference from the method
     * @return this builder
     */
    default CodeBuilder areturn() {
        return return_(TypeKind.ReferenceType);
    }

    /**
     * Generate an instruction to get length of an array
     * @return this builder
     */
    default CodeBuilder arraylength() {
        return with(OperatorInstruction.of(Opcode.ARRAYLENGTH));
    }

    /**
     * Generate an instruction to store a reference into a local variable
     *
     * <p>This may also generate {@code astore_<N>} and
     * {@code wide astore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder astore(int slot) {
        return storeLocal(TypeKind.ReferenceType, slot);
    }

    /**
     * Generate an instruction to throw an exception or error
     * @return this builder
     */
    default CodeBuilder athrow() {
        return with(ThrowInstruction.of());
    }

    /**
     * Generate an instruction to load a byte from a array
     * @return this builder
     */
    default CodeBuilder baload() {
        return arrayLoad(TypeKind.ByteType);
    }

    /**
     * Generate an instruction to store into a byte array
     * @return this builder
     */
    default CodeBuilder bastore() {
        return arrayStore(TypeKind.ByteType);
    }

    /**
     * Generate an instruction pushing a byte onto the operand stack
     * @param b the byte
     * @return this builder
     */
    default CodeBuilder bipush(int b) {
        return loadConstant(Opcode.BIPUSH, b);
    }

    /**
     * Generate an instruction to load a char from an array
     * @return this builder
     */
    default CodeBuilder caload() {
        return arrayLoad(TypeKind.CharType);
    }

    /**
     * Generate an instruction to store into a char array
     * @return this builder
     */
    default CodeBuilder castore() {
        return arrayStore(TypeKind.CharType);
    }

    /**
     * Generate an instruction to check whether an object is of the given type
     * @param type the object type
     * @return this builder
     */
    default CodeBuilder checkcast(ClassEntry type) {
        return with(TypeCheckInstruction.of(Opcode.CHECKCAST, type));
    }

    /**
     * Generate an instruction to check whether an object is of the given type
     * @param type the object type
     * @return this builder
     * @throws IllegalArgumentException if {@code type} represents a primitive type
     */
    default CodeBuilder checkcast(ClassDesc type) {
        return checkcast(constantPool().classEntry(type));
    }

    /**
     * Generate an instruction to convert a double into a float
     * @return this builder
     */
    default CodeBuilder d2f() {
        return with(ConvertInstruction.of(Opcode.D2F));
    }

    /**
     * Generate an instruction to convert a double into an int
     * @return this builder
     */
    default CodeBuilder d2i() {
        return with(ConvertInstruction.of(Opcode.D2I));
    }

    /**
     * Generate an instruction to convert a double into a long
     * @return this builder
     */
    default CodeBuilder d2l() {
        return with(ConvertInstruction.of(Opcode.D2L));
    }

    /**
     * Generate an instruction to add a double
     * @return this builder
     */
    default CodeBuilder dadd() {
        return with(OperatorInstruction.of(Opcode.DADD));
    }

    /**
     * Generate an instruction to load a double from an array
     * @return this builder
     */
    default CodeBuilder daload() {
        return arrayLoad(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to store into a double array
     * @return this builder
     */
    default CodeBuilder dastore() {
        return arrayStore(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to add a double
     * @return this builder
     */
    default CodeBuilder dcmpg() {
        return with(OperatorInstruction.of(Opcode.DCMPG));
    }

    /**
     * Generate an instruction to compare doubles
     * @return this builder
     */
    default CodeBuilder dcmpl() {
        return with(OperatorInstruction.of(Opcode.DCMPL));
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
        return with(OperatorInstruction.of(Opcode.DDIV));
    }

    /**
     * Generate an instruction to load a double from a local variable
     *
     * <p>This may also generate {@code dload_<N>} and
     * {@code wide dload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder dload(int slot) {
        return loadLocal(TypeKind.DoubleType, slot);
    }

    /**
     * Generate an instruction to multiply doubles
     * @return this builder
     */
    default CodeBuilder dmul() {
        return with(OperatorInstruction.of(Opcode.DMUL));
    }

    /**
     * Generate an instruction to negate a double
     * @return this builder
     */
    default CodeBuilder dneg() {
        return with(OperatorInstruction.of(Opcode.DNEG));
    }

    /**
     * Generate an instruction to calculate double remainder
     * @return this builder
     */
    default CodeBuilder drem() {
        return with(OperatorInstruction.of(Opcode.DREM));
    }

    /**
     * Generate an instruction to return a double from the method
     * @return this builder
     */
    default CodeBuilder dreturn() {
        return return_(TypeKind.DoubleType);
    }

    /**
     * Generate an instruction to store a double into a local variable
     *
     * <p>This may also generate {@code dstore_<N>} and
     * {@code wide dstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder dstore(int slot) {
        return storeLocal(TypeKind.DoubleType, slot);
    }

    /**
     * Generate an instruction to subtract doubles
     * @return this builder
     */
    default CodeBuilder dsub() {
        return with(OperatorInstruction.of(Opcode.DSUB));
    }

    /**
     * Generate an instruction to duplicate the top operand stack value
     * @return this builder
     */
    default CodeBuilder dup() {
        return with(StackInstruction.of(Opcode.DUP));
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack value
     * @return this builder
     */
    default CodeBuilder dup2() {
        return with(StackInstruction.of(Opcode.DUP2));
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack values and insert two or three
     * values down
     * @return this builder
     */
    default CodeBuilder dup2_x1() {
        return with(StackInstruction.of(Opcode.DUP2_X1));
    }

    /**
     * Generate an instruction to duplicate the top one or two operand stack values and insert two, three,
     * or four values down
     * @return this builder
     */
    default CodeBuilder dup2_x2() {
        return with(StackInstruction.of(Opcode.DUP2_X2));
    }

    /**
     * Generate an instruction to duplicate the top operand stack value and insert two values down
     * @return this builder
     */
    default CodeBuilder dup_x1() {
        return with(StackInstruction.of(Opcode.DUP_X1));
    }

    /**
     * Generate an instruction to duplicate the top operand stack value and insert two or three values down
     * @return this builder
     */
    default CodeBuilder dup_x2() {
        return with(StackInstruction.of(Opcode.DUP_X2));
    }

    /**
     * Generate an instruction to convert a float into a double
     * @return this builder
     */
    default CodeBuilder f2d() {
        return with(ConvertInstruction.of(Opcode.F2D));
    }

    /**
     * Generate an instruction to convert a float into an int
     * @return this builder
     */
    default CodeBuilder f2i() {
        return with(ConvertInstruction.of(Opcode.F2I));
    }

    /**
     * Generate an instruction to convert a float into a long
     * @return this builder
     */
    default CodeBuilder f2l() {
        return with(ConvertInstruction.of(Opcode.F2L));
    }

    /**
     * Generate an instruction to add a float
     * @return this builder
     */
    default CodeBuilder fadd() {
        return with(OperatorInstruction.of(Opcode.FADD));
    }

    /**
     * Generate an instruction to load a float from an array
     * @return this builder
     */
    default CodeBuilder faload() {
        return arrayLoad(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to store into a float array
     * @return this builder
     */
    default CodeBuilder fastore() {
        return arrayStore(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to compare floats
     * @return this builder
     */
    default CodeBuilder fcmpg() {
        return with(OperatorInstruction.of(Opcode.FCMPG));
    }

    /**
     * Generate an instruction to compare floats
     * @return this builder
     */
    default CodeBuilder fcmpl() {
        return with(OperatorInstruction.of(Opcode.FCMPL));
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
        return with(OperatorInstruction.of(Opcode.FDIV));
    }

    /**
     * Generate an instruction to load a float from a local variable
     *
     * <p>This may also generate {@code fload_<N>} and
     * {@code wide fload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder fload(int slot) {
        return loadLocal(TypeKind.FloatType, slot);
    }

    /**
     * Generate an instruction to multiply floats
     * @return this builder
     */
    default CodeBuilder fmul() {
        return with(OperatorInstruction.of(Opcode.FMUL));
    }

    /**
     * Generate an instruction to negate a float
     * @return this builder
     */
    default CodeBuilder fneg() {
        return with(OperatorInstruction.of(Opcode.FNEG));
    }

    /**
     * Generate an instruction to calculate floats remainder
     * @return this builder
     */
    default CodeBuilder frem() {
        return with(OperatorInstruction.of(Opcode.FREM));
    }

    /**
     * Generate an instruction to return a float from the method
     * @return this builder
     */
    default CodeBuilder freturn() {
        return return_(TypeKind.FloatType);
    }

    /**
     * Generate an instruction to store a float into a local variable
     *
     * <p>This may also generate {@code fstore_<N>} and
     * {@code wide fstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder fstore(int slot) {
        return storeLocal(TypeKind.FloatType, slot);
    }

    /**
     * Generate an instruction to subtract floats
     * @return this builder
     */
    default CodeBuilder fsub() {
        return with(OperatorInstruction.of(Opcode.FSUB));
    }

    /**
     * Generate an instruction to fetch field from an object
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder getfield(FieldRefEntry ref) {
        return fieldAccess(Opcode.GETFIELD, ref);
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
        return fieldAccess(Opcode.GETFIELD, owner, name, type);
    }

    /**
     * Generate an instruction to get static field from a class
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder getstatic(FieldRefEntry ref) {
        return fieldAccess(Opcode.GETSTATIC, ref);
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
        return fieldAccess(Opcode.GETSTATIC, owner, name, type);
    }

    /**
     * Generate an instruction to branch always
     *
     * <p>This may also generate {@code goto_w} instructions if the {@link
     * ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS FIX_SHORT_JUMPS} option
     * is set.
     *
     * @apiNote The instruction's name is {@code goto}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with an extra {@code _} suffix instead.
     *
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder goto_(Label target) {
        return branch(Opcode.GOTO, target);
    }

    /**
     * Generate an instruction to branch always with wide index
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder goto_w(Label target) {
        return branch(Opcode.GOTO_W, target);
    }

    /**
     * Generate an instruction to convert an int into a byte
     * @return this builder
     */
    default CodeBuilder i2b() {
        return with(ConvertInstruction.of(Opcode.I2B));
    }

    /**
     * Generate an instruction to convert an int into a char
     * @return this builder
     */
    default CodeBuilder i2c() {
        return with(ConvertInstruction.of(Opcode.I2C));
    }

    /**
     * Generate an instruction to convert an int into a double
     * @return this builder
     */
    default CodeBuilder i2d() {
        return with(ConvertInstruction.of(Opcode.I2D));
    }

    /**
     * Generate an instruction to convert an int into a float
     * @return this builder
     */
    default CodeBuilder i2f() {
        return with(ConvertInstruction.of(Opcode.I2F));
    }

    /**
     * Generate an instruction to convert an int into a long
     * @return this builder
     */
    default CodeBuilder i2l() {
        return with(ConvertInstruction.of(Opcode.I2L));
    }

    /**
     * Generate an instruction to convert an int into a short
     * @return this builder
     */
    default CodeBuilder i2s() {
        return with(ConvertInstruction.of(Opcode.I2S));
    }

    /**
     * Generate an instruction to add an int
     * @return this builder
     */
    default CodeBuilder iadd() {
        return with(OperatorInstruction.of(Opcode.IADD));
    }

    /**
     * Generate an instruction to load a int from an array
     * @return this builder
     */
    default CodeBuilder iaload() {
        return arrayLoad(TypeKind.IntType);
    }

    /**
     * Generate an instruction to calculate boolean AND of ints
     * @return this builder
     */
    default CodeBuilder iand() {
        return with(OperatorInstruction.of(Opcode.IAND));
    }

    /**
     * Generate an instruction to store into an int array
     * @return this builder
     */
    default CodeBuilder iastore() {
        return arrayStore(TypeKind.IntType);
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
        return with(OperatorInstruction.of(Opcode.IDIV));
    }

    /**
     * Generate an instruction to branch if reference comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_acmpeq(Label target) {
        return branch(Opcode.IF_ACMPEQ, target);
    }

    /**
     * Generate an instruction to branch if reference comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_acmpne(Label target) {
        return branch(Opcode.IF_ACMPNE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpeq(Label target) {
        return branch(Opcode.IF_ICMPEQ, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpge(Label target) {
        return branch(Opcode.IF_ICMPGE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpgt(Label target) {
        return branch(Opcode.IF_ICMPGT, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmple(Label target) {
        return branch(Opcode.IF_ICMPLE, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmplt(Label target) {
        return branch(Opcode.IF_ICMPLT, target);
    }

    /**
     * Generate an instruction to branch if int comparison succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder if_icmpne(Label target) {
        return branch(Opcode.IF_ICMPNE, target);
    }

    /**
     * Generate an instruction to branch if reference is not null
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifnonnull(Label target) {
        return branch(Opcode.IFNONNULL, target);
    }

    /**
     * Generate an instruction to branch if reference is null
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifnull(Label target) {
        return branch(Opcode.IFNULL, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifeq(Label target) {
        return branch(Opcode.IFEQ, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifge(Label target) {
        return branch(Opcode.IFGE, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifgt(Label target) {
        return branch(Opcode.IFGT, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifle(Label target) {
        return branch(Opcode.IFLE, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder iflt(Label target) {
        return branch(Opcode.IFLT, target);
    }

    /**
     * Generate an instruction to branch if int comparison with zero succeeds
     * @param target the branch target
     * @return this builder
     */
    default CodeBuilder ifne(Label target) {
        return branch(Opcode.IFNE, target);
    }

    /**
     * Generate an instruction to increment a local variable by a constant
     * @param slot the local variable slot
     * @param val the increment value
     * @return this builder
     */
    default CodeBuilder iinc(int slot, int val) {
        return with(IncrementInstruction.of(slot, val));
    }

    /**
     * Generate an instruction to load an int from a local variable
     *
     * <p>This may also generate {@code iload_<N>} and
     * {@code wide iload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder iload(int slot) {
        return loadLocal(TypeKind.IntType, slot);
    }

    /**
     * Generate an instruction to multiply ints
     * @return this builder
     */
    default CodeBuilder imul() {
        return with(OperatorInstruction.of(Opcode.IMUL));
    }

    /**
     * Generate an instruction to negate an int
     * @return this builder
     */
    default CodeBuilder ineg() {
        return with(OperatorInstruction.of(Opcode.INEG));
    }

    /**
     * Generate an instruction to determine if an object is of the given type
     *
     * @apiNote The instruction's name is {@code instanceof}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with camel case instead.
     *
     * @param target the target type
     * @return this builder
     * @since 23
     */
    default CodeBuilder instanceOf(ClassEntry target) {
        return with(TypeCheckInstruction.of(Opcode.INSTANCEOF, target));
    }

    /**
     * Generate an instruction to determine if an object is of the given type
     *
     * @apiNote The instruction's name is {@code instanceof}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with camel case instead.
     *
     * @param target the target type
     * @return this builder
     * @throws IllegalArgumentException if {@code target} represents a primitive type
     * @since 23
     */
    default CodeBuilder instanceOf(ClassDesc target) {
        return instanceOf(constantPool().classEntry(target));
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param ref the dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        return with(InvokeDynamicInstruction.of(ref));
    }

    /**
     * Generate an instruction to invoke a dynamically-computed call site
     * @param ref the dynamic call site
     * @return this builder
     */
    default CodeBuilder invokedynamic(DynamicCallSiteDesc ref) {
        MethodHandleEntry bsMethod = handleDescToHandleInfo(constantPool(), (DirectMethodHandleDesc) ref.bootstrapMethod());
        var cpArgs = ref.bootstrapArgs();
        List<LoadableConstantEntry> bsArguments = new ArrayList<>(cpArgs.length);
        for (var constantValue : cpArgs) {
            bsArguments.add(BytecodeHelpers.constantEntry(constantPool(), constantValue));
        }
        BootstrapMethodEntry bm = constantPool().bsmEntry(bsMethod, bsArguments);
        NameAndTypeEntry nameAndType = constantPool().nameAndTypeEntry(ref.invocationName(), ref.invocationType());
        return invokedynamic(constantPool().invokeDynamicEntry(bm, nameAndType));
    }

    /**
     * Generate an instruction to invoke an interface method
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKEINTERFACE, ref);
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
        return invoke(Opcode.INVOKEINTERFACE, constantPool().interfaceMethodRefEntry(owner, name, type));
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Generate an instruction to invoke an instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokespecial(MethodRefEntry ref) {
        return invoke(Opcode.INVOKESPECIAL, ref);
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
        return invoke(Opcode.INVOKESPECIAL, owner, name, type, false);
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
        return invoke(Opcode.INVOKESPECIAL, owner, name, type, isInterface);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param ref the interface method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Generate an instruction to invoke a class (static) method
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokestatic(MethodRefEntry ref) {
        return invoke(Opcode.INVOKESTATIC, ref);
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
        return invoke(Opcode.INVOKESTATIC, owner, name, type, false);
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
        return invoke(Opcode.INVOKESTATIC, owner, name, type, isInterface);
    }

    /**
     * Generate an instruction to invoke an instance method; dispatch based on class
     * @param ref the method reference
     * @return this builder
     */
    default CodeBuilder invokevirtual(MethodRefEntry ref) {
        return invoke(Opcode.INVOKEVIRTUAL, ref);
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
        return invoke(Opcode.INVOKEVIRTUAL, owner, name, type, false);
    }

    /**
     * Generate an instruction to calculate boolean OR of ints
     * @return this builder
     */
    default CodeBuilder ior() {
        return with(OperatorInstruction.of(Opcode.IOR));
    }

    /**
     * Generate an instruction to calculate ints remainder
     * @return this builder
     */
    default CodeBuilder irem() {
        return with(OperatorInstruction.of(Opcode.IREM));
    }

    /**
     * Generate an instruction to return an int from the method
     * @return this builder
     */
    default CodeBuilder ireturn() {
        return return_(TypeKind.IntType);
    }

    /**
     * Generate an instruction to shift an int left
     * @return this builder
     */
    default CodeBuilder ishl() {
        return with(OperatorInstruction.of(Opcode.ISHL));
    }

    /**
     * Generate an instruction to shift an int right
     * @return this builder
     */
    default CodeBuilder ishr() {
        return with(OperatorInstruction.of(Opcode.ISHR));
    }

    /**
     * Generate an instruction to store an int into a local variable
     *
     * <p>This may also generate {@code istore_<N>} and
     * {@code wide istore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder istore(int slot) {
        return storeLocal(TypeKind.IntType, slot);
    }

    /**
     * Generate an instruction to subtract ints
     * @return this builder
     */
    default CodeBuilder isub() {
        return with(OperatorInstruction.of(Opcode.ISUB));
    }

    /**
     * Generate an instruction to logical shift an int right
     * @return this builder
     */
    default CodeBuilder iushr() {
        return with(OperatorInstruction.of(Opcode.IUSHR));
    }

    /**
     * Generate an instruction to calculate boolean XOR of ints
     * @return this builder
     */
    default CodeBuilder ixor() {
        return with(OperatorInstruction.of(Opcode.IXOR));
    }

    /**
     * Generate an instruction to access a jump table by key match and jump
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     */
    default CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        return with(LookupSwitchInstruction.of(defaultTarget, cases));
    }

    /**
     * Generate an instruction to convert a long into a double
     * @return this builder
     */
    default CodeBuilder l2d() {
        return with(ConvertInstruction.of(Opcode.L2D));
    }

    /**
     * Generate an instruction to convert a long into a float
     * @return this builder
     */
    default CodeBuilder l2f() {
        return with(ConvertInstruction.of(Opcode.L2F));
    }

    /**
     * Generate an instruction to convert a long into an int
     * @return this builder
     */
    default CodeBuilder l2i() {
        return with(ConvertInstruction.of(Opcode.L2I));
    }

    /**
     * Generate an instruction to add a long
     * @return this builder
     */
    default CodeBuilder ladd() {
        return with(OperatorInstruction.of(Opcode.LADD));
    }

    /**
     * Generate an instruction to load a long from an array
     * @return this builder
     */
    default CodeBuilder laload() {
        return arrayLoad(TypeKind.LongType);
    }

    /**
     * Generate an instruction to calculate boolean AND of longs
     * @return this builder
     */
    default CodeBuilder land() {
        return with(OperatorInstruction.of(Opcode.LAND));
    }

    /**
     * Generate an instruction to store into a long array
     * @return this builder
     */
    default CodeBuilder lastore() {
        return arrayStore(TypeKind.LongType);
    }

    /**
     * Generate an instruction to compare longs
     * @return this builder
     */
    default CodeBuilder lcmp() {
        return with(OperatorInstruction.of(Opcode.LCMP));
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
     *
     * <p>This may also generate {@code ldc_w} and {@code ldc2_w} instructions.
     *
     * @apiNote {@link #loadConstant(ConstantDesc) loadConstant} generates more optimal instructions
     * and should be used for general constants if an {@code ldc} instruction is not strictly required.
     *
     * @param value the constant value
     * @return this builder
     */
    default CodeBuilder ldc(ConstantDesc value) {
        return ldc(BytecodeHelpers.constantEntry(constantPool(), value));
    }

    /**
     * Generate an instruction pushing an item from the run-time constant pool onto the operand stack
     *
     * <p>This may also generate {@code ldc_w} and {@code ldc2_w} instructions.
     *
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
        return with(OperatorInstruction.of(Opcode.LDIV));
    }

    /**
     * Generate an instruction to load a long from a local variable
     *
     * <p>This may also generate {@code lload_<N>} and
     * {@code wide lload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder lload(int slot) {
        return loadLocal(TypeKind.LongType, slot);
    }

    /**
     * Generate an instruction to multiply longs
     * @return this builder
     */
    default CodeBuilder lmul() {
        return with(OperatorInstruction.of(Opcode.LMUL));
    }

    /**
     * Generate an instruction to negate a long
     * @return this builder
     */
    default CodeBuilder lneg() {
        return with(OperatorInstruction.of(Opcode.LNEG));
    }

    /**
     * Generate an instruction to calculate boolean OR of longs
     * @return this builder
     */
    default CodeBuilder lor() {
        return with(OperatorInstruction.of(Opcode.LOR));
    }

    /**
     * Generate an instruction to calculate longs remainder
     * @return this builder
     */
    default CodeBuilder lrem() {
        return with(OperatorInstruction.of(Opcode.LREM));
    }

    /**
     * Generate an instruction to return a long from the method
     * @return this builder
     */
    default CodeBuilder lreturn() {
        return return_(TypeKind.LongType);
    }

    /**
     * Generate an instruction to shift a long left
     * @return this builder
     */
    default CodeBuilder lshl() {
        return with(OperatorInstruction.of(Opcode.LSHL));
    }

    /**
     * Generate an instruction to shift a long right
     * @return this builder
     */
    default CodeBuilder lshr() {
        return with(OperatorInstruction.of(Opcode.LSHR));
    }

    /**
     * Generate an instruction to store a long into a local variable
     *
     * <p>This may also generate {@code lstore_<N>} and
     * {@code wide lstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     */
    default CodeBuilder lstore(int slot) {
        return storeLocal(TypeKind.LongType, slot);
    }

    /**
     * Generate an instruction to subtract longs
     * @return this builder
     */
    default CodeBuilder lsub() {
        return with(OperatorInstruction.of(Opcode.LSUB));
    }

    /**
     * Generate an instruction to logical shift a long left
     * @return this builder
     */
    default CodeBuilder lushr() {
        return with(OperatorInstruction.of(Opcode.LUSHR));
    }

    /**
     * Generate an instruction to calculate boolean XOR of longs
     * @return this builder
     */
    default CodeBuilder lxor() {
        return with(OperatorInstruction.of(Opcode.LXOR));
    }

    /**
     * Generate an instruction to enter monitor for an object
     * @return this builder
     */
    default CodeBuilder monitorenter() {
        return with(MonitorInstruction.of(Opcode.MONITORENTER));
    }

    /**
     * Generate an instruction to exit monitor for an object
     * @return this builder
     */
    default CodeBuilder monitorexit() {
        return with(MonitorInstruction.of(Opcode.MONITOREXIT));
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     */
    default CodeBuilder multianewarray(ClassEntry array, int dims) {
        return with(NewMultiArrayInstruction.of(array, dims));
    }

    /**
     * Generate an instruction to create a new multidimensional array
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     * @throws IllegalArgumentException if {@code array} represents a primitive type
     */
    default CodeBuilder multianewarray(ClassDesc array, int dims) {
        return multianewarray(constantPool().classEntry(array), dims);
    }

    /**
     * Generate an instruction to create a new object
     *
     * @apiNote The instruction's name is {@code new}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with an extra {@code _} suffix instead.
     *
     * @param clazz the new class type
     * @return this builder
     */
    default CodeBuilder new_(ClassEntry clazz) {
        return with(NewObjectInstruction.of(clazz));
    }

    /**
     * Generate an instruction to create a new object
     *
     * @apiNote The instruction's name is {@code new}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with an extra {@code _} suffix instead.
     *
     * @param clazz the new class type
     * @return this builder
     * @throws IllegalArgumentException if {@code clazz} represents a primitive type
     */
    default CodeBuilder new_(ClassDesc clazz) {
        return new_(constantPool().classEntry(clazz));
    }

    /**
     * Generate an instruction to create a new array of a primitive type
     * @param typeKind the primitive array type
     * @return this builder
     */
    default CodeBuilder newarray(TypeKind typeKind) {
        return with(NewPrimitiveArrayInstruction.of(typeKind));
    }

    /**
     * Generate an instruction to pop the top operand stack value
     * @return this builder
     */
    default CodeBuilder pop() {
        return with(StackInstruction.of(Opcode.POP));
    }

    /**
     * Generate an instruction to pop the top one or two operand stack values
     * @return this builder
     */
    default CodeBuilder pop2() {
        return with(StackInstruction.of(Opcode.POP2));
    }

    /**
     * Generate an instruction to set field in an object
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder putfield(FieldRefEntry ref) {
        return fieldAccess(Opcode.PUTFIELD, ref);
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
        return fieldAccess(Opcode.PUTFIELD, owner, name, type);
    }

    /**
     * Generate an instruction to set static field in a class
     * @param ref the field reference
     * @return this builder
     */
    default CodeBuilder putstatic(FieldRefEntry ref) {
        return fieldAccess(Opcode.PUTSTATIC, ref);
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
        return fieldAccess(Opcode.PUTSTATIC, owner, name, type);
    }

    /**
     * Generate an instruction to return void from the method
     *
     * @apiNote The instruction's name is {@code return}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with an extra {@code _} suffix instead.
     *
     * @return this builder
     */
    default CodeBuilder return_() {
        return return_(TypeKind.VoidType);
    }

    /**
     * Generate an instruction to load a short from an array
     * @return this builder
     */
    default CodeBuilder saload() {
        return arrayLoad(TypeKind.ShortType);
    }

    /**
     * Generate an instruction to store into a short array
     * @return this builder
     */
    default CodeBuilder sastore() {
        return arrayStore(TypeKind.ShortType);
    }

    /**
     * Generate an instruction pushing a short onto the operand stack
     * @param s the short
     * @return this builder
     */
    default CodeBuilder sipush(int s) {
        return loadConstant(Opcode.SIPUSH, s);
    }

    /**
     * Generate an instruction to swap the top two operand stack values
     * @return this builder
     */
    default CodeBuilder swap() {
        return with(StackInstruction.of(Opcode.SWAP));
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
        return with(TableSwitchInstruction.of(low, high, defaultTarget, cases));
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
        return tableswitch(low, high, defaultTarget, cases);
    }
}
