/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_MARKWORD_HPP
#define SHARE_OOPS_MARKWORD_HPP

#include "cppstdlib/type_traits.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/globals.hpp"
#include "utilities/vmEnums.hpp"

// The markWord describes the header of an object.
//
// Bit-format of an object header (most significant first, big endian layout below):
//
//  32 bits:
//  --------
//             hash:25 ------------>| age:4  self-fwd:1  lock:2 (normal object)
//
//  64 bits:
//  --------
//  unused:22 hash:31 -->| valhalla:4  age:4  self-fwd:1  lock:2 (normal object)
//
//  64 bits (with compact headers):
//  -------------------------------
//  klass:22  hash:31 -->| valhalla:4  age:4  self-fwd:1  lock:2 (normal object)
//
//  - hash contains the identity hash value: largest value is
//    31 bits, see os::random().  Also, 64-bit vm's require
//    a hash value no bigger than 32 bits because they will not
//    properly generate a mask larger than that: see library_call.cpp
//
//  - the two lock bits are used to describe three states: locked/unlocked and monitor.
//
//    [ptr             | 00]  locked             ptr points to real header on stack (stack-locking in use)
//    [header          | 00]  locked             locked regular object header (fast-locking in use)
//    [header          | 01]  unlocked           regular object header
//    [ptr             | 10]  monitor            inflated lock (header is swapped out, UseObjectMonitorTable == false)
//    [header          | 10]  monitor            inflated lock (UseObjectMonitorTable == true)
//    [ptr             | 11]  marked             used to mark an object
//
//  VALHALLA EXTENSIONS:
//
//  N.B.: 32 bit mode is not supported, this section assumes 64 bit systems.
//
//  Project Valhalla uses markWord bits to denote the following oops (listed least to most significant):
//  * inline types: have alternative bytecode behavior, e.g. can not be locked
//  * flat arrays: load/decode of klass layout helper is expensive for aaload
//  * "null free" arrays: load/decode of klass layout helper again for aaload
//  * inline type: "larval state": mutable state, but only during object init, observable
//      by only by a single thread (generally do not mutate markWord)
//
//  Inline types cannot be locked, monitored or inflating.
//
//  Note the position of 'self-fwd' is not by accident. When forwarding an
//  object to a new heap position, HeapWord alignment guarantees the lower
//  bits, including 'self-fwd' are 0. "is_self_forwarded()" will be correctly
//  set to false. Otherwise encode_pointer_as_mark() may have 'self-fwd' set.

class BasicLock;
class ObjectMonitor;
class JavaThread;
class outputStream;

class markWord {
 private:
  uintptr_t _value;

 public:
  explicit markWord(uintptr_t value) : _value(value) {}

  markWord() = default;         // Doesn't initialize _value.

  // It is critical for performance that this class be trivially
  // destructable, copyable, and assignable.
  ~markWord() = default;
  markWord(const markWord&) = default;
  markWord& operator=(const markWord&) = default;

  static markWord from_pointer(void* ptr) {
    return markWord((uintptr_t)ptr);
  }
  void* to_pointer() const {
    return (void*)_value;
  }

  bool operator==(const markWord& other) const {
    return _value == other._value;
  }
  bool operator!=(const markWord& other) const {
    return !operator==(other);
  }

  // Conversion
  uintptr_t value() const { return _value; }

  // Constants, in least significant bit order
  static const int lock_bits                      = 2;
  static const int self_fwd_bits                  = 1;
  // instance state
  static const int age_bits                       = 4;
  // prototype header bits (fast path instead of klass layout_helper)
  static const int inline_type_bits               = 1;
  static const int null_free_array_bits           = LP64_ONLY(1) NOT_LP64(0);
  static const int flat_array_bits                = LP64_ONLY(1) NOT_LP64(0);
  static const int larval_bits                    = 1;
  static const int max_hash_bits                  = BitsPerWord - age_bits - lock_bits - inline_type_bits - larval_bits - flat_array_bits - null_free_array_bits - self_fwd_bits;
  static const int hash_bits                      = max_hash_bits > 31 ? 31 : max_hash_bits;

  static const int lock_shift                     = 0;
  static const int self_fwd_shift                 = lock_shift + lock_bits;
  static const int age_shift                      = self_fwd_shift + self_fwd_bits;
  static const int inline_type_shift              = age_shift + age_bits;
  static const int null_free_array_shift          = inline_type_shift + inline_type_bits;
  static const int flat_array_shift               = null_free_array_shift + null_free_array_bits;
  static const int larval_shift                   = flat_array_shift + flat_array_bits;
  static const int hash_shift                     = larval_shift + larval_bits;

