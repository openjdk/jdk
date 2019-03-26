/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "opto/ad.hpp"
#include "opto/compile.hpp"
#include "opto/matcher.hpp"
#include "opto/node.hpp"
#include "opto/regmask.hpp"
#include "utilities/population_count.hpp"

#define RM_SIZE _RM_SIZE /* a constant private to the class RegMask */

//------------------------------dump-------------------------------------------

#ifndef PRODUCT
void OptoReg::dump(int r, outputStream *st) {
  switch (r) {
  case Special: st->print("r---"); break;
  case Bad:     st->print("rBAD"); break;
  default:
    if (r < _last_Mach_Reg) st->print("%s", Matcher::regName[r]);
    else st->print("rS%d",r);
    break;
  }
}
#endif


//=============================================================================
const RegMask RegMask::Empty(
# define BODY(I) 0,
  FORALL_BODY
# undef BODY
  0
);

//=============================================================================
bool RegMask::is_vector(uint ireg) {
  return (ireg == Op_VecS || ireg == Op_VecD ||
          ireg == Op_VecX || ireg == Op_VecY || ireg == Op_VecZ );
}

int RegMask::num_registers(uint ireg) {
    switch(ireg) {
      case Op_VecZ:
        return 16;
      case Op_VecY:
        return 8;
      case Op_VecX:
        return 4;
      case Op_VecD:
      case Op_RegD:
      case Op_RegL:
#ifdef _LP64
      case Op_RegP:
#endif
        return 2;
    }
    // Op_VecS and the rest ideal registers.
    return 1;
}

// Clear out partial bits; leave only bit pairs
void RegMask::clear_to_pairs() {
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    int bits = _A[i];
    bits &= ((bits & 0x55555555)<<1); // 1 hi-bit set for each pair
    bits |= (bits>>1);          // Smear 1 hi-bit into a pair
    _A[i] = bits;
  }
  assert(is_aligned_pairs(), "mask is not aligned, adjacent pairs");
}

bool RegMask::is_misaligned_pair() const {
  return Size() == 2 && !is_aligned_pairs();
}

bool RegMask::is_aligned_pairs() const {
  // Assert that the register mask contains only bit pairs.
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    int bits = _A[i];
    while (bits) {              // Check bits for pairing
      int bit = bits & -bits;   // Extract low bit
      // Low bit is not odd means its mis-aligned.
      if ((bit & 0x55555555) == 0) return false;
      bits -= bit;              // Remove bit from mask
      // Check for aligned adjacent bit
      if ((bits & (bit<<1)) == 0) return false;
      bits -= (bit<<1);         // Remove other halve of pair
    }
  }
  return true;
}

// Return TRUE if the mask contains a single bit
bool RegMask::is_bound1() const {
  if (is_AllStack()) return false;
  return Size() == 1;
}

// Return TRUE if the mask contains an adjacent pair of bits and no other bits.
bool RegMask::is_bound_pair() const {
  if (is_AllStack()) return false;
  int bit = -1;                 // Set to hold the one bit allowed
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    if (_A[i]) {                   // Found some bits
      if (bit != -1) return false; // Already had bits, so fail
      bit = _A[i] & -(_A[i]);      // Extract 1 bit from mask
      if ((bit << 1) != 0) {       // Bit pair stays in same word?
        if ((bit | (bit<<1)) != _A[i])
          return false;            // Require adjacent bit pair and no more bits
      } else {                     // Else its a split-pair case
        if(bit != _A[i]) return false; // Found many bits, so fail
        i++;                       // Skip iteration forward
        if (i > _hwm || _A[i] != 1)
          return false; // Require 1 lo bit in next word
      }
    }
  }
  // True for both the empty mask and for a bit pair
  return true;
}

// Test for a single adjacent set of ideal register's size.
bool RegMask::is_bound(uint ireg) const {
  if (is_vector(ireg)) {
    if (is_bound_set(num_registers(ireg)))
      return true;
  } else if (is_bound1() || is_bound_pair()) {
    return true;
  }
  return false;
}

