/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_BINARYTREEDICTIONARY_HPP
#define SHARE_MEMORY_BINARYTREEDICTIONARY_HPP

#include "memory/freeList.hpp"
#include "memory/memRegion.hpp"

class Mutex;

/*
 * A binary tree based search structure for free blocks.
 * This is currently used in the Concurrent Mark&Sweep implementation, but
 * will be used for free block management for metadata.
 */

// A TreeList is a FreeList which can be used to maintain a
// binary tree of free lists.

template <class Chunk_t, class FreeList_t> class TreeChunk;
template <class Chunk_t, class FreeList_t> class BinaryTreeDictionary;
template <class Chunk_t, class FreeList_t> class AscendTreeCensusClosure;
template <class Chunk_t, class FreeList_t> class DescendTreeCensusClosure;
template <class Chunk_t, class FreeList_t> class DescendTreeSearchClosure;

template <class Chunk_t, class FreeList_t>
class TreeList : public FreeList_t {
  friend class TreeChunk<Chunk_t, FreeList_t>;
  friend class BinaryTreeDictionary<Chunk_t, FreeList_t>;
  friend class AscendTreeCensusClosure<Chunk_t, FreeList_t>;
  friend class DescendTreeCensusClosure<Chunk_t, FreeList_t>;
  friend class DescendTreeSearchClosure<Chunk_t, FreeList_t>;

  TreeList<Chunk_t, FreeList_t>* _parent;
  TreeList<Chunk_t, FreeList_t>* _left;
  TreeList<Chunk_t, FreeList_t>* _right;

 protected:

  TreeList<Chunk_t, FreeList_t>* parent() const { return _parent; }
  TreeList<Chunk_t, FreeList_t>* left()   const { return _left;   }
  TreeList<Chunk_t, FreeList_t>* right()  const { return _right;  }

  // Wrapper on call to base class, to get the template to compile.
  Chunk_t* head() const { return FreeList_t::head(); }
  Chunk_t* tail() const { return FreeList_t::tail(); }
  void set_head(Chunk_t* head) { FreeList_t::set_head(head); }
  void set_tail(Chunk_t* tail) { FreeList_t::set_tail(tail); }

  size_t size() const { return FreeList_t::size(); }

  // Accessors for links in tree.

  void set_left(TreeList<Chunk_t, FreeList_t>* tl) {
    _left   = tl;
    if (tl != NULL)
      tl->set_parent(this);
  }
  void set_right(TreeList<Chunk_t, FreeList_t>* tl) {
    _right  = tl;
    if (tl != NULL)
      tl->set_parent(this);
  }
  void set_parent(TreeList<Chunk_t, FreeList_t>* tl)  { _parent = tl;   }

  void clear_left()               { _left = NULL;   }
  void clear_right()              { _right = NULL;  }
  void clear_parent()             { _parent = NULL; }
  void initialize()               { clear_left(); clear_right(), clear_parent(); FreeList_t::initialize(); }

  // For constructing a TreeList from a Tree chunk or
  // address and size.
  TreeList();
  static TreeList<Chunk_t, FreeList_t>*
          as_TreeList(TreeChunk<Chunk_t, FreeList_t>* tc);
  static TreeList<Chunk_t, FreeList_t>* as_TreeList(HeapWord* addr, size_t size);

  // Returns the head of the free list as a pointer to a TreeChunk.
  TreeChunk<Chunk_t, FreeList_t>* head_as_TreeChunk();

  // Returns the first available chunk in the free list as a pointer
  // to a TreeChunk.
  TreeChunk<Chunk_t, FreeList_t>* first_available();

  // Returns the block with the largest heap address amongst
  // those in the list for this size; potentially slow and expensive,
  // use with caution!
  TreeChunk<Chunk_t, FreeList_t>* largest_address();

  TreeList<Chunk_t, FreeList_t>* get_better_list(
    BinaryTreeDictionary<Chunk_t, FreeList_t>* dictionary);

  // remove_chunk_replace_if_needed() removes the given "tc" from the TreeList.
  // If "tc" is the first chunk in the list, it is also the
  // TreeList that is the node in the tree.  remove_chunk_replace_if_needed()
  // returns the possibly replaced TreeList* for the node in
  // the tree.  It also updates the parent of the original
  // node to point to the new node.
  TreeList<Chunk_t, FreeList_t>* remove_chunk_replace_if_needed(TreeChunk<Chunk_t, FreeList_t>* tc);
  // See FreeList.
  void return_chunk_at_tail(TreeChunk<Chunk_t, FreeList_t>* tc);
};

// A TreeChunk is a subclass of a Chunk that additionally
// maintains a pointer to the free list on which it is currently
// linked.
// A TreeChunk is also used as a node in the binary tree.  This
// allows the binary tree to be maintained without any additional
// storage (the free chunks are used).  In a binary tree the first
// chunk in the free list is also the tree node.  Note that the
// TreeChunk has an embedded TreeList for this purpose.  Because
// the first chunk in the list is distinguished in this fashion
// (also is the node in the tree), it is the last chunk to be found
// on the free list for a node in the tree and is only removed if
// it is the last chunk on the free list.

