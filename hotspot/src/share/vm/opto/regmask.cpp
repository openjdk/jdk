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

#include "precompiled.hpp"
#include "opto/compile.hpp"
#include "opto/regmask.hpp"
#ifdef TARGET_ARCH_MODEL_x86_32
# include "adfiles/ad_x86_32.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_x86_64
# include "adfiles/ad_x86_64.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_sparc
# include "adfiles/ad_sparc.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_zero
# include "adfiles/ad_zero.hpp"
#endif

#define RM_SIZE _RM_SIZE /* a constant private to the class RegMask */

//-------------Non-zero bit search methods used by RegMask---------------------
// Find lowest 1, or return 32 if empty
int find_lowest_bit( uint32 mask ) {
  int n = 0;
  if( (mask & 0xffff) == 0 ) {
    mask >>= 16;
    n += 16;
  }
  if( (mask & 0xff) == 0 ) {
    mask >>= 8;
    n += 8;
  }
  if( (mask & 0xf) == 0 ) {
    mask >>= 4;
    n += 4;
  }
  if( (mask & 0x3) == 0 ) {
    mask >>= 2;
    n += 2;
  }
  if( (mask & 0x1) == 0 ) {
    mask >>= 1;
     n += 1;
  }
  if( mask == 0 ) {
    n = 32;
  }
  return n;
}

// Find highest 1, or return 32 if empty
int find_hihghest_bit( uint32 mask ) {
  int n = 0;
  if( mask > 0xffff ) {
    mask >>= 16;
    n += 16;
  }
  if( mask > 0xff ) {
    mask >>= 8;
    n += 8;
  }
  if( mask > 0xf ) {
    mask >>= 4;
    n += 4;
  }
  if( mask > 0x3 ) {
    mask >>= 2;
    n += 2;
  }
  if( mask > 0x1 ) {
    mask >>= 1;
    n += 1;
  }
  if( mask == 0 ) {
    n = 32;
  }
  return n;
}

//------------------------------dump-------------------------------------------

