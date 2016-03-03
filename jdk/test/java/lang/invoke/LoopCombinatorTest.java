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
 * @bug 8150635
 * @run testng/othervm -ea -esa test.java.lang.invoke.LoopCombinatorTest
 */

package test.java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.*;

import static java.lang.invoke.MethodType.methodType;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.*;

/**
 * Tests for the loop combinators introduced in JEP 274.
 */
public class LoopCombinatorTest {

    static final Lookup LOOKUP = MethodHandles.lookup();

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

    @DataProvider
    static Object[][] negativeTestData() {
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
        return new Object[][] {
                {null, "null or no clauses passed"},
                {new MethodHandle[][]{}, "null or no clauses passed"},
                {new MethodHandle[][]{{null, Fac.MH_inc}, {Fac.MH_one, null, Fac.MH_mult, Fac.MH_pred, Fac.MH_fin}},
                        "All loop clauses must be represented as MethodHandle arrays with at most 4 elements."},
                {new MethodHandle[][]{{null, Fac.MH_inc}, null}, "null clauses are not allowed"},
                {new MethodHandle[][]{{Fac.MH_zero, Fac.MH_dot}},
                        "clause 0: init and step return types must match: int != void"},
                {new MethodHandle[][]{{ii}, {id}, {i3}},
                        "found non-effectively identical init parameter type lists: " + inits +
                                " (common suffix: " + ints + ")"},
                {new MethodHandle[][]{{null, Fac.MH_inc, null, Fac.MH_fin}, {null, Fac.MH_inc, null, Fac.MH_inc},
                        {null, Counted.MH_start, null, Counted.MH_step}},
                        "found non-identical finalizer return types: " + finis + " (return type: int)"},
                {new MethodHandle[][]{{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, Fac.MH_mult, null, Fac.MH_fin},
                        {null, Fac.MH_dot}}, "no predicate found: " + preds1},
                {new MethodHandle[][]{{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, Fac.MH_mult, Fac.MH_fin, Fac.MH_fin},
                        {null, Fac.MH_dot}}, "predicates must have boolean return type: " + preds2},
                {new MethodHandle[][]{{Fac.MH_zero, Fac.MH_inc}, {Fac.MH_one, eek, Fac.MH_pred, Fac.MH_fin},
                        {null, Fac.MH_dot}},
                        "found non-effectively identical parameter type lists:\nstep: " + nesteps +
                                "\npred: " + nepreds + "\nfini: " + nefinis + " (common parameter sequence: " + ints + ")"},
                {new MethodHandle[][]{{null, LoopWithVirtuals.MH_inc},
                        {LoopWithVirtuals.MH_one, LoopWithVirtuals.MH_mult, LoopWithVirtuals.MH_pred, LoopWithVirtuals.MH_fin}},
                        "found non-effectively identical parameter type lists:\nstep: " + lvsteps +
                                "\npred: " + lvpreds + "\nfini: " + lvfinis + " (common parameter sequence: " + ints + ")"}
        };
    }

    static final MethodHandle MH_loop;

    static {
        try {
            MH_loop = LOOKUP.findStatic(MethodHandles.class, "loop", methodType(MethodHandle.class, MethodHandle[][].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test(dataProvider = "negativeTestData")
    public static void testLoopNegative(MethodHandle[][] clauses, String expectedMessage) throws Throwable {
        boolean caught = false;
        try {
            MH_loop.invokeWithArguments(clauses);
        } catch (IllegalArgumentException iae) {
            assertEquals(expectedMessage, iae.getMessage());
            caught = true;
        }
        assertTrue(caught);
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
    public static void testDoWhileNullInit() throws Throwable {
        While w = new While();
        int v = 5;
        MethodHandle loop = MethodHandles.doWhileLoop(null, While.MH_voidBody.bindTo(w), While.MH_voidPred.bindTo(w));
        assertEquals(While.MT_void, loop.type());
        loop.invoke(v);
        assertEquals(v, w.i);
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
    public static void testWhileNullInit() throws Throwable {
        While w = new While();
        int v = 5;
        MethodHandle loop = MethodHandles.whileLoop(null, While.MH_voidPred.bindTo(w), While.MH_voidBody.bindTo(w));
        assertEquals(While.MT_void, loop.type());
        loop.invoke(v);
        assertEquals(v, w.i);
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

        private int i = 0;

        void voidBody(int k) {
            ++i;
        }

        boolean voidPred(int k) {
            return i < k;
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
        static final MethodType MT_voidBody = methodType(void.class, int.class);
        static final MethodType MT_voidPred = methodType(boolean.class, int.class);

        static final MethodHandle MH_zero;
        static final MethodHandle MH_pred;
        static final MethodHandle MH_step;
        static final MethodHandle MH_initString;
        static final MethodHandle MH_predString;
        static final MethodHandle MH_stepString;
        static final MethodHandle MH_zipInitZip;
        static final MethodHandle MH_zipPred;
        static final MethodHandle MH_zipStep;
        static final MethodHandle MH_voidBody;
        static final MethodHandle MH_voidPred;

        static final MethodType MT_while = methodType(int.class, int.class);
        static final MethodType MT_string = methodType(String.class);
        static final MethodType MT_zip = methodType(List.class, Iterator.class, Iterator.class);
        static final MethodType MT_void = methodType(void.class, int.class);

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
                MH_voidBody = LOOKUP.findVirtual(WHILE, "voidBody", MT_voidBody);
                MH_voidPred = LOOKUP.findVirtual(WHILE, "voidPred", MT_voidPred);
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

}
