/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.inline.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/compactHashtable.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/vmClasses.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "services/diagnosticCommand.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/resizeableResourceHash.hpp"
#include "utilities/utf8.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#endif

// We prefer short chains of avg 2
const double PREF_AVG_LIST_LEN = 2.0;
// 2^24 is max size
const size_t END_SIZE = 24;
// If a chain gets to 100 something might be wrong
const size_t REHASH_LEN = 100;
// If we have as many dead items as 50% of the number of bucket
const double CLEAN_DEAD_HIGH_WATER_MARK = 0.5;

#if INCLUDE_CDS_JAVA_HEAP
bool StringTable::_is_two_dimensional_shared_strings_array = false;
OopHandle StringTable::_shared_strings_array;
int StringTable::_shared_strings_array_root_index;

inline oop StringTable::read_string_from_compact_hashtable(address base_address, u4 index) {
  assert(ArchiveHeapLoader::is_in_use(), "sanity");
  objArrayOop array = (objArrayOop)(_shared_strings_array.resolve());
  oop s;

  if (!_is_two_dimensional_shared_strings_array) {
    s = array->obj_at((int)index);
  } else {
    int primary_index = index >> _secondary_array_index_bits;
    int secondary_index = index & _secondary_array_index_mask;
    objArrayOop secondary = (objArrayOop)array->obj_at(primary_index);
    s = secondary->obj_at(secondary_index);
  }

  assert(java_lang_String::is_instance(s), "must be");
  return s;
}

typedef CompactHashtable<
  const jchar*, oop,
  StringTable::read_string_from_compact_hashtable,
  java_lang_String::equals> SharedStringTable;

static SharedStringTable _shared_table;
#endif

// --------------------------------------------------------------------------

typedef ConcurrentHashTable<StringTableConfig, mtSymbol> StringTableHash;
static StringTableHash* _local_table = nullptr;

volatile bool StringTable::_has_work = false;
volatile bool StringTable::_needs_rehashing = false;
OopStorage*   StringTable::_oop_storage;

static size_t _current_size = 0;
static volatile size_t _items_count = 0;

volatile bool _alt_hash = false;

static bool _rehashed = false;
static uint64_t _alt_hash_seed = 0;

unsigned int hash_string(const jchar* s, int len, bool useAlt) {
  return  useAlt ?
    AltHashing::halfsiphash_32(_alt_hash_seed, s, len) :
    java_lang_String::hash_code(s, len);
}

class StringTableConfig : public StackObj {
 private:
 public:
  typedef WeakHandle Value;

  static uintx get_hash(Value const& value, bool* is_dead) {
    oop val_oop = value.peek();
    if (val_oop == nullptr) {
      *is_dead = true;
      return 0;
    }
    *is_dead = false;
    ResourceMark rm;
    // All String oops are hashed as unicode
    int length;
    jchar* chars = java_lang_String::as_unicode_string_or_null(val_oop, length);
    if (chars != nullptr) {
      return hash_string(chars, length, _alt_hash);
    }
    vm_exit_out_of_memory(length, OOM_MALLOC_ERROR, "get hash from oop");
    return 0;
  }
  // We use default allocation/deallocation but counted
  static void* allocate_node(void* context, size_t size, Value const& value) {
    StringTable::item_added();
    return AllocateHeap(size, mtSymbol);
  }
  static void free_node(void* context, void* memory, Value& value) {
    value.release(StringTable::_oop_storage);
    FreeHeap(memory);
    StringTable::item_removed();
  }
};

class StringTableLookupJchar : StackObj {
 private:
  Thread* _thread;
  uintx _hash;
  int _len;
  const jchar* _str;
  Handle _found;

 public:
  StringTableLookupJchar(Thread* thread, uintx hash, const jchar* key, int len)
    : _thread(thread), _hash(hash), _len(len), _str(key) {
  }
  uintx get_hash() const {
    return _hash;
  }
  bool equals(WeakHandle* value) {
    oop val_oop = value->peek();
    if (val_oop == nullptr) {
      return false;
    }
    bool equals = java_lang_String::equals(val_oop, _str, _len);
    if (!equals) {
      return false;
    }
    // Need to resolve weak handle and Handleize through possible safepoint.
     _found = Handle(_thread, value->resolve());
    return true;
  }
  bool is_dead(WeakHandle* value) {
    oop val_oop = value->peek();
    return val_oop == nullptr;
  }
};

