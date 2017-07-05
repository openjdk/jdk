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

#ifndef CPU_SPARC_VM_MACROASSEMBLER_SPARC_INLINE_HPP
#define CPU_SPARC_VM_MACROASSEMBLER_SPARC_INLINE_HPP

#include "asm/assembler.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCache.hpp"

inline bool Address::is_simm13(int offset) { return Assembler::is_simm13(disp() + offset); }


inline int AddressLiteral::low10() const {
  return Assembler::low10(value());
}


inline void MacroAssembler::pd_patch_instruction(address branch, address target) {
  jint& stub_inst = *(jint*) branch;
  stub_inst = patched_branch(target - branch, stub_inst, 0);
}

// Use the right loads/stores for the platform
inline void MacroAssembler::ld_ptr( Register s1, Register s2, Register d ) {
#ifdef _LP64
  Assembler::ldx(s1, s2, d);
#else
             ld( s1, s2, d);
#endif
}

inline void MacroAssembler::ld_ptr( Register s1, int simm13a, Register d ) {
#ifdef _LP64
  Assembler::ldx(s1, simm13a, d);
#else
             ld( s1, simm13a, d);
#endif
}

#ifdef ASSERT
// ByteSize is only a class when ASSERT is defined, otherwise it's an int.
inline void MacroAssembler::ld_ptr( Register s1, ByteSize simm13a, Register d ) {
  ld_ptr(s1, in_bytes(simm13a), d);
}
#endif

inline void MacroAssembler::ld_ptr( Register s1, RegisterOrConstant s2, Register d ) {
#ifdef _LP64
  ldx(s1, s2, d);
#else
  ld( s1, s2, d);
#endif
}

inline void MacroAssembler::ld_ptr(const Address& a, Register d, int offset) {
#ifdef _LP64
  ldx(a, d, offset);
#else
  ld( a, d, offset);
#endif
}

inline void MacroAssembler::st_ptr( Register d, Register s1, Register s2 ) {
#ifdef _LP64
  Assembler::stx(d, s1, s2);
#else
             st( d, s1, s2);
#endif
}

inline void MacroAssembler::st_ptr( Register d, Register s1, int simm13a ) {
#ifdef _LP64
  Assembler::stx(d, s1, simm13a);
#else
             st( d, s1, simm13a);
#endif
}

#ifdef ASSERT
// ByteSize is only a class when ASSERT is defined, otherwise it's an int.
inline void MacroAssembler::st_ptr( Register d, Register s1, ByteSize simm13a ) {
  st_ptr(d, s1, in_bytes(simm13a));
}
#endif

inline void MacroAssembler::st_ptr( Register d, Register s1, RegisterOrConstant s2 ) {
#ifdef _LP64
  stx(d, s1, s2);
#else
  st( d, s1, s2);
#endif
}

inline void MacroAssembler::st_ptr(Register d, const Address& a, int offset) {
#ifdef _LP64
  stx(d, a, offset);
#else
  st( d, a, offset);
#endif
}

// Use the right loads/stores for the platform
inline void MacroAssembler::ld_long( Register s1, Register s2, Register d ) {
#ifdef _LP64
  Assembler::ldx(s1, s2, d);
#else
  Assembler::ldd(s1, s2, d);
#endif
}

inline void MacroAssembler::ld_long( Register s1, int simm13a, Register d ) {
#ifdef _LP64
  Assembler::ldx(s1, simm13a, d);
#else
  Assembler::ldd(s1, simm13a, d);
#endif
}

inline void MacroAssembler::ld_long( Register s1, RegisterOrConstant s2, Register d ) {
#ifdef _LP64
  ldx(s1, s2, d);
#else
  ldd(s1, s2, d);
#endif
}

inline void MacroAssembler::ld_long(const Address& a, Register d, int offset) {
#ifdef _LP64
  ldx(a, d, offset);
#else
  ldd(a, d, offset);
#endif
}

