/*
 * Copyright (c) 2026, SAP SE. All rights reserved.
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

#if defined(PPC64) && !defined(ZERO)

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

#include <vector>

#define DEVELOPMENT_WIP 0

// Format a 64-bit value as "0x..." into a caller-owned buffer, for messages.
static const char* hex64(uint64_t x, char* buf, size_t len) {
  (void)os::snprintf(buf, len, UINT64_FORMAT_X_0, x);
  return buf;
}

// ---------------------------------------------------------------------------
// Tests for Assembler::load_const_optimized

typedef long (*load_const_func)();

// Emit load_const_optimized (or the new version) into a stub, execute it, and
// return the loaded value.
static long run_load_const(long x, Register d, bool have_tmp, bool return_simm16_rest,
                           bool use_new, int& out_insns) {
  ResourceMark rm;
  BufferBlob* bb = BufferBlob::create("load_const_optimized_test", 256);
  guarantee(bb != nullptr, "allocation failed");
  CodeBuffer code(bb);
  MacroAssembler _masm(&code);

  if (Verbose) {
    char xb[24], rb[24], bb_[24];
    tty->print_cr("run_load_const: x=" UINT64_FORMAT_X_0 " d=%s tmp=%s return_simm16_rest=%s use_new=%s",
                  x,
                  (d == R0 ? "R0" : "R3_RET"),
                  (have_tmp ? "R4_ARG2" : "noreg"),
                  (return_simm16_rest ? "true" : "false"),
                  (use_new ? "true" : "false"));
  }

  Register tmp = have_tmp ? R4_ARG2 : noreg;
  address start = _masm.pc();
  int simm16_rest =
#if DEVELOPMENT_WIP
    use_new ? _masm.load_const_optimized_new(d, x, tmp, return_simm16_rest) :
#endif
    _masm.load_const_optimized(d, x, tmp, return_simm16_rest);
  out_insns = (int)((_masm.pc() - start) / BytesPerInstWord);
  _masm.mr_if_needed(R3_RET, d);
  _masm.blr();

  _masm.invalidate_icache();

  long result_base = ((load_const_func)start)();
  long result = result_base + simm16_rest;

  if (Verbose) {
    char xb[24], rb[24], bb_[24];
    tty->print_cr("    insns=%d result_base=" UINT64_FORMAT_X_0 " simm16_rest=%d result=" UINT64_FORMAT_X_0,
                  out_insns, result_base, simm16_rest, result);
  }

  BufferBlob::free(bb);
  return result;
}

// Verify that load_const_optimized loads x correctly for all combinations of
// d (R3_RET / R0), tmp (noreg / R4_ARG2) and return_simm16_rest (false / true),
// and that the new version never emits more instructions than the old one.
static void check_value(long x) {
  char xb[24];
  const Register dregs[] = { R3_RET, R0 };
  for (Register d : dregs) {
    for (int m = 0; m < 4; m++) {
      bool have_tmp           = (m & 1) != 0;
      bool return_simm16_rest = (m & 2) != 0;
      int insns = 0;
      long result     = run_load_const(x, d, have_tmp, return_simm16_rest, false, insns);
      EXPECT_EQ(result, x)
          << "wrong value for x=" << hex64((uint64_t)x, xb, sizeof(xb))
          << (d == R0             ? " (d=R0)"               : " (d=R3_RET)")
          << (have_tmp            ? " (with tmp)"           : " (no tmp)")
          << (return_simm16_rest  ? " (return_simm16_rest)" : "");
#if DEVELOPMENT_WIP
      int insns_new = 0;
      long result_new = run_load_const(x, d, have_tmp, return_simm16_rest, true,  insns_new);
      EXPECT_EQ(result_new, x)
          << "wrong value (new) for x=" << hex64((uint64_t)x, xb, sizeof(xb))
          << (d == R0             ? " (d=R0)"               : " (d=R3_RET)")
          << (have_tmp            ? " (with tmp)"           : " (no tmp)")
          << (return_simm16_rest  ? " (return_simm16_rest)" : "");
      EXPECT_LE(insns_new, insns)
          << "load_const_optimized_new emitted more instructions than old for x="
          << hex64((uint64_t)x, xb, sizeof(xb))
          << " (" << insns_new << " vs " << insns << ")"
          << (d == R0             ? " (d=R0)"               : " (d=R3_RET)")
          << (have_tmp            ? " (with tmp)"           : " (no tmp)")
          << (return_simm16_rest  ? " (return_simm16_rest)" : "");
#endif
    }
  }
}

// Add each combination of the 2 highest and 2 lowest bits of each halfword.
// Foreach of the resulting constants add every combination of the 12 bits in the middle
// being either all set or all unset.
static void add_halfword_edge_bits(std::vector<long>& v) {
  // There are 4 bits in each halfword. Thats 16 bits in total.
  for (unsigned c = 0; c <= 0xffff /*16 bits*/; c++) {
    uint64_t x = 0;
    for (int hw = 0; hw < 4; hw++) {
      unsigned bits4 = (c >> (hw * 4)) & 0xf;
      uint64_t lo2   = (bits4 >> 0) & 0x3;   // -> half-word bits [1:0]
      uint64_t hi2   = (bits4 >> 2) & 0x3;   // -> half-word bits [15:14]
      uint64_t hw_val = lo2 | (hi2 << 14);
      x |= hw_val << (hw * 16);
    }
    // set 12 middle bits of each half word
    const uint64_t ones = UCONST64(0x3ffc);
    for (int i = 0; i < 2; i++) {
      v.push_back((long)(x | ((0*ones)<<32)  | ((0*ones)<<16) | (0*ones)));
      v.push_back((long)(x | ((0*ones)<<32)  | ((0*ones)<<16) | (1*ones)));
      v.push_back((long)(x | ((0*ones)<<32)  | ((1*ones)<<16) | (0*ones)));
      v.push_back((long)(x | ((0*ones)<<32)  | ((1*ones)<<16) | (1*ones)));
      v.push_back((long)(x | ((1*ones)<<32)  | ((0*ones)<<16) | (0*ones)));
      v.push_back((long)(x | ((1*ones)<<32)  | ((0*ones)<<16) | (1*ones)));
      v.push_back((long)(x | ((1*ones)<<32)  | ((1*ones)<<16) | (0*ones)));
      v.push_back((long)(x | ((1*ones)<<32)  | ((1*ones)<<16) | (1*ones)));
      x = x | (ones<<48);
    }
  }
}

