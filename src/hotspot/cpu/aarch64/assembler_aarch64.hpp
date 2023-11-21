/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2021, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_ASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_ASSEMBLER_AARCH64_HPP

#include "asm/register.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include <type_traits>

#ifdef __GNUC__

// __nop needs volatile so that compiler doesn't optimize it away
#define NOP() asm volatile ("nop");

#elif defined(_MSC_VER)

// Use MSVC intrinsic: https://docs.microsoft.com/en-us/cpp/intrinsics/arm64-intrinsics?view=vs-2019#I
#define NOP() __nop();

#endif


// definitions of various symbolic names for machine registers

// First intercalls between C and Java which use 8 general registers
// and 8 floating registers

// we also have to copy between x86 and ARM registers but that's a
// secondary complication -- not all code employing C call convention
// executes as x86 code though -- we generate some of it

class Argument {
 public:
  enum {
    n_int_register_parameters_c   = 8,  // r0, r1, ... r7 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8,  // v0, v1, ... v7 (c_farg0, c_farg1, ... )

    n_int_register_parameters_j   = 8, // r1, ... r7, r0 (rj_rarg0, j_rarg1, ...
    n_float_register_parameters_j = 8  // v0, v1, ... v7 (j_farg0, j_farg1, ...
  };
};

constexpr Register c_rarg0 = r0;
constexpr Register c_rarg1 = r1;
constexpr Register c_rarg2 = r2;
constexpr Register c_rarg3 = r3;
constexpr Register c_rarg4 = r4;
constexpr Register c_rarg5 = r5;
constexpr Register c_rarg6 = r6;
constexpr Register c_rarg7 = r7;

constexpr FloatRegister c_farg0 = v0;
constexpr FloatRegister c_farg1 = v1;
constexpr FloatRegister c_farg2 = v2;
constexpr FloatRegister c_farg3 = v3;
constexpr FloatRegister c_farg4 = v4;
constexpr FloatRegister c_farg5 = v5;
constexpr FloatRegister c_farg6 = v6;
constexpr FloatRegister c_farg7 = v7;

// Symbolically name the register arguments used by the Java calling convention.
// We have control over the convention for java so we can do what we please.
// What pleases us is to offset the java calling convention so that when
// we call a suitable jni method the arguments are lined up and we don't
// have to do much shuffling. A suitable jni method is non-static and a
// small number of arguments
//
//  |--------------------------------------------------------------------|
//  | c_rarg0  c_rarg1  c_rarg2 c_rarg3 c_rarg4 c_rarg5 c_rarg6 c_rarg7  |
//  |--------------------------------------------------------------------|
//  | r0       r1       r2      r3      r4      r5      r6      r7       |
//  |--------------------------------------------------------------------|
//  | j_rarg7  j_rarg0  j_rarg1 j_rarg2 j_rarg3 j_rarg4 j_rarg5 j_rarg6  |
//  |--------------------------------------------------------------------|


constexpr Register j_rarg0 = c_rarg1;
constexpr Register j_rarg1 = c_rarg2;
constexpr Register j_rarg2 = c_rarg3;
constexpr Register j_rarg3 = c_rarg4;
constexpr Register j_rarg4 = c_rarg5;
constexpr Register j_rarg5 = c_rarg6;
constexpr Register j_rarg6 = c_rarg7;
constexpr Register j_rarg7 = c_rarg0;

// Java floating args are passed as per C

constexpr FloatRegister j_farg0 = v0;
constexpr FloatRegister j_farg1 = v1;
constexpr FloatRegister j_farg2 = v2;
constexpr FloatRegister j_farg3 = v3;
constexpr FloatRegister j_farg4 = v4;
constexpr FloatRegister j_farg5 = v5;
constexpr FloatRegister j_farg6 = v6;
constexpr FloatRegister j_farg7 = v7;

// registers used to hold VM data either temporarily within a method
// or across method calls

// volatile (caller-save) registers

// r8 is used for indirect result location return
// we use it and r9 as scratch registers
constexpr Register rscratch1 = r8;
constexpr Register rscratch2 = r9;

// current method -- must be in a call-clobbered register
constexpr Register rmethod = r12;

// non-volatile (callee-save) registers are r16-29
// of which the following are dedicated global state

constexpr Register lr            = r30; // link register
constexpr Register rfp           = r29; // frame pointer
constexpr Register rthread       = r28; // current thread
constexpr Register rheapbase     = r27; // base of heap
constexpr Register rcpool        = r26; // constant pool cache
constexpr Register rlocals       = r24; // locals on stack
constexpr Register rbcp          = r22; // bytecode pointer
constexpr Register rdispatch     = r21; // dispatch table base
constexpr Register esp           = r20; // Java expression stack pointer
constexpr Register r19_sender_sp = r19; // sender's SP while in interpreter

// Preserved predicate register with all elements set TRUE.
constexpr PRegister ptrue = p7;

#define assert_cond(ARG1) assert(ARG1, #ARG1)

namespace asm_util {
  uint32_t encode_logical_immediate(bool is32, uint64_t imm);
  uint32_t encode_sve_logical_immediate(unsigned elembits, uint64_t imm);
  bool operand_valid_for_immediate_bits(int64_t imm, unsigned nbits);
};

using namespace asm_util;


class Assembler;

class Instruction_aarch64 {
  unsigned insn;
#ifdef ASSERT
  unsigned bits;
#endif
  Assembler *assem;

public:

  Instruction_aarch64(class Assembler *as) {
#ifdef ASSERT
    bits = 0;
#endif
    insn = 0;
    assem = as;
  }

  inline ~Instruction_aarch64();

  unsigned &get_insn() { return insn; }
#ifdef ASSERT
  unsigned &get_bits() { return bits; }
#endif

  static inline int32_t extend(unsigned val, int hi = 31, int lo = 0) {
    union {
      unsigned u;
      int n;
    };

    u = val << (31 - hi);
    n = n >> (31 - hi + lo);
    return n;
  }

  static inline uint32_t extract(uint32_t val, int msb, int lsb) {
    int nbits = msb - lsb + 1;
    assert_cond(msb >= lsb);
    uint32_t mask = checked_cast<uint32_t>(right_n_bits(nbits));
    uint32_t result = val >> lsb;
    result &= mask;
    return result;
  }

  static inline int32_t sextract(uint32_t val, int msb, int lsb) {
    uint32_t uval = extract(val, msb, lsb);
    return extend(uval, msb - lsb);
  }

  static ALWAYSINLINE void patch(address a, int msb, int lsb, uint64_t val) {
    int nbits = msb - lsb + 1;
    guarantee(val < (1ULL << nbits), "Field too big for insn");
    assert_cond(msb >= lsb);
    unsigned mask = checked_cast<unsigned>(right_n_bits(nbits));
    val <<= lsb;
    mask <<= lsb;
    unsigned target = *(unsigned *)a;
    target &= ~mask;
    target |= (unsigned)val;
    *(unsigned *)a = target;
  }

  static void spatch(address a, int msb, int lsb, int64_t val) {
    int nbits = msb - lsb + 1;
    int64_t chk = val >> (nbits - 1);
    guarantee (chk == -1 || chk == 0, "Field too big for insn at " INTPTR_FORMAT, p2i(a));
    uint64_t uval = val;
    unsigned mask = checked_cast<unsigned>(right_n_bits(nbits));
    uval &= mask;
    uval <<= lsb;
    mask <<= lsb;
    unsigned target = *(unsigned *)a;
    target &= ~mask;
    target |= (unsigned)uval;
    *(unsigned *)a = target;
  }

  void f(unsigned val, int msb, int lsb) {
    int nbits = msb - lsb + 1;
    guarantee(val < (1ULL << nbits), "Field too big for insn");
    assert_cond(msb >= lsb);
    val <<= lsb;
    insn |= val;
#ifdef ASSERT
    unsigned mask = checked_cast<unsigned>(right_n_bits(nbits));
    mask <<= lsb;
    assert_cond((bits & mask) == 0);
    bits |= mask;
#endif
  }

  void f(unsigned val, int bit) {
    f(val, bit, bit);
  }

  void sf(int64_t val, int msb, int lsb) {
    int nbits = msb - lsb + 1;
    int64_t chk = val >> (nbits - 1);
    guarantee (chk == -1 || chk == 0, "Field too big for insn");
    uint64_t uval = val;
    unsigned mask = checked_cast<unsigned>(right_n_bits(nbits));
    uval &= mask;
    f((unsigned)uval, lsb + nbits - 1, lsb);
  }

  void rf(Register r, int lsb) {
    f(r->raw_encoding(), lsb + 4, lsb);
  }

  // reg|ZR
  void zrf(Register r, int lsb) {
    f(r->raw_encoding() - (r == zr), lsb + 4, lsb);
  }

  // reg|SP
  void srf(Register r, int lsb) {
    f(r == sp ? 31 : r->raw_encoding(), lsb + 4, lsb);
  }

  void rf(FloatRegister r, int lsb) {
    f(r->raw_encoding(), lsb + 4, lsb);
  }

  void prf(PRegister r, int lsb) {
    f(r->raw_encoding(), lsb + 3, lsb);
  }

  void pgrf(PRegister r, int lsb) {
    f(r->raw_encoding(), lsb + 2, lsb);
  }

  unsigned get(int msb = 31, int lsb = 0) {
    int nbits = msb - lsb + 1;
    unsigned mask = checked_cast<unsigned>(right_n_bits(nbits)) << lsb;
    assert_cond((bits & mask) == mask);
    return (insn & mask) >> lsb;
  }
};

#define starti Instruction_aarch64 current_insn(this);

class PrePost {
  int _offset;
  Register _r;
protected:
  PrePost(Register reg, int o) : _offset(o), _r(reg) { }
  ~PrePost() = default;
  PrePost(const PrePost&) = default;
  PrePost& operator=(const PrePost&) = default;
public:
  int offset() const { return _offset; }
  Register reg() const { return _r; }
};

class Pre : public PrePost {
public:
  Pre(Register reg, int o) : PrePost(reg, o) { }
};

class Post : public PrePost {
  Register _idx;
  bool _is_postreg;
public:
  Post(Register reg, int o) : PrePost(reg, o), _idx(noreg), _is_postreg(false) {}
  Post(Register reg, Register idx) : PrePost(reg, 0), _idx(idx), _is_postreg(true) {}
  Register idx_reg() const { return _idx; }
  bool is_postreg() const { return _is_postreg; }
};

namespace ext
{
  enum operation { uxtb, uxth, uxtw, uxtx, sxtb, sxth, sxtw, sxtx };
};

// Addressing modes
class Address {
 public:

  enum mode { no_mode, base_plus_offset, pre, post, post_reg,
              base_plus_offset_reg, literal };

  // Shift and extend for base reg + reg offset addressing
  class extend {
    int _option, _shift;
    ext::operation _op;
  public:
    extend() { }
    extend(int s, int o, ext::operation op) : _option(o), _shift(s), _op(op) { }
    int option() const{ return _option; }
    int shift() const { return _shift; }
    ext::operation op() const { return _op; }
  };

  static extend uxtw(int shift = -1) { return extend(shift, 0b010, ext::uxtw); }
  static extend lsl(int shift = -1)  { return extend(shift, 0b011, ext::uxtx); }
  static extend sxtw(int shift = -1) { return extend(shift, 0b110, ext::sxtw); }
  static extend sxtx(int shift = -1) { return extend(shift, 0b111, ext::sxtx); }

 private:
  struct Nonliteral {
    Nonliteral(Register base, Register index, int64_t offset, extend ext = extend())
      : _base(base), _index(index), _offset(offset), _ext(ext) {}
    Register _base;
    Register _index;
    int64_t _offset;
    extend _ext;
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

  Address(Register r, Register r1, extend ext = lsl()) :
    _mode(base_plus_offset_reg),
    _nonliteral(r, r1, 0, ext)
  {}

  Address(Pre p) :
    _mode(pre),
    _nonliteral(p.reg(), noreg, p.offset())
  {}

  Address(Post p) :
    _mode(p.is_postreg() ? post_reg : post),
    _nonliteral(p.reg(), p.idx_reg(), p.offset())
  {}

  Address(address target, const RelocationHolder& rspec) :
    _mode(literal),
    _literal(target, rspec)
  {}

  Address(address target, relocInfo::relocType rtype = relocInfo::external_word_type);

  Address(Register base, RegisterOrConstant index, extend ext = lsl()) {
    if (index.is_register()) {
      _mode = base_plus_offset_reg;
      new (&_nonliteral) Nonliteral(base, index.as_register(), 0, ext);
    } else {
      guarantee(ext.option() == ext::uxtx, "should be");
      assert(index.is_constant(), "should be");
      _mode = base_plus_offset;
      new (&_nonliteral) Nonliteral(base,
                                    noreg,
                                    index.as_constant() << ext.shift());
    }
  }

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

  Register base() const {
    assert_is_nonliteral();
    return _nonliteral._base;
  }

  int64_t offset() const {
    assert_is_nonliteral();
    return _nonliteral._offset;
  }

  Register index() const {
    assert_is_nonliteral();
    return _nonliteral._index;
  }

  extend ext() const {
    assert_is_nonliteral();
    return _nonliteral._ext;
  }

  mode getMode() const {
    return _mode;
  }

  bool uses(Register reg) const {
    switch (_mode) {
    case literal:
    case no_mode:
      return false;
    case base_plus_offset:
    case base_plus_offset_reg:
    case pre:
    case post:
    case post_reg:
      return base() == reg || index() == reg;
    default:
      ShouldNotReachHere();
      return false;
    }
  }

  address target() const {
    assert_is_literal();
    return _literal._target;
  }

  const RelocationHolder& rspec() const {
    assert_is_literal();
    return _literal._rspec;
  }

  void encode(Instruction_aarch64 *i) const {
    i->f(0b111, 29, 27);
    i->srf(base(), 5);

    switch(_mode) {
    case base_plus_offset:
      {
        unsigned size = i->get(31, 30);
        if (i->get(26, 26) && i->get(23, 23)) {
          // SIMD Q Type - Size = 128 bits
          assert(size == 0, "bad size");
          size = 0b100;
        }
        assert(offset_ok_for_immed(offset(), size),
               "must be, was: " INT64_FORMAT ", %d", offset(), size);
        unsigned mask = (1 << size) - 1;
        if (offset() < 0 || offset() & mask) {
          i->f(0b00, 25, 24);
          i->f(0, 21), i->f(0b00, 11, 10);
          i->sf(offset(), 20, 12);
        } else {
          i->f(0b01, 25, 24);
          i->f(checked_cast<unsigned>(offset() >> size), 21, 10);
        }
      }
      break;

    case base_plus_offset_reg:
      {
        i->f(0b00, 25, 24);
        i->f(1, 21);
        i->rf(index(), 16);
        i->f(ext().option(), 15, 13);
        unsigned size = i->get(31, 30);
        if (i->get(26, 26) && i->get(23, 23)) {
          // SIMD Q Type - Size = 128 bits
          assert(size == 0, "bad size");
          size = 0b100;
        }
        if (size == 0) // It's a byte
          i->f(ext().shift() >= 0, 12);
        else {
          guarantee(ext().shift() <= 0 || ext().shift() == (int)size, "bad shift");
          i->f(ext().shift() > 0, 12);
        }
        i->f(0b10, 11, 10);
      }
      break;

    case pre:
      i->f(0b00, 25, 24);
      i->f(0, 21), i->f(0b11, 11, 10);
      i->sf(offset(), 20, 12);
      break;

    case post:
      i->f(0b00, 25, 24);
      i->f(0, 21), i->f(0b01, 11, 10);
      i->sf(offset(), 20, 12);
      break;

    default:
      ShouldNotReachHere();
    }
  }

  void encode_pair(Instruction_aarch64 *i) const {
    switch(_mode) {
    case base_plus_offset:
      i->f(0b010, 25, 23);
      break;
    case pre:
      i->f(0b011, 25, 23);
      break;
    case post:
      i->f(0b001, 25, 23);
      break;
    default:
      ShouldNotReachHere();
    }

    unsigned size; // Operand shift in 32-bit words

    if (i->get(26, 26)) { // float
      switch(i->get(31, 30)) {
      case 0b10:
        size = 2; break;
      case 0b01:
        size = 1; break;
      case 0b00:
        size = 0; break;
      default:
        ShouldNotReachHere();
        size = 0;  // unreachable
      }
    } else {
      size = i->get(31, 31);
    }

    size = 4 << size;
    guarantee(offset() % size == 0, "bad offset");
    i->sf(offset() / size, 21, 15);
    i->srf(base(), 5);
  }

  void encode_nontemporal_pair(Instruction_aarch64 *i) const {
    guarantee(_mode == base_plus_offset, "Bad addressing mode for nontemporal op");
    i->f(0b000, 25, 23);
    unsigned size = i->get(31, 31);
    size = 4 << size;
    guarantee(offset() % size == 0, "bad offset");
    i->sf(offset() / size, 21, 15);
    i->srf(base(), 5);
  }

  void lea(MacroAssembler *, Register) const;

  static bool offset_ok_for_immed(int64_t offset, uint shift);

  static bool offset_ok_for_sve_immed(int64_t offset, int shift, int vl /* sve vector length */) {
    if (offset % vl == 0) {
      // Convert address offset into sve imm offset (MUL VL).
      int64_t sve_offset = offset / vl;
      int32_t range = 1 << (shift - 1);
      if ((-range <= sve_offset) && (sve_offset < range)) {
        // sve_offset can be encoded
        return true;
      }
    }
    return false;
  }
};

// Convenience classes
class RuntimeAddress: public Address {

  public:

  RuntimeAddress(address target) : Address(target, relocInfo::runtime_call_type) {}

};

class OopAddress: public Address {

  public:

  OopAddress(address target) : Address(target, relocInfo::oop_type){}

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

};

class InternalAddress: public Address {

  public:

  InternalAddress(address target) : Address(target, relocInfo::internal_word_type) {}
};

const int FPUStateSizeInWords = FloatRegister::number_of_registers * FloatRegister::save_slots_per_register;

typedef enum {
  PLDL1KEEP = 0b00000, PLDL1STRM, PLDL2KEEP, PLDL2STRM, PLDL3KEEP, PLDL3STRM,
  PSTL1KEEP = 0b10000, PSTL1STRM, PSTL2KEEP, PSTL2STRM, PSTL3KEEP, PSTL3STRM,
  PLIL1KEEP = 0b01000, PLIL1STRM, PLIL2KEEP, PLIL2STRM, PLIL3KEEP, PLIL3STRM
} prfop;

class Assembler : public AbstractAssembler {

public:

#ifndef PRODUCT
  static const uintptr_t asm_bp;

  void emit_int32(jint x) {
    if ((uintptr_t)pc() == asm_bp)
      NOP();
    AbstractAssembler::emit_int32(x);
  }
#else
  void emit_int32(jint x) {
    AbstractAssembler::emit_int32(x);
  }
#endif

  enum { instruction_size = 4 };

  //---<  calculate length of instruction  >---
  // We just use the values set above.
  // instruction must start at passed address
  static unsigned int instr_len(unsigned char *instr) { return instruction_size; }

  //---<  longest instructions  >---
  static unsigned int instr_maxlen() { return instruction_size; }

  Address adjust(Register base, int offset, bool preIncrement) {
    if (preIncrement)
      return Address(Pre(base, offset));
    else
      return Address(Post(base, offset));
  }

  Address pre(Register base, int offset) {
    return adjust(base, offset, true);
  }

  Address post(Register base, int offset) {
    return adjust(base, offset, false);
  }

  Address post(Register base, Register idx) {
    return Address(Post(base, idx));
  }

  static address locate_next_instruction(address inst);

#define f current_insn.f
#define sf current_insn.sf
#define rf current_insn.rf
#define srf current_insn.srf
#define zrf current_insn.zrf
#define prf current_insn.prf
#define pgrf current_insn.pgrf

  typedef void (Assembler::* uncond_branch_insn)(address dest);
  typedef void (Assembler::* compare_and_branch_insn)(Register Rt, address dest);
  typedef void (Assembler::* test_and_branch_insn)(Register Rt, int bitpos, address dest);
  typedef void (Assembler::* prefetch_insn)(address target, prfop);

  void wrap_label(Label &L, uncond_branch_insn insn);
  void wrap_label(Register r, Label &L, compare_and_branch_insn insn);
  void wrap_label(Register r, int bitpos, Label &L, test_and_branch_insn insn);
  void wrap_label(Label &L, prfop, prefetch_insn insn);

  // PC-rel. addressing

  void adr(Register Rd, address dest);
  void _adrp(Register Rd, address dest);

  void adr(Register Rd, const Address &dest);
  void _adrp(Register Rd, const Address &dest);

