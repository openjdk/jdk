/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.runtime;
import jdk.internal.misc.Unsafe;

/**
 * Implementation for Object::equals and Object::hashCode for value objects.
 *
 * ValueObjectMethods::isSubstitutable and valueObjectHashCode are
 * private entry points called by VM.
 */
final class ValueObjectMethods {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final boolean VERBOSE =
            System.getProperty("value.bsm.debug") != null;

    private ValueObjectMethods() {
    }

    /**
     * Return whether two value objects of the same class are statewise equivalent,
     * as used by {@code ==} operator.
     * The fields to compare are determined by Unsafe.getFieldMap.
     * This method is called by the JVM.
     *
     * @param a a value class instance, non-null
     * @param b a value class instance of the same class as {@code a}, non-null
     * @return whether these two value objects are statewise equivalent
     */
    private static boolean isSubstitutable(Object a, Object b) {
        if (VERBOSE) {
            System.out.println("isSubstitutable " + a + " vs " + b);
        }
        // This method assumes a and b are not null and they are both instances of the same value class
        final Unsafe U = UNSAFE;
        int[] map = U.getFieldMap(a.getClass());
        int nbNonRef = map[0];
        for (int i = 0; i < nbNonRef; i++) {
            int offset = map[i * 2 + 1];
            int size = map[i * 2 + 2];
            int nlong = size / 8;
            for (int j = 0; j < nlong; j++) {
                long la = U.getLong(a, offset);
                long lb = U.getLong(b, offset);
                if (la != lb) return false;
                offset += 8;
            }
            size -= nlong * 8;
            int nint = size / 4;
            for (int j = 0; j < nint; j++) {
                int ia = U.getInt(a, offset);
                int ib = U.getInt(b, offset);
                if (ia != ib) return false;
                offset += 4;
            }
            size -= nint * 4;
            int nshort = size / 2;
            for (int j = 0; j < nshort; j++) {
                short sa = U.getShort(a, offset);
                short sb = U.getShort(b, offset);
                if (sa != sb) return false;
                offset += 2;
            }
            size -= nshort * 2;
            for (int j = 0; j < size; j++) {
                byte ba = U.getByte(a, offset);
                byte bb = U.getByte(b, offset);
                if (ba != bb) return false;
                offset++;
            }
        }
        for (int i = nbNonRef * 2 + 1; i < map.length; i++) {
            int offset = map[i];
            Object oa = U.getReference(a, offset);
            Object ob = U.getReference(b, offset);
            if (oa != ob) return false;
        }
        return true;
    }

    /**
     * Return the identity hashCode of a value object.
     * Two statewise equivalent value objects produce the same hashCode.
     * This method is called by the JVM.
     *
     * The generated identity hash must be invariantly immutable.
     * We divide into two cases:
     *   1. No references: the identity hash is computed from the immutable
     *      fields, no matter when this is called the same identity hash code
     *      is expected.
     *   2. References: the above still applies, but the references' identity
     *      hash code must be used, the user overwriteable hashCode may change
     *      due to mutability.
     *
     * The hashCode is computed using Unsafe.getFieldMap.
     *
     * @param obj a value class instance, non-null
     * @return the hashCode of the object
     */
    private static int valueObjectHashCode(Object obj) {
        if (VERBOSE) {
            System.out.println("valueObjectHashCode: obj.getClass:" + obj.getClass().getName());
        }
        // This method assumes a is not null and is an instance of a value class
        Class<?> type = obj.getClass();
        final Unsafe U = UNSAFE;
        int[] map = U.getFieldMap(type);
        int result = System.identityHashCode(type);
        int nbNonRef = map[0];
        for (int i = 0; i < nbNonRef; i++) {
            int offset = map[i * 2 + 1];
            int size = map[i * 2 + 2];
            int nlong = size / 8;
            for (int j = 0; j < nlong; j++) {
                long la = U.getLong(obj, offset);
                result = 31 * result + (int) la;
                result = 31 * result + (int) (la >>> 32);
                offset += 8;
            }
            size -= nlong * 8;
            int nint = size / 4;
            for (int j = 0; j < nint; j++) {
                int ia = U.getInt(obj, offset);
                result = 31 * result + ia;
                offset += 4;
            }
            size -= nint * 4;
            int nshort = size / 2;
            for (int j = 0; j < nshort; j++) {
                short sa = U.getShort(obj, offset);
                result = 31 * result + sa;
                offset += 2;
            }
            size -= nshort * 2;
            for (int j = 0; j < size; j++) {
                byte ba = U.getByte(obj, offset);
                result = 31 * result + ba;
                offset++;
            }
        }
        for (int i = nbNonRef * 2 + 1; i < map.length; i++) {
            int offset = map[i];
            Object oa = U.getReference(obj, offset);
            result = 31 * result + System.identityHashCode(oa);
        }
        return result;
    }
}
