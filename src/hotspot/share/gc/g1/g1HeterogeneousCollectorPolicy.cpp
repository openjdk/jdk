/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1HeterogeneousCollectorPolicy.hpp"
#include "logging/log.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/formatBuffer.hpp"

const double G1HeterogeneousCollectorPolicy::MaxRamFractionForYoung = 0.8;
size_t G1HeterogeneousCollectorPolicy::MaxMemoryForYoung;

static size_t calculate_reasonable_max_memory_for_young(FormatBuffer<100> &calc_str, double max_ram_fraction_for_young) {
  julong phys_mem;
  // If MaxRam is specified, we use that as maximum physical memory available.
  if (FLAG_IS_DEFAULT(MaxRAM)) {
    phys_mem = os::physical_memory();
    calc_str.append("Physical_Memory");
  } else {
    phys_mem = (julong)MaxRAM;
    calc_str.append("MaxRAM");
  }

  julong reasonable_max = phys_mem;

  // If either MaxRAMFraction or MaxRAMPercentage is specified, we use them to calculate
  // reasonable max size of young generation.
  if (!FLAG_IS_DEFAULT(MaxRAMFraction)) {
    reasonable_max = (julong)(phys_mem / MaxRAMFraction);
    calc_str.append(" / MaxRAMFraction");
  }  else if (!FLAG_IS_DEFAULT(MaxRAMPercentage)) {
    reasonable_max = (julong)((phys_mem * MaxRAMPercentage) / 100);
    calc_str.append(" * MaxRAMPercentage / 100");
  }  else {
    // We use our own fraction to calculate max size of young generation.
    reasonable_max = phys_mem * max_ram_fraction_for_young;
    calc_str.append(" * %0.2f", max_ram_fraction_for_young);
  }

  return (size_t)reasonable_max;
}

void G1HeterogeneousCollectorPolicy::initialize_flags() {

  FormatBuffer<100> calc_str("");

  MaxMemoryForYoung = calculate_reasonable_max_memory_for_young(calc_str, MaxRamFractionForYoung);

  if (MaxNewSize > MaxMemoryForYoung) {
    if (FLAG_IS_CMDLINE(MaxNewSize)) {
      log_warning(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            MaxMemoryForYoung, calc_str.buffer());
    } else {
      log_info(gc, ergo)("Setting MaxNewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s)). "
                         "Dram usage can be lowered by setting MaxNewSize to a lower value", MaxMemoryForYoung, calc_str.buffer());
    }
    MaxNewSize = MaxMemoryForYoung;
  }
  if (NewSize > MaxMemoryForYoung) {
    if (FLAG_IS_CMDLINE(NewSize)) {
      log_warning(gc, ergo)("Setting NewSize to " SIZE_FORMAT " based on dram available (calculation = align(%s))",
                            MaxMemoryForYoung, calc_str.buffer());
    }
    NewSize = MaxMemoryForYoung;
  }

  // After setting new size flags, call base class initialize_flags()
  G1CollectorPolicy::initialize_flags();
}

size_t G1HeterogeneousCollectorPolicy::reasonable_max_memory_for_young() {
  return MaxMemoryForYoung;
}

size_t G1HeterogeneousCollectorPolicy::heap_reserved_size_bytes() const {
    return 2 * _max_heap_byte_size;
}

bool G1HeterogeneousCollectorPolicy::is_heterogeneous_heap() const {
  return true;
}
