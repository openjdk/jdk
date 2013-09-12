/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/virtualspace.hpp"
#include "services/memTracker.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_bsd.inline.hpp"
#endif


// ReservedSpace

// Dummy constructor
ReservedSpace::ReservedSpace() : _base(NULL), _size(0), _noaccess_prefix(0),
    _alignment(0), _special(false), _executable(false) {
}

ReservedSpace::ReservedSpace(size_t size) {
  size_t page_size = os::page_size_for_region(size, size, 1);
  bool large_pages = page_size != (size_t)os::vm_page_size();
  // Don't force the alignment to be large page aligned,
  // since that will waste memory.
  size_t alignment = os::vm_allocation_granularity();
  initialize(size, alignment, large_pages, NULL, 0, false);
}

ReservedSpace::ReservedSpace(size_t size, size_t alignment,
                             bool large,
                             char* requested_address,
                             const size_t noaccess_prefix) {
  initialize(size+noaccess_prefix, alignment, large, requested_address,
             noaccess_prefix, false);
}

ReservedSpace::ReservedSpace(size_t size, size_t alignment,
                             bool large,
                             bool executable) {
  initialize(size, alignment, large, NULL, 0, executable);
}

// Helper method.
static bool failed_to_reserve_as_requested(char* base, char* requested_address,
                                           const size_t size, bool special)
{
  if (base == requested_address || requested_address == NULL)
    return false; // did not fail

  if (base != NULL) {
    // Different reserve address may be acceptable in other cases
    // but for compressed oops heap should be at requested address.
    assert(UseCompressedOops, "currently requested address used only for compressed oops");
    if (PrintCompressedOopsMode) {
      tty->cr();
      tty->print_cr("Reserved memory not at requested address: " PTR_FORMAT " vs " PTR_FORMAT, base, requested_address);
    }
    // OS ignored requested address. Try different address.
    if (special) {
      if (!os::release_memory_special(base, size)) {
        fatal("os::release_memory_special failed");
      }
    } else {
      if (!os::release_memory(base, size)) {
        fatal("os::release_memory failed");
      }
    }
  }
  return true;
}

void ReservedSpace::initialize(size_t size, size_t alignment, bool large,
                               char* requested_address,
                               const size_t noaccess_prefix,
                               bool executable) {
  const size_t granularity = os::vm_allocation_granularity();
  assert((size & (granularity - 1)) == 0,
         "size not aligned to os::vm_allocation_granularity()");
  assert((alignment & (granularity - 1)) == 0,
         "alignment not aligned to os::vm_allocation_granularity()");
  assert(alignment == 0 || is_power_of_2((intptr_t)alignment),
         "not a power of 2");

  alignment = MAX2(alignment, (size_t)os::vm_page_size());

  // Assert that if noaccess_prefix is used, it is the same as alignment.
  assert(noaccess_prefix == 0 ||
         noaccess_prefix == alignment, "noaccess prefix wrong");

  _base = NULL;
  _size = 0;
  _special = false;
  _executable = executable;
  _alignment = 0;
  _noaccess_prefix = 0;
  if (size == 0) {
    return;
  }

  // If OS doesn't support demand paging for large page memory, we need
  // to use reserve_memory_special() to reserve and pin the entire region.
  bool special = large && !os::can_commit_large_page_memory();
  char* base = NULL;

  if (requested_address != 0) {
    requested_address -= noaccess_prefix; // adjust requested address
    assert(requested_address != NULL, "huge noaccess prefix?");
  }

  if (special) {

    base = os::reserve_memory_special(size, alignment, requested_address, executable);

    if (base != NULL) {
      if (failed_to_reserve_as_requested(base, requested_address, size, true)) {
        // OS ignored requested address. Try different address.
        return;
      }
      // Check alignment constraints.
      assert((uintptr_t) base % alignment == 0,
             err_msg("Large pages returned a non-aligned address, base: "
                 PTR_FORMAT " alignment: " PTR_FORMAT,
                 base, (void*)(uintptr_t)alignment));
      _special = true;
    } else {
      // failed; try to reserve regular memory below
      if (UseLargePages && (!FLAG_IS_DEFAULT(UseLargePages) ||
                            !FLAG_IS_DEFAULT(LargePageSizeInBytes))) {
        if (PrintCompressedOopsMode) {
          tty->cr();
          tty->print_cr("Reserve regular memory without large pages.");
        }
      }
    }
  }

  if (base == NULL) {
    // Optimistically assume that the OSes returns an aligned base pointer.
    // When reserving a large address range, most OSes seem to align to at
    // least 64K.

    // If the memory was requested at a particular address, use
    // os::attempt_reserve_memory_at() to avoid over mapping something
    // important.  If available space is not detected, return NULL.

    if (requested_address != 0) {
      base = os::attempt_reserve_memory_at(size, requested_address);
      if (failed_to_reserve_as_requested(base, requested_address, size, false)) {
        // OS ignored requested address. Try different address.
        base = NULL;
      }
    } else {
      base = os::reserve_memory(size, NULL, alignment);
    }

    if (base == NULL) return;

    // Check alignment constraints
    if ((((size_t)base + noaccess_prefix) & (alignment - 1)) != 0) {
      // Base not aligned, retry
      if (!os::release_memory(base, size)) fatal("os::release_memory failed");
      // Make sure that size is aligned
      size = align_size_up(size, alignment);
      base = os::reserve_memory_aligned(size, alignment);

      if (requested_address != 0 &&
          failed_to_reserve_as_requested(base, requested_address, size, false)) {
        // As a result of the alignment constraints, the allocated base differs
        // from the requested address. Return back to the caller who can
        // take remedial action (like try again without a requested address).
        assert(_base == NULL, "should be");
        return;
      }
    }
  }
  // Done
  _base = base;
  _size = size;
  _alignment = alignment;
  _noaccess_prefix = noaccess_prefix;

  // Assert that if noaccess_prefix is used, it is the same as alignment.
  assert(noaccess_prefix == 0 ||
         noaccess_prefix == _alignment, "noaccess prefix wrong");

  assert(markOopDesc::encode_pointer_as_mark(_base)->decode_pointer() == _base,
         "area must be distinguisable from marks for mark-sweep");
  assert(markOopDesc::encode_pointer_as_mark(&_base[size])->decode_pointer() == &_base[size],
         "area must be distinguisable from marks for mark-sweep");
}


