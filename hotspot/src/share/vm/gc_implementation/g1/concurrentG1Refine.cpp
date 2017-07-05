/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentG1RefineThread.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1HotCardCache.hpp"
#include "runtime/java.hpp"

ConcurrentG1Refine::ConcurrentG1Refine(G1CollectedHeap* g1h) :
  _threads(NULL), _n_threads(0),
  _hot_card_cache(g1h)
{
  // Ergonomically select initial concurrent refinement parameters
  if (FLAG_IS_DEFAULT(G1ConcRefinementGreenZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementGreenZone, MAX2<int>(ParallelGCThreads, 1));
  }
  set_green_zone(G1ConcRefinementGreenZone);

  if (FLAG_IS_DEFAULT(G1ConcRefinementYellowZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementYellowZone, green_zone() * 3);
  }
  set_yellow_zone(MAX2<int>(G1ConcRefinementYellowZone, green_zone()));

  if (FLAG_IS_DEFAULT(G1ConcRefinementRedZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementRedZone, yellow_zone() * 2);
  }
  set_red_zone(MAX2<int>(G1ConcRefinementRedZone, yellow_zone()));

  _n_worker_threads = thread_num();
  // We need one extra thread to do the young gen rset size sampling.
  _n_threads = _n_worker_threads + 1;

  reset_threshold_step();

  _threads = NEW_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _n_threads, mtGC);

  int worker_id_offset = (int)DirtyCardQueueSet::num_par_ids();

  ConcurrentG1RefineThread *next = NULL;
  for (int i = _n_threads - 1; i >= 0; i--) {
    ConcurrentG1RefineThread* t = new ConcurrentG1RefineThread(this, next, worker_id_offset, i);
    assert(t != NULL, "Conc refine should have been created");
    if (t->osthread() == NULL) {
        vm_shutdown_during_initialization("Could not create ConcurrentG1RefineThread");
    }

    assert(t->cg1r() == this, "Conc refine thread should refer to this");
    _threads[i] = t;
    next = t;
  }
}

void ConcurrentG1Refine::reset_threshold_step() {
  if (FLAG_IS_DEFAULT(G1ConcRefinementThresholdStep)) {
    _thread_threshold_step = (yellow_zone() - green_zone()) / (worker_thread_num() + 1);
  } else {
    _thread_threshold_step = G1ConcRefinementThresholdStep;
  }
}

void ConcurrentG1Refine::init() {
  _hot_card_cache.initialize();
}

void ConcurrentG1Refine::stop() {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      _threads[i]->stop();
    }
  }
}

void ConcurrentG1Refine::reinitialize_threads() {
  reset_threshold_step();
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      _threads[i]->initialize();
    }
  }
}

ConcurrentG1Refine::~ConcurrentG1Refine() {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      delete _threads[i];
    }
    FREE_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _threads, mtGC);
  }
}

void ConcurrentG1Refine::threads_do(ThreadClosure *tc) {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      tc->do_thread(_threads[i]);
    }
  }
}

void ConcurrentG1Refine::worker_threads_do(ThreadClosure * tc) {
  if (_threads != NULL) {
    for (int i = 0; i < worker_thread_num(); i++) {
      tc->do_thread(_threads[i]);
    }
  }
}

int ConcurrentG1Refine::thread_num() {
  int n_threads = (G1ConcRefinementThreads > 0) ? G1ConcRefinementThreads
                                                : ParallelGCThreads;
  return MAX2<int>(n_threads, 1);
}

void ConcurrentG1Refine::print_worker_threads_on(outputStream* st) const {
  for (int i = 0; i < _n_threads; ++i) {
    _threads[i]->print_on(st);
    st->cr();
  }
}

ConcurrentG1RefineThread * ConcurrentG1Refine::sampling_thread() const {
  return _threads[worker_thread_num()];
}
