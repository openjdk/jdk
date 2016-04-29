/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCacheExtensions.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/virtualspace.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "services/memTracker.hpp"

// ReservedSpace

// Dummy constructor
ReservedSpace::ReservedSpace() : _base(NULL), _size(0), _noaccess_prefix(0),
    _alignment(0), _special(false), _executable(false) {
}

ReservedSpace::ReservedSpace(size_t size, size_t preferred_page_size) {
  bool has_preferred_page_size = preferred_page_size != 0;
  // Want to use large pages where possible and pad with small pages.
  size_t page_size = has_preferred_page_size ? preferred_page_size : os::page_size_for_region_unaligned(size, 1);
  bool large_pages = page_size != (size_t)os::vm_page_size();
  size_t alignment;
  if (large_pages && has_preferred_page_size) {
    alignment = MAX2(page_size, (size_t)os::vm_allocation_granularity());
    // ReservedSpace initialization requires size to be aligned to the given
    // alignment. Align the size up.
    size = align_size_up(size, alignment);
  } else {
    // Don't force the alignment to be large page aligned,
    // since that will waste memory.
    alignment = os::vm_allocation_granularity();
  }
  initialize(size, alignment, large_pages, NULL, false);
}

ReservedSpace::ReservedSpace(size_t size, size_t alignment,
                             bool large,
                             char* requested_address) {
  initialize(size, alignment, large, requested_address, false);
}

