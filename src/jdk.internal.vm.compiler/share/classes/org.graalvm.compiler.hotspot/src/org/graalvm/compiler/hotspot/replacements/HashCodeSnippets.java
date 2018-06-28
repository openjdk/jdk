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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.IDENTITY_HASHCODE;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.biasedLockMaskInPlace;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCode;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.identityHashCodeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadWordFromObject;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.markOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.uninitializedIdentityHashCodeValue;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.unlockedMask;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.TargetDescription;

public class HashCodeSnippets implements Snippets {

    @Snippet
    public static int identityHashCodeSnippet(final Object thisObj) {
        if (probability(NOT_FREQUENT_PROBABILITY, thisObj == null)) {
            return 0;
        }
        return computeHashCode(thisObj);
    }

    static int computeHashCode(final Object x) {
        Word mark = loadWordFromObject(x, markOffset(INJECTED_VMCONFIG));

        // this code is independent from biased locking (although it does not look that way)
        final Word biasedLock = mark.and(biasedLockMaskInPlace(INJECTED_VMCONFIG));
        if (probability(FAST_PATH_PROBABILITY, biasedLock.equal(WordFactory.unsigned(unlockedMask(INJECTED_VMCONFIG))))) {
            int hash = (int) mark.unsignedShiftRight(identityHashCodeShift(INJECTED_VMCONFIG)).rawValue();
            if (probability(FAST_PATH_PROBABILITY, hash != uninitializedIdentityHashCodeValue(INJECTED_VMCONFIG))) {
                return hash;
            }
        }
        return identityHashCode(IDENTITY_HASHCODE, x);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo identityHashCodeSnippet = snippet(HashCodeSnippets.class, "identityHashCodeSnippet", HotSpotReplacementsUtil.MARK_WORD_LOCATION);

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(IdentityHashCodeNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(identityHashCodeSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.object);
            SnippetTemplate template = template(node, args);
            template.instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

}
