/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2015, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_VM_ASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_VM_ASSEMBLER_AARCH64_HPP

#include "asm/register.hpp"

// definitions of various symbolic names for machine registers

// First intercalls between C and Java which use 8 general registers
// and 8 floating registers

// we also have to copy between x86 and ARM registers but that's a
// secondary complication -- not all code employing C call convention
// executes as x86 code though -- we generate some of it

class Argument VALUE_OBJ_CLASS_SPEC {
 public:
  enum {
    n_int_register_parameters_c   = 8,  // r0, r1, ... r7 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8,  // v0, v1, ... v7 (c_farg0, c_farg1, ... )

    n_int_register_parameters_j   = 8, // r1, ... r7, r0 (rj_rarg0, j_rarg1, ...
    n_float_register_parameters_j = 8  // v0, v1, ... v7 (j_farg0, j_farg1, ...
  };
};

REGISTER_DECLARATION(Register, c_rarg0, r0);
REGISTER_DECLARATION(Register, c_rarg1, r1);
REGISTER_DECLARATION(Register, c_rarg2, r2);
REGISTER_DECLARATION(Register, c_rarg3, r3);
REGISTER_DECLARATION(Register, c_rarg4, r4);
REGISTER_DECLARATION(Register, c_rarg5, r5);
REGISTER_DECLARATION(Register, c_rarg6, r6);
REGISTER_DECLARATION(Register, c_rarg7, r7);

REGISTER_DECLARATION(FloatRegister, c_farg0, v0);
REGISTER_DECLARATION(FloatRegister, c_farg1, v1);
REGISTER_DECLARATION(FloatRegister, c_farg2, v2);
REGISTER_DECLARATION(FloatRegister, c_farg3, v3);
REGISTER_DECLARATION(FloatRegister, c_farg4, v4);
REGISTER_DECLARATION(FloatRegister, c_farg5, v5);
REGISTER_DECLARATION(FloatRegister, c_farg6, v6);
REGISTER_DECLARATION(FloatRegister, c_farg7, v7);

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


REGISTER_DECLARATION(Register, j_rarg0, c_rarg1);
REGISTER_DECLARATION(Register, j_rarg1, c_rarg2);
REGISTER_DECLARATION(Register, j_rarg2, c_rarg3);
REGISTER_DECLARATION(Register, j_rarg3, c_rarg4);
REGISTER_DECLARATION(Register, j_rarg4, c_rarg5);
REGISTER_DECLARATION(Register, j_rarg5, c_rarg6);
REGISTER_DECLARATION(Register, j_rarg6, c_rarg7);
REGISTER_DECLARATION(Register, j_rarg7, c_rarg0);

// Java floating args are passed as per C

REGISTER_DECLARATION(FloatRegister, j_farg0, v0);
REGISTER_DECLARATION(FloatRegister, j_farg1, v1);
REGISTER_DECLARATION(FloatRegister, j_farg2, v2);
REGISTER_DECLARATION(FloatRegister, j_farg3, v3);
REGISTER_DECLARATION(FloatRegister, j_farg4, v4);
REGISTER_DECLARATION(FloatRegister, j_farg5, v5);
REGISTER_DECLARATION(FloatRegister, j_farg6, v6);
REGISTER_DECLARATION(FloatRegister, j_farg7, v7);

// registers used to hold VM data either temporarily within a method
// or across method calls

// volatile (caller-save) registers

// r8 is used for indirect result location return
// we use it and r9 as scratch registers
REGISTER_DECLARATION(Register, rscratch1, r8);
REGISTER_DECLARATION(Register, rscratch2, r9);

// current method -- must be in a call-clobbered register
REGISTER_DECLARATION(Register, rmethod,   r12);

// non-volatile (callee-save) registers are r16-29
// of which the following are dedicated global state

// link register
REGISTER_DECLARATION(Register, lr,        r30);
// frame pointer
REGISTER_DECLARATION(Register, rfp,       r29);
// current thread
REGISTER_DECLARATION(Register, rthread,   r28);
// base of heap
REGISTER_DECLARATION(Register, rheapbase, r27);
// constant pool cache
REGISTER_DECLARATION(Register, rcpool,    r26);
// monitors allocated on stack
REGISTER_DECLARATION(Register, rmonitors, r25);
// locals on stack
REGISTER_DECLARATION(Register, rlocals,   r24);
// bytecode pointer
REGISTER_DECLARATION(Register, rbcp,      r22);
// Dispatch table base
REGISTER_DECLARATION(Register, rdispatch, r21);
// Java stack pointer
REGISTER_DECLARATION(Register, esp,      r20);

#define assert_cond(ARG1) assert(ARG1, #ARG1)

namespace asm_util {
  uint32_t encode_logical_immediate(bool is32, uint64_t imm);
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
    uint32_t mask = (1U << nbits) - 1;
    uint32_t result = val >> lsb;
    result &= mask;
    return result;
  }

  static inline int32_t sextract(uint32_t val, int msb, int lsb) {
    uint32_t uval = extract(val, msb, lsb);
    return extend(uval, msb - lsb);
  }

  static void patch(address a, int msb, int lsb, unsigned long val) {
    int nbits = msb - lsb + 1;
    guarantee(val < (1U << nbits), "Field too big for insn");
    assert_cond(msb >= lsb);
    unsigned mask = (1U << nbits) - 1;
    val <<= lsb;
    mask <<= lsb;
    unsigned target = *(unsigned *)a;
    target &= ~mask;
    target |= val;
    *(unsigned *)a = target;
  }

  static void spatch(address a, int msb, int lsb, long val) {
    int nbits = msb - lsb + 1;
    long chk = val >> (nbits - 1);
    guarantee (chk == -1 || chk == 0, "Field too big for insn");
    unsigned uval = val;
    unsigned mask = (1U << nbits) - 1;
    uval &= mask;
    uval <<= lsb;
    mask <<= lsb;
    unsigned target = *(unsigned *)a;
    target &= ~mask;
    target |= uval;
    *(unsigned *)a = target;
  }

  void f(unsigned val, int msb, int lsb) {
    int nbits = msb - lsb + 1;
    guarantee(val < (1U << nbits), "Field too big for insn");
    assert_cond(msb >= lsb);
    unsigned mask = (1U << nbits) - 1;
    val <<= lsb;
    mask <<= lsb;
    insn |= val;
    assert_cond((bits & mask) == 0);
#ifdef ASSERT
    bits |= mask;
#endif
  }

  void f(unsigned val, int bit) {
    f(val, bit, bit);
  }

  void sf(long val, int msb, int lsb) {
    int nbits = msb - lsb + 1;
    long chk = val >> (nbits - 1);
    guarantee (chk == -1 || chk == 0, "Field too big for insn");
    unsigned uval = val;
    unsigned mask = (1U << nbits) - 1;
    uval &= mask;
    f(uval, lsb + nbits - 1, lsb);
  }

  void rf(Register r, int lsb) {
    f(r->encoding_nocheck(), lsb + 4, lsb);
  }

  // reg|ZR
  void zrf(Register r, int lsb) {
    f(r->encoding_nocheck() - (r == zr), lsb + 4, lsb);
  }

  // reg|SP
  void srf(Register r, int lsb) {
    f(r == sp ? 31 : r->encoding_nocheck(), lsb + 4, lsb);
  }

  void rf(FloatRegister r, int lsb) {
    f(r->encoding_nocheck(), lsb + 4, lsb);
  }

  unsigned get(int msb = 31, int lsb = 0) {
    int nbits = msb - lsb + 1;
    unsigned mask = ((1U << nbits) - 1) << lsb;
    assert_cond(bits & mask == mask);
    return (insn & mask) >> lsb;
  }

  void fixed(unsigned value, unsigned mask) {
    assert_cond ((mask & bits) == 0);
#ifdef ASSERT
    bits |= mask;
#endif
    insn |= value;
  }
};

#define starti Instruction_aarch64 do_not_use(this); set_current(&do_not_use)

