/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASS_INLINE_HPP
#define SHARE_OOPS_KLASS_INLINE_HPP

#include "oops/klass.hpp"

#include "classfile/classLoaderData.inline.hpp"
#include "oops/klassVtable.hpp"
#include "oops/markWord.hpp"
#include "utilities/rotate_bits.hpp"

// This loads and keeps the klass's loader alive.
inline oop Klass::klass_holder() const {
  return class_loader_data()->holder();
}

inline bool Klass::is_non_strong_hidden() const {
  return is_hidden() && class_loader_data()->has_class_mirror_holder();
}

// Iff the class loader (or mirror for non-strong hidden classes) is alive the
// Klass is considered alive. This is safe to call before the CLD is marked as
// unloading, and hence during concurrent class unloading.
// This returns false if the Klass is unloaded, or about to be unloaded because the holder of
// the CLD is no longer strongly reachable.
// The return value of this function may change from true to false after a safepoint. So the caller
// of this function must ensure that a safepoint doesn't happen while interpreting the return value.
inline bool Klass::is_loader_alive() const {
  return class_loader_data()->is_alive();
}

inline oop Klass::java_mirror() const {
  return _java_mirror.resolve();
}

inline oop Klass::java_mirror_no_keepalive() const {
  return _java_mirror.peek();
}

inline klassVtable Klass::vtable() const {
  return klassVtable(const_cast<Klass*>(this), start_of_vtable(), vtable_length() / vtableEntry::size());
}

inline oop Klass::class_loader() const {
  return class_loader_data()->class_loader();
}

inline vtableEntry* Klass::start_of_vtable() const {
  return (vtableEntry*) ((address)this + in_bytes(vtable_start_offset()));
}

inline ByteSize Klass::vtable_start_offset() {
  return in_ByteSize(InstanceKlass::header_size() * wordSize);
}

// subtype check: true if is_subclass_of, or if k is interface and receiver implements it
inline bool Klass::is_subtype_of(Klass* k) const {
  assert(secondary_supers() != nullptr, "must be");
  const juint off = k->super_check_offset();
  const juint secondary_offset = in_bytes(secondary_super_cache_offset());
  if (off == secondary_offset) {
    return search_secondary_supers(k);
  } else {
    Klass* sup = *(Klass**)( (address)this + off );
    return (sup == k);
  }
}

// Hashed search for secondary super k.
inline bool Klass::lookup_secondary_supers_table(Klass* k) const {
  uintx bitmap = _secondary_supers_bitmap;

  constexpr int highest_bit_number = SECONDARY_SUPERS_TABLE_SIZE - 1;
  uint8_t slot = k->_hash_slot;
  uintx shifted_bitmap = bitmap << (highest_bit_number - slot);

  precond((int)population_count(bitmap) <= secondary_supers()->length());

  // First check the bitmap to see if super_klass might be present. If
  // the bit is zero, we are certain that super_klass is not one of
  // the secondary supers.
  if (((shifted_bitmap >> highest_bit_number) & 1) == 0) {
    return false;
  }

  // Calculate the initial hash probe
  int index = population_count(shifted_bitmap) - 1;
  if (secondary_supers()->at(index) == k) {
    // Yes! It worked the first time.
    return true;
  }

  // Is there another entry to check? Consult the bitmap. If Bit 1,
  // the next bit to test, is zero, we are certain that super_klass is
  // not one of the secondary supers.
  bitmap = rotate_right(bitmap, slot);
  if ((bitmap & 2) == 0) {
    return false;
  }

  // Continue probing the hash table
  return fallback_search_secondary_supers(k, index, bitmap);
}

inline bool Klass::search_secondary_supers(Klass *k) const {
  // This is necessary because I am never in my own secondary_super list.
  if (this == k)
    return true;

  bool result = lookup_secondary_supers_table(k);

#ifndef PRODUCT
  if (VerifySecondarySupers) {
    bool linear_result = linear_search_secondary_supers(k);
    if (linear_result != result) {
      on_secondary_supers_verification_failure((Klass*)this, k, linear_result, result, "mismatch");
    }
  }
#endif // PRODUCT

  return result;
}

#endif // SHARE_OOPS_KLASS_INLINE_HPP
