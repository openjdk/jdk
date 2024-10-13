/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8314226
 * @summary Series of colon-style fallthrough switch cases with guards compiled incorrectly
 * @enablePreview
 * @compile T8314226.java
 * @run main T8314226
 */

public class T8314226 {
    int multipleGuardedCases(Object obj) {
        switch (obj) {
            case Integer _ when ((Integer) obj) > 0:
            case String _  when !((String) obj).isEmpty():
                return 1;
            default:
                return -1;
        }
    }

    int multipleGuardedCases2a(Object obj) {
        switch (obj) {
            case Integer _ when ((Integer) obj) > 0:
            case Float _   when ((Float) obj) > 0.0f:
            case String _  when !((String) obj).isEmpty():
                return 1;
            default:
                return -1;
        }
    }

    int multipleGuardedCases2b(Object obj) {
        switch (obj) {
            case Float _   when ((Float) obj) > 0.0f: // reversing the order
            case Integer _ when ((Integer) obj) > 0:
            case String _  when !((String) obj).isEmpty():
                return 1;
            default:
                return -1;
        }
    }

    int multipleGuardedCasesMultiplePatterns(Object obj) {
        switch (obj) {
            case String _          when !((String) obj).isEmpty():
            case Integer _, Byte _ when ((Number) obj).intValue() > 0:
                return 1;
            default:
                return -1;
        }
    }

    int singleGuardedCase(Object obj) {
        switch (obj) {
            case String _ when !((String) obj).isEmpty():
                return 1;
            default:
                return -1;
        }
    }

    int multipleCasesWithReturn(Object obj) {
        switch (obj) {
            case Integer _ when ((Integer) obj) > 0:
            case String _ when !((String) obj).isEmpty():
                return 1;
            case null:
                return 2;
            default:
                return 3;
        }
    }

    int multipleCasesWithEffectNoReturn(Object obj) {
        int i = 0;
        switch (obj) {
            case Integer _ when ((Integer) obj) > 0:
            case String _ when !((String) obj).isEmpty():
                i = i + 1;
            case null:
                i = i + 10;
            default:
                i = i + 100;
        }
        return i;
    }

    int multipleCasesWithLoop(Object obj) {
        int i = 0;
        switch (obj) {
            case Integer _ when ((Integer) obj) > 0:
            case String _ when !((String) obj).isEmpty():
                i = i + 1;
            case null:
                while (true) {
                    i = i + 10;
                    break;
                }
            default:
                i = i + 100;
        }

        return i;
    }

    public static void main(String[] args) {
        new T8314226().run();
    }

    private void run() {
        assertEquals(1, multipleGuardedCases(42));
        assertEquals(1, multipleGuardedCases("test"));
        assertEquals(-1, multipleGuardedCases(""));
        assertEquals(1, multipleGuardedCases2a(42.0f));
        assertEquals(1, multipleGuardedCases2a("test"));
        assertEquals(-1, multipleGuardedCases2a(""));
        assertEquals(1, multipleGuardedCases2b(42.0f));
        assertEquals(1, multipleGuardedCases2b("test"));
        assertEquals(-1, multipleGuardedCases2b(""));
        assertEquals(1, multipleGuardedCasesMultiplePatterns((byte) 42));
        assertEquals(1, multipleGuardedCasesMultiplePatterns("test"));
        assertEquals(-1, multipleGuardedCasesMultiplePatterns(""));
        assertEquals(-1, singleGuardedCase(42));
        assertEquals(1, singleGuardedCase("test"));
        assertEquals(-1, singleGuardedCase(""));
        assertEquals(1, multipleCasesWithReturn(42));
        assertEquals(1, multipleCasesWithReturn("test"));
        assertEquals(2, multipleCasesWithReturn(null));
        assertEquals(3, multipleCasesWithReturn(""));
        assertEquals(111, multipleCasesWithEffectNoReturn(42));
        assertEquals(111, multipleCasesWithEffectNoReturn("test"));
        assertEquals(110, multipleCasesWithEffectNoReturn(null));
        assertEquals(100, multipleCasesWithEffectNoReturn(""));
        assertEquals(111, multipleCasesWithLoop(42));
        assertEquals(111, multipleCasesWithLoop("test"));
        assertEquals(110, multipleCasesWithLoop(null));
        assertEquals(100, multipleCasesWithLoop(""));
    }

    void assertEquals(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}