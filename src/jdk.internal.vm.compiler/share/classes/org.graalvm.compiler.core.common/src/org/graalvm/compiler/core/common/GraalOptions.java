/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 */
// @formatter:off
public final class GraalOptions {

    @Option(help = "Use compiler intrinsifications.", type = OptionType.Debug)
    public static final OptionKey<Boolean> Intrinsify = new OptionKey<>(true);

    @Option(help = "Inline calls with monomorphic type profile.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineMonomorphicCalls = new OptionKey<>(true);

    @Option(help = "Inline calls with polymorphic type profile.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlinePolymorphicCalls = new OptionKey<>(true);

    @Option(help = "Inline calls with megamorphic type profile (i.e., not all types could be recorded).", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineMegamorphicCalls = new OptionKey<>(true);

    @Option(help = "Maximum desired size of the compiler graph in nodes.", type = OptionType.User)
    public static final OptionKey<Integer> MaximumDesiredSize = new OptionKey<>(20000);

    @Option(help = "Minimum probability for methods to be inlined for megamorphic type profiles.", type = OptionType.Expert)
    public static final OptionKey<Double> MegamorphicInliningMinMethodProbability = new OptionKey<>(0.33D);

    @Option(help = "Maximum level of recursive inlining.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumRecursiveInlining = new OptionKey<>(5);

    @Option(help = "Graphs with less than this number of nodes are trivial and therefore always inlined.", type = OptionType.Expert)
    public static final OptionKey<Integer> TrivialInliningSize = new OptionKey<>(10);

    @Option(help = "Inlining is explored up to this number of nodes in the graph for each call site.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumInliningSize = new OptionKey<>(300);

    @Option(help = "If the previous low-level graph size of the method exceeds the threshold, it is not inlined.", type = OptionType.Expert)
    public static final OptionKey<Integer> SmallCompiledLowLevelGraphSize = new OptionKey<>(300);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Double> LimitInlinedInvokes = new OptionKey<>(5.0);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineEverything = new OptionKey<>(false);

    // escape analysis settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PartialEscapeAnalysis = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> EscapeAnalysisIterations = new OptionKey<>(2);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> EscapeAnalysisLoopCutoff = new OptionKey<>(20);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<String> EscapeAnalyzeOnly = new OptionKey<>(null);

    @Option(help = "The maximum length of an array that will be escape analyzed.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumEscapeAnalysisArrayLength = new OptionKey<>(128);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PEAInliningHints = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Double> TailDuplicationProbability = new OptionKey<>(0.5);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Integer> TailDuplicationTrivialSize = new OptionKey<>(1);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Integer> DeoptsToDisableOptimisticOptimization = new OptionKey<>(40);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> LoopPeeling = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ReassociateInvariants = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> FullUnroll = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> LoopUnswitch = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> PartialUnroll = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Float> MinimumPeelProbability = new OptionKey<>(0.35f);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Integer> LoopMaxUnswitch = new OptionKey<>(3);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseLoopLimitChecks = new OptionKey<>(true);

    // debugging settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ZapStackOnMethodEntry = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> DeoptALot = new OptionKey<>(false);

    @Option(help = "Stress the code emitting explicit exception throwing code.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressExplicitExceptionCode = new OptionKey<>(false);

    @Option(help = "Stress the code emitting invokes with explicit exception edges.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressInvokeWithExceptionNode = new OptionKey<>(false);

    @Option(help = "Stress the code by emitting reads at earliest instead of latest point.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressTestEarlyReads = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyPhases = new OptionKey<>(false);

    // Debug settings:
    @Option(help = "Start tracing compiled GC barriers after N garbage collections (disabled if N <= 0).", type = OptionType.Debug)
    public static final OptionKey<Integer> GCDebugStartCycle = new OptionKey<>(-1);

    @Option(help = "Perform platform dependent validation of the Java heap at returns", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyHeapAtReturn = new OptionKey<>(false);

    // Other printing settings
    @Option(help = "Print profiling information when parsing a method's bytecode", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintProfilingInformation = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceEscapeAnalysis = new OptionKey<>(false);

    // HotSpot command line options
    @Option(help = "Print inlining optimizations", type = OptionType.Debug)
    public static final OptionKey<Boolean> HotSpotPrintInlining = new OptionKey<>(false);

    // Register allocator debugging
    @Option(help = "Comma separated list of registers that register allocation is limited to.", type = OptionType.Debug)
    public static final OptionKey<String> RegisterPressure = new OptionKey<>(null);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ConditionalElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> RawConditionalElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ReplaceInputsWithConstantsBasedOnStamps = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> RemoveNeverExecutedCode = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseExceptionProbability = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OmitHotExceptionStacktrace = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> GenSafepoints = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> GenLoopSafepoints = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseTypeCheckHints = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineVTableStubs = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Boolean> AlwaysInlineVTableStubs = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> CanOmitFrame = new OptionKey<>(true);

    // Ahead of time compilation
    @Option(help = "Try to avoid emitting code where patching is required", type = OptionType.Expert)
    public static final OptionKey<Boolean> ImmutableCode = new OptionKey<>(false);

    @Option(help = "Generate position independent code", type = OptionType.Expert)
    public static final OptionKey<Boolean> GeneratePIC = new OptionKey<>(false);

    // Runtime settings
    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Boolean> SupportJsrBytecodes = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptAssumptions = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptConvertDeoptsToGuards = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptReadElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> ReadEliminationMaxLoopVisits = new OptionKey<>(5);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptDeoptimizationGrouping = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptScheduleOutOfLoops = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> GuardPriorities = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptEliminateGuards = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptImplicitNullChecks = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptClearNonLiveLocals = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptLoopTransform = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptFloatingReads = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptEliminatePartiallyRedundantGuards = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptFilterProfiledTypes = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptDevirtualizeInvokesOptimistically = new OptionKey<>(true);

    @Option(help = "Track the NodeSourcePosition.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TrackNodeSourcePosition = new OptionKey<>(false);

    @Option(help = "Track source stack trace where a node was inserted into the graph.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TrackNodeInsertion = new OptionKey<>(false);

    @Option(help = "Allow backend to match complex expressions.", type = OptionType.Debug)
    public static final OptionKey<Boolean> MatchExpressions = new OptionKey<>(true);

    @Option(help = "Enable counters for various paths in snippets.", type = OptionType.Debug)
    public static final OptionKey<Boolean> SnippetCounters = new OptionKey<>(false);

    @Option(help = "Eagerly construct extra snippet info.", type = OptionType.Debug)
    public static final OptionKey<Boolean> EagerSnippets = new OptionKey<>(false);

    @Option(help = "Use a cache for snippet graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseSnippetGraphCache = new OptionKey<>(true);

    @Option(help = "file:doc-files/TraceInliningHelp.txt", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Enable inlining decision tracing in stubs and snippets.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceInliningForStubsAndSnippets = new OptionKey<>(false);
}
