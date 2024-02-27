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
#include "classfile/classLoaderData.hpp"
#include "jfr/support/jfrSymbolTable.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/mutexLocker.hpp"

// incremented on each rotation
static u8 checkpoint_id = 1;

// creates a unique id by combining a checkpoint relative symbol id (2^24)
// with the current checkpoint id (2^40)
#define CREATE_SYMBOL_ID(sym_id) (((u8)((checkpoint_id << 24) | sym_id)))

static traceid create_symbol_id(traceid artifact_id) {
  return artifact_id != 0 ? CREATE_SYMBOL_ID(artifact_id) : 0;
}

static uintptr_t string_hash(const char* str) {
  return java_lang_String::hash_code(reinterpret_cast<const jbyte*>(str), static_cast<int>(strlen(str)));
}

static JfrSymbolTable::StringEntry* bootstrap = nullptr;

static JfrSymbolTable* _instance = nullptr;

static JfrSymbolTable& instance() {
  assert(_instance != nullptr, "invariant");
  return *_instance;
}

JfrSymbolTable* JfrSymbolTable::create() {
  assert(_instance == nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  _instance = new JfrSymbolTable();
  return _instance;
}

void JfrSymbolTable::destroy() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (_instance != nullptr) {
    delete _instance;
    _instance = nullptr;
  }
  assert(_instance == nullptr, "invariant");
}

JfrSymbolTable::JfrSymbolTable() :
  _symbols(new Symbols(this)),
  _strings(new Strings(this)),
  _symbol_list(nullptr),
  _string_list(nullptr),
  _symbol_query(nullptr),
  _string_query(nullptr),
  _id_counter(1),
  _class_unload(false) {
  assert(_symbols != nullptr, "invariant");
  assert(_strings != nullptr, "invariant");
  bootstrap = new StringEntry(0, (const char*)&BOOTSTRAP_LOADER_NAME);
  assert(bootstrap != nullptr, "invariant");
  bootstrap->set_id(create_symbol_id(1));
  _string_list = bootstrap;
}

JfrSymbolTable::~JfrSymbolTable() {
  clear();
  delete _symbols;
  delete _strings;
  delete bootstrap;
}

void JfrSymbolTable::clear() {
  assert(_symbols != nullptr, "invariant");
  if (_symbols->has_entries()) {
    _symbols->clear_entries();
  }
  assert(!_symbols->has_entries(), "invariant");

  assert(_strings != nullptr, "invariant");
  if (_strings->has_entries()) {
    _strings->clear_entries();
  }
  assert(!_strings->has_entries(), "invariant");

  _symbol_list = nullptr;
  _id_counter = 1;

  _symbol_query = nullptr;
  _string_query = nullptr;

  assert(bootstrap != nullptr, "invariant");
  bootstrap->reset();
  _string_list = bootstrap;
}

void JfrSymbolTable::set_class_unload(bool class_unload) {
  _class_unload = class_unload;
}

void JfrSymbolTable::increment_checkpoint_id() {
  assert_lock_strong(ClassLoaderDataGraph_lock);
  clear();
  ++checkpoint_id;
}

template <typename T>
inline void JfrSymbolTable::assign_id(T* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(create_symbol_id(++_id_counter));
}

void JfrSymbolTable::on_link(const SymbolEntry* entry) {
  assign_id(entry);
  const_cast<Symbol*>(entry->literal())->increment_refcount();
  entry->set_list_next(_symbol_list);
  _symbol_list = entry;
}

bool JfrSymbolTable::on_equals(uintptr_t hash, const SymbolEntry* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->hash() == hash, "invariant");
  assert(_symbol_query != nullptr, "invariant");
  return _symbol_query == entry->literal();
}

void JfrSymbolTable::on_unlink(const SymbolEntry* entry) {
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

void JfrSymbolTable::on_link(const StringEntry* entry) {
  assign_id(entry);
  const_cast<StringEntry*>(entry)->set_literal(resource_to_c_heap_string(entry->literal()));
  entry->set_list_next(_string_list);
  _string_list = entry;
}

static bool string_compare(const char* query, const char* candidate) {
  assert(query != nullptr, "invariant");
  assert(candidate != nullptr, "invariant");
  const size_t length = strlen(query);
  return strncmp(query, candidate, length) == 0;
}

bool JfrSymbolTable::on_equals(uintptr_t hash, const StringEntry* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->hash() == hash, "invariant");
  assert(_string_query != nullptr, "invariant");
  return string_compare(_string_query, entry->literal());
}

void JfrSymbolTable::on_unlink(const StringEntry* entry) {
  assert(entry != nullptr, "invariant");
  JfrCHeapObj::free(const_cast<char*>(entry->literal()), strlen(entry->literal() + 1));
}

