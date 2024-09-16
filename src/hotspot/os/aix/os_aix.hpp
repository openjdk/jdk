/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2024 SAP SE. All rights reserved.
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

#ifndef OS_AIX_OS_AIX_HPP
#define OS_AIX_OS_AIX_HPP

#include "runtime/os.hpp"

// Class Aix defines the interface to the Aix operating systems.

class os::Aix {
  friend class os;

 private:

  static julong _physical_memory;
  static pthread_t _main_thread;

  // 0 = uninitialized, otherwise 16 bit number:
  //  lower 8 bit - minor version
  //  higher 8 bit - major version
  //  For AIX, e.g. 0x0601 for AIX 6.1
  static uint32_t _os_version;

  // -1 = uninitialized,
  //  0 - SPEC1170 not requested (XPG_SUS_ENV is OFF or not set)
  //  1 - SPEC1170 requested (XPG_SUS_ENV is ON)
  static int _xpg_sus_mode;

  // -1 = uninitialized,
  //  0 - EXTSHM=OFF or not set
  //  1 - EXTSHM=ON
  static int _extshm;

  static julong available_memory();
  static julong free_memory();
  static julong physical_memory() { return _physical_memory; }
  static void initialize_system_info();

  // OS recognitions (AIX OS level) call this before calling Aix::os_version().
  static void initialize_os_info();

  // Scan environment for important settings which might effect the
  // VM. Trace out settings. Warn about invalid settings and/or
  // correct them.
  //
  // Must run after os::Aix::initialue_os_info().
  static void scan_environment();

  // Initialize libperfstat; call this
  // before relying on functions from either lib, e.g. Aix::get_meminfo().
  static void initialize_libperfstat();

 public:
  static void init_thread_fpu_state();
  static pthread_t main_thread(void)                                { return _main_thread; }
  static bool supports_64K_mmap_pages();

  // Given an address, returns the size of the page backing that address
  static size_t query_pagesize(void* p);

  static intptr_t* ucontext_get_sp(const ucontext_t* uc);
  static intptr_t* ucontext_get_fp(const ucontext_t* uc);

  static bool get_frame_at_stack_banging_point(JavaThread* thread, ucontext_t* uc, frame* fr);

  // libpthread version string
  static void libpthread_init();

  // Get 4 byte AIX kernel version number:
  // highest 2 bytes: Version, Release
  // if available: lowest 2 bytes: Tech Level, Service Pack.
  static uint32_t os_version() {
    assert(_os_version != 0, "not initialized");
    return _os_version;
  }

  // 0 = uninitialized, otherwise 16 bit number:
  // lower 8 bit - minor version
  // higher 8 bit - major version
  // For AIX, e.g. 0x0701 for AIX 7.1
  static int os_version_short() {
    return os_version() >> 16;
  }

  // Returns true if we run in SPEC1170 compliant mode (XPG_SUS_ENV=ON).
  static bool xpg_sus_mode() {
    assert(_xpg_sus_mode != -1, "not initialized");
    return _xpg_sus_mode;
  }

  // Returns true if EXTSHM=ON.
  static bool extshm() {
    assert(_extshm != -1, "not initialized");
    return _extshm;
  }

  // result struct for get_meminfo()
  struct meminfo_t {

    // Amount of virtual memory (in units of 4 KB pages)
    size_t virt_total;

    // Amount of real memory, in bytes
    size_t real_total;

    // Amount of free real memory, in bytes
    size_t real_free;

    // Total amount of paging space, in bytes
    size_t pgsp_total;

    // Amount of free paging space, in bytes
    size_t pgsp_free;

  };

  // function to retrieve memory information, using libperfstat
  // Returns true if ok, false if error.
  static bool get_meminfo(meminfo_t* pmi);

  static bool platform_print_native_stack(outputStream* st, const void* context, char *buf, int buf_size, address& lastpc);
  static void* resolve_function_descriptor(void* p);

};

#endif // OS_AIX_OS_AIX_HPP
