/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/* @test
 * @bug 8139885
 * @bug 8143798
 * @bug 8150825
 * @bug 8150635
 * @run testng/othervm -ea -esa test.java.lang.invoke.T8139885
 */

package test.java.lang.invoke;

import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.*;

import static java.lang.invoke.MethodType.methodType;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.*;

/**
 * Example-scale and negative tests for JEP 274 extensions.
 */
public class T8139885 {

    static final Lookup LOOKUP = MethodHandles.lookup();

    //
    // Tests.
    //

    @Test
    public static void testLoopFac() throws Throwable {
        MethodHandle[] counterClause = new MethodHandle[]{Fac.MH_zero, Fac.MH_inc};
        MethodHandle[] accumulatorClause = new MethodHandle[]{Fac.MH_one, Fac.MH_mult, Fac.MH_pred, Fac.MH_fin};
        MethodHandle loop = MethodHandles.loop(counterClause, accumulatorClause);
        assertEquals(Fac.MT_fac, loop.type());
        assertEquals(120, loop.invoke(5));
    }

    @Test
    public static void testLoopFacNullInit() throws Throwable {
        // null initializer for counter, should initialize to 0
        MethodHandle[] counterClause = new MethodHandle[]{null, Fac.MH_inc};
        MethodHandle[] accumulatorClause = new MethodHandle[]{Fac.MH_one, Fac.MH_mult, Fac.MH_pred, Fac.MH_fin};
        MethodHandle loop = MethodHandles.loop(counterClause, accumulatorClause);
        assertEquals(Fac.MT_fac, loop.type());
        assertEquals(120, loop.invoke(5));
    }

    @Test
    public static void testLoopNullInit() throws Throwable {
        // null initializer for counter, should initialize to 0, one-clause loop
        MethodHandle[] counterClause = new MethodHandle[]{null, Loop.MH_inc, Loop.MH_pred, Loop.MH_fin};
        MethodHandle loop = MethodHandles.loop(counterClause);
        assertEquals(Loop.MT_loop, loop.type());
        assertEquals(10, loop.invoke(10));
    }

    @Test
    public static void testLoopVoid1() throws Throwable {
        // construct a post-checked loop that only does one iteration and has a void body and void local state
        MethodHandle loop = MethodHandles.loop(new MethodHandle[]{Empty.MH_f, Empty.MH_f, Empty.MH_pred, null});
        assertEquals(MethodType.methodType(void.class), loop.type());
        loop.invoke();
    }

    @Test
    public static void testLoopVoid2() throws Throwable {
        // construct a post-checked loop that only does one iteration and has a void body and void local state,
        // initialized implicitly from the step type
        MethodHandle loop = MethodHandles.loop(new MethodHandle[]{null, Empty.MH_f, Empty.MH_pred, null});
        assertEquals(MethodType.methodType(void.class), loop.type());
        loop.invoke();
    }

    @Test
    public static void testLoopVoid3() throws Throwable {
        // construct a post-checked loop that only does one iteration and has a void body and void local state,
        // and that has a void finalizer
        MethodHandle loop = MethodHandles.loop(new MethodHandle[]{null, Empty.MH_f, Empty.MH_pred, Empty.MH_f});
        assertEquals(MethodType.methodType(void.class), loop.type());
        loop.invoke();
    }

    @Test
    public static void testLoopFacWithVoidState() throws Throwable {
        // like testLoopFac, but with additional void state that outputs a dot
        MethodHandle[] counterClause = new MethodHandle[]{Fac.MH_zero, Fac.MH_inc};
        MethodHandle[] accumulatorClause = new MethodHandle[]{Fac.MH_one, Fac.MH_mult, Fac.MH_pred, Fac.MH_fin};
        MethodHandle[] dotClause = new MethodHandle[]{null, Fac.MH_dot};
        MethodHandle loop = MethodHandles.loop(counterClause, accumulatorClause, dotClause);
        assertEquals(Fac.MT_fac, loop.type());
        assertEquals(120, loop.invoke(5));
    }

    @Test
    public static void testLoopVoidInt() throws Throwable {
        // construct a post-checked loop that only does one iteration and has a void body and void local state,
        // and that returns a constant
        MethodHandle loop = MethodHandles.loop(new MethodHandle[]{null, Empty.MH_f, Empty.MH_pred, Empty.MH_c});
        assertEquals(MethodType.methodType(int.class), loop.type());
        assertEquals(23, loop.invoke());
    }

    @Test
    public static void testLoopWithVirtuals() throws Throwable {
        // construct a loop (to calculate factorial) that uses a mix of static and virtual methods
        MethodHandle[] counterClause = new MethodHandle[]{null, LoopWithVirtuals.permute(LoopWithVirtuals.MH_inc)};
        MethodHandle[] accumulatorClause = new MethodHandle[]{
                // init function must indicate the loop arguments (there is no other means to determine them)
                MethodHandles.dropArguments(LoopWithVirtuals.MH_one, 0, LoopWithVirtuals.class),
                LoopWithVirtuals.permute(LoopWithVirtuals.MH_mult),
                LoopWithVirtuals.permute(LoopWithVirtuals.MH_pred),
                LoopWithVirtuals.permute(LoopWithVirtuals.MH_fin)
        };
        MethodHandle loop = MethodHandles.loop(counterClause, accumulatorClause);
        assertEquals(LoopWithVirtuals.MT_loop, loop.type());
        assertEquals(120, loop.invoke(new LoopWithVirtuals(), 5));
    }

