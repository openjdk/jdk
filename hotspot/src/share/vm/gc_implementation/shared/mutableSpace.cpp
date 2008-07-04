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

# include "incls/_precompiled.incl"
# include "incls/_mutableSpace.cpp.incl"

void MutableSpace::initialize(MemRegion mr, bool clear_space) {
  HeapWord* bottom = mr.start();
  HeapWord* end    = mr.end();

  assert(Universe::on_page_boundary(bottom) && Universe::on_page_boundary(end),
         "invalid space boundaries");
  set_bottom(bottom);
  set_end(end);

  if (clear_space) clear();
}

void MutableSpace::clear() {
  set_top(bottom());
  if (ZapUnusedHeapArea) mangle_unused_area();
}

// This version requires locking. */
HeapWord* MutableSpace::allocate(size_t size) {
  assert(Heap_lock->owned_by_self() ||
         (SafepointSynchronize::is_at_safepoint() &&
          Thread::current()->is_VM_thread()),
         "not locked");
  HeapWord* obj = top();
  if (pointer_delta(end(), obj) >= size) {
    HeapWord* new_top = obj + size;
    set_top(new_top);
    assert(is_object_aligned((intptr_t)obj) && is_object_aligned((intptr_t)new_top),
           "checking alignment");
    return obj;
  } else {
    return NULL;
  }
}

// This version is lock-free.
HeapWord* MutableSpace::cas_allocate(size_t size) {
  do {
    HeapWord* obj = top();
    if (pointer_delta(end(), obj) >= size) {
      HeapWord* new_top = obj + size;
      HeapWord* result = (HeapWord*)Atomic::cmpxchg_ptr(new_top, top_addr(), obj);
      // result can be one of two:
      //  the old top value: the exchange succeeded
      //  otherwise: the new value of the top is returned.
      if (result != obj) {
        continue; // another thread beat us to the allocation, try again
      }
      assert(is_object_aligned((intptr_t)obj) && is_object_aligned((intptr_t)new_top),
             "checking alignment");
      return obj;
    } else {
      return NULL;
    }
  } while (true);
}

// Try to deallocate previous allocation. Returns true upon success.
bool MutableSpace::cas_deallocate(HeapWord *obj, size_t size) {
  HeapWord* expected_top = obj + size;
  return (HeapWord*)Atomic::cmpxchg_ptr(obj, top_addr(), expected_top) == expected_top;
}

void MutableSpace::oop_iterate(OopClosure* cl) {
  HeapWord* obj_addr = bottom();
  HeapWord* t = top();
  // Could call objects iterate, but this is easier.
  while (obj_addr < t) {
    obj_addr += oop(obj_addr)->oop_iterate(cl);
  }
}

void MutableSpace::object_iterate(ObjectClosure* cl) {
  HeapWord* p = bottom();
  while (p < top()) {
    cl->do_object(oop(p));
    p += oop(p)->size();
  }
}

void MutableSpace::print_short() const { print_short_on(tty); }
void MutableSpace::print_short_on( outputStream* st) const {
  st->print(" space " SIZE_FORMAT "K, %d%% used", capacity_in_bytes() / K,
            (int) ((double) used_in_bytes() * 100 / capacity_in_bytes()));
}

void MutableSpace::print() const { print_on(tty); }
void MutableSpace::print_on(outputStream* st) const {
  MutableSpace::print_short_on(st);
  st->print_cr(" [" INTPTR_FORMAT "," INTPTR_FORMAT "," INTPTR_FORMAT ")",
                 bottom(), top(), end());
}

void MutableSpace::verify(bool allow_dirty) {
  HeapWord* p = bottom();
  HeapWord* t = top();
  HeapWord* prev_p = NULL;
  while (p < t) {
    oop(p)->verify();
    prev_p = p;
    p += oop(p)->size();
  }
  guarantee(p == top(), "end of last object must match end of space");
}
