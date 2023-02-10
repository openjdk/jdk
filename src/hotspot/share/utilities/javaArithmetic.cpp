/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

//----------------------magic_int_divide_constants-----------------------------
// Compute magic multiplier and shift constant for converting a 32 bit divide
// by constant into a multiply/shift series.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.

void magic_int_divide_constants(jint d, jlong& M, jint& s) {
  assert(d > 1, "sanity");
  int32_t p;
  jlong ad, anc, delta, q1, r1, q2, r2, t;
  const jlong two31 = 0x80000000L; // 2**31.

  ad = jlong(d);
  t = two31;
  anc = t - 1 - t%ad;     // Absolute value of nc.
  p = 31;                 // Init. p.
  q1 = two31/anc;         // Init. q1 = 2**p/|nc|.
  r1 = two31 - q1*anc;    // Init. r1 = rem(2**p, |nc|).
  q2 = two31/ad;          // Init. q2 = 2**p/|d|.
  r2 = two31 - q2*ad;     // Init. r2 = rem(2**p, |d|).
  do {
    p = p + 1;
    q1 = 2*q1;            // Update q1 = 2**p/|nc|.
    r1 = 2*r1;            // Update r1 = rem(2**p, |nc|).
    if (r1 >= anc) {      // (Must be an unsigned
      q1 = q1 + 1;        // comparison here).
      r1 = r1 - anc;
    }
    q2 = 2*q2;            // Update q2 = 2**p/|d|.
    r2 = 2*r2;            // Update r2 = rem(2**p, |d|).
    if (r2 >= ad) {       // (Must be an unsigned
      q2 = q2 + 1;        // comparison here).
      r2 = r2 - ad;
    }
    delta = ad - r2;
  } while (q1 < delta || (q1 == delta && r1 == 0));

  M = q2 + 1;             // Magic number and
  s = p - 32;             // shift amount to return.
}

//---------------magic_int_unsigned_divide_constants_down----------------------
// Compute magic multiplier and shift constant for converting a 32 bit divide
// by constant into a multiply/add/shift series.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.
void magic_int_unsigned_divide_constants_down(juint d, jlong& M, jint& s) {
  assert(d > 1, "sanity");
  jlong two31 = jlong(juint(min_jint));
  jlong two31m1 = jlong(juint(max_jint));

  jint p;
  jlong nc, delta, q1, r1, q2, r2;

  jlong ad = jlong(d);
  nc = jlong(max_juint) - (two31 * 2 - ad)%ad;
  p = 31;                  // Init. p.
  q1 = two31/nc;           // Init. q1 = 2**p/nc.
  r1 = two31 - q1*nc;      // Init. r1 = rem(2**p, nc).
  q2 = two31m1/ad;         // Init. q2 = (2**p - 1)/d.
  r2 = two31m1 - q2*ad;    // Init. r2 = rem(2**p - 1, d).
  do {
    p = p + 1;
    if (r1 >= nc - r1) {
      q1 = 2*q1 + 1;       // Update q1.
      r1 = 2*r1 - nc;      // Update r1.
    } else {
      q1 = 2*q1;
      r1 = 2*r1;
    }
    if (r2 + 1 >= ad - r2) {
      q2 = 2*q2 + 1;       // Update q2.
      r2 = 2*r2 + 1 - ad;  // Update r2.
    } else {
      q2 = 2*q2;
      r2 = 2*r2 + 1;
    }
    delta = ad - 1 - r2;
  } while (p < 64 && (q1 < delta || (q1 == delta && r1 == 0)));
  M = q2 + 1; // Magic number
  s = p - 32; // and shift amount to return
}

//-----------------magic_int_unsigned_divide_constants_up----------------------
// Compute magic multiplier and shift constant for converting a 32 bit divide
// by constant into a multiply/add/shift series.
//
// Borrowed almost verbatim from N-Bit Unsigned Division Via N-Bit Multiply-Add
// by Arch D. Robison
//
// Call this up since we do this after failing with the down attempt
void magic_int_unsigned_divide_constants_up(juint d, jlong& M, jint& s) {
  assert(d > 1, "sanity");
  jint N = 32;
  s = log2i_graceful(d);
  julong t = (julong(1) << (s + N)) / julong(d);
  M = t;
#ifdef ASSERT
  julong r = ((t + 1) * julong(d)) & julong(max_juint);
  assert(r > (julong(1) << s), "Should call down first since it is more efficient");
#endif
}

