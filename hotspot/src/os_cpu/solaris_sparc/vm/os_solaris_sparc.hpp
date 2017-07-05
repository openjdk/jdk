/*
 * Copyright 1999-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

  //
  // NOTE: we are back in class os here, not Solaris
  //
  static jint  (*atomic_xchg_func)        (jint,  volatile jint*);
  static jint  (*atomic_cmpxchg_func)     (jint,  volatile jint*,  jint);
  static jlong (*atomic_cmpxchg_long_func)(jlong, volatile jlong*, jlong);
  static jint  (*atomic_add_func)         (jint,  volatile jint*);

  static jint  atomic_xchg_bootstrap        (jint,  volatile jint*);
  static jint  atomic_cmpxchg_bootstrap     (jint,  volatile jint*,  jint);
  static jlong atomic_cmpxchg_long_bootstrap(jlong, volatile jlong*, jlong);
  static jint  atomic_add_bootstrap         (jint,  volatile jint*);

  static void setup_fpu() {}

  static bool is_allocatable(size_t bytes);

  // Used to register dynamic code cache area with the OS
  // Note: Currently only used in 64 bit Windows implementations
  static bool register_code_area(char *low, char *high) { return true; }
