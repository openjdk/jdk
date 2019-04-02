/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/thread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/synchronizer.hpp"
#include "services/management.hpp"
#include "utilities/debug.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

static const ZStatSubPhase ZSubPhasePauseRootsSetup("Pause Roots Setup");
static const ZStatSubPhase ZSubPhasePauseRoots("Pause Roots");
static const ZStatSubPhase ZSubPhasePauseRootsTeardown("Pause Roots Teardown");
static const ZStatSubPhase ZSubPhasePauseRootsUniverse("Pause Roots Universe");
static const ZStatSubPhase ZSubPhasePauseRootsObjectSynchronizer("Pause Roots ObjectSynchronizer");
static const ZStatSubPhase ZSubPhasePauseRootsManagement("Pause Roots Management");
static const ZStatSubPhase ZSubPhasePauseRootsJVMTIExport("Pause Roots JVMTIExport");
static const ZStatSubPhase ZSubPhasePauseRootsJVMTIWeakExport("Pause Roots JVMTIWeakExport");
static const ZStatSubPhase ZSubPhasePauseRootsSystemDictionary("Pause Roots SystemDictionary");
static const ZStatSubPhase ZSubPhasePauseRootsThreads("Pause Roots Threads");
static const ZStatSubPhase ZSubPhasePauseRootsCodeCache("Pause Roots CodeCache");

static const ZStatSubPhase ZSubPhaseConcurrentRootsSetup("Concurrent Roots Setup");
static const ZStatSubPhase ZSubPhaseConcurrentRoots("Concurrent Roots");
static const ZStatSubPhase ZSubPhaseConcurrentRootsTeardown("Concurrent Roots Teardown");
static const ZStatSubPhase ZSubPhaseConcurrentRootsJNIHandles("Concurrent Roots JNIHandles");
static const ZStatSubPhase ZSubPhaseConcurrentRootsClassLoaderDataGraph("Concurrent Roots ClassLoaderDataGraph");

static const ZStatSubPhase ZSubPhasePauseWeakRootsSetup("Pause Weak Roots Setup");
static const ZStatSubPhase ZSubPhasePauseWeakRoots("Pause Weak Roots");
static const ZStatSubPhase ZSubPhasePauseWeakRootsTeardown("Pause Weak Roots Teardown");
static const ZStatSubPhase ZSubPhasePauseWeakRootsJVMTIWeakExport("Pause Weak Roots JVMTIWeakExport");
static const ZStatSubPhase ZSubPhasePauseWeakRootsJFRWeak("Pause Weak Roots JFRWeak");

static const ZStatSubPhase ZSubPhaseConcurrentWeakRoots("Concurrent Weak Roots");
static const ZStatSubPhase ZSubPhaseConcurrentWeakRootsVMWeakHandles("Concurrent Weak Roots VMWeakHandles");
static const ZStatSubPhase ZSubPhaseConcurrentWeakRootsJNIWeakHandles("Concurrent Weak Roots JNIWeakHandles");
static const ZStatSubPhase ZSubPhaseConcurrentWeakRootsStringTable("Concurrent Weak Roots StringTable");

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
ZSerialOopsDo<T, F>::ZSerialOopsDo(T* iter) :
    _iter(iter),
    _claimed(false) {}

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
void ZSerialOopsDo<T, F>::oops_do(ZRootsIteratorClosure* cl) {
  if (!_claimed && Atomic::cmpxchg(true, &_claimed, false) == false) {
    (_iter->*F)(cl);
  }
}

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
ZParallelOopsDo<T, F>::ZParallelOopsDo(T* iter) :
    _iter(iter),
    _completed(false) {}

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
void ZParallelOopsDo<T, F>::oops_do(ZRootsIteratorClosure* cl) {
  if (!_completed) {
    (_iter->*F)(cl);
    if (!_completed) {
      _completed = true;
    }
  }
}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
ZSerialWeakOopsDo<T, F>::ZSerialWeakOopsDo(T* iter) :
    _iter(iter),
    _claimed(false) {}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
void ZSerialWeakOopsDo<T, F>::weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  if (!_claimed && Atomic::cmpxchg(true, &_claimed, false) == false) {
    (_iter->*F)(is_alive, cl);
  }
}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
ZParallelWeakOopsDo<T, F>::ZParallelWeakOopsDo(T* iter) :
    _iter(iter),
    _completed(false) {}

template <typename T, void (T::*F)(BoolObjectClosure*, ZRootsIteratorClosure*)>
void ZParallelWeakOopsDo<T, F>::weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  if (!_completed) {
    (_iter->*F)(is_alive, cl);
    if (!_completed) {
      _completed = true;
    }
  }
}

