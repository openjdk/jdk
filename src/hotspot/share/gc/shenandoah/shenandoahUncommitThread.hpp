/*
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHUNCOMMITTHREAD
#define SHARE_GC_SHENANDOAH_SHENANDOAHUNCOMMITTHREAD

#include "gc/shared/concurrentGCThread.hpp"

class ShenandoahHeap;
class ShenandoahHeapRegion;

class ShenandoahUncommitThread : public ConcurrentGCThread {
  ShenandoahHeap* const _heap;

  struct Candidate {
    ShenandoahHeapRegion* _region;
    uint64_t _priority;
  };

  // Candidate regions
  Candidate* _candidates;
  size_t _candidates_count;

  // Indicates that `SoftMaxHeapSize` has changed
  ShenandoahSharedFlag _soft_max_changed;

  // Indicates that an explicit gc has been requested
  ShenandoahSharedFlag _explicit_gc_requested;

  // Indicates whether it is safe to uncommit regions
  ShenandoahSharedFlag _uncommit_allowed;

  // Indicates that regions are being actively uncommitted
  ShenandoahSharedFlag _uncommit_in_progress;

  // This lock is used to coordinate allowing or forbidding regions to be uncommitted
  Monitor _uncommit_lock;

  // Plan work, fill out candidate regions. True if there is work.
  bool plan_work(double shrink_delay, size_t shrink_until);

  // Perform the work of uncommitting empty regions
  void uncommit(double shrink_delay, size_t shrink_until);

  // True if the control thread has allowed this thread to uncommit regions
  bool is_uncommit_allowed() const;

  // Stall uncommit thread to allow allocator progress
  bool check_uncommit_or_delay();

  // Iterate over and uncommit eligible regions until committed heap falls below
  // `shrink_until` bytes. A region is eligible for uncommit if the timestamp at which
  // it was last made empty is before `shrink_delay` seconds since jvm start.
  // Returns the number of regions uncommitted. May be interrupted by `forbid_uncommit`.
  size_t do_uncommit_work(double shrink_delay, size_t shrink_until);

  static int compare_uncommit_priority(Candidate& a, Candidate& b);

public:
  explicit ShenandoahUncommitThread(ShenandoahHeap* heap);

  // Periodically check for regions to uncommit
  void run_service() override;

  // Wake up this thread and try to uncommit for changed soft max size
  void notify_soft_max_changed();

  // Wake up this thread and try to uncommit for min heap size
  void notify_explicit_gc_requested();

  // Wait for uncommit operations to stop, returns immediately if uncommit thread is idle
  void forbid_uncommit();

  // Allows uncommit operations to happen, does not block
  void allow_uncommit();

  // True if uncommit is in progress
  bool is_uncommit_in_progress() const {
    return _uncommit_in_progress.is_set();
  }
protected:
  // Interrupt and stop this thread
  void stop_service() override;
};


#endif //SHARE_GC_SHENANDOAH_SHENANDOAHUNCOMMITTHREAD
