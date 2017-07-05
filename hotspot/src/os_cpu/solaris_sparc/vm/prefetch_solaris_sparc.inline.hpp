/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_SOLARIS_SPARC_VM_PREFETCH_SOLARIS_SPARC_INLINE_HPP
#define OS_CPU_SOLARIS_SPARC_VM_PREFETCH_SOLARIS_SPARC_INLINE_HPP

#include "runtime/prefetch.hpp"

#if defined(COMPILER2) || defined(_LP64)

// For Sun Studio inplementation is in solaris_sparc.il
// For gcc inplementation is just below
extern "C" void _Prefetch_read (void *loc, intx interval);
extern "C" void _Prefetch_write(void *loc, intx interval);

inline void Prefetch::read(void *loc, intx interval) {
  _Prefetch_read(loc, interval);
}

inline void Prefetch::write(void *loc, intx interval) {
  _Prefetch_write(loc, interval);
}

#ifdef _GNU_SOURCE
extern "C" {
  inline void _Prefetch_read (void *loc, intx interval) {
    __asm__ volatile
      ("prefetch [%0+%1], 0" : : "r" (loc), "r" (interval) : "memory" );
  }
  inline void _Prefetch_write(void *loc, intx interval) {
    __asm__ volatile
      ("prefetch [%0+%1], 2" : : "r" (loc), "r" (interval) : "memory" );
  }
}
#endif // _GNU_SOURCE

#else  // defined(COMPILER2) || defined(_LP64)

inline void Prefetch::read (void *loc, intx interval) {}
inline void Prefetch::write(void *loc, intx interval) {}

#endif // defined(COMPILER2) || defined(_LP64)

#endif // OS_CPU_SOLARIS_SPARC_VM_PREFETCH_SOLARIS_SPARC_INLINE_HPP
