/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/register.hpp"
#include "code/codeCache.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include <type_traits>

#define XLEN 64

// definitions of various symbolic names for machine registers

// First intercalls between C and Java which use 8 general registers
// and 8 floating registers

class Argument {
 public:
  enum {
    n_int_register_parameters_c   = 8, // x10, x11, ... x17 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8, // f10, f11, ... f17 (c_farg0, c_farg1, ... )

    n_int_register_parameters_j   = 8, // x11, ... x17, x10 (j_rarg0, j_rarg1, ...)
    n_float_register_parameters_j = 8  // f10, f11, ... f17 (j_farg0, j_farg1, ...)
  };
};

// function argument(caller-save registers)
constexpr Register c_rarg0 = x10;
constexpr Register c_rarg1 = x11;
constexpr Register c_rarg2 = x12;
constexpr Register c_rarg3 = x13;
constexpr Register c_rarg4 = x14;
constexpr Register c_rarg5 = x15;
constexpr Register c_rarg6 = x16;
constexpr Register c_rarg7 = x17;

constexpr FloatRegister c_farg0 = f10;
constexpr FloatRegister c_farg1 = f11;
constexpr FloatRegister c_farg2 = f12;
constexpr FloatRegister c_farg3 = f13;
constexpr FloatRegister c_farg4 = f14;
constexpr FloatRegister c_farg5 = f15;
constexpr FloatRegister c_farg6 = f16;
constexpr FloatRegister c_farg7 = f17;

// Symbolically name the register arguments used by the Java calling convention.
// We have control over the convention for java so we can do what we please.
// What pleases us is to offset the java calling convention so that when
// we call a suitable jni method the arguments are lined up and we don't
// have to do much shuffling. A suitable jni method is non-static and a
// small number of arguments.
//
// |------------------------------------------------------------------------|
// | c_rarg0  c_rarg1  c_rarg2  c_rarg3  c_rarg4  c_rarg5  c_rarg6  c_rarg7 |
// |------------------------------------------------------------------------|
// | x10      x11      x12      x13      x14      x15      x16      x17     |
// |------------------------------------------------------------------------|
// | j_rarg7  j_rarg0  j_rarg1  j_rarg2  j_rarg3  j_rarg4  j_rarg5  j_rarg6 |
// |------------------------------------------------------------------------|

constexpr Register j_rarg0 = c_rarg1;
constexpr Register j_rarg1 = c_rarg2;
constexpr Register j_rarg2 = c_rarg3;
constexpr Register j_rarg3 = c_rarg4;
constexpr Register j_rarg4 = c_rarg5;
constexpr Register j_rarg5 = c_rarg6;
constexpr Register j_rarg6 = c_rarg7;
constexpr Register j_rarg7 = c_rarg0;

// Java floating args are passed as per C

constexpr FloatRegister j_farg0 = f10;
constexpr FloatRegister j_farg1 = f11;
constexpr FloatRegister j_farg2 = f12;
constexpr FloatRegister j_farg3 = f13;
constexpr FloatRegister j_farg4 = f14;
constexpr FloatRegister j_farg5 = f15;
constexpr FloatRegister j_farg6 = f16;
constexpr FloatRegister j_farg7 = f17;

// zero rigster
constexpr Register zr = x0;
// global pointer
constexpr Register gp = x3;
// thread pointer
constexpr Register tp = x4;

// registers used to hold VM data either temporarily within a method
// or across method calls

// volatile (caller-save) registers

// current method -- must be in a call-clobbered register
constexpr Register xmethod =  x31;
// return address
constexpr Register ra      =  x1;

// non-volatile (callee-save) registers

constexpr Register sp            = x2; // stack pointer
constexpr Register fp            = x8; // frame pointer
constexpr Register xheapbase     = x27; // base of heap
constexpr Register xcpool        = x26; // constant pool cache
constexpr Register xmonitors     = x25; // monitors allocated on stack
constexpr Register xlocals       = x24; // locals on stack
constexpr Register xthread       = x23; // java thread pointer
constexpr Register xbcp          = x22; // bytecode pointer
constexpr Register xdispatch     = x21; // Dispatch table base
constexpr Register esp           = x20; // Java expression stack pointer
constexpr Register x19_sender_sp = x19; // Sender's SP while in interpreter

// temporary register(caller-save registers)
constexpr Register t0 = x5;
constexpr Register t1 = x6;
constexpr Register t2 = x7;

const Register g_INTArgReg[Argument::n_int_register_parameters_c] = {
  c_rarg0, c_rarg1, c_rarg2, c_rarg3, c_rarg4, c_rarg5, c_rarg6, c_rarg7
};

const FloatRegister g_FPArgReg[Argument::n_float_register_parameters_c] = {
  c_farg0, c_farg1, c_farg2, c_farg3, c_farg4, c_farg5, c_farg6, c_farg7
};

#define assert_cond(ARG1) assert(ARG1, #ARG1)

// Addressing modes
class Address {
 public:

  enum mode { no_mode, base_plus_offset, literal };

 private:
  struct Nonliteral {
    Nonliteral(Register base, Register index, int64_t offset)
      : _base(base), _index(index), _offset(offset) {}
    Register _base;
    Register _index;
    int64_t _offset;
  };

  struct Literal {
    Literal(address target, const RelocationHolder& rspec)
      : _target(target), _rspec(rspec) {}
    // If the target is far we'll need to load the ea of this to a
    // register to reach it. Otherwise if near we can do PC-relative
    // addressing.
    address _target;

    RelocationHolder _rspec;
  };

  void assert_is_nonliteral() const NOT_DEBUG_RETURN;
  void assert_is_literal() const NOT_DEBUG_RETURN;

  // Discriminated union, based on _mode.
  // - no_mode: uses dummy _nonliteral, for ease of copying.
  // - literal: only _literal is used.
  // - others: only _nonliteral is used.
  enum mode _mode;
  union {
    Nonliteral _nonliteral;
    Literal _literal;
  };

  // Helper for copy constructor and assignment operator.
  // Copy mode-relevant part of a into this.
  void copy_data(const Address& a) {
    assert(_mode == a._mode, "precondition");
    if (_mode == literal) {
      new (&_literal) Literal(a._literal);
    } else {
      // non-literal mode or no_mode.
      new (&_nonliteral) Nonliteral(a._nonliteral);
    }
  }

 public:
  // no_mode initializes _nonliteral for ease of copying.
  Address() :
    _mode(no_mode),
    _nonliteral(noreg, noreg, 0)
  {}

  Address(Register r) :
    _mode(base_plus_offset),
    _nonliteral(r, noreg, 0)
  {}

  template<typename T, ENABLE_IF(std::is_integral<T>::value)>
  Address(Register r, T o) :
    _mode(base_plus_offset),
    _nonliteral(r, noreg, o)
  {}

  Address(Register r, ByteSize disp) : Address(r, in_bytes(disp)) {}

  Address(address target, const RelocationHolder& rspec) :
    _mode(literal),
    _literal(target, rspec)
  {}

  Address(address target, relocInfo::relocType rtype = relocInfo::external_word_type);

  Address(const Address& a) : _mode(a._mode) { copy_data(a); }

  // Verify the value is trivially destructible regardless of mode, so our
  // destructor can also be trivial, and so our assignment operator doesn't
  // need to destruct the old value before copying over it.
  static_assert(std::is_trivially_destructible<Literal>::value, "must be");
  static_assert(std::is_trivially_destructible<Nonliteral>::value, "must be");

  Address& operator=(const Address& a) {
    _mode = a._mode;
    copy_data(a);
    return *this;
  }

  ~Address() = default;

  const Register base() const {
    assert_is_nonliteral();
    return _nonliteral._base;
  }

  long offset() const {
    assert_is_nonliteral();
    return _nonliteral._offset;
  }

  Register index() const {
    assert_is_nonliteral();
    return _nonliteral._index;
  }

  mode getMode() const {
    return _mode;
  }

  bool uses(Register reg) const {
    return _mode != literal && base() == reg;
  }

  address target() const {
    assert_is_literal();
    return _literal._target;
  }

  const RelocationHolder& rspec() const {
    assert_is_literal();
    return _literal._rspec;
  }
};

