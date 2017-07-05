/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_GLOBALDEFINITIONS_HPP
#define SHARE_VM_UTILITIES_GLOBALDEFINITIONS_HPP

#ifndef __STDC_FORMAT_MACROS
#define __STDC_FORMAT_MACROS
#endif

#ifdef TARGET_COMPILER_gcc
# include "utilities/globalDefinitions_gcc.hpp"
#endif
#ifdef TARGET_COMPILER_visCPP
# include "utilities/globalDefinitions_visCPP.hpp"
#endif
#ifdef TARGET_COMPILER_sparcWorks
# include "utilities/globalDefinitions_sparcWorks.hpp"
#endif
#ifdef TARGET_COMPILER_xlc
# include "utilities/globalDefinitions_xlc.hpp"
#endif

#ifndef NOINLINE
#define NOINLINE
#endif
#ifndef ALWAYSINLINE
#define ALWAYSINLINE inline
#endif
#ifndef PRAGMA_DIAG_PUSH
#define PRAGMA_DIAG_PUSH
#endif
#ifndef PRAGMA_DIAG_POP
#define PRAGMA_DIAG_POP
#endif
#ifndef PRAGMA_FORMAT_NONLITERAL_IGNORED
#define PRAGMA_FORMAT_NONLITERAL_IGNORED
#endif
#ifndef PRAGMA_FORMAT_IGNORED
#define PRAGMA_FORMAT_IGNORED
#endif
#ifndef PRAGMA_FORMAT_NONLITERAL_IGNORED_INTERNAL
#define PRAGMA_FORMAT_NONLITERAL_IGNORED_INTERNAL
#endif
#ifndef PRAGMA_FORMAT_NONLITERAL_IGNORED_EXTERNAL
#define PRAGMA_FORMAT_NONLITERAL_IGNORED_EXTERNAL
#endif
#ifndef ATTRIBUTE_PRINTF
#define ATTRIBUTE_PRINTF(fmt, vargs)
#endif
#ifndef ATTRIBUTE_SCANF
#define ATTRIBUTE_SCANF(fmt, vargs)
#endif


#include "utilities/macros.hpp"

// This file holds all globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

//----------------------------------------------------------------------------------------------------
// Constants

const int LogBytesPerShort   = 1;
const int LogBytesPerInt     = 2;
#ifdef _LP64
const int LogBytesPerWord    = 3;
#else
const int LogBytesPerWord    = 2;
#endif
const int LogBytesPerLong    = 3;

const int BytesPerShort      = 1 << LogBytesPerShort;
const int BytesPerInt        = 1 << LogBytesPerInt;
const int BytesPerWord       = 1 << LogBytesPerWord;
const int BytesPerLong       = 1 << LogBytesPerLong;

const int LogBitsPerByte     = 3;
const int LogBitsPerShort    = LogBitsPerByte + LogBytesPerShort;
const int LogBitsPerInt      = LogBitsPerByte + LogBytesPerInt;
const int LogBitsPerWord     = LogBitsPerByte + LogBytesPerWord;
const int LogBitsPerLong     = LogBitsPerByte + LogBytesPerLong;

const int BitsPerByte        = 1 << LogBitsPerByte;
const int BitsPerShort       = 1 << LogBitsPerShort;
const int BitsPerInt         = 1 << LogBitsPerInt;
const int BitsPerWord        = 1 << LogBitsPerWord;
const int BitsPerLong        = 1 << LogBitsPerLong;

const int WordAlignmentMask  = (1 << LogBytesPerWord) - 1;
const int LongAlignmentMask  = (1 << LogBytesPerLong) - 1;

const int WordsPerLong       = 2;       // Number of stack entries for longs

const int oopSize            = sizeof(char*); // Full-width oop
extern int heapOopSize;                       // Oop within a java object
const int wordSize           = sizeof(char*);
const int longSize           = sizeof(jlong);
const int jintSize           = sizeof(jint);
const int size_tSize         = sizeof(size_t);

const int BytesPerOop        = BytesPerWord;  // Full-width oop

extern int LogBytesPerHeapOop;                // Oop within a java object
extern int LogBitsPerHeapOop;
extern int BytesPerHeapOop;
extern int BitsPerHeapOop;

const int BitsPerJavaInteger = 32;
const int BitsPerJavaLong    = 64;
const int BitsPerSize_t      = size_tSize * BitsPerByte;

// Size of a char[] needed to represent a jint as a string in decimal.
const int jintAsStringSize = 12;

// In fact this should be
// log2_intptr(sizeof(class JavaThread)) - log2_intptr(64);
// see os::set_memory_serialize_page()
#ifdef _LP64
const int SerializePageShiftCount = 4;
#else
const int SerializePageShiftCount = 3;
#endif

// An opaque struct of heap-word width, so that HeapWord* can be a generic
// pointer into the heap.  We require that object sizes be measured in
// units of heap words, so that that
//   HeapWord* hw;
//   hw += oop(hw)->foo();
// works, where foo is a method (like size or scavenge) that returns the
// object size.
class HeapWord {
  friend class VMStructs;
 private:
  char* i;
#ifndef PRODUCT
 public:
  char* value() { return i; }
#endif
};

// Analogous opaque struct for metadata allocated from
// metaspaces.
class MetaWord {
 private:
  char* i;
};

// HeapWordSize must be 2^LogHeapWordSize.
const int HeapWordSize        = sizeof(HeapWord);
#ifdef _LP64
const int LogHeapWordSize     = 3;
#else
const int LogHeapWordSize     = 2;
#endif
const int HeapWordsPerLong    = BytesPerLong / HeapWordSize;
const int LogHeapWordsPerLong = LogBytesPerLong - LogHeapWordSize;

// The larger HeapWordSize for 64bit requires larger heaps
// for the same application running in 64bit.  See bug 4967770.
// The minimum alignment to a heap word size is done.  Other
// parts of the memory system may require additional alignment
// and are responsible for those alignments.
#ifdef _LP64
#define ScaleForWordSize(x) align_size_down_((x) * 13 / 10, HeapWordSize)
#else
#define ScaleForWordSize(x) (x)
#endif

// The minimum number of native machine words necessary to contain "byte_size"
// bytes.
inline size_t heap_word_size(size_t byte_size) {
  return (byte_size + (HeapWordSize-1)) >> LogHeapWordSize;
}

const size_t K                  = 1024;
const size_t M                  = K*K;
const size_t G                  = M*K;
const size_t HWperKB            = K / sizeof(HeapWord);

const jint min_jint = (jint)1 << (sizeof(jint)*BitsPerByte-1); // 0x80000000 == smallest jint
const jint max_jint = (juint)min_jint - 1;                     // 0x7FFFFFFF == largest jint

