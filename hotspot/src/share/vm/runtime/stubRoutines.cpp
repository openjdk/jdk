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

#include "precompiled.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeCacheExtensions.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/copy.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif


// Implementation of StubRoutines - for a description
// of how to extend it, see the header file.

// Class Variables

BufferBlob* StubRoutines::_code1                                = NULL;
BufferBlob* StubRoutines::_code2                                = NULL;

address StubRoutines::_call_stub_return_address                 = NULL;
address StubRoutines::_call_stub_entry                          = NULL;

address StubRoutines::_catch_exception_entry                    = NULL;
address StubRoutines::_forward_exception_entry                  = NULL;
address StubRoutines::_throw_AbstractMethodError_entry          = NULL;
address StubRoutines::_throw_IncompatibleClassChangeError_entry = NULL;
address StubRoutines::_throw_NullPointerException_at_call_entry = NULL;
address StubRoutines::_throw_StackOverflowError_entry           = NULL;
address StubRoutines::_throw_delayed_StackOverflowError_entry   = NULL;
address StubRoutines::_handler_for_unsafe_access_entry          = NULL;
jint    StubRoutines::_verify_oop_count                         = 0;
address StubRoutines::_verify_oop_subroutine_entry              = NULL;
address StubRoutines::_atomic_xchg_entry                        = NULL;
address StubRoutines::_atomic_xchg_ptr_entry                    = NULL;
address StubRoutines::_atomic_store_entry                       = NULL;
address StubRoutines::_atomic_store_ptr_entry                   = NULL;
address StubRoutines::_atomic_cmpxchg_entry                     = NULL;
address StubRoutines::_atomic_cmpxchg_ptr_entry                 = NULL;
address StubRoutines::_atomic_cmpxchg_byte_entry                = NULL;
address StubRoutines::_atomic_cmpxchg_long_entry                = NULL;
address StubRoutines::_atomic_add_entry                         = NULL;
address StubRoutines::_atomic_add_ptr_entry                     = NULL;
address StubRoutines::_fence_entry                              = NULL;
address StubRoutines::_d2i_wrapper                              = NULL;
address StubRoutines::_d2l_wrapper                              = NULL;

jint    StubRoutines::_fpu_cntrl_wrd_std                        = 0;
jint    StubRoutines::_fpu_cntrl_wrd_24                         = 0;
jint    StubRoutines::_fpu_cntrl_wrd_64                         = 0;
jint    StubRoutines::_fpu_cntrl_wrd_trunc                      = 0;
jint    StubRoutines::_mxcsr_std                                = 0;
jint    StubRoutines::_fpu_subnormal_bias1[3]                   = { 0, 0, 0 };
jint    StubRoutines::_fpu_subnormal_bias2[3]                   = { 0, 0, 0 };

// Compiled code entry points default values
// The default functions don't have separate disjoint versions.
address StubRoutines::_jbyte_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);
address StubRoutines::_oop_arraycopy_uninit     = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy_uninit);
address StubRoutines::_jbyte_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_disjoint_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_disjoint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_disjoint_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);
address StubRoutines::_oop_disjoint_arraycopy_uninit     = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy_uninit);

address StubRoutines::_arrayof_jbyte_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_arraycopy    = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);
address StubRoutines::_arrayof_oop_arraycopy_uninit      = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy_uninit);
address StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_disjoint_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_disjoint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_disjoint_arraycopy    = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);
address StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy_uninit);

address StubRoutines::_zero_aligned_words = CAST_FROM_FN_PTR(address, Copy::zero_to_words);

address StubRoutines::_checkcast_arraycopy               = NULL;
address StubRoutines::_checkcast_arraycopy_uninit        = NULL;
address StubRoutines::_unsafe_arraycopy                  = NULL;
address StubRoutines::_generic_arraycopy                 = NULL;


address StubRoutines::_jbyte_fill;
address StubRoutines::_jshort_fill;
address StubRoutines::_jint_fill;
address StubRoutines::_arrayof_jbyte_fill;
address StubRoutines::_arrayof_jshort_fill;
address StubRoutines::_arrayof_jint_fill;

