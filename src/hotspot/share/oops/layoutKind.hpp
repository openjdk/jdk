/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_LAYOUTKIND_HPP
#define SHARE_OOPS_LAYOUTKIND_HPP

#include "memory/allStatic.hpp"
#include "utilities/enumIterator.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/sizes.hpp"

class outputStream;

// LayoutKind is an enum used to indicate which flat layout has been used for a given flat value field.
//
// Each layout has its own properties and its own access protocol that is detailed below.
//
// NULL_FREE_NON_ATOMIC_FLAT : This layout is the simplest form of flattening. Any field embedded inside the flat field
//             can be accessed independently. The field is null-restricted, meaning putfield must perform a
//             null-check before performing a field update.
//
// NULL_FREE_ATOMIC_FLAT : This flat layout is designed for atomic updates, with size and alignment that make use of
//             atomic instructions possible. All accesses, reads and writes, must be performed atomically.
//             The field is null-restricted, meaning putfield must perform a null-check before performing a
//             field update.
//
// NULLABLE_ATOMIC_FLAT : This is the flat layout designed for JEP 401. It is designed for atomic updates,
//             with size and alignment that make use of atomic instructions possible. All accesses, reads and
//             writes, must be performed atomically. The layout includes a null marker which indicates if the
//             field's value must be considered as null or not. The null marker is a byte, with the value zero
//             meaning the field's value is null, and a non-zero value meaning the field's value is not null.
//             A getfield must check the value of the null marker before returning a value. If the null marker
//             is zero, getfield  must return the null reference, otherwise it returns the field's value read
//             from the receiver. When a putfield writes a non-null value to such field, the update, including
//             the field's value and the null marker, must be performed in a single atomic operation. If the
//             source of the value is a heap allocated instance of the field's class, it is allowed to set the
//             null marker to non-zero in the heap allocated instance before copying the value to the receiver
//             (the BUFFERED layout used in heap allocated values guarantees that the space for the null marker
//             is included, but has no meaning for the heap allocated instance which is always non-null, and that
//             the whole payload is correctly aligned for atomic operations). When a putfield writes null to such
//             field, the null marker must be set to zero. However, if the field contains oops, those oops must be
//             cleared too in order to prevent memory leaks. In order to simplify such operation, value classes
//             supporting a NULLABLE_ATOMIC_FLAT layout have a pre-allocated reset value instance, filled with
//             zeros, which can be used to simply overwrite the whole flat field and reset everything (oops and
//             null marker). The reset value instance is needed because the VM needs an instance guaranteed to
//             always be filled with zeros, and the default value could have its null marker set to non-zero if
//             it is used as a source to update a NULLABLE_ATOMIC_FLAT field.
//
// NULLABLE_NON_ATOMIC_FLAT: This is a special layout, only used for strict final non-static fields. Because strict
//             final non-static fields cannot be updated after the call to the super constructor, there's no
//             concurrency issue on those fields, so they can be flattened even if they are nullable. During the
//             construction of the instance, the uninitializedThis reference cannot escape before the call to
//             the super's constructor, so no concurrent reads are possible when the field is initialized. After
//             the call to the super's constructor, no update is possible because the field is strict and final,
//             so no write possible during a read. This field has a null marker similar to the one of the
//             NULLABLE_ATOMIC_FLAT layout. However, there's no requirement to read the null marker and the
//             rest of the value atomically. If the null marker indicates a non-null value, the fields of the
//             field's value can be read independently. Same rules for a putfield, no atomicity requirement,
//             as long as all fields and the null marker are up to date at the end of the putfield.

enum class LayoutKind : uint32_t {
  NULL_FREE_NON_ATOMIC_FLAT = 0,    // flat, null-free (no null marker), no guarantee of atomic updates
  NULL_FREE_ATOMIC_FLAT     = 1,    // flat, null-free, size compatible with atomic updates, alignment requirement is equal to the size
  NULLABLE_ATOMIC_FLAT      = 2,    // flat, include a null marker, plus same size/alignment properties as ATOMIC layout
  NULLABLE_NON_ATOMIC_FLAT  = 3,    // flat, include a null marker, non-atomic, only used for strict final non-static fields
};

