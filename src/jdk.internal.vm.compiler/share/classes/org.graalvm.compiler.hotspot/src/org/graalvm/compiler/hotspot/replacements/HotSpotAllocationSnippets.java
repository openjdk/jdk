/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateRecompile;
import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.MinimalBulkZeroingSize;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_OPTIONVALUES;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_ARRAY_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_INIT_STATE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HUB_WRITE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.PROTOTYPE_MARK_WORD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayKlassOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.instanceKlassStateBeingInitialized;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.isInstanceKlassFullyInitialized;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeMask;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeShift;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadKlassFromObject;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.prototypeMarkWordOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readInstanceKlassInitState;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readInstanceKlassInitThread;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readLayoutHelper;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileAllocations;
import static org.graalvm.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileAllocationsContext;
import static org.graalvm.compiler.nodes.PiArrayNode.piArrayCastToSnippetReplaceeStamp;
import static org.graalvm.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.DEOPT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.ReplacementsUtil.dynamicAssert;
import static org.graalvm.compiler.replacements.ReplacementsUtil.staticAssert;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static org.graalvm.compiler.replacements.nodes.CStringConstant.cstring;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.KlassBeingInitializedCheckNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyFixedNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.DynamicCounterNode;
import org.graalvm.compiler.nodes.debug.VerifyHeapNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.AllocationSnippets;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.UnsignedWord;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotAllocationSnippets extends AllocationSnippets {
    /** New dynamic array stub that throws an {@link OutOfMemoryError} on allocation failure. */
    public static final HotSpotForeignCallDescriptor DYNAMIC_NEW_INSTANCE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "dynamic_new_instance", Object.class, Class.class);
    /** New dynamic array stub that returns null on allocation failure. */
    public static final HotSpotForeignCallDescriptor DYNAMIC_NEW_INSTANCE_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "dynamic_new_instance_or_null",
                    Object.class, Class.class);

    private final GraalHotSpotVMConfig config;
    private final Register threadRegister;

    public HotSpotAllocationSnippets(GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        this.config = config;
        this.threadRegister = registers.getThreadRegister();
    }

    @Snippet
    protected Object allocateInstance(KlassPointer hub,
                    Word prototypeMarkWord,
                    @ConstantParameter long size,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Object result = allocateInstanceImpl(hub.asWord(), prototypeMarkWord, WordFactory.unsigned(size), fillContents, emitMemoryBarrier, true, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(KlassPointer hub,
                    Word prototypeMarkWord,
                    int length,
                    @ConstantParameter int headerSize,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Object result = allocateArrayImpl(hub.asWord(), prototypeMarkWord, length, headerSize, log2ElementSize, fillContents, headerSize, emitMemoryBarrier, maybeUnroll, supportsBulkZeroing,
                        profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object allocateInstancePIC(KlassPointer hub,
                    Word prototypeMarkWord,
                    @ConstantParameter long size,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        // Klass must be initialized by the time the first instance is allocated, therefore we can
        // just load it from the corresponding cell and avoid the resolution check. We have to use a
        // fixed load though, to prevent it from floating above the initialization.
        KlassPointer picHub = LoadConstantIndirectlyFixedNode.loadKlass(hub);
        Object result = allocateInstanceImpl(picHub.asWord(), prototypeMarkWord, WordFactory.unsigned(size), fillContents, emitMemoryBarrier, true, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateInstanceDynamic(Class<?> type,
                    Class<?> classClass,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        if (probability(DEOPT_PROBABILITY, type == null)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        Class<?> nonNullType = PiNode.piCastNonNullClass(type, SnippetAnchorNode.anchor());

        if (probability(DEOPT_PROBABILITY, DynamicNewInstanceNode.throwsInstantiationException(type, classClass))) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }

        KlassPointer hub = ClassGetHubNode.readClass(nonNullType);
        if (probability(FAST_PATH_PROBABILITY, !hub.isNull())) {
            KlassPointer nonNullHub = ClassGetHubNode.piCastNonNull(hub, SnippetAnchorNode.anchor());

            if (probability(VERY_FAST_PATH_PROBABILITY, isInstanceKlassFullyInitialized(nonNullHub))) {
                int layoutHelper = readLayoutHelper(nonNullHub);
                /*
                 * src/share/vm/oops/klass.hpp: For instances, layout helper is a positive number,
                 * the instance size. This size is already passed through align_object_size and
                 * scaled to bytes. The low order bit is set if instances of this class cannot be
                 * allocated using the fastpath.
                 */
                if (probability(FAST_PATH_PROBABILITY, (layoutHelper & 1) == 0)) {
                    Word prototypeMarkWord = nonNullHub.readWord(prototypeMarkWordOffset(INJECTED_VMCONFIG), PROTOTYPE_MARK_WORD_LOCATION);
                    /*
                     * FIXME(je,ds): we should actually pass typeContext instead of "" but late
                     * binding of parameters is not yet supported by the GraphBuilderPlugin system.
                     */
                    UnsignedWord size = WordFactory.unsigned(layoutHelper);
                    return allocateInstanceImpl(nonNullHub.asWord(), prototypeMarkWord, size, fillContents, emitMemoryBarrier, false, profilingData);
                }
            } else {
                DeoptimizeNode.deopt(None, RuntimeConstraint);
            }
        }
        return PiNode.piCastToSnippetReplaceeStamp(dynamicNewInstanceStub(type));
    }

    @Snippet
    public Object allocatePrimitiveArrayPIC(KlassPointer hub,
                    Word prototypeMarkWord,
                    int length,
                    @ConstantParameter int headerSize,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter AllocationProfilingData profilingData) {
        // Primitive array types are eagerly pre-resolved. We can use a floating load.
        KlassPointer picHub = LoadConstantIndirectlyNode.loadKlass(hub);
        return allocateArrayImpl(picHub.asWord(), prototypeMarkWord, length, headerSize, log2ElementSize, fillContents, headerSize, emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, profilingData);
    }

    @Snippet
    public Object allocateArrayPIC(KlassPointer hub,
                    Word prototypeMarkWord,
                    int length,
                    @ConstantParameter int headerSize,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter AllocationProfilingData profilingData) {
        // Array type would be resolved by dominating resolution.
        KlassPointer picHub = LoadConstantIndirectlyFixedNode.loadKlass(hub);
        return allocateArrayImpl(picHub.asWord(), prototypeMarkWord, length, headerSize, log2ElementSize, fillContents, headerSize, emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, profilingData);
    }

    @Snippet
    public Object allocateArrayDynamic(Class<?> elementType,
                    Word prototypeMarkWord,
                    Class<?> voidClass,
                    int length,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter JavaKind knownElementKind,
                    @ConstantParameter int knownLayoutHelper,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter AllocationProfilingData profilingData) {
        /*
         * We only need the dynamic check for void when we have no static information from
         * knownElementKind.
         */
        staticAssert(knownElementKind != JavaKind.Void, "unsupported knownElementKind");
        if (knownElementKind == JavaKind.Illegal && probability(SLOW_PATH_PROBABILITY, elementType == null || DynamicNewArrayNode.throwsIllegalArgumentException(elementType, voidClass))) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }

        KlassPointer klass = loadKlassFromObject(elementType, arrayKlassOffset(INJECTED_VMCONFIG), CLASS_ARRAY_KLASS_LOCATION);
        if (probability(DEOPT_PROBABILITY, klass.isNull())) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        KlassPointer nonNullKlass = ClassGetHubNode.piCastNonNull(klass, SnippetAnchorNode.anchor());

        if (probability(DEOPT_PROBABILITY, length < 0)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        int layoutHelper;
        if (knownElementKind == JavaKind.Illegal) {
            layoutHelper = readLayoutHelper(nonNullKlass);
        } else {
            dynamicAssert(knownLayoutHelper == readLayoutHelper(nonNullKlass), "layout mismatch");
            layoutHelper = knownLayoutHelper;
        }
        //@formatter:off
        // from src/share/vm/oops/klass.hpp:
        //
        // For arrays, layout helper is a negative number, containing four
        // distinct bytes, as follows:
        //    MSB:[tag, hsz, ebt, log2(esz)]:LSB
        // where:
        //    tag is 0x80 if the elements are oops, 0xC0 if non-oops
        //    hsz is array header size in bytes (i.e., offset of first element)
        //    ebt is the BasicType of the elements
        //    esz is the element size in bytes
        //@formatter:on

        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift(INJECTED_VMCONFIG)) & layoutHelperHeaderSizeMask(INJECTED_VMCONFIG);
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift(INJECTED_VMCONFIG)) & layoutHelperLog2ElementSizeMask(INJECTED_VMCONFIG);

        Object result = allocateArrayImpl(nonNullKlass.asWord(), prototypeMarkWord, length, headerSize, log2ElementSize, fillContents, headerSize, emitMemoryBarrier, false, supportsBulkZeroing,
                        profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object newmultiarray(KlassPointer hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions) {
        return newMultiArrayImpl(hub.asWord(), rank, dimensions);
    }

    @Snippet
    private Object newmultiarrayPIC(KlassPointer hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions) {
        // Array type would be resolved by dominating resolution.
        KlassPointer picHub = LoadConstantIndirectlyFixedNode.loadKlass(hub);
        return newMultiArrayImpl(picHub.asWord(), rank, dimensions);
    }

    @Snippet
    private void verifyHeap() {
        Word tlabInfo = getTLABInfo();
        Word topValue = readTlabTop(tlabInfo);
        if (!topValue.equal(WordFactory.zero())) {
            Word topValueContents = topValue.readWord(0, MARK_WORD_LOCATION);
            if (topValueContents.equal(WordFactory.zero())) {
                AssertionSnippets.vmMessageC(AssertionSnippets.ASSERTION_VM_MESSAGE_C, true, cstring("overzeroing of TLAB detected"), 0L, 0L, 0L);
            }
        }
    }

    @Snippet
    private void threadBeingInitializedCheck(KlassPointer klass) {
        int state = readInstanceKlassInitState(klass);
        if (state != instanceKlassStateBeingInitialized(INJECTED_VMCONFIG)) {
            // The klass is no longer being initialized so force recompilation
            DeoptimizeNode.deopt(InvalidateRecompile, RuntimeConstraint);
        } else if (getThread() != readInstanceKlassInitThread(klass)) {
            // The klass is being initialized but this isn't the initializing thread so
            // so deopt and allow execution to resume in the interpreter where it should block.
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
    }

    @Override
    protected final int getPrefetchStyle() {
        return HotSpotReplacementsUtil.allocatePrefetchStyle(INJECTED_VMCONFIG);
    }

    @Override
    protected final int getPrefetchLines(boolean isArray) {
        if (isArray) {
            return HotSpotReplacementsUtil.allocatePrefetchLines(INJECTED_VMCONFIG);
        } else {
            return HotSpotReplacementsUtil.allocateInstancePrefetchLines(INJECTED_VMCONFIG);
        }
    }

    @Override
    protected final int getPrefetchStepSize() {
        return HotSpotReplacementsUtil.allocatePrefetchStepSize(INJECTED_VMCONFIG);
    }

    @Override
    protected final int getPrefetchDistance() {
        return HotSpotReplacementsUtil.allocatePrefetchDistance(INJECTED_VMCONFIG);
    }

    @Override
    protected final Object callNewInstanceStub(Word hub) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        if (useNullAllocationStubs(INJECTED_VMCONFIG)) {
            return nonNullOrDeopt(newInstanceOrNull(NEW_INSTANCE_OR_NULL, klassPtr));
        } else {
            return newInstance(NEW_INSTANCE, klassPtr);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = true)
    private static native Object newInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub);

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = false)
    private static native Object newInstanceOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub);

    /**
     * When allocating on the slow path, determines whether to use a version of the runtime call
     * that returns {@code null} on a failed allocation instead of raising an OutOfMemoryError.
     */
    @Fold
    static boolean useNullAllocationStubs(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.areNullAllocationStubsAvailable();
    }

    @Override
    protected final Object callNewArrayStub(Word hub, int length) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        if (useNullAllocationStubs(INJECTED_VMCONFIG)) {
            return nonNullOrDeopt(newArrayOrNull(NEW_ARRAY_OR_NULL, klassPtr, length));
        } else {
            return newArray(NEW_ARRAY, klassPtr, length);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = true)
    private static native Object newArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int length);

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = false)
    private static native Object newArrayOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int length);

    /**
     * Deoptimizes if {@code obj == null} otherwise returns {@code obj}.
     */
    private static Object nonNullOrDeopt(Object obj) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.DEOPT_PROBABILITY, obj == null)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        return obj;
    }

    public static Object dynamicNewInstanceStub(Class<?> elementType) {
        if (useNullAllocationStubs(INJECTED_VMCONFIG)) {
            return nonNullOrDeopt(dynamicNewInstanceOrNull(DYNAMIC_NEW_INSTANCE_OR_NULL, elementType));
        } else {
            return dynamicNewInstance(DYNAMIC_NEW_INSTANCE, elementType);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = true)
    public static native Object dynamicNewInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = false)
    public static native Object dynamicNewInstanceOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    @Override
    protected final Object callNewMultiArrayStub(Word hub, int rank, Word dims) {
        KlassPointer klassPointer = KlassPointer.fromWord(hub);
        if (useNullAllocationStubs(INJECTED_VMCONFIG)) {
            return nonNullOrDeopt(newMultiArrayOrNull(NEW_MULTI_ARRAY_OR_NULL, klassPointer, rank, dims));
        } else {
            return newMultiArray(NEW_MULTI_ARRAY, klassPointer, rank, dims);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = true)
    private static native Object newMultiArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int rank, Word dims);

    @NodeIntrinsic(value = ForeignCallNode.class, injectedStampIsNonNull = false)
    private static native Object newMultiArrayOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int rank, Word dims);

    @Fold
    static int getMinimalBulkZeroingSize(@InjectedParameter OptionValues optionValues) {
        return MinimalBulkZeroingSize.getValue(optionValues);
    }

    @Override
    protected final int getMinimalBulkZeroingSize() {
        return getMinimalBulkZeroingSize(INJECTED_OPTIONVALUES);
    }

    @Override
    protected final void initializeObjectHeader(Word memory, Word hub, Word prototypeMarkWord, boolean isArray) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        Word markWord = prototypeMarkWord;
        if (!isArray && HotSpotReplacementsUtil.useBiasedLocking(INJECTED_VMCONFIG)) {
            markWord = klassPtr.readWord(prototypeMarkWordOffset(INJECTED_VMCONFIG), PROTOTYPE_MARK_WORD_LOCATION);
        }

        HotSpotReplacementsUtil.initializeObjectHeader(memory, markWord, klassPtr);
    }

    @Override
    protected final int instanceHeaderSize() {
        return HotSpotReplacementsUtil.instanceHeaderSize(INJECTED_VMCONFIG);
    }

    @Override
    protected final int arrayLengthOffset() {
        return HotSpotReplacementsUtil.arrayLengthOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected final int objectAlignment() {
        return HotSpotReplacementsUtil.objectAlignment(INJECTED_VMCONFIG);
    }

    @Override
    protected final boolean useTLAB() {
        return HotSpotReplacementsUtil.useTLAB(INJECTED_VMCONFIG);
    }

    @Override
    protected final boolean shouldAllocateInTLAB(UnsignedWord allocationSize, boolean isArray) {
        if (HotSpotReplacementsUtil.useG1GC(INJECTED_VMCONFIG)) {
            // The TLAB is sized in a way that humongous objects are never allocated in the TLAB.
            // So, whatever fits into the TLAB can be allocated there.
            return true;
        } else {
            return !isArray || allocationSize.belowThan(16 * 1024 * 1024);
        }
    }

    @Override
    protected final Word getTLABInfo() {
        return getThread();
    }

    private Word getThread() {
        return registerAsWord(threadRegister);
    }

    @Override
    protected final Word readTlabEnd(Word thread) {
        return HotSpotReplacementsUtil.readTlabEnd(thread);
    }

    @Override
    protected final Word readTlabTop(Word thread) {
        return HotSpotReplacementsUtil.readTlabTop(thread);
    }

    @Override
    protected final void writeTlabTop(Word thread, Word newTop) {
        HotSpotReplacementsUtil.writeTlabTop(thread, newTop);
    }

    @Override
    protected final Object verifyOop(Object obj) {
        return HotSpotReplacementsUtil.verifyOop(obj);
    }

    @Override
    protected final void profileAllocation(AllocationProfilingData profilingData, UnsignedWord size) {
        if (doProfile(INJECTED_OPTIONVALUES)) {
            String name = createName(INJECTED_OPTIONVALUES, profilingData);

            boolean context = withContext(INJECTED_OPTIONVALUES);
            DynamicCounterNode.counter("number of bytes allocated", name, size.rawValue(), context);
            DynamicCounterNode.counter("number of allocations", name, 1, context);
        }
    }

    @Fold
    static boolean doProfile(@Fold.InjectedParameter OptionValues options) {
        return ProfileAllocations.getValue(options);
    }

    enum ProfileContext {
        AllocatingMethod,
        InstanceOrArray,
        AllocatedType,
        AllocatedTypesInMethod,
        Total
    }

    @Fold
    static String createName(@Fold.InjectedParameter OptionValues options, AllocationProfilingData profilingData) {
        HotSpotAllocationProfilingData hotspotAllocationProfilingData = (HotSpotAllocationProfilingData) profilingData;
        switch (ProfileAllocationsContext.getValue(options)) {
            case AllocatingMethod:
                return "";
            case InstanceOrArray:
                return hotspotAllocationProfilingData.path;
            case AllocatedType:
            case AllocatedTypesInMethod:
                return hotspotAllocationProfilingData.typeContext;
            case Total:
                return "bytes";
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Fold
    static boolean withContext(@Fold.InjectedParameter OptionValues options) {
        ProfileContext context = ProfileAllocationsContext.getValue(options);
        return context == ProfileContext.AllocatingMethod || context == ProfileContext.AllocatedTypesInMethod;
    }

    static HotSpotResolvedObjectType lookupArrayClass(MetaAccessProvider metaAccessProvider, JavaKind kind) {
        return (HotSpotResolvedObjectType) metaAccessProvider.lookupJavaType(kind == JavaKind.Object ? Object.class : kind.toJavaClass()).getArrayClass();
    }

    public static class Templates extends AbstractTemplates {
        private final GraalHotSpotVMConfig config;
        private final AllocationSnippetCounters snippetCounters;
        private HotSpotAllocationProfilingData profilingData;

        private final SnippetInfo allocateInstance;
        private final SnippetInfo allocateInstancePIC;
        private final SnippetInfo allocateArray;
        private final SnippetInfo allocateArrayPIC;
        private final SnippetInfo allocatePrimitiveArrayPIC;
        private final SnippetInfo allocateArrayDynamic;
        private final SnippetInfo allocateInstanceDynamic;
        private final SnippetInfo newmultiarray;
        private final SnippetInfo newmultiarrayPIC;
        private final SnippetInfo verifyHeap;
        private final SnippetInfo threadBeingInitializedCheck;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, SnippetCounter.Group.Factory groupFactory, HotSpotProviders providers, TargetDescription target,
                        GraalHotSpotVMConfig config) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            this.config = config;
            snippetCounters = new AllocationSnippetCounters(groupFactory);

            HotSpotAllocationSnippets receiver = new HotSpotAllocationSnippets(config, providers.getRegisters());

            allocateInstance = snippet(HotSpotAllocationSnippets.class, "allocateInstance", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION,
                            PROTOTYPE_MARK_WORD_LOCATION);
            allocateArray = snippet(HotSpotAllocationSnippets.class, "allocateArray", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            allocateInstancePIC = snippet(HotSpotAllocationSnippets.class, "allocateInstancePIC", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION,
                            PROTOTYPE_MARK_WORD_LOCATION);
            allocateArrayPIC = snippet(HotSpotAllocationSnippets.class, "allocateArrayPIC", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            allocatePrimitiveArrayPIC = snippet(HotSpotAllocationSnippets.class, "allocatePrimitiveArrayPIC", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION);
            allocateArrayDynamic = snippet(HotSpotAllocationSnippets.class, "allocateArrayDynamic", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION);
            allocateInstanceDynamic = snippet(HotSpotAllocationSnippets.class, "allocateInstanceDynamic", null, receiver, MARK_WORD_LOCATION, HUB_WRITE_LOCATION, TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION, PROTOTYPE_MARK_WORD_LOCATION, CLASS_INIT_STATE_LOCATION);
            newmultiarray = snippet(HotSpotAllocationSnippets.class, "newmultiarray", null, receiver, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            newmultiarrayPIC = snippet(HotSpotAllocationSnippets.class, "newmultiarrayPIC", null, receiver, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            verifyHeap = snippet(HotSpotAllocationSnippets.class, "verifyHeap", null, receiver);
            threadBeingInitializedCheck = snippet(HotSpotAllocationSnippets.class, "threadBeingInitializedCheck", null, receiver);
        }

        private AllocationProfilingData getProfilingData(OptionValues localOptions, String path, ResolvedJavaType type) {
            if (ProfileAllocations.getValue(localOptions)) {
                // Create one object per snippet instantiation - this kills the snippet caching as
                // we need to add the object as a constant to the snippet.
                String typeContext = type == null ? null : type.toJavaName(false);
                return new HotSpotAllocationProfilingData(snippetCounters, path, typeContext);
            } else if (profilingData == null) {
                profilingData = new HotSpotAllocationProfilingData(snippetCounters, null, null);
            }

            return profilingData;
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        public void lower(NewInstanceNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.instanceClass();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), providers.getMetaAccess(), graph);
            long size = instanceSize(type);

            OptionValues localOptions = graph.getOptions();
            SnippetInfo snippet = GeneratePIC.getValue(localOptions) ? allocateInstancePIC : allocateInstance;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            args.add("prototypeMarkWord", type.prototypeMarkWord());
            args.addConst("size", size);
            args.addConst("fillContents", node.fillContents());
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("profilingData", getProfilingData(localOptions, "instance", type));

            SnippetTemplate template = template(node, args);
            graph.getDebug().log("Lowering allocateInstance in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        /**
         * Lowers a {@link NewArrayNode}.
         */
        public void lower(NewArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ResolvedJavaType elementType = node.elementType();
            HotSpotResolvedObjectType arrayType = (HotSpotResolvedObjectType) elementType.getArrayClass();
            JavaKind elementKind = elementType.getJavaKind();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), arrayType.klass(), providers.getMetaAccess(), graph);
            final int headerSize = tool.getMetaAccess().getArrayBaseOffset(elementKind);
            int log2ElementSize = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(elementKind));

            OptionValues localOptions = graph.getOptions();
            SnippetInfo snippet;
            if (GeneratePIC.getValue(localOptions)) {
                if (elementType.isPrimitive()) {
                    snippet = allocatePrimitiveArrayPIC;
                } else {
                    snippet = allocateArrayPIC;
                }
            } else {
                snippet = allocateArray;
            }

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            assert arrayType.prototypeMarkWord() == lookupArrayClass(tool, JavaKind.Object).prototypeMarkWord() : "all array types are assumed to have the same prototypeMarkWord";
            args.add("prototypeMarkWord", arrayType.prototypeMarkWord());
            ValueNode length = node.length();
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("headerSize", headerSize);
            args.addConst("log2ElementSize", log2ElementSize);
            args.addConst("fillContents", node.fillContents());
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("maybeUnroll", length.isConstant());
            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("profilingData", getProfilingData(localOptions, "array", arrayType));

            SnippetTemplate template = template(node, args);
            graph.getDebug().log("Lowering allocateArray in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(NewMultiArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            OptionValues localOptions = graph.getOptions();
            int rank = node.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < node.dimensionCount(); i++) {
                dims[i] = node.dimension(i);
            }
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.type();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), providers.getMetaAccess(), graph);

            SnippetInfo snippet = GeneratePIC.getValue(localOptions) ? newmultiarrayPIC : newmultiarray;
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            args.addConst("rank", rank);
            args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

            template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
            OptionValues localOptions = node.graph().getOptions();
            ValueNode classClass = node.getClassClass();
            assert classClass != null;

            Arguments args = new Arguments(allocateInstanceDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getInstanceType());
            args.add("classClass", classClass);
            args.addConst("fillContents", node.fillContents());
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("profilingData", getProfilingData(localOptions, "", null));

            template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            OptionValues localOptions = graph.getOptions();
            ValueNode length = node.length();
            ValueNode voidClass = node.getVoidClass();
            assert voidClass != null;

            Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("elementType", node.getElementType());
            args.add("prototypeMarkWord", lookupArrayClass(tool, JavaKind.Object).prototypeMarkWord());
            args.add("voidClass", voidClass);
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("fillContents", node.fillContents());
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            /*
             * We use Kind.Illegal as a marker value instead of null because constant snippet
             * parameters cannot be null.
             */
            args.addConst("knownElementKind", node.getKnownElementKind() == null ? JavaKind.Illegal : node.getKnownElementKind());
            if (node.getKnownElementKind() != null) {
                args.addConst("knownLayoutHelper", lookupArrayClass(tool, node.getKnownElementKind()).layoutHelper());
            } else {
                args.addConst("knownLayoutHelper", 0);
            }
            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("profilingData", getProfilingData(localOptions, "dynamic type", null));

            template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(VerifyHeapNode node, LoweringTool tool) {
            if (config.cAssertions) {
                Arguments args = new Arguments(verifyHeap, node.graph().getGuardsStage(), tool.getLoweringStage());

                template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
            } else {
                GraphUtil.removeFixedWithUnusedInputs(node);
            }
        }

        public void lower(KlassBeingInitializedCheckNode node, LoweringTool tool) {
            Arguments args = new Arguments(threadBeingInitializedCheck, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("klass", node.getKlass());

            template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        private static HotSpotResolvedObjectType lookupArrayClass(LoweringTool tool, JavaKind kind) {
            return HotSpotAllocationSnippets.lookupArrayClass(tool.getMetaAccess(), kind);
        }

        private static long instanceSize(HotSpotResolvedObjectType type) {
            long size = type.instanceSize();
            assert size >= 0;
            return size;
        }
    }

    private static class HotSpotAllocationProfilingData extends AllocationProfilingData {
        String path;
        String typeContext;

        HotSpotAllocationProfilingData(AllocationSnippetCounters snippetCounters, String path, String typeContext) {
            super(snippetCounters);
            this.path = path;
            this.typeContext = typeContext;
        }
    }
}
