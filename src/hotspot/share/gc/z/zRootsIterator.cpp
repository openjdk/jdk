/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

static const ZStatSubPhase ZSubPhasePauseRootsJVMTIWeakExport("Pause Roots JVMTIWeakExport");
static const ZStatSubPhase ZSubPhaseConcurrentRootsOopStorageSet("Concurrent Roots OopStorageSet");
static const ZStatSubPhase ZSubPhaseConcurrentRootsClassLoaderDataGraph("Concurrent Roots ClassLoaderDataGraph");
static const ZStatSubPhase ZSubPhaseConcurrentRootsJavaThreads("Concurrent Roots JavaThreads");
static const ZStatSubPhase ZSubPhaseConcurrentRootsCodeCache("Concurrent Roots CodeCache");
static const ZStatSubPhase ZSubPhasePauseWeakRootsJVMTIWeakExport("Pause Weak Roots JVMTIWeakExport");
static const ZStatSubPhase ZSubPhaseConcurrentWeakRootsOopStorageSet("Concurrent Weak Roots OopStorageSet");

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
ZParallelOopsDo<T, F>::ZParallelOopsDo(T* iter) :
    _iter(iter),
    _completed(false) {}

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
void ZParallelOopsDo<T, F>::oops_do(ZRootsIteratorClosure* cl) {
  if (!Atomic::load(&_completed)) {
    (_iter->*F)(cl);
    if (!Atomic::load(&_completed)) {
      Atomic::store(&_completed, true);
    }
  }
}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
ZSerialWeakOopsDo<T, F>::ZSerialWeakOopsDo(T* iter) :
    _iter(iter),
    _claimed(false) {}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
void ZSerialWeakOopsDo<T, F>::weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  if (!Atomic::load(&_claimed) && Atomic::cmpxchg(&_claimed, false, true) == false) {
    (_iter->*F)(is_alive, cl);
  }
}

ZJavaThreadsIterator::ZJavaThreadsIterator() :
    _threads(),
    _claimed(0) {}

uint ZJavaThreadsIterator::claim() {
  return Atomic::fetch_and_add(&_claimed, 1u);
}

void ZJavaThreadsIterator::threads_do(ThreadClosure* cl) {
  for (uint i = claim(); i < _threads.length(); i = claim()) {
    cl->do_thread(_threads.thread_at(i));
  }
}

void ZRelocateRoots::oops_do(OopClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsJVMTIWeakExport);
  AlwaysTrueClosure always_alive;
  JvmtiExport::weak_oops_do(&always_alive, cl);
}

ZConcurrentRootsIterator::ZConcurrentRootsIterator(int cld_claim) :
    _oop_storage_set_iter(),
    _java_threads_iter(),
    _cld_claim(cld_claim),
    _oop_storage_set(this),
    _class_loader_data_graph(this),
    _java_threads(this),
    _code_cache(this) {
  ClassLoaderDataGraph::clear_claimed_marks(cld_claim);
  if (!ClassUnloading) {
    ZNMethodTable::nmethods_do_begin();
  }
}

ZConcurrentRootsIterator::~ZConcurrentRootsIterator() {
  if (!ClassUnloading) {
    ZNMethodTable::nmethods_do_end();
  }
}

void ZConcurrentRootsIterator::do_oop_storage_set(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsOopStorageSet);
  _oop_storage_set_iter.oops_do(cl);
}

void ZConcurrentRootsIterator::do_class_loader_data_graph(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph);
  CLDToOopClosure cld_cl(cl, _cld_claim);
  ClassLoaderDataGraph::always_strong_cld_do(&cld_cl);
}

void ZConcurrentRootsIterator::do_code_cache(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsCodeCache);
  ZNMethod::oops_do(cl, cl->should_disarm_nmethods());
}

class ZConcurrentRootsIteratorThreadClosure : public ThreadClosure {
private:
  // The resource mark is needed because interpreter oop maps are
  // not reused in concurrent mode. Instead, they are temporary and
  // resource allocated.
  ResourceMark                 _rm;
  ZRootsIteratorClosure* const _cl;

public:
  ZConcurrentRootsIteratorThreadClosure(ZRootsIteratorClosure* cl) :
      _cl(cl) {}

  virtual void do_thread(Thread* thread) {
    _cl->do_thread(thread);
  }
};

void ZConcurrentRootsIterator::do_java_threads(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsJavaThreads);
  ZConcurrentRootsIteratorThreadClosure thread_cl(cl);
  _java_threads_iter.threads_do(&thread_cl);
}

void ZConcurrentRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  _oop_storage_set.oops_do(cl);
  _class_loader_data_graph.oops_do(cl);
  _java_threads.oops_do(cl);
  if (!ClassUnloading) {
    _code_cache.oops_do(cl);
  }
}

ZWeakRootsIterator::ZWeakRootsIterator() :
    _jvmti_weak_export(this) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
}

void ZWeakRootsIterator::do_jvmti_weak_export(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseWeakRootsJVMTIWeakExport);
  JvmtiExport::weak_oops_do(is_alive, cl);
}

void ZWeakRootsIterator::weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  _jvmti_weak_export.weak_oops_do(is_alive, cl);
}

void ZWeakRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  AlwaysTrueClosure always_alive;
  weak_oops_do(&always_alive, cl);
}

ZConcurrentWeakRootsIterator::ZConcurrentWeakRootsIterator() :
    _oop_storage_set_iter(),
    _oop_storage_set(this) {
}

void ZConcurrentWeakRootsIterator::report_num_dead() {
  _oop_storage_set_iter.report_num_dead();
}

void ZConcurrentWeakRootsIterator::do_oop_storage_set(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsOopStorageSet);
  _oop_storage_set_iter.oops_do(cl);
}

void ZConcurrentWeakRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  _oop_storage_set.oops_do(cl);
}
