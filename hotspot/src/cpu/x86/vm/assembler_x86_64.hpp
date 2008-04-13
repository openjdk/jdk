/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class BiasedLockingCounters;

// Contains all the definitions needed for amd64 assembly code generation.

#ifdef _LP64
// Calling convention
class Argument VALUE_OBJ_CLASS_SPEC {
 public:
  enum {
#ifdef _WIN64
    n_int_register_parameters_c   = 4, // rcx, rdx, r8, r9 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 4,  // xmm0 - xmm3 (c_farg0, c_farg1, ... )
#else
    n_int_register_parameters_c   = 6, // rdi, rsi, rdx, rcx, r8, r9 (c_rarg0, c_rarg1, ...)
    n_float_register_parameters_c = 8,  // xmm0 - xmm7 (c_farg0, c_farg1, ... )
#endif  // _WIN64
    n_int_register_parameters_j   = 6, // j_rarg0, j_rarg1, ...
    n_float_register_parameters_j = 8  // j_farg0, j_farg1, ...
  };
};


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

#endif  // _WIN64

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
#endif // _WIN64
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
REGISTER_DECLARATION(Register, r15_thread, r15);   // callee-saved

#endif // _LP64

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
    times_8  =  3
  };

 private:
  Register         _base;
  Register         _index;
  ScaleFactor      _scale;
  int              _disp;
  RelocationHolder _rspec;

  // Easily misused constructors make them private
  Address(int disp, address loc, relocInfo::relocType rtype);
  Address(int disp, address loc, RelocationHolder spec);

 public:
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
#endif // ASSERT

  // accessors
  bool uses(Register reg) const {
    return _base == reg || _index == reg;
  }

  // Convert the raw encoding form into the form expected by the constructor for
  // Address.  An index of 4 (rsp) corresponds to having no index, so convert
  // that to noreg for the Address constructor.
  static Address make_raw(int base, int index, int scale, int disp);

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

class OopAddress: public AddressLiteral {

  public:

  OopAddress(address target) : AddressLiteral(target, relocInfo::oop_type){}

};

class ExternalAddress: public AddressLiteral {

  public:

  ExternalAddress(address target) : AddressLiteral(target, relocInfo::external_word_type){}

};

class InternalAddress: public AddressLiteral {

  public:

  InternalAddress(address target) : AddressLiteral(target, relocInfo::internal_word_type) {}

};

// x86 can do array addressing as a single operation since disp can be an absolute
// address but amd64 can't [e.g. array_base(rx, ry:width) ]. We create a class
// that expresses the concept but does extra magic on amd64 to get the final result

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

// The amd64 Assembler: Pure assembler doing NO optimizations on
// the instruction level (e.g. mov rax, 0 is not translated into xor
// rax, rax!); i.e., what you write is what you get. The Assembler is
// generating code into a CodeBuffer.

const int FPUStateSizeInWords = 512 / wordSize;

class Assembler : public AbstractAssembler  {
  friend class AbstractAssembler; // for the non-virtual hack
  friend class StubGenerator;


 protected:
#ifdef ASSERT
  void check_relocation(RelocationHolder const& rspec, int format);
#endif

  inline void emit_long64(jlong x);

  void emit_data(jint data, relocInfo::relocType rtype, int format /* = 1 */);
  void emit_data(jint data, RelocationHolder const& rspec, int format /* = 1 */);
  void emit_data64(jlong data, relocInfo::relocType rtype, int format = 0);
  void emit_data64(jlong data, RelocationHolder const& rspec, int format = 0);

  // Helper functions for groups of instructions
  void emit_arith_b(int op1, int op2, Register dst, int imm8);

  void emit_arith(int op1, int op2, Register dst, int imm32);
  // only x86??
  void emit_arith(int op1, int op2, Register dst, jobject obj);
  void emit_arith(int op1, int op2, Register dst, Register src);

  void emit_operand(Register reg,
                    Register base, Register index, Address::ScaleFactor scale,
                    int disp,
                    RelocationHolder const& rspec,
                    int rip_relative_correction = 0);
  void emit_operand(Register reg, Address adr,
                    int rip_relative_correction = 0);
  void emit_operand(XMMRegister reg,
                    Register base, Register index, Address::ScaleFactor scale,
                    int disp,
                    RelocationHolder const& rspec,
                    int rip_relative_correction = 0);
  void emit_operand(XMMRegister reg, Address adr,
                    int rip_relative_correction = 0);

  // Immediate-to-memory forms
  void emit_arith_operand(int op1, Register rm, Address adr, int imm32);

  void emit_farith(int b1, int b2, int i);

  bool reachable(AddressLiteral adr);

