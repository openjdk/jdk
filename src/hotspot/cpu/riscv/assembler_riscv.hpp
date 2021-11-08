/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

#ifndef CPU_RISCV_ASSEMBLER_RISCV_HPP
#define CPU_RISCV_ASSEMBLER_RISCV_HPP

#include "asm/register.hpp"
#include "assembler_riscv.inline.hpp"

#define registerSize 64

// definitions of various symbolic names for machine registers

// First intercalls between C and Java which use 8 general registers
// and 8 floating registers

class Argument {
 public:
  enum {
    n_int_register_parameters_c   = 8,  // x10, x11, ... x17 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8,  // f10, f11, ... f17 (c_farg0, c_farg1, ... )

    n_int_register_parameters_j   = 8, // x11, ... x17, x10 (rj_rarg0, j_rarg1, ...)
    n_float_register_parameters_j = 8  // f10, f11, ... f17 (j_farg0, j_farg1, ...)
  };
};

// function argument(caller-save registers)
REGISTER_DECLARATION(Register, c_rarg0, x10);
REGISTER_DECLARATION(Register, c_rarg1, x11);
REGISTER_DECLARATION(Register, c_rarg2, x12);
REGISTER_DECLARATION(Register, c_rarg3, x13);
REGISTER_DECLARATION(Register, c_rarg4, x14);
REGISTER_DECLARATION(Register, c_rarg5, x15);
REGISTER_DECLARATION(Register, c_rarg6, x16);
REGISTER_DECLARATION(Register, c_rarg7, x17);

REGISTER_DECLARATION(FloatRegister, c_farg0, f10);
REGISTER_DECLARATION(FloatRegister, c_farg1, f11);
REGISTER_DECLARATION(FloatRegister, c_farg2, f12);
REGISTER_DECLARATION(FloatRegister, c_farg3, f13);
REGISTER_DECLARATION(FloatRegister, c_farg4, f14);
REGISTER_DECLARATION(FloatRegister, c_farg5, f15);
REGISTER_DECLARATION(FloatRegister, c_farg6, f16);
REGISTER_DECLARATION(FloatRegister, c_farg7, f17);

// java function register(caller-save registers)
REGISTER_DECLARATION(Register, j_rarg0, c_rarg1);
REGISTER_DECLARATION(Register, j_rarg1, c_rarg2);
REGISTER_DECLARATION(Register, j_rarg2, c_rarg3);
REGISTER_DECLARATION(Register, j_rarg3, c_rarg4);
REGISTER_DECLARATION(Register, j_rarg4, c_rarg5);
REGISTER_DECLARATION(Register, j_rarg5, c_rarg6);
REGISTER_DECLARATION(Register, j_rarg6, c_rarg7);
REGISTER_DECLARATION(Register, j_rarg7, c_rarg0);

REGISTER_DECLARATION(FloatRegister, j_farg0, f10);
REGISTER_DECLARATION(FloatRegister, j_farg1, f11);
REGISTER_DECLARATION(FloatRegister, j_farg2, f12);
REGISTER_DECLARATION(FloatRegister, j_farg3, f13);
REGISTER_DECLARATION(FloatRegister, j_farg4, f14);
REGISTER_DECLARATION(FloatRegister, j_farg5, f15);
REGISTER_DECLARATION(FloatRegister, j_farg6, f16);
REGISTER_DECLARATION(FloatRegister, j_farg7, f17);

// zero rigster
REGISTER_DECLARATION(Register, zr,        x0);
// global pointer
REGISTER_DECLARATION(Register, gp,        x3);
// thread pointer
REGISTER_DECLARATION(Register, tp,        x4);

// volatile (caller-save) registers

// current method -- must be in a call-clobbered register
REGISTER_DECLARATION(Register, xmethod,   x31);
// return address
REGISTER_DECLARATION(Register, ra,        x1);
// link rigster
REGISTER_DECLARATION(Register, lr,        x1);


// non-volatile (callee-save) registers

// stack pointer
REGISTER_DECLARATION(Register, sp,        x2);
// frame pointer
REGISTER_DECLARATION(Register, fp,        x8);
// base of heap
REGISTER_DECLARATION(Register, xheapbase, x27);
// constant pool cache
REGISTER_DECLARATION(Register, xcpool,    x26);
// monitors allocated on stack
REGISTER_DECLARATION(Register, xmonitors, x25);
// locals on stack
REGISTER_DECLARATION(Register, xlocals,   x24);

/* If you use x4(tp) as java thread pointer according to the instruction manual,
 * it overlaps with the register used by c++ thread.
 */
// java thread pointer
REGISTER_DECLARATION(Register, xthread,   x23);
// bytecode pointer
REGISTER_DECLARATION(Register, xbcp,      x22);
// Dispatch table base
REGISTER_DECLARATION(Register, xdispatch, x21);
// Java stack pointer
REGISTER_DECLARATION(Register, esp,       x20);

// tempory register(caller-save registers)
REGISTER_DECLARATION(Register, t0, x5);
REGISTER_DECLARATION(Register, t1, x6);
REGISTER_DECLARATION(Register, t2, x7);

const Register g_INTArgReg[Argument::n_int_register_parameters_c] = {
  c_rarg0, c_rarg1, c_rarg2, c_rarg3, c_rarg4, c_rarg5,  c_rarg6,  c_rarg7
};

const FloatRegister g_FPArgReg[Argument::n_float_register_parameters_c] = {
  c_farg0, c_farg1, c_farg2, c_farg3, c_farg4, c_farg5, c_farg6, c_farg7
};

#define assert_cond(ARG1) assert(ARG1, #ARG1)

// Addressing modes
class Address {
 public:

  enum mode { no_mode, base_plus_offset, pcrel, literal };

 private:
  Register _base;
  Register _index;
  int64_t _offset;
  enum mode _mode;

  RelocationHolder _rspec;

  // If the target is far we'll need to load the ea of this to a
  // register to reach it. Otherwise if near we can do PC-relative
  // addressing.
  address          _target;

 public:
  Address()
    : _base(noreg), _index(noreg), _offset(0), _mode(no_mode), _target(NULL) { }
  Address(Register r)
    : _base(r),     _index(noreg), _offset(0), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, int o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, long o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, long long o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, unsigned int o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, unsigned long o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, unsigned long long o)
    : _base(r),     _index(noreg), _offset(o), _mode(base_plus_offset), _target(NULL) { }
  Address(Register r, ByteSize disp)
    : Address(r, in_bytes(disp)) {}
  Address(address target, RelocationHolder const& rspec)
    : _base(noreg),
      _index(noreg),
      _offset(0),
      _mode(literal),
      _rspec(rspec),
      _target(target) { }
  Address(address target, relocInfo::relocType rtype = relocInfo::external_word_type);

  const Register base() const {
    guarantee((_mode == base_plus_offset | _mode == pcrel | _mode == literal), "wrong mode");
    return _base;
  }
  long offset() const {
    return _offset;
  }
  Register index() const {
    return _index;
  }
  mode getMode() const {
    return _mode;
  }

  bool uses(Register reg) const { return _base == reg; }
  const address target() const { return _target; }
  const RelocationHolder& rspec() const { return _rspec; }
  ~Address() {
    _target = NULL;
    _base = NULL;
  }
};

// Convience classes
class RuntimeAddress: public Address {

  public:

  RuntimeAddress(address target) : Address(target, relocInfo::runtime_call_type) {}
  ~RuntimeAddress() {}
};

class OopAddress: public Address {

  public:

  OopAddress(address target) : Address(target, relocInfo::oop_type) {}
  ~OopAddress() {}
};

class ExternalAddress: public Address {
 private:
  static relocInfo::relocType reloc_for_target(address target) {
    // Sometimes ExternalAddress is used for values which aren't
    // exactly addresses, like the card table base.
    // external_word_type can't be used for values in the first page
    // so just skip the reloc in that case.
    return external_word_Relocation::can_be_relocated(target) ? relocInfo::external_word_type : relocInfo::none;
  }

 public:

  ExternalAddress(address target) : Address(target, reloc_for_target(target)) {}
  ~ExternalAddress() {}
};

class InternalAddress: public Address {

  public:

  InternalAddress(address target) : Address(target, relocInfo::internal_word_type) {}
  ~InternalAddress() {}
};

class Assembler : public AbstractAssembler {
public:

  enum { instruction_size = 4 };

  //---<  calculate length of instruction  >---
  // We just use the values set above.
  // instruction must start at passed address
  static unsigned int instr_len(unsigned char *instr) { return instruction_size; }

  //---<  longest instructions  >---
  static unsigned int instr_maxlen() { return instruction_size; }

  enum RoundingMode {
    rne = 0b000,     // round to Nearest, ties to Even
    rtz = 0b001,     // round towards Zero
    rdn = 0b010,     // round Down (towards eegative infinity)
    rup = 0b011,     // round Up (towards infinity)
    rmm = 0b100,     // round to Nearest, ties to Max Magnitude
    rdy = 0b111,     // in instruction's rm field, selects dynamic rounding mode.In Rounding Mode register, Invalid.
  };

  void baseOffset32(Register temp, const Address &adr, int32_t &offset) {
    assert(temp != noreg, "temp must not be empty register!");
    guarantee(adr.base() != temp, "should use different registers!");
    if (is_offset_in_range(adr.offset(), 32)) {
      int32_t imm = adr.offset();
      int32_t upper = imm, lower = imm;
      lower = (imm << 20) >> 20;
      upper -= lower;
      lui(temp, upper);
      offset = lower;
    } else {
      movptr_with_offset(temp, (address)(uintptr_t)adr.offset(), offset);
    }
    add(temp, temp, adr.base());
  }

  void baseOffset(Register temp, const Address &adr, int32_t &offset) {
    if (is_offset_in_range(adr.offset(), 12)) {
      assert(temp != noreg, "temp must not be empty register!");
      addi(temp, adr.base(), adr.offset());
      offset = 0;
    } else {
      baseOffset32(temp, adr, offset);
    }
  }

  void li(Register Rd, int64_t imm);  // optimized load immediate
  void li32(Register Rd, int32_t imm);
  void li64(Register Rd, int64_t imm);
  void movptr(Register Rd, address addr);
  void movptr_with_offset(Register Rd, address addr, int32_t &offset);
  void movptr(Register Rd, uintptr_t imm64);
  void ifence();
  void j(const address &dest, Register temp = t0);
  void j(const Address &adr, Register temp = t0) ;
  void j(Label &l, Register temp = t0);
  void jal(Label &l, Register temp = t0);
  void jal(const address &dest, Register temp = t0);
  void jal(const Address &adr, Register temp = t0);
  void jr(Register Rs);
  void jalr(Register Rs);
  void ret();
  void call(const address &dest, Register temp = t0);
  void call(const Address &adr, Register temp = t0);
  void tail(const address &dest, Register temp = t0);
  void tail(const Address &adr, Register temp = t0);
  void call(Label &l, Register temp) {
    call(target(l), temp);
  }
  void tail(Label &l, Register temp) {
    tail(target(l), temp);
  }

  static inline uint32_t extract(uint32_t val, unsigned msb, unsigned lsb) {
    assert_cond(msb >= lsb && msb <= 31);
    unsigned nbits = msb - lsb + 1;
    uint32_t mask = (1U << nbits) - 1;
    uint32_t result = val >> lsb;
    result &= mask;
    return result;
  }

  static inline int32_t sextract(uint32_t val, unsigned msb, unsigned lsb) {
    assert_cond(msb >= lsb && msb <= 31);
    int32_t result = val << (31 - msb);
    result >>= (31 - msb + lsb);
    return result;
  }

  static void patch(address a, unsigned msb, unsigned lsb, unsigned val) {
    assert_cond(a != NULL);
    assert_cond(msb >= lsb && msb <= 31);
    unsigned nbits = msb - lsb + 1;
    guarantee(val < (1U << nbits), "Field too big for insn");
    unsigned mask = (1U << nbits) - 1;
    val <<= lsb;
    mask <<= lsb;
    unsigned target = *(unsigned *)a;
    target &= ~mask;
    target |= val;
    *(unsigned *)a = target;
  }

  static void patch(address a, unsigned bit, unsigned val) {
    patch(a, bit, bit, val);
  }

  static void patch_reg(address a, unsigned lsb, Register reg) {
    patch(a, lsb + 4, lsb, reg->encoding_nocheck());
  }

  static void patch_reg(address a, unsigned lsb, FloatRegister reg) {
    patch(a, lsb + 4, lsb, reg->encoding_nocheck());
  }

  static void patch_reg(address a, unsigned lsb, VectorRegister reg) {
    patch(a, lsb + 4, lsb, reg->encoding_nocheck());
  }

  void emit(unsigned insn) {
    emit_int32((jint)insn);
  }

  void halt() {
    emit_int32(0);
  }

// Rigster Instruction
#define INSN(NAME, op, funct3, funct7)                          \
  void NAME(Register Rd, Register Rs1, Register Rs2) {          \
    unsigned insn = 0;                                          \
    patch((address)&insn, 6,  0, op);                           \
    patch((address)&insn, 14, 12, funct3);                      \
    patch((address)&insn, 31, 25, funct7);                      \
    patch_reg((address)&insn, 7, Rd);                           \
    patch_reg((address)&insn, 15, Rs1);                         \
    patch_reg((address)&insn, 20, Rs2);                         \
    emit(insn);                                                 \
  }

