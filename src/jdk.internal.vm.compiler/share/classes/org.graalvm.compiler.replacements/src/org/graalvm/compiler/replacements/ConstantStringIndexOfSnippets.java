/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class ConstantStringIndexOfSnippets implements Snippets {
    public static class Templates extends AbstractTemplates {

        private final SnippetInfo indexOfConstant = snippet(ConstantStringIndexOfSnippets.class, "indexOfConstant");
        private final SnippetInfo latin1IndexOfConstant = snippet(ConstantStringIndexOfSnippets.class, "latin1IndexOfConstant");
        private final SnippetInfo utf16IndexOfConstant = snippet(ConstantStringIndexOfSnippets.class, "utf16IndexOfConstant");

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
            template(stringIndexOf, args).instantiate(providers.getMetaAccess(), stringIndexOf, DEFAULT_REPLACER, args);
        }

        public void lowerLatin1(SnippetLowerableMemoryNode latin1IndexOf, LoweringTool tool) {
            StructuredGraph graph = latin1IndexOf.graph();
            Arguments args = new Arguments(latin1IndexOfConstant, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("source", latin1IndexOf.getArgument(0));
            args.add("sourceCount", latin1IndexOf.getArgument(1));
            args.addConst("target", latin1IndexOf.getArgument(2));
            args.add("targetCount", latin1IndexOf.getArgument(3));
            args.add("origFromIndex", latin1IndexOf.getArgument(4));
            byte[] targetByteArray = snippetReflection.asObject(byte[].class, latin1IndexOf.getArgument(2).asJavaConstant());
            args.addConst("md2", md2(targetByteArray));
            args.addConst("cache", computeCache(targetByteArray));
            template(latin1IndexOf, args).instantiate(providers.getMetaAccess(), latin1IndexOf, DEFAULT_REPLACER, args);
        }

        public void lowerUTF16(SnippetLowerableMemoryNode utf16IndexOf, LoweringTool tool) {
            StructuredGraph graph = utf16IndexOf.graph();
            Arguments args = new Arguments(utf16IndexOfConstant, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("source", utf16IndexOf.getArgument(0));
            args.add("sourceCount", utf16IndexOf.getArgument(1));
            args.addConst("target", utf16IndexOf.getArgument(2));
            args.add("targetCount", utf16IndexOf.getArgument(3));
            args.add("origFromIndex", utf16IndexOf.getArgument(4));
            byte[] targetByteArray = snippetReflection.asObject(byte[].class, utf16IndexOf.getArgument(2).asJavaConstant());
            args.addConst("md2", md2Utf16(tool.getMetaAccess(), targetByteArray));
            args.addConst("cache", computeCacheUtf16(tool.getMetaAccess(), targetByteArray));
            template(utf16IndexOf, args).instantiate(providers.getMetaAccess(), utf16IndexOf, DEFAULT_REPLACER, args);
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

    static int md2(byte[] target) {
        int c = target.length;
        if (c == 0) {
            return 0;
        }
        byte lastByte = target[c - 1];
        int md2 = c;
        for (int i = 0; i < c - 1; i++) {
            if (target[i] == lastByte) {
                md2 = (c - 1) - i;
            }
        }
        return md2;
    }

    static long computeCache(byte[] s) {
        int c = s.length;
        int cache = 0;
        int i;
        for (i = 0; i < c - 1; i++) {
            cache |= (1 << (s[i] & 63));
        }
        return cache;
    }

    static int md2Utf16(MetaAccessProvider metaAccess, byte[] target) {
        int c = target.length / 2;
        if (c == 0) {
            return 0;
        }
        long base = metaAccess.getArrayBaseOffset(JavaKind.Byte);
        char lastChar = UnsafeAccess.UNSAFE.getChar(target, base + (c - 1) * 2);
        int md2 = c;
        for (int i = 0; i < c - 1; i++) {
            char currChar = UnsafeAccess.UNSAFE.getChar(target, base + i * 2);
            if (currChar == lastChar) {
                md2 = (c - 1) - i;
            }
        }
        return md2;
    }

    static long computeCacheUtf16(MetaAccessProvider metaAccess, byte[] s) {
        int c = s.length / 2;
        int cache = 0;
        int i;
        long base = metaAccess.getArrayBaseOffset(JavaKind.Byte);
        for (i = 0; i < c - 1; i++) {
            char currChar = UnsafeAccess.UNSAFE.getChar(s, base + i * 2);
            cache |= (1 << (currChar & 63));
        }
        return cache;
    }

    @Fold
    static int byteArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    static int charArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Char);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED = null;

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

        long base = charArrayBaseOffset(INJECTED);
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

    @Snippet
    public static int utf16IndexOfConstant(byte[] source, int sourceCount,
                    @ConstantParameter byte[] target, int targetCount,
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

        long base = byteArrayBaseOffset(INJECTED);
        int lastChar = UnsafeAccess.UNSAFE.getChar(target, base + targetCountLess1 * 2);

        outer_loop: for (long i = fromIndex; i < sourceEnd;) {
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
                    if (UnsafeAccess.UNSAFE.getChar(target, base + j * 2) != sourceChar) {
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
                return (int) i;
            }
            if ((cache & (1 << src)) == 0) {
                i += targetCountLess1;
            }
            i++;
        }
        return -1;
    }

    @Snippet
    public static int latin1IndexOfConstant(byte[] source, int sourceCount,
                    @ConstantParameter byte[] target, int targetCount,
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

        long base = byteArrayBaseOffset(INJECTED);
        int lastByte = UnsafeAccess.UNSAFE.getByte(target, base + targetCountLess1);

        outer_loop: for (long i = fromIndex; i < sourceEnd;) {
            int src = UnsafeAccess.UNSAFE.getByte(source, base + i + targetCountLess1);
            if (src == lastByte) {
                // With random strings and a 4-character alphabet,
                // reverse matching at this point sets up 0.8% fewer
                // frames, but (paradoxically) makes 0.3% more probes.
                // Since those probes are nearer the lastByte probe,
                // there is may be a net D$ win with reverse matching.
                // But, reversing loop inhibits unroll of inner loop
                // for unknown reason. So, does running outer loop from
                // (sourceOffset - targetCountLess1) to (sourceOffset + sourceCount)
                if (targetCount <= 8) {
                    ExplodeLoopNode.explodeLoop();
                }
                for (long j = 0; j < targetCountLess1; j++) {
                    byte sourceByte = UnsafeAccess.UNSAFE.getByte(source, base + i + j);
                    if (UnsafeAccess.UNSAFE.getByte(target, base + j) != sourceByte) {
                        if ((cache & (1 << sourceByte)) == 0) {
                            if (md2 < j + 1) {
                                i += j + 1;
                                continue outer_loop;
                            }
                        }
                        i += md2;
                        continue outer_loop;
                    }
                }
                return (int) i;
            }
            if ((cache & (1 << src)) == 0) {
                i += targetCountLess1;
            }
            i++;
        }
        return -1;
    }
}
