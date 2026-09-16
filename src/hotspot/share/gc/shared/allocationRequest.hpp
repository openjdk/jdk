/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Xiang Gao. All rights reserved.
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

#ifndef SHARE_GC_SHARED_ALLOCATIONREQUEST_HPP
#define SHARE_GC_SHARED_ALLOCATIONREQUEST_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class AllocationRequest {
  const size_t _word_size;
  const int _numa_id;

  AllocationRequest(size_t word_size, int numa_id)
  :_word_size(word_size), _numa_id(numa_id) {}

public:
  static constexpr int AnyNumaId = -1;

  static AllocationRequest no_allocation() {
    return AllocationRequest(0, AnyNumaId);
  }

  static AllocationRequest for_allocation(size_t word_size) {
    assert(word_size != 0, "An allocation should always be requested with this operation.");
    return AllocationRequest(word_size, AnyNumaId);
  }

  static AllocationRequest for_numa_allocation(size_t word_size, int numa_id) {
    assert(word_size != 0, "An allocation should always be requested with this operation.");
    assert(numa_id >= 0, "invalid node id %d", numa_id);
    return AllocationRequest(word_size, numa_id);
  }

 size_t word_size() const {return _word_size;}
 int numa_id() const {return _numa_id;}
 bool is_empty() const {return _word_size == 0;}
 bool has_numa_id() const {return _numa_id != AnyNumaId;}
};

#endif //SHARE_GC_SHARED_ALLOCATIONREQUEST_HPP
