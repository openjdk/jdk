/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test.ea;

import static org.graalvm.compiler.graph.iterators.NodePredicates.isA;

import java.util.List;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

//JaCoCo Exclude

/**
 * This base class for all Escape Analysis tests does not contain tests itself, therefore it is not
 * automatically excluded from JaCoCo. Since it includes code that is used in the test snippets, it
 * needs to be excluded manually.
 */
public class EATestBase extends GraalCompilerTest {

    public static class TestClassInt {
        public int x;
        public int y;
        public int z;

        public TestClassInt() {
            this(0, 0);
        }

        public TestClassInt(int x) {
            this(x, 0);
        }

        public TestClassInt(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            TestClassInt other = (TestClassInt) obj;
            return x == other.x && y == other.y && z == other.z;
        }

        @Override
        public String toString() {
            return "{" + x + "," + y + "," + z + "}";
        }

        @Override
        public int hashCode() {
            return x + 13 * y;
        }

        public static final long fieldOffset1;
        public static final long fieldOffset2;
        public static final boolean firstFieldIsX;

        static {
            try {
                long localFieldOffset1 = UNSAFE.objectFieldOffset(EATestBase.TestClassInt.class.getField("x"));
                // Make the fields 8 byte aligned (Required for testing setLong on Architectures
                // which does not support unaligned memory access. The code has to be extra careful
                // because some JDKs do a better job of packing fields.
                if (localFieldOffset1 % 8 == 0) {
                    fieldOffset1 = localFieldOffset1;
                    fieldOffset2 = UNSAFE.objectFieldOffset(EATestBase.TestClassInt.class.getField("y"));
                    firstFieldIsX = true;
                } else {
                    fieldOffset1 = UNSAFE.objectFieldOffset(EATestBase.TestClassInt.class.getField("y"));
                    fieldOffset2 = UNSAFE.objectFieldOffset(EATestBase.TestClassInt.class.getField("z"));
                    firstFieldIsX = false;
                }
                assert fieldOffset2 == fieldOffset1 + 4;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void setFirstField(int v) {
            if (firstFieldIsX) {
                x = v;
            } else {
                y = v;
            }
        }

        public void setSecondField(int v) {
            if (firstFieldIsX) {
                y = v;
            } else {
                z = v;
            }
        }
    }

    public static class TestClassObject {
        public Object x;
        public Object y;

        public TestClassObject() {
            this(null, null);
        }

        public TestClassObject(Object x) {
            this(x, null);
        }

        public TestClassObject(Object x, Object y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            TestClassObject other = (TestClassObject) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public String toString() {
            return "{" + x + "," + y + "}";
        }

        @Override
        public int hashCode() {
            return (x == null ? 0 : x.hashCode()) + 13 * (y == null ? 0 : y.hashCode());
        }
    }

    protected static native void notInlineable();

    protected StructuredGraph graph;
    protected HighTierContext context;
    protected List<ReturnNode> returnNodes;

    /**
     * Runs Escape Analysis on the given snippet and makes sure that no allocations remain in the
     * graph.
     *
     * @param snippet the name of the method whose graph should be processed
     * @param expectedConstantResult if this is non-null, the resulting graph needs to have the
     *            given constant return value
     * @param iterativeEscapeAnalysis true if escape analysis should be run for more than one
     *            iteration
     */
    protected void testEscapeAnalysis(String snippet, JavaConstant expectedConstantResult, boolean iterativeEscapeAnalysis) {
        testEscapeAnalysis(snippet, expectedConstantResult, iterativeEscapeAnalysis, 0);
    }

    protected void testEscapeAnalysis(String snippet, JavaConstant expectedConstantResult, boolean iterativeEscapeAnalysis, int expectedAllocationCount) {
        prepareGraph(snippet, iterativeEscapeAnalysis);
        if (expectedConstantResult != null) {
            for (ReturnNode returnNode : returnNodes) {
                Assert.assertTrue(returnNode.result().toString(), returnNode.result().isConstant());
                Assert.assertEquals(expectedConstantResult, returnNode.result().asConstant());
            }
        }
        int newInstanceCount = getAllocationCount();
        Assert.assertEquals("Expected allocation count does not match", expectedAllocationCount, newInstanceCount);
        if (expectedAllocationCount == 0) {
            Assert.assertTrue("Unexpected CommitAllocationNode", graph.getNodes().filter(CommitAllocationNode.class).isEmpty());
        }
    }

    protected int getAllocationCount() {
        return graph.getNodes().filter(isA(NewInstanceNode.class).or(NewArrayNode.class).or(AllocatedObjectNode.class)).count();
    }

    @SuppressWarnings("try")
    protected void prepareGraph(String snippet, boolean iterativeEscapeAnalysis) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope(getClass(), method, getCodeCache())) {
            graph = parseEager(method, AllowAssumptions.YES, debug);
            context = getDefaultHighTierContext();
            createInliningPhase().apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            canonicalizeGraph();
            new PartialEscapePhase(iterativeEscapeAnalysis, false, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
            postEACanonicalizeGraph();
            returnNodes = graph.getNodes(ReturnNode.TYPE).snapshot();
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void postEACanonicalizeGraph() {
    }

    protected void canonicalizeGraph() {
        this.createCanonicalizerPhase().apply(graph, context);
    }
}
