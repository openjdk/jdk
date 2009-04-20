/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_heap.cpp.incl"


size_t CodeHeap::header_size() {
  return sizeof(HeapBlock);
}


// Implementation of Heap

CodeHeap::CodeHeap() {
  _number_of_committed_segments = 0;
  _number_of_reserved_segments  = 0;
  _segment_size                 = 0;
  _log2_segment_size            = 0;
  _next_segment                 = 0;
  _freelist                     = NULL;
  _free_segments                = 0;
}


void CodeHeap::mark_segmap_as_free(size_t beg, size_t end) {
  assert(0   <= beg && beg <  _number_of_committed_segments, "interval begin out of bounds");
  assert(beg <  end && end <= _number_of_committed_segments, "interval end   out of bounds");
  // setup _segmap pointers for faster indexing
  address p = (address)_segmap.low() + beg;
  address q = (address)_segmap.low() + end;
  // initialize interval
  while (p < q) *p++ = 0xFF;
}


void CodeHeap::mark_segmap_as_used(size_t beg, size_t end) {
  assert(0   <= beg && beg <  _number_of_committed_segments, "interval begin out of bounds");
  assert(beg <  end && end <= _number_of_committed_segments, "interval end   out of bounds");
  // setup _segmap pointers for faster indexing
  address p = (address)_segmap.low() + beg;
  address q = (address)_segmap.low() + end;
  // initialize interval
  int i = 0;
  while (p < q) {
    *p++ = i++;
    if (i == 0xFF) i = 1;
  }
}


static size_t align_to_page_size(size_t size) {
  const size_t alignment = (size_t)os::vm_page_size();
  assert(is_power_of_2(alignment), "no kidding ???");
  return (size + alignment - 1) & ~(alignment - 1);
}


static size_t align_to_allocation_size(size_t size) {
  const size_t alignment = (size_t)os::vm_allocation_granularity();
  assert(is_power_of_2(alignment), "no kidding ???");
  return (size + alignment - 1) & ~(alignment - 1);
}


void CodeHeap::on_code_mapping(char* base, size_t size) {
#ifdef LINUX
  extern void linux_wrap_code(char* base, size_t size);
  linux_wrap_code(base, size);
#endif
}


bool CodeHeap::reserve(size_t reserved_size, size_t committed_size,
                       size_t segment_size) {
  assert(reserved_size >= committed_size, "reserved < committed");
  assert(segment_size >= sizeof(FreeBlock), "segment size is too small");
  assert(is_power_of_2(segment_size), "segment_size must be a power of 2");

  _segment_size      = segment_size;
  _log2_segment_size = exact_log2(segment_size);

  // Reserve and initialize space for _memory.
  const size_t page_size = os::can_execute_large_page_memory() ?
          os::page_size_for_region(committed_size, reserved_size, 8) :
          os::vm_page_size();
  const size_t granularity = os::vm_allocation_granularity();
  const size_t r_align = MAX2(page_size, granularity);
  const size_t r_size = align_size_up(reserved_size, r_align);
  const size_t c_size = align_size_up(committed_size, page_size);

  const size_t rs_align = page_size == (size_t) os::vm_page_size() ? 0 :
    MAX2(page_size, granularity);
  ReservedCodeSpace rs(r_size, rs_align, rs_align > 0);
  os::trace_page_sizes("code heap", committed_size, reserved_size, page_size,
                       rs.base(), rs.size());
  if (!_memory.initialize(rs, c_size)) {
    return false;
  }

  on_code_mapping(_memory.low(), _memory.committed_size());
  _number_of_committed_segments = number_of_segments(_memory.committed_size());
  _number_of_reserved_segments  = number_of_segments(_memory.reserved_size());
  assert(_number_of_reserved_segments >= _number_of_committed_segments, "just checking");

  // reserve space for _segmap
  if (!_segmap.initialize(align_to_page_size(_number_of_reserved_segments), align_to_page_size(_number_of_committed_segments))) {
    return false;
  }
  assert(_segmap.committed_size() >= (size_t) _number_of_committed_segments, "could not commit  enough space for segment map");
  assert(_segmap.reserved_size()  >= (size_t) _number_of_reserved_segments , "could not reserve enough space for segment map");
  assert(_segmap.reserved_size()  >= _segmap.committed_size()     , "just checking");

  // initialize remaining instance variables
  clear();
  return true;
}


void CodeHeap::release() {
  Unimplemented();
}


