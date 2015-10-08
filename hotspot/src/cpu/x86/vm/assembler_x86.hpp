/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_ASSEMBLER_X86_HPP
#define CPU_X86_VM_ASSEMBLER_X86_HPP

#include "asm/register.hpp"
#include "vm_version_x86.hpp"

class BiasedLockingCounters;

// Contains all the definitions needed for x86 assembly code generation.

// Calling convention
class Argument VALUE_OBJ_CLASS_SPEC {
 public:
  enum {
#ifdef _LP64
#ifdef _WIN64
    n_int_register_parameters_c   = 4, // rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 4,  // xmm0 - xmm3 (c_farg0, c_farg1, ... )
#else
    n_int_register_parameters_c   = 6, // rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8,  // xmm0 - xmm7 (c_farg0, c_farg1, ... )
#endif // _WIN64
    n_int_register_parameters_j   = 6, // j_rarg0, j_rarg1, ...
    n_float_register_parameters_j = 8  // j_farg0, j_farg1, ...
#else
    n_register_parameters = 0   // 0 registers used to pass arguments
#endif // _LP64
  };
};


#ifdef _LP64
// Symbolically name the register arguments used by the c calling convention.
// Windows is different from linux/solaris. So much for standards...

#ifdef _WIN64

REGISTER_DECLARATION(Register, c_rarg0, rcx);
REGISTER_DECLARATION(Register, c_rarg1, rdx);
REGISTER_DECLARATION(Register, c_rarg2, r8);
REGISTER_DECLARATION(Register, c_rarg3, r9);

REGISTER_DECLARATION(XMMRegister, c_farg0, xmm0);
REGISTER_DECLARATION(XMMRegister, c_farg1, xmm1);
REGISTER_DECLARATION(XMMRegister, c_farg2, xmm2);
REGISTER_DECLARATION(XMMRegister, c_farg3, xmm3);

#else

REGISTER_DECLARATION(Register, c_rarg0, rdi);
REGISTER_DECLARATION(Register, c_rarg1, rsi);
REGISTER_DECLARATION(Register, c_rarg2, rdx);
REGISTER_DECLARATION(Register, c_rarg3, rcx);
REGISTER_DECLARATION(Register, c_rarg4, r8);
REGISTER_DECLARATION(Register, c_rarg5, r9);

REGISTER_DECLARATION(XMMRegister, c_farg0, xmm0);
REGISTER_DECLARATION(XMMRegister, c_farg1, xmm1);
REGISTER_DECLARATION(XMMRegister, c_farg2, xmm2);
REGISTER_DECLARATION(XMMRegister, c_farg3, xmm3);
REGISTER_DECLARATION(XMMRegister, c_farg4, xmm4);
REGISTER_DECLARATION(XMMRegister, c_farg5, xmm5);
REGISTER_DECLARATION(XMMRegister, c_farg6, xmm6);
REGISTER_DECLARATION(XMMRegister, c_farg7, xmm7);

#endif // _WIN64

// Symbolically name the register arguments used by the Java calling convention.
// We have control over the convention for java so we can do what we please.
// What pleases us is to offset the java calling convention so that when
// we call a suitable jni method the arguments are lined up and we don't
// have to do little shuffling. A suitable jni method is non-static and a
// small number of arguments (two fewer args on windows)
//
//        |-------------------------------------------------------|
//        | c_rarg0   c_rarg1  c_rarg2 c_rarg3 c_rarg4 c_rarg5    |
//        |-------------------------------------------------------|
//        | rcx       rdx      r8      r9      rdi*    rsi*       | windows (* not a c_rarg)
//        | rdi       rsi      rdx     rcx     r8      r9         | solaris/linux
//        |-------------------------------------------------------|
//        | j_rarg5   j_rarg0  j_rarg1 j_rarg2 j_rarg3 j_rarg4    |
//        |-------------------------------------------------------|

REGISTER_DECLARATION(Register, j_rarg0, c_rarg1);
REGISTER_DECLARATION(Register, j_rarg1, c_rarg2);
REGISTER_DECLARATION(Register, j_rarg2, c_rarg3);
// Windows runs out of register args here
#ifdef _WIN64
REGISTER_DECLARATION(Register, j_rarg3, rdi);
REGISTER_DECLARATION(Register, j_rarg4, rsi);
#else
REGISTER_DECLARATION(Register, j_rarg3, c_rarg4);
REGISTER_DECLARATION(Register, j_rarg4, c_rarg5);
#endif /* _WIN64 */
REGISTER_DECLARATION(Register, j_rarg5, c_rarg0);

REGISTER_DECLARATION(XMMRegister, j_farg0, xmm0);
REGISTER_DECLARATION(XMMRegister, j_farg1, xmm1);
REGISTER_DECLARATION(XMMRegister, j_farg2, xmm2);
REGISTER_DECLARATION(XMMRegister, j_farg3, xmm3);
REGISTER_DECLARATION(XMMRegister, j_farg4, xmm4);
REGISTER_DECLARATION(XMMRegister, j_farg5, xmm5);
REGISTER_DECLARATION(XMMRegister, j_farg6, xmm6);
REGISTER_DECLARATION(XMMRegister, j_farg7, xmm7);

REGISTER_DECLARATION(Register, rscratch1, r10);  // volatile
REGISTER_DECLARATION(Register, rscratch2, r11);  // volatile

REGISTER_DECLARATION(Register, r12_heapbase, r12); // callee-saved
REGISTER_DECLARATION(Register, r15_thread, r15); // callee-saved

#else
// rscratch1 will apear in 32bit code that is dead but of course must compile
// Using noreg ensures if the dead code is incorrectly live and executed it
// will cause an assertion failure
#define rscratch1 noreg
#define rscratch2 noreg

#endif // _LP64

// JSR 292
// On x86, the SP does not have to be saved when invoking method handle intrinsics
// or compiled lambda forms. We indicate that by setting rbp_mh_SP_save to noreg.
REGISTER_DECLARATION(Register, rbp_mh_SP_save, noreg);

// Address is an abstraction used to represent a memory location
// using any of the amd64 addressing modes with one object.
//
// Note: A register location is represented via a Register, not
//       via an address for efficiency & simplicity reasons.

class ArrayAddress;

class Address VALUE_OBJ_CLASS_SPEC {
 public:
  enum ScaleFactor {
    no_scale = -1,
    times_1  =  0,
    times_2  =  1,
    times_4  =  2,
    times_8  =  3,
    times_ptr = LP64_ONLY(times_8) NOT_LP64(times_4)
  };
  static ScaleFactor times(int size) {
    assert(size >= 1 && size <= 8 && is_power_of_2(size), "bad scale size");
    if (size == 8)  return times_8;
    if (size == 4)  return times_4;
    if (size == 2)  return times_2;
    return times_1;
  }
  static int scale_size(ScaleFactor scale) {
    assert(scale != no_scale, "");
    assert(((1 << (int)times_1) == 1 &&
            (1 << (int)times_2) == 2 &&
            (1 << (int)times_4) == 4 &&
            (1 << (int)times_8) == 8), "");
    return (1 << (int)scale);
  }

 private:
  Register         _base;
  Register         _index;
  ScaleFactor      _scale;
  int              _disp;
  RelocationHolder _rspec;

  // Easily misused constructors make them private
  // %%% can we make these go away?
  NOT_LP64(Address(address loc, RelocationHolder spec);)
  Address(int disp, address loc, relocInfo::relocType rtype);
  Address(int disp, address loc, RelocationHolder spec);

 public:

 int disp() { return _disp; }
  // creation
  Address()
    : _base(noreg),
      _index(noreg),
      _scale(no_scale),
      _disp(0) {
  }

  // No default displacement otherwise Register can be implicitly
  // converted to 0(Register) which is quite a different animal.

  Address(Register base, int disp)
    : _base(base),
      _index(noreg),
      _scale(no_scale),
      _disp(disp) {
  }

  Address(Register base, Register index, ScaleFactor scale, int disp = 0)
    : _base (base),
      _index(index),
      _scale(scale),
      _disp (disp) {
    assert(!index->is_valid() == (scale == Address::no_scale),
           "inconsistent address");
  }

  Address(Register base, RegisterOrConstant index, ScaleFactor scale = times_1, int disp = 0)
    : _base (base),
      _index(index.register_or_noreg()),
      _scale(scale),
      _disp (disp + (index.constant_or_zero() * scale_size(scale))) {
    if (!index.is_register())  scale = Address::no_scale;
    assert(!_index->is_valid() == (scale == Address::no_scale),
           "inconsistent address");
  }

  Address plus_disp(int disp) const {
    Address a = (*this);
    a._disp += disp;
    return a;
  }
  Address plus_disp(RegisterOrConstant disp, ScaleFactor scale = times_1) const {
    Address a = (*this);
    a._disp += disp.constant_or_zero() * scale_size(scale);
    if (disp.is_register()) {
      assert(!a.index()->is_valid(), "competing indexes");
      a._index = disp.as_register();
      a._scale = scale;
    }
    return a;
  }
  bool is_same_address(Address a) const {
    // disregard _rspec
    return _base == a._base && _disp == a._disp && _index == a._index && _scale == a._scale;
  }