ReservedSpace::ReservedSpace(char* base, size_t size, size_t alignment,
                             bool special, bool executable) {
  assert((size % os::vm_allocation_granularity()) == 0,
         "size not allocation aligned");
  _base = base;
  _size = size;
  _alignment = alignment;
  _noaccess_prefix = 0;
  _special = special;
  _executable = executable;
}


ReservedSpace ReservedSpace::first_part(size_t partition_size, size_t alignment,
                                        bool split, bool realloc) {
  assert(partition_size <= size(), "partition failed");
  if (split) {
    os::split_reserved_memory(base(), size(), partition_size, realloc);
  }
  ReservedSpace result(base(), partition_size, alignment, special(),
                       executable());
  return result;
}


ReservedSpace
ReservedSpace::last_part(size_t partition_size, size_t alignment) {
  assert(partition_size <= size(), "partition failed");
  ReservedSpace result(base() + partition_size, size() - partition_size,
                       alignment, special(), executable());
  return result;
}


size_t ReservedSpace::page_align_size_up(size_t size) {
  return align_size_up(size, os::vm_page_size());
}


size_t ReservedSpace::page_align_size_down(size_t size) {
  return align_size_down(size, os::vm_page_size());
}


size_t ReservedSpace::allocation_align_size_up(size_t size) {
  return align_size_up(size, os::vm_allocation_granularity());
}


size_t ReservedSpace::allocation_align_size_down(size_t size) {
  return align_size_down(size, os::vm_allocation_granularity());
}


void ReservedSpace::release() {
  if (is_reserved()) {
    char *real_base = _base - _noaccess_prefix;
    const size_t real_size = _size + _noaccess_prefix;
    if (special()) {
      os::release_memory_special(real_base, real_size);
    } else{
      os::release_memory(real_base, real_size);
    }
    _base = NULL;
    _size = 0;
    _noaccess_prefix = 0;
    _special = false;
    _executable = false;
  }
}

void ReservedSpace::protect_noaccess_prefix(const size_t size) {
  assert( (_noaccess_prefix != 0) == (UseCompressedOops && _base != NULL &&
                                      (Universe::narrow_oop_base() != NULL) &&
                                      Universe::narrow_oop_use_implicit_null_checks()),
         "noaccess_prefix should be used only with non zero based compressed oops");

  // If there is no noaccess prefix, return.
  if (_noaccess_prefix == 0) return;

  assert(_noaccess_prefix >= (size_t)os::vm_page_size(),
         "must be at least page size big");

  // Protect memory at the base of the allocated region.
  // If special, the page was committed (only matters on windows)
  if (!os::protect_memory(_base, _noaccess_prefix, os::MEM_PROT_NONE,
                          _special)) {
    fatal("cannot protect protection page");
  }
  if (PrintCompressedOopsMode) {
    tty->cr();
    tty->print_cr("Protected page at the reserved heap base: " PTR_FORMAT " / " INTX_FORMAT " bytes", _base, _noaccess_prefix);
  }

  _base += _noaccess_prefix;
  _size -= _noaccess_prefix;
  assert((size == _size) && ((uintptr_t)_base % _alignment == 0),
         "must be exactly of required size and alignment");
}