  // These are all easily abused and hence protected

  // Make these disappear in 64bit mode since they would never be correct
#ifndef _LP64
  void cmp_literal32(Register src1, int32_t imm32, RelocationHolder const& rspec);
  void cmp_literal32(Address src1, int32_t imm32, RelocationHolder const& rspec);

  void mov_literal32(Register dst, int32_t imm32, RelocationHolder const& rspec);
  void mov_literal32(Address dst, int32_t imm32, RelocationHolder const& rspec);

  void push_literal32(int32_t imm32, RelocationHolder const& rspec);
#endif // _LP64


  void mov_literal64(Register dst, intptr_t imm64, RelocationHolder const& rspec);

  // These are unique in that we are ensured by the caller that the 32bit
  // relative in these instructions will always be able to reach the potentially
  // 64bit address described by entry. Since they can take a 64bit address they
  // don't have the 32 suffix like the other instructions in this class.
  void jmp_literal(address entry, RelocationHolder const& rspec);
  void call_literal(address entry, RelocationHolder const& rspec);

 public:
  enum Condition { // The amd64 condition codes used for conditional jumps/moves.
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
    // XXX remove segment prefixes
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
    REX_WRXB   = 0x4F
  };

  enum WhichOperand {
    // input to locate_operand, and format code for relocations
    imm64_operand  = 0,          // embedded 64-bit immediate operand
    disp32_operand = 1,          // embedded 32-bit displacement
    call32_operand = 2,          // embedded 32-bit self-relative displacement
    _WhichOperand_limit = 3
  };

  public:

  // Creation
  Assembler(CodeBuffer* code)
    : AbstractAssembler(code) {
  }

  // Decoding
  static address locate_operand(address inst, WhichOperand which);
  static address locate_next_instruction(address inst);

  // Utilities

 static bool is_simm(int64_t x, int nbits) { return -( CONST64(1) << (nbits-1) )  <= x   &&   x  <  ( CONST64(1) << (nbits-1) ); }
 static bool is_simm32 (int64_t x) { return x == (int64_t)(int32_t)x; }


  // Stack
  void pushaq();
  void popaq();

  void pushfq();
  void popfq();

  void pushq(int imm32);

  void pushq(Register src);
  void pushq(Address src);

  void popq(Register dst);
  void popq(Address dst);

  // Instruction prefixes
  void prefix(Prefix p);

  int prefix_and_encode(int reg_enc, bool byteinst = false);
  int prefixq_and_encode(int reg_enc);

  int prefix_and_encode(int dst_enc, int src_enc, bool byteinst = false);
  int prefixq_and_encode(int dst_enc, int src_enc);

  void prefix(Register reg);
  void prefix(Address adr);
  void prefixq(Address adr);

  void prefix(Address adr, Register reg,  bool byteinst = false);
  void prefixq(Address adr, Register reg);

  void prefix(Address adr, XMMRegister reg);

  // Moves
  void movb(Register dst, Address src);
  void movb(Address dst, int imm8);
  void movb(Address dst, Register src);

  void movw(Address dst, int imm16);
  void movw(Register dst, Address src);
  void movw(Address dst, Register src);

  void movl(Register dst, int imm32);
  void movl(Register dst, Register src);
  void movl(Register dst, Address src);
  void movl(Address dst, int imm32);
  void movl(Address dst, Register src);

  void movq(Register dst, Register src);
  void movq(Register dst, Address src);
  void movq(Address dst, Register src);
  // These prevent using movq from converting a zero (like NULL) into Register
  // by giving the compiler two choices it can't resolve
  void movq(Address dst, void* dummy);
  void movq(Register dst, void* dummy);

  void mov64(Register dst, intptr_t imm64);
  void mov64(Address dst, intptr_t imm64);

  void movsbl(Register dst, Address src);
  void movsbl(Register dst, Register src);
  void movswl(Register dst, Address src);
  void movswl(Register dst, Register src);
  void movslq(Register dst, Address src);
  void movslq(Register dst, Register src);

  void movzbl(Register dst, Address src);
  void movzbl(Register dst, Register src);
  void movzwl(Register dst, Address src);
  void movzwl(Register dst, Register src);

 protected: // Avoid using the next instructions directly.
  // New cpus require use of movsd and movss to avoid partial register stall
  // when loading from memory. But for old Opteron use movlpd instead of movsd.
  // The selection is done in MacroAssembler::movdbl() and movflt().
  void movss(XMMRegister dst, XMMRegister src);
  void movss(XMMRegister dst, Address src);
  void movss(Address dst, XMMRegister src);
  void movsd(XMMRegister dst, XMMRegister src);
  void movsd(Address dst, XMMRegister src);
  void movsd(XMMRegister dst, Address src);
  void movlpd(XMMRegister dst, Address src);
  // New cpus require use of movaps and movapd to avoid partial register stall
  // when moving between registers.
  void movapd(XMMRegister dst, XMMRegister src);
  void movaps(XMMRegister dst, XMMRegister src);
 public:

