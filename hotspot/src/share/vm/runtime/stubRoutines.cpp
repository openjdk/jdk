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

#include "incls/_precompiled.incl"
#include "incls/_stubRoutines.cpp.incl"


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
address StubRoutines::_throw_ArithmeticException_entry          = NULL;
address StubRoutines::_throw_NullPointerException_entry         = NULL;
address StubRoutines::_throw_NullPointerException_at_call_entry = NULL;
address StubRoutines::_throw_StackOverflowError_entry           = NULL;
address StubRoutines::_handler_for_unsafe_access_entry          = NULL;
jint    StubRoutines::_verify_oop_count                         = 0;
address StubRoutines::_verify_oop_subroutine_entry              = NULL;
address StubRoutines::_atomic_xchg_entry                        = NULL;
address StubRoutines::_atomic_xchg_ptr_entry                    = NULL;
address StubRoutines::_atomic_store_entry                       = NULL;
address StubRoutines::_atomic_store_ptr_entry                   = NULL;
address StubRoutines::_atomic_cmpxchg_entry                     = NULL;
address StubRoutines::_atomic_cmpxchg_ptr_entry                 = NULL;
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
// The dafault functions don't have separate disjoint versions.
address StubRoutines::_jbyte_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);
address StubRoutines::_jbyte_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_disjoint_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_disjoint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_disjoint_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);

address StubRoutines::_arrayof_jbyte_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_arraycopy    = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);
address StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_disjoint_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_disjoint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);

address StubRoutines::_checkcast_arraycopy               = NULL;
address StubRoutines::_unsafe_arraycopy                  = NULL;
address StubRoutines::_generic_arraycopy                 = NULL;

double (* StubRoutines::_intrinsic_log   )(double) = NULL;
double (* StubRoutines::_intrinsic_log10 )(double) = NULL;
double (* StubRoutines::_intrinsic_exp   )(double) = NULL;
double (* StubRoutines::_intrinsic_pow   )(double, double) = NULL;
double (* StubRoutines::_intrinsic_sin   )(double) = NULL;
double (* StubRoutines::_intrinsic_cos   )(double) = NULL;
double (* StubRoutines::_intrinsic_tan   )(double) = NULL;

// Initialization
//
// Note: to break cycle with universe initialization, stubs are generated in two phases.
// The first one generates stubs needed during universe init (e.g., _handle_must_compile_first_entry).
// The second phase includes all other stubs (which may depend on universe being initialized.)

extern void StubGenerator_generate(CodeBuffer* code, bool all); // only interface to generators

void StubRoutines::initialize1() {
  if (_code1 == NULL) {
    ResourceMark rm;
    TraceTime timer("StubRoutines generation 1", TraceStartupTime);
    _code1 = BufferBlob::create("StubRoutines (1)", code_size1);
    if (_code1 == NULL) {
      vm_exit_out_of_memory(code_size1,
                            "CodeCache: no room for StubRoutines (1)");
    }
    CodeBuffer buffer(_code1->instructions_begin(), _code1->instructions_size());
    StubGenerator_generate(&buffer, false);
  }
}


#ifdef ASSERT
typedef void (*arraycopy_fn)(address src, address dst, int count);

// simple tests of generated arraycopy functions
static void test_arraycopy_func(address func, int alignment) {
  int v = 0xcc;
  int v2 = 0x11;
  jlong lbuffer[2];
  jlong lbuffer2[2];
  address buffer  = (address) lbuffer;
  address buffer2 = (address) lbuffer2;
  unsigned int i;
  for (i = 0; i < sizeof(lbuffer); i++) {
    buffer[i] = v; buffer2[i] = v2;
  }
  // do an aligned copy
  ((arraycopy_fn)func)(buffer, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(buffer[i] == v && buffer2[i] == v2, "shouldn't have copied anything");
  }
  // adjust destination alignment
  ((arraycopy_fn)func)(buffer, buffer2 + alignment, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(buffer[i] == v && buffer2[i] == v2, "shouldn't have copied anything");
  }
  // adjust source alignment
  ((arraycopy_fn)func)(buffer + alignment, buffer2, 0);
  for (i = 0; i < sizeof(lbuffer); i++) {
    assert(buffer[i] == v && buffer2[i] == v2, "shouldn't have copied anything");
  }
}
#endif


void StubRoutines::initialize2() {
  if (_code2 == NULL) {
    ResourceMark rm;
    TraceTime timer("StubRoutines generation 2", TraceStartupTime);
    _code2 = BufferBlob::create("StubRoutines (2)", code_size2);
    if (_code2 == NULL) {
      vm_exit_out_of_memory(code_size2,
                            "CodeCache: no room for StubRoutines (2)");
    }
    CodeBuffer buffer(_code2->instructions_begin(), _code2->instructions_size());
    StubGenerator_generate(&buffer, true);
  }

#ifdef ASSERT

#define TEST_ARRAYCOPY(type)                                                    \
  test_arraycopy_func(          type##_arraycopy(),          sizeof(type));     \
  test_arraycopy_func(          type##_disjoint_arraycopy(), sizeof(type));     \
  test_arraycopy_func(arrayof_##type##_arraycopy(),          sizeof(HeapWord)); \
  test_arraycopy_func(arrayof_##type##_disjoint_arraycopy(), sizeof(HeapWord))

  // Make sure all the arraycopy stubs properly handle zeros
  TEST_ARRAYCOPY(jbyte);
  TEST_ARRAYCOPY(jshort);
  TEST_ARRAYCOPY(jint);
  TEST_ARRAYCOPY(jlong);

#undef TEST_ARRAYCOPY

#endif
}


void stubRoutines_init1() { StubRoutines::initialize1(); }
void stubRoutines_init2() { StubRoutines::initialize2(); }

//
// Default versions of arraycopy functions
//

static void gen_arraycopy_barrier_pre(oop* dest, size_t count) {
    assert(count != 0, "count should be non-zero");
    assert(count <= (size_t)max_intx, "count too large");
    BarrierSet* bs = Universe::heap()->barrier_set();
    assert(bs->has_write_ref_array_pre_opt(), "Must have pre-barrier opt");
    bs->write_ref_array_pre(dest, (int)count);
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
  assert(count != 0, "count should be non-zero");
  Copy::conjoint_bytes_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jshort_copy(jshort* src, jshort* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::conjoint_jshorts_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jint_copy(jint* src, jint* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::conjoint_jints_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jlong_copy(jlong* src, jlong* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;      // Slow-path long/double array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::conjoint_jlongs_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::oop_copy(oop* src, oop* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre(dest, count);
  Copy::conjoint_oops_atomic(src, dest, count);
  gen_arraycopy_barrier(dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jbyte_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jbyte_array_copy_ctr++;      // Slow-path byte array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::arrayof_conjoint_bytes(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jshort_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::arrayof_conjoint_jshorts(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jint_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::arrayof_conjoint_jints(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jlong_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  Copy::arrayof_conjoint_jlongs(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_oop_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  gen_arraycopy_barrier_pre((oop *) dest, count);
  Copy::arrayof_conjoint_oops(src, dest, count);
  gen_arraycopy_barrier((oop *) dest, count);
JRT_END
