/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_SPARC_VM_OS_LINUX_SPARC_HPP
#define OS_CPU_LINUX_SPARC_VM_OS_LINUX_SPARC_HPP

  //
  // NOTE: we are back in class os here, not Linux
  //
  static int32_t  (*atomic_xchg_func)        (int32_t,  volatile int32_t*);
  static int32_t  (*atomic_cmpxchg_func)     (int32_t,  volatile int32_t*, int32_t);
  static int64_t  (*atomic_cmpxchg_long_func)(int64_t,  volatile int64_t*, int64_t);
  static int32_t  (*atomic_add_func)         (int32_t,  volatile int32_t*);

  static int32_t  atomic_xchg_bootstrap        (int32_t,  volatile int32_t*);
  static int32_t  atomic_cmpxchg_bootstrap     (int32_t,  volatile int32_t*, int32_t);
  static int64_t  atomic_cmpxchg_long_bootstrap(int64_t,  volatile int64_t*, int64_t);
  static int32_t  atomic_add_bootstrap         (int32_t,  volatile int32_t*);

  static void setup_fpu() {}

  static bool is_allocatable(size_t bytes);

  // Used to register dynamic code cache area with the OS
  // Note: Currently only used in 64 bit Windows implementations
  static bool register_code_area(char *low, char *high) { return true; }

#endif // OS_CPU_LINUX_SPARC_VM_OS_LINUX_SPARC_HPP
