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

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.ClassFileImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTypeInfo;
import java.lang.classfile.instruction.ExceptionCatch;
import java.util.List;
import static java.util.Objects.requireNonNull;
import jdk.internal.javac.PreviewFeature;

/**
 * Represents a context for parsing, transforming, and generating classfiles.
 * A {@code ClassFile} has a set of options that condition how parsing and
 * generation is done.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ClassFile
        permits ClassFileImpl {

    /**
     * {@return a context with default options}
     */
    static ClassFile of() {
        return ClassFileImpl.DEFAULT_CONTEXT;
    }

    /**
     * {@return a new context with options altered from the default}
     * @param options the desired processing options
     */
    static ClassFile of(Option... options) {
        return of().withOptions(options);
    }

    /**
     * {@return a copy of the context with altered options}
     * @param options the desired processing options
     */
    ClassFile withOptions(Option... options);

    /**
     * An option that affects the parsing and writing of classfiles.
     *
     * @sealedGraph
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface Option {
    }

    /**
     * Option describing attribute mappers for custom attributes.
     * Default is only to process standard attributes.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface AttributeMapperOption extends Option
            permits ClassFileImpl.AttributeMapperOptionImpl {

        /**
         * {@return an option describing attribute mappers for custom attributes}
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
     * Option describing the class hierarchy resolver to use when generating
     * stack maps.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface ClassHierarchyResolverOption extends Option
            permits ClassFileImpl.ClassHierarchyResolverOptionImpl {

        /**
         * {@return an option describing the class hierarchy resolver to use when
         * generating stack maps}
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
     * Option describing whether to preserve the original constant pool when
     * transforming a classfile.  Reusing the constant pool enables significant
     * optimizations in processing time and minimizes differences between the
     * original and transformed classfile, but may result in a bigger classfile
     * when a classfile is significantly transformed.
     * Default is {@code SHARED_POOL} to preserve the original constant
     * pool.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum ConstantPoolSharingOption implements Option {

        /** Preserves the original constant pool when transforming classfile */
        SHARED_POOL,

        /** Creates a new constant pool when transforming classfile */
        NEW_POOL
    }

    /**
     * Option describing whether to patch out unreachable code.
     * Default is {@code PATCH_DEAD_CODE} to automatically patch out unreachable
     * code with NOPs.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum DeadCodeOption implements Option {

        /** Patch unreachable code */
        PATCH_DEAD_CODE,

        /** Keep the unreachable code */
        KEEP_DEAD_CODE
    }

    /**
     * Option describing whether to filter unresolved labels.
     * Default is {@code FAIL_ON_DEAD_LABELS} to throw IllegalArgumentException
     * when any {@link ExceptionCatch}, {@link LocalVariableInfo},
     * {@link LocalVariableTypeInfo}, or {@link CharacterRangeInfo}
     * reference to unresolved {@link Label} during bytecode serialization.
     * Setting this option to {@code DROP_DEAD_LABELS} filters the above
     * elements instead.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum DeadLabelsOption implements Option {

        /** Fail on unresolved labels */
        FAIL_ON_DEAD_LABELS,

        /** Filter unresolved labels */
        DROP_DEAD_LABELS
    }

    /**
     * Option describing whether to process or discard debug elements.
     * Debug elements include the local variable table, local variable type
     * table, and character range table.  Discarding debug elements may
     * reduce the overhead of parsing or transforming classfiles.
     * Default is {@code PASS_DEBUG} to process debug elements.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum DebugElementsOption implements Option {

        /** Process debug elements */
        PASS_DEBUG,

        /** Drop debug elements */
        DROP_DEBUG
    }

    /**
     * Option describing whether to process or discard line numbers.
     * Discarding line numbers may reduce the overhead of parsing or transforming
     * classfiles.
     * Default is {@code PASS_LINE_NUMBERS} to process line numbers.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum LineNumbersOption implements Option {

        /** Process line numbers */
        PASS_LINE_NUMBERS,

        /** Drop line numbers */
        DROP_LINE_NUMBERS;
    }

    /**
     * Option describing whether to automatically rewrite short jumps to
     * long when necessary.
     * Default is {@code FIX_SHORT_JUMPS} to automatically rewrite jump
     * instructions.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum ShortJumpsOption implements Option {

        /** Automatically convert short jumps to long when necessary */
        FIX_SHORT_JUMPS,

        /** Fail if short jump overflows */
        FAIL_ON_SHORT_JUMPS
    }

    /**
     * Option describing whether to generate stackmaps.
     * Default is {@code STACK_MAPS_WHEN_REQUIRED} to generate stack
     * maps for {@link #JAVA_6_VERSION} or above, where specifically for
     * {@link #JAVA_6_VERSION} the stack maps may not be generated.
     * @jvms 4.10.1 Verification by Type Checking
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum StackMapsOption implements Option {

        /** Generate stack maps when required */
        STACK_MAPS_WHEN_REQUIRED,

        /** Always generate stack maps */
        GENERATE_STACK_MAPS,

        /** Drop stack maps from code */
        DROP_STACK_MAPS
    }

    /**
     * Option describing whether to process or discard unrecognized or problematic
     * original attributes when a class, record component, field, method or code is
     * transformed in its exploded form.
     * Default is {@code PASS_ALL_ATTRIBUTES} to process all original attributes.
     * @see AttributeMapper.AttributeStability
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum AttributesProcessingOption implements Option {

        /** Process all original attributes during transformation */
        PASS_ALL_ATTRIBUTES,

        /** Drop unknown attributes during transformation */
        DROP_UNKNOWN_ATTRIBUTES,

        /** Drop unknown and unstable original attributes during transformation */
        DROP_UNSTABLE_ATRIBUTES;
    }

    /**
     * Parse a classfile into a {@link ClassModel}.
     * @param bytes the bytes of the classfile
     * @return the class model
     * @throws IllegalArgumentException or its subclass if the classfile format is
     * not supported or an incompatibility prevents parsing of the classfile
     */
    ClassModel parse(byte[] bytes);

    /**
     * Parse a classfile into a {@link ClassModel}.
     * @param path the path to the classfile
     * @return the class model
     * @throws java.io.IOException if an I/O error occurs
     * @throws IllegalArgumentException or its subclass if the classfile format is
     * not supported or an incompatibility prevents parsing of the classfile
     */
    default ClassModel parse(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    /**
     * Build a classfile into a byte array.
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     * @throws IllegalArgumentException if {@code thisClass} represents a primitive type
     */
    default byte[] build(ClassDesc thisClass,
                         Consumer<? super ClassBuilder> handler) {
        ConstantPoolBuilder pool = ConstantPoolBuilder.of();
        return build(pool.classEntry(thisClass), pool, handler);
    }

    /**
     * Build a classfile into a byte array using the provided constant pool
     * builder.
     *
     * @param thisClassEntry the name of the class to build
     * @param constantPool the constant pool builder
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    byte[] build(ClassEntry thisClassEntry,
                 ConstantPoolBuilder constantPool,
                 Consumer<? super ClassBuilder> handler);

    /**
     * Build a classfile into a file.
     * @param path the path to the file to write
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws java.io.IOException if an I/O error occurs
     */
    default void buildTo(Path path,
                         ClassDesc thisClass,
                         Consumer<ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClass, handler));
    }

    /**
     * Build a classfile into a file using the provided constant pool
     * builder.
     *
     * @param path the path to the file to write
     * @param thisClassEntry the name of the class to build
     * @param constantPool the constant pool builder
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws java.io.IOException if an I/O error occurs
     */
    default void buildTo(Path path,
                         ClassEntry thisClassEntry,
                         ConstantPoolBuilder constantPool,
                         Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClassEntry, constantPool, handler));
    }

    /**
     * Build a module descriptor into a byte array.
     * @param moduleAttribute the {@code Module} attribute
     * @return the classfile bytes
     */
    default byte[] buildModule(ModuleAttribute moduleAttribute) {
        return buildModule(moduleAttribute, clb -> {});
    }

    /**
     * Build a module descriptor into a byte array.
     * @param moduleAttribute the {@code Module} attribute
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    default byte[] buildModule(ModuleAttribute moduleAttribute,
                                     Consumer<? super ClassBuilder> handler) {
        return build(ClassDesc.of("module-info"), clb -> {
            clb.withFlags(AccessFlag.MODULE);
            clb.with(moduleAttribute);
            handler.accept(clb);
        });
    }

    /**
     * Build a module descriptor into a file.
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @throws java.io.IOException if an I/O error occurs
     */
    default void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute) throws IOException {
        buildModuleTo(path, moduleAttribute, clb -> {});
    }

    /**
     * Build a module descriptor into a file.
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @param handler a handler that receives a {@link ClassBuilder}
     * @throws java.io.IOException if an I/O error occurs
     */
    default void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute,
                                     Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, buildModule(moduleAttribute, handler));
    }

    /**
     * Transform one classfile into a new classfile with the aid of a
     * {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     *
     * @implNote
     * This method behaves as if:
     * {@snippet lang=java :
     *     this.build(model.thisClass(), ConstantPoolBuilder.of(model),
     *                     b -> b.transform(model, transform));
     * }
     *
     * @param model the class model to transform
     * @param transform the transform
     * @return the bytes of the new class
     */
    default byte[] transform(ClassModel model, ClassTransform transform) {
        return transform(model, model.thisClass(), transform);
    }

    /**
     * Transform one classfile into a new classfile with the aid of a
     * {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     *
     * @param model the class model to transform
     * @param newClassName new class name
     * @param transform the transform
     * @return the bytes of the new class
     */
    default byte[] transform(ClassModel model, ClassDesc newClassName, ClassTransform transform) {
        return transform(model, TemporaryConstantPool.INSTANCE.classEntry(newClassName), transform);
    }

    /**
     * Transform one classfile into a new classfile with the aid of a
     * {@link ClassTransform}.  The transform will receive each element of
     * this class, as well as a {@link ClassBuilder} for building the new class.
     * The transform is free to preserve, remove, or replace elements as it
     * sees fit.
     *
     * @implNote
     * This method behaves as if:
     * {@snippet lang=java :
     *     this.build(newClassName, ConstantPoolBuilder.of(model),
     *                     b -> b.transform(model, transform));
     * }
     *
     * @param model the class model to transform
     * @param newClassName new class name
     * @param transform the transform
     * @return the bytes of the new class
     */
    byte[] transform(ClassModel model, ClassEntry newClassName, ClassTransform transform);

    /**
     * Verify a classfile.  Any verification errors found will be returned.
     * @param model the class model to verify
     * @return a list of verification errors, or an empty list if no errors are
     * found
     */
    List<VerifyError> verify(ClassModel model);

    /**
     * Verify a classfile.  Any verification errors found will be returned.
     * @param bytes the classfile bytes to verify
     * @return a list of verification errors, or an empty list if no errors are
     * found
     */
    List<VerifyError> verify(byte[] bytes);

    /**
     * Verify a classfile.  Any verification errors found will be returned.
     * @param path the classfile path to verify
     * @return a list of verification errors, or an empty list if no errors are
     * found
     * @throws java.io.IOException if an I/O error occurs
     */
    default List<VerifyError> verify(Path path) throws IOException {
        return verify(Files.readAllBytes(path));
    }

    /** 0xCAFEBABE */
    int MAGIC_NUMBER = 0xCAFEBABE;

    /** The integer value used to encode the NOP instruction. */
    int NOP             = 0;

    /** The integer value used to encode the ACONST_NULL instruction. */
    int ACONST_NULL     = 1;

    /** The integer value used to encode the ICONST_M1 instruction. */
    int ICONST_M1       = 2;

    /** The integer value used to encode the ICONST_0 instruction. */
    int ICONST_0        = 3;

    /** The integer value used to encode the ICONST_1 instruction. */
    int ICONST_1        = 4;

    /** The integer value used to encode the ICONST_2 instruction. */
    int ICONST_2        = 5;

    /** The integer value used to encode the ICONST_3 instruction. */
    int ICONST_3        = 6;

    /** The integer value used to encode the ICONST_4 instruction. */
    int ICONST_4        = 7;

    /** The integer value used to encode the ICONST_5 instruction. */
    int ICONST_5        = 8;

    /** The integer value used to encode the LCONST_0 instruction. */
    int LCONST_0        = 9;

    /** The integer value used to encode the LCONST_1 instruction. */
    int LCONST_1        = 10;

    /** The integer value used to encode the FCONST_0 instruction. */
    int FCONST_0        = 11;

    /** The integer value used to encode the FCONST_1 instruction. */
    int FCONST_1        = 12;

    /** The integer value used to encode the FCONST_2 instruction. */
    int FCONST_2        = 13;

    /** The integer value used to encode the DCONST_0 instruction. */
    int DCONST_0        = 14;

    /** The integer value used to encode the DCONST_1 instruction. */
    int DCONST_1        = 15;

    /** The integer value used to encode the BIPUSH instruction. */
    int BIPUSH          = 16;

    /** The integer value used to encode the SIPUSH instruction. */
    int SIPUSH          = 17;

    /** The integer value used to encode the LDC instruction. */
    int LDC             = 18;

    /** The integer value used to encode the LDC_W instruction. */
    int LDC_W           = 19;

    /** The integer value used to encode the LDC2_W instruction. */
    int LDC2_W          = 20;

    /** The integer value used to encode the ILOAD instruction. */
    int ILOAD           = 21;

    /** The integer value used to encode the LLOAD instruction. */
    int LLOAD           = 22;

    /** The integer value used to encode the FLOAD instruction. */
    int FLOAD           = 23;

    /** The integer value used to encode the DLOAD instruction. */
    int DLOAD           = 24;

    /** The integer value used to encode the ALOAD instruction. */
    int ALOAD           = 25;

    /** The integer value used to encode the ILOAD_0 instruction. */
    int ILOAD_0         = 26;

    /** The integer value used to encode the ILOAD_1 instruction. */
    int ILOAD_1         = 27;

    /** The integer value used to encode the ILOAD_2 instruction. */
    int ILOAD_2         = 28;

    /** The integer value used to encode the ILOAD_3 instruction. */
    int ILOAD_3         = 29;

    /** The integer value used to encode the LLOAD_0 instruction. */
    int LLOAD_0         = 30;

    /** The integer value used to encode the LLOAD_1 instruction. */
    int LLOAD_1         = 31;

    /** The integer value used to encode the LLOAD_2 instruction. */
    int LLOAD_2         = 32;

    /** The integer value used to encode the LLOAD_3 instruction. */
    int LLOAD_3         = 33;

    /** The integer value used to encode the FLOAD_0 instruction. */
    int FLOAD_0         = 34;

    /** The integer value used to encode the FLOAD_1 instruction. */
    int FLOAD_1         = 35;

    /** The integer value used to encode the FLOAD_2 instruction. */
    int FLOAD_2         = 36;

    /** The integer value used to encode the FLOAD_3 instruction. */
    int FLOAD_3         = 37;

    /** The integer value used to encode the DLOAD_0 instruction. */
    int DLOAD_0         = 38;

    /** The integer value used to encode the DLOAD_1 instruction. */
    int DLOAD_1         = 39;

    /** The integer value used to encode the DLOAD_2 instruction. */
    int DLOAD_2         = 40;

    /** The integer value used to encode the DLOAD_3 instruction. */
    int DLOAD_3         = 41;

    /** The integer value used to encode the ALOAD_0 instruction. */
    int ALOAD_0         = 42;

    /** The integer value used to encode the ALOAD_1 instruction. */
    int ALOAD_1         = 43;

    /** The integer value used to encode the ALOAD_2 instruction. */
    int ALOAD_2         = 44;

    /** The integer value used to encode the ALOAD_3 instruction. */
    int ALOAD_3         = 45;

    /** The integer value used to encode the IALOAD instruction. */
    int IALOAD          = 46;

    /** The integer value used to encode the LALOAD instruction. */
    int LALOAD          = 47;

    /** The integer value used to encode the FALOAD instruction. */
    int FALOAD          = 48;

    /** The integer value used to encode the DALOAD instruction. */
    int DALOAD          = 49;

    /** The integer value used to encode the AALOAD instruction. */
    int AALOAD          = 50;

    /** The integer value used to encode the BALOAD instruction. */
    int BALOAD          = 51;

    /** The integer value used to encode the CALOAD instruction. */
    int CALOAD          = 52;

    /** The integer value used to encode the SALOAD instruction. */
    int SALOAD          = 53;

    /** The integer value used to encode the ISTORE instruction. */
    int ISTORE          = 54;

    /** The integer value used to encode the LSTORE instruction. */
    int LSTORE          = 55;

    /** The integer value used to encode the FSTORE instruction. */
    int FSTORE          = 56;

    /** The integer value used to encode the DSTORE instruction. */
    int DSTORE          = 57;

    /** The integer value used to encode the ASTORE instruction. */
    int ASTORE          = 58;

    /** The integer value used to encode the ISTORE_0 instruction. */
    int ISTORE_0        = 59;

    /** The integer value used to encode the ISTORE_1 instruction. */
    int ISTORE_1        = 60;

    /** The integer value used to encode the ISTORE_2 instruction. */
    int ISTORE_2        = 61;

    /** The integer value used to encode the ISTORE_3 instruction. */
    int ISTORE_3        = 62;

    /** The integer value used to encode the LSTORE_0 instruction. */
    int LSTORE_0        = 63;

    /** The integer value used to encode the LSTORE_1 instruction. */
    int LSTORE_1        = 64;

    /** The integer value used to encode the LSTORE_2 instruction. */
    int LSTORE_2        = 65;

    /** The integer value used to encode the LSTORE_3 instruction. */
    int LSTORE_3        = 66;

    /** The integer value used to encode the FSTORE_0 instruction. */
    int FSTORE_0        = 67;

    /** The integer value used to encode the FSTORE_1 instruction. */
    int FSTORE_1        = 68;

    /** The integer value used to encode the FSTORE_2 instruction. */
    int FSTORE_2        = 69;

    /** The integer value used to encode the FSTORE_3 instruction. */
    int FSTORE_3        = 70;

    /** The integer value used to encode the DSTORE_0 instruction. */
    int DSTORE_0        = 71;

    /** The integer value used to encode the DSTORE_1 instruction. */
    int DSTORE_1        = 72;

    /** The integer value used to encode the DSTORE_2 instruction. */
    int DSTORE_2        = 73;

    /** The integer value used to encode the DSTORE_3 instruction. */
    int DSTORE_3        = 74;

    /** The integer value used to encode the ASTORE_0 instruction. */
    int ASTORE_0        = 75;

    /** The integer value used to encode the ASTORE_1 instruction. */
    int ASTORE_1        = 76;

    /** The integer value used to encode the ASTORE_2 instruction. */
    int ASTORE_2        = 77;

    /** The integer value used to encode the ASTORE_3 instruction. */
    int ASTORE_3        = 78;

    /** The integer value used to encode the IASTORE instruction. */
    int IASTORE         = 79;

    /** The integer value used to encode the LASTORE instruction. */
    int LASTORE         = 80;

    /** The integer value used to encode the FASTORE instruction. */
    int FASTORE         = 81;

    /** The integer value used to encode the DASTORE instruction. */
    int DASTORE         = 82;

    /** The integer value used to encode the AASTORE instruction. */
    int AASTORE         = 83;

    /** The integer value used to encode the BASTORE instruction. */
    int BASTORE         = 84;

    /** The integer value used to encode the CASTORE instruction. */
    int CASTORE         = 85;

    /** The integer value used to encode the SASTORE instruction. */
    int SASTORE         = 86;

    /** The integer value used to encode the POP instruction. */
    int POP             = 87;

    /** The integer value used to encode the POP2 instruction. */
    int POP2            = 88;

    /** The integer value used to encode the DUP instruction. */
    int DUP             = 89;

    /** The integer value used to encode the DUP_X1 instruction. */
    int DUP_X1          = 90;

    /** The integer value used to encode the DUP_X2 instruction. */
    int DUP_X2          = 91;

    /** The integer value used to encode the DUP2 instruction. */
    int DUP2            = 92;

    /** The integer value used to encode the DUP2_X1 instruction. */
    int DUP2_X1         = 93;

    /** The integer value used to encode the DUP2_X2 instruction. */
    int DUP2_X2         = 94;

    /** The integer value used to encode the SWAP instruction. */
    int SWAP            = 95;

    /** The integer value used to encode the IADD instruction. */
    int IADD            = 96;

    /** The integer value used to encode the LADD instruction. */
    int LADD            = 97;

    /** The integer value used to encode the FADD instruction. */
    int FADD            = 98;

    /** The integer value used to encode the DADD instruction. */
    int DADD            = 99;

    /** The integer value used to encode the ISUB instruction. */
    int ISUB            = 100;

    /** The integer value used to encode the LSUB instruction. */
    int LSUB            = 101;

    /** The integer value used to encode the FSUB instruction. */
    int FSUB            = 102;

    /** The integer value used to encode the DSUB instruction. */
    int DSUB            = 103;

    /** The integer value used to encode the IMUL instruction. */
    int IMUL            = 104;

    /** The integer value used to encode the LMUL instruction. */
    int LMUL            = 105;

    /** The integer value used to encode the FMUL instruction. */
    int FMUL            = 106;

    /** The integer value used to encode the DMUL instruction. */
    int DMUL            = 107;

    /** The integer value used to encode the IDIV instruction. */
    int IDIV            = 108;

    /** The integer value used to encode the LDIV instruction. */
    int LDIV            = 109;

    /** The integer value used to encode the FDIV instruction. */
    int FDIV            = 110;

    /** The integer value used to encode the DDIV instruction. */
    int DDIV            = 111;

    /** The integer value used to encode the IREM instruction. */
    int IREM            = 112;

    /** The integer value used to encode the LREM instruction. */
    int LREM            = 113;

    /** The integer value used to encode the FREM instruction. */
    int FREM            = 114;

    /** The integer value used to encode the DREM instruction. */
    int DREM            = 115;

    /** The integer value used to encode the INEG instruction. */
    int INEG            = 116;

    /** The integer value used to encode the LNEG instruction. */
    int LNEG            = 117;

    /** The integer value used to encode the FNEG instruction. */
    int FNEG            = 118;

    /** The integer value used to encode the DNEG instruction. */
    int DNEG            = 119;

    /** The integer value used to encode the ISHL instruction. */
    int ISHL            = 120;

    /** The integer value used to encode the LSHL instruction. */
    int LSHL            = 121;

    /** The integer value used to encode the ISHR instruction. */
    int ISHR            = 122;

    /** The integer value used to encode the LSHR instruction. */
    int LSHR            = 123;

    /** The integer value used to encode the IUSHR instruction. */
    int IUSHR           = 124;

    /** The integer value used to encode the LUSHR instruction. */
    int LUSHR           = 125;

    /** The integer value used to encode the IAND instruction. */
    int IAND            = 126;

    /** The integer value used to encode the LAND instruction. */
    int LAND            = 127;

    /** The integer value used to encode the IOR instruction. */
    int IOR             = 128;

    /** The integer value used to encode the LOR instruction. */
    int LOR             = 129;

    /** The integer value used to encode the IXOR instruction. */
    int IXOR            = 130;

    /** The integer value used to encode the LXOR instruction. */
    int LXOR            = 131;

    /** The integer value used to encode the IINC instruction. */
    int IINC            = 132;

    /** The integer value used to encode the I2L instruction. */
    int I2L             = 133;

    /** The integer value used to encode the I2F instruction. */
    int I2F             = 134;

    /** The integer value used to encode the I2D instruction. */
    int I2D             = 135;

    /** The integer value used to encode the L2I instruction. */
    int L2I             = 136;

    /** The integer value used to encode the L2F instruction. */
    int L2F             = 137;

    /** The integer value used to encode the L2D instruction. */
    int L2D             = 138;

    /** The integer value used to encode the F2I instruction. */
    int F2I             = 139;

    /** The integer value used to encode the F2L instruction. */
    int F2L             = 140;

    /** The integer value used to encode the F2D instruction. */
    int F2D             = 141;

    /** The integer value used to encode the D2I instruction. */
    int D2I             = 142;

    /** The integer value used to encode the D2L instruction. */
    int D2L             = 143;

    /** The integer value used to encode the D2F instruction. */
    int D2F             = 144;

    /** The integer value used to encode the I2B instruction. */
    int I2B             = 145;

    /** The integer value used to encode the I2C instruction. */
    int I2C             = 146;

    /** The integer value used to encode the I2S instruction. */
    int I2S             = 147;

    /** The integer value used to encode the LCMP instruction. */
    int LCMP            = 148;

    /** The integer value used to encode the FCMPL instruction. */
    int FCMPL           = 149;

    /** The integer value used to encode the FCMPG instruction. */
    int FCMPG           = 150;

    /** The integer value used to encode the DCMPL instruction. */
    int DCMPL           = 151;

    /** The integer value used to encode the DCMPG instruction. */
    int DCMPG           = 152;

    /** The integer value used to encode the IFEQ instruction. */
    int IFEQ            = 153;

    /** The integer value used to encode the IFNE instruction. */
    int IFNE            = 154;

    /** The integer value used to encode the IFLT instruction. */
    int IFLT            = 155;

    /** The integer value used to encode the IFGE instruction. */
    int IFGE            = 156;

    /** The integer value used to encode the IFGT instruction. */
    int IFGT            = 157;

    /** The integer value used to encode the IFLE instruction. */
    int IFLE            = 158;

    /** The integer value used to encode the IF_ICMPEQ instruction. */
    int IF_ICMPEQ       = 159;

    /** The integer value used to encode the IF_ICMPNE instruction. */
    int IF_ICMPNE       = 160;

    /** The integer value used to encode the IF_ICMPLT instruction. */
    int IF_ICMPLT       = 161;

    /** The integer value used to encode the IF_ICMPGE instruction. */
    int IF_ICMPGE       = 162;

    /** The integer value used to encode the IF_ICMPGT instruction. */
    int IF_ICMPGT       = 163;

    /** The integer value used to encode the IF_ICMPLE instruction. */
    int IF_ICMPLE       = 164;

    /** The integer value used to encode the IF_ACMPEQ instruction. */
    int IF_ACMPEQ       = 165;

    /** The integer value used to encode the IF_ACMPNE instruction. */
    int IF_ACMPNE       = 166;

    /** The integer value used to encode the GOTO instruction. */
    int GOTO            = 167;

    /** The integer value used to encode the JSR instruction. */
    int JSR             = 168;

    /** The integer value used to encode the RET instruction. */
    int RET             = 169;

    /** The integer value used to encode the TABLESWITCH instruction. */
    int TABLESWITCH     = 170;

    /** The integer value used to encode the LOOKUPSWITCH instruction. */
    int LOOKUPSWITCH    = 171;

    /** The integer value used to encode the IRETURN instruction. */
    int IRETURN         = 172;

    /** The integer value used to encode the LRETURN instruction. */
    int LRETURN         = 173;

    /** The integer value used to encode the FRETURN instruction. */
    int FRETURN         = 174;

    /** The integer value used to encode the DRETURN instruction. */
    int DRETURN         = 175;

    /** The integer value used to encode the ARETURN instruction. */
    int ARETURN         = 176;

    /** The integer value used to encode the RETURN instruction. */
    int RETURN          = 177;

    /** The integer value used to encode the GETSTATIC instruction. */
    int GETSTATIC       = 178;

    /** The integer value used to encode the PUTSTATIC instruction. */
    int PUTSTATIC       = 179;

    /** The integer value used to encode the GETFIELD instruction. */
    int GETFIELD        = 180;

    /** The integer value used to encode the PUTFIELD instruction. */
    int PUTFIELD        = 181;

    /** The integer value used to encode the INVOKEVIRTUAL instruction. */
    int INVOKEVIRTUAL   = 182;

    /** The integer value used to encode the INVOKESPECIAL instruction. */
    int INVOKESPECIAL   = 183;

    /** The integer value used to encode the INVOKESTATIC instruction. */
    int INVOKESTATIC    = 184;

    /** The integer value used to encode the INVOKEINTERFACE instruction. */
    int INVOKEINTERFACE = 185;

    /** The integer value used to encode the INVOKEDYNAMIC instruction. */
    int INVOKEDYNAMIC   = 186;

    /** The integer value used to encode the NEW instruction. */
    int NEW             = 187;

    /** The integer value used to encode the NEWARRAY instruction. */
    int NEWARRAY        = 188;

    /** The integer value used to encode the ANEWARRAY instruction. */
    int ANEWARRAY       = 189;

    /** The integer value used to encode the ARRAYLENGTH instruction. */
    int ARRAYLENGTH     = 190;

    /** The integer value used to encode the ATHROW instruction. */
    int ATHROW          = 191;

    /** The integer value used to encode the CHECKCAST instruction. */
    int CHECKCAST       = 192;

    /** The integer value used to encode the INSTANCEOF instruction. */
    int INSTANCEOF      = 193;

    /** The integer value used to encode the MONITORENTER instruction. */
    int MONITORENTER    = 194;

    /** The integer value used to encode the MONITOREXIT instruction. */
    int MONITOREXIT     = 195;

    /** The integer value used to encode the WIDE instruction. */
    int WIDE            = 196;

    /** The integer value used to encode the MULTIANEWARRAY instruction. */
    int MULTIANEWARRAY  = 197;

    /** The integer value used to encode the IFNULL instruction. */
    int IFNULL          = 198;

    /** The integer value used to encode the IFNONNULL instruction. */
    int IFNONNULL       = 199;

    /** The integer value used to encode the GOTO_W instruction. */
    int GOTO_W          = 200;

    /** The integer value used to encode the JSR_W instruction. */
    int JSR_W           = 201;

    /** The value of PUBLIC access and property modifier. */
    int ACC_PUBLIC = 0x0001;

    /** The value of PROTECTED access and property modifier. */
    int ACC_PROTECTED = 0x0004;

    /** The value of PRIVATE access and property modifier. */
    int ACC_PRIVATE = 0x0002;

    /** The value of INTERFACE access and property modifier. */
    int ACC_INTERFACE = 0x0200;

    /** The value of ENUM access and property modifier. */
    int ACC_ENUM = 0x4000;

    /** The value of ANNOTATION access and property modifier. */
    int ACC_ANNOTATION = 0x2000;

    /** The value of SUPER access and property modifier. */
    int ACC_SUPER = 0x0020;

    /** The value of ABSTRACT access and property modifier. */
    int ACC_ABSTRACT = 0x0400;

    /** The value of VOLATILE access and property modifier. */
    int ACC_VOLATILE = 0x0040;

    /** The value of TRANSIENT access and property modifier. */
    int ACC_TRANSIENT = 0x0080;

    /** The value of SYNTHETIC access and property modifier. */
    int ACC_SYNTHETIC = 0x1000;

    /** The value of STATIC access and property modifier. */
    int ACC_STATIC = 0x0008;

    /** The value of FINAL access and property modifier. */
    int ACC_FINAL = 0x0010;

    /** The value of SYNCHRONIZED access and property modifier. */
    int ACC_SYNCHRONIZED = 0x0020;

    /** The value of BRIDGE access and property modifier. */
    int ACC_BRIDGE = 0x0040;

    /** The value of VARARGS access and property modifier. */
    int ACC_VARARGS = 0x0080;

    /** The value of NATIVE access and property modifier. */
    int ACC_NATIVE = 0x0100;

    /** The value of STRICT access and property modifier. */
    int ACC_STRICT = 0x0800;

    /** The value of MODULE access and property modifier. */
    int ACC_MODULE = 0x8000;

    /** The value of OPEN access and property modifier. */
    int ACC_OPEN = 0x20;

    /** The value of MANDATED access and property modifier. */
    int ACC_MANDATED = 0x8000;

    /** The value of TRANSITIVE access and property modifier. */
    int ACC_TRANSITIVE = 0x20;

    /** The value of STATIC_PHASE access and property modifier. */
    int ACC_STATIC_PHASE = 0x40;

    /** The value of STATEMENT {@link CharacterRangeInfo} kind. */
    int CRT_STATEMENT       = 0x0001;

    /** The value of BLOCK {@link CharacterRangeInfo} kind. */
    int CRT_BLOCK           = 0x0002;

    /** The value of ASSIGNMENT {@link CharacterRangeInfo} kind. */
    int CRT_ASSIGNMENT      = 0x0004;

    /** The value of FLOW_CONTROLLER {@link CharacterRangeInfo} kind. */
    int CRT_FLOW_CONTROLLER = 0x0008;

    /** The value of FLOW_TARGET {@link CharacterRangeInfo} kind. */
    int CRT_FLOW_TARGET     = 0x0010;

    /** The value of INVOKE {@link CharacterRangeInfo} kind. */
    int CRT_INVOKE          = 0x0020;

    /** The value of CREATE {@link CharacterRangeInfo} kind. */
    int CRT_CREATE          = 0x0040;

    /** The value of BRANCH_TRUE {@link CharacterRangeInfo} kind. */
    int CRT_BRANCH_TRUE     = 0x0080;

    /** The value of BRANCH_FALSE {@link CharacterRangeInfo} kind. */
    int CRT_BRANCH_FALSE    = 0x0100;

    /** The value of constant pool tag CLASS. */
    int TAG_CLASS = 7;

    /** The value of constant pool tag CONSTANTDYNAMIC. */
    int TAG_CONSTANTDYNAMIC = 17;

    /** The value of constant pool tag DOUBLE. */
    int TAG_DOUBLE = 6;

    /** The value of constant pool tag FIELDREF. */
    int TAG_FIELDREF = 9;

    /** The value of constant pool tag FLOAT. */
    int TAG_FLOAT = 4;

    /** The value of constant pool tag INTEGER. */
    int TAG_INTEGER = 3;

    /** The value of constant pool tag INTERFACEMETHODREF. */
    int TAG_INTERFACEMETHODREF = 11;

    /** The value of constant pool tag INVOKEDYNAMIC. */
    int TAG_INVOKEDYNAMIC = 18;

    /** The value of constant pool tag LONG. */
    int TAG_LONG = 5;

    /** The value of constant pool tag METHODHANDLE. */
    int TAG_METHODHANDLE = 15;

    /** The value of constant pool tag METHODREF. */
    int TAG_METHODREF = 10;

    /** The value of constant pool tag METHODTYPE. */
    int TAG_METHODTYPE = 16;

    /** The value of constant pool tag MODULE. */
    int TAG_MODULE = 19;

    /** The value of constant pool tag NAMEANDTYPE. */
    int TAG_NAMEANDTYPE = 12;

    /** The value of constant pool tag PACKAGE. */
    int TAG_PACKAGE = 20;

    /** The value of constant pool tag STRING. */
    int TAG_STRING = 8;

    /** The value of constant pool tag UNICODE. */
    int TAG_UNICODE = 2;

    /** The value of constant pool tag UTF8. */
    int TAG_UTF8 = 1;

    // annotation element values

    /** The value of annotation element value type AEV_BYTE. */
    int AEV_BYTE = 'B';

    /** The value of annotation element value type AEV_CHAR. */
    int AEV_CHAR = 'C';

    /** The value of annotation element value type AEV_DOUBLE. */
    int AEV_DOUBLE = 'D';

    /** The value of annotation element value type AEV_FLOAT. */
    int AEV_FLOAT = 'F';

    /** The value of annotation element value type AEV_INT. */
    int AEV_INT = 'I';

    /** The value of annotation element value type AEV_LONG. */
    int AEV_LONG = 'J';

    /** The value of annotation element value type AEV_SHORT. */
    int AEV_SHORT = 'S';

    /** The value of annotation element value type AEV_BOOLEAN. */
    int AEV_BOOLEAN = 'Z';

    /** The value of annotation element value type AEV_STRING. */
    int AEV_STRING = 's';

    /** The value of annotation element value type AEV_ENUM. */
    int AEV_ENUM = 'e';

    /** The value of annotation element value type AEV_CLASS. */
    int AEV_CLASS = 'c';

    /** The value of annotation element value type AEV_ANNOTATION. */
    int AEV_ANNOTATION = '@';

    /** The value of annotation element value type AEV_ARRAY. */
    int AEV_ARRAY = '[';

    //type annotations

    /** The value of type annotation target type CLASS_TYPE_PARAMETER. */
    int TAT_CLASS_TYPE_PARAMETER = 0x00;

    /** The value of type annotation target type METHOD_TYPE_PARAMETER. */
    int TAT_METHOD_TYPE_PARAMETER = 0x01;

    /** The value of type annotation target type CLASS_EXTENDS. */
    int TAT_CLASS_EXTENDS = 0x10;

    /** The value of type annotation target type CLASS_TYPE_PARAMETER_BOUND. */
    int TAT_CLASS_TYPE_PARAMETER_BOUND = 0x11;

    /** The value of type annotation target type METHOD_TYPE_PARAMETER_BOUND. */
    int TAT_METHOD_TYPE_PARAMETER_BOUND = 0x12;

    /** The value of type annotation target type FIELD. */
    int TAT_FIELD = 0x13;

    /** The value of type annotation target type METHOD_RETURN. */
    int TAT_METHOD_RETURN = 0x14;

    /** The value of type annotation target type METHOD_RECEIVER. */
    int TAT_METHOD_RECEIVER = 0x15;

    /** The value of type annotation target type METHOD_FORMAL_PARAMETER. */
    int TAT_METHOD_FORMAL_PARAMETER = 0x16;

    /** The value of type annotation target type THROWS. */
    int TAT_THROWS = 0x17;

    /** The value of type annotation target type LOCAL_VARIABLE. */
    int TAT_LOCAL_VARIABLE = 0x40;

    /** The value of type annotation target type RESOURCE_VARIABLE. */
    int TAT_RESOURCE_VARIABLE = 0x41;

    /** The value of type annotation target type EXCEPTION_PARAMETER. */
    int TAT_EXCEPTION_PARAMETER = 0x42;

    /** The value of type annotation target type INSTANCEOF. */
    int TAT_INSTANCEOF = 0x43;

    /** The value of type annotation target type NEW. */
    int TAT_NEW = 0x44;

    /** The value of type annotation target type CONSTRUCTOR_REFERENCE. */
    int TAT_CONSTRUCTOR_REFERENCE = 0x45;

    /** The value of type annotation target type METHOD_REFERENCE. */
    int TAT_METHOD_REFERENCE = 0x46;

    /** The value of type annotation target type CAST. */
    int TAT_CAST = 0x47;

    /** The value of type annotation target type CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT. */
    int TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT = 0x48;

    /** The value of type annotation target type METHOD_INVOCATION_TYPE_ARGUMENT. */
    int TAT_METHOD_INVOCATION_TYPE_ARGUMENT = 0x49;

    /** The value of type annotation target type CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT. */
    int TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT = 0x4A;

    /** The value of type annotation target type METHOD_REFERENCE_TYPE_ARGUMENT. */
    int TAT_METHOD_REFERENCE_TYPE_ARGUMENT = 0x4B;

    //stackmap verification types

    /** The value of verification type TOP. */
    int VT_TOP = 0;

    /** The value of verification type INTEGER. */
    int VT_INTEGER = 1;

    /** The value of verification type FLOAT. */
    int VT_FLOAT = 2;

    /** The value of verification type DOUBLE. */
    int VT_DOUBLE = 3;

    /** The value of verification type LONG. */
    int VT_LONG = 4;

    /** The value of verification type NULL. */
    int VT_NULL = 5;

    /** The value of verification type UNINITIALIZED_THIS. */
    int VT_UNINITIALIZED_THIS = 6;

    /** The value of verification type OBJECT. */
    int VT_OBJECT = 7;

    /** The value of verification type UNINITIALIZED. */
    int VT_UNINITIALIZED = 8;

    /** The value of default class access flags */
    int DEFAULT_CLASS_FLAGS = ACC_PUBLIC;

    /** The class major version of JAVA_1. */
    int JAVA_1_VERSION = 45;

    /** The class major version of JAVA_2. */
    int JAVA_2_VERSION = 46;

    /** The class major version of JAVA_3. */
    int JAVA_3_VERSION = 47;

    /** The class major version of JAVA_4. */
    int JAVA_4_VERSION = 48;

    /** The class major version of JAVA_5. */
    int JAVA_5_VERSION = 49;

    /** The class major version of JAVA_6. */
    int JAVA_6_VERSION = 50;

    /** The class major version of JAVA_7. */
    int JAVA_7_VERSION = 51;

    /** The class major version of JAVA_8. */
    int JAVA_8_VERSION = 52;

    /** The class major version of JAVA_9. */
    int JAVA_9_VERSION = 53;

    /** The class major version of JAVA_10. */
    int JAVA_10_VERSION = 54;

    /** The class major version of JAVA_11. */
    int JAVA_11_VERSION = 55;

    /** The class major version of JAVA_12. */
    int JAVA_12_VERSION = 56;

    /** The class major version of JAVA_13. */
    int JAVA_13_VERSION = 57;

    /** The class major version of JAVA_14. */
    int JAVA_14_VERSION = 58;

    /** The class major version of JAVA_15. */
    int JAVA_15_VERSION = 59;

    /** The class major version of JAVA_16. */
    int JAVA_16_VERSION = 60;

    /** The class major version of JAVA_17. */
    int JAVA_17_VERSION = 61;

    /** The class major version of JAVA_18. */
    int JAVA_18_VERSION = 62;

    /** The class major version of JAVA_19. */
    int JAVA_19_VERSION = 63;

    /** The class major version of JAVA_20. */
    int JAVA_20_VERSION = 64;

    /** The class major version of JAVA_21. */
    int JAVA_21_VERSION = 65;

    /** The class major version of JAVA_22. */
    int JAVA_22_VERSION = 66;

    /**
     * The class major version of JAVA_23.
     * @since 23
     */
    int JAVA_23_VERSION = 67;

    /**
     * A minor version number indicating a class uses preview features
     * of a Java SE version since 12, for major versions {@value
     * #JAVA_12_VERSION} and above.
     */
    int PREVIEW_MINOR_VERSION = 65535;

    /**
     * {@return the latest major Java version}
     */
    static int latestMajorVersion() {
        return JAVA_23_VERSION;
    }

    /**
     * {@return the latest minor Java version}
     */
    static int latestMinorVersion() {
        return 0;
    }

}
