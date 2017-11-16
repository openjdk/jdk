/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HEAP_END_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HEAP_TOP_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.PROTOTYPE_MARK_WORD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_FAST_REFILL_WASTE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_NOF_REFILLS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_REFILL_WASTE_LIMIT_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_SIZE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_SLOW_ALLOCATIONS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_THREAD_ALLOCATED_BYTES_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayBaseOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.getAndClearObjectResult;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.initializeTlab;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.isInstanceKlassFullyInitialized;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readLayoutHelper;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.log2WordSize;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.prototypeMarkWordOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readTlabEnd;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readTlabStart;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readTlabTop;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.threadAllocatedBytesOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.threadTlabSizeOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabAlignmentReserveInHeapWords;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabFastRefillWasteOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabIntArrayMarkWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabNumberOfRefillsOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabRefillWasteIncrement;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabRefillWasteLimitOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabSlowAllocationsOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.tlabStats;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.useCMSIncrementalMode;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.useG1GC;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.useTLAB;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeTlabTop;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.handlePendingException;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.newDescriptor;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.printf;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.verifyObject;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.replacements.NewObjectSnippets;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaKind;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation. This stub is
 * called from the {@linkplain NewObjectSnippets inline} allocation code when TLAB allocation fails.
 * If this stub fails to refill the TLAB or allocate the object, it calls out to the HotSpot C++
 * runtime for to complete the allocation.
 */
public class NewInstanceStub extends SnippetStub {

