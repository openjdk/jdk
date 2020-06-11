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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zOopClosures.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/atomic.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
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
static const ZStatSubPhase ZSubPhasePauseRootsVMThread("Pause Roots VM Thread");
static const ZStatSubPhase ZSubPhasePauseRootsJavaThreads("Pause Roots Java Threads");
static const ZStatSubPhase ZSubPhasePauseRootsCodeCache("Pause Roots CodeCache");

static const ZStatSubPhase ZSubPhaseConcurrentRootsSetup("Concurrent Roots Setup");
static const ZStatSubPhase ZSubPhaseConcurrentRoots("Concurrent Roots");
static const ZStatSubPhase ZSubPhaseConcurrentRootsTeardown("Concurrent Roots Teardown");
static const ZStatSubPhase ZSubPhaseConcurrentRootsJNIHandles("Concurrent Roots JNIHandles");
static const ZStatSubPhase ZSubPhaseConcurrentRootsVMHandles("Concurrent Roots VMHandles");
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
static const ZStatSubPhase ZSubPhaseConcurrentWeakRootsResolvedMethodTable("Concurrent Weak Roots ResolvedMethodTable");

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
ZSerialOopsDo<T, F>::ZSerialOopsDo(T* iter) :
    _iter(iter),
    _claimed(false) {}

template <typename T, void (T::*F)(ZRootsIteratorClosure*)>
void ZSerialOopsDo<T, F>::oops_do(ZRootsIteratorClosure* cl) {
  if (!_claimed && Atomic::cmpxchg(&_claimed, false, true) == false) {
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
  if (!_claimed && Atomic::cmpxchg(&_claimed, false, true) == false) {
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

class ZRootsIteratorCodeBlobClosure : public CodeBlobClosure {
private:
  ZRootsIteratorClosure* const _cl;
  const bool                   _should_disarm_nmethods;

public:
  ZRootsIteratorCodeBlobClosure(ZRootsIteratorClosure* cl) :
      _cl(cl),
      _should_disarm_nmethods(cl->should_disarm_nmethods()) {}

  virtual void do_code_blob(CodeBlob* cb) {
    nmethod* const nm = cb->as_nmethod_or_null();
    if (nm != NULL && nm->oops_do_try_claim()) {
      ZNMethod::nmethod_oops_do(nm, _cl);
      assert(!ZNMethod::supports_entry_barrier(nm) ||
             ZNMethod::is_armed(nm) == _should_disarm_nmethods, "Invalid state");
      if (_should_disarm_nmethods) {
        ZNMethod::disarm(nm);
      }
    }
  }
};

class ZRootsIteratorThreadClosure : public ThreadClosure {
private:
  ZRootsIteratorClosure* const _cl;
  ResourceMark                 _rm;

public:
  ZRootsIteratorThreadClosure(ZRootsIteratorClosure* cl) :
      _cl(cl) {}

  virtual void do_thread(Thread* thread) {
    ZRootsIteratorCodeBlobClosure code_cl(_cl);
    thread->oops_do(_cl, ClassUnloading ? &code_cl : NULL);
    _cl->do_thread(thread);
  }
};

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

ZRootsIterator::ZRootsIterator(bool visit_jvmti_weak_export) :
    _visit_jvmti_weak_export(visit_jvmti_weak_export),
    _java_threads_iter(),
    _universe(this),
    _object_synchronizer(this),
    _management(this),
    _jvmti_export(this),
    _jvmti_weak_export(this),
    _vm_thread(this),
    _java_threads(this),
    _code_cache(this) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZStatTimer timer(ZSubPhasePauseRootsSetup);
  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::clear());
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

  COMPILER2_OR_JVMCI_PRESENT(DerivedPointerTable::update_pointers());
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

void ZRootsIterator::do_vm_thread(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsVMThread);
  ZRootsIteratorThreadClosure thread_cl(cl);
  thread_cl.do_thread(VMThread::vm_thread());
}

void ZRootsIterator::do_java_threads(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsJavaThreads);
  ZRootsIteratorThreadClosure thread_cl(cl);
  _java_threads_iter.threads_do(&thread_cl);
}

