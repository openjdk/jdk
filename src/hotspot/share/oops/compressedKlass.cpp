/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

int CompressedKlassPointers::_narrow_klass_pointer_bits = -1;
int CompressedKlassPointers::_max_shift = -1;

address CompressedKlassPointers::_base = (address)-1;
int CompressedKlassPointers::_shift = -1;
address CompressedKlassPointers::_klass_range_start = nullptr;
address CompressedKlassPointers::_klass_range_end = nullptr;
narrowKlass CompressedKlassPointers::_lowest_valid_narrow_klass_id = (narrowKlass)-1;
narrowKlass CompressedKlassPointers::_highest_valid_narrow_klass_id = (narrowKlass)-1;
size_t CompressedKlassPointers::_protection_zone_size = 0;

#ifdef _LP64

size_t CompressedKlassPointers::max_klass_range_size() {
  // We disallow klass range sizes larger than 4GB even if the encoding
  // range would allow for a larger Klass range (e.g. Base=zero, shift=3 -> 32GB).
  // That is because many CPU-specific compiler decodings do not want the
  // shifted narrow Klass to spill over into the third quadrant of the 64-bit target
  // address, e.g. to use a 16-bit move for a simplified base addition.
  return MIN2(4 * G, max_encoding_range_size());
}

void CompressedKlassPointers::pre_initialize() {
  if (UseCompactObjectHeaders) {
    _narrow_klass_pointer_bits = narrow_klass_pointer_bits_coh;
    _max_shift = max_shift_coh;
  } else {
    _narrow_klass_pointer_bits = narrow_klass_pointer_bits_noncoh;
    _max_shift = max_shift_noncoh;
  }
}

#ifdef ASSERT
void CompressedKlassPointers::sanity_check_after_initialization() {
  // In expectation of an assert, prepare condensed info to be printed with the assert.
  char tmp[256];
  os::snprintf(tmp, sizeof(tmp), "klass range: " RANGE2FMT ","
      " base " PTR_FORMAT ", shift %d, lowest/highest valid narrowKlass %u/%u",
      RANGE2FMTARGS(_klass_range_start, _klass_range_end),
      p2i(_base), _shift, _lowest_valid_narrow_klass_id, _highest_valid_narrow_klass_id);
#define ASSERT_HERE(cond) assert(cond, " (%s)", tmp);
#define ASSERT_HERE_2(cond, msg) assert(cond, msg " (%s)", tmp);

  // All values must be inited
  ASSERT_HERE(_max_shift != -1);
  ASSERT_HERE(_klass_range_start != (address)-1);
  ASSERT_HERE(_klass_range_end != (address)-1);
  ASSERT_HERE(_lowest_valid_narrow_klass_id != (narrowKlass)-1);
  ASSERT_HERE(_base != (address)-1);
  ASSERT_HERE(_shift != -1);

  const size_t klass_align = klass_alignment_in_bytes();

  // must be aligned enough hold 64-bit data
  ASSERT_HERE(is_aligned(klass_align, sizeof(uint64_t)));

  // should be smaller than the minimum metaspace chunk size (soft requirement)
  ASSERT_HERE(klass_align <= K);

  ASSERT_HERE(_klass_range_end > _klass_range_start);

  // Check that Klass range is fully engulfed in the encoding range
  const address encoding_start = _base;
  const address encoding_end = (address)(p2u(_base) + (uintptr_t)nth_bit(narrow_klass_pointer_bits() + _shift));
  ASSERT_HERE_2(_klass_range_start >= _base && _klass_range_end <= encoding_end,
                "Resulting encoding range does not fully cover the class range");

  // Check that Klass range is aligned to Klass alignment. Note that this should never be
  // an issue since the Klass range is handed in by either CDS- or Metaspace-initialization, and
  // it should be the result of an mmap operation that operates on page sizes. So as long as
  // the Klass alignment is <= page size, we are fine.
  ASSERT_HERE_2(is_aligned(_klass_range_start, klass_align) &&
                is_aligned(_klass_range_end, klass_align),
                "Klass range must start and end at a properly aligned address");

  // Check _lowest_valid_narrow_klass_id and _highest_valid_narrow_klass_id
  ASSERT_HERE_2(_lowest_valid_narrow_klass_id > 0, "Null is not a valid narrowKlass");
  ASSERT_HERE(_highest_valid_narrow_klass_id > _lowest_valid_narrow_klass_id);

  Klass* const k1 = decode_not_null_without_asserts(_lowest_valid_narrow_klass_id, _base, _shift);
  if (encoding_start == _klass_range_start) {
    ASSERT_HERE_2((address)k1 == _klass_range_start + klass_align, "Not lowest");
  } else {
    ASSERT_HERE_2((address)k1 == _klass_range_start, "Not lowest");
  }
  narrowKlass nk1 = encode_not_null_without_asserts(k1, _base, _shift);
  ASSERT_HERE_2(nk1 == _lowest_valid_narrow_klass_id, "not reversible");

  Klass* const k2 = decode_not_null_without_asserts(_highest_valid_narrow_klass_id, _base, _shift);
  ASSERT_HERE((address)k2 == _klass_range_end - klass_align);
  narrowKlass nk2 = encode_not_null_without_asserts(k2, _base, _shift);
  ASSERT_HERE_2(nk2 == _highest_valid_narrow_klass_id, "not reversible");

#ifdef AARCH64
  // On aarch64, we never expect a shift value > 0 in standard (non-coh) mode
  ASSERT_HERE_2(UseCompactObjectHeaders || _shift == 0, "Shift > 0 in non-coh mode?");
#endif
#undef ASSERT_HERE
#undef ASSERT_HERE_2
}
#endif // ASSERT