  // The following two overloads are used in connection with the
  // ByteSize type (see sizes.hpp).  They simplify the use of
  // ByteSize'd arguments in assembly code. Note that their equivalent
  // for the optimized build are the member functions with int disp
  // argument since ByteSize is mapped to an int type in that case.
  //
  // Note: DO NOT introduce similar overloaded functions for WordSize
  // arguments as in the optimized mode, both ByteSize and WordSize
  // are mapped to the same type and thus the compiler cannot make a
  // distinction anymore (=> compiler errors).

#ifdef ASSERT
  Address(Register base, ByteSize disp)
    : _base(base),
      _index(noreg),
      _scale(no_scale),
      _disp(in_bytes(disp)) {
  }

  Address(Register base, Register index, ScaleFactor scale, ByteSize disp)
    : _base(base),
      _index(index),
      _scale(scale),
      _disp(in_bytes(disp)) {
    assert(!index->is_valid() == (scale == Address::no_scale),
           "inconsistent address");
  }

  Address(Register base, RegisterOrConstant index, ScaleFactor scale, ByteSize disp)
    : _base (base),
      _index(index.register_or_noreg()),
      _scale(scale),
      _disp (in_bytes(disp) + (index.constant_or_zero() * scale_size(scale))) {
    if (!index.is_register())  scale = Address::no_scale;
    assert(!_index->is_valid() == (scale == Address::no_scale),
           "inconsistent address");
  }

#endif // ASSERT

  // accessors
  bool        uses(Register reg) const { return _base == reg || _index == reg; }
  Register    base()             const { return _base;  }
  Register    index()            const { return _index; }
  ScaleFactor scale()            const { return _scale; }
  int         disp()             const { return _disp;  }

  // Convert the raw encoding form into the form expected by the constructor for
  // Address.  An index of 4 (rsp) corresponds to having no index, so convert
  // that to noreg for the Address constructor.
  static Address make_raw(int base, int index, int scale, int disp, relocInfo::relocType disp_reloc);

  static Address make_array(ArrayAddress);

 private:
  bool base_needs_rex() const {
    return _base != noreg && _base->encoding() >= 8;
  }

  bool index_needs_rex() const {
    return _index != noreg &&_index->encoding() >= 8;
  }

  relocInfo::relocType reloc() const { return _rspec.type(); }

  friend class Assembler;
  friend class MacroAssembler;
  friend class LIR_Assembler; // base/index/scale/disp
};

//
// AddressLiteral has been split out from Address because operands of this type
// need to be treated specially on 32bit vs. 64bit platforms. By splitting it out
// the few instructions that need to deal with address literals are unique and the
// MacroAssembler does not have to implement every instruction in the Assembler
// in order to search for address literals that may need special handling depending
// on the instruction and the platform. As small step on the way to merging i486/amd64
// directories.
//
class AddressLiteral VALUE_OBJ_CLASS_SPEC {
  friend class ArrayAddress;
  RelocationHolder _rspec;
  // Typically we use AddressLiterals we want to use their rval
  // However in some situations we want the lval (effect address) of the item.
  // We provide a special factory for making those lvals.
  bool _is_lval;

  // If the target is far we'll need to load the ea of this to
  // a register to reach it. Otherwise if near we can do rip
  // relative addressing.

  address          _target;

 protected:
  // creation
  AddressLiteral()
    : _is_lval(false),
      _target(NULL)
  {}

  public:


  AddressLiteral(address target, relocInfo::relocType rtype);

  AddressLiteral(address target, RelocationHolder const& rspec)
    : _rspec(rspec),
      _is_lval(false),
      _target(target)
  {}

  AddressLiteral addr() {
    AddressLiteral ret = *this;
    ret._is_lval = true;
    return ret;
  }


 private:

  address target() { return _target; }
  bool is_lval() { return _is_lval; }

  relocInfo::relocType reloc() const { return _rspec.type(); }
  const RelocationHolder& rspec() const { return _rspec; }

  friend class Assembler;
  friend class MacroAssembler;
  friend class Address;
  friend class LIR_Assembler;
};

// Convience classes
class RuntimeAddress: public AddressLiteral {

  public:

  RuntimeAddress(address target) : AddressLiteral(target, relocInfo::runtime_call_type) {}

};

class ExternalAddress: public AddressLiteral {
 private:
  static relocInfo::relocType reloc_for_target(address target) {
    // Sometimes ExternalAddress is used for values which aren't
    // exactly addresses, like the card table base.
    // external_word_type can't be used for values in the first page
    // so just skip the reloc in that case.
    return external_word_Relocation::can_be_relocated(target) ? relocInfo::external_word_type : relocInfo::none;
  }

 public:

  ExternalAddress(address target) : AddressLiteral(target, reloc_for_target(target)) {}

};

class InternalAddress: public AddressLiteral {

  public:

  InternalAddress(address target) : AddressLiteral(target, relocInfo::internal_word_type) {}

};

// x86 can do array addressing as a single operation since disp can be an absolute
// address amd64 can't. We create a class that expresses the concept but does extra
// magic on amd64 to get the final result

class ArrayAddress VALUE_OBJ_CLASS_SPEC {
  private:

  AddressLiteral _base;
  Address        _index;

  public:

  ArrayAddress() {};
  ArrayAddress(AddressLiteral base, Address index): _base(base), _index(index) {};
  AddressLiteral base() { return _base; }
  Address index() { return _index; }

};

// 64-bit refect the fxsave size which is 512 bytes and the new xsave area on EVEX which is another 2176 bytes
// See fxsave and xsave(EVEX enabled) documentation for layout
const int FPUStateSizeInWords = NOT_LP64(27) LP64_ONLY(2688 / wordSize);

// The Intel x86/Amd64 Assembler: Pure assembler doing NO optimizations on the instruction
// level (e.g. mov rax, 0 is not translated into xor rax, rax!); i.e., what you write
// is what you get. The Assembler is generating code into a CodeBuffer.

class Assembler : public AbstractAssembler  {
  friend class AbstractAssembler; // for the non-virtual hack
  friend class LIR_Assembler; // as_Address()
  friend class StubGenerator;

 public:
  enum Condition {                     // The x86 condition codes used for conditional jumps/moves.
    zero          = 0x4,
    notZero       = 0x5,
    equal         = 0x4,
    notEqual      = 0x5,
    less          = 0xc,
    lessEqual     = 0xe,
    greater       = 0xf,
    greaterEqual  = 0xd,
    below         = 0x2,
    belowEqual    = 0x6,
    above         = 0x7,
    aboveEqual    = 0x3,
    overflow      = 0x0,
    noOverflow    = 0x1,
    carrySet      = 0x2,
    carryClear    = 0x3,
    negative      = 0x8,
    positive      = 0x9,
    parity        = 0xa,
    noParity      = 0xb
  };

  enum Prefix {
    // segment overrides
    CS_segment = 0x2e,
    SS_segment = 0x36,
    DS_segment = 0x3e,
    ES_segment = 0x26,
    FS_segment = 0x64,
    GS_segment = 0x65,

    REX        = 0x40,

    REX_B      = 0x41,
    REX_X      = 0x42,
    REX_XB     = 0x43,
    REX_R      = 0x44,
    REX_RB     = 0x45,
    REX_RX     = 0x46,
    REX_RXB    = 0x47,

    REX_W      = 0x48,

    REX_WB     = 0x49,
    REX_WX     = 0x4A,
    REX_WXB    = 0x4B,
    REX_WR     = 0x4C,
    REX_WRB    = 0x4D,
    REX_WRX    = 0x4E,
    REX_WRXB   = 0x4F,

    VEX_3bytes = 0xC4,
    VEX_2bytes = 0xC5,
    EVEX_4bytes = 0x62,
    Prefix_EMPTY = 0x0
  };

  enum VexPrefix {
    VEX_B = 0x20,
    VEX_X = 0x40,
    VEX_R = 0x80,
    VEX_W = 0x80
  };

  enum ExexPrefix {
    EVEX_F  = 0x04,
    EVEX_V  = 0x08,
    EVEX_Rb = 0x10,
    EVEX_X  = 0x40,
    EVEX_Z  = 0x80
  };

  enum VexSimdPrefix {
    VEX_SIMD_NONE = 0x0,
    VEX_SIMD_66   = 0x1,
    VEX_SIMD_F3   = 0x2,
    VEX_SIMD_F2   = 0x3
  };

  enum VexOpcode {
    VEX_OPCODE_NONE  = 0x0,
    VEX_OPCODE_0F    = 0x1,
    VEX_OPCODE_0F_38 = 0x2,
    VEX_OPCODE_0F_3A = 0x3
  };

  enum AvxVectorLen {
    AVX_128bit = 0x0,
    AVX_256bit = 0x1,
    AVX_512bit = 0x2,
    AVX_NoVec  = 0x4
  };

  enum EvexTupleType {
    EVEX_FV   = 0,
    EVEX_HV   = 4,
    EVEX_FVM  = 6,
    EVEX_T1S  = 7,
    EVEX_T1F  = 11,
    EVEX_T2   = 13,
    EVEX_T4   = 15,
    EVEX_T8   = 17,
    EVEX_HVM  = 18,
    EVEX_QVM  = 19,
    EVEX_OVM  = 20,
    EVEX_M128 = 21,
    EVEX_DUP  = 22,
    EVEX_ETUP = 23
  };

  enum EvexInputSizeInBits {
    EVEX_8bit  = 0,
    EVEX_16bit = 1,
    EVEX_32bit = 2,
    EVEX_64bit = 3
  };

