/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/macros.hpp"
#if INCLUDE_MANAGEMENT
#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/synchronizer.hpp"
#include "services/finalizerService.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/debug.hpp"

static const char* allocate(oop string) {
  char* str = nullptr;
  const typeArrayOop value = java_lang_String::value(string);
  if (value != nullptr) {
    const size_t length = java_lang_String::utf8_length(string, value);
    str = NEW_C_HEAP_ARRAY(char, length + 1, mtServiceability);
    java_lang_String::as_utf8_string(string, value, str, length + 1);
  }
  return str;
}

static int compute_field_offset(const Klass* klass, const char* field_name, const char* field_signature) {
  assert(klass != nullptr, "invariant");
  Symbol* const name = SymbolTable::new_symbol(field_name);
  assert(name != nullptr, "invariant");
  Symbol* const signature = SymbolTable::new_symbol(field_signature);
  assert(signature != nullptr, "invariant");
  assert(klass->is_instance_klass(), "invariant");
  fieldDescriptor fd;
  InstanceKlass::cast(klass)->find_field(name, signature, false, &fd);
  return fd.offset();
}

static const char* location_no_frag_string(oop codesource) {
  assert(codesource != nullptr, "invariant");
  static int loc_no_frag_offset = compute_field_offset(codesource->klass(), "locationNoFragString", "Ljava/lang/String;");
  oop string = codesource->obj_field(loc_no_frag_offset);
  return string != nullptr ? allocate(string) : nullptr;
}

static oop codesource(oop pd) {
  assert(pd != nullptr, "invariant");
  static int codesource_offset = compute_field_offset(pd->klass(), "codesource", "Ljava/security/CodeSource;");
  return pd->obj_field(codesource_offset);
}

static const char* get_codesource(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  oop pd = java_lang_Class::protection_domain(ik->java_mirror());
  if (pd == nullptr) {
    return nullptr;
  }
  oop cs = codesource(pd);
  return cs != nullptr ? location_no_frag_string(cs) : nullptr;
}

FinalizerEntry::FinalizerEntry(const InstanceKlass* ik) :
    _ik(ik),
    _codesource(get_codesource(ik)),
    _objects_on_heap(0),
    _total_finalizers_run(0) {}

FinalizerEntry::~FinalizerEntry() {
  FREE_C_HEAP_ARRAY(char, _codesource);
}

const InstanceKlass* FinalizerEntry::klass() const {
  return _ik;
}

const char* FinalizerEntry::codesource() const {
  return _codesource;
}

uintptr_t FinalizerEntry::objects_on_heap() const {
  return Atomic::load(&_objects_on_heap);
}

uintptr_t FinalizerEntry::total_finalizers_run() const {
  return Atomic::load(&_total_finalizers_run);
}

void FinalizerEntry::on_register() {
  Atomic::inc(&_objects_on_heap, memory_order_relaxed);
}

void FinalizerEntry::on_complete() {
  Atomic::inc(&_total_finalizers_run, memory_order_relaxed);
  Atomic::dec(&_objects_on_heap, memory_order_relaxed);
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
  bool equals(FinalizerEntry** value) {
    assert(value != nullptr, "invariant");
    assert(*value != nullptr, "invariant");
    return (*value)->klass() == _ik;
  }
  bool is_dead(FinalizerEntry** value) {
    return false;
  }
};

class FinalizerTableConfig : public AllStatic {
 public:
  typedef FinalizerEntry* Value;  // value of the Node in the hashtable

  static uintx get_hash(Value const& value, bool* is_dead) {
    return hash_function(value);
  }
  static void* allocate_node(void* context, size_t size, Value const& value) {
    return AllocateHeap(size, mtServiceability);
  }
  static void free_node(void* context, void* memory, Value const& value) {
    FreeHeap(memory);
  }
};

typedef ConcurrentHashTable<FinalizerTableConfig, mtServiceability> FinalizerHashtable;
static FinalizerHashtable* _table = nullptr;
static const size_t DEFAULT_TABLE_SIZE = 2048;
// 2^24 is max size, like StringTable.
static const size_t MAX_SIZE = 24;
static volatile bool _has_work = false;

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

static inline void set_has_work(bool value) {
  Atomic::store(&_has_work, value);
}

static inline bool has_work() {
  return Atomic::load(&_has_work);
}

static void request_resize() {
  if (!has_work()) {
    MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    if (!has_work()) {
      set_has_work(true);
      Service_lock->notify_all();
    }
  }
}

