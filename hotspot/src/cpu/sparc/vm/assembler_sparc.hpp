/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

class BiasedLockingCounters;

// <sys/trap.h> promises that the system will not use traps 16-31
#define ST_RESERVED_FOR_USER_0 0x10

/* Written: David Ungar 4/19/97 */

// Contains all the definitions needed for sparc assembly code generation.

// Register aliases for parts of the system:

// 64 bit values can be kept in g1-g5, o1-o5 and o7 and all 64 bits are safe
// across context switches in V8+ ABI.  Of course, there are no 64 bit regs
// in V8 ABI. All 64 bits are preserved in V9 ABI for all registers.

// g2-g4 are scratch registers called "application globals".  Their
// meaning is reserved to the "compilation system"--which means us!
// They are are not supposed to be touched by ordinary C code, although
// highly-optimized C code might steal them for temps.  They are safe
// across thread switches, and the ABI requires that they be safe
// across function calls.
//
// g1 and g3 are touched by more modules.  V8 allows g1 to be clobbered
// across func calls, and V8+ also allows g5 to be clobbered across
// func calls.  Also, g1 and g5 can get touched while doing shared
// library loading.
//
// We must not touch g7 (it is the thread-self register) and g6 is
// reserved for certain tools.  g0, of course, is always zero.
//
// (Sources:  SunSoft Compilers Group, thread library engineers.)

// %%%% The interpreter should be revisited to reduce global scratch regs.

// This global always holds the current JavaThread pointer:

REGISTER_DECLARATION(Register, G2_thread , G2);
REGISTER_DECLARATION(Register, G6_heapbase , G6);

// The following globals are part of the Java calling convention:

REGISTER_DECLARATION(Register, G5_method             , G5);
REGISTER_DECLARATION(Register, G5_megamorphic_method , G5_method);
REGISTER_DECLARATION(Register, G5_inline_cache_reg   , G5_method);

// The following globals are used for the new C1 & interpreter calling convention:
REGISTER_DECLARATION(Register, Gargs        , G4); // pointing to the last argument

// This local is used to preserve G2_thread in the interpreter and in stubs:
REGISTER_DECLARATION(Register, L7_thread_cache , L7);

// These globals are used as scratch registers in the interpreter:

REGISTER_DECLARATION(Register, Gframe_size   , G1); // SAME REG as G1_scratch
REGISTER_DECLARATION(Register, G1_scratch    , G1); // also SAME
REGISTER_DECLARATION(Register, G3_scratch    , G3);
REGISTER_DECLARATION(Register, G4_scratch    , G4);

// These globals are used as short-lived scratch registers in the compiler:

REGISTER_DECLARATION(Register, Gtemp  , G5);

// JSR 292 fixed register usages:
REGISTER_DECLARATION(Register, G5_method_type        , G5);
REGISTER_DECLARATION(Register, G3_method_handle      , G3);
REGISTER_DECLARATION(Register, L7_mh_SP_save         , L7);

// The compiler requires that G5_megamorphic_method is G5_inline_cache_klass,
// because a single patchable "set" instruction (NativeMovConstReg,
// or NativeMovConstPatching for compiler1) instruction
// serves to set up either quantity, depending on whether the compiled
// call site is an inline cache or is megamorphic.  See the function
// CompiledIC::set_to_megamorphic.
//
// If a inline cache targets an interpreted method, then the
// G5 register will be used twice during the call.  First,
// the call site will be patched to load a compiledICHolder
// into G5. (This is an ordered pair of ic_klass, method.)
// The c2i adapter will first check the ic_klass, then load
// G5_method with the method part of the pair just before
// jumping into the interpreter.
//
// Note that G5_method is only the method-self for the interpreter,
// and is logically unrelated to G5_megamorphic_method.
//
// Invariants on G2_thread (the JavaThread pointer):
//  - it should not be used for any other purpose anywhere
//  - it must be re-initialized by StubRoutines::call_stub()
//  - it must be preserved around every use of call_VM

// We can consider using g2/g3/g4 to cache more values than the
// JavaThread, such as the card-marking base or perhaps pointers into
// Eden.  It's something of a waste to use them as scratch temporaries,
// since they are not supposed to be volatile.  (Of course, if we find
// that Java doesn't benefit from application globals, then we can just
// use them as ordinary temporaries.)
//
// Since g1 and g5 (and/or g6) are the volatile (caller-save) registers,
// it makes sense to use them routinely for procedure linkage,
// whenever the On registers are not applicable.  Examples:  G5_method,
// G5_inline_cache_klass, and a double handful of miscellaneous compiler
// stubs.  This means that compiler stubs, etc., should be kept to a
// maximum of two or three G-register arguments.


// stub frames

REGISTER_DECLARATION(Register, Lentry_args      , L0); // pointer to args passed to callee (interpreter) not stub itself

// Interpreter frames

#ifdef CC_INTERP
REGISTER_DECLARATION(Register, Lstate           , L0); // interpreter state object pointer
REGISTER_DECLARATION(Register, L1_scratch       , L1); // scratch
REGISTER_DECLARATION(Register, Lmirror          , L1); // mirror (for native methods only)
REGISTER_DECLARATION(Register, L2_scratch       , L2);
REGISTER_DECLARATION(Register, L3_scratch       , L3);
REGISTER_DECLARATION(Register, L4_scratch       , L4);
REGISTER_DECLARATION(Register, Lscratch         , L5); // C1 uses
REGISTER_DECLARATION(Register, Lscratch2        , L6); // C1 uses
REGISTER_DECLARATION(Register, L7_scratch       , L7); // constant pool cache
REGISTER_DECLARATION(Register, O5_savedSP       , O5);
REGISTER_DECLARATION(Register, I5_savedSP       , I5); // Saved SP before bumping for locals.  This is simply
                                                       // a copy SP, so in 64-bit it's a biased value.  The bias
                                                       // is added and removed as needed in the frame code.
// Interface to signature handler
REGISTER_DECLARATION(Register, Llocals          , L7); // pointer to locals for signature handler
REGISTER_DECLARATION(Register, Lmethod          , L6); // methodOop when calling signature handler

#else
REGISTER_DECLARATION(Register, Lesp             , L0); // expression stack pointer
REGISTER_DECLARATION(Register, Lbcp             , L1); // pointer to next bytecode
REGISTER_DECLARATION(Register, Lmethod          , L2);
REGISTER_DECLARATION(Register, Llocals          , L3);
REGISTER_DECLARATION(Register, Largs            , L3); // pointer to locals for signature handler
                                                       // must match Llocals in asm interpreter
REGISTER_DECLARATION(Register, Lmonitors        , L4);
REGISTER_DECLARATION(Register, Lbyte_code       , L5);
// When calling out from the interpreter we record SP so that we can remove any extra stack
// space allocated during adapter transitions. This register is only live from the point
// of the call until we return.
REGISTER_DECLARATION(Register, Llast_SP         , L5);
REGISTER_DECLARATION(Register, Lscratch         , L5);
REGISTER_DECLARATION(Register, Lscratch2        , L6);
REGISTER_DECLARATION(Register, LcpoolCache      , L6); // constant pool cache

REGISTER_DECLARATION(Register, O5_savedSP       , O5);
REGISTER_DECLARATION(Register, I5_savedSP       , I5); // Saved SP before bumping for locals.  This is simply
                                                       // a copy SP, so in 64-bit it's a biased value.  The bias
                                                       // is added and removed as needed in the frame code.
REGISTER_DECLARATION(Register, IdispatchTables  , I4); // Base address of the bytecode dispatch tables
REGISTER_DECLARATION(Register, IdispatchAddress , I3); // Register which saves the dispatch address for each bytecode
REGISTER_DECLARATION(Register, ImethodDataPtr   , I2); // Pointer to the current method data
#endif /* CC_INTERP */

// NOTE: Lscratch2 and LcpoolCache point to the same registers in
//       the interpreter code. If Lscratch2 needs to be used for some
//       purpose than LcpoolCache should be restore after that for
//       the interpreter to work right
// (These assignments must be compatible with L7_thread_cache; see above.)

// Since Lbcp points into the middle of the method object,
// it is temporarily converted into a "bcx" during GC.

// Exception processing
// These registers are passed into exception handlers.
// All exception handlers require the exception object being thrown.
// In addition, an nmethod's exception handler must be passed
// the address of the call site within the nmethod, to allow
// proper selection of the applicable catch block.
// (Interpreter frames use their own bcp() for this purpose.)
//
// The Oissuing_pc value is not always needed.  When jumping to a
// handler that is known to be interpreted, the Oissuing_pc value can be
// omitted.  An actual catch block in compiled code receives (from its
// nmethod's exception handler) the thrown exception in the Oexception,
// but it doesn't need the Oissuing_pc.
//
// If an exception handler (either interpreted or compiled)
// discovers there is no applicable catch block, it updates
// the Oissuing_pc to the continuation PC of its own caller,
// pops back to that caller's stack frame, and executes that
// caller's exception handler.  Obviously, this process will
// iterate until the control stack is popped back to a method
// containing an applicable catch block.  A key invariant is
// that the Oissuing_pc value is always a value local to
// the method whose exception handler is currently executing.
//
// Note:  The issuing PC value is __not__ a raw return address (I7 value).
// It is a "return pc", the address __following__ the call.
// Raw return addresses are converted to issuing PCs by frame::pc(),
// or by stubs.  Issuing PCs can be used directly with PC range tables.
//
REGISTER_DECLARATION(Register, Oexception  , O0); // exception being thrown
REGISTER_DECLARATION(Register, Oissuing_pc , O1); // where the exception is coming from


// These must occur after the declarations above
#ifndef DONT_USE_REGISTER_DEFINES

#define Gthread             AS_REGISTER(Register, Gthread)
#define Gmethod             AS_REGISTER(Register, Gmethod)
#define Gmegamorphic_method AS_REGISTER(Register, Gmegamorphic_method)
#define Ginline_cache_reg   AS_REGISTER(Register, Ginline_cache_reg)
#define Gargs               AS_REGISTER(Register, Gargs)
#define Lthread_cache       AS_REGISTER(Register, Lthread_cache)
#define Gframe_size         AS_REGISTER(Register, Gframe_size)
#define Gtemp               AS_REGISTER(Register, Gtemp)

#ifdef CC_INTERP
#define Lstate              AS_REGISTER(Register, Lstate)
#define Lesp                AS_REGISTER(Register, Lesp)
#define L1_scratch          AS_REGISTER(Register, L1_scratch)
#define Lmirror             AS_REGISTER(Register, Lmirror)
#define L2_scratch          AS_REGISTER(Register, L2_scratch)
#define L3_scratch          AS_REGISTER(Register, L3_scratch)
#define L4_scratch          AS_REGISTER(Register, L4_scratch)
#define Lscratch            AS_REGISTER(Register, Lscratch)
#define Lscratch2           AS_REGISTER(Register, Lscratch2)
#define L7_scratch          AS_REGISTER(Register, L7_scratch)
#define Ostate              AS_REGISTER(Register, Ostate)
#else
#define Lesp                AS_REGISTER(Register, Lesp)
#define Lbcp                AS_REGISTER(Register, Lbcp)
#define Lmethod             AS_REGISTER(Register, Lmethod)
#define Llocals             AS_REGISTER(Register, Llocals)
#define Lmonitors           AS_REGISTER(Register, Lmonitors)
#define Lbyte_code          AS_REGISTER(Register, Lbyte_code)
#define Lscratch            AS_REGISTER(Register, Lscratch)
#define Lscratch2           AS_REGISTER(Register, Lscratch2)
#define LcpoolCache         AS_REGISTER(Register, LcpoolCache)
#endif /* ! CC_INTERP */

#define Lentry_args         AS_REGISTER(Register, Lentry_args)
#define I5_savedSP          AS_REGISTER(Register, I5_savedSP)
#define O5_savedSP          AS_REGISTER(Register, O5_savedSP)
#define IdispatchAddress    AS_REGISTER(Register, IdispatchAddress)
#define ImethodDataPtr      AS_REGISTER(Register, ImethodDataPtr)
#define IdispatchTables     AS_REGISTER(Register, IdispatchTables)

#define Oexception          AS_REGISTER(Register, Oexception)
#define Oissuing_pc         AS_REGISTER(Register, Oissuing_pc)


#endif

// Address is an abstraction used to represent a memory location.
//
// Note: A register location is represented via a Register, not
//       via an address for efficiency & simplicity reasons.

class Address VALUE_OBJ_CLASS_SPEC {
 private:
  Register           _base;           // Base register.
  RegisterOrConstant _index_or_disp;  // Index register or constant displacement.
  RelocationHolder   _rspec;

 public:
  Address() : _base(noreg), _index_or_disp(noreg) {}

  Address(Register base, RegisterOrConstant index_or_disp)
    : _base(base),
      _index_or_disp(index_or_disp) {
  }

  Address(Register base, Register index)
    : _base(base),
      _index_or_disp(index) {
  }

