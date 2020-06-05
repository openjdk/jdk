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


package org.graalvm.compiler.replacements;

import static java.util.FormattableFlags.ALTERNATE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.debug.DebugContext.applyFormattingFlagsAndWidth;
import static org.graalvm.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static org.graalvm.compiler.graph.iterators.NodePredicates.isNotA;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formattable;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.MapCursor;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptBefore;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InliningLog;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode.Placeholder;
import org.graalvm.compiler.nodes.PiNode.PlaceholderStamp;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.UnreachableControlSinkNode;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.VirtualState.NodePositionClosure;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.AbstractBoxingNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryAnchorNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MemoryMap;
import org.graalvm.compiler.nodes.memory.MemoryMapNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.MemoryEdgeProxy;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase.MemoryMapImpl;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.SnippetFrameStateAssignment;
import org.graalvm.compiler.phases.common.SnippetFrameStateAssignment.NodeStateAssignment;
import org.graalvm.compiler.phases.common.SnippetFrameStateAssignment.SnippetFrameStateAssignmentClosure;
import org.graalvm.compiler.phases.common.WriteBarrierAdditionPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;
import org.graalvm.compiler.replacements.nodes.LoadSnippetVarargParameterNode;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.util.CollectionsUtil;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordBase;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A snippet template is a graph created by parsing a snippet method and then specialized by binding
 * constants to the snippet's {@link ConstantParameter} parameters.
 *
 * Snippet templates can be managed in a cache maintained by {@link AbstractTemplates}.
 */
public class SnippetTemplate {

    private boolean mayRemoveLocation = false;

    /**
     * Holds the {@link ResolvedJavaMethod} of the snippet, together with some information about the
     * method that needs to be computed only once. The {@link SnippetInfo} should be created once
     * per snippet and then cached.
     */
    public abstract static class SnippetInfo {

        protected final ResolvedJavaMethod method;
        protected final ResolvedJavaMethod original;
        protected final LocationIdentity[] privateLocations;
        protected final Object receiver;

        public Object getReceiver() {
            return receiver;
        }

        boolean hasReceiver() {
            assert hasReceiver(method) == (receiver != null) : "Snippet with the receiver must have it set as constant. Snippet: " + this;
            return hasReceiver(method);
        }

        static boolean hasReceiver(ResolvedJavaMethod method) {
            return method.hasReceiver();
        }

        /**
         * Times instantiations of all templates derived from this snippet.
         */
        private final TimerKey instantiationTimer;

        /**
         * Counts instantiations of all templates derived from this snippet.
         */
        private final CounterKey instantiationCounter;

        protected abstract SnippetParameterInfo info();

        protected SnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver) {
            this.method = method;
            this.original = original;
            this.privateLocations = privateLocations;
            instantiationCounter = DebugContext.counter("SnippetInstantiationCount[%s]", method.getName());
            instantiationTimer = DebugContext.timer("SnippetInstantiationTime[%s]", method.getName());
            this.receiver = receiver;
        }

        public ResolvedJavaMethod getMethod() {
            return method;
        }

        public int getParameterCount() {
            return info().getParameterCount();
        }

        public boolean isConstantParameter(int paramIdx) {
            return info().isConstantParameter(paramIdx);
        }

        public boolean isVarargsParameter(int paramIdx) {
            return info().isVarargsParameter(paramIdx);
        }

        public boolean isNonNullParameter(int paramIdx) {
            return info().isNonNullParameter(paramIdx);
        }

