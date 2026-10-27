/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZADAPTIVEHEAP_INLINE_HPP
#define SHARE_GC_Z_ZADAPTIVEHEAP_INLINE_HPP

#include "gc/z/zAdaptiveHeap.hpp"

#include "utilities/globalDefinitions.hpp"

inline physical_memory_size_type ZSystemMemoryPressureMetrics::available_memory() const {
  return _max_memory - MIN2(_used_memory, _max_memory);
}

inline double ZSystemMemoryPressureMetrics::fraction_of_max(physical_memory_size_type memory) const {
  return percent_of(memory, _max_memory) / 100.0;
}

inline double ZSystemMemoryPressureMetrics::used_fraction() const {
  return fraction_of_max(_used_memory);
}

inline double ZSystemMemoryPressureMetrics::available_fraction() const {
  return fraction_of_max(available_memory());
}

inline bool ZAdaptiveHeap::explicit_max_capacity() {
  precond(_initialized);
  return _explicit_max_capacity;
}

#endif // SHARE_GC_Z_ZADAPTIVEHEAP_INLINE_HPP