  enum WhichOperand {
    // input to locate_operand, and format code for relocations
    imm_operand  = 0,            // embedded 32-bit|64-bit immediate operand
    disp32_operand = 1,          // embedded 32-bit displacement or address
    call32_operand = 2,          // embedded 32-bit self-relative displacement
#ifndef _LP64
    _WhichOperand_limit = 3
#else
     narrow_oop_operand = 3,     // embedded 32-bit immediate narrow oop
    _WhichOperand_limit = 4
#endif
  };



  // NOTE: The general philopsophy of the declarations here is that 64bit versions
  // of instructions are freely declared without the need for wrapping them an ifdef.
  // (Some dangerous instructions are ifdef's out of inappropriate jvm's.)
  // In the .cpp file the implementations are wrapped so that they are dropped out
  // of the resulting jvm. This is done mostly to keep the footprint of MINIMAL
  // to the size it was prior to merging up the 32bit and 64bit assemblers.
  //
  // This does mean you'll get a linker/runtime error if you use a 64bit only instruction
  // in a 32bit vm. This is somewhat unfortunate but keeps the ifdef noise down.

private:

  int _evex_encoding;
  int _input_size_in_bits;
  int _avx_vector_len;
  int _tuple_type;
  bool _is_evex_instruction;
  bool _legacy_mode_bw;
  bool _legacy_mode_dq;
  bool _legacy_mode_vl;
  bool _legacy_mode_vlbw;
  bool _instruction_uses_vl;

  // 64bit prefixes
  int prefix_and_encode(int reg_enc, bool byteinst = false);
  int prefixq_and_encode(int reg_enc);

  int prefix_and_encode(int dst_enc, int src_enc, bool byteinst = false);
  int prefixq_and_encode(int dst_enc, int src_enc);

  void prefix(Register reg);
  void prefix(Register dst, Register src, Prefix p);
  void prefix(Register dst, Address adr, Prefix p);
  void prefix(Address adr);
  void prefixq(Address adr);

  void prefix(Address adr, Register reg,  bool byteinst = false);
  void prefix(Address adr, XMMRegister reg);
  void prefixq(Address adr, Register reg);
  void prefixq(Address adr, XMMRegister reg);

  void prefetch_prefix(Address src);

  void rex_prefix(Address adr, XMMRegister xreg,
                  VexSimdPrefix pre, VexOpcode opc, bool rex_w);
  int  rex_prefix_and_encode(int dst_enc, int src_enc,
                             VexSimdPrefix pre, VexOpcode opc, bool rex_w);

  void vex_prefix(bool vex_r, bool vex_b, bool vex_x, bool vex_w,
                  int nds_enc, VexSimdPrefix pre, VexOpcode opc,
                  int vector_len);

  void evex_prefix(bool vex_r, bool vex_b, bool vex_x, bool vex_w, bool evex_r, bool evex_v,
                   int nds_enc, VexSimdPrefix pre, VexOpcode opc,
                   bool is_extended_context, bool is_merge_context,
                   int vector_len, bool no_mask_reg );

  void vex_prefix(Address adr, int nds_enc, int xreg_enc,
                  VexSimdPrefix pre, VexOpcode opc,
                  bool vex_w, int vector_len,
                  bool legacy_mode = false, bool no_mask_reg = false);

  void vex_prefix(XMMRegister dst, XMMRegister nds, Address src,
                  VexSimdPrefix pre, int vector_len = AVX_128bit,
                  bool no_mask_reg = false, bool legacy_mode = false) {
    int dst_enc = dst->encoding();
    int nds_enc = nds->is_valid() ? nds->encoding() : 0;
    vex_prefix(src, nds_enc, dst_enc, pre, VEX_OPCODE_0F, false, vector_len, legacy_mode, no_mask_reg);
  }

  void vex_prefix_q(XMMRegister dst, XMMRegister nds, Address src,
                    VexSimdPrefix pre, int vector_len = AVX_128bit,
                    bool no_mask_reg = false) {
    int dst_enc = dst->encoding();
    int nds_enc = nds->is_valid() ? nds->encoding() : 0;
    vex_prefix(src, nds_enc, dst_enc, pre, VEX_OPCODE_0F, true, vector_len, false, no_mask_reg);
  }

  void vex_prefix_0F38(Register dst, Register nds, Address src, bool no_mask_reg = false) {
    bool vex_w = false;
    int vector_len = AVX_128bit;
    vex_prefix(src, nds->encoding(), dst->encoding(),
               VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w,
               vector_len, no_mask_reg);
  }

  void vex_prefix_0F38_legacy(Register dst, Register nds, Address src, bool no_mask_reg = false) {
    bool vex_w = false;
    int vector_len = AVX_128bit;
    vex_prefix(src, nds->encoding(), dst->encoding(),
               VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w,
               vector_len, true, no_mask_reg);
  }

  void vex_prefix_0F38_q(Register dst, Register nds, Address src, bool no_mask_reg = false) {
    bool vex_w = true;
    int vector_len = AVX_128bit;
    vex_prefix(src, nds->encoding(), dst->encoding(),
               VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w,
               vector_len, no_mask_reg);
  }

  void vex_prefix_0F38_q_legacy(Register dst, Register nds, Address src, bool no_mask_reg = false) {
    bool vex_w = true;
    int vector_len = AVX_128bit;
    vex_prefix(src, nds->encoding(), dst->encoding(),
               VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w,
               vector_len, true, no_mask_reg);
  }

  int  vex_prefix_and_encode(int dst_enc, int nds_enc, int src_enc,
                             VexSimdPrefix pre, VexOpcode opc,
                             bool vex_w, int vector_len,
                             bool legacy_mode, bool no_mask_reg);

  int  vex_prefix_0F38_and_encode(Register dst, Register nds, Register src, bool no_mask_reg = false) {
    bool vex_w = false;
    int vector_len = AVX_128bit;
    return vex_prefix_and_encode(dst->encoding(), nds->encoding(), src->encoding(),
                                 VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w, vector_len,
                                 false, no_mask_reg);
  }

  int  vex_prefix_0F38_and_encode_legacy(Register dst, Register nds, Register src, bool no_mask_reg = false) {
    bool vex_w = false;
    int vector_len = AVX_128bit;
    return vex_prefix_and_encode(dst->encoding(), nds->encoding(), src->encoding(),
      VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w, vector_len,
      true, no_mask_reg);
  }

  int  vex_prefix_0F38_and_encode_q(Register dst, Register nds, Register src, bool no_mask_reg = false) {
    bool vex_w = true;
    int vector_len = AVX_128bit;
    return vex_prefix_and_encode(dst->encoding(), nds->encoding(), src->encoding(),
                                 VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w, vector_len,
                                 false, no_mask_reg);
  }

  int  vex_prefix_0F38_and_encode_q_legacy(Register dst, Register nds, Register src, bool no_mask_reg = false) {
    bool vex_w = true;
    int vector_len = AVX_128bit;
    return vex_prefix_and_encode(dst->encoding(), nds->encoding(), src->encoding(),
                                 VEX_SIMD_NONE, VEX_OPCODE_0F_38, vex_w, vector_len,
                                 true, no_mask_reg);
  }

  int  vex_prefix_and_encode(XMMRegister dst, XMMRegister nds, XMMRegister src,
                             VexSimdPrefix pre, int vector_len = AVX_128bit,
                             VexOpcode opc = VEX_OPCODE_0F, bool legacy_mode = false,
                             bool no_mask_reg = false) {
    int src_enc = src->encoding();
    int dst_enc = dst->encoding();
    int nds_enc = nds->is_valid() ? nds->encoding() : 0;
    return vex_prefix_and_encode(dst_enc, nds_enc, src_enc, pre, opc, false, vector_len, legacy_mode, no_mask_reg);
  }

  void simd_prefix(XMMRegister xreg, XMMRegister nds, Address adr,
                   VexSimdPrefix pre, bool no_mask_reg, VexOpcode opc = VEX_OPCODE_0F,
                   bool rex_w = false, int vector_len = AVX_128bit, bool legacy_mode = false);

  void simd_prefix(XMMRegister dst, Address src, VexSimdPrefix pre,
                   bool no_mask_reg, VexOpcode opc = VEX_OPCODE_0F) {
    simd_prefix(dst, xnoreg, src, pre, no_mask_reg, opc);
  }

  void simd_prefix(Address dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg) {
    simd_prefix(src, dst, pre, no_mask_reg);
  }
  void simd_prefix_q(XMMRegister dst, XMMRegister nds, Address src,
                     VexSimdPrefix pre, bool no_mask_reg = false) {
    bool rex_w = true;
    simd_prefix(dst, nds, src, pre, no_mask_reg, VEX_OPCODE_0F, rex_w);
  }

  int simd_prefix_and_encode(XMMRegister dst, XMMRegister nds, XMMRegister src,
                             VexSimdPrefix pre, bool no_mask_reg,
                             VexOpcode opc = VEX_OPCODE_0F,
                             bool rex_w = false, int vector_len = AVX_128bit,
                             bool legacy_mode = false);

  int kreg_prefix_and_encode(KRegister dst, KRegister nds, KRegister src,
                             VexSimdPrefix pre, bool no_mask_reg,
                             VexOpcode opc = VEX_OPCODE_0F,
                             bool rex_w = false, int vector_len = AVX_128bit);

  int kreg_prefix_and_encode(KRegister dst, KRegister nds, Register src,
                             VexSimdPrefix pre, bool no_mask_reg,
                             VexOpcode opc = VEX_OPCODE_0F,
                             bool rex_w = false, int vector_len = AVX_128bit);