class StringTableLookupOop : public StackObj {
 private:
  Thread* _thread;
  uintx _hash;
  Handle _find;
  Handle _found;  // Might be a different oop with the same value that's already
                  // in the table, which is the point.
 public:
  StringTableLookupOop(Thread* thread, uintx hash, Handle handle)
    : _thread(thread), _hash(hash), _find(handle) { }

  uintx get_hash() const {
    return _hash;
  }

  bool equals(WeakHandle* value) {
    oop val_oop = value->peek();
    if (val_oop == nullptr) {
      return false;
    }
    bool equals = java_lang_String::equals(_find(), val_oop);
    if (!equals) {
      return false;
    }
    // Need to resolve weak handle and Handleize through possible safepoint.
    _found = Handle(_thread, value->resolve());
    return true;
  }

  bool is_dead(WeakHandle* value) {
    oop val_oop = value->peek();
    return val_oop == nullptr;
  }
};

void StringTable::create_table() {
  size_t start_size_log_2 = ceil_log2(StringTableSize);
  _current_size = ((size_t)1) << start_size_log_2;
  log_trace(stringtable)("Start size: " SIZE_FORMAT " (" SIZE_FORMAT ")",
                         _current_size, start_size_log_2);
  _local_table = new StringTableHash(start_size_log_2, END_SIZE, REHASH_LEN, true);
  _oop_storage = OopStorageSet::create_weak("StringTable Weak", mtSymbol);
  _oop_storage->register_num_dead_callback(&gc_notification);

#if INCLUDE_CDS_JAVA_HEAP
  if (ArchiveHeapLoader::is_in_use()) {
    _shared_strings_array = OopHandle(Universe::vm_global(), HeapShared::get_root(_shared_strings_array_root_index));
  }
#endif
}

void StringTable::item_added() {
  Atomic::inc(&_items_count);
}

void StringTable::item_removed() {
  Atomic::dec(&_items_count);
}

double StringTable::get_load_factor() {
  return double(_items_count)/double(_current_size);
}

double StringTable::get_dead_factor(size_t num_dead) {
  return double(num_dead)/double(_current_size);
}

size_t StringTable::table_size() {
  return ((size_t)1) << _local_table->get_size_log2(Thread::current());
}

void StringTable::trigger_concurrent_work() {
  MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  Atomic::store(&_has_work, true);
  Service_lock->notify_all();
}

// Probing
oop StringTable::lookup(Symbol* symbol) {
  ResourceMark rm;
  int length;
  jchar* chars = symbol->as_unicode(length);
  return lookup(chars, length);
}

oop StringTable::lookup(const jchar* name, int len) {
  unsigned int hash = java_lang_String::hash_code(name, len);
  oop string = lookup_shared(name, len, hash);
  if (string != nullptr) {
    return string;
  }
  if (_alt_hash) {
    hash = hash_string(name, len, true);
  }
  return do_lookup(name, len, hash);
}

class StringTableGet : public StackObj {
  Thread* _thread;
  Handle  _return;
 public:
  StringTableGet(Thread* thread) : _thread(thread) {}
  void operator()(WeakHandle* val) {
    oop result = val->resolve();
    assert(result != nullptr, "Result should be reachable");
    _return = Handle(_thread, result);
  }
  oop get_res_oop() {
    return _return();
  }
};

oop StringTable::do_lookup(const jchar* name, int len, uintx hash) {
  Thread* thread = Thread::current();
  StringTableLookupJchar lookup(thread, hash, name, len);
  StringTableGet stg(thread);
  bool rehash_warning;
  _local_table->get(thread, lookup, stg, &rehash_warning);
  update_needs_rehash(rehash_warning);
  return stg.get_res_oop();
}

