/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_ARM_VM_ASSEMBLER_ARM_64_HPP
#define CPU_ARM_VM_ASSEMBLER_ARM_64_HPP

enum AsmShift12 {
  lsl0, lsl12
};

enum AsmPrefetchOp {
    pldl1keep = 0b00000,
    pldl1strm,
    pldl2keep,
    pldl2strm,
    pldl3keep,
    pldl3strm,

    plil1keep = 0b01000,
    plil1strm,
    plil2keep,
    plil2strm,
    plil3keep,
    plil3strm,

    pstl1keep = 0b10000,
    pstl1strm,
    pstl2keep,
    pstl2strm,
    pstl3keep,
    pstl3strm,
};

// Shifted register operand for data processing instructions.
class AsmOperand VALUE_OBJ_CLASS_SPEC {
 private:
  Register _reg;
  AsmShift _shift;
  int _shift_imm;

 public:
  AsmOperand(Register reg) {
    assert(reg != SP, "SP is not allowed in shifted register operand");
    _reg = reg;
    _shift = lsl;
    _shift_imm = 0;
  }

  AsmOperand(Register reg, AsmShift shift, int shift_imm) {
    assert(reg != SP, "SP is not allowed in shifted register operand");
    assert(shift_imm >= 0, "shift amount should be non-negative");
    _reg = reg;
    _shift = shift;
    _shift_imm = shift_imm;
  }

  Register reg() const {
    return _reg;
  }

  AsmShift shift() const {
    return _shift;
  }

  int shift_imm() const {
    return _shift_imm;
  }
};


class Assembler : public AbstractAssembler  {

 public:

  static const int LogInstructionSize = 2;
  static const int InstructionSize    = 1 << LogInstructionSize;

  Assembler(CodeBuffer* code) : AbstractAssembler(code) {}

  static inline AsmCondition inverse(AsmCondition cond) {
    assert ((cond != al) && (cond != nv), "AL and NV conditions cannot be inversed");
    return (AsmCondition)((int)cond ^ 1);
  }

  // Returns value of nzcv flags conforming to the given condition.
  static inline int flags_for_condition(AsmCondition cond) {
    switch(cond) {            // NZCV
      case mi: case lt: return 0b1000;
      case eq: case le: return 0b0100;
      case hs: case hi: return 0b0010;
      case vs:          return 0b0001;
      default:          return 0b0000;
    }
  }

  // Immediate, encoded into logical instructions.
  class LogicalImmediate {
   private:
    bool _encoded;
    bool _is32bit;
    int _immN;
    int _immr;
    int _imms;

    static inline bool has_equal_subpatterns(uintx imm, int size);
    static inline int least_pattern_size(uintx imm);
    static inline int population_count(uintx x);
    static inline uintx set_least_zeroes(uintx x);

#ifdef ASSERT
    uintx decode();
#endif

    void construct(uintx imm, bool is32);

   public:
    LogicalImmediate(uintx imm, bool is32 = false) { construct(imm, is32); }

    // Returns true if given immediate can be used in AArch64 logical instruction.
    bool is_encoded() const { return _encoded; }

    bool is32bit() const { return _is32bit; }
    int immN() const { assert(_encoded, "should be"); return _immN; }
    int immr() const { assert(_encoded, "should be"); return _immr; }
    int imms() const { assert(_encoded, "should be"); return _imms; }
  };

  // Immediate, encoded into arithmetic add/sub instructions.
  class ArithmeticImmediate {
   private:
    bool _encoded;
    int _imm;
    AsmShift12 _shift;

   public:
    ArithmeticImmediate(intx x) {
      if (is_unsigned_imm_in_range(x, 12, 0)) {
        _encoded = true;
        _imm = x;
        _shift = lsl0;
      } else if (is_unsigned_imm_in_range(x, 12, 12)) {
        _encoded = true;
        _imm = x >> 12;
        _shift = lsl12;
      } else {
        _encoded = false;
      }
    }

    ArithmeticImmediate(intx x, AsmShift12 sh) {
      if (is_unsigned_imm_in_range(x, 12, 0)) {
        _encoded = true;
        _imm = x;
        _shift = sh;
      } else {
        _encoded = false;
      }
    }

    // Returns true if this immediate can be used in AArch64 arithmetic (add/sub/cmp/cmn) instructions.
    bool is_encoded() const  { return _encoded; }

    int imm() const          { assert(_encoded, "should be"); return _imm; }
    AsmShift12 shift() const { assert(_encoded, "should be"); return _shift; }
  };

  static inline bool is_imm_in_range(intx value, int bits, int align_bits) {
    intx sign_bits = (value >> (bits + align_bits - 1));
    return ((value & right_n_bits(align_bits)) == 0) && ((sign_bits == 0) || (sign_bits == -1));
  }

  static inline int encode_imm(intx value, int bits, int align_bits, int low_bit_in_encoding) {
    assert (is_imm_in_range(value, bits, align_bits), "immediate value is out of range");
    return ((value >> align_bits) & right_n_bits(bits)) << low_bit_in_encoding;
  }

  static inline bool is_unsigned_imm_in_range(intx value, int bits, int align_bits) {
    return (value >= 0) && ((value & right_n_bits(align_bits)) == 0) && ((value >> (align_bits + bits)) == 0);
  }

  static inline int encode_unsigned_imm(intx value, int bits, int align_bits, int low_bit_in_encoding) {
    assert (is_unsigned_imm_in_range(value, bits, align_bits), "immediate value is out of range");
    return (value >> align_bits) << low_bit_in_encoding;
  }

  static inline bool is_offset_in_range(intx offset, int bits) {
    assert (bits == 14 || bits == 19 || bits == 26, "wrong bits number");
    return is_imm_in_range(offset, bits, 2);
  }

  static inline int encode_offset(intx offset, int bits, int low_bit_in_encoding) {
    return encode_imm(offset, bits, 2, low_bit_in_encoding);
  }

  // Returns true if given value can be used as immediate in arithmetic (add/sub/cmp/cmn) instructions.
  static inline bool is_arith_imm_in_range(intx value) {
    return ArithmeticImmediate(value).is_encoded();
  }


  // Load/store instructions

#define F(mnemonic, opc) \
  void mnemonic(Register rd, address literal_addr) {                                                       \
    intx offset = literal_addr - pc();                                                                     \
    assert (opc != 0b01 || offset == 0 || ((uintx)literal_addr & 7) == 0, "ldr target should be aligned"); \
    assert (is_offset_in_range(offset, 19), "offset is out of range");                                     \
    emit_int32(opc << 30 | 0b011 << 27 | encode_offset(offset, 19, 5) | rd->encoding_with_zr());           \
  }

  F(ldr_w, 0b00)
  F(ldr,   0b01)
  F(ldrsw, 0b10)
#undef F

#define F(mnemonic, opc) \
  void mnemonic(FloatRegister rt, address literal_addr) {                                                  \
    intx offset = literal_addr - pc();                                                                     \
    assert (offset == 0 || ((uintx)literal_addr & right_n_bits(2 + opc)) == 0, "ldr target should be aligned"); \
    assert (is_offset_in_range(offset, 19), "offset is out of range");                                     \
    emit_int32(opc << 30 | 0b011100 << 24 | encode_offset(offset, 19, 5) | rt->encoding());                \
  }

  F(ldr_s, 0b00)
  F(ldr_d, 0b01)
  F(ldr_q, 0b10)
#undef F

#define F(mnemonic, size, o2, L, o1, o0) \
  void mnemonic(Register rt, Register rn) {                                                                \
    emit_int32(size << 30 | 0b001000 << 24 | o2 << 23 | L << 22 | o1 << 21 | 0b11111 << 16 |               \
        o0 << 15 | 0b11111 << 10 | rn->encoding_with_sp() << 5 | rt->encoding_with_zr());                  \
  }

  F(ldxrb,   0b00, 0, 1, 0, 0)
  F(ldaxrb,  0b00, 0, 1, 0, 1)
  F(ldarb,   0b00, 1, 1, 0, 1)
  F(ldxrh,   0b01, 0, 1, 0, 0)
  F(ldaxrh,  0b01, 0, 1, 0, 1)
  F(ldarh,   0b01, 1, 1, 0, 1)
  F(ldxr_w,  0b10, 0, 1, 0, 0)
  F(ldaxr_w, 0b10, 0, 1, 0, 1)
  F(ldar_w,  0b10, 1, 1, 0, 1)
  F(ldxr,    0b11, 0, 1, 0, 0)
  F(ldaxr,   0b11, 0, 1, 0, 1)
  F(ldar,    0b11, 1, 1, 0, 1)

  F(stlrb,   0b00, 1, 0, 0, 1)
  F(stlrh,   0b01, 1, 0, 0, 1)
  F(stlr_w,  0b10, 1, 0, 0, 1)
  F(stlr,    0b11, 1, 0, 0, 1)
#undef F

#define F(mnemonic, size, o2, L, o1, o0) \
  void mnemonic(Register rs, Register rt, Register rn) {                                                     \
    assert (rs != rt, "should be different");                                                                \
    assert (rs != rn, "should be different");                                                                \
    emit_int32(size << 30 | 0b001000 << 24 | o2 << 23 | L << 22 | o1 << 21 | rs->encoding_with_zr() << 16 |  \
        o0 << 15 | 0b11111 << 10 | rn->encoding_with_sp() << 5 | rt->encoding_with_zr());                    \
  }

