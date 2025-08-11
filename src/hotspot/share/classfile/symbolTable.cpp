/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/compactHashtable.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "services/diagnosticCommand.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/concurrentHashTableTasks.inline.hpp"
#include "utilities/utf8.hpp"

// We used to not resize at all, so let's be conservative
// and not set it too short before we decide to resize,
// to match previous startup behavior
const double PREF_AVG_LIST_LEN = 8.0;
// 2^24 is max size, like StringTable.
const size_t END_SIZE = 24;
// If a chain gets to 100 something might be wrong
const size_t REHASH_LEN = 100;

const size_t ON_STACK_BUFFER_LENGTH = 128;

// --------------------------------------------------------------------------

inline bool symbol_equals_compact_hashtable_entry(Symbol* value, const char* key, int len) {
  if (value->equals(key, len)) {
    return true;
  } else {
    return false;
  }
}

static OffsetCompactHashtable<
  const char*, Symbol*,
  symbol_equals_compact_hashtable_entry
> _shared_table, _dynamic_shared_table, _shared_table_for_dumping;

// --------------------------------------------------------------------------

typedef ConcurrentHashTable<SymbolTableConfig, mtSymbol> SymbolTableHash;
static SymbolTableHash* _local_table = nullptr;

volatile bool SymbolTable::_has_work = 0;
volatile bool SymbolTable::_needs_rehashing = false;

// For statistics
static size_t _symbols_removed = 0;
static size_t _symbols_counted = 0;
static size_t _current_size = 0;

static volatile size_t _items_count = 0;
static volatile bool   _has_items_to_clean = false;


static volatile bool _alt_hash = false;

// "_lookup_shared_first" can get highly contended with many cores if multiple threads
// are updating "lookup success history" in a global shared variable, so use built-in TLS
static THREAD_LOCAL bool _lookup_shared_first = false;

// Static arena for symbols that are not deallocated
Arena* SymbolTable::_arena = nullptr;

static bool _rehashed = false;
static uint64_t _alt_hash_seed = 0;

static inline void log_trace_symboltable_helper(Symbol* sym, const char* msg) {
#ifndef PRODUCT
  ResourceMark rm;
  log_trace(symboltable)("%s [%s]", msg, sym->as_quoted_ascii());
#endif // PRODUCT
}

// Pick hashing algorithm.
static unsigned int hash_symbol(const char* s, int len, bool useAlt) {
  return useAlt ?
  AltHashing::halfsiphash_32(_alt_hash_seed, (const uint8_t*)s, len) :
  java_lang_String::hash_code((const jbyte*)s, len);
}

#if INCLUDE_CDS
static unsigned int hash_shared_symbol(const char* s, int len) {
  return java_lang_String::hash_code((const jbyte*)s, len);
}
#endif

class SymbolTableConfig : public AllStatic {

public:
  typedef Symbol Value;  // value of the Node in the hashtable

