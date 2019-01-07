/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1_GLOBALS_HPP
#define SHARE_VM_GC_G1_G1_GLOBALS_HPP

#include <float.h> // for DBL_MAX
//
// Defines all globals flags used by the garbage-first compiler.
//

#define GC_G1_FLAGS(develop,                                                \
                    develop_pd,                                             \
                    product,                                                \
                    product_pd,                                             \
                    diagnostic,                                             \
                    diagnostic_pd,                                          \
                    experimental,                                           \
                    notproduct,                                             \
                    manageable,                                             \
                    product_rw,                                             \
                    lp64_product,                                           \
                    range,                                                  \
                    constraint,                                             \
                    writeable)                                              \
                                                                            \
  product(bool, G1UseAdaptiveIHOP, true,                                    \
          "Adaptively adjust the initiating heap occupancy from the "       \
          "initial value of InitiatingHeapOccupancyPercent. The policy "    \
          "attempts to start marking in time based on application "         \
          "behavior.")                                                      \
                                                                            \
  experimental(size_t, G1AdaptiveIHOPNumInitialSamples, 3,                  \
          "How many completed time periods from initial mark to first "     \
          "mixed gc are required to use the input values for prediction "   \
          "of the optimal occupancy to start marking.")                     \
          range(1, max_intx)                                                \
                                                                            \
  product(uintx, G1ConfidencePercent, 50,                                   \
          "Confidence level for MMU/pause predictions")                     \
          range(0, 100)                                                     \
                                                                            \
  diagnostic(intx, G1SummarizeRSetStatsPeriod, 0,                           \
          "The period (in number of GCs) at which we will generate "        \
          "update buffer processing info "                                  \
          "(0 means do not periodically generate this info); "              \
          "it also requires that logging is enabled on the trace"           \
          "level for gc+remset")                                            \
          range(0, max_intx)                                                \
                                                                            \
  product(double, G1ConcMarkStepDurationMillis, 10.0,                       \
          "Target duration of individual concurrent marking steps "         \
          "in milliseconds.")                                               \
          range(1.0, DBL_MAX)                                               \
                                                                            \
  product(uint, G1RefProcDrainInterval, 1000,                               \
          "The number of discovered reference objects to process before "   \
          "draining concurrent marking work queues.")                       \
          range(1, INT_MAX)                                                 \
                                                                            \
  experimental(bool, G1UseReferencePrecleaning, true,                       \
               "Concurrently preclean java.lang.ref.references instances "  \
               "before the Remark pause.")                                  \
                                                                            \
  experimental(double, G1LastPLABAverageOccupancy, 50.0,                    \
               "The expected average occupancy of the last PLAB in "        \
               "percent.")                                                  \
               range(0.001, 100.0)                                          \
                                                                            \
  product(size_t, G1SATBBufferSize, 1*K,                                    \
          "Number of entries in an SATB log buffer.")                       \
          range(1, max_uintx)                                               \
                                                                            \
  develop(intx, G1SATBProcessCompletedThreshold, 20,                        \
          "Number of completed buffers that triggers log processing.")      \
          range(0, max_jint)                                                \
                                                                            \
  product(uintx, G1SATBBufferEnqueueingThresholdPercent, 60,                \
          "Before enqueueing them, each mutator thread tries to do some "   \
          "filtering on the SATB buffers it generates. If post-filtering "  \
          "the percentage of retained entries is over this threshold "      \
          "the buffer will be enqueued for processing. A value of 0 "       \
          "specifies that mutator threads should not do such filtering.")   \
          range(0, 100)                                                     \
                                                                            \
  experimental(intx, G1ExpandByPercentOfAvailable, 20,                      \
          "When expanding, % of uncommitted space to claim.")               \
          range(0, 100)                                                     \
                                                                            \
  product(size_t, G1UpdateBufferSize, 256,                                  \
          "Size of an update buffer")                                       \
          range(1, NOT_LP64(32*M) LP64_ONLY(1*G))                           \
                                                                            \
  product(size_t, G1ConcRefinementYellowZone, 0,                            \
          "Number of enqueued update buffers that will "                    \
          "trigger concurrent processing. Will be selected ergonomically "  \
          "by default.")                                                    \
          range(0, max_intx)                                                \
                                                                            \
  product(size_t, G1ConcRefinementRedZone, 0,                               \
          "Maximum number of enqueued update buffers before mutator "       \
          "threads start processing new ones instead of enqueueing them. "  \
          "Will be selected ergonomically by default.")                     \
          range(0, max_intx)                                                \
                                                                            \
  product(size_t, G1ConcRefinementGreenZone, 0,                             \
          "The number of update buffers that are left in the queue by the " \
          "concurrent processing threads. Will be selected ergonomically "  \
          "by default.")                                                    \
          range(0, max_intx)                                                \
                                                                            \
  product(uintx, G1ConcRefinementServiceIntervalMillis, 300,                \
          "The last concurrent refinement thread wakes up every "           \
          "specified number of milliseconds to do miscellaneous work.")     \
          range(0, max_jint)                                                \
                                                                            \
  product(size_t, G1ConcRefinementThresholdStep, 2,                         \
          "Each time the rset update queue increases by this amount "       \
          "activate the next refinement thread if available. "              \
          "The actual step size will be selected ergonomically by "         \
          "default, with this value used to determine a lower bound.")      \
          range(1, SIZE_MAX)                                                \
                                                                            \
  product(intx, G1RSetUpdatingPauseTimePercent, 10,                         \
          "A target percentage of time that is allowed to be spend on "     \
          "process RS update buffers during the collection pause.")         \
          range(0, 100)                                                     \
                                                                            \
  product(bool, G1UseAdaptiveConcRefinement, true,                          \
          "Select green, yellow and red zones adaptively to meet the "      \
          "the pause requirements.")                                        \
                                                                            \
  product(size_t, G1ConcRSLogCacheSize, 10,                                 \
          "Log base 2 of the length of conc RS hot-card cache.")            \
          range(0, 27)                                                      \
                                                                            \
  product(uintx, G1ConcRSHotCardLimit, 4,                                   \
          "The threshold that defines (>=) a hot card.")                    \
          range(0, max_jubyte)                                              \
                                                                            \
  develop(intx, G1RSetRegionEntriesBase, 256,                               \
          "Max number of regions in a fine-grain table per MB.")            \
          range(1, max_jint/wordSize)                                       \
                                                                            \
  product(intx, G1RSetRegionEntries, 0,                                     \
          "Max number of regions for which we keep bitmaps."                \
          "Will be set ergonomically by default")                           \
          range(0, max_jint/wordSize)                                       \
          constraint(G1RSetRegionEntriesConstraintFunc,AfterErgo)           \
                                                                            \
  develop(intx, G1RSetSparseRegionEntriesBase, 4,                           \
          "Max number of entries per region in a sparse table "             \
          "per MB.")                                                        \
          range(1, max_jint/wordSize)                                       \
                                                                            \
  product(intx, G1RSetSparseRegionEntries, 0,                               \
          "Max number of entries per region in a sparse table."             \
          "Will be set ergonomically by default.")                          \
          range(0, max_jint/wordSize)                                       \
          constraint(G1RSetSparseRegionEntriesConstraintFunc,AfterErgo)     \
                                                                            \
  develop(intx, G1MaxVerifyFailures, -1,                                    \
          "The maximum number of verification failures to print.  "         \
          "-1 means print all.")                                            \
          range(-1, max_jint)                                               \
                                                                            \
  product(uintx, G1ReservePercent, 10,                                      \
          "It determines the minimum reserve we should have in the heap "   \
          "to minimize the probability of promotion failure.")              \
          range(0, 50)                                                      \
                                                                            \
  product(size_t, G1HeapRegionSize, 0,                                      \
          "Size of the G1 regions.")                                        \
          range(0, 32*M)                                                    \
          constraint(G1HeapRegionSizeConstraintFunc,AfterMemoryInit)        \
                                                                            \
  product(uint, G1ConcRefinementThreads, 0,                                 \
          "The number of parallel rem set update threads. "                 \
          "Will be set ergonomically by default.")                          \
          range(0, (max_jint-1)/wordSize)                                   \
                                                                            \
  develop(bool, G1VerifyCTCleanup, false,                                   \
          "Verify card table cleanup.")                                     \
                                                                            \
  product(size_t, G1RSetScanBlockSize, 64,                                  \
          "Size of a work unit of cards claimed by a worker thread"         \
          "during RSet scanning.")                                          \
          range(1, max_uintx)                                               \
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
  experimental(uintx, G1MaxNewSizePercent, 60,                              \
          "Percentage (0-100) of the heap size to use as default "          \
          " maximum young gen size.")                                       \
          range(0, 100)                                                     \
          constraint(G1MaxNewSizePercentConstraintFunc,AfterErgo)           \
                                                                            \
  experimental(uintx, G1NewSizePercent, 5,                                  \
          "Percentage (0-100) of the heap size to use as default "          \
          "minimum young gen size.")                                        \
          range(0, 100)                                                     \
          constraint(G1NewSizePercentConstraintFunc,AfterErgo)              \
                                                                            \
  experimental(uintx, G1MixedGCLiveThresholdPercent, 85,                    \
          "Threshold for regions to be considered for inclusion in the "    \
          "collection set of mixed GCs. "                                   \
          "Regions with live bytes exceeding this will not be collected.")  \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, G1HeapWastePercent, 5,                                     \
          "Amount of space, expressed as a percentage of the heap size, "   \
          "that G1 is willing not to collect to avoid expensive GCs.")      \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, G1MixedGCCountTarget, 8,                                   \
          "The target number of mixed GCs after a marking cycle.")          \
          range(0, max_uintx)                                               \
                                                                            \
  experimental(bool, G1PretouchAuxiliaryMemory, false,                      \
          "Pre-touch large auxiliary data structures used by the GC.")      \
                                                                            \
  experimental(bool, G1EagerReclaimHumongousObjects, true,                  \
          "Try to reclaim dead large objects at every young GC.")           \
                                                                            \
  experimental(bool, G1EagerReclaimHumongousObjectsWithStaleRefs, true,     \
          "Try to reclaim dead large objects that have a few stale "        \
          "references at every young GC.")                                  \
                                                                            \
  experimental(size_t, G1RebuildRemSetChunkSize, 256 * K,                   \
          "Chunk size used for rebuilding the remembered set.")             \
          range(4 * K, 32 * M)                                              \
                                                                            \
  experimental(uintx, G1OldCSetRegionThresholdPercent, 10,                  \
          "An upper bound for the number of old CSet regions expressed "    \
          "as a percentage of the heap size.")                              \
          range(0, 100)                                                     \
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
          "If true, perform verification of each heap region's "            \
          "remembered set when verifying the heap during a full GC.")       \
                                                                            \
  diagnostic(bool, G1VerifyHeapRegionCodeRoots, false,                      \
          "Verify the code root lists attached to each heap region.")       \
                                                                            \
  develop(bool, G1VerifyBitmaps, false,                                     \
          "Verifies the consistency of the marking bitmaps")                \
                                                                            \
  manageable(uintx, G1PeriodicGCInterval, 0,                                \
          "Number of milliseconds after a previous GC to wait before "      \
          "triggering a periodic gc. A value of zero disables periodically "\
          "enforced gc cycles.")                                            \
                                                                            \
  product(bool, G1PeriodicGCInvokesConcurrent, true,                        \
          "Determines the kind of periodic GC. Set to true to have G1 "     \
          "perform a concurrent GC as periodic GC, otherwise use a STW "    \
          "Full GC.")                                                       \
                                                                            \
  manageable(double, G1PeriodicGCSystemLoadThreshold, 0.0,                  \
          "Maximum recent system wide load as returned by the 1m value "    \
          "of getloadavg() at which G1 triggers a periodic GC. A load "     \
          "above this value cancels a given periodic GC. A value of zero "  \
          "disables this check.")                                           \
          range(0.0, (double)max_uintx)                                     \
                                                                            \
  experimental(uintx, G1YoungExpansionBufferPercent, 10,                    \
               "When heterogenous heap is enabled by AllocateOldGenAt "     \
               "option, after every GC, young gen is re-sized which "       \
               "involves system calls to commit/uncommit memory. To "       \
               "reduce these calls, we keep a buffer of extra regions to "  \
               "absorb small changes in young gen length. This flag takes " \
               "the buffer size as an percentage of young gen length")      \
               range(0, 100)                                                \


#endif // SHARE_VM_GC_G1_G1_GLOBALS_HPP
