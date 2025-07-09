/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassFile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
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

import jdk.internal.classfile.impl.*;

import static java.util.Objects.requireNonNull;
import static jdk.internal.classfile.impl.BytecodeHelpers.handleDescToHandleInfo;

/**
 * A builder for {@link CodeModel Code} attributes (method bodies).  {@link
 * MethodBuilder#withCode} is the basic way to obtain a code builder; {@link
 * ClassBuilder#withMethodBody} is a shortcut.  There are also derived code
 * builders from {@link #block}, which handles code blocks and {@link
 * #transforming}, which runs transforms on existing handlers, both of which
 * requires a code builder to be available first.
 * <p>
 * Refer to {@link ClassFileBuilder} for general guidance and caution around
 * the use of builders for structures in the {@code class} file format.  Unlike
 * in other builders, the order of member elements in a code builder is
 * significant: they affect the resulting bytecode.  Many Class-File API options
 * affect code builders: {@link DeadCodeOption} and {@link ShortJumpsOption}
 * affect the resulting bytecode, and {@link DeadLabelsOption}, {@link
 * DebugElementsOption}, {@link LineNumbersOption}, {@link StackMapsOption}, and
 * {@link AttributesProcessingOption} affect the resulting attributes on the
 * built {@code Code} attribute, that some elements sent to a code builder is
 * otherwise ignored.
 *
 * <h2 id="instruction-factories">Instruction Factories</h2>
 * {@code CodeBuilder} provides convenience methods to create instructions (See
 * JVMS {@jvms 6.5} Instructions) by their mnemonic, taking necessary operands.
 * <ul>
 * <li>Instructions that encode their operands in their opcode, such as {@code
 * aload_<n>}, share their factories with their generic version like {@link
 * #aload aload}. Note that some constant instructions, such as {@link #iconst_1
 * iconst_1}, do not have generic versions, and thus have their own factories.
 * <li>Instructions that accept wide operands, such as {@code ldc2_w} or {@code
 * wide}, share their factories with their regular version like {@link #ldc}.
 * Note that {@link #goto_w goto_w} has its own factory to avoid {@linkplain
 * ShortJumpsOption short jumps}.
 * <li>The {@code goto}, {@code instanceof}, {@code new}, and {@code return}
 * instructions' factories are named {@link #goto_ goto_}, {@link #instanceOf
 * instanceOf}, {@link #new_ new_}, and {@link #return_() return_} respectively,
 * due to clashes with keywords in the Java programming language.
 * <li>Factories are not provided for instructions {@link Opcode#JSR jsr},
 * {@link Opcode#JSR_W jsr_w}, {@link Opcode#RET ret}, and {@link Opcode#RET_W
 * wide ret}, which cannot appear in class files with major version {@value
 * ClassFile#JAVA_7_VERSION} or higher. (JVMS {@jvms 4.9.1})  They can still be
 * provided via {@link #with}.
 * </ul>
 *
 * @see MethodBuilder#withCode
 * @see CodeModel
 * @see CodeTransform
 * @since 24
 */
