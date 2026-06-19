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
#include "vm_version_aarch64.hpp"

jlong Cntvctss::_epoch = 0;

static inline jlong read_cntvctss() {
  uint64_t res;
  __asm__ volatile("mrs %0, s3_3_c14_c0_6" : "=r"(res)); // s3_3_c14_c0_6 is the numeric encoding of CNTVCTSS_EL0 for old GNU assemblers
  return (jlong)res;
}

jlong Cntvctss::set_epoch() {
  assert(0 == _epoch, "invariant");
  _epoch = read_cntvctss();
  return _epoch;
}

static bool ergonomics() {
  if (Cntvctss::is_supported()) {
    FLAG_SET_ERGO_IF_DEFAULT(UseFastUnorderedTimeStamps, true);
  } else if (UseFastUnorderedTimeStamps) {
    assert(!FLAG_IS_DEFAULT(UseFastUnorderedTimeStamps), "Unexpected default value");
    warning("Ignoring UseFastUnorderedTimeStamps, hardware does not support FEAT_ECV");
    FLAG_SET_ERGO(UseFastUnorderedTimeStamps, false);
  }
  return UseFastUnorderedTimeStamps;
}

bool Cntvctss::initialize() {
  assert(0 == _epoch, "invariant");
  if (!ergonomics()) {
    return false;
  }
  set_epoch();
  return _epoch != 0;
}

bool Cntvctss::is_supported() {
  return VM_Version::supports_ecv();
}

jlong Cntvctss::frequency() {
  return 1000000000LL; // Generic Timer runs at 1 GHz on Armv8.6-A+ (FEAT_ECV)
}

jlong Cntvctss::elapsed_counter() {
  return read_cntvctss() - _epoch;
}

jlong Cntvctss::epoch() {
  return _epoch;
}

jlong Cntvctss::raw() {
  return read_cntvctss();
}

bool Cntvctss::enabled() {
  static bool result = initialize();
  return result;
}