  F(stxrb,   0b00, 0, 0, 0, 0)
  F(stlxrb,  0b00, 0, 0, 0, 1)
  F(stxrh,   0b01, 0, 0, 0, 0)
  F(stlxrh,  0b01, 0, 0, 0, 1)
  F(stxr_w,  0b10, 0, 0, 0, 0)
  F(stlxr_w, 0b10, 0, 0, 0, 1)
  F(stxr,    0b11, 0, 0, 0, 0)
  F(stlxr,   0b11, 0, 0, 0, 1)
#undef F

#define F(mnemonic, size, o2, L, o1, o0) \
  void mnemonic(Register rt, Register rt2, Register rn) {                                                  \
    assert (rt != rt2, "should be different");                                                             \
    emit_int32(size << 30 | 0b001000 << 24 | o2 << 23 | L << 22 | o1 << 21 | 0b11111 << 16 |               \
        o0 << 15 | rt2->encoding_with_zr() << 10 | rn->encoding_with_sp() << 5 | rt->encoding_with_zr());  \
  }

  F(ldxp_w,  0b10, 0, 1, 1, 0)
  F(ldaxp_w, 0b10, 0, 1, 1, 1)
  F(ldxp,    0b11, 0, 1, 1, 0)
  F(ldaxp,   0b11, 0, 1, 1, 1)
#undef F

#define F(mnemonic, size, o2, L, o1, o0) \
  void mnemonic(Register rs, Register rt, Register rt2, Register rn) {                                       \
    assert (rs != rt, "should be different");                                                                \
    assert (rs != rt2, "should be different");                                                               \
    assert (rs != rn, "should be different");                                                                \
    emit_int32(size << 30 | 0b001000 << 24 | o2 << 23 | L << 22 | o1 << 21 | rs->encoding_with_zr() << 16 |  \
        o0 << 15 | rt2->encoding_with_zr() << 10 | rn->encoding_with_sp() << 5 | rt->encoding_with_zr());    \
  }

  F(stxp_w,  0b10, 0, 0, 1, 0)
  F(stlxp_w, 0b10, 0, 0, 1, 1)
  F(stxp,    0b11, 0, 0, 1, 0)
  F(stlxp,   0b11, 0, 0, 1, 1)
#undef F

#define F(mnemonic, opc, V, L) \
  void mnemonic(Register rt, Register rt2, Register rn, int offset = 0) {                                  \
    assert (!L || rt != rt2, "should be different");                                                       \
    int align_bits = 2 + (opc >> 1);                                                                       \
    assert (is_imm_in_range(offset, 7, align_bits), "offset is out of range");                             \
    emit_int32(opc << 30 | 0b101 << 27 | V << 26 | L << 22 | encode_imm(offset, 7, align_bits, 15) |       \
        rt2->encoding_with_zr() << 10 | rn->encoding_with_sp() << 5 | rt->encoding_with_zr());             \
  }

  F(stnp_w,  0b00, 0, 0)
  F(ldnp_w,  0b00, 0, 1)
  F(stnp,    0b10, 0, 0)
  F(ldnp,    0b10, 0, 1)
#undef F

#define F(mnemonic, opc, V, L) \
  void mnemonic(FloatRegister rt, FloatRegister rt2, Register rn, int offset = 0) {                        \
    assert (!L || (rt != rt2), "should be different");                                                     \
    int align_bits = 2 + opc;                                                                              \
    assert (is_imm_in_range(offset, 7, align_bits), "offset is out of range");                             \
    emit_int32(opc << 30 | 0b101 << 27 | V << 26 | L << 22 | encode_imm(offset, 7, align_bits, 15) |       \
        rt2->encoding() << 10 | rn->encoding_with_sp() << 5 | rt->encoding());                             \
  }

  F(stnp_s,  0b00, 1, 0)
  F(stnp_d,  0b01, 1, 0)
  F(stnp_q,  0b10, 1, 0)
  F(ldnp_s,  0b00, 1, 1)
  F(ldnp_d,  0b01, 1, 1)
  F(ldnp_q,  0b10, 1, 1)
#undef F

#define F(mnemonic, size, V, opc) \
  void mnemonic(Register rt, Address addr) { \
    assert((addr.mode() == basic_offset) || (rt != addr.base()), "should be different");                    \
    if (addr.index() == noreg) {                                                                            \
      if ((addr.mode() == basic_offset) && is_unsigned_imm_in_range(addr.disp(), 12, size)) {               \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | 0b01 << 24 | opc << 22 |                            \
           encode_unsigned_imm(addr.disp(), 12, size, 10) |                                                 \
           addr.base()->encoding_with_sp() << 5 | rt->encoding_with_zr());                                  \
      } else {                                                                                              \
        assert(is_imm_in_range(addr.disp(), 9, 0), "offset is out of range");                               \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | encode_imm(addr.disp(), 9, 0, 12) |     \
           addr.mode() << 10 | addr.base()->encoding_with_sp() << 5 | rt->encoding_with_zr());              \
      }                                                                                                     \
    } else {                                                                                                \
      assert (addr.disp() == 0, "non-zero displacement for [reg + reg] address mode");                      \
      assert ((addr.shift_imm() == 0) || (addr.shift_imm() == size), "invalid shift amount");               \
      emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | 1 << 21 |                                 \
         addr.index()->encoding_with_zr() << 16 | addr.extend() << 13 | (addr.shift_imm() != 0) << 12 |     \
         0b10 << 10 | addr.base()->encoding_with_sp() << 5 | rt->encoding_with_zr());                       \
    }                                                                                                       \
  }

  F(strb,    0b00, 0, 0b00)
  F(ldrb,    0b00, 0, 0b01)
  F(ldrsb,   0b00, 0, 0b10)
  F(ldrsb_w, 0b00, 0, 0b11)

  F(strh,    0b01, 0, 0b00)
  F(ldrh,    0b01, 0, 0b01)
  F(ldrsh,   0b01, 0, 0b10)
  F(ldrsh_w, 0b01, 0, 0b11)

  F(str_w,   0b10, 0, 0b00)
  F(ldr_w,   0b10, 0, 0b01)
  F(ldrsw,   0b10, 0, 0b10)

  F(str,     0b11, 0, 0b00)
  F(ldr,     0b11, 0, 0b01)
#undef F

#define F(mnemonic, size, V, opc) \
  void mnemonic(AsmPrefetchOp prfop, Address addr) { \
    assert (addr.mode() == basic_offset, #mnemonic " supports only basic_offset address mode");             \
    if (addr.index() == noreg) {                                                                            \
      if (is_unsigned_imm_in_range(addr.disp(), 12, size)) {                                                \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | 0b01 << 24 | opc << 22 |                            \
           encode_unsigned_imm(addr.disp(), 12, size, 10) |                                                 \
           addr.base()->encoding_with_sp() << 5 | prfop);                                                   \
      } else {                                                                                              \
        assert(is_imm_in_range(addr.disp(), 9, 0), "offset is out of range");                               \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | encode_imm(addr.disp(), 9, 0, 12) |     \
           addr.base()->encoding_with_sp() << 5 | prfop);                                                   \
      }                                                                                                     \
    } else {                                                                                                \
      assert (addr.disp() == 0, "non-zero displacement for [reg + reg] address mode");                      \
      assert ((addr.shift_imm() == 0) || (addr.shift_imm() == size), "invalid shift amount");               \
      emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | 1 << 21 |                                 \
         addr.index()->encoding_with_zr() << 16 | addr.extend() << 13 | (addr.shift_imm() != 0) << 12 |     \
         0b10 << 10 | addr.base()->encoding_with_sp() << 5 | prfop);                                        \
    }                                                                                                       \
  }

  F(prfm, 0b11, 0, 0b10)
#undef F

#define F(mnemonic, size, V, opc) \
  void mnemonic(FloatRegister rt, Address addr) { \
    int align_bits = (((opc & 0b10) >> 1) << 2) | size;                                                     \
    if (addr.index() == noreg) {                                                                            \
      if ((addr.mode() == basic_offset) && is_unsigned_imm_in_range(addr.disp(), 12, align_bits)) {         \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | 0b01 << 24 | opc << 22 |                            \
           encode_unsigned_imm(addr.disp(), 12, align_bits, 10) |                                           \
           addr.base()->encoding_with_sp() << 5 | rt->encoding());                                          \
      } else {                                                                                              \
        assert(is_imm_in_range(addr.disp(), 9, 0), "offset is out of range");                               \
        emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | encode_imm(addr.disp(), 9, 0, 12) |     \
           addr.mode() << 10 | addr.base()->encoding_with_sp() << 5 | rt->encoding());                      \
      }                                                                                                     \
    } else {                                                                                                \
      assert (addr.disp() == 0, "non-zero displacement for [reg + reg] address mode");                      \
      assert ((addr.shift_imm() == 0) || (addr.shift_imm() == align_bits), "invalid shift amount");         \
      emit_int32(size << 30 | 0b111 << 27 | V << 26 | opc << 22 | 1 << 21 |                                 \
         addr.index()->encoding_with_zr() << 16 | addr.extend() << 13 | (addr.shift_imm() != 0) << 12 |     \
         0b10 << 10 | addr.base()->encoding_with_sp() << 5 | rt->encoding());                               \
    }                                                                                                       \
  }

  F(str_b, 0b00, 1, 0b00)
  F(ldr_b, 0b00, 1, 0b01)
  F(str_h, 0b01, 1, 0b00)
  F(ldr_h, 0b01, 1, 0b01)
  F(str_s, 0b10, 1, 0b00)
  F(ldr_s, 0b10, 1, 0b01)
  F(str_d, 0b11, 1, 0b00)
  F(ldr_d, 0b11, 1, 0b01)
  F(str_q, 0b00, 1, 0b10)
  F(ldr_q, 0b00, 1, 0b11)
