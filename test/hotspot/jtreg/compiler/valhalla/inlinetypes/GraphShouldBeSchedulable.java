/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary We need to make sure to have a CheckCastPP under the initial value in ValueClass.newNull.*AtomicArray. If missing,
 *          a load of the initial value of the array may find an object with an imprecise type (initVal1 in this case, that is
 *          an Object), potentially leading to wrong alias indices.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+UnlockExperimentalVMOptions -Xbatch
 *                   -XX:-UseNullableAtomicValueFlattening -XX:-UseNullFreeAtomicValueFlattening -XX:+UseNullFreeNonAtomicValueFlattening
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:-DoEscapeAnalysis -XX:+AlwaysIncrementalInline
 *                   ${test.main.class}
 */

public class GraphShouldBeSchedulable {
    static value class TwoBytes {
        byte b1;
        byte b2;

        public TwoBytes(byte b1, byte b2) {
            this.b1 = b1;
            this.b2 = b2;
        }

        static final TwoBytes DEFAULT = new TwoBytes((byte)0, (byte)0);
    }

    static final TwoBytes CANARY1 = new TwoBytes((byte)42, (byte)42);
    // We hide the type of the initial value so that C2 can only tell is a load of type Object.
    static Object initVal1 = CANARY1;

    public static void main(String[] args) {
        for (int i = -50_000; i < 50_000; ++i) {
            TwoBytes val1 = new TwoBytes((byte)i, (byte)(i + 1));
            TwoBytes[] nullFreeAtomicArray1 = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, 3, TwoBytes.DEFAULT);
            nullFreeAtomicArray1[1] = val1;
            // Here, initVal1 is a load of type Object. Now, with a CheckCastPP under.
            TwoBytes[] nullFreeAtomicArray2 = (TwoBytes[])ValueClass.newNullRestrictedAtomicArray(TwoBytes.class, 3, initVal1);
            Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray2), false);
            // Here, the access is simplified into the initial value: we need to make sure we find the CheckCastPP otherwise
            // the fields b1 and b2 are not recognized as strict final fields, and wrong precedence edges are added during GCM.
            Asserts.assertEquals(nullFreeAtomicArray2[1], CANARY1);
        }
    }
}