  // Move/convert 32-bit integer value.
  int simd_prefix_and_encode(XMMRegister dst, XMMRegister nds, Register src,
                             VexSimdPrefix pre, bool no_mask_reg) {
    // It is OK to cast from Register to XMMRegister to pass argument here
    // since only encoding is used in simd_prefix_and_encode() and number of
    // Gen and Xmm registers are the same.
    return simd_prefix_and_encode(dst, nds, as_XMMRegister(src->encoding()), pre, no_mask_reg, VEX_OPCODE_0F);
  }
  int simd_prefix_and_encode(XMMRegister dst, Register src, VexSimdPrefix pre, bool no_mask_reg) {
    return simd_prefix_and_encode(dst, xnoreg, src, pre, no_mask_reg);
  }
  int simd_prefix_and_encode(Register dst, XMMRegister src,
                             VexSimdPrefix pre, VexOpcode opc = VEX_OPCODE_0F,
                             bool no_mask_reg = false) {
    return simd_prefix_and_encode(as_XMMRegister(dst->encoding()), xnoreg, src, pre, no_mask_reg, opc);
  }

  // Move/convert 64-bit integer value.
  int simd_prefix_and_encode_q(XMMRegister dst, XMMRegister nds, Register src,
                               VexSimdPrefix pre, bool no_mask_reg = false) {
    bool rex_w = true;
    return simd_prefix_and_encode(dst, nds, as_XMMRegister(src->encoding()), pre, no_mask_reg, VEX_OPCODE_0F, rex_w);
  }
  int simd_prefix_and_encode_q(XMMRegister dst, Register src, VexSimdPrefix pre, bool no_mask_reg) {
    return simd_prefix_and_encode_q(dst, xnoreg, src, pre, no_mask_reg);
  }
  int simd_prefix_and_encode_q(Register dst, XMMRegister src,
                               VexSimdPrefix pre, VexOpcode opc = VEX_OPCODE_0F,
                               bool no_mask_reg = false) {
    bool rex_w = true;
    return simd_prefix_and_encode(as_XMMRegister(dst->encoding()), xnoreg, src, pre, no_mask_reg, opc, rex_w);
  }

  // Helper functions for groups of instructions
  void emit_arith_b(int op1, int op2, Register dst, int imm8);

  void emit_arith(int op1, int op2, Register dst, int32_t imm32);
  // Force generation of a 4 byte immediate value even if it fits into 8bit
  void emit_arith_imm32(int op1, int op2, Register dst, int32_t imm32);
  void emit_arith(int op1, int op2, Register dst, Register src);

  void emit_simd_arith(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg = false, bool legacy_mode = false);
  void emit_simd_arith_q(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg = false);
  void emit_simd_arith(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg = false, bool legacy_mode = false);
  void emit_simd_arith_q(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg = false);
  void emit_simd_arith_nonds(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg = false);
  void emit_simd_arith_nonds_q(int opcode, XMMRegister dst, Address src, VexSimdPrefix pre, bool no_mask_reg = false);
  void emit_simd_arith_nonds(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg = false, bool legacy_mode = false);
  void emit_simd_arith_nonds_q(int opcode, XMMRegister dst, XMMRegister src, VexSimdPrefix pre, bool no_mask_reg = false);
  void emit_vex_arith(int opcode, XMMRegister dst, XMMRegister nds,
                      Address src, VexSimdPrefix pre, int vector_len,
                      bool no_mask_reg = false, bool legacy_mode = false);
  void emit_vex_arith_q(int opcode, XMMRegister dst, XMMRegister nds,
                        Address src, VexSimdPrefix pre, int vector_len,
                        bool no_mask_reg = false);
  void emit_vex_arith(int opcode, XMMRegister dst, XMMRegister nds,
                      XMMRegister src, VexSimdPrefix pre, int vector_len,
                      bool no_mask_reg = false, bool legacy_mode = false);
  void emit_vex_arith_q(int opcode, XMMRegister dst, XMMRegister nds,
                        XMMRegister src, VexSimdPrefix pre, int vector_len,
                        bool no_mask_reg = false);

  bool emit_compressed_disp_byte(int &disp);

  void emit_operand(Register reg,
                    Register base, Register index, Address::ScaleFactor scale,
                    int disp,
                    RelocationHolder const& rspec,
                    int rip_relative_correction = 0);

  void emit_operand(Register reg, Address adr, int rip_relative_correction = 0);

  // operands that only take the original 32bit registers
  void emit_operand32(Register reg, Address adr);

  void emit_operand(XMMRegister reg,
                    Register base, Register index, Address::ScaleFactor scale,
                    int disp,
                    RelocationHolder const& rspec);

  void emit_operand(XMMRegister reg, Address adr);

  void emit_operand(MMXRegister reg, Address adr);

  // workaround gcc (3.2.1-7) bug
  void emit_operand(Address adr, MMXRegister reg);


  // Immediate-to-memory forms
  void emit_arith_operand(int op1, Register rm, Address adr, int32_t imm32);

  void emit_farith(int b1, int b2, int i);


 protected:
  #ifdef ASSERT
  void check_relocation(RelocationHolder const& rspec, int format);
  #endif

  void emit_data(jint data, relocInfo::relocType    rtype, int format);
  void emit_data(jint data, RelocationHolder const& rspec, int format);
  void emit_data64(jlong data, relocInfo::relocType rtype, int format = 0);
  void emit_data64(jlong data, RelocationHolder const& rspec, int format = 0);

  bool reachable(AddressLiteral adr) NOT_LP64({ return true;});

  // These are all easily abused and hence protected

  // 32BIT ONLY SECTION
#ifndef _LP64
  // Make these disappear in 64bit mode since they would never be correct
  void cmp_literal32(Register src1, int32_t imm32, RelocationHolder const& rspec);   // 32BIT ONLY
  void cmp_literal32(Address src1, int32_t imm32, RelocationHolder const& rspec);    // 32BIT ONLY

  void mov_literal32(Register dst, int32_t imm32, RelocationHolder const& rspec);    // 32BIT ONLY
  void mov_literal32(Address dst, int32_t imm32, RelocationHolder const& rspec);     // 32BIT ONLY

  void push_literal32(int32_t imm32, RelocationHolder const& rspec);                 // 32BIT ONLY
#else
  // 64BIT ONLY SECTION
  void mov_literal64(Register dst, intptr_t imm64, RelocationHolder const& rspec);   // 64BIT ONLY

  void cmp_narrow_oop(Register src1, int32_t imm32, RelocationHolder const& rspec);
  void cmp_narrow_oop(Address src1, int32_t imm32, RelocationHolder const& rspec);

  void mov_narrow_oop(Register dst, int32_t imm32, RelocationHolder const& rspec);
  void mov_narrow_oop(Address dst, int32_t imm32, RelocationHolder const& rspec);
#endif // _LP64

  // These are unique in that we are ensured by the caller that the 32bit
  // relative in these instructions will always be able to reach the potentially
  // 64bit address described by entry. Since they can take a 64bit address they
  // don't have the 32 suffix like the other instructions in this class.

  void call_literal(address entry, RelocationHolder const& rspec);
  void jmp_literal(address entry, RelocationHolder const& rspec);

  // Avoid using directly section
  // Instructions in this section are actually usable by anyone without danger
  // of failure but have performance issues that are addressed my enhanced
  // instructions which will do the proper thing base on the particular cpu.
  // We protect them because we don't trust you...

  // Don't use next inc() and dec() methods directly. INC & DEC instructions
  // could cause a partial flag stall since they don't set CF flag.
  // Use MacroAssembler::decrement() & MacroAssembler::increment() methods
  // which call inc() & dec() or add() & sub() in accordance with
  // the product flag UseIncDec value.

  void decl(Register dst);
  void decl(Address dst);
  void decq(Register dst);
  void decq(Address dst);

  void incl(Register dst);
  void incl(Address dst);
  void incq(Register dst);
  void incq(Address dst);

  // New cpus require use of movsd and movss to avoid partial register stall
  // when loading from memory. But for old Opteron use movlpd instead of movsd.
  // The selection is done in MacroAssembler::movdbl() and movflt().

  // Move Scalar Single-Precision Floating-Point Values
  void movss(XMMRegister dst, Address src);
  void movss(XMMRegister dst, XMMRegister src);
  void movss(Address dst, XMMRegister src);

  // Move Scalar Double-Precision Floating-Point Values
  void movsd(XMMRegister dst, Address src);
  void movsd(XMMRegister dst, XMMRegister src);
  void movsd(Address dst, XMMRegister src);
  void movlpd(XMMRegister dst, Address src);

  // New cpus require use of movaps and movapd to avoid partial register stall
  // when moving between registers.
  void movaps(XMMRegister dst, XMMRegister src);
  void movapd(XMMRegister dst, XMMRegister src);

  // End avoid using directly


  // Instruction prefixes
  void prefix(Prefix p);

  public:

