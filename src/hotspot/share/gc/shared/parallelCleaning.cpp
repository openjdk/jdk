/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/parallelCleaning.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "logging/log.hpp"

StringCleaningTask::StringCleaningTask(BoolObjectClosure* is_alive, StringDedupUnlinkOrOopsDoClosure* dedup_closure, bool process_strings) :
  AbstractGangTask("String Unlinking"),
  _is_alive(is_alive),
  _dedup_closure(dedup_closure),
  _par_state_string(StringTable::weak_storage()),
  _initial_string_table_size((int) StringTable::the_table()->table_size()),
  _process_strings(process_strings), _strings_processed(0), _strings_removed(0) {

  if (process_strings) {
    StringTable::reset_dead_counter();
  }
}

StringCleaningTask::~StringCleaningTask() {
  log_info(gc, stringtable)(
      "Cleaned string table, "
      "strings: " SIZE_FORMAT " processed, " SIZE_FORMAT " removed",
      strings_processed(), strings_removed());
  if (_process_strings) {
    StringTable::finish_dead_counter();
  }
}

void StringCleaningTask::work(uint worker_id) {
  size_t strings_processed = 0;
  size_t strings_removed = 0;
  if (_process_strings) {
    StringTable::possibly_parallel_unlink(&_par_state_string, _is_alive, &strings_processed, &strings_removed);
    Atomic::add(strings_processed, &_strings_processed);
    Atomic::add(strings_removed, &_strings_removed);
  }
  if (_dedup_closure != NULL) {
    StringDedup::parallel_unlink(_dedup_closure, worker_id);
  }
}

CodeCacheUnloadingTask::CodeCacheUnloadingTask(uint num_workers, BoolObjectClosure* is_alive, bool unloading_occurred) :
      _unloading_scope(is_alive),
      _unloading_occurred(unloading_occurred),
      _num_workers(num_workers),
      _first_nmethod(NULL),
      _claimed_nmethod(NULL) {
  // Get first alive nmethod
  CompiledMethodIterator iter(CompiledMethodIterator::only_alive);
  if(iter.next()) {
    _first_nmethod = iter.method();
  }
  _claimed_nmethod = _first_nmethod;
}

CodeCacheUnloadingTask::~CodeCacheUnloadingTask() {
  CodeCache::verify_clean_inline_caches();

  guarantee(CodeCache::scavenge_root_nmethods() == NULL, "Must be");

  CodeCache::verify_icholder_relocations();
}

void CodeCacheUnloadingTask::claim_nmethods(CompiledMethod** claimed_nmethods, int *num_claimed_nmethods) {
  CompiledMethod* first;
  CompiledMethodIterator last(CompiledMethodIterator::only_alive);

  do {
    *num_claimed_nmethods = 0;

    first = _claimed_nmethod;
    last = CompiledMethodIterator(CompiledMethodIterator::only_alive, first);

    if (first != NULL) {

      for (int i = 0; i < MaxClaimNmethods; i++) {
        if (!last.next()) {
          break;
        }
        claimed_nmethods[i] = last.method();
        (*num_claimed_nmethods)++;
      }
    }

  } while (Atomic::cmpxchg(last.method(), &_claimed_nmethod, first) != first);
}

void CodeCacheUnloadingTask::work(uint worker_id) {
  // The first nmethods is claimed by the first worker.
  if (worker_id == 0 && _first_nmethod != NULL) {
    _first_nmethod->do_unloading(_unloading_occurred);
    _first_nmethod = NULL;
  }

  int num_claimed_nmethods;
  CompiledMethod* claimed_nmethods[MaxClaimNmethods];

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

KlassCleaningTask::KlassCleaningTask() :
  _clean_klass_tree_claimed(0),
  _klass_iterator() {
}

bool KlassCleaningTask::claim_clean_klass_tree_task() {
  if (_clean_klass_tree_claimed) {
    return false;
  }

  return Atomic::cmpxchg(1, &_clean_klass_tree_claimed, 0) == 0;
}

InstanceKlass* KlassCleaningTask::claim_next_klass() {
  Klass* klass;
  do {
    klass =_klass_iterator.next_klass();
  } while (klass != NULL && !klass->is_instance_klass());

  // this can be null so don't call InstanceKlass::cast
  return static_cast<InstanceKlass*>(klass);
}

void KlassCleaningTask::work() {
  ResourceMark rm;

  // One worker will clean the subklass/sibling klass tree.
  if (claim_clean_klass_tree_task()) {
    Klass::clean_subklass_tree();
  }

  // All workers will help cleaning the classes,
  InstanceKlass* klass;
  while ((klass = claim_next_klass()) != NULL) {
    clean_klass(klass);
  }
}

ParallelCleaningTask::ParallelCleaningTask(BoolObjectClosure* is_alive,
  StringDedupUnlinkOrOopsDoClosure* dedup_closure, uint num_workers, bool unloading_occurred) :
  AbstractGangTask("Parallel Cleaning"),
  _unloading_occurred(unloading_occurred),
  _string_task(is_alive, StringDedup::is_enabled() ? dedup_closure : NULL, true),
  _code_cache_task(num_workers, is_alive, unloading_occurred),
  _klass_cleaning_task() {
}

// The parallel work done by all worker threads.
void ParallelCleaningTask::work(uint worker_id) {
  // Do first pass of code cache cleaning.
  _code_cache_task.work(worker_id);

  // Clean the Strings and Symbols.
  _string_task.work(worker_id);

  // Clean all klasses that were not unloaded.
  // The weak metadata in klass doesn't need to be
  // processed if there was no unloading.
  if (_unloading_occurred) {
    _klass_cleaning_task.work();
  }
}