//---------------------magic_long_divide_constants-----------------------------
// Compute magic multiplier and shift constant for converting a 64 bit divide
// by constant into a multiply/shift/add series.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.  Adjusted to 64 bit word width.
void magic_long_divide_constants(jlong d, jlong& M, jint& s) {
  assert(d > 1, "sanity");

  int64_t p;
  uint64_t ad, anc, delta, q1, r1, q2, r2, t;
  const uint64_t two63 = UCONST64(0x8000000000000000);     // 2**63.

  ad = ABS(d);
  t = two63;
  anc = t - 1 - t%ad;     // Absolute value of nc.
  p = 63;                 // Init. p.
  q1 = two63/anc;         // Init. q1 = 2**p/|nc|.
  r1 = two63 - q1*anc;    // Init. r1 = rem(2**p, |nc|).
  q2 = two63/ad;          // Init. q2 = 2**p/|d|.
  r2 = two63 - q2*ad;     // Init. r2 = rem(2**p, |d|).
  do {
    p = p + 1;
    q1 = 2*q1;            // Update q1 = 2**p/|nc|.
    r1 = 2*r1;            // Update r1 = rem(2**p, |nc|).
    if (r1 >= anc) {      // (Must be an unsigned
      q1 = q1 + 1;        // comparison here).
      r1 = r1 - anc;
    }
    q2 = 2*q2;            // Update q2 = 2**p/|d|.
    r2 = 2*r2;            // Update r2 = rem(2**p, |d|).
    if (r2 >= ad) {       // (Must be an unsigned
      q2 = q2 + 1;        // comparison here).
      r2 = r2 - ad;
    }
    delta = ad - r2;
  } while (q1 < delta || (q1 == delta && r1 == 0));

  M = q2 + 1;
  s = p - 64;             // shift amount to return.
}

//-----------------magic_long_unsigned_divide_constants------------------------
// Compute magic multiplier and shift constant for converting a 64 bit divide
// by constant into a multiply/shift/add series.
//
// Borrowed almost verbatim from Hacker's Delight by Henry S. Warren, Jr. with
// minor type name and parameter changes.  Adjusted to 64 bit word width.
void magic_long_unsigned_divide_constants(julong d, jlong& M, jint& s, bool& magic_const_ovf) {
  assert(d > 1, "sanity");
  julong two63 = julong(min_jlong);
  julong two63m1 = julong(max_jlong);

  jint p;
  julong nc, delta, q1, r1, q2, r2;

  nc = -1 - (-d)%d;       // Unsigned arithmetic here.
  p = 63;                 // Init. p.
  q1 = two63/nc;          // Init. q1 = 2**p/nc.
  r1 = two63 - q1*nc;     // Init. r1 = rem(2**p, nc).
  q2 = two63m1/d;         // Init. q2 = (2**p - 1)/d.
  r2 = two63m1 - q2*d;    // Init. r2 = rem(2**p - 1, d).
  magic_const_ovf = false;
  do {
    p = p + 1;
    if (r1 >= nc - r1) {
      q1 = 2*q1 + 1;      // Update q1.
      r1 = 2*r1 - nc;     // Update r1.
    } else {
      q1 = 2*q1;
      r1 = 2*r1;
    }
    if (r2 + 1 >= d - r2) {
      if (q2 >= two63m1) {
        magic_const_ovf = true;
      }
      q2 = 2*q2 + 1;      // Update q2.
      r2 = 2*r2 + 1 - d;  // Update r2.
    } else {
      if (q2 >= two63) {
        magic_const_ovf = true;
      }
      q2 = 2*q2;
      r2 = 2*r2 + 1;
    }
    delta = d - 1 - r2;
  } while (p < 128 && (q1 < delta || (q1 == delta && r1 == 0)));
  M = q2 + 1;             // Magic number
  s = p - 64;             // and shift amount to return
}
