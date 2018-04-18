/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_SIZES_HPP
#define SHARE_VM_UTILITIES_SIZES_HPP

#include "utilities/globalDefinitions.hpp"

// The following two classes are used to represent 'sizes' and 'offsets' in the VM;
// they serve as 'unit' types. ByteSize is used for sizes measured in bytes, while
// WordSize is used for sizes measured in machine words (i.e., 32bit or 64bit words
// depending on platform).
//
// The classes are defined with friend functions operating on them instead of member
// functions so that they (the classes) can be re-#define'd to int types in optimized
// mode. This allows full type checking and maximum safety in debug mode, and full
// optimizations (constant folding) and zero overhead (time and space wise) in the
// optimized build (some compilers do not optimize one-element value classes but
// instead create an object in memory - thus the overhead may be significant).
//
// Note: 1) DO NOT add new overloaded friend functions that do not have a unique function
//          function name but require signature types for resolution. This will not work
//          in optimized mode as both, ByteSize and WordSize are mapped to the same type
//          and thus the distinction would not be possible anymore (=> compiler errors).
//
//       2) DO NOT add non-static member functions as they cannot be mapped so something
//          compilable in the optimized build. Static member functions could be added
//          but require a corresponding class definition in the optimized build.
//
// These classes should help doing a transition from (currently) word-size based offsets
// to byte-size based offsets in the VM (this will be important if we desire to pack
// objects more densely in the VM for 64bit machines). Such a transition should proceed
// in two steps to minimize the risk of introducing hard-to-find bugs:
//
// a) first transition the whole VM into a form where all sizes are strongly typed
// b) change all WordSize's to ByteSize's where desired and fix the compilation errors


#ifdef ASSERT

class ByteSize {
 private:
  int _size;

  // Note: This constructor must be private to avoid implicit conversions!
  ByteSize(int size)                                  { _size = size; }

 public:
  // constructors
  inline friend ByteSize in_ByteSize(int size);

  // accessors
  inline friend int in_bytes(ByteSize x);

  // operators
  friend ByteSize operator + (ByteSize x, ByteSize y) { return ByteSize(in_bytes(x) + in_bytes(y)); }
  friend ByteSize operator - (ByteSize x, ByteSize y) { return ByteSize(in_bytes(x) - in_bytes(y)); }
  friend ByteSize operator * (ByteSize x, int      y) { return ByteSize(in_bytes(x) * y          ); }

  // comparison
  friend bool operator == (ByteSize x, ByteSize y)    { return in_bytes(x) == in_bytes(y); }
  friend bool operator != (ByteSize x, ByteSize y)    { return in_bytes(x) != in_bytes(y); }
  friend bool operator <  (ByteSize x, ByteSize y)    { return in_bytes(x) <  in_bytes(y); }
  friend bool operator <= (ByteSize x, ByteSize y)    { return in_bytes(x) <= in_bytes(y); }
  friend bool operator >  (ByteSize x, ByteSize y)    { return in_bytes(x) >  in_bytes(y); }
  friend bool operator >= (ByteSize x, ByteSize y)    { return in_bytes(x) >= in_bytes(y); }
};

inline ByteSize in_ByteSize(int size) { return ByteSize(size); }
inline int      in_bytes(ByteSize x)  { return x._size; }


class WordSize {
 private:
  int _size;

  // Note: This constructor must be private to avoid implicit conversions!
  WordSize(int size)                                  { _size = size; }

 public:
  // constructors
  inline friend WordSize in_WordSize(int size);

  // accessors
  inline friend int in_words(WordSize x);

  // operators
  friend WordSize operator + (WordSize x, WordSize y) { return WordSize(in_words(x) + in_words(y)); }
  friend WordSize operator - (WordSize x, WordSize y) { return WordSize(in_words(x) - in_words(y)); }
  friend WordSize operator * (WordSize x, int      y) { return WordSize(in_words(x) * y          ); }

  // comparison
  friend bool operator == (WordSize x, WordSize y)    { return in_words(x) == in_words(y); }
  friend bool operator != (WordSize x, WordSize y)    { return in_words(x) != in_words(y); }
  friend bool operator <  (WordSize x, WordSize y)    { return in_words(x) <  in_words(y); }
  friend bool operator <= (WordSize x, WordSize y)    { return in_words(x) <= in_words(y); }
  friend bool operator >  (WordSize x, WordSize y)    { return in_words(x) >  in_words(y); }
  friend bool operator >= (WordSize x, WordSize y)    { return in_words(x) >= in_words(y); }
};

inline WordSize in_WordSize(int size) { return WordSize(size); }
inline int      in_words(WordSize x)  { return x._size; }


#else // ASSERT

// The following definitions must match the corresponding friend declarations
// in the Byte/WordSize classes if they are typedef'ed to be int. This will
// be the case in optimized mode to ensure zero overhead for these types.
//
// Note: If a compiler does not inline these function calls away, one may
//       want to use #define's to make sure full optimization (constant
//       folding in particular) is possible.

typedef int ByteSize;
inline ByteSize in_ByteSize(int size)                 { return size; }
inline int      in_bytes   (ByteSize x)               { return x; }

typedef int WordSize;
inline WordSize in_WordSize(int size)                 { return size; }
inline int      in_words   (WordSize x)               { return x; }

#endif // ASSERT


// Use the following #define to get C++ field member offsets

#define byte_offset_of(klass,field)   in_ByteSize((int)offset_of(klass, field))

#endif // SHARE_VM_UTILITIES_SIZES_HPP
