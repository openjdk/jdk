/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#if INCLUDE_MANAGEMENT
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vm_version.hpp"
#include "services/finalizerTable.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/debug.hpp"

static inline void atomic_inc(uint64_t* volatile dest) {
  assert(VM_Version::supports_cx8(), "invariant");
  uint64_t compare;
  uint64_t exchange;
  do {
    compare = *dest;
    exchange = compare + 1;
  } while (Atomic::cmpxchg(dest, compare, exchange) != compare);
}

const InstanceKlass* FinalizerEntry::klass() const {
  return _ik;
}

uint64_t FinalizerEntry::completed() const {
  return Atomic::load(&_completed);
}

uint64_t FinalizerEntry::registered() const {
  return Atomic::load(&_registered);
}

void FinalizerEntry::on_register() {
  atomic_inc(&_registered);
}

void FinalizerEntry::on_complete() {
  atomic_inc(&_completed);
}

static constexpr const size_t DEFAULT_TABLE_SIZE = 2048;
// 2^24 is max size, like StringTable.
static constexpr const size_t MAX_SIZE = 24;
// If a chain gets to 50, something might be wrong
static constexpr const size_t REHASH_LEN = 50;
static constexpr const double PREF_AVG_LIST_LEN = 8.0;

static size_t _table_size = 0;
static volatile uint64_t _entries = 0;
static volatile uint64_t _count = 0;
static volatile bool _has_work = 0;
static volatile bool _needs_rehashing = false;
static volatile bool _has_items_to_clean = false;

static inline void reset_has_items_to_clean() {
  Atomic::store(&_has_items_to_clean, false);
}

static inline void set_has_items_to_clean() {
  Atomic::store(&_has_items_to_clean, true);
}

static inline bool has_items_to_clean() {
  return Atomic::load(&_has_items_to_clean);
}

static inline void added() {
  Atomic::inc(&_count);
}

static inline void removed() {
  Atomic::dec(&_count);
}

static inline uintx hash_function(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  return primitive_hash(ik);
}

static inline uintx hash_function(const FinalizerEntry* fe) {
  return hash_function(fe->klass());
}

class FinalizerEntryLookup : StackObj {
 private:
  const InstanceKlass* const _ik;
 public:
  FinalizerEntryLookup(const InstanceKlass* ik) : _ik(ik) {}
  uintx get_hash() const { return hash_function(_ik); }
  bool equals(FinalizerEntry** value, bool* is_dead) {
    assert(value != nullptr, "invariant");
    assert(*value != nullptr, "invariant");
    return (*value)->klass() == _ik;
  }
};

class FinalizerTableConfig : public AllStatic {
 public:
  typedef FinalizerEntry* Value;  // value of the Node in the hashtable

  static uintx get_hash(Value const& value, bool* is_dead) {
    return hash_function(value);
  }
  // We use default allocation/deallocation but counted
  static void* allocate_node(void* context, size_t size, Value const& value) {
    added();
    return AllocateHeap(size, mtClass);
  }
  static void free_node(void* context, void* memory, Value const& value) {
    // We get here because some threads lost a race to insert a newly created FinalizerEntry
    FreeHeap(memory);
    removed();
  }
};

typedef ConcurrentHashTable<FinalizerTableConfig, mtClass> FinalizerHashtable;
static FinalizerHashtable* _table = nullptr;

static size_t ceil_log2(size_t value) {
  size_t ret;
  for (ret = 1; ((size_t)1 << ret) < value; ++ret);
  return ret;
}

static double table_load_factor() {
  return (double)_count / _table_size;
}

static inline size_t table_size() {
  return ((size_t)1) << _table->get_size_log2(Thread::current());
}

static inline bool table_needs_rehashing() {
  return _needs_rehashing;
}

static inline void update_table_needs_rehash(bool rehash) {
  if (rehash) {
    _needs_rehashing = true;
  }
}

class FinalizerEntryLookupResult {
 private:
  FinalizerEntry* _result;
 public:
  FinalizerEntryLookupResult() : _result(nullptr) {}
  void operator()(FinalizerEntry* node) {
    assert(node != nullptr, "invariant");
    _result = node;
  }
  FinalizerEntry* result() const { return _result; }
};

