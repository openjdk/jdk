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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;

/**
 * Snippets used for conversion operations on AMD64 where the AMD64 instruction used does not match
 * the semantics of the JVM specification.
 */
public class AMD64ConvertSnippets implements Snippets {

    /**
     * Converts a float to an int.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSS2SI instruction used to do the
     * conversion. If the float value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Integer#MAX_VALUE} then CVTTSS2SI returns {@link Integer#MIN_VALUE} and
     * extra tests are required on the float value to return the correct int value.
     *
     * @param input the float being converted
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static int f2i(float input, int result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (Float.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0f) {
                // input is > 0 -> return max int
                return Integer.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a float to a long.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSS2SI instruction used to do the
     * conversion. If the float value is a NaN or infinity then CVTTSS2SI returns
     * {@link Long#MIN_VALUE} and extra tests are required on the float value to return the correct
     * long value.
     *
     * @param input the float being converted
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static long f2l(float input, long result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (Float.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0f) {
                // input is > 0 -> return max int
                return Long.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a double to an int.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSD2SI instruction used to do the
     * conversion. If the double value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Integer#MAX_VALUE} then CVTTSD2SI returns {@link Integer#MIN_VALUE} and
     * extra tests are required on the double value to return the correct int value.
     *
     * @param input the double being converted
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static int d2i(double input, int result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Integer.MIN_VALUE)) {
            if (Double.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0d) {
                // input is positive -> return maxInt
                return Integer.MAX_VALUE;
            }
        }
        return result;
    }

    /**
     * Converts a double to a long.
     * <p>
     * This snippet accounts for the semantics of the x64 CVTTSD2SI instruction used to do the
     * conversion. If the double value is a NaN, infinity or if the result of the conversion is
     * larger than {@link Long#MAX_VALUE} then CVTTSD2SI returns {@link Long#MIN_VALUE} and extra
     * tests are required on the double value to return the correct long value.
     *
     * @param input the double being converted
     * @param result the result produced by the CVTTSS2SI instruction
     */
    @Snippet
    public static long d2l(double input, long result) {
        if (probability(SLOW_PATH_PROBABILITY, result == Long.MIN_VALUE)) {
            if (Double.isNaN(input)) {
                // input is NaN -> return 0
                return 0;
            } else if (input > 0.0d) {
                // input is positive -> return maxInt
                return Long.MAX_VALUE;
            }
        }
        return result;
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo f2i;
        private final SnippetInfo f2l;
        private final SnippetInfo d2i;
        private final SnippetInfo d2l;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);

            f2i = snippet(AMD64ConvertSnippets.class, "f2i");
            f2l = snippet(AMD64ConvertSnippets.class, "f2l");
            d2i = snippet(AMD64ConvertSnippets.class, "d2i");
            d2l = snippet(AMD64ConvertSnippets.class, "d2l");
        }

        public void lower(FloatConvertNode convert, LoweringTool tool) {
            SnippetInfo key;
            switch (convert.getFloatConvert()) {
                case F2I:
                    key = f2i;
                    break;
                case F2L:
                    key = f2l;
                    break;
                case D2I:
                    key = d2i;
                    break;
                case D2L:
                    key = d2l;
                    break;
                default:
                    return;
            }

            StructuredGraph graph = convert.graph();

            Arguments args = new Arguments(key, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("input", convert.getValue());
            args.add("result", graph.unique(new AMD64FloatConvertNode(convert.getFloatConvert(), convert.getValue())));

            SnippetTemplate template = template(convert, args);
            convert.getDebug().log("Lowering %s in %s: node=%s, template=%s, arguments=%s", convert.getFloatConvert(), graph, convert, template, args);
            template.instantiate(providers.getMetaAccess(), convert, DEFAULT_REPLACER, tool, args);
            convert.safeDelete();
        }
    }
}