// Convenience classes
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

  enum {
    instruction_size = 4,
    compressed_instruction_size = 2,
  };

  // instruction must start at passed address
  static bool is_compressed_instr(address instr) {
    // The RISC-V ISA Manual, Section 'Base Instruction-Length Encoding':
    // Instructions are stored in memory as a sequence of 16-bit little-endian parcels, regardless of
    // memory system endianness. Parcels forming one instruction are stored at increasing halfword
    // addresses, with the lowest-addressed parcel holding the lowest-numbered bits in the instruction
    // specification.
    if (UseRVC && (((uint16_t *)instr)[0] & 0b11) != 0b11) {
      // 16-bit instructions have their lowest two bits equal to 0b00, 0b01, or 0b10
      return true;
    }
    // 32-bit instructions have their lowest two bits set to 0b11
    return false;
  }

  //---<  calculate length of instruction  >---
  // We just use the values set above.
  // instruction must start at passed address
  static unsigned int instr_len(address instr) {
    return is_compressed_instr(instr) ? compressed_instruction_size : instruction_size;
  }

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

  // handle unaligned access
  static inline uint16_t ld_c_instr(address addr) {
    return Bytes::get_native_u2(addr);
  }
  static inline void sd_c_instr(address addr, uint16_t c_instr) {
    Bytes::put_native_u2(addr, c_instr);
  }

  // handle unaligned access
  static inline uint32_t ld_instr(address addr) {
    return Bytes::get_native_u4(addr);
  }
  static inline void sd_instr(address addr, uint32_t instr) {
    Bytes::put_native_u4(addr, instr);
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
    assert_cond(a != nullptr);
    assert_cond(msb >= lsb && msb <= 31);
    unsigned nbits = msb - lsb + 1;
    guarantee(val < (1U << nbits), "Field too big for insn");
    unsigned mask = (1U << nbits) - 1;
    val <<= lsb;
    mask <<= lsb;
    unsigned target = ld_instr(a);
    target &= ~mask;
    target |= val;
    sd_instr(a, target);
  }

  static void patch(address a, unsigned bit, unsigned val) {
    patch(a, bit, bit, val);
  }

  static void patch_reg(address a, unsigned lsb, Register reg) {
    patch(a, lsb + 4, lsb, reg->raw_encoding());
  }

  static void patch_reg(address a, unsigned lsb, FloatRegister reg) {
    patch(a, lsb + 4, lsb, reg->raw_encoding());
  }

  static void patch_reg(address a, unsigned lsb, VectorRegister reg) {
    patch(a, lsb + 4, lsb, reg->raw_encoding());
  }

  void emit(unsigned insn) {
    emit_int32((jint)insn);
  }

  enum csr {
    cycle = 0xc00,
    time,
    instret,
    hpmcounter3,
    hpmcounter4,
    hpmcounter5,
    hpmcounter6,
    hpmcounter7,
    hpmcounter8,
    hpmcounter9,
    hpmcounter10,
    hpmcounter11,
    hpmcounter12,
    hpmcounter13,
    hpmcounter14,
    hpmcounter15,
    hpmcounter16,
    hpmcounter17,
    hpmcounter18,
    hpmcounter19,
    hpmcounter20,
    hpmcounter21,
    hpmcounter22,
    hpmcounter23,
    hpmcounter24,
    hpmcounter25,
    hpmcounter26,
    hpmcounter27,
    hpmcounter28,
    hpmcounter29,
    hpmcounter30,
    hpmcounter31 = 0xc1f
  };

  // Emit an illegal instruction that's known to trap, with 32 read-only CSR
  // to choose as the input operand.
  // According to the RISC-V Assembly Programmer's Manual, a de facto implementation
  // of this instruction is the UNIMP pseduo-instruction, 'CSRRW x0, cycle, x0',
  // attempting to write zero to a read-only CSR 'cycle' (0xC00).
  // RISC-V ISAs provide a set of up to 32 read-only CSR registers 0xC00-0xC1F,
  // and an attempt to write into any read-only CSR (whether it exists or not)
  // will generate an illegal instruction exception.
  void illegal_instruction(csr csr_reg) {
    csrrw(x0, (unsigned)csr_reg, x0);
  }

// Register Instruction
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

  INSN(_add,  0b0110011, 0b000, 0b0000000);
  INSN(_sub,  0b0110011, 0b000, 0b0100000);
  INSN(_andr, 0b0110011, 0b111, 0b0000000);
  INSN(_orr,  0b0110011, 0b110, 0b0000000);
  INSN(_xorr, 0b0110011, 0b100, 0b0000000);
  INSN(sll,   0b0110011, 0b001, 0b0000000);
  INSN(sra,   0b0110011, 0b101, 0b0100000);
  INSN(srl,   0b0110011, 0b101, 0b0000000);
  INSN(slt,   0b0110011, 0b010, 0b0000000);
  INSN(sltu,  0b0110011, 0b011, 0b0000000);
  INSN(_addw, 0b0111011, 0b000, 0b0000000);
  INSN(_subw, 0b0111011, 0b000, 0b0100000);
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

#undef INSN

// Load/store register (all modes)
#define INSN(NAME, op, funct3)                                                                     \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                      \
    guarantee(is_simm12(offset), "offset is invalid.");                                            \
    unsigned insn = 0;                                                                             \
    int32_t val = offset & 0xfff;                                                                  \
    patch((address)&insn, 6, 0, op);                                                               \
    patch((address)&insn, 14, 12, funct3);                                                         \
    patch_reg((address)&insn, 15, Rs);                                                             \
    patch_reg((address)&insn, 7, Rd);                                                              \
    patch((address)&insn, 31, 20, val);                                                            \
    emit(insn);                                                                                    \
  }

  INSN(lb,  0b0000011, 0b000);
  INSN(lbu, 0b0000011, 0b100);
  INSN(lh,  0b0000011, 0b001);
  INSN(lhu, 0b0000011, 0b101);
  INSN(_lw, 0b0000011, 0b010);
  INSN(lwu, 0b0000011, 0b110);
  INSN(_ld, 0b0000011, 0b011);

#undef INSN

#define INSN(NAME, op, funct3)                                                                     \
  void NAME(FloatRegister Rd, Register Rs, const int32_t offset) {                                 \
    guarantee(is_simm12(offset), "offset is invalid.");                                            \
    unsigned insn = 0;                                                                             \
    uint32_t val = offset & 0xfff;                                                                 \
    patch((address)&insn, 6, 0, op);                                                               \
    patch((address)&insn, 14, 12, funct3);                                                         \
    patch_reg((address)&insn, 15, Rs);                                                             \
    patch_reg((address)&insn, 7, Rd);                                                              \
    patch((address)&insn, 31, 20, val);                                                            \
    emit(insn);                                                                                    \
  }

  INSN(flw,  0b0000111, 0b010);
  INSN(_fld, 0b0000111, 0b011);

#undef INSN

#define INSN(NAME, op, funct3)                                                                           \
  void NAME(Register Rs1, Register Rs2, const int64_t offset) {                                          \
    guarantee(is_simm13(offset) && ((offset % 2) == 0), "offset is invalid.");                           \
    unsigned insn = 0;                                                                                   \
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
  }

  INSN(beq,  0b1100011, 0b000);
  INSN(bne,  0b1100011, 0b001);
  INSN(bge,  0b1100011, 0b101);
  INSN(bgeu, 0b1100011, 0b111);
  INSN(blt,  0b1100011, 0b100);
  INSN(bltu, 0b1100011, 0b110);

#undef INSN

