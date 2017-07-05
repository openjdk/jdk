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

#ifndef SHARE_VM_RUNTIME_STUBROUTINES_HPP
#define SHARE_VM_RUNTIME_STUBROUTINES_HPP

#include "code/codeBlob.hpp"
#include "memory/allocation.hpp"
#include "runtime/frame.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "utilities/top.hpp"
#ifdef TARGET_ARCH_x86
# include "nativeInst_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "nativeInst_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "nativeInst_zero.hpp"
#endif

// StubRoutines provides entry points to assembly routines used by
// compiled code and the run-time system. Platform-specific entry
// points are defined in the platform-specific inner class.
//
// Class scheme:
//
//    platform-independent               platform-dependent
//
//    stubRoutines.hpp  <-- included --  stubRoutines_<arch>.hpp
//           ^                                  ^
//           |                                  |
//       implements                         implements
//           |                                  |
//           |                                  |
//    stubRoutines.cpp                   stubRoutines_<arch>.cpp
//    stubRoutines_<os_family>.cpp       stubGenerator_<arch>.cpp
//    stubRoutines_<os_arch>.cpp
//
// Note 1: The important thing is a clean decoupling between stub
//         entry points (interfacing to the whole vm; i.e., 1-to-n
//         relationship) and stub generators (interfacing only to
//         the entry points implementation; i.e., 1-to-1 relationship).
//         This significantly simplifies changes in the generator
//         structure since the rest of the vm is not affected.
//
// Note 2: stubGenerator_<arch>.cpp contains a minimal portion of
//         machine-independent code; namely the generator calls of
//         the generator functions that are used platform-independently.
//         However, it comes with the advantage of having a 1-file
//         implementation of the generator. It should be fairly easy
//         to change, should it become a problem later.
//
// Scheme for adding a new entry point:
//
// 1. determine if it's a platform-dependent or independent entry point
//    a) if platform independent: make subsequent changes in the independent files
//    b) if platform   dependent: make subsequent changes in the   dependent files
// 2. add a private instance variable holding the entry point address
// 3. add a public accessor function to the instance variable
// 4. implement the corresponding generator function in the platform-dependent
//    stubGenerator_<arch>.cpp file and call the function in generate_all() of that file


class StubRoutines: AllStatic {

 public:
  enum platform_independent_constants {
    max_size_of_parameters = 256                           // max. parameter size supported by megamorphic lookups
  };

  // Dependencies
  friend class StubGenerator;
#ifdef TARGET_ARCH_MODEL_x86_32
# include "stubRoutines_x86_32.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_x86_64
# include "stubRoutines_x86_64.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_sparc
# include "stubRoutines_sparc.hpp"
#endif
#ifdef TARGET_ARCH_MODEL_zero
# include "stubRoutines_zero.hpp"
#endif


  static jint    _verify_oop_count;
  static address _verify_oop_subroutine_entry;

  static address _call_stub_return_address;                // the return PC, when returning to a call stub
  static address _call_stub_entry;
  static address _forward_exception_entry;
  static address _catch_exception_entry;
  static address _throw_AbstractMethodError_entry;
  static address _throw_IncompatibleClassChangeError_entry;
  static address _throw_ArithmeticException_entry;
  static address _throw_NullPointerException_entry;
  static address _throw_NullPointerException_at_call_entry;
  static address _throw_StackOverflowError_entry;
  static address _handler_for_unsafe_access_entry;

  static address _atomic_xchg_entry;
  static address _atomic_xchg_ptr_entry;
  static address _atomic_store_entry;
  static address _atomic_store_ptr_entry;
  static address _atomic_cmpxchg_entry;
  static address _atomic_cmpxchg_ptr_entry;
  static address _atomic_cmpxchg_long_entry;
  static address _atomic_add_entry;
  static address _atomic_add_ptr_entry;
  static address _fence_entry;
  static address _d2i_wrapper;
  static address _d2l_wrapper;

  static jint    _fpu_cntrl_wrd_std;
  static jint    _fpu_cntrl_wrd_24;
  static jint    _fpu_cntrl_wrd_64;
  static jint    _fpu_cntrl_wrd_trunc;
  static jint    _mxcsr_std;
  static jint    _fpu_subnormal_bias1[3];
  static jint    _fpu_subnormal_bias2[3];

  static BufferBlob* _code1;                               // code buffer for initial routines
  static BufferBlob* _code2;                               // code buffer for all other routines

  // Leaf routines which implement arraycopy and their addresses
  // arraycopy operands aligned on element type boundary
  static address _jbyte_arraycopy;
  static address _jshort_arraycopy;
  static address _jint_arraycopy;
  static address _jlong_arraycopy;
  static address _oop_arraycopy;
  static address _jbyte_disjoint_arraycopy;
  static address _jshort_disjoint_arraycopy;
  static address _jint_disjoint_arraycopy;
  static address _jlong_disjoint_arraycopy;
  static address _oop_disjoint_arraycopy;

