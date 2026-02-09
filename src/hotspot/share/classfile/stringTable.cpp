/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotMappedHeapLoader.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.inline.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/compactHashtable.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/compileBroker.hpp"
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
#include "runtime/atomicAccess.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "runtime/vmOperations.hpp"
#include "services/diagnosticCommand.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/resizableHashTable.hpp"
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
inline oop StringTable::read_string_from_compact_hashtable(address base_address, u4 index) {
  assert(AOTMappedHeapLoader::is_in_use(), "sanity");
  oop s = HeapShared::get_root((int)index, false);
  assert(java_lang_String::is_instance(s), "must be");
  return s;
}

typedef CompactHashtable<
  const StringTable::StringWrapper&, oop,
  StringTable::read_string_from_compact_hashtable,
  StringTable::wrapped_string_equals> SharedStringTable;

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

enum class StringType {
  OopStr, UnicodeStr, SymbolStr, UTF8Str
};

struct StringWrapperInternal {
  union {
    const Handle oop_str;
    const jchar* unicode_str;
    const Symbol* symbol_str;
    const char* utf8_str;
  };
  const StringType type;
  const size_t length;

  StringWrapperInternal(const Handle oop_str, const size_t length)     : oop_str(oop_str),         type(StringType::OopStr), length(length)     {}
  StringWrapperInternal(const jchar* unicode_str, const size_t length) : unicode_str(unicode_str), type(StringType::UnicodeStr), length(length) {}
  StringWrapperInternal(const Symbol* symbol_str, const size_t length) : symbol_str(symbol_str),   type(StringType::SymbolStr), length(length)  {}
  StringWrapperInternal(const char* utf8_str, const size_t length)     : utf8_str(utf8_str),       type(StringType::UTF8Str), length(length)    {}
};

static unsigned int hash_string(const jchar* s, int len, bool useAlt) {
  return  useAlt ?
    AltHashing::halfsiphash_32(_alt_hash_seed, s, len) :
    java_lang_String::hash_code(s, len);
}

const char* StringTable::get_symbol_utf8(const StringWrapper& symbol) {
  return reinterpret_cast<const char*>(symbol.symbol_str->bytes());
}

unsigned int StringTable::hash_wrapped_string(const StringWrapper& wrapped_str) {
  switch (wrapped_str.type) {
  case StringType::OopStr:
    return java_lang_String::hash_code(wrapped_str.oop_str());
  case StringType::UnicodeStr:
    return java_lang_String::hash_code(wrapped_str.unicode_str, static_cast<int>(wrapped_str.length));
  case StringType::SymbolStr:
    return java_lang_String::hash_code(get_symbol_utf8(wrapped_str), wrapped_str.length);
  case StringType::UTF8Str:
    return java_lang_String::hash_code(wrapped_str.utf8_str, wrapped_str.length);
  default:
    ShouldNotReachHere();
  }
  return 0;
}

