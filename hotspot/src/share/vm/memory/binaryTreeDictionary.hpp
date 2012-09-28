/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_BINARYTREEDICTIONARY_HPP
#define SHARE_VM_MEMORY_BINARYTREEDICTIONARY_HPP

#include "memory/freeBlockDictionary.hpp"
#include "memory/freeList.hpp"

/*
 * A binary tree based search structure for free blocks.
 * This is currently used in the Concurrent Mark&Sweep implementation, but
 * will be used for free block management for metadata.
 */

// A TreeList is a FreeList which can be used to maintain a
// binary tree of free lists.

template <class Chunk> class TreeChunk;
template <class Chunk> class BinaryTreeDictionary;
template <class Chunk> class AscendTreeCensusClosure;
template <class Chunk> class DescendTreeCensusClosure;
template <class Chunk> class DescendTreeSearchClosure;

template <class Chunk>
class TreeList: public FreeList<Chunk> {
  friend class TreeChunk<Chunk>;
  friend class BinaryTreeDictionary<Chunk>;
  friend class AscendTreeCensusClosure<Chunk>;
  friend class DescendTreeCensusClosure<Chunk>;
  friend class DescendTreeSearchClosure<Chunk>;

  TreeList<Chunk>* _parent;
  TreeList<Chunk>* _left;
  TreeList<Chunk>* _right;

 protected:
  TreeList<Chunk>* parent() const { return _parent; }
  TreeList<Chunk>* left()   const { return _left;   }
  TreeList<Chunk>* right()  const { return _right;  }

  // Explicitly import these names into our namespace to fix name lookup with templates
  using FreeList<Chunk>::head;
  using FreeList<Chunk>::set_head;

  using FreeList<Chunk>::tail;
  using FreeList<Chunk>::set_tail;
  using FreeList<Chunk>::link_tail;

  using FreeList<Chunk>::increment_count;
  NOT_PRODUCT(using FreeList<Chunk>::increment_returned_bytes_by;)
  using FreeList<Chunk>::verify_chunk_in_free_list;
  using FreeList<Chunk>::size;

  // Accessors for links in tree.

  void set_left(TreeList<Chunk>* tl) {
    _left   = tl;
    if (tl != NULL)
      tl->set_parent(this);
  }
  void set_right(TreeList<Chunk>* tl) {
    _right  = tl;
    if (tl != NULL)
      tl->set_parent(this);
  }
  void set_parent(TreeList<Chunk>* tl)  { _parent = tl;   }

  void clearLeft()               { _left = NULL;   }
  void clear_right()              { _right = NULL;  }
  void clear_parent()             { _parent = NULL; }
  void initialize()              { clearLeft(); clear_right(), clear_parent(); }

  // For constructing a TreeList from a Tree chunk or
  // address and size.
  static TreeList<Chunk>* as_TreeList(TreeChunk<Chunk>* tc);
  static TreeList<Chunk>* as_TreeList(HeapWord* addr, size_t size);

  // Returns the head of the free list as a pointer to a TreeChunk.
  TreeChunk<Chunk>* head_as_TreeChunk();

  // Returns the first available chunk in the free list as a pointer
  // to a TreeChunk.
  TreeChunk<Chunk>* first_available();

  // Returns the block with the largest heap address amongst
  // those in the list for this size; potentially slow and expensive,
  // use with caution!
  TreeChunk<Chunk>* largest_address();

  // remove_chunk_replace_if_needed() removes the given "tc" from the TreeList.
  // If "tc" is the first chunk in the list, it is also the
  // TreeList that is the node in the tree.  remove_chunk_replace_if_needed()
  // returns the possibly replaced TreeList* for the node in
  // the tree.  It also updates the parent of the original
  // node to point to the new node.
  TreeList<Chunk>* remove_chunk_replace_if_needed(TreeChunk<Chunk>* tc);
  // See FreeList.
  void return_chunk_at_head(TreeChunk<Chunk>* tc);
  void return_chunk_at_tail(TreeChunk<Chunk>* tc);
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

template <class Chunk>
class TreeChunk : public Chunk {
  friend class TreeList<Chunk>;
  TreeList<Chunk>* _list;
  TreeList<Chunk> _embedded_list;  // if non-null, this chunk is on _list
 protected:
  TreeList<Chunk>* embedded_list() const { return (TreeList<Chunk>*) &_embedded_list; }
  void set_embedded_list(TreeList<Chunk>* v) { _embedded_list = *v; }
 public:
  TreeList<Chunk>* list() { return _list; }
  void set_list(TreeList<Chunk>* v) { _list = v; }
  static TreeChunk<Chunk>* as_TreeChunk(Chunk* fc);
  // Initialize fields in a TreeChunk that should be
  // initialized when the TreeChunk is being added to
  // a free list in the tree.
  void initialize() { embedded_list()->initialize(); }

