/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/klass.inline.hpp"
#include "runtime/atomicAccess.hpp"

CodeCacheUnloadingTask::CodeCacheUnloadingTask(bool unloading_occurred) :
  _unloading_occurred(unloading_occurred),
  _first_nmethod(nullptr),
  _claimed_nmethod(nullptr) {
  // Get first alive nmethod
  NMethodIterator iter(NMethodIterator::all);
  if(iter.next()) {
    _first_nmethod = iter.method();
  }
  _claimed_nmethod.store_relaxed(_first_nmethod);
}

CodeCacheUnloadingTask::~CodeCacheUnloadingTask() {
  CodeCache::verify_clean_inline_caches();
}

void CodeCacheUnloadingTask::claim_nmethods(nmethod** claimed_nmethods, int *num_claimed_nmethods) {
  nmethod* first;
  NMethodIterator last(NMethodIterator::all);

  do {
    *num_claimed_nmethods = 0;

    first = _claimed_nmethod.load_relaxed();
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

  } while (!_claimed_nmethod.compare_set(first, last.method()));
}

void CodeCacheUnloadingTask::work(uint worker_id) {
  // The first nmethods is claimed by the first worker.
  if (worker_id == 0 && _first_nmethod != nullptr) {
    _first_nmethod->do_unloading(_unloading_occurred);
    _first_nmethod = nullptr;
  }

  int num_claimed_nmethods;
  nmethod* claimed_nmethods[MaxClaimNmethods];

  while (true) {
    claim_nmethods(claimed_nmethods, &num_claimed_nmethods);

    if (num_claimed_nmethods == 0) {
      break;
    }

    for (int i = 0; i < num_claimed_nmethods; i++) {
      claimed_nmethods[i]->do_unloading(_unloading_occurred);
    }
  }
}

void KlassCleaningTask::work() {
  for (ClassLoaderData* cur = _cld_iterator_atomic.next(); cur != nullptr; cur = _cld_iterator_atomic.next()) {
      class CleanKlasses : public KlassClosure {
      public:

        void do_klass(Klass* klass) override {
          klass->clean_subklass(true);

          Klass* sibling = klass->next_sibling(true);
          klass->set_next_sibling(sibling);

          if (klass->is_instance_klass()) {
            Klass::clean_weak_instanceklass_links(InstanceKlass::cast(klass));
          }

          assert(klass->subklass() == nullptr || klass->subklass()->is_loader_alive(), "must be");
          assert(klass->next_sibling(false) == nullptr || klass->next_sibling(false)->is_loader_alive(), "must be");
        }
      } cl;

      cur->classes_do(&cl);
  }
}
