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
#include "libadt/vectset.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"

// Vector Sets - An Abstract Data Type

// BitsInByte is a lookup table which tells the number of bits that
// are in the looked-up number.  It is very useful in VectorSet_Size.

uint8_t bitsInByte[BITS_IN_BYTE_ARRAY_SIZE] = {
  0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
  1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
  1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
  1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
  2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
  3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
  3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
  4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8
};

//------------------------------VectorSet--------------------------------------
// Create a new, empty Set.
VectorSet::VectorSet(Arena *arena) : Set(arena) {
  size = 2;                     // Small initial size
  data = (uint32_t *)_set_arena->Amalloc(size*sizeof(uint32_t));
  data[0] = 0;                  // No elements
  data[1] = 0;
}

//------------------------------operator=--------------------------------------
Set &VectorSet::operator = (const Set &set)
{
  if( &set == this ) return *this;
  FREE_FAST(data);
  // The cast is a virtual function that checks that "set" is a VectorSet.
  slamin(*(set.asVectorSet()));
  return *this;
}

//------------------------------slamin-----------------------------------------
// Initialize one set with another.  No regard is made to the existing Set.
void VectorSet::slamin(const VectorSet& s)
{
  size = s.size;                // Use new size
  data = (uint32_t*)s._set_arena->Amalloc(size*sizeof(uint32_t)); // Make array of required size
  memcpy( data, s.data, size*sizeof(uint32_t) ); // Fill the array
}

//------------------------------grow-------------------------------------------
// Expand the existing set to a bigger size
void VectorSet::grow( uint newsize )
{
  newsize = (newsize+31) >> 5;  // Convert to longwords
  uint x = size;
  while( x < newsize ) x <<= 1;
  data = (uint32_t *)_set_arena->Arealloc(data, size*sizeof(uint32_t), x*sizeof(uint32_t));
  memset((char *)(data + size), 0, (x - size)*sizeof(uint32_t));
  size = x;
}

//------------------------------operator<<=------------------------------------
// Insert a member into an existing Set.
Set &VectorSet::operator <<= (uint elem)
{
  uint word = elem >> 5;            // Get the longword offset
  uint32_t mask = 1L << (elem & 31);  // Get bit mask

  if( word >= size )            // Need to grow set?
    grow(elem+1);               // Then grow it
  data[word] |= mask;           // Set new bit
  return *this;
}

//------------------------------operator>>=------------------------------------
// Delete a member from an existing Set.
Set &VectorSet::operator >>= (uint elem)
{
  uint word = elem >> 5;          // Get the longword offset
  if( word >= size )              // Beyond the last?
    return *this;                 // Then it's clear & return clear
  uint32_t mask = 1L << (elem & 31);     // Get bit mask
  data[word] &= ~mask;            // Clear bit
  return *this;
}

//------------------------------operator&=-------------------------------------
// Intersect one set into another.
VectorSet &VectorSet::operator &= (const VectorSet &s)
{
  // NOTE: The intersection is never any larger than the smallest set.
  if( s.size < size ) size = s.size; // Get smaller size
  uint32_t *u1 = data;          // Pointer to the destination data
  uint32_t *u2 = s.data;        // Pointer to the source data
  for( uint i=0; i<size; i++)   // For data in set
    *u1++ &= *u2++;             // Copy and AND longwords
  return *this;                 // Return set
}

//------------------------------operator&=-------------------------------------
Set &VectorSet::operator &= (const Set &set)
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) &= *(set.asVectorSet());
}

//------------------------------operator|=-------------------------------------
// Union one set into another.
VectorSet &VectorSet::operator |= (const VectorSet &s)
{
  // This many words must be unioned
  uint cnt = ((size<s.size)?size:s.size);
  uint32_t *u1 = data;          // Pointer to the destination data
  uint32_t *u2 = s.data;        // Pointer to the source data
  for( uint i=0; i<cnt; i++)    // Copy and OR the two sets
    *u1++ |= *u2++;
  if( size < s.size ) {         // Is set 2 larger than set 1?
    // Extend result by larger set
    grow(s.size*sizeof(uint32_t)*8);
    memcpy(&data[cnt], u2, (s.size - cnt)*sizeof(uint32_t));
  }
  return *this;                 // Return result set
}

//------------------------------operator|=-------------------------------------
Set &VectorSet::operator |= (const Set &set)
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) |= *(set.asVectorSet());
}

//------------------------------operator-=-------------------------------------
// Difference one set from another.
VectorSet &VectorSet::operator -= (const VectorSet &s)
{
  // This many words must be unioned
  uint cnt = ((size<s.size)?size:s.size);
  uint32_t *u1 = data;          // Pointer to the destination data
  uint32_t *u2 = s.data;        // Pointer to the source data
  for( uint i=0; i<cnt; i++ )   // For data in set
    *u1++ &= ~(*u2++);          // A <-- A & ~B  with longwords
  return *this;                 // Return new set
}

//------------------------------operator-=-------------------------------------
Set &VectorSet::operator -= (const Set &set)
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) -= *(set.asVectorSet());
}