// only indicies of power 2 are accessed, so index 3 is only filled in for storage.
static int low_bits[5] = { 0x55555555, 0x11111111, 0x01010101, 0x00000000, 0x00010001 };

// Find the lowest-numbered register set in the mask.  Return the
// HIGHEST register number in the set, or BAD if no sets.
// Works also for size 1.
OptoReg::Name RegMask::find_first_set(const int size) const {
  assert(is_aligned_sets(size), "mask is not aligned, adjacent sets");
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    if (_A[i]) {                // Found some bits
      // Convert to bit number, return hi bit in pair
      return OptoReg::Name((i<<_LogWordBits) + find_lowest_bit(_A[i]) + (size - 1));
    }
  }
  return OptoReg::Bad;
}

// Clear out partial bits; leave only aligned adjacent bit pairs
void RegMask::clear_to_sets(const int size) {
  if (size == 1) return;
  assert(2 <= size && size <= 16, "update low bits table");
  assert(is_power_of_2(size), "sanity");
  assert(valid_watermarks(), "sanity");
  int low_bits_mask = low_bits[size>>2];
  for (int i = _lwm; i <= _hwm; i++) {
    int bits = _A[i];
    int sets = (bits & low_bits_mask);
    for (int j = 1; j < size; j++) {
      sets = (bits & (sets<<1)); // filter bits which produce whole sets
    }
    sets |= (sets>>1);           // Smear 1 hi-bit into a set
    if (size > 2) {
      sets |= (sets>>2);         // Smear 2 hi-bits into a set
      if (size > 4) {
        sets |= (sets>>4);       // Smear 4 hi-bits into a set
        if (size > 8) {
          sets |= (sets>>8);     // Smear 8 hi-bits into a set
        }
      }
    }
    _A[i] = sets;
  }
  assert(is_aligned_sets(size), "mask is not aligned, adjacent sets");
}

// Smear out partial bits to aligned adjacent bit sets
void RegMask::smear_to_sets(const int size) {
  if (size == 1) return;
  assert(2 <= size && size <= 16, "update low bits table");
  assert(is_power_of_2(size), "sanity");
  assert(valid_watermarks(), "sanity");
  int low_bits_mask = low_bits[size>>2];
  for (int i = _lwm; i <= _hwm; i++) {
    int bits = _A[i];
    int sets = 0;
    for (int j = 0; j < size; j++) {
      sets |= (bits & low_bits_mask);  // collect partial bits
      bits  = bits>>1;
    }
    sets |= (sets<<1);           // Smear 1 lo-bit  into a set
    if (size > 2) {
      sets |= (sets<<2);         // Smear 2 lo-bits into a set
      if (size > 4) {
        sets |= (sets<<4);       // Smear 4 lo-bits into a set
        if (size > 8) {
          sets |= (sets<<8);     // Smear 8 lo-bits into a set
        }
      }
    }
    _A[i] = sets;
  }
  assert(is_aligned_sets(size), "mask is not aligned, adjacent sets");
}

// Assert that the register mask contains only bit sets.
bool RegMask::is_aligned_sets(const int size) const {
  if (size == 1) return true;
  assert(2 <= size && size <= 16, "update low bits table");
  assert(is_power_of_2(size), "sanity");
  int low_bits_mask = low_bits[size>>2];
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    int bits = _A[i];
    while (bits) {              // Check bits for pairing
      int bit = bits & -bits;   // Extract low bit
      // Low bit is not odd means its mis-aligned.
      if ((bit & low_bits_mask) == 0) return false;
      // Do extra work since (bit << size) may overflow.
      int hi_bit = bit << (size-1); // high bit
      int set = hi_bit + ((hi_bit-1) & ~(bit-1));
      // Check for aligned adjacent bits in this set
      if ((bits & set) != set) return false;
      bits -= set;  // Remove this set
    }
  }
  return true;
}

