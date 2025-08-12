/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "export.h"

struct S1 { char f0[1]; };
struct S2 { char f0[2]; };
struct S3 { char f0[3]; };
struct S4 { char f0[4]; };
struct S5 { char f0[5]; };
struct S6 { char f0[6]; };
struct S7 { char f0[7]; };
struct S8 { char f0[8]; };
struct S9 { char f0[9]; };
struct S10 { char f0[10]; };
struct S11 { char f0[11]; };
struct S12 { char f0[12]; };
struct S13 { char f0[13]; };
struct S14 { char f0[14]; };
struct S15 { char f0[15]; };
struct S16 { char f0[16]; };

EXPORT struct S1 F1(struct S1 (*cb)(struct S1, char), struct S1 a0) {
  return cb(a0, a0.f0[0]);
}
EXPORT struct S2 F2(struct S2 (*cb)(struct S2, char, char), struct S2 a0) {
  return cb(a0, a0.f0[0], a0.f0[1]);
}
EXPORT struct S3 F3(struct S3 (*cb)(struct S3, char, char, char), struct S3 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2]);
}
EXPORT struct S4 F4(struct S4 (*cb)(struct S4, char, char, char, char), struct S4 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3]);
}
EXPORT struct S5 F5(struct S5 (*cb)(struct S5, char, char, char, char, char), struct S5 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4]);
}
EXPORT struct S6 F6(struct S6 (*cb)(struct S6, char, char, char, char, char, char), struct S6 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5]);
}
EXPORT struct S7 F7(struct S7 (*cb)(struct S7, char, char, char, char, char, char, char), struct S7 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6]);
}
EXPORT struct S8 F8(struct S8 (*cb)(struct S8, char, char, char, char, char, char, char, char), struct S8 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7]);
}
EXPORT struct S9 F9(struct S9 (*cb)(struct S9, char, char, char, char, char, char, char, char, char), struct S9 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8]);
}
EXPORT struct S10 F10(struct S10 (*cb)(struct S10, char, char, char, char, char, char, char, char, char, char), struct S10 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9]);
}
EXPORT struct S11 F11(struct S11 (*cb)(struct S11, char, char, char, char, char, char, char, char, char, char, char), struct S11 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10]);
}
EXPORT struct S12 F12(struct S12 (*cb)(struct S12, char, char, char, char, char, char, char, char, char, char, char, char), struct S12 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11]);
}
EXPORT struct S13 F13(struct S13 (*cb)(struct S13, char, char, char, char, char, char, char, char, char, char, char, char, char), struct S13 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12]);
}
EXPORT struct S14 F14(struct S14 (*cb)(struct S14, char, char, char, char, char, char, char, char, char, char, char, char, char, char), struct S14 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13]);
}
EXPORT struct S15 F15(struct S15 (*cb)(struct S15, char, char, char, char, char, char, char, char, char, char, char, char, char, char, char), struct S15 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13], a0.f0[14]);
}
EXPORT struct S16 F16(struct S16 (*cb)(struct S16, char, char, char, char, char, char, char, char, char, char, char, char, char, char, char, char), struct S16 a0) {
  return cb(a0, a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13], a0.f0[14], a0.f0[15]);
}

EXPORT struct S1 F1_stack(struct S1 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S1,
                                          char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S1 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0]);
}
EXPORT struct S2 F2_stack(struct S2 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S2,
                                          char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S2 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1]);
}
EXPORT struct S3 F3_stack(struct S3 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S3,
                                          char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S3 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2]);
}
EXPORT struct S4 F4_stack(struct S4 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S4,
                                          char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S4 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3]);
}
EXPORT struct S5 F5_stack(struct S5 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S5,
                                          char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S5 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4]);
}
EXPORT struct S6 F6_stack(struct S6 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S6,
                                          char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S6 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5]);
}
EXPORT struct S7 F7_stack(struct S7 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S7,
                                          char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S7 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6]);
}
EXPORT struct S8 F8_stack(struct S8 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S8,
                                          char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S8 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7]);
}
EXPORT struct S9 F9_stack(struct S9 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S9,
                                          char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S9 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8]);
}
EXPORT struct S10 F10_stack(struct S10 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S10,
                                          char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S10 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9]);
}
EXPORT struct S11 F11_stack(struct S11 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S11,
                                          char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S11 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10]);
}
EXPORT struct S12 F12_stack(struct S12 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S12,
                                          char, char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S12 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11]);
}
EXPORT struct S13 F13_stack(struct S13 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S13,
                                          char, char, char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S13 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12]);
}
EXPORT struct S14 F14_stack(struct S14 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S14,
                                          char, char, char, char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S14 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13]);
}
EXPORT struct S15 F15_stack(struct S15 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S15,
                                          char, char, char, char, char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S15 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13], a0.f0[14]);
}
EXPORT struct S16 F16_stack(struct S16 (*cb)(long long, long long, long long, long long, long long, long long, long long, long long,
                                          double, double, double, double, double, double, double, double, struct S16,
                                          char, char, char, char, char, char, char, char, char, char, char, char, char, char, char, char),
                          long long pf0, long long pf1, long long pf2, long long pf3, long long pf4, long long pf5, long long pf6, long long pf7,
                          double pf8, double pf9, double pf10, double pf11, double pf12, double pf13, double pf14, double pf15,
                          struct S16 a0) {
    return cb(pf0, pf1, pf2, pf3, pf4, pf5, pf6, pf7, pf8, pf9, pf10, pf11, pf12, pf13, pf14, pf15, a0,
              a0.f0[0], a0.f0[1], a0.f0[2], a0.f0[3], a0.f0[4], a0.f0[5], a0.f0[6], a0.f0[7], a0.f0[8], a0.f0[9], a0.f0[10], a0.f0[11], a0.f0[12], a0.f0[13], a0.f0[14], a0.f0[15]);
}
