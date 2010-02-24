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
  product(intx, G1ParallelGCAllocBufferSize, 8*K,                           \
          "Size of parallel G1 allocation buffers in to-space.")            \
                                                                            \
  product(intx, G1ConfidencePercent, 50,                                    \
          "Confidence level for MMU/pause predictions")                     \
                                                                            \
  develop(intx, G1MarkingOverheadPercent, 0,                                \
          "Overhead of concurrent marking")                                 \
                                                                            \
  product(uintx, G1YoungGenSize, 0,                                         \
          "Size of the G1 young generation, 0 is the adaptive policy")      \
                                                                            \
  develop(bool, G1Gen, true,                                                \
          "If true, it will enable the generational G1")                    \
                                                                            \
  develop(intx, G1GCPercent, 10,                                            \
          "The desired percent time spent on GC")                           \
                                                                            \
  develop(intx, G1PolicyVerbose, 0,                                         \
          "The verbosity level on G1 policy decisions")                     \
                                                                            \
  develop(bool, G1UseHRIntoRS, true,                                        \
          "Determines whether the 'advanced' HR Into rem set is used.")     \
                                                                            \
  develop(intx, G1MarkingVerboseLevel, 0,                                   \
          "Level (0-4) of verboseness of the marking code")                 \
                                                                            \
  develop(bool, G1PrintReachableAtInitialMark, false,                       \
          "Reachable object dump at the initial mark pause")                \
                                                                            \
  develop(bool, G1VerifyDuringGCPrintReachable, false,                      \
          "If conc mark verification fails, dump reachable objects")        \
                                                                            \
  develop(ccstr, G1PrintReachableBaseFile, NULL,                            \
          "The base file name for the reachable object dumps")              \
                                                                            \
  develop(bool, G1TraceMarkStackOverflow, false,                            \
          "If true, extra debugging code for CM restart for ovflw.")        \
                                                                            \
  develop(intx, G1PausesBtwnConcMark, -1,                                   \
          "If positive, fixed number of pauses between conc markings")      \
                                                                            \
  diagnostic(bool, G1SummarizeConcMark, false,                              \
          "Summarize concurrent mark info")                                 \
                                                                            \
  diagnostic(bool, G1SummarizeRSetStats, false,                             \
          "Summarize remembered set processing info")                       \
                                                                            \
  diagnostic(intx, G1SummarizeRSetStatsPeriod, 0,                           \
          "The period (in number of GCs) at which we will generate "        \
          "update buffer processing info "                                  \
          "(0 means do not periodically generate this info); "              \
          "it also requires -XX:+G1SummarizeRSetStats")                     \
                                                                            \
  diagnostic(bool, G1SummarizeZFStats, false,                               \
          "Summarize zero-filling info")                                    \
                                                                            \
  diagnostic(bool, G1TraceConcRefinement, false,                            \
          "Trace G1 concurrent refinement")                                 \
                                                                            \
  product(intx, G1MarkStackSize, 2 * 1024 * 1024,                           \
          "Size of the mark stack for concurrent marking.")                 \
                                                                            \
  product(intx, G1MarkRegionStackSize, 1024 * 1024,                         \
          "Size of the region stack for concurrent marking.")               \
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
  product(intx, G1SATBBufferSize, 1*K,                                      \
          "Number of entries in an SATB log buffer.")                       \
                                                                            \
  develop(intx, G1SATBProcessCompletedThreshold, 20,                        \
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
  experimental(intx, G1ExpandByPercentOfAvailable, 20,                      \
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
  develop(bool, G1DeferredRSUpdate, true,                                   \
          "If true, use deferred RS updates")                               \
                                                                            \
  develop(bool, G1RSLogCheckCardTable, false,                               \
          "If true, verify that no dirty cards remain after RS log "        \
          "processing.")                                                    \
                                                                            \
  develop(bool, G1RSCountHisto, false,                                      \
          "If true, print a histogram of RS occupancies after each pause")  \
                                                                            \
  develop(intx, G1PrintRegionLivenessInfo, 0,                               \
          "When > 0, print the occupancies of the <n> best and worst"       \
          "regions.")                                                       \
                                                                            \
  develop(bool, G1PrintParCleanupStats, false,                              \
          "When true, print extra stats about parallel cleanup.")           \
                                                                            \
  develop(bool, G1DisablePreBarrier, false,                                 \
          "Disable generation of pre-barrier (i.e., marking barrier)   ")   \
                                                                            \
  develop(bool, G1DisablePostBarrier, false,                                \
          "Disable generation of post-barrier (i.e., RS barrier)   ")       \
                                                                            \
  product(intx, G1UpdateBufferSize, 256,                                    \
          "Size of an update buffer")                                       \
                                                                            \
  product(intx, G1ConcRefinementYellowZone, 0,                              \
          "Number of enqueued update buffers that will "                    \
          "trigger concurrent processing. Will be selected ergonomically "  \
          "by default.")                                                    \
                                                                            \
  product(intx, G1ConcRefinementRedZone, 0,                                 \
          "Maximum number of enqueued update buffers before mutator "       \
          "threads start processing new ones instead of enqueueing them. "  \
          "Will be selected ergonomically by default. Zero will disable "   \
          "concurrent processing.")                                         \
                                                                            \
  product(intx, G1ConcRefinementGreenZone, 0,                               \
          "The number of update buffers that are left in the queue by the " \
          "concurrent processing threads. Will be selected ergonomically "  \
          "by default.")                                                    \
                                                                            \
  product(intx, G1ConcRefinementServiceIntervalMillis, 300,                 \
          "The last concurrent refinement thread wakes up every "           \
          "specified number of milliseconds to do miscellaneous work.")     \
                                                                            \
  product(intx, G1ConcRefinementThresholdStep, 0,                           \
          "Each time the rset update queue increases by this amount "       \
          "activate the next refinement thread if available. "              \
          "Will be selected ergonomically by default.")                     \
                                                                            \
  product(intx, G1RSetUpdatingPauseTimePercent, 10,                         \
          "A target percentage of time that is allowed to be spend on "     \
          "process RS update buffers during the collection pause.")         \
                                                                            \
  product(bool, G1UseAdaptiveConcRefinement, true,                          \
          "Select green, yellow and red zones adaptively to meet the "      \
          "the pause requirements.")                                        \
                                                                            \
  develop(intx, G1ConcRSLogCacheSize, 10,                                   \
          "Log base 2 of the length of conc RS hot-card cache.")            \
                                                                            \
  develop(intx, G1ConcRSHotCardLimit, 4,                                    \
          "The threshold that defines (>=) a hot card.")                    \
                                                                            \
  develop(bool, G1PrintOopAppls, false,                                     \
          "When true, print applications of closures to external locs.")    \
                                                                            \
  develop(intx, G1RSetRegionEntriesBase, 256,                               \
          "Max number of regions in a fine-grain table per MB.")            \
                                                                            \
  product(intx, G1RSetRegionEntries, 0,                                     \
          "Max number of regions for which we keep bitmaps."                \
          "Will be set ergonomically by default")                           \
                                                                            \
  develop(intx, G1RSetSparseRegionEntriesBase, 4,                           \
          "Max number of entries per region in a sparse table "             \
          "per MB.")                                                        \
                                                                            \
  product(intx, G1RSetSparseRegionEntries, 0,                               \
          "Max number of entries per region in a sparse table."             \
          "Will be set ergonomically by default.")                          \
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
  develop(bool, G1UseScanOnlyPrefix, false,                                 \
          "It determines whether the system will calculate an optimum "     \
          "scan-only set.")                                                 \
                                                                            \
  product(intx, G1ReservePercent, 10,                                       \
          "It determines the minimum reserve we should have in the heap "   \
          "to minimize the probability of promotion failure.")              \
                                                                            \
  diagnostic(bool, G1PrintHeapRegions, false,                               \
          "If set G1 will print information on which regions are being "    \
          "allocated and which are reclaimed.")                             \
                                                                            \
  develop(bool, G1HRRSUseSparseTable, true,                                 \
          "When true, use sparse table to save space.")                     \
                                                                            \
  develop(bool, G1HRRSFlushLogBuffersOnVerify, false,                       \
          "Forces flushing of log buffers before verification.")            \
                                                                            \
  develop(bool, G1FailOnFPError, false,                                     \
          "When set, G1 will fail when it encounters an FP 'error', "       \
          "so as to allow debugging")                                       \
                                                                            \
  develop(bool, G1FixedTenuringThreshold, false,                            \
          "When set, G1 will not adjust the tenuring threshold")            \
                                                                            \
  develop(bool, G1FixedEdenSize, false,                                     \
          "When set, G1 will not allocate unused survivor space regions")   \
                                                                            \
  develop(uintx, G1FixedSurvivorSpaceSize, 0,                               \
          "If non-0 is the size of the G1 survivor space, "                 \
          "otherwise SurvivorRatio is used to determine the size")          \
                                                                            \
  product(bool, G1UseFixedWindowMMUTracker, false,                          \
          "If the MMU tracker's memory is full, forget the oldest entry")   \
                                                                            \
  product(uintx, G1HeapRegionSize, 0,                                       \
          "Size of the G1 regions.")                                        \
                                                                            \
  experimental(bool, G1UseParallelRSetUpdating, false,                      \
          "Enables the parallelization of remembered set updating "         \
          "during evacuation pauses")                                       \
                                                                            \
  experimental(bool, G1UseParallelRSetScanning, false,                      \
          "Enables the parallelization of remembered set scanning "         \
          "during evacuation pauses")                                       \
                                                                            \
  product(uintx, G1ConcRefinementThreads, 0,                                \
          "If non-0 is the number of parallel rem set update threads, "     \
          "otherwise the value is determined ergonomically.")               \
                                                                            \
  develop(intx, G1CardCountCacheExpandThreshold, 16,                        \
          "Expand the card count cache if the number of collisions for "    \
          "a particular entry exceeds this value.")                         \
                                                                            \
  develop(bool, G1VerifyCTCleanup, false,                                   \
          "Verify card table cleanup.")                                     \
                                                                            \
  product(uintx, G1RSetScanBlockSize, 64,                                   \
          "Size of a work unit of cards claimed by a worker thread"         \
          "during RSet scanning.")                                          \
                                                                            \
  develop(bool, ReduceInitialCardMarksForG1, false,                         \
          "When ReduceInitialCardMarks is true, this flag setting "         \
          " controls whether G1 allows the RICM optimization")

G1_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG, DECLARE_MANAGEABLE_FLAG, DECLARE_PRODUCT_RW_FLAG)
