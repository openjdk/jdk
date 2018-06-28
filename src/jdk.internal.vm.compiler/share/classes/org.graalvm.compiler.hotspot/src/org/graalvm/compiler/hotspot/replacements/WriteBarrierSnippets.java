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

import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigBase.INJECTED_METAACCESS;
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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotCompressionNode;
import org.graalvm.compiler.hotspot.nodes.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.SerialWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.VMErrorNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
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
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.Log;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.replacements.nodes.DirectStoreNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.UnsignedWord;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

public class WriteBarrierSnippets implements Snippets {

    static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            Group countersWriteBarriers = factory.createSnippetCounterGroup("WriteBarriers");
            serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
            g1AttemptedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPreWriteBarrier", "Number of attempted G1 Pre Write Barriers");
            g1EffectivePreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectivePreWriteBarrier", "Number of effective G1 Pre Write Barriers");
            g1ExecutedPreWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPreWriteBarrier", "Number of executed G1 Pre Write Barriers");
            g1AttemptedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1AttemptedPostWriteBarrier", "Number of attempted G1 Post Write Barriers");
            g1EffectiveAfterXORPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterXORPostWriteBarrier",
                            "Number of effective G1 Post Write Barriers (after passing the XOR test)");
            g1EffectiveAfterNullPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1EffectiveAfterNullPostWriteBarrier",
                            "Number of effective G1 Post Write Barriers (after passing the NULL test)");
            g1ExecutedPostWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "g1ExecutedPostWriteBarrier", "Number of executed G1 Post Write Barriers");

        }

        final SnippetCounter serialWriteBarrierCounter;
        final SnippetCounter g1AttemptedPreWriteBarrierCounter;
        final SnippetCounter g1EffectivePreWriteBarrierCounter;
        final SnippetCounter g1ExecutedPreWriteBarrierCounter;
        final SnippetCounter g1AttemptedPostWriteBarrierCounter;
        final SnippetCounter g1EffectiveAfterXORPostWriteBarrierCounter;
        final SnippetCounter g1EffectiveAfterNullPostWriteBarrierCounter;
        final SnippetCounter g1ExecutedPostWriteBarrierCounter;
    }

    public static final LocationIdentity GC_CARD_LOCATION = NamedLocationIdentity.mutable("GC-Card");
    public static final LocationIdentity GC_LOG_LOCATION = NamedLocationIdentity.mutable("GC-Log");
    public static final LocationIdentity GC_INDEX_LOCATION = NamedLocationIdentity.mutable("GC-Index");

    private static void serialWriteBarrier(Pointer ptr, Counters counters) {
        counters.serialWriteBarrierCounter.inc();
        final long startAddress = GraalHotSpotVMConfigNode.cardTableAddress();
        Word base = (Word) ptr.unsignedShiftRight(cardTableShift(INJECTED_VMCONFIG));
        if (((int) startAddress) == startAddress && GraalHotSpotVMConfigNode.isCardTableAddressConstant()) {
            base.writeByte((int) startAddress, (byte) 0, GC_CARD_LOCATION);
        } else {
            base.writeByte(WordFactory.unsigned(startAddress), (byte) 0, GC_CARD_LOCATION);
        }
    }

    @Snippet
    public static void serialImpreciseWriteBarrier(Object object, @ConstantParameter boolean verifyBarrier, @ConstantParameter Counters counters) {
        if (verifyBarrier) {
            verifyNotArray(object);
        }
        serialWriteBarrier(Word.objectToTrackedPointer(object), counters);
    }

    @Snippet
    public static void serialPreciseWriteBarrier(Address address, @ConstantParameter Counters counters) {
        serialWriteBarrier(Word.fromAddress(address), counters);
    }

    @Snippet
    public static void serialArrayRangeWriteBarrier(Address address, int length, @ConstantParameter int elementStride) {
        if (length == 0) {
            return;
        }
        int cardShift = cardTableShift(INJECTED_VMCONFIG);
        final long cardStart = GraalHotSpotVMConfigNode.cardTableAddress();
        long start = getPointerToFirstArrayElement(address, length, elementStride) >>> cardShift;
        long end = getPointerToLastArrayElement(address, length, elementStride) >>> cardShift;
        long count = end - start + 1;
        while (count-- > 0) {
            DirectStoreNode.storeBoolean((start + cardStart) + count, false, JavaKind.Boolean);
        }
    }

    @Snippet
    public static void g1PreWriteBarrier(Address address, Object object, Object expectedObject, @ConstantParameter boolean doLoad, @ConstantParameter boolean nullCheck,
                    @ConstantParameter Register threadRegister, @ConstantParameter boolean trace, @ConstantParameter Counters counters) {
        if (nullCheck) {
            NullCheckNode.nullCheck(address);
        }
        Word thread = registerAsWord(threadRegister);
        verifyOop(object);
        Object fixedExpectedObject = FixedValueAnchorNode.getObject(expectedObject);
        Word field = Word.fromAddress(address);
        Pointer previousOop = Word.objectToTrackedPointer(fixedExpectedObject);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset(INJECTED_VMCONFIG));
        int gcCycle = 0;
        if (trace) {
            Pointer gcTotalCollectionsAddress = WordFactory.pointer(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG));
            gcCycle = (int) gcTotalCollectionsAddress.readLong(0);
            log(trace, "[%d] G1-Pre Thread %p Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Expected Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(fixedExpectedObject).rawValue());
            log(trace, "[%d] G1-Pre Thread %p Field %p\n", gcCycle, thread.rawValue(), field.rawValue());
            log(trace, "[%d] G1-Pre Thread %p Marking %d\n", gcCycle, thread.rawValue(), markingValue);
            log(trace, "[%d] G1-Pre Thread %p DoLoad %d\n", gcCycle, thread.rawValue(), doLoad ? 1L : 0L);
        }
        counters.g1AttemptedPreWriteBarrierCounter.inc();
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
            counters.g1EffectivePreWriteBarrierCounter.inc();
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousOop.notEqual(0))) {
                counters.g1ExecutedPreWriteBarrierCounter.inc();
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
    public static void g1PostWriteBarrier(Address address, Object object, Object value, @ConstantParameter boolean usePrecise, @ConstantParameter boolean verifyBarrier,
                    @ConstantParameter Register threadRegister, @ConstantParameter boolean trace, @ConstantParameter Counters counters) {
        Word thread = registerAsWord(threadRegister);
        Object fixedValue = FixedValueAnchorNode.getObject(value);
        verifyOop(object);
        verifyOop(fixedValue);
        validateObject(object, fixedValue);
        Pointer oop;
        if (usePrecise) {
            oop = Word.fromAddress(address);
        } else {
            if (verifyBarrier) {
                verifyNotArray(object);
            }
            oop = Word.objectToTrackedPointer(object);
        }
        int gcCycle = 0;
        if (trace) {
            Pointer gcTotalCollectionsAddress = WordFactory.pointer(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG));
            gcCycle = (int) gcTotalCollectionsAddress.readLong(0);
            log(trace, "[%d] G1-Post Thread: %p Object: %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] G1-Post Thread: %p Field: %p\n", gcCycle, thread.rawValue(), oop.rawValue());
        }
        Pointer writtenValue = Word.objectToTrackedPointer(fixedValue);
        // The result of the xor reveals whether the installed pointer crosses heap regions.
        // In case it does the write barrier has to be issued.
        final int logOfHeapRegionGrainBytes = GraalHotSpotVMConfigNode.logOfHeapRegionGrainBytes();
        UnsignedWord xorResult = (oop.xor(writtenValue)).unsignedShiftRight(logOfHeapRegionGrainBytes);

        // Calculate the address of the card to be enqueued to the
        // thread local card queue.
        UnsignedWord cardBase = oop.unsignedShiftRight(cardTableShift(INJECTED_VMCONFIG));
        final long startAddress = GraalHotSpotVMConfigNode.cardTableAddress();
        int displacement = 0;
        if (((int) startAddress) == startAddress && GraalHotSpotVMConfigNode.isCardTableAddressConstant()) {
            displacement = (int) startAddress;
        } else {
            cardBase = cardBase.add(WordFactory.unsigned(startAddress));
        }
        Word cardAddress = (Word) cardBase.add(displacement);

        counters.g1AttemptedPostWriteBarrierCounter.inc();
        if (probability(FREQUENT_PROBABILITY, xorResult.notEqual(0))) {
            counters.g1EffectiveAfterXORPostWriteBarrierCounter.inc();

            // If the written value is not null continue with the barrier addition.
            if (probability(FREQUENT_PROBABILITY, writtenValue.notEqual(0))) {
                byte cardByte = cardAddress.readByte(0, GC_CARD_LOCATION);
                counters.g1EffectiveAfterNullPostWriteBarrierCounter.inc();

                // If the card is already dirty, (hence already enqueued) skip the insertion.
                if (probability(NOT_FREQUENT_PROBABILITY, cardByte != g1YoungCardValue(INJECTED_VMCONFIG))) {
                    MembarNode.memoryBarrier(STORE_LOAD, GC_CARD_LOCATION);
                    byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
                    if (probability(NOT_FREQUENT_PROBABILITY, cardByteReload != dirtyCardValue(INJECTED_VMCONFIG))) {
                        log(trace, "[%d] G1-Post Thread: %p Card: %p \n", gcCycle, thread.rawValue(), WordFactory.unsigned((int) cardByte).rawValue());
                        cardAddress.writeByte(0, (byte) 0, GC_CARD_LOCATION);
                        counters.g1ExecutedPostWriteBarrierCounter.inc();

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

    private static void verifyNotArray(Object object) {
        if (object != null) {
            // Manually build the null check and cast because we're in snippet that's lowered late.
            AssertionNode.assertion(false, !PiNode.piCastNonNull(object, Object.class).getClass().isArray(), "imprecise card mark used with array");
        }
    }

    @Snippet
    public static void g1ArrayRangePreWriteBarrier(Address address, int length, @ConstantParameter int elementStride, @ConstantParameter Register threadRegister) {
        Word thread = registerAsWord(threadRegister);
        byte markingValue = thread.readByte(g1SATBQueueMarkingOffset(INJECTED_VMCONFIG));
        // If the concurrent marker is not enabled or the vector length is zero, return.
        if (markingValue == (byte) 0 || length == 0) {
            return;
        }
        Word bufferAddress = thread.readWord(g1SATBQueueBufferOffset(INJECTED_VMCONFIG));
        Word indexAddress = thread.add(g1SATBQueueIndexOffset(INJECTED_VMCONFIG));
        long indexValue = indexAddress.readWord(0).rawValue();
        final int scale = HotSpotReplacementsUtil.arrayIndexScale(INJECTED_METAACCESS, JavaKind.Object);
        long start = getPointerToFirstArrayElement(address, length, elementStride);

        for (int i = 0; i < length; i++) {
            Word arrElemPtr = WordFactory.pointer(start + i * scale);
            Pointer oop = Word.objectToTrackedPointer(arrElemPtr.readObject(0, BarrierType.NONE));
            verifyOop(oop.toObject());
            if (oop.notEqual(0)) {
                if (indexValue != 0) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(WordFactory.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    logAddress.writeWord(0, oop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, WordFactory.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    g1PreBarrierStub(G1WBPRECALL, oop.toObject());
                }
            }
        }
    }

    @Snippet
    public static void g1ArrayRangePostWriteBarrier(Address address, int length, @ConstantParameter int elementStride, @ConstantParameter Register threadRegister) {
        if (length == 0) {
            return;
        }
        Word thread = registerAsWord(threadRegister);
        Word bufferAddress = thread.readWord(g1CardQueueBufferOffset(INJECTED_VMCONFIG));
        Word indexAddress = thread.add(g1CardQueueIndexOffset(INJECTED_VMCONFIG));
        long indexValue = thread.readWord(g1CardQueueIndexOffset(INJECTED_VMCONFIG)).rawValue();

        int cardShift = cardTableShift(INJECTED_VMCONFIG);
        final long cardStart = GraalHotSpotVMConfigNode.cardTableAddress();
        long start = getPointerToFirstArrayElement(address, length, elementStride) >>> cardShift;
        long end = getPointerToLastArrayElement(address, length, elementStride) >>> cardShift;
        long count = end - start + 1;

        while (count-- > 0) {
            Word cardAddress = WordFactory.unsigned((start + cardStart) + count);
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
                        Word logAddress = bufferAddress.add(WordFactory.unsigned(indexValue));
                        // Log the object to be scanned as well as update
                        // the card queue's next index.
                        logAddress.writeWord(0, cardAddress, GC_LOG_LOCATION);
                        indexAddress.writeWord(0, WordFactory.unsigned(indexValue), GC_INDEX_LOCATION);
                    } else {
                        g1PostBarrierStub(G1WBPOSTCALL, cardAddress);
                    }
                }
            }
        }
    }

    private static long getPointerToFirstArrayElement(Address address, int length, int elementStride) {
        long result = Word.fromAddress(address).rawValue();
        if (elementStride < 0) {
            // the address points to the place after the last array element
            result = result + elementStride * length;
        }
        return result;
    }

    private static long getPointerToLastArrayElement(Address address, int length, int elementStride) {
        long result = Word.fromAddress(address).rawValue();
        if (elementStride < 0) {
            // the address points to the place after the last array element
            result = result + elementStride;
        } else {
            result = result + (length - 1) * elementStride;
        }
        return result;
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
        private final Counters counters;
        private final boolean verifyBarrier;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Group.Factory factory, HotSpotProviders providers, TargetDescription target,
                        GraalHotSpotVMConfig config) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            this.oopEncoding = config.useCompressedOops ? config.getOopEncoding() : null;
            this.verifyBarrier = ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED || config.verifyBeforeGC || config.verifyAfterGC;
            this.counters = new Counters(factory);
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
                args.addConst("verifyBarrier", verifyBarrier);
            }
            args.addConst("counters", counters);
            template(writeBarrier, args).instantiate(providers.getMetaAccess(), writeBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(SerialArrayRangeWriteBarrier arrayRangeWriteBarrier, LoweringTool tool) {
            Arguments args = new Arguments(serialArrayRangeWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", arrayRangeWriteBarrier.getAddress());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("elementStride", arrayRangeWriteBarrier.getElementStride());
            template(arrayRangeWriteBarrier, args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
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
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = HotSpotCompressionNode.uncompress(expected, oopEncoding);
            }
            args.add("expectedObject", expected);

            args.addConst("doLoad", writeBarrierPre.doLoad());
            args.addConst("nullCheck", writeBarrierPre.getNullCheck());
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier(writeBarrierPre.graph()));
            args.addConst("counters", counters);
            template(writeBarrierPre, args).instantiate(providers.getMetaAccess(), writeBarrierPre, DEFAULT_REPLACER, args);
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
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                expected = HotSpotCompressionNode.uncompress(expected, oopEncoding);
            }

            args.add("expectedObject", expected);
            args.addConst("doLoad", readBarrier.doLoad());
            args.addConst("nullCheck", false);
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier(readBarrier.graph()));
            args.addConst("counters", counters);
            template(readBarrier, args).instantiate(providers.getMetaAccess(), readBarrier, DEFAULT_REPLACER, args);
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
            if (value.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                assert oopEncoding != null;
                value = HotSpotCompressionNode.uncompress(value, oopEncoding);
            }
            args.add("value", value);

            args.addConst("usePrecise", writeBarrierPost.usePrecise());
            args.addConst("verifyBarrier", verifyBarrier);
            args.addConst("threadRegister", registers.getThreadRegister());
            args.addConst("trace", traceBarrier(writeBarrierPost.graph()));
            args.addConst("counters", counters);
            template(writeBarrierPost, args).instantiate(providers.getMetaAccess(), writeBarrierPost, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePreWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePreWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", arrayRangeWriteBarrier.getAddress());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("elementStride", arrayRangeWriteBarrier.getElementStride());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(arrayRangeWriteBarrier, args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
        }

        public void lower(G1ArrayRangePostWriteBarrier arrayRangeWriteBarrier, HotSpotRegistersProvider registers, LoweringTool tool) {
            Arguments args = new Arguments(g1ArrayRangePostWriteBarrier, arrayRangeWriteBarrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", arrayRangeWriteBarrier.getAddress());
            args.add("length", arrayRangeWriteBarrier.getLength());
            args.addConst("elementStride", arrayRangeWriteBarrier.getElementStride());
            args.addConst("threadRegister", registers.getThreadRegister());
            template(arrayRangeWriteBarrier, args).instantiate(providers.getMetaAccess(), arrayRangeWriteBarrier, DEFAULT_REPLACER, args);
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

    public static boolean traceBarrier(StructuredGraph graph) {
        return GraalOptions.GCDebugStartCycle.getValue(graph.getOptions()) > 0 &&
                        ((int) ((Pointer) WordFactory.pointer(HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG))).readLong(0) > GraalOptions.GCDebugStartCycle.getValue(
                                        graph.getOptions()));
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    public static void validateObject(Object parent, Object child) {
        if (verifyOops(INJECTED_VMCONFIG) && child != null) {
            Word parentWord = Word.objectToTrackedPointer(parent);
            Word childWord = Word.objectToTrackedPointer(child);
            if (!validateOop(VALIDATE_OBJECT, parentWord, childWord)) {
                log(true, "Verification ERROR, Parent: %p Child: %p\n", parentWord.rawValue(), childWord.rawValue());
                VMErrorNode.vmError("Verification ERROR, Parent: %p\n", parentWord.rawValue());
            }
        }
    }

    public static final ForeignCallDescriptor VALIDATE_OBJECT = new ForeignCallDescriptor("validate_object", boolean.class, Word.class, Word.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word parent, Word object);

}
