/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/checkpoint/types/jfrTypeSetUtils.hpp"
#include "jfr/support/jfrSymbolTable.inline.hpp"
#include "jfr/utilities/jfrPredicate.hpp"
#include "jfr/utilities/jfrRelation.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"

JfrArtifactSet::JfrArtifactSet(bool class_unload, bool previous_epoch) : _klass_set(nullptr),
                                                                         _klass_loader_set(nullptr),
                                                                         _klass_loader_leakp_set(nullptr),
                                                                         _total_count(0),
                                                                         _class_unload(class_unload),
                                                                         _previous_epoch(previous_epoch) {
  initialize(class_unload, previous_epoch);
  assert(!previous_epoch || _klass_loader_leakp_set != nullptr, "invariant");
  assert(_klass_loader_set != nullptr, "invariant");
  assert(_klass_set != nullptr, "invariant");
}

static unsigned initial_klass_set_size = 4096;
static unsigned initial_klass_loader_set_size = 64;
static unsigned initial_klass_loader_leakp_set_size = 64;

void JfrArtifactSet::initialize(bool class_unload, bool previous_epoch) {
  _class_unload = class_unload;
  _previous_epoch = previous_epoch;
  _total_count = 0;
  // Resource allocations. Keep in this allocation order.
  if (previous_epoch) {
    _klass_loader_leakp_set = new JfrKlassSet(initial_klass_loader_leakp_set_size);
  }
  _klass_loader_set = new JfrKlassSet(initial_klass_loader_set_size);
  _klass_set = new JfrKlassSet(initial_klass_set_size);
}

void JfrArtifactSet::clear() {
  assert(_previous_epoch, "invariant");
  JfrSymbolTable::clear_previous_epoch();
  assert(_klass_loader_leakp_set != nullptr, "invariant");
  initial_klass_loader_leakp_set_size = MAX2(initial_klass_loader_leakp_set_size, _klass_loader_leakp_set->table_size());
}

JfrArtifactSet::~JfrArtifactSet() {
  // _klass_loader_set, _klass_loader_leakp_set and
  // _klass_list will be cleared by a ResourceMark
}

traceid JfrArtifactSet::bootstrap_name(bool leakp) {
  return JfrSymbolTable::bootstrap_name(leakp);
}

traceid JfrArtifactSet::mark(const Klass* klass, bool leakp) {
  return JfrSymbolTable::mark(klass, leakp, _class_unload, _previous_epoch);
}

traceid JfrArtifactSet::mark(const Symbol* symbol, bool leakp) {
  return JfrSymbolTable::mark(symbol, leakp, _class_unload, _previous_epoch);
}

bool JfrArtifactSet::has_klass_entries() const {
  return _klass_set->is_nonempty();
}

static inline bool not_in_set(JfrArtifactSet::JfrKlassSet* set, const Klass* k) {
  assert(set != nullptr, "invariant");
  assert(k != nullptr, "invariant");
  return set->add(k);
}

bool JfrArtifactSet::should_do_cld_klass(const Klass* k, bool leakp) {
  assert(k != nullptr, "invariant");
  assert(_klass_loader_set != nullptr, "invariant");
  assert(_klass_loader_leakp_set != nullptr, "invariant");
  return not_in_set(leakp ? _klass_loader_leakp_set : _klass_loader_set, k);
}

void JfrArtifactSet::register_klass(const Klass* k) {
  assert(k != nullptr, "invariant");
  assert(IS_SERIALIZED(k), "invariant");
  assert(_klass_set != nullptr, "invariant");
  _klass_set->add(k);
}

size_t JfrArtifactSet::total_count() const {
  assert(_klass_set != nullptr, "invariant");
  initial_klass_set_size = MAX2(initial_klass_set_size, _klass_set->table_size());
  assert(_klass_loader_set != nullptr, "invariant");
  initial_klass_loader_set_size = MAX2(initial_klass_loader_set_size, _klass_loader_set->table_size());
  return _total_count;
}
