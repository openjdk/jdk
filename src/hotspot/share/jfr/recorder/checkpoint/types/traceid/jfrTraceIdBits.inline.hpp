/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdBits.hpp"

#include "oops/method.hpp"
#include "runtime/atomic.hpp"
#include "utilities/macros.hpp"

#ifdef VM_LITTLE_ENDIAN
const int low_offset = 0;
const int meta_offset = low_offset + 1;
#else
const int low_offset = 7;
const int meta_offset = low_offset - 1;
#endif

inline uint8_t* low_addr(uint8_t* addr) {
  assert(addr != nullptr, "invariant");
  return addr + low_offset;
}

inline uint8_t* low_addr(traceid* addr) {
  return low_addr(reinterpret_cast<uint8_t*>(addr));
}

inline uint8_t* meta_addr(uint8_t* addr) {
  assert(addr != nullptr, "invariant");
  return addr + meta_offset;
}

inline uint8_t* meta_addr(traceid* addr) {
  return meta_addr(reinterpret_cast<uint8_t*>(addr));
}

template <typename T>
inline uint8_t* traceid_tag_byte(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return low_addr(ptr->trace_id_addr());
}

template <>
inline uint8_t* traceid_tag_byte<Method>(const Method* ptr) {
  assert(ptr != nullptr, "invariant");
  return ptr->trace_flags_addr();
}

template <typename T>
inline uint8_t* traceid_meta_byte(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return meta_addr(ptr->trace_id_addr());
}

template <>
inline uint8_t* traceid_meta_byte<Method>(const Method* ptr) {
  assert(ptr != nullptr, "invariant");
  return ptr->trace_flags_meta_addr();
}

inline uint8_t traceid_and(uint8_t bits, uint8_t current) {
  return bits & current;
}

inline uint8_t traceid_or(uint8_t bits, uint8_t current) {
  return bits | current;
}

inline uint8_t traceid_xor(uint8_t bits, uint8_t current) {
  return bits ^ current;
}

template <uint8_t op(uint8_t, uint8_t)>
inline void set_form(uint8_t bits, uint8_t* dest) {
  assert(dest != nullptr, "invariant");
  *dest = op(bits, *dest);
  OrderAccess::storestore();
}

template <uint8_t op(uint8_t, uint8_t)>
inline void set_cas_form(uint8_t bits, uint8_t volatile* dest) {
  assert(dest != nullptr, "invariant");
  do {
    const uint8_t current = *dest;
    const uint8_t new_value = op(bits, current);
    if (current == new_value || Atomic::cmpxchg(dest, current, new_value) == current) {
      return;
    }
  } while (true);
}

template <typename T>
inline void JfrTraceIdBits::cas(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  set_cas_form<traceid_or>(bits, traceid_tag_byte(ptr));
}

template <typename T>
inline traceid JfrTraceIdBits::load(const T* ptr) {
  assert(ptr != nullptr, "invariant");
  return ptr->trace_id();
}

inline void set(uint8_t bits, uint8_t* dest) {
  assert(dest != nullptr, "invariant");
  set_form<traceid_or>(bits, dest);
}

template <typename T>
inline void JfrTraceIdBits::store(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  // gcc12 warns "writing 1 byte into a region of size 0" when T == Klass.
  // The warning seems to be a false positive.  And there is no warning for
  // other types that use the same mechanisms.  The warning also sometimes
  // goes away with minor code perturbations, such as replacing function calls
  // with equivalent code directly inlined.
  PRAGMA_DIAG_PUSH
  PRAGMA_STRINGOP_OVERFLOW_IGNORED
  set(bits, traceid_tag_byte(ptr));
  PRAGMA_DIAG_POP
}

template <typename T>
inline void JfrTraceIdBits::meta_store(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  set(bits, traceid_meta_byte(ptr));
}

inline void set_mask(uint8_t mask, uint8_t* dest) {
  set_cas_form<traceid_and>(mask, dest);
}

template <typename T>
inline void JfrTraceIdBits::mask_store(uint8_t mask, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  set_mask(mask, traceid_tag_byte(ptr));
}

template <typename T>
inline void JfrTraceIdBits::meta_mask_store(uint8_t mask, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  set_mask(mask, traceid_meta_byte(ptr));
}

inline void clear_bits(uint8_t bits, uint8_t* dest) {
  set_form<traceid_xor>(bits, dest);
}

template <typename T>
inline void JfrTraceIdBits::clear(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  clear_bits(bits, traceid_tag_byte(ptr));
}

inline void clear_bits_cas(uint8_t bits, uint8_t* dest) {
  set_cas_form<traceid_xor>(bits, dest);
}

template <typename T>
inline void JfrTraceIdBits::clear_cas(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  clear_bits_cas(bits, traceid_tag_byte(ptr));
}

template <typename T>
inline void JfrTraceIdBits::meta_clear(uint8_t bits, const T* ptr) {
  assert(ptr != nullptr, "invariant");
  clear_bits(bits, traceid_meta_byte(ptr));
}

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBITS_INLINE_HPP