#define INSN(NAME, REGISTER, op, funct3)                                                                    \
  void NAME(REGISTER Rs1, Register Rs2, const int32_t offset) {                                             \
    guarantee(is_simm12(offset), "offset is invalid.");                                                     \
    unsigned insn = 0;                                                                                      \
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

  INSN(sb,   Register,      0b0100011, 0b000);
  INSN(sh,   Register,      0b0100011, 0b001);
  INSN(_sw,  Register,      0b0100011, 0b010);
  INSN(_sd,  Register,      0b0100011, 0b011);
  INSN(fsw,  FloatRegister, 0b0100111, 0b010);
  INSN(_fsd, FloatRegister, 0b0100111, 0b011);

#undef INSN

#define INSN(NAME, op, funct3)                                                        \
  void NAME(Register Rd, const uint32_t csr, Register Rs1) {                          \
    guarantee(is_uimm12(csr), "csr is invalid");                                      \
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
    guarantee(is_uimm12(csr), "csr is invalid");                                      \
    guarantee(is_uimm5(uimm), "uimm is invalid");                                     \
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

#define INSN(NAME, op)                                                                \
  void NAME(Register Rd, const int32_t offset) {                                      \
    guarantee(is_simm21(offset) && ((offset % 2) == 0), "offset is invalid.");        \
    unsigned insn = 0;                                                                \
    patch((address)&insn, 6, 0, op);                                                  \
    patch_reg((address)&insn, 7, Rd);                                                 \
    patch((address)&insn, 19, 12, (uint32_t)((offset >> 12) & 0xff));                 \
    patch((address)&insn, 20, (uint32_t)((offset >> 11) & 0x1));                      \
    patch((address)&insn, 30, 21, (uint32_t)((offset >> 1) & 0x3ff));                 \
    patch((address)&insn, 31, (uint32_t)((offset >> 20) & 0x1));                      \
    emit(insn);                                                                       \
  }

  INSN(jal, 0b1101111);

#undef INSN

#define INSN(NAME, op, funct)                                                         \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                         \
    guarantee(is_simm12(offset), "offset is invalid.");                               \
    unsigned insn = 0;                                                                \
    patch((address)&insn, 6, 0, op);                                                  \
    patch_reg((address)&insn, 7, Rd);                                                 \
    patch((address)&insn, 14, 12, funct);                                             \
    patch_reg((address)&insn, 15, Rs);                                                \
    int32_t val = offset & 0xfff;                                                     \
    patch((address)&insn, 31, 20, val);                                               \
    emit(insn);                                                                       \
  }

  INSN(_jalr, 0b1100111, 0b000);

#undef INSN

  enum barrier {
    i = 0b1000, o = 0b0100, r = 0b0010, w = 0b0001,
    ir = i | r, ow = o | w, iorw = i | o | r | w
  };

  void fence(const uint32_t predecessor, const uint32_t successor) {
    unsigned insn = 0;
    guarantee(predecessor < 16, "predecessor is invalid");
    guarantee(successor < 16, "successor is invalid");
    patch((address)&insn, 6, 0, 0b001111);      // opcode
    patch((address)&insn, 11, 7, 0b00000);      // rd
    patch((address)&insn, 14, 12, 0b000);
    patch((address)&insn, 19, 15, 0b00000);     // rs1
    patch((address)&insn, 23, 20, successor);   // succ
    patch((address)&insn, 27, 24, predecessor); // pred
    patch((address)&insn, 31, 28, 0b0000);      // fm
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

  INSN(ecall,   0b1110011, 0b000, 0b000000000000);
  INSN(_ebreak, 0b1110011, 0b000, 0b000000000001);

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

  INSN(fsqrt_s,  0b1010011, 0b00000, 0b0101100);
  INSN(fsqrt_d,  0b1010011, 0b00000, 0b0101101);
  INSN(fcvt_s_d, 0b1010011, 0b00001, 0b0100000);
  INSN(fcvt_d_s, 0b1010011, 0b00000, 0b0100001);
#undef INSN

// Immediate Instruction
#define INSN(NAME, op, funct3)                                                              \
  void NAME(Register Rd, Register Rs1, int32_t imm) {                                       \
    guarantee(is_simm12(imm), "Immediate is out of validity");                              \
    unsigned insn = 0;                                                                      \
    patch((address)&insn, 6, 0, op);                                                        \
    patch((address)&insn, 14, 12, funct3);                                                  \
    patch((address)&insn, 31, 20, imm & 0x00000fff);                                        \
    patch_reg((address)&insn, 7, Rd);                                                       \
    patch_reg((address)&insn, 15, Rs1);                                                     \
    emit(insn);                                                                             \
  }

  INSN(_addi,      0b0010011, 0b000);
  INSN(slti,       0b0010011, 0b010);
  INSN(_addiw,     0b0011011, 0b000);
  INSN(_and_imm12, 0b0010011, 0b111);
  INSN(ori,        0b0010011, 0b110);
  INSN(xori,       0b0010011, 0b100);

#undef INSN

#define INSN(NAME, op, funct3)                                                              \
  void NAME(Register Rd, Register Rs1, uint32_t imm) {                                      \
    guarantee(is_uimm12(imm), "Immediate is out of validity");                              \
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

  INSN(_slli, 0b0010011, 0b001, 0b000000);
  INSN(_srai, 0b0010011, 0b101, 0b010000);
  INSN(_srli, 0b0010011, 0b101, 0b000000);

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

  INSN(_lui,  0b0110111);
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

// ==========================
// RISC-V Vector Extension
// ==========================
enum SEW {
  e8,
  e16,
  e32,
  e64,
  RESERVED,
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

static Assembler::SEW elembytes_to_sew(int ebytes) {
  assert(ebytes > 0 && ebytes <= 8, "unsupported element size");
  return (Assembler::SEW) exact_log2(ebytes);
}

static Assembler::SEW elemtype_to_sew(BasicType etype) {
  return Assembler::elembytes_to_sew(type2aelembytes(etype));
}

#define patch_vtype(hsb, lsb, vlmul, vsew, vta, vma, vill)   \
    if (vill == 1) {                                         \
      guarantee((vlmul | vsew | vta | vma == 0),             \
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
    guarantee(is_uimm5(imm), "imm is invalid");                           \
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

  // Vector Configuration Instruction
  INSN(vsetvl, 0b1010111, 0b111, 0b1000000);

#undef INSN

enum VectorMask {
  v0_t = 0b0,
  unmasked = 0b1
};

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
  INSN(vcpop_m,  0b1010111, 0b010, 0b10000, 0b010000);
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
  INSN(vfcvt_f_x_v,      0b1010111, 0b001, 0b00011, 0b010010);
  INSN(vfcvt_rtz_x_f_v,  0b1010111, 0b001, 0b00111, 0b010010);

  // Vector Widening Floating-Point/Integer Type-Convert Instructions
  INSN(vfwcvt_f_x_v,      0b1010111, 0b001, 0b01011, 0b010010);
  INSN(vfwcvt_f_f_v,      0b1010111, 0b001, 0b01100, 0b010010);
  INSN(vfwcvt_rtz_x_f_v,  0b1010111, 0b001, 0b01111, 0b010010);

  // Vector Narrowing Floating-Point/Integer Type-Convert Instructions
  INSN(vfncvt_f_x_w,      0b1010111, 0b001, 0b10011, 0b010010);
  INSN(vfncvt_f_f_w,      0b1010111, 0b001, 0b10100, 0b010010);
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
    guarantee(is_uimm5(imm), "imm is invalid");                                                    \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);                         \
  }

  // Vector Single-Width Bit Shift Instructions
  INSN(vsra_vi,    0b1010111, 0b011, 0b101001);
  INSN(vsrl_vi,    0b1010111, 0b011, 0b101000);
  INSN(vsll_vi,    0b1010111, 0b011, 0b100101);

  // Vector Slide Instructions
  INSN(vslidedown_vi, 0b1010111, 0b011, 0b001111);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs1, VectorRegister Vs2, VectorMask vm = unmasked) { \
    patch_VArith(op, Vd, funct3, Vs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Single-Width Floating-Point Fused Multiply-Add Instructions
  INSN(vfnmsub_vv, 0b1010111, 0b001, 0b101011);
  INSN(vfmsub_vv,  0b1010111, 0b001, 0b101010);
  INSN(vfnmadd_vv, 0b1010111, 0b001, 0b101001);
  INSN(vfmadd_vv,  0b1010111, 0b001, 0b101000);
  INSN(vfnmsac_vv, 0b1010111, 0b001, 0b101111);
  INSN(vfmsac_vv,  0b1010111, 0b001, 0b101110);
  INSN(vfmacc_vv,  0b1010111, 0b001, 0b101100);
  INSN(vfnmacc_vv, 0b1010111, 0b001, 0b101101);

  // Vector Single-Width Integer Multiply-Add Instructions
  INSN(vnmsub_vv, 0b1010111, 0b010, 0b101011);
  INSN(vmadd_vv,  0b1010111, 0b010, 0b101001);
  INSN(vnmsac_vv, 0b1010111, 0b010, 0b101111);
  INSN(vmacc_vv,  0b1010111, 0b010, 0b101101);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, Register Rs1, VectorRegister Vs2, VectorMask vm = unmasked) {       \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Single-Width Integer Multiply-Add Instructions
  INSN(vnmsub_vx, 0b1010111, 0b110, 0b101011);
  INSN(vmadd_vx,  0b1010111, 0b110, 0b101001);
  INSN(vnmsac_vx, 0b1010111, 0b110, 0b101111);
  INSN(vmacc_vx,  0b1010111, 0b110, 0b101101);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, FloatRegister Rs1, VectorRegister Vs2, VectorMask vm = unmasked) {  \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

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
    patch_VArith(op, Vd, funct3, Vs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Single-Width Floating-Point Reduction Instructions
  INSN(vfredusum_vs,  0b1010111, 0b001, 0b000001);
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

  // Vector Floating-Point Compare Instructions
  INSN(vmfle_vv, 0b1010111, 0b001, 0b011001);
  INSN(vmflt_vv, 0b1010111, 0b001, 0b011011);
  INSN(vmfne_vv, 0b1010111, 0b001, 0b011100);
  INSN(vmfeq_vv, 0b1010111, 0b001, 0b011000);

  // Vector Floating-Point Sign-Injection Instructions
  INSN(vfsgnjx_vv, 0b1010111, 0b001, 0b001010);
  INSN(vfsgnjn_vv, 0b1010111, 0b001, 0b001001);

  // Vector Floating-Point MIN/MAX Instructions
  INSN(vfmax_vv,   0b1010111, 0b001, 0b000110);
  INSN(vfmin_vv,   0b1010111, 0b001, 0b000100);

  // Vector Single-Width Floating-Point Multiply/Divide Instructions
  INSN(vfdiv_vv,   0b1010111, 0b001, 0b100000);
  INSN(vfmul_vv,   0b1010111, 0b001, 0b100100);

  // Vector Single-Width Floating-Point Add/Subtract Instructions
  INSN(vfsub_vv, 0b1010111, 0b001, 0b000010);
  INSN(vfadd_vv, 0b1010111, 0b001, 0b000000);

  // Vector Single-Width Fractional Multiply with Rounding and Saturation
  INSN(vsmul_vv, 0b1010111, 0b000, 0b100111);

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

  // Vector Single-Width Bit Shift Instructions
  INSN(vsra_vv, 0b1010111, 0b000, 0b101001);
  INSN(vsrl_vv, 0b1010111, 0b000, 0b101000);
  INSN(vsll_vv, 0b1010111, 0b000, 0b100101);

  // Vector Bitwise Logical Instructions
  INSN(vxor_vv, 0b1010111, 0b000, 0b001011);
  INSN(vor_vv,  0b1010111, 0b000, 0b001010);
  INSN(vand_vv, 0b1010111, 0b000, 0b001001);

  // Vector Single-Width Integer Add and Subtract
  INSN(vsub_vv, 0b1010111, 0b000, 0b000010);
  INSN(vadd_vv, 0b1010111, 0b000, 0b000000);

  // Vector Register Gather Instructions
  INSN(vrgather_vv,     0b1010111, 0b000, 0b001100);

#undef INSN


#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, Register Rs1, VectorMask vm = unmasked) {       \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

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

  // Vector Single-Width Integer Add and Subtract
  INSN(vsub_vx,  0b1010111, 0b100, 0b000010);
  INSN(vadd_vx,  0b1010111, 0b100, 0b000000);
  INSN(vrsub_vx, 0b1010111, 0b100, 0b000011);

  // Vector Slide Instructions
  INSN(vslidedown_vx, 0b1010111, 0b100, 0b001111);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                         \
  void NAME(VectorRegister Vd, VectorRegister Vs2, Register Rs1) {                                 \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Integer Merge Instructions
  INSN(vmerge_vxm,  0b1010111, 0b100, 0b0, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                                         \
  void NAME(VectorRegister Vd, VectorRegister Vs2, FloatRegister Rs1) {                            \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Floating-Point Merge Instruction
  INSN(vfmerge_vfm,  0b1010111, 0b101, 0b0, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, FloatRegister Rs1, VectorMask vm = unmasked) {  \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);                            \
  }

  // Vector Floating-Point Compare Instructions
  INSN(vmfge_vf, 0b1010111, 0b101, 0b011111);
  INSN(vmfgt_vf, 0b1010111, 0b101, 0b011101);
  INSN(vmfle_vf, 0b1010111, 0b101, 0b011001);
  INSN(vmflt_vf, 0b1010111, 0b101, 0b011011);
  INSN(vmfne_vf, 0b1010111, 0b101, 0b011100);
  INSN(vmfeq_vf, 0b1010111, 0b101, 0b011000);

  // Vector Floating-Point MIN/MAX Instructions
  INSN(vfmax_vf, 0b1010111, 0b101, 0b000110);
  INSN(vfmin_vf, 0b1010111, 0b101, 0b000100);

  // Vector Single-Width Floating-Point Multiply/Divide Instructions
  INSN(vfdiv_vf,  0b1010111, 0b101, 0b100000);
  INSN(vfmul_vf,  0b1010111, 0b101, 0b100100);
  INSN(vfrdiv_vf, 0b1010111, 0b101, 0b100001);

  // Vector Single-Width Floating-Point Add/Subtract Instructions
  INSN(vfsub_vf,  0b1010111, 0b101, 0b000010);
  INSN(vfadd_vf,  0b1010111, 0b101, 0b000000);
  INSN(vfrsub_vf, 0b1010111, 0b101, 0b100111);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                                                             \
  void NAME(VectorRegister Vd, VectorRegister Vs2, int32_t imm, VectorMask vm = unmasked) {        \
    guarantee(is_simm5(imm), "imm is invalid");                                                    \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);                         \
  }

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
  INSN(vrsub_vi,  0b1010111, 0b011, 0b000011);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                    \
  void NAME(VectorRegister Vd, VectorRegister Vs2, int32_t imm) {             \
    guarantee(is_simm5(imm), "imm is invalid");                               \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6);    \
  }

  // Vector Integer Merge Instructions
  INSN(vmerge_vim,  0b1010111, 0b011, 0b0, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, vm, funct6)                                   \
  void NAME(VectorRegister Vd, VectorRegister Vs2, VectorRegister Vs1) {     \
    patch_VArith(op, Vd, funct3, Vs1->raw_encoding(), Vs2, vm, funct6);      \
  }

  // Vector Compress Instruction
  INSN(vcompress_vm, 0b1010111, 0b010, 0b1, 0b010111);

  // Vector Mask-Register Logical Instructions
  INSN(vmxnor_mm,   0b1010111, 0b010, 0b1, 0b011111);
  INSN(vmorn_mm,    0b1010111, 0b010, 0b1, 0b011100);
  INSN(vmnor_mm,    0b1010111, 0b010, 0b1, 0b011110);
  INSN(vmor_mm,     0b1010111, 0b010, 0b1, 0b011010);
  INSN(vmxor_mm,    0b1010111, 0b010, 0b1, 0b011011);
  INSN(vmandn_mm,   0b1010111, 0b010, 0b1, 0b011000);
  INSN(vmnand_mm,   0b1010111, 0b010, 0b1, 0b011101);
  INSN(vmand_mm,    0b1010111, 0b010, 0b1, 0b011001);

  // Vector Integer Merge Instructions
  INSN(vmerge_vvm,  0b1010111, 0b000, 0b0, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                            \
  void NAME(VectorRegister Vd, int32_t imm) {                              \
    guarantee(is_simm5(imm), "imm is invalid");                            \
    patch_VArith(op, Vd, funct3, (uint32_t)(imm & 0x1f), Vs2, vm, funct6); \
  }

  // Vector Integer Move Instructions
  INSN(vmv_v_i, 0b1010111, 0b011, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
  void NAME(VectorRegister Vd, FloatRegister Rs1) {                         \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);     \
  }

  // Floating-Point Scalar Move Instructions
  INSN(vfmv_s_f, 0b1010111, 0b101, v0, 0b1, 0b010000);
  // Vector Floating-Point Move Instruction
  INSN(vfmv_v_f, 0b1010111, 0b101, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
  void NAME(VectorRegister Vd, VectorRegister Vs1) {                        \
    patch_VArith(op, Vd, funct3, Vs1->raw_encoding(), Vs2, vm, funct6);     \
  }

  // Vector Integer Move Instructions
  INSN(vmv_v_v, 0b1010111, 0b000, v0, 0b1, 0b010111);

#undef INSN

#define INSN(NAME, op, funct3, Vs2, vm, funct6)                             \
   void NAME(VectorRegister Vd, Register Rs1) {                             \
    patch_VArith(op, Vd, funct3, Rs1->raw_encoding(), Vs2, vm, funct6);     \
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
    guarantee(is_uimm3(width), "width is invalid");                                  \
    patch_VLdSt(op, Vd, width, Rs1, lumop, vm, mop, mew, nf);                        \
  }

  // Vector Load/Store Instructions
  INSN(vl1re8_v, 0b0000111, 0b01000, 0b1, 0b00, g1);

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
  INSN(vlm_v, 0b0000111, 0b000, 0b01011, 0b00, 0b0);
  INSN(vsm_v, 0b0100111, 0b000, 0b01011, 0b00, 0b0);

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
    patch_VLdSt(op, Vd, width, Rs1, Vs2->raw_encoding(), vm, mop, mew, nf);                              \
  }

  // Vector unordered indexed load instructions
  INSN(vluxei32_v, 0b0000111, 0b110, 0b01, 0b0);

  // Vector unordered indexed store instructions
  INSN(vsuxei32_v, 0b0100111, 0b110, 0b01, 0b0);

#undef INSN

#define INSN(NAME, op, width, mop, mew)                                                                  \
  void NAME(VectorRegister Vd, Register Rs1, Register Rs2, VectorMask vm = unmasked, Nf nf = g1) {       \
    patch_VLdSt(op, Vd, width, Rs1, Rs2->raw_encoding(), vm, mop, mew, nf);                              \
  }

  // Vector Strided Instructions
  INSN(vlse8_v,  0b0000111, 0b000, 0b10, 0b0);
  INSN(vlse16_v, 0b0000111, 0b101, 0b10, 0b0);
  INSN(vlse32_v, 0b0000111, 0b110, 0b10, 0b0);
  INSN(vlse64_v, 0b0000111, 0b111, 0b10, 0b0);

  INSN(vsse8_v,  0b0100111, 0b000, 0b10, 0b0);
  INSN(vsse16_v, 0b0100111, 0b101, 0b10, 0b0);
  INSN(vsse32_v, 0b0100111, 0b110, 0b10, 0b0);
  INSN(vsse64_v, 0b0100111, 0b111, 0b10, 0b0);

#undef INSN
#undef patch_VLdSt

// ====================================
// RISC-V Bit-Manipulation Extension
// Currently only support Zba, Zbb and Zbs bitmanip extensions.
// ====================================
#define INSN(NAME, op, funct3, funct7)                  \
  void NAME(Register Rd, Register Rs1, Register Rs2) {  \
    unsigned insn = 0;                                  \
    patch((address)&insn, 6,  0, op);                   \
    patch((address)&insn, 14, 12, funct3);              \
    patch((address)&insn, 31, 25, funct7);              \
    patch_reg((address)&insn, 7, Rd);                   \
    patch_reg((address)&insn, 15, Rs1);                 \
    patch_reg((address)&insn, 20, Rs2);                 \
    emit(insn);                                         \
  }

  INSN(add_uw,    0b0111011, 0b000, 0b0000100);
  INSN(rol,       0b0110011, 0b001, 0b0110000);
  INSN(rolw,      0b0111011, 0b001, 0b0110000);
  INSN(ror,       0b0110011, 0b101, 0b0110000);
  INSN(rorw,      0b0111011, 0b101, 0b0110000);
  INSN(sh1add,    0b0110011, 0b010, 0b0010000);
  INSN(sh2add,    0b0110011, 0b100, 0b0010000);
  INSN(sh3add,    0b0110011, 0b110, 0b0010000);
  INSN(sh1add_uw, 0b0111011, 0b010, 0b0010000);
  INSN(sh2add_uw, 0b0111011, 0b100, 0b0010000);
  INSN(sh3add_uw, 0b0111011, 0b110, 0b0010000);
  INSN(andn,      0b0110011, 0b111, 0b0100000);
  INSN(orn,       0b0110011, 0b110, 0b0100000);
  INSN(xnor,      0b0110011, 0b100, 0b0100000);
  INSN(max,       0b0110011, 0b110, 0b0000101);
  INSN(maxu,      0b0110011, 0b111, 0b0000101);
  INSN(min,       0b0110011, 0b100, 0b0000101);
  INSN(minu,      0b0110011, 0b101, 0b0000101);

#undef INSN

#define INSN(NAME, op, funct3, funct12)                 \
  void NAME(Register Rd, Register Rs1) {                \
    unsigned insn = 0;                                  \
    patch((address)&insn, 6, 0, op);                    \
    patch((address)&insn, 14, 12, funct3);              \
    patch((address)&insn, 31, 20, funct12);             \
    patch_reg((address)&insn, 7, Rd);                   \
    patch_reg((address)&insn, 15, Rs1);                 \
    emit(insn);                                         \
  }

  INSN(rev8,   0b0010011, 0b101, 0b011010111000);
  INSN(sext_b, 0b0010011, 0b001, 0b011000000100);
  INSN(sext_h, 0b0010011, 0b001, 0b011000000101);
  INSN(zext_h, 0b0111011, 0b100, 0b000010000000);
  INSN(clz,    0b0010011, 0b001, 0b011000000000);
  INSN(clzw,   0b0011011, 0b001, 0b011000000000);
  INSN(ctz,    0b0010011, 0b001, 0b011000000001);
  INSN(ctzw,   0b0011011, 0b001, 0b011000000001);
  INSN(cpop,   0b0010011, 0b001, 0b011000000010);
  INSN(cpopw,  0b0011011, 0b001, 0b011000000010);
  INSN(orc_b,  0b0010011, 0b101, 0b001010000111);

#undef INSN

#define INSN(NAME, op, funct3, funct6)                  \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {\
    guarantee(shamt <= 0x3f, "Shamt is invalid");       \
    unsigned insn = 0;                                  \
    patch((address)&insn, 6, 0, op);                    \
    patch((address)&insn, 14, 12, funct3);              \
    patch((address)&insn, 25, 20, shamt);               \
    patch((address)&insn, 31, 26, funct6);              \
    patch_reg((address)&insn, 7, Rd);                   \
    patch_reg((address)&insn, 15, Rs1);                 \
    emit(insn);                                         \
  }

  INSN(rori,    0b0010011, 0b101, 0b011000);
  INSN(slli_uw, 0b0011011, 0b001, 0b000010);
  INSN(bexti,   0b0010011, 0b101, 0b010010);

#undef INSN

#define INSN(NAME, op, funct3, funct7)                  \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {\
    guarantee(shamt <= 0x1f, "Shamt is invalid");       \
    unsigned insn = 0;                                  \
    patch((address)&insn, 6, 0, op);                    \
    patch((address)&insn, 14, 12, funct3);              \
    patch((address)&insn, 24, 20, shamt);               \
    patch((address)&insn, 31, 25, funct7);              \
    patch_reg((address)&insn, 7, Rd);                   \
    patch_reg((address)&insn, 15, Rs1);                 \
    emit(insn);                                         \
  }

  INSN(roriw, 0b0011011, 0b101, 0b0110000);

#undef INSN

// ========================================
// RISC-V Compressed Instructions Extension
// ========================================
// Note:
// 1. Assembler functions encoding 16-bit compressed instructions always begin with a 'c_'
//    prefix, such as 'c_add'. Correspondingly, assembler functions encoding normal 32-bit
//    instructions with begin with a '_' prefix, such as "_add". Most of time users have no
//    need to explicitly emit these compressed instructions. Instead, they still use unified
//    wrappers such as 'add' which do the compressing work through 'c_add' depending on the
//    the operands of the instruction and availability of the RVC hardware extension.
//
// 2. 'CompressibleRegion' and 'IncompressibleRegion' are introduced to mark assembler scopes
//     within which instructions are qualified or unqualified to be compressed into their 16-bit
//     versions. An example:
//
//      CompressibleRegion cr(_masm);
//      __ add(...);       // this instruction will be compressed into 'c.add' when possible
//      {
//         IncompressibleRegion ir(_masm);
//         __ add(...);    // this instruction will not be compressed
//         {
//            CompressibleRegion cr(_masm);
//            __ add(...); // this instruction will be compressed into 'c.add' when possible
//         }
//      }
//
// 3. When printing JIT assembly code, using -XX:PrintAssemblyOptions=no-aliases could help
//    distinguish compressed 16-bit instructions from normal 32-bit ones.

private:
  bool _in_compressible_region;
public:
  bool in_compressible_region() const { return _in_compressible_region; }
  void set_in_compressible_region(bool b) { _in_compressible_region = b; }
public:

  // an abstract compressible region
  class AbstractCompressibleRegion : public StackObj {
  protected:
    Assembler *_masm;
    bool _saved_in_compressible_region;
  protected:
    AbstractCompressibleRegion(Assembler *_masm)
    : _masm(_masm)
    , _saved_in_compressible_region(_masm->in_compressible_region()) {}
  };
  // a compressible region
  class CompressibleRegion : public AbstractCompressibleRegion {
  public:
    CompressibleRegion(Assembler *_masm) : AbstractCompressibleRegion(_masm) {
      _masm->set_in_compressible_region(true);
    }
    ~CompressibleRegion() {
      _masm->set_in_compressible_region(_saved_in_compressible_region);
    }
  };
  // an incompressible region
  class IncompressibleRegion : public AbstractCompressibleRegion {
  public:
    IncompressibleRegion(Assembler *_masm) : AbstractCompressibleRegion(_masm) {
      _masm->set_in_compressible_region(false);
    }
    ~IncompressibleRegion() {
      _masm->set_in_compressible_region(_saved_in_compressible_region);
    }
  };

public:
  // Emit a relocation.
  void relocate(RelocationHolder const& rspec, int format = 0) {
    AbstractAssembler::relocate(rspec, format);
  }
  void relocate(relocInfo::relocType rtype, int format = 0) {
    AbstractAssembler::relocate(rtype, format);
  }
  template <typename Callback>
  void relocate(RelocationHolder const& rspec, Callback emit_insts, int format = 0) {
    AbstractAssembler::relocate(rspec, format);
    IncompressibleRegion ir(this);  // relocations
    emit_insts();
  }
  template <typename Callback>
  void relocate(relocInfo::relocType rtype, Callback emit_insts, int format = 0) {
    AbstractAssembler::relocate(rtype, format);
    IncompressibleRegion ir(this);  // relocations
    emit_insts();
  }

  // patch a 16-bit instruction.
  static void c_patch(address a, unsigned msb, unsigned lsb, uint16_t val) {
    assert_cond(a != nullptr);
    assert_cond(msb >= lsb && msb <= 15);
    unsigned nbits = msb - lsb + 1;
    guarantee(val < (1U << nbits), "Field too big for insn");
    uint16_t mask = (1U << nbits) - 1;
    val <<= lsb;
    mask <<= lsb;
    uint16_t target = ld_c_instr(a);
    target &= ~mask;
    target |= val;
    sd_c_instr(a, target);
  }

  static void c_patch(address a, unsigned bit, uint16_t val) {
    c_patch(a, bit, bit, val);
  }

  // patch a 16-bit instruction with a general purpose register ranging [0, 31] (5 bits)
  static void c_patch_reg(address a, unsigned lsb, Register reg) {
    c_patch(a, lsb + 4, lsb, reg->raw_encoding());
  }

  // patch a 16-bit instruction with a general purpose register ranging [8, 15] (3 bits)
  static void c_patch_compressed_reg(address a, unsigned lsb, Register reg) {
    c_patch(a, lsb + 2, lsb, reg->compressed_raw_encoding());
  }

  // patch a 16-bit instruction with a float register ranging [0, 31] (5 bits)
  static void c_patch_reg(address a, unsigned lsb, FloatRegister reg) {
    c_patch(a, lsb + 4, lsb, reg->raw_encoding());
  }

  // patch a 16-bit instruction with a float register ranging [8, 15] (3 bits)
  static void c_patch_compressed_reg(address a, unsigned lsb, FloatRegister reg) {
    c_patch(a, lsb + 2, lsb, reg->compressed_raw_encoding());
  }

// --------------  RVC Instruction Definitions  --------------

  void c_nop() {
    c_addi(x0, 0);
  }

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd_Rs1, int32_t imm) {                                                  \
    assert_cond(is_simm6(imm));                                                              \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (imm & right_n_bits(5)));                                  \
    c_patch_reg((address)&insn, 7, Rd_Rs1);                                                  \
    c_patch((address)&insn, 12, 12, (imm & nth_bit(5)) >> 5);                                \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_addi,   0b000, 0b01);
  INSN(c_addiw,  0b001, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(int32_t imm) {                                                                   \
    assert_cond(is_simm10(imm));                                                             \
    assert_cond((imm & 0b1111) == 0);                                                        \
    assert_cond(imm != 0);                                                                   \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 2, 2, (imm & nth_bit(5)) >> 5);                                  \
    c_patch((address)&insn, 4, 3, (imm & right_n_bits(9)) >> 7);                             \
    c_patch((address)&insn, 5, 5, (imm & nth_bit(6)) >> 6);                                  \
    c_patch((address)&insn, 6, 6, (imm & nth_bit(4)) >> 4);                                  \
    c_patch_reg((address)&insn, 7, sp);                                                      \
    c_patch((address)&insn, 12, 12, (imm & nth_bit(9)) >> 9);                                \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_addi16sp, 0b011, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd, uint32_t uimm) {                                                    \
    assert_cond(is_uimm10(uimm));                                                            \
    assert_cond((uimm & 0b11) == 0);                                                         \
    assert_cond(uimm != 0);                                                                  \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_compressed_reg((address)&insn, 2, Rd);                                           \
    c_patch((address)&insn, 5, 5, (uimm & nth_bit(3)) >> 3);                                 \
    c_patch((address)&insn, 6, 6, (uimm & nth_bit(2)) >> 2);                                 \
    c_patch((address)&insn, 10, 7, (uimm & right_n_bits(10)) >> 6);                          \
    c_patch((address)&insn, 12, 11, (uimm & right_n_bits(6)) >> 4);                          \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_addi4spn, 0b000, 0b00);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd_Rs1, uint32_t shamt) {                                               \
    assert_cond(is_uimm6(shamt));                                                            \
    assert_cond(shamt != 0);                                                                 \
    assert_cond(Rd_Rs1 != x0);                                                               \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (shamt & right_n_bits(5)));                                \
    c_patch_reg((address)&insn, 7, Rd_Rs1);                                                  \
    c_patch((address)&insn, 12, 12, (shamt & nth_bit(5)) >> 5);                              \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_slli, 0b000, 0b10);

#undef INSN

#define INSN(NAME, funct3, funct2, op)                                                       \
  void NAME(Register Rd_Rs1, uint32_t shamt) {                                               \
    assert_cond(is_uimm6(shamt));                                                            \
    assert_cond(shamt != 0);                                                                 \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (shamt & right_n_bits(5)));                                \
    c_patch_compressed_reg((address)&insn, 7, Rd_Rs1);                                       \
    c_patch((address)&insn, 11, 10, funct2);                                                 \
    c_patch((address)&insn, 12, 12, (shamt & nth_bit(5)) >> 5);                              \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_srli, 0b100, 0b00, 0b01);
  INSN(c_srai, 0b100, 0b01, 0b01);

