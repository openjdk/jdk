/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2020, Red Hat, Inc. All rights reserved.
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
                            diagnostic,                                     \
                            diagnostic_pd,                                  \
                            experimental,                                   \
                            notproduct,                                     \
                            manageable,                                     \
                            product_rw,                                     \
                            lp64_product,                                   \
                            range,                                          \
                            constraint)                                     \
                                                                            \
  experimental(size_t, ShenandoahRegionSize, 0,                             \
          "Static heap region size. Set zero to enable automatic sizing.")  \
                                                                            \
  experimental(size_t, ShenandoahTargetNumRegions, 2048,                    \
          "With automatic region sizing, this is the approximate number "   \
          "of regions that would be used, within min/max region size "      \
          "limits.")                                                        \
                                                                            \
  experimental(size_t, ShenandoahMinRegionSize, 256 * K,                    \
          "With automatic region sizing, the regions would be at least "    \
          "this large.")                                                    \
                                                                            \
  experimental(size_t, ShenandoahMaxRegionSize, 32 * M,                     \
          "With automatic region sizing, the regions would be at most "     \
          "this large.")                                                    \
                                                                            \
  experimental(intx, ShenandoahHumongousThreshold, 100,                     \
          "Humongous objects are allocated in separate regions. "           \
          "This setting defines how large the object should be to be "      \
          "deemed humongous. Value is in  percents of heap region size. "   \
          "This also caps the maximum TLAB size.")                          \
          range(1, 100)                                                     \
                                                                            \
  product(ccstr, ShenandoahGCMode, "satb",                                  \
          "GC mode to use.  Among other things, this defines which "        \
          "barriers are in in use. Possible values are:"                    \
          " satb - snapshot-at-the-beginning concurrent GC (three pass mark-evac-update);"  \
          " iu - incremental-update concurrent GC (three pass mark-evac-update);"  \
          " passive - stop the world GC only (either degenerated or full)") \
                                                                            \
  product(ccstr, ShenandoahGCHeuristics, "adaptive",                        \
          "GC heuristics to use. This fine-tunes the GC mode selected, "    \
          "by choosing when to start the GC, how much to process on each "  \
          "cycle, and what other features to automatically enable. "        \
          "Possible values are:"                                            \
          " adaptive - adapt to maintain the given amount of free heap "    \
          "at all times, even during the GC cycle;"                         \
          " static -  trigger GC when free heap falls below the threshold;" \
          " aggressive - run GC continuously, try to evacuate everything;"  \
          " compact - run GC more frequently and with deeper targets to "   \
          "free up more memory.")                                           \
                                                                            \
  experimental(uintx, ShenandoahRefProcFrequency, 5,                        \
          "Process process weak (soft, phantom, finalizers) references "    \
          "every Nth cycle. Normally affects concurrent GC cycles only, "   \
          "as degenerated and full GCs would try to process references "    \
          "regardless. Set to zero to disable reference processing "        \
          "completely.")                                                    \
                                                                            \
  experimental(uintx, ShenandoahUnloadClassesFrequency, 1,                  \
          "Unload the classes every Nth cycle. Normally affects concurrent "\
          "GC cycles, as degenerated and full GCs would try to unload "     \
          "classes regardless. Set to zero to disable class unloading.")    \
                                                                            \
  experimental(uintx, ShenandoahGarbageThreshold, 25,                       \
          "How much garbage a region has to contain before it would be "    \
          "taken for collection. This a guideline only, as GC heuristics "  \
          "may select the region for collection even if it has little "     \
          "garbage. This also affects how much internal fragmentation the " \
          "collector accepts. In percents of heap region size.")            \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahInitFreeThreshold, 70,                      \
          "How much heap should be free before some heuristics trigger the "\
          "initial (learning) cycles. Affects cycle frequency on startup "  \
          "and after drastic state changes, e.g. after degenerated/full "   \
          "GC cycles. In percents of (soft) max heap size.")                \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahMinFreeThreshold, 10,                       \
          "How much heap should be free before most heuristics trigger the "\
          "collection, even without other triggers. Provides the safety "   \
          "margin for many heuristics. In percents of (soft) max heap size.")\
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahAllocationThreshold, 0,                     \
          "How many new allocations should happen since the last GC cycle " \
          "before some heuristics trigger the collection. In percents of "  \
          "(soft) max heap size. Set to zero to effectively disable.")      \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahAllocSpikeFactor, 5,                        \
          "How much of heap should some heuristics reserve for absorbing "  \
          "the allocation spikes. Larger value wastes more memory in "      \
          "non-emergency cases, but provides more safety in emergency "     \
          "cases. In percents of (soft) max heap size.")                    \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahLearningSteps, 5,                           \
          "The number of cycles some heuristics take to collect in order "  \
          "to learn application and GC performance.")                       \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahImmediateThreshold, 90,                     \
          "The cycle may shortcut when enough garbage can be reclaimed "    \
          "from the immediate garbage (completely garbage regions). "       \
          "In percents of total garbage found. Setting this threshold "     \
          "to 100 effectively disables the shortcut.")                      \
          range(0,100)                                                      \
                                                                            \
  experimental(uintx, ShenandoahGuaranteedGCInterval, 5*60*1000,            \
          "Many heuristics would guarantee a concurrent GC cycle at "       \
          "least with this interval. This is useful when large idle "       \
          "intervals are present, where GC can run without stealing "       \
          "time from active application. Time is in milliseconds. "         \
          "Setting this to 0 disables the feature.")                        \
                                                                            \
  experimental(bool, ShenandoahAlwaysClearSoftRefs, false,                  \
          "Unconditionally clear soft references, instead of using any "    \
          "other cleanup policy. This minimizes footprint at expense of"    \
          "more soft reference churn in applications.")                     \
                                                                            \
  experimental(bool, ShenandoahUncommit, true,                              \
          "Allow to uncommit memory under unused regions and metadata. "    \
          "This optimizes footprint at expense of allocation latency in "   \
          "regions that require committing back. Uncommits would be "       \
          "disabled by some heuristics, or with static heap size.")         \
                                                                            \
  experimental(uintx, ShenandoahUncommitDelay, 5*60*1000,                   \
          "Uncommit memory for regions that were not used for more than "   \
          "this time. First use after that would incur allocation stalls. " \
          "Actively used regions would never be uncommitted, because they " \
          "do not become unused longer than this delay. Time is in "        \
          "milliseconds. Setting this delay to 0 effectively uncommits "    \
          "regions almost immediately after they become unused.")           \
                                                                            \
  experimental(bool, ShenandoahRegionSampling, false,                       \
          "Provide heap region sampling data via jvmstat.")                 \
                                                                            \
  experimental(int, ShenandoahRegionSamplingRate, 40,                       \
          "Sampling rate for heap region sampling. In milliseconds between "\
          "the samples. Higher values provide more fidelity, at expense "   \
          "of more sampling overhead.")                                     \
                                                                            \
  experimental(uintx, ShenandoahControlIntervalMin, 1,                      \
          "The minimum sleep interval for the control loop that drives "    \
          "the cycles. Lower values would increase GC responsiveness "      \
          "to changing heap conditions, at the expense of higher perf "     \
          "overhead. Time is in milliseconds.")                             \
                                                                            \
  experimental(uintx, ShenandoahControlIntervalMax, 10,                     \
          "The maximum sleep interval for control loop that drives "        \
          "the cycles. Lower values would increase GC responsiveness "      \
          "to changing heap conditions, at the expense of higher perf "     \
          "overhead. Time is in milliseconds.")                             \
                                                                            \
  experimental(uintx, ShenandoahControlIntervalAdjustPeriod, 1000,          \
          "The time period for one step in control loop interval "          \
          "adjustment. Lower values make adjustments faster, at the "       \
          "expense of higher perf overhead. Time is in milliseconds.")      \
                                                                            \
  diagnostic(bool, ShenandoahVerify, false,                                 \
          "Enable internal verification. This would catch many GC bugs, "   \
          "but it would also stall the collector during the verification, " \
          "which prolongs the pauses and might hide other bugs.")           \
                                                                            \
  diagnostic(intx, ShenandoahVerifyLevel, 4,                                \
          "Verification level, higher levels check more, taking more time. "\
          "Accepted values are:"                                            \
          " 0 = basic heap checks; "                                        \
          " 1 = previous level, plus basic region checks; "                 \
          " 2 = previous level, plus all roots; "                           \
          " 3 = previous level, plus all reachable objects; "               \
          " 4 = previous level, plus all marked objects")                   \
                                                                            \
  diagnostic(bool, ShenandoahElasticTLAB, true,                             \
          "Use Elastic TLABs with Shenandoah")                              \
                                                                            \
  experimental(uintx, ShenandoahEvacReserve, 5,                             \
          "How much of heap to reserve for evacuations. Larger values make "\
          "GC evacuate more live objects on every cycle, while leaving "    \
          "less headroom for application to allocate in. In percents of "   \
          "total heap size.")                                               \
          range(1,100)                                                      \
                                                                            \
  experimental(double, ShenandoahEvacWaste, 1.2,                            \
          "How much waste evacuations produce within the reserved space. "  \
          "Larger values make evacuations more resilient against "          \
          "evacuation conflicts, at expense of evacuating less on each "    \
          "GC cycle.")                                                      \
          range(1.0,100.0)                                                  \
                                                                            \
  experimental(bool, ShenandoahEvacReserveOverflow, true,                   \
          "Allow evacuations to overflow the reserved space. Enabling it "  \
          "will make evacuations more resilient when evacuation "           \
          "reserve/waste is incorrect, at the risk that application "       \
          "runs out of memory too early.")                                  \
                                                                            \
  experimental(bool, ShenandoahPacing, true,                                \
          "Pace application allocations to give GC chance to start "        \
          "and complete before allocation failure is reached.")             \
                                                                            \
  experimental(uintx, ShenandoahPacingMaxDelay, 10,                         \
          "Max delay for pacing application allocations. Larger values "    \
          "provide more resilience against out of memory, at expense at "   \
          "hiding the GC latencies in the allocation path. Time is in "     \
          "milliseconds. Setting it to arbitrarily large value makes "      \
          "GC effectively stall the threads indefinitely instead of going " \
          "to degenerated or Full GC.")                                     \
                                                                            \
  experimental(uintx, ShenandoahPacingIdleSlack, 2,                         \
          "How much of heap counted as non-taxable allocations during idle "\
          "phases. Larger value makes the pacing milder when collector is " \
          "idle, requiring less rendezvous with control thread. Lower "     \
          "value makes the pacing control less responsive to out-of-cycle " \
          "allocs. In percent of total heap size.")                         \
          range(0, 100)                                                     \
                                                                            \
  experimental(uintx, ShenandoahPacingCycleSlack, 10,                       \
          "How much of free space to take as non-taxable allocations "      \
          "the GC cycle. Larger value makes the pacing milder at the "      \
          "beginning of the GC cycle. Lower value makes the pacing less "   \
          "uniform during the cycle. In percent of free space.")            \
          range(0, 100)                                                     \
                                                                            \
  experimental(double, ShenandoahPacingSurcharge, 1.1,                      \
          "Additional pacing tax surcharge to help unclutter the heap. "    \
          "Larger values makes the pacing more aggressive. Lower values "   \
          "risk GC cycles finish with less memory than were available at "  \
          "the beginning of it.")                                           \
          range(1.0, 100.0)                                                 \
                                                                            \
  experimental(uintx, ShenandoahCriticalFreeThreshold, 1,                   \
          "How much of the heap needs to be free after recovery cycles, "   \
          "either Degenerated or Full GC to be claimed successful. If this "\
          "much space is not available, next recovery step would be "       \
          "triggered.")                                                     \
          range(0, 100)                                                     \
                                                                            \
  diagnostic(bool, ShenandoahDegeneratedGC, true,                           \
          "Enable Degenerated GC as the graceful degradation step. "        \
          "Disabling this option leads to degradation to Full GC instead. " \
          "When running in passive mode, this can be toggled to measure "   \
          "either Degenerated GC or Full GC costs.")                        \
                                                                            \
  experimental(uintx, ShenandoahFullGCThreshold, 3,                         \
          "How many back-to-back Degenerated GCs should happen before "     \
          "going to a Full GC.")                                            \
                                                                            \
  experimental(bool, ShenandoahImplicitGCInvokesConcurrent, false,          \
          "Should internally-caused GC requests invoke concurrent cycles, " \
          "should they do the stop-the-world (Degenerated / Full GC)? "     \
          "Many heuristics automatically enable this. This option is "      \
          "similar to global ExplicitGCInvokesConcurrent.")                 \
                                                                            \
  diagnostic(bool, ShenandoahHumongousMoves, true,                          \
          "Allow moving humongous regions. This makes GC more resistant "   \
          "to external fragmentation that may otherwise fail other "        \
          "humongous allocations, at the expense of higher GC copying "     \
          "costs. Currently affects stop-the-world (Full) cycle only.")     \
                                                                            \
  diagnostic(bool, ShenandoahOOMDuringEvacALot, false,                      \
          "Testing: simulate OOM during evacuation.")                       \
                                                                            \
  diagnostic(bool, ShenandoahAllocFailureALot, false,                       \
          "Testing: make lots of artificial allocation failures.")          \
                                                                            \
  experimental(intx, ShenandoahMarkScanPrefetch, 32,                        \
          "How many objects to prefetch ahead when traversing mark bitmaps."\
          "Set to 0 to disable prefetching.")                               \
          range(0, 256)                                                     \
                                                                            \
  experimental(uintx, ShenandoahMarkLoopStride, 1000,                       \
          "How many items to process during one marking iteration before "  \
          "checking for cancellation, yielding, etc. Larger values improve "\
          "marking performance at expense of responsiveness.")              \
                                                                            \
  experimental(uintx, ShenandoahParallelRegionStride, 1024,                 \
          "How many regions to process at once during parallel region "     \
          "iteration. Affects heaps with lots of regions.")                 \
                                                                            \
  experimental(size_t, ShenandoahSATBBufferSize, 1 * K,                     \
          "Number of entries in an SATB log buffer.")                       \
          range(1, max_uintx)                                               \
                                                                            \
  experimental(uintx, ShenandoahSATBBufferFlushInterval, 100,               \
          "Forcefully flush non-empty SATB buffers at this interval. "      \
          "Time is in milliseconds.")                                       \
                                                                            \
  diagnostic(bool, ShenandoahPreclean, true,                                \
          "Do concurrent preclean phase before final mark: process "        \
          "definitely alive references to avoid dealing with them during "  \
          "pause.")                                                         \
                                                                            \
  experimental(bool, ShenandoahSuspendibleWorkers, false,                   \
          "Suspend concurrent GC worker threads at safepoints")             \
                                                                            \
  diagnostic(bool, ShenandoahSATBBarrier, true,                             \
          "Turn on/off SATB barriers in Shenandoah")                        \
                                                                            \
  diagnostic(bool, ShenandoahStoreValEnqueueBarrier, false,                 \
          "Turn on/off enqueuing of oops for storeval barriers")            \
                                                                            \
  diagnostic(bool, ShenandoahCASBarrier, true,                              \
          "Turn on/off CAS barriers in Shenandoah")                         \
                                                                            \
  diagnostic(bool, ShenandoahCloneBarrier, true,                            \
          "Turn on/off clone barriers in Shenandoah")                       \
                                                                            \
  diagnostic(bool, ShenandoahLoadRefBarrier, true,                          \
          "Turn on/off load-reference barriers in Shenandoah")              \
                                                                            \
  diagnostic(uintx, ShenandoahCodeRootsStyle, 2,                            \
          "Use this style to scan the code cache roots:"                    \
          " 0 - sequential iterator;"                                       \
          " 1 - parallel iterator;"                                         \
          " 2 - parallel iterator with cset filters;")                      \
                                                                            \
  develop(bool, ShenandoahVerifyOptoBarriers, false,                        \
          "Verify no missing barriers in C2.")                              \
                                                                            \
  diagnostic(bool, ShenandoahLoopOptsAfterExpansion, true,                  \
          "Attempt more loop opts after barrier expansion.")                \
                                                                            \
  diagnostic(bool, ShenandoahSelfFixing, true,                              \
          "Fix references with load reference barrier. Disabling this "     \
          "might degrade performance.")                                     \


#endif // SHARE_GC_SHENANDOAH_SHENANDOAH_GLOBALS_HPP
