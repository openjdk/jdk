/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2022, Red Hat, Inc. All rights reserved.
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

#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahNMethod.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/powerOfTwo.hpp"


ShenandoahNMethodTable* ShenandoahCodeRoots::_nmethod_table;
int ShenandoahCodeRoots::_disarmed_value = 1;

void ShenandoahCodeRoots::initialize() {
  _nmethod_table = new ShenandoahNMethodTable();
}

void ShenandoahCodeRoots::register_nmethod(nmethod* nm) {
  assert(CodeCache_lock->owned_by_self(), "Must have CodeCache_lock held");
  _nmethod_table->register_nmethod(nm);
}

void ShenandoahCodeRoots::unregister_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
  _nmethod_table->unregister_nmethod(nm);
}

void ShenandoahCodeRoots::arm_nmethods() {
  BarrierSet::barrier_set()->barrier_set_nmethod()->arm_all_nmethods();
}

class ShenandoahDisarmNMethodClosure : public NMethodClosure {
public:
  virtual void do_nmethod(nmethod* nm) {
    ShenandoahNMethod::disarm_nmethod(nm);
  }
};

class ShenandoahDisarmNMethodsTask : public WorkerTask {
private:
  ShenandoahDisarmNMethodClosure      _cl;
  ShenandoahConcurrentNMethodIterator _iterator;

public:
  ShenandoahDisarmNMethodsTask() :
    WorkerTask("Shenandoah Disarm NMethods"),
    _iterator(ShenandoahCodeRoots::table()) {
    assert(SafepointSynchronize::is_at_safepoint(), "Only at a safepoint");
  }

  virtual void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    _iterator.nmethods_do(&_cl);
  }
};

void ShenandoahCodeRoots::disarm_nmethods() {
  ShenandoahDisarmNMethodsTask task;
  ShenandoahHeap::heap()->workers()->run_task(&task);
}

class ShenandoahNMethodUnlinkClosure : public NMethodClosure {
private:
  bool                      _unloading_occurred;
  ShenandoahHeap* const     _heap;
  BarrierSetNMethod* const  _bs;

public:
  ShenandoahNMethodUnlinkClosure(bool unloading_occurred) :
      _unloading_occurred(unloading_occurred),
      _heap(ShenandoahHeap::heap()),
      _bs(ShenandoahBarrierSet::barrier_set()->barrier_set_nmethod()) {}

  virtual void do_nmethod(nmethod* nm) {
    assert(_heap->is_concurrent_weak_root_in_progress(), "Only this phase");

    ShenandoahNMethod* nm_data = ShenandoahNMethod::gc_data(nm);
    assert(!nm_data->is_unregistered(), "Should not see unregistered entry");

    if (nm->is_unloading()) {
      ShenandoahNMethodLocker locker(nm_data->lock());
      nm->unlink();
      return;
    }

    {
      ShenandoahNMethodLocker locker(nm_data->lock());

      // Heal oops
      if (_bs->is_armed(nm)) {
        ShenandoahNMethod::heal_nmethod_metadata(nm_data);
        // Must remain armed to complete remaining work in nmethod entry barrier
        assert(_bs->is_armed(nm), "Should remain armed");
      }
    }

    // Clear compiled ICs and exception caches
    ShenandoahNMethodLocker locker(nm_data->ic_lock());
    nm->unload_nmethod_caches(_unloading_occurred);
  }
};

class ShenandoahUnlinkTask : public WorkerTask {
private:
  ShenandoahNMethodUnlinkClosure      _cl;
  ShenandoahConcurrentNMethodIterator _iterator;

public:
  ShenandoahUnlinkTask(bool unloading_occurred) :
    WorkerTask("Shenandoah Unlink NMethods"),
    _cl(unloading_occurred),
    _iterator(ShenandoahCodeRoots::table()) {}

  virtual void work(uint worker_id) {
    _iterator.nmethods_do(&_cl);
  }
};

void ShenandoahCodeRoots::unlink(WorkerThreads* workers, bool unloading_occurred) {
  assert(ShenandoahHeap::heap()->unload_classes(), "Only when running concurrent class unloading");

  ShenandoahUnlinkTask task(unloading_occurred);
  workers->run_task(&task);
}

void ShenandoahCodeRoots::purge() {
  assert(ShenandoahHeap::heap()->unload_classes(), "Only when running concurrent class unloading");

  ClassUnloadingContext::context()->purge_and_free_nmethods();
}

ShenandoahCodeRootsIterator::ShenandoahCodeRootsIterator() :
        _table_snapshot(nullptr) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  MutexLocker locker(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  _table_snapshot = ShenandoahCodeRoots::table()->snapshot_for_iteration();
}

ShenandoahCodeRootsIterator::~ShenandoahCodeRootsIterator() {
  MonitorLocker locker(CodeCache_lock, Mutex::_no_safepoint_check_flag);
  ShenandoahCodeRoots::table()->finish_iteration(_table_snapshot);
  _table_snapshot = nullptr;
  locker.notify_all();
}

void ShenandoahCodeRootsIterator::possibly_parallel_nmethods_do(NMethodClosure *f) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  assert(_table_snapshot != nullptr, "Sanity");
  _table_snapshot->parallel_nmethods_do(f);
}
