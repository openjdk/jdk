/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package runtime.valhalla.inlinetypes;

import java.util.ArrayList;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;


/*
 * @test AcmpTest
 * @summary Test acmp with various layouts of values
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm runtime.valhalla.inlinetypes.AcmpTest
 */
public class AcmpTest {
    static record TestCase(Object a, Object b, boolean equal) {}

    static value class IntValue {
        int value;

        public IntValue(int val) {
            value = val;
        }

        public String toString() {
            return "IntValue(" + value +
                ", bits=0x" + Integer.toHexString(value) + ")";
        }
    }

    static value class NestedValue {
        IntValue value;

        public NestedValue(IntValue val) {
            value = val;
        }

        public String toString() {
            return "NestedValue(" + value + ")";
        }
    }

    @LooselyConsistentValue
    static value record TwoLongs(long l0, long l1) {}

    static value record MyShort(short s) { }

    static abstract value class AbstractShort {
        MyShort s;

        AbstractShort(MyShort s) {
            this.s = s;
        }

        public String toString() {
            return "AbstractShort(" + s + ")";
        }
    }

    static value class NShortInt extends AbstractShort {
        int i;

        NShortInt(MyShort s, int i) {
            this.i = i;
            super(s);
        }

        public String toString() {
            return "NShortInt((" + s +
                "), (" + i + ", bits=0x" + Integer.toHexString(i) + "))";
        }
    }

    static value record ThreeBytes(byte b0, byte b1, byte b2) {}
    static value record ThreeBytesPlusOne(ThreeBytes tb, byte b) {}

    public static void main(String[] args) {

        final byte[] BYTE_EDGE_CASES = {
            (byte)0, (byte)-1, (byte)1,
            Byte.MIN_VALUE, Byte.MAX_VALUE
        };

        final short[] SHORT_EDGE_CASES = {
            (short)0, (short)-1, (short)1,
            Short.MIN_VALUE, Short.MAX_VALUE
        };

        final int[] INT_EDGE_CASES = {
            0, -1, 1,
            Integer.MIN_VALUE, Integer.MAX_VALUE
        };

        final long[] LONG_EDGE_CASES = {
            0, -1, 1,
            Long.MIN_VALUE, Long.MAX_VALUE
        };

        // Preparing NestValues test cases
        var nestedValueValues = new ArrayList<NestedValue>();
        for (int i : INT_EDGE_CASES) {
            nestedValueValues.add(new NestedValue(new IntValue(i)));
        }
        for (int i = 0; i < nestedValueValues.size(); i++) {
            for (int j = 0; j < nestedValueValues.size(); j++) {
                run(nestedValueValues.get(i), nestedValueValues.get(j), i == j);
            }
        }

        // Preparing TwoLongs test cases
        var twoLongsValues = new ArrayList<TwoLongs>();
        for (long l0 : LONG_EDGE_CASES) {
            for (long l1 : LONG_EDGE_CASES) {
                twoLongsValues.add(new TwoLongs(l0, l1));
            }
        }
        for (int i = 0; i < twoLongsValues.size(); i++) {
            for (int j = 0; j < twoLongsValues.size(); j++) {
                run(twoLongsValues.get(i), twoLongsValues.get(j), i == j);
            }
        }

        // Preparing NShortInt test cases
        var myShortValues = new ArrayList<MyShort>();
        for (short s : SHORT_EDGE_CASES) {
            myShortValues.add(new MyShort(s));
        }
        myShortValues.add(null);
        var nShortIntValues = new ArrayList<NShortInt>();
        for (MyShort ms : myShortValues) {
            for (int i : INT_EDGE_CASES) {
                nShortIntValues.add(new NShortInt(ms, i));
            }
        }
        for (int i = 0; i < nShortIntValues.size(); i++) {
            for (int j = 0; j < nShortIntValues.size(); j++) {
                run(nShortIntValues.get(i), nShortIntValues.get(j), i == j);
            }
        }

        // Preparing ThreeBytesPlusOne test cases
        var threeBytesValues = new ArrayList<ThreeBytes>();
        for (byte b0 : BYTE_EDGE_CASES) {
            for (byte b1 : BYTE_EDGE_CASES) {
                for (byte b2 : BYTE_EDGE_CASES) {
                    threeBytesValues.add(new ThreeBytes(b0, b1, b2));
                }
            }
        }
        threeBytesValues.add(null);
        var threeBytesPlusOneValues = new ArrayList<ThreeBytesPlusOne>();
        for (ThreeBytes v : threeBytesValues) {
            for (byte b : BYTE_EDGE_CASES) {
                threeBytesPlusOneValues.add(new ThreeBytesPlusOne(v, b));
            }
        }
        for (int i = 0; i < threeBytesPlusOneValues.size(); i++) {
            for (int j = 0; j < threeBytesPlusOneValues.size(); j++) {
                run(threeBytesPlusOneValues.get(i), threeBytesPlusOneValues.get(j), i == j);
            }
        }
    }

    static void run(Object a, Object b, boolean equal) {
        boolean res = a == b;
        if (res != equal) {
            throw new RuntimeException("Incorrect result'" + res + "' for " + a + " == " + b);
        }
    }
}