bool CodeHeap::expand_by(size_t size) {
  // expand _memory space
  size_t dm = align_to_page_size(_memory.committed_size() + size) - _memory.committed_size();
  if (dm > 0) {
    char* base = _memory.low() + _memory.committed_size();
    if (!_memory.expand_by(dm)) return false;
    on_code_mapping(base, dm);
    size_t i = _number_of_committed_segments;
    _number_of_committed_segments = number_of_segments(_memory.committed_size());
    assert(_number_of_reserved_segments == number_of_segments(_memory.reserved_size()), "number of reserved segments should not change");
    assert(_number_of_reserved_segments >= _number_of_committed_segments, "just checking");
    // expand _segmap space
    size_t ds = align_to_page_size(_number_of_committed_segments) - _segmap.committed_size();
    if (ds > 0) {
      if (!_segmap.expand_by(ds)) return false;
    }
    assert(_segmap.committed_size() >= (size_t) _number_of_committed_segments, "just checking");
    // initialize additional segmap entries
    mark_segmap_as_free(i, _number_of_committed_segments);
  }
  return true;
}


void CodeHeap::shrink_by(size_t size) {
  Unimplemented();
}


void CodeHeap::clear() {
  _next_segment = 0;
  mark_segmap_as_free(0, _number_of_committed_segments);
}


void* CodeHeap::allocate(size_t size) {
  size_t length = number_of_segments(size + sizeof(HeapBlock));
  assert(length *_segment_size >= sizeof(FreeBlock), "not enough room for FreeList");

  // First check if we can satify request from freelist
  debug_only(verify());
  HeapBlock* block = search_freelist(length);
  debug_only(if (VerifyCodeCacheOften) verify());
  if (block != NULL) {
    assert(block->length() >= length && block->length() < length + CodeCacheMinBlockLength, "sanity check");
    assert(!block->free(), "must be marked free");
#ifdef ASSERT
    memset((void *)block->allocated_space(), badCodeHeapNewVal, size);
#endif
    return block->allocated_space();
  }

  if (length < CodeCacheMinBlockLength) {
    length = CodeCacheMinBlockLength;
  }
  if (_next_segment + length <= _number_of_committed_segments) {
    mark_segmap_as_used(_next_segment, _next_segment + length);
    HeapBlock* b =  block_at(_next_segment);
    b->initialize(length);
    _next_segment += length;
#ifdef ASSERT
    memset((void *)b->allocated_space(), badCodeHeapNewVal, size);
#endif
    return b->allocated_space();
  } else {
    return NULL;
  }
}


void CodeHeap::deallocate(void* p) {
  assert(p == find_start(p), "illegal deallocation");
  // Find start of HeapBlock
  HeapBlock* b = (((HeapBlock *)p) - 1);
  assert(b->allocated_space() == p, "sanity check");
#ifdef ASSERT
  memset((void *)b->allocated_space(),
         badCodeHeapFreeVal,
         size(b->length()) - sizeof(HeapBlock));
#endif
  add_to_freelist(b);

  debug_only(if (VerifyCodeCacheOften) verify());
}


void* CodeHeap::find_start(void* p) const {
  if (!contains(p)) {
    return NULL;
  }
  size_t i = segment_for(p);
  address b = (address)_segmap.low();
  if (b[i] == 0xFF) {
    return NULL;
  }
  while (b[i] > 0) i -= (int)b[i];
  HeapBlock* h = block_at(i);
  if (h->free()) {
    return NULL;
  }
  return h->allocated_space();
}


size_t CodeHeap::alignment_unit() const {
  // this will be a power of two
  return _segment_size;
}


size_t CodeHeap::alignment_offset() const {
  // The lowest address in any allocated block will be
  // equal to alignment_offset (mod alignment_unit).
  return sizeof(HeapBlock) & (_segment_size - 1);
}

// Finds the next free heapblock. If the current one is free, that it returned
void* CodeHeap::next_free(HeapBlock *b) const {
  // Since free blocks are merged, there is max. on free block
  // between two used ones
  if (b != NULL && b->free()) b = next_block(b);
  assert(b == NULL || !b->free(), "must be in use or at end of heap");
  return (b == NULL) ? NULL : b->allocated_space();
}

// Returns the first used HeapBlock
HeapBlock* CodeHeap::first_block() const {
  if (_next_segment > 0)
    return block_at(0);
  return NULL;
}

HeapBlock *CodeHeap::block_start(void *q) const {
  HeapBlock* b = (HeapBlock*)find_start(q);
  if (b == NULL) return NULL;
  return b - 1;
}

// Returns the next Heap block an offset into one
HeapBlock* CodeHeap::next_block(HeapBlock *b) const {
  if (b == NULL) return NULL;
  size_t i = segment_for(b) + b->length();
  if (i < _next_segment)
    return block_at(i);
  return NULL;
}


// Returns current capacity
size_t CodeHeap::capacity() const {
  return _memory.committed_size();
}

size_t CodeHeap::max_capacity() const {
  return _memory.reserved_size();
}

size_t CodeHeap::allocated_capacity() const {
  // Start with the committed size in _memory;
  size_t l = _memory.committed_size();

  // Subtract the committed, but unused, segments
  l -= size(_number_of_committed_segments - _next_segment);

  // Subtract the size of the freelist
  l -= size(_free_segments);

  return l;
}

