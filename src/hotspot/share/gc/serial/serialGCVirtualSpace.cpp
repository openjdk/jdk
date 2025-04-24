/*
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

#include "gc/serial/serialGCVirtualSpace.hpp"
#include "gc/shared/genArguments.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "runtime/java.hpp"

// See os::is_server_class_machine()
const julong server_memory     = 2UL * G;
const julong missing_memory   = 256UL * M;

size_t SerialGCVirtualSpace::max_new_size() const {
  return server_memory - missing_memory;
}

void SerialGCVirtualSpace::initialize(ReservedSpace rs, size_t old_size, size_t new_size) {
  assert(old_size != 0, "old_size must not be 0");
  assert(new_size != 0, "new_size must not be 0");

  size_t initial_virtual_space_size = old_size + new_size;
  if (!_virtual_space.initialize(rs, initial_virtual_space_size)) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
  }

  _heap_region = MemRegion((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());

  // Mangle all of the initial generations.
  if (ZapUnusedHeapArea) {
    SpaceMangler::mangle_region(_heap_region);
  }

  MemRegion tenured_region((HeapWord*)_virtual_space.low(), heap_word_size(OldSize));
  assert(tenured_region.byte_size() == old_size, "_tenured_region size in bytes must match old_size");
  set_tenured_region(tenured_region);

  MemRegion young_region = _heap_region.minus(_tenured_region);
  assert(young_region.byte_size() == new_size, "_young_region size in bytes must match new_size");
  set_young_region(young_region);
}

size_t SerialGCVirtualSpace::committed_size() {
  return _virtual_space.committed_size();
}

void SerialGCVirtualSpace::set_tenured_region(MemRegion region) {
  log_trace(gc, heap)("SerialGCVirtualSpace updating tenured region: ");
  log_trace(gc, heap)("   from _tenured_region.start(): " PTR_FORMAT " _tenured_region.end(): " PTR_FORMAT,
                      p2i(_tenured_region.start()), p2i(_tenured_region.end()));
  log_trace(gc, heap)("   to            region.start(): " PTR_FORMAT "          region.end(): " PTR_FORMAT,
                      p2i(region.start()), p2i(region.end()));
  _tenured_region = region;
}

void SerialGCVirtualSpace::set_young_region(MemRegion region) {
  log_trace(gc, heap)("SerialGCVirtualSpace updating young region: ");
  log_trace(gc, heap)("   from _young_region.start(): " PTR_FORMAT " _young_region.end(): " PTR_FORMAT,
                      p2i(_young_region.start()), p2i(_young_region.end()));
  log_trace(gc, heap)("   to          region.start(): " PTR_FORMAT "        region.end(): " PTR_FORMAT,
                      p2i(region.start()), p2i(region.end()));
  _young_region = region;
}

bool SerialGCVirtualSpace::resize_virtual_space(size_t tenured_gen_size, size_t young_gen_size) {
  size_t curr_capacity = committed_size();
  size_t new_capacity = tenured_gen_size + young_gen_size;

  bool success;

  if (new_capacity > curr_capacity) {
    size_t expand_bytes = new_capacity - curr_capacity;
    if (expand_bytes < MinHeapDeltaBytes) {
      // Always expand by at least MinHeapDeltaBytes
      expand_bytes = MinHeapDeltaBytes;
    }

    success = expand_by(expand_bytes);
    log_trace(gc, heap)("SerialGCVirtualSpace attempting expansion:  new_capacity: %6.1fK  expand_bytes: %6.1fK  MinHeapDeltaBytes: %6.1fK  success: %d",
                  new_capacity / (double) K,
                  expand_bytes / (double) K,
                  MinHeapDeltaBytes / (double) K,
                  success);
  } else if (new_capacity < curr_capacity) {
    size_t shrink_bytes = curr_capacity - new_capacity;
    shrink_by(shrink_bytes);
    success = true;
  }

  if (success) {
    _heap_region = MemRegion((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());
  }
  return success;
}

bool SerialGCVirtualSpace::resize(size_t tenured_gen_size, size_t young_gen_size) {
  assert(tenured_gen_size > 0, "tenured_gen_size must not be 0");
  assert(young_gen_size > 0, "young_gen_size must not be 0");

  size_t curr_capacity = committed_size();
  bool success = resize_virtual_space(tenured_gen_size, young_gen_size);
  size_t new_capacity = committed_size();

  if (success) {
    // update young and tenured regions
    MemRegion tr(_tenured_region.start(), heap_word_size(tenured_gen_size));
    MemRegion yr = _heap_region.minus(tr);
    set_tenured_region(tr);
    set_young_region(yr);
  }

  log_trace(gc, heap)("SerialGCVirtualSpace size %6.1fK->%6.1fK [young=%6.1fK,tenured=%6.1fK]",
                      curr_capacity / (double) K,
                      new_capacity / (double) K,
                      _young_region.byte_size() / (double) K,
                      _tenured_region.byte_size() / (double) K);

  return success;
}

bool SerialGCVirtualSpace::resize(size_t young_gen_size) {
  assert(young_gen_size > 0, "young_gen_size must not be 0");

  size_t prev_capacity = committed_size();
  bool success = resize_virtual_space(_tenured_region.byte_size(), young_gen_size);

  if (success) {
    MemRegion young_region = _heap_region.minus(_tenured_region);
    set_young_region(young_region);
  }

  size_t new_capacity = committed_size();
  log_trace(gc, heap)("SerialGCVirtualSpace size %s: %6.1fK->%6.1fK [young=%6.1fK,tenured=%6.1fK]",
                      new_capacity == prev_capacity ? "unchanged" : "changed",
                      prev_capacity / (double) K,
                      new_capacity / (double) K,
                      _young_region.byte_size() / (double) K,
                      _tenured_region.byte_size() / (double) K);

  return success;
}

bool SerialGCVirtualSpace::expand_by(size_t bytes, bool pre_touch) {
  HeapWord* prev_high = (HeapWord*)_virtual_space.high();
  bool success = _virtual_space.expand_by(bytes, pre_touch);

  if (success) {
    _heap_region = MemRegion((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());

    if (ZapUnusedHeapArea) {
      HeapWord* new_high = (HeapWord*) _virtual_space.high();
      MemRegion mangle_region(prev_high, new_high);
      SpaceMangler::mangle_region(mangle_region);
    }
  }

  return success;
}

void SerialGCVirtualSpace::shrink_by(size_t size) {
  _virtual_space.shrink_by(size);
  _heap_region = MemRegion((HeapWord*)_virtual_space.low(), (HeapWord*)_virtual_space.high());
}