    @Test
    public static void testLoopNegative() throws Throwable {
        MethodHandle mh_loop =
                LOOKUP.findStatic(MethodHandles.class, "loop", methodType(MethodHandle.class, MethodHandle[][].class));
        MethodHandle i0 = MethodHandles.constant(int.class, 0);
        MethodHandle ii = MethodHandles.dropArguments(i0, 0, int.class, int.class);
        MethodHandle id = MethodHandles.dropArguments(i0, 0, int.class, double.class);
        MethodHandle i3 = MethodHandles.dropArguments(i0, 0, int.class, int.class, int.class);
        List<MethodHandle> inits = Arrays.asList(ii, id, i3);
        List<Class<?>> ints = Arrays.asList(int.class, int.class, int.class);
        List<MethodHandle> finis = Arrays.asList(Fac.MH_fin, Fac.MH_inc, Counted.MH_step);
        List<MethodHandle> preds1 = Arrays.asList(null, null, null);
        List<MethodHandle> preds2 = Arrays.asList(null, Fac.MH_fin, null);
        MethodHandle eek = MethodHandles.dropArguments(i0, 0, int.class, int.class, double.class);
        List<MethodHandle> nesteps = Arrays.asList(Fac.MH_inc, eek, Fac.MH_dot);
        List<MethodHandle> nepreds = Arrays.asList(null, Fac.MH_pred, null);
        List<MethodHandle> nefinis = Arrays.asList(null, Fac.MH_fin, null);
        List<MethodHandle> lvsteps = Arrays.asList(LoopWithVirtuals.MH_inc, LoopWithVirtuals.MH_mult);
        List<MethodHandle> lvpreds = Arrays.asList(null, LoopWithVirtuals.MH_pred);
        List<MethodHandle> lvfinis = Arrays.asList(null, LoopWithVirtuals.MH_fin);
        MethodHandle[][][] cases = {
                /*  1 */ null,
                /*  2 */ {},
                /*  3 */ {{null, Fac.MH_inc}, {Fac.MH_one, null, Fac.MH_mult, Fac.MH_pred, Fac.MH_fin}},
                /*  4 */ {{null, Fac.MH_inc}, null},
                /*  5 */ {{Fac.MH_zero, Fac.MH_dot}},
                /*  6 */ {{ii}, {id}, {i3}},
                /*  7 */ {{null, Fac.MH_inc, null, Fac.MH_fin}, {null, Fac.MH_inc, null, Fac.MH_inc},
                            {null, Counted.MH_start, null, Counted.MH_step}},
                /*  8 */ {{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, Fac.MH_mult, null, Fac.MH_fin}, {null, Fac.MH_dot}},
                /*  9 */ {{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, Fac.MH_mult, Fac.MH_fin, Fac.MH_fin}, {null, Fac.MH_dot}},
                /* 10 */ {{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, eek, Fac.MH_pred, Fac.MH_fin}, {null, Fac.MH_dot}},
                /* 11 */ {{null, LoopWithVirtuals.MH_inc}, {LoopWithVirtuals.MH_one, LoopWithVirtuals.MH_mult, LoopWithVirtuals.MH_pred, LoopWithVirtuals.MH_fin}}
        };
        String[] messages = {
                /*  1 */ "null or no clauses passed",
                /*  2 */ "null or no clauses passed",
                /*  3 */ "All loop clauses must be represented as MethodHandle arrays with at most 4 elements.",
                /*  4 */ "null clauses are not allowed",
                /*  5 */ "clause 0: init and step return types must match: int != void",
                /*  6 */ "found non-effectively identical init parameter type lists: " + inits +
                            " (common suffix: " + ints + ")",
                /*  7 */ "found non-identical finalizer return types: " + finis + " (return type: int)",
                /*  8 */ "no predicate found: " + preds1,
                /*  9 */ "predicates must have boolean return type: " + preds2,
                /* 10 */ "found non-effectively identical parameter type lists:\nstep: " + nesteps +
                            "\npred: " + nepreds + "\nfini: " + nefinis + " (common parameter sequence: " + ints + ")",
                /* 11 */ "found non-effectively identical parameter type lists:\nstep: " + lvsteps +
                            "\npred: " + lvpreds + "\nfini: " + lvfinis + " (common parameter sequence: " + ints + ")"
        };
        for (int i = 0; i < cases.length; ++i) {
            boolean caught = false;
            try {
                mh_loop.invokeWithArguments(cases[i]);
            } catch (IllegalArgumentException iae) {
                assertEquals(messages[i], iae.getMessage());
                caught = true;
            }
            assertTrue(caught);
        }
    }

    @Test
    public static void testWhileLoop() throws Throwable {
        // int i = 0; while (i < limit) { ++i; } return i; => limit
        MethodHandle loop = MethodHandles.whileLoop(While.MH_zero, While.MH_pred, While.MH_step);
        assertEquals(While.MT_while, loop.type());
        assertEquals(23, loop.invoke(23));
    }

    @Test
    public static void testWhileLoopNoIteration() throws Throwable {
        // a while loop that never executes its body because the predicate evaluates to false immediately
        MethodHandle loop = MethodHandles.whileLoop(While.MH_initString, While.MH_predString, While.MH_stepString);
        assertEquals(While.MT_string, loop.type());
        assertEquals("a", loop.invoke());
    }

    @Test
    public static void testDoWhileLoop() throws Throwable {
        // int i = 0; do { ++i; } while (i < limit); return i; => limit
        MethodHandle loop = MethodHandles.doWhileLoop(While.MH_zero, While.MH_step, While.MH_pred);
        assertEquals(While.MT_while, loop.type());
        assertEquals(23, loop.invoke(23));
    }

    @Test
    public static void testWhileZip() throws Throwable {
        MethodHandle loop = MethodHandles.doWhileLoop(While.MH_zipInitZip, While.MH_zipStep, While.MH_zipPred);
        assertEquals(While.MT_zip, loop.type());
        List<String> a = Arrays.asList("a", "b", "c", "d");
        List<String> b = Arrays.asList("e", "f", "g", "h");
        List<String> zipped = Arrays.asList("a", "e", "b", "f", "c", "g", "d", "h");
        assertEquals(zipped, (List<String>) loop.invoke(a.iterator(), b.iterator()));
    }

    @Test
    public static void testCountedLoop() throws Throwable {
        // String s = "Lambdaman!"; for (int i = 0; i < 13; ++i) { s = "na " + s; } return s; => a variation on a well known theme
        MethodHandle fit13 = MethodHandles.constant(int.class, 13);
        MethodHandle loop = MethodHandles.countedLoop(fit13, Counted.MH_start, Counted.MH_step);
        assertEquals(Counted.MT_counted, loop.type());
        assertEquals("na na na na na na na na na na na na na Lambdaman!", loop.invoke("Lambdaman!"));
    }

    @Test
    public static void testCountedArrayLoop() throws Throwable {
        // int[] a = new int[]{0}; for (int i = 0; i < 13; ++i) { ++a[0]; } => a[0] == 13
        MethodHandle fit13 = MethodHandles.dropArguments(MethodHandles.constant(int.class, 13), 0, int[].class);
        MethodHandle loop = MethodHandles.countedLoop(fit13, null, Counted.MH_stepUpdateArray);
        assertEquals(Counted.MT_arrayCounted, loop.type());
        int[] a = new int[]{0};
        loop.invoke(a);
        assertEquals(13, a[0]);
    }

