/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1_GLOBALS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1_GLOBALS_HPP

#include "runtime/globals.hpp"
//
// Defines all globals flags used by the garbage-first compiler.
//

#define G1_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct, manageable, product_rw) \
                                                                            \
  product(uintx, G1ConfidencePercent, 50,                                   \
          "Confidence level for MMU/pause predictions")                     \
                                                                            \
  develop(intx, G1MarkingOverheadPercent, 0,                                \
          "Overhead of concurrent marking")                                 \
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
  develop(bool, G1TraceHeapRegionRememberedSet, false,                      \
          "Enables heap region remembered set debug logs")                  \
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
  diagnostic(bool, G1TraceConcRefinement, false,                            \
          "Trace G1 concurrent refinement")                                 \
                                                                            \
  experimental(bool, G1TraceStringSymbolTableScrubbing, false,              \
          "Trace information string and symbol table scrubbing.")           \
                                                                            \
  product(double, G1ConcMarkStepDurationMillis, 10.0,                       \
          "Target duration of individual concurrent marking steps "         \
          "in milliseconds.")                                               \
                                                                            \
  product(intx, G1RefProcDrainInterval, 10,                                 \
          "The number of discovered reference objects to process before "   \
          "draining concurrent marking work queues.")                       \
                                                                            \
  experimental(bool, G1UseConcMarkReferenceProcessing, true,                \
          "If true, enable reference discovery during concurrent "          \
          "marking and reference processing at the end of remark.")         \
                                                                            \
  product(intx, G1SATBBufferSize, 1*K,                                      \
          "Number of entries in an SATB log buffer.")                       \
                                                                            \
  develop(intx, G1SATBProcessCompletedThreshold, 20,                        \
          "Number of completed buffers that triggers log processing.")      \
                                                                            \
  product(uintx, G1SATBBufferEnqueueingThresholdPercent, 60,                \
          "Before enqueueing them, each mutator thread tries to do some "   \
          "filtering on the SATB buffers it generates. If post-filtering "  \
          "the percentage of retained entries is over this threshold "      \
          "the buffer will be enqueued for processing. A value of 0 "       \
          "specifies that mutator threads should not do such filtering.")   \
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
  develop(bool, G1DeferredRSUpdate, true,                                   \
          "If true, use deferred RS updates")                               \
                                                                            \
  develop(bool, G1RSLogCheckCardTable, false,                               \
          "If true, verify that no dirty cards remain after RS log "        \
          "processing.")                                                    \
                                                                            \
  diagnostic(bool, G1PrintRegionLivenessInfo, false,                        \
            "Prints the liveness information for all regions in the heap "  \
            "at the end of a marking cycle.")                               \
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
  product(uintx, G1ConcRSLogCacheSize, 10,                                  \
          "Log base 2 of the length of conc RS hot-card cache.")            \
                                                                            \
  product(uintx, G1ConcRSHotCardLimit, 4,                                   \
          "The threshold that defines (>=) a hot card.")                    \
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
          "The maximum number of verification failures to print.  "         \
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
  product(uintx, G1ReservePercent, 10,                                      \
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
  product(uintx, G1HeapRegionSize, 0,                                       \
          "Size of the G1 regions.")                                        \
                                                                            \
  experimental(bool, G1UseParallelRSetUpdating, true,                       \
          "Enables the parallelization of remembered set updating "         \
          "during evacuation pauses")                                       \
                                                                            \
  experimental(bool, G1UseParallelRSetScanning, true,                       \
          "Enables the parallelization of remembered set scanning "         \
          "during evacuation pauses")                                       \
                                                                            \
  product(uintx, G1ConcRefinementThreads, 0,                                \
          "If non-0 is the number of parallel rem set update threads, "     \
          "otherwise the value is determined ergonomically.")               \
                                                                            \
  develop(bool, G1VerifyCTCleanup, false,                                   \
          "Verify card table cleanup.")                                     \
                                                                            \
  product(uintx, G1RSetScanBlockSize, 64,                                   \
          "Size of a work unit of cards claimed by a worker thread"         \
          "during RSet scanning.")                                          \
                                                                            \
  develop(uintx, G1SecondaryFreeListAppendLength, 5,                        \
          "The number of regions we will add to the secondary free list "   \
          "at every append operation")                                      \
                                                                            \
  develop(bool, G1ConcRegionFreeingVerbose, false,                          \
          "Enables verboseness during concurrent region freeing")           \
                                                                            \
  develop(bool, G1StressConcRegionFreeing, false,                           \
          "It stresses the concurrent region freeing operation")            \
                                                                            \
  develop(uintx, G1StressConcRegionFreeingDelayMillis, 0,                   \
          "Artificial delay during concurrent region freeing")              \
                                                                            \
  develop(uintx, G1DummyRegionsPerGC, 0,                                    \
          "The number of dummy regions G1 will allocate at the end of "     \
          "each evacuation pause in order to artificially fill up the "     \
          "heap and stress the marking implementation.")                    \
                                                                            \
  develop(bool, G1ExitOnExpansionFailure, false,                            \
          "Raise a fatal VM exit out of memory failure in the event "       \
          " that heap expansion fails due to running out of swap.")         \
                                                                            \
  develop(uintx, G1ConcMarkForceOverflow, 0,                                \
          "The number of times we'll force an overflow during "             \
          "concurrent marking")                                             \
                                                                            \
  experimental(uintx, G1NewSizePercent, 5,                                  \
          "Percentage (0-100) of the heap size to use as default "          \
          "minimum young gen size.")                                        \
                                                                            \
  experimental(uintx, G1MaxNewSizePercent, 60,                              \
          "Percentage (0-100) of the heap size to use as default "          \
          " maximum young gen size.")                                       \
                                                                            \
  experimental(uintx, G1MixedGCLiveThresholdPercent, 65,                    \
          "Threshold for regions to be considered for inclusion in the "    \
          "collection set of mixed GCs. "                                   \
          "Regions with live bytes exceeding this will not be collected.")  \
                                                                            \
  product(uintx, G1HeapWastePercent, 10,                                    \
          "Amount of space, expressed as a percentage of the heap size, "   \
          "that G1 is willing not to collect to avoid expensive GCs.")      \
                                                                            \
  product(uintx, G1MixedGCCountTarget, 8,                                   \
          "The target number of mixed GCs after a marking cycle.")          \
                                                                            \
  experimental(uintx, G1OldCSetRegionThresholdPercent, 10,                  \
          "An upper bound for the number of old CSet regions expressed "    \
          "as a percentage of the heap size.")                              \
                                                                            \
  experimental(ccstr, G1LogLevel, NULL,                                     \
          "Log level for G1 logging: fine, finer, finest")                  \
                                                                            \
  notproduct(bool, G1EvacuationFailureALot, false,                          \
          "Force use of evacuation failure handling during certain "        \
          "evacuation pauses")                                              \
                                                                            \
  develop(uintx, G1EvacuationFailureALotCount, 1000,                        \
          "Number of successful evacuations between evacuation failures "   \
          "occurring at object copying")                                    \
                                                                            \
  develop(uintx, G1EvacuationFailureALotInterval, 5,                        \
          "Total collections between forced triggering of evacuation "      \
          "failures")                                                       \
                                                                            \
  develop(bool, G1EvacuationFailureALotDuringConcMark, true,                \
          "Force use of evacuation failure handling during evacuation "     \
          "pauses when marking is in progress")                             \
                                                                            \
  develop(bool, G1EvacuationFailureALotDuringInitialMark, true,             \
          "Force use of evacuation failure handling during initial mark "   \
          "evacuation pauses")                                              \
                                                                            \
  develop(bool, G1EvacuationFailureALotDuringYoungGC, true,                 \
          "Force use of evacuation failure handling during young "          \
          "evacuation pauses")                                              \
                                                                            \
  develop(bool, G1EvacuationFailureALotDuringMixedGC, true,                 \
          "Force use of evacuation failure handling during mixed "          \
          "evacuation pauses")                                              \
                                                                            \
  diagnostic(bool, G1VerifyRSetsDuringFullGC, false,                        \
             "If true, perform verification of each heap region's "         \
             "remembered set when verifying the heap during a full GC.")    \
                                                                            \
  diagnostic(bool, G1VerifyHeapRegionCodeRoots, false,                      \
             "Verify the code root lists attached to each heap region.")

G1_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG, DECLARE_MANAGEABLE_FLAG, DECLARE_PRODUCT_RW_FLAG)

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1_GLOBALS_HPP
