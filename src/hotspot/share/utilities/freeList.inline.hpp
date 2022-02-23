/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_FREELIST_INLINE_HPP
#define SHARE_UTILITIES_FREELIST_INLINE_HPP

#include "utilities/freeList.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

class outputStream;

template <class T>
uintx FreeList<T>::iterate(FreeList<T>::Closure& closure) const {
  uintx processed = 0;
  bool go_on = true;
  for (const T* p = head(); go_on && p != NULL; p = Tptr_at(p)) {
    processed ++;
    go_on = closure.do_it(p);
  }
  return processed;
}

#ifdef ASSERT
template <class T>
void FreeList<T>::verify(bool paranoid) const {

  STATIC_ASSERT(sizeof(T) >= sizeof(T*));

  quick_verify();

  // Simple verify list and list length. Also call verify_closure if it is set.
  if (_counting) {
    uintx counted = 0;
    for (const T* p = head(); p != NULL; p = Tptr_at(p)) {
      assert(counted < _count, "too many elements (more than " UINTX_FORMAT ")?", _count);
      counted ++;
    }
    assert(!_counting || _count == counted, "count is off");
  }

  // In paranoid mode, or if we have know we have fewer than n elements,
  // we check for duplicates. Slow (O(n^2)/2).
  if (paranoid || (_counting && _count < 10)) {
    for (const T* p = head(); p != NULL; p = Tptr_at(p)) {
      for (const T* p2 = Tptr_at(p); p2 != NULL; p2 = Tptr_at(p2)) {
        assert(p2 != p, "duplicate in list");
      }
    }
  }
}
#endif // ASSERT

template <class T>
void FreeList<T>::print_on(outputStream* st, bool print_elems) const {
  if (_counting) {
    st->print(UINTX_FORMAT " elems (peak: " UINTX_FORMAT " elems)", _count, _peak_count);
  } else {
    // No count, do the best we can
    if (_head == NULL) {
      st->print("0 elems");
    } else if (_head == _tail) {
      st->print("1 elems");
    } else {
      st->print(">1 elems");
    }
  }
  if (print_elems) {
    st->cr();
    for (const T* p = head(); p != NULL; p = Tptr_at(p)) {
      st->print(PTR_FORMAT "->", p2i(p));
    }
    st->cr();
  }
}

#endif // SHARE_UTILITIES_FREELIST_INLINE_HPP
