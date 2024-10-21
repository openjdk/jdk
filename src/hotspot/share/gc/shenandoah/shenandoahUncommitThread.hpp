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
  ShenandoahHeap* _heap;
  ShenandoahSharedFlag _soft_max_changed;
  ShenandoahSharedFlag _explicit_gc_requested;
  Monitor _lock;

  bool has_work(double shrink_before, size_t shrink_until) const;
  void uncommit(double shrink_before, size_t shrink_until);
public:
  explicit ShenandoahUncommitThread(ShenandoahHeap* heap);
  void run_service() override;

protected:
  void stop_service() override;

public:

  void notify_soft_max_changed();
  void notify_explicit_gc_requested();
};


#endif //SHARE_GC_SHENANDOAH_SHENANDOAHUNCOMMITTHREAD
