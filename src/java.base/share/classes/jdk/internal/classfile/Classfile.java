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

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.UnknownAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.constantpool.PackageEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.ClassImpl;
import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.DirectClassBuilder;
import jdk.internal.classfile.impl.Options;
import jdk.internal.classfile.impl.SplitConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.attribute.CharacterRangeInfo;
import jdk.internal.classfile.attribute.LocalVariableInfo;
import jdk.internal.classfile.attribute.LocalVariableTypeInfo;
import jdk.internal.classfile.instruction.ExceptionCatch;
import jdk.internal.classfile.jdktypes.PackageDesc;

/**
 * Main entry points for parsing, transforming, and generating classfiles.
 */
public class Classfile {
    private Classfile() {
    }

    /**
     * An option that affects the writing of classfiles.
     */
    public sealed interface Option permits Options.OptionValue {

        /**
         * {@return an option describing whether or not to generate stackmaps}
         * Default is to generate stack maps.
         * @param b whether to generate stack maps
         */
        static Option generateStackmap(boolean b) { return new Options.OptionValue(Options.Key.GENERATE_STACK_MAPS, b); }

        /**
         * {@return an option describing whether to process or discard debug elements}
         * Debug elements include the local variable table, local variable type
         * table, and character range table.  Discarding debug elements may
         * reduce the overhead of parsing or transforming classfiles.
         * Default is to process debug elements.
         * @param b whether or not to process debug elements
         */
        static Option processDebug(boolean b) { return new Options.OptionValue(Options.Key.PROCESS_DEBUG, b); }

        /**
         * {@return an option describing whether to process or discard line numbers}
         * Discarding line numbers may reduce the overhead of parsing or transforming
         * classfiles.
         * Default is to process line numbers.
         * @param b whether or not to process line numbers
         */
        static Option processLineNumbers(boolean b) { return new Options.OptionValue(Options.Key.PROCESS_LINE_NUMBERS, b); }

        /**
         * {@return an option describing whether to process or discard unrecognized
         * attributes}
         * Default is to process unrecognized attributes, and deliver as instances
         * of {@link UnknownAttribute}.
         * @param b whether or not to process unrecognized attributes
         */
        static Option processUnknownAttributes(boolean b) { return new Options.OptionValue(Options.Key.PROCESS_UNKNOWN_ATTRIBUTES, b); }

        /**
         * {@return an option describing whether to preserve the original constant
         * pool when transforming a classfile}  Reusing the constant pool enables significant
         * optimizations in processing time and minimizes differences between the
         * original and transformed classfile, but may result in a bigger classfile
         * when a classfile is significantly transformed.
         * Default is to preserve the original constant pool.
         * @param b whether or not to preserve the original constant pool
         */
        static Option constantPoolSharing(boolean b) { return new Options.OptionValue(Options.Key.CP_SHARING, b); }

        /**
         * {@return an option describing whether or not to automatically rewrite
         * short jumps to long when necessary}
         * Default is to automatically rewrite jump instructions.
         * @param b whether or not to automatically rewrite short jumps to long when necessary
         */
        static Option fixShortJumps(boolean b) { return new Options.OptionValue(Options.Key.FIX_SHORT_JUMPS, b); }

        /**
         * {@return an option describing whether or not to patch out unreachable code}
         * Default is to automatically patch out unreachable code with NOPs.
         * @param b whether or not to automatically patch out unreachable code
         */
        static Option patchDeadCode(boolean b) { return new Options.OptionValue(Options.Key.PATCH_DEAD_CODE, b); }

        /**
         * {@return an option describing the class hierarchy resolver to use when
         * generating stack maps}
         * @param r the resolver
         */
        static Option classHierarchyResolver(ClassHierarchyResolver r) { return new Options.OptionValue(Options.Key.HIERARCHY_RESOLVER, r); }

        /**
         * {@return an option describing attribute mappers for custom attributes}
         * Default is only to process standard attributes.
         * @param r a function mapping attribute names to attribute mappers
         */
        static Option attributeMapper(Function<Utf8Entry, AttributeMapper<?>> r) { return new Options.OptionValue(Options.Key.ATTRIBUTE_MAPPER, r); }

