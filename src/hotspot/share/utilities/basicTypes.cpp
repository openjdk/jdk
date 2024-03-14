/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/basicTypes.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "utilities/powerOfTwo.hpp"

// Something to help porters sleep at night

#ifdef ASSERT
static BasicType char2type(int ch) {
  switch (ch) {
#define EACH_SIG(ch, bt, ignore) \
    case ch: return bt;
    SIGNATURE_TYPES_DO(EACH_SIG, ignore)
#undef EACH_SIG
  }
  return T_ILLEGAL;
}

extern bool signature_constants_sane();
#endif //ASSERT

void basic_types_init() {
#ifdef ASSERT
  #ifdef _LP64
    static_assert(min_intx ==  (intx)CONST64(0x8000000000000000), "correct constant");
    static_assert(max_intx ==  CONST64(0x7FFFFFFFFFFFFFFF), "correct constant");
    static_assert(max_uintx == CONST64(0xFFFFFFFFFFFFFFFF), "correct constant");
    static_assert( 8 == sizeof( intx),      "wrong size for basic type");
    static_assert( 8 == sizeof( jobject),   "wrong size for basic type");
#else
    static_assert(min_intx ==  (intx)0x80000000,  "correct constant");
  static_assert(max_intx ==  0x7FFFFFFF,  "correct constant");
  static_assert(max_uintx == 0xFFFFFFFF,  "correct constant");
  static_assert( 4 == sizeof( intx),      "wrong size for basic type");
  static_assert( 4 == sizeof( jobject),   "wrong size for basic type");
#endif
    static_assert( (~max_juint) == 0,  "max_juint has all its bits");
    static_assert( (~max_uintx) == 0,  "max_uintx has all its bits");
    static_assert( (~max_julong) == 0, "max_julong has all its bits");
    static_assert( 1 == sizeof( jbyte),     "wrong size for basic type");
    static_assert( 2 == sizeof( jchar),     "wrong size for basic type");
    static_assert( 2 == sizeof( jshort),    "wrong size for basic type");
    static_assert( 4 == sizeof( juint),     "wrong size for basic type");
    static_assert( 4 == sizeof( jint),      "wrong size for basic type");
    static_assert( 1 == sizeof( jboolean),  "wrong size for basic type");
    static_assert( 8 == sizeof( jlong),     "wrong size for basic type");
    static_assert( 4 == sizeof( jfloat),    "wrong size for basic type");
    static_assert( 8 == sizeof( jdouble),   "wrong size for basic type");
    static_assert( 1 == sizeof( u1),        "wrong size for basic type");
    static_assert( 2 == sizeof( u2),        "wrong size for basic type");
    static_assert( 4 == sizeof( u4),        "wrong size for basic type");
    static_assert(wordSize == BytesPerWord, "should be the same since they're used interchangeably");
    static_assert(wordSize == HeapWordSize, "should be the same since they're also used interchangeably");

    assert(signature_constants_sane(), "");

    int num_type_chars = 0;
    for (int i = 0; i < 99; i++) {
      if (type2char((BasicType)i) != 0) {
        assert(char2type(type2char((BasicType)i)) == i, "proper inverses");
        assert(Signature::basic_type(type2char((BasicType)i)) == i, "proper inverses");
        num_type_chars++;
      }
    }
    assert(num_type_chars == 11, "must have tested the right number of mappings");
    assert(char2type(0) == T_ILLEGAL, "correct illegality");

    {
      for (int i = T_BOOLEAN; i <= T_CONFLICT; i++) {
        BasicType vt = (BasicType)i;
        BasicType ft = type2field[vt];
        switch (vt) {
          // the following types might plausibly show up in memory layouts:
          case T_BOOLEAN:
          case T_BYTE:
          case T_CHAR:
          case T_SHORT:
          case T_INT:
          case T_FLOAT:
          case T_DOUBLE:
          case T_LONG:
          case T_OBJECT:
          case T_ADDRESS:     // random raw pointer
          case T_METADATA:    // metadata pointer
          case T_NARROWOOP:   // compressed pointer
          case T_NARROWKLASS: // compressed klass pointer
          case T_CONFLICT:    // might as well support a bottom type
          case T_VOID:        // padding or other unaddressed word
            // layout type must map to itself
            assert(vt == ft, "");
            break;
          default:
            // non-layout type must map to a (different) layout type
            assert(vt != ft, "");
            assert(ft == type2field[ft], "");
        }
        // every type must map to same-sized layout type:
        assert(type2size[vt] == type2size[ft], "");
      }
    }
  // These are assumed, e.g., when filling HeapWords with juints.
  static_assert(is_power_of_2(sizeof(juint)), "juint must be power of 2");
  static_assert(is_power_of_2(HeapWordSize), "HeapWordSize must be power of 2");
  static_assert((size_t)HeapWordSize >= sizeof(juint),
                "HeapWord should be at least as large as juint");
#endif

  if( JavaPriority1_To_OSPriority != -1 )
    os::java_to_os_priority[1] = JavaPriority1_To_OSPriority;
  if( JavaPriority2_To_OSPriority != -1 )
    os::java_to_os_priority[2] = JavaPriority2_To_OSPriority;
  if( JavaPriority3_To_OSPriority != -1 )
    os::java_to_os_priority[3] = JavaPriority3_To_OSPriority;
  if( JavaPriority4_To_OSPriority != -1 )
    os::java_to_os_priority[4] = JavaPriority4_To_OSPriority;
  if( JavaPriority5_To_OSPriority != -1 )
    os::java_to_os_priority[5] = JavaPriority5_To_OSPriority;
  if( JavaPriority6_To_OSPriority != -1 )
    os::java_to_os_priority[6] = JavaPriority6_To_OSPriority;
  if( JavaPriority7_To_OSPriority != -1 )
    os::java_to_os_priority[7] = JavaPriority7_To_OSPriority;
  if( JavaPriority8_To_OSPriority != -1 )
    os::java_to_os_priority[8] = JavaPriority8_To_OSPriority;
  if( JavaPriority9_To_OSPriority != -1 )
    os::java_to_os_priority[9] = JavaPriority9_To_OSPriority;
  if(JavaPriority10_To_OSPriority != -1 )
    os::java_to_os_priority[10] = JavaPriority10_To_OSPriority;

  // Set the size of basic types here (after argument parsing but before
  // stub generation).
  if (UseCompressedOops) {
    // Size info for oops within java objects is fixed
    heapOopSize        = jintSize;
    LogBytesPerHeapOop = LogBytesPerInt;
    LogBitsPerHeapOop  = LogBitsPerInt;
    BytesPerHeapOop    = BytesPerInt;
    BitsPerHeapOop     = BitsPerInt;
  } else {
    heapOopSize        = oopSize;
    LogBytesPerHeapOop = LogBytesPerWord;
    LogBitsPerHeapOop  = LogBitsPerWord;
    BytesPerHeapOop    = BytesPerWord;
    BitsPerHeapOop     = BitsPerWord;
  }
  _type2aelembytes[T_OBJECT] = heapOopSize;
  _type2aelembytes[T_ARRAY]  = heapOopSize;
}
