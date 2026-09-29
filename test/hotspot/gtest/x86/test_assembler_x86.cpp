/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

TEST_VM(AssemblerX86, validate) {
  UseAVX = 3;
  FlagSetting flag_change_apx(UseAPX, true);
  VM_Version::set_bmi_cpuFeatures();
  VM_Version::set_evex_cpuFeatures();
  VM_Version::set_avx_cpuFeatures();
  VM_Version::set_apx_cpuFeatures();
  BufferBlob* b = BufferBlob::create("x64Test", 5000000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);
  address entry = __ pc();

  // To build asmtest.out.h, ensure you have binutils version 2.34 or higher, then run:
  // python3 x86-asmtest.py | expand > asmtest.out.h to generate tests with random inputs
  // python3 x86-asmtest.py --full | expand > asmtest.out.h to generate tests with all possible inputs
#include "asmtest.out.h"

  asm_check((const uint8_t *)entry, (const uint8_t *)insns, insns_lens, insns_strs, sizeof(insns_lens) / sizeof(insns_lens[0]));
  BufferBlob::free(b);
}

#endif // X86