#undef INSN

#define INSN(NAME, funct3, funct2, op)                                                       \
  void NAME(Register Rd_Rs1, int32_t imm) {                                                  \
    assert_cond(is_simm6(imm));                                                              \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (imm & right_n_bits(5)));                                  \
    c_patch_compressed_reg((address)&insn, 7, Rd_Rs1);                                       \
    c_patch((address)&insn, 11, 10, funct2);                                                 \
    c_patch((address)&insn, 12, 12, (imm & nth_bit(5)) >> 5);                                \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_andi, 0b100, 0b10, 0b01);

#undef INSN

#define INSN(NAME, funct6, funct2, op)                                                       \
  void NAME(Register Rd_Rs1, Register Rs2) {                                                 \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_compressed_reg((address)&insn, 2, Rs2);                                          \
    c_patch((address)&insn, 6, 5, funct2);                                                   \
    c_patch_compressed_reg((address)&insn, 7, Rd_Rs1);                                       \
    c_patch((address)&insn, 15, 10, funct6);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_sub,  0b100011, 0b00, 0b01);
  INSN(c_xor,  0b100011, 0b01, 0b01);
  INSN(c_or,   0b100011, 0b10, 0b01);
  INSN(c_and,  0b100011, 0b11, 0b01);
  INSN(c_subw, 0b100111, 0b00, 0b01);
  INSN(c_addw, 0b100111, 0b01, 0b01);