inline void MacroAssembler::st_long( Register d, Register s1, Register s2 ) {
#ifdef _LP64
  Assembler::stx(d, s1, s2);
#else
  Assembler::std(d, s1, s2);
#endif
}

inline void MacroAssembler::st_long( Register d, Register s1, int simm13a ) {
#ifdef _LP64
  Assembler::stx(d, s1, simm13a);
#else
  Assembler::std(d, s1, simm13a);
#endif
}

inline void MacroAssembler::st_long( Register d, Register s1, RegisterOrConstant s2 ) {
#ifdef _LP64
  stx(d, s1, s2);
#else
  std(d, s1, s2);
#endif
}

inline void MacroAssembler::st_long( Register d, const Address& a, int offset ) {
#ifdef _LP64
  stx(d, a, offset);
#else
  std(d, a, offset);
#endif
}

// Functions for isolating 64 bit shifts for LP64

inline void MacroAssembler::sll_ptr( Register s1, Register s2, Register d ) {
#ifdef _LP64
  Assembler::sllx(s1, s2, d);
#else
  Assembler::sll( s1, s2, d);
#endif
}

inline void MacroAssembler::sll_ptr( Register s1, int imm6a,   Register d ) {
#ifdef _LP64
  Assembler::sllx(s1, imm6a, d);
#else
  Assembler::sll( s1, imm6a, d);
#endif
}

inline void MacroAssembler::srl_ptr( Register s1, Register s2, Register d ) {
#ifdef _LP64
  Assembler::srlx(s1, s2, d);
#else
  Assembler::srl( s1, s2, d);
#endif
}

inline void MacroAssembler::srl_ptr( Register s1, int imm6a,   Register d ) {
#ifdef _LP64
  Assembler::srlx(s1, imm6a, d);
#else
  Assembler::srl( s1, imm6a, d);
#endif
}

inline void MacroAssembler::sll_ptr( Register s1, RegisterOrConstant s2, Register d ) {
  if (s2.is_register())  sll_ptr(s1, s2.as_register(), d);
  else                   sll_ptr(s1, s2.as_constant(), d);
}

// Use the right branch for the platform

inline void MacroAssembler::br( Condition c, bool a, Predict p, address d, relocInfo::relocType rt ) {
  Assembler::bp(c, a, icc, p, d, rt);
}

inline void MacroAssembler::br( Condition c, bool a, Predict p, Label& L ) {
  insert_nop_after_cbcond();
  br(c, a, p, target(L));
}


// Branch that tests either xcc or icc depending on the
// architecture compiled (LP64 or not)
inline void MacroAssembler::brx( Condition c, bool a, Predict p, address d, relocInfo::relocType rt ) {
#ifdef _LP64
    Assembler::bp(c, a, xcc, p, d, rt);
#else
    MacroAssembler::br(c, a, p, d, rt);
#endif
}

inline void MacroAssembler::brx( Condition c, bool a, Predict p, Label& L ) {
  insert_nop_after_cbcond();
  brx(c, a, p, target(L));
}

inline void MacroAssembler::ba( Label& L ) {
  br(always, false, pt, L);
}

// Warning: V9 only functions
inline void MacroAssembler::bp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt ) {
  Assembler::bp(c, a, cc, p, d, rt);
}

inline void MacroAssembler::bp( Condition c, bool a, CC cc, Predict p, Label& L ) {
  Assembler::bp(c, a, cc, p, L);
}

inline void MacroAssembler::fb( Condition c, bool a, Predict p, address d, relocInfo::relocType rt ) {
  fbp(c, a, fcc0, p, d, rt);
}

inline void MacroAssembler::fb( Condition c, bool a, Predict p, Label& L ) {
  insert_nop_after_cbcond();
  fb(c, a, p, target(L));
}

inline void MacroAssembler::fbp( Condition c, bool a, CC cc, Predict p, address d, relocInfo::relocType rt ) {
  Assembler::fbp(c, a, cc, p, d, rt);
}