// Constants for converting from a base unit to milli-base units.  For
// example from seconds to milliseconds and microseconds

const int MILLIUNITS    = 1000;         // milli units per base unit
const int MICROUNITS    = 1000000;      // micro units per base unit
const int NANOUNITS     = 1000000000;   // nano units per base unit

const jlong NANOSECS_PER_SEC      = CONST64(1000000000);
const jint  NANOSECS_PER_MILLISEC = 1000000;

inline const char* proper_unit_for_byte_size(size_t s) {
#ifdef _LP64
  if (s >= 10*G) {
    return "G";
  }
#endif
  if (s >= 10*M) {
    return "M";
  } else if (s >= 10*K) {
    return "K";
  } else {
    return "B";
  }
}

template <class T>
inline T byte_size_in_proper_unit(T s) {
#ifdef _LP64
  if (s >= 10*G) {
    return (T)(s/G);
  }
#endif
  if (s >= 10*M) {
    return (T)(s/M);
  } else if (s >= 10*K) {
    return (T)(s/K);
  } else {
    return s;
  }
}

//----------------------------------------------------------------------------------------------------
// VM type definitions

// intx and uintx are the 'extended' int and 'extended' unsigned int types;
// they are 32bit wide on a 32-bit platform, and 64bit wide on a 64bit platform.

typedef intptr_t  intx;
typedef uintptr_t uintx;

const intx  min_intx  = (intx)1 << (sizeof(intx)*BitsPerByte-1);
const intx  max_intx  = (uintx)min_intx - 1;
const uintx max_uintx = (uintx)-1;

// Table of values:
//      sizeof intx         4               8
// min_intx             0x80000000      0x8000000000000000
// max_intx             0x7FFFFFFF      0x7FFFFFFFFFFFFFFF
// max_uintx            0xFFFFFFFF      0xFFFFFFFFFFFFFFFF

typedef unsigned int uint;   NEEDS_CLEANUP


//----------------------------------------------------------------------------------------------------
// Java type definitions

// All kinds of 'plain' byte addresses
typedef   signed char s_char;
typedef unsigned char u_char;
typedef u_char*       address;
typedef uintptr_t     address_word; // unsigned integer which will hold a pointer
                                    // except for some implementations of a C++
                                    // linkage pointer to function. Should never
                                    // need one of those to be placed in this
                                    // type anyway.

//  Utility functions to "portably" (?) bit twiddle pointers
//  Where portable means keep ANSI C++ compilers quiet

inline address       set_address_bits(address x, int m)       { return address(intptr_t(x) | m); }
inline address       clear_address_bits(address x, int m)     { return address(intptr_t(x) & ~m); }

//  Utility functions to "portably" make cast to/from function pointers.

inline address_word  mask_address_bits(address x, int m)      { return address_word(x) & m; }
inline address_word  castable_address(address x)              { return address_word(x) ; }
inline address_word  castable_address(void* x)                { return address_word(x) ; }

// Pointer subtraction.
// The idea here is to avoid ptrdiff_t, which is signed and so doesn't have
// the range we might need to find differences from one end of the heap
// to the other.
// A typical use might be:
//     if (pointer_delta(end(), top()) >= size) {
//       // enough room for an object of size
//       ...
// and then additions like
//       ... top() + size ...
// are safe because we know that top() is at least size below end().
inline size_t pointer_delta(const void* left,
                            const void* right,
                            size_t element_size) {
  return (((uintptr_t) left) - ((uintptr_t) right)) / element_size;
}
// A version specialized for HeapWord*'s.
inline size_t pointer_delta(const HeapWord* left, const HeapWord* right) {
  return pointer_delta(left, right, sizeof(HeapWord));
}
// A version specialized for MetaWord*'s.
inline size_t pointer_delta(const MetaWord* left, const MetaWord* right) {
  return pointer_delta(left, right, sizeof(MetaWord));
}

//
// ANSI C++ does not allow casting from one pointer type to a function pointer
// directly without at best a warning. This macro accomplishes it silently
// In every case that is present at this point the value be cast is a pointer
// to a C linkage function. In some case the type used for the cast reflects
// that linkage and a picky compiler would not complain. In other cases because
// there is no convenient place to place a typedef with extern C linkage (i.e
// a platform dependent header file) it doesn't. At this point no compiler seems
// picky enough to catch these instances (which are few). It is possible that
// using templates could fix these for all cases. This use of templates is likely
// so far from the middle of the road that it is likely to be problematic in
// many C++ compilers.
//
#define CAST_TO_FN_PTR(func_type, value) ((func_type)(castable_address(value)))
#define CAST_FROM_FN_PTR(new_type, func_ptr) ((new_type)((address_word)(func_ptr)))

// Unsigned byte types for os and stream.hpp

// Unsigned one, two, four and eigth byte quantities used for describing
// the .class file format. See JVM book chapter 4.

typedef jubyte  u1;
typedef jushort u2;
typedef juint   u4;
typedef julong  u8;

const jubyte  max_jubyte  = (jubyte)-1;  // 0xFF       largest jubyte
const jushort max_jushort = (jushort)-1; // 0xFFFF     largest jushort
const juint   max_juint   = (juint)-1;   // 0xFFFFFFFF largest juint
const julong  max_julong  = (julong)-1;  // 0xFF....FF largest julong

typedef jbyte  s1;
typedef jshort s2;
typedef jint   s4;
typedef jlong  s8;

//----------------------------------------------------------------------------------------------------
// JVM spec restrictions

const int max_method_code_size = 64*K - 1;  // JVM spec, 2nd ed. section 4.8.1 (p.134)

// Default ProtectionDomainCacheSize values

const int defaultProtectionDomainCacheSize = NOT_LP64(137) LP64_ONLY(2017);

//----------------------------------------------------------------------------------------------------
// Default and minimum StringTableSize values

const int defaultStringTableSize = NOT_LP64(1009) LP64_ONLY(60013);
const int minimumStringTableSize = 1009;

const int defaultSymbolTableSize = 20011;
const int minimumSymbolTableSize = 1009;


//----------------------------------------------------------------------------------------------------
// HotSwap - for JVMTI   aka Class File Replacement and PopFrame
//
// Determines whether on-the-fly class replacement and frame popping are enabled.

#define HOTSWAP

//----------------------------------------------------------------------------------------------------
// Object alignment, in units of HeapWords.
//
// Minimum is max(BytesPerLong, BytesPerDouble, BytesPerOop) / HeapWordSize, so jlong, jdouble and
// reference fields can be naturally aligned.

extern int MinObjAlignment;
extern int MinObjAlignmentInBytes;
extern int MinObjAlignmentInBytesMask;

extern int LogMinObjAlignment;
extern int LogMinObjAlignmentInBytes;

