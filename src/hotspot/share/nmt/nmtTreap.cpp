/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifdef ASSERT

#include "nmt/nmtTreap.hpp"
#include <math.h>

template<typename K, typename V, typename COMPARATOR, typename ALLOCATOR>
bool Treap<K,V,COMPARATOR,ALLOCATOR>::verify_self() {
  double expected_maximum_depth = log(this->_node_count) * 3;
  // Find the maximum depth through DFS.
  int maximum_depth_found = 0;

  struct DFS {
    int depth;
    uint64_t parent_prio;
    TreapNode* n;
  };
  GrowableArrayCHeap<TreapNode*, mtNMT> to_visit;
  uint64_t positive_infinity = 0xFFFFFFFFFFFFFFFF;

  to_visit.push({0, positive_infinity, this->_root});
  while (!to_visit.is_empty()) {
    DFS head = to_visit.pop();
    if (head.n == nullptr) continue;
    if (maximum_depth_found < head.depth) {
      maximum_depth_found = head.depth;
    }
    if (head.parent_prio < head.n->_priority) {
      return false;
    }
    to_visit.push({head.depth+1, head.n->_priority, head.n->left()});
    to_visit.push({head.depth+1, head.n->_priority, head.n->right()});
  }
  if (maximum_depth_found > (int)expected_maximum_depth) {
    return false;
  }
  return true;
}

#endif // ASSERT
