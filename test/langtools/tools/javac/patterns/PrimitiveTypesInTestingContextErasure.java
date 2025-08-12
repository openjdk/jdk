/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341408
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Second Preview)
 * @enablePreview
 */

import java.util.List;

public class PrimitiveTypesInTestingContextErasure {
    public static void main(String[] args) {
        erasureSwitch();
        erasureInstanceofTypeComparisonOperator();
        erasureInstanceofPatternMatchingOperator();

        pollutedInstanceofPatternMatchingOperatorReference();
        pollutedInstanceofPatternMatchingOperator();
        pollutedInstanceofTypeComparisonOperator();
        pollutedSwitch();
    }

    public static void erasureSwitch() {
        List<Short> ls = List.of((short) 42);
        Short s = 42;

        assertTrue(switch(ls.get(0)) {
            case int _ -> true;                  // Short to int
            default -> false;
        });
    }

    public static void erasureInstanceofTypeComparisonOperator() {
        List<Short> ls = List.of((short) 42);

        assertTrue(ls.get(0) instanceof int);   // Short to int
    }

    public static void erasureInstanceofPatternMatchingOperator() {
        List<Short> ls = List.of((short) 42);

        assertTrue(ls.get(0) instanceof int i); // Short to int
    }

    public static void pollutedInstanceofPatternMatchingOperator() {
        List<Short> ls = (List) List.of("42");

        assertTrue(!(ls.get(0) instanceof int i));
    }

    public static void pollutedInstanceofTypeComparisonOperator() {
        List<Short> ls = (List) List.of("42");

        assertTrue(!(ls.get(0) instanceof int));
    }

    public static void pollutedInstanceofPatternMatchingOperatorReference() {
        List<Short> ls = (List) List.of("42");

        assertTrue(!(ls.get(0) instanceof Short));
    }

    public static void pollutedSwitch() {
        List<Short> ls = (List) List.of("42");

        try {
            var res = switch(ls.get(0)) {
                case int _ -> true;
                default -> false;
            };
            throw new AssertionError("Expected: ClassCastException");
        } catch (ClassCastException e) {
            ;
        }
    }

    static void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("Expected: true, but got false");
        }
    }
}