  Address(Register base, int disp)
    : _base(base),
      _index_or_disp(disp) {
  }

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  Address(Register base, ByteSize disp)
    : _base(base),
      _index_or_disp(in_bytes(disp)) {
  }
#endif

  // accessors
  Register base()      const { return _base; }
  Register index()     const { return _index_or_disp.as_register(); }
  int      disp()      const { return _index_or_disp.as_constant(); }

  bool     has_index() const { return _index_or_disp.is_register(); }
  bool     has_disp()  const { return _index_or_disp.is_constant(); }

  const relocInfo::relocType rtype() { return _rspec.type(); }
  const RelocationHolder&    rspec() { return _rspec; }

  RelocationHolder rspec(int offset) const {
    return offset == 0 ? _rspec : _rspec.plus(offset);
  }

  inline bool is_simm13(int offset = 0);  // check disp+offset for overflow

  Address plus_disp(int plusdisp) const {     // bump disp by a small amount
    assert(_index_or_disp.is_constant(), "must have a displacement");
    Address a(base(), disp() + plusdisp);
    return a;
  }

  Address after_save() const {
    Address a = (*this);
    a._base = a._base->after_save();
    return a;
  }

  Address after_restore() const {
    Address a = (*this);
    a._base = a._base->after_restore();
    return a;
  }

  // Convert the raw encoding form into the form expected by the
  // constructor for Address.
  static Address make_raw(int base, int index, int scale, int disp, bool disp_is_oop);

  friend class Assembler;
};


class AddressLiteral VALUE_OBJ_CLASS_SPEC {
 private:
  address          _address;
  RelocationHolder _rspec;

  RelocationHolder rspec_from_rtype(relocInfo::relocType rtype, address addr) {
    switch (rtype) {
    case relocInfo::external_word_type:
      return external_word_Relocation::spec(addr);
    case relocInfo::internal_word_type:
      return internal_word_Relocation::spec(addr);
#ifdef _LP64
    case relocInfo::opt_virtual_call_type:
      return opt_virtual_call_Relocation::spec();
    case relocInfo::static_call_type:
      return static_call_Relocation::spec();
    case relocInfo::runtime_call_type:
      return runtime_call_Relocation::spec();
#endif
    case relocInfo::none:
      return RelocationHolder();
    default:
      ShouldNotReachHere();
      return RelocationHolder();
    }
  }

 protected:
  // creation
  AddressLiteral() : _address(NULL), _rspec(NULL) {}

 public:
  AddressLiteral(address addr, RelocationHolder const& rspec)
    : _address(addr),
      _rspec(rspec) {}

  // Some constructors to avoid casting at the call site.
  AddressLiteral(jobject obj, RelocationHolder const& rspec)
    : _address((address) obj),
      _rspec(rspec) {}

  AddressLiteral(intptr_t value, RelocationHolder const& rspec)
    : _address((address) value),
      _rspec(rspec) {}