ENUMERATOR_RANGE(LayoutKind, LayoutKind::NULL_FREE_NON_ATOMIC_FLAT, LayoutKind::NULLABLE_NON_ATOMIC_FLAT)

class LayoutKindHelper : AllStatic {
 public:
  static bool is_valid_underlying_value(uint32_t value) {
    return (uint32_t)EnumRange<LayoutKind>().first() <= value && value <= (uint32_t)EnumRange<LayoutKind>().last();
  }

  static bool is_atomic_flat(LayoutKind lk) {
    return lk == LayoutKind::NULL_FREE_ATOMIC_FLAT ||
           lk == LayoutKind::NULLABLE_ATOMIC_FLAT;
  }
  static bool is_nullable_flat(LayoutKind lk) {
    return lk == LayoutKind::NULLABLE_ATOMIC_FLAT ||
           lk == LayoutKind::NULLABLE_NON_ATOMIC_FLAT;
  }
  static const char* layout_kind_as_string(LayoutKind lk);
};

// This class puts an abstraction around flat layouts and provides convenience
// methods that can be used instead of explicit calls to LayoutKindHelper.
class FlatLayout {
  LayoutKind _layout_kind;

public:
  FlatLayout(LayoutKind layout_kind) : _layout_kind(layout_kind) {}

  LayoutKind layout_kind() const { return _layout_kind; }

  bool is_nullable() const       { return LayoutKindHelper::is_nullable_flat(_layout_kind); }
  bool is_atomic() const         { return LayoutKindHelper::is_atomic_flat(_layout_kind); }

  static ByteSize layout_kind_offset() { return in_ByteSize(offset_of(FlatLayout, _layout_kind)); }

  bool operator==(const FlatLayout& other) const {
    return _layout_kind == other._layout_kind;
  }

  const char* as_string() const;
  void print_on(outputStream* st) const NOT_DEBUG_RETURN;
};

// This class is used for places where storing a FlatLayout is optional
// and its presence depends on an external "has flat layout" discriminator.
//
// This helps keep information about flatness in one place, instead of
// duplicating it together with the FlatLayout. If this duplication isn't
// problematic, then OptionalFlatLayout is probably a more natural class to
// use.
//
// Note that the implementation of the class is subtle in that it supports
// a tri-state:
//
// 1) A FlatLayout is present and the slot is considered initialized
// 2) No FlatLayout is present but the slot is still considered initialized
// 3) The slot is not considered initialized
//
// All these three cases are currently used in the code. Care must be taken
// to ensure that the external discriminator (has_flat_layout) is properly
// initialized and agrees with the active union member:
//
// has_flat_layout == true => _flat_layout is active
// has_flat_layout == false => _initialized_non_flat is active
//
// Here again is a reason to use the safer OptionalFlatLayout class. It handles
// the active member and it has built-in initialization asserts. Raw
// FlatLayoutSlot users typically don't gain much from differentiating between
// the two states of _initialized_non_flat.
class FlatLayoutSlot {
  // Note that the union makes sure that a FlatLayout object is only
  // created (in the C++ sense) when we have a proper FlatLayout.
  // Otherwise the bool object is created.
  union {
    // This is only set when a flat layout is present.
    FlatLayout _flat_layout;

    // This bool is set when the flat layout is not present.
    //
    // Its value is used to determine if this slot should be considered
    // initialized
    bool _initialized_non_flat;
  };

public:
  FlatLayoutSlot(FlatLayout flat_layout)
    : _flat_layout(flat_layout) {}

  explicit FlatLayoutSlot(bool initialized_non_flat = false)
    : _initialized_non_flat(initialized_non_flat) {}

  // Get the FlatLayout. The external has_flat_layout discriminator is used to
  // catch when code tries to fetch layout without having a flat layout.
  FlatLayout get_if(bool has_flat_layout) const {
    precond(has_flat_layout);
    return _flat_layout;
  }

