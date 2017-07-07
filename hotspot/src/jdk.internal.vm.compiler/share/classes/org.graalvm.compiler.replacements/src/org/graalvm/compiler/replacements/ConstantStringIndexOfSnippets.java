/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;

import jdk.vm.ci.code.TargetDescription;
import sun.misc.Unsafe;

public class ConstantStringIndexOfSnippets implements Snippets {
    public static class Templates extends AbstractTemplates {

        private final SnippetInfo indexOfConstant = snippet(ConstantStringIndexOfSnippets.class, "indexOfConstant");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);
        }

        public void lower(SnippetLowerableMemoryNode stringIndexOf, LoweringTool tool) {
            StructuredGraph graph = stringIndexOf.graph();
            Arguments args = new Arguments(indexOfConstant, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("source", stringIndexOf.getArgument(0));
            args.add("sourceOffset", stringIndexOf.getArgument(1));
            args.add("sourceCount", stringIndexOf.getArgument(2));
            args.addConst("target", stringIndexOf.getArgument(3));
            args.add("targetOffset", stringIndexOf.getArgument(4));
            args.add("targetCount", stringIndexOf.getArgument(5));
            args.add("origFromIndex", stringIndexOf.getArgument(6));
            char[] targetCharArray = snippetReflection.asObject(char[].class, stringIndexOf.getArgument(3).asJavaConstant());
            args.addConst("md2", md2(targetCharArray));
            args.addConst("cache", computeCache(targetCharArray));
            template(graph.getDebug(), args).instantiate(providers.getMetaAccess(), stringIndexOf, DEFAULT_REPLACER, args);
        }
    }

    static int md2(char[] target) {
        int c = target.length;
        if (c == 0) {
            return 0;
        }
        char lastChar = target[c - 1];
        int md2 = c;
        for (int i = 0; i < c - 1; i++) {
            if (target[i] == lastChar) {
                md2 = (c - 1) - i;
            }
        }
        return md2;
    }

    static long computeCache(char[] s) {
        int c = s.length;
        int cache = 0;
        int i;
        for (i = 0; i < c - 1; i++) {
            cache |= (1 << (s[i] & 63));
        }
        return cache;
    }

    @Snippet
    public static int indexOfConstant(char[] source, int sourceOffset, int sourceCount,
                    @ConstantParameter char[] target, int targetOffset, int targetCount,
                    int origFromIndex, @ConstantParameter int md2, @ConstantParameter long cache) {
        int fromIndex = origFromIndex;
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        int targetCountLess1 = targetCount - 1;
        int sourceEnd = sourceCount - targetCountLess1;

        long base = Unsafe.ARRAY_CHAR_BASE_OFFSET;
        int lastChar = UnsafeAccess.UNSAFE.getChar(target, base + targetCountLess1 * 2);

        outer_loop: for (long i = sourceOffset + fromIndex; i < sourceEnd;) {
            int src = UnsafeAccess.UNSAFE.getChar(source, base + (i + targetCountLess1) * 2);
            if (src == lastChar) {
                // With random strings and a 4-character alphabet,
                // reverse matching at this point sets up 0.8% fewer
                // frames, but (paradoxically) makes 0.3% more probes.
                // Since those probes are nearer the lastChar probe,
                // there is may be a net D$ win with reverse matching.
                // But, reversing loop inhibits unroll of inner loop
                // for unknown reason. So, does running outer loop from
                // (sourceOffset - targetCountLess1) to (sourceOffset + sourceCount)
                if (targetCount <= 8) {
                    ExplodeLoopNode.explodeLoop();
                }
                for (long j = 0; j < targetCountLess1; j++) {
                    char sourceChar = UnsafeAccess.UNSAFE.getChar(source, base + (i + j) * 2);
                    if (UnsafeAccess.UNSAFE.getChar(target, base + (targetOffset + j) * 2) != sourceChar) {
                        if ((cache & (1 << sourceChar)) == 0) {
                            if (md2 < j + 1) {
                                i += j + 1;
                                continue outer_loop;
                            }
                        }
                        i += md2;
                        continue outer_loop;
                    }
                }
                return (int) (i - sourceOffset);
            }
            if ((cache & (1 << src)) == 0) {
                i += targetCountLess1;
            }
            i++;
        }
        return -1;
    }
}
