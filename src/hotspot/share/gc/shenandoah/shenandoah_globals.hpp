/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAH_GLOBALS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAH_GLOBALS_HPP

#define GC_SHENANDOAH_FLAGS(develop,                                        \
                            develop_pd,                                     \
                            product,                                        \
                            product_pd,                                     \
                            range,                                          \
                            constraint)                                     \
                                                                            \
  product(uintx, ShenandoahGenerationalMinPIPUsage, 30, EXPERIMENTAL,       \
          "(Generational mode only) What percent of a heap region "         \
          "should be used before we consider promoting a region in "        \
          "place?  Regions with less than this amount of used will "        \
          "promoted by evacuation.  A benefit of promoting in place "       \
          "is that less work is required by the GC at the time the "        \
          "region is promoted.  A disadvantage of promoting in place "      \
          "is that this introduces fragmentation of old-gen memory, "       \
          "with old-gen regions scattered throughout the heap.  Regions "   \
          "that have been promoted in place may need to be evacuated at "   \
          "a later time in order to compact old-gen memory to enable "      \
          "future humongous allocations.")                                  \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahGenerationalHumongousReserve, 0, EXPERIMENTAL,   \
          "(Generational mode only) What percent of the heap should be "    \
          "reserved for humongous objects if possible.  Old-generation "    \
          "collections will endeavor to evacuate old-gen regions within "   \
          "this reserved area even if these regions do not contain high "   \
          "percentage of garbage.  Setting a larger value will cause "      \
          "more frequent old-gen collections.  A smaller value will "       \
          "increase the likelihood that humongous object allocations "      \
          "fail, resulting in stop-the-world full GCs.")                    \
          range(0,100)                                                      \
                                                                            \
  product(double, ShenandoahMinOldGenGrowthPercent, 50, EXPERIMENTAL,       \
          "(Generational mode only) If the usage within old generation "    \
          "has grown by at least this percent of its live memory size "     \
          "at the start of the previous old-generation marking effort, "    \
          "heuristics may trigger the start of a new old-gen collection.")  \
          range(0.0,100.0)                                                  \
                                                                            \
  product(double, ShenandoahMinOldGenGrowthRemainingHeapPercent,            \
          35, EXPERIMENTAL,                                                 \
          "(Generational mode only) If the usage within old generation "    \
          "has grown to exceed this percent of the remaining heap that "    \
          "was not marked live within the old generation at the time "      \
          "of the last old-generation marking effort, heuristics may "      \
          "trigger the start of a new old-gen collection.  Setting "        \
          "this value to a smaller value may cause back-to-back old "       \
          "generation marking triggers, since the typical memory used "     \
          "by the old generation is about 30% larger than the live "        \
          "memory contained within the old generation (because default "    \
          "value of ShenandoahOldGarbageThreshold is 25.")                  \
          range(0.0,100.0)                                                  \
                                                                            \
  product(uintx, ShenandoahIgnoreOldGrowthBelowPercentage,                  \
          40, EXPERIMENTAL,                                                 \
          "(Generational mode only) If the total usage of the old "         \
          "generation is smaller than this percent, we do not trigger "     \
          "old gen collections even if old has grown, except when "         \
          "ShenandoahGenerationalDoNotIgnoreGrowthAfterYoungCycles "        \
          "consecutive cycles have been completed following the "           \
          "preceding old-gen collection.")                                  \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahDoNotIgnoreGrowthAfterYoungCycles,               \
          100, EXPERIMENTAL,                                                \
          "(Generational mode only) Trigger an old-generation mark "        \
          "if old has grown and this many consecutive young-gen "           \
          "collections have been completed following the preceding "        \
          "old-gen collection.  We perform this old-generation mark "       \
          "evvort even if the usage of old generation is below "            \
          "ShenandoahIgnoreOldGrowthBelowPercentage.")                      \
                                                                            \
  product(bool, ShenandoahGenerationalAdaptiveTenuring, true, EXPERIMENTAL, \
          "(Generational mode only) Dynamically adapt tenuring age.")       \
                                                                            \
  product(bool, ShenandoahGenerationalCensusIgnoreOlderCohorts, true,       \
                                                               EXPERIMENTAL,\
          "(Generational mode only) Ignore mortality rates older than the " \
          "oldest cohort under the tenuring age for the last cycle." )      \
                                                                            \
  product(uintx, ShenandoahGenerationalMinTenuringAge, 1, EXPERIMENTAL,     \
          "(Generational mode only) Floor for adaptive tenuring age. "      \
          "Setting floor and ceiling to the same value fixes the tenuring " \
          "age; setting both to 1 simulates a poor approximation to "       \
          "AlwaysTenure, and setting both to 16 simulates NeverTenure.")    \
          range(1,16)                                                       \
                                                                            \
  product(uintx, ShenandoahGenerationalMaxTenuringAge, 15, EXPERIMENTAL,    \
          "(Generational mode only) Ceiling for adaptive tenuring age. "    \
          "Setting floor and ceiling to the same value fixes the tenuring " \
          "age; setting both to 1 simulates a poor approximation to "       \
          "AlwaysTenure, and setting both to 16 simulates NeverTenure.")    \
          range(1,16)                                                       \
                                                                            \
  product(double, ShenandoahGenerationalTenuringMortalityRateThreshold,     \
                                                         0.1, EXPERIMENTAL, \
          "(Generational mode only) Cohort mortality rates below this "     \
          "value will be treated as indicative of longevity, leading to "   \
          "tenuring. A lower value delays tenuring, a higher value hastens "\
          "it. Used only when ShenandoahGenerationalhenAdaptiveTenuring is "\
          "enabled.")                                                       \
          range(0.001,0.999)                                                \
                                                                            \
  product(size_t, ShenandoahGenerationalTenuringCohortPopulationThreshold,  \
                                                         4*K, EXPERIMENTAL, \
          "(Generational mode only) Cohorts whose population is lower than "\
          "this value in the previous census are ignored wrt tenuring "     \
          "decisions. Effectively this makes then tenurable as soon as all "\
          "older cohorts are. Set this value to the largest cohort "        \
          "population volume that you are comfortable ignoring when making "\
          "tenuring decisions.")                                            \
                                                                            \
  product(size_t, ShenandoahRegionSize, 0, EXPERIMENTAL,                    \
          "Static heap region size. Set zero to enable automatic sizing.")  \
                                                                            \
  product(size_t, ShenandoahTargetNumRegions, 2048, EXPERIMENTAL,           \
          "With automatic region sizing, this is the approximate number "   \
          "of regions that would be used, within min/max region size "      \
          "limits.")                                                        \
                                                                            \
  product(size_t, ShenandoahMinRegionSize, 256 * K, EXPERIMENTAL,           \
          "With automatic region sizing, the regions would be at least "    \
          "this large.")                                                    \
                                                                            \
  product(size_t, ShenandoahMaxRegionSize, 32 * M, EXPERIMENTAL,            \
          "With automatic region sizing, the regions would be at most "     \
          "this large.")                                                    \
                                                                            \
  product(ccstr, ShenandoahGCMode, "satb",                                  \
          "GC mode to use.  Among other things, this defines which "        \
          "barriers are in in use. Possible values are:"                    \
          " satb - snapshot-at-the-beginning concurrent GC (three pass mark-evac-update);"  \
          " passive - stop the world GC only (either degenerated or full);" \
          " generational - generational concurrent GC")                     \
                                                                            \
  product(ccstr, ShenandoahGCHeuristics, "adaptive",                        \
          "GC heuristics to use. This fine-tunes the GC mode selected, "    \
          "by choosing when to start the GC, how much to process on each "  \
          "cycle, and what other features to automatically enable. "        \
          "When -XX:ShenandoahGCMode is generational, the only supported "  \
          "option is the default, adaptive. Possible values are:"           \
          " adaptive - adapt to maintain the given amount of free heap "    \
          "at all times, even during the GC cycle;"                         \
          " static - trigger GC when free heap falls below a specified "    \
          "threshold;"                                                      \
          " aggressive - run GC continuously, try to evacuate everything;"  \
          " compact - run GC more frequently and with deeper targets to "   \
          "free up more memory.")                                           \
                                                                            \
  product(uintx, ShenandoahExpeditePromotionsThreshold, 5, EXPERIMENTAL,    \
          "When Shenandoah expects to promote at least this percentage "    \
          "of the young generation, trigger a young collection to "         \
          "expedite these promotions.")                                     \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahExpediteMixedThreshold, 10, EXPERIMENTAL,        \
          "When there are this many old regions waiting to be collected, "  \
          "trigger a mixed collection immediately.")                        \
                                                                            \
  product(uintx, ShenandoahGarbageThreshold, 25, EXPERIMENTAL,              \
          "How much garbage a region has to contain before it would be "    \
          "taken for collection. This a guideline only, as GC heuristics "  \
          "may select the region for collection even if it has little "     \
          "garbage. This also affects how much internal fragmentation the " \
          "collector accepts. In percents of heap region size.")            \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahOldGarbageThreshold, 25, EXPERIMENTAL,           \
          "How much garbage an old region has to contain before it would "  \
          "be taken for collection.")                                       \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahIgnoreGarbageThreshold, 5, EXPERIMENTAL,         \
          "When less than this amount of garbage (as a percentage of "      \
          "region size) exists within a region, the region will not be "    \
          "added to the collection set, even when the heuristic has "       \
          "chosen to aggressively add regions with less than "              \
          "ShenandoahGarbageThreshold amount of garbage into the "          \
          "collection set.")                                                \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahInitFreeThreshold, 70, EXPERIMENTAL,             \
          "When less than this amount of memory is free within the "        \
          "heap or generation, trigger a learning cycle if we are "         \
          "in learning mode.  Learning mode happens during initialization " \
          "and following a drastic state change, such as following a "      \
          "degenerated or Full GC cycle.  In percents of soft max "         \
          "heap size.")                                                     \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahMinFreeThreshold, 10, EXPERIMENTAL,              \
          "Percentage of free heap memory (or young generation, in "        \
          "generational mode) below which most heuristics trigger "         \
          "collection independent of other triggers. Provides a safety "    \
          "margin for many heuristics. In percents of (soft) max heap "     \
          "size.")                                                          \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahAllocationThreshold, 0, EXPERIMENTAL,            \
          "How many new allocations should happen since the last GC cycle " \
          "before some heuristics trigger the collection. In percents of "  \
          "(soft) max heap size. Set to zero to effectively disable.")      \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahAllocSpikeFactor, 5, EXPERIMENTAL,               \
          "How much of heap should some heuristics reserve for absorbing "  \
          "the allocation spikes. Larger value wastes more memory in "      \
          "non-emergency cases, but provides more safety in emergency "     \
          "cases. In percents of (soft) max heap size.")                    \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahLearningSteps, 5, EXPERIMENTAL,                  \
          "The number of cycles some heuristics take to collect in order "  \
          "to learn application and GC performance.")                       \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahImmediateThreshold, 70, EXPERIMENTAL,            \
          "The cycle may shortcut when enough garbage can be reclaimed "    \
          "from the immediate garbage (completely garbage regions). "       \
          "In percents of total garbage found. Setting this threshold "     \
          "to 100 effectively disables the shortcut.")                      \
          range(0,100)                                                      \
                                                                            \
  product(uintx, ShenandoahAdaptiveSampleFrequencyHz, 10, EXPERIMENTAL,     \
          "The number of times per second to update the allocation rate "   \
          "moving average.")                                                \
                                                                            \
  product(uintx, ShenandoahAdaptiveSampleSizeSeconds, 10, EXPERIMENTAL,     \
          "The size of the moving window over which the average "           \
          "allocation rate is maintained. The total number of samples "     \
          "is the product of this number and the sample frequency.")        \
                                                                            \
  product(double, ShenandoahAdaptiveInitialConfidence, 1.8, EXPERIMENTAL,   \
          "The number of standard deviations used to determine an initial " \
          "margin of error for the average cycle time and average "         \
          "allocation rate. Increasing this value will cause the "          \
          "heuristic to initiate more concurrent cycles." )                 \
                                                                            \
  product(double, ShenandoahAdaptiveInitialSpikeThreshold, 1.8, EXPERIMENTAL, \
          "If the most recently sampled allocation rate is more than "      \
          "this many standard deviations away from the moving average, "    \
          "then a cycle is initiated. This value controls how sensitive "   \
          "the heuristic is to allocation spikes. Decreasing this number "  \
          "increases the sensitivity. ")                                    \
                                                                            \
  product(double, ShenandoahAdaptiveDecayFactor, 0.5, EXPERIMENTAL,         \
          "The decay factor (alpha) used for values in the weighted "       \
          "moving average of cycle time and allocation rate. "              \
          "Larger values give more weight to recent values.")               \
          range(0,1.0)                                                      \
                                                                            \
  product(uintx, ShenandoahGuaranteedGCInterval, 5*60*1000, EXPERIMENTAL,   \
          "Many heuristics would guarantee a concurrent GC cycle at "       \
          "least with this interval. This is useful when large idle "       \
          "intervals are present, where GC can run without stealing "       \
          "time from active application. Time is in milliseconds. "         \
          "Setting this to 0 disables the feature.")                        \
                                                                            \
  product(uintx, ShenandoahGuaranteedOldGCInterval, 10*60*1000, EXPERIMENTAL, \
          "Run a collection of the old generation at least this often. "    \
          "Heuristics may trigger collections more frequently. Time is in " \
          "milliseconds. Setting this to 0 disables the feature.")          \
                                                                            \
  product(uintx, ShenandoahGuaranteedYoungGCInterval, 5*60*1000,  EXPERIMENTAL,  \
          "Run a collection of the young generation at least this often. "  \
          "Heuristics may trigger collections more frequently. Time is in " \
          "milliseconds. Setting this to 0 disables the feature.")          \
                                                                            \
  product(bool, ShenandoahAlwaysClearSoftRefs, false, EXPERIMENTAL,         \
          "Unconditionally clear soft references, instead of using any "    \
          "other cleanup policy. This minimizes footprint at expense of"    \
          "more soft reference churn in applications.")                     \
                                                                            \
  product(bool, ShenandoahUncommit, true, EXPERIMENTAL,                     \
          "Allow to uncommit memory under unused regions and metadata. "    \
          "This optimizes footprint at expense of allocation latency in "   \
          "regions that require committing back. Uncommits would be "       \
          "disabled by some heuristics, or with static heap size.")         \
                                                                            \
  product(uintx, ShenandoahUncommitDelay, 5*60*1000, EXPERIMENTAL,          \
          "Uncommit memory for regions that were not used for more than "   \
          "this time. First use after that would incur allocation stalls. " \
          "Actively used regions would never be uncommitted, because they " \
          "do not become unused longer than this delay. Time is in "        \
          "milliseconds. Setting this delay to 0 effectively uncommits "    \
          "regions almost immediately after they become unused.")           \
                                                                            \
  product(bool, ShenandoahRegionSampling, false, EXPERIMENTAL,              \
          "Provide heap region sampling data via jvmstat.")                 \
                                                                            \
  product(int, ShenandoahRegionSamplingRate, 40, EXPERIMENTAL,              \
          "Sampling rate for heap region sampling. In milliseconds between "\
          "the samples. Higher values provide more fidelity, at expense "   \
          "of more sampling overhead.")                                     \
                                                                            \
  product(uintx, ShenandoahControlIntervalMin, 1, EXPERIMENTAL,             \
          "The minimum sleep interval for the control loop that drives "    \
          "the cycles. Lower values would increase GC responsiveness "      \
          "to changing heap conditions, at the expense of higher perf "     \
          "overhead. Time is in milliseconds.")                             \
          range(1, 999)                                                     \
                                                                            \
  product(uintx, ShenandoahControlIntervalMax, 10, EXPERIMENTAL,            \
          "The maximum sleep interval for control loop that drives "        \
          "the cycles. Lower values would increase GC responsiveness "      \
          "to changing heap conditions, at the expense of higher perf "     \
          "overhead. Time is in milliseconds.")                             \
          range(1, 999)                                                     \
                                                                            \
  product(uintx, ShenandoahControlIntervalAdjustPeriod, 1000, EXPERIMENTAL, \
          "The time period for one step in control loop interval "          \
          "adjustment. Lower values make adjustments faster, at the "       \
          "expense of higher perf overhead. Time is in milliseconds.")      \
                                                                            \
  product(bool, ShenandoahVerify, false, DIAGNOSTIC,                        \
          "Enable internal verification. This would catch many GC bugs, "   \
          "but it would also stall the collector during the verification, " \
          "which prolongs the pauses and might hide other bugs.")           \
                                                                            \
  product(intx, ShenandoahVerifyLevel, 4, DIAGNOSTIC,                       \
          "Verification level, higher levels check more, taking more time. "\
          "Accepted values are:"                                            \
          " 0 = basic heap checks; "                                        \
          " 1 = previous level, plus basic region checks; "                 \
          " 2 = previous level, plus all roots; "                           \
          " 3 = previous level, plus all reachable objects; "               \
          " 4 = previous level, plus all marked objects")                   \
                                                                            \
  product(uintx, ShenandoahEvacReserve, 5, EXPERIMENTAL,                    \
          "How much of (young-generation) heap to reserve for "             \
          "(young-generation) evacuations.  Larger values allow GC to "     \
          "evacuate more live objects on every cycle, while leaving "       \
          "less headroom for application to allocate while GC is "          \
          "evacuating and updating references. This parameter is "          \
          "consulted at the end of marking, before selecting the "          \
          "collection set.  If available memory at this time is smaller "   \
          "than the indicated reserve, the bound on collection set size is "\
          "adjusted downward.  The size of a generational mixed "           \
          "evacuation collection set (comprised of both young and old "     \
          "regions) is also bounded by this parameter.  In percents of "    \
          "total (young-generation) heap size.")                            \
          range(1,100)                                                      \
                                                                            \
  product(double, ShenandoahEvacWaste, 1.2, EXPERIMENTAL,                   \
          "How much waste evacuations produce within the reserved space. "  \
          "Larger values make evacuations more resilient against "          \
          "evacuation conflicts, at expense of evacuating less on each "    \
          "GC cycle.  Smaller values increase the risk of evacuation "      \
          "failures, which will trigger stop-the-world Full GC passes.")    \
          range(1.0,100.0)                                                  \
                                                                            \
  product(double, ShenandoahOldEvacWaste, 1.4, EXPERIMENTAL,                \
          "How much waste evacuations produce within the reserved space. "  \
          "Larger values make evacuations more resilient against "          \
          "evacuation conflicts, at expense of evacuating less on each "    \
          "GC cycle.  Smaller values increase the risk of evacuation "      \
          "failures, which will trigger stop-the-world Full GC passes.")    \
          range(1.0,100.0)                                                  \
                                                                            \
  product(double, ShenandoahPromoEvacWaste, 1.2, EXPERIMENTAL,              \
          "How much waste promotions produce within the reserved space. "   \
          "Larger values make evacuations more resilient against "          \
          "evacuation conflicts, at expense of promoting less on each "     \
          "GC cycle.  Smaller values increase the risk of evacuation "      \
          "failures, which will trigger stop-the-world Full GC passes.")    \
          range(1.0,100.0)                                                  \
                                                                            \
  product(bool, ShenandoahEvacReserveOverflow, true, EXPERIMENTAL,          \
          "Allow evacuations to overflow the reserved space. Enabling it "  \
          "will make evacuations more resilient when evacuation "           \
          "reserve/waste is incorrect, at the risk that application "       \
          "runs out of memory too early.")                                  \
                                                                            \
  product(uintx, ShenandoahOldEvacPercent, 75, EXPERIMENTAL,                \
          "The maximum evacuation to old-gen expressed as a percent of "    \
          "the total live memory within the collection set.  With the "     \
          "default setting, if collection set evacuates X, no more than "   \
          "75% of X may hold objects evacuated from old or promoted to "    \
          "old from young.  A value of 100 allows the entire collection "   \
          "set to be comprised of old-gen regions and young regions that "  \
          "have reached the tenure age.  Larger values allow fewer mixed "  \
          "evacuations to reclaim all the garbage from old.  Smaller "      \
          "values result in less variation in GC cycle times between "      \
          "young vs. mixed cycles.  A value of 0 prevents mixed "           \
          "evacations from running and blocks promotion of aged regions "   \
          "by evacuation.  Setting the value to 0 does not prevent "        \
          "regions from being promoted in place.")                          \
          range(0,100)                                                      \
                                                                            \
  product(bool, ShenandoahEvacTracking, false, DIAGNOSTIC,                  \
          "Collect additional metrics about evacuations. Enabling this "    \
          "tracks how many objects and how many bytes were evacuated, and " \
          "how many were abandoned. The information will be categorized "   \
          "by thread type (worker or mutator) and evacuation type (young, " \
          "old, or promotion.")                                             \
                                                                            \
  product(uintx, ShenandoahCriticalFreeThreshold, 1, EXPERIMENTAL,          \
          "How much of the heap needs to be free after recovery cycles, "   \
          "either Degenerated or Full GC to be claimed successful. If this "\
          "much space is not available, next recovery step would be "       \
          "triggered.")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(bool, ShenandoahDegeneratedGC, true, DIAGNOSTIC,                  \
          "Enable Degenerated GC as the graceful degradation step. "        \
          "Disabling this option leads to degradation to Full GC instead. " \
          "When running in passive mode, this can be toggled to measure "   \
          "either Degenerated GC or Full GC costs.")                        \
                                                                            \
  product(uintx, ShenandoahFullGCThreshold, 3, EXPERIMENTAL,                \
          "How many back-to-back Degenerated GCs should happen before "     \
          "going to a Full GC.")                                            \
                                                                            \
  product(uintx, ShenandoahNoProgressThreshold, 5, EXPERIMENTAL,            \
          "After this number of consecutive Full GCs fail to make "         \
          "progress, Shenandoah will raise out of memory errors. Note "     \
          "that progress is determined by ShenandoahCriticalFreeThreshold") \
                                                                            \
  product(bool, ShenandoahImplicitGCInvokesConcurrent, false, EXPERIMENTAL, \
          "Should internally-caused GC requests invoke concurrent cycles, " \
          "should they do the stop-the-world (Degenerated / Full GC)? "     \
          "Many heuristics automatically enable this. This option is "      \
          "similar to global ExplicitGCInvokesConcurrent.")                 \
                                                                            \
  product(bool, ShenandoahHumongousMoves, true, DIAGNOSTIC,                 \
          "Allow moving humongous regions. This makes GC more resistant "   \
          "to external fragmentation that may otherwise fail other "        \
          "humongous allocations, at the expense of higher GC copying "     \
          "costs. Currently affects stop-the-world (Full) cycle only.")     \
                                                                            \
  product(bool, ShenandoahOOMDuringEvacALot, false, DIAGNOSTIC,             \
          "Testing: simulate OOM during evacuation.")                       \
                                                                            \
  product(bool, ShenandoahAllocFailureALot, false, DIAGNOSTIC,              \
          "Testing: make lots of artificial allocation failures.")          \
                                                                            \
  product(uintx, ShenandoahCoalesceChance, 0, DIAGNOSTIC,                   \
          "Testing: Abandon remaining mixed collections with this "         \
          "likelihood. Following each mixed collection, abandon all "       \
          "remaining mixed collection candidate regions with likelihood "   \
          "ShenandoahCoalesceChance. Abandoning a mixed collection will "   \
          "cause the old regions to be made parsable, rather than being "   \
          "evacuated.")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(intx, ShenandoahMarkScanPrefetch, 32, EXPERIMENTAL,               \
          "How many objects to prefetch ahead when traversing mark bitmaps."\
          "Set to 0 to disable prefetching.")                               \
          range(0, 256)                                                     \
                                                                            \
  product(uintx, ShenandoahMarkLoopStride, 1000, EXPERIMENTAL,              \
          "How many items to process during one marking iteration before "  \
          "checking for cancellation, yielding, etc. Larger values improve "\
          "marking performance at expense of responsiveness.")              \
                                                                            \
  product(uintx, ShenandoahParallelRegionStride, 0, EXPERIMENTAL,           \
          "How many regions to process at once during parallel region "     \
          "iteration. Affects heaps with lots of regions. "                 \
          "Set to 0 to let Shenandoah to decide the best value.")           \
                                                                            \
  product(size_t, ShenandoahSATBBufferSize, 1 * K, EXPERIMENTAL,            \
          "Number of entries in an SATB log buffer.")                       \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, ShenandoahMaxSATBBufferFlushes, 5, EXPERIMENTAL,           \
          "How many times to maximum attempt to flush SATB buffers at the " \
          "end of concurrent marking.")                                     \
                                                                            \
  product(bool, ShenandoahSATBBarrier, true, DIAGNOSTIC,                    \
          "Turn on/off SATB barriers in Shenandoah")                        \
                                                                            \
  product(bool, ShenandoahCardBarrier, false, DIAGNOSTIC,                   \
          "Turn on/off card-marking post-write barrier in Shenandoah: "     \
          " true when ShenandoahGCMode is generational, false otherwise")   \
                                                                            \
  product(bool, ShenandoahCASBarrier, true, DIAGNOSTIC,                     \
          "Turn on/off CAS barriers in Shenandoah")                         \
                                                                            \
  product(bool, ShenandoahCloneBarrier, true, DIAGNOSTIC,                   \
          "Turn on/off clone barriers in Shenandoah")                       \
                                                                            \
  product(bool, ShenandoahLoadRefBarrier, true, DIAGNOSTIC,                 \
          "Turn on/off load-reference barriers in Shenandoah")              \
                                                                            \
  product(bool, ShenandoahStackWatermarkBarrier, true, DIAGNOSTIC,          \
          "Turn on/off stack watermark barriers in Shenandoah")             \
                                                                            \
  develop(bool, ShenandoahVerifyOptoBarriers, trueInDebug,                  \
          "Verify no missing barriers in C2.")                              \
                                                                            \
  product(uintx, ShenandoahOldCompactionReserve, 8, EXPERIMENTAL,           \
          "During generational GC, prevent promotions from filling "        \
          "this number of heap regions.  These regions are reserved "       \
          "for the purpose of supporting compaction of old-gen "            \
          "memory.  Otherwise, old-gen memory cannot be compacted.")        \
          range(0, 128)                                                     \
                                                                            \
  product(bool, ShenandoahAllowOldMarkingPreemption, true, DIAGNOSTIC,      \
          "Allow young generation collections to suspend concurrent"        \
          " marking in the old generation.")                                \
                                                                            \
  product(uintx, ShenandoahAgingCyclePeriod, 1, EXPERIMENTAL,               \
          "With generational mode, increment the age of objects and"        \
          "regions each time this many young-gen GC cycles are completed.") \
                                                                            \
  develop(bool, ShenandoahEnableCardStats, false,                           \
          "Enable statistics collection related to clean & dirty cards")    \
                                                                            \
  develop(int, ShenandoahCardStatsLogInterval, 50,                          \
          "Log cumulative card stats every so many remembered set or "      \
          "update refs scans")                                              \
                                                                            \
  product(uintx, ShenandoahMinimumOldTimeMs, 100, EXPERIMENTAL,             \
         "Minimum amount of time in milliseconds to run old collections "   \
         "before a young collection is allowed to run. This is intended "   \
         "to prevent starvation of the old collector. Setting this to "     \
         "0 will allow back to back young collections to run during old "   \
         "collections.")                                                    \
  // end of GC_SHENANDOAH_FLAGS

#endif // SHARE_GC_SHENANDOAH_SHENANDOAH_GLOBALS_HPP
