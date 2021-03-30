/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "logging/logStream.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/vmThread.hpp"
#include "services/heapObjectStatistics.hpp"
#include "utilities/copy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

HeapObjectStatistics* HeapObjectStatistics::_instance = NULL;

class HeapObjectStatsObjectClosure : public ObjectClosure {
private:
  HeapObjectStatistics* const _stats;
public:
  HeapObjectStatsObjectClosure() : _stats(HeapObjectStatistics::instance()) {}
  void do_object(oop obj) {
    _stats->visit_object(obj);
  }
};

class VM_HeapObjectStatistics : public VM_Operation {
public:
  VMOp_Type type() const { return VMOp_HeapObjectStatistics; }
  bool doit_prologue() {
    Heap_lock->lock();
    return true;
  }

  void doit_epilogue() {
    Heap_lock->unlock();
  }

  void doit() {
    assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");
    assert(Heap_lock->is_locked(), "should have the Heap_lock");

    CollectedHeap* heap = Universe::heap();
    heap->ensure_parsability(false);

    HeapObjectStatistics* stats = HeapObjectStatistics::instance();
    stats->begin_sample();

    HeapObjectStatsObjectClosure cl;
    heap->object_iterate(&cl);
  }
};

HeapObjectStatisticsTask::HeapObjectStatisticsTask() : PeriodicTask(HeapObjectStatsSamplingInterval) {}

void HeapObjectStatisticsTask::task() {
  VM_HeapObjectStatistics vmop;
  VMThread::execute(&vmop);
}

void HeapObjectStatistics::initialize() {
  assert(_instance == NULL, "Don't init twice");
  if (HeapObjectStats) {
    _instance = new HeapObjectStatistics();
    _instance->start();
  }
}

void HeapObjectStatistics::shutdown() {
  if (HeapObjectStats) {
    assert(_instance != NULL, "Must be initialized");
    LogTarget(Info, heap, stats) lt;
    if (lt.is_enabled()) {
      LogStream ls(lt);
      ResourceMark rm;
      _instance->print(&ls);
    }
    _instance->stop();
    delete _instance;
    _instance = NULL;
  }
}

HeapObjectStatistics* HeapObjectStatistics::instance() {
  assert(_instance != NULL, "Must be initialized");
  return _instance;
}

void HeapObjectStatistics::increase_counter(uint64_t& counter, uint64_t val) {
  uint64_t oldval = counter;
  uint64_t newval = counter + val;
  if (newval < oldval) {
    log_warning(heap, stats)("HeapObjectStats counter overflow: resulting statistics will be useless");
  }
  counter = newval;
}

HeapObjectStatistics::HeapObjectStatistics() :
  _task(), _num_samples(0), _num_objects(0), _num_ihashed(0), _num_locked(0), _lds(0) { }

void HeapObjectStatistics::start() {
  _task.enroll();
}

void HeapObjectStatistics::stop() {
  _task.disenroll();
}

void HeapObjectStatistics::begin_sample() {
  _num_samples++;
}

void HeapObjectStatistics::visit_object(oop obj) {
  increase_counter(_num_objects);
  if (!obj->mark().has_no_hash()) {
    increase_counter(_num_ihashed);
    if (obj->mark().age() > 0) {
      increase_counter(_num_ihashed_moved);
    }
  }
  if (obj->mark().is_locked()) {
    increase_counter(_num_locked);
  }
  size_t size = obj->size();
  increase_counter(_lds, obj->size());
}

void HeapObjectStatistics::print(outputStream* out) const {
  if (!HeapObjectStats) {
    return;
  }
  if (_num_samples == 0 || _num_objects == 0) {
    return;
  }

  out->print_cr("Number of samples:  " UINT64_FORMAT, _num_samples);
  out->print_cr("Average number of objects: " UINT64_FORMAT, _num_objects / _num_samples);
  out->print_cr("Average object size: " UINT64_FORMAT " bytes, %.1f words", (_lds * HeapWordSize) / _num_objects, (float) _lds / _num_objects);
  out->print_cr("Average number of hashed objects: " UINT64_FORMAT " (%.2f%%)", _num_ihashed / _num_samples, (float) (_num_ihashed * 100.0) / _num_objects);
  out->print_cr("Average number of moved hashed objects: " UINT64_FORMAT " (%.2f%%)", _num_ihashed_moved / _num_samples, (float) (_num_ihashed_moved * 100.0) / _num_objects);
  out->print_cr("Average number of locked objects: " UINT64_FORMAT " (%.2f%%)", _num_locked / _num_samples, (float) (_num_locked * 100) / _num_objects);
  out->print_cr("Average LDS: " UINT64_FORMAT " bytes", _lds * HeapWordSize / _num_samples);
  out->print_cr("Avg LDS with (assumed) 64bit header: " UINT64_FORMAT " bytes (%.1f%%)", (_lds - _num_objects) * HeapWordSize / _num_samples, ((float) _lds - _num_objects) * 100.0 / _lds);
}
