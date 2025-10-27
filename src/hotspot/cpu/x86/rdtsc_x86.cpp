/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "rdtsc_x86.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/macros.hpp"
#include "vm_version_x86.hpp"

DEBUG_ONLY(volatile int Rdtsc::_initialized = 0;)
jlong Rdtsc::_epoch = 0;
jlong Rdtsc::_tsc_frequency = 0;

jlong Rdtsc::set_epoch() {
  assert(0 == _epoch, "invariant");
  _epoch = os::rdtsc();
  return _epoch;
}

jlong Rdtsc::initialize_frequency() {
  assert(0 == _tsc_frequency, "invariant");
  assert(0 == _epoch, "invariant");
  const jlong initial_counter = set_epoch();
  if (initial_counter == 0) {
    return 0;
  }
  // os time frequency
  static double os_freq = (double)os::elapsed_frequency();
  assert(os_freq > 0, "os_elapsed frequency corruption!");

  double tsc_freq = .0;
  double os_to_tsc_conv_factor = 1.0;

  // if platform supports invariant tsc,
  // apply higher resolution and granularity for conversion calculations
  if (VM_Version::supports_tscinv_ext()) {
    // for invariant tsc platforms, take the maximum qualified cpu frequency
    tsc_freq = (double)VM_Version::maximum_qualified_cpu_frequency();
    os_to_tsc_conv_factor = tsc_freq / os_freq;
  }

  if ((tsc_freq < 0) || (tsc_freq > 0 && tsc_freq <= os_freq) || (os_to_tsc_conv_factor <= 1)) {
    // safer to run with normal os time
    tsc_freq = .0;
  }

  // frequency of the tsc_counter
  return (jlong)tsc_freq;
}

bool Rdtsc::initialize_elapsed_counter() {
  _tsc_frequency = initialize_frequency();
  return _tsc_frequency != 0 && _epoch != 0;
}

static bool ergonomics() {
  if (Rdtsc::is_supported()) {
    // Use rdtsc when it is supported by default
    FLAG_SET_ERGO_IF_DEFAULT(UseFastUnorderedTimeStamps, true);
  } else if (UseFastUnorderedTimeStamps) {
    assert(!FLAG_IS_DEFAULT(UseFastUnorderedTimeStamps), "Unexpected default value");

    if (VM_Version::supports_tsc()) {
      warning("Ignoring UseFastUnorderedTimeStamps, the hardware does not support invariant tsc (INVTSC) register and/or cannot guarantee tsc synchronization between sockets at startup.\n"
              "Values returned via rdtsc() are not guaranteed to be accurate, esp. when comparing values from cross sockets reads.");
    } else {
      warning("Ignoring UseFastUnorderedTimeStamps, hardware does not support normal tsc");
    }

    // We do not support non invariant rdtsc
    FLAG_SET_ERGO(UseFastUnorderedTimeStamps, false);
  }

  return UseFastUnorderedTimeStamps;
}

bool Rdtsc::initialize() {
  precond(AtomicAccess::xchg(&_initialized, 1) == 0);
  assert(0 == _tsc_frequency, "invariant");
  assert(0 == _epoch, "invariant");

  if (!ergonomics()) {
    // We decided to ergonomically not support rdtsc.
    return false;
  }

  // Try to initialize the elapsed counter
  return initialize_elapsed_counter();
}

bool Rdtsc::is_supported() {
  return VM_Version::supports_tscinv_ext();
}

jlong Rdtsc::frequency() {
  return _tsc_frequency;
}

jlong Rdtsc::elapsed_counter() {
  return os::rdtsc() - _epoch;
}

jlong Rdtsc::epoch() {
  return _epoch;
}

jlong Rdtsc::raw() {
  return os::rdtsc();
}

bool Rdtsc::enabled() {
  static bool enabled = initialize();
  return enabled;
}
