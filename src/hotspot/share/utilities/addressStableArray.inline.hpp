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

#ifndef SHARE_UTILITIES_ADDRESSSTABLEARRAY_INLINE_HPP
#define SHARE_UTILITIES_ADDRESSSTABLEARRAY_INLINE_HPP

#include "runtime/os.hpp"
#include "utilities/addressStableArray.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/freeList.inline.hpp"
#include "utilities/globalDefinitions.hpp"

template <class T>
void AddressStableArray<T>::enlarge_capacity(uintx min_needed_capacity) {

  assert(_capacity < _max_capacity, "cannot enlarge capacity");
  const uintx new_capacity =
      clamp((uintx)(_capacity * 1.25), min_needed_capacity, _max_capacity);

  const size_t committed_bytes = bytes_needed_page_aligned(_capacity);
  const size_t new_committed_bytes = bytes_needed_page_aligned(new_capacity);

  // Since we always set _capacity to either _max_capacity or the limit of what is
  // committed, this should hold always true:
  assert(new_committed_bytes > committed_bytes, "_capacity not at commit boundary");

  os::commit_memory_or_exit(_rs.base() + committed_bytes,
                        new_committed_bytes - committed_bytes,
                        false, "");
  _capacity = MIN2(capacity_of(new_committed_bytes), _max_capacity);

  DEBUG_ONLY(verify();)
}

#ifdef ASSERT
template <class T>
void AddressStableArray<T>::verify() const {
  assert(_rs.is_reserved(), "no space");
  assert(_elements != NULL, "elements null");
  assert(_capacity <= _max_capacity, "Sanity");
  assert(_max_capacity <= capacity_of(_rs.size()), "Space too small?");
  assert(_used <= _capacity, "Sanity");
}
#endif // ASSERT

template <class T>
void AddressStableArray<T>::print_on(outputStream* st) const {
  st->print("elem size: " SIZE_FORMAT ", "
      "[" PTR_FORMAT "-" PTR_FORMAT "), res/comm " SIZE_FORMAT "/" SIZE_FORMAT ", "
      "used/capacity/max: " UINTX_FORMAT "/" UINTX_FORMAT "/" UINTX_FORMAT
      ,
      sizeof(T),
      p2i(_rs.base()), p2i(_rs.base() + _rs.size()),
      _rs.size(), bytes_needed_page_aligned(_capacity),
      _used, _capacity, _max_capacity
      );
}

// AddressStableHeap = AddressStableArray + freelist

template <class T>
struct VerifyFreeListClosure : public FreeList<T>::Closure {
  const AddressStableHeap<T>* _container;
  bool do_it(const T* p) override {
    assert(_container->contains(p), "kukuck");
    return true;
  }
};

#ifdef ASSERT
template <class T>
void AddressStableHeap<T>::verify(bool paranoid) const {
  _array.verify();
  _freelist.verify(paranoid);
  // verify that all elements are part of the array
  VerifyFreeListClosure<T> verifier;
  verifier._container = this;
  _freelist.iterate(verifier);
}
#endif // ASSERT

template <class T>
void AddressStableHeap<T>::print_on(outputStream* st) const {
  _array.print_on(st);
  st->print(", freelist: ");
  _freelist.print_on(st, false);
}


#endif // SHARE_UTILITIES_ADDRESSSTABLEARRAY_INLINE_HPP