  static uintx get_hash(Value const& value, bool* is_dead) {
    *is_dead = (value.refcount() == 0);
    if (*is_dead) {
      return 0;
    } else {
      return hash_symbol((const char*)value.bytes(), value.utf8_length(), _alt_hash);
    }
  }
  // We use default allocation/deallocation but counted
  static void* allocate_node(void* context, size_t size, Value const& value) {
    SymbolTable::item_added();
    return allocate_node_impl(size, value);
  }
  static void free_node(void* context, void* memory, Value & value) {
    // We get here because #1 some threads lost a race to insert a newly created Symbol
    // or #2 we're cleaning up unused symbol.
    // If #1, then the symbol can be either permanent,
    // or regular newly created one (refcount==1)
    // If #2, then the symbol is dead (refcount==0)
    assert(value.is_permanent() || (value.refcount() == 1) || (value.refcount() == 0),
           "refcount %d", value.refcount());
#if INCLUDE_CDS
    if (CDSConfig::is_dumping_static_archive()) {
      // We have allocated with MetaspaceShared::symbol_space_alloc(). No deallocation is needed.
      // Unreferenced Symbols will not be copied into the archive.
      return;
    }
#endif
    if (value.refcount() == 1) {
      value.decrement_refcount();
      assert(value.refcount() == 0, "expected dead symbol");
    }
    if (value.refcount() != PERM_REFCOUNT) {
      FreeHeap(memory);
    } else {
      MutexLocker ml(SymbolArena_lock, Mutex::_no_safepoint_check_flag); // Protect arena
      // Deleting permanent symbol should not occur very often (insert race condition),
      // so log it.
      log_trace_symboltable_helper(&value, "Freeing permanent symbol");
      size_t alloc_size = SymbolTableHash::get_dynamic_node_size(value.byte_size());
      if (!SymbolTable::arena()->Afree(memory, alloc_size)) {
        // Can't access the symbol after Afree, but we just printed it above.
        NOT_PRODUCT(log_trace(symboltable)(" - Leaked permanent symbol");)
      }
    }
    SymbolTable::item_removed();
  }

private:
  static void* allocate_node_impl(size_t size, Value const& value) {
    size_t alloc_size = SymbolTableHash::get_dynamic_node_size(value.byte_size());
#if INCLUDE_CDS
    if (CDSConfig::is_dumping_static_archive()) {
      MutexLocker ml(DumpRegion_lock, Mutex::_no_safepoint_check_flag);
      // To get deterministic output from -Xshare:dump, we ensure that Symbols are allocated in
      // increasing addresses. When the symbols are copied into the archive, we preserve their
      // relative address order (sorted, see ArchiveBuilder::gather_klasses_and_symbols).
      //
      // We cannot use arena because arena chunks are allocated by the OS. As a result, for example,
      // the archived symbol of "java/lang/Object" may sometimes be lower than "java/lang/String", and
      // sometimes be higher. This would cause non-deterministic contents in the archive.
      DEBUG_ONLY(static void* last = nullptr);
      void* p = (void*)MetaspaceShared::symbol_space_alloc(alloc_size);
      assert(p > last, "must increase monotonically");
      DEBUG_ONLY(last = p);
      return p;
    }
#endif
    if (value.refcount() != PERM_REFCOUNT) {
      return AllocateHeap(alloc_size, mtSymbol);
    } else {
      // Allocate to global arena
      MutexLocker ml(SymbolArena_lock, Mutex::_no_safepoint_check_flag); // Protect arena
      return SymbolTable::arena()->Amalloc(alloc_size);
    }
  }
};

void SymbolTable::create_table ()  {
  size_t start_size_log_2 = log2i_ceil(SymbolTableSize);
  _current_size = ((size_t)1) << start_size_log_2;
  log_trace(symboltable)("Start size: %zu (%zu)",
                         _current_size, start_size_log_2);
  _local_table = new SymbolTableHash(start_size_log_2, END_SIZE, REHASH_LEN, true);

  // Initialize the arena for global symbols, size passed in depends on CDS.
  if (symbol_alloc_arena_size == 0) {
    _arena = new (mtSymbol) Arena(mtSymbol);
  } else {
    _arena = new (mtSymbol) Arena(mtSymbol, Arena::Tag::tag_other, symbol_alloc_arena_size);
  }
}

void SymbolTable::reset_has_items_to_clean() { Atomic::store(&_has_items_to_clean, false); }
void SymbolTable::mark_has_items_to_clean()  { Atomic::store(&_has_items_to_clean, true); }
bool SymbolTable::has_items_to_clean()       { return Atomic::load(&_has_items_to_clean); }

void SymbolTable::item_added() {
  Atomic::inc(&_items_count);
}

void SymbolTable::item_removed() {
  Atomic::inc(&(_symbols_removed));
  Atomic::dec(&_items_count);
}

double SymbolTable::get_load_factor() {
  return (double)_items_count/(double)_current_size;
}

size_t SymbolTable::table_size() {
  return ((size_t)1) << _local_table->get_size_log2(Thread::current());
}

bool SymbolTable::has_work() { return Atomic::load_acquire(&_has_work); }

void SymbolTable::trigger_cleanup() {
  // Avoid churn on ServiceThread
  if (!has_work()) {
    MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    _has_work = true;
    Service_lock->notify_all();
  }
}