    public NewInstanceStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super("newInstance", options, providers, linkage);
    }

    @Override
    protected Object[] makeConstArgs() {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) providers.getMetaAccess().lookupJavaType(int[].class);
        int count = method.getSignature().getParameterCount(false);
        Object[] args = new Object[count];
        assert checkConstArg(1, "intArrayHub");
        assert checkConstArg(2, "threadRegister");
        assert checkConstArg(3, "options");
        args[1] = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), intArrayType.klass(), null);
        args[2] = providers.getRegisters().getThreadRegister();
        args[3] = options;
        return args;
    }

    private static Word allocate(Word thread, int size) {
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        Word newTop = top.add(size);
        /*
         * this check might lead to problems if the TLAB is within 16GB of the address space end
         * (checked in c++ code)
         */
        if (probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            return top;
        }
        return WordFactory.zero();
    }

    @Fold
    static boolean logging(OptionValues options) {
        return StubOptions.TraceNewInstanceStub.getValue(options);
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     *
     * @param hub the hub of the object to be allocated
     * @param intArrayHub the hub for {@code int[].class}
     */
    @Snippet
    private static Object newInstance(KlassPointer hub, @ConstantParameter KlassPointer intArrayHub, @ConstantParameter Register threadRegister, @ConstantParameter OptionValues options) {
        /*
         * The type is known to be an instance so Klass::_layout_helper is the instance size as a
         * raw number
         */
        Word thread = registerAsWord(threadRegister);
        boolean inlineContiguousAllocationSupported = GraalHotSpotVMConfigNode.inlineContiguousAllocationSupported();
        if (!forceSlowPath(options) && inlineContiguousAllocationSupported && !useCMSIncrementalMode(INJECTED_VMCONFIG)) {
            if (isInstanceKlassFullyInitialized(hub)) {
                int sizeInBytes = readLayoutHelper(hub);
                Word memory = refillAllocate(thread, intArrayHub, sizeInBytes, logging(options));
                if (memory.notEqual(0)) {
                    Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(INJECTED_VMCONFIG), PROTOTYPE_MARK_WORD_LOCATION);
                    NewObjectSnippets.formatObjectForStub(hub, sizeInBytes, memory, prototypeMarkWord);
                    return verifyObject(memory.toObject());
                }
            }
        }

        if (logging(options)) {
            printf("newInstance: calling new_instance_c\n");
        }

        newInstanceC(NEW_INSTANCE_C, thread, hub);
        handlePendingException(thread, true);
        return verifyObject(getAndClearObjectResult(thread));
    }

    /**
     * Attempts to refill the current thread's TLAB and retries the allocation.
     *
     * @param intArrayHub the hub for {@code int[].class}
     * @param sizeInBytes the size of the allocation
     * @param log specifies if logging is enabled
     *
     * @return the newly allocated, uninitialized chunk of memory, or {@link WordFactory#zero()} if
     *         the operation was unsuccessful
     */
    static Word refillAllocate(Word thread, KlassPointer intArrayHub, int sizeInBytes, boolean log) {
        // If G1 is enabled, the "eden" allocation space is not the same always
        // and therefore we have to go to slowpath to allocate a new TLAB.
        if (useG1GC(INJECTED_VMCONFIG)) {
            return WordFactory.zero();
        }
        if (!useTLAB(INJECTED_VMCONFIG)) {
            return edenAllocate(WordFactory.unsigned(sizeInBytes), log);
        }
        Word intArrayMarkWord = WordFactory.unsigned(tlabIntArrayMarkWord(INJECTED_VMCONFIG));
        int alignmentReserveInBytes = tlabAlignmentReserveInHeapWords(INJECTED_VMCONFIG) * wordSize();

        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);

        // calculate amount of free space
        long tlabFreeSpaceInBytes = end.subtract(top).rawValue();

        if (log) {
            printf("refillTLAB: thread=%p\n", thread.rawValue());
            printf("refillTLAB: top=%p\n", top.rawValue());
            printf("refillTLAB: end=%p\n", end.rawValue());
            printf("refillTLAB: tlabFreeSpaceInBytes=%ld\n", tlabFreeSpaceInBytes);
        }

        long tlabFreeSpaceInWords = tlabFreeSpaceInBytes >>> log2WordSize();

        // Retain TLAB and allocate object in shared space if
        // the amount free in the TLAB is too large to discard.
        Word refillWasteLimit = thread.readWord(tlabRefillWasteLimitOffset(INJECTED_VMCONFIG), TLAB_REFILL_WASTE_LIMIT_LOCATION);
        if (tlabFreeSpaceInWords <= refillWasteLimit.rawValue()) {
            if (tlabStats(INJECTED_VMCONFIG)) {
                // increment number of refills
                thread.writeInt(tlabNumberOfRefillsOffset(INJECTED_VMCONFIG), thread.readInt(tlabNumberOfRefillsOffset(INJECTED_VMCONFIG), TLAB_NOF_REFILLS_LOCATION) + 1, TLAB_NOF_REFILLS_LOCATION);
                if (log) {
                    printf("thread: %p -- number_of_refills %d\n", thread.rawValue(), thread.readInt(tlabNumberOfRefillsOffset(INJECTED_VMCONFIG), TLAB_NOF_REFILLS_LOCATION));
                }
                // accumulate wastage
                int wastage = thread.readInt(tlabFastRefillWasteOffset(INJECTED_VMCONFIG), TLAB_FAST_REFILL_WASTE_LOCATION) + (int) tlabFreeSpaceInWords;
                if (log) {
                    printf("thread: %p -- accumulated wastage %d\n", thread.rawValue(), wastage);
                }
                thread.writeInt(tlabFastRefillWasteOffset(INJECTED_VMCONFIG), wastage, TLAB_FAST_REFILL_WASTE_LOCATION);
            }

            // if TLAB is currently allocated (top or end != null) then
            // fill [top, end + alignment_reserve) with array object
            if (top.notEqual(0)) {
                int headerSize = arrayBaseOffset(JavaKind.Int);
                // just like the HotSpot assembler stubs, assumes that tlabFreeSpaceInInts fits in
                // an int
                int tlabFreeSpaceInInts = (int) tlabFreeSpaceInBytes >>> 2;
                int length = ((alignmentReserveInBytes - headerSize) >>> 2) + tlabFreeSpaceInInts;
                NewObjectSnippets.formatArray(intArrayHub, 0, length, headerSize, top, intArrayMarkWord, false, false, null);

                long allocated = thread.readLong(threadAllocatedBytesOffset(INJECTED_VMCONFIG), TLAB_THREAD_ALLOCATED_BYTES_LOCATION);
                allocated = allocated + top.subtract(readTlabStart(thread)).rawValue();
                thread.writeLong(threadAllocatedBytesOffset(INJECTED_VMCONFIG), allocated, TLAB_THREAD_ALLOCATED_BYTES_LOCATION);
            }

            // refill the TLAB with an eden allocation
            Word tlabRefillSizeInWords = thread.readWord(threadTlabSizeOffset(INJECTED_VMCONFIG), TLAB_SIZE_LOCATION);
            Word tlabRefillSizeInBytes = tlabRefillSizeInWords.multiply(wordSize());
            // allocate new TLAB, address returned in top
            top = edenAllocate(tlabRefillSizeInBytes, log);
            if (top.notEqual(0)) {
                end = top.add(tlabRefillSizeInBytes.subtract(alignmentReserveInBytes));
                initializeTlab(thread, top, end);

                return NewInstanceStub.allocate(thread, sizeInBytes);
            } else {
                return WordFactory.zero();
            }
        } else {
            // Retain TLAB
            Word newRefillWasteLimit = refillWasteLimit.add(tlabRefillWasteIncrement(INJECTED_VMCONFIG));
            thread.writeWord(tlabRefillWasteLimitOffset(INJECTED_VMCONFIG), newRefillWasteLimit, TLAB_REFILL_WASTE_LIMIT_LOCATION);
            if (log) {
                printf("refillTLAB: retaining TLAB - newRefillWasteLimit=%p\n", newRefillWasteLimit.rawValue());
            }

            if (tlabStats(INJECTED_VMCONFIG)) {
                thread.writeInt(tlabSlowAllocationsOffset(INJECTED_VMCONFIG), thread.readInt(tlabSlowAllocationsOffset(INJECTED_VMCONFIG), TLAB_SLOW_ALLOCATIONS_LOCATION) + 1,
                                TLAB_SLOW_ALLOCATIONS_LOCATION);
            }

            return edenAllocate(WordFactory.unsigned(sizeInBytes), log);
        }
    }

    /**
     * Attempts to allocate a chunk of memory from Eden space.
     *
     * @param sizeInBytes the size of the chunk to allocate
     * @param log specifies if logging is enabled
     * @return the allocated chunk or {@link WordFactory#zero()} if allocation fails
     */
    public static Word edenAllocate(Word sizeInBytes, boolean log) {
        final long heapTopRawAddress = GraalHotSpotVMConfigNode.heapTopAddress();
        final long heapEndRawAddress = GraalHotSpotVMConfigNode.heapEndAddress();

        Word heapTopAddress = WordFactory.unsigned(heapTopRawAddress);
        Word heapEndAddress = WordFactory.unsigned(heapEndRawAddress);

        while (true) {
            Word heapTop = heapTopAddress.readWord(0, HEAP_TOP_LOCATION);
            Word newHeapTop = heapTop.add(sizeInBytes);
            if (newHeapTop.belowOrEqual(heapTop)) {
                return WordFactory.zero();
            }

            Word heapEnd = heapEndAddress.readWord(0, HEAP_END_LOCATION);
            if (newHeapTop.aboveThan(heapEnd)) {
                return WordFactory.zero();
            }
            if (heapTopAddress.logicCompareAndSwapWord(0, heapTop, newHeapTop, HEAP_TOP_LOCATION)) {
                return heapTop;
            }
        }
    }

    @Fold
    static boolean forceSlowPath(OptionValues options) {
        return StubOptions.ForceUseOfNewInstanceStub.getValue(options);
    }

    public static final ForeignCallDescriptor NEW_INSTANCE_C = newDescriptor(NewInstanceStub.class, "newInstanceC", void.class, Word.class, KlassPointer.class);

    @NodeIntrinsic(StubForeignCallNode.class)
    public static native void newInstanceC(@ConstantNodeParameter ForeignCallDescriptor newInstanceC, Word thread, KlassPointer hub);
}
