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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.VERIFY_OOP;
import static org.graalvm.compiler.hotspot.replacements.UnsafeAccess.UNSAFE;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.CompressionNode;
import org.graalvm.compiler.hotspot.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.hotspot.nodes.SnippetAnchorNode;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.CanonicalizableLocation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.StoreHubNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.nodes.memory.Access;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.replacements.nodes.WriteRegisterNode;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

//JaCoCo Exclude

/**
 * A collection of methods used in HotSpot snippets, substitutions and stubs.
 */
public class HotSpotReplacementsUtil {

    abstract static class HotSpotOptimizingLocationIdentity extends NamedLocationIdentity implements CanonicalizableLocation {

        HotSpotOptimizingLocationIdentity(String name) {
            super(name, true);
        }

        @Override
        public abstract ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool);

        protected ValueNode findReadHub(ValueNode object) {
            ValueNode base = object;
            if (base instanceof CompressionNode) {
                base = ((CompressionNode) base).getValue();
            }
            if (base instanceof Access) {
                Access access = (Access) base;
                if (access.getLocationIdentity().equals(HUB_LOCATION) || access.getLocationIdentity().equals(COMPRESSED_HUB_LOCATION)) {
                    AddressNode address = access.getAddress();
                    if (address instanceof OffsetAddressNode) {
                        OffsetAddressNode offset = (OffsetAddressNode) address;
                        return offset.getBase();
                    }
                }
            } else if (base instanceof LoadHubNode) {
                LoadHubNode loadhub = (LoadHubNode) base;
                return loadhub.getValue();
            }
            return null;
        }