        /**
         * {@return an option describing whether or not to filter unresolved labels}
         * Default is to throw IllegalStateException when any {@link ExceptionCatch},
         * {@link LocalVariableInfo}, {@link LocalVariableTypeInfo}, or {@link CharacterRangeInfo}
         * reference to unresolved {@link Label} during bytecode serialization.
         * Setting this option to true filters the above elements instead.
         * @param b whether or not to automatically patch out unreachable code
         */
        static Option filterDeadLabels(boolean b) { return new Options.OptionValue(Options.Key.FILTER_DEAD_LABELS, b); }
    }

    /**
     * Parse a classfile into a {@link ClassModel}.
     * @param bytes the bytes of the classfile
     * @param options the desired processing options
     * @return the class model
     */
    public static ClassModel parse(byte[] bytes, Option... options) {
        Collection<Option> os = (options == null || options.length == 0)
                                   ? Collections.emptyList()
                                   : List.of(options);
        return new ClassImpl(bytes, os);
    }

    /**
     * Parse a classfile into a {@link ClassModel}.
     * @param path the path to the classfile
     * @param options the desired processing options
     * @return the class model
     */
    public static ClassModel parse(Path path, Option... options) throws IOException {
        return parse(Files.readAllBytes(path), options);
    }

    /**
     * Build a classfile into a byte array.
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    public static byte[] build(ClassDesc thisClass,
                               Consumer<ClassBuilder> handler) {
        return build(thisClass, Collections.emptySet(), handler);
    }

    /**
     * Build a classfile into a byte array.
     * @param thisClass the name of the class to build
     * @param options the desired processing options
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    public static byte[] build(ClassDesc thisClass,
                               Collection<Option> options,
                               Consumer<? super ClassBuilder> handler) {
        ConstantPoolBuilder pool = ConstantPoolBuilder.of(options);
        return build(pool.classEntry(thisClass), pool, handler);
    }

    /**
     * Build a classfile into a byte array using the provided constant pool
     * builder (which encapsulates classfile processing options.)
     *
     * @param thisClassEntry the name of the class to build
     * @param constantPool the constant pool builder
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    public static byte[] build(ClassEntry thisClassEntry,
                               ConstantPoolBuilder constantPool,
                               Consumer<? super ClassBuilder> handler) {
        thisClassEntry = AbstractPoolEntry.maybeClone(constantPool, thisClassEntry);
        DirectClassBuilder builder = new DirectClassBuilder((SplitConstantPool)constantPool, thisClassEntry);
        handler.accept(builder);
        return builder.build();
    }

    /**
     * Build a classfile into a file.
     * @param path the path to the file to write
     * @param thisClass the name of the class to build
     * @param handler a handler that receives a {@link ClassBuilder}
     */
    public static void buildTo(Path path,
                               ClassDesc thisClass,
                               Consumer<ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClass, Collections.emptySet(), handler));
    }

    /**
     * Build a classfile into a file.
     * @param path the path to the file to write
     * @param thisClass the name of the class to build
     * @param options the desired processing options
     * @param handler a handler that receives a {@link ClassBuilder}
     */
    public static void buildTo(Path path,
                               ClassDesc thisClass,
                               Collection<Option> options,
                               Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, build(thisClass, options, handler));
    }

    /**
     * Build a module descriptor into a byte array.
     * @param moduleAttribute the {@code Module} attribute
     * @return the classfile bytes
     */
    public static byte[] buildModule(ModuleAttribute moduleAttribute) {
        return buildModule(moduleAttribute, List.of(), clb -> {});
    }

    /**
     * Build a module descriptor into a byte array.
     * @param moduleAttribute the {@code Module} attribute
     * @param packages additional module packages
     * @return the classfile bytes
     */
    public static byte[] buildModule(ModuleAttribute moduleAttribute,
                                     List<PackageDesc> packages) {
        return buildModule(moduleAttribute, packages, clb -> {});
    }

    /**
     * Build a module descriptor into a byte array.
     * @param moduleAttribute the {@code Module} attribute
     * @param packages additional module packages
     * @param handler a handler that receives a {@link ClassBuilder}
     * @return the classfile bytes
     */
    public static byte[] buildModule(ModuleAttribute moduleAttribute,
                                     List<PackageDesc> packages,
                                     Consumer<? super ClassBuilder> handler) {
        return build(ClassDesc.of("module-info"), clb -> {
            clb.withFlags(AccessFlag.MODULE);
            clb.with(moduleAttribute);
            if (!packages.isEmpty()) {
                var cp = clb.constantPool();
                var allPackages = new LinkedHashSet<PackageEntry>();
                for (var exp : moduleAttribute.exports()) allPackages.add(AbstractPoolEntry.maybeClone(cp, exp.exportedPackage()));
                for (var opn : moduleAttribute.opens()) allPackages.add(AbstractPoolEntry.maybeClone(cp, opn.openedPackage()));
                boolean emitMPA = false;
                for (var p : packages)
                    emitMPA |= allPackages.add(cp.packageEntry(p));
                if(emitMPA)
                    clb.with(new UnboundAttribute.UnboundModulePackagesAttribute(allPackages));
            }
            handler.accept(clb);
        });
    }

    /**
     * Build a module descriptor into a file.
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     */
    public static void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute) throws IOException {
        buildModuleTo(path, moduleAttribute, List.of(), clb -> {});
    }

    /**
     * Build a module descriptor into a file.
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @param packages additional module packages
     */
    public static void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute,
                                     List<PackageDesc> packages) throws IOException {
        buildModuleTo(path, moduleAttribute, packages, clb -> {});
    }

    /**
     * Build a module descriptor into a file.
     * @param path the file to write
     * @param moduleAttribute the {@code Module} attribute
     * @param packages additional module packages
     * @param handler a handler that receives a {@link ClassBuilder}
     */
    public static void buildModuleTo(Path path,
                                     ModuleAttribute moduleAttribute,
                                     List<PackageDesc> packages,
                                     Consumer<? super ClassBuilder> handler) throws IOException {
        Files.write(path, buildModule(moduleAttribute, packages, handler));
    }

    public static final int MAGIC_NUMBER = 0xCAFEBABE;

    public static final int NOP             = 0;
    public static final int ACONST_NULL     = 1;
    public static final int ICONST_M1       = 2;
    public static final int ICONST_0        = 3;
    public static final int ICONST_1        = 4;
    public static final int ICONST_2        = 5;
    public static final int ICONST_3        = 6;
    public static final int ICONST_4        = 7;
    public static final int ICONST_5        = 8;
    public static final int LCONST_0        = 9;
    public static final int LCONST_1        = 10;
    public static final int FCONST_0        = 11;
    public static final int FCONST_1        = 12;
    public static final int FCONST_2        = 13;
    public static final int DCONST_0        = 14;
    public static final int DCONST_1        = 15;
    public static final int BIPUSH          = 16;
    public static final int SIPUSH          = 17;
    public static final int LDC             = 18;
    public static final int LDC_W           = 19;
    public static final int LDC2_W          = 20;
    public static final int ILOAD           = 21;
    public static final int LLOAD           = 22;
    public static final int FLOAD           = 23;
    public static final int DLOAD           = 24;
    public static final int ALOAD           = 25;
    public static final int ILOAD_0         = 26;
    public static final int ILOAD_1         = 27;
    public static final int ILOAD_2         = 28;
    public static final int ILOAD_3         = 29;
    public static final int LLOAD_0         = 30;
    public static final int LLOAD_1         = 31;
    public static final int LLOAD_2         = 32;
    public static final int LLOAD_3         = 33;
    public static final int FLOAD_0         = 34;
    public static final int FLOAD_1         = 35;
    public static final int FLOAD_2         = 36;
    public static final int FLOAD_3         = 37;
    public static final int DLOAD_0         = 38;
    public static final int DLOAD_1         = 39;
    public static final int DLOAD_2         = 40;
    public static final int DLOAD_3         = 41;
    public static final int ALOAD_0         = 42;
    public static final int ALOAD_1         = 43;
    public static final int ALOAD_2         = 44;
    public static final int ALOAD_3         = 45;
    public static final int IALOAD          = 46;
    public static final int LALOAD          = 47;
    public static final int FALOAD          = 48;
    public static final int DALOAD          = 49;
    public static final int AALOAD          = 50;
    public static final int BALOAD          = 51;
    public static final int CALOAD          = 52;
    public static final int SALOAD          = 53;
    public static final int ISTORE          = 54;
    public static final int LSTORE          = 55;
    public static final int FSTORE          = 56;
    public static final int DSTORE          = 57;
    public static final int ASTORE          = 58;
    public static final int ISTORE_0        = 59;
    public static final int ISTORE_1        = 60;
    public static final int ISTORE_2        = 61;
    public static final int ISTORE_3        = 62;
    public static final int LSTORE_0        = 63;
    public static final int LSTORE_1        = 64;
    public static final int LSTORE_2        = 65;
    public static final int LSTORE_3        = 66;
    public static final int FSTORE_0        = 67;
    public static final int FSTORE_1        = 68;
    public static final int FSTORE_2        = 69;
    public static final int FSTORE_3        = 70;
    public static final int DSTORE_0        = 71;
    public static final int DSTORE_1        = 72;
    public static final int DSTORE_2        = 73;
    public static final int DSTORE_3        = 74;
    public static final int ASTORE_0        = 75;
    public static final int ASTORE_1        = 76;
    public static final int ASTORE_2        = 77;
    public static final int ASTORE_3        = 78;
    public static final int IASTORE         = 79;
    public static final int LASTORE         = 80;
    public static final int FASTORE         = 81;
    public static final int DASTORE         = 82;
    public static final int AASTORE         = 83;
    public static final int BASTORE         = 84;
    public static final int CASTORE         = 85;
    public static final int SASTORE         = 86;
    public static final int POP             = 87;
    public static final int POP2            = 88;
    public static final int DUP             = 89;
    public static final int DUP_X1          = 90;
    public static final int DUP_X2          = 91;
    public static final int DUP2            = 92;
    public static final int DUP2_X1         = 93;
    public static final int DUP2_X2         = 94;
    public static final int SWAP            = 95;
    public static final int IADD            = 96;
    public static final int LADD            = 97;
    public static final int FADD            = 98;
    public static final int DADD            = 99;
    public static final int ISUB            = 100;
    public static final int LSUB            = 101;
    public static final int FSUB            = 102;
    public static final int DSUB            = 103;
    public static final int IMUL            = 104;
    public static final int LMUL            = 105;
    public static final int FMUL            = 106;
    public static final int DMUL            = 107;
    public static final int IDIV            = 108;
    public static final int LDIV            = 109;
    public static final int FDIV            = 110;
    public static final int DDIV            = 111;
    public static final int IREM            = 112;
    public static final int LREM            = 113;
    public static final int FREM            = 114;
    public static final int DREM            = 115;
    public static final int INEG            = 116;
    public static final int LNEG            = 117;
    public static final int FNEG            = 118;
    public static final int DNEG            = 119;
    public static final int ISHL            = 120;
    public static final int LSHL            = 121;
    public static final int ISHR            = 122;
    public static final int LSHR            = 123;
    public static final int IUSHR           = 124;
    public static final int LUSHR           = 125;
    public static final int IAND            = 126;
    public static final int LAND            = 127;
    public static final int IOR             = 128;
    public static final int LOR             = 129;
    public static final int IXOR            = 130;
    public static final int LXOR            = 131;
    public static final int IINC            = 132;
    public static final int I2L             = 133;
    public static final int I2F             = 134;
    public static final int I2D             = 135;
    public static final int L2I             = 136;
    public static final int L2F             = 137;
    public static final int L2D             = 138;
    public static final int F2I             = 139;
    public static final int F2L             = 140;
    public static final int F2D             = 141;
    public static final int D2I             = 142;
    public static final int D2L             = 143;
    public static final int D2F             = 144;
    public static final int I2B             = 145;
    public static final int I2C             = 146;
    public static final int I2S             = 147;
    public static final int LCMP            = 148;
    public static final int FCMPL           = 149;
    public static final int FCMPG           = 150;
    public static final int DCMPL           = 151;
    public static final int DCMPG           = 152;
    public static final int IFEQ            = 153;
    public static final int IFNE            = 154;
    public static final int IFLT            = 155;
    public static final int IFGE            = 156;
    public static final int IFGT            = 157;
    public static final int IFLE            = 158;
    public static final int IF_ICMPEQ       = 159;
    public static final int IF_ICMPNE       = 160;
    public static final int IF_ICMPLT       = 161;
    public static final int IF_ICMPGE       = 162;
    public static final int IF_ICMPGT       = 163;
    public static final int IF_ICMPLE       = 164;
    public static final int IF_ACMPEQ       = 165;
    public static final int IF_ACMPNE       = 166;
    public static final int GOTO            = 167;
    public static final int JSR             = 168;
    public static final int RET             = 169;
    public static final int TABLESWITCH     = 170;
    public static final int LOOKUPSWITCH    = 171;
    public static final int IRETURN         = 172;
    public static final int LRETURN         = 173;
    public static final int FRETURN         = 174;
    public static final int DRETURN         = 175;
    public static final int ARETURN         = 176;
    public static final int RETURN          = 177;
    public static final int GETSTATIC       = 178;
    public static final int PUTSTATIC       = 179;
    public static final int GETFIELD        = 180;
    public static final int PUTFIELD        = 181;
    public static final int INVOKEVIRTUAL   = 182;
    public static final int INVOKESPECIAL   = 183;
    public static final int INVOKESTATIC    = 184;
    public static final int INVOKEINTERFACE = 185;
    public static final int INVOKEDYNAMIC   = 186;
    public static final int NEW             = 187;
    public static final int NEWARRAY        = 188;
    public static final int ANEWARRAY       = 189;
    public static final int ARRAYLENGTH     = 190;
    public static final int ATHROW          = 191;
    public static final int CHECKCAST       = 192;
    public static final int INSTANCEOF      = 193;
    public static final int MONITORENTER    = 194;
    public static final int MONITOREXIT     = 195;
    public static final int WIDE            = 196;
    public static final int MULTIANEWARRAY  = 197;
    public static final int IFNULL          = 198;
    public static final int IFNONNULL       = 199;
    public static final int GOTO_W          = 200;
    public static final int JSR_W           = 201;

    public static final int ACC_PUBLIC = 0x0001;
    public static final int ACC_PROTECTED = 0x0004;
    public static final int ACC_PRIVATE = 0x0002;
    public static final int ACC_INTERFACE = 0x0200;
    public static final int ACC_ENUM = 0x4000;
    public static final int ACC_ANNOTATION = 0x2000;
    public static final int ACC_SUPER = 0x0020;
    public static final int ACC_ABSTRACT = 0x0400;
    public static final int ACC_VOLATILE = 0x0040;
    public static final int ACC_TRANSIENT = 0x0080;
    public static final int ACC_SYNTHETIC = 0x1000;
    public static final int ACC_STATIC = 0x0008;
    public static final int ACC_FINAL = 0x0010;
    public static final int ACC_SYNCHRONIZED = 0x0020;
    public static final int ACC_BRIDGE = 0x0040;
    public static final int ACC_VARARGS = 0x0080;
    public static final int ACC_NATIVE = 0x0100;
    public static final int ACC_STRICT = 0x0800;
    public static final int ACC_MODULE = 0x8000;
    public static final int ACC_OPEN = 0x20;
    public static final int ACC_MANDATED = 0x8000;
    public static final int ACC_TRANSITIVE = 0x20;
    public static final int ACC_STATIC_PHASE = 0x40;

    public static final int CRT_STATEMENT       = 0x0001;
    public static final int CRT_BLOCK           = 0x0002;
    public static final int CRT_ASSIGNMENT      = 0x0004;
    public static final int CRT_FLOW_CONTROLLER = 0x0008;
    public static final int CRT_FLOW_TARGET     = 0x0010;
    public static final int CRT_INVOKE          = 0x0020;
    public static final int CRT_CREATE          = 0x0040;
    public static final int CRT_BRANCH_TRUE     = 0x0080;
    public static final int CRT_BRANCH_FALSE    = 0x0100;

    public static final int TAG_CLASS = 7;
    public static final int TAG_CONSTANTDYNAMIC = 17;
    public static final int TAG_DOUBLE = 6;
    public static final int TAG_FIELDREF = 9;
    public static final int TAG_FLOAT = 4;
    public static final int TAG_INTEGER = 3;
    public static final int TAG_INTERFACEMETHODREF = 11;
    public static final int TAG_INVOKEDYNAMIC = 18;
    public static final int TAG_LONG = 5;
    public static final int TAG_METHODHANDLE = 15;
    public static final int TAG_METHODREF = 10;
    public static final int TAG_METHODTYPE = 16;
    public static final int TAG_MODULE = 19;
    public static final int TAG_NAMEANDTYPE = 12;
    public static final int TAG_PACKAGE = 20;
    public static final int TAG_STRING = 8;
    public static final int TAG_UNICODE = 2;
    public static final int TAG_UTF8 = 1;

    //type annotations
    public static final int TAT_CLASS_TYPE_PARAMETER = 0x00;
    public static final int TAT_METHOD_TYPE_PARAMETER = 0x01;
    public static final int TAT_CLASS_EXTENDS = 0x10;
    public static final int TAT_CLASS_TYPE_PARAMETER_BOUND = 0x11;
    public static final int TAT_METHOD_TYPE_PARAMETER_BOUND = 0x12;
    public static final int TAT_FIELD = 0x13;
    public static final int TAT_METHOD_RETURN = 0x14;
    public static final int TAT_METHOD_RECEIVER = 0x15;
    public static final int TAT_METHOD_FORMAL_PARAMETER = 0x16;
    public static final int TAT_THROWS = 0x17;
    public static final int TAT_LOCAL_VARIABLE = 0x40;
    public static final int TAT_RESOURCE_VARIABLE = 0x41;
    public static final int TAT_EXCEPTION_PARAMETER = 0x42;
    public static final int TAT_INSTANCEOF = 0x43;
    public static final int TAT_NEW = 0x44;
    public static final int TAT_CONSTRUCTOR_REFERENCE = 0x45;
    public static final int TAT_METHOD_REFERENCE = 0x46;
    public static final int TAT_CAST = 0x47;
    public static final int TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT = 0x48;
    public static final int TAT_METHOD_INVOCATION_TYPE_ARGUMENT = 0x49;
    public static final int TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT = 0x4A;
    public static final int TAT_METHOD_REFERENCE_TYPE_ARGUMENT = 0x4B;

    //stackmap verification types
    public static final int VT_TOP = 0;
    public static final int VT_INTEGER = 1;
    public static final int VT_FLOAT = 2;
    public static final int VT_DOUBLE = 3;
    public static final int VT_LONG = 4;
    public static final int VT_NULL = 5;
    public static final int VT_UNINITIALIZED_THIS = 6;
    public static final int VT_OBJECT = 7;
    public static final int VT_UNINITIALIZED = 8;

    public static final int DEFAULT_CLASS_FLAGS = ACC_PUBLIC;

    public static final int JAVA_1_VERSION = 45;
    public static final int JAVA_2_VERSION = 46;
    public static final int JAVA_3_VERSION = 47;
    public static final int JAVA_4_VERSION = 48;
    public static final int JAVA_5_VERSION = 49;
    public static final int JAVA_6_VERSION = 50;
    public static final int JAVA_7_VERSION = 51;
    public static final int JAVA_8_VERSION = 52;
    public static final int JAVA_9_VERSION = 53;
    public static final int JAVA_10_VERSION = 54;
    public static final int JAVA_11_VERSION = 55;
    public static final int JAVA_12_VERSION = 56;
    public static final int JAVA_13_VERSION = 57;
    public static final int JAVA_14_VERSION = 58;
    public static final int JAVA_15_VERSION = 59;
    public static final int JAVA_16_VERSION = 60;
    public static final int JAVA_17_VERSION = 61;
    public static final int JAVA_18_VERSION = 62;
    public static final int JAVA_19_VERSION = 63;
    public static final int JAVA_20_VERSION = 64;
    public static final int JAVA_21_VERSION = 65;

    public static final int LATEST_MAJOR_VERSION = JAVA_21_VERSION;
    public static final int LATEST_MINOR_VERSION = 0;
    public static final int PREVIEW_MINOR_VERSION = -1;

}
