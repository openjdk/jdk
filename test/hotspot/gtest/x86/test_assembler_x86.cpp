/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#if defined(X86) && !defined(ZERO)

#include "utilities/vmassert_uninstall.hpp"
#include <cstring>
#include "utilities/vmassert_reinstall.hpp"

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "code/codeCache.hpp"
#include "memory/resourceArea.hpp"

#include "unittest.hpp"

#define __ _masm.

static void asm_check(const uint8_t *insns, const uint8_t *insns1, const unsigned int *insns_lens, const char *insns_strs[], size_t len) {
  ResourceMark rm;
  size_t cur_idx = 0;
  for (size_t i = 0; i < len; i++) {
    size_t insn_len = insns_lens[i];
    const char *insn = insns_strs[i];
    std::string insn_str(insn);
    std::string insns_name = insn_str.substr(3, insn_str.find('(') - 3);

    if (std::memcmp(&insns[cur_idx], &insns1[cur_idx], insn_len) != 0) {
      stringStream ss;
      ss.print("%s\n", insn);
      ss.print("OpenJDK:       ");
      for (size_t j = 0; j < insn_len; j++) {
        ss.print("%02x ", (uint8_t)insns[cur_idx + j]);
      }
      ss.print_cr("");
      ss.print("GNU Assembler: ");
      for (size_t j = 0; j < insn_len; j++) {
        ss.print("%02x ", (uint8_t)insns1[cur_idx + j]);
      }
      ADD_FAILURE() << ss.as_string();
    }
    cur_idx += insn_len;
  }
}

// Full synthetic x86 feature profile for asmtest: the features needed for testing
// the encoding of various instructions.
// Key settings:
//   use_avx() == 3              - EVEX encoding and 512-bit vectors
//   use_apx() == true           - extended GPRs (r16-r31), REX2 and APX EVEX forms
//   supports_evex() == true     - EVEX instructions
//   supports_avx512vl() == true - EVEX at 128/256-bit vector lengths
//   cpu_supports_evex() == true - accepted by the locate_operand() EVEX assert
// Some features also select encodings (e.g. avx512bw/dq, avx512ifma), so the
// expected bytes in asmtest.out.h depend on this whole profile.
// Values that VM_Version derives from others must stay consistent:
//   supports_avx512novl() == supports_evex() && !supports_avx512vl()
//   supports_avx512vlbw() == supports_evex() && supports_avx512bw() && supports_avx512vl()
//   supports_avx512vldq() == supports_evex() && supports_avx512dq() && supports_avx512vl()
class SyntheticAssemblerFeatures : public AbstractAssemblerFeatures {
 public:
  int use_avx() const override { return 3; }
  bool use_apx() const override { return true; }
  bool supports_3dnow_prefetch() const override { return true; }
  bool supports_adx() const override { return true; }
  bool supports_aes() const override { return true; }
  bool supports_apx_f() const override { return true; }
  bool supports_avx() const override { return true; }
  bool supports_avx10_2() const override { return true; }
  bool supports_avx2() const override { return true; }
  bool supports_avx512_bitalg() const override { return true; }
  bool supports_avx512_fp16() const override { return true; }
  bool supports_avx512_vbmi() const override { return true; }
  bool supports_avx512_vbmi2() const override { return true; }
  bool supports_avx512_vnni() const override { return true; }
  bool supports_avx512_vpclmulqdq() const override { return true; }
  bool supports_avx512_vpopcntdq() const override { return true; }
  bool supports_avx512bw() const override { return true; }
  bool supports_avx512cd() const override { return true; }
  bool supports_avx512dq() const override { return true; }
  bool supports_avx512ifma() const override { return true; }
  bool supports_avx512novl() const override { return false; }
  bool supports_avx512vl() const override { return true; }
  bool supports_avx512vlbw() const override { return true; }
  bool supports_avx512vldq() const override { return true; }
  bool supports_avxifma() const override { return true; }
  bool supports_bmi1() const override { return true; }
  bool supports_bmi2() const override { return true; }
  bool supports_clflushopt() const override { return true; }
  bool supports_clmul() const override { return true; }
  bool supports_clwb() const override { return true; }
  bool cpu_supports_evex() const override { return true; }
  bool supports_evex() const override { return true; }
  bool supports_f16c() const override { return true; }
  bool supports_fma() const override { return true; }
  bool supports_gfni() const override { return true; }
  bool supports_lzcnt() const override { return true; }
  bool supports_popcnt() const override { return true; }
  bool supports_serialize() const override { return true; }
  bool supports_sha() const override { return true; }
  bool supports_sha512() const override { return true; }
  bool supports_sse3() const override { return true; }
  bool supports_sse4_1() const override { return true; }
  bool supports_sse4_2() const override { return true; }
  bool supports_ssse3() const override { return true; }
  bool supports_vaes() const override { return true; }
  bool supports_vzeroupper() const override { return true; }
};

TEST_VM(AssemblerX86, validate) {
  SyntheticAssemblerFeatures asm_features;

  BufferBlob* b = BufferBlob::create("x64Test", 5000000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code, asm_features);
  address entry = __ pc();

  // To build asmtest.out.h, ensure you have binutils version 2.34 or higher, then run:
  // python3 x86-asmtest.py | expand > asmtest.out.h to generate tests with random inputs
  // python3 x86-asmtest.py --full | expand > asmtest.out.h to generate tests with all possible inputs
#include "asmtest.out.h"

  asm_check((const uint8_t *)entry, (const uint8_t *)insns, insns_lens, insns_strs, sizeof(insns_lens) / sizeof(insns_lens[0]));
  BufferBlob::free(b);
}

#endif // X86
