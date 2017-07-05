/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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
class ContiguousSpace;
class G1BlockOffsetSharedArray;

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
    assert(new_bottom <= _end, "new_bottom > _end");
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

class G1BlockOffsetSharedArray: public CHeapObj {
  friend class G1BlockOffsetArray;
  friend class G1BlockOffsetArrayContigSpace;
  friend class VMStructs;

private:
  // The reserved region covered by the shared array.
  MemRegion _reserved;

  // End of the current committed region.
  HeapWord* _end;

  // Array for keeping offsets for retrieving object start fast given an
  // address.
  VirtualSpace _vs;
  u_char* _offset_array;          // byte array keeping backwards offsets

  // Bounds checking accessors:
  // For performance these have to devolve to array accesses in product builds.
  u_char offset_array(size_t index) const {
    assert(index < _vs.committed_size(), "index out of range");
    return _offset_array[index];
  }

  void set_offset_array(size_t index, u_char offset) {
    assert(index < _vs.committed_size(), "index out of range");
    assert(offset <= N_words, "offset too large");
    _offset_array[index] = offset;
  }

  void set_offset_array(size_t index, HeapWord* high, HeapWord* low) {
    assert(index < _vs.committed_size(), "index out of range");
    assert(high >= low, "addresses out of order");
    assert(pointer_delta(high, low) <= N_words, "offset too large");
    _offset_array[index] = (u_char) pointer_delta(high, low);
  }

  void set_offset_array(HeapWord* left, HeapWord* right, u_char offset) {
    assert(index_for(right - 1) < _vs.committed_size(),
           "right address out of range");
    assert(left  < right, "Heap addresses out of order");
    size_t num_cards = pointer_delta(right, left) >> LogN_words;
    memset(&_offset_array[index_for(left)], offset, num_cards);
  }

  void set_offset_array(size_t left, size_t right, u_char offset) {
    assert(right < _vs.committed_size(), "right address out of range");
    assert(left  <= right, "indexes out of order");
    size_t num_cards = right - left + 1;
    memset(&_offset_array[left], offset, num_cards);
  }

  void check_offset_array(size_t index, HeapWord* high, HeapWord* low) const {
    assert(index < _vs.committed_size(), "index out of range");
    assert(high >= low, "addresses out of order");
    assert(pointer_delta(high, low) <= N_words, "offset too large");
    assert(_offset_array[index] == pointer_delta(high, low),
           "Wrong offset");
  }

  bool is_card_boundary(HeapWord* p) const;

  // Return the number of slots needed for an offset array
  // that covers mem_region_words words.
  // We always add an extra slot because if an object
  // ends on a card boundary we put a 0 in the next
  // offset array slot, so we want that slot always
  // to be reserved.

  size_t compute_size(size_t mem_region_words) {
    size_t number_of_slots = (mem_region_words / N_words) + 1;
    return ReservedSpace::page_align_size_up(number_of_slots);
  }

public:
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
  G1BlockOffsetSharedArray(MemRegion reserved, size_t init_word_size);

  // Notes a change in the committed size of the region covered by the
  // table.  The "new_word_size" may not be larger than the size of the
  // reserved region this table covers.
  void resize(size_t new_word_size);

  void set_bottom(HeapWord* new_bottom);

  // Updates all the BlockOffsetArray's sharing this shared array to
  // reflect the current "top"'s of their spaces.
  void update_offset_arrays();

  // Return the appropriate index into "_offset_array" for "p".
  inline size_t index_for(const void* p) const;

  // Return the address indicating the start of the region corresponding to
  // "index" in "_offset_array".
  inline HeapWord* address_for_index(size_t index) const;
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

  // The following enums are used by do_block_helper
  enum Action {
    Action_single,      // BOT records a single block (see single_block())
    Action_mark,        // BOT marks the start of a block (see mark_block())
    Action_check        // Check that BOT records block correctly
                        // (see verify_single_block()).
  };

  // This is the array, which can be shared by several BlockOffsetArray's
  // servicing different
  G1BlockOffsetSharedArray* _array;

  // The space that owns this subregion.
  Space* _sp;

  // If "_sp" is a contiguous space, the field below is the view of "_sp"
  // as a contiguous space, else NULL.
  ContiguousSpace* _csp;

  // If true, array entries are initialized to 0; otherwise, they are
  // initialized to point backwards to the beginning of the covered region.
  bool _init_to_zero;

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

  // A helper function for BOT adjustment/verification work
  void do_block_internal(HeapWord* blk_start, HeapWord* blk_end, Action action);

protected:

  ContiguousSpace* csp() const { return _csp; }

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
  // region is passed as a parameter.  If "init_to_zero" is true, the
  // elements of the array are initialized to zero.  Otherwise, they are
  // initialized to point backwards to the beginning.
  G1BlockOffsetArray(G1BlockOffsetSharedArray* array, MemRegion mr,
                     bool init_to_zero);

  // Note: this ought to be part of the constructor, but that would require
  // "this" to be passed as a parameter to a member constructor for
  // the containing concrete subtype of Space.
  // This would be legal C++, but MS VC++ doesn't allow it.
  void set_space(Space* sp);

  // Resets the covered region to the given "mr".
  void set_region(MemRegion mr);

  // Resets the covered region to one with the same _bottom as before but
  // the "new_word_size".
  void resize(size_t new_word_size);

