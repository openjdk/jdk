/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOPREF_INLINE_HPP
#define SHARE_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOPREF_INLINE_HPP

#include "jfr/leakprofiler/utilities/unifiedOopRef.hpp"

#include "oops/access.inline.hpp"
#include "utilities/debug.hpp"

template <typename T>
inline T UnifiedOopRef::addr() const {
  return reinterpret_cast<T>(UnifiedOopRef::addr<uintptr_t>());
}

// Visual Studio 2019 and earlier have a problem with reinterpret_cast
// when the new type is the same as the expression type. For example:
//  reinterpret_cast<int>(1);
//  "error C2440: 'reinterpret_cast': cannot convert from 'int' to 'int'"
// this specialization provides a workaround.
template<>
inline uintptr_t UnifiedOopRef::addr<uintptr_t>() const {
  return (_value & ~tag_mask) LP64_ONLY(>> 1);
}

inline bool UnifiedOopRef::is_narrow() const {
  return (_value & narrow_tag) != 0;
}

inline bool UnifiedOopRef::is_native() const {
  return (_value & native_tag) != 0;
}

inline bool UnifiedOopRef::is_raw() const {
  return (_value & raw_tag) != 0;
}

inline bool UnifiedOopRef::is_null() const {
  return _value == 0;
}

template <typename T>
inline UnifiedOopRef create_with_tag(T ref, uintptr_t tag) {
  assert(ref != nullptr, "invariant");

  uintptr_t value = reinterpret_cast<uintptr_t>(ref);

#ifdef _LP64
  // tag_mask is 3 bits. When ref is a narrowOop* we only have 2 alignment
  // bits, because of the 4 byte alignment of compressed oops addresses.
  // Shift up to make way for one more bit.
  assert((value & (1ull << 63)) == 0, "Unexpected high-order bit");
  value <<= 1;
#endif
  assert((value & UnifiedOopRef::tag_mask) == 0, "Unexpected low-order bits");

  UnifiedOopRef result = { value | tag };
  assert(result.addr<T>() == ref, "sanity");
  return result;
}

inline UnifiedOopRef UnifiedOopRef::encode_in_native(const narrowOop* ref) {
  NOT_LP64(ShouldNotReachHere());
  return create_with_tag(ref, native_tag | narrow_tag);
}

inline UnifiedOopRef UnifiedOopRef::encode_in_native(const oop* ref) {
  return create_with_tag(ref, native_tag);
}

inline UnifiedOopRef UnifiedOopRef::encode_as_raw(const narrowOop* ref) {
  NOT_LP64(ShouldNotReachHere());
  return create_with_tag(ref, raw_tag | narrow_tag);
}

inline UnifiedOopRef UnifiedOopRef::encode_as_raw(const oop* ref) {
  return create_with_tag(ref, raw_tag);
}

inline UnifiedOopRef UnifiedOopRef::encode_in_heap(const narrowOop* ref) {
  NOT_LP64(ShouldNotReachHere());
  return create_with_tag(ref, narrow_tag);
}

inline UnifiedOopRef UnifiedOopRef::encode_in_heap(const oop* ref) {
  return create_with_tag(ref, 0);
}

inline UnifiedOopRef UnifiedOopRef::encode_null() {
  UnifiedOopRef result = { 0 };
  return result;
}

inline oop UnifiedOopRef::dereference() const {
  if (is_raw()) {
    if (is_narrow()) {
      NOT_LP64(ShouldNotReachHere());
      return RawAccess<>::oop_load(addr<narrowOop*>());
    } else {
      return *addr<oop*>();
    }
  } else if (is_native()) {
    if (is_narrow()) {
      NOT_LP64(ShouldNotReachHere());
      return NativeAccess<AS_NO_KEEPALIVE>::oop_load(addr<narrowOop*>());
    } else {
      return NativeAccess<AS_NO_KEEPALIVE>::oop_load(addr<oop*>());
    }
  } else {
    if (is_narrow()) {
      NOT_LP64(ShouldNotReachHere());
      return HeapAccess<AS_NO_KEEPALIVE>::oop_load(addr<narrowOop*>());
    } else {
      return HeapAccess<AS_NO_KEEPALIVE>::oop_load(addr<oop*>());
    }
  }
}

#endif // SHARE_JFR_LEAKPROFILER_UTILITIES_UNIFIEDOOPREF_INLINE_HPP
