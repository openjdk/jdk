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

static JfrSymbolTable::StringEntry* bootstrap = NULL;

static JfrSymbolTable* _instance = NULL;

static JfrSymbolTable& instance() {
  assert(_instance != NULL, "invariant");
  return *_instance;
}

JfrSymbolTable* JfrSymbolTable::create() {
  assert(_instance == NULL, "invariant");
  assert_lock_strong(ClassLoaderDataGraph_lock);
  _instance = new JfrSymbolTable();
  return _instance;
}

void JfrSymbolTable::destroy() {
  assert_lock_strong(ClassLoaderDataGraph_lock);
  if (_instance != NULL) {
    delete _instance;
    _instance = NULL;
  }
  assert(_instance == NULL, "invariant");
}

JfrSymbolTable::JfrSymbolTable() :
  _symbols(new Symbols(this)),
  _strings(new Strings(this)),
  _symbol_list(NULL),
  _string_list(NULL),
  _symbol_query(NULL),
  _string_query(NULL),
  _id_counter(1),
  _class_unload(false) {
  assert(_symbols != NULL, "invariant");
  assert(_strings != NULL, "invariant");
  bootstrap = new StringEntry(0, (const char*)&BOOTSTRAP_LOADER_NAME);
  assert(bootstrap != NULL, "invariant");
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
  assert(_symbols != NULL, "invariant");
  if (_symbols->has_entries()) {
    _symbols->clear_entries();
  }
  assert(!_symbols->has_entries(), "invariant");

  assert(_strings != NULL, "invariant");
  if (_strings->has_entries()) {
    _strings->clear_entries();
  }
  assert(!_strings->has_entries(), "invariant");

  _symbol_list = NULL;
  _id_counter = 1;

  _symbol_query = NULL;
  _string_query = NULL;

  assert(bootstrap != NULL, "invariant");
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
  assert(entry != NULL, "invariant");
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
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  assert(_symbol_query != NULL, "invariant");
  return _symbol_query == entry->literal();
}

void JfrSymbolTable::on_unlink(const SymbolEntry* entry) {
  assert(entry != NULL, "invariant");
  const_cast<Symbol*>(entry->literal())->decrement_refcount();
}

static const char* resource_to_c_heap_string(const char* resource_str) {
  assert(resource_str != NULL, "invariant");
  const size_t length = strlen(resource_str);
  char* const c_string = JfrCHeapObj::new_array<char>(length + 1);
  assert(c_string != NULL, "invariant");
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
  assert(query != NULL, "invariant");
  assert(candidate != NULL, "invariant");
  const size_t length = strlen(query);
  return strncmp(query, candidate, length) == 0;
}

bool JfrSymbolTable::on_equals(uintptr_t hash, const StringEntry* entry) {
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  assert(_string_query != NULL, "invariant");
  return string_compare(_string_query, entry->literal());
}

void JfrSymbolTable::on_unlink(const StringEntry* entry) {
  assert(entry != NULL, "invariant");
  JfrCHeapObj::free(const_cast<char*>(entry->literal()), strlen(entry->literal() + 1));
}

traceid JfrSymbolTable::bootstrap_name(bool leakp) {
  assert(bootstrap != NULL, "invariant");
  if (leakp) {
    bootstrap->set_leakp();
  }
  return bootstrap->id();
}

traceid JfrSymbolTable::mark(const Symbol* sym, bool leakp /* false */) {
  assert(sym != NULL, "invariant");
  return mark((uintptr_t)sym->identity_hash(), sym, leakp);
}

traceid JfrSymbolTable::mark(uintptr_t hash, const Symbol* sym, bool leakp) {
  assert(sym != NULL, "invariant");
  assert(_symbols != NULL, "invariant");
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
  assert(str != NULL, "invariant");
  assert(_strings != NULL, "invariant");
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
  assert(ik != NULL, "invariant");
  assert(ik->is_hidden(), "invariant");
  const oop mirror = ik->java_mirror_no_keepalive();
  assert(mirror != NULL, "invariant");
  return (uintptr_t)mirror->identity_hash();
}

static const char* create_hidden_klass_symbol(const InstanceKlass* ik, uintptr_t hash) {
  assert(ik != NULL, "invariant");
  assert(ik->is_hidden(), "invariant");
  assert(hash != 0, "invariant");
  char* hidden_symbol = NULL;
  const oop mirror = ik->java_mirror_no_keepalive();
  assert(mirror != NULL, "invariant");
  char hash_buf[40];
  sprintf(hash_buf, "/" UINTX_FORMAT, hash);
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
  assert(k != NULL, "invariant");
  return k->is_instance_klass() && ((const InstanceKlass*)k)->is_hidden();
}

traceid JfrSymbolTable::mark_hidden_klass_name(const InstanceKlass* ik, bool leakp) {
  assert(ik != NULL, "invariant");
  assert(ik->is_hidden(), "invariant");
  const uintptr_t hash = hidden_klass_name_hash(ik);
  const char* const hidden_symbol = create_hidden_klass_symbol(ik, hash);
  return mark(hash, hidden_symbol, leakp);
}

traceid JfrSymbolTable::mark(const Klass* k, bool leakp) {
  assert(k != NULL, "invariant");
  traceid symbol_id = 0;
  if (is_hidden_klass(k)) {
    assert(k->is_instance_klass(), "invariant");
    symbol_id = mark_hidden_klass_name((const InstanceKlass*)k, leakp);
  } else {
    Symbol* const sym = k->name();
    if (sym != NULL) {
      symbol_id = mark(sym, leakp);
    }
  }
  assert(symbol_id > 0, "a symbol handler must mark the symbol for writing");
  return symbol_id;
}

template <typename T>
traceid JfrSymbolTable::add_impl(const T* sym) {
  assert(sym != NULL, "invariant");
  assert(_instance != NULL, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  return instance().mark(sym);
}

traceid JfrSymbolTable::add(const Symbol* sym) {
  return add_impl(sym);
}

traceid JfrSymbolTable::add(const char* str) {
  return add_impl(str);
}