class SymbolsDo : StackObj {
  SymbolClosure *_cl;
public:
  SymbolsDo(SymbolClosure *cl) : _cl(cl) {}
  bool operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    _cl->do_symbol(&value);
    return true;
  };
};

class SharedSymbolIterator {
  SymbolClosure* _symbol_closure;
public:
  SharedSymbolIterator(SymbolClosure* f) : _symbol_closure(f) {}
  void do_value(Symbol* symbol) {
    _symbol_closure->do_symbol(&symbol);
  }
};

// Call function for all symbols in the symbol table.
void SymbolTable::symbols_do(SymbolClosure *cl) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  // all symbols from shared table
  SharedSymbolIterator iter(cl);
  _shared_table.iterate(&iter);
  _dynamic_shared_table.iterate(&iter);

  // all symbols from the dynamic table
  SymbolsDo sd(cl);
  _local_table->do_safepoint_scan(sd);
}

// Call function for all symbols in shared table. Used by -XX:+PrintSharedArchiveAndExit
void SymbolTable::shared_symbols_do(SymbolClosure *cl) {
  SharedSymbolIterator iter(cl);
  _shared_table.iterate(&iter);
  _dynamic_shared_table.iterate(&iter);
}

Symbol* SymbolTable::lookup_dynamic(const char* name,
                                    int len, unsigned int hash) {
  Symbol* sym = do_lookup(name, len, hash);
  assert((sym == nullptr) || sym->refcount() != 0, "refcount must not be zero");
  return sym;
}

#if INCLUDE_CDS
Symbol* SymbolTable::lookup_shared(const char* name,
                                   int len, unsigned int hash) {
  Symbol* sym = nullptr;
  if (!_shared_table.empty()) {
    if (_alt_hash) {
      // hash_code parameter may use alternate hashing algorithm but the shared table
      // always uses the same original hash code.
      hash = hash_shared_symbol(name, len);
    }
    sym = _shared_table.lookup(name, hash, len);
    if (sym == nullptr && DynamicArchive::is_mapped()) {
      sym = _dynamic_shared_table.lookup(name, hash, len);
    }
  }
  return sym;
}
#endif

Symbol* SymbolTable::lookup_common(const char* name,
                            int len, unsigned int hash) {
  Symbol* sym;
  if (_lookup_shared_first) {
    sym = lookup_shared(name, len, hash);
    if (sym == nullptr) {
      _lookup_shared_first = false;
      sym = lookup_dynamic(name, len, hash);
    }
  } else {
    sym = lookup_dynamic(name, len, hash);
    if (sym == nullptr) {
      sym = lookup_shared(name, len, hash);
      if (sym != nullptr) {
        _lookup_shared_first = true;
      }
    }
  }
  return sym;
}

// Symbols should represent entities from the constant pool that are
// limited to <64K in length, but usage errors creep in allowing Symbols
// to be used for arbitrary strings. For debug builds we will assert if
// a string is too long, whereas product builds will truncate it.
static int check_length(const char* name, int len) {
  assert(len >= 0, "negative length %d suggests integer overflow in the caller", len);
  assert(len <= Symbol::max_length(),
         "String length %d exceeds the maximum Symbol length of %d", len, Symbol::max_length());
  if (len > Symbol::max_length()) {
    warning("A string \"%.80s ... %.80s\" exceeds the maximum Symbol "
            "length of %d and has been truncated", name, (name + len - 80), Symbol::max_length());
    len = Symbol::max_length();
  }
  return len;
}

Symbol* SymbolTable::new_symbol(const char* name, int len) {
  len = check_length(name, len);
  unsigned int hash = hash_symbol(name, len, _alt_hash);
  Symbol* sym = lookup_common(name, len, hash);
  if (sym == nullptr) {
    sym = do_add_if_needed(name, len, hash, /* is_permanent */ false);
  }
  assert(sym->refcount() != 0, "lookup should have incremented the count");
  assert(sym->equals(name, len), "symbol must be properly initialized");
  return sym;
}

