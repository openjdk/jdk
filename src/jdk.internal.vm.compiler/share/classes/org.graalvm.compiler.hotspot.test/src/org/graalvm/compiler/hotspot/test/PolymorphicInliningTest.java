/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParserOptions;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

public class PolymorphicInliningTest extends GraalCompilerTest {

    @Before
    public void initializeNotInlinableMethod() {
        ((HotSpotResolvedJavaMethod) getResolvedJavaMethod(NotInlinableSubClass.class, "foo")).setNotInlinableOrCompilable();
        // Resolve classes
        new A().foo();
        new B().foo();
        new NotInlinableSubClass().foo();
    }

    public int snippet(SuperClass receiver) {
        return receiver.foo();
    }

    @Test
    public void testBimorphicInlined() {
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
        StructuredGraph graph = parseForCompile(method, disableInlineDuringParsing());

        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(A.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(B.class), 0.5D)};
        injectTypeProfile(graph, "PolymorphicInliningTest$SuperClass.foo", new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));

        createInliningPhase().apply(graph, getDefaultHighTierContext());

        // This callsite should be inlined with a TypeCheckedInliningViolated deoptimization.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 0);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 1);
        assertTrue(getNodeCount(graph, AbstractDeoptimizeNode.class) == 1);
    }

    @Test
    public void testBimorphicNotInlined() {
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
        StructuredGraph graph = parseForCompile(method, disableInlineDuringParsing());

        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(A.class), 0.8D),
                        new ProfiledType(metaAccess.lookupJavaType(NotInlinableSubClass.class), 0.2D)};
        injectTypeProfile(graph, "PolymorphicInliningTest$SuperClass.foo", new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));

        createInliningPhase().apply(graph, getDefaultHighTierContext());

        // This callsite is not inlined due to one of the potential callee method is not inlinable.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 0);
        assertTrue(getNodeCount(graph, AbstractDeoptimizeNode.class) == 0);
    }

    @Test
    public void testMegamorphicInlined() {
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
        StructuredGraph graph = parseForCompile(method, disableInlineDuringParsing());

        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(A.class), 0.79D),
                        new ProfiledType(metaAccess.lookupJavaType(NotInlinableSubClass.class), 0.2D)};
        injectTypeProfile(graph, "PolymorphicInliningTest$SuperClass.foo", new JavaTypeProfile(TriState.FALSE, 0.01D, injectedProfile));

        createInliningPhase().apply(graph, getDefaultHighTierContext());

        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 1);
        assertTrue(getNodeCount(graph, AbstractDeoptimizeNode.class) == 0);
    }

    @Test
    public void testMegamorphicNotInlined() {
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
        StructuredGraph graph = parseForCompile(method, disableInlineDuringParsing());

        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(A.class), 0.3D),
                        new ProfiledType(metaAccess.lookupJavaType(B.class), 0.3D),
                        new ProfiledType(metaAccess.lookupJavaType(NotInlinableSubClass.class), 0.3D)};
        injectTypeProfile(graph, "PolymorphicInliningTest$SuperClass.foo", new JavaTypeProfile(TriState.FALSE, 0.1D, injectedProfile));

        createInliningPhase().apply(graph, getDefaultHighTierContext());

        // This callsite should not be inlined due to non of the potential callee method exceeds the
        // probability specified by GraalOptions.MegamorphicInliningMinMethodProbability.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 0);
        assertTrue(getNodeCount(graph, AbstractDeoptimizeNode.class) == 0);
    }

    private static OptionValues disableInlineDuringParsing() {
        return new OptionValues(getInitialOptions(), BytecodeParserOptions.InlineDuringParsing, false, BytecodeParserOptions.InlineIntrinsicsDuringParsing, false);
    }

    private static void injectTypeProfile(StructuredGraph graph, String targetMethod, JavaTypeProfile profile) {
        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (targetMethod.equals(callTargetNode.targetName())) {
                callTargetNode.setJavaTypeProfile(profile);
            }
        }
    }

    private static int getNodeCount(StructuredGraph graph, Class<? extends Node> nodeClass) {
        return graph.getNodes().filter(nodeClass).count();
    }

    private abstract static class SuperClass {
        abstract int foo();
    }

    private static class A extends SuperClass {
        @Override
        public int foo() {
            return 'A';
        }
    }

    private static class B extends SuperClass {
        @Override
        public int foo() {
            return 'B';
        }
    }

    private static class NotInlinableSubClass extends SuperClass {
        @Override
        public int foo() {
            return 'X';
        }
    }

}
