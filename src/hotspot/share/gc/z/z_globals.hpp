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

#define GC_Z_FLAGS(develop,                                                 \
                   develop_pd,                                              \
                   product,                                                 \
                   product_pd,                                              \
                   diagnostic,                                              \
                   diagnostic_pd,                                           \
                   experimental,                                            \
                   notproduct,                                              \
                   manageable,                                              \
                   product_rw,                                              \
                   lp64_product,                                            \
                   range,                                                   \
                   constraint)                                              \
                                                                            \
  experimental(double, ZAllocationSpikeTolerance, 2.0,                      \
          "Allocation spike tolerance factor")                              \
                                                                            \
  experimental(double, ZFragmentationLimit, 25.0,                           \
          "Maximum allowed heap fragmentation")                             \
                                                                            \
  experimental(size_t, ZMarkStackSpaceLimit, 8*G,                           \
          "Maximum number of bytes allocated for mark stacks")              \
          range(32*M, 1024*G)                                               \
                                                                            \
  experimental(uint, ZCollectionInterval, 0,                                \
          "Force GC at a fixed time interval (in seconds)")                 \
                                                                            \
  experimental(bool, ZProactive, true,                                      \
          "Enable proactive GC cycles")                                     \
                                                                            \
  experimental(bool, ZUncommit, true,                                       \
          "Uncommit unused memory")                                         \
                                                                            \
  experimental(uintx, ZUncommitDelay, 5 * 60,                               \
          "Uncommit memory if it has been unused for the specified "        \
          "amount of time (in seconds)")                                    \
                                                                            \
  diagnostic(uint, ZStatisticsInterval, 10,                                 \
          "Time between statistics print outs (in seconds)")                \
          range(1, (uint)-1)                                                \
                                                                            \
  diagnostic(bool, ZVerifyViews, false,                                     \
          "Verify heap view accesses")                                      \
                                                                            \
  diagnostic(bool, ZVerifyRoots, trueInDebug,                               \
          "Verify roots")                                                   \
                                                                            \
  diagnostic(bool, ZVerifyObjects, false,                                   \
          "Verify objects")                                                 \
                                                                            \
  diagnostic(bool, ZVerifyMarking, trueInDebug,                             \
          "Verify marking stacks")                                          \
                                                                            \
  diagnostic(bool, ZVerifyForwarding, false,                                \
          "Verify forwarding tables")

#endif // SHARE_GC_Z_Z_GLOBALS_HPP