class PrePost {
  int _offset;
  Register _r;
public:
  PrePost(Register reg, int o) : _r(reg), _offset(o) { }
  int offset() { return _offset; }
  Register reg() { return _r; }
};

class Pre : public PrePost {
public:
  Pre(Register reg, int o) : PrePost(reg, o) { }
};
class Post : public PrePost {
public:
  Post(Register reg, int o) : PrePost(reg, o) { }
};

namespace ext
{
  enum operation { uxtb, uxth, uxtw, uxtx, sxtb, sxth, sxtw, sxtx };
};

// abs methods which cannot overflow and so are well-defined across
// the entire domain of integer types.
static inline unsigned int uabs(unsigned int n) {
  union {
    unsigned int result;
    int value;
  };
  result = n;
  if (value < 0) result = -result;
  return result;
}
static inline unsigned long uabs(unsigned long n) {
  union {
    unsigned long result;
    long value;
  };
  result = n;
  if (value < 0) result = -result;
  return result;
}
static inline unsigned long uabs(long n) { return uabs((unsigned long)n); }
static inline unsigned long uabs(int n) { return uabs((unsigned int)n); }

// Addressing modes
class Address VALUE_OBJ_CLASS_SPEC {
 public:

  enum mode { no_mode, base_plus_offset, pre, post, pcrel,
              base_plus_offset_reg, literal };

  // Shift and extend for base reg + reg offset addressing
  class extend {
    int _option, _shift;
    ext::operation _op;
  public:
    extend() { }
    extend(int s, int o, ext::operation op) : _shift(s), _option(o), _op(op) { }
    int option() const{ return _option; }
    int shift() const { return _shift; }
    ext::operation op() const { return _op; }
  };
  class uxtw : public extend {
  public:
    uxtw(int shift = -1): extend(shift, 0b010, ext::uxtw) { }
  };
  class lsl : public extend {
  public:
    lsl(int shift = -1): extend(shift, 0b011, ext::uxtx) { }
  };
  class sxtw : public extend {
  public:
    sxtw(int shift = -1): extend(shift, 0b110, ext::sxtw) { }
  };
  class sxtx : public extend {
  public:
    sxtx(int shift = -1): extend(shift, 0b111, ext::sxtx) { }
  };

 private:
  Register _base;
  Register _index;
  long _offset;
  enum mode _mode;
  extend _ext;

  RelocationHolder _rspec;

  // Typically we use AddressLiterals we want to use their rval
  // However in some situations we want the lval (effect address) of
  // the item.  We provide a special factory for making those lvals.
  bool _is_lval;

  // If the target is far we'll need to load the ea of this to a
  // register to reach it. Otherwise if near we can do PC-relative
  // addressing.
  address          _target;

 public:
  Address()
    : _mode(no_mode) { }
  Address(Register r)
    : _mode(base_plus_offset), _base(r), _offset(0), _index(noreg), _target(0) { }
  Address(Register r, int o)
    : _mode(base_plus_offset), _base(r), _offset(o), _index(noreg), _target(0) { }
  Address(Register r, long o)
    : _mode(base_plus_offset), _base(r), _offset(o), _index(noreg), _target(0) { }
  Address(Register r, unsigned long o)
    : _mode(base_plus_offset), _base(r), _offset(o), _index(noreg), _target(0) { }
#ifdef ASSERT
  Address(Register r, ByteSize disp)
    : _mode(base_plus_offset), _base(r), _offset(in_bytes(disp)),
      _index(noreg), _target(0) { }
#endif
  Address(Register r, Register r1, extend ext = lsl())
    : _mode(base_plus_offset_reg), _base(r), _index(r1),
    _ext(ext), _offset(0), _target(0) { }
  Address(Pre p)
    : _mode(pre), _base(p.reg()), _offset(p.offset()) { }
  Address(Post p)
    : _mode(post), _base(p.reg()), _offset(p.offset()), _target(0) { }
  Address(address target, RelocationHolder const& rspec)
    : _mode(literal),
      _rspec(rspec),
      _is_lval(false),
      _target(target)  { }
  Address(address target, relocInfo::relocType rtype = relocInfo::external_word_type);
  Address(Register base, RegisterOrConstant index, extend ext = lsl())
    : _base (base),
      _ext(ext), _offset(0), _target(0) {
    if (index.is_register()) {
      _mode = base_plus_offset_reg;
      _index = index.as_register();
    } else {
      guarantee(ext.option() == ext::uxtx, "should be");
      assert(index.is_constant(), "should be");
      _mode = base_plus_offset;
      _offset = index.as_constant() << ext.shift();
    }
  }

