/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zLargePages.hpp"
#include "hugepages.hpp"
#include "os_linux.hpp"
#include "runtime/globals.hpp"

void ZLargePages::pd_initialize() {
  if (os::Linux::thp_requested()) {
    // Check if the OS config turned off transparent huge pages for shmem.
    _os_enforced_transparent_mode = HugePages::shmem_thp_info().is_disabled();
    _state = _os_enforced_transparent_mode ? Disabled : Transparent;
    return;
  }

  if (UseLargePages) {
    _state = Explicit;
    return;
  }

  // Check if the OS config turned on transparent huge pages for shmem.
  _os_enforced_transparent_mode = HugePages::shmem_thp_info().is_forced();
  _state = _os_enforced_transparent_mode ? Transparent : Disabled;
}