// Helper function: given current Klass Range, Base and Shift, calculate the lowest and highest values
// of narrowKlass we can expect.
void CompressedKlassPointers::calc_lowest_highest_narrow_klass_id() {
  address lowest_possible_klass_location = _klass_range_start;

  // A Klass will never be placed at the Encoding range start, since that would translate to a narrowKlass=0, which
  // is disallowed. If the encoding range starts at the klass range start, both Metaspace and CDS establish an
  // mprotected zone for this reason (see establish_protection_zone).
  if (lowest_possible_klass_location == _base) {
    lowest_possible_klass_location += klass_alignment_in_bytes();
  }
  _lowest_valid_narrow_klass_id = (narrowKlass) ((uintptr_t)(lowest_possible_klass_location - _base) >> _shift);

  address highest_possible_klass_location = _klass_range_end - klass_alignment_in_bytes();
  _highest_valid_narrow_klass_id = (narrowKlass) ((uintptr_t)(highest_possible_klass_location - _base) >> _shift);
}

// Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
// set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
// archived heap objects.
void CompressedKlassPointers::initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift) {
  if (len > max_klass_range_size()) {
    stringStream ss;
    ss.print("Class space size and CDS archive size combined (%zu) "
             "exceed the maximum possible size (%zu)",
             len, max_klass_range_size());
    vm_exit_during_initialization(ss.base());
  }

  // Remember Klass range:
  _klass_range_start = addr;
  _klass_range_end = addr + len;

  _base = requested_base;
  _shift = requested_shift;

  calc_lowest_highest_narrow_klass_id();

  // This has already been checked for SharedBaseAddress and if this fails, it's a bug in the allocation code.
  if (!set_klass_decode_mode()) {
    fatal("base=" PTR_FORMAT " given with shift %d, cannot be used to encode class pointers",
          p2i(_base), _shift);
  }

  DEBUG_ONLY(sanity_check_after_initialization();)
}

char* CompressedKlassPointers::reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr) {
  alignment = MAX2(Metaspace::reserve_alignment(), alignment);
  return os::attempt_reserve_memory_between((char*)from, (char*)to, size, alignment, aslr);
}