// Interning
oop StringTable::intern(Symbol* symbol, TRAPS) {
  if (symbol == nullptr) return nullptr;
  ResourceMark rm(THREAD);
  int length;
  jchar* chars = symbol->as_unicode(length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}

oop StringTable::intern(oop string, TRAPS) {
  if (string == nullptr) return nullptr;
  ResourceMark rm(THREAD);
  int length;
  Handle h_string (THREAD, string);
  jchar* chars = java_lang_String::as_unicode_string(string, length,
                                                     CHECK_NULL);
  oop result = intern(h_string, chars, length, CHECK_NULL);
  return result;
}

oop StringTable::intern(const char* utf8_string, TRAPS) {
  if (utf8_string == nullptr) return nullptr;
  ResourceMark rm(THREAD);
  int length = UTF8::unicode_length(utf8_string);
  jchar* chars = NEW_RESOURCE_ARRAY(jchar, length);
  UTF8::convert_to_unicode(utf8_string, chars, length);
  Handle string;
  oop result = intern(string, chars, length, CHECK_NULL);
  return result;
}

oop StringTable::intern(Handle string_or_null_h, const jchar* name, int len, TRAPS) {
  // shared table always uses java_lang_String::hash_code
  unsigned int hash = java_lang_String::hash_code(name, len);
  oop found_string = lookup_shared(name, len, hash);
  if (found_string != nullptr) {
    return found_string;
  }
  if (_alt_hash) {
    hash = hash_string(name, len, true);
  }
  found_string = do_lookup(name, len, hash);
  if (found_string != nullptr) {
    return found_string;
  }
  return do_intern(string_or_null_h, name, len, hash, THREAD);
}

oop StringTable::do_intern(Handle string_or_null_h, const jchar* name,
                           int len, uintx hash, TRAPS) {
  HandleMark hm(THREAD);  // cleanup strings created
  Handle string_h;

  if (!string_or_null_h.is_null()) {
    string_h = string_or_null_h;
  } else {
    string_h = java_lang_String::create_from_unicode(name, len, CHECK_NULL);
  }

  assert(java_lang_String::equals(string_h(), name, len),
         "string must be properly initialized");
  assert(len == java_lang_String::length(string_h()), "Must be same length");

  // Notify deduplication support that the string is being interned.  A string
  // must never be deduplicated after it has been interned.  Doing so interferes
  // with compiler optimizations done on e.g. interned string literals.
  if (StringDedup::is_enabled()) {
    StringDedup::notify_intern(string_h());
  }

  StringTableLookupOop lookup(THREAD, hash, string_h);
  StringTableGet stg(THREAD);

  bool rehash_warning;
  do {
    // Callers have already looked up the String using the jchar* name, so just go to add.
    WeakHandle wh(_oop_storage, string_h);
    // The hash table takes ownership of the WeakHandle, even if it's not inserted.
    if (_local_table->insert(THREAD, lookup, wh, &rehash_warning)) {
      update_needs_rehash(rehash_warning);
      return wh.resolve();
    }
    // In case another thread did a concurrent add, return value already in the table.
    // This could fail if the String got gc'ed concurrently, so loop back until success.
    if (_local_table->get(THREAD, lookup, stg, &rehash_warning)) {
      update_needs_rehash(rehash_warning);
      return stg.get_res_oop();
    }
  } while(true);
}

// Concurrent work
void StringTable::grow(JavaThread* jt) {
  StringTableHash::GrowTask gt(_local_table);
  if (!gt.prepare(jt)) {
    return;
  }
  log_trace(stringtable)("Started to grow");
  {
    TraceTime timer("Grow", TRACETIME_LOG(Debug, stringtable, perf));
    while (gt.do_task(jt)) {
      gt.pause(jt);
      {
        ThreadBlockInVM tbivm(jt);
      }
      gt.cont(jt);
    }
  }
  gt.done(jt);
  _current_size = table_size();
  log_debug(stringtable)("Grown to size:" SIZE_FORMAT, _current_size);
}

struct StringTableDoDelete : StackObj {
  void operator()(WeakHandle* val) {
    /* do nothing */
  }
};

struct StringTableDeleteCheck : StackObj {
  long _count;
  long _item;
  StringTableDeleteCheck() : _count(0), _item(0) {}
  bool operator()(WeakHandle* val) {
    ++_item;
    oop tmp = val->peek();
    if (tmp == nullptr) {
      ++_count;
      return true;
    } else {
      return false;
    }
  }
};

void StringTable::clean_dead_entries(JavaThread* jt) {
  StringTableHash::BulkDeleteTask bdt(_local_table);
  if (!bdt.prepare(jt)) {
    return;
  }

  StringTableDeleteCheck stdc;
  StringTableDoDelete stdd;
  NativeHeapTrimmer::SuspendMark sm("stringtable");
  {
    TraceTime timer("Clean", TRACETIME_LOG(Debug, stringtable, perf));
    while(bdt.do_task(jt, stdc, stdd)) {
      bdt.pause(jt);
      {
        ThreadBlockInVM tbivm(jt);
      }
      bdt.cont(jt);
    }
    bdt.done(jt);
  }
  log_debug(stringtable)("Cleaned %ld of %ld", stdc._count, stdc._item);
}

void StringTable::gc_notification(size_t num_dead) {
  log_trace(stringtable)("Uncleaned items:" SIZE_FORMAT, num_dead);

  if (has_work()) {
    return;
  }

  double load_factor = StringTable::get_load_factor();
  double dead_factor = StringTable::get_dead_factor(num_dead);
  // We should clean/resize if we have more dead than alive,
  // more items than preferred load factor or
  // more dead items than water mark.
  if ((dead_factor > load_factor) ||
      (load_factor > PREF_AVG_LIST_LEN) ||
      (dead_factor > CLEAN_DEAD_HIGH_WATER_MARK)) {
    log_debug(stringtable)("Concurrent work triggered, live factor: %g dead factor: %g",
                           load_factor, dead_factor);
    trigger_concurrent_work();
  }
}

bool StringTable::has_work() {
  return Atomic::load_acquire(&_has_work);
}

void StringTable::do_concurrent_work(JavaThread* jt) {
  double load_factor = get_load_factor();
  log_debug(stringtable, perf)("Concurrent work, live factor: %g", load_factor);
  // We prefer growing, since that also removes dead items
  if (load_factor > PREF_AVG_LIST_LEN && !_local_table->is_max_size_reached()) {
    grow(jt);
  } else {
    clean_dead_entries(jt);
  }
  Atomic::release_store(&_has_work, false);
}

// Rehash
bool StringTable::do_rehash() {
  if (!_local_table->is_safepoint_safe()) {
    return false;
  }

  // We use current size, not max size.
  size_t new_size = _local_table->get_size_log2(Thread::current());
  StringTableHash* new_table = new StringTableHash(new_size, END_SIZE, REHASH_LEN, true);
  // Use alt hash from now on
  _alt_hash = true;
  if (!_local_table->try_move_nodes_to(Thread::current(), new_table)) {
    _alt_hash = false;
    delete new_table;
    return false;
  }

  // free old table
  delete _local_table;
  _local_table = new_table;

  return true;
}

bool StringTable::should_grow() {
  return get_load_factor() > PREF_AVG_LIST_LEN && !_local_table->is_max_size_reached();
}

bool StringTable::rehash_table_expects_safepoint_rehashing() {
  // No rehashing required
  if (!needs_rehashing()) {
    return false;
  }

  // Grow instead of rehash
  if (should_grow()) {
    return false;
  }

  // Already rehashed
  if (_rehashed) {
    return false;
  }

  // Resizing in progress
  if (!_local_table->is_safepoint_safe()) {
    return false;
  }

  return true;
}

void StringTable::rehash_table() {
  log_debug(stringtable)("Table imbalanced, rehashing called.");

  // Grow instead of rehash.
  if (should_grow()) {
    log_debug(stringtable)("Choosing growing over rehashing.");
    trigger_concurrent_work();
    _needs_rehashing = false;
    return;
  }
  // Already rehashed.
  if (_rehashed) {
    log_warning(stringtable)("Rehashing already done, still long lists.");
    trigger_concurrent_work();
    _needs_rehashing = false;
    return;
  }

  _alt_hash_seed = AltHashing::compute_seed();
  {
    if (do_rehash()) {
      _rehashed = true;
    } else {
      log_info(stringtable)("Resizes in progress rehashing skipped.");
    }
  }
  _needs_rehashing = false;
}

// Statistics
static size_t literal_size(oop obj) {
  if (obj == nullptr) {
    return 0;
  }

  size_t word_size = obj->size();

  if (obj->klass() == vmClasses::String_klass()) {
    // This may overcount if String.value arrays are shared.
    word_size += java_lang_String::value(obj)->size();
  }

  return word_size * HeapWordSize;
}

struct SizeFunc : StackObj {
  size_t operator()(WeakHandle* val) {
    oop s = val->peek();
    if (s == nullptr) {
      // Dead
      return 0;
    }
    return literal_size(s);
  };
};

TableStatistics StringTable::get_table_statistics() {
  static TableStatistics ts;
  SizeFunc sz;
  ts = _local_table->statistics_get(Thread::current(), sz, ts);
  return ts;
}

void StringTable::print_table_statistics(outputStream* st) {
  SizeFunc sz;
  _local_table->statistics_to(Thread::current(), sz, st, "StringTable");
#if INCLUDE_CDS_JAVA_HEAP
  if (!_shared_table.empty()) {
    _shared_table.print_table_statistics(st, "Shared String Table");
  }
#endif
}

// Verification
class VerifyStrings : StackObj {
 public:
  bool operator()(WeakHandle* val) {
    oop s = val->peek();
    if (s != nullptr) {
      assert(java_lang_String::length(s) >= 0, "Length on string must work.");
    }
    return true;
  };
};

// This verification is part of Universe::verify() and needs to be quick.
void StringTable::verify() {
  VerifyStrings vs;
  _local_table->do_safepoint_scan(vs);
}

// Verification and comp
class VerifyCompStrings : StackObj {
  static unsigned string_hash(oop const& str) {
    return java_lang_String::hash_code_noupdate(str);
  }
  static bool string_equals(oop const& a, oop const& b) {
    return java_lang_String::equals(a, b);
  }

  ResizeableResourceHashtable<oop, bool, AnyObj::C_HEAP, mtInternal,
                              string_hash, string_equals> _table;
 public:
  size_t _errors;
  VerifyCompStrings() : _table(unsigned(_items_count / 8) + 1, 0 /* do not resize */), _errors(0) {}
  bool operator()(WeakHandle* val) {
    oop s = val->resolve();
    if (s == nullptr) {
      return true;
    }
    bool created;
    _table.put_if_absent(s, true, &created);
    assert(created, "Duplicate strings");
    if (!created) {
      _errors++;
    }
    return true;
  };
};

size_t StringTable::verify_and_compare_entries() {
  Thread* thr = Thread::current();
  VerifyCompStrings vcs;
  _local_table->do_scan(thr, vcs);
  return vcs._errors;
}

static void print_string(Thread* current, outputStream* st, oop s) {
  typeArrayOop value     = java_lang_String::value_no_keepalive(s);
  int          length    = java_lang_String::length(s);
  bool         is_latin1 = java_lang_String::is_latin1(s);

  if (length <= 0) {
    st->print("%d: ", length);
  } else {
    ResourceMark rm(current);
    int utf8_length = length;
    char* utf8_string;

    if (!is_latin1) {
      jchar* chars = value->char_at_addr(0);
      utf8_string = UNICODE::as_utf8(chars, utf8_length);
    } else {
      jbyte* bytes = value->byte_at_addr(0);
      utf8_string = UNICODE::as_utf8(bytes, utf8_length);
    }

    st->print("%d: ", utf8_length);
    HashtableTextDump::put_utf8(st, utf8_string, utf8_length);
  }
  st->cr();
}

// Dumping
class PrintString : StackObj {
  Thread* _thr;
  outputStream* _st;
 public:
  PrintString(Thread* thr, outputStream* st) : _thr(thr), _st(st) {}
  bool operator()(WeakHandle* val) {
    oop s = val->peek();
    if (s == nullptr) {
      return true;
    }
    print_string(_thr, _st, s);
    return true;
  };
};

class PrintSharedString : StackObj {
  Thread* _thr;
  outputStream* _st;
public:
  PrintSharedString(Thread* thr, outputStream* st) : _thr(thr), _st(st) {}
  void do_value(oop s) {
    if (s == nullptr) {
      return;
    }
    print_string(_thr, _st, s);
  };
};

void StringTable::dump(outputStream* st, bool verbose) {
  if (!verbose) {
    print_table_statistics(st);
  } else {
    Thread* thr = Thread::current();
    ResourceMark rm(thr);
    st->print_cr("VERSION: 1.1");
    PrintString ps(thr, st);
    if (!_local_table->try_scan(thr, ps)) {
      st->print_cr("dump unavailable at this moment");
    }
#if INCLUDE_CDS_JAVA_HEAP
    if (!_shared_table.empty()) {
      st->print_cr("#----------------");
      st->print_cr("# Shared strings:");
      st->print_cr("#----------------");
      PrintSharedString pss(thr, st);
      _shared_table.iterate(&pss);
    }
#endif
  }
}

// Utility for dumping strings
StringtableDCmd::StringtableDCmd(outputStream* output, bool heap) :
                                 DCmdWithParser(output, heap),
  _verbose("-verbose", "Dump the content of each string in the table",
           "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_verbose);
}

void StringtableDCmd::execute(DCmdSource source, TRAPS) {
  VM_DumpHashtable dumper(output(), VM_DumpHashtable::DumpStrings,
                         _verbose.value());
  VMThread::execute(&dumper);
}

// Sharing
#if INCLUDE_CDS_JAVA_HEAP
size_t StringTable::shared_entry_count() {
  return _shared_table.entry_count();
}

oop StringTable::lookup_shared(const jchar* name, int len, unsigned int hash) {
  assert(hash == java_lang_String::hash_code(name, len),
         "hash must be computed using java_lang_String::hash_code");
  return _shared_table.lookup(name, hash, len);
}

oop StringTable::lookup_shared(const jchar* name, int len) {
  return _shared_table.lookup(name, java_lang_String::hash_code(name, len), len);
}

// This is called BEFORE we enter the CDS safepoint. We can allocate heap objects.
// This should be called when we know no more strings will be added (which will be easy
// to guarantee because CDS runs with a single Java thread. See JDK-8253495.)
void StringTable::allocate_shared_strings_array(TRAPS) {
  if (!CDSConfig::is_dumping_heap()) {
    return;
  }
  if (_items_count > (size_t)max_jint) {
    fatal("Too many strings to be archived: %zu", _items_count);
  }

  int total = (int)_items_count;
  size_t single_array_size = objArrayOopDesc::object_size(total);

  log_info(cds)("allocated string table for %d strings", total);

  if (!ArchiveHeapWriter::is_too_large_to_archive(single_array_size)) {
    // The entire table can fit in a single array
    objArrayOop array = oopFactory::new_objArray(vmClasses::Object_klass(), total, CHECK);
    _shared_strings_array = OopHandle(Universe::vm_global(), array);
    log_info(cds)("string table array (single level) length = %d", total);
  } else {
    // Split the table in two levels of arrays.
    int primary_array_length = (total + _secondary_array_max_length - 1) / _secondary_array_max_length;
    size_t primary_array_size = objArrayOopDesc::object_size(primary_array_length);
    size_t secondary_array_size = objArrayOopDesc::object_size(_secondary_array_max_length);

    if (ArchiveHeapWriter::is_too_large_to_archive(secondary_array_size)) {
      // This can only happen if you have an extremely large number of classes that
      // refer to more than 16384 * 16384 = 26M interned strings! Not a practical concern
      // but bail out for safety.
      log_error(cds)("Too many strings to be archived: %zu", _items_count);
      MetaspaceShared::unrecoverable_writing_error();
    }

    objArrayOop primary = oopFactory::new_objArray(vmClasses::Object_klass(), primary_array_length, CHECK);
    objArrayHandle primaryHandle(THREAD, primary);
    _shared_strings_array = OopHandle(Universe::vm_global(), primary);

    log_info(cds)("string table array (primary) length = %d", primary_array_length);
    for (int i = 0; i < primary_array_length; i++) {
      int len;
      if (total > _secondary_array_max_length) {
        len = _secondary_array_max_length;
      } else {
        len = total;
      }
      total -= len;

      objArrayOop secondary = oopFactory::new_objArray(vmClasses::Object_klass(), len, CHECK);
      primaryHandle()->obj_at_put(i, secondary);

      log_info(cds)("string table array (secondary)[%d] length = %d", i, len);
      assert(!ArchiveHeapWriter::is_too_large_to_archive(secondary), "sanity");
    }

    assert(total == 0, "must be");
    _is_two_dimensional_shared_strings_array = true;
  }
}

#ifndef PRODUCT
void StringTable::verify_secondary_array_index_bits() {
  int max;
  for (max = 1; ; max++) {
    size_t next_size = objArrayOopDesc::object_size(1 << (max + 1));
    if (ArchiveHeapWriter::is_too_large_to_archive(next_size)) {
      break;
    }
  }
  // Currently max is 17 for +UseCompressedOops, 16 for -UseCompressedOops.
  // When we add support for Shenandoah (which has a smaller mininum region size than G1),
  // max will become 15/14.
  //
  // We use _secondary_array_index_bits==14 as that will be the eventual value, and will
  // make testing easier.
  assert(_secondary_array_index_bits <= max,
         "_secondary_array_index_bits (%d) must be smaller than max possible value (%d)",
         _secondary_array_index_bits, max);
}
#endif // PRODUCT

// This is called AFTER we enter the CDS safepoint.
//
// For each shared string:
// [1] Store it into _shared_strings_array. Encode its position as a 32-bit index.
// [2] Store the index and hashcode into _shared_table.
oop StringTable::init_shared_table(const DumpedInternedStrings* dumped_interned_strings) {
  assert(HeapShared::can_write(), "must be");
  objArrayOop array = (objArrayOop)(_shared_strings_array.resolve());

  verify_secondary_array_index_bits();

  _shared_table.reset();
  CompactHashtableWriter writer((int)_items_count, ArchiveBuilder::string_stats());

  int index = 0;
  auto copy_into_array = [&] (oop string, bool value_ignored) {
    unsigned int hash = java_lang_String::hash_code(string);
    writer.add(hash, index);

    if (!_is_two_dimensional_shared_strings_array) {
      assert(index < array->length(), "no strings should have been added");
      array->obj_at_put(index, string);
    } else {
      int primary_index = index >> _secondary_array_index_bits;
      int secondary_index = index & _secondary_array_index_mask;

      assert(primary_index < array->length(), "no strings should have been added");
      objArrayOop secondary = (objArrayOop)array->obj_at(primary_index);

      assert(secondary != nullptr && secondary->is_objArray(), "must be");
      assert(secondary_index < secondary->length(), "no strings should have been added");
      secondary->obj_at_put(secondary_index, string);
    }

    index ++;
  };
  dumped_interned_strings->iterate_all(copy_into_array);

  writer.dump(&_shared_table, "string");

  return array;
}

void StringTable::set_shared_strings_array_index(int root_index) {
  _shared_strings_array_root_index = root_index;
}

void StringTable::serialize_shared_table_header(SerializeClosure* soc) {
  _shared_table.serialize_header(soc);

  if (soc->writing()) {
    // Sanity. Make sure we don't use the shared table at dump time
    _shared_table.reset();
  } else if (!ArchiveHeapLoader::is_in_use()) {
    _shared_table.reset();
  }

  soc->do_bool(&_is_two_dimensional_shared_strings_array);
  soc->do_int(&_shared_strings_array_root_index);
}
#endif //INCLUDE_CDS_JAVA_HEAP