#undef INSN

#define INSN(NAME, funct4, op)                                                               \
  void NAME(Register Rd_Rs1, Register Rs2) {                                                 \
    assert_cond(Rd_Rs1 != x0);                                                               \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_reg((address)&insn, 2, Rs2);                                                     \
    c_patch_reg((address)&insn, 7, Rd_Rs1);                                                  \
    c_patch((address)&insn, 15, 12, funct4);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_mv,  0b1000, 0b10);
  INSN(c_add, 0b1001, 0b10);

#undef INSN

#define INSN(NAME, funct4, op)                                                               \
  void NAME(Register Rs1) {                                                                  \
    assert_cond(Rs1 != x0);                                                                  \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_reg((address)&insn, 2, x0);                                                      \
    c_patch_reg((address)&insn, 7, Rs1);                                                     \
    c_patch((address)&insn, 15, 12, funct4);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_jr,   0b1000, 0b10);
  INSN(c_jalr, 0b1001, 0b10);

#undef INSN

  typedef void (Assembler::* j_c_insn)(address dest);
  typedef void (Assembler::* compare_and_branch_c_insn)(Register Rs1, address dest);

  void wrap_label(Label &L, j_c_insn insn) {
    if (L.is_bound()) {
      (this->*insn)(target(L));
    } else {
      L.add_patch_at(code(), locator());
      (this->*insn)(pc());
    }
  }

  void wrap_label(Label &L, Register r, compare_and_branch_c_insn insn) {
    if (L.is_bound()) {
      (this->*insn)(r, target(L));
    } else {
      L.add_patch_at(code(), locator());
      (this->*insn)(r, pc());
    }
  }