const int LogKlassAlignmentInBytes = 3;
const int LogKlassAlignment        = LogKlassAlignmentInBytes - LogHeapWordSize;
const int KlassAlignmentInBytes    = 1 << LogKlassAlignmentInBytes;
const int KlassAlignment           = KlassAlignmentInBytes / HeapWordSize;

// Maximal size of heap where unscaled compression can be used. Also upper bound
// for heap placement: 4GB.
const  uint64_t UnscaledOopHeapMax = (uint64_t(max_juint) + 1);
// Maximal size of heap where compressed oops can be used. Also upper bound for heap
// placement for zero based compression algorithm: UnscaledOopHeapMax << LogMinObjAlignmentInBytes.
extern uint64_t OopEncodingHeapMax;

// Maximal size of compressed class space. Above this limit compression is not possible.
// Also upper bound for placement of zero based class space. (Class space is further limited
// to be < 3G, see arguments.cpp.)
const  uint64_t KlassEncodingMetaspaceMax = (uint64_t(max_juint) + 1) << LogKlassAlignmentInBytes;

// Machine dependent stuff

// States of Restricted Transactional Memory usage.
enum RTMState {
  NoRTM      = 0x2, // Don't use RTM
  UseRTM     = 0x1, // Use RTM
  ProfileRTM = 0x0  // Use RTM with abort ratio calculation
};

// The maximum size of the code cache.  Can be overridden by targets.
#define CODE_CACHE_SIZE_LIMIT (2*G)
// Allow targets to reduce the default size of the code cache.
#define CODE_CACHE_DEFAULT_LIMIT CODE_CACHE_SIZE_LIMIT

#ifdef TARGET_ARCH_x86
# include "globalDefinitions_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "globalDefinitions_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "globalDefinitions_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "globalDefinitions_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "globalDefinitions_ppc.hpp"
#endif
#ifdef TARGET_ARCH_aarch64
# include "globalDefinitions_aarch64.hpp"
#endif

#ifndef INCLUDE_RTM_OPT
#define INCLUDE_RTM_OPT 0
#endif
#if INCLUDE_RTM_OPT
#define RTM_OPT_ONLY(code) code
#else
#define RTM_OPT_ONLY(code)
#endif

// To assure the IRIW property on processors that are not multiple copy
// atomic, sync instructions must be issued between volatile reads to
// assure their ordering, instead of after volatile stores.
// (See "A Tutorial Introduction to the ARM and POWER Relaxed Memory Models"
// by Luc Maranget, Susmit Sarkar and Peter Sewell, INRIA/Cambridge)
#ifdef CPU_NOT_MULTIPLE_COPY_ATOMIC
const bool support_IRIW_for_not_multiple_copy_atomic_cpu = true;
#else
const bool support_IRIW_for_not_multiple_copy_atomic_cpu = false;
#endif

// The byte alignment to be used by Arena::Amalloc.  See bugid 4169348.
// Note: this value must be a power of 2

#define ARENA_AMALLOC_ALIGNMENT (2*BytesPerWord)

// Signed variants of alignment helpers.  There are two versions of each, a macro
// for use in places like enum definitions that require compile-time constant
// expressions and a function for all other places so as to get type checking.

#define align_size_up_(size, alignment) (((size) + ((alignment) - 1)) & ~((alignment) - 1))

inline bool is_size_aligned(size_t size, size_t alignment) {
  return align_size_up_(size, alignment) == size;
}

inline bool is_ptr_aligned(void* ptr, size_t alignment) {
  return align_size_up_((intptr_t)ptr, (intptr_t)alignment) == (intptr_t)ptr;
}

inline intptr_t align_size_up(intptr_t size, intptr_t alignment) {
  return align_size_up_(size, alignment);
}

#define align_size_down_(size, alignment) ((size) & ~((alignment) - 1))

inline intptr_t align_size_down(intptr_t size, intptr_t alignment) {
  return align_size_down_(size, alignment);
}

#define is_size_aligned_(size, alignment) ((size) == (align_size_up_(size, alignment)))

inline void* align_ptr_up(const void* ptr, size_t alignment) {
  return (void*)align_size_up((intptr_t)ptr, (intptr_t)alignment);
}

inline void* align_ptr_down(void* ptr, size_t alignment) {
  return (void*)align_size_down((intptr_t)ptr, (intptr_t)alignment);
}

// Align metaspace objects by rounding up to natural word boundary

inline intptr_t align_metadata_size(intptr_t size) {
  return align_size_up(size, 1);
}

// Align objects in the Java Heap by rounding up their size, in HeapWord units.
// Since the size is given in words this is somewhat of a nop, but
// distinguishes it from align_object_size.
inline intptr_t align_object_size(intptr_t size) {
  return align_size_up(size, MinObjAlignment);
}

inline bool is_object_aligned(intptr_t addr) {
  return addr == align_object_size(addr);
}

// Pad out certain offsets to jlong alignment, in HeapWord units.

inline intptr_t align_object_offset(intptr_t offset) {
  return align_size_up(offset, HeapWordsPerLong);
}

// Align down with a lower bound. If the aligning results in 0, return 'alignment'.

inline size_t align_size_down_bounded(size_t size, size_t alignment) {
  size_t aligned_size = align_size_down_(size, alignment);
  return aligned_size > 0 ? aligned_size : alignment;
}

// Clamp an address to be within a specific page
// 1. If addr is on the page it is returned as is
// 2. If addr is above the page_address the start of the *next* page will be returned
// 3. Otherwise, if addr is below the page_address the start of the page will be returned
inline address clamp_address_in_page(address addr, address page_address, intptr_t page_size) {
  if (align_size_down(intptr_t(addr), page_size) == align_size_down(intptr_t(page_address), page_size)) {
    // address is in the specified page, just return it as is
    return addr;
  } else if (addr > page_address) {
    // address is above specified page, return start of next page
    return (address)align_size_down(intptr_t(page_address), page_size) + page_size;
  } else {
    // address is below specified page, return start of page
    return (address)align_size_down(intptr_t(page_address), page_size);
  }
}


// The expected size in bytes of a cache line, used to pad data structures.
#ifndef DEFAULT_CACHE_LINE_SIZE
  #define DEFAULT_CACHE_LINE_SIZE 64
#endif


//----------------------------------------------------------------------------------------------------
// Utility macros for compilers
// used to silence compiler warnings

#define Unused_Variable(var) var


//----------------------------------------------------------------------------------------------------
// Miscellaneous

// 6302670 Eliminate Hotspot __fabsf dependency
// All fabs() callers should call this function instead, which will implicitly
// convert the operand to double, avoiding a dependency on __fabsf which
// doesn't exist in early versions of Solaris 8.
inline double fabsd(double value) {
  return fabs(value);
}

