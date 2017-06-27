/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayBaseOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayClassElementOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayIndexScale;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperElementTypePrimitiveInPlace;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHub;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.readLayoutHelper;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.superCheckOffsetOffset;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.reflect.Method;
import java.util.EnumMap;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.util.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ArrayCopySnippets implements Snippets {

    private static int checkArrayType(KlassPointer nonNullHub) {
        int layoutHelper = readLayoutHelper(nonNullHub);
        if (probability(SLOW_PATH_PROBABILITY, layoutHelper >= 0)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return layoutHelper;
    }

    private static void checkLimits(Object src, int srcPos, Object dest, int destPos, int length, Counters counters) {
        if (probability(SLOW_PATH_PROBABILITY, srcPos < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, length < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, srcPos > ArrayLengthNode.arrayLength(src) - length)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos > ArrayLengthNode.arrayLength(dest) - length)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        counters.checkSuccessCounter.inc();
    }

    @Snippet
    public static void arraycopyZeroLengthIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        checkArrayType(srcHub);
        checkArrayType(destHub);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        counters.zeroLengthStaticCounter.inc();
    }

    @Snippet
    public static void arraycopyExactIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter JavaKind elementKind, @ConstantParameter SnippetCounter counter,
                    @ConstantParameter SnippetCounter copiedCounter, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        counter.inc();
        copiedCounter.add(length);
        ArrayCopyCallNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind);
        if (length == 0) {
            counters.zeroLengthDynamicCounter.inc();
        } else {
            counters.nonZeroLengthDynamicCounter.inc();
            counters.nonZeroLengthDynamicCopiedCounter.add(length);
        }
    }

    /**
     * This intrinsic is useful for the case where we know something statically about one of the
     * inputs but not the other.
     */
    @Snippet
    public static void arraycopyPredictedExactIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter SnippetCounter counter, @ConstantParameter SnippetCounter copiedCounter, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        if (probability(SLOW_PATH_PROBABILITY, srcHub != destHub)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        counter.inc();
        copiedCounter.add(length);
        ArrayCopyCallNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind);
        if (length == 0) {
            counters.zeroLengthDynamicCounter.inc();
        } else {
            counters.nonZeroLengthDynamicCounter.inc();
            counters.nonZeroLengthDynamicCopiedCounter.add(length);
        }
    }

    @Snippet
    public static void arraycopyPredictedObjectWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, KlassPointer objectArrayKlass,
                    @ConstantParameter SnippetCounter counter, @ConstantParameter SnippetCounter copiedCounter, @ConstantParameter Counters counters) {
        if (length > 0) {
            KlassPointer srcHub = loadHub(PiNode.asNonNullObject(nonNullSrc));
            KlassPointer destHub = loadHub(PiNode.asNonNullObject(nonNullDest));
            if (probability(FAST_PATH_PROBABILITY, srcHub == destHub || destHub == objectArrayKlass)) {
                counter.inc();
                copiedCounter.add(length);
                counters.predictedObjectArrayCopyFastPathCounter.inc();
                counters.predictedObjectArrayCopyFastPathCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length);
            } else {
                counters.predictedObjectArrayCopySlowPathCounter.inc();
                counters.predictedObjectArrayCopySlowPathCopiedCounter.add(length);
                System.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length);
            }
        }
    }

    /**
     * This is the basic template for the full arraycopy checks, including a check that the
     * underlying type is really an array type.
     */
    @Snippet
    public static void arraycopySlowPathIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, KlassPointer predictedKlass, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter SnippetInfo slowPath, @ConstantParameter Object slowPathArgument, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        checkArrayType(srcHub);
        checkArrayType(destHub);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        if (length == 0) {
            counters.zeroLengthDynamicCounter.inc();
        } else {
            counters.nonZeroLengthDynamicCounter.inc();
            counters.nonZeroLengthDynamicCopiedCounter.add(length);
        }
        ArrayCopySlowPathNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, predictedKlass, elementKind, slowPath, slowPathArgument);
    }

    /**
     * Snippet for unrolled arraycopy.
     */
    @Snippet
    public static void arraycopyUnrolledIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter int unrolledLength, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        if (length == 0) {
            counters.zeroLengthDynamicCounter.inc();
        } else {
            counters.nonZeroLengthDynamicCounter.inc();
            counters.nonZeroLengthDynamicCopiedCounter.add(length);
        }
        ArrayCopyUnrollNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, unrolledLength, elementKind);
    }

    @Snippet
    public static void checkcastArraycopyWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, @ConstantParameter Counters counters) {
        if (length > 0) {
            KlassPointer destKlass = loadHub(nonNullDest);
            KlassPointer srcKlass = loadHub(nonNullSrc);
            if (probability(SLOW_PATH_PROBABILITY, srcKlass == destKlass)) {
                // no storecheck required.
                counters.objectCheckcastSameTypeCounter.inc();
                counters.objectCheckcastSameTypeCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length);
            } else {
                KlassPointer destElemKlass = destKlass.readKlassPointer(arrayClassElementOffset(INJECTED_VMCONFIG), OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION);
                Word superCheckOffset = WordFactory.signed(destElemKlass.readInt(superCheckOffsetOffset(INJECTED_VMCONFIG), KLASS_SUPER_CHECK_OFFSET_LOCATION));
                counters.objectCheckcastCounter.inc();
                counters.objectCheckcastCopiedCounter.add(length);
                int copiedElements = CheckcastArrayCopyCallNode.checkcastArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, superCheckOffset, destElemKlass, false);
                if (copiedElements != 0) {
                    /*
                     * the checkcast stub doesn't throw the ArrayStoreException, but returns the
                     * number of copied elements (xor'd with -1).
                     */
                    copiedElements ^= -1;
                    System.arraycopy(nonNullSrc, srcPos + copiedElements, nonNullDest, destPos + copiedElements, length - copiedElements);
                }
            }
        }
    }

    @Snippet
    public static void arraycopyGeneric(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        if (probability(FAST_PATH_PROBABILITY, srcHub.equal(destHub)) && probability(FAST_PATH_PROBABILITY, nonNullSrc != nonNullDest)) {
            int layoutHelper = checkArrayType(srcHub);
            final boolean isObjectArray = ((layoutHelper & layoutHelperElementTypePrimitiveInPlace(INJECTED_VMCONFIG)) == 0);
            checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
            if (probability(FAST_PATH_PROBABILITY, isObjectArray)) {
                counters.genericObjectExactCallCounter.inc();
                counters.genericObjectExactCallCopiedCounter.add(length);
                ArrayCopyCallNode.disjointArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, JavaKind.Object);
            } else {
                counters.genericPrimitiveCallCounter.inc();
                counters.genericPrimitiveCallCopiedCounter.add(length);
                UnsafeArrayCopyNode.arraycopyPrimitive(nonNullSrc, srcPos, nonNullDest, destPos, length, layoutHelper);
            }
        } else {
            counters.systemArraycopyCounter.inc();
            counters.systemArraycopyCopiedCounter.add(length);
            System.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length);
        }
    }

    @Fold
    static LocationIdentity getArrayLocation(JavaKind kind) {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    @Snippet
    public static void arraycopyUnrolledWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, @ConstantParameter int length, @ConstantParameter JavaKind elementKind) {
        final int scale = arrayIndexScale(elementKind);
        int arrayBaseOffset = arrayBaseOffset(elementKind);
        LocationIdentity arrayLocation = getArrayLocation(elementKind);
        if (nonNullSrc == nonNullDest && srcPos < destPos) { // bad aliased case
            long start = (long) (length - 1) * scale;
            long i = start;
            ExplodeLoopNode.explodeLoop();
            for (int iteration = 0; iteration < length; iteration++) {
                if (i >= 0) {
                    Object a = RawLoadNode.load(nonNullSrc, arrayBaseOffset + i + (long) srcPos * scale, elementKind, arrayLocation);
                    RawStoreNode.storeObject(nonNullDest, arrayBaseOffset + i + (long) destPos * scale, a, elementKind, arrayLocation, false);
                    i -= scale;
                }
            }
        } else {
            long end = (long) length * scale;
            long i = 0;
            ExplodeLoopNode.explodeLoop();
            for (int iteration = 0; iteration < length; iteration++) {
                if (i < end) {
                    Object a = RawLoadNode.load(nonNullSrc, arrayBaseOffset + i + (long) srcPos * scale, elementKind, arrayLocation);
                    RawStoreNode.storeObject(nonNullDest, arrayBaseOffset + i + (long) destPos * scale, a, elementKind, arrayLocation, false);
                    i += scale;
                }
            }
        }
    }

    static class Counters {
        final SnippetCounter checkSuccessCounter;
        final SnippetCounter checkAIOOBECounter;

        final SnippetCounter objectCheckcastCounter;
        final SnippetCounter objectCheckcastSameTypeCounter;
        final SnippetCounter predictedObjectArrayCopySlowPathCounter;
        final SnippetCounter predictedObjectArrayCopyFastPathCounter;

        final SnippetCounter genericPrimitiveCallCounter;
        final SnippetCounter genericObjectExactCallCounter;
        final SnippetCounter systemArraycopyCounter;

        final SnippetCounter zeroLengthStaticCounter;
        final SnippetCounter zeroLengthDynamicCounter;
        final SnippetCounter nonZeroLengthDynamicCounter;

        final SnippetCounter nonZeroLengthDynamicCopiedCounter;
        final SnippetCounter genericPrimitiveCallCopiedCounter;
        final SnippetCounter genericObjectExactCallCopiedCounter;
        final SnippetCounter systemArraycopyCopiedCounter;

        final SnippetCounter objectCheckcastCopiedCounter;
        final SnippetCounter objectCheckcastSameTypeCopiedCounter;
        final SnippetCounter predictedObjectArrayCopySlowPathCopiedCounter;
        final SnippetCounter predictedObjectArrayCopyFastPathCopiedCounter;

        final EnumMap<JavaKind, SnippetCounter> arraycopyCallCounters = new EnumMap<>(JavaKind.class);
        final EnumMap<JavaKind, SnippetCounter> arraycopyCounters = new EnumMap<>(JavaKind.class);

        final EnumMap<JavaKind, SnippetCounter> arraycopyCallCopiedCounters = new EnumMap<>(JavaKind.class);
        final EnumMap<JavaKind, SnippetCounter> arraycopyCopiedCounters = new EnumMap<>(JavaKind.class);

        Counters(SnippetCounter.Group.Factory factory) {
            final Group checkCounters = factory.createSnippetCounterGroup("System.arraycopy checkInputs");
            final Group counters = factory.createSnippetCounterGroup("System.arraycopy");
            final Group copiedCounters = factory.createSnippetCounterGroup("System.arraycopy copied elements");
            final Group lengthCounters = factory.createSnippetCounterGroup("System.arraycopy 0-length checks");

            checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
            checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

            objectCheckcastCounter = new SnippetCounter(counters, "Object[]{non-exact}", "arraycopy for non-exact Object[] arrays");
            objectCheckcastSameTypeCounter = new SnippetCounter(counters, "Object[]{same-type}", "arraycopy call for src.klass == dest.klass Object[] arrays");
            predictedObjectArrayCopySlowPathCounter = new SnippetCounter(counters, "Object[]{slow-path}", "used System.arraycopy slow path for predicted Object[] arrays");
            predictedObjectArrayCopyFastPathCounter = new SnippetCounter(counters, "Object[]{fast-path}", "used oop_arraycopy for predicted Object[] arrays");
            genericPrimitiveCallCounter = new SnippetCounter(counters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
            genericObjectExactCallCounter = new SnippetCounter(counters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
            systemArraycopyCounter = new SnippetCounter(counters, "genericObject", "call to System.arraycopy");

            zeroLengthStaticCounter = new SnippetCounter(lengthCounters, "0-lengthcopy static", "arraycopy where the length is statically 0");
            zeroLengthDynamicCounter = new SnippetCounter(lengthCounters, "0-lengthcopy dynamically", "arraycopy where the length is dynamically 0");
            nonZeroLengthDynamicCounter = new SnippetCounter(lengthCounters, "non-0-lengthcopy dynamically", "arraycopy where the length is dynamically not zero");

            nonZeroLengthDynamicCopiedCounter = new SnippetCounter(copiedCounters, "non-0-lengthcopy dynamically", "arraycopy where the length is dynamically not zero");
            genericPrimitiveCallCopiedCounter = new SnippetCounter(copiedCounters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
            genericObjectExactCallCopiedCounter = new SnippetCounter(copiedCounters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
            systemArraycopyCopiedCounter = new SnippetCounter(copiedCounters, "genericObject", "call to System.arraycopy");

            objectCheckcastCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{non-exact}", "arraycopy for non-exact Object[] arrays");
            objectCheckcastSameTypeCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{same-type}", "arraycopy call for src.klass == dest.klass Object[] arrays");
            predictedObjectArrayCopySlowPathCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{slow-path}",
                            "used System.arraycopy slow path for predicted Object[] arrays");
            predictedObjectArrayCopyFastPathCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{fast-path}", "used oop_arraycopy for predicted Object[] arrays");
            createArraycopyCounter(JavaKind.Byte, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Boolean, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Char, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Short, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Int, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Long, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Float, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Double, counters, copiedCounters);
            createArraycopyCounter(JavaKind.Object, counters, copiedCounters);
        }

        void createArraycopyCounter(JavaKind kind, Group counters, Group copiedCounters) {
            arraycopyCallCounters.put(kind, new SnippetCounter(counters, kind + "[]{stub}", "arraycopy call for " + kind + "[] arrays"));
            arraycopyCounters.put(kind, new SnippetCounter(counters, kind + "[]{inline}", "inline arraycopy for " + kind + "[] arrays"));

            arraycopyCallCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[]{stub}", "arraycopy call for " + kind + "[] arrays"));
            arraycopyCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[]{inline}", "inline arraycopy for " + kind + "[] arrays"));
        }
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public Templates(OptionValues options, SnippetCounter.Group.Factory factory, HotSpotProviders providers, TargetDescription target) {
            super(options, providers, providers.getSnippetReflection(), target);
            this.counters = new Counters(factory);
        }

        private ResolvedJavaMethod originalArraycopy() throws GraalError {
            if (originalArraycopy == null) {
                Method method;
                try {
                    method = System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class);
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new GraalError(e);
                }
                originalArraycopy = providers.getMetaAccess().lookupJavaMethod(method);
            }
            return originalArraycopy;
        }

        private ResolvedJavaMethod originalArraycopy;

        private final SnippetInfo checkcastArraycopyWorkSnippet = snippet("checkcastArraycopyWork");
        private final SnippetInfo arraycopyGenericSnippet = snippet("arraycopyGeneric");

        private final SnippetInfo arraycopySlowPathIntrinsicSnippet = snippet("arraycopySlowPathIntrinsic");
        private final SnippetInfo arraycopyUnrolledIntrinsicSnippet = snippet("arraycopyUnrolledIntrinsic");
        private final SnippetInfo arraycopyExactIntrinsicSnippet = snippet("arraycopyExactIntrinsic");
        private final SnippetInfo arraycopyZeroLengthIntrinsicSnippet = snippet("arraycopyZeroLengthIntrinsic");
        private final SnippetInfo arraycopyPredictedExactIntrinsicSnippet = snippet("arraycopyPredictedExactIntrinsic");
        private final SnippetInfo arraycopyPredictedObjectWorkSnippet = snippet("arraycopyPredictedObjectWork");

        private final SnippetInfo arraycopyUnrolledWorkSnippet = snippet("arraycopyUnrolledWork");

        private final Counters counters;

        protected SnippetInfo snippet(String methodName) {
            SnippetInfo info = snippet(ArrayCopySnippets.class, methodName, LocationIdentity.any());
            info.setOriginalMethod(originalArraycopy());
            return info;
        }

        public static JavaKind selectComponentKind(BasicArrayCopyNode arraycopy) {
            return selectComponentKind(arraycopy, true);
        }

        public static JavaKind selectComponentKind(BasicArrayCopyNode arraycopy, boolean exact) {
            ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp());
            ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp());

            if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
                if (!exact) {
                    JavaKind component = getComponentKind(srcType);
                    if (component != null) {
                        return component;
                    }
                    return getComponentKind(destType);
                }
                return null;
            }
            if (exact) {
                if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
                    return null;
                }
                if (!arraycopy.isExact()) {
                    return null;
                }
            }
            return srcType.getComponentType().getJavaKind();
        }

        private static JavaKind getComponentKind(ResolvedJavaType type) {
            if (type != null && type.isArray()) {
                return type.getComponentType().getJavaKind();
            }
            return null;
        }

        private static boolean shouldUnroll(ValueNode length) {
            return length.isConstant() && length.asJavaConstant().asInt() <= 8 && length.asJavaConstant().asInt() != 0;
        }

        public void lower(ArrayCopyNode arraycopy, LoweringTool tool) {
            JavaKind componentKind = selectComponentKind(arraycopy);
            SnippetInfo snippetInfo = null;
            SnippetInfo slowPathSnippetInfo = null;
            Object slowPathArgument = null;

            if (arraycopy.getLength().isConstant() && arraycopy.getLength().asJavaConstant().asLong() == 0) {
                snippetInfo = arraycopyZeroLengthIntrinsicSnippet;
            } else if (arraycopy.isExact()) {
                snippetInfo = arraycopyExactIntrinsicSnippet;
                if (shouldUnroll(arraycopy.getLength())) {
                    snippetInfo = arraycopyUnrolledIntrinsicSnippet;
                }
            } else {
                if (componentKind == JavaKind.Object) {
                    ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp());
                    ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp());
                    ResolvedJavaType srcComponentType = srcType == null ? null : srcType.getComponentType();
                    ResolvedJavaType destComponentType = destType == null ? null : destType.getComponentType();
                    if (srcComponentType != null && destComponentType != null && !srcComponentType.isPrimitive() && !destComponentType.isPrimitive()) {
                        snippetInfo = arraycopySlowPathIntrinsicSnippet;
                        slowPathSnippetInfo = checkcastArraycopyWorkSnippet;
                        slowPathArgument = LocationIdentity.any();
                        /*
                         * Because this snippet has to use Sysytem.arraycopy as a slow path, we must
                         * pretend to kill any() so clear the componentKind.
                         */
                        componentKind = null;
                    }
                }
                if (componentKind == null && snippetInfo == null) {
                    JavaKind predictedKind = selectComponentKind(arraycopy, false);
                    if (predictedKind != null) {
                        /*
                         * At least one array is of a known type requiring no store checks, so
                         * assume the other is of the same type. Generally this is working around
                         * deficiencies in our propagation of type information.
                         */
                        componentKind = predictedKind;
                        if (predictedKind == JavaKind.Object) {
                            snippetInfo = arraycopySlowPathIntrinsicSnippet;
                            slowPathSnippetInfo = arraycopyPredictedObjectWorkSnippet;
                            slowPathArgument = predictedKind;
                            componentKind = null;
                        } else {
                            snippetInfo = arraycopyPredictedExactIntrinsicSnippet;
                        }
                    }
                }
                if (snippetInfo == null) {
                    snippetInfo = arraycopyGenericSnippet;
                }
            }
            Arguments args = new Arguments(snippetInfo, arraycopy.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());
            if (snippetInfo == arraycopyUnrolledIntrinsicSnippet) {
                args.addConst("unrolledLength", arraycopy.getLength().asJavaConstant().asInt());
                args.addConst("elementKind", componentKind != null ? componentKind : JavaKind.Illegal);
            } else if (snippetInfo == arraycopySlowPathIntrinsicSnippet) {
                ValueNode predictedKlass = null;
                if (slowPathArgument == arraycopyPredictedObjectWorkSnippet) {
                    HotSpotResolvedObjectType arrayClass = (HotSpotResolvedObjectType) tool.getMetaAccess().lookupJavaType(Object[].class);
                    predictedKlass = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), arrayClass.klass(), tool.getMetaAccess(), arraycopy.graph());
                } else {
                    predictedKlass = ConstantNode.forConstant(KlassPointerStamp.klassAlwaysNull(), JavaConstant.NULL_POINTER, tool.getMetaAccess(), arraycopy.graph());
                }
                args.add("predictedKlass", predictedKlass);
                args.addConst("elementKind", componentKind != null ? componentKind : JavaKind.Illegal);
                args.addConst("slowPath", slowPathSnippetInfo);
                assert slowPathArgument != null;
                args.addConst("slowPathArgument", slowPathArgument);
            } else if (snippetInfo == arraycopyExactIntrinsicSnippet || snippetInfo == arraycopyPredictedExactIntrinsicSnippet) {
                assert componentKind != null;
                args.addConst("elementKind", componentKind);
                args.addConst("counter", counters.arraycopyCallCounters.get(componentKind));
                args.addConst("copiedCounter", counters.arraycopyCallCopiedCounters.get(componentKind));
            }
            args.addConst("counters", counters);
            instantiate(args, arraycopy);
        }

        public void lower(ArrayCopySlowPathNode arraycopy, LoweringTool tool) {
            StructuredGraph graph = arraycopy.graph();
            if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                // Can't be lowered yet
                return;
            }
            SnippetInfo snippetInfo = arraycopy.getSnippet();
            Arguments args = new Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("nonNullSrc", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("nonNullDest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            if (snippetInfo == arraycopyUnrolledWorkSnippet) {
                args.addConst("length", ((Integer) arraycopy.getArgument()).intValue());
                args.addConst("elementKind", arraycopy.getElementKind());
            } else {
                args.add("length", arraycopy.getLength());
            }
            if (snippetInfo == arraycopyPredictedObjectWorkSnippet) {
                args.add("objectArrayKlass", arraycopy.getPredictedKlass());
                args.addConst("counter", counters.arraycopyCallCounters.get(JavaKind.Object));
                args.addConst("copiedCounter", counters.arraycopyCallCopiedCounters.get(JavaKind.Object));
                args.addConst("counters", counters);
            }
            instantiate(args, arraycopy);
        }

        public void lower(ArrayCopyUnrollNode arraycopy, LoweringTool tool) {
            StructuredGraph graph = arraycopy.graph();
            if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                // Can't be lowered yet
                return;
            }
            SnippetInfo snippetInfo = arraycopyUnrolledWorkSnippet;
            Arguments args = new Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("nonNullSrc", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("nonNullDest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.addConst("length", arraycopy.getUnrollLength());
            args.addConst("elementKind", arraycopy.getElementKind());
            template(args).instantiate(providers.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        /**
         * Instantiate the snippet template and fix up the FrameState of any Invokes of
         * System.arraycopy and propagate the captured bci in the ArrayCopySlowPathNode.
         *
         * @param args
         * @param arraycopy
         */
        private void instantiate(Arguments args, BasicArrayCopyNode arraycopy) {
            StructuredGraph graph = arraycopy.graph();
            SnippetTemplate template = template(args);
            UnmodifiableEconomicMap<Node, Node> replacements = template.instantiate(providers.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args, false);
            for (Node originalNode : replacements.getKeys()) {
                if (originalNode instanceof Invoke) {
                    Invoke invoke = (Invoke) replacements.get(originalNode);
                    assert invoke.asNode().graph() == graph;
                    CallTargetNode call = invoke.callTarget();

                    if (!call.targetMethod().equals(originalArraycopy)) {
                        throw new GraalError("unexpected invoke %s in snippet", call.targetMethod());
                    }
                    // Here we need to fix the bci of the invoke
                    InvokeNode newInvoke = graph.add(new InvokeNode(invoke.callTarget(), arraycopy.getBci()));
                    if (arraycopy.stateDuring() != null) {
                        newInvoke.setStateDuring(arraycopy.stateDuring());
                    } else {
                        assert arraycopy.stateAfter() != null : arraycopy;
                        newInvoke.setStateAfter(arraycopy.stateAfter());
                    }
                    graph.replaceFixedWithFixed((InvokeNode) invoke.asNode(), newInvoke);
                } else if (originalNode instanceof ArrayCopySlowPathNode) {
                    ArrayCopySlowPathNode slowPath = (ArrayCopySlowPathNode) replacements.get(originalNode);
                    assert arraycopy.stateAfter() != null : arraycopy;
                    assert slowPath.stateAfter() == arraycopy.stateAfter();
                    slowPath.setBci(arraycopy.getBci());
                }
            }
            GraphUtil.killCFG(arraycopy);
        }
    }
}
