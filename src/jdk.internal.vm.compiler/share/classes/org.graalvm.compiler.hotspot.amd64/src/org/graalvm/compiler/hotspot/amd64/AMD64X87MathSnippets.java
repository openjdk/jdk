/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.code.TargetDescription;

public class AMD64X87MathSnippets implements Snippets {

    private static final double PI_4 = Math.PI / 4;

    @Snippet
    public static double sin(double input) {
        if (Math.abs(input) < PI_4) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.SIN);
        }
        return callDouble1(UnaryOperation.SIN.foreignCallDescriptor, input);
    }

    @Snippet
    public static double cos(double input) {
        if (Math.abs(input) < PI_4) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.COS);
        }
        return callDouble1(UnaryOperation.COS.foreignCallDescriptor, input);
    }

    @Snippet
    public static double tan(double input) {
        if (Math.abs(input) < PI_4) {
            return AMD64X87MathIntrinsicNode.compute(input, UnaryOperation.TAN);
        }
        return callDouble1(UnaryOperation.TAN.foreignCallDescriptor, input);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native double callDouble1(@ConstantNodeParameter ForeignCallDescriptor descriptor, double value);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo sin;
        private final SnippetInfo cos;
        private final SnippetInfo tan;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);

            sin = snippet(AMD64X87MathSnippets.class, "sin");
            cos = snippet(AMD64X87MathSnippets.class, "cos");
            tan = snippet(AMD64X87MathSnippets.class, "tan");
        }

        public void lower(UnaryMathIntrinsicNode mathIntrinsicNode, LoweringTool tool) {
            SnippetInfo info;

            switch (mathIntrinsicNode.getOperation()) {
                case SIN:
                    info = sin;
                    break;
                case COS:
                    info = cos;
                    break;
                case TAN:
                    info = tan;
                    break;
                default:
                    throw GraalError.shouldNotReachHere("Snippet not found for math intrinsic " + mathIntrinsicNode.getOperation().name());
            }

            Arguments args = new Arguments(info, mathIntrinsicNode.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("input", mathIntrinsicNode.getValue());
            template(mathIntrinsicNode, args).instantiate(providers.getMetaAccess(), mathIntrinsicNode, DEFAULT_REPLACER, tool, args);
            mathIntrinsicNode.safeDelete();
        }
    }
}
