/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CardSetGroup.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HumongousCardSetGroups.inline.hpp"

G1HumongousCardSetGroups::~G1HumongousCardSetGroups() {
  clear();
}

void G1HumongousCardSetGroups::clear() {
  _groups.clear(true /* clear_backlinks */);
}

G1CardSetGroup* G1HumongousCardSetGroups::new_group(G1HeapRegion* starts_humongous, G1CardSetGroup::State state) {
  precond(starts_humongous->is_starts_humongous());

  G1CardSetGroup* gr = new G1CardSetGroup(state);
  G1CollectedHeap::heap()->humongous_obj_regions_iterate(starts_humongous, [&] (G1HeapRegion* r) { gr->add(r); });
  return gr;
}

void G1HumongousCardSetGroups::add_newly_allocated(G1HeapRegion* starts_humongous) {
  G1CardSetGroup* gr = new_group(starts_humongous, G1CardSetGroup::State::Complete);
  _groups.append(gr);
}

void G1HumongousCardSetGroups::add_for_rebuild(GrowableArrayCHeap<G1HeapRegion*, mtGC>* regions) {
  assert_at_safepoint_on_vm_thread();

  for (G1HeapRegion* starts_humongous : *regions) {
    G1CardSetGroup* gr = new_group(starts_humongous, G1CardSetGroup::State::Updating);
    _groups.append(gr);
  }
}

void G1HumongousCardSetGroups::prepare_for_scan() {
  assert_at_safepoint_on_vm_thread();
  iterate([&] (G1CardSetGroup* gr) {
    if (gr->is_complete()) {
      gr->card_set()->reset_table_scanner();
    }
  });
}

void G1HumongousCardSetGroups::after_rebuild() {
  assert_at_safepoint_on_vm_thread();

  clean([&] (G1CardSetGroup* gr) {
    return !G1CollectedHeap::heap()->policy()->remset_tracker()->update_humongous_after_rebuild(gr);
  });
}

void G1HumongousCardSetGroups::verify() {
  iterate([&] (G1CardSetGroup* gr) {
    gr->verify();
  });
}