inline void MacroAssembler::fbp( Condition c, bool a, CC cc, Predict p, Label& L ) {
  Assembler::fbp(c, a, cc, p, L);
}

inline void MacroAssembler::jmp( Register s1, Register s2 ) { jmpl( s1, s2, G0 ); }
inline void MacroAssembler::jmp( Register s1, int simm13a, RelocationHolder const& rspec ) { jmpl( s1, simm13a, G0, rspec); }

inline bool MacroAssembler::is_far_target(address d) {
  if (ForceUnreachable) {
    // References outside the code cache should be treated as far
    return d < CodeCache::low_bound() || d > CodeCache::high_bound();
  }
  return !is_in_wdisp30_range(d, CodeCache::low_bound()) || !is_in_wdisp30_range(d, CodeCache::high_bound());
}

// Call with a check to see if we need to deal with the added
// expense of relocation and if we overflow the displacement
// of the quick call instruction.
inline void MacroAssembler::call( address d, relocInfo::relocType rt ) {
#ifdef _LP64
  intptr_t disp;
  // NULL is ok because it will be relocated later.
  // Must change NULL to a reachable address in order to
  // pass asserts here and in wdisp.
  if ( d == NULL )
    d = pc();

  // Is this address within range of the call instruction?
  // If not, use the expensive instruction sequence
  if (is_far_target(d)) {
    relocate(rt);
    AddressLiteral dest(d);
    jumpl_to(dest, O7, O7);
  } else {
    Assembler::call(d, rt);
  }
#else
  Assembler::call( d, rt );
#endif
}

inline void MacroAssembler::call( Label& L,   relocInfo::relocType rt ) {
  insert_nop_after_cbcond();
  MacroAssembler::call( target(L), rt);
}



inline void MacroAssembler::callr( Register s1, Register s2 ) { jmpl( s1, s2, O7 ); }
inline void MacroAssembler::callr( Register s1, int simm13a, RelocationHolder const& rspec ) { jmpl( s1, simm13a, O7, rspec); }

// prefetch instruction
inline void MacroAssembler::iprefetch( address d, relocInfo::relocType rt ) {
  Assembler::bp( never, true, xcc, pt, d, rt );
    Assembler::bp( never, true, xcc, pt, d, rt );
}
inline void MacroAssembler::iprefetch( Label& L) { iprefetch( target(L) ); }


// clobbers o7 on V8!!
// returns delta from gotten pc to addr after
inline int MacroAssembler::get_pc( Register d ) {
  int x = offset();
  rdpc(d);
  return offset() - x;
}


// Note:  All MacroAssembler::set_foo functions are defined out-of-line.


// Loads the current PC of the following instruction as an immediate value in
// 2 instructions.  All PCs in the CodeCache are within 2 Gig of each other.
inline intptr_t MacroAssembler::load_pc_address( Register reg, int bytes_to_skip ) {
  intptr_t thepc = (intptr_t)pc() + 2*BytesPerInstWord + bytes_to_skip;
#ifdef _LP64
  Unimplemented();
#else
  Assembler::sethi(   thepc & ~0x3ff, reg, internal_word_Relocation::spec((address)thepc));
             add(reg, thepc &  0x3ff, reg, internal_word_Relocation::spec((address)thepc));
#endif
  return thepc;
}


inline void MacroAssembler::load_contents(const AddressLiteral& addrlit, Register d, int offset) {
  assert_not_delayed();
  if (ForceUnreachable) {
    patchable_sethi(addrlit, d);
  } else {
    sethi(addrlit, d);
  }
  ld(d, addrlit.low10() + offset, d);
}


inline void MacroAssembler::load_bool_contents(const AddressLiteral& addrlit, Register d, int offset) {
  assert_not_delayed();
  if (ForceUnreachable) {
    patchable_sethi(addrlit, d);
  } else {
    sethi(addrlit, d);
  }
  ldub(d, addrlit.low10() + offset, d);
}


