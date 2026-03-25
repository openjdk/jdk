/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.*;
import jdk.internal.vm.annotation.LooselyConsistentValue;


/*
 * @test Ifacmp
 * @requires vm.gc == null
 * @summary if_acmpeq/ne bytecode test
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile --source 27 Ifacmp.java
 * @run main/othervm -Xms16m -Xmx16m -XX:+UseSerialGC runtime.valhalla.inlinetypes.Ifacmp
 */
public class Ifacmp {

    @LooselyConsistentValue
    static value class MyValue {
        int value;
        public MyValue(int v) { this.value = v; }
    };

    @LooselyConsistentValue
    static value class MyValue2 {
        int value;
        public MyValue2(int v) { this.value = v; }
    };

    boolean acmpModeInlineAlwaysFalse = false;

    Object aNull = null;
    Object bNull = null;

    Object aObject = new String("Hi");
    Object bObject = new String("Hi");

    Object aValue = new MyValue(1);
    Object bValue = new MyValue(1);
    Object cValue = new MyValue(0);
    Object aValue2 = new MyValue2(4711);

    Object[][] equalUseCases = {
        { aNull, bNull },
        { aObject, aObject },
        { aValue, bValue },
        { cValue, cValue },
        { aValue2, aValue2 }
    };

    int objectEqualsUseCases = 2; // Nof object equals use cases

    // Would just generate these fail case from the "equal set" above,
    // but to do so needs ==, so write out by hand it is...
    Object[][] notEqualUseCases = {
        { aNull, aObject },
        { aNull, bObject },
        { aNull, aValue },
        { aNull, bValue },
        { aNull, cValue },
        { aNull, aValue2 },
        { aObject, bObject },
        { aObject, aValue },
        { aObject, bValue },
        { aObject, cValue },
        { aObject, aValue2 },
        { bObject, cValue },
        { bObject, aValue2 },
        { aValue, cValue },
        { aValue, aValue2 },
    };

    public Ifacmp() { this(false); }
    public Ifacmp(boolean acmpModeInlineAlwaysFalse) {
        this.acmpModeInlineAlwaysFalse = acmpModeInlineAlwaysFalse;
        if (acmpModeInlineAlwaysFalse) {
            System.out.println("ifacmp always false for inline types");
        } else {
            System.out.println("ifacmp substitutability inline types");
        }
    }

    public void test() {
        testAllUseCases();
    }

    public void testUntilGc(int nofGc) {
        for (int i = 0; i < nofGc; i++) {
            System.out.println("GC num " + (i + 1));
            testUntilGc();
        }
    }

    public void testUntilGc() {
        Reference ref = new WeakReference<Object>(new Object(), new ReferenceQueue<>());
        do {
            test();
        } while (ref.get() != null);
    }

    public void testAllUseCases() {
        int useCase = 0;
        for (Object[] pair : equalUseCases) {
            useCase++;
            boolean equal = acmpModeInlineAlwaysFalse ? (useCase <= objectEqualsUseCases) : true;
            checkEqual(pair[0], pair[1], equal);
        }
        for (Object[] pair : notEqualUseCases) {
            checkEqual(pair[0], pair[1], false);
        }
        testLocalValues();
        testAlot();
    }

    public void testValues() {
        checkEqual(aValue, bValue, true);

        checkEqual(aValue, cValue, false);
        checkEqual(aValue, aValue2, false);
        checkEqual(aValue2, bValue, false);
        checkEqual(aValue2, cValue, false);
        testLocalValues();
    }

    public void testLocalValues() {
        // "aload + ifacmp" should be same as "aaload + ifamcp"
        // but let's be paranoid...
        MyValue a = new MyValue(11);
        MyValue b = new MyValue(11);
        MyValue c = a;
        MyValue a1 = new MyValue(7);
        MyValue2 a2 = new MyValue2(13);

        if (acmpModeInlineAlwaysFalse) {
            if (a == b) throw new RuntimeException("Always false fail " + a + " == " + b);
            if (a == c) throw new RuntimeException("Always false fail " + a + " == " + c);
        } else {
            if (a != b) throw new RuntimeException("Substitutability test failed" + a + " != " + b);
            if (a != c) throw new RuntimeException("Substitutability test failed");
        }
        if (a == a1) throw new RuntimeException();
        checkEqual(a, a2, false);
    }

    public void testAlot() {
        MyValue a = new MyValue(4711);
        Reference ref = new WeakReference<Object>(new Object(), new ReferenceQueue<>());
        do {
            for (int i = 0; i < 1000; i++) {
                MyValue b = new MyValue(4711);
                if (acmpModeInlineAlwaysFalse) {
                    if (a == b) throw new RuntimeException("Always false fail " + a + " == " + b);
                } else {
                    if (a != b) throw new RuntimeException("Substitutability test failed" + a + " != " + b);
                }
            }
            System.gc();
        } while (ref.get() != null);
    }

    boolean shouldEqualSelf(Object a) {
        return acmpModeInlineAlwaysFalse ? (!(a != null && a.getClass().isValue())) : true;
    }

    void checkEqual(Object a, Object b, boolean isEqual) {
        testEquals(a, a, shouldEqualSelf(a));
        testEquals(b, b, shouldEqualSelf(b));
        testEquals(a, b, isEqual);
        testNotEquals(a, b, !isEqual);
    }

    public static void testEquals(Object a, Object b, boolean expected) {
        boolean isEqual = (a == b);
        if (isEqual != expected) {
            throw new RuntimeException("Expected " + expected + " : "
                                       + a + " == " + b);
        }
    }

    public static void testNotEquals(Object a, Object b, boolean expected) {
        boolean isNotEqual = (a != b);
        if (isNotEqual != expected) {
            throw new RuntimeException("Expected " + expected + " : "
                                       + a + " != " + b);
        }
    }

    public static void main(String[] args) {
        boolean inlineTypesAlwaysFalse = (args.length > 0) && args[0].equals("alwaysFalse");
        new Ifacmp(inlineTypesAlwaysFalse).test();
        new Ifacmp(inlineTypesAlwaysFalse).testUntilGc(3);
    }
}