  void movdl(XMMRegister dst, Register src);
  void movdl(Register dst, XMMRegister src);
  void movdq(XMMRegister dst, Register src);
  void movdq(Register dst, XMMRegister src);

  void cmovl(Condition cc, Register dst, Register src);
  void cmovl(Condition cc, Register dst, Address src);
  void cmovq(Condition cc, Register dst, Register src);
  void cmovq(Condition cc, Register dst, Address src);

  // Prefetches
 private:
  void prefetch_prefix(Address src);
 public:
  void prefetcht0(Address src);
  void prefetcht1(Address src);
  void prefetcht2(Address src);
  void prefetchnta(Address src);
  void prefetchw(Address src);

  // Arithmetics
  void adcl(Register dst, int imm32);
  void adcl(Register dst, Address src);
  void adcl(Register dst, Register src);
  void adcq(Register dst, int imm32);
  void adcq(Register dst, Address src);
  void adcq(Register dst, Register src);

  void addl(Address dst, int imm32);
  void addl(Address dst, Register src);
  void addl(Register dst, int imm32);
  void addl(Register dst, Address src);
  void addl(Register dst, Register src);
  void addq(Address dst, int imm32);
  void addq(Address dst, Register src);
  void addq(Register dst, int imm32);
  void addq(Register dst, Address src);
  void addq(Register dst, Register src);

  void andl(Register dst, int imm32);
  void andl(Register dst, Address src);
  void andl(Register dst, Register src);
  void andq(Register dst, int imm32);
  void andq(Register dst, Address src);
  void andq(Register dst, Register src);

  void cmpb(Address dst, int imm8);
  void cmpl(Address dst, int imm32);
  void cmpl(Register dst, int imm32);
  void cmpl(Register dst, Register src);
  void cmpl(Register dst, Address src);
  void cmpq(Address dst, int imm32);
  void cmpq(Address dst, Register src);
  void cmpq(Register dst, int imm32);
  void cmpq(Register dst, Register src);
  void cmpq(Register dst, Address src);

  void ucomiss(XMMRegister dst, XMMRegister src);
  void ucomisd(XMMRegister dst, XMMRegister src);

 protected:
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

 public:
  void idivl(Register src);
  void idivq(Register src);
  void cdql();
  void cdqq();

  void imull(Register dst, Register src);
  void imull(Register dst, Register src, int value);
  void imulq(Register dst, Register src);
  void imulq(Register dst, Register src, int value);

  void leal(Register dst, Address src);
  void leaq(Register dst, Address src);

  void mull(Address src);
  void mull(Register src);

  void negl(Register dst);
  void negq(Register dst);

  void notl(Register dst);
  void notq(Register dst);

  void orl(Address dst, int imm32);
  void orl(Register dst, int imm32);
  void orl(Register dst, Address src);
  void orl(Register dst, Register src);
  void orq(Address dst, int imm32);
  void orq(Register dst, int imm32);
  void orq(Register dst, Address src);
  void orq(Register dst, Register src);

  void rcll(Register dst, int imm8);
  void rclq(Register dst, int imm8);

  void sarl(Register dst, int imm8);
  void sarl(Register dst);
  void sarq(Register dst, int imm8);
  void sarq(Register dst);

  void sbbl(Address dst, int imm32);
  void sbbl(Register dst, int imm32);
  void sbbl(Register dst, Address src);
  void sbbl(Register dst, Register src);
  void sbbq(Address dst, int imm32);
  void sbbq(Register dst, int imm32);
  void sbbq(Register dst, Address src);
  void sbbq(Register dst, Register src);

  void shll(Register dst, int imm8);
  void shll(Register dst);
  void shlq(Register dst, int imm8);
  void shlq(Register dst);

  void shrl(Register dst, int imm8);
  void shrl(Register dst);
  void shrq(Register dst, int imm8);
  void shrq(Register dst);

  void subl(Address dst, int imm32);
  void subl(Address dst, Register src);
  void subl(Register dst, int imm32);
  void subl(Register dst, Address src);
  void subl(Register dst, Register src);
  void subq(Address dst, int imm32);
  void subq(Address dst, Register src);
  void subq(Register dst, int imm32);
  void subq(Register dst, Address src);
  void subq(Register dst, Register src);

