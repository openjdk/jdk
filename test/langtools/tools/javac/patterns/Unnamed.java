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
 * @bug 8304246
 * @summary Compiler Implementation for Unnamed patterns and variables
 * @compile Unnamed.java
 * @run main Unnamed
 */

import java.util.Objects;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Unnamed {
    public static void main(String[] args) throws Throwable {
        new Unnamed().run();
    }

    public void run() {
        assertEquals(1, testMultiValuesTopLevel(new R1()));
        assertEquals(2, testMultiValuesTopLevel(new R3()));
        assertEquals(1, testMultiValuesTopLevel2(new R1()));
        assertEquals(2, testMultiValuesTopLevel2(new R2()));
        assertEquals(2, testMultiValuesTopLevel2(new R4()));
        assertEquals(1, testMultiValuesNested(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNested(new Box<>(new R2())));
        assertEquals(2, testMultiValuesNested(new Box<>(new R3())));
        assertEquals(3, testMultiValuesNested(new Box<>(new R4())));
        assertEquals(1, testMultiValuesNestedUnnamedVarAndPattern(new Box<>(new R1())));
        assertEquals(2, testMultiValuesNestedUnnamedVarAndPattern(new Box<>(new R4())));
        assertEquals(1, testMultiValuesNestedMix(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix(new Box2<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix2(new Box<>(new R1())));
        assertEquals(1, testMultiValuesNestedMix2("BOX"));
        assertEquals(2, testMultiValuesNestedMix2(new Box2<>(new R1())));
        assertEquals(1, testMultiValuesStatementBlock(42));
        assertEquals(1, testMultiValuesStatementBlock(42.0f));
        assertEquals(2, testMultiValuesStatementBlock("BOX"));
        assertEquals(1, testMultiValuesStatementBlock2(new Box<>(new R1())));
        assertEquals(1, testMultiValuesStatementBlock2("BOX"));
        assertEquals(2, testMultiValuesStatementBlock2(new Box2<>(new R1())));
        assertEquals(2, testMultiValuesGuards(new R3(), 1));
        assertEquals(3, testMultiValuesGuards(new R4(), 42));
        assertEquals(3, testMultiValuesGuards(new R3(), 42));
        assertEquals(1, testMultiValuesNestedGuards(new Box(new R2()), 42));
        assertEquals(2, testMultiValuesNestedGuards(new Box(new R3()), 1));
        assertEquals(1, testMixUnconditionalAndConditional(new R1()));
        assertEquals(2, testMixUnconditionalAndConditional(new R2()));
        assertEquals(2, testMixUnconditionalAndConditional(new R3()));
        assertEquals(1, testMultipleExpr(new Box<>(new R1())));
        assertEquals(1, testUnrolledExpr(new Box<>(new R1())));
        assertEquals(1, testMultipleStat(new Box<>(new R1())));
        assertEquals(1, testUnrolledStat(new Box<>(new R1())));
        assertEquals(2, testMixVarWithExplicit(new Box<>(new R2())));
        assertEquals("binding", unnamedGuardAddsBindings("match1", "binding"));
        assertEquals("any", unnamedGuardAddsBindings(42, 42));
        assertEquals(true, testUnnamedPrimitiveAndExhaustiveness(new Prim1(4)));
        assertEquals(false, testUnnamedPrimitiveAndExhaustiveness(new Prim2(5)));

        unnamedTest();
    }

    private void unnamedTest() {
        int _ = 0;
        int _ = 1;
        try (Lock _ = null) {
            try (Lock _ = null) {
            } catch (Exception _) {
                try {
                } catch (Exception _) {}
            }
        }
        try (final Lock _ = null) { }
        try (@Foo Lock _ = null) { }

        try (Lock _ = null) { }
        catch (Exception | Error _) { }

        String[] strs = new String[] { "str1", "str2" };
        for (var _ : strs) {
            for (var _ : strs) {
            }
        }
        TwoParams p1 = (_, _) -> {};
        TwoParams p2 = (var _, var _) -> {};
        TwoIntParams p3 = (int _, int b) -> {};
        TwoIntParams p4 = (int _, int _) -> {};
        TwoIntParamsIntRet p5 = (int _, int _) -> { return 1; };

        p1.run(1, 2);
        p2.run(1, 2);
        p3.run(1, 2);
        p4.run(1, 2);
        p5.run(1, 2);

        R r = new R(null);
        if (r instanceof R _) {}
        if (r instanceof R(_)) {}
        for (int _ = 0, _ = 1, x = 1; x <= 1 ; x++) {}
    }

    int testMultiValuesTopLevel(Object o) {
        return switch (o) {
            case R1 _, R2 _ -> 1;
            default -> 2;
        };
    }

    int testMultiValuesTopLevel2(Base o) {
        return switch (o) {
            case R1 r -> 1;
            case R2 _, R3 _, R4 _ -> 2;
        };
    }

    int testMultiValuesNested(Box<?> b) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _) -> 2;
            case Box(_)  -> 3;
        };
    }

    int testMultiValuesNestedUnnamedVarAndPattern(Box<?> b) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _), Box(_) -> 2;
        };
    }

    int testMultiValuesNestedMix(Object b) {
        return switch (b) {
            case Box(_), Box2(_) -> 1;
            default -> 2;
        };
    }

    int testMultiValuesNestedMix2(Object b) {
        return switch (b) {
            case Box(_), String _ -> 1;
            default -> 2;
        };
    }

    int testMultiValuesStatementBlock(Object o) {
        switch (o) {
            case Integer _:
            case Number _:
                return 1;
            default:
                return 2;
        }
    }

    int testMultiValuesStatementBlock2(Object o) {
        switch (o) {
            case Box(_):
            case String _:
                return 1;
            default:
                return 2;
        }
    }

    int testMultiValuesGuards(Base b, int x) {
        return switch (b) {
            case R1 r -> 1;
            case R2 _, R3 _, R4 _ when x == 1 -> 2;
            case R2 _, R3 _, R4 _ -> 3;
        };
    }

    int testMultiValuesNestedGuards(Box<?> b, int x) {
        return switch (b) {
            case Box(R1 _), Box(R2 _) -> 1;
            case Box(R3 _), Box(_) when x == 1 -> 2;
            case Box(_) -> 3;
        };
    }

    int testMixUnconditionalAndConditional(Base t) {
        return switch(t) {
            case R1 _ -> 1;
            case R2 _, Base _-> 2;
        };
    }

    int testMultipleExpr(Box<?> t) {
        return switch(t) {
            case Box(R1 _), Box(R2 _) -> 1;
            default -> -2;
        };
    }

    int testUnrolledExpr(Box<?> t) {
        return switch(t) {
            case Box(R1 _) -> 1;
            case Box(R2 _) -> 0;
            default -> -2;
        };
    }

    int testMultipleStat(Box<?> t) {
        int ret = -1;
        switch(t) {
            case Box(R1 _), Box(R2 _):
                ret = 1;
                break;
            default:
                ret = -2;
        }
        return ret;
    }

    int testUnrolledStat(Box<?> t) {
        int ret = -1;
        switch(t) {
            case Box(R1 _):
                ret = 1;
                break;
            case Box(R2 _):
                ret = 0;
                break;
            default:
                ret = -2;
        }
        return ret;
    }

    int testMixVarWithExplicit(Box<?> t) {
        int success = -1;
        success = switch(t) {
            case Box(R1 _) : {
                yield 1;
            }
            case Box(R2 _), Box(var _) : {
                yield 2;
            }
            default : {
                yield -2;
            }
        };
        return success;
    }

    String unnamedGuardAddsBindings(Object o1, Object o2) {
        return switch (o1) {
            case String _, Object _ when o2 instanceof String s: yield s;
            case Object _: yield "any";
        };
    }

    boolean testUnnamedPrimitiveAndExhaustiveness(RecordWithPrimitive a) {
        boolean r1 = switch (a) {
            case Prim1(var _) -> true;
            case Prim2(_) -> false;
        };

        boolean r2 = switch (a) {
            case Prim1(var _) -> true;
            case Prim2(var _) -> false;
        };

        boolean r3 = switch (a) {
            case Prim1(_) -> true;
            case Prim2(_) -> false;
        };

        return r1 && r2 && r3;
    }

    sealed interface RecordWithPrimitive permits Prim1, Prim2 {};
    record Prim1(int n1) implements RecordWithPrimitive {};
    record Prim2(int n2) implements RecordWithPrimitive {};

    // JEP 443 examples
    record Point(int x, int y) { }
    enum Color { RED, GREEN, BLUE }
    record ColoredPoint(Point p, Color c) { }

    void jep443examples(ColoredPoint r) {
        if (r instanceof ColoredPoint(Point(int x, int y), _)) { }
        if (r instanceof ColoredPoint(_, Color c)) { }
        if (r instanceof ColoredPoint(Point(int x, _), _)) { }
        if (r instanceof ColoredPoint(Point(int x, int _), Color _)) { }
        if (r instanceof ColoredPoint _) { }
    }

    class Lock implements AutoCloseable {
        @Override
        public void close() {}
    }
    interface TwoParams {
        public void run(Object o1, Object o2);
    }
    interface TwoIntParams {
        public void run(int o1, int o2);
    }
    interface TwoIntParamsIntRet {
        public int run(int a, int b);
    }
    record R(Object o) {}
    @Target(ElementType.LOCAL_VARIABLE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Foo { }

    sealed abstract class Base permits R1, R2, R3, R4 { }
    final  class R1  extends Base { }
    final  class R2  extends Base { }
    final  class R3  extends Base { }
    final  class R4  extends Base { }
    record Box<T extends Base>(T content) { }
    record Box2<T extends Base>(T content) { }
    void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