  void adr(Register Rd, Label &L) {
    wrap_label(Rd, L, &Assembler::Assembler::adr);
  }
  void _adrp(Register Rd, Label &L) {
    wrap_label(Rd, L, &Assembler::_adrp);
  }

  void adrp(Register Rd, const Address &dest, uint64_t &offset) = delete;

#undef INSN

  void add_sub_immediate(Instruction_aarch64 &current_insn, Register Rd, Register Rn,
                         unsigned uimm, int op, int negated_op);

  // Add/subtract (immediate)
#define INSN(NAME, decode, negated)                                     \
  void NAME(Register Rd, Register Rn, unsigned imm, unsigned shift) {   \
    starti;                                                             \
    f(decode, 31, 29), f(0b10001, 28, 24), f(shift, 23, 22), f(imm, 21, 10); \
    zrf(Rd, 0), srf(Rn, 5);                                             \
  }                                                                     \
                                                                        \
  void NAME(Register Rd, Register Rn, unsigned imm) {                   \
    starti;                                                             \
    add_sub_immediate(current_insn, Rd, Rn, imm, decode, negated);      \
  }

  INSN(addsw, 0b001, 0b011);
  INSN(subsw, 0b011, 0b001);
  INSN(adds,  0b101, 0b111);
  INSN(subs,  0b111, 0b101);

#undef INSN

#define INSN(NAME, decode, negated)                     \
  void NAME(Register Rd, Register Rn, unsigned imm) {   \
    starti;                                             \
    add_sub_immediate(current_insn, Rd, Rn, imm, decode, negated);     \
  }

  INSN(addw, 0b000, 0b010);
  INSN(subw, 0b010, 0b000);
  INSN(add,  0b100, 0b110);
  INSN(sub,  0b110, 0b100);

#undef INSN

 // Logical (immediate)
#define INSN(NAME, decode, is32)                                \
  void NAME(Register Rd, Register Rn, uint64_t imm) {           \
    starti;                                                     \
    uint32_t val = encode_logical_immediate(is32, imm);         \
    f(decode, 31, 29), f(0b100100, 28, 23), f(val, 22, 10);     \
    srf(Rd, 0), zrf(Rn, 5);                                     \
  }

  INSN(andw, 0b000, true);
  INSN(orrw, 0b001, true);
  INSN(eorw, 0b010, true);
  INSN(andr, 0b100, false);
  INSN(orr,  0b101, false);
  INSN(eor,  0b110, false);

#undef INSN

#define INSN(NAME, decode, is32)                                \
  void NAME(Register Rd, Register Rn, uint64_t imm) {           \
    starti;                                                     \
    uint32_t val = encode_logical_immediate(is32, imm);         \
    f(decode, 31, 29), f(0b100100, 28, 23), f(val, 22, 10);     \
    zrf(Rd, 0), zrf(Rn, 5);                                     \
  }

  INSN(ands, 0b111, false);
  INSN(andsw, 0b011, true);

#undef INSN

  // Move wide (immediate)
#define INSN(NAME, opcode)                                              \
  void NAME(Register Rd, unsigned imm, unsigned shift = 0) {            \
    assert_cond((shift/16)*16 == shift);                                \
    starti;                                                             \
    f(opcode, 31, 29), f(0b100101, 28, 23), f(shift/16, 22, 21),        \
      f(imm, 20, 5);                                                    \
    zrf(Rd, 0);                                                         \
  }

  INSN(movnw, 0b000);
  INSN(movzw, 0b010);
  INSN(movkw, 0b011);
  INSN(movn,  0b100);
  INSN(movz,  0b110);
  INSN(movk,  0b111);

#undef INSN

  // Bitfield
#define INSN(NAME, opcode, size)                                        \
  void NAME(Register Rd, Register Rn, unsigned immr, unsigned imms) {   \
    starti;                                                             \
    guarantee(size == 1 || (immr < 32 && imms < 32), "incorrect immr/imms");\
    f(opcode, 31, 22), f(immr, 21, 16), f(imms, 15, 10);                \
    zrf(Rn, 5), rf(Rd, 0);                                              \
  }

  INSN(sbfmw, 0b0001001100, 0);
  INSN(bfmw,  0b0011001100, 0);
  INSN(ubfmw, 0b0101001100, 0);
  INSN(sbfm,  0b1001001101, 1);
  INSN(bfm,   0b1011001101, 1);
  INSN(ubfm,  0b1101001101, 1);

#undef INSN

  // Extract
#define INSN(NAME, opcode, size)                                        \
  void NAME(Register Rd, Register Rn, Register Rm, unsigned imms) {     \
    starti;                                                             \
    guarantee(size == 1 || imms < 32, "incorrect imms");                \
    f(opcode, 31, 21), f(imms, 15, 10);                                 \
    zrf(Rm, 16), zrf(Rn, 5), zrf(Rd, 0);                                \
  }

  INSN(extrw, 0b00010011100, 0);
  INSN(extr,  0b10010011110, 1);

#undef INSN

  // The maximum range of a branch is fixed for the AArch64
  // architecture.  In debug mode we shrink it in order to test
  // trampolines, but not so small that branches in the interpreter
  // are out of range.
  static const uint64_t branch_range = NOT_DEBUG(128 * M) DEBUG_ONLY(2 * M);

  static bool reachable_from_branch_at(address branch, address target) {
    return uabs(target - branch) < branch_range;
  }

  // Unconditional branch (immediate)
#define INSN(NAME, opcode)                                              \
  void NAME(address dest) {                                             \
    starti;                                                             \
    int64_t offset = (dest - pc()) >> 2;                                \
    DEBUG_ONLY(assert(reachable_from_branch_at(pc(), dest), "debug only")); \
    f(opcode, 31), f(0b00101, 30, 26), sf(offset, 25, 0);               \
  }                                                                     \
  void NAME(Label &L) {                                                 \
    wrap_label(L, &Assembler::NAME);                                    \
  }                                                                     \
  void NAME(const Address &dest);

  INSN(b, 0);
  INSN(bl, 1);

#undef INSN

  // Compare & branch (immediate)
#define INSN(NAME, opcode)                              \
  void NAME(Register Rt, address dest) {                \
    int64_t offset = (dest - pc()) >> 2;                \
    starti;                                             \
    f(opcode, 31, 24), sf(offset, 23, 5), rf(Rt, 0);    \
  }                                                     \
  void NAME(Register Rt, Label &L) {                    \
    wrap_label(Rt, L, &Assembler::NAME);                \
  }

  INSN(cbzw,  0b00110100);
  INSN(cbnzw, 0b00110101);
  INSN(cbz,   0b10110100);
  INSN(cbnz,  0b10110101);

#undef INSN

  // Test & branch (immediate)
#define INSN(NAME, opcode)                                              \
  void NAME(Register Rt, int bitpos, address dest) {                    \
    int64_t offset = (dest - pc()) >> 2;                                \
    int b5 = bitpos >> 5;                                               \
    bitpos &= 0x1f;                                                     \
    starti;                                                             \
    f(b5, 31), f(opcode, 30, 24), f(bitpos, 23, 19), sf(offset, 18, 5); \
    rf(Rt, 0);                                                          \
  }                                                                     \
  void NAME(Register Rt, int bitpos, Label &L) {                        \
    wrap_label(Rt, bitpos, L, &Assembler::NAME);                        \
  }

  INSN(tbz,  0b0110110);
  INSN(tbnz, 0b0110111);

#undef INSN

  // Conditional branch (immediate)
  enum Condition
    {EQ, NE, HS, CS=HS, LO, CC=LO, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL, NV};

  void br(Condition  cond, address dest) {
    int64_t offset = (dest - pc()) >> 2;
    starti;
    f(0b0101010, 31, 25), f(0, 24), sf(offset, 23, 5), f(0, 4), f(cond, 3, 0);
  }

#define INSN(NAME, cond)                        \
  void NAME(address dest) {                     \
    br(cond, dest);                             \
  }

  INSN(beq, EQ);
  INSN(bne, NE);
  INSN(bhs, HS);
  INSN(bcs, CS);
  INSN(blo, LO);
  INSN(bcc, CC);
  INSN(bmi, MI);
  INSN(bpl, PL);
  INSN(bvs, VS);
  INSN(bvc, VC);
  INSN(bhi, HI);
  INSN(bls, LS);
  INSN(bge, GE);
  INSN(blt, LT);
  INSN(bgt, GT);
  INSN(ble, LE);
  INSN(bal, AL);
  INSN(bnv, NV);

  void br(Condition cc, Label &L);

#undef INSN

  // Exception generation
  void generate_exception(int opc, int op2, int LL, unsigned imm) {
    starti;
    f(0b11010100, 31, 24);
    f(opc, 23, 21), f(imm, 20, 5), f(op2, 4, 2), f(LL, 1, 0);
  }

#define INSN(NAME, opc, op2, LL)                \
  void NAME(unsigned imm) {                     \
    generate_exception(opc, op2, LL, imm);      \
  }

  INSN(svc, 0b000, 0, 0b01);
  INSN(hvc, 0b000, 0, 0b10);
  INSN(smc, 0b000, 0, 0b11);
  INSN(brk, 0b001, 0, 0b00);
  INSN(hlt, 0b010, 0, 0b00);
  INSN(dcps1, 0b101, 0, 0b01);
  INSN(dcps2, 0b101, 0, 0b10);
  INSN(dcps3, 0b101, 0, 0b11);

#undef INSN

  // System
  void system(int op0, int op1, int CRn, int CRm, int op2,
              Register rt = dummy_reg)
  {
    starti;
    f(0b11010101000, 31, 21);
    f(op0, 20, 19);
    f(op1, 18, 16);
    f(CRn, 15, 12);
    f(CRm, 11, 8);
    f(op2, 7, 5);
    rf(rt, 0);
  }

  // Hint instructions

#define INSN(NAME, crm, op2)               \
  void NAME() {                            \
    system(0b00, 0b011, 0b0010, crm, op2); \
  }

  INSN(nop,   0b000, 0b0000);
  INSN(yield, 0b000, 0b0001);
  INSN(wfe,   0b000, 0b0010);
  INSN(wfi,   0b000, 0b0011);
  INSN(sev,   0b000, 0b0100);
  INSN(sevl,  0b000, 0b0101);

  INSN(autia1716, 0b0001, 0b100);
  INSN(autiasp,   0b0011, 0b101);
  INSN(autiaz,    0b0011, 0b100);
  INSN(autib1716, 0b0001, 0b110);
  INSN(autibsp,   0b0011, 0b111);
  INSN(autibz,    0b0011, 0b110);
  INSN(pacia1716, 0b0001, 0b000);
  INSN(paciasp,   0b0011, 0b001);
  INSN(paciaz,    0b0011, 0b000);
  INSN(pacib1716, 0b0001, 0b010);
  INSN(pacibsp,   0b0011, 0b011);
  INSN(pacibz,    0b0011, 0b010);
  INSN(xpaclri,   0b0000, 0b111);

#undef INSN

  // we only provide mrs and msr for the special purpose system
  // registers where op1 (instr[20:19]) == 11
  // n.b msr has L (instr[21]) == 0 mrs has L == 1

  void msr(int op1, int CRn, int CRm, int op2, Register rt) {
    starti;
    f(0b1101010100011, 31, 19);
    f(op1, 18, 16);
    f(CRn, 15, 12);
    f(CRm, 11, 8);
    f(op2, 7, 5);
    // writing zr is ok
    zrf(rt, 0);
  }

  void mrs(int op1, int CRn, int CRm, int op2, Register rt) {
    starti;
    f(0b1101010100111, 31, 19);
    f(op1, 18, 16);
    f(CRn, 15, 12);
    f(CRm, 11, 8);
    f(op2, 7, 5);
    // reading to zr is a mistake
    rf(rt, 0);
  }

  enum barrier {OSHLD = 0b0001, OSHST, OSH, NSHLD=0b0101, NSHST, NSH,
                ISHLD = 0b1001, ISHST, ISH, LD=0b1101, ST, SY};

  void dsb(barrier imm) {
    system(0b00, 0b011, 0b00011, imm, 0b100);
  }

  void dmb(barrier imm) {
    system(0b00, 0b011, 0b00011, imm, 0b101);
  }

  void isb() {
    system(0b00, 0b011, 0b00011, SY, 0b110);
  }

  void sys(int op1, int CRn, int CRm, int op2,
           Register rt = as_Register(0b11111)) {
    system(0b01, op1, CRn, CRm, op2, rt);
  }

  // Only implement operations accessible from EL0 or higher, i.e.,
  //            op1    CRn    CRm    op2
  // IC IVAU     3      7      5      1
  // DC CVAC     3      7      10     1
  // DC CVAP     3      7      12     1
  // DC CVAU     3      7      11     1
  // DC CIVAC    3      7      14     1
  // DC ZVA      3      7      4      1
  // So only deal with the CRm field.
  enum icache_maintenance {IVAU = 0b0101};
  enum dcache_maintenance {CVAC = 0b1010, CVAP = 0b1100, CVAU = 0b1011, CIVAC = 0b1110, ZVA = 0b100};

  void dc(dcache_maintenance cm, Register Rt) {
    sys(0b011, 0b0111, cm, 0b001, Rt);
  }

  void ic(icache_maintenance cm, Register Rt) {
    sys(0b011, 0b0111, cm, 0b001, Rt);
  }

  // A more convenient access to dmb for our purposes
  enum Membar_mask_bits {
    // We can use ISH for a barrier because the Arm ARM says "This
    // architecture assumes that all Processing Elements that use the
    // same operating system or hypervisor are in the same Inner
    // Shareable shareability domain."
    StoreStore = ISHST,
    LoadStore  = ISHLD,
    LoadLoad   = ISHLD,
    StoreLoad  = ISH,
    AnyAny     = ISH
  };

  void membar(Membar_mask_bits order_constraint) {
    dmb(Assembler::barrier(order_constraint));
  }

  // Unconditional branch (register)

  void branch_reg(int OP, int A, int M, Register RN, Register RM) {
    starti;
    f(0b1101011, 31, 25);
    f(OP, 24, 21);
    f(0b111110000, 20, 12);
    f(A, 11, 11);
    f(M, 10, 10);
    rf(RN, 5);
    rf(RM, 0);
  }

#define INSN(NAME, opc)                         \
  void NAME(Register RN) {                      \
    branch_reg(opc, 0, 0, RN, r0);              \
  }

  INSN(br,  0b0000);
  INSN(blr, 0b0001);
  INSN(ret, 0b0010);

  void ret(void *p); // This forces a compile-time error for ret(0)

#undef INSN

#define INSN(NAME, opc)                         \
  void NAME() {                                 \
    branch_reg(opc, 0, 0, dummy_reg, r0);       \
  }

  INSN(eret, 0b0100);
  INSN(drps, 0b0101);

#undef INSN

#define INSN(NAME, M)                                  \
  void NAME() {                                        \
    branch_reg(0b0010, 1, M, dummy_reg, dummy_reg);    \
  }

  INSN(retaa, 0);
  INSN(retab, 1);

#undef INSN

#define INSN(NAME, OP, M)                   \
  void NAME(Register rn) {                  \
    branch_reg(OP, 1, M, rn, dummy_reg);    \
  }

  INSN(braaz,  0b0000, 0);
  INSN(brabz,  0b0000, 1);
  INSN(blraaz, 0b0001, 0);
  INSN(blrabz, 0b0001, 1);

#undef INSN

#define INSN(NAME, OP, M)                  \
  void NAME(Register rn, Register rm) {    \
    branch_reg(OP, 1, M, rn, rm);          \
  }

  INSN(braa,  0b1000, 0);
  INSN(brab,  0b1000, 1);
  INSN(blraa, 0b1001, 0);
  INSN(blrab, 0b1001, 1);

#undef INSN

  // Load/store exclusive
  enum operand_size { byte, halfword, word, xword };

  void load_store_exclusive(Register Rs, Register Rt1, Register Rt2,
    Register Rn, enum operand_size sz, int op, bool ordered) {
    starti;
    f(sz, 31, 30), f(0b001000, 29, 24), f(op, 23, 21);
    rf(Rs, 16), f(ordered, 15), zrf(Rt2, 10), srf(Rn, 5), zrf(Rt1, 0);
  }

  void load_exclusive(Register dst, Register addr,
                      enum operand_size sz, bool ordered) {
    load_store_exclusive(dummy_reg, dst, dummy_reg, addr,
                         sz, 0b010, ordered);
  }

  void store_exclusive(Register status, Register new_val, Register addr,
                       enum operand_size sz, bool ordered) {
    load_store_exclusive(status, new_val, dummy_reg, addr,
                         sz, 0b000, ordered);
  }

#define INSN4(NAME, sz, op, o0) /* Four registers */                    \
  void NAME(Register Rs, Register Rt1, Register Rt2, Register Rn) {     \
    guarantee(Rs != Rn && Rs != Rt1 && Rs != Rt2, "unpredictable instruction"); \
    load_store_exclusive(Rs, Rt1, Rt2, Rn, sz, op, o0);                 \
  }

#define INSN3(NAME, sz, op, o0) /* Three registers */                   \
  void NAME(Register Rs, Register Rt, Register Rn) {                    \
    guarantee(Rs != Rn && Rs != Rt, "unpredictable instruction");       \
    load_store_exclusive(Rs, Rt, dummy_reg, Rn, sz, op, o0); \
  }

#define INSN2(NAME, sz, op, o0) /* Two registers */                     \
  void NAME(Register Rt, Register Rn) {                                 \
    load_store_exclusive(dummy_reg, Rt, dummy_reg, \
                         Rn, sz, op, o0);                               \
  }

#define INSN_FOO(NAME, sz, op, o0) /* Three registers, encoded differently */ \
  void NAME(Register Rt1, Register Rt2, Register Rn) {                  \
    guarantee(Rt1 != Rt2, "unpredictable instruction");                 \
    load_store_exclusive(dummy_reg, Rt1, Rt2, Rn, sz, op, o0);          \
  }

  // bytes
  INSN3(stxrb,  byte, 0b000, 0);
  INSN3(stlxrb, byte, 0b000, 1);
  INSN2(ldxrb,  byte, 0b010, 0);
  INSN2(ldaxrb, byte, 0b010, 1);
  INSN2(stlrb,  byte, 0b100, 1);
  INSN2(ldarb,  byte, 0b110, 1);

  // halfwords
  INSN3(stxrh,  halfword, 0b000, 0);
  INSN3(stlxrh, halfword, 0b000, 1);
  INSN2(ldxrh,  halfword, 0b010, 0);
  INSN2(ldaxrh, halfword, 0b010, 1);
  INSN2(stlrh,  halfword, 0b100, 1);
  INSN2(ldarh,  halfword, 0b110, 1);

  // words
  INSN3(stxrw,  word, 0b000, 0);
  INSN3(stlxrw, word, 0b000, 1);
  INSN4(stxpw,  word, 0b001, 0);
  INSN4(stlxpw, word, 0b001, 1);
  INSN2(ldxrw,  word, 0b010, 0);
  INSN2(ldaxrw, word, 0b010, 1);
  INSN2(stlrw,  word, 0b100, 1);
  INSN2(ldarw,  word, 0b110, 1);
  // pairs of words
  INSN_FOO(ldxpw,  word, 0b011, 0);
  INSN_FOO(ldaxpw, word, 0b011, 1);

  // xwords
  INSN3(stxr,  xword, 0b000, 0);
  INSN3(stlxr, xword, 0b000, 1);
  INSN4(stxp,  xword, 0b001, 0);
  INSN4(stlxp, xword, 0b001, 1);
  INSN2(ldxr,  xword, 0b010, 0);
  INSN2(ldaxr, xword, 0b010, 1);
  INSN2(stlr,  xword, 0b100, 1);
  INSN2(ldar,  xword, 0b110, 1);
  // pairs of xwords
  INSN_FOO(ldxp,  xword, 0b011, 0);
  INSN_FOO(ldaxp, xword, 0b011, 1);

#undef INSN2
#undef INSN3
#undef INSN4
#undef INSN_FOO

  // 8.1 Compare and swap extensions
  void lse_cas(Register Rs, Register Rt, Register Rn,
                        enum operand_size sz, bool a, bool r, bool not_pair) {
    starti;
    if (! not_pair) { // Pair
      assert(sz == word || sz == xword, "invalid size");
      /* The size bit is in bit 30, not 31 */
      sz = (operand_size)(sz == word ? 0b00:0b01);
    }
    f(sz, 31, 30), f(0b001000, 29, 24), f(not_pair ? 1 : 0, 23), f(a, 22), f(1, 21);
    zrf(Rs, 16), f(r, 15), f(0b11111, 14, 10), srf(Rn, 5), zrf(Rt, 0);
  }

