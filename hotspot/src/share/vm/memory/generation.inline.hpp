/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

bool OneContigSpaceCardGeneration::is_in(const void* p) const {
  return the_space()->is_in(p);
}


WaterMark OneContigSpaceCardGeneration::top_mark() {
  return the_space()->top_mark();
}

CompactibleSpace*
OneContigSpaceCardGeneration::first_compaction_space() const {
  return the_space();
}

HeapWord* OneContigSpaceCardGeneration::allocate(size_t word_size,
                                                 bool is_tlab) {
  assert(!is_tlab, "OneContigSpaceCardGeneration does not support TLAB allocation");
  return the_space()->allocate(word_size);
}

HeapWord* OneContigSpaceCardGeneration::par_allocate(size_t word_size,
                                                     bool is_tlab) {
  assert(!is_tlab, "OneContigSpaceCardGeneration does not support TLAB allocation");
  return the_space()->par_allocate(word_size);
}

WaterMark OneContigSpaceCardGeneration::bottom_mark() {
  return the_space()->bottom_mark();
}

size_t OneContigSpaceCardGeneration::block_size(const HeapWord* addr) const {
  if (addr < the_space()->top()) return oop(addr)->size();
  else {
    assert(addr == the_space()->top(), "non-block head arg to block_size");
    return the_space()->_end - the_space()->top();
  }
}

bool OneContigSpaceCardGeneration::block_is_obj(const HeapWord* addr) const {
  return addr < the_space()->top();
}
