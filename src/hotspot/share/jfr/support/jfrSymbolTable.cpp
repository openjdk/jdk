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

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/support/jfrSymbolTable.inline.hpp"
#include "oops/klass.hpp"
#include "oops/symbol.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/mutexLocker.hpp"

JfrSymbolTable::Impl* JfrSymbolTable::_epoch_0 = nullptr;
JfrSymbolTable::Impl* JfrSymbolTable::_epoch_1 = nullptr;
JfrSymbolTable::StringEntry* JfrSymbolTable::_bootstrap = nullptr;

JfrSymbolCallback::JfrSymbolCallback() : _id_counter(2) {} // 1 is reserved for "bootstrap" entry

template <typename T>
inline void JfrSymbolCallback::assign_id(const T* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(AtomicAccess::fetch_then_add(&_id_counter, (traceid)1));
}

void JfrSymbolCallback::on_link(const JfrSymbolTable::SymbolEntry* entry) {
  assign_id(entry);
  const_cast<Symbol*>(entry->literal())->increment_refcount();
}

void JfrSymbolCallback::on_unlink(const JfrSymbolTable::SymbolEntry* entry) {
  assert(entry != nullptr, "invariant");
  const_cast<Symbol*>(entry->literal())->decrement_refcount();
}

static const char* resource_to_c_heap_string(const char* resource_str) {
  assert(resource_str != nullptr, "invariant");
  const size_t length = strlen(resource_str);
  char* const c_string = JfrCHeapObj::new_array<char>(length + 1);
  assert(c_string != nullptr, "invariant");
  strncpy(c_string, resource_str, length + 1);
  return c_string;
}

void JfrSymbolCallback::on_link(const JfrSymbolTable::StringEntry* entry) {
  assign_id(entry);
  const_cast<JfrSymbolTable::StringEntry*>(entry)->set_literal(resource_to_c_heap_string(entry->literal()));
}

void JfrSymbolCallback::on_unlink(const JfrSymbolTable::StringEntry* entry) {
  assert(entry != nullptr, "invariant");
  JfrCHeapObj::free(const_cast<char*>(entry->literal()), strlen(entry->literal()) + 1);
}

static JfrSymbolCallback* _callback = nullptr;

template <typename T, typename IdType>
JfrSymbolTableEntry<T, IdType>::JfrSymbolTableEntry(unsigned hash, const T& data) :
  JfrConcurrentHashtableEntry<T, IdType>(hash, data), _serialized(false), _unloading(false), _leakp(false) {}

template <typename T, typename IdType>
bool JfrSymbolTableEntry<T, IdType>::on_equals(const char* str) {
  assert(str != nullptr, "invariant");
  return strcmp((const char*)this->literal(), str) == 0;
}

static const constexpr unsigned max_capacity = 1 << 30;

static inline unsigned calculate_capacity(unsigned size, unsigned capacity) {
  assert(is_power_of_2(capacity), "invariant");
  assert(capacity <= max_capacity, "invariant");
  double load_factor = (double)size / (double)capacity;
  if (load_factor < 0.75) {
    return capacity;
  }
  do {
    capacity <<= 1;
    assert(is_power_of_2(capacity), "invariant");
    guarantee(capacity <= max_capacity, "overflow");
    load_factor = (double)size / (double)capacity;
  } while (load_factor >= 0.75);
  return capacity;
}

bool JfrSymbolTable::create() {
  assert(_callback == nullptr, "invariant");
  // Allocate callback instance before tables.
  _callback = new JfrSymbolCallback();
  if (_callback == nullptr) {
    return false;
  }
  assert(_bootstrap == nullptr, "invariant");
  _bootstrap = new StringEntry(0, (const char*)&BOOTSTRAP_LOADER_NAME);
  if (_bootstrap == nullptr) {
    return false;
  }
  _bootstrap->set_id(1);
  assert(this_epoch_table() == nullptr, "invariant");
  Impl* table = new JfrSymbolTable::Impl();
  if (table == nullptr) {
    return false;
  }
  set_this_epoch(table);
  assert(previous_epoch_table() == nullptr, "invariant");
  return true;
}

