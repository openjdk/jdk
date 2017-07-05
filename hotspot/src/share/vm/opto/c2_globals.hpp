/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

//
// Defines all globals flags used by the server compiler.
//

#define C2_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct) \
                                                                            \
  notproduct(intx, CompileZapFirst, 0,                                      \
          "If +ZapDeadCompiledLocals, "                                     \
          "skip this many before compiling in zap calls")                   \
                                                                            \
  notproduct(intx, CompileZapLast, -1,                                      \
          "If +ZapDeadCompiledLocals, "                                     \
          "compile this many after skipping (incl. skip count, -1 = all)")  \
                                                                            \
  notproduct(intx, ZapDeadCompiledLocalsFirst, 0,                           \
          "If +ZapDeadCompiledLocals, "                                     \
          "skip this many before really doing it")                          \
                                                                            \
  notproduct(intx, ZapDeadCompiledLocalsLast, -1,                           \
          "If +ZapDeadCompiledLocals, "                                     \
          "do this many after skipping (incl. skip count, -1 = all)")       \
                                                                            \
  develop(intx, OptoPrologueNops, 0,                                        \
          "Insert this many extra nop instructions "                        \
          "in the prologue of every nmethod")                               \
                                                                            \
  product_pd(intx, InteriorEntryAlignment,                                  \
          "Code alignment for interior entry points "                       \
          "in generated code (in bytes)")                                   \
                                                                            \
  product_pd(intx, OptoLoopAlignment,                                       \
          "Align inner loops to zero relative to this modulus")             \
                                                                            \
  product(intx, MaxLoopPad, (OptoLoopAlignment-1),                          \
          "Align a loop if padding size in bytes is less or equal to this value") \
                                                                            \
  product(intx, NumberOfLoopInstrToAlign, 4,                                \
          "Number of first instructions in a loop to align")                \
                                                                            \
  notproduct(intx, IndexSetWatch, 0,                                        \
          "Trace all operations on this IndexSet (-1 means all, 0 none)")   \
                                                                            \
  develop(intx, OptoNodeListSize, 4,                                        \
          "Starting allocation size of Node_List data structures")          \
                                                                            \
  develop(intx, OptoBlockListSize, 8,                                       \
          "Starting allocation size of Block_List data structures")         \
                                                                            \
  develop(intx, OptoPeepholeAt, -1,                                         \
          "Apply peephole optimizations to this peephole rule")             \
                                                                            \
  notproduct(bool, PrintIdeal, false,                                       \
          "Print ideal graph before code generation")                       \
                                                                            \
  notproduct(bool, PrintOpto, false,                                        \
          "Print compiler2 attempts")                                       \
                                                                            \
  notproduct(bool, PrintOptoInlining, false,                                \
          "Print compiler2 inlining decisions")                             \
                                                                            \
  notproduct(bool, VerifyOpto, false,                                       \
          "Apply more time consuming verification during compilation")      \
                                                                            \
  notproduct(bool, VerifyOptoOopOffsets, false,                             \
          "Check types of base addresses in field references")              \
                                                                            \
  develop(bool, IdealizedNumerics, false,                                   \
          "Check performance difference allowing FP "                       \
          "associativity and commutativity...")                             \
                                                                            \
  develop(bool, OptoBreakpoint, false,                                      \
          "insert breakpoint at method entry")                              \
                                                                            \
  notproduct(bool, OptoBreakpointOSR, false,                                \
          "insert breakpoint at osr method entry")                          \
                                                                            \
  notproduct(intx, BreakAtNode, 0,                                          \
          "Break at construction of this Node (either _idx or _debug_idx)") \
                                                                            \
  notproduct(bool, OptoBreakpointC2R, false,                                \
          "insert breakpoint at runtime stub entry")                        \
                                                                            \
  notproduct(bool, OptoNoExecute, false,                                    \
          "Attempt to parse and compile but do not execute generated code") \
                                                                            \
  notproduct(bool, PrintOptoStatistics, false,                              \
          "Print New compiler statistics")                                  \
                                                                            \
  notproduct(bool, PrintOptoAssembly, false,                                \
          "Print New compiler assembly output")                             \
                                                                            \
  develop_pd(bool, OptoPeephole,                                            \
          "Apply peephole optimizations after register allocation")         \
                                                                            \
  develop(bool, OptoRemoveUseless, true,                                    \
          "Remove useless nodes after parsing")                             \
                                                                            \
  notproduct(bool, PrintFrameConverterAssembly, false,                      \
          "Print New compiler assembly output for frame converters")        \
                                                                            \
  notproduct(bool, PrintParseStatistics, false,                             \
          "Print nodes, transforms and new values made per bytecode parsed")\
                                                                            \
  notproduct(bool, PrintOptoPeephole, false,                                \
          "Print New compiler peephole replacements")                       \
                                                                            \
  develop(bool, PrintCFGBlockFreq, false,                                   \
          "Print CFG block freqencies")                                     \
                                                                            \
  develop(bool, TraceOptoParse, false,                                      \
          "Trace bytecode parse and control-flow merge")                    \
                                                                            \
  product_pd(intx,  LoopUnrollLimit,                                        \
          "Unroll loop bodies with node count less than this")              \
                                                                            \
  product(intx,  LoopUnrollMin, 4,                                          \
          "Minimum number of unroll loop bodies before checking progress"   \
          "of rounds of unroll,optimize,..")                                \
                                                                            \
  develop(intx, UnrollLimitForProfileCheck, 1,                              \
          "Don't use profile_trip_cnt() to restrict unrolling until "       \
          "unrolling would push the number of unrolled iterations above "   \
          "UnrollLimitForProfileCheck. A higher value allows more "         \
          "unrolling. Zero acts as a very large value." )                   \
                                                                            \
  product(intx, MultiArrayExpandLimit, 6,                                   \
          "Maximum number of individual allocations in an inline-expanded " \
          "multianewarray instruction")                                     \
                                                                            \
  notproduct(bool, TraceProfileTripCount, false,                            \
          "Trace profile loop trip count information")                      \
                                                                            \
  develop(bool, OptoCoalesce, true,                                         \
          "Use Conservative Copy Coalescing in the Register Allocator")     \
                                                                            \
  develop(bool, UseUniqueSubclasses, true,                                  \
          "Narrow an abstract reference to the unique concrete subclass")   \
                                                                            \
  develop(bool, UseExactTypes, true,                                        \
          "Use exact types to eliminate array store checks and v-calls")    \
                                                                            \
  product(intx, TrackedInitializationLimit, 50,                             \
          "When initializing fields, track up to this many words")          \
                                                                            \
  product(bool, ReduceFieldZeroing, true,                                   \
          "When initializing fields, try to avoid needless zeroing")        \
                                                                            \
  product(bool, ReduceInitialCardMarks, true,                               \
          "When initializing fields, try to avoid needless card marks")     \
                                                                            \
  product(bool, ReduceBulkZeroing, true,                                    \
          "When bulk-initializing, try to avoid needless zeroing")          \
                                                                            \
  develop_pd(intx, RegisterCostAreaRatio,                                   \
          "Spill selection in reg allocator: scale area by (X/64K) before " \
          "adding cost")                                                    \
                                                                            \
  develop_pd(bool, UseCISCSpill,                                            \
          "Use ADLC supplied cisc instructions during allocation")          \
                                                                            \
  notproduct(bool, VerifyGraphEdges , false,                                \
          "Verify Bi-directional Edges")                                    \
                                                                            \
  notproduct(bool, VerifyDUIterators, true,                                 \
          "Verify the safety of all iterations of Bi-directional Edges")    \
                                                                            \
  notproduct(bool, VerifyHashTableKeys, true,                               \
          "Verify the immutability of keys in the VN hash tables")          \
                                                                            \
  notproduct(bool, VerifyRegisterAllocator , false,                         \
          "Verify Register Allocator")                                      \
                                                                            \
  develop_pd(intx, FLOATPRESSURE,                                           \
          "Number of float LRG's that constitute high register pressure")   \
                                                                            \
  develop_pd(intx, INTPRESSURE,                                             \
          "Number of integer LRG's that constitute high register pressure") \
                                                                            \
  notproduct(bool, TraceOptoPipelining, false,                              \
          "Trace pipelining information")                                   \
                                                                            \
  notproduct(bool, TraceOptoOutput, false,                                  \
          "Trace pipelining information")                                   \
                                                                            \
  product_pd(bool, OptoScheduling,                                          \
          "Instruction Scheduling after register allocation")               \
                                                                            \
  product(bool, PartialPeelLoop, true,                                      \
          "Partial peel (rotate) loops")                                    \
                                                                            \
  product(intx, PartialPeelNewPhiDelta, 0,                                  \
          "Additional phis that can be created by partial peeling")         \
                                                                            \
  notproduct(bool, TracePartialPeeling, false,                              \
          "Trace partial peeling (loop rotation) information")              \
                                                                            \
  product(bool, PartialPeelAtUnsignedTests, true,                           \
          "Partial peel at unsigned tests if no signed test exists")        \
                                                                            \
  product(bool, ReassociateInvariants, true,                                \
          "Enable reassociation of expressions with loop invariants.")      \
                                                                            \
  product(bool, LoopUnswitching, true,                                      \
          "Enable loop unswitching (a form of invariant test hoisting)")    \
                                                                            \
  notproduct(bool, TraceLoopUnswitching, false,                             \
          "Trace loop unswitching")                                         \
                                                                            \
  product(bool, UseSuperWord, true,                                         \
          "Transform scalar operations into superword operations")          \
                                                                            \
  develop(bool, SuperWordRTDepCheck, false,                                 \
          "Enable runtime dependency checks.")                              \
                                                                            \
  product(bool, TraceSuperWord, false,                                      \
          "Trace superword transforms")                                     \
                                                                            \
  product_pd(bool, OptoBundling,                                            \
          "Generate nops to fill i-cache lines")                            \
                                                                            \
  product_pd(intx, ConditionalMoveLimit,                                    \
          "Limit of ops to make speculative when using CMOVE")              \
                                                                            \
  /* Set BranchOnRegister == false. See 4965987. */                         \
  product(bool, BranchOnRegister, false,                                    \
          "Use Sparc V9 branch-on-register opcodes")                        \
                                                                            \
  develop(bool, SparcV9RegsHiBitsZero, true,                                \
          "Assume Sparc V9 I&L registers on V8+ systems are zero-extended") \
                                                                            \
  develop(intx, PrintIdealGraphLevel, 0,                                    \
          "Print ideal graph to XML file / network interface. "             \
          "By default attempts to connect to the visualizer on a socket.")  \
                                                                            \
  develop(intx, PrintIdealGraphPort, 4444,                                  \
          "Ideal graph printer to network port")                            \
                                                                            \
  notproduct(ccstr, PrintIdealGraphAddress, "127.0.0.1",                    \
          "IP address to connect to visualizer")                            \
                                                                            \
  notproduct(ccstr, PrintIdealGraphFile, NULL,                              \
          "File to dump ideal graph to.  If set overrides the "             \
          "use of the network")                                             \
                                                                            \
  product(bool, UseOldInlining, true,                                       \
          "Enable the 1.3 inlining strategy")                               \
                                                                            \
  product(bool, UseBimorphicInlining, true,                                 \
          "Profiling based inlining for two receivers")                     \
                                                                            \
  product(bool, UseOnlyInlinedBimorphic, true,                              \
          "Don't use BimorphicInlining if can't inline a second method")    \
                                                                            \
  product(bool, InsertMemBarAfterArraycopy, true,                           \
          "Insert memory barrier after arraycopy call")                     \
                                                                            \
  /* controls for tier 1 compilations */                                    \
                                                                            \
  develop(bool, Tier1CountInvocations, true,                                \
          "Generate code, during tier 1, to update invocation counter")     \
                                                                            \
  product(intx, Tier1Inline, false,                                         \
          "enable inlining during tier 1")                                  \
                                                                            \
  product(intx, Tier1MaxInlineSize, 8,                                      \
          "maximum bytecode size of a method to be inlined, during tier 1") \
                                                                            \
  product(intx, Tier1FreqInlineSize, 35,                                    \
          "max bytecode size of a frequent method to be inlined, tier 1")   \
                                                                            \
  develop(intx, ImplicitNullCheckThreshold, 3,                              \
          "Don't do implicit null checks if NPE's in a method exceeds limit") \
                                                                            \
 /* controls for loop optimization */                                       \
  product(intx, Tier1LoopOptsCount, 0,                                      \
          "Set level of loop optimization for tier 1 compiles")             \
                                                                            \
  product(intx, LoopOptsCount, 43,                                          \
          "Set level of loop optimization for tier 1 compiles")             \
                                                                            \
  /* controls for heat-based inlining */                                    \
                                                                            \
  develop(intx, NodeCountInliningCutoff, 18000,                             \
          "If parser node generation exceeds limit stop inlining")          \
                                                                            \
  develop(intx, NodeCountInliningStep, 1000,                                \
          "Target size of warm calls inlined between optimization passes")  \
                                                                            \
  develop(bool, InlineWarmCalls, false,                                     \
          "Use a heat-based priority queue to govern inlining")             \
                                                                            \
  develop(intx, HotCallCountThreshold, 999999,                              \
          "large numbers of calls (per method invocation) force hotness")   \
                                                                            \
  develop(intx, HotCallProfitThreshold, 999999,                             \
          "highly profitable inlining opportunities force hotness")         \
                                                                            \
  develop(intx, HotCallTrivialWork, -1,                                     \
          "trivial execution time (no larger than this) forces hotness")    \
                                                                            \
  develop(intx, HotCallTrivialSize, -1,                                     \
          "trivial methods (no larger than this) force calls to be hot")    \
                                                                            \
  develop(intx, WarmCallMinCount, -1,                                       \
          "number of calls (per method invocation) to enable inlining")     \
                                                                            \
  develop(intx, WarmCallMinProfit, -1,                                      \
          "number of calls (per method invocation) to enable inlining")     \
                                                                            \
  develop(intx, WarmCallMaxWork, 999999,                                    \
          "execution time of the largest inlinable method")                 \
                                                                            \
  develop(intx, WarmCallMaxSize, 999999,                                    \
          "size of the largest inlinable method")                           \
                                                                            \
  product(intx, MaxNodeLimit, 65000,                                        \
          "Maximum number of nodes")                                        \
                                                                            \
  product(intx, NodeLimitFudgeFactor, 1000,                                 \
          "Fudge Factor for certain optimizations")                         \
                                                                            \
  product(bool, UseJumpTables, true,                                        \
          "Use JumpTables instead of a binary search tree for switches")    \
                                                                            \
  product(bool, UseDivMod, true,                                            \
          "Use combined DivMod instruction if available")                   \
                                                                            \
  product(intx, MinJumpTableSize, 18,                                       \
          "Minimum number of targets in a generated jump table")            \
                                                                            \
  product(intx, MaxJumpTableSize, 65000,                                    \
          "Maximum number of targets in a generated jump table")            \
                                                                            \
  product(intx, MaxJumpTableSparseness, 5,                                  \
          "Maximum sparseness for jumptables")                              \
                                                                            \
  product(bool, EliminateLocks, true,                                       \
          "Coarsen locks when possible")                                    \
                                                                            \
  notproduct(bool, PrintLockStatistics, false,                              \
          "Print precise statistics on the dynamic lock usage")             \
                                                                            \
  diagnostic(bool, PrintPreciseBiasedLockingStatistics, false,              \
          "Print per-lock-site statistics of biased locking in JVM")        \
                                                                            \
  notproduct(bool, PrintEliminateLocks, false,                              \
          "Print out when locks are eliminated")                            \
                                                                            \
  diagnostic(bool, EliminateAutoBox, false,                                 \
          "Private flag to control optimizations for autobox elimination")  \
                                                                            \
  product(intx, AutoBoxCacheMax, 128,                                       \
          "Sets max value cached by the java.lang.Integer autobox cache")   \
                                                                            \
  product(bool, DoEscapeAnalysis, true,                                     \
          "Perform escape analysis")                                        \
                                                                            \
  notproduct(bool, PrintEscapeAnalysis, false,                              \
          "Print the results of escape analysis")                           \
                                                                            \
  product(bool, EliminateAllocations, true,                                 \
          "Use escape analysis to eliminate allocations")                   \
                                                                            \
  notproduct(bool, PrintEliminateAllocations, false,                        \
          "Print out when allocations are eliminated")                      \
                                                                            \
  product(intx, EliminateAllocationArraySizeLimit, 64,                      \
          "Array size (number of elements) limit for scalar replacement")   \
                                                                            \
  product(bool, UseOptoBiasInlining, true,                                  \
          "Generate biased locking code in C2 ideal graph")                 \
                                                                            \
  product(bool, OptimizeStringConcat, false,                                \
          "Optimize the construction of Strings by StringBuilder")          \
                                                                            \
  notproduct(bool, PrintOptimizeStringConcat, false,                        \
          "Print information about transformations performed on Strings")   \
                                                                            \
  product(intx, ValueSearchLimit, 1000,                                     \
          "Recursion limit in PhaseMacroExpand::value_from_mem_phi")        \
                                                                            \
  product(intx, MaxLabelRootDepth, 1100,                                    \
          "Maximum times call Label_Root to prevent stack overflow")        \
                                                                            \
  diagnostic(intx, DominatorSearchLimit, 1000,                              \
          "Iterations limit in Node::dominates")                            \
                                                                            \
  product(bool, BlockLayoutByFrequency, true,                               \
          "Use edge frequencies to drive block ordering")                   \
                                                                            \
  product(intx, BlockLayoutMinDiamondPercentage, 20,                        \
          "Miniumum %% of a successor (predecessor) for which block layout "\
          "a will allow a fork (join) in a single chain")                   \
                                                                            \
  product(bool, BlockLayoutRotateLoops, true,                               \
          "Allow back branches to be fall throughs in the block layour")    \

C2_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG)