  // These must be guaranteed to work properly (i.e., do nothing)
  // when "blk_start" ("blk" for second version) is "NULL".
  virtual void alloc_block(HeapWord* blk_start, HeapWord* blk_end);
  virtual void alloc_block(HeapWord* blk, size_t size) {
    alloc_block(blk, blk + size);
  }

  // The following methods are useful and optimized for a
  // general, non-contiguous space.

  // The given arguments are required to be the starts of adjacent ("blk1"
  // before "blk2") well-formed blocks covered by "this".  After this call,
  // they should be considered to form one block.
  virtual void join_blocks(HeapWord* blk1, HeapWord* blk2);

  // Given a block [blk_start, blk_start + full_blk_size), and
  // a left_blk_size < full_blk_size, adjust the BOT to show two
  // blocks [blk_start, blk_start + left_blk_size) and
  // [blk_start + left_blk_size, blk_start + full_blk_size).
  // It is assumed (and verified in the non-product VM) that the
  // BOT was correct for the original block.
  void split_block(HeapWord* blk_start, size_t full_blk_size,
                           size_t left_blk_size);

  // Adjust the BOT to show that it has a single block in the
  // range [blk_start, blk_start + size). All necessary BOT
  // cards are adjusted, but _unallocated_block isn't.
  void single_block(HeapWord* blk_start, HeapWord* blk_end);
  void single_block(HeapWord* blk, size_t size) {
    single_block(blk, blk + size);
  }

  // Adjust BOT to show that it has a block in the range
  // [blk_start, blk_start + size). Only the first card
  // of BOT is touched. It is assumed (and verified in the
  // non-product VM) that the remaining cards of the block
  // are correct.
  void mark_block(HeapWord* blk_start, HeapWord* blk_end);
  void mark_block(HeapWord* blk, size_t size) {
    mark_block(blk, blk + size);
  }

  // Adjust _unallocated_block to indicate that a particular
  // block has been newly allocated or freed. It is assumed (and
  // verified in the non-product VM) that the BOT is correct for
  // the given block.
  inline void allocated(HeapWord* blk_start, HeapWord* blk_end) {
    // Verify that the BOT shows [blk, blk + blk_size) to be one block.
    verify_single_block(blk_start, blk_end);
    if (BlockOffsetArrayUseUnallocatedBlock) {
      _unallocated_block = MAX2(_unallocated_block, blk_end);
    }
  }

  inline void allocated(HeapWord* blk, size_t size) {
    allocated(blk, blk + size);
  }

  inline void freed(HeapWord* blk_start, HeapWord* blk_end);

  inline void freed(HeapWord* blk, size_t size);

  virtual HeapWord* block_start_unsafe(const void* addr);
  virtual HeapWord* block_start_unsafe_const(const void* addr) const;

  // Requires "addr" to be the start of a card and returns the
  // start of the block that contains the given address.
  HeapWord* block_start_careful(const void* addr) const;

  // If true, initialize array slots with no allocated blocks to zero.
  // Otherwise, make them point back to the front.
  bool init_to_zero() { return _init_to_zero; }

  // Verification & debugging - ensure that the offset table reflects the fact
  // that the block [blk_start, blk_end) or [blk, blk + size) is a
  // single block of storage. NOTE: can;t const this because of
  // call to non-const do_block_internal() below.
  inline void verify_single_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (VerifyBlockOffsetArray) {
      do_block_internal(blk_start, blk_end, Action_check);
    }
  }

  inline void verify_single_block(HeapWord* blk, size_t size) {
    verify_single_block(blk, blk + size);
  }

  // Verify that the given block is before _unallocated_block
  inline void verify_not_unallocated(HeapWord* blk_start,
                                     HeapWord* blk_end) const {
    if (BlockOffsetArrayUseUnallocatedBlock) {
      assert(blk_start < blk_end, "Block inconsistency?");
      assert(blk_end <= _unallocated_block, "_unallocated_block problem");
    }
  }

  inline void verify_not_unallocated(HeapWord* blk, size_t size) const {
    verify_not_unallocated(blk, blk + size);
  }

  void check_all_cards(size_t left_card, size_t right_card) const;

  virtual void set_for_starts_humongous(HeapWord* new_end);
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


 public:
  G1BlockOffsetArrayContigSpace(G1BlockOffsetSharedArray* array, MemRegion mr);

  // Initialize the threshold to reflect the first boundary after the
  // bottom of the covered region.
  HeapWord* initialize_threshold();

  // Zero out the entry for _bottom (offset will be zero).
  void      zero_bottom_entry();

  // Return the next threshold, the point at which the table should be
  // updated.
  HeapWord* threshold() const { return _next_offset_threshold; }

  // These must be guaranteed to work properly (i.e., do nothing)
  // when "blk_start" ("blk" for second version) is "NULL".  In this
  // implementation, that's true because NULL is represented as 0, and thus
  // never exceeds the "_next_offset_threshold".
  void alloc_block(HeapWord* blk_start, HeapWord* blk_end) {
    if (blk_end > _next_offset_threshold)
      alloc_block_work1(blk_start, blk_end);
  }
  void alloc_block(HeapWord* blk, size_t size) {
     alloc_block(blk, blk+size);
  }

  HeapWord* block_start_unsafe(const void* addr);
  HeapWord* block_start_unsafe_const(const void* addr) const;

  virtual void set_for_starts_humongous(HeapWord* new_end);
};
