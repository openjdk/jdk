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

#include "logging/log.hpp"
#include "memory/memoryReserver.hpp"
#include "oops/compressedOops.hpp"
#include "oops/markWord.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

static void sanity_check_size_and_alignment(size_t size, size_t alignment) {
  assert(size > 0, "Precondition");

  DEBUG_ONLY(const size_t granularity = os::vm_allocation_granularity());
  assert(is_aligned(size, granularity), "size not aligned to os::vm_allocation_granularity()");

  assert(alignment >= granularity, "Must be set");
  assert(is_power_of_2(alignment), "not a power of 2");
  assert(is_aligned(alignment, granularity), "alignment not aligned to os::vm_allocation_granularity()");
}

static void sanity_check_page_size(size_t page_size) {
  assert(page_size >= os::vm_page_size(), "Invalid page size");
  assert(is_power_of_2(page_size), "Invalid page size");
}

static void sanity_check_arguments(size_t size, size_t alignment, size_t page_size) {
  sanity_check_size_and_alignment(size, alignment);
  sanity_check_page_size(page_size);
}

static bool large_pages_requested() {
  return UseLargePages &&
         (!FLAG_IS_DEFAULT(UseLargePages) || !FLAG_IS_DEFAULT(LargePageSizeInBytes));
}

static void log_on_large_pages_failure(char* req_addr, size_t bytes) {
  if (large_pages_requested()) {
    // Compressed oops logging.
    log_debug(gc, heap, coops)("Reserve regular memory without large pages");
    // JVM style warning that we did not succeed in using large pages.
    warning("Failed to reserve and commit memory using large pages. "
            "req_addr: " PTR_FORMAT " bytes: %zu",
            p2i(req_addr), bytes);
  }
}

static bool use_explicit_large_pages(size_t page_size) {
  return !os::can_commit_large_page_memory() &&
         page_size != os::vm_page_size();
}

static char* reserve_memory_inner(char* requested_address,
                                  size_t size,
                                  size_t alignment,
                                  bool exec,
                                  MemTag mem_tag) {
  // If the memory was requested at a particular address, use
  // os::attempt_reserve_memory_at() to avoid mapping over something
  // important.  If the reservation fails, return null.
  if (requested_address != nullptr) {
    assert(is_aligned(requested_address, alignment),
           "Requested address " PTR_FORMAT " must be aligned to %zu",
           p2i(requested_address), alignment);
    return os::attempt_reserve_memory_at(requested_address, size, mem_tag, exec);
  }

  // Optimistically assume that the OS returns an aligned base pointer.
  // When reserving a large address range, most OSes seem to align to at
  // least 64K.
  char* base = os::reserve_memory(size, mem_tag, exec);
  if (is_aligned(base, alignment)) {
    return base;
  }

  // Base not aligned, retry.
  os::release_memory(base, size);

  // Map using the requested alignment.
  return os::reserve_memory_aligned(size, alignment, mem_tag, exec);
}

ReservedSpace MemoryReserver::reserve_memory(char* requested_address,
                                             size_t size,
                                             size_t alignment,
                                             size_t page_size,
                                             bool exec,
                                             MemTag mem_tag) {
  char* base = reserve_memory_inner(requested_address, size, alignment, exec, mem_tag);

  if (base != nullptr) {
    return ReservedSpace(base, size, alignment, page_size, exec, false /* special */);
  }

  // Failed
  return {};
}

ReservedSpace MemoryReserver::reserve_memory_special(char* requested_address,
                                                     size_t size,
                                                     size_t alignment,
                                                     size_t page_size,
                                                     bool exec) {
  log_trace(pagesize)("Attempt special mapping: size: " EXACTFMT ", alignment: " EXACTFMT,
                      EXACTFMTARGS(size),
                      EXACTFMTARGS(alignment));

  char* base = os::reserve_memory_special(size, alignment, page_size, requested_address, exec);

  if (base != nullptr) {
    assert(is_aligned(base, alignment),
           "reserve_memory_special() returned an unaligned address, "
           "base: " PTR_FORMAT " alignment: 0x%zx",
           p2i(base), alignment);

    return ReservedSpace(base, size, alignment, page_size, exec, true /* special */);
  }

  // Failed
  return {};
}

