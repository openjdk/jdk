/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/freeBlockDictionary.hpp"
#include "memory/freeList.hpp"
#include "memory/metachunk.hpp"
#include "memory/sharedHeap.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutex.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/freeChunk.hpp"
#include "gc_implementation/g1/g1CodeCacheRemSet.hpp"
#endif // INCLUDE_ALL_GCS

// Free list.  A FreeList is used to access a linked list of chunks
// of space in the heap.  The head and tail are maintained so that
// items can be (as in the current implementation) added at the
// at the tail of the list and removed from the head of the list to
// maintain a FIFO queue.

template <class Chunk>
FreeList<Chunk>::FreeList() :
  _head(NULL), _tail(NULL)
#ifdef ASSERT
  , _protecting_lock(NULL)
#endif
{
  _size         = 0;
  _count        = 0;
}

template <class Chunk>
void FreeList<Chunk>::link_head(Chunk* v) {
  assert_proper_lock_protection();
  set_head(v);
  // If this method is not used (just set the head instead),
  // this check can be avoided.
  if (v != NULL) {
    v->link_prev(NULL);
  }
}



template <class Chunk>
void FreeList<Chunk>::reset() {
  // Don't set the _size to 0 because this method is
  // used with a existing list that has a size but which has
  // been emptied.
  // Don't clear the _protecting_lock of an existing list.
  set_count(0);
  set_head(NULL);
  set_tail(NULL);
}

template <class Chunk>
void FreeList<Chunk>::initialize() {
#ifdef ASSERT
  // Needed early because it might be checked in other initializing code.
  set_protecting_lock(NULL);
#endif
  reset();
  set_size(0);
}

template <class Chunk_t>
Chunk_t* FreeList<Chunk_t>::get_chunk_at_head() {
  assert_proper_lock_protection();
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
  Chunk_t* fc = head();
  if (fc != NULL) {
    Chunk_t* nextFC = fc->next();
    if (nextFC != NULL) {
      // The chunk fc being removed has a "next".  Set the "next" to the
      // "prev" of fc.
      nextFC->link_prev(NULL);
    } else { // removed tail of list
      link_tail(NULL);
    }
    link_head(nextFC);
    decrement_count();
  }
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
  return fc;
}


template <class Chunk>
void FreeList<Chunk>::getFirstNChunksFromList(size_t n, FreeList<Chunk>* fl) {
  assert_proper_lock_protection();
  assert(fl->count() == 0, "Precondition");
  if (count() > 0) {
    int k = 1;
    fl->set_head(head()); n--;
    Chunk* tl = head();
    while (tl->next() != NULL && n > 0) {
      tl = tl->next(); n--; k++;
    }
    assert(tl != NULL, "Loop Inv.");

    // First, fix up the list we took from.
    Chunk* new_head = tl->next();
    set_head(new_head);
    set_count(count() - k);
    if (new_head == NULL) {
      set_tail(NULL);
    } else {
      new_head->link_prev(NULL);
    }
    // Now we can fix up the tail.
    tl->link_next(NULL);
    // And return the result.
    fl->set_tail(tl);
    fl->set_count(k);
  }
}

// Remove this chunk from the list
template <class Chunk>
void FreeList<Chunk>::remove_chunk(Chunk*fc) {
   assert_proper_lock_protection();
   assert(head() != NULL, "Remove from empty list");
   assert(fc != NULL, "Remove a NULL chunk");
   assert(size() == fc->size(), "Wrong list");
   assert(head() == NULL || head()->prev() == NULL, "list invariant");
   assert(tail() == NULL || tail()->next() == NULL, "list invariant");

   Chunk* prevFC = fc->prev();
   Chunk* nextFC = fc->next();
   if (nextFC != NULL) {
     // The chunk fc being removed has a "next".  Set the "next" to the
     // "prev" of fc.
     nextFC->link_prev(prevFC);
   } else { // removed tail of list
     link_tail(prevFC);
   }
   if (prevFC == NULL) { // removed head of list
     link_head(nextFC);
     assert(nextFC == NULL || nextFC->prev() == NULL,
       "Prev of head should be NULL");
   } else {
     prevFC->link_next(nextFC);
     assert(tail() != prevFC || prevFC->next() == NULL,
       "Next of tail should be NULL");
   }
   decrement_count();
   assert(((head() == NULL) + (tail() == NULL) + (count() == 0)) % 3 == 0,
          "H/T/C Inconsistency");
   // clear next and prev fields of fc, debug only
   NOT_PRODUCT(
     fc->link_prev(NULL);
     fc->link_next(NULL);
   )
   assert(fc->is_free(), "Should still be a free chunk");
   assert(head() == NULL || head()->prev() == NULL, "list invariant");
   assert(tail() == NULL || tail()->next() == NULL, "list invariant");
   assert(head() == NULL || head()->size() == size(), "wrong item on list");
   assert(tail() == NULL || tail()->size() == size(), "wrong item on list");
}

