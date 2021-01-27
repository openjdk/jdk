/*
 * Copyright (c) 1999, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2018 SAP SE. All rights reserved.
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

#ifndef OS_AIX_OS_AIX_INLINE_HPP
#define OS_AIX_OS_AIX_INLINE_HPP

#include "runtime/os.hpp"
#include "os_posix.inline.hpp"

// System includes

#include <unistd.h>

// Whether or not calling code should/can commit/uncommit stack pages
// before guarding them. Answer for AIX is definitly no, because memory
// is automatically committed on touch.
inline bool os::must_commit_stack_guard_pages() {
  assert(uses_stack_guard_pages(), "sanity check");
  return false;
}

inline int os::ftruncate(int fd, jlong length) {
  return ::ftruncate64(fd, length);
}

// We don't have NUMA support on Aix, but we need this for compilation.
inline bool os::numa_has_static_binding()   { ShouldNotReachHere(); return true; }
inline bool os::numa_has_group_homing()     { ShouldNotReachHere(); return false;  }

#endif // OS_AIX_OS_AIX_INLINE_HPP
