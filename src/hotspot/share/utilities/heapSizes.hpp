/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_UTILITIES_HEAP_SIZES_HPP
#define SHARE_UTILITIES_HEAP_SIZES_HPP

#include "utilities/globalDefinitions.hpp"

enum class HeapWordUnit : size_t {};

constexpr HeapWordUnit in_HeapWordUnit(size_t size)      { return static_cast<HeapWordUnit>(size); }
constexpr size_t       in_heap_words(HeapWordUnit x)     { return static_cast<size_t>(x); }

constexpr HeapWordUnit operator +  (HeapWordUnit x, HeapWordUnit y) { return in_HeapWordUnit(in_heap_words(x) + in_heap_words(y)); }
constexpr HeapWordUnit operator -  (HeapWordUnit x, HeapWordUnit y) { return in_HeapWordUnit(in_heap_words(x) - in_heap_words(y)); }
constexpr HeapWordUnit operator *  (HeapWordUnit x, size_t       y) { return in_HeapWordUnit(in_heap_words(x) * y               ); }
constexpr HeapWordUnit operator /  (HeapWordUnit x, size_t       y) { return in_HeapWordUnit(in_heap_words(x) / y               ); }

inline HeapWordUnit& operator += (HeapWordUnit& x, HeapWordUnit y) { return x = x + y; }
inline HeapWordUnit& operator -= (HeapWordUnit& x, HeapWordUnit y) { return x = x - y; }

constexpr bool operator == (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) == in_heap_words(y); }
constexpr bool operator != (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) != in_heap_words(y); }
constexpr bool operator >  (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) >  in_heap_words(y); }
constexpr bool operator >= (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) >= in_heap_words(y); }
constexpr bool operator <  (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) <  in_heap_words(y); }
constexpr bool operator <= (HeapWordUnit x, HeapWordUnit y) { return in_heap_words(x) <= in_heap_words(y); }

// HeapByteUnit is a strongly-typed byte size for the heap. ByteSize (from sizes.hpp)
// is backed by int, which is too small for heap-scale byte counts.
enum class HeapByteUnit : size_t {};

constexpr HeapByteUnit in_HeapByteUnit(size_t size)      { return static_cast<HeapByteUnit>(size); }
constexpr size_t       in_heap_bytes(HeapByteUnit x)     { return static_cast<size_t>(x); }

constexpr HeapByteUnit operator +  (HeapByteUnit x, HeapByteUnit y) { return in_HeapByteUnit(in_heap_bytes(x) + in_heap_bytes(y)); }
constexpr HeapByteUnit operator -  (HeapByteUnit x, HeapByteUnit y) { return in_HeapByteUnit(in_heap_bytes(x) - in_heap_bytes(y)); }
constexpr HeapByteUnit operator *  (HeapByteUnit x, size_t       y) { return in_HeapByteUnit(in_heap_bytes(x) * y               ); }
constexpr HeapByteUnit operator /  (HeapByteUnit x, size_t       y) { return in_HeapByteUnit(in_heap_bytes(x) / y               ); }

constexpr bool operator == (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) == in_heap_bytes(y); }
constexpr bool operator != (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) != in_heap_bytes(y); }
constexpr bool operator >  (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) >  in_heap_bytes(y); }
constexpr bool operator >= (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) >= in_heap_bytes(y); }
constexpr bool operator <  (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) <  in_heap_bytes(y); }
constexpr bool operator <= (HeapByteUnit x, HeapByteUnit y) { return in_heap_bytes(x) <= in_heap_bytes(y); }

inline HeapByteUnit& operator += (HeapByteUnit& x, HeapByteUnit y) { return x = x + y; }
inline HeapByteUnit& operator -= (HeapByteUnit& x, HeapByteUnit y) { return x = x - y; }

constexpr HeapByteUnit to_HeapByteUnit(HeapWordUnit x)   { return in_HeapByteUnit(in_heap_words(x) * HeapWordSize); }
constexpr HeapWordUnit to_HeapWordUnit(HeapByteUnit x)   { return in_HeapWordUnit(in_heap_bytes(x) / HeapWordSize); }

#endif // SHARE_UTILITIES_HEAP_SIZES_HPP
