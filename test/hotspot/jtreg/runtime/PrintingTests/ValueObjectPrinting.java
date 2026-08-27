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

/**
 * @test
 * @bug 8325945
 * @comment ObjArrayKlass::print_on is enabled only in debug builds.
 * @requires vm.debug
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @modules java.base/jdk.internal.vm.annotation
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ValueObjectPrinting
 */

import jdk.test.whitebox.WhiteBox;

import jdk.internal.vm.annotation.NullRestricted;
import jdk.internal.value.ValueClass;

public class ValueObjectPrinting {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    static void checkEqual(String s1, String s2) {
        if (!s1.equals(s2)) {
            throw new RuntimeException("Different strings: " + s1 + " vs " + s2);
        }
    }

    static void checkEqual(int len1, int len2) {
        if (len1 != len2) {
            throw new RuntimeException("Different lengths: " + len1 + " vs " + len2);
        }
    }

    static void checkMatch(String s, String regexp) {
        if (!s.matches("(?s).*" + regexp + ".*")) { // (?s) enables DOTALL mode
            System.err.println("Found:\n" + s);
            throw new RuntimeException("String does not match " + regexp);
        }
    }

    static void checkNullFree(String s, String fieldName, String type) {
        checkMatch(s, "final value flat '" + fieldName + "' .* Flat inline null-free type field '" + type + "'");
    }

    static void checkNonNullFree(String s, String fieldName, String type) {
        checkMatch(s, "final value flat '" + fieldName + "' .* Flat inline type field '" + type + "'");
    }


    public static void main(String[] args) {
        test(new Point(0x11, 0x22), (s) -> {
                checkNullFree(s, "x", "java/lang/Integer");
                checkNullFree(s, "y", "java/lang/Integer");
                checkMatch(s, "'value'.*0x00000011");
                checkMatch(s, "'value'.*0x00000022");
            });


        test(new Rectangle(0x1111, 0x2222, 0x3333, 0x4444), (s) -> {
                checkNullFree(s, "p1", "Point");
                checkNullFree(s, "p2", "Point");
                checkMatch(s, "'value'.*0x00001111");
                checkMatch(s, "'value'.*0x00002222");
                checkMatch(s, "'value'.*0x00003333");
                checkMatch(s, "'value'.*0x00004444");
            });


        test(new PaddedRectangle(0x1, 0x22, 0x333, 0x4444), (s) -> {
                checkNullFree(s, "p1", "Point");
                checkNullFree(s, "p2", "Point");
                checkMatch(s, "'value'.*0x00000001");
                checkMatch(s, "'value'.*0x00000022");
                checkMatch(s, "'value'.*0x00000333");
                checkMatch(s, "'value'.*0x00004444");
                checkMatch(s, "'c' .* 1000001003");
                checkMatch(s, "'d' .* 1000001004");
                checkMatch(s, "'a' .* \"info\"");
            });

        test(new NullableRectangle(1111, 2222), (s) -> {
                checkNonNullFree(s, "p1", "Point");
                checkNonNullFree(s, "p2", "Point");
                checkMatch(s, "Field marked as non-null.*marked as null");
                checkMatch(s, "'value'.* 1111 ");
                checkMatch(s, "'value'.* 2222 ");
            });


        test(new NullableRectangle(33333, 44444), (s) -> {
                checkMatch(s, "Field marked as null.*marked as non-null");
                checkMatch(s, "'value'.* 33333 ");
                checkMatch(s, "'value'.* 44444 ");
            });


        test(new NullableRectanglePair(111, 2222), (s) -> {
                checkMatch(s, "Field marked as null.*marked as non-null");
                checkMatch(s, "'value'.* 111 ");
                checkMatch(s, "'value'.* 2222 ");

                checkMatch(s, "Field marked as non-null.*as null.*as non-null.*as null");
            });


        test(new NullableRectanglePair(333333, 44444), (s) -> {
                checkMatch(s, "Field marked as null.*marked as non-null");
                checkMatch(s, "'value'.* 333333 ");
                checkMatch(s, "'value'.* 44444 ");

                checkMatch(s, "Field marked as null.*as null.*as non-null.*as non-null");
            });

        {
            Integer[] array = (Integer[])ValueClass.newNullableAtomicArray(Integer.class, 5);
            array[1] = new Integer(10011);
            array[4] = new Integer(40044);
            test(array, (s) -> {
                    checkMatch(s, "null.* 10011 .*null.*null.* 40044 ");
                });
        }

        {
            Point[] array = (Point[])ValueClass.newNullRestrictedAtomicArray(Point.class, 3, new Point(0, 0));
            array[0] = new Point(1111, 2222);
            array[1] = new Point(3333, 4444);
            array[2] = new Point(5555, 6666);
            test(array, (s) -> {
                    checkMatch(s, " 1111 .* 2222 .* 3333 .* 4444 .* 5555 .* 6666 ");
                });
        }

    }

    static void test(Object o, Checker c) {
        System.out.println(o);
        String s = WB.printObject(o);
        System.out.println(s);
        c.check(s);
    }
}

interface Checker {
    void check(String s);
}

value class Point {
    @NullRestricted Integer x;
    @NullRestricted Integer y;

    Point(int x, int y) {
        this.x = new Integer(x);
        this.y = new Integer(y);
        super();
    }

}

value class Rectangle {
    @NullRestricted Point p1;
    @NullRestricted Point p2;

    Rectangle(int x1, int y1, int x2, int y2) {
        this.p1 = new Point(x1, y1);
        this.p2 = new Point(x2, y2);
        super();
    }
}

value class PaddedRectangle {
    String a;
    @NullRestricted Point p1;
    int c, d;
    @NullRestricted Point p2;

    PaddedRectangle(int x1, int y1, int x2, int y2) {
        a = "info";
        this.p1 = new Point(x1, y1);
        c = 1000001003;
        d = 1000001004;
        this.p2 = new Point(x2, y2);
        super();
    }
}

value class NullableRectangle {
    Point p1;
    Point p2;

    NullableRectangle(int x1, int y1, int x2, int y2) {
        this.p1 = new Point(x1, y1);
        this.p2 = new Point(x2, y2);
        super();
    }
    NullableRectangle(int x, int y) {
        if (x < 3000) {
            this.p1 = new Point(x, y);
            this.p2 = null;
        } else {
            this.p1 = null;
            this.p2 = new Point(x, y);
        }
        super();
    }
}

value class NullableRectanglePair {
    NullableRectangle r1;
    NullableRectangle r2;

    NullableRectanglePair(int x, int y) {
        if (x < 3000) {
            r1 = new NullableRectangle(x, y);
            r2 = null;
        } else {
            r1 = null;
            r2 = new NullableRectangle(x, y);
        }
        super();
    }
}
