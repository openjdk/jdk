/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.UnknownAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.ClassfileImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.attribute.CharacterRangeInfo;
import jdk.internal.classfile.attribute.LocalVariableInfo;
import jdk.internal.classfile.attribute.LocalVariableTypeInfo;
import jdk.internal.classfile.instruction.ExceptionCatch;
import static java.util.Objects.requireNonNull;

/**
 * Represents a context for parsing, transforming, and generating classfiles.
 * A {@code Classfile} has a set of options that condition how parsing and
 * generation is done.
 */
public sealed interface Classfile
        permits ClassfileImpl {

    /**
     * {@return a context with default options}
     */
    static Classfile of() {
        return ClassfileImpl.DEFAULT_CONTEXT;
    }

    /**
     * {@return a new context with options altered from the default}
     * @param options the desired processing options
     */
    static Classfile of(Option... options) {
        return of().withOptions(options);
    }

    /**
     * {@return a copy of the context with altered options}
     * @param options the desired processing options
     */
    Classfile withOptions(Option... options);

    /**
     * An option that affects the parsing and writing of classfiles.
     */
    sealed interface Option {
    }

    /**
     * Option describing attribute mappers for custom attributes.
     * Default is only to process standard attributes.
     */
    sealed interface AttributeMapperOption extends Option
            permits ClassfileImpl.AttributeMapperOptionImpl {

        /**
         * {@return an option describing attribute mappers for custom attributes}
         * @param attributeMapper a function mapping attribute names to attribute mappers
         */
        static AttributeMapperOption of(Function<Utf8Entry, AttributeMapper<?>> attributeMapper) {
            requireNonNull(attributeMapper);
            return new ClassfileImpl.AttributeMapperOptionImpl(attributeMapper);
        }

        /**
         * {@return the function mapping attribute names to attribute mappers}
         */
        Function<Utf8Entry, AttributeMapper<?>> attributeMapper();
    }

    /**
     * Option describing the class hierarchy resolver to use when generating
     * stack maps.
     */
    sealed interface ClassHierarchyResolverOption extends Option
            permits ClassfileImpl.ClassHierarchyResolverOptionImpl {

        /**
         * {@return an option describing the class hierarchy resolver to use when
         * generating stack maps}
         * @param classHierarchyResolver the resolver
         */
        static ClassHierarchyResolverOption of(ClassHierarchyResolver classHierarchyResolver) {
            requireNonNull(classHierarchyResolver);
            return new ClassfileImpl.ClassHierarchyResolverOptionImpl(classHierarchyResolver);
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
     */
    enum ConstantPoolSharingOption implements Option {

        /** Preserves the original constant pool when transforming classfile */
        SHARED_POOL,

        /** Creates a new constant pool when transforming classfile */
        NEW_POOL
    }

    /**
     * Option describing whether or not to patch out unreachable code.
     * Default is {@code PATCH_DEAD_CODE} to automatically patch out unreachable
     * code with NOPs.
     */
    enum DeadCodeOption implements Option {

        /** Patch unreachable code */
        PATCH_DEAD_CODE,

        /** Keep the unreachable code */
        KEEP_DEAD_CODE
    }

    /**
     * Option describing whether or not to filter unresolved labels.
     * Default is {@code FAIL_ON_DEAD_LABELS} to throw IllegalStateException
     * when any {@link ExceptionCatch}, {@link LocalVariableInfo},
     * {@link LocalVariableTypeInfo}, or {@link CharacterRangeInfo}
     * reference to unresolved {@link Label} during bytecode serialization.
     * Setting this option to {@code DROP_DEAD_LABELS} filters the above
     * elements instead.
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
     */
    enum LineNumbersOption implements Option {

        /** Process line numbers */
        PASS_LINE_NUMBERS,

        /** Drop line numbers */
        DROP_LINE_NUMBERS;
    }

    /**
     * Option describing whether or not to automatically rewrite short jumps to
     * long when necessary.
     * Default is {@code FIX_SHORT_JUMPS} to automatically rewrite jump
     * instructions.
     */
    enum ShortJumpsOption implements Option {

        /** Automatically convert short jumps to long when necessary */
        FIX_SHORT_JUMPS,

        /** Fail if short jump overflows */
        FAIL_ON_SHORT_JUMPS
    }

    /**
     * Option describing whether or not to generate stackmaps.
     * Default is {@code STACK_MAPS_WHEN_REQUIRED} to generate stack
     * maps for {@link #JAVA_6_VERSION} or above, where specifically for
     * {@link #JAVA_6_VERSION} the stack maps may not be generated.
     * @jvms 4.10.1 Verification by Type Checking
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
     */
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
     */
    ClassModel parse(byte[] bytes);

    /**
     * Parse a classfile into a {@link ClassModel}.
     * @param path the path to the classfile
     * @return the class model
     * @throws java.io.IOException
     */
    default ClassModel parse(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    /**
     * Build a classfile into a byte array.
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
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
     * @throws java.io.IOException
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
     * @throws java.io.IOException
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
     * @throws java.io.IOException
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
     * @throws java.io.IOException
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

    /** 0xCAFEBABE */
    int MAGIC_NUMBER = 0xCAFEBABE;

    /** 0 */
    int NOP             = 0;

    /** 1 */
    int ACONST_NULL     = 1;

    /** 2 */
    int ICONST_M1       = 2;

    /** 3 */
    int ICONST_0        = 3;

    /** 4 */
    int ICONST_1        = 4;

    /** 5 */
    int ICONST_2        = 5;

    /** 6 */
    int ICONST_3        = 6;

    /** 7 */
    int ICONST_4        = 7;

    /** 8 */
    int ICONST_5        = 8;

    /** 9 */
    int LCONST_0        = 9;

    /** 10 */
    int LCONST_1        = 10;

    /** 11 */
    int FCONST_0        = 11;

    /** 12 */
    int FCONST_1        = 12;

    /** 13 */
    int FCONST_2        = 13;

    /** 14 */
    int DCONST_0        = 14;

    /** 15 */
    int DCONST_1        = 15;

    /** 16 */
    int BIPUSH          = 16;

    /** 17 */
    int SIPUSH          = 17;

    /** 18 */
    int LDC             = 18;

    /** 19 */
    int LDC_W           = 19;

    /** 20 */
    int LDC2_W          = 20;

    /** 21 */
    int ILOAD           = 21;

    /** 22 */
    int LLOAD           = 22;

    /** 23 */
    int FLOAD           = 23;

    /** 24 */
    int DLOAD           = 24;

    /** 25 */
    int ALOAD           = 25;

    /** 26 */
    int ILOAD_0         = 26;

    /** 27 */
    int ILOAD_1         = 27;

    /** 28 */
    int ILOAD_2         = 28;

    /** 29 */
    int ILOAD_3         = 29;

    /** 30 */
    int LLOAD_0         = 30;

    /** 31 */
    int LLOAD_1         = 31;

    /** 32 */
    int LLOAD_2         = 32;

    /** 33 */
    int LLOAD_3         = 33;

    /** 34 */
    int FLOAD_0         = 34;

    /** 35 */
    int FLOAD_1         = 35;

    /** 36 */
    int FLOAD_2         = 36;

    /** 37 */
    int FLOAD_3         = 37;

    /** 38 */
    int DLOAD_0         = 38;

    /** 39 */
    int DLOAD_1         = 39;

    /** 40 */
    int DLOAD_2         = 40;

    /** 41 */
    int DLOAD_3         = 41;

    /** 42 */
    int ALOAD_0         = 42;

    /** 43 */
    int ALOAD_1         = 43;

    /** 44 */
    int ALOAD_2         = 44;

    /** 45 */
    int ALOAD_3         = 45;

    /** 46 */
    int IALOAD          = 46;

    /** 47 */
    int LALOAD          = 47;

    /** 48 */
    int FALOAD          = 48;

    /** 49 */
    int DALOAD          = 49;

    /** 50 */
    int AALOAD          = 50;

    /** 51 */
    int BALOAD          = 51;

    /** 52 */
    int CALOAD          = 52;

    /** 53 */
    int SALOAD          = 53;

    /** 54 */
    int ISTORE          = 54;

    /** 55 */
    int LSTORE          = 55;

    /** 56 */
    int FSTORE          = 56;

    /** 57 */
    int DSTORE          = 57;

    /** 58 */
    int ASTORE          = 58;

    /** 59 */
    int ISTORE_0        = 59;

    /** 60 */
    int ISTORE_1        = 60;

    /** 61 */
    int ISTORE_2        = 61;

    /** 62 */
    int ISTORE_3        = 62;

    /** 63 */
    int LSTORE_0        = 63;

    /** 64 */
    int LSTORE_1        = 64;

    /** 65 */
    int LSTORE_2        = 65;

    /** 66 */
    int LSTORE_3        = 66;

    /** 67 */
    int FSTORE_0        = 67;

    /** 68 */
    int FSTORE_1        = 68;

    /** 69 */
    int FSTORE_2        = 69;

    /** 70 */
    int FSTORE_3        = 70;

    /** 71 */
    int DSTORE_0        = 71;

    /** 72 */
    int DSTORE_1        = 72;

    /** 73 */
    int DSTORE_2        = 73;

    /** 74 */
    int DSTORE_3        = 74;

    /** 75 */
    int ASTORE_0        = 75;

    /** 76 */
    int ASTORE_1        = 76;

    /** 77 */
    int ASTORE_2        = 77;

    /** 78 */
    int ASTORE_3        = 78;

    /** 79 */
    int IASTORE         = 79;

    /** 80 */
    int LASTORE         = 80;

    /** 81 */
    int FASTORE         = 81;

    /** 82 */
    int DASTORE         = 82;

    /** 83 */
    int AASTORE         = 83;

    /** 84 */
    int BASTORE         = 84;

    /** 85 */
    int CASTORE         = 85;

    /** 86 */
    int SASTORE         = 86;

    /** 87 */
    int POP             = 87;

    /** 88 */
    int POP2            = 88;

    /** 89 */
    int DUP             = 89;

    /** 90 */
    int DUP_X1          = 90;

    /** 91 */
    int DUP_X2          = 91;

    /** 92 */
    int DUP2            = 92;

    /** 93 */
    int DUP2_X1         = 93;

    /** 94 */
    int DUP2_X2         = 94;

    /** 95 */
    int SWAP            = 95;

    /** 96 */
    int IADD            = 96;

    /** 97 */
    int LADD            = 97;

    /** 98 */
    int FADD            = 98;

    /** 99 */
    int DADD            = 99;

    /** 100 */
    int ISUB            = 100;

    /** 101 */
    int LSUB            = 101;

    /** 102 */
    int FSUB            = 102;

    /** 103 */
    int DSUB            = 103;

    /** 104 */
    int IMUL            = 104;

    /** 105 */
    int LMUL            = 105;

    /** 106 */
    int FMUL            = 106;

    /** 107 */
    int DMUL            = 107;

    /** 108 */
    int IDIV            = 108;

    /** 109 */
    int LDIV            = 109;

    /** 110 */
    int FDIV            = 110;

    /** 111 */
    int DDIV            = 111;

    /** 112 */
    int IREM            = 112;

    /** 113 */
    int LREM            = 113;

    /** 114 */
    int FREM            = 114;

    /** 115 */
    int DREM            = 115;

    /** 116 */
    int INEG            = 116;

    /** 117 */
    int LNEG            = 117;

    /** 118 */
    int FNEG            = 118;

    /** 119 */
    int DNEG            = 119;

    /** 120 */
    int ISHL            = 120;

    /** 121 */
    int LSHL            = 121;

    /** 122 */
    int ISHR            = 122;

    /** 123 */
    int LSHR            = 123;

    /** 124 */
    int IUSHR           = 124;

    /** 125 */
    int LUSHR           = 125;

    /** 126 */
    int IAND            = 126;

    /** 127 */
    int LAND            = 127;

    /** 128 */
    int IOR             = 128;

    /** 129 */
    int LOR             = 129;

    /** 130 */
    int IXOR            = 130;

    /** 131 */
    int LXOR            = 131;

    /** 132 */
    int IINC            = 132;

    /** 133 */
    int I2L             = 133;

    /** 134 */
    int I2F             = 134;

    /** 135 */
    int I2D             = 135;

    /** 136 */
    int L2I             = 136;

    /** 137 */
    int L2F             = 137;

    /** 138 */
    int L2D             = 138;

    /** 139 */
    int F2I             = 139;

    /** 140 */
    int F2L             = 140;

    /** 141 */
    int F2D             = 141;

    /** 142 */
    int D2I             = 142;

    /** 143 */
    int D2L             = 143;

    /** 144 */
    int D2F             = 144;

    /** 145 */
    int I2B             = 145;

    /** 146 */
    int I2C             = 146;

    /** 147 */
    int I2S             = 147;

    /** 148 */
    int LCMP            = 148;

    /** 149 */
    int FCMPL           = 149;

    /** 150 */
    int FCMPG           = 150;

    /** 151 */
    int DCMPL           = 151;

    /** 152 */
    int DCMPG           = 152;

    /** 153 */
    int IFEQ            = 153;

    /** 154 */
    int IFNE            = 154;

    /** 155 */
    int IFLT            = 155;

    /** 156 */
    int IFGE            = 156;

    /** 157 */
    int IFGT            = 157;

    /** 158 */
    int IFLE            = 158;

    /** 159 */
    int IF_ICMPEQ       = 159;

    /** 160 */
    int IF_ICMPNE       = 160;

    /** 161 */
    int IF_ICMPLT       = 161;

    /** 162 */
    int IF_ICMPGE       = 162;

    /** 163 */
    int IF_ICMPGT       = 163;

    /** 164 */
    int IF_ICMPLE       = 164;

    /** 165 */
    int IF_ACMPEQ       = 165;

    /** 166 */
    int IF_ACMPNE       = 166;

    /** 167 */
    int GOTO            = 167;

    /** 168 */
    int JSR             = 168;

    /** 169 */
    int RET             = 169;

    /** 170 */
    int TABLESWITCH     = 170;

    /** 171 */
    int LOOKUPSWITCH    = 171;

    /** 172 */
    int IRETURN         = 172;

    /** 173 */
    int LRETURN         = 173;

    /** 174 */
    int FRETURN         = 174;

    /** 175 */
    int DRETURN         = 175;

    /** 176 */
    int ARETURN         = 176;

    /** 177 */
    int RETURN          = 177;

    /** 178 */
    int GETSTATIC       = 178;

    /** 179 */
    int PUTSTATIC       = 179;

    /** 180 */
    int GETFIELD        = 180;

    /** 181 */
    int PUTFIELD        = 181;

    /** 182 */
    int INVOKEVIRTUAL   = 182;

    /** 183 */
    int INVOKESPECIAL   = 183;

    /** 184 */
    int INVOKESTATIC    = 184;

    /** 185 */
    int INVOKEINTERFACE = 185;

    /** 186 */
    int INVOKEDYNAMIC   = 186;

    /** 187 */
    int NEW             = 187;

    /** 188 */
    int NEWARRAY        = 188;

    /** 189 */
    int ANEWARRAY       = 189;

    /** 190 */
    int ARRAYLENGTH     = 190;

    /** 191 */
    int ATHROW          = 191;

    /** 192 */
    int CHECKCAST       = 192;

    /** 193 */
    int INSTANCEOF      = 193;

    /** 194 */
    int MONITORENTER    = 194;

    /** 195 */
    int MONITOREXIT     = 195;

    /** 196 */
    int WIDE            = 196;

    /** 197 */
    int MULTIANEWARRAY  = 197;

    /** 198 */
    int IFNULL          = 198;

    /** 199 */
    int IFNONNULL       = 199;

    /** 200 */
    int GOTO_W          = 200;

    /** 201 */
    int JSR_W           = 201;

    /** 0x0001 */
    int ACC_PUBLIC = 0x0001;

    /** 0x0004 */
    int ACC_PROTECTED = 0x0004;

    /** 0x0002 */
    int ACC_PRIVATE = 0x0002;

    /** 0x0200 */
    int ACC_INTERFACE = 0x0200;

    /** 0x4000 */
    int ACC_ENUM = 0x4000;

    /** 0x2000 */
    int ACC_ANNOTATION = 0x2000;

    /** 0x0020 */
    int ACC_SUPER = 0x0020;

    /** 0x0400 */
    int ACC_ABSTRACT = 0x0400;

    /** 0x0040 */
    int ACC_VOLATILE = 0x0040;

    /** 0x0080 */
    int ACC_TRANSIENT = 0x0080;

    /** 0x1000 */
    int ACC_SYNTHETIC = 0x1000;

    /** 0x0008 */
    int ACC_STATIC = 0x0008;

    /** 0x0010 */
    int ACC_FINAL = 0x0010;

    /** 0x0020 */
    int ACC_SYNCHRONIZED = 0x0020;

    /** 0x0040 */
    int ACC_BRIDGE = 0x0040;

    /** 0x0080 */
    int ACC_VARARGS = 0x0080;

    /** 0x0100 */
    int ACC_NATIVE = 0x0100;

    /** 0x0800 */
    int ACC_STRICT = 0x0800;

    /** 0x8000 */
    int ACC_MODULE = 0x8000;

    /** 0x20 */
    int ACC_OPEN = 0x20;

    /** 0x8000 */
    int ACC_MANDATED = 0x8000;

    /** 0x20 */
    int ACC_TRANSITIVE = 0x20;

    /** 0x40 */
    int ACC_STATIC_PHASE = 0x40;

    /** 0x0001 */
    int CRT_STATEMENT       = 0x0001;

    /** 0x0002 */
    int CRT_BLOCK           = 0x0002;

    /** 0x0004 */
    int CRT_ASSIGNMENT      = 0x0004;

    /** 0x0008 */
    int CRT_FLOW_CONTROLLER = 0x0008;

    /** 0x0010 */
    int CRT_FLOW_TARGET     = 0x0010;

    /** 0x0020 */
    int CRT_INVOKE          = 0x0020;

    /** 0x0040 */
    int CRT_CREATE          = 0x0040;

    /** 0x0080 */
    int CRT_BRANCH_TRUE     = 0x0080;

    /** 0x0100 */
    int CRT_BRANCH_FALSE    = 0x0100;

    /** 7 */
    int TAG_CLASS = 7;

    /** 17 */
    int TAG_CONSTANTDYNAMIC = 17;

    /** 6 */
    int TAG_DOUBLE = 6;

    /** 9 */
    int TAG_FIELDREF = 9;

    /** 4 */
    int TAG_FLOAT = 4;

    /** 3 */
    int TAG_INTEGER = 3;

    /** 11 */
    int TAG_INTERFACEMETHODREF = 11;

    /** 18 */
    int TAG_INVOKEDYNAMIC = 18;

    /** 5 */
    int TAG_LONG = 5;

    /** 15 */
    int TAG_METHODHANDLE = 15;

    /** 10 */
    int TAG_METHODREF = 10;

    /** 16 */
    int TAG_METHODTYPE = 16;

    /** 19 */
    int TAG_MODULE = 19;

    /** 12 */
    int TAG_NAMEANDTYPE = 12;

    /** 20 */
    int TAG_PACKAGE = 20;

    /** 8 */
    int TAG_STRING = 8;

    /** 2 */
    int TAG_UNICODE = 2;

    /** 1 */
    int TAG_UTF8 = 1;

    // annotation element values

    /** 'B' */
    char AEV_BYTE = 'B';

    /** 'C' */
    char AEV_CHAR = 'C';

    /** 'D' */
    char AEV_DOUBLE = 'D';

    /** 'F' */
    char AEV_FLOAT = 'F';

    /** 'I' */
    char AEV_INT = 'I';

    /** 'J' */
    char AEV_LONG = 'J';

    /** 'S' */
    char AEV_SHORT = 'S';

    /** 'Z' */
    char AEV_BOOLEAN = 'Z';

    /** 's' */
    char AEV_STRING = 's';

    /** 'e' */
    char AEV_ENUM = 'e';

    /** 'c' */
    char AEV_CLASS = 'c';

    /** '@' */
    char AEV_ANNOTATION = '@';

    /** '[' */
    char AEV_ARRAY = '[';

    //type annotations

    /** 0x00 */
    int TAT_CLASS_TYPE_PARAMETER = 0x00;

    /** 0x01 */
    int TAT_METHOD_TYPE_PARAMETER = 0x01;

    /** 0x10 */
    int TAT_CLASS_EXTENDS = 0x10;

    /** 0x11 */
    int TAT_CLASS_TYPE_PARAMETER_BOUND = 0x11;

    /** 0x12 */
    int TAT_METHOD_TYPE_PARAMETER_BOUND = 0x12;

    /** 0x13 */
    int TAT_FIELD = 0x13;

    /** 0x14 */
    int TAT_METHOD_RETURN = 0x14;

    /** 0x15 */
    int TAT_METHOD_RECEIVER = 0x15;

    /** 0x16 */
    int TAT_METHOD_FORMAL_PARAMETER = 0x16;

    /** 0x17 */
    int TAT_THROWS = 0x17;

    /** 0x40 */
    int TAT_LOCAL_VARIABLE = 0x40;

    /** 0x41 */
    int TAT_RESOURCE_VARIABLE = 0x41;

    /** 0x42 */
    int TAT_EXCEPTION_PARAMETER = 0x42;

    /** 0x43 */
    int TAT_INSTANCEOF = 0x43;

    /** 0x44 */
    int TAT_NEW = 0x44;

    /** 0x45 */
    int TAT_CONSTRUCTOR_REFERENCE = 0x45;

    /** 0x46 */
    int TAT_METHOD_REFERENCE = 0x46;

    /** 0x47 */
    int TAT_CAST = 0x47;

    /** 0x48 */
    int TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT = 0x48;

    /** 0x49 */
    int TAT_METHOD_INVOCATION_TYPE_ARGUMENT = 0x49;

    /** 0x4A */
    int TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT = 0x4A;

    /** 0x4B */
    int TAT_METHOD_REFERENCE_TYPE_ARGUMENT = 0x4B;

    //stackmap verification types

    /** 0 */
    int VT_TOP = 0;

    /** 1 */
    int VT_INTEGER = 1;

    /** 2 */
    int VT_FLOAT = 2;

    /** 3 */
    int VT_DOUBLE = 3;

    /** 4 */
    int VT_LONG = 4;

    /** 5 */
    int VT_NULL = 5;

    /** 6 */
    int VT_UNINITIALIZED_THIS = 6;

    /** 7 */
    int VT_OBJECT = 7;

    /** 8 */
    int VT_UNINITIALIZED = 8;

    /** ACC_PUBLIC */
    int DEFAULT_CLASS_FLAGS = ACC_PUBLIC;

    /** 45 */
    int JAVA_1_VERSION = 45;

    /** 46 */
    int JAVA_2_VERSION = 46;

    /** 47 */
    int JAVA_3_VERSION = 47;

    /** 48 */
    int JAVA_4_VERSION = 48;

    /** 49 */
    int JAVA_5_VERSION = 49;

    /** 50 */
    int JAVA_6_VERSION = 50;

    /** 51 */
    int JAVA_7_VERSION = 51;

    /** 52 */
    int JAVA_8_VERSION = 52;

    /** 53 */
    int JAVA_9_VERSION = 53;

    /** 54 */
    int JAVA_10_VERSION = 54;

    /** 55 */
    int JAVA_11_VERSION = 55;

    /** 56 */
    int JAVA_12_VERSION = 56;

    /** 57 */
    int JAVA_13_VERSION = 57;

    /** 58 */
    int JAVA_14_VERSION = 58;

    /** 59 */
    int JAVA_15_VERSION = 59;

    /** 60 */
    int JAVA_16_VERSION = 60;

    /** 61 */
    int JAVA_17_VERSION = 61;

    /** 62 */
    int JAVA_18_VERSION = 62;

    /** 63 */
    int JAVA_19_VERSION = 63;

    /** 64 */
    int JAVA_20_VERSION = 64;

    /** 65 */
    int JAVA_21_VERSION = 65;

    /** 66 */
    int JAVA_22_VERSION = 66;

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
        return JAVA_22_VERSION;
    }

    /**
     * {@return the latest minor Java version}
     */
    static int latestMinorVersion() {
        return 0;
    }

}