  // CAS
#define INSN(NAME, a, r)                                                \
  void NAME(operand_size sz, Register Rs, Register Rt, Register Rn) {   \
    assert(Rs != Rn && Rs != Rt, "unpredictable instruction");          \
    lse_cas(Rs, Rt, Rn, sz, a, r, true);                                \
  }
  INSN(cas,   false, false)
  INSN(casa,  true,  false)
  INSN(casl,  false, true)
  INSN(casal, true,  true)
#undef INSN

  // CASP
#define INSN(NAME, a, r)                                                \
  void NAME(operand_size sz, Register Rs, Register Rs1,                 \
            Register Rt, Register Rt1, Register Rn) {                   \
    assert((Rs->encoding() & 1) == 0 && (Rt->encoding() & 1) == 0 &&    \
           Rs->successor() == Rs1 && Rt->successor() == Rt1 &&          \
           Rs != Rn && Rs1 != Rn && Rs != Rt, "invalid registers");     \
    lse_cas(Rs, Rt, Rn, sz, a, r, false);                               \
  }
  INSN(casp,   false, false)
  INSN(caspa,  true,  false)
  INSN(caspl,  false, true)
  INSN(caspal, true,  true)
#undef INSN

  // 8.1 Atomic operations
  void lse_atomic(Register Rs, Register Rt, Register Rn,
                  enum operand_size sz, int op1, int op2, bool a, bool r) {
    starti;
    f(sz, 31, 30), f(0b111000, 29, 24), f(a, 23), f(r, 22), f(1, 21);
    zrf(Rs, 16), f(op1, 15), f(op2, 14, 12), f(0, 11, 10), srf(Rn, 5), zrf(Rt, 0);
  }

#define INSN(NAME, NAME_A, NAME_L, NAME_AL, op1, op2)                   \
  void NAME(operand_size sz, Register Rs, Register Rt, Register Rn) {   \
    lse_atomic(Rs, Rt, Rn, sz, op1, op2, false, false);                 \
  }                                                                     \
  void NAME_A(operand_size sz, Register Rs, Register Rt, Register Rn) { \
    lse_atomic(Rs, Rt, Rn, sz, op1, op2, true, false);                  \
  }                                                                     \
  void NAME_L(operand_size sz, Register Rs, Register Rt, Register Rn) { \
    lse_atomic(Rs, Rt, Rn, sz, op1, op2, false, true);                  \
  }                                                                     \
  void NAME_AL(operand_size sz, Register Rs, Register Rt, Register Rn) {\
    lse_atomic(Rs, Rt, Rn, sz, op1, op2, true, true);                   \
  }
  INSN(ldadd,  ldadda,  ldaddl,  ldaddal,  0, 0b000);
  INSN(ldbic,  ldbica,  ldbicl,  ldbical,  0, 0b001);
  INSN(ldeor,  ldeora,  ldeorl,  ldeoral,  0, 0b010);
  INSN(ldorr,  ldorra,  ldorrl,  ldorral,  0, 0b011);
  INSN(ldsmax, ldsmaxa, ldsmaxl, ldsmaxal, 0, 0b100);
  INSN(ldsmin, ldsmina, ldsminl, ldsminal, 0, 0b101);
  INSN(ldumax, ldumaxa, ldumaxl, ldumaxal, 0, 0b110);
  INSN(ldumin, ldumina, lduminl, lduminal, 0, 0b111);
  INSN(swp,    swpa,    swpl,    swpal,    1, 0b000);
#undef INSN

  // Load register (literal)
#define INSN(NAME, opc, V)                                              \
  void NAME(Register Rt, address dest) {                                \
    int64_t offset = (dest - pc()) >> 2;                                \
    starti;                                                             \
    f(opc, 31, 30), f(0b011, 29, 27), f(V, 26), f(0b00, 25, 24),        \
      sf(offset, 23, 5);                                                \
    rf(Rt, 0);                                                          \
  }                                                                     \
  void NAME(Register Rt, address dest, relocInfo::relocType rtype) {    \
    InstructionMark im(this);                                           \
    guarantee(rtype == relocInfo::internal_word_type,                   \
              "only internal_word_type relocs make sense here");        \
    code_section()->relocate(inst_mark(), InternalAddress(dest).rspec()); \
    NAME(Rt, dest);                                                     \
  }                                                                     \
  void NAME(Register Rt, Label &L) {                                    \
    wrap_label(Rt, L, &Assembler::NAME);                                \
  }

  INSN(ldrw, 0b00, 0);
  INSN(ldr, 0b01, 0);
  INSN(ldrsw, 0b10, 0);

#undef INSN

#define INSN(NAME, opc, V)                                              \
  void NAME(FloatRegister Rt, address dest) {                           \
    int64_t offset = (dest - pc()) >> 2;                                \
    starti;                                                             \
    f(opc, 31, 30), f(0b011, 29, 27), f(V, 26), f(0b00, 25, 24),        \
      sf(offset, 23, 5);                                                \
    rf(as_Register(Rt), 0);                                             \
  }

  INSN(ldrs, 0b00, 1);
  INSN(ldrd, 0b01, 1);
  INSN(ldrq, 0b10, 1);

#undef INSN

#define INSN(NAME, size, opc)                                           \
  void NAME(FloatRegister Rt, Register Rn) {                            \
    starti;                                                             \
    f(size, 31, 30), f(0b111100, 29, 24), f(opc, 23, 22), f(0, 21);     \
    f(0, 20, 12), f(0b01, 11, 10);                                      \
    rf(Rn, 5), rf(as_Register(Rt), 0);                                  \
  }

  INSN(ldrs, 0b10, 0b01);
  INSN(ldrd, 0b11, 0b01);
  INSN(ldrq, 0b00, 0b11);

#undef INSN


#define INSN(NAME, opc, V)                                              \
  void NAME(address dest, prfop op = PLDL1KEEP) {                       \
    int64_t offset = (dest - pc()) >> 2;                                \
    starti;                                                             \
    f(opc, 31, 30), f(0b011, 29, 27), f(V, 26), f(0b00, 25, 24),        \
      sf(offset, 23, 5);                                                \
    f(op, 4, 0);                                                        \
  }                                                                     \
  void NAME(Label &L, prfop op = PLDL1KEEP) {                           \
    wrap_label(L, op, &Assembler::NAME);                                \
  }

  INSN(prfm, 0b11, 0);

#undef INSN

  // Load/store
  void ld_st1(int opc, int p1, int V, int L,
              Register Rt1, Register Rt2, Address adr, bool no_allocate) {
    starti;
    f(opc, 31, 30), f(p1, 29, 27), f(V, 26), f(L, 22);
    zrf(Rt2, 10), zrf(Rt1, 0);
    if (no_allocate) {
      adr.encode_nontemporal_pair(&current_insn);
    } else {
      adr.encode_pair(&current_insn);
    }
  }

  // Load/store register pair (offset)
#define INSN(NAME, size, p1, V, L, no_allocate)         \
  void NAME(Register Rt1, Register Rt2, Address adr) {  \
    ld_st1(size, p1, V, L, Rt1, Rt2, adr, no_allocate); \
   }

  INSN(stpw,  0b00, 0b101, 0, 0, false);
  INSN(ldpw,  0b00, 0b101, 0, 1, false);
  INSN(ldpsw, 0b01, 0b101, 0, 1, false);
  INSN(stp,   0b10, 0b101, 0, 0, false);
  INSN(ldp,   0b10, 0b101, 0, 1, false);

  // Load/store no-allocate pair (offset)
  INSN(stnpw, 0b00, 0b101, 0, 0, true);
  INSN(ldnpw, 0b00, 0b101, 0, 1, true);
  INSN(stnp,  0b10, 0b101, 0, 0, true);
  INSN(ldnp,  0b10, 0b101, 0, 1, true);

#undef INSN

#define INSN(NAME, size, p1, V, L, no_allocate)                         \
  void NAME(FloatRegister Rt1, FloatRegister Rt2, Address adr) {        \
    ld_st1(size, p1, V, L,                                              \
           as_Register(Rt1), as_Register(Rt2), adr, no_allocate);       \
   }

  INSN(stps, 0b00, 0b101, 1, 0, false);
  INSN(ldps, 0b00, 0b101, 1, 1, false);
  INSN(stpd, 0b01, 0b101, 1, 0, false);
  INSN(ldpd, 0b01, 0b101, 1, 1, false);
  INSN(stpq, 0b10, 0b101, 1, 0, false);
  INSN(ldpq, 0b10, 0b101, 1, 1, false);

#undef INSN

  // Load/store register (all modes)
  void ld_st2(Register Rt, const Address &adr, int size, int op, int V = 0) {
    starti;

    f(V, 26); // general reg?
    zrf(Rt, 0);

    // Encoding for literal loads is done here (rather than pushed
    // down into Address::encode) because the encoding of this
    // instruction is too different from all of the other forms to
    // make it worth sharing.
    if (adr.getMode() == Address::literal) {
      assert(size == 0b10 || size == 0b11, "bad operand size in ldr");
      assert(op == 0b01, "literal form can only be used with loads");
      f(size & 0b01, 31, 30), f(0b011, 29, 27), f(0b00, 25, 24);
      int64_t offset = (adr.target() - pc()) >> 2;
      sf(offset, 23, 5);
      code_section()->relocate(pc(), adr.rspec());
      return;
    }

    f(size, 31, 30);
    f(op, 23, 22); // str
    adr.encode(&current_insn);
  }

#define INSN(NAME, size, op)                            \
  void NAME(Register Rt, const Address &adr) {          \
    ld_st2(Rt, adr, size, op);                          \
  }                                                     \

  INSN(str,  0b11, 0b00);
  INSN(strw, 0b10, 0b00);
  INSN(strb, 0b00, 0b00);
  INSN(strh, 0b01, 0b00);

  INSN(ldr,  0b11, 0b01);
  INSN(ldrw, 0b10, 0b01);
  INSN(ldrb, 0b00, 0b01);
  INSN(ldrh, 0b01, 0b01);

  INSN(ldrsb,  0b00, 0b10);
  INSN(ldrsbw, 0b00, 0b11);
  INSN(ldrsh,  0b01, 0b10);
  INSN(ldrshw, 0b01, 0b11);
  INSN(ldrsw,  0b10, 0b10);

#undef INSN

#define INSN(NAME, size, op)                                    \
  void NAME(const Address &adr, prfop pfop = PLDL1KEEP) {       \
    ld_st2(as_Register(pfop), adr, size, op);                   \
  }

  INSN(prfm, 0b11, 0b10); // FIXME: PRFM should not be used with
                          // writeback modes, but the assembler
                          // doesn't enfore that.

#undef INSN

#define INSN(NAME, size, op)                            \
  void NAME(FloatRegister Rt, const Address &adr) {     \
    ld_st2(as_Register(Rt), adr, size, op, 1);          \
  }

  INSN(strd, 0b11, 0b00);
  INSN(strs, 0b10, 0b00);
  INSN(ldrd, 0b11, 0b01);
  INSN(ldrs, 0b10, 0b01);
  INSN(strq, 0b00, 0b10);
  INSN(ldrq, 0x00, 0b11);

#undef INSN

/* SIMD extensions
 *
 * We just use FloatRegister in the following. They are exactly the same
 * as SIMD registers.
 */
public:

  enum SIMD_Arrangement {
    T8B, T16B, T4H, T8H, T2S, T4S, T1D, T2D, T1Q, INVALID_ARRANGEMENT
  };

  enum SIMD_RegVariant {
      B, H, S, D, Q, INVALID
  };

private:

  static SIMD_Arrangement _esize2arrangement_table[9][2];
  static SIMD_RegVariant _esize2regvariant[9];

public:

  static SIMD_Arrangement esize2arrangement(unsigned esize, bool isQ);
  static SIMD_RegVariant elemType_to_regVariant(BasicType bt);
  static SIMD_RegVariant elemBytes_to_regVariant(unsigned esize);
  // Return the corresponding bits for different SIMD_RegVariant value.
  static unsigned regVariant_to_elemBits(SIMD_RegVariant T);

  enum shift_kind { LSL, LSR, ASR, ROR };

  void op_shifted_reg(Instruction_aarch64 &current_insn, unsigned decode,
                      enum shift_kind kind, unsigned shift,
                      unsigned size, unsigned op) {
    f(size, 31);
    f(op, 30, 29);
    f(decode, 28, 24);
    f(shift, 15, 10);
    f(kind, 23, 22);
  }

  // Logical (shifted register)
#define INSN(NAME, size, op, N)                                         \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
            enum shift_kind kind = LSL, unsigned shift = 0) {           \
    starti;                                                             \
    guarantee(size == 1 || shift < 32, "incorrect shift");              \
    f(N, 21);                                                           \
    zrf(Rm, 16), zrf(Rn, 5), zrf(Rd, 0);                                \
    op_shifted_reg(current_insn, 0b01010, kind, shift, size, op);       \
  }

  INSN(andr,  1, 0b00, 0);
  INSN(orr,   1, 0b01, 0);
  INSN(eor,   1, 0b10, 0);
  INSN(ands,  1, 0b11, 0);
  INSN(andw,  0, 0b00, 0);
  INSN(orrw,  0, 0b01, 0);
  INSN(eorw,  0, 0b10, 0);
  INSN(andsw, 0, 0b11, 0);

#undef INSN

#define INSN(NAME, size, op, N)                                         \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
            enum shift_kind kind = LSL, unsigned shift = 0) {           \
    starti;                                                             \
    f(N, 21);                                                           \
    zrf(Rm, 16), zrf(Rn, 5), zrf(Rd, 0);                                \
    op_shifted_reg(current_insn, 0b01010, kind, shift, size, op);       \
  }                                                                     \
                                                                        \
  /* These instructions have no immediate form. Provide an overload so  \
     that if anyone does try to use an immediate operand -- this has    \
     happened! -- we'll get a compile-time error. */                    \
  void NAME(Register Rd, Register Rn, unsigned imm,                     \
            enum shift_kind kind = LSL, unsigned shift = 0) {           \
    assert(false, " can't be used with immediate operand");             \
  }

  INSN(bic,   1, 0b00, 1);
  INSN(orn,   1, 0b01, 1);
  INSN(eon,   1, 0b10, 1);
  INSN(bics,  1, 0b11, 1);
  INSN(bicw,  0, 0b00, 1);
  INSN(ornw,  0, 0b01, 1);
  INSN(eonw,  0, 0b10, 1);
  INSN(bicsw, 0, 0b11, 1);

#undef INSN

#ifdef _WIN64
// In MSVC, `mvn` is defined as a macro and it affects compilation
#undef mvn
#endif

  // Aliases for short forms of orn
void mvn(Register Rd, Register Rm,
            enum shift_kind kind = LSL, unsigned shift = 0) {
  orn(Rd, zr, Rm, kind, shift);
}

void mvnw(Register Rd, Register Rm,
            enum shift_kind kind = LSL, unsigned shift = 0) {
  ornw(Rd, zr, Rm, kind, shift);
}

  // Add/subtract (shifted register)
#define INSN(NAME, size, op)                            \
  void NAME(Register Rd, Register Rn, Register Rm,      \
            enum shift_kind kind, unsigned shift = 0) { \
    starti;                                             \
    f(0, 21);                                           \
    assert_cond(kind != ROR);                           \
    guarantee(size == 1 || shift < 32, "incorrect shift");\
    zrf(Rd, 0), zrf(Rn, 5), zrf(Rm, 16);                \
    op_shifted_reg(current_insn, 0b01011, kind, shift, size, op);      \
  }

  INSN(add,  1, 0b000);
  INSN(sub,  1, 0b10);
  INSN(addw, 0, 0b000);
  INSN(subw, 0, 0b10);

  INSN(adds,  1, 0b001);
  INSN(subs,  1, 0b11);
  INSN(addsw, 0, 0b001);
  INSN(subsw, 0, 0b11);

#undef INSN

  // Add/subtract (extended register)
#define INSN(NAME, op)                                                  \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    starti;                                                             \
    zrf(Rm, 16), srf(Rn, 5), srf(Rd, 0);                                \
    add_sub_extended_reg(current_insn, op, 0b01011, Rd, Rn, Rm, 0b00, option, amount); \
  }

  void add_sub_extended_reg(Instruction_aarch64 &current_insn, unsigned op, unsigned decode,
    Register Rd, Register Rn, Register Rm,
    unsigned opt, ext::operation option, unsigned imm) {
    guarantee(imm <= 4, "shift amount must be <= 4");
    f(op, 31, 29), f(decode, 28, 24), f(opt, 23, 22), f(1, 21);
    f(option, 15, 13), f(imm, 12, 10);
  }

  INSN(addw, 0b000);
  INSN(subw, 0b010);
  INSN(add,  0b100);
  INSN(sub,  0b110);

#undef INSN

#define INSN(NAME, op)                                                  \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    starti;                                                             \
    zrf(Rm, 16), srf(Rn, 5), zrf(Rd, 0);                                \
    add_sub_extended_reg(current_insn, op, 0b01011, Rd, Rn, Rm, 0b00, option, amount); \
  }

  INSN(addsw, 0b001);
  INSN(subsw, 0b011);
  INSN(adds,  0b101);
  INSN(subs,  0b111);

#undef INSN

  // Aliases for short forms of add and sub
#define INSN(NAME)                                      \
  void NAME(Register Rd, Register Rn, Register Rm) {    \
    if (Rd == sp || Rn == sp)                           \
      NAME(Rd, Rn, Rm, ext::uxtx);                      \
    else                                                \
      NAME(Rd, Rn, Rm, LSL);                            \
  }

  INSN(addw);
  INSN(subw);
  INSN(add);
  INSN(sub);

  INSN(addsw);
  INSN(subsw);
  INSN(adds);
  INSN(subs);

#undef INSN

  // Add/subtract (with carry)
  void add_sub_carry(unsigned op, Register Rd, Register Rn, Register Rm) {
    starti;
    f(op, 31, 29);
    f(0b11010000, 28, 21);
    f(0b000000, 15, 10);
    zrf(Rm, 16), zrf(Rn, 5), zrf(Rd, 0);
  }

  #define INSN(NAME, op)                                \
    void NAME(Register Rd, Register Rn, Register Rm) {  \
      add_sub_carry(op, Rd, Rn, Rm);                    \
    }

  INSN(adcw,  0b000);
  INSN(adcsw, 0b001);
  INSN(sbcw,  0b010);
  INSN(sbcsw, 0b011);
  INSN(adc,   0b100);
  INSN(adcs,  0b101);
  INSN(sbc,   0b110);
  INSN(sbcs,  0b111);

#undef INSN

  // Conditional compare (both kinds)
  void conditional_compare(unsigned op, int o1, int o2, int o3,
                           Register Rn, unsigned imm5, unsigned nzcv,
                           unsigned cond) {
    starti;
    f(op, 31, 29);
    f(0b11010010, 28, 21);
    f(cond, 15, 12);
    f(o1, 11);
    f(o2, 10);
    f(o3, 4);
    f(nzcv, 3, 0);
    f(imm5, 20, 16), zrf(Rn, 5);
  }

#define INSN(NAME, op)                                                  \
  void NAME(Register Rn, Register Rm, int imm, Condition cond) {        \
    int regNumber = (Rm == zr ? 31 : Rm->encoding());                   \
    conditional_compare(op, 0, 0, 0, Rn, regNumber, imm, cond);         \
  }                                                                     \
                                                                        \
  void NAME(Register Rn, int imm5, int imm, Condition cond) {           \
    conditional_compare(op, 1, 0, 0, Rn, imm5, imm, cond);              \
  }

  INSN(ccmnw, 0b001);
  INSN(ccmpw, 0b011);
  INSN(ccmn, 0b101);
  INSN(ccmp, 0b111);

#undef INSN

  // Conditional select
  void conditional_select(unsigned op, unsigned op2,
                          Register Rd, Register Rn, Register Rm,
                          unsigned cond) {
    starti;
    f(op, 31, 29);
    f(0b11010100, 28, 21);
    f(cond, 15, 12);
    f(op2, 11, 10);
    zrf(Rm, 16), zrf(Rn, 5), rf(Rd, 0);
  }

#define INSN(NAME, op, op2)                                             \
  void NAME(Register Rd, Register Rn, Register Rm, Condition cond) {    \
    conditional_select(op, op2, Rd, Rn, Rm, cond);                      \
  }

  INSN(cselw,  0b000, 0b00);
  INSN(csincw, 0b000, 0b01);
  INSN(csinvw, 0b010, 0b00);
  INSN(csnegw, 0b010, 0b01);
  INSN(csel,   0b100, 0b00);
  INSN(csinc,  0b100, 0b01);
  INSN(csinv,  0b110, 0b00);
  INSN(csneg,  0b110, 0b01);

