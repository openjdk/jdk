/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1BLOCKOFFSETTABLE_HPP
#define SHARE_VM_GC_G1_G1BLOCKOFFSETTABLE_HPP

#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "utilities/globalDefinitions.hpp"

// The CollectedHeap type requires subtypes to implement a method
// "block_start".  For some subtypes, notably generational
// systems using card-table-based write barriers, the efficiency of this
// operation may be important.  Implementations of the "BlockOffsetArray"
// class may be useful in providing such efficient implementations.
//
// While generally mirroring the structure of the BOT for GenCollectedHeap,
// the following types are tailored more towards G1's uses; these should,
// however, be merged back into a common BOT to avoid code duplication
// and reduce maintenance overhead.
//
//    G1BlockOffsetTable (abstract)
//    -- G1BlockOffsetArray                (uses G1BlockOffsetSharedArray)
//       -- G1BlockOffsetArrayContigSpace
//
// A main impediment to the consolidation of this code might be the
// effect of making some of the block_start*() calls non-const as
// below. Whether that might adversely affect performance optimizations
// that compilers might normally perform in the case of non-G1
// collectors needs to be carefully investigated prior to any such
// consolidation.

// Forward declarations
class G1BlockOffsetSharedArray;
class G1OffsetTableContigSpace;

class G1BlockOffsetTable VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
protected:
  // These members describe the region covered by the table.

  // The space this table is covering.
  HeapWord* _bottom;    // == reserved.start
  HeapWord* _end;       // End of currently allocated region.

public:
  // Initialize the table to cover the given space.
  // The contents of the initial table are undefined.
  G1BlockOffsetTable(HeapWord* bottom, HeapWord* end) :
    _bottom(bottom), _end(end)
    {
      assert(_bottom <= _end, "arguments out of order");
    }

  // Note that the committed size of the covered space may have changed,
  // so the table size might also wish to change.
  virtual void resize(size_t new_word_size) = 0;

  virtual void set_bottom(HeapWord* new_bottom) {
    assert(new_bottom <= _end,
           "new_bottom (" PTR_FORMAT ") > _end (" PTR_FORMAT ")",
           p2i(new_bottom), p2i(_end));
    _bottom = new_bottom;
    resize(pointer_delta(_end, _bottom));
  }

  // Requires "addr" to be contained by a block, and returns the address of
  // the start of that block.  (May have side effects, namely updating of
  // shared array entries that "point" too far backwards.  This can occur,
  // for example, when LAB allocation is used in a space covered by the
  // table.)
  virtual HeapWord* block_start_unsafe(const void* addr) = 0;
  // Same as above, but does not have any of the possible side effects
  // discussed above.
  virtual HeapWord* block_start_unsafe_const(const void* addr) const = 0;

  // Returns the address of the start of the block containing "addr", or
  // else "null" if it is covered by no block.  (May have side effects,
  // namely updating of shared array entries that "point" too far
  // backwards.  This can occur, for example, when lab allocation is used
  // in a space covered by the table.)
  inline HeapWord* block_start(const void* addr);
  // Same as above, but does not have any of the possible side effects
  // discussed above.
  inline HeapWord* block_start_const(const void* addr) const;
};

class G1BlockOffsetSharedArrayMappingChangedListener : public G1MappingChangedListener {
 public:
  virtual void on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
    // Nothing to do. The BOT is hard-wired to be part of the HeapRegion, and we cannot
    // retrieve it here since this would cause firing of several asserts. The code
    // executed after commit of a region already needs to do some re-initialization of
    // the HeapRegion, so we combine that.
  }
};

// This implementation of "G1BlockOffsetTable" divides the covered region
// into "N"-word subregions (where "N" = 2^"LogN".  An array with an entry
// for each such subregion indicates how far back one must go to find the
// start of the chunk that includes the first word of the subregion.
//
// Each BlockOffsetArray is owned by a Space.  However, the actual array
// may be shared by several BlockOffsetArrays; this is useful
// when a single resizable area (such as a generation) is divided up into
// several spaces in which contiguous allocation takes place,
// such as, for example, in G1 or in the train generation.)

// Here is the shared array type.

class G1BlockOffsetSharedArray: public CHeapObj<mtGC> {
  friend class G1BlockOffsetArray;
  friend class G1BlockOffsetArrayContigSpace;
  friend class VMStructs;

private:
  G1BlockOffsetSharedArrayMappingChangedListener _listener;
  // The reserved region covered by the shared array.
  MemRegion _reserved;

