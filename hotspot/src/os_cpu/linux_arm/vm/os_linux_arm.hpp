/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_LINUX_ARM_VM_OS_LINUX_ARM_HPP
#define OS_CPU_LINUX_ARM_VM_OS_LINUX_ARM_HPP

#ifndef __thumb__
  enum {
    // Offset to add to frame::_fp when dealing with non-thumb C frames
#ifdef AARCH64
    C_frame_offset =  0,
#else
    C_frame_offset =  -1,
#endif
  };
#endif

  static void setup_fpu();

  static bool is_allocatable(size_t bytes);

  // Used to register dynamic code cache area with the OS
  // Note: Currently only used in 64 bit Windows implementations
  static bool register_code_area(char *low, char *high) { return true; }

#ifndef AARCH64
  static jlong (*atomic_cmpxchg_long_func)(jlong compare_value,
                                           jlong exchange_value,
                                           volatile jlong *dest);

  static jlong (*atomic_load_long_func)(volatile jlong*);

  static void (*atomic_store_long_func)(jlong, volatile jlong*);

  static jint  (*atomic_add_func)(jint add_value, volatile jint *dest);

  static jint  (*atomic_xchg_func)(jint exchange_value, volatile jint *dest);

  static jint  (*atomic_cmpxchg_func)(jint compare_value,
                                      jint exchange_value,
                                      volatile jint *dest);

  static jlong atomic_cmpxchg_long_bootstrap(jlong, jlong, volatile jlong*);

  static jlong atomic_load_long_bootstrap(volatile jlong*);

  static void atomic_store_long_bootstrap(jlong, volatile jlong*);

  static jint  atomic_add_bootstrap(jint add_value, volatile jint *dest);

  static jint  atomic_xchg_bootstrap(jint exchange_value, volatile jint *dest);

  static jint  atomic_cmpxchg_bootstrap(jint compare_value,
                                        jint exchange_value,
                                        volatile jint *dest);
#endif // !AARCH64

#endif // OS_CPU_LINUX_ARM_VM_OS_LINUX_ARM_HPP
