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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "oops/klass.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

int CompressedKlassPointers::_tiny_cp = -1;
int CompressedKlassPointers::_narrow_klass_pointer_bits = -1;
int CompressedKlassPointers::_max_shift = -1;
#ifdef ASSERT
address CompressedKlassPointers::_klass_range_start = (address)-1;
address CompressedKlassPointers::_klass_range_end = (address)-1;
narrowKlass CompressedKlassPointers::_lowest_valid_narrow_klass_id = (narrowKlass)-1;
narrowKlass CompressedKlassPointers::_highest_valid_narrow_klass_id = (narrowKlass)-1;
#endif

address CompressedKlassPointers::_base = (address)-1;
int CompressedKlassPointers::_shift = -1;
size_t CompressedKlassPointers::_range = (size_t)-1;

// The maximum allowed length of the Klass range (the address range engulfing
// CDS + class space) must not exceed 32-bit.
// There is a theoretical limit of: must not exceed the size of a fully-shifted
// narrow Klass pointer, which would be 32 + 3 = 35 bits in legacy mode;
// however, keeping this size below 32-bit allows us to use decoding techniques
// like 16-bit moves into the third quadrant on some architectures, and keeps
// the code less complex. 32-bit have always been enough for CDS+class space.
static constexpr size_t max_klass_range_size = 4 * G;

#ifdef _LP64

void CompressedKlassPointers::pre_initialize() {
  if (UseTinyClassPointers) {
    _tiny_cp = 1;
    _narrow_klass_pointer_bits = narrow_klass_pointer_bits_tinycp;
    _max_shift = max_shift_tinycp;
  } else {
    _tiny_cp = 0;
    _narrow_klass_pointer_bits = narrow_klass_pointer_bits_legacy;
    _max_shift = max_shift_legacy;
  }
}

#ifdef ASSERT
void CompressedKlassPointers::sanity_check_after_initialization() {
  // In expectation of an assert, prepare condensed info to be printed with the assert.
  char tmp[256];
  os::snprintf(tmp, sizeof(tmp), PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " %d " SIZE_FORMAT " %u %u",
      p2i(_klass_range_start), p2i(_klass_range_end), p2i(_base), _shift, _range,
      _lowest_valid_narrow_klass_id, _highest_valid_narrow_klass_id);
#define ASSERT_HERE(cond) assert(cond, " (%s)", tmp);
#define ASSERT_HERE_2(cond, msg) assert(cond, msg " (%s)", tmp);

  // There is no technical reason preventing us from using other klass pointer bit lengths,
  // but it should be a deliberate choice
  ASSERT_HERE(_narrow_klass_pointer_bits == 32 || _narrow_klass_pointer_bits == 22);

  // All values must be inited
  ASSERT_HERE(_max_shift != -1);
  ASSERT_HERE(_klass_range_start != (address)-1);
  ASSERT_HERE(_klass_range_end != (address)-1);
  ASSERT_HERE(_lowest_valid_narrow_klass_id != (narrowKlass)-1);
  ASSERT_HERE(_base != (address)-1);
  ASSERT_HERE(_shift != -1);
  ASSERT_HERE(_range != (size_t)-1);

  const size_t klab = klass_alignment_in_bytes();
  ASSERT_HERE(klab >= sizeof(uint64_t) && klab <= K);

  // Check that Klass range is fully engulfed in the encoding range
  ASSERT_HERE(_klass_range_end > _klass_range_start);

  const address encoding_end = _base + nth_bit(narrow_klass_pointer_bits() + _shift);
  ASSERT_HERE_2(_klass_range_start >= _base && _klass_range_end <= encoding_end,
                "Resulting encoding range does not fully cover the class range");

  // Check that Klass range is aligned to Klass alignment. That should never be an issue since we mmap the
  // relevant regions and klass alignment - tied to smallest metachunk size of 1K - will always be smaller
  // than smallest page size of 4K.
  ASSERT_HERE_2(is_aligned(_klass_range_start, klab) && is_aligned(_klass_range_end, klab),
                "Klass range must start at a properly aligned address");

  // Check that lowest and highest possible narrowKlass values make sense
  ASSERT_HERE_2(_lowest_valid_narrow_klass_id > 0, "Null is not a valid narrowKlass");
  ASSERT_HERE(_highest_valid_narrow_klass_id > _lowest_valid_narrow_klass_id);

  Klass* k1 = decode_raw(_lowest_valid_narrow_klass_id, _base, _shift);
  ASSERT_HERE_2((address)k1 == _klass_range_start + klab, "Not lowest");
  narrowKlass nk1 = encode_raw(k1, _base, _shift);
  ASSERT_HERE_2(nk1 == _lowest_valid_narrow_klass_id, "not reversible");

  Klass* k2 = decode_raw(_highest_valid_narrow_klass_id, _base, _shift);
  // _highest_valid_narrow_klass_id must be decoded to the highest theoretically possible
  // valid Klass* position in range, if we assume minimal Klass size
  ASSERT_HERE((address)k2 < _klass_range_end);
  ASSERT_HERE_2(align_up(((address)k2 + sizeof(Klass)), klab) >= _klass_range_end, "Not highest");
  narrowKlass nk2 = encode_raw(k2, _base, _shift);
  ASSERT_HERE_2(nk2 == _highest_valid_narrow_klass_id, "not reversible");

#ifdef AARCH64
  // On aarch64, we never expect a shift value > 0 in legacy mode
  ASSERT_HERE_2(tiny_classpointer_mode() || _shift == 0, "Shift > 0 in legacy mode?");
#endif
#undef ASSERT_HERE
#undef ASSERT_HERE_2
}

