/*
 * Copyright 2001-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

CompactibleSpace* DefNewGeneration::first_compaction_space() const {
  return eden();
}

HeapWord* DefNewGeneration::allocate(size_t word_size,
                                     bool is_tlab) {
  // This is the slow-path allocation for the DefNewGeneration.
  // Most allocations are fast-path in compiled code.
  // We try to allocate from the eden.  If that works, we are happy.
  // Note that since DefNewGeneration supports lock-free allocation, we
  // have to use it here, as well.
  HeapWord* result = eden()->par_allocate(word_size);
  if (result != NULL) {
    return result;
  }
  do {
    HeapWord* old_limit = eden()->soft_end();
    if (old_limit < eden()->end()) {
      // Tell the next generation we reached a limit.
      HeapWord* new_limit =
        next_gen()->allocation_limit_reached(eden(), eden()->top(), word_size);
      if (new_limit != NULL) {
        Atomic::cmpxchg_ptr(new_limit, eden()->soft_end_addr(), old_limit);
      } else {
        assert(eden()->soft_end() == eden()->end(),
               "invalid state after allocation_limit_reached returned null");
      }
    } else {
      // The allocation failed and the soft limit is equal to the hard limit,
      // there are no reasons to do an attempt to allocate
      assert(old_limit == eden()->end(), "sanity check");
      break;
    }
    // Try to allocate until succeeded or the soft limit can't be adjusted
    result = eden()->par_allocate(word_size);
  } while (result == NULL);

  // If the eden is full and the last collection bailed out, we are running
  // out of heap space, and we try to allocate the from-space, too.
  // allocate_from_space can't be inlined because that would introduce a
  // circular dependency at compile time.
  if (result == NULL) {
    result = allocate_from_space(word_size);
  }
  return result;
}

HeapWord* DefNewGeneration::par_allocate(size_t word_size,
                                         bool is_tlab) {
  return eden()->par_allocate(word_size);
}

void DefNewGeneration::gc_prologue(bool full) {
  // Ensure that _end and _soft_end are the same in eden space.
  eden()->set_soft_end(eden()->end());
}

size_t DefNewGeneration::tlab_capacity() const {
  return eden()->capacity();
}

size_t DefNewGeneration::unsafe_max_tlab_alloc() const {
  return unsafe_max_alloc_nogc();
}