  void testb(Register dst, int imm8);
  void testl(Register dst, int imm32);
  void testl(Register dst, Register src);
  void testq(Register dst, int imm32);
  void testq(Register dst, Register src);

  void xaddl(Address dst, Register src);
  void xaddq(Address dst, Register src);

  void xorl(Register dst, int imm32);
  void xorl(Register dst, Address src);
  void xorl(Register dst, Register src);
  void xorq(Register dst, int imm32);
  void xorq(Register dst, Address src);
  void xorq(Register dst, Register src);

  // Miscellaneous
  void bswapl(Register reg);
  void bswapq(Register reg);
  void lock();

  void xchgl(Register reg, Address adr);
  void xchgl(Register dst, Register src);
  void xchgq(Register reg, Address adr);
  void xchgq(Register dst, Register src);

  void cmpxchgl(Register reg, Address adr);
  void cmpxchgq(Register reg, Address adr);

  void nop(int i = 1);
  void addr_nop_4();
  void addr_nop_5();
  void addr_nop_7();
  void addr_nop_8();

  void hlt();
  void ret(int imm16);
  void smovl();
  void rep_movl();
  void rep_movq();
  void rep_set();
  void repne_scanl();
  void repne_scanq();
  void setb(Condition cc, Register dst);

  void clflush(Address adr);

  enum Membar_mask_bits {
    StoreStore = 1 << 3,
    LoadStore  = 1 << 2,
    StoreLoad  = 1 << 1,
    LoadLoad   = 1 << 0
  };

  // Serializes memory.
  void membar(Membar_mask_bits order_constraint) {
    // We only have to handle StoreLoad and LoadLoad
    if (order_constraint & StoreLoad) {
      // MFENCE subsumes LFENCE
      mfence();
    } /* [jk] not needed currently: else if (order_constraint & LoadLoad) {
         lfence();
    } */
  }

  void lfence() {
    emit_byte(0x0F);
    emit_byte(0xAE);
    emit_byte(0xE8);
  }

  void mfence() {
    emit_byte(0x0F);
    emit_byte(0xAE);
    emit_byte(0xF0);
  }

  // Identify processor type and features
  void cpuid() {
    emit_byte(0x0F);
    emit_byte(0xA2);
  }

  void cld() { emit_byte(0xfc);
  }

  void std() { emit_byte(0xfd);
  }


  // Calls

  void call(Label& L, relocInfo::relocType rtype);
  void call(Register reg);
  void call(Address adr);

  // Jumps

  void jmp(Register reg);
  void jmp(Address adr);

  // Label operations & relative jumps (PPUM Appendix D)
  // unconditional jump to L
  void jmp(Label& L, relocInfo::relocType rtype = relocInfo::none);


  // Unconditional 8-bit offset jump to L.
  // WARNING: be very careful using this for forward jumps.  If the label is
  // not bound within an 8-bit offset of this instruction, a run-time error
  // will occur.
  void jmpb(Label& L);

  // jcc is the generic conditional branch generator to run- time
  // routines, jcc is used for branches to labels. jcc takes a branch
  // opcode (cc) and a label (L) and generates either a backward
  // branch or a forward branch and links it to the label fixup
  // chain. Usage:
  //
  // Label L;      // unbound label
  // jcc(cc, L);   // forward branch to unbound label
  // bind(L);      // bind label to the current pc
  // jcc(cc, L);   // backward branch to bound label
  // bind(L);      // illegal: a label may be bound only once
  //
  // Note: The same Label can be used for forward and backward branches
  // but it may be bound only once.

  void jcc(Condition cc, Label& L,
           relocInfo::relocType rtype = relocInfo::none);

  // Conditional jump to a 8-bit offset to L.
  // WARNING: be very careful using this for forward jumps.  If the label is
  // not bound within an 8-bit offset of this instruction, a run-time error
  // will occur.
  void jccb(Condition cc, Label& L);

  // Floating-point operations

  void fxsave(Address dst);
  void fxrstor(Address src);
  void ldmxcsr(Address src);
  void stmxcsr(Address dst);

  void addss(XMMRegister dst, XMMRegister src);
  void addss(XMMRegister dst, Address src);
  void subss(XMMRegister dst, XMMRegister src);
  void subss(XMMRegister dst, Address src);
  void mulss(XMMRegister dst, XMMRegister src);
  void mulss(XMMRegister dst, Address src);
  void divss(XMMRegister dst, XMMRegister src);
  void divss(XMMRegister dst, Address src);
  void addsd(XMMRegister dst, XMMRegister src);
  void addsd(XMMRegister dst, Address src);
  void subsd(XMMRegister dst, XMMRegister src);
  void subsd(XMMRegister dst, Address src);
  void mulsd(XMMRegister dst, XMMRegister src);
  void mulsd(XMMRegister dst, Address src);
  void divsd(XMMRegister dst, XMMRegister src);
  void divsd(XMMRegister dst, Address src);

