/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * @test
 * @bug 8262891 8268333 8268896
 * @summary Check behavior of pattern switches.
 * @compile --enable-preview -source ${jdk.version} Switches.java
 * @run main/othervm --enable-preview Switches
 */
public class Switches {

    public static void main(String... args) {
        new Switches().run();
    }

    void run() {
        run(this::typeTestPatternSwitchTest);
        run(this::typeTestPatternSwitchExpressionTest);
        run(this::testBooleanSwitchExpression);
        assertFalse(testNullSwitch(null));
        assertTrue(testNullSwitch(""));
        runArrayTypeTest(this::testArrayTypeStatement);
        runArrayTypeTest(this::testArrayTypeExpression);
        runDefaultTest(this::testDefaultDoesNotDominateStatement);
        runDefaultTest(this::testDefaultDoesNotDominateExpression);
        runEnumTest(this::testEnumExpression1);
        runEnumTest(this::testEnumExpression2);
        runEnumTest(this::testEnumWithGuards1);
        runEnumTest(this::testEnumWithGuards2);
        runEnumTest(this::testEnumWithGuardsExpression1);
        runEnumTest(this::testEnumWithGuardsExpression2);
        runEnumTest(this::testStringWithGuards1);
        runEnumTest(this::testStringWithGuardsExpression1);
        runEnumTest(this::testIntegerWithGuards1);
        runEnumTest(this::testIntegerWithGuardsExpression1);
        runStringWithConstant(this::testStringWithConstant);
        runStringWithConstant(this::testStringWithConstantExpression);
        runFallThrough(this::testFallThroughStatement);
        runFallThrough(this::testFallThroughExpression);
        npeTest(this::npeTestStatement);
        npeTest(this::npeTestExpression);
        exhaustiveStatementSane("");
        exhaustiveStatementSane(null);
    }

    void run(Function<Object, Integer> mapper) {
        assertEquals(2, mapper.apply("2"));
        assertEquals(3, mapper.apply("3"));
        assertEquals(8, mapper.apply(new StringBuilder("4")));
        assertEquals(2, mapper.apply(2));
        assertEquals(3, mapper.apply(3));
        assertEquals(-1, mapper.apply(2.0));
        assertEquals(-1, mapper.apply(new Object()));
    }

    void runArrayTypeTest(Function<Object, String> mapper) {
        assertEquals("arr0", mapper.apply(new int[0]));
        assertEquals("str6", mapper.apply("string"));
        assertEquals("i1", mapper.apply(1));
        assertEquals("", mapper.apply(1.0));
    }

    void runDefaultTest(Function<Object, String> mapper) {
        assertEquals("default", mapper.apply(new int[0]));
        assertEquals("str6", mapper.apply("string"));
        assertEquals("default", mapper.apply(1));
        assertEquals("default", mapper.apply(1.0));
    }

    void runEnumTest(Function<E, String> mapper) {
        assertEquals("a", mapper.apply(E.A));
        assertEquals("b", mapper.apply(E.B));
        assertEquals("C", mapper.apply(E.C));
        assertEquals("null", mapper.apply(null));
    }

    void runStringWithConstant(Function<String, Integer> mapper) {
        assertEquals(1, mapper.apply("A"));
        assertEquals(2, mapper.apply("AA"));
        assertEquals(0, mapper.apply(""));
        assertEquals(-1, mapper.apply(null));
    }

    void runFallThrough(Function<Integer, Integer> mapper) {
        assertEquals(2, mapper.apply(1));
    }

    void npeTest(Consumer<I> testCase) {
        try {
            testCase.accept(null);
            throw new AssertionError("Expected a NullPointerException, but got nothing.");
        } catch (NullPointerException ex) {
            //OK
        }
    }

    int typeTestPatternSwitchTest(Object o) {
        switch (o) {
            case String s: return Integer.parseInt(s.toString());
            case CharSequence s: return 2 * Integer.parseInt(s.toString());
            case Integer i: return i;
            case Object x: return -1;
        }
    }

    int typeTestPatternSwitchExpressionTest(Object o) {
        return switch (o) {
            case String s -> Integer.parseInt(s.toString());
            case @Deprecated CharSequence s -> { yield 2 * Integer.parseInt(s.toString()); }
            case final Integer i -> i;
            case Object x -> -1;
        };
    }

    int testBooleanSwitchExpression(Object o) {
        Object x;
        if (switch (o) {
            default -> false;
        }) {
            return -3;
        } else if (switch (o) {
            case String s -> (x = s) != null;
            default -> false;
        }) {
            return Integer.parseInt(x.toString());
        } else if (switch (o) {
            case CharSequence s -> {
                x = s;
                yield true;
            }
            default -> false;
        }) {
            return 2 * Integer.parseInt(x.toString());
        }
        return typeTestPatternSwitchTest(o);
    }

