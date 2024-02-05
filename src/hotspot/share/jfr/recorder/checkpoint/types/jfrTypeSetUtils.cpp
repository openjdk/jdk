/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

JfrArtifactSet::JfrArtifactSet(bool class_unload) : _symbol_table(nullptr),
                                                    _klass_list(nullptr),
                                                    _total_count(0),
                                                    _class_unload(class_unload) {
  initialize(class_unload);
  assert(_klass_list != nullptr, "invariant");
}

static const size_t initial_klass_list_size = 256;
const int initial_klass_loader_set_size = 64;

void JfrArtifactSet::initialize(bool class_unload) {
  _class_unload = class_unload;
  if (_symbol_table == nullptr) {
    _symbol_table = JfrSymbolTable::create();
    assert(_symbol_table != nullptr, "invariant");
  }
  assert(_symbol_table != nullptr, "invariant");
  _symbol_table->set_class_unload(class_unload);
  _total_count = 0;
  // resource allocation
  _klass_list = new GrowableArray<const Klass*>(initial_klass_list_size);
  _klass_loader_set = new GrowableArray<const Klass*>(initial_klass_loader_set_size);
  _klass_loader_leakp_set = new GrowableArray<const Klass*>(initial_klass_loader_set_size);

  if (class_unload) {
    _unloading_set = new GrowableArray<const Klass*>(initial_klass_list_size);
  }
}

void JfrArtifactSet::clear() {
  if (_symbol_table != nullptr) {
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

static inline bool not_in_set(GrowableArray<const Klass*>* set, const Klass* k) {
  assert(set != nullptr, "invariant");
  assert(k != nullptr, "invariant");
  return !JfrMutablePredicate<const Klass*, compare_klasses>::test(set, k);
}

bool JfrArtifactSet::should_do_cld_klass(const Klass* k, bool leakp) {
  assert(k != nullptr, "invariant");
  assert(_klass_loader_set != nullptr, "invariant");
  assert(_klass_loader_leakp_set != nullptr, "invariant");
  return not_in_set(leakp ? _klass_loader_leakp_set : _klass_loader_set, k);
}

bool JfrArtifactSet::should_do_unloading_artifact(const void* ptr) {
  assert(ptr != nullptr, "invariant");
  assert(_class_unload, "invariant");
  assert(_unloading_set != nullptr, "invariant");
  // The incoming pointers are of all kinds of different types.
  // However, we are only interested in set membership.
  // Treat them uniformly as const Klass* for simplicity and code reuse.
  return not_in_set(_unloading_set, static_cast<const Klass*>(ptr));
}

void JfrArtifactSet::register_klass(const Klass* k) {
  assert(k != nullptr, "invariant");
  assert(_klass_list != nullptr, "invariant");
  _klass_list->append(k);
}

size_t JfrArtifactSet::total_count() const {
  return _total_count;
}

void JfrArtifactSet::increment_checkpoint_id() {
  assert(_symbol_table != nullptr, "invariant");
  _symbol_table->increment_checkpoint_id();
}

