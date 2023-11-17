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
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Testing public API of Gatherer
 * @enablePreview
 * @run junit GathererAPITest
 */

public class GathererAPITest {
    final static Supplier<Void> initializer = () -> (Void)null;
    final static Gatherer.Integrator<Void, Integer, Integer> integrator = (v,e,d) -> d.push(e);
    final static BinaryOperator<Void> combiner = (l,r) -> l;
    final static BiConsumer<Void,Gatherer.Downstream<? super Integer>> finisher = (v,d) -> {};

    final static Supplier<Void> nullInitializer = null;
    final static Gatherer.Integrator<Void, Integer, Integer> nullIntegrator = null;
    final static BinaryOperator<Void> nullCombiner = null;
    final static BiConsumer<Void,Gatherer.Downstream<? super Integer>> nullFinisher = null;

    private final static <T> Gatherer<T,?,T> passthrough() {
        return Gatherer.of(
                () -> (Void)null,
                Gatherer.Integrator.<Void,T,T>ofGreedy((v,e,d) -> d.push(e)),
                (l,r) -> l,
                (v,d) -> {}
        );
    }

    private final static <T,A,R> Gatherer<T,A,R> verifyGathererContract(Gatherer<T,A,R> gatherer) {
        // basics
        assertNotNull(gatherer);

        // components
        assertNotNull(gatherer.initializer());
        assertNotNull(gatherer.integrator());
        assertNotNull(gatherer.combiner());
        assertNotNull(gatherer.finisher());
        assertNotNull(gatherer.andThen(passthrough()));

        return gatherer;
    }

    private final static <T,A,R> Gatherer<T,A,R> verifyGathererStructure(
            Gatherer<T,A,R> gatherer,
            Supplier<A> expectedSupplier,
            Gatherer.Integrator<A,T,R> expectedIntegrator,
            BinaryOperator<A> expectedCombiner,
            BiConsumer<A,Gatherer.Downstream<? super R>> expectedFinisher
    ) {
        // basics
        assertNotNull(gatherer);

        // components
        assertSame(expectedSupplier, gatherer.initializer());
        assertSame(expectedIntegrator, gatherer.integrator());
        assertSame(expectedCombiner, gatherer.combiner());
        assertSame(expectedFinisher, gatherer.finisher());

        return gatherer;
    }

    @Test
    public void testGathererDefaults() {
        final Gatherer.Integrator<Void,Void,Void> expectedIntegrator =
                (a,b,c) -> false;

        class Test implements Gatherer<Void,Void,Void> {
            @Override
            public Integrator<Void, Void, Void> integrator() {
                return expectedIntegrator;
            }
        }

        var t = new Test();
        assertSame(Gatherer.<Void>defaultInitializer(), t.initializer());
        assertSame(expectedIntegrator, t.integrator());
        assertSame(Gatherer.<Void>defaultCombiner(), t.combiner());
        assertSame(Gatherer.<Void,Gatherer.Downstream<? super Void>>defaultFinisher(), t.finisher());
    }

    @Test
    public void testDownstreamDefaults() {
        class Test implements Gatherer.Downstream<Void> {
            @Override public boolean push(Void v) { return false; }
        }

        var t = new Test();
        assertEquals(false, t.isRejecting());
    }