ReservedHeapSpace::ReservedHeapSpace(size_t size, size_t alignment,
                                     bool large, char* requested_address) :
  ReservedSpace(size, alignment, large,
                requested_address,
                (UseCompressedOops && (Universe::narrow_oop_base() != NULL) &&
                 Universe::narrow_oop_use_implicit_null_checks()) ?
                  lcm(os::vm_page_size(), alignment) : 0) {
  if (base() > 0) {
    MemTracker::record_virtual_memory_type((address)base(), mtJavaHeap);
  }

  // Only reserved space for the java heap should have a noaccess_prefix
  // if using compressed oops.
  protect_noaccess_prefix(size);
}

// Reserve space for code segment.  Same as Java heap only we mark this as
// executable.
ReservedCodeSpace::ReservedCodeSpace(size_t r_size,
                                     size_t rs_align,
                                     bool large) :
  ReservedSpace(r_size, rs_align, large, /*executable*/ true) {
  MemTracker::record_virtual_memory_type((address)base(), mtCode);
}

// VirtualSpace

VirtualSpace::VirtualSpace() {
  _low_boundary           = NULL;
  _high_boundary          = NULL;
  _low                    = NULL;
  _high                   = NULL;
  _lower_high             = NULL;
  _middle_high            = NULL;
  _upper_high             = NULL;
  _lower_high_boundary    = NULL;
  _middle_high_boundary   = NULL;
  _upper_high_boundary    = NULL;
  _lower_alignment        = 0;
  _middle_alignment       = 0;
  _upper_alignment        = 0;
  _special                = false;
  _executable             = false;
}


bool VirtualSpace::initialize(ReservedSpace rs, size_t committed_size) {
  if(!rs.is_reserved()) return false;  // allocation failed.
  assert(_low_boundary == NULL, "VirtualSpace already initialized");
  _low_boundary  = rs.base();
  _high_boundary = low_boundary() + rs.size();

  _low = low_boundary();
  _high = low();

  _special = rs.special();
  _executable = rs.executable();

  // When a VirtualSpace begins life at a large size, make all future expansion
  // and shrinking occur aligned to a granularity of large pages.  This avoids
  // fragmentation of physical addresses that inhibits the use of large pages
  // by the OS virtual memory system.  Empirically,  we see that with a 4MB
  // page size, the only spaces that get handled this way are codecache and
  // the heap itself, both of which provide a substantial performance
  // boost in many benchmarks when covered by large pages.
  //
  // No attempt is made to force large page alignment at the very top and
  // bottom of the space if they are not aligned so already.
  _lower_alignment  = os::vm_page_size();
  _middle_alignment = os::page_size_for_region(rs.size(), rs.size(), 1);
  _upper_alignment  = os::vm_page_size();

  // End of each region
  _lower_high_boundary = (char*) round_to((intptr_t) low_boundary(), middle_alignment());
  _middle_high_boundary = (char*) round_down((intptr_t) high_boundary(), middle_alignment());
  _upper_high_boundary = high_boundary();

  // High address of each region
  _lower_high = low_boundary();
  _middle_high = lower_high_boundary();
  _upper_high = middle_high_boundary();

  // commit to initial size
  if (committed_size > 0) {
    if (!expand_by(committed_size)) {
      return false;
    }
  }
  return true;
}


VirtualSpace::~VirtualSpace() {
  release();
}


void VirtualSpace::release() {
  // This does not release memory it never reserved.
  // Caller must release via rs.release();
  _low_boundary           = NULL;
  _high_boundary          = NULL;
  _low                    = NULL;
  _high                   = NULL;
  _lower_high             = NULL;
  _middle_high            = NULL;
  _upper_high             = NULL;
  _lower_high_boundary    = NULL;
  _middle_high_boundary   = NULL;
  _upper_high_boundary    = NULL;
  _lower_alignment        = 0;
  _middle_alignment       = 0;
  _upper_alignment        = 0;
  _special                = false;
  _executable             = false;
}


