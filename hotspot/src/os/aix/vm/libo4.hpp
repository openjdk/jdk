/*
 * Copyright (c) 2012, 2015 SAP SE. All rights reserved.
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

// A C++ wrapper around the libo4 porting library. The libo4 porting library
// is a set of bridge functions into native AS/400 functionality.

#ifndef OS_AIX_VM_LIBO4_HPP
#define OS_AIX_VM_LIBO4_HPP


class libo4 {
public:

  // Initialize the libo4 porting library.
  // Returns true if succeeded, false if error.
  static bool init();

  // cleanup of the libo4 porting library.
  static void cleanup();

  // returns a number of memory statistics from the
  // AS/400.
  //
  // Specify NULL for numbers you are not interested in.
  //
  // returns false if an error happened. Activate OsMisc trace for
  // trace output.
  //
  static bool get_memory_info (unsigned long long* p_virt_total, unsigned long long* p_real_total,
    unsigned long long* p_real_free, unsigned long long* p_pgsp_total, unsigned long long* p_pgsp_free);

  // returns information about system load
  // (similar to "loadavg()" under other Unices)
  //
  // Specify NULL for numbers you are not interested in.
  //
  // returns false if an error happened. Activate OsMisc trace for
  // trace output.
  //
  static bool get_load_avg (double* p_avg1, double* p_avg5, double* p_avg15);

  // this is a replacement for the "realpath()" API which does not really work
  // on PASE
  //
  // Specify NULL for numbers you are not interested in.
  //
  // returns false if an error happened. Activate OsMisc trace for
  // trace output.
  //
  static bool realpath (const char* file_name,
      char* resolved_name, int resolved_name_len);

};

#endif // OS_AIX_VM_LIBO4_HPP