#undef INSN

  // Data processing
  void data_processing(Instruction_aarch64 &current_insn, unsigned op29, unsigned opcode,
                       Register Rd, Register Rn) {
    f(op29, 31, 29), f(0b11010110, 28, 21);
    f(opcode, 15, 10);
    rf(Rn, 5), rf(Rd, 0);
  }

  // (1 source)
#define INSN(NAME, op29, opcode2, opcode)                       \
  void NAME(Register Rd, Register Rn) {                         \
    starti;                                                     \
    f(opcode2, 20, 16);                                         \
    data_processing(current_insn, op29, opcode, Rd, Rn);        \
  }

  INSN(rbitw,  0b010, 0b00000, 0b00000);
  INSN(rev16w, 0b010, 0b00000, 0b00001);
  INSN(revw,   0b010, 0b00000, 0b00010);
  INSN(clzw,   0b010, 0b00000, 0b00100);
  INSN(clsw,   0b010, 0b00000, 0b00101);

  INSN(rbit,   0b110, 0b00000, 0b00000);
  INSN(rev16,  0b110, 0b00000, 0b00001);
  INSN(rev32,  0b110, 0b00000, 0b00010);
  INSN(rev,    0b110, 0b00000, 0b00011);
  INSN(clz,    0b110, 0b00000, 0b00100);
  INSN(cls,    0b110, 0b00000, 0b00101);

  // PAC instructions
  INSN(pacia,  0b110, 0b00001, 0b00000);
  INSN(pacib,  0b110, 0b00001, 0b00001);
  INSN(pacda,  0b110, 0b00001, 0b00010);
  INSN(pacdb,  0b110, 0b00001, 0b00011);
  INSN(autia,  0b110, 0b00001, 0b00100);
  INSN(autib,  0b110, 0b00001, 0b00101);
  INSN(autda,  0b110, 0b00001, 0b00110);
  INSN(autdb,  0b110, 0b00001, 0b00111);

#undef INSN

#define INSN(NAME, op29, opcode2, opcode)                       \
  void NAME(Register Rd) {                                      \
    starti;                                                     \
    f(opcode2, 20, 16);                                         \
    data_processing(current_insn, op29, opcode, Rd, dummy_reg); \
  }

  // PAC instructions (with zero modifier)
  INSN(paciza,  0b110, 0b00001, 0b01000);
  INSN(pacizb,  0b110, 0b00001, 0b01001);
  INSN(pacdza,  0b110, 0b00001, 0b01010);
  INSN(pacdzb,  0b110, 0b00001, 0b01011);
  INSN(autiza,  0b110, 0b00001, 0b01100);
  INSN(autizb,  0b110, 0b00001, 0b01101);
  INSN(autdza,  0b110, 0b00001, 0b01110);
  INSN(autdzb,  0b110, 0b00001, 0b01111);
  INSN(xpaci,   0b110, 0b00001, 0b10000);
  INSN(xpacd,   0b110, 0b00001, 0b10001);

#undef INSN

  // Data-processing (2 source)
#define INSN(NAME, op29, opcode)                                \
  void NAME(Register Rd, Register Rn, Register Rm) {            \
    starti;                                                     \
    rf(Rm, 16);                                                 \
    data_processing(current_insn, op29, opcode, Rd, Rn);        \
  }

  INSN(udivw, 0b000, 0b000010);
  INSN(sdivw, 0b000, 0b000011);
  INSN(lslvw, 0b000, 0b001000);
  INSN(lsrvw, 0b000, 0b001001);
  INSN(asrvw, 0b000, 0b001010);
  INSN(rorvw, 0b000, 0b001011);

  INSN(udiv, 0b100, 0b000010);
  INSN(sdiv, 0b100, 0b000011);
  INSN(lslv, 0b100, 0b001000);
  INSN(lsrv, 0b100, 0b001001);
  INSN(asrv, 0b100, 0b001010);
  INSN(rorv, 0b100, 0b001011);

#undef INSN

  // Data-processing (3 source)
  void data_processing(unsigned op54, unsigned op31, unsigned o0,
                       Register Rd, Register Rn, Register Rm,
                       Register Ra) {
    starti;
    f(op54, 31, 29), f(0b11011, 28, 24);
    f(op31, 23, 21), f(o0, 15);
    zrf(Rm, 16), zrf(Ra, 10), zrf(Rn, 5), zrf(Rd, 0);
  }

#define INSN(NAME, op54, op31, o0)                                      \
  void NAME(Register Rd, Register Rn, Register Rm, Register Ra) {       \
    data_processing(op54, op31, o0, Rd, Rn, Rm, Ra);                    \
  }

  INSN(maddw,  0b000, 0b000, 0);
  INSN(msubw,  0b000, 0b000, 1);
  INSN(madd,   0b100, 0b000, 0);
  INSN(msub,   0b100, 0b000, 1);
  INSN(smaddl, 0b100, 0b001, 0);
  INSN(smsubl, 0b100, 0b001, 1);
  INSN(umaddl, 0b100, 0b101, 0);
  INSN(umsubl, 0b100, 0b101, 1);

#undef INSN

#define INSN(NAME, op54, op31, o0)                                      \
  void NAME(Register Rd, Register Rn, Register Rm) {                    \
    data_processing(op54, op31, o0, Rd, Rn, Rm, as_Register(31));       \
  }

  INSN(smulh, 0b100, 0b010, 0);
  INSN(umulh, 0b100, 0b110, 0);

#undef INSN

  // Floating-point data-processing (1 source)
  void data_processing(unsigned type, unsigned opcode,
                       FloatRegister Vd, FloatRegister Vn) {
    starti;
    f(0b000, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21), f(opcode, 20, 15), f(0b10000, 14, 10);
    rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, type, opcode)                        \
  void NAME(FloatRegister Vd, FloatRegister Vn) {       \
    data_processing(type, opcode, Vd, Vn);              \
  }

  INSN(fmovs,  0b00, 0b000000);
  INSN(fabss,  0b00, 0b000001);
  INSN(fnegs,  0b00, 0b000010);
  INSN(fsqrts, 0b00, 0b000011);
  INSN(fcvts,  0b00, 0b000101);   // Single-precision to double-precision
  INSN(fcvths, 0b11, 0b000100);   // Half-precision to single-precision
  INSN(fcvtsh, 0b00, 0b000111);   // Single-precision to half-precision

  INSN(fmovd,  0b01, 0b000000);
  INSN(fabsd,  0b01, 0b000001);
  INSN(fnegd,  0b01, 0b000010);
  INSN(fsqrtd, 0b01, 0b000011);
  INSN(fcvtd,  0b01, 0b000100);   // Double-precision to single-precision

private:
  void _fcvt_narrow_extend(FloatRegister Vd, SIMD_Arrangement Ta,
                           FloatRegister Vn, SIMD_Arrangement Tb, bool do_extend) {
    assert((do_extend && (Tb >> 1) + 1 == (Ta >> 1))
           || (!do_extend && (Ta >> 1) + 1 == (Tb >> 1)), "Incompatible arrangement");
    starti;
    int op30 = (do_extend ? Tb : Ta) & 1;
    int op22 = ((do_extend ? Ta : Tb) >> 1) & 1;
    f(0, 31), f(op30, 30), f(0b0011100, 29, 23), f(op22, 22);
    f(0b100001011, 21, 13), f(do_extend ? 1 : 0, 12), f(0b10, 11, 10);
    rf(Vn, 5), rf(Vd, 0);
  }

public:
  void fcvtl(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb) {
    assert(Tb == T4H || Tb == T8H|| Tb == T2S || Tb == T4S, "invalid arrangement");
    _fcvt_narrow_extend(Vd, Ta, Vn, Tb, true);
  }

  void fcvtn(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb) {
    assert(Ta == T4H || Ta == T8H|| Ta == T2S || Ta == T4S, "invalid arrangement");
    _fcvt_narrow_extend(Vd, Ta, Vn, Tb, false);
  }

#undef INSN

  // Floating-point data-processing (2 source)
  void data_processing(unsigned op31, unsigned type, unsigned opcode,
                       FloatRegister Vd, FloatRegister Vn, FloatRegister Vm) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21), f(opcode, 15, 10);
    rf(Vm, 16), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, op31, type, opcode)                  \
  void NAME(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm) {     \
    data_processing(op31, type, opcode, Vd, Vn, Vm);    \
  }

  INSN(fabds,  0b011, 0b10, 0b110101);
  INSN(fmuls,  0b000, 0b00, 0b000010);
  INSN(fdivs,  0b000, 0b00, 0b000110);
  INSN(fadds,  0b000, 0b00, 0b001010);
  INSN(fsubs,  0b000, 0b00, 0b001110);
  INSN(fmaxs,  0b000, 0b00, 0b010010);
  INSN(fmins,  0b000, 0b00, 0b010110);
  INSN(fnmuls, 0b000, 0b00, 0b100010);

  INSN(fabdd,  0b011, 0b11, 0b110101);
  INSN(fmuld,  0b000, 0b01, 0b000010);
  INSN(fdivd,  0b000, 0b01, 0b000110);
  INSN(faddd,  0b000, 0b01, 0b001010);
  INSN(fsubd,  0b000, 0b01, 0b001110);
  INSN(fmaxd,  0b000, 0b01, 0b010010);
  INSN(fmind,  0b000, 0b01, 0b010110);
  INSN(fnmuld, 0b000, 0b01, 0b100010);

#undef INSN

   // Floating-point data-processing (3 source)
  void data_processing(unsigned op31, unsigned type, unsigned o1, unsigned o0,
                       FloatRegister Vd, FloatRegister Vn, FloatRegister Vm,
                       FloatRegister Va) {
    starti;
    f(op31, 31, 29);
    f(0b11111, 28, 24);
    f(type, 23, 22), f(o1, 21), f(o0, 15);
    rf(Vm, 16), rf(Va, 10), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, op31, type, o1, o0)                                  \
  void NAME(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm,       \
            FloatRegister Va) {                                         \
    data_processing(op31, type, o1, o0, Vd, Vn, Vm, Va);                \
  }

  INSN(fmadds,  0b000, 0b00, 0, 0);
  INSN(fmsubs,  0b000, 0b00, 0, 1);
  INSN(fnmadds, 0b000, 0b00, 1, 0);
  INSN(fnmsubs, 0b000, 0b00, 1, 1);

  INSN(fmaddd,  0b000, 0b01, 0, 0);
  INSN(fmsubd,  0b000, 0b01, 0, 1);
  INSN(fnmaddd, 0b000, 0b01, 1, 0);
  INSN(fnmsub,  0b000, 0b01, 1, 1);

#undef INSN

   // Floating-point conditional select
  void fp_conditional_select(unsigned op31, unsigned type,
                             unsigned op1, unsigned op2,
                             Condition cond, FloatRegister Vd,
                             FloatRegister Vn, FloatRegister Vm) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22);
    f(op1, 21, 21);
    f(op2, 11, 10);
    f(cond, 15, 12);
    rf(Vm, 16), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, op31, type, op1, op2)                                \
  void NAME(FloatRegister Vd, FloatRegister Vn,                         \
            FloatRegister Vm, Condition cond) {                         \
    fp_conditional_select(op31, type, op1, op2, cond, Vd, Vn, Vm);      \
  }

  INSN(fcsels, 0b000, 0b00, 0b1, 0b11);
  INSN(fcseld, 0b000, 0b01, 0b1, 0b11);

#undef INSN

  // Conversion between floating-point and integer
  void float_int_convert(unsigned sflag, unsigned ftype,
                         unsigned rmode, unsigned opcode,
                         Register Rd, Register Rn) {
    starti;
    f(sflag, 31);
    f(0b00, 30, 29);
    f(0b11110, 28, 24);
    f(ftype, 23, 22), f(1, 21), f(rmode, 20, 19);
    f(opcode, 18, 16), f(0b000000, 15, 10);
    zrf(Rn, 5), zrf(Rd, 0);
  }

#define INSN(NAME, sflag, ftype, rmode, opcode)                          \
  void NAME(Register Rd, FloatRegister Vn) {                             \
    float_int_convert(sflag, ftype, rmode, opcode, Rd, as_Register(Vn)); \
  }

  INSN(fcvtzsw, 0b0, 0b00, 0b11, 0b000);
  INSN(fcvtzs,  0b1, 0b00, 0b11, 0b000);
  INSN(fcvtzdw, 0b0, 0b01, 0b11, 0b000);
  INSN(fcvtzd,  0b1, 0b01, 0b11, 0b000);

  // RoundToNearestTiesAway
  INSN(fcvtassw, 0b0, 0b00, 0b00, 0b100);  // float -> signed word
  INSN(fcvtasd,  0b1, 0b01, 0b00, 0b100);  // double -> signed xword

  // RoundTowardsNegative
  INSN(fcvtmssw, 0b0, 0b00, 0b10, 0b000);  // float -> signed word
  INSN(fcvtmsd,  0b1, 0b01, 0b10, 0b000);  // double -> signed xword

  INSN(fmovs, 0b0, 0b00, 0b00, 0b110);
  INSN(fmovd, 0b1, 0b01, 0b00, 0b110);

  INSN(fmovhid, 0b1, 0b10, 0b01, 0b110);

#undef INSN

#define INSN(NAME, sflag, type, rmode, opcode)                          \
  void NAME(FloatRegister Vd, Register Rn) {                            \
    float_int_convert(sflag, type, rmode, opcode, as_Register(Vd), Rn); \
  }

  INSN(fmovs, 0b0, 0b00, 0b00, 0b111);
  INSN(fmovd, 0b1, 0b01, 0b00, 0b111);

  INSN(scvtfws, 0b0, 0b00, 0b00, 0b010);
  INSN(scvtfs,  0b1, 0b00, 0b00, 0b010);
  INSN(scvtfwd, 0b0, 0b01, 0b00, 0b010);
  INSN(scvtfd,  0b1, 0b01, 0b00, 0b010);

  // INSN(fmovhid, 0b100, 0b10, 0b01, 0b111);

#undef INSN

  enum sign_kind { SIGNED, UNSIGNED };

private:
  void _xcvtf_scalar_integer(sign_kind sign, unsigned sz,
                             FloatRegister Rd, FloatRegister Rn) {
    starti;
    f(0b01, 31, 30), f(sign == SIGNED ? 0 : 1, 29);
    f(0b111100, 27, 23), f((sz >> 1) & 1, 22), f(0b100001110110, 21, 10);
    rf(Rn, 5), rf(Rd, 0);
  }

public:
#define INSN(NAME, sign, sz)                        \
  void NAME(FloatRegister Rd, FloatRegister Rn) {   \
    _xcvtf_scalar_integer(sign, sz, Rd, Rn);        \
  }

  INSN(scvtfs, SIGNED, 0);
  INSN(scvtfd, SIGNED, 1);

#undef INSN

private:
  void _xcvtf_vector_integer(sign_kind sign, SIMD_Arrangement T,
                             FloatRegister Rd, FloatRegister Rn) {
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");
    starti;
    f(0, 31), f(T & 1, 30), f(sign == SIGNED ? 0 : 1, 29);
    f(0b011100, 28, 23), f((T >> 1) & 1, 22), f(0b100001110110, 21, 10);
    rf(Rn, 5), rf(Rd, 0);
  }

public:
  void scvtfv(SIMD_Arrangement T, FloatRegister Rd, FloatRegister Rn) {
    _xcvtf_vector_integer(SIGNED, T, Rd, Rn);
  }

  // Floating-point compare
  void float_compare(unsigned op31, unsigned type,
                     unsigned op, unsigned op2,
                     FloatRegister Vn, FloatRegister Vm = as_FloatRegister(0)) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21);
    f(op, 15, 14), f(0b1000, 13, 10), f(op2, 4, 0);
    rf(Vn, 5), rf(Vm, 16);
  }


#define INSN(NAME, op31, type, op, op2)                 \
  void NAME(FloatRegister Vn, FloatRegister Vm) {       \
    float_compare(op31, type, op, op2, Vn, Vm);         \
  }

#define INSN1(NAME, op31, type, op, op2)        \
  void NAME(FloatRegister Vn, double d) {       \
    assert_cond(d == 0.0);                      \
    float_compare(op31, type, op, op2, Vn);     \
  }

  INSN(fcmps, 0b000, 0b00, 0b00, 0b00000);
  INSN1(fcmps, 0b000, 0b00, 0b00, 0b01000);
  // INSN(fcmpes, 0b000, 0b00, 0b00, 0b10000);
  // INSN1(fcmpes, 0b000, 0b00, 0b00, 0b11000);

  INSN(fcmpd, 0b000,   0b01, 0b00, 0b00000);
  INSN1(fcmpd, 0b000,  0b01, 0b00, 0b01000);
  // INSN(fcmped, 0b000,  0b01, 0b00, 0b10000);
  // INSN1(fcmped, 0b000, 0b01, 0b00, 0b11000);

#undef INSN
#undef INSN1

// Floating-point compare. 3-registers versions (scalar).
#define INSN(NAME, sz, e)                                             \
  void NAME(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm) {   \
    starti;                                                           \
    f(0b01111110, 31, 24), f(e, 23), f(sz, 22), f(1, 21), rf(Vm, 16); \
    f(0b111011, 15, 10), rf(Vn, 5), rf(Vd, 0);                        \
  }                                                                   \

  INSN(facged, 1, 0); // facge-double
  INSN(facges, 0, 0); // facge-single
  INSN(facgtd, 1, 1); // facgt-double
  INSN(facgts, 0, 1); // facgt-single

#undef INSN

  // Floating-point Move (immediate)
private:
  unsigned pack(double value);

  void fmov_imm(FloatRegister Vn, double value, unsigned size) {
    starti;
    f(0b00011110, 31, 24), f(size, 23, 22), f(1, 21);
    f(pack(value), 20, 13), f(0b10000000, 12, 5);
    rf(Vn, 0);
  }

public:

  void fmovs(FloatRegister Vn, double value) {
    if (value)
      fmov_imm(Vn, value, 0b00);
    else
      movi(Vn, T2S, 0);
  }
  void fmovd(FloatRegister Vn, double value) {
    if (value)
      fmov_imm(Vn, value, 0b01);
    else
      movi(Vn, T1D, 0);
  }

  // Floating-point data-processing (1 source)

   // Floating-point rounding
   // type: half-precision = 11
   //       single         = 00
   //       double         = 01
   // rmode: A = Away     = 100
   //        I = current  = 111
   //        M = MinusInf = 010
   //        N = eveN     = 000
   //        P = PlusInf  = 001
   //        X = eXact    = 110
   //        Z = Zero     = 011
  void float_round(unsigned type, unsigned rmode, FloatRegister Rd, FloatRegister Rn) {
    starti;
    f(0b00011110, 31, 24);
    f(type, 23, 22);
    f(0b1001, 21, 18);
    f(rmode, 17, 15);
    f(0b10000, 14, 10);
    rf(Rn, 5), rf(Rd, 0);
  }
#define INSN(NAME, type, rmode)                   \
  void NAME(FloatRegister Vd, FloatRegister Vn) { \
    float_round(type, rmode, Vd, Vn);             \
  }

public:
  INSN(frintah, 0b11, 0b100);
  INSN(frintih, 0b11, 0b111);
  INSN(frintmh, 0b11, 0b010);
  INSN(frintnh, 0b11, 0b000);
  INSN(frintph, 0b11, 0b001);
  INSN(frintxh, 0b11, 0b110);
  INSN(frintzh, 0b11, 0b011);

  INSN(frintas, 0b00, 0b100);
  INSN(frintis, 0b00, 0b111);
  INSN(frintms, 0b00, 0b010);
  INSN(frintns, 0b00, 0b000);
  INSN(frintps, 0b00, 0b001);
  INSN(frintxs, 0b00, 0b110);
  INSN(frintzs, 0b00, 0b011);

  INSN(frintad, 0b01, 0b100);
  INSN(frintid, 0b01, 0b111);
  INSN(frintmd, 0b01, 0b010);
  INSN(frintnd, 0b01, 0b000);
  INSN(frintpd, 0b01, 0b001);
  INSN(frintxd, 0b01, 0b110);
  INSN(frintzd, 0b01, 0b011);
#undef INSN

private:
  static short SIMD_Size_in_bytes[];

public:
#define INSN(NAME, op)                                                  \
  void NAME(FloatRegister Rt, SIMD_RegVariant T, const Address &adr) {  \
    ld_st2(as_Register(Rt), adr, (int)T & 3, op + ((T==Q) ? 0b10:0b00), 1); \
  }

  INSN(ldr, 1);
  INSN(str, 0);

