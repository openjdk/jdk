/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/mallocTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtUsage.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/threadCritical.hpp"

// Enabled all options for snapshot.
const NMTUsageOptions NMTUsage::OptionsAll = { true, true, true };
// Skip expensive thread stacks when refreshing usage.
const NMTUsageOptions NMTUsage::OptionsNoTS = { false, true, true };

NMTUsage::NMTUsage(NMTUsageOptions options) :
    _malloc_by_type(),
    _malloc_total(),
    _vm_by_type(),
    _vm_total(),
    _usage_options(options) { }

void NMTUsage::walk_thread_stacks() {
  // Snapping the thread stacks involves walking the areas to figure out how
  // much memory had been committed if they are backed by virtual memory. This
  // needs to happen before we take the snapshot of the virtual memory since it
  // will update this information.
  VirtualMemoryTracker::snapshot_thread_stacks();
}

void NMTUsage::update_malloc_usage() {
  // Thread critical needed keep values in sync, total area size
  // is deducted from mtChunk in the end to give correct values.
  ThreadCritical tc;
  const MallocMemorySnapshot* ms = MallocMemorySummary::as_snapshot();

  size_t total_arena_size = 0;
  for (int i = 0; i < mt_number_of_types; i++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(i);
    const MallocMemory* mm = ms->by_type(flag);
    _malloc_by_type[i] = mm->malloc_size() + mm->arena_size();
    total_arena_size +=  mm->arena_size();
  }

  // Total malloc size.
  _malloc_total = ms->total();

  // Adjustment due to mtChunk double counting.
  _malloc_by_type[NMTUtil::flag_to_index(mtChunk)] -= total_arena_size;
  _malloc_total -= total_arena_size;

  // Adjust mtNMT to include malloc overhead.
  _malloc_by_type[NMTUtil::flag_to_index(mtNMT)] += ms->malloc_overhead();
}

void NMTUsage::update_vm_usage() {
  const VirtualMemorySnapshot* vms = VirtualMemorySummary::as_snapshot();

  // Reset total to allow recalculation.
  _vm_total.committed = 0;
  _vm_total.reserved = 0;
  for (int i = 0; i < mt_number_of_types; i++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(i);
    const VirtualMemory* vm = vms->by_type(flag);

    _vm_by_type[i].reserved = vm->reserved();
    _vm_by_type[i].committed = vm->committed();
    _vm_total.reserved += vm->reserved();
    _vm_total.committed += vm->committed();
  }
}

void NMTUsage::refresh() {
  if (_usage_options.include_malloc) {
    update_malloc_usage();
  }

  if (_usage_options.include_vm) {
    // Thread stacks only makes sense if virtual memory
    // is also included. It must be executed before the
    // over all usage is calculated.
    if (_usage_options.update_thread_stacks) {
      walk_thread_stacks();
    }
    update_vm_usage();
  }
}

size_t NMTUsage::total_reserved() const {
  return _malloc_total + _vm_total.reserved;
}

size_t NMTUsage::total_committed() const {
  return _malloc_total + _vm_total.committed;
}

size_t NMTUsage::reserved(MEMFLAGS flag) const {
  int index = NMTUtil::flag_to_index(flag);
  return _malloc_by_type[index] + _vm_by_type[index].reserved;
}

size_t NMTUsage::committed(MEMFLAGS flag) const {
  int index = NMTUtil::flag_to_index(flag);
  return _malloc_by_type[index] + _vm_by_type[index].committed;
}
