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

#ifndef SHARE_VM_MEMORY_FREEBLOCKDICTIONARY_HPP
#define SHARE_VM_MEMORY_FREEBLOCKDICTIONARY_HPP

#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// A FreeBlockDictionary is an abstract superclass that will allow
// a number of alternative implementations in the future.
template <class Chunk>
class FreeBlockDictionary: public CHeapObj<mtGC> {
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
  // This field is added and can be set to point to the
  // the Mutex used to synchronize access to the
  // dictionary so that assertion checking can be done.
  // For example it is set to point to _parDictionaryAllocLock.
  NOT_PRODUCT(Mutex* _lock;)

 public:
  virtual void       remove_chunk(Chunk* fc) = 0;
  virtual Chunk*     get_chunk(size_t size, Dither dither = atLeast) = 0;
  virtual void       return_chunk(Chunk* chunk) = 0;
  virtual size_t     total_chunk_size(debug_only(const Mutex* lock)) const = 0;
  virtual size_t     max_chunk_size()   const = 0;
  virtual size_t     min_size()        const = 0;
  // Reset the dictionary to the initial conditions for a single
  // block.
  virtual void       reset(HeapWord* addr, size_t size) = 0;
  virtual void       reset() = 0;

  virtual void       dict_census_update(size_t size, bool split, bool birth) = 0;
  virtual bool       coal_dict_over_populated(size_t size) = 0;
  virtual void       begin_sweep_dict_census(double coalSurplusPercent,
                       float inter_sweep_current, float inter_sweep_estimate,
                       float intra__sweep_current) = 0;
  virtual void       end_sweep_dict_census(double splitSurplusPercent) = 0;
  virtual Chunk*     find_largest_dict() const = 0;
  // verify that the given chunk is in the dictionary.
  virtual bool verify_chunk_in_free_list(Chunk* tc) const = 0;

  // Sigma_{all_free_blocks} (block_size^2)
  virtual double sum_of_squared_block_sizes() const = 0;

  virtual Chunk* find_chunk_ends_at(HeapWord* target) const = 0;
  virtual void inc_total_size(size_t v) = 0;
  virtual void dec_total_size(size_t v) = 0;

  NOT_PRODUCT (
    virtual size_t   sum_dict_returned_bytes() = 0;
    virtual void     initialize_dict_returned_bytes() = 0;
    virtual size_t   total_count() = 0;
  )

  virtual void       report_statistics(outputStream* st) const {
    st->print_cr("No statistics available");
  }

  virtual void       print_dict_census(outputStream* st) const = 0;
  virtual void       print_free_lists(outputStream* st) const = 0;

  virtual void       verify()         const = 0;

  Mutex* par_lock()                const PRODUCT_RETURN0;
  void   set_par_lock(Mutex* lock)       PRODUCT_RETURN;
  void   verify_par_locked()       const PRODUCT_RETURN;
};

#endif // SHARE_VM_MEMORY_FREEBLOCKDICTIONARY_HPP