void JfrSymbolTable::destroy() {
  if (_callback != nullptr) {
    delete _callback;
    _callback = nullptr;
  }
  if (_bootstrap != nullptr) {
    delete _bootstrap;
    _bootstrap = nullptr;
  }
  if (_epoch_0 != nullptr) {
    delete _epoch_0;
    _epoch_0 = nullptr;
  }
  if (_epoch_1 != nullptr) {
    delete _epoch_1;
    _epoch_1 = nullptr;
  }
}

void JfrSymbolTable::allocate_next_epoch() {
  assert(nullptr == previous_epoch_table(), "invariant");
  const Impl* const current = this_epoch_table();
  assert(current != nullptr, "invariant");
  const unsigned next_symbols_capacity = calculate_capacity(current->symbols_size(), current->symbols_capacity());
  const unsigned next_strings_capacity = calculate_capacity(current->strings_size(), current->strings_capacity());
  assert(_callback != nullptr, "invariant");
  // previous epoch to become the next epoch.
  set_previous_epoch(new JfrSymbolTable::Impl(next_symbols_capacity, next_strings_capacity));
  assert(this_epoch_table() != nullptr, "invariant");
  assert(previous_epoch_table() != nullptr, "invariant");
}

void JfrSymbolTable::clear_previous_epoch() {
  Impl* const table = previous_epoch_table();
  assert(table != nullptr, "invariant");
  set_previous_epoch(nullptr);
  delete table;
  assert(_bootstrap != nullptr, "invariant");
  _bootstrap->reset();
  assert(!_bootstrap->is_serialized(), "invariant");
}

void JfrSymbolTable::set_this_epoch(JfrSymbolTable::Impl* table) {
  assert(table != nullptr, "invariant");
  const u1 epoch = JfrTraceIdEpoch::current();
  if (epoch == 0) {
    _epoch_0 = table;
  } else {
    _epoch_1 = table;
  }
}

void JfrSymbolTable::set_previous_epoch(JfrSymbolTable::Impl* table) {
  const u1 epoch = JfrTraceIdEpoch::previous();
  if (epoch == 0) {
    _epoch_0 = table;
  } else {
    _epoch_1 = table;
  }
}

inline bool JfrSymbolTable::Impl::has_symbol_entries() const {
  return _symbols->is_nonempty();
}

inline bool JfrSymbolTable::Impl::has_string_entries() const {
  return _strings->is_nonempty();
}

inline bool JfrSymbolTable::Impl::has_entries() const {
  return has_symbol_entries() || has_string_entries();
}

inline unsigned JfrSymbolTable::Impl::symbols_capacity() const {
  return _symbols->capacity();
}

inline unsigned JfrSymbolTable::Impl::symbols_size() const {
  return _symbols->size();
}

inline unsigned JfrSymbolTable::Impl::strings_capacity() const {
  return _strings->capacity();
}

inline unsigned JfrSymbolTable::Impl::strings_size() const {
  return _strings->size();
}

bool JfrSymbolTable::has_entries(bool previous_epoch /* false */) {
  const Impl* table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->has_entries();
}

bool JfrSymbolTable::has_symbol_entries(bool previous_epoch /* false */) {
  const Impl* table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->has_symbol_entries();
}

bool JfrSymbolTable::has_string_entries(bool previous_epoch /* false */) {
  const Impl* table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->has_string_entries();
}

traceid JfrSymbolTable::bootstrap_name(bool leakp) {
  assert(_bootstrap != nullptr, "invariant");
  if (leakp) {
    _bootstrap->set_leakp();
  }
  return _bootstrap->id();
}

JfrSymbolTable::Impl::Impl(unsigned symbols_capacity /* 0*/, unsigned strings_capacity /* 0 */) :
  _symbols(new Symbols(_callback, symbols_capacity)), _strings(new Strings(_callback, strings_capacity)) {}

JfrSymbolTable::Impl::~Impl() {
  delete _symbols;
  delete _strings;
}