size_t VirtualSpace::committed_size() const {
  return pointer_delta(high(), low(), sizeof(char));
}


size_t VirtualSpace::reserved_size() const {
  return pointer_delta(high_boundary(), low_boundary(), sizeof(char));
}


size_t VirtualSpace::uncommitted_size()  const {
  return reserved_size() - committed_size();
}

size_t VirtualSpace::actual_committed_size() const {
  // Special VirtualSpaces commit all reserved space up front.
  if (special()) {
    return reserved_size();
  }

  size_t committed_low    = pointer_delta(_lower_high,  _low_boundary,         sizeof(char));
  size_t committed_middle = pointer_delta(_middle_high, _lower_high_boundary,  sizeof(char));
  size_t committed_high   = pointer_delta(_upper_high,  _middle_high_boundary, sizeof(char));

#ifdef ASSERT
  size_t lower  = pointer_delta(_lower_high_boundary,  _low_boundary,         sizeof(char));
  size_t middle = pointer_delta(_middle_high_boundary, _lower_high_boundary,  sizeof(char));
  size_t upper  = pointer_delta(_upper_high_boundary,  _middle_high_boundary, sizeof(char));

  if (committed_high > 0) {
    assert(committed_low == lower, "Must be");
    assert(committed_middle == middle, "Must be");
  }

  if (committed_middle > 0) {
    assert(committed_low == lower, "Must be");
  }
  if (committed_middle < middle) {
    assert(committed_high == 0, "Must be");
  }

  if (committed_low < lower) {
    assert(committed_high == 0, "Must be");
    assert(committed_middle == 0, "Must be");
  }
#endif

  return committed_low + committed_middle + committed_high;
}


bool VirtualSpace::contains(const void* p) const {
  return low() <= (const char*) p && (const char*) p < high();
}

