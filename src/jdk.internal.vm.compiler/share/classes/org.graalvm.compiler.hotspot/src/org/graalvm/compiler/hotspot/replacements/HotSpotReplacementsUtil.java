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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.VERIFY_OOP;

import java.lang.ref.Reference;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.CanonicalizableLocation;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubOrNullNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.StoreHubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.memory.AddressableMemoryAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.replacements.nodes.WriteRegisterNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordFactory;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

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
            if (base instanceof AddressableMemoryAccess) {
                AddressableMemoryAccess access = (AddressableMemoryAccess) base;
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
            if (object instanceof AddressableMemoryAccess) {
                AddressableMemoryAccess access = (AddressableMemoryAccess) object;
                if (access.getLocationIdentity().equals(otherLocation)) {
                    AddressNode address = access.getAddress();
                    if (address instanceof OffsetAddressNode) {
                        OffsetAddressNode offset = (OffsetAddressNode) address;
                        assert offset.getBase().stamp(NodeView.DEFAULT).isCompatible(read.stamp(NodeView.DEFAULT));
                        return offset.getBase();
                    }
                }
            }
            return read;
        }
    }

    @Fold
    public static ResolvedJavaType methodHolderClass(@InjectedParameter IntrinsicContext context) {
        return context.getOriginalMethod().getDeclaringClass();
    }

    @Fold
    static ResolvedJavaType getType(@Fold.InjectedParameter IntrinsicContext context, String typeName) {
        try {
            UnresolvedJavaType unresolved = UnresolvedJavaType.create(typeName);
            return unresolved.resolve(methodHolderClass(context));
        } catch (LinkageError e) {
            throw new GraalError(e);
        }
    }

    @Fold
    public static int getFieldOffset(ResolvedJavaType type, String fieldName) {
        return getField(type, fieldName).getOffset();
    }

    private static ResolvedJavaField getField(ResolvedJavaType type, String fieldName) {
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        throw new GraalError("missing field " + fieldName + " in type " + type);
    }

    public static HotSpotJVMCIRuntime runtime() {
        return HotSpotJVMCIRuntime.runtime();
    }

    @Fold
    public static int getHeapWordSize(@InjectedParameter GraalHotSpotVMConfig injectedVMConfig) {
        return injectedVMConfig.heapWordSize;
    }

    @Fold
    public static int klassLayoutHelperNeutralValue(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.klassLayoutHelperNeutralValue;
    }

    @Fold
    public static boolean useTLAB(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useTLAB;
    }

    @Fold
    public static boolean useG1GC(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useG1GC;
    }

    @Fold
    public static boolean verifyOops(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.verifyOops;
    }

    /**
     * @see GraalHotSpotVMConfig#doingUnsafeAccessOffset
     */
    @Fold
    public static int doingUnsafeAccessOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.doingUnsafeAccessOffset;
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

    public static final LocationIdentity PENDING_EXCEPTION_LOCATION = NamedLocationIdentity.mutable("PendingException");

    /**
     * @see GraalHotSpotVMConfig#pendingExceptionOffset
     */
    @Fold
    static int threadPendingExceptionOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.pendingExceptionOffset;
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

    public static void writeTlabTop(Word thread, Word top) {
        thread.writeWord(threadTlabTopOffset(INJECTED_VMCONFIG), top, TLAB_TOP_LOCATION);
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
     * Gets the pending exception for the given thread.
     *
     * @return the pending exception, or null if there was none
     */
    @SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF_NONVIRTUAL", justification = "foldable method parameters are injected")
    public static Object getPendingException(Word thread) {
        return thread.readObject(threadPendingExceptionOffset(INJECTED_VMCONFIG), PENDING_EXCEPTION_LOCATION);
    }

    /*
     * As far as Java code is concerned this can be considered immutable: it is set just after the
     * JavaThread is created, before it is published. After that, it is never changed.
     */
    public static final LocationIdentity JAVA_THREAD_THREAD_OBJECT_LOCATION = NamedLocationIdentity.immutable("JavaThread::_threadObj");

    @Fold
    public static int threadObjectOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.threadObjectOffset;
    }

    public static final LocationIdentity JAVA_THREAD_OSTHREAD_LOCATION = NamedLocationIdentity.mutable("JavaThread::_osthread");

    @Fold
    public static int osThreadOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config.osThreadOffset != Integer.MAX_VALUE;
        return config.osThreadOffset;
    }

    @Fold
    public static int osThreadInterruptedOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config.osThreadInterruptedOffset != Integer.MAX_VALUE;
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
    public static int pageSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.vmPageSize;
    }

    public static final LocationIdentity PROTOTYPE_MARK_WORD_LOCATION = NamedLocationIdentity.mutable("PrototypeMarkWord");

    @Fold
    public static int prototypeMarkWordOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.prototypeMarkWordOffset;
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

    @Fold
    public static int jvmAccIsHiddenClass(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.jvmAccIsHiddenClass;
    }

    public static final LocationIdentity KLASS_LAYOUT_HELPER_LOCATION = new HotSpotOptimizingLocationIdentity("Klass::_layout_helper") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            ValueNode javaObject = findReadHub(object);
            if (javaObject != null) {
                if (javaObject.stamp(NodeView.DEFAULT) instanceof ObjectStamp) {
                    ObjectStamp stamp = (ObjectStamp) javaObject.stamp(NodeView.DEFAULT);
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
    public static int allocatePrefetchStyle(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchStyle;
    }

    @Fold
    public static int allocatePrefetchLines(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchLines;
    }

    @Fold
    public static int allocatePrefetchDistance(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchDistance;
    }

    @Fold
    public static int allocateInstancePrefetchLines(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocateInstancePrefetchLines;
    }

    @Fold
    public static int allocatePrefetchStepSize(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.allocatePrefetchStepSize;
    }

    @Fold
    public static int invocationCounterIncrement(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.invocationCounterIncrement;
    }

    @Fold
    public static int invocationCounterOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.invocationCounterOffset;
    }

    @Fold
    public static int backedgeCounterOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.backedgeCounterOffset;
    }

    @Fold
    public static int invocationCounterShift(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.invocationCounterShift;
    }

    @Fold
    public static int stackBias(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.stackBias;
    }

    @NodeIntrinsic(value = KlassLayoutHelperNode.class)
    public static native int readLayoutHelper(KlassPointer object);

    /**
     * Checks if class {@code klass} is an array.
     *
     * See: Klass::layout_helper_is_array
     *
     * @param klassNonNull the class to be checked
     * @return true if klassNonNull is an array, false otherwise
     */
    public static boolean klassIsArray(KlassPointer klassNonNull) {
        /*
         * The less-than check only works if both values are ints. We use local variables to make
         * sure these are still ints and haven't changed.
         */
        final int layoutHelper = readLayoutHelper(klassNonNull);
        final int layoutHelperNeutralValue = klassLayoutHelperNeutralValue(INJECTED_VMCONFIG);
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
                return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), tool.getConstantReflection().asObjectHub(constantType.getType()), tool.getMetaAccess());
            }
            return read;
        }
    };

    public static final LocationIdentity COMPRESSED_HUB_LOCATION = new HotSpotOptimizingLocationIdentity("CompressedHub") {
        @Override
        public ValueNode canonicalizeRead(ValueNode read, AddressNode location, ValueNode object, CanonicalizerTool tool) {
            TypeReference constantType = StampTool.typeReferenceOrNull(object);
            if (constantType != null && constantType.isExact()) {
                return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), ((HotSpotMetaspaceConstant) tool.getConstantReflection().asObjectHub(constantType.getType())).compress(),
                                tool.getMetaAccess());
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

    @Fold
    public static int monitorMask(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.monitorMask;
    }

    @Fold
    public static int objectMonitorOwnerOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectMonitorOwner;
    }

    @Fold
    public static int objectMonitorRecursionsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectMonitorRecursions;
    }

    @Fold
    public static int objectMonitorCxqOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectMonitorCxq;
    }

    @Fold
    public static int objectMonitorEntryListOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectMonitorEntryList;
    }

    @Fold
    public static int objectMonitorSuccOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectMonitorSucc;
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

    public static Word arrayStart(int[] a) {
        return WordFactory.unsigned(ComputeObjectAddressNode.get(a, ReplacementsUtil.getArrayBaseOffset(INJECTED_METAACCESS, JavaKind.Int)));
    }

    /**
     * Idiom for making {@link GraalHotSpotVMConfig} a constant.
     */
    @Fold
    public static int objectAlignment(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.objectAlignment;
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
    public static int g1CardQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueIndexOffset;
    }

    @Fold
    public static int g1CardQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1CardQueueBufferOffset;
    }

    @Fold
    public static int g1SATBQueueMarkingOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueMarkingOffset;
    }

    @Fold
    public static int g1SATBQueueIndexOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueIndexOffset;
    }

    @Fold
    public static int g1SATBQueueBufferOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.g1SATBQueueBufferOffset;
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

    public static final LocationIdentity OBJECT_MONITOR_OWNER_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_owner");

    public static final LocationIdentity OBJECT_MONITOR_RECURSION_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_recursions");

    public static final LocationIdentity OBJECT_MONITOR_CXQ_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_cxq");

    public static final LocationIdentity OBJECT_MONITOR_ENTRY_LIST_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_EntryList");

    public static final LocationIdentity OBJECT_MONITOR_SUCC_LOCATION = NamedLocationIdentity.mutable("ObjectMonitor::_succ");

    @Fold
    public static int lockDisplacedMarkOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.basicLockDisplacedHeaderOffset;
    }

    @Fold
    public static boolean useBiasedLocking(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useBiasedLocking;
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
        return loadWordFromObjectIntrinsic(object, offset, LocationIdentity.any(), getWordKind());
    }

    public static Word loadWordFromObject(Object object, int offset, LocationIdentity identity) {
        ReplacementsUtil.staticAssert(offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadWordFromObject");
        return loadWordFromObjectIntrinsic(object, offset, identity, getWordKind());
    }

    public static KlassPointer loadKlassFromObject(Object object, int offset, LocationIdentity identity) {
        ReplacementsUtil.staticAssert(offset != hubOffset(INJECTED_VMCONFIG), "Use loadHubIntrinsic instead of loadKlassFromObject");
        return loadKlassFromObjectIntrinsic(object, offset, identity, getWordKind());
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

    @NodeIntrinsic(value = ReadRegisterNode.class)
    public static native Word registerAsWord(@ConstantNodeParameter Register register, @ConstantNodeParameter boolean directUse, @ConstantNodeParameter boolean incoming);

    @NodeIntrinsic(value = WriteRegisterNode.class)
    public static native void writeRegisterAsWord(@ConstantNodeParameter Register register, Word value);

    @NodeIntrinsic(value = RawLoadNode.class)
    private static native Word loadWordFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter JavaKind wordKind);

    @NodeIntrinsic(value = RawLoadNode.class)
    private static native KlassPointer loadKlassFromObjectIntrinsic(Object object, long offset, @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter JavaKind wordKind);

    @NodeIntrinsic(value = LoadHubNode.class)
    public static native KlassPointer loadHubIntrinsic(Object object);

    @NodeIntrinsic(value = LoadHubOrNullNode.class)
    public static native KlassPointer loadHubOrNullIntrinsic(Object object);

    static final LocationIdentity CLASS_INIT_STATE_LOCATION = NamedLocationIdentity.mutable("ClassInitState");

    static final LocationIdentity CLASS_INIT_THREAD_LOCATION = NamedLocationIdentity.mutable("ClassInitThread");

    @Fold
    static int instanceKlassInitStateOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.instanceKlassInitStateOffset;
    }

    @Fold
    static int instanceKlassInitThreadOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config.instanceKlassInitThreadOffset != -1;
        return config.instanceKlassInitThreadOffset;
    }

    @Fold
    public static int instanceKlassStateFullyInitialized(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.instanceKlassStateFullyInitialized;
    }

    @Fold
    public static int instanceKlassStateBeingInitialized(@InjectedParameter GraalHotSpotVMConfig config) {
        assert config.instanceKlassStateBeingInitialized != -1;
        return config.instanceKlassStateBeingInitialized;
    }

    /**
     *
     * @param hub the hub of an InstanceKlass
     * @return true is the InstanceKlass represented by hub is fully initialized
     */
    public static boolean isInstanceKlassFullyInitialized(KlassPointer hub) {
        return readInstanceKlassInitState(hub) == instanceKlassStateFullyInitialized(INJECTED_VMCONFIG);
    }

    static byte readInstanceKlassInitState(KlassPointer hub) {
        return hub.readByte(instanceKlassInitStateOffset(INJECTED_VMCONFIG), CLASS_INIT_STATE_LOCATION);
    }

    static Word readInstanceKlassInitThread(KlassPointer hub) {
        return hub.readWord(instanceKlassInitThreadOffset(INJECTED_VMCONFIG), CLASS_INIT_THREAD_LOCATION);
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

    public static final LocationIdentity CLASS_MIRROR_HANDLE_LOCATION = NamedLocationIdentity.immutable("Klass::_java_mirror handle");

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

    @NodeIntrinsic(ForeignCallNode.class)
    public static native int identityHashCode(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Fold
    public static long gcTotalCollectionsAddress(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.gcTotalCollectionsAddress();
    }

    public static String referentFieldName() {
        return "referent";
    }

    @Fold
    public static long referentOffset(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return referentField(metaAccessProvider).getOffset();
    }

    @Fold
    public static ResolvedJavaField referentField(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return getField(referenceType(metaAccessProvider), referentFieldName());
    }

    @Fold
    public static ResolvedJavaType referenceType(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return metaAccessProvider.lookupJavaType(Reference.class);
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
                            return ConstantNode.forConstant(read.stamp(NodeView.DEFAULT), tool.getConstantReflection().asObjectHub(leafType.getResult()), tool.getMetaAccess());
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