#define INSN(NAME, funct3, op)                                                               \
  void NAME(int32_t offset) {                                                                \
    assert(is_simm12(offset) && ((offset % 2) == 0), "invalid encoding");                    \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 2, 2, (offset & nth_bit(5)) >> 5);                               \
    c_patch((address)&insn, 5, 3, (offset & right_n_bits(4)) >> 1);                          \
    c_patch((address)&insn, 6, 6, (offset & nth_bit(7)) >> 7);                               \
    c_patch((address)&insn, 7, 7, (offset & nth_bit(6)) >> 6);                               \
    c_patch((address)&insn, 8, 8, (offset & nth_bit(10)) >> 10);                             \
    c_patch((address)&insn, 10, 9, (offset & right_n_bits(10)) >> 8);                        \
    c_patch((address)&insn, 11, 11, (offset & nth_bit(4)) >> 4);                             \
    c_patch((address)&insn, 12, 12, (offset & nth_bit(11)) >> 11);                           \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }                                                                                          \
  void NAME(address dest) {                                                                  \
    assert_cond(dest != nullptr);                                                            \
    int64_t distance = dest - pc();                                                          \
    assert(is_simm12(distance) && ((distance % 2) == 0), "invalid encoding");                \
    c_j(distance);                                                                           \
  }                                                                                          \
  void NAME(Label &L) {                                                                      \
    wrap_label(L, &Assembler::NAME);                                                         \
  }

  INSN(c_j, 0b101, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rs1, int32_t imm) {                                                     \
    assert(is_simm9(imm) && ((imm % 2) == 0), "invalid encoding");                           \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 2, 2, (imm & nth_bit(5)) >> 5);                                  \
    c_patch((address)&insn, 4, 3, (imm & right_n_bits(3)) >> 1);                             \
    c_patch((address)&insn, 6, 5, (imm & right_n_bits(8)) >> 6);                             \
    c_patch_compressed_reg((address)&insn, 7, Rs1);                                          \
    c_patch((address)&insn, 11, 10, (imm & right_n_bits(5)) >> 3);                           \
    c_patch((address)&insn, 12, 12, (imm & nth_bit(8)) >> 8);                                \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }                                                                                          \
  void NAME(Register Rs1, address dest) {                                                    \
    assert_cond(dest != nullptr);                                                            \
    int64_t distance = dest - pc();                                                          \
    assert(is_simm9(distance) && ((distance % 2) == 0), "invalid encoding");                 \
    NAME(Rs1, distance);                                                                     \
  }                                                                                          \
  void NAME(Register Rs1, Label &L) {                                                        \
    wrap_label(L, Rs1, &Assembler::NAME);                                                    \
  }

  INSN(c_beqz, 0b110, 0b01);
  INSN(c_bnez, 0b111, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd, int32_t imm) {                                                      \
    assert_cond(is_simm18(imm));                                                             \
    assert_cond((imm & 0xfff) == 0);                                                         \
    assert_cond(imm != 0);                                                                   \
    assert_cond(Rd != x0 && Rd != x2);                                                       \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (imm & right_n_bits(17)) >> 12);                           \
    c_patch_reg((address)&insn, 7, Rd);                                                      \
    c_patch((address)&insn, 12, 12, (imm & nth_bit(17)) >> 17);                              \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_lui, 0b011, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd, int32_t imm) {                                                      \
    assert_cond(is_simm6(imm));                                                              \
    assert_cond(Rd != x0);                                                                   \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 6, 2, (imm & right_n_bits(5)));                                  \
    c_patch_reg((address)&insn, 7, Rd);                                                      \
    c_patch((address)&insn, 12, 12, (imm & right_n_bits(6)) >> 5);                           \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_li, 0b010, 0b01);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd, uint32_t uimm) {                                                    \
    assert_cond(is_uimm9(uimm));                                                             \
    assert_cond((uimm & 0b111) == 0);                                                        \
    assert_cond(Rd != x0);                                                                   \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 4, 2, (uimm & right_n_bits(9)) >> 6);                            \
    c_patch((address)&insn, 6, 5, (uimm & right_n_bits(5)) >> 3);                            \
    c_patch_reg((address)&insn, 7, Rd);                                                      \
    c_patch((address)&insn, 12, 12, (uimm & nth_bit(5)) >> 5);                               \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_ldsp,  0b011, 0b10);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(FloatRegister Rd, uint32_t uimm) {                                               \
    assert_cond(is_uimm9(uimm));                                                             \
    assert_cond((uimm & 0b111) == 0);                                                        \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 4, 2, (uimm & right_n_bits(9)) >> 6);                            \
    c_patch((address)&insn, 6, 5, (uimm & right_n_bits(5)) >> 3);                            \
    c_patch_reg((address)&insn, 7, Rd);                                                      \
    c_patch((address)&insn, 12, 12, (uimm & nth_bit(5)) >> 5);                               \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_fldsp, 0b001, 0b10);

#undef INSN

