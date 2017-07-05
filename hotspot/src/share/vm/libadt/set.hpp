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

#ifndef SHARE_VM_LIBADT_SET_HPP
#define SHARE_VM_LIBADT_SET_HPP

#include "libadt/port.hpp"
#include "memory/allocation.hpp"

// Sets - An Abstract Data Type

//INTERFACE

class SparseSet;
class VectorSet;
class ListSet;
class CoSet;

class ostream;
class SetI_;

// These sets can grow or shrink, based on the initial size and the largest
// element currently in them.  Basically, they allow a bunch of bits to be
// grouped together, tested, set & cleared, intersected, etc.  The basic
// Set class is an abstract class, and cannot be constructed.  Instead,
// one of VectorSet, SparseSet, or ListSet is created.  Each variation has
// different asymptotic running times for different operations, and different
// constants of proportionality as well.
// {n = number of elements, N = largest element}

//              VectorSet       SparseSet       ListSet
// Create       O(N)            O(1)            O(1)
// Clear        O(N)            O(1)            O(1)
// Insert       O(1)            O(1)            O(log n)
// Delete       O(1)            O(1)            O(log n)
// Member       O(1)            O(1)            O(log n)
// Size         O(N)            O(1)            O(1)
// Copy         O(N)            O(n)            O(n)
// Union        O(N)            O(n)            O(n log n)
// Intersect    O(N)            O(n)            O(n log n)
// Difference   O(N)            O(n)            O(n log n)
// Equal        O(N)            O(n)            O(n log n)
// ChooseMember O(N)            O(1)            O(1)
// Sort         O(1)            O(n log n)      O(1)
// Forall       O(N)            O(n)            O(n)
// Complement   O(1)            O(1)            O(1)

// TIME:        N/32            n               8*n     Accesses
// SPACE:       N/8             4*N+4*n         8*n     Bytes

// Create:      Make an empty set
// Clear:       Remove all the elements of a Set
// Insert:      Insert an element into a Set; duplicates are ignored
// Delete:      Removes an element from a Set
// Member:      Tests for membership in a Set
// Size:        Returns the number of members of a Set
// Copy:        Copy or assign one Set to another
// Union:       Union 2 sets together
// Intersect:   Intersect 2 sets together
// Difference:  Compute A & !B; remove from set A those elements in set B
// Equal:       Test for equality between 2 sets
// ChooseMember Pick a random member
// Sort:        If no other operation changes the set membership, a following
//              Forall will iterate the members in ascending order.
// Forall:      Iterate over the elements of a Set.  Operations that modify
//              the set membership during iteration work, but the iterator may
//              skip any member or duplicate any member.
// Complement:  Only supported in the Co-Set variations.  It adds a small
//              constant-time test to every Set operation.
//
// PERFORMANCE ISSUES:
// If you "cast away" the specific set variation you are using, and then do
// operations on the basic "Set" object you will pay a virtual function call
// to get back the specific set variation.  On the other hand, using the
// generic Set means you can change underlying implementations by just
// changing the initial declaration.  Examples:
//      void foo(VectorSet vs1, VectorSet vs2) { vs1 |= vs2; }
// "foo" must be called with a VectorSet.  The vector set union operation
// is called directly.
//      void foo(Set vs1, Set vs2) { vs1 |= vs2; }
// "foo" may be called with *any* kind of sets; suppose it is called with
// VectorSets.  Two virtual function calls are used to figure out the that vs1
// and vs2 are VectorSets.  In addition, if vs2 is not a VectorSet then a
// temporary VectorSet copy of vs2 will be made before the union proceeds.
//
// VectorSets have a small constant.  Time and space are proportional to the
//   largest element.  Fine for dense sets and largest element < 10,000.
// SparseSets have a medium constant.  Time is proportional to the number of
//   elements, space is proportional to the largest element.
//   Fine (but big) with the largest element < 100,000.
// ListSets have a big constant.  Time *and space* are proportional to the
//   number of elements.  They work well for a few elements of *any* size
//   (i.e. sets of pointers)!

//------------------------------Set--------------------------------------------
class Set : public ResourceObj {
 public:

  // Creates a new, empty set.
  // DO NOT CONSTRUCT A Set.  THIS IS AN ABSTRACT CLASS, FOR INHERITENCE ONLY
  Set(Arena *arena) : _set_arena(arena) {};

  // Creates a new set from an existing set
  // DO NOT CONSTRUCT A Set.  THIS IS AN ABSTRACT CLASS, FOR INHERITENCE ONLY
  Set(const Set &) {};