  // Creation
  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
    init_attributes();
  }

  // Decoding
  static address locate_operand(address inst, WhichOperand which);
  static address locate_next_instruction(address inst);

  // Utilities
  static bool is_polling_page_far() NOT_LP64({ return false;});
  static bool query_compressed_disp_byte(int disp, bool is_evex_inst, int vector_len,
                                         int cur_tuple_type, int in_size_in_bits, int cur_encoding);

  // Generic instructions
  // Does 32bit or 64bit as needed for the platform. In some sense these
  // belong in macro assembler but there is no need for both varieties to exist

  void init_attributes(void) {
    _evex_encoding = 0;
    _input_size_in_bits = 0;
    _avx_vector_len = AVX_NoVec;
    _tuple_type = EVEX_ETUP;
    _is_evex_instruction = false;
    _legacy_mode_bw = (VM_Version::supports_avx512bw() == false);
    _legacy_mode_dq = (VM_Version::supports_avx512dq() == false);
    _legacy_mode_vl = (VM_Version::supports_avx512vl() == false);
    _legacy_mode_vlbw = (VM_Version::supports_avx512vlbw() == false);
    _instruction_uses_vl = false;
  }

  void lea(Register dst, Address src);

  void mov(Register dst, Register src);

  void pusha();
  void popa();

  void pushf();
  void popf();

  void push(int32_t imm32);

  void push(Register src);

  void pop(Register dst);

  // These are dummies to prevent surprise implicit conversions to Register
  void push(void* v);
  void pop(void* v);

  // These do register sized moves/scans
  void rep_mov();
  void rep_stos();
  void rep_stosb();
  void repne_scan();
#ifdef _LP64
  void repne_scanl();
#endif

  // Vanilla instructions in lexical order

  void adcl(Address dst, int32_t imm32);
  void adcl(Address dst, Register src);
  void adcl(Register dst, int32_t imm32);
  void adcl(Register dst, Address src);
  void adcl(Register dst, Register src);

  void adcq(Register dst, int32_t imm32);
  void adcq(Register dst, Address src);
  void adcq(Register dst, Register src);

  void addl(Address dst, int32_t imm32);
  void addl(Address dst, Register src);
  void addl(Register dst, int32_t imm32);
  void addl(Register dst, Address src);
  void addl(Register dst, Register src);

  void addq(Address dst, int32_t imm32);
  void addq(Address dst, Register src);
  void addq(Register dst, int32_t imm32);
  void addq(Register dst, Address src);
  void addq(Register dst, Register src);

#ifdef _LP64
 //Add Unsigned Integers with Carry Flag
  void adcxq(Register dst, Register src);

 //Add Unsigned Integers with Overflow Flag
  void adoxq(Register dst, Register src);
#endif

  void addr_nop_4();
  void addr_nop_5();
  void addr_nop_7();
  void addr_nop_8();

  // Add Scalar Double-Precision Floating-Point Values
  void addsd(XMMRegister dst, Address src);
  void addsd(XMMRegister dst, XMMRegister src);

  // Add Scalar Single-Precision Floating-Point Values
  void addss(XMMRegister dst, Address src);
  void addss(XMMRegister dst, XMMRegister src);

  // AES instructions
  void aesdec(XMMRegister dst, Address src);
  void aesdec(XMMRegister dst, XMMRegister src);
  void aesdeclast(XMMRegister dst, Address src);
  void aesdeclast(XMMRegister dst, XMMRegister src);
  void aesenc(XMMRegister dst, Address src);
  void aesenc(XMMRegister dst, XMMRegister src);
  void aesenclast(XMMRegister dst, Address src);
  void aesenclast(XMMRegister dst, XMMRegister src);


  void andl(Address  dst, int32_t imm32);
  void andl(Register dst, int32_t imm32);
  void andl(Register dst, Address src);
  void andl(Register dst, Register src);

  void andq(Address  dst, int32_t imm32);
  void andq(Register dst, int32_t imm32);
  void andq(Register dst, Address src);
  void andq(Register dst, Register src);

  // BMI instructions
  void andnl(Register dst, Register src1, Register src2);
  void andnl(Register dst, Register src1, Address src2);
  void andnq(Register dst, Register src1, Register src2);
  void andnq(Register dst, Register src1, Address src2);

  void blsil(Register dst, Register src);
  void blsil(Register dst, Address src);
  void blsiq(Register dst, Register src);
  void blsiq(Register dst, Address src);

  void blsmskl(Register dst, Register src);
  void blsmskl(Register dst, Address src);
  void blsmskq(Register dst, Register src);
  void blsmskq(Register dst, Address src);

  void blsrl(Register dst, Register src);
  void blsrl(Register dst, Address src);
  void blsrq(Register dst, Register src);
  void blsrq(Register dst, Address src);

  void bsfl(Register dst, Register src);
  void bsrl(Register dst, Register src);

#ifdef _LP64
  void bsfq(Register dst, Register src);
  void bsrq(Register dst, Register src);
#endif

  void bswapl(Register reg);

  void bswapq(Register reg);

  void call(Label& L, relocInfo::relocType rtype);
  void call(Register reg);  // push pc; pc <- reg
  void call(Address adr);   // push pc; pc <- adr

  void cdql();

  void cdqq();

  void cld();

  void clflush(Address adr);

  void cmovl(Condition cc, Register dst, Register src);
  void cmovl(Condition cc, Register dst, Address src);

  void cmovq(Condition cc, Register dst, Register src);
  void cmovq(Condition cc, Register dst, Address src);


  void cmpb(Address dst, int imm8);

  void cmpl(Address dst, int32_t imm32);

  void cmpl(Register dst, int32_t imm32);
  void cmpl(Register dst, Register src);
  void cmpl(Register dst, Address src);

  void cmpq(Address dst, int32_t imm32);
  void cmpq(Address dst, Register src);

  void cmpq(Register dst, int32_t imm32);
  void cmpq(Register dst, Register src);
  void cmpq(Register dst, Address src);

  // these are dummies used to catch attempting to convert NULL to Register
  void cmpl(Register dst, void* junk); // dummy
  void cmpq(Register dst, void* junk); // dummy

  void cmpw(Address dst, int imm16);

  void cmpxchg8 (Address adr);

  void cmpxchgb(Register reg, Address adr);
  void cmpxchgl(Register reg, Address adr);

  void cmpxchgq(Register reg, Address adr);

  // Ordered Compare Scalar Double-Precision Floating-Point Values and set EFLAGS
  void comisd(XMMRegister dst, Address src);
  void comisd(XMMRegister dst, XMMRegister src);

  // Ordered Compare Scalar Single-Precision Floating-Point Values and set EFLAGS
  void comiss(XMMRegister dst, Address src);
  void comiss(XMMRegister dst, XMMRegister src);

  // Identify processor type and features
  void cpuid();

  // CRC32C
  void crc32(Register crc, Register v, int8_t sizeInBytes);
  void crc32(Register crc, Address adr, int8_t sizeInBytes);

  // Convert Scalar Double-Precision Floating-Point Value to Scalar Single-Precision Floating-Point Value
  void cvtsd2ss(XMMRegister dst, XMMRegister src);
  void cvtsd2ss(XMMRegister dst, Address src);

  // Convert Doubleword Integer to Scalar Double-Precision Floating-Point Value
  void cvtsi2sdl(XMMRegister dst, Register src);
  void cvtsi2sdl(XMMRegister dst, Address src);
  void cvtsi2sdq(XMMRegister dst, Register src);
  void cvtsi2sdq(XMMRegister dst, Address src);

  // Convert Doubleword Integer to Scalar Single-Precision Floating-Point Value
  void cvtsi2ssl(XMMRegister dst, Register src);
  void cvtsi2ssl(XMMRegister dst, Address src);
  void cvtsi2ssq(XMMRegister dst, Register src);
  void cvtsi2ssq(XMMRegister dst, Address src);

  // Convert Packed Signed Doubleword Integers to Packed Double-Precision Floating-Point Value
  void cvtdq2pd(XMMRegister dst, XMMRegister src);

  // Convert Packed Signed Doubleword Integers to Packed Single-Precision Floating-Point Value
  void cvtdq2ps(XMMRegister dst, XMMRegister src);

  // Convert Scalar Single-Precision Floating-Point Value to Scalar Double-Precision Floating-Point Value
  void cvtss2sd(XMMRegister dst, XMMRegister src);
  void cvtss2sd(XMMRegister dst, Address src);

  // Convert with Truncation Scalar Double-Precision Floating-Point Value to Doubleword Integer
  void cvttsd2sil(Register dst, Address src);
  void cvttsd2sil(Register dst, XMMRegister src);
  void cvttsd2siq(Register dst, XMMRegister src);

  // Convert with Truncation Scalar Single-Precision Floating-Point Value to Doubleword Integer
  void cvttss2sil(Register dst, XMMRegister src);
  void cvttss2siq(Register dst, XMMRegister src);

  // Divide Scalar Double-Precision Floating-Point Values
  void divsd(XMMRegister dst, Address src);
  void divsd(XMMRegister dst, XMMRegister src);

  // Divide Scalar Single-Precision Floating-Point Values
  void divss(XMMRegister dst, Address src);
  void divss(XMMRegister dst, XMMRegister src);

  void emms();

  void fabs();

  void fadd(int i);

  void fadd_d(Address src);
  void fadd_s(Address src);

  // "Alternate" versions of x87 instructions place result down in FPU
  // stack instead of on TOS

  void fadda(int i); // "alternate" fadd
  void faddp(int i = 1);

  void fchs();

  void fcom(int i);

  void fcomp(int i = 1);
  void fcomp_d(Address src);
  void fcomp_s(Address src);

  void fcompp();

  void fcos();

  void fdecstp();

  void fdiv(int i);
  void fdiv_d(Address src);
  void fdivr_s(Address src);
  void fdiva(int i);  // "alternate" fdiv
  void fdivp(int i = 1);

  void fdivr(int i);
  void fdivr_d(Address src);
  void fdiv_s(Address src);

  void fdivra(int i); // "alternate" reversed fdiv

  void fdivrp(int i = 1);

  void ffree(int i = 0);

  void fild_d(Address adr);
  void fild_s(Address adr);

  void fincstp();

  void finit();

  void fist_s (Address adr);
  void fistp_d(Address adr);
  void fistp_s(Address adr);

  void fld1();

  void fld_d(Address adr);
  void fld_s(Address adr);
  void fld_s(int index);
  void fld_x(Address adr);  // extended-precision (80-bit) format

  void fldcw(Address src);

  void fldenv(Address src);

  void fldlg2();

  void fldln2();

  void fldz();

  void flog();
  void flog10();

  void fmul(int i);

  void fmul_d(Address src);
  void fmul_s(Address src);

  void fmula(int i);  // "alternate" fmul

  void fmulp(int i = 1);

  void fnsave(Address dst);

  void fnstcw(Address src);

  void fnstsw_ax();

  void fprem();
  void fprem1();

  void frstor(Address src);

  void fsin();

  void fsqrt();

  void fst_d(Address adr);
  void fst_s(Address adr);

  void fstp_d(Address adr);
  void fstp_d(int index);
  void fstp_s(Address adr);
  void fstp_x(Address adr); // extended-precision (80-bit) format

  void fsub(int i);
  void fsub_d(Address src);
  void fsub_s(Address src);

  void fsuba(int i);  // "alternate" fsub

  void fsubp(int i = 1);

  void fsubr(int i);
  void fsubr_d(Address src);
  void fsubr_s(Address src);

  void fsubra(int i); // "alternate" reversed fsub

  void fsubrp(int i = 1);

  void ftan();

  void ftst();

  void fucomi(int i = 1);
  void fucomip(int i = 1);

  void fwait();

  void fxch(int i = 1);

  void fxrstor(Address src);
  void xrstor(Address src);

  void fxsave(Address dst);
  void xsave(Address dst);

  void fyl2x();
  void frndint();
  void f2xm1();
  void fldl2e();

  void hlt();

  void idivl(Register src);
  void divl(Register src); // Unsigned division