// Returns numerator/denominator as percentage value from 0 to 100. If denominator
// is zero, return 0.0.
template<typename T>
inline double percent_of(T numerator, T denominator) {
  return denominator != 0 ? (double)numerator / denominator * 100.0 : 0.0;
}

//----------------------------------------------------------------------------------------------------
// Special casts
// Cast floats into same-size integers and vice-versa w/o changing bit-pattern
typedef union {
  jfloat f;
  jint i;
} FloatIntConv;

typedef union {
  jdouble d;
  jlong l;
  julong ul;
} DoubleLongConv;

inline jint    jint_cast    (jfloat  x)  { return ((FloatIntConv*)&x)->i; }
inline jfloat  jfloat_cast  (jint    x)  { return ((FloatIntConv*)&x)->f; }

inline jlong   jlong_cast   (jdouble x)  { return ((DoubleLongConv*)&x)->l;  }
inline julong  julong_cast  (jdouble x)  { return ((DoubleLongConv*)&x)->ul; }
inline jdouble jdouble_cast (jlong   x)  { return ((DoubleLongConv*)&x)->d;  }

inline jint low (jlong value)                    { return jint(value); }
inline jint high(jlong value)                    { return jint(value >> 32); }

// the fancy casts are a hopefully portable way
// to do unsigned 32 to 64 bit type conversion
inline void set_low (jlong* value, jint low )    { *value &= (jlong)0xffffffff << 32;
                                                   *value |= (jlong)(julong)(juint)low; }

inline void set_high(jlong* value, jint high)    { *value &= (jlong)(julong)(juint)0xffffffff;
                                                   *value |= (jlong)high       << 32; }

inline jlong jlong_from(jint h, jint l) {
  jlong result = 0; // initialization to avoid warning
  set_high(&result, h);
  set_low(&result,  l);
  return result;
}

union jlong_accessor {
  jint  words[2];
  jlong long_value;
};

void basic_types_init(); // cannot define here; uses assert


// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicType {
  T_BOOLEAN     =  4,
  T_CHAR        =  5,
  T_FLOAT       =  6,
  T_DOUBLE      =  7,
  T_BYTE        =  8,
  T_SHORT       =  9,
  T_INT         = 10,
  T_LONG        = 11,
  T_OBJECT      = 12,
  T_ARRAY       = 13,
  T_VOID        = 14,
  T_ADDRESS     = 15,
  T_NARROWOOP   = 16,
  T_METADATA    = 17,
  T_NARROWKLASS = 18,
  T_CONFLICT    = 19, // for stack value type with conflicting contents
  T_ILLEGAL     = 99
};

inline bool is_java_primitive(BasicType t) {
  return T_BOOLEAN <= t && t <= T_LONG;
}

inline bool is_subword_type(BasicType t) {
  // these guys are processed exactly like T_INT in calling sequences:
  return (t == T_BOOLEAN || t == T_CHAR || t == T_BYTE || t == T_SHORT);
}

inline bool is_signed_subword_type(BasicType t) {
  return (t == T_BYTE || t == T_SHORT);
}

// Convert a char from a classfile signature to a BasicType
inline BasicType char2type(char c) {
  switch( c ) {
  case 'B': return T_BYTE;
  case 'C': return T_CHAR;
  case 'D': return T_DOUBLE;
  case 'F': return T_FLOAT;
  case 'I': return T_INT;
  case 'J': return T_LONG;
  case 'S': return T_SHORT;
  case 'Z': return T_BOOLEAN;
  case 'V': return T_VOID;
  case 'L': return T_OBJECT;
  case '[': return T_ARRAY;
  }
  return T_ILLEGAL;
}

extern char type2char_tab[T_CONFLICT+1];     // Map a BasicType to a jchar
inline char type2char(BasicType t) { return (uint)t < T_CONFLICT+1 ? type2char_tab[t] : 0; }
extern int type2size[T_CONFLICT+1];         // Map BasicType to result stack elements
extern const char* type2name_tab[T_CONFLICT+1];     // Map a BasicType to a jchar
inline const char* type2name(BasicType t) { return (uint)t < T_CONFLICT+1 ? type2name_tab[t] : NULL; }
extern BasicType name2type(const char* name);

// Auxiliary math routines
// least common multiple
extern size_t lcm(size_t a, size_t b);


// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicTypeSize {
  T_BOOLEAN_size     = 1,
  T_CHAR_size        = 1,
  T_FLOAT_size       = 1,
  T_DOUBLE_size      = 2,
  T_BYTE_size        = 1,
  T_SHORT_size       = 1,
  T_INT_size         = 1,
  T_LONG_size        = 2,
  T_OBJECT_size      = 1,
  T_ARRAY_size       = 1,
  T_NARROWOOP_size   = 1,
  T_NARROWKLASS_size = 1,
  T_VOID_size        = 0
};


// maps a BasicType to its instance field storage type:
// all sub-word integral types are widened to T_INT
extern BasicType type2field[T_CONFLICT+1];
extern BasicType type2wfield[T_CONFLICT+1];


// size in bytes
enum ArrayElementSize {
  T_BOOLEAN_aelem_bytes     = 1,
  T_CHAR_aelem_bytes        = 2,
  T_FLOAT_aelem_bytes       = 4,
  T_DOUBLE_aelem_bytes      = 8,
  T_BYTE_aelem_bytes        = 1,
  T_SHORT_aelem_bytes       = 2,
  T_INT_aelem_bytes         = 4,
  T_LONG_aelem_bytes        = 8,
#ifdef _LP64
  T_OBJECT_aelem_bytes      = 8,
  T_ARRAY_aelem_bytes       = 8,
#else
  T_OBJECT_aelem_bytes      = 4,
  T_ARRAY_aelem_bytes       = 4,
#endif
  T_NARROWOOP_aelem_bytes   = 4,
  T_NARROWKLASS_aelem_bytes = 4,
  T_VOID_aelem_bytes        = 0
};

extern int _type2aelembytes[T_CONFLICT+1]; // maps a BasicType to nof bytes used by its array element
#ifdef ASSERT
extern int type2aelembytes(BasicType t, bool allow_address = false); // asserts
#else
inline int type2aelembytes(BasicType t, bool allow_address = false) { return _type2aelembytes[t]; }
#endif


// JavaValue serves as a container for arbitrary Java values.

class JavaValue {

 public:
  typedef union JavaCallValue {
    jfloat   f;
    jdouble  d;
    jint     i;
    jlong    l;
    jobject  h;
  } JavaCallValue;

 private:
  BasicType _type;
  JavaCallValue _value;

 public:
  JavaValue(BasicType t = T_ILLEGAL) { _type = t; }

  JavaValue(jfloat value) {
    _type    = T_FLOAT;
    _value.f = value;
  }