  // We only need the double form
  void sqrtsd(XMMRegister dst, XMMRegister src);
  void sqrtsd(XMMRegister dst, Address src);

  void xorps(XMMRegister dst, XMMRegister src);
  void xorps(XMMRegister dst, Address src);
  void xorpd(XMMRegister dst, XMMRegister src);
  void xorpd(XMMRegister dst, Address src);

  void cvtsi2ssl(XMMRegister dst, Register src);
  void cvtsi2ssq(XMMRegister dst, Register src);
  void cvtsi2sdl(XMMRegister dst, Register src);
  void cvtsi2sdq(XMMRegister dst, Register src);
  void cvttss2sil(Register dst, XMMRegister src); // truncates
  void cvttss2siq(Register dst, XMMRegister src); // truncates
  void cvttsd2sil(Register dst, XMMRegister src); // truncates
  void cvttsd2siq(Register dst, XMMRegister src); // truncates
  void cvtss2sd(XMMRegister dst, XMMRegister src);
  void cvtsd2ss(XMMRegister dst, XMMRegister src);
  void cvtdq2pd(XMMRegister dst, XMMRegister src);
  void cvtdq2ps(XMMRegister dst, XMMRegister src);

  void pxor(XMMRegister dst, Address src);       // Xor Packed Byte Integer Values
  void pxor(XMMRegister dst, XMMRegister src);   // Xor Packed Byte Integer Values

  void movdqa(XMMRegister dst, Address src);     // Move Aligned Double Quadword
  void movdqa(XMMRegister dst, XMMRegister src);
  void movdqa(Address     dst, XMMRegister src);

  void movq(XMMRegister dst, Address src);
  void movq(Address dst, XMMRegister src);

  void pshufd(XMMRegister dst, XMMRegister src, int mode); // Shuffle Packed Doublewords
  void pshufd(XMMRegister dst, Address src,     int mode);
  void pshuflw(XMMRegister dst, XMMRegister src, int mode); // Shuffle Packed Low Words
  void pshuflw(XMMRegister dst, Address src,     int mode);

  void psrlq(XMMRegister dst, int shift); // Shift Right Logical Quadword Immediate

  void punpcklbw(XMMRegister dst, XMMRegister src); // Interleave Low Bytes
  void punpcklbw(XMMRegister dst, Address src);
};


// MacroAssembler extends Assembler by frequently used macros.
//
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

class MacroAssembler : public Assembler {
 friend class LIR_Assembler;
 protected:

  Address as_Address(AddressLiteral adr);
  Address as_Address(ArrayAddress adr);

  // Support for VM calls
  //
  // This is the base routine called by the different versions of
  // call_VM_leaf. The interpreter may customize this version by
  // overriding it for its purposes (e.g., to save/restore additional
  // registers when doing a VM call).

  virtual void call_VM_leaf_base(
    address entry_point,               // the entry point
    int     number_of_arguments        // the number of arguments to
                                       // pop after the call
  );

  // This is the base routine called by the different versions of
  // call_VM. The interpreter may customize this version by overriding
  // it for its purposes (e.g., to save/restore additional registers
  // when doing a VM call).
  //
  // If no java_thread register is specified (noreg) than rdi will be
  // used instead. call_VM_base returns the register which contains
  // the thread upon return. If a thread register has been specified,
  // the return value will correspond to that register. If no
  // last_java_sp is specified (noreg) than rsp will be used instead.
  virtual void call_VM_base(           // returns the register
                                       // containing the thread upon
                                       // return
    Register oop_result,               // where an oop-result ends up
                                       // if any; use noreg otherwise
    Register java_thread,              // the thread if computed
                                       // before ; use noreg otherwise
    Register last_java_sp,             // to set up last_Java_frame in
                                       // stubs; use noreg otherwise
    address  entry_point,              // the entry point
    int      number_of_arguments,      // the number of arguments (w/o
                                       // thread) to pop after the
                                       // call
    bool     check_exceptions          // whether to check for pending
                                       // exceptions after return
  );

  // This routines should emit JVMTI PopFrame handling and ForceEarlyReturn code.
  // The implementation is only non-empty for the InterpreterMacroAssembler,
  // as only the interpreter handles PopFrame and ForceEarlyReturn requests.
  virtual void check_and_handle_popframe(Register java_thread);
  virtual void check_and_handle_earlyret(Register java_thread);

