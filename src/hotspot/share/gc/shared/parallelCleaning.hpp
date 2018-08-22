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

#ifndef SHARE_VM_GC_SHARED_PARALLELCLEANING_HPP
#define SHARE_VM_GC_SHARED_PARALLELCLEANING_HPP

#include "gc/shared/oopStorageParState.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/workgroup.hpp"

class ParallelCleaningTask;

class StringCleaningTask : public AbstractGangTask {
private:
  BoolObjectClosure* _is_alive;
  StringDedupUnlinkOrOopsDoClosure * const _dedup_closure;

  OopStorage::ParState<false /* concurrent */, false /* const */> _par_state_string;

  int _initial_string_table_size;

  bool  _process_strings;
  int   _strings_processed;
  int   _strings_removed;

public:
  StringCleaningTask(BoolObjectClosure* is_alive, StringDedupUnlinkOrOopsDoClosure* dedup_closure, bool process_strings);
  ~StringCleaningTask();

  void work(uint worker_id);

  size_t strings_processed() const { return (size_t)_strings_processed; }
  size_t strings_removed()   const { return (size_t)_strings_removed; }
};

class CodeCacheUnloadingTask {
private:
  static Monitor* _lock;

  BoolObjectClosure* const _is_alive;
  const bool               _unloading_occurred;
  const uint               _num_workers;

  // Variables used to claim nmethods.
  CompiledMethod* _first_nmethod;
  CompiledMethod* volatile _claimed_nmethod;

  // The list of nmethods that need to be processed by the second pass.
  CompiledMethod* volatile _postponed_list;
  volatile uint            _num_entered_barrier;

public:
  CodeCacheUnloadingTask(uint num_workers, BoolObjectClosure* is_alive, bool unloading_occurred);
  ~CodeCacheUnloadingTask();

private:
  void add_to_postponed_list(CompiledMethod* nm);
  void clean_nmethod(CompiledMethod* nm);
  void clean_nmethod_postponed(CompiledMethod* nm);

  static const int MaxClaimNmethods = 16;

  void claim_nmethods(CompiledMethod** claimed_nmethods, int *num_claimed_nmethods);
  CompiledMethod* claim_postponed_nmethod();
public:
  // Mark that we're done with the first pass of nmethod cleaning.
  void barrier_mark(uint worker_id);

  // See if we have to wait for the other workers to
  // finish their first-pass nmethod cleaning work.
  void barrier_wait(uint worker_id);

  // Cleaning and unloading of nmethods. Some work has to be postponed
  // to the second pass, when we know which nmethods survive.
  void work_first_pass(uint worker_id);
  void work_second_pass(uint worker_id);
};


class KlassCleaningTask : public StackObj {
  volatile int                            _clean_klass_tree_claimed;
  ClassLoaderDataGraphKlassIteratorAtomic _klass_iterator;

public:
  KlassCleaningTask();

private:
  bool claim_clean_klass_tree_task();
  InstanceKlass* claim_next_klass();

public:

  void clean_klass(InstanceKlass* ik) {
    ik->clean_weak_instanceklass_links();
  }

  void work();
};

class ResolvedMethodCleaningTask : public StackObj {
  volatile int       _resolved_method_task_claimed;
public:
  ResolvedMethodCleaningTask() :
      _resolved_method_task_claimed(0) {}

  bool claim_resolved_method_task();

  void work();
};

class ParallelCleaningTimes {
  friend class ParallelCleaningTask;
private:
  // All times are in microseconds, making room for ~2 hrs in jint
  jint _sync, _codecache_work, _tables_work, _rmt_work, _klass_work;

public:
  ParallelCleaningTimes() :
          _sync(0),
          _codecache_work(0),
          _tables_work(0),
          _rmt_work(0),
          _klass_work(0) {};

  jint sync_us()           const { return _sync; }
  jint codecache_work_us() const { return _codecache_work; }
  jint tables_work_us()    const { return _tables_work; }
  jint rmt_work_us()       const { return _rmt_work; }
  jint klass_work_us()     const { return _klass_work; }
};

// To minimize the remark pause times, the tasks below are done in parallel.
class ParallelCleaningTask : public AbstractGangTask {
private:
  bool                        _unloading_occurred;
  StringCleaningTask          _string_task;
  CodeCacheUnloadingTask      _code_cache_task;
  KlassCleaningTask           _klass_cleaning_task;
  ResolvedMethodCleaningTask  _resolved_method_cleaning_task;

  ParallelCleaningTimes       _times;
public:
  // The constructor is run in the VMThread.
  ParallelCleaningTask(BoolObjectClosure* is_alive, StringDedupUnlinkOrOopsDoClosure* dedup_closure,
    uint num_workers, bool unloading_occurred);

  void work(uint worker_id);

  ParallelCleaningTimes times() const {
    return _times;
  }
};

#endif // SHARE_VM_GC_SHARED_PARALLELCLEANING_HPP