ReservedSpace MemoryReserver::reserve(char* requested_address,
                                      size_t size,
                                      size_t alignment,
                                      size_t page_size,
                                      bool executable,
                                      MemTag mem_tag) {
  sanity_check_arguments(size, alignment, page_size);

  // Reserve the memory.

  // There are basically three different cases that we need to handle:
  // 1. Mapping backed by a file
  // 2. Mapping backed by explicit large pages
  // 3. Mapping backed by normal pages or transparent huge pages
  // The first two have restrictions that requires the whole mapping to be
  // committed up front. To record this the ReservedSpace is marked 'special'.

  // == Case 1 ==
  // This case is contained within the HeapReserver

  // == Case 2 ==
  if (use_explicit_large_pages(page_size)) {
    // System can't commit large pages i.e. use transparent huge pages and
    // the caller requested large pages. To satisfy this request we use
    // explicit large pages and these have to be committed up front to ensure
    // no reservations are lost.
    do {
      ReservedSpace reserved = reserve_memory_special(requested_address, size, alignment, page_size, executable);
      if (reserved.is_reserved()) {
        // Successful reservation using large pages.
        return reserved;
      }
      page_size = os::page_sizes().next_smaller(page_size);
    } while (page_size > os::vm_page_size());

    // Failed to reserve explicit large pages, do proper logging.
    log_on_large_pages_failure(requested_address, size);
    // Now fall back to normal reservation.
    assert(page_size == os::vm_page_size(), "inv");
  }

  // == Case 3 ==
  return reserve_memory(requested_address, size, alignment, page_size, executable, mem_tag);
}

ReservedSpace MemoryReserver::reserve(char* requested_address,
                                      size_t size,
                                      size_t alignment,
                                      size_t page_size,
                                      MemTag mem_tag) {
  return reserve(requested_address,
                 size,
                 alignment,
                 page_size,
                 !ExecMem,
                 mem_tag);
}


ReservedSpace MemoryReserver::reserve(size_t size,
                                      size_t alignment,
                                      size_t page_size,
                                      MemTag mem_tag) {
  return reserve(nullptr /* requested_address */,
                 size,
                 alignment,
                 page_size,
                 mem_tag);
}

ReservedSpace MemoryReserver::reserve(size_t size,
                                      MemTag mem_tag) {
  // Want to use large pages where possible. If the size is
  // not large page aligned the mapping will be a mix of
  // large and normal pages.
  size_t page_size = os::page_size_for_region_unaligned(size, 1);
  size_t alignment = os::vm_allocation_granularity();

  return reserve(size,
                 alignment,
                 page_size,
                 mem_tag);
}

void MemoryReserver::release(const ReservedSpace& reserved) {
  assert(reserved.is_reserved(), "Precondition");

  if (reserved.special()) {
    os::release_memory_special(reserved.base(), reserved.size());
  } else {
    os::release_memory(reserved.base(), reserved.size());
  }
}

static char* map_memory_to_file(char* requested_address,
                                size_t size,
                                size_t alignment,
                                int fd,
                                MemTag mem_tag) {
  // If the memory was requested at a particular address, use
  // os::attempt_reserve_memory_at() to avoid mapping over something
  // important.  If the reservation fails, return null.
  if (requested_address != nullptr) {
    assert(is_aligned(requested_address, alignment),
           "Requested address " PTR_FORMAT " must be aligned to %zu",
           p2i(requested_address), alignment);
    return os::attempt_map_memory_to_file_at(requested_address, size, fd, mem_tag);
  }

  // Optimistically assume that the OS returns an aligned base pointer.
  // When reserving a large address range, most OSes seem to align to at
  // least 64K.
  char* base = os::map_memory_to_file(size, fd, mem_tag);
  if (is_aligned(base, alignment)) {
    return base;
  }


  // Base not aligned, retry.
  os::unmap_memory(base, size);

  // Map using the requested alignment.
  return os::map_memory_to_file_aligned(size, alignment, fd, mem_tag);
}