#undef INSN

 private:

  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn, int op1, int op2) {
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1, 29, 21), f(0, 20, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), srf(Xn, 5), rf(Vt, 0);
  }
  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn,
             int imm, int op1, int op2, int regs) {

    bool replicate = op2 >> 2 == 3;
    // post-index value (imm) is formed differently for replicate/non-replicate ld* instructions
    int expectedImmediate = replicate ? regs * (1 << (T >> 1)) : SIMD_Size_in_bytes[T] * regs;
    guarantee(T < T1Q , "incorrect arrangement");
    guarantee(imm == expectedImmediate, "bad offset");
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1 | 0b100, 29, 21), f(0b11111, 20, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), srf(Xn, 5), rf(Vt, 0);
  }
  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn,
             Register Xm, int op1, int op2) {
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1 | 0b100, 29, 21), rf(Xm, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), srf(Xn, 5), rf(Vt, 0);
  }

  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Address a, int op1, int op2, int regs) {
    switch (a.getMode()) {
    case Address::base_plus_offset:
      guarantee(a.offset() == 0, "no offset allowed here");
      ld_st(Vt, T, a.base(), op1, op2);
      break;
    case Address::post:
      ld_st(Vt, T, a.base(), checked_cast<int>(a.offset()), op1, op2, regs);
      break;
    case Address::post_reg:
      ld_st(Vt, T, a.base(), a.index(), op1, op2);
      break;
    default:
      ShouldNotReachHere();
    }
  }

  // Single-structure load/store method (all addressing variants)
  void ld_st(FloatRegister Vt, SIMD_RegVariant T, int index, Address a,
             int op1, int op2, int regs) {
    int expectedImmediate = (regVariant_to_elemBits(T) >> 3) * regs;
    int sVal = (T < D) ? (index >> (2 - T)) & 0x01 : 0;
    int opcode = (T < D) ? (T << 2) : ((T & 0x02) << 2);
    int size = (T < D) ? (index & (0x3 << T)) : 1;  // only care about low 2b
    Register Xn = a.base();
    int Rm;

    switch (a.getMode()) {
    case Address::base_plus_offset:
      guarantee(a.offset() == 0, "no offset allowed here");
      Rm = 0;
      break;
    case Address::post:
      guarantee(a.offset() == expectedImmediate, "bad offset");
      op1 |= 0b100;
      Rm = 0b11111;
      break;
    case Address::post_reg:
      op1 |= 0b100;
      Rm = a.index()->encoding();
      break;
    default:
      ShouldNotReachHere();
      Rm = 0;  // unreachable
    }

    starti;
    f(0,31), f((index >> (3 - T)), 30);
    f(op1, 29, 21), f(Rm, 20, 16), f(op2 | opcode | sVal, 15, 12);
    f(size, 11, 10), srf(Xn, 5), rf(Vt, 0);
  }

 public:

#define INSN1(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, SIMD_Arrangement T, const Address &a) {   \
    ld_st(Vt, T, a, op1, op2, 1);                                       \
 }

#define INSN2(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, SIMD_Arrangement T, const Address &a) { \
    assert(Vt->successor() == Vt2, "Registers must be ordered");        \
    ld_st(Vt, T, a, op1, op2, 2);                                       \
  }

#define INSN3(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            SIMD_Arrangement T, const Address &a) {                     \
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3,           \
           "Registers must be ordered");                                \
    ld_st(Vt, T, a, op1, op2, 3);                                       \
  }

#define INSN4(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            FloatRegister Vt4, SIMD_Arrangement T, const Address &a) {  \
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3 &&         \
           Vt3->successor() == Vt4, "Registers must be ordered");       \
    ld_st(Vt, T, a, op1, op2, 4);                                       \
  }

  INSN1(ld1,  0b001100010, 0b0111);
  INSN2(ld1,  0b001100010, 0b1010);
  INSN3(ld1,  0b001100010, 0b0110);
  INSN4(ld1,  0b001100010, 0b0010);

  INSN2(ld2,  0b001100010, 0b1000);
  INSN3(ld3,  0b001100010, 0b0100);
  INSN4(ld4,  0b001100010, 0b0000);

  INSN1(st1,  0b001100000, 0b0111);
  INSN2(st1,  0b001100000, 0b1010);
  INSN3(st1,  0b001100000, 0b0110);
  INSN4(st1,  0b001100000, 0b0010);

  INSN2(st2,  0b001100000, 0b1000);
  INSN3(st3,  0b001100000, 0b0100);
  INSN4(st4,  0b001100000, 0b0000);

  INSN1(ld1r, 0b001101010, 0b1100);
  INSN2(ld2r, 0b001101011, 0b1100);
  INSN3(ld3r, 0b001101010, 0b1110);
  INSN4(ld4r, 0b001101011, 0b1110);

#undef INSN1
#undef INSN2
#undef INSN3
#undef INSN4

// Handle common single-structure ld/st parameter sanity checks
// for all variations (1 to 4) of SIMD reigster inputs.  This
// method will call the routine that generates the opcode.
template<typename R, typename... Rx>
  void ldst_sstr(SIMD_RegVariant T, int index, const Address &a,
            int op1, int op2, R firstReg, Rx... otherRegs) {
    const FloatRegister vtSet[] = { firstReg, otherRegs... };
    const int regCount = sizeof...(otherRegs) + 1;
    assert(index >= 0 && (T <= D) && ((T == B && index <= 15) ||
              (T == H && index <= 7) || (T == S && index <= 3) ||
              (T == D && index <= 1)), "invalid index");
    assert(regCount >= 1 && regCount <= 4, "illegal register count");

    // Check to make sure when multiple SIMD registers are used
    // that they are in successive order.
    for (int i = 0; i < regCount - 1; i++) {
      assert(vtSet[i]->successor() == vtSet[i + 1],
             "Registers must be ordered");
    }

    ld_st(firstReg, T, index, a, op1, op2, regCount);
  }

// Define a set of INSN1/2/3/4 macros to handle single-structure
// load/store instructions.
#define INSN1(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, SIMD_RegVariant T, int index,             \
            const Address &a) {                                         \
    ldst_sstr(T, index, a, op1, op2, Vt);                               \
 }

#define INSN2(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, SIMD_RegVariant T,     \
            int index, const Address &a) {                              \
    ldst_sstr(T, index, a, op1, op2, Vt, Vt2);                          \
  }

#define INSN3(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            SIMD_RegVariant T, int index, const Address &a) {           \
    ldst_sstr(T, index, a, op1, op2, Vt, Vt2, Vt3);                     \
  }

#define INSN4(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            FloatRegister Vt4, SIMD_RegVariant T, int index,            \
            const Address &a) {                                         \
    ldst_sstr(T, index, a, op1, op2, Vt, Vt2, Vt3, Vt4);                \
  }

  INSN1(st1, 0b001101000, 0b0000);
  INSN2(st2, 0b001101001, 0b0000);
  INSN3(st3, 0b001101000, 0b0010);
  INSN4(st4, 0b001101001, 0b0010);

#undef INSN1
#undef INSN2
#undef INSN3
#undef INSN4

#define INSN(NAME, opc)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    assert(T == T8B || T == T16B, "must be T8B or T16B");                               \
    f(0, 31), f((int)T & 1, 30), f(opc, 29, 21);                                        \
    rf(Vm, 16), f(0b000111, 15, 10), rf(Vn, 5), rf(Vd, 0);                              \
  }

  INSN(eor,  0b101110001);
  INSN(orr,  0b001110101);
  INSN(andr, 0b001110001);
  INSN(bic,  0b001110011);
  INSN(bif,  0b101110111);
  INSN(bit,  0b101110101);
  INSN(bsl,  0b101110011);
  INSN(orn,  0b001110111);

#undef INSN

  // Advanced SIMD three different