  Chunk* next() const { return Chunk::next(); }
  Chunk* prev() const { return Chunk::prev(); }
  size_t size() const volatile { return Chunk::size(); }

  // debugging
  void verify_tree_chunk_list() const;
};


template <class Chunk>
class BinaryTreeDictionary: public FreeBlockDictionary<Chunk> {
  friend class VMStructs;
  bool       _splay;
  bool       _adaptive_freelists;
  size_t     _total_size;
  size_t     _total_free_blocks;
  TreeList<Chunk>* _root;

  // private accessors
  bool splay() const { return _splay; }
  void set_splay(bool v) { _splay = v; }
  void set_total_size(size_t v) { _total_size = v; }
  virtual void inc_total_size(size_t v);
  virtual void dec_total_size(size_t v);
  size_t total_free_blocks() const { return _total_free_blocks; }
  void set_total_free_blocks(size_t v) { _total_free_blocks = v; }
  TreeList<Chunk>* root() const { return _root; }
  void set_root(TreeList<Chunk>* v) { _root = v; }
  bool adaptive_freelists() { return _adaptive_freelists; }

  // This field is added and can be set to point to the
  // the Mutex used to synchronize access to the
  // dictionary so that assertion checking can be done.
  // For example it is set to point to _parDictionaryAllocLock.
  NOT_PRODUCT(Mutex* _lock;)

  // Remove a chunk of size "size" or larger from the tree and
  // return it.  If the chunk
  // is the last chunk of that size, remove the node for that size
  // from the tree.
  TreeChunk<Chunk>* get_chunk_from_tree(size_t size, enum FreeBlockDictionary<Chunk>::Dither dither, bool splay);
  // Return a list of the specified size or NULL from the tree.
  // The list is not removed from the tree.
  TreeList<Chunk>* find_list (size_t size) const;
  // Remove this chunk from the tree.  If the removal results
  // in an empty list in the tree, remove the empty list.
  TreeChunk<Chunk>* remove_chunk_from_tree(TreeChunk<Chunk>* tc);
  // Remove the node in the trees starting at tl that has the
  // minimum value and return it.  Repair the tree as needed.
  TreeList<Chunk>* remove_tree_minimum(TreeList<Chunk>* tl);
  void       semi_splay_step(TreeList<Chunk>* tl);
  // Add this free chunk to the tree.
  void       insert_chunk_in_tree(Chunk* freeChunk);
 public:

  static const size_t min_tree_chunk_size  = sizeof(TreeChunk<Chunk>)/HeapWordSize;

  void       verify_tree() const;
  // verify that the given chunk is in the tree.
  bool       verify_chunk_in_free_list(Chunk* tc) const;
 private:
  void          verify_tree_helper(TreeList<Chunk>* tl) const;
  static size_t verify_prev_free_ptrs(TreeList<Chunk>* tl);

  // Returns the total number of chunks in the list.
  size_t     total_list_length(TreeList<Chunk>* tl) const;
  // Returns the total number of words in the chunks in the tree
  // starting at "tl".
  size_t     total_size_in_tree(TreeList<Chunk>* tl) const;
  // Returns the sum of the square of the size of each block
  // in the tree starting at "tl".
  double     sum_of_squared_block_sizes(TreeList<Chunk>* const tl) const;
  // Returns the total number of free blocks in the tree starting
  // at "tl".
  size_t     total_free_blocks_in_tree(TreeList<Chunk>* tl) const;
  size_t     num_free_blocks() const;
  size_t     treeHeight() const;
  size_t     tree_height_helper(TreeList<Chunk>* tl) const;
  size_t     total_nodes_in_tree(TreeList<Chunk>* tl) const;
  size_t     total_nodes_helper(TreeList<Chunk>* tl) const;