ReservedSpace::ReservedSpace(size_t size, size_t alignment,
                             bool large,
                             bool executable) {
  initialize(size, alignment, large, NULL, executable);
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
    log_debug(gc, heap, coops)("Reserved memory not at requested address: " PTR_FORMAT " vs " PTR_FORMAT, p2i(base), p2i(requested_address));
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
                               bool executable) {
  const size_t granularity = os::vm_allocation_granularity();
  assert((size & (granularity - 1)) == 0,
         "size not aligned to os::vm_allocation_granularity()");
  assert((alignment & (granularity - 1)) == 0,
         "alignment not aligned to os::vm_allocation_granularity()");
  assert(alignment == 0 || is_power_of_2((intptr_t)alignment),
         "not a power of 2");

  alignment = MAX2(alignment, (size_t)os::vm_page_size());

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

    base = os::reserve_memory_special(size, alignment, requested_address, executable);

    if (base != NULL) {
      if (failed_to_reserve_as_requested(base, requested_address, size, true)) {
        // OS ignored requested address. Try different address.
        return;
      }
      // Check alignment constraints.
      assert((uintptr_t) base % alignment == 0,
             "Large pages returned a non-aligned address, base: "
             PTR_FORMAT " alignment: " SIZE_FORMAT_HEX,
             p2i(base), alignment);
      _special = true;
    } else {
      // failed; try to reserve regular memory below
      if (UseLargePages && (!FLAG_IS_DEFAULT(UseLargePages) ||
                            !FLAG_IS_DEFAULT(LargePageSizeInBytes))) {
        log_debug(gc, heap, coops)("Reserve regular memory without large pages");
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
    if ((((size_t)base) & (alignment - 1)) != 0) {
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
    _alignment = 0;
    _special = false;
    _executable = false;
  }
}

static size_t noaccess_prefix_size(size_t alignment) {
  return lcm(os::vm_page_size(), alignment);
}

void ReservedHeapSpace::establish_noaccess_prefix() {
  assert(_alignment >= (size_t)os::vm_page_size(), "must be at least page size big");
  _noaccess_prefix = noaccess_prefix_size(_alignment);

  if (base() && base() + _size > (char *)OopEncodingHeapMax) {
    if (true
        WIN64_ONLY(&& !UseLargePages)
        AIX_ONLY(&& os::vm_page_size() != SIZE_64K)) {
      // Protect memory at the base of the allocated region.
      // If special, the page was committed (only matters on windows)
      if (!os::protect_memory(_base, _noaccess_prefix, os::MEM_PROT_NONE, _special)) {
        fatal("cannot protect protection page");
      }
      log_debug(gc, heap, coops)("Protected page at the reserved heap base: "
                                 PTR_FORMAT " / " INTX_FORMAT " bytes",
                                 p2i(_base),
                                 _noaccess_prefix);
      assert(Universe::narrow_oop_use_implicit_null_checks() == true, "not initialized?");
    } else {
      Universe::set_narrow_oop_use_implicit_null_checks(false);
    }
  }

  _base += _noaccess_prefix;
  _size -= _noaccess_prefix;
  assert(((uintptr_t)_base % _alignment == 0), "must be exactly of required alignment");
}

// Tries to allocate memory of size 'size' at address requested_address with alignment 'alignment'.
// Does not check whether the reserved memory actually is at requested_address, as the memory returned
// might still fulfill the wishes of the caller.
// Assures the memory is aligned to 'alignment'.
// NOTE: If ReservedHeapSpace already points to some reserved memory this is freed, first.
void ReservedHeapSpace::try_reserve_heap(size_t size,
                                         size_t alignment,
                                         bool large,
                                         char* requested_address) {
  if (_base != NULL) {
    // We tried before, but we didn't like the address delivered.
    release();
  }

  // If OS doesn't support demand paging for large page memory, we need
  // to use reserve_memory_special() to reserve and pin the entire region.
  bool special = large && !os::can_commit_large_page_memory();
  char* base = NULL;

  log_trace(gc, heap, coops)("Trying to allocate at address " PTR_FORMAT
                             " heap of size " SIZE_FORMAT_HEX,
                             p2i(requested_address),
                             size);

  if (special) {
    base = os::reserve_memory_special(size, alignment, requested_address, false);

    if (base != NULL) {
      // Check alignment constraints.
      assert((uintptr_t) base % alignment == 0,
             "Large pages returned a non-aligned address, base: "
             PTR_FORMAT " alignment: " SIZE_FORMAT_HEX,
             p2i(base), alignment);
      _special = true;
    }
  }

  if (base == NULL) {
    // Failed; try to reserve regular memory below
    if (UseLargePages && (!FLAG_IS_DEFAULT(UseLargePages) ||
                          !FLAG_IS_DEFAULT(LargePageSizeInBytes))) {
      log_debug(gc, heap, coops)("Reserve regular memory without large pages");
    }

    // Optimistically assume that the OSes returns an aligned base pointer.
    // When reserving a large address range, most OSes seem to align to at
    // least 64K.

    // If the memory was requested at a particular address, use
    // os::attempt_reserve_memory_at() to avoid over mapping something
    // important.  If available space is not detected, return NULL.

    if (requested_address != 0) {
      base = os::attempt_reserve_memory_at(size, requested_address);
    } else {
      base = os::reserve_memory(size, NULL, alignment);
    }
  }
  if (base == NULL) { return; }

  // Done
  _base = base;
  _size = size;
  _alignment = alignment;

  // Check alignment constraints
  if ((((size_t)base) & (alignment - 1)) != 0) {
    // Base not aligned, retry.
    release();
  }
}

void ReservedHeapSpace::try_reserve_range(char *highest_start,
                                          char *lowest_start,
                                          size_t attach_point_alignment,
                                          char *aligned_heap_base_min_address,
                                          char *upper_bound,
                                          size_t size,
                                          size_t alignment,
                                          bool large) {
  const size_t attach_range = highest_start - lowest_start;
  // Cap num_attempts at possible number.
  // At least one is possible even for 0 sized attach range.
  const uint64_t num_attempts_possible = (attach_range / attach_point_alignment) + 1;
  const uint64_t num_attempts_to_try   = MIN2((uint64_t)HeapSearchSteps, num_attempts_possible);

  const size_t stepsize = (attach_range == 0) ? // Only one try.
    (size_t) highest_start : align_size_up(attach_range / num_attempts_to_try, attach_point_alignment);

  // Try attach points from top to bottom.
  char* attach_point = highest_start;
  while (attach_point >= lowest_start  &&
         attach_point <= highest_start &&  // Avoid wrap around.
         ((_base == NULL) ||
          (_base < aligned_heap_base_min_address || _base + size > upper_bound))) {
    try_reserve_heap(size, alignment, large, attach_point);
    attach_point -= stepsize;
  }
}

#define SIZE_64K  ((uint64_t) UCONST64(      0x10000))
#define SIZE_256M ((uint64_t) UCONST64(   0x10000000))
#define SIZE_32G  ((uint64_t) UCONST64(  0x800000000))

// Helper for heap allocation. Returns an array with addresses
// (OS-specific) which are suited for disjoint base mode. Array is
// NULL terminated.
static char** get_attach_addresses_for_disjoint_mode() {
  static uint64_t addresses[] = {
     2 * SIZE_32G,
     3 * SIZE_32G,
     4 * SIZE_32G,
     8 * SIZE_32G,
    10 * SIZE_32G,
     1 * SIZE_64K * SIZE_32G,
     2 * SIZE_64K * SIZE_32G,
     3 * SIZE_64K * SIZE_32G,
     4 * SIZE_64K * SIZE_32G,
    16 * SIZE_64K * SIZE_32G,
    32 * SIZE_64K * SIZE_32G,
    34 * SIZE_64K * SIZE_32G,
    0
  };

  // Sort out addresses smaller than HeapBaseMinAddress. This assumes
  // the array is sorted.
  uint i = 0;
  while (addresses[i] != 0 &&
         (addresses[i] < OopEncodingHeapMax || addresses[i] < HeapBaseMinAddress)) {
    i++;
  }
  uint start = i;

  // Avoid more steps than requested.
  i = 0;
  while (addresses[start+i] != 0) {
    if (i == HeapSearchSteps) {
      addresses[start+i] = 0;
      break;
    }
    i++;
  }

  return (char**) &addresses[start];
}

void ReservedHeapSpace::initialize_compressed_heap(const size_t size, size_t alignment, bool large) {
  guarantee(size + noaccess_prefix_size(alignment) <= OopEncodingHeapMax,
            "can not allocate compressed oop heap for this size");
  guarantee(alignment == MAX2(alignment, (size_t)os::vm_page_size()), "alignment too small");
  assert(HeapBaseMinAddress > 0, "sanity");

  const size_t granularity = os::vm_allocation_granularity();
  assert((size & (granularity - 1)) == 0,
         "size not aligned to os::vm_allocation_granularity()");
  assert((alignment & (granularity - 1)) == 0,
         "alignment not aligned to os::vm_allocation_granularity()");
  assert(alignment == 0 || is_power_of_2((intptr_t)alignment),
         "not a power of 2");

  // The necessary attach point alignment for generated wish addresses.
  // This is needed to increase the chance of attaching for mmap and shmat.
  const size_t os_attach_point_alignment =
    AIX_ONLY(SIZE_256M)  // Known shm boundary alignment.
    NOT_AIX(os::vm_allocation_granularity());
  const size_t attach_point_alignment = lcm(alignment, os_attach_point_alignment);

  char *aligned_heap_base_min_address = (char *)align_ptr_up((void *)HeapBaseMinAddress, alignment);
  size_t noaccess_prefix = ((aligned_heap_base_min_address + size) > (char*)OopEncodingHeapMax) ?
    noaccess_prefix_size(alignment) : 0;

  // Attempt to alloc at user-given address.
  if (!FLAG_IS_DEFAULT(HeapBaseMinAddress)) {
    try_reserve_heap(size + noaccess_prefix, alignment, large, aligned_heap_base_min_address);
    if (_base != aligned_heap_base_min_address) { // Enforce this exact address.
      release();
    }
  }

  // Keep heap at HeapBaseMinAddress.
  if (_base == NULL) {

    // Try to allocate the heap at addresses that allow efficient oop compression.
    // Different schemes are tried, in order of decreasing optimization potential.
    //
    // For this, try_reserve_heap() is called with the desired heap base addresses.
    // A call into the os layer to allocate at a given address can return memory
    // at a different address than requested.  Still, this might be memory at a useful
    // address. try_reserve_heap() always returns this allocated memory, as only here
    // the criteria for a good heap are checked.

    // Attempt to allocate so that we can run without base and scale (32-Bit unscaled compressed oops).
    // Give it several tries from top of range to bottom.
    if (aligned_heap_base_min_address + size <= (char *)UnscaledOopHeapMax) {

      // Calc address range within we try to attach (range of possible start addresses).
      char* const highest_start = (char *)align_ptr_down((char *)UnscaledOopHeapMax - size, attach_point_alignment);
      char* const lowest_start  = (char *)align_ptr_up(aligned_heap_base_min_address, attach_point_alignment);
      try_reserve_range(highest_start, lowest_start, attach_point_alignment,
                        aligned_heap_base_min_address, (char *)UnscaledOopHeapMax, size, alignment, large);
    }

    // zerobased: Attempt to allocate in the lower 32G.
    // But leave room for the compressed class pointers, which is allocated above
    // the heap.
    char *zerobased_max = (char *)OopEncodingHeapMax;
    const size_t class_space = align_size_up(CompressedClassSpaceSize, alignment);
    // For small heaps, save some space for compressed class pointer
    // space so it can be decoded with no base.
    if (UseCompressedClassPointers && !UseSharedSpaces &&
        OopEncodingHeapMax <= KlassEncodingMetaspaceMax &&
        (uint64_t)(aligned_heap_base_min_address + size + class_space) <= KlassEncodingMetaspaceMax) {
      zerobased_max = (char *)OopEncodingHeapMax - class_space;
    }

    // Give it several tries from top of range to bottom.
    if (aligned_heap_base_min_address + size <= zerobased_max &&    // Zerobased theoretical possible.
        ((_base == NULL) ||                        // No previous try succeeded.
         (_base + size > zerobased_max))) {        // Unscaled delivered an arbitrary address.

      // Calc address range within we try to attach (range of possible start addresses).
      char *const highest_start = (char *)align_ptr_down(zerobased_max - size, attach_point_alignment);
      // Need to be careful about size being guaranteed to be less
      // than UnscaledOopHeapMax due to type constraints.
      char *lowest_start = aligned_heap_base_min_address;
      uint64_t unscaled_end = UnscaledOopHeapMax - size;
      if (unscaled_end < UnscaledOopHeapMax) { // unscaled_end wrapped if size is large
        lowest_start = MAX2(lowest_start, (char*)unscaled_end);
      }
      lowest_start  = (char *)align_ptr_up(lowest_start, attach_point_alignment);
      try_reserve_range(highest_start, lowest_start, attach_point_alignment,
                        aligned_heap_base_min_address, zerobased_max, size, alignment, large);
    }

    // Now we go for heaps with base != 0.  We need a noaccess prefix to efficiently
    // implement null checks.
    noaccess_prefix = noaccess_prefix_size(alignment);

    // Try to attach at addresses that are aligned to OopEncodingHeapMax. Disjointbase mode.
    char** addresses = get_attach_addresses_for_disjoint_mode();
    int i = 0;
    while (addresses[i] &&                                 // End of array not yet reached.
           ((_base == NULL) ||                             // No previous try succeeded.
            (_base + size >  (char *)OopEncodingHeapMax && // Not zerobased or unscaled address.
             !Universe::is_disjoint_heap_base_address((address)_base)))) {  // Not disjoint address.
      char* const attach_point = addresses[i];
      assert(attach_point >= aligned_heap_base_min_address, "Flag support broken");
      try_reserve_heap(size + noaccess_prefix, alignment, large, attach_point);
      i++;
    }

    // Last, desperate try without any placement.
    if (_base == NULL) {
      log_trace(gc, heap, coops)("Trying to allocate at address NULL heap of size " SIZE_FORMAT_HEX, size + noaccess_prefix);
      initialize(size + noaccess_prefix, alignment, large, NULL, false);
    }
  }
}

ReservedHeapSpace::ReservedHeapSpace(size_t size, size_t alignment, bool large) : ReservedSpace() {

  if (size == 0) {
    return;
  }

  // Heap size should be aligned to alignment, too.
  guarantee(is_size_aligned(size, alignment), "set by caller");

  if (UseCompressedOops) {
    initialize_compressed_heap(size, alignment, large);
    if (_size > size) {
      // We allocated heap with noaccess prefix.
      // It can happen we get a zerobased/unscaled heap with noaccess prefix,
      // if we had to try at arbitrary address.
      establish_noaccess_prefix();
    }
  } else {
    initialize(size, alignment, large, NULL, false);
  }

  assert(markOopDesc::encode_pointer_as_mark(_base)->decode_pointer() == _base,
         "area must be distinguishable from marks for mark-sweep");
  assert(markOopDesc::encode_pointer_as_mark(&_base[size])->decode_pointer() == &_base[size],
         "area must be distinguishable from marks for mark-sweep");

  if (base() > 0) {
    MemTracker::record_virtual_memory_type((address)base(), mtJavaHeap);
  }
}

// Reserve space for code segment.  Same as Java heap only we mark this as
// executable.
ReservedCodeSpace::ReservedCodeSpace(size_t r_size,
                                     size_t rs_align,
                                     bool large) :
  ReservedSpace(r_size, rs_align, large, /*executable*/ CodeCacheExtensions::support_dynamic_code()) {
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
  const size_t max_commit_granularity = os::page_size_for_region_unaligned(rs.size(), 1);
  return initialize_with_granularity(rs, committed_size, max_commit_granularity);
}

bool VirtualSpace::initialize_with_granularity(ReservedSpace rs, size_t committed_size, size_t max_commit_granularity) {
  if(!rs.is_reserved()) return false;  // allocation failed.
  assert(_low_boundary == NULL, "VirtualSpace already initialized");
  assert(max_commit_granularity > 0, "Granularity must be non-zero.");

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
  _middle_alignment = max_commit_granularity;
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
  // region so calculate the size based on high().  For the middle and
  // upper regions, determine the starting point of growth based on the
  // location of high().  By getting the MAX of the region's low address
  // (or the previous region's high address) and high(), we can tell if it
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
                         p2i(lower_high()), lower_needs, _executable);)
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
                         ", %d) failed", p2i(middle_high()), middle_needs,
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
                         p2i(upper_high()), upper_needs, _executable);)
      return false;
    } else {
      _upper_high += upper_needs;
    }
  }

  if (pre_touch || AlwaysPreTouch) {
    os::pretouch_memory(previous_high, unaligned_new_high);
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

void VirtualSpace::print_on(outputStream* out) {
  out->print   ("Virtual space:");
  if (special()) out->print(" (pinned in memory)");
  out->cr();
  out->print_cr(" - committed: " SIZE_FORMAT, committed_size());
  out->print_cr(" - reserved:  " SIZE_FORMAT, reserved_size());
  out->print_cr(" - [low, high]:     [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  p2i(low()), p2i(high()));
  out->print_cr(" - [low_b, high_b]: [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",  p2i(low_boundary()), p2i(high_boundary()));
}

void VirtualSpace::print() {
  print_on(tty);
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
                     (char *)NULL); // requested_address

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

#define assert_equals(actual, expected)  \
  assert(actual == expected,             \
         "Got " SIZE_FORMAT " expected " \
         SIZE_FORMAT, actual, expected);

#define assert_ge(value1, value2)                  \
  assert(value1 >= value2,                         \
         "'" #value1 "': " SIZE_FORMAT " '"        \
         #value2 "': " SIZE_FORMAT, value1, value2);

#define assert_lt(value1, value2)                  \
  assert(value1 < value2,                          \
         "'" #value1 "': " SIZE_FORMAT " '"        \
         #value2 "': " SIZE_FORMAT, value1, value2);


class TestVirtualSpace : AllStatic {
  enum TestLargePages {
    Default,
    Disable,
    Reserve,
    Commit
  };

  static ReservedSpace reserve_memory(size_t reserve_size_aligned, TestLargePages mode) {
    switch(mode) {
    default:
    case Default:
    case Reserve:
      return ReservedSpace(reserve_size_aligned);
    case Disable:
    case Commit:
      return ReservedSpace(reserve_size_aligned,
                           os::vm_allocation_granularity(),
                           /* large */ false, /* exec */ false);
    }
  }

  static bool initialize_virtual_space(VirtualSpace& vs, ReservedSpace rs, TestLargePages mode) {
    switch(mode) {
    default:
    case Default:
    case Reserve:
      return vs.initialize(rs, 0);
    case Disable:
      return vs.initialize_with_granularity(rs, 0, os::vm_page_size());
    case Commit:
      return vs.initialize_with_granularity(rs, 0, os::page_size_for_region_unaligned(rs.size(), 1));
    }
  }

 public:
  static void test_virtual_space_actual_committed_space(size_t reserve_size, size_t commit_size,
                                                        TestLargePages mode = Default) {
    size_t granularity = os::vm_allocation_granularity();
    size_t reserve_size_aligned = align_size_up(reserve_size, granularity);

    ReservedSpace reserved = reserve_memory(reserve_size_aligned, mode);

    assert(reserved.is_reserved(), "Must be");

    VirtualSpace vs;
    bool initialized = initialize_virtual_space(vs, reserved, mode);
    assert(initialized, "Failed to initialize VirtualSpace");

    vs.expand_by(commit_size, false);

    if (vs.special()) {
      assert_equals(vs.actual_committed_size(), reserve_size_aligned);
    } else {
      assert_ge(vs.actual_committed_size(), commit_size);
      // Approximate the commit granularity.
      // Make sure that we don't commit using large pages
      // if large pages has been disabled for this VirtualSpace.
      size_t commit_granularity = (mode == Disable || !UseLargePages) ?
                                   os::vm_page_size() : os::large_page_size();
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

  static void test_virtual_space_disable_large_pages() {
    if (!UseLargePages) {
      return;
    }
    // These test cases verify that if we force VirtualSpace to disable large pages
    test_virtual_space_actual_committed_space(10 * M, 0, Disable);
    test_virtual_space_actual_committed_space(10 * M, 4 * K, Disable);
    test_virtual_space_actual_committed_space(10 * M, 8 * K, Disable);
    test_virtual_space_actual_committed_space(10 * M, 1 * M, Disable);
    test_virtual_space_actual_committed_space(10 * M, 2 * M, Disable);
    test_virtual_space_actual_committed_space(10 * M, 5 * M, Disable);
    test_virtual_space_actual_committed_space(10 * M, 10 * M, Disable);

    test_virtual_space_actual_committed_space(10 * M, 0, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 4 * K, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 8 * K, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 1 * M, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 2 * M, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 5 * M, Reserve);
    test_virtual_space_actual_committed_space(10 * M, 10 * M, Reserve);

    test_virtual_space_actual_committed_space(10 * M, 0, Commit);
    test_virtual_space_actual_committed_space(10 * M, 4 * K, Commit);
    test_virtual_space_actual_committed_space(10 * M, 8 * K, Commit);
    test_virtual_space_actual_committed_space(10 * M, 1 * M, Commit);
    test_virtual_space_actual_committed_space(10 * M, 2 * M, Commit);
    test_virtual_space_actual_committed_space(10 * M, 5 * M, Commit);
    test_virtual_space_actual_committed_space(10 * M, 10 * M, Commit);
  }

  static void test_virtual_space() {
    test_virtual_space_actual_committed_space();
    test_virtual_space_actual_committed_space_one_large_page();
    test_virtual_space_disable_large_pages();
  }
};

void TestVirtualSpace_test() {
  TestVirtualSpace::test_virtual_space();
}

#endif // PRODUCT

#endif