    @Test
    public static void testCountedPrintingLoop() throws Throwable {
        MethodHandle fit5 = MethodHandles.constant(int.class, 5);
        MethodHandle loop = MethodHandles.countedLoop(fit5, null, Counted.MH_printHello);
        assertEquals(Counted.MT_countedPrinting, loop.type());
        loop.invoke();
    }

    @Test
    public static void testCountedRangeLoop() throws Throwable {
        // String s = "Lambdaman!"; for (int i = -5; i < 8; ++i) { s = "na " + s; } return s; => a well known theme
        MethodHandle fitm5 = MethodHandles.dropArguments(Counted.MH_m5, 0, String.class);
        MethodHandle fit8 = MethodHandles.dropArguments(Counted.MH_8, 0, String.class);
        MethodHandle loop = MethodHandles.countedLoop(fitm5, fit8, Counted.MH_start, Counted.MH_step);
        assertEquals(Counted.MT_counted, loop.type());
        assertEquals("na na na na na na na na na na na na na Lambdaman!", loop.invoke("Lambdaman!"));
    }

    @Test
    public static void testIterateSum() throws Throwable {
        // Integer[] a = new Integer[]{1,2,3,4,5,6}; int sum = 0; for (int e : a) { sum += e; } return sum; => 21
        MethodHandle loop = MethodHandles.iteratedLoop(Iterate.MH_sumIterator, Iterate.MH_sumInit, Iterate.MH_sumStep);
        assertEquals(Iterate.MT_sum, loop.type());
        assertEquals(21, loop.invoke(new Integer[]{1, 2, 3, 4, 5, 6}));
    }

    @Test
    public static void testIterateReverse() throws Throwable {
        MethodHandle loop = MethodHandles.iteratedLoop(null, Iterate.MH_reverseInit, Iterate.MH_reverseStep);
        assertEquals(Iterate.MT_reverse, loop.type());
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");
        List<String> reversedList = Arrays.asList("e", "d", "c", "b", "a");
        assertEquals(reversedList, (List<String>) loop.invoke(list));
    }

    @Test
    public static void testIterateLength() throws Throwable {
        MethodHandle loop = MethodHandles.iteratedLoop(null, Iterate.MH_lengthInit, Iterate.MH_lengthStep);
        assertEquals(Iterate.MT_length, loop.type());
        List<Double> list = Arrays.asList(23.0, 148.0, 42.0);
        assertEquals(list.size(), (int) loop.invoke(list));
    }

    @Test
    public static void testIterateMap() throws Throwable {
        MethodHandle loop = MethodHandles.iteratedLoop(null, Iterate.MH_mapInit, Iterate.MH_mapStep);
        assertEquals(Iterate.MT_map, loop.type());
        List<String> list = Arrays.asList("Hello", "world", "!");
        List<String> upList = Arrays.asList("HELLO", "WORLD", "!");
        assertEquals(upList, (List<String>) loop.invoke(list));
    }

    @Test
    public static void testIteratePrint() throws Throwable {
        MethodHandle loop = MethodHandles.iteratedLoop(null, null, Iterate.MH_printStep);
        assertEquals(Iterate.MT_print, loop.type());
        loop.invoke(Arrays.asList("hello", "world"));
    }