inline void MacroAssembler::load_ptr_contents(const AddressLiteral& addrlit, Register d, int offset) {
  assert_not_delayed();
  if (ForceUnreachable) {
    patchable_sethi(addrlit, d);
  } else {
    sethi(addrlit, d);
  }
  ld_ptr(d, addrlit.low10() + offset, d);
}


inline void MacroAssembler::store_contents(Register s, const AddressLiteral& addrlit, Register temp, int offset) {
  assert_not_delayed();
  if (ForceUnreachable) {
    patchable_sethi(addrlit, temp);
  } else {
    sethi(addrlit, temp);
  }
  st(s, temp, addrlit.low10() + offset);
}


inline void MacroAssembler::store_ptr_contents(Register s, const AddressLiteral& addrlit, Register temp, int offset) {
  assert_not_delayed();
  if (ForceUnreachable) {
    patchable_sethi(addrlit, temp);
  } else {
    sethi(addrlit, temp);
  }
  st_ptr(s, temp, addrlit.low10() + offset);
}


// This code sequence is relocatable to any address, even on LP64.
inline void MacroAssembler::jumpl_to(const AddressLiteral& addrlit, Register temp, Register d, int offset) {
  assert_not_delayed();
  // Force fixed length sethi because NativeJump and NativeFarCall don't handle
  // variable length instruction streams.
  patchable_sethi(addrlit, temp);
  jmpl(temp, addrlit.low10() + offset, d);
}


inline void MacroAssembler::jump_to(const AddressLiteral& addrlit, Register temp, int offset) {
  jumpl_to(addrlit, temp, G0, offset);
}


inline void MacroAssembler::jump_indirect_to(Address& a, Register temp,
                                             int ld_offset, int jmp_offset) {
  assert_not_delayed();
  //sethi(al);                   // sethi is caller responsibility for this one
  ld_ptr(a, temp, ld_offset);
  jmp(temp, jmp_offset);
}


inline void MacroAssembler::set_metadata(Metadata* obj, Register d) {
  set_metadata(allocate_metadata_address(obj), d);
}

inline void MacroAssembler::set_metadata_constant(Metadata* obj, Register d) {
  set_metadata(constant_metadata_address(obj), d);
}

inline void MacroAssembler::set_metadata(const AddressLiteral& obj_addr, Register d) {
  assert(obj_addr.rspec().type() == relocInfo::metadata_type, "must be a metadata reloc");
  set(obj_addr, d);
}

inline void MacroAssembler::set_oop(jobject obj, Register d) {
  set_oop(allocate_oop_address(obj), d);
}


inline void MacroAssembler::set_oop_constant(jobject obj, Register d) {
  set_oop(constant_oop_address(obj), d);
}


inline void MacroAssembler::set_oop(const AddressLiteral& obj_addr, Register d) {
  assert(obj_addr.rspec().type() == relocInfo::oop_type, "must be an oop reloc");
  set(obj_addr, d);
}


inline void MacroAssembler::load_argument( Argument& a, Register  d ) {
  if (a.is_register())
    mov(a.as_register(), d);
  else
    ld (a.as_address(),  d);
}

inline void MacroAssembler::store_argument( Register s, Argument& a ) {
  if (a.is_register())
    mov(s, a.as_register());
  else
    st_ptr (s, a.as_address());         // ABI says everything is right justified.
}

inline void MacroAssembler::store_ptr_argument( Register s, Argument& a ) {
  if (a.is_register())
    mov(s, a.as_register());
  else
    st_ptr (s, a.as_address());
}


#ifdef _LP64
inline void MacroAssembler::store_float_argument( FloatRegister s, Argument& a ) {
  if (a.is_float_register())
// V9 ABI has F1, F3, F5 are used to pass instead of O0, O1, O2
    fmov(FloatRegisterImpl::S, s, a.as_float_register() );
  else
    // Floats are stored in the high half of the stack entry
    // The low half is undefined per the ABI.
    stf(FloatRegisterImpl::S, s, a.as_address(), sizeof(jfloat));
}

