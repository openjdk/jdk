/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.inline.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/growableArray.hpp"

ClassUnloadingContext* ClassUnloadingContext::_context = nullptr;

ClassUnloadingContext::ClassUnloadingContext(uint num_workers,
                                             bool unregister_nmethods_during_purge,
                                             bool lock_nmethod_free_separately) :
  _cld_head(nullptr),
  _num_nmethod_unlink_workers(num_workers),
  _unlinked_nmethods(nullptr),
  _unregister_nmethods_during_purge(unregister_nmethods_during_purge),
  _lock_nmethod_free_separately(lock_nmethod_free_separately) {

  assert(_context == nullptr, "context already set");
  _context = this;

  assert(num_workers > 0, "must be");

  _unlinked_nmethods = NEW_C_HEAP_ARRAY(NMethodSet*, num_workers, mtGC);
  for (uint i = 0; i < num_workers; ++i) {
    _unlinked_nmethods[i] = new NMethodSet();
  }
}

ClassUnloadingContext::~ClassUnloadingContext() {
  for (uint i = 0; i < _num_nmethod_unlink_workers; ++i) {
    delete _unlinked_nmethods[i];
  }
  FREE_C_HEAP_ARRAY(NMethodSet*, _unlinked_nmethods);

  assert(_context == this, "context not set correctly");
  _context = nullptr;
}

bool ClassUnloadingContext::has_unloaded_classes() const {
  return _cld_head != nullptr;
}

void ClassUnloadingContext::register_unloading_class_loader_data(ClassLoaderData* cld) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  cld->unload();

  cld->set_unloading_next(_cld_head);
  _cld_head = cld;
}

void ClassUnloadingContext::purge_class_loader_data() {
  for (ClassLoaderData* cld = _cld_head; cld != nullptr;) {
    assert(cld->is_unloading(), "invariant");

    ClassLoaderData* next = cld->unloading_next();
    delete cld;
    cld = next;
  }
}

void ClassUnloadingContext::classes_unloading_do(void f(Klass* const)) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  for (ClassLoaderData* cld = _cld_head; cld != nullptr; cld = cld->unloading_next()) {
    assert(cld->is_unloading(), "invariant");
    cld->classes_do(f);
  }
}

void ClassUnloadingContext::register_unlinked_nmethod(nmethod* nm) {
  assert(_context != nullptr, "no context set");

  assert(!nm->is_unlinked(), "Only register for unloading once");
  assert(_num_nmethod_unlink_workers == 1 || Thread::current()->is_Worker_thread(), "must be worker thread if parallel");

  uint worker_id = _num_nmethod_unlink_workers == 1 ? 0 : WorkerThread::worker_id();
  assert(worker_id < _num_nmethod_unlink_workers, "larger than expected worker id %u", worker_id);

  _unlinked_nmethods[worker_id]->append(nm);

  nm->set_is_unlinked();
}

void ClassUnloadingContext::purge_nmethods() {
  assert(_context != nullptr, "no context set");

  size_t freed_memory = 0;

  for (uint i = 0; i < _num_nmethod_unlink_workers; ++i) {
    NMethodSet* set = _unlinked_nmethods[i];
    for (nmethod* nm : *set) {
      freed_memory += nm->size();
      nm->purge(_unregister_nmethods_during_purge);
    }
  }

  CodeCache::maybe_restart_compiler(freed_memory);
}

void ClassUnloadingContext::free_nmethods() {
  assert(_context != nullptr, "no context set");

  // Sort nmethods before freeing to benefit from optimizations. If Nmethods were
  // collected in parallel, use a new temporary buffer for the result, otherwise
  // sort in-place.
  NMethodSet* nmethod_set = nullptr;

  bool is_parallel = _num_nmethod_unlink_workers > 1;

  // Merge all collected nmethods into a huge array.
  if (is_parallel) {
    int num_nmethods = 0;

    for (uint i = 0; i < _num_nmethod_unlink_workers; ++i) {
      num_nmethods += _unlinked_nmethods[i]->length();
    }
    nmethod_set = new NMethodSet(num_nmethods);
    for (uint i = 0; i < _num_nmethod_unlink_workers; ++i) {
      nmethod_set->appendAll(_unlinked_nmethods[i]);
    }
  } else {
    nmethod_set = _unlinked_nmethods[0];
  }

  // Sort by ascending address.
  auto sort_nmethods = [] (nmethod** a, nmethod** b) -> int {
    uintptr_t u_a = (uintptr_t)*a;
    uintptr_t u_b = (uintptr_t)*b;
    if (u_a == u_b) return 0;
    if (u_a < u_b) return -1;
    return 1;
  };
  nmethod_set->sort(sort_nmethods);

  // And free. Duplicate loop for clarity depending on where we want the locking.
  if (_lock_nmethod_free_separately) {
    for (nmethod* nm : *nmethod_set) {
      MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      CodeCache::free(nm);
    }
  } else {
    MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    for (nmethod* nm : *nmethod_set) {
      CodeCache::free(nm);
    }
  }

  if (is_parallel) {
    delete nmethod_set;
  }
}
