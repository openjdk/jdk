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

/*
 * @test
 * @bug 8302202
 * @summary Testing record patterns with null components
 * @enablePreview
 * @compile NullsInDeconstructionPatterns2.java
 * @run main NullsInDeconstructionPatterns2
 */

import java.util.Objects;
import java.util.function.Function;

public class NullsInDeconstructionPatterns2 {

    public static void main(String[] args) {
        new NullsInDeconstructionPatterns2().run();
    }

    private void run() {
        run1(this::test1a);
        run1(this::test1b);
        run2(this::test2a);
        run2(this::test2b);
        run3(this::test3a);
        run3(this::test3b);
        run4();
    }

    private void run1(Function<Object, String> method) {
        assertEquals("R1(null)", method.apply(new R1(null)));
        assertEquals("R1(!null)", method.apply(new R1("")));
    }

    private void run2(Function<Object, String> method) {
        assertEquals("R2(null, null)", method.apply(new R2(null, null)));
        assertEquals("R2(!null, null)", method.apply(new R2("", null)));
        assertEquals("R2(null, !null)", method.apply(new R2(null, "")));
        assertEquals("R2(!null, !null)", method.apply(new R2("", "")));
    }

    private void run3(Function<Object, String> method) {
        assertEquals("R3(null, null, null)", method.apply(new R3(null, null, null)));
        assertEquals("R3(!null, null, null)", method.apply(new R3("", null, null)));
        assertEquals("R3(null, !null, null)", method.apply(new R3(null, "", null)));
        assertEquals("R3(!null, !null, null)", method.apply(new R3("", "", null)));
        assertEquals("R3(null, null, !null)", method.apply(new R3(null, null, "")));
        assertEquals("R3(!null, null, !null)", method.apply(new R3("", null, "")));
        assertEquals("R3(null, !null, !null)", method.apply(new R3(null, "", "")));
        assertEquals("R3(!null, !null, !null)", method.apply(new R3("", "", "")));
    }

    private void run4() {
        assertEquals("integer", test4(new R1(0)));
        assertEquals("empty", test4(new R1("")));
        assertEquals("default", test4(new R1("a")));
    }
    private String test1a(Object i) {
        return switch (i) {
            case R1(Object o) when o == null -> "R1(null)";
            case R1(Object o) when o != null -> "R1(!null)";
            default -> "default";
        };
    }

    private String test1b(Object i) {
        return switch (i) {
            case R1(Object o) when o == null -> "R1(null)";
            case R1(Object o) -> "R1(!null)";
            default -> "default";
        };
    }

    private String test2a(Object i) {
        return switch (i) {
            case R2(Object o1, Object o2) when o1 == null && o2 == null -> "R2(null, null)";
            case R2(Object o1, Object o2) when o1 != null && o2 == null -> "R2(!null, null)";
            case R2(Object o1, Object o2) when o1 == null && o2 != null -> "R2(null, !null)";
            case R2(Object o1, Object o2) when o1 != null && o2 != null -> "R2(!null, !null)";
            default -> "default";
        };
    }

    private String test2b(Object i) {
        return switch (i) {
            case R2(Object o1, Object o2) when o1 == null && o2 == null -> "R2(null, null)";
            case R2(Object o1, Object o2) when o1 != null && o2 == null -> "R2(!null, null)";
            case R2(Object o1, Object o2) when o1 == null && o2 != null -> "R2(null, !null)";
            case R2(Object o1, Object o2) -> "R2(!null, !null)";
            default -> "default";
        };
    }

    private String test3a(Object i) {
        return switch (i) {
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 == null && o3 == null -> "R3(null, null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 == null && o3 == null -> "R3(!null, null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 != null && o3 == null -> "R3(null, !null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 != null && o3 == null -> "R3(!null, !null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 == null && o3 != null -> "R3(null, null, !null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 == null && o3 != null -> "R3(!null, null, !null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 != null && o3 != null -> "R3(null, !null, !null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 != null && o3 != null -> "R3(!null, !null, !null)";
            default -> "default";
        };
    }

    private String test3b(Object i) {
        return switch (i) {
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 == null && o3 == null -> "R3(null, null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 == null && o3 == null -> "R3(!null, null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 != null && o3 == null -> "R3(null, !null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 != null && o3 == null -> "R3(!null, !null, null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 == null && o3 != null -> "R3(null, null, !null)";
            case R3(Object o1, Object o2, Object o3) when o1 != null && o2 == null && o3 != null -> "R3(!null, null, !null)";
            case R3(Object o1, Object o2, Object o3) when o1 == null && o2 != null && o3 != null -> "R3(null, !null, !null)";
            case R3(Object o1, Object o2, Object o3) -> "R3(!null, !null, !null)";
            default -> "default";
        };
    }

    private String test4(Object i) {
        return switch (i) {
            case R1(Integer o) -> "integer";
            case R1(Object o) when o.toString().isEmpty() -> "empty";
            default -> "default";
        };
    }

    private static void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Unexpected result, expected: " + expected + "," +
                                                       " actual: " + actual);
        }
    }

    record R1(Object o) {}
    record R2(Object o1, Object o2) {}
    record R3(Object o1, Object o2, Object o3) {}

}