void CompressedKlassPointers::calc_lowest_highest_narrow_klass_id() {
  // Given a Klass range, calculate lowest and highest narrowKlass.
  const size_t klab = klass_alignment_in_bytes();
  // Note that 0 is not a valid narrowKlass, and Metaspace prevents us for that reason from allocating at
  // the very start of class space. So the very first valid Klass position is start-of-range + klab.
  _lowest_valid_narrow_klass_id =
      (narrowKlass) (((uintptr_t)(_klass_range_start - _base) + klab) >> _shift);
  address highest_possible_klass = align_down(_klass_range_end - sizeof(Klass), klab);
  _highest_valid_narrow_klass_id = (narrowKlass) ((uintptr_t)(highest_possible_klass - _base) >> _shift);
}
#endif // ASSERT

// Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
// set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
// archived heap objects.
void CompressedKlassPointers::initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift) {
  address const end = addr + len;

  if (len > max_klass_range_size) {
    // Class space size is limited to 3G. This can theoretically happen if the CDS archive
    // is larger than 1G and class space size is set to the maximum possible 3G.
    vm_exit_during_initialization("Sum of CDS archive size and class space size exceed 4 GB");
  }

  const size_t encoding_range_size = nth_bit(narrow_klass_pointer_bits() + requested_shift);
  address encoding_range_end = requested_base + encoding_range_size;

  // Note: it would be technically valid for the encoding base to precede the start of the Klass range. But we only call
  // this function from CDS, and therefore know this to be true.
  assert(requested_base == addr, "Invalid requested base");

  _base = requested_base;
  _shift = requested_shift;
  _range = encoding_range_size;

#ifdef ASSERT
  _klass_range_start = addr;
  _klass_range_end = addr + len;
  calc_lowest_highest_narrow_klass_id();
  sanity_check_after_initialization();
#endif

  DEBUG_ONLY(sanity_check_after_initialization();)
}