  JavaValue(jdouble value) {
    _type    = T_DOUBLE;
    _value.d = value;
  }

 jfloat get_jfloat() const { return _value.f; }
 jdouble get_jdouble() const { return _value.d; }
 jint get_jint() const { return _value.i; }
 jlong get_jlong() const { return _value.l; }
 jobject get_jobject() const { return _value.h; }
 JavaCallValue* get_value_addr() { return &_value; }
 BasicType get_type() const { return _type; }

 void set_jfloat(jfloat f) { _value.f = f;}
 void set_jdouble(jdouble d) { _value.d = d;}
 void set_jint(jint i) { _value.i = i;}
 void set_jlong(jlong l) { _value.l = l;}
 void set_jobject(jobject h) { _value.h = h;}
 void set_type(BasicType t) { _type = t; }

 jboolean get_jboolean() const { return (jboolean) (_value.i);}
 jbyte get_jbyte() const { return (jbyte) (_value.i);}
 jchar get_jchar() const { return (jchar) (_value.i);}
 jshort get_jshort() const { return (jshort) (_value.i);}

};


#define STACK_BIAS      0
// V9 Sparc CPU's running in 64 Bit mode use a stack bias of 7ff
// in order to extend the reach of the stack pointer.
#if defined(SPARC) && defined(_LP64)
#undef STACK_BIAS
#define STACK_BIAS      0x7ff
#endif


// TosState describes the top-of-stack state before and after the execution of
// a bytecode or method. The top-of-stack value may be cached in one or more CPU
// registers. The TosState corresponds to the 'machine representation' of this cached
// value. There's 4 states corresponding to the JAVA types int, long, float & double
// as well as a 5th state in case the top-of-stack value is actually on the top
// of stack (in memory) and thus not cached. The atos state corresponds to the itos
// state when it comes to machine representation but is used separately for (oop)
// type specific operations (e.g. verification code).

enum TosState {         // describes the tos cache contents
  btos = 0,             // byte, bool tos cached
  ctos = 1,             // char tos cached
  stos = 2,             // short tos cached
  itos = 3,             // int tos cached
  ltos = 4,             // long tos cached
  ftos = 5,             // float tos cached
  dtos = 6,             // double tos cached
  atos = 7,             // object cached
  vtos = 8,             // tos not cached
  number_of_states,
  ilgl                  // illegal state: should not occur
};


inline TosState as_TosState(BasicType type) {
  switch (type) {
    case T_BYTE   : return btos;
    case T_BOOLEAN: return btos; // FIXME: Add ztos
    case T_CHAR   : return ctos;
    case T_SHORT  : return stos;
    case T_INT    : return itos;
    case T_LONG   : return ltos;
    case T_FLOAT  : return ftos;
    case T_DOUBLE : return dtos;
    case T_VOID   : return vtos;
    case T_ARRAY  : // fall through
    case T_OBJECT : return atos;
  }
  return ilgl;
}

inline BasicType as_BasicType(TosState state) {
  switch (state) {
    //case ztos: return T_BOOLEAN;//FIXME
    case btos : return T_BYTE;
    case ctos : return T_CHAR;
    case stos : return T_SHORT;
    case itos : return T_INT;
    case ltos : return T_LONG;
    case ftos : return T_FLOAT;
    case dtos : return T_DOUBLE;
    case atos : return T_OBJECT;
    case vtos : return T_VOID;
  }
  return T_ILLEGAL;
}


// Helper function to convert BasicType info into TosState
// Note: Cannot define here as it uses global constant at the time being.
TosState as_TosState(BasicType type);


// JavaThreadState keeps track of which part of the code a thread is executing in. This
// information is needed by the safepoint code.
//
// There are 4 essential states:
//
//  _thread_new         : Just started, but not executed init. code yet (most likely still in OS init code)
//  _thread_in_native   : In native code. This is a safepoint region, since all oops will be in jobject handles
//  _thread_in_vm       : Executing in the vm
//  _thread_in_Java     : Executing either interpreted or compiled Java code (or could be in a stub)
//
// Each state has an associated xxxx_trans state, which is an intermediate state used when a thread is in
// a transition from one state to another. These extra states makes it possible for the safepoint code to
// handle certain thread_states without having to suspend the thread - making the safepoint code faster.
//
// Given a state, the xxxx_trans state can always be found by adding 1.
//
enum JavaThreadState {
  _thread_uninitialized     =  0, // should never happen (missing initialization)
  _thread_new               =  2, // just starting up, i.e., in process of being initialized
  _thread_new_trans         =  3, // corresponding transition state (not used, included for completness)
  _thread_in_native         =  4, // running in native code
  _thread_in_native_trans   =  5, // corresponding transition state
  _thread_in_vm             =  6, // running in VM
  _thread_in_vm_trans       =  7, // corresponding transition state
  _thread_in_Java           =  8, // running in Java or in stub code
  _thread_in_Java_trans     =  9, // corresponding transition state (not used, included for completness)
  _thread_blocked           = 10, // blocked in vm
  _thread_blocked_trans     = 11, // corresponding transition state
  _thread_max_state         = 12  // maximum thread state+1 - used for statistics allocation
};


// Handy constants for deciding which compiler mode to use.
enum MethodCompilation {
  InvocationEntryBci = -1     // i.e., not a on-stack replacement compilation
};

// Enumeration to distinguish tiers of compilation
enum CompLevel {
  CompLevel_any               = -1,
  CompLevel_all               = -1,
  CompLevel_none              = 0,         // Interpreter
  CompLevel_simple            = 1,         // C1
  CompLevel_limited_profile   = 2,         // C1, invocation & backedge counters
  CompLevel_full_profile      = 3,         // C1, invocation & backedge counters + mdo
  CompLevel_full_optimization = 4,         // C2, Shark or JVMCI

#if defined(COMPILER2) || defined(SHARK) || INCLUDE_JVMCI
  CompLevel_highest_tier      = CompLevel_full_optimization,  // pure C2 and tiered or JVMCI and tiered
#elif defined(COMPILER1)
  CompLevel_highest_tier      = CompLevel_simple,             // pure C1 or JVMCI
#else
  CompLevel_highest_tier      = CompLevel_none,
#endif

#if defined(TIERED)
  CompLevel_initial_compile   = CompLevel_full_profile        // tiered
#elif defined(COMPILER1) || INCLUDE_JVMCI
  CompLevel_initial_compile   = CompLevel_simple              // pure C1 or JVMCI
#elif defined(COMPILER2) || defined(SHARK)
  CompLevel_initial_compile   = CompLevel_full_optimization   // pure C2
#else
  CompLevel_initial_compile   = CompLevel_none
#endif
};

inline bool is_c1_compile(int comp_level) {
  return comp_level > CompLevel_none && comp_level < CompLevel_full_optimization;
}

