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

#include "nmt/vmatree.hpp"

int addr_cmp(size_t a, size_t b) {
  if (a < b) return -1;
  if (a == b) return 0;
  if (a > b) return 1;
  else {
    // Can't happen
    ShouldNotReachHere();
  }
}
VMATree::VTreap* VMATree::closest_geq(size_t B) {
  // Need to go "left-ward" for EQ node, so do a leq search first.
  VTreap* leqB = closest_leq(B);
  if (leqB != nullptr && leqB->key() == B) {
    return leqB;
  }
  VTreap* gtB = nullptr;
  VTreap* head = tree.tree;
  while (head != nullptr) {
    int cmp_r = addr_cmp(head->key(), B);
    if (cmp_r == 0) { // Exact match
      gtB = head;
      break; // Can't become better than that.
    }
    if (cmp_r > 0) {
      // Found a match, try to find a better one.
      gtB = head;
      head = head->left;
    } else if (cmp_r < 0) {
      head = head->right;
    }
  }
  return gtB;
}
VMATree::VTreap* VMATree::closest_leq(size_t A) { // LEQ search
  VTreap* leqA_n = nullptr;
  VTreap* head = tree.tree;
  while (head != nullptr) {
    int cmp_r = addr_cmp(head->key(), A);
    if (cmp_r == 0) { // Exact match
      leqA_n = head;
      break; // Can't become better than that.
    }
    if (cmp_r < 0) {
      // Found a match, try to find a better one.
      leqA_n = head;
      head = head->right;
    } else if (cmp_r > 0) {
      head = head->left;
    }
  }
  return leqA_n;
}
