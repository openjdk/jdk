/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

// This is a shared helper class used during phase 3 and 4 to move all the objects
// Dead regions in a Space are linked together to keep track of the live regions
// so that the live data can be traversed quickly without having to look at each
// object.

class LiveRange: public MemRegion {
public:
  LiveRange(HeapWord* bottom, HeapWord* top): MemRegion(bottom, top) {}

  void set_end(HeapWord* e) {
    assert(e >= start(), "should be a non-zero range");
    MemRegion::set_end(e);
  }
  void set_word_size(size_t ws) {
    assert(ws >= 0, "should be a non-zero range");
    MemRegion::set_word_size(ws);
  }

  LiveRange * next() { return (LiveRange *) end(); }

  void move_to(HeapWord* destination) {
    Copy::aligned_conjoint_words(start(), destination, word_size());
  }
};