class ZRootsIteratorCodeBlobClosure : public CodeBlobToOopClosure {
private:
  BarrierSetNMethod* _bs;

public:
  ZRootsIteratorCodeBlobClosure(OopClosure* cl) :
    CodeBlobToOopClosure(cl, true /* fix_relocations */),
    _bs(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

  virtual void do_code_blob(CodeBlob* cb) {
    nmethod* const nm = cb->as_nmethod_or_null();
    if (nm != NULL && !nm->test_set_oops_do_mark()) {
      CodeBlobToOopClosure::do_code_blob(cb);
      _bs->disarm(nm);
    }
  }
};

class ZRootsIteratorThreadClosure : public ThreadClosure {
private:
  ZRootsIteratorClosure* _cl;

public:
  ZRootsIteratorThreadClosure(ZRootsIteratorClosure* cl) :
      _cl(cl) {}

  virtual void do_thread(Thread* thread) {
    ZRootsIteratorCodeBlobClosure code_cl(_cl);
    thread->oops_do(_cl, ClassUnloading ? &code_cl : NULL);
    _cl->do_thread(thread);
  }
};

ZRootsIterator::ZRootsIterator() :
    _universe(this),
    _object_synchronizer(this),
    _management(this),
    _jvmti_export(this),
    _jvmti_weak_export(this),
    _system_dictionary(this),
    _threads(this),
    _code_cache(this) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZStatTimer timer(ZSubPhasePauseRootsSetup);
  Threads::change_thread_claim_token();
  COMPILER2_PRESENT(DerivedPointerTable::clear());
  if (ClassUnloading) {
    nmethod::oops_do_marking_prologue();
  } else {
    ZNMethod::oops_do_begin();
  }
}

ZRootsIterator::~ZRootsIterator() {
  ZStatTimer timer(ZSubPhasePauseRootsTeardown);
  ResourceMark rm;
  if (ClassUnloading) {
    nmethod::oops_do_marking_epilogue();
  } else {
    ZNMethod::oops_do_end();
  }
  JvmtiExport::gc_epilogue();

  COMPILER2_PRESENT(DerivedPointerTable::update_pointers());
  Threads::assert_all_threads_claimed();
}

void ZRootsIterator::do_universe(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsUniverse);
  Universe::oops_do(cl);
}

void ZRootsIterator::do_object_synchronizer(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsObjectSynchronizer);
  ObjectSynchronizer::oops_do(cl);
}

void ZRootsIterator::do_management(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsManagement);
  Management::oops_do(cl);
}

void ZRootsIterator::do_jvmti_export(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsJVMTIExport);
  JvmtiExport::oops_do(cl);
}

void ZRootsIterator::do_jvmti_weak_export(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsJVMTIWeakExport);
  AlwaysTrueClosure always_alive;
  JvmtiExport::weak_oops_do(&always_alive, cl);
}

void ZRootsIterator::do_system_dictionary(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsSystemDictionary);
  SystemDictionary::oops_do(cl);
}

void ZRootsIterator::do_threads(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsThreads);
  ResourceMark rm;
  ZRootsIteratorThreadClosure thread_cl(cl);
  Threads::possibly_parallel_threads_do(true, &thread_cl);
}

void ZRootsIterator::do_code_cache(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsCodeCache);
  ZNMethod::oops_do(cl);
}

void ZRootsIterator::oops_do(ZRootsIteratorClosure* cl, bool visit_jvmti_weak_export) {
  ZStatTimer timer(ZSubPhasePauseRoots);
  _universe.oops_do(cl);
  _object_synchronizer.oops_do(cl);
  _management.oops_do(cl);
  _jvmti_export.oops_do(cl);
  _system_dictionary.oops_do(cl);
  _threads.oops_do(cl);
  if (!ClassUnloading) {
    _code_cache.oops_do(cl);
  }
  if (visit_jvmti_weak_export) {
    _jvmti_weak_export.oops_do(cl);
  }
}

ZConcurrentRootsIterator::ZConcurrentRootsIterator(bool marking) :
    _marking(marking),
    _sts_joiner(marking /* active */),
    _jni_handles_iter(JNIHandles::global_handles()),
    _jni_handles(this),
    _class_loader_data_graph(this) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsSetup);
  if (_marking) {
    ClassLoaderDataGraph_lock->lock();
    ClassLoaderDataGraph::clear_claimed_marks();
  }
}

