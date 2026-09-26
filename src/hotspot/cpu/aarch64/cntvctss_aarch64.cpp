/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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

#include "cntvctss_aarch64.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.inline.hpp"
#include "vm_version_aarch64.hpp"

// Armv8.6-A mandates a system counter frequency of 1GHz.
static const jlong ARMV86_ECV_FREQUENCY_HZ = 1000000000LL;

jlong Cntvctss::_epoch = 0;

jlong Cntvctss::set_epoch() {
  assert(0 == _epoch, "invariant");
  _epoch = os::cntvctss();
  return _epoch;
}

bool Cntvctss::ergonomics() {
  if (Cntvctss::is_supported()) {
    FLAG_SET_ERGO_IF_DEFAULT(UseFastUnorderedTimeStamps, true);
  } else if (UseFastUnorderedTimeStamps) {
    warning("Ignoring UseFastUnorderedTimeStamps, hardware does not support FEAT_ECV");
    FLAG_SET_ERGO(UseFastUnorderedTimeStamps, false);
  }
  return UseFastUnorderedTimeStamps;
}

bool Cntvctss::initialize() {
  assert(0 == _epoch, "invariant");
  if (!UseFastUnorderedTimeStamps) {
    return false;
  }
  set_epoch();
  return _epoch != 0;
}

bool Cntvctss::is_supported() {
  return VM_Version::supports_ecv() &&
         // FEAT_ECV is optional from Armv8.5-A and may appear on an Armv8.4-A CPU;
         // only Armv8.6-A guarantees the 1GHz frequency, so reject lower ones.
         os::cntfrq() == ARMV86_ECV_FREQUENCY_HZ;
}

jlong Cntvctss::frequency() {
  return ARMV86_ECV_FREQUENCY_HZ;
}

jlong Cntvctss::elapsed_counter() {
  return os::cntvctss() - _epoch;
}

jlong Cntvctss::epoch() {
  return _epoch;
}

jlong Cntvctss::raw() {
  return os::cntvctss();
}

bool Cntvctss::enabled() {
  static bool result = initialize();
  return result;
}