inline bool is_c2_compile(int comp_level) {
  return comp_level == CompLevel_full_optimization;
}

inline bool is_highest_tier_compile(int comp_level) {
  return comp_level == CompLevel_highest_tier;
}

inline bool is_compile(int comp_level) {
  return is_c1_compile(comp_level) || is_c2_compile(comp_level);
}

//----------------------------------------------------------------------------------------------------
// 'Forward' declarations of frequently used classes
// (in order to reduce interface dependencies & reduce
// number of unnecessary compilations after changes)

class ClassFileStream;

class Event;

class Thread;
class  VMThread;
class  JavaThread;
class Threads;

class VM_Operation;
class VMOperationQueue;

class CodeBlob;
class  nmethod;
class  OSRAdapter;
class  I2CAdapter;
class  C2IAdapter;
class CompiledIC;
class relocInfo;
class ScopeDesc;
class PcDesc;

class Recompiler;
class Recompilee;
class RecompilationPolicy;
class RFrame;
class  CompiledRFrame;
class  InterpretedRFrame;

class frame;

class vframe;
class   javaVFrame;
class     interpretedVFrame;
class     compiledVFrame;
class     deoptimizedVFrame;
class   externalVFrame;
class     entryVFrame;

class RegisterMap;

class Mutex;
class Monitor;
class BasicLock;
class BasicObjectLock;

class PeriodicTask;

class JavaCallWrapper;

class   oopDesc;
class   metaDataOopDesc;

class NativeCall;

class zone;

class StubQueue;

class outputStream;

class ResourceArea;

class DebugInformationRecorder;
class ScopeValue;
class CompressedStream;
class   DebugInfoReadStream;
class   DebugInfoWriteStream;
class LocationValue;
class ConstantValue;
class IllegalValue;

class PrivilegedElement;
class MonitorArray;

class MonitorInfo;

class OffsetClosure;
class OopMapCache;
class InterpreterOopMap;
class OopMapCacheEntry;
class OSThread;

typedef int (*OSThreadStartFunc)(void*);

class Space;

class JavaValue;
class methodHandle;
class JavaCallArguments;

// Basic support for errors (general debug facilities not defined at this point fo the include phase)

extern void basic_fatal(const char* msg);


//----------------------------------------------------------------------------------------------------
// Special constants for debugging

const jint     badInt           = -3;                       // generic "bad int" value
const long     badAddressVal    = -2;                       // generic "bad address" value
const long     badOopVal        = -1;                       // generic "bad oop" value
const intptr_t badHeapOopVal    = (intptr_t) CONST64(0x2BAD4B0BBAADBABE); // value used to zap heap after GC
const int      badHandleValue   = 0xBC;                     // value used to zap vm handle area
const int      badResourceValue = 0xAB;                     // value used to zap resource area
const int      freeBlockPad     = 0xBA;                     // value used to pad freed blocks.
const int      uninitBlockPad   = 0xF1;                     // value used to zap newly malloc'd blocks.
const juint    uninitMetaWordVal= 0xf7f7f7f7;               // value used to zap newly allocated metachunk
const intptr_t badJNIHandleVal  = (intptr_t) UCONST64(0xFEFEFEFEFEFEFEFE); // value used to zap jni handle area
const juint    badHeapWordVal   = 0xBAADBABE;               // value used to zap heap after GC
const juint    badMetaWordVal   = 0xBAADFADE;               // value used to zap metadata heap after GC
const int      badCodeHeapNewVal= 0xCC;                     // value used to zap Code heap at allocation
const int      badCodeHeapFreeVal = 0xDD;                   // value used to zap Code heap at deallocation


// (These must be implemented as #defines because C++ compilers are
// not obligated to inline non-integral constants!)
#define       badAddress        ((address)::badAddressVal)
#define       badOop            (cast_to_oop(::badOopVal))
#define       badHeapWord       (::badHeapWordVal)
#define       badJNIHandle      (cast_to_oop(::badJNIHandleVal))

// Default TaskQueue size is 16K (32-bit) or 128K (64-bit)
#define TASKQUEUE_SIZE (NOT_LP64(1<<14) LP64_ONLY(1<<17))

//----------------------------------------------------------------------------------------------------
// Utility functions for bitfield manipulations

const intptr_t AllBits    = ~0; // all bits set in a word
const intptr_t NoBits     =  0; // no bits set in a word
const jlong    NoLongBits =  0; // no bits set in a long
const intptr_t OneBit     =  1; // only right_most bit set in a word

// get a word with the n.th or the right-most or left-most n bits set
// (note: #define used only so that they can be used in enum constant definitions)
#define nth_bit(n)        (((n) >= BitsPerWord) ? 0 : (OneBit << (n)))
#define right_n_bits(n)   (nth_bit(n) - 1)
#define left_n_bits(n)    (right_n_bits(n) << (((n) >= BitsPerWord) ? 0 : (BitsPerWord - (n))))

// bit-operations using a mask m
inline void   set_bits    (intptr_t& x, intptr_t m) { x |= m; }
inline void clear_bits    (intptr_t& x, intptr_t m) { x &= ~m; }
inline intptr_t mask_bits      (intptr_t  x, intptr_t m) { return x & m; }
inline jlong    mask_long_bits (jlong     x, jlong    m) { return x & m; }
inline bool mask_bits_are_true (intptr_t flags, intptr_t mask) { return (flags & mask) == mask; }

// bit-operations using the n.th bit
inline void    set_nth_bit(intptr_t& x, int n) { set_bits  (x, nth_bit(n)); }
inline void  clear_nth_bit(intptr_t& x, int n) { clear_bits(x, nth_bit(n)); }
inline bool is_set_nth_bit(intptr_t  x, int n) { return mask_bits (x, nth_bit(n)) != NoBits; }

// returns the bitfield of x starting at start_bit_no with length field_length (no sign-extension!)
inline intptr_t bitfield(intptr_t x, int start_bit_no, int field_length) {
  return mask_bits(x >> start_bit_no, right_n_bits(field_length));
}


//----------------------------------------------------------------------------------------------------
// Utility functions for integers

// Avoid use of global min/max macros which may cause unwanted double
// evaluation of arguments.
#ifdef max
#undef max
#endif

#ifdef min
#undef min
#endif

#define max(a,b) Do_not_use_max_use_MAX2_instead
#define min(a,b) Do_not_use_min_use_MIN2_instead