  bool is_initialized(bool has_flat_layout) const {
    return has_flat_layout || _initialized_non_flat;
  }
};

// This class optionally holds a FlatLayout.
//
// It is similar to FlatLayoutSlot and has support for the same tri-state. The
// difference is that this class has its own "has flat layout" (_is_flat)
// discriminator. This also means that it can assert that the current instance
// has been initialized.
class OptionalFlatLayout {
  bool           _is_flat;
  FlatLayoutSlot _flat_layout;

  OptionalFlatLayout(bool is_flat, FlatLayoutSlot flat_layout)
    : _is_flat(is_flat),
      _flat_layout(flat_layout) {}

public:
  // Default constructor creates an "uninitialized" state.
  OptionalFlatLayout()
    : OptionalFlatLayout(false /* is_flat */, FlatLayoutSlot(false /* initialized */)) {}

  OptionalFlatLayout(FlatLayout flat_layout)
    : OptionalFlatLayout(true /* is_flat */, FlatLayoutSlot(flat_layout)) {}

  static OptionalFlatLayout flat(FlatLayout flat_layout) {
    return OptionalFlatLayout(flat_layout);
  }

  static OptionalFlatLayout non_flat() {
    return OptionalFlatLayout(false /* is_flat */, FlatLayoutSlot(true /* initialized */));
  }

  static OptionalFlatLayout uninitialized() {
    return OptionalFlatLayout();
  }

  bool is_initialized() const {
    return _flat_layout.is_initialized(_is_flat);
  }

  bool is_flat() const {
    precond(is_initialized());
    return _is_flat;
  }

  // Get the FlatLayout, assert if it isn't present.
  FlatLayout get() const {
    return _flat_layout.get_if(_is_flat);
  }

  bool is_nullable_flat() const {
    return is_flat() && get().is_nullable();
  }

  bool is_atomic_flat() const {
    return is_flat() && get().is_atomic();
  }

  bool operator==(const OptionalFlatLayout& other) const {
    precond(is_initialized());
    precond(other.is_initialized());

    if (_is_flat) {
      if (other._is_flat) {
        return get() == other.get();
      } else {
        return false;
      }
    } else {
      // No other data to check for non-flat field layouts
      return !other._is_flat;
    }
  }
};

// Class to help encoding and decoding optional FlatLayouts to and from an int.
//
//  0 -> non-flat layout
// >0 =  flat layout with (value - 1) corresponding to the LayoutKind
class FlatLayoutEncoding {
public:
  static bool is_valid_layout_value(int layout_value) {
    if (layout_value == 0) {
      // Means non-flat field layout
      return true;
    } else {
      // The flat values are shifted one step in order to make place for the non-flat values
      const uint32_t layout_kind_value = (uint32_t)layout_value - 1;
      return LayoutKindHelper::is_valid_underlying_value(layout_kind_value);
    }
  }

  static jint encode(OptionalFlatLayout ofl) {
    if (!ofl.is_flat()) {
      // Unsafe.nonFlatValue == 0
      return 0;
    }
    return static_cast<jint>(ofl.get().layout_kind()) + 1;
  }

  static jint encode_flat(FlatLayout flat_layout) {
    return encode(flat_layout);
  }

  static jint encode_non_flat() {
    return encode(OptionalFlatLayout::non_flat());
  }

  static OptionalFlatLayout decode(jint layout_value) {
    assert(is_valid_layout_value(layout_value),
           "invalid encoded layout value %d", layout_value);

    if (layout_value == 0) {
      return OptionalFlatLayout::non_flat();
    } else {
      // The flat values are shifted one step in order to make place for the non-flat values
      const uint32_t layout_kind_value = (uint32_t)layout_value - 1;
      return FlatLayout(static_cast<LayoutKind>(layout_kind_value));
    }
  }
};

#endif // SHARE_OOPS_LAYOUTKIND_HPP
