/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Arrays;

import static java.lang.classfile.ClassFile.*;

/**
 * An interface to obtain field properties for direct class builders.
 * Required to filter strict instance fields for stack map generation.
 * Public for benchmark access.
 */
public sealed interface WritableField extends Util.Writable
        permits FieldImpl, DirectFieldBuilder {
    Utf8Entry fieldName();
    Utf8Entry fieldType();
    int fieldFlags();

    static WritableField.UnsetField[] filterStrictInstanceFields(ConstantPoolBuilder cpb, WritableField[] array, int count) {
        // assume there's no toctou for trusted incoming array
        int size = 0;
        for (int i = 0; i < count; i++) {
            var field = array[i];
            if ((field.fieldFlags() & (ACC_STATIC | ACC_STRICT_INIT)) == ACC_STRICT_INIT) {
                size++;
            }
        }
        if (size == 0)
            return UnsetField.EMPTY_ARRAY;
        UnsetField[] ret = new UnsetField[size];
        int j = 0;
        for (int i = 0; i < count; i++) {
            var field = array[i];
            if ((field.fieldFlags() & (ACC_STATIC | ACC_STRICT_INIT)) == ACC_STRICT_INIT) {
                ret[j++] = new UnsetField(AbstractPoolEntry.maybeClone(cpb, field.fieldName()),
                        AbstractPoolEntry.maybeClone(cpb, field.fieldType()));
            }
        }
        assert j == size : "toctou: " + j + " != " + size;
        Arrays.sort(ret);
        return ret;
    }

    // The captured information of unset fields, pool entries localized to class writing context
    // avoid creating NAT until we need to write the fields to stack maps
    record UnsetField(Utf8Entry name, Utf8Entry type) implements Comparable<UnsetField> {
        public UnsetField {
            assert Util.checkConstantPoolsCompatible(name.constantPool(), type.constantPool());
        }
        public static final UnsetField[] EMPTY_ARRAY = new UnsetField[0];

        public static UnsetField[] copyArray(UnsetField[] incoming, int resultLen) {
            assert resultLen <= incoming.length : resultLen + " > " + incoming.length;
            return resultLen == 0 ? EMPTY_ARRAY : Arrays.copyOf(incoming, resultLen, UnsetField[].class);
        }

        public static boolean matches(UnsetField[] one, int sizeOne, UnsetField[] two, int sizeTwo) {
            if (sizeOne != sizeTwo)
                return false;
            for (int i = 0; i < sizeOne; i++) {
                if (!one[i].equals(two[i])) {
                    return false;
                }
            }
            return true;
        }

        // Warning: inconsistent with equals (which uses UTF8 object equality)
        @Override
        public int compareTo(UnsetField o) {
            assert Util.checkConstantPoolsCompatible(name.constantPool(), o.name.constantPool());
            var ret = Integer.compare(name.index(), o.name.index());
            if (ret != 0)
                return ret;
            return Integer.compare(type.index(), o.type.index());
        }
    }
}
