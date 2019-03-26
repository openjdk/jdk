/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.arraycopy;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.EnumMap;

import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
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
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetCounter.Group.Factory;
import org.graalvm.compiler.replacements.SnippetIntegerHistogram;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class ArrayCopySnippets implements Snippets {

    private enum ArrayCopyTypeCheck {
        UNDEFINED_ARRAY_TYPE_CHECK,
        // either we know that both objects are arrays and have the same type,
        // or we apply generic array copy snippet, which enforces type check
        NO_ARRAY_TYPE_CHECK,
        // can be used when we know that one of the objects is a primitive array
        HUB_BASED_ARRAY_TYPE_CHECK,
        // can be used when we know that one of the objects is an object array
        LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED_META_ACCESS = null;

    public abstract Pointer loadHub(Object nonNullSrc);

    public abstract Pointer getDestElemClass(Pointer destKlass);

    public abstract Word getSuperCheckOffset(Pointer destElemKlass);

    public abstract int getReadLayoutHelper(Pointer srcHub);

    protected abstract int heapWordSize();

    @Snippet
    public void arraycopyZeroLengthSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        this.checkArrayTypes(nonNullSrc, nonNullDest, arrayTypeCheck);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        counters.zeroLengthStaticCounter.inc();
    }

    @Snippet
    public void arraycopyExactSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter JavaKind elementKind, @ConstantParameter LocationIdentity locationIdentity,
                    @ConstantParameter SnippetCounter elementKindCounter, @ConstantParameter SnippetCounter elementKindCopiedCounter, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkArrayTypes(nonNullSrc, nonNullDest, arrayTypeCheck);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        incrementLengthCounter(length, counters);

        elementKindCounter.inc();
        elementKindCopiedCounter.add(length);
        ArrayCopyCallNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind, locationIdentity, heapWordSize());
    }

    @Snippet
    public void arraycopyUnrolledSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter JavaKind elementKind, @ConstantParameter LocationIdentity locationIdentity, @ConstantParameter int unrolledLength, @ConstantParameter Counters counters) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkArrayTypes(nonNullSrc, nonNullDest, arrayTypeCheck);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        incrementLengthCounter(length, counters);

        unrolledArraycopyWork(nonNullSrc, srcPos, nonNullDest, destPos, unrolledLength, elementKind, locationIdentity);
    }

    @Snippet
    public void arraycopyCheckcastSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter Counters counters, @ConstantParameter SnippetInfo workSnippet, @ConstantParameter JavaKind elementKind) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkArrayTypes(nonNullSrc, nonNullDest, arrayTypeCheck);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        incrementLengthCounter(length, counters);

        ArrayCopyWithSlowPathNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, workSnippet, elementKind);
    }

    @Snippet
    public void arraycopyGenericSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck, @ConstantParameter Counters counters,
                    @ConstantParameter SnippetInfo workSnippet, @ConstantParameter JavaKind elementKind) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkArrayTypes(nonNullSrc, nonNullDest, arrayTypeCheck);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length, counters);
        incrementLengthCounter(length, counters);

        ArrayCopyWithSlowPathNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, workSnippet, elementKind);
    }

    @Snippet
    public static void arraycopyNativeSnippet(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        // all checks are done in the native method, so no need to emit additional checks here
        incrementLengthCounter(length, counters);
        counters.systemArraycopyCounter.inc();
        counters.systemArraycopyCopiedCounter.add(length);

        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    private static void unrolledArraycopyWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation) {
        int scale = ReplacementsUtil.arrayIndexScale(INJECTED_META_ACCESS, elementKind);
        int arrayBaseOffset = ReplacementsUtil.getArrayBaseOffset(INJECTED_META_ACCESS, elementKind);

        long sourceOffset = arrayBaseOffset + (long) srcPos * scale;
        long destOffset = arrayBaseOffset + (long) destPos * scale;
        long position = 0;
        long delta = scale;
        if (probability(NOT_FREQUENT_PROBABILITY, nonNullSrc == nonNullDest && srcPos < destPos)) {
            // bad aliased case so we need to copy the array from back to front
            position = (long) (length - 1) * scale;
            delta = -delta;
        }

        // the length was already checked before - we can emit unconditional instructions
        ExplodeLoopNode.explodeLoop();
        for (int iteration = 0; iteration < length; iteration++) {
            Object value = RawLoadNode.load(nonNullSrc, sourceOffset + position, elementKind, arrayLocation);
            RawStoreNode.storeObject(nonNullDest, destOffset + position, value, elementKind, arrayLocation, false);
            position += delta;
        }
    }

    @Snippet(allowPartialIntrinsicArgumentMismatch = true)
    public void checkcastArraycopyWithSlowPathWork(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        if (probability(FREQUENT_PROBABILITY, length > 0)) {
            Object nonNullSrc = PiNode.asNonNullObject(src);
            Object nonNullDest = PiNode.asNonNullObject(dest);
            Pointer srcKlass = loadHub(nonNullSrc);
            Pointer destKlass = loadHub(nonNullDest);
            if (probability(LIKELY_PROBABILITY, srcKlass == destKlass)) {
                // no storecheck required.
                counters.objectCheckcastSameTypeCounter.inc();
                counters.objectCheckcastSameTypeCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length, heapWordSize());
            } else {
                Pointer destElemKlass = getDestElemClass(destKlass);
                Word superCheckOffset = getSuperCheckOffset(destElemKlass);

                counters.objectCheckcastDifferentTypeCounter.inc();
                counters.objectCheckcastDifferentTypeCopiedCounter.add(length);

                int copiedElements = CheckcastArrayCopyCallNode.checkcastArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, superCheckOffset, destElemKlass, false);
                if (probability(SLOW_PATH_PROBABILITY, copiedElements != 0)) {
                    /*
                     * the stub doesn't throw the ArrayStoreException, but returns the number of
                     * copied elements (xor'd with -1).
                     */
                    copiedElements ^= -1;
                    System.arraycopy(nonNullSrc, srcPos + copiedElements, nonNullDest, destPos + copiedElements, length - copiedElements);
                }
            }
        }
    }

    @Snippet(allowPartialIntrinsicArgumentMismatch = true)
    public void genericArraycopyWithSlowPathWork(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        // The length > 0 check should not be placed here because generic array copy stub should
        // enforce type check. This is fine performance-wise because this snippet is rarely used.
        counters.genericArraycopyDifferentTypeCounter.inc();
        counters.genericArraycopyDifferentTypeCopiedCounter.add(length);
        int copiedElements = GenericArrayCopyCallNode.genericArraycopy(src, srcPos, dest, destPos, length);
        if (probability(SLOW_PATH_PROBABILITY, copiedElements != 0)) {
            /*
             * the stub doesn't throw the ArrayStoreException, but returns the number of copied
             * elements (xor'd with -1).
             */
            copiedElements ^= -1;
            System.arraycopy(src, srcPos + copiedElements, dest, destPos + copiedElements, length - copiedElements);
        }
    }

    private static void incrementLengthCounter(int length, Counters counters) {
        if (!IS_BUILDING_NATIVE_IMAGE) {
            counters.lengthHistogram.inc(length);
        }
    }

    private static void checkLimits(Object src, int srcPos, Object dest, int destPos, int length, Counters counters) {
        if (probability(SLOW_PATH_PROBABILITY, srcPos < 0) ||
                        probability(SLOW_PATH_PROBABILITY, destPos < 0) ||
                        probability(SLOW_PATH_PROBABILITY, length < 0) ||
                        probability(SLOW_PATH_PROBABILITY, srcPos > ArrayLengthNode.arrayLength(src) - length) ||
                        probability(SLOW_PATH_PROBABILITY, destPos > ArrayLengthNode.arrayLength(dest) - length)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        counters.checkSuccessCounter.inc();
    }

    private void checkArrayTypes(Object nonNullSrc, Object nonNullDest, ArrayCopyTypeCheck arrayTypeCheck) {
        if (arrayTypeCheck == ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK) {
            // nothing to do
        } else if (arrayTypeCheck == ArrayCopyTypeCheck.HUB_BASED_ARRAY_TYPE_CHECK) {
            Pointer srcHub = loadHub(nonNullSrc);
            Pointer destHub = loadHub(nonNullDest);
            if (probability(SLOW_PATH_PROBABILITY, srcHub != destHub)) {
                DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
            }
        } else if (arrayTypeCheck == ArrayCopyTypeCheck.LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK) {
            Pointer srcHub = loadHub(nonNullSrc);
            Pointer destHub = loadHub(nonNullDest);
            if (probability(SLOW_PATH_PROBABILITY, getReadLayoutHelper(srcHub) != getReadLayoutHelper(destHub))) {
                DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
            }
        } else {
            ReplacementsUtil.staticAssert(false, "unknown array type check ", arrayTypeCheck);
        }
    }

    static class Counters {
        final SnippetCounter checkSuccessCounter;
        final SnippetCounter checkAIOOBECounter;

        final SnippetCounter zeroLengthStaticCounter;
        final SnippetIntegerHistogram lengthHistogram;

        final SnippetCounter systemArraycopyCounter;
        final SnippetCounter systemArraycopyCopiedCounter;

        final SnippetCounter genericArraycopyDifferentTypeCopiedCounter;
        final SnippetCounter genericArraycopyDifferentTypeCounter;

        final SnippetCounter objectCheckcastSameTypeCopiedCounter;
        final SnippetCounter objectCheckcastSameTypeCounter;
        final SnippetCounter objectCheckcastDifferentTypeCopiedCounter;
        final SnippetCounter objectCheckcastDifferentTypeCounter;

        final EnumMap<JavaKind, SnippetCounter> arraycopyCallCounters = new EnumMap<>(JavaKind.class);
        final EnumMap<JavaKind, SnippetCounter> arraycopyCallCopiedCounters = new EnumMap<>(JavaKind.class);

        Counters(SnippetCounter.Group.Factory factory) {
            final Group checkCounters = factory.createSnippetCounterGroup("System.arraycopy checkInputs");
            final Group callCounters = factory.createSnippetCounterGroup("System.arraycopy calls");
            final Group copiedElementsCounters = factory.createSnippetCounterGroup("System.arraycopy copied elements");
            final Group lengthCounters = factory.createSnippetCounterGroup("System.arraycopy with 0-length");

            checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
            checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

            zeroLengthStaticCounter = new SnippetCounter(lengthCounters, "0-length copy static", "calls where the length is statically 0");
            lengthHistogram = new SnippetIntegerHistogram(lengthCounters, 2, "length", "length");

            systemArraycopyCounter = new SnippetCounter(callCounters, "native System.arraycopy", "JNI-based System.arraycopy call");
            systemArraycopyCopiedCounter = new SnippetCounter(copiedElementsCounters, "native System.arraycopy", "JNI-based System.arraycopy call");

            genericArraycopyDifferentTypeCounter = new SnippetCounter(callCounters, "generic[] stub", "generic arraycopy stub");
            genericArraycopyDifferentTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "generic[] stub", "generic arraycopy stub");

            objectCheckcastSameTypeCounter = new SnippetCounter(callCounters, "checkcast object[] (same-type)", "checkcast object[] stub but src.klass == dest.klass Object[] arrays");
            objectCheckcastSameTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "checkcast object[] (same-type)", "checkcast object[] stub but src.klass == dest.klass Object[] arrays");
            objectCheckcastDifferentTypeCounter = new SnippetCounter(callCounters, "checkcast object[] (store-check)", "checkcast object[] stub with store check");
            objectCheckcastDifferentTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "checkcast object[] (store-check)", "checkcast object[] stub with store check");

            createArraycopyCounter(JavaKind.Byte, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Boolean, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Char, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Short, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Int, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Long, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Float, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Double, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Object, callCounters, copiedElementsCounters);
        }

        void createArraycopyCounter(JavaKind kind, Group counters, Group copiedCounters) {
            arraycopyCallCounters.put(kind, new SnippetCounter(counters, kind + "[] stub", "arraycopy call for " + kind + "[] arrays"));
            arraycopyCallCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[] stub", "arraycopy call for " + kind + "[] arrays"));
        }
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {
        private final SnippetInfo arraycopyGenericSnippet;
        private final SnippetInfo arraycopyUnrolledSnippet;
        private final SnippetInfo arraycopyExactSnippet;
        private final SnippetInfo arraycopyZeroLengthSnippet;
        private final SnippetInfo arraycopyCheckcastSnippet;
        private final SnippetInfo arraycopyNativeSnippet;
        private final SnippetInfo checkcastArraycopyWithSlowPathWork;
        private final SnippetInfo genericArraycopyWithSlowPathWork;

        private ResolvedJavaMethod originalArraycopy;
        private final Counters counters;

        public Templates(ArrayCopySnippets receiver, OptionValues options, Iterable<DebugHandlersFactory> factories, Factory factory, Providers providers,
                        SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);
            this.counters = new Counters(factory);

            arraycopyGenericSnippet = snippet(receiver, "arraycopyGenericSnippet");
            arraycopyUnrolledSnippet = snippet(receiver, "arraycopyUnrolledSnippet");
            arraycopyExactSnippet = snippet(receiver, "arraycopyExactSnippet");
            arraycopyZeroLengthSnippet = snippet(receiver, "arraycopyZeroLengthSnippet");
            arraycopyCheckcastSnippet = snippet(receiver, "arraycopyCheckcastSnippet");
            arraycopyNativeSnippet = snippet(null, "arraycopyNativeSnippet");
            checkcastArraycopyWithSlowPathWork = snippet(receiver, "checkcastArraycopyWithSlowPathWork");
            genericArraycopyWithSlowPathWork = snippet(receiver, "genericArraycopyWithSlowPathWork");
        }

        protected SnippetInfo snippet(ArrayCopySnippets receiver, String methodName) {
            SnippetInfo info = snippet(ArrayCopySnippets.class, methodName, originalArraycopy(), receiver, LocationIdentity.any());
            return info;
        }

        public void lower(ArrayCopyNode arraycopy, LoweringTool tool) {
            JavaKind elementKind = selectComponentKind(arraycopy);
            SnippetInfo snippetInfo;
            ArrayCopyTypeCheck arrayTypeCheck;

            ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp(NodeView.DEFAULT));
            ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp(NodeView.DEFAULT));
            if (!canBeArray(srcType) || !canBeArray(destType)) {
                // at least one of the objects is definitely not an array - use the native call
                // right away as the copying will fail anyways
                snippetInfo = arraycopyNativeSnippet;
                arrayTypeCheck = ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK;
            } else {
                ResolvedJavaType srcComponentType = srcType == null ? null : srcType.getComponentType();
                ResolvedJavaType destComponentType = destType == null ? null : destType.getComponentType();

                if (arraycopy.isExact()) {
                    // there is a sufficient type match - we don't need any additional type checks
                    snippetInfo = arraycopyExactSnippet;
                    arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                } else if (srcComponentType == null && destComponentType == null) {
                    // we don't know anything about the types - use the generic copying
                    snippetInfo = arraycopyGenericSnippet;
                    // no need for additional type check to avoid duplicated work
                    arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                } else if (srcComponentType != null && destComponentType != null) {
                    if (!srcComponentType.isPrimitive() && !destComponentType.isPrimitive()) {
                        // it depends on the array content if the copy succeeds - we need
                        // a type check for every store
                        snippetInfo = arraycopyCheckcastSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                    } else {
                        // one object is an object array, the other one is a primitive array.
                        // this copy will always fail - use the native call right away
                        assert !srcComponentType.equals(destComponentType) : "must be handled by arraycopy.isExact()";
                        snippetInfo = arraycopyNativeSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK;
                    }
                } else {
                    ResolvedJavaType nonNullComponentType = srcComponentType != null ? srcComponentType : destComponentType;
                    if (nonNullComponentType.isPrimitive()) {
                        // one involved object is a primitive array - it is sufficient to directly
                        // compare the hub.
                        snippetInfo = arraycopyExactSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.HUB_BASED_ARRAY_TYPE_CHECK;
                        elementKind = nonNullComponentType.getJavaKind();
                    } else {
                        // one involved object is an object array - the other array's element type
                        // may be primitive or object, hence we compare the layout helper.
                        snippetInfo = arraycopyCheckcastSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK;
                    }
                }
            }

            // a few special cases that are easier to handle when all other variables already have a
            // value
            if (snippetInfo != arraycopyNativeSnippet && snippetInfo != arraycopyGenericSnippet && arraycopy.getLength().isConstant() && arraycopy.getLength().asJavaConstant().asLong() == 0) {
                // Copying 0 element between object arrays with conflicting types will not throw an
                // exception - once we pass the preliminary element type checks that we are not
                // mixing arrays of different basic types, ArrayStoreException is only thrown when
                // an *astore would have thrown it. Therefore, copying null between object arrays
                // with conflicting types will also succeed (we do not optimize for such case here).
                snippetInfo = arraycopyZeroLengthSnippet;
            } else if (snippetInfo == arraycopyExactSnippet && shouldUnroll(arraycopy.getLength())) {
                snippetInfo = arraycopyUnrolledSnippet;
            }

            // create the snippet
            Arguments args = new Arguments(snippetInfo, arraycopy.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());
            if (snippetInfo != arraycopyNativeSnippet) {
                assert arrayTypeCheck != ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK;
                args.addConst("arrayTypeCheck", arrayTypeCheck);
            }
            Object locationIdentity = arraycopy.killsAnyLocation() ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(elementKind);
            if (snippetInfo == arraycopyUnrolledSnippet) {
                args.addConst("elementKind", elementKind != null ? elementKind : JavaKind.Illegal);
                args.addConst("locationIdentity", locationIdentity);
                args.addConst("unrolledLength", arraycopy.getLength().asJavaConstant().asInt());
            }
            if (snippetInfo == arraycopyExactSnippet) {
                assert elementKind != null;
                args.addConst("elementKind", elementKind);
                args.addConst("locationIdentity", locationIdentity);
                args.addConst("elementKindCounter", counters.arraycopyCallCounters.get(elementKind));
                args.addConst("elementKindCopiedCounter", counters.arraycopyCallCopiedCounters.get(elementKind));
            }
            args.addConst("counters", counters);
            if (snippetInfo == arraycopyCheckcastSnippet) {
                args.addConst("workSnippet", checkcastArraycopyWithSlowPathWork);
                args.addConst("elementKind", JavaKind.Illegal);
            }
            if (snippetInfo == arraycopyGenericSnippet) {
                args.addConst("workSnippet", genericArraycopyWithSlowPathWork);
                args.addConst("elementKind", JavaKind.Illegal);
            }

            instantiate(args, arraycopy);
        }

        public void lower(ArrayCopyWithSlowPathNode arraycopy, LoweringTool tool) {
            StructuredGraph graph = arraycopy.graph();
            if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                // if an arraycopy contains a slow path, we can't lower it right away
                return;
            }

            SnippetInfo snippetInfo = arraycopy.getSnippet();
            Arguments args = new Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());
            args.addConst("counters", counters);
            instantiate(args, arraycopy);
        }

        private static boolean canBeArray(ResolvedJavaType type) {
            return type == null || type.isJavaLangObject() || type.isArray();
        }

        public static JavaKind selectComponentKind(BasicArrayCopyNode arraycopy) {
            ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp(NodeView.DEFAULT));
            ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp(NodeView.DEFAULT));

            if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
                return null;
            }
            if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
                return null;
            }
            if (!arraycopy.isExact()) {
                return null;
            }
            return srcType.getComponentType().getJavaKind();
        }

        private static boolean shouldUnroll(ValueNode length) {
            return length.isConstant() && length.asJavaConstant().asInt() <= 8 && length.asJavaConstant().asInt() != 0;
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
            SnippetTemplate template = template(arraycopy, args);
            UnmodifiableEconomicMap<Node, Node> replacements = template.instantiate(providers.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args, false);
            for (Node originalNode : replacements.getKeys()) {
                if (originalNode instanceof InvokeNode) {
                    InvokeNode invoke = (InvokeNode) replacements.get(originalNode);
                    assert invoke.asNode().graph() == graph;
                    CallTargetNode call = invoke.callTarget();

                    if (!call.targetMethod().equals(originalArraycopy)) {
                        throw new GraalError("unexpected invoke %s in snippet", call.targetMethod());
                    }
                    // Here we need to fix the bci of the invoke
                    InvokeNode newInvoke = invoke.replaceWithNewBci(arraycopy.getBci());
                    newInvoke.setStateDuring(null);
                    newInvoke.setStateAfter(null);
                    if (arraycopy.stateDuring() != null) {
                        newInvoke.setStateDuring(arraycopy.stateDuring());
                    } else {
                        assert arraycopy.stateAfter() != null : arraycopy;
                        newInvoke.setStateAfter(arraycopy.stateAfter());
                    }
                } else if (originalNode instanceof InvokeWithExceptionNode) {
                    throw new GraalError("unexpected invoke with exception %s in snippet", originalNode);
                } else if (originalNode instanceof ArrayCopyWithSlowPathNode) {
                    ArrayCopyWithSlowPathNode slowPath = (ArrayCopyWithSlowPathNode) replacements.get(originalNode);
                    assert arraycopy.stateAfter() != null : arraycopy;
                    assert slowPath.stateAfter() == arraycopy.stateAfter();
                    slowPath.setBci(arraycopy.getBci());
                }
            }
            GraphUtil.killCFG(arraycopy);
        }

        private ResolvedJavaMethod originalArraycopy() throws GraalError {
            if (originalArraycopy == null) {
                try {
                    originalArraycopy = findMethod(providers.getMetaAccess(), System.class, "arraycopy");
                } catch (SecurityException e) {
                    throw new GraalError(e);
                }
            }
            return originalArraycopy;
        }
    }
}