inline void MacroAssembler::store_double_argument( FloatRegister s, Argument& a ) {
  if (a.is_float_register())
// V9 ABI has D0, D2, D4 are used to pass instead of O0, O1, O2
    fmov(FloatRegisterImpl::D, s, a.as_double_register() );
  else
    stf(FloatRegisterImpl::D, s, a.as_address());
}

inline void MacroAssembler::store_long_argument( Register s, Argument& a ) {
  if (a.is_register())
    mov(s, a.as_register());
  else
    stx(s, a.as_address());
}
#endif

inline void MacroAssembler::add(Register s1, int simm13a, Register d, relocInfo::relocType rtype) {
  relocate(rtype);
  add(s1, simm13a, d);
}
inline void MacroAssembler::add(Register s1, int simm13a, Register d, RelocationHolder const& rspec) {
  relocate(rspec);
  add(s1, simm13a, d);
}

// form effective addresses this way:
inline void MacroAssembler::add(const Address& a, Register d, int offset) {
  if (a.has_index())   add(a.base(), a.index(),         d);
  else               { add(a.base(), a.disp() + offset, d, a.rspec(offset)); offset = 0; }
  if (offset != 0)     add(d,        offset,            d);
}
inline void MacroAssembler::add(Register s1, RegisterOrConstant s2, Register d, int offset) {
  if (s2.is_register())  add(s1, s2.as_register(),          d);
  else                 { add(s1, s2.as_constant() + offset, d); offset = 0; }
  if (offset != 0)       add(d,  offset,                    d);
}

inline void MacroAssembler::andn(Register s1, RegisterOrConstant s2, Register d) {
  if (s2.is_register())  andn(s1, s2.as_register(), d);
  else                   andn(s1, s2.as_constant(), d);
}

inline void MacroAssembler::clrb( Register s1, Register s2) { stb( G0, s1, s2 ); }
inline void MacroAssembler::clrh( Register s1, Register s2) { sth( G0, s1, s2 ); }
inline void MacroAssembler::clr(  Register s1, Register s2) { stw( G0, s1, s2 ); }
inline void MacroAssembler::clrx( Register s1, Register s2) { stx( G0, s1, s2 ); }

inline void MacroAssembler::clrb( Register s1, int simm13a) { stb( G0, s1, simm13a); }
inline void MacroAssembler::clrh( Register s1, int simm13a) { sth( G0, s1, simm13a); }
inline void MacroAssembler::clr(  Register s1, int simm13a) { stw( G0, s1, simm13a); }
inline void MacroAssembler::clrx( Register s1, int simm13a) { stx( G0, s1, simm13a); }

#ifdef _LP64
// Make all 32 bit loads signed so 64 bit registers maintain proper sign
inline void MacroAssembler::ld(  Register s1, Register s2, Register d)      { ldsw( s1, s2, d); }
inline void MacroAssembler::ld(  Register s1, int simm13a, Register d)      { ldsw( s1, simm13a, d); }
#else
inline void MacroAssembler::ld(  Register s1, Register s2, Register d)      { lduw( s1, s2, d); }
inline void MacroAssembler::ld(  Register s1, int simm13a, Register d)      { lduw( s1, simm13a, d); }
#endif

#ifdef ASSERT
  // ByteSize is only a class when ASSERT is defined, otherwise it's an int.
# ifdef _LP64
inline void MacroAssembler::ld(Register s1, ByteSize simm13a, Register d) { ldsw( s1, in_bytes(simm13a), d); }
# else
inline void MacroAssembler::ld(Register s1, ByteSize simm13a, Register d) { lduw( s1, in_bytes(simm13a), d); }
# endif
#endif

inline void MacroAssembler::ld(  const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ld(  a.base(), a.index(),         d); }
  else               {                          ld(  a.base(), a.disp() + offset, d); }
}

