/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_KLASSINFOLUT_INLINE_HPP
#define SHARE_OOPS_KLASSINFOLUT_INLINE_HPP

#include "oops/compressedKlass.inline.hpp"
#include "oops/klassInfoLUT.hpp"
#include "oops/klassInfoLUTEntry.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/debug.hpp"

ALWAYSINLINE uint32_t KlassInfoLUT::at(unsigned index) {
  assert(_entries != nullptr, "LUT table does not exist");
  assert(index < num_entries(), "oob (%x vs %x)", index, num_entries());
  return _entries[index];
}

ALWAYSINLINE KlassLUTEntry KlassInfoLUT::lookup(narrowKlass nk) {

  const uint32_t v = at(nk);
  KlassLUTEntry e(v);
#if INCLUDE_CDS
  if (e.is_invalid()) {
    // This branch only exists because it is so very difficult to iterate CDS classes after loading
    // CDS archives. See discussion surrounding 8353225. Hopefully we can remove this in the future.
    // And equally hopefully branch prediction takes the sting out of this branch in real oop iteration.
    // In order to measure without this branch, build without CDS.
    return late_register_klass(nk);
  }
#else
  assert(!e.is_invalid(), "must never be invalid");
#endif

#ifdef KLUT_ENABLE_EXPENSIVE_STATS
  update_hit_stats(e);
#endif

#ifdef KLUT_ENABLE_EXPENSIVE_LOG
  log_hit(e);
#endif

  return e;
}

ALWAYSINLINE ClassLoaderData* KlassInfoLUT::get_perma_cld(int index) {
  assert(index >= 1 && index <= 3, "Sanity");
  return _common_loaders[index];
}

#endif // SHARE_OOPS_KLASSINFOLUT_INLINE_HPP
