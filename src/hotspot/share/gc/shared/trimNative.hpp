/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_TRIMNATIVE_HPP
#define SHARE_GC_SHARED_TRIMNATIVE_HPP

#include "memory/allStatic.hpp"

class TrimNative : public AllStatic {
public:

  static void initialize();
  static void cleanup();

  // Pause periodic trim (if enabled).
  static void pause_periodic_trim();

  // Unpause periodic trim (if enabled).
  static void unpause_periodic_trim();

  // Schedule an explicit trim now.
  // If periodic trims are enabled and had been paused, they are unpaused
  // and the interval is reset.
  static void schedule_trim();

  // Pause periodic trimming while in scope; when leaving scope,
  // resume periodic trimming.
  struct PauseMark {
    PauseMark()   { pause_periodic_trim(); }
    ~PauseMark()  { unpause_periodic_trim(); }
  };

  // Pause periodic trimming while in scope; when leaving scope,
  // trim immediately and resume periodic trimming with a new interval.
  struct PauseThenTrimMark {
    PauseThenTrimMark()   { pause_periodic_trim(); }
    ~PauseThenTrimMark()  { schedule_trim(); }
  };

};

#endif // SHARE_GC_SHARED_TRIMNATIVE_HPP