// Add this chunk at the head of the list.
template <class Chunk>
void FreeList<Chunk>::return_chunk_at_head(Chunk* chunk, bool record_return) {
  assert_proper_lock_protection();
  assert(chunk != NULL, "insert a NULL chunk");
  assert(size() == chunk->size(), "Wrong size");
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");

  Chunk* oldHead = head();
  assert(chunk != oldHead, "double insertion");
  chunk->link_after(oldHead);
  link_head(chunk);
  if (oldHead == NULL) { // only chunk in list
    assert(tail() == NULL, "inconsistent FreeList");
    link_tail(chunk);
  }
  increment_count(); // of # of chunks in list
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
  assert(head() == NULL || head()->size() == size(), "wrong item on list");
  assert(tail() == NULL || tail()->size() == size(), "wrong item on list");
}

template <class Chunk>
void FreeList<Chunk>::return_chunk_at_head(Chunk* chunk) {
  assert_proper_lock_protection();
  return_chunk_at_head(chunk, true);
}

// Add this chunk at the tail of the list.
template <class Chunk>
void FreeList<Chunk>::return_chunk_at_tail(Chunk* chunk, bool record_return) {
  assert_proper_lock_protection();
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
  assert(chunk != NULL, "insert a NULL chunk");
  assert(size() == chunk->size(), "wrong size");

  Chunk* oldTail = tail();
  assert(chunk != oldTail, "double insertion");
  if (oldTail != NULL) {
    oldTail->link_after(chunk);
  } else { // only chunk in list
    assert(head() == NULL, "inconsistent FreeList");
    link_head(chunk);
  }
  link_tail(chunk);
  increment_count();  // of # of chunks in list
  assert(head() == NULL || head()->prev() == NULL, "list invariant");
  assert(tail() == NULL || tail()->next() == NULL, "list invariant");
  assert(head() == NULL || head()->size() == size(), "wrong item on list");
  assert(tail() == NULL || tail()->size() == size(), "wrong item on list");
}

template <class Chunk>
void FreeList<Chunk>::return_chunk_at_tail(Chunk* chunk) {
  return_chunk_at_tail(chunk, true);
}

template <class Chunk>
void FreeList<Chunk>::prepend(FreeList<Chunk>* fl) {
  assert_proper_lock_protection();
  if (fl->count() > 0) {
    if (count() == 0) {
      set_head(fl->head());
      set_tail(fl->tail());
      set_count(fl->count());
    } else {
      // Both are non-empty.
      Chunk* fl_tail = fl->tail();
      Chunk* this_head = head();
      assert(fl_tail->next() == NULL, "Well-formedness of fl");
      fl_tail->link_next(this_head);
      this_head->link_prev(fl_tail);
      set_head(fl->head());
      set_count(count() + fl->count());
    }
    fl->set_head(NULL);
    fl->set_tail(NULL);
    fl->set_count(0);
  }
}

// verify_chunk_in_free_lists() is used to verify that an item is in this free list.
// It is used as a debugging aid.
template <class Chunk>
bool FreeList<Chunk>::verify_chunk_in_free_list(Chunk* fc) const {
  // This is an internal consistency check, not part of the check that the
  // chunk is in the free lists.
  guarantee(fc->size() == size(), "Wrong list is being searched");
  Chunk* curFC = head();
  while (curFC) {
    // This is an internal consistency check.
    guarantee(size() == curFC->size(), "Chunk is in wrong list.");
    if (fc == curFC) {
      return true;
    }
    curFC = curFC->next();
  }
  return false;
}

#ifndef PRODUCT
template <class Chunk>
void FreeList<Chunk>::assert_proper_lock_protection_work() const {
  assert(protecting_lock() != NULL, "Don't call this directly");
  assert(ParallelGCThreads > 0, "Don't call this directly");
  Thread* thr = Thread::current();
  if (thr->is_VM_thread() || thr->is_ConcurrentGC_thread()) {
    // assert that we are holding the freelist lock
  } else if (thr->is_GC_task_thread()) {
    assert(protecting_lock()->owned_by_self(), "FreeList RACE DETECTED");
  } else if (thr->is_Java_thread()) {
    assert(!SafepointSynchronize::is_at_safepoint(), "Should not be executing");
  } else {
    ShouldNotReachHere();  // unaccounted thread type?
  }
}
#endif

// Print the "label line" for free list stats.
template <class Chunk>
void FreeList<Chunk>::print_labels_on(outputStream* st, const char* c) {
  st->print("%16s\t", c);
  st->print("%14s\t"    "%14s\t"    "%14s\t"    "%14s\t"    "%14s\t"
            "%14s\t"    "%14s\t"    "%14s\t"    "%14s\t"    "%14s\t"    "\n",
            "bfrsurp", "surplus", "desired", "prvSwep", "bfrSwep",
            "count",   "cBirths", "cDeaths", "sBirths", "sDeaths");
}

// Print the AllocationStats for the given free list. If the second argument
// to the call is a non-null string, it is printed in the first column;
// otherwise, if the argument is null (the default), then the size of the
// (free list) block is printed in the first column.
template <class Chunk_t>
void FreeList<Chunk_t>::print_on(outputStream* st, const char* c) const {
  if (c != NULL) {
    st->print("%16s", c);
  } else {
    st->print(SIZE_FORMAT_W(16), size());
  }
}

template class FreeList<Metablock>;
template class FreeList<Metachunk>;
#if INCLUDE_ALL_GCS
template class FreeList<FreeChunk>;
template class FreeList<G1CodeRootChunk>;
#endif // INCLUDE_ALL_GCS