ReservedSpace FileMappedMemoryReserver::reserve(char* requested_address,
                                                size_t size,
                                                size_t alignment,
                                                int fd,
                                                MemTag mem_tag) {
  sanity_check_size_and_alignment(size, alignment);

  char* base = map_memory_to_file(requested_address, size, alignment, fd, mem_tag);

  if (base != nullptr) {
    return ReservedSpace(base, size, alignment, os::vm_page_size(), !ExecMem, true /* special */);
  }

  // Failed
  return {};
}

ReservedSpace CodeMemoryReserver::reserve(size_t size,
                                          size_t alignment,
                                          size_t page_size) {
  return MemoryReserver::reserve(nullptr /* requested_address */,
                                 size,
                                 alignment,
                                 page_size,
                                 ExecMem,
                                 mtCode);
}

ReservedHeapSpace HeapReserver::Instance::reserve_uncompressed_oops_heap(size_t size,
                                                                         size_t alignment,
                                                                         size_t page_size) {
  ReservedSpace reserved = reserve_memory(size, alignment, page_size);

  if (reserved.is_reserved()) {
    return ReservedHeapSpace(reserved, 0 /* noaccess_prefix */);
  }

  // Failed
  return {};
}


static int maybe_create_file(const char* heap_allocation_directory) {
  if (heap_allocation_directory == nullptr) {
    return -1;
  }

  int fd = os::create_file_for_heap(heap_allocation_directory);
  if (fd == -1) {
    vm_exit_during_initialization(
        err_msg("Could not create file for Heap at location %s", heap_allocation_directory));
  }

  return fd;
}

HeapReserver::Instance::Instance(const char* heap_allocation_directory)
  : _fd(maybe_create_file(heap_allocation_directory)) {}

HeapReserver::Instance::~Instance() {
  if (_fd != -1) {
    ::close(_fd);
  }
}

ReservedSpace HeapReserver::Instance::reserve_memory(size_t size,
                                                     size_t alignment,
                                                     size_t page_size,
                                                     char* requested_address) {

  // There are basically three different cases that we need to handle below:
  // 1. Mapping backed by a file
  // 2. Mapping backed by explicit large pages
  // 3. Mapping backed by normal pages or transparent huge pages
  // The first two have restrictions that requires the whole mapping to be
  // committed up front. To record this the ReservedSpace is marked 'special'.

  // == Case 1 ==
  if (_fd != -1) {
    // When there is a backing file directory for this space then whether
    // large pages are allocated is up to the filesystem of the backing file.
    // So UseLargePages is not taken into account for this reservation.
    //
    // If requested, let the user know that explicit large pages can't be used.
    if (use_explicit_large_pages(page_size) && large_pages_requested()) {
      log_debug(gc, heap)("Cannot allocate explicit large pages for Java Heap when AllocateHeapAt option is set.");
    }

    // Always return, not possible to fall back to reservation not using a file.
    return FileMappedMemoryReserver::reserve(requested_address, size, alignment, _fd, mtJavaHeap);
  }

  // == Case 2 & 3 ==
  return MemoryReserver::reserve(requested_address, size, alignment, page_size, mtJavaHeap);
}

// Compressed oop support is not relevant in 32bit builds.
#ifdef _LP64

void HeapReserver::Instance::release(const ReservedSpace& reserved) {
  if (reserved.is_reserved()) {
    if (_fd == -1) {
      if (reserved.special()) {
        os::release_memory_special(reserved.base(), reserved.size());
      } else{
        os::release_memory(reserved.base(), reserved.size());
      }
    } else {
      os::unmap_memory(reserved.base(), reserved.size());
    }
  }
}

