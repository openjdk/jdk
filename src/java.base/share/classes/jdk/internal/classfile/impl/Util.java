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
package jdk.internal.classfile.impl;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.ModuleDesc;
import java.lang.reflect.AccessFlag;

import static jdk.internal.classfile.Classfile.ACC_STATIC;
import jdk.internal.access.SharedSecrets;
import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.Classfile;

/**
 * Helper to create and manipulate type descriptors, where type descriptors are
 * represented as JVM type descriptor strings and symbols are represented as
 * name strings
 */
public class Util {

    private Util() {
    }

    private static final int ATTRIBUTE_STABILITY_COUNT = AttributeMapper.AttributeStability.values().length;

    public static boolean isAttributeAllowed(final Attribute<?> attr,
                                             final Classfile.AttributesProcessingOption processingOption) {
        return attr instanceof BoundAttribute
                ? ATTRIBUTE_STABILITY_COUNT - attr.attributeMapper().stability().ordinal() > processingOption.ordinal()
                : true;
    }

    public static int parameterSlots(MethodTypeDesc mDesc) {
        int count = 0;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += slotSize(mDesc.parameterType(i));
        }
        return count;
    }

    public static int[] parseParameterSlots(int flags, MethodTypeDesc mDesc) {
        int[] result = new int[mDesc.parameterCount()];
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < result.length; i++) {
            result[i] = count;
            count += slotSize(mDesc.parameterType(i));
        }
        return result;
    }

    public static int maxLocals(int flags, MethodTypeDesc mDesc) {
        int count = ((flags & ACC_STATIC) != 0) ? 0 : 1;
        for (int i = 0; i < mDesc.parameterCount(); i++) {
            count += slotSize(mDesc.parameterType(i));
        }
        return count;
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
        var desc = cd.descriptorString();
        if (desc.charAt(0) == 'L')
            return desc.substring(1, desc.length() - 1);
        throw new IllegalArgumentException(desc);
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
            throw new IllegalArgumentException(
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

    public static ClassDesc fieldTypeSymbol(NameAndTypeEntry nat) {
        return ((AbstractPoolEntry.NameAndTypeEntryImpl)nat).fieldTypeSymbol();
    }

    public static MethodTypeDesc methodTypeSymbol(NameAndTypeEntry nat) {
        return ((AbstractPoolEntry.NameAndTypeEntryImpl)nat).methodTypeSymbol();
    }

    public static int slotSize(ClassDesc desc) {
        return switch (desc.descriptorString().charAt(0)) {
            case 'V' -> 0;
            case 'D','J' -> 2;
            default -> 1;
        };
    }

    public static boolean isDoubleSlot(ClassDesc desc) {
        char ch = desc.descriptorString().charAt(0);
        return ch == 'D' || ch == 'J';
    }
}
