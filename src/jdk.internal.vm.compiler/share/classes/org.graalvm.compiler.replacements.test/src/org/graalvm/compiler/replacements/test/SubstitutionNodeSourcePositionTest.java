/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.core.common.GraalOptions.TrackNodeSourcePosition;

import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.SourceMapping;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Test that various substitution implementations produce the expected
 * {@link org.graalvm.compiler.graph.NodeSourcePosition} structure. Method substitutions and method
 * plugins should leave behind a frame for the substitution. Method substitutions should have
 * bytecodes below the substitution. Snippet lowerings should just have the bytecodes without a
 * marker frame.
 */
public class SubstitutionNodeSourcePositionTest extends ReplacementsTest {

    private static class TestMethod {

        public static int test(int i) {
            return i;
        }
    }

    @ClassSubstitution(TestMethod.class)
    public static class TestMethodSubstitution {

        @MethodSubstitution
        public static int test(int i) {
            blackhole(i);
            return i;
        }

        @Node.NodeIntrinsic(BlackholeNode.class)
        private static native void blackhole(int i);
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        new PluginFactory_SubstitutionNodeSourcePositionTest().registerPlugins(invocationPlugins, null);
        ClassfileBytecodeProvider bytecodeProvider = getSystemClassLoaderBytecodeProvider();
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, TestMethod.class, bytecodeProvider);
        r.registerMethodSubstitution(TestMethodSubstitution.class, "test", int.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public int methodSubstitution() {
        return TestMethod.test(42);
    }

    @Test
    public void testMethodSubstitution() {
        // @formatter:off
        // Expect mappings of the form:
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest$TestMethodSubstitution.blackhole(int) [bci: -1]
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest$TestMethodSubstitution.test(SubstitutionNodeSourcePositionTest.java:71) [bci: 1] Substitution
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest$TestMethod.test(int) [bci: -1]
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest.methodSubstitution(SubstitutionNodeSourcePositionTest.java:89) [bci: 2]
        // @formatter:on
        checkMappings("methodSubstitution", true, TestMethod.class, "test");
    }

    public void snippetLowering(String[] array, String value) {
        array[0] = value;
    }

    @Test
    public void testSnippetLowering() {
        // @formatter:off
        // Expect mappings of the form:
        //   at org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.serialWriteBarrier(WriteBarrierSnippets.java:140) [bci: 18]
        //   at org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.serialPreciseWriteBarrier(WriteBarrierSnippets.java:158) [bci: 5] Substitution
        //   at org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.serialPreciseWriteBarrier(AddressNode$Address, WriteBarrierSnippets$Counters) [bci: -1] Substitution
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest.snippetLowering(SubstitutionNodeSourcePositionTest.java:99) [bci: 3]
        // @formatter:on
        //
        // The precise snippet bytecodes don't matter, just ensure that some actually appear after
        // lowering.
        checkMappings("snippetLowering", true, SubstitutionNodeSourcePositionTest.class, "snippetLowering");
    }

    public int methodPlugin(int i) {
        GraalDirectives.blackhole(i);
        return i;
    }

    @Test
    public void testMethodPlugin() {
        // @formatter:off
        // Expect mappings of the form:
        //   at org.graalvm.compiler.api.directives.GraalDirectives.blackhole(int) [bci: -1]
        //   at org.graalvm.compiler.replacements.test.SubstitutionNodeSourcePositionTest.methodPlugin(SubstitutionNodeSourcePositionTest.java:109) [bci: 1]
        // @formatter:on
        checkMappings("methodPlugin", false, GraalDirectives.class, "blackhole");
    }

    private void checkMappings(String snippetMethod, boolean hasBytecodes, Class<?> boundaryClass, String boundaryMethod) {
        List<SourceMapping> mappings = getSourceMappings(snippetMethod);
        ResolvedJavaType resolvedJavaType = getMetaAccess().lookupJavaType(boundaryClass);
        boolean found = false;
        Assert.assertTrue("must have mappings", !mappings.isEmpty());
        for (SourceMapping mapping : mappings) {
            NodeSourcePosition callee = null;
            for (NodeSourcePosition pos = mapping.getSourcePosition(); pos != null; pos = pos.getCaller()) {
                ResolvedJavaMethod method = pos.getMethod();
                if (method.getName().equals(boundaryMethod) && method.getDeclaringClass().equals(resolvedJavaType)) {
                    if ((callee != null) == hasBytecodes) {
                        if (hasBytecodes) {
                            assertTrue(callee.isSubstitution());
                        }
                        assertTrue(pos.trim() == pos);
                        assertTrue(mapping.getSourcePosition().trim() == pos);
                        found = true;
                    }
                }
                callee = pos;
            }
        }
        Assert.assertTrue("must have substitution for " + resolvedJavaType + "." + boundaryMethod, found);
    }

    private List<SourceMapping> getSourceMappings(String name) {
        final ResolvedJavaMethod method = getResolvedJavaMethod(name);
        final OptionValues options = new OptionValues(getInitialOptions(), TrackNodeSourcePosition, true);
        final StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES, options);
        final CompilationResult cr = compileGraph(graph, graph.method(), getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, graph.getProfilingInfo(),
                        createSuites(graph.getOptions()), createLIRSuites(graph.getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default, true);
        return cr.getSourceMappings();
    }
}
