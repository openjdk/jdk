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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCallee;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.replacements.nodes.ReadRegisterNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A {@linkplain #getGraph generated} stub for a {@link Transition non-leaf} foreign call from
 * compiled code. A stub is required for such calls as the caller may be scheduled for
 * deoptimization while the call is in progress. And since these are foreign/runtime calls on slow
 * paths, we don't want to force the register allocator to spill around the call. As such, this stub
 * saves and restores all allocatable registers. It also
 * {@linkplain StubUtil#handlePendingException(Word, boolean) handles} any exceptions raised during
 * the foreign call.
 */
public class ForeignCallStub extends Stub {

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;

    /**
     * The target of the call.
     */
    private final HotSpotForeignCallLinkage target;

    /**
     * Specifies if the JavaThread value for the current thread is to be prepended to the arguments
     * for the call to {@link #target}.
     */
    protected final boolean prependThread;

    /**
     * Creates a stub for a call to code at a given address.
     *
     * @param address the address of the code to call
     * @param descriptor the signature of the call to this stub
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     * @param reexecutable specifies if the stub call can be re-executed without (meaningful) side
     *            effects. Deoptimization will not return to a point before a stub call that cannot
     *            be re-executed.
     * @param killedLocations the memory locations killed by the stub call
     */
    public ForeignCallStub(HotSpotJVMCIRuntimeProvider runtime, HotSpotProviders providers, long address, ForeignCallDescriptor descriptor, boolean prependThread, Transition transition,
                    boolean reexecutable, LocationIdentity... killedLocations) {
        super(providers, HotSpotForeignCallLinkageImpl.create(providers.getMetaAccess(), providers.getCodeCache(), providers.getWordTypes(), providers.getForeignCalls(), descriptor, 0L,
                        PRESERVES_REGISTERS, JavaCall, JavaCallee, transition, reexecutable, killedLocations));
        this.jvmciRuntime = runtime;
        this.prependThread = prependThread;
        Class<?>[] targetParameterTypes = createTargetParameters(descriptor);
        ForeignCallDescriptor targetSig = new ForeignCallDescriptor(descriptor.getName() + ":C", descriptor.getResultType(), targetParameterTypes);
        target = HotSpotForeignCallLinkageImpl.create(providers.getMetaAccess(), providers.getCodeCache(), providers.getWordTypes(), providers.getForeignCalls(), targetSig, address,
                        DESTROYS_REGISTERS, NativeCall, NativeCall, transition, reexecutable, killedLocations);
    }

    /**
     * Gets the linkage information for the call from this stub.
     */
    public HotSpotForeignCallLinkage getTargetLinkage() {
        return target;
    }

    private Class<?>[] createTargetParameters(ForeignCallDescriptor descriptor) {
        Class<?>[] parameters = descriptor.getArgumentTypes();
        if (prependThread) {
            Class<?>[] newParameters = new Class<?>[parameters.length + 1];
            System.arraycopy(parameters, 0, newParameters, 1, parameters.length);
            newParameters[0] = Word.class;
            return newParameters;
        }
        return parameters;
    }

    @Override
    protected ResolvedJavaMethod getInstalledCodeOwner() {
        return null;
    }

    private class DebugScopeContext implements JavaMethod, JavaMethodContext {
        @Override
        public JavaMethod asJavaMethod() {
            return this;
        }

        @Override
        public Signature getSignature() {
            ForeignCallDescriptor d = linkage.getDescriptor();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            Class<?>[] arguments = d.getArgumentTypes();
            ResolvedJavaType[] parameters = new ResolvedJavaType[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                parameters[i] = metaAccess.lookupJavaType(arguments[i]);
            }
            return new HotSpotSignature(jvmciRuntime, metaAccess.lookupJavaType(d.getResultType()), parameters);
        }

        @Override
        public String getName() {
            return linkage.getDescriptor().getName();
        }

        @Override
        public JavaType getDeclaringClass() {
            return providers.getMetaAccess().lookupJavaType(ForeignCallStub.class);
        }

        @Override
        public String toString() {
            return format("ForeignCallStub<%n(%p)>");
        }
    }

    @Override
    protected Object debugScopeContext() {
        return new DebugScopeContext() {

        };
    }

    /**
     * Creates a graph for this stub.
     * <p>
     * If the stub returns an object, the graph created corresponds to this pseudo code:
     *
     * <pre>
     *     Object foreignFunctionStub(args...) {
     *         foreignFunction(currentThread,  args);
     *         if (clearPendingException(thread())) {
     *             getAndClearObjectResult(thread());
     *             DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
     *         }
     *         return verifyObject(getAndClearObjectResult(thread()));
     *     }
     * </pre>
     *
     * If the stub returns a primitive or word, the graph created corresponds to this pseudo code
     * (using {@code int} as the primitive return type):
     *
     * <pre>
     *     int foreignFunctionStub(args...) {
     *         int result = foreignFunction(currentThread,  args);
     *         if (clearPendingException(thread())) {
     *             DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
     *         }
     *         return result;
     *     }
     * </pre>
     *
     * If the stub is void, the graph created corresponds to this pseudo code:
     *
     * <pre>
     *     void foreignFunctionStub(args...) {
     *         foreignFunction(currentThread,  args);
     *         if (clearPendingException(thread())) {
     *             DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
     *         }
     *     }
     * </pre>
     *
     * In each example above, the {@code currentThread} argument is the C++ JavaThread value (i.e.,
     * %r15 on AMD64) and is only prepended if {@link #prependThread} is true.
     */
    @Override
    protected StructuredGraph getGraph(CompilationIdentifier compilationId) {
        WordTypes wordTypes = providers.getWordTypes();
        Class<?>[] args = linkage.getDescriptor().getArgumentTypes();
        boolean isObjectResult = !LIRKind.isValue(linkage.getOutgoingCallingConvention().getReturn());

        StructuredGraph graph = new StructuredGraph(toString(), null, AllowAssumptions.NO, compilationId);
        graph.disableUnsafeAccessTracking();

        GraphKit kit = new GraphKit(graph, providers, wordTypes, providers.getGraphBuilderPlugins());
        ParameterNode[] params = createParameters(kit, args);

        ReadRegisterNode thread = kit.append(new ReadRegisterNode(providers.getRegisters().getThreadRegister(), wordTypes.getWordKind(), true, false));
        ValueNode result = createTargetCall(kit, params, thread);
        kit.createInvoke(StubUtil.class, "handlePendingException", thread, ConstantNode.forBoolean(isObjectResult, graph));
        if (isObjectResult) {
            InvokeNode object = kit.createInvoke(HotSpotReplacementsUtil.class, "getAndClearObjectResult", thread);
            result = kit.createInvoke(StubUtil.class, "verifyObject", object);
        }
        kit.append(new ReturnNode(linkage.getDescriptor().getResultType() == void.class ? null : result));

        if (Debug.isDumpEnabled(Debug.INFO_LOG_LEVEL)) {
            Debug.dump(Debug.INFO_LOG_LEVEL, graph, "Initial stub graph");
        }

        kit.inlineInvokes();

        if (Debug.isDumpEnabled(Debug.INFO_LOG_LEVEL)) {
            Debug.dump(Debug.INFO_LOG_LEVEL, graph, "Stub graph before compilation");
        }

        return graph;
    }

    private ParameterNode[] createParameters(GraphKit kit, Class<?>[] args) {
        ParameterNode[] params = new ParameterNode[args.length];
        ResolvedJavaType accessingClass = providers.getMetaAccess().lookupJavaType(getClass());
        for (int i = 0; i < args.length; i++) {
            ResolvedJavaType type = providers.getMetaAccess().lookupJavaType(args[i]).resolve(accessingClass);
            StampPair stamp = StampFactory.forDeclaredType(kit.getGraph().getAssumptions(), type, false);
            ParameterNode param = kit.unique(new ParameterNode(i, stamp));
            params[i] = param;
        }
        return params;
    }

    private StubForeignCallNode createTargetCall(GraphKit kit, ParameterNode[] params, ReadRegisterNode thread) {
        if (prependThread) {
            ValueNode[] targetArguments = new ValueNode[1 + params.length];
            targetArguments[0] = thread;
            System.arraycopy(params, 0, targetArguments, 1, params.length);
            return kit.append(new StubForeignCallNode(providers.getForeignCalls(), target.getDescriptor(), targetArguments));
        } else {
            return kit.append(new StubForeignCallNode(providers.getForeignCalls(), target.getDescriptor(), params));
        }
    }
}