// Unnamed int needed to fit CompactHashtable's equals type signature
bool StringTable::wrapped_string_equals(oop java_string, const StringWrapper& wrapped_str, int) {
  switch (wrapped_str.type) {
  case StringType::OopStr:
    return java_lang_String::equals(java_string, wrapped_str.oop_str());
  case StringType::UnicodeStr:
    return java_lang_String::equals(java_string, wrapped_str.unicode_str, static_cast<int>(wrapped_str.length));
  case StringType::SymbolStr:
    return java_lang_String::equals(java_string, get_symbol_utf8(wrapped_str), wrapped_str.length);
  case StringType::UTF8Str:
    return java_lang_String::equals(java_string, wrapped_str.utf8_str, wrapped_str.length);
  default:
    ShouldNotReachHere();
  }
  return false;
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

class StringTableLookup : StackObj {
  uintx _hash;

protected:
  Thread* _thread;
  Handle _found;

public:
  StringTableLookup(Thread* thread, uintx hash)
      : _hash(hash), _thread(thread) {}
  uintx get_hash() const { return _hash; }
  bool is_dead(WeakHandle* value) {
    oop val_oop = value->peek();
    return val_oop == nullptr;
  }
};

class StringTableLookupUnicode : public StringTableLookup {
private:
  const jchar* _str;
  int _len;

public:
  StringTableLookupUnicode(Thread* thread, uintx hash, const jchar* key, int len)
      : StringTableLookup(thread, hash), _str(key), _len(len) {}

  bool equals(const WeakHandle* value) {
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
};

class StringTableLookupUTF8 : public StringTableLookup {
private:
  const char* _str;
  size_t _utf8_len;

public:
  StringTableLookupUTF8(Thread* thread, uintx hash, const char* key, size_t utf8_len)
      : StringTableLookup(thread, hash), _str(key), _utf8_len(utf8_len) {}

  bool equals(const WeakHandle* value) {
    oop val_oop = value->peek();
    if (val_oop == nullptr) {
      return false;
    }
    bool equals = java_lang_String::equals(val_oop, _str, _utf8_len);
    if (!equals) {
      return false;
    }
    // Need to resolve weak handle and Handleize through possible safepoint.
    _found = Handle(_thread, value->resolve());
    return true;
  }
};

class StringTableLookupOop : public StringTableLookup {
private:
  Handle _find;

public:
  StringTableLookupOop(Thread* thread, uintx hash, Handle handle)
      : StringTableLookup(thread, hash), _find(handle) {}

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
};

void StringTable::create_table() {
  size_t start_size_log_2 = log2i_ceil(StringTableSize);
  _current_size = ((size_t)1) << start_size_log_2;
  log_trace(stringtable)("Start size: %zu (%zu)",
                         _current_size, start_size_log_2);
  _local_table = new StringTableHash(start_size_log_2, END_SIZE, REHASH_LEN, true);
  _oop_storage = OopStorageSet::create_weak("StringTable Weak", mtSymbol);
  _oop_storage->register_num_dead_callback(&gc_notification);
}

void StringTable::item_added() {
  AtomicAccess::inc(&_items_count);
}

void StringTable::item_removed() {
  AtomicAccess::dec(&_items_count);
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

bool StringTable::has_work() {
  return AtomicAccess::load_acquire(&_has_work);
}

size_t StringTable::items_count_acquire() {
  return AtomicAccess::load_acquire(&_items_count);
}

void StringTable::trigger_concurrent_work() {
  // Avoid churn on ServiceThread
  if (!has_work()) {
    MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    AtomicAccess::store(&_has_work, true);
    Service_lock->notify_all();
  }
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
  StringWrapper wrapped_name(name, len);
  oop string = lookup_shared(wrapped_name, hash);
  if (string != nullptr) {
    return string;
  }
  if (_alt_hash) {
    hash = hash_string(name, len, true);
  }
  return do_lookup(wrapped_name, hash);
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

void StringTable::update_needs_rehash(bool rehash) {
  if (rehash) {
    _needs_rehashing = true;
    trigger_concurrent_work();
  }
}

oop StringTable::do_lookup(const StringWrapper& name, uintx hash) {
  Thread* thread = Thread::current();
  StringTableGet stg(thread);
  bool rehash_warning;

  switch (name.type) {
  case StringType::OopStr: {
    StringTableLookupOop lookup(thread, hash, name.oop_str);
    _local_table->get(thread, lookup, stg, &rehash_warning);
    break;
  }
  case StringType::UnicodeStr: {
    StringTableLookupUnicode lookup(thread, hash, name.unicode_str, static_cast<int>(name.length));
    _local_table->get(thread, lookup, stg, &rehash_warning);
    break;
  }
  case StringType::SymbolStr: {
    StringTableLookupUTF8 lookup(thread, hash, get_symbol_utf8(name), name.length);
    _local_table->get(thread, lookup, stg, &rehash_warning);
    break;
  }
  case StringType::UTF8Str: {
    StringTableLookupUTF8 lookup(thread, hash, name.utf8_str, name.length);
    _local_table->get(thread, lookup, stg, &rehash_warning);
    break;
  }
  default:
    ShouldNotReachHere();
  }

  update_needs_rehash(rehash_warning);
  return stg.get_res_oop();
}

// Converts and allocates to a unicode string and stores the unicode length in len
const jchar* StringTable::to_unicode(const StringWrapper& wrapped_str, int &len, TRAPS) {
  switch (wrapped_str.type) {
  case StringType::UnicodeStr:
    len = static_cast<int>(wrapped_str.length);
    return wrapped_str.unicode_str;
  case StringType::OopStr:
    return java_lang_String::as_unicode_string(wrapped_str.oop_str(), len, CHECK_NULL);
  case StringType::SymbolStr: {
    const char* utf8_str = get_symbol_utf8(wrapped_str);
    int unicode_length = UTF8::unicode_length(utf8_str, wrapped_str.symbol_str->utf8_length());
    jchar* chars = NEW_RESOURCE_ARRAY(jchar, unicode_length);
    UTF8::convert_to_unicode(utf8_str, chars, unicode_length);
    len = unicode_length;
    return chars;
  }
  case StringType::UTF8Str: {
    int unicode_length = UTF8::unicode_length(wrapped_str.utf8_str);
    jchar* chars = NEW_RESOURCE_ARRAY(jchar, unicode_length);
    UTF8::convert_to_unicode(wrapped_str.utf8_str, chars, unicode_length);
    len = unicode_length;
    return chars;
  }
  default:
    ShouldNotReachHere();
  }
  return nullptr;
}

Handle StringTable::handle_from_wrapped_string(const StringWrapper& wrapped_str, TRAPS) {
  switch (wrapped_str.type) {
  case StringType::OopStr:
    return wrapped_str.oop_str;
  case StringType::UnicodeStr:
    return java_lang_String::create_from_unicode(wrapped_str.unicode_str, static_cast<int>(wrapped_str.length), THREAD);
  case StringType::SymbolStr:
    return java_lang_String::create_from_symbol(wrapped_str.symbol_str, THREAD);
  case StringType::UTF8Str:
    return java_lang_String::create_from_str(wrapped_str.utf8_str, THREAD);
  default:
    ShouldNotReachHere();
  }
  return Handle();
}

// Interning
oop StringTable::intern(Symbol* symbol, TRAPS) {
  if (symbol == nullptr) return nullptr;
  int length = symbol->utf8_length();
  StringWrapper name(symbol, length);
  oop result = intern(name, CHECK_NULL);
  return result;
}

oop StringTable::intern(oop string, TRAPS) {
  if (string == nullptr) return nullptr;
  int length = java_lang_String::length(string);
  Handle h_string (THREAD, string);
  StringWrapper name(h_string, length);
  oop result = intern(name, CHECK_NULL);
  return result;
}

oop StringTable::intern(const char* utf8_string, TRAPS) {
  if (utf8_string == nullptr) return nullptr;
  size_t length = strlen(utf8_string);
  StringWrapper name(utf8_string, length);
  oop result = intern(name, CHECK_NULL);
  return result;
}

oop StringTable::intern(const StringWrapper& name, TRAPS) {
  // shared table always uses java_lang_String::hash_code
  unsigned int hash = hash_wrapped_string(name);
  oop found_string = lookup_shared(name, hash);
  if (found_string != nullptr) {
    return found_string;
  }

  if (_alt_hash) {
    ResourceMark rm(THREAD);
    // Convert to unicode for alt hashing
    int unicode_length;
    const jchar* chars = to_unicode(name, unicode_length, CHECK_NULL);
    hash = hash_string(chars, unicode_length, true);
  }

  found_string = do_lookup(name, hash);
  if (found_string != nullptr) {
    return found_string;
  }
  return do_intern(name, hash, THREAD);
}

oop StringTable::do_intern(const StringWrapper& name, uintx hash, TRAPS) {
  HandleMark hm(THREAD);  // cleanup strings created
  Handle string_h = handle_from_wrapped_string(name, CHECK_NULL);

  assert(StringTable::wrapped_string_equals(string_h(), name),
         "string must be properly initialized");

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
    // Callers have already looked up the String, so just go to add.
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
  log_debug(stringtable)("Grown to size:%zu", _current_size);
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
  // BulkDeleteTask::prepare() may take ConcurrentHashTableResize_lock (nosafepoint-2).
  // When NativeHeapTrimmer is enabled, SuspendMark may take NativeHeapTrimmer::_lock (nosafepoint).
  // Take SuspendMark first to keep lock order and avoid deadlock.
  NativeHeapTrimmer::SuspendMark sm("stringtable");
  StringTableHash::BulkDeleteTask bdt(_local_table);
  if (!bdt.prepare(jt)) {
    return;
  }

  StringTableDeleteCheck stdc;
  StringTableDoDelete stdd;
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
  log_trace(stringtable)("Uncleaned items:%zu", num_dead);

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

bool StringTable::should_grow() {
  return get_load_factor() > PREF_AVG_LIST_LEN && !_local_table->is_max_size_reached();
}

void StringTable::do_concurrent_work(JavaThread* jt) {
  // Rehash if needed.  Rehashing goes to a safepoint but the rest of this
  // work is concurrent.
  if (needs_rehashing() && maybe_rehash_table()) {
    AtomicAccess::release_store(&_has_work, false);
    return; // done, else grow
  }
  log_debug(stringtable, perf)("Concurrent work, live factor: %g", get_load_factor());
  // We prefer growing, since that also removes dead items
  if (should_grow()) {
    grow(jt);
  } else {
    clean_dead_entries(jt);
  }
  AtomicAccess::release_store(&_has_work, false);
}

// Called at VM_Operation safepoint
void StringTable::rehash_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be called at safepoint");
  // The ServiceThread initiates the rehashing so it is not resizing.
  assert (_local_table->is_safepoint_safe(), "Should not be resizing now");

  _alt_hash_seed = AltHashing::compute_seed();

  // We use current size, not max size.
  size_t new_size = _local_table->get_size_log2(Thread::current());
  StringTableHash* new_table = new StringTableHash(new_size, END_SIZE, REHASH_LEN, true);
  // Use alt hash from now on
  _alt_hash = true;
  _local_table->rehash_nodes_to(Thread::current(), new_table);

  // free old table
  delete _local_table;
  _local_table = new_table;

  _rehashed = true;
  _needs_rehashing = false;
}

bool StringTable::maybe_rehash_table() {
  log_debug(stringtable)("Table imbalanced, rehashing called.");

  // Grow instead of rehash.
  if (should_grow()) {
    log_debug(stringtable)("Choosing growing over rehashing.");
    _needs_rehashing = false;
    return false;
  }
  // Already rehashed.
  if (_rehashed) {
    log_warning(stringtable)("Rehashing already done, still long lists.");
    _needs_rehashing = false;
    return false;
  }

  VM_RehashStringTable op;
  VMThread::execute(&op);
  return true;  // return true because we tried.
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

  Thread* jt = Thread::current();
  StringTableHash::StatisticsTask sts(_local_table);
  if (!sts.prepare(jt)) {
    return ts;  // return old table statistics
  }
  {
    TraceTime timer("GetStatistics", TRACETIME_LOG(Debug, stringtable, perf));
    while (sts.do_task(jt, sz)) {
      sts.pause(jt);
      if (jt->is_Java_thread()) {
        ThreadBlockInVM tbivm(JavaThread::cast(jt));
      }
      sts.cont(jt);
    }
  }
  ts = sts.done(jt);
  return ts;
}

void StringTable::print_table_statistics(outputStream* st) {
  TableStatistics ts = get_table_statistics();
  ts.print(st, "StringTable");
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
class StringTable::VerifyCompStrings : StackObj {
  static unsigned string_hash(oop const& str) {
    return java_lang_String::hash_code_noupdate(str);
  }
  static bool string_equals(oop const& a, oop const& b) {
    return java_lang_String::equals(a, b);
  }

  ResizeableHashTable<oop, bool, AnyObj::C_HEAP, mtInternal,
                              string_hash, string_equals> _table;
 public:
  size_t _errors;
  VerifyCompStrings() : _table(unsigned(items_count_acquire() / 8) + 1, 0 /* do not resize */), _errors(0) {}
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
    size_t utf8_length = length;
    char* utf8_string;

    if (!is_latin1) {
      jchar* chars = value->char_at_addr(0);
      utf8_string = UNICODE::as_utf8(chars, utf8_length);
    } else {
      jbyte* bytes = value->byte_at_addr(0);
      utf8_string = UNICODE::as_utf8(bytes, utf8_length);
    }

    st->print("%zu: ", utf8_length);
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
      _shared_table.iterate_all(&pss);
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
  assert(HeapShared::is_loading_mapping_mode(), "should not reach here");
  return _shared_table.entry_count();
}

oop StringTable::lookup_shared(const StringWrapper& name, unsigned int hash) {
  if (!AOTMappedHeapLoader::is_in_use()) {
    return nullptr;
  }
  assert(hash == hash_wrapped_string(name),
         "hash must be computed using java_lang_String::hash_code");
  // len is required but is already part of StringWrapper, so 0 is used
  return _shared_table.lookup(name, hash, 0);
}

oop StringTable::lookup_shared(const jchar* name, int len) {
  if (!AOTMappedHeapLoader::is_in_use()) {
    return nullptr;
  }
  StringWrapper wrapped_name(name, len);
  // len is required but is already part of StringWrapper, so 0 is used
  return _shared_table.lookup(wrapped_name, java_lang_String::hash_code(name, len), 0);
}

void StringTable::init_shared_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "inside AOT safepoint");
  precond(CDSConfig::is_dumping_heap());
  assert(HeapShared::is_writing_mapping_mode(), "not used for streamed oops");

  int n = 0;
  auto copy_into_aot_heap = [&] (WeakHandle* val) {
    oop string = val->peek();
    if (string != nullptr && !HeapShared::is_string_too_large_to_archive(string)) {
      // If string is too large, don't put it into the string table.
      // - If there are no other references to it, it won't be stored into the archive,
      //   so we are all good.
      // - If there's a reference to it, we will report an error inside HeapShared.cpp and
      //   dumping will fail.
      HeapShared::add_to_dumped_interned_strings(string);
    }
    n++;
    return true;
  };

  _local_table->do_safepoint_scan(copy_into_aot_heap);
  log_info(aot)("Archived %d interned strings", n);
};

void StringTable::write_shared_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "inside AOT safepoint");
  precond(CDSConfig::is_dumping_heap());
  assert(HeapShared::is_writing_mapping_mode(), "not used for streamed oops");

  _shared_table.reset();
  CompactHashtableWriter writer((int)items_count_acquire(), ArchiveBuilder::string_stats());

  auto copy_into_shared_table = [&] (WeakHandle* val) {
    oop string = val->peek();
    if (string != nullptr && !HeapShared::is_string_too_large_to_archive(string)) {
      unsigned int hash = java_lang_String::hash_code(string);
      int root_id = HeapShared::append_root(string);
      writer.add(hash, root_id);
    }
    return true;
  };
  _local_table->do_safepoint_scan(copy_into_shared_table);
  writer.dump(&_shared_table, "string");
}

void StringTable::serialize_shared_table_header(SerializeClosure* soc) {
  _shared_table.serialize_header(soc);

  if (soc->writing()) {
    // Sanity. Make sure we don't use the shared table at dump time
    _shared_table.reset();
  } else if (!AOTMappedHeapLoader::is_in_use()) {
    _shared_table.reset();
  }
}

void StringTable::move_shared_strings_into_runtime_table() {
  precond(CDSConfig::is_dumping_final_static_archive());
  JavaThread* THREAD = JavaThread::current();
  HandleMark hm(THREAD);

  int n = 0;
  _shared_table.iterate_all([&](oop string) {
    int length = java_lang_String::length(string);
    Handle h_string (THREAD, string);
    StringWrapper name(h_string, length);
    unsigned int hash = hash_wrapped_string(name);

    assert(!_alt_hash, "too early");
    oop interned = do_intern(name, hash, THREAD);
    assert(string == interned, "must be");
    n++;
  });

  _shared_table.reset();
  log_info(aot)("Moved %d interned strings to runtime table", n);
}
#endif //INCLUDE_CDS_JAVA_HEAP