  // arraycopy operands aligned on zero'th element boundary
  // These are identical to the ones aligned aligned on an
  // element type boundary, except that they assume that both
  // source and destination are HeapWord aligned.
  static address _arrayof_jbyte_arraycopy;
  static address _arrayof_jshort_arraycopy;
  static address _arrayof_jint_arraycopy;
  static address _arrayof_jlong_arraycopy;
  static address _arrayof_oop_arraycopy;
  static address _arrayof_jbyte_disjoint_arraycopy;
  static address _arrayof_jshort_disjoint_arraycopy;
  static address _arrayof_jint_disjoint_arraycopy;
  static address _arrayof_jlong_disjoint_arraycopy;
  static address _arrayof_oop_disjoint_arraycopy;

  // these are recommended but optional:
  static address _checkcast_arraycopy;
  static address _unsafe_arraycopy;
  static address _generic_arraycopy;

  static address _jbyte_fill;
  static address _jshort_fill;
  static address _jint_fill;
  static address _arrayof_jbyte_fill;
  static address _arrayof_jshort_fill;
  static address _arrayof_jint_fill;

  // These are versions of the java.lang.Math methods which perform
  // the same operations as the intrinsic version.  They are used for
  // constant folding in the compiler to ensure equivalence.  If the
  // intrinsic version returns the same result as the strict version
  // then they can be set to the appropriate function from
  // SharedRuntime.
  static double (*_intrinsic_log)(double);
  static double (*_intrinsic_log10)(double);
  static double (*_intrinsic_exp)(double);
  static double (*_intrinsic_pow)(double, double);
  static double (*_intrinsic_sin)(double);
  static double (*_intrinsic_cos)(double);
  static double (*_intrinsic_tan)(double);

 public:
  // Initialization/Testing
  static void    initialize1();                            // must happen before universe::genesis
  static void    initialize2();                            // must happen after  universe::genesis

  static bool contains(address addr) {
    return
      (_code1 != NULL && _code1->blob_contains(addr)) ||
      (_code2 != NULL && _code2->blob_contains(addr)) ;
  }

  // Debugging
  static jint    verify_oop_count()                        { return _verify_oop_count; }
  static jint*   verify_oop_count_addr()                   { return &_verify_oop_count; }
  // a subroutine for debugging the GC
  static address verify_oop_subroutine_entry_address()    { return (address)&_verify_oop_subroutine_entry; }

  static address catch_exception_entry()                   { return _catch_exception_entry; }

  // Calls to Java
  typedef void (*CallStub)(
    address   link,
    intptr_t* result,
    BasicType result_type,
    methodOopDesc* method,
    address   entry_point,
    intptr_t* parameters,
    int       size_of_parameters,
    TRAPS
  );

  static CallStub call_stub()                              { return CAST_TO_FN_PTR(CallStub, _call_stub_entry); }

  // Exceptions
  static address forward_exception_entry()                 { return _forward_exception_entry; }
  // Implicit exceptions
  static address throw_AbstractMethodError_entry()         { return _throw_AbstractMethodError_entry; }
  static address throw_IncompatibleClassChangeError_entry(){ return _throw_IncompatibleClassChangeError_entry; }
  static address throw_ArithmeticException_entry()         { return _throw_ArithmeticException_entry; }
  static address throw_NullPointerException_entry()        { return _throw_NullPointerException_entry; }
  static address throw_NullPointerException_at_call_entry(){ return _throw_NullPointerException_at_call_entry; }
  static address throw_StackOverflowError_entry()          { return _throw_StackOverflowError_entry; }

  // Exceptions during unsafe access - should throw Java exception rather
  // than crash.
  static address handler_for_unsafe_access()               { return _handler_for_unsafe_access_entry; }

  static address atomic_xchg_entry()                       { return _atomic_xchg_entry; }
  static address atomic_xchg_ptr_entry()                   { return _atomic_xchg_ptr_entry; }
  static address atomic_store_entry()                      { return _atomic_store_entry; }
  static address atomic_store_ptr_entry()                  { return _atomic_store_ptr_entry; }
  static address atomic_cmpxchg_entry()                    { return _atomic_cmpxchg_entry; }
  static address atomic_cmpxchg_ptr_entry()                { return _atomic_cmpxchg_ptr_entry; }
  static address atomic_cmpxchg_long_entry()               { return _atomic_cmpxchg_long_entry; }
  static address atomic_add_entry()                        { return _atomic_add_entry; }
  static address atomic_add_ptr_entry()                    { return _atomic_add_ptr_entry; }
  static address fence_entry()                             { return _fence_entry; }

