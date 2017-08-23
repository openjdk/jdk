/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This test illustrates problems and limitations with class hierarchy analysis when default methods
 * are involved.
 */
public class FindUniqueDefaultMethodTest extends GraalCompilerTest {

    interface Interface1 {
        default int v1() {
            return 1;
        }
    }

    static class Implementor1 implements Interface1 {
        int callV1() {
            return v1();
        }
    }

    static class Subclass1 extends Implementor1 {

    }

    /**
     * HotSpot has an internal mismatch with CHA and default methods. The initial query says that
     * it's a unique method but the verification code that ensures that a dependence of this kind
     * would pass will fail an assert in debug mode.
     */
    @Test
    public void testFindUnique() {
        ResolvedJavaType cType = getMetaAccess().lookupJavaType(Implementor1.class);
        cType.initialize();
        ResolvedJavaMethod v1Method = getMetaAccess().lookupJavaMethod(this.getMethod(Interface1.class, "v1"));
        AssumptionResult<ResolvedJavaMethod> method = cType.findUniqueConcreteMethod(v1Method);
        assertDeepEquals(null, method);
    }

    interface Interface2 {
        default int v1() {
            return 1;
        }
    }

    static class Base2 {
        public int v2() {
            return 1;
        }
    }

    static class Implementor2 extends Base2 implements Interface2 {
        int callV1() {
            return v1();
        }

        int callV2() {
            return v2();
        }
    }

    static class Subclass2 extends Implementor2 {

    }

    /**
     * This test illustrates a common pattern where a method at the root of a hierarchy is the only
     * implementation and can be statically inlined.
     */
    @SuppressWarnings("unused")
    @Test
    public void testInherited() {
        Subclass2 s = new Subclass2();
        testConstantReturn("runInherited", 1);
    }

    /**
     * Test same pattern as above but using default methods instead. HotSpot doesn't allow this
     * version to be optimized.
     */
    @SuppressWarnings("unused")
    @Test
    @Ignore("HotSpot CHA doesn't treat default methods like regular methods")
    public void testDefault() {
        Subclass2 s = new Subclass2();
        testConstantReturn("runDefault", 1);
    }

    public int runDefault(Implementor2 i) {
        return i.callV1();
    }

    public int runInherited(Implementor2 i) {
        return i.callV2();
    }

    private void testConstantReturn(String name, Object value) {
        StructuredGraph result = buildGraph(name);
        ReturnNode ret = result.getNodes(ReturnNode.TYPE).first();
        assertDeepEquals(1, result.getNodes(ReturnNode.TYPE).count());

        assertDeepEquals(true, ret.result().isConstant());
        assertDeepEquals(value, ret.result().asJavaConstant().asBoxedPrimitive());
    }

    @SuppressWarnings("try")
    protected StructuredGraph buildGraph(final String snippet) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("InstanceOfTest", getMetaAccess().lookupJavaMethod(getMethod(snippet)))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, debug);
            compile(graph.method(), graph);
            debug.dump(DebugContext.BASIC_LEVEL, graph, snippet);
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
