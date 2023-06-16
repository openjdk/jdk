/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahReservedSubSpace.hpp"

#include "runtime/os.hpp"
#include "utilities/align.hpp"


void SubSpace::split(size_t bytes, SubSpace& left, SubSpace& right) const {
  assert(is_aligned(bytes, BytesPerWord), "Sanity");
  size_t words = bytes / BytesPerWord;
  if (is_null()) {
    left = right = SubSpace();
  } else {
    SubSpace tmp = *this;
    clamp(words, (size_t)0, word_size());
    if (bytes == 0) {
      left = SubSpace();
      right = tmp;
    } else if (bytes == byte_size()) {
      left = tmp;
      right = SubSpace();
    } else {
      left = SubSpace(MemRegion(start(), words), _special, _pagesize);
      right = SubSpace(MemRegion(start() + words, word_size() - words), _special, _pagesize);
    }
  }
}

SubSpace SubSpace::first_part(size_t bytes) const {
  SubSpace right, left;
  split(bytes, right, left);
  return right;
}

SubSpace SubSpace::aligned_start(size_t alignment) const {
  SubSpace ss;
  if (!is_null()) {
    char* p = align_up((char*)start(), alignment);
    if (p > (char*)end()) {
      return SubSpace();
    }
    SubSpace dummy;
    split(p - (char*)start(), dummy, ss);
  }
  return ss;
}

#ifdef ASSERT
void SubSpace::verify() const {
  if (!is_null()) {
    assert(pagesize() > 0, "unknown pagesize");
    assert(special() || is_aligned(start(), pagesize()), "must be special or page-aligned");
  }
}
void SubSpace::verify_not_null() const {
  assert(!is_empty() && !is_null(), "Empty");
  verify();
}
#endif
