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

import java.io.IOException;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTypeInfo;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.classfile.impl.ClassFileImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;

import static java.util.Objects.requireNonNull;
import static jdk.internal.constant.ConstantUtils.CD_module_info;

/**
 * Provides ability to parse, transform, and generate {@code class} files.
 * A {@code ClassFile} is a context with a set of options that condition how
 * parsing and generation are done.
 *
 * @since 24
 */
public sealed interface ClassFile
        permits ClassFileImpl {

    /**
     * {@return a context with default options}  Each subtype of {@link Option}
     * specifies its default.
     * <p>
     * The default {@link AttributeMapperOption} and {@link
     * ClassHierarchyResolverOption} may be unsuitable for some {@code class}
     * files and result in parsing or generation errors.
     */
    static ClassFile of() {
        return ClassFileImpl.DEFAULT_CONTEXT;
    }

    /**
     * {@return a context with options altered from the default}  Equivalent to
     * {@link #of() ClassFile.of().withOptions(options)}.
     * @param options the desired processing options
     */
    static ClassFile of(Option... options) {
        return of().withOptions(options);
    }

    /**
     * {@return a context with altered options from this context}
     * @param options the desired processing options
     */
    ClassFile withOptions(Option... options);

    /**
     * An option that affects the parsing or writing of {@code class} files.
     *
     * @see java.lang.classfile##options Options
     * @sealedGraph
     * @since 24
     */
    sealed interface Option {
    }

    /**
     * The option describing user-defined attributes for parsing {@code class}
     * files.  The default does not recognize any user-defined attribute.
     * <p>
     * An {@code AttributeMapperOption} contains a function that maps an
     * attribute name to a user attribute mapper. The function may return {@code
     * null} if it does not recognize an attribute name.  The returned mapper
     * must ensure its {@link AttributeMapper#name() name()} is equivalent to
     * the {@link Utf8Entry#stringValue() stringValue()} of the input {@link
     * Utf8Entry}.
     * <p>
     * The mapping function in this attribute has lower priority than mappers in
     * {@link Attributes}, so it is impossible to override built-in attributes
     * with this option.  If an attribute is not recognized by any mapper in
     * {@link Attributes} and is not assigned a mapper, or recognized, by this
     * option, that attribute will be modeled by an {@link UnknownAttribute}.
     *
     * @see AttributeMapper
     * @see CustomAttribute
     * @since 24
     */
    sealed interface AttributeMapperOption extends Option
            permits ClassFileImpl.AttributeMapperOptionImpl {

        /**
         * {@return an option describing user-defined attributes for parsing}
         *
         * @param attributeMapper a function mapping attribute names to attribute mappers
         */
        static AttributeMapperOption of(Function<Utf8Entry, AttributeMapper<?>> attributeMapper) {
            requireNonNull(attributeMapper);
            return new ClassFileImpl.AttributeMapperOptionImpl(attributeMapper);
        }

        /**
         * {@return the function mapping attribute names to attribute mappers}
         */
        Function<Utf8Entry, AttributeMapper<?>> attributeMapper();
    }

    /**
     * The option describing the class hierarchy resolver to use when generating
     * stack maps or verifying classes.  The default is {@link
     * ClassHierarchyResolver#defaultResolver()}, which uses core reflection to
     * find a class with a given name in {@linkplain ClassLoader#getSystemClassLoader()
     * system class loader} and inspect it, and is insufficient if a class is
     * not present in the system class loader as in applications, or if loading
     * of system classes is not desired as in agents.
     * <p>
     * A {@code ClassHierarchyResolverOption} contains a {@link ClassHierarchyResolver}.
     * The resolver must be able to process all classes and interfaces, including
     * those appearing as the component types of array types, that appear in the
     * operand stack of the generated bytecode.  If the resolver fails on any
     * of the classes and interfaces with an {@link IllegalArgumentException},
     * the {@code class} file generation fails.
     *
     * @see ClassHierarchyResolver
     * @jvms 4.10.1.2 Verification Type System
     * @since 24
     */
    sealed interface ClassHierarchyResolverOption extends Option
            permits ClassFileImpl.ClassHierarchyResolverOptionImpl {

        /**
         * {@return an option describing the class hierarchy resolver to use}
         *
         * @param classHierarchyResolver the resolver
         */
        static ClassHierarchyResolverOption of(ClassHierarchyResolver classHierarchyResolver) {
            requireNonNull(classHierarchyResolver);
            return new ClassFileImpl.ClassHierarchyResolverOptionImpl(classHierarchyResolver);
        }

        /**
         * {@return the class hierarchy resolver}
         */
        ClassHierarchyResolver classHierarchyResolver();
    }

    /**
     * Option describing whether to extend from the original constant pool when
     * transforming a {@code class} file.  The default is {@link #SHARED_POOL}
     * to extend from the original constant pool.
     * <p>
     * This option affects all overloads of {@link #transformClass transformClass}.
     * Extending from the original constant pool keeps the indices into the
     * constant pool intact, which enables significant optimizations in processing
     * time and minimizes differences between the original and transformed {@code
     * class} files, but may result in a bigger transformed {@code class} file
     * when many elements of the original {@code class} file are dropped and
     * many original constant pool entries become unused.
     * <p>
     * An alternative to this option is to use {@link #build(ClassEntry,
     * ConstantPoolBuilder, Consumer)} directly.  It allows extension from
     * arbitrary constant pools, and may be useful if a built {@code class} file
     * reuses structures from multiple original {@code class} files.
     *
     * @see ConstantPoolBuilder
     * @see #build(ClassEntry, ConstantPoolBuilder, Consumer)
     * @see #transformClass(ClassModel, ClassTransform)
     * @since 24
     */
    enum ConstantPoolSharingOption implements Option {

        /**
         * Extend the new constant pool from the original constant pool when
         * transforming the {@code class} file.
         * <p>
         * These two transformations below are equivalent:
         * {@snippet lang=java :
         * ClassModel originalClass = null; // @replace substring=null; replacement=...
         * ClassDesc resultClassName = null; // @replace substring=null; replacement=...
         * ClassTransform classTransform = null; // @replace substring=null; replacement=...
         * var resultOne = ClassFile.of(ConstantPoolSharingOption.SHARED_POOL)
         *         .transformClass(originalClass, resultClassName, classTransform);
         * var resultTwo = ClassFile.of().build(resultClassName, ConstantPoolBuilder.of(originalClass),
         *         clb -> clb.transform(originalClass, classTransform));
         * }
         *
         * @see ConstantPoolBuilder#of(ClassModel) ConstantPoolBuilder::of(ClassModel)
         */
        SHARED_POOL,

        /**
         * Creates a new constant pool when transforming the {@code class} file.
         * <p>
         * These two transformations below are equivalent:
         * {@snippet lang=java :
         * ClassModel originalClass = null; // @replace substring=null; replacement=...
         * ClassDesc resultClassName = null; // @replace substring=null; replacement=...
         * ClassTransform classTransform = null; // @replace substring=null; replacement=...
         * var resultOne = ClassFile.of(ConstantPoolSharingOption.NEW_POOL)
         *         .transformClass(originalClass, resultClassName, classTransform);
         * var resultTwo = ClassFile.of().build(resultClassName, ConstantPoolBuilder.of(),
         *         clb -> clb.transform(originalClass, classTransform));
         * }
         *
         * @see ConstantPoolBuilder#of() ConstantPoolBuilder::of()
         */
        NEW_POOL
    }

    /**
     * The option describing whether to patch out unreachable code for stack map
     * generation.  The default is {@link #PATCH_DEAD_CODE} to automatically
     * patch unreachable code and generate a valid stack map entry for the
     * patched code.
     * <p>
     * The stack map generation process may fail when it encounters unreachable
     * code and {@link #KEEP_DEAD_CODE} is set.  In such cases, users should
     * set {@link StackMapsOption#DROP_STACK_MAPS} and provide their own stack
     * maps that passes verification (JVMS {@jvms 4.10.1}).
     *
     * @see StackMapsOption
     * @jvms 4.10.1 Verification by Type Checking
     * @since 24
     */
    enum DeadCodeOption implements Option {

        /**
         * Patch unreachable code with dummy code, and generate valid dummy
         * stack map entries.  This ensures the generated code can pass
         * verification (JVMS {@jvms 4.10.1}).
         */
        PATCH_DEAD_CODE,

        /**
         * Keep the unreachable code for the accuracy of the generated {@code
         * class} file.  Users should set {@link StackMapsOption#DROP_STACK_MAPS}
         * to prevent stack map generation from running and provide their own
         * {@link StackMapTableAttribute} to a {@link CodeBuilder}.
         */
        KEEP_DEAD_CODE
    }

    /**
     * The option describing whether to filter {@linkplain
     * CodeBuilder#labelBinding(Label) unbound labels} and drop their
     * enclosing structures if possible.  The default is {@link
     * #FAIL_ON_DEAD_LABELS} to fail fast with an {@link IllegalArgumentException}
     * when a {@link PseudoInstruction} refers to an unbound label during
     * bytecode generation.
     * <p>
     * The affected {@link PseudoInstruction}s include {@link ExceptionCatch},
     * {@link LocalVariable}, {@link LocalVariableType}, and {@link
     * CharacterRange}.  Setting this option to {@link #DROP_DEAD_LABELS}
     * filters these pseudo-instructions from a {@link CodeBuilder} instead.
     * Note that instructions, such as {@link BranchInstruction}, with unbound
     * labels always fail-fast with an {@link IllegalArgumentException}.
     *
     * @see DebugElementsOption
     * @since 24
     */
    enum DeadLabelsOption implements Option {

        /**
         * Fail fast on {@linkplain CodeBuilder#labelBinding(Label) unbound
         * labels}.  This also ensures the accuracy of the generated {@code
         * class} files.
         */
        FAIL_ON_DEAD_LABELS,

        /**
         * Filter {@link PseudoInstruction}s with {@linkplain
         * CodeBuilder#labelBinding(Label) unbound labels}.  Note that
         * instructions with unbound labels still cause an {@link
         * IllegalArgumentException}.
         */
        DROP_DEAD_LABELS
    }

    /**
     * The option describing whether to process or discard debug {@link
     * PseudoInstruction}s in the traversal of a {@link CodeModel} or a {@link
     * CodeBuilder}.  The default is {@link #PASS_DEBUG} to process debug
     * pseudo-instructions as all other {@link CodeElement}.
     * <p>
     * Debug pseudo-instructions include {@link LocalVariable}, {@link
     * LocalVariableType}, and {@link CharacterRange}.  Discarding debug
     * elements may reduce the overhead of parsing or transforming {@code class}
     * files and has no impact on the run-time behavior.
     *
     * @see LineNumbersOption
     * @since 24
     */
    enum DebugElementsOption implements Option {

        /**
         * Process debug pseudo-instructions like other member elements of a
         * {@link CodeModel}.
         */
        PASS_DEBUG,

        /**
         * Drop debug pseudo-instructions from traversal and builders.
         */
        DROP_DEBUG
    }

    /**
     * The option describing whether to process or discard {@link LineNumber}s
     * in the traversal of a {@link CodeModel} or a {@link CodeBuilder}.  The
     * default is {@link #PASS_LINE_NUMBERS} to process all line number entries
     * as all other {@link CodeElement}.
     * <p>
     * Discarding line numbers may reduce the overhead of parsing or transforming
     * {@code class} files and has no impact on the run-time behavior.
     *
     * @see DebugElementsOption
     * @since 24
     */
    enum LineNumbersOption implements Option {

        /**
         * Process {@link LineNumber} like other member elements of a {@link
         * CodeModel}.
         */
        PASS_LINE_NUMBERS,

        /**
         * Drop {@link LineNumber} from traversal and builders.
         */
        DROP_LINE_NUMBERS;
    }

    /**
     * The option describing whether to automatically rewrite short jumps to
     * equivalent instructions when necessary.  The default is {@link
     * #FIX_SHORT_JUMPS} to automatically rewrite.
     * <p>
     * Due to physical restrictions, some types of instructions cannot encode
     * certain jump targets with bci offsets less than -32768 or greater than
     * 32767, as they use a {@code s2} to encode such an offset.  (The maximum
     * length of the {@code code} array is 65535.)  These types of instructions
     * are called "short jumps".
     * <p>
     * Disabling rewrite can ensure the physical accuracy of a generated {@code
     * class} file and avoid the overhead from a failed first attempt for
     * overflowing forward jumps in some cases, if the generated {@code class}
     * file is stable.
     *
     * @see BranchInstruction
     * @see DiscontinuedInstruction.JsrInstruction
     * @since 24
     */
    enum ShortJumpsOption implements Option {

        /**
         * Automatically convert short jumps to long when necessary.
         * <p>
         * For an invalid instruction model, a {@link CodeBuilder} may generate
         * another or a few other instructions to accomplish the same effect.
         */
        FIX_SHORT_JUMPS,

        /**
         * Fail with an {@link IllegalArgumentException} if short jump overflows.
         * <p>
         * This is useful to ensure the physical accuracy of a generated {@code
         * class} file and avoids the overhead from a failed first attempt for
         * overflowing forward jumps in some cases.
         */
        FAIL_ON_SHORT_JUMPS
    }

    /**
     * The option describing whether to generate stack maps.  The default is
     * {@link #STACK_MAPS_WHEN_REQUIRED} to generate stack maps or reuse
     * existing ones if compatible.
     * <p>
     * The {@link StackMapTableAttribute} is a derived property from a {@link
     * CodeAttribute Code} attribute to allow a Java Virtual Machine to perform
     * verification in one pass.  Thus, it is not modeled as part of a {@link
     * CodeModel}, but computed on-demand instead via stack maps generation.
     * <p>
     * Stack map generation may fail with an {@link IllegalArgumentException} if
     * there is {@linkplain DeadCodeOption unreachable code} or legacy
     * {@linkplain DiscontinuedInstruction.JsrInstruction jump routine}
     * instructions.  When {@link #DROP_STACK_MAPS} option is used, users can
     * provide their own stack maps by supplying a {@link StackMapTableAttribute}
     * to a {@link CodeBuilder}.
     *
     * @see StackMapTableAttribute
     * @see DeadCodeOption
     * @jvms 4.10.1 Verification by Type Checking
     * @since 24
     */
    enum StackMapsOption implements Option {

        /**
         * Generate stack maps or reuse existing ones if compatible.  Stack maps
         * are present on major versions {@value #JAVA_6_VERSION} or above.  For
         * these versions, {@link CodeBuilder} tries to reuse compatible stack
         * maps information if the code array and exception handlers are still
         * compatible after a transformation; otherwise, it runs stack map
         * generation.  However, it does not fail fast if the major version is
         * {@value #JAVA_6_VERSION}, which allows jump subroutine instructions
         * that are incompatible with stack maps to exist in the {@code code}
         * array.
         */
        STACK_MAPS_WHEN_REQUIRED,

        /**
         * Forces running stack map generation.  This runs stack map generation
         * unconditionally and fails fast if the generation fails due to any
         * reason.
         */
        GENERATE_STACK_MAPS,

        /**
         * Do not run stack map generation.  Users must supply their own
         * {@link StackMapTableAttribute} to a {@link CodeBuilder} if the code
         * has branches or exception handlers; otherwise, the generated code
         * will fail verification (JVMS {@jvms 4.10.1}).
         * <p>
         * This option is required for user-supplied {@link StackMapTableAttribute}
         * to be respected.  Stack maps on an existing {@link CodeAttribute Code}
         * attribute can be reused as below with this option:
         * {@snippet lang=java file="PackageSnippets.java" region="manual-reuse-stack-maps"}
         */
        DROP_STACK_MAPS
    }

    /**
     * The option describing whether to retain or discard attributes that cannot
     * verify their correctness after a transformation.  The default is {@link
     * #PASS_ALL_ATTRIBUTES} to retain all attributes as-is.
     * <p>
     * Many attributes only depend on data managed by the Class-File API, such
     * as constant pool entries or labels into the {@code code} array.  If they
     * change, the Class-File API knows their updated values and can write a
     * correct version by expanding the structures and recomputing the updated
     * indexes, known as "explosion".  However, some attributes, such as type
     * annotations, depend on arbitrary data that may be modified during
     * transformations but the Class-File API does not track, such as index to
     * an entry in the {@linkplain ClassModel#interfaces() interfaces} of a
     * {@code ClassFile} structure.  As a result, the Class-File API cannot
     * verify the correctness of such information.
     *
     * @see AttributeStability
     * @since 24
     */
    enum AttributesProcessingOption implements Option {

        /**
         * Retain all original attributes during transformation.
         */
        PASS_ALL_ATTRIBUTES,

        /**
         * Drop attributes with {@link AttributeStability#UNKNOWN} data
         * dependency during transformation.
         */
        DROP_UNKNOWN_ATTRIBUTES,

        /**
         * Drop attributes with {@link AttributeStability#UNSTABLE} or higher
         * data dependency during transformation.
         */
        DROP_UNSTABLE_ATTRIBUTES
    }

    /**
     * Parses a {@code class} file into a {@link ClassModel}.
     * <p>
     * Due to the on-demand nature of {@code class} file parsing, an {@link
     * IllegalArgumentException} may be thrown on any accessor method invocation
     * on the returned model or any structure returned by the accessors in the
     * structure hierarchy.
     *
     * @param bytes the bytes of the {@code class} file
     * @return the class model
     * @throws IllegalArgumentException if the {@code class} file is malformed
     *         or of a version {@linkplain #latestMajorVersion() not supported}
     *         by the current runtime
     */
    ClassModel parse(byte[] bytes);

    /**
     * Parses a {@code class} into a {@link ClassModel}.
     * <p>
     * Due to the on-demand nature of {@code class} file parsing, an {@link
     * IllegalArgumentException} may be thrown on any accessor method invocation
     * on the returned model or any structure returned by the accessors in the
     * structure hierarchy.
     *
     * @param path the path to the {@code class} file
     * @return the class model
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if the {@code class} file is malformed
     *         or of a version {@linkplain #latestMajorVersion() not supported}
     *         by the current runtime
     * @see #parse(byte[])
     */
    default ClassModel parse(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    /**
     * Builds a {@code class} file into a byte array.
     *
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the {@code class} file bytes
     * @throws IllegalArgumentException if {@code thisClass} represents a
     *         primitive type or building encounters a failure
     */
    default byte[] build(ClassDesc thisClass,
                         Consumer<? super ClassBuilder> handler) {
        ConstantPoolBuilder pool = ConstantPoolBuilder.of();
        return build(pool.classEntry(thisClass), pool, handler);
    }

    /**
     * Builds a {@code class} file into a byte array using the provided constant
     * pool builder.
     *
     * @param thisClassEntry the name of the class to build
     * @param constantPool the constant pool builder
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the {@code class} file bytes
     * @throws IllegalArgumentException if building encounters a failure
     */
    byte[] build(ClassEntry thisClassEntry,
                 ConstantPoolBuilder constantPool,
                 Consumer<? super ClassBuilder> handler);

    /**
     * Builds a {@code class} file into a file in a file system.
     *
     * @param path the path to the file to write
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if building encounters a failure
     */
    default void buildTo(Path path,
                         ClassDesc thisClass,
                         Consumer<ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClass, handler));
    }

    /**
     * Builds a {@code class} file into a file in a file system using the
     * provided constant pool builder.
     *
     * @param path the path to the file to write
     * @param thisClassEntry the name of the class to build
     * @param constantPool the constant pool builder
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if building encounters a failure
     */
    default void buildTo(Path path,
                         ClassEntry thisClassEntry,
                         ConstantPoolBuilder constantPool,
                         Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClassEntry, constantPool, handler));
    }

    /**
     * Builds a module descriptor into a byte array.
     *
     * @param moduleAttribute the {@code Module} attribute
     * @return the {@code class} file bytes
     * @throws IllegalArgumentException if building encounters a failure
     */
    default byte[] buildModule(ModuleAttribute moduleAttribute) {
        return buildModule(moduleAttribute, clb -> {});
    }

    /**
     * Builds a module descriptor into a byte array.
     *
     * @param moduleAttribute the {@code Module} attribute
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the {@code class} file bytes
     * @throws IllegalArgumentException if building encounters a failure
     */
    default byte[] buildModule(ModuleAttribute moduleAttribute,
                               Consumer<? super ClassBuilder> handler) {
        return build(CD_module_info, clb -> {
            clb.withFlags(AccessFlag.MODULE);
            clb.with(moduleAttribute);
            handler.accept(clb);
        });
    }

    /**
     * Builds a module descriptor into a file in a file system.
     *
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if building encounters a failure
     */
    default void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute) throws IOException {
        buildModuleTo(path, moduleAttribute, clb -> {});
    }

    /**
     * Builds a module descriptor into a file in a file system.
     *
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if building encounters a failure
     */
    default void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute,
                                     Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, buildModule(moduleAttribute, handler));
    }

    /**
     * Transform one {@code class} file into a new {@code class} file according
     * to a {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * ConstantPoolBuilder cpb = null; // @replace substring=null; replacement=...
     * this.build(model.thisClass(), cpb,
     *            clb -> clb.transform(model, transform));
     * }
     * where {@code cpb} is determined by {@link ConstantPoolSharingOption}.
     *
     * @apiNote
     * This is named {@code transformClass} instead of {@code transform} for
     * consistency with {@link ClassBuilder#transformField}, {@link
     * ClassBuilder#transformMethod}, and {@link MethodBuilder#transformCode},
     * and to distinguish from {@link ClassFileBuilder#transform}, which is
     * more generic and powerful.
     *
     * @param model the class model to transform
     * @param transform the transform
     * @return the bytes of the new class
     * @throws IllegalArgumentException if building encounters a failure
     * @see ConstantPoolSharingOption
     */
    default byte[] transformClass(ClassModel model, ClassTransform transform) {
        return transformClass(model, model.thisClass(), transform);
    }

    /**
     * Transform one {@code class} file into a new {@code class} file according
     * to a {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     *
     * @apiNote
     * This is named {@code transformClass} instead of {@code transform} for
     * consistency with {@link ClassBuilder#transformField}, {@link
     * ClassBuilder#transformMethod}, and {@link MethodBuilder#transformCode},
     * and to distinguish from {@link ClassFileBuilder#transform}, which is
     * more generic and powerful.
     *
     * @param model the class model to transform
     * @param newClassName new class name
     * @param transform the transform
     * @return the bytes of the new class
     * @throws IllegalArgumentException if building encounters a failure
     * @see ConstantPoolSharingOption
     */
    default byte[] transformClass(ClassModel model, ClassDesc newClassName, ClassTransform transform) {
        return transformClass(model, TemporaryConstantPool.INSTANCE.classEntry(newClassName), transform);
    }

    /**
     * Transform one {@code class} file into a new {@code class} file according
     * to a {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     * <p>
     * This method behaves as if:
     * {@snippet lang=java :
     * ConstantPoolBuilder cpb = null; // @replace substring=null; replacement=...
     * this.build(newClassName, cpb, clb -> clb.transform(model, transform));
     * }
     * where {@code cpb} is determined by {@link ConstantPoolSharingOption}.
     *
     * @apiNote
     * This is named {@code transformClass} instead of {@code transform} for
     * consistency with {@link ClassBuilder#transformField}, {@link
     * ClassBuilder#transformMethod}, and {@link MethodBuilder#transformCode},
     * and to distinguish from {@link ClassFileBuilder#transform}, which is
     * more generic and powerful.
     *
     * @param model the class model to transform
     * @param newClassName new class name
     * @param transform the transform
     * @return the bytes of the new class
     * @throws IllegalArgumentException if building encounters a failure
     * @see ConstantPoolSharingOption
     */
    byte[] transformClass(ClassModel model, ClassEntry newClassName, ClassTransform transform);

    /**
     * Verify a {@code class} file.  All verification errors found will be returned.
     *
     * @param model the class model to verify
     * @return a list of verification errors, or an empty list if no error is
     * found
     */
    List<VerifyError> verify(ClassModel model);

    /**
     * Verify a {@code class} file.  All verification errors found will be returned.
     *
     * @param bytes the {@code class} file bytes to verify
     * @return a list of verification errors, or an empty list if no error is
     * found
     */
    List<VerifyError> verify(byte[] bytes);

    /**
     * Verify a {@code class} file.  All verification errors found will be returned.
     *
     * @param path the {@code class} file path to verify
     * @return a list of verification errors, or an empty list if no error is
     * found
     * @throws IOException if an I/O error occurs
     */
    default List<VerifyError> verify(Path path) throws IOException {
        return verify(Files.readAllBytes(path));
    }

    /**
     * The magic number identifying the {@code class} file format,  {@value
     * "0x%04x" #MAGIC_NUMBER}.  It is a big-endian 4-byte value.
     */
    int MAGIC_NUMBER = 0xCAFEBABE;

    /** The bit mask of {@link AccessFlag#PUBLIC} access and property modifier. */
    int ACC_PUBLIC = 0x0001;

    /** The bit mask of {@link AccessFlag#PROTECTED} access and property modifier. */
    int ACC_PROTECTED = 0x0004;

    /** The bit mask of {@link AccessFlag#PRIVATE} access and property modifier. */
    int ACC_PRIVATE = 0x0002;

    /** The bit mask of {@link AccessFlag#INTERFACE} access and property modifier. */
    int ACC_INTERFACE = 0x0200;

    /** The bit mask of {@link AccessFlag#ENUM} access and property modifier. */
    int ACC_ENUM = 0x4000;

    /** The bit mask of {@link AccessFlag#ANNOTATION} access and property modifier. */
    int ACC_ANNOTATION = 0x2000;

    /** The bit mask of {@link AccessFlag#SUPER} access and property modifier. */
    int ACC_SUPER = 0x0020;

    /** The bit mask of {@link AccessFlag#ABSTRACT} access and property modifier. */
    int ACC_ABSTRACT = 0x0400;

    /** The bit mask of {@link AccessFlag#VOLATILE} access and property modifier. */
    int ACC_VOLATILE = 0x0040;

    /** The bit mask of {@link AccessFlag#TRANSIENT} access and property modifier. */
    int ACC_TRANSIENT = 0x0080;

    /** The bit mask of {@link AccessFlag#SYNTHETIC} access and property modifier. */
    int ACC_SYNTHETIC = 0x1000;

    /** The bit mask of {@link AccessFlag#STATIC} access and property modifier. */
    int ACC_STATIC = 0x0008;

    /** The bit mask of {@link AccessFlag#FINAL} access and property modifier. */
    int ACC_FINAL = 0x0010;

    /** The bit mask of {@link AccessFlag#SYNCHRONIZED} access and property modifier. */
    int ACC_SYNCHRONIZED = 0x0020;

    /** The bit mask of {@link AccessFlag#BRIDGE} access and property modifier. */
    int ACC_BRIDGE = 0x0040;

    /** The bit mask of {@link AccessFlag#VARARGS} access and property modifier. */
    int ACC_VARARGS = 0x0080;

    /** The bit mask of {@link AccessFlag#NATIVE} access and property modifier. */
    int ACC_NATIVE = 0x0100;

    /** The bit mask of {@link AccessFlag#STRICT} access and property modifier. */
    int ACC_STRICT = 0x0800;

    /** The bit mask of {@link AccessFlag#MODULE} access and property modifier. */
    int ACC_MODULE = 0x8000;

    /** The bit mask of {@link AccessFlag#OPEN} access and property modifier. */
    int ACC_OPEN = 0x20;

    /** The bit mask of {@link AccessFlag#MANDATED} access and property modifier. */
    int ACC_MANDATED = 0x8000;

    /** The bit mask of {@link AccessFlag#TRANSITIVE} access and property modifier. */
    int ACC_TRANSITIVE = 0x20;

    /** The bit mask of {@link AccessFlag#STATIC_PHASE} access and property modifier. */
    int ACC_STATIC_PHASE = 0x40;

    /**
     * The class major version of the initial version of Java, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_0
     * @see ClassFileFormatVersion#RELEASE_1
     */
    int JAVA_1_VERSION = 45;

    /**
     * The class major version introduced by Java 2 SE 1.2, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_2
     */
    int JAVA_2_VERSION = 46;

    /**
     * The class major version introduced by Java 2 SE 1.3, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_3
     */
    int JAVA_3_VERSION = 47;

    /**
     * The class major version introduced by Java 2 SE 1.4, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_4
     */
    int JAVA_4_VERSION = 48;

    /**
     * The class major version introduced by Java 2 SE 5.0, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_5
     */
    int JAVA_5_VERSION = 49;

    /**
     * The class major version introduced by Java SE 6, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_6
     */
    int JAVA_6_VERSION = 50;

    /**
     * The class major version introduced by Java SE 7, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_7
     */
    int JAVA_7_VERSION = 51;

    /**
     * The class major version introduced by Java SE 8, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_8
     */
    int JAVA_8_VERSION = 52;

    /**
     * The class major version introduced by Java SE 9, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_9
     */
    int JAVA_9_VERSION = 53;

    /**
     * The class major version introduced by Java SE 10, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_10
     */
    int JAVA_10_VERSION = 54;

    /**
     * The class major version introduced by Java SE 11, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_11
     */
    int JAVA_11_VERSION = 55;

    /**
     * The class major version introduced by Java SE 12, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_12
     */
    int JAVA_12_VERSION = 56;

    /**
     * The class major version introduced by Java SE 13, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_13
     */
    int JAVA_13_VERSION = 57;

    /**
     * The class major version introduced by Java SE 14, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_14
     */
    int JAVA_14_VERSION = 58;

    /**
     * The class major version introduced by Java SE 15, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_15
     */
    int JAVA_15_VERSION = 59;

    /**
     * The class major version introduced by Java SE 16, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_16
     */
    int JAVA_16_VERSION = 60;

    /**
     * The class major version introduced by Java SE 17, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_17
     */
    int JAVA_17_VERSION = 61;

    /**
     * The class major version introduced by Java SE 18, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_18
     */
    int JAVA_18_VERSION = 62;

    /**
     * The class major version introduced by Java SE 19, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_19
     */
    int JAVA_19_VERSION = 63;

    /**
     * The class major version introduced by Java SE 20, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_20
     */
    int JAVA_20_VERSION = 64;

    /**
     * The class major version introduced by Java SE 21, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_21
     */
    int JAVA_21_VERSION = 65;

    /**
     * The class major version introduced by Java SE 22, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_22
     */
    int JAVA_22_VERSION = 66;

    /**
     * The class major version introduced by Java SE 23, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_23
     */
    int JAVA_23_VERSION = 67;

    /**
     * The class major version introduced by Java SE 24, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_24
     */
    int JAVA_24_VERSION = 68;

    /**
     * The class major version introduced by Java SE 25, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_25
     * @since 25
     */
    int JAVA_25_VERSION = 69;

    /**
     * The class major version introduced by Java SE 26, {@value}.
     *
     * @see ClassFileFormatVersion#RELEASE_26
     * @since 26
     */
    int JAVA_26_VERSION = 70;

    /**
     * A minor version number {@value} indicating a class uses preview features
     * of a Java SE release since 12, for major versions {@value
     * #JAVA_12_VERSION} and above.
     */
    int PREVIEW_MINOR_VERSION = 65535;

    /**
     * {@return the latest class major version supported by the current runtime}
     */
    static int latestMajorVersion() {
        return JAVA_26_VERSION;
    }

    /**
     * {@return the latest class minor version supported by the current runtime}
     *
     * @apiNote
     * This does not report the {@link #PREVIEW_MINOR_VERSION} when the current
     * runtime has preview feature enabled, as {@code class} files with a major
     * version other than {@link #latestMajorVersion()} and the preview minor
     * version are not supported.
     */
    static int latestMinorVersion() {
        return 0;
    }

}