class FinalizerEntryLookupGet {
 private:
  FinalizerEntry* _result;
 public:
  FinalizerEntryLookupGet() : _result(nullptr) {}
  void operator()(FinalizerEntry** node) {
    assert(node != nullptr, "invariant");
    _result = *node;
  }
  FinalizerEntry* result() const { return _result; }
};

static void trigger_table_cleanup() {
  MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  _has_work = true;
  Service_lock->notify_all();
}

static void check_table_concurrent_work() {
  if (_has_work) {
    return;
  }
  // We should clean/resize if we have
  // more items than preferred load factor or
  // more dead items than water mark.
  if (has_items_to_clean() || (table_load_factor() > PREF_AVG_LIST_LEN)) {
    trigger_table_cleanup();
  }
}

static FinalizerEntry* add_to_table_if_needed(const InstanceKlass* ik, Thread* thread) {
  FinalizerEntryLookup lookup(ik);
  bool clean_hint = false;
  bool rehash_warning = false;
  FinalizerEntry* entry = nullptr;
  do {
    // We have looked up the entry once, proceed with insertion.
    entry = new FinalizerEntry(ik);
    if (_table->insert(thread, lookup, entry, &rehash_warning, &clean_hint)) {
      break;
    }
    // In case another thread did a concurrent add, return value already in the table.
    // This could fail if the entry got deleted concurrently, so loop back until success.
    FinalizerEntryLookupGet felg;
    if (_table->get(thread, lookup, felg, &rehash_warning)) {
      entry = felg.result();
      break;
    }
  } while (true);
  update_table_needs_rehash(rehash_warning);
  if (clean_hint) {
    set_has_items_to_clean();
    check_table_concurrent_work();
  }
  assert(entry != nullptr, "invariant");
  return entry;
}

// Concurrent work
static void grow_table(JavaThread* jt) {
  FinalizerHashtable::GrowTask gt(_table);
  if (!gt.prepare(jt)) {
    return;
  }
  while (gt.do_task(jt)) {
    gt.pause(jt);
    {
      ThreadBlockInVM tbivm(jt);
    }
    gt.cont(jt);
  }
  gt.done(jt);
  _table_size = table_size();
}

struct FinalizerEntryDelete : StackObj {
  size_t _deleted;
  FinalizerEntryDelete() : _deleted(0) {}
  void operator()(FinalizerEntry** value) {
    assert(value != nullptr, "invariant");
    assert(*value != nullptr, "invariant");
    _deleted++;
  }
};

struct FinalizerEntryDeleteCheck : StackObj {
  size_t _processed;
  FinalizerEntryDeleteCheck() : _processed(0) {}
  bool operator()(FinalizerEntry** value) {
    assert(value != nullptr, "invariant");
    assert(*value != nullptr, "invariant");
    _processed++;
    return true;
  }
};

static void clean_table_entries(JavaThread* jt) {
  FinalizerHashtable::BulkDeleteTask bdt(_table);
  if (!bdt.prepare(jt)) {
    return;
  }
  FinalizerEntryDeleteCheck fedc;
  FinalizerEntryDelete fed;
  while (bdt.do_task(jt, fedc, fed)) {
    bdt.pause(jt);
    {
      ThreadBlockInVM tbivm(jt);
    }
    bdt.cont(jt);
  }
  reset_has_items_to_clean();
  bdt.done(jt);
}

static void do_table_concurrent_work(JavaThread* jt) {
  // We prefer growing, since that also removes dead items
  if (table_load_factor() > PREF_AVG_LIST_LEN && !_table->is_max_size_reached()) {
    grow_table(jt);
  } else {
    clean_table_entries(jt);
  }
  _has_work = false;
}

// Rehash
static bool do_table_rehash() {
  if (!_table->is_safepoint_safe()) {
    return false;
  }
  Thread* const thread = Thread::current();
  // We use current size
  const size_t new_size = _table->get_size_log2(thread);
  FinalizerHashtable* const new_table = new FinalizerHashtable(new_size, MAX_SIZE, REHASH_LEN);
  if (!_table->try_move_nodes_to(thread, new_table)) {
    delete new_table;
    return false;
  }
  // free old table
  delete _table;
  _table = new_table;
  return true;
}

bool FinalizerTable::needs_rehashing() {
  return _needs_rehashing;
}