#ifdef _LP64
  void idivq(Register src);
#endif

  void imull(Register dst, Register src);
  void imull(Register dst, Register src, int value);
  void imull(Register dst, Address src);

#ifdef _LP64
  void imulq(Register dst, Register src);
  void imulq(Register dst, Register src, int value);
  void imulq(Register dst, Address src);
#endif

  // jcc is the generic conditional branch generator to run-
  // time routines, jcc is used for branches to labels. jcc
  // takes a branch opcode (cc) and a label (L) and generates
  // either a backward branch or a forward branch and links it
  // to the label fixup chain. Usage:
  //
  // Label L;      // unbound label
  // jcc(cc, L);   // forward branch to unbound label
  // bind(L);      // bind label to the current pc
  // jcc(cc, L);   // backward branch to bound label
  // bind(L);      // illegal: a label may be bound only once
  //
  // Note: The same Label can be used for forward and backward branches
  // but it may be bound only once.

  void jcc(Condition cc, Label& L, bool maybe_short = true);

  // Conditional jump to a 8-bit offset to L.
  // WARNING: be very careful using this for forward jumps.  If the label is
  // not bound within an 8-bit offset of this instruction, a run-time error
  // will occur.
  void jccb(Condition cc, Label& L);

  void jmp(Address entry);    // pc <- entry

  // Label operations & relative jumps (PPUM Appendix D)
  void jmp(Label& L, bool maybe_short = true);   // unconditional jump to L

  void jmp(Register entry); // pc <- entry

  // Unconditional 8-bit offset jump to L.
  // WARNING: be very careful using this for forward jumps.  If the label is
  // not bound within an 8-bit offset of this instruction, a run-time error
  // will occur.
  void jmpb(Label& L);

  void ldmxcsr( Address src );

  void leal(Register dst, Address src);

  void leaq(Register dst, Address src);

  void lfence();

  void lock();

  void lzcntl(Register dst, Register src);

#ifdef _LP64
  void lzcntq(Register dst, Register src);
#endif

  enum Membar_mask_bits {
    StoreStore = 1 << 3,
    LoadStore  = 1 << 2,
    StoreLoad  = 1 << 1,
    LoadLoad   = 1 << 0
  };

  // Serializes memory and blows flags
  void membar(Membar_mask_bits order_constraint) {
    if (os::is_MP()) {
      // We only have to handle StoreLoad
      if (order_constraint & StoreLoad) {
        // All usable chips support "locked" instructions which suffice
        // as barriers, and are much faster than the alternative of
        // using cpuid instruction. We use here a locked add [esp-C],0.
        // This is conveniently otherwise a no-op except for blowing
        // flags, and introducing a false dependency on target memory
        // location. We can't do anything with flags, but we can avoid
        // memory dependencies in the current method by locked-adding
        // somewhere else on the stack. Doing [esp+C] will collide with
        // something on stack in current method, hence we go for [esp-C].
        // It is convenient since it is almost always in data cache, for
        // any small C.  We need to step back from SP to avoid data
        // dependencies with other things on below SP (callee-saves, for
        // example). Without a clear way to figure out the minimal safe
        // distance from SP, it makes sense to step back the complete
        // cache line, as this will also avoid possible second-order effects
        // with locked ops against the cache line. Our choice of offset
        // is bounded by x86 operand encoding, which should stay within
        // [-128; +127] to have the 8-byte displacement encoding.
        //
        // Any change to this code may need to revisit other places in
        // the code where this idiom is used, in particular the
        // orderAccess code.

        int offset = -VM_Version::L1_line_size();
        if (offset < -128) {
          offset = -128;
        }

        lock();
        addl(Address(rsp, offset), 0);// Assert the lock# signal here
      }
    }
  }

  void mfence();

  // Moves

  void mov64(Register dst, int64_t imm64);

  void movb(Address dst, Register src);
  void movb(Address dst, int imm8);
  void movb(Register dst, Address src);

  void kmovql(KRegister dst, KRegister src);
  void kmovql(KRegister dst, Register src);
  void kmovdl(KRegister dst, Register src);
  void kmovwl(KRegister dst, Register src);
  void kmovql(Address dst, KRegister src);
  void kmovql(KRegister dst, Address src);

  void movdl(XMMRegister dst, Register src);
  void movdl(Register dst, XMMRegister src);
  void movdl(XMMRegister dst, Address src);
  void movdl(Address dst, XMMRegister src);

  // Move Double Quadword
  void movdq(XMMRegister dst, Register src);
  void movdq(Register dst, XMMRegister src);

  // Move Aligned Double Quadword
  void movdqa(XMMRegister dst, XMMRegister src);
  void movdqa(XMMRegister dst, Address src);

  // Move Unaligned Double Quadword
  void movdqu(Address     dst, XMMRegister src);
  void movdqu(XMMRegister dst, Address src);
  void movdqu(XMMRegister dst, XMMRegister src);

  // Move Unaligned 256bit Vector
  void vmovdqu(Address dst, XMMRegister src);
  void vmovdqu(XMMRegister dst, Address src);
  void vmovdqu(XMMRegister dst, XMMRegister src);

   // Move Unaligned 512bit Vector
  void evmovdqul(Address dst, XMMRegister src, int vector_len);
  void evmovdqul(XMMRegister dst, Address src, int vector_len);
  void evmovdqul(XMMRegister dst, XMMRegister src, int vector_len);
  void evmovdquq(Address dst, XMMRegister src, int vector_len);
  void evmovdquq(XMMRegister dst, Address src, int vector_len);
  void evmovdquq(XMMRegister dst, XMMRegister src, int vector_len);

  // Move lower 64bit to high 64bit in 128bit register
  void movlhps(XMMRegister dst, XMMRegister src);

  void movl(Register dst, int32_t imm32);
  void movl(Address dst, int32_t imm32);
  void movl(Register dst, Register src);
  void movl(Register dst, Address src);
  void movl(Address dst, Register src);

  // These dummies prevent using movl from converting a zero (like NULL) into Register
  // by giving the compiler two choices it can't resolve

  void movl(Address  dst, void* junk);
  void movl(Register dst, void* junk);

#ifdef _LP64
  void movq(Register dst, Register src);
  void movq(Register dst, Address src);
  void movq(Address  dst, Register src);
#endif

  void movq(Address     dst, MMXRegister src );
  void movq(MMXRegister dst, Address src );

#ifdef _LP64
  // These dummies prevent using movq from converting a zero (like NULL) into Register
  // by giving the compiler two choices it can't resolve

  void movq(Address  dst, void* dummy);
  void movq(Register dst, void* dummy);
#endif

  // Move Quadword
  void movq(Address     dst, XMMRegister src);
  void movq(XMMRegister dst, Address src);

  void movsbl(Register dst, Address src);
  void movsbl(Register dst, Register src);

#ifdef _LP64
  void movsbq(Register dst, Address src);
  void movsbq(Register dst, Register src);

  // Move signed 32bit immediate to 64bit extending sign
  void movslq(Address  dst, int32_t imm64);
  void movslq(Register dst, int32_t imm64);

  void movslq(Register dst, Address src);
  void movslq(Register dst, Register src);
  void movslq(Register dst, void* src); // Dummy declaration to cause NULL to be ambiguous
#endif

  void movswl(Register dst, Address src);
  void movswl(Register dst, Register src);