inline void MacroAssembler::ldsb(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldsb(a.base(), a.index(),         d); }
  else               {                          ldsb(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::ldsh(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldsh(a.base(), a.index(),         d); }
  else               {                          ldsh(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::ldsw(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldsw(a.base(), a.index(),         d); }
  else               {                          ldsw(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::ldub(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldub(a.base(), a.index(),         d); }
  else               {                          ldub(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::lduh(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); lduh(a.base(), a.index(),         d); }
  else               {                          lduh(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::lduw(const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); lduw(a.base(), a.index(),         d); }
  else               {                          lduw(a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::ldd( const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldd( a.base(), a.index(),         d); }
  else               {                          ldd( a.base(), a.disp() + offset, d); }
}
inline void MacroAssembler::ldx( const Address& a, Register d, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); ldx( a.base(), a.index(),         d); }
  else               {                          ldx( a.base(), a.disp() + offset, d); }
}

inline void MacroAssembler::ldub(Register s1, RegisterOrConstant s2, Register d) { ldub(Address(s1, s2), d); }
inline void MacroAssembler::ldsb(Register s1, RegisterOrConstant s2, Register d) { ldsb(Address(s1, s2), d); }
inline void MacroAssembler::lduh(Register s1, RegisterOrConstant s2, Register d) { lduh(Address(s1, s2), d); }
inline void MacroAssembler::ldsh(Register s1, RegisterOrConstant s2, Register d) { ldsh(Address(s1, s2), d); }
inline void MacroAssembler::lduw(Register s1, RegisterOrConstant s2, Register d) { lduw(Address(s1, s2), d); }
inline void MacroAssembler::ldsw(Register s1, RegisterOrConstant s2, Register d) { ldsw(Address(s1, s2), d); }
inline void MacroAssembler::ldx( Register s1, RegisterOrConstant s2, Register d) { ldx( Address(s1, s2), d); }
inline void MacroAssembler::ld(  Register s1, RegisterOrConstant s2, Register d) { ld(  Address(s1, s2), d); }
inline void MacroAssembler::ldd( Register s1, RegisterOrConstant s2, Register d) { ldd( Address(s1, s2), d); }

inline void MacroAssembler::ldf(FloatRegisterImpl::Width w, Register s1, RegisterOrConstant s2, FloatRegister d) {
  if (s2.is_register())  ldf(w, s1, s2.as_register(), d);
  else                   ldf(w, s1, s2.as_constant(), d);
}

inline void MacroAssembler::ldf(FloatRegisterImpl::Width w, const Address& a, FloatRegister d, int offset) {
  relocate(a.rspec(offset));
  if (a.has_index()) {
    assert(offset == 0, "");
    ldf(w, a.base(), a.index(), d);
  } else {
    ldf(w, a.base(), a.disp() + offset, d);
  }
}

// returns if membar generates anything, obviously this code should mirror
// membar below.
inline bool MacroAssembler::membar_has_effect( Membar_mask_bits const7a ) {
  if (!os::is_MP())
    return false;  // Not needed on single CPU
  const Membar_mask_bits effective_mask =
      Membar_mask_bits(const7a & ~(LoadLoad | LoadStore | StoreStore));
  return (effective_mask != 0);
}

inline void MacroAssembler::membar( Membar_mask_bits const7a ) {
  // Uniprocessors do not need memory barriers
  if (!os::is_MP())
    return;
  // Weakened for current Sparcs and TSO.  See the v9 manual, sections 8.4.3,
  // 8.4.4.3, a.31 and a.50.
  // Under TSO, setting bit 3, 2, or 0 is redundant, so the only value
  // of the mmask subfield of const7a that does anything that isn't done
  // implicitly is StoreLoad.
  const Membar_mask_bits effective_mask =
      Membar_mask_bits(const7a & ~(LoadLoad | LoadStore | StoreStore));
  if (effective_mask != 0) {
    Assembler::membar(effective_mask);
  }
}

inline void MacroAssembler::prefetch(const Address& a, PrefetchFcn f, int offset) {
  relocate(a.rspec(offset));
  assert(!a.has_index(), "");
  prefetch(a.base(), a.disp() + offset, f);
}

inline void MacroAssembler::st(Register d, Register s1, Register s2)      { stw(d, s1, s2); }
inline void MacroAssembler::st(Register d, Register s1, int simm13a)      { stw(d, s1, simm13a); }

#ifdef ASSERT
// ByteSize is only a class when ASSERT is defined, otherwise it's an int.
inline void MacroAssembler::st(Register d, Register s1, ByteSize simm13a) { stw(d, s1, in_bytes(simm13a)); }
#endif

inline void MacroAssembler::st(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); st( d, a.base(), a.index()        ); }
  else               {                          st( d, a.base(), a.disp() + offset); }
}

inline void MacroAssembler::stb(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); stb(d, a.base(), a.index()        ); }
  else               {                          stb(d, a.base(), a.disp() + offset); }
}
inline void MacroAssembler::sth(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); sth(d, a.base(), a.index()        ); }
  else               {                          sth(d, a.base(), a.disp() + offset); }
}
inline void MacroAssembler::stw(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); stw(d, a.base(), a.index()        ); }
  else               {                          stw(d, a.base(), a.disp() + offset); }
}
inline void MacroAssembler::std(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); std(d, a.base(), a.index()        ); }
  else               {                          std(d, a.base(), a.disp() + offset); }
}
inline void MacroAssembler::stx(Register d, const Address& a, int offset) {
  if (a.has_index()) { assert(offset == 0, ""); stx(d, a.base(), a.index()        ); }
  else               {                          stx(d, a.base(), a.disp() + offset); }
}

