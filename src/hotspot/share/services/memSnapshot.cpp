/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/threadCritical.hpp"
#include "services/memSnapshot.hpp"
#include "services/nmtCommon.hpp"
#include "services/mallocTracker.hpp"
#include "services/threadStackTracker.hpp"
#include "services/virtualMemoryTracker.hpp"

// Enabled all options for snapshot.
const MemSnapshotOptions MemSnapshot::OptionsAll = { true, true, true };
// Skip expensive thread stacks for snapshot.
const MemSnapshotOptions MemSnapshot::OptionsNoTS = { false, true, true };

MemSnapshot::MemSnapshot(MemSnapshotOptions options) :
    _malloc_snapshot(),
    _malloc_total(),
    _vm_snapshot(),
    _vm_total(),
    _snapshot_options(options) { }

void MemSnapshot::walk_thread_stacks() {
  // If backed by virtual memory, snapping the thread stacks involves walking
  // them to to figure out how much memory is committed if they are backed by
  // virtual memory. This needs ot happen before we take the snapshot of the
  // virtual memory since it will update this information.
  if (ThreadStackTracker::track_as_vm()) {
    VirtualMemoryTracker::snapshot_thread_stacks();
  }
}

void MemSnapshot::update_malloc_snapshot() {
  // Thread critical needed keep values in sync, total area size
  // is deducted from mtChunk in the end to give correct values.
  ThreadCritical tc;
  const MallocMemorySnapshot* ms = MallocMemorySummary::as_snapshot();

  size_t total_arena_size = 0;
  for (int i = 0; i < mt_number_of_types; i++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(i);
    const MallocMemory* mm = ms->by_type(flag);
    _malloc_snapshot[i] = mm->malloc_size() + mm->arena_size();
    total_arena_size +=  mm->arena_size();
  }

  // Total malloc size.
  _malloc_total = ms->total();

  // Adjustment due to mtChunk double counting.
  _malloc_snapshot[NMTUtil::flag_to_index(mtChunk)] -= total_arena_size;
  _malloc_total -= total_arena_size;

  // Adjust mtNMT to include malloc overhead.
  _malloc_snapshot[NMTUtil::flag_to_index(mtNMT)] += ms->malloc_overhead();
}

void MemSnapshot::update_vm_snapshot() {
  const VirtualMemorySnapshot* vms = VirtualMemorySummary::as_snapshot();

  // Reset total to allow recalculation.
  _vm_total.committed = 0;
  _vm_total.reserved = 0;
  for (int i = 0; i < mt_number_of_types; i++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(i);
    const VirtualMemory* vm = vms->by_type(flag);

    _vm_snapshot[i].reserved = vm->reserved();
    _vm_snapshot[i].committed = vm->committed();
    _vm_total.reserved += vm->reserved();
    _vm_total.committed += vm->committed();
  }
}

void MemSnapshot::snap() {
  if (_snapshot_options.include_malloc) {
    update_malloc_snapshot();
  }

  if (_snapshot_options.include_vm) {
    // Thread stacks only makes sense if virtual memory
    // is also included. It must be executed before the
    // over all usage is calculated.
    if (_snapshot_options.update_thread_stacks) {
      walk_thread_stacks();
    }
    update_vm_snapshot();
  }
}

size_t MemSnapshot::total_reserved() const {
  return _malloc_total + _vm_total.reserved;
}

size_t MemSnapshot::total_committed() const {
  return _malloc_total + _vm_total.reserved;
}

size_t MemSnapshot::reserved(MEMFLAGS flag) const {
  int index = NMTUtil::flag_to_index(flag);
  return _malloc_snapshot[index] + _vm_snapshot[index].reserved;
}

size_t MemSnapshot::committed(MEMFLAGS flag) const {
  int index = NMTUtil::flag_to_index(flag);
  return _malloc_snapshot[index] + _vm_snapshot[index].committed;
}