#ifndef PRODUCT
void OptoReg::dump( int r ) {
  switch( r ) {
  case Special: tty->print("r---");   break;
  case Bad:     tty->print("rBAD");   break;
  default:
    if( r < _last_Mach_Reg ) tty->print(Matcher::regName[r]);
    else tty->print("rS%d",r);
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

//------------------------------find_first_pair--------------------------------
// Find the lowest-numbered register pair in the mask.  Return the
// HIGHEST register number in the pair, or BAD if no pairs.
OptoReg::Name RegMask::find_first_pair() const {
  VerifyPairs();
  for( int i = 0; i < RM_SIZE; i++ ) {
    if( _A[i] ) {               // Found some bits
      int bit = _A[i] & -_A[i]; // Extract low bit
      // Convert to bit number, return hi bit in pair
      return OptoReg::Name((i<<_LogWordBits)+find_lowest_bit(bit)+1);
    }
  }
  return OptoReg::Bad;
}

//------------------------------ClearToPairs-----------------------------------
// Clear out partial bits; leave only bit pairs
void RegMask::ClearToPairs() {
  for( int i = 0; i < RM_SIZE; i++ ) {
    int bits = _A[i];
    bits &= ((bits & 0x55555555)<<1); // 1 hi-bit set for each pair
    bits |= (bits>>1);          // Smear 1 hi-bit into a pair
    _A[i] = bits;
  }
  VerifyPairs();
}

//------------------------------SmearToPairs-----------------------------------
// Smear out partial bits; leave only bit pairs
void RegMask::SmearToPairs() {
  for( int i = 0; i < RM_SIZE; i++ ) {
    int bits = _A[i];
    bits |= ((bits & 0x55555555)<<1); // Smear lo bit hi per pair
    bits |= ((bits & 0xAAAAAAAA)>>1); // Smear hi bit lo per pair
    _A[i] = bits;
  }
  VerifyPairs();
}

//------------------------------is_aligned_pairs-------------------------------
bool RegMask::is_aligned_Pairs() const {
  // Assert that the register mask contains only bit pairs.
  for( int i = 0; i < RM_SIZE; i++ ) {
    int bits = _A[i];
    while( bits ) {             // Check bits for pairing
      int bit = bits & -bits;   // Extract low bit
      // Low bit is not odd means its mis-aligned.
      if( (bit & 0x55555555) == 0 ) return false;
      bits -= bit;              // Remove bit from mask
      // Check for aligned adjacent bit
      if( (bits & (bit<<1)) == 0 ) return false;
      bits -= (bit<<1);         // Remove other halve of pair
    }
  }
  return true;
}

//------------------------------is_bound1--------------------------------------
// Return TRUE if the mask contains a single bit
int RegMask::is_bound1() const {
  if( is_AllStack() ) return false;
  int bit = -1;                 // Set to hold the one bit allowed
  for( int i = 0; i < RM_SIZE; i++ ) {
    if( _A[i] ) {               // Found some bits
      if( bit != -1 ) return false; // Already had bits, so fail
      bit = _A[i] & -_A[i];     // Extract 1 bit from mask
      if( bit != _A[i] ) return false; // Found many bits, so fail
    }
  }
  // True for both the empty mask and for a single bit
  return true;
}

//------------------------------is_bound2--------------------------------------
// Return TRUE if the mask contains an adjacent pair of bits and no other bits.
int RegMask::is_bound2() const {
  if( is_AllStack() ) return false;

  int bit = -1;                 // Set to hold the one bit allowed
  for( int i = 0; i < RM_SIZE; i++ ) {
    if( _A[i] ) {               // Found some bits
      if( bit != -1 ) return false; // Already had bits, so fail
      bit = _A[i] & -(_A[i]);   // Extract 1 bit from mask
      if( (bit << 1) != 0 ) {   // Bit pair stays in same word?
        if( (bit | (bit<<1)) != _A[i] )
          return false;         // Require adjacent bit pair and no more bits
      } else {                  // Else its a split-pair case
        if( bit != _A[i] ) return false; // Found many bits, so fail
        i++;                    // Skip iteration forward
        if( _A[i] != 1 ) return false; // Require 1 lo bit in next word
      }
    }
  }
  // True for both the empty mask and for a bit pair
  return true;
}

//------------------------------is_UP------------------------------------------
// UP means register only, Register plus stack, or stack only is DOWN
bool RegMask::is_UP() const {
  // Quick common case check for DOWN (any stack slot is legal)
  if( is_AllStack() )
    return false;
  // Slower check for any stack bits set (also DOWN)
  if( overlap(Matcher::STACK_ONLY_mask) )
    return false;
  // Not DOWN, so must be UP
  return true;
}

//------------------------------Size-------------------------------------------
// Compute size of register mask in bits
uint RegMask::Size() const {
  extern uint8 bitsInByte[256];
  uint sum = 0;
  for( int i = 0; i < RM_SIZE; i++ )
    sum +=
      bitsInByte[(_A[i]>>24) & 0xff] +
      bitsInByte[(_A[i]>>16) & 0xff] +
      bitsInByte[(_A[i]>> 8) & 0xff] +
      bitsInByte[ _A[i]      & 0xff];
  return sum;
}

#ifndef PRODUCT
//------------------------------print------------------------------------------
void RegMask::dump( ) const {
  tty->print("[");
  RegMask rm = *this;           // Structure copy into local temp

  OptoReg::Name start = rm.find_first_elem(); // Get a register
  if( OptoReg::is_valid(start) ) { // Check for empty mask
    rm.Remove(start);           // Yank from mask
    OptoReg::dump(start);       // Print register
    OptoReg::Name last = start;

    // Now I have printed an initial register.
    // Print adjacent registers as "rX-rZ" instead of "rX,rY,rZ".
    // Begin looping over the remaining registers.
    while( 1 ) {                //
      OptoReg::Name reg = rm.find_first_elem(); // Get a register
      if( !OptoReg::is_valid(reg) )
        break;                  // Empty mask, end loop
      rm.Remove(reg);           // Yank from mask

      if( last+1 == reg ) {     // See if they are adjacent
        // Adjacent registers just collect into long runs, no printing.
        last = reg;
      } else {                  // Ending some kind of run
        if( start == last ) {   // 1-register run; no special printing
        } else if( start+1 == last ) {
          tty->print(",");      // 2-register run; print as "rX,rY"
          OptoReg::dump(last);
        } else {                // Multi-register run; print as "rX-rZ"
          tty->print("-");
          OptoReg::dump(last);
        }
        tty->print(",");        // Seperate start of new run
        start = last = reg;     // Start a new register run
        OptoReg::dump(start); // Print register
      } // End of if ending a register run or not
    } // End of while regmask not empty

    if( start == last ) {       // 1-register run; no special printing
    } else if( start+1 == last ) {
      tty->print(",");          // 2-register run; print as "rX,rY"
      OptoReg::dump(last);
    } else {                    // Multi-register run; print as "rX-rZ"
      tty->print("-");
      OptoReg::dump(last);
    }
    if( rm.is_AllStack() ) tty->print("...");
  }
  tty->print("]");
}
#endif
