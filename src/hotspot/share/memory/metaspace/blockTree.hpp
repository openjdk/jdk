/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_BLOCKTREE_HPP
#define SHARE_MEMORY_METASPACE_BLOCKTREE_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace/chunklevel.hpp"
#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/metablock.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/rbTree.inline.hpp"

namespace metaspace {

// BlockTree is tree built on an intrusive red-black tree.
//  It is used to manage medium to large free memory blocks.
//
// There is no separation between payload (managed blocks) and nodes: the
//  memory blocks themselves are the nodes, with the block size being the key.
//
// We store node pointer information in these blocks when storing them. That
//  imposes a minimum size to the managed memory blocks (1 MinWordSize)
//
// We want to manage many memory blocks of the same size, but we want
//  to prevent the tree from blowing up and degenerating into a list. Therefore
//  there is only one node for each unique block size; subsequent blocks of the
//  same size are stacked below that first node:
//
//                   +-----+
//                   | 100 |
//                   +-----+
//                  /       \
//           +-----+         +-----+
//           | 80  |         | 120 |
//           +-----+         +-----+
//          /   |   \
//         / +-----+ \
//  +-----+  | 80  |  +-----+
//  | 70  |  +-----+  | 85  |
//  +-----+     |     +-----+
//           +-----+
//           | 80  |
//           +-----+
//

class BlockTree: public CHeapObj<mtMetaspace> {

  using TreeNode = IntrusiveRBNode;

  struct Node {

    static const intptr_t _canary_value =
        NOT_LP64(0x4e4f4445) LP64_ONLY(0x4e4f44454e4f4445ULL); // "NODE" resp "NODENODE"

    // Note: we afford us the luxury of an always-there canary value.
    //  The space for that is there (these nodes are only used to manage larger blocks).
    //  It is initialized in debug and release, but only automatically tested
    //  in debug.
    const intptr_t _canary;

    // Tree node for linking blocks in the intrusive tree.
    TreeNode _tree_node;

    // Blocks with the same size are put in a list with this node as head.
    Node* _next;

    // Word size of node. Note that size cannot be larger than max metaspace size,
    // so this could very well be a 32bit value.
    const size_t _word_size;

    Node(size_t word_size) :
      _canary(_canary_value),
      _tree_node{},
      _next(nullptr),
      _word_size(word_size)
    {}

    static Node* cast_to_node(const TreeNode* tree_node) {
      return (Node*)((uintptr_t)tree_node - offset_of(Node, _tree_node));
    }

#ifdef ASSERT
    bool valid() const {
      return _canary == _canary_value &&
        _word_size >= sizeof(Node) &&
        _word_size < chunklevel::MAX_CHUNK_WORD_SIZE;
    }
#endif
  };

  struct TreeComparator {
    static RBTreeOrdering cmp(const size_t a, const TreeNode* b) {
      const size_t node_word_size = Node::cast_to_node(b)->_word_size;

      if (a < node_word_size) { return RBTreeOrdering::LT; }
      if (a > node_word_size) { return RBTreeOrdering::GT; }
      return RBTreeOrdering::EQ;
    }

    static bool less_than(const TreeNode* a, const TreeNode* b) {
      const size_t a_word_size = Node::cast_to_node(a)->_word_size;
      const size_t b_word_size = Node::cast_to_node(b)->_word_size;

      if (a_word_size < b_word_size) { return true; }
      return false;
    }
  };

#ifdef ASSERT
  // Run a quick check on a node; upon suspicion dive into a full tree check.
  void check_node(const Node* n) const { if (!n->valid()) verify(); }
#endif

public:

  // Minimum word size a block has to be to be added to this structure (note ceil division).
  const static size_t MinWordSize =
      (sizeof(Node) + sizeof(MetaWord) - 1) / sizeof(MetaWord);

private:

  IntrusiveRBTree<const size_t, TreeComparator> _tree;

  MemRangeCounter _counter;

  // Given a node n, add it to the list starting at head
  static void add_to_list(Node* n, Node* head) {
    assert(head->_word_size == n->_word_size, "sanity");
    n->_next = head->_next;
    head->_next = n;
    DEBUG_ONLY(n->_tree_node = TreeNode());
  }

  // Given a node list starting at head, remove one of the follow up nodes from
  //  that list and return it. The head node gets not modified and remains in the
  //  tree.
  // List must contain at least one other node.
  static Node* remove_from_list(Node* head) {
    assert(head->_next != nullptr, "sanity");
    Node* n = head->_next;
    head->_next = n->_next;
    return n;
  }

#ifdef ASSERT
  void zap_block(MetaBlock block);
  // Helper for verify()
  void verify_node_pointer(const Node* n) const;
#endif // ASSERT

public:

  BlockTree() {}

  // Add a memory block to the tree. Its content will be overwritten.
  void add_block(MetaBlock block) {
    DEBUG_ONLY(zap_block(block);)
    const size_t word_size = block.word_size();
    assert(word_size >= MinWordSize, "invalid block size %zu", word_size);
    Node* n = new(block.base()) Node(word_size);
    IntrusiveRBTree<const size_t, TreeComparator>::Cursor cursor = _tree.cursor(word_size);
    if (cursor.found()) {
      add_to_list(n, Node::cast_to_node(cursor.node()));
    }
    else {
      _tree.insert_at_cursor(&n->_tree_node, cursor);
    }
    _counter.add(word_size);
  }

  // Given a word_size, search and return the smallest block that is equal or
  //  larger than that size.
  MetaBlock remove_block(size_t word_size) {
    assert(word_size >= MinWordSize, "invalid block size %zu", word_size);

    MetaBlock result;
    TreeNode* tree_node = _tree.closest_ge(word_size);

    if (tree_node != nullptr) {
      Node* n = Node::cast_to_node(tree_node);
      DEBUG_ONLY(check_node(n);)
      assert(n->_word_size >= word_size, "sanity");

      if (n->_next != nullptr) {
        // If the node is head of a chain of same sized nodes, we leave it alone
        //  and instead remove one of the follow up nodes (which is simpler than
        //  removing the chain head node and then having to graft the follow up
        //  node into its place in the tree).
        n = remove_from_list(n);
      } else {
        _tree.remove(tree_node);
      }

      result = MetaBlock((MetaWord*)n, n->_word_size);

      _counter.sub(n->_word_size);

      DEBUG_ONLY(zap_block(result);)
    }
    return result;
  }

  // Returns number of blocks in this structure
  unsigned count() const { return _counter.count(); }

  // Returns total size, in words, of all elements.
  size_t total_size() const { return _counter.total_size(); }

  bool is_empty() const { return _tree.size() == 0; }

  DEBUG_ONLY(void print_tree(outputStream* st) const;)
  DEBUG_ONLY(void verify() const;)
};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_BLOCKTREE_HPP
