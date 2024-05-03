/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1_GLOBALS_HPP
#define SHARE_GC_G1_G1_GLOBALS_HPP

#include "runtime/globals_shared.hpp"

// Enable allocation failure injector by default in non-product builds.

#ifdef ALLOCATION_FAILURE_INJECTOR
#error "ALLOCATION_FAILURE_INJECTOR already defined"
#endif
#ifndef PRODUCT
#define ALLOCATION_FAILURE_INJECTOR 1
#else
#define ALLOCATION_FAILURE_INJECTOR 0
#endif

#if ALLOCATION_FAILURE_INJECTOR
#define GC_G1_EVACUATION_FAILURE_FLAGS(develop,                             \
                                       develop_pd,                          \
                                       product,                             \
                                       product_pd,                          \
                                       range,                               \
                                       constraint)                          \
                                                                            \
  product(bool, G1GCAllocationFailureALot, false,                           \
          "Force execution of evacuation failure handling by inducing "     \
          "allocation failures during certain young collection pauses")     \
                                                                            \
  product(uintx, G1GCAllocationFailureALotCount, 1000,                      \
          "Number of successful evacuations between induced allocation "    \
          "failures occurring at object copying per thread")                \
                                                                            \
  product(uintx, G1GCAllocationFailureALotInterval, 5,                      \
          "Total collections between forced triggering of evacuation "      \
          "failures")                                                       \
                                                                            \
  product(bool, G1GCAllocationFailureALotDuringConcMark, true,              \
          "Trigger evacuation failure handling in collection pauses where " \
          "marking is in progress")                                         \
                                                                            \
  product(bool, G1GCAllocationFailureALotDuringConcurrentStart, true,       \
          "Trigger evacuation failure handling during concurrent start "    \
          "collection pauses")                                              \
                                                                            \
  product(bool, G1GCAllocationFailureALotDuringYoungGC, true,               \
          "Trigger evacuation failure handling during young collection "    \
          "pauses")                                                         \
                                                                            \
  product(bool, G1GCAllocationFailureALotDuringMixedGC, true,               \
          "Force use of evacuation failure handling during mixed "          \
          "collection pauses")                                              \
                                                                            \
  product(uint, G1GCAllocationFailureALotCSetPercent, 100,                  \
          "The percentage of regions in the collection set starting "       \
          "from the beginning where the allocation failures are "           \
          "injected.")                                                      \
          range(1, 100)
#else
#define GC_G1_EVACUATION_FAILURE_FLAGS(develop,                             \
                                       develop_pd,                          \
                                       product,                             \
                                       product_pd,                          \
                                       range,                               \
                                       constraint)
#endif
//
// Defines all globals flags used by the garbage-first compiler.
//

