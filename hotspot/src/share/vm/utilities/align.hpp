/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_ALIGN_HPP
#define SHARE_VM_UTILITIES_ALIGN_HPP

#include "utilities/globalDefinitions.hpp"

// Signed variants of alignment helpers.  There are two versions of each, a macro
// for use in places like enum definitions that require compile-time constant
// expressions and a function for all other places so as to get type checking.

// Using '(what) & ~align_mask(alignment)' to align 'what' down is broken when
// 'alignment' is an unsigned int and 'what' is a wider type. The & operation
// will widen the inverted mask, and not sign extend it, leading to a mask with
// zeros in the most significant bits. The use of align_mask_widened() solves
// this problem.
#define align_mask(alignment) ((alignment) - 1)
#define widen_to_type_of(what, type_carrier) (true ? (what) : (type_carrier))
#define align_mask_widened(alignment, type_carrier) widen_to_type_of(align_mask(alignment), (type_carrier))

#define align_down_(size, alignment) ((size) & ~align_mask_widened((alignment), (size)))

#define align_up_(size, alignment) (align_down_((size) + align_mask(alignment), (alignment)))

#define is_aligned_(size, alignment) (((size) & align_mask(alignment)) == 0)

// Temporary declaration until this file has been restructured.
template <typename T>
bool is_power_of_2_t(T x) {
  return (x != T(0)) && ((x & (x - 1)) == T(0));
}

// Helpers to align sizes and check for alignment

template <typename T, typename A>
inline T align_up(T size, A alignment) {
  assert(is_power_of_2_t(alignment), "must be a power of 2: " UINT64_FORMAT, (uint64_t)alignment);

  T ret = align_up_(size, alignment);
  assert(is_aligned_(ret, alignment), "must be aligned: " UINT64_FORMAT, (uint64_t)ret);

  return ret;
}

template <typename T, typename A>
inline T align_down(T size, A alignment) {
  assert(is_power_of_2_t(alignment), "must be a power of 2: " UINT64_FORMAT, (uint64_t)alignment);

  T ret = align_down_(size, alignment);
  assert(is_aligned_(ret, alignment), "must be aligned: " UINT64_FORMAT, (uint64_t)ret);

  return ret;
}

template <typename T, typename A>
inline bool is_aligned(T size, A alignment) {
  assert(is_power_of_2_t(alignment), "must be a power of 2: " UINT64_FORMAT, (uint64_t)alignment);

  return is_aligned_(size, alignment);
}

// Align down with a lower bound. If the aligning results in 0, return 'alignment'.
template <typename T, typename A>
inline T align_down_bounded(T size, A alignment) {
  A aligned_size = align_down(size, alignment);
  return aligned_size > 0 ? aligned_size : alignment;
}

// Helpers to align pointers and check for alignment.

template <typename T, typename A>
inline T* align_up(T* ptr, A alignment) {
  return (T*)align_up((uintptr_t)ptr, alignment);
}

template <typename T, typename A>
inline T* align_down(T* ptr, A alignment) {
  return (T*)align_down((uintptr_t)ptr, alignment);
}

template <typename T, typename A>
inline bool is_aligned(T* ptr, A alignment) {
  return is_aligned((uintptr_t)ptr, alignment);
}

// Align metaspace objects by rounding up to natural word boundary
template <typename T>
inline T align_metadata_size(T size) {
  return align_up(size, 1);
}

// Align objects in the Java Heap by rounding up their size, in HeapWord units.
template <typename T>
inline T align_object_size(T word_size) {
  return align_up(word_size, MinObjAlignment);
}

inline bool is_object_aligned(size_t word_size) {
  return is_aligned(word_size, MinObjAlignment);
}

inline bool is_object_aligned(const void* addr) {
  return is_aligned(addr, MinObjAlignmentInBytes);
}

// Pad out certain offsets to jlong alignment, in HeapWord units.
template <typename T>
inline T align_object_offset(T offset) {
  return align_up(offset, HeapWordsPerLong);
}

// Clamp an address to be within a specific page
// 1. If addr is on the page it is returned as is
// 2. If addr is above the page_address the start of the *next* page will be returned
// 3. Otherwise, if addr is below the page_address the start of the page will be returned
template <typename T>
inline T* clamp_address_in_page(T* addr, T* page_address, size_t page_size) {
  if (align_down(addr, page_size) == align_down(page_address, page_size)) {
    // address is in the specified page, just return it as is
    return addr;
  } else if (addr > page_address) {
    // address is above specified page, return start of next page
    return align_down(page_address, page_size) + page_size;
  } else {
    // address is below specified page, return start of page
    return align_down(page_address, page_size);
  }
}

#endif // SHARE_VM_UTILITIES_ALIGN_HPP