address StubRoutines::_aescrypt_encryptBlock               = NULL;
address StubRoutines::_aescrypt_decryptBlock               = NULL;
address StubRoutines::_cipherBlockChaining_encryptAESCrypt = NULL;
address StubRoutines::_cipherBlockChaining_decryptAESCrypt = NULL;
address StubRoutines::_counterMode_AESCrypt                = NULL;
address StubRoutines::_ghash_processBlocks                 = NULL;

address StubRoutines::_sha1_implCompress     = NULL;
address StubRoutines::_sha1_implCompressMB   = NULL;
address StubRoutines::_sha256_implCompress   = NULL;
address StubRoutines::_sha256_implCompressMB = NULL;
address StubRoutines::_sha512_implCompress   = NULL;
address StubRoutines::_sha512_implCompressMB = NULL;

address StubRoutines::_updateBytesCRC32 = NULL;
address StubRoutines::_crc_table_adr =    NULL;

address StubRoutines::_crc32c_table_addr = NULL;
address StubRoutines::_updateBytesCRC32C = NULL;
address StubRoutines::_updateBytesAdler32 = NULL;

address StubRoutines::_multiplyToLen = NULL;
address StubRoutines::_squareToLen = NULL;
address StubRoutines::_mulAdd = NULL;
address StubRoutines::_montgomeryMultiply = NULL;
address StubRoutines::_montgomerySquare = NULL;

address StubRoutines::_vectorizedMismatch = NULL;

address StubRoutines::_dexp = NULL;
address StubRoutines::_dlog = NULL;
address StubRoutines::_dlog10 = NULL;
address StubRoutines::_dpow = NULL;
address StubRoutines::_dsin = NULL;
address StubRoutines::_dcos = NULL;
address StubRoutines::_dlibm_sin_cos_huge = NULL;
address StubRoutines::_dlibm_reduce_pi04l = NULL;
address StubRoutines::_dlibm_tan_cot_huge = NULL;
address StubRoutines::_dtan = NULL;

double (* StubRoutines::_intrinsic_log10 )(double) = NULL;
double (* StubRoutines::_intrinsic_sin   )(double) = NULL;
double (* StubRoutines::_intrinsic_cos   )(double) = NULL;
double (* StubRoutines::_intrinsic_tan   )(double) = NULL;

address StubRoutines::_safefetch32_entry                 = NULL;
address StubRoutines::_safefetch32_fault_pc              = NULL;
address StubRoutines::_safefetch32_continuation_pc       = NULL;
address StubRoutines::_safefetchN_entry                  = NULL;
address StubRoutines::_safefetchN_fault_pc               = NULL;
address StubRoutines::_safefetchN_continuation_pc        = NULL;

// Initialization
//
// Note: to break cycle with universe initialization, stubs are generated in two phases.
// The first one generates stubs needed during universe init (e.g., _handle_must_compile_first_entry).
// The second phase includes all other stubs (which may depend on universe being initialized.)

extern void StubGenerator_generate(CodeBuffer* code, bool all); // only interface to generators

void StubRoutines::initialize1() {
  if (_code1 == NULL) {
    ResourceMark rm;
    TraceTime timer("StubRoutines generation 1", TRACETIME_LOG(Info, startuptime));
    _code1 = BufferBlob::create("StubRoutines (1)", code_size1);
    if (_code1 == NULL) {
      vm_exit_out_of_memory(code_size1, OOM_MALLOC_ERROR, "CodeCache: no room for StubRoutines (1)");
    }
    CodeBuffer buffer(_code1);
    StubGenerator_generate(&buffer, false);
    // When new stubs added we need to make sure there is some space left
    // to catch situation when we should increase size again.
    assert(code_size1 == 0 || buffer.insts_remaining() > 200, "increase code_size1");
  }
}


#ifdef ASSERT
typedef void (*arraycopy_fn)(address src, address dst, int count);

