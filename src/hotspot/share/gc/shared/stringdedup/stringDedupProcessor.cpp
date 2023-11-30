/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/stringdedup/stringDedupProcessor.hpp"
#include "gc/shared/stringdedup/stringDedupStat.hpp"
#include "gc/shared/stringdedup/stringDedupStorageUse.hpp"
#include "gc/shared/stringdedup/stringDedupTable.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalCounter.hpp"
#include "utilities/globalDefinitions.hpp"

OopStorage* StringDedup::Processor::_storages[2] = {};

StringDedup::StorageUse* volatile StringDedup::Processor::_storage_for_requests = nullptr;
StringDedup::StorageUse* StringDedup::Processor::_storage_for_processing = nullptr;

void StringDedup::Processor::initialize_storage() {
  assert(_storages[0] == nullptr, "storage already created");
  assert(_storages[1] == nullptr, "storage already created");
  assert(_storage_for_requests == nullptr, "storage already created");
  assert(_storage_for_processing == nullptr, "storage already created");
  _storages[0] = OopStorageSet::create_weak("StringDedup Requests0 Weak", mtStringDedup);
  _storages[1] = OopStorageSet::create_weak("StringDedup Requests1 Weak", mtStringDedup);
  _storage_for_requests = new StorageUse(_storages[0]);
  _storage_for_processing = new StorageUse(_storages[1]);
}

StringDedup::Processor::Processor() : _thread(nullptr) {}

void StringDedup::Processor::initialize() {
  _processor = new Processor();
  CPUTimeCounters::create_counter(CPUTimeGroups::CPUTimeType::conc_dedup);
}

void StringDedup::Processor::wait_for_requests() const {
  assert(Thread::current() == _thread, "precondition");
  // Wait for the current request storage object to be non-empty, or for the
  // table to need cleanup.  The num-dead notification from the Table notifies
  // the monitor.
  {
    ThreadBlockInVM tbivm(_thread);
    MonitorLocker ml(StringDedup_lock, Mutex::_no_safepoint_check_flag);
    OopStorage* storage = Atomic::load(&_storage_for_requests)->storage();
    while ((storage->allocation_count() == 0) &&
           !Table::is_dead_entry_removal_needed()) {
      ml.wait();
    }
  }
  // Swap the request and processing storage objects.
  log_trace(stringdedup)("swapping request storages");
  _storage_for_processing = Atomic::xchg(&_storage_for_requests, _storage_for_processing);
  GlobalCounter::write_synchronize();
  // Wait for the now current processing storage object to no longer be used
  // by an in-progress GC.  Again here, the num-dead notification from the
  // Table notifies the monitor.
  {
    log_trace(stringdedup)("waiting for storage to process");
    ThreadBlockInVM tbivm(_thread);
    MonitorLocker ml(StringDedup_lock, Mutex::_no_safepoint_check_flag);
    while (_storage_for_processing->is_used_acquire()) {
      ml.wait();
    }
  }
}

StringDedup::StorageUse* StringDedup::Processor::storage_for_requests() {
  return StorageUse::obtain(&_storage_for_requests);
}

void StringDedup::Processor::yield() const {
  assert(Thread::current() == _thread, "precondition");
  ThreadBlockInVM tbivm(_thread);
}

void StringDedup::Processor::cleanup_table(bool grow_only, bool force) const {
  if (Table::cleanup_start_if_needed(grow_only, force)) {
    do {
      yield();
    } while (Table::cleanup_step());
    Table::cleanup_end();
  }
}

class StringDedup::Processor::ProcessRequest final : public OopClosure {
  OopStorage* _storage;
  size_t _release_index;
  oop* _bulk_release[OopStorage::bulk_allocate_limit];

  void release_ref(oop* ref) {
    assert(_release_index < ARRAY_SIZE(_bulk_release), "invariant");
    NativeAccess<ON_PHANTOM_OOP_REF>::oop_store(ref, nullptr);
    _bulk_release[_release_index++] = ref;
    if (_release_index == ARRAY_SIZE(_bulk_release)) {
      _storage->release(_bulk_release, _release_index);
      _release_index = 0;
    }
  }

public:
  ProcessRequest(OopStorage* storage) :
    _storage(storage),
    _release_index(0),
    _bulk_release()
  {}

  ~ProcessRequest() {
    _storage->release(_bulk_release, _release_index);
  }

  virtual void do_oop(narrowOop*) { ShouldNotReachHere(); }

  virtual void do_oop(oop* ref) {
    _processor->yield();
    oop java_string = NativeAccess<ON_PHANTOM_OOP_REF>::oop_load(ref);
    release_ref(ref);
    // Dedup java_string, after checking for various reasons to skip it.
    if (java_string == nullptr) {
      // String became unreachable before we got a chance to process it.
      _cur_stat.inc_skipped_dead();
    } else if (java_lang_String::value(java_string) == nullptr) {
      // Request during String construction, before its value array has
      // been initialized.
      _cur_stat.inc_skipped_incomplete();
    } else {
      Table::deduplicate(java_string);
      if (Table::is_grow_needed()) {
        _cur_stat.report_process_pause();
        _processor->cleanup_table(true /* grow_only */, false /* force */);
        _cur_stat.report_process_resume();
      }
    }
  }
};

void StringDedup::Processor::process_requests() const {
  _cur_stat.report_process_start();
  OopStorage::ParState<true, false> par_state{_storage_for_processing->storage(), 1};
  ProcessRequest processor{_storage_for_processing->storage()};
  par_state.oops_do(&processor);
  _cur_stat.report_process_end();
}

void StringDedup::Processor::run(JavaThread* thread) {
  assert(thread == Thread::current(), "precondition");
  _thread = thread;
  log_debug(stringdedup)("Starting string deduplication thread");
  while (true) {
    _cur_stat.report_idle_start();
    wait_for_requests();
    _cur_stat.report_idle_end();
    _cur_stat.report_active_start();
    process_requests();
    cleanup_table(false /* grow_only */, StringDeduplicationResizeALot /* force */);
    _cur_stat.report_active_end();
    log_statistics();
    if (UsePerfData && os::is_thread_cpu_time_supported()) {
      ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::conc_dedup);
      tttc.do_thread(thread);
    }
  }
}

void StringDedup::Processor::log_statistics() {
  _total_stat.add(&_cur_stat);
  Stat::log_summary(&_cur_stat, &_total_stat);
  if (log_is_enabled(Debug, stringdedup)) {
    _cur_stat.log_statistics(false);
    _total_stat.log_statistics(true);
    Table::log_statistics();
  }
  _cur_stat = Stat{};
}
