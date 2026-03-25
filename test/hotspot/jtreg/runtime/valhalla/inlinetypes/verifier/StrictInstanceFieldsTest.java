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

/*
 * @test
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @compile BadChild.jasm
 *          BadChild1.jasm
 *          StrictFieldNotSubset.jasm
 *          ControlFlowChildBad.jasm
 *          TryCatchChildBad.jasm
 *          NestedEarlyLarval.jcod
 *          EndsInEarlyLarval.jcod
 *          EarlyLarvalNotSubset.jcod
 *          InvalidIndexInEarlyLarval.jcod
 * @compile StrictInstanceFieldsTest.java
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             StrictInstanceFieldsTest
 *             Child ControlFlowChild TryCatchChild AssignedInConditionalChild
 *             SwitchCaseChild NestedConstructorChild FinalChild
 * @run main/othervm -Xlog:verification StrictInstanceFieldsTest
 */

import java.lang.reflect.Field;
import jdk.test.lib.helpers.StrictInit;

public class StrictInstanceFieldsTest {

    public static <T> void negativeTest(Class<T> clazz, String msg, boolean... args) throws Exception {
        try {
            T child = clazz.getDeclaredConstructor().newInstance(args);
            System.out.println(child);
            throw new RuntimeException("Should fail verification");
        } catch (java.lang.VerifyError e) {
            if (!e.getMessage().contains(msg)) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {

        // --------------
        // POSITIVE TESTS
        // --------------

        // Base case
        Child c = new Child();
        System.out.println(c);

        // Constructor with control flow
        ControlFlowChild c1 = new ControlFlowChild(true, true);
        System.out.println(c1);

        // Constructor with try-catch-finally
        TryCatchChild c2 = new TryCatchChild();
        System.out.println(c2);

        // Constructor with switch case
        SwitchCaseChild c3 = new SwitchCaseChild(2);
        System.out.println(c3);

        // Constructor with strict field assignment in conditional
        AssignedInConditionalChild c4 = new AssignedInConditionalChild();
        System.out.println(c4);

        // Constructor with nested constructor calls
        NestedConstructorChild c5 = new NestedConstructorChild();
        System.out.println(c5);

        // Final stirct fields defined in constructor
        FinalChild fc = new FinalChild();
        System.out.println(fc);

        // --------------
        // NEGATIVE TESTS
        // --------------

        // Field not initialized before super call
        negativeTest(BadChild.class, "All strict final fields must be initialized before super()");

        // Field not initialized before super call
        negativeTest(BadChild1.class, "All strict final fields must be initialized before super()");

        // Attempt to assign a strict field not present in the original set of unset fields
        negativeTest(StrictFieldNotSubset.class, "Initializing unknown strict field");

        // Constructor with control flow but field is not initialized
        negativeTest(ControlFlowChildBad.class, "Inconsistent stackmap frames at branch target", true, false);

        // Constructor with try-catch but field is not initialized
        negativeTest(TryCatchChildBad.class, "Inconsistent stackmap frames at branch target");

        // Early_Larval frame contains another early_larval instead of a base frame
        negativeTest(NestedEarlyLarval.class, "Early larval frame must be followed by a base frame", true, false);

        // Stack map table ends in early_larval frame without base frame
        negativeTest(EndsInEarlyLarval.class, "Early larval frame must be followed by a base frame", true, false);

        // Early_larval frame includes a strict field not preset in the original set of unset fields
        negativeTest(EarlyLarvalNotSubset.class, "Strict fields not a subset of initial strict instance fields", true, false);

        // Early_larval frame includes a constant pool index that doesn't point to a NameAndType
        negativeTest(InvalidIndexInEarlyLarval.class, "Invalid constant pool index in early larval frame", true, false);

        System.out.println("Passed");
    }
}

class Parent {
    int z;

    Parent() {
        z = 0;
        checkStrict(this.getClass());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field f : fields) {
            try {
                sb.append(f.getName() + ": " + f.get(this) + "\n");
           } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
           }
        }
        return sb.toString();
    }

    // Every class in this test has strict fields x and y,
    // make sure they have the ACC_STRICT_INIT flag set
    public static void checkStrict(Class<?> c) {
        Field[] fields = c.getDeclaredFields();

        for (Field f : fields) {
            if (f.getName().equals("x") && !f.isStrictInit()) {
                throw new RuntimeException("Field x should be strict");
            }
            if (f.getName().equals("y") && !f.isStrictInit()) {
                throw new RuntimeException("Field y should be strict");
            }
        }
    }
}

class Child extends Parent {

    @StrictInit
    int x;
    @StrictInit
    int y;

    Child() {
        x = y = 1;
        super();
    }
}

class ControlFlowChild extends Parent {

    @StrictInit
    int x;
    @StrictInit
    int y;

    ControlFlowChild(boolean a, boolean b) {
        if (a) {
            x = 1;
            if (b) {
                y = 1;
            } else {
                y = 2;
            }
        } else {
            x = y = 3;
        }
        super();
    }
}

class TryCatchChild extends Parent {

    @StrictInit
    int x;
    @StrictInit
    int y;

    TryCatchChild() {
        try {
            x = 0;
            int[] a = new int[1];
            System.out.println(a[2]);
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            y = 0;
        } finally {
            x = y = 1;
        }
        super();
    }
}

class AssignedInConditionalChild extends Parent {

    @StrictInit
    final int x;
    @StrictInit
    final int y;

    AssignedInConditionalChild() {
        if ((x=1) == 1) {
            y = 1;
        } else {
            y = 2;
        }
        super();
    }
}

class SwitchCaseChild extends Parent {

    @StrictInit
    final int x;
    @StrictInit
    final int y;

    SwitchCaseChild(int n) {
        switch(n) {
            case 0:
                x = y = 0;
                break;
            case 1:
                x = y = 1;
                break;
            case 2:
                x = y = 2;
                break;
            default:
                x = y = 100;
                break;
        }
        super();
    }
}

class NestedConstructorChild extends Parent {

    @StrictInit
    final int x;
    @StrictInit
    final int y;

    NestedConstructorChild(boolean a, boolean b) {
        if (a) {
            x = 1;
            if (b) {
                y = 1;
            } else {
                y = 2;
            }
        } else {
            x = y = 3;
        }
        super();
    }

    NestedConstructorChild() {
        this(true, true);
    }
}

class FinalChild extends Parent {

    @StrictInit
    final int x;
    @StrictInit
    final int y;

    FinalChild() {
        x = y = 1;
        super();
    }
}