  static const uintptr_t lock_mask                = right_n_bits(lock_bits);
  static const uintptr_t lock_mask_in_place       = lock_mask << lock_shift;
  static const uintptr_t self_fwd_mask            = right_n_bits(self_fwd_bits);
  static const uintptr_t self_fwd_mask_in_place   = self_fwd_mask << self_fwd_shift;
  static const uintptr_t inline_type_bit_in_place = right_n_bits(inline_type_bits) << inline_type_shift;
  static const uintptr_t inline_type_mask_in_place = inline_type_bit_in_place + lock_mask;
  static const uintptr_t null_free_array_mask     = right_n_bits(null_free_array_bits);
  static const uintptr_t null_free_array_mask_in_place = (null_free_array_mask << null_free_array_shift) | lock_mask_in_place;
  static const uintptr_t null_free_array_bit_in_place  = (right_n_bits(null_free_array_bits) << null_free_array_shift);
  static const uintptr_t flat_array_mask          = right_n_bits(flat_array_bits);
  static const uintptr_t flat_array_mask_in_place = (flat_array_mask << flat_array_shift) | null_free_array_mask_in_place | lock_mask_in_place;
  static const uintptr_t flat_array_bit_in_place  = right_n_bits(flat_array_bits) << flat_array_shift;
  static const uintptr_t age_mask                 = right_n_bits(age_bits);
  static const uintptr_t age_mask_in_place        = age_mask << age_shift;

  static const uintptr_t larval_mask              = right_n_bits(larval_bits);
  static const uintptr_t larval_mask_in_place     = (larval_mask << larval_shift) | inline_type_mask_in_place;
  static const uintptr_t larval_bit_in_place      = right_n_bits(larval_bits) << larval_shift;

  static const uintptr_t hash_mask                = right_n_bits(hash_bits);
  static const uintptr_t hash_mask_in_place       = hash_mask << hash_shift;

#ifdef _LP64
  // Used only with compact headers:
  // We store the (narrow) Klass* in the bits 43 to 64.

  // These are for bit-precise extraction of the narrow Klass* from the 64-bit Markword
  static constexpr int klass_offset_in_bytes      = 4;
  static constexpr int klass_shift                = hash_shift + hash_bits;
  static constexpr int klass_shift_at_offset      = klass_shift - klass_offset_in_bytes * BitsPerByte;
  static constexpr int klass_bits                 = 22;
  static constexpr uintptr_t klass_mask           = right_n_bits(klass_bits);
  static constexpr uintptr_t klass_mask_in_place  = klass_mask << klass_shift;
#endif


  static const uintptr_t locked_value             = 0;
  static const uintptr_t unlocked_value           = 1;
  static const uintptr_t monitor_value            = 2;
  static const uintptr_t marked_value             = 3;

  static const uintptr_t inline_type_pattern      = inline_type_bit_in_place | unlocked_value;
  static const uintptr_t null_free_array_pattern  = null_free_array_bit_in_place | unlocked_value;
  static const uintptr_t null_free_flat_array_pattern = flat_array_bit_in_place | null_free_array_pattern;
  static const uintptr_t nullable_flat_array_pattern = flat_array_bit_in_place | unlocked_value;

  static const uintptr_t larval_pattern           = larval_bit_in_place | inline_type_pattern;

  static const uintptr_t no_hash                  = 0 ;  // no hash value assigned
  static const uintptr_t no_hash_in_place         = (uintptr_t)no_hash << hash_shift;
  static const uintptr_t no_lock_in_place         = unlocked_value;

  static const uint max_age                       = age_mask;

  // Creates a markWord with all bits set to zero.
  static markWord zero() { return markWord(uintptr_t(0)); }

  bool is_inline_type() const {
    return (mask_bits(value(), inline_type_mask_in_place) == inline_type_pattern);
  }

  // lock accessors (note that these assume lock_shift == 0)
  bool is_locked()   const {
    return (mask_bits(value(), lock_mask_in_place) != unlocked_value);
  }
  bool is_unlocked() const {
    return (mask_bits(value(), lock_mask_in_place) == unlocked_value);
  }
  bool is_marked()   const {
    return (mask_bits(value(), lock_mask_in_place) == marked_value);
  }

  // is unlocked and not an inline type (which cannot be involved in locking, displacement or inflation)
  // i.e. test both lock bits and the inline type bit together
  bool is_neutral()  const {  // Not locked, or marked - a "clean" neutral state
    return (mask_bits(value(), inline_type_mask_in_place) == unlocked_value);
  }

  bool is_forwarded() const {
    // Returns true for normal forwarded (0b011) and self-forwarded (0b1xx).
    return mask_bits(value(), lock_mask_in_place | self_fwd_mask_in_place) >= static_cast<intptr_t>(marked_value);
  }

  // Should this header be preserved during GC?
  bool must_be_preserved() const {
    return (!is_unlocked() || !has_no_hash() || is_larval_state());
  }

  // WARNING: The following routines are used EXCLUSIVELY by
  // synchronization functions. They are not really gc safe.
  // They must get updated if markWord layout get changed.
  markWord set_unlocked() const {
    return markWord(value() | unlocked_value);
  }

  bool is_fast_locked() const {
    return (value() & lock_mask_in_place) == locked_value;
  }
  markWord set_fast_locked() const {
    // Clear the lock_mask_in_place bits to set locked_value:
    return markWord(value() & ~lock_mask_in_place);
  }