void FinalizerTable::rehash_table() {
  static bool rehashed = false;
  log_debug(finalizertable)("Table imbalanced, rehashing called.");
  // Grow instead of rehash.
  if (table_load_factor() > PREF_AVG_LIST_LEN && !_table->is_max_size_reached()) {
    log_debug(finalizertable)("Choosing growing over rehashing.");
    trigger_table_cleanup();
    _needs_rehashing = false;
    return;
  }
  // Already rehashed.
  if (rehashed) {
    log_warning(finalizertable)("Rehashing already done, still long lists.");
    trigger_table_cleanup();
    _needs_rehashing = false;
    return;
  }
  if (do_table_rehash()) {
    rehashed = true;
  } else {
    log_info(finalizertable)("Resizes in progress rehashing skipped.");
  }
  _needs_rehashing = false;
}

bool FinalizerTable::has_work() {
  return _has_work;
}

void FinalizerTable::do_concurrent_work(JavaThread* service_thread) {
  assert(service_thread != nullptr, "invariant");
  if (_has_work) {
    do_table_concurrent_work(service_thread);
  }
}

bool FinalizerTable::create_table() {
  assert(_table == nullptr, "invariant");
  const size_t start_size_log_2 = ceil_log2(DEFAULT_TABLE_SIZE);
  _table_size = ((size_t)1) << start_size_log_2;
  _table = new FinalizerHashtable(start_size_log_2, MAX_SIZE, REHASH_LEN);
  return _table != nullptr;
}

static FinalizerEntry* lookup_entry(const InstanceKlass* ik, Thread* thread) {
  FinalizerEntryLookup lookup(ik);
  FinalizerEntryLookupGet felg;
  bool rehash_warning;
  _table->get(thread, lookup, felg, &rehash_warning);
  return felg.result();
}

static FinalizerEntry* get_entry(const InstanceKlass* ik, Thread* thread) {
  assert(ik != nullptr, "invariant");
  FinalizerEntry* const entry = lookup_entry(ik, thread);
  return entry != nullptr ? entry : add_to_table_if_needed(ik, thread);
}

const FinalizerEntry* FinalizerTable::lookup(const InstanceKlass* ik, Thread* thread) {
  assert(ik != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  assert(ik->has_finalizer(), "invariant");
  return lookup_entry(ik, thread);
}

void FinalizerTable::on_register(const instanceHandle& h_i, Thread* thread) {
  assert(h_i.not_null(), "invariant");
  const InstanceKlass* const ik = InstanceKlass::cast(h_i->klass());
  assert(ik != nullptr, "invariant");
  assert(ik->has_finalizer(), "invariant");
  FinalizerEntry* const fe = get_entry(ik, thread);
  assert(fe != nullptr, "invariant");
  fe->on_register();
}

void FinalizerTable::on_complete(const instanceHandle& h_i, JavaThread* finalizerThread) {
  assert(h_i.not_null(), "invariant");
  const InstanceKlass* const ik = InstanceKlass::cast(h_i->klass());
  assert(ik != nullptr, "invariant");
  assert(ik->has_finalizer(), "invariant");
  FinalizerEntry* const fe = get_entry(ik, finalizerThread);
  assert(fe != nullptr, "invariant");
  fe->on_complete();
}

class FinalizerScan : public StackObj {
 private:
  FinalizerEntryClosure* _closure;
 public:
  FinalizerScan(FinalizerEntryClosure* closure) : _closure(closure) {}
  bool operator()(FinalizerEntry** fe) {
    return _closure->do_entry(*fe);
  }
};

void FinalizerTable::do_entries(FinalizerEntryClosure* closure, Thread* thread) {
  assert(closure != nullptr, "invariant");
  FinalizerScan scan(closure);
  _table->do_scan(thread, scan);
}

static bool remove_entry(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  FinalizerEntryLookup lookup(ik);
  return _table->remove(Thread::current(), lookup);
}

static void on_unloading(Klass* klass) {
  assert(klass != nullptr, "invariant");
  if (!klass->is_instance_klass()) {
    return;
  }
  const InstanceKlass* const ik = InstanceKlass::cast(klass);
  if (ik->has_finalizer()) {
    remove_entry(ik);
  }
}

void FinalizerTable::purge_unloaded() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  ClassLoaderDataGraph::classes_unloading_do(&on_unloading);
}

#endif // INCLUDE_MANAGEMENT
