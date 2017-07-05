/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

//
// Free block maintenance for Concurrent Mark Sweep Generation
//
// The main data structure for free blocks are
// . an indexed array of small free blocks, and
// . a dictionary of large free blocks
//

// No virtuals in FreeChunk (don't want any vtables).

// A FreeChunk is merely a chunk that can be in a doubly linked list
// and has a size field. NOTE: FreeChunks are distinguished from allocated
// objects in two ways (by the sweeper). The second word (prev) has the
// LSB set to indicate a free chunk; allocated objects' klass() pointers
// don't have their LSB set. The corresponding bit in the CMSBitMap is
// set when the chunk is allocated. There are also blocks that "look free"
// but are not part of the free list and should not be coalesced into larger
// free blocks. These free blocks have their two LSB's set.

class FreeChunk VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
  FreeChunk* _next;
  FreeChunk* _prev;
  size_t     _size;

 public:
  NOT_PRODUCT(static const size_t header_size();)
  // Returns "true" if the "wrd", which is required to be the second word
  // of a block, indicates that the block represents a free chunk.
  static bool secondWordIndicatesFreeChunk(intptr_t wrd) {
    return (wrd & 0x1) == 0x1;
  }
  bool isFree()       const {
    return secondWordIndicatesFreeChunk((intptr_t)_prev);
  }
  bool cantCoalesce() const { return (((intptr_t)_prev) & 0x3) == 0x3; }
  FreeChunk* next()   const { return _next; }
  FreeChunk* prev()   const { return (FreeChunk*)(((intptr_t)_prev) & ~(0x3)); }
  debug_only(void* prev_addr() const { return (void*)&_prev; })

  void linkAfter(FreeChunk* ptr) {
    linkNext(ptr);
    if (ptr != NULL) ptr->linkPrev(this);
  }
  void linkAfterNonNull(FreeChunk* ptr) {
    assert(ptr != NULL, "precondition violation");
    linkNext(ptr);
    ptr->linkPrev(this);
  }
  void linkNext(FreeChunk* ptr) { _next = ptr; }
  void linkPrev(FreeChunk* ptr) { _prev = (FreeChunk*)((intptr_t)ptr | 0x1); }
  void clearPrev()              { _prev = NULL; }
  void clearNext()              { _next = NULL; }
  void dontCoalesce()      {
    // the block should be free
    assert(isFree(), "Should look like a free block");
    _prev = (FreeChunk*)(((intptr_t)_prev) | 0x2);
  }
  void markFree()    { _prev = (FreeChunk*)((intptr_t)_prev | 0x1);    }
  void markNotFree() { _prev = NULL; }

  size_t size()           const { return _size; }
  void setSize(size_t size)     { _size = size; }

  // For volatile reads:
  size_t* size_addr()           { return &_size; }

  // Return the address past the end of this chunk
  HeapWord* end() const { return ((HeapWord*) this) + _size; }

  // debugging
  void verify()             const PRODUCT_RETURN;
  void verifyList()         const PRODUCT_RETURN;
  void mangleAllocated(size_t size) PRODUCT_RETURN;
  void mangleFreed(size_t size)     PRODUCT_RETURN;
};

// Alignment helpers etc.
#define numQuanta(x,y) ((x+y-1)/y)
enum AlignmentConstants {
  MinChunkSize = numQuanta(sizeof(FreeChunk), MinObjAlignmentInBytes) * MinObjAlignment
};

// A FreeBlockDictionary is an abstract superclass that will allow
// a number of alternative implementations in the future.
class FreeBlockDictionary: public CHeapObj {
 public:
  enum Dither {
    atLeast,
    exactly,
    roughly
  };
  enum DictionaryChoice {
    dictionaryBinaryTree = 0,
    dictionarySplayTree  = 1,
    dictionarySkipList   = 2
  };

 private:
  NOT_PRODUCT(Mutex* _lock;)

 public:
  virtual void       removeChunk(FreeChunk* fc) = 0;
  virtual FreeChunk* getChunk(size_t size, Dither dither = atLeast) = 0;
  virtual void       returnChunk(FreeChunk* chunk) = 0;
  virtual size_t     totalChunkSize(debug_only(const Mutex* lock)) const = 0;
  virtual size_t     maxChunkSize()   const = 0;
  virtual size_t     minSize()        const = 0;
  // Reset the dictionary to the initial conditions for a single
  // block.
  virtual void       reset(HeapWord* addr, size_t size) = 0;
  virtual void       reset() = 0;

  virtual void       dictCensusUpdate(size_t size, bool split, bool birth) = 0;
  virtual bool       coalDictOverPopulated(size_t size) = 0;
  virtual void       beginSweepDictCensus(double coalSurplusPercent,
                       float sweep_current, float sweep_ewstimate) = 0;
  virtual void       endSweepDictCensus(double splitSurplusPercent) = 0;
  virtual FreeChunk* findLargestDict() const = 0;
  // verify that the given chunk is in the dictionary.
  virtual bool verifyChunkInFreeLists(FreeChunk* tc) const = 0;

  // Sigma_{all_free_blocks} (block_size^2)
  virtual double sum_of_squared_block_sizes() const = 0;

  virtual FreeChunk* find_chunk_ends_at(HeapWord* target) const = 0;
  virtual void inc_totalSize(size_t v) = 0;
  virtual void dec_totalSize(size_t v) = 0;

  NOT_PRODUCT (
    virtual size_t   sumDictReturnedBytes() = 0;
    virtual void     initializeDictReturnedBytes() = 0;
    virtual size_t   totalCount() = 0;
  )

  virtual void       reportStatistics() const {
    gclog_or_tty->print("No statistics available");
  }

  virtual void       printDictCensus() const = 0;

  virtual void       verify()         const = 0;

  Mutex* par_lock()                const PRODUCT_RETURN0;
  void   set_par_lock(Mutex* lock)       PRODUCT_RETURN;
  void   verify_par_locked()       const PRODUCT_RETURN;
};
