/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import static org.junit.Assert.assertNotEquals;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.nodes.KlassBeingInitializedCheckNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotClassInitializationTest extends HotSpotGraalCompilerTest {

    static HotSpotClassInitializationTest instance;

    static class InvokeStatic {
        static {
            instance.test(InvokeStatic.class, "m");
        }

        static boolean m() {
            double value = 123;
            doOtherWork(value);
            return GraalDirectives.inCompiledCode();
        }

        static double doOtherWork(double value) {
            return value;
        }

        static int field;
    }

    static class GetStatic {
        static class Inner {
            static int N = 5000;

            static {
                instance.test(GetStatic.class, "m", LoadFieldNode.class);
            }
        }

        static {
            @SuppressWarnings("unused")
            int n = Inner.N;
        }

        @SuppressWarnings("unused")
        static boolean m() {
            double value = 123 * Inner.N;
            return GraalDirectives.inCompiledCode();
        }

        static double field;
    }

    static class NewInstance {
        static {
            instance.test(NewInstance.class, "m", NewInstanceNode.class);
        }

        @SuppressWarnings("unused")
        static boolean m() {
            Object o = new NewInstance();
            return GraalDirectives.inCompiledCode();
        }

        static double field;
    }

    @Before
    public void checkAssumptions() {
        // cannot be BeforeClass because we need a runtime and BeforeClass must be static
        Assume.assumeTrue("init_thread field must be visible", runtime().getVMConfig().instanceKlassInitThreadOffset != -1);
    }

    @SafeVarargs
    final void test(Class<?> testClass, String methodName, Class<? extends Node>... nodeTypes) {
        ResolvedJavaMethod method = getResolvedJavaMethod(testClass, methodName);
        StructuredGraph graph = parseProfiled(method, StructuredGraph.AllowAssumptions.NO);
        for (DeoptimizeNode d : graph.getNodes().filter(DeoptimizeNode.class)) {
            assertNotEquals("No unresolved deopts expected", d.getReason(), DeoptimizationReason.Unresolved);
        }
        assertTrue("A dynamic check should have been emitted", graph.getNodes().filter(KlassBeingInitializedCheckNode.class).count() == 1);

        // Ensure that all expected nodes exist.
        for (Class<? extends Node> nodeType : nodeTypes) {
            assertTrue("expected node of type " + nodeType, graph.getNodes().filter(nodeType).count() == 1);
        }

        // Ensure that execution remains in the compiled code to the return point
        InstalledCode code = instance.getCode(method, graph);
        try {
            boolean result = (boolean) code.executeVarargs();
            Assert.assertEquals("should have completed in compiled code", result, true);
        } catch (InvalidInstalledCodeException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInvokeStatic() {
        GraalDirectives.inCompiledCode();
        instance = this;
        InvokeStatic.field = 0;
        instance = null;
    }

    @Test
    public void testGetStatic() {
        GraalDirectives.inCompiledCode();
        instance = this;
        GetStatic.field = 0;
        instance = null;
    }

    @Test
    public void testNewInstance() {
        GraalDirectives.inCompiledCode();
        instance = this;
        NewInstance.field = 0;
        instance = null;
    }

}
