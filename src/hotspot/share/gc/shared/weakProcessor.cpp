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
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/weakProcessorPhases.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/globals.hpp"
#include "utilities/macros.hpp"

template <typename Container>
class OopsDoAndReportCounts {
public:
  void operator()(BoolObjectClosure* is_alive, OopClosure* keep_alive, WeakProcessorPhase phase) {
    Container::reset_dead_counter();

    CountingSkippedIsAliveClosure<BoolObjectClosure, OopClosure> cl(is_alive, keep_alive);
    WeakProcessorPhases::oop_storage(phase)->oops_do(&cl);

    Container::inc_dead_counter(cl.num_dead() + cl.num_skipped());
    Container::finish_dead_counter();
  }
};

void WeakProcessor::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive) {
  FOR_EACH_WEAK_PROCESSOR_PHASE(phase) {
    if (WeakProcessorPhases::is_serial(phase)) {
      WeakProcessorPhases::processor(phase)(is_alive, keep_alive);
    } else {
      if (WeakProcessorPhases::is_stringtable(phase)) {
        OopsDoAndReportCounts<StringTable>()(is_alive, keep_alive, phase);
      } else if (WeakProcessorPhases::is_resolved_method_table(phase)){
        OopsDoAndReportCounts<ResolvedMethodTable>()(is_alive, keep_alive, phase);
      } else {
        WeakProcessorPhases::oop_storage(phase)->weak_oops_do(is_alive, keep_alive);
      }
    }
  }
}

void WeakProcessor::oops_do(OopClosure* closure) {
  AlwaysTrueClosure always_true;
  weak_oops_do(&always_true, closure);
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
  FOR_EACH_WEAK_PROCESSOR_OOP_STORAGE_PHASE(phase) {
    ref_count += WeakProcessorPhases::oop_storage(phase)->allocation_count();
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

  uint storage_count = WeakProcessorPhases::oop_storage_phase_count;
  _storage_states = NEW_C_HEAP_ARRAY(StorageState, storage_count, mtGC);

  StorageState* states = _storage_states;
  FOR_EACH_WEAK_PROCESSOR_OOP_STORAGE_PHASE(phase) {
    OopStorage* storage = WeakProcessorPhases::oop_storage(phase);
    new (states++) StorageState(storage, _nworkers);
  }
  StringTable::reset_dead_counter();
  ResolvedMethodTable::reset_dead_counter();
}

WeakProcessor::Task::Task(uint nworkers) :
  _phase_times(NULL),
  _nworkers(nworkers),
  _serial_phases_done(WeakProcessorPhases::serial_phase_count),
  _storage_states(NULL)
{
  initialize();
}

WeakProcessor::Task::Task(WeakProcessorPhaseTimes* phase_times, uint nworkers) :
  _phase_times(phase_times),
  _nworkers(nworkers),
  _serial_phases_done(WeakProcessorPhases::serial_phase_count),
  _storage_states(NULL)
{
  initialize();
}

WeakProcessor::Task::~Task() {
  if (_storage_states != NULL) {
    StorageState* states = _storage_states;
    FOR_EACH_WEAK_PROCESSOR_OOP_STORAGE_PHASE(phase) {
      states->StorageState::~StorageState();
      ++states;
    }
    FREE_C_HEAP_ARRAY(StorageState, _storage_states);
  }
  StringTable::finish_dead_counter();
  ResolvedMethodTable::finish_dead_counter();
}

void WeakProcessor::GangTask::work(uint worker_id) {
  _erased_do_work(this, worker_id);
}
