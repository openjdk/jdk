/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1SegmentedArrayFreePool.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/ostream.hpp"

G1SegmentedArrayMemoryStats::G1SegmentedArrayMemoryStats() {
  clear();
}

void G1SegmentedArrayMemoryStats::clear() {
  for (uint i = 0; i < num_pools(); i++) {
    _num_mem_sizes[i] = 0;
    _num_segments[i] = 0;
  }
}

void G1SegmentedArrayFreePool::update_unlink_processors(G1ReturnMemoryProcessorSet* unlink_processor) {
  uint num_free_lists = _freelist_pool.num_free_lists();

  for (uint i = 0; i < num_free_lists; i++) {
    unlink_processor->at(i)->visit_free_list(_freelist_pool.free_list(i));
  }
}

void G1SegmentedArrayFreePool::G1ReturnMemoryProcessor::visit_free_list(G1SegmentedArrayFreeList* source) {
  assert(_source == nullptr, "already visited");
  if (_return_to_vm_size > 0) {
    _source = source;
  } else {
    assert(_source == nullptr, "must be");
  }
  if (source->mem_size() > _return_to_vm_size) {
    _first = source->get_all(_num_unlinked, _unlinked_bytes);
  } else {
    assert(_first == nullptr, "must be");
  }
  // Above we were racing with other threads getting the contents of the free list,
  // so while we might have been asked to return something to the OS initially,
  // the free list might be empty anyway. In this case just reset internal values
  // used for checking whether there is work available.
  if (_first == nullptr) {
    _source = nullptr;
    _return_to_vm_size = 0;
  }
}

bool G1SegmentedArrayFreePool::G1ReturnMemoryProcessor::return_to_vm(jlong deadline) {
  assert(!finished_return_to_vm(), "already returned everything to the VM");
  assert(_first != nullptr, "must have segment to return");

  size_t keep_size = 0;
  size_t keep_num = 0;

  G1SegmentedArraySegment* cur = _first;
  G1SegmentedArraySegment* last = nullptr;

  while (cur != nullptr && _return_to_vm_size > 0) {
    size_t cur_size = cur->mem_size();
    _return_to_vm_size -= MIN2(_return_to_vm_size, cur_size);

    keep_size += cur_size;
    keep_num++;

    last = cur;
    cur = cur->next();
    // To ensure progress, perform the deadline check here.
    if (os::elapsed_counter() > deadline) {
      break;
    }
  }

  assert(_first != nullptr, "must be");
  assert(last != nullptr, "must be");

  last->set_next(nullptr);

  // Wait for any in-progress pops to avoid ABA for them.
  GlobalCounter::write_synchronize();
  _source->bulk_add(*_first, *last, keep_num, keep_size);
  _first = cur;

  log_trace(gc, task)("Segmented Array Free Memory: Returned to VM %zu segments size %zu", keep_num, keep_size);

  // _return_to_vm_size may be larger than what is available in the list at the
  // time we actually get the list. I.e. the list and _return_to_vm_size may be
  // inconsistent.
  // So also check if we actually already at the end of the list for the exit
  // condition.
  if (_return_to_vm_size == 0 || _first == nullptr) {
    _source = nullptr;
    _return_to_vm_size = 0;
  }
  return _source != nullptr;
}

bool G1SegmentedArrayFreePool::G1ReturnMemoryProcessor::return_to_os(jlong deadline) {
  assert(finished_return_to_vm(), "not finished returning to VM");
  assert(!finished_return_to_os(), "already returned everything to the OS");

  // Now delete the rest.
  size_t num_delete = 0;
  size_t mem_size_deleted = 0;

  while (_first != nullptr) {
    G1SegmentedArraySegment* next = _first->next();
    num_delete++;
    mem_size_deleted += _first->mem_size();
    G1SegmentedArraySegment::delete_segment(_first);
    _first = next;

    // To ensure progress, perform the deadline check here.
    if (os::elapsed_counter() > deadline) {
      break;
    }
  }

  log_trace(gc, task)("Segmented Array Free Memory: Return to OS %zu segments size %zu", num_delete, mem_size_deleted);

  return _first != nullptr;
}

G1SegmentedArrayFreePool G1SegmentedArrayFreePool::_freelist_pool(G1CardSetConfiguration::num_mem_object_types());

G1SegmentedArrayFreePool::G1SegmentedArrayFreePool(uint num_free_lists) :
  _num_free_lists(num_free_lists) {

  _free_lists = NEW_C_HEAP_ARRAY(G1SegmentedArrayFreeList, _num_free_lists, mtGC);
  for (uint i = 0; i < _num_free_lists; i++) {
    new (&_free_lists[i]) G1SegmentedArrayFreeList();
  }
}

G1SegmentedArrayFreePool::~G1SegmentedArrayFreePool() {
  for (uint i = 0; i < _num_free_lists; i++) {
    _free_lists[i].~G1SegmentedArrayFreeList();
  }
  FREE_C_HEAP_ARRAY(mtGC, _free_lists);
}

G1SegmentedArrayMemoryStats G1SegmentedArrayFreePool::memory_sizes() const {
  G1SegmentedArrayMemoryStats free_list_stats;
  assert(free_list_stats.num_pools() == num_free_lists(), "must be");
  for (uint i = 0; i < num_free_lists(); i++) {
    free_list_stats._num_mem_sizes[i] = _free_lists[i].mem_size();
    free_list_stats._num_segments[i] = _free_lists[i].num_segments();
  }
  return free_list_stats;
}

size_t G1SegmentedArrayFreePool::mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < _num_free_lists; i++) {
    result += _free_lists[i].mem_size();
  }
  return result;
}

void G1SegmentedArrayFreePool::print_on(outputStream* out) {
  out->print_cr("  Free Pool: size %zu", free_list_pool()->mem_size());
  for (uint i = 0; i < _num_free_lists; i++) {
    FormatBuffer<> fmt("    %s", G1CardSetConfiguration::mem_object_type_name_str(i));
    _free_lists[i].print_on(out, fmt);
  }
}