// simple tests of generated arraycopy functions
static void test_arraycopy_func(address func, int alignment) {
  if (CodeCacheExtensions::use_pregenerated_interpreter() || !CodeCacheExtensions::is_executable(func)) {
    // Exit safely if stubs were generated but cannot be used.
    // Also excluding pregenerated interpreter since the code may depend on
    // some registers being properly initialized (for instance Rthread)
    return;
  }
  int v = 0xcc;
  int v2 = 0x11;
  jlong lbuffer[8];
  jlong lbuffer2[8];
  address fbuffer  = (address) lbuffer;
  address fbuffer2 = (address) lbuffer2;
  unsigned int i;
  for (i = 0; i < sizeof(lbuffer); i++) {
    fbuffer[i] = v; fbuffer2[i] = v2;
  }
  // C++ does not guarantee jlong[] array alignment to 8 bytes.
  // Use middle of array to check that memory before it is not modified.
  address buffer  = (address) round_to((intptr_t)&lbuffer[4], BytesPerLong);
  address buffer2 = (address) round_to((intptr_t)&lbuffer2[4], BytesPerLong);
  // do an aligned copy
  ((arraycopy_fn)func)(buffer, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(fbuffer[i] == v && fbuffer2[i] == v2, "shouldn't have copied anything");
  }
  // adjust destination alignment
  ((arraycopy_fn)func)(buffer, buffer2 + alignment, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(fbuffer[i] == v && fbuffer2[i] == v2, "shouldn't have copied anything");
  }
  // adjust source alignment
  ((arraycopy_fn)func)(buffer + alignment, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(fbuffer[i] == v && fbuffer2[i] == v2, "shouldn't have copied anything");
  }
}

// simple test for SafeFetch32
static void test_safefetch32() {
  if (CanUseSafeFetch32()) {
    int dummy = 17;
    int* const p_invalid = (int*) get_segfault_address();
    int* const p_valid = &dummy;
    int result_invalid = SafeFetch32(p_invalid, 0xABC);
    assert(result_invalid == 0xABC, "SafeFetch32 error");
    int result_valid = SafeFetch32(p_valid, 0xABC);
    assert(result_valid == 17, "SafeFetch32 error");
  }
}

// simple test for SafeFetchN
static void test_safefetchN() {
  if (CanUseSafeFetchN()) {
#ifdef _LP64
    const intptr_t v1 = UCONST64(0xABCD00000000ABCD);
    const intptr_t v2 = UCONST64(0xDEFD00000000DEFD);
#else
    const intptr_t v1 = 0xABCDABCD;
    const intptr_t v2 = 0xDEFDDEFD;
#endif
    intptr_t dummy = v1;
    intptr_t* const p_invalid = (intptr_t*) get_segfault_address();
    intptr_t* const p_valid = &dummy;
    intptr_t result_invalid = SafeFetchN(p_invalid, v2);
    assert(result_invalid == v2, "SafeFetchN error");
    intptr_t result_valid = SafeFetchN(p_valid, v2);
    assert(result_valid == v1, "SafeFetchN error");
  }
}
#endif

