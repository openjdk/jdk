/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTest2 extends ConditionalEliminationTestBase {

    public static Object field;

    static class Entry {

        final String name;

        Entry(String name) {
            this.name = name;
        }
    }

    static class EntryWithNext extends Entry {

        EntryWithNext(String name, Entry next) {
            super(name);
            this.next = next;
        }

        final Entry next;
    }

    static class A {
    }

    static class B extends A {
    }

    static class C extends A {
    }

    public static Entry search(Entry start, String name, Entry alternative) {
        Entry current = start;
        do {
            while (current instanceof EntryWithNext) {
                if (name != null && current.name == name) {
                    current = null;
                } else {
                    Entry next = ((EntryWithNext) current).next;
                    current = next;
                }
            }

            if (current != null) {
                if (current.name.equals(name)) {
                    return current;
                }
            }
            if (current == alternative) {
                return null;
            }
            current = alternative;

        } while (true);
    }

    public static int testRedundantComparesSnippet(int[] array) {
        if (array == null) {
            return 0;
        }
        return array[0] + array[1] + array[2] + array[3];
    }

    @Test
    public void testRedundantCompares() {
        StructuredGraph graph = parseEager("testRedundantComparesSnippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());

        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new FloatingReadPhase().apply(graph);
        new ConditionalEliminationPhase(true).apply(graph, context);
        canonicalizer.apply(graph, context);

        assertDeepEquals(1, graph.getNodes().filter(GuardNode.class).count());
    }

    public static String testInstanceOfCheckCastSnippet(Object e) {
        if (e instanceof Entry) {
            return ((Entry) e).name;
        }
        return null;
    }

    @Test
    public void testInstanceOfCheckCastLowered() {
        StructuredGraph graph = parseEager("testInstanceOfCheckCastSnippet", AllowAssumptions.YES);

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());

        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new ConditionalEliminationPhase(true).apply(graph, context);
        canonicalizer.apply(graph, context);

        assertDeepEquals(0, graph.getNodes().filter(GuardNode.class).count());
    }

    private void checkInstanceOfCount(String methodName, int count) {
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());

        canonicalizer.apply(graph, context);
        new ConditionalEliminationPhase(true).apply(graph, context);
        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After ConditionalEliminationPhase");
        canonicalizer.apply(graph, context);

        Assert.assertEquals(count, graph.getNodes().filter(InstanceOfNode.class).count());
    }

    public static A testRedundantInstanceOfClass(Object value) {
        if (value != null && value.getClass() == A.class) {
            return (A) value;
        }
        return null;
    }

    public static Object testRedundantInstanceOfArray(Object value) {
        if (value != null && value.getClass() == Object[].class) {
            return ((Object[]) value)[0];
        }
        return null;
    }

    public static boolean testRedundantInstanceOfPrecise(Object value) {
        if (value != null && value.getClass() == A.class) {
            return value instanceof A;
        }
        return false;
    }

    public static boolean testRedundantInstanceOfImplicitNonNull(Object value) {
        if (value.getClass() == A.class) {
            return value instanceof A;
        }
        return false;
    }

    @Test
    public void testRedundantInstanceOf() {
        checkInstanceOfCount("testRedundantInstanceOfClass", 1);
        checkInstanceOfCount("testRedundantInstanceOfArray", 1);
        checkInstanceOfCount("testRedundantInstanceOfPrecise", 1);
        checkInstanceOfCount("testRedundantInstanceOfImplicitNonNull", 1);
    }

    public static boolean testNonRedundantInstanceOfClass(Object value) {
        if (value instanceof A) {
            return (value != null && value.getClass() == A.class);
        }
        return false;
    }

    public static boolean testNonRedundantInstanceOfArray(Object value) {
        if (value instanceof Object[]) {
            return (value != null && value.getClass() == Object[].class);
        }
        return false;
    }

    public static boolean testNonRedundantInstanceOfImplicitNonNull(Object value) {
        if (value instanceof Object[]) {
            return value.getClass() == Object[].class;
        }
        return false;
    }

    @Test
    public void testNonRedundantInstanceOf() {
        checkInstanceOfCount("testNonRedundantInstanceOfClass", 2);
        checkInstanceOfCount("testNonRedundantInstanceOfArray", 2);
        checkInstanceOfCount("testNonRedundantInstanceOfImplicitNonNull", 2);
    }
}