/*
   First we need to determine if a particular virtual space is using large
   pages.  This is done at the initialize function and only virtual spaces
   that are larger than LargePageSizeInBytes use large pages.  Once we
   have determined this, all expand_by and shrink_by calls must grow and
   shrink by large page size chunks.  If a particular request
   is within the current large page, the call to commit and uncommit memory
   can be ignored.  In the case that the low and high boundaries of this
   space is not large page aligned, the pages leading to the first large
   page address and the pages after the last large page address must be
   allocated with default pages.
*/
bool VirtualSpace::expand_by(size_t bytes, bool pre_touch) {
  if (uncommitted_size() < bytes) return false;

  if (special()) {
    // don't commit memory if the entire space is pinned in memory
    _high += bytes;
    return true;
  }

  char* previous_high = high();
  char* unaligned_new_high = high() + bytes;
  assert(unaligned_new_high <= high_boundary(),
         "cannot expand by more than upper boundary");

  // Calculate where the new high for each of the regions should be.  If
  // the low_boundary() and high_boundary() are LargePageSizeInBytes aligned
  // then the unaligned lower and upper new highs would be the
  // lower_high() and upper_high() respectively.
  char* unaligned_lower_new_high =
    MIN2(unaligned_new_high, lower_high_boundary());
  char* unaligned_middle_new_high =
    MIN2(unaligned_new_high, middle_high_boundary());
  char* unaligned_upper_new_high =
    MIN2(unaligned_new_high, upper_high_boundary());

  // Align the new highs based on the regions alignment.  lower and upper
  // alignment will always be default page size.  middle alignment will be
  // LargePageSizeInBytes if the actual size of the virtual space is in
  // fact larger than LargePageSizeInBytes.
  char* aligned_lower_new_high =
    (char*) round_to((intptr_t) unaligned_lower_new_high, lower_alignment());
  char* aligned_middle_new_high =
    (char*) round_to((intptr_t) unaligned_middle_new_high, middle_alignment());
  char* aligned_upper_new_high =
    (char*) round_to((intptr_t) unaligned_upper_new_high, upper_alignment());

  // Determine which regions need to grow in this expand_by call.
  // If you are growing in the lower region, high() must be in that
  // region so calcuate the size based on high().  For the middle and
  // upper regions, determine the starting point of growth based on the
  // location of high().  By getting the MAX of the region's low address
  // (or the prevoius region's high address) and high(), we can tell if it
  // is an intra or inter region growth.
  size_t lower_needs = 0;
  if (aligned_lower_new_high > lower_high()) {
    lower_needs =
      pointer_delta(aligned_lower_new_high, lower_high(), sizeof(char));
  }
  size_t middle_needs = 0;
  if (aligned_middle_new_high > middle_high()) {
    middle_needs =
      pointer_delta(aligned_middle_new_high, middle_high(), sizeof(char));
  }
  size_t upper_needs = 0;
  if (aligned_upper_new_high > upper_high()) {
    upper_needs =
      pointer_delta(aligned_upper_new_high, upper_high(), sizeof(char));
  }

  // Check contiguity.
  assert(low_boundary() <= lower_high() &&
         lower_high() <= lower_high_boundary(),
         "high address must be contained within the region");
  assert(lower_high_boundary() <= middle_high() &&
         middle_high() <= middle_high_boundary(),
         "high address must be contained within the region");
  assert(middle_high_boundary() <= upper_high() &&
         upper_high() <= upper_high_boundary(),
         "high address must be contained within the region");

  // Commit regions
  if (lower_needs > 0) {
    assert(low_boundary() <= lower_high() &&
           lower_high() + lower_needs <= lower_high_boundary(),
           "must not expand beyond region");
    if (!os::commit_memory(lower_high(), lower_needs, _executable)) {
      debug_only(warning("INFO: os::commit_memory(" PTR_FORMAT
                         ", lower_needs=" SIZE_FORMAT ", %d) failed",
                         lower_high(), lower_needs, _executable);)
      return false;
    } else {
      _lower_high += lower_needs;
    }
  }
  if (middle_needs > 0) {
    assert(lower_high_boundary() <= middle_high() &&
           middle_high() + middle_needs <= middle_high_boundary(),
           "must not expand beyond region");
    if (!os::commit_memory(middle_high(), middle_needs, middle_alignment(),
                           _executable)) {
      debug_only(warning("INFO: os::commit_memory(" PTR_FORMAT
                         ", middle_needs=" SIZE_FORMAT ", " SIZE_FORMAT
                         ", %d) failed", middle_high(), middle_needs,
                         middle_alignment(), _executable);)
      return false;
    }
    _middle_high += middle_needs;
  }
  if (upper_needs > 0) {
    assert(middle_high_boundary() <= upper_high() &&
           upper_high() + upper_needs <= upper_high_boundary(),
           "must not expand beyond region");
    if (!os::commit_memory(upper_high(), upper_needs, _executable)) {
      debug_only(warning("INFO: os::commit_memory(" PTR_FORMAT
                         ", upper_needs=" SIZE_FORMAT ", %d) failed",
                         upper_high(), upper_needs, _executable);)
      return false;
    } else {
      _upper_high += upper_needs;
    }
  }

  if (pre_touch || AlwaysPreTouch) {
    int vm_ps = os::vm_page_size();
    for (char* curr = previous_high;
         curr < unaligned_new_high;
         curr += vm_ps) {
      // Note the use of a write here; originally we tried just a read, but
      // since the value read was unused, the optimizer removed the read.
      // If we ever have a concurrent touchahead thread, we'll want to use
      // a read, to avoid the potential of overwriting data (if a mutator
      // thread beats the touchahead thread to a page).  There are various
      // ways of making sure this read is not optimized away: for example,
      // generating the code for a read procedure at runtime.
      *curr = 0;
    }
  }

  _high += bytes;
  return true;
}