//------------------------------compare----------------------------------------
// Compute 2 booleans: bits in A not B, bits in B not A.
// Return X0 --  A is not a subset of B
//        X1 --  A is a subset of B
//        0X --  B is not a subset of A
//        1X --  B is a subset of A
int VectorSet::compare (const VectorSet &s) const
{
  uint32_t *u1 = data;          // Pointer to the destination data
  uint32_t *u2 = s.data;        // Pointer to the source data
  uint32_t AnotB = 0, BnotA = 0;
  // This many words must be unioned
  uint cnt = ((size<s.size)?size:s.size);

  // Get bits for both sets
  uint i;                       // Exit value of loop
  for( i=0; i<cnt; i++ ) {      // For data in BOTH sets
    uint32_t A = *u1++;         // Data from one guy
    uint32_t B = *u2++;         // Data from other guy
    AnotB |= (A & ~B);          // Compute bits in A not B
    BnotA |= (B & ~A);          // Compute bits in B not A
  }

  // Get bits from bigger set
  if( size < s.size ) {
    for( ; i<s.size; i++ )      // For data in larger set
      BnotA |= *u2++;           // These bits are in B not A
  } else {
    for( ; i<size; i++ )        // For data in larger set
      AnotB |= *u1++;           // These bits are in A not B
  }

  // Set & return boolean flags
  return ((!BnotA)<<1) + (!AnotB);
}

//------------------------------operator==-------------------------------------
// Test for set equality
int VectorSet::operator == (const VectorSet &s) const
{
  return compare(s) == 3;       // TRUE if A and B are mutual subsets
}

//------------------------------operator==-------------------------------------
int VectorSet::operator == (const Set &set) const
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) == *(set.asVectorSet());
}

//------------------------------disjoint---------------------------------------
// Check for sets being disjoint.
int VectorSet::disjoint(const Set &set) const
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  const VectorSet &s = *(set.asVectorSet());

  // NOTE: The intersection is never any larger than the smallest set.
  uint small_size = ((size<s.size)?size:s.size);
  uint32_t *u1 = data;               // Pointer to the destination data
  uint32_t *u2 = s.data;             // Pointer to the source data
  for( uint i=0; i<small_size; i++)  // For data in set
    if( *u1++ & *u2++ )              // If any elements in common
      return 0;                      // Then not disjoint
  return 1;                          // Else disjoint
}

//------------------------------operator<--------------------------------------
// Test for strict subset
int VectorSet::operator < (const VectorSet &s) const
{
  return compare(s) == 1;       // A subset B, B not subset A
}

//------------------------------operator<--------------------------------------
int VectorSet::operator < (const Set &set) const
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) < *(set.asVectorSet());
}

//------------------------------operator<=-------------------------------------
// Test for subset
int VectorSet::operator <= (const VectorSet &s) const
{
  return compare(s) & 1;        // A subset B
}

//------------------------------operator<=-------------------------------------
int VectorSet::operator <= (const Set &set) const
{
  // The cast is a virtual function that checks that "set" is a VectorSet.
  return (*this) <= *(set.asVectorSet());
}

//------------------------------operator[]-------------------------------------
// Test for membership.  A Zero/Non-Zero value is returned!
int VectorSet::operator[](uint elem) const
{
  uint word = elem >> 5;              // Get the longword offset
  if( word >= size )                  // Beyond the last?
    return 0;                         // Then it's clear
  uint32_t mask = 1L << (elem & 31);  // Get bit mask
  return ((data[word] & mask))!=0;    // Return the sense of the bit
}

//------------------------------Clear------------------------------------------
// Clear a set
void VectorSet::Clear(void)
{
  if( size > 100 ) {            // Reclaim storage only if huge
    FREE_RESOURCE_ARRAY(uint32_t,data,size);
    size = 2;                   // Small initial size
    data = NEW_RESOURCE_ARRAY(uint32_t,size);
  }
  memset( data, 0, size*sizeof(uint32_t) );
}

//------------------------------Size-------------------------------------------
// Return number of elements in a Set
uint VectorSet::Size(void) const
{
  uint sum = 0;                 // Cumulative size so far.
  uint8_t* currByte = (uint8_t*) data;
  for( uint32_t i = 0; i < (size<<2); i++) // While have bytes to process
    sum += bitsInByte[*currByte++];      // Add bits in current byte to size.
  return sum;
}

//------------------------------Sort-------------------------------------------
// Sort the elements for the next forall statement
void VectorSet::Sort(void)
{
}

//------------------------------hash-------------------------------------------
int VectorSet::hash() const
{
  uint32_t _xor = 0;
  uint lim = ((size<4)?size:4);
  for( uint i = 0; i < lim; i++ )
    _xor ^= data[i];
  return (int)_xor;
}

//------------------------------iterate----------------------------------------
// Used by Set::print().
class VSetI_ : public SetI_ {
  VectorSetI vsi;
public:
  VSetI_( const VectorSet *vset, uint &elem ) : vsi(vset) { elem = vsi.elem; }

  uint next(void) { ++vsi; return vsi.elem; }
  int  test(void) { return vsi.test(); }
};

SetI_ *VectorSet::iterate(uint &elem) const {
  return new(ResourceObj::C_HEAP, mtInternal) VSetI_(this, elem);
}

//=============================================================================
//------------------------------next-------------------------------------------
// Find and return the next element of a vector set, or return garbage and
// make "VectorSetI::test()" fail.
uint VectorSetI::next(void)
{
  j++;                          // Next element in word
  mask = (mask & max_jint) << 1;// Next bit in word
  do {                          // Do While still have words
    while( mask ) {             // While have bits in word
      if( s->data[i] & mask ) { // If found a bit
        return (i<<5)+j;        // Return the bit address
      }
      j++;                      // Skip to next bit
      mask = (mask & max_jint) << 1;
    }
    j = 0;                      // No more bits in word; setup for next word
    mask = 1;
    for( i++; (i<s->size) && (!s->data[i]); i++ ); // Skip to non-zero word
  } while( i<s->size );
  return max_juint;             // No element, iterated them all
}
