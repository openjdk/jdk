/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.java.BytecodeParserOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that the defaults for {@link GraalOptions#TrivialInliningSize} and
 * {@link BytecodeParserOptions#InlineDuringParsingMaxDepth} prevent explosive graph growth for code
 * with small recursive methods.
 */
public class TrivialInliningExplosionTest extends GraalCompilerTest {

    public static void trivial() {
        trivial();
        trivial();
        trivial();
    }

    public static void main() {
        trivial();
        trivial();
        trivial();
        trivial();
        trivial();
        trivial();
        trivial();
        trivial();
        trivial();
    }

    private int afterParseSize;

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        final StructuredGraph graph = super.parseForCompile(method, compilationId, options);
        this.afterParseSize = graph.getNodeCount();
        return graph;
    }

    @Test
    public void test() {
        ResolvedJavaMethod methodm0 = getResolvedJavaMethod("trivial");
        Assert.assertTrue(methodm0.getCodeSize() <= GraalOptions.TrivialInliningSize.getValue(getInitialOptions()));
        test("main");
        int afterCompileSize = lastCompiledGraph.getNodeCount();

        // The values of afterParseSize and afterCompileSize when this
        // test was written were 3223 and 3505 respectively.
        Assert.assertTrue(afterParseSize < 4000);
        Assert.assertTrue(afterCompileSize < 4000);

    }
}
