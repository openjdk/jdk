/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_REGMASK_HPP
#define SHARE_VM_OPTO_REGMASK_HPP

#include "code/vmreg.hpp"
#include "opto/optoreg.hpp"

// Some fun naming (textual) substitutions:
//
// RegMask::get_low_elem() ==> RegMask::find_first_elem()
// RegMask::Special        ==> RegMask::Empty
// RegMask::_flags         ==> RegMask::is_AllStack()
// RegMask::operator<<=()  ==> RegMask::Insert()
// RegMask::operator>>=()  ==> RegMask::Remove()
// RegMask::Union()        ==> RegMask::OR
// RegMask::Inter()        ==> RegMask::AND
//
// OptoRegister::RegName   ==> OptoReg::Name
//
// OptoReg::stack0()       ==> _last_Mach_Reg  or ZERO in core version
//
// numregs in chaitin      ==> proper degree in chaitin

//-------------Non-zero bit search methods used by RegMask---------------------
// Find lowest 1, or return 32 if empty
int find_lowest_bit( uint32_t mask );
// Find highest 1, or return 32 if empty
int find_hihghest_bit( uint32_t mask );

//------------------------------RegMask----------------------------------------
// The ADL file describes how to print the machine-specific registers, as well
// as any notion of register classes.  We provide a register mask, which is
// just a collection of Register numbers.

// The ADLC defines 2 macros, RM_SIZE and FORALL_BODY.
// RM_SIZE is the size of a register mask in words.
// FORALL_BODY replicates a BODY macro once per word in the register mask.
// The usage is somewhat clumsy and limited to the regmask.[h,c]pp files.
// However, it means the ADLC can redefine the unroll macro and all loops
// over register masks will be unrolled by the correct amount.

class RegMask VALUE_OBJ_CLASS_SPEC {
  union {
    double _dummy_force_double_alignment[RM_SIZE>>1];
    // Array of Register Mask bits.  This array is large enough to cover
    // all the machine registers and all parameters that need to be passed
    // on the stack (stack registers) up to some interesting limit.  Methods
    // that need more parameters will NOT be compiled.  On Intel, the limit
    // is something like 90+ parameters.
    int _A[RM_SIZE];
  };

  enum {
    _WordBits    = BitsPerInt,
    _LogWordBits = LogBitsPerInt,
    _RM_SIZE     = RM_SIZE   // local constant, imported, then hidden by #undef
  };

public:
  enum { CHUNK_SIZE = RM_SIZE*_WordBits };

  // SlotsPerLong is 2, since slots are 32 bits and longs are 64 bits.
  // Also, consider the maximum alignment size for a normally allocated
  // value.  Since we allocate register pairs but not register quads (at
  // present), this alignment is SlotsPerLong (== 2).  A normally
  // aligned allocated register is either a single register, or a pair
  // of adjacent registers, the lower-numbered being even.
  // See also is_aligned_Pairs() below, and the padding added before
  // Matcher::_new_SP to keep allocated pairs aligned properly.
  // If we ever go to quad-word allocations, SlotsPerQuad will become
  // the controlling alignment constraint.  Note that this alignment
  // requirement is internal to the allocator, and independent of any
  // particular platform.
  enum { SlotsPerLong = 2,
         SlotsPerVecS = 1,
         SlotsPerVecD = 2,
         SlotsPerVecX = 4,
         SlotsPerVecY = 8,
         SlotsPerVecZ = 16 };

  // A constructor only used by the ADLC output.  All mask fields are filled
  // in directly.  Calls to this look something like RM(1,2,3,4);
  RegMask(
#   define BODY(I) int a##I,
    FORALL_BODY
#   undef BODY
    int dummy = 0 ) {
#   define BODY(I) _A[I] = a##I;
    FORALL_BODY
#   undef BODY
  }

  // Handy copying constructor
  RegMask( RegMask *rm ) {
#   define BODY(I) _A[I] = rm->_A[I];
    FORALL_BODY
#   undef BODY
  }

  // Construct an empty mask
  RegMask( ) { Clear(); }

  // Construct a mask with a single bit
  RegMask( OptoReg::Name reg ) { Clear(); Insert(reg); }

  // Check for register being in mask
  int Member( OptoReg::Name reg ) const {
    assert( reg < CHUNK_SIZE, "" );
    return _A[reg>>_LogWordBits] & (1<<(reg&(_WordBits-1)));
  }

  // The last bit in the register mask indicates that the mask should repeat
  // indefinitely with ONE bits.  Returns TRUE if mask is infinite or
  // unbounded in size.  Returns FALSE if mask is finite size.
  int is_AllStack() const { return _A[RM_SIZE-1] >> (_WordBits-1); }

  // Work around an -xO3 optimization problme in WS6U1. The old way:
  //   void set_AllStack() { _A[RM_SIZE-1] |= (1<<(_WordBits-1)); }
  // will cause _A[RM_SIZE-1] to be clobbered, not updated when set_AllStack()
  // follows an Insert() loop, like the one found in init_spill_mask(). Using
  // Insert() instead works because the index into _A in computed instead of
  // constant.  See bug 4665841.
  void set_AllStack() { Insert(OptoReg::Name(CHUNK_SIZE-1)); }

  // Test for being a not-empty mask.
  int is_NotEmpty( ) const {
    int tmp = 0;
#   define BODY(I) tmp |= _A[I];
    FORALL_BODY
#   undef BODY
    return tmp;
  }