  // Set assignment; deep-copy guts
  virtual Set &operator =(const Set &s)=0;
  virtual Set &clone(void) const=0;

  // Virtual destructor
  virtual ~Set() {};

  // Add member to set
  virtual Set &operator <<=(uint elem)=0;
  // virtual Set  operator << (uint elem);

  // Delete member from set
  virtual Set &operator >>=(uint elem)=0;
  // virtual Set  operator >> (uint elem);

  // Membership test.  Result is Zero (absent)/ Non-Zero (present)
  virtual int operator [](uint elem) const=0;

  // Intersect sets
  virtual Set &operator &=(const Set &s)=0;
  // virtual Set  operator & (const Set &s) const;

  // Union sets
  virtual Set &operator |=(const Set &s)=0;
  // virtual Set  operator | (const Set &s) const;

  // Difference sets
  virtual Set &operator -=(const Set &s)=0;
  // virtual Set  operator - (const Set &s) const;

  // Tests for equality.  Result is Zero (false)/ Non-Zero (true)
  virtual int operator ==(const Set &s) const=0;
  int operator !=(const Set &s) const { return !(*this == s); }
  virtual int disjoint(const Set &s) const=0;

  // Tests for strict subset.  Result is Zero (false)/ Non-Zero (true)
  virtual int operator < (const Set &s) const=0;
  int operator > (const Set &s) const { return s < *this; }

  // Tests for subset.  Result is Zero (false)/ Non-Zero (true)
  virtual int operator <=(const Set &s) const=0;
  int operator >=(const Set &s) const { return s <= *this; }

  // Return any member of the Set.  Undefined if the Set is empty.
  virtual uint getelem(void) const=0;

  // Clear all the elements in the Set
  virtual void Clear(void)=0;

  // Return the number of members in the Set
  virtual uint Size(void) const=0;

  // If an iterator follows a "Sort()" without any Set-modifying operations
  // inbetween then the iterator will visit the elements in ascending order.
  virtual void Sort(void)=0;

  // Convert a set to printable string in an allocated buffer.
  // The caller must deallocate the string.
  virtual char *setstr(void) const;

  // Print the Set on "stdout".  Can be conveniently called in the debugger
  void print() const;

  // Parse text from the string into the Set.  Return length parsed.
  virtual int parse(const char *s);

  // Convert a generic Set to a specific Set
  /* Removed for MCC BUG
     virtual operator const SparseSet* (void) const;
     virtual operator const VectorSet* (void) const;
     virtual operator const ListSet  * (void) const;
     virtual operator const CoSet    * (void) const; */
  virtual const SparseSet *asSparseSet(void) const;
  virtual const VectorSet *asVectorSet(void) const;
  virtual const ListSet   *asListSet  (void) const;
  virtual const CoSet     *asCoSet    (void) const;

  // Hash the set.  Sets of different types but identical elements will NOT
  // hash the same.  Same set type, same elements WILL hash the same.
  virtual int hash() const = 0;

protected:
  friend class SetI;
  friend class CoSet;
  virtual class SetI_ *iterate(uint&) const=0;

  // Need storeage for the set
  Arena *_set_arena;
};
typedef Set&((*Set_Constructor)(Arena *arena));
extern Set &ListSet_Construct(Arena *arena);
extern Set &VectorSet_Construct(Arena *arena);
extern Set &SparseSet_Construct(Arena *arena);

//------------------------------Iteration--------------------------------------
// Loop thru all elements of the set, setting "elem" to the element numbers
// in random order.  Inserted or deleted elements during this operation may
// or may not be iterated over; untouched elements will be affected once.

// Usage:  for( SetI  i(s); i.test(); i++ ) { body = i.elem; }   ...OR...
//         for( i.reset(s); i.test(); i++ ) { body = i.elem; }

class SetI_ : public ResourceObj {
protected:
  friend class SetI;
  virtual ~SetI_();
  virtual uint next(void)=0;
  virtual int test(void)=0;
};

class SetI {
protected:
  SetI_ *impl;
public:
  uint elem;                    // The publically accessible element

  SetI( const Set *s ) { impl = s->iterate(elem); }
  ~SetI() { delete impl; }
  void reset( const Set *s ) { delete impl; impl = s->iterate(elem); }
  void operator ++(void) { elem = impl->next(); }
  int test(void) { return impl->test(); }
};

#endif // SHARE_VM_LIBADT_SET_HPP
