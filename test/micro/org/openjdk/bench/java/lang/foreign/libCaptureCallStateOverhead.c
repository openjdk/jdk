/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include <errno.h>
#include "export.h"

// A function that computes both the F_n and n! of a given n.
// This is meant to represent meaningful work.
EXPORT void fib_and_fact(int n, int* fib, int* fact) {
  if (n < 0) {
    errno = EINVAL;
    *fib = -1;
    *fact = -1;
    return;
  } else if (n == 0) {
    *fib = 0;
    *fact = 1;
    return;
  } else if (n == 1) {
    *fib = 1;
    *fact = 1;
    return;
  }
  int fib_prev = 1;
  int fib_cur = 1;
  int fact_accum = 1;
  for (int i = 2; i < n; i++) {
    int fib_new = fib_prev + fib_cur;
    fib_prev = fib_cur;
    fib_cur = fib_new;
    fact_accum *= i;
  }
  fact_accum *= n;
  *fib = fib_cur;
  *fact = fact_accum;
}
