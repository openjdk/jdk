/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.openjdk.bench.java.lang.foreign.pointers;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

public sealed abstract class NativeType<X> {

    public abstract MemoryLayout layout();

    public non-sealed static abstract class OfInt<X> extends NativeType<X> {
        public abstract ValueLayout.OfInt layout();
    }
    public non-sealed static abstract class OfDouble<X> extends NativeType<X> {
        public abstract ValueLayout.OfDouble layout();
    }

    private static final AddressLayout UNSAFE_ADDRESS = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));

    public final static class OfPointer<X> extends NativeType<X> {
        public AddressLayout layout() {
            return UNSAFE_ADDRESS;
        }
    }

    public non-sealed static abstract class OfStruct<X> extends NativeType<X> {
        public abstract GroupLayout layout();
        public abstract X make(Pointer<X> ptr);
    }

    public static final OfInt<Integer> C_INT = new OfInt<>() {
        @Override
        public ValueLayout.OfInt layout() {
            return ValueLayout.JAVA_INT;
        }
    };

    public static final OfDouble<Double> C_DOUBLE = new OfDouble<>() {
        @Override
        public ValueLayout.OfDouble layout() {
            return ValueLayout.JAVA_DOUBLE;
        }
    };

    @SuppressWarnings("unchecked")
    final static OfPointer C_VOID_PTR = new OfPointer();

    @SuppressWarnings("unchecked")
    public static final OfPointer<Pointer<Integer>> C_INT_PTR = NativeType.C_VOID_PTR;
    @SuppressWarnings("unchecked")
    public static final OfPointer<Pointer<Double>> C_DOUBLE_PTR = NativeType.C_VOID_PTR;



    @SuppressWarnings("unchecked")
    public static <Z> OfPointer<Pointer<Z>> ptr(NativeType<Z> type) {
        return NativeType.C_VOID_PTR;
    }
}