    @Test
    public static void testIterateNullBody() {
        boolean caught = false;
        try {
            MethodHandles.iteratedLoop(MethodHandles.identity(int.class), MethodHandles.identity(int.class), null);
        } catch (IllegalArgumentException iae) {
            assertEquals("iterated loop body must not be null", iae.getMessage());
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public static void testTryFinally() throws Throwable {
        MethodHandle hello = MethodHandles.tryFinally(TryFinally.MH_greet, TryFinally.MH_exclaim);
        assertEquals(TryFinally.MT_hello, hello.type());
        assertEquals("Hello, world!", hello.invoke("world"));
    }

    @Test
    public static void testTryFinallyVoid() throws Throwable {
        MethodHandle tfVoid = MethodHandles.tryFinally(TryFinally.MH_print, TryFinally.MH_printMore);
        assertEquals(TryFinally.MT_printHello, tfVoid.type());
        tfVoid.invoke("world");
    }

    @Test
    public static void testTryFinallySublist() throws Throwable {
        MethodHandle helloMore = MethodHandles.tryFinally(TryFinally.MH_greetMore, TryFinally.MH_exclaimMore);
        assertEquals(TryFinally.MT_moreHello, helloMore.type());
        assertEquals("Hello, world and universe (but world first)!", helloMore.invoke("world", "universe"));
    }

    @Test
    public static void testTryFinallyNegative() {
        MethodHandle intid = MethodHandles.identity(int.class);
        MethodHandle intco = MethodHandles.constant(int.class, 0);
        MethodHandle errTarget = MethodHandles.dropArguments(intco, 0, int.class, double.class, String.class, int.class);
        MethodHandle errCleanup = MethodHandles.dropArguments(MethodHandles.constant(int.class, 0), 0, Throwable.class,
                int.class, double.class, Object.class);
        MethodHandle[][] cases = {
                {intid, MethodHandles.identity(double.class)},
                {intid, MethodHandles.dropArguments(intid, 0, String.class)},
                {intid, MethodHandles.dropArguments(intid, 0, Throwable.class, double.class)},
                {errTarget, errCleanup},
                {TryFinally.MH_voidTarget, TryFinally.MH_voidCleanup}
        };
        String[] messages = {
                "target and return types must match: double != int",
                "cleanup first argument and Throwable must match: (String,int)int != class java.lang.Throwable",
                "cleanup second argument and target return type must match: (Throwable,double,int)int != int",
                "cleanup parameters after (Throwable,result) and target parameter list prefix must match: " +
                        errCleanup.type() + " != " + errTarget.type(),
                "cleanup parameters after (Throwable,result) and target parameter list prefix must match: " +
                        TryFinally.MH_voidCleanup.type() + " != " + TryFinally.MH_voidTarget.type()
        };
        for (int i = 0; i < cases.length; ++i) {
            boolean caught = false;
            try {
                MethodHandles.tryFinally(cases[i][0], cases[i][1]);
            } catch (IllegalArgumentException iae) {
                assertEquals(messages[i], iae.getMessage());
                caught = true;
            }
            assertTrue(caught);
        }
    }

    @Test
    public static void testFold0a() throws Throwable {
        // equivalence to foldArguments(MethodHandle,MethodHandle)
        MethodHandle fold = MethodHandles.foldArguments(Fold.MH_multer, 0, Fold.MH_adder);
        assertEquals(Fold.MT_folded1, fold.type());
        assertEquals(720, (int) fold.invoke(3, 4, 5));
    }

    @Test
    public static void testFold1a() throws Throwable {
        // test foldArguments for folding position 1
        MethodHandle fold = MethodHandles.foldArguments(Fold.MH_multer, 1, Fold.MH_adder1);
        assertEquals(Fold.MT_folded1, fold.type());
        assertEquals(540, (int) fold.invoke(3, 4, 5));
    }

    @Test
    public static void testFold0b() throws Throwable {
        // test foldArguments equivalence with multiple types
        MethodHandle fold = MethodHandles.foldArguments(Fold.MH_str, 0, Fold.MH_comb);
        assertEquals(Fold.MT_folded2, fold.type());
        assertEquals(23, (int) fold.invoke("true", true, 23));
    }

    @Test
    public static void testFold1b() throws Throwable {
        // test folgArguments for folding position 1, with multiple types
        MethodHandle fold = MethodHandles.foldArguments(Fold.MH_str, 1, Fold.MH_comb2);
        assertEquals(Fold.MT_folded3, fold.type());
        assertEquals(1, (int) fold.invoke(true, true, 1));
        assertEquals(-1, (int) fold.invoke(true, false, -1));
    }

    @Test
    public static void testFoldArgumentsExample() throws Throwable {
        // test the JavaDoc foldArguments-with-pos example
        StringWriter swr = new StringWriter();
        MethodHandle trace = LOOKUP.findVirtual(StringWriter.class, "write", methodType(void.class, String.class)).bindTo(swr);
        MethodHandle cat = LOOKUP.findVirtual(String.class, "concat", methodType(String.class, String.class));
        assertEquals("boojum", (String) cat.invokeExact("boo", "jum"));
        MethodHandle catTrace = MethodHandles.foldArguments(cat, 1, trace);
        assertEquals("boojum", (String) catTrace.invokeExact("boo", "jum"));
        assertEquals("jum", swr.toString());
    }

    @Test
    public static void testAsSpreader() throws Throwable {
        MethodHandle spreader = SpreadCollect.MH_forSpreading.asSpreader(1, int[].class, 3);
        assertEquals(SpreadCollect.MT_spreader, spreader.type());
        assertEquals("A456B", (String) spreader.invoke("A", new int[]{4, 5, 6}, "B"));
    }

    @Test
    public static void testAsSpreaderExample() throws Throwable {
        // test the JavaDoc asSpreader-with-pos example
        MethodHandle compare = LOOKUP.findStatic(Objects.class, "compare", methodType(int.class, Object.class, Object.class, Comparator.class));
        MethodHandle compare2FromArray = compare.asSpreader(0, Object[].class, 2);
        Object[] ints = new Object[]{3, 9, 7, 7};
        Comparator<Integer> cmp = (a, b) -> a - b;
        assertTrue((int) compare2FromArray.invoke(Arrays.copyOfRange(ints, 0, 2), cmp) < 0);
        assertTrue((int) compare2FromArray.invoke(Arrays.copyOfRange(ints, 1, 3), cmp) > 0);
        assertTrue((int) compare2FromArray.invoke(Arrays.copyOfRange(ints, 2, 4), cmp) == 0);
    }

    @Test
    public static void testAsSpreaderIllegalPos() throws Throwable {
        int[] illegalPos = {-7, 3, 19};
        int caught = 0;
        for (int p : illegalPos) {
            try {
                SpreadCollect.MH_forSpreading.asSpreader(p, Object[].class, 3);
            } catch (IllegalArgumentException iae) {
                assertEquals("bad spread position", iae.getMessage());
                ++caught;
            }
        }
        assertEquals(illegalPos.length, caught);
    }

    @Test
    public static void testAsSpreaderIllegalMethodType() throws Throwable {
        MethodHandle h = MethodHandles.dropArguments(MethodHandles.constant(String.class, ""), 0, int.class, int.class);
        boolean caught = false;
        try {
            MethodHandle s = h.asSpreader(String[].class, 1);
        } catch (WrongMethodTypeException wmte) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public static void testAsCollector() throws Throwable {
        MethodHandle collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 1);
        assertEquals(SpreadCollect.MT_collector1, collector.type());
        assertEquals("A4B", (String) collector.invoke("A", 4, "B"));
        collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 2);
        assertEquals(SpreadCollect.MT_collector2, collector.type());
        assertEquals("A45B", (String) collector.invoke("A", 4, 5, "B"));
        collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 3);
        assertEquals(SpreadCollect.MT_collector3, collector.type());
        assertEquals("A456B", (String) collector.invoke("A", 4, 5, 6, "B"));
    }