  INSN(add,   0b0110011, 0b000, 0b0000000);
  INSN(sub,   0b0110011, 0b000, 0b0100000);
  INSN(andr,  0b0110011, 0b111, 0b0000000);
  INSN(orr,   0b0110011, 0b110, 0b0000000);
  INSN(xorr,  0b0110011, 0b100, 0b0000000);
  INSN(sll,   0b0110011, 0b001, 0b0000000);
  INSN(sra,   0b0110011, 0b101, 0b0100000);
  INSN(srl,   0b0110011, 0b101, 0b0000000);
  INSN(slt,   0b0110011, 0b010, 0b0000000);
  INSN(sltu,  0b0110011, 0b011, 0b0000000);
  INSN(addw,  0b0111011, 0b000, 0b0000000);
  INSN(subw,  0b0111011, 0b000, 0b0100000);
  INSN(sllw,  0b0111011, 0b001, 0b0000000);
  INSN(sraw,  0b0111011, 0b101, 0b0100000);
  INSN(srlw,  0b0111011, 0b101, 0b0000000);
  INSN(mul,   0b0110011, 0b000, 0b0000001);
  INSN(mulh,  0b0110011, 0b001, 0b0000001);
  INSN(mulhsu,0b0110011, 0b010, 0b0000001);
  INSN(mulhu, 0b0110011, 0b011, 0b0000001);
  INSN(mulw,  0b0111011, 0b000, 0b0000001);
  INSN(div,   0b0110011, 0b100, 0b0000001);
  INSN(divu,  0b0110011, 0b101, 0b0000001);
  INSN(divw,  0b0111011, 0b100, 0b0000001);
  INSN(divuw, 0b0111011, 0b101, 0b0000001);
  INSN(rem,   0b0110011, 0b110, 0b0000001);
  INSN(remu,  0b0110011, 0b111, 0b0000001);
  INSN(remw,  0b0111011, 0b110, 0b0000001);
  INSN(remuw, 0b0111011, 0b111, 0b0000001);