void ZRootsIterator::do_code_cache(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRootsCodeCache);
  ZNMethod::oops_do(cl);
}

void ZRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhasePauseRoots);
  _universe.oops_do(cl);
  _object_synchronizer.oops_do(cl);
  _management.oops_do(cl);
  _jvmti_export.oops_do(cl);
  _vm_thread.oops_do(cl);
  _java_threads.oops_do(cl);
  if (!ClassUnloading) {
    _code_cache.oops_do(cl);
  }
  if (_visit_jvmti_weak_export) {
    _jvmti_weak_export.oops_do(cl);
  }
}

ZConcurrentRootsIterator::ZConcurrentRootsIterator(int cld_claim) :
    _jni_handles_iter(OopStorageSet::jni_global()),
    _vm_handles_iter(OopStorageSet::vm_global()),
    _cld_claim(cld_claim),
    _jni_handles(this),
    _vm_handles(this),
    _class_loader_data_graph(this) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsSetup);
  ClassLoaderDataGraph::clear_claimed_marks(cld_claim);
}

ZConcurrentRootsIterator::~ZConcurrentRootsIterator() {
  ZStatTimer timer(ZSubPhaseConcurrentRootsTeardown);
}

void ZConcurrentRootsIterator::do_jni_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsJNIHandles);
  _jni_handles_iter.oops_do(cl);
}

void ZConcurrentRootsIterator::do_vm_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsVMHandles);
  _vm_handles_iter.oops_do(cl);
}

void ZConcurrentRootsIterator::do_class_loader_data_graph(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph);
  CLDToOopClosure cld_cl(cl, _cld_claim);
  ClassLoaderDataGraph::always_strong_cld_do(&cld_cl);
}

void ZConcurrentRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentRoots);
  _jni_handles.oops_do(cl);
  _vm_handles.oops_do(cl),
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
    _vm_weak_handles_iter(OopStorageSet::vm_weak()),
    _jni_weak_handles_iter(OopStorageSet::jni_weak()),
    _string_table_iter(OopStorageSet::string_table_weak()),
    _resolved_method_table_iter(OopStorageSet::resolved_method_table_weak()),
    _vm_weak_handles(this),
    _jni_weak_handles(this),
    _string_table(this),
    _resolved_method_table(this) {
  StringTable::reset_dead_counter();
  ResolvedMethodTable::reset_dead_counter();
}

ZConcurrentWeakRootsIterator::~ZConcurrentWeakRootsIterator() {
  StringTable::finish_dead_counter();
  ResolvedMethodTable::finish_dead_counter();
}

void ZConcurrentWeakRootsIterator::do_vm_weak_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsVMWeakHandles);
  _vm_weak_handles_iter.oops_do(cl);
}

void ZConcurrentWeakRootsIterator::do_jni_weak_handles(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsJNIWeakHandles);
  _jni_weak_handles_iter.oops_do(cl);
}

template <class Container>
class ZDeadCounterClosure : public ZRootsIteratorClosure  {
private:
  ZRootsIteratorClosure* const _cl;
  size_t                       _ndead;

public:
  ZDeadCounterClosure(ZRootsIteratorClosure* cl) :
      _cl(cl),
      _ndead(0) {}

  ~ZDeadCounterClosure() {
    Container::inc_dead_counter(_ndead);
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
  ZDeadCounterClosure<StringTable> counter_cl(cl);
  _string_table_iter.oops_do(&counter_cl);
}

void ZConcurrentWeakRootsIterator::do_resolved_method_table(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRootsResolvedMethodTable);
  ZDeadCounterClosure<ResolvedMethodTable> counter_cl(cl);
  _resolved_method_table_iter.oops_do(&counter_cl);
}

void ZConcurrentWeakRootsIterator::oops_do(ZRootsIteratorClosure* cl) {
  ZStatTimer timer(ZSubPhaseConcurrentWeakRoots);
  _vm_weak_handles.oops_do(cl);
  _jni_weak_handles.oops_do(cl);
  _string_table.oops_do(cl);
  _resolved_method_table.oops_do(cl);
}
