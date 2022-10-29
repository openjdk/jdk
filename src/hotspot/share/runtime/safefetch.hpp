/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_RUNTIME_SAFEFETCH_HPP
#define SHARE_RUNTIME_SAFEFETCH_HPP

#include "utilities/macros.hpp"

// Safefetch allows to load a value from a location that's not known
// to be valid. If the load causes a fault, the error value is returned.

#ifdef _WIN32
  // Windows uses Structured Exception Handling
  #include "safefetch_windows.hpp"
#elif defined(ZERO) || defined (_AIX)
  // These platforms implement safefetch via Posix sigsetjmp/longjmp.
  // This is slower than the other methods and uses more thread stack,
  // but its safe and portable.
  #include "safefetch_sigjmp.hpp"
  #define SAFEFETCH_METHOD_SIGSETJMP
#else
  // All other platforms use static assembly
  #include "safefetch_static.hpp"
  #define SAFEFETCH_METHOD_STATIC_ASSEMBLY
#endif


inline int SafeFetch32(int* adr, int errValue) {
  return SafeFetch32_impl(adr, errValue);
}

inline intptr_t SafeFetchN(intptr_t* adr, intptr_t errValue) {
  return SafeFetchN_impl(adr, errValue);
}

#endif // SHARE_RUNTIME_SAFEFETCH_HPP