// Return TRUE if the mask contains one adjacent set of bits and no other bits.
// Works also for size 1.
int RegMask::is_bound_set(const int size) const {
  if (is_AllStack()) return false;
  assert(1 <= size && size <= 16, "update low bits table");
  assert(valid_watermarks(), "sanity");
  int bit = -1;                 // Set to hold the one bit allowed
  for (int i = _lwm; i <= _hwm; i++) {
    if (_A[i] ) {               // Found some bits
      if (bit != -1)
       return false;            // Already had bits, so fail
      bit = _A[i] & -_A[i];     // Extract low bit from mask
      int hi_bit = bit << (size-1); // high bit
      if (hi_bit != 0) {        // Bit set stays in same word?
        int set = hi_bit + ((hi_bit-1) & ~(bit-1));
        if (set != _A[i])
          return false;         // Require adjacent bit set and no more bits
      } else {                  // Else its a split-set case
        if (((-1) & ~(bit-1)) != _A[i])
          return false;         // Found many bits, so fail
        i++;                    // Skip iteration forward and check high part
        // The lower (32-size) bits should be 0 since it is split case.
        int clear_bit_size = 32-size;
        int shift_back_size = 32-clear_bit_size;
        int set = bit>>clear_bit_size;
        set = set & -set; // Remove sign extension.
        set = (((set << size) - 1) >> shift_back_size);
        if (i > _hwm || _A[i] != set)
          return false; // Require expected low bits in next word
      }
    }
  }
  // True for both the empty mask and for a bit set
  return true;
}

// UP means register only, Register plus stack, or stack only is DOWN
bool RegMask::is_UP() const {
  // Quick common case check for DOWN (any stack slot is legal)
  if (is_AllStack())
    return false;
  // Slower check for any stack bits set (also DOWN)
  if (overlap(Matcher::STACK_ONLY_mask))
    return false;
  // Not DOWN, so must be UP
  return true;
}

// Compute size of register mask in bits
uint RegMask::Size() const {
  uint sum = 0;
  assert(valid_watermarks(), "sanity");
  for (int i = _lwm; i <= _hwm; i++) {
    sum += population_count(_A[i]);
  }
  return sum;
}

#ifndef PRODUCT
void RegMask::dump(outputStream *st) const {
  st->print("[");
  RegMask rm = *this;           // Structure copy into local temp

  OptoReg::Name start = rm.find_first_elem(); // Get a register
  if (OptoReg::is_valid(start)) { // Check for empty mask
    rm.Remove(start);           // Yank from mask
    OptoReg::dump(start, st);   // Print register
    OptoReg::Name last = start;

    // Now I have printed an initial register.
    // Print adjacent registers as "rX-rZ" instead of "rX,rY,rZ".
    // Begin looping over the remaining registers.
    while (1) {                 //
      OptoReg::Name reg = rm.find_first_elem(); // Get a register
      if (!OptoReg::is_valid(reg))
        break;                  // Empty mask, end loop
      rm.Remove(reg);           // Yank from mask

      if (last+1 == reg) {      // See if they are adjacent
        // Adjacent registers just collect into long runs, no printing.
        last = reg;
      } else {                  // Ending some kind of run
        if (start == last) {    // 1-register run; no special printing
        } else if (start+1 == last) {
          st->print(",");       // 2-register run; print as "rX,rY"
          OptoReg::dump(last, st);
        } else {                // Multi-register run; print as "rX-rZ"
          st->print("-");
          OptoReg::dump(last, st);
        }
        st->print(",");         // Seperate start of new run
        start = last = reg;     // Start a new register run
        OptoReg::dump(start, st); // Print register
      } // End of if ending a register run or not
    } // End of while regmask not empty

    if (start == last) {        // 1-register run; no special printing
    } else if (start+1 == last) {
      st->print(",");           // 2-register run; print as "rX,rY"
      OptoReg::dump(last, st);
    } else {                    // Multi-register run; print as "rX-rZ"
      st->print("-");
      OptoReg::dump(last, st);
    }
    if (rm.is_AllStack()) st->print("...");
  }
  st->print("]");
}
#endif
