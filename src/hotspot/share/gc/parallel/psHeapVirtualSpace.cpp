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

#include "gc/parallel/psHeapVirtualSpace.hpp"
#include "logging/log.hpp"
#include "memory/reservedSpace.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"

PSHeapVirtualSpace::PSHeapVirtualSpace(ReservedSpace rs, size_t alignment, char* gen_boundary) :
  _alignment(alignment),
  _page_size(rs.page_size()),
  _reserved_low_addr(rs.base()),
  _reserved_high_addr(rs.base() + rs.size()),
  _old_gen_committed_high_addr(rs.base()),
  _gen_boundary(gen_boundary),
  _young_gen_committed_high_addr(gen_boundary),
  _special(rs.special()) {}

PSHeapVirtualSpace::~PSHeapVirtualSpace() {
  _reserved_low_addr = _reserved_high_addr = nullptr;
  _gen_boundary = nullptr;
  _old_gen_committed_high_addr = nullptr;
  _young_gen_committed_high_addr = nullptr;
  _special = false;
}

void PSHeapVirtualSpace::right_shift_gen_boundary(char* new_boundary) {
  assert(is_aligned(new_boundary, _alignment), "boundary alignment");
  assert(new_boundary > _reserved_low_addr && new_boundary <= _reserved_high_addr, "boundary out of range");
  assert(_old_gen_committed_high_addr <= _gen_boundary, "precondition");
  assert(_gen_boundary < new_boundary, "precondition");

  char* original_gen_boundary = _gen_boundary;
  char* original_young_gen_committed_high_addr = _young_gen_committed_high_addr;
  const size_t original_young_gen_committed_size = young_gen_committed_size();

  // Ensure old-gen is committed up to the current generation boundary.
  if (_old_gen_committed_high_addr < original_gen_boundary) {
    size_t diff = pointer_delta(original_gen_boundary, _old_gen_committed_high_addr, sizeof(char));
    if (!expand_old_gen(diff)) {
      vm_exit_out_of_memory(diff, OOM_MMAP_ERROR, "right_shift_gen_boundary");
    }
  }
  assert(_old_gen_committed_high_addr == original_gen_boundary, "inv");

  _gen_boundary = new_boundary;

  if (original_young_gen_committed_high_addr < new_boundary) {
    _old_gen_committed_high_addr = original_young_gen_committed_high_addr;
    size_t diff = pointer_delta(new_boundary, original_young_gen_committed_high_addr, sizeof(char));
    if (!expand_old_gen(diff)) {
      vm_exit_out_of_memory(diff, OOM_MMAP_ERROR, "right_shift_gen_boundary");
    }
    _young_gen_committed_high_addr = new_boundary;
  } else {
    _old_gen_committed_high_addr = new_boundary;
  }
  // maintain old-gen fully committed
  assert(_old_gen_committed_high_addr == _gen_boundary, "inv");

  size_t young_gen_remaining_size = pointer_delta(_reserved_high_addr, _young_gen_committed_high_addr, sizeof(char));
  // Try to maintain the same committed size in young-gen
  if (young_gen_remaining_size > 0) {
    size_t current_committed = young_gen_committed_size();
    if (original_young_gen_committed_size > current_committed) {
      size_t diff = original_young_gen_committed_size - current_committed;
      diff = MIN2(diff, young_gen_remaining_size);
      if (!expand_young_gen(diff)) {
        vm_exit_out_of_memory(diff, OOM_MMAP_ERROR, "right_shift_gen_boundary");
      }
    }
  }
}

void PSHeapVirtualSpace::left_shift_gen_boundary(char* new_boundary) {
  assert(is_aligned(new_boundary, _alignment), "boundary alignment");
  assert(new_boundary > _reserved_low_addr && new_boundary < _reserved_high_addr, "boundary out of range");
  assert(new_boundary < _gen_boundary, "precondition");

  if (new_boundary <= _old_gen_committed_high_addr) {
    // [_old_gen_committed_high_addr, _gen_boundary) needs to become committed
    // and re-assigned to young-gen.
    size_t to_commit_size = pointer_delta(_gen_boundary, _old_gen_committed_high_addr, sizeof(char));
    if (to_commit_size > 0) {
      if (!expand_old_gen(to_commit_size)) {
        vm_exit_out_of_memory(to_commit_size, OOM_MMAP_ERROR, "left_shift_gen_boundary: expand_old_gen");
      }
    }
    _old_gen_committed_high_addr = new_boundary;
  } else {
    size_t to_commit_bytes = pointer_delta(_gen_boundary, new_boundary, sizeof(char));
    if (!left_expand_young_gen(to_commit_bytes)) {
      vm_exit_out_of_memory(to_commit_bytes, OOM_MMAP_ERROR, "left_expand_young_gen");
    }
  }

  _gen_boundary = new_boundary;
}

