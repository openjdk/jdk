/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.core.common.GraalOptions.TrivialInliningSize;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsingMaxDepth;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import org.graalvm.compiler.java.BytecodeParserOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InlineDuringParsingPlugin implements InlineInvokePlugin {

    /**
     * Budget which when exceeded reduces the effective value of
     * {@link BytecodeParserOptions#InlineDuringParsingMaxDepth} to
     * {@link #MaxDepthAfterBudgetExceeded}.
     */
    private static final int NodeBudget = Integer.getInteger("InlineDuringParsingPlugin.NodeBudget", 2000);

    private static final int MaxDepthAfterBudgetExceeded = Integer.getInteger("InlineDuringParsingPlugin.MaxDepthAfterBudgetExceeded", 3);

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        // @formatter:off
        if (method.hasBytecodes() &&
            method.getDeclaringClass().isLinked() &&
            method.canBeInlined()) {

            // Test force inlining first
            if (method.shouldBeInlined() && checkInliningDepth(b)) {
                return createStandardInlineInfo(method);
            }

            if (!method.isSynchronized() &&
                checkSize(method, args, b.getGraph()) &&
                checkInliningDepth(b)) {
                return createStandardInlineInfo(method);
            }
        }
        // @formatter:on
        return null;
    }

    private static boolean checkInliningDepth(GraphBuilderContext b) {
        int nodeCount = b.getGraph().getNodeCount();
        int maxDepth = InlineDuringParsingMaxDepth.getValue(b.getOptions());
        if (nodeCount > NodeBudget && MaxDepthAfterBudgetExceeded < maxDepth) {
            maxDepth = MaxDepthAfterBudgetExceeded;
        }
        return b.getDepth() < maxDepth;
    }

    private static boolean checkSize(ResolvedJavaMethod method, ValueNode[] args, StructuredGraph graph) {
        int bonus = 1;
        for (ValueNode v : args) {
            if (v.isConstant()) {
                bonus++;
            }
        }
        return method.getCode().length <= TrivialInliningSize.getValue(graph.getOptions()) * bonus;
    }
}