  Register base() const {
    guarantee((_mode == base_plus_offset | _mode == base_plus_offset_reg
               | _mode == post),
              "wrong mode");
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
  bool uses(Register reg) const { return _base == reg || _index == reg; }
  address target() const { return _target; }
  const RelocationHolder& rspec() const { return _rspec; }

  void encode(Instruction_aarch64 *i) const {
    i->f(0b111, 29, 27);
    i->srf(_base, 5);

    switch(_mode) {
    case base_plus_offset:
      {
        unsigned size = i->get(31, 30);
        if (i->get(26, 26) && i->get(23, 23)) {
          // SIMD Q Type - Size = 128 bits
          assert(size == 0, "bad size");
          size = 0b100;
        }
        unsigned mask = (1 << size) - 1;
        if (_offset < 0 || _offset & mask)
          {
            i->f(0b00, 25, 24);
            i->f(0, 21), i->f(0b00, 11, 10);
            i->sf(_offset, 20, 12);
          } else {
            i->f(0b01, 25, 24);
            i->f(_offset >> size, 21, 10);
          }
      }
      break;

    case base_plus_offset_reg:
      {
        i->f(0b00, 25, 24);
        i->f(1, 21);
        i->rf(_index, 16);
        i->f(_ext.option(), 15, 13);
        unsigned size = i->get(31, 30);
        if (i->get(26, 26) && i->get(23, 23)) {
          // SIMD Q Type - Size = 128 bits
          assert(size == 0, "bad size");
          size = 0b100;
        }
        if (size == 0) // It's a byte
          i->f(_ext.shift() >= 0, 12);
        else {
          if (_ext.shift() > 0)
            assert(_ext.shift() == (int)size, "bad shift");
          i->f(_ext.shift() > 0, 12);
        }
        i->f(0b10, 11, 10);
      }
      break;

    case pre:
      i->f(0b00, 25, 24);
      i->f(0, 21), i->f(0b11, 11, 10);
      i->sf(_offset, 20, 12);
      break;

    case post:
      i->f(0b00, 25, 24);
      i->f(0, 21), i->f(0b01, 11, 10);
      i->sf(_offset, 20, 12);
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
    guarantee(_offset % size == 0, "bad offset");
    i->sf(_offset / size, 21, 15);
    i->srf(_base, 5);
  }

  void encode_nontemporal_pair(Instruction_aarch64 *i) const {
    // Only base + offset is allowed
    i->f(0b000, 25, 23);
    unsigned size = i->get(31, 31);
    size = 4 << size;
    guarantee(_offset % size == 0, "bad offset");
    i->sf(_offset / size, 21, 15);
    i->srf(_base, 5);
    guarantee(_mode == Address::base_plus_offset,
              "Bad addressing mode for non-temporal op");
  }

  void lea(MacroAssembler *, Register) const;

  static bool offset_ok_for_immed(long offset, int shift = 0) {
    unsigned mask = (1 << shift) - 1;
    if (offset < 0 || offset & mask) {
      return (uabs(offset) < (1 << (20 - 12))); // Unscaled offset
    } else {
      return ((offset >> shift) < (1 << (21 - 10 + 1))); // Scaled, unsigned offset
    }
  }
};

// Convience classes
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

const int FPUStateSizeInWords = 32 * 2;
typedef enum {
  PLDL1KEEP = 0b00000, PLDL1STRM, PLDL2KEEP, PLDL2STRM, PLDL3KEEP, PLDL3STRM,
  PSTL1KEEP = 0b10000, PSTL1STRM, PSTL2KEEP, PSTL2STRM, PSTL3KEEP, PSTL3STRM,
  PLIL1KEEP = 0b01000, PLIL1STRM, PLIL2KEEP, PLIL2STRM, PLIL3KEEP, PLIL3STRM
} prfop;

class Assembler : public AbstractAssembler {

#ifndef PRODUCT
  static const unsigned long asm_bp;

  void emit_long(jint x) {
    if ((unsigned long)pc() == asm_bp)
      asm volatile ("nop");
    AbstractAssembler::emit_int32(x);
  }
#else
  void emit_long(jint x) {
    AbstractAssembler::emit_int32(x);
  }
#endif

public:

  enum { instruction_size = 4 };

  Address adjust(Register base, int offset, bool preIncrement) {
    if (preIncrement)
      return Address(Pre(base, offset));
    else
      return Address(Post(base, offset));
  }

  Address pre(Register base, int offset) {
    return adjust(base, offset, true);
  }

  Address post (Register base, int offset) {
    return adjust(base, offset, false);
  }

  Instruction_aarch64* current;

  void set_current(Instruction_aarch64* i) { current = i; }

  void f(unsigned val, int msb, int lsb) {
    current->f(val, msb, lsb);
  }
  void f(unsigned val, int msb) {
    current->f(val, msb, msb);
  }
  void sf(long val, int msb, int lsb) {
    current->sf(val, msb, lsb);
  }
  void rf(Register reg, int lsb) {
    current->rf(reg, lsb);
  }
  void srf(Register reg, int lsb) {
    current->srf(reg, lsb);
  }
  void zrf(Register reg, int lsb) {
    current->zrf(reg, lsb);
  }
  void rf(FloatRegister reg, int lsb) {
    current->rf(reg, lsb);
  }
  void fixed(unsigned value, unsigned mask) {
    current->fixed(value, mask);
  }

  void emit() {
    emit_long(current->get_insn());
    assert_cond(current->get_bits() == 0xffffffff);
    current = NULL;
  }

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

  void adrp(Register Rd, const Address &dest, unsigned long &offset);

#undef INSN

  void add_sub_immediate(Register Rd, Register Rn, unsigned uimm, int op,
                         int negated_op);

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
    add_sub_immediate(Rd, Rn, imm, decode, negated);                    \
  }

  INSN(addsw, 0b001, 0b011);
  INSN(subsw, 0b011, 0b001);
  INSN(adds,  0b101, 0b111);
  INSN(subs,  0b111, 0b101);

#undef INSN

#define INSN(NAME, decode, negated)                     \
  void NAME(Register Rd, Register Rn, unsigned imm) {   \
    starti;                                             \
    add_sub_immediate(Rd, Rn, imm, decode, negated);    \
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
  INSN(andr,  0b100, false);
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
    rf(Rd, 0);                                                          \
  }

  INSN(movnw, 0b000);
  INSN(movzw, 0b010);
  INSN(movkw, 0b011);
  INSN(movn, 0b100);
  INSN(movz, 0b110);
  INSN(movk, 0b111);

#undef INSN

  // Bitfield
#define INSN(NAME, opcode)                                              \
  void NAME(Register Rd, Register Rn, unsigned immr, unsigned imms) {   \
    starti;                                                             \
    f(opcode, 31, 22), f(immr, 21, 16), f(imms, 15, 10);                \
    rf(Rn, 5), rf(Rd, 0);                                               \
  }

  INSN(sbfmw, 0b0001001100);
  INSN(bfmw,  0b0011001100);
  INSN(ubfmw, 0b0101001100);
  INSN(sbfm,  0b1001001101);
  INSN(bfm,   0b1011001101);
  INSN(ubfm,  0b1101001101);

#undef INSN

  // Extract
#define INSN(NAME, opcode)                                              \
  void NAME(Register Rd, Register Rn, Register Rm, unsigned imms) {     \
    starti;                                                             \
    f(opcode, 31, 21), f(imms, 15, 10);                                 \
    rf(Rm, 16), rf(Rn, 5), rf(Rd, 0);                                   \
  }

  INSN(extrw, 0b00010011100);
  INSN(extr,  0b10010011110);

#undef INSN

  // The maximum range of a branch is fixed for the AArch64
  // architecture.  In debug mode we shrink it in order to test
  // trampolines, but not so small that branches in the interpreter
  // are out of range.
  static const unsigned long branch_range = INCLUDE_JVMCI ? 128 * M : NOT_DEBUG(128 * M) DEBUG_ONLY(2 * M);

  static bool reachable_from_branch_at(address branch, address target) {
    return uabs(target - branch) < branch_range;
  }

  // Unconditional branch (immediate)
#define INSN(NAME, opcode)                                              \
  void NAME(address dest) {                                             \
    starti;                                                             \
    long offset = (dest - pc()) >> 2;                                   \
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
    long offset = (dest - pc()) >> 2;                   \
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
    long offset = (dest - pc()) >> 2;                                   \
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
    long offset = (dest - pc()) >> 2;
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
  INSN(dpcs1, 0b101, 0, 0b01);
  INSN(dpcs2, 0b101, 0, 0b10);
  INSN(dpcs3, 0b101, 0, 0b11);

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

  void hint(int imm) {
    system(0b00, 0b011, 0b0010, imm, 0b000);
  }

  void nop() {
    hint(0);
  }
  // we only provide mrs and msr for the special purpose system
  // registers where op1 (instr[20:19]) == 11 and, (currently) only
  // use it for FPSR n.b msr has L (instr[21]) == 0 mrs has L == 1

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
           Register rt = (Register)0b11111) {
    system(0b01, op1, CRn, CRm, op2, rt);
  }

  // Only implement operations accessible from EL0 or higher, i.e.,
  //            op1    CRn    CRm    op2
  // IC IVAU     3      7      5      1
  // DC CVAC     3      7      10     1
  // DC CVAU     3      7      11     1
  // DC CIVAC    3      7      14     1
  // DC ZVA      3      7      4      1
  // So only deal with the CRm field.
  enum icache_maintenance {IVAU = 0b0101};
  enum dcache_maintenance {CVAC = 0b1010, CVAU = 0b1011, CIVAC = 0b1110, ZVA = 0b100};

  void dc(dcache_maintenance cm, Register Rt) {
    sys(0b011, 0b0111, cm, 0b001, Rt);
  }

  void ic(icache_maintenance cm, Register Rt) {
    sys(0b011, 0b0111, cm, 0b001, Rt);
  }

  // A more convenient access to dmb for our purposes
  enum Membar_mask_bits {
    // We can use ISH for a barrier because the ARM ARM says "This
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
  void branch_reg(Register R, int opc) {
    starti;
    f(0b1101011, 31, 25);
    f(opc, 24, 21);
    f(0b11111000000, 20, 10);
    rf(R, 5);
    f(0b00000, 4, 0);
  }

#define INSN(NAME, opc)                         \
  void NAME(Register R) {                       \
    branch_reg(R, opc);                         \
  }

  INSN(br, 0b0000);
  INSN(blr, 0b0001);
  INSN(ret, 0b0010);

  void ret(void *p); // This forces a compile-time error for ret(0)

#undef INSN

#define INSN(NAME, opc)                         \
  void NAME() {                 \
    branch_reg(dummy_reg, opc);         \
  }

  INSN(eret, 0b0100);
  INSN(drps, 0b0101);

#undef INSN

  // Load/store exclusive
  enum operand_size { byte, halfword, word, xword };

  void load_store_exclusive(Register Rs, Register Rt1, Register Rt2,
    Register Rn, enum operand_size sz, int op, bool ordered) {
    starti;
    f(sz, 31, 30), f(0b001000, 29, 24), f(op, 23, 21);
    rf(Rs, 16), f(ordered, 15), rf(Rt2, 10), rf(Rn, 5), rf(Rt1, 0);
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
  INSN3(stxrb, byte, 0b000, 0);
  INSN3(stlxrb, byte, 0b000, 1);
  INSN2(ldxrb, byte, 0b010, 0);
  INSN2(ldaxrb, byte, 0b010, 1);
  INSN2(stlrb, byte, 0b100, 1);
  INSN2(ldarb, byte, 0b110, 1);

  // halfwords
  INSN3(stxrh, halfword, 0b000, 0);
  INSN3(stlxrh, halfword, 0b000, 1);
  INSN2(ldxrh, halfword, 0b010, 0);
  INSN2(ldaxrh, halfword, 0b010, 1);
  INSN2(stlrh, halfword, 0b100, 1);
  INSN2(ldarh, halfword, 0b110, 1);

  // words
  INSN3(stxrw, word, 0b000, 0);
  INSN3(stlxrw, word, 0b000, 1);
  INSN4(stxpw, word, 0b001, 0);
  INSN4(stlxpw, word, 0b001, 1);
  INSN2(ldxrw, word, 0b010, 0);
  INSN2(ldaxrw, word, 0b010, 1);
  INSN_FOO(ldxpw, word, 0b011, 0);
  INSN_FOO(ldaxpw, word, 0b011, 1);
  INSN2(stlrw, word, 0b100, 1);
  INSN2(ldarw, word, 0b110, 1);

  // xwords
  INSN3(stxr, xword, 0b000, 0);
  INSN3(stlxr, xword, 0b000, 1);
  INSN4(stxp, xword, 0b001, 0);
  INSN4(stlxp, xword, 0b001, 1);
  INSN2(ldxr, xword, 0b010, 0);
  INSN2(ldaxr, xword, 0b010, 1);
  INSN_FOO(ldxp, xword, 0b011, 0);
  INSN_FOO(ldaxp, xword, 0b011, 1);
  INSN2(stlr, xword, 0b100, 1);
  INSN2(ldar, xword, 0b110, 1);

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
    f(sz, 31, 30), f(0b001000, 29, 24), f(1, 23), f(a, 22), f(1, 21);
    rf(Rs, 16), f(r, 15), f(0b11111, 14, 10), rf(Rn, 5), rf(Rt, 0);
  }

  // CAS
#define INSN(NAME, a, r)                                                \
  void NAME(operand_size sz, Register Rs, Register Rt, Register Rn) {   \
    assert(Rs != Rn && Rs != Rt, "unpredictable instruction");          \
    lse_cas(Rs, Rt, Rn, sz, a, r, true);                                \
  }
  INSN(cas,    false, false)
  INSN(casa,   true,  false)
  INSN(casl,   false, true)
  INSN(casal,  true,  true)
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
  INSN(casp,    false, false)
  INSN(caspa,   true,  false)
  INSN(caspl,   false, true)
  INSN(caspal,  true,  true)
#undef INSN

  // 8.1 Atomic operations
  void lse_atomic(Register Rs, Register Rt, Register Rn,
                  enum operand_size sz, int op1, int op2, bool a, bool r) {
    starti;
    f(sz, 31, 30), f(0b111000, 29, 24), f(a, 23), f(r, 22), f(1, 21);
    rf(Rs, 16), f(op1, 15), f(op2, 14, 12), f(0, 11, 10), rf(Rn, 5), zrf(Rt, 0);
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
    long offset = (dest - pc()) >> 2;                                   \
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
    long offset = (dest - pc()) >> 2;                                   \
    starti;                                                             \
    f(opc, 31, 30), f(0b011, 29, 27), f(V, 26), f(0b00, 25, 24),        \
      sf(offset, 23, 5);                                                \
    rf((Register)Rt, 0);                                                \
  }

  INSN(ldrs, 0b00, 1);
  INSN(ldrd, 0b01, 1);
  INSN(ldrq, 0b10, 1);

#undef INSN

#define INSN(NAME, opc, V)                                              \
  void NAME(address dest, prfop op = PLDL1KEEP) {                       \
    long offset = (dest - pc()) >> 2;                                   \
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
      adr.encode_nontemporal_pair(current);
    } else {
      adr.encode_pair(current);
    }
  }

  // Load/store register pair (offset)
#define INSN(NAME, size, p1, V, L, no_allocate)         \
  void NAME(Register Rt1, Register Rt2, Address adr) {  \
    ld_st1(size, p1, V, L, Rt1, Rt2, adr, no_allocate); \
   }

  INSN(stpw, 0b00, 0b101, 0, 0, false);
  INSN(ldpw, 0b00, 0b101, 0, 1, false);
  INSN(ldpsw, 0b01, 0b101, 0, 1, false);
  INSN(stp, 0b10, 0b101, 0, 0, false);
  INSN(ldp, 0b10, 0b101, 0, 1, false);

  // Load/store no-allocate pair (offset)
  INSN(stnpw, 0b00, 0b101, 0, 0, true);
  INSN(ldnpw, 0b00, 0b101, 0, 1, true);
  INSN(stnp, 0b10, 0b101, 0, 0, true);
  INSN(ldnp, 0b10, 0b101, 0, 1, true);

#undef INSN

#define INSN(NAME, size, p1, V, L, no_allocate)                         \
  void NAME(FloatRegister Rt1, FloatRegister Rt2, Address adr) {        \
    ld_st1(size, p1, V, L, (Register)Rt1, (Register)Rt2, adr, no_allocate); \
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
      long offset = (adr.target() - pc()) >> 2;
      sf(offset, 23, 5);
      code_section()->relocate(pc(), adr.rspec());
      return;
    }

    f(size, 31, 30);
    f(op, 23, 22); // str
    adr.encode(current);
  }

#define INSN(NAME, size, op)                            \
  void NAME(Register Rt, const Address &adr) {          \
    ld_st2(Rt, adr, size, op);                          \
  }                                                     \