    @Test
    public static void testAsCollectorInvokeWithArguments() throws Throwable {
        MethodHandle collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 1);
        assertEquals(SpreadCollect.MT_collector1, collector.type());
        assertEquals("A4B", (String) collector.invokeWithArguments("A", 4, "B"));
        collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 2);
        assertEquals(SpreadCollect.MT_collector2, collector.type());
        assertEquals("A45B", (String) collector.invokeWithArguments("A", 4, 5, "B"));
        collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 3);
        assertEquals(SpreadCollect.MT_collector3, collector.type());
        assertEquals("A456B", (String) collector.invokeWithArguments("A", 4, 5, 6, "B"));
    }

    @Test
    public static void testAsCollectorLeading() throws Throwable {
        MethodHandle collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 1);
        assertEquals(SpreadCollect.MT_collectorLeading1, collector.type());
        assertEquals("7Q", (String) collector.invoke(7, "Q"));
        collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 2);
        assertEquals(SpreadCollect.MT_collectorLeading2, collector.type());
        assertEquals("78Q", (String) collector.invoke(7, 8, "Q"));
        collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 3);
        assertEquals(SpreadCollect.MT_collectorLeading3, collector.type());
        assertEquals("789Q", (String) collector.invoke(7, 8, 9, "Q"));
    }

    @Test
    public static void testAsCollectorLeadingInvokeWithArguments() throws Throwable {
        MethodHandle collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 1);
        assertEquals(SpreadCollect.MT_collectorLeading1, collector.type());
        assertEquals("7Q", (String) collector.invokeWithArguments(7, "Q"));
        collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 2);
        assertEquals(SpreadCollect.MT_collectorLeading2, collector.type());
        assertEquals("78Q", (String) collector.invokeWithArguments(7, 8, "Q"));
        collector = SpreadCollect.MH_forCollectingLeading.asCollector(0, int[].class, 3);
        assertEquals(SpreadCollect.MT_collectorLeading3, collector.type());
        assertEquals("789Q", (String) collector.invokeWithArguments(7, 8, 9, "Q"));
    }

    @Test
    public static void testAsCollectorNone() throws Throwable {
        MethodHandle collector = SpreadCollect.MH_forCollecting.asCollector(1, int[].class, 0);
        assertEquals(SpreadCollect.MT_collector0, collector.type());
        assertEquals("AB", (String) collector.invoke("A", "B"));
    }

    @Test
    public static void testAsCollectorIllegalPos() throws Throwable {
        int[] illegalPos = {-1, 17};
        int caught = 0;
        for (int p : illegalPos) {
            try {
                SpreadCollect.MH_forCollecting.asCollector(p, int[].class, 0);
            } catch (IllegalArgumentException iae) {
                assertEquals("bad collect position", iae.getMessage());
                ++caught;
            }
        }
        assertEquals(illegalPos.length, caught);
    }

    @Test
    public static void testAsCollectorExample() throws Throwable {
        // test the JavaDoc asCollector-with-pos example
        StringWriter swr = new StringWriter();
        MethodHandle swWrite = LOOKUP.
                findVirtual(StringWriter.class, "write", methodType(void.class, char[].class, int.class, int.class)).
                bindTo(swr);
        MethodHandle swWrite4 = swWrite.asCollector(0, char[].class, 4);
        swWrite4.invoke('A', 'B', 'C', 'D', 1, 2);
        assertEquals("BC", swr.toString());
        swWrite4.invoke('P', 'Q', 'R', 'S', 0, 4);
        assertEquals("BCPQRS", swr.toString());
        swWrite4.invoke('W', 'X', 'Y', 'Z', 3, 1);
        assertEquals("BCPQRSZ", swr.toString());
    }

    @Test
    public static void testFindSpecial() throws Throwable {
        FindSpecial.C c = new FindSpecial.C();
        assertEquals("I1.m", c.m());
        MethodType t = MethodType.methodType(String.class);
        MethodHandle ci1m = LOOKUP.findSpecial(FindSpecial.I1.class, "m", t, FindSpecial.C.class);
        assertEquals("I1.m", (String) ci1m.invoke(c));
    }

    @Test
    public static void testFindSpecialAbstract() throws Throwable {
        FindSpecial.C c = new FindSpecial.C();
        assertEquals("q", c.q());
        MethodType t = MethodType.methodType(String.class);
        boolean caught = false;
        try {
            MethodHandle ci3q = LOOKUP.findSpecial(FindSpecial.I3.class, "q", t, FindSpecial.C.class);
        } catch (Throwable thrown) {
            if (!(thrown instanceof IllegalAccessException) || !FindSpecial.ABSTRACT_ERROR.equals(thrown.getMessage())) {
                throw new AssertionError(thrown.getMessage(), thrown);
            }
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public static void testFindClassCNFE() throws Throwable {
        boolean caught = false;
        try {
            LOOKUP.findClass("does.not.Exist");
        } catch (ClassNotFoundException cnfe) {
            caught = true;
        }
        assertTrue(caught);
    }

    //
    // Methods used to assemble tests.
    //

    static class Empty {

        static void f() { }

        static boolean pred() {
            return false;
        }

        static int c() {
            return 23;
        }

        static final Class<Empty> EMPTY = Empty.class;

        static final MethodType MT_f = methodType(void.class);
        static final MethodType MT_pred = methodType(boolean.class);
        static final MethodType MT_c = methodType(int.class);

        static final MethodHandle MH_f;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_c;

        static {
            try {
                MH_f = LOOKUP.findStatic(EMPTY, "f", MT_f);
                MH_pred = LOOKUP.findStatic(EMPTY, "pred", MT_pred);
                MH_c = LOOKUP.findStatic(EMPTY, "c", MT_c);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    static class Fac {

        static int zero(int k) {
            return 0;
        }

        static int one(int k) {
            return 1;
        }

        static boolean pred(int i, int acc, int k) {
            return i < k;
        }

        static int inc(int i, int acc, int k) {
            return i + 1;
        }

        static int mult(int i, int acc, int k) {
            return i * acc;
        }

        static void dot(int i, int acc, int k) {
            System.out.print('.');
        }

        static int fin(int i, int acc, int k) {
            return acc;
        }

        static final Class<Fac> FAC = Fac.class;

        static final MethodType MT_init = methodType(int.class, int.class);
        static final MethodType MT_fn = methodType(int.class, int.class, int.class, int.class);
        static final MethodType MT_dot = methodType(void.class, int.class, int.class, int.class);
        static final MethodType MT_pred = methodType(boolean.class, int.class, int.class, int.class);

        static final MethodHandle MH_zero;
        static final MethodHandle MH_one;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_inc;
        static final MethodHandle MH_mult;
        static final MethodHandle MH_dot;
        static final MethodHandle MH_fin;

        static final MethodType MT_fac = methodType(int.class, int.class);

        static {
            try {
                MH_zero = LOOKUP.findStatic(FAC, "zero", MT_init);
                MH_one = LOOKUP.findStatic(FAC, "one", MT_init);
                MH_pred = LOOKUP.findStatic(FAC, "pred", MT_pred);
                MH_inc = LOOKUP.findStatic(FAC, "inc", MT_fn);
                MH_mult = LOOKUP.findStatic(FAC, "mult", MT_fn);
                MH_dot = LOOKUP.findStatic(FAC, "dot", MT_dot);
                MH_fin = LOOKUP.findStatic(FAC, "fin", MT_fn);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class Loop {

        static int inc(int i, int k) {
            return i + 1;
        }

        static boolean pred(int i, int k) {
            return i < k;
        }

        static int fin(int i, int k) {
            return k;
        }

        static final Class<Loop> LOOP = Loop.class;

        static final MethodType MT_inc = methodType(int.class, int.class, int.class);
        static final MethodType MT_pred = methodType(boolean.class, int.class, int.class);
        static final MethodType MT_fin = methodType(int.class, int.class, int.class);

        static final MethodHandle MH_inc;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_fin;

        static final MethodType MT_loop = methodType(int.class, int.class);

        static {
            try {
                MH_inc = LOOKUP.findStatic(LOOP, "inc", MT_inc);
                MH_pred = LOOKUP.findStatic(LOOP, "pred", MT_pred);
                MH_fin = LOOKUP.findStatic(LOOP, "fin", MT_fin);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class LoopWithVirtuals {

        static int one(int k) {
            return 1;
        }

        int inc(int i, int acc, int k) {
            return i + 1;
        }

        int mult(int i, int acc, int k) {
            return i * acc;
        }

        boolean pred(int i, int acc, int k) {
            return i < k;
        }

        int fin(int i, int acc, int k) {
            return acc;
        }

        static final Class<LoopWithVirtuals> LOOP_WITH_VIRTUALS = LoopWithVirtuals.class;

        static final MethodType MT_one = methodType(int.class, int.class);
        static final MethodType MT_inc = methodType(int.class, int.class, int.class, int.class);
        static final MethodType MT_mult = methodType(int.class, int.class, int.class, int.class);
        static final MethodType MT_pred = methodType(boolean.class, int.class, int.class, int.class);
        static final MethodType MT_fin = methodType(int.class, int.class, int.class, int.class);

        static final MethodHandle MH_one;
        static final MethodHandle MH_inc;
        static final MethodHandle MH_mult;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_fin;

        static final MethodType MT_loop = methodType(int.class, LOOP_WITH_VIRTUALS, int.class);

        static {
            try {
                MH_one = LOOKUP.findStatic(LOOP_WITH_VIRTUALS, "one", MT_one);
                MH_inc = LOOKUP.findVirtual(LOOP_WITH_VIRTUALS, "inc", MT_inc);
                MH_mult = LOOKUP.findVirtual(LOOP_WITH_VIRTUALS, "mult", MT_mult);
                MH_pred = LOOKUP.findVirtual(LOOP_WITH_VIRTUALS, "pred", MT_pred);
                MH_fin = LOOKUP.findVirtual(LOOP_WITH_VIRTUALS, "fin", MT_fin);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        static MethodHandle permute(MethodHandle h) {
            // The handles representing virtual methods need to be rearranged to match the required order of arguments
            // (loop-local state comes first, then loop arguments). As the receiver comes first in the signature but is
            // a loop argument, it must be moved to the appropriate position in the signature.
            return MethodHandles.permuteArguments(h,
                    methodType(h.type().returnType(), int.class, int.class, LOOP_WITH_VIRTUALS, int.class), 2, 0, 1, 3);
        }

    }

    static class While {

        static int zero(int limit) {
            return 0;
        }

        static boolean pred(int i, int limit) {
            return i < limit;
        }

        static int step(int i, int limit) {
            return i + 1;
        }

        static String initString() {
            return "a";
        }

        static boolean predString(String s) {
            return s.length() != 1;
        }

        static String stepString(String s) {
            return s + "a";
        }

        static List<String> zipInitZip(Iterator<String> a, Iterator<String> b) {
            return new ArrayList<>();
        }

        static boolean zipPred(List<String> zip, Iterator<String> a, Iterator<String> b) {
            return a.hasNext() && b.hasNext();
        }

        static List<String> zipStep(List<String> zip, Iterator<String> a, Iterator<String> b) {
            zip.add(a.next());
            zip.add(b.next());
            return zip;
        }

        static final Class<While> WHILE = While.class;

        static final MethodType MT_zero = methodType(int.class, int.class);
        static final MethodType MT_pred = methodType(boolean.class, int.class, int.class);
        static final MethodType MT_fn = methodType(int.class, int.class, int.class);
        static final MethodType MT_initString = methodType(String.class);
        static final MethodType MT_predString = methodType(boolean.class, String.class);
        static final MethodType MT_stepString = methodType(String.class, String.class);
        static final MethodType MT_zipInitZip = methodType(List.class, Iterator.class, Iterator.class);
        static final MethodType MT_zipPred = methodType(boolean.class, List.class, Iterator.class, Iterator.class);
        static final MethodType MT_zipStep = methodType(List.class, List.class, Iterator.class, Iterator.class);

        static final MethodHandle MH_zero;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_step;
        static final MethodHandle MH_initString;
        static final MethodHandle MH_predString;
        static final MethodHandle MH_stepString;
        static final MethodHandle MH_zipInitZip;
        static final MethodHandle MH_zipPred;
        static final MethodHandle MH_zipStep;

        static final MethodType MT_while = methodType(int.class, int.class);
        static final MethodType MT_string = methodType(String.class);
        static final MethodType MT_zip = methodType(List.class, Iterator.class, Iterator.class);

        static {
            try {
                MH_zero = LOOKUP.findStatic(WHILE, "zero", MT_zero);
                MH_pred = LOOKUP.findStatic(WHILE, "pred", MT_pred);
                MH_step = LOOKUP.findStatic(WHILE, "step", MT_fn);
                MH_initString = LOOKUP.findStatic(WHILE, "initString", MT_initString);
                MH_predString = LOOKUP.findStatic(WHILE, "predString", MT_predString);
                MH_stepString = LOOKUP.findStatic(WHILE, "stepString", MT_stepString);
                MH_zipInitZip = LOOKUP.findStatic(WHILE, "zipInitZip", MT_zipInitZip);
                MH_zipPred = LOOKUP.findStatic(WHILE, "zipPred", MT_zipPred);
                MH_zipStep = LOOKUP.findStatic(WHILE, "zipStep", MT_zipStep);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class Counted {

        static String start(String arg) {
            return arg;
        }

        static String step(int counter, String v, String arg) {
            return "na " + v;
        }

        static void stepUpdateArray(int counter, int[] a) {
            ++a[0];
        }

        static void printHello(int counter) {
            System.out.print("hello");
        }

        static final Class<Counted> COUNTED = Counted.class;

        static final MethodType MT_start = methodType(String.class, String.class);
        static final MethodType MT_step = methodType(String.class, int.class, String.class, String.class);
        static final MethodType MT_stepUpdateArray = methodType(void.class, int.class, int[].class);
        static final MethodType MT_printHello = methodType(void.class, int.class);

        static final MethodHandle MH_13;
        static final MethodHandle MH_m5;
        static final MethodHandle MH_8;
        static final MethodHandle MH_start;
        static final MethodHandle MH_step;
        static final MethodHandle MH_stepUpdateArray;
        static final MethodHandle MH_printHello;

        static final MethodType MT_counted = methodType(String.class, String.class);
        static final MethodType MT_arrayCounted = methodType(void.class, int[].class);
        static final MethodType MT_countedPrinting = methodType(void.class);

        static {
            try {
                MH_13 = MethodHandles.constant(int.class, 13);
                MH_m5 = MethodHandles.constant(int.class, -5);
                MH_8 = MethodHandles.constant(int.class, 8);
                MH_start = LOOKUP.findStatic(COUNTED, "start", MT_start);
                MH_step = LOOKUP.findStatic(COUNTED, "step", MT_step);
                MH_stepUpdateArray = LOOKUP.findStatic(COUNTED, "stepUpdateArray", MT_stepUpdateArray);
                MH_printHello = LOOKUP.findStatic(COUNTED, "printHello", MT_printHello);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class Iterate {

        static Iterator<Integer> sumIterator(Integer[] a) {
            return Arrays.asList(a).iterator();
        }

        static int sumInit(Integer[] a) {
            return 0;
        }

        static int sumStep(int s, int e, Integer[] a) {
            return s + e;
        }

        static List<String> reverseInit(List<String> l) {
            return new ArrayList<>();
        }

        static List<String> reverseStep(String e, List<String> r, List<String> l) {
            r.add(0, e);
            return r;
        }

        static int lengthInit(List<Double> l) {
            return 0;
        }

        static int lengthStep(Object o, int len, List<Double> l) {
            return len + 1;
        }

        static List<String> mapInit(List<String> l) {
            return new ArrayList<>();
        }

        static List<String> mapStep(String e, List<String> r, List<String> l) {
            r.add(e.toUpperCase());
            return r;
        }

        static void printStep(String s, List<String> l) {
            System.out.print(s);
        }

        static final Class<Iterate> ITERATE = Iterate.class;

        static final MethodType MT_sumIterator = methodType(Iterator.class, Integer[].class);

        static final MethodType MT_sumInit = methodType(int.class, Integer[].class);
        static final MethodType MT_reverseInit = methodType(List.class, List.class);
        static final MethodType MT_lenghInit = methodType(int.class, List.class);
        static final MethodType MT_mapInit = methodType(List.class, List.class);

        static final MethodType MT_sumStep = methodType(int.class, int.class, int.class, Integer[].class);
        static final MethodType MT_reverseStep = methodType(List.class, String.class, List.class, List.class);
        static final MethodType MT_lengthStep = methodType(int.class, Object.class, int.class, List.class);
        static final MethodType MT_mapStep = methodType(List.class, String.class, List.class, List.class);
        static final MethodType MT_printStep = methodType(void.class, String.class, List.class);

        static final MethodHandle MH_sumIterator;
        static final MethodHandle MH_sumInit;
        static final MethodHandle MH_sumStep;
        static final MethodHandle MH_printStep;

        static final MethodHandle MH_reverseInit;
        static final MethodHandle MH_reverseStep;

        static final MethodHandle MH_lengthInit;
        static final MethodHandle MH_lengthStep;

        static final MethodHandle MH_mapInit;
        static final MethodHandle MH_mapStep;

        static final MethodType MT_sum = methodType(int.class, Integer[].class);
        static final MethodType MT_reverse = methodType(List.class, List.class);
        static final MethodType MT_length = methodType(int.class, List.class);
        static final MethodType MT_map = methodType(List.class, List.class);
        static final MethodType MT_print = methodType(void.class, List.class);

        static {
            try {
                MH_sumIterator = LOOKUP.findStatic(ITERATE, "sumIterator", MT_sumIterator);
                MH_sumInit = LOOKUP.findStatic(ITERATE, "sumInit", MT_sumInit);
                MH_sumStep = LOOKUP.findStatic(ITERATE, "sumStep", MT_sumStep);
                MH_reverseInit = LOOKUP.findStatic(ITERATE, "reverseInit", MT_reverseInit);
                MH_reverseStep = LOOKUP.findStatic(ITERATE, "reverseStep", MT_reverseStep);
                MH_lengthInit = LOOKUP.findStatic(ITERATE, "lengthInit", MT_lenghInit);
                MH_lengthStep = LOOKUP.findStatic(ITERATE, "lengthStep", MT_lengthStep);
                MH_mapInit = LOOKUP.findStatic(ITERATE, "mapInit", MT_mapInit);
                MH_mapStep = LOOKUP.findStatic(ITERATE, "mapStep", MT_mapStep);
                MH_printStep = LOOKUP.findStatic(ITERATE, "printStep", MT_printStep);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class TryFinally {

        static String greet(String whom) {
            return "Hello, " + whom;
        }

        static String exclaim(Throwable t, String r, String whom) {
            return r + "!";
        }

        static void print(String what) {
            System.out.print("Hello, " + what);
        }

        static void printMore(Throwable t, String what) {
            System.out.println("!");
        }

        static String greetMore(String first, String second) {
            return "Hello, " + first + " and " + second;
        }

        static String exclaimMore(Throwable t, String r, String first) {
            return r + " (but " + first + " first)!";
        }

        static void voidTarget() {}

        static void voidCleanup(Throwable t, int a) {}

        static final Class<TryFinally> TRY_FINALLY = TryFinally.class;

        static final MethodType MT_greet = methodType(String.class, String.class);
        static final MethodType MT_exclaim = methodType(String.class, Throwable.class, String.class, String.class);
        static final MethodType MT_print = methodType(void.class, String.class);
        static final MethodType MT_printMore = methodType(void.class, Throwable.class, String.class);
        static final MethodType MT_greetMore = methodType(String.class, String.class, String.class);
        static final MethodType MT_exclaimMore = methodType(String.class, Throwable.class, String.class, String.class);
        static final MethodType MT_voidTarget = methodType(void.class);
        static final MethodType MT_voidCleanup = methodType(void.class, Throwable.class, int.class);

        static final MethodHandle MH_greet;
        static final MethodHandle MH_exclaim;
        static final MethodHandle MH_print;
        static final MethodHandle MH_printMore;
        static final MethodHandle MH_greetMore;
        static final MethodHandle MH_exclaimMore;
        static final MethodHandle MH_voidTarget;
        static final MethodHandle MH_voidCleanup;

        static final MethodType MT_hello = methodType(String.class, String.class);
        static final MethodType MT_printHello = methodType(void.class, String.class);
        static final MethodType MT_moreHello = methodType(String.class, String.class, String.class);

        static {
            try {
                MH_greet = LOOKUP.findStatic(TRY_FINALLY, "greet", MT_greet);
                MH_exclaim = LOOKUP.findStatic(TRY_FINALLY, "exclaim", MT_exclaim);
                MH_print = LOOKUP.findStatic(TRY_FINALLY, "print", MT_print);
                MH_printMore = LOOKUP.findStatic(TRY_FINALLY, "printMore", MT_printMore);
                MH_greetMore = LOOKUP.findStatic(TRY_FINALLY, "greetMore", MT_greetMore);
                MH_exclaimMore = LOOKUP.findStatic(TRY_FINALLY, "exclaimMore", MT_exclaimMore);
                MH_voidTarget = LOOKUP.findStatic(TRY_FINALLY, "voidTarget", MT_voidTarget);
                MH_voidCleanup = LOOKUP.findStatic(TRY_FINALLY, "voidCleanup", MT_voidCleanup);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class Fold {

        static int adder(int a, int b, int c) {
            return a + b + c;
        }

        static int adder1(int a, int b) {
            return a + b;
        }

        static int multer(int x, int q, int r, int s) {
            return x * q * r * s;
        }

        static int str(boolean b1, String s, boolean b2, int x) {
            return b1 && s.equals(String.valueOf(b2)) ? x : -x;
        }

        static boolean comb(String s, boolean b2) {
            return !s.equals(b2);
        }

        static String comb2(boolean b2, int x) {
            int ib = b2 ? 1 : 0;
            return ib == x ? "true" : "false";
        }

        static final Class<Fold> FOLD = Fold.class;

        static final MethodType MT_adder = methodType(int.class, int.class, int.class, int.class);
        static final MethodType MT_adder1 = methodType(int.class, int.class, int.class);
        static final MethodType MT_multer = methodType(int.class, int.class, int.class, int.class, int.class);
        static final MethodType MT_str = methodType(int.class, boolean.class, String.class, boolean.class, int.class);
        static final MethodType MT_comb = methodType(boolean.class, String.class, boolean.class);
        static final MethodType MT_comb2 = methodType(String.class, boolean.class, int.class);

        static final MethodHandle MH_adder;
        static final MethodHandle MH_adder1;
        static final MethodHandle MH_multer;
        static final MethodHandle MH_str;
        static final MethodHandle MH_comb;
        static final MethodHandle MH_comb2;

        static final MethodType MT_folded1 = methodType(int.class, int.class, int.class, int.class);
        static final MethodType MT_folded2 = methodType(int.class, String.class, boolean.class, int.class);
        static final MethodType MT_folded3 = methodType(int.class, boolean.class, boolean.class, int.class);

        static {
            try {
                MH_adder = LOOKUP.findStatic(FOLD, "adder", MT_adder);
                MH_adder1 = LOOKUP.findStatic(FOLD, "adder1", MT_adder1);
                MH_multer = LOOKUP.findStatic(FOLD, "multer", MT_multer);
                MH_str = LOOKUP.findStatic(FOLD, "str", MT_str);
                MH_comb = LOOKUP.findStatic(FOLD, "comb", MT_comb);
                MH_comb2 = LOOKUP.findStatic(FOLD, "comb2", MT_comb2);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    static class SpreadCollect {

        static String forSpreading(String s1, int i1, int i2, int i3, String s2) {
            return s1 + i1 + i2 + i3 + s2;
        }

        static String forCollecting(String s1, int[] is, String s2) {
            StringBuilder sb = new StringBuilder(s1);
            for (int i : is) {
                sb.append(i);
            }
            return sb.append(s2).toString();
        }

        static String forCollectingLeading(int[] is, String s) {
            return forCollecting("", is, s);
        }

        static final Class<SpreadCollect> SPREAD_COLLECT = SpreadCollect.class;

        static final MethodType MT_forSpreading = methodType(String.class, String.class, int.class, int.class, int.class, String.class);
        static final MethodType MT_forCollecting = methodType(String.class, String.class, int[].class, String.class);
        static final MethodType MT_forCollectingLeading = methodType(String.class, int[].class, String.class);

        static final MethodHandle MH_forSpreading;
        static final MethodHandle MH_forCollecting;
        static final MethodHandle MH_forCollectingLeading;

        static final MethodType MT_spreader = methodType(String.class, String.class, int[].class, String.class);
        static final MethodType MT_collector0 = methodType(String.class, String.class, String.class);
        static final MethodType MT_collector1 = methodType(String.class, String.class, int.class, String.class);
        static final MethodType MT_collector2 = methodType(String.class, String.class, int.class, int.class, String.class);
        static final MethodType MT_collector3 = methodType(String.class, String.class, int.class, int.class, int.class, String.class);
        static final MethodType MT_collectorLeading1 = methodType(String.class, int.class, String.class);
        static final MethodType MT_collectorLeading2 = methodType(String.class, int.class, int.class, String.class);
        static final MethodType MT_collectorLeading3 = methodType(String.class, int.class, int.class, int.class, String.class);

        static final String NONE_ERROR = "zero array length in MethodHandle.asCollector";

        static {
            try {
                MH_forSpreading = LOOKUP.findStatic(SPREAD_COLLECT, "forSpreading", MT_forSpreading);
                MH_forCollecting = LOOKUP.findStatic(SPREAD_COLLECT, "forCollecting", MT_forCollecting);
                MH_forCollectingLeading = LOOKUP.findStatic(SPREAD_COLLECT, "forCollectingLeading", MT_forCollectingLeading);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

    }

    static class FindSpecial {

        interface I1 {
            default String m() {
                return "I1.m";
            }
        }

        interface I2 {
            default String m() {
                return "I2.m";
            }
        }

        interface I3 {
            String q();
        }

        static class C implements I1, I2, I3 {
            public String m() {
                return I1.super.m();
            }
            public String q() {
                return "q";
            }
        }

        static final String ABSTRACT_ERROR = "no such method: test.java.lang.invoke.T8139885$FindSpecial$I3.q()String/invokeSpecial";

    }

    //
    // Auxiliary methods.
    //

    static MethodHandle[] mha(MethodHandle... mhs) {
        return mhs;
    }

}
