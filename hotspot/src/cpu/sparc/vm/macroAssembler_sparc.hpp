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

#ifndef CPU_SPARC_VM_MACROASSEMBLER_SPARC_HPP
#define CPU_SPARC_VM_MACROASSEMBLER_SPARC_HPP

#include "asm/assembler.hpp"
#include "utilities/macros.hpp"

// <sys/trap.h> promises that the system will not use traps 16-31
#define ST_RESERVED_FOR_USER_0 0x10

class BiasedLockingCounters;


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
REGISTER_DECLARATION(Register, Lmethod          , L6); // Method* when calling signature handler

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
  Register base()             const { return _base; }
  Register index()            const { return _index_or_disp.as_register(); }
  int      disp()             const { return _index_or_disp.as_constant(); }

  bool     has_index()        const { return _index_or_disp.is_register(); }
  bool     has_disp()         const { return _index_or_disp.is_constant(); }

  bool     uses(Register reg) const { return base() == reg || (has_index() && index() == reg); }

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
  bool is_same_address(Address a) const {
    // disregard _rspec
    return base() == a.base() && (has_index() ? index() == a.index() : disp() == a.disp());
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
  static Address make_raw(int base, int index, int scale, int disp, relocInfo::relocType disp_reloc);

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

  AddressLiteral(Metadata* addr, relocInfo::relocType rtype = relocInfo::none)
    : _address((address) addr),
      _rspec(rspec_from_rtype(rtype, (address) addr)) {}

  AddressLiteral(Metadata** addr, relocInfo::relocType rtype = relocInfo::none)
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

// Convenience classes
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
  ExternalAddress(address target) : AddressLiteral(target, reloc_for_target(          target)) {}
  ExternalAddress(Metadata** target) : AddressLiteral(target, reloc_for_target((address) target)) {}
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


class MacroAssembler : public Assembler {
  // code patchers need various routines like inv_wdisp()
  friend class NativeInstruction;
  friend class NativeGeneralJump;
  friend class Relocation;
  friend class Label;

 protected:
  static int  patched_branch(int dest_pos, int inst, int inst_pos);
  static int  branch_destination(int inst, int pos);

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

  // compares register with zero (32 bit) and branches (V9 and V8 instructions)
  void cmp_zero_and_br( Condition c, Register s1, Label& L, bool a = false, Predict p = pn );
  // Compares a pointer register with zero and branches on (not)null.
  // Does a test & branch on 32-bit systems and a register-branch on 64-bit.
  void br_null   ( Register s1, bool a, Predict p, Label& L );
  void br_notnull( Register s1, bool a, Predict p, Label& L );

  //
  // Compare registers and branch with nop in delay slot or cbcond without delay slot.
  //
  // ATTENTION: use these instructions with caution because cbcond instruction
  //            has very short distance: 512 instructions (2Kbyte).

  // Compare integer (32 bit) values (icc only).
  void cmp_and_br_short(Register s1, Register s2, Condition c, Predict p, Label& L);
  void cmp_and_br_short(Register s1, int simm13a, Condition c, Predict p, Label& L);
  // Platform depending version for pointer compare (icc on !LP64 and xcc on LP64).
  void cmp_and_brx_short(Register s1, Register s2, Condition c, Predict p, Label& L);
  void cmp_and_brx_short(Register s1, int simm13a, Condition c, Predict p, Label& L);

  // Short branch version for compares a pointer pwith zero.
  void br_null_short   ( Register s1, Predict p, Label& L );
  void br_notnull_short( Register s1, Predict p, Label& L );

  // unconditional short branch
  void ba_short(Label& L);

  inline void bp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void bp( Condition c, bool a, CC cc, Predict p, Label& L );

  // Branch that tests xcc in LP64 and icc in !LP64
  inline void brx( Condition c, bool a, Predict p, address d, relocInfo::relocType rt = relocInfo::none );
  inline void brx( Condition c, bool a, Predict p, Label& L );

  // unconditional branch
  inline void ba( Label& L );

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

  // Check if the call target is out of wdisp30 range (relative to the code cache)
  static inline bool is_far_target(address d);
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

  // sethi Macro handles optimizations and relocations
private:
  void internal_sethi(const AddressLiteral& addrlit, Register d, bool ForceRelocatable);
public:
  void sethi(const AddressLiteral& addrlit, Register d);
  void patchable_sethi(const AddressLiteral& addrlit, Register d);

  // compute the number of instructions for a sethi/set
  static int  insts_for_sethi( address a, bool worst_case = false );
  static int  worst_case_insts_for_set();

  // set may be either setsw or setuw (high 32 bits may be zero or sign)
private:
  void internal_set(const AddressLiteral& al, Register d, bool ForceRelocatable);
  static int insts_for_internal_set(intptr_t value);
public:
  void set(const AddressLiteral& addrlit, Register d);
  void set(intptr_t value, Register d);
  void set(address addr, Register d, RelocationHolder const& rspec);
  static int insts_for_set(intptr_t value) { return insts_for_internal_set(value); }

  void patchable_set(const AddressLiteral& addrlit, Register d);
  void patchable_set(intptr_t value, Register d);
  void set64(jlong value, Register d, Register tmp);
  static int insts_for_set64(jlong value);

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

  using Assembler::add;
  inline void add(Register s1, int simm13a, Register d, relocInfo::relocType rtype);
  inline void add(Register s1, int simm13a, Register d, RelocationHolder const& rspec);
  inline void add(Register s1, RegisterOrConstant s2, Register d, int offset = 0);
  inline void add(const Address& a, Register d, int offset = 0);

  using Assembler::andn;
  inline void andn(  Register s1, RegisterOrConstant s2, Register d);

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

  using Assembler::ldsb;
  using Assembler::ldsh;
  using Assembler::ldsw;
  using Assembler::ldub;
  using Assembler::lduh;
  using Assembler::lduw;
  using Assembler::ldx;
  using Assembler::ldd;

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  inline void ld(Register s1, ByteSize simm13a, Register d);
#endif

  inline void ld(Register s1, Register s2, Register d);
  inline void ld(Register s1, int simm13a, Register d);

  inline void ldsb(const Address& a, Register d, int offset = 0);
  inline void ldsh(const Address& a, Register d, int offset = 0);
  inline void ldsw(const Address& a, Register d, int offset = 0);
  inline void ldub(const Address& a, Register d, int offset = 0);
  inline void lduh(const Address& a, Register d, int offset = 0);
  inline void lduw(const Address& a, Register d, int offset = 0);
  inline void ldx( const Address& a, Register d, int offset = 0);
  inline void ld(  const Address& a, Register d, int offset = 0);
  inline void ldd( const Address& a, Register d, int offset = 0);

  inline void ldub(Register s1, RegisterOrConstant s2, Register d );
  inline void ldsb(Register s1, RegisterOrConstant s2, Register d );
  inline void lduh(Register s1, RegisterOrConstant s2, Register d );
  inline void ldsh(Register s1, RegisterOrConstant s2, Register d );
  inline void lduw(Register s1, RegisterOrConstant s2, Register d );
  inline void ldsw(Register s1, RegisterOrConstant s2, Register d );
  inline void ldx( Register s1, RegisterOrConstant s2, Register d );
  inline void ld(  Register s1, RegisterOrConstant s2, Register d );
  inline void ldd( Register s1, RegisterOrConstant s2, Register d );

  using Assembler::ldf;
  inline void ldf(FloatRegisterImpl::Width w, Register s1, RegisterOrConstant s2, FloatRegister d);
  inline void ldf(FloatRegisterImpl::Width w, const Address& a, FloatRegister d, int offset = 0);

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

  using Assembler::prefetch;
  inline void prefetch(const Address& a, PrefetchFcn F, int offset = 0);

  using Assembler::stb;
  using Assembler::sth;
  using Assembler::stw;
  using Assembler::stx;
  using Assembler::std;

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
  inline void st(Register d, Register s1, ByteSize simm13a);
#endif

  inline void st(Register d, Register s1, Register s2);
  inline void st(Register d, Register s1, int simm13a);

  inline void stb(Register d, const Address& a, int offset = 0 );
  inline void sth(Register d, const Address& a, int offset = 0 );
  inline void stw(Register d, const Address& a, int offset = 0 );
  inline void stx(Register d, const Address& a, int offset = 0 );
  inline void st( Register d, const Address& a, int offset = 0 );
  inline void std(Register d, const Address& a, int offset = 0 );

  inline void stb(Register d, Register s1, RegisterOrConstant s2 );
  inline void sth(Register d, Register s1, RegisterOrConstant s2 );
  inline void stw(Register d, Register s1, RegisterOrConstant s2 );
  inline void stx(Register d, Register s1, RegisterOrConstant s2 );
  inline void std(Register d, Register s1, RegisterOrConstant s2 );
  inline void st( Register d, Register s1, RegisterOrConstant s2 );

  using Assembler::stf;
  inline void stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, RegisterOrConstant s2);
  inline void stf(FloatRegisterImpl::Width w, FloatRegister d, const Address& a, int offset = 0);

  // Note: offset is added to s2.
  using Assembler::sub;
  inline void sub(Register s1, RegisterOrConstant s2, Register d, int offset = 0);

  using Assembler::swap;
  inline void swap(const Address& a, Register d, int offset = 0);

  // address pseudos: make these names unlike instruction names to avoid confusion
  inline intptr_t load_pc_address( Register reg, int bytes_to_skip );
  inline void load_contents(const AddressLiteral& addrlit, Register d, int offset = 0);
  inline void load_bool_contents(const AddressLiteral& addrlit, Register d, int offset = 0);
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
  void breakpoint_trap(Condition c, CC cc);

  // Support for serializing memory accesses between threads
  void serialize_memory(Register thread, Register tmp1, Register tmp2);

  // Stack frame creation/removal
  void enter();
  void leave();

  // Manipulation of C++ bools
  // These are idioms to flag the need for care with accessing bools but on
  // this platform we assume byte size

  inline void stbool(Register d, const Address& a) { stb(d, a); }
  inline void ldbool(const Address& a, Register d) { ldub(a, d); }
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

  void encode_klass_not_null(Register r);
  void decode_klass_not_null(Register r);
  void encode_klass_not_null(Register src, Register dst);
  void decode_klass_not_null(Register src, Register dst);

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
  void get_vm_result_2(Register metadata_result);

  // vm result is currently getting hijacked to for oop preservation
  void set_vm_result(Register oop_result);

  // Emit the CompiledIC call idiom
  void ic_call(address entry, bool emit_delay = true);

  // if call_VM_base was called with check_exceptions=false, then call
  // check_and_forward_exception to handle exceptions when it is safe
  void check_and_forward_exception(Register scratch_reg);

  // Write to card table for - register is destroyed afterwards.
  void card_table_write(jbyte* byte_map_base, Register tmp, Register obj);

  void card_write_barrier_post(Register store_addr, Register new_val, Register tmp);

#if INCLUDE_ALL_GCS
  // General G1 pre-barrier generator.
  void g1_write_barrier_pre(Register obj, Register index, int offset, Register pre_val, Register tmp, bool preserve_o_regs);

  // General G1 post-barrier generator
  void g1_write_barrier_post(Register store_addr, Register new_val, Register tmp);
#endif // INCLUDE_ALL_GCS

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

  // TODO: verify_method and klass metadata (compare against vptr?)
  void _verify_method_ptr(Register reg, const char * msg, const char * file, int line) {}
  void _verify_klass_ptr(Register reg, const char * msg, const char * file, int line){}

#define verify_oop(reg) _verify_oop(reg, "broken oop " #reg, __FILE__, __LINE__)
#define verify_oop_addr(addr) _verify_oop_addr(addr, "broken oop addr ", __FILE__, __LINE__)
#define verify_method_ptr(reg) _verify_method_ptr(reg, "broken method " #reg, __FILE__, __LINE__)
#define verify_klass_ptr(reg) _verify_klass_ptr(reg, "broken klass " #reg, __FILE__, __LINE__)

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

  // metadata in code that we have to keep track of
  AddressLiteral allocate_metadata_address(Metadata* obj); // allocate_index
  AddressLiteral constant_metadata_address(Metadata* obj); // find_index
  inline void    set_metadata             (Metadata* obj, Register d);              // uses allocate_metadata_address
  inline void    set_metadata_constant    (Metadata* obj, Register d);              // uses constant_metadata_address
  inline void    set_metadata             (const AddressLiteral& obj_addr, Register d); // same as load_address

  void set_narrow_oop( jobject obj, Register d );
  void set_narrow_klass( Klass* k, Register d );

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

  int total_frame_size_in_bytes(int extraWords);

  // used when extraWords known statically
  void save_frame(int extraWords = 0);
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

  // Load and store values by size and signed-ness
  void load_sized_value( Address src, Register dst, size_t size_in_bytes, bool is_signed);
  void store_sized_value(Register src, Address dst, size_t size_in_bytes);

  void float_cmp( bool is_float, int unordered_result,
                  FloatRegister Fa, FloatRegister Fb,
                  Register Rresult);

  void save_all_globals_into_locals();
  void restore_globals_from_locals();

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
  void incr_allocated_bytes(RegisterOrConstant size_in_bytes,
                            Register t1, Register t2);

  // interface method calling
  void lookup_interface_method(Register recv_klass,
                               Register intf_klass,
                               RegisterOrConstant itable_index,
                               Register method_result,
                               Register temp_reg, Register temp2_reg,
                               Label& no_such_interface);

  // virtual method calling
  void lookup_virtual_method(Register recv_klass,
                             RegisterOrConstant vtable_index,
                             Register method_result);

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
                RegisterOrConstant super_check_offset = RegisterOrConstant(-1));

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
  // offset relative to Gargs of argument at tos[arg_slot].
  // (arg_slot == 0 means the last argument, not the first).
  RegisterOrConstant argument_offset(RegisterOrConstant arg_slot,
                                     Register temp_reg,
                                     int extra_slot_offset = 0);
  // Address of Gargs and argument_offset.
  Address            argument_address(RegisterOrConstant arg_slot,
                                      Register temp_reg = noreg,
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
  // Use BIS for zeroing
  void bis_zeroing(Register to, Register count, Register temp, Label& Ldone);

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

#endif // CPU_SPARC_VM_MACROASSEMBLER_SPARC_HPP