    boolean testNullSwitch(Object o) {
        return switch (o) {
            case null -> false;
            default -> true;
        };
    }

    String testArrayTypeStatement(Object o) {
        String res;
        switch (o) {
            case Integer i -> res = "i" + i;
            case int[] arr -> res = "arr" + arr.length;
            case String str -> res = "str" + str.length();
            default -> res = "";
        }
        return res;
    }

    String testArrayTypeExpression(Object o) {
        return switch (o) {
            case Integer i -> "i" + i;
            case int[] arr -> "arr" + arr.length;
            case String str -> "str" + str.length();
            default -> "";
        };
    }

    String testDefaultDoesNotDominateStatement(Object o) {
        String res;
        switch (o) {
            default -> res = "default";
            case String str -> res = "str" + str.length();
        }
        return res;
    }

    String testDefaultDoesNotDominateExpression(Object o) {
        return switch (o) {
            case default -> "default";
            case String str -> "str" + str.length();
        };
    }

    int testStringWithConstant(String str) {
        switch (str) {
            case "A": return 1;
            case null: return -1;
            case String s:  return s.length();
        }
    }

    int testStringWithConstantExpression(String str) {
        return switch (str) {
            case "A" -> 1;
            case null -> -1;
            case String s -> s.length();
        };
    }

    String testEnumExpression1(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case null, E x -> String.valueOf(x);
        };
    }

    String testEnumExpression2(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x, null -> String.valueOf(x);
        };
    }

    String testEnumWithGuards1(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case E x && "A".equals(x.name()): return "broken";
            case C: return String.valueOf(e);
            case null, E x: return String.valueOf(x);
        }
    }

    String testEnumWithGuardsExpression1(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x && "A".equals(x.name()) -> "broken";
            case C -> String.valueOf(e);
            case null, E x -> String.valueOf(x);
        };
    }

    String testEnumWithGuards2(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case E x && "C".equals(x.name()): return "C";
            case C: return "broken";
            case null, E x: return String.valueOf(x);
        }
    }

    String testEnumWithGuardsExpression2(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x && "C".equals(x.name()) -> "C";
            case C -> "broken";
            case null, E x -> String.valueOf(x);
        };
    }

    String testStringWithGuards1(E e) {
        switch (e != null ? e.name() : null) {
            case "A": return "a";
            case Switches.ConstantClassClash: return "b";
            case String x && "C".equals(x): return "C";
            case "C": return "broken";
            case null, String x: return String.valueOf(x);
        }
    }

    String testStringWithGuardsExpression1(E e) {
        return switch (e != null ? e.name() : null) {
            case "A" -> "a";
            case ConstantClassClash -> "b";
            case String x && "C".equals(x) -> "C";
            case "C" -> "broken";
            case null, String x -> String.valueOf(x);
        };
    }

    String testIntegerWithGuards1(E e) {
        switch (e != null ? e.ordinal() : null) {
            case 0: return "a";
            case 1: return "b";
            case Integer x && x.equals(2): return "C";
            case 2: return "broken";
            case null, Integer x: return String.valueOf(x);
        }
    }

    String testIntegerWithGuardsExpression1(E e) {
        return switch (e != null ? e.ordinal() : null) {
            case 0 -> "a";
            case 1 -> "b";
            case Integer x && x.equals(2) -> "C";
            case 2 -> "broken";
            case null, Integer x -> String.valueOf(x);
        };
    }

    Integer testFallThroughStatement(Integer i) {
        int r = 0;

        switch (i) {
            case Integer o && o != null:
                r = 1;
            default:
                r = 2;
        }

        return r;
    }

    Integer testFallThroughExpression(Integer i) {
        int r = switch (i) {
            case Integer o && o != null:
                r = 1;
            default:
                r = 2;
                yield r;
        };

        return r;
    }

    void npeTestStatement(I i) {
        switch (i) {
            case A a -> {}
            case B b -> {}
        }
    }

    void npeTestExpression(I i) {
        int j = switch (i) {
            case A a -> 0;
            case B b -> 1;
        };
    }

    void exhaustiveStatementSane(Object o) {
        switch (o) {
            case Object obj:; //no break intentionally - should not fall through to any possible default
        }
        switch (o) {
            case null, Object obj:; //no break intentionally - should not fall through to any possible default
        }
        switch (o) {
            case Object obj, null:; //no break intentionally - should not fall through to any possible default
        }
    }

    //verify that for cases like:
    //case ConstantClassClash ->
    //ConstantClassClash is interpreted as a field, not as a class
    private static final String ConstantClassClash = "B";
    private static class ConstantClassClash {}

    sealed interface I {}
    final class A implements I {}
    final class B implements I {}

    void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("Expected: true, but got false");
        }
    }

    void assertFalse(boolean actual) {
        if (actual) {
            throw new AssertionError("Expected: false, but got true");
        }
    }

    public enum E {
        A, B, C;
    }
}