    @Test
    public void testGathererFactoriesNPE() {
        assertThrows(NullPointerException.class,
                () -> Gatherer.of(nullInitializer, integrator, combiner, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.of(initializer, nullIntegrator, combiner, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.of(initializer, integrator, nullCombiner, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.of(initializer, integrator, combiner, nullFinisher));

        assertThrows(NullPointerException.class,
                () -> Gatherer.of(nullIntegrator));

        assertThrows(NullPointerException.class,
                () -> Gatherer.of(nullIntegrator, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.of(integrator, nullFinisher));

        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(nullInitializer, integrator));
        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(initializer, nullIntegrator));

        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(nullIntegrator));

        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(nullIntegrator, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(integrator, nullFinisher));

        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(nullInitializer, integrator, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(initializer, nullIntegrator, finisher));
        assertThrows(NullPointerException.class,
                () -> Gatherer.ofSequential(initializer, integrator, nullFinisher));
    }

    @Test
    public void testGathererFactoriesAPI() {
        final var defaultInitializer = Gatherer.<Void>defaultInitializer();
        final var defaultCombiner = Gatherer.<Void>defaultCombiner();
        final var defaultFinisher = Gatherer.<Void,Integer>defaultFinisher();

        var g1 = verifyGathererContract(passthrough()); // Quis custodiet ipsos custodes?
        verifyGathererContract(g1.andThen(g1));

        var g2 = verifyGathererContract(Gatherer.of(integrator));
        verifyGathererContract(g2.andThen(g2));
        assertSame(defaultInitializer, g2.initializer());
        assertSame(integrator, g2.integrator());
        assertNotSame(defaultCombiner, g2.combiner());
        assertSame(defaultFinisher, g2.finisher());

        var g3 = verifyGathererContract(Gatherer.of(integrator, finisher));
        verifyGathererContract(g3.andThen(g3));
        assertSame(integrator, g3.integrator());
        assertNotSame(defaultCombiner, g3.combiner());
        assertSame(finisher, g3.finisher());

        var g4 = verifyGathererContract(Gatherer.ofSequential(integrator));
        verifyGathererContract(g4.andThen(g4));
        verifyGathererStructure(g4, defaultInitializer, integrator, defaultCombiner, defaultFinisher);

        var g5 = verifyGathererContract(Gatherer.ofSequential(initializer, integrator));
        verifyGathererContract(g5.andThen(g5));
        verifyGathererStructure(g5, initializer, integrator, defaultCombiner, defaultFinisher);

        var g6 = verifyGathererContract(Gatherer.ofSequential(integrator, finisher));
        verifyGathererContract(g6.andThen(g6));
        verifyGathererStructure(g6, defaultInitializer, integrator, defaultCombiner, finisher);

        var g7 = verifyGathererContract(Gatherer.ofSequential(initializer, integrator, finisher));
        verifyGathererContract(g7.andThen(g7));
        verifyGathererStructure(g7, initializer, integrator, defaultCombiner, finisher);

        var g8 = verifyGathererContract(Gatherer.of(initializer, integrator, combiner, finisher));
        verifyGathererContract(g8.andThen(g8));
        verifyGathererStructure(g8, initializer, integrator, combiner, finisher);
    }

    @Test
    public void testGathererVariance() {

        // Make sure that Gatherers can pass-through type
        Gatherer<Number,?,Number> nums = Gatherer.of((unused, element, downstream) -> downstream.push(element));

        // Make sure that Gatherers can upcast the output type from the input type
        Gatherer<Number,?,Object> upcast = Gatherer.of((unused, element, downstream) -> downstream.push(element));

        // Make sure that Gatherers can consume a supertype of the Stream output
        assertEquals(List.of(1,2,3,4,5), Stream.<Integer>of(1,2,3,4,5).gather(nums).toList());

        Gatherer<Integer,?,Integer> ints = Gatherer.of((unused, element, downstream) -> downstream.push(element));

        // Make sure that Gatherers can be composed where the output is a subtype of the input type of the next
        Gatherer<Integer,?,Number> composition = ints.andThen(nums);

        // Make sure that composition works transitively, typing-wise
        Gatherer<Integer,?,Object> upcastComposition = ints.andThen(nums.andThen(upcast));

        assertEquals(List.of(1,2,3,4,5), Stream.<Integer>of(1,2,3,4,5).gather(composition).toList());
        assertEquals(List.of(1,2,3,4,5), Stream.<Integer>of(1,2,3,4,5).gather(upcastComposition).toList());
    }
}