// Tries to allocate memory of size 'size' at address requested_address with alignment 'alignment'.
// Does not check whether the reserved memory actually is at requested_address, as the memory returned
// might still fulfill the wishes of the caller.
// Assures the memory is aligned to 'alignment'.
ReservedSpace HeapReserver::Instance::try_reserve_memory(size_t size,
                                                         size_t alignment,
                                                         size_t page_size,
                                                         char* requested_address) {
  // Try to reserve the memory for the heap.
  log_trace(gc, heap, coops)("Trying to allocate at address " PTR_FORMAT
                             " heap of size 0x%zx",
                             p2i(requested_address),
                             size);

  ReservedSpace reserved = reserve_memory(size, alignment, page_size, requested_address);

  if (reserved.is_reserved()) {
    // Check alignment constraints.
    assert(reserved.alignment() == alignment, "Unexpected");
    assert(is_aligned(reserved.base(), alignment), "Unexpected");
    return reserved;
  }

  // Failed
  return {};
}

ReservedSpace HeapReserver::Instance::try_reserve_range(char *highest_start,
                                                        char *lowest_start,
                                                        size_t attach_point_alignment,
                                                        char *aligned_heap_base_min_address,
                                                        char *upper_bound,
                                                        size_t size,
                                                        size_t alignment,
                                                        size_t page_size) {
  assert(is_aligned(highest_start, attach_point_alignment), "precondition");
  assert(is_aligned(lowest_start, attach_point_alignment), "precondition");

  const size_t attach_range = pointer_delta(highest_start, lowest_start, sizeof(char));
  const size_t num_attempts_possible = (attach_range / attach_point_alignment) + 1;
  const size_t num_attempts_to_try   = MIN2((size_t)HeapSearchSteps, num_attempts_possible);
  const size_t num_intervals = num_attempts_to_try - 1;
  const size_t stepsize = num_intervals == 0 ? 0 : align_down(attach_range / num_intervals, attach_point_alignment);

  for (size_t i = 0; i < num_attempts_to_try; ++i) {
    char* const attach_point = highest_start - stepsize * i;
    ReservedSpace reserved = try_reserve_memory(size, alignment, page_size, attach_point);

    if (reserved.is_reserved()) {
      if (reserved.base() >= aligned_heap_base_min_address &&
          size <= (size_t)(upper_bound - reserved.base())) {
        // Got a successful reservation.
        return reserved;
      }

      release(reserved);
    }
  }

  // Failed
  return {};
}

#define SIZE_64K  ((uint64_t) UCONST64(      0x10000))
#define SIZE_256M ((uint64_t) UCONST64(   0x10000000))
#define SIZE_32G  ((uint64_t) UCONST64(  0x800000000))

// Helper for heap allocation. Returns an array with addresses
// (OS-specific) which are suited for disjoint base mode. Array is
// null terminated.
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

// Create protection page at the beginning of the space.
static ReservedSpace establish_noaccess_prefix(const ReservedSpace& reserved, size_t noaccess_prefix) {
  assert(reserved.alignment() >= os::vm_page_size(), "must be at least page size big");
  assert(reserved.is_reserved(), "should only be called on a reserved memory area");

  if (reserved.end() > (char *)OopEncodingHeapMax) {
    if (true
        WIN64_ONLY(&& !UseLargePages)
        AIX_ONLY(&& (os::Aix::supports_64K_mmap_pages() || os::vm_page_size() == 4*K))) {
      // Protect memory at the base of the allocated region.
      if (!os::protect_memory(reserved.base(), noaccess_prefix, os::MEM_PROT_NONE, reserved.special())) {
        fatal("cannot protect protection page");
      }
      log_debug(gc, heap, coops)("Protected page at the reserved heap base: "
                                 PTR_FORMAT " / %zd bytes",
                                 p2i(reserved.base()),
                                 noaccess_prefix);
      assert(CompressedOops::use_implicit_null_checks() == true, "not initialized?");
    } else {
      CompressedOops::set_use_implicit_null_checks(false);
    }
  }

  return reserved.last_part(noaccess_prefix);
}

