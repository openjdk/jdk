/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "utilities/ostream.hpp"

HeapWord* G1HeapRegionRemSet::_heap_base_address = nullptr;

const char* G1HeapRegionRemSet::_state_strings[] =  {"Untracked", "Updating", "Complete"};
const char* G1HeapRegionRemSet::_short_state_strings[] =  {"UNTRA", "UPDAT", "CMPLT"};

void G1HeapRegionRemSet::initialize(MemRegion reserved) {
  G1CardSet::initialize(reserved);
  _heap_base_address = reserved.start();
}

void G1HeapRegionRemSet::uninstall_card_set_group() {
  _card_set_group = nullptr;
}

G1HeapRegionRemSet::G1HeapRegionRemSet() :
  _code_roots(),
  _card_set_group(nullptr),
  _state(Untracked) { }

G1HeapRegionRemSet::~G1HeapRegionRemSet() {
  assert(!has_card_set_group(), "Still assigned to a card set group");
}

void G1HeapRegionRemSet::clear() {
  assert(card_set_is_empty(), "Card set must be empty");
  _code_roots.clear();
  set_state_untracked();
}

void G1HeapRegionRemSet::reset_code_root_table_scanner() {
  _code_roots.reset_table_scanner();
}

void G1HeapRegionRemSet::reset_table_scanner() {
  reset_code_root_table_scanner();
  if (has_card_set_group()) {
    card_set()->reset_table_scanner();
  }
}

G1MonotonicArenaMemoryStats G1HeapRegionRemSet::card_set_memory_stats() const {
  assert(has_card_set_group(), "pre-condition");
  return card_set_group()->card_set_memory_stats();
}

void G1HeapRegionRemSet::print_static_mem_size(outputStream* out) {
  out->print_cr("  Static structures = %zu", G1HeapRegionRemSet::static_mem_size());
}

// Code roots support

void G1HeapRegionRemSet::add_code_root(nmethod* nm) {
  assert(nm != nullptr, "sanity");
  _code_roots.add(nm);
}

void G1HeapRegionRemSet::bulk_remove_code_roots() {
  _code_roots.bulk_remove();
}

void G1HeapRegionRemSet::prepare_for_adding_code_roots(size_t num_code_roots) {
  _code_roots.prepare_for_adding_code_roots(num_code_roots);
}

void G1HeapRegionRemSet::code_roots_do(NMethodClosure* blk) const {
  _code_roots.nmethods_do(blk);
}

void G1HeapRegionRemSet::clean_code_roots(G1HeapRegion* hr) {
  _code_roots.clean(hr);
}

size_t G1HeapRegionRemSet::code_roots_mem_size() {
  return _code_roots.mem_size();
}
