/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.StableOptionValue;

/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 */
// @formatter:off
public final class GraalOptions {

    @Option(help = "Use compiler intrinsifications.", type = OptionType.Debug)
    public static final OptionValue<Boolean> Intrinsify = new OptionValue<>(true);

    @Option(help = "Inline calls with monomorphic type profile.", type = OptionType.Expert)
    public static final OptionValue<Boolean> InlineMonomorphicCalls = new OptionValue<>(true);

    @Option(help = "Inline calls with polymorphic type profile.", type = OptionType.Expert)
    public static final OptionValue<Boolean> InlinePolymorphicCalls = new OptionValue<>(true);

    @Option(help = "Inline calls with megamorphic type profile (i.e., not all types could be recorded).", type = OptionType.Expert)
    public static final OptionValue<Boolean> InlineMegamorphicCalls = new OptionValue<>(true);

    @Option(help = "Maximum desired size of the compiler graph in nodes.", type = OptionType.User)
    public static final OptionValue<Integer> MaximumDesiredSize = new OptionValue<>(20000);

    @Option(help = "Minimum probability for methods to be inlined for megamorphic type profiles.", type = OptionType.Expert)
    public static final OptionValue<Double> MegamorphicInliningMinMethodProbability = new OptionValue<>(0.33D);

    @Option(help = "Maximum level of recursive inlining.", type = OptionType.Expert)
    public static final OptionValue<Integer> MaximumRecursiveInlining = new OptionValue<>(5);

    @Option(help = "Graphs with less than this number of nodes are trivial and therefore always inlined.", type = OptionType.Expert)
    public static final OptionValue<Integer> TrivialInliningSize = new OptionValue<>(10);

    @Option(help = "Inlining is explored up to this number of nodes in the graph for each call site.", type = OptionType.Expert)
    public static final OptionValue<Integer> MaximumInliningSize = new OptionValue<>(300);

    @Option(help = "If the previous low-level graph size of the method exceeds the threshold, it is not inlined.", type = OptionType.Expert)
    public static final OptionValue<Integer> SmallCompiledLowLevelGraphSize = new OptionValue<>(300);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Double> LimitInlinedInvokes = new OptionValue<>(5.0);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> InlineEverything = new OptionValue<>(false);

    // escape analysis settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> PartialEscapeAnalysis = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Integer> EscapeAnalysisIterations = new OptionValue<>(2);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Integer> EscapeAnalysisLoopCutoff = new OptionValue<>(20);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<String> EscapeAnalyzeOnly = new OptionValue<>(null);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Integer> MaximumEscapeAnalysisArrayLength = new OptionValue<>(32);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> PEAInliningHints = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Double> TailDuplicationProbability = new OptionValue<>(0.5);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Integer> TailDuplicationTrivialSize = new OptionValue<>(1);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Integer> DeoptsToDisableOptimisticOptimization = new OptionValue<>(40);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> LoopPeeling = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> ReassociateInvariants = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> FullUnroll = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> LoopUnswitch = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Float> MinimumPeelProbability = new OptionValue<>(0.35f);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Integer> LoopMaxUnswitch = new OptionValue<>(3);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseLoopLimitChecks = new OptionValue<>(true);

    // debugging settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> ZapStackOnMethodEntry = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> DeoptALot = new OptionValue<>(false);

    @Option(help = "Stress the code emitting explicit exception throwing code.", type = OptionType.Debug)
    public static final OptionValue<Boolean> StressExplicitExceptionCode = new OptionValue<>(false);

    @Option(help = "Stress the code emitting invokes with explicit exception edges.", type = OptionType.Debug)
    public static final OptionValue<Boolean> StressInvokeWithExceptionNode = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> VerifyPhases = new OptionValue<>(false);

    // Debug settings:
    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Integer> GCDebugStartCycle = new OptionValue<>(-1);

    @Option(help = "Perform platform dependent validation of the Java heap at returns", type = OptionType.Debug)
    public static final OptionValue<Boolean> VerifyHeapAtReturn = new OptionValue<>(false);

    // Other printing settings
    @Option(help = "Print profiling information when parsing a method's bytecode", type = OptionType.Debug)
    public static final OptionValue<Boolean> PrintProfilingInformation = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> TraceEscapeAnalysis = new StableOptionValue<>(false);

    // HotSpot command line options
    @Option(help = "Print inlining optimizations", type = OptionType.Debug)
    public static final OptionValue<Boolean> HotSpotPrintInlining = new OptionValue<>(false);

    // Register allocator debugging
    @Option(help = "Comma separated list of registers that register allocation is limited to.", type = OptionType.Debug)
    public static final OptionValue<String> RegisterPressure = new OptionValue<>(null);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> ConditionalElimination = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> RemoveNeverExecutedCode = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseExceptionProbability = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseExceptionProbabilityForOperations = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OmitHotExceptionStacktrace = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> GenSafepoints = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> GenLoopSafepoints = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseTypeCheckHints = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> InlineVTableStubs = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> AlwaysInlineVTableStubs = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> ResolveClassBeforeStaticInvoke = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> CanOmitFrame = new OptionValue<>(true);

    // Ahead of time compilation
    @Option(help = "Try to avoid emitting code where patching is required", type = OptionType.Expert)
    public static final OptionValue<Boolean> ImmutableCode = new OptionValue<>(false);

    @Option(help = "Generate position independent code", type = OptionType.Expert)
    public static final OptionValue<Boolean> GeneratePIC = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> CallArrayCopy = new OptionValue<>(true);

    // Runtime settings
    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> SupportJsrBytecodes = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionValue<Boolean> OptAssumptions = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptConvertDeoptsToGuards = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptReadElimination = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Integer> ReadEliminationMaxLoopVisits = new OptionValue<>(5);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptDeoptimizationGrouping = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptScheduleOutOfLoops = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptEliminateGuards = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptImplicitNullChecks = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptClearNonLiveLocals = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptLoopTransform = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptFloatingReads = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptEliminatePartiallyRedundantGuards = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptFilterProfiledTypes = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptDevirtualizeInvokesOptimistically = new OptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> OptPushThroughPi = new OptionValue<>(true);

    @Option(help = "Allow backend to match complex expressions.", type = OptionType.Debug)
    public static final OptionValue<Boolean> MatchExpressions = new OptionValue<>(true);

    @Option(help = "Enable counters for various paths in snippets.", type = OptionType.Debug)
    public static final OptionValue<Boolean> SnippetCounters = new OptionValue<>(false);

    @Option(help = "Eagerly construct extra snippet info.", type = OptionType.Debug)
    public static final OptionValue<Boolean> EagerSnippets = new OptionValue<>(false);

    @Option(help = "Use a cache for snippet graphs.", type = OptionType.Debug)
    public static final OptionValue<Boolean> UseSnippetGraphCache = new OptionValue<>(true);

    @Option(help = "Enable expensive assertions", type = OptionType.Debug)
    public static final OptionValue<Boolean> DetailedAsserts = new StableOptionValue<Boolean>() {
        @Override
        protected Boolean defaultValue() {
            boolean enabled = false;
            // turn detailed assertions on when the general assertions are on (misusing the assert keyword for this)
            assert (enabled = true) == true;
            return enabled;
        }
    };

    @Option(help = "Enable Graal instrumentation")
    public static final OptionValue<Boolean> UseGraalInstrumentation = new OptionValue<>(false);

    @Option(help = "Enable experimental Trace Register Allocation.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceRA = new OptionValue<>(false);

}