  INSN(str, 0b11, 0b00);
  INSN(strw, 0b10, 0b00);
  INSN(strb, 0b00, 0b00);
  INSN(strh, 0b01, 0b00);

  INSN(ldr, 0b11, 0b01);
  INSN(ldrw, 0b10, 0b01);
  INSN(ldrb, 0b00, 0b01);
  INSN(ldrh, 0b01, 0b01);

  INSN(ldrsb, 0b00, 0b10);
  INSN(ldrsbw, 0b00, 0b11);
  INSN(ldrsh, 0b01, 0b10);
  INSN(ldrshw, 0b01, 0b11);
  INSN(ldrsw, 0b10, 0b10);

#undef INSN

#define INSN(NAME, size, op)                                    \
  void NAME(const Address &adr, prfop pfop = PLDL1KEEP) {       \
    ld_st2((Register)pfop, adr, size, op);                      \
  }

  INSN(prfm, 0b11, 0b10); // FIXME: PRFM should not be used with
                          // writeback modes, but the assembler
                          // doesn't enfore that.

#undef INSN

#define INSN(NAME, size, op)                            \
  void NAME(FloatRegister Rt, const Address &adr) {     \
    ld_st2((Register)Rt, adr, size, op, 1);             \
  }

  INSN(strd, 0b11, 0b00);
  INSN(strs, 0b10, 0b00);
  INSN(ldrd, 0b11, 0b01);
  INSN(ldrs, 0b10, 0b01);
  INSN(strq, 0b00, 0b10);
  INSN(ldrq, 0x00, 0b11);

#undef INSN

  enum shift_kind { LSL, LSR, ASR, ROR };

  void op_shifted_reg(unsigned decode,
                      enum shift_kind kind, unsigned shift,
                      unsigned size, unsigned op) {
    f(size, 31);
    f(op, 30, 29);
    f(decode, 28, 24);
    f(shift, 15, 10);
    f(kind, 23, 22);
  }

  // Logical (shifted register)
#define INSN(NAME, size, op, N)                                 \
  void NAME(Register Rd, Register Rn, Register Rm,              \
            enum shift_kind kind = LSL, unsigned shift = 0) {   \
    starti;                                                     \
    f(N, 21);                                                   \
    zrf(Rm, 16), zrf(Rn, 5), zrf(Rd, 0);                        \
    op_shifted_reg(0b01010, kind, shift, size, op);             \
  }