#ifdef _LP64
  void movswq(Register dst, Address src);
  void movswq(Register dst, Register src);
#endif

  void movw(Address dst, int imm16);
  void movw(Register dst, Address src);
  void movw(Address dst, Register src);

  void movzbl(Register dst, Address src);
  void movzbl(Register dst, Register src);

#ifdef _LP64
  void movzbq(Register dst, Address src);
  void movzbq(Register dst, Register src);
#endif

  void movzwl(Register dst, Address src);
  void movzwl(Register dst, Register src);

#ifdef _LP64
  void movzwq(Register dst, Address src);
  void movzwq(Register dst, Register src);
#endif

  // Unsigned multiply with RAX destination register
  void mull(Address src);
  void mull(Register src);

#ifdef _LP64
  void mulq(Address src);
  void mulq(Register src);
  void mulxq(Register dst1, Register dst2, Register src);
#endif

  // Multiply Scalar Double-Precision Floating-Point Values
  void mulsd(XMMRegister dst, Address src);
  void mulsd(XMMRegister dst, XMMRegister src);

  // Multiply Scalar Single-Precision Floating-Point Values
  void mulss(XMMRegister dst, Address src);
  void mulss(XMMRegister dst, XMMRegister src);

  void negl(Register dst);

#ifdef _LP64
  void negq(Register dst);
#endif

  void nop(int i = 1);

  void notl(Register dst);

#ifdef _LP64
  void notq(Register dst);
#endif

  void orl(Address dst, int32_t imm32);
  void orl(Register dst, int32_t imm32);
  void orl(Register dst, Address src);
  void orl(Register dst, Register src);
  void orl(Address dst, Register src);

  void orq(Address dst, int32_t imm32);
  void orq(Register dst, int32_t imm32);
  void orq(Register dst, Address src);
  void orq(Register dst, Register src);

  // Pack with unsigned saturation
  void packuswb(XMMRegister dst, XMMRegister src);
  void packuswb(XMMRegister dst, Address src);
  void vpackuswb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);

  // Pemutation of 64bit words
  void vpermq(XMMRegister dst, XMMRegister src, int imm8, int vector_len);
  void vpermq(XMMRegister dst, XMMRegister src, int imm8);

  void pause();

  // SSE4.2 string instructions
  void pcmpestri(XMMRegister xmm1, XMMRegister xmm2, int imm8);
  void pcmpestri(XMMRegister xmm1, Address src, int imm8);

  // SSE 4.1 extract
  void pextrd(Register dst, XMMRegister src, int imm8);
  void pextrq(Register dst, XMMRegister src, int imm8);
  // SSE 2 extract
  void pextrw(Register dst, XMMRegister src, int imm8);

  // SSE 4.1 insert
  void pinsrd(XMMRegister dst, Register src, int imm8);
  void pinsrq(XMMRegister dst, Register src, int imm8);
  // SSE 2 insert
  void pinsrw(XMMRegister dst, Register src, int imm8);

  // SSE4.1 packed move
  void pmovzxbw(XMMRegister dst, XMMRegister src);
  void pmovzxbw(XMMRegister dst, Address src);

#ifndef _LP64 // no 32bit push/pop on amd64
  void popl(Address dst);
#endif

#ifdef _LP64
  void popq(Address dst);
#endif

  void popcntl(Register dst, Address src);
  void popcntl(Register dst, Register src);

#ifdef _LP64
  void popcntq(Register dst, Address src);
  void popcntq(Register dst, Register src);
#endif

  // Prefetches (SSE, SSE2, 3DNOW only)

  void prefetchnta(Address src);
  void prefetchr(Address src);
  void prefetcht0(Address src);
  void prefetcht1(Address src);
  void prefetcht2(Address src);
  void prefetchw(Address src);

  // Shuffle Bytes
  void pshufb(XMMRegister dst, XMMRegister src);
  void pshufb(XMMRegister dst, Address src);

  // Shuffle Packed Doublewords
  void pshufd(XMMRegister dst, XMMRegister src, int mode);
  void pshufd(XMMRegister dst, Address src,     int mode);

  // Shuffle Packed Low Words
  void pshuflw(XMMRegister dst, XMMRegister src, int mode);
  void pshuflw(XMMRegister dst, Address src,     int mode);

  // Shift Right by bytes Logical DoubleQuadword Immediate
  void psrldq(XMMRegister dst, int shift);
  // Shift Left by bytes Logical DoubleQuadword Immediate
  void pslldq(XMMRegister dst, int shift);

  // Logical Compare 128bit
  void ptest(XMMRegister dst, XMMRegister src);
  void ptest(XMMRegister dst, Address src);
  // Logical Compare 256bit
  void vptest(XMMRegister dst, XMMRegister src);
  void vptest(XMMRegister dst, Address src);

  // Interleave Low Bytes
  void punpcklbw(XMMRegister dst, XMMRegister src);
  void punpcklbw(XMMRegister dst, Address src);

  // Interleave Low Doublewords
  void punpckldq(XMMRegister dst, XMMRegister src);
  void punpckldq(XMMRegister dst, Address src);

  // Interleave Low Quadwords
  void punpcklqdq(XMMRegister dst, XMMRegister src);

#ifndef _LP64 // no 32bit push/pop on amd64
  void pushl(Address src);
#endif

  void pushq(Address src);

  void rcll(Register dst, int imm8);

  void rclq(Register dst, int imm8);

  void rcrq(Register dst, int imm8);

  void rdtsc();

  void ret(int imm16);

#ifdef _LP64
  void rorq(Register dst, int imm8);
  void rorxq(Register dst, Register src, int imm8);
