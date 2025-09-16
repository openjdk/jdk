/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"

class ShenandoahControlThread: public ShenandoahController {
  friend class VMStructs;

private:
  typedef enum {
    none,
    concurrent_normal,
    stw_degenerated,
    stw_full
  } GCMode;

  ShenandoahSharedFlag _gc_requested;
  GCCause::Cause       _requested_gc_cause;
  ShenandoahGC::ShenandoahDegenPoint _degen_point;

public:
  ShenandoahControlThread();

  void run_service() override;
  void stop_service() override;

  void request_gc(GCCause::Cause cause) override;

private:

  bool check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point);
  void service_concurrent_normal_cycle(GCCause::Cause cause);
  void service_stw_full_cycle(GCCause::Cause cause);
  void service_stw_degenerated_cycle(GCCause::Cause cause, ShenandoahGC::ShenandoahDegenPoint point);

  void notify_gc_waiters();

  // Handle GC request.
  // Blocks until GC is over.
  void handle_requested_gc(GCCause::Cause cause);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONTROLTHREAD_HPP
