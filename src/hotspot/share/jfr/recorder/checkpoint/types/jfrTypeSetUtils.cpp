/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"

JfrSymbolId::JfrSymbolId() : _symbol_id_counter(0), _sym_table(new SymbolTable(this)), _cstring_table(new CStringTable(this)) {
  assert(_sym_table != NULL, "invariant");
  assert(_cstring_table != NULL, "invariant");
  initialize();
}

void JfrSymbolId::initialize() {
  clear();
  assert(_symbol_id_counter == 0, "invariant");
}

void JfrSymbolId::clear() {
  assert(_sym_table != NULL, "invariant");
  if (_sym_table->has_entries()) {
    _sym_table->clear_entries();
  }
  assert(!_sym_table->has_entries(), "invariant");

  assert(_cstring_table != NULL, "invariant");
  if (_cstring_table->has_entries()) {
    _cstring_table->clear_entries();
  }
  assert(!_cstring_table->has_entries(), "invariant");
  _symbol_id_counter = 0;
}

JfrSymbolId::~JfrSymbolId() {
  delete _sym_table;
  delete _cstring_table;
}

traceid JfrSymbolId::mark_anonymous_klass_name(const Klass* k) {
  assert(k != NULL, "invariant");
  assert(k->is_instance_klass(), "invariant");
  assert(is_anonymous_klass(k), "invariant");

  uintptr_t anonymous_symbol_hash_code = 0;
  const char* const anonymous_symbol =
    create_anonymous_klass_symbol((const InstanceKlass*)k, anonymous_symbol_hash_code);

  if (anonymous_symbol == NULL) {
    return 0;
  }

  assert(anonymous_symbol_hash_code != 0, "invariant");
  traceid symbol_id = mark(anonymous_symbol, anonymous_symbol_hash_code);
  assert(mark(anonymous_symbol, anonymous_symbol_hash_code) == symbol_id, "invariant");
  return symbol_id;
}

const JfrSymbolId::SymbolEntry* JfrSymbolId::map_symbol(const Symbol* symbol) const {
  return _sym_table->lookup_only(symbol, (uintptr_t)const_cast<Symbol*>(symbol)->identity_hash());
}

const JfrSymbolId::SymbolEntry* JfrSymbolId::map_symbol(uintptr_t hash) const {
  return _sym_table->lookup_only(NULL, hash);
}

const JfrSymbolId::CStringEntry* JfrSymbolId::map_cstring(uintptr_t hash) const {
  return _cstring_table->lookup_only(NULL, hash);
}