Symbol* SymbolTable::new_symbol(const Symbol* sym, int begin, int end) {
  assert(begin <= end && end <= sym->utf8_length(), "just checking");
  assert(sym->refcount() != 0, "require a valid symbol");
  const char* name = (const char*)sym->base() + begin;
  int len = end - begin;
  assert(len <= Symbol::max_length(), "sanity");
  unsigned int hash = hash_symbol(name, len, _alt_hash);
  Symbol* found = lookup_common(name, len, hash);
  if (found == nullptr) {
    found = do_add_if_needed(name, len, hash, /* is_permanent */ false);
  }
  return found;
}

class SymbolTableLookup : StackObj {
private:
  uintx _hash;
  int _len;
  const char* _str;
public:
  SymbolTableLookup(const char* key, int len, uintx hash)
  : _hash(hash), _len(len), _str(key) {}
  uintx get_hash() const {
    return _hash;
  }
  // Note: When equals() returns "true", the symbol's refcount is incremented. This is
  // needed to ensure that the symbol is kept alive before equals() returns to the caller,
  // so that another thread cannot clean the symbol up concurrently. The caller is
  // responsible for decrementing the refcount, when the symbol is no longer needed.
  bool equals(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    Symbol *sym = value;
    if (sym->equals(_str, _len)) {
      if (sym->try_increment_refcount()) {
        // something is referencing this symbol now.
        return true;
      } else {
        assert(sym->refcount() == 0, "expected dead symbol");
        return false;
      }
    } else {
      return false;
    }
  }
  bool is_dead(Symbol* value) {
    return value->refcount() == 0;
  }
};

class SymbolTableGet : public StackObj {
  Symbol* _return;
public:
  SymbolTableGet() : _return(nullptr) {}
  void operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    _return = value;
  }
  Symbol* get_res_sym() const {
    return _return;
  }
};

void SymbolTable::update_needs_rehash(bool rehash) {
  if (rehash) {
    _needs_rehashing = true;
    trigger_cleanup();
  }
}

Symbol* SymbolTable::do_lookup(const char* name, int len, uintx hash) {
  Thread* thread = Thread::current();
  SymbolTableLookup lookup(name, len, hash);
  SymbolTableGet stg;
  bool rehash_warning = false;
  _local_table->get(thread, lookup, stg, &rehash_warning);
  update_needs_rehash(rehash_warning);
  Symbol* sym = stg.get_res_sym();
  assert((sym == nullptr) || sym->refcount() != 0, "found dead symbol");
  return sym;
}

Symbol* SymbolTable::lookup_only(const char* name, int len, unsigned int& hash) {
  hash = hash_symbol(name, len, _alt_hash);
  return lookup_common(name, len, hash);
}

// Suggestion: Push unicode-based lookup all the way into the hashing
// and probing logic, so there is no need for convert_to_utf8 until
// an actual new Symbol* is created.
Symbol* SymbolTable::new_symbol(const jchar* name, int utf16_length) {
  size_t utf8_length = UNICODE::utf8_length((jchar*) name, utf16_length);
  char stack_buf[ON_STACK_BUFFER_LENGTH];
  if (utf8_length < sizeof(stack_buf)) {
    char* chars = stack_buf;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return new_symbol(chars, checked_cast<int>(utf8_length));
  } else {
    ResourceMark rm;
    char* chars = NEW_RESOURCE_ARRAY(char, utf8_length + 1);
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return new_symbol(chars, checked_cast<int>(utf8_length));
  }
}

Symbol* SymbolTable::lookup_only_unicode(const jchar* name, int utf16_length,
                                         unsigned int& hash) {
  size_t utf8_length = UNICODE::utf8_length((jchar*) name, utf16_length);
  char stack_buf[ON_STACK_BUFFER_LENGTH];
  if (utf8_length < sizeof(stack_buf)) {
    char* chars = stack_buf;
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup_only(chars, checked_cast<int>(utf8_length), hash);
  } else {
    ResourceMark rm;
    char* chars = NEW_RESOURCE_ARRAY(char, utf8_length + 1);
    UNICODE::convert_to_utf8(name, utf16_length, chars);
    return lookup_only(chars, checked_cast<int>(utf8_length), hash);
  }
}