traceid JfrSymbolTable::bootstrap_name(bool leakp) {
  assert(bootstrap != nullptr, "invariant");
  if (leakp) {
    bootstrap->set_leakp();
  }
  return bootstrap->id();
}

traceid JfrSymbolTable::mark(const Symbol* sym, bool leakp /* false */) {
  assert(sym != nullptr, "invariant");
  return mark((uintptr_t)sym->identity_hash(), sym, leakp);
}

traceid JfrSymbolTable::mark(uintptr_t hash, const Symbol* sym, bool leakp) {
  assert(sym != nullptr, "invariant");
  assert(_symbols != nullptr, "invariant");
  _symbol_query = sym;
  const SymbolEntry& entry = _symbols->lookup_put(hash, sym);
  if (_class_unload) {
    entry.set_unloading();
  }
  if (leakp) {
    entry.set_leakp();
  }
  return entry.id();
}

traceid JfrSymbolTable::mark(const char* str, bool leakp /* false*/) {
  return mark(string_hash(str), str, leakp);
}

traceid JfrSymbolTable::mark(uintptr_t hash, const char* str, bool leakp) {
  assert(str != nullptr, "invariant");
  assert(_strings != nullptr, "invariant");
  _string_query = str;
  const StringEntry& entry = _strings->lookup_put(hash, str);
  if (_class_unload) {
    entry.set_unloading();
  }
  if (leakp) {
    entry.set_leakp();
  }
  return entry.id();
}

/*
* hidden classes symbol is the external name +
* the address of its InstanceKlass slash appended:
*   java.lang.invoke.LambdaForm$BMH/22626602
*
* caller needs ResourceMark
*/

uintptr_t JfrSymbolTable::hidden_klass_name_hash(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert(ik->is_hidden(), "invariant");
  const oop mirror = ik->java_mirror_no_keepalive();
  assert(mirror != nullptr, "invariant");
  return (uintptr_t)mirror->identity_hash();
}

static const char* create_hidden_klass_symbol(const InstanceKlass* ik, uintptr_t hash) {
  assert(ik != nullptr, "invariant");
  assert(ik->is_hidden(), "invariant");
  assert(hash != 0, "invariant");
  char* hidden_symbol = nullptr;
  const oop mirror = ik->java_mirror_no_keepalive();
  assert(mirror != nullptr, "invariant");
  char hash_buf[40];
  os::snprintf_checked(hash_buf, sizeof(hash_buf), "/" UINTX_FORMAT, hash);
  const size_t hash_len = strlen(hash_buf);
  const size_t result_len = ik->name()->utf8_length();
  hidden_symbol = NEW_RESOURCE_ARRAY(char, result_len + hash_len + 1);
  ik->name()->as_klass_external_name(hidden_symbol, (int)result_len + 1);
  assert(strlen(hidden_symbol) == result_len, "invariant");
  strcpy(hidden_symbol + result_len, hash_buf);
  assert(strlen(hidden_symbol) == result_len + hash_len, "invariant");
  return hidden_symbol;
}

bool JfrSymbolTable::is_hidden_klass(const Klass* k) {
  assert(k != nullptr, "invariant");
  return k->is_instance_klass() && ((const InstanceKlass*)k)->is_hidden();
}

traceid JfrSymbolTable::mark_hidden_klass_name(const InstanceKlass* ik, bool leakp) {
  assert(ik != nullptr, "invariant");
  assert(ik->is_hidden(), "invariant");
  const uintptr_t hash = hidden_klass_name_hash(ik);
  const char* const hidden_symbol = create_hidden_klass_symbol(ik, hash);
  return mark(hash, hidden_symbol, leakp);
}

traceid JfrSymbolTable::mark(const Klass* k, bool leakp) {
  assert(k != nullptr, "invariant");
  traceid symbol_id = 0;
  if (is_hidden_klass(k)) {
    assert(k->is_instance_klass(), "invariant");
    symbol_id = mark_hidden_klass_name((const InstanceKlass*)k, leakp);
  } else {
    Symbol* const sym = k->name();
    if (sym != nullptr) {
      symbol_id = mark(sym, leakp);
    }
  }
  assert(symbol_id > 0, "a symbol handler must mark the symbol for writing");
  return symbol_id;
}

template <typename T>
traceid JfrSymbolTable::add_impl(const T* sym) {
  assert(sym != nullptr, "invariant");
  assert(_instance != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  return instance().mark(sym);
}

traceid JfrSymbolTable::add(const Symbol* sym) {
  return add_impl(sym);
}

traceid JfrSymbolTable::add(const char* str) {
  return add_impl(str);
}