#define INSN(NAME, opc, opc2, acceptT2D)                                                \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    guarantee(T != T1Q && T != T1D, "incorrect arrangement");                           \
    if (!acceptT2D) guarantee(T != T2D, "incorrect arrangement");                       \
    starti;                                                                             \
    f(0, 31), f((int)T & 1, 30), f(opc, 29), f(0b01110, 28, 24);                        \
    f((int)T >> 1, 23, 22), f(1, 21), rf(Vm, 16), f(opc2, 15, 10);                      \
    rf(Vn, 5), rf(Vd, 0);                                                               \
  }

  INSN(addv,   0, 0b100001, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(subv,   1, 0b100001, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(uqsubv, 1, 0b001011, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(mulv,   0, 0b100111, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(mlav,   0, 0b100101, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(mlsv,   1, 0b100101, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(sshl,   0, 0b010001, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(ushl,   1, 0b010001, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(addpv,  0, 0b101111, true);  // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(smullv, 0, 0b110000, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(umullv, 1, 0b110000, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(umlalv, 1, 0b100000, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(maxv,   0, 0b011001, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(minv,   0, 0b011011, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(smaxp,  0, 0b101001, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(sminp,  0, 0b101011, false); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S

#undef INSN

#define INSN(NAME, opc, opc2, accepted) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {                   \
    guarantee(T != T1Q && T != T1D, "incorrect arrangement");                           \
    if (accepted < 3) guarantee(T != T2D, "incorrect arrangement");                     \
    if (accepted < 2) guarantee(T != T2S, "incorrect arrangement");                     \
    if (accepted < 1) guarantee(T == T8B || T == T16B, "incorrect arrangement");        \
    starti;                                                                             \
    f(0, 31), f((int)T & 1, 30), f(opc, 29), f(0b01110, 28, 24);                        \
    f((int)T >> 1, 23, 22), f(opc2, 21, 10);                                            \
    rf(Vn, 5), rf(Vd, 0);                                                               \
  }

  INSN(absr,   0, 0b100000101110, 3); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(negr,   1, 0b100000101110, 3); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S, T2D
  INSN(notr,   1, 0b100000010110, 0); // accepted arrangements: T8B, T16B
  INSN(addv,   0, 0b110001101110, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S
  INSN(smaxv,  0, 0b110000101010, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S
  INSN(umaxv,  1, 0b110000101010, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S
  INSN(sminv,  0, 0b110001101010, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S
  INSN(uminv,  1, 0b110001101010, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S
  INSN(cls,    0, 0b100000010010, 2); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(clz,    1, 0b100000010010, 2); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(cnt,    0, 0b100000010110, 0); // accepted arrangements: T8B, T16B
  INSN(uaddlp, 1, 0b100000001010, 2); // accepted arrangements: T8B, T16B, T4H, T8H, T2S, T4S
  INSN(uaddlv, 1, 0b110000001110, 1); // accepted arrangements: T8B, T16B, T4H, T8H,      T4S

#undef INSN

#define INSN(NAME, opc) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {                  \
    starti;                                                                            \
    assert(T == T4S, "arrangement must be T4S");                                       \
    f(0, 31), f((int)T & 1, 30), f(0b101110, 29, 24), f(opc, 23),                      \
    f(T == T4S ? 0 : 1, 22), f(0b110000111110, 21, 10); rf(Vn, 5), rf(Vd, 0);          \
  }

  INSN(fmaxv, 0);
  INSN(fminv, 1);

#undef INSN

// Advanced SIMD modified immediate
#define INSN(NAME, op0, cmode0) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, unsigned imm8, unsigned lsl = 0) {   \
    unsigned cmode = cmode0;                                                           \
    unsigned op = op0;                                                                 \
    starti;                                                                            \
    assert(lsl == 0 ||                                                                 \
           ((T == T4H || T == T8H) && lsl == 8) ||                                     \
           ((T == T2S || T == T4S) && ((lsl >> 3) < 4) && ((lsl & 7) == 0)), "invalid shift");\
    cmode |= lsl >> 2;                                                                 \
    if (T == T4H || T == T8H) cmode |= 0b1000;                                         \
    if (!(T == T4H || T == T8H || T == T2S || T == T4S)) {                             \
      assert(op == 0 && cmode0 == 0, "must be MOVI");                                  \
      cmode = 0b1110;                                                                  \
      if (T == T1D || T == T2D) op = 1;                                                \
    }                                                                                  \
    f(0, 31), f((int)T & 1, 30), f(op, 29), f(0b0111100000, 28, 19);                   \
    f(imm8 >> 5, 18, 16), f(cmode, 15, 12), f(0x01, 11, 10), f(imm8 & 0b11111, 9, 5);  \
    rf(Vd, 0);                                                                         \
  }

  INSN(movi, 0, 0);
  INSN(orri, 0, 1);
  INSN(mvni, 1, 0);
  INSN(bici, 1, 1);

#undef INSN

#define INSN(NAME, op, cmode)                                           \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, double imm) {         \
    unsigned imm8 = pack(imm);                                          \
    starti;                                                             \
    f(0, 31), f((int)T & 1, 30), f(op, 29), f(0b0111100000, 28, 19);    \
    f(imm8 >> 5, 18, 16), f(cmode, 15, 12), f(0x01, 11, 10), f(imm8 & 0b11111, 9, 5); \
    rf(Vd, 0);                                                          \
  }

  INSN(fmovs, 0, 0b1111);
  INSN(fmovd, 1, 0b1111);

#undef INSN

// Advanced SIMD three same
#define INSN(NAME, op1, op2, op3)                                                       \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");                    \
    f(0, 31), f((int)T & 1, 30), f(op1, 29), f(0b01110, 28, 24), f(op2, 23);            \
    f(T==T2D ? 1:0, 22); f(1, 21), rf(Vm, 16), f(op3, 15, 10), rf(Vn, 5), rf(Vd, 0);    \
  }

  INSN(fabd, 1, 1, 0b110101);
  INSN(fadd, 0, 0, 0b110101);
  INSN(fdiv, 1, 0, 0b111111);
  INSN(faddp, 1, 0, 0b110101);
  INSN(fmul, 1, 0, 0b110111);
  INSN(fsub, 0, 1, 0b110101);
  INSN(fmla, 0, 0, 0b110011);
  INSN(fmls, 0, 1, 0b110011);
  INSN(fmax, 0, 0, 0b111101);
  INSN(fmin, 0, 1, 0b111101);
  INSN(facgt, 1, 1, 0b111011);

#undef INSN

  // AdvSIMD vector compare
  void cm(Condition cond, FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) {
    starti;
    assert(T != T1Q && T != T1D, "incorrect arrangement");
    int cond_op;
    switch (cond) {
      case EQ: cond_op = 0b110001; break;
      case GT: cond_op = 0b000110; break;
      case GE: cond_op = 0b000111; break;
      case HI: cond_op = 0b100110; break;
      case HS: cond_op = 0b100111; break;
      default:
        ShouldNotReachHere();
        break;
    }

    f(0, 31), f((int)T & 1, 30), f((cond_op >> 5) & 1, 29);
    f(0b01110, 28, 24), f((int)T >> 1, 23, 22), f(1, 21), rf(Vm, 16);
    f(cond_op & 0b11111, 15, 11), f(1, 10), rf(Vn, 5), rf(Vd, 0);
  }

  // AdvSIMD Floating-point vector compare
  void fcm(Condition cond, FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) {
    starti;
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");
    int cond_op;
    switch (cond) {
      case EQ: cond_op = 0b00; break;
      case GT: cond_op = 0b11; break;
      case GE: cond_op = 0b10; break;
      default:
        ShouldNotReachHere();
        break;
    }

    f(0, 31), f((int)T & 1, 30), f((cond_op >> 1) & 1, 29);
    f(0b01110, 28, 24), f(cond_op & 1, 23), f(T == T2D ? 1 : 0, 22);
    f(1, 21), rf(Vm, 16), f(0b111001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, opc)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    assert(T == T4S, "arrangement must be T4S");                                        \
    f(0b01011110000, 31, 21), rf(Vm, 16), f(opc, 15, 10), rf(Vn, 5), rf(Vd, 0);         \
  }

  INSN(sha1c,     0b000000);
  INSN(sha1m,     0b001000);
  INSN(sha1p,     0b000100);
  INSN(sha1su0,   0b001100);
  INSN(sha256h2,  0b010100);
  INSN(sha256h,   0b010000);
  INSN(sha256su1, 0b011000);

#undef INSN

#define INSN(NAME, opc)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {                   \
    starti;                                                                             \
    assert(T == T4S, "arrangement must be T4S");                                        \
    f(0b0101111000101000, 31, 16), f(opc, 15, 10), rf(Vn, 5), rf(Vd, 0);                \
  }

  INSN(sha1h,     0b000010);
  INSN(sha1su1,   0b000110);
  INSN(sha256su0, 0b001010);

#undef INSN

#define INSN(NAME, opc)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    assert(T == T2D, "arrangement must be T2D");                                        \
    f(0b11001110011, 31, 21), rf(Vm, 16), f(opc, 15, 10), rf(Vn, 5), rf(Vd, 0);         \
  }

  INSN(sha512h,   0b100000);
  INSN(sha512h2,  0b100001);
  INSN(sha512su1, 0b100010);

#undef INSN

#define INSN(NAME, opc)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {                   \
    starti;                                                                             \
    assert(T == T2D, "arrangement must be T2D");                                        \
    f(opc, 31, 10), rf(Vn, 5), rf(Vd, 0);                                               \
  }

  INSN(sha512su0, 0b1100111011000000100000);

#undef INSN

#define INSN(NAME, opc)                                                                                   \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm, FloatRegister Va) { \
    starti;                                                                                               \
    assert(T == T16B, "arrangement must be T16B");                                                        \
    f(0b11001110, 31, 24), f(opc, 23, 21), rf(Vm, 16), f(0b0, 15, 15), rf(Va, 10), rf(Vn, 5), rf(Vd, 0);  \
  }

  INSN(eor3, 0b000);
  INSN(bcax, 0b001);

#undef INSN

#define INSN(NAME, opc)                                                                               \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm, unsigned imm) { \
    starti;                                                                                           \
    assert(T == T2D, "arrangement must be T2D");                                                      \
    f(0b11001110, 31, 24), f(opc, 23, 21), rf(Vm, 16), f(imm, 15, 10), rf(Vn, 5), rf(Vd, 0);          \
  }

  INSN(xar, 0b100);

#undef INSN

#define INSN(NAME, opc)                                                                           \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) {           \
    starti;                                                                                       \
    assert(T == T2D, "arrangement must be T2D");                                                  \
    f(0b11001110, 31, 24), f(opc, 23, 21), rf(Vm, 16), f(0b100011, 15, 10), rf(Vn, 5), rf(Vd, 0); \
  }

  INSN(rax1, 0b011);

#undef INSN

#define INSN(NAME, opc)                           \
  void NAME(FloatRegister Vd, FloatRegister Vn) { \
    starti;                                       \
    f(opc, 31, 10), rf(Vn, 5), rf(Vd, 0);         \
  }

  INSN(aese,   0b0100111000101000010010);
  INSN(aesd,   0b0100111000101000010110);
  INSN(aesmc,  0b0100111000101000011010);
  INSN(aesimc, 0b0100111000101000011110);

#undef INSN

#define INSN(NAME, op1, op2) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm, int index = 0) { \
    starti;                                                                                            \
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");                                   \
    assert(index >= 0 && ((T == T2D && index <= 1) || (T != T2D && index <= 3)), "invalid index");     \
    f(0, 31), f((int)T & 1, 30), f(op1, 29); f(0b011111, 28, 23);                                      \
    f(T == T2D ? 1 : 0, 22), f(T == T2D ? 0 : index & 1, 21), rf(Vm, 16);                              \
    f(op2, 15, 12), f(T == T2D ? index : (index >> 1), 11), f(0, 10);                                  \
    rf(Vn, 5), rf(Vd, 0);                                                                              \
  }

  // FMLA/FMLS - Vector - Scalar
  INSN(fmlavs, 0, 0b0001);
  INSN(fmlsvs, 0, 0b0101);
  // FMULX - Vector - Scalar
  INSN(fmulxvs, 1, 0b1001);

#undef INSN

  // Floating-point Reciprocal Estimate
  void frecpe(FloatRegister Vd, FloatRegister Vn, SIMD_RegVariant type) {
    assert(type == D || type == S, "Wrong type for frecpe");
    starti;
    f(0b010111101, 31, 23);
    f(type == D ? 1 : 0, 22);
    f(0b100001110110, 21, 10);
    rf(Vn, 5), rf(Vd, 0);
  }

  // (long) {a, b} -> (a + b)
  void addpd(FloatRegister Vd, FloatRegister Vn) {
    starti;
    f(0b0101111011110001101110, 31, 10);
    rf(Vn, 5), rf(Vd, 0);
  }

  // Floating-point AdvSIMD scalar pairwise
#define INSN(NAME, op1, op2) \
  void NAME(FloatRegister Vd, FloatRegister Vn, SIMD_RegVariant type) {                 \
    starti;                                                                             \
    assert(type == D || type == S, "Wrong type for faddp/fmaxp/fminp");                 \
    f(0b0111111, 31, 25), f(op1, 24, 23),                                               \
    f(type == S ? 0 : 1, 22), f(0b11000, 21, 17), f(op2, 16, 10), rf(Vn, 5), rf(Vd, 0); \
  }

  INSN(faddp, 0b00, 0b0110110);
  INSN(fmaxp, 0b00, 0b0111110);
  INSN(fminp, 0b01, 0b0111110);

#undef INSN

  void ins(FloatRegister Vd, SIMD_RegVariant T, FloatRegister Vn, int didx, int sidx) {
    starti;
    assert(T != Q, "invalid register variant");
    f(0b01101110000, 31, 21), f(((didx<<1)|1)<<(int)T, 20, 16), f(0, 15);
    f(sidx<<(int)T, 14, 11), f(1, 10), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, cond, op1, op2)                                                      \
  void NAME(Register Rd, FloatRegister Vn, SIMD_RegVariant T, int idx) {                \
    starti;                                                                             \
    assert(cond, "invalid register variant");                                           \
    f(0, 31), f(op1, 30), f(0b001110000, 29, 21);                                       \
    f(((idx << 1) | 1) << (int)T, 20, 16), f(op2, 15, 10);                              \
    rf(Vn, 5), rf(Rd, 0);                                                               \
  }

  INSN(umov, (T != Q), (T == D ? 1 : 0), 0b001111);
  INSN(smov, (T < D),  1,                0b001011);

#undef INSN

#define INSN(NAME, opc, opc2, isSHR)                                    \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, int shift){ \
    starti;                                                             \
    /* The encodings for the immh:immb fields (bits 22:16) in *SHR are  \
     *   0001 xxx       8B/16B, shift = 16  - UInt(immh:immb)           \
     *   001x xxx       4H/8H,  shift = 32  - UInt(immh:immb)           \
     *   01xx xxx       2S/4S,  shift = 64  - UInt(immh:immb)           \
     *   1xxx xxx       1D/2D,  shift = 128 - UInt(immh:immb)           \
     *   (1D is RESERVED)                                               \
     * for SHL shift is calculated as:                                  \
     *   0001 xxx       8B/16B, shift = UInt(immh:immb) - 8             \
     *   001x xxx       4H/8H,  shift = UInt(immh:immb) - 16            \
     *   01xx xxx       2S/4S,  shift = UInt(immh:immb) - 32            \
     *   1xxx xxx       1D/2D,  shift = UInt(immh:immb) - 64            \
     *   (1D is RESERVED)                                               \
     */                                                                 \
    guarantee(!isSHR || (isSHR && (shift != 0)), "impossible encoding");\
    assert((1 << ((T>>1)+3)) > shift, "Invalid Shift value");           \
    int cVal = (1 << (((T >> 1) + 3) + (isSHR ? 1 : 0)));               \
    int encodedShift = isSHR ? cVal - shift : cVal + shift;             \
    f(0, 31), f(T & 1, 30), f(opc, 29), f(0b011110, 28, 23),            \
    f(encodedShift, 22, 16); f(opc2, 15, 10), rf(Vn, 5), rf(Vd, 0);     \
  }

  INSN(shl,  0, 0b010101, /* isSHR = */ false);
  INSN(sshr, 0, 0b000001, /* isSHR = */ true);
  INSN(ushr, 1, 0b000001, /* isSHR = */ true);
  INSN(usra, 1, 0b000101, /* isSHR = */ true);
  INSN(ssra, 0, 0b000101, /* isSHR = */ true);
  INSN(sli,  1, 0b010101, /* isSHR = */ false);

#undef INSN

#define INSN(NAME, opc, opc2, isSHR)                                    \
  void NAME(FloatRegister Vd, FloatRegister Vn, int shift){             \
    starti;                                                             \
    int encodedShift = isSHR ? 128 - shift : 64 + shift;                \
    f(0b01, 31, 30), f(opc, 29), f(0b111110, 28, 23),                   \
    f(encodedShift, 22, 16); f(opc2, 15, 10), rf(Vn, 5), rf(Vd, 0);     \
  }

  INSN(shld,  0, 0b010101, /* isSHR = */ false);
  INSN(sshrd, 0, 0b000001, /* isSHR = */ true);
  INSN(ushrd, 1, 0b000001, /* isSHR = */ true);

#undef INSN

private:
  void _xshll(sign_kind sign, FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, SIMD_Arrangement Tb, int shift) {
    starti;
    /* The encodings for the immh:immb fields (bits 22:16) are
     *   0001 xxx       8H, 8B/16B shift = xxx
     *   001x xxx       4S, 4H/8H  shift = xxxx
     *   01xx xxx       2D, 2S/4S  shift = xxxxx
     *   1xxx xxx       RESERVED
     */
    assert((Tb >> 1) + 1 == (Ta >> 1), "Incompatible arrangement");
    assert((1 << ((Tb>>1)+3)) > shift, "Invalid shift value");
    f(0, 31), f(Tb & 1, 30), f(sign == SIGNED ? 0 : 1, 29), f(0b011110, 28, 23);
    f((1 << ((Tb>>1)+3))|shift, 22, 16);
    f(0b101001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

public:
  void ushll(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb, int shift) {
    assert(Tb == T8B || Tb == T4H || Tb == T2S, "invalid arrangement");
    _xshll(UNSIGNED, Vd, Ta, Vn, Tb, shift);
  }

  void ushll2(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb, int shift) {
    assert(Tb == T16B || Tb == T8H || Tb == T4S, "invalid arrangement");
    _xshll(UNSIGNED, Vd, Ta, Vn, Tb, shift);
  }

  void uxtl(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb) {
    ushll(Vd, Ta, Vn, Tb, 0);
  }

  void sshll(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb, int shift) {
    assert(Tb == T8B || Tb == T4H || Tb == T2S, "invalid arrangement");
    _xshll(SIGNED, Vd, Ta, Vn, Tb, shift);
  }

  void sshll2(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb, int shift) {
    assert(Tb == T16B || Tb == T8H || Tb == T4S, "invalid arrangement");
    _xshll(SIGNED, Vd, Ta, Vn, Tb, shift);
  }

  void sxtl(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb) {
    sshll(Vd, Ta, Vn, Tb, 0);
  }

  // Move from general purpose register
  //   mov  Vd.T[index], Rn
  void mov(FloatRegister Vd, SIMD_RegVariant T, int index, Register Xn) {
    guarantee(T != Q, "invalid register variant");
    starti;
    f(0b01001110000, 31, 21), f(((1 << T) | (index << (T + 1))), 20, 16);
    f(0b000111, 15, 10), zrf(Xn, 5), rf(Vd, 0);
  }

  // Move to general purpose register
  //   mov  Rd, Vn.T[index]
  void mov(Register Xd, FloatRegister Vn, SIMD_RegVariant T, int index) {
    guarantee(T == S || T == D, "invalid register variant");
    umov(Xd, Vn, T, index);
  }

private:
  void _pmull(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, FloatRegister Vm, SIMD_Arrangement Tb) {
    starti;
    assert((Ta == T1Q && (Tb == T1D || Tb == T2D)) ||
           (Ta == T8H && (Tb == T8B || Tb == T16B)), "Invalid Size specifier");
    int size = (Ta == T1Q) ? 0b11 : 0b00;
    f(0, 31), f(Tb & 1, 30), f(0b001110, 29, 24), f(size, 23, 22);
    f(1, 21), rf(Vm, 16), f(0b111000, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

public:
  void pmull(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, FloatRegister Vm, SIMD_Arrangement Tb) {
    assert(Tb == T1D || Tb == T8B, "pmull assumes T1D or T8B as the second size specifier");
    _pmull(Vd, Ta, Vn, Vm, Tb);
  }

  void pmull2(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, FloatRegister Vm, SIMD_Arrangement Tb) {
    assert(Tb == T2D || Tb == T16B, "pmull2 assumes T2D or T16B as the second size specifier");
    _pmull(Vd, Ta, Vn, Vm, Tb);
  }

  void uqxtn(FloatRegister Vd, SIMD_Arrangement Tb, FloatRegister Vn, SIMD_Arrangement Ta) {
    starti;
    int size_b = (int)Tb >> 1;
    int size_a = (int)Ta >> 1;
    assert(size_b < 3 && size_b == size_a - 1, "Invalid size specifier");
    f(0, 31), f(Tb & 1, 30), f(0b101110, 29, 24), f(size_b, 23, 22);
    f(0b100001010010, 21, 10), rf(Vn, 5), rf(Vd, 0);
  }

  void xtn(FloatRegister Vd, SIMD_Arrangement Tb, FloatRegister Vn, SIMD_Arrangement Ta) {
    starti;
    int size_b = (int)Tb >> 1;
    int size_a = (int)Ta >> 1;
    assert(size_b < 3 && size_b == size_a - 1, "Invalid size specifier");
    f(0, 31), f(Tb & 1, 30), f(0b001110, 29, 24), f(size_b, 23, 22);
    f(0b100001001010, 21, 10), rf(Vn, 5), rf(Vd, 0);
  }

  void dup(FloatRegister Vd, SIMD_Arrangement T, Register Xs)
  {
    starti;
    assert(T != T1D, "reserved encoding");
    f(0,31), f((int)T & 1, 30), f(0b001110000, 29, 21);
    f((1 << (T >> 1)), 20, 16), f(0b000011, 15, 10), zrf(Xs, 5), rf(Vd, 0);
  }

  void dup(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, int index = 0)
  {
    starti;
    assert(T != T1D, "reserved encoding");
    f(0, 31), f((int)T & 1, 30), f(0b001110000, 29, 21);
    f(((1 << (T >> 1)) | (index << ((T >> 1) + 1))), 20, 16);
    f(0b000001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

  // Advanced SIMD scalar copy
  void dup(FloatRegister Vd, SIMD_RegVariant T, FloatRegister Vn, int index = 0)
  {
    starti;
    assert(T != Q, "invalid size");
    f(0b01011110000, 31, 21);
    f((1 << T) | (index << (T + 1)), 20, 16);
    f(0b000001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

  // AdvSIMD ZIP/UZP/TRN
#define INSN(NAME, opcode)                                              \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    guarantee(T != T1D && T != T1Q, "invalid arrangement");             \
    starti;                                                             \
    f(0, 31), f(0b001110, 29, 24), f(0, 21), f(0, 15);                  \
    f(opcode, 14, 12), f(0b10, 11, 10);                                 \
    rf(Vm, 16), rf(Vn, 5), rf(Vd, 0);                                   \
    f(T & 1, 30), f(T >> 1, 23, 22);                                    \
  }

  INSN(uzp1, 0b001);
  INSN(trn1, 0b010);
  INSN(zip1, 0b011);
  INSN(uzp2, 0b101);
  INSN(trn2, 0b110);
  INSN(zip2, 0b111);

#undef INSN

  // CRC32 instructions
#define INSN(NAME, c, sf, sz)                                             \
  void NAME(Register Rd, Register Rn, Register Rm) {                      \
    starti;                                                               \
    f(sf, 31), f(0b0011010110, 30, 21), f(0b010, 15, 13), f(c, 12);       \
    f(sz, 11, 10), rf(Rm, 16), rf(Rn, 5), rf(Rd, 0);                      \
  }

  INSN(crc32b,  0, 0, 0b00);
  INSN(crc32h,  0, 0, 0b01);
  INSN(crc32w,  0, 0, 0b10);
  INSN(crc32x,  0, 1, 0b11);
  INSN(crc32cb, 1, 0, 0b00);
  INSN(crc32ch, 1, 0, 0b01);
  INSN(crc32cw, 1, 0, 0b10);
  INSN(crc32cx, 1, 1, 0b11);

#undef INSN

  // Table vector lookup
#define INSN(NAME, op)                                                  \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, unsigned registers, FloatRegister Vm) { \
    starti;                                                             \
    assert(T == T8B || T == T16B, "invalid arrangement");               \
    assert(0 < registers && registers <= 4, "invalid number of registers"); \
    f(0, 31), f((int)T & 1, 30), f(0b001110000, 29, 21), rf(Vm, 16), f(0, 15); \
    f(registers - 1, 14, 13), f(op, 12),f(0b00, 11, 10), rf(Vn, 5), rf(Vd, 0); \
  }

  INSN(tbl, 0);
  INSN(tbx, 1);

#undef INSN

  // AdvSIMD two-reg misc
  // In this instruction group, the 2 bits in the size field ([23:22]) may be
  // fixed or determined by the "SIMD_Arrangement T", or both. The additional
  // parameter "tmask" is a 2-bit mask used to indicate which bits in the size
  // field are determined by the SIMD_Arrangement. The bit of "tmask" should be
  // set to 1 if corresponding bit marked as "x" in the ArmARM.
#define INSN(NAME, U, size, tmask, opcode)                                          \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {               \
       starti;                                                                      \
       assert((ASSERTION), MSG);                                                    \
       f(0, 31), f((int)T & 1, 30), f(U, 29), f(0b01110, 28, 24);                   \
       f(size | ((int)(T >> 1) & tmask), 23, 22), f(0b10000, 21, 17);               \
       f(opcode, 16, 12), f(0b10, 11, 10), rf(Vn, 5), rf(Vd, 0);                    \
 }

#define MSG "invalid arrangement"

#define ASSERTION (T == T2S || T == T4S || T == T2D)
  INSN(fsqrt,  1, 0b10, 0b01, 0b11111);
  INSN(fabs,   0, 0b10, 0b01, 0b01111);
  INSN(fneg,   1, 0b10, 0b01, 0b01111);
  INSN(frintn, 0, 0b00, 0b01, 0b11000);
  INSN(frintm, 0, 0b00, 0b01, 0b11001);
  INSN(frintp, 0, 0b10, 0b01, 0b11000);
  INSN(fcvtas, 0, 0b00, 0b01, 0b11100);
  INSN(fcvtzs, 0, 0b10, 0b01, 0b11011);
  INSN(fcvtms, 0, 0b00, 0b01, 0b11011);
#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B || T == T4H || T == T8H || T == T2S || T == T4S)
  INSN(rev64, 0, 0b00, 0b11, 0b00000);
#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B || T == T4H || T == T8H)
  INSN(rev32, 1, 0b00, 0b11, 0b00000);
#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B)
  INSN(rev16, 0, 0b00, 0b11, 0b00001);
  INSN(rbit,  1, 0b01, 0b00, 0b00101);
#undef ASSERTION

#undef MSG

#undef INSN

  // AdvSIMD compare with zero (vector)
  void cm(Condition cond, FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {
    starti;
    assert(T != T1Q && T != T1D, "invalid arrangement");
    int cond_op;
    switch (cond) {
      case EQ: cond_op = 0b001; break;
      case GE: cond_op = 0b100; break;
      case GT: cond_op = 0b000; break;
      case LE: cond_op = 0b101; break;
      case LT: cond_op = 0b010; break;
      default:
        ShouldNotReachHere();
        break;
    }

    f(0, 31), f((int)T & 1, 30), f((cond_op >> 2) & 1, 29);
    f(0b01110, 28, 24), f((int)T >> 1, 23, 22), f(0b10000010, 21, 14);
    f(cond_op & 0b11, 13, 12), f(0b10, 11, 10), rf(Vn, 5), rf(Vd, 0);
  }

  // AdvSIMD Floating-point compare with zero (vector)
  void fcm(Condition cond, FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {
    starti;
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");
    int cond_op;
    switch (cond) {
      case EQ: cond_op = 0b010; break;
      case GT: cond_op = 0b000; break;
      case GE: cond_op = 0b001; break;
      case LE: cond_op = 0b011; break;
      case LT: cond_op = 0b100; break;
      default:
        ShouldNotReachHere();
        break;
    }

    f(0, 31), f((int)T & 1, 30), f(cond_op & 1, 29), f(0b011101, 28, 23);
    f(((int)(T >> 1) & 1), 22), f(0b10000011, 21, 14);
    f((cond_op >> 1) & 0b11, 13, 12), f(0b10, 11, 10), rf(Vn, 5), rf(Vd, 0);
  }

  void ext(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm, int index)
  {
    starti;
    assert(T == T8B || T == T16B, "invalid arrangement");
    assert((T == T8B && index <= 0b0111) || (T == T16B && index <= 0b1111), "Invalid index value");
    f(0, 31), f((int)T & 1, 30), f(0b101110000, 29, 21);
    rf(Vm, 16), f(0, 15), f(index, 14, 11);
    f(0, 10), rf(Vn, 5), rf(Vd, 0);
  }

// SVE arithmetic - unpredicated
#define INSN(NAME, opcode)                                                             \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, FloatRegister Zm) { \
    starti;                                                                            \
    assert(T != Q, "invalid register variant");                                        \
    f(0b00000100, 31, 24), f(T, 23, 22), f(1, 21),                                     \
    rf(Zm, 16), f(0, 15, 13), f(opcode, 12, 10), rf(Zn, 5), rf(Zd, 0);                 \
  }
  INSN(sve_add, 0b000);
  INSN(sve_sub, 0b001);
#undef INSN

// SVE integer add/subtract immediate (unpredicated)
#define INSN(NAME, op)                                                  \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, unsigned imm8) {       \
    starti;                                                             \
    /* The immediate is an unsigned value in the range 0 to 255, and    \
     * for element width of 16 bits or higher it may also be a          \
     * positive multiple of 256 in the range 256 to 65280.              \
     */                                                                 \
    assert(T != Q, "invalid size");                                     \
    int sh = 0;                                                         \
    if (imm8 <= 0xff) {                                                 \
      sh = 0;                                                           \
    } else if (T != B && imm8 <= 0xff00 && (imm8 & 0xff) == 0) {        \
      sh = 1;                                                           \
      imm8 = (imm8 >> 8);                                               \
    } else {                                                            \
      guarantee(false, "invalid immediate");                            \
    }                                                                   \
    f(0b00100101, 31, 24), f(T, 23, 22), f(0b10000, 21, 17);            \
    f(op, 16, 14), f(sh, 13), f(imm8, 12, 5), rf(Zd, 0);                \
  }

  INSN(sve_add, 0b011);
  INSN(sve_sub, 0b111);
#undef INSN

// SVE floating-point arithmetic - unpredicated
#define INSN(NAME, opcode)                                                             \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, FloatRegister Zm) { \
    starti;                                                                            \
    assert(T == S || T == D, "invalid register variant");                              \
    f(0b01100101, 31, 24), f(T, 23, 22), f(0, 21),                                     \
    rf(Zm, 16), f(0, 15, 13), f(opcode, 12, 10), rf(Zn, 5), rf(Zd, 0);                 \
  }

  INSN(sve_fadd, 0b000);
  INSN(sve_fmul, 0b010);
  INSN(sve_fsub, 0b001);
#undef INSN

private:
  void sve_predicate_reg_insn(unsigned op24, unsigned op13,
                              FloatRegister Zd_or_Vd, SIMD_RegVariant T,
                              PRegister Pg, FloatRegister Zn_or_Vn) {
    starti;
    f(op24, 31, 24), f(T, 23, 22), f(op13, 21, 13);
    pgrf(Pg, 10), rf(Zn_or_Vn, 5), rf(Zd_or_Vd, 0);
  }

  void sve_shift_imm_encoding(SIMD_RegVariant T, int shift, bool isSHR,
                              int& tszh, int& tszl_imm) {
    /* The encodings for the tszh:tszl:imm3 fields
     * for shift right is calculated as:
     *   0001 xxx       B, shift = 16  - UInt(tszh:tszl:imm3)
     *   001x xxx       H, shift = 32  - UInt(tszh:tszl:imm3)
     *   01xx xxx       S, shift = 64  - UInt(tszh:tszl:imm3)
     *   1xxx xxx       D, shift = 128 - UInt(tszh:tszl:imm3)
     * for shift left is calculated as:
     *   0001 xxx       B, shift = UInt(tszh:tszl:imm3) - 8
     *   001x xxx       H, shift = UInt(tszh:tszl:imm3) - 16
     *   01xx xxx       S, shift = UInt(tszh:tszl:imm3) - 32
     *   1xxx xxx       D, shift = UInt(tszh:tszl:imm3) - 64
     */
    assert(T != Q, "Invalid register variant");
    if (isSHR) {
      assert(((1 << (T + 3)) >= shift) && (shift > 0) , "Invalid shift value");
    } else {
      assert(((1 << (T + 3)) > shift) && (shift >= 0) , "Invalid shift value");
    }
    int cVal = (1 << ((T + 3) + (isSHR ? 1 : 0)));
    int encodedShift = isSHR ? cVal - shift : cVal + shift;
    tszh = encodedShift >> 5;
    tszl_imm = encodedShift & 0x1f;
  }

public:

// SVE integer arithmetic - predicate
#define INSN(NAME, op1, op2)                                                                            \
  void NAME(FloatRegister Zdn_or_Zd_or_Vd, SIMD_RegVariant T, PRegister Pg, FloatRegister Znm_or_Vn) {  \
    assert(T != Q, "invalid register variant");                                                         \
    sve_predicate_reg_insn(op1, op2, Zdn_or_Zd_or_Vd, T, Pg, Znm_or_Vn);                                \
  }

  INSN(sve_abs,   0b00000100, 0b010110101); // vector abs, unary
  INSN(sve_add,   0b00000100, 0b000000000); // vector add
  INSN(sve_and,   0b00000100, 0b011010000); // vector and
  INSN(sve_andv,  0b00000100, 0b011010001); // bitwise and reduction to scalar
  INSN(sve_asr,   0b00000100, 0b010000100); // vector arithmetic shift right
  INSN(sve_bic,   0b00000100, 0b011011000); // vector bitwise clear
  INSN(sve_clz,   0b00000100, 0b011001101); // vector count leading zero bits
  INSN(sve_cnt,   0b00000100, 0b011010101); // count non-zero bits
  INSN(sve_cpy,   0b00000101, 0b100000100); // copy scalar to each active vector element
  INSN(sve_eor,   0b00000100, 0b011001000); // vector eor
  INSN(sve_eorv,  0b00000100, 0b011001001); // bitwise xor reduction to scalar
  INSN(sve_lsl,   0b00000100, 0b010011100); // vector logical shift left
  INSN(sve_lsr,   0b00000100, 0b010001100); // vector logical shift right
  INSN(sve_mul,   0b00000100, 0b010000000); // vector mul
  INSN(sve_neg,   0b00000100, 0b010111101); // vector neg, unary
  INSN(sve_not,   0b00000100, 0b011110101); // bitwise invert vector, unary
  INSN(sve_orr,   0b00000100, 0b011000000); // vector or
  INSN(sve_orv,   0b00000100, 0b011000001); // bitwise or reduction to scalar
  INSN(sve_smax,  0b00000100, 0b001000000); // signed maximum vectors
  INSN(sve_smaxv, 0b00000100, 0b001000001); // signed maximum reduction to scalar
  INSN(sve_smin,  0b00000100, 0b001010000); // signed minimum vectors
  INSN(sve_sminv, 0b00000100, 0b001010001); // signed minimum reduction to scalar
  INSN(sve_sub,   0b00000100, 0b000001000); // vector sub
  INSN(sve_uaddv, 0b00000100, 0b000001001); // unsigned add reduction to scalar
#undef INSN

// SVE floating-point arithmetic - predicate
#define INSN(NAME, op1, op2)                                                                          \
  void NAME(FloatRegister Zd_or_Zdn_or_Vd, SIMD_RegVariant T, PRegister Pg, FloatRegister Zn_or_Zm) { \
    assert(T == S || T == D, "invalid register variant");                                             \
    sve_predicate_reg_insn(op1, op2, Zd_or_Zdn_or_Vd, T, Pg, Zn_or_Zm);                               \
  }

  INSN(sve_fabd,   0b01100101, 0b001000100); // floating-point absolute difference
  INSN(sve_fabs,   0b00000100, 0b011100101);
  INSN(sve_fadd,   0b01100101, 0b000000100);
  INSN(sve_fadda,  0b01100101, 0b011000001); // add strictly-ordered reduction to scalar Vd
  INSN(sve_fdiv,   0b01100101, 0b001101100);
  INSN(sve_fmax,   0b01100101, 0b000110100); // floating-point maximum
  INSN(sve_fmaxv,  0b01100101, 0b000110001); // floating-point maximum recursive reduction to scalar
  INSN(sve_fmin,   0b01100101, 0b000111100); // floating-point minimum
  INSN(sve_fminv,  0b01100101, 0b000111001); // floating-point minimum recursive reduction to scalar
  INSN(sve_fmul,   0b01100101, 0b000010100);
  INSN(sve_fneg,   0b00000100, 0b011101101);
  INSN(sve_frintm, 0b01100101, 0b000010101); // floating-point round to integral value, toward minus infinity
  INSN(sve_frintn, 0b01100101, 0b000000101); // floating-point round to integral value, nearest with ties to even
  INSN(sve_frinta, 0b01100101, 0b000100101); // floating-point round to integral value, nearest with ties to away
  INSN(sve_frintp, 0b01100101, 0b000001101); // floating-point round to integral value, toward plus infinity
  INSN(sve_fsqrt,  0b01100101, 0b001101101);
  INSN(sve_fsub,   0b01100101, 0b000001100);
#undef INSN

  // SVE multiple-add/sub - predicated
#define INSN(NAME, op0, op1, op2)                                                                     \
  void NAME(FloatRegister Zda, SIMD_RegVariant T, PRegister Pg, FloatRegister Zn, FloatRegister Zm) { \
    starti;                                                                                           \
    assert(T != Q, "invalid size");                                                                   \
    f(op0, 31, 24), f(T, 23, 22), f(op1, 21), rf(Zm, 16);                                             \
    f(op2, 15, 13), pgrf(Pg, 10), rf(Zn, 5), rf(Zda, 0);                                              \
  }

  INSN(sve_fmla,  0b01100101, 1, 0b000); // floating-point fused multiply-add, writing addend: Zda = Zda + Zn * Zm
  INSN(sve_fmls,  0b01100101, 1, 0b001); // floating-point fused multiply-subtract: Zda = Zda + -Zn * Zm
  INSN(sve_fnmla, 0b01100101, 1, 0b010); // floating-point negated fused multiply-add: Zda = -Zda + -Zn * Zm
  INSN(sve_fnmls, 0b01100101, 1, 0b011); // floating-point negated fused multiply-subtract: Zda = -Zda + Zn * Zm
  INSN(sve_fmad,  0b01100101, 1, 0b100); // floating-point fused multiply-add, writing multiplicand: Zda = Zm + Zda * Zn
  INSN(sve_fmsb,  0b01100101, 1, 0b101); // floating-point fused multiply-subtract, writing multiplicand: Zda = Zm + -Zda * Zn
  INSN(sve_fnmad, 0b01100101, 1, 0b110); // floating-point negated fused multiply-add, writing multiplicand: Zda = -Zm + -Zda * Zn
  INSN(sve_fnmsb, 0b01100101, 1, 0b111); // floating-point negated fused multiply-subtract, writing multiplicand: Zda = -Zm + Zda * Zn
  INSN(sve_mla,   0b00000100, 0, 0b010); // multiply-add, writing addend: Zda = Zda + Zn*Zm
  INSN(sve_mls,   0b00000100, 0, 0b011); // multiply-subtract, writing addend: Zda = Zda + -Zn*Zm
#undef INSN

// SVE bitwise logical - unpredicated
#define INSN(NAME, opc)                                              \
  void NAME(FloatRegister Zd, FloatRegister Zn, FloatRegister Zm) {  \
    starti;                                                          \
    f(0b00000100, 31, 24), f(opc, 23, 22), f(1, 21),                 \
    rf(Zm, 16), f(0b001100, 15, 10), rf(Zn, 5), rf(Zd, 0);           \
  }
  INSN(sve_and, 0b00);
  INSN(sve_eor, 0b10);
  INSN(sve_orr, 0b01);
  INSN(sve_bic, 0b11);
#undef INSN

// SVE bitwise logical with immediate (unpredicated)
#define INSN(NAME, opc)                                                      \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, uint64_t imm) {             \
    starti;                                                                  \
    unsigned elembits = regVariant_to_elemBits(T);                           \
    uint32_t val = encode_sve_logical_immediate(elembits, imm);              \
    f(0b00000101, 31, 24), f(opc, 23, 22), f(0b0000, 21, 18);                \
    f(val, 17, 5), rf(Zd, 0);                                                \
  }
  INSN(sve_and, 0b10);
  INSN(sve_eor, 0b01);
  INSN(sve_orr, 0b00);
#undef INSN

// SVE shift immediate - unpredicated
#define INSN(NAME, opc, isSHR)                                                  \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, int shift) { \
    starti;                                                                     \
    int tszh, tszl_imm;                                                         \
    sve_shift_imm_encoding(T, shift, isSHR, tszh, tszl_imm);                    \
    f(0b00000100, 31, 24);                                                      \
    f(tszh, 23, 22), f(1,21), f(tszl_imm, 20, 16);                              \
    f(0b100, 15, 13), f(opc, 12, 10), rf(Zn, 5), rf(Zd, 0);                     \
  }

  INSN(sve_asr, 0b100, /* isSHR = */ true);
  INSN(sve_lsl, 0b111, /* isSHR = */ false);
  INSN(sve_lsr, 0b101, /* isSHR = */ true);
#undef INSN

// SVE bitwise shift by immediate (predicated)
#define INSN(NAME, opc, isSHR)                                                  \
  void NAME(FloatRegister Zdn, SIMD_RegVariant T, PRegister Pg, int shift) {    \
    starti;                                                                     \
    int tszh, tszl_imm;                                                         \
    sve_shift_imm_encoding(T, shift, isSHR, tszh, tszl_imm);                    \
    f(0b00000100, 31, 24), f(tszh, 23, 22), f(0b00, 21, 20), f(opc, 19, 16);    \
    f(0b100, 15, 13), pgrf(Pg, 10), f(tszl_imm, 9, 5), rf(Zdn, 0);              \
  }

  INSN(sve_asr, 0b0000, /* isSHR = */ true);
  INSN(sve_lsl, 0b0011, /* isSHR = */ false);
  INSN(sve_lsr, 0b0001, /* isSHR = */ true);
#undef INSN

private:

  // Scalar base + immediate index
  void sve_ld_st1(FloatRegister Zt, Register Xn, int imm, PRegister Pg,
              SIMD_RegVariant T, int op1, int type, int op2) {
    starti;
    assert_cond(T >= type);
    f(op1, 31, 25), f(type, 24, 23), f(T, 22, 21);
    f(0, 20), sf(imm, 19, 16), f(op2, 15, 13);
    pgrf(Pg, 10), srf(Xn, 5), rf(Zt, 0);
  }

  // Scalar base + scalar index
  void sve_ld_st1(FloatRegister Zt, Register Xn, Register Xm, PRegister Pg,
              SIMD_RegVariant T, int op1, int type, int op2) {
    starti;
    assert_cond(T >= type);
    f(op1, 31, 25), f(type, 24, 23), f(T, 22, 21);
    rf(Xm, 16), f(op2, 15, 13);
    pgrf(Pg, 10), srf(Xn, 5), rf(Zt, 0);
  }

  void sve_ld_st1(FloatRegister Zt, PRegister Pg,
              SIMD_RegVariant T, const Address &a,
              int op1, int type, int imm_op2, int scalar_op2) {
    switch (a.getMode()) {
    case Address::base_plus_offset:
      sve_ld_st1(Zt, a.base(), checked_cast<int>(a.offset()), Pg, T, op1, type, imm_op2);
      break;
    case Address::base_plus_offset_reg:
      sve_ld_st1(Zt, a.base(), a.index(), Pg, T, op1, type, scalar_op2);
      break;
    default:
      ShouldNotReachHere();
    }
  }

public:

// SVE contiguous load/store
#define INSN(NAME, op1, type, imm_op2, scalar_op2)                                   \
  void NAME(FloatRegister Zt, SIMD_RegVariant T, PRegister Pg, const Address &a) {   \
    assert(T != Q, "invalid register variant");                                      \
    sve_ld_st1(Zt, Pg, T, a, op1, type, imm_op2, scalar_op2);                        \
  }

  INSN(sve_ld1b, 0b1010010, 0b00, 0b101, 0b010);
  INSN(sve_st1b, 0b1110010, 0b00, 0b111, 0b010);
  INSN(sve_ld1h, 0b1010010, 0b01, 0b101, 0b010);
  INSN(sve_st1h, 0b1110010, 0b01, 0b111, 0b010);
  INSN(sve_ld1w, 0b1010010, 0b10, 0b101, 0b010);
  INSN(sve_st1w, 0b1110010, 0b10, 0b111, 0b010);
  INSN(sve_ld1d, 0b1010010, 0b11, 0b101, 0b010);
  INSN(sve_st1d, 0b1110010, 0b11, 0b111, 0b010);
#undef INSN

// Gather/scatter load/store (SVE) - scalar plus vector
#define INSN(NAME, op1, type, op2, op3)                                         \
  void NAME(FloatRegister Zt, PRegister Pg, Register Xn, FloatRegister Zm) {    \
    starti;                                                                     \
    f(op1, 31, 25), f(type, 24, 23), f(op2, 22, 21), rf(Zm, 16);                \
    f(op3, 15, 13), pgrf(Pg, 10), srf(Xn, 5), rf(Zt, 0);                        \
  }
  // SVE 32-bit gather load words (scalar plus 32-bit scaled offsets)
  INSN(sve_ld1w_gather,  0b1000010, 0b10, 0b01, 0b010);
  // SVE 64-bit gather load (scalar plus 32-bit unpacked scaled offsets)
  INSN(sve_ld1d_gather,  0b1100010, 0b11, 0b01, 0b010);
  // SVE 32-bit scatter store (scalar plus 32-bit scaled offsets)
  INSN(sve_st1w_scatter, 0b1110010, 0b10, 0b11, 0b100);
  // SVE 64-bit scatter store (scalar plus unpacked 32-bit scaled offsets)
  INSN(sve_st1d_scatter, 0b1110010, 0b11, 0b01, 0b100);
#undef INSN

// SVE load/store - unpredicated
#define INSN(NAME, op1)                                                         \
  void NAME(FloatRegister Zt, const Address &a)  {                              \
    starti;                                                                     \
    assert(a.index() == noreg, "invalid address variant");                      \
    f(op1, 31, 29), f(0b0010110, 28, 22), sf(a.offset() >> 3, 21, 16),          \
    f(0b010, 15, 13), f(a.offset() & 0x7, 12, 10), srf(a.base(), 5), rf(Zt, 0); \
  }

  INSN(sve_ldr, 0b100); // LDR (vector)
  INSN(sve_str, 0b111); // STR (vector)
#undef INSN

// SVE stack frame adjustment
#define INSN(NAME, op) \
  void NAME(Register Xd, Register Xn, int imm6) {                 \
    starti;                                                       \
    f(0b000001000, 31, 23), f(op, 22, 21);                        \
    srf(Xn, 16), f(0b01010, 15, 11), sf(imm6, 10, 5), srf(Xd, 0); \
  }

  INSN(sve_addvl, 0b01); // Add multiple of vector register size to scalar register
  INSN(sve_addpl, 0b11); // Add multiple of predicate register size to scalar register
#undef INSN

// SVE inc/dec register by element count
#define INSN(NAME, op) \
  void NAME(Register Xdn, SIMD_RegVariant T, unsigned imm4 = 1, int pattern = 0b11111) { \
    starti;                                                                              \
    assert(T != Q, "invalid size");                                                      \
    f(0b00000100,31, 24), f(T, 23, 22), f(0b11, 21, 20);                                 \
    f(imm4 - 1, 19, 16), f(0b11100, 15, 11), f(op, 10), f(pattern, 9, 5), rf(Xdn, 0);    \
  }

  INSN(sve_inc, 0);
  INSN(sve_dec, 1);
#undef INSN

// SVE predicate logical operations
#define INSN(NAME, op1, op2, op3) \
  void NAME(PRegister Pd, PRegister Pg, PRegister Pn, PRegister Pm) { \
    starti;                                                           \
    f(0b00100101, 31, 24), f(op1, 23, 22), f(0b00, 21, 20);           \
    prf(Pm, 16), f(0b01, 15, 14), prf(Pg, 10), f(op2, 9);             \
    prf(Pn, 5), f(op3, 4), prf(Pd, 0);                                \
  }

  INSN(sve_and,  0b00, 0b0, 0b0);
  INSN(sve_ands, 0b01, 0b0, 0b0);
  INSN(sve_eor,  0b00, 0b1, 0b0);
  INSN(sve_eors, 0b01, 0b1, 0b0);
  INSN(sve_orr,  0b10, 0b0, 0b0);
  INSN(sve_orrs, 0b11, 0b0, 0b0);
  INSN(sve_bic,  0b00, 0b0, 0b1);
#undef INSN

  // SVE increment register by predicate count
  void sve_incp(const Register rd, SIMD_RegVariant T, PRegister pg) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00100101, 31, 24), f(T, 23, 22), f(0b1011001000100, 21, 9),
    prf(pg, 5), rf(rd, 0);
  }

  // SVE broadcast general-purpose register to vector elements (unpredicated)
  void sve_dup(FloatRegister Zd, SIMD_RegVariant T, Register Rn) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b100000001110, 21, 10);
    srf(Rn, 5), rf(Zd, 0);
  }

  // SVE broadcast signed immediate to vector elements (unpredicated)
  void sve_dup(FloatRegister Zd, SIMD_RegVariant T, int imm8) {
    starti;
    assert(T != Q, "invalid size");
    int sh = 0;
    if (imm8 <= 127 && imm8 >= -128) {
      sh = 0;
    } else if (T != B && imm8 <= 32512 && imm8 >= -32768 && (imm8 & 0xff) == 0) {
      sh = 1;
      imm8 = (imm8 >> 8);
    } else {
      guarantee(false, "invalid immediate");
    }
    f(0b00100101, 31, 24), f(T, 23, 22), f(0b11100011, 21, 14);
    f(sh, 13), sf(imm8, 12, 5), rf(Zd, 0);
  }

  // SVE predicate test
  void sve_ptest(PRegister Pg, PRegister Pn) {
    starti;
    f(0b001001010101000011, 31, 14), prf(Pg, 10), f(0, 9), prf(Pn, 5), f(0, 4, 0);
  }

  // SVE predicate initialize
  void sve_ptrue(PRegister pd, SIMD_RegVariant esize, int pattern = 0b11111) {
    starti;
    f(0b00100101, 31, 24), f(esize, 23, 22), f(0b011000111000, 21, 10);
    f(pattern, 9, 5), f(0b0, 4), prf(pd, 0);
  }

  // SVE predicate zero
  void sve_pfalse(PRegister pd) {
    starti;
    f(0b00100101, 31, 24), f(0b00, 23, 22), f(0b011000111001, 21, 10);
    f(0b000000, 9, 4), prf(pd, 0);
  }

