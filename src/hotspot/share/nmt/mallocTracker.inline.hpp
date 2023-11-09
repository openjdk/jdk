/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MALLOCTRACKER_INLINE_HPP
#define SHARE_NMT_MALLOCTRACKER_INLINE_HPP

#include "nmt/mallocTracker.hpp"
#include "services/mallocLimit.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Returns true if allocating s bytes on f would trigger either global or the category limit
inline bool MallocMemorySummary::check_exceeds_limit(size_t s, MEMFLAGS f) {

  // Note: checks are ordered to have as little impact as possible on the standard code path,
  // when MallocLimit is unset, resp. it is set but we have reached no limit yet.
  // Somewhat expensive are:
  // - as_snapshot()->total(), total malloc load (requires iteration over arena types)
  // - VMError::is_error_reported() is a load from a volatile.
  if (MallocLimitHandler::have_limit()) {

    // Global Limit ?
    const malloclimit* l = MallocLimitHandler::global_limit();
    if (l->sz > 0) {
      size_t so_far = as_snapshot()->total();
      if ((so_far + s) > l->sz) { // hit the limit
        return total_limit_reached(s, so_far, l);
      }
    } else {
      // Category Limit?
      l = MallocLimitHandler::category_limit(f);
      if (l->sz > 0) {
        const MallocMemory* mm = as_snapshot()->by_type(f);
        size_t so_far = mm->malloc_size() + mm->arena_size();
        if ((so_far + s) > l->sz) {
          return category_limit_reached(f, s, so_far, l);
        }
      }
    }
  }

  return false;
}

inline bool MallocTracker::check_exceeds_limit(size_t s, MEMFLAGS f) {
  return MallocMemorySummary::check_exceeds_limit(s, f);
}


#endif // SHARE_NMT_MALLOCTRACKER_INLINE_HPP
