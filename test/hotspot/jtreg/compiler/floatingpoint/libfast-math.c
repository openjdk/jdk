/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

#include <assert.h>
#include <fenv.h>
#include "jni.h"

// See GCC bug 55522:
//
// "When used at link-time, [ GCC with -ffast-math ] may include
// libraries or startup files that change the default FPU control word
// or other similar optimizations."
//
// This breaks Java's floating point arithmetic.

#if defined(__GNUC__) && defined(__x86_64__) && defined(__GNU_LIBRARY__)

void set_flush_to_zero(void) __attribute__((constructor));
void set_flush_to_zero(void) {
  fenv_t fenv;
  int rtn = fegetenv(&fenv);
  assert(rtn == 0);

  fenv.__mxcsr |= 0x8000; // Flush to zero

  rtn = fesetenv(&fenv);
  assert(rtn == 0);
}

#endif // defined(__GNUC__) && defined(__x86_64__) && defined(__GNU_LIBRARY__)
