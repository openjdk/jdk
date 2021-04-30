/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_Z_GLOBALS_HPP
#define SHARE_GC_Z_Z_GLOBALS_HPP

#include "zPageAge.hpp"

#define GC_Z_FLAGS(develop,                                                 \
                   develop_pd,                                              \
                   product,                                                 \
                   product_pd,                                              \
                   notproduct,                                              \
                   range,                                                   \
                   constraint)                                              \
                                                                            \
  product(double, ZAllocationSpikeTolerance, 2.0,                           \
          "Allocation spike tolerance factor")                              \
                                                                            \
  product(double, ZFragmentationLimit, 5.0,                                 \
          "Maximum allowed heap fragmentation")                             \
                                                                            \
  product(double, ZYoungCompactionLimit, 25.0,                              \
          "Maximum allowed garbage in young pages")                         \
                                                                            \
  product(size_t, ZMarkStackSpaceLimit, 8*G,                                \
          "Maximum number of bytes allocated for mark stacks")              \
          range(32*M, 1024*G)                                               \
                                                                            \
  product(double, ZCollectionInterval, -1,                                  \
          "Backwards compatible alias for ZCollectionIntervalMajor")        \
                                                                            \
  product(double, ZCollectionIntervalMinor, -1,                             \
          "Force Minor GC at a fixed time interval (in seconds)")           \
                                                                            \
  product(double, ZCollectionIntervalMajor, -1,                             \
          "Force GC at a fixed time interval (in seconds)")                 \
                                                                            \
  product(bool, ZCollectionIntervalOnly, false,                             \
          "Only use timers for GC heuristics")                              \
                                                                            \
  product(bool, ZProactive, true,                                           \
          "Enable proactive GC cycles")                                     \
                                                                            \
  product(bool, ZUncommit, true,                                            \
          "Uncommit unused memory")                                         \
                                                                            \
  product(bool, ZBufferStoreBarriers, true, DIAGNOSTIC,                     \
          "Buffer store barriers")                                          \
                                                                            \
  product(uint, ZYoungGCThreads, 0, DIAGNOSTIC,                             \
          "Number of GC threads for the young generation")                  \
                                                                            \
  product(uint, ZOldGCThreads, 0, DIAGNOSTIC,                               \
          "Number of GC threads for the old generation")                    \
                                                                            \
  product(uintx, ZUncommitDelay, 5 * 60,                                    \
          "Uncommit memory if it has been unused for the specified "        \
          "amount of time (in seconds)")                                    \
                                                                            \
  product(uintx, ZIndexDistributorStrategy, 0, DIAGNOSTIC,                  \
          "Strategy used to distribute indices to parallel workers "        \
          "0: Claim tree "                                                  \
          "1: Simple Striped ")                                             \
                                                                            \
  product(uint, ZStatisticsInterval, 10, DIAGNOSTIC,                        \
          "Time between statistics print outs (in seconds)")                \
          range(1, (uint)-1)                                                \
                                                                            \
  product(bool, ZStressRelocateInPlace, false, DIAGNOSTIC,                  \
          "Always relocate pages in-place")                                 \
                                                                            \
  product(bool, ZVerifyRemembered, trueInDebug, DIAGNOSTIC,                 \
          "Verify remembered sets")                                         \
                                                                            \
  product(bool, ZVerifyRoots, trueInDebug, DIAGNOSTIC,                      \
          "Verify roots")                                                   \
                                                                            \
  product(bool, ZVerifyObjects, false, DIAGNOSTIC,                          \
          "Verify objects")                                                 \
                                                                            \
  develop(bool, ZVerifyOops, false,                                         \
          "Verify accessed oops")                                           \
                                                                            \
  product(bool, ZVerifyMarking, trueInDebug, DIAGNOSTIC,                    \
          "Verify marking stacks")                                          \
                                                                            \
  product(bool, ZVerifyForwarding, false, DIAGNOSTIC,                       \
          "Verify forwarding tables")                                       \
                                                                            \
  product(int, ZTenuringThreshold, -1, DIAGNOSTIC,                          \
          "Young generation tenuring threshold, -1 for dynamic computation")\
          range(-1, static_cast<int>(ZPageAgeMax))

// end of GC_Z_FLAGS

#endif // SHARE_GC_Z_Z_GLOBALS_HPP