// SVE load/store predicate register
#define INSN(NAME, op1)                                                  \
  void NAME(PRegister Pt, const Address &a)  {                           \
    starti;                                                              \
    assert(a.index() == noreg, "invalid address variant");               \
    f(op1, 31, 29), f(0b0010110, 28, 22), sf(a.offset() >> 3, 21, 16),   \
    f(0b000, 15, 13), f(a.offset() & 0x7, 12, 10), srf(a.base(), 5),     \
    f(0, 4), prf(Pt, 0);                                                 \
  }

  INSN(sve_ldr, 0b100); // LDR (predicate)
  INSN(sve_str, 0b111); // STR (predicate)
#undef INSN

  // SVE move predicate register
  void sve_mov(PRegister Pd, PRegister Pn) {
    starti;
    f(0b001001011000, 31, 20), prf(Pn, 16), f(0b01, 15, 14), prf(Pn, 10);
    f(0, 9), prf(Pn, 5), f(0, 4), prf(Pd, 0);
  }

  // SVE copy general-purpose register to vector elements (predicated)
  void sve_cpy(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg, Register Rn) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b101000101, 21, 13);
    pgrf(Pg, 10), srf(Rn, 5), rf(Zd, 0);
  }

private:
  void sve_cpy(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg, int imm8,
               bool isMerge, bool isFloat) {
    starti;
    assert(T != Q, "invalid size");
    int sh = 0;
    if (imm8 <= 127 && imm8 >= -128) {
      sh = 0;
    } else if (T != B && imm8 <= 32512 && imm8 >= -32768 && (imm8 & 0xff) == 0) {
      sh = 1;
      imm8 = (imm8 >> 8);
    } else {
      guarantee(false, "invalid immediate");
    }
    int m = isMerge ? 1 : 0;
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b01, 21, 20);
    prf(Pg, 16), f(isFloat ? 1 : 0, 15), f(m, 14), f(sh, 13), sf(imm8, 12, 5), rf(Zd, 0);
  }

public:
  // SVE copy signed integer immediate to vector elements (predicated)
  void sve_cpy(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg, int imm8, bool isMerge) {
    sve_cpy(Zd, T, Pg, imm8, isMerge, /*isFloat*/false);
  }
  // SVE copy floating-point immediate to vector elements (predicated)
  void sve_cpy(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg, double d) {
    sve_cpy(Zd, T, Pg, checked_cast<int8_t>(pack(d)), /*isMerge*/true, /*isFloat*/true);
  }

  // SVE conditionally select elements from two vectors
  void sve_sel(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg,
               FloatRegister Zn, FloatRegister Zm) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1, 21), rf(Zm, 16);
    f(0b11, 15, 14), prf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

  // SVE Permute Vector - Extract
  void sve_ext(FloatRegister Zdn, FloatRegister Zm, int imm8) {
    starti;
    f(0b00000101001, 31, 21), f(imm8 >> 3, 20, 16), f(0b000, 15, 13);
    f(imm8 & 0b111, 12, 10), rf(Zm, 5), rf(Zdn, 0);
  }