  AddressLiteral(address addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
    _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  // Some constructors to avoid casting at the call site.
  AddressLiteral(address* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
    _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(bool* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(const bool* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(signed char* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(int* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(intptr_t addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

#ifdef _LP64
  // 32-bit complains about a multiple declaration for int*.
  AddressLiteral(intptr_t* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}
#endif

  AddressLiteral(oop addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(float* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(double* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  intptr_t value() const { return (intptr_t) _address; }
  int      low10() const;

  const relocInfo::relocType rtype() const { return _rspec.type(); }
  const RelocationHolder&    rspec() const { return _rspec; }

  RelocationHolder rspec(int offset) const {
    return offset == 0 ? _rspec : _rspec.plus(offset);
  }
};


inline Address RegisterImpl::address_in_saved_window() const {
   return (Address(SP, (sp_offset_in_saved_window() * wordSize) + STACK_BIAS));
}



// Argument is an abstraction used to represent an outgoing
// actual argument or an incoming formal parameter, whether
// it resides in memory or in a register, in a manner consistent
// with the SPARC Application Binary Interface, or ABI.  This is
// often referred to as the native or C calling convention.

class Argument VALUE_OBJ_CLASS_SPEC {
 private:
  int _number;
  bool _is_in;

 public:
#ifdef _LP64
  enum {
    n_register_parameters = 6,          // only 6 registers may contain integer parameters
    n_float_register_parameters = 16    // Can have up to 16 floating registers
  };
#else
  enum {
    n_register_parameters = 6           // only 6 registers may contain integer parameters
  };
#endif

  // creation
  Argument(int number, bool is_in) : _number(number), _is_in(is_in) {}

  int  number() const  { return _number;  }
  bool is_in()  const  { return _is_in;   }
  bool is_out() const  { return !is_in(); }

  Argument successor() const  { return Argument(number() + 1, is_in()); }
  Argument as_in()     const  { return Argument(number(), true ); }
  Argument as_out()    const  { return Argument(number(), false); }

  // locating register-based arguments:
  bool is_register() const { return _number < n_register_parameters; }

#ifdef _LP64
  // locating Floating Point register-based arguments:
  bool is_float_register() const { return _number < n_float_register_parameters; }

  FloatRegister as_float_register() const {
    assert(is_float_register(), "must be a register argument");
    return as_FloatRegister(( number() *2 ) + 1);
  }
  FloatRegister as_double_register() const {
    assert(is_float_register(), "must be a register argument");
    return as_FloatRegister(( number() *2 ));
  }
#endif

  Register as_register() const {
    assert(is_register(), "must be a register argument");
    return is_in() ? as_iRegister(number()) : as_oRegister(number());
  }

  // locating memory-based arguments
  Address as_address() const {
    assert(!is_register(), "must be a memory argument");
    return address_in_frame();
  }

  // When applied to a register-based argument, give the corresponding address
  // into the 6-word area "into which callee may store register arguments"
  // (This is a different place than the corresponding register-save area location.)
  Address address_in_frame() const;

  // debugging
  const char* name() const;

  friend class Assembler;
};


// The SPARC Assembler: Pure assembler doing NO optimizations on the instruction
// level; i.e., what you write
// is what you get. The Assembler is generating code into a CodeBuffer.

class Assembler : public AbstractAssembler  {
 protected:

  static void print_instruction(int inst);
  static int  patched_branch(int dest_pos, int inst, int inst_pos);
  static int  branch_destination(int inst, int pos);


  friend class AbstractAssembler;
  friend class AddressLiteral;

  // code patchers need various routines like inv_wdisp()
  friend class NativeInstruction;
  friend class NativeGeneralJump;
  friend class Relocation;
  friend class Label;

 public:
  // op carries format info; see page 62 & 267

  enum ops {
    call_op   = 1, // fmt 1
    branch_op = 0, // also sethi (fmt2)
    arith_op  = 2, // fmt 3, arith & misc
    ldst_op   = 3  // fmt 3, load/store
  };

  enum op2s {
    bpr_op2   = 3,
    fb_op2    = 6,
    fbp_op2   = 5,
    br_op2    = 2,
    bp_op2    = 1,
    cb_op2    = 7, // V8
    sethi_op2 = 4
  };

  enum op3s {
    // selected op3s
    add_op3      = 0x00,
    and_op3      = 0x01,
    or_op3       = 0x02,
    xor_op3      = 0x03,
    sub_op3      = 0x04,
    andn_op3     = 0x05,
    orn_op3      = 0x06,
    xnor_op3     = 0x07,
    addc_op3     = 0x08,
    mulx_op3     = 0x09,
    umul_op3     = 0x0a,
    smul_op3     = 0x0b,
    subc_op3     = 0x0c,
    udivx_op3    = 0x0d,
    udiv_op3     = 0x0e,
    sdiv_op3     = 0x0f,

    addcc_op3    = 0x10,
    andcc_op3    = 0x11,
    orcc_op3     = 0x12,
    xorcc_op3    = 0x13,
    subcc_op3    = 0x14,
    andncc_op3   = 0x15,
    orncc_op3    = 0x16,
    xnorcc_op3   = 0x17,
    addccc_op3   = 0x18,
    umulcc_op3   = 0x1a,
    smulcc_op3   = 0x1b,
    subccc_op3   = 0x1c,
    udivcc_op3   = 0x1e,
    sdivcc_op3   = 0x1f,

    taddcc_op3   = 0x20,
    tsubcc_op3   = 0x21,
    taddcctv_op3 = 0x22,
    tsubcctv_op3 = 0x23,
    mulscc_op3   = 0x24,
    sll_op3      = 0x25,
    sllx_op3     = 0x25,
    srl_op3      = 0x26,
    srlx_op3     = 0x26,
    sra_op3      = 0x27,
    srax_op3     = 0x27,
    rdreg_op3    = 0x28,
    membar_op3   = 0x28,

    flushw_op3   = 0x2b,
    movcc_op3    = 0x2c,
    sdivx_op3    = 0x2d,
    popc_op3     = 0x2e,
    movr_op3     = 0x2f,

    sir_op3      = 0x30,
    wrreg_op3    = 0x30,
    saved_op3    = 0x31,

    fpop1_op3    = 0x34,
    fpop2_op3    = 0x35,
    impdep1_op3  = 0x36,
    impdep2_op3  = 0x37,
    jmpl_op3     = 0x38,
    rett_op3     = 0x39,
    trap_op3     = 0x3a,
    flush_op3    = 0x3b,
    save_op3     = 0x3c,
    restore_op3  = 0x3d,
    done_op3     = 0x3e,
    retry_op3    = 0x3e,

    lduw_op3     = 0x00,
    ldub_op3     = 0x01,
    lduh_op3     = 0x02,
    ldd_op3      = 0x03,
    stw_op3      = 0x04,
    stb_op3      = 0x05,
    sth_op3      = 0x06,
    std_op3      = 0x07,
    ldsw_op3     = 0x08,
    ldsb_op3     = 0x09,
    ldsh_op3     = 0x0a,
    ldx_op3      = 0x0b,

    ldstub_op3   = 0x0d,
    stx_op3      = 0x0e,
    swap_op3     = 0x0f,

    stwa_op3     = 0x14,
    stxa_op3     = 0x1e,

    ldf_op3      = 0x20,
    ldfsr_op3    = 0x21,
    ldqf_op3     = 0x22,
    lddf_op3     = 0x23,
    stf_op3      = 0x24,
    stfsr_op3    = 0x25,
    stqf_op3     = 0x26,
    stdf_op3     = 0x27,

    prefetch_op3 = 0x2d,


    ldc_op3      = 0x30,
    ldcsr_op3    = 0x31,
    lddc_op3     = 0x33,
    stc_op3      = 0x34,
    stcsr_op3    = 0x35,
    stdcq_op3    = 0x36,
    stdc_op3     = 0x37,

    casa_op3     = 0x3c,
    casxa_op3    = 0x3e,

    alt_bit_op3  = 0x10,
     cc_bit_op3  = 0x10
  };

  enum opfs {
    // selected opfs
    fmovs_opf   = 0x01,
    fmovd_opf   = 0x02,

    fnegs_opf   = 0x05,
    fnegd_opf   = 0x06,

    fadds_opf   = 0x41,
    faddd_opf   = 0x42,
    fsubs_opf   = 0x45,
    fsubd_opf   = 0x46,

    fmuls_opf   = 0x49,
    fmuld_opf   = 0x4a,
    fdivs_opf   = 0x4d,
    fdivd_opf   = 0x4e,

    fcmps_opf   = 0x51,
    fcmpd_opf   = 0x52,

    fstox_opf   = 0x81,
    fdtox_opf   = 0x82,
    fxtos_opf   = 0x84,
    fxtod_opf   = 0x88,
    fitos_opf   = 0xc4,
    fdtos_opf   = 0xc6,
    fitod_opf   = 0xc8,
    fstod_opf   = 0xc9,
    fstoi_opf   = 0xd1,
    fdtoi_opf   = 0xd2
  };

  enum RCondition {  rc_z = 1,  rc_lez = 2,  rc_lz = 3, rc_nz = 5, rc_gz = 6, rc_gez = 7  };

  enum Condition {
     // for FBfcc & FBPfcc instruction
    f_never                     = 0,
    f_notEqual                  = 1,
    f_notZero                   = 1,
    f_lessOrGreater             = 2,
    f_unorderedOrLess           = 3,
    f_less                      = 4,
    f_unorderedOrGreater        = 5,
    f_greater                   = 6,
    f_unordered                 = 7,
    f_always                    = 8,
    f_equal                     = 9,
    f_zero                      = 9,
    f_unorderedOrEqual          = 10,
    f_greaterOrEqual            = 11,
    f_unorderedOrGreaterOrEqual = 12,
    f_lessOrEqual               = 13,
    f_unorderedOrLessOrEqual    = 14,
    f_ordered                   = 15,

    // V8 coproc, pp 123 v8 manual

    cp_always  = 8,
    cp_never   = 0,
    cp_3       = 7,
    cp_2       = 6,
    cp_2or3    = 5,
    cp_1       = 4,
    cp_1or3    = 3,
    cp_1or2    = 2,
    cp_1or2or3 = 1,
    cp_0       = 9,
    cp_0or3    = 10,
    cp_0or2    = 11,
    cp_0or2or3 = 12,
    cp_0or1    = 13,
    cp_0or1or3 = 14,
    cp_0or1or2 = 15,


    // for integers

    never                 =  0,
    equal                 =  1,
    zero                  =  1,
    lessEqual             =  2,
    less                  =  3,
    lessEqualUnsigned     =  4,
    lessUnsigned          =  5,
    carrySet              =  5,
    negative              =  6,
    overflowSet           =  7,
    always                =  8,
    notEqual              =  9,
    notZero               =  9,
    greater               =  10,
    greaterEqual          =  11,
    greaterUnsigned       =  12,
    greaterEqualUnsigned  =  13,
    carryClear            =  13,
    positive              =  14,
    overflowClear         =  15
  };

  enum CC {
    icc  = 0,  xcc  = 2,
    // ptr_cc is the correct condition code for a pointer or intptr_t:
    ptr_cc = NOT_LP64(icc) LP64_ONLY(xcc),
    fcc0 = 0,  fcc1 = 1, fcc2 = 2, fcc3 = 3
  };

  enum PrefetchFcn {
    severalReads = 0,  oneRead = 1,  severalWritesAndPossiblyReads = 2, oneWrite = 3, page = 4
  };

 public:
  // Helper functions for groups of instructions

  enum Predict { pt = 1, pn = 0 }; // pt = predict taken

  enum Membar_mask_bits { // page 184, v9
    StoreStore = 1 << 3,
    LoadStore  = 1 << 2,
    StoreLoad  = 1 << 1,
    LoadLoad   = 1 << 0,

    Sync       = 1 << 6,
    MemIssue   = 1 << 5,
    Lookaside  = 1 << 4
  };

  // test if x is within signed immediate range for nbits
  static bool is_simm(int x, int nbits) { return -( 1 << nbits-1 )  <= x   &&   x  <  ( 1 << nbits-1 ); }

  // test if -4096 <= x <= 4095
  static bool is_simm13(int x) { return is_simm(x, 13); }

  // test if label is in simm16 range in words (wdisp16).
  bool is_in_wdisp16_range(Label& L) {
    intptr_t d = intptr_t(pc()) - intptr_t(target(L));
    return is_simm(d, 18);
  }

  enum ASIs { // page 72, v9
    ASI_PRIMARY        = 0x80,
    ASI_PRIMARY_LITTLE = 0x88
    // add more from book as needed
  };

 protected:
  // helpers

  // x is supposed to fit in a field "nbits" wide
  // and be sign-extended. Check the range.

  static void assert_signed_range(intptr_t x, int nbits) {
    assert( nbits == 32
        ||  -(1 << nbits-1) <= x  &&  x < ( 1 << nbits-1),
      "value out of range");
  }

  static void assert_signed_word_disp_range(intptr_t x, int nbits) {
    assert( (x & 3) == 0, "not word aligned");
    assert_signed_range(x, nbits + 2);
  }

  static void assert_unsigned_const(int x, int nbits) {
    assert( juint(x)  <  juint(1 << nbits), "unsigned constant out of range");
  }

  // fields: note bits numbered from LSB = 0,
  //  fields known by inclusive bit range

  static int fmask(juint hi_bit, juint lo_bit) {
    assert( hi_bit >= lo_bit  &&  0 <= lo_bit  &&  hi_bit < 32, "bad bits");
    return (1 << ( hi_bit-lo_bit + 1 )) - 1;
  }

  // inverse of u_field

  static int inv_u_field(int x, int hi_bit, int lo_bit) {
    juint r = juint(x) >> lo_bit;
    r &= fmask( hi_bit, lo_bit);
    return int(r);
  }


  // signed version: extract from field and sign-extend

  static int inv_s_field(int x, int hi_bit, int lo_bit) {
    int sign_shift = 31 - hi_bit;
    return inv_u_field( ((x << sign_shift) >> sign_shift), hi_bit, lo_bit);
  }

  // given a field that ranges from hi_bit to lo_bit (inclusive,
  // LSB = 0), and an unsigned value for the field,
  // shift it into the field

#ifdef ASSERT
  static int u_field(int x, int hi_bit, int lo_bit) {
    assert( ( x & ~fmask(hi_bit, lo_bit))  == 0,
            "value out of range");
    int r = x << lo_bit;
    assert( inv_u_field(r, hi_bit, lo_bit) == x, "just checking");
    return r;
  }
#else
  // make sure this is inlined as it will reduce code size significantly
  #define u_field(x, hi_bit, lo_bit)   ((x) << (lo_bit))
#endif

  static int inv_op(  int x ) { return inv_u_field(x, 31, 30); }
  static int inv_op2( int x ) { return inv_u_field(x, 24, 22); }
  static int inv_op3( int x ) { return inv_u_field(x, 24, 19); }
  static int inv_cond( int x ){ return inv_u_field(x, 28, 25); }

  static bool inv_immed( int x ) { return (x & Assembler::immed(true)) != 0; }

  static Register inv_rd(  int x ) { return as_Register(inv_u_field(x, 29, 25)); }
  static Register inv_rs1( int x ) { return as_Register(inv_u_field(x, 18, 14)); }
  static Register inv_rs2( int x ) { return as_Register(inv_u_field(x,  4,  0)); }

  static int op(       int         x)  { return  u_field(x,             31, 30); }
  static int rd(       Register    r)  { return  u_field(r->encoding(), 29, 25); }
  static int fcn(      int         x)  { return  u_field(x,             29, 25); }
  static int op3(      int         x)  { return  u_field(x,             24, 19); }
  static int rs1(      Register    r)  { return  u_field(r->encoding(), 18, 14); }
  static int rs2(      Register    r)  { return  u_field(r->encoding(),  4,  0); }
  static int annul(    bool        a)  { return  u_field(a ? 1 : 0,     29, 29); }
  static int cond(     int         x)  { return  u_field(x,             28, 25); }
  static int cond_mov( int         x)  { return  u_field(x,             17, 14); }
  static int rcond(    RCondition  x)  { return  u_field(x,             12, 10); }
  static int op2(      int         x)  { return  u_field(x,             24, 22); }
  static int predict(  bool        p)  { return  u_field(p ? 1 : 0,     19, 19); }
  static int branchcc( CC       fcca)  { return  u_field(fcca,          21, 20); }
  static int cmpcc(    CC       fcca)  { return  u_field(fcca,          26, 25); }
  static int imm_asi(  int         x)  { return  u_field(x,             12,  5); }
  static int immed(    bool        i)  { return  u_field(i ? 1 : 0,     13, 13); }
  static int opf_low6( int         w)  { return  u_field(w,             10,  5); }
  static int opf_low5( int         w)  { return  u_field(w,              9,  5); }
  static int trapcc(   CC         cc)  { return  u_field(cc,            12, 11); }
  static int sx(       int         i)  { return  u_field(i,             12, 12); } // shift x=1 means 64-bit
  static int opf(      int         x)  { return  u_field(x,             13,  5); }

  static int opf_cc(   CC          c, bool useFloat ) { return u_field((useFloat ? 0 : 4) + c, 13, 11); }
  static int mov_cc(   CC          c, bool useFloat ) { return u_field(useFloat ? 0 : 1,  18, 18) | u_field(c, 12, 11); }

  static int fd( FloatRegister r,  FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa), 29, 25); };
  static int fs1(FloatRegister r,  FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa), 18, 14); };
  static int fs2(FloatRegister r,  FloatRegisterImpl::Width fwa) { return u_field(r->encoding(fwa),  4,  0); };

  // some float instructions use this encoding on the op3 field
  static int alt_op3(int op, FloatRegisterImpl::Width w) {
    int r;
    switch(w) {
     case FloatRegisterImpl::S: r = op + 0;  break;
     case FloatRegisterImpl::D: r = op + 3;  break;
     case FloatRegisterImpl::Q: r = op + 2;  break;
     default: ShouldNotReachHere(); break;
    }
    return op3(r);
  }


  // compute inverse of simm
  static int inv_simm(int x, int nbits) {
    return (int)(x << (32 - nbits)) >> (32 - nbits);
  }

  static int inv_simm13( int x ) { return inv_simm(x, 13); }

  // signed immediate, in low bits, nbits long
  static int simm(int x, int nbits) {
    assert_signed_range(x, nbits);
    return x  &  (( 1 << nbits ) - 1);
  }

  // compute inverse of wdisp16
  static intptr_t inv_wdisp16(int x, intptr_t pos) {
    int lo = x & (( 1 << 14 ) - 1);
    int hi = (x >> 20) & 3;
    if (hi >= 2) hi |= ~1;
    return (((hi << 14) | lo) << 2) + pos;
  }

  // word offset, 14 bits at LSend, 2 bits at B21, B20
  static int wdisp16(intptr_t x, intptr_t off) {
    intptr_t xx = x - off;
    assert_signed_word_disp_range(xx, 16);
    int r =  (xx >> 2) & ((1 << 14) - 1)
           |  (  ( (xx>>(2+14)) & 3 )  <<  20 );
    assert( inv_wdisp16(r, off) == x,  "inverse is not inverse");
    return r;
  }


  // word displacement in low-order nbits bits

  static intptr_t inv_wdisp( int x, intptr_t pos, int nbits ) {
    int pre_sign_extend = x & (( 1 << nbits ) - 1);
    int r =  pre_sign_extend >= ( 1 << (nbits-1) )
       ?   pre_sign_extend | ~(( 1 << nbits ) - 1)
       :   pre_sign_extend;
    return (r << 2) + pos;
  }

  static int wdisp( intptr_t x, intptr_t off, int nbits ) {
    intptr_t xx = x - off;
    assert_signed_word_disp_range(xx, nbits);
    int r =  (xx >> 2) & (( 1 << nbits ) - 1);
    assert( inv_wdisp( r, off, nbits )  ==  x, "inverse not inverse");
    return r;
  }


  // Extract the top 32 bits in a 64 bit word
  static int32_t hi32( int64_t x ) {
    int32_t r = int32_t( (uint64_t)x >> 32 );
    return r;
  }

  // given a sethi instruction, extract the constant, left-justified
  static int inv_hi22( int x ) {
    return x << 10;
  }

  // create an imm22 field, given a 32-bit left-justified constant
  static int hi22( int x ) {
    int r = int( juint(x) >> 10 );
    assert( (r & ~((1 << 22) - 1))  ==  0, "just checkin'");
    return r;
  }

  // create a low10 __value__ (not a field) for a given a 32-bit constant
  static int low10( int x ) {
    return x & ((1 << 10) - 1);
  }

  // instruction only in v9
  static void v9_only() { assert( VM_Version::v9_instructions_work(), "This instruction only works on SPARC V9"); }

  // instruction only in v8
  static void v8_only() { assert( VM_Version::v8_instructions_work(), "This instruction only works on SPARC V8"); }

  // instruction deprecated in v9
  static void v9_dep()  { } // do nothing for now

  // some float instructions only exist for single prec. on v8
  static void v8_s_only(FloatRegisterImpl::Width w)  { if (w != FloatRegisterImpl::S)  v9_only(); }

  // v8 has no CC field
  static void v8_no_cc(CC cc)  { if (cc)  v9_only(); }

 protected:
  // Simple delay-slot scheme:
  // In order to check the programmer, the assembler keeps track of deley slots.
  // It forbids CTIs in delay slots (conservative, but should be OK).
  // Also, when putting an instruction into a delay slot, you must say
  // asm->delayed()->add(...), in order to check that you don't omit
  // delay-slot instructions.
  // To implement this, we use a simple FSA

#ifdef ASSERT
  #define CHECK_DELAY
#endif
#ifdef CHECK_DELAY
  enum Delay_state { no_delay, at_delay_slot, filling_delay_slot } delay_state;
#endif

 public:
  // Tells assembler next instruction must NOT be in delay slot.
  // Use at start of multinstruction macros.
  void assert_not_delayed() {
    // This is a separate overloading to avoid creation of string constants
    // in non-asserted code--with some compilers this pollutes the object code.
#ifdef CHECK_DELAY
    assert_not_delayed("next instruction should not be a delay slot");
#endif
  }
  void assert_not_delayed(const char* msg) {
#ifdef CHECK_DELAY
    assert(delay_state == no_delay, msg);
#endif
  }

 protected:
  // Delay slot helpers
  // cti is called when emitting control-transfer instruction,
  // BEFORE doing the emitting.
  // Only effective when assertion-checking is enabled.
  void cti() {
#ifdef CHECK_DELAY
    assert_not_delayed("cti should not be in delay slot");
#endif
  }

  // called when emitting cti with a delay slot, AFTER emitting
  void has_delay_slot() {
#ifdef CHECK_DELAY
    assert_not_delayed("just checking");
    delay_state = at_delay_slot;
#endif
  }

public:
  // Tells assembler you know that next instruction is delayed
  Assembler* delayed() {
#ifdef CHECK_DELAY
    assert ( delay_state == at_delay_slot, "delayed instruction is not in delay slot");
    delay_state = filling_delay_slot;
#endif
    return this;
  }

  void flush() {
#ifdef CHECK_DELAY
    assert ( delay_state == no_delay, "ending code with a delay slot");
#endif
    AbstractAssembler::flush();
  }

  inline void emit_long(int);  // shadows AbstractAssembler::emit_long
  inline void emit_data(int x) { emit_long(x); }
  inline void emit_data(int, RelocationHolder const&);
  inline void emit_data(int, relocInfo::relocType rtype);
  // helper for above fcns
  inline void check_delay();


 public:
  // instructions, refer to page numbers in the SPARC Architecture Manual, V9

  // pp 135 (addc was addx in v8)

  inline void add(Register s1, Register s2, Register d );
  inline void add(Register s1, int simm13a, Register d, relocInfo::relocType rtype = relocInfo::none);
  inline void add(Register s1, int simm13a, Register d, RelocationHolder const& rspec);
  inline void add(Register s1, RegisterOrConstant s2, Register d, int offset = 0);
  inline void add(const Address& a, Register d, int offset = 0) { add( a.base(), a.disp() + offset, d, a.rspec(offset)); }

  void addcc(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(add_op3  | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void addcc(  Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(add_op3  | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void addc(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(addc_op3             ) | rs1(s1) | rs2(s2) ); }
  void addc(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(addc_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void addccc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(addc_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void addccc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(addc_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 136

  inline void bpr( RCondition c, bool a, Predict p, Register s1, address d, relocInfo::relocType rt = relocInfo::none );
  inline void bpr( RCondition c, bool a, Predict p, Register s1, Label& L);

 protected: // use MacroAssembler::br instead

  // pp 138

  inline void fb( Condition c, bool a, address d, relocInfo::relocType rt = relocInfo::none );
  inline void fb( Condition c, bool a, Label& L );

  // pp 141

  inline void fbp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void fbp( Condition c, bool a, CC cc, Predict p, Label& L );

 public:

  // pp 144

  inline void br( Condition c, bool a, address d, relocInfo::relocType rt = relocInfo::none );
  inline void br( Condition c, bool a, Label& L );

  // pp 146

  inline void bp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void bp( Condition c, bool a, CC cc, Predict p, Label& L );

  // pp 121 (V8)

  inline void cb( Condition c, bool a, address d, relocInfo::relocType rt = relocInfo::none );
  inline void cb( Condition c, bool a, Label& L );

  // pp 149

  inline void call( address d,  relocInfo::relocType rt = relocInfo::runtime_call_type );
  inline void call( Label& L,   relocInfo::relocType rt = relocInfo::runtime_call_type );

  // pp 150

  // These instructions compare the contents of s2 with the contents of
  // memory at address in s1. If the values are equal, the contents of memory
  // at address s1 is swapped with the data in d. If the values are not equal,
  // the the contents of memory at s1 is loaded into d, without the swap.

  void casa(  Register s1, Register s2, Register d, int ia = -1 ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(casa_op3 ) | rs1(s1) | (ia == -1  ? immed(true) : imm_asi(ia)) | rs2(s2)); }
  void casxa( Register s1, Register s2, Register d, int ia = -1 ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(casxa_op3) | rs1(s1) | (ia == -1  ? immed(true) : imm_asi(ia)) | rs2(s2)); }

  // pp 152

  void udiv(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(udiv_op3             ) | rs1(s1) | rs2(s2)); }
  void udiv(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(udiv_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void sdiv(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sdiv_op3             ) | rs1(s1) | rs2(s2)); }
  void sdiv(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sdiv_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void udivcc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(udiv_op3 | cc_bit_op3) | rs1(s1) | rs2(s2)); }
  void udivcc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(udiv_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void sdivcc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sdiv_op3 | cc_bit_op3) | rs1(s1) | rs2(s2)); }
  void sdivcc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sdiv_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 155

  void done()  { v9_only();  cti();  emit_long( op(arith_op) | fcn(0) | op3(done_op3) ); }
  void retry() { v9_only();  cti();  emit_long( op(arith_op) | fcn(1) | op3(retry_op3) ); }

  // pp 156

  void fadd( FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d ) { emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x40 + w) | fs2(s2, w)); }
  void fsub( FloatRegisterImpl::Width w, FloatRegister s1, FloatRegister s2, FloatRegister d ) { emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | fs1(s1, w) | opf(0x44 + w) | fs2(s2, w)); }

  // pp 157

  void fcmp(  FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2) { v8_no_cc(cc);  emit_long( op(arith_op) | cmpcc(cc) | op3(fpop2_op3) | fs1(s1, w) | opf(0x50 + w) | fs2(s2, w)); }
  void fcmpe( FloatRegisterImpl::Width w, CC cc, FloatRegister s1, FloatRegister s2) { v8_no_cc(cc);  emit_long( op(arith_op) | cmpcc(cc) | op3(fpop2_op3) | fs1(s1, w) | opf(0x54 + w) | fs2(s2, w)); }

  // pp 159

  void ftox( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { v9_only();  emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x80 + w) | fs2(s, w)); }
  void ftoi( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) {             emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0xd0 + w) | fs2(s, w)); }

  // pp 160

  void ftof( FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw, FloatRegister s, FloatRegister d ) { emit_long( op(arith_op) | fd(d, dw) | op3(fpop1_op3) | opf(0xc0 + sw + dw*4) | fs2(s, sw)); }

  // pp 161

  void fxtof( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { v9_only();  emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x80 + w*4) | fs2(s, w)); }
  void fitof( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) {             emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0xc0 + w*4) | fs2(s, w)); }

  // pp 162

  void fmov( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { v8_s_only(w);  emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x00 + w) | fs2(s, w)); }

  void fneg( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { v8_s_only(w);  emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x04 + w) | fs2(s, w)); }

  // page 144 sparc v8 architecture (double prec works on v8 if the source and destination registers are the same). fnegs is the only instruction available
  // on v8 to do negation of single, double and quad precision floats.

  void fneg( FloatRegisterImpl::Width w, FloatRegister sd ) { if (VM_Version::v9_instructions_work()) emit_long( op(arith_op) | fd(sd, w) | op3(fpop1_op3) | opf(0x04 + w) | fs2(sd, w)); else emit_long( op(arith_op) | fd(sd, w) | op3(fpop1_op3) |  opf(0x05) | fs2(sd, w)); }

  void fabs( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { v8_s_only(w);  emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x08 + w) | fs2(s, w)); }

  // page 144 sparc v8 architecture (double prec works on v8 if the source and destination registers are the same). fabss is the only instruction available
  // on v8 to do abs operation on single/double/quad precision floats.

  void fabs( FloatRegisterImpl::Width w, FloatRegister sd ) { if (VM_Version::v9_instructions_work()) emit_long( op(arith_op) | fd(sd, w) | op3(fpop1_op3) | opf(0x08 + w) | fs2(sd, w)); else emit_long( op(arith_op) | fd(sd, w) | op3(fpop1_op3) | opf(0x09) | fs2(sd, w)); }

  // pp 163

  void fmul( FloatRegisterImpl::Width w,                            FloatRegister s1, FloatRegister s2, FloatRegister d ) { emit_long( op(arith_op) | fd(d, w)  | op3(fpop1_op3) | fs1(s1, w)  | opf(0x48 + w)         | fs2(s2, w)); }
  void fmul( FloatRegisterImpl::Width sw, FloatRegisterImpl::Width dw,  FloatRegister s1, FloatRegister s2, FloatRegister d ) { emit_long( op(arith_op) | fd(d, dw) | op3(fpop1_op3) | fs1(s1, sw) | opf(0x60 + sw + dw*4) | fs2(s2, sw)); }
  void fdiv( FloatRegisterImpl::Width w,                            FloatRegister s1, FloatRegister s2, FloatRegister d ) { emit_long( op(arith_op) | fd(d, w)  | op3(fpop1_op3) | fs1(s1, w)  | opf(0x4c + w)         | fs2(s2, w)); }

  // pp 164

  void fsqrt( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d ) { emit_long( op(arith_op) | fd(d, w) | op3(fpop1_op3) | opf(0x28 + w) | fs2(s, w)); }

  // pp 165

  inline void flush( Register s1, Register s2 );
  inline void flush( Register s1, int simm13a);

  // pp 167

  void flushw() { v9_only();  emit_long( op(arith_op) | op3(flushw_op3) ); }

  // pp 168

  void illtrap( int const22a) { if (const22a != 0) v9_only();  emit_long( op(branch_op) | u_field(const22a, 21, 0) ); }
  // v8 unimp == illtrap(0)

  // pp 169

  void impdep1( int id1, int const19a ) { v9_only();  emit_long( op(arith_op) | fcn(id1) | op3(impdep1_op3) | u_field(const19a, 18, 0)); }
  void impdep2( int id1, int const19a ) { v9_only();  emit_long( op(arith_op) | fcn(id1) | op3(impdep2_op3) | u_field(const19a, 18, 0)); }

  // pp 149 (v8)

  void cpop1( int opc, int cr1, int cr2, int crd ) { v8_only();  emit_long( op(arith_op) | fcn(crd) | op3(impdep1_op3) | u_field(cr1, 18, 14) | opf(opc) | u_field(cr2, 4, 0)); }
  void cpop2( int opc, int cr1, int cr2, int crd ) { v8_only();  emit_long( op(arith_op) | fcn(crd) | op3(impdep2_op3) | u_field(cr1, 18, 14) | opf(opc) | u_field(cr2, 4, 0)); }

  // pp 170

  void jmpl( Register s1, Register s2, Register d );
  void jmpl( Register s1, int simm13a, Register d, RelocationHolder const& rspec = RelocationHolder() );

  // 171

  inline void ldf(FloatRegisterImpl::Width w, Register s1, RegisterOrConstant s2, FloatRegister d);
  inline void ldf(FloatRegisterImpl::Width w, Register s1, Register s2, FloatRegister d);
  inline void ldf(FloatRegisterImpl::Width w, Register s1, int simm13a, FloatRegister d, RelocationHolder const& rspec = RelocationHolder());

  inline void ldf(FloatRegisterImpl::Width w, const Address& a, FloatRegister d, int offset = 0);


  inline void ldfsr(  Register s1, Register s2 );
  inline void ldfsr(  Register s1, int simm13a);
  inline void ldxfsr( Register s1, Register s2 );
  inline void ldxfsr( Register s1, int simm13a);

  // pp 94 (v8)

  inline void ldc(   Register s1, Register s2, int crd );
  inline void ldc(   Register s1, int simm13a, int crd);
  inline void lddc(  Register s1, Register s2, int crd );
  inline void lddc(  Register s1, int simm13a, int crd);
  inline void ldcsr( Register s1, Register s2, int crd );
  inline void ldcsr( Register s1, int simm13a, int crd);


  // 173

  void ldfa(  FloatRegisterImpl::Width w, Register s1, Register s2, int ia, FloatRegister d ) { v9_only();  emit_long( op(ldst_op) | fd(d, w) | alt_op3(ldf_op3 | alt_bit_op3, w) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldfa(  FloatRegisterImpl::Width w, Register s1, int simm13a,         FloatRegister d ) { v9_only();  emit_long( op(ldst_op) | fd(d, w) | alt_op3(ldf_op3 | alt_bit_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 175, lduw is ld on v8

  inline void ldsb(  Register s1, Register s2, Register d );
  inline void ldsb(  Register s1, int simm13a, Register d);
  inline void ldsh(  Register s1, Register s2, Register d );
  inline void ldsh(  Register s1, int simm13a, Register d);
  inline void ldsw(  Register s1, Register s2, Register d );
  inline void ldsw(  Register s1, int simm13a, Register d);
  inline void ldub(  Register s1, Register s2, Register d );
  inline void ldub(  Register s1, int simm13a, Register d);
  inline void lduh(  Register s1, Register s2, Register d );
  inline void lduh(  Register s1, int simm13a, Register d);
  inline void lduw(  Register s1, Register s2, Register d );
  inline void lduw(  Register s1, int simm13a, Register d);
  inline void ldx(   Register s1, Register s2, Register d );
  inline void ldx(   Register s1, int simm13a, Register d);
  inline void ld(    Register s1, Register s2, Register d );
  inline void ld(    Register s1, int simm13a, Register d);
  inline void ldd(   Register s1, Register s2, Register d );
  inline void ldd(   Register s1, int simm13a, Register d);

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  inline void ld(    Register s1, ByteSize simm13a, Register d);
#endif

  inline void ldsb(const Address& a, Register d, int offset = 0);
  inline void ldsh(const Address& a, Register d, int offset = 0);
  inline void ldsw(const Address& a, Register d, int offset = 0);
  inline void ldub(const Address& a, Register d, int offset = 0);
  inline void lduh(const Address& a, Register d, int offset = 0);
  inline void lduw(const Address& a, Register d, int offset = 0);
  inline void ldx( const Address& a, Register d, int offset = 0);
  inline void ld(  const Address& a, Register d, int offset = 0);
  inline void ldd( const Address& a, Register d, int offset = 0);

  inline void ldub(  Register s1, RegisterOrConstant s2, Register d );
  inline void ldsb(  Register s1, RegisterOrConstant s2, Register d );
  inline void lduh(  Register s1, RegisterOrConstant s2, Register d );
  inline void ldsh(  Register s1, RegisterOrConstant s2, Register d );
  inline void lduw(  Register s1, RegisterOrConstant s2, Register d );
  inline void ldsw(  Register s1, RegisterOrConstant s2, Register d );
  inline void ldx(   Register s1, RegisterOrConstant s2, Register d );
  inline void ld(    Register s1, RegisterOrConstant s2, Register d );
  inline void ldd(   Register s1, RegisterOrConstant s2, Register d );

  // pp 177

  void ldsba(  Register s1, Register s2, int ia, Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldsb_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldsba(  Register s1, int simm13a,         Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldsb_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void ldsha(  Register s1, Register s2, int ia, Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldsh_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldsha(  Register s1, int simm13a,         Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldsh_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void ldswa(  Register s1, Register s2, int ia, Register d ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(ldsw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldswa(  Register s1, int simm13a,         Register d ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(ldsw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void lduba(  Register s1, Register s2, int ia, Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldub_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void lduba(  Register s1, int simm13a,         Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(ldub_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void lduha(  Register s1, Register s2, int ia, Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(lduh_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void lduha(  Register s1, int simm13a,         Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(lduh_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void lduwa(  Register s1, Register s2, int ia, Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(lduw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void lduwa(  Register s1, int simm13a,         Register d ) {             emit_long( op(ldst_op) | rd(d) | op3(lduw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void ldxa(   Register s1, Register s2, int ia, Register d ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(ldx_op3  | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldxa(   Register s1, int simm13a,         Register d ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(ldx_op3  | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void ldda(   Register s1, Register s2, int ia, Register d ) { v9_dep();   emit_long( op(ldst_op) | rd(d) | op3(ldd_op3  | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldda(   Register s1, int simm13a,         Register d ) { v9_dep();   emit_long( op(ldst_op) | rd(d) | op3(ldd_op3  | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 179

  inline void ldstub(  Register s1, Register s2, Register d );
  inline void ldstub(  Register s1, int simm13a, Register d);

  // pp 180

  void ldstuba( Register s1, Register s2, int ia, Register d ) { emit_long( op(ldst_op) | rd(d) | op3(ldstub_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void ldstuba( Register s1, int simm13a,         Register d ) { emit_long( op(ldst_op) | rd(d) | op3(ldstub_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 181

  void and3(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(and_op3              ) | rs1(s1) | rs2(s2) ); }
  void and3(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(and_op3              ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void andcc(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(and_op3  | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void andcc(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(and_op3  | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void andn(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(andn_op3             ) | rs1(s1) | rs2(s2) ); }
  void andn(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(andn_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void andn(    Register s1, RegisterOrConstant s2, Register d);
  void andncc(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(andn_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void andncc(  Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(andn_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void or3(     Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(or_op3               ) | rs1(s1) | rs2(s2) ); }
  void or3(     Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(or_op3               ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void orcc(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(or_op3   | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void orcc(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(or_op3   | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void orn(     Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(orn_op3) | rs1(s1) | rs2(s2) ); }
  void orn(     Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(orn_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void orncc(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(orn_op3  | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void orncc(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(orn_op3  | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void xor3(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xor_op3              ) | rs1(s1) | rs2(s2) ); }
  void xor3(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xor_op3              ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void xorcc(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xor_op3  | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void xorcc(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xor_op3  | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void xnor(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xnor_op3             ) | rs1(s1) | rs2(s2) ); }
  void xnor(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xnor_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void xnorcc(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xnor_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void xnorcc(  Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(xnor_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 183

  void membar( Membar_mask_bits const7a ) { v9_only(); emit_long( op(arith_op) | op3(membar_op3) | rs1(O7) | immed(true) | u_field( int(const7a), 6, 0)); }

  // pp 185

  void fmov( FloatRegisterImpl::Width w, Condition c,  bool floatCC, CC cca, FloatRegister s2, FloatRegister d ) { v9_only();  emit_long( op(arith_op) | fd(d, w) | op3(fpop2_op3) | cond_mov(c) | opf_cc(cca, floatCC) | opf_low6(w) | fs2(s2, w)); }

  // pp 189

  void fmov( FloatRegisterImpl::Width w, RCondition c, Register s1,  FloatRegister s2, FloatRegister d ) { v9_only();  emit_long( op(arith_op) | fd(d, w) | op3(fpop2_op3) | rs1(s1) | rcond(c) | opf_low5(4 + w) | fs2(s2, w)); }

  // pp 191

  void movcc( Condition c, bool floatCC, CC cca, Register s2, Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(movcc_op3) | mov_cc(cca, floatCC) | cond_mov(c) | rs2(s2) ); }
  void movcc( Condition c, bool floatCC, CC cca, int simm11a, Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(movcc_op3) | mov_cc(cca, floatCC) | cond_mov(c) | immed(true) | simm(simm11a, 11) ); }

  // pp 195

  void movr( RCondition c, Register s1, Register s2,  Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(movr_op3) | rs1(s1) | rcond(c) | rs2(s2) ); }
  void movr( RCondition c, Register s1, int simm10a,  Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(movr_op3) | rs1(s1) | rcond(c) | immed(true) | simm(simm10a, 10) ); }

  // pp 196

  void mulx(  Register s1, Register s2, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(mulx_op3 ) | rs1(s1) | rs2(s2) ); }
  void mulx(  Register s1, int simm13a, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(mulx_op3 ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void sdivx( Register s1, Register s2, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(sdivx_op3) | rs1(s1) | rs2(s2) ); }
  void sdivx( Register s1, int simm13a, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(sdivx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void udivx( Register s1, Register s2, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(udivx_op3) | rs1(s1) | rs2(s2) ); }
  void udivx( Register s1, int simm13a, Register d ) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(udivx_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 197

  void umul(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(umul_op3             ) | rs1(s1) | rs2(s2) ); }
  void umul(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(umul_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void smul(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(smul_op3             ) | rs1(s1) | rs2(s2) ); }
  void smul(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(smul_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void umulcc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(umul_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void umulcc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(umul_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void smulcc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(smul_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void smulcc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(smul_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 199

  void mulscc(   Register s1, Register s2, Register d ) { v9_dep();  emit_long( op(arith_op) | rd(d) | op3(mulscc_op3) | rs1(s1) | rs2(s2) ); }
  void mulscc(   Register s1, int simm13a, Register d ) { v9_dep();  emit_long( op(arith_op) | rd(d) | op3(mulscc_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 201

  void nop() { emit_long( op(branch_op) | op2(sethi_op2) ); }


  // pp 202

  void popc( Register s,  Register d) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(popc_op3) | rs2(s)); }
  void popc( int simm13a, Register d) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(popc_op3) | immed(true) | simm(simm13a, 13)); }

  // pp 203

  void prefetch(   Register s1, Register s2,         PrefetchFcn f);
  void prefetch(   Register s1, int simm13a,         PrefetchFcn f);
  void prefetcha(  Register s1, Register s2, int ia, PrefetchFcn f ) { v9_only();  emit_long( op(ldst_op) | fcn(f) | op3(prefetch_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void prefetcha(  Register s1, int simm13a,         PrefetchFcn f ) { v9_only();  emit_long( op(ldst_op) | fcn(f) | op3(prefetch_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  inline void prefetch(const Address& a, PrefetchFcn F, int offset = 0);

  // pp 208

  // not implementing read privileged register

  inline void rdy(    Register d) { v9_dep();  emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(0, 18, 14)); }
  inline void rdccr(  Register d) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(2, 18, 14)); }
  inline void rdasi(  Register d) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(3, 18, 14)); }
  inline void rdtick( Register d) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(4, 18, 14)); } // Spoon!
  inline void rdpc(   Register d) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(5, 18, 14)); }
  inline void rdfprs( Register d) { v9_only(); emit_long( op(arith_op) | rd(d) | op3(rdreg_op3) | u_field(6, 18, 14)); }

  // pp 213

  inline void rett( Register s1, Register s2);
  inline void rett( Register s1, int simm13a, relocInfo::relocType rt = relocInfo::none);

  // pp 214

  void save(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(save_op3) | rs1(s1) | rs2(s2) ); }
  void save(    Register s1, int simm13a, Register d ) {
    // make sure frame is at least large enough for the register save area
    assert(-simm13a >= 16 * wordSize, "frame too small");
    emit_long( op(arith_op) | rd(d) | op3(save_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) );
  }

  void restore( Register s1 = G0,  Register s2 = G0, Register d = G0 ) { emit_long( op(arith_op) | rd(d) | op3(restore_op3) | rs1(s1) | rs2(s2) ); }
  void restore( Register s1,       int simm13a,      Register d      ) { emit_long( op(arith_op) | rd(d) | op3(restore_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 216

  void saved()    { v9_only();  emit_long( op(arith_op) | fcn(0) | op3(saved_op3)); }
  void restored() { v9_only();  emit_long( op(arith_op) | fcn(1) | op3(saved_op3)); }

  // pp 217

  inline void sethi( int imm22a, Register d, RelocationHolder const& rspec = RelocationHolder() );
  // pp 218

  void sll(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(0) | rs2(s2) ); }
  void sll(  Register s1, int imm5a,   Register d ) { emit_long( op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0) ); }
  void srl(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(0) | rs2(s2) ); }
  void srl(  Register s1, int imm5a,   Register d ) { emit_long( op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0) ); }
  void sra(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(0) | rs2(s2) ); }
  void sra(  Register s1, int imm5a,   Register d ) { emit_long( op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(0) | immed(true) | u_field(imm5a, 4, 0) ); }

  void sllx( Register s1, Register s2, Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(1) | rs2(s2) ); }
  void sllx( Register s1, int imm6a,   Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(sll_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0) ); }
  void srlx( Register s1, Register s2, Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(1) | rs2(s2) ); }
  void srlx( Register s1, int imm6a,   Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(srl_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0) ); }
  void srax( Register s1, Register s2, Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(1) | rs2(s2) ); }
  void srax( Register s1, int imm6a,   Register d ) { v9_only();  emit_long( op(arith_op) | rd(d) | op3(sra_op3) | rs1(s1) | sx(1) | immed(true) | u_field(imm6a, 5, 0) ); }

  // pp 220

  void sir( int simm13a ) { emit_long( op(arith_op) | fcn(15) | op3(sir_op3) | immed(true) | simm(simm13a, 13)); }

  // pp 221

  void stbar() { emit_long( op(arith_op) | op3(membar_op3) | u_field(15, 18, 14)); }

  // pp 222

  inline void stf(    FloatRegisterImpl::Width w, FloatRegister d, Register s1, RegisterOrConstant s2);
  inline void stf(    FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2);
  inline void stf(    FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a);
  inline void stf(    FloatRegisterImpl::Width w, FloatRegister d, const Address& a, int offset = 0);

  inline void stfsr(  Register s1, Register s2 );
  inline void stfsr(  Register s1, int simm13a);
  inline void stxfsr( Register s1, Register s2 );
  inline void stxfsr( Register s1, int simm13a);

  //  pp 224

  void stfa(  FloatRegisterImpl::Width w, FloatRegister d, Register s1, Register s2, int ia ) { v9_only();  emit_long( op(ldst_op) | fd(d, w) | alt_op3(stf_op3 | alt_bit_op3, w) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stfa(  FloatRegisterImpl::Width w, FloatRegister d, Register s1, int simm13a         ) { v9_only();  emit_long( op(ldst_op) | fd(d, w) | alt_op3(stf_op3 | alt_bit_op3, w) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // p 226

  inline void stb(  Register d, Register s1, Register s2 );
  inline void stb(  Register d, Register s1, int simm13a);
  inline void sth(  Register d, Register s1, Register s2 );
  inline void sth(  Register d, Register s1, int simm13a);
  inline void stw(  Register d, Register s1, Register s2 );
  inline void stw(  Register d, Register s1, int simm13a);
  inline void st(   Register d, Register s1, Register s2 );
  inline void st(   Register d, Register s1, int simm13a);
  inline void stx(  Register d, Register s1, Register s2 );
  inline void stx(  Register d, Register s1, int simm13a);
  inline void std(  Register d, Register s1, Register s2 );
  inline void std(  Register d, Register s1, int simm13a);

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  inline void st(   Register d, Register s1, ByteSize simm13a);
#endif

  inline void stb(  Register d, const Address& a, int offset = 0 );
  inline void sth(  Register d, const Address& a, int offset = 0 );
  inline void stw(  Register d, const Address& a, int offset = 0 );
  inline void stx(  Register d, const Address& a, int offset = 0 );
  inline void st(   Register d, const Address& a, int offset = 0 );
  inline void std(  Register d, const Address& a, int offset = 0 );

  inline void stb(  Register d, Register s1, RegisterOrConstant s2 );
  inline void sth(  Register d, Register s1, RegisterOrConstant s2 );
  inline void stw(  Register d, Register s1, RegisterOrConstant s2 );
  inline void stx(  Register d, Register s1, RegisterOrConstant s2 );
  inline void std(  Register d, Register s1, RegisterOrConstant s2 );
  inline void st(   Register d, Register s1, RegisterOrConstant s2 );

  // pp 177

  void stba(  Register d, Register s1, Register s2, int ia ) {             emit_long( op(ldst_op) | rd(d) | op3(stb_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stba(  Register d, Register s1, int simm13a         ) {             emit_long( op(ldst_op) | rd(d) | op3(stb_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void stha(  Register d, Register s1, Register s2, int ia ) {             emit_long( op(ldst_op) | rd(d) | op3(sth_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stha(  Register d, Register s1, int simm13a         ) {             emit_long( op(ldst_op) | rd(d) | op3(sth_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void stwa(  Register d, Register s1, Register s2, int ia ) {             emit_long( op(ldst_op) | rd(d) | op3(stw_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stwa(  Register d, Register s1, int simm13a         ) {             emit_long( op(ldst_op) | rd(d) | op3(stw_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void stxa(  Register d, Register s1, Register s2, int ia ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(stx_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stxa(  Register d, Register s1, int simm13a         ) { v9_only();  emit_long( op(ldst_op) | rd(d) | op3(stx_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void stda(  Register d, Register s1, Register s2, int ia ) {             emit_long( op(ldst_op) | rd(d) | op3(std_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void stda(  Register d, Register s1, int simm13a         ) {             emit_long( op(ldst_op) | rd(d) | op3(std_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 97 (v8)

  inline void stc(   int crd, Register s1, Register s2 );
  inline void stc(   int crd, Register s1, int simm13a);
  inline void stdc(  int crd, Register s1, Register s2 );
  inline void stdc(  int crd, Register s1, int simm13a);
  inline void stcsr( int crd, Register s1, Register s2 );
  inline void stcsr( int crd, Register s1, int simm13a);
  inline void stdcq( int crd, Register s1, Register s2 );
  inline void stdcq( int crd, Register s1, int simm13a);

  // pp 230

  void sub(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sub_op3              ) | rs1(s1) | rs2(s2) ); }
  void sub(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sub_op3              ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void subcc(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sub_op3 | cc_bit_op3 ) | rs1(s1) | rs2(s2) ); }
  void subcc(  Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(sub_op3 | cc_bit_op3 ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void subc(   Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(subc_op3             ) | rs1(s1) | rs2(s2) ); }
  void subc(   Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(subc_op3             ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void subccc( Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(subc_op3 | cc_bit_op3) | rs1(s1) | rs2(s2) ); }
  void subccc( Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(subc_op3 | cc_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 231

  inline void swap( Register s1, Register s2, Register d );
  inline void swap( Register s1, int simm13a, Register d);
  inline void swap( Address& a,               Register d, int offset = 0 );

  // pp 232

  void swapa(   Register s1, Register s2, int ia, Register d ) { v9_dep();  emit_long( op(ldst_op) | rd(d) | op3(swap_op3 | alt_bit_op3) | rs1(s1) | imm_asi(ia) | rs2(s2) ); }
  void swapa(   Register s1, int simm13a,         Register d ) { v9_dep();  emit_long( op(ldst_op) | rd(d) | op3(swap_op3 | alt_bit_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 234, note op in book is wrong, see pp 268

  void taddcc(    Register s1, Register s2, Register d ) {            emit_long( op(arith_op) | rd(d) | op3(taddcc_op3  ) | rs1(s1) | rs2(s2) ); }
  void taddcc(    Register s1, int simm13a, Register d ) {            emit_long( op(arith_op) | rd(d) | op3(taddcc_op3  ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void taddcctv(  Register s1, Register s2, Register d ) { v9_dep();  emit_long( op(arith_op) | rd(d) | op3(taddcctv_op3) | rs1(s1) | rs2(s2) ); }
  void taddcctv(  Register s1, int simm13a, Register d ) { v9_dep();  emit_long( op(arith_op) | rd(d) | op3(taddcctv_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 235

  void tsubcc(    Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(tsubcc_op3  ) | rs1(s1) | rs2(s2) ); }
  void tsubcc(    Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(tsubcc_op3  ) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }
  void tsubcctv(  Register s1, Register s2, Register d ) { emit_long( op(arith_op) | rd(d) | op3(tsubcctv_op3) | rs1(s1) | rs2(s2) ); }
  void tsubcctv(  Register s1, int simm13a, Register d ) { emit_long( op(arith_op) | rd(d) | op3(tsubcctv_op3) | rs1(s1) | immed(true) | simm(simm13a, 13) ); }

  // pp 237

  void trap( Condition c, CC cc, Register s1, Register s2 ) { v8_no_cc(cc);  emit_long( op(arith_op) | cond(c) | op3(trap_op3) | rs1(s1) | trapcc(cc) | rs2(s2)); }
  void trap( Condition c, CC cc, Register s1, int trapa   ) { v8_no_cc(cc);  emit_long( op(arith_op) | cond(c) | op3(trap_op3) | rs1(s1) | trapcc(cc) | immed(true) | u_field(trapa, 6, 0)); }
  // simple uncond. trap
  void trap( int trapa ) { trap( always, icc, G0, trapa ); }

  // pp 239 omit write priv register for now

  inline void wry(    Register d) { v9_dep();  emit_long( op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(0, 29, 25)); }
  inline void wrccr(Register s) { v9_only(); emit_long( op(arith_op) | rs1(s) | op3(wrreg_op3) | u_field(2, 29, 25)); }
  inline void wrccr(Register s, int simm13a) { v9_only(); emit_long( op(arith_op) |
                                                                           rs1(s) |
                                                                           op3(wrreg_op3) |
                                                                           u_field(2, 29, 25) |
                                                                           u_field(1, 13, 13) |
                                                                           simm(simm13a, 13)); }
  inline void wrasi(  Register d) { v9_only(); emit_long( op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(3, 29, 25)); }
  inline void wrfprs( Register d) { v9_only(); emit_long( op(arith_op) | rs1(d) | op3(wrreg_op3) | u_field(6, 29, 25)); }

  // For a given register condition, return the appropriate condition code
  // Condition (the one you would use to get the same effect after "tst" on
  // the target register.)
  Assembler::Condition reg_cond_to_cc_cond(RCondition in);


  // Creation
  Assembler(CodeBuffer* code) : AbstractAssembler(code) {
#ifdef CHECK_DELAY
    delay_state = no_delay;
#endif
  }

  // Testing
#ifndef PRODUCT
  void test_v9();
  void test_v8_onlys();
#endif
};


class RegistersForDebugging : public StackObj {
 public:
  intptr_t i[8], l[8], o[8], g[8];
  float    f[32];
  double   d[32];

  void print(outputStream* s);

  static int i_offset(int j) { return offset_of(RegistersForDebugging, i[j]); }
  static int l_offset(int j) { return offset_of(RegistersForDebugging, l[j]); }
  static int o_offset(int j) { return offset_of(RegistersForDebugging, o[j]); }
  static int g_offset(int j) { return offset_of(RegistersForDebugging, g[j]); }
  static int f_offset(int j) { return offset_of(RegistersForDebugging, f[j]); }
  static int d_offset(int j) { return offset_of(RegistersForDebugging, d[j / 2]); }

  // gen asm code to save regs
  static void save_registers(MacroAssembler* a);

  // restore global registers in case C code disturbed them
  static void restore_registers(MacroAssembler* a, Register r);


};


// MacroAssembler extends Assembler by a few frequently used macros.
//
// Most of the standard SPARC synthetic ops are defined here.
// Instructions for which a 'better' code sequence exists depending
// on arguments should also go in here.

#define JMP2(r1, r2) jmp(r1, r2, __FILE__, __LINE__)
#define JMP(r1, off) jmp(r1, off, __FILE__, __LINE__)
#define JUMP(a, temp, off)     jump(a, temp, off, __FILE__, __LINE__)
#define JUMPL(a, temp, d, off) jumpl(a, temp, d, off, __FILE__, __LINE__)


class MacroAssembler: public Assembler {
 protected:
  // Support for VM calls
  // This is the base routine called by the different versions of call_VM_leaf. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
#ifdef CC_INTERP
  #define VIRTUAL
#else
  #define VIRTUAL virtual
#endif

  VIRTUAL void call_VM_leaf_base(Register thread_cache, address entry_point, int number_of_arguments);

  //
  // It is imperative that all calls into the VM are handled via the call_VM macros.
  // They make sure that the stack linkage is setup correctly. call_VM's correspond
  // to ENTRY/ENTRY_X entry points while call_VM_leaf's correspond to LEAF entry points.
  //
  // This is the base routine called by the different versions of call_VM. The interpreter
  // may customize this version by overriding it for its purposes (e.g., to save/restore
  // additional registers when doing a VM call).
  //
  // A non-volatile java_thread_cache register should be specified so
  // that the G2_thread value can be preserved across the call.
  // (If java_thread_cache is noreg, then a slow get_thread call
  // will re-initialize the G2_thread.) call_VM_base returns the register that contains the
  // thread.
  //
  // If no last_java_sp is specified (noreg) than SP will be used instead.

  virtual void call_VM_base(
    Register        oop_result,             // where an oop-result ends up if any; use noreg otherwise
    Register        java_thread_cache,      // the thread if computed before     ; use noreg otherwise
    Register        last_java_sp,           // to set up last_Java_frame in stubs; use noreg otherwise
    address         entry_point,            // the entry point
    int             number_of_arguments,    // the number of arguments (w/o thread) to pop after call
    bool            check_exception=true    // flag which indicates if exception should be checked
  );

  // This routine should emit JVMTI PopFrame and ForceEarlyReturn handling code.
  // The implementation is only non-empty for the InterpreterMacroAssembler,
  // as only the interpreter handles and ForceEarlyReturn PopFrame requests.
  virtual void check_and_handle_popframe(Register scratch_reg);
  virtual void check_and_handle_earlyret(Register scratch_reg);

 public:
  MacroAssembler(CodeBuffer* code) : Assembler(code) {}

  // Support for NULL-checks
  //
  // Generates code that causes a NULL OS exception if the content of reg is NULL.
  // If the accessed location is M[reg + offset] and the offset is known, provide the
  // offset.  No explicit code generation is needed if the offset is within a certain
  // range (0 <= offset <= page_size).
  //
  // %%%%%% Currently not done for SPARC

  void null_check(Register reg, int offset = -1);
  static bool needs_explicit_null_check(intptr_t offset);

  // support for delayed instructions
  MacroAssembler* delayed() { Assembler::delayed();  return this; }

  // branches that use right instruction for v8 vs. v9
  inline void br( Condition c, bool a, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void br( Condition c, bool a, Predict p, Label& L );
  inline void fb( Condition c, bool a, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void fb( Condition c, bool a, Predict p, Label& L );

  // compares register with zero and branches (V9 and V8 instructions)
  void br_zero( Condition c, bool a, Predict p, Register s1, Label& L);
  // Compares a pointer register with zero and branches on (not)null.
  // Does a test & branch on 32-bit systems and a register-branch on 64-bit.
  void br_null   ( Register s1, bool a, Predict p, Label& L );
  void br_notnull( Register s1, bool a, Predict p, Label& L );

  // These versions will do the most efficient thing on v8 and v9.  Perhaps
  // this is what the routine above was meant to do, but it didn't (and
  // didn't cover both target address kinds.)
  void br_on_reg_cond( RCondition c, bool a, Predict p, Register s1, address d, relocInfo::relocType rt = relocInfo::none );
  void br_on_reg_cond( RCondition c, bool a, Predict p, Register s1, Label& L);

  inline void bp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void bp( Condition c, bool a, CC cc, Predict p, Label& L );

  // Branch that tests xcc in LP64 and icc in !LP64
  inline void brx( Condition c, bool a, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void brx( Condition c, bool a, Predict p, Label& L );

  // unconditional short branch
  inline void ba( bool a, Label& L );

  // Branch that tests fp condition codes
  inline void fbp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void fbp( Condition c, bool a, CC cc, Predict p, Label& L );

  // get PC the best way
  inline int get_pc( Register d );

  // Sparc shorthands(pp 85, V8 manual, pp 289 V9 manual)
  inline void cmp(  Register s1, Register s2 ) { subcc( s1, s2, G0 ); }
  inline void cmp(  Register s1, int simm13a ) { subcc( s1, simm13a, G0 ); }

  inline void jmp( Register s1, Register s2 );
  inline void jmp( Register s1, int simm13a, RelocationHolder const& rspec = RelocationHolder() );

  inline void call( address d,  relocInfo::relocType rt = relocInfo::runtime_call_type );
  inline void call( Label& L,   relocInfo::relocType rt = relocInfo::runtime_call_type );
  inline void callr( Register s1, Register s2 );
  inline void callr( Register s1, int simm13a, RelocationHolder const& rspec = RelocationHolder() );

  // Emits nothing on V8
  inline void iprefetch( address d, relocInfo::relocType rt = relocInfo::none );
  inline void iprefetch( Label& L);

  inline void tst( Register s ) { orcc( G0, s, G0 ); }

#ifdef PRODUCT
  inline void ret(  bool trace = TraceJumps )   { if (trace) {
                                                    mov(I7, O7); // traceable register
                                                    JMP(O7, 2 * BytesPerInstWord);
                                                  } else {
                                                    jmpl( I7, 2 * BytesPerInstWord, G0 );
                                                  }
                                                }

  inline void retl( bool trace = TraceJumps )  { if (trace) JMP(O7, 2 * BytesPerInstWord);
                                                 else jmpl( O7, 2 * BytesPerInstWord, G0 ); }
#else
  void ret(  bool trace = TraceJumps );
  void retl( bool trace = TraceJumps );
#endif /* PRODUCT */

  // Required platform-specific helpers for Label::patch_instructions.
  // They _shadow_ the declarations in AbstractAssembler, which are undefined.
  void pd_patch_instruction(address branch, address target);
#ifndef PRODUCT
  static void pd_print_patched_instruction(address branch);
#endif

  // sethi Macro handles optimizations and relocations
private:
  void internal_sethi(const AddressLiteral& addrlit, Register d, bool ForceRelocatable);
public:
  void sethi(const AddressLiteral& addrlit, Register d);
  void patchable_sethi(const AddressLiteral& addrlit, Register d);

  // compute the size of a sethi/set
  static int  size_of_sethi( address a, bool worst_case = false );
  static int  worst_case_size_of_set();

  // set may be either setsw or setuw (high 32 bits may be zero or sign)
private:
  void internal_set(const AddressLiteral& al, Register d, bool ForceRelocatable);
public:
  void set(const AddressLiteral& addrlit, Register d);
  void set(intptr_t value, Register d);
  void set(address addr, Register d, RelocationHolder const& rspec);
  void patchable_set(const AddressLiteral& addrlit, Register d);
  void patchable_set(intptr_t value, Register d);
  void set64(jlong value, Register d, Register tmp);

  // sign-extend 32 to 64
  inline void signx( Register s, Register d ) { sra( s, G0, d); }
  inline void signx( Register d )             { sra( d, G0, d); }

  inline void not1( Register s, Register d ) { xnor( s, G0, d ); }
  inline void not1( Register d )             { xnor( d, G0, d ); }

  inline void neg( Register s, Register d ) { sub( G0, s, d ); }
  inline void neg( Register d )             { sub( G0, d, d ); }

  inline void cas(  Register s1, Register s2, Register d) { casa( s1, s2, d, ASI_PRIMARY); }
  inline void casx( Register s1, Register s2, Register d) { casxa(s1, s2, d, ASI_PRIMARY); }
  // Functions for isolating 64 bit atomic swaps for LP64
  // cas_ptr will perform cas for 32 bit VM's and casx for 64 bit VM's
  inline void cas_ptr(  Register s1, Register s2, Register d) {
#ifdef _LP64
    casx( s1, s2, d );
#else
    cas( s1, s2, d );
#endif
  }

  // Functions for isolating 64 bit shifts for LP64
  inline void sll_ptr( Register s1, Register s2, Register d );
  inline void sll_ptr( Register s1, int imm6a,   Register d );
  inline void sll_ptr( Register s1, RegisterOrConstant s2, Register d );
  inline void srl_ptr( Register s1, Register s2, Register d );
  inline void srl_ptr( Register s1, int imm6a,   Register d );

  // little-endian
  inline void casl(  Register s1, Register s2, Register d) { casa( s1, s2, d, ASI_PRIMARY_LITTLE); }
  inline void casxl( Register s1, Register s2, Register d) { casxa(s1, s2, d, ASI_PRIMARY_LITTLE); }

  inline void inc(   Register d,  int const13 = 1 ) { add(   d, const13, d); }
  inline void inccc( Register d,  int const13 = 1 ) { addcc( d, const13, d); }

  inline void dec(   Register d,  int const13 = 1 ) { sub(   d, const13, d); }
  inline void deccc( Register d,  int const13 = 1 ) { subcc( d, const13, d); }

  inline void btst( Register s1,  Register s2 ) { andcc( s1, s2, G0 ); }
  inline void btst( int simm13a,  Register s )  { andcc( s,  simm13a, G0 ); }

  inline void bset( Register s1,  Register s2 ) { or3( s1, s2, s2 ); }
  inline void bset( int simm13a,  Register s )  { or3( s,  simm13a, s ); }

  inline void bclr( Register s1,  Register s2 ) { andn( s1, s2, s2 ); }
  inline void bclr( int simm13a,  Register s )  { andn( s,  simm13a, s ); }

  inline void btog( Register s1,  Register s2 ) { xor3( s1, s2, s2 ); }
  inline void btog( int simm13a,  Register s )  { xor3( s,  simm13a, s ); }

  inline void clr( Register d ) { or3( G0, G0, d ); }

  inline void clrb( Register s1, Register s2);
  inline void clrh( Register s1, Register s2);
  inline void clr(  Register s1, Register s2);
  inline void clrx( Register s1, Register s2);

  inline void clrb( Register s1, int simm13a);
  inline void clrh( Register s1, int simm13a);
  inline void clr(  Register s1, int simm13a);
  inline void clrx( Register s1, int simm13a);

  // copy & clear upper word
  inline void clruw( Register s, Register d ) { srl( s, G0, d); }
  // clear upper word
  inline void clruwu( Register d ) { srl( d, G0, d); }

  // membar psuedo instruction.  takes into account target memory model.
  inline void membar( Assembler::Membar_mask_bits const7a );

  // returns if membar generates anything.
  inline bool membar_has_effect( Assembler::Membar_mask_bits const7a );

  // mov pseudo instructions
  inline void mov( Register s,  Register d) {
    if ( s != d )    or3( G0, s, d);
    else             assert_not_delayed();  // Put something useful in the delay slot!
  }

  inline void mov_or_nop( Register s,  Register d) {
    if ( s != d )    or3( G0, s, d);
    else             nop();
  }

  inline void mov( int simm13a, Register d) { or3( G0, simm13a, d); }

  // address pseudos: make these names unlike instruction names to avoid confusion
  inline intptr_t load_pc_address( Register reg, int bytes_to_skip );
  inline void load_contents(const AddressLiteral& addrlit, Register d, int offset = 0);
  inline void load_ptr_contents(const AddressLiteral& addrlit, Register d, int offset = 0);
  inline void store_contents(Register s, const AddressLiteral& addrlit, Register temp, int offset = 0);
  inline void store_ptr_contents(Register s, const AddressLiteral& addrlit, Register temp, int offset = 0);
  inline void jumpl_to(const AddressLiteral& addrlit, Register temp, Register d, int offset = 0);
  inline void jump_to(const AddressLiteral& addrlit, Register temp, int offset = 0);
  inline void jump_indirect_to(Address& a, Register temp, int ld_offset = 0, int jmp_offset = 0);

  // ring buffer traceable jumps

  void jmp2( Register r1, Register r2, const char* file, int line );
  void jmp ( Register r1, int offset,  const char* file, int line );

  void jumpl(const AddressLiteral& addrlit, Register temp, Register d, int offset, const char* file, int line);
  void jump (const AddressLiteral& addrlit, Register temp,             int offset, const char* file, int line);


  // argument pseudos:

  inline void load_argument( Argument& a, Register  d );
  inline void store_argument( Register s, Argument& a );
  inline void store_ptr_argument( Register s, Argument& a );
  inline void store_float_argument( FloatRegister s, Argument& a );
  inline void store_double_argument( FloatRegister s, Argument& a );
  inline void store_long_argument( Register s, Argument& a );

  // handy macros:

  inline void round_to( Register r, int modulus ) {
    assert_not_delayed();
    inc( r, modulus - 1 );
    and3( r, -modulus, r );
  }

  // --------------------------------------------------

  // Functions for isolating 64 bit loads for LP64
  // ld_ptr will perform ld for 32 bit VM's and ldx for 64 bit VM's
  // st_ptr will perform st for 32 bit VM's and stx for 64 bit VM's
  inline void ld_ptr(Register s1, Register s2, Register d);
  inline void ld_ptr(Register s1, int simm13a, Register d);
  inline void ld_ptr(Register s1, RegisterOrConstant s2, Register d);
  inline void ld_ptr(const Address& a, Register d, int offset = 0);
  inline void st_ptr(Register d, Register s1, Register s2);
  inline void st_ptr(Register d, Register s1, int simm13a);
  inline void st_ptr(Register d, Register s1, RegisterOrConstant s2);
  inline void st_ptr(Register d, const Address& a, int offset = 0);

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  inline void ld_ptr(Register s1, ByteSize simm13a, Register d);
  inline void st_ptr(Register d, Register s1, ByteSize simm13a);
#endif

  // ld_long will perform ldd for 32 bit VM's and ldx for 64 bit VM's
  // st_long will perform std for 32 bit VM's and stx for 64 bit VM's
  inline void ld_long(Register s1, Register s2, Register d);
  inline void ld_long(Register s1, int simm13a, Register d);
  inline void ld_long(Register s1, RegisterOrConstant s2, Register d);
  inline void ld_long(const Address& a, Register d, int offset = 0);
  inline void st_long(Register d, Register s1, Register s2);
  inline void st_long(Register d, Register s1, int simm13a);
  inline void st_long(Register d, Register s1, RegisterOrConstant s2);
  inline void st_long(Register d, const Address& a, int offset = 0);

  // Helpers for address formation.
  // - They emit only a move if s2 is a constant zero.
  // - If dest is a constant and either s1 or s2 is a register, the temp argument is required and becomes the result.
  // - If dest is a register and either s1 or s2 is a non-simm13 constant, the temp argument is required and used to materialize the constant.
  RegisterOrConstant regcon_andn_ptr(RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp = noreg);
  RegisterOrConstant regcon_inc_ptr( RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp = noreg);
  RegisterOrConstant regcon_sll_ptr( RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp = noreg);

  RegisterOrConstant ensure_simm13_or_reg(RegisterOrConstant src, Register temp) {
    if (is_simm13(src.constant_or_zero()))
      return src;               // register or short constant
    guarantee(temp != noreg, "constant offset overflow");
    set(src.as_constant(), temp);
    return temp;
  }

  // --------------------------------------------------

 public:
  // traps as per trap.h (SPARC ABI?)

  void breakpoint_trap();
  void breakpoint_trap(Condition c, CC cc = icc);
  void flush_windows_trap();
  void clean_windows_trap();
  void get_psr_trap();
  void set_psr_trap();

  // V8/V9 flush_windows
  void flush_windows();

  // Support for serializing memory accesses between threads
  void serialize_memory(Register thread, Register tmp1, Register tmp2);

  // Stack frame creation/removal
  void enter();
  void leave();

  // V8/V9 integer multiply
  void mult(Register s1, Register s2, Register d);
  void mult(Register s1, int simm13a, Register d);

  // V8/V9 read and write of condition codes.
  void read_ccr(Register d);
  void write_ccr(Register s);

  // Manipulation of C++ bools
  // These are idioms to flag the need for care with accessing bools but on
  // this platform we assume byte size

  inline void stbool(Register d, const Address& a) { stb(d, a); }
  inline void ldbool(const Address& a, Register d) { ldsb(a, d); }
  inline void tstbool( Register s ) { tst(s); }
  inline void movbool( bool boolconst, Register d) { mov( (int) boolconst, d); }

  // klass oop manipulations if compressed
  void load_klass(Register src_oop, Register klass);
  void store_klass(Register klass, Register dst_oop);
  void store_klass_gap(Register s, Register dst_oop);

   // oop manipulations
  void load_heap_oop(const Address& s, Register d);
  void load_heap_oop(Register s1, Register s2, Register d);
  void load_heap_oop(Register s1, int simm13a, Register d);
  void load_heap_oop(Register s1, RegisterOrConstant s2, Register d);
  void store_heap_oop(Register d, Register s1, Register s2);
  void store_heap_oop(Register d, Register s1, int simm13a);
  void store_heap_oop(Register d, const Address& a, int offset = 0);

  void encode_heap_oop(Register src, Register dst);
  void encode_heap_oop(Register r) {
    encode_heap_oop(r, r);
  }
  void decode_heap_oop(Register src, Register dst);
  void decode_heap_oop(Register r) {
    decode_heap_oop(r, r);
  }
  void encode_heap_oop_not_null(Register r);
  void decode_heap_oop_not_null(Register r);
  void encode_heap_oop_not_null(Register src, Register dst);
  void decode_heap_oop_not_null(Register src, Register dst);

  // Support for managing the JavaThread pointer (i.e.; the reference to
  // thread-local information).
  void get_thread();                                // load G2_thread
  void verify_thread();                             // verify G2_thread contents
  void save_thread   (const Register threache); // save to cache
  void restore_thread(const Register thread_cache); // restore from cache

  // Support for last Java frame (but use call_VM instead where possible)
  void set_last_Java_frame(Register last_java_sp, Register last_Java_pc);
  void reset_last_Java_frame(void);

  // Call into the VM.
  // Passes the thread pointer (in O0) as a prepended argument.
  // Makes sure oop return values are visible to the GC.
  void call_VM(Register oop_result, address entry_point, int number_of_arguments = 0, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);

  // these overloadings are not presently used on SPARC:
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments = 0, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions = true);
  void call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions = true);

  void call_VM_leaf(Register thread_cache, address entry_point, int number_of_arguments = 0);
  void call_VM_leaf(Register thread_cache, address entry_point, Register arg_1);
  void call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2);
  void call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2, Register arg_3);

  void get_vm_result  (Register oop_result);
  void get_vm_result_2(Register oop_result);

  // vm result is currently getting hijacked to for oop preservation
  void set_vm_result(Register oop_result);

  // if call_VM_base was called with check_exceptions=false, then call
  // check_and_forward_exception to handle exceptions when it is safe
  void check_and_forward_exception(Register scratch_reg);

 private:
  // For V8
  void read_ccr_trap(Register ccr_save);
  void write_ccr_trap(Register ccr_save1, Register scratch1, Register scratch2);

#ifdef ASSERT
  // For V8 debugging.  Uses V8 instruction sequence and checks
  // result with V9 insturctions rdccr and wrccr.
  // Uses Gscatch and Gscatch2
  void read_ccr_v8_assert(Register ccr_save);
  void write_ccr_v8_assert(Register ccr_save);
#endif // ASSERT

 public:

  // Write to card table for - register is destroyed afterwards.
  void card_table_write(jbyte* byte_map_base, Register tmp, Register obj);

  void card_write_barrier_post(Register store_addr, Register new_val, Register tmp);

#ifndef SERIALGC
  // Array store and offset
  void g1_write_barrier_pre(Register obj, Register index, int offset, Register tmp, bool preserve_o_regs);

  void g1_write_barrier_post(Register store_addr, Register new_val, Register tmp);

  // May do filtering, depending on the boolean arguments.
  void g1_card_table_write(jbyte* byte_map_base,
                           Register tmp, Register obj, Register new_val,
                           bool region_filter, bool null_filter);
#endif // SERIALGC

  // pushes double TOS element of FPU stack on CPU stack; pops from FPU stack
  void push_fTOS();

  // pops double TOS element from CPU stack and pushes on FPU stack
  void pop_fTOS();

  void empty_FPU_stack();

  void push_IU_state();
  void pop_IU_state();

  void push_FPU_state();
  void pop_FPU_state();

  void push_CPU_state();
  void pop_CPU_state();

  // if heap base register is used - reinit it with the correct value
  void reinit_heapbase();

  // Debugging
  void _verify_oop(Register reg, const char * msg, const char * file, int line);
  void _verify_oop_addr(Address addr, const char * msg, const char * file, int line);

#define verify_oop(reg) _verify_oop(reg, "broken oop " #reg, __FILE__, __LINE__)
#define verify_oop_addr(addr) _verify_oop_addr(addr, "broken oop addr ", __FILE__, __LINE__)

        // only if +VerifyOops
  void verify_FPU(int stack_depth, const char* s = "illegal FPU state");
        // only if +VerifyFPU
  void stop(const char* msg);                          // prints msg, dumps registers and stops execution
  void warn(const char* msg);                          // prints msg, but don't stop
  void untested(const char* what = "");
  void unimplemented(const char* what = "")      { char* b = new char[1024];  jio_snprintf(b, 1024, "unimplemented: %s", what);  stop(b); }
  void should_not_reach_here()                   { stop("should not reach here"); }
  void print_CPU_state();

  // oops in code
  AddressLiteral allocate_oop_address(jobject obj);                          // allocate_index
  AddressLiteral constant_oop_address(jobject obj);                          // find_index
  inline void    set_oop             (jobject obj, Register d);              // uses allocate_oop_address
  inline void    set_oop_constant    (jobject obj, Register d);              // uses constant_oop_address
  inline void    set_oop             (const AddressLiteral& obj_addr, Register d); // same as load_address

  void set_narrow_oop( jobject obj, Register d );

  // nop padding
  void align(int modulus);

  // declare a safepoint
  void safepoint();

  // factor out part of stop into subroutine to save space
  void stop_subroutine();
  // factor out part of verify_oop into subroutine to save space
  void verify_oop_subroutine();

  // side-door communication with signalHandler in os_solaris.cpp
  static address _verify_oop_implicit_branch[3];

#ifndef PRODUCT
  static void test();
#endif

  // convert an incoming arglist to varargs format; put the pointer in d
  void set_varargs( Argument a, Register d );

  int total_frame_size_in_bytes(int extraWords);

  // used when extraWords known statically
  void save_frame(int extraWords);
  void save_frame_c1(int size_in_bytes);
  // make a frame, and simultaneously pass up one or two register value
  // into the new register window
  void save_frame_and_mov(int extraWords, Register s1, Register d1, Register s2 = Register(), Register d2 = Register());

  // give no. (outgoing) params, calc # of words will need on frame
  void calc_mem_param_words(Register Rparam_words, Register Rresult);

  // used to calculate frame size dynamically
  // result is in bytes and must be negated for save inst
  void calc_frame_size(Register extraWords, Register resultReg);

  // calc and also save
  void calc_frame_size_and_save(Register extraWords, Register resultReg);

  static void debug(char* msg, RegistersForDebugging* outWindow);

  // implementations of bytecodes used by both interpreter and compiler

  void lcmp( Register Ra_hi, Register Ra_low,
             Register Rb_hi, Register Rb_low,
             Register Rresult);

  void lneg( Register Rhi, Register Rlow );

  void lshl(  Register Rin_high,  Register Rin_low,  Register Rcount,
              Register Rout_high, Register Rout_low, Register Rtemp );

  void lshr(  Register Rin_high,  Register Rin_low,  Register Rcount,
              Register Rout_high, Register Rout_low, Register Rtemp );

  void lushr( Register Rin_high,  Register Rin_low,  Register Rcount,
              Register Rout_high, Register Rout_low, Register Rtemp );

#ifdef _LP64
  void lcmp( Register Ra, Register Rb, Register Rresult);
#endif

  // Loading values by size and signed-ness
  void load_sized_value(Address src, Register dst, size_t size_in_bytes, bool is_signed);

  void float_cmp( bool is_float, int unordered_result,
                  FloatRegister Fa, FloatRegister Fb,
                  Register Rresult);

  void fneg( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);
  void fneg( FloatRegisterImpl::Width w, FloatRegister sd ) { Assembler::fneg(w, sd); }
  void fmov( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);
  void fabs( FloatRegisterImpl::Width w, FloatRegister s, FloatRegister d);

  void save_all_globals_into_locals();
  void restore_globals_from_locals();

  void casx_under_lock(Register top_ptr_reg, Register top_reg, Register ptr_reg,
    address lock_addr=0, bool use_call_vm=false);
  void cas_under_lock(Register top_ptr_reg, Register top_reg, Register ptr_reg,
    address lock_addr=0, bool use_call_vm=false);
  void casn (Register addr_reg, Register cmp_reg, Register set_reg) ;

  // These set the icc condition code to equal if the lock succeeded
  // and notEqual if it failed and requires a slow case
  void compiler_lock_object(Register Roop, Register Rmark, Register Rbox,
                            Register Rscratch,
                            BiasedLockingCounters* counters = NULL,
                            bool try_bias = UseBiasedLocking);
  void compiler_unlock_object(Register Roop, Register Rmark, Register Rbox,
                              Register Rscratch,
                              bool try_bias = UseBiasedLocking);

  // Biased locking support
  // Upon entry, lock_reg must point to the lock record on the stack,
  // obj_reg must contain the target object, and mark_reg must contain
  // the target object's header.
  // Destroys mark_reg if an attempt is made to bias an anonymously
  // biased lock. In this case a failure will go either to the slow
  // case or fall through with the notEqual condition code set with
  // the expectation that the slow case in the runtime will be called.
  // In the fall-through case where the CAS-based lock is done,
  // mark_reg is not destroyed.
  void biased_locking_enter(Register obj_reg, Register mark_reg, Register temp_reg,
                            Label& done, Label* slow_case = NULL,
                            BiasedLockingCounters* counters = NULL);
  // Upon entry, the base register of mark_addr must contain the oop.
  // Destroys temp_reg.

  // If allow_delay_slot_filling is set to true, the next instruction
  // emitted after this one will go in an annulled delay slot if the
  // biased locking exit case failed.
  void biased_locking_exit(Address mark_addr, Register temp_reg, Label& done, bool allow_delay_slot_filling = false);

  // allocation
  void eden_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );
  void tlab_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );
  void tlab_refill(Label& retry_tlab, Label& try_eden, Label& slow_case);

  // interface method calling
  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               RegisterOrConstant itable_index,
                               Register method_result,
                               Register temp_reg, Register temp2_reg,
                               Label& no_such_interface);

  // Test sub_klass against super_klass, with fast and slow paths.

  // The fast path produces a tri-state answer: yes / no / maybe-slow.
  // One of the three labels can be NULL, meaning take the fall-through.
  // If super_check_offset is -1, the value is loaded up from super_klass.
  // No registers are killed, except temp_reg and temp2_reg.
  // If super_check_offset is not -1, temp2_reg is not used and can be noreg.
  void check_klass_subtype_fast_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Register temp2_reg,
                                     Label* L_success,
                                     Label* L_failure,
                                     Label* L_slow_path,
                RegisterOrConstant super_check_offset = RegisterOrConstant(-1),
                Register instanceof_hack = noreg);

  // The rest of the type check; must be wired to a corresponding fast path.
  // It does not repeat the fast path logic, so don't use it standalone.
  // The temp_reg can be noreg, if no temps are available.
  // It can also be sub_klass or super_klass, meaning it's OK to kill that one.
  // Updates the sub's secondary super cache as necessary.
  void check_klass_subtype_slow_path(Register sub_klass,
                                     Register super_klass,
                                     Register temp_reg,
                                     Register temp2_reg,
                                     Register temp3_reg,
                                     Register temp4_reg,
                                     Label* L_success,
                                     Label* L_failure);

  // Simplified, combined version, good for typical uses.
  // Falls through on failure.
  void check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Register temp2_reg,
                           Label& L_success);

  // method handles (JSR 292)
  void check_method_handle_type(Register mtype_reg, Register mh_reg,
                                Register temp_reg,
                                Label& wrong_method_type);
  void load_method_handle_vmslots(Register vmslots_reg, Register mh_reg,
                                  Register temp_reg);
  void jump_to_method_handle_entry(Register mh_reg, Register temp_reg, bool emit_delayed_nop = true);
  // offset relative to Gargs of argument at tos[arg_slot].
  // (arg_slot == 0 means the last argument, not the first).
  RegisterOrConstant argument_offset(RegisterOrConstant arg_slot,
                                     int extra_slot_offset = 0);
  // Address of Gargs and argument_offset.
  Address            argument_address(RegisterOrConstant arg_slot,
                                      int extra_slot_offset = 0);

  // Stack overflow checking

  // Note: this clobbers G3_scratch
  void bang_stack_with_offset(int offset) {
    // stack grows down, caller passes positive offset
    assert(offset > 0, "must bang with negative offset");
    set((-offset)+STACK_BIAS, G3_scratch);
    st(G0, SP, G3_scratch);
  }

  // Writes to stack successive pages until offset reached to check for
  // stack overflow + shadow pages.  Clobbers tsp and scratch registers.
  void bang_stack_size(Register Rsize, Register Rtsp, Register Rscratch);

  virtual RegisterOrConstant delayed_value_impl(intptr_t* delayed_value_addr, Register tmp, int offset);

  void verify_tlab();

  Condition negate_condition(Condition cond);

  // Helper functions for statistics gathering.
  // Conditionally (non-atomically) increments passed counter address, preserving condition codes.
  void cond_inc(Condition cond, address counter_addr, Register Rtemp1, Register Rtemp2);
  // Unconditional increment.
  void inc_counter(address counter_addr, Register Rtmp1, Register Rtmp2);
  void inc_counter(int*    counter_addr, Register Rtmp1, Register Rtmp2);

  // Compare char[] arrays aligned to 4 bytes.
  void char_arrays_equals(Register ary1, Register ary2,
                          Register limit, Register result,
                          Register chr1, Register chr2, Label& Ldone);

#undef VIRTUAL

};

/**
 * class SkipIfEqual:
 *
 * Instantiating this class will result in assembly code being output that will
 * jump around any code emitted between the creation of the instance and it's
 * automatic destruction at the end of a scope block, depending on the value of
 * the flag passed to the constructor, which will be checked at run-time.
 */
class SkipIfEqual : public StackObj {
 private:
  MacroAssembler* _masm;
  Label _label;

 public:
   // 'temp' is a temp register that this object can use (and trash)
   SkipIfEqual(MacroAssembler*, Register temp,
               const bool* flag_addr, Assembler::Condition condition);
   ~SkipIfEqual();
};

#ifdef ASSERT
// On RISC, there's no benefit to verifying instruction boundaries.
inline bool AbstractAssembler::pd_check_instruction_mark() { return false; }
#endif