char* CompressedKlassPointers::reserve_address_space_below_4G(size_t size, bool aslr) {
  return reserve_address_space_X(0, nth_bit(32), size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_unscaled_encoding(size_t size, bool aslr) {
  const size_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
  return reserve_address_space_X(0, unscaled_max, size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_zerobased_encoding(size_t size, bool aslr) {
  const size_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
  const size_t zerobased_max = nth_bit(narrow_klass_pointer_bits() + max_shift());
  return reserve_address_space_X(unscaled_max, zerobased_max, size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_16bit_move(size_t size, bool aslr) {
  return reserve_address_space_X(nth_bit(32), nth_bit(48), size, nth_bit(32), aslr);
}

void CompressedKlassPointers::initialize(address addr, size_t len) {

  if (len > max_klass_range_size()) {
    stringStream ss;
    ss.print("Class space size (%zu) exceeds the maximum possible size (%zu)",
              len, max_klass_range_size());
    vm_exit_during_initialization(ss.base());
  }

  // Remember the Klass range:
  _klass_range_start = addr;
  _klass_range_end = addr + len;

  // Calculate Base and Shift:

  if (UseCompactObjectHeaders) {

    // In compact object header mode, with 22-bit narrowKlass, we don't attempt for
    // zero-based mode. Instead, we set the base to the start of the klass range and
    // then try for the smallest shift possible that still covers the whole range.
    // The reason is that we want to avoid, if possible, shifts larger than
    // a cacheline size.
    _base = addr;

    const int log_cacheline = exact_log2(DEFAULT_CACHE_LINE_SIZE);
    int s = max_shift();
    while (s > log_cacheline && ((size_t)nth_bit(narrow_klass_pointer_bits() + s - 1) > len)) {
      s--;
    }
    _shift = s;

  } else {

    // Traditional (non-compact) header mode
    const uintptr_t unscaled_max = nth_bit(narrow_klass_pointer_bits());
    const uintptr_t zerobased_max = nth_bit(narrow_klass_pointer_bits() + max_shift());

#ifdef AARCH64
    // Aarch64 avoids zero-base shifted mode (_base=0 _shift>0), instead prefers
    // non-zero-based mode with a zero shift.
    _shift = 0;
    address const end = addr + len;
    _base = (end <= (address)unscaled_max) ? nullptr : addr;
#else
    // We try, in order of preference:
    // -unscaled    (base=0 shift=0)
    // -zero-based  (base=0 shift>0)
    // -nonzero-base (base>0 shift=0)
    // Note that base>0 shift>0 should never be needed, since the klass range will
    // never exceed 4GB.
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
#endif // AARCH64
  }

  calc_lowest_highest_narrow_klass_id();

  // Initialize klass decode mode and check compability with decode instructions
  if (!set_klass_decode_mode()) {

    // Give fatal error if this is a specified address
    if (CompressedClassSpaceBaseAddress == (size_t)_base) {
      vm_exit_during_initialization(
            err_msg("CompressedClassSpaceBaseAddress=" PTR_FORMAT " given with shift %d, cannot be used to encode class pointers",
                    CompressedClassSpaceBaseAddress, _shift));
    } else {
      // If this fails, it's a bug in the allocation code.
      fatal("CompressedClassSpaceBaseAddress=" PTR_FORMAT " given with shift %d, cannot be used to encode class pointers",
            p2i(_base), _shift);
    }
  }
#ifdef ASSERT
  sanity_check_after_initialization();
#endif
}

void CompressedKlassPointers::print_mode(outputStream* st) {
  st->print_cr("UseCompressedClassPointers %d, UseCompactObjectHeaders %d",
               UseCompressedClassPointers, UseCompactObjectHeaders);
  if (UseCompressedClassPointers) {
    st->print_cr("Narrow klass pointer bits %d, Max shift %d",
                 _narrow_klass_pointer_bits, _max_shift);
    st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d",
                  p2i(base()), shift());
    st->print_cr("Encoding Range: " RANGE2FMT, RANGE2FMTARGS(_base, encoding_range_end()));
    st->print_cr("Klass Range:    " RANGE2FMT, RANGE2FMTARGS(_klass_range_start, _klass_range_end));
    st->print_cr("Klass ID Range:  [%u - %u) (%u)", _lowest_valid_narrow_klass_id, _highest_valid_narrow_klass_id + 1,
                 _highest_valid_narrow_klass_id + 1 - _lowest_valid_narrow_klass_id);
    if (_protection_zone_size > 0) {
      st->print_cr("Protection zone: " RANGEFMT, RANGEFMTARGS(_base, _protection_zone_size));
    } else {
      st->print_cr("No protection zone.");
    }
  } else {
    st->print_cr("UseCompressedClassPointers off");
  }
}

// On AIX, we cannot mprotect archive space or class space since they are reserved with SystemV shm.
static constexpr bool can_mprotect_archive_space = NOT_AIX(true) AIX_ONLY(false);

// Protect a zone a the start of the encoding range
void CompressedKlassPointers::establish_protection_zone(address addr, size_t size) {
  assert(_protection_zone_size == 0, "just once");
  assert(addr == base(), "Protection zone not at start of encoding range?");
  assert(size > 0 && is_aligned(size, os::vm_page_size()), "Protection zone not page sized");
  const bool rc = can_mprotect_archive_space && os::protect_memory((char*)addr, size, os::MEM_PROT_NONE, false);
  log_info(metaspace)("%s Narrow Klass Protection zone " RANGEFMT,
      (rc ? "Established" : "FAILED to establish "),
      RANGEFMTARGS(addr, size));
  if (!rc) {
    // If we fail to establish the protection zone, we fill it with a clear pattern to make it
    // stick out in register values (0x50 aka 'P', repeated)
    os::commit_memory((char*)addr, size, false);
    memset(addr, 'P', size);
  }
  _protection_zone_size = size;
}

bool CompressedKlassPointers::is_in_protection_zone(address addr) {
  return _protection_zone_size > 0 ?
      (addr >= base() && addr < base() + _protection_zone_size) : false;
}

#endif // _LP64