// SVE Integer/Floating-Point Compare - Vectors
#define INSN(NAME, op1, op2, fp)  \
  void NAME(Condition cond, PRegister Pd, SIMD_RegVariant T, PRegister Pg,             \
            FloatRegister Zn, FloatRegister Zm) {                                      \
    starti;                                                                            \
    assert(T != Q, "invalid size");                                                    \
    bool is_absolute = op2 == 0b11;                                                    \
    if (fp == 1) {                                                                     \
      assert(T != B, "invalid size");                                                  \
      if (is_absolute) {                                                               \
        assert(cond == GT || cond == GE, "invalid condition for fac");                 \
      } else {                                                                         \
        assert(cond != HI && cond != HS, "invalid condition for fcm");                 \
      }                                                                                \
    }                                                                                  \
    int cond_op;                                                                       \
    switch(cond) {                                                                     \
      case EQ: cond_op = (op2 << 2) | 0b10; break;                                     \
      case NE: cond_op = (op2 << 2) | 0b11; break;                                     \
      case GE: cond_op = (op2 << 2) | (is_absolute ? 0b01 : 0b00); break;              \
      case GT: cond_op = (op2 << 2) | (is_absolute ? 0b11 : 0b01); break;              \
      case HI: cond_op = 0b0001; break;                                                \
      case HS: cond_op = 0b0000; break;                                                \
      default:                                                                         \
        ShouldNotReachHere();                                                          \
    }                                                                                  \
    f(op1, 31, 24), f(T, 23, 22), f(0, 21), rf(Zm, 16), f((cond_op >> 1) & 7, 15, 13); \
    pgrf(Pg, 10), rf(Zn, 5), f(cond_op & 1, 4), prf(Pd, 0);                            \
  }

  INSN(sve_cmp, 0b00100100, 0b10, 0); // Integer compare vectors
  INSN(sve_fcm, 0b01100101, 0b01, 1); // Floating-point compare vectors
  INSN(sve_fac, 0b01100101, 0b11, 1); // Floating-point absolute compare vectors
#undef INSN

private:
  // Convert Assembler::Condition to op encoding - used by sve integer compare encoding
  static int assembler_cond_to_sve_op(Condition cond, bool &is_unsigned) {
    if (cond == HI || cond == HS || cond == LO || cond == LS) {
      is_unsigned = true;
    } else {
      is_unsigned = false;
    }

    switch (cond) {
      case HI:
      case GT:
        return 0b0001;
      case HS:
      case GE:
        return 0b0000;
      case LO:
      case LT:
        return 0b0010;
      case LS:
      case LE:
        return 0b0011;
      case EQ:
        return 0b1000;
      case NE:
        return 0b1001;
      default:
        ShouldNotReachHere();
        return -1;
    }
  }

public:
  // SVE Integer Compare - 5 bits signed imm and 7 bits unsigned imm
  void sve_cmp(Condition cond, PRegister Pd, SIMD_RegVariant T,
               PRegister Pg, FloatRegister Zn, int imm) {
    starti;
    assert(T != Q, "invalid size");
    bool is_unsigned = false;
    int cond_op = assembler_cond_to_sve_op(cond, is_unsigned);
    f(is_unsigned ? 0b00100100 : 0b00100101, 31, 24), f(T, 23, 22);
    f(is_unsigned ? 0b1 : 0b0, 21);
    if (is_unsigned) {
      f(imm, 20, 14), f((cond_op >> 1) & 0x1, 13);
    } else {
      sf(imm, 20, 16), f((cond_op >> 1) & 0x7, 15, 13);
    }
    pgrf(Pg, 10), rf(Zn, 5), f(cond_op & 0x1, 4), prf(Pd, 0);
  }

  // SVE Floating-point compare vector with zero
  void sve_fcm(Condition cond, PRegister Pd, SIMD_RegVariant T,
               PRegister Pg, FloatRegister Zn, double d) {
    starti;
    assert(T != Q, "invalid size");
    guarantee(d == 0.0, "invalid immediate");
    int cond_op;
    switch(cond) {
      case EQ: cond_op = 0b100; break;
      case GT: cond_op = 0b001; break;
      case GE: cond_op = 0b000; break;
      case LT: cond_op = 0b010; break;
      case LE: cond_op = 0b011; break;
      case NE: cond_op = 0b110; break;
      default:
        ShouldNotReachHere();
    }
    f(0b01100101, 31, 24), f(T, 23, 22), f(0b0100, 21, 18),
    f((cond_op >> 1) & 0x3, 17, 16), f(0b001, 15, 13),
    pgrf(Pg, 10), rf(Zn, 5);
    f(cond_op & 0x1, 4), prf(Pd, 0);
  }

// SVE unpack vector elements
#define INSN(NAME, op) \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn) { \
    starti;                                                          \
    assert(T != B && T != Q, "invalid size");                        \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1100, 21, 18);          \
    f(op, 17, 16), f(0b001110, 15, 10), rf(Zn, 5), rf(Zd, 0);        \
  }

  INSN(sve_uunpkhi, 0b11); // Signed unpack and extend half of vector - high half
  INSN(sve_uunpklo, 0b10); // Signed unpack and extend half of vector - low half
  INSN(sve_sunpkhi, 0b01); // Unsigned unpack and extend half of vector - high half
  INSN(sve_sunpklo, 0b00); // Unsigned unpack and extend half of vector - low half
#undef INSN

// SVE unpack predicate elements
#define INSN(NAME, op) \
  void NAME(PRegister Pd, PRegister Pn) { \
    starti;                                                          \
    f(0b000001010011000, 31, 17), f(op, 16), f(0b0100000, 15, 9);    \
    prf(Pn, 5), f(0b0, 4), prf(Pd, 0);                               \
  }

  INSN(sve_punpkhi, 0b1); // Unpack and widen high half of predicate
  INSN(sve_punpklo, 0b0); // Unpack and widen low half of predicate
#undef INSN

// SVE permute vector elements
#define INSN(NAME, op) \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, FloatRegister Zm) { \
    starti;                                                                            \
    assert(T != Q, "invalid size");                                                    \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1, 21), rf(Zm, 16);                       \
    f(0b01101, 15, 11), f(op, 10), rf(Zn, 5), rf(Zd, 0);                               \
  }

  INSN(sve_uzp1, 0b0); // Concatenate even elements from two vectors
  INSN(sve_uzp2, 0b1); // Concatenate odd elements from two vectors
#undef INSN

// SVE permute predicate elements
#define INSN(NAME, op) \
  void NAME(PRegister Pd, SIMD_RegVariant T, PRegister Pn, PRegister Pm) {             \
    starti;                                                                            \
    assert(T != Q, "invalid size");                                                    \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b10, 21, 20), prf(Pm, 16);                 \
    f(0b01001, 15, 11), f(op, 10), f(0b0, 9), prf(Pn, 5), f(0b0, 4), prf(Pd, 0);       \
  }

  INSN(sve_uzp1, 0b0); // Concatenate even elements from two predicates
  INSN(sve_uzp2, 0b1); // Concatenate odd elements from two predicates
#undef INSN

// SVE integer compare scalar count and limit
#define INSN(NAME, sf, op)                                                \
  void NAME(PRegister Pd, SIMD_RegVariant T, Register Rn, Register Rm) {  \
    starti;                                                               \
    assert(T != Q, "invalid register variant");                           \
    f(0b00100101, 31, 24), f(T, 23, 22), f(1, 21),                        \
    zrf(Rm, 16), f(0, 15, 13), f(sf, 12), f(op >> 1, 11, 10),             \
    zrf(Rn, 5), f(op & 1, 4), prf(Pd, 0);                                 \
  }
  // While incrementing signed scalar less than scalar
  INSN(sve_whileltw, 0b0, 0b010);
  INSN(sve_whilelt,  0b1, 0b010);
  // While incrementing signed scalar less than or equal to scalar
  INSN(sve_whilelew, 0b0, 0b011);
  INSN(sve_whilele,  0b1, 0b011);
  // While incrementing unsigned scalar lower than scalar
  INSN(sve_whilelow, 0b0, 0b110);
  INSN(sve_whilelo,  0b1, 0b110);
  // While incrementing unsigned scalar lower than or the same as scalar
  INSN(sve_whilelsw, 0b0, 0b111);
  INSN(sve_whilels,  0b1, 0b111);
#undef INSN

  // SVE predicate reverse
  void sve_rev(PRegister Pd, SIMD_RegVariant T, PRegister Pn) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1101000100000, 21, 9);
    prf(Pn, 5), f(0, 4), prf(Pd, 0);
  }

// SVE partition break condition
#define INSN(NAME, op) \
  void NAME(PRegister Pd, PRegister Pg, PRegister Pn, bool isMerge) {      \
    starti;                                                                \
    f(0b00100101, 31, 24), f(op, 23, 22), f(0b01000001, 21, 14);           \
    prf(Pg, 10), f(0b0, 9), prf(Pn, 5), f(isMerge ? 1 : 0, 4), prf(Pd, 0); \
  }

  INSN(sve_brka, 0b00); // Break after first true condition
  INSN(sve_brkb, 0b10); // Break before first true condition
#undef INSN

// Element count and increment scalar (SVE)
#define INSN(NAME, TYPE)                                                             \
  void NAME(Register Xdn, unsigned imm4 = 1, int pattern = 0b11111) {                \
    starti;                                                                          \
    f(0b00000100, 31, 24), f(TYPE, 23, 22), f(0b10, 21, 20);                         \
    f(imm4 - 1, 19, 16), f(0b11100, 15, 11), f(0, 10), f(pattern, 9, 5), rf(Xdn, 0); \
  }

  INSN(sve_cntb, B);  // Set scalar to multiple of 8-bit predicate constraint element count
  INSN(sve_cnth, H);  // Set scalar to multiple of 16-bit predicate constraint element count
  INSN(sve_cntw, S);  // Set scalar to multiple of 32-bit predicate constraint element count
  INSN(sve_cntd, D);  // Set scalar to multiple of 64-bit predicate constraint element count
#undef INSN

  // Set scalar to active predicate element count
  void sve_cntp(Register Xd, SIMD_RegVariant T, PRegister Pg, PRegister Pn) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00100101, 31, 24), f(T, 23, 22), f(0b10000010, 21, 14);
    prf(Pg, 10), f(0, 9), prf(Pn, 5), rf(Xd, 0);
  }

  // SVE convert signed integer to floating-point (predicated)
  void sve_scvtf(FloatRegister Zd, SIMD_RegVariant T_dst, PRegister Pg,
                 FloatRegister Zn, SIMD_RegVariant T_src) {
    starti;
    assert(T_src != B && T_dst != B && T_src != Q && T_dst != Q &&
           (T_src != H || T_dst == T_src), "invalid register variant");
    int opc = T_dst;
    int opc2 = T_src;
    // In most cases we can treat T_dst, T_src as opc, opc2,
    // except for the following two combinations.
    // +-----+------+---+------------------------------------+
    // | opc | opc2 | U |        Instruction Details         |
    // +-----+------+---+------------------------------------+
    // |  11 |   00 | 0 | SCVTF - 32-bit to double-precision |
    // |  11 |   10 | 0 | SCVTF - 64-bit to single-precision |
    // +-----+------+---+------------------------------------+
    if (T_src == S && T_dst == D) {
      opc = 0b11;
      opc2 = 0b00;
    } else if (T_src == D && T_dst == S) {
      opc = 0b11;
      opc2 = 0b10;
    }
    f(0b01100101, 31, 24), f(opc, 23, 22), f(0b010, 21, 19);
    f(opc2, 18, 17), f(0b0101, 16, 13);
    pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

  // SVE floating-point convert to signed integer, rounding toward zero (predicated)
  void sve_fcvtzs(FloatRegister Zd, SIMD_RegVariant T_dst, PRegister Pg,
                  FloatRegister Zn, SIMD_RegVariant T_src) {
    starti;
    assert(T_src != B && T_dst != B && T_src != Q && T_dst != Q &&
           (T_dst != H || T_src == H), "invalid register variant");
    int opc = T_src;
    int opc2 = T_dst;
    // In most cases we can treat T_src, T_dst as opc, opc2,
    // except for the following two combinations.
    // +-----+------+---+-------------------------------------+
    // | opc | opc2 | U |         Instruction Details         |
    // +-----+------+---+-------------------------------------+
    // |  11 |  10  | 0 | FCVTZS - single-precision to 64-bit |
    // |  11 |  00  | 0 | FCVTZS - double-precision to 32-bit |
    // +-----+------+---+-------------------------------------+
    if (T_src == S && T_dst == D) {
      opc = 0b11;
      opc2 = 0b10;
    } else if (T_src == D && T_dst == S) {
      opc = 0b11;
      opc2 = 0b00;
    }
    f(0b01100101, 31, 24), f(opc, 23, 22), f(0b011, 21, 19);
    f(opc2, 18, 17), f(0b0101, 16, 13);
    pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

  // SVE floating-point convert precision (predicated)
  void sve_fcvt(FloatRegister Zd, SIMD_RegVariant T_dst, PRegister Pg,
                FloatRegister Zn, SIMD_RegVariant T_src) {
    starti;
    assert(T_src != B && T_dst != B && T_src != Q && T_dst != Q &&
           T_src != T_dst, "invalid register variant");
    // The encodings of fields op1 (bits 17-16) and op2 (bits 23-22)
    // depend on T_src and T_dst as given below -
    // +-----+------+---------------------------------------------+
    // | op2 | op1  |             Instruction Details             |
    // +-----+------+---------------------------------------------+
    // |  10 |  01  | FCVT - half-precision to single-precision   |
    // |  11 |  01  | FCVT - half-precision to double-precision   |
    // |  10 |  00  | FCVT - single-precision to half-precision   |
    // |  11 |  11  | FCVT - single-precision to double-precision |
    // |  11 |  00  | FCVT - double-preciison to half-precision   |
    // |  11 |  10  | FCVT - double-precision to single-precision |
    // +-----+------+---+-----------------------------------------+
    int op1 = 0b00;
    int op2 = (T_src == D || T_dst == D) ? 0b11 : 0b10;
    if (T_src == H) {
      op1 = 0b01;
    } else if (T_dst == S) {
      op1 = 0b10;
    } else if (T_dst == D) {
      op1 = 0b11;
    }
    f(0b01100101, 31, 24), f(op2, 23, 22), f(0b0010, 21, 18);
    f(op1, 17, 16), f(0b101, 15, 13);
    pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

// SVE extract element to general-purpose register
#define INSN(NAME, before)                                                      \
  void NAME(Register Rd, SIMD_RegVariant T, PRegister Pg,  FloatRegister Zn) {  \
    starti;                                                                     \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b10000, 21, 17);                    \
    f(before, 16), f(0b101, 15, 13);                                            \
    pgrf(Pg, 10), rf(Zn, 5), rf(Rd, 0);                                         \
  }

  INSN(sve_lasta, 0b0);
  INSN(sve_lastb, 0b1);
#undef INSN

// SVE extract element to SIMD&FP scalar register
#define INSN(NAME, before)                                                           \
  void NAME(FloatRegister Vd, SIMD_RegVariant T, PRegister Pg,  FloatRegister Zn) {  \
    starti;                                                                          \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b10001, 21, 17);                         \
    f(before, 16), f(0b100, 15, 13);                                                 \
    pgrf(Pg, 10), rf(Zn, 5), rf(Vd, 0);                                              \
  }

  INSN(sve_lasta, 0b0);
  INSN(sve_lastb, 0b1);
#undef INSN

// SVE reverse within elements
#define INSN(NAME, opc, cond)                                                        \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg,  FloatRegister Zn) {  \
    starti;                                                                          \
    assert(cond, "invalid size");                                                    \
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1001, 21, 18), f(opc, 17, 16);          \
    f(0b100, 15, 13), pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);                            \
  }

  INSN(sve_revb, 0b00, T == H || T == S || T == D);
  INSN(sve_rbit, 0b11, T != Q);
#undef INSN

  // SVE Create index starting from general-purpose register and incremented by immediate
  void sve_index(FloatRegister Zd, SIMD_RegVariant T, Register Rn, int imm) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000100, 31, 24), f(T, 23, 22), f(0b1, 21);
    sf(imm, 20, 16), f(0b010001, 15, 10);
    rf(Rn, 5), rf(Zd, 0);
  }

  // SVE create index starting from and incremented by immediate
  void sve_index(FloatRegister Zd, SIMD_RegVariant T, int imm1, int imm2) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000100, 31, 24), f(T, 23, 22), f(0b1, 21);
    sf(imm2, 20, 16), f(0b010000, 15, 10);
    sf(imm1, 9, 5), rf(Zd, 0);
  }

  // SVE programmable table lookup/permute using vector of element indices
  void sve_tbl(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, FloatRegister Zm) {
    starti;
    assert(T != Q, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b1, 21), rf(Zm, 16);
    f(0b001100, 15, 10), rf(Zn, 5), rf(Zd, 0);
  }

  // Shuffle active elements of vector to the right and fill with zero
  void sve_compact(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn, PRegister Pg) {
    starti;
    assert(T == S || T == D, "invalid size");
    f(0b00000101, 31, 24), f(T, 23, 22), f(0b100001100, 21, 13);
    pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

  // SVE2 Count matching elements in vector
  void sve_histcnt(FloatRegister Zd, SIMD_RegVariant T, PRegister Pg,
                   FloatRegister Zn, FloatRegister Zm) {
    starti;
    assert(T == S || T == D, "invalid size");
    f(0b01000101, 31, 24), f(T, 23, 22), f(0b1, 21), rf(Zm, 16);
    f(0b110, 15, 13), pgrf(Pg, 10), rf(Zn, 5), rf(Zd, 0);
  }

// SVE2 bitwise permute
#define INSN(NAME, opc)                                                                  \
  void NAME(FloatRegister Zd, SIMD_RegVariant T, FloatRegister Zn,  FloatRegister Zm) {  \
    starti;                                                                              \
    assert(T != Q, "invalid size");                                                      \
    f(0b01000101, 31, 24), f(T, 23, 22), f(0b0, 21);                                     \
    rf(Zm, 16), f(0b1011, 15, 12), f(opc, 11, 10);                                       \
    rf(Zn, 5), rf(Zd, 0);                                                                \
  }

  INSN(sve_bext, 0b00);
  INSN(sve_bdep, 0b01);
#undef INSN

// SVE2 bitwise ternary operations
#define INSN(NAME, opc)                                               \
  void NAME(FloatRegister Zdn, FloatRegister Zm, FloatRegister Zk) {  \
    starti;                                                           \
    f(0b00000100, 31, 24), f(opc, 23, 21), rf(Zm, 16);                \
    f(0b001110, 15, 10), rf(Zk, 5), rf(Zdn, 0);                       \
  }

  INSN(sve_eor3, 0b001); // Bitwise exclusive OR of three vectors
#undef INSN

  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
  }

  // Stack overflow checking
  virtual void bang_stack_with_offset(int offset);

  static bool operand_valid_for_logical_immediate(bool is32, uint64_t imm);
  static bool operand_valid_for_sve_logical_immediate(unsigned elembits, uint64_t imm);
  static bool operand_valid_for_add_sub_immediate(int64_t imm);
  static bool operand_valid_for_sve_add_sub_immediate(int64_t imm);
  static bool operand_valid_for_float_immediate(double imm);
  static int  operand_valid_for_movi_immediate(uint64_t imm64, SIMD_Arrangement T);

  void emit_data64(jlong data, relocInfo::relocType rtype, int format = 0);
  void emit_data64(jlong data, RelocationHolder const& rspec, int format = 0);
};

inline Assembler::Membar_mask_bits operator|(Assembler::Membar_mask_bits a,
                                             Assembler::Membar_mask_bits b) {
  return Assembler::Membar_mask_bits(unsigned(a)|unsigned(b));
}

Instruction_aarch64::~Instruction_aarch64() {
  assem->emit_int32(insn);
  assert_cond(get_bits() == 0xffffffff);
}

#undef f
#undef sf
#undef rf
#undef srf
#undef zrf
#undef prf
#undef pgrf
#undef fixed

#undef starti

// Invert a condition
inline Assembler::Condition operator~(const Assembler::Condition cond) {
  return Assembler::Condition(int(cond) ^ 1);
}

extern "C" void das(uint64_t start, int len);

#endif // CPU_AARCH64_ASSEMBLER_AARCH64_HPP
