/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_WEAKPROCESSOR_INLINE_HPP
#define SHARE_GC_SHARED_WEAKPROCESSOR_INLINE_HPP

#include "classfile/stringTable.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "gc/shared/weakProcessorPhases.hpp"
#include "gc/shared/weakProcessorPhaseTimes.hpp"
#include "gc/shared/workgroup.hpp"
#include "utilities/debug.hpp"

class BoolObjectClosure;
class OopClosure;

template<typename IsAlive>
class CountingIsAliveClosure : public BoolObjectClosure {
  IsAlive* _inner;

  size_t _num_dead;
  size_t _num_total;

public:
  CountingIsAliveClosure(IsAlive* cl) : _inner(cl), _num_dead(0), _num_total(0) { }

  virtual bool do_object_b(oop obj) {
    bool result = _inner->do_object_b(obj);
    _num_dead += !result;
    _num_total++;
    return result;
  }

  size_t num_dead() const { return _num_dead; }
  size_t num_total() const { return _num_total; }
};

template <typename IsAlive, typename KeepAlive>
class CountingSkippedIsAliveClosure : public Closure {
  CountingIsAliveClosure<IsAlive> _counting_is_alive;
  KeepAlive* _keep_alive;

  size_t _num_skipped;

public:
  CountingSkippedIsAliveClosure(IsAlive* is_alive, KeepAlive* keep_alive) :
    _counting_is_alive(is_alive), _keep_alive(keep_alive), _num_skipped(0) { }

  void do_oop(oop* p) {
    oop obj = *p;
    if (obj == NULL) {
      _num_skipped++;
    } else if (_counting_is_alive.do_object_b(obj)) {
      _keep_alive->do_oop(p);
    } else {
      *p = NULL;
    }
  }

  size_t num_dead() const { return _counting_is_alive.num_dead(); }
  size_t num_skipped() const { return _num_skipped; }
  size_t num_total() const { return _counting_is_alive.num_total() + num_skipped(); }
};

template<typename IsAlive, typename KeepAlive>
void WeakProcessor::Task::work(uint worker_id,
                               IsAlive* is_alive,
                               KeepAlive* keep_alive) {
  assert(worker_id < _nworkers,
         "worker_id (%u) exceeds task's configured workers (%u)",
         worker_id, _nworkers);

  FOR_EACH_WEAK_PROCESSOR_PHASE(phase) {
    if (WeakProcessorPhases::is_serial(phase)) {
      CountingIsAliveClosure<IsAlive> cl(is_alive);
      uint serial_index = WeakProcessorPhases::serial_index(phase);
      if (_serial_phases_done.try_claim_task(serial_index)) {
        WeakProcessorPhaseTimeTracker pt(_phase_times, phase);
        WeakProcessorPhases::processor(phase)(&cl, keep_alive);
        if (_phase_times != NULL) {
          _phase_times->record_phase_items(phase, cl.num_dead(), cl.num_total());
        }
      }
    } else {
      CountingSkippedIsAliveClosure<IsAlive, KeepAlive> cl(is_alive, keep_alive);
      WeakProcessorPhaseTimeTracker pt(_phase_times, phase, worker_id);
      uint storage_index = WeakProcessorPhases::oop_storage_index(phase);
      _storage_states[storage_index].oops_do(&cl);
      if (_phase_times != NULL) {
        _phase_times->record_worker_items(worker_id, phase, cl.num_dead(), cl.num_total());
      }
      if (WeakProcessorPhases::is_stringtable(phase)) {
        StringTable::inc_dead_counter(cl.num_dead() + cl.num_skipped());
      }
    }
  }

  _serial_phases_done.all_tasks_completed(_nworkers);
}

class WeakProcessor::GangTask : public AbstractGangTask {
  Task _task;
  BoolObjectClosure* _is_alive;
  OopClosure* _keep_alive;
  void (*_erased_do_work)(GangTask* task, uint worker_id);

  template<typename IsAlive, typename KeepAlive>
  static void erased_do_work(GangTask* task, uint worker_id) {
    task->_task.work(worker_id,
                     static_cast<IsAlive*>(task->_is_alive),
                     static_cast<KeepAlive*>(task->_keep_alive));
  }

public:
  template<typename IsAlive, typename KeepAlive>
  GangTask(const char* name,
           IsAlive* is_alive,
           KeepAlive* keep_alive,
           WeakProcessorPhaseTimes* phase_times,
           uint nworkers) :
    AbstractGangTask(name),
    _task(phase_times, nworkers),
    _is_alive(is_alive),
    _keep_alive(keep_alive),
    _erased_do_work(&erased_do_work<IsAlive, KeepAlive>)
  {}

  virtual void work(uint worker_id);
};

template<typename IsAlive, typename KeepAlive>
void WeakProcessor::weak_oops_do(WorkGang* workers,
                                 IsAlive* is_alive,
                                 KeepAlive* keep_alive,
                                 WeakProcessorPhaseTimes* phase_times) {
  WeakProcessorTimeTracker tt(phase_times);

  uint nworkers = ergo_workers(MIN2(workers->active_workers(),
                                    phase_times->max_threads()));

  GangTask task("Weak Processor", is_alive, keep_alive, phase_times, nworkers);
  workers->run_task(&task, nworkers);
}

template<typename IsAlive, typename KeepAlive>
void WeakProcessor::weak_oops_do(WorkGang* workers,
                                 IsAlive* is_alive,
                                 KeepAlive* keep_alive,
                                 uint indent_log) {
  uint nworkers = ergo_workers(workers->active_workers());
  WeakProcessorPhaseTimes pt(nworkers);
  weak_oops_do(workers, is_alive, keep_alive, &pt);
  pt.log_print_phases(indent_log);
}

#endif // SHARE_GC_SHARED_WEAKPROCESSOR_INLINE_HPP