void PSHeapVirtualSpace::commit_old_gen_to_boundary() {
  assert(_old_gen_committed_high_addr <= _gen_boundary, "precondition");
  if (_old_gen_committed_high_addr < _gen_boundary) {
    size_t diff = pointer_delta(_gen_boundary, _old_gen_committed_high_addr, sizeof(char));
    if (!expand_old_gen(diff)) {
      vm_exit_out_of_memory(diff, OOM_MMAP_ERROR, "commit_old_gen_to_boundary");
    }
  }
}

bool PSHeapVirtualSpace::expand_old_gen(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(is_aligned(bytes, _alignment), "arg not aligned");

  char* old_high = _old_gen_committed_high_addr;
  char* new_high = old_high + bytes;

  assert(new_high <= _gen_boundary, "cannot expand beyond boundary");

  if (_special || os::commit_memory(old_high, bytes, _alignment, !ExecMem)) {
    _old_gen_committed_high_addr = new_high;
    return true;
  }

  log_warning(gc)("PSHeapVirtualSpace::expand_old_gen failed: "
                  "os::commit_memory(" PTR_FORMAT ", %zu, %zu, %d) failed.",
                  p2i(old_high), bytes, _alignment, !ExecMem);

  return false;
}

void PSHeapVirtualSpace::shrink_old_gen(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(bytes < old_gen_committed_size(), "cannot shrink more than committed");
  assert(is_aligned(bytes, _alignment), "arg not aligned");

  char* old_high = _old_gen_committed_high_addr;
  char* new_high = old_high - bytes;

  if (!_special) {
    os::uncommit_memory(new_high, bytes);
  }

  _old_gen_committed_high_addr = new_high;
}

bool PSHeapVirtualSpace::expand_young_gen(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(is_aligned(bytes, _alignment), "arg not aligned");

  char* old_high = _young_gen_committed_high_addr;
  char* new_high = old_high + bytes;

  assert(new_high <= _reserved_high_addr, "cannot expand beyond reserved");

  if (_special || os::commit_memory(old_high, bytes, _alignment, !ExecMem)) {
    _young_gen_committed_high_addr = new_high;
    return true;
  }

  log_warning(gc)("PSHeapVirtualSpace::expand_young_gen failed: "
                  "os::commit_memory(" PTR_FORMAT ", %zu, %zu, %d) failed.",
                  p2i(old_high), bytes, _alignment, !ExecMem);

  return false;
}

bool PSHeapVirtualSpace::left_expand_young_gen(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(is_aligned(bytes, _alignment), "precondition");

  size_t old_gen_uncommitted_size = pointer_delta(_gen_boundary, _old_gen_committed_high_addr, sizeof(char));
  assert(old_gen_uncommitted_size >= bytes, "precondition");

  char* new_gen_boundary = _gen_boundary - bytes;
  assert(new_gen_boundary >= _old_gen_committed_high_addr, "inv");

  if (_special || os::commit_memory(new_gen_boundary, bytes, _alignment, !ExecMem)) {
    return true;
  }

  log_warning(gc)("PSHeapVirtualSpace::left_expand_young_gen failed: "
                  "os::commit_memory(" PTR_FORMAT ", %zu, %zu, %d) failed.",
                  p2i(new_gen_boundary), bytes, _alignment, !ExecMem);

  return false;
}

void PSHeapVirtualSpace::shrink_young_gen(size_t bytes) {
  assert(bytes != 0, "precondition");
  assert(bytes < young_gen_committed_size(), "cannot shrink more than committed");
  assert(is_aligned(bytes, _alignment), "arg not aligned");

  char* old_high = _young_gen_committed_high_addr;
  char* new_high = old_high - bytes;

  if (!_special) {
    os::uncommit_memory(new_high, bytes);
  }

  _young_gen_committed_high_addr = new_high;
}

#ifndef PRODUCT
void PSHeapVirtualSpace::verify() const {
  assert(is_aligned(_alignment, _page_size), "inv");
  assert(is_aligned(_reserved_low_addr, _alignment), "bad reserved_low_addr");
  assert(is_aligned(_reserved_high_addr, _alignment), "bad reserved_high_addr");
  assert(is_aligned(_gen_boundary, _alignment), "bad gen_boundary");
  assert(is_aligned(_old_gen_committed_high_addr, _alignment), "bad old_gen_committed_high_addr");
  assert(is_aligned(_young_gen_committed_high_addr, _alignment), "bad young_gen_committed_high_addr");

  assert(_reserved_low_addr <= _old_gen_committed_high_addr, "inv");
  assert(_old_gen_committed_high_addr <= _gen_boundary, "inv");
  assert(_gen_boundary <= _young_gen_committed_high_addr, "inv");
  assert(_young_gen_committed_high_addr <= _reserved_high_addr, "inv");
}
#endif