#endif

  void sahf();

  void sarl(Register dst, int imm8);
  void sarl(Register dst);

  void sarq(Register dst, int imm8);
  void sarq(Register dst);

  void sbbl(Address dst, int32_t imm32);
  void sbbl(Register dst, int32_t imm32);
  void sbbl(Register dst, Address src);
  void sbbl(Register dst, Register src);

  void sbbq(Address dst, int32_t imm32);
  void sbbq(Register dst, int32_t imm32);
  void sbbq(Register dst, Address src);
  void sbbq(Register dst, Register src);

  void setb(Condition cc, Register dst);

  void shldl(Register dst, Register src);
  void shldl(Register dst, Register src, int8_t imm8);

  void shll(Register dst, int imm8);
  void shll(Register dst);

  void shlq(Register dst, int imm8);
  void shlq(Register dst);

  void shrdl(Register dst, Register src);

  void shrl(Register dst, int imm8);
  void shrl(Register dst);

  void shrq(Register dst, int imm8);
  void shrq(Register dst);

  void smovl(); // QQQ generic?

  // Compute Square Root of Scalar Double-Precision Floating-Point Value
  void sqrtsd(XMMRegister dst, Address src);
  void sqrtsd(XMMRegister dst, XMMRegister src);

  // Compute Square Root of Scalar Single-Precision Floating-Point Value
  void sqrtss(XMMRegister dst, Address src);
  void sqrtss(XMMRegister dst, XMMRegister src);

  void std();

  void stmxcsr( Address dst );

  void subl(Address dst, int32_t imm32);
  void subl(Address dst, Register src);
  void subl(Register dst, int32_t imm32);
  void subl(Register dst, Address src);
  void subl(Register dst, Register src);

  void subq(Address dst, int32_t imm32);
  void subq(Address dst, Register src);
  void subq(Register dst, int32_t imm32);
  void subq(Register dst, Address src);
  void subq(Register dst, Register src);

  // Force generation of a 4 byte immediate value even if it fits into 8bit
  void subl_imm32(Register dst, int32_t imm32);
  void subq_imm32(Register dst, int32_t imm32);

  // Subtract Scalar Double-Precision Floating-Point Values
  void subsd(XMMRegister dst, Address src);
  void subsd(XMMRegister dst, XMMRegister src);

  // Subtract Scalar Single-Precision Floating-Point Values
  void subss(XMMRegister dst, Address src);
  void subss(XMMRegister dst, XMMRegister src);

  void testb(Register dst, int imm8);

  void testl(Register dst, int32_t imm32);
  void testl(Register dst, Register src);
  void testl(Register dst, Address src);

  void testq(Register dst, int32_t imm32);
  void testq(Register dst, Register src);

  // BMI - count trailing zeros
  void tzcntl(Register dst, Register src);
  void tzcntq(Register dst, Register src);

  // Unordered Compare Scalar Double-Precision Floating-Point Values and set EFLAGS
  void ucomisd(XMMRegister dst, Address src);
  void ucomisd(XMMRegister dst, XMMRegister src);

  // Unordered Compare Scalar Single-Precision Floating-Point Values and set EFLAGS
  void ucomiss(XMMRegister dst, Address src);
  void ucomiss(XMMRegister dst, XMMRegister src);

  void xabort(int8_t imm8);

  void xaddl(Address dst, Register src);

  void xaddq(Address dst, Register src);

  void xbegin(Label& abort, relocInfo::relocType rtype = relocInfo::none);

  void xchgl(Register reg, Address adr);
  void xchgl(Register dst, Register src);

  void xchgq(Register reg, Address adr);
  void xchgq(Register dst, Register src);

  void xend();

  // Get Value of Extended Control Register
  void xgetbv();

  void xorl(Register dst, int32_t imm32);
  void xorl(Register dst, Address src);
  void xorl(Register dst, Register src);

  void xorq(Register dst, Address src);
  void xorq(Register dst, Register src);

  void set_byte_if_not_zero(Register dst); // sets reg to 1 if not zero, otherwise 0

  // AVX 3-operands scalar instructions (encoded with VEX prefix)

  void vaddsd(XMMRegister dst, XMMRegister nds, Address src);
  void vaddsd(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vaddss(XMMRegister dst, XMMRegister nds, Address src);
  void vaddss(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vdivsd(XMMRegister dst, XMMRegister nds, Address src);
  void vdivsd(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vdivss(XMMRegister dst, XMMRegister nds, Address src);
  void vdivss(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vmulsd(XMMRegister dst, XMMRegister nds, Address src);
  void vmulsd(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vmulss(XMMRegister dst, XMMRegister nds, Address src);
  void vmulss(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vsubsd(XMMRegister dst, XMMRegister nds, Address src);
  void vsubsd(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vsubss(XMMRegister dst, XMMRegister nds, Address src);
  void vsubss(XMMRegister dst, XMMRegister nds, XMMRegister src);


  //====================VECTOR ARITHMETIC=====================================

  // Add Packed Floating-Point Values
  void addpd(XMMRegister dst, XMMRegister src);
  void addps(XMMRegister dst, XMMRegister src);
  void vaddpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vaddps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vaddpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vaddps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Subtract Packed Floating-Point Values
  void subpd(XMMRegister dst, XMMRegister src);
  void subps(XMMRegister dst, XMMRegister src);
  void vsubpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vsubps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vsubpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vsubps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Multiply Packed Floating-Point Values
  void mulpd(XMMRegister dst, XMMRegister src);
  void mulpd(XMMRegister dst, Address src);
  void mulps(XMMRegister dst, XMMRegister src);
  void vmulpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vmulps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vmulpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vmulps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Divide Packed Floating-Point Values
  void divpd(XMMRegister dst, XMMRegister src);
  void divps(XMMRegister dst, XMMRegister src);
  void vdivpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vdivps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vdivpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vdivps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Sqrt Packed Floating-Point Values - Double precision only
  void vsqrtpd(XMMRegister dst, XMMRegister src, int vector_len);
  void vsqrtpd(XMMRegister dst, Address src, int vector_len);

  // Bitwise Logical AND of Packed Floating-Point Values
  void andpd(XMMRegister dst, XMMRegister src);
  void andps(XMMRegister dst, XMMRegister src);
  void vandpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vandps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vandpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vandps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  void unpckhpd(XMMRegister dst, XMMRegister src);
  void unpcklpd(XMMRegister dst, XMMRegister src);

  // Bitwise Logical XOR of Packed Floating-Point Values
  void xorpd(XMMRegister dst, XMMRegister src);
  void xorps(XMMRegister dst, XMMRegister src);
  void vxorpd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vxorps(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vxorpd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vxorps(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Add horizontal packed integers
  void vphaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vphaddd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void phaddw(XMMRegister dst, XMMRegister src);
  void phaddd(XMMRegister dst, XMMRegister src);

  // Add packed integers
  void paddb(XMMRegister dst, XMMRegister src);
  void paddw(XMMRegister dst, XMMRegister src);
  void paddd(XMMRegister dst, XMMRegister src);
  void paddq(XMMRegister dst, XMMRegister src);
  void vpaddb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpaddb(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpaddw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpaddd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpaddq(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Sub packed integers
  void psubb(XMMRegister dst, XMMRegister src);
  void psubw(XMMRegister dst, XMMRegister src);
  void psubd(XMMRegister dst, XMMRegister src);
  void psubq(XMMRegister dst, XMMRegister src);
  void vpsubb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubd(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpsubb(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpsubw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpsubd(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpsubq(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Multiply packed integers (only shorts and ints)
  void pmullw(XMMRegister dst, XMMRegister src);
  void pmulld(XMMRegister dst, XMMRegister src);
  void vpmullw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpmulld(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpmullq(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpmullw(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpmulld(XMMRegister dst, XMMRegister nds, Address src, int vector_len);
  void vpmullq(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Shift left packed integers
  void psllw(XMMRegister dst, int shift);
  void pslld(XMMRegister dst, int shift);
  void psllq(XMMRegister dst, int shift);
  void psllw(XMMRegister dst, XMMRegister shift);
  void pslld(XMMRegister dst, XMMRegister shift);
  void psllq(XMMRegister dst, XMMRegister shift);
  void vpsllw(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpslld(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsllq(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsllw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);
  void vpslld(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);
  void vpsllq(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);

  // Logical shift right packed integers
  void psrlw(XMMRegister dst, int shift);
  void psrld(XMMRegister dst, int shift);
  void psrlq(XMMRegister dst, int shift);
  void psrlw(XMMRegister dst, XMMRegister shift);
  void psrld(XMMRegister dst, XMMRegister shift);
  void psrlq(XMMRegister dst, XMMRegister shift);
  void vpsrlw(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsrld(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsrlq(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsrlw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);
  void vpsrld(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);
  void vpsrlq(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);

  // Arithmetic shift right packed integers (only shorts and ints, no instructions for longs)
  void psraw(XMMRegister dst, int shift);
  void psrad(XMMRegister dst, int shift);
  void psraw(XMMRegister dst, XMMRegister shift);
  void psrad(XMMRegister dst, XMMRegister shift);
  void vpsraw(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsrad(XMMRegister dst, XMMRegister src, int shift, int vector_len);
  void vpsraw(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);
  void vpsrad(XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len);

  // And packed integers
  void pand(XMMRegister dst, XMMRegister src);
  void vpand(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpand(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Andn packed integers
  void pandn(XMMRegister dst, XMMRegister src);

  // Or packed integers
  void por(XMMRegister dst, XMMRegister src);
  void vpor(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpor(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Xor packed integers
  void pxor(XMMRegister dst, XMMRegister src);
  void vpxor(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len);
  void vpxor(XMMRegister dst, XMMRegister nds, Address src, int vector_len);

  // Copy low 128bit into high 128bit of YMM registers.
  void vinsertf128h(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vinserti128h(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vextractf128h(XMMRegister dst, XMMRegister src);
  void vextracti128h(XMMRegister dst, XMMRegister src);

  // Load/store high 128bit of YMM registers which does not destroy other half.
  void vinsertf128h(XMMRegister dst, Address src);
  void vinserti128h(XMMRegister dst, Address src);
  void vextractf128h(Address dst, XMMRegister src);
  void vextracti128h(Address dst, XMMRegister src);

  // Copy low 256bit into high 256bit of ZMM registers.
  void vinserti64x4h(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vinsertf64x4h(XMMRegister dst, XMMRegister nds, XMMRegister src);
  void vextracti64x4h(XMMRegister dst, XMMRegister src);
  void vextractf64x4h(XMMRegister dst, XMMRegister src);
  void vextractf64x4h(Address dst, XMMRegister src);
  void vinsertf64x4h(XMMRegister dst, Address src);

  // Copy targeted 128bit segments of the ZMM registers
  void vextracti64x2h(XMMRegister dst, XMMRegister src, int value);
  void vextractf64x2h(XMMRegister dst, XMMRegister src, int value);
  void vextractf32x4h(XMMRegister dst, XMMRegister src, int value);
  void vextractf32x4h(Address dst, XMMRegister src, int value);
  void vinsertf32x4h(XMMRegister dst, XMMRegister nds, XMMRegister src, int value);
  void vinsertf32x4h(XMMRegister dst, Address src, int value);

  // duplicate 4-bytes integer data from src into 8 locations in dest
  void vpbroadcastd(XMMRegister dst, XMMRegister src);

  // duplicate n-bytes integer data from src into vector_len locations in dest
  void evpbroadcastb(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastb(XMMRegister dst, Address src, int vector_len);
  void evpbroadcastw(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastw(XMMRegister dst, Address src, int vector_len);
  void evpbroadcastd(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastd(XMMRegister dst, Address src, int vector_len);
  void evpbroadcastq(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastq(XMMRegister dst, Address src, int vector_len);

  void evpbroadcastss(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastss(XMMRegister dst, Address src, int vector_len);
  void evpbroadcastsd(XMMRegister dst, XMMRegister src, int vector_len);
  void evpbroadcastsd(XMMRegister dst, Address src, int vector_len);

  void evpbroadcastb(XMMRegister dst, Register src, int vector_len);
  void evpbroadcastw(XMMRegister dst, Register src, int vector_len);
  void evpbroadcastd(XMMRegister dst, Register src, int vector_len);
  void evpbroadcastq(XMMRegister dst, Register src, int vector_len);

  // Carry-Less Multiplication Quadword
  void pclmulqdq(XMMRegister dst, XMMRegister src, int mask);
  void vpclmulqdq(XMMRegister dst, XMMRegister nds, XMMRegister src, int mask);

  // AVX instruction which is used to clear upper 128 bits of YMM registers and
  // to avoid transaction penalty between AVX and SSE states. There is no
  // penalty if legacy SSE instructions are encoded using VEX prefix because
  // they always clear upper 128 bits. It should be used before calling
  // runtime code and native libraries.
  void vzeroupper();

 protected:
  // Next instructions require address alignment 16 bytes SSE mode.
  // They should be called only from corresponding MacroAssembler instructions.
  void andpd(XMMRegister dst, Address src);
  void andps(XMMRegister dst, Address src);
  void xorpd(XMMRegister dst, Address src);
  void xorps(XMMRegister dst, Address src);

};

#endif // CPU_X86_VM_ASSEMBLER_X86_HPP
