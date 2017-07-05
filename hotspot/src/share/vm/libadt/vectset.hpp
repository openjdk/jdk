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

#ifndef SHARE_VM_LIBADT_VECTSET_HPP
#define SHARE_VM_LIBADT_VECTSET_HPP

#include "libadt/set.hpp"

// Vector Sets - An Abstract Data Type
//INTERFACE

// These sets can grow or shrink, based on the initial size and the largest
// element currently in them.  Slow and bulky for sparse sets, these sets
// are super for dense sets.  They are fast and compact when dense.

// TIME:
// O(1) - Insert, Delete, Member, Sort
// O(max_element) - Create, Clear, Size, Copy, Union, Intersect, Difference,
//                  Equal, ChooseMember, Forall

// SPACE: (max_element)/(8*sizeof(int))


//------------------------------VectorSet--------------------------------------
class VectorSet : public Set {
friend class VectorSetI;        // Friendly iterator class
protected:
  uint size;                    // Size of data IN LONGWORDS (32bits)
  uint32 *data;                 // The data, bit packed

  void slamin( const VectorSet& s );     // Initialize one set with another
  int compare(const VectorSet &s) const; // Compare set contents
  void grow(uint newsize);               // Grow vector to required bitsize

public:
  VectorSet(Arena *arena);                      // Creates a new, empty set.
  VectorSet(const VectorSet &s) : Set(s._set_arena) {slamin(s);} // Set clone; deep-copy guts
  Set &operator =(const Set &s);                // Set clone; deep-copy guts
  VectorSet &operator =(const VectorSet &s)     // Set clone; deep-copy guts
  { if( &s != this ) { slamin(s); } return *this; }
  ~VectorSet() {}
  Set &clone(void) const { return *(new VectorSet(*this)); }

  Set &operator <<=(uint elem);          // Add member to set
  VectorSet operator << (uint elem)      // Add member to new set
  { VectorSet foo(*this); foo <<= elem; return foo; }
  Set &operator >>=(uint elem);          // Delete member from set
  VectorSet operator >> (uint elem)      // Delete member from new set
  { VectorSet foo(*this); foo >>= elem; return foo; }

  VectorSet &operator &=(const VectorSet &s); // Intersect sets into first set
  Set       &operator &=(const Set       &s); // Intersect sets into first set
  VectorSet operator & (const VectorSet &s) const
  { VectorSet foo(*this); foo &= s; return foo; }

  VectorSet &operator |=(const VectorSet &s); // Intersect sets into first set
  Set       &operator |=(const Set       &s); // Intersect sets into first set
  VectorSet operator | (const VectorSet &s) const
  { VectorSet foo(*this); foo |= s; return foo; }

  VectorSet &operator -=(const VectorSet &s); // Intersect sets into first set
  Set       &operator -=(const Set       &s); // Intersect sets into first set
  VectorSet operator - (const VectorSet &s) const
  { VectorSet foo(*this); foo -= s; return foo; }

  int operator ==(const VectorSet &s) const;  // True if sets are equal
  int operator ==(const Set       &s) const;  // True if sets are equal
  int operator < (const VectorSet &s) const;  // True if strict subset
  int operator < (const Set       &s) const;  // True if strict subset
  int operator <=(const VectorSet &s) const;  // True if subset relation holds.
  int operator <=(const Set       &s) const;  // True if subset relation holds.
  int disjoint   (const Set       &s) const;  // True if sets are disjoint

  int operator [](uint elem) const; // Test for membership
  uint getelem(void) const;         // Return a random element
  void Clear(void);                 // Clear a set
  uint Size(void) const;            // Number of elements in the Set.
  void Sort(void);                  // Sort before iterating
  int hash() const;                 // Hash function

  /* Removed for MCC BUG
     operator const VectorSet* (void) const { return this; } */
  const VectorSet *asVectorSet() const { return this; }

  // Expose internals for speed-critical fast iterators
  uint word_size() const { return size; }
  uint32 *EXPOSE() const { return data; }

  // Fast inlined "test and set".  Replaces the idiom:
  //     if( visited[idx] ) return;
  //     visited <<= idx;
  // With:
  //     if( visited.test_set(idx) ) return;
  //
  int test_set( uint elem ) {
    uint word = elem >> 5;           // Get the longword offset
    if( word >= size )               // Beyond the last?
      return test_set_grow(elem);    // Then grow; set; return 0;
    uint32 mask = 1L << (elem & 31); // Get bit mask
    uint32 datum = data[word] & mask;// Get bit
    data[word] |= mask;              // Set bit
    return datum;                    // Return bit
  }
  int test_set_grow( uint elem ) {    // Insert & return 0;
    (*this) <<= elem;                 // Insert into set
    return 0;                         // Return 0!
  }

  // Fast inlined test
  int test( uint elem ) const {
    uint word = elem >> 5;      // Get the longword offset
    if( word >= size ) return 0; // Beyond the last?
    uint32 mask = 1L << (elem & 31); // Get bit mask
    return data[word] & mask;   // Get bit
  }

  // Fast inlined set
  void set( uint elem ) {
    uint word = elem >> 5;      // Get the longword offset
    if( word >= size ) {        // Beyond the last?
      test_set_grow(elem);      // Then grow and set
    } else {
      uint32 mask = 1L << (elem & 31); // Get bit mask
      data[word] |= mask;       // Set bit
    }
  }


private:
  friend class VSetI_;
  SetI_ *iterate(uint&) const;
};

//------------------------------Iteration--------------------------------------
// Loop thru all elements of the set, setting "elem" to the element numbers
// in random order.  Inserted or deleted elements during this operation may
// or may not be iterated over; untouched elements will be affected once.
// Usage:  for( VectorSetI i(s); i.test(); i++ ) { body = i.elem; }

class VSetI_ : public SetI_ {
  friend class VectorSet;
  friend class VectorSetI;
  const VectorSet *s;
  uint i, j;
  uint32 mask;
  VSetI_(const VectorSet *vset);
  uint next(void);
  int test(void) { return i < s->size; }
};

class VectorSetI : public SetI {
public:
  VectorSetI( const VectorSet *s ) : SetI(s) { }
  void operator ++(void) { elem = ((VSetI_*)impl)->next(); }
  int test(void) { return ((VSetI_*)impl)->test(); }
};

#endif // SHARE_VM_LIBADT_VECTSET_HPP