// A page is uncommitted if the contents of the entire page is deemed unusable.
// Continue to decrement the high() pointer until it reaches a page boundary
// in which case that particular page can now be uncommitted.
void VirtualSpace::shrink_by(size_t size) {
  if (committed_size() < size)
    fatal("Cannot shrink virtual space to negative size");

  if (special()) {
    // don't uncommit if the entire space is pinned in memory
    _high -= size;
    return;
  }

  char* unaligned_new_high = high() - size;
  assert(unaligned_new_high >= low_boundary(), "cannot shrink past lower boundary");

  // Calculate new unaligned address
  char* unaligned_upper_new_high =
    MAX2(unaligned_new_high, middle_high_boundary());
  char* unaligned_middle_new_high =
    MAX2(unaligned_new_high, lower_high_boundary());
  char* unaligned_lower_new_high =
    MAX2(unaligned_new_high, low_boundary());

  // Align address to region's alignment
  char* aligned_upper_new_high =
    (char*) round_to((intptr_t) unaligned_upper_new_high, upper_alignment());
  char* aligned_middle_new_high =
    (char*) round_to((intptr_t) unaligned_middle_new_high, middle_alignment());
  char* aligned_lower_new_high =
    (char*) round_to((intptr_t) unaligned_lower_new_high, lower_alignment());

  // Determine which regions need to shrink
  size_t upper_needs = 0;
  if (aligned_upper_new_high < upper_high()) {
    upper_needs =
      pointer_delta(upper_high(), aligned_upper_new_high, sizeof(char));
  }
  size_t middle_needs = 0;
  if (aligned_middle_new_high < middle_high()) {
    middle_needs =
      pointer_delta(middle_high(), aligned_middle_new_high, sizeof(char));
  }
  size_t lower_needs = 0;
  if (aligned_lower_new_high < lower_high()) {
    lower_needs =
      pointer_delta(lower_high(), aligned_lower_new_high, sizeof(char));
  }

  // Check contiguity.
  assert(middle_high_boundary() <= upper_high() &&
         upper_high() <= upper_high_boundary(),
         "high address must be contained within the region");
  assert(lower_high_boundary() <= middle_high() &&
         middle_high() <= middle_high_boundary(),
         "high address must be contained within the region");
  assert(low_boundary() <= lower_high() &&
         lower_high() <= lower_high_boundary(),
         "high address must be contained within the region");

  // Uncommit
  if (upper_needs > 0) {
    assert(middle_high_boundary() <= aligned_upper_new_high &&
           aligned_upper_new_high + upper_needs <= upper_high_boundary(),
           "must not shrink beyond region");
    if (!os::uncommit_memory(aligned_upper_new_high, upper_needs)) {
      debug_only(warning("os::uncommit_memory failed"));
      return;
    } else {
      _upper_high -= upper_needs;
    }
  }
  if (middle_needs > 0) {
    assert(lower_high_boundary() <= aligned_middle_new_high &&
           aligned_middle_new_high + middle_needs <= middle_high_boundary(),
           "must not shrink beyond region");
    if (!os::uncommit_memory(aligned_middle_new_high, middle_needs)) {
      debug_only(warning("os::uncommit_memory failed"));
      return;
    } else {
      _middle_high -= middle_needs;
    }
  }
  if (lower_needs > 0) {
    assert(low_boundary() <= aligned_lower_new_high &&
           aligned_lower_new_high + lower_needs <= lower_high_boundary(),
           "must not shrink beyond region");
    if (!os::uncommit_memory(aligned_lower_new_high, lower_needs)) {
      debug_only(warning("os::uncommit_memory failed"));
      return;
    } else {
      _lower_high -= lower_needs;
    }
  }

  _high -= size;
}

#ifndef PRODUCT
void VirtualSpace::check_for_contiguity() {
  // Check contiguity.
  assert(low_boundary() <= lower_high() &&
         lower_high() <= lower_high_boundary(),
         "high address must be contained within the region");
  assert(lower_high_boundary() <= middle_high() &&
         middle_high() <= middle_high_boundary(),
         "high address must be contained within the region");
  assert(middle_high_boundary() <= upper_high() &&
         upper_high() <= upper_high_boundary(),
         "high address must be contained within the region");
  assert(low() >= low_boundary(), "low");
  assert(low_boundary() <= lower_high_boundary(), "lower high boundary");
  assert(upper_high_boundary() <= high_boundary(), "upper high boundary");
  assert(high() <= upper_high(), "upper high");
}

