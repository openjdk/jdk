/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/z/zAdaptiveHeap.inline.hpp"
#include "gc/z/zLargePages.hpp"
#include "hugepages.hpp"
#include "os_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"

#include <sys/mman.h>

// Define MADV_COLLAPSE here so we can build HotSpot on old systems.
#define MADV_COLLAPSE_value 25
#ifndef MADV_COLLAPSE
#define MADV_COLLAPSE MADV_COLLAPSE_value
#else
  // Sanity-check our assumed default value if we build with a new enough libc.
  STATIC_ASSERT(MADV_COLLAPSE == MADV_COLLAPSE_value);
#endif

bool ZLargePages::pd_collapse(void* addr, size_t bytes) {
  // When MADV_COLLAPSE races with THP khugepaged, you sometimes get
  // EAGAIN. We just do it again then.
  // Note: mlock, mlockall, DMA / GUP with FOLL_PIN/FOLL_LONGTERM can
  //       can all cause EAGAIN to be persistent. We treat this as a
  //       user error, and allow a calling thread to spin forever here.
  //       We may need to evaluate potential live-locking here in the future.
  for (;;) {
    const int result = ::madvise(addr, bytes, MADV_COLLAPSE);
    if (result == 0) {
      return true;
    }
    if (result == -1 && errno == EAGAIN) {
      continue;
    }

    return false;
  }
}

static bool madv_collapse_available() {
  const size_t size = 2 * M;
  void* const res = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);

  if (res == MAP_FAILED) {
    return false;
  }

  assert(size >= os::vm_page_size(), "Unexpected page size");
  os::pretouch_memory(res, (void*)(((char*)res) + os::vm_page_size()));

  const bool result = ZLargePages::pd_collapse(res, size);

  munmap(res, size);

  return result;
}

void ZLargePages::pd_initialize() {
  // Evaluate if we should move madv collapse logic to os::Linux / HugePages so
  // that validate_thps_configured can log the warning correctly if shmem THP
  // is disabled.
  // Maybe combined with a general GC callback. Let os::Linux / HugePages contain
  // the OS logic, let the GC do the extra log warnings as part of the
  // validation callback.
  const auto can_collapse = []() {
    return ZMemoryHeating && madv_collapse_available();
  };

  if (os::Linux::thp_requested()) {
    if (can_collapse()) {
      _state = Collapse;
      return;
    }
    // Check if the OS config turned off transparent huge pages for shmem.
    _os_enforced_transparent_mode = HugePages::shmem_thp_info().is_disabled();
    _state = _os_enforced_transparent_mode ? Disabled : Transparent;
    return;
  }

  if (UseLargePages) {
    if (!ZAdaptiveHeapSizing || ZAdaptiveHeap::explicit_max_capacity()) {
      _state = Explicit;
      return;
    }

    log_warning(gc, init)("UseLargePages requires a max heap size to be set (-Xmx) when running with Adaptive Heap Sizing. "
                          "Disabling the use of explicit large pages for the heap");
  }

  // Because os::Linux::large_page_init does not set the origin, we cannot tell
  // whether the user explicitly disabled large pages or explicitly requested
  // large pages which the OS configuration did not support.
  // Look into adding a similar cached user request as os::Linux::thp_requested
  // for UseLargePages.
  const bool large_pages_explicitly_disabled = !FLAG_IS_DEFAULT(UseLargePages) && !UseLargePages;
  const bool thp_explicitly_disabled = !FLAG_IS_DEFAULT(UseTransparentHugePages) && !UseTransparentHugePages;

  if (!large_pages_explicitly_disabled && !thp_explicitly_disabled && can_collapse()) {
    _state = Collapse;
    return;
  }

  // Check if the OS config turned on transparent huge pages for shmem.
  _os_enforced_transparent_mode = HugePages::shmem_thp_info().is_forced();
  _state = _os_enforced_transparent_mode ? Transparent : Disabled;
}
