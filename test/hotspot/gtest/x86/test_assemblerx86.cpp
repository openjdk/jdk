#include "precompiled.hpp"

#if defined(X86)

#include "utilities/vmassert_uninstall.hpp"
#include <unordered_map>
#include "utilities/vmassert_reinstall.hpp"

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "memory/resourceArea.hpp"

#include "unittest.hpp"

#define __ _masm.

static std::unordered_map<std::string, std::pair<uint8_t, uint8_t>> insns_map = {
  // Different encoding for GCC and OpenJDK
  {"shll", {'\xd3', '\xd1'}},
  {"shlq", {'\xd3', '\xd1'}},
  {"shrl", {'\xd3', '\xd1'}},
  {"shrq", {'\xd3', '\xd1'}},
  {"rorl", {'\xd3', '\xd1'}},
  {"rorq", {'\xd3', '\xd1'}},
  {"roll", {'\xd3', '\xd1'}},
  {"rolq", {'\xd3', '\xd1'}},
  {"sarl", {'\xd3', '\xd1'}},
  {"sarq", {'\xd3', '\xd1'}},
};

static void asm_check(const uint8_t *insns, const uint8_t *insns1, const unsigned int *insns_lens, const char *insns_strs[], size_t len) {
  ResourceMark rm;
  size_t cur_idx = 0;
  for (size_t i = 0; i < len; i++) {
    size_t insn_len = insns_lens[i];
    const char *insn = insns_strs[i];
    std::string insn_str(insn);
    std::string insns_name = insn_str.substr(3, insn_str.find('(') - 3);

    auto p = std::mismatch(&insns[cur_idx], &insns[cur_idx + insn_len], &insns1[cur_idx], &insns1[cur_idx + insn_len], [&insns_name](uint8_t a, uint8_t b) {
      return (a == b) || (insns_map.find(insns_name) != insns_map.end() && a == insns_map[insns_name].first && b == insns_map[insns_name].second);
    });
    if (p.first != &insns[cur_idx + insn_len]) {
      stringStream ss;
      ss.print("%s\n", insn);
      ss.print("Ours:   ");
      for (size_t j = 0; j < insn_len; j++) {
        ss.print("%02x ", (uint8_t)insns[cur_idx + j]);
      }
      ss.print_cr("");
      ss.print("Theirs: ");
      for (size_t j = 0; j < insn_len; j++) {
        ss.print("%02x ", (uint8_t)insns1[cur_idx + j]);
      }
      ADD_FAILURE() << ss.as_string();
    }
    cur_idx += insn_len;
  }
}

TEST_VM(AssemblerX86, validate) {
  FlagSetting flag_change_apx(UseAPX, true);
  VM_Version::set_apx_cpuFeatures();
  BufferBlob* b = BufferBlob::create("x64Test", 500000);
  CodeBuffer code(b);
  MacroAssembler _masm(&code);
  address entry = __ pc();

  // python x86-asmtest.py | expand > asmtest.out.h
#include "asmtest.out.h"

  asm_check((const uint8_t *)entry, (const uint8_t *)insns, insns_lens, insns_strs, sizeof(insns_lens) / sizeof(insns_lens[0]));
  BufferBlob::free(b);
}

#endif // X86