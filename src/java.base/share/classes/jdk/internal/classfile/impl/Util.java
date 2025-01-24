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
package jdk.internal.classfile.impl;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.components.ClassPrinter;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.lang.reflect.AccessFlag;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import jdk.internal.access.SharedSecrets;
import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static jdk.internal.constant.PrimitiveClassDescImpl.CD_double;
import static jdk.internal.constant.PrimitiveClassDescImpl.CD_long;
import static jdk.internal.constant.PrimitiveClassDescImpl.CD_void;

/**
 * Helper to create and manipulate type descriptors, where type descriptors are
 * represented as JVM type descriptor strings and symbols are represented as
 * name strings
 */
public class Util {

    private Util() {
    }

    public static <T> Consumer<Consumer<T>> writingAll(Iterable<T> container) {
        record ForEachConsumer<T>(Iterable<T> container) implements Consumer<Consumer<T>> {
            @Override
            public void accept(Consumer<T> consumer) {
                container.forEach(consumer);
            }
        }
        return new ForEachConsumer<>(container);
    }

    public static Consumer<MethodBuilder> buildingCode(Consumer<? super CodeBuilder> codeHandler) {
        record WithCodeMethodHandler(Consumer<? super CodeBuilder> codeHandler) implements Consumer<MethodBuilder> {
            @Override
            public void accept(MethodBuilder builder) {
                builder.withCode(codeHandler);
            }
        }
        return new WithCodeMethodHandler(codeHandler);
    }

    public static Consumer<FieldBuilder> buildingFlags(int flags) {
        record WithFlagFieldHandler(int flags) implements Consumer<FieldBuilder> {
            @Override
            public void accept(FieldBuilder builder) {
                builder.withFlags(flags);
            }
        }
        return new WithFlagFieldHandler(flags);
    }

    private static final int ATTRIBUTE_STABILITY_COUNT = AttributeMapper.AttributeStability.values().length;

    public static boolean isAttributeAllowed(final Attribute<?> attr,
                                             final ClassFileImpl context) {
        return attr instanceof BoundAttribute
                ? ATTRIBUTE_STABILITY_COUNT - attr.attributeMapper().stability().ordinal() > context.attributesProcessingOption().ordinal()
                : true;
    }

    public static int parameterSlots(MethodTypeDesc mDesc) {
        int count = mDesc.parameterCount();
        for (int i = count - 1; i >= 0; i--) {
            if (isDoubleSlot(mDesc.parameterType(i))) {
                count++;
            }
        }
        return count;
    }