  void call_VM_helper(Register oop_result,
                      address entry_point,
                      int number_of_arguments,
                      bool check_exceptions = true);

 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

  // Support for NULL-checks
  //
  // Generates code that causes a NULL OS exception if the content of
  // reg is NULL.  If the accessed location is M[reg + offset] and the
  // offset is known, provide the offset. No explicit code generation
  // is needed if the offset is within a certain range (0 <= offset <=
  // page_size).
  void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(int offset);

  // Required platform-specific helpers for Label::patch_instructions.
  // They _shadow_ the declarations in AbstractAssembler, which are undefined.
  void pd_patch_instruction(address branch, address target);
#ifndef PRODUCT
  static void pd_print_patched_instruction(address branch);
#endif


  // The following 4 methods return the offset of the appropriate move
  // instruction.  Note: these are 32 bit instructions

  // Support for fast byte/word loading with zero extension (depending
  // on particular CPU)
  int load_unsigned_byte(Register dst, Address src);
  int load_unsigned_word(Register dst, Address src);

  // Support for fast byte/word loading with sign extension (depending
  // on particular CPU)
  int load_signed_byte(Register dst, Address src);
  int load_signed_word(Register dst, Address src);

  // Support for inc/dec with optimal instruction selection depending
  // on value
  void incrementl(Register reg, int value = 1);
  void decrementl(Register reg, int value = 1);
  void incrementq(Register reg, int value = 1);
  void decrementq(Register reg, int value = 1);

  void incrementl(Address dst, int value = 1);
  void decrementl(Address dst, int value = 1);
  void incrementq(Address dst, int value = 1);
  void decrementq(Address dst, int value = 1);

  // Support optimal SSE move instructions.
  void movflt(XMMRegister dst, XMMRegister src) {
    if (UseXmmRegToRegMoveAll) { movaps(dst, src); return; }
    else                       { movss (dst, src); return; }
  }

  void movflt(XMMRegister dst, Address src) { movss(dst, src); }

  void movflt(XMMRegister dst, AddressLiteral src);

  void movflt(Address dst, XMMRegister src) { movss(dst, src); }

  void movdbl(XMMRegister dst, XMMRegister src) {
    if (UseXmmRegToRegMoveAll) { movapd(dst, src); return; }
    else                       { movsd (dst, src); return; }
  }

  void movdbl(XMMRegister dst, AddressLiteral src);

  void movdbl(XMMRegister dst, Address src) {
    if (UseXmmLoadAndClearUpper) { movsd (dst, src); return; }
    else                         { movlpd(dst, src); return; }
  }

  void movdbl(Address dst, XMMRegister src) { movsd(dst, src); }

  void incrementl(AddressLiteral dst);
  void incrementl(ArrayAddress dst);

  // Alignment
  void align(int modulus);

  // Misc
  void fat_nop(); // 5 byte nop


  // C++ bool manipulation

  void movbool(Register dst, Address src);
  void movbool(Address dst, bool boolconst);
  void movbool(Address dst, Register src);
  void testbool(Register dst);

  // oop manipulations
  void load_klass(Register dst, Register src);
  void store_klass(Register dst, Register src);

