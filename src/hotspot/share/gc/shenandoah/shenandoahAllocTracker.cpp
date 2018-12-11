/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahAllocTracker.hpp"
#include "utilities/ostream.hpp"

void ShenandoahAllocTracker::print_on(outputStream* out) const {
  out->print_cr("ALLOCATION TRACING");
  out->print_cr("  These are the slow-path allocations, including TLAB/GCLAB refills, and out-of-TLAB allocations.");
  out->print_cr("  In-TLAB/GCLAB allocations happen orders of magnitude more frequently, and without delays.");
  out->cr();

  out->print("%22s", "");
  for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
    out->print("%12s", ShenandoahAllocRequest::alloc_type_to_string(ShenandoahAllocRequest::Type(t)));
  }
  out->cr();

  out->print_cr("Counts:");
  out->print("%22s", "#");
  for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
    out->print(SIZE_FORMAT_W(12), _alloc_size[t].num());
  }
  out->cr();
  out->cr();

  // Figure out max and min levels
  int lat_min_level = +1000;
  int lat_max_level = -1000;
  int size_min_level = +1000;
  int size_max_level = -1000;
  for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
    lat_min_level = MIN2(lat_min_level, _alloc_latency[t].min_level());
    lat_max_level = MAX2(lat_max_level, _alloc_latency[t].max_level());
    size_min_level = MIN2(size_min_level, _alloc_size[t].min_level());
    size_max_level = MAX2(size_max_level, _alloc_size[t].max_level());
  }

  out->print_cr("Latency summary:");
  out->print("%22s", "sum, ms:");
  for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
    out->print(SIZE_FORMAT_W(12), _alloc_latency[t].sum() / K);
  }
  out->cr();
  out->cr();

  out->print_cr("Sizes summary:");
  out->print("%22s", "sum, M:");
  for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
    out->print(SIZE_FORMAT_W(12), _alloc_size[t].sum() * HeapWordSize / M);
  }
  out->cr();
  out->cr();

  out->print_cr("Latency histogram (time in microseconds):");
  for (int c = lat_min_level; c <= lat_max_level; c++) {
    out->print("%9d - %9d:", (c == 0) ? 0 : 1 << (c - 1), 1 << c);
    for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
      out->print(SIZE_FORMAT_W(12), _alloc_latency[t].level(c));
    }
    out->cr();
  }
  out->cr();

  out->print_cr("Sizes histogram (size in bytes):");
  for (int c = size_min_level; c <= size_max_level; c++) {
    int l = (c == 0) ? 0 : 1 << (c - 1);
    int r = 1 << c;
    out->print("%9d - %9d:", l * HeapWordSize, r * HeapWordSize);
    for (size_t t = 0; t < ShenandoahAllocRequest::_ALLOC_LIMIT; t++) {
      out->print(SIZE_FORMAT_W(12), _alloc_size[t].level(c));
    }
    out->cr();
  }
  out->cr();
}