void SymbolTable::new_symbols(ClassLoaderData* loader_data, const constantPoolHandle& cp,
                              int names_count, const char** names, int* lengths,
                              int* cp_indices, unsigned int* hashValues) {
  // Note that is_permanent will be false for non-strong hidden classes.
  // even if their loader is the boot loader because they will have a different cld.
  bool is_permanent = loader_data->is_the_null_class_loader_data();
  for (int i = 0; i < names_count; i++) {
    const char *name = names[i];
    int len = lengths[i];
    assert(len <= Symbol::max_length(), "must be - these come from the constant pool");
    unsigned int hash = hashValues[i];
    assert(lookup_shared(name, len, hash) == nullptr, "must have checked already");
    Symbol* sym = do_add_if_needed(name, len, hash, is_permanent);
    assert(sym->refcount() != 0, "lookup should have incremented the count");
    cp->symbol_at_put(cp_indices[i], sym);
  }
}

Symbol* SymbolTable::do_add_if_needed(const char* name, int len, uintx hash, bool is_permanent) {
  assert(len <= Symbol::max_length(), "caller should have ensured this");
  SymbolTableLookup lookup(name, len, hash);
  SymbolTableGet stg;
  bool clean_hint = false;
  bool rehash_warning = false;
  Thread* current = Thread::current();
  Symbol* sym;

  ResourceMark rm(current);
  const int alloc_size = Symbol::byte_size(len);
  u1* u1_buf = NEW_RESOURCE_ARRAY_IN_THREAD(current, u1, alloc_size);
  Symbol* tmp = ::new ((void*)u1_buf) Symbol((const u1*)name, len,
                                             (is_permanent || CDSConfig::is_dumping_static_archive()) ? PERM_REFCOUNT : 1);

  do {
    if (_local_table->insert(current, lookup, *tmp, &rehash_warning, &clean_hint)) {
      if (_local_table->get(current, lookup, stg, &rehash_warning)) {
        sym = stg.get_res_sym();
        // The get adds one to ref count, but we inserted with our ref already included.
        // Therefore decrement with one.
        if (sym->refcount() != PERM_REFCOUNT) {
          sym->decrement_refcount();
        }
        break;
      }
    }

    // In case another thread did a concurrent add, return value already in the table.
    // This could fail if the symbol got deleted concurrently, so loop back until success.
    if (_local_table->get(current, lookup, stg, &rehash_warning)) {
      // The lookup added a refcount, which is ours.
      sym = stg.get_res_sym();
      break;
    }
  } while(true);

  update_needs_rehash(rehash_warning);

  if (clean_hint) {
    mark_has_items_to_clean();
    check_concurrent_work();
  }

  assert((sym == nullptr) || sym->refcount() != 0, "found dead symbol");
  return sym;
}

Symbol* SymbolTable::new_permanent_symbol(const char* name) {
  unsigned int hash = 0;
  int len = check_length(name, (int)strlen(name));
  Symbol* sym = SymbolTable::lookup_only(name, len, hash);
  if (sym == nullptr) {
    sym = do_add_if_needed(name, len, hash, /* is_permanent */ true);
  }
  if (!sym->is_permanent()) {
    sym->make_permanent();
    log_trace_symboltable_helper(sym, "Asked for a permanent symbol, but got a regular one");
  }
  return sym;
}