  INSN(andr, 1, 0b00, 0);
  INSN(orr, 1, 0b01, 0);
  INSN(eor, 1, 0b10, 0);
  INSN(ands, 1, 0b11, 0);
  INSN(andw, 0, 0b00, 0);
  INSN(orrw, 0, 0b01, 0);
  INSN(eorw, 0, 0b10, 0);
  INSN(andsw, 0, 0b11, 0);

  INSN(bic, 1, 0b00, 1);
  INSN(orn, 1, 0b01, 1);
  INSN(eon, 1, 0b10, 1);
  INSN(bics, 1, 0b11, 1);
  INSN(bicw, 0, 0b00, 1);
  INSN(ornw, 0, 0b01, 1);
  INSN(eonw, 0, 0b10, 1);
  INSN(bicsw, 0, 0b11, 1);

#undef INSN

  // Add/subtract (shifted register)
#define INSN(NAME, size, op)                            \
  void NAME(Register Rd, Register Rn, Register Rm,      \
            enum shift_kind kind, unsigned shift = 0) { \
    starti;                                             \
    f(0, 21);                                           \
    assert_cond(kind != ROR);                           \
    zrf(Rd, 0), zrf(Rn, 5), zrf(Rm, 16);                \
    op_shifted_reg(0b01011, kind, shift, size, op);     \
  }

  INSN(add, 1, 0b000);
  INSN(sub, 1, 0b10);
  INSN(addw, 0, 0b000);
  INSN(subw, 0, 0b10);

  INSN(adds, 1, 0b001);
  INSN(subs, 1, 0b11);
  INSN(addsw, 0, 0b001);
  INSN(subsw, 0, 0b11);

#undef INSN

  // Add/subtract (extended register)
#define INSN(NAME, op)                                                  \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    starti;                                                             \
    zrf(Rm, 16), srf(Rn, 5), srf(Rd, 0);                                \
    add_sub_extended_reg(op, 0b01011, Rd, Rn, Rm, 0b00, option, amount); \
  }

  void add_sub_extended_reg(unsigned op, unsigned decode,
    Register Rd, Register Rn, Register Rm,
    unsigned opt, ext::operation option, unsigned imm) {
    guarantee(imm <= 4, "shift amount must be < 4");
    f(op, 31, 29), f(decode, 28, 24), f(opt, 23, 22), f(1, 21);
    f(option, 15, 13), f(imm, 12, 10);
  }

  INSN(addw, 0b000);
  INSN(subw, 0b010);
  INSN(add, 0b100);
  INSN(sub, 0b110);

#undef INSN

#define INSN(NAME, op)                                                  \
  void NAME(Register Rd, Register Rn, Register Rm,                      \
           ext::operation option, int amount = 0) {                     \
    starti;                                                             \
    zrf(Rm, 16), srf(Rn, 5), zrf(Rd, 0);                                \
    add_sub_extended_reg(op, 0b01011, Rd, Rn, Rm, 0b00, option, amount); \
  }

  INSN(addsw, 0b001);
  INSN(subsw, 0b011);
  INSN(adds, 0b101);
  INSN(subs, 0b111);

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

  INSN(adcw, 0b000);
  INSN(adcsw, 0b001);
  INSN(sbcw, 0b010);
  INSN(sbcsw, 0b011);
  INSN(adc, 0b100);
  INSN(adcs, 0b101);
  INSN(sbc,0b110);
  INSN(sbcs, 0b111);

#undef INSN

  // Conditional compare (both kinds)
  void conditional_compare(unsigned op, int o2, int o3,
                           Register Rn, unsigned imm5, unsigned nzcv,
                           unsigned cond) {
    f(op, 31, 29);
    f(0b11010010, 28, 21);
    f(cond, 15, 12);
    f(o2, 10);
    f(o3, 4);
    f(nzcv, 3, 0);
    f(imm5, 20, 16), rf(Rn, 5);
  }

#define INSN(NAME, op)                                                  \
  void NAME(Register Rn, Register Rm, int imm, Condition cond) {        \
    starti;                                                             \
    f(0, 11);                                                           \
    conditional_compare(op, 0, 0, Rn, (uintptr_t)Rm, imm, cond);        \
  }                                                                     \
                                                                        \
  void NAME(Register Rn, int imm5, int imm, Condition cond) {   \
    starti;                                                             \
    f(1, 11);                                                           \
    conditional_compare(op, 0, 0, Rn, imm5, imm, cond);                 \
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
  void NAME(Register Rd, Register Rn, Register Rm, Condition cond) { \
    conditional_select(op, op2, Rd, Rn, Rm, cond);                      \
  }

  INSN(cselw, 0b000, 0b00);
  INSN(csincw, 0b000, 0b01);
  INSN(csinvw, 0b010, 0b00);
  INSN(csnegw, 0b010, 0b01);
  INSN(csel, 0b100, 0b00);
  INSN(csinc, 0b100, 0b01);
  INSN(csinv, 0b110, 0b00);
  INSN(csneg, 0b110, 0b01);

#undef INSN

  // Data processing
  void data_processing(unsigned op29, unsigned opcode,
                       Register Rd, Register Rn) {
    f(op29, 31, 29), f(0b11010110, 28, 21);
    f(opcode, 15, 10);
    rf(Rn, 5), rf(Rd, 0);
  }

  // (1 source)
#define INSN(NAME, op29, opcode2, opcode)       \
  void NAME(Register Rd, Register Rn) {         \
    starti;                                     \
    f(opcode2, 20, 16);                         \
    data_processing(op29, opcode, Rd, Rn);      \
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

#undef INSN

  // (2 sources)
#define INSN(NAME, op29, opcode)                        \
  void NAME(Register Rd, Register Rn, Register Rm) {    \
    starti;                                             \
    rf(Rm, 16);                                         \
    data_processing(op29, opcode, Rd, Rn);              \
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

  // (3 sources)
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

  INSN(maddw, 0b000, 0b000, 0);
  INSN(msubw, 0b000, 0b000, 1);
  INSN(madd, 0b100, 0b000, 0);
  INSN(msub, 0b100, 0b000, 1);
  INSN(smaddl, 0b100, 0b001, 0);
  INSN(smsubl, 0b100, 0b001, 1);
  INSN(umaddl, 0b100, 0b101, 0);
  INSN(umsubl, 0b100, 0b101, 1);

#undef INSN

#define INSN(NAME, op54, op31, o0)                      \
  void NAME(Register Rd, Register Rn, Register Rm) {    \
    data_processing(op54, op31, o0, Rd, Rn, Rm, (Register)31);  \
  }

  INSN(smulh, 0b100, 0b010, 0);
  INSN(umulh, 0b100, 0b110, 0);

#undef INSN

  // Floating-point data-processing (1 source)
  void data_processing(unsigned op31, unsigned type, unsigned opcode,
                       FloatRegister Vd, FloatRegister Vn) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21), f(opcode, 20, 15), f(0b10000, 14, 10);
    rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, op31, type, opcode)                  \
  void NAME(FloatRegister Vd, FloatRegister Vn) {       \
    data_processing(op31, type, opcode, Vd, Vn);        \
  }

private:
  INSN(i_fmovs, 0b000, 0b00, 0b000000);
public:
  INSN(fabss, 0b000, 0b00, 0b000001);
  INSN(fnegs, 0b000, 0b00, 0b000010);
  INSN(fsqrts, 0b000, 0b00, 0b000011);
  INSN(fcvts, 0b000, 0b00, 0b000101);   // Single-precision to double-precision

private:
  INSN(i_fmovd, 0b000, 0b01, 0b000000);
public:
  INSN(fabsd, 0b000, 0b01, 0b000001);
  INSN(fnegd, 0b000, 0b01, 0b000010);
  INSN(fsqrtd, 0b000, 0b01, 0b000011);
  INSN(fcvtd, 0b000, 0b01, 0b000100);   // Double-precision to single-precision

  void fmovd(FloatRegister Vd, FloatRegister Vn) {
    assert(Vd != Vn, "should be");
    i_fmovd(Vd, Vn);
  }

  void fmovs(FloatRegister Vd, FloatRegister Vn) {
    assert(Vd != Vn, "should be");
    i_fmovs(Vd, Vn);
  }

#undef INSN

  // Floating-point data-processing (2 source)
  void data_processing(unsigned op31, unsigned type, unsigned opcode,
                       FloatRegister Vd, FloatRegister Vn, FloatRegister Vm) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21), f(opcode, 15, 12), f(0b10, 11, 10);
    rf(Vm, 16), rf(Vn, 5), rf(Vd, 0);
  }