  // End of the current committed region.
  HeapWord* _end;

  // Array for keeping offsets for retrieving object start fast given an
  // address.
  u_char* _offset_array;          // byte array keeping backwards offsets

  void check_offset(size_t offset, const char* msg) const {
    assert(offset <= N_words,
           "%s - offset: " SIZE_FORMAT ", N_words: %u",
           msg, offset, (uint)N_words);
  }

  // Bounds checking accessors:
  // For performance these have to devolve to array accesses in product builds.
  inline u_char offset_array(size_t index) const;

  void set_offset_array_raw(size_t index, u_char offset) {
    _offset_array[index] = offset;
  }

  inline void set_offset_array(size_t index, u_char offset);

  inline void set_offset_array(size_t index, HeapWord* high, HeapWord* low);

  inline void set_offset_array(size_t left, size_t right, u_char offset);

  bool is_card_boundary(HeapWord* p) const;

  void check_index(size_t index, const char* msg) const NOT_DEBUG_RETURN;

public:

  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  static size_t compute_size(size_t mem_region_words) {
    size_t number_of_slots = (mem_region_words / N_words);
    return ReservedSpace::allocation_align_size_up(number_of_slots);
  }

  // Returns how many bytes of the heap a single byte of the BOT corresponds to.
  static size_t heap_map_factor() {
    return N_bytes;
  }

  enum SomePublicConstants {
    LogN = 9,
    LogN_words = LogN - LogHeapWordSize,
    N_bytes = 1 << LogN,
    N_words = 1 << LogN_words
  };

  // Initialize the table to cover from "base" to (at least)
  // "base + init_word_size".  In the future, the table may be expanded
  // (see "resize" below) up to the size of "_reserved" (which must be at
  // least "init_word_size".) The contents of the initial table are
  // undefined; it is the responsibility of the constituent
  // G1BlockOffsetTable(s) to initialize cards.
  G1BlockOffsetSharedArray(MemRegion heap, G1RegionToSpaceMapper* storage);

  // Return the appropriate index into "_offset_array" for "p".
  inline size_t index_for(const void* p) const;
  inline size_t index_for_raw(const void* p) const;

  // Return the address indicating the start of the region corresponding to
  // "index" in "_offset_array".
  inline HeapWord* address_for_index(size_t index) const;
  // Variant of address_for_index that does not check the index for validity.
  inline HeapWord* address_for_index_raw(size_t index) const {
    return _reserved.start() + (index << LogN_words);
  }
};

// And here is the G1BlockOffsetTable subtype that uses the array.

class G1BlockOffsetArray: public G1BlockOffsetTable {
  friend class G1BlockOffsetSharedArray;
  friend class G1BlockOffsetArrayContigSpace;
  friend class VMStructs;
private:
  enum SomePrivateConstants {
    N_words = G1BlockOffsetSharedArray::N_words,
    LogN    = G1BlockOffsetSharedArray::LogN
  };

  // This is the array, which can be shared by several BlockOffsetArray's
  // servicing different
  G1BlockOffsetSharedArray* _array;

  // The space that owns this subregion.
  G1OffsetTableContigSpace* _gsp;

  // The portion [_unallocated_block, _sp.end()) of the space that
  // is a single block known not to contain any objects.
  // NOTE: See BlockOffsetArrayUseUnallocatedBlock flag.
  HeapWord* _unallocated_block;

  // Sets the entries
  // corresponding to the cards starting at "start" and ending at "end"
  // to point back to the card before "start": the interval [start, end)
  // is right-open.
  void set_remainder_to_point_to_start(HeapWord* start, HeapWord* end);
  // Same as above, except that the args here are a card _index_ interval
  // that is closed: [start_index, end_index]
  void set_remainder_to_point_to_start_incl(size_t start, size_t end);

protected:

  G1OffsetTableContigSpace* gsp() const { return _gsp; }

  inline size_t block_size(const HeapWord* p) const;

  // Returns the address of a block whose start is at most "addr".
  // If "has_max_index" is true, "assumes "max_index" is the last valid one
  // in the array.
  inline HeapWord* block_at_or_preceding(const void* addr,
                                         bool has_max_index,
                                         size_t max_index) const;

  // "q" is a block boundary that is <= "addr"; "n" is the address of the
  // next block (or the end of the space.)  Return the address of the
  // beginning of the block that contains "addr".  Does so without side
  // effects (see, e.g., spec of  block_start.)
  inline HeapWord*
  forward_to_block_containing_addr_const(HeapWord* q, HeapWord* n,
                                         const void* addr) const;