static FinalizerEntry* add_to_table_if_needed(const InstanceKlass* ik, Thread* thread) {
  FinalizerEntryLookup lookup(ik);
  FinalizerEntry* entry = nullptr;
  bool grow_hint = false;
  do {
    // We have looked up the entry once, proceed with insertion.
    entry = new FinalizerEntry(ik);
    if (_table->insert(thread, lookup, entry, &grow_hint)) {
      break;
    }
    // In case another thread did a concurrent add, return value already in the table.
    // This could fail if the entry got deleted concurrently, so loop back until success.
    FinalizerEntryLookupGet felg;
    if (_table->get(thread, lookup, felg, &grow_hint)) {
      entry = felg.result();
      break;
    }
  } while (true);
  if (grow_hint) {
    request_resize();
  }
  assert(entry != nullptr, "invariant");
  return entry;
}

static void do_table_concurrent_work(JavaThread* jt) {
  if (!_table->is_max_size_reached()) {
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
  }
  set_has_work(false);
}

bool FinalizerService::has_work() {
  return ::has_work();
}

void FinalizerService::do_concurrent_work(JavaThread* service_thread) {
  assert(service_thread != nullptr, "invariant");
  assert(has_work(), "invariant");
  do_table_concurrent_work(service_thread);
}

void FinalizerService::init() {
  assert(_table == nullptr, "invariant");
  const size_t start_size_log_2 = log2i_ceil(DEFAULT_TABLE_SIZE);
  _table = new FinalizerHashtable(start_size_log_2, MAX_SIZE, FinalizerHashtable::DEFAULT_GROW_HINT);
}

static FinalizerEntry* lookup_entry(const InstanceKlass* ik, Thread* thread) {
  FinalizerEntryLookup lookup(ik);
  FinalizerEntryLookupGet felg;
  _table->get(thread, lookup, felg);
  return felg.result();
}

const FinalizerEntry* FinalizerService::lookup(const InstanceKlass* ik, Thread* thread) {
  assert(ik != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  assert(ik->has_finalizer(), "invariant");
  return lookup_entry(ik, thread);
}

// Add if not exist.
static FinalizerEntry* get_entry(const InstanceKlass* ik, Thread* thread) {
  assert(ik != nullptr, "invariant");
  assert(ik->has_finalizer(), "invariant");
  FinalizerEntry* const entry = lookup_entry(ik, thread);
  return entry != nullptr ? entry : add_to_table_if_needed(ik, thread);
}

static FinalizerEntry* get_entry(oop finalizee, Thread* thread) {
  assert(finalizee != nullptr, "invariant");
  assert(finalizee->is_instance(), "invariant");
  return get_entry(InstanceKlass::cast(finalizee->klass()), thread);
}

static void log_registered(oop finalizee, Thread* thread) {
  ResourceMark rm(thread);
  const intptr_t identity_hash = ObjectSynchronizer::FastHashCode(thread, finalizee);
  log_info(finalizer)("Registered object (" INTPTR_FORMAT ") of class %s as finalizable", identity_hash, finalizee->klass()->external_name());
}

void FinalizerService::on_register(oop finalizee, Thread* thread) {
  FinalizerEntry* const fe = get_entry(finalizee, thread);
  assert(fe != nullptr, "invariant");
  fe->on_register();
  if (log_is_enabled(Info, finalizer)) {
    log_registered(finalizee, thread);
  }
}

static void log_completed(oop finalizee, Thread* thread) {
  ResourceMark rm(thread);
  const intptr_t identity_hash = ObjectSynchronizer::FastHashCode(thread, finalizee);
  log_info(finalizer)("Finalizer was run for object (" INTPTR_FORMAT ") of class %s", identity_hash, finalizee->klass()->external_name());
}

void FinalizerService::on_complete(oop finalizee, JavaThread* finalizer_thread) {
  FinalizerEntry* const fe = get_entry(finalizee, finalizer_thread);
  assert(fe != nullptr, "invariant");
  fe->on_complete();
  if (log_is_enabled(Info, finalizer)) {
    log_completed(finalizee, finalizer_thread);
  }
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

void FinalizerService::do_entries(FinalizerEntryClosure* closure, Thread* thread) {
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

void FinalizerService::purge_unloaded() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  ClassLoaderDataGraph::classes_unloading_do(&on_unloading);
}

#endif // INCLUDE_MANAGEMENT