#define INSN(NAME, op31, type, opcode)                  \
  void NAME(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm) {     \
    data_processing(op31, type, opcode, Vd, Vn, Vm);    \
  }

  INSN(fmuls, 0b000, 0b00, 0b0000);
  INSN(fdivs, 0b000, 0b00, 0b0001);
  INSN(fadds, 0b000, 0b00, 0b0010);
  INSN(fsubs, 0b000, 0b00, 0b0011);
  INSN(fnmuls, 0b000, 0b00, 0b1000);

  INSN(fmuld, 0b000, 0b01, 0b0000);
  INSN(fdivd, 0b000, 0b01, 0b0001);
  INSN(faddd, 0b000, 0b01, 0b0010);
  INSN(fsubd, 0b000, 0b01, 0b0011);
  INSN(fnmuld, 0b000, 0b01, 0b1000);

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

  INSN(fmadds, 0b000, 0b00, 0, 0);
  INSN(fmsubs, 0b000, 0b00, 0, 1);
  INSN(fnmadds, 0b000, 0b00, 1, 0);
  INSN(fnmsubs, 0b000, 0b00, 1, 1);

  INSN(fmaddd, 0b000, 0b01, 0, 0);
  INSN(fmsubd, 0b000, 0b01, 0, 1);
  INSN(fnmaddd, 0b000, 0b01, 1, 0);
  INSN(fnmsub, 0b000, 0b01, 1, 1);

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

   // Floating-point<->integer conversions
  void float_int_convert(unsigned op31, unsigned type,
                         unsigned rmode, unsigned opcode,
                         Register Rd, Register Rn) {
    starti;
    f(op31, 31, 29);
    f(0b11110, 28, 24);
    f(type, 23, 22), f(1, 21), f(rmode, 20, 19);
    f(opcode, 18, 16), f(0b000000, 15, 10);
    zrf(Rn, 5), zrf(Rd, 0);
  }

#define INSN(NAME, op31, type, rmode, opcode)                           \
  void NAME(Register Rd, FloatRegister Vn) {                            \
    float_int_convert(op31, type, rmode, opcode, Rd, (Register)Vn);     \
  }

  INSN(fcvtzsw, 0b000, 0b00, 0b11, 0b000);
  INSN(fcvtzs,  0b100, 0b00, 0b11, 0b000);
  INSN(fcvtzdw, 0b000, 0b01, 0b11, 0b000);
  INSN(fcvtzd,  0b100, 0b01, 0b11, 0b000);

  INSN(fmovs, 0b000, 0b00, 0b00, 0b110);
  INSN(fmovd, 0b100, 0b01, 0b00, 0b110);

  // INSN(fmovhid, 0b100, 0b10, 0b01, 0b110);

#undef INSN

#define INSN(NAME, op31, type, rmode, opcode)                           \
  void NAME(FloatRegister Vd, Register Rn) {                            \
    float_int_convert(op31, type, rmode, opcode, (Register)Vd, Rn);     \
  }

  INSN(fmovs, 0b000, 0b00, 0b00, 0b111);
  INSN(fmovd, 0b100, 0b01, 0b00, 0b111);

  INSN(scvtfws, 0b000, 0b00, 0b00, 0b010);
  INSN(scvtfs,  0b100, 0b00, 0b00, 0b010);
  INSN(scvtfwd, 0b000, 0b01, 0b00, 0b010);
  INSN(scvtfd,  0b100, 0b01, 0b00, 0b010);

  // INSN(fmovhid, 0b100, 0b10, 0b01, 0b111);

#undef INSN

  // Floating-point compare
  void float_compare(unsigned op31, unsigned type,
                     unsigned op, unsigned op2,
                     FloatRegister Vn, FloatRegister Vm = (FloatRegister)0) {
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
      fmovs(Vn, zr);
  }
  void fmovd(FloatRegister Vn, double value) {
    if (value)
      fmov_imm(Vn, value, 0b01);
    else
      fmovd(Vn, zr);
  }

/* SIMD extensions
 *
 * We just use FloatRegister in the following. They are exactly the same
 * as SIMD registers.
 */
 public:

  enum SIMD_Arrangement {
       T8B, T16B, T4H, T8H, T2S, T4S, T1D, T2D, T1Q
  };

  enum SIMD_RegVariant {
       B, H, S, D, Q
  };

#define INSN(NAME, op)                                            \
  void NAME(FloatRegister Rt, SIMD_RegVariant T, const Address &adr) {   \
    ld_st2((Register)Rt, adr, (int)T & 3, op + ((T==Q) ? 0b10:0b00), 1); \
  }                                                                      \

  INSN(ldr, 1);
  INSN(str, 0);

#undef INSN

 private:

  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn, int op1, int op2) {
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1, 29, 21), f(0, 20, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), rf(Xn, 5), rf(Vt, 0);
  }
  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn,
             int imm, int op1, int op2) {
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1 | 0b100, 29, 21), f(0b11111, 20, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), rf(Xn, 5), rf(Vt, 0);
  }
  void ld_st(FloatRegister Vt, SIMD_Arrangement T, Register Xn,
             Register Xm, int op1, int op2) {
    starti;
    f(0,31), f((int)T & 1, 30);
    f(op1 | 0b100, 29, 21), rf(Xm, 16), f(op2, 15, 12);
    f((int)T >> 1, 11, 10), rf(Xn, 5), rf(Vt, 0);
  }

 void ld_st(FloatRegister Vt, SIMD_Arrangement T, Address a, int op1, int op2) {
   switch (a.getMode()) {
   case Address::base_plus_offset:
     guarantee(a.offset() == 0, "no offset allowed here");
     ld_st(Vt, T, a.base(), op1, op2);
     break;
   case Address::post:
     ld_st(Vt, T, a.base(), a.offset(), op1, op2);
     break;
   case Address::base_plus_offset_reg:
     ld_st(Vt, T, a.base(), a.index(), op1, op2);
     break;
   default:
     ShouldNotReachHere();
   }
 }

 public:

#define INSN1(NAME, op1, op2)                                   \
  void NAME(FloatRegister Vt, SIMD_Arrangement T, const Address &a) {   \
   ld_st(Vt, T, a, op1, op2);                                           \
 }

#define INSN2(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, SIMD_Arrangement T, const Address &a) { \
    assert(Vt->successor() == Vt2, "Registers must be ordered");        \
    ld_st(Vt, T, a, op1, op2);                                          \
  }

#define INSN3(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            SIMD_Arrangement T, const Address &a) {                     \
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3,           \
           "Registers must be ordered");                                \
    ld_st(Vt, T, a, op1, op2);                                          \
  }

#define INSN4(NAME, op1, op2)                                           \
  void NAME(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,     \
            FloatRegister Vt4, SIMD_Arrangement T, const Address &a) {  \
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3 &&         \
           Vt3->successor() == Vt4, "Registers must be ordered");       \
    ld_st(Vt, T, a, op1, op2);                                          \
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

#define INSN(NAME, opc, opc2)                                                                 \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    f(0, 31), f((int)T & 1, 30), f(opc, 29), f(0b01110, 28, 24);                        \
    f((int)T >> 1, 23, 22), f(1, 21), rf(Vm, 16), f(opc2, 15, 10);                      \
    rf(Vn, 5), rf(Vd, 0);                                                               \
  }

  INSN(addv, 0, 0b100001);
  INSN(subv, 1, 0b100001);
  INSN(mulv, 0, 0b100111);
  INSN(mlav, 0, 0b100101);
  INSN(mlsv, 1, 0b100101);
  INSN(sshl, 0, 0b010001);
  INSN(ushl, 1, 0b010001);