#define INSN(NAME, funct3, op, REGISTER_TYPE)                                                \
  void NAME(REGISTER_TYPE Rd_Rs2, Register Rs1, uint32_t uimm) {                             \
    assert_cond(is_uimm8(uimm));                                                             \
    assert_cond((uimm & 0b111) == 0);                                                        \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_compressed_reg((address)&insn, 2, Rd_Rs2);                                       \
    c_patch((address)&insn, 6, 5, (uimm & right_n_bits(8)) >> 6);                            \
    c_patch_compressed_reg((address)&insn, 7, Rs1);                                          \
    c_patch((address)&insn, 12, 10, (uimm & right_n_bits(6)) >> 3);                          \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_ld,  0b011, 0b00, Register);
  INSN(c_sd,  0b111, 0b00, Register);
  INSN(c_fld, 0b001, 0b00, FloatRegister);
  INSN(c_fsd, 0b101, 0b00, FloatRegister);

#undef INSN

#define INSN(NAME, funct3, op, REGISTER_TYPE)                                                \
  void NAME(REGISTER_TYPE Rs2, uint32_t uimm) {                                              \
    assert_cond(is_uimm9(uimm));                                                             \
    assert_cond((uimm & 0b111) == 0);                                                        \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_reg((address)&insn, 2, Rs2);                                                     \
    c_patch((address)&insn, 9, 7, (uimm & right_n_bits(9)) >> 6);                            \
    c_patch((address)&insn, 12, 10, (uimm & right_n_bits(6)) >> 3);                          \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_sdsp,  0b111, 0b10, Register);
  INSN(c_fsdsp, 0b101, 0b10, FloatRegister);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rs2, uint32_t uimm) {                                                   \
    assert_cond(is_uimm8(uimm));                                                             \
    assert_cond((uimm & 0b11) == 0);                                                         \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_reg((address)&insn, 2, Rs2);                                                     \
    c_patch((address)&insn, 8, 7, (uimm & right_n_bits(8)) >> 6);                            \
    c_patch((address)&insn, 12, 9, (uimm & right_n_bits(6)) >> 2);                           \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_swsp, 0b110, 0b10);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd, uint32_t uimm) {                                                    \
    assert_cond(is_uimm8(uimm));                                                             \
    assert_cond((uimm & 0b11) == 0);                                                         \
    assert_cond(Rd != x0);                                                                   \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 3, 2, (uimm & right_n_bits(8)) >> 6);                            \
    c_patch((address)&insn, 6, 4, (uimm & right_n_bits(5)) >> 2);                            \
    c_patch_reg((address)&insn, 7, Rd);                                                      \
    c_patch((address)&insn, 12, 12, (uimm & nth_bit(5)) >> 5);                               \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_lwsp, 0b010, 0b10);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME(Register Rd_Rs2, Register Rs1, uint32_t uimm) {                                  \
    assert_cond(is_uimm7(uimm));                                                             \
    assert_cond((uimm & 0b11) == 0);                                                         \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch_compressed_reg((address)&insn, 2, Rd_Rs2);                                       \
    c_patch((address)&insn, 5, 5, (uimm & nth_bit(6)) >> 6);                                 \
    c_patch((address)&insn, 6, 6, (uimm & nth_bit(2)) >> 2);                                 \
    c_patch_compressed_reg((address)&insn, 7, Rs1);                                          \
    c_patch((address)&insn, 12, 10, (uimm & right_n_bits(6)) >> 3);                          \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_lw, 0b010, 0b00);
  INSN(c_sw, 0b110, 0b00);

#undef INSN

#define INSN(NAME, funct3, op)                                                               \
  void NAME() {                                                                              \
    uint16_t insn = 0;                                                                       \
    c_patch((address)&insn, 1, 0, op);                                                       \
    c_patch((address)&insn, 11, 2, 0x0);                                                     \
    c_patch((address)&insn, 12, 12, 0b1);                                                    \
    c_patch((address)&insn, 15, 13, funct3);                                                 \
    emit_int16(insn);                                                                        \
  }

  INSN(c_ebreak, 0b100, 0b10);

#undef INSN

// --------------  RVC Transformation Functions  --------------

// --------------------------
// Register instructions
// --------------------------
#define INSN(NAME)                                                                             \
  void NAME(Register Rd, Register Rs1, Register Rs2) {                                         \
    /* add -> c.add */                                                                         \
    if (do_compress()) {                                                                       \
      Register src = noreg;                                                                    \
      if (Rs1 != x0 && Rs2 != x0 && ((src = Rs1, Rs2 == Rd) || (src = Rs2, Rs1 == Rd))) {      \
        c_add(Rd, src);                                                                        \
        return;                                                                                \
      }                                                                                        \
    }                                                                                          \
    _add(Rd, Rs1, Rs2);                                                                        \
  }

  INSN(add);

#undef INSN

// --------------------------
#define INSN(NAME, C_NAME, NORMAL_NAME)                                                      \
  void NAME(Register Rd, Register Rs1, Register Rs2) {                                       \
    /* sub/subw -> c.sub/c.subw */                                                           \
    if (do_compress() &&                                                                     \
        (Rd == Rs1 && Rd->is_compressed_valid() && Rs2->is_compressed_valid())) {            \
      C_NAME(Rd, Rs2);                                                                       \
      return;                                                                                \
    }                                                                                        \
    NORMAL_NAME(Rd, Rs1, Rs2);                                                               \
  }

  INSN(sub,  c_sub,  _sub);
  INSN(subw, c_subw, _subw);

#undef INSN

