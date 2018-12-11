/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahMemoryPool.hpp"

ShenandoahMemoryPool::ShenandoahMemoryPool(ShenandoahHeap* heap) :
        CollectedMemoryPool("Shenandoah",
                            heap->capacity(),
                            heap->max_capacity(),
                            true /* support_usage_threshold */),
                            _heap(heap) {}

MemoryUsage ShenandoahMemoryPool::get_memory_usage() {
  size_t initial   = initial_size();
  size_t max       = max_size();
  size_t used      = used_in_bytes();
  size_t committed = _heap->committed();

  assert(initial <= max,    "initial: "   SIZE_FORMAT ", max: "       SIZE_FORMAT, initial,   max);
  assert(used <= max,       "used: "      SIZE_FORMAT ", max: "       SIZE_FORMAT, used,      max);
  assert(committed <= max,  "committed: " SIZE_FORMAT ", max: "       SIZE_FORMAT, committed, max);
  assert(used <= committed, "used: "      SIZE_FORMAT ", committed: " SIZE_FORMAT, used,      committed);

  return MemoryUsage(initial, used, committed, max);
}
