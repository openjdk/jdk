/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.VerifyPhases;

import java.util.ListIterator;

import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotInstructionProfiling;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.phases.AheadOfTimeVerificationPhase;
import org.graalvm.compiler.hotspot.phases.LoadJavaMirrorWithKlassPhase;
import org.graalvm.compiler.hotspot.phases.WriteBarrierAdditionPhase;
import org.graalvm.compiler.hotspot.phases.WriteBarrierVerificationPhase;
import org.graalvm.compiler.hotspot.phases.aot.AOTInliningPolicy;
import org.graalvm.compiler.hotspot.phases.aot.EliminateRedundantInitializationPhase;
import org.graalvm.compiler.hotspot.phases.aot.ReplaceConstantNodesPhase;
import org.graalvm.compiler.hotspot.phases.profiling.FinalizeProfileNodesPhase;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.java.SuitesProviderBase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.SimplifyingGraphDecoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ExpandLogicPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesCreator;

/**
 * HotSpot implementation of {@link SuitesCreator}.
 */
public class HotSpotSuitesProvider extends SuitesProviderBase {

    protected final GraalHotSpotVMConfig config;
    protected final HotSpotGraalRuntimeProvider runtime;

    private final AddressLowering addressLowering;
    private final SuitesCreator defaultSuitesCreator;

    public HotSpotSuitesProvider(SuitesCreator defaultSuitesCreator, GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, AddressLowering addressLowering) {
        this.defaultSuitesCreator = defaultSuitesCreator;
        this.config = config;
        this.runtime = runtime;
        this.addressLowering = addressLowering;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite();
    }

    @Override
    public Suites createSuites() {
        Suites ret = defaultSuitesCreator.createSuites();

        if (ImmutableCode.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase(config.classMirrorOffset, config.useCompressedOops ? config.getOopEncoding() : null));
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
            if (GeneratePIC.getValue()) {
                // EliminateRedundantInitializationPhase must happen before the first lowering.
                ListIterator<BasePhase<? super HighTierContext>> highTierLowering = ret.getHighTier().findPhase(LoweringPhase.class);
                highTierLowering.previous();
                highTierLowering.add(new EliminateRedundantInitializationPhase());
                if (HotSpotAOTProfilingPlugin.Options.TieredAOT.getValue()) {
                    highTierLowering.add(new FinalizeProfileNodesPhase(HotSpotAOTProfilingPlugin.Options.TierAInvokeInlineeNotifyFreqLog.getValue()));
                }
                ret.getMidTier().findPhase(LoopSafepointInsertionPhase.class).add(new ReplaceConstantNodesPhase());

                // Replace inlining policy
                ListIterator<BasePhase<? super HighTierContext>> iter = ret.getHighTier().findPhase(InliningPhase.class);
                InliningPhase inlining = (InliningPhase) iter.previous();
                CanonicalizerPhase canonicalizer = inlining.getCanonicalizer();
                iter.set(new InliningPhase(new AOTInliningPolicy(null), canonicalizer));
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase(config));
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase(config));
        }

        ret.getLowTier().findPhase(ExpandLogicPhase.class).add(new AddressLoweringPhase(addressLowering));

        return ret;
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = defaultSuitesCreator.getDefaultGraphBuilderSuite().copy();
        assert appendGraphEncoderTest(suite);
        return suite;
    }

    /**
     * When assertions are enabled, we encode and decode every parsed graph, to ensure that the
     * encoding and decoding process work correctly. The decoding performs canonicalization during
     * decoding, so the decoded graph can be different than the encoded graph - we cannot check them
     * for equality here. However, the encoder {@link GraphEncoder#verifyEncoding verifies the
     * encoding itself}, i.e., performs a decoding without canoncialization and checks the graphs
     * for equality.
     */
    private boolean appendGraphEncoderTest(PhaseSuite<HighTierContext> suite) {
        suite.appendPhase(new BasePhase<HighTierContext>() {
            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, runtime.getTarget().arch);

                SimplifyingGraphDecoder graphDecoder = new SimplifyingGraphDecoder(context.getMetaAccess(), context.getConstantReflection(), context.getConstantFieldProvider(),
                                context.getStampProvider(), !ImmutableCode.getValue(), runtime.getTarget().arch);
                StructuredGraph targetGraph = new StructuredGraph(graph.method(), AllowAssumptions.YES, INVALID_COMPILATION_ID);
                graphDecoder.decode(targetGraph, encodedGraph);
            }

            @Override
            protected CharSequence getName() {
                return "VerifyEncodingDecoding";
            }
        });
        return true;
    }

    /**
     * Modifies a given {@link GraphBuilderConfiguration} to record per node source information.
     *
     * @param gbs the current graph builder suite to modify
     */
    public static PhaseSuite<HighTierContext> withNodeSourcePosition(PhaseSuite<HighTierContext> gbs) {
        PhaseSuite<HighTierContext> newGbs = gbs.copy();
        GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
        GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
        GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig.withNodeSourcePosition(true));
        newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
        return newGbs;
    }

    @Override
    public LIRSuites createLIRSuites() {
        LIRSuites suites = defaultSuitesCreator.createLIRSuites();
        String profileInstructions = HotSpotBackend.Options.ASMInstructionProfiling.getValue();
        if (profileInstructions != null) {
            suites.getPostAllocationOptimizationStage().appendPhase(new HotSpotInstructionProfiling(profileInstructions));
        }
        return suites;
    }
}