void JfrSymbolId::assign_id(SymbolEntry* entry) {
  assert(entry != NULL, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(++_symbol_id_counter);
}

bool JfrSymbolId::equals(const Symbol* query, uintptr_t hash, const SymbolEntry* entry) {
  // query might be NULL
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  return true;
}

void JfrSymbolId::assign_id(CStringEntry* entry) {
  assert(entry != NULL, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(++_symbol_id_counter);
}

bool JfrSymbolId::equals(const char* query, uintptr_t hash, const CStringEntry* entry) {
  // query might be NULL
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  return true;
}

traceid JfrSymbolId::mark(const Klass* k) {
  assert(k != NULL, "invariant");
  traceid symbol_id = 0;
  if (is_anonymous_klass(k)) {
    symbol_id = mark_anonymous_klass_name(k);
  }
  if (0 == symbol_id) {
    const Symbol* const sym = k->name();
    if (sym != NULL) {
      symbol_id = mark(sym);
    }
  }
  assert(symbol_id > 0, "a symbol handler must mark the symbol for writing");
  return symbol_id;
}

traceid JfrSymbolId::mark(const Symbol* symbol) {
  assert(symbol != NULL, "invariant");
  return mark(symbol, (uintptr_t)const_cast<Symbol*>(symbol)->identity_hash());
}

traceid JfrSymbolId::mark(const Symbol* data, uintptr_t hash) {
  assert(data != NULL, "invariant");
  assert(_sym_table != NULL, "invariant");
  return _sym_table->id(data, hash);
}

traceid JfrSymbolId::mark(const char* str, uintptr_t hash) {
  assert(str != NULL, "invariant");
  return _cstring_table->id(str, hash);
}

bool JfrSymbolId::is_anonymous_klass(const Klass* k) {
  assert(k != NULL, "invariant");
  return k->is_instance_klass() && ((const InstanceKlass*)k)->is_anonymous();
}

/*
* jsr292 anonymous classes symbol is the external name +
* the identity_hashcode slash appended:
*   java.lang.invoke.LambdaForm$BMH/22626602
*
* caller needs ResourceMark
*/

uintptr_t JfrSymbolId::anonymous_klass_name_hash_code(const InstanceKlass* ik) {
  assert(ik != NULL, "invariant");
  assert(ik->is_anonymous(), "invariant");
  const oop mirror = ik->java_mirror();
  assert(mirror != NULL, "invariant");
  return (uintptr_t)mirror->identity_hash();
}

const char* JfrSymbolId::create_anonymous_klass_symbol(const InstanceKlass* ik, uintptr_t& hashcode) {
  assert(ik != NULL, "invariant");
  assert(ik->is_anonymous(), "invariant");
  assert(0 == hashcode, "invariant");
  char* anonymous_symbol = NULL;
  const oop mirror = ik->java_mirror();
  assert(mirror != NULL, "invariant");
  char hash_buf[40];
  hashcode = anonymous_klass_name_hash_code(ik);
  sprintf(hash_buf, "/" UINTX_FORMAT, hashcode);
  const size_t hash_len = strlen(hash_buf);
  const size_t result_len = ik->name()->utf8_length();
  anonymous_symbol = NEW_RESOURCE_ARRAY(char, result_len + hash_len + 1);
  ik->name()->as_klass_external_name(anonymous_symbol, (int)result_len + 1);
  assert(strlen(anonymous_symbol) == result_len, "invariant");
  strcpy(anonymous_symbol + result_len, hash_buf);
  assert(strlen(anonymous_symbol) == result_len + hash_len, "invariant");
  return anonymous_symbol;
}

uintptr_t JfrSymbolId::regular_klass_name_hash_code(const Klass* k) {
  assert(k != NULL, "invariant");
  const Symbol* const sym = k->name();
  assert(sym != NULL, "invariant");
  return (uintptr_t)const_cast<Symbol*>(sym)->identity_hash();
}

JfrArtifactSet::JfrArtifactSet(bool class_unload) : _symbol_id(new JfrSymbolId()),
                                                    _klass_list(NULL),
                                                    _class_unload(class_unload) {
  initialize(class_unload);
  assert(_klass_list != NULL, "invariant");
}

static const size_t initial_class_list_size = 200;
void JfrArtifactSet::initialize(bool class_unload) {
  assert(_symbol_id != NULL, "invariant");
  _symbol_id->initialize();
  assert(!_symbol_id->has_entries(), "invariant");
  _symbol_id->mark(BOOTSTRAP_LOADER_NAME, 0); // pre-load "bootstrap"
  _class_unload = class_unload;
  // resource allocation
  _klass_list = new GrowableArray<const Klass*>(initial_class_list_size, false, mtTracing);
}

JfrArtifactSet::~JfrArtifactSet() {
  clear();
}

void JfrArtifactSet::clear() {
  _symbol_id->clear();
  // _klass_list will be cleared by a ResourceMark
}

traceid JfrArtifactSet::mark_anonymous_klass_name(const Klass* klass) {
  return _symbol_id->mark_anonymous_klass_name(klass);
}

traceid JfrArtifactSet::mark(const Symbol* sym, uintptr_t hash) {
  return _symbol_id->mark(sym, hash);
}

traceid JfrArtifactSet::mark(const Klass* klass) {
  return _symbol_id->mark(klass);
}

traceid JfrArtifactSet::mark(const Symbol* symbol) {
  return _symbol_id->mark(symbol);
}

traceid JfrArtifactSet::mark(const char* const str, uintptr_t hash) {
  return _symbol_id->mark(str, hash);
}

const JfrSymbolId::SymbolEntry* JfrArtifactSet::map_symbol(const Symbol* symbol) const {
  return _symbol_id->map_symbol(symbol);
}

const JfrSymbolId::SymbolEntry* JfrArtifactSet::map_symbol(uintptr_t hash) const {
  return _symbol_id->map_symbol(hash);
}

const JfrSymbolId::CStringEntry* JfrArtifactSet::map_cstring(uintptr_t hash) const {
  return _symbol_id->map_cstring(hash);
}

bool JfrArtifactSet::has_klass_entries() const {
  return _klass_list->is_nonempty();
}

int JfrArtifactSet::entries() const {
  return _klass_list->length();
}

void JfrArtifactSet::register_klass(const Klass* k) {
  assert(k != NULL, "invariant");
  assert(_klass_list != NULL, "invariant");
  assert(_klass_list->find(k) == -1, "invariant");
  _klass_list->append(k);
}