#define GC_G1_FLAGS(develop,                                                \
                    develop_pd,                                             \
                    product,                                                \
                    product_pd,                                             \
                    range,                                                  \
                    constraint)                                             \
                                                                            \
  product(bool, G1UseAdaptiveIHOP, true,                                    \
          "Adaptively adjust the initiating heap occupancy from the "       \
          "initial value of InitiatingHeapOccupancyPercent. The policy "    \
          "attempts to start marking in time based on application "         \
          "behavior.")                                                      \
                                                                            \
  product(size_t, G1AdaptiveIHOPNumInitialSamples, 3, EXPERIMENTAL,         \
          "How many completed time periods from concurrent start to first " \
          "mixed gc are required to use the input values for prediction "   \
          "of the optimal occupancy to start marking.")                     \
          range(1, max_intx)                                                \
                                                                            \
  product(uint, G1ConfidencePercent, 50,                                    \
          "Confidence level for MMU/pause predictions")                     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, G1SummarizeRSetStatsPeriod, 0, DIAGNOSTIC,                 \
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
  product(bool, G1UseReferencePrecleaning, true, EXPERIMENTAL,              \
               "Concurrently preclean java.lang.ref.references instances "  \
               "before the Remark pause.")                                  \
                                                                            \
  product(double, G1LastPLABAverageOccupancy, 50.0, EXPERIMENTAL,           \
               "The expected average occupancy of the last PLAB in "        \
               "percent.")                                                  \
               range(0.001, 100.0)                                          \
                                                                            \
  product(size_t, G1SATBBufferSize, 1*K,                                    \
          "Number of entries in an SATB log buffer.")                       \
          constraint(G1SATBBufferSizeConstraintFunc, AtParse)               \
                                                                            \
  develop(uintx, G1SATBProcessCompletedThreshold, 20,                       \
          "Number of completed buffers that triggers log processing.")      \
          range(0, max_jint)                                                \
                                                                            \
  product(uint, G1SATBBufferEnqueueingThresholdPercent, 60,                \
          "Before enqueueing them, each mutator thread tries to do some "   \
          "filtering on the SATB buffers it generates. If post-filtering "  \
          "the percentage of retained entries is over this threshold "      \
          "the buffer will be enqueued for processing. A value of 0 "       \
          "specifies that mutator threads should not do such filtering.")   \
          range(0, 100)                                                     \
                                                                            \
  product(uint, G1ExpandByPercentOfAvailable, 20, EXPERIMENTAL,             \
          "When expanding, % of uncommitted space to claim.")               \
          range(0, 100)                                                     \
                                                                            \
  product(size_t, G1UpdateBufferSize, 256,                                  \
          "Size of an update buffer")                                       \
          constraint(G1UpdateBufferSizeConstraintFunc, AtParse)             \
                                                                            \
  product(uint, G1RSetUpdatingPauseTimePercent, 10,                         \
          "A target percentage of time that is allowed to be spend on "     \
          "processing remembered set update buffers during the collection " \
          "pause.")                                                         \
          range(0, 100)                                                     \
                                                                            \
  product(bool, G1UseConcRefinement, true, DIAGNOSTIC,                      \
          "Control whether concurrent refinement is performed. "            \
          "Disabling effectively ignores G1RSetUpdatingPauseTimePercent")   \
                                                                            \
  develop(uint, G1RemSetArrayOfCardsEntriesBase, 8,                         \
          "Maximum number of entries per region in the Array of Cards "     \
          "card set container per MB of a heap region.")                    \
          range(1, 65536)                                                   \
                                                                            \
  product(uint, G1RemSetArrayOfCardsEntries, 0,  EXPERIMENTAL,              \
          "Maximum number of entries per Array of Cards card set "          \
          "container. Will be set ergonomically by default.")               \
          range(0, 65536)                                                   \
          constraint(G1RemSetArrayOfCardsEntriesConstraintFunc,AfterErgo)   \
                                                                            \
  product(uint, G1RemSetHowlMaxNumBuckets, 8, EXPERIMENTAL,                 \
          "Maximum number of buckets per Howl card set container. The "     \
          "default gives at worst bitmaps of size 8k. This showed to be a " \
          "good tradeoff between bitmap size (waste) and cacheability of "  \
          "the bucket array. Must be a power of two.")                      \
          range(1, 1024)                                                    \
          constraint(G1RemSetHowlMaxNumBucketsConstraintFunc,AfterErgo)     \
                                                                            \
  product(uint, G1RemSetHowlNumBuckets, 0, EXPERIMENTAL,                    \
          "Number of buckets per Howl card set container. Must be a power " \
          "of two. Will be set ergonomically by default.")                  \
          range(0, 1024)                                                    \
          constraint(G1RemSetHowlNumBucketsConstraintFunc,AfterErgo)        \
                                                                            \
  product(uint, G1RemSetCoarsenHowlBitmapToHowlFullPercent, 90, EXPERIMENTAL, \
          "Percentage at which to coarsen a Howl bitmap to Howl full card " \
          "set container.")                                                 \
          range(1, 100)                                                     \
                                                                            \
  product(uint, G1RemSetCoarsenHowlToFullPercent, 90, EXPERIMENTAL,         \
          "Percentage at which to coarsen a Howl card set to Full card "    \
          "set container.")                                                 \
          range(1, 100)                                                     \
                                                                            \
  develop(size_t, G1MaxVerifyFailures, SIZE_MAX,                            \
          "The maximum number of liveness and remembered set verification " \
          "failures to print per thread.")                                  \
          range(1, SIZE_MAX)                                                \
                                                                            \
  product(uint, G1ReservePercent, 10,                                      \
          "It determines the minimum reserve we should have in the heap "   \
          "to minimize the probability of promotion failure.")              \
          range(0, 50)                                                      \
                                                                            \
  product(size_t, G1HeapRegionSize, 0,                                      \
          "Size of the G1 regions.")                                        \
          range(0, NOT_LP64(32*M) LP64_ONLY(512*M))                         \
          constraint(G1HeapRegionSizeConstraintFunc,AfterMemoryInit)        \
                                                                            \
  product(uint, G1ConcRefinementThreads, 0,                                 \
          "The number of parallel remembered set update threads. "          \
          "Will be set ergonomically by default.")                          \
          range(0, (max_jint-1)/wordSize)                                   \
                                                                            \
  product(uint, G1MaxNewSizePercent, 60, EXPERIMENTAL,                     \
          "Percentage (0-100) of the heap size to use as default "          \
          " maximum young gen size.")                                       \
          range(0, 100)                                                     \
          constraint(G1MaxNewSizePercentConstraintFunc,AfterErgo)           \
                                                                            \
  product(uint, G1NewSizePercent, 5, EXPERIMENTAL,                         \
          "Percentage (0-100) of the heap size to use as default "          \
          "minimum young gen size.")                                        \
          range(0, 100)                                                     \
          constraint(G1NewSizePercentConstraintFunc,AfterErgo)              \
                                                                            \
  product(uint, G1MixedGCLiveThresholdPercent, 85, EXPERIMENTAL,           \
          "Threshold for regions to be considered for inclusion in the "    \
          "collection set of mixed GCs. "                                   \
          "Regions with live bytes exceeding this will not be collected.")  \
          range(0, 100)                                                     \
                                                                            \
  product(uint, G1RetainRegionLiveThresholdPercent, 85, EXPERIMENTAL,      \
          "Threshold for evacuation failed regions to be considered for "   \
          "inclusion in the collection set candidates."                     \
          "Regions with live bytes exceeding this will not be retained.")   \
          range(0, 100)                                                     \
                                                                            \
  product(uint, G1HeapWastePercent, 5,                                     \
          "Amount of space, expressed as a percentage of the heap size, "   \
          "that G1 is willing not to collect to avoid expensive GCs.")      \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, G1MixedGCCountTarget, 8,                                   \
          "The target number of mixed GCs after a marking cycle.")          \
          range(0, max_uintx)                                               \
                                                                            \
  product(uint, G1EagerReclaimRemSetThreshold, 0, EXPERIMENTAL,             \
          "Maximum number of remembered set entries a humongous region "    \
          "otherwise eligible for eager reclaim may have to be a candidate "\
          "for eager reclaim. Will be selected ergonomically by default.")  \
                                                                            \
  product(size_t, G1RebuildRemSetChunkSize, 256 * K, EXPERIMENTAL,          \
          "Chunk size used for rebuilding the remembered set.")             \
          range(4 * K, 32 * M)                                              \
                                                                            \
  product(uint, G1OldCSetRegionThresholdPercent, 10, EXPERIMENTAL,         \
          "An upper bound for the number of old CSet regions expressed "    \
          "as a percentage of the heap size.")                              \
          range(0, 100)                                                     \
                                                                            \
  product(bool, G1VerifyHeapRegionCodeRoots, false, DIAGNOSTIC,             \
          "Verify the code root lists attached to each heap region.")       \
                                                                            \
  develop(bool, G1VerifyBitmaps, false,                                     \
          "Verifies the consistency of the marking bitmaps")                \
                                                                            \
  product(uintx, G1PeriodicGCInterval, 0, MANAGEABLE,                       \
          "Number of milliseconds after a previous GC to wait before "      \
          "triggering a periodic gc. A value of zero disables periodically "\
          "enforced gc cycles.")                                            \
                                                                            \
  product(bool, G1PeriodicGCInvokesConcurrent, true,                        \
          "Determines the kind of periodic GC. Set to true to have G1 "     \
          "perform a concurrent GC as periodic GC, otherwise use a STW "    \
          "Full GC.")                                                       \
                                                                            \
  product(double, G1PeriodicGCSystemLoadThreshold, 0.0, MANAGEABLE,         \
          "Maximum recent system wide load as returned by the 1m value "    \
          "of getloadavg() at which G1 triggers a periodic GC. A load "     \
          "above this value cancels a given periodic GC. A value of zero "  \
          "disables this check.")                                           \
          range(0.0, (double)max_uintx)                                     \
                                                                            \
  product(uint, G1RemSetFreeMemoryRescheduleDelayMillis, 10, EXPERIMENTAL,  \
          "Time after which the card set free memory task reschedules "     \
          "itself if there is work remaining.")                             \
          range(1, UINT_MAX)                                                \
                                                                            \
  product(double, G1RemSetFreeMemoryStepDurationMillis, 1, EXPERIMENTAL,    \
          "The amount of time that the free memory task should spend "      \
          "before a pause of G1RemSetFreeMemoryRescheduleDelayMillis "      \
          "length.")                                                        \
          range(1e-3, 1e+6)                                                 \
                                                                            \
  product(double, G1RemSetFreeMemoryKeepExcessRatio, 0.1, EXPERIMENTAL,     \
          "The percentage of free card set memory that G1 should keep as "  \
          "percentage of the currently used memory.")                       \
          range(0.0, 1.0)                                                   \
                                                                            \
  product(uint, G1RestoreRetainedRegionChunksPerWorker, 16, DIAGNOSTIC,     \
          "The number of chunks assigned per worker thread for "            \
          "retained region restore purposes.")                              \
          range(1, 256)                                                     \
                                                                            \
  product(uint, G1NumCollectionsKeepPinned, 8, DIAGNOSTIC,                  \
          "After how many GCs a region has been found pinned G1 should "    \
          "give up reclaiming it.")                                         \
                                                                            \
  product(uint, G1NumCardsCostSampleThreshold, 1000, DIAGNOSTIC,            \
          "Threshold for the number of cards when reporting remembered set "\
          "card cost related prediction samples. A sample must involve "    \
          "the same or more than that number of cards to be used.")         \
                                                                            \
  product(uint, G1NumCodeRootsCostSampleThreshold, 100, DIAGNOSTIC,         \
          "Threshold for the number of code roots when reporting code root "\
          "scan cost related prediction samples. A sample must involve "    \
          "the same or more than this number of code roots to be used.")    \
                                                                            \
  GC_G1_EVACUATION_FAILURE_FLAGS(develop,                                   \
                    develop_pd,                                             \
                    product,                                                \
                    product_pd,                                             \
                    range,                                                  \
                    constraint)

// end of GC_G1_FLAGS

#endif // SHARE_GC_G1_G1_GLOBALS_HPP