ZConcurrentRootsIterator::~ZConcurrentRootsIterator() {
  ZStatTimer timer(ZSubPhaseConcurrentRootsTeardown);
  if (_marking) {
    ClassLoaderDataGraph_lock->unlock();
  }
}

void ZConcurrentRootsIterator::do_jni_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsJNIHandles);
  _jni_handles_iter.oops_do(cl);
}

void ZConcurrentRootsIterator::do_class_loader_data_graph(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph);
  if (_marking) {
    CLDToOopClosure cld_cl(cl, ClassLoaderData::_claim_strong);
    ClassLoaderDataGraph::always_strong_cld_do(&cld_cl);
  } else {
    CLDToOopClosure cld_cl(cl, ClassLoaderData::_claim_none);
    ClassLoaderDataGraph::cld_do(&cld_cl);
  }
}

void ZConcurrentRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRoots);
  _jni_handles.oops_do(cl);
  _class_loader_data_graph.oops_do(cl);
}

ZWeakRootsIterator::ZWeakRootsIterator() :
    _jvmti_weak_export(this),
    _jfr_weak(this) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZStatTimer timer(ZSubPhasePauseWeakRootsSetup);
}

ZWeakRootsIterator::~ZWeakRootsIterator() {
  ZStatTimer timer(ZSubPhasePauseWeakRootsTeardown);
}

void ZWeakRootsIterator::do_jvmti_weak_export(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseWeakRootsJVMTIWeakExport);
  JvmtiExport::weak_oops_do(is_alive, cl);
}

void ZWeakRootsIterator::do_jfr_weak(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
#if INCLUDE_JFR
  ZStatTimer timer(ZSubPhasePauseWeakRootsJFRWeak);
  Jfr::weak_oops_do(is_alive, cl);
#endif
}

void ZWeakRootsIterator::weak_oops_do(BoolObjectClosure* is_alive, ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseWeakRoots);
  _jvmti_weak_export.weak_oops_do(is_alive, cl);
  _jfr_weak.weak_oops_do(is_alive, cl);
}

void ZWeakRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  AlwaysTrueClosure always_alive;
  weak_oops_do(&always_alive, cl);
}

ZConcurrentWeakRootsIterator::ZConcurrentWeakRootsIterator() :
    _vm_weak_handles_iter(SystemDictionary::vm_weak_oop_storage()),
    _jni_weak_handles_iter(JNIHandles::weak_global_handles()),
    _string_table_iter(StringTable::weak_storage()),
    _vm_weak_handles(this),
    _jni_weak_handles(this),
    _string_table(this) {
  StringTable::reset_dead_counter();
}

ZConcurrentWeakRootsIterator::~ZConcurrentWeakRootsIterator() {
  StringTable::finish_dead_counter();
}

void ZConcurrentWeakRootsIterator::do_vm_weak_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsVMWeakHandles);
  _vm_weak_handles_iter.oops_do(cl);
}

void ZConcurrentWeakRootsIterator::do_jni_weak_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsJNIWeakHandles);
  _jni_weak_handles_iter.oops_do(cl);
}

class ZStringTableDeadCounterClosure : public ZRootsIteratorClosure  {
private:
  ZRootsIteratorClosure* const _cl;
  size_t                       _ndead;

public:
  ZStringTableDeadCounterClosure(ZRootsIteratorClosure* cl) :
      _cl(cl),
      _ndead(0) {}

  ~ZStringTableDeadCounterClosure() {
    StringTable::inc_dead_counter(_ndead);
  }

  virtual void do_oop(oop* p) {
    _cl->do_oop(p);
    if (*p == NULL) {
      _ndead++;
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

void ZConcurrentWeakRootsIterator::do_string_table(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsStringTable);
  ZStringTableDeadCounterClosure counter_cl(cl);
  _string_table_iter.oops_do(&counter_cl);
}

void ZConcurrentWeakRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRoots);
  _vm_weak_handles.oops_do(cl);
  _jni_weak_handles.oops_do(cl);
  _string_table.oops_do(cl);
}

ZThreadRootsIterator::ZThreadRootsIterator() :
    _threads(this) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZStatTimer timer(ZSubPhasePauseRootsSetup);
  Threads::change_thread_claim_token();
}

ZThreadRootsIterator::~ZThreadRootsIterator() {
  ZStatTimer timer(ZSubPhasePauseRootsTeardown);
  Threads::assert_all_threads_claimed();
}

void ZThreadRootsIterator::do_threads(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsThreads);
  ResourceMark rm;
  Threads::possibly_parallel_oops_do(true, cl, NULL);
}

void ZThreadRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRoots);
  _threads.oops_do(cl);
}
