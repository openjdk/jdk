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

#ifndef SHARE_GC_PARALLEL_PSHEAPVIRTUALSPACE_HPP
#define SHARE_GC_PARALLEL_PSHEAPVIRTUALSPACE_HPP

#include "memory/allocation.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/virtualspace.hpp"

class PSHeapVirtualSpace : public CHeapObj<mtGC> {
  friend class VMStructs;

  // The space is committed/uncommitted in chunks of size _alignment.  The
  // ReservedSpace passed to initialize() must be aligned to this value.
  const size_t _alignment;

  // OS page size used. If using Transparent Huge Pages, it's the desired large page-size.
  const size_t _page_size;

  // Reserved area
  char* _reserved_low_addr;
  char* _reserved_high_addr;

  // Committed area for Old Gen
  char* _old_gen_committed_high_addr;

  // Generation boundary
  char* _gen_boundary;

  // Committed area for Young Gen
  char* _young_gen_committed_high_addr;

  // The entire space has been committed and pinned in memory, no
  // os::commit_memory() or os::uncommit_memory().
  bool _special;

  size_t old_gen_committed_size() const {
    return pointer_delta(_old_gen_committed_high_addr, _reserved_low_addr, sizeof(char));
  }

  size_t young_gen_committed_size() const {
    return pointer_delta(_young_gen_committed_high_addr, _gen_boundary, sizeof(char));
  }

public:
  PSHeapVirtualSpace(ReservedSpace rs, size_t alignment, char* gen_boundary);
  ~PSHeapVirtualSpace();

  // Accessors
  size_t alignment()          const { return _alignment; }
  size_t page_size()          const { return _page_size; }

  size_t reserved_bytes() const {
    return pointer_delta(_reserved_high_addr, _reserved_low_addr, sizeof(char));
  }

  char* old_gen_low_addr() const { return _reserved_low_addr; }
  char* old_gen_high_addr() const { return _gen_boundary; }

  char* young_gen_low_addr() const { return _gen_boundary; }
  char* young_gen_high_addr() const { return _reserved_high_addr; }

  char* old_gen_committed_high_addr() const { return _old_gen_committed_high_addr; }
  char* young_gen_committed_high_addr() const { return _young_gen_committed_high_addr; }

  // Generation boundary management
  char* gen_boundary() const { return _gen_boundary; }
  void right_shift_gen_boundary(char* new_boundary);
  void left_shift_gen_boundary(char* new_boundary);
  void commit_old_gen_to_boundary();

  bool expand_old_gen(size_t bytes);

  void shrink_old_gen(size_t bytes);

  bool expand_young_gen(size_t bytes);
  // Usually we expand at higher address (right).
  // This API performs expansion at lower address (left).
  bool left_expand_young_gen(size_t bytes);

  void shrink_young_gen(size_t bytes);
#ifndef PRODUCT
  void verify() const;
#endif
};

#endif // SHARE_GC_PARALLEL_PSHEAPVIRTUALSPACE_HPP
