/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahMemoryPool.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"

ShenandoahMemoryPool::ShenandoahMemoryPool(ShenandoahHeap* heap,
                                           const char* name) :
        CollectedMemoryPool(name,
                            heap->initial_capacity(),
                            heap->max_capacity(),
                            true /* support_usage_threshold */),
                            _heap(heap) {}

ShenandoahMemoryPool::ShenandoahMemoryPool(ShenandoahHeap* heap,
                                           const char* name,
                                           size_t initial_capacity,
                                           size_t max_capacity) :
        CollectedMemoryPool(name,
                            initial_capacity,
                            max_capacity,
                            true /* support_usage_threshold */),
                            _heap(heap) {}


MemoryUsage ShenandoahMemoryPool::get_memory_usage() {
  return shenandoah_memory_usage(initial_size(), used_in_bytes(), _heap->committed(), max_size());
}

size_t ShenandoahMemoryPool::used_in_bytes() {
  return _heap->used();
}

size_t ShenandoahMemoryPool::max_size() const {
  return _heap->max_capacity();
}

ShenandoahGenerationalMemoryPool::ShenandoahGenerationalMemoryPool(ShenandoahHeap* heap, const char* name,
                                                                   ShenandoahGeneration* generation) :
        ShenandoahMemoryPool(heap, name, 0, heap->max_capacity()),
        _generation(generation) { }

MemoryUsage ShenandoahGenerationalMemoryPool::get_memory_usage() {
  return shenandoah_memory_usage(initial_size(), used_in_bytes(), _generation->used_regions_size(), max_size());
}

size_t ShenandoahGenerationalMemoryPool::used_in_bytes() {
  return _generation->used();
}

ShenandoahYoungGenMemoryPool::ShenandoahYoungGenMemoryPool(ShenandoahHeap* heap) :
        ShenandoahGenerationalMemoryPool(heap,
                             "Shenandoah Young Gen",
                             heap->young_generation()) { }

ShenandoahOldGenMemoryPool::ShenandoahOldGenMemoryPool(ShenandoahHeap* heap) :
        ShenandoahGenerationalMemoryPool(heap,
                             "Shenandoah Old Gen",
                             heap->old_generation()) { }
