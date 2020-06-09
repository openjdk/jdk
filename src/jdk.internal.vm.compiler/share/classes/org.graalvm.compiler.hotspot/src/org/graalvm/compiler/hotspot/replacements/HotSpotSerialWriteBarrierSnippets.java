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

import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.nodes.gc.SerialWriteBarrier;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.gc.SerialWriteBarrierSnippets;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.TargetDescription;

public class HotSpotSerialWriteBarrierSnippets extends SerialWriteBarrierSnippets {
    private final GraalHotSpotVMConfig config;

    public HotSpotSerialWriteBarrierSnippets(GraalHotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    public Word cardTableAddress() {
        return WordFactory.unsigned(GraalHotSpotVMConfigNode.cardTableAddress());
    }

    @Override
    public int cardTableShift() {
        return HotSpotReplacementsUtil.cardTableShift(INJECTED_VMCONFIG);
    }

    @Override
    public boolean verifyBarrier() {
        return ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED || config.verifyBeforeGC || config.verifyAfterGC;
    }

    @Override
    protected byte dirtyCardValue() {
        return config.dirtyCardValue;
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo serialImpreciseWriteBarrier;
        private final SnippetInfo serialPreciseWriteBarrier;
        private final SnippetInfo serialArrayRangeWriteBarrier;

        private final SerialWriteBarrierLowerer lowerer;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Group.Factory factory, HotSpotProviders providers, TargetDescription target, GraalHotSpotVMConfig config) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            this.lowerer = new SerialWriteBarrierLowerer(factory);

            HotSpotSerialWriteBarrierSnippets receiver = new HotSpotSerialWriteBarrierSnippets(config);
            serialImpreciseWriteBarrier = snippet(SerialWriteBarrierSnippets.class, "serialImpreciseWriteBarrier", null, receiver, GC_CARD_LOCATION);
            serialPreciseWriteBarrier = snippet(SerialWriteBarrierSnippets.class, "serialPreciseWriteBarrier", null, receiver, GC_CARD_LOCATION);
            serialArrayRangeWriteBarrier = snippet(SerialWriteBarrierSnippets.class, "serialArrayRangeWriteBarrier", null, receiver, GC_CARD_LOCATION);
        }

        public void lower(SerialWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, serialPreciseWriteBarrier, serialImpreciseWriteBarrier, barrier, tool);
        }

        public void lower(SerialArrayRangeWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, serialArrayRangeWriteBarrier, barrier, tool);
        }
    }
}