void StubRoutines::initialize2() {
  if (_code2 == NULL) {
    ResourceMark rm;
    TraceTime timer("StubRoutines generation 2", TRACETIME_LOG(Info, startuptime));
    _code2 = BufferBlob::create("StubRoutines (2)", code_size2);
    if (_code2 == NULL) {
      vm_exit_out_of_memory(code_size2, OOM_MALLOC_ERROR, "CodeCache: no room for StubRoutines (2)");
    }
    CodeBuffer buffer(_code2);
    StubGenerator_generate(&buffer, true);
    // When new stubs added we need to make sure there is some space left
    // to catch situation when we should increase size again.
    assert(code_size2 == 0 || buffer.insts_remaining() > 200, "increase code_size2");
  }

#ifdef ASSERT

#define TEST_ARRAYCOPY(type)                                                    \
  test_arraycopy_func(          type##_arraycopy(),          sizeof(type));     \
  test_arraycopy_func(          type##_disjoint_arraycopy(), sizeof(type));     \
  test_arraycopy_func(arrayof_##type##_arraycopy(),          sizeof(HeapWord)); \
  test_arraycopy_func(arrayof_##type##_disjoint_arraycopy(), sizeof(HeapWord))

  // Make sure all the arraycopy stubs properly handle zero count
  TEST_ARRAYCOPY(jbyte);
  TEST_ARRAYCOPY(jshort);
  TEST_ARRAYCOPY(jint);
  TEST_ARRAYCOPY(jlong);

#undef TEST_ARRAYCOPY

#define TEST_FILL(type)                                                                      \
  if (_##type##_fill != NULL) {                                                              \
    union {                                                                                  \
      double d;                                                                              \
      type body[96];                                                                         \
    } s;                                                                                     \
                                                                                             \
    int v = 32;                                                                              \
    for (int offset = -2; offset <= 2; offset++) {                                           \
      for (int i = 0; i < 96; i++) {                                                         \
        s.body[i] = 1;                                                                       \
      }                                                                                      \
      type* start = s.body + 8 + offset;                                                     \
      for (int aligned = 0; aligned < 2; aligned++) {                                        \
        if (aligned) {                                                                       \
          if (((intptr_t)start) % HeapWordSize == 0) {                                       \
            ((void (*)(type*, int, int))StubRoutines::_arrayof_##type##_fill)(start, v, 80); \
          } else {                                                                           \
            continue;                                                                        \
          }                                                                                  \
        } else {                                                                             \
          ((void (*)(type*, int, int))StubRoutines::_##type##_fill)(start, v, 80);           \
        }                                                                                    \
        for (int i = 0; i < 96; i++) {                                                       \
          if (i < (8 + offset) || i >= (88 + offset)) {                                      \
            assert(s.body[i] == 1, "what?");                                                 \
          } else {                                                                           \
            assert(s.body[i] == 32, "what?");                                                \
          }                                                                                  \
        }                                                                                    \
      }                                                                                      \
    }                                                                                        \
  }                                                                                          \

  TEST_FILL(jbyte);
  TEST_FILL(jshort);
  TEST_FILL(jint);

#undef TEST_FILL

#define TEST_COPYRTN(type) \
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::conjoint_##type##s_atomic),  sizeof(type)); \
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::arrayof_conjoint_##type##s), (int)MAX2(sizeof(HeapWord), sizeof(type)))

  // Make sure all the copy runtime routines properly handle zero count
  TEST_COPYRTN(jbyte);
  TEST_COPYRTN(jshort);
  TEST_COPYRTN(jint);
  TEST_COPYRTN(jlong);

#undef TEST_COPYRTN

  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::conjoint_words), sizeof(HeapWord));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::disjoint_words), sizeof(HeapWord));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::disjoint_words_atomic), sizeof(HeapWord));
  // Aligned to BytesPerLong
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::aligned_conjoint_words), sizeof(jlong));
  test_arraycopy_func(CAST_FROM_FN_PTR(address, Copy::aligned_disjoint_words), sizeof(jlong));

  // test safefetch routines
  // Not on Windows 32bit until 8074860 is fixed
#if ! (defined(_WIN32) && defined(_M_IX86))
  test_safefetch32();
  test_safefetchN();
#endif

#endif
}


void stubRoutines_init1() { StubRoutines::initialize1(); }
void stubRoutines_init2() { StubRoutines::initialize2(); }

//
// Default versions of arraycopy functions
//

static void gen_arraycopy_barrier_pre(oop* dest, size_t count, bool dest_uninitialized) {
    assert(count != 0, "count should be non-zero");
    assert(count <= (size_t)max_intx, "count too large");
    BarrierSet* bs = Universe::heap()->barrier_set();
    assert(bs->has_write_ref_array_pre_opt(), "Must have pre-barrier opt");
    bs->write_ref_array_pre(dest, (int)count, dest_uninitialized);
}

static void gen_arraycopy_barrier(oop* dest, size_t count) {
    assert(count != 0, "count should be non-zero");
    BarrierSet* bs = Universe::heap()->barrier_set();
    assert(bs->has_write_ref_array_opt(), "Barrier set must have ref array opt");
    bs->write_ref_array((HeapWord*)dest, count);
}

