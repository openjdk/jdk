/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.SnippetCounters;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayBaseOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayIndexScale;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.cardTableShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.dirtyCardValue;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1CardQueueBufferOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1CardQueueIndexOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1SATBQueueBufferOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1SATBQueueIndexOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1SATBQueueMarkingOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.g1YoungCardValue;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOop;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.verifyOops;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.CompressEncoding;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.CompressionNode;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.hotspot.nodes.GetObjectAddressNode;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.SerialWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.NullCheckNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.Log;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.DirectObjectStoreNode;
import org.graalvm.compiler.replacements.nodes.DirectStoreNode;
import org.graalvm.compiler.word.Pointer;
import org.graalvm.compiler.word.Unsigned;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

public class WriteBarrierSnippets implements Snippets {

    private static final SnippetCounter.Group countersWriteBarriers = SnippetCounters.getValue() ? new SnippetCounter.Group("WriteBarriers") : null;
    private static final SnippetCounter serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
    private static final SnippetCounter g1AttemptedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPreWriteBarrier", "Number of attempted G1 Pre Write Barriers");
    private static final SnippetCounter g1EffectivePreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectivePreWriteBarrier", "Number of effective G1 Pre Write Barriers");
    private static final SnippetCounter g1ExecutedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPreWriteBarrier", "Number of executed G1 Pre Write Barriers");
    private static final SnippetCounter g1AttemptedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPostWriteBarrier", "Number of attempted G1 Post Write Barriers");
    private static final SnippetCounter g1EffectiveAfterXORPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterXORPostWriteBarrier",
                    "Number of effective G1 Post Write Barriers (after passing the XOR test)");
    private static final SnippetCounter g1EffectiveAfterNullPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterNullPostWriteBarrier",
                    "Number of effective G1 Post Write Barriers (after passing the NULL test)");
    private static final SnippetCounter g1ExecutedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPostWriteBarrier", "Number of executed G1 Post Write Barriers");

    public static final LocationIdentity GC_CARD_LOCATION = NamedLocationIdentity.mutable("GC-Card");
    public static final LocationIdentity GC_LOG_LOCATION = NamedLocationIdentity.mutable("GC-Log");
    public static final LocationIdentity GC_INDEX_LOCATION = NamedLocationIdentity.mutable("GC-Index");

    private static void serialWriteBarrier(Pointer ptr) {
        serialWriteBarrierCounter.inc();
        final long startAddress = GraalHotSpotVMConfigNode.cardTableAddress();
        Word base = (Word) ptr.unsignedShiftRight(cardTableShift(INJECTED_VMCONFIG));
        if (((int) startAddress) == startAddress && GraalHotSpotVMConfigNode.isCardTableAddressConstant()) {
            base.writeByte((int) startAddress, (byte) 0, GC_CARD_LOCATION);
        } else {
            base.writeByte(Word.unsigned(startAddress), (byte) 0, GC_CARD_LOCATION);
        }
    }

    @Snippet
    public static void serialImpreciseWriteBarrier(Object object) {
        serialWriteBarrier(Word.objectToTrackedPointer(object));
    }

    @Snippet
    public static void serialPreciseWriteBarrier(Address address) {
        serialWriteBarrier(Word.fromAddress(address));
    }

    @Snippet
    public static void serialArrayRangeWriteBarrier(Object object, int startIndex, int length) {
        if (length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        int cardShift = cardTableShift(INJECTED_VMCONFIG);
        final long cardStart = GraalHotSpotVMConfigNode.cardTableAddress();
        final int scale = arrayIndexScale(JavaKind.Object);
        int header = arrayBaseOffset(JavaKind.Object);
        long dstAddr = GetObjectAddressNode.get(dest);
        long start = (dstAddr + header + (long) startIndex * scale) >>> cardShift;
        long end = (dstAddr + header + ((long) startIndex + length - 1) * scale) >>> cardShift;
        long count = end - start + 1;
        while (count-- > 0) {
            DirectStoreNode.storeBoolean((start + cardStart) + count, false, JavaKind.Boolean);
        }
    }

    @Snippet
    public static void g1PreWriteBarrier(Address address, Object object, Object expectedObject, @ConstantParameter boolean doLoad, @ConstantParameter boolean nullCheck,
                    @ConstantParameter Register threadRegister, @ConstantParameter boolean trace) {
        if (nullCheck) {
            NullCheckNode.nullCheck(address);
        }
        Word thread = registerAsWord(threadRegister);
        verifyOop(object);
        Object fixedExpectedObject = FixedValueAnchorNode.getObject(expectedObject);
        Pointer field = Word.fromAddress(address);
        Pointer previousOop = Word.objectToTrackedPointer(fixedExpectedObject);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset(INJECTED_VMCONFIG));
        int gcCycle = 0;
        if (trace) {
            gcCycle = (int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG)).readLong(0);
            log(trace, "[%d] G1-Pre Thread %p Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Expected Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(fixedExpectedObject).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Field %p\n", gcCycle, thread.rawValue(), field.rawValue());
            log(trace, "[%d] G1-Pre Thread %p Marking %d\n", gcCycle, thread.rawValue(), markingValue);
            log(trace, "[%d] G1-Pre Thread %p DoLoad %d\n", gcCycle, thread.rawValue(), doLoad ? 1L : 0L);
        }
        g1AttemptedPreWriteBarrierCounter.inc();
        // If the concurrent marker is enabled, the barrier is issued.
        if (probability(NOT_FREQUENT_PROBABILITY, markingValue != (byte) 0)) {
            // If the previous value has to be loaded (before the write), the load is issued.
            // The load is always issued except the cases of CAS and referent field.
            if (probability(LIKELY_PROBABILITY, doLoad)) {
                previousOop = Word.objectToTrackedPointer(field.readObject(0, BarrierType.NONE));
                if (trace) {
                    log(trace, "[%d] G1-Pre Thread %p Previous Object %p\n ", gcCycle, thread.rawValue(), previousOop.rawValue());
                    verifyOop(previousOop.toObject());
                }
            }
            g1EffectivePreWriteBarrierCounter.inc();
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousOop.notEqual(0))) {
                g1ExecutedPreWriteBarrierCounter.inc();
                // If the thread-local SATB buffer is full issue a native call which will
                // initialize a new one and add the entry.
                Word indexAddress = thread.add(g1SATBQueueIndexOffset(INJECTED_VMCONFIG));
                Word indexValue = indexAddress.readWord(0);
                if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                    Word bufferAddress = thread.readWord(g1SATBQueueBufferOffset(INJECTED_VMCONFIG));
                    Word nextIndex = indexValue.subtract(wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, previousOop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, previousOop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1PostWriteBarrier(Address address, Object object, Object value, @ConstantParameter boolean usePrecise, @ConstantParameter Register threadRegister,
                    @ConstantParameter boolean trace) {
        Word thread = registerAsWord(threadRegister);
        Object fixedValue = FixedValueAnchorNode.getObject(value);
        verifyOop(object);
        verifyOop(fixedValue);
        validateObject(object, fixedValue);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromAddress(address);
        } else {
            oop = Word.objectToTrackedPointer(object);
        }
        int gcCycle = 0;
        if (trace) {
            gcCycle = (int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG)).readLong(0);
            log(trace, "[%d] G1-Post Thread: %p Object: %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Post Thread: %p Field: %p\n", gcCycle, thread.rawValue(), oop.rawValue());
        }
        Pointer writtenValue = Word.objectToTrackedPointer(fixedValue);
        // The result of the xor reveals whether the installed pointer crosses heap regions.
        // In case it does the write barrier has to be issued.
        final int logOfHeapRegionGrainBytes = GraalHotSpotVMConfigNode.logOfHeapRegionGrainBytes();
        Unsigned xorResult = (oop.xor(writtenValue)).unsignedShiftRight(logOfHeapRegionGrainBytes);

        // Calculate the address of the card to be enqueued to the
        // thread local card queue.
        Unsigned cardBase = oop.unsignedShiftRight(cardTableShift(INJECTED_VMCONFIG));
        final long startAddress = GraalHotSpotVMConfigNode.cardTableAddress();
        int displacement = 0;
        if (((int) startAddress) == startAddress && GraalHotSpotVMConfigNode.isCardTableAddressConstant()) {
            displacement = (int) startAddress;
        } else {
            cardBase = cardBase.add(Word.unsigned(startAddress));
        }
        Word cardAddress = (Word) cardBase.add(displacement);

        g1AttemptedPostWriteBarrierCounter.inc();
        if (probability(FREQUENT_PROBABILITY, xorResult.notEqual(0))) {
            g1EffectiveAfterXORPostWriteBarrierCounter.inc();

            // If the written value is not null continue with the barrier addition.
            if (probability(FREQUENT_PROBABILITY, writtenValue.notEqual(0))) {
                byte cardByte = cardAddress.readByte(0, GC_CARD_LOCATION);
                g1EffectiveAfterNullPostWriteBarrierCounter.inc();

                // If the card is already dirty, (hence already enqueued) skip the insertion.
                if (probability(NOT_FREQUENT_PROBABILITY, cardByte != g1YoungCardValue(INJECTED_VMCONFIG))) {
                    MembarNode.memoryBarrier(STORE_LOAD, GC_CARD_LOCATION);
                    byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
                    if (probability(NOT_FREQUENT_PROBABILITY, cardByteReload != dirtyCardValue(INJECTED_VMCONFIG))) {
                        log(trace, "[%d] G1-Post Thread: %p Card: %p \n", gcCycle, thread.rawValue(), Word.unsigned(cardByte).rawValue());
                        cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                        g1ExecutedPostWriteBarrierCounter.inc();

                        // If the thread local card queue is full, issue a native call which will
                        // initialize a new one and add the card entry.
                        Word indexAddress = thread.add(g1CardQueueIndexOffset(INJECTED_VMCONFIG));
                        Word indexValue = thread.readWord(g1CardQueueIndexOffset(INJECTED_VMCONFIG));
                        if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                            Word bufferAddress = thread.readWord(g1CardQueueBufferOffset(INJECTED_VMCONFIG));
                            Word nextIndex = indexValue.subtract(wordSize());
                            Word logAddress = bufferAddress.add(nextIndex);
                            // Log the object to be scanned as well as update
                            // the card queue's next index.
                            logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                            indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                        } else {
                            g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                        }
                    }
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePreWriteBarrier(Object object, int startIndex, int length, @ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset(INJECTED_VMCONFIG));
        // If the concurrent marker is not enabled or the vector length is zero, return.
        if (markingValue == (byte) 0 || length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        Word bufferAddress = thread.readWord(g1SATBQueueBufferOffset(INJECTED_VMCONFIG));
        Word indexAddress = thread.add(g1SATBQueueIndexOffset(INJECTED_VMCONFIG));
        long dstAddr = GetObjectAddressNode.get(dest);
        long indexValue = indexAddress.readWord(0).rawValue();
        final int scale = arrayIndexScale(JavaKind.Object);
        int header = arrayBaseOffset(JavaKind.Object);

        for (int i = startIndex; i < length; i++) {
            long address = dstAddr + header + (i * scale);
            Pointer oop = Word.objectToTrackedPointer(Word.unsigned(address).readObject(0, BarrierType.NONE));
            verifyOop(oop.toObject());
            if (oop.notEqual(0)) {
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, oop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, Word.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, oop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePostWriteBarrier(Object object, int startIndex, int length, @ConstantParameter Register threadRegister) {
        if (length == 0) {
            return;
        }
        Object dest = FixedValueAnchorNode.getObject(object);
        Word thread = registerAsWord(threadRegister);
        Word bufferAddress = thread.readWord(g1CardQueueBufferOffset(INJECTED_VMCONFIG));
        Word indexAddress = thread.add(g1CardQueueIndexOffset(INJECTED_VMCONFIG));
        long indexValue = thread.readWord(g1CardQueueIndexOffset(INJECTED_VMCONFIG)).rawValue();

        int cardShift = cardTableShift(INJECTED_VMCONFIG);
        final long cardStart = GraalHotSpotVMConfigNode.cardTableAddress();
        final int scale = arrayIndexScale(JavaKind.Object);
        int header = arrayBaseOffset(JavaKind.Object);
        long dstAddr = GetObjectAddressNode.get(dest);
        long start = (dstAddr + header + (long) startIndex * scale) >>> cardShift;
        long end = (dstAddr + header + ((long) startIndex + length - 1) * scale) >>> cardShift;
        long count = end - start + 1;

        while (count-- > 0) {
            Word cardAddress = Word.unsigned((start + cardStart) + count);
            byte cardByte = cardAddress.readByte(0, GC_CARD_LOCATION);
            // If the card is already dirty, (hence already enqueued) skip the insertion.
            if (probability(NOT_FREQUENT_PROBABILITY, cardByte != g1YoungCardValue(INJECTED_VMCONFIG))) {
                MembarNode.memoryBarrier(STORE_LOAD, GC_CARD_LOCATION);
                byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
                if (probability(NOT_FREQUENT_PROBABILITY, cardByteReload != dirtyCardValue(INJECTED_VMCONFIG))) {
                    cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                    // If the thread local card queue is full, issue a native call which will
                    // initialize a new one and add the card entry.
                    if (indexValue != 0) {
                        indexValue = indexValue - wordSize();
                        Word logAddress = bufferAddress.add(Word.unsigned(indexValue));
                        // Log the object to be scanned as well as update
                        // the card queue's next index.
                        logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                        indexAddress.writeWord(0, Word.unsigned(indexValue), GC_INDEX_LOCATION);
                    } else {
                        g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                    }
                }
            }
        }
    }

    public static final ForeignCallDescriptor G1WBPRECALL = new ForeignCallDescriptor("write_barrier_pre", void.class, Object.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void g1PreBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    public static final ForeignCallDescriptor G1WBPOSTCALL = new ForeignCallDescriptor("write_barrier_post", void.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void g1PostBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word card);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo serialImpreciseWriteBarrier = snippet(WriteBarrierSnippets.class, "serialImpreciseWriteBarrier", GC_CARD_LOCATION);
        private final SnippetInfo serialPreciseWriteBarrier = snippet(WriteBarrierSnippets.class, "serialPreciseWriteBarrier", GC_CARD_LOCATION);
        private final SnippetInfo serialArrayRangeWriteBarrier = snippet(WriteBarrierSnippets.class, "serialArrayRangeWriteBarrier");
        private final SnippetInfo g1PreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier", GC_INDEX_LOCATION, GC_LOG_LOCATION);
        private final SnippetInfo g1ReferentReadBarrier = snippet(WriteBarrierSnippets.class, "g1PreWriteBarrier", GC_INDEX_LOCATION, GC_LOG_LOCATION);
        private final SnippetInfo g1PostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1PostWriteBarrier", GC_CARD_LOCATION, GC_INDEX_LOCATION, GC_LOG_LOCATION);
        private final SnippetInfo g1ArrayRangePreWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePreWriteBarrier", GC_INDEX_LOCATION, GC_LOG_LOCATION);
        private final SnippetInfo g1ArrayRangePostWriteBarrier = snippet(WriteBarrierSnippets.class, "g1ArrayRangePostWriteBarrier", GC_CARD_LOCATION, GC_INDEX_LOCATION, GC_LOG_LOCATION);

        private final CompressEncoding oopEncoding;

        public Templates(HotSpotProviders providers, TargetDescription target, CompressEncoding oopEncoding) {
            super(providers, providers.getSnippetReflection(), target);
            this.oopEncoding = oopEncoding;
        }

        public void lower(SerialWriteBarrier writeBarrier, LoweringTool tool) {
            Arguments args;
            if (writeBarrier.usePrecise()) {
                args = new Arguments(serialPreciseWriteBarrier, writeBarrier.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("address", writeBarrier.getAddress());
            } else {
                args = new Arguments(serialImpreciseWriteBarrier, writeBarrier.graph().getGuardsStage(), tool.getLoweringStage());
                OffsetAddressNode address = (OffsetAddressNode) writeBarrier.getAddress();
                args.add("object", address.getBase());
            }
            template(args).instantiate(providers.getMetaAccess(), writeBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(SerialArrayRangeWriteBarrier arrayRangeWriteBarrier, LoweringTool tool) {
            Arguments args = new Arguments(serialArrayRangeWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PreWriteBarrier writeBarrierPre, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1PreWriteBarrier, writeBarrierPre.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = writeBarrierPre.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                args.add("object", null);
            }

            ValueNode expected = writeBarrierPre.getExpectedObject();
            if (expected != null && expected.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = CompressionNode.uncompress(expected, oopEncoding);
            }
            args.add("expectedObject", expected);

            args.addConst("doLoad", writeBarrierPre.doLoad());
            args.addConst("nullCheck", writeBarrierPre.getNullCheck());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), writeBarrierPre, DEFAULT_REPLACER, args);
        }

        public void lower(G1ReferentFieldReadBarrier readBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ReferentReadBarrier, readBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = readBarrier.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                args.add("object", null);
            }

            ValueNode expected = readBarrier.getExpectedObject();
            if (expected != null && expected.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = CompressionNode.uncompress(expected, oopEncoding);
            }

            args.add("expectedObject", expected);
            args.addConst("doLoad", readBarrier.doLoad());
            args.addConst("nullCheck", false);
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), readBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1PostWriteBarrier writeBarrierPost, HotSpotRegistersProvider registers, LoweringTool tool) {
            StructuredGraph graph = writeBarrierPost.graph();
            if (writeBarrierPost.alwaysNull()) {
                graph.removeFixed(writeBarrierPost);
                return;
            }
            Arguments args = new Arguments(g1PostWriteBarrier, graph.getGuardsStage(), tool.getLoweringStage());
            AddressNode address = writeBarrierPost.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                assert writeBarrierPost.usePrecise() : "found imprecise barrier that's not an object access " + writeBarrierPost;
                args.add("object", null);
            }

            ValueNode value = writeBarrierPost.getValue();
            if (value.stamp() instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                value = CompressionNode.uncompress(value, oopEncoding);
            }
            args.add("value", value);

            args.addConst("usePrecise", writeBarrierPost.usePrecise());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier());
            template(args).instantiate(providers.getMetaAccess(), writeBarrierPost, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePreWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePreWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePostWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePostWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("object", arrayRangeWriteBarrier.getObject());
            args.add("startIndex", arrayRangeWriteBarrier.getStartIndex());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }
    }

    /**
     * Log method of debugging purposes.
     */
    public static void log(boolean enabled, String format, long value) {
        if (enabled) {
            Log.printf(format, value);
        }
    }

    public static void log(boolean enabled, String format, long value1, long value2) {
        if (enabled) {
            Log.printf(format, value1, value2);
        }
    }

    public static void log(boolean enabled, String format, long value1, long value2, long value3) {
        if (enabled) {
            Log.printf(format, value1, value2, value3);
        }
    }

    public static boolean traceBarrier() {
        return GraalOptions.GCDebugStartCycle.getValue() > 0 &&
                        ((int) Word.unsigned(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG)).readLong(0) > GraalOptions.GCDebugStartCycle.getValue());
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    public static void validateObject(Object parent, Object child) {
        if (verifyOops(INJECTED_VMCONFIG) && child != null && !validateOop(VALIDATE_OBJECT, parent, child)) {
            log(true, "Verification ERROR, Parent: %p Child: %p\n", Word.objectToTrackedPointer(parent).rawValue(), Word.objectToTrackedPointer(child).rawValue());
            DirectObjectStoreNode.storeObject(null, 0, 0, null, LocationIdentity.any(), JavaKind.Object);
        }
    }

    public static final ForeignCallDescriptor VALIDATE_OBJECT = new ForeignCallDescriptor("validate_object", boolean.class, Word.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object parent, Object object);

}