  void load_heap_oop(Register dst, Address src);
  void store_heap_oop(Address dst, Register src);
  void encode_heap_oop(Register r);
  void decode_heap_oop(Register r);
  void encode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register r);

  // Stack frame creation/removal
  void enter();
  void leave();

  // Support for getting the JavaThread pointer (i.e.; a reference to
  // thread-local information) The pointer will be loaded into the
  // thread register.
  void get_thread(Register thread);

  void int3();

  // Support for VM calls
  //
  // It is imperative that all calls into the VM are handled via the
  // call_VM macros.  They make sure that the stack linkage is setup
  // correctly. call_VM's correspond to ENTRY/ENTRY_X entry points
  // while call_VM_leaf's correspond to LEAF entry points.
  void call_VM(Register oop_result,
               address entry_point,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1, Register arg_2,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               address entry_point,
               Register arg_1, Register arg_2, Register arg_3,
               bool check_exceptions = true);

  // Overloadings with last_Java_sp
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               int number_of_arguments = 0,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, bool
               check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, Register arg_2,
               bool check_exceptions = true);
  void call_VM(Register oop_result,
               Register last_java_sp,
               address entry_point,
               Register arg_1, Register arg_2, Register arg_3,
               bool check_exceptions = true);

  void call_VM_leaf(address entry_point,
                    int number_of_arguments = 0);
  void call_VM_leaf(address entry_point,
                    Register arg_1);
  void call_VM_leaf(address entry_point,
                    Register arg_1, Register arg_2);
  void call_VM_leaf(address entry_point,
                    Register arg_1, Register arg_2, Register arg_3);

  // last Java Frame (fills frame anchor)
  void set_last_Java_frame(Register last_java_sp,
                           Register last_java_fp,
                           address last_java_pc);
  void reset_last_Java_frame(bool clear_fp, bool clear_pc);

  // Stores
  void store_check(Register obj);                // store check for
                                                 // obj - register is
                                                 // destroyed
                                                 // afterwards
  void store_check(Register obj, Address dst);   // same as above, dst
                                                 // is exact store
                                                 // location (reg. is
                                                 // destroyed)

  // split store_check(Register obj) to enhance instruction interleaving
  void store_check_part_1(Register obj);
  void store_check_part_2(Register obj);

  // C 'boolean' to Java boolean: x == 0 ? 0 : 1
  void c2bool(Register x);

  // Int division/reminder for Java
  // (as idivl, but checks for special case as described in JVM spec.)
  // returns idivl instruction offset for implicit exception handling
  int corrected_idivl(Register reg);
  // Long division/reminder for Java
  // (as idivq, but checks for special case as described in JVM spec.)
  // returns idivq instruction offset for implicit exception handling
  int corrected_idivq(Register reg);

  // Push and pop integer/fpu/cpu state
  void push_IU_state();
  void pop_IU_state();

  void push_FPU_state();
  void pop_FPU_state();

  void push_CPU_state();
  void pop_CPU_state();

  // Sign extension
  void sign_extend_short(Register reg);
  void sign_extend_byte(Register reg);

  // Division by power of 2, rounding towards 0
  void division_with_shift(Register reg, int shift_value);

  // Round up to a power of two
  void round_to_l(Register reg, int modulus);
  void round_to_q(Register reg, int modulus);

  // allocation
  void eden_allocate(
    Register obj,               // result: pointer to object after
                                // successful allocation
    Register var_size_in_bytes, // object size in bytes if unknown at
                                // compile time; invalid otherwise
    int con_size_in_bytes,      // object size in bytes if known at
                                // compile time
    Register t1,                // temp register
    Label& slow_case            // continuation point if fast
                                // allocation fails
    );
  void tlab_allocate(
    Register obj,               // result: pointer to object after
                                // successful allocation
    Register var_size_in_bytes, // object size in bytes if unknown at
                                // compile time; invalid otherwise
    int con_size_in_bytes,      // object size in bytes if known at
                                // compile time
    Register t1,                // temp register
    Register t2,                // temp register
    Label& slow_case            // continuation point if fast
                                // allocation fails
  );
  void tlab_refill(Label& retry_tlab, Label& try_eden, Label& slow_case);

  //----

  // Debugging

  // only if +VerifyOops
  void verify_oop(Register reg, const char* s = "broken oop");
  void verify_oop_addr(Address addr, const char * s = "broken oop addr");

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  // only if +VerifyFPU
  void verify_FPU(int stack_depth, const char* s = "illegal FPU state") {}

  // prints msg, dumps registers and stops execution
  void stop(const char* msg);

  // prints message and continues
  void warn(const char* msg);

  static void debug(char* msg, int64_t pc, int64_t regs[]);

  void os_breakpoint();

  void untested()
  {
    stop("untested");
  }

  void unimplemented(const char* what = "")
  {
    char* b = new char[1024];
    sprintf(b, "unimplemented: %s", what);
    stop(b);
  }

  void should_not_reach_here()
  {
    stop("should not reach here");
  }

  // Stack overflow checking
  void bang_stack_with_offset(int offset)
  {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    movl(Address(rsp, (-offset)), rax);
  }

  // Writes to stack successive pages until offset reached to check for
  // stack overflow + shadow pages.  Also, clobbers tmp
  void bang_stack_size(Register offset, Register tmp);

  // Support for serializing memory accesses between threads.
  void serialize_memory(Register thread, Register tmp);

  void verify_tlab();

  // Biased locking support
  // lock_reg and obj_reg must be loaded up with the appropriate values.
  // swap_reg must be rax and is killed.
  // tmp_reg must be supplied and is killed.
  // If swap_reg_contains_mark is true then the code assumes that the
  // mark word of the object has already been loaded into swap_reg.
  // Optional slow case is for implementations (interpreter and C1) which branch to
  // slow case directly. Leaves condition codes set for C2's Fast_Lock node.
  // Returns offset of first potentially-faulting instruction for null
  // check info (currently consumed only by C1). If
  // swap_reg_contains_mark is true then returns -1 as it is assumed
  // the calling code has already passed any potential faults.
  int biased_locking_enter(Register lock_reg, Register obj_reg, Register swap_reg, Register tmp_reg,
                           bool swap_reg_contains_mark,
                           Label& done, Label* slow_case = NULL,
                           BiasedLockingCounters* counters = NULL);
  void biased_locking_exit (Register obj_reg, Register temp_reg, Label& done);

  Condition negate_condition(Condition cond);

  // Instructions that use AddressLiteral operands. These instruction can handle 32bit/64bit
  // operands. In general the names are modified to avoid hiding the instruction in Assembler
  // so that we don't need to implement all the varieties in the Assembler with trivial wrappers
  // here in MacroAssembler. The major exception to this rule is call

  // Arithmetics

  void cmp8(AddressLiteral src1, int8_t imm32);

  void cmp32(AddressLiteral src1, int32_t src2);
  // compare reg - mem, or reg - &mem
  void cmp32(Register src1, AddressLiteral src2);

  void cmp32(Register src1, Address src2);