  // Vector Configuration Instruction
  INSN(vsetvl, 0b1010111, 0b111, 0b1000000);

#undef INSN

#define INSN_ENTRY_RELOC(result_type, header)                               \
  result_type header {                                                      \
    InstructionMark im(this);                                               \
    guarantee(rtype == relocInfo::internal_word_type,                       \
              "only internal_word_type relocs make sense here");            \
    code_section()->relocate(inst_mark(), InternalAddress(dest).rspec());

  // Load/store register (all modes)
#define INSN(NAME, op, funct3)                                                                     \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                      \
    unsigned insn = 0;                                                                             \
    guarantee(is_offset_in_range(offset, 12), "offset is invalid.");                               \
    int32_t val = offset & 0xfff;                                                                  \
    patch((address)&insn, 6, 0, op);                                                               \
    patch((address)&insn, 14, 12, funct3);                                                         \
    patch_reg((address)&insn, 15, Rs);                                                             \
    patch_reg((address)&insn, 7, Rd);                                                              \
    patch((address)&insn, 31, 20, val);                                                            \
    emit(insn);                                                                                    \
  }                                                                                                \
  void NAME(Register Rd, address dest) {                                                           \
    assert_cond(dest != NULL);                                                                     \
    int64_t distance = (dest - pc());                                                              \
    if (is_offset_in_range(distance, 32)) {                                                        \
      auipc(Rd, (int32_t)distance + 0x800);                                                        \
      NAME(Rd, Rd, ((int32_t)distance << 20) >> 20);                                               \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr_with_offset(Rd, dest, offset);                                                        \
      NAME(Rd, Rd, offset);                                                                        \
    }                                                                                              \
  }                                                                                                \
  INSN_ENTRY_RELOC(void, NAME(Register Rd, address dest, relocInfo::relocType rtype))              \
    NAME(Rd, dest);                                                                                \
  }                                                                                                \
  void NAME(Register Rd, const Address &adr, Register temp = t0) {                                 \
    switch(adr.getMode()) {                                                                        \
      case Address::literal: {                                                                     \
        code_section()->relocate(pc(), adr.rspec());                                               \
        NAME(Rd, adr.target());                                                                    \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset:{                                                             \
        if (is_offset_in_range(adr.offset(), 12)) {                                                \
          NAME(Rd, adr.base(), adr.offset());                                                      \
        } else {                                                                                   \
          int32_t offset = 0;                                                                      \
          if (Rd == adr.base()) {                                                                  \
            baseOffset32(temp, adr, offset);                                                       \
            NAME(Rd, temp, offset);                                                                \
          } else {                                                                                 \
            baseOffset32(Rd, adr, offset);                                                         \
            NAME(Rd, Rd, offset);                                                                  \
          }                                                                                        \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }                                                                                                \
  void NAME(Register Rd, Label &L) {                                                               \
    wrap_label(Rd, L, &Assembler::NAME);                                                           \
  }

  INSN(lb,  0b0000011, 0b000);
  INSN(lbu, 0b0000011, 0b100);
  INSN(ld,  0b0000011, 0b011);
  INSN(lh,  0b0000011, 0b001);
  INSN(lhu, 0b0000011, 0b101);
  INSN(lw,  0b0000011, 0b010);
  INSN(lwu, 0b0000011, 0b110);

#undef INSN

#define INSN(NAME, op, funct3)                                                                     \
  void NAME(FloatRegister Rd, Register Rs, const int32_t offset) {                                 \
    unsigned insn = 0;                                                                             \
    guarantee(is_offset_in_range(offset, 12), "offset is invalid.");                               \
    uint32_t val = offset & 0xfff;                                                                 \
    patch((address)&insn, 6, 0, op);                                                               \
    patch((address)&insn, 14, 12, funct3);                                                         \
    patch_reg((address)&insn, 15, Rs);                                                             \
    patch_reg((address)&insn, 7, Rd);                                                              \
    patch((address)&insn, 31, 20, val);                                                            \
    emit(insn);                                                                                    \
  }                                                                                                \
  void NAME(FloatRegister Rd, address dest, Register temp = t0) {                                  \
    assert_cond(dest != NULL);                                                                     \
    int64_t distance = (dest - pc());                                                              \
    if (is_offset_in_range(distance, 32)) {                                                        \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      NAME(Rd, temp, ((int32_t)distance << 20) >> 20);                                             \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr_with_offset(temp, dest, offset);                                                      \
      NAME(Rd, temp, offset);                                                                      \
    }                                                                                              \
  }                                                                                                \
  INSN_ENTRY_RELOC(void, NAME(FloatRegister Rd, address dest, relocInfo::relocType rtype, Register temp = t0)) \
    NAME(Rd, dest, temp);                                                                          \
  }                                                                                                \
  void NAME(FloatRegister Rd, const Address &adr, Register temp = t0) {                            \
    switch(adr.getMode()) {                                                                        \
      case Address::literal: {                                                                     \
        code_section()->relocate(pc(), adr.rspec());                                               \
        NAME(Rd, adr.target(), temp);                                                              \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset:{                                                             \
        if (is_offset_in_range(adr.offset(), 12)) {                                                \
          NAME(Rd, adr.base(), adr.offset());                                                      \
        } else {                                                                                   \
          int32_t offset = 0;                                                                      \
          baseOffset32(temp, adr, offset);                                                         \
          NAME(Rd, temp, offset);                                                                  \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(flw, 0b0000111, 0b010);
  INSN(fld, 0b0000111, 0b011);
#undef INSN

#define INSN(NAME, op, funct3)                                                                           \
  void NAME(Register Rs1, Register Rs2, const int64_t offset) {                                          \
    unsigned insn = 0;                                                                                   \
    guarantee(is_imm_in_range(offset, 12, 1), "offset is invalid.");                                     \
    uint32_t val  = offset & 0x1fff;                                                                     \
    uint32_t val11 = (val >> 11) & 0x1;                                                                  \
    uint32_t val12 = (val >> 12) & 0x1;                                                                  \
    uint32_t low  = (val >> 1) & 0xf;                                                                    \
    uint32_t high = (val >> 5) & 0x3f;                                                                   \
    patch((address)&insn, 6, 0, op);                                                                     \
    patch((address)&insn, 14, 12, funct3);                                                               \
    patch_reg((address)&insn, 15, Rs1);                                                                  \
    patch_reg((address)&insn, 20, Rs2);                                                                  \
    patch((address)&insn, 7, val11);                                                                     \
    patch((address)&insn, 11, 8, low);                                                                   \
    patch((address)&insn, 30, 25, high);                                                                 \
    patch((address)&insn, 31, val12);                                                                    \
    emit(insn);                                                                                          \
  }                                                                                                      \
  void NAME(Register Rs1, Register Rs2, const address dest) {                                            \
    assert_cond(dest != NULL);                                                                           \
    int64_t offset = (dest - pc());                                                                      \
    guarantee(is_imm_in_range(offset, 12, 1), "offset is invalid.");                                     \
    NAME(Rs1, Rs2, offset);                                                                              \
  }                                                                                                      \
  INSN_ENTRY_RELOC(void, NAME(Register Rs1, Register Rs2, address dest, relocInfo::relocType rtype))     \
    NAME(Rs1, Rs2, dest);                                                                                \
  }

  INSN(beq,  0b1100011, 0b000);
  INSN(bge,  0b1100011, 0b101);
  INSN(bgeu, 0b1100011, 0b111);
  INSN(blt,  0b1100011, 0b100);
  INSN(bltu, 0b1100011, 0b110);
  INSN(bne,  0b1100011, 0b001);

#undef INSN

#define INSN(NAME, NEG_INSN)                                                                \
  void NAME(Register Rs1, Register Rs2, Label &L, bool is_far = false) {                    \
    wrap_label(Rs1, Rs2, L, &Assembler::NAME, &Assembler::NEG_INSN, is_far);                \
  }

  INSN(beq,  bne);
  INSN(bne,  beq);
  INSN(blt,  bge);
  INSN(bge,  blt);
  INSN(bltu, bgeu);
  INSN(bgeu, bltu);

#undef INSN

#define INSN(NAME, REGISTER, op, funct3)                                                                    \
  void NAME(REGISTER Rs1, Register Rs2, const int32_t offset) {                                             \
    unsigned insn = 0;                                                                                      \
    guarantee(is_offset_in_range(offset, 12), "offset is invalid.");                                        \
    uint32_t val  = offset & 0xfff;                                                                         \
    uint32_t low  = val & 0x1f;                                                                             \
    uint32_t high = (val >> 5) & 0x7f;                                                                      \
    patch((address)&insn, 6, 0, op);                                                                        \
    patch((address)&insn, 14, 12, funct3);                                                                  \
    patch_reg((address)&insn, 15, Rs2);                                                                     \
    patch_reg((address)&insn, 20, Rs1);                                                                     \
    patch((address)&insn, 11, 7, low);                                                                      \
    patch((address)&insn, 31, 25, high);                                                                    \
    emit(insn);                                                                                             \
  }                                                                                                         \
  INSN_ENTRY_RELOC(void, NAME(REGISTER Rs, address dest, relocInfo::relocType rtype, Register temp = t0))   \
    NAME(Rs, dest, temp);                                                                                   \
  }

  INSN(sb,  Register,      0b0100011, 0b000);
  INSN(sh,  Register,      0b0100011, 0b001);
  INSN(sw,  Register,      0b0100011, 0b010);
  INSN(sd,  Register,      0b0100011, 0b011);
  INSN(fsw, FloatRegister, 0b0100111, 0b010);
  INSN(fsd, FloatRegister, 0b0100111, 0b011);

#undef INSN

#define INSN(NAME)                                                                                 \
  void NAME(Register Rs, address dest, Register temp = t0) {                                       \
    assert_cond(dest != NULL);                                                                     \
    assert_different_registers(Rs, temp);                                                          \
    int64_t distance = (dest - pc());                                                              \
    if (is_offset_in_range(distance, 32)) {                                                        \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      NAME(Rs, temp, ((int32_t)distance << 20) >> 20);                                             \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr_with_offset(temp, dest, offset);                                                      \
      NAME(Rs, temp, offset);                                                                      \
    }                                                                                              \
  }                                                                                                \
  void NAME(Register Rs, const Address &adr, Register temp = t0) {                                 \
    switch(adr.getMode()) {                                                                        \
      case Address::literal: {                                                                     \
        assert_different_registers(Rs, temp);                                                      \
        code_section()->relocate(pc(), adr.rspec());                                               \
        NAME(Rs, adr.target(), temp);                                                              \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset:{                                                             \
        if (is_offset_in_range(adr.offset(), 12)) {                                                \
          NAME(Rs, adr.base(), adr.offset());                                                      \
        } else {                                                                                   \
          int32_t offset= 0;                                                                       \
          assert_different_registers(Rs, temp);                                                    \
          baseOffset32(temp, adr, offset);                                                         \
          NAME(Rs, temp, offset);                                                                  \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(sb);
  INSN(sh);
  INSN(sw);
  INSN(sd);

#undef INSN

#define INSN(NAME)                                                                                 \
  void NAME(FloatRegister Rs, address dest, Register temp = t0) {                                  \
    assert_cond(dest != NULL);                                                                     \
    int64_t distance = (dest - pc());                                                              \
    if (is_offset_in_range(distance, 32)) {                                                        \
      auipc(temp, (int32_t)distance + 0x800);                                                      \
      NAME(Rs, temp, ((int32_t)distance << 20) >> 20);                                             \
    } else {                                                                                       \
      int32_t offset = 0;                                                                          \
      movptr_with_offset(temp, dest, offset);                                                      \
      NAME(Rs, temp, offset);                                                                      \
    }                                                                                              \
  }                                                                                                \
  void NAME(FloatRegister Rs, const Address &adr, Register temp = t0) {                            \
    switch(adr.getMode()) {                                                                        \
      case Address::literal: {                                                                     \
        code_section()->relocate(pc(), adr.rspec());                                               \
        NAME(Rs, adr.target(), temp);                                                              \
        break;                                                                                     \
      }                                                                                            \
      case Address::base_plus_offset:{                                                             \
        if (is_offset_in_range(adr.offset(), 12)) {                                                \
          NAME(Rs, adr.base(), adr.offset());                                                      \
        } else {                                                                                   \
          int32_t offset = 0;                                                                      \
          baseOffset32(temp, adr, offset);                                                         \
          NAME(Rs, temp, offset);                                                                  \
        }                                                                                          \
        break;                                                                                     \
      }                                                                                            \
      default:                                                                                     \
        ShouldNotReachHere();                                                                      \
    }                                                                                              \
  }

  INSN(fsw);
  INSN(fsd);

#undef INSN

#define INSN(NAME, op, funct3)                                                        \
  void NAME(Register Rd, const uint32_t csr, Register Rs1) {                          \
    guarantee(is_unsigned_imm_in_range(csr, 12, 0), "csr is invalid");                \
    unsigned insn = 0;                                                                \
    patch((address)&insn, 6, 0, op);                                                  \
    patch((address)&insn, 14, 12, funct3);                                            \
    patch_reg((address)&insn, 7, Rd);                                                 \
    patch_reg((address)&insn, 15, Rs1);                                               \
    patch((address)&insn, 31, 20, csr);                                               \
    emit(insn);                                                                       \
  }

  INSN(csrrw, 0b1110011, 0b001);
  INSN(csrrs, 0b1110011, 0b010);
  INSN(csrrc, 0b1110011, 0b011);

#undef INSN

#define INSN(NAME, op, funct3)                                                        \
  void NAME(Register Rd, const uint32_t csr, const uint32_t uimm) {                   \
    guarantee(is_unsigned_imm_in_range(csr, 12, 0), "csr is invalid");                \
    guarantee(is_unsigned_imm_in_range(uimm, 5, 0), "uimm is invalid");               \
    unsigned insn = 0;                                                                \
    uint32_t val  = uimm & 0x1f;                                                      \
    patch((address)&insn, 6, 0, op);                                                  \
    patch((address)&insn, 14, 12, funct3);                                            \
    patch_reg((address)&insn, 7, Rd);                                                 \
    patch((address)&insn, 19, 15, val);                                               \
    patch((address)&insn, 31, 20, csr);                                               \
    emit(insn);                                                                       \
  }

  INSN(csrrwi, 0b1110011, 0b101);
  INSN(csrrsi, 0b1110011, 0b110);
  INSN(csrrci, 0b1110011, 0b111);

#undef INSN

#define INSN(NAME, op)                                                                        \
  void NAME(Register Rd, const int32_t offset) {                                              \
    unsigned insn = 0;                                                                        \
    guarantee(is_imm_in_range(offset, 20, 1), "offset is invalid.");                          \
    patch((address)&insn, 6, 0, op);                                                          \
    patch_reg((address)&insn, 7, Rd);                                                         \
    patch((address)&insn, 19, 12, (uint32_t)((offset >> 12) & 0xff));                         \
    patch((address)&insn, 20, (uint32_t)((offset >> 11) & 0x1));                              \
    patch((address)&insn, 30, 21, (uint32_t)((offset >> 1) & 0x3ff));                         \
    patch((address)&insn, 31, (uint32_t)((offset >> 20) & 0x1));                              \
    emit(insn);                                                                               \
  }                                                                                           \
  void NAME(Register Rd, const address dest, Register temp = t0) {                            \
    assert_cond(dest != NULL);                                                                \
    int64_t offset = dest - pc();                                                             \
    if (is_imm_in_range(offset, 20, 1)) {                                                     \
      NAME(Rd, offset);                                                                       \
    } else {                                                                                  \
      assert_different_registers(Rd, temp);                                                   \
      int32_t off = 0;                                                                        \
      movptr_with_offset(temp, dest, off);                                                    \
      jalr(Rd, temp, off);                                                                    \
    }                                                                                         \
  }                                                                                           \
  void NAME(Register Rd, Label &L, Register temp = t0) {                                      \
    assert_different_registers(Rd, temp);                                                     \
    wrap_label(Rd, L, temp, &Assembler::NAME);                                                \
  }

  INSN(jal, 0b1101111);

#undef INSN

#undef INSN_ENTRY_RELOC

#define INSN(NAME, op, funct)                                                              \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                              \
    unsigned insn = 0;                                                                     \
    guarantee(is_offset_in_range(offset, 12), "offset is invalid.");                       \
    patch((address)&insn, 6, 0, op);                                                       \
    patch_reg((address)&insn, 7, Rd);                                                      \
    patch((address)&insn, 14, 12, funct);                                                  \
    patch_reg((address)&insn, 15, Rs);                                                     \
    int32_t val = offset & 0xfff;                                                          \
    patch((address)&insn, 31, 20, val);                                                    \
    emit(insn);                                                                            \
  }

  INSN(jalr, 0b1100111, 0b000);

#undef INSN

  enum barrier {
    i = 0b1000, o = 0b0100, r = 0b0010, w = 0b0001,
    ir = i | r, ow = o | w, iorw = i | o | r | w
  };

  void fence(const uint32_t predecessor, const uint32_t successor) {
    unsigned insn = 0;
    guarantee(predecessor < 16, "predecessor is invalid");
    guarantee(successor < 16, "successor is invalid");
    patch((address)&insn, 6, 0, 0b001111);
    patch((address)&insn, 11, 7, 0b00000);
    patch((address)&insn, 14, 12, 0b000);
    patch((address)&insn, 19, 15, 0b00000);
    patch((address)&insn, 23, 20, successor);
    patch((address)&insn, 27, 24, predecessor);
    patch((address)&insn, 31, 28, 0b0000);
    emit(insn);
  }

#define INSN(NAME, op, funct3, funct7)                      \
  void NAME() {                                             \
    unsigned insn = 0;                                      \
    patch((address)&insn, 6, 0, op);                        \
    patch((address)&insn, 11, 7, 0b00000);                  \
    patch((address)&insn, 14, 12, funct3);                  \
    patch((address)&insn, 19, 15, 0b00000);                 \
    patch((address)&insn, 31, 20, funct7);                  \
    emit(insn);                                             \
  }

  INSN(fence_i, 0b0001111, 0b001, 0b000000000000);
  INSN(ecall,   0b1110011, 0b000, 0b000000000000);
  INSN(ebreak,  0b1110011, 0b000, 0b000000000001);
#undef INSN

enum Aqrl {relaxed = 0b00, rl = 0b01, aq = 0b10, aqrl = 0b11};

#define INSN(NAME, op, funct3, funct7)                                                  \
  void NAME(Register Rd, Register Rs1, Register Rs2, Aqrl memory_order = aqrl) {        \
    unsigned insn = 0;                                                                  \
    patch((address)&insn, 6, 0, op);                                                    \
    patch((address)&insn, 14, 12, funct3);                                              \
    patch_reg((address)&insn, 7, Rd);                                                   \
    patch_reg((address)&insn, 15, Rs1);                                                 \
    patch_reg((address)&insn, 20, Rs2);                                                 \
    patch((address)&insn, 31, 27, funct7);                                              \
    patch((address)&insn, 26, 25, memory_order);                                        \
    emit(insn);                                                                         \
  }

  INSN(amoswap_w, 0b0101111, 0b010, 0b00001);
  INSN(amoadd_w,  0b0101111, 0b010, 0b00000);
  INSN(amoxor_w,  0b0101111, 0b010, 0b00100);
  INSN(amoand_w,  0b0101111, 0b010, 0b01100);
  INSN(amoor_w,   0b0101111, 0b010, 0b01000);
  INSN(amomin_w,  0b0101111, 0b010, 0b10000);
  INSN(amomax_w,  0b0101111, 0b010, 0b10100);
  INSN(amominu_w, 0b0101111, 0b010, 0b11000);
  INSN(amomaxu_w, 0b0101111, 0b010, 0b11100);
  INSN(amoswap_d, 0b0101111, 0b011, 0b00001);
  INSN(amoadd_d,  0b0101111, 0b011, 0b00000);
  INSN(amoxor_d,  0b0101111, 0b011, 0b00100);
  INSN(amoand_d,  0b0101111, 0b011, 0b01100);
  INSN(amoor_d,   0b0101111, 0b011, 0b01000);
  INSN(amomin_d,  0b0101111, 0b011, 0b10000);
  INSN(amomax_d , 0b0101111, 0b011, 0b10100);
  INSN(amominu_d, 0b0101111, 0b011, 0b11000);
  INSN(amomaxu_d, 0b0101111, 0b011, 0b11100);
#undef INSN

enum operand_size { int8, int16, int32, uint32, int64 };

#define INSN(NAME, op, funct3, funct7)                                              \
  void NAME(Register Rd, Register Rs1, Aqrl memory_order = relaxed) {               \
    unsigned insn = 0;                                                              \
    uint32_t val = memory_order & 0x3;                                              \
    patch((address)&insn, 6, 0, op);                                                \
    patch((address)&insn, 14, 12, funct3);                                          \
    patch_reg((address)&insn, 7, Rd);                                               \
    patch_reg((address)&insn, 15, Rs1);                                             \
    patch((address)&insn, 25, 20, 0b00000);                                         \
    patch((address)&insn, 31, 27, funct7);                                          \
    patch((address)&insn, 26, 25, val);                                             \
    emit(insn);                                                                     \
  }

  INSN(lr_w, 0b0101111, 0b010, 0b00010);
  INSN(lr_d, 0b0101111, 0b011, 0b00010);

#undef INSN

#define INSN(NAME, op, funct3, funct7)                                                      \
  void NAME(Register Rd, Register Rs1, Register Rs2, Aqrl memory_order = relaxed) {         \
    unsigned insn = 0;                                                                      \
    uint32_t val = memory_order & 0x3;                                                      \
    patch((address)&insn, 6, 0, op);                                                        \
    patch((address)&insn, 14, 12, funct3);                                                  \
    patch_reg((address)&insn, 7, Rd);                                                       \
    patch_reg((address)&insn, 15, Rs2);                                                     \
    patch_reg((address)&insn, 20, Rs1);                                                     \
    patch((address)&insn, 31, 27, funct7);                                                  \
    patch((address)&insn, 26, 25, val);                                                     \
    emit(insn);                                                                             \
  }

  INSN(sc_w, 0b0101111, 0b010, 0b00011);
  INSN(sc_d, 0b0101111, 0b011, 0b00011);
#undef INSN

#define INSN(NAME, op, funct5, funct7)                                                      \
  void NAME(FloatRegister Rd, FloatRegister Rs1, RoundingMode rm = rne) {                   \
    unsigned insn = 0;                                                                      \
    patch((address)&insn, 6, 0, op);                                                        \
    patch((address)&insn, 14, 12, rm);                                                      \
    patch((address)&insn, 24, 20, funct5);                                                  \
    patch((address)&insn, 31, 25, funct7);                                                  \
    patch_reg((address)&insn, 7, Rd);                                                       \
    patch_reg((address)&insn, 15, Rs1);                                                     \
    emit(insn);                                                                             \
  }

  INSN(fsqrt_s,   0b1010011, 0b00000, 0b0101100);
  INSN(fsqrt_d,   0b1010011, 0b00000, 0b0101101);
  INSN(fcvt_s_d,  0b1010011, 0b00001, 0b0100000);
  INSN(fcvt_d_s,  0b1010011, 0b00000, 0b0100001);
#undef INSN

// Immediate Instruction
#define INSN(NAME, op, funct3)                                                              \
  void NAME(Register Rd, Register Rs1, int32_t imm) {                                       \
    guarantee(is_imm_in_range(imm, 12, 0), "Immediate is out of validity");                 \
    unsigned insn = 0;                                                                      \
    patch((address)&insn, 6, 0, op);                                                        \
    patch((address)&insn, 14, 12, funct3);                                                  \
    patch((address)&insn, 31, 20, imm & 0x00000fff);                                        \
    patch_reg((address)&insn, 7, Rd);                                                       \
    patch_reg((address)&insn, 15, Rs1);                                                     \
    emit(insn);                                                                             \
  }

  INSN(addi,  0b0010011, 0b000);
  INSN(slti,  0b0010011, 0b010);
  INSN(addiw, 0b0011011, 0b000);
  INSN(and_imm12,  0b0010011, 0b111);
  INSN(ori,   0b0010011, 0b110);
  INSN(xori,  0b0010011, 0b100);

#undef INSN

#define INSN(NAME, op, funct3)                                                              \
  void NAME(Register Rd, Register Rs1, uint32_t imm) {                                      \
    guarantee(is_unsigned_imm_in_range(imm, 12, 0), "Immediate is out of validity");        \
    unsigned insn = 0;                                                                      \
    patch((address)&insn,6, 0,  op);                                                        \
    patch((address)&insn, 14, 12, funct3);                                                  \
    patch((address)&insn, 31, 20, imm & 0x00000fff);                                        \
    patch_reg((address)&insn, 7, Rd);                                                       \
    patch_reg((address)&insn, 15, Rs1);                                                     \
    emit(insn);                                                                             \
  }

  INSN(sltiu, 0b0010011, 0b011);

#undef INSN

// Shift Immediate Instruction
#define INSN(NAME, op, funct3, funct6)                                   \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {                 \
    guarantee(shamt <= 0x3f, "Shamt is invalid");                        \
    unsigned insn = 0;                                                   \
    patch((address)&insn, 6, 0, op);                                     \
    patch((address)&insn, 14, 12, funct3);                               \
    patch((address)&insn, 25, 20, shamt);                                \
    patch((address)&insn, 31, 26, funct6);                               \
    patch_reg((address)&insn, 7, Rd);                                    \
    patch_reg((address)&insn, 15, Rs1);                                  \
    emit(insn);                                                          \
  }

  INSN(slli,  0b0010011, 0b001, 0b000000);
  INSN(srai,  0b0010011, 0b101, 0b010000);
  INSN(srli,  0b0010011, 0b101, 0b000000);

#undef INSN

// Shift Word Immediate Instruction
#define INSN(NAME, op, funct3, funct7)                                  \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {                \
    guarantee(shamt <= 0x1f, "Shamt is invalid");                       \
    unsigned insn = 0;                                                  \
    patch((address)&insn, 6, 0, op);                                    \
    patch((address)&insn, 14, 12, funct3);                              \
    patch((address)&insn, 24, 20, shamt);                               \
    patch((address)&insn, 31, 25, funct7);                              \
    patch_reg((address)&insn, 7, Rd);                                   \
    patch_reg((address)&insn, 15, Rs1);                                 \
    emit(insn);                                                         \
  }

  INSN(slliw, 0b0011011, 0b001, 0b0000000);
  INSN(sraiw, 0b0011011, 0b101, 0b0100000);
  INSN(srliw, 0b0011011, 0b101, 0b0000000);

#undef INSN

// Upper Immediate Instruction
#define INSN(NAME, op)                                                  \
  void NAME(Register Rd, int32_t imm) {                                 \
    int32_t upperImm = imm >> 12;                                       \
    unsigned insn = 0;                                                  \
    patch((address)&insn, 6, 0, op);                                    \
    patch_reg((address)&insn, 7, Rd);                                   \
    upperImm &= 0x000fffff;                                             \
    patch((address)&insn, 31, 12, upperImm);                            \
    emit(insn);                                                         \
  }

  INSN(lui,   0b0110111);
  INSN(auipc, 0b0010111);

#undef INSN

// Float and Double Rigster Instruction
#define INSN(NAME, op, funct2)                                                                                     \
  void NAME(FloatRegister Rd, FloatRegister Rs1, FloatRegister Rs2, FloatRegister Rs3, RoundingMode rm = rne) {    \
    unsigned insn = 0;                                                                                             \
    patch((address)&insn, 6, 0, op);                                                                               \
    patch((address)&insn, 14, 12, rm);                                                                             \
    patch((address)&insn, 26, 25, funct2);                                                                         \
    patch_reg((address)&insn, 7, Rd);                                                                              \
    patch_reg((address)&insn, 15, Rs1);                                                                            \
    patch_reg((address)&insn, 20, Rs2);                                                                            \
    patch_reg((address)&insn, 27, Rs3);                                                                            \
    emit(insn);                                                                                                    \
  }

  INSN(fmadd_s,   0b1000011,  0b00);
  INSN(fmsub_s,   0b1000111,  0b00);
  INSN(fnmsub_s,  0b1001011,  0b00);
  INSN(fnmadd_s,  0b1001111,  0b00);
  INSN(fmadd_d,   0b1000011,  0b01);
  INSN(fmsub_d,   0b1000111,  0b01);
  INSN(fnmsub_d,  0b1001011,  0b01);
  INSN(fnmadd_d,  0b1001111,  0b01);

#undef INSN

// Float and Double Rigster Instruction
#define INSN(NAME, op, funct3, funct7)                                        \
  void NAME(FloatRegister Rd, FloatRegister Rs1, FloatRegister Rs2) {         \
    unsigned insn = 0;                                                        \
    patch((address)&insn, 6, 0, op);                                          \
    patch((address)&insn, 14, 12, funct3);                                    \
    patch((address)&insn, 31, 25, funct7);                                    \
    patch_reg((address)&insn, 7, Rd);                                         \
    patch_reg((address)&insn, 15, Rs1);                                       \
    patch_reg((address)&insn, 20, Rs2);                                       \
    emit(insn);                                                               \
  }

  INSN(fsgnj_s,  0b1010011, 0b000, 0b0010000);
  INSN(fsgnjn_s, 0b1010011, 0b001, 0b0010000);
  INSN(fsgnjx_s, 0b1010011, 0b010, 0b0010000);
  INSN(fmin_s,   0b1010011, 0b000, 0b0010100);
  INSN(fmax_s,   0b1010011, 0b001, 0b0010100);
  INSN(fsgnj_d,  0b1010011, 0b000, 0b0010001);
  INSN(fsgnjn_d, 0b1010011, 0b001, 0b0010001);
  INSN(fsgnjx_d, 0b1010011, 0b010, 0b0010001);
  INSN(fmin_d,   0b1010011, 0b000, 0b0010101);
  INSN(fmax_d,   0b1010011, 0b001, 0b0010101);

#undef INSN

// Float and Double Rigster Arith Instruction
#define INSN(NAME, op, funct3, funct7)                                    \
  void NAME(Register Rd, FloatRegister Rs1, FloatRegister Rs2) {          \
    unsigned insn = 0;                                                    \
    patch((address)&insn, 6, 0, op);                                      \
    patch((address)&insn, 14, 12, funct3);                                \
    patch((address)&insn, 31, 25, funct7);                                \
    patch_reg((address)&insn, 7, Rd);                                     \
    patch_reg((address)&insn, 15, Rs1);                                   \
    patch_reg((address)&insn, 20, Rs2);                                   \
    emit(insn);                                                           \
  }

  INSN(feq_s,    0b1010011, 0b010, 0b1010000);
  INSN(flt_s,    0b1010011, 0b001, 0b1010000);
  INSN(fle_s,    0b1010011, 0b000, 0b1010000);
  INSN(feq_d,    0b1010011, 0b010, 0b1010001);
  INSN(fle_d,    0b1010011, 0b000, 0b1010001);
  INSN(flt_d,    0b1010011, 0b001, 0b1010001);
#undef INSN

// Float and Double Arith Instruction
#define INSN(NAME, op, funct7)                                                                  \
  void NAME(FloatRegister Rd, FloatRegister Rs1, FloatRegister Rs2, RoundingMode rm = rne) {    \
    unsigned insn = 0;                                                                          \
    patch((address)&insn, 6, 0, op);                                                            \
    patch((address)&insn, 14, 12, rm);                                                          \
    patch((address)&insn, 31, 25, funct7);                                                      \
    patch_reg((address)&insn, 7, Rd);                                                           \
    patch_reg((address)&insn, 15, Rs1);                                                         \
    patch_reg((address)&insn, 20, Rs2);                                                         \
    emit(insn);                                                                                 \
  }

  INSN(fadd_s,   0b1010011, 0b0000000);
  INSN(fsub_s,   0b1010011, 0b0000100);
  INSN(fmul_s,   0b1010011, 0b0001000);
  INSN(fdiv_s,   0b1010011, 0b0001100);
  INSN(fadd_d,   0b1010011, 0b0000001);
  INSN(fsub_d,   0b1010011, 0b0000101);
  INSN(fmul_d,   0b1010011, 0b0001001);
  INSN(fdiv_d,   0b1010011, 0b0001101);

#undef INSN

// Whole Float and Double Conversion Instruction
#define INSN(NAME, op, funct5, funct7)                                  \
  void NAME(FloatRegister Rd, Register Rs1, RoundingMode rm = rne) {    \
    unsigned insn = 0;                                                  \
    patch((address)&insn, 6, 0, op);                                    \
    patch((address)&insn, 14, 12, rm);                                  \
    patch((address)&insn, 24, 20, funct5);                              \
    patch((address)&insn, 31, 25, funct7);                              \
    patch_reg((address)&insn, 7, Rd);                                   \
    patch_reg((address)&insn, 15, Rs1);                                 \
    emit(insn);                                                         \
  }

  INSN(fcvt_s_w,   0b1010011, 0b00000, 0b1101000);
  INSN(fcvt_s_wu,  0b1010011, 0b00001, 0b1101000);
  INSN(fcvt_s_l,   0b1010011, 0b00010, 0b1101000);
  INSN(fcvt_s_lu,  0b1010011, 0b00011, 0b1101000);
  INSN(fcvt_d_w,   0b1010011, 0b00000, 0b1101001);
  INSN(fcvt_d_wu,  0b1010011, 0b00001, 0b1101001);
  INSN(fcvt_d_l,   0b1010011, 0b00010, 0b1101001);
  INSN(fcvt_d_lu,  0b1010011, 0b00011, 0b1101001);

#undef INSN

// Float and Double Conversion Instruction
#define INSN(NAME, op, funct5, funct7)                                  \
  void NAME(Register Rd, FloatRegister Rs1, RoundingMode rm = rtz) {    \
    unsigned insn = 0;                                                  \
    patch((address)&insn, 6, 0, op);                                    \
    patch((address)&insn, 14, 12, rm);                                  \
    patch((address)&insn, 24, 20, funct5);                              \
    patch((address)&insn, 31, 25, funct7);                              \
    patch_reg((address)&insn, 7, Rd);                                   \
    patch_reg((address)&insn, 15, Rs1);                                 \
    emit(insn);                                                         \
  }

  INSN(fcvt_w_s,   0b1010011, 0b00000, 0b1100000);
  INSN(fcvt_l_s,   0b1010011, 0b00010, 0b1100000);
  INSN(fcvt_wu_s,  0b1010011, 0b00001, 0b1100000);
  INSN(fcvt_lu_s,  0b1010011, 0b00011, 0b1100000);
  INSN(fcvt_w_d,   0b1010011, 0b00000, 0b1100001);
  INSN(fcvt_wu_d,  0b1010011, 0b00001, 0b1100001);
  INSN(fcvt_l_d,   0b1010011, 0b00010, 0b1100001);
  INSN(fcvt_lu_d,  0b1010011, 0b00011, 0b1100001);

#undef INSN

// Float and Double Move Instruction
#define INSN(NAME, op, funct3, funct5, funct7)       \
  void NAME(FloatRegister Rd, Register Rs1) {        \
    unsigned insn = 0;                               \
    patch((address)&insn, 6, 0, op);                 \
    patch((address)&insn, 14, 12, funct3);           \
    patch((address)&insn, 20, funct5);               \
    patch((address)&insn, 31, 25, funct7);           \
    patch_reg((address)&insn, 7, Rd);                \
    patch_reg((address)&insn, 15, Rs1);              \
    emit(insn);                                      \
  }

  INSN(fmv_w_x,  0b1010011, 0b000, 0b00000, 0b1111000);
  INSN(fmv_d_x,  0b1010011, 0b000, 0b00000, 0b1111001);

#undef INSN

// Float and Double Conversion Instruction
#define INSN(NAME, op, funct3, funct5, funct7)            \
  void NAME(Register Rd, FloatRegister Rs1) {             \
    unsigned insn = 0;                                    \
    patch((address)&insn, 6, 0, op);                      \
    patch((address)&insn, 14, 12, funct3);                \
    patch((address)&insn, 20, funct5);                    \
    patch((address)&insn, 31, 25, funct7);                \
    patch_reg((address)&insn, 7, Rd);                     \
    patch_reg((address)&insn, 15, Rs1);                   \
    emit(insn);                                           \
  }

  INSN(fclass_s, 0b1010011, 0b001, 0b00000, 0b1110000);
  INSN(fclass_d, 0b1010011, 0b001, 0b00000, 0b1110001);
  INSN(fmv_x_w,  0b1010011, 0b000, 0b00000, 0b1110000);
  INSN(fmv_x_d,  0b1010011, 0b000, 0b00000, 0b1110001);

#undef INSN

enum SEW {
  e8    = 0b000,
  e16   = 0b001,
  e32   = 0b010,
  e64   = 0b011,
  e128  = 0b100,
  e256  = 0b101,
  e512  = 0b110,
  e1024 = 0b111,
};

enum LMUL {
  mf8 = 0b101,
  mf4 = 0b110,
  mf2 = 0b111,
  m1  = 0b000,
  m2  = 0b001,
  m4  = 0b010,
  m8  = 0b011,
};

enum VMA {
  mu, // undisturbed
  ma, // agnostic
};

enum VTA {
  tu, // undisturbed
  ta, // agnostic
};

#define patch_vtype(hsb, lsb, vlmul, vsew, vta, vma, vill)   \
    if (vill == 1) {                                         \
      guarantee((vlmul | vsew | vsew | vta | vma == 0),      \
                "the other bits in vtype shall be zero");    \
    }                                                        \
    patch((address)&insn, lsb + 2, lsb, vlmul);              \
    patch((address)&insn, lsb + 5, lsb + 3, vsew);           \
    patch((address)&insn, lsb + 6, vta);                     \
    patch((address)&insn, lsb + 7, vma);                     \
    patch((address)&insn, hsb - 1, lsb + 8, 0);              \
    patch((address)&insn, hsb, vill)

#define INSN(NAME, op, funct3)                                            \
  void NAME(Register Rd, Register Rs1, SEW sew, LMUL lmul = m1,           \
            VMA vma = mu, VTA vta = tu, bool vill = false) {              \
    unsigned insn = 0;                                                    \
    patch((address)&insn, 6, 0, op);                                      \
    patch((address)&insn, 14, 12, funct3);                                \
    patch_vtype(30, 20, lmul, sew, vta, vma, vill);                       \
    patch((address)&insn, 31, 0);                                         \
    patch_reg((address)&insn, 7, Rd);                                     \
    patch_reg((address)&insn, 15, Rs1);                                   \
    emit(insn);                                                           \
  }

  INSN(vsetvli, 0b1010111, 0b111);

#undef INSN

#define INSN(NAME, op, funct3)                                            \
  void NAME(Register Rd, uint32_t imm, SEW sew, LMUL lmul = m1,           \
            VMA vma = mu, VTA vta = tu, bool vill = false) {              \
    unsigned insn = 0;                                                    \
    guarantee(is_unsigned_imm_in_range(imm, 5, 0), "imm is invalid");     \
    patch((address)&insn, 6, 0, op);                                      \
    patch((address)&insn, 14, 12, funct3);                                \
    patch((address)&insn, 19, 15, imm);                                   \
    patch_vtype(29, 20, lmul, sew, vta, vma, vill);                       \
    patch((address)&insn, 31, 30, 0b11);                                  \
    patch_reg((address)&insn, 7, Rd);                                     \
    emit(insn);                                                           \
  }

  INSN(vsetivli, 0b1010111, 0b111);

#undef INSN

#undef patch_vtype

enum VectorMask {
  v0_t = 0b0,
  unmasked = 0b1
};

// Vector AMO operations
#define INSN(NAME, op, funct3, funct5)                                   \
  void NAME(VectorRegister vSrc, Register rBase, VectorRegister vOffset, \
            bool src_as_dst, VectorMask vm = unmasked) {                 \
    unsigned insn = 0;                                                   \
    patch((address)&insn, 6, 0, op);                                     \
    patch((address)&insn, 14, 12, funct3);                               \
    patch((address)&insn, 25, vm);                                       \
    patch((address)&insn, 26, (uint32_t)src_as_dst);                     \
    patch((address)&insn, 31, 27, funct5);                               \
    patch_reg((address)&insn, 7, vSrc);                                  \
    patch_reg((address)&insn, 15, rBase);                                \
    patch_reg((address)&insn, 20, vOffset);                              \
    emit(insn);                                                          \
  }

  INSN(vamoswapei8_v,  0b0101111, 0b000, 0b00001);
  INSN(vamoswapei16_v, 0b0101111, 0b101, 0b00001);
  INSN(vamoswapei32_v, 0b0101111, 0b110, 0b00001);
  INSN(vamoaddei8_v,   0b0101111, 0b000, 0b00000);
  INSN(vamoaddei16_v,  0b0101111, 0b101, 0b00000);
  INSN(vamoaddei32_v,  0b0101111, 0b110, 0b00000);
  INSN(vamoxorei8_v,   0b0101111, 0b000, 0b00100);
  INSN(vamoxorei16_v,  0b0101111, 0b101, 0b00100);
  INSN(vamoxorei32_v,  0b0101111, 0b110, 0b00100);
  INSN(vamoandei8_v,   0b0101111, 0b000, 0b01100);
  INSN(vamoandei16_v,  0b0101111, 0b101, 0b01100);
  INSN(vamoandei32_v,  0b0101111, 0b110, 0b01100);
  INSN(vamoorei8_v,    0b0101111, 0b000, 0b01000);
  INSN(vamoorei16_v,   0b0101111, 0b101, 0b01000);
  INSN(vamoorei32_v,   0b0101111, 0b110, 0b01000);
  INSN(vamominei8_v,   0b0101111, 0b000, 0b10000);
  INSN(vamominei16_v,  0b0101111, 0b101, 0b10000);
  INSN(vamominei32_v,  0b0101111, 0b110, 0b10000);
  INSN(vamomaxei8_v,   0b0101111, 0b000, 0b10100);
  INSN(vamomaxei16_v,  0b0101111, 0b101, 0b10100);
  INSN(vamomaxei32_v,  0b0101111, 0b110, 0b10100);
  INSN(vamominuei8_v,  0b0101111, 0b000, 0b11000);
  INSN(vamominuei16_v, 0b0101111, 0b101, 0b11000);
  INSN(vamominuei32_v, 0b0101111, 0b110, 0b11000);
  INSN(vamomaxuei8_v,  0b0101111, 0b000, 0b11100);
  INSN(vamomaxuei16_v, 0b0101111, 0b101, 0b11100);
  INSN(vamomaxuei32_v, 0b0101111, 0b110, 0b11100);

#undef INSN

#define patch_VArith(op, Reg, funct3, Reg_or_Imm5, Vs2, vm, funct6)            \
    unsigned insn = 0;                                                         \
    patch((address)&insn, 6, 0, op);                                           \
    patch((address)&insn, 14, 12, funct3);                                     \
    patch((address)&insn, 19, 15, Reg_or_Imm5);                                \
    patch((address)&insn, 25, vm);                                             \
    patch((address)&insn, 31, 26, funct6);                                     \
    patch_reg((address)&insn, 7, Reg);                                         \
    patch_reg((address)&insn, 20, Vs2);                                        \
    emit(insn)

// r2_vm
#define INSN(NAME, op, funct3, Vs1, funct6)                                    \
  void NAME(Register Rd, VectorRegister Vs2, VectorMask vm = unmasked) {       \
    patch_VArith(op, Rd, funct3, Vs1, Vs2, vm, funct6);                        \
  }

  // Vector Mask
  INSN(vpopc_m,  0b1010111, 0b010, 0b10000, 0b010000);
  INSN(vfirst_m, 0b1010111, 0b010, 0b10001, 0b010000);
#undef INSN

#define INSN(NAME, op, funct3, Vs1, funct6)                                    \
  void NAME(VectorRegister Vd, VectorRegister Vs2, VectorMask vm = unmasked) { \
    patch_VArith(op, Vd, funct3, Vs1, Vs2, vm, funct6);                        \
  }

  // Vector Integer Extension
  INSN(vzext_vf2, 0b1010111, 0b010, 0b00110, 0b010010);
  INSN(vzext_vf4, 0b1010111, 0b010, 0b00100, 0b010010);
  INSN(vzext_vf8, 0b1010111, 0b010, 0b00010, 0b010010);
  INSN(vsext_vf2, 0b1010111, 0b010, 0b00111, 0b010010);
  INSN(vsext_vf4, 0b1010111, 0b010, 0b00101, 0b010010);
  INSN(vsext_vf8, 0b1010111, 0b010, 0b00011, 0b010010);

  // Vector Mask
  INSN(vmsbf_m,   0b1010111, 0b010, 0b00001, 0b010100);
  INSN(vmsif_m,   0b1010111, 0b010, 0b00011, 0b010100);
  INSN(vmsof_m,   0b1010111, 0b010, 0b00010, 0b010100);
  INSN(viota_m,   0b1010111, 0b010, 0b10000, 0b010100);

  // Vector Single-Width Floating-Point/Integer Type-Convert Instructions
  INSN(vfcvt_xu_f_v, 0b1010111, 0b001, 0b00000, 0b010010);
  INSN(vfcvt_x_f_v,  0b1010111, 0b001, 0b00001, 0b010010);
  INSN(vfcvt_f_xu_v, 0b1010111, 0b001, 0b00010, 0b010010);
  INSN(vfcvt_f_x_v,  0b1010111, 0b001, 0b00011, 0b010010);
  INSN(vfcvt_rtz_xu_f_v, 0b1010111, 0b001, 0b00110, 0b010010);
  INSN(vfcvt_rtz_x_f_v,  0b1010111, 0b001, 0b00111, 0b010010);

  // Vector Widening Floating-Point/Integer Type-Convert Instructions
  INSN(vfwcvt_xu_f_v, 0b1010111, 0b001, 0b01000, 0b010010);
  INSN(vfwcvt_x_f_v,  0b1010111, 0b001, 0b01001, 0b010010);
  INSN(vfwcvt_f_xu_v, 0b1010111, 0b001, 0b01010, 0b010010);
  INSN(vfwcvt_f_x_v,  0b1010111, 0b001, 0b01011, 0b010010);
  INSN(vfwcvt_f_f_v,  0b1010111, 0b001, 0b01100, 0b010010);
  INSN(vfwcvt_rtz_xu_f_v, 0b1010111, 0b001, 0b01110, 0b010010);
  INSN(vfwcvt_rtz_x_f_v,  0b1010111, 0b001, 0b01111, 0b010010);

  // Vector Narrowing Floating-Point/Integer Type-Convert Instructions
  INSN(vfncvt_xu_f_w, 0b1010111, 0b001, 0b10000, 0b010010);
  INSN(vfncvt_x_f_w,  0b1010111, 0b001, 0b10001, 0b010010);
  INSN(vfncvt_f_xu_w, 0b1010111, 0b001, 0b10010, 0b010010);
  INSN(vfncvt_f_x_w,  0b1010111, 0b001, 0b10011, 0b010010);
  INSN(vfncvt_f_f_w,  0b1010111, 0b001, 0b10100, 0b010010);
  INSN(vfncvt_rod_f_f_w,  0b1010111, 0b001, 0b10101, 0b010010);
  INSN(vfncvt_rtz_xu_f_w, 0b1010111, 0b001, 0b10110, 0b010010);
  INSN(vfncvt_rtz_x_f_w,  0b1010111, 0b001, 0b10111, 0b010010);

  // Vector Floating-Point Instruction
  INSN(vfsqrt_v,  0b1010111, 0b001, 0b00000, 0b010011);
  INSN(vfclass_v, 0b1010111, 0b001, 0b10000, 0b010011);

#undef INSN

// r2rd
#define INSN(NAME, op, funct3, simm5, vm, funct6)         \
  void NAME(VectorRegister Vd, VectorRegister Vs2) {      \
    patch_VArith(op, Vd, funct3, simm5, Vs2, vm, funct6); \
  }

  // Vector Whole Vector Register Move
  INSN(vmv1r_v, 0b1010111, 0b011, 0b00000, 0b1, 0b100111);
  INSN(vmv2r_v, 0b1010111, 0b011, 0b00001, 0b1, 0b100111);
  INSN(vmv4r_v, 0b1010111, 0b011, 0b00011, 0b1, 0b100111);
  INSN(vmv8r_v, 0b1010111, 0b011, 0b00111, 0b1, 0b100111);

#undef INSN

#define INSN(NAME, op, funct3, Vs1, vm, funct6)           \
  void NAME(FloatRegister Rd, VectorRegister Vs2) {       \
    patch_VArith(op, Rd, funct3, Vs1, Vs2, vm, funct6);   \
  }

  // Vector Floating-Point Move Instruction
  INSN(vfmv_f_s, 0b1010111, 0b001, 0b00000, 0b1, 0b010000);

#undef INSN

#define INSN(NAME, op, funct3, Vs1, vm, funct6)          \
  void NAME(Register Rd, VectorRegister Vs2) {           \
    patch_VArith(op, Rd, funct3, Vs1, Vs2, vm, funct6);  \
  }

  // Vector Integer Scalar Move Instructions
  INSN(vmv_x_s, 0b1010111, 0b010, 0b00000, 0b1, 0b010000);

#undef INSN

// r_vm
#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, uint32_t imm, VectorMask vm = unmasked) {       \
    guarantee(is_unsigned_imm_in_range(imm, 5, 0), "imm is invalid");                              \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);                         \
  }

  // Vector Register Gather Instruction
  INSN(vrgather_vi,   0b1010111, 0b011, 0b001100);

  // Vector Slide Instructions
  INSN(vslidedown_vi, 0b1010111, 0b011, 0b001111);
  INSN(vslideup_vi,   0b1010111, 0b011, 0b001110);

  // Vector Narrowing Fixed-Point Clip Instructions
  INSN(vnclip_wi,  0b1010111, 0b011, 0b101111);
  INSN(vnclipu_wi, 0b1010111, 0b011, 0b101110);

  // Vector Single-Width Scaling Shift Instructions
  INSN(vssra_vi,   0b1010111, 0b011, 0b101011);
  INSN(vssrl_vi,   0b1010111, 0b011, 0b101010);

  // Vector Narrowing Integer Right Shift Instructions
  INSN(vnsra_wi,   0b1010111, 0b011, 0b101101);
  INSN(vnsrl_wi,   0b1010111, 0b011, 0b101100);

  // Vector Single-Width Bit Shift Instructions
  INSN(vsra_vi,    0b1010111, 0b011, 0b101001);
  INSN(vsrl_vi,    0b1010111, 0b011, 0b101000);
  INSN(vsll_vi,    0b1010111, 0b011, 0b100101);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs1, VectorRegister Vs2, VectorMask vm = unmasked) { \
    patch_VArith(op, Vd, funct3, Vs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Widening Floating-Point Fused Multiply-Add Instructions
  INSN(vfwnmsac_vv, 0b1010111, 0b001, 0b111111);
  INSN(vfwmsac_vv,  0b1010111, 0b001, 0b111110);
  INSN(vfwnmacc_vv, 0b1010111, 0b001, 0b111101);
  INSN(vfwmacc_vv,  0b1010111, 0b001, 0b111100);

  // Vector Single-Width Floating-Point Fused Multiply-Add Instructions
  INSN(vfnmsub_vv, 0b1010111, 0b001, 0b101011);
  INSN(vfmsub_vv,  0b1010111, 0b001, 0b101010);
  INSN(vfnmadd_vv, 0b1010111, 0b001, 0b101001);
  INSN(vfmadd_vv,  0b1010111, 0b001, 0b101000);
  INSN(vfnmsac_vv, 0b1010111, 0b001, 0b101111);
  INSN(vfmsac_vv,  0b1010111, 0b001, 0b101110);
  INSN(vfmacc_vv,  0b1010111, 0b001, 0b101100);
  INSN(vfnmacc_vv, 0b1010111, 0b001, 0b101101);

  // Vector Widening Integer Multiply-Add Instructions
  INSN(vwmaccsu_vv, 0b1010111, 0b010, 0b111111);
  INSN(vwmacc_vv,   0b1010111, 0b010, 0b111101);
  INSN(vwmaccu_vv,  0b1010111, 0b010, 0b111100);

  // Vector Single-Width Integer Multiply-Add Instructions
  INSN(vnmsub_vv, 0b1010111, 0b010, 0b101011);
  INSN(vmadd_vv,  0b1010111, 0b010, 0b101001);
  INSN(vnmsac_vv, 0b1010111, 0b010, 0b101111);
  INSN(vmacc_vv,  0b1010111, 0b010, 0b101101);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, Register Rs1, VectorRegister Vs2, VectorMask vm = unmasked) {       \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Widening Integer Multiply-Add Instructions
  INSN(vwmaccsu_vx, 0b1010111, 0b110, 0b111111);
  INSN(vwmacc_vx,   0b1010111, 0b110, 0b111101);
  INSN(vwmaccu_vx,  0b1010111, 0b110, 0b111100);
  INSN(vwmaccus_vx, 0b1010111, 0b110, 0b111110);

  // Vector Single-Width Integer Multiply-Add Instructions
  INSN(vnmsub_vx, 0b1010111, 0b110, 0b101011);
  INSN(vmadd_vx,  0b1010111, 0b110, 0b101001);
  INSN(vnmsac_vx, 0b1010111, 0b110, 0b101111);
  INSN(vmacc_vx,  0b1010111, 0b110, 0b101101);

  INSN(vrsub_vx,  0b1010111, 0b100, 0b000011);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, FloatRegister Rs1, VectorRegister Vs2, VectorMask vm = unmasked) {  \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Widening Floating-Point Fused Multiply-Add Instructions
  INSN(vfwnmsac_vf, 0b1010111, 0b101, 0b111111);
  INSN(vfwmsac_vf,  0b1010111, 0b101, 0b111110);
  INSN(vfwnmacc_vf, 0b1010111, 0b101, 0b111101);
  INSN(vfwmacc_vf,  0b1010111, 0b101, 0b111100);

  // Vector Single-Width Floating-Point Fused Multiply-Add Instructions
  INSN(vfnmsub_vf, 0b1010111, 0b101, 0b101011);
  INSN(vfmsub_vf,  0b1010111, 0b101, 0b101010);
  INSN(vfnmadd_vf, 0b1010111, 0b101, 0b101001);
  INSN(vfmadd_vf,  0b1010111, 0b101, 0b101000);
  INSN(vfnmsac_vf, 0b1010111, 0b101, 0b101111);
  INSN(vfmsac_vf,  0b1010111, 0b101, 0b101110);
  INSN(vfmacc_vf,  0b1010111, 0b101, 0b101100);
  INSN(vfnmacc_vf, 0b1010111, 0b101, 0b101101);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, VectorRegister Vs1, VectorMask vm = unmasked) { \
    patch_VArith(op, Vd, funct3, Vs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Register Gather Instruction
  INSN(vrgather_vv,     0b1010111, 0b000, 0b001100);
  INSN(vrgatherei16_vv, 0b1010111, 0b000, 0b001110);

  // Vector Widening Floating-Point Reduction Instructions
  INSN(vfwredsum_vs,  0b1010111, 0b001, 0b110001);
  INSN(vfwredosum_vs, 0b1010111, 0b001, 0b110011);

  // Vector Single-Width Floating-Point Reduction Instructions
  INSN(vfredsum_vs,   0b1010111, 0b001, 0b000001);
  INSN(vfredosum_vs,  0b1010111, 0b001, 0b000011);
  INSN(vfredmin_vs,   0b1010111, 0b001, 0b000101);
  INSN(vfredmax_vs,   0b1010111, 0b001, 0b000111);

  // Vector Single-Width Integer Reduction Instructions
  INSN(vredsum_vs,    0b1010111, 0b010, 0b000000);
  INSN(vredand_vs,    0b1010111, 0b010, 0b000001);
  INSN(vredor_vs,     0b1010111, 0b010, 0b000010);
  INSN(vredxor_vs,    0b1010111, 0b010, 0b000011);
  INSN(vredminu_vs,   0b1010111, 0b010, 0b000100);
  INSN(vredmin_vs,    0b1010111, 0b010, 0b000101);
  INSN(vredmaxu_vs,   0b1010111, 0b010, 0b000110);
  INSN(vredmax_vs,    0b1010111, 0b010, 0b000111);

  // Vector Widening Integer Reduction Instructions
  INSN(vwredsumu_vs,  0b1010111, 0b000, 0b110000);
  INSN(vwredsum_vs,   0b1010111, 0b000, 0b110001);

  // Vector Floating-Point Compare Instructions
  INSN(vmfle_vv, 0b1010111, 0b001, 0b011001);
  INSN(vmflt_vv, 0b1010111, 0b001, 0b011011);
  INSN(vmfne_vv, 0b1010111, 0b001, 0b011100);
  INSN(vmfeq_vv, 0b1010111, 0b001, 0b011000);

  // Vector Floating-Point Sign-Injection Instructions
  INSN(vfsgnjx_vv, 0b1010111, 0b001, 0b001010);
  INSN(vfsgnjn_vv, 0b1010111, 0b001, 0b001001);
  INSN(vfsgnj_vv,  0b1010111, 0b001, 0b001000);

  // Vector Floating-Point MIN/MAX Instructions
  INSN(vfmax_vv,   0b1010111, 0b001, 0b000110);
  INSN(vfmin_vv,   0b1010111, 0b001, 0b000100);

  // Vector Widening Floating-Point Multiply
  INSN(vfwmul_vv,  0b1010111, 0b001, 0b111000);

  // Vector Single-Width Floating-Point Multiply/Divide Instructions
  INSN(vfdiv_vv,   0b1010111, 0b001, 0b100000);
  INSN(vfmul_vv,   0b1010111, 0b001, 0b100100);

  // Vector Widening Floating-Point Add/Subtract Instructions
  INSN(vfwsub_wv, 0b1010111, 0b001, 0b110110);
  INSN(vfwsub_vv, 0b1010111, 0b001, 0b110010);
  INSN(vfwadd_wv, 0b1010111, 0b001, 0b110100);
  INSN(vfwadd_vv, 0b1010111, 0b001, 0b110000);

  // Vector Single-Width Floating-Point Add/Subtract Instructions
  INSN(vfsub_vv, 0b1010111, 0b001, 0b000010);
  INSN(vfadd_vv, 0b1010111, 0b001, 0b000000);

  // Vector Narrowing Fixed-Point Clip Instructions
  INSN(vnclip_wv,  0b1010111, 0b000, 0b101111);
  INSN(vnclipu_wv, 0b1010111, 0b000, 0b101110);

  // Vector Single-Width Scaling Shift Instructions
  INSN(vssra_vv, 0b1010111, 0b000, 0b101011);
  INSN(vssrl_vv, 0b1010111, 0b000, 0b101010);

  // Vector Single-Width Fractional Multiply with Rounding and Saturation
  INSN(vsmul_vv, 0b1010111, 0b000, 0b100111);

  // Vector Single-Width Averaging Add and Subtract
  INSN(vasubu_vv, 0b1010111, 0b010, 0b001010);
  INSN(vasub_vv,  0b1010111, 0b010, 0b001011);
  INSN(vaaddu_vv, 0b1010111, 0b010, 0b001000);
  INSN(vaadd_vv,  0b1010111, 0b010, 0b001001);

  // Vector Single-Width Saturating Add and Subtract
  INSN(vssub_vv,  0b1010111, 0b000, 0b100011);
  INSN(vssubu_vv, 0b1010111, 0b000, 0b100010);
  INSN(vsadd_vv,  0b1010111, 0b000, 0b100001);
  INSN(vsaddu_vv, 0b1010111, 0b000, 0b100000);

  // Vector Widening Integer Multiply Instructions
  INSN(vwmul_vv,   0b1010111, 0b010, 0b111011);
  INSN(vwmulsu_vv, 0b1010111, 0b010, 0b111010);
  INSN(vwmulu_vv,  0b1010111, 0b010, 0b111000);

  // Vector Integer Divide Instructions
  INSN(vrem_vv,  0b1010111, 0b010, 0b100011);
  INSN(vremu_vv, 0b1010111, 0b010, 0b100010);
  INSN(vdiv_vv,  0b1010111, 0b010, 0b100001);
  INSN(vdivu_vv, 0b1010111, 0b010, 0b100000);

  // Vector Single-Width Integer Multiply Instructions
  INSN(vmulhsu_vv, 0b1010111, 0b010, 0b100110);
  INSN(vmulhu_vv,  0b1010111, 0b010, 0b100100);
  INSN(vmulh_vv,   0b1010111, 0b010, 0b100111);
  INSN(vmul_vv,    0b1010111, 0b010, 0b100101);

  // Vector Integer Min/Max Instructions
  INSN(vmax_vv,  0b1010111, 0b000, 0b000111);
  INSN(vmaxu_vv, 0b1010111, 0b000, 0b000110);
  INSN(vmin_vv,  0b1010111, 0b000, 0b000101);
  INSN(vminu_vv, 0b1010111, 0b000, 0b000100);

  // Vector Integer Comparison Instructions
  INSN(vmsle_vv,  0b1010111, 0b000, 0b011101);
  INSN(vmsleu_vv, 0b1010111, 0b000, 0b011100);
  INSN(vmslt_vv,  0b1010111, 0b000, 0b011011);
  INSN(vmsltu_vv, 0b1010111, 0b000, 0b011010);
  INSN(vmsne_vv,  0b1010111, 0b000, 0b011001);
  INSN(vmseq_vv,  0b1010111, 0b000, 0b011000);

  // Vector Narrowing Integer Right Shift Instructions
  INSN(vnsra_wv, 0b1010111, 0b000, 0b101101);
  INSN(vnsrl_wv, 0b1010111, 0b000, 0b101100);

  // Vector Single-Width Bit Shift Instructions
  INSN(vsra_vv, 0b1010111, 0b000, 0b101001);
  INSN(vsrl_vv, 0b1010111, 0b000, 0b101000);
  INSN(vsll_vv, 0b1010111, 0b000, 0b100101);

  // Vector Bitwise Logical Instructions
  INSN(vxor_vv, 0b1010111, 0b000, 0b001011);
  INSN(vor_vv,  0b1010111, 0b000, 0b001010);
  INSN(vand_vv, 0b1010111, 0b000, 0b001001);

  // Vector Widening Integer Add/Subtract
  INSN(vwsub_wv,  0b1010111, 0b010, 0b110111);
  INSN(vwsubu_wv, 0b1010111, 0b010, 0b110110);
  INSN(vwadd_wv,  0b1010111, 0b010, 0b110101);
  INSN(vwaddu_wv, 0b1010111, 0b010, 0b110100);
  INSN(vwsub_vv,  0b1010111, 0b010, 0b110011);
  INSN(vwsubu_vv, 0b1010111, 0b010, 0b110010);
  INSN(vwadd_vv,  0b1010111, 0b010, 0b110001);
  INSN(vwaddu_vv, 0b1010111, 0b010, 0b110000);

  // Vector Single-Width Integer Add and Subtract
  INSN(vsub_vv, 0b1010111, 0b000, 0b000010);
  INSN(vadd_vv, 0b1010111, 0b000, 0b000000);

#undef INSN


#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, Register Rs1, VectorMask vm = unmasked) {       \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Register Gather Instruction
  INSN(vrgather_vx, 0b1010111, 0b100, 0b001100);

  // Vector Slide Instructions
  INSN(vslide1down_vx, 0b1010111, 0b110, 0b001111);
  INSN(vslidedown_vx,  0b1010111, 0b100, 0b001111);
  INSN(vslide1up_vx,   0b1010111, 0b110, 0b001110);
  INSN(vslideup_vx,    0b1010111, 0b100, 0b001110);

  // Vector Narrowing Fixed-Point Clip Instructions
  INSN(vnclip_wx,  0b1010111, 0b100, 0b101111);
  INSN(vnclipu_wx, 0b1010111, 0b100, 0b101110);

  // Vector Single-Width Scaling Shift Instructions
  INSN(vssra_vx, 0b1010111, 0b100, 0b101011);
  INSN(vssrl_vx, 0b1010111, 0b100, 0b101010);

  // Vector Single-Width Fractional Multiply with Rounding and Saturation
  INSN(vsmul_vx, 0b1010111, 0b100, 0b100111);

  // Vector Single-Width Averaging Add and Subtract
  INSN(vasubu_vx, 0b1010111, 0b110, 0b001010);
  INSN(vasub_vx,  0b1010111, 0b110, 0b001011);
  INSN(vaaddu_vx, 0b1010111, 0b110, 0b001000);
  INSN(vaadd_vx,  0b1010111, 0b110, 0b001001);

  // Vector Single-Width Saturating Add and Subtract
  INSN(vssub_vx,  0b1010111, 0b100, 0b100011);
  INSN(vssubu_vx, 0b1010111, 0b100, 0b100010);
  INSN(vsadd_vx,  0b1010111, 0b100, 0b100001);
  INSN(vsaddu_vx, 0b1010111, 0b100, 0b100000);

  // Vector Widening Integer Multiply Instructions
  INSN(vwmul_vx,   0b1010111, 0b110, 0b111011);
  INSN(vwmulsu_vx, 0b1010111, 0b110, 0b111010);
  INSN(vwmulu_vx,  0b1010111, 0b110, 0b111000);

  // Vector Integer Divide Instructions
  INSN(vrem_vx,  0b1010111, 0b110, 0b100011);
  INSN(vremu_vx, 0b1010111, 0b110, 0b100010);
  INSN(vdiv_vx,  0b1010111, 0b110, 0b100001);
  INSN(vdivu_vx, 0b1010111, 0b110, 0b100000);

  // Vector Single-Width Integer Multiply Instructions
  INSN(vmulhsu_vx, 0b1010111, 0b110, 0b100110);
  INSN(vmulhu_vx,  0b1010111, 0b110, 0b100100);
  INSN(vmulh_vx,   0b1010111, 0b110, 0b100111);
  INSN(vmul_vx,    0b1010111, 0b110, 0b100101);

  // Vector Integer Min/Max Instructions
  INSN(vmax_vx,  0b1010111, 0b100, 0b000111);
  INSN(vmaxu_vx, 0b1010111, 0b100, 0b000110);
  INSN(vmin_vx,  0b1010111, 0b100, 0b000101);
  INSN(vminu_vx, 0b1010111, 0b100, 0b000100);

  // Vector Integer Comparison Instructions
  INSN(vmsgt_vx,  0b1010111, 0b100, 0b011111);
  INSN(vmsgtu_vx, 0b1010111, 0b100, 0b011110);
  INSN(vmsle_vx,  0b1010111, 0b100, 0b011101);
  INSN(vmsleu_vx, 0b1010111, 0b100, 0b011100);
  INSN(vmslt_vx,  0b1010111, 0b100, 0b011011);
  INSN(vmsltu_vx, 0b1010111, 0b100, 0b011010);
  INSN(vmsne_vx,  0b1010111, 0b100, 0b011001);
  INSN(vmseq_vx,  0b1010111, 0b100, 0b011000);

  // Vector Narrowing Integer Right Shift Instructions
  INSN(vnsra_wx, 0b1010111, 0b100, 0b101101);
  INSN(vnsrl_wx, 0b1010111, 0b100, 0b101100);

  // Vector Single-Width Bit Shift Instructions
  INSN(vsra_vx, 0b1010111, 0b100, 0b101001);
  INSN(vsrl_vx, 0b1010111, 0b100, 0b101000);
  INSN(vsll_vx, 0b1010111, 0b100, 0b100101);

  // Vector Bitwise Logical Instructions
  INSN(vxor_vx, 0b1010111, 0b100, 0b001011);
  INSN(vor_vx,  0b1010111, 0b100, 0b001010);
  INSN(vand_vx, 0b1010111, 0b100, 0b001001);

  // Vector Widening Integer Add/Subtract
  INSN(vwsub_wx,  0b1010111, 0b110, 0b110111);
  INSN(vwsubu_wx, 0b1010111, 0b110, 0b110110);
  INSN(vwadd_wx,  0b1010111, 0b110, 0b110101);
  INSN(vwadd_wv,  0b1010111, 0b010, 0b110101);
  INSN(vwaddu_wx, 0b1010111, 0b110, 0b110100);
  INSN(vwsub_vx,  0b1010111, 0b110, 0b110011);
  INSN(vwsubu_vx, 0b1010111, 0b110, 0b110010);
  INSN(vwadd_vx,  0b1010111, 0b110, 0b110001);
  INSN(vwaddu_vx, 0b1010111, 0b110, 0b110000);

  // Vector Single-Width Integer Add and Subtract
  INSN(vsub_vx, 0b1010111, 0b100, 0b000010);
  INSN(vadd_vx, 0b1010111, 0b100, 0b000000);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, FloatRegister Rs1, VectorMask vm = unmasked) {  \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                        \
  }

  // Vector Floating-Point Compare Instructions
  INSN(vmfge_vf, 0b1010111, 0b101, 0b011111);
  INSN(vmfgt_vf, 0b1010111, 0b101, 0b011101);
  INSN(vmfle_vf, 0b1010111, 0b101, 0b011001);
  INSN(vmflt_vf, 0b1010111, 0b101, 0b011011);
  INSN(vmfne_vf, 0b1010111, 0b101, 0b011100);
  INSN(vmfeq_vf, 0b1010111, 0b101, 0b011000);

  // Vector Slide1up/Slide1down Instruction
  INSN(vfslide1down_vf, 0b1010111, 0b101, 0b001111);
  INSN(vfslide1up_vf,   0b1010111, 0b101, 0b001110);

  // Vector Floating-Point Sign-Injection Instructions
  INSN(vfsgnjx_vf, 0b1010111, 0b101, 0b001010);
  INSN(vfsgnjn_vf, 0b1010111, 0b101, 0b001001);
  INSN(vfsgnj_vf,  0b1010111, 0b101, 0b001000);

  // Vector Floating-Point MIN/MAX Instructions
  INSN(vfmax_vf, 0b1010111, 0b101, 0b000110);
  INSN(vfmin_vf, 0b1010111, 0b101, 0b000100);

  // Vector Widening Floating-Point Multiply
  INSN(vfwmul_vf, 0b1010111, 0b101, 0b111000);

  // Vector Single-Width Floating-Point Multiply/Divide Instructions
  INSN(vfdiv_vf,  0b1010111, 0b101, 0b100000);
  INSN(vfmul_vf,  0b1010111, 0b101, 0b100100);
  INSN(vfrdiv_vf, 0b1010111, 0b101, 0b100001);

  // Vector Widening Floating-Point Add/Subtract Instructions
  INSN(vfwsub_wf, 0b1010111, 0b101, 0b110110);
  INSN(vfwsub_vf, 0b1010111, 0b101, 0b110010);
  INSN(vfwadd_wf, 0b1010111, 0b101, 0b110100);
  INSN(vfwadd_vf, 0b1010111, 0b101, 0b110000);

  // Vector Single-Width Floating-Point Add/Subtract Instructions
  INSN(vfsub_vf,  0b1010111, 0b101, 0b000010);
  INSN(vfadd_vf,  0b1010111, 0b101, 0b000000);
  INSN(vfrsub_vf, 0b1010111, 0b101, 0b100111);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, int32_t imm, VectorMask vm = unmasked) {        \
    guarantee(is_imm_in_range(imm, 5, 0), "imm is invalid");                                       \
    patch_VArith(op, Vd, funct3, (uint32_t)imm & 0x1f, Vs2, vm, funct6);                           \
  }

  INSN(vsadd_vi,  0b1010111, 0b011, 0b100001);
  INSN(vsaddu_vi, 0b1010111, 0b011, 0b100000);
  INSN(vmsgt_vi,  0b1010111, 0b011, 0b011111);
  INSN(vmsgtu_vi, 0b1010111, 0b011, 0b011110);
  INSN(vmsle_vi,  0b1010111, 0b011, 0b011101);
  INSN(vmsleu_vi, 0b1010111, 0b011, 0b011100);
  INSN(vmsne_vi,  0b1010111, 0b011, 0b011001);
  INSN(vmseq_vi,  0b1010111, 0b011, 0b011000);
  INSN(vxor_vi,   0b1010111, 0b011, 0b001011);
  INSN(vor_vi,    0b1010111, 0b011, 0b001010);
  INSN(vand_vi,   0b1010111, 0b011, 0b001001);
  INSN(vadd_vi,   0b1010111, 0b011, 0b000000);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, int32_t imm, VectorRegister Vs2, VectorMask vm = unmasked) {        \
    guarantee(is_imm_in_range(imm, 5, 0), "imm is invalid");                                       \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);                         \
  }

  INSN(vrsub_vi, 0b1010111, 0b011, 0b000011);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                   \
  void NAME(VectorRegister Vd, VectorRegister Vs2, VectorRegister Vs1) {     \
    patch_VArith(op, Vd, funct3, Vs1->encoding_nocheck(), Vs2, vm, funct6);  \
  }

  // Vector Compress Instruction
  INSN(vcompress_vm, 0b1010111, 0b010, 0b1, 0b010111);

  // Vector Mask-Register Logical Instructions
  INSN(vmxnor_mm,   0b1010111, 0b010, 0b1, 0b011111);
  INSN(vmornot_mm,  0b1010111, 0b010, 0b1, 0b011100);
  INSN(vmnor_mm,    0b1010111, 0b010, 0b1, 0b011110);
  INSN(vmor_mm,     0b1010111, 0b010, 0b1, 0b011010);
  INSN(vmxor_mm,    0b1010111, 0b010, 0b1, 0b011011);
  INSN(vmandnot_mm, 0b1010111, 0b010, 0b1, 0b011000);
  INSN(vmnand_mm,   0b1010111, 0b010, 0b1, 0b011101);
  INSN(vmand_mm,    0b1010111, 0b010, 0b1, 0b011001);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                  \
  void NAME(VectorRegister Vd, VectorRegister Vs2, int32_t imm, VectorRegister V0) {        \
    guarantee(is_imm_in_range(imm, 5, 0), "imm is invalid");                                \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);                  \
  }

  // Vector Integer Merge Instructions
  INSN(vmerge_vim, 0b1010111, 0b011, 0b0, 0b010111);

  // Vector Integer Add-with-Carry / Subtract-with-Borrow Instructions
  INSN(vadc_vim,   0b1010111, 0b011, 0b0, 0b010000);
  INSN(vmadc_vim,  0b1010111, 0b011, 0b0, 0b010001);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                  \
  void NAME(VectorRegister Vd, VectorRegister Vs2, VectorRegister Vs1, VectorRegister V0) { \
    patch_VArith(op, Vd, funct3, Vs1->encoding_nocheck(), Vs2, vm, funct6);                 \
  }

  // Vector Integer Merge Instructions
  INSN(vmerge_vvm, 0b1010111, 0b000, 0b0, 0b010111);

  // Vector Integer Add-with-Carry / Subtract-with-Borrow Instructions
  INSN(vsbc_vvm, 0b1010111, 0b000, 0b0, 0b010010);
  INSN(vadc_vvm, 0b1010111, 0b000, 0b0, 0b010000);

  // Vector Integer Add-with-Carry / Subtract-with-Borrow Instructions
  INSN(vmadc_vvm, 0b1010111, 0b000, 0b0, 0b010001);
  INSN(vmsbc_vvm, 0b1010111, 0b000, 0b0, 0b010011);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                  \
  void NAME(VectorRegister Vd, VectorRegister Vs2, FloatRegister Rs1, VectorRegister V0) {  \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                 \
  }

  // Vector Floating-Point Merge Instruction
  INSN(vfmerge_vfm, 0b1010111, 0b101, 0b0, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                  \
  void NAME(VectorRegister Vd, VectorRegister Vs2, Register Rs1, VectorRegister V0) {       \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6);                 \
  }

  // Vector Integer Merge Instructions
  INSN(vmerge_vxm, 0b1010111, 0b100, 0b0, 0b010111);

  // Vector Integer Add-with-Carry / Subtract-with-Borrow Instructions
  INSN(vsbc_vxm, 0b1010111, 0b100, 0b0, 0b010010);
  INSN(vadc_vxm, 0b1010111, 0b100, 0b0, 0b010000);

  // Vector Integer Add-with-Carry / Subtract-with-Borrow Instructions
  INSN(vmadc_vxm, 0b1010111, 0b100, 0b0, 0b010001);
  INSN(vmsbc_vxm, 0b1010111, 0b100, 0b0, 0b010011);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                            \
  void NAME(VectorRegister Vd, int32_t imm) {                              \
    guarantee(is_imm_in_range(imm, 5, 0), "imm is invalid");               \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6); \
  }

  // Vector Integer Move Instructions
  INSN(vmv_v_i, 0b1010111, 0b011, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
  void NAME(VectorRegister Vd, FloatRegister Rs1) {                         \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6); \
  }

  // Floating-Point Scalar Move Instructions
  INSN(vfmv_s_f, 0b1010111, 0b101, v0, 0b1, 0b010000);
  // Vector Floating-Point Move Instruction
  INSN(vfmv_v_f, 0b1010111, 0b101, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
  void NAME(VectorRegister Vd, VectorRegister Vs1) {                        \
    patch_VArith(op, Vd, funct3, Vs1->encoding_nocheck(), Vs2, vm, funct6); \
  }

  // Vector Integer Move Instructions
  INSN(vmv_v_v, 0b1010111, 0b000, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
   void NAME(VectorRegister Vd, Register Rs1) {                             \
    patch_VArith(op, Vd, funct3, Rs1->encoding_nocheck(), Vs2, vm, funct6); \
   }

  // Integer Scalar Move Instructions
  INSN(vmv_s_x, 0b1010111, 0b110, v0, 0b1, 0b010000);

  // Vector Integer Move Instructions
  INSN(vmv_v_x, 0b1010111, 0b100, v0, 0b1, 0b010111);

#undef INSN
#undef patch_VArith

#define INSN(NAME, op, funct13, funct6)                    \
  void NAME(VectorRegister Vd, VectorMask vm = unmasked) { \
    unsigned insn = 0;                                     \
    patch((address)&insn, 6, 0, op);                       \
    patch((address)&insn, 24, 12, funct13);                \
    patch((address)&insn, 25, vm);                         \
    patch((address)&insn, 31, 26, funct6);                 \
    patch_reg((address)&insn, 7, Vd);                      \
    emit(insn);                                            \
  }

  // Vector Element Index Instruction
  INSN(vid_v, 0b1010111, 0b0000010001010, 0b010100);

#undef INSN

enum Nf {
  g1 = 0b000,
  g2 = 0b001,
  g3 = 0b010,
  g4 = 0b011,
  g5 = 0b100,
  g6 = 0b101,
  g7 = 0b110,
  g8 = 0b111
};

#define patch_VLdSt(op, VReg, width, Rs1, Reg_or_umop, vm, mop, mew, nf) \
    unsigned insn = 0;                                                   \
    patch((address)&insn, 6, 0, op);                                     \
    patch((address)&insn, 14, 12, width);                                \
    patch((address)&insn, 24, 20, Reg_or_umop);                          \
    patch((address)&insn, 25, vm);                                       \
    patch((address)&insn, 27, 26, mop);                                  \
    patch((address)&insn, 28, mew);                                      \
    patch((address)&insn, 31, 29, nf);                                   \
    patch_reg((address)&insn, 7, VReg);                                  \
    patch_reg((address)&insn, 15, Rs1);                                  \
    emit(insn)

#define INSN(NAME, op, lumop, vm, mop, nf)                                           \
  void NAME(VectorRegister Vd, Register Rs1, uint32_t width = 0, bool mew = false) { \
    guarantee(is_unsigned_imm_in_range(width, 3, 0), "width is invalid");            \
    patch_VLdSt(op, Vd, width, Rs1, lumop, vm, mop, mew, nf);                        \
  }

  // Vector Load/Store Instructions
  INSN(vl1r_v, 0b0000111, 0b01000, 0b1, 0b00, g1);

#undef INSN

#define INSN(NAME, op, width, sumop, vm, mop, mew, nf)           \
  void NAME(VectorRegister Vs3, Register Rs1) {                  \
    patch_VLdSt(op, Vs3, width, Rs1, sumop, vm, mop, mew, nf);   \
  }

  // Vector Load/Store Instructions
  INSN(vs1r_v, 0b0100111, 0b000, 0b01000, 0b1, 0b00, 0b0, g1);

#undef INSN

// r2_nfvm
#define INSN(NAME, op, width, umop, mop, mew)                         \
  void NAME(VectorRegister Vd_or_Vs3, Register Rs1, Nf nf = g1) {     \
    patch_VLdSt(op, Vd_or_Vs3, width, Rs1, umop, 1, mop, mew, nf);    \
  }

  // Vector Unit-Stride Instructions
  INSN(vle1_v, 0b0000111, 0b000, 0b01011, 0b00, 0b0);
  INSN(vse1_v, 0b0100111, 0b000, 0b01011, 0b00, 0b0);

#undef INSN

#define INSN(NAME, op, width, umop, mop, mew)                                               \
  void NAME(VectorRegister Vd_or_Vs3, Register Rs1, VectorMask vm = unmasked, Nf nf = g1) { \
    patch_VLdSt(op, Vd_or_Vs3, width, Rs1, umop, vm, mop, mew, nf);                         \
  }

  // Vector Unit-Stride Instructions
  INSN(vle8_v,    0b0000111, 0b000, 0b00000, 0b00, 0b0);
  INSN(vle16_v,   0b0000111, 0b101, 0b00000, 0b00, 0b0);
  INSN(vle32_v,   0b0000111, 0b110, 0b00000, 0b00, 0b0);
  INSN(vle64_v,   0b0000111, 0b111, 0b00000, 0b00, 0b0);

  // Vector unit-stride fault-only-first Instructions
  INSN(vle8ff_v,  0b0000111, 0b000, 0b10000, 0b00, 0b0);
  INSN(vle16ff_v, 0b0000111, 0b101, 0b10000, 0b00, 0b0);
  INSN(vle32ff_v, 0b0000111, 0b110, 0b10000, 0b00, 0b0);
  INSN(vle64ff_v, 0b0000111, 0b111, 0b10000, 0b00, 0b0);

  INSN(vse8_v,  0b0100111, 0b000, 0b00000, 0b00, 0b0);
  INSN(vse16_v, 0b0100111, 0b101, 0b00000, 0b00, 0b0);
  INSN(vse32_v, 0b0100111, 0b110, 0b00000, 0b00, 0b0);
  INSN(vse64_v, 0b0100111, 0b111, 0b00000, 0b00, 0b0);

#undef INSN

#define INSN(NAME, op, width, mop, mew)                                                                  \
  void NAME(VectorRegister Vd, Register Rs1, VectorRegister Vs2, VectorMask vm = unmasked, Nf nf = g1) { \
    patch_VLdSt(op, Vd, width, Rs1, Vs2->encoding_nocheck(), vm, mop, mew, nf);                          \
  }

  // Vector unordered indexed load instructions
  INSN(vluxei8_v,  0b0000111, 0b000, 0b01, 0b0);
  INSN(vluxei16_v, 0b0000111, 0b101, 0b01, 0b0);
  INSN(vluxei32_v, 0b0000111, 0b110, 0b01, 0b0);
  INSN(vluxei64_v, 0b0000111, 0b111, 0b01, 0b0);

  // Vector ordered indexed load instructions
  INSN(vloxei8_v,  0b0000111, 0b000, 0b11, 0b0);
  INSN(vloxei16_v, 0b0000111, 0b101, 0b11, 0b0);
  INSN(vloxei32_v, 0b0000111, 0b110, 0b11, 0b0);
  INSN(vloxei64_v, 0b0000111, 0b111, 0b11, 0b0);
#undef INSN

#define INSN(NAME, op, width, mop, mew)                                                                  \
  void NAME(VectorRegister Vd, Register Rs1, Register Rs2, VectorMask vm = unmasked, Nf nf = g1) {       \
    patch_VLdSt(op, Vd, width, Rs1, Rs2->encoding_nocheck(), vm, mop, mew, nf);                          \
  }

  // Vector Strided Instructions
  INSN(vlse8_v,  0b0000111, 0b000, 0b10, 0b0);
  INSN(vlse16_v, 0b0000111, 0b101, 0b10, 0b0);
  INSN(vlse32_v, 0b0000111, 0b110, 0b10, 0b0);
  INSN(vlse64_v, 0b0000111, 0b111, 0b10, 0b0);

#undef INSN

#define INSN(NAME, op, width, mop, mew)                                                                   \
  void NAME(VectorRegister Vs3, Register Rs1, VectorRegister Vs2, VectorMask vm = unmasked, Nf nf = g1) { \
    patch_VLdSt(op, Vs3, width, Rs1, Vs2->encoding_nocheck(), vm, mop, mew, nf);                          \
  }

  // Vector unordered-indexed store instructions
  INSN(vsuxei8_v,  0b0100111, 0b000, 0b01, 0b0);
  INSN(vsuxei16_v, 0b0100111, 0b101, 0b01, 0b0);
  INSN(vsuxei32_v, 0b0100111, 0b110, 0b01, 0b0);
  INSN(vsuxei64_v, 0b0100111, 0b111, 0b01, 0b0);

  // Vector ordered indexed store instructions
  INSN(vsoxei8_v,  0b0100111, 0b000, 0b11, 0b0);
  INSN(vsoxei16_v, 0b0100111, 0b101, 0b11, 0b0);
  INSN(vsoxei32_v, 0b0100111, 0b110, 0b11, 0b0);
  INSN(vsoxei64_v, 0b0100111, 0b111, 0b11, 0b0);

#undef INSN

#define INSN(NAME, op, width, mop, mew)                                                                   \
  void NAME(VectorRegister Vs3, Register Rs1, Register Rs2, VectorMask vm = unmasked, Nf nf = g1) {       \
    patch_VLdSt(op, Vs3, width, Rs1, Rs2->encoding_nocheck(), vm, mop, mew, nf);                          \
  }

  INSN(vsse8_v,  0b0100111, 0b000, 0b10, 0b0);
  INSN(vsse16_v, 0b0100111, 0b101, 0b10, 0b0);
  INSN(vsse32_v, 0b0100111, 0b110, 0b10, 0b0);
  INSN(vsse64_v, 0b0100111, 0b111, 0b10, 0b0);

#undef INSN
#undef patch_VLdSt

  void bgt(Register Rs, Register Rt, const address &dest);
  void ble(Register Rs, Register Rt, const address &dest);
  void bgtu(Register Rs, Register Rt, const address &dest);
  void bleu(Register Rs, Register Rt, const address &dest);
  void bgt(Register Rs, Register Rt, Label &l, bool is_far = false);
  void ble(Register Rs, Register Rt, Label &l, bool is_far = false);
  void bgtu(Register Rs, Register Rt, Label &l, bool is_far = false);
  void bleu(Register Rs, Register Rt, Label &l, bool is_far = false);

  typedef void (Assembler::* jal_jalr_insn)(Register Rt, address dest);
  typedef void (Assembler::* load_insn_by_temp)(Register Rt, address dest, Register temp);
  typedef void (Assembler::* compare_and_branch_insn)(Register Rs1, Register Rs2, const address dest);
  typedef void (Assembler::* compare_and_branch_label_insn)(Register Rs1, Register Rs2, Label &L, bool is_far);

  void wrap_label(Register r1, Register r2, Label &L, compare_and_branch_insn insn,
                  compare_and_branch_label_insn neg_insn, bool is_far);
  void wrap_label(Register r, Label &L, Register t, load_insn_by_temp insn);
  void wrap_label(Register r, Label &L, jal_jalr_insn insn);

  // calculate pseudoinstruction
  void add(Register Rd, Register Rn, int64_t increment, Register temp = t0);
  void addw(Register Rd, Register Rn, int64_t increment, Register temp = t0);
  void sub(Register Rd, Register Rn, int64_t decrement, Register temp = t0);
  void subw(Register Rd, Register Rn, int64_t decrement, Register temp = t0);

  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
  }

  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr,
                                                Register tmp,
                                                int offset) {
    ShouldNotCallThis();
    return RegisterOrConstant();
  }

  // Stack overflow checking
  virtual void bang_stack_with_offset(int offset) { Unimplemented(); }

  static bool operand_valid_for_add_immediate(long imm) {
    return is_imm_in_range(imm, 12, 0);
  }

  // The maximum range of a branch is fixed for the riscv64
  // architecture.
  static const unsigned long branch_range = 1 * M;

  static bool reachable_from_branch_at(address branch, address target) {
    return uabs(target - branch) < branch_range;
  }

  static Assembler::SEW elemBytes_to_sew(int esize) {
    assert(esize > 0 && esize <= 64 && is_power_of_2(esize), "unsupported element size");
    return (Assembler::SEW) log2i_exact(esize);
  }

  virtual ~Assembler() {}

};

class BiasedLockingCounters;

#endif // CPU_RISCV_ASSEMBLER_RISCV_HPP