traceid JfrSymbolTable::Impl::mark(const Symbol* sym, bool leakp /* false */, bool class_unload /* false */) {
  assert(sym != nullptr, "invariant");
  return mark(sym->identity_hash(), sym, leakp, class_unload);
}

traceid JfrSymbolTable::Impl::mark(unsigned hash, const Symbol* sym, bool leakp, bool class_unload /* false */) {
  assert(sym != nullptr, "invariant");
  assert(_symbols != nullptr, "invariant");
  const SymbolEntry* entry = _symbols->lookup_put(hash, sym);
  assert(entry != nullptr, "invariant");
  if (leakp) {
    entry->set_leakp();
  } else if (class_unload) {
    entry->set_unloading();
  }
  return entry->id();
}

traceid JfrSymbolTable::mark(const Symbol* sym, bool leakp /* false */, bool class_unload /* false */, bool previous_epoch /* false */) {
  Impl* const table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->mark(sym->identity_hash(), sym, leakp, class_unload);
}

static inline unsigned string_hash(const char* str) {
  return java_lang_String::hash_code(reinterpret_cast<const jbyte*>(str), static_cast<int>(strlen(str)));
}

traceid JfrSymbolTable::Impl::mark(const char* str, bool leakp /* false*/, bool class_unload /* false */) {
  return mark(string_hash(str), str, leakp, class_unload);
}

traceid JfrSymbolTable::Impl::mark(unsigned hash, const char* str, bool leakp, bool class_unload /* false */) {
  assert(str != nullptr, "invariant");
  assert(_strings != nullptr, "invariant");
  const StringEntry* entry = _strings->lookup_put(hash, str);
  assert(entry != nullptr, "invariant");
  if (leakp) {
    entry->set_leakp();
  } else if (class_unload) {
    entry->set_unloading();
  }
  return entry->id();
}

traceid JfrSymbolTable::mark(unsigned hash, const char* str, bool leakp, bool class_unload /* false */, bool previous_epoch /* false */) {
  Impl* const table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->mark(hash, str, leakp, class_unload);
}

/*
 * The hidden class symbol is the external name with the
 * address of its Klass slash appended.
 *
 * "java.lang.invoke.LambdaForm$DMH/0x0000000037144c00"
 *
 * Caller needs ResourceMark.
 */
inline traceid JfrSymbolTable::Impl::mark_hidden_klass_name(const Klass* k, bool leakp, bool class_unload /* false */) {
  assert(k != nullptr, "invariant");
  assert(k->is_hidden(), "invariant");
  return mark(k->name()->identity_hash(), k->external_name(), leakp, class_unload);
}

traceid JfrSymbolTable::Impl::mark(const Klass* k, bool leakp, bool class_unload /* false */) {
  assert(k != nullptr, "invariant");
  traceid symbol_id = 0;
  if (k->is_hidden()) {
    symbol_id = mark_hidden_klass_name(k, leakp, class_unload);
  } else {
    Symbol* const sym = k->name();
    if (sym != nullptr) {
      symbol_id = mark(sym, leakp, class_unload);
    }
  }
  assert(symbol_id > 0, "a symbol handler must mark the symbol for writing");
  return symbol_id;
}

traceid JfrSymbolTable::mark(const Klass* k, bool leakp, bool class_unload /* false */, bool previous_epoch /* false */) {
  Impl* const table = previous_epoch ? previous_epoch_table() : this_epoch_table();
  assert(table != nullptr, "invariant");
  return table->mark(k, leakp, class_unload);
}

inline traceid JfrSymbolTable::Impl::add(const Symbol* sym) {
  assert(sym != nullptr, "invariant");
  return _symbols->lookup_put(sym->identity_hash(), sym)->id();
}

traceid JfrSymbolTable::Impl::add(const char* str) {
  assert(str != nullptr, "invariant");
  return _strings->lookup_put(string_hash(str), str)->id();
}

inline traceid JfrSymbolTable::add(const Symbol* sym) {
  return this_epoch_table()->add(sym);
}

traceid JfrSymbolTable::add(const char* str) {
  return this_epoch_table()->add(str);
}
