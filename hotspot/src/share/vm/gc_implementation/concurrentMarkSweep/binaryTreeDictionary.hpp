/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 * A binary tree based search structure for free blocks.
 * This is currently used in the Concurrent Mark&Sweep implementation.
 */

// A TreeList is a FreeList which can be used to maintain a
// binary tree of free lists.

class TreeChunk;
class BinaryTreeDictionary;
class AscendTreeCensusClosure;
class DescendTreeCensusClosure;
class DescendTreeSearchClosure;

class TreeList: public FreeList {
  friend class TreeChunk;
  friend class BinaryTreeDictionary;
  friend class AscendTreeCensusClosure;
  friend class DescendTreeCensusClosure;
  friend class DescendTreeSearchClosure;

 protected:
  TreeList* parent() const { return _parent; }
  TreeList* left()   const { return _left;   }
  TreeList* right()  const { return _right;  }

  // Accessors for links in tree.

  void setLeft(TreeList* tl) {
    _left   = tl;
    if (tl != NULL)
      tl->setParent(this);
  }
  void setRight(TreeList* tl) {
    _right  = tl;
    if (tl != NULL)
      tl->setParent(this);
  }
  void setParent(TreeList* tl)  { _parent = tl;   }

  void clearLeft()               { _left = NULL;   }
  void clearRight()              { _right = NULL;  }
  void clearParent()             { _parent = NULL; }
  void initialize()              { clearLeft(); clearRight(), clearParent(); }

  // For constructing a TreeList from a Tree chunk or
  // address and size.
  static TreeList* as_TreeList(TreeChunk* tc);
  static TreeList* as_TreeList(HeapWord* addr, size_t size);

  // Returns the head of the free list as a pointer to a TreeChunk.
  TreeChunk* head_as_TreeChunk();

  // Returns the first available chunk in the free list as a pointer
  // to a TreeChunk.
  TreeChunk* first_available();

  // Returns the block with the largest heap address amongst
  // those in the list for this size; potentially slow and expensive,
  // use with caution!
  TreeChunk* largest_address();

  // removeChunkReplaceIfNeeded() removes the given "tc" from the TreeList.
  // If "tc" is the first chunk in the list, it is also the
  // TreeList that is the node in the tree.  removeChunkReplaceIfNeeded()
  // returns the possibly replaced TreeList* for the node in
  // the tree.  It also updates the parent of the original
  // node to point to the new node.
  TreeList* removeChunkReplaceIfNeeded(TreeChunk* tc);
  // See FreeList.
  void returnChunkAtHead(TreeChunk* tc);
  void returnChunkAtTail(TreeChunk* tc);
};

// A TreeChunk is a subclass of a FreeChunk that additionally
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

class TreeChunk : public FreeChunk {
  friend class TreeList;
  TreeList* _list;
  TreeList _embedded_list;  // if non-null, this chunk is on _list
 protected:
  TreeList* embedded_list() const { return (TreeList*) &_embedded_list; }
  void set_embedded_list(TreeList* v) { _embedded_list = *v; }
 public:
  TreeList* list() { return _list; }
  void set_list(TreeList* v) { _list = v; }
  static TreeChunk* as_TreeChunk(FreeChunk* fc);
  // Initialize fields in a TreeChunk that should be
  // initialized when the TreeChunk is being added to
  // a free list in the tree.
  void initialize() { embedded_list()->initialize(); }

  // debugging
  void verifyTreeChunkList() const;
};

const size_t MIN_TREE_CHUNK_SIZE  = sizeof(TreeChunk)/HeapWordSize;

class BinaryTreeDictionary: public FreeBlockDictionary {
  friend class VMStructs;
  bool       _splay;
  size_t     _totalSize;
  size_t     _totalFreeBlocks;
  TreeList* _root;

  // private accessors
  bool splay() const { return _splay; }
  void set_splay(bool v) { _splay = v; }
  size_t totalSize() const { return _totalSize; }
  void set_totalSize(size_t v) { _totalSize = v; }
  virtual void inc_totalSize(size_t v);
  virtual void dec_totalSize(size_t v);
  size_t totalFreeBlocks() const { return _totalFreeBlocks; }
  void set_totalFreeBlocks(size_t v) { _totalFreeBlocks = v; }
  TreeList* root() const { return _root; }
  void set_root(TreeList* v) { _root = v; }