  static address d2i_wrapper()                             { return _d2i_wrapper; }
  static address d2l_wrapper()                             { return _d2l_wrapper; }
  static jint    fpu_cntrl_wrd_std()                       { return _fpu_cntrl_wrd_std;   }
  static address addr_fpu_cntrl_wrd_std()                  { return (address)&_fpu_cntrl_wrd_std;   }
  static address addr_fpu_cntrl_wrd_24()                   { return (address)&_fpu_cntrl_wrd_24;   }
  static address addr_fpu_cntrl_wrd_64()                   { return (address)&_fpu_cntrl_wrd_64;   }
  static address addr_fpu_cntrl_wrd_trunc()                { return (address)&_fpu_cntrl_wrd_trunc; }
  static address addr_mxcsr_std()                          { return (address)&_mxcsr_std; }
  static address addr_fpu_subnormal_bias1()                { return (address)&_fpu_subnormal_bias1; }
  static address addr_fpu_subnormal_bias2()                { return (address)&_fpu_subnormal_bias2; }


  static address jbyte_arraycopy()  { return _jbyte_arraycopy; }
  static address jshort_arraycopy() { return _jshort_arraycopy; }
  static address jint_arraycopy()   { return _jint_arraycopy; }
  static address jlong_arraycopy()  { return _jlong_arraycopy; }
  static address oop_arraycopy()    { return _oop_arraycopy; }
  static address jbyte_disjoint_arraycopy()  { return _jbyte_disjoint_arraycopy; }
  static address jshort_disjoint_arraycopy() { return _jshort_disjoint_arraycopy; }
  static address jint_disjoint_arraycopy()   { return _jint_disjoint_arraycopy; }
  static address jlong_disjoint_arraycopy()  { return _jlong_disjoint_arraycopy; }
  static address oop_disjoint_arraycopy()    { return _oop_disjoint_arraycopy; }

  static address arrayof_jbyte_arraycopy()  { return _arrayof_jbyte_arraycopy; }
  static address arrayof_jshort_arraycopy() { return _arrayof_jshort_arraycopy; }
  static address arrayof_jint_arraycopy()   { return _arrayof_jint_arraycopy; }
  static address arrayof_jlong_arraycopy()  { return _arrayof_jlong_arraycopy; }
  static address arrayof_oop_arraycopy()    { return _arrayof_oop_arraycopy; }

  static address arrayof_jbyte_disjoint_arraycopy()  { return _arrayof_jbyte_disjoint_arraycopy; }
  static address arrayof_jshort_disjoint_arraycopy() { return _arrayof_jshort_disjoint_arraycopy; }
  static address arrayof_jint_disjoint_arraycopy()   { return _arrayof_jint_disjoint_arraycopy; }
  static address arrayof_jlong_disjoint_arraycopy()  { return _arrayof_jlong_disjoint_arraycopy; }
  static address arrayof_oop_disjoint_arraycopy()    { return _arrayof_oop_disjoint_arraycopy; }

  static address checkcast_arraycopy()     { return _checkcast_arraycopy; }
  static address unsafe_arraycopy()        { return _unsafe_arraycopy; }
  static address generic_arraycopy()       { return _generic_arraycopy; }

  static address jbyte_fill()          { return _jbyte_fill; }
  static address jshort_fill()         { return _jshort_fill; }
  static address jint_fill()           { return _jint_fill; }
  static address arrayof_jbyte_fill()  { return _arrayof_jbyte_fill; }
  static address arrayof_jshort_fill() { return _arrayof_jshort_fill; }
  static address arrayof_jint_fill()   { return _arrayof_jint_fill; }

  static address select_fill_function(BasicType t, bool aligned, const char* &name);


  static double  intrinsic_log(double d) {
    assert(_intrinsic_log != NULL, "must be defined");
    return _intrinsic_log(d);
  }
  static double  intrinsic_log10(double d) {
    assert(_intrinsic_log != NULL, "must be defined");
    return _intrinsic_log10(d);
  }
  static double  intrinsic_exp(double d) {
    assert(_intrinsic_exp != NULL, "must be defined");
    return _intrinsic_exp(d);
  }
  static double  intrinsic_pow(double d, double d2) {
    assert(_intrinsic_pow != NULL, "must be defined");
    return _intrinsic_pow(d, d2);
  }
  static double  intrinsic_sin(double d) {
    assert(_intrinsic_sin != NULL, "must be defined");
    return _intrinsic_sin(d);
  }
  static double  intrinsic_cos(double d) {
    assert(_intrinsic_cos != NULL, "must be defined");
    return _intrinsic_cos(d);
  }
  static double  intrinsic_tan(double d) {
    assert(_intrinsic_tan != NULL, "must be defined");
    return _intrinsic_tan(d);
  }

  //
  // Default versions of the above arraycopy functions for platforms which do
  // not have specialized versions
  //
  static void jbyte_copy (jbyte*  src, jbyte*  dest, size_t count);
  static void jshort_copy(jshort* src, jshort* dest, size_t count);
  static void jint_copy  (jint*   src, jint*   dest, size_t count);
  static void jlong_copy (jlong*  src, jlong*  dest, size_t count);
  static void oop_copy   (oop*    src, oop*    dest, size_t count);

  static void arrayof_jbyte_copy (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jshort_copy(HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jint_copy  (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jlong_copy (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_oop_copy   (HeapWord* src, HeapWord* dest, size_t count);
};

#endif // SHARE_VM_RUNTIME_STUBROUTINES_HPP
