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
#include "memory/metaspace/chunklevel.hpp"
#include "memory/metaspace/blockTree.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

// Needed to prevent linker errors on MacOS and AIX
const size_t BlockTree::MinWordSize;

#define NODE_FORMAT \
  "@" PTR_FORMAT \
  ": canary " INTPTR_FORMAT \
  ", parent " PTR_FORMAT \
  ", left " PTR_FORMAT \
  ", right " PTR_FORMAT \
  ", next " PTR_FORMAT \
  ", size " SIZE_FORMAT

#define NODE_FORMAT_ARGS(n) \
  p2i(n), \
  ((n) ? (n)->_canary : 0), \
  p2i((n) ? (n)->_parent : NULL), \
  p2i((n) ? (n)->_left : NULL), \
  p2i((n) ? (n)->_right : NULL), \
  p2i((n) ? (n)->_next : NULL), \
  ((n) ? (n)->_word_size : 0)

#ifdef ASSERT

// Tree verification

// This assert prints the tree too
#define tree_assert(cond, format, ...) \
  do { \
    if (!(cond)) { \
      tty->print("Error in tree @" PTR_FORMAT ": ", p2i(this)); \
      tty->print_cr(format, __VA_ARGS__); \
      tty->print_cr("Tree:"); \
      print_tree(tty); \
      assert(cond, format, __VA_ARGS__); \
    } \
  } while (0)

// Assert, prints tree and specific given node
#define tree_assert_invalid_node(cond, failure_node) \
  tree_assert(cond, "Invalid node: " NODE_FORMAT, NODE_FORMAT_ARGS(failure_node))


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
      tree_assert(info.depth < 10000, "too deep (%u)", info.depth);
      counter.add(n->_word_size);

      // Verify node.
      tree_assert_invalid_node(n->_canary == Node::_canary_value, n);

      if (n == _root) {
        tree_assert_invalid_node(n->_parent == NULL, n);
      } else {
        tree_assert_invalid_node(n->_parent != NULL, n);
      }

      // check size and ordering
      tree_assert_invalid_node(n->_word_size >= MinWordSize &&
                               n->_word_size <= chunklevel::MAX_CHUNK_WORD_SIZE, n);
      tree_assert_invalid_node(n->_word_size > info.lim1, n);
      tree_assert_invalid_node(n->_word_size < info.lim2, n);

      // Check children
      if (n->_left != NULL) {
        tree_assert_invalid_node(n->_left != n, n);
        tree_assert_invalid_node(n->_left->_parent == n, n);

        walkinfo info2;
        info2.n = n->_left;
        info2.lim1 = info.lim1;
        info2.lim2 = n->_word_size;
        info2.depth = info.depth + 1;
        stack.push(info2);
      }

      if (n->_right != NULL) {
        tree_assert_invalid_node(n->_right != n, n);
        tree_assert_invalid_node(n->_right->_parent == n, n);

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
        tree_assert_invalid_node(n2->_canary == Node::_canary_value, n2);
        tree_assert_invalid_node(n2 != n, n2);
        tree_assert_invalid_node(n2->_word_size == n->_word_size, n2);
        counter.add(n2->_word_size);
        n2 = n2->_next;
      }
    }
  }

  // At the end, check that counters match
  // (which also verifies that we visited every node, or at least
  //  as many nodes as are in this tree)
  _counter.check(counter);

  #undef assrt0n
}

void BlockTree::zap_range(MetaWord* p, size_t word_size) {
  memset(p, 0xF3, word_size * sizeof(MetaWord));
}

void BlockTree::print_tree(outputStream* st) const {

  // Note: we do not print the tree indented, since I found that printing it
  //  as a quasi list is much clearer to the eye.
  // We print the tree depth-first, with stacked nodes below normal ones
  //  (normal "real" nodes are marked with a leading '+')

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
      st->print("%4d + ", info.depth);
      st->print_cr(NODE_FORMAT, NODE_FORMAT_ARGS(n));

      // Print same-sized-nodes stacked under this node
      for (Node* n2 = n->_next; n2 != NULL; n2 = n2->_next) {
        st->print_raw("       ");
        st->print_cr(NODE_FORMAT, NODE_FORMAT_ARGS(n2));
      }

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
