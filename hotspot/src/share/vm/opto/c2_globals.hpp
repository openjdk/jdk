/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_VM_OPTO_C2_GLOBALS_HPP
#define SHARE_VM_OPTO_C2_GLOBALS_HPP

#include "runtime/globals.hpp"
#ifdef TARGET_ARCH_x86
# include "c2_globals_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "c2_globals_sparc.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "c2_globals_arm.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "c2_globals_linux.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "c2_globals_solaris.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "c2_globals_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "c2_globals_bsd.hpp"
#endif

//
// Defines all globals flags used by the server compiler.
//

#define C2_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct) \
                                                                            \
  develop(bool, StressLCM, false,                                           \
          "Randomize instruction scheduling in LCM")                        \
                                                                            \
  develop(bool, StressGCM, false,                                           \
          "Randomize instruction scheduling in GCM")                        \
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
  product(intx, MaxLoopPad, (OptoLoopAlignment-1),                          \
          "Align a loop if padding size in bytes is less or equal to this value") \
                                                                            \
  product(intx, MaxVectorSize, 32,                                          \
          "Max vector size in bytes, "                                      \
          "actual size could be less depending on elements type")           \
                                                                            \
  product(bool, AlignVector, true,                                          \
          "Perform vector store/load alignment in loop")                    \
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
  notproduct(bool, VerifyIdealNodeCount, false,                             \
          "Verify that tracked dead ideal node count is accurate")          \
                                                                            \
  notproduct(bool, PrintIdealNodeCount, false,                              \
          "Print liveness counts of ideal nodes")                           \
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
  product(intx,  LoopMaxUnroll, 16,                                         \
          "Maximum number of unrolls for main loop")                        \
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
  product(bool, UseLoopPredicate, true,                                     \
          "Generate a predicate to select fast/slow loop versions")         \
                                                                            \
  develop(bool, TraceLoopPredicate, false,                                  \
          "Trace generation of loop predicates")                            \
                                                                            \
  develop(bool, TraceLoopOpts, false,                                       \
          "Trace executed loop optimizations")                              \
                                                                            \
  diagnostic(bool, LoopLimitCheck, true,                                    \
          "Generate a loop limits check for overflow")                      \
                                                                            \
  develop(bool, TraceLoopLimitCheck, false,                                 \
          "Trace generation of loop limits checks")                         \
                                                                            \
  diagnostic(bool, RangeLimitCheck, true,                                   \
          "Additional overflow checks during range check elimination")      \
                                                                            \
  develop(bool, TraceRangeLimitCheck, false,                                \
          "Trace additional overflow checks in RCE")                        \
                                                                            \
  diagnostic(bool, UnrollLimitCheck, true,                                  \
          "Additional overflow checks during loop unroll")                  \
                                                                            \
  product(bool, OptimizeFill, true,                                         \
          "convert fill/copy loops into intrinsic")                         \
                                                                            \
  develop(bool, TraceOptimizeFill, false,                                   \
          "print detailed information about fill conversion")               \
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
  product(bool, UseFPUForSpilling, false,                                   \
          "Spill integer registers to FPU instead of stack when possible")  \
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
  notproduct(bool, TraceSuperWord, false,                                   \
          "Trace superword transforms")                                     \
                                                                            \
  notproduct(bool, TraceNewVectors, false,                                  \
          "Trace creation of Vector nodes")                                 \
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
  product(bool, UseRDPCForConstantTableBase, false,                         \
          "Use Sparc RDPC instruction for the constant table base.")        \
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
  develop(bool, SubsumeLoads, true,                                         \
          "Attempt to compile while subsuming loads into machine instructions.") \
                                                                            \
  develop(bool, StressRecompilation, false,                                 \
          "Recompile each compiled method without subsuming loads or escape analysis.") \
                                                                            \
  develop(intx, ImplicitNullCheckThreshold, 3,                              \
          "Don't do implicit null checks if NPE's in a method exceeds limit") \
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
  product(intx, MaxNodeLimit, 80000,                                        \
          "Maximum number of nodes")                                        \
                                                                            \
  product(intx, NodeLimitFudgeFactor, 2000,                                 \
          "Fudge Factor for certain optimizations")                         \
                                                                            \
  product(bool, UseJumpTables, true,                                        \
          "Use JumpTables instead of a binary search tree for switches")    \
                                                                            \
  product(bool, UseDivMod, true,                                            \
          "Use combined DivMod instruction if available")                   \
                                                                            \
  product_pd(intx, MinJumpTableSize,                                        \
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
  product(bool, EliminateNestedLocks, true,                                 \
          "Eliminate nested locks of the same object when possible")        \
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
  product(bool, EliminateAutoBox, true,                                     \
          "Control optimizations for autobox elimination")                  \
                                                                            \
  experimental(bool, UseImplicitStableValues, false,                        \
          "Mark well-known stable fields as such (e.g. String.value)")      \
                                                                            \
  product(intx, AutoBoxCacheMax, 128,                                       \
          "Sets max value cached by the java.lang.Integer autobox cache")   \
                                                                            \
  experimental(bool, AggressiveUnboxing, false,                             \
          "Control optimizations for aggressive boxing elimination")        \
                                                                            \
  product(bool, DoEscapeAnalysis, true,                                     \
          "Perform escape analysis")                                        \
                                                                            \
  develop(bool, ExitEscapeAnalysisOnTimeout, true,                          \
          "Exit or throw assert in EA when it reaches time limit")          \
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
  product(bool, OptimizePtrCompare, true,                                   \
          "Use escape analysis to optimize pointers compare")               \
                                                                            \
  notproduct(bool, PrintOptimizePtrCompare, false,                          \
          "Print information about optimized pointers compare")             \
                                                                            \
  notproduct(bool, VerifyConnectionGraph , true,                            \
          "Verify Connection Graph construction in Escape Analysis")        \
                                                                            \
  product(bool, UseOptoBiasInlining, true,                                  \
          "Generate biased locking code in C2 ideal graph")                 \
                                                                            \
  product(bool, OptimizeStringConcat, true,                                 \
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
                                                                            \
  develop(bool, InlineReflectionGetCallerClass, true,                       \
          "inline sun.reflect.Reflection.getCallerClass(), known to be part "\
          "of base library DLL")                                            \
                                                                            \
  develop(bool, InlineObjectCopy, true,                                     \
          "inline Object.clone and Arrays.copyOf[Range] intrinsics")        \
                                                                            \
  develop(bool, SpecialStringCompareTo, true,                               \
          "special version of string compareTo")                            \
                                                                            \
  develop(bool, SpecialStringIndexOf, true,                                 \
          "special version of string indexOf")                              \
                                                                            \
  develop(bool, SpecialStringEquals, true,                                  \
          "special version of string equals")                               \
                                                                            \
  develop(bool, SpecialArraysEquals, true,                                  \
          "special version of Arrays.equals(char[],char[])")                \
                                                                            \
  product(bool, SpecialEncodeISOArray, true,                                \
          "special version of ISO_8859_1$Encoder.encodeISOArray")           \
                                                                            \
  develop(bool, BailoutToInterpreterForThrows, false,                       \
          "Compiled methods which throws/catches exceptions will be "       \
          "deopt and intp.")                                                \
                                                                            \
  develop(bool, ConvertCmpD2CmpF, true,                                     \
          "Convert cmpD to cmpF when one input is constant in float range") \
                                                                            \
  develop(bool, ConvertFloat2IntClipping, true,                             \
          "Convert float2int clipping idiom to integer clipping")           \
                                                                            \
  develop(bool, Use24BitFPMode, true,                                       \
          "Set 24-bit FPU mode on a per-compile basis ")                    \
                                                                            \
  develop(bool, Use24BitFP, true,                                           \
          "use FP instructions that produce 24-bit precise results")        \
                                                                            \
  develop(bool, MonomorphicArrayCheck, true,                                \
          "Uncommon-trap array store checks that require full type check")  \
                                                                            \
  notproduct(bool, TracePhaseCCP, false,                                    \
          "Print progress during Conditional Constant Propagation")         \
                                                                            \
  develop(bool, PrintDominators, false,                                     \
          "Print out dominator trees for GVN")                              \
                                                                            \
  notproduct(bool, TraceSpilling, false,                                    \
          "Trace spilling")                                                 \
                                                                            \
  diagnostic(bool, TraceTypeProfile, false,                                 \
          "Trace type profile")                                             \
                                                                            \
  develop(bool, PoisonOSREntry, true,                                       \
           "Detect abnormal calls to OSR code")                             \
                                                                            \
  product(bool, UseCondCardMark, false,                                     \
          "Check for already marked card before updating card table")       \
                                                                            \
  develop(bool, SoftMatchFailure, trueInProduct,                            \
          "If the DFA fails to match a node, print a message and bail out") \
                                                                            \
  develop(bool, InlineAccessors, true,                                      \
          "inline accessor methods (get/set)")                              \
                                                                            \
  product(intx, TypeProfileMajorReceiverPercent, 90,                        \
          "% of major receiver type to all profiled receivers")             \
                                                                            \
  notproduct(bool, TimeCompiler2, false,                                    \
          "detailed time the compiler (requires +TimeCompiler)")            \
                                                                            \
  diagnostic(bool, PrintIntrinsics, false,                                  \
          "prints attempted and successful inlining of intrinsics")         \
                                                                            \
  diagnostic(ccstrlist, DisableIntrinsic, "",                               \
          "do not expand intrinsics whose (internal) names appear here")    \
                                                                            \
  develop(bool, StressReflectiveCode, false,                                \
          "Use inexact types at allocations, etc., to test reflection")     \
                                                                            \
  diagnostic(bool, DebugInlinedCalls, true,                                 \
         "If false, restricts profiled locations to the root method only")  \
                                                                            \
  notproduct(bool, VerifyLoopOptimizations, false,                          \
          "verify major loop optimizations")                                \
                                                                            \
  diagnostic(bool, ProfileDynamicTypes, true,                               \
          "do extra type profiling and use it more aggressively")           \
                                                                            \
  develop(bool, TraceIterativeGVN, false,                                   \
          "Print progress during Iterative Global Value Numbering")         \
                                                                            \
  develop(bool, VerifyIterativeGVN, false,                                  \
          "Verify Def-Use modifications during sparse Iterative Global "    \
          "Value Numbering")                                                \
                                                                            \
  notproduct(bool, TraceCISCSpill, false,                                   \
          "Trace allocators use of cisc spillable instructions")            \
                                                                            \
  product(bool, SplitIfBlocks, true,                                        \
          "Clone compares and control flow through merge points to fold "   \
          "some branches")                                                  \
                                                                            \
  develop(intx, FreqCountInvocations,  1,                                   \
          "Scaling factor for branch frequencies (deprecated)")             \
                                                                            \
  product(intx, AliasLevel,     3,                                          \
          "0 for no aliasing, 1 for oop/field/static/array split, "         \
          "2 for class split, 3 for unique instances")                      \
                                                                            \
  develop(bool, VerifyAliases, false,                                       \
          "perform extra checks on the results of alias analysis")          \
                                                                            \
  product(bool, IncrementalInline, true,                                    \
          "do post parse inlining")                                         \
                                                                            \
  develop(bool, AlwaysIncrementalInline, false,                             \
          "do all inlining incrementally")                                  \
                                                                            \
  product(intx, LiveNodeCountInliningCutoff, 20000,                         \
          "max number of live nodes in a method")                           \
                                                                            \
  diagnostic(bool, OptimizeExpensiveOps, true,                              \
          "Find best control for expensive operations")                     \
                                                                            \
  experimental(bool, UseMathExactIntrinsics, false,                         \
          "Enables intrinsification of various java.lang.Math functions")   \
                                                                            \
  experimental(bool, ReplaceInParentMaps, false,                            \
          "Propagate type improvements in callers of inlinee if possible")  \
                                                                            \
  experimental(bool, UseTypeSpeculation, false,                             \
          "Speculatively propagate types from profiles")

C2_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG)

#endif // SHARE_VM_OPTO_C2_GLOBALS_HPP
