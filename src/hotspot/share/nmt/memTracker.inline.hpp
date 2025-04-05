/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2025 Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_NMT_MEMTRACKER_INLINE_HPP
#define SHARE_NMT_MEMTRACKER_INLINE_HPP

#include "nmt/memTracker.hpp"
#include "nmt/nMemLimit.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "nmt/nMemoryLimitPrinter.hpp"
#include "utilities/vmError.hpp"
#include "logging/log.hpp"

// Returns true if allocating s bytes on f would trigger either global or the category limit
inline bool MallocMemorySummary::check_exceeds_limit(size_t s, MemTag mem_tag) {
  // Note: checks are ordered to have as little impact as possible on the standard code path,
  // when MallocLimit is unset, resp. it is set but we have reached no limit yet.
  // Somewhat expensive are:
  // - as_snapshot()->total(), total malloc load (requires iteration over arena types)
  // - category_limit_reached: uses VMError::is_error_reported(), which is a load from a volatile.
  if (NMemLimitHandler::have_limit(NMemType::Malloc)) {

    // Global Limit ?
    const nMemlimit* l = NMemLimitHandler::global_limit(NMemType::Malloc);
    if (l->sz > 0) {
      size_t so_far = MallocMemorySummary::as_snapshot()->total();
      if ((so_far + s) > l->sz) { // hit the limit
        return NMemoryLimitPrinter::total_limit_reached(s, so_far, l, NMemType::Malloc);
      }
    } else {
      // Category Limit?
      l = NMemLimitHandler::category_limit(mem_tag, NMemType::Malloc);
      if (l->sz > 0) {
        const MallocMemory* mm = MallocMemorySummary::as_snapshot()->by_tag(mem_tag);
        size_t so_far = mm->malloc_size() + mm->arena_size();
        if ((so_far + s) > l->sz) {
          return NMemoryLimitPrinter::category_limit_reached(mem_tag, s, so_far, l, NMemType::Malloc);
        }
      }
    }
  }

  return false;
}

// Returns true if allocating s bytes on f would trigger either global or the category limit
inline bool VirtualMemorySummary::check_exceeds_limit(size_t s, MemTag mem_tag) {
  // Note: checks are ordered to have as little impact as possible on the standard code path,
  // when MmapLimit is unset, resp. it is set but we have reached no limit yet.
  // Somewhat expensive are:
  // - as_snapshot()->total_committed()
  // - category_limit_reached: uses VMError::is_error_reported(), which is a load from a volatile.

  if (NMemLimitHandler::have_limit(NMemType::Mmap)) {

    // Global Limit ?
    const nMemlimit* l = NMemLimitHandler::global_limit(NMemType::Mmap);
    if (l->sz > 0) {
      size_t so_far = VirtualMemorySummary::as_snapshot()->total_committed();
      if ((so_far + s) > l->sz) { // hit the limit
        return NMemoryLimitPrinter::total_limit_reached(s, so_far, l, NMemType::Mmap);
      }
    } else {
      // Category Limit?
      l = NMemLimitHandler::category_limit(mem_tag, NMemType::Mmap);
      if (l->sz > 0) {
        const VirtualMemory* mm = VirtualMemorySummary::as_snapshot()->by_tag(mem_tag);
        size_t so_far = mm->committed();
        if ((so_far + s) > l->sz) {
          return NMemoryLimitPrinter::category_limit_reached(mem_tag, s, so_far, l, NMemType::Mmap);
        }
      }
    }
  }

  return false;
}

inline bool MallocTracker::check_exceeds_limit(size_t s, MemTag mem_tag) {
  return MallocMemorySummary::check_exceeds_limit(s, mem_tag);
}

inline bool VirtualMemoryTracker::check_exceeds_limit(size_t s, MemTag mem_tag) {
  return VirtualMemorySummary::check_exceeds_limit(s, mem_tag);
}

inline bool MemTracker::check_exceeds_limit(size_t s, MemTag mem_tag, NMemType type) {
  if (!enabled()) {
    return false;
  }

  if (NMemType::Malloc == type) {
    return MallocTracker::check_exceeds_limit(s, mem_tag);
  } else if (NMemType::Mmap == type) {
    return VirtualMemoryTracker::check_exceeds_limit(s, mem_tag);
  } else {
    ShouldNotReachHere();
  }
}

#endif // SHARE_NMT_MEMTRACKER_INLINE_HPP
