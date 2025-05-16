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

#include "gc/shared/gc_globals.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"

// VirtualSpace

VirtualSpace::VirtualSpace() {
  _low_boundary           = nullptr;
  _high_boundary          = nullptr;
  _low                    = nullptr;
  _high                   = nullptr;
  _lower_high             = nullptr;
  _middle_high            = nullptr;
  _upper_high             = nullptr;
  _lower_high_boundary    = nullptr;
  _middle_high_boundary   = nullptr;
  _upper_high_boundary    = nullptr;
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
  assert(rs.is_reserved(), "ReservedSpace should have been initialized");
  assert(_low_boundary == nullptr, "VirtualSpace already initialized");
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
  _lower_high_boundary = align_up(low_boundary(), middle_alignment());
  _middle_high_boundary = align_down(high_boundary(), middle_alignment());
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
  // This does not release memory it reserved.
  // Caller must release via rs.release();
  _low_boundary           = nullptr;
  _high_boundary          = nullptr;
  _low                    = nullptr;
  _high                   = nullptr;
  _lower_high             = nullptr;
  _middle_high            = nullptr;
  _upper_high             = nullptr;
  _lower_high_boundary    = nullptr;
  _middle_high_boundary   = nullptr;
  _upper_high_boundary    = nullptr;
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

static void pretouch_expanded_memory(void* start, void* end) {
  assert(is_aligned(start, os::vm_page_size()), "Unexpected alignment");
  assert(is_aligned(end,   os::vm_page_size()), "Unexpected alignment");

  os::pretouch_memory(start, end);
}

static bool commit_expanded(char* start, size_t size, size_t alignment, bool pre_touch, bool executable) {
  if (os::commit_memory(start, size, alignment, executable)) {
    if (pre_touch || AlwaysPreTouch) {
      pretouch_expanded_memory(start, start + size);
    }
    return true;
  }

  DEBUG_ONLY(warning(
      "INFO: os::commit_memory(" PTR_FORMAT ", " PTR_FORMAT
      " size=%zu, executable=%d) failed",
      p2i(start), p2i(start + size), size, executable);)

  return false;
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
  if (uncommitted_size() < bytes) {
    return false;
  }

  if (special()) {
    // don't commit memory if the entire space is pinned in memory
    _high += bytes;
    return true;
  }

  char* previous_high = high();
  char* unaligned_new_high = high() + bytes;
  assert(unaligned_new_high <= high_boundary(), "cannot expand by more than upper boundary");

  // Calculate where the new high for each of the regions should be.  If
  // the low_boundary() and high_boundary() are LargePageSizeInBytes aligned
  // then the unaligned lower and upper new highs would be the
  // lower_high() and upper_high() respectively.
  char* unaligned_lower_new_high =  MIN2(unaligned_new_high, lower_high_boundary());
  char* unaligned_middle_new_high = MIN2(unaligned_new_high, middle_high_boundary());
  char* unaligned_upper_new_high =  MIN2(unaligned_new_high, upper_high_boundary());

  // Align the new highs based on the regions alignment.  lower and upper
  // alignment will always be default page size.  middle alignment will be
  // LargePageSizeInBytes if the actual size of the virtual space is in
  // fact larger than LargePageSizeInBytes.
  char* aligned_lower_new_high =  align_up(unaligned_lower_new_high, lower_alignment());
  char* aligned_middle_new_high = align_up(unaligned_middle_new_high, middle_alignment());
  char* aligned_upper_new_high =  align_up(unaligned_upper_new_high, upper_alignment());

  // Determine which regions need to grow in this expand_by call.
  // If you are growing in the lower region, high() must be in that
  // region so calculate the size based on high().  For the middle and
  // upper regions, determine the starting point of growth based on the
  // location of high().  By getting the MAX of the region's low address
  // (or the previous region's high address) and high(), we can tell if it
  // is an intra or inter region growth.
  size_t lower_needs = 0;
  if (aligned_lower_new_high > lower_high()) {
    lower_needs = pointer_delta(aligned_lower_new_high, lower_high(), sizeof(char));
  }
  size_t middle_needs = 0;
  if (aligned_middle_new_high > middle_high()) {
    middle_needs = pointer_delta(aligned_middle_new_high, middle_high(), sizeof(char));
  }
  size_t upper_needs = 0;
  if (aligned_upper_new_high > upper_high()) {
    upper_needs = pointer_delta(aligned_upper_new_high, upper_high(), sizeof(char));
  }

  // Check contiguity.
  assert(low_boundary() <= lower_high() && lower_high() <= lower_high_boundary(),
         "high address must be contained within the region");
  assert(lower_high_boundary() <= middle_high() && middle_high() <= middle_high_boundary(),
         "high address must be contained within the region");
  assert(middle_high_boundary() <= upper_high() && upper_high() <= upper_high_boundary(),
         "high address must be contained within the region");

  // Commit regions
  if (lower_needs > 0) {
    assert(lower_high() + lower_needs <= lower_high_boundary(), "must not expand beyond region");
    if (!commit_expanded(lower_high(), lower_needs, _lower_alignment, pre_touch, _executable)) {
      return false;
    }
    _lower_high += lower_needs;
  }

  if (middle_needs > 0) {
    assert(middle_high() + middle_needs <= middle_high_boundary(), "must not expand beyond region");
    if (!commit_expanded(middle_high(), middle_needs, _middle_alignment, pre_touch, _executable)) {
      return false;
    }
    _middle_high += middle_needs;
  }

  if (upper_needs > 0) {
    assert(upper_high() + upper_needs <= upper_high_boundary(), "must not expand beyond region");
    if (!commit_expanded(upper_high(), upper_needs, _upper_alignment, pre_touch, _executable)) {
      return false;
    }
    _upper_high += upper_needs;
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
  char* aligned_upper_new_high =  align_up(unaligned_upper_new_high, upper_alignment());
  char* aligned_middle_new_high = align_up(unaligned_middle_new_high, middle_alignment());
  char* aligned_lower_new_high =  align_up(unaligned_lower_new_high, lower_alignment());

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
    if (!os::uncommit_memory(aligned_upper_new_high, upper_needs, _executable)) {
      DEBUG_ONLY(warning("os::uncommit_memory failed"));
      return;
    } else {
      _upper_high -= upper_needs;
    }
  }
  if (middle_needs > 0) {
    assert(lower_high_boundary() <= aligned_middle_new_high &&
           aligned_middle_new_high + middle_needs <= middle_high_boundary(),
           "must not shrink beyond region");
    if (!os::uncommit_memory(aligned_middle_new_high, middle_needs, _executable)) {
      DEBUG_ONLY(warning("os::uncommit_memory failed"));
      return;
    } else {
      _middle_high -= middle_needs;
    }
  }
  if (lower_needs > 0) {
    assert(low_boundary() <= aligned_lower_new_high &&
           aligned_lower_new_high + lower_needs <= lower_high_boundary(),
           "must not shrink beyond region");
    if (!os::uncommit_memory(aligned_lower_new_high, lower_needs, _executable)) {
      DEBUG_ONLY(warning("os::uncommit_memory failed"));
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

void VirtualSpace::print_on(outputStream* out) const {
  out->print_cr("Virtual space:%s", special() ? " (pinned in memory)" : "");

  StreamIndentor si(out, 1);
  out->print_cr("- committed: %zu", committed_size());
  out->print_cr("- reserved:  %zu", reserved_size());
  out->print_cr("- [low, high]:     [" PTR_FORMAT ", " PTR_FORMAT "]",  p2i(low()), p2i(high()));
  out->print_cr("- [low_b, high_b]: [" PTR_FORMAT ", " PTR_FORMAT "]",  p2i(low_boundary()), p2i(high_boundary()));
}

void VirtualSpace::print() const {
  print_on(tty);
}

#endif

void VirtualSpace::print_space_boundaries_on(outputStream* out) const {
  out->print_cr("[" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
                p2i(low_boundary()), p2i(high()), p2i(high_boundary()));
}