template <class Chunk_t, class FreeList_t>
class TreeChunk : public Chunk_t {
  friend class TreeList<Chunk_t, FreeList_t>;
  TreeList<Chunk_t, FreeList_t>* _list;
  TreeList<Chunk_t, FreeList_t> _embedded_list;  // if non-null, this chunk is on _list

  static size_t _min_tree_chunk_size;

 protected:
  TreeList<Chunk_t, FreeList_t>* embedded_list() const { return (TreeList<Chunk_t, FreeList_t>*) &_embedded_list; }
  void set_embedded_list(TreeList<Chunk_t, FreeList_t>* v) { _embedded_list = *v; }
 public:
  TreeList<Chunk_t, FreeList_t>* list() { return _list; }
  void set_list(TreeList<Chunk_t, FreeList_t>* v) { _list = v; }
  static TreeChunk<Chunk_t, FreeList_t>* as_TreeChunk(Chunk_t* fc);
  // Initialize fields in a TreeChunk that should be
  // initialized when the TreeChunk is being added to
  // a free list in the tree.
  void initialize() { embedded_list()->initialize(); }

  Chunk_t* next() const { return Chunk_t::next(); }
  Chunk_t* prev() const { return Chunk_t::prev(); }
  size_t size() const volatile { return Chunk_t::size(); }

  static size_t min_size();

  // debugging
  void verify_tree_chunk_list() const;
  void assert_is_mangled() const;
};

template <class Chunk_t, class FreeList_t>
size_t TreeChunk<Chunk_t, FreeList_t>::_min_tree_chunk_size = sizeof(TreeChunk<Chunk_t, FreeList_t>)/HeapWordSize;
template <class Chunk_t, class FreeList_t>
size_t TreeChunk<Chunk_t, FreeList_t>::min_size() { return _min_tree_chunk_size; }

template <class Chunk_t, class FreeList_t>
class BinaryTreeDictionary: public CHeapObj<mtGC> {
  friend class VMStructs;

 protected:
  size_t     _total_size;
  size_t     _total_free_blocks;
  TreeList<Chunk_t, FreeList_t>* _root;

  // private accessors
  void set_total_size(size_t v) { _total_size = v; }
  void inc_total_size(size_t v);
  void dec_total_size(size_t v);
  void set_total_free_blocks(size_t v) { _total_free_blocks = v; }
  TreeList<Chunk_t, FreeList_t>* root() const { return _root; }
  void set_root(TreeList<Chunk_t, FreeList_t>* v) { _root = v; }

  // This field is added and can be set to point to the
  // the Mutex used to synchronize access to the
  // dictionary so that assertion checking can be done.
  // For example it is set to point to _parDictionaryAllocLock.
  NOT_PRODUCT(Mutex* _lock;)

  // Remove a chunk of size "size" or larger from the tree and
  // return it.  If the chunk
  // is the last chunk of that size, remove the node for that size
  // from the tree.
  TreeChunk<Chunk_t, FreeList_t>* get_chunk_from_tree(size_t size);
  // Remove this chunk from the tree.  If the removal results
  // in an empty list in the tree, remove the empty list.
  TreeChunk<Chunk_t, FreeList_t>* remove_chunk_from_tree(TreeChunk<Chunk_t, FreeList_t>* tc);
  // Remove the node in the trees starting at tl that has the
  // minimum value and return it.  Repair the tree as needed.
  TreeList<Chunk_t, FreeList_t>* remove_tree_minimum(TreeList<Chunk_t, FreeList_t>* tl);
  // Add this free chunk to the tree.
  void       insert_chunk_in_tree(Chunk_t* freeChunk);
 public:

  // Return a list of the specified size or NULL from the tree.
  // The list is not removed from the tree.
  TreeList<Chunk_t, FreeList_t>* find_list (size_t size) const;

  void       verify_tree() const;
  // verify that the given chunk is in the tree.
  bool       verify_chunk_in_free_list(Chunk_t* tc) const;
 private:
  void          verify_tree_helper(TreeList<Chunk_t, FreeList_t>* tl) const;
  static size_t verify_prev_free_ptrs(TreeList<Chunk_t, FreeList_t>* tl);

  // Returns the total number of chunks in the list.
  size_t     total_list_length(TreeList<Chunk_t, FreeList_t>* tl) const;
  // Returns the total number of words in the chunks in the tree
  // starting at "tl".
  size_t     total_size_in_tree(TreeList<Chunk_t, FreeList_t>* tl) const;
  // Returns the sum of the square of the size of each block
  // in the tree starting at "tl".
  double     sum_of_squared_block_sizes(TreeList<Chunk_t, FreeList_t>* const tl) const;
  // Returns the total number of free blocks in the tree starting
  // at "tl".
  size_t     total_free_blocks_in_tree(TreeList<Chunk_t, FreeList_t>* tl) const;
  size_t     num_free_blocks()  const;
  size_t     tree_height() const;
  size_t     tree_height_helper(TreeList<Chunk_t, FreeList_t>* tl) const;
  size_t     total_nodes_helper(TreeList<Chunk_t, FreeList_t>* tl) const;