#undef F

#define F(mnemonic, opc, V, L) \
  void mnemonic(Register rt, Register rt2, Address addr) {                                                         \
    assert((addr.mode() == basic_offset) || ((rt != addr.base()) && (rt2 != addr.base())), "should be different"); \
    assert(!L || (rt != rt2), "should be different");                                                              \
    assert(addr.index() == noreg, "[reg + reg] address mode is not available for load/store pair");                \
    int align_bits = 2 + (opc >> 1);                                                                               \
    int mode_encoding = (addr.mode() == basic_offset) ? 0b10 : addr.mode();                                        \
    assert(is_imm_in_range(addr.disp(), 7, align_bits), "offset is out of range");                                 \
    emit_int32(opc << 30 | 0b101 << 27 | V << 26 | mode_encoding << 23 | L << 22 |                                 \
       encode_imm(addr.disp(), 7, align_bits, 15) | rt2->encoding_with_zr() << 10 |                                \
       addr.base()->encoding_with_sp() << 5 | rt->encoding_with_zr());                                             \
  }

  F(stp_w, 0b00, 0, 0)
  F(ldp_w, 0b00, 0, 1)
  F(ldpsw, 0b01, 0, 1)
  F(stp,   0b10, 0, 0)
  F(ldp,   0b10, 0, 1)
#undef F

#define F(mnemonic, opc, V, L) \
  void mnemonic(FloatRegister rt, FloatRegister rt2, Address addr) {                                                         \
    assert(!L || (rt != rt2), "should be different");                                                              \
    assert(addr.index() == noreg, "[reg + reg] address mode is not available for load/store pair");                \
    int align_bits = 2 + opc;                                                                                      \
    int mode_encoding = (addr.mode() == basic_offset) ? 0b10 : addr.mode();                                        \
    assert(is_imm_in_range(addr.disp(), 7, align_bits), "offset is out of range");                                 \
    emit_int32(opc << 30 | 0b101 << 27 | V << 26 | mode_encoding << 23 | L << 22 |                                 \
       encode_imm(addr.disp(), 7, align_bits, 15) | rt2->encoding() << 10 |                                        \
       addr.base()->encoding_with_sp() << 5 | rt->encoding());                                                     \
  }

  F(stp_s, 0b00, 1, 0)
  F(ldp_s, 0b00, 1, 1)
  F(stp_d, 0b01, 1, 0)
  F(ldp_d, 0b01, 1, 1)
  F(stp_q, 0b10, 1, 0)
  F(ldp_q, 0b10, 1, 1)
#undef F

  // Data processing instructions

#define F(mnemonic, sf, opc) \
  void mnemonic(Register rd, Register rn, const LogicalImmediate& imm) {                      \
    assert (imm.is_encoded(), "illegal immediate for logical instruction");                   \
    assert (imm.is32bit() == (sf == 0), "immediate size does not match instruction size");    \
    emit_int32(sf << 31 | opc << 29 | 0b100100 << 23 | imm.immN() << 22 | imm.immr() << 16 |  \
        imm.imms() << 10 | rn->encoding_with_zr() << 5 |                                      \
        ((opc == 0b11) ? rd->encoding_with_zr() : rd->encoding_with_sp()));                   \
  }                                                                                           \
  void mnemonic(Register rd, Register rn, uintx imm) {                                        \
    LogicalImmediate limm(imm, (sf == 0));                                                    \
    mnemonic(rd, rn, limm);                                                                   \
  }                                                                                           \
  void mnemonic(Register rd, Register rn, unsigned int imm) {                                 \
    mnemonic(rd, rn, (uintx)imm);                                                             \
  }

  F(andr_w, 0, 0b00)
  F(orr_w,  0, 0b01)
  F(eor_w,  0, 0b10)
  F(ands_w, 0, 0b11)

  F(andr, 1, 0b00)
  F(orr,  1, 0b01)
  F(eor,  1, 0b10)
  F(ands, 1, 0b11)
#undef F

  void tst(Register rn, unsigned int imm) {
    ands(ZR, rn, imm);
  }

  void tst_w(Register rn, unsigned int imm) {
    ands_w(ZR, rn, imm);
  }

#define F(mnemonic, sf, opc, N) \
  void mnemonic(Register rd, Register rn, AsmOperand operand) { \
    assert (operand.shift_imm() >> (5 + sf) == 0, "shift amount is too large");          \
    emit_int32(sf << 31 | opc << 29 | 0b01010 << 24 | operand.shift() << 22 | N << 21 |  \
        operand.reg()->encoding_with_zr() << 16 | operand.shift_imm() << 10 |            \
        rn->encoding_with_zr() << 5 | rd->encoding_with_zr());                           \
  }

  F(andr_w, 0, 0b00, 0)
  F(bic_w,  0, 0b00, 1)
  F(orr_w,  0, 0b01, 0)
  F(orn_w,  0, 0b01, 1)
  F(eor_w,  0, 0b10, 0)
  F(eon_w,  0, 0b10, 1)
  F(ands_w, 0, 0b11, 0)
  F(bics_w, 0, 0b11, 1)

  F(andr, 1, 0b00, 0)
  F(bic,  1, 0b00, 1)
  F(orr,  1, 0b01, 0)
  F(orn,  1, 0b01, 1)
  F(eor,  1, 0b10, 0)
  F(eon,  1, 0b10, 1)
  F(ands, 1, 0b11, 0)
  F(bics, 1, 0b11, 1)
#undef F

  void tst(Register rn, AsmOperand operand) {
    ands(ZR, rn, operand);
  }

  void tst_w(Register rn, AsmOperand operand) {
    ands_w(ZR, rn, operand);
  }

  void mvn(Register rd, AsmOperand operand) {
    orn(rd, ZR, operand);
  }

  void mvn_w(Register rd, AsmOperand operand) {
    orn_w(rd, ZR, operand);
  }

#define F(mnemonic, sf, op, S) \
  void mnemonic(Register rd, Register rn, const ArithmeticImmediate& imm) {                       \
    assert(imm.is_encoded(), "immediate is out of range");                                        \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b10001 << 24 | imm.shift() << 22 |                \
        imm.imm() << 10 | rn->encoding_with_sp() << 5 |                                           \
        (S == 1 ? rd->encoding_with_zr() : rd->encoding_with_sp()));                              \
  }                                                                                               \
  void mnemonic(Register rd, Register rn, int imm) {                                              \
    mnemonic(rd, rn, ArithmeticImmediate(imm));                                                   \
  }                                                                                               \
  void mnemonic(Register rd, Register rn, int imm, AsmShift12 shift) {                            \
    mnemonic(rd, rn, ArithmeticImmediate(imm, shift));                                            \
  }                                                                                               \
  void mnemonic(Register rd, Register rn, Register rm, AsmExtendOp extend, int shift_imm = 0) {   \
    assert ((0 <= shift_imm) && (shift_imm <= 4), "shift amount is out of range");                \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b01011001 << 21 | rm->encoding_with_zr() << 16 |  \
        extend << 13 | shift_imm << 10 | rn->encoding_with_sp() << 5 |                            \
        (S == 1 ? rd->encoding_with_zr() : rd->encoding_with_sp()));                              \
  }                                                                                               \
  void mnemonic(Register rd, Register rn, AsmOperand operand) {                                   \
    assert (operand.shift() != ror, "illegal shift type");                                        \
    assert (operand.shift_imm() >> (5 + sf) == 0, "shift amount is too large");                   \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b01011 << 24 | operand.shift() << 22 |            \
        operand.reg()->encoding_with_zr() << 16 | operand.shift_imm() << 10 |                     \
        rn->encoding_with_zr() << 5 | rd->encoding_with_zr());                                    \
  }

  F(add_w,  0, 0, 0)
  F(adds_w, 0, 0, 1)
  F(sub_w,  0, 1, 0)
  F(subs_w, 0, 1, 1)

  F(add,    1, 0, 0)
  F(adds,   1, 0, 1)
  F(sub,    1, 1, 0)
  F(subs,   1, 1, 1)