        public String getParameterName(int paramIdx) {
            return info().getParameterName(paramIdx);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":" + method.format("%h.%n");
        }
    }

    protected static class LazySnippetInfo extends SnippetInfo {
        protected final AtomicReference<SnippetParameterInfo> lazy = new AtomicReference<>(null);

        protected LazySnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver) {
            super(method, original, privateLocations, receiver);
        }

        @Override
        protected SnippetParameterInfo info() {
            if (lazy.get() == null) {
                lazy.compareAndSet(null, new SnippetParameterInfo(method));
            }
            return lazy.get();
        }
    }

    protected static class EagerSnippetInfo extends SnippetInfo {
        protected final SnippetParameterInfo snippetParameterInfo;

        protected EagerSnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver, SnippetParameterInfo snippetParameterInfo) {
            super(method, original, privateLocations, receiver);
            this.snippetParameterInfo = snippetParameterInfo;
        }

        @Override
        protected SnippetParameterInfo info() {
            return snippetParameterInfo;
        }
    }

    /**
     * Values that are bound to the snippet method parameters. The methods {@link #add},
     * {@link #addConst}, and {@link #addVarargs} must be called in the same order as in the
     * signature of the snippet method. The parameter name is passed to the add methods for
     * assertion checking, i.e., to enforce that the order matches. Which method needs to be called
     * depends on the annotation of the snippet method parameter:
     * <ul>
     * <li>Use {@link #add} for a parameter without an annotation. The value is bound when the
     * {@link SnippetTemplate} is {@link SnippetTemplate#instantiate instantiated}.
     * <li>Use {@link #addConst} for a parameter annotated with {@link ConstantParameter}. The value
     * is bound when the {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created}.
     * <li>Use {@link #addVarargs} for an array parameter annotated with {@link VarargsParameter}. A
     * separate {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created} for every
     * distinct array length. The actual values are bound when the {@link SnippetTemplate} is
     * {@link SnippetTemplate#instantiate instantiated}
     * </ul>
     */
    public static class Arguments implements Formattable {

        protected final SnippetInfo info;
        protected final CacheKey cacheKey;
        protected final Object[] values;
        protected final Stamp[] constStamps;
        protected boolean cacheable;

        protected int nextParamIdx;

        public Arguments(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.info = info;
            this.cacheKey = new CacheKey(info, guardsStage, loweringStage);
            this.values = new Object[info.getParameterCount()];
            this.constStamps = new Stamp[info.getParameterCount()];
            this.cacheable = true;
            if (info.hasReceiver()) {
                addConst("this", info.getReceiver());
            }
        }

        public Arguments add(String name, Object value) {
            assert check(name, false, false);
            values[nextParamIdx] = value;
            nextParamIdx++;
            return this;
        }

        public Arguments addConst(String name, Object value) {
            assert value != null;
            return addConst(name, value, null);
        }

        public Arguments addConst(String name, Object value, Stamp stamp) {
            assert check(name, true, false);
            values[nextParamIdx] = value;
            constStamps[nextParamIdx] = stamp;
            cacheKey.setParam(nextParamIdx, value);
            nextParamIdx++;
            return this;
        }

        public Arguments addVarargs(String name, Class<?> componentType, Stamp argStamp, Object value) {
            assert check(name, false, true);
            Varargs varargs = new Varargs(componentType, argStamp, value);
            values[nextParamIdx] = varargs;
            // A separate template is necessary for every distinct array length
            cacheKey.setParam(nextParamIdx, varargs.length);
            nextParamIdx++;
            return this;
        }

        public void setCacheable(boolean cacheable) {
            this.cacheable = cacheable;
        }

        private boolean check(String name, boolean constParam, boolean varargsParam) {
            assert nextParamIdx < info.getParameterCount() : "too many parameters: " + name + "  " + this;
            assert info.getParameterName(nextParamIdx) == null || info.getParameterName(nextParamIdx).equals(name) : "wrong parameter name at " + nextParamIdx + " : " + name + "  " + this;
            assert constParam == info.isConstantParameter(nextParamIdx) : "Parameter " + (constParam ? "not " : "") + "annotated with @" + ConstantParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            assert varargsParam == info.isVarargsParameter(nextParamIdx) : "Parameter " + (varargsParam ? "not " : "") + "annotated with @" + VarargsParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("Parameters<").append(info.method.format("%h.%n")).append(" [");
            String sep = "";
            for (int i = 0; i < info.getParameterCount(); i++) {
                result.append(sep);
                if (info.isConstantParameter(i)) {
                    result.append("const ");
                } else if (info.isVarargsParameter(i)) {
                    result.append("varargs ");
                }
                result.append(info.getParameterName(i)).append(" = ").append(values[i]);
                sep = ", ";
            }
            result.append(">");
            return result.toString();
        }

        @Override
        public void formatTo(Formatter formatter, int flags, int width, int precision) {
            if ((flags & ALTERNATE) == 0) {
                formatter.format(applyFormattingFlagsAndWidth(toString(), flags, width));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(info.method.getName()).append('(');
                String sep = "";
                for (int i = 0; i < info.getParameterCount(); i++) {
                    if (info.isConstantParameter(i)) {
                        sb.append(sep);
                        if (info.getParameterName(i) != null) {
                            sb.append(info.getParameterName(i));
                        } else {
                            sb.append(i);
                        }
                        sb.append('=').append(values[i]);
                        sep = ", ";
                    }
                }
                sb.append(")");
                String string = sb.toString();
                if (string.indexOf('%') != -1) {
                    // Quote any % signs
                    string = string.replace("%", "%%");
                }
                formatter.format(applyFormattingFlagsAndWidth(string, flags & ~ALTERNATE, width));
            }
        }
    }

    /**
     * Wrapper for the prototype value of a {@linkplain VarargsParameter varargs} parameter.
     */
    static class Varargs {

        protected final Class<?> componentType;
        protected final Stamp stamp;
        protected final Object value;
        protected final int length;

        protected Varargs(Class<?> componentType, Stamp stamp, Object value) {
            this.componentType = componentType;
            this.stamp = stamp;
            this.value = value;
            if (value instanceof List) {
                this.length = ((List<?>) value).size();
            } else {
                this.length = Array.getLength(value);
            }
        }

        @Override
        public String toString() {
            if (value instanceof boolean[]) {
                return Arrays.toString((boolean[]) value);
            }
            if (value instanceof byte[]) {
                return Arrays.toString((byte[]) value);
            }
            if (value instanceof char[]) {
                return Arrays.toString((char[]) value);
            }
            if (value instanceof short[]) {
                return Arrays.toString((short[]) value);
            }
            if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            }
            if (value instanceof long[]) {
                return Arrays.toString((long[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((float[]) value);
            }
            if (value instanceof double[]) {
                return Arrays.toString((double[]) value);
            }
            if (value instanceof Object[]) {
                return Arrays.toString((Object[]) value);
            }
            return String.valueOf(value);
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class VarargsPlaceholderNode extends FloatingNode implements ArrayLengthProvider {

        public static final NodeClass<VarargsPlaceholderNode> TYPE = NodeClass.create(VarargsPlaceholderNode.class);
        protected final Varargs varargs;

        protected VarargsPlaceholderNode(Varargs varargs, MetaAccessProvider metaAccess) {
            super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(varargs.componentType).getArrayClass())));
            this.varargs = varargs;
        }

        @Override
        public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
            return ConstantNode.forInt(varargs.length);
        }
    }

    static class CacheKey {

        private final ResolvedJavaMethod method;
        private final Object[] values;
        private final GuardsStage guardsStage;
        private final LoweringTool.LoweringStage loweringStage;
        private int hash;

        protected CacheKey(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.method = info.method;
            this.guardsStage = guardsStage;
            this.loweringStage = loweringStage;
            this.values = new Object[info.getParameterCount()];
            this.hash = info.method.hashCode() + 31 * guardsStage.ordinal();
        }

        protected void setParam(int paramIdx, Object value) {
            values[paramIdx] = value;
            hash = (hash * 31) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            if (!method.equals(other.method)) {
                return false;
            }
            if (guardsStage != other.guardsStage || loweringStage != other.loweringStage) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && !values[i].equals(other.values[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final TimerKey SnippetTemplateCreationTime = DebugContext.timer("SnippetTemplateCreationTime");
    private static final CounterKey SnippetTemplates = DebugContext.counter("SnippetTemplateCount");

    static class Options {
        @Option(help = "Use a LRU cache for snippet templates.")//
        public static final OptionKey<Boolean> UseSnippetTemplateCache = new OptionKey<>(true);

        @Option(help = "")//
        static final OptionKey<Integer> MaxTemplatesPerSnippet = new OptionKey<>(50);
    }

    /**
     * Base class for snippet classes. It provides a cache for {@link SnippetTemplate}s.
     */
    public abstract static class AbstractTemplates implements org.graalvm.compiler.api.replacements.SnippetTemplateCache {

        protected final OptionValues options;
        protected final Providers providers;
        protected final SnippetReflectionProvider snippetReflection;
        protected final Iterable<DebugHandlersFactory> factories;
        protected final TargetDescription target;
        private final Map<CacheKey, SnippetTemplate> templates;

        protected AbstractTemplates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            this.options = options;
            this.providers = providers;
            this.snippetReflection = snippetReflection;
            this.target = target;
            this.factories = factories;
            if (Options.UseSnippetTemplateCache.getValue(options)) {
                int size = Options.MaxTemplatesPerSnippet.getValue(options);
                this.templates = Collections.synchronizedMap(new LRUCache<>(size, size));
            } else {
                this.templates = null;
            }
        }

        public Providers getProviders() {
            return providers;
        }

        public static Method findMethod(Class<? extends Snippets> declaringClass, String methodName, Method except) {
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && !m.equals(except)) {
                    return m;
                }
            }
            return null;
        }

        public static ResolvedJavaMethod findMethod(MetaAccessProvider metaAccess, Class<?> declaringClass, String methodName) {
            ResolvedJavaType type = metaAccess.lookupJavaType(declaringClass);
            ResolvedJavaMethod result = null;
            for (ResolvedJavaMethod m : type.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    if (!Assertions.assertionsEnabled()) {
                        return m;
                    } else {
                        assert result == null : "multiple definitions found";
                        result = m;
                    }
                }
            }
            if (result == null) {
                throw new GraalError("Could not find method in " + declaringClass + " named " + methodName);
            }
            return result;
        }

        protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, LocationIdentity... initialPrivateLocations) {
            return snippet(declaringClass, methodName, null, null, initialPrivateLocations);
        }

        /**
         * Finds the unique method in {@code declaringClass} named {@code methodName} annotated by
         * {@link Snippet} and returns a {@link SnippetInfo} value describing it. There must be
         * exactly one snippet method in {@code declaringClass} with a given name.
         */
        protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, ResolvedJavaMethod original, Object receiver,
                        LocationIdentity... initialPrivateLocations) {
            assert methodName != null;
            ResolvedJavaMethod javaMethod = findMethod(providers.getMetaAccess(), declaringClass, methodName);
            assert javaMethod != null : "did not find @" + Snippet.class.getSimpleName() + " method in " + declaringClass + " named " + methodName;
            providers.getReplacements().registerSnippet(javaMethod, original, receiver, GraalOptions.TrackNodeSourcePosition.getValue(options), options);
            LocationIdentity[] privateLocations = GraalOptions.SnippetCounters.getValue(options) ? SnippetCounterNode.addSnippetCounters(initialPrivateLocations) : initialPrivateLocations;
            if (IS_IN_NATIVE_IMAGE || GraalOptions.EagerSnippets.getValue(options)) {
                SnippetParameterInfo snippetParameterInfo = providers.getReplacements().getSnippetParameterInfo(javaMethod);
                return new EagerSnippetInfo(javaMethod, original, privateLocations, receiver, snippetParameterInfo);
            } else {
                return new LazySnippetInfo(javaMethod, original, privateLocations, receiver);
            }
        }

        static final AtomicInteger nextSnippetTemplateId = new AtomicInteger();

        private DebugContext openDebugContext(DebugContext outer, Arguments args) {
            if (DebugStubsAndSnippets.getValue(options)) {
                Description description = new Description(args.cacheKey.method, "SnippetTemplate_" + nextSnippetTemplateId.incrementAndGet());
                return new Builder(options, factories).globalMetrics(outer.getGlobalMetrics()).description(description).build();
            }
            return DebugContext.disabled(options);
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        @SuppressWarnings("try")
        public SnippetTemplate template(ValueNode replacee, final Arguments args) {
            StructuredGraph graph = replacee.graph();
            DebugContext outer = graph.getDebug();
            SnippetTemplate template = Options.UseSnippetTemplateCache.getValue(options) && args.cacheable ? templates.get(args.cacheKey) : null;
            if (template == null || (graph.trackNodeSourcePosition() && !template.snippet.trackNodeSourcePosition())) {
                try (DebugContext debug = openDebugContext(outer, args)) {
                    try (DebugCloseable a = SnippetTemplateCreationTime.start(debug); DebugContext.Scope s = debug.scope("SnippetSpecialization", args.info.method)) {
                        SnippetTemplates.increment(debug);
                        OptionValues snippetOptions = new OptionValues(options, GraalOptions.TraceInlining, GraalOptions.TraceInliningForStubsAndSnippets.getValue(options));
                        template = new SnippetTemplate(snippetOptions, debug, providers, snippetReflection, args, graph.trackNodeSourcePosition(), replacee, createMidTierPhases());
                        if (Options.UseSnippetTemplateCache.getValue(snippetOptions) && args.cacheable) {
                            templates.put(args.cacheKey, template);
                        }
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                }
            }
            return template;
        }

        /**
         * Additional mid-tier optimization phases to run on the snippet graph during
         * {@link #template} creation. These phases are only run for snippets lowered in the
         * low-tier lowering.
         */
        protected PhaseSuite<Providers> createMidTierPhases() {
            return null;
        }
    }

    private static final class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int maxCacheSize;

        LRUCache(int initialCapacity, int maxCacheSize) {
            super(initialCapacity, 0.75F, true);
            this.maxCacheSize = maxCacheSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxCacheSize;
        }
    }

    // These values must be compared with equals() not '==' to support replay compilation.
    private static final Object UNUSED_PARAMETER = "UNUSED_PARAMETER";
    private static final Object CONSTANT_PARAMETER = "CONSTANT_PARAMETER";

    /**
     * Determines if any parameter of a given method is annotated with {@link ConstantParameter}.
     */
    public static boolean hasConstantParameter(ResolvedJavaMethod method) {
        for (ConstantParameter p : method.getParameterAnnotations(ConstantParameter.class)) {
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    private final SnippetReflectionProvider snippetReflection;

    /**
     * Creates a snippet template.
     */
    @SuppressWarnings("try")
    protected SnippetTemplate(OptionValues options, DebugContext debug, final Providers providers, SnippetReflectionProvider snippetReflection, Arguments args, boolean trackNodeSourcePosition,
                    Node replacee, PhaseSuite<Providers> midTierPhases) {
        this.snippetReflection = snippetReflection;
        this.info = args.info;

        Object[] constantArgs = getConstantArgs(args);
        boolean shouldTrackNodeSourcePosition1 = trackNodeSourcePosition || (providers.getCodeCache() != null && providers.getCodeCache().shouldDebugNonSafepoints());
        StructuredGraph snippetGraph = providers.getReplacements().getSnippet(args.info.method, args.info.original, constantArgs, shouldTrackNodeSourcePosition1, replacee.getNodeSourcePosition(),
                        options);

        ResolvedJavaMethod method = snippetGraph.method();
        Signature signature = method.getSignature();

        // Copy snippet graph, replacing constant parameters with given arguments
        final StructuredGraph snippetCopy = new StructuredGraph.Builder(options, debug).name(snippetGraph.name).method(snippetGraph.method()).trackNodeSourcePosition(
                        snippetGraph.trackNodeSourcePosition()).setIsSubstitution(true).build();
        snippetCopy.setGuardsStage(snippetGraph.getGuardsStage());
        assert !GraalOptions.TrackNodeSourcePosition.getValue(options) || snippetCopy.trackNodeSourcePosition();
        try (DebugContext.Scope scope = debug.scope("SpecializeSnippet", snippetCopy)) {
            if (!snippetGraph.isUnsafeAccessTrackingEnabled()) {
                snippetCopy.disableUnsafeAccessTracking();
            }
            assert snippetCopy.isSubstitution();

            EconomicMap<Node, Node> nodeReplacements = EconomicMap.create(Equivalence.IDENTITY);
            nodeReplacements.put(snippetGraph.start(), snippetCopy.start());

            MetaAccessProvider metaAccess = providers.getMetaAccess();
            assert checkTemplate(metaAccess, args, method, signature);

            int parameterCount = args.info.getParameterCount();
            VarargsPlaceholderNode[] placeholders = new VarargsPlaceholderNode[parameterCount];

            for (int i = 0; i < parameterCount; i++) {
                ParameterNode parameter = snippetGraph.getParameter(i);
                if (parameter != null) {
                    if (args.info.isConstantParameter(i)) {
                        Object arg = args.values[i];
                        JavaKind kind = signature.getParameterKind(i);
                        ConstantNode constantNode;
                        if (arg instanceof Constant) {
                            Stamp stamp = args.constStamps[i];
                            if (stamp == null) {
                                assert arg instanceof JavaConstant : "could not determine type of constant " + arg;
                                constantNode = ConstantNode.forConstant((JavaConstant) arg, metaAccess, snippetCopy);
                            } else {
                                constantNode = ConstantNode.forConstant(stamp, (Constant) arg, metaAccess, snippetCopy);
                            }
                        } else {
                            constantNode = ConstantNode.forConstant(snippetReflection.forBoxed(kind, arg), metaAccess, snippetCopy);
                        }
                        nodeReplacements.put(parameter, constantNode);
                    } else if (args.info.isVarargsParameter(i)) {
                        Varargs varargs = (Varargs) args.values[i];
                        VarargsPlaceholderNode placeholder = snippetCopy.unique(new VarargsPlaceholderNode(varargs, providers.getMetaAccess()));
                        nodeReplacements.put(parameter, placeholder);
                        placeholders[i] = placeholder;
                    } else if (args.info.isNonNullParameter(i)) {
                        parameter.setStamp(parameter.stamp(NodeView.DEFAULT).join(StampFactory.objectNonNull()));
                    }
                }
            }
            try (InliningLog.UpdateScope updateScope = snippetCopy.getInliningLog().openDefaultUpdateScope()) {
                UnmodifiableEconomicMap<Node, Node> duplicates = snippetCopy.addDuplicates(snippetGraph.getNodes(), snippetGraph, snippetGraph.getNodeCount(), nodeReplacements);
                if (updateScope != null) {
                    snippetCopy.getInliningLog().replaceLog(duplicates, snippetGraph.getInliningLog());
                }
            }

            debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "Before specialization");

            // Gather the template parameters
            parameters = new Object[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                if (args.info.isConstantParameter(i)) {
                    parameters[i] = CONSTANT_PARAMETER;
                } else if (args.info.isVarargsParameter(i)) {
                    assert snippetCopy.getParameter(i) == null;
                    Varargs varargs = (Varargs) args.values[i];
                    int length = varargs.length;
                    ParameterNode[] params = new ParameterNode[length];
                    Stamp stamp = varargs.stamp;
                    for (int j = 0; j < length; j++) {
                        // Use a decimal friendly numbering make it more obvious how values map
                        assert parameterCount < 10000;
                        int idx = (i + 1) * 10000 + j;
                        assert idx >= parameterCount : "collision in parameter numbering";
                        ParameterNode local = snippetCopy.addOrUnique(new ParameterNode(idx, StampPair.createSingle(stamp)));
                        params[j] = local;
                    }
                    parameters[i] = params;

                    VarargsPlaceholderNode placeholder = placeholders[i];
                    if (placeholder != null) {
                        for (Node usage : placeholder.usages().snapshot()) {
                            if (usage instanceof LoadIndexedNode) {
                                LoadIndexedNode loadIndexed = (LoadIndexedNode) usage;
                                debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "Before replacing %s", loadIndexed);
                                LoadSnippetVarargParameterNode loadSnippetParameter = snippetCopy.add(
                                                new LoadSnippetVarargParameterNode(params, loadIndexed.index(), loadIndexed.stamp(NodeView.DEFAULT)));
                                snippetCopy.replaceFixedWithFixed(loadIndexed, loadSnippetParameter);
                                debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "After replacing %s", loadIndexed);
                            } else if (usage instanceof StoreIndexedNode) {
                                /*
                                 * The template lowering doesn't really treat this as an array so
                                 * you can't store back into the varargs. Allocate your own array if
                                 * you really need this and EA should eliminate it.
                                 */
                                throw new GraalError("Can't store into VarargsParameter array");
                            }
                        }
                    }
                } else {
                    ParameterNode local = snippetCopy.getParameter(i);
                    if (local == null) {
                        // Parameter value was eliminated
                        parameters[i] = UNUSED_PARAMETER;
                    } else {
                        parameters[i] = local;
                    }
                }
            }

            explodeLoops(snippetCopy, providers);

            List<UnwindNode> unwindNodes = snippetCopy.getNodes().filter(UnwindNode.class).snapshot();
            if (unwindNodes.size() == 0) {
                unwindPath = null;
            } else if (unwindNodes.size() > 1) {
                throw GraalError.shouldNotReachHere("Graph has more than one UnwindNode");
            } else {
                UnwindNode unwindNode = unwindNodes.get(0);
                GraalError.guarantee(unwindNode.predecessor() instanceof ExceptionObjectNode, "Currently only a single direct exception unwind path is supported in snippets");
                ExceptionObjectNode exceptionObjectNode = (ExceptionObjectNode) unwindNode.predecessor();

                /*
                 * Replace the path to the UnwindNode with a stub to avoid any optimizations or
                 * lowerings to modify it. Also removes the FrameState of the ExceptionObjectNode.
                 */
                unwindPath = snippetCopy.add(new BeginNode());
                exceptionObjectNode.replaceAtPredecessor(unwindPath);
                GraphUtil.killCFG(exceptionObjectNode);
                unwindPath.setNext(snippetCopy.add(new UnreachableControlSinkNode()));
            }

            CanonicalizerPhase canonicalizer;
            if (GraalOptions.ImmutableCode.getValue(snippetCopy.getOptions())) {
                canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
            } else {
                canonicalizer = CanonicalizerPhase.create();
            }

            /*-
             * Mirror the behavior of normal compilations here (without aggressive optimizations
             * but with the most important phases)
             *
             * (1) Run some important high-tier optimizations
             * (2) Perform high-tier lowering
             * (3) If lowering stage is != high tier --> Run important mid tier phases --> guard lowering
             * (4) perform mid-tier lowering
             * (5) If lowering stage is != mid tier --> Run important mid tier phases after lowering --> write barrier addition
             * (6) perform low-tier lowering
             * (7) Run final phases --> dead code elimination
             *
             */
            // (1)
            GuardsStage guardsStage = args.cacheKey.guardsStage;
            boolean needsPEA = false;
            boolean needsCE = false;
            LoweringTool.LoweringStage loweringStage = args.cacheKey.loweringStage;
            for (Node n : snippetCopy.getNodes()) {
                if (n instanceof AbstractNewObjectNode || n instanceof AbstractBoxingNode) {
                    needsPEA = true;
                    break;
                } else if (n instanceof LogicNode) {
                    needsCE = true;
                }
            }
            if (needsPEA) {
                /*
                 * Certain snippets do not have real side-effects if they do allocations, i.e., they
                 * only access the init_location, therefore we might require a late schedule to get
                 * that. However, snippets are small so the compile time cost for this can be
                 * ignored.
                 *
                 * An example of a snippet doing allocation are the boxing snippets since they only
                 * write to newly allocated memory which is not yet visible to the interpreter until
                 * the entire allocation escapes. See LocationIdentity#INIT_LOCATION and
                 * WriteNode#hasSideEffect for details.
                 */
                new PartialEscapePhase(true, true, canonicalizer, null, options, SchedulingStrategy.LATEST).apply(snippetCopy, providers);
            }
            if (needsCE) {
                new IterativeConditionalEliminationPhase(canonicalizer, false).apply(snippetCopy, providers);
            }
            // (2)
            try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_HIGH_TIER", snippetCopy)) {
                new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(snippetCopy, providers);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            if (loweringStage != LoweringTool.StandardLoweringStage.HIGH_TIER) {
                // (3)
                assert !guardsStage.allowsFloatingGuards() : guardsStage;
                // only create memory map nodes if we need the memory graph
                new IncrementalCanonicalizerPhase<>(canonicalizer, new FloatingReadPhase(true, true)).apply(snippetCopy, providers);
                new GuardLoweringPhase().apply(snippetCopy, providers);
                new IncrementalCanonicalizerPhase<>(canonicalizer, new RemoveValueProxyPhase()).apply(snippetCopy, providers);
                // (4)
                try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_MID_TIER", snippetCopy)) {
                    new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER).apply(snippetCopy, providers);
                    snippetCopy.setGuardsStage(GuardsStage.AFTER_FSA);
                    snippetCopy.disableFrameStateVerification();
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
                if (loweringStage != LoweringTool.StandardLoweringStage.MID_TIER) {
                    // (5)
                    if (midTierPhases != null) {
                        midTierPhases.apply(snippetCopy, providers);
                    }
                    new WriteBarrierAdditionPhase().apply(snippetCopy, providers);
                    try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_LOW_TIER", snippetCopy)) {
                        // (6)
                        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER).apply(snippetCopy, providers);
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                    // (7)
                    new DeadCodeEliminationPhase(Required).apply(snippetCopy);
                }
            }
            assert checkAllVarargPlaceholdersAreDeleted(parameterCount, placeholders);

            ArrayList<StateSplit> curSideEffectNodes = new ArrayList<>();
            ArrayList<DeoptimizingNode> curDeoptNodes = new ArrayList<>();
            ArrayList<ValueNode> curPlaceholderStampedNodes = new ArrayList<>();

            boolean containsMerge = false;
            boolean containsLoopExit = false;

            for (Node node : snippetCopy.getNodes()) {
                if (node instanceof AbstractMergeNode) {
                    containsMerge = true;
                }
                if (node instanceof LoopExitNode) {
                    containsLoopExit = true;
                }
                if (node instanceof ValueNode) {
                    ValueNode valueNode = (ValueNode) node;
                    if (valueNode.stamp(NodeView.DEFAULT) == PlaceholderStamp.singleton()) {
                        curPlaceholderStampedNodes.add(valueNode);
                    }
                }

                if (node instanceof StateSplit) {
                    StateSplit stateSplit = (StateSplit) node;
                    FrameState frameState = stateSplit.stateAfter();
                    if (stateSplit.hasSideEffect()) {
                        curSideEffectNodes.add((StateSplit) node);
                    }
                    if (frameState != null) {
                        stateSplit.setStateAfter(null);
                    }
                }
                if (node instanceof DeoptimizingNode) {
                    DeoptimizingNode deoptNode = (DeoptimizingNode) node;
                    if (deoptNode.canDeoptimize()) {
                        curDeoptNodes.add(deoptNode);
                    }
                }
            }
            this.snippet = snippetCopy;
            StartNode entryPointNode = snippet.start();
            MemoryAnchorNode anchor = snippetCopy.add(new MemoryAnchorNode(info.privateLocations));
            snippetCopy.start().replaceAtUsages(anchor, InputType.Memory);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, snippetCopy, "After adding memory anchor %s", anchor);
            if (anchor.hasNoUsages()) {
                anchor.safeDelete();
                this.memoryAnchor = null;
            } else {
                // Find out if all the return memory maps point to the anchor (i.e., there's no
                // kill
                // anywhere)
                boolean needsMemoryMaps = false;
                for (ReturnNode retNode : snippet.getNodes(ReturnNode.TYPE)) {
                    MemoryMapNode memoryMap = retNode.getMemoryMap();
                    if (memoryMap.getLocations().size() > 1 || memoryMap.getLastLocationAccess(LocationIdentity.any()) != anchor) {
                        needsMemoryMaps = true;
                        break;
                    }
                }
                boolean needsAnchor;
                if (needsMemoryMaps) {
                    needsAnchor = true;
                } else {
                    // Check that all those memory maps where the only usages of the anchor
                    needsAnchor = anchor.usages().filter(isNotA(MemoryMapNode.class)).isNotEmpty();
                    // Remove the useless memory map
                    MemoryMapNode memoryMap = null;
                    for (ReturnNode retNode : snippet.getNodes(ReturnNode.TYPE)) {
                        if (memoryMap == null) {
                            memoryMap = retNode.getMemoryMap();
                        } else {
                            assert memoryMap == retNode.getMemoryMap();
                        }
                        retNode.setMemoryMap(null);
                    }
                    if (memoryMap != null) {
                        memoryMap.safeDelete();
                    }
                }
                if (needsAnchor) {
                    snippetCopy.addAfterFixed(snippetCopy.start(), anchor);
                    this.memoryAnchor = anchor;
                } else {
                    anchor.safeDelete();
                    this.memoryAnchor = null;
                }
            }
            debug.dump(DebugContext.INFO_LEVEL, snippet, "SnippetTemplate after fixing memory anchoring");
            List<ReturnNode> returnNodes = snippet.getNodes(ReturnNode.TYPE).snapshot();
            if (returnNodes.isEmpty()) {
                this.returnNode = null;
            } else if (returnNodes.size() == 1) {
                this.returnNode = returnNodes.get(0);
            } else {
                AbstractMergeNode merge = snippet.add(new MergeNode());
                List<MemoryMapNode> memMaps = new ArrayList<>();
                for (ReturnNode retNode : returnNodes) {
                    MemoryMapNode memoryMapNode = retNode.getMemoryMap();
                    if (memoryMapNode != null) {
                        memMaps.add(memoryMapNode);
                    }
                }
                containsMerge = true;
                ValueNode returnValue = InliningUtil.mergeReturns(merge, returnNodes);
                this.returnNode = snippet.add(new ReturnNode(returnValue));
                if (!memMaps.isEmpty()) {
                    MemoryMapImpl mmap = FloatingReadPhase.mergeMemoryMaps(merge, memMaps);
                    MemoryMapNode memoryMap = snippet.unique(new MemoryMapNode(mmap.getMap()));
                    this.returnNode.setMemoryMap(memoryMap);
                    for (MemoryMapNode mm : memMaps) {
                        if (mm != memoryMap && mm.isAlive()) {
                            assert mm.hasNoUsages();
                            GraphUtil.killWithUnusedFloatingInputs(mm);
                        }
                    }
                }
                merge.setNext(this.returnNode);
            }
            debug.dump(DebugContext.INFO_LEVEL, snippet, "After fixing returns");

            boolean needsMergeStateMap = !guardsStage.areFrameStatesAtDeopts() && (containsMerge || containsLoopExit);

            if (needsMergeStateMap) {
                frameStateAssignment = new SnippetFrameStateAssignmentClosure(snippetCopy);
                ReentrantNodeIterator.apply(frameStateAssignment, snippetCopy.start(), SnippetFrameStateAssignment.NodeStateAssignment.BEFORE_BCI);
                assert frameStateAssignment.verify() : info;
            } else {
                frameStateAssignment = null;
            }

            assert verifyIntrinsicsProcessed(snippetCopy);

            this.sideEffectNodes = curSideEffectNodes;
            this.deoptNodes = curDeoptNodes;
            this.placeholderStampedNodes = curPlaceholderStampedNodes;

            nodes = new ArrayList<>(snippet.getNodeCount());
            for (Node node : snippet.getNodes()) {
                if (node != entryPointNode && node != entryPointNode.stateAfter()) {
                    nodes.add(node);
                }
            }

            if (debug.areMetricsEnabled()) {
                DebugContext.counter("SnippetTemplateNodeCount[%#s]", args).add(debug, nodes.size());
            }
            debug.dump(DebugContext.INFO_LEVEL, snippet, "SnippetTemplate final state");
            this.snippet.freeze();

        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    private static boolean verifyIntrinsicsProcessed(StructuredGraph snippetCopy) {
        if (IS_IN_NATIVE_IMAGE) {
            return true;
        }
        for (MethodCallTargetNode target : snippetCopy.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod targetMethod = target.targetMethod();
            if (targetMethod != null) {
                assert targetMethod.getAnnotation(Fold.class) == null && targetMethod.getAnnotation(NodeIntrinsic.class) == null : "plugin should have been processed";
            }
        }
        return true;
    }

    public static void explodeLoops(final StructuredGraph snippetCopy, CoreProviders providers) {
        // Do any required loop explosion
        boolean exploded = false;
        do {
            exploded = false;
            ExplodeLoopNode explodeLoop = snippetCopy.getNodes().filter(ExplodeLoopNode.class).first();
            if (explodeLoop != null) { // Earlier canonicalization may have removed the loop
                // altogether
                LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                if (loopBegin != null) {
                    LoopEx loop = new LoopsData(snippetCopy).loop(loopBegin);
                    Mark mark = snippetCopy.getMark();
                    CanonicalizerPhase canonicalizer = null;
                    if (GraalOptions.ImmutableCode.getValue(snippetCopy.getOptions())) {
                        canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
                    } else {
                        canonicalizer = CanonicalizerPhase.create();
                    }
                    LoopTransformations.fullUnroll(loop, providers, canonicalizer);
                    CanonicalizerPhase.create().applyIncremental(snippetCopy, providers, mark, false);
                    loop.deleteUnusedNodes();
                }
                GraphUtil.removeFixedWithUnusedInputs(explodeLoop);
                exploded = true;
            }
        } while (exploded);
    }

    protected Object[] getConstantArgs(Arguments args) {
        Object[] constantArgs = args.values.clone();
        for (int i = 0; i < args.info.getParameterCount(); i++) {
            if (!args.info.isConstantParameter(i)) {
                constantArgs[i] = null;
            } else {
                assert constantArgs[i] != null : "Can't pass raw null through as argument";
            }
        }
        return constantArgs;
    }

    private static boolean checkAllVarargPlaceholdersAreDeleted(int parameterCount, VarargsPlaceholderNode[] placeholders) {
        for (int i = 0; i < parameterCount; i++) {
            if (placeholders[i] != null) {
                assert placeholders[i].isDeleted() : placeholders[i];
            }
        }
        return true;
    }

    private static boolean checkConstantArgument(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int paramIndex, String name, Object arg, JavaKind kind) {
        ResolvedJavaType type = signature.getParameterType(paramIndex, method.getDeclaringClass()).resolve(method.getDeclaringClass());
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            assert arg instanceof JavaConstant : method + ": word constant parameters must be passed boxed in a Constant value: " + arg;
            return true;
        }
        if (kind != JavaKind.Object) {
            assert arg != null && kind.toBoxedJavaClass() == arg.getClass() : method + ": wrong value kind for " + name + ": expected " + kind + ", got " +
                            (arg == null ? "null" : arg.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean checkVarargs(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int i, String name, Varargs varargs) {
        ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
        assert type.isArray() : "varargs parameter must be an array type";
        assert type.getComponentType().isAssignableFrom(metaAccess.lookupJavaType(varargs.componentType)) : "componentType for " + name + " not matching " + type.toJavaName() + " instance: " +
                        varargs.componentType;
        return true;
    }

    /**
     * The graph built from the snippet method.
     */
    private final StructuredGraph snippet;

    private final SnippetInfo info;

    /**
     * The named parameters of this template that must be bound to values during instantiation. For
     * a parameter that is still live after specialization, the value in this map is either a
     * {@link ParameterNode} instance or a {@link ParameterNode} array. For an eliminated parameter,
     * the value is identical to the key.
     */
    private final Object[] parameters;

    /**
     * The return node (if any) of the snippet.
     */
    private final ReturnNode returnNode;

    /**
     * The node that will be replaced with the exception handler of the replacee node, or null if
     * the snippet does not have an exception handler path.
     */
    private final FixedWithNextNode unwindPath;

    /**
     * The memory anchor (if any) of the snippet.
     */
    private final MemoryAnchorNode memoryAnchor;

    /**
     * Nodes that inherit the {@link StateSplit#stateAfter()} from the replacee during
     * instantiation.
     */
    private final ArrayList<StateSplit> sideEffectNodes;

    /**
     * Nodes that inherit a deoptimization {@link FrameState} from the replacee during
     * instantiation.
     */
    private final ArrayList<DeoptimizingNode> deoptNodes;

    /**
     * Mapping of merge and loop exit nodes to frame state info determining if a merge/loop exit
     * node is required to have a framestate after the lowering, and if so which state
     * (before,after).
     */
    private SnippetFrameStateAssignment.SnippetFrameStateAssignmentClosure frameStateAssignment;

    /**
     * Nodes that have a stamp originating from a {@link Placeholder}.
     */
    private final ArrayList<ValueNode> placeholderStampedNodes;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     *
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private EconomicMap<Node, Node> bind(StructuredGraph replaceeGraph, MetaAccessProvider metaAccess, Arguments args) {
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        assert args.info.getParameterCount() == parameters.length : "number of args (" + args.info.getParameterCount() + ") != number of parameters (" + parameters.length + ")";
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            assert parameter != null : this + " has no parameter named " + args.info.getParameterName(i);
            Object argument = args.values[i];
            if (parameter instanceof ParameterNode) {
                if (argument instanceof ValueNode) {
                    replacements.put((ParameterNode) parameter, (ValueNode) argument);
                } else {
                    JavaKind kind = ((ParameterNode) parameter).getStackKind();
                    assert argument != null || kind == JavaKind.Object : this + " cannot accept null for non-object parameter named " + args.info.getParameterName(i);
                    JavaConstant constant = forBoxed(argument, kind);
                    replacements.put((ParameterNode) parameter, ConstantNode.forConstant(constant, metaAccess, replaceeGraph));
                }
            } else if (parameter instanceof ParameterNode[]) {
                ParameterNode[] params = (ParameterNode[]) parameter;
                Varargs varargs = (Varargs) argument;
                int length = params.length;
                List<?> list = null;
                Object array = null;
                if (varargs.value instanceof List) {
                    list = (List<?>) varargs.value;
                    assert list.size() == length : length + " != " + list.size();
                } else {
                    array = varargs.value;
                    assert array != null && array.getClass().isArray();
                    assert Array.getLength(array) == length : length + " != " + Array.getLength(array);
                }

                for (int j = 0; j < length; j++) {
                    ParameterNode param = params[j];
                    assert param != null;
                    Object value = list != null ? list.get(j) : Array.get(array, j);
                    if (value instanceof ValueNode) {
                        replacements.put(param, (ValueNode) value);
                    } else {
                        JavaConstant constant = forBoxed(value, param.getStackKind());
                        ConstantNode element = ConstantNode.forConstant(constant, metaAccess, replaceeGraph);
                        replacements.put(param, element);
                    }
                }
            } else {
                assert parameter.equals(CONSTANT_PARAMETER) || parameter.equals(UNUSED_PARAMETER) : "unexpected entry for parameter: " + args.info.getParameterName(i) + " -> " + parameter;
            }
        }
        return replacements;
    }

    /**
     * Converts a Java boxed value to a {@link JavaConstant} of the right kind. This adjusts for the
     * limitation that a {@link Local}'s kind is a {@linkplain JavaKind#getStackKind() stack kind}
     * and so cannot be used for re-boxing primitives smaller than an int.
     *
     * @param argument a Java boxed value
     * @param localKind the kind of the {@link Local} to which {@code argument} will be bound
     */
    protected JavaConstant forBoxed(Object argument, JavaKind localKind) {
        assert localKind == localKind.getStackKind();
        if (localKind == JavaKind.Int) {
            return JavaConstant.forBoxedPrimitive(argument);
        }
        return snippetReflection.forBoxed(localKind, argument);
    }

    /**
     * Logic for replacing a snippet-lowered node at its usages with the return value of the
     * snippet. An alternative to the {@linkplain SnippetTemplate#DEFAULT_REPLACER default}
     * replacement logic can be used to handle mismatches between the stamp of the node being
     * lowered and the stamp of the snippet's return value.
     */
    public interface UsageReplacer {
        /**
         * Replaces all usages of {@code oldNode} with direct or indirect usages of {@code newNode}.
         */
        void replace(ValueNode oldNode, ValueNode newNode);
    }

    /**
     * Represents the default {@link UsageReplacer usage replacer} logic which simply delegates to
     * {@link Node#replaceAtUsages(Node)}.
     */
    public static final UsageReplacer DEFAULT_REPLACER = new UsageReplacer() {

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode) {
            if (newNode == null) {
                assert oldNode.hasNoUsages();
            } else {
                oldNode.replaceAtUsages(newNode);
            }
        }
    };

    private boolean assertSnippetKills(ValueNode replacee) {
        if (!replacee.graph().isAfterFloatingReadPhase()) {
            // no floating reads yet, ignore locations created while lowering
            return true;
        }
        if (returnNode == null) {
            // The snippet terminates control flow
            return true;
        }
        MemoryMapNode memoryMap = returnNode.getMemoryMap();
        if (memoryMap == null || memoryMap.isEmpty()) {
            // there are no kills in the snippet graph
            return true;
        }

        EconomicSet<LocationIdentity> kills = EconomicSet.create(Equivalence.DEFAULT);
        kills.addAll(memoryMap.getLocations());

        if (replacee instanceof SingleMemoryKill) {
            // check if some node in snippet graph also kills the same location
            LocationIdentity locationIdentity = ((SingleMemoryKill) replacee).getKilledLocationIdentity();
            if (locationIdentity.isAny()) {
                // if the replacee kills ANY_LOCATION, the snippet can kill arbitrary locations
                return true;
            }
            assert kills.contains(locationIdentity) : replacee + " kills " + locationIdentity + ", but snippet doesn't contain a kill to this location";
            kills.remove(locationIdentity);
        }
        assert !(replacee instanceof MultiMemoryKill) : replacee + " multi not supported (yet)";

        // remove ANY_LOCATION if it's just a kill by the start node
        if (memoryMap.getLastLocationAccess(any()) instanceof MemoryAnchorNode) {
            kills.remove(any());
        }

        // node can only lower to a ANY_LOCATION kill if the replacee also kills ANY_LOCATION
        assert !kills.contains(any()) : "snippet graph contains a kill to ANY_LOCATION, but replacee (" + replacee + ") doesn't kill ANY_LOCATION.  kills: " + kills;

        /*
         * Kills to private locations are safe, since there can be no floating read to these
         * locations except reads that are introduced by the snippet itself or related snippets in
         * the same lowering round. These reads are anchored to a MemoryAnchor at the beginning of
         * their snippet, so they can not float above a kill in another instance of the same
         * snippet.
         */
        for (LocationIdentity p : this.info.privateLocations) {
            kills.remove(p);
        }

        assert kills.isEmpty() : "snippet graph kills non-private locations " + kills + " that replacee (" + replacee + ") doesn't kill";
        return true;
    }

    private static class MemoryInputMap implements MemoryMap {

        private final LocationIdentity locationIdentity;
        private final MemoryKill lastLocationAccess;

        MemoryInputMap(ValueNode replacee) {
            if (replacee instanceof MemoryAccess) {
                MemoryAccess access = (MemoryAccess) replacee;
                locationIdentity = access.getLocationIdentity();
                lastLocationAccess = access.getLastLocationAccess();
            } else {
                locationIdentity = null;
                lastLocationAccess = null;
            }
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity location) {
            if (locationIdentity != null && locationIdentity.equals(location)) {
                return lastLocationAccess;
            } else {
                return null;
            }
        }

        @Override
        public Collection<LocationIdentity> getLocations() {
            if (locationIdentity == null) {
                return Collections.emptySet();
            } else {
                return Collections.singleton(locationIdentity);
            }
        }
    }

    private class MemoryOutputMap extends MemoryInputMap {

        private final UnmodifiableEconomicMap<Node, Node> duplicates;

        MemoryOutputMap(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
            super(replacee);
            this.duplicates = duplicates;
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity locationIdentity) {
            MemoryMapNode memoryMap = returnNode.getMemoryMap();
            assert memoryMap != null : "no memory map stored for this snippet graph (snippet doesn't have a ReturnNode?)";
            MemoryKill lastLocationAccess = memoryMap.getLastLocationAccess(locationIdentity);
            assert lastLocationAccess != null : locationIdentity;
            if (lastLocationAccess == memoryAnchor) {
                return super.getLastLocationAccess(locationIdentity);
            } else {
                return (MemoryKill) duplicates.get(ValueNodeUtil.asNode(lastLocationAccess));
            }
        }

        @Override
        public Collection<LocationIdentity> getLocations() {
            return returnNode.getMemoryMap().getLocations();
        }
    }

    private void rewireMemoryGraph(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        if (replacee.graph().isAfterFloatingReadPhase()) {
            // rewire outgoing memory edges
            replaceMemoryUsages(replacee, new MemoryOutputMap(replacee, duplicates));

            if (returnNode != null) {
                ReturnNode ret = (ReturnNode) duplicates.get(returnNode);
                if (ret != null) {
                    MemoryMapNode memoryMap = ret.getMemoryMap();
                    if (memoryMap != null) {
                        ret.setMemoryMap(null);
                        memoryMap.safeDelete();
                    }
                }
            }
            if (memoryAnchor != null) {
                // rewire incoming memory edges
                MemoryAnchorNode memoryDuplicate = (MemoryAnchorNode) duplicates.get(memoryAnchor);
                replaceMemoryUsages(memoryDuplicate, new MemoryInputMap(replacee));

                if (memoryDuplicate.hasNoUsages()) {
                    if (memoryDuplicate.next() != null) {
                        memoryDuplicate.graph().removeFixed(memoryDuplicate);
                    } else {
                        // this was a dummy memory node used when instantiating pure data-flow
                        // snippets: it was not attached to the control flow.
                        memoryDuplicate.safeDelete();
                    }
                }
            }
        }
    }

    private static LocationIdentity getLocationIdentity(Node node) {
        if (node instanceof MemoryAccess) {
            return ((MemoryAccess) node).getLocationIdentity();
        } else if (node instanceof MemoryEdgeProxy) {
            return ((MemoryEdgeProxy) node).getLocationIdentity();
        } else if (node instanceof MemoryPhiNode) {
            return ((MemoryPhiNode) node).getLocationIdentity();
        } else {
            return null;
        }
    }

    private void replaceMemoryUsages(ValueNode node, MemoryMap map) {
        for (Node usage : node.usages().snapshot()) {
            if (usage instanceof MemoryMapNode) {
                continue;
            }

            LocationIdentity location = getLocationIdentity(usage);
            if (location != null) {
                for (Position pos : usage.inputPositions()) {
                    if (pos.getInputType() == InputType.Memory && pos.get(usage) == node) {
                        MemoryKill replacement = map.getLastLocationAccess(location);
                        if (replacement == null) {
                            assert mayRemoveLocation || LocationIdentity.any().equals(location) ||
                                            CollectionsUtil.anyMatch(info.privateLocations, Predicate.isEqual(location)) : "Snippet " +
                                                            info.method.format("%h.%n") + " contains access to the non-private location " +
                                                            location + ", but replacee doesn't access this location." + map.getLocations();
                        } else {
                            pos.set(usage, replacement.asNode());
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @return the map of duplicated nodes (original -&gt; duplicate)
     */
    @SuppressWarnings("try")
    public UnmodifiableEconomicMap<Node, Node> instantiate(MetaAccessProvider metaAccess, FixedNode replacee, UsageReplacer replacer, Arguments args) {
        return instantiate(metaAccess, replacee, replacer, args, true);
    }

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @param killReplacee is true, the replacee node is deleted
     * @return the map of duplicated nodes (original -&gt; duplicate)
     */
    @SuppressWarnings("try")
    public UnmodifiableEconomicMap<Node, Node> instantiate(MetaAccessProvider metaAccess, FixedNode replacee, UsageReplacer replacer, Arguments args, boolean killReplacee) {
        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);
            // Inline the snippet nodes, replacing parameters with the given args in the process
            final FixedNode replaceeGraphPredecessor = (FixedNode) replacee.predecessor();
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, AbstractBeginNode.prevBegin(replacee));
            UnmodifiableEconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            // Re-wire the control flow graph around the replacee
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            replacee.replaceAtPredecessor(firstCFGNodeDuplicate);

            if (replacee.graph().getGuardsStage().areFrameStatesAtSideEffects()) {
                boolean replaceeHasSideEffect = replacee instanceof StateSplit && ((StateSplit) replacee).hasSideEffect();
                boolean replacementHasSideEffect = !sideEffectNodes.isEmpty();

                /*
                 * Following cases are allowed: Either the replacee and replacement don't have
                 * side-effects or the replacee has and the replacement hasn't (lowered to something
                 * without a side-effect which is fine regarding correctness) or both have
                 * side-effects, under which conditions also merges should have states.
                 */

                if (replacementHasSideEffect) {
                    GraalError.guarantee(replaceeHasSideEffect, "Lowering node %s without side-effect to snippet %s with sideeffects=%s", replacee, info, this.sideEffectNodes);
                }
            }

            rewireFrameStates(replacee, duplicates, replaceeGraphPredecessor);

            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);

            // Replace all usages of the replacee with the value returned by the snippet
            ValueNode returnValue = null;
            if (returnNode != null && !(replacee instanceof ControlSinkNode)) {
                ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
                returnValue = returnDuplicate.result();
                if (returnValue == null && replacee.usages().isNotEmpty() && replacee instanceof MemoryKill) {
                    replacer.replace(replacee, null);
                } else {
                    assert returnValue != null || replacee.hasNoUsages();
                    replacer.replace(replacee, returnValue);
                }
                if (returnDuplicate.isAlive()) {
                    FixedNode next = null;
                    if (replacee instanceof FixedWithNextNode) {
                        FixedWithNextNode fwn = (FixedWithNextNode) replacee;
                        next = fwn.next();
                        fwn.setNext(null);
                    } else if (replacee instanceof WithExceptionNode) {
                        WithExceptionNode withExceptionNode = (WithExceptionNode) replacee;
                        next = withExceptionNode.next();
                        withExceptionNode.setNext(null);
                    }
                    returnDuplicate.replaceAndDelete(next);
                }
            }
            if (unwindPath != null) {
                GraalError.guarantee(!replacee.graph().isAfterFloatingReadPhase(), "Using a snippet with an UnwindNode after floating reads would require support for the memory graph");
                GraalError.guarantee(replacee instanceof WithExceptionNode, "Snippet has an UnwindNode, but replacee is not a node with an exception handler");

                FixedWithNextNode unwindPathDuplicate = (FixedWithNextNode) duplicates.get(unwindPath);
                WithExceptionNode withExceptionNode = (WithExceptionNode) replacee;
                AbstractBeginNode exceptionEdge = withExceptionNode.exceptionEdge();
                withExceptionNode.setExceptionEdge(null);
                unwindPathDuplicate.replaceAtPredecessor(exceptionEdge);
                GraphUtil.killCFG(unwindPathDuplicate);

            } else {
                GraalError.guarantee(!(replacee instanceof WithExceptionNode), "Snippet does not have an UnwindNode, but replacee is a node with an exception handler");
            }

            if (killReplacee) {
                // Remove the replacee from its graph
                GraphUtil.killCFG(replacee);
            }

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);
            return duplicates;
        }
    }

    private UnmodifiableEconomicMap<Node, Node> inlineSnippet(Node replacee, DebugContext debug, StructuredGraph replaceeGraph, EconomicMap<Node, Node> replacements) {
        Mark mark = replaceeGraph.getMark();
        try (InliningLog.UpdateScope scope = replaceeGraph.getInliningLog().openUpdateScope((oldNode, newNode) -> {
            InliningLog log = replaceeGraph.getInliningLog();
            if (oldNode == null) {
                log.trackNewCallsite(newNode);
            }
        })) {
            UnmodifiableEconomicMap<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, snippet, snippet.getNodeCount(), replacements);
            if (scope != null) {
                replaceeGraph.getInliningLog().addLog(duplicates, snippet.getInliningLog());
            }
            NodeSourcePosition position = replacee.getNodeSourcePosition();
            InliningUtil.updateSourcePosition(replaceeGraph, duplicates, mark, position, true);
            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After inlining snippet %s", snippet.method());
            return duplicates;
        }
    }

    private void propagateStamp(Node node) {
        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.inferStamp()) {
                for (Node usage : node.usages()) {
                    propagateStamp(usage);
                }
            }
        }
    }

    private void updateStamps(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        for (ValueNode node : placeholderStampedNodes) {
            ValueNode dup = (ValueNode) duplicates.get(node);
            Stamp replaceeStamp = replacee.stamp(NodeView.DEFAULT);
            if (node instanceof Placeholder) {
                Placeholder placeholderDup = (Placeholder) dup;
                placeholderDup.makeReplacement(replaceeStamp);
            } else {
                dup.setStamp(replaceeStamp);
            }
        }
        for (ParameterNode paramNode : snippet.getNodes(ParameterNode.TYPE)) {
            for (Node usage : paramNode.usages()) {
                Node usageDup = duplicates.get(usage);
                propagateStamp(usageDup);
            }
        }
    }

    /**
     * Gets a copy of the specialized graph.
     */
    public StructuredGraph copySpecializedGraph(DebugContext debugForCopy) {
        return (StructuredGraph) snippet.copy(debugForCopy);
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param tool lowering tool used to insert the snippet into the control-flow
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    @SuppressWarnings("try")
    public void instantiate(MetaAccessProvider metaAccess, FloatingNode replacee, UsageReplacer replacer, LoweringTool tool, Arguments args) {
        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);

            // Inline the snippet nodes, replacing parameters with the given args in the process
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, tool.getCurrentGuardAnchor().asNode());
            UnmodifiableEconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            FixedWithNextNode lastFixedNode = tool.lastFixedNode();
            assert lastFixedNode != null && lastFixedNode.isAlive() : replaceeGraph + " lastFixed=" + lastFixedNode;
            FixedNode next = lastFixedNode.next();
            lastFixedNode.setNext(null);
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            replaceeGraph.addAfterFixed(lastFixedNode, firstCFGNodeDuplicate);

            /*
             * floating nodes that are not state-splits do not need to re-wire frame states, however
             * snippets might contain merges for which we want proper frame states
             */
            assert !(replacee instanceof StateSplit);
            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);

            rewireFrameStates(replacee, duplicates, lastFixedNode);

            // Replace all usages of the replacee with the value returned by the snippet
            ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
            ValueNode returnValue = returnDuplicate.result();
            assert returnValue != null || replacee.hasNoUsages();
            replacer.replace(replacee, returnValue);

            if (returnDuplicate.isAlive()) {
                returnDuplicate.replaceAndDelete(next);
            }

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);
        }
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     *
     * This snippet must be pure data-flow
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    @SuppressWarnings("try")
    public void instantiate(MetaAccessProvider metaAccess, FloatingNode replacee, UsageReplacer replacer, Arguments args) {
        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);

            // Inline the snippet nodes, replacing parameters with the given args in the process
            StartNode entryPointNode = snippet.start();
            assert entryPointNode.next() == (memoryAnchor == null ? returnNode : memoryAnchor) : entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            MemoryAnchorNode anchorDuplicate = null;
            if (memoryAnchor != null) {
                anchorDuplicate = replaceeGraph.add(new MemoryAnchorNode(info.privateLocations));
                replacements.put(memoryAnchor, anchorDuplicate);
            }
            List<Node> floatingNodes = new ArrayList<>(nodes.size() - 2);
            for (Node n : nodes) {
                if (n != entryPointNode && n != returnNode) {
                    floatingNodes.add(n);
                }
            }
            UnmodifiableEconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            // floating nodes are not state-splits not need to re-wire frame states
            assert !(replacee instanceof StateSplit);
            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);
            assert anchorDuplicate == null || anchorDuplicate.isDeleted();

            // Replace all usages of the replacee with the value returned by the snippet
            ValueNode returnValue = (ValueNode) duplicates.get(returnNode.result());
            replacer.replace(replacee, returnValue);

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);
        }
    }

    protected void rewireFrameStates(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        if (replacee.graph().getGuardsStage().areFrameStatesAtSideEffects() && requiresFrameStateProcessingBeforeFSA(replacee)) {
            rewireFrameStatesBeforeFSA(replacee, duplicates, replaceeGraphCFGPredecessor);
        } else if (replacee.graph().getGuardsStage().areFrameStatesAtDeopts() && replacee instanceof DeoptimizingNode) {
            rewireFrameStatesAfterFSA(replacee, duplicates);
        }
    }

    private boolean requiresFrameStateProcessingBeforeFSA(ValueNode replacee) {
        return replacee instanceof StateSplit || frameStateAssignment != null;
    }

    private void rewireFrameStatesBeforeFSA(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        if (replacee instanceof StateSplit && ((StateSplit) replacee).hasSideEffect() && ((StateSplit) replacee).stateAfter() != null) {
            /*
             * We have a side-effecting node that is lowered to a snippet that also contains
             * side-effecting nodes. Either of 2 cases applies: either there is a frame state merge
             * assignment meaning there are merges in the snippet that require states, then those
             * will be assigned based on a reverse post order iteration of the snippet examining
             * effects and trying to find a proper state, or no merges are in the graph, i.e., there
             * is no complex control flow so every side-effecting node in the snippet can have the
             * state after of the original node with the correct values replaced (i.e. the replacee
             * itself in the snippet)
             */
            for (StateSplit sideEffectNode : sideEffectNodes) {
                assert ((StateSplit) replacee).hasSideEffect();
                Node sideEffectDup = duplicates.get(sideEffectNode.asNode());
                assert sideEffectDup != null : sideEffectNode;
                FrameState stateAfter = ((StateSplit) replacee).stateAfter();
                assert stateAfter != null : "Replacee " + replacee + " has no state after";

                if (sideEffectDup instanceof DeoptBciSupplier) {
                    if (replacee instanceof DeoptBciSupplier) {
                        ((DeoptBciSupplier) sideEffectDup).setBci(((DeoptBciSupplier) replacee).bci());
                    }
                }
                if (stateAfter.values().contains(replacee)) {
                    FrameState duplicated = stateAfter.duplicate();
                    ValueNode valueInReplacement = (ValueNode) duplicates.get(returnNode.result());
                    if (valueInReplacement instanceof ValuePhiNode) {
                        valueInReplacement = (ValueNode) sideEffectDup;
                    }
                    ValueNode replacement = valueInReplacement;
                    duplicated.applyToNonVirtual(new NodePositionClosure<Node>() {
                        @Override
                        public void apply(Node from, Position p) {
                            if (p.get(from) == replacee) {
                                p.set(from, replacement);
                            }
                        }
                    });
                    ((StateSplit) sideEffectDup).setStateAfter(duplicated);
                } else {
                    ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
                }
            }
        }
        if (frameStateAssignment != null) {
            assignNecessaryFrameStates(replacee, duplicates, replaceeGraphCFGPredecessor);
        }
    }

    private void assignNecessaryFrameStates(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        FrameState stateAfter = null;
        if (replacee instanceof StateSplit && ((StateSplit) replacee).hasSideEffect()) {
            stateAfter = ((StateSplit) replacee).stateAfter();
            assert stateAfter != null : "Statesplit with side-effect needs a framestate " + replacee;
        } else {
            /*
             * We dont have a state split as a replacee, thus we take the prev state as the state
             * after for the node in the snippet.
             */
            stateAfter = findLastFrameState(replaceeGraphCFGPredecessor);
        }
        NodeMap<NodeStateAssignment> assignedStateMappings = frameStateAssignment.getStateMapping();
        MapCursor<Node, NodeStateAssignment> stateAssignments = assignedStateMappings.getEntries();
        while (stateAssignments.advance()) {
            Node nodeRequiringState = stateAssignments.getKey();
            NodeStateAssignment assignment = stateAssignments.getValue();
            switch (assignment) {
                case AFTER_BCI:
                    FrameState newState = stateAfter.duplicate();
                    if (stateAfter.values().contains(replacee)) {
                        ValueNode valueInReplacement = (ValueNode) duplicates.get(returnNode.result());
                        newState.applyToNonVirtual(new NodePositionClosure<Node>() {
                            @Override
                            public void apply(Node from, Position p) {
                                if (p.get(from) == replacee) {
                                    p.set(from, valueInReplacement);
                                }
                            }
                        });
                    }
                    ((StateSplit) duplicates.get(nodeRequiringState)).setStateAfter(newState);
                    break;
                case BEFORE_BCI:
                    FrameState stateBeforeSnippet = findLastFrameState(replaceeGraphCFGPredecessor);
                    ((StateSplit) duplicates.get(nodeRequiringState)).setStateAfter(stateBeforeSnippet.duplicate());
                    break;
                case INVALID:
                    /*
                     * We cannot assign a proper frame state for this snippet's node since there are
                     * effects which cannot be represented by a single state at the node
                     */
                    throw GraalError.shouldNotReachHere("Invalid snippet replacing a node before frame state assignment with node " + nodeRequiringState + " for replacee " + replacee);
                default:
                    throw GraalError.shouldNotReachHere("Unknown StateAssigment:" + assignment);
            }
            replacee.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL,
                            replacee.graph(), "After duplicating after state for node %s in snippet", duplicates.get(nodeRequiringState));
        }
    }

    private void rewireFrameStatesAfterFSA(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        DeoptimizingNode replaceeDeopt = (DeoptimizingNode) replacee;
        FrameState stateBefore = null;
        FrameState stateDuring = null;
        FrameState stateAfter = null;
        if (replaceeDeopt.canDeoptimize()) {
            if (replaceeDeopt instanceof DeoptimizingNode.DeoptBefore) {
                stateBefore = ((DeoptimizingNode.DeoptBefore) replaceeDeopt).stateBefore();
            }
            if (replaceeDeopt instanceof DeoptimizingNode.DeoptDuring) {
                stateDuring = ((DeoptimizingNode.DeoptDuring) replaceeDeopt).stateDuring();
            }
            if (replaceeDeopt instanceof DeoptimizingNode.DeoptAfter) {
                stateAfter = ((DeoptimizingNode.DeoptAfter) replaceeDeopt).stateAfter();
            }
        }

        for (DeoptimizingNode deoptNode : deoptNodes) {
            DeoptimizingNode deoptDup = (DeoptimizingNode) duplicates.get(deoptNode.asNode());
            if (deoptDup.canDeoptimize()) {
                if (deoptDup instanceof DeoptimizingNode.DeoptBefore) {
                    ((DeoptimizingNode.DeoptBefore) deoptDup).setStateBefore(stateBefore);
                }
                if (deoptDup instanceof DeoptimizingNode.DeoptDuring) {
                    // compute a state "during" for a DeoptDuring inside the snippet depending
                    // on what kind of states we had on the node we are replacing.
                    // If the original node had a state "during" already, we just use that,
                    // otherwise we need to find a strategy to compute a state during based on
                    // some other state (before or after).
                    DeoptimizingNode.DeoptDuring deoptDupDuring = (DeoptimizingNode.DeoptDuring) deoptDup;
                    if (stateDuring != null) {
                        deoptDupDuring.setStateDuring(stateDuring);
                    } else if (stateAfter != null) {
                        deoptDupDuring.computeStateDuring(stateAfter);
                    } else if (stateBefore != null) {
                        assert ((DeoptBefore) replaceeDeopt).canUseAsStateDuring() || !deoptDupDuring.hasSideEffect() : "can't use stateBefore as stateDuring for state split " + deoptDupDuring;
                        deoptDupDuring.setStateDuring(stateBefore);
                    }
                }
                if (deoptDup instanceof DeoptimizingNode.DeoptAfter) {
                    DeoptimizingNode.DeoptAfter deoptDupAfter = (DeoptimizingNode.DeoptAfter) deoptDup;
                    if (stateAfter != null) {
                        deoptDupAfter.setStateAfter(stateAfter);
                    } else {
                        assert !deoptDupAfter.hasSideEffect() : "can't use stateBefore as stateAfter for state split " + deoptDupAfter;
                        deoptDupAfter.setStateAfter(stateBefore);
                    }

                }
            }
        }
    }

    public static FrameState findLastFrameState(FixedNode start) {
        FrameState state = findLastFrameState(start, false);
        assert state != null : "Must find a prev state (this can be transitively broken) for node " + start + " " + findLastFrameState(start, true);
        return state;
    }

    public static FrameState findLastFrameState(FixedNode start, boolean log) {
        assert start != null;
        FixedNode lastFixedNode = null;
        FixedNode currentStart = start;
        while (true) {
            for (FixedNode fixed : GraphUtil.predecessorIterable(currentStart)) {
                if (fixed instanceof StateSplit) {
                    StateSplit stateSplit = (StateSplit) fixed;
                    assert !stateSplit.hasSideEffect() || stateSplit.stateAfter() != null : "Found state split with side-effect without framestate=" + stateSplit;
                    if (stateSplit.stateAfter() != null) {
                        return stateSplit.stateAfter();
                    }
                }
                lastFixedNode = fixed;
            }
            if (lastFixedNode instanceof LoopBeginNode) {
                currentStart = ((LoopBeginNode) lastFixedNode).forwardEnd();
                continue;
            }
            if (log) {
                NodeSourcePosition p = lastFixedNode.getNodeSourcePosition();
                DebugContext debug = start.getDebug();
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Last fixed node %s\n with source position -> %s", lastFixedNode,
                                p == null ? "null" : p.toString());
                if (lastFixedNode instanceof MergeNode) {
                    MergeNode merge = (MergeNode) lastFixedNode;
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "Last fixed node is a merge with predecessors:");
                    for (EndNode end : merge.forwardEnds()) {
                        for (FixedNode fixed : GraphUtil.predecessorIterable(end)) {
                            NodeSourcePosition sp = fixed.getNodeSourcePosition();
                            debug.log(DebugContext.VERY_DETAILED_LEVEL, "%s:source position%s", fixed, sp != null ? sp.toString() : "null");
                        }
                    }
                }
            }
            break;
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(snippet.toString()).append('(');
        String sep = "";
        for (int i = 0; i < parameters.length; i++) {
            String name = "[" + i + "]";
            Object value = parameters[i];
            buf.append(sep);
            sep = ", ";
            if (value == null) {
                buf.append("<null> ").append(name);
            } else if (value.equals(UNUSED_PARAMETER)) {
                buf.append("<unused> ").append(name);
            } else if (value.equals(CONSTANT_PARAMETER)) {
                buf.append("<constant> ").append(name);
            } else if (value instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) value;
                buf.append(param.getStackKind().getJavaName()).append(' ').append(name);
            } else {
                ParameterNode[] params = (ParameterNode[]) value;
                String kind = params.length == 0 ? "?" : params[0].getStackKind().getJavaName();
                buf.append(kind).append('[').append(params.length).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }

    private static boolean checkTemplate(MetaAccessProvider metaAccess, Arguments args, ResolvedJavaMethod method, Signature signature) {
        int offset = args.info.hasReceiver() ? 1 : 0;
        for (int i = offset; i < args.info.getParameterCount(); i++) {
            if (args.info.isConstantParameter(i)) {
                JavaKind kind = signature.getParameterKind(i - offset);
                assert checkConstantArgument(metaAccess, method, signature, i - offset, args.info.getParameterName(i), args.values[i], kind);

            } else if (args.info.isVarargsParameter(i)) {
                assert args.values[i] instanceof Varargs;
                Varargs varargs = (Varargs) args.values[i];
                assert checkVarargs(metaAccess, method, signature, i - offset, args.info.getParameterName(i), varargs);
            }
        }
        return true;
    }

    public void setMayRemoveLocation(boolean mayRemoveLocation) {
        this.mayRemoveLocation = mayRemoveLocation;
    }
}