// It is necessary to use templates here. Having normal overloaded
// functions does not work because it is necessary to provide both 32-
// and 64-bit overloaded functions, which does not work, and having
// explicitly-typed versions of these routines (i.e., MAX2I, MAX2L)
// will be even more error-prone than macros.
template<class T> inline T MAX2(T a, T b)           { return (a > b) ? a : b; }
template<class T> inline T MIN2(T a, T b)           { return (a < b) ? a : b; }
template<class T> inline T MAX3(T a, T b, T c)      { return MAX2(MAX2(a, b), c); }
template<class T> inline T MIN3(T a, T b, T c)      { return MIN2(MIN2(a, b), c); }
template<class T> inline T MAX4(T a, T b, T c, T d) { return MAX2(MAX3(a, b, c), d); }
template<class T> inline T MIN4(T a, T b, T c, T d) { return MIN2(MIN3(a, b, c), d); }

template<class T> inline T ABS(T x)                 { return (x > 0) ? x : -x; }

// true if x is a power of 2, false otherwise
inline bool is_power_of_2(intptr_t x) {
  return ((x != NoBits) && (mask_bits(x, x - 1) == NoBits));
}

// long version of is_power_of_2
inline bool is_power_of_2_long(jlong x) {
  return ((x != NoLongBits) && (mask_long_bits(x, x - 1) == NoLongBits));
}

// Returns largest i such that 2^i <= x.
// If x < 0, the function returns 31 on a 32-bit machine and 63 on a 64-bit machine.
// If x == 0, the function returns -1.
inline int log2_intptr(intptr_t x) {
  int i = -1;
  uintptr_t p = 1;
  while (p != 0 && p <= (uintptr_t)x) {
    // p = 2^(i+1) && p <= x (i.e., 2^(i+1) <= x)
    i++; p *= 2;
  }
  // p = 2^(i+1) && x < p (i.e., 2^i <= x < 2^(i+1))
  // If p = 0, overflow has occurred and i = 31 or i = 63 (depending on the machine word size).
  return i;
}

//* largest i such that 2^i <= x
//  A negative value of 'x' will return '63'
inline int log2_long(jlong x) {
  int i = -1;
  julong p =  1;
  while (p != 0 && p <= (julong)x) {
    // p = 2^(i+1) && p <= x (i.e., 2^(i+1) <= x)
    i++; p *= 2;
  }
  // p = 2^(i+1) && x < p (i.e., 2^i <= x < 2^(i+1))
  // (if p = 0 then overflow occurred and i = 63)
  return i;
}

//* the argument must be exactly a power of 2
inline int exact_log2(intptr_t x) {
  #ifdef ASSERT
    if (!is_power_of_2(x)) basic_fatal("x must be a power of 2");
  #endif
  return log2_intptr(x);
}

//* the argument must be exactly a power of 2
inline int exact_log2_long(jlong x) {
  #ifdef ASSERT
    if (!is_power_of_2_long(x)) basic_fatal("x must be a power of 2");
  #endif
  return log2_long(x);
}


// returns integer round-up to the nearest multiple of s (s must be a power of two)
inline intptr_t round_to(intptr_t x, uintx s) {
  #ifdef ASSERT
    if (!is_power_of_2(s)) basic_fatal("s must be a power of 2");
  #endif
  const uintx m = s - 1;
  return mask_bits(x + m, ~m);
}

// returns integer round-down to the nearest multiple of s (s must be a power of two)
inline intptr_t round_down(intptr_t x, uintx s) {
  #ifdef ASSERT
    if (!is_power_of_2(s)) basic_fatal("s must be a power of 2");
  #endif
  const uintx m = s - 1;
  return mask_bits(x, ~m);
}


inline bool is_odd (intx x) { return x & 1;      }
inline bool is_even(intx x) { return !is_odd(x); }

// "to" should be greater than "from."
inline intx byte_size(void* from, void* to) {
  return (address)to - (address)from;
}

//----------------------------------------------------------------------------------------------------
// Avoid non-portable casts with these routines (DEPRECATED)

// NOTE: USE Bytes class INSTEAD WHERE POSSIBLE
//       Bytes is optimized machine-specifically and may be much faster then the portable routines below.

// Given sequence of four bytes, build into a 32-bit word
// following the conventions used in class files.
// On the 386, this could be realized with a simple address cast.
//

// This routine takes eight bytes:
inline u8 build_u8_from( u1 c1, u1 c2, u1 c3, u1 c4, u1 c5, u1 c6, u1 c7, u1 c8 ) {
  return  (( u8(c1) << 56 )  &  ( u8(0xff) << 56 ))
       |  (( u8(c2) << 48 )  &  ( u8(0xff) << 48 ))
       |  (( u8(c3) << 40 )  &  ( u8(0xff) << 40 ))
       |  (( u8(c4) << 32 )  &  ( u8(0xff) << 32 ))
       |  (( u8(c5) << 24 )  &  ( u8(0xff) << 24 ))
       |  (( u8(c6) << 16 )  &  ( u8(0xff) << 16 ))
       |  (( u8(c7) <<  8 )  &  ( u8(0xff) <<  8 ))
       |  (( u8(c8) <<  0 )  &  ( u8(0xff) <<  0 ));
}

// This routine takes four bytes:
inline u4 build_u4_from( u1 c1, u1 c2, u1 c3, u1 c4 ) {
  return  (( u4(c1) << 24 )  &  0xff000000)
       |  (( u4(c2) << 16 )  &  0x00ff0000)
       |  (( u4(c3) <<  8 )  &  0x0000ff00)
       |  (( u4(c4) <<  0 )  &  0x000000ff);
}

// And this one works if the four bytes are contiguous in memory:
inline u4 build_u4_from( u1* p ) {
  return  build_u4_from( p[0], p[1], p[2], p[3] );
}

// Ditto for two-byte ints:
inline u2 build_u2_from( u1 c1, u1 c2 ) {
  return  u2((( u2(c1) <<  8 )  &  0xff00)
          |  (( u2(c2) <<  0 )  &  0x00ff));
}

// And this one works if the two bytes are contiguous in memory:
inline u2 build_u2_from( u1* p ) {
  return  build_u2_from( p[0], p[1] );
}

// Ditto for floats:
inline jfloat build_float_from( u1 c1, u1 c2, u1 c3, u1 c4 ) {
  u4 u = build_u4_from( c1, c2, c3, c4 );
  return  *(jfloat*)&u;
}

inline jfloat build_float_from( u1* p ) {
  u4 u = build_u4_from( p );
  return  *(jfloat*)&u;
}


// now (64-bit) longs

inline jlong build_long_from( u1 c1, u1 c2, u1 c3, u1 c4, u1 c5, u1 c6, u1 c7, u1 c8 ) {
  return  (( jlong(c1) << 56 )  &  ( jlong(0xff) << 56 ))
       |  (( jlong(c2) << 48 )  &  ( jlong(0xff) << 48 ))
       |  (( jlong(c3) << 40 )  &  ( jlong(0xff) << 40 ))
       |  (( jlong(c4) << 32 )  &  ( jlong(0xff) << 32 ))
       |  (( jlong(c5) << 24 )  &  ( jlong(0xff) << 24 ))
       |  (( jlong(c6) << 16 )  &  ( jlong(0xff) << 16 ))
       |  (( jlong(c7) <<  8 )  &  ( jlong(0xff) <<  8 ))
       |  (( jlong(c8) <<  0 )  &  ( jlong(0xff) <<  0 ));
}