TableStatistics SymbolTable::get_table_statistics() {
  static TableStatistics ts;
  auto sz = [&](Symbol* value) {
    assert(value != nullptr, "expected valid value");
    return (value)->size() * HeapWordSize;
  };

  Thread* jt = Thread::current();
  SymbolTableHash::StatisticsTask sts(_local_table);
  if (!sts.prepare(jt)) {
    return ts;  // return old table statistics
  }
  {
    TraceTime timer("GetStatistics", TRACETIME_LOG(Debug, symboltable, perf));
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
};

void SymbolTable::print_table_statistics(outputStream* st) {
  TableStatistics ts = get_table_statistics();
  ts.print(st, "SymbolTable");

  if (!_shared_table.empty()) {
    _shared_table.print_table_statistics(st, "Shared Symbol Table");
  }

  if (!_dynamic_shared_table.empty()) {
    _dynamic_shared_table.print_table_statistics(st, "Dynamic Shared Symbol Table");
  }
}

// Verification
class VerifySymbols : StackObj {
public:
  bool operator()(Symbol* value) {
    guarantee(value != nullptr, "expected valid value");
    Symbol* sym = value;
    guarantee(sym->equals((const char*)sym->bytes(), sym->utf8_length()),
              "symbol must be internally consistent");
    return true;
  };
};

void SymbolTable::verify() {
  Thread* thr = Thread::current();
  VerifySymbols vs;
  if (!_local_table->try_scan(thr, vs)) {
    log_info(symboltable)("verify unavailable at this moment");
  }
}

static void print_symbol(outputStream* st, Symbol* sym) {
  const char* utf8_string = (const char*)sym->bytes();
  int utf8_length = sym->utf8_length();
  st->print("%d %d: ", utf8_length, sym->refcount());
  HashtableTextDump::put_utf8(st, utf8_string, utf8_length);
  st->cr();
}

// Dumping
class DumpSymbol : StackObj {
  Thread* _thr;
  outputStream* _st;
public:
  DumpSymbol(Thread* thr, outputStream* st) : _thr(thr), _st(st) {}
  bool operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    print_symbol(_st, value);
    return true;
  };
};

class DumpSharedSymbol : StackObj {
  outputStream* _st;
public:
  DumpSharedSymbol(outputStream* st) : _st(st) {}
  void do_value(Symbol* value) {
    assert(value != nullptr, "value should point to a symbol");
    print_symbol(_st, value);
  };
};

void SymbolTable::dump(outputStream* st, bool verbose) {
  if (!verbose) {
    print_table_statistics(st);
  } else {
    Thread* thr = Thread::current();
    ResourceMark rm(thr);
    st->print_cr("VERSION: 1.1");
    DumpSymbol ds(thr, st);
    if (!_local_table->try_scan(thr, ds)) {
      log_info(symboltable)("dump unavailable at this moment");
    }
    if (!_shared_table.empty()) {
      st->print_cr("#----------------");
      st->print_cr("# Shared symbols:");
      st->print_cr("#----------------");
      DumpSharedSymbol dss(st);
      _shared_table.iterate(&dss);
    }
    if (!_dynamic_shared_table.empty()) {
      st->print_cr("#------------------------");
      st->print_cr("# Dynamic shared symbols:");
      st->print_cr("#------------------------");
      DumpSharedSymbol dss(st);
      _dynamic_shared_table.iterate(&dss);
    }
  }
}

#if INCLUDE_CDS
void SymbolTable::copy_shared_symbol_table(GrowableArray<Symbol*>* symbols,
                                           CompactHashtableWriter* writer) {
  ArchiveBuilder* builder = ArchiveBuilder::current();
  int len = symbols->length();
  for (int i = 0; i < len; i++) {
    Symbol* sym = ArchiveBuilder::get_buffered_symbol(symbols->at(i));
    unsigned int fixed_hash = hash_shared_symbol((const char*)sym->bytes(), sym->utf8_length());
    assert(fixed_hash == hash_symbol((const char*)sym->bytes(), sym->utf8_length(), false),
           "must not rehash during dumping");
    sym->set_permanent();
    writer->add(fixed_hash, builder->buffer_to_offset_u4((address)sym));
  }
}

void SymbolTable::write_to_archive(GrowableArray<Symbol*>* symbols) {
  CompactHashtableWriter writer(int(_items_count), ArchiveBuilder::symbol_stats());
  copy_shared_symbol_table(symbols, &writer);
  _shared_table_for_dumping.reset();
  writer.dump(&_shared_table_for_dumping, "symbol");
}