        /**
         * Fold reads that convert from Class -> Hub -> Class or vice versa.
         *
         * @param read
         * @param object
         * @param otherLocation
         * @return an earlier read or the original {@code read}
         */
        protected static ValueNode foldIndirection(ValueNode read, ValueNode object, LocationIdentity otherLocation) {
            if (object instanceof Access) {
                Access access = (Access) object;
                if (access.getLocationIdentity().equals(otherLocation)) {
                    AddressNode address = access.getAddress();
                    if (address instanceof OffsetAddressNode) {
                        OffsetAddressNode offset = (OffsetAddressNode) address;
                        assert offset.getBase().stamp().isCompatible(read.stamp());
                        return offset.getBase();
                    }
                }
            }
            return read;
        }
    }

    public static HotSpotJVMCIRuntimeProvider runtime() {
        return HotSpotJVMCIRuntime.runtime();
    }

    @Fold
    public static GraalHotSpotVMConfig config(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config != null;
        return config;
    }

    @Fold
    public static boolean useTLAB(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useTLAB;
    }

    @Fold
    public static boolean verifyOops(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOops;
    }

    public static final LocationIdentity EXCEPTION_OOP_LOCATION = NamedLocationIdentity.mutable("ExceptionOop");

    /**
     * @see GraalHotSpotVMConfig#threadExceptionOopOffset
     */
    @Fold
    public static int threadExceptionOopOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadExceptionOopOffset;
    }

    public static final LocationIdentity EXCEPTION_PC_LOCATION = NamedLocationIdentity.mutable("ExceptionPc");

    @Fold
    public static int threadExceptionPcOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadExceptionPcOffset;
    }

    public static final LocationIdentity TLAB_TOP_LOCATION = NamedLocationIdentity.mutable("TlabTop");

    @Fold
    public static int threadTlabTopOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadTlabTopOffset();
    }

    public static final LocationIdentity TLAB_END_LOCATION = NamedLocationIdentity.mutable("TlabEnd");

    @Fold
    static int threadTlabEndOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadTlabEndOffset();
    }

    public static final LocationIdentity TLAB_START_LOCATION = NamedLocationIdentity.mutable("TlabStart");

    @Fold
    static int threadTlabStartOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadTlabStartOffset();
    }

    public static final LocationIdentity PENDING_EXCEPTION_LOCATION = NamedLocationIdentity.mutable("PendingException");

    /**
     * @see GraalHotSpotVMConfig#pendingExceptionOffset
     */
    @Fold
    static int threadPendingExceptionOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.pendingExceptionOffset;
    }

    public static final LocationIdentity PENDING_DEOPTIMIZATION_LOCATION = NamedLocationIdentity.mutable("PendingDeoptimization");

    /**
     * @see GraalHotSpotVMConfig#pendingDeoptimizationOffset
     */
    @Fold
    static int threadPendingDeoptimizationOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.pendingDeoptimizationOffset;
    }

    public static final LocationIdentity OBJECT_RESULT_LOCATION = NamedLocationIdentity.mutable("ObjectResult");

    @Fold
    static int objectResultOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadObjectResultOffset;
    }

    /**
     * @see GraalHotSpotVMConfig#threadExceptionOopOffset
     */
    public static Object readExceptionOop(Word thread) {
        return thread.readObject(threadExceptionOopOffset(INJECTED_VMCONFIG), EXCEPTION_OOP_LOCATION);
    }

    public static Word readExceptionPc(Word thread) {
        return thread.readWord(threadExceptionPcOffset(INJECTED_VMCONFIG), EXCEPTION_PC_LOCATION);
    }

    /**
     * @see GraalHotSpotVMConfig#threadExceptionOopOffset
     */
    public static void writeExceptionOop(Word thread, Object value) {
        thread.writeObject(threadExceptionOopOffset(INJECTED_VMCONFIG), value, EXCEPTION_OOP_LOCATION);
    }

    public static void writeExceptionPc(Word thread, Word value) {
        thread.writeWord(threadExceptionPcOffset(INJECTED_VMCONFIG), value, EXCEPTION_PC_LOCATION);
    }

    public static Word readTlabTop(Word thread) {
        return thread.readWord(threadTlabTopOffset(INJECTED_VMCONFIG), TLAB_TOP_LOCATION);
    }

    public static Word readTlabEnd(Word thread) {
        return thread.readWord(threadTlabEndOffset(INJECTED_VMCONFIG), TLAB_END_LOCATION);
    }

    public static Word readTlabStart(Word thread) {
        return thread.readWord(threadTlabStartOffset(INJECTED_VMCONFIG), TLAB_START_LOCATION);
    }

    public static void writeTlabTop(Word thread, Word top) {
        thread.writeWord(threadTlabTopOffset(INJECTED_VMCONFIG), top, TLAB_TOP_LOCATION);
    }

    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF_NONVIRTUAL", justification = "foldable method parameters are injected")
    public static void initializeTlab(Word thread, Word start, Word end) {
        thread.writeWord(threadTlabStartOffset(INJECTED_VMCONFIG), start, TLAB_START_LOCATION);
        thread.writeWord(threadTlabTopOffset(INJECTED_VMCONFIG), start, TLAB_TOP_LOCATION);
        thread.writeWord(threadTlabEndOffset(INJECTED_VMCONFIG), end, TLAB_END_LOCATION);
    }

    /**
     * Clears the pending exception for the given thread.
     *
     * @return the pending exception, or null if there was none
     */
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF_NONVIRTUAL", justification = "foldable method parameters are injected")
    public static Object clearPendingException(Word thread) {
        Object result = thread.readObject(threadPendingExceptionOffset(INJECTED_VMCONFIG), PENDING_EXCEPTION_LOCATION);
        thread.writeObject(threadPendingExceptionOffset(INJECTED_VMCONFIG), null, PENDING_EXCEPTION_LOCATION);
        return result;
    }

    /**
     * Reads the pending deoptimization value for the given thread.
     *
     * @return {@code true} if there was a pending deoptimization
     */
    public static int readPendingDeoptimization(Word thread) {
        return thread.readInt(threadPendingDeoptimizationOffset(INJECTED_VMCONFIG), PENDING_DEOPTIMIZATION_LOCATION);
    }

    /**
     * Writes the pending deoptimization value for the given thread.
     */
    public static void writePendingDeoptimization(Word thread, int value) {
        thread.writeInt(threadPendingDeoptimizationOffset(INJECTED_VMCONFIG), value, PENDING_DEOPTIMIZATION_LOCATION);
    }

    /**
     * Gets and clears the object result from a runtime call stored in a thread local.
     *
     * @return the object that was in the thread local
     */
    public static Object getAndClearObjectResult(Word thread) {
        Object result = thread.readObject(objectResultOffset(INJECTED_VMCONFIG), OBJECT_RESULT_LOCATION);
        thread.writeObject(objectResultOffset(INJECTED_VMCONFIG), null, OBJECT_RESULT_LOCATION);
        return result;
    }

    public static final LocationIdentity JAVA_THREAD_THREAD_OBJECT_LOCATION = NamedLocationIdentity.mutable("JavaThread::_threadObj");

    @Fold
    public static int threadObjectOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadObjectOffset;
    }

    public static final LocationIdentity JAVA_THREAD_OSTHREAD_LOCATION = NamedLocationIdentity.mutable("JavaThread::_osthread");

    @Fold
    public static int osThreadOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.osThreadOffset;
    }

    @Fold
    public static int osThreadInterruptedOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.osThreadInterruptedOffset;
    }

    @Fold
    public static JavaKind getWordKind() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordJavaKind;
    }

    @Fold
    public static int wordSize() {
        return runtime().getHostJVMCIBackend().getCodeCache().getTarget().wordSize;
    }

    @Fold
    public static int pageSize() {
        return UNSAFE.pageSize();
    }

    @Fold
    public static int heapWordSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.heapWordSize;
    }

    public static final LocationIdentity PROTOTYPE_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("PrototypeMarkWord");

    @Fold
    public static int prototypeMarkWordOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.prototypeMarkWordOffset;
    }

    @Fold
    public static long arrayPrototypeMarkWord(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.arrayPrototypeMarkWord();
    }

    public static final LocationIdentity KLASS_ACCESS_FLAGS_LOCATION = NamedLocationIdentity.immutable("Klass::_access_flags");

    @Fold
    public static int klassAccessFlagsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassAccessFlagsOffset;
    }

    @Fold
    public static int jvmAccWrittenFlags(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.jvmAccWrittenFlags;
    }

    public static final LocationIdentity KLASS_LAYOUT_HELPER_LOCATION = new HotSpotOptimizingLocationIdentity("Klass::_layout_helper") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            ValueNode javaObject = findReadHub(object);
            if (javaObject != null) {
                if (javaObject.stamp() instanceof ObjectStamp) {
                    ObjectStamp stamp = (ObjectStamp) javaObject.stamp();
                    HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) stamp.javaType(tool.getMetaAccess());
                    if (type.isArray() && !type.getComponentType().isPrimitive()) {
                        int layout = type.layoutHelper();
                        return ConstantNode.forInt(layout);
                    }
                }
            }
            return read;
        }
    };

    @Fold
    public static int klassLayoutHelperOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassLayoutHelperOffset;
    }

    public static int readLayoutHelper(KlassPointer hub) {
        // return hub.readInt(klassLayoutHelperOffset(), KLASS_LAYOUT_HELPER_LOCATION);
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        return loadKlassLayoutHelperIntrinsic(hub, anchorNode);
    }

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int loadKlassLayoutHelperIntrinsic(KlassPointer object, GuardingNode anchor);

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int loadKlassLayoutHelperIntrinsic(KlassPointer object);

    /**
     * Checks if class {@code klass} is an array.
     *
     * See: Klass::layout_helper_is_array
     *
     * @param klass the class to be checked
     * @return true if klass is an array, false otherwise
     */
    public static boolean klassIsArray(KlassPointer klass) {
        /*
         * The less-than check only works if both values are ints. We use local variables to make
         * sure these are still ints and haven't changed.
         */
        final int layoutHelper = readLayoutHelper(klass);
        final int layoutHelperNeutralValue = config(INJECTED_VMCONFIG).klassLayoutHelperNeutralValue;
        return (layoutHelper < layoutHelperNeutralValue);
    }

    public static final LocationIdentity ARRAY_KLASS_COMPONENT_MIRROR = NamedLocationIdentity.immutable("ArrayKlass::_component_mirror");

    @Fold
    public static int arrayKlassComponentMirrorOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop");
    }

    public static final LocationIdentity KLASS_SUPER_KLASS_LOCATION = NamedLocationIdentity.immutable("Klass::_super");

    @Fold
    public static int klassSuperKlassOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassSuperKlassOffset;
    }

    public static final LocationIdentity MARK_WORD_LOCATION = NamedLocationIdentity.mutable("MarkWord");

    @Fold
    public static int markOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.markOffset;
    }

    public static final LocationIdentity HUB_WRITE_LOCATION = NamedLocationIdentity.mutable("Hub:write");

    public static final LocationIdentity HUB_LOCATION = new HotSpotOptimizingLocationIdentity("Hub") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            TypeReference constantType = StampTool.typeReferenceOrNull(object);
            if (constantType != null && constantType.isExact()) {
                return ConstantNode.forConstant(read.stamp(), tool.getConstantReflection().asObjectHub(constantType.getType()), tool.getMetaAccess());
            }
            return read;
        }
    };

    public static final LocationIdentity COMPRESSED_HUB_LOCATION = new HotSpotOptimizingLocationIdentity("CompressedHub") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            TypeReference constantType = StampTool.typeReferenceOrNull(object);
            if (constantType != null && constantType.isExact()) {
                return ConstantNode.forConstant(read.stamp(), ((HotSpotMetaspaceConstant) tool.getConstantReflection().asObjectHub(constantType.getType())).compress(), tool.getMetaAccess());
            }
            return read;
        }
    };

    @Fold
    static int hubOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.hubOffset;
    }

    public static void initializeObjectHeader(Word memory, Word markWord, KlassPointer hub) {
        memory.writeWord(markOffset(INJECTED_VMCONFIG), markWord, MARK_WORD_LOCATION);
        StoreHubNode.write(memory, hub);
    }

    @Fold
    public static int unlockedMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.unlockedMask;
    }

    /**
     * Mask for a biasable, locked or unlocked mark word.
     *
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|1|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockMaskInPlace(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.biasedLockMaskInPlace;
    }

    @Fold
    public static int epochMaskInPlace(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.epochMaskInPlace;
    }

    /**
     * Pattern for a biasable, unlocked mark word.
     *
     * <pre>
     * +----------------------------------+-+-+
     * |                                 1|0|1|
     * +----------------------------------+-+-+
     * </pre>
     *
     */
    @Fold
    public static int biasedLockPattern(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.biasedLockPattern;
    }

    @Fold
    public static int ageMaskInPlace(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.ageMaskInPlace;
    }

    @Fold
    public static int metaspaceArrayLengthOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.metaspaceArrayLengthOffset;
    }

    @Fold
    public static int metaspaceArrayBaseOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.metaspaceArrayBaseOffset;
    }

    @Fold
    public static int arrayLengthOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.arrayOopDescLengthOffset();
    }

    @Fold
    public static int arrayBaseOffset(JavaKind elementKind) {
        return getArrayBaseOffset(elementKind);
    }

    @Fold
    public static int arrayIndexScale(JavaKind elementKind) {
        return getArrayIndexScale(elementKind);
    }

    public static Word arrayStart(int[] a) {
        return Word.unsigned(ComputeObjectAddressNode.get(a, getArrayBaseOffset(JavaKind.Int)));
    }

    @Fold
    public static int instanceHeaderSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useCompressedClassPointers ? (2 * wordSize()) - 4 : 2 * wordSize();
    }

    @Fold
    public static byte dirtyCardValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.dirtyCardValue;
    }

    @Fold
    public static byte g1YoungCardValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1YoungCardValue;
    }

    @Fold
    public static int cardTableShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cardtableShift;
    }

    @Fold
    public static long cardTableStart(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.cardtableStartAddress;
    }

    @Fold
    public static int g1CardQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueIndexOffset();
    }

    @Fold
    public static int g1CardQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueBufferOffset();
    }

    @Fold
    public static int logOfHeapRegionGrainBytes(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.logOfHRGrainBytes;
    }

    @Fold
    public static int g1SATBQueueMarkingOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueMarkingOffset();
    }

    @Fold
    public static int g1SATBQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueIndexOffset();
    }

    @Fold
    public static int g1SATBQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueBufferOffset();
    }

    public static final LocationIdentity KLASS_SUPER_CHECK_OFFSET_LOCATION = NamedLocationIdentity.immutable("Klass::_super_check_offset");

    @Fold
    public static int superCheckOffsetOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.superCheckOffsetOffset;
    }

    public static final LocationIdentity SECONDARY_SUPER_CACHE_LOCATION = NamedLocationIdentity.mutable("SecondarySuperCache");

    @Fold
    public static int secondarySuperCacheOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.secondarySuperCacheOffset;
    }

    public static final LocationIdentity SECONDARY_SUPERS_LOCATION = NamedLocationIdentity.immutable("SecondarySupers");

    @Fold
    public static int secondarySupersOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.secondarySupersOffset;
    }

    public static final LocationIdentity DISPLACED_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("DisplacedMarkWord");

    @Fold
    public static int lockDisplacedMarkOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.basicLockDisplacedHeaderOffset;
    }

    @Fold
    public static boolean useBiasedLocking(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useBiasedLocking;
    }

    @Fold
    public static boolean useDeferredInitBarriers(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useDeferredInitBarriers;
    }

    @Fold
    public static boolean useG1GC(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useG1GC;
    }

    @Fold
    public static boolean useCompressedOops(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useCompressedOops;
    }

    @Fold
    static int uninitializedIdentityHashCodeValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.uninitializedIdentityHashCodeValue;
    }

    @Fold
    static int identityHashCodeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.identityHashCodeShift;
    }

    /**
     * Loads the hub of an object (without null checking it first).
     */
    public static KlassPointer loadHub(Object object) {
        return loadHubIntrinsic(object);
    }

    public static Object verifyOop(Object object) {
        if (verifyOops(INJECTED_VMCONFIG)) {
            verifyOopStub(VERIFY_OOP, object);
        }
        return object;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native Object verifyOopStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    public static Word loadWordFromObject(Object object, int offset) {
        ReplacementsUtil.staticAssert(offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadWordFromObjectIntrinsic(object, offset, getWordKind(), LocationIdentity.any());
    }

    public static Word loadWordFromObject(Object object, int offset, LocationIdentity identity) {
        ReplacementsUtil.staticAssert(offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadWordFromObjectIntrinsic(object, offset, getWordKind(), identity);
    }

    public static KlassPointer loadKlassFromObject(Object object, int offset, LocationIdentity identity) {
        ReplacementsUtil.staticAssert(offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadKlassFromObjectIntrinsic(object, offset, getWordKind(), identity);
    }

    /**
     * Reads the value of a given register.
     *
     * @param register a register which must not be available to the register allocator
     * @return the value of {@code register} as a word
     */
    public static Word registerAsWord(@ConstantNodeParameter Register register) {
        return registerAsWord(register, true, false);
    }

    @NodeIntrinsic(value = ReadRegisterNode.class, setStampFromReturnType = true)
    public static native Word registerAsWord(@ConstantNodeParameter Register register, @ConstantNodeParameter boolean directUse, @ConstantNodeParameter boolean incoming);

    @NodeIntrinsic(value = WriteRegisterNode.class, setStampFromReturnType = true)
    public static native void writeRegisterAsWord(@ConstantNodeParameter Register register, Word value);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native Word loadWordFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter JavaKind wordKind, @ConstantNodeParameter LocationIdentity locationIdentity);

    @NodeIntrinsic(value = UnsafeLoadNode.class, setStampFromReturnType = true)
    private static native KlassPointer loadKlassFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter JavaKind wordKind, @ConstantNodeParameter LocationIdentity locationIdentity);

    @NodeIntrinsic(value = LoadHubNode.class)
    public static native KlassPointer loadHubIntrinsic(Object object);

    @Fold
    public static int log2WordSize() {
        return CodeUtil.log2(wordSize());
    }

    public static final LocationIdentity CLASS_STATE_LOCATION = NamedLocationIdentity.mutable("ClassState");

    @Fold
    public static int instanceKlassInitStateOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.instanceKlassInitStateOffset;
    }

    @Fold
    public static int instanceKlassStateFullyInitialized(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.instanceKlassStateFullyInitialized;
    }

    /**
     *
     * @param hub the hub of an InstanceKlass
     * @return true is the InstanceKlass represented by hub is fully initialized
     */
    public static boolean isInstanceKlassFullyInitialized(KlassPointer hub) {
        return readInstanceKlassState(hub) == instanceKlassStateFullyInitialized(INJECTED_VMCONFIG);
    }

    private static byte readInstanceKlassState(KlassPointer hub) {
        return hub.readByte(instanceKlassInitStateOffset(INJECTED_VMCONFIG), CLASS_STATE_LOCATION);
    }

    public static final LocationIdentity KLASS_MODIFIER_FLAGS_LOCATION = NamedLocationIdentity.immutable("Klass::_modifier_flags");

    @Fold
    public static int klassModifierFlagsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassModifierFlagsOffset;
    }

    public static final LocationIdentity CLASS_KLASS_LOCATION = new HotSpotOptimizingLocationIdentity("Class._klass") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            return foldIndirection(read, object, CLASS_MIRROR_LOCATION);
        }
    };

    @Fold
    public static int klassOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassOffset;
    }

    public static final LocationIdentity CLASS_ARRAY_KLASS_LOCATION = new HotSpotOptimizingLocationIdentity("Class._array_klass") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            return foldIndirection(read, object, ARRAY_KLASS_COMPONENT_MIRROR);
        }
    };

    @Fold
    public static int arrayKlassOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.arrayKlassOffset;
    }

    public static final LocationIdentity CLASS_MIRROR_LOCATION = NamedLocationIdentity.immutable("Klass::_java_mirror");

    public static final LocationIdentity HEAP_TOP_LOCATION = NamedLocationIdentity.mutable("HeapTop");

    @Fold
    public static long heapTopAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.heapTopAddress;
    }

    public static final LocationIdentity HEAP_END_LOCATION = NamedLocationIdentity.mutable("HeapEnd");

    @Fold
    public static long heapEndAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.heapEndAddress;
    }

    @Fold
    public static long tlabIntArrayMarkWord(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabIntArrayMarkWord();
    }

    @Fold
    public static boolean inlineContiguousAllocationSupported(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.inlineContiguousAllocationSupported;
    }

    @Fold
    public static int tlabAlignmentReserveInHeapWords(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabAlignmentReserve;
    }

    public static final LocationIdentity TLAB_SIZE_LOCATION = NamedLocationIdentity.mutable("TlabSize");

    @Fold
    public static int threadTlabSizeOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadTlabSizeOffset();
    }

    public static final LocationIdentity TLAB_THREAD_ALLOCATED_BYTES_LOCATION = NamedLocationIdentity.mutable("TlabThreadAllocatedBytes");

    @Fold
    public static int threadAllocatedBytesOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadAllocatedBytesOffset;
    }

    public static final LocationIdentity TLAB_REFILL_WASTE_LIMIT_LOCATION = NamedLocationIdentity.mutable("RefillWasteLimit");

    @Fold
    public static int tlabRefillWasteLimitOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabRefillWasteLimitOffset();
    }

    public static final LocationIdentity TLAB_NOF_REFILLS_LOCATION = NamedLocationIdentity.mutable("TlabNOfRefills");

    @Fold
    public static int tlabNumberOfRefillsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabNumberOfRefillsOffset();
    }

    public static final LocationIdentity TLAB_FAST_REFILL_WASTE_LOCATION = NamedLocationIdentity.mutable("TlabFastRefillWaste");

    @Fold
    public static int tlabFastRefillWasteOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabFastRefillWasteOffset();
    }

    public static final LocationIdentity TLAB_SLOW_ALLOCATIONS_LOCATION = NamedLocationIdentity.mutable("TlabSlowAllocations");

    @Fold
    public static int tlabSlowAllocationsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabSlowAllocationsOffset();
    }

    @Fold
    public static int tlabRefillWasteIncrement(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabRefillWasteIncrement;
    }

    @Fold
    public static boolean tlabStats(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.tlabStats;
    }

    @Fold
    public static int layoutHelperHeaderSizeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperHeaderSizeShift;
    }

    @Fold
    public static int layoutHelperHeaderSizeMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperHeaderSizeMask;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperLog2ElementSizeShift;
    }

    @Fold
    public static int layoutHelperLog2ElementSizeMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperLog2ElementSizeMask;
    }

    @Fold
    public static int layoutHelperElementTypeShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperElementTypeShift;
    }

    @Fold
    public static int layoutHelperElementTypeMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperElementTypeMask;
    }

    @Fold
    public static int layoutHelperElementTypePrimitiveInPlace(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.layoutHelperElementTypePrimitiveInPlace();
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native int identityHashCode(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Fold
    public static int verifiedEntryPointOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.nmethodEntryOffset;
    }

    @Fold
    public static long gcTotalCollectionsAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.gcTotalCollectionsAddress();
    }

    @Fold
    public static long referentOffset() {
        try {
            return UNSAFE.objectFieldOffset(java.lang.ref.Reference.class.getDeclaredField("referent"));
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    public static final LocationIdentity OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION = new HotSpotOptimizingLocationIdentity("ObjArrayKlass::_element_klass") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            ValueNode javaObject = findReadHub(object);
            if (javaObject != null) {
                ResolvedJavaType type = StampTool.typeOrNull(javaObject);
                if (type != null && type.isArray()) {
                    ResolvedJavaType element = type.getComponentType();
                    if (element != null && !element.isPrimitive() && !element.getElementalType().isInterface()) {
                        Assumptions assumptions = object.graph().getAssumptions();
                        AssumptionResult<ResolvedJavaType> leafType = element.findLeafConcreteSubtype();
                        if (leafType != null && leafType.canRecordTo(assumptions)) {
                            leafType.recordTo(assumptions);
                            return ConstantNode.forConstant(read.stamp(), tool.getConstantReflection().asObjectHub(leafType.getResult()), tool.getMetaAccess());
                        }
                    }
                }
            }
            return read;
        }
    };

    @Fold
    public static int arrayClassElementOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.arrayClassElementOffset;
    }

    public static final LocationIdentity PRIMARY_SUPERS_LOCATION = NamedLocationIdentity.immutable("PrimarySupers");

    public static final LocationIdentity METASPACE_ARRAY_LENGTH_LOCATION = NamedLocationIdentity.immutable("MetaspaceArrayLength");

    public static final LocationIdentity SECONDARY_SUPERS_ELEMENT_LOCATION = NamedLocationIdentity.immutable("SecondarySupersElement");
}
