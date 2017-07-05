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

import static org.graalvm.compiler.core.GraalCompiler.emitBackEnd;
import static org.graalvm.compiler.core.GraalCompiler.emitFrontEnd;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.StubStartNode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.profiling.MoveProfilingPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.Suites;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

//JaCoCo Exclude

/**
 * Base class for implementing some low level code providing the out-of-line slow path for a snippet
 * and/or a callee saved call to a HotSpot C/C++ runtime function or even a another compiled Java
 * method.
 */
public abstract class Stub {

    private static final List<Stub> stubs = new ArrayList<>();

    /**
     * The linkage information for a call to this stub from compiled code.
     */
    protected final HotSpotForeignCallLinkage linkage;

    /**
     * The code installed for the stub.
     */
    protected InstalledCode code;

    /**
     * The registers destroyed by this stub (from the caller's perspective).
     */
    private Set<Register> destroyedCallerRegisters;

    public void initDestroyedCallerRegisters(Set<Register> registers) {
        assert registers != null;
        assert destroyedCallerRegisters == null || registers.equals(destroyedCallerRegisters) : "cannot redefine";
        destroyedCallerRegisters = registers;
    }

    /**
     * Gets the registers destroyed by this stub from a caller's perspective. These are the
     * temporaries of this stub and must thus be caller saved by a callers of this stub.
     */
    public Set<Register> getDestroyedCallerRegisters() {
        assert destroyedCallerRegisters != null : "not yet initialized";
        return destroyedCallerRegisters;
    }

    /**
     * Determines if this stub preserves all registers apart from those it
     * {@linkplain #getDestroyedCallerRegisters() destroys}.
     */
    public boolean preservesRegisters() {
        return true;
    }

    protected final HotSpotProviders providers;

    /**
     * Creates a new stub.
     *
     * @param linkage linkage details for a call to the stub
     */
    public Stub(HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this.linkage = linkage;
        this.providers = providers;
        stubs.add(this);
    }

    /**
     * Gets an immutable view of all stubs that have been created.
     */
    public static Collection<Stub> getStubs() {
        return Collections.unmodifiableList(stubs);
    }

    /**
     * Gets the linkage for a call to this stub from compiled code.
     */
    public HotSpotForeignCallLinkage getLinkage() {
        return linkage;
    }

    public RegisterConfig getRegisterConfig() {
        return null;
    }

    /**
     * Gets the graph that from which the code for this stub will be compiled.
     *
     * @param compilationId unique compilation id for the stub
     */
    protected abstract StructuredGraph getGraph(CompilationIdentifier compilationId);

    @Override
    public String toString() {
        return "Stub<" + linkage.getDescriptor() + ">";
    }

    /**
     * Gets the method the stub's code will be associated with once installed. This may be null.
     */
    protected abstract ResolvedJavaMethod getInstalledCodeOwner();

    /**
     * Gets a context object for the debug scope created when producing the code for this stub.
     */
    protected abstract Object debugScopeContext();

    /**
     * Gets the code for this stub, compiling it first if necessary.
     */
    @SuppressWarnings("try")
    public synchronized InstalledCode getCode(final Backend backend) {
        if (code == null) {
            try (Scope d = Debug.sandbox("CompilingStub", DebugScope.getConfig(), providers.getCodeCache(), debugScopeContext())) {
                CodeCacheProvider codeCache = providers.getCodeCache();
                CompilationResult compResult = buildCompilationResult(backend);
                try (Scope s = Debug.scope("CodeInstall", compResult)) {
                    assert destroyedCallerRegisters != null;
                    // Add a GeneratePIC check here later, we don't want to install
                    // code if we don't have a corresponding VM global symbol.
                    HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(null, null, compResult);
                    code = codeCache.installCode(null, compiledCode, null, null, false);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            assert code != null : "error installing stub " + this;
        }

        return code;
    }

    @SuppressWarnings("try")
    private CompilationResult buildCompilationResult(final Backend backend) {
        CompilationResult compResult = new CompilationResult(toString(), GeneratePIC.getValue());
        final StructuredGraph graph = getGraph(getStubCompilationId());

        // Stubs cannot be recompiled so they cannot be compiled with assumptions
        assert graph.getAssumptions() == null;

        if (!(graph.start() instanceof StubStartNode)) {
            StubStartNode newStart = graph.add(new StubStartNode(Stub.this));
            newStart.setStateAfter(graph.start().stateAfter());
            graph.replaceFixed(graph.start(), newStart);
        }

        try (Scope s0 = Debug.scope("StubCompilation", graph, providers.getCodeCache())) {
            Suites suites = createSuites();
            emitFrontEnd(providers, backend, graph, providers.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, DefaultProfilingInfo.get(TriState.UNKNOWN), suites);
            LIRSuites lirSuites = createLIRSuites();
            emitBackEnd(graph, Stub.this, getInstalledCodeOwner(), backend, compResult, CompilationResultBuilderFactory.Default, getRegisterConfig(), lirSuites);
            assert checkStubInvariants(compResult);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return compResult;
    }

    /**
     * Gets a {@link CompilationResult} that can be used for code generation. Required for AOT.
     */
    @SuppressWarnings("try")
    public CompilationResult getCompilationResult(final Backend backend) {
        try (Scope d = Debug.sandbox("CompilingStub", DebugScope.getConfig(), providers.getCodeCache(), debugScopeContext())) {
            return buildCompilationResult(backend);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public CompilationIdentifier getStubCompilationId() {
        return new StubCompilationIdentifier(this);
    }

    /**
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        assert compResult.getExceptionHandlers().isEmpty() : this;

        // Stubs cannot be recompiled so they cannot be compiled with
        // assumptions and there is no point in recording evol_method dependencies
        assert compResult.getAssumptions() == null : "stubs should not use assumptions: " + this;

        for (DataPatch data : compResult.getDataPatches()) {
            if (data.reference instanceof ConstantReference) {
                ConstantReference ref = (ConstantReference) data.reference;
                if (ref.getConstant() instanceof HotSpotMetaspaceConstant) {
                    HotSpotMetaspaceConstant c = (HotSpotMetaspaceConstant) ref.getConstant();
                    if (c.asResolvedJavaType() != null && c.asResolvedJavaType().getName().equals("[I")) {
                        // special handling for NewArrayStub
                        // embedding the type '[I' is safe, since it is never unloaded
                        continue;
                    }
                }
            }

            assert !(data.reference instanceof ConstantReference) : this + " cannot have embedded object or metadata constant: " + data.reference;
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : this + " cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotForeignCallLinkage : this + " cannot have non runtime call: " + call.target;
            HotSpotForeignCallLinkage callLinkage = (HotSpotForeignCallLinkage) call.target;
            assert !callLinkage.isCompiledStub() || callLinkage.getDescriptor().equals(UNCOMMON_TRAP_HANDLER) : this + " cannot call compiled stub " + callLinkage;
        }
        return true;
    }

    protected Suites createSuites() {
        Suites defaultSuites = providers.getSuites().getDefaultSuites();
        return new Suites(new PhaseSuite<>(), defaultSuites.getMidTier(), defaultSuites.getLowTier());
    }

    protected LIRSuites createLIRSuites() {
        LIRSuites lirSuites = new LIRSuites(providers.getSuites().getDefaultLIRSuites());
        ListIterator<LIRPhase<PostAllocationOptimizationContext>> moveProfiling = lirSuites.getPostAllocationOptimizationStage().findPhase(MoveProfilingPhase.class);
        if (moveProfiling != null) {
            moveProfiling.remove();
        }
        return lirSuites;
    }
}