void SymbolTable::serialize_shared_table_header(SerializeClosure* soc,
                                                bool is_static_archive) {
  OffsetCompactHashtable<const char*, Symbol*, symbol_equals_compact_hashtable_entry> * table;
  if (soc->reading()) {
    if (is_static_archive) {
      table = &_shared_table;
    } else {
      table = &_dynamic_shared_table;
    }
  } else {
    table = &_shared_table_for_dumping;
  }

  table->serialize_header(soc);
}
#endif //INCLUDE_CDS

// Concurrent work
void SymbolTable::grow(JavaThread* jt) {
  SymbolTableHash::GrowTask gt(_local_table);
  if (!gt.prepare(jt)) {
    return;
  }
  log_trace(symboltable)("Started to grow");
  {
    TraceTime timer("Grow", TRACETIME_LOG(Debug, symboltable, perf));
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
  log_debug(symboltable)("Grown to size:%zu", _current_size);
}

struct SymbolTableDoDelete : StackObj {
  size_t _deleted;
  SymbolTableDoDelete() : _deleted(0) {}
  void operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    Symbol *sym = value;
    assert(sym->refcount() == 0, "refcount");
    _deleted++;
  }
};

struct SymbolTableDeleteCheck : StackObj {
  size_t _processed;
  SymbolTableDeleteCheck() : _processed(0) {}
  bool operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    _processed++;
    Symbol *sym = value;
    return (sym->refcount() == 0);
  }
};

void SymbolTable::clean_dead_entries(JavaThread* jt) {
  SymbolTableHash::BulkDeleteTask bdt(_local_table);
  if (!bdt.prepare(jt)) {
    return;
  }

  SymbolTableDeleteCheck stdc;
  SymbolTableDoDelete stdd;
  NativeHeapTrimmer::SuspendMark sm("symboltable");
  {
    TraceTime timer("Clean", TRACETIME_LOG(Debug, symboltable, perf));
    while (bdt.do_task(jt, stdc, stdd)) {
      bdt.pause(jt);
      {
        ThreadBlockInVM tbivm(jt);
      }
      bdt.cont(jt);
    }
    reset_has_items_to_clean();
    bdt.done(jt);
  }

  Atomic::add(&_symbols_counted, stdc._processed);

  log_debug(symboltable)("Cleaned %zu of %zu",
                         stdd._deleted, stdc._processed);
}

void SymbolTable::check_concurrent_work() {
  if (has_work()) {
    return;
  }
  // We should clean/resize if we have
  // more items than preferred load factor or
  // more dead items than water mark.
  if (has_items_to_clean() || (get_load_factor() > PREF_AVG_LIST_LEN)) {
    log_debug(symboltable)("Concurrent work triggered, load factor: %f, items to clean: %s",
                           get_load_factor(), has_items_to_clean() ? "true" : "false");
    trigger_cleanup();
  }
}

bool SymbolTable::should_grow() {
  return get_load_factor() > PREF_AVG_LIST_LEN && !_local_table->is_max_size_reached();
}

void SymbolTable::do_concurrent_work(JavaThread* jt) {
  // Rehash if needed.  Rehashing goes to a safepoint but the rest of this
  // work is concurrent.
  if (needs_rehashing() && maybe_rehash_table()) {
    Atomic::release_store(&_has_work, false);
    return; // done, else grow
  }
  log_debug(symboltable, perf)("Concurrent work, live factor: %g", get_load_factor());
  // We prefer growing, since that also removes dead items
  if (should_grow()) {
    grow(jt);
  } else {
    clean_dead_entries(jt);
  }
  Atomic::release_store(&_has_work, false);
}

// Called at VM_Operation safepoint
void SymbolTable::rehash_table() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be called at safepoint");
  // The ServiceThread initiates the rehashing so it is not resizing.
  assert (_local_table->is_safepoint_safe(), "Should not be resizing now");

  _alt_hash_seed = AltHashing::compute_seed();

  // We use current size
  size_t new_size = _local_table->get_size_log2(Thread::current());
  SymbolTableHash* new_table = new SymbolTableHash(new_size, END_SIZE, REHASH_LEN, true);
  // Use alt hash from now on
  _alt_hash = true;
  _local_table->rehash_nodes_to(Thread::current(), new_table);

  // free old table
  delete _local_table;
  _local_table = new_table;

  _rehashed = true;
  _needs_rehashing = false;
}

