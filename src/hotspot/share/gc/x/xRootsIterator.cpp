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
#include "gc/x/xNMethod.hpp"
#include "gc/x/xNMethodTable.hpp"
#include "gc/x/xRootsIterator.hpp"
#include "gc/x/xStat.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

static const XStatSubPhase XSubPhaseConcurrentRootsOopStorageSet("Concurrent Roots OopStorageSet");
static const XStatSubPhase XSubPhaseConcurrentRootsClassLoaderDataGraph("Concurrent Roots ClassLoaderDataGraph");
static const XStatSubPhase XSubPhaseConcurrentRootsJavaThreads("Concurrent Roots JavaThreads");
static const XStatSubPhase XSubPhaseConcurrentRootsCodeCache("Concurrent Roots CodeCache");
static const XStatSubPhase XSubPhaseConcurrentWeakRootsOopStorageSet("Concurrent Weak Roots OopStorageSet");

template <typename Iterator>
template <typename ClosureType>
void XParallelApply<Iterator>::apply(ClosureType* cl) {
  if (!Atomic::load(&_completed)) {
    _iter.apply(cl);
    if (!Atomic::load(&_completed)) {
      Atomic::store(&_completed, true);
    }
  }
}

XStrongOopStorageSetIterator::XStrongOopStorageSetIterator() :
    _iter() {}

void XStrongOopStorageSetIterator::apply(OopClosure* cl) {
  XStatTimer timer(XSubPhaseConcurrentRootsOopStorageSet);
  _iter.oops_do(cl);
}

void XStrongCLDsIterator::apply(CLDClosure* cl) {
  XStatTimer timer(XSubPhaseConcurrentRootsClassLoaderDataGraph);
  ClassLoaderDataGraph::always_strong_cld_do(cl);
}

XJavaThreadsIterator::XJavaThreadsIterator() :
    _threads(),
    _claimed(0) {}

uint XJavaThreadsIterator::claim() {
  return Atomic::fetch_then_add(&_claimed, 1u);
}

void XJavaThreadsIterator::apply(ThreadClosure* cl) {
  XStatTimer timer(XSubPhaseConcurrentRootsJavaThreads);

  // The resource mark is needed because interpreter oop maps are
  // not reused in concurrent mode. Instead, they are temporary and
  // resource allocated.
  ResourceMark                 _rm;

  for (uint i = claim(); i < _threads.length(); i = claim()) {
    cl->do_thread(_threads.thread_at(i));
  }
}

XNMethodsIterator::XNMethodsIterator() {
  if (!ClassUnloading) {
    XNMethod::nmethods_do_begin();
  }
}

XNMethodsIterator::~XNMethodsIterator() {
  if (!ClassUnloading) {
    XNMethod::nmethods_do_end();
  }
}

void XNMethodsIterator::apply(NMethodClosure* cl) {
  XStatTimer timer(XSubPhaseConcurrentRootsCodeCache);
  XNMethod::nmethods_do(cl);
}

XRootsIterator::XRootsIterator(int cld_claim) {
  if (cld_claim != ClassLoaderData::_claim_none) {
    ClassLoaderDataGraph::verify_claimed_marks_cleared(cld_claim);
  }
}

void XRootsIterator::apply(OopClosure* cl,
                           CLDClosure* cld_cl,
                           ThreadClosure* thread_cl,
                           NMethodClosure* nm_cl) {
  _oop_storage_set.apply(cl);
  _class_loader_data_graph.apply(cld_cl);
  _java_threads.apply(thread_cl);
  if (!ClassUnloading) {
    _nmethods.apply(nm_cl);
  }
}

XWeakOopStorageSetIterator::XWeakOopStorageSetIterator() :
    _iter() {}

void XWeakOopStorageSetIterator::apply(OopClosure* cl) {
  XStatTimer timer(XSubPhaseConcurrentWeakRootsOopStorageSet);
  _iter.oops_do(cl);
}

void XWeakOopStorageSetIterator::report_num_dead() {
  _iter.report_num_dead();
}

void XWeakRootsIterator::report_num_dead() {
  _oop_storage_set.iter().report_num_dead();
}

void XWeakRootsIterator::apply(OopClosure* cl) {
  _oop_storage_set.apply(cl);
}