// --------------------------
#define INSN(NAME, C_NAME, NORMAL_NAME)                                                      \
  void NAME(Register Rd, Register Rs1, Register Rs2) {                                       \
    /* and/or/xor/addw -> c.and/c.or/c.xor/c.addw */                                         \
    if (do_compress()) {                                                                     \
      Register src = noreg;                                                                  \
      if (Rs1->is_compressed_valid() && Rs2->is_compressed_valid() &&                        \
        ((src = Rs1, Rs2 == Rd) || (src = Rs2, Rs1 == Rd))) {                                \
        C_NAME(Rd, src);                                                                     \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    NORMAL_NAME(Rd, Rs1, Rs2);                                                               \
  }

  INSN(andr, c_and,  _andr);
  INSN(orr,  c_or,   _orr);
  INSN(xorr, c_xor,  _xorr);
  INSN(addw, c_addw, _addw);

#undef INSN

private:
// some helper functions
#define FUNC(NAME, funct3, bits)                                                             \
  bool NAME(Register rs1, Register rd_rs2, int32_t imm12, bool ld) {                         \
    return rs1 == sp &&                                                                      \
      is_uimm(imm12, bits) &&                                                                \
      (intx(imm12) & funct3) == 0x0 &&                                                       \
      (!ld || rd_rs2 != x0);                                                                 \
  }                                                                                          \

  FUNC(is_c_ldsdsp,  0b111, 9);
  FUNC(is_c_lwswsp,  0b011, 8);

#undef FUNC

#define FUNC(NAME, funct3, bits)                                                             \
  bool NAME(Register rs1, int32_t imm12) {                                                   \
    return rs1 == sp &&                                                                      \
      is_uimm(imm12, bits) &&                                                                \
      (intx(imm12) & funct3) == 0x0;                                                         \
  }                                                                                          \

  FUNC(is_c_fldsdsp, 0b111, 9);

#undef FUNC

#define FUNC(NAME, REG_TYPE, funct3, bits)                                                   \
  bool NAME(Register rs1, REG_TYPE rd_rs2, int32_t imm12) {                                  \
    return rs1->is_compressed_valid() &&                                                     \
      rd_rs2->is_compressed_valid() &&                                                       \
      is_uimm(imm12, bits) &&                                                                \
      (intx(imm12) & funct3) == 0x0;                                                         \
  }                                                                                          \

  FUNC(is_c_ldsd,  Register,      0b111, 8);
  FUNC(is_c_lwsw,  Register,      0b011, 7);
  FUNC(is_c_fldsd, FloatRegister, 0b111, 8);

#undef FUNC

public:
  bool do_compress() const {
    return UseRVC && in_compressible_region();
  }

// --------------------------
// Load/store register
// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                \
    /* lw -> c.lwsp/c.lw */                                                                  \
    if (do_compress()) {                                                                     \
      if (is_c_lwswsp(Rs, Rd, offset, true)) {                                               \
        c_lwsp(Rd, offset);                                                                  \
        return;                                                                              \
      } else if (is_c_lwsw(Rs, Rd, offset)) {                                                \
        c_lw(Rd, Rs, offset);                                                                \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _lw(Rd, Rs, offset);                                                                     \
  }

  INSN(lw);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                \
    /* ld -> c.ldsp/c.ld */                                                                  \
    if (do_compress()) {                                                                     \
      if (is_c_ldsdsp(Rs, Rd, offset, true)) {                                               \
        c_ldsp(Rd, offset);                                                                  \
        return;                                                                              \
      } else if (is_c_ldsd(Rs, Rd, offset)) {                                                \
        c_ld(Rd, Rs, offset);                                                                \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _ld(Rd, Rs, offset);                                                                     \
  }

  INSN(ld);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(FloatRegister Rd, Register Rs, const int32_t offset) {                           \
    /* fld -> c.fldsp/c.fld */                                                               \
    if (do_compress()) {                                                                     \
      if (is_c_fldsdsp(Rs, offset)) {                                                        \
        c_fldsp(Rd, offset);                                                                 \
        return;                                                                              \
      } else if (is_c_fldsd(Rs, Rd, offset)) {                                               \
        c_fld(Rd, Rs, offset);                                                               \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _fld(Rd, Rs, offset);                                                                    \
  }

  INSN(fld);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                \
    /* sd -> c.sdsp/c.sd */                                                                  \
    if (do_compress()) {                                                                     \
      if (is_c_ldsdsp(Rs, Rd, offset, false)) {                                              \
        c_sdsp(Rd, offset);                                                                  \
        return;                                                                              \
      } else if (is_c_ldsd(Rs, Rd, offset)) {                                                \
        c_sd(Rd, Rs, offset);                                                                \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _sd(Rd, Rs, offset);                                                                     \
  }

  INSN(sd);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                \
    /* sw -> c.swsp/c.sw */                                                                  \
    if (do_compress()) {                                                                     \
      if (is_c_lwswsp(Rs, Rd, offset, false)) {                                              \
        c_swsp(Rd, offset);                                                                  \
        return;                                                                              \
      } else if (is_c_lwsw(Rs, Rd, offset)) {                                                \
        c_sw(Rd, Rs, offset);                                                                \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _sw(Rd, Rs, offset);                                                                     \
  }

  INSN(sw);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(FloatRegister Rd, Register Rs, const int32_t offset) {                           \
    /* fsd -> c.fsdsp/c.fsd */                                                               \
    if (do_compress()) {                                                                     \
      if (is_c_fldsdsp(Rs, offset)) {                                                        \
        c_fsdsp(Rd, offset);                                                                 \
        return;                                                                              \
      } else if (is_c_fldsd(Rs, Rd, offset)) {                                               \
        c_fsd(Rd, Rs, offset);                                                               \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _fsd(Rd, Rs, offset);                                                                    \
  }

  INSN(fsd);

#undef INSN

// --------------------------
// Unconditional branch instructions
// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs, const int32_t offset) {                                \
    /* jalr -> c.jr/c.jalr */                                                                \
    if (do_compress() && (offset == 0 && Rs != x0)) {                                        \
      if (Rd == x1) {                                                                        \
        c_jalr(Rs);                                                                          \
        return;                                                                              \
      } else if (Rd == x0) {                                                                 \
        c_jr(Rs);                                                                            \
        return;                                                                              \
      }                                                                                      \
    }                                                                                        \
    _jalr(Rd, Rs, offset);                                                                   \
  }

  INSN(jalr);

#undef INSN

// --------------------------
// Miscellaneous Instructions
// --------------------------
#define INSN(NAME)                                                     \
  void NAME() {                                                        \
    /* ebreak -> c.ebreak */                                           \
    if (do_compress()) {                                               \
      c_ebreak();                                                      \
      return;                                                          \
    }                                                                  \
    _ebreak();                                                         \
  }

  INSN(ebreak);

#undef INSN

// --------------------------
// Immediate Instructions
// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs1, int32_t imm) {                                        \
    /* addi -> c.addi/c.nop/c.mv/c.addi16sp/c.addi4spn */                                    \
    if (do_compress()) {                                                                     \
      if (Rd == Rs1 && is_simm6(imm)) {                                                      \
        c_addi(Rd, imm);                                                                     \
        return;                                                                              \
      } else if (imm == 0 && Rd != x0 && Rs1 != x0) {                                        \
        c_mv(Rd, Rs1);                                                                       \
        return;                                                                              \
      } else if (Rs1 == sp && imm != 0) {                                                    \
        if (Rd == Rs1 && (imm & 0b1111) == 0x0 && is_simm10(imm)) {                          \
          c_addi16sp(imm);                                                                   \
          return;                                                                            \
        } else if (Rd->is_compressed_valid() && (imm & 0b11) == 0x0 && is_uimm10(imm)) {     \
          c_addi4spn(Rd, imm);                                                               \
          return;                                                                            \
        }                                                                                    \
      }                                                                                      \
    }                                                                                        \
    _addi(Rd, Rs1, imm);                                                                     \
  }

  INSN(addi);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs1, int32_t imm) {                                        \
    /* addiw -> c.addiw */                                                                   \
    if (do_compress() && (Rd == Rs1 && Rd != x0 && is_simm6(imm))) {                         \
      c_addiw(Rd, imm);                                                                      \
      return;                                                                                \
    }                                                                                        \
    _addiw(Rd, Rs1, imm);                                                                    \
  }

  INSN(addiw);

#undef INSN

// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs1, int32_t imm) {                                        \
    /* and_imm12 -> c.andi */                                                                \
    if (do_compress() &&                                                                     \
        (Rd == Rs1 && Rd->is_compressed_valid() && is_simm6(imm))) {                         \
      c_andi(Rd, imm);                                                                       \
      return;                                                                                \
    }                                                                                        \
    _and_imm12(Rd, Rs1, imm);                                                                \
  }

  INSN(and_imm12);

#undef INSN

// --------------------------
// Shift Immediate Instructions
// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {                                     \
    /* slli -> c.slli */                                                                     \
    if (do_compress() && (Rd == Rs1 && Rd != x0 && shamt != 0)) {                            \
      c_slli(Rd, shamt);                                                                     \
      return;                                                                                \
    }                                                                                        \
    if (shamt != 0) {                                                                        \
      _slli(Rd, Rs1, shamt);                                                                 \
    } else {                                                                                 \
      if (Rd != Rs1) {                                                                       \
        addi(Rd, Rs1, 0);                                                                    \
      }                                                                                      \
    }                                                                                        \
  }

  INSN(slli);

#undef INSN

// --------------------------
#define INSN(NAME, C_NAME, NORMAL_NAME)                                                      \
  void NAME(Register Rd, Register Rs1, unsigned shamt) {                                     \
    /* srai/srli -> c.srai/c.srli */                                                         \
    if (do_compress() && (Rd == Rs1 && Rd->is_compressed_valid() && shamt != 0)) {           \
      C_NAME(Rd, shamt);                                                                     \
      return;                                                                                \
    }                                                                                        \
    if (shamt != 0) {                                                                        \
      NORMAL_NAME(Rd, Rs1, shamt);                                                           \
    } else {                                                                                 \
      if (Rd != Rs1) {                                                                       \
        addi(Rd, Rs1, 0);                                                                    \
      }                                                                                      \
    }                                                                                        \
  }

  INSN(srai, c_srai, _srai);
  INSN(srli, c_srli, _srli);

#undef INSN

// --------------------------
// Upper Immediate Instruction
// --------------------------
#define INSN(NAME)                                                                           \
  void NAME(Register Rd, int32_t imm) {                                                      \
    /* lui -> c.lui */                                                                       \
    if (do_compress() && (Rd != x0 && Rd != x2 && imm != 0 && is_simm18(imm))) {             \
      c_lui(Rd, imm);                                                                        \
      return;                                                                                \
    }                                                                                        \
    _lui(Rd, imm);                                                                           \
  }

  INSN(lui);

#undef INSN

// Cache Management Operations
#define INSN(NAME, funct)                                                                    \
  void NAME(Register Rs1) {                                                                  \
    unsigned insn = 0;                                                                       \
    patch((address)&insn, 6,  0, 0b0001111);                                                 \
    patch((address)&insn, 14, 12, 0b010);                                                    \
    patch_reg((address)&insn, 15, Rs1);                                                      \
    patch((address)&insn, 31, 20, funct);                                                    \
    emit(insn);                                                                              \
  }

  INSN(cbo_inval, 0b0000000000000);
  INSN(cbo_clean, 0b0000000000001);
  INSN(cbo_flush, 0b0000000000010);
  INSN(cbo_zero,  0b0000000000100);

#undef INSN

#define INSN(NAME, funct)                                                                    \
  void NAME(Register Rs1, int32_t offset) {                                                  \
    guarantee((offset & 0x1f) == 0, "offset lowest 5 bits must be zero");                    \
    int32_t upperOffset = offset >> 5;                                                       \
    unsigned insn = 0;                                                                       \
    patch((address)&insn, 6,  0, 0b0010011);                                                 \
    patch((address)&insn, 14, 12, 0b110);                                                    \
    patch_reg((address)&insn, 15, Rs1);                                                      \
    patch((address)&insn, 24, 20, funct);                                                    \
    upperOffset &= 0x7f;                                                                     \
    patch((address)&insn, 31, 25, upperOffset);                                              \
    emit(insn);                                                                              \
  }

  INSN(prefetch_i, 0b0000000000000);
  INSN(prefetch_r, 0b0000000000001);
  INSN(prefetch_w, 0b0000000000011);

#undef INSN

// ---------------------------------------------------------------------------------------

#define INSN(NAME, REGISTER)                       \
  void NAME(Register Rs) {                         \
    jalr(REGISTER, Rs, 0);                         \
  }

  INSN(jr,   x0);
  INSN(jalr, x1);

#undef INSN

  // Stack overflow checking
  virtual void bang_stack_with_offset(int offset) { Unimplemented(); }

  static bool is_simm5(int64_t x);
  static bool is_simm6(int64_t x);
  static bool is_simm12(int64_t x);
  static bool is_simm13(int64_t x);
  static bool is_simm18(int64_t x);
  static bool is_simm21(int64_t x);

  static bool is_uimm3(uint64_t x);
  static bool is_uimm5(uint64_t x);
  static bool is_uimm6(uint64_t x);
  static bool is_uimm7(uint64_t x);
  static bool is_uimm8(uint64_t x);
  static bool is_uimm9(uint64_t x);
  static bool is_uimm10(uint64_t x);

  // The maximum range of a branch is fixed for the RISCV architecture.
  static const unsigned long branch_range = 1 * M;

  static bool reachable_from_branch_at(address branch, address target) {
    return uabs(target - branch) < branch_range;
  }

  Assembler(CodeBuffer* code) : AbstractAssembler(code), _in_compressible_region(true) {}
};

#endif // CPU_RISCV_ASSEMBLER_RISCV_HPP