bool SymbolTable::maybe_rehash_table() {
  log_debug(symboltable)("Table imbalanced, rehashing called.");

  // Grow instead of rehash.
  if (should_grow()) {
    log_debug(symboltable)("Choosing growing over rehashing.");
    _needs_rehashing = false;
    return false;
  }

  // Already rehashed.
  if (_rehashed) {
    log_warning(symboltable)("Rehashing already done, still long lists.");
    _needs_rehashing = false;
    return false;
  }

  VM_RehashSymbolTable op;
  VMThread::execute(&op);
  return true;
}

//---------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

class HistogramIterator : StackObj {
public:
  static const size_t results_length = 100;
  size_t counts[results_length];
  size_t sizes[results_length];
  size_t total_size;
  size_t total_count;
  size_t total_length;
  size_t max_length;
  size_t out_of_range_count;
  size_t out_of_range_size;
  HistogramIterator() : total_size(0), total_count(0), total_length(0),
                        max_length(0), out_of_range_count(0), out_of_range_size(0) {
    // initialize results to zero
    for (size_t i = 0; i < results_length; i++) {
      counts[i] = 0;
      sizes[i] = 0;
    }
  }
  bool operator()(Symbol* value) {
    assert(value != nullptr, "expected valid value");
    Symbol* sym = value;
    size_t size = sym->size();
    size_t len = sym->utf8_length();
    if (len < results_length) {
      counts[len]++;
      sizes[len] += size;
    } else {
      out_of_range_count++;
      out_of_range_size += size;
    }
    total_count++;
    total_size += size;
    total_length += len;
    max_length = MAX2(max_length, len);

    return true;
  };
};

void SymbolTable::print_histogram() {
  HistogramIterator hi;
  _local_table->do_scan(Thread::current(), hi);
  tty->print_cr("Symbol Table Histogram:");
  tty->print_cr("  Total number of symbols  %7zu", hi.total_count);
  tty->print_cr("  Total size in memory     %7zuK", (hi.total_size * wordSize) / K);
  tty->print_cr("  Total counted            %7zu", _symbols_counted);
  tty->print_cr("  Total removed            %7zu", _symbols_removed);
  if (_symbols_counted > 0) {
    tty->print_cr("  Percent removed          %3.2f",
          ((double)_symbols_removed / (double)_symbols_counted) * 100);
  }
  tty->print_cr("  Reference counts         %7zu", Symbol::_total_count);
  tty->print_cr("  Symbol arena used        %7zuK", arena()->used() / K);
  tty->print_cr("  Symbol arena size        %7zuK", arena()->size_in_bytes() / K);
  tty->print_cr("  Total symbol length      %7zu", hi.total_length);
  tty->print_cr("  Maximum symbol length    %7zu", hi.max_length);
  tty->print_cr("  Average symbol length    %7.2f", ((double)hi.total_length / (double)hi.total_count));
  tty->print_cr("  Symbol length histogram:");
  tty->print_cr("    %6s %10s %10s", "Length", "#Symbols", "Size");
  for (size_t i = 0; i < hi.results_length; i++) {
    if (hi.counts[i] > 0) {
      tty->print_cr("    %6zu %10zu %10zuK",
                    i, hi.counts[i], (hi.sizes[i] * wordSize) / K);
    }
  }
  tty->print_cr("  >= %6zu %10zu %10zuK\n",
                hi.results_length, hi.out_of_range_count, (hi.out_of_range_size*wordSize) / K);
}
#endif // PRODUCT

// Utility for dumping symbols
SymboltableDCmd::SymboltableDCmd(outputStream* output, bool heap) :
                                 DCmdWithParser(output, heap),
  _verbose("-verbose", "Dump the content of each symbol in the table",
           "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_verbose);
}

void SymboltableDCmd::execute(DCmdSource source, TRAPS) {
  VM_DumpHashtable dumper(output(), VM_DumpHashtable::DumpSymbols,
                         _verbose.value());
  VMThread::execute(&dumper);
}