ReservedHeapSpace HeapReserver::Instance::reserve_compressed_oops_heap(const size_t size, size_t alignment, size_t page_size) {
  const size_t noaccess_prefix_size = lcm(os::vm_page_size(), alignment);
  const size_t granularity = os::vm_allocation_granularity();

  assert(size + noaccess_prefix_size <= OopEncodingHeapMax,  "can not allocate compressed oop heap for this size");
  assert(is_aligned(size, granularity), "size not aligned to os::vm_allocation_granularity()");

  assert(alignment >= os::vm_page_size(), "alignment too small");
  assert(is_aligned(alignment, granularity), "alignment not aligned to os::vm_allocation_granularity()");
  assert(is_power_of_2(alignment), "not a power of 2");

  // The necessary attach point alignment for generated wish addresses.
  // This is needed to increase the chance of attaching for mmap and shmat.
  // AIX is the only platform that uses System V shm for reserving virtual memory.
  // In this case, the required alignment of the allocated size (64K) and the alignment
  // of possible start points of the memory region (256M) differ.
  // This is not reflected by os_allocation_granularity().
  // The logic here is dual to the one in pd_reserve_memory in os_aix.cpp
  const size_t os_attach_point_alignment =
    AIX_ONLY(os::vm_page_size() == 4*K ? 4*K : 256*M)
    NOT_AIX(os::vm_allocation_granularity());

  const size_t attach_point_alignment = lcm(alignment, os_attach_point_alignment);

  uintptr_t aligned_heap_base_min_address = align_up(MAX2(HeapBaseMinAddress, alignment), alignment);
  size_t noaccess_prefix = ((aligned_heap_base_min_address + size) > OopEncodingHeapMax) ?
    noaccess_prefix_size : 0;

  ReservedSpace reserved{};

  // Attempt to alloc at user-given address.
  if (!FLAG_IS_DEFAULT(HeapBaseMinAddress)) {
    reserved = try_reserve_memory(size + noaccess_prefix, alignment, page_size, (char*)aligned_heap_base_min_address);
    if (reserved.base() != (char*)aligned_heap_base_min_address) { // Enforce this exact address.
      release(reserved);
      reserved = {};
    }
  }

  // Keep heap at HeapBaseMinAddress.
  if (!reserved.is_reserved()) {

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
    if (aligned_heap_base_min_address + size <= UnscaledOopHeapMax) {

      // Calc address range within we try to attach (range of possible start addresses).
      uintptr_t const highest_start = align_down(UnscaledOopHeapMax - size, attach_point_alignment);
      uintptr_t const lowest_start  = align_up(aligned_heap_base_min_address, attach_point_alignment);
      assert(lowest_start <= highest_start, "lowest: " INTPTR_FORMAT " highest: " INTPTR_FORMAT ,
                                          lowest_start, highest_start);
      reserved = try_reserve_range((char*)highest_start, (char*)lowest_start, attach_point_alignment,
                                   (char*)aligned_heap_base_min_address, (char*)UnscaledOopHeapMax, size, alignment, page_size);
    }

    // zerobased: Attempt to allocate in the lower 32G.
    const uintptr_t zerobased_max = OopEncodingHeapMax;

    // Give it several tries from top of range to bottom.
    if (aligned_heap_base_min_address + size <= zerobased_max && // Zerobased theoretical possible.
        ((!reserved.is_reserved()) ||                            // No previous try succeeded.
         (reserved.end() > (char*)zerobased_max))) {             // Unscaled delivered an arbitrary address.

      // Release previous reservation
      release(reserved);

      // Calc address range within we try to attach (range of possible start addresses).
      uintptr_t const highest_start = align_down(zerobased_max - size, attach_point_alignment);
      // Need to be careful about size being guaranteed to be less
      // than UnscaledOopHeapMax due to type constraints.
      uintptr_t lowest_start = aligned_heap_base_min_address;
      if (size < UnscaledOopHeapMax) {
        lowest_start = MAX2<uintptr_t>(lowest_start, UnscaledOopHeapMax - size);
      }
      lowest_start = align_up(lowest_start, attach_point_alignment);
      assert(lowest_start <= highest_start, "lowest: " INTPTR_FORMAT " highest: " INTPTR_FORMAT,
                                          lowest_start, highest_start);
      reserved = try_reserve_range((char*)highest_start, (char*)lowest_start, attach_point_alignment,
                                   (char*)aligned_heap_base_min_address, (char*)zerobased_max, size, alignment, page_size);
    }

    // Now we go for heaps with base != 0.  We need a noaccess prefix to efficiently
    // implement null checks.
    noaccess_prefix = noaccess_prefix_size;

    // Try to attach at addresses that are aligned to OopEncodingHeapMax. Disjointbase mode.
    char** addresses = get_attach_addresses_for_disjoint_mode();
    int i = 0;
    while ((addresses[i] != nullptr) &&              // End of array not yet reached.
           ((!reserved.is_reserved()) ||             // No previous try succeeded.
           (reserved.end() > (char*)zerobased_max && // Not zerobased or unscaled address.
                                                     // Not disjoint address.
            !CompressedOops::is_disjoint_heap_base_address((address)reserved.base())))) {

      // Release previous reservation
      release(reserved);

      char* const attach_point = addresses[i];
      assert((uintptr_t)attach_point >= aligned_heap_base_min_address, "Flag support broken");
      reserved = try_reserve_memory(size + noaccess_prefix, alignment, page_size, attach_point);
      i++;
    }

    // Last, desperate try without any placement.
    if (!reserved.is_reserved()) {
      log_trace(gc, heap, coops)("Trying to allocate at address null heap of size 0x%zx", size + noaccess_prefix);
      assert(alignment >= os::vm_page_size(), "Unexpected");
      reserved = reserve_memory(size + noaccess_prefix, alignment, page_size);
    }
  }

  // No more reserve attempts

  if (reserved.is_reserved()) {
    // Successfully found and reserved memory for the heap.

    if (reserved.size() > size) {
      // We reserved heap memory with a noaccess prefix.

      assert(reserved.size() == size + noaccess_prefix, "Prefix should be included");
      // It can happen we get a zerobased/unscaled heap with noaccess prefix,
      // if we had to try at arbitrary address.
      reserved = establish_noaccess_prefix(reserved, noaccess_prefix);
      assert(reserved.size() == size, "Prefix should be gone");
      return ReservedHeapSpace(reserved, noaccess_prefix);
    }

    // We reserved heap memory without a noaccess prefix.
    return ReservedHeapSpace(reserved, 0 /* noaccess_prefix */);
  }

  // Failed
  return {};
}

#endif // _LP64

ReservedHeapSpace HeapReserver::Instance::reserve_heap(size_t size, size_t alignment, size_t page_size) {
  if (UseCompressedOops) {
#ifdef _LP64
    return reserve_compressed_oops_heap(size, alignment, page_size);
#endif
  } else {
    return reserve_uncompressed_oops_heap(size, alignment, page_size);
  }
}

ReservedHeapSpace HeapReserver::reserve(size_t size, size_t alignment, size_t page_size, const char* heap_allocation_directory) {
  sanity_check_arguments(size, alignment, page_size);

  assert(alignment != 0, "Precondition");
  assert(is_aligned(size, alignment), "Precondition");

  Instance instance(heap_allocation_directory);

  return instance.reserve_heap(size, alignment, page_size);
}