void VirtualSpace::print() {
  tty->print   ("Virtual space:");
  if (special()) tty->print(" (pinned in memory)");
  tty->cr();
  tty->print_cr(" - committed: " SIZE_FORMAT, committed_size());
  tty->print_cr(" - reserved:  " SIZE_FORMAT, reserved_size());
  tty->print_cr(" - [low, high]:     [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  low(), high());
  tty->print_cr(" - [low_b, high_b]: [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  low_boundary(), high_boundary());
}


/////////////// Unit tests ///////////////

#ifndef PRODUCT

#define test_log(...) \
  do {\
    if (VerboseInternalVMTests) { \
      tty->print_cr(__VA_ARGS__); \
      tty->flush(); \
    }\
  } while (false)

class TestReservedSpace : AllStatic {
 public:
  static void small_page_write(void* addr, size_t size) {
    size_t page_size = os::vm_page_size();

    char* end = (char*)addr + size;
    for (char* p = (char*)addr; p < end; p += page_size) {
      *p = 1;
    }
  }

  static void release_memory_for_test(ReservedSpace rs) {
    if (rs.special()) {
      guarantee(os::release_memory_special(rs.base(), rs.size()), "Shouldn't fail");
    } else {
      guarantee(os::release_memory(rs.base(), rs.size()), "Shouldn't fail");
    }
  }

  static void test_reserved_space1(size_t size, size_t alignment) {
    test_log("test_reserved_space1(%p)", (void*) (uintptr_t) size);

    assert(is_size_aligned(size, alignment), "Incorrect input parameters");

    ReservedSpace rs(size,          // size
                     alignment,     // alignment
                     UseLargePages, // large
                     NULL,          // requested_address
                     0);            // noacces_prefix

    test_log(" rs.special() == %d", rs.special());

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    assert(is_ptr_aligned(rs.base(), alignment), "aligned sizes should always give aligned addresses");
    assert(is_size_aligned(rs.size(), alignment), "aligned sizes should always give aligned addresses");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }

  static void test_reserved_space2(size_t size) {
    test_log("test_reserved_space2(%p)", (void*)(uintptr_t)size);

    assert(is_size_aligned(size, os::vm_allocation_granularity()), "Must be at least AG aligned");

    ReservedSpace rs(size);

    test_log(" rs.special() == %d", rs.special());

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }

  static void test_reserved_space3(size_t size, size_t alignment, bool maybe_large) {
    test_log("test_reserved_space3(%p, %p, %d)",
        (void*)(uintptr_t)size, (void*)(uintptr_t)alignment, maybe_large);

    assert(is_size_aligned(size, os::vm_allocation_granularity()), "Must be at least AG aligned");
    assert(is_size_aligned(size, alignment), "Must be at least aligned against alignment");

    bool large = maybe_large && UseLargePages && size >= os::large_page_size();

    ReservedSpace rs(size, alignment, large, false);

    test_log(" rs.special() == %d", rs.special());

    assert(rs.base() != NULL, "Must be");
    assert(rs.size() == size, "Must be");

    if (rs.special()) {
      small_page_write(rs.base(), size);
    }

    release_memory_for_test(rs);
  }


  static void test_reserved_space1() {
    size_t size = 2 * 1024 * 1024;
    size_t ag   = os::vm_allocation_granularity();

    test_reserved_space1(size,      ag);
    test_reserved_space1(size * 2,  ag);
    test_reserved_space1(size * 10, ag);
  }

  static void test_reserved_space2() {
    size_t size = 2 * 1024 * 1024;
    size_t ag = os::vm_allocation_granularity();

    test_reserved_space2(size * 1);
    test_reserved_space2(size * 2);
    test_reserved_space2(size * 10);
    test_reserved_space2(ag);
    test_reserved_space2(size - ag);
    test_reserved_space2(size);
    test_reserved_space2(size + ag);
    test_reserved_space2(size * 2);
    test_reserved_space2(size * 2 - ag);
    test_reserved_space2(size * 2 + ag);
    test_reserved_space2(size * 3);
    test_reserved_space2(size * 3 - ag);
    test_reserved_space2(size * 3 + ag);
    test_reserved_space2(size * 10);
    test_reserved_space2(size * 10 + size / 2);
  }

  static void test_reserved_space3() {
    size_t ag = os::vm_allocation_granularity();

    test_reserved_space3(ag,      ag    , false);
    test_reserved_space3(ag * 2,  ag    , false);
    test_reserved_space3(ag * 3,  ag    , false);
    test_reserved_space3(ag * 2,  ag * 2, false);
    test_reserved_space3(ag * 4,  ag * 2, false);
    test_reserved_space3(ag * 8,  ag * 2, false);
    test_reserved_space3(ag * 4,  ag * 4, false);
    test_reserved_space3(ag * 8,  ag * 4, false);
    test_reserved_space3(ag * 16, ag * 4, false);

    if (UseLargePages) {
      size_t lp = os::large_page_size();

      // Without large pages
      test_reserved_space3(lp,     ag * 4, false);
      test_reserved_space3(lp * 2, ag * 4, false);
      test_reserved_space3(lp * 4, ag * 4, false);
      test_reserved_space3(lp,     lp    , false);
      test_reserved_space3(lp * 2, lp    , false);
      test_reserved_space3(lp * 3, lp    , false);
      test_reserved_space3(lp * 2, lp * 2, false);
      test_reserved_space3(lp * 4, lp * 2, false);
      test_reserved_space3(lp * 8, lp * 2, false);

      // With large pages
      test_reserved_space3(lp, ag * 4    , true);
      test_reserved_space3(lp * 2, ag * 4, true);
      test_reserved_space3(lp * 4, ag * 4, true);
      test_reserved_space3(lp, lp        , true);
      test_reserved_space3(lp * 2, lp    , true);
      test_reserved_space3(lp * 3, lp    , true);
      test_reserved_space3(lp * 2, lp * 2, true);
      test_reserved_space3(lp * 4, lp * 2, true);
      test_reserved_space3(lp * 8, lp * 2, true);
    }
  }

  static void test_reserved_space() {
    test_reserved_space1();
    test_reserved_space2();
    test_reserved_space3();
  }
};

void TestReservedSpace_test() {
  TestReservedSpace::test_reserved_space();
}

#define assert_equals(actual, expected)     \
  assert(actual == expected,                \
    err_msg("Got " SIZE_FORMAT " expected " \
      SIZE_FORMAT, actual, expected));

#define assert_ge(value1, value2)                  \
  assert(value1 >= value2,                         \
    err_msg("'" #value1 "': " SIZE_FORMAT " '"     \
      #value2 "': " SIZE_FORMAT, value1, value2));

#define assert_lt(value1, value2)                  \
  assert(value1 < value2,                          \
    err_msg("'" #value1 "': " SIZE_FORMAT " '"     \
      #value2 "': " SIZE_FORMAT, value1, value2));


class TestVirtualSpace : AllStatic {
 public:
  static void test_virtual_space_actual_committed_space(size_t reserve_size, size_t commit_size) {
    size_t granularity = os::vm_allocation_granularity();
    size_t reserve_size_aligned = align_size_up(reserve_size, granularity);

    ReservedSpace reserved(reserve_size_aligned);

    assert(reserved.is_reserved(), "Must be");

    VirtualSpace vs;
    bool initialized = vs.initialize(reserved, 0);
    assert(initialized, "Failed to initialize VirtualSpace");

    vs.expand_by(commit_size, false);

    if (vs.special()) {
      assert_equals(vs.actual_committed_size(), reserve_size_aligned);
    } else {
      assert_ge(vs.actual_committed_size(), commit_size);
      // Approximate the commit granularity.
      size_t commit_granularity = UseLargePages ? os::large_page_size() : os::vm_page_size();
      assert_lt(vs.actual_committed_size(), commit_size + commit_granularity);
    }

    reserved.release();
  }

  static void test_virtual_space_actual_committed_space_one_large_page() {
    if (!UseLargePages) {
      return;
    }

    size_t large_page_size = os::large_page_size();

    ReservedSpace reserved(large_page_size, large_page_size, true, false);

    assert(reserved.is_reserved(), "Must be");

    VirtualSpace vs;
    bool initialized = vs.initialize(reserved, 0);
    assert(initialized, "Failed to initialize VirtualSpace");

    vs.expand_by(large_page_size, false);

    assert_equals(vs.actual_committed_size(), large_page_size);

    reserved.release();
  }

  static void test_virtual_space_actual_committed_space() {
    test_virtual_space_actual_committed_space(4 * K, 0);
    test_virtual_space_actual_committed_space(4 * K, 4 * K);
    test_virtual_space_actual_committed_space(8 * K, 0);
    test_virtual_space_actual_committed_space(8 * K, 4 * K);
    test_virtual_space_actual_committed_space(8 * K, 8 * K);
    test_virtual_space_actual_committed_space(12 * K, 0);
    test_virtual_space_actual_committed_space(12 * K, 4 * K);
    test_virtual_space_actual_committed_space(12 * K, 8 * K);
    test_virtual_space_actual_committed_space(12 * K, 12 * K);
    test_virtual_space_actual_committed_space(64 * K, 0);
    test_virtual_space_actual_committed_space(64 * K, 32 * K);
    test_virtual_space_actual_committed_space(64 * K, 64 * K);
    test_virtual_space_actual_committed_space(2 * M, 0);
    test_virtual_space_actual_committed_space(2 * M, 4 * K);
    test_virtual_space_actual_committed_space(2 * M, 64 * K);
    test_virtual_space_actual_committed_space(2 * M, 1 * M);
    test_virtual_space_actual_committed_space(2 * M, 2 * M);
    test_virtual_space_actual_committed_space(10 * M, 0);
    test_virtual_space_actual_committed_space(10 * M, 4 * K);
    test_virtual_space_actual_committed_space(10 * M, 8 * K);
    test_virtual_space_actual_committed_space(10 * M, 1 * M);
    test_virtual_space_actual_committed_space(10 * M, 2 * M);
    test_virtual_space_actual_committed_space(10 * M, 5 * M);
    test_virtual_space_actual_committed_space(10 * M, 10 * M);
  }

  static void test_virtual_space() {
    test_virtual_space_actual_committed_space();
    test_virtual_space_actual_committed_space_one_large_page();
  }
};

void TestVirtualSpace_test() {
  TestVirtualSpace::test_virtual_space();
}

#endif // PRODUCT

#endif