// Free list management

FreeBlock *CodeHeap::following_block(FreeBlock *b) {
  return (FreeBlock*)(((address)b) + _segment_size * b->length());
}

// Inserts block b after a
void CodeHeap::insert_after(FreeBlock* a, FreeBlock* b) {
  assert(a != NULL && b != NULL, "must be real pointers");

  // Link b into the list after a
  b->set_link(a->link());
  a->set_link(b);

  // See if we can merge blocks
  merge_right(b); // Try to make b bigger
  merge_right(a); // Try to make a include b
}

// Try to merge this block with the following block
void CodeHeap::merge_right(FreeBlock *a) {
  assert(a->free(), "must be a free block");
  if (following_block(a) == a->link()) {
    assert(a->link() != NULL && a->link()->free(), "must be free too");
    // Update block a to include the following block
    a->set_length(a->length() + a->link()->length());
    a->set_link(a->link()->link());
    // Update find_start map
    size_t beg = segment_for(a);
    mark_segmap_as_used(beg, beg + a->length());
  }
}

void CodeHeap::add_to_freelist(HeapBlock *a) {
  FreeBlock* b = (FreeBlock*)a;
  assert(b != _freelist, "cannot be removed twice");

  // Mark as free and update free space count
  _free_segments += b->length();
  b->set_free();

  // First element in list?
  if (_freelist == NULL) {
    _freelist = b;
    b->set_link(NULL);
    return;
  }

  // Scan for right place to put into list. List
  // is sorted by increasing addresseses
  FreeBlock* prev = NULL;
  FreeBlock* cur  = _freelist;
  while(cur != NULL && cur < b) {
    assert(prev == NULL || prev < cur, "must be ordered");
    prev = cur;
    cur  = cur->link();
  }

  assert( (prev == NULL && b < _freelist) ||
          (prev < b && (cur == NULL || b < cur)), "list must be ordered");

  if (prev == NULL) {
    // Insert first in list
    b->set_link(_freelist);
    _freelist = b;
    merge_right(_freelist);
  } else {
    insert_after(prev, b);
  }
}

// Search freelist for an entry on the list with the best fit
// Return NULL if no one was found
FreeBlock* CodeHeap::search_freelist(size_t length) {
  FreeBlock *best_block = NULL;
  FreeBlock *best_prev  = NULL;
  size_t best_length = 0;

  // Search for smallest block which is bigger than length
  FreeBlock *prev = NULL;
  FreeBlock *cur = _freelist;
  while(cur != NULL) {
    size_t l = cur->length();
    if (l >= length && (best_block == NULL || best_length > l)) {
      // Remember best block, its previous element, and its length
      best_block = cur;
      best_prev  = prev;
      best_length = best_block->length();
    }

    // Next element in list
    prev = cur;
    cur  = cur->link();
  }

  if (best_block == NULL) {
    // None found
    return NULL;
  }

  assert((best_prev == NULL && _freelist == best_block ) ||
         (best_prev != NULL && best_prev->link() == best_block), "sanity check");

  // Exact (or at least good enough) fit. Remove from list.
  // Don't leave anything on the freelist smaller than CodeCacheMinBlockLength.
  if (best_length < length + CodeCacheMinBlockLength) {
    length = best_length;
    if (best_prev == NULL) {
      assert(_freelist == best_block, "sanity check");
      _freelist = _freelist->link();
    } else {
      // Unmap element
      best_prev->set_link(best_block->link());
    }
  } else {
    // Truncate block and return a pointer to the following block
    best_block->set_length(best_length - length);
    best_block = following_block(best_block);
    // Set used bit and length on new block
    size_t beg = segment_for(best_block);
    mark_segmap_as_used(beg, beg + length);
    best_block->set_length(length);
  }

  best_block->set_used();
  _free_segments -= length;
  return best_block;
}

//----------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

void CodeHeap::print() {
  tty->print_cr("The Heap");
}

#endif

void CodeHeap::verify() {
  // Count the number of blocks on the freelist, and the amount of space
  // represented.
  int count = 0;
  size_t len = 0;
  for(FreeBlock* b = _freelist; b != NULL; b = b->link()) {
    len += b->length();
    count++;
  }

  // Verify that freelist contains the right amount of free space
  guarantee(len == _free_segments, "wrong freelist");

  // Verify that the number of free blocks is not out of hand.
  static int free_block_threshold = 10000;
  if (count > free_block_threshold) {
    warning("CodeHeap: # of free blocks > %d", free_block_threshold);
    // Double the warning limit
    free_block_threshold *= 2;
  }

  // Verify that the freelist contains the same number of free blocks that is
  // found on the full list.
  for(HeapBlock *h = first_block(); h != NULL; h = next_block(h)) {
    if (h->free()) count--;
  }
  guarantee(count == 0, "missing free blocks");
}
