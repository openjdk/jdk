/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZTLABUSAGE_HPP
#define SHARE_GC_Z_ZTLABUSAGE_HPP

#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/numberSeq.hpp"

// ZGC is retiring TLABs concurrently with the application running when
// processing the stack watermarks. For the common TLAB heuristic to work we
// need to return consistent TLAB usage information when a TLAB is retired.
// We snapshot the TLAB usage in the mark start pause for the young generation
// and use this information until the next garbage collection cycle.
//
// ZGC does not have set generation sizes unlike most other GCs and because of
// this there is no fixed TLAB capacity. For the common TLAB sizing heuristic
// to work properly ZGC estimates the current capacity by using a weighted
// average of the last 10 used values. ZGC uses the last snapshotted value as
// the value returned as tlab_used().

class ZTLABUsage {
private:
  // Accounting TLAB used until the next GC cycle
  Atomic<size_t> _used;
  // Sequence of historic used values
  TruncatedSeq   _used_history;

public:
  ZTLABUsage();

  void increase_used(size_t size);
  void decrease_used(size_t size);
  void reset();

  size_t tlab_used() const;
  size_t tlab_capacity() const;
};

#endif // SHARE_GC_Z_ZTLABUSAGE_HPP