char* CompressedKlassPointers::reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr) {
  alignment = MAX2(Metaspace::reserve_alignment(), alignment);
  return os::attempt_reserve_memory_between((char*)from, (char*)to, size, alignment, aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_unscaled_encoding(size_t size, bool aslr) {
  if (tiny_classpointer_mode()) {
    return nullptr;
  }
  const size_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
  return reserve_address_space_X(0, unscaled_max, size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_zerobased_encoding(size_t size, bool aslr) {
  if (tiny_classpointer_mode()) {
    return nullptr;
  }
  const size_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
  const size_t zerobased_max = nth_bit(narrow_klass_pointer_bits() + max_shift());
  return reserve_address_space_X(unscaled_max, zerobased_max, size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_16bit_move(size_t size, bool aslr) {
  return reserve_address_space_X(nth_bit(32), nth_bit(48), size, nth_bit(32), aslr);
}

void CompressedKlassPointers::initialize(address addr, size_t len) {

  if (len > max_klass_range_size) {
    // Class space size is limited to 3G. This can theoretically happen if the CDS archive
    // is larger than 1G and class space size is set to the maximum possible 3G.
    vm_exit_during_initialization("Sum of CDS archive size and class space size exceed 4 GB");
  }

  // Give CPU a shot at a specialized init sequence
#ifndef ZERO
  if (pd_initialize(addr, len)) {
    return;
  }
#endif

  if (tiny_classpointer_mode()) {

    // This handles the case that we - experimentally - reduce the number of
    // class pointer bits further, such that (shift + num bits) < 32.
    assert(len <= (size_t)nth_bit(narrow_klass_pointer_bits() + max_shift()),
           "klass range size exceeds encoding");

    // In tiny classpointer mode, we don't attempt for zero-based mode.
    // Instead, we set the base to the start of the klass range and then try
    // for the smallest shift possible that still covers the whole range.
    // The reason is that we want to avoid, if possible, shifts larger than
    // a cacheline size.
    _base = addr;
    _range = len;

    if (TinyClassPointerShift != 0) {
      _shift = TinyClassPointerShift;
    } else {
      constexpr int log_cacheline = 6;
      int s = max_shift();
      while (s > log_cacheline && ((size_t)nth_bit(narrow_klass_pointer_bits() + s - 1) > len)) {
        s--;
      }
      _shift = s;
    }

  } else {

    // In legacy mode, we try, in order of preference:
    // -unscaled    (base=0 shift=0)
    // -zero-based  (base=0 shift>0)
    // -nonzero-base (base>0 shift=0)
    // Note that base>0 shift>0 should never be needed, since the klass range will
    // never exceed 4GB.
    const uintptr_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
    const uintptr_t zerobased_max = nth_bit(narrow_klass_pointer_bits() + max_shift());

    address const end = addr + len;
    if (end <= (address)unscaled_max) {
      _base = nullptr;
      _shift = 0;
    } else {
      if (end <= (address)zerobased_max) {
        _base = nullptr;
        _shift = max_shift();
      } else {
        _base = addr;
        _shift = 0;
      }
    }
    _range = end - _base;

  }

#ifdef ASSERT
  _klass_range_start = addr;
  _klass_range_end = addr + len;
  calc_lowest_highest_narrow_klass_id();
  sanity_check_after_initialization();
#endif
}

void CompressedKlassPointers::print_mode(outputStream* st) {
  st->print_cr("UseCompressedClassPointers %d, UseTinyClassPointers %d, "
               "narrow klass pointer bits %d, max shift %d",
               UseCompressedClassPointers, UseTinyClassPointers,
               _narrow_klass_pointer_bits, _max_shift);
  if (_base == (address)-1) {
    st->print_cr("Narrow klass encoding not initialized");
    return;
  }
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d, "
               "Narrow klass range: " SIZE_FORMAT_X, p2i(base()), shift(),
               range());
#ifdef ASSERT
  st->print_cr("Klass range: [" PTR_FORMAT "," PTR_FORMAT ")",
               p2i(_klass_range_start), p2i(_klass_range_end));
  st->print_cr("Lowest valid nklass id: %u Highest valid nklass id: %u",
               _lowest_valid_narrow_klass_id, _highest_valid_narrow_klass_id);
#endif
}

#endif // _LP64