  // Find lowest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_first_elem() const {
    int base, bits;
#   define BODY(I) if( (bits = _A[I]) != 0 ) base = I<<_LogWordBits; else
    FORALL_BODY
#   undef BODY
      { base = OptoReg::Bad; bits = 1<<0; }
    return OptoReg::Name(base + find_lowest_bit(bits));
  }
  // Get highest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_last_elem() const {
    int base, bits;
#   define BODY(I) if( (bits = _A[RM_SIZE-1-I]) != 0 ) base = (RM_SIZE-1-I)<<_LogWordBits; else
    FORALL_BODY
#   undef BODY
      { base = OptoReg::Bad; bits = 1<<0; }
    return OptoReg::Name(base + find_hihghest_bit(bits));
  }

  // Find the lowest-numbered register pair in the mask.  Return the
  // HIGHEST register number in the pair, or BAD if no pairs.
  // Assert that the mask contains only bit pairs.
  OptoReg::Name find_first_pair() const;

  // Clear out partial bits; leave only aligned adjacent bit pairs.
  void clear_to_pairs();
  // Smear out partial bits; leave only aligned adjacent bit pairs.
  void smear_to_pairs();
  // Verify that the mask contains only aligned adjacent bit pairs
  void verify_pairs() const { assert( is_aligned_pairs(), "mask is not aligned, adjacent pairs" ); }
  // Test that the mask contains only aligned adjacent bit pairs
  bool is_aligned_pairs() const;

  // mask is a pair of misaligned registers
  bool is_misaligned_pair() const { return Size()==2 && !is_aligned_pairs(); }
  // Test for single register
  int is_bound1() const;
  // Test for a single adjacent pair
  int is_bound_pair() const;
  // Test for a single adjacent set of ideal register's size.
  int is_bound(uint ireg) const {
    if (is_vector(ireg)) {
      if (is_bound_set(num_registers(ireg)))
        return true;
    } else if (is_bound1() || is_bound_pair()) {
      return true;
    }
    return false;
  }

  // Find the lowest-numbered register set in the mask.  Return the
  // HIGHEST register number in the set, or BAD if no sets.
  // Assert that the mask contains only bit sets.
  OptoReg::Name find_first_set(const int size) const;

  // Clear out partial bits; leave only aligned adjacent bit sets of size.
  void clear_to_sets(const int size);
  // Smear out partial bits to aligned adjacent bit sets.
  void smear_to_sets(const int size);
  // Verify that the mask contains only aligned adjacent bit sets
  void verify_sets(int size) const { assert(is_aligned_sets(size), "mask is not aligned, adjacent sets"); }
  // Test that the mask contains only aligned adjacent bit sets
  bool is_aligned_sets(const int size) const;

  // mask is a set of misaligned registers
  bool is_misaligned_set(int size) const { return (int)Size()==size && !is_aligned_sets(size);}

  // Test for a single adjacent set
  int is_bound_set(const int size) const;

  static bool is_vector(uint ireg);
  static int num_registers(uint ireg);

  // Fast overlap test.  Non-zero if any registers in common.
  int overlap( const RegMask &rm ) const {
    return
#   define BODY(I) (_A[I] & rm._A[I]) |
    FORALL_BODY
#   undef BODY
    0 ;
  }

  // Special test for register pressure based splitting
  // UP means register only, Register plus stack, or stack only is DOWN
  bool is_UP() const;

  // Clear a register mask
  void Clear( ) {
#   define BODY(I) _A[I] = 0;
    FORALL_BODY
#   undef BODY
  }

  // Fill a register mask with 1's
  void Set_All( ) {
#   define BODY(I) _A[I] = -1;
    FORALL_BODY
#   undef BODY
  }

  // Insert register into mask
  void Insert( OptoReg::Name reg ) {
    assert( reg < CHUNK_SIZE, "" );
    _A[reg>>_LogWordBits] |= (1<<(reg&(_WordBits-1)));
  }

  // Remove register from mask
  void Remove( OptoReg::Name reg ) {
    assert( reg < CHUNK_SIZE, "" );
    _A[reg>>_LogWordBits] &= ~(1<<(reg&(_WordBits-1)));
  }

  // OR 'rm' into 'this'
  void OR( const RegMask &rm ) {
#   define BODY(I) this->_A[I] |= rm._A[I];
    FORALL_BODY
#   undef BODY
  }

  // AND 'rm' into 'this'
  void AND( const RegMask &rm ) {
#   define BODY(I) this->_A[I] &= rm._A[I];
    FORALL_BODY
#   undef BODY
  }

  // Subtract 'rm' from 'this'
  void SUBTRACT( const RegMask &rm ) {
#   define BODY(I) _A[I] &= ~rm._A[I];
    FORALL_BODY
#   undef BODY
  }

  // Compute size of register mask: number of bits
  uint Size() const;

#ifndef PRODUCT
  void print() const { dump(); }
  void dump(outputStream *st = tty) const; // Print a mask
#endif

  static const RegMask Empty;   // Common empty mask

  static bool can_represent(OptoReg::Name reg) {
    // NOTE: -1 in computation reflects the usage of the last
    //       bit of the regmask as an infinite stack flag and
    //       -7 is to keep mask aligned for largest value (VecZ).
    return (int)reg < (int)(CHUNK_SIZE-1);
  }
  static bool can_represent_arg(OptoReg::Name reg) {
    // NOTE: -SlotsPerVecZ in computation reflects the need
    //       to keep mask aligned for largest value (VecZ).
    return (int)reg < (int)(CHUNK_SIZE-SlotsPerVecZ);
  }
};

// Do not use this constant directly in client code!
#undef RM_SIZE

#endif // SHARE_VM_OPTO_REGMASK_HPP
