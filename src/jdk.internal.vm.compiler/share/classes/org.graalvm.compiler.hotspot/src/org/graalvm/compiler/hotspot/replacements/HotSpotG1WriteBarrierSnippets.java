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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotCompressionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetCounter.Group.Factory;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.gc.G1WriteBarrierSnippets;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotG1WriteBarrierSnippets extends G1WriteBarrierSnippets {
    public static final HotSpotForeignCallDescriptor G1WBPRECALL = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "write_barrier_pre", void.class, Object.class);
    public static final HotSpotForeignCallDescriptor G1WBPOSTCALL = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "write_barrier_post", void.class, Word.class);
    public static final HotSpotForeignCallDescriptor VALIDATE_OBJECT = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "validate_object", boolean.class, Word.class,
                    Word.class);

    private final GraalHotSpotVMConfig config;
    private final Register threadRegister;

    public HotSpotG1WriteBarrierSnippets(GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        this.config = config;
        this.threadRegister = registers.getThreadRegister();
    }

    @Override
    protected Word getThread() {
        return HotSpotReplacementsUtil.registerAsWord(threadRegister);
    }

    @Override
    protected int wordSize() {
        return HotSpotReplacementsUtil.wordSize();
    }

    @Override
    protected int objectArrayIndexScale() {
        return ReplacementsUtil.arrayIndexScale(INJECTED_METAACCESS, JavaKind.Object);
    }

    @Override
    protected int satbQueueMarkingOffset() {
        return HotSpotReplacementsUtil.g1SATBQueueMarkingOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int satbQueueBufferOffset() {
        return HotSpotReplacementsUtil.g1SATBQueueBufferOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int satbQueueIndexOffset() {
        return HotSpotReplacementsUtil.g1SATBQueueIndexOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int cardQueueBufferOffset() {
        return HotSpotReplacementsUtil.g1CardQueueBufferOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int cardQueueIndexOffset() {
        return HotSpotReplacementsUtil.g1CardQueueIndexOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected byte dirtyCardValue() {
        return HotSpotReplacementsUtil.dirtyCardValue(INJECTED_VMCONFIG);
    }

    @Override
    protected byte youngCardValue() {
        return HotSpotReplacementsUtil.g1YoungCardValue(INJECTED_VMCONFIG);
    }

    @Override
    protected Word cardTableAddress() {
        return WordFactory.unsigned(GraalHotSpotVMConfigNode.cardTableAddress());
    }

    @Override
    protected int cardTableShift() {
        return HotSpotReplacementsUtil.cardTableShift(INJECTED_VMCONFIG);
    }

    @Override
    protected int logOfHeapRegionGrainBytes() {
        return GraalHotSpotVMConfigNode.logOfHeapRegionGrainBytes();
    }

    @Override
    protected ForeignCallDescriptor preWriteBarrierCallDescriptor() {
        return G1WBPRECALL;
    }

    @Override
    protected ForeignCallDescriptor postWriteBarrierCallDescriptor() {
        return G1WBPOSTCALL;
    }

    @Override
    protected boolean verifyOops() {
        return HotSpotReplacementsUtil.verifyOops(INJECTED_VMCONFIG);
    }

    @Override
    protected boolean verifyBarrier() {
        return ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED || config.verifyBeforeGC || config.verifyAfterGC;
    }

    @Override
    protected long gcTotalCollectionsAddress() {
        return HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG);
    }

    @Override
    protected ForeignCallDescriptor verifyOopCallDescriptor() {
        return HotSpotForeignCallsProviderImpl.VERIFY_OOP;
    }

    @Override
    protected ForeignCallDescriptor validateObjectCallDescriptor() {
        return VALIDATE_OBJECT;
    }

    @Override
    protected ForeignCallDescriptor printfCallDescriptor() {
        return Log.LOG_PRINTF;
    }

    @Override
    protected ResolvedJavaType referenceType() {
        return HotSpotReplacementsUtil.referenceType(INJECTED_METAACCESS);
    }

    @Override
    protected long referentOffset() {
        return HotSpotReplacementsUtil.referentOffset(INJECTED_METAACCESS);
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo g1PreWriteBarrier;
        private final SnippetInfo g1ReferentReadBarrier;
        private final SnippetInfo g1PostWriteBarrier;
        private final SnippetInfo g1ArrayRangePreWriteBarrier;
        private final SnippetInfo g1ArrayRangePostWriteBarrier;

        private final G1WriteBarrierLowerer lowerer;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Group.Factory factory, HotSpotProviders providers, TargetDescription target, GraalHotSpotVMConfig config) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            this.lowerer = new HotspotG1WriteBarrierLowerer(config, factory);

            HotSpotG1WriteBarrierSnippets receiver = new HotSpotG1WriteBarrierSnippets(config, providers.getRegisters());
            g1PreWriteBarrier = snippet(G1WriteBarrierSnippets.class, "g1PreWriteBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION, SATB_QUEUE_INDEX_LOCATION,
                            SATB_QUEUE_BUFFER_LOCATION);
            g1ReferentReadBarrier = snippet(G1WriteBarrierSnippets.class, "g1ReferentReadBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION,
                            SATB_QUEUE_INDEX_LOCATION, SATB_QUEUE_BUFFER_LOCATION);
            g1PostWriteBarrier = snippet(G1WriteBarrierSnippets.class, "g1PostWriteBarrier", null, receiver, GC_CARD_LOCATION, GC_INDEX_LOCATION, GC_LOG_LOCATION, CARD_QUEUE_INDEX_LOCATION,
                            CARD_QUEUE_BUFFER_LOCATION);
            g1ArrayRangePreWriteBarrier = snippet(G1WriteBarrierSnippets.class, "g1ArrayRangePreWriteBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION,
                            SATB_QUEUE_INDEX_LOCATION, SATB_QUEUE_BUFFER_LOCATION);
            g1ArrayRangePostWriteBarrier = snippet(G1WriteBarrierSnippets.class, "g1ArrayRangePostWriteBarrier", null, receiver, GC_CARD_LOCATION, GC_INDEX_LOCATION, GC_LOG_LOCATION,
                            CARD_QUEUE_INDEX_LOCATION, CARD_QUEUE_BUFFER_LOCATION);
        }

        public void lower(G1PreWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, g1PreWriteBarrier, barrier, tool);
        }

        public void lower(G1ReferentFieldReadBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, g1ReferentReadBarrier, barrier, tool);
        }

        public void lower(G1PostWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, g1PostWriteBarrier, barrier, tool);
        }

        public void lower(G1ArrayRangePreWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, g1ArrayRangePreWriteBarrier, barrier, tool);
        }

        public void lower(G1ArrayRangePostWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, g1ArrayRangePostWriteBarrier, barrier, tool);
        }
    }

    static final class HotspotG1WriteBarrierLowerer extends G1WriteBarrierLowerer {
        private final CompressEncoding oopEncoding;

        HotspotG1WriteBarrierLowerer(GraalHotSpotVMConfig config, Factory factory) {
            super(factory);
            oopEncoding = config.useCompressedOops ? config.getOopEncoding() : null;
        }

        @Override
        public ValueNode uncompress(ValueNode expected) {
            assert oopEncoding != null;
            return HotSpotCompressionNode.uncompress(expected, oopEncoding);
        }
    }
}
