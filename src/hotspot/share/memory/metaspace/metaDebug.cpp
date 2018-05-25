/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "memory/metaspace/metaDebug.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

int Metadebug::_allocation_fail_alot_count = 0;

void Metadebug::init_allocation_fail_alot_count() {
  if (MetadataAllocationFailALot) {
    _allocation_fail_alot_count =
      1+(long)((double)MetadataAllocationFailALotInterval*os::random()/(max_jint+1.0));
  }
}

#ifdef ASSERT
bool Metadebug::test_metadata_failure() {
  if (MetadataAllocationFailALot &&
      Threads::is_vm_complete()) {
    if (_allocation_fail_alot_count > 0) {
      _allocation_fail_alot_count--;
    } else {
      log_trace(gc, metaspace, freelist)("Metadata allocation failing for MetadataAllocationFailALot");
      init_allocation_fail_alot_count();
      return true;
    }
  }
  return false;
}
#endif


} // namespace metaspace


