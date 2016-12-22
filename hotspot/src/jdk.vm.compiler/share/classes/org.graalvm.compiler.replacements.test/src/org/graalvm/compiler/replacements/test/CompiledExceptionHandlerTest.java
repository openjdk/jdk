/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.tiers.Suites;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests compilation of a hot exception handler.
 */
public class CompiledExceptionHandlerTest extends GraalCompilerTest {

    @Override
    @SuppressWarnings("try")
    protected Suites createSuites() {
        try (OverrideScope scope = OptionValue.override(HighTier.Options.Inline, false)) {
            return super.createSuites();
        }
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        /*
         * We don't care whether other invokes are inlined or not, but we definitely don't want
         * another explicit exception handler in the graph.
         */
        return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
    }

    @Override
    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        StructuredGraph graph = super.parseEager(m, allowAssumptions, compilationId);
        int handlers = graph.getNodes().filter(ExceptionObjectNode.class).count();
        Assert.assertEquals(1, handlers);
        return graph;
    }

    @BytecodeParserNeverInline(invokeWithException = true)
    private static void raiseExceptionSimple(String s) {
        throw new RuntimeException("Raising exception with message \"" + s + "\"");
    }

    @Test
    public void test1() {
        test("test1Snippet", "a string");
        test("test1Snippet", (String) null);
    }

    public static String test1Snippet(String message) {
        if (message != null) {
            try {
                raiseExceptionSimple(message);
            } catch (Exception e) {
                return message + e.getMessage();
            }
        }
        return null;
    }

    @BytecodeParserNeverInline(invokeWithException = true)
    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test2() {
        test("test2Snippet", "m1", "m2", "m3", "m4", "m5");
        test("test2Snippet", null, "m2", "m3", "m4", "m5");
    }

    public static String test2Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            try {
                raiseException(m1, m2, m3, m4, m5);
            } catch (Exception e) {
                return m5 + m4 + m3 + m2 + m1;
            }
        }
        return m4 + m3;
    }
}