    public static int[] parseParameterSlots(int flags, MethodTypeDesc mDesc) {
        int[] result = new int[mDesc.parameterCount()];
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < result.length; i++) {
            result[i] = count;
            count += paramSlotSize(mDesc.parameterType(i));
        }
        return result;
    }

    public static int maxLocals(int flags, MethodTypeDesc mDesc) {
        return parameterSlots(mDesc) + ((flags & ACC_STATIC) == 0 ? 1 : 0) ;
    }

    /**
     * Converts a descriptor of classes or interfaces into
     * a binary name. Rejects primitive types or arrays.
     * This is an inverse of {@link ClassDesc#of(String)}.
     */
    public static String toBinaryName(ClassDesc cd) {
        return toInternalName(cd).replace('/', '.');
    }

    public static String toInternalName(ClassDesc cd) {
        if (cd instanceof ClassOrInterfaceDescImpl coi) {
            return coi.internalName();
        }
        throw new IllegalArgumentException(cd.descriptorString());
    }

    public static ClassDesc toClassDesc(String classInternalNameOrArrayDesc) {
        return classInternalNameOrArrayDesc.charAt(0) == '['
                ? ClassDesc.ofDescriptor(classInternalNameOrArrayDesc)
                : ClassDesc.ofInternalName(classInternalNameOrArrayDesc);
    }

    public static<T, U> List<U> mappedList(List<? extends T> list, Function<T, U> mapper) {
        return new AbstractList<>() {
            @Override
            public U get(int index) {
                return mapper.apply(list.get(index));
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    public static List<ClassEntry> entryList(List<? extends ClassDesc> list) {
        var result = new Object[list.size()]; // null check
        for (int i = 0; i < result.length; i++) {
            result[i] = TemporaryConstantPool.INSTANCE.classEntry(list.get(i));
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(result);
    }

    public static List<ModuleEntry> moduleEntryList(List<? extends ModuleDesc> list) {
        var result = new Object[list.size()]; // null check
        for (int i = 0; i < result.length; i++) {
            result[i] = TemporaryConstantPool.INSTANCE.moduleEntry(TemporaryConstantPool.INSTANCE.utf8Entry(list.get(i).name()));
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(result);
    }

    public static void checkKind(Opcode op, Opcode.Kind k) {
        if (op.kind() != k)
            throw badOpcodeKindException(op, k);
    }

    public static IllegalArgumentException badOpcodeKindException(Opcode op, Opcode.Kind k) {
        return new IllegalArgumentException(
                String.format("Wrong opcode kind specified; found %s(%s), expected %s", op, op.kind(), k));
    }

    public static int flagsToBits(AccessFlag.Location location, Collection<AccessFlag> flags) {
        int i = 0;
        for (AccessFlag f : flags) {
            if (!f.locations().contains(location)) {
                throw new IllegalArgumentException("unexpected flag: " + f + " use in target location: " + location);
            }
            i |= f.mask();
        }
        return i;
    }

    public static int flagsToBits(AccessFlag.Location location, AccessFlag... flags) {
        int i = 0;
        for (AccessFlag f : flags) {
            if (!f.locations().contains(location)) {
                throw new IllegalArgumentException("unexpected flag: " + f + " use in target location: " + location);
            }
            i |= f.mask();
        }
        return i;
    }

    public static boolean has(AccessFlag.Location location, int flagsMask, AccessFlag flag) {
        return (flag.mask() & flagsMask) == flag.mask() && flag.locations().contains(location);
    }

    public static ClassDesc fieldTypeSymbol(Utf8Entry utf8) {
        return ((AbstractPoolEntry.Utf8EntryImpl) utf8).fieldTypeSymbol();
    }

    public static MethodTypeDesc methodTypeSymbol(Utf8Entry utf8) {
        return ((AbstractPoolEntry.Utf8EntryImpl) utf8).methodTypeSymbol();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Attribute<T>> void writeAttribute(BufWriterImpl writer, Attribute<?> attr) {
        if (attr instanceof CustomAttribute<?> ca) {
            var mapper = (AttributeMapper<T>) ca.attributeMapper();
            mapper.writeAttribute(writer, (T) ca);
        } else {
            assert attr instanceof BoundAttribute || attr instanceof UnboundAttribute;
            ((Writable) attr).writeTo(writer);
        }
    }

    @ForceInline
    public static void writeAttributes(BufWriterImpl buf, List<? extends Attribute<?>> list) {
        int size = list.size();
        buf.writeU2(size);
        for (int i = 0; i < size; i++) {
            writeAttribute(buf, list.get(i));
        }
    }

    @ForceInline
    static void writeList(BufWriterImpl buf, Writable[] array, int size) {
        buf.writeU2(size);
        for (int i = 0; i < size; i++) {
            array[i].writeTo(buf);
        }
    }

    public static int slotSize(ClassDesc desc) {
        return desc == CD_void ? 0 : isDoubleSlot(desc) ? 2 : 1;
    }

    public static int paramSlotSize(ClassDesc desc) {
        return isDoubleSlot(desc) ? 2 : 1;
    }

    public static boolean isDoubleSlot(ClassDesc desc) {
        return desc == CD_double || desc == CD_long;
    }

    public static void dumpMethod(SplitConstantPool cp,
                                  ClassDesc cls,
                                  String methodName,
                                  MethodTypeDesc methodDesc,
                                  int acc,
                                  RawBytecodeHelper.CodeRange bytecode,
                                  Consumer<String> dump) {

        // try to dump debug info about corrupted bytecode
        try {
            var cc = ClassFile.of();
            var clm = cc.parse(cc.build(cp.classEntry(cls), cp, clb ->
                    clb.withMethod(methodName, methodDesc, acc, mb ->
                            ((DirectMethodBuilder)mb).writeAttribute(new UnboundAttribute.AdHocAttribute<CodeAttribute>(Attributes.code()) {
                                @Override
                                public void writeBody(BufWriterImpl b) {
                                    b.writeU2U2(-1, -1);//max stack & locals
                                    b.writeInt(bytecode.length());
                                    b.writeBytes(bytecode.array(), 0, bytecode.length());
                                    b.writeU2U2(0, 0);//exception handlers & attributes
                                }

                                @Override
                                public Utf8Entry attributeName() {
                                    return cp.utf8Entry(Attributes.NAME_CODE);
                                }
                    }))));
            ClassPrinter.toYaml(clm.methods().get(0).code().get(), ClassPrinter.Verbosity.TRACE_ALL, dump);
        } catch (Error | Exception _) {
            // fallback to bytecode hex dump
            dumpBytesHex(dump, bytecode.array(), bytecode.length());
        }
    }

    public static void dumpBytesHex(Consumer<String> dump, byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            if (i % 16 == 0) {
                dump.accept("%n%04x:".formatted(i));
            }
            dump.accept(" %02x".formatted(bytes[i]));
        }
    }

    public static void writeListIndices(BufWriter writer, List<? extends PoolEntry> list) {
        writer.writeU2(list.size());
        for (PoolEntry info : list) {
            writer.writeIndex(info);
        }
    }

    public static boolean writeLocalVariable(BufWriterImpl buf, PseudoInstruction lvOrLvt) {
        return ((WritableLocalVariable) lvOrLvt).writeLocalTo(buf);
    }

    /**
     * A generic interface for objects to write to a
     * buf writer. Do not implement unless necessary,
     * as this writeTo is public, which can be troublesome.
     */
    interface Writable {
        void writeTo(BufWriterImpl writer);
    }

    interface WritableLocalVariable {
        boolean writeLocalTo(BufWriterImpl buf);
    }

    /**
     * Returns the hash code of a class or interface L descriptor given the internal name.
     */
    public static int descriptorStringHash(int length, int hash) {
        if (length > 0xffff)
            throw new IllegalArgumentException("String too long: ".concat(Integer.toString(length)));
        return 'L' * pow31(length + 1) + hash * 31 + ';';
    }

    // k is at most 65536, length of Utf8 entry + 1
    public static int pow31(int k) {
        int r = 1;
        // calculate the power contribution from index-th octal digit
        // from least to most significant (right to left)
        // e.g. decimal 26=octal 32, power(26)=powerOctal(2,0)*powerOctal(3,1)
        for (int i = 0; i < SIGNIFICANT_OCTAL_DIGITS; i++) {
            r *= powerOctal(k & 7, i);
            k >>= 3;
        }
        return r;
    }

    // The inverse of 31 in Z/2^32Z* modulo group, a * INVERSE_31 * 31 = a
    static final int INVERSE_31 = 0xbdef7bdf;

    // k is at most 65536 = octal 200000, only consider 6 octal digits
    // Note: 31 powers repeat beyond 1 << 27, only 9 octal digits matter
    static final int SIGNIFICANT_OCTAL_DIGITS = 6;

    // for base k, storage is k * log_k(N)=k/ln(k) * ln(N)
    // k = 2 or 4 is better for space at the cost of more multiplications
    /**
     * The code below is as if:
     * {@snippet lang=java :
     * int[] powers = new int[7 * SIGNIFICANT_OCTAL_DIGITS];
     *
     * for (int i = 1, k = 31; i <= 7; i++, k *= 31) {
     *    int t = powers[powersIndex(i, 0)] = k;
     *    for (int j = 1; j < SIGNIFICANT_OCTAL_DIGITS; j++) {
     *        t *= t;
     *        t *= t;
     *        t *= t;
     *        powers[powersIndex(i, j)] = t;
     *    }
     * }
     * }
     * This is converted to explicit initialization to avoid bootstrap overhead.
     * Validated in UtilTest.
     */
    static final @Stable int[] powers = new int[] {
            0x0000001f, 0x000003c1, 0x0000745f, 0x000e1781, 0x01b4d89f, 0x34e63b41, 0x67e12cdf,
            0x94446f01, 0x50a9de01, 0x84304d01, 0x7dd7bc01, 0x8ca02b01, 0xff899a01, 0x25940901,
            0x4dbf7801, 0xe3bef001, 0xc1fe6801, 0xe87de001, 0x573d5801, 0x0e3cd001, 0x0d7c4801,
            0x54fbc001, 0xb9f78001, 0x2ef34001, 0xb3ef0001, 0x48eac001, 0xede68001, 0xa2e24001,
            0x67de0001, 0xcfbc0001, 0x379a0001, 0x9f780001, 0x07560001, 0x6f340001, 0xd7120001,
            0x3ef00001, 0x7de00001, 0xbcd00001, 0xfbc00001, 0x3ab00001, 0x79a00001, 0xb8900001,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    static int powersIndex(int digit, int index) {
        return (digit - 1) + index * 7;
    }

    // (31 ^ digit) ^ (8 * index) = 31 ^ (digit * (8 ^ index))
    // digit: 0 - 7
    // index: 0 - SIGNIFICANT_OCTAL_DIGITS - 1
    private static int powerOctal(int digit, int index) {
        return digit == 0 ? 1 : powers[powersIndex(digit, index) & 0x3F]; // & 0x3F eliminates bound check
    }
}
