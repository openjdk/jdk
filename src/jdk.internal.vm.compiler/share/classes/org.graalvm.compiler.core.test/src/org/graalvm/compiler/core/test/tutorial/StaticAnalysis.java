/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test.tutorial;

import static org.graalvm.compiler.core.test.GraalCompilerTest.getInitialOptions;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.graph.StatelessPostOrderNodeIterator;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A simple context-insensitive static analysis based on the Graal API. It is intended for
 * educational purposes, not for use in production. Only a limited set of Java functionality is
 * supported to keep the code minimal.
 * <p>
 * The analysis builds a directed graph of {@link TypeFlow type flows}. If a type is added to type
 * flow, it is propagated to all {@link TypeFlow#uses uses} of the type flow. Types are propagated
 * using a {@link #worklist} of changed type flows until a fixpoint is reached, i.e., until no more
 * types need to be added to any type state.
 * <p>
 * The type flows are constructed from a high-level Graal graph by the {@link TypeFlowBuilder}. All
 * nodes that operate on {@link JavaKind#Object object} values are converted to the appropriate type
 * flows. The analysis is context insensitive: every Java field has {@link Results#lookupField one
 * list} of types assigned to the field; every Java method has {@link Results#lookupMethod one
 * state} for each {@link MethodState#formalParameters parameter} as well as the
 * {@link MethodState#formalReturn return value}.
 */
public class StaticAnalysis {
    /**
     * Access to various builtin providers.
     */
    private final CoreProviders providers;
    /**
     * The results of the static analysis.
     */
    private final Results results;
    /**
     * Worklist for fixpoint iteration.
     */
    private final Deque<WorklistEntry> worklist;

    public StaticAnalysis(CoreProviders providers) {
        this.providers = providers;
        this.results = new Results();
        this.worklist = new ArrayDeque<>();
    }

    /**
     * Adds a root method to the static analysis. The method must be static and must not have any
     * parameters, because the possible types of the parameters would not be known.
     */
    public void addMethod(ResolvedJavaMethod method) {
        if (!method.isStatic() || method.getSignature().getParameterCount(false) > 0) {
            error("Entry point method is not static or has parameters: " + method.format("%H.%n(%p)"));
        }
        addToWorklist(results.lookupMethod(method));
    }

    /**
     * Performs the fixed-point analysis that finds all methods transitively reachable from the
     * {@link #addMethod root methods}.
     */
    public void finish() {
        while (!worklist.isEmpty()) {
            worklist.removeFirst().process();
        }
    }

    /**
     * Returns the static analysis results computed by {@link StaticAnalysis#finish}.
     */
    public Results getResults() {
        return results;
    }

    protected void addToWorklist(WorklistEntry task) {
        worklist.addLast(task);
    }

    protected static RuntimeException error(String msg) {
        throw GraalError.shouldNotReachHere(msg);
    }

    /**
     * Base class for all work items that can be {@link #addToWorklist added to the worklist}.
     */
    abstract class WorklistEntry {
        protected abstract void process();
    }

    /**
     * The results computed by the static analysis.
     */
    public class Results {
        private final TypeFlow allInstantiatedTypes;
        private final Map<ResolvedJavaField, TypeFlow> fields;
        private final Map<ResolvedJavaMethod, MethodState> methods;

        protected Results() {
            allInstantiatedTypes = new TypeFlow();
            fields = new HashMap<>();
            methods = new HashMap<>();
        }

        /**
         * All {@link TypeFlow#getTypes() types} that are found to be instantiated, i.e., all types
         * allocated by the reachable instance and array allocation bytecodes.
         */
        public TypeFlow getAllInstantiatedTypes() {
            return allInstantiatedTypes;
        }

        /**
         * All {@link TypeFlow#getTypes() types} that the given field can have, i.e., all types
         * assigned by the reachable field store bytecodes.
         */
        public TypeFlow lookupField(ResolvedJavaField field) {
            TypeFlow result = fields.get(field);
            if (result == null) {
                result = new TypeFlow();
                fields.put(field, result);
            }
            return result;
        }

        /**
         * All {@link TypeFlow#getTypes() types} that {@link MethodState#formalParameters
         * parameters} and {@link MethodState#formalReturn return value} of the given method can
         * have.
         */
        public MethodState lookupMethod(ResolvedJavaMethod method) {
            MethodState result = methods.get(method);
            if (result == null) {
                result = new MethodState(method);
                methods.put(method, result);
            }
            return result;
        }
    }

    /**
     * The {@link TypeFlow#getTypes() types} of the parameters and return value of a method. Also
     * serves as the worklist element to parse the bytecodes of the method.
     */
    public class MethodState extends WorklistEntry {
        private final ResolvedJavaMethod method;
        private final TypeFlow[] formalParameters;
        private final TypeFlow formalReturn;
        private boolean processed;

        protected MethodState(ResolvedJavaMethod method) {
            this.method = method;

            formalParameters = new TypeFlow[method.getSignature().getParameterCount(!method.isStatic())];
            for (int i = 0; i < formalParameters.length; i++) {
                formalParameters[i] = new TypeFlow();
            }
            formalReturn = new TypeFlow();
        }

        /**
         * All {@link TypeFlow#getTypes() types} that the parameters of this method can have.
         */
        public TypeFlow[] getFormalParameters() {
            return formalParameters;
        }

        /**
         * All {@link TypeFlow#getTypes() types} that the return value of this method can have.
         */
        public TypeFlow getFormalReturn() {
            return formalReturn;
        }

        @Override
        @SuppressWarnings("try")
        protected void process() {
            if (!processed) {
                /* We want to process a method only once. */
                processed = true;

                /*
                 * Build the Graal graph for the method using the bytecode parser provided by Graal.
                 */

                OptionValues options = getInitialOptions();
                DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                /*
                 * Support for graph dumping, IGV uses this information to show the method name of a
                 * graph.
                 */
                try (DebugContext.Scope scope = debug.scope("graph building", graph)) {
                    /*
                     * We want all types to be resolved by the graph builder, i.e., we want classes
                     * referenced by the bytecodes to be loaded and initialized. Since we do not run
                     * the code before static analysis, the classes would otherwise be not loaded
                     * yet and the bytecode parser would only create a graph.
                     */
                    Plugins plugins = new Plugins(new InvocationPlugins());
                    GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
                    /*
                     * For simplicity, we ignore all exception handling during the static analysis.
                     * This is a constraint of this example code, a real static analysis needs to
                     * handle the Graal nodes for throwing and handling exceptions.
                     */
                    graphBuilderConfig = graphBuilderConfig.withBytecodeExceptionMode(BytecodeExceptionMode.OmitAll);
                    /*
                     * We do not want Graal to perform any speculative optimistic optimizations,
                     * i.e., we do not want to use profiling information. Since we do not run the
                     * code before static analysis, the profiling information is empty and therefore
                     * wrong.
                     */
                    OptimisticOptimizations optimisticOpts = OptimisticOptimizations.NONE;

                    GraphBuilderPhase.Instance graphBuilder = new GraphBuilderPhase.Instance(providers, graphBuilderConfig, optimisticOpts, null);
                    graphBuilder.apply(graph);
                } catch (Throwable ex) {
                    debug.handle(ex);
                }

                /*
                 * Build the type flow graph from the Graal graph, i.e., process all nodes that are
                 * deal with objects.
                 */

                TypeFlowBuilder typeFlowBuilder = new TypeFlowBuilder(graph);
                typeFlowBuilder.apply();
            }
        }
    }

    /**
     * The active element during static analysis: types are added until a fixed point is reached.
     * When a new type is added, it is propagated to all usages by putting this element on the
     * {@link StaticAnalysis#addToWorklist worklist}.
     */
    public class TypeFlow extends WorklistEntry {
        private final Set<ResolvedJavaType> types;
        private final Set<TypeFlow> uses;

        protected TypeFlow() {
            types = new HashSet<>();
            uses = new HashSet<>();
        }

        /**
         * Returns the types of this element.
         */
        public Set<ResolvedJavaType> getTypes() {
            return types;
        }

        /**
         * Adds new types to this element. If that changes the state of this element, it is added to
         * the {@link StaticAnalysis#addToWorklist worklist} in order to propagate the added types
         * to all usages.
         */
        protected void addTypes(Set<ResolvedJavaType> newTypes) {
            if (types.addAll(newTypes)) {
                addToWorklist(this);
            }
        }

        /**
         * Adds a new use to this element. All types of this element are propagated to the new
         * usage.
         */
        protected void addUse(TypeFlow use) {
            if (uses.add(use)) {
                use.addTypes(types);
            }
        }

        /**
         * Processing of the worklist element: propagate the types to all usages. That in turn can
         * add the usages to the worklist (if the types of the usage are changed).
         */
        @Override
        protected void process() {
            for (TypeFlow use : uses) {
                use.addTypes(types);
            }
        }
    }

    /**
     * The active element for method invocations. For {@link InvokeKind#Virtual virtual} and
     * {@link InvokeKind#Interface interface} calls, the {@link TypeFlow#getTypes() types} of this
     * node are the receiver types. When a new receiver type is added, a new callee might be added.
     * Adding a new callee means linking the type flow of the actual parameters with the formal
     * parameters of the callee, and linking the return value of the callee with the return value
     * state of the invocation.
     * <p>
     * Statically bindable methods calls ({@link InvokeKind#Static static} and
     * {@link InvokeKind#Special special} calls) have only one callee, but use the same code for
     * simplicity.
     */
    class InvokeTypeFlow extends TypeFlow {
        private final MethodCallTargetNode callTarget;
        private final TypeFlow[] actualParameters;
        private final TypeFlow actualReturn;
        private final Set<ResolvedJavaMethod> callees;

        protected InvokeTypeFlow(MethodCallTargetNode callTarget, TypeFlow[] actualParameterFlows, TypeFlow actualReturnFlow) {
            this.callTarget = callTarget;
            this.actualParameters = actualParameterFlows;
            this.actualReturn = actualReturnFlow;
            this.callees = new HashSet<>();
        }

        private void linkCallee(ResolvedJavaMethod callee) {
            if (callees.add(callee)) {
                /* We have added a new callee. */

                /*
                 * Connect the actual parameters of the invocation with the formal parameters of the
                 * callee.
                 */
                MethodState calleeState = results.lookupMethod(callee);
                for (int i = 0; i < actualParameters.length; i++) {
                    if (actualParameters[i] != null) {
                        actualParameters[i].addUse(calleeState.formalParameters[i]);
                    }
                }

                /*
                 * Connect the formal return value of the callee with the actual return value of the
                 * invocation.
                 */
                if (actualReturn != null) {
                    calleeState.formalReturn.addUse(actualReturn);
                }
                addToWorklist(calleeState);
            }
        }

        @Override
        protected void process() {
            if (callTarget.invokeKind().isDirect()) {
                /* Static and special calls: link the statically known callee method. */
                linkCallee(callTarget.targetMethod());
            } else {
                /* Virtual and interface call: Iterate all receiver types. */
                for (ResolvedJavaType type : getTypes()) {
                    /*
                     * Resolve the method call for one exact receiver type. The method linking
                     * semantics of Java are complicated, but fortunatley we can use the linker of
                     * the hosting Java VM. The Graal API exposes this functionality.
                     */
                    ResolvedJavaMethod method = type.resolveConcreteMethod(callTarget.targetMethod(), callTarget.invoke().getContextType());

                    /*
                     * Since the static analysis is conservative, the list of receiver types can
                     * contain types that actually do not provide the method to be called. Ignore
                     * these.
                     */
                    if (method != null && !method.isAbstract()) {
                        linkCallee(method);
                    }
                }
            }
            super.process();
        }
    }

    /**
     * Converts the Graal nodes of a method to a type flow graph. The main part of the algorithm is
     * a reverse-postorder iteration of the Graal nodes, which is provided by the base class
     * {@link StatelessPostOrderNodeIterator}.
     */
    class TypeFlowBuilder extends StatelessPostOrderNodeIterator {
        private final StructuredGraph graph;
        private final MethodState methodState;
        /**
         * Mapping from Graal nodes to type flows. This uses an efficient Graal-provided
         * {@link NodeMap collection class}.
         */
        private final NodeMap<TypeFlow> typeFlows;

        protected TypeFlowBuilder(StructuredGraph graph) {
            super(graph.start());

            this.graph = graph;
            this.methodState = results.lookupMethod(graph.method());
            this.typeFlows = new NodeMap<>(graph);
        }

        /**
         * Register the type flow node for a Graal node.
         */
        private void registerFlow(ValueNode node, TypeFlow flow) {
            /*
             * We ignore intermediate nodes used by Graal that, e.g., add more type information or
             * encapsulate values flowing out of loops.
             */
            ValueNode unproxiedNode = GraphUtil.unproxify(node);

            assert typeFlows.get(unproxiedNode) == null : "overwriting existing value";
            typeFlows.set(unproxiedNode, flow);
        }

        /**
         * Lookup the type flow node for a Graal node.
         */
        private TypeFlow lookupFlow(ValueNode node) {
            ValueNode unproxiedNode = GraphUtil.unproxify(node);
            TypeFlow result = typeFlows.get(unproxiedNode);
            if (result == null) {
                /*
                 * This is only the prototype of a static analysis, the handling of many Graal nodes
                 * (such as array accesses) is not implemented.
                 */
                throw error("Node is not supported yet by static analysis: " + node.getClass().getName());
            }
            return result;
        }

        private boolean isObject(ValueNode node) {
            return node.getStackKind() == JavaKind.Object;
        }

        @Override
        public void apply() {
            /*
             * Before the reverse-postorder iteration of fixed nodes, we handle some classes of
             * floating nodes.
             */
            for (Node n : graph.getNodes()) {
                if (n instanceof ParameterNode) {
                    /*
                     * Incoming method parameter already have a type flow created by the
                     * MethodState.
                     */
                    ParameterNode node = (ParameterNode) n;
                    if (isObject(node)) {
                        registerFlow(node, methodState.formalParameters[(node.index())]);
                    }
                } else if (n instanceof ValuePhiNode) {
                    /*
                     * Phi functions for loops are cyclic. We create the type flow here (before
                     * processing any loop nodes), but link the phi values only later (after
                     * processing of all loop nodes.
                     */
                    ValuePhiNode node = (ValuePhiNode) n;
                    if (isObject(node)) {
                        registerFlow(node, new TypeFlow());
                    }
                } else if (n instanceof ConstantNode) {
                    /* Constants have a known type. */
                    ConstantNode node = (ConstantNode) n;
                    JavaConstant constant = node.asJavaConstant();
                    if (constant.isNull()) {
                        registerFlow(node, new TypeFlow());
                    }
                }
            }

            super.apply();

            for (Node n : graph.getNodes()) {
                if (n instanceof ValuePhiNode) {
                    /*
                     * Post-processing of phi functions. Now the type flow for all input values has
                     * been created, so we can link the type flows together.
                     */
                    ValuePhiNode node = (ValuePhiNode) n;
                    if (isObject(node)) {
                        TypeFlow phiFlow = lookupFlow(node);
                        for (ValueNode value : node.values()) {
                            lookupFlow(value).addUse(phiFlow);
                        }
                    }
                }
            }
        }

        private void allocation(ValueNode node, ResolvedJavaType type) {
            /*
             * The type flow of allocation nodes is one exact type. This is the source of the
             * fixpoint iteration, the types are propagated downwards from these sources.
             */
            TypeFlow flow = new TypeFlow();
            flow.addTypes(Collections.singleton(type));
            registerFlow(node, flow);
            flow.addUse(results.getAllInstantiatedTypes());
        }

        @Override
        protected void node(FixedNode n) {
            if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                allocation(node, node.instanceClass());
            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                allocation(node, node.elementType().getArrayClass());

            } else if (n instanceof LoadFieldNode) {
                /*
                 * The type flow of a field load is the type flow of the field itself. It
                 * accumulates all types ever stored to the field.
                 */
                LoadFieldNode node = (LoadFieldNode) n;
                if (isObject(node)) {
                    registerFlow(node, results.lookupField(node.field()));
                }
            } else if (n instanceof StoreFieldNode) {
                /*
                 * Connect the type flow of the stored value with the type flow of the field.
                 */
                StoreFieldNode node = (StoreFieldNode) n;
                if (isObject(node.value())) {
                    TypeFlow fieldFlow = results.lookupField(node.field());
                    lookupFlow(node.value()).addUse(fieldFlow);
                }

            } else if (n instanceof ReturnNode) {
                /*
                 * Connect the type flow of the returned value with the formal return type flow of
                 * the MethodState.
                 */
                ReturnNode node = (ReturnNode) n;
                if (node.result() != null && isObject(node.result())) {
                    lookupFlow(node.result()).addUse(methodState.formalReturn);
                }

            } else if (n instanceof Invoke) {
                /*
                 * Create the InvokeTypeFlow, which performs all the linking of actual and formal
                 * parameter values with all identified callees.
                 */
                Invoke invoke = (Invoke) n;
                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();

                TypeFlow[] actualParameters = new TypeFlow[callTarget.arguments().size()];
                for (int i = 0; i < actualParameters.length; i++) {
                    ValueNode actualParam = callTarget.arguments().get(i);
                    if (isObject(actualParam)) {
                        actualParameters[i] = lookupFlow(actualParam);
                    }
                }
                TypeFlow actualReturn = null;
                if (isObject(invoke.asNode())) {
                    actualReturn = new TypeFlow();
                    registerFlow(invoke.asNode(), actualReturn);
                }

                InvokeTypeFlow invokeFlow = new InvokeTypeFlow(callTarget, actualParameters, actualReturn);

                if (callTarget.invokeKind().isIndirect()) {
                    /*
                     * For virtual and interface calls, new receiver types can lead to new callees.
                     * Connect the type flow of the receiver with the invocation flow.
                     */
                    lookupFlow(callTarget.arguments().get(0)).addUse(invokeFlow);
                }
                /*
                 * Ensure the invocation is on the worklist at least once, even if it is a static
                 * call with not parameters that does not involve any type flows.
                 */
                addToWorklist(invokeFlow);
            }
        }
    }
}
