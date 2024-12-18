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

class ShenandoahUncommitThread : public ConcurrentGCThread {
  ShenandoahHeap* const _heap;

  // Indicates that `SoftMaxHeapSize` has changed
  ShenandoahSharedFlag _soft_max_changed;

  // Indicates that an explicit gc has been requested
  ShenandoahSharedFlag _explicit_gc_requested;

  // Indicates that the thread should stop and terminate
  ShenandoahSharedFlag _stop_requested;

  // Indicates whether it is safe to uncommit regions
  ShenandoahSharedFlag _uncommit_allowed;

  // Indicates that regions are being actively uncommitted
  ShenandoahSharedFlag _uncommit_in_progress;

  // This lock is used to coordinate stopping and terminating this thread
  Monitor _stop_lock;

  // This lock is used to coordinate allowing or forbidding regions to be uncommitted
  Monitor _uncommit_lock;

  // True if there are regions to uncommit and uncommits are allowed
  bool should_uncommit(double shrink_before, size_t shrink_until) const;

  // True if there are regions that have been empty for longer than ShenandoahUncommitDelay and the committed
  // memory is higher than soft max capacity or minimum capacity
  bool has_work(double shrink_before, size_t shrink_until) const;

  // Perform the work of uncommitting empty regions
  void uncommit(double shrink_before, size_t shrink_until);

  // True if the control thread has allowed this thread to uncommit regions
  bool is_uncommit_allowed() const;

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
  bool is_uncommit_in_progress() {
    return _uncommit_in_progress.is_set();
  }
protected:
  // Interrupt and stop this thread
  void stop_service() override;
};


#endif //SHARE_GC_SHENANDOAH_SHENANDOAHUNCOMMITTHREAD
