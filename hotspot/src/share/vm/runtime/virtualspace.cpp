/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_virtualspace.cpp.incl"


// ReservedSpace
ReservedSpace::ReservedSpace(size_t size) {
  initialize(size, 0, false, NULL, 0, false);
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

char *
ReservedSpace::align_reserved_region(char* addr, const size_t len,
                                     const size_t prefix_size,
                                     const size_t prefix_align,
                                     const size_t suffix_size,
                                     const size_t suffix_align)
{
  assert(addr != NULL, "sanity");
  const size_t required_size = prefix_size + suffix_size;
  assert(len >= required_size, "len too small");

  const size_t s = size_t(addr);
  const size_t beg_ofs = s + prefix_size & suffix_align - 1;
  const size_t beg_delta = beg_ofs == 0 ? 0 : suffix_align - beg_ofs;

  if (len < beg_delta + required_size) {
     return NULL; // Cannot do proper alignment.
  }
  const size_t end_delta = len - (beg_delta + required_size);

  if (beg_delta != 0) {
    os::release_memory(addr, beg_delta);
  }

  if (end_delta != 0) {
    char* release_addr = (char*) (s + beg_delta + required_size);
    os::release_memory(release_addr, end_delta);
  }

  return (char*) (s + beg_delta);
}

char* ReservedSpace::reserve_and_align(const size_t reserve_size,
                                       const size_t prefix_size,
                                       const size_t prefix_align,
                                       const size_t suffix_size,
                                       const size_t suffix_align)
{
  assert(reserve_size > prefix_size + suffix_size, "should not be here");

  char* raw_addr = os::reserve_memory(reserve_size, NULL, prefix_align);
  if (raw_addr == NULL) return NULL;

  char* result = align_reserved_region(raw_addr, reserve_size, prefix_size,
                                       prefix_align, suffix_size,
                                       suffix_align);
  if (result == NULL && !os::release_memory(raw_addr, reserve_size)) {
    fatal("os::release_memory failed");
  }

#ifdef ASSERT
  if (result != NULL) {
    const size_t raw = size_t(raw_addr);
    const size_t res = size_t(result);
    assert(res >= raw, "alignment decreased start addr");
    assert(res + prefix_size + suffix_size <= raw + reserve_size,
           "alignment increased end addr");
    assert((res & prefix_align - 1) == 0, "bad alignment of prefix");
    assert((res + prefix_size & suffix_align - 1) == 0,
           "bad alignment of suffix");
  }
#endif

  return result;
}

ReservedSpace::ReservedSpace(const size_t prefix_size,
                             const size_t prefix_align,
                             const size_t suffix_size,
                             const size_t suffix_align,
                             char* requested_address,
                             const size_t noaccess_prefix)
{
  assert(prefix_size != 0, "sanity");
  assert(prefix_align != 0, "sanity");
  assert(suffix_size != 0, "sanity");
  assert(suffix_align != 0, "sanity");
  assert((prefix_size & prefix_align - 1) == 0,
    "prefix_size not divisible by prefix_align");
  assert((suffix_size & suffix_align - 1) == 0,
    "suffix_size not divisible by suffix_align");
  assert((suffix_align & prefix_align - 1) == 0,
    "suffix_align not divisible by prefix_align");

  // Add in noaccess_prefix to prefix_size;
  const size_t adjusted_prefix_size = prefix_size + noaccess_prefix;
  const size_t size = adjusted_prefix_size + suffix_size;

  // On systems where the entire region has to be reserved and committed up
  // front, the compound alignment normally done by this method is unnecessary.
  const bool try_reserve_special = UseLargePages &&
    prefix_align == os::large_page_size();
  if (!os::can_commit_large_page_memory() && try_reserve_special) {
    initialize(size, prefix_align, true, requested_address, noaccess_prefix,
               false);
    return;
  }

  _base = NULL;
  _size = 0;
  _alignment = 0;
  _special = false;
  _noaccess_prefix = 0;
  _executable = false;

  // Assert that if noaccess_prefix is used, it is the same as prefix_align.
  assert(noaccess_prefix == 0 ||
         noaccess_prefix == prefix_align, "noaccess prefix wrong");

  // Optimistically try to reserve the exact size needed.
  char* addr;
  if (requested_address != 0) {
    addr = os::attempt_reserve_memory_at(size,
                                         requested_address-noaccess_prefix);
  } else {
    addr = os::reserve_memory(size, NULL, prefix_align);
  }
  if (addr == NULL) return;

  // Check whether the result has the needed alignment (unlikely unless
  // prefix_align == suffix_align).
  const size_t ofs = size_t(addr) + adjusted_prefix_size & suffix_align - 1;
  if (ofs != 0) {
    // Wrong alignment.  Release, allocate more space and do manual alignment.
    //
    // On most operating systems, another allocation with a somewhat larger size
    // will return an address "close to" that of the previous allocation.  The
    // result is often the same address (if the kernel hands out virtual
    // addresses from low to high), or an address that is offset by the increase
    // in size.  Exploit that to minimize the amount of extra space requested.
    if (!os::release_memory(addr, size)) {
      fatal("os::release_memory failed");
    }

    const size_t extra = MAX2(ofs, suffix_align - ofs);
    addr = reserve_and_align(size + extra, adjusted_prefix_size, prefix_align,
                             suffix_size, suffix_align);
    if (addr == NULL) {
      // Try an even larger region.  If this fails, address space is exhausted.
      addr = reserve_and_align(size + suffix_align, adjusted_prefix_size,
                               prefix_align, suffix_size, suffix_align);
    }
  }

  _base = addr;
  _size = size;
  _alignment = prefix_align;
  _noaccess_prefix = noaccess_prefix;
}

void ReservedSpace::initialize(size_t size, size_t alignment, bool large,
                               char* requested_address,
                               const size_t noaccess_prefix,
                               bool executable) {
  const size_t granularity = os::vm_allocation_granularity();
  assert((size & granularity - 1) == 0,
         "size not aligned to os::vm_allocation_granularity()");
  assert((alignment & granularity - 1) == 0,
         "alignment not aligned to os::vm_allocation_granularity()");
  assert(alignment == 0 || is_power_of_2((intptr_t)alignment),
         "not a power of 2");

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

  if (special) {

    base = os::reserve_memory_special(size, requested_address, executable);

    if (base != NULL) {
      // Check alignment constraints
      if (alignment > 0) {
        assert((uintptr_t) base % alignment == 0,
               "Large pages returned a non-aligned address");
      }
      _special = true;
    } else {
      // failed; try to reserve regular memory below
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
      base = os::attempt_reserve_memory_at(size,
                                           requested_address-noaccess_prefix);
    } else {
      base = os::reserve_memory(size, NULL, alignment);
    }

    if (base == NULL) return;

    // Check alignment constraints
    if (alignment > 0 && ((size_t)base & alignment - 1) != 0) {
      // Base not aligned, retry
      if (!os::release_memory(base, size)) fatal("os::release_memory failed");
      // Reserve size large enough to do manual alignment and
      // increase size to a multiple of the desired alignment
      size = align_size_up(size, alignment);
      size_t extra_size = size + alignment;
      do {
        char* extra_base = os::reserve_memory(extra_size, NULL, alignment);
        if (extra_base == NULL) return;
        // Do manual alignement
        base = (char*) align_size_up((uintptr_t) extra_base, alignment);
        assert(base >= extra_base, "just checking");
        // Re-reserve the region at the aligned base address.
        os::release_memory(extra_base, extra_size);
        base = os::reserve_memory(size, base);
      } while (base == NULL);
    }
  }
  // Done
  _base = base;
  _size = size;
  _alignment = MAX2(alignment, (size_t) os::vm_page_size());
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
  // If there is noaccess prefix, return.
  if (_noaccess_prefix == 0) return;

  assert(_noaccess_prefix >= (size_t)os::vm_page_size(),
         "must be at least page size big");

  // Protect memory at the base of the allocated region.
  // If special, the page was committed (only matters on windows)
  if (!os::protect_memory(_base, _noaccess_prefix, os::MEM_PROT_NONE,
                          _special)) {
    fatal("cannot protect protection page");
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
  // Only reserved space for the java heap should have a noaccess_prefix
  // if using compressed oops.
  protect_noaccess_prefix(size);
}

ReservedHeapSpace::ReservedHeapSpace(const size_t prefix_size,
                                     const size_t prefix_align,
                                     const size_t suffix_size,
                                     const size_t suffix_align,
                                     char* requested_address) :
  ReservedSpace(prefix_size, prefix_align, suffix_size, suffix_align,
                requested_address,
                (UseCompressedOops && (Universe::narrow_oop_base() != NULL) &&
                 Universe::narrow_oop_use_implicit_null_checks()) ?
                  lcm(os::vm_page_size(), prefix_align) : 0) {
  protect_noaccess_prefix(prefix_size+suffix_size);
}

// Reserve space for code segment.  Same as Java heap only we mark this as
// executable.
ReservedCodeSpace::ReservedCodeSpace(size_t r_size,
                                     size_t rs_align,
                                     bool large) :
  ReservedSpace(r_size, rs_align, large, /*executable*/ true) {
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
      debug_only(warning("os::commit_memory failed"));
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
      debug_only(warning("os::commit_memory failed"));
      return false;
    }
    _middle_high += middle_needs;
  }
  if (upper_needs > 0) {
    assert(middle_high_boundary() <= upper_high() &&
           upper_high() + upper_needs <= upper_high_boundary(),
           "must not expand beyond region");
    if (!os::commit_memory(upper_high(), upper_needs, _executable)) {
      debug_only(warning("os::commit_memory failed"));
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
  tty->print_cr(" - committed: %ld", committed_size());
  tty->print_cr(" - reserved:  %ld", reserved_size());
  tty->print_cr(" - [low, high]:     [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  low(), high());
  tty->print_cr(" - [low_b, high_b]: [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  low_boundary(), high_boundary());
}

#endif
