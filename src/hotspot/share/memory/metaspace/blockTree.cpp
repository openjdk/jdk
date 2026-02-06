/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022 SAP SE. All rights reserved.
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

#include "memory/metaspace/blockTree.hpp"
#include "memory/metaspace/chunklevel.hpp"
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
  ", tree " PTR_FORMAT \
  ", next " PTR_FORMAT \
  ", size %zu"

#define NODE_FORMAT_ARGS(n) \
  p2i(n), \
  (n)->_canary, \
  p2i(&(n)->_tree_node), \
  p2i((n)->_next), \
  (n)->_word_size

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

// Helper for verify()
void BlockTree::verify_node_pointer(const Node* n) const {
  tree_assert(os::is_readable_pointer(n),
              "Invalid node: @" PTR_FORMAT " is unreadable.", p2i(n));
  // If the canary is broken, this is either an invalid node pointer or
  // the node has been overwritten. Either way, print a hex dump, then
  // assert away.
  if (n->_canary != Node::_canary_value) {
    os::print_hex_dump(tty, (address)n, (address)n + sizeof(Node), 1);
    tree_assert(false, "Invalid node: @" PTR_FORMAT " canary broken or pointer invalid", p2i(n));
  }
}

void BlockTree::verify() const {
  // Traverse the tree and test that all nodes are in the correct order.
  MemRangeCounter counter;

  // Verifies node ordering (n1 < n2 => word_size1 < word_size2),
  // node validity, and that the tree is balanced and not ill-formed.
  _tree.verify_self([&](const TreeNode* tree_node) {
    const Node* n = Node::cast_to_node(tree_node);

    verify_node_pointer(n);

    counter.add(n->_word_size);

    tree_assert_invalid_node(n->_word_size >= MinWordSize, n);
    tree_assert_invalid_node(n->_word_size <= chunklevel::MAX_CHUNK_WORD_SIZE, n);

    // If node has same-sized siblings check those too.
    const Node* n2 = n->_next;
    while (n2 != nullptr) {
      verify_node_pointer(n2);
      tree_assert_invalid_node(n2 != n, n2); // catch simple circles
      tree_assert_invalid_node(n2->_word_size == n->_word_size, n2);
      counter.add(n2->_word_size);
      n2 = n2->_next;
    }

    return true;
  });

  // At the end, check that counters match
  // (which also verifies that we visited every node, or at least
  //  as many nodes as are in this tree)
  _counter.check(counter);
}

void BlockTree::zap_block(MetaBlock bl) {
  memset(bl.base(), 0xF3, bl.word_size() * sizeof(MetaWord));
}

void BlockTree::print_tree(outputStream* st) const {

  // Note: we do not print the tree indented, since I found that printing it
  //  as a quasi list is much clearer to the eye.
  // We print the tree depth-first, with stacked nodes below normal ones
  //  (normal "real" nodes are marked with a leading '+')
  if (is_empty()) {
    st->print_cr("<no nodes>");
    return;
  }

  _tree.print_on(st, [&](outputStream *st, const TreeNode *tree_node, int depth) {
    const Node* n = Node::cast_to_node(tree_node);

    // Print node.
    st->print("%4d + ", depth);
    if (os::is_readable_pointer(n)) {
      st->print_cr(NODE_FORMAT, NODE_FORMAT_ARGS(n));
    } else {
      st->print_cr("@" PTR_FORMAT ": unreadable", p2i(n));
      return;
    }

    // Print same-sized-nodes stacked under this node
    for (Node* n2 = n->_next; n2 != nullptr; n2 = n2->_next) {
      st->print_raw("       ");
      if (os::is_readable_pointer(n2)) {
        st->print_cr(NODE_FORMAT, NODE_FORMAT_ARGS(n2));
      } else {
        st->print_cr("@" PTR_FORMAT ": unreadable (skipping rest of chain).", p2i(n2));
        break; // stop printing this chain.
      }
    }
  });
}

#endif // ASSERT

} // namespace metaspace