 public:
  // Constructor
  BinaryTreeDictionary(bool adaptive_freelists, bool splay = false);
  BinaryTreeDictionary(MemRegion mr, bool adaptive_freelists, bool splay = false);

  // Public accessors
  size_t total_size() const { return _total_size; }

  // Reset the dictionary to the initial conditions with
  // a single free chunk.
  void       reset(MemRegion mr);
  void       reset(HeapWord* addr, size_t size);
  // Reset the dictionary to be empty.
  void       reset();

  // Return a chunk of size "size" or greater from
  // the tree.
  // want a better dynamic splay strategy for the future.
  Chunk* get_chunk(size_t size, enum FreeBlockDictionary<Chunk>::Dither dither) {
    FreeBlockDictionary<Chunk>::verify_par_locked();
    Chunk* res = get_chunk_from_tree(size, dither, splay());
    assert(res == NULL || res->is_free(),
           "Should be returning a free chunk");
    return res;
  }

  void return_chunk(Chunk* chunk) {
    FreeBlockDictionary<Chunk>::verify_par_locked();
    insert_chunk_in_tree(chunk);
  }

  void remove_chunk(Chunk* chunk) {
    FreeBlockDictionary<Chunk>::verify_par_locked();
    remove_chunk_from_tree((TreeChunk<Chunk>*)chunk);
    assert(chunk->is_free(), "Should still be a free chunk");
  }

  size_t     max_chunk_size() const;
  size_t     total_chunk_size(debug_only(const Mutex* lock)) const {
    debug_only(
      if (lock != NULL && lock->owned_by_self()) {
        assert(total_size_in_tree(root()) == total_size(),
               "_total_size inconsistency");
      }
    )
    return total_size();
  }

  size_t     min_size() const {
    return min_tree_chunk_size;
  }

  double     sum_of_squared_block_sizes() const {
    return sum_of_squared_block_sizes(root());
  }

  Chunk* find_chunk_ends_at(HeapWord* target) const;

  // Find the list with size "size" in the binary tree and update
  // the statistics in the list according to "split" (chunk was
  // split or coalesce) and "birth" (chunk was added or removed).
  void       dict_census_udpate(size_t size, bool split, bool birth);
  // Return true if the dictionary is overpopulated (more chunks of
  // this size than desired) for size "size".
  bool       coal_dict_over_populated(size_t size);
  // Methods called at the beginning of a sweep to prepare the
  // statistics for the sweep.
  void       begin_sweep_dict_census(double coalSurplusPercent,
                                  float inter_sweep_current,
                                  float inter_sweep_estimate,
                                  float intra_sweep_estimate);
  // Methods called after the end of a sweep to modify the
  // statistics for the sweep.
  void       end_sweep_dict_census(double splitSurplusPercent);
  // Return the largest free chunk in the tree.
  Chunk* find_largest_dict() const;
  // Accessors for statistics
  void       set_tree_surplus(double splitSurplusPercent);
  void       set_tree_hints(void);
  // Reset statistics for all the lists in the tree.
  void       clear_tree_census(void);
  // Print the statistcis for all the lists in the tree.  Also may
  // print out summaries.
  void       print_dict_census(void) const;
  void       print_free_lists(outputStream* st) const;

  // For debugging.  Returns the sum of the _returned_bytes for
  // all lists in the tree.
  size_t     sum_dict_returned_bytes()     PRODUCT_RETURN0;
  // Sets the _returned_bytes for all the lists in the tree to zero.
  void       initialize_dict_returned_bytes()      PRODUCT_RETURN;
  // For debugging.  Return the total number of chunks in the dictionary.
  size_t     total_count()       PRODUCT_RETURN0;

  void       report_statistics() const;

  void       verify() const;
};

#endif // SHARE_VM_MEMORY_BINARYTREEDICTIONARY_HPP
