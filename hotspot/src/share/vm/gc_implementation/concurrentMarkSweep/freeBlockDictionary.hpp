/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
                       float inter_sweep_current, float inter_sweep_estimate,
                       float intra__sweep_current) = 0;
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
  virtual void       print_free_lists(outputStream* st) const = 0;

  virtual void       verify()         const = 0;

  Mutex* par_lock()                const PRODUCT_RETURN0;
  void   set_par_lock(Mutex* lock)       PRODUCT_RETURN;
  void   verify_par_locked()       const PRODUCT_RETURN;
};
