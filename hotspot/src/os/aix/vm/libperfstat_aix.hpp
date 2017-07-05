/*
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

// encapsulates the libperfstat library.
//
// The purpose of this code is to dynamically load the libperfstat library
// instead of statically linking against it. The libperfstat library is an
// AIX-specific library which only exists on AIX, not on PASE. If I want to
// share binaries between AIX and PASE, I cannot directly link against libperfstat.so.

#ifndef OS_AIX_VM_LIBPERFSTAT_AIX_HPP
#define OS_AIX_VM_LIBPERFSTAT_AIX_HPP

#include <libperfstat.h>

class libperfstat {

public:

  // Load the libperfstat library (must be in LIBPATH).
  // Returns true if succeeded, false if error.
  static bool init();

  // cleanup of the libo4 porting library.
  static void cleanup();

  // direct wrappers for the libperfstat functionality. All they do is
  // to call the functions with the same name via function pointers.
  static int perfstat_cpu_total(perfstat_id_t *name, perfstat_cpu_total_t* userbuff,
                                int sizeof_userbuff, int desired_number);

  static int perfstat_memory_total(perfstat_id_t *name, perfstat_memory_total_t* userbuff,
                                   int sizeof_userbuff, int desired_number);

  static void perfstat_reset();
};

#endif // OS_AIX_VM_LIBPERFSTAT_AIX_HPP