#undef INSN

#define INSN(NAME, opc, opc2) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {                   \
    starti;                                                                             \
    f(0, 31), f((int)T & 1, 30), f(opc, 29), f(0b01110, 28, 24);                        \
    f((int)T >> 1, 23, 22), f(opc2, 21, 10);                                            \
    rf(Vn, 5), rf(Vd, 0);                                                               \
  }

  INSN(absr,  0, 0b100000101110);
  INSN(negr,  1, 0b100000101110);
  INSN(notr,  1, 0b100000010110);
  INSN(addv,  0, 0b110001101110);
  INSN(cls,   0, 0b100000010010);
  INSN(clz,   1, 0b100000010010);
  INSN(cnt,   0, 0b100000010110);

#undef INSN

#define INSN(NAME, op0, cmode0) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, unsigned imm8, unsigned lsl = 0) {   \
    unsigned cmode = cmode0;                                                           \
    unsigned op = op0;                                                                 \
    starti;                                                                            \
    assert(lsl == 0 ||                                                                 \
           ((T == T4H || T == T8H) && lsl == 8) ||                                     \
           ((T == T2S || T == T4S) && ((lsl >> 3) < 4)), "invalid shift");             \
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

#define INSN(NAME, op1, op2, op3) \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                                             \
    assert(T == T2S || T == T4S || T == T2D, "invalid arrangement");                    \
    f(0, 31), f((int)T & 1, 30), f(op1, 29), f(0b01110, 28, 24), f(op2, 23);            \
    f(T==T2D ? 1:0, 22); f(1, 21), rf(Vm, 16), f(op3, 15, 10), rf(Vn, 5), rf(Vd, 0);    \
  }

  INSN(fadd, 0, 0, 0b110101);
  INSN(fdiv, 1, 0, 0b111111);
  INSN(fmul, 1, 0, 0b110111);
  INSN(fsub, 0, 1, 0b110101);

#undef INSN

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

#define INSN(NAME, opc)                           \
  void NAME(FloatRegister Vd, FloatRegister Vn) { \
    starti;                                       \
    f(opc, 31, 10), rf(Vn, 5), rf(Vd, 0);         \
  }

  INSN(aese, 0b0100111000101000010010);
  INSN(aesd, 0b0100111000101000010110);
  INSN(aesmc, 0b0100111000101000011010);
  INSN(aesimc, 0b0100111000101000011110);

#undef INSN

  void ins(FloatRegister Vd, SIMD_RegVariant T, FloatRegister Vn, int didx, int sidx) {
    starti;
    assert(T != Q, "invalid register variant");
    f(0b01101110000, 31, 21), f(((didx<<1)|1)<<(int)T, 20, 16), f(0, 15);
    f(sidx<<(int)T, 14, 11), f(1, 10), rf(Vn, 5), rf(Vd, 0);
  }

  void umov(Register Rd, FloatRegister Vn, SIMD_RegVariant T, int idx) {
    starti;
    f(0, 31), f(T==D ? 1:0, 30), f(0b001110000, 29, 21);
    f(((idx<<1)|1)<<(int)T, 20, 16), f(0b001111, 15, 10);
    rf(Vn, 5), rf(Rd, 0);
  }

#define INSN(NAME, opc, opc2)                                           \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, int shift){ \
    starti;                                                             \
    /* The encodings for the immh:immb fields (bits 22:16) are          \
     *   0001 xxx       8B/16B, shift = xxx                             \
     *   001x xxx       4H/8H,  shift = xxxx                            \
     *   01xx xxx       2S/4S,  shift = xxxxx                           \
     *   1xxx xxx       1D/2D,  shift = xxxxxx (1D is RESERVED)         \
     */                                                                 \
    assert((1 << ((T>>1)+3)) > shift, "Invalid Shift value");           \
    f(0, 31), f(T & 1, 30), f(opc, 29), f(0b011110, 28, 23),            \
    f((1 << ((T>>1)+3))|shift, 22, 16); f(opc2, 15, 10), rf(Vn, 5), rf(Vd, 0); \
  }

  INSN(shl,  0, 0b010101);
  INSN(sshr, 0, 0b000001);
  INSN(ushr, 1, 0b000001);