JRT_LEAF(void, StubRoutines::jbyte_copy(jbyte* src, jbyte* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jbyte_array_copy_ctr++;      // Slow-path byte array copy
#endif // !PRODUCT
  Copy::conjoint_jbytes_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jshort_copy(jshort* src, jshort* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  Copy::conjoint_jshorts_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jint_copy(jint* src, jint* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::conjoint_jints_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jlong_copy(jlong* src, jlong* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;      // Slow-path long/double array copy
#endif // !PRODUCT
  Copy::conjoint_jlongs_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::oop_copy(oop* src, oop* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre(dest, count, /*dest_uninitialized*/false);
  Copy::conjoint_oops_atomic(src, dest, count);
  gen_arraycopy_barrier(dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::oop_copy_uninit(oop* src, oop* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre(dest, count, /*dest_uninitialized*/true);
  Copy::conjoint_oops_atomic(src, dest, count);
  gen_arraycopy_barrier(dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jbyte_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jbyte_array_copy_ctr++;      // Slow-path byte array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jbytes(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jshort_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jshorts(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jint_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jints(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jlong_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jlongs(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_oop_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre((oop *) dest, count, /*dest_uninitialized*/false);
  Copy::arrayof_conjoint_oops(src, dest, count);
  gen_arraycopy_barrier((oop *) dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_oop_copy_uninit(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre((oop *) dest, count, /*dest_uninitialized*/true);
  Copy::arrayof_conjoint_oops(src, dest, count);
  gen_arraycopy_barrier((oop *) dest, count);
JRT_END

address StubRoutines::select_fill_function(BasicType t, bool aligned, const char* &name) {
#define RETURN_STUB(xxx_fill) { \
  name = #xxx_fill; \
  return StubRoutines::xxx_fill(); }

  switch (t) {
  case T_BYTE:
  case T_BOOLEAN:
    if (!aligned) RETURN_STUB(jbyte_fill);
    RETURN_STUB(arrayof_jbyte_fill);
  case T_CHAR:
  case T_SHORT:
    if (!aligned) RETURN_STUB(jshort_fill);
    RETURN_STUB(arrayof_jshort_fill);
  case T_INT:
  case T_FLOAT:
    if (!aligned) RETURN_STUB(jint_fill);
    RETURN_STUB(arrayof_jint_fill);
  case T_DOUBLE:
  case T_LONG:
  case T_ARRAY:
  case T_OBJECT:
  case T_NARROWOOP:
  case T_NARROWKLASS:
  case T_ADDRESS:
    // Currently unsupported
    return NULL;

  default:
    ShouldNotReachHere();
    return NULL;
  }

#undef RETURN_STUB
}

// constants for computing the copy function
enum {
  COPYFUNC_UNALIGNED = 0,
  COPYFUNC_ALIGNED = 1,                 // src, dest aligned to HeapWordSize
  COPYFUNC_CONJOINT = 0,
  COPYFUNC_DISJOINT = 2                 // src != dest, or transfer can descend
};

// Note:  The condition "disjoint" applies also for overlapping copies
// where an descending copy is permitted (i.e., dest_offset <= src_offset).
address
StubRoutines::select_arraycopy_function(BasicType t, bool aligned, bool disjoint, const char* &name, bool dest_uninitialized) {
  int selector =
    (aligned  ? COPYFUNC_ALIGNED  : COPYFUNC_UNALIGNED) +
    (disjoint ? COPYFUNC_DISJOINT : COPYFUNC_CONJOINT);

#define RETURN_STUB(xxx_arraycopy) { \
  name = #xxx_arraycopy; \
  return StubRoutines::xxx_arraycopy(); }

#define RETURN_STUB_PARM(xxx_arraycopy, parm) {           \
  name = #xxx_arraycopy; \
  return StubRoutines::xxx_arraycopy(parm); }

  switch (t) {
  case T_BYTE:
  case T_BOOLEAN:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jbyte_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jbyte_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jbyte_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jbyte_disjoint_arraycopy);
    }
  case T_CHAR:
  case T_SHORT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jshort_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jshort_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jshort_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jshort_disjoint_arraycopy);
    }
  case T_INT:
  case T_FLOAT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jint_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jint_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jint_disjoint_arraycopy);
    }
  case T_DOUBLE:
  case T_LONG:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jlong_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jlong_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jlong_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jlong_disjoint_arraycopy);
    }
  case T_ARRAY:
  case T_OBJECT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB_PARM(oop_arraycopy, dest_uninitialized);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB_PARM(arrayof_oop_arraycopy, dest_uninitialized);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB_PARM(oop_disjoint_arraycopy, dest_uninitialized);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB_PARM(arrayof_oop_disjoint_arraycopy, dest_uninitialized);
    }
  default:
    ShouldNotReachHere();
    return NULL;
  }

#undef RETURN_STUB
#undef RETURN_STUB_PARM
}
