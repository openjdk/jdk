/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.doingUnsafeAccessOffset;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.TargetDescription;

public class UnsafeSnippets implements Snippets {

    public static final String copyMemoryName = JavaVersionUtil.JAVA_SPEC <= 8 ? "copyMemory" : "copyMemory0";

    @SuppressWarnings("unused")
    @Snippet
    static void copyMemory(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = WordFactory.signed(bytes);

        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
    }

    @SuppressWarnings("unused")
    @Snippet
    static void copyMemoryGuarded(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = WordFactory.signed(bytes);
        Word javaThread = CurrentJavaThreadNode.get();
        int offset = doingUnsafeAccessOffset(INJECTED_VMCONFIG);
        LocationIdentity any = LocationIdentity.any();

        /* Set doingUnsafeAccess to guard and handle unsafe memory access failures */
        javaThread.writeByte(offset, (byte) 1, any);
        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
        /* Reset doingUnsafeAccess */
        javaThread.writeByte(offset, (byte) 0, any);
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo copyMemory = snippet(UnsafeSnippets.class, "copyMemory");
        private final SnippetInfo copyMemoryGuarded = snippet(UnsafeSnippets.class, "copyMemoryGuarded");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(UnsafeCopyMemoryNode copyMemoryNode, LoweringTool tool) {
            StructuredGraph graph = copyMemoryNode.graph();
            Arguments args = new Arguments(copyMemoryNode.isGuarded() ? copyMemoryGuarded : copyMemory, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("receiver", copyMemoryNode.receiver);
            args.add("srcBase", copyMemoryNode.srcBase);
            args.add("srcOffset", copyMemoryNode.srcOffset);
            args.add("destBase", copyMemoryNode.destBase);
            args.add("destOffset", copyMemoryNode.desOffset);
            args.add("bytes", copyMemoryNode.bytes);
            SnippetTemplate template = template(copyMemoryNode, args);
            template.instantiate(providers.getMetaAccess(), copyMemoryNode, DEFAULT_REPLACER, args);
        }
    }
}