#undef F

  void mov(Register rd, Register rm) {
    if ((rd == SP) || (rm == SP)) {
      add(rd, rm, 0);
    } else {
      orr(rd, ZR, rm);
    }
  }

  void mov_w(Register rd, Register rm) {
    if ((rd == SP) || (rm == SP)) {
      add_w(rd, rm, 0);
    } else {
      orr_w(rd, ZR, rm);
    }
  }

  void cmp(Register rn, int imm) {
    subs(ZR, rn, imm);
  }

  void cmp_w(Register rn, int imm) {
    subs_w(ZR, rn, imm);
  }

  void cmp(Register rn, Register rm) {
    assert (rm != SP, "SP should not be used as the 2nd operand of cmp");
    if (rn == SP) {
      subs(ZR, rn, rm, ex_uxtx);
    } else {
      subs(ZR, rn, rm);
    }
  }

  void cmp_w(Register rn, Register rm) {
    assert ((rn != SP) && (rm != SP), "SP should not be used in 32-bit cmp");
    subs_w(ZR, rn, rm);
  }

  void cmp(Register rn, AsmOperand operand) {
    assert (rn != SP, "SP is not allowed in cmp with shifted register (AsmOperand)");
    subs(ZR, rn, operand);
  }

  void cmn(Register rn, int imm) {
    adds(ZR, rn, imm);
  }

  void cmn_w(Register rn, int imm) {
    adds_w(ZR, rn, imm);
  }

  void cmn(Register rn, Register rm) {
    assert (rm != SP, "SP should not be used as the 2nd operand of cmp");
    if (rn == SP) {
      adds(ZR, rn, rm, ex_uxtx);
    } else {
      adds(ZR, rn, rm);
    }
  }

  void cmn_w(Register rn, Register rm) {
    assert ((rn != SP) && (rm != SP), "SP should not be used in 32-bit cmp");
    adds_w(ZR, rn, rm);
  }

  void neg(Register rd, Register rm) {
    sub(rd, ZR, rm);
  }

  void neg_w(Register rd, Register rm) {
    sub_w(rd, ZR, rm);
  }

#define F(mnemonic, sf, op, S) \
  void mnemonic(Register rd, Register rn, Register rm) { \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b11010000 << 21 | rm->encoding_with_zr() << 16 |   \
        rn->encoding_with_zr() << 5 | rd->encoding_with_zr());                                     \
  }

  F(adc_w,  0, 0, 0)
  F(adcs_w, 0, 0, 1)
  F(sbc_w,  0, 1, 0)
  F(sbcs_w, 0, 1, 1)

  F(adc,    1, 0, 0)
  F(adcs,   1, 0, 1)
  F(sbc,    1, 1, 0)
  F(sbcs,   1, 1, 1)
#undef F

#define F(mnemonic, sf, N) \
  void mnemonic(Register rd, Register rn, Register rm, int lsb) { \
    assert ((lsb >> (5 + sf)) == 0, "illegal least significant bit position");        \
    emit_int32(sf << 31 | 0b100111 << 23 | N << 22 | rm->encoding_with_zr() << 16 |   \
        lsb << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());            \
  }

  F(extr_w,  0, 0)
  F(extr,    1, 1)
#undef F

#define F(mnemonic, sf, opc) \
  void mnemonic(Register rd, int imm, int shift) { \
    assert ((imm >> 16) == 0, "immediate is out of range");                       \
    assert (((shift & 0xf) == 0) && ((shift >> (5 + sf)) == 0), "invalid shift"); \
    emit_int32(sf << 31 | opc << 29 | 0b100101 << 23 | (shift >> 4) << 21 |       \
        imm << 5 | rd->encoding_with_zr());                                       \
  }

  F(movn_w,  0, 0b00)
  F(movz_w,  0, 0b10)
  F(movk_w,  0, 0b11)
  F(movn,    1, 0b00)
  F(movz,    1, 0b10)
  F(movk,    1, 0b11)
#undef F

  void mov(Register rd, int imm) {
    assert ((imm >> 16) == 0, "immediate is out of range");
    movz(rd, imm, 0);
  }

  void mov_w(Register rd, int imm) {
    assert ((imm >> 16) == 0, "immediate is out of range");
    movz_w(rd, imm, 0);
  }

#define F(mnemonic, sf, op, S) \
  void mnemonic(Register rn, int imm, int nzcv, AsmCondition cond) { \
    assert ((imm >> 5) == 0, "immediate is out of range");                      \
    assert ((nzcv >> 4) == 0, "illegal nzcv");                                  \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b11010010 << 21 | imm << 16 |   \
         cond << 12 | 1 << 11 | rn->encoding_with_zr() << 5 | nzcv);            \
  }

  F(ccmn_w, 0, 0, 1)
  F(ccmp_w, 0, 1, 1)
  F(ccmn,   1, 0, 1)
  F(ccmp,   1, 1, 1)
#undef F

#define F(mnemonic, sf, op, S) \
  void mnemonic(Register rn, Register rm, int nzcv, AsmCondition cond) { \
    assert ((nzcv >> 4) == 0, "illegal nzcv");                                                    \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b11010010 << 21 | rm->encoding_with_zr() << 16 |  \
        cond << 12 | rn->encoding_with_zr() << 5 | nzcv);                                         \
  }

  F(ccmn_w, 0, 0, 1)
  F(ccmp_w, 0, 1, 1)
  F(ccmn,   1, 0, 1)
  F(ccmp,   1, 1, 1)
#undef F

#define F(mnemonic, sf, op, S, op2) \
  void mnemonic(Register rd, Register rn, Register rm, AsmCondition cond) { \
    emit_int32(sf << 31 | op << 30 | S << 29 | 0b11010100 << 21 | rm->encoding_with_zr() << 16 |  \
        cond << 12 | op2 << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());           \
  }

  F(csel_w,  0, 0, 0, 0b00)
  F(csinc_w, 0, 0, 0, 0b01)
  F(csinv_w, 0, 1, 0, 0b00)
  F(csneg_w, 0, 1, 0, 0b01)

  F(csel,    1, 0, 0, 0b00)
  F(csinc,   1, 0, 0, 0b01)
  F(csinv,   1, 1, 0, 0b00)
  F(csneg,   1, 1, 0, 0b01)
#undef F

  void cset(Register rd, AsmCondition cond) {
    csinc(rd, ZR, ZR, inverse(cond));
  }

  void cset_w(Register rd, AsmCondition cond) {
    csinc_w(rd, ZR, ZR, inverse(cond));
  }

  void csetm(Register rd, AsmCondition cond) {
    csinv(rd, ZR, ZR, inverse(cond));
  }

  void csetm_w(Register rd, AsmCondition cond) {
    csinv_w(rd, ZR, ZR, inverse(cond));
  }

  void cinc(Register rd, Register rn, AsmCondition cond) {
    csinc(rd, rn, rn, inverse(cond));
  }

  void cinc_w(Register rd, Register rn, AsmCondition cond) {
    csinc_w(rd, rn, rn, inverse(cond));
  }

  void cinv(Register rd, Register rn, AsmCondition cond) {
    csinv(rd, rn, rn, inverse(cond));
  }

  void cinv_w(Register rd, Register rn, AsmCondition cond) {
    csinv_w(rd, rn, rn, inverse(cond));
  }

#define F(mnemonic, sf, S, opcode) \
  void mnemonic(Register rd, Register rn) { \
    emit_int32(sf << 31 | 1 << 30 | S << 29 | 0b11010110 << 21 | opcode << 10 |  \
        rn->encoding_with_zr() << 5 | rd->encoding_with_zr());                   \
  }

  F(rbit_w,  0, 0, 0b000000)
  F(rev16_w, 0, 0, 0b000001)
  F(rev_w,   0, 0, 0b000010)
  F(clz_w,   0, 0, 0b000100)
  F(cls_w,   0, 0, 0b000101)

  F(rbit,    1, 0, 0b000000)
  F(rev16,   1, 0, 0b000001)
  F(rev32,   1, 0, 0b000010)
  F(rev,     1, 0, 0b000011)
  F(clz,     1, 0, 0b000100)
  F(cls,     1, 0, 0b000101)
#undef F

#define F(mnemonic, sf, S, opcode) \
  void mnemonic(Register rd, Register rn, Register rm) { \
    emit_int32(sf << 31 | S << 29 | 0b11010110 << 21 | rm->encoding_with_zr() << 16 |  \
        opcode << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());          \
  }

  F(udiv_w,  0, 0, 0b000010)
  F(sdiv_w,  0, 0, 0b000011)
  F(lslv_w,  0, 0, 0b001000)
  F(lsrv_w,  0, 0, 0b001001)
  F(asrv_w,  0, 0, 0b001010)
  F(rorv_w,  0, 0, 0b001011)

  F(udiv,    1, 0, 0b000010)
  F(sdiv,    1, 0, 0b000011)
  F(lslv,    1, 0, 0b001000)
  F(lsrv,    1, 0, 0b001001)
  F(asrv,    1, 0, 0b001010)
  F(rorv,    1, 0, 0b001011)
#undef F

#define F(mnemonic, sf, op31, o0) \
  void mnemonic(Register rd, Register rn, Register rm, Register ra) { \
    emit_int32(sf << 31 | 0b11011 << 24 | op31 << 21 | rm->encoding_with_zr() << 16 |                     \
        o0 << 15 | ra->encoding_with_zr() << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());  \
  }

  F(madd_w,  0, 0b000, 0)
  F(msub_w,  0, 0b000, 1)
  F(madd,    1, 0b000, 0)
  F(msub,    1, 0b000, 1)

  F(smaddl,  1, 0b001, 0)
  F(smsubl,  1, 0b001, 1)
  F(umaddl,  1, 0b101, 0)
  F(umsubl,  1, 0b101, 1)
