/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zNMethodTable.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

class ZRootStatSubPhase {
private:
  ZStatSubPhase _young;
  ZStatSubPhase _old;

public:
  ZRootStatSubPhase(const char* name)
    : _young(name, ZGenerationId::young),
      _old(name, ZGenerationId::old) {}

  const ZStatSubPhase& young() const { return _young; }
  const ZStatSubPhase& old() const { return _old; }
};

static const ZRootStatSubPhase ZSubPhaseConcurrentRootsOopStorageSet("Concurrent Roots OopStorageSet");
static const ZRootStatSubPhase ZSubPhaseConcurrentRootsClassLoaderDataGraph("Concurrent Roots ClassLoaderDataGraph");
static const ZRootStatSubPhase ZSubPhaseConcurrentRootsJavaThreads("Concurrent Roots JavaThreads");
static const ZRootStatSubPhase ZSubPhaseConcurrentRootsCodeCache("Concurrent Roots CodeCache");
static const ZRootStatSubPhase ZSubPhaseConcurrentWeakRootsOopStorageSet("Concurrent Weak Roots OopStorageSet");

class ZRootStatTimer {
private:
  const ZStatPhase*  _phase;
  const Ticks        _start;

  ZRootStatTimer(const ZStatPhase* phase)
    : _phase(phase),
      _start(Ticks::now()) {
    if (phase != nullptr) {
      _phase->register_start(nullptr /* timer */, _start);
    }
  }

public:
  ~ZRootStatTimer() {
    if (_phase != nullptr) {
      const Ticks end = Ticks::now();
      _phase->register_end(nullptr /* timer */, _start, end);
    }
  }

  static const ZStatSubPhase* calculate_subphase(const ZGenerationIdOptional generation, const ZRootStatSubPhase& subphase) {
    switch (generation) {
      case ZGenerationIdOptional::young: return &subphase.young();
      case ZGenerationIdOptional::old: return &subphase.old();
      default: return nullptr;
    }
  }

public:
  ZRootStatTimer(const ZRootStatSubPhase& subphase, const ZGenerationIdOptional generation)
    : ZRootStatTimer(calculate_subphase(generation, subphase)) {}
};

template <typename Iterator>
template <typename ClosureType>
void ZParallelApply<Iterator>::apply(ClosureType* cl) {
  if (!Atomic::load(&_completed)) {
    _iter.apply(cl);
    if (!Atomic::load(&_completed)) {
      Atomic::store(&_completed, true);
    }
  }
}

void ZOopStorageSetIteratorStrong::apply(OopClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsOopStorageSet, _generation);
  _iter.oops_do(cl);
}

void ZCLDsIteratorStrong::apply(CLDClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph, _generation);
  ClassLoaderDataGraph::always_strong_cld_do(cl);
}

void ZCLDsIteratorWeak::apply(CLDClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph, _generation);
  ClassLoaderDataGraph::roots_cld_do(nullptr /* strong */, cl /* weak */);
}

void ZCLDsIteratorAll::apply(CLDClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsClassLoaderDataGraph, _generation);
  ClassLoaderDataGraph::cld_do(cl);
}

uint ZJavaThreadsIterator::claim() {
  return Atomic::fetch_then_add(&_claimed, 1u);
}

void ZJavaThreadsIterator::apply(ThreadClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsJavaThreads, _generation);

  // The resource mark is needed because interpreter oop maps are
  // not reused in concurrent mode. Instead, they are temporary and
  // resource allocated.
  ResourceMark rm;

  for (uint i = claim(); i < _threads.length(); i = claim()) {
    cl->do_thread(_threads.thread_at(i));
  }
}

ZNMethodsIteratorImpl::ZNMethodsIteratorImpl(ZGenerationIdOptional generation, bool enabled, bool secondary)
  : _enabled(enabled),
    _secondary(secondary),
    _generation(generation) {
  if (_enabled) {
    ZNMethod::nmethods_do_begin(secondary);
  }
}

ZNMethodsIteratorImpl::~ZNMethodsIteratorImpl() {
  if (_enabled) {
    ZNMethod::nmethods_do_end(_secondary);
  }
}

void ZNMethodsIteratorImpl::apply(NMethodClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentRootsCodeCache, _generation);
  ZNMethod::nmethods_do(_secondary, cl);
}

void ZRootsIteratorStrongColored::apply(OopClosure* cl,
                                  CLDClosure* cld_cl) {
  _oop_storage_set_strong.apply(cl);
  _clds_strong.apply(cld_cl);
}

void ZRootsIteratorStrongUncolored::apply(ThreadClosure* thread_cl,
                                          NMethodClosure* nm_cl) {
  _java_threads.apply(thread_cl);
  if (!ClassUnloading) {
    _nmethods_strong.apply(nm_cl);
  }
}

void ZRootsIteratorWeakUncolored::apply(NMethodClosure* nm_cl) {
  _nmethods_weak.apply(nm_cl);
}

void ZOopStorageSetIteratorWeak::apply(OopClosure* cl) {
  ZRootStatTimer timer(ZSubPhaseConcurrentWeakRootsOopStorageSet, _generation);
  _iter.oops_do(cl);
}

void ZOopStorageSetIteratorWeak::report_num_dead() {
  _iter.report_num_dead();
}

void ZRootsIteratorWeakColored::report_num_dead() {
  _oop_storage_set_weak.iter().report_num_dead();
}

void ZRootsIteratorWeakColored::apply(OopClosure* cl) {
  _oop_storage_set_weak.apply(cl);
}

void ZRootsIteratorAllColored::apply(OopClosure* cl,
                                     CLDClosure* cld_cl) {
  _oop_storage_set_strong.apply(cl);
  _oop_storage_set_weak.apply(cl);
  _clds_all.apply(cld_cl);
}

void ZRootsIteratorAllUncolored::apply(ThreadClosure* thread_cl,
                                       NMethodClosure* nm_cl) {
  _java_threads.apply(thread_cl);
  _nmethods_all.apply(nm_cl);
}
