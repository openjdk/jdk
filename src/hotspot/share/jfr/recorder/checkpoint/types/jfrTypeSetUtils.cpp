/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/utilities/jfrPredicate.hpp"
#include "jfr/utilities/jfrRelation.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"

JfrArtifactSet::JfrArtifactSet(bool class_unload) : _symbol_table(NULL),
                                                    _klass_list(NULL),
                                                    _total_count(0) {
  initialize(class_unload);
  assert(_klass_list != NULL, "invariant");
}

static const size_t initial_klass_list_size = 256;
const int initial_klass_loader_set_size = 64;

void JfrArtifactSet::initialize(bool class_unload) {
  if (_symbol_table == NULL) {
    _symbol_table = JfrSymbolTable::create();
    assert(_symbol_table != NULL, "invariant");
  }
  assert(_symbol_table != NULL, "invariant");
  _symbol_table->set_class_unload(class_unload);
  _total_count = 0;
  // resource allocation
  _klass_list = new GrowableArray<const Klass*>(initial_klass_list_size);
  _klass_loader_set = new GrowableArray<const Klass*>(initial_klass_loader_set_size);
}

void JfrArtifactSet::clear() {
  if (_symbol_table != NULL) {
    _symbol_table->clear();
  }
}

JfrArtifactSet::~JfrArtifactSet() {
  delete _symbol_table;
  // _klass_list and _klass_loader_list will be cleared by a ResourceMark
}

traceid JfrArtifactSet::bootstrap_name(bool leakp) {
  return _symbol_table->bootstrap_name(leakp);
}

traceid JfrArtifactSet::mark_hidden_klass_name(const Klass* klass, bool leakp) {
  assert(klass->is_instance_klass(), "invariant");
  return _symbol_table->mark_hidden_klass_name((const InstanceKlass*)klass, leakp);
}

traceid JfrArtifactSet::mark(uintptr_t hash, const Symbol* sym, bool leakp) {
  return _symbol_table->mark(hash, sym, leakp);
}

traceid JfrArtifactSet::mark(const Klass* klass, bool leakp) {
  return _symbol_table->mark(klass, leakp);
}

traceid JfrArtifactSet::mark(const Symbol* symbol, bool leakp) {
  return _symbol_table->mark(symbol, leakp);
}

traceid JfrArtifactSet::mark(uintptr_t hash, const char* const str, bool leakp) {
  return _symbol_table->mark(hash, str, leakp);
}

bool JfrArtifactSet::has_klass_entries() const {
  return _klass_list->is_nonempty();
}

int JfrArtifactSet::entries() const {
  return _klass_list->length();
}

bool JfrArtifactSet::should_do_loader_klass(const Klass* k) {
  assert(k != NULL, "invariant");
  assert(_klass_loader_set != NULL, "invariant");
  return !JfrMutablePredicate<const Klass*, compare_klasses>::test(_klass_loader_set, k);
}

void JfrArtifactSet::register_klass(const Klass* k) {
  assert(k != NULL, "invariant");
  assert(_klass_list != NULL, "invariant");
  _klass_list->append(k);
}

size_t JfrArtifactSet::total_count() const {
  return _total_count;
}

void JfrArtifactSet::increment_checkpoint_id() {
  assert(_symbol_table != NULL, "invariant");
  _symbol_table->increment_checkpoint_id();
}