#undef F

  void mul(Register rd, Register rn, Register rm) {
      madd(rd, rn, rm, ZR);
  }

  void mul_w(Register rd, Register rn, Register rm) {
      madd_w(rd, rn, rm, ZR);
  }

#define F(mnemonic, sf, op31, o0) \
  void mnemonic(Register rd, Register rn, Register rm) { \
    emit_int32(sf << 31 | 0b11011 << 24 | op31 << 21 | rm->encoding_with_zr() << 16 |      \
        o0 << 15 | 0b11111 << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());  \
  }

  F(smulh,   1, 0b010, 0)
  F(umulh,   1, 0b110, 0)
#undef F

#define F(mnemonic, op) \
  void mnemonic(Register rd, address addr) { \
    intx offset;                                                        \
    if (op == 0) {                                                      \
      offset = addr - pc();                                             \
    } else {                                                            \
      offset = (((intx)addr) - (((intx)pc()) & ~0xfff)) >> 12;          \
    }                                                                   \
    assert (is_imm_in_range(offset, 21, 0), "offset is out of range");  \
    emit_int32(op << 31 | (offset & 3) << 29 | 0b10000 << 24 |          \
        encode_imm(offset >> 2, 19, 0, 5) | rd->encoding_with_zr());    \
  }                                                                     \

  F(adr,   0)
  F(adrp,  1)
#undef F

  void adr(Register rd, Label& L) {
    adr(rd, target(L));
  }

#define F(mnemonic, sf, opc, N)                                                \
  void mnemonic(Register rd, Register rn, int immr, int imms) {                \
    assert ((immr >> (5 + sf)) == 0, "immr is out of range");                  \
    assert ((imms >> (5 + sf)) == 0, "imms is out of range");                  \
    emit_int32(sf << 31 | opc << 29 | 0b100110 << 23 | N << 22 | immr << 16 |  \
        imms << 10 | rn->encoding_with_zr() << 5 | rd->encoding_with_zr());    \
  }

  F(sbfm_w, 0, 0b00, 0)
  F(bfm_w,  0, 0b01, 0)
  F(ubfm_w, 0, 0b10, 0)

  F(sbfm, 1, 0b00, 1)
  F(bfm,  1, 0b01, 1)
  F(ubfm, 1, 0b10, 1)
#undef F

#define F(alias, mnemonic, sf, immr, imms) \
  void alias(Register rd, Register rn, int lsb, int width) {                        \
    assert ((lsb >> (5 + sf)) == 0, "lsb is out of range");                         \
    assert ((1 <= width) && (width <= (32 << sf) - lsb), "width is out of range");  \
    mnemonic(rd, rn, immr, imms);                                                   \
  }

  F(bfi_w,   bfm_w,  0, (-lsb) & 0x1f, width - 1)
  F(bfi,     bfm,    1, (-lsb) & 0x3f, width - 1)
  F(bfxil_w, bfm_w,  0, lsb,           lsb + width - 1)
  F(bfxil,   bfm,    1, lsb,           lsb + width - 1)
  F(sbfiz_w, sbfm_w, 0, (-lsb) & 0x1f, width - 1)
  F(sbfiz,   sbfm,   1, (-lsb) & 0x3f, width - 1)
  F(sbfx_w,  sbfm_w, 0, lsb,           lsb + width - 1)
  F(sbfx,    sbfm,   1, lsb,           lsb + width - 1)
  F(ubfiz_w, ubfm_w, 0, (-lsb) & 0x1f, width - 1)
  F(ubfiz,   ubfm,   1, (-lsb) & 0x3f, width - 1)
  F(ubfx_w,  ubfm_w, 0, lsb,           lsb + width - 1)
  F(ubfx,    ubfm,   1, lsb,           lsb + width - 1)
#undef F

#define F(alias, mnemonic, sf, immr, imms) \
  void alias(Register rd, Register rn, int shift) {              \
    assert ((shift >> (5 + sf)) == 0, "shift is out of range");  \
    mnemonic(rd, rn, immr, imms);                                \
  }

  F(_asr_w, sbfm_w, 0, shift, 31)
  F(_asr,   sbfm,   1, shift, 63)
  F(_lsl_w, ubfm_w, 0, (-shift) & 0x1f, 31 - shift)
  F(_lsl,   ubfm,   1, (-shift) & 0x3f, 63 - shift)
  F(_lsr_w, ubfm_w, 0, shift, 31)
  F(_lsr,   ubfm,   1, shift, 63)
#undef F

#define F(alias, mnemonic, immr, imms) \
  void alias(Register rd, Register rn) {   \
    mnemonic(rd, rn, immr, imms);          \
  }

  F(sxtb_w, sbfm_w, 0, 7)
  F(sxtb,   sbfm,   0, 7)
  F(sxth_w, sbfm_w, 0, 15)
  F(sxth,   sbfm,   0, 15)
  F(sxtw,   sbfm,   0, 31)
  F(uxtb_w, ubfm_w, 0, 7)
  F(uxtb,   ubfm,   0, 7)
  F(uxth_w, ubfm_w, 0, 15)
  F(uxth,   ubfm,   0, 15)
#undef F

  // Branch instructions

#define F(mnemonic, op) \
  void mnemonic(Register rn) {                                                             \
    emit_int32(0b1101011 << 25 | op << 21 | 0b11111 << 16 | rn->encoding_with_zr() << 5);  \
  }

  F(br,  0b00)
  F(blr, 0b01)
  F(ret, 0b10)
#undef F

  void ret() {
    ret(LR);
  }

#define F(mnemonic, op) \
  void mnemonic(address target) {                                         \
    intx offset = target - pc();                                          \
    assert (is_offset_in_range(offset, 26), "offset is out of range");    \
    emit_int32(op << 31 | 0b00101 << 26 | encode_offset(offset, 26, 0));  \
  }

  F(b,  0)
  F(bl, 1)
#undef F

  void b(address target, AsmCondition cond) {
    if (cond == al) {
      b(target);
    } else {
      intx offset = target - pc();
      assert (is_offset_in_range(offset, 19), "offset is out of range");
      emit_int32(0b0101010 << 25 | encode_offset(offset, 19, 5) | cond);
    }
  }


#define F(mnemonic, sf, op)                                             \
  void mnemonic(Register rt, address target) {                          \
    intx offset = target - pc();                                        \
    assert (is_offset_in_range(offset, 19), "offset is out of range");  \
    emit_int32(sf << 31 | 0b011010 << 25 | op << 24 | encode_offset(offset, 19, 5) | rt->encoding_with_zr()); \
  }                                                                     \

  F(cbz_w,  0, 0)
  F(cbnz_w, 0, 1)
  F(cbz,    1, 0)
  F(cbnz,   1, 1)
#undef F

#define F(mnemonic, op)                                                 \
  void mnemonic(Register rt, int bit, address target) {                 \
    intx offset = target - pc();                                        \
    assert (is_offset_in_range(offset, 14), "offset is out of range");  \
    assert (0 <= bit && bit < 64, "bit number is out of range");        \
    emit_int32((bit >> 5) << 31 | 0b011011 << 25 | op << 24 | (bit & 0x1f) << 19 | \
        encode_offset(offset, 14, 5) | rt->encoding_with_zr());         \
  }                                                                     \

  F(tbz,  0)
  F(tbnz, 1)
#undef F

  // System instructions

  enum DMB_Opt {
    DMB_ld  = 0b1101,
    DMB_st  = 0b1110,
    DMB_all = 0b1111
  };

#define F(mnemonic, L, op0, op1, CRn, op2, Rt) \
  void mnemonic(DMB_Opt option) {                                       \
    emit_int32(0b1101010100 << 22 | L << 21 | op0 << 19 | op1 << 16 |   \
        CRn << 12 | option << 8 | op2 << 5 | Rt);                       \
  }

  F(dsb,  0, 0b00, 0b011, 0b0011, 0b100, 0b11111)
  F(dmb,  0, 0b00, 0b011, 0b0011, 0b101, 0b11111)
#undef F

#define F(mnemonic, L, op0, op1, CRn, Rt) \
  void mnemonic(int imm) {                                              \
    assert ((imm >> 7) == 0, "immediate is out of range");              \
    emit_int32(0b1101010100 << 22 | L << 21 | op0 << 19 | op1 << 16 |   \
        CRn << 12 | imm << 5 | Rt);                                     \
  }

  F(hint, 0, 0b00, 0b011, 0b0010, 0b11111)
#undef F

  void nop() {
    hint(0);
  }

  void yield() {
    hint(1);
  }

#define F(mnemonic, opc, op2, LL) \
  void mnemonic(int imm = 0) {                                           \
    assert ((imm >> 16) == 0, "immediate is out of range");              \
    emit_int32(0b11010100 << 24 | opc << 21 | imm << 5 | op2 << 2 | LL); \
  }

  F(brk, 0b001, 0b000, 0b00)
  F(hlt, 0b010, 0b000, 0b00)