 public:
  // Constructor
  BinaryTreeDictionary() :
    _total_size(0), _total_free_blocks(0), _root(0) {}

  BinaryTreeDictionary(MemRegion mr);

  // Public accessors
  size_t total_size() const { return _total_size; }
  size_t total_free_blocks() const { return _total_free_blocks; }

  // Reset the dictionary to the initial conditions with
  // a single free chunk.
  void       reset(MemRegion mr);
  void       reset(HeapWord* addr, size_t size);
  // Reset the dictionary to be empty.
  void       reset();

  // Return a chunk of size "size" or greater from
  // the tree.
  Chunk_t* get_chunk(size_t size) {
    verify_par_locked();
    Chunk_t* res = get_chunk_from_tree(size);
    assert(res == NULL || res->is_free(),
           "Should be returning a free chunk");
    return res;
  }

  void return_chunk(Chunk_t* chunk) {
    verify_par_locked();
    insert_chunk_in_tree(chunk);
  }

  void remove_chunk(Chunk_t* chunk) {
    verify_par_locked();
    remove_chunk_from_tree((TreeChunk<Chunk_t, FreeList_t>*)chunk);
    assert(chunk->is_free(), "Should still be a free chunk");
  }

  size_t     max_chunk_size() const;
  inline size_t total_chunk_size(debug_only(const Mutex* lock)) const;

  size_t     min_size() const {
    return TreeChunk<Chunk_t, FreeList_t>::min_size();
  }

  double     sum_of_squared_block_sizes() const {
    return sum_of_squared_block_sizes(root());
  }

  Chunk_t* find_chunk_ends_at(HeapWord* target) const;

  // Return the largest free chunk in the tree.
  Chunk_t* find_largest_dict() const;

  void       print_free_lists(outputStream* st) const;

  // For debugging.  Returns the sum of the _returned_bytes for
  // all lists in the tree.
  size_t     sum_dict_returned_bytes()     PRODUCT_RETURN0;
  // Sets the _returned_bytes for all the lists in the tree to zero.
  void       initialize_dict_returned_bytes()      PRODUCT_RETURN;
  // For debugging.  Return the total number of chunks in the dictionary.
  size_t     total_count()       PRODUCT_RETURN0;

  void       report_statistics(outputStream* st) const;

  void       verify() const;

  Mutex*     par_lock()                const PRODUCT_RETURN0;
  void       set_par_lock(Mutex* lock)       PRODUCT_RETURN;
  void       verify_par_locked()       const PRODUCT_RETURN;
};


// Closures for walking the binary tree.
//   do_list() walks the free list in a node applying the closure
//     to each free chunk in the list
//   do_tree() walks the nodes in the binary tree applying do_list()
//     to each list at each node.

template <class Chunk_t, class FreeList_t>
class TreeCensusClosure : public StackObj {
 protected:
  virtual void do_list(FreeList_t* fl) = 0;
 public:
  virtual void do_tree(TreeList<Chunk_t, FreeList_t>* tl) = 0;
};

template <class Chunk_t, class FreeList_t>
class AscendTreeCensusClosure : public TreeCensusClosure<Chunk_t, FreeList_t> {
 public:
  void do_tree(TreeList<Chunk_t, FreeList_t>* tl) {
    if (tl != NULL) {
      do_tree(tl->left());
      this->do_list(tl);
      do_tree(tl->right());
    }
  }
};

template <class Chunk_t, class FreeList_t>
class DescendTreeCensusClosure : public TreeCensusClosure<Chunk_t, FreeList_t> {
 public:
  void do_tree(TreeList<Chunk_t, FreeList_t>* tl) {
    if (tl != NULL) {
      do_tree(tl->right());
      this->do_list(tl);
      do_tree(tl->left());
    }
  }
};

// Used to search the tree until a condition is met.
// Similar to TreeCensusClosure but searches the
// tree and returns promptly when found.

template <class Chunk_t, class FreeList_t>
class TreeSearchClosure : public StackObj {
 protected:
  virtual bool do_list(FreeList_t* fl) = 0;
 public:
  virtual bool do_tree(TreeList<Chunk_t, FreeList_t>* tl) = 0;
};

#if 0 //  Don't need this yet but here for symmetry.
template <class Chunk_t, class FreeList_t>
class AscendTreeSearchClosure : public TreeSearchClosure<Chunk_t> {
 public:
  bool do_tree(TreeList<Chunk_t, FreeList_t>* tl) {
    if (tl != NULL) {
      if (do_tree(tl->left())) return true;
      if (do_list(tl)) return true;
      if (do_tree(tl->right())) return true;
    }
    return false;
  }
};
#endif

template <class Chunk_t, class FreeList_t>
class DescendTreeSearchClosure : public TreeSearchClosure<Chunk_t, FreeList_t> {
 public:
  bool do_tree(TreeList<Chunk_t, FreeList_t>* tl) {
    if (tl != NULL) {
      if (do_tree(tl->right())) return true;
      if (this->do_list(tl)) return true;
      if (do_tree(tl->left())) return true;
    }
    return false;
  }
};

#endif // SHARE_MEMORY_BINARYTREEDICTIONARY_HPP