inline jlong build_long_from( u1* p ) {
  return  build_long_from( p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7] );
}


// Doubles, too!
inline jdouble build_double_from( u1 c1, u1 c2, u1 c3, u1 c4, u1 c5, u1 c6, u1 c7, u1 c8 ) {
  jlong u = build_long_from( c1, c2, c3, c4, c5, c6, c7, c8 );
  return  *(jdouble*)&u;
}

inline jdouble build_double_from( u1* p ) {
  jlong u = build_long_from( p );
  return  *(jdouble*)&u;
}


// Portable routines to go the other way:

inline void explode_short_to( u2 x, u1& c1, u1& c2 ) {
  c1 = u1(x >> 8);
  c2 = u1(x);
}

inline void explode_short_to( u2 x, u1* p ) {
  explode_short_to( x, p[0], p[1]);
}

inline void explode_int_to( u4 x, u1& c1, u1& c2, u1& c3, u1& c4 ) {
  c1 = u1(x >> 24);
  c2 = u1(x >> 16);
  c3 = u1(x >>  8);
  c4 = u1(x);
}

inline void explode_int_to( u4 x, u1* p ) {
  explode_int_to( x, p[0], p[1], p[2], p[3]);
}


// Pack and extract shorts to/from ints:

inline int extract_low_short_from_int(jint x) {
  return x & 0xffff;
}

inline int extract_high_short_from_int(jint x) {
  return (x >> 16) & 0xffff;
}

inline int build_int_from_shorts( jushort low, jushort high ) {
  return ((int)((unsigned int)high << 16) | (unsigned int)low);
}

// Convert pointer to intptr_t, for use in printing pointers.
inline intptr_t p2i(const void * p) {
  return (intptr_t) p;
}

// swap a & b
template<class T> static void swap(T& a, T& b) {
  T tmp = a;
  a = b;
  b = tmp;
}

// Printf-style formatters for fixed- and variable-width types as pointers and
// integers.  These are derived from the definitions in inttypes.h.  If the platform
// doesn't provide appropriate definitions, they should be provided in
// the compiler-specific definitions file (e.g., globalDefinitions_gcc.hpp)

#define BOOL_TO_STR(_b_) ((_b_) ? "true" : "false")

// Format 32-bit quantities.
#define INT32_FORMAT           "%" PRId32
#define UINT32_FORMAT          "%" PRIu32
#define INT32_FORMAT_W(width)  "%" #width PRId32
#define UINT32_FORMAT_W(width) "%" #width PRIu32

#define PTR32_FORMAT           "0x%08" PRIx32
#define PTR32_FORMAT_W(width)  "0x%" #width PRIx32

// Format 64-bit quantities.
#define INT64_FORMAT           "%" PRId64
#define UINT64_FORMAT          "%" PRIu64
#define UINT64_FORMAT_X        "%" PRIx64
#define INT64_FORMAT_W(width)  "%" #width PRId64
#define UINT64_FORMAT_W(width) "%" #width PRIu64

#define PTR64_FORMAT           "0x%016" PRIx64

// Format jlong, if necessary
#ifndef JLONG_FORMAT
#define JLONG_FORMAT           INT64_FORMAT
#endif
#ifndef JULONG_FORMAT
#define JULONG_FORMAT          UINT64_FORMAT
#endif
#ifndef JULONG_FORMAT_X
#define JULONG_FORMAT_X        UINT64_FORMAT_X
#endif

// Format pointers which change size between 32- and 64-bit.
#ifdef  _LP64
#define INTPTR_FORMAT "0x%016" PRIxPTR
#define PTR_FORMAT    "0x%016" PRIxPTR
#else   // !_LP64
#define INTPTR_FORMAT "0x%08"  PRIxPTR
#define PTR_FORMAT    "0x%08"  PRIxPTR
#endif  // _LP64

#define INTPTR_FORMAT_W(width)   "%" #width PRIxPTR

#define SSIZE_FORMAT             "%"   PRIdPTR
#define SIZE_FORMAT              "%"   PRIuPTR
#define SIZE_FORMAT_HEX          "0x%" PRIxPTR
#define SSIZE_FORMAT_W(width)    "%"   #width PRIdPTR
#define SIZE_FORMAT_W(width)     "%"   #width PRIuPTR
#define SIZE_FORMAT_HEX_W(width) "0x%" #width PRIxPTR

#define INTX_FORMAT           "%" PRIdPTR
#define UINTX_FORMAT          "%" PRIuPTR
#define INTX_FORMAT_W(width)  "%" #width PRIdPTR
#define UINTX_FORMAT_W(width) "%" #width PRIuPTR


#define ARRAY_SIZE(array) (sizeof(array)/sizeof((array)[0]))

//----------------------------------------------------------------------------------------------------
// Sum and product which can never overflow: they wrap, just like the
// Java operations.  Note that we don't intend these to be used for
// general-purpose arithmetic: their purpose is to emulate Java
// operations.

// The goal of this code to avoid undefined or implementation-defined
// behavior.  The use of an lvalue to reference cast is explicitly
// permitted by Lvalues and rvalues [basic.lval].  [Section 3.10 Para
// 15 in C++03]
#define JAVA_INTEGER_OP(OP, NAME, TYPE, UNSIGNED_TYPE)  \
inline TYPE NAME (TYPE in1, TYPE in2) {                 \
  UNSIGNED_TYPE ures = static_cast<UNSIGNED_TYPE>(in1); \
  ures OP ## = static_cast<UNSIGNED_TYPE>(in2);         \
  return reinterpret_cast<TYPE&>(ures);                 \
}

JAVA_INTEGER_OP(+, java_add, jint, juint)
JAVA_INTEGER_OP(-, java_subtract, jint, juint)
JAVA_INTEGER_OP(*, java_multiply, jint, juint)
JAVA_INTEGER_OP(+, java_add, jlong, julong)
JAVA_INTEGER_OP(-, java_subtract, jlong, julong)
JAVA_INTEGER_OP(*, java_multiply, jlong, julong)

#undef JAVA_INTEGER_OP

// Dereference vptr
// All C++ compilers that we know of have the vtbl pointer in the first
// word.  If there are exceptions, this function needs to be made compiler
// specific.
static inline void* dereference_vptr(const void* addr) {
  return *(void**)addr;
}

#endif // SHARE_VM_UTILITIES_GLOBALDEFINITIONS_HPP
