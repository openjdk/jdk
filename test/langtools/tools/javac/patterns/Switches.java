/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * @test
 * @bug 8262891 8268333 8268896 8269802 8269808 8270151 8269113 8277864 8290709 8339296
 * @summary Check behavior of pattern switches.
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
        runEnumTest(this::testEnumExpression1);
        runEnumTest(this::testEnumExpression2);
        runEnumTest(this::testEnumWithGuards1);
        runEnumTest(this::testEnumWithGuards2);
        runEnumTest(this::testEnumWithGuards3);
        runEnumTest(this::testEnumWithGuards4);
        runEnumTest(this::testEnumWithGuards5);
        runEnumTest(this::testEnumWithGuardsExpression1);
        runEnumTest(this::testEnumWithGuardsExpression2);
        runEnumTest(this::testEnumWithGuardsExpression3);
        runEnumTest(this::testEnumWithGuardsExpression4);
        runEnumTest(this::testEnumWithGuardsExpression5);
        runEnumTest(this::testStringWithGuards1);
        runEnumTest(this::testStringWithGuardsExpression1);
        runEnumTest(this::testIntegerWithGuards1);
        runEnumTest(this::testIntegerWithGuardsExpression1);
        runStringWithConstant(this::testStringWithConstant);
        runStringWithConstant(this::testStringWithConstantExpression);
        runFallThrough(this::testFallThroughStatement);
        runFallThrough(this::testFallThroughExpression);
        runFallThrough(this::testFallThrough2Statement);
        runFallThrough(this::testFallThrough2Expression);
        npeTest(this::npeTestStatement);
        npeTest(this::npeTestExpression);
        npeTest(this::switchOverNullNPE);
        exhaustiveStatementSane("");
        exhaustiveStatementSane(null);
        exhaustiveStatementSane2(null);
        exhaustiveStatementSane2(new A());
        exhaustiveStatementSane2(new B());
        switchNestingTest(this::switchNestingStatementStatement);
        switchNestingTest(this::switchNestingStatementExpression);
        switchNestingTest(this::switchNestingExpressionStatement);
        switchNestingTest(this::switchNestingExpressionExpression);
        switchNestingTest(this::switchNestingIfSwitch);
        npeTest(x -> switchOverNull1());
        assertEquals(2, switchOverNull2());
        assertEquals(2, switchOverNull3());
        assertEquals(5, switchOverPrimitiveInt(0));
        assertEquals(7, switchOverPrimitiveInt(1));
        assertEquals(9, switchOverPrimitiveInt(2));
        assertEquals("a", deconstructStatement(new R("a")));
        assertEquals("1", deconstructStatement(new R(1)));
        assertEquals("other", deconstructStatement(""));
        assertEquals("a", deconstructExpression(new R("a")));
        assertEquals("1", deconstructExpression(new R(1)));
        assertEquals("other", deconstructExpression(""));
        assertEquals("a", translationTest("a"));
        assertEquals("Rb", translationTest(new R("b")));
        assertEquals("R2c", translationTest(new R2("c")));
        assertEquals("other", translationTest(0));
        assertEquals("OK", totalPatternAndNull(Integer.valueOf(42)));
        assertEquals("OK", totalPatternAndNull(null));
        assertEquals("1", nullAfterTotal(Integer.valueOf(42)));
        assertEquals("OK", nullAfterTotal(null));
        emptyFallThrough(1);
        emptyFallThrough("");
        emptyFallThrough(1.0);
        testSimpleSwitch();
        testSimpleSwitchExpression();
        assertEquals(0, constantAndPatternGuardInteger(0, true));
        assertEquals(0, constantAndPatternGuardInteger(1, true));
        assertEquals(1, constantAndPatternGuardInteger(1, false));
        assertEquals(2, constantAndPatternGuardInteger(0, false));
        assertEquals(0, constantAndPatternGuardString("", true));
        assertEquals(0, constantAndPatternGuardString("a", true));
        assertEquals(1, constantAndPatternGuardString("a", false));
        assertEquals(2, constantAndPatternGuardString("", false));
        assertEquals(0, constantAndPatternGuardEnum(E.A, true));
        assertEquals(0, constantAndPatternGuardEnum(E.B, true));
        assertEquals(1, constantAndPatternGuardEnum(E.B, false));
        assertEquals(2, constantAndPatternGuardEnum(E.A, false));
        assertEquals(0, nestedSwitchesInArgumentPosition(1));
        assertEquals(1, nestedSwitchesInArgumentPosition(new R(1)));
        assertEquals(5, nestedSwitchesInArgumentPosition(new R(new R("hello"))));
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

    void switchNestingTest(BiFunction<Object, Object, String> testCase) {
        assertEquals("string, string", testCase.apply("", ""));
        assertEquals("string, other", testCase.apply("", 1));
        assertEquals("other, string", testCase.apply(1, ""));
        assertEquals("other, other", testCase.apply(1, 1));
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
            case E x -> String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumExpression2(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x -> String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumWithGuards1(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case C: return String.valueOf(e);
            case E x when "A".equals(x.name()): return "broken";
            case E x: return String.valueOf(x);
            case null: return "null";
        }
    }

    String testEnumWithGuardsExpression1(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case C -> String.valueOf(e);
            case E x when "A".equals(x.name()) -> "broken";
            case E x -> String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumWithGuards2(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case E x when "C".equals(x.name()): return "C";
            case E x: return e == E.C ? "broken" : String.valueOf(x);
            case null: return "null";
        }
    }

    String testEnumWithGuardsExpression2(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x when "C".equals(x.name()) -> "C";
            case E x -> e == E.C ? "broken" : String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumWithGuards3(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case Object x when "C".equals(x.toString()): return "C";
            case E x: return e == E.C ? "broken" : String.valueOf(x);
            case null: return "null";
        }
    }

    String testEnumWithGuardsExpression3(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case Object x when "C".equals(x.toString()) -> "C";
            case E x -> e == E.C ? "broken" : String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumWithGuards4(E e) {
        switch (e) {
            case A: return "a";
            case B: return "b";
            case Runnable x when "C".equals(x.toString()): return "C";
            case E x: return e == E.C ? "broken" : String.valueOf(x);
            case null: return "null";
        }
    }

    String testEnumWithGuardsExpression4(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case Runnable x when "C".equals(x.toString()) -> "C";
            case E x -> e == E.C ? "broken" : String.valueOf(x);
            case null -> "null";
        };
    }

    String testEnumWithGuards5(Object e) {
        switch (e) {
            case E.A: return "a";
            case E.B: return "b";
            case Runnable x when "C".equals(x.toString()): return "C";
            case E x: return e == E.C ? "broken" : String.valueOf(x);
            case null: return "null";
            default: throw new AssertionError("Unexpected case!");
        }
    }

    String testEnumWithGuardsExpression5(Object e) {
        return switch (e) {
            case E.A -> "a";
            case E.B -> "b";
            case Runnable x when "C".equals(x.toString()) -> "C";
            case E x -> e == E.C ? "broken" : String.valueOf(x);
            case null -> "null";
            default -> throw new AssertionError("Unexpected case!");
        };
    }

    String testStringWithGuards1(E e) {
        switch (e != null ? e.name() : null) {
            case "A": return "a";
            case Switches.ConstantClassClash: return "b";
            case String x when "C".equals(x): return "C";
            case String x: return "C".equals(x) ? "broken" : String.valueOf(x);
            case null: return "null";
        }
    }

    String testStringWithGuardsExpression1(E e) {
        return switch (e != null ? e.name() : null) {
            case "A" -> "a";
            case ConstantClassClash -> "b";
            case String x when "C".equals(x) -> "C";
            case String x -> e == E.C ? "broken" : String.valueOf(x);
            case null -> "null";
        };
    }

    String testIntegerWithGuards1(E e) {
        switch (e != null ? e.ordinal() : null) {
            case 0: return "a";
            case 1: return "b";
            case Integer x when x.equals(2): return "C";
            case Integer x: return Objects.equals(x, 2) ? "broken" : String.valueOf(x);
            case null: return "null";
        }
    }

    String testIntegerWithGuardsExpression1(E e) {
        return switch (e != null ? e.ordinal() : null) {
            case 0 -> "a";
            case 1 -> "b";
            case Integer x when x.equals(2) -> "C";
            case Integer x -> Objects.equals(x, 2) ? "broken" : String.valueOf(x);
            case null -> "null";
        };
    }

    Integer testFallThroughStatement(Integer i) {
        int r = 0;

        switch (i) {
            case Integer o when o != null:
                r = 1;
            default:
                r = 2;
        }

        return r;
    }

    Integer testFallThroughExpression(Integer i) {
        int r = switch (i) {
            case Integer o when o != null:
                r = 1;
            default:
                r = 2;
                yield r;
        };

        return r;
    }

    Integer testFallThrough2Statement(Integer i) {
        int r = 0;

        switch (i) {
            case Integer o when o != null:
                r = 1;
            case null, default:
                r = 2;
        }

        return r;
    }

    Integer testFallThrough2Expression(Integer i) {
        int r = switch (i) {
            case Integer o when o != null:
                r = 1;
            case null, default:
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
        try {
            switch (o) {
                case Object obj:; //no break intentionally - should not fall through to any possible default
            }
            if (o == null) {
                throw new AssertionError();
            }
        } catch (NullPointerException ex) {
            if (o != null) {
                throw new AssertionError();
            }
        }
        switch (o) {
            case Object obj: int i;
            case null:; //no break intentionally - should not fall through to any possible default
        }
    }

    void exhaustiveStatementSane2(I i) {
        switch (i) {
            case A a: break;
            case B b:; //no break intentionally - should not fall through to any possible default
            case null:;
        }
        switch (i) {
            case A a -> {}
            case B b -> {}
            case null -> {}
        }
    }

    String switchNestingStatementStatement(Object o1, Object o2) {
        switch (o1) {
            case String s1 -> {
                switch (o2) {
                    case String s2 -> {
                        return "string, string";
                    }
                    default -> {
                        return "string, other";
                    }
                }
            }
            default -> {
                switch (o2) {
                    case String s2 -> {
                        return "other, string";
                    }
                    default -> {
                        return "other, other";
                    }
                }
            }
        }
    }

    String switchNestingStatementExpression(Object o1, Object o2) {
        switch (o1) {
            case String s1 -> {
                return switch (o2) {
                    case String s2 -> "string, string";
                    default -> "string, other";
                };
            }
            default -> {
                return switch (o2) {
                    case String s2 -> "other, string";
                    default -> "other, other";
                };
            }
        }
    }

    String switchNestingExpressionStatement(Object o1, Object o2) {
        return switch (o1) {
            case String s1 -> {
                switch (o2) {
                    case String s2 -> {
                        yield "string, string";
                    }
                    default -> {
                        yield "string, other";
                    }
                }
            }
            default -> {
                switch (o2) {
                    case String s2 -> {
                        yield "other, string";
                    }
                    default -> {
                        yield "other, other";
                    }
                }
            }
        };
    }

    String switchNestingExpressionExpression(Object o1, Object o2) {
        return switch (o1) {
            case String s1 ->
                switch (o2) {
                    case String s2 -> "string, string";
                    default -> "string, other";
                };
            default ->
                switch (o2) {
                    case String s2 -> "other, string";
                    default -> "other, other";
                };
        };
    }

    String switchNestingIfSwitch(Object o1, Object o2) {
        BiFunction<Object, Object, String> f = (n1, n2) -> {
            if (o1 instanceof CharSequence cs) {
                return switch (cs) {
                    case String s1 ->
                        switch (o2) {
                            case String s2 -> "string, string";
                            default -> "string, other";
                        };
                    default ->
                        switch (o2) {
                            case String s2 -> "other, string";
                            default -> "other, other";
                        };
                };
            } else {
                return switch (o2) {
                            case String s2 -> "other, string";
                            default -> "other, other";
                        };
            }
        };
        return f.apply(o1, o2);
    }

    private void switchOverNullNPE(I i) {
        int r = switch (null) {
            default -> 1;
        };
    }

    private int  switchOverNull1() {
        return switch (null) {
            case Object o -> 2;
        };
    }

    private int  switchOverNull2() {
        return switch (null) {
            case null -> 2;
            default -> 1;
        };
    }

    private int  switchOverNull3() {
        return switch (null) {
            case null -> 2;
            case Object o -> 1;
        };
    }

    private int switchOverPrimitiveInt(Integer i) {
        return switch (i) {
            case 0 -> 5 + 0;
            case Integer j when j == 1 -> 6 + j;
            case Integer j -> 7 + j;
        };
    }

    String deconstructStatement(Object o) {
        switch (o) {
            case R(String s) -> {return s;}
            case R(Integer i) -> {return i.toString();}
            case Object x -> {return "other";}
        }
    }

    String deconstructExpression(Object o) {
        return switch (o) {
            case R(String s) -> s;
            case R(Integer i) -> i.toString();
            case Object x -> "other";
        };
    }

    String translationTest(Object o) {
        return switch (o) {
            case R(String s) -> "R" + s;
            case String s -> s;
            case R2(String s) -> "R2" + s;
            default -> "other";
        };
    }

    String totalPatternAndNull(Integer in) {
        return switch (in) {
            case -1: { yield "";}
            case null: { yield "OK";}
            case Integer i: { yield "OK";}
        };
    }

    String nullAfterTotal(Object o) {
        return switch (o) {
            case Object obj: { yield "1";}
            case null: { yield "OK";}
        };
    }

    void emptyFallThrough(Object o) {
        switch (o) {
            case Integer i:
            case String s:
            case Object obj:
        }
    }

    void testSimpleSwitch() {
        Object o = "";
        int res;
        switch (o) {
            default -> res = 1;
        };
        assertEquals(1, res);
    }

    void testSimpleSwitchExpression() {
        Object o = "";
        int res = switch (o) {
            default -> 1;
        };
        assertEquals(1, res);
    }

    int constantAndPatternGuardInteger(Integer i, boolean g) {
        return switch (i) {
            case Integer j when g -> 0;
            case 1 -> 1;
            case Integer j -> 2;
        };
    }

    int constantAndPatternGuardString(String s, boolean g) {
        return switch (s) {
            case String t when g -> 0;
            case "a" -> 1;
            case String t -> 2;
        };
    }

    int constantAndPatternGuardEnum(E e, boolean g) {
        return switch (e) {
            case E f when g -> 0;
            case E.B -> 1;
            case E f -> 2;
        };
    }

    int nestedSwitchesInArgumentPosition(Object o1) {
        return id(switch (o1) {
            case R(var o2) -> switch (o2) {
                case R(String s) -> s;
                default -> "n";
            };
            default -> "";
        });
    }

    int id(String s) {
        return s.length();
    }

    int id(int i) {
        return i;
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

    public enum E implements Runnable {
        A, B, C;

        @Override public void run() {}
    }

    record R(Object o) {}
    record R2(Object o) {}
}
