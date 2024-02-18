/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

#include "jni.h"

// See GCC bug 55522:
//
// "When used at link-time, [ GCC with -ffast-math ] may include
// libraries or startup files that change the default FPU control word
// or other similar optimizations."
//
// This breaks Java's floating point arithmetic.

#if defined(__GNUC__)

// On systems on which GCC bug 55522 has been fixed, this constructor
// serves to reproduce that bug for the purposes of testing HotSpot.
static void __attribute__((constructor)) set_flush_to_zero(void) {

#if defined(__x86_64__)

#define MXCSR_DAZ (1 << 6)      /* Enable denormals are zero mode */
#define MXCSR_FTZ (1 << 15)     /* Enable flush to zero mode */
  unsigned int mxcsr = __builtin_ia32_stmxcsr ();
  mxcsr |= MXCSR_DAZ | MXCSR_FTZ;
  __builtin_ia32_ldmxcsr (mxcsr);

#elif defined(__aarch64__)

#define _FPU_FPCR_FZ (unsigned long)0x1000000
#define _FPU_SETCW(fpcr) \
  __asm__ __volatile__ ("msr fpcr, %0" : : "r" (fpcr));

  /* Flush to zero, round to nearest, IEEE exceptions disabled.  */
  _FPU_SETCW (_FPU_FPCR_FZ);

#endif // CPU arch

}
#endif // defined(__GNUC__)
