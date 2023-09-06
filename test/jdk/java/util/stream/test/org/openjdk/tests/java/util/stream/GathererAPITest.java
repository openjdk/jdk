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
package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import static java.util.stream.LambdaTestHelpers.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;


/**
 * GathererAPITest
 *
 * @author Viktor Klang
 */
@Test
public class GathererAPITest extends OpTestCase {

    final static List<Integer> TEST_STREAM_SIZES = List.of(0, 1, 10, 33, 99, 9999);
    final static Supplier<Void> initializer = () -> (Void)null;
    final static Gatherer.Integrator<Void, Integer, Integer> integrator = (v,e,d) -> d.flush(e);
    final static BinaryOperator<Void> combiner = (l,r) -> l;
    final static BiConsumer<Void,Gatherer.Sink<? super Integer>> finisher = (v,d) -> {};

    final static Supplier<Void> nullInitializer = null;
    final static Gatherer.Integrator<Void, Integer, Integer> nullIntegrator = null;
    final static BinaryOperator<Void> nullCombiner = null;
    final static BiConsumer<Void,Gatherer.Sink<? super Integer>> nullFinisher = null;

    private final static <T> Gatherer<T,?,T> passthrough() {
        return Gatherer.of(
                () -> (Void)null,
                Gatherer.Integrator.<Void,T,T>ofGreedy((v,e,d) -> d.flush(e)),
                (l,r) -> l,
                (v,d) -> {}
        );
    }

    private final static void assertThrowsNPE(Supplier<? extends Object> supplier) {
        try {
            var discard = supplier.get();
        } catch (NullPointerException npe) {
            return;
        }
        fail("Expected NullPointerException but wasn't thrown!");
    }

    private final static <T> void assertThrowsUOE(Supplier<T> supplier) {
        try {
            var discard = supplier.get();
        } catch (UnsupportedOperationException uoe) {
            return;
        }
        fail("Expected NullPointerException but wasn't thrown!");
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

        //.collect()
        var c = Collectors.<R>toList();
        var tc = gatherer.collect(c);

        assertNotNull(tc);
        assertTrue(tc.gatherer() == gatherer);
        assertTrue(tc.collector() == c);

        var ttc = tc.gatherer().andThen(passthrough()).collect(tc.collector());
        assertNotNull(ttc);
        assertNotNull(ttc.gatherer());
        assertNotNull(ttc.collector());

        assertTrue(tc.collector() == c);

        return gatherer;
    }

    public void testGathererFactoriesNPE() {
        assertThrowsNPE(() -> Gatherer.of(nullInitializer, integrator, combiner, finisher));
        assertThrowsNPE(() -> Gatherer.of(initializer, nullIntegrator, combiner, finisher));
        assertThrowsNPE(() -> Gatherer.of(initializer, integrator, nullCombiner, finisher));
        assertThrowsNPE(() -> Gatherer.of(initializer, integrator, combiner, nullFinisher));

        assertThrowsNPE(() -> Gatherer.of(nullIntegrator, combiner));
        assertThrowsNPE(() -> Gatherer.of(integrator, nullCombiner));

        assertThrowsNPE(() -> Gatherer.of(nullIntegrator, combiner, finisher));
        assertThrowsNPE(() -> Gatherer.of(integrator, nullCombiner, finisher));
        assertThrowsNPE(() -> Gatherer.of(integrator, combiner, nullFinisher));

        assertThrowsNPE(() -> Gatherer.ofSequential(nullInitializer, integrator));
        assertThrowsNPE(() -> Gatherer.ofSequential(initializer, nullIntegrator));

        assertThrowsNPE(() -> Gatherer.ofSequential(nullIntegrator));

        assertThrowsNPE(() -> Gatherer.ofSequential(nullIntegrator, finisher));
        assertThrowsNPE(() -> Gatherer.ofSequential(integrator, nullFinisher));

        assertThrowsNPE(() -> Gatherer.ofSequential(nullInitializer, integrator, finisher));
        assertThrowsNPE(() -> Gatherer.ofSequential(initializer, nullIntegrator, finisher));
        assertThrowsNPE(() -> Gatherer.ofSequential(initializer, integrator, nullFinisher));
    }

    public void testGathererFactoriesAPI() {
        var g1 = verifyGathererContract(passthrough()); // Quis custodiet ipsos custodes?
        verifyGathererContract(g1.andThen(g1));

        var g2 = verifyGathererContract(Gatherer.of(integrator, combiner));
        verifyGathererContract(g2.andThen(g2));

        var g3 = verifyGathererContract(Gatherer.of(integrator, combiner, finisher));
        verifyGathererContract(g3.andThen(g3));

        var g4 = verifyGathererContract(Gatherer.ofSequential(initializer, integrator));
        verifyGathererContract(g4.andThen(g4));

        var g5 = verifyGathererContract(Gatherer.ofSequential(integrator));
        verifyGathererContract(g5.andThen(g5));

        var g6 = verifyGathererContract(Gatherer.ofSequential(integrator, finisher));
        verifyGathererContract(g6.andThen(g6));

        var g7 = verifyGathererContract(Gatherer.ofSequential(initializer, integrator, finisher));
        verifyGathererContract(g7.andThen(g7));

        var g8 = verifyGathererContract(Gatherer.of(initializer, integrator, combiner, finisher));
        verifyGathererContract(g8.andThen(g8));
    }
}