  // "q" is a block boundary that is <= "addr"; return the address of the
  // beginning of the block that contains "addr".  May have side effects
  // on "this", by updating imprecise entries.
  inline HeapWord* forward_to_block_containing_addr(HeapWord* q,
                                                    const void* addr);

  // "q" is a block boundary that is <= "addr"; "n" is the address of the
  // next block (or the end of the space.)  Return the address of the
  // beginning of the block that contains "addr".  May have side effects
  // on "this", by updating imprecise entries.
  HeapWord* forward_to_block_containing_addr_slow(HeapWord* q,
                                                  HeapWord* n,
                                                  const void* addr);

  // Requires that "*threshold_" be the first array entry boundary at or
  // above "blk_start", and that "*index_" be the corresponding array
  // index.  If the block starts at or crosses "*threshold_", records
  // "blk_start" as the appropriate block start for the array index
  // starting at "*threshold_", and for any other indices crossed by the
  // block.  Updates "*threshold_" and "*index_" to correspond to the first
  // index after the block end.
  void alloc_block_work2(HeapWord** threshold_, size_t* index_,
                         HeapWord* blk_start, HeapWord* blk_end);

public:
  // The space may not have it's bottom and top set yet, which is why the
  // region is passed as a parameter. The elements of the array are
  // initialized to zero.
  G1BlockOffsetArray(G1BlockOffsetSharedArray* array, MemRegion mr);

  // Note: this ought to be part of the constructor, but that would require
  // "this" to be passed as a parameter to a member constructor for
  // the containing concrete subtype of Space.
  // This would be legal C++, but MS VC++ doesn't allow it.
  void set_space(G1OffsetTableContigSpace* sp);

  // Resets the covered region to one with the same _bottom as before but
  // the "new_word_size".
  void resize(size_t new_word_size);

  virtual HeapWord* block_start_unsafe(const void* addr);
  virtual HeapWord* block_start_unsafe_const(const void* addr) const;

  void check_all_cards(size_t left_card, size_t right_card) const;

  void verify() const;

  virtual void print_on(outputStream* out) PRODUCT_RETURN;
};

// A subtype of BlockOffsetArray that takes advantage of the fact
// that its underlying space is a ContiguousSpace, so that its "active"
// region can be more efficiently tracked (than for a non-contiguous space).
class G1BlockOffsetArrayContigSpace: public G1BlockOffsetArray {
  friend class VMStructs;

  // allocation boundary at which offset array must be updated
  HeapWord* _next_offset_threshold;
  size_t    _next_offset_index;      // index corresponding to that boundary

  // Work function to be called when allocation start crosses the next
  // threshold in the contig space.
  void alloc_block_work1(HeapWord* blk_start, HeapWord* blk_end) {
    alloc_block_work2(&_next_offset_threshold, &_next_offset_index,
                      blk_start, blk_end);
  }

  // Zero out the entry for _bottom (offset will be zero). Does not check for availability of the
  // memory first.
  void zero_bottom_entry_raw();
  // Variant of initialize_threshold that does not check for availability of the
  // memory first.
  HeapWord* initialize_threshold_raw();
 public:
  G1BlockOffsetArrayContigSpace(G1BlockOffsetSharedArray* array, MemRegion mr);

  // Initialize the threshold to reflect the first boundary after the
  // bottom of the covered region.
  HeapWord* initialize_threshold();

  void reset_bot() {
    zero_bottom_entry_raw();
    initialize_threshold_raw();
  }

  // Return the next threshold, the point at which the table should be
  // updated.
  HeapWord* threshold() const { return _next_offset_threshold; }

  // These must be guaranteed to work properly (i.e., do nothing)
  // when "blk_start" ("blk" for second version) is "NULL".  In this
  // implementation, that's true because NULL is represented as 0, and thus
  // never exceeds the "_next_offset_threshold".
  void alloc_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (blk_end > _next_offset_threshold) {
      alloc_block_work1(blk_start, blk_end);
    }
  }
  void alloc_block(HeapWord* blk, size_t size) {
    alloc_block(blk, blk+size);
  }

  HeapWord* block_start_unsafe(const void* addr);
  HeapWord* block_start_unsafe_const(const void* addr) const;

  void set_for_starts_humongous(HeapWord* obj_top);

  virtual void print_on(outputStream* out) PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_G1_G1BLOCKOFFSETTABLE_HPP