#ifndef _LP64
  void cmpoop(Address dst, jobject obj);
  void cmpoop(Register dst, jobject obj);
#endif // _LP64

  // NOTE src2 must be the lval. This is NOT an mem-mem compare
  void cmpptr(Address src1, AddressLiteral src2);

  void cmpptr(Register src1, AddressLiteral src);

  // will be cmpreg(?)
  void cmp64(Register src1, AddressLiteral src);

  void cmpxchgptr(Register reg, Address adr);
  void cmpxchgptr(Register reg, AddressLiteral adr);

  // Helper functions for statistics gathering.
  // Conditionally (atomically, on MPs) increments passed counter address, preserving condition codes.
  void cond_inc32(Condition cond, AddressLiteral counter_addr);
  // Unconditional atomic increment.
  void atomic_incl(AddressLiteral counter_addr);


  void lea(Register dst, AddressLiteral src);
  void lea(Register dst, Address src);


  // Calls
  void call(Label& L, relocInfo::relocType rtype);
  void call(Register entry);
  void call(AddressLiteral entry);

  // Jumps

  // 32bit can do a case table jump in one instruction but we no longer allow the base
  // to be installed in the Address class
  void jump(ArrayAddress entry);

  void jump(AddressLiteral entry);
  void jump_cc(Condition cc, AddressLiteral dst);

  // Floating

  void ldmxcsr(Address src) { Assembler::ldmxcsr(src); }
  void ldmxcsr(AddressLiteral src);

private:
  // these are private because users should be doing movflt/movdbl

  void movss(XMMRegister dst, XMMRegister src) { Assembler::movss(dst, src); }
  void movss(Address dst, XMMRegister src)       { Assembler::movss(dst, src); }
  void movss(XMMRegister dst, Address src)       { Assembler::movss(dst, src); }
  void movss(XMMRegister dst, AddressLiteral src);

  void movlpd(XMMRegister dst, Address src)      {Assembler::movlpd(dst, src); }
  void movlpd(XMMRegister dst, AddressLiteral src);

public:


  void xorpd(XMMRegister dst, XMMRegister src) {Assembler::xorpd(dst, src); }
  void xorpd(XMMRegister dst, Address src)       {Assembler::xorpd(dst, src); }
  void xorpd(XMMRegister dst, AddressLiteral src);

  void xorps(XMMRegister dst, XMMRegister src) {Assembler::xorps(dst, src); }
  void xorps(XMMRegister dst, Address src)       {Assembler::xorps(dst, src); }
  void xorps(XMMRegister dst, AddressLiteral src);


  // Data

  void movoop(Register dst, jobject obj);
  void movoop(Address dst, jobject obj);

  void movptr(ArrayAddress dst, Register src);
  void movptr(Register dst, AddressLiteral src);

  void movptr(Register dst, intptr_t src);
  void movptr(Address dst, intptr_t src);

  void movptr(Register dst, ArrayAddress src);

  // to avoid hiding movl
  void mov32(AddressLiteral dst, Register src);
  void mov32(Register dst, AddressLiteral src);

  void pushoop(jobject obj);

  // Can push value or effective address
  void pushptr(AddressLiteral src);

};

/**
 * class SkipIfEqual:
 *
 * Instantiating this class will result in assembly code being output that will
 * jump around any code emitted between the creation of the instance and it's
 * automatic destruction at the end of a scope block, depending on the value of
 * the flag passed to the constructor, which will be checked at run-time.
 */
class SkipIfEqual {
 private:
  MacroAssembler* _masm;
  Label _label;

 public:
   SkipIfEqual(MacroAssembler*, const bool* flag_addr, bool value);
   ~SkipIfEqual();
};


#ifdef ASSERT
inline bool AbstractAssembler::pd_check_instruction_mark() { return true; }
#endif
