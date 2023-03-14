/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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

#include "shared.h"

struct S_FFFFFFF { float p0, p1, p2, p3, p4, p5, p6; };

EXPORT struct S_FFFFFFF add_float_structs(struct S_FFFFFFF p0,
  struct S_FFFFFFF p1){
  p0.p0 += p1.p0;
  p0.p1 += p1.p1;
  p0.p2 += p1.p2;
  p0.p3 += p1.p3;
  p0.p4 += p1.p4;
  p0.p5 += p1.p5;
  p0.p6 += p1.p6;
  return p0;
}

// Corner case on PPC64le: Pass struct S_FF partially in FP register and on stack.
// Pass additional float on stack.
EXPORT struct S_FF add_float_to_struct_after_floats(
  float f1, float f2, float f3, float f4, float f5,
  float f6, float f7, float f8, float f9, float f10,
  float f11, float f12, struct S_FF s, float f) {
  s.p0 += f;
  return s;
}

// Corner case on PPC64le: Pass struct S_FF partially in FP register and in GP register.
// Pass additional float in GP register.
EXPORT struct S_FF add_float_to_struct_after_structs(
  struct S_FF s1, struct S_FF s2, struct S_FF s3, struct S_FF s4, struct S_FF s5, struct S_FF s6,
  struct S_FF s, float f) {
  s.p0 += f;
  return s;
}

// Corner case on PPC64le: Pass struct S_FF partially in FP register and in GP register and on stack.
EXPORT struct S_FFFFFFF add_float_to_large_struct_after_structs(
  struct S_FF s1, struct S_FF s2, struct S_FF s3, struct S_FF s4, struct S_FF s5, struct S_FF s6,
  struct S_FFFFFFF s, float f) {
  s.p0 += f;
  return s;
}

// Upcall versions.
EXPORT struct S_FFFFFFF pass_two_large_structs(struct S_FFFFFFF (*fun)(struct S_FFFFFFF, struct S_FFFFFFF),
                                               struct S_FFFFFFF s1, struct S_FFFFFFF s2) {
  return fun(s1, s2);
}

EXPORT struct S_FF pass_struct_after_floats(struct S_FF (*fun)(
                                              float, float, float, float, float,
                                              float, float, float, float, float,
                                              float, float, struct S_FF, float),
                                            struct S_FF s1, float f) {
  return fun(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f, 12.0f, s1, f);
}

EXPORT struct S_FF pass_struct_after_structs(struct S_FF (*fun)(
                                               struct S_FF, struct S_FF, struct S_FF,
                                               struct S_FF, struct S_FF, struct S_FF,
                                               struct S_FF, float),
                                             struct S_FF s1, float f) {
  struct S_FF dummy;
  dummy.p0 = 1; dummy.p1 = 2;
  return fun(dummy, dummy, dummy, dummy, dummy, dummy, s1, f);
}

EXPORT struct S_FFFFFFF pass_large_struct_after_structs(struct S_FFFFFFF (*fun)(
                                                          struct S_FF, struct S_FF, struct S_FF,
                                                          struct S_FF, struct S_FF, struct S_FF,
                                                          struct S_FFFFFFF, float),
                                                        struct S_FFFFFFF s1, float f) {
  struct S_FF dummy;
  dummy.p0 = 1; dummy.p1 = 2;
  return fun(dummy, dummy, dummy, dummy, dummy, dummy, s1, f);
}
