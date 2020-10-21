/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "memory/metaspace/blockTree.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

// Needed to prevent linker errors on MacOS and AIX
const size_t BlockTree::MinWordSize;

#ifdef ASSERT

// Tree verification

// These asserts prints the tree, then asserts
#define assrt(cond, format, ...) \
  do { \
    if (!(cond)) { \
      print_tree(tty); \
      assert(cond, format, __VA_ARGS__); \
    } \
  } while (0)

  // This assert prints the tree, then stops (generic message)
#define assrt0(cond) \
  do { \
    if (!(cond)) { \
      print_tree(tty); \
      assert(cond, "sanity"); \
    } \
  } while (0)

// walkinfo keeps a node plus the size corridor it and its children
//  are supposed to be in.
struct BlockTree::walkinfo {
  BlockTree::Node* n;
  int depth;
  size_t lim1; // (
  size_t lim2; // )
};

void BlockTree::verify() const {
  // Traverse the tree and test that all nodes are in the correct order.

  MemRangeCounter counter;
  int longest_edge = 0;

  if (_root != NULL) {

    ResourceMark rm;
    GrowableArray<walkinfo> stack;

    walkinfo info;
    info.n = _root;
    info.lim1 = 0;
    info.lim2 = SIZE_MAX;
    info.depth = 0;

    stack.push(info);

    while (stack.length() > 0) {
      info = stack.pop();
      const Node* n = info.n;

      // Assume a (ridiculously large) edge limit to catch cases
      //  of badly degenerated or circular trees.
      assrt0(info.depth < 10000);
      counter.add(n->_word_size);

      // Verify node.
      if (n == _root) {
        assrt0(n->_parent == NULL);
      } else {
        assrt0(n->_parent != NULL);
      }

      // check size and ordering
      assrt(n->_word_size >= MinWordSize, "bad node size " SIZE_FORMAT, n->_word_size);
      assrt0(n->_word_size > info.lim1);
      assrt0(n->_word_size < info.lim2);

      // Check children
      if (n->_left != NULL) {
        assrt0(n->_left != n);
        assrt0(n->_left->_parent == n);

        walkinfo info2;
        info2.n = n->_left;
        info2.lim1 = info.lim1;
        info2.lim2 = n->_word_size;
        info2.depth = info.depth + 1;
        stack.push(info2);
      }

      if (n->_right != NULL) {
        assrt0(n->_right != n);
        assrt0(n->_right->_parent == n);

        walkinfo info2;
        info2.n = n->_right;
        info2.lim1 = n->_word_size;
        info2.lim2 = info.lim2;
        info2.depth = info.depth + 1;
        stack.push(info2);
      }

      // If node has same-sized siblings check those too.
      const Node* n2 = n->_next;
      while (n2 != NULL) {
        assrt0(n2 != n);
        assrt0(n2->_word_size == n->_word_size);
        counter.add(n2->_word_size);
        n2 = n2->_next;
      }
    }
  }

  // At the end, check that counters match
  _counter.check(counter);
}

void BlockTree::zap_range(MetaWord* p, size_t word_size) {
  memset(p, 0xF3, word_size * sizeof(MetaWord));
}

#undef assrt
#undef assrt0

void BlockTree::print_tree(outputStream* st) const {
  if (_root != NULL) {

    ResourceMark rm;
    GrowableArray<walkinfo> stack;

    walkinfo info;
    info.n = _root;
    info.depth = 0;

    stack.push(info);
    while (stack.length() > 0) {
      info = stack.pop();
      const Node* n = info.n;
      // Print node.
      for (int i = 0; i < info.depth; i++) {
         st->print("---");
      }
      st->print_cr("<" PTR_FORMAT " (size " SIZE_FORMAT ")", p2i(n), n->_word_size);
      // Handle children.
      if (n->_right != NULL) {
        walkinfo info2;
        info2.n = n->_right;
        info2.depth = info.depth + 1;
        stack.push(info2);
      }
      if (n->_left != NULL) {
        walkinfo info2;
        info2.n = n->_left;
        info2.depth = info.depth + 1;
        stack.push(info2);
      }
    }

  } else {
    st->print_cr("<no nodes>");
  }
}

#endif // ASSERT

} // namespace metaspace