// Single runs of consecutive ones (and complements / wrapping runs).
static void add_consec_ones(std::vector<long>& v) {
  for (int len = 1; len <= 64; len++) {
    uint64_t mask = (len >= 64) ? ~(uint64_t)0 : (((uint64_t)1 << len) - 1);
    for (int pos = 0; pos + len <= 64; pos++) {
      long x = (long)(mask << pos);
      v.push_back(x);
      v.push_back(~x);
    }
  }
}

// Add every combination of the 4 lowest and 4 highest bits for a 16 bit value.
// Put every value from above at each position in a 64 bit value.
// Double the values by setting the bits in the middle of the 16 bit values to 1.
static void add_field16(std::vector<long>& v) {
  for (uint64_t c = 1; c <= 0xff; c++) {
    uint64_t lo = c & 0xf;
    uint64_t hi = (c >> 4) & 0xf;
    uint64_t value = hi | lo;
    uint64_t value2 = value | 0x0ff0;
    for (int pos = 0; pos <= 48; pos++) {
      v.push_back((long)(value << pos));
      v.push_back((long)(value2 << pos));
    }
  }
}

// ---- Tests ---------------------------------------------------------------

TEST_VM(AssemblerPPC, load_const_optimized_regression) {
  std::vector<long> patterns;
  add_halfword_edge_bits(patterns);

  for (long x : patterns) {
    check_value(x);
  }
}

TEST_VM(AssemblerPPC, load_const_optimized_consec_ones) {
  std::vector<long> patterns;
  add_consec_ones(patterns);
  for (long x : patterns) {
    check_value(x);
  }
}

TEST_VM(AssemblerPPC, load_const_optimized_field16) {
  std::vector<long> patterns;
  add_field16(patterns);
  for (long x : patterns) {
    check_value(x);
  }
}

#if DEVELOPMENT_WIP
TEST_VM(AssemblerPPC, load_const_optimized_dev) {
  std::vector<long> patterns;
  patterns.push_back(0x000000000000ffffull);
  patterns.push_back(0xff0000000000ffffull);
  patterns.push_back(0xffff00000000ffffull);
  patterns.push_back(0xffffffff7fff8000ull);
  for (long x : patterns) {
    check_value(x);
  }
}
#endif

#endif // PPC64 && !ZERO
