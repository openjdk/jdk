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

#ifndef SHARE_GC_G1_G1ALLOCATIONREQUEST_HPP
#define SHARE_GC_G1_G1ALLOCATIONREQUEST_HPP

#include "utilities/globalDefinitions.hpp"

class G1AllocationRequest {
  const size_t _min_word_size;
  const size_t _desired_word_size;
  const uint _node_index;

public:
  G1AllocationRequest(size_t min_word_size, size_t desired_word_size, uint node_index)
    : _min_word_size(min_word_size),
      _desired_word_size(desired_word_size),
      _node_index(node_index) {
    assert(desired_word_size != 0, "An allocation should always be requested with this operation.");
  }

  G1AllocationRequest(size_t word_size, uint node_index)
    : G1AllocationRequest(word_size, word_size, node_index) {}

  size_t min_word_size() const { return _min_word_size; }
  size_t desired_word_size() const { return _desired_word_size; }
  uint node_index() const { return _node_index; }
};

#endif // SHARE_GC_G1_G1ALLOCATIONREQUEST_HPP
