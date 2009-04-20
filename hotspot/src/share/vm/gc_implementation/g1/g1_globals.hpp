/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
// Defines all globals flags used by the garbage-first compiler.
//

#define G1_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct, manageable, product_rw) \
                                                                            \
  product(intx, ParallelGCG1AllocBufferSize, 8*K,                           \
          "Size of parallel G1 allocation buffers in to-space.")            \
                                                                            \
  product(intx, G1TimeSliceMS, 500,                                         \
          "Time slice for MMU specification")                               \
                                                                            \
  product(intx, G1MaxPauseTimeMS, 200,                                      \
          "Max GC time per MMU time slice")                                 \
                                                                            \
  product(intx, G1ConfidencePerc, 50,                                       \
          "Confidence level for MMU/pause predictions")                     \
                                                                            \
  product(intx, G1MarkingOverheadPerc, 0,                                   \
          "Overhead of concurrent marking")                                 \
                                                                            \
  product(bool, G1AccountConcurrentOverhead, false,                         \
          "Whether soft real-time compliance in G1 will take into account"  \
          "concurrent overhead")                                            \
                                                                            \
  product(intx, G1YoungGenSize, 0,                                          \
          "Size of the G1 young generation, 0 is the adaptive policy")      \
                                                                            \
  product(bool, G1Gen, true,                                                \
          "If true, it will enable the generational G1")                    \
                                                                            \
  develop(intx, G1GCPct, 10,                                                \
          "The desired percent time spent on GC")                           \
                                                                            \
  product(intx, G1PolicyVerbose, 0,                                         \
          "The verbosity level on G1 policy decisions")                     \
                                                                            \
  develop(bool, G1UseHRIntoRS, true,                                        \
          "Determines whether the 'advanced' HR Into rem set is used.")     \
                                                                            \
  product(bool, G1VerifyRemSet, false,                                      \
          "If true, verify the rem set functioning at each GC")             \
                                                                            \
  product(bool, G1VerifyConcMark, false,                                    \
          "If true, verify the conc marking code at full GC time")          \
                                                                            \
  develop(intx, G1MarkingVerboseLevel, 0,                                   \
          "Level (0-4) of verboseness of the marking code")                 \
                                                                            \
  develop(bool, G1VerifyConcMarkPrintReachable, true,                       \
          "If conc mark verification fails, print reachable objects")       \
                                                                            \
  develop(bool, G1TraceMarkStackOverflow, false,                            \
          "If true, extra debugging code for CM restart for ovflw.")        \
                                                                            \
  product(bool, G1VerifyMarkingInEvac, false,                               \
          "If true, verify marking info during evacuation")                 \
                                                                            \
  develop(intx, G1PausesBtwnConcMark, -1,                                   \
          "If positive, fixed number of pauses between conc markings")      \
                                                                            \
  product(intx, G1EfficiencyPctCausesMark, 80,                              \
          "The cum gc efficiency since mark fall-off that causes "          \
          "new marking")                                                    \
                                                                            \
  product(bool, TraceConcurrentMark, false,                                 \
          "Trace concurrent mark")                                          \
                                                                            \
  product(bool, SummarizeG1ConcMark, false,                                 \
          "Summarize concurrent mark info")                                 \
                                                                            \
  product(bool, SummarizeG1RSStats, false,                                  \
          "Summarize remembered set processing info")                       \
                                                                            \
  product(bool, SummarizeG1ZFStats, false,                                  \
          "Summarize zero-filling info")                                    \
                                                                            \
  product(bool, TraceG1Refine, false,                                       \
          "Trace G1 concurrent refinement")                                 \
                                                                            \
  develop(bool, G1ConcMark, true,                                           \
          "If true, run concurrent marking for G1")                         \
                                                                            \
  product(intx, G1CMStackSize, 2 * 1024 * 1024,                             \
          "Size of the mark stack for concurrent marking.")                 \
                                                                            \
  product(intx, G1CMRegionStackSize, 1024 * 1024,                           \
          "Size of the region stack for concurrent marking.")               \
                                                                            \
  develop(bool, G1ConcRefine, true,                                         \
          "If true, run concurrent rem set refinement for G1")              \
                                                                            \
  develop(intx, G1ConcRefineTargTraversals, 4,                              \
          "Number of concurrent refinement we try to achieve")              \
                                                                            \
  develop(intx, G1ConcRefineInitialDelta, 4,                                \
          "Number of heap regions of alloc ahead of starting collection "   \
          "pause to start concurrent refinement (initially)")               \
                                                                            \
  product(bool, G1SmoothConcRefine, true,                                   \
          "Attempts to smooth out the overhead of concurrent refinement")   \
                                                                            \
  develop(bool, G1ConcZeroFill, true,                                       \
          "If true, run concurrent zero-filling thread")                    \
                                                                            \
  develop(intx, G1ConcZFMaxRegions, 1,                                      \
          "Stop zero-filling when # of zf'd regions reaches")               \
                                                                            \
  product(intx, G1SteadyStateUsed, 90,                                      \
          "If non-0, try to maintain 'used' at this pct (of max)")          \
                                                                            \
  product(intx, G1SteadyStateUsedDelta, 30,                                 \
          "If G1SteadyStateUsed is non-0, then do pause this number of "    \
          "of percentage points earlier if no marking is in progress.")     \
                                                                            \
  develop(bool, G1SATBBarrierPrintNullPreVals, false,                       \
          "If true, count frac of ptr writes with null pre-vals.")          \
                                                                            \
  product(intx, G1SATBLogBufferSize, 1*K,                                   \
          "Number of entries in an SATB log buffer.")                       \
                                                                            \
  product(intx, G1SATBProcessCompletedThreshold, 20,                        \
          "Number of completed buffers that triggers log processing.")      \
                                                                            \
  develop(intx, G1ExtraRegionSurvRate, 33,                                  \
          "If the young survival rate is S, and there's room left in "      \
          "to-space, we will allow regions whose survival rate is up to "   \
          "S + (1 - S)*X, where X is this parameter (as a fraction.)")      \
                                                                            \
  develop(intx, G1InitYoungSurvRatio, 50,                                   \
          "Expected Survival Rate for newly allocated bytes")               \
                                                                            \
  develop(bool, G1SATBPrintStubs, false,                                    \
          "If true, print generated stubs for the SATB barrier")            \
                                                                            \
  product(intx, G1ExpandByPctOfAvail, 20,                                   \
          "When expanding, % of uncommitted space to claim.")               \
                                                                            \
  develop(bool, G1RSBarrierRegionFilter, true,                              \
          "If true, generate region filtering code in RS barrier")          \
                                                                            \
  develop(bool, G1RSBarrierNullFilter, true,                                \
          "If true, generate null-pointer filtering code in RS barrier")    \
                                                                            \
  develop(bool, G1PrintCTFilterStats, false,                                \
          "If true, print stats on RS filtering effectiveness")             \
                                                                            \
  develop(bool, G1RSBarrierUseQueue, true,                                  \
          "If true, use queueing RS barrier")                               \
                                                                            \
  develop(bool, G1DeferredRSUpdate, true,                                   \
          "If true, use deferred RS updates")                               \
                                                                            \
  develop(bool, G1RSLogCheckCardTable, false,                               \
          "If true, verify that no dirty cards remain after RS log "        \
          "processing.")                                                    \
                                                                            \
  product(intx, G1MinPausesBetweenMarks, 2,                                 \
          "Number of inefficient pauses necessary to trigger marking.")     \
                                                                            \
  product(intx, G1InefficientPausePct, 80,                                  \
          "Threshold of an 'inefficient' pauses (as % of cum efficiency.")  \
                                                                            \
  develop(bool, G1RSCountHisto, false,                                      \
          "If true, print a histogram of RS occupancies after each pause")  \
                                                                            \
  product(bool, G1TraceFileOverwrite, false,                                \
          "Allow the trace file to be overwritten")                         \
                                                                            \
  develop(intx, G1PrintRegionLivenessInfo, 0,                               \
          "When > 0, print the occupancies of the <n> best and worst"       \
          "regions.")                                                       \
                                                                            \
  develop(bool, G1PrintParCleanupStats, false,                              \
          "When true, print extra stats about parallel cleanup.")           \
                                                                            \
  product(bool, G1DoAgeCohortChecks, false,                                 \
          "When true, check well-formedness of age cohort structures.")     \
                                                                            \
  develop(bool, G1DisablePreBarrier, false,                                 \
          "Disable generation of pre-barrier (i.e., marking barrier)   ")   \
                                                                            \
  develop(bool, G1DisablePostBarrier, false,                                \
          "Disable generation of post-barrier (i.e., RS barrier)   ")       \
                                                                            \
  product(intx, G1DirtyCardQueueMax, 30,                                    \
          "Maximum number of completed RS buffers before mutator threads "  \
          "start processing them.")                                         \
                                                                            \
  develop(intx, G1ConcRSLogCacheSize, 10,                                   \
          "Log base 2 of the length of conc RS hot-card cache.")            \
                                                                            \
  product(bool, G1ConcRSCountTraversals, false,                             \
          "If true, gather data about the number of times CR traverses "    \
          "cards ")                                                         \
                                                                            \
  product(intx, G1ConcRSHotCardLimit, 4,                                    \
          "The threshold that defines (>=) a hot card.")                    \
                                                                            \
  develop(bool, G1PrintOopAppls, false,                                     \
          "When true, print applications of closures to external locs.")    \
                                                                            \
  product(intx, G1LogRSRegionEntries, 7,                                    \
          "Log_2 of max number of regions for which we keep bitmaps.")      \
                                                                            \
  develop(bool, G1RecordHRRSOops, false,                                    \
          "When true, record recent calls to rem set operations.")          \
                                                                            \
  develop(bool, G1RecordHRRSEvents, false,                                  \
          "When true, record recent calls to rem set operations.")          \
                                                                            \
  develop(intx, G1MaxVerifyFailures, -1,                                    \
          "The maximum number of verification failrues to print.  "         \
          "-1 means print all.")                                            \
                                                                            \
  develop(bool, G1ScrubRemSets, true,                                       \
          "When true, do RS scrubbing after cleanup.")                      \
                                                                            \
  develop(bool, G1RSScrubVerbose, false,                                    \
          "When true, do RS scrubbing with verbose output.")                \
                                                                            \
  develop(bool, G1YoungSurvRateVerbose, false,                              \
          "print out the survival rate of young regions according to age.") \
                                                                            \
  develop(intx, G1YoungSurvRateNumRegionsSummary, 0,                        \
          "the number of regions for which we'll print a surv rate "        \
          "summary.")                                                       \
                                                                            \
  product(bool, G1UseScanOnlyPrefix, false,                                 \
          "It determines whether the system will calculate an optimum "     \
          "scan-only set.")                                                 \
                                                                            \
  product(intx, G1MinReservePerc, 10,                                       \
          "It determines the minimum reserve we should have in the heap "   \
          "to minimize the probability of promotion failure.")              \
                                                                            \
  product(bool, G1TraceRegions, false,                                      \
          "If set G1 will print information on which regions are being "    \
          "allocated and which are reclaimed.")                             \
                                                                            \
  develop(bool, G1HRRSUseSparseTable, true,                                 \
          "When true, use sparse table to save space.")                     \
                                                                            \
  develop(bool, G1HRRSFlushLogBuffersOnVerify, false,                       \
          "Forces flushing of log buffers before verification.")            \
                                                                            \
  product(bool, G1UseSurvivorSpace, true,                                   \
          "When true, use survivor space.")                                 \
                                                                            \
  product(bool, G1FixedTenuringThreshold, false,                            \
          "When set, G1 will not adjust the tenuring threshold")            \
                                                                            \
  product(bool, G1FixedEdenSize, false,                                     \
          "When set, G1 will not allocate unused survivor space regions")   \
                                                                            \
  product(uintx, G1FixedSurvivorSpaceSize, 0,                               \
          "If non-0 is the size of the G1 survivor space, "                 \
          "otherwise SurvivorRatio is used to determine the size")          \
                                                                            \
  experimental(bool, G1EnableParallelRSetUpdating, false,                   \
          "Enables the parallelization of remembered set updating "         \
          "during evacuation pauses")                                       \
                                                                            \
  experimental(bool, G1EnableParallelRSetScanning, false,                   \
          "Enables the parallelization of remembered set scanning "         \
          "during evacuation pauses")

G1_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG, DECLARE_MANAGEABLE_FLAG, DECLARE_PRODUCT_RW_FLAG)
