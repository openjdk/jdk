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

/*
 * @test
 * @summary Regression test for JVMTIs tag map value class instance substitutability test
 * @bug 8386824 8392964
 * @requires vm.jvmti
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueTagSubstitutabilityTest ValueTagSubstitutabilityTest
 */

public class ValueTagSubstitutabilityTest {
    private static void log(String msg) {
        System.out.println(msg);
        System.out.flush();
    }

    static value record FloatValue(float value) {}
    static value record DoubleValue(double value) {}

    static abstract value class Base {
        private int base;

        Base(int base) {
            this.base = base;
        }
    }

    static abstract value class Middle extends Base {
        private int middle;

        Middle(int base, int middle) {
            this.middle = middle;
            super(base);
        }
    }

    static value class Leaf extends Middle {
        private int leaf;

        Leaf(int base, int middle, int leaf) {
            this.leaf = leaf;
            super(base, middle);
        }
    }

    private static native void setTag(Object object, long tag);
    private static native long getTag(Object object);

    private static int failures;

    private static void checkTag(String description, Object object, long expected) {
        final long actual = getTag(object);
        if (actual != expected) {
            log("checkTag: Failed: " + description + ": expected tag " + expected + ", got " + actual);
            failures++;
        }
    }

    private static void checkPair(String description, Object first, Object second, boolean substitutable) {
        log("checkPair: Testing " + description);
        if ((first == second) != substitutable) {
            throw new RuntimeException(description + ": unexpected Java substitutability");
        }

        try {
            // First tagged with 1. If they are substitutable, lookup on second
            // expects to see this tag, otherwise it should not be tagged / 0.
            setTag(first, 1);
            checkTag(description + " / original", first, 1);
            checkTag(description + " / lookup", second, substitutable ? 1 : 0);

            // Second tagged with 2. If they are substitutable, the first tag 1
            // should be overwritten, and lookup on first should produce 2,
            // otherwise first should keep its tag 1.
            setTag(second, 2);
            checkTag(description + " / first after update", first, substitutable ? 2 : 1);
            checkTag(description + " / second after update", second, 2);

            // This propery should work for removals as well.
            setTag(second, 0);
            checkTag(description + " / first after removal", first, substitutable ? 0 : 1);
            checkTag(description + " / second after removal", second, 0);
        } finally {
            setTag(first, 0);
            setTag(second, 0);
        }
    }

    private static void testFloat() {
        checkPair("equal finite floats", new FloatValue(1.0f), new FloatValue(1.0f), true);
        checkPair("different finite floats", new FloatValue(1.0f), new FloatValue(2.0f), false);
        checkPair("signed float zeros", new FloatValue(+0.0f), new FloatValue(-0.0f), false);
        checkPair("identical float NaNs", new FloatValue(Float.NaN), new FloatValue(Float.NaN), true);

        // Use quiet NaNs so merely loading a signaling NaN cannot change its bits.
        final float nan1 = Float.intBitsToFloat(0x7fc00001);
        final float nan2 = Float.intBitsToFloat(0x7fc00002);
        checkPair("identical float NaN payloads", new FloatValue(nan1), new FloatValue(nan1), true);
        checkPair("different float NaN payloads", new FloatValue(nan1), new FloatValue(nan2), false);
    }

    private static void testDouble() {
        checkPair("equal finite doubles", new DoubleValue(1.0), new DoubleValue(1.0), true);
        checkPair("different finite doubles", new DoubleValue(1.0), new DoubleValue(2.0), false);
        checkPair("signed double zeros", new DoubleValue(+0.0), new DoubleValue(-0.0), false);
        checkPair("identical double NaNs", new DoubleValue(Double.NaN), new DoubleValue(Double.NaN), true);

        final double nan1 = Double.longBitsToDouble(0x7ff8000000000001L);
        final double nan2 = Double.longBitsToDouble(0x7ff8000000000002L);
        checkPair("identical double NaN payloads", new DoubleValue(nan1), new DoubleValue(nan1), true);
        checkPair("different double NaN payloads", new DoubleValue(nan1), new DoubleValue(nan2), false);
    }

    private static void testInheritance() {
        checkPair("equal inherited fields", new Leaf(1, 2, 3), new Leaf(1, 2, 3), true);
        checkPair("different leaf field", new Leaf(1, 2, 3), new Leaf(1, 2, 4), false);
        checkPair("different middle field", new Leaf(1, 2, 3), new Leaf(1, 4, 3), false);
        checkPair("different base field", new Leaf(1, 2, 3), new Leaf(4, 2, 3), false);
    }

    public static void main(String[] args) {
        System.loadLibrary("ValueTagSubstitutabilityTest");

        testFloat();
        testDouble();
        testInheritance();

        if (failures != 0) {
            throw new RuntimeException(failures + " tag checks failed");
        }
    }
}