#undef F

  enum SystemRegister { // o0<1> op1<3> CRn<4> CRm<4> op2<3>
    SysReg_NZCV = 0b101101000010000,
    SysReg_FPCR = 0b101101000100000,
  };

  void mrs(Register rt, SystemRegister systemReg) {
    assert ((systemReg >> 15) == 0, "systemReg is out of range");
    emit_int32(0b110101010011 << 20 | systemReg << 5 | rt->encoding_with_zr());
  }

  void msr(SystemRegister systemReg, Register rt) {
    assert ((systemReg >> 15) == 0, "systemReg is out of range");
    emit_int32(0b110101010001 << 20 | systemReg << 5 | rt->encoding_with_zr());
  }

  // Floating-point instructions

#define F(mnemonic, M, S, type, opcode2) \
  void mnemonic(FloatRegister rn, FloatRegister rm) {                         \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |     \
        rm->encoding() << 16 | 0b1000 << 10 | rn->encoding() << 5 | opcode2); \
  }

  F(fcmp_s,   0, 0, 0b00, 0b00000)
  F(fcmpe_s,  0, 0, 0b00, 0b01000)
  F(fcmp_d,   0, 0, 0b01, 0b00000)
  F(fcmpe_d,  0, 0, 0b01, 0b10000)
#undef F

#define F(mnemonic, M, S, type, opcode2) \
  void mnemonic(FloatRegister rn) {                                           \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |     \
        0b1000 << 10 | rn->encoding() << 5 | opcode2);                        \
  }

  F(fcmp0_s,   0, 0, 0b00, 0b01000)
  F(fcmpe0_s,  0, 0, 0b00, 0b11000)
  F(fcmp0_d,   0, 0, 0b01, 0b01000)
  F(fcmpe0_d,  0, 0, 0b01, 0b11000)
#undef F

#define F(mnemonic, M, S, type, op) \
  void mnemonic(FloatRegister rn, FloatRegister rm, int nzcv, AsmCondition cond) { \
    assert ((nzcv >> 4) == 0, "illegal nzcv");                                                  \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |                       \
        rm->encoding() << 16 | cond << 12 | 0b01 << 10 | rn->encoding() << 5 | op << 4 | nzcv); \
  }

  F(fccmp_s,   0, 0, 0b00, 0)
  F(fccmpe_s,  0, 0, 0b00, 1)
  F(fccmp_d,   0, 0, 0b01, 0)
  F(fccmpe_d,  0, 0, 0b01, 1)
#undef F

#define F(mnemonic, M, S, type) \
  void mnemonic(FloatRegister rd, FloatRegister rn, FloatRegister rm, AsmCondition cond) { \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |                       \
        rm->encoding() << 16 | cond << 12 | 0b11 << 10 | rn->encoding() << 5 | rd->encoding()); \
  }

  F(fcsel_s,   0, 0, 0b00)
  F(fcsel_d,   0, 0, 0b01)
#undef F

#define F(mnemonic, M, S, type, opcode) \
  void mnemonic(FloatRegister rd, FloatRegister rn) { \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |      \
        opcode << 15 | 0b10000 << 10 | rn->encoding() << 5 | rd->encoding());  \
  }

  F(fmov_s,   0, 0, 0b00, 0b000000)
  F(fabs_s,   0, 0, 0b00, 0b000001)
  F(fneg_s,   0, 0, 0b00, 0b000010)
  F(fsqrt_s,  0, 0, 0b00, 0b000011)
  F(fcvt_ds,  0, 0, 0b00, 0b000101)
  F(fcvt_hs,  0, 0, 0b00, 0b000111)
  F(frintn_s, 0, 0, 0b00, 0b001000)
  F(frintp_s, 0, 0, 0b00, 0b001001)
  F(frintm_s, 0, 0, 0b00, 0b001010)
  F(frintz_s, 0, 0, 0b00, 0b001011)
  F(frinta_s, 0, 0, 0b00, 0b001100)
  F(frintx_s, 0, 0, 0b00, 0b001110)
  F(frinti_s, 0, 0, 0b00, 0b001111)

  F(fmov_d,   0, 0, 0b01, 0b000000)
  F(fabs_d,   0, 0, 0b01, 0b000001)
  F(fneg_d,   0, 0, 0b01, 0b000010)
  F(fsqrt_d,  0, 0, 0b01, 0b000011)
  F(fcvt_sd,  0, 0, 0b01, 0b000100)
  F(fcvt_hd,  0, 0, 0b01, 0b000111)
  F(frintn_d, 0, 0, 0b01, 0b001000)
  F(frintp_d, 0, 0, 0b01, 0b001001)
  F(frintm_d, 0, 0, 0b01, 0b001010)
  F(frintz_d, 0, 0, 0b01, 0b001011)
  F(frinta_d, 0, 0, 0b01, 0b001100)
  F(frintx_d, 0, 0, 0b01, 0b001110)
  F(frinti_d, 0, 0, 0b01, 0b001111)

  F(fcvt_sh,  0, 0, 0b11, 0b000100)
  F(fcvt_dh,  0, 0, 0b11, 0b000101)
#undef F

#define F(mnemonic, M, S, type, opcode) \
  void mnemonic(FloatRegister rd, FloatRegister rn, FloatRegister rm) { \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |                          \
        rm->encoding() << 16 | opcode << 12 | 0b10 << 10 | rn->encoding() << 5 | rd->encoding());  \
  }

  F(fmul_s,   0, 0, 0b00, 0b0000)
  F(fdiv_s,   0, 0, 0b00, 0b0001)
  F(fadd_s,   0, 0, 0b00, 0b0010)
  F(fsub_s,   0, 0, 0b00, 0b0011)
  F(fmax_s,   0, 0, 0b00, 0b0100)
  F(fmin_s,   0, 0, 0b00, 0b0101)
  F(fmaxnm_s, 0, 0, 0b00, 0b0110)
  F(fminnm_s, 0, 0, 0b00, 0b0111)
  F(fnmul_s,  0, 0, 0b00, 0b1000)

  F(fmul_d,   0, 0, 0b01, 0b0000)
  F(fdiv_d,   0, 0, 0b01, 0b0001)
  F(fadd_d,   0, 0, 0b01, 0b0010)
  F(fsub_d,   0, 0, 0b01, 0b0011)
  F(fmax_d,   0, 0, 0b01, 0b0100)
  F(fmin_d,   0, 0, 0b01, 0b0101)
  F(fmaxnm_d, 0, 0, 0b01, 0b0110)
  F(fminnm_d, 0, 0, 0b01, 0b0111)
  F(fnmul_d,  0, 0, 0b01, 0b1000)
#undef F

#define F(mnemonic, M, S, type, o1, o0) \
  void mnemonic(FloatRegister rd, FloatRegister rn, FloatRegister rm, FloatRegister ra) { \
    emit_int32(M << 31 | S << 29 | 0b11111 << 24 | type << 22 | o1 << 21 | rm->encoding() << 16 |  \
         o0 << 15 | ra->encoding() << 10 | rn->encoding() << 5 | rd->encoding());                  \
  }

  F(fmadd_s,  0, 0, 0b00, 0, 0)
  F(fmsub_s,  0, 0, 0b00, 0, 1)
  F(fnmadd_s, 0, 0, 0b00, 1, 0)
  F(fnmsub_s, 0, 0, 0b00, 1, 1)

  F(fmadd_d,  0, 0, 0b01, 0, 0)
  F(fmsub_d,  0, 0, 0b01, 0, 1)
  F(fnmadd_d, 0, 0, 0b01, 1, 0)
  F(fnmsub_d, 0, 0, 0b01, 1, 1)
#undef F

#define F(mnemonic, M, S, type) \
  void mnemonic(FloatRegister rd, int imm8) { \
    assert ((imm8 >> 8) == 0, "immediate is out of range");                \
    emit_int32(M << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |  \
         imm8 << 13 | 0b100 << 10 | rd->encoding());                       \
  }

  F(fmov_s, 0, 0, 0b00)
  F(fmov_d, 0, 0, 0b01)
#undef F

