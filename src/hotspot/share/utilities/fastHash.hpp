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

#ifndef SHARE_UTILITIES_FASTHASH_HPP
#define SHARE_UTILITIES_FASTHASH_HPP

#include "memory/allStatic.hpp"

class FastHash : public AllStatic {
private:
  static void fullmul64(uint64_t& hi, uint64_t& lo, uint64_t op1, uint64_t op2) {
#if defined(__SIZEOF_INT128__)
    __uint128_t prod = static_cast<__uint128_t>(op1) * static_cast<__uint128_t>(op2);
    hi = static_cast<uint64_t>(prod >> 64);
    lo = static_cast<uint64_t>(prod >>  0);
#else
    /* First calculate all of the cross products. */
    uint64_t lo_lo = (op1 & 0xFFFFFFFF) * (op2 & 0xFFFFFFFF);
    uint64_t hi_lo = (op1 >> 32)        * (op2 & 0xFFFFFFFF);
    uint64_t lo_hi = (op1 & 0xFFFFFFFF) * (op2 >> 32);
    uint64_t hi_hi = (op1 >> 32)        * (op2 >> 32);

    /* Now add the products together. These will never overflow. */
    uint64_t cross = (lo_lo >> 32) + (hi_lo & 0xFFFFFFFF) + lo_hi;
    uint64_t upper = (hi_lo >> 32) + (cross >> 32)        + hi_hi;
    hi = upper;
    lo = (cross << 32) | (lo_lo & 0xFFFFFFFF);
#endif
  }

  static void fullmul32(uint32_t& hi, uint32_t& lo, uint32_t op1, uint32_t op2) {
    uint64_t x64 = op1, y64 = op2, xy64 = x64 * y64;
    hi = (uint32_t)(xy64 >> 32);
    lo = (uint32_t)(xy64 >>  0);
  }

  static uint64_t ror(uint64_t x, uint64_t distance) {
    distance = distance & 0x3F;
    return (x >> distance) | (x << (64 - distance));
  }

public:
  static uint64_t get_hash64(uint64_t x, uint64_t y) {
    const uint64_t M  = 0x8ADAE89C337954D5;
    const uint64_t A  = 0xAAAAAAAAAAAAAAAA; // REPAA
    const uint64_t H0 = (x ^ y), L0 = (x ^ A);

    uint64_t U0, V0; fullmul64(U0, V0, L0, M);
    const uint64_t Q0 = (H0 * M);
    const uint64_t L1 = (Q0 ^ U0);

    uint64_t U1, V1; fullmul64(U1, V1, L1, M);
    const uint64_t P1 = (V0 ^ M);
    const uint64_t Q1 = ror(P1, L1);
    const uint64_t L2 = (Q1 ^ U1);
    return V1 ^ L2;
  }

  static uint32_t get_hash32(uint32_t x, uint32_t y) {
    const uint32_t M  = 0x337954D5;
    const uint32_t A  = 0xAAAAAAAA; // REPAA
    const uint32_t H0 = (x ^ y), L0 = (x ^ A);

    uint32_t U0, V0; fullmul32(U0, V0, L0, M);
    const uint32_t Q0 = (H0 * M);
    const uint32_t L1 = (Q0 ^ U0);

    uint32_t U1, V1; fullmul32(U1, V1, L1, M);
    const uint32_t P1 = (V0 ^ M);
    const uint32_t Q1 = ror(P1, L1);
    const uint32_t L2 = (Q1 ^ U1);
    return V1 ^ L2;
  }
};

#endif// SHARE_UTILITIES_FASTHASH_HPP
