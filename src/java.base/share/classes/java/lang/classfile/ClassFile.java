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
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTypeInfo;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
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
 * Represents a context for parsing, transforming, and generating classfiles.
 * A {@code ClassFile} has a set of options that condition how parsing and
 * generation is done.
 *
 * @since 24
 */
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
     * @since 24
     */
    sealed interface Option {
    }

    /**
     * Option describing attribute mappers for custom attributes.
     * Default is only to process standard attributes.
     *
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
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
     * @since 24
     */
    enum AttributesProcessingOption implements Option {

        /** Process all original attributes during transformation */
        PASS_ALL_ATTRIBUTES,

        /** Drop unknown attributes during transformation */
        DROP_UNKNOWN_ATTRIBUTES,

        /** Drop unknown and unstable original attributes during transformation */
        DROP_UNSTABLE_ATTRIBUTES
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
        return build(CD_module_info, clb -> {
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
     *                     clb -> clb.transform(model, transform));
     * }
     *
     * @param model the class model to transform
     * @param transform the transform
     * @return the bytes of the new class
     */
    default byte[] transformClass(ClassModel model, ClassTransform transform) {
        return transformClass(model, model.thisClass(), transform);
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
    default byte[] transformClass(ClassModel model, ClassDesc newClassName, ClassTransform transform) {
        return transformClass(model, TemporaryConstantPool.INSTANCE.classEntry(newClassName), transform);
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
     *                     clb -> clb.transform(model, transform));
     * }
     *
     * @param model the class model to transform
     * @param newClassName new class name
     * @param transform the transform
     * @return the bytes of the new class
     */
    byte[] transformClass(ClassModel model, ClassEntry newClassName, ClassTransform transform);

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

    /** The bit mask of PUBLIC access and property modifier. */
    int ACC_PUBLIC = 0x0001;

    /** The bit mask of PROTECTED access and property modifier. */
    int ACC_PROTECTED = 0x0004;

    /** The bit mask of PRIVATE access and property modifier. */
    int ACC_PRIVATE = 0x0002;

    /** The bit mask of INTERFACE access and property modifier. */
    int ACC_INTERFACE = 0x0200;

    /** The bit mask of ENUM access and property modifier. */
    int ACC_ENUM = 0x4000;

    /** The bit mask of ANNOTATION access and property modifier. */
    int ACC_ANNOTATION = 0x2000;

    /** The bit mask of SUPER access and property modifier. */
    int ACC_SUPER = 0x0020;

    /** The bit mask of ABSTRACT access and property modifier. */
    int ACC_ABSTRACT = 0x0400;

    /** The bit mask of VOLATILE access and property modifier. */
    int ACC_VOLATILE = 0x0040;

    /** The bit mask of TRANSIENT access and property modifier. */
    int ACC_TRANSIENT = 0x0080;

    /** The bit mask of SYNTHETIC access and property modifier. */
    int ACC_SYNTHETIC = 0x1000;

    /** The bit mask of STATIC access and property modifier. */
    int ACC_STATIC = 0x0008;

    /** The bit mask of FINAL access and property modifier. */
    int ACC_FINAL = 0x0010;

    /** The bit mask of SYNCHRONIZED access and property modifier. */
    int ACC_SYNCHRONIZED = 0x0020;

    /** The bit mask of BRIDGE access and property modifier. */
    int ACC_BRIDGE = 0x0040;

    /** The bit mask of VARARGS access and property modifier. */
    int ACC_VARARGS = 0x0080;

    /** The bit mask of NATIVE access and property modifier. */
    int ACC_NATIVE = 0x0100;

    /** The bit mask of STRICT access and property modifier. */
    int ACC_STRICT = 0x0800;

    /** The bit mask of MODULE access and property modifier. */
    int ACC_MODULE = 0x8000;

    /** The bit mask of OPEN access and property modifier. */
    int ACC_OPEN = 0x20;

    /** The bit mask of MANDATED access and property modifier. */
    int ACC_MANDATED = 0x8000;

    /** The bit mask of TRANSITIVE access and property modifier. */
    int ACC_TRANSITIVE = 0x20;

    /** The bit mask of STATIC_PHASE access and property modifier. */
    int ACC_STATIC_PHASE = 0x40;

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

    /** The class major version of JAVA_23. */
    int JAVA_23_VERSION = 67;

    /** The class major version of JAVA_24. */
    int JAVA_24_VERSION = 68;

    /**
     * The class major version of JAVA_25.
     * @since 25
     */
    int JAVA_25_VERSION = 69;

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
        return JAVA_25_VERSION;
    }

    /**
     * {@return the latest minor Java version}
     */
    static int latestMinorVersion() {
        return 0;
    }

}