#define F(mnemonic, sf, S, type, rmode, opcode) \
  void mnemonic(Register rd, FloatRegister rn) {                                     \
    emit_int32(sf << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |           \
         rmode << 19 | opcode << 16 | rn->encoding() << 5 | rd->encoding_with_zr()); \
  }

  F(fcvtns_ws, 0, 0, 0b00, 0b00, 0b000)
  F(fcvtnu_ws, 0, 0, 0b00, 0b00, 0b001)
  F(fcvtas_ws, 0, 0, 0b00, 0b00, 0b100)
  F(fcvtau_ws, 0, 0, 0b00, 0b00, 0b101)
  F(fmov_ws,   0, 0, 0b00, 0b00, 0b110)
  F(fcvtps_ws, 0, 0, 0b00, 0b01, 0b000)
  F(fcvtpu_ws, 0, 0, 0b00, 0b01, 0b001)
  F(fcvtms_ws, 0, 0, 0b00, 0b10, 0b000)
  F(fcvtmu_ws, 0, 0, 0b00, 0b10, 0b001)
  F(fcvtzs_ws, 0, 0, 0b00, 0b11, 0b000)
  F(fcvtzu_ws, 0, 0, 0b00, 0b11, 0b001)

  F(fcvtns_wd, 0, 0, 0b01, 0b00, 0b000)
  F(fcvtnu_wd, 0, 0, 0b01, 0b00, 0b001)
  F(fcvtas_wd, 0, 0, 0b01, 0b00, 0b100)
  F(fcvtau_wd, 0, 0, 0b01, 0b00, 0b101)
  F(fcvtps_wd, 0, 0, 0b01, 0b01, 0b000)
  F(fcvtpu_wd, 0, 0, 0b01, 0b01, 0b001)
  F(fcvtms_wd, 0, 0, 0b01, 0b10, 0b000)
  F(fcvtmu_wd, 0, 0, 0b01, 0b10, 0b001)
  F(fcvtzs_wd, 0, 0, 0b01, 0b11, 0b000)
  F(fcvtzu_wd, 0, 0, 0b01, 0b11, 0b001)

  F(fcvtns_xs, 1, 0, 0b00, 0b00, 0b000)
  F(fcvtnu_xs, 1, 0, 0b00, 0b00, 0b001)
  F(fcvtas_xs, 1, 0, 0b00, 0b00, 0b100)
  F(fcvtau_xs, 1, 0, 0b00, 0b00, 0b101)
  F(fcvtps_xs, 1, 0, 0b00, 0b01, 0b000)
  F(fcvtpu_xs, 1, 0, 0b00, 0b01, 0b001)
  F(fcvtms_xs, 1, 0, 0b00, 0b10, 0b000)
  F(fcvtmu_xs, 1, 0, 0b00, 0b10, 0b001)
  F(fcvtzs_xs, 1, 0, 0b00, 0b11, 0b000)
  F(fcvtzu_xs, 1, 0, 0b00, 0b11, 0b001)

  F(fcvtns_xd, 1, 0, 0b01, 0b00, 0b000)
  F(fcvtnu_xd, 1, 0, 0b01, 0b00, 0b001)
  F(fcvtas_xd, 1, 0, 0b01, 0b00, 0b100)
  F(fcvtau_xd, 1, 0, 0b01, 0b00, 0b101)
  F(fmov_xd,   1, 0, 0b01, 0b00, 0b110)
  F(fcvtps_xd, 1, 0, 0b01, 0b01, 0b000)
  F(fcvtpu_xd, 1, 0, 0b01, 0b01, 0b001)
  F(fcvtms_xd, 1, 0, 0b01, 0b10, 0b000)
  F(fcvtmu_xd, 1, 0, 0b01, 0b10, 0b001)
  F(fcvtzs_xd, 1, 0, 0b01, 0b11, 0b000)
  F(fcvtzu_xd, 1, 0, 0b01, 0b11, 0b001)

  F(fmov_xq,   1, 0, 0b10, 0b01, 0b110)
#undef F

#define F(mnemonic, sf, S, type, rmode, opcode) \
  void mnemonic(FloatRegister rd, Register rn) {                                     \
    emit_int32(sf << 31 | S << 29 | 0b11110 << 24 | type << 22 | 1 << 21 |           \
         rmode << 19 | opcode << 16 | rn->encoding_with_zr() << 5 | rd->encoding()); \
  }

  F(scvtf_sw,  0, 0, 0b00, 0b00, 0b010)
  F(ucvtf_sw,  0, 0, 0b00, 0b00, 0b011)
  F(fmov_sw,   0, 0, 0b00, 0b00, 0b111)
  F(scvtf_dw,  0, 0, 0b01, 0b00, 0b010)
  F(ucvtf_dw,  0, 0, 0b01, 0b00, 0b011)

  F(scvtf_sx,  1, 0, 0b00, 0b00, 0b010)
  F(ucvtf_sx,  1, 0, 0b00, 0b00, 0b011)
  F(scvtf_dx,  1, 0, 0b01, 0b00, 0b010)
  F(ucvtf_dx,  1, 0, 0b01, 0b00, 0b011)
  F(fmov_dx,   1, 0, 0b01, 0b00, 0b111)

  F(fmov_qx,   1, 0, 0b10, 0b01, 0b111)
#undef F

#define F(mnemonic, opcode) \
  void mnemonic(FloatRegister Vd, FloatRegister Vn) {                                     \
    emit_int32( opcode << 10 | Vn->encoding() << 5 | Vd->encoding());             \
  }

  F(aese, 0b0100111000101000010010);
  F(aesd, 0b0100111000101000010110);
  F(aesmc, 0b0100111000101000011010);
  F(aesimc, 0b0100111000101000011110);
#undef F

#ifdef COMPILER2
  typedef VFP::double_num double_num;
  typedef VFP::float_num  float_num;
#endif

  void vcnt(FloatRegister Dd, FloatRegister Dn, int quad = 0, int size = 0) {
    // emitted at VM startup to detect whether the instruction is available
    assert(!VM_Version::is_initialized() || VM_Version::has_simd(), "simd instruction");
    assert(size == 0, "illegal size value");
    emit_int32(0x0e205800 | quad << 30 | size << 22 | Dn->encoding() << 5 | Dd->encoding());
  }

#ifdef COMPILER2
  void addv(FloatRegister Dd, FloatRegister Dm, int quad, int size) {
    // emitted at VM startup to detect whether the instruction is available
    assert(VM_Version::has_simd(), "simd instruction");
    assert((quad & ~1) == 0, "illegal value");
    assert(size >= 0 && size < 3, "illegal value");
    assert(((size << 1) | quad) != 4, "illegal values (size 2, quad 0)");
    emit_int32(0x0e31b800 | quad << 30 | size << 22 | Dm->encoding() << 5 | Dd->encoding());
  }

  enum VElem_Size {
    VELEM_SIZE_8  = 0x00,
    VELEM_SIZE_16 = 0x01,
    VELEM_SIZE_32 = 0x02,
    VELEM_SIZE_64 = 0x03
  };

  enum VLD_Type {
    VLD1_TYPE_1_REG  = 0b0111,
    VLD1_TYPE_2_REGS = 0b1010,
    VLD1_TYPE_3_REGS = 0b0110,
    VLD1_TYPE_4_REGS = 0b0010
  };

  enum VFloat_Arith_Size {
    VFA_SIZE_F32 = 0b0,
    VFA_SIZE_F64 = 0b1
  };

#define F(mnemonic, U, S, P) \
  void mnemonic(FloatRegister fd, FloatRegister fn, FloatRegister fm,    \
                int size, int quad) {                                    \
    assert(VM_Version::has_simd(), "simd instruction");                  \
    assert(!(size == VFA_SIZE_F64 && !quad), "reserved");                \
    assert((size & 1) == size, "overflow");                              \
    emit_int32(quad << 30 | U << 29 | 0b01110 << 24 |                    \
               S << 23 | size << 22 | 1 << 21 | P << 11 | 1 << 10 |      \
               fm->encoding() << 16 |                                    \
               fn->encoding() <<  5 |                                    \
               fd->encoding());                                          \
  }

  F(vaddF, 0, 0, 0b11010)  // Vd = Vn + Vm (float)
  F(vsubF, 0, 1, 0b11010)  // Vd = Vn - Vm (float)
  F(vmulF, 1, 0, 0b11011)  // Vd = Vn - Vm (float)
  F(vdivF, 1, 0, 0b11111)  // Vd = Vn / Vm (float)
#undef F

#define F(mnemonic, U) \
  void mnemonic(FloatRegister fd, FloatRegister fm, FloatRegister fn,    \
                int size, int quad) {                                    \
    assert(VM_Version::has_simd(), "simd instruction");                  \
    assert(!(size == VELEM_SIZE_64 && !quad), "reserved");               \
    assert((size & 0b11) == size, "overflow");                           \
    int R = 0; /* rounding */                                            \
    int S = 0; /* saturating */                                          \
    emit_int32(quad << 30 | U << 29 | 0b01110 << 24 | size << 22 |       \
               1 << 21 | R << 12 | S << 11 | 0b10001 << 10 |             \
               fm->encoding() << 16 |                                    \
               fn->encoding() <<  5 |                                    \
               fd->encoding());                                          \
  }

  F(vshlSI, 0)  // Vd = ashift(Vn,Vm) (int)
  F(vshlUI, 1)  // Vd = lshift(Vn,Vm) (int)
#undef F

#define F(mnemonic, U, P, M) \
  void mnemonic(FloatRegister fd, FloatRegister fn, FloatRegister fm,    \
                int size, int quad) {                                    \
    assert(VM_Version::has_simd(), "simd instruction");                  \
    assert(!(size == VELEM_SIZE_64 && !quad), "reserved");               \
    assert(!(size == VELEM_SIZE_64 && M), "reserved");                   \
    assert((size & 0b11) == size, "overflow");                           \
    emit_int32(quad << 30 | U << 29 | 0b01110 << 24 | size << 22 |       \
               1 << 21 | P << 11 | 1 << 10 |                             \
               fm->encoding() << 16 |                                    \
               fn->encoding() <<  5 |                                    \
               fd->encoding());                                          \
  }

  F(vmulI, 0, 0b10011,  true)  // Vd = Vn * Vm (int)
  F(vaddI, 0, 0b10000, false)  // Vd = Vn + Vm (int)
  F(vsubI, 1, 0b10000, false)  // Vd = Vn - Vm (int)
#undef F

