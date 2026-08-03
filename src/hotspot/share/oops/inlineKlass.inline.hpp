/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_VM_OOPS_INLINEKLASS_INLINE_HPP
#define SHARE_VM_OOPS_INLINEKLASS_INLINE_HPP

#include "oops/inlineKlass.hpp"

#include "oops/valuePayload.inline.hpp"
#include "runtime/handles.hpp"
#include "utilities/debug.hpp"
#include "utilities/devirtualizer.inline.hpp"

inline bool InlineKlass::layout_has_null_marker(LayoutKind lk) const {
  assert(is_layout_supported(lk), "Must be");
  return LayoutKindHelper::is_nullable_flat(lk) ||
         (lk == LayoutKind::BUFFERED && supports_nullable_layouts());
}

inline bool InlineKlass::is_layout_supported(LayoutKind lk) const {
  switch(lk) {
    case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT:
      return has_null_free_non_atomic_layout();
      break;
    case LayoutKind::NULL_FREE_ATOMIC_FLAT:
      return has_null_free_atomic_layout();
      break;
    case LayoutKind::NULLABLE_ATOMIC_FLAT:
      return has_nullable_atomic_layout();
      break;
    case LayoutKind::NULLABLE_NON_ATOMIC_FLAT:
      return has_nullable_non_atomic_layout();
      break;
    case LayoutKind::BUFFERED:
      return true;
      break;
    default:
      ShouldNotReachHere();
  }
}

inline int InlineKlass::layout_size_in_bytes(LayoutKind kind) const {
  switch(kind) {
    case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT:
      assert(has_null_free_non_atomic_layout(), "Layout not available");
      return null_free_non_atomic_size_in_bytes();
      break;
    case LayoutKind::NULL_FREE_ATOMIC_FLAT:
      assert(has_null_free_atomic_layout(), "Layout not available");
      return null_free_atomic_size_in_bytes();
      break;
    case LayoutKind::NULLABLE_ATOMIC_FLAT:
      assert(has_nullable_atomic_layout(), "Layout not available");
      return nullable_atomic_size_in_bytes();
      break;
    case LayoutKind::NULLABLE_NON_ATOMIC_FLAT:
      assert(has_nullable_non_atomic_layout(), "Layout not available");
      return nullable_non_atomic_size_in_bytes();
      break;
    case LayoutKind::BUFFERED:
      return payload_size_in_bytes();
      break;
    default:
      ShouldNotReachHere();
  }
}

inline int InlineKlass::layout_alignment(LayoutKind kind) const {
  switch(kind) {
    case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT:
      assert(has_null_free_non_atomic_layout(), "Layout not available");
      return null_free_non_atomic_alignment();
      break;
    case LayoutKind::NULL_FREE_ATOMIC_FLAT:
      assert(has_null_free_atomic_layout(), "Layout not available");
      return null_free_atomic_size_in_bytes();
      break;
    case LayoutKind::NULLABLE_ATOMIC_FLAT:
      assert(has_nullable_atomic_layout(), "Layout not available");
      return nullable_atomic_size_in_bytes();
      break;
    case LayoutKind::NULLABLE_NON_ATOMIC_FLAT:
      assert(has_nullable_non_atomic_layout(), "Layout not available");
      return null_free_non_atomic_alignment();
    break;
    case LayoutKind::BUFFERED:
      return payload_alignment();
      break;
    default:
      ShouldNotReachHere();
  }
}

inline address InlineKlass::payload_addr(oop o) const {
  return cast_from_oop<address>(o) + payload_offset();
}

template <typename T, class OopClosureType>
void InlineKlass::oop_iterate_specialized(const address oop_addr, OopClosureType* closure) {
  OopMapBlock* map = start_of_nonstatic_oop_maps();
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();

  for (; map < end_map; map++) {
    T* p = (T*) (oop_addr + map->offset());
    T* const end = p + map->count();
    for (; p < end; ++p) {
      Devirtualizer::do_oop(closure, p);
    }
  }
}

template <typename T, class OopClosureType>
inline void InlineKlass::oop_iterate_specialized_bounded(const address oop_addr, OopClosureType* closure, uintptr_t lo, uintptr_t hi) {
  OopMapBlock* map = start_of_nonstatic_oop_maps();
  OopMapBlock* const end_map = map + nonstatic_oop_map_count();

  T* const l   = (T*) lo;
  T* const h   = (T*) hi;

  for (; map < end_map; map++) {
    T* p = (T*) (oop_addr + map->offset());
    T* end = p + map->count();
    if (p < l) {
      p = l;
    }
    if (end > h) {
      end = h;
    }
    for (; p < end; ++p) {
      Devirtualizer::do_oop(closure, p);
    }
  }
}

#endif // SHARE_VM_OOPS_INLINEKLASS_INLINE_HPP