  bool has_monitor() const {
    return ((value() & lock_mask_in_place) == monitor_value);
  }
  markWord set_has_monitor() const {
    return markWord((value() & ~lock_mask_in_place) | monitor_value);
  }
  ObjectMonitor* monitor() const {
    assert(has_monitor(), "check");
    assert(!UseObjectMonitorTable, "Locking with OM table does not use markWord for monitors");
    // Use xor instead of &~ to provide one extra tag-bit check.
    return (ObjectMonitor*) (value() ^ monitor_value);
  }

  static markWord encode(ObjectMonitor* monitor) {
    assert(!UseObjectMonitorTable, "Locking with OM table does not use markWord for monitors");
    uintptr_t tmp = (uintptr_t) monitor;
    return markWord(tmp | monitor_value);
  }

  bool has_displaced_mark_helper() const {
    intptr_t lockbits = value() & lock_mask_in_place;
    return !UseObjectMonitorTable && lockbits == monitor_value;
  }
  markWord displaced_mark_helper() const;
  void set_displaced_mark_helper(markWord m) const;

  // used to encode pointers during GC
  markWord clear_lock_bits() const { return markWord(value() & ~lock_mask_in_place); }

  // age operations
  markWord set_marked()   { return markWord((value() & ~lock_mask_in_place) | marked_value); }
  markWord set_unmarked() { return markWord((value() & ~lock_mask_in_place) | unlocked_value); }

  uint     age()           const { return (uint) mask_bits(value() >> age_shift, age_mask); }
  markWord set_age(uint v) const {
    assert((v & ~age_mask) == 0, "shouldn't overflow age field");
    return markWord((value() & ~age_mask_in_place) | ((v & age_mask) << age_shift));
  }
  markWord incr_age()      const { return age() == max_age ? markWord(_value) : set_age(age() + 1); }

  // hash operations
  intptr_t hash() const {
    return mask_bits(value() >> hash_shift, hash_mask);
  }

  bool has_no_hash() const {
    return hash() == no_hash;
  }

  // private buffered value operations
  markWord enter_larval_state() const {
    return markWord(value() | larval_bit_in_place);
  }
  markWord exit_larval_state() const {
    return markWord(value() & ~larval_bit_in_place);
  }
  bool is_larval_state() const {
    return (mask_bits(value(), larval_mask_in_place) == larval_pattern);
  }

  bool is_flat_array() const {
#ifdef _LP64 // 64 bit encodings only
    return (mask_bits(value(), flat_array_mask_in_place) == null_free_flat_array_pattern)
           || (mask_bits(value(), flat_array_mask_in_place) == nullable_flat_array_pattern);
#else
    return false;
#endif
  }

  bool is_null_free_array() const {
#ifdef _LP64 // 64 bit encodings only
    return (mask_bits(value(), null_free_array_mask_in_place) == null_free_array_pattern);
#else
    return false;
#endif
  }

  markWord copy_set_hash(intptr_t hash) const {
    uintptr_t tmp = value() & (~hash_mask_in_place);
    tmp |= ((hash & hash_mask) << hash_shift);
    return markWord(tmp);
  }

  inline Klass* klass() const;
  inline Klass* klass_or_null() const;
  inline Klass* klass_without_asserts() const;
  inline narrowKlass narrow_klass() const;
  inline markWord set_narrow_klass(narrowKlass narrow_klass) const;

  // Prototype mark for initialization
  static markWord prototype() {
    return markWord( no_hash_in_place | no_lock_in_place );
  }

  static markWord inline_type_prototype() {
    return markWord(inline_type_pattern);
  }

#ifdef _LP64 // 64 bit encodings only
  static markWord flat_array_prototype(bool null_free);

  static markWord null_free_array_prototype() {
    return markWord(null_free_array_pattern);
  }
#endif

  // Debugging
  void print_on(outputStream* st, bool print_monitor_info = true) const;

  // Prepare address of oop for placement into mark
  inline static markWord encode_pointer_as_mark(void* p) { return from_pointer(p).set_marked(); }

  inline void* decode_pointer() const {
    return (void*) (clear_lock_bits().value());
  }

  inline bool is_self_forwarded() const {
    return mask_bits(value(), self_fwd_mask_in_place) != 0;
  }

  inline markWord set_self_forwarded() const {
    return markWord(value() | self_fwd_mask_in_place);
  }

  inline markWord unset_self_forwarded() const {
    return markWord(value() & ~self_fwd_mask_in_place);
  }

  inline oop forwardee() const {
    return cast_to_oop(decode_pointer());
  }
};

// Support atomic operations.
template<>
struct PrimitiveConversions::Translate<markWord> : public std::true_type {
  typedef markWord Value;
  typedef uintptr_t Decayed;

  static Decayed decay(const Value& x) { return x.value(); }
  static Value recover(Decayed x) { return Value(x); }
};

#endif // SHARE_OOPS_MARKWORD_HPP
