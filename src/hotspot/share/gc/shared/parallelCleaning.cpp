/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/parallelCleaning.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "oops/klass.inline.hpp"

CodeCacheUnloadingTask::CodeCacheUnloadingTask(uint num_workers, bool unloading_occurred) :
  _unloading_occurred(unloading_occurred),
  _num_workers(num_workers),
  _first_nmethod(nullptr),
  _claimed_nmethod(nullptr) {
  // Get first alive nmethod
  NMethodIterator iter(NMethodIterator::all);
  if(iter.next()) {
    _first_nmethod = iter.method();
  }
  _claimed_nmethod = _first_nmethod;
}

CodeCacheUnloadingTask::~CodeCacheUnloadingTask() {
  CodeCache::verify_clean_inline_caches();
}

void CodeCacheUnloadingTask::claim_nmethods(nmethod** claimed_nmethods, int *num_claimed_nmethods) {
  nmethod* first;
  NMethodIterator last(NMethodIterator::all);

  do {
    *num_claimed_nmethods = 0;

    first = _claimed_nmethod;
    last = NMethodIterator(NMethodIterator::all, first);

    if (first != nullptr) {

      for (int i = 0; i < MaxClaimNmethods; i++) {
        if (!last.next()) {
          break;
        }
        claimed_nmethods[i] = last.method();
        (*num_claimed_nmethods)++;
      }
    }

  } while (Atomic::cmpxchg(&_claimed_nmethod, first, last.method()) != first);
}

void CodeCacheUnloadingTask::work(uint worker_id) {
  jlong start = os::elapsed_counter();
  // The first nmethods is claimed by the first worker.
  if (worker_id == 0 && _first_nmethod != nullptr) {
    _first_nmethod->do_unloading(_unloading_occurred);
    _first_nmethod = nullptr;
  }

  int num_claimed_nmethods;
  nmethod* claimed_nmethods[MaxClaimNmethods];

  jlong clean = 0;
  while (true) {
    claim_nmethods(claimed_nmethods, &num_claimed_nmethods);

    if (num_claimed_nmethods == 0) {
      break;
    }
    jlong clean_start = os::elapsed_counter();
    for (int i = 0; i < num_claimed_nmethods; i++) {
      claimed_nmethods[i]->do_unloading(_unloading_occurred);
    }
    clean += os::elapsed_counter() - clean_start;
  }
  log_debug(gc)("CodeCleaning %d (%u): clean %.2fms total %.2fms", worker_id, worker_id == 0, TimeHelper::counter_to_millis(clean), TimeHelper::counter_to_millis(os::elapsed_counter() - start));
}

KlassCleaningTask::KlassCleaningTask() :
  _klass_iterator(), _processed(0), _num_clds_processed(0) {
}

KlassCleaningTask::~KlassCleaningTask() {
    log_debug(gc)("KlassCleaningTask cmpxchg-fail %u", _klass_iterator._cmpxchgfail);
}

void KlassCleaningTask::work(uint worker_id) {
  jlong start = os::elapsed_counter();
  jlong clean =0;
  bool clean_tree = false;

  uint num_processed = 0;
  uint num_ik = 0;
  uint num_clds_processed = 0;

  for (ClassLoaderData* cur = _klass_iterator.next(); cur != nullptr; cur = _klass_iterator.next()) {
      jlong start_clean = os::elapsed_counter();
    num_clds_processed++;
    for (Klass* klass = cur->klasses(); klass != nullptr; klass = klass->next_link()) {
      klass->clean_subklass();
      Klass* sibling = klass->next_sibling(true);
      klass->set_next_sibling(sibling);

      if (klass->is_instance_klass()) {
        Klass::clean_weak_instanceklass_links(InstanceKlass::cast(klass));
        num_ik++;
      }

      guarantee(klass->subklass(false) == nullptr || klass->subklass(false)->is_loader_alive(), "must be");
      guarantee(klass->next_sibling(false) == nullptr || klass->next_sibling(false)->is_loader_alive(), "must be");
      num_processed++;
    }
    clean += os::elapsed_counter() - start_clean;
  }
  Atomic::add(&_num_clds_processed, num_clds_processed);
  Atomic::add(&_processed, num_processed);
  
  log_debug(gc)("KlassCleaningTask %u (%d): processed %u clds %u ik %u clean %.2fms total %.2fms", worker_id, clean_tree, num_processed, num_clds_processed, num_ik, TimeHelper::counter_to_millis(clean), TimeHelper::counter_to_millis(os::elapsed_counter() - start));
}