#define F(mnemonic, U, O) \
  void mnemonic(FloatRegister fd, FloatRegister fn, FloatRegister fm,    \
                int quad) {                                              \
    assert(VM_Version::has_simd(), "simd instruction");                  \
    emit_int32(quad << 30 | U << 29 | 0b01110 << 24 | O << 22 |          \
               1 << 21 | 0b00011 << 11 | 1 << 10 |                       \
               fm->encoding() << 16 |                                    \
               fn->encoding() <<  5 |                                    \
               fd->encoding());                                          \
  }

  F(vandI, 0, 0b00)  // Vd = Vn & Vm (int)
  F(vorI,  0, 0b10)  // Vd = Vn | Vm (int)
  F(vxorI, 1, 0b00)  // Vd = Vn ^ Vm (int)
#undef F

  void vnegI(FloatRegister fd, FloatRegister fn, int size, int quad) {
    int U = 1;
    assert(VM_Version::has_simd(), "simd instruction");
    assert(quad || size != VELEM_SIZE_64, "reserved");
    emit_int32(quad << 30 | U << 29 | 0b01110 << 24 |
              size << 22 | 0b100000101110 << 10 |
              fn->encoding() << 5 |
              fd->encoding() << 0);
  }

  void vshli(FloatRegister fd, FloatRegister fn, int esize, int imm, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");

    if (imm >= esize) {
      // maximum shift gives all zeroes, direction doesn't matter,
      // but only available for shift right
      vshri(fd, fn, esize, esize, true /* unsigned */, quad);
      return;
    }
    assert(imm >= 0 && imm < esize, "out of range");

    int imm7 = esize + imm;
    int immh = imm7 >> 3;
    assert(immh != 0, "encoding constraint");
    assert((uint)immh < 16, "sanity");
    assert(((immh >> 2) | quad) != 0b10, "reserved");
    emit_int32(quad << 30 | 0b011110 << 23 | imm7 << 16 |
               0b010101 << 10 | fn->encoding() << 5 | fd->encoding() << 0);
  }

  void vshri(FloatRegister fd, FloatRegister fn, int esize, int imm,
             bool U /* unsigned */, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(imm > 0, "out of range");
    if (imm >= esize) {
      // maximum shift (all zeroes)
      imm = esize;
    }
    int imm7 = 2 * esize - imm ;
    int immh = imm7 >> 3;
    assert(immh != 0, "encoding constraint");
    assert((uint)immh < 16, "sanity");
    assert(((immh >> 2) | quad) != 0b10, "reserved");
    emit_int32(quad << 30 | U << 29 | 0b011110 << 23 | imm7 << 16 |
               0b000001 << 10 | fn->encoding() << 5 | fd->encoding() << 0);
  }
  void vshrUI(FloatRegister fd, FloatRegister fm, int size, int imm, int quad) {
    vshri(fd, fm, size, imm, true /* unsigned */, quad);
  }
  void vshrSI(FloatRegister fd, FloatRegister fm, int size, int imm, int quad) {
    vshri(fd, fm, size, imm, false /* signed */, quad);
  }

  void vld1(FloatRegister Vt, Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(addr.disp() == 0 || addr.disp() == 16, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 1;
    int opcode = VLD1_TYPE_1_REG;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vst1(FloatRegister Vt, Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(addr.disp() == 0 || addr.disp() == 16, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 0;
    int opcode = VLD1_TYPE_1_REG;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vld1(FloatRegister Vt, FloatRegister Vt2, Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(Vt->successor() == Vt2, "Registers must be ordered");
    assert(addr.disp() == 0 || addr.disp() == 32, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 1;
    int opcode = VLD1_TYPE_2_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vst1(FloatRegister Vt, FloatRegister Vt2, Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(Vt->successor() == Vt2, "Registers must be ordered");
    assert(bits == 128, "unsupported");
    assert(addr.disp() == 0 || addr.disp() == 32, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 0;
    int opcode = VLD1_TYPE_2_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vld1(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,
            Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3,
          "Registers must be ordered");
    assert(addr.disp() == 0 || addr.disp() == 48, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 1;
    int opcode = VLD1_TYPE_3_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vst1(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,
            Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(Vt->successor() == Vt2 &&  Vt2->successor() == Vt3,
           "Registers must be ordered");
    assert(addr.disp() == 0 || addr.disp() == 48, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 0;
    int opcode = VLD1_TYPE_3_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vld1(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,
            FloatRegister Vt4, Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3 &&
           Vt3->successor() == Vt4, "Registers must be ordered");
    assert(addr.disp() == 0 || addr.disp() == 64, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 1;
    int opcode = VLD1_TYPE_4_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void vst1(FloatRegister Vt, FloatRegister Vt2, FloatRegister Vt3,
            FloatRegister Vt4,  Address addr, VElem_Size size, int bits) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(bits == 128, "unsupported");
    assert(Vt->successor() == Vt2 && Vt2->successor() == Vt3 &&
           Vt3->successor() == Vt4, "Registers must be ordered");
    assert(addr.disp() == 0 || addr.disp() == 64, "must be");
    int type = 0b11; // 2D
    int quad = 1;
    int L = 0;
    int opcode = VLD1_TYPE_4_REGS;
    emit_int32(quad << 30 | 0b11 << 26 | L << 22 | opcode << 12 | size << 10 |
               Vt->encoding() << 0 | addr.encoding_simd());
  }

  void rev32(FloatRegister Vd, FloatRegister Vn, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(size == VELEM_SIZE_8 || size == VELEM_SIZE_16, "must be");
    emit_int32(quad << 30 | 0b101110 << 24 | size << 22 |
               0b100000000010 << 10 | Vn->encoding() << 5 | Vd->encoding());
  }

  void eor(FloatRegister Vd, FloatRegister Vn,  FloatRegister Vm, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(size == VELEM_SIZE_8, "must be");
    emit_int32(quad << 30 | 0b101110001 << 21 | Vm->encoding() << 16 |
               0b000111 << 10 | Vn->encoding() << 5 | Vd->encoding());
  }

  void orr(FloatRegister Vd, FloatRegister Vn,  FloatRegister Vm, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(size == VELEM_SIZE_8, "must be");
    emit_int32(quad << 30 | 0b001110101 << 21 | Vm->encoding() << 16 |
               0b000111 << 10 | Vn->encoding() << 5 | Vd->encoding());
  }

  void vmovI(FloatRegister Dd, int imm8, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(imm8 >= 0 && imm8 < 256, "out of range");
    int op;
    int cmode;
    switch (size) {
    case VELEM_SIZE_8:
      op = 0;
      cmode = 0b1110;
      break;
    case VELEM_SIZE_16:
      op = 0;
      cmode = 0b1000;
      break;
    case VELEM_SIZE_32:
      op = 0;
      cmode = 0b0000;
      break;
    default:
      cmode = 0;
      ShouldNotReachHere();
    }
    int abc = imm8 >> 5;
    int defgh = imm8 & 0b11111;
    emit_int32(quad << 30 | op << 29 | 0b1111 << 24 |
               abc << 16 | cmode << 12 | 0b01 << 10 |
               defgh << 5 | Dd->encoding() << 0);
  }

  void vdupI(FloatRegister Dd, Register Rn, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    assert(size <= 3, "unallocated encoding");
    assert(size != 3 || quad == 1, "reserved");
    int imm5 = 1 << size;
#ifdef ASSERT
    switch (size) {
    case VELEM_SIZE_8:
      assert(imm5 == 0b00001, "sanity");
      break;
    case VELEM_SIZE_16:
      assert(imm5 == 0b00010, "sanity");
      break;
    case VELEM_SIZE_32:
      assert(imm5 == 0b00100, "sanity");
      break;
    case VELEM_SIZE_64:
      assert(imm5 == 0b01000, "sanity");
      break;
    default:
      ShouldNotReachHere();
    }
#endif
    emit_int32(quad << 30 | 0b111 << 25 | 0b11 << 10 |
               imm5 << 16 | Rn->encoding() << 5 |
               Dd->encoding() << 0);
  }

  void vdup(FloatRegister Vd, FloatRegister Vn, VElem_Size size, int quad) {
    assert(VM_Version::has_simd(), "simd instruction");
    int index = 0;
    int bytes = 1 << size;
    int range = 16 / bytes;
    assert(index < range, "overflow");

    assert(size != VELEM_SIZE_64 || quad, "reserved");
    assert(8 << VELEM_SIZE_8  ==  8, "sanity");
    assert(8 << VELEM_SIZE_16 == 16, "sanity");
    assert(8 << VELEM_SIZE_32 == 32, "sanity");
    assert(8 << VELEM_SIZE_64 == 64, "sanity");

    int imm5 = (index << (size + 1)) | bytes;

    emit_int32(quad << 30 | 0b001110000 << 21 | imm5 << 16 | 0b000001 << 10 |
               Vn->encoding() << 5 | Vd->encoding() << 0);
  }

  void vdupF(FloatRegister Vd, FloatRegister Vn, int quad) {
    vdup(Vd, Vn, VELEM_SIZE_32, quad);
  }

  void vdupD(FloatRegister Vd, FloatRegister Vn, int quad) {
    vdup(Vd, Vn, VELEM_SIZE_64, quad);
  }
#endif
};


#endif // CPU_ARM_VM_ASSEMBLER_ARM_64_HPP
