/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/shared/weakProcessorPhases.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/globals.hpp"
#include "utilities/macros.hpp"

void WeakProcessor::do_serial_parts(BoolObjectClosure* is_alive,
                                    OopClosure* keep_alive) {
  WeakProcessorPhases::Iterator it = WeakProcessorPhases::serial_iterator();
  for ( ; !it.is_end(); ++it) {
    WeakProcessorPhases::processor(*it)(is_alive, keep_alive);
  }
}

void WeakProcessor::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive) {
  do_serial_parts(is_alive, keep_alive);

  OopStorageSet::Iterator it = OopStorageSet::weak_iterator();
  for ( ; !it.is_end(); ++it) {
    if (it->should_report_num_dead()) {
      CountingSkippedIsAliveClosure<BoolObjectClosure, OopClosure> cl(is_alive, keep_alive);
      it->oops_do(&cl);
      it->report_num_dead(cl.num_skipped() + cl.num_dead());
    } else {
      it->weak_oops_do(is_alive, keep_alive);
    }
  }
}

void WeakProcessor::oops_do(OopClosure* closure) {
  AlwaysTrueClosure always_true;
  do_serial_parts(&always_true, closure);

  OopStorageSet::Iterator it = OopStorageSet::weak_iterator();
  for ( ; !it.is_end(); ++it) {
    it->weak_oops_do(closure);
  }
}

uint WeakProcessor::ergo_workers(uint max_workers) {
  // Ignore ParallelRefProcEnabled; that's for j.l.r.Reference processing.
  if (ReferencesPerThread == 0) {
    // Configuration says always use all the threads.
    return max_workers;
  }

  // One thread per ReferencesPerThread references (or fraction thereof)
  // in the various OopStorage objects, bounded by max_threads.
  //
  // Serial phases are ignored in this calculation, because of the
  // cost of running unnecessary threads.  These phases are normally
  // small or empty (assuming they are configured to exist at all),
  // and development oriented, so not allocating any threads
  // specifically for them is okay.
  size_t ref_count = 0;
  OopStorageSet::Iterator it = OopStorageSet::weak_iterator();
  for ( ; !it.is_end(); ++it) {
    ref_count += it->allocation_count();
  }

  // +1 to (approx) round up the ref per thread division.
  size_t nworkers = 1 + (ref_count / ReferencesPerThread);
  nworkers = MIN2(nworkers, static_cast<size_t>(max_workers));
  return static_cast<uint>(nworkers);
}

void WeakProcessor::Task::initialize() {
  assert(_nworkers != 0, "must be");
  assert(_phase_times == NULL || _nworkers <= _phase_times->max_threads(),
         "nworkers (%u) exceeds max threads (%u)",
         _nworkers, _phase_times->max_threads());

  if (_phase_times) {
    _phase_times->set_active_workers(_nworkers);
  }
}

WeakProcessor::Task::Task(uint nworkers) :
  _phase_times(NULL),
  _nworkers(nworkers),
  _serial_phases_done(WeakProcessorPhases::serial_phase_count),
  _storage_states()
{
  initialize();
}

WeakProcessor::Task::Task(WeakProcessorPhaseTimes* phase_times, uint nworkers) :
  _phase_times(phase_times),
  _nworkers(nworkers),
  _serial_phases_done(WeakProcessorPhases::serial_phase_count),
  _storage_states()
{
  initialize();
}

void WeakProcessor::Task::report_num_dead() {
  for (int i = 0; i < _storage_states.par_state_count(); ++i) {
    StorageState* state = _storage_states.par_state(i);
    state->report_num_dead();
  }
}

void WeakProcessor::GangTask::work(uint worker_id) {
  _erased_do_work(this, worker_id);
}