  // Remove a chunk of size "size" or larger from the tree and
  // return it.  If the chunk
  // is the last chunk of that size, remove the node for that size
  // from the tree.
  TreeChunk* getChunkFromTree(size_t size, Dither dither, bool splay);
  // Return a list of the specified size or NULL from the tree.
  // The list is not removed from the tree.
  TreeList* findList (size_t size) const;
  // Remove this chunk from the tree.  If the removal results
  // in an empty list in the tree, remove the empty list.
  TreeChunk* removeChunkFromTree(TreeChunk* tc);
  // Remove the node in the trees starting at tl that has the
  // minimum value and return it.  Repair the tree as needed.
  TreeList* removeTreeMinimum(TreeList* tl);
  void       semiSplayStep(TreeList* tl);
  // Add this free chunk to the tree.
  void       insertChunkInTree(FreeChunk* freeChunk);
 public:
  void       verifyTree() const;
  // verify that the given chunk is in the tree.
  bool       verifyChunkInFreeLists(FreeChunk* tc) const;
 private:
  void          verifyTreeHelper(TreeList* tl) const;
  static size_t verifyPrevFreePtrs(TreeList* tl);

  // Returns the total number of chunks in the list.
  size_t     totalListLength(TreeList* tl) const;
  // Returns the total number of words in the chunks in the tree
  // starting at "tl".
  size_t     totalSizeInTree(TreeList* tl) const;
  // Returns the sum of the square of the size of each block
  // in the tree starting at "tl".
  double     sum_of_squared_block_sizes(TreeList* const tl) const;
  // Returns the total number of free blocks in the tree starting
  // at "tl".
  size_t     totalFreeBlocksInTree(TreeList* tl) const;
  size_t     numFreeBlocks() const;
  size_t     treeHeight() const;
  size_t     treeHeightHelper(TreeList* tl) const;
  size_t     totalNodesInTree(TreeList* tl) const;
  size_t     totalNodesHelper(TreeList* tl) const;

 public:
  // Constructor
  BinaryTreeDictionary(MemRegion mr, bool splay = false);

  // Reset the dictionary to the initial conditions with
  // a single free chunk.
  void       reset(MemRegion mr);
  void       reset(HeapWord* addr, size_t size);
  // Reset the dictionary to be empty.
  void       reset();

  // Return a chunk of size "size" or greater from
  // the tree.
  // want a better dynamic splay strategy for the future.
  FreeChunk* getChunk(size_t size, Dither dither) {
    verify_par_locked();
    FreeChunk* res = getChunkFromTree(size, dither, splay());
    assert(res == NULL || res->isFree(),
           "Should be returning a free chunk");
    return res;
  }

  void returnChunk(FreeChunk* chunk) {
    verify_par_locked();
    insertChunkInTree(chunk);
  }

  void removeChunk(FreeChunk* chunk) {
    verify_par_locked();
    removeChunkFromTree((TreeChunk*)chunk);
    assert(chunk->isFree(), "Should still be a free chunk");
  }

  size_t     maxChunkSize() const;
  size_t     totalChunkSize(debug_only(const Mutex* lock)) const {
    debug_only(
      if (lock != NULL && lock->owned_by_self()) {
        assert(totalSizeInTree(root()) == totalSize(),
               "_totalSize inconsistency");
      }
    )
    return totalSize();
  }

  size_t     minSize() const {
    return MIN_TREE_CHUNK_SIZE;
  }

  double     sum_of_squared_block_sizes() const {
    return sum_of_squared_block_sizes(root());
  }

  FreeChunk* find_chunk_ends_at(HeapWord* target) const;

  // Find the list with size "size" in the binary tree and update
  // the statistics in the list according to "split" (chunk was
  // split or coalesce) and "birth" (chunk was added or removed).
  void       dictCensusUpdate(size_t size, bool split, bool birth);
  // Return true if the dictionary is overpopulated (more chunks of
  // this size than desired) for size "size".
  bool       coalDictOverPopulated(size_t size);
  // Methods called at the beginning of a sweep to prepare the
  // statistics for the sweep.
  void       beginSweepDictCensus(double coalSurplusPercent,
                                  float inter_sweep_current,
                                  float inter_sweep_estimate,
                                  float intra_sweep_estimate);
  // Methods called after the end of a sweep to modify the
  // statistics for the sweep.
  void       endSweepDictCensus(double splitSurplusPercent);
  // Return the largest free chunk in the tree.
  FreeChunk* findLargestDict() const;
  // Accessors for statistics
  void       setTreeSurplus(double splitSurplusPercent);
  void       setTreeHints(void);
  // Reset statistics for all the lists in the tree.
  void       clearTreeCensus(void);
  // Print the statistcis for all the lists in the tree.  Also may
  // print out summaries.
  void       printDictCensus(void) const;
  void       print_free_lists(outputStream* st) const;

  // For debugging.  Returns the sum of the _returnedBytes for
  // all lists in the tree.
  size_t     sumDictReturnedBytes()     PRODUCT_RETURN0;
  // Sets the _returnedBytes for all the lists in the tree to zero.
  void       initializeDictReturnedBytes()      PRODUCT_RETURN;
  // For debugging.  Return the total number of chunks in the dictionary.
  size_t     totalCount()       PRODUCT_RETURN0;

  void       reportStatistics() const;

  void       verify() const;
};
