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
package org.graalvm.compiler.phases.common.inlining.info.elem;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.graalvm.compiler.core.common.GraalOptions.UseGraalInstrumentation;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.instrumentation.ExtractInstrumentationPhase;
import org.graalvm.compiler.phases.graph.FixedNodeProbabilityCache;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * <p>
 * Represents a feasible concrete target for inlining, whose graph has been copied already and thus
 * can be modified without affecting the original (usually cached) version.
 * </p>
 *
 * <p>
 * Instances of this class don't make sense in isolation but as part of an
 * {@link org.graalvm.compiler.phases.common.inlining.info.InlineInfo InlineInfo}.
 * </p>
 *
 * @see org.graalvm.compiler.phases.common.inlining.walker.InliningData#moveForward()
 * @see org.graalvm.compiler.phases.common.inlining.walker.CallsiteHolderExplorable
 */
public class InlineableGraph implements Inlineable {

    private final StructuredGraph graph;

    private FixedNodeProbabilityCache probabilites = new FixedNodeProbabilityCache();

    public InlineableGraph(final ResolvedJavaMethod method, final Invoke invoke, final HighTierContext context, CanonicalizerPhase canonicalizer) {
        StructuredGraph original = getOriginalGraph(method, context, canonicalizer, invoke.asNode().graph(), invoke.bci());
        // TODO copying the graph is only necessary if it is modified or if it contains any invokes
        this.graph = (StructuredGraph) original.copy();
        specializeGraphToArguments(invoke, context, canonicalizer);
    }

    /**
     * This method looks up in a cache the graph for the argument, if not found bytecode is parsed.
     * The graph thus obtained is returned, ie the caller is responsible for cloning before
     * modification.
     */
    private static StructuredGraph getOriginalGraph(final ResolvedJavaMethod method, final HighTierContext context, CanonicalizerPhase canonicalizer, StructuredGraph caller, int callerBci) {
        StructuredGraph result = InliningUtil.getIntrinsicGraph(context.getReplacements(), method, callerBci);
        if (result != null) {
            return result;
        }
        return parseBytecodes(method, context, canonicalizer, caller);
    }

    /**
     * @return true iff one or more parameters <code>newGraph</code> were specialized to account for
     *         a constant argument, or an argument with a more specific stamp.
     */
    @SuppressWarnings("try")
    private boolean specializeGraphToArguments(final Invoke invoke, final HighTierContext context, CanonicalizerPhase canonicalizer) {
        try (Debug.Scope s = Debug.scope("InlineGraph", graph)) {

            ArrayList<Node> parameterUsages = replaceParamsWithMoreInformativeArguments(invoke, context);
            if (parameterUsages != null) {
                assert !parameterUsages.isEmpty() : "The caller didn't have more information about arguments after all";
                canonicalizer.applyIncremental(graph, context, parameterUsages);
                return true;
            } else {
                // TODO (chaeubl): if args are not more concrete, inlining should be avoided
                // in most cases or we could at least use the previous graph size + invoke
                // probability to check the inlining
                return false;
            }

        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static boolean isArgMoreInformativeThanParam(ValueNode arg, ParameterNode param) {
        return arg.isConstant() || canStampBeImproved(arg, param);
    }

    private static boolean canStampBeImproved(ValueNode arg, ParameterNode param) {
        return improvedStamp(arg, param) != null;
    }

    private static Stamp improvedStamp(ValueNode arg, ParameterNode param) {
        Stamp joinedStamp = param.stamp().join(arg.stamp());
        if (joinedStamp == null || joinedStamp.equals(param.stamp())) {
            return null;
        }
        return joinedStamp;
    }

    /**
     * This method detects:
     * <ul>
     * <li>constants among the arguments to the <code>invoke</code></li>
     * <li>arguments with more precise type than that declared by the corresponding parameter</li>
     * </ul>
     *
     * <p>
     * The corresponding parameters are updated to reflect the above information. Before doing so,
     * their usages are added to <code>parameterUsages</code> for later incremental
     * canonicalization.
     * </p>
     *
     * @return null if no incremental canonicalization is need, a list of nodes for such
     *         canonicalization otherwise.
     */
    private ArrayList<Node> replaceParamsWithMoreInformativeArguments(final Invoke invoke, final HighTierContext context) {
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        ArrayList<Node> parameterUsages = null;
        List<ParameterNode> params = graph.getNodes(ParameterNode.TYPE).snapshot();
        assert params.size() <= args.size();
        /*
         * param-nodes that aren't used (eg, as a result of canonicalization) don't occur in
         * `params`. Thus, in general, the sizes of `params` and `args` don't always match. Still,
         * it's always possible to pair a param-node with its corresponding arg-node using
         * param.index() as index into `args`.
         */
        for (ParameterNode param : params) {
            if (param.usages().isNotEmpty()) {
                ValueNode arg = args.get(param.index());
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    parameterUsages = trackParameterUsages(param, parameterUsages);
                    // collect param usages before replacing the param
                    param.replaceAtUsagesAndDelete(graph.unique(
                                    ConstantNode.forConstant(arg.stamp(), constant, ((ConstantNode) arg).getStableDimension(), ((ConstantNode) arg).isDefaultStable(), context.getMetaAccess())));
                    // param-node gone, leaving a gap in the sequence given by param.index()
                } else {
                    Stamp impro = improvedStamp(arg, param);
                    if (impro != null) {
                        param.setStamp(impro);
                        parameterUsages = trackParameterUsages(param, parameterUsages);
                    } else {
                        assert !isArgMoreInformativeThanParam(arg, param);
                    }
                }
            }
        }
        assert (parameterUsages == null) || (!parameterUsages.isEmpty());
        return parameterUsages;
    }

    private static ArrayList<Node> trackParameterUsages(ParameterNode param, ArrayList<Node> parameterUsages) {
        ArrayList<Node> result = (parameterUsages == null) ? new ArrayList<>() : parameterUsages;
        param.usages().snapshotTo(result);
        return result;
    }

    /**
     * This method builds the IR nodes for the given <code>method</code> and canonicalizes them.
     * Provided profiling info is mature, the resulting graph is cached. The caller is responsible
     * for cloning before modification.
     * </p>
     */
    @SuppressWarnings("try")
    private static StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer, StructuredGraph caller) {
        StructuredGraph newGraph = new StructuredGraph(method, AllowAssumptions.from(caller.getAssumptions() != null), INVALID_COMPILATION_ID);
        try (Debug.Scope s = Debug.scope("InlineGraph", newGraph)) {
            if (!caller.isUnsafeAccessTrackingEnabled()) {
                newGraph.disableUnsafeAccessTracking();
            }
            if (context.getGraphBuilderSuite() != null) {
                context.getGraphBuilderSuite().apply(newGraph, context);
            }
            assert newGraph.start().next() != null : "graph needs to be populated by the GraphBuilderSuite " + method + ", " + method.canBeInlined();

            if (UseGraalInstrumentation.getValue()) {
                new ExtractInstrumentationPhase().apply(newGraph, context);
            }
            new DeadCodeEliminationPhase(Optional).apply(newGraph);

            canonicalizer.apply(newGraph, context);

            return newGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @Override
    public int getNodeCount() {
        return InliningUtil.getNodeCount(graph);
    }

    @Override
    public Iterable<Invoke> getInvokes() {
        return graph.getInvokes();
    }

    @Override
    public double getProbability(Invoke invoke) {
        return probabilites.applyAsDouble(invoke.asNode());
    }

    public StructuredGraph getGraph() {
        return graph;
    }
}
