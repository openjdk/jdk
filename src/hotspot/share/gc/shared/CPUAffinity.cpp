/*
* Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/CPUAffinity.inline.hpp"
#include "memory/padded.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"


#define UNKNOWN_AFFINITY ((Thread*)-1)
#define UNKNOWN_SELF     ((Thread*)-2)

PaddedEnd<CPUAffinity::Affinity>* CPUAffinity::_affinity = nullptr;
THREAD_LOCAL Thread*           CPUAffinity::_self     = UNKNOWN_SELF;
THREAD_LOCAL uint32_t          CPUAffinity::_cpu      = 0;

void CPUAffinity::initialize() {
  assert(_affinity == nullptr, "Already initialized");
  const uint32_t ncpus = count();

  _affinity = PaddedArray<Affinity, mtGC>::create_unfreeable(ncpus);

  for (uint32_t i = 0; i < ncpus; i++) {
    _affinity[i]._thread = UNKNOWN_AFFINITY;
  }

  log_info_p(gc, init)("CPUs: %u total, %u available",
                       os::processor_count(),
                       os::initial_active_processor_count());
}

uint32_t CPUAffinity::id_slow() {
  // Set current thread
  if (_self == UNKNOWN_SELF) {
    _self = Thread::current();
  }

  // Set current CPU
  _cpu = os::processor_id();

  // Update affinity table
  _affinity[_cpu]._thread = _self;

  return _cpu;
}