#undef INSN

  void ushll(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, SIMD_Arrangement Tb, int shift) {
    starti;
    /* The encodings for the immh:immb fields (bits 22:16) are
     *   0001 xxx       8H, 8B/16b shift = xxx
     *   001x xxx       4S, 4H/8H  shift = xxxx
     *   01xx xxx       2D, 2S/4S  shift = xxxxx
     *   1xxx xxx       RESERVED
     */
    assert((Tb >> 1) + 1 == (Ta >> 1), "Incompatible arrangement");
    assert((1 << ((Tb>>1)+3)) > shift, "Invalid shift value");
    f(0, 31), f(Tb & 1, 30), f(0b1011110, 29, 23), f((1 << ((Tb>>1)+3))|shift, 22, 16);
    f(0b101001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }
  void ushll2(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn,  SIMD_Arrangement Tb, int shift) {
    ushll(Vd, Ta, Vn, Tb, shift);
  }

  void uzp1(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm,  SIMD_Arrangement T, int op = 0){
    starti;
    f(0, 31), f((T & 0x1), 30), f(0b001110, 29, 24), f((T >> 1), 23, 22), f(0, 21);
    rf(Vm, 16), f(0, 15), f(op, 14), f(0b0110, 13, 10), rf(Vn, 5), rf(Vd, 0);
  }
  void uzp2(FloatRegister Vd, FloatRegister Vn, FloatRegister Vm,  SIMD_Arrangement T){
    uzp1(Vd, Vn, Vm, T, 1);
  }

  // Move from general purpose register
  //   mov  Vd.T[index], Rn
  void mov(FloatRegister Vd, SIMD_Arrangement T, int index, Register Xn) {
    starti;
    f(0b01001110000, 31, 21), f(((1 << (T >> 1)) | (index << ((T >> 1) + 1))), 20, 16);
    f(0b000111, 15, 10), rf(Xn, 5), rf(Vd, 0);
  }

  // Move to general purpose register
  //   mov  Rd, Vn.T[index]
  void mov(Register Xd, FloatRegister Vn, SIMD_Arrangement T, int index) {
    starti;
    f(0, 31), f((T >= T1D) ? 1:0, 30), f(0b001110000, 29, 21);
    f(((1 << (T >> 1)) | (index << ((T >> 1) + 1))), 20, 16);
    f(0b001111, 15, 10), rf(Vn, 5), rf(Xd, 0);
  }

  void pmull(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, FloatRegister Vm, SIMD_Arrangement Tb) {
    starti;
    assert((Ta == T1Q && (Tb == T1D || Tb == T2D)) ||
           (Ta == T8H && (Tb == T8B || Tb == T16B)), "Invalid Size specifier");
    int size = (Ta == T1Q) ? 0b11 : 0b00;
    f(0, 31), f(Tb & 1, 30), f(0b001110, 29, 24), f(size, 23, 22);
    f(1, 21), rf(Vm, 16), f(0b111000, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }
  void pmull2(FloatRegister Vd, SIMD_Arrangement Ta, FloatRegister Vn, FloatRegister Vm, SIMD_Arrangement Tb) {
    assert(Tb == T2D || Tb == T16B, "pmull2 assumes T2D or T16B as the second size specifier");
    pmull(Vd, Ta, Vn, Vm, Tb);
  }

  void uqxtn(FloatRegister Vd, SIMD_Arrangement Tb, FloatRegister Vn, SIMD_Arrangement Ta) {
    starti;
    int size_b = (int)Tb >> 1;
    int size_a = (int)Ta >> 1;
    assert(size_b < 3 && size_b == size_a - 1, "Invalid size specifier");
    f(0, 31), f(Tb & 1, 30), f(0b101110, 29, 24), f(size_b, 23, 22);
    f(0b100001010010, 21, 10), rf(Vn, 5), rf(Vd, 0);
  }

  void dup(FloatRegister Vd, SIMD_Arrangement T, Register Xs)
  {
    starti;
    assert(T != T1D, "reserved encoding");
    f(0,31), f((int)T & 1, 30), f(0b001110000, 29, 21);
    f((1 << (T >> 1)), 20, 16), f(0b000011, 15, 10), rf(Xs, 5), rf(Vd, 0);
  }

  void dup(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, int index = 0)
  {
    starti;
    assert(T != T1D, "reserved encoding");
    f(0, 31), f((int)T & 1, 30), f(0b001110000, 29, 21);
    f(((1 << (T >> 1)) | (index << ((T >> 1) + 1))), 20, 16);
    f(0b000001, 15, 10), rf(Vn, 5), rf(Vd, 0);
  }

  // AdvSIMD ZIP/UZP/TRN
#define INSN(NAME, opcode)                                              \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm) { \
    starti;                                                             \
    f(0, 31), f(0b001110, 29, 24), f(0, 21), f(0b001110, 15, 10);       \
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
#define INSN(NAME, U, opcode)                                                       \
  void NAME(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {               \
       starti;                                                                      \
       assert((ASSERTION), MSG);                                                    \
       f(0, 31), f((int)T & 1, 30), f(U, 29), f(0b01110, 28, 24);                   \
       f((int)(T >> 1), 23, 22), f(0b10000, 21, 17), f(opcode, 16, 12);             \
       f(0b10, 11, 10), rf(Vn, 5), rf(Vd, 0);                                       \
 }

#define MSG "invalid arrangement"

#define ASSERTION (T == T2S || T == T4S || T == T2D)
  INSN(fsqrt, 1, 0b11111);
  INSN(fabs,  0, 0b01111);
  INSN(fneg,  1, 0b01111);
#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B || T == T4H || T == T8H || T == T2S || T == T4S)
  INSN(rev64, 0, 0b00000);
#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B || T == T4H || T == T8H)
  INSN(rev32, 1, 0b00000);
private:
  INSN(_rbit, 1, 0b00101);
public:

#undef ASSERTION

#define ASSERTION (T == T8B || T == T16B)
  INSN(rev16, 0, 0b00001);
  // RBIT only allows T8B and T16B but encodes them oddly.  Argh...
  void rbit(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn) {
    assert((ASSERTION), MSG);
    _rbit(Vd, SIMD_Arrangement(T & 1 | 0b010), Vn);
  }
#undef ASSERTION

#undef MSG

#undef INSN

void ext(FloatRegister Vd, SIMD_Arrangement T, FloatRegister Vn, FloatRegister Vm, int index)
  {
    starti;
    assert(T == T8B || T == T16B, "invalid arrangement");
    assert((T == T8B && index <= 0b0111) || (T == T16B && index <= 0b1111), "Invalid index value");
    f(0, 31), f((int)T & 1, 30), f(0b101110000, 29, 21);
    rf(Vm, 16), f(0, 15), f(index, 14, 11);
    f(0, 10), rf(Vn, 5), rf(Vd, 0);
  }

/* Simulator extensions to the ISA

   haltsim

   takes no arguments, causes the sim to enter a debug break and then
   return from the simulator run() call with STATUS_HALT? The linking
   code will call fatal() when it sees STATUS_HALT.

   blrt Xn, Wm
   blrt Xn, #gpargs, #fpargs, #type
   Xn holds the 64 bit x86 branch_address
   call format is encoded either as immediate data in the call
   or in register Wm. In the latter case
     Wm[13..6] = #gpargs,
     Wm[5..2] = #fpargs,
     Wm[1,0] = #type

   calls the x86 code address 'branch_address' supplied in Xn passing
   arguments taken from the general and floating point registers according
   to the supplied counts 'gpargs' and 'fpargs'. may return a result in r0
   or v0 according to the the return type #type' where

   address branch_address;
   uimm4 gpargs;
   uimm4 fpargs;
   enum ReturnType type;

   enum ReturnType
     {
       void_ret = 0,
       int_ret = 1,
       long_ret = 1,
       obj_ret = 1, // i.e. same as long
       float_ret = 2,
       double_ret = 3
     }

   notify

   notifies the simulator of a transfer of control. instr[14:0]
   identifies the type of change of control.

   0 ==> initial entry to a method.

   1 ==> return into a method from a submethod call.

   2 ==> exit out of Java method code.

   3 ==> start execution for a new bytecode.

   in cases 1 and 2 the simulator is expected to use a JVM callback to
   identify the name of the specific method being executed. in case 4
   the simulator is expected to use a JVM callback to identify the
   bytecode index.

   Instruction encodings
   ---------------------

   These are encoded in the space with instr[28:25] = 00 which is
   unallocated. Encodings are

                     10987654321098765432109876543210
   PSEUDO_HALT   = 0x11100000000000000000000000000000
   PSEUDO_BLRT  = 0x11000000000000000_______________
   PSEUDO_BLRTR = 0x1100000000000000100000__________
   PSEUDO_NOTIFY = 0x10100000000000000_______________

   instr[31,29] = op1 : 111 ==> HALT, 110 ==> BLRT/BLRTR, 101 ==> NOTIFY

   for BLRT
     instr[14,11] = #gpargs, instr[10,7] = #fpargs
     instr[6,5] = #type, instr[4,0] = Rn
   for BLRTR
     instr[9,5] = Rm, instr[4,0] = Rn
   for NOTIFY
     instr[14:0] = type : 0 ==> entry, 1 ==> reentry, 2 ==> exit, 3 ==> bcstart
*/

  enum NotifyType { method_entry, method_reentry, method_exit, bytecode_start };

  virtual void notify(int type) {
    if (UseBuiltinSim) {
      starti;
      //  109
      f(0b101, 31, 29);
      //  87654321098765
      f(0b00000000000000, 28, 15);
      f(type, 14, 0);
    }
  }

  void blrt(Register Rn, int gpargs, int fpargs, int type) {
    if (UseBuiltinSim) {
      starti;
      f(0b110, 31 ,29);
      f(0b00, 28, 25);
      //  4321098765
      f(0b0000000000, 24, 15);
      f(gpargs, 14, 11);
      f(fpargs, 10, 7);
      f(type, 6, 5);
      rf(Rn, 0);
    } else {
      blr(Rn);
    }
  }

  void blrt(Register Rn, Register Rm) {
    if (UseBuiltinSim) {
      starti;
      f(0b110, 31 ,29);
      f(0b00, 28, 25);
      //  4321098765
      f(0b0000000001, 24, 15);
      //  43210
      f(0b00000, 14, 10);
      rf(Rm, 5);
      rf(Rn, 0);
    } else {
      blr(Rn);
    }
  }

  void haltsim() {
    starti;
    f(0b111, 31 ,29);
    f(0b00, 28, 27);
    //  654321098765432109876543210
    f(0b000000000000000000000000000, 26, 0);
  }

  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
  }

  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr,
                                                Register tmp,
                                                int offset) {
    ShouldNotCallThis();
    return RegisterOrConstant();
  }

  // Stack overflow checking
  virtual void bang_stack_with_offset(int offset);

  static bool operand_valid_for_logical_immediate(bool is32, uint64_t imm);
  static bool operand_valid_for_add_sub_immediate(long imm);
  static bool operand_valid_for_float_immediate(double imm);

  void emit_data64(jlong data, relocInfo::relocType rtype, int format = 0);
  void emit_data64(jlong data, RelocationHolder const& rspec, int format = 0);
};

inline Assembler::Membar_mask_bits operator|(Assembler::Membar_mask_bits a,
                                             Assembler::Membar_mask_bits b) {
  return Assembler::Membar_mask_bits(unsigned(a)|unsigned(b));
}

Instruction_aarch64::~Instruction_aarch64() {
  assem->emit();
}

#undef starti

// Invert a condition
inline const Assembler::Condition operator~(const Assembler::Condition cond) {
  return Assembler::Condition(int(cond) ^ 1);
}

class BiasedLockingCounters;

extern "C" void das(uint64_t start, int len);

#endif // CPU_AARCH64_VM_ASSEMBLER_AARCH64_HPP
