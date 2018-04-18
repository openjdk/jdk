/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_CMS_CMS_GLOBALS_HPP
#define SHARE_GC_CMS_CMS_GLOBALS_HPP

#define GC_CMS_FLAGS(develop,                                               \
                     develop_pd,                                            \
                     product,                                               \
                     product_pd,                                            \
                     diagnostic,                                            \
                     diagnostic_pd,                                         \
                     experimental,                                          \
                     notproduct,                                            \
                     manageable,                                            \
                     product_rw,                                            \
                     lp64_product,                                          \
                     range,                                                 \
                     constraint,                                            \
                     writeable)                                             \
  product(bool, UseCMSBestFit, true,                                        \
          "Use CMS best fit allocation strategy")                           \
                                                                            \
  product(size_t, CMSOldPLABMax, 1024,                                      \
          "Maximum size of CMS gen promotion LAB caches per worker "        \
          "per block size")                                                 \
          range(1, max_uintx)                                               \
          constraint(CMSOldPLABMaxConstraintFunc,AfterMemoryInit)           \
                                                                            \
  product(size_t, CMSOldPLABMin, 16,                                        \
          "Minimum size of CMS gen promotion LAB caches per worker "        \
          "per block size")                                                 \
          range(1, max_uintx)                                               \
          constraint(CMSOldPLABMinConstraintFunc,AfterMemoryInit)           \
                                                                            \
  product(uintx, CMSOldPLABNumRefills, 4,                                   \
          "Nominal number of refills of CMS gen promotion LAB cache "       \
          "per worker per block size")                                      \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSOldPLABResizeQuicker, false,                             \
          "React on-the-fly during a scavenge to a sudden "                 \
          "change in block demand rate")                                    \
                                                                            \
  product(uintx, CMSOldPLABToleranceFactor, 4,                              \
          "The tolerance of the phase-change detector for on-the-fly "      \
          "PLAB resizing during a scavenge")                                \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSOldPLABReactivityFactor, 2,                             \
          "The gain in the feedback loop for on-the-fly PLAB resizing "     \
          "during a scavenge")                                              \
          range(1, max_uintx)                                               \
                                                                            \
  product_pd(size_t, CMSYoungGenPerWorker,                                  \
          "The maximum size of young gen chosen by default per GC worker "  \
          "thread available")                                               \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSIncrementalSafetyFactor, 10,                            \
          "Percentage (0-100) used to add conservatism when computing the " \
          "duty cycle")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSExpAvgFactor, 50,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponential averages for CMS statistics")              \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_FLSWeight, 75,                                         \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying averages for CMS FLS "          \
          "statistics")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_FLSPadding, 1,                                         \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in free list demand")                         \
          range(0, max_juint)                                               \
                                                                            \
  product(uintx, FLSCoalescePolicy, 2,                                      \
          "CMS: aggressiveness level for coalescing, increasing "           \
          "from 0 to 4")                                                    \
          range(0, 4)                                                       \
                                                                            \
  product(bool, FLSAlwaysCoalesceLarge, false,                              \
          "CMS: larger free blocks are always available for coalescing")    \
                                                                            \
  product(double, FLSLargestBlockCoalesceProximity, 0.99,                   \
          "CMS: the smaller the percentage the greater the coalescing "     \
          "force")                                                          \
          range(0.0, 1.0)                                                   \
                                                                            \
  product(double, CMSSmallCoalSurplusPercent, 1.05,                         \
          "CMS: the factor by which to inflate estimated demand of small "  \
          "block sizes to prevent coalescing with an adjoining block")      \
          range(0.0, DBL_MAX)                                               \
                                                                            \
  product(double, CMSLargeCoalSurplusPercent, 0.95,                         \
          "CMS: the factor by which to inflate estimated demand of large "  \
          "block sizes to prevent coalescing with an adjoining block")      \
          range(0.0, DBL_MAX)                                               \
                                                                            \
  product(double, CMSSmallSplitSurplusPercent, 1.10,                        \
          "CMS: the factor by which to inflate estimated demand of small "  \
          "block sizes to prevent splitting to supply demand for smaller "  \
          "blocks")                                                         \
          range(0.0, DBL_MAX)                                               \
                                                                            \
  product(double, CMSLargeSplitSurplusPercent, 1.00,                        \
          "CMS: the factor by which to inflate estimated demand of large "  \
          "block sizes to prevent splitting to supply demand for smaller "  \
          "blocks")                                                         \
          range(0.0, DBL_MAX)                                               \
                                                                            \
  product(bool, CMSExtrapolateSweep, false,                                 \
          "CMS: cushion for block demand during sweep")                     \
                                                                            \
  product(uintx, CMS_SweepWeight, 75,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for inter-sweep "       \
          "duration")                                                       \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_SweepPadding, 1,                                       \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in inter-sweep duration")                     \
          range(0, max_juint)                                               \
                                                                            \
  product(uintx, CMS_SweepTimerThresholdMillis, 10,                         \
          "Skip block flux-rate sampling for an epoch unless inter-sweep "  \
          "duration exceeds this threshold in milliseconds")                \
          range(0, max_uintx)                                               \
                                                                            \
  product(bool, CMSClassUnloadingEnabled, true,                             \
          "Whether class unloading enabled when using CMS GC")              \
                                                                            \
  product(uintx, CMSClassUnloadingMaxInterval, 0,                           \
          "When CMS class unloading is enabled, the maximum CMS cycle "     \
          "count for which classes may not be unloaded")                    \
          range(0, max_uintx)                                               \
                                                                            \
  product(uintx, CMSIndexedFreeListReplenish, 4,                            \
          "Replenish an indexed free list with this number of chunks")      \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSReplenishIntermediate, true,                             \
          "Replenish all intermediate free-list caches")                    \
                                                                            \
  product(bool, CMSSplitIndexedFreeListBlocks, true,                        \
          "When satisfying batched demand, split blocks from the "          \
          "IndexedFreeList whose size is a multiple of requested size")     \
                                                                            \
  product(bool, CMSLoopWarn, false,                                         \
          "Warn in case of excessive CMS looping")                          \
                                                                            \
  notproduct(bool, CMSMarkStackOverflowALot, false,                         \
          "Simulate frequent marking stack / work queue overflow")          \
                                                                            \
  notproduct(uintx, CMSMarkStackOverflowInterval, 1000,                     \
          "An \"interval\" counter that determines how frequently "         \
          "to simulate overflow; a smaller number increases frequency")     \
                                                                            \
  product(uintx, CMSMaxAbortablePrecleanLoops, 0,                           \
          "Maximum number of abortable preclean iterations, if > 0")        \
          range(0, max_uintx)                                               \
                                                                            \
  product(intx, CMSMaxAbortablePrecleanTime, 5000,                          \
          "Maximum time in abortable preclean (in milliseconds)")           \
          range(0, max_intx)                                                \
                                                                            \
  product(uintx, CMSAbortablePrecleanMinWorkPerIteration, 100,              \
          "Nominal minimum work per abortable preclean iteration")          \
          range(0, max_uintx)                                               \
                                                                            \
  manageable(intx, CMSAbortablePrecleanWaitMillis, 100,                     \
          "Time that we sleep between iterations when not given "           \
          "enough work per iteration")                                      \
          range(0, max_intx)                                                \
                                                                            \
  /* 4096 = CardTable::card_size_in_words * BitsPerWord */                  \
  product(size_t, CMSRescanMultiple, 32,                                    \
          "Size (in cards) of CMS parallel rescan task")                    \
          range(1, SIZE_MAX / 4096)                                         \
          constraint(CMSRescanMultipleConstraintFunc,AfterMemoryInit)       \
                                                                            \
  /* 4096 = CardTable::card_size_in_words * BitsPerWord */                  \
  product(size_t, CMSConcMarkMultiple, 32,                                  \
          "Size (in cards) of CMS concurrent MT marking task")              \
          range(1, SIZE_MAX / 4096)                                         \
          constraint(CMSConcMarkMultipleConstraintFunc,AfterMemoryInit)     \
                                                                            \
  product(bool, CMSAbortSemantics, false,                                   \
          "Whether abort-on-overflow semantics is implemented")             \
                                                                            \
  product(bool, CMSParallelInitialMarkEnabled, true,                        \
          "Use the parallel initial mark.")                                 \
                                                                            \
  product(bool, CMSParallelRemarkEnabled, true,                             \
          "Whether parallel remark enabled (only if ParNewGC)")             \
                                                                            \
  product(bool, CMSParallelSurvivorRemarkEnabled, true,                     \
          "Whether parallel remark of survivor space "                      \
          "enabled (effective only if CMSParallelRemarkEnabled)")           \
                                                                            \
  product(bool, CMSPLABRecordAlways, true,                                  \
          "Always record survivor space PLAB boundaries (effective only "   \
          "if CMSParallelSurvivorRemarkEnabled)")                           \
                                                                            \
  product(bool, CMSEdenChunksRecordAlways, true,                            \
          "Always record eden chunks used for the parallel initial mark "   \
          "or remark of eden")                                              \
                                                                            \
  product(bool, CMSConcurrentMTEnabled, true,                               \
          "Whether multi-threaded concurrent work enabled "                 \
          "(effective only if ParNewGC)")                                   \
                                                                            \
  product(bool, CMSPrecleaningEnabled, true,                                \
          "Whether concurrent precleaning enabled")                         \
                                                                            \
  product(uintx, CMSPrecleanIter, 3,                                        \
          "Maximum number of precleaning iteration passes")                 \
          range(0, 9)                                                       \
                                                                            \
  product(uintx, CMSPrecleanDenominator, 3,                                 \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence " \
          "ratio")                                                          \
          range(1, max_uintx)                                               \
          constraint(CMSPrecleanDenominatorConstraintFunc,AfterErgo)        \
                                                                            \
  product(uintx, CMSPrecleanNumerator, 2,                                   \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence " \
          "ratio")                                                          \
          range(0, max_uintx-1)                                             \
          constraint(CMSPrecleanNumeratorConstraintFunc,AfterErgo)          \
                                                                            \
  product(bool, CMSPrecleanRefLists1, true,                                 \
          "Preclean ref lists during (initial) preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanRefLists2, false,                                \
          "Preclean ref lists during abortable preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanSurvivors1, false,                               \
          "Preclean survivors during (initial) preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanSurvivors2, true,                                \
          "Preclean survivors during abortable preclean phase")             \
                                                                            \
  product(uintx, CMSPrecleanThreshold, 1000,                                \
          "Do not iterate again if number of dirty cards is less than this")\
          range(100, max_uintx)                                             \
                                                                            \
  product(bool, CMSCleanOnEnter, true,                                      \
          "Clean-on-enter optimization for reducing number of dirty cards") \
                                                                            \
  product(uintx, CMSRemarkVerifyVariant, 1,                                 \
          "Choose variant (1,2) of verification following remark")          \
          range(1, 2)                                                       \
                                                                            \
  product(size_t, CMSScheduleRemarkEdenSizeThreshold, 2*M,                  \
          "If Eden size is below this, do not try to schedule remark")      \
          range(0, max_uintx)                                               \
                                                                            \
  product(uintx, CMSScheduleRemarkEdenPenetration, 50,                      \
          "The Eden occupancy percentage (0-100) at which "                 \
          "to try and schedule remark pause")                               \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSScheduleRemarkSamplingRatio, 5,                         \
          "Start sampling eden top at least before young gen "              \
          "occupancy reaches 1/<ratio> of the size at which "               \
          "we plan to schedule remark")                                     \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSSamplingGrain, 16*K,                                    \
          "The minimum distance between eden samples for CMS (see above)")  \
          range(ObjectAlignmentInBytes, max_uintx)                          \
          constraint(CMSSamplingGrainConstraintFunc,AfterMemoryInit)        \
                                                                            \
  product(bool, CMSScavengeBeforeRemark, false,                             \
          "Attempt scavenge before the CMS remark step")                    \
                                                                            \
  product(uintx, CMSWorkQueueDrainThreshold, 10,                            \
          "Don't drain below this size per parallel worker/thief")          \
          range(1, max_juint)                                               \
          constraint(CMSWorkQueueDrainThresholdConstraintFunc,AfterErgo)    \
                                                                            \
  manageable(intx, CMSWaitDuration, 2000,                                   \
          "Time in milliseconds that CMS thread waits for young GC")        \
          range(min_jint, max_jint)                                         \
                                                                            \
  develop(uintx, CMSCheckInterval, 1000,                                    \
          "Interval in milliseconds that CMS thread checks if it "          \
          "should start a collection cycle")                                \
                                                                            \
  product(bool, CMSYield, true,                                             \
          "Yield between steps of CMS")                                     \
                                                                            \
  product(size_t, CMSBitMapYieldQuantum, 10*M,                              \
          "Bitmap operations should process at most this many bits "        \
          "between yields")                                                 \
          range(1, max_uintx)                                               \
          constraint(CMSBitMapYieldQuantumConstraintFunc,AfterMemoryInit)   \
                                                                            \
  product(bool, CMSPrintChunksInDump, false,                                \
          "If logging for the \"gc\" and \"promotion\" tags is enabled on"  \
          "trace level include more detailed information about the"         \
          "free chunks")                                                    \
                                                                            \
  product(bool, CMSPrintObjectsInDump, false,                               \
          "If logging for the \"gc\" and \"promotion\" tags is enabled on"  \
          "trace level include more detailed information about the"         \
          "allocated objects")                                              \
                                                                            \
  diagnostic(bool, FLSVerifyAllHeapReferences, false,                       \
          "Verify that all references across the FLS boundary "             \
          "are to valid objects")                                           \
                                                                            \
  diagnostic(bool, FLSVerifyLists, false,                                   \
          "Do lots of (expensive) FreeListSpace verification")              \
                                                                            \
  diagnostic(bool, FLSVerifyIndexTable, false,                              \
          "Do lots of (expensive) FLS index table verification")            \
                                                                            \
  product(uintx, CMSTriggerRatio, 80,                                       \
          "Percentage of MinHeapFreeRatio in CMS generation that is "       \
          "allocated before a CMS collection cycle commences")              \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSBootstrapOccupancy, 50,                                 \
          "Percentage CMS generation occupancy at which to "                \
          "initiate CMS collection for bootstrapping collection stats")     \
          range(0, 100)                                                     \
                                                                            \
  product(intx, CMSInitiatingOccupancyFraction, -1,                         \
          "Percentage CMS generation occupancy to start a CMS collection "  \
          "cycle. A negative value means that CMSTriggerRatio is used")     \
          range(min_intx, 100)                                              \
                                                                            \
  manageable(intx, CMSTriggerInterval, -1,                                  \
          "Commence a CMS collection cycle (at least) every so many "       \
          "milliseconds (0 permanently, -1 disabled)")                      \
          range(-1, max_intx)                                               \
                                                                            \
  product(bool, UseCMSInitiatingOccupancyOnly, false,                       \
          "Only use occupancy as a criterion for starting a CMS collection")\
                                                                            \
  product(uintx, CMSIsTooFullPercentage, 98,                                \
          "An absolute ceiling above which CMS will always consider the "   \
          "unloading of classes when class unloading is enabled")           \
          range(0, 100)                                                     \
                                                                            \
  develop(bool, CMSTestInFreeList, false,                                   \
          "Check if the coalesced range is already in the "                 \
          "free lists as claimed")                                          \
                                                                            \
  notproduct(bool, CMSVerifyReturnedBytes, false,                           \
          "Check that all the garbage collected was returned to the "       \
          "free lists")                                                     \
                                                                            \
  diagnostic(bool, BindCMSThreadToCPU, false,                               \
          "Bind CMS Thread to CPU if possible")                             \
                                                                            \
  diagnostic(uintx, CPUForCMSThread, 0,                                     \
          "When BindCMSThreadToCPU is true, the CPU to bind CMS thread to") \
          range(0, max_juint)                                               \
                                                                            \
  product(uintx, CMSCoordinatorYieldSleepCount, 10,                         \
          "Number of times the coordinator GC thread will sleep while "     \
          "yielding before giving up and resuming GC")                      \
          range(0, max_juint)                                               \
                                                                            \
  product(uintx, CMSYieldSleepCount, 0,                                     \
          "Number of times a GC thread (minus the coordinator) "            \
          "will sleep while yielding before giving up and resuming GC")     \
          range(0, max_juint)                                               \
                                                                            \
  product(bool, ParGCUseLocalOverflow, false,                               \
          "Instead of a global overflow list, use local overflow stacks")   \
                                                                            \
  product(bool, ParGCTrimOverflow, true,                                    \
          "Eagerly trim the local overflow lists "                          \
          "(when ParGCUseLocalOverflow)")                                   \
                                                                            \
  notproduct(bool, ParGCWorkQueueOverflowALot, false,                       \
          "Simulate work queue overflow in ParNew")                         \
                                                                            \
  notproduct(uintx, ParGCWorkQueueOverflowInterval, 1000,                   \
          "An `interval' counter that determines how frequently "           \
          "we simulate overflow; a smaller number increases frequency")     \
                                                                            \
  product(uintx, ParGCDesiredObjsFromOverflowList, 20,                      \
          "The desired number of objects to claim from the overflow list")  \
          range(0, max_uintx)                                               \
                                                                            \
  diagnostic(uintx, ParGCStridesPerThread, 2,                               \
          "The number of strides per worker thread that we divide up the "  \
          "card table scanning work into")                                  \
          range(1, max_uintx)                                               \
          constraint(ParGCStridesPerThreadConstraintFunc,AfterErgo)         \
                                                                            \
  diagnostic(intx, ParGCCardsPerStrideChunk, 256,                           \
          "The number of cards in each chunk of the parallel chunks used "  \
          "during card table scanning")                                     \
          range(1, max_intx)                                                \
          constraint(ParGCCardsPerStrideChunkConstraintFunc,AfterMemoryInit)

#endif // SHARE_GC_CMS_CMS_GLOBALS_HPP