public sealed interface CodeBuilder
        extends ClassFileBuilder<CodeElement, CodeBuilder>
        permits CodeBuilder.BlockCodeBuilder, ChainedCodeBuilder, TerminalCodeBuilder, NonterminalCodeBuilder {

    /**
     * {@return a fresh unbound label}
     * The label can be bound with {@link #labelBinding}.
     */
    Label newLabel();

    /**
     * {@return the label associated with the beginning of the current block}
     * If this builder is not a "block" builder, such as those provided by
     * {@link #block(Consumer)} or {@link #ifThenElse(Consumer, Consumer)},
     * the current block will be the entire method body.
     */
    Label startLabel();

    /**
     * {@return the label associated with the end of the current block}
     * If this builder is not a "block" builder, such as those provided by
     * {@link #block(Consumer)} or {@link #ifThenElse(Consumer, Consumer)},
     * the current block will be the entire method body.
     */
    Label endLabel();

    /**
     * {@return the local variable slot associated with the receiver}
     *
     * @throws IllegalStateException if this is a static method
     */
    int receiverSlot();

    /**
     * {@return the local variable slot associated with the specified parameter}
     * The returned value is adjusted for the receiver slot (if the method is
     * an instance method) and for the requirement that {@link TypeKind#LONG
     * long} and {@link TypeKind#DOUBLE double}
     * values require two slots.
     *
     * @param paramNo the index of the parameter
     * @throws IndexOutOfBoundsException if the parameter index is out of bounds
     */
    int parameterSlot(int paramNo);

    /**
     * {@return the local variable slot of a fresh local variable}  This method
     * makes reasonable efforts to determine which slots are in use and which
     * are not.  When transforming a method, fresh locals begin at the {@code
     * maxLocals} of the original method.  For a method being built directly,
     * fresh locals begin after the last parameter slot.
     * <p>
     * If the current code builder is a {@link BlockCodeBuilder}, at the end of
     * the block, locals are reset to their value at the beginning of the block.
     *
     * @param typeKind the type of the local variable
     */
    int allocateLocal(TypeKind typeKind);

    /**
     * Apply a transform to the code built by a handler, directing results to
     * this builder.
     *
     * @apiNote
     * This is similar to {@link #transform}, but this does not require the
     * code elements to be viewed as a {@link CodeModel} first.
     *
     * @param transform the transform to apply to the code built by the handler
     * @param handler the handler that receives a {@link CodeBuilder} to
     * build the code
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
     * A builder for blocks of code.  Its {@link #startLabel()} and {@link
     * #endLabel()} do not enclose the entire method body, but from the start to
     * the end of the block.
     *
     * @since 24
     */
    sealed interface BlockCodeBuilder extends CodeBuilder
            permits BlockCodeBuilderImpl {
        /**
         * {@return the label locating where control is passed back to the
         * parent block}  A branch to this label "break"'s out of the current
         * block.
         * <p>
         * If the last instruction in this block does not lead to the break
         * label, Class-File API may append instructions to target the "break"
         * label to the built block.
         */
        Label breakLabel();
    }

    /**
     * Adds a lexical block to the method being built.
     * <p>
     * Within this block, the {@link #startLabel()} and {@link #endLabel()}
     * correspond to the start and end of the block, and the {@link
     * BlockCodeBuilder#breakLabel()} also corresponds to the end of the block,
     * or the cursor position immediately after this call in this builder.
     *
     * @param handler handler that receives a {@link BlockCodeBuilder} to
     * generate the body of the lexical block
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
     * Adds an "if-then" block that is conditional on the {@link TypeKind#BOOLEAN
     * boolean} value on top of the operand stack.  Control flow enters the
     * "then" block if the value represents {@code true}.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for the "then" block corresponds
     * to the cursor position immediately after this call in this builder.
     *
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder}
     *                    to generate the body of the {@code if}
     * @return this builder
     * @see #ifThen(Opcode, Consumer)
     */
    default CodeBuilder ifThen(Consumer<BlockCodeBuilder> thenHandler) {
        return ifThen(Opcode.IFNE, thenHandler);
    }

    /**
     * Adds an "if-then" block that is conditional on the value(s) on top of the
     * operand stack in accordance with the given opcode.  Control flow enters
     * the "then" block if the branching condition for {@code opcode} succeeds.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for the "then" block corresponds
     * to the cursor position immediately after this call in this builder.
     *
     * @param opcode the operation code for a branch instruction that accepts
     *               one or two operands on the stack
     * @param thenHandler handler that receives a {@link BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @return this builder
     * @throws IllegalArgumentException if the operation code is not for a
     *         branch instruction that accepts one or two operands
     */
    default CodeBuilder ifThen(Opcode opcode,
                               Consumer<BlockCodeBuilder> thenHandler) {
        if (opcode.kind() != Opcode.Kind.BRANCH || BytecodeHelpers.isUnconditionalBranch(opcode)) {
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
     * Adds an "if-then-else" block that is conditional on the {@link
     * TypeKind#BOOLEAN boolean} value on top of the operand stack.  Control
     * flow enters the "then" block if the value represents {@code true}, and
     * enters the "else" block otherwise.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for each block corresponds to
     * the cursor position immediately after this call in this builder.
     *
     * @param thenHandler handler that receives a {@link BlockCodeBuilder} to
     *                    generate the body of the {@code if}
     * @param elseHandler handler that receives a {@link BlockCodeBuilder} to
     *                    generate the body of the {@code else}
     * @return this builder
     * @see #ifThenElse(Opcode, Consumer, Consumer)
     */
    default CodeBuilder ifThenElse(Consumer<BlockCodeBuilder> thenHandler,
                                   Consumer<BlockCodeBuilder> elseHandler) {
        return ifThenElse(Opcode.IFNE, thenHandler, elseHandler);
    }

    /**
     * Adds an "if-then-else" block that is conditional on the value(s) on top
     * of the operand stack in accordance with the given opcode.  Control flow
     * enters the "then" block if the branching condition for {@code opcode}
     * succeeds, and enters the "else" block otherwise.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for each block corresponds to
     * the cursor position immediately after this call in this builder.
     *
     * @param opcode the operation code for a branch instruction that accepts
     *               one or two operands on the stack
     * @param thenHandler handler that receives a {@linkplain BlockCodeBuilder}
     *                    to generate the body of the {@code if}
     * @param elseHandler handler that receives a {@linkplain BlockCodeBuilder}
     *                    to generate the body of the {@code else}
     * @return this builder
     * @throws IllegalArgumentException if the operation code is not for a
     *         branch instruction that accepts one or two operands
     */
    default CodeBuilder ifThenElse(Opcode opcode,
                                   Consumer<BlockCodeBuilder> thenHandler,
                                   Consumer<BlockCodeBuilder> elseHandler) {
        if (opcode.kind() != Opcode.Kind.BRANCH || BytecodeHelpers.isUnconditionalBranch(opcode)) {
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
     * @see ExceptionCatch
     * @since 24
     */
    sealed interface CatchBuilder permits CatchBuilderImpl {
        /**
         * Adds a catch block that catches an exception of the given type.
         * <p>
         * The caught exception will be on top of the operand stack when the
         * catch block is entered.
         * <p>
         * The {@link BlockCodeBuilder#breakLabel()} for the catch block corresponds
         * to the break label of the {@code tryHandler} block in {@link #trying}.
         * <p>
         * If the type of exception is {@code null} then the catch block catches
         * all exceptions.
         *
         * @param exceptionType the type of exception to catch, may be {@code null}
         * @param catchHandler handler that receives a {@link BlockCodeBuilder} to
         *                     generate the body of the catch block
         * @return this builder
         * @throws IllegalArgumentException if an existing catch block catches
         *         an exception of the given type or {@code exceptionType}
         *         represents a primitive type
         * @see #catchingMulti
         * @see #catchingAll
         */
        CatchBuilder catching(ClassDesc exceptionType, Consumer<BlockCodeBuilder> catchHandler);

        /**
         * Adds a catch block that catches exceptions of the given types.
         * <p>
         * The caught exception will be on top of the operand stack when the
         * catch block is entered.
         * <p>
         * The {@link BlockCodeBuilder#breakLabel()} for the catch block corresponds
         * to the break label of the {@code tryHandler} block in {@link #trying}.
         * <p>
         * If list of exception types is empty then the catch block catches all
         * exceptions.
         *
         * @param exceptionTypes the types of exception to catch
         * @param catchHandler handler that receives a {@link BlockCodeBuilder}
         *                     to generate the body of the catch block
         * @return this builder
         * @throws IllegalArgumentException if an existing catch block catches
         *         one or more exceptions of the given types
         * @see #catching
         * @see #catchingAll
         */
        CatchBuilder catchingMulti(List<ClassDesc> exceptionTypes, Consumer<BlockCodeBuilder> catchHandler);

        /**
         * Adds a "catch" block that catches all exceptions.
         * <p>
         * The {@link BlockCodeBuilder#breakLabel()} for the catch block corresponds
         * to the break label of the {@code tryHandler} block in {@link #trying}.
         * <p>
         * The caught exception will be on top of the operand stack when the
         * catch block is entered.
         *
         * @param catchAllHandler handler that receives a {@link BlockCodeBuilder}
         *                        to generate the body of the catch block
         * @throws IllegalArgumentException if an existing catch block catches
         *         all exceptions
         * @see #catching
         * @see #catchingMulti
         */
        void catchingAll(Consumer<BlockCodeBuilder> catchAllHandler);
    }

    /**
     * Adds a "try-catch" block comprising one try block and zero or more catch
     * blocks.  Exceptions thrown by instructions in the try block may be caught
     * by catch blocks.
     * <p>
     * The {@link BlockCodeBuilder#breakLabel()} for the try block and all
     * catch blocks in the {@code catchesHandler} correspond to the cursor
     * position immediately after this call in this builder.
     *
     * @param tryHandler handler that receives a {@link BlockCodeBuilder} to
     *                   generate the body of the try block.
     * @param catchesHandler a handler that receives a {@link CatchBuilder}
     *                       to generate bodies of catch blocks
     * @return this builder
     * @throws IllegalArgumentException if the try block is empty
     * @see CatchBuilder
     * @see ExceptionCatch
     * @see #exceptionCatch
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
     * Generates an instruction to load a value from a local variable.
     *
     * @param tk the load type
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code tk} is {@link TypeKind#VOID
     *         void} or {@code slot} is out of range
     * @see LoadInstruction
     */
    default CodeBuilder loadLocal(TypeKind tk, int slot) {
        return with(LoadInstruction.of(tk, slot));
    }

    /**
     * Generates an instruction to store a value to a local variable.
     *
     * @param tk the store type
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code tk} is {@link TypeKind#VOID
     *         void} or {@code slot} is out of range
     * @see StoreInstruction
     */
    default CodeBuilder storeLocal(TypeKind tk, int slot) {
        return with(StoreInstruction.of(tk, slot));
    }

    /**
     * Generates a branch instruction.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set, the
     * opcode has {@linkplain Opcode#sizeIfFixed() size} 3, and {@code target}
     * cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param op the branch opcode
     * @param target the branch target
     * @return this builder
     * @throws IllegalArgumentException if {@code op} is not of {@link
     *         Opcode.Kind#BRANCH}
     * @see BranchInstruction
     */
    default CodeBuilder branch(Opcode op, Label target) {
        return with(BranchInstruction.of(op, target));
    }

    /**
     * Generates a return instruction.
     *
     * @param tk the return type
     * @return this builder
     * @see ReturnInstruction
     */
    default CodeBuilder return_(TypeKind tk) {
        return with(ReturnInstruction.of(tk));
    }

    /**
     * Generates an instruction to access a field.
     *
     * @param opcode the field access opcode
     * @param ref the field reference
     * @return this builder
     * @throws IllegalArgumentException if {@code opcode} is not of {@link
     *         Opcode.Kind#FIELD_ACCESS}
     * @see FieldInstruction
     */
    default CodeBuilder fieldAccess(Opcode opcode, FieldRefEntry ref) {
        return with(FieldInstruction.of(opcode, ref));
    }

    /**
     * Generates an instruction to access a field.
     *
     * @param opcode the field access opcode
     * @param owner the class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code opcode} is not of {@link
     *         Opcode.Kind#FIELD_ACCESS}, or {@code owner} is primitive
     * @see FieldInstruction
     */
    default CodeBuilder fieldAccess(Opcode opcode, ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(opcode, constantPool().fieldRefEntry(owner, name, type));
    }

    /**
     * Generates an instruction to invoke a method.
     *
     * @param opcode the invoke opcode
     * @param ref the interface method or method reference
     * @return this builder
     * @throws IllegalArgumentException if {@code opcode} is not of {@link
     *         Opcode.Kind#INVOKE}
     * @see InvokeInstruction
     */
    default CodeBuilder invoke(Opcode opcode, MemberRefEntry ref) {
        return with(InvokeInstruction.of(opcode, ref));
    }

    /**
     * Generates an instruction to invoke a method.
     *
     * @param opcode the invoke opcode
     * @param owner the class
     * @param name the method name
     * @param desc the method type
     * @param isInterface whether the owner class is an interface
     * @return this builder
     * @throws IllegalArgumentException if {@code opcode} is not of {@link
     *         Opcode.Kind#INVOKE}, or {@code owner} is primitive
     * @see InvokeInstruction
     */
    default CodeBuilder invoke(Opcode opcode, ClassDesc owner, String name, MethodTypeDesc desc, boolean isInterface) {
        return invoke(opcode,
                isInterface ? constantPool().interfaceMethodRefEntry(owner, name, desc)
                            : constantPool().methodRefEntry(owner, name, desc));
    }

    /**
     * Generates an instruction to load from an array.
     *
     * @param tk the array element type
     * @return this builder
     * @throws IllegalArgumentException if {@code tk} is {@link TypeKind#VOID
     *         void}
     * @see ArrayLoadInstruction
     */
    default CodeBuilder arrayLoad(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayLoadOpcode(tk);
        return with(ArrayLoadInstruction.of(opcode));
    }

    /**
     * Generates an instruction to store into an array.
     *
     * @param tk the array element type
     * @return this builder
     * @throws IllegalArgumentException if {@code tk} is {@link TypeKind#VOID
     *         void}
     * @see ArrayStoreInstruction
     */
    default CodeBuilder arrayStore(TypeKind tk) {
        Opcode opcode = BytecodeHelpers.arrayStoreOpcode(tk);
        return with(ArrayStoreInstruction.of(opcode));
    }

    /**
     * Generates instruction(s) to convert {@code fromType} to {@code toType}.
     *
     * @param fromType the source type
     * @param toType the target type
     * @return this builder
     * @throws IllegalArgumentException for conversions of {@link TypeKind#VOID
     *         void} or {@link TypeKind#REFERENCE reference}
     * @see ConvertInstruction
     */
    default CodeBuilder conversion(TypeKind fromType, TypeKind toType) {
        var computationalFrom = fromType.asLoadable();
        var computationalTo = toType.asLoadable();
        if (computationalFrom != computationalTo) {
            switch (computationalTo) {
                case INT -> {
                    switch (computationalFrom) {
                        case FLOAT -> f2i();
                        case LONG -> l2i();
                        case DOUBLE -> d2i();
                        default -> throw BytecodeHelpers.cannotConvertException(fromType, toType);
                    }
                }
                case FLOAT -> {
                    switch (computationalFrom) {
                        case INT -> i2f();
                        case LONG -> l2f();
                        case DOUBLE -> d2f();
                        default -> throw BytecodeHelpers.cannotConvertException(fromType, toType);
                    }
                }
                case LONG -> {
                    switch (computationalFrom) {
                        case INT -> i2l();
                        case FLOAT -> f2l();
                        case DOUBLE -> d2l();
                        default -> throw BytecodeHelpers.cannotConvertException(fromType, toType);
                    }
                }
                case DOUBLE -> {
                    switch (computationalFrom) {
                        case INT -> i2d();
                        case FLOAT -> f2d();
                        case LONG -> l2d();
                        default -> throw BytecodeHelpers.cannotConvertException(fromType, toType);
                    }
                }
            }
        }
        if (computationalTo == TypeKind.INT && toType != TypeKind.INT) {
            switch (toType) {
                case BOOLEAN -> iconst_1().iand();
                case BYTE -> i2b();
                case CHAR -> i2c();
                case SHORT -> i2s();
            }
        }
        return this;
    }

    /**
     * Generates an instruction pushing a constant onto the operand stack.
     *
     * @param value the constant value, may be {@code null}
     * @return this builder
     * @see ConstantInstruction
     */
    default CodeBuilder loadConstant(ConstantDesc value) {
        //avoid switch expressions here
        if (value == null || value == ConstantDescs.NULL)
            return aconst_null();
        if (value instanceof Number) {
            if (value instanceof Integer) return loadConstant((int)    value);
            if (value instanceof Long   ) return loadConstant((long)   value);
            if (value instanceof Float  ) return loadConstant((float)  value);
            if (value instanceof Double ) return loadConstant((double) value);
        }
        return ldc(value);
    }


    /**
     * Generates an instruction pushing a constant {@link TypeKind#INT int}
     * value onto the operand stack.  This is equivalent to {@link
     * #loadConstant(ConstantDesc) loadConstant(Integer.valueOf(value))}.
     *
     * @param value the int value
     * @return this builder
     * @see ConstantInstruction
     */
    default CodeBuilder loadConstant(int value) {
        return switch (value) {
            case -1 -> iconst_m1();
            case  0 -> iconst_0();
            case  1 -> iconst_1();
            case  2 -> iconst_2();
            case  3 -> iconst_3();
            case  4 -> iconst_4();
            case  5 -> iconst_5();
            default -> (value >= Byte.MIN_VALUE  && value <= Byte.MAX_VALUE ) ? bipush(value)
                     : (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) ? sipush(value)
                     : ldc(constantPool().intEntry(value));
        };
    }

    /**
     * Generates an instruction pushing a constant {@link TypeKind#LONG long}
     * value onto the operand stack.  This is equivalent to {@link
     * #loadConstant(ConstantDesc) loadConstant(Long.valueOf(value))}.
     *
     * @param value the long value
     * @return this builder
     * @see ConstantInstruction
     */
    default CodeBuilder loadConstant(long value) {
        return value == 0l ? lconst_0()
             : value == 1l ? lconst_1()
             : ldc(constantPool().longEntry(value));
    }

    /**
     * Generates an instruction pushing a constant {@link TypeKind#FLOAT float}
     * value onto the operand stack.  This is equivalent to {@link
     * #loadConstant(ConstantDesc) loadConstant(Float.valueOf(value))}.
     * <p>
     * All NaN values of the {@code float} may or may not be collapsed
     * into a single {@linkplain Float#NaN "canonical" NaN value}.
     *
     * @param value the float value
     * @return this builder
     * @see ConstantInstruction
     */
    default CodeBuilder loadConstant(float value) {
        return Float.floatToRawIntBits(value) == 0 ? fconst_0()
             : value == 1.0f                       ? fconst_1()
             : value == 2.0f                       ? fconst_2()
             : ldc(constantPool().floatEntry(value));
    }

    /**
     * Generates an instruction pushing a constant {@link TypeKind#DOUBLE double}
     * value onto the operand stack.  This is equivalent to {@link
     * #loadConstant(ConstantDesc) loadConstant(Double.valueOf(value))}.
     * <p>
     * All NaN values of the {@code double} may or may not be collapsed
     * into a single {@linkplain Double#NaN "canonical" NaN value}.
     *
     * @param value the double value
     * @return this builder
     * @see ConstantInstruction
     */
    default CodeBuilder loadConstant(double value) {
        return Double.doubleToRawLongBits(value) == 0l ? dconst_0()
             : value == 1.0d                           ? dconst_1()
             : ldc(constantPool().doubleEntry(value));
    }

    /**
     * Generates a do-nothing instruction.
     *
     * @return this builder
     * @see NopInstruction
     */
    default CodeBuilder nop() {
        return with(NopInstruction.of());
    }

    // Base pseudo-instruction builder methods

    /**
     * Creates a new label bound at the current position.
     *
     * @return this builder
     * @see #newLabel()
     * @see #labelBinding
     */
    default Label newBoundLabel() {
        var label = newLabel();
        labelBinding(label);
        return label;
    }

    /**
     * Binds a label to the current position.
     *
     * @apiNote
     * The label to bind does not have to be {@linkplain #newLabel() from this
     * builder}; it can be from another parsed {@link CodeModel}.
     *
     * @param label the label
     * @return this builder
     * @see LabelTarget
     */
    default CodeBuilder labelBinding(Label label) {
        return with((LabelImpl) label);
    }

    /**
     * Declares a source line number beginning at the current position.
     * <p>
     * This call may be ignored according to {@link ClassFile.LineNumbersOption}.
     *
     * @param line the line number
     * @return this builder
     * @see LineNumber
     */
    default CodeBuilder lineNumber(int line) {
        return with(LineNumber.of(line));
    }

    /**
     * Declares an exception table entry.
     * <p>
     * This call may be ignored if any of the argument labels is not {@linkplain
     * #labelBinding bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS}
     * is set.
     *
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the catch type, may be {@code null} to catch all exceptions and errors
     * @return this builder
     * @see ExceptionCatch
     * @see CodeBuilder#trying
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassEntry catchType) {
        return with(ExceptionCatch.of(handler, start, end, Optional.ofNullable(catchType)));
    }

    /**
     * Declares an exception table entry.
     * <p>
     * This call may be ignored if any of the argument labels is not {@linkplain
     * #labelBinding bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS}
     * is set.
     *
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the optional catch type, empty to catch all exceptions and errors
     * @return this builder
     * @see ExceptionCatch
     * @see CodeBuilder#trying
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, Optional<ClassEntry> catchType) {
        return with(ExceptionCatch.of(handler, start, end, catchType));
    }

    /**
     * Declares an exception table entry.
     * <p>
     * This call may be ignored if any of the argument labels is not {@linkplain
     * #labelBinding bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS}
     * is set.
     *
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @param catchType the catch type
     * @return this builder
     * @throws IllegalArgumentException if {@code catchType} is primitive
     * @see ExceptionCatch
     * @see CodeBuilder#trying
     */
    default CodeBuilder exceptionCatch(Label start, Label end, Label handler, ClassDesc catchType) {
        requireNonNull(catchType);
        return exceptionCatch(start, end, handler, constantPool().classEntry(catchType));
    }

    /**
     * Declares an exception table entry catching all exceptions and errors.
     * <p>
     * This call may be ignored if any of the argument labels is not {@linkplain
     * #labelBinding bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS}
     * is set.
     *
     * @param start the try block start
     * @param end the try block end
     * @param handler the exception handler start
     * @return this builder
     * @see ExceptionCatch
     * @see CodeBuilder#trying
     */
    default CodeBuilder exceptionCatchAll(Label start, Label end, Label handler) {
        return with(ExceptionCatch.of(handler, start, end));
    }

    /**
     * Declares a character range entry.
     * <p>
     * This call may be ignored if {@link ClassFile.DebugElementsOption#DROP_DEBUG}
     * is set, or if any of the argument labels is not {@linkplain #labelBinding
     * bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS} is set.
     *
     * @param startScope the start scope of the character range
     * @param endScope the end scope of the character range
     * @param characterRangeStart the encoded start of the character range region (inclusive)
     * @param characterRangeEnd the encoded end of the character range region (exclusive)
     * @param flags the flags word, indicating the kind of range
     * @return this builder
     * @see CharacterRange
     */
    default CodeBuilder characterRange(Label startScope, Label endScope, int characterRangeStart, int characterRangeEnd, int flags) {
        return with(CharacterRange.of(startScope, endScope, characterRangeStart, characterRangeEnd, flags));
    }

    /**
     * Declares a local variable entry.
     * <p>
     * This call may be ignored if {@link ClassFile.DebugElementsOption#DROP_DEBUG}
     * is set, or if any of the argument labels is not {@linkplain #labelBinding
     * bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS} is set.
     *
     * @param slot the local variable slot
     * @param nameEntry the variable name
     * @param descriptorEntry the variable descriptor
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see LocalVariable
     */
    default CodeBuilder localVariable(int slot, Utf8Entry nameEntry, Utf8Entry descriptorEntry, Label startScope, Label endScope) {
        return with(LocalVariable.of(slot, nameEntry, descriptorEntry, startScope, endScope));
    }

    /**
     * Declares a local variable entry.
     * <p>
     * This call may be ignored if {@link ClassFile.DebugElementsOption#DROP_DEBUG}
     * is set, or if any of the argument labels is not {@linkplain #labelBinding
     * bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS} is set.
     *
     * @param slot the local variable slot
     * @param name the variable name
     * @param descriptor the variable descriptor
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see LocalVariable
     */
    default CodeBuilder localVariable(int slot, String name, ClassDesc descriptor, Label startScope, Label endScope) {
        return localVariable(slot,
                             constantPool().utf8Entry(name),
                             constantPool().utf8Entry(descriptor),
                             startScope, endScope);
    }

    /**
     * Declares a local variable type entry.
     * <p>
     * This call may be ignored if {@link ClassFile.DebugElementsOption#DROP_DEBUG}
     * is set, or if any of the argument labels is not {@linkplain #labelBinding
     * bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS} is set.
     *
     * @apiNote
     * When a local variable type entry is declared, a local variable entry with
     * the descriptor derived from erasure (JLS {@jls 4.6}) of the signature
     * should be declared as well.
     *
     * @param slot the local variable slot
     * @param nameEntry the variable name
     * @param signatureEntry the variable signature
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see LocalVariableType
     */
    default CodeBuilder localVariableType(int slot, Utf8Entry nameEntry, Utf8Entry signatureEntry, Label startScope, Label endScope) {
        return with(LocalVariableType.of(slot, nameEntry, signatureEntry, startScope, endScope));
    }

    /**
     * Declares a local variable type entry.
     * <p>
     * This call may be ignored if {@link ClassFile.DebugElementsOption#DROP_DEBUG}
     * is set, or if any of the argument labels is not {@linkplain #labelBinding
     * bound} and {@link ClassFile.DeadLabelsOption#DROP_DEAD_LABELS} is set.
     *
     * @apiNote
     * When a local variable type entry is declared, a local variable entry with
     * the descriptor derived from erasure (JLS {@jls 4.6}) of the signature
     * should be declared as well.
     *
     * @param slot the local variable slot
     * @param name the variable name
     * @param signature the variable signature
     * @param startScope the start scope of the variable
     * @param endScope the end scope of the variable
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see LocalVariableType
     */
    default CodeBuilder localVariableType(int slot, String name, Signature signature, Label startScope, Label endScope) {
        return localVariableType(slot,
                                 constantPool().utf8Entry(name),
                                 constantPool().utf8Entry(signature.signatureString()),
                                 startScope, endScope);
    }

    // Bytecode conveniences

    /**
     * Generates an instruction pushing the null object {@link TypeKind#REFERENCE
     * reference} onto the operand stack.
     *
     * @return this builder
     * @see Opcode#ACONST_NULL
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder aconst_null() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ACONST_NULL));
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#REFERENCE
     * reference} array.
     *
     * @return this builder
     * @see Opcode#AALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder aaload() {
        return arrayLoad(TypeKind.REFERENCE);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#REFERENCE
     * reference} array.
     *
     * @return this builder
     * @see Opcode#AASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder aastore() {
        return arrayStore(TypeKind.REFERENCE);
    }

    /**
     * Generates an instruction to load a {@link TypeKind#REFERENCE reference}
     * from a local variable.
     * <p>
     * This may also generate {@link Opcode#ALOAD_0 aload_&lt;N&gt;} and {@link
     * Opcode#ALOAD_W wide aload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#ALOAD
     * @see #loadLocal
     * @see LoadInstruction
     */
    default CodeBuilder aload(int slot) {
        return loadLocal(TypeKind.REFERENCE, slot);
    }

    /**
     * Generates an instruction to create a new array of {@link TypeKind#REFERENCE
     * reference}.
     *
     * @param classEntry the component type
     * @return this builder
     * @see Opcode#ANEWARRAY
     * @see NewReferenceArrayInstruction
     */
    default CodeBuilder anewarray(ClassEntry classEntry) {
        return with(NewReferenceArrayInstruction.of(classEntry));
    }

    /**
     * Generates an instruction to create a new array of {@link TypeKind#REFERENCE
     * reference}.
     *
     * @param className the component type
     * @return this builder
     * @throws IllegalArgumentException if {@code className} represents a primitive type
     * @see Opcode#ANEWARRAY
     * @see NewReferenceArrayInstruction
     */
    default CodeBuilder anewarray(ClassDesc className) {
        return anewarray(constantPool().classEntry(className));
    }

    /**
     * Generates an instruction to return a {@link TypeKind#REFERENCE reference}
     * from this method.
     *
     * @return this builder
     * @see Opcode#ARETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder areturn() {
        return return_(TypeKind.REFERENCE);
    }

    /**
     * Generates an instruction to get the length of an array.
     *
     * @return this builder
     * @see Opcode#ARRAYLENGTH
     * @see OperatorInstruction
     */
    default CodeBuilder arraylength() {
        return with(OperatorInstruction.of(Opcode.ARRAYLENGTH));
    }

    /**
     * Generates an instruction to store a {@link TypeKind#REFERENCE reference}
     * into a local variable.  Such an instruction can also store a {@link
     * TypeKind##returnAddress returnAddress}.
     * <p>
     * This may also generate {@link Opcode#ASTORE_0 astore_&lt;N&gt;} and
     * {@link Opcode#ASTORE_W wide astore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#ASTORE
     * @see #storeLocal
     * @see StoreInstruction
     */
    default CodeBuilder astore(int slot) {
        return storeLocal(TypeKind.REFERENCE, slot);
    }

    /**
     * Generates an instruction to throw an exception or error.
     *
     * @return this builder
     * @see Opcode#ATHROW
     * @see ThrowInstruction
     */
    default CodeBuilder athrow() {
        return with(ThrowInstruction.of());
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#BYTE byte} or
     * {@link TypeKind#BOOLEAN boolean} array.
     *
     * @return this builder
     * @see Opcode#BALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder baload() {
        return arrayLoad(TypeKind.BYTE);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#BYTE byte} or
     * {@link TypeKind#BOOLEAN boolean} array.
     *
     * @return this builder
     * @see Opcode#BASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder bastore() {
        return arrayStore(TypeKind.BYTE);
    }

    /**
     * Generates an instruction pushing an {@link TypeKind#INT int} in the range
     * of {@link TypeKind#BYTE byte} ({@code [-128, 127]}) onto the operand
     * stack.
     *
     * @param b the int in the range of byte
     * @return this builder
     * @throws IllegalArgumentException if {@code b} is out of range of byte
     * @see Opcode#BIPUSH
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder bipush(int b) {
        return with(ConstantInstruction.ofArgument(Opcode.BIPUSH, b));
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#CHAR char} array.
     *
     * @return this builder
     * @see Opcode#CALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder caload() {
        return arrayLoad(TypeKind.CHAR);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#CHAR char} array.
     *
     * @return this builder
     * @see Opcode#CASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder castore() {
        return arrayStore(TypeKind.CHAR);
    }

    /**
     * Generates an instruction to check whether an object is of the given type,
     * throwing a {@link ClassCastException} if the check fails.
     *
     * @param type the object type
     * @return this builder
     * @see Opcode#CHECKCAST
     * @see TypeCheckInstruction
     */
    default CodeBuilder checkcast(ClassEntry type) {
        return with(TypeCheckInstruction.of(Opcode.CHECKCAST, type));
    }

    /**
     * Generates an instruction to check whether an object is of the given type,
     * throwing a {@link ClassCastException} if the check fails.
     *
     * @param type the object type
     * @return this builder
     * @throws IllegalArgumentException if {@code type} represents a primitive type
     * @see Opcode#CHECKCAST
     * @see TypeCheckInstruction
     */
    default CodeBuilder checkcast(ClassDesc type) {
        return checkcast(constantPool().classEntry(type));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#DOUBLE double} into
     * a {@link TypeKind#FLOAT float}.
     *
     * @return this builder
     * @see Opcode#D2F
     * @see ConvertInstruction
     */
    default CodeBuilder d2f() {
        return with(ConvertInstruction.of(Opcode.D2F));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#DOUBLE double} into
     * an {@link TypeKind#INT int}.
     *
     * @return this builder
     * @see Opcode#D2I
     * @see ConvertInstruction
     */
    default CodeBuilder d2i() {
        return with(ConvertInstruction.of(Opcode.D2I));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#DOUBLE double} into
     * a {@link TypeKind#LONG long}.
     *
     * @return this builder
     * @see Opcode#D2L
     * @see ConvertInstruction
     */
    default CodeBuilder d2l() {
        return with(ConvertInstruction.of(Opcode.D2L));
    }

    /**
     * Generates an instruction to add two {@link TypeKind#DOUBLE doubles}.
     *
     * @return this builder
     * @see Opcode#DADD
     * @see OperatorInstruction
     */
    default CodeBuilder dadd() {
        return with(OperatorInstruction.of(Opcode.DADD));
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#DOUBLE double}
     * array.
     *
     * @return this builder
     * @see Opcode#DALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder daload() {
        return arrayLoad(TypeKind.DOUBLE);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#DOUBLE double}
     * array.
     *
     * @return this builder
     * @see Opcode#DASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder dastore() {
        return arrayStore(TypeKind.DOUBLE);
    }

    /**
     * Generates an instruction to compare two {@link TypeKind#DOUBLE doubles},
     * producing {@code 1} if any operand is {@link Double#isNaN(double) NaN}.
     *
     * @return this builder
     * @see Opcode#DCMPG
     * @see OperatorInstruction
     */
    default CodeBuilder dcmpg() {
        return with(OperatorInstruction.of(Opcode.DCMPG));
    }

    /**
     * Generates an instruction to compare two {@link TypeKind#DOUBLE doubles},
     * producing {@code -1} if any operand is {@link Double#isNaN(double) NaN}.
     *
     * @return this builder
     * @see Opcode#DCMPL
     * @see OperatorInstruction
     */
    default CodeBuilder dcmpl() {
        return with(OperatorInstruction.of(Opcode.DCMPL));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#DOUBLE double} constant
     * 0 onto the operand stack.
     *
     * @return this builder
     * @see Opcode#DCONST_0
     * @see #loadConstant(double)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder dconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_0));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#DOUBLE double} constant
     * 1 onto the operand stack.
     *
     * @return this builder
     * @see Opcode#DCONST_1
     * @see #loadConstant(double)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder dconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.DCONST_1));
    }

    /**
     * Generates an instruction to divide {@link TypeKind#DOUBLE doubles}.
     *
     * @return this builder
     * @see Opcode#DDIV
     * @see OperatorInstruction
     */
    default CodeBuilder ddiv() {
        return with(OperatorInstruction.of(Opcode.DDIV));
    }

    /**
     * Generates an instruction to load a {@link TypeKind#DOUBLE double} from a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#DLOAD_0 dload_&lt;N&gt;} and {@link
     * Opcode#DLOAD_W wide dload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#DLOAD
     * @see #loadLocal(TypeKind, int)
     * @see LoadInstruction
     */
    default CodeBuilder dload(int slot) {
        return loadLocal(TypeKind.DOUBLE, slot);
    }

    /**
     * Generates an instruction to multiply {@link TypeKind#DOUBLE doubles}.
     *
     * @return this builder
     * @see Opcode#DMUL
     * @see OperatorInstruction
     */
    default CodeBuilder dmul() {
        return with(OperatorInstruction.of(Opcode.DMUL));
    }

    /**
     * Generates an instruction to negate a {@link TypeKind#DOUBLE double}.
     *
     * @return this builder
     * @see Opcode#DNEG
     * @see OperatorInstruction
     */
    default CodeBuilder dneg() {
        return with(OperatorInstruction.of(Opcode.DNEG));
    }

    /**
     * Generates an instruction to calculate {@link TypeKind#DOUBLE double}
     * remainder.
     *
     * @return this builder
     * @see Opcode#DREM
     * @see OperatorInstruction
     */
    default CodeBuilder drem() {
        return with(OperatorInstruction.of(Opcode.DREM));
    }

    /**
     * Generates an instruction to return a {@link TypeKind#DOUBLE double} from
     * this method.
     *
     * @return this builder
     * @see Opcode#DRETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder dreturn() {
        return return_(TypeKind.DOUBLE);
    }

    /**
     * Generates an instruction to store a {@link TypeKind#DOUBLE double} into a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#DSTORE_0 dstore_&lt;N&gt;} and
     * {@link Opcode#DSTORE_W wide dstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#DSTORE
     * @see #storeLocal(TypeKind, int)
     * @see StoreInstruction
     */
    default CodeBuilder dstore(int slot) {
        return storeLocal(TypeKind.DOUBLE, slot);
    }

    /**
     * Generates an instruction to subtract {@link TypeKind#DOUBLE doubles}.
     *
     * @return this builder
     * @see Opcode#DSUB
     * @see OperatorInstruction
     */
    default CodeBuilder dsub() {
        return with(OperatorInstruction.of(Opcode.DSUB));
    }

    /**
     * Generates an instruction to duplicate the top operand stack value.
     *
     * @return this builder
     * @see Opcode#DUP
     * @see StackInstruction
     */
    default CodeBuilder dup() {
        return with(StackInstruction.of(Opcode.DUP));
    }

    /**
     * Generates an instruction to duplicate the top one or two operand stack
     * value.
     *
     * @return this builder
     * @see Opcode#DUP2
     * @see StackInstruction
     */
    default CodeBuilder dup2() {
        return with(StackInstruction.of(Opcode.DUP2));
    }

    /**
     * Generates an instruction to duplicate the top one or two operand stack
     * values and insert two or three values down.
     *
     * @return this builder
     * @see Opcode#DUP2_X1
     * @see StackInstruction
     */
    default CodeBuilder dup2_x1() {
        return with(StackInstruction.of(Opcode.DUP2_X1));
    }

    /**
     * Generates an instruction to duplicate the top one or two operand stack
     * values and insert two, three, or four values down.
     *
     * @return this builder
     * @see Opcode#DUP2_X2
     * @see StackInstruction
     */
    default CodeBuilder dup2_x2() {
        return with(StackInstruction.of(Opcode.DUP2_X2));
    }

    /**
     * Generates an instruction to duplicate the top operand stack value and
     * insert two values down.
     *
     * @return this builder
     * @see Opcode#DUP_X1
     * @see StackInstruction
     */
    default CodeBuilder dup_x1() {
        return with(StackInstruction.of(Opcode.DUP_X1));
    }

    /**
     * Generates an instruction to duplicate the top operand stack value and
     * insert two or three values down.
     *
     * @return this builder
     * @see Opcode#DUP_X2
     * @see StackInstruction
     */
    default CodeBuilder dup_x2() {
        return with(StackInstruction.of(Opcode.DUP_X2));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#FLOAT float} into a
     * {@link TypeKind#DOUBLE double}.
     *
     * @return this builder
     * @see Opcode#F2D
     * @see ConvertInstruction
     */
    default CodeBuilder f2d() {
        return with(ConvertInstruction.of(Opcode.F2D));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#FLOAT float} into
     * an {@link TypeKind#INT int}.
     *
     * @return this builder
     * @see Opcode#F2I
     * @see ConvertInstruction
     */
    default CodeBuilder f2i() {
        return with(ConvertInstruction.of(Opcode.F2I));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#FLOAT float} into a
     * {@link TypeKind#LONG long}.
     *
     * @return this builder
     * @see Opcode#F2L
     * @see ConvertInstruction
     */
    default CodeBuilder f2l() {
        return with(ConvertInstruction.of(Opcode.F2L));
    }

    /**
     * Generates an instruction to add two {@link TypeKind#FLOAT floats}.
     *
     * @return this builder
     * @see Opcode#FADD
     * @see OperatorInstruction
     */
    default CodeBuilder fadd() {
        return with(OperatorInstruction.of(Opcode.FADD));
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#FLOAT float}
     * array.
     *
     * @return this builder
     * @see Opcode#FALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder faload() {
        return arrayLoad(TypeKind.FLOAT);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#FLOAT float}
     * array.
     *
     * @return this builder
     * @see Opcode#FASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder fastore() {
        return arrayStore(TypeKind.FLOAT);
    }

    /**
     * Generates an instruction to compare {@link TypeKind#FLOAT floats},
     * producing {@code 1} if any operand is {@link Float#isNaN(float) NaN}.
     *
     * @return this builder
     * @see Opcode#FCMPG
     * @see OperatorInstruction
     */
    default CodeBuilder fcmpg() {
        return with(OperatorInstruction.of(Opcode.FCMPG));
    }

    /**
     * Generates an instruction to compare {@link TypeKind#FLOAT floats},
     * producing {@code -1} if any operand is {@link Float#isNaN(float) NaN}.
     *
     * @return this builder
     * @see Opcode#FCMPL
     * @see OperatorInstruction
     */
    default CodeBuilder fcmpl() {
        return with(OperatorInstruction.of(Opcode.FCMPL));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#FLOAT float} constant 0
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#FCONST_0
     * @see #loadConstant(float)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder fconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_0));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#FLOAT float} constant 1
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#FCONST_1
     * @see #loadConstant(float)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder fconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_1));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#FLOAT float} constant 2
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#FCONST_2
     * @see #loadConstant(float)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder fconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.FCONST_2));
    }

    /**
     * Generates an instruction to divide {@link TypeKind#FLOAT floats}.
     *
     * @return this builder
     * @see Opcode#FDIV
     * @see OperatorInstruction
     */
    default CodeBuilder fdiv() {
        return with(OperatorInstruction.of(Opcode.FDIV));
    }

    /**
     * Generates an instruction to load a {@link TypeKind#FLOAT float} from a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#FLOAD_0 fload_&lt;N&gt;} and {@link
     * Opcode#FLOAD_W wide fload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#FLOAD
     * @see #loadLocal(TypeKind, int)
     * @see LoadInstruction
     */
    default CodeBuilder fload(int slot) {
        return loadLocal(TypeKind.FLOAT, slot);
    }

    /**
     * Generates an instruction to multiply {@link TypeKind#FLOAT floats}.
     *
     * @return this builder
     * @see Opcode#FMUL
     * @see OperatorInstruction
     */
    default CodeBuilder fmul() {
        return with(OperatorInstruction.of(Opcode.FMUL));
    }

    /**
     * Generates an instruction to negate a {@link TypeKind#FLOAT float}.
     *
     * @return this builder
     * @see Opcode#FNEG
     * @see OperatorInstruction
     */
    default CodeBuilder fneg() {
        return with(OperatorInstruction.of(Opcode.FNEG));
    }

    /**
     * Generates an instruction to calculate {@link TypeKind#FLOAT floats}
     * remainder.
     *
     * @return this builder
     * @see Opcode#FREM
     * @see OperatorInstruction
     */
    default CodeBuilder frem() {
        return with(OperatorInstruction.of(Opcode.FREM));
    }

    /**
     * Generates an instruction to return a {@link TypeKind#FLOAT float} from
     * this method.
     *
     * @return this builder
     * @see Opcode#FRETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder freturn() {
        return return_(TypeKind.FLOAT);
    }

    /**
     * Generates an instruction to store a {@link TypeKind#FLOAT float} into a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#FSTORE_0 fstore_&lt;N&gt;} and
     * {@link Opcode#FSTORE_W wide fstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#FSTORE
     * @see #storeLocal(TypeKind, int)
     * @see StoreInstruction
     */
    default CodeBuilder fstore(int slot) {
        return storeLocal(TypeKind.FLOAT, slot);
    }

    /**
     * Generates an instruction to subtract {@link TypeKind#FLOAT floats}.
     *
     * @return this builder
     * @see Opcode#FSUB
     * @see OperatorInstruction
     */
    default CodeBuilder fsub() {
        return with(OperatorInstruction.of(Opcode.FSUB));
    }

    /**
     * Generates an instruction to fetch field from an object.
     *
     * @param ref the field reference
     * @return this builder
     * @see Opcode#GETFIELD
     * @see #fieldAccess(Opcode, FieldRefEntry)
     * @see FieldInstruction
     */
    default CodeBuilder getfield(FieldRefEntry ref) {
        return fieldAccess(Opcode.GETFIELD, ref);
    }

    /**
     * Generates an instruction to fetch field from an object.
     *
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#GETFIELD
     * @see #fieldAccess(Opcode, ClassDesc, String, ClassDesc)
     * @see FieldInstruction
     */
    default CodeBuilder getfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(Opcode.GETFIELD, owner, name, type);
    }

    /**
     * Generates an instruction to get static field from a class or interface.
     *
     * @param ref the field reference
     * @return this builder
     * @see Opcode#GETSTATIC
     * @see #fieldAccess(Opcode, FieldRefEntry)
     * @see FieldInstruction
     */
    default CodeBuilder getstatic(FieldRefEntry ref) {
        return fieldAccess(Opcode.GETSTATIC, ref);
    }

    /**
     * Generates an instruction to get static field from a class or interface.
     *
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#GETSTATIC
     * @see #fieldAccess(Opcode, ClassDesc, String, ClassDesc)
     * @see FieldInstruction
     */
    default CodeBuilder getstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(Opcode.GETSTATIC, owner, name, type);
    }

    /**
     * Generates an instruction to branch always.
     * <p>
     * This may also generate {@link Opcode#GOTO_W goto_w} instructions if
     * {@link ShortJumpsOption#FIX_SHORT_JUMPS} is set.
     *
     * @apiNote
     * The instruction's name is {@code goto}, which coincides with a reserved
     * keyword of the Java programming language, thus this method is named with
     * an extra {@code _} suffix instead.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#GOTO
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder goto_(Label target) {
        return branch(Opcode.GOTO, target);
    }

    /**
     * Generates an instruction to branch always with wide index.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#GOTO_W
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder goto_w(Label target) {
        return branch(Opcode.GOTO_W, target);
    }

    /**
     * Generates an instruction to truncate an {@link TypeKind#INT int} into the
     * range of {@link TypeKind#BYTE byte} and sign-extend it.
     *
     * @return this builder
     * @see Opcode#I2B
     * @see ConvertInstruction
     */
    default CodeBuilder i2b() {
        return with(ConvertInstruction.of(Opcode.I2B));
    }

    /**
     * Generates an instruction to truncate an {@link TypeKind#INT int} into the
     * range of {@link TypeKind#CHAR char} and zero-extend it.
     *
     * @return this builder
     * @see Opcode#I2C
     * @see ConvertInstruction
     */
    default CodeBuilder i2c() {
        return with(ConvertInstruction.of(Opcode.I2C));
    }

    /**
     * Generates an instruction to convert an {@link TypeKind#INT int} into a
     * {@link TypeKind#DOUBLE double}.
     *
     * @return this builder
     * @see Opcode#I2D
     * @see ConvertInstruction
     */
    default CodeBuilder i2d() {
        return with(ConvertInstruction.of(Opcode.I2D));
    }

    /**
     * Generates an instruction to convert an {@link TypeKind#INT int} into a
     * {@link TypeKind#FLOAT float}.
     *
     * @return this builder
     * @see Opcode#I2F
     * @see ConvertInstruction
     */
    default CodeBuilder i2f() {
        return with(ConvertInstruction.of(Opcode.I2F));
    }

    /**
     * Generates an instruction to convert an {@link TypeKind#INT int} into a
     * {@link TypeKind#LONG long}.
     *
     * @return this builder
     * @see Opcode#I2L
     * @see ConvertInstruction
     */
    default CodeBuilder i2l() {
        return with(ConvertInstruction.of(Opcode.I2L));
    }

    /**
     * Generates an instruction to truncate an {@link TypeKind#INT int} into the
     * range of {@link TypeKind#SHORT short} and sign-extend it.
     *
     * @return this builder
     * @see Opcode#I2S
     * @see ConvertInstruction
     */
    default CodeBuilder i2s() {
        return with(ConvertInstruction.of(Opcode.I2S));
    }

    /**
     * Generates an instruction to add two {@link TypeKind#INT ints}.
     *
     * @return this builder
     * @see Opcode#IADD
     * @see OperatorInstruction
     */
    default CodeBuilder iadd() {
        return with(OperatorInstruction.of(Opcode.IADD));
    }

    /**
     * Generates an instruction to load from an {@link TypeKind#INT int} array.
     *
     * @return this builder
     * @see Opcode#IALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder iaload() {
        return arrayLoad(TypeKind.INT);
    }

    /**
     * Generates an instruction to calculate bitwise AND of {@link TypeKind#INT
     * ints}, also used for {@link TypeKind#BOOLEAN boolean} AND.
     *
     * @return this builder
     * @see Opcode#IAND
     * @see OperatorInstruction
     */
    default CodeBuilder iand() {
        return with(OperatorInstruction.of(Opcode.IAND));
    }

    /**
     * Generates an instruction to store into an {@link TypeKind#INT int} array.
     *
     * @return this builder
     * @see Opcode#IASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder iastore() {
        return arrayStore(TypeKind.INT);
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 0 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_0
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_0));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 1 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_1
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_1));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 2 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_2
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_2() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_2));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 3 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_3
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_3() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_3));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 4 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_4
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_4() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_4));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant 5 onto
     * the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_5
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_5() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_5));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#INT int} constant -1
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#ICONST_M1
     * @see #loadConstant(int)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder iconst_m1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.ICONST_M1));
    }

    /**
     * Generates an instruction to divide {@link TypeKind#INT ints}.
     *
     * @return this builder
     * @see Opcode#IDIV
     * @see OperatorInstruction
     */
    default CodeBuilder idiv() {
        return with(OperatorInstruction.of(Opcode.IDIV));
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#REFERENCE reference}
     * comparison {@code operand1 == operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ACMPEQ
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_acmpeq(Label target) {
        return branch(Opcode.IF_ACMPEQ, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#REFERENCE reference}
     * comparison {@code operand1 != operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ACMPNE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_acmpne(Label target) {
        return branch(Opcode.IF_ACMPNE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 == operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPEQ
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmpeq(Label target) {
        return branch(Opcode.IF_ICMPEQ, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 >= operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPGE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmpge(Label target) {
        return branch(Opcode.IF_ICMPGE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 > operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPGT
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmpgt(Label target) {
        return branch(Opcode.IF_ICMPGT, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 <= operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPLE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmple(Label target) {
        return branch(Opcode.IF_ICMPLE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 < operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPLT
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmplt(Label target) {
        return branch(Opcode.IF_ICMPLT, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * {@code operand1 != operand2} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IF_ICMPNE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder if_icmpne(Label target) {
        return branch(Opcode.IF_ICMPNE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#REFERENCE reference}
     * is not {@code null}.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFNONNULL
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifnonnull(Label target) {
        return branch(Opcode.IFNONNULL, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#REFERENCE reference}
     * is {@code null}.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFNULL
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifnull(Label target) {
        return branch(Opcode.IFNULL, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code == 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFEQ
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifeq(Label target) {
        return branch(Opcode.IFEQ, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code >= 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFGE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifge(Label target) {
        return branch(Opcode.IFGE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code > 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFGT
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifgt(Label target) {
        return branch(Opcode.IFGT, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code <= 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFLE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifle(Label target) {
        return branch(Opcode.IFLE, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code < 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFLT
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder iflt(Label target) {
        return branch(Opcode.IFLT, target);
    }

    /**
     * Generates an instruction to branch if {@link TypeKind#INT int} comparison
     * with zero {@code != 0} succeeds.
     * <p>
     * This may generate multiple instructions to accomplish the same effect if
     * {@link ClassFile.ShortJumpsOption#FIX_SHORT_JUMPS} is set and {@code
     * target} cannot be encoded as a BCI offset in {@code [-32768, 32767]}.
     *
     * @param target the branch target
     * @return this builder
     * @see Opcode#IFNE
     * @see #branch(Opcode, Label)
     * @see BranchInstruction
     */
    default CodeBuilder ifne(Label target) {
        return branch(Opcode.IFNE, target);
    }

    /**
     * Generates an instruction to increment an {@link TypeKind#INT int} local
     * variable by a constant.
     * <p>
     * This may also generate {@link Opcode#IINC_W wide iinc} instructions if
     * {@code slot} exceeds {@code 255} or {@code val} exceeds the range of
     * {@link TypeKind#BYTE byte}.
     *
     * @param slot the local variable slot
     * @param val the increment value
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} or {@code val} is out of range
     * @see Opcode#IINC
     * @see IncrementInstruction
     */
    default CodeBuilder iinc(int slot, int val) {
        return with(IncrementInstruction.of(slot, val));
    }

    /**
     * Generates an instruction to load an {@link TypeKind#INT int} from a local
     * variable.
     * <p>
     * This may also generate {@link Opcode#ILOAD_0 iload_&lt;N&gt;} and {@link
     * Opcode#ILOAD_W wide iload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#ILOAD
     * @see #loadLocal(TypeKind, int)
     * @see LoadInstruction
     */
    default CodeBuilder iload(int slot) {
        return loadLocal(TypeKind.INT, slot);
    }

    /**
     * Generates an instruction to multiply {@link TypeKind#INT ints}.
     *
     * @return this builder
     * @see Opcode#IMUL
     * @see OperatorInstruction
     */
    default CodeBuilder imul() {
        return with(OperatorInstruction.of(Opcode.IMUL));
    }

    /**
     * Generates an instruction to negate an {@link TypeKind#INT int}.
     *
     * @return this builder
     * @see Opcode#INEG
     * @see OperatorInstruction
     */
    default CodeBuilder ineg() {
        return with(OperatorInstruction.of(Opcode.INEG));
    }

    /**
     * Generates an instruction to determine if an object is of the given type,
     * producing a {@link TypeKind#BOOLEAN boolean} result on the operand stack.
     *
     * @apiNote
     * The instruction's name is {@code instanceof}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with camel case instead.
     *
     * @param target the target type
     * @return this builder
     * @see Opcode#INSTANCEOF
     * @see TypeCheckInstruction
     */
    default CodeBuilder instanceOf(ClassEntry target) {
        return with(TypeCheckInstruction.of(Opcode.INSTANCEOF, target));
    }

    /**
     * Generates an instruction to determine if an object is of the given type,
     * producing a {@link TypeKind#BOOLEAN boolean} result on the operand stack.
     *
     * @apiNote
     * The instruction's name is {@code instanceof}, which coincides with a
     * reserved keyword of the Java programming language, thus this method is
     * named with camel case instead.
     *
     * @param target the target type
     * @return this builder
     * @throws IllegalArgumentException if {@code target} represents a primitive type
     * @see Opcode#INSTANCEOF
     * @see TypeCheckInstruction
     */
    default CodeBuilder instanceOf(ClassDesc target) {
        return instanceOf(constantPool().classEntry(target));
    }

    /**
     * Generates an instruction to invoke a dynamically-computed call site.
     *
     * @param ref the dynamic call site
     * @return this builder
     * @see Opcode#INVOKEDYNAMIC
     * @see InvokeDynamicInstruction
     */
    default CodeBuilder invokedynamic(InvokeDynamicEntry ref) {
        return with(InvokeDynamicInstruction.of(ref));
    }

    /**
     * Generates an instruction to invoke a dynamically-computed call site.
     *
     * @param ref the dynamic call site
     * @return this builder
     * @see Opcode#INVOKEDYNAMIC
     * @see InvokeDynamicInstruction
     */
    default CodeBuilder invokedynamic(DynamicCallSiteDesc ref) {
        MethodHandleEntry bsMethod = handleDescToHandleInfo(constantPool(), (DirectMethodHandleDesc) ref.bootstrapMethod());
        var cpArgs = ref.bootstrapArgs();
        List<LoadableConstantEntry> bsArguments = new ArrayList<>(cpArgs.length);
        for (var constantValue : cpArgs) {
            bsArguments.add(constantPool().loadableConstantEntry(requireNonNull(constantValue)));
        }
        BootstrapMethodEntry bm = constantPool().bsmEntry(bsMethod, bsArguments);
        NameAndTypeEntry nameAndType = constantPool().nameAndTypeEntry(ref.invocationName(), ref.invocationType());
        return invokedynamic(constantPool().invokeDynamicEntry(bm, nameAndType));
    }

    /**
     * Generates an instruction to invoke an interface method.
     *
     * @param ref the interface method reference
     * @return this builder
     * @see Opcode#INVOKEINTERFACE
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokeinterface(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKEINTERFACE, ref);
    }

    /**
     * Generates an instruction to invoke an interface method.
     *
     * @param owner the owner interface
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKEINTERFACE
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokeinterface(ClassDesc owner, String name, MethodTypeDesc type) {
        return invoke(Opcode.INVOKEINTERFACE, constantPool().interfaceMethodRefEntry(owner, name, type));
    }

    /**
     * Generates an instruction to invoke an instance method in an interface;
     * direct invocation of methods of the current class and its supertypes.
     *
     * @param ref the interface method reference
     * @return this builder
     * @see Opcode#INVOKESPECIAL
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokespecial(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Generates an instruction to invoke an instance method in a class; direct
     * invocation of instance initialization methods and methods of the current
     * class and its supertypes.
     *
     * @param ref the method reference
     * @return this builder
     * @see Opcode#INVOKESPECIAL
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokespecial(MethodRefEntry ref) {
        return invoke(Opcode.INVOKESPECIAL, ref);
    }

    /**
     * Generates an instruction to invoke an instance method in a class; direct
     * invocation of instance initialization methods and methods of the current
     * class and its supertypes.
     *
     * @param owner the owner class, must not be an interface
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKESPECIAL
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type) {
        return invoke(Opcode.INVOKESPECIAL, owner, name, type, false);
    }

    /**
     * Generates an instruction to invoke an instance method; direct invocation
     * of instance initialization methods and methods of the current class and
     * its supertypes.
     *
     * @param owner the owner class or interface
     * @param name the method name
     * @param type the method type
     * @param isInterface whether the owner is an interface
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKESPECIAL
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokespecial(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invoke(Opcode.INVOKESPECIAL, owner, name, type, isInterface);
    }

    /**
     * Generates an instruction to invoke a class (static) method of an interface.
     *
     * @param ref the interface method reference
     * @return this builder
     * @see Opcode#INVOKESTATIC
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokestatic(InterfaceMethodRefEntry ref) {
        return invoke(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Generates an instruction to invoke a class (static) method of a class.
     *
     * @param ref the method reference
     * @return this builder
     * @see Opcode#INVOKESTATIC
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokestatic(MethodRefEntry ref) {
        return invoke(Opcode.INVOKESTATIC, ref);
    }

    /**
     * Generates an instruction to invoke a class (static) method of a class.
     *
     * @param owner the owner class, must not be an interface
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKESTATIC
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type) {
        return invoke(Opcode.INVOKESTATIC, owner, name, type, false);
    }

    /**
     * Generates an instruction to invoke a class (static) method.
     *
     * @param owner the owner class or interface
     * @param name the method name
     * @param type the method type
     * @param isInterface whether the owner is an interface
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKESTATIC
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokestatic(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) {
        return invoke(Opcode.INVOKESTATIC, owner, name, type, isInterface);
    }

    /**
     * Generates an instruction to invoke an instance method; dispatch based on class.
     *
     * @param ref the method reference
     * @return this builder
     * @see Opcode#INVOKEVIRTUAL
     * @see #invoke(Opcode, MemberRefEntry)
     * @see InvokeInstruction
     */
    default CodeBuilder invokevirtual(MethodRefEntry ref) {
        return invoke(Opcode.INVOKEVIRTUAL, ref);
    }

    /**
     * Generates an instruction to invoke an instance method; dispatch based on class.
     *
     * @param owner the owner class, must not be an interface
     * @param name the method name
     * @param type the method type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#INVOKEVIRTUAL
     * @see #invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean)
     * @see InvokeInstruction
     */
    default CodeBuilder invokevirtual(ClassDesc owner, String name, MethodTypeDesc type) {
        return invoke(Opcode.INVOKEVIRTUAL, owner, name, type, false);
    }

    /**
     * Generates an instruction to calculate bitwise OR of {@link TypeKind#INT
     * ints}, also used for {@link TypeKind#BOOLEAN boolean} OR.
     *
     * @return this builder
     * @see Opcode#IOR
     * @see OperatorInstruction
     */
    default CodeBuilder ior() {
        return with(OperatorInstruction.of(Opcode.IOR));
    }

    /**
     * Generates an instruction to calculate {@link TypeKind#INT ints} remainder.
     *
     * @return this builder
     * @see Opcode#IREM
     * @see OperatorInstruction
     */
    default CodeBuilder irem() {
        return with(OperatorInstruction.of(Opcode.IREM));
    }

    /**
     * Generates an instruction to return an {@link TypeKind#INT int} from this
     * method.
     *
     * @return this builder
     * @see Opcode#IRETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder ireturn() {
        return return_(TypeKind.INT);
    }

    /**
     * Generates an instruction to shift an {@link TypeKind#INT int} left.
     *
     * @return this builder
     * @see Opcode#ISHL
     * @see OperatorInstruction
     */
    default CodeBuilder ishl() {
        return with(OperatorInstruction.of(Opcode.ISHL));
    }

    /**
     * Generates an instruction to shift an {@link TypeKind#INT int} right.
     * This carries the sign bit to the vacated most significant bits, as
     * opposed to {@link #iushr()} that fills vacated most significant bits with
     * {@code 0}.
     *
     * @return this builder
     * @see Opcode#ISHR
     * @see OperatorInstruction
     */
    default CodeBuilder ishr() {
        return with(OperatorInstruction.of(Opcode.ISHR));
    }

    /**
     * Generates an instruction to store an {@link TypeKind#INT int} into a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#ISTORE_0 istore_&lt;N&gt;} and
     * {@link Opcode#ISTORE_W wide istore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#ISTORE
     * @see #storeLocal(TypeKind, int)
     * @see StoreInstruction
     */
    default CodeBuilder istore(int slot) {
        return storeLocal(TypeKind.INT, slot);
    }

    /**
     * Generates an instruction to subtract {@link TypeKind#INT ints}.
     *
     * @return this builder
     * @see Opcode#ISUB
     * @see OperatorInstruction
     */
    default CodeBuilder isub() {
        return with(OperatorInstruction.of(Opcode.ISUB));
    }

    /**
     * Generates an instruction to logical shift an {@link TypeKind#INT int}
     * right.  This fills vacated most significant bits with {@code 0}, as
     * opposed to {@link #ishr()} that carries the sign bit to the vacated most
     * significant bits.
     *
     * @return this builder
     * @see Opcode#IUSHR
     * @see OperatorInstruction
     */
    default CodeBuilder iushr() {
        return with(OperatorInstruction.of(Opcode.IUSHR));
    }

    /**
     * Generates an instruction to calculate bitwise XOR of {@link TypeKind#INT
     * ints}.  This can also be used for {@link TypeKind#BOOLEAN boolean} XOR.
     *
     * @return this builder
     * @see Opcode#IXOR
     * @see OperatorInstruction
     */
    default CodeBuilder ixor() {
        return with(OperatorInstruction.of(Opcode.IXOR));
    }

    /**
     * Generates an instruction to access a jump table by key match and jump.
     *
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     * @see Opcode#LOOKUPSWITCH
     * @see LookupSwitchInstruction
     */
    default CodeBuilder lookupswitch(Label defaultTarget, List<SwitchCase> cases) {
        return with(LookupSwitchInstruction.of(defaultTarget, cases));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#LONG long} into a
     * {@link TypeKind#DOUBLE double}.
     *
     * @return this builder
     * @see Opcode#L2D
     * @see OperatorInstruction
     */
    default CodeBuilder l2d() {
        return with(ConvertInstruction.of(Opcode.L2D));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#LONG long} into a
     * {@link TypeKind#FLOAT float}.
     *
     * @return this builder
     * @see Opcode#L2F
     * @see OperatorInstruction
     */
    default CodeBuilder l2f() {
        return with(ConvertInstruction.of(Opcode.L2F));
    }

    /**
     * Generates an instruction to convert a {@link TypeKind#LONG long} into an
     * {@link TypeKind#INT int}.
     *
     * @return this builder
     * @see Opcode#L2I
     * @see OperatorInstruction
     */
    default CodeBuilder l2i() {
        return with(ConvertInstruction.of(Opcode.L2I));
    }

    /**
     * Generates an instruction to add two {@link TypeKind#LONG longs}.
     *
     * @return this builder
     * @see Opcode#LADD
     * @see OperatorInstruction
     */
    default CodeBuilder ladd() {
        return with(OperatorInstruction.of(Opcode.LADD));
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#LONG long} array.
     *
     * @return this builder
     * @see Opcode#LALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder laload() {
        return arrayLoad(TypeKind.LONG);
    }

    /**
     * Generates an instruction to calculate bitwise AND of {@link TypeKind#LONG
     * longs}.
     *
     * @return this builder
     * @see Opcode#LAND
     * @see OperatorInstruction
     */
    default CodeBuilder land() {
        return with(OperatorInstruction.of(Opcode.LAND));
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#LONG long} array.
     *
     * @return this builder
     * @see Opcode#LASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder lastore() {
        return arrayStore(TypeKind.LONG);
    }

    /**
     * Generates an instruction to compare {@link TypeKind#LONG longs}.
     *
     * @return this builder
     * @see Opcode#LCMP
     * @see OperatorInstruction
     */
    default CodeBuilder lcmp() {
        return with(OperatorInstruction.of(Opcode.LCMP));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#LONG long} constant 0
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#LCONST_0
     * @see #loadConstant(long)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder lconst_0() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_0));
    }

    /**
     * Generates an instruction pushing {@link TypeKind#LONG long} constant 1
     * onto the operand stack.
     *
     * @return this builder
     * @see Opcode#LCONST_1
     * @see #loadConstant(long)
     * @see ConstantInstruction.IntrinsicConstantInstruction
     */
    default CodeBuilder lconst_1() {
        return with(ConstantInstruction.ofIntrinsic(Opcode.LCONST_1));
    }

    /**
     * Generates an instruction pushing an item from the run-time constant pool
     * onto the operand stack.
     * <p>
     * This may also generate {@link Opcode#LDC_W ldc_w} and {@link Opcode#LDC2_W
     * ldc2_w} instructions.
     *
     * @apiNote
     * {@link #loadConstant(ConstantDesc) loadConstant} generates more optimal
     * instructions and should be used for general constants if an {@code ldc}
     * instruction is not strictly required.
     *
     * @param value the constant value
     * @return this builder
     * @see Opcode#LDC
     * @see #loadConstant(ConstantDesc)
     * @see ConstantInstruction.LoadConstantInstruction
     */
    default CodeBuilder ldc(ConstantDesc value) {
        return ldc(constantPool().loadableConstantEntry(requireNonNull(value)));
    }

    /**
     * Generates an instruction pushing an item from the run-time constant pool
     * onto the operand stack.
     * <p>
     * This may also generate {@link Opcode#LDC_W ldc_w} and {@link Opcode#LDC2_W
     * ldc2_w} instructions.
     *
     * @param entry the constant value
     * @return this builder
     * @see Opcode#LDC
     * @see #loadConstant(ConstantDesc)
     * @see ConstantInstruction.LoadConstantInstruction
     */
    default CodeBuilder ldc(LoadableConstantEntry entry) {
        return with(ConstantInstruction.ofLoad(BytecodeHelpers.ldcOpcode(entry), entry));
    }

    /**
     * Generates an instruction to divide {@link TypeKind#LONG longs}.
     *
     * @return this builder
     * @see Opcode#LDIV
     * @see OperatorInstruction
     */
    default CodeBuilder ldiv() {
        return with(OperatorInstruction.of(Opcode.LDIV));
    }

    /**
     * Generates an instruction to load a {@link TypeKind#LONG long} from a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#LLOAD_0 lload_&lt;N&gt;} and {@link
     * Opcode#LLOAD_W wide lload} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#LLOAD
     * @see #loadLocal(TypeKind, int)
     * @see LoadInstruction
     */
    default CodeBuilder lload(int slot) {
        return loadLocal(TypeKind.LONG, slot);
    }

    /**
     * Generates an instruction to multiply {@link TypeKind#LONG longs}.
     *
     * @return this builder
     * @see Opcode#LMUL
     * @see OperatorInstruction
     */
    default CodeBuilder lmul() {
        return with(OperatorInstruction.of(Opcode.LMUL));
    }

    /**
     * Generates an instruction to negate a {@link TypeKind#LONG long}.
     *
     * @return this builder
     * @see Opcode#LNEG
     * @see OperatorInstruction
     */
    default CodeBuilder lneg() {
        return with(OperatorInstruction.of(Opcode.LNEG));
    }

    /**
     * Generates an instruction to calculate bitwise OR of {@link TypeKind#LONG
     * longs}.
     *
     * @return this builder
     * @see Opcode#LOR
     * @see OperatorInstruction
     */
    default CodeBuilder lor() {
        return with(OperatorInstruction.of(Opcode.LOR));
    }

    /**
     * Generates an instruction to calculate {@link TypeKind#LONG longs}
     * remainder.
     *
     * @return this builder
     * @see Opcode#LREM
     * @see OperatorInstruction
     */
    default CodeBuilder lrem() {
        return with(OperatorInstruction.of(Opcode.LREM));
    }

    /**
     * Generates an instruction to return a {@link TypeKind#LONG long} from this
     * method.
     *
     * @return this builder
     * @see Opcode#LRETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder lreturn() {
        return return_(TypeKind.LONG);
    }

    /**
     * Generates an instruction to shift a {@link TypeKind#LONG long} left.
     *
     * @return this builder
     * @see Opcode#LSHL
     * @see OperatorInstruction
     */
    default CodeBuilder lshl() {
        return with(OperatorInstruction.of(Opcode.LSHL));
    }

    /**
     * Generates an instruction to shift a {@link TypeKind#LONG long} right.
     * This carries the sign bit to the vacated most significant bits, as
     * opposed to {@link #lushr()} that fills vacated most significant bits with
     * {@code 0}.
     *
     * @return this builder
     * @see Opcode#LSHR
     * @see OperatorInstruction
     */
    default CodeBuilder lshr() {
        return with(OperatorInstruction.of(Opcode.LSHR));
    }

    /**
     * Generates an instruction to store a {@link TypeKind#LONG long} into a
     * local variable.
     * <p>
     * This may also generate {@link Opcode#LSTORE_0 lstore_&lt;N&gt;} and
     * {@link Opcode#LSTORE_W wide lstore} instructions.
     *
     * @param slot the local variable slot
     * @return this builder
     * @throws IllegalArgumentException if {@code slot} is out of range
     * @see Opcode#LSTORE
     * @see #storeLocal(TypeKind, int)
     * @see StoreInstruction
     */
    default CodeBuilder lstore(int slot) {
        return storeLocal(TypeKind.LONG, slot);
    }

    /**
     * Generates an instruction to subtract {@link TypeKind#LONG longs}.
     *
     * @return this builder
     * @see Opcode#LSUB
     * @see OperatorInstruction
     */
    default CodeBuilder lsub() {
        return with(OperatorInstruction.of(Opcode.LSUB));
    }

    /**
     * Generates an instruction to logical shift a {@link TypeKind#LONG long}
     * right.  This fills vacated most significant bits with {@code 0}, as
     * opposed to {@link #lshr()} that carries the sign bit to the vacated most
     * significant bits.
     *
     * @return this builder
     * @see Opcode#LUSHR
     * @see OperatorInstruction
     */
    default CodeBuilder lushr() {
        return with(OperatorInstruction.of(Opcode.LUSHR));
    }

    /**
     * Generates an instruction to calculate bitwise XOR of {@link TypeKind#LONG
     * longs}.
     *
     * @return this builder
     * @see Opcode#LXOR
     * @see OperatorInstruction
     */
    default CodeBuilder lxor() {
        return with(OperatorInstruction.of(Opcode.LXOR));
    }

    /**
     * Generates an instruction to enter monitor for an object.
     *
     * @return this builder
     * @see Opcode#MONITORENTER
     * @see MonitorInstruction
     */
    default CodeBuilder monitorenter() {
        return with(MonitorInstruction.of(Opcode.MONITORENTER));
    }

    /**
     * Generates an instruction to exit monitor for an object.
     *
     * @return this builder
     * @see Opcode#MONITOREXIT
     * @see MonitorInstruction
     */
    default CodeBuilder monitorexit() {
        return with(MonitorInstruction.of(Opcode.MONITOREXIT));
    }

    /**
     * Generates an instruction to create a new multidimensional array.
     *
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     * @throws IllegalArgumentException if {@code dims} is out of range
     * @see Opcode#MULTIANEWARRAY
     * @see NewMultiArrayInstruction
     */
    default CodeBuilder multianewarray(ClassEntry array, int dims) {
        return with(NewMultiArrayInstruction.of(array, dims));
    }

    /**
     * Generates an instruction to create a new multidimensional array.
     *
     * @param array the array type
     * @param dims the number of dimensions
     * @return this builder
     * @throws IllegalArgumentException if {@code array} represents a primitive type
     *         or if {@code dims} is out of range
     * @see Opcode#MULTIANEWARRAY
     * @see NewMultiArrayInstruction
     */
    default CodeBuilder multianewarray(ClassDesc array, int dims) {
        return multianewarray(constantPool().classEntry(array), dims);
    }

    /**
     * Generates an instruction to create a new object.
     *
     * @apiNote
     * The instruction's name is {@code new}, which coincides with a reserved
     * keyword of the Java programming language, thus this method is named with
     * an extra {@code _} suffix instead.
     *
     * @param clazz the new class type
     * @return this builder
     * @see Opcode#NEW
     * @see NewObjectInstruction
     */
    default CodeBuilder new_(ClassEntry clazz) {
        return with(NewObjectInstruction.of(clazz));
    }

    /**
     * Generates an instruction to create a new object.
     *
     * @apiNote
     * The instruction's name is {@code new}, which coincides with a reserved
     * keyword of the Java programming language, thus this method is named with
     * an extra {@code _} suffix instead.
     *
     * @param clazz the new class type
     * @return this builder
     * @throws IllegalArgumentException if {@code clazz} represents a primitive type
     * @see Opcode#NEW
     * @see NewObjectInstruction
     */
    default CodeBuilder new_(ClassDesc clazz) {
        return new_(constantPool().classEntry(clazz));
    }

    /**
     * Generates an instruction to create a new array of a primitive type.
     *
     * @param typeKind the primitive array type
     * @return this builder
     * @throws IllegalArgumentException when the {@code typeKind} is not a legal
     *         primitive array component type
     * @see Opcode#NEWARRAY
     * @see NewPrimitiveArrayInstruction
     */
    default CodeBuilder newarray(TypeKind typeKind) {
        return with(NewPrimitiveArrayInstruction.of(typeKind));
    }

    /**
     * Generates an instruction to pop the top operand stack value.
     *
     * @return this builder
     * @see Opcode#POP
     * @see StackInstruction
     */
    default CodeBuilder pop() {
        return with(StackInstruction.of(Opcode.POP));
    }

    /**
     * Generates an instruction to pop the top one or two operand stack values.
     *
     * @return this builder
     * @see Opcode#POP2
     * @see StackInstruction
     */
    default CodeBuilder pop2() {
        return with(StackInstruction.of(Opcode.POP2));
    }

    /**
     * Generates an instruction to set field in an object.
     *
     * @param ref the field reference
     * @return this builder
     * @see Opcode#PUTFIELD
     * @see #fieldAccess(Opcode, FieldRefEntry)
     * @see FieldInstruction
     */
    default CodeBuilder putfield(FieldRefEntry ref) {
        return fieldAccess(Opcode.PUTFIELD, ref);
    }

    /**
     * Generates an instruction to set field in an object.
     *
     * @param owner the owner class
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#PUTFIELD
     * @see #fieldAccess(Opcode, ClassDesc, String, ClassDesc)
     * @see FieldInstruction
     */
    default CodeBuilder putfield(ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(Opcode.PUTFIELD, owner, name, type);
    }

    /**
     * Generates an instruction to set static field in a class.
     *
     * @param ref the field reference
     * @return this builder
     * @see Opcode#PUTSTATIC
     * @see #fieldAccess(Opcode, FieldRefEntry)
     * @see FieldInstruction
     */
    default CodeBuilder putstatic(FieldRefEntry ref) {
        return fieldAccess(Opcode.PUTSTATIC, ref);
    }

    /**
     * Generates an instruction to set static field in a class.
     *
     * @param owner the owner class or interface
     * @param name the field name
     * @param type the field type
     * @return this builder
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see Opcode#PUTSTATIC
     * @see #fieldAccess(Opcode, ClassDesc, String, ClassDesc)
     * @see FieldInstruction
     */
    default CodeBuilder putstatic(ClassDesc owner, String name, ClassDesc type) {
        return fieldAccess(Opcode.PUTSTATIC, owner, name, type);
    }

    /**
     * Generates an instruction to return {@link TypeKind#VOID void} from this
     * method.
     *
     * @apiNote
     * The instruction's name is {@code return}, which coincides with a reserved
     * keyword of the Java programming language, thus this method is named with
     * an extra {@code _} suffix instead.
     *
     * @return this builder
     * @see Opcode#RETURN
     * @see #return_(TypeKind)
     * @see ReturnInstruction
     */
    default CodeBuilder return_() {
        return return_(TypeKind.VOID);
    }

    /**
     * Generates an instruction to load from a {@link TypeKind#SHORT short}
     * array.
     *
     * @return this builder
     * @see Opcode#SALOAD
     * @see #arrayLoad(TypeKind)
     * @see ArrayLoadInstruction
     */
    default CodeBuilder saload() {
        return arrayLoad(TypeKind.SHORT);
    }

    /**
     * Generates an instruction to store into a {@link TypeKind#SHORT short}
     * array.
     *
     * @return this builder
     * @see Opcode#SASTORE
     * @see #arrayStore(TypeKind)
     * @see ArrayStoreInstruction
     */
    default CodeBuilder sastore() {
        return arrayStore(TypeKind.SHORT);
    }

    /**
     * Generates an instruction pushing an {@link TypeKind#INT int} in the range
     * of {@link TypeKind#SHORT short}, {@code [-32768, 32767]}, onto the
     * operand stack.
     *
     * @param s the int in the range of short
     * @return this builder
     * @throws IllegalArgumentException if {@code s} is out of range of short
     * @see Opcode#SIPUSH
     * @see #loadConstant(int)
     * @see ConstantInstruction.ArgumentConstantInstruction
     */
    default CodeBuilder sipush(int s) {
        return with(ConstantInstruction.ofArgument(Opcode.SIPUSH, s));
    }

    /**
     * Generates an instruction to swap the top two operand stack values.
     *
     * @return this builder
     * @see Opcode#SWAP
     * @see StackInstruction
     */
    default CodeBuilder swap() {
        return with(StackInstruction.of(Opcode.SWAP));
    }

    /**
     * Generates an instruction to access a jump table by index and jump.
     *
     * @param low the minimum key, inclusive
     * @param high the maximum key, inclusive
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     * @see Opcode#TABLESWITCH
     * @see TableSwitchInstruction
     */
    default CodeBuilder tableswitch(int low, int high, Label defaultTarget, List<SwitchCase> cases) {
        return with(TableSwitchInstruction.of(low, high, defaultTarget, cases));
    }

    /**
     * Generates an instruction to access a jump table by index and jump.
     * Computes the minimum and maximum keys from the {@code cases}.
     *
     * @param defaultTarget the default jump target
     * @param cases the switch cases
     * @return this builder
     * @see Opcode#TABLESWITCH
     * @see #tableswitch(int, int, Label, List)
     * @see TableSwitchInstruction
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
