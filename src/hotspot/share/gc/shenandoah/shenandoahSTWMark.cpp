/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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


#include "precompiled.hpp"

#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/workgroup.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"


class ShenandoahSTWMarkTask : public AbstractGangTask {
private:
  ShenandoahSTWMark* const _mark;

public:
  ShenandoahSTWMarkTask(ShenandoahSTWMark* mark);
  void work(uint worker_id);
};

ShenandoahSTWMarkTask::ShenandoahSTWMarkTask(ShenandoahSTWMark* mark) :
  AbstractGangTask("Shenandoah STW mark"),
  _mark(mark) {
}

void ShenandoahSTWMarkTask::work(uint worker_id) {
  ShenandoahParallelWorkerSession worker_session(worker_id);
  _mark->mark_roots(worker_id);
  _mark->finish_mark(worker_id);
}

ShenandoahSTWMark::ShenandoahSTWMark(bool full_gc) :
  ShenandoahMark(),
  _root_scanner(full_gc ? ShenandoahPhaseTimings::full_gc_mark : ShenandoahPhaseTimings::degen_gc_stw_mark),
  _terminator(ShenandoahHeap::heap()->workers()->active_workers(), ShenandoahHeap::heap()->task_queues()),
  _full_gc(full_gc) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a Shenandoah safepoint");
}

void ShenandoahSTWMark::mark() {
  // Init mark, do not expect forwarded pointers in roots
  if (ShenandoahVerify) {
    assert(Thread::current()->is_VM_thread(), "Must be");
    heap()->verifier()->verify_roots_no_forwarded();
  }

  uint nworkers = heap()->workers()->active_workers();
  task_queues()->reserve(nworkers);

  ReferenceProcessor* rp = NULL;
  if (heap()->process_references()) {
    rp = heap()->ref_processor();
    rp->set_active_mt_degree(nworkers);

    // enable ("weak") refs discovery
    rp->enable_discovery(true /*verify_no_refs*/);
    rp->setup_policy(_heap->soft_ref_policy()->should_clear_all_soft_refs());
  }

  shenandoah_assert_rp_isalive_not_installed();
  ShenandoahIsAliveSelector is_alive;
  ReferenceProcessorIsAliveMutator fix_isalive(rp, is_alive.is_alive_closure());

  {
    // Mark
    StrongRootsScope scope(nworkers);
    ShenandoahSTWMarkTask task(this);
    heap()->workers()->run_task(&task);

    assert(task_queues()->is_empty(), "Should be empty");
  }

  // Weak reference processing
  process_weak_refs(_full_gc /*full gc*/);

  assert(task_queues()->is_empty(), "Should be empty");
  TASKQUEUE_STATS_ONLY(task_queues()->print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());

}

void ShenandoahSTWMark::mark_roots(uint worker_id) {
  ShenandoahInitMarkRootsClosure  init_mark(task_queues()->queue(worker_id));
  _root_scanner.roots_do(&init_mark, worker_id);
}

void ShenandoahSTWMark::finish_mark(uint worker_id) {
  ShenandoahPhaseTimings::Phase phase = _full_gc ? ShenandoahPhaseTimings::full_gc_mark : ShenandoahPhaseTimings::degen_gc_stw_mark;
  ShenandoahWorkerTimingsTracker timer(phase, ShenandoahPhaseTimings::ParallelMark, worker_id);

  ReferenceProcessor* rp = NULL;
  if (heap()->process_references()) {
    rp = heap()->ref_processor();
  }

  mark_loop(worker_id, &_terminator, rp,
            false, // not cancellable
            ShenandoahStringDedup::is_enabled());
}