inline void MacroAssembler::stb(Register d, Register s1, RegisterOrConstant s2) { stb(d, Address(s1, s2)); }
inline void MacroAssembler::sth(Register d, Register s1, RegisterOrConstant s2) { sth(d, Address(s1, s2)); }
inline void MacroAssembler::stw(Register d, Register s1, RegisterOrConstant s2) { stw(d, Address(s1, s2)); }
inline void MacroAssembler::stx(Register d, Register s1, RegisterOrConstant s2) { stx(d, Address(s1, s2)); }
inline void MacroAssembler::std(Register d, Register s1, RegisterOrConstant s2) { std(d, Address(s1, s2)); }
inline void MacroAssembler::st( Register d, Register s1, RegisterOrConstant s2) { st( d, Address(s1, s2)); }

inline void MacroAssembler::stf(FloatRegisterImpl::Width w, FloatRegister d, Register s1, RegisterOrConstant s2) {
  if (s2.is_register())  stf(w, d, s1, s2.as_register());
  else                   stf(w, d, s1, s2.as_constant());
}

inline void MacroAssembler::stf(FloatRegisterImpl::Width w, FloatRegister d, const Address& a, int offset) {
  relocate(a.rspec(offset));
  if (a.has_index()) { assert(offset == 0, ""); stf(w, d, a.base(), a.index()        ); }
  else               {                          stf(w, d, a.base(), a.disp() + offset); }
}

inline void MacroAssembler::sub(Register s1, RegisterOrConstant s2, Register d, int offset) {
  if (s2.is_register())  sub(s1, s2.as_register(),          d);
  else                 { sub(s1, s2.as_constant() + offset, d); offset = 0; }
  if (offset != 0)       sub(d,  offset,                    d);
}

inline void MacroAssembler::swap(const Address& a, Register d, int offset) {
  relocate(a.rspec(offset));
  if (a.has_index()) { assert(offset == 0, ""); swap(a.base(), a.index(), d        ); }
  else               {                          swap(a.base(), a.disp() + offset, d); }
}

#endif // CPU_SPARC_VM_MACROASSEMBLER_SPARC_INLINE_HPP
