/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_VMINTRINSICS_HPP
#define SHARE_CLASSFILE_VMINTRINSICS_HPP

#include "jfr/support/jfrIntrinsics.hpp"
#include "memory/allStatic.hpp"
#include "utilities/enumIterator.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/tribool.hpp"
#include "utilities/vmEnums.hpp"

class Method;
class methodHandle;

// Here are all the intrinsics known to the runtime and the CI.
// Each intrinsic consists of a public enum name (like _hashCode),
// followed by a specification of its klass, name, and signature:
//    template(<id>,  <klass>,  <name>, <sig>, <FCODE>)
//
// If you add an intrinsic here, you must also define its name
// and signature as members of the VM symbols.  The VM symbols for
// the intrinsic name and signature may be defined above.
//
// Because the VM_SYMBOLS_DO macro makes reference to VM_INTRINSICS_DO,
// you can also define an intrinsic's name and/or signature locally to the
// intrinsic, if this makes sense.  (It often does make sense.)
//
// For example:
//    do_intrinsic(_foo,  java_lang_Object,  foo_name, foo_signature, F_xx)
//     do_name(     foo_name, "foo")
//     do_signature(foo_signature, "()F")
// klass      = vmSymbols::java_lang_Object()
// name       = vmSymbols::foo_name()
// signature  = vmSymbols::foo_signature()
//
// The name and/or signature might be a "well known" symbol
// like "equal" or "()I", in which case there will be no local
// re-definition of the symbol.
//
// The do_class, do_name, and do_signature calls are all used for the
// same purpose:  Define yet another VM symbol.  They could all be merged
// into a common 'do_symbol' call, but it seems useful to record our
// intentions here about kinds of symbols (class vs. name vs. signature).
//
// The F_xx is one of the Flags enum; see below.
//
// for Emacs: (let ((c-backslash-column 120) (c-backslash-max-column 120)) (c-backslash-region (point) (point-max) nil t))
//
//
// There are two types of intrinsic methods: (1) Library intrinsics and (2) bytecode intrinsics.
//
// (1) A library intrinsic method may be replaced with hand-crafted assembly code,
// with hand-crafted compiler IR, or with a combination of the two. The semantics
// of the replacement code may differ from the semantics of the replaced code.
//
// (2) Bytecode intrinsic methods are not replaced by special code, but they are
// treated in some other special way by the compiler. For example, the compiler
// may delay inlining for some String-related intrinsic methods (e.g., some methods
// defined in the StringBuilder and StringBuffer classes, see
// Compile::should_delay_string_inlining() for more details).
//
// Due to the difference between the semantics of an intrinsic method as defined
// in the (Java) source code and the semantics of the method as defined
// by the code in the VM, intrinsic methods must be explicitly marked.
//
// Intrinsic methods are marked by the jdk.internal.vm.annotation.IntrinsicCandidate
// annotation. If CheckIntrinsics is enabled, the VM performs the following
// checks when a class C is loaded: (1) all intrinsics defined by the VM for
// class C are present in the loaded class file and are marked;
// (2) an intrinsic is defined by the VM for all marked methods of class C;
// (3) check for orphan methods in class C (i.e., methods for which the VM
// declares an intrinsic but that are not declared for the loaded class C.
// Check (3) is available only in debug builds.
//
// If a mismatch is detected for a method, the VM behaves differently depending
// on the type of build. A fastdebug build exits and reports an error on a mismatch.
// A product build will not replace an unmarked library intrinsic method with
// hand-crafted code, that is, unmarked library intrinsics are treated as ordinary
// methods in a product build. The special treatment of a bytecode intrinsic method
// persists even if the method not marked.
//
// When adding an intrinsic for a method, please make sure to appropriately
// annotate the method in the source code. The list below contains all
// library intrinsics followed by bytecode intrinsics. Please also make sure to
// add the declaration of the intrinsic to the appropriate section of the list.
#define VM_INTRINSICS_DO(do_intrinsic, do_class, do_name, do_signature, do_alias)                                       \
  /* (1) Library intrinsics                                                                        */                   \
  do_intrinsic(_hashCode,                 java_lang_Object,       hashCode_name, void_int_signature,             F_RN)  \
   do_name(     hashCode_name,                                   "hashCode")                                            \
  do_intrinsic(_getClass,                 java_lang_Object,       getClass_name, void_class_signature,           F_RN)  \
   do_name(     getClass_name,                                   "getClass")                                            \
  do_intrinsic(_clone,                    java_lang_Object,       clone_name, void_object_signature,             F_RN)  \
   do_name(     clone_name,                                      "clone")                                               \
  do_intrinsic(_notify,                   java_lang_Object,       notify_name, void_method_signature,            F_RN)  \
   do_name(     notify_name,                                     "notify")                                              \
  do_intrinsic(_notifyAll,                java_lang_Object,       notifyAll_name, void_method_signature,         F_RN)  \
   do_name(     notifyAll_name,                                  "notifyAll")                                           \
                                                                                                                        \
  /* Math & StrictMath intrinsics are defined in terms of just a few signatures: */                                     \
  do_class(java_lang_Math,                "java/lang/Math")                                                             \
  do_class(java_lang_StrictMath,          "java/lang/StrictMath")                                                       \
  do_signature(double2_double_signature,  "(DD)D")                                                                      \
  do_signature(double3_double_signature,  "(DDD)D")                                                                     \
  do_signature(float2_float_signature,    "(FF)F")                                                                      \
  do_signature(float3_float_signature,    "(FFF)F")                                                                     \
  do_signature(int2_int_signature,        "(II)I")                                                                      \
  do_signature(long2_int_signature,       "(JJ)I")                                                                      \
  do_signature(long2_long_signature,      "(JJ)J")                                                                      \
                                                                                                                        \
  /* here are the math names, all together: */                                                                          \
  do_name(abs_name,"abs")       do_name(sin_name,"sin")         do_name(cos_name,"cos")                                 \
  do_name(tan_name,"tan")       do_name(atan2_name,"atan2")     do_name(sqrt_name,"sqrt")                               \
  do_name(log_name,"log")       do_name(log10_name,"log10")     do_name(pow_name,"pow")                                 \
  do_name(exp_name,"exp")       do_name(min_name,"min")         do_name(max_name,"max")                                 \
  do_name(floor_name, "floor")  do_name(ceil_name, "ceil")      do_name(rint_name, "rint")                              \
  do_name(round_name, "round")  do_name(sinh_name,"sinh")       do_name(tanh_name,"tanh")                               \
  do_name(cbrt_name,"cbrt")                                                                                             \
                                                                                                                        \
  do_name(addExact_name,"addExact")                                                                                     \
  do_name(decrementExact_name,"decrementExact")                                                                         \
  do_name(incrementExact_name,"incrementExact")                                                                         \
  do_name(multiplyExact_name,"multiplyExact")                                                                           \
  do_name(multiplyHigh_name,"multiplyHigh")                                                                             \
  do_name(unsignedMultiplyHigh_name,"unsignedMultiplyHigh")                                                             \
  do_name(negateExact_name,"negateExact")                                                                               \
  do_name(subtractExact_name,"subtractExact")                                                                           \
  do_name(fma_name, "fma")                                                                                              \
  do_name(copySign_name, "copySign")                                                                                    \
  do_name(signum_name,"signum")                                                                                         \
  do_name(expand_name,"expand")                                                                                         \
                                                                                                                        \
  do_intrinsic(_dabs,                     java_lang_Math,         abs_name,   double_double_signature,           F_S)   \
  do_intrinsic(_fabs,                     java_lang_Math,         abs_name,   float_float_signature,             F_S)   \
  do_intrinsic(_iabs,                     java_lang_Math,         abs_name,   int_int_signature,                 F_S)   \
  do_intrinsic(_labs,                     java_lang_Math,         abs_name,   long_long_signature,               F_S)   \
  do_intrinsic(_dsin,                     java_lang_Math,         sin_name,   double_double_signature,           F_S)   \
  do_intrinsic(_floor,                    java_lang_Math,         floor_name, double_double_signature,           F_S)   \
  do_intrinsic(_ceil,                     java_lang_Math,         ceil_name,  double_double_signature,           F_S)   \
  do_intrinsic(_rint,                     java_lang_Math,         rint_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dcos,                     java_lang_Math,         cos_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dtan,                     java_lang_Math,         tan_name,   double_double_signature,           F_S)   \
  do_intrinsic(_datan2,                   java_lang_Math,         atan2_name, double2_double_signature,          F_S)   \
  do_intrinsic(_dsinh,                    java_lang_Math,         sinh_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dtanh,                    java_lang_Math,         tanh_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dcbrt,                    java_lang_Math,         cbrt_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dsqrt,                    java_lang_Math,         sqrt_name,  double_double_signature,           F_S)   \
  do_intrinsic(_dlog,                     java_lang_Math,         log_name,   double_double_signature,           F_S)   \
  do_intrinsic(_dlog10,                   java_lang_Math,         log10_name, double_double_signature,           F_S)   \
  do_intrinsic(_dpow,                     java_lang_Math,         pow_name,   double2_double_signature,          F_S)   \
  do_intrinsic(_dexp,                     java_lang_Math,         exp_name,   double_double_signature,           F_S)   \
  do_intrinsic(_min,                      java_lang_Math,         min_name,   int2_int_signature,                F_S)   \
  do_intrinsic(_max,                      java_lang_Math,         max_name,   int2_int_signature,                F_S)   \
  do_intrinsic(_addExactI,                java_lang_Math,         addExact_name, int2_int_signature,             F_S)   \
  do_intrinsic(_addExactL,                java_lang_Math,         addExact_name, long2_long_signature,           F_S)   \
  do_intrinsic(_decrementExactI,          java_lang_Math,         decrementExact_name, int_int_signature,        F_S)   \
  do_intrinsic(_decrementExactL,          java_lang_Math,         decrementExact_name, long_long_signature,      F_S)   \
  do_intrinsic(_incrementExactI,          java_lang_Math,         incrementExact_name, int_int_signature,        F_S)   \
  do_intrinsic(_incrementExactL,          java_lang_Math,         incrementExact_name, long_long_signature,      F_S)   \
  do_intrinsic(_multiplyExactI,           java_lang_Math,         multiplyExact_name, int2_int_signature,        F_S)   \
  do_intrinsic(_multiplyExactL,           java_lang_Math,         multiplyExact_name, long2_long_signature,      F_S)   \
  do_intrinsic(_multiplyHigh,             java_lang_Math,         multiplyHigh_name, long2_long_signature,       F_S)   \
  do_intrinsic(_unsignedMultiplyHigh,     java_lang_Math,         unsignedMultiplyHigh_name, long2_long_signature, F_S) \
  do_intrinsic(_negateExactI,             java_lang_Math,         negateExact_name, int_int_signature,           F_S)   \
  do_intrinsic(_negateExactL,             java_lang_Math,         negateExact_name, long_long_signature,         F_S)   \
  do_intrinsic(_subtractExactI,           java_lang_Math,         subtractExact_name, int2_int_signature,        F_S)   \
  do_intrinsic(_subtractExactL,           java_lang_Math,         subtractExact_name, long2_long_signature,      F_S)   \
  do_intrinsic(_fmaD,                     java_lang_Math,         fma_name,           double3_double_signature,  F_S)   \
  do_intrinsic(_fmaF,                     java_lang_Math,         fma_name,           float3_float_signature,    F_S)   \
  do_intrinsic(_maxF,                     java_lang_Math,         max_name,           float2_float_signature,    F_S)   \
  do_intrinsic(_minF,                     java_lang_Math,         min_name,           float2_float_signature,    F_S)   \
  do_intrinsic(_maxD,                     java_lang_Math,         max_name,           double2_double_signature,  F_S)   \
  do_intrinsic(_minD,                     java_lang_Math,         min_name,           double2_double_signature,  F_S)   \
  do_intrinsic(_maxL,                     java_lang_Math,         max_name,           long2_long_signature,      F_S)   \
  do_intrinsic(_minL,                     java_lang_Math,         min_name,           long2_long_signature,      F_S)   \
  do_intrinsic(_roundD,                   java_lang_Math,         round_name,         double_long_signature,     F_S)   \
  do_intrinsic(_roundF,                   java_lang_Math,         round_name,         float_int_signature,       F_S)   \
  do_intrinsic(_dcopySign,                java_lang_Math,         copySign_name,      double2_double_signature,  F_S)   \
  do_intrinsic(_fcopySign,                java_lang_Math,         copySign_name,      float2_float_signature,    F_S)   \
  do_intrinsic(_dsignum,                  java_lang_Math,         signum_name,        double_double_signature,   F_S)   \
  do_intrinsic(_fsignum,                  java_lang_Math,         signum_name,        float_float_signature,     F_S)   \
                                                                                                                        \
  /* StrictMath intrinsics, similar to what we have in Math. */                                                         \
  do_intrinsic(_min_strict,               java_lang_StrictMath,   min_name,           int2_int_signature,        F_S)   \
  do_intrinsic(_max_strict,               java_lang_StrictMath,   max_name,           int2_int_signature,        F_S)   \
  do_intrinsic(_minF_strict,              java_lang_StrictMath,   min_name,           float2_float_signature,    F_S)   \
  do_intrinsic(_maxF_strict,              java_lang_StrictMath,   max_name,           float2_float_signature,    F_S)   \
  do_intrinsic(_minD_strict,              java_lang_StrictMath,   min_name,           double2_double_signature,  F_S)   \
  do_intrinsic(_maxD_strict,              java_lang_StrictMath,   max_name,           double2_double_signature,  F_S)   \
  /* Additional dsqrt intrinsic to directly handle the sqrt method in StrictMath. Otherwise the same as in Math. */     \
  do_intrinsic(_dsqrt_strict,             java_lang_StrictMath,   sqrt_name,          double_double_signature,   F_S)   \
                                                                                                                        \
  do_intrinsic(_floatIsInfinite,          java_lang_Float,        isInfinite_name,    float_bool_signature,      F_S)   \
   do_name(     isInfinite_name,                                  "isInfinite")                                         \
  do_intrinsic(_floatIsFinite,            java_lang_Float,        isFinite_name,      float_bool_signature,      F_S)   \
   do_name(     isFinite_name,                                    "isFinite")                                           \
  do_intrinsic(_doubleIsInfinite,         java_lang_Double,       isInfinite_name,    double_bool_signature,     F_S)   \
  do_intrinsic(_doubleIsFinite,           java_lang_Double,       isFinite_name,      double_bool_signature,     F_S)   \
                                                                                                                        \
  do_intrinsic(_floatToRawIntBits,        java_lang_Float,        floatToRawIntBits_name,   float_int_signature, F_SN)  \
   do_name(     floatToRawIntBits_name,                          "floatToRawIntBits")                                   \
  do_intrinsic(_floatToIntBits,           java_lang_Float,        floatToIntBits_name,      float_int_signature, F_S)   \
   do_name(     floatToIntBits_name,                             "floatToIntBits")                                      \
  do_intrinsic(_intBitsToFloat,           java_lang_Float,        intBitsToFloat_name,      int_float_signature, F_SN)  \
   do_name(     intBitsToFloat_name,                             "intBitsToFloat")                                      \
  do_intrinsic(_doubleToRawLongBits,      java_lang_Double,       doubleToRawLongBits_name, double_long_signature, F_SN)\
   do_name(     doubleToRawLongBits_name,                        "doubleToRawLongBits")                                 \
  do_intrinsic(_doubleToLongBits,         java_lang_Double,       doubleToLongBits_name,    double_long_signature, F_S) \
   do_name(     doubleToLongBits_name,                           "doubleToLongBits")                                    \
  do_intrinsic(_longBitsToDouble,         java_lang_Double,       longBitsToDouble_name,    long_double_signature, F_SN)\
   do_name(     longBitsToDouble_name,                           "longBitsToDouble")                                    \
  do_intrinsic(_float16ToFloat,           java_lang_Float,        float16ToFloat_name,      f16_float_signature, F_S)   \
   do_name(     float16ToFloat_name,                             "float16ToFloat")                                      \
   do_signature(f16_float_signature,                             "(S)F")                                                \
  do_intrinsic(_floatToFloat16,           java_lang_Float,        floatToFloat16_name,      float_f16_signature, F_S)   \
   do_name(     floatToFloat16_name,                             "floatToFloat16")                                      \
   do_signature(float_f16_signature,                             "(F)S")                                                \
                                                                                                                        \
  do_intrinsic(_compareUnsigned_i,        java_lang_Integer,      compareUnsigned_name,     int2_int_signature,  F_S)   \
  do_intrinsic(_compareUnsigned_l,        java_lang_Long,         compareUnsigned_name,     long2_int_signature, F_S)   \
   do_name(     compareUnsigned_name,                            "compareUnsigned")                                     \
                                                                                                                        \
  do_intrinsic(_divideUnsigned_i,         java_lang_Integer,      divideUnsigned_name,      int2_int_signature,  F_S)   \
  do_intrinsic(_remainderUnsigned_i,      java_lang_Integer,      remainderUnsigned_name,   int2_int_signature,  F_S)   \
   do_name(     divideUnsigned_name,                             "divideUnsigned")                                      \
  do_intrinsic(_divideUnsigned_l,         java_lang_Long,         divideUnsigned_name,      long2_long_signature, F_S)  \
  do_intrinsic(_remainderUnsigned_l,      java_lang_Long,         remainderUnsigned_name,   long2_long_signature, F_S)  \
   do_name(     remainderUnsigned_name,                          "remainderUnsigned")                                   \
                                                                                                                        \
  do_intrinsic(_numberOfLeadingZeros_i,   java_lang_Integer,      numberOfLeadingZeros_name,int_int_signature,   F_S)   \
  do_intrinsic(_numberOfLeadingZeros_l,   java_lang_Long,         numberOfLeadingZeros_name,long_int_signature,  F_S)   \
                                                                                                                        \
  do_intrinsic(_numberOfTrailingZeros_i,  java_lang_Integer,      numberOfTrailingZeros_name,int_int_signature,  F_S)   \
  do_intrinsic(_numberOfTrailingZeros_l,  java_lang_Long,         numberOfTrailingZeros_name,long_int_signature, F_S)   \
                                                                                                                        \
  do_intrinsic(_bitCount_i,               java_lang_Integer,      bitCount_name,            int_int_signature,   F_S)   \
  do_intrinsic(_bitCount_l,               java_lang_Long,         bitCount_name,            long_int_signature,  F_S)   \
  do_intrinsic(_compress_i,               java_lang_Integer,      compress_name,            int2_int_signature,   F_S)  \
  do_intrinsic(_compress_l,               java_lang_Long,         compress_name,            long2_long_signature, F_S)  \
  do_intrinsic(_expand_i,                 java_lang_Integer,      expand_name,              int2_int_signature,   F_S)  \
  do_intrinsic(_expand_l,                 java_lang_Long,         expand_name,              long2_long_signature, F_S)  \
                                                                                                                        \
  do_intrinsic(_reverse_i,                java_lang_Integer,      reverse_name,             int_int_signature,   F_S)   \
   do_name(     reverse_name,                                    "reverse")                                             \
  do_intrinsic(_reverse_l,                java_lang_Long,         reverse_name,             long_long_signature, F_S)   \
  do_intrinsic(_reverseBytes_i,           java_lang_Integer,      reverseBytes_name,        int_int_signature,   F_S)   \
   do_name(     reverseBytes_name,                               "reverseBytes")                                        \
  do_intrinsic(_reverseBytes_l,           java_lang_Long,         reverseBytes_name,        long_long_signature, F_S)   \
    /*  (symbol reverseBytes_name defined above) */                                                                     \
  do_intrinsic(_reverseBytes_c,           java_lang_Character,    reverseBytes_name,        char_char_signature, F_S)   \
    /*  (symbol reverseBytes_name defined above) */                                                                     \
  do_intrinsic(_reverseBytes_s,           java_lang_Short,        reverseBytes_name,        short_short_signature, F_S) \
    /*  (symbol reverseBytes_name defined above) */                                                                     \
                                                                                                                        \
  do_intrinsic(_identityHashCode,         java_lang_System,       identityHashCode_name, object_int_signature,   F_SN)  \
   do_name(     identityHashCode_name,                           "identityHashCode")                                    \
  do_intrinsic(_currentTimeMillis,        java_lang_System,       currentTimeMillis_name, void_long_signature,   F_SN)  \
                                                                                                                        \
   do_name(     currentTimeMillis_name,                          "currentTimeMillis")                                   \
  do_intrinsic(_nanoTime,                 java_lang_System,       nanoTime_name,          void_long_signature,   F_SN)  \
   do_name(     nanoTime_name,                                   "nanoTime")                                            \
                                                                                                                        \
  JFR_INTRINSICS(do_intrinsic, do_class, do_name, do_signature, do_alias)                                               \
                                                                                                                        \
  do_intrinsic(_arraycopy,                java_lang_System,       arraycopy_name, arraycopy_signature,           F_SN)  \
   do_name(     arraycopy_name,                                  "arraycopy")                                           \
   do_signature(arraycopy_signature,                             "(Ljava/lang/Object;ILjava/lang/Object;II)V")          \
                                                                                                                        \
  do_intrinsic(_currentCarrierThread,     java_lang_Thread,       currentCarrierThread_name, currentThread_signature, F_SN) \
   do_name(     currentCarrierThread_name,                       "currentCarrierThread")                                \
  do_intrinsic(_currentThread,            java_lang_Thread,       currentThread_name, currentThread_signature,   F_SN)  \
   do_name(     currentThread_name,                              "currentThread")                                       \
   do_signature(currentThread_signature,                         "()Ljava/lang/Thread;")                                \
  do_intrinsic(_scopedValueCache,         java_lang_Thread,       scopedValueCache_name, scopedValueCache_signature, F_SN) \
   do_name(     scopedValueCache_name,                           "scopedValueCache")                                    \
   do_signature(scopedValueCache_signature,                      "()[Ljava/lang/Object;")                               \
  do_intrinsic(_setScopedValueCache,      java_lang_Thread,       setScopedValueCache_name, setScopedValueCache_signature, F_SN) \
   do_name(     setScopedValueCache_name,                        "setScopedValueCache")                                 \
   do_signature(setScopedValueCache_signature,                   "([Ljava/lang/Object;)V")                              \
  do_intrinsic(_findScopedValueBindings,  java_lang_Thread,       findScopedValueBindings_name, void_object_signature, F_SN) \
   do_name(     findScopedValueBindings_name,                    "findScopedValueBindings")                             \
                                                                                                                        \
  do_intrinsic(_setCurrentThread,         java_lang_Thread,       setCurrentThread_name, thread_void_signature,   F_RN) \
   do_name(     setCurrentThread_name,                           "setCurrentThread")                                    \
                                                                                                                        \
  /* reflective intrinsics, for java/lang/Class, etc. */                                                                \
  do_intrinsic(_isAssignableFrom,         java_lang_Class,        isAssignableFrom_name, class_boolean_signature, F_RN) \
   do_name(     isAssignableFrom_name,                           "isAssignableFrom")                                    \
  do_intrinsic(_isInstance,               java_lang_Class,        isInstance_name, object_boolean_signature,     F_RN)  \
   do_name(     isInstance_name,                                 "isInstance")                                          \
  do_intrinsic(_isHidden,                 java_lang_Class,        isHidden_name, void_boolean_signature,         F_RN)  \
   do_name(     isHidden_name,                                   "isHidden")                                            \
  do_intrinsic(_getSuperclass,            java_lang_Class,        getSuperclass_name, void_class_signature,      F_RN)  \
   do_name(     getSuperclass_name,                              "getSuperclass")                                       \
  do_intrinsic(_Class_cast,               java_lang_Class,        Class_cast_name, object_object_signature,      F_R)   \
   do_name(     Class_cast_name,                                 "cast")                                                \
                                                                                                                        \
  do_intrinsic(_getLength,                java_lang_reflect_Array, getLength_name, object_int_signature,         F_SN)  \
   do_name(     getLength_name,                                   "getLength")                                          \
                                                                                                                        \
  do_intrinsic(_getCallerClass,           reflect_Reflection,     getCallerClass_name, void_class_signature,     F_SN)  \
   do_name(     getCallerClass_name,                             "getCallerClass")                                      \
                                                                                                                        \
  do_intrinsic(_newArray,                 java_lang_reflect_Array, newArray_name, newArray_signature,            F_SN)  \
   do_name(     newArray_name,                                    "newArray")                                           \
   do_signature(newArray_signature,                               "(Ljava/lang/Class;I)Ljava/lang/Object;")             \
                                                                                                                        \
  do_intrinsic(_onSpinWait,               java_lang_Thread,       onSpinWait_name, onSpinWait_signature,         F_S)   \
   do_name(     onSpinWait_name,                                  "onSpinWait")                                         \
   do_alias(    onSpinWait_signature,                             void_method_signature)                                \
                                                                                                                        \
  do_intrinsic(_ensureMaterializedForStackWalk, java_lang_Thread, ensureMaterializedForStackWalk_name, object_void_signature, F_SN)  \
   do_name(     ensureMaterializedForStackWalk_name,              "ensureMaterializedForStackWalk")                     \
                                                                                                                        \
  do_intrinsic(_copyOf,                   java_util_Arrays,       copyOf_name, copyOf_signature,                 F_S)   \
   do_name(     copyOf_name,                                     "copyOf")                                              \
   do_signature(copyOf_signature,             "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;")             \
                                                                                                                        \
  do_intrinsic(_arraySort,                java_util_DualPivotQuicksort, arraySort_name, arraySort_signature,     F_S)   \
   do_name(     arraySort_name,                                  "sort")                                                \
   do_signature(arraySort_signature, "(Ljava/lang/Class;Ljava/lang/Object;JIILjava/util/DualPivotQuicksort$SortOperation;)V") \
                                                                                                                        \
  do_intrinsic(_arrayPartition,           java_util_DualPivotQuicksort, arrayPartition_name, arrayPartition_signature, F_S) \
   do_name(     arrayPartition_name,                             "partition")                                           \
   do_signature(arrayPartition_signature, "(Ljava/lang/Class;Ljava/lang/Object;JIIIILjava/util/DualPivotQuicksort$PartitionOperation;)[I") \
                                                                                                                        \
  do_intrinsic(_copyOfRange,              java_util_Arrays,       copyOfRange_name, copyOfRange_signature,       F_S)   \
   do_name(     copyOfRange_name,                                "copyOfRange")                                         \
   do_signature(copyOfRange_signature,        "([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;")            \
                                                                                                                        \
  do_intrinsic(_equalsC,                  java_util_Arrays,       equals_name,    equalsC_signature,             F_S)   \
   do_signature(equalsC_signature,                               "([C[C)Z")                                             \
  do_intrinsic(_equalsB,                  java_util_Arrays,       equals_name,    equalsB_signature,             F_S)   \
   do_signature(equalsB_signature,                               "([B[B)Z")                                             \
                                                                                                                        \
  do_intrinsic(_vectorizedHashCode,       jdk_internal_util_ArraysSupport, vectorizedHashCode_name,  vectorizedHashCode_signature, F_S)   \
   do_name(     vectorizedHashCode_name,                         "vectorizedHashCode")                                  \
   do_signature(vectorizedHashCode_signature,                    "(Ljava/lang/Object;IIII)I")                           \
                                                                                                                        \
  do_intrinsic(_compressStringC,          java_lang_StringUTF16,  compress_name, encodeISOArray_signature,       F_S)   \
   do_name(     compress_name,                                   "compress")                                            \
  do_intrinsic(_compressStringB,          java_lang_StringUTF16,  compress_name, indexOfI_signature,             F_S)   \
  do_intrinsic(_inflateStringC,           java_lang_StringLatin1, inflate_name, inflateC_signature,              F_S)   \
   do_name(     inflate_name,                                    "inflate")                                             \
   do_signature(inflateC_signature,                              "([BI[CII)V")                                          \
  do_intrinsic(_inflateStringB,           java_lang_StringLatin1, inflate_name, inflateB_signature,              F_S)   \
   do_signature(inflateB_signature,                              "([BI[BII)V")                                          \
  do_intrinsic(_toBytesStringU,           java_lang_StringUTF16, toBytes_name, toBytesU_signature,               F_S)   \
   do_name(     toBytes_name,                                    "toBytes")                                             \
   do_signature(toBytesU_signature,                              "([CII)[B")                                            \
  do_intrinsic(_getCharsStringU,          java_lang_StringUTF16, getCharsU_name, getCharsU_signature,            F_S)   \
   do_name(     getCharsU_name,                                  "getChars")                                            \
   do_signature(getCharsU_signature,                             "([BII[CI)V")                                          \
  do_intrinsic(_getCharStringU,           java_lang_StringUTF16, getChar_name, getCharStringU_signature,         F_S)   \
   do_signature(getCharStringU_signature,                        "([BI)C")                                              \
  do_intrinsic(_putCharStringU,           java_lang_StringUTF16, putChar_name, putCharStringU_signature,         F_S)   \
   do_signature(putCharStringU_signature,                        "([BII)V")                                             \
  do_intrinsic(_compareToL,               java_lang_StringLatin1,compareTo_name, compareTo_indexOf_signature,    F_S)   \
  do_intrinsic(_compareToU,               java_lang_StringUTF16, compareTo_name, compareTo_indexOf_signature,    F_S)   \
  do_intrinsic(_compareToLU,              java_lang_StringLatin1,compareToLU_name, compareTo_indexOf_signature,  F_S)   \
  do_intrinsic(_compareToUL,              java_lang_StringUTF16, compareToUL_name, compareTo_indexOf_signature,  F_S)   \
   do_signature(compareTo_indexOf_signature,                     "([B[B)I")                                             \
   do_name(     compareTo_name,                                  "compareTo")                                           \
   do_name(     compareToLU_name,                                "compareToUTF16")                                      \
   do_name(     compareToUL_name,                                "compareToLatin1")                                     \
  do_intrinsic(_indexOfL,                 java_lang_StringLatin1,indexOf_name, compareTo_indexOf_signature,      F_S)   \
  do_intrinsic(_indexOfU,                 java_lang_StringUTF16, indexOf_name, compareTo_indexOf_signature,      F_S)   \
  do_intrinsic(_indexOfUL,                java_lang_StringUTF16, indexOfUL_name, compareTo_indexOf_signature,    F_S)   \
  do_intrinsic(_indexOfIL,                java_lang_StringLatin1,indexOf_name, indexOfI_signature,               F_S)   \
  do_intrinsic(_indexOfIU,                java_lang_StringUTF16, indexOf_name, indexOfI_signature,               F_S)   \
  do_intrinsic(_indexOfIUL,               java_lang_StringUTF16, indexOfUL_name, indexOfI_signature,             F_S)   \
  do_intrinsic(_indexOfU_char,            java_lang_StringUTF16, indexOfChar_name, indexOfChar_signature,        F_S)   \
  do_intrinsic(_indexOfL_char,            java_lang_StringLatin1,indexOfChar_name, indexOfChar_signature,        F_S)   \
   do_name(     indexOf_name,                                    "indexOf")                                             \
   do_name(     indexOfChar_name,                                "indexOfChar")                                         \
   do_name(     indexOfUL_name,                                  "indexOfLatin1")                                       \
   do_signature(indexOfI_signature,                              "([BI[BII)I")                                          \
   do_signature(indexOfChar_signature,                           "([BIII)I")                                            \
  do_intrinsic(_equalsL,                  java_lang_StringLatin1,equals_name, equalsB_signature,                 F_S)   \
                                                                                                                        \
  do_intrinsic(_isDigit,                  java_lang_CharacterDataLatin1, isDigit_name,      int_bool_signature,  F_R)   \
   do_name(     isDigit_name,                                           "isDigit")                                      \
  do_intrinsic(_isLowerCase,              java_lang_CharacterDataLatin1, isLowerCase_name,  int_bool_signature,  F_R)   \
   do_name(     isLowerCase_name,                                       "isLowerCase")                                  \
  do_intrinsic(_isUpperCase,              java_lang_CharacterDataLatin1, isUpperCase_name,  int_bool_signature,  F_R)   \
   do_name(     isUpperCase_name,                                       "isUpperCase")                                  \
  do_intrinsic(_isWhitespace,             java_lang_CharacterDataLatin1, isWhitespace_name, int_bool_signature,  F_R)   \
   do_name(     isWhitespace_name,                                      "isWhitespace")                                 \
                                                                                                                        \
  do_intrinsic(_Preconditions_checkIndex, jdk_internal_util_Preconditions, checkIndex_name, Preconditions_checkIndex_signature, F_S)   \
   do_signature(Preconditions_checkIndex_signature,              "(IILjava/util/function/BiFunction;)I")                \
  do_intrinsic(_Preconditions_checkLongIndex, jdk_internal_util_Preconditions, checkIndex_name, Preconditions_checkLongIndex_signature, F_S)   \
   do_signature(Preconditions_checkLongIndex_signature,          "(JJLjava/util/function/BiFunction;)J")                \
                                                                                                                        \
  do_class(java_lang_StringCoding,        "java/lang/StringCoding")                                                     \
  do_intrinsic(_countPositives,     java_lang_StringCoding, countPositives_name, countPositives_signature, F_S)         \
   do_name(     countPositives_name,                       "countPositives0")                                           \
   do_signature(countPositives_signature,                  "([BII)I")                                                   \
                                                                                                                        \
  do_class(sun_nio_cs_iso8859_1_Encoder,  "sun/nio/cs/ISO_8859_1$Encoder")                                              \
  do_intrinsic(_encodeISOArray,     sun_nio_cs_iso8859_1_Encoder, encodeISOArray_name, encodeISOArray_signature, F_S)   \
   do_name(     encodeISOArray_name,                             "encodeISOArray0")                                     \
   do_signature(encodeISOArray_signature,                        "([CI[BII)I")                                          \
                                                                                                                        \
  do_intrinsic(_encodeByteISOArray,     java_lang_StringCoding, encodeISOArray_name, indexOfI_signature,         F_S)   \
                                                                                                                        \
  do_intrinsic(_encodeAsciiArray,       java_lang_StringCoding, encodeAsciiArray_name, encodeISOArray_signature, F_S)   \
   do_name(     encodeAsciiArray_name,                           "encodeAsciiArray0")                                   \
                                                                                                                        \
  do_class(java_math_BigInteger,                      "java/math/BigInteger")                                           \
  do_intrinsic(_multiplyToLen,      java_math_BigInteger, multiplyToLen_name, multiplyToLen_signature, F_S)             \
   do_name(     multiplyToLen_name,                             "implMultiplyToLen")                                    \
   do_signature(multiplyToLen_signature,                        "([II[II[I)[I")                                         \
                                                                                                                        \
  do_intrinsic(_squareToLen, java_math_BigInteger, squareToLen_name, squareToLen_signature, F_S)                        \
   do_name(     squareToLen_name,                             "implSquareToLen")                                        \
   do_signature(squareToLen_signature,                        "([II[II)[I")                                             \
                                                                                                                        \
  do_intrinsic(_mulAdd, java_math_BigInteger, mulAdd_name, mulAdd_signature, F_S)                                       \
   do_name(     mulAdd_name,                                  "implMulAdd")                                             \
   do_signature(mulAdd_signature,                             "([I[IIII)I")                                             \
                                                                                                                        \
  do_intrinsic(_montgomeryMultiply,      java_math_BigInteger, montgomeryMultiply_name, montgomeryMultiply_signature, F_S) \
   do_name(     montgomeryMultiply_name,                             "implMontgomeryMultiply")                          \
   do_signature(montgomeryMultiply_signature,                        "([I[I[IIJ[I)[I")                                  \
                                                                                                                        \
  do_intrinsic(_montgomerySquare,      java_math_BigInteger, montgomerySquare_name, montgomerySquare_signature, F_S)    \
   do_name(     montgomerySquare_name,                             "implMontgomerySquare")                              \
   do_signature(montgomerySquare_signature,                        "([I[IIJ[I)[I")                                      \
                                                                                                                        \
  do_intrinsic(_bigIntegerRightShiftWorker, java_math_BigInteger, rightShift_name, big_integer_shift_worker_signature, F_S) \
   do_name(     rightShift_name,                                 "shiftRightImplWorker")                                \
                                                                                                                        \
  do_intrinsic(_bigIntegerLeftShiftWorker, java_math_BigInteger, leftShift_name, big_integer_shift_worker_signature, F_S) \
   do_name(     leftShift_name,                                 "shiftLeftImplWorker")                                  \
                                                                                                                        \
  do_class(jdk_internal_util_ArraysSupport, "jdk/internal/util/ArraysSupport")                                                          \
  do_intrinsic(_vectorizedMismatch, jdk_internal_util_ArraysSupport, vectorizedMismatch_name, vectorizedMismatch_signature, F_S)\
   do_name(vectorizedMismatch_name, "vectorizedMismatch")                                                               \
   do_signature(vectorizedMismatch_signature, "(Ljava/lang/Object;JLjava/lang/Object;JII)I")                            \
                                                                                                                        \
  /* java/lang/ref/Reference */                                                                                         \
  do_intrinsic(_Reference_get0,             java_lang_ref_Reference, get0_name,      void_object_signature,    F_RN)    \
  do_intrinsic(_Reference_refersTo0,        java_lang_ref_Reference, refersTo0_name, object_boolean_signature, F_RN)    \
  do_intrinsic(_PhantomReference_refersTo0, java_lang_ref_PhantomReference, refersTo0_name, object_boolean_signature, F_RN) \
  do_intrinsic(_Reference_clear0,           java_lang_ref_Reference, clear0_name,    void_method_signature, F_RN)       \
  do_intrinsic(_PhantomReference_clear0,    java_lang_ref_PhantomReference, clear0_name, void_method_signature, F_RN)   \
                                                                                                                        \
  /* support for com.sun.crypto.provider.AES_Crypt and some of its callers */                                            \
  do_class(com_sun_crypto_provider_aescrypt,      "com/sun/crypto/provider/AES_Crypt")                                   \
  do_intrinsic(_aescrypt_encryptBlock, com_sun_crypto_provider_aescrypt, encryptBlock_name, byteArray_int_byteArray_int_signature, F_R)   \
  do_intrinsic(_aescrypt_decryptBlock, com_sun_crypto_provider_aescrypt, decryptBlock_name, byteArray_int_byteArray_int_signature, F_R)   \
   do_name(     encryptBlock_name,                                 "implEncryptBlock")                                  \
   do_name(     decryptBlock_name,                                 "implDecryptBlock")                                  \
   do_signature(byteArray_int_byteArray_int_signature,             "([BI[BI)V")                                         \
                                                                                                                        \
  do_class(com_sun_crypto_provider_cipherBlockChaining,            "com/sun/crypto/provider/CipherBlockChaining")       \
   do_intrinsic(_cipherBlockChaining_encryptAESCrypt, com_sun_crypto_provider_cipherBlockChaining, encrypt_name, byteArray_int_int_byteArray_int_signature, F_R)   \
   do_intrinsic(_cipherBlockChaining_decryptAESCrypt, com_sun_crypto_provider_cipherBlockChaining, decrypt_name, byteArray_int_int_byteArray_int_signature, F_R)   \
   do_name(     encrypt_name,                                      "implEncrypt")                                       \
   do_name(     decrypt_name,                                      "implDecrypt")                                       \
   do_signature(byteArray_int_int_byteArray_int_signature,         "([BII[BI)I")                                        \
                                                                                                                        \
  do_class(com_sun_crypto_provider_electronicCodeBook, "com/sun/crypto/provider/ElectronicCodeBook")                    \
   do_intrinsic(_electronicCodeBook_encryptAESCrypt, com_sun_crypto_provider_electronicCodeBook, ecb_encrypt_name, byteArray_int_int_byteArray_int_signature, F_R)  \
   do_intrinsic(_electronicCodeBook_decryptAESCrypt, com_sun_crypto_provider_electronicCodeBook, ecb_decrypt_name, byteArray_int_int_byteArray_int_signature, F_R)  \
   do_name(ecb_encrypt_name, "implECBEncrypt")                                                                          \
   do_name(ecb_decrypt_name, "implECBDecrypt")                                                                          \
                                                                                                                        \
  do_class(com_sun_crypto_provider_counterMode,      "com/sun/crypto/provider/CounterMode")                             \
   do_intrinsic(_counterMode_AESCrypt, com_sun_crypto_provider_counterMode, crypt_name, byteArray_int_int_byteArray_int_signature, F_R)   \
   do_name(     crypt_name,                                 "implCrypt")                                                    \
                                                                                                                        \
  do_class(com_sun_crypto_provider_galoisCounterMode, "com/sun/crypto/provider/GaloisCounterMode")                      \
   do_intrinsic(_galoisCounterMode_AESCrypt, com_sun_crypto_provider_galoisCounterMode, gcm_crypt_name, aes_gcm_signature, F_S)   \
   do_name(gcm_crypt_name, "implGCMCrypt0")                                                                                 \
   do_signature(aes_gcm_signature, "([BII[BI[BILcom/sun/crypto/provider/GCTR;Lcom/sun/crypto/provider/GHASH;)I")                                                             \
                                                                                                                        \
  /* support for sun.security.provider.MD5 */                                                                           \
  do_class(sun_security_provider_md5,                              "sun/security/provider/MD5")                         \
  do_intrinsic(_md5_implCompress, sun_security_provider_md5, implCompress_name, implCompress_signature, F_R)            \
   do_name(     implCompress_name,                                 "implCompress0")                                     \
   do_signature(implCompress_signature,                            "([BI)V")                                            \
                                                                                                                        \
  /* support for sun.security.provider.SHA */                                                                           \
  do_class(sun_security_provider_sha,                              "sun/security/provider/SHA")                         \
  do_intrinsic(_sha_implCompress, sun_security_provider_sha, implCompress_name, implCompress_signature, F_R)            \
                                                                                                                        \
  /* support for sun.security.provider.SHA2 */                                                                          \
  do_class(sun_security_provider_sha2,                             "sun/security/provider/SHA2")                        \
  do_intrinsic(_sha2_implCompress, sun_security_provider_sha2, implCompress_name, implCompress_signature, F_R)          \
                                                                                                                        \
  /* support for sun.security.provider.SHA5 */                                                                          \
  do_class(sun_security_provider_sha5,                             "sun/security/provider/SHA5")                        \
  do_intrinsic(_sha5_implCompress, sun_security_provider_sha5, implCompress_name, implCompress_signature, F_R)          \
                                                                                                                        \
  /* support for sun.security.provider.SHA3 */                                                                          \
  do_class(sun_security_provider_sha3,                             "sun/security/provider/SHA3")                        \
  do_intrinsic(_sha3_implCompress, sun_security_provider_sha3, implCompress_name, implCompress_signature, F_R)          \
                                                                                                                        \
  /* support for sun.security.provider.SHAKE128Parallel */                                                              \
  do_class(sun_security_provider_sha3_parallel,                "sun/security/provider/SHA3Parallel")                    \
   do_intrinsic(_double_keccak, sun_security_provider_sha3_parallel, double_keccak_name, double_keccak_signature, F_S)   \
   do_name(     double_keccak_name,                                 "doubleKeccak")                                     \
   do_signature(double_keccak_signature,                            "([J[J)I")                                          \
                                                                                                                        \
  /* support for sun.security.provider.DigestBase */                                                                    \
  do_class(sun_security_provider_digestbase,                       "sun/security/provider/DigestBase")                  \
  do_intrinsic(_digestBase_implCompressMB, sun_security_provider_digestbase, implCompressMB_name, countPositives_signature, F_R)   \
   do_name(     implCompressMB_name,                               "implCompressMultiBlock0")                           \
                                                                                                                        \
  /* support for sun.security.util.math.intpoly.MontgomeryIntegerPolynomialP256 */                                      \
  do_class(sun_security_util_math_intpoly_MontgomeryIntegerPolynomialP256, "sun/security/util/math/intpoly/MontgomeryIntegerPolynomialP256")  \
  do_intrinsic(_intpoly_montgomeryMult_P256, sun_security_util_math_intpoly_MontgomeryIntegerPolynomialP256, intPolyMult_name, intPolyMult_signature, F_R) \
  do_name(intPolyMult_name, "mult")                                                                                     \
  do_signature(intPolyMult_signature, "([J[J[J)V")                                                                      \
                                                                                                                        \
  do_class(sun_security_util_math_intpoly_IntegerPolynomial, "sun/security/util/math/intpoly/IntegerPolynomial")        \
  do_intrinsic(_intpoly_assign, sun_security_util_math_intpoly_IntegerPolynomial, intPolyAssign_name, intPolyAssign_signature, F_S) \
   do_name(intPolyAssign_name, "conditionalAssign")                                                                     \
   do_signature(intPolyAssign_signature, "(I[J[J)V")                                                                    \
                                                                                                                        \
  /* support for java.util.Base64.Encoder*/                                                                             \
  do_class(java_util_Base64_Encoder, "java/util/Base64$Encoder")                                                        \
  do_intrinsic(_base64_encodeBlock, java_util_Base64_Encoder, encodeBlock_name, encodeBlock_signature, F_R)             \
  do_name(encodeBlock_name, "encodeBlock")                                                                              \
  do_signature(encodeBlock_signature, "([BII[BIZ)V")                                                                    \
                                                                                                                        \
  /* support for java.util.Base64.Decoder*/                                                                             \
  do_class(java_util_Base64_Decoder, "java/util/Base64$Decoder")                                                        \
  do_intrinsic(_base64_decodeBlock, java_util_Base64_Decoder, decodeBlock_name, decodeBlock_signature, F_R)             \
   do_name(decodeBlock_name, "decodeBlock")                                                                             \
   do_signature(decodeBlock_signature, "([BII[BIZZ)I")                                                                  \
                                                                                                                        \
  /* support for com.sun.crypto.provider.GHASH */                                                                       \
  do_class(com_sun_crypto_provider_ghash, "com/sun/crypto/provider/GHASH")                                              \
  do_intrinsic(_ghash_processBlocks, com_sun_crypto_provider_ghash, processBlocks_name, ghash_processBlocks_signature, F_S) \
   do_name(processBlocks_name, "processBlocks")                                                                         \
   do_signature(ghash_processBlocks_signature, "([BII[J[J)V")                                                           \
                                                                                                                        \
  /* support for com.sun.crypto.provider.Poly1305 */                                                                    \
  do_class(com_sun_crypto_provider_Poly1305, "com/sun/crypto/provider/Poly1305")                                        \
  do_intrinsic(_poly1305_processBlocks, com_sun_crypto_provider_Poly1305, processMultipleBlocks_name, ghash_processBlocks_signature, F_R) \
   do_name(processMultipleBlocks_name, "processMultipleBlocks")                                                         \
                                                                                                                        \
  /* support for com.sun.crypto.provider.ChaCha20Cipher */                                                              \
  do_class(com_sun_crypto_provider_chacha20cipher,      "com/sun/crypto/provider/ChaCha20Cipher")                       \
  do_intrinsic(_chacha20Block, com_sun_crypto_provider_chacha20cipher, chacha20Block_name, chacha20Block_signature, F_S) \
   do_name(chacha20Block_name,                                 "implChaCha20Block")                                         \
   do_signature(chacha20Block_signature, "([I[B)I")                                                                    \
                                                                                                                        \
  /* support for com.sun.crypto.provider.ML_KEM */                                                                      \
  do_class(com_sun_crypto_provider_ML_KEM,      "com/sun/crypto/provider/ML_KEM")                                       \
   do_signature(SaSaSaSaI_signature, "([S[S[S[S)I")                                                                     \
   do_signature(BaISaII_signature, "([BI[SI)I")                                                                         \
   do_signature(SaSaSaI_signature, "([S[S[S)I")                                                                         \
   do_signature(SaSaI_signature, "([S[S)I")                                                                             \
   do_signature(SaI_signature, "([S)I")                                                                                 \
   do_name(kyberAddPoly_name,                             "implKyberAddPoly")                                           \
  do_intrinsic(_kyberNtt, com_sun_crypto_provider_ML_KEM, kyberNtt_name, SaSaI_signature, F_S)                          \
   do_name(kyberNtt_name,                                  "implKyberNtt")                                              \
  do_intrinsic(_kyberInverseNtt, com_sun_crypto_provider_ML_KEM, kyberInverseNtt_name, SaSaI_signature, F_S)            \
   do_name(kyberInverseNtt_name,                           "implKyberInverseNtt")                                       \
  do_intrinsic(_kyberNttMult, com_sun_crypto_provider_ML_KEM, kyberNttMult_name, SaSaSaSaI_signature, F_S)              \
   do_name(kyberNttMult_name,                              "implKyberNttMult")                                          \
  do_intrinsic(_kyberAddPoly_2, com_sun_crypto_provider_ML_KEM, kyberAddPoly_name, SaSaSaI_signature, F_S)              \
  do_intrinsic(_kyberAddPoly_3, com_sun_crypto_provider_ML_KEM, kyberAddPoly_name, SaSaSaSaI_signature, F_S)            \
  do_intrinsic(_kyber12To16, com_sun_crypto_provider_ML_KEM, kyber12To16_name, BaISaII_signature, F_S)                  \
   do_name(kyber12To16_name,                             "implKyber12To16")                                             \
  do_intrinsic(_kyberBarrettReduce, com_sun_crypto_provider_ML_KEM, kyberBarrettReduce_name, SaI_signature, F_S)        \
   do_name(kyberBarrettReduce_name,                        "implKyberBarrettReduce")                                    \
                                                                                                                        \
  /* support for sun.security.provider.ML_DSA */                                                                        \
  do_class(sun_security_provider_ML_DSA,      "sun/security/provider/ML_DSA")                                           \
   do_signature(IaII_signature, "([II)I")                                                                               \
   do_signature(IaIaI_signature, "([I[I)I")                                                                             \
   do_signature(IaIaIaI_signature, "([I[I[I)I")                                                                         \
   do_signature(IaIaIaIII_signature, "([I[I[III)I")                                                                     \
  do_intrinsic(_dilithiumAlmostNtt, sun_security_provider_ML_DSA, dilithiumAlmostNtt_name, IaIaI_signature, F_S)        \
   do_name(dilithiumAlmostNtt_name,                            "implDilithiumAlmostNtt")                                \
  do_intrinsic(_dilithiumAlmostInverseNtt, sun_security_provider_ML_DSA,                                                \
                dilithiumAlmostInverseNtt_name, IaIaI_signature, F_S)                                                   \
   do_name(dilithiumAlmostInverseNtt_name,                     "implDilithiumAlmostInverseNtt")                         \
  do_intrinsic(_dilithiumNttMult, sun_security_provider_ML_DSA, dilithiumNttMult_name, IaIaIaI_signature, F_S)          \
   do_name(dilithiumNttMult_name,                              "implDilithiumNttMult")                                  \
  do_intrinsic(_dilithiumMontMulByConstant, sun_security_provider_ML_DSA,                                               \
                dilithiumMontMulByConstant_name, IaII_signature, F_S)                                                   \
   do_name(dilithiumMontMulByConstant_name,                    "implDilithiumMontMulByConstant")                        \
  do_intrinsic(_dilithiumDecomposePoly, sun_security_provider_ML_DSA,                                                   \
                dilithiumDecomposePoly_name, IaIaIaIII_signature, F_S)                                                  \
   do_name(dilithiumDecomposePoly_name,                    "implDilithiumDecomposePoly")                                \
                                                                                                                        \
  /* support for java.util.zip */                                                                                       \
  do_class(java_util_zip_CRC32,           "java/util/zip/CRC32")                                                        \
  do_intrinsic(_updateCRC32,               java_util_zip_CRC32,   update_name, int2_int_signature,               F_SN)  \
   do_name(     update_name,                                      "update")                                             \
  do_intrinsic(_updateBytesCRC32,          java_util_zip_CRC32,   updateBytes_name, updateBytes_signature,       F_SN)  \
   do_name(     updateBytes_name,                                "updateBytes0")                                        \
   do_signature(updateBytes_signature,                           "(I[BII)I")                                            \
  do_intrinsic(_updateByteBufferCRC32,     java_util_zip_CRC32,   updateByteBuffer_name, updateByteBuffer_signature, F_SN) \
   do_name(     updateByteBuffer_name,                           "updateByteBuffer0")                                   \
   do_signature(updateByteBuffer_signature,                      "(IJII)I")                                             \
                                                                                                                        \
  /* support for java.util.zip.CRC32C */                                                                                \
  do_class(java_util_zip_CRC32C,          "java/util/zip/CRC32C")                                                       \
  do_intrinsic(_updateBytesCRC32C,         java_util_zip_CRC32C,  updateBytes_C_name, updateBytes_signature,       F_S) \
   do_name(     updateBytes_C_name,                               "updateBytes")                                        \
  do_intrinsic(_updateDirectByteBufferCRC32C, java_util_zip_CRC32C, updateDirectByteBuffer_C_name, updateByteBuffer_signature, F_S) \
   do_name(    updateDirectByteBuffer_C_name,                     "updateDirectByteBuffer")                             \
                                                                                                                        \
   /* support for java.util.zip.Adler32 */                                                                              \
  do_class(java_util_zip_Adler32,        "java/util/zip/Adler32")                                                       \
  do_intrinsic(_updateBytesAdler32,       java_util_zip_Adler32,  updateBytes_C_name,  updateBytes_signature,  F_SN)    \
  do_intrinsic(_updateByteBufferAdler32,  java_util_zip_Adler32,  updateByteBuffer_A_name,  updateByteBuffer_signature,  F_SN) \
   do_name(     updateByteBuffer_A_name,                          "updateByteBuffer")                                   \
                                                                                                                        \
  /* jdk/internal/vm/Continuation */                                                                                    \
  do_class(jdk_internal_vm_Continuation, "jdk/internal/vm/Continuation")                                                \
  do_intrinsic(_Continuation_enter,        jdk_internal_vm_Continuation, enter_name,        continuationEnter_signature, F_S) \
   do_signature(continuationEnter_signature,                      "(Ljdk/internal/vm/Continuation;Z)V")                 \
  do_intrinsic(_Continuation_enterSpecial, jdk_internal_vm_Continuation, enterSpecial_name, continuationEnterSpecial_signature, F_SN) \
   do_signature(continuationEnterSpecial_signature,               "(Ljdk/internal/vm/Continuation;ZZ)V")                \
  do_signature(continuationGetStacks_signature,                   "(III)V")                                             \
  do_alias(continuationOnPinned_signature,      int_void_signature)                                                     \
  do_intrinsic(_Continuation_doYield,      jdk_internal_vm_Continuation, doYield_name,      continuationDoYield_signature, F_SN) \
   do_alias(    continuationDoYield_signature,     void_int_signature)                                                  \
  do_intrinsic(_Continuation_pin,          jdk_internal_vm_Continuation, pin_name, void_method_signature, F_SN)         \
  do_intrinsic(_Continuation_unpin,        jdk_internal_vm_Continuation, unpin_name, void_method_signature, F_SN)       \
                                                                                                                        \
  /* java/lang/VirtualThread */                                                                                         \
  do_intrinsic(_vthreadEndFirstTransition, java_lang_VirtualThread, endFirstTransition_name, void_method_signature, F_RN) \
  do_intrinsic(_vthreadStartFinalTransition, java_lang_VirtualThread, startFinalTransition_name, void_method_signature, F_RN) \
  do_intrinsic(_vthreadStartTransition, java_lang_VirtualThread, startTransition_name, bool_void_signature, F_RN)       \
  do_intrinsic(_vthreadEndTransition, java_lang_VirtualThread, endTransition_name, bool_void_signature, F_RN)           \
  do_intrinsic(_notifyJvmtiVThreadDisableSuspend, java_lang_VirtualThread, notifyJvmtiDisableSuspend_name, bool_void_signature, F_SN) \
                                                                                                                        \
  /* support for UnsafeConstants */                                                                                     \
  do_class(jdk_internal_misc_UnsafeConstants,      "jdk/internal/misc/UnsafeConstants")                                 \
                                                                                                                        \
  /* support for Unsafe */                                                                                              \
  do_class(jdk_internal_misc_Unsafe,               "jdk/internal/misc/Unsafe")                                          \
  do_class(sun_misc_Unsafe,                        "sun/misc/Unsafe")                                                   \
  do_class(jdk_internal_misc_ScopedMemoryAccess,   "jdk/internal/misc/ScopedMemoryAccess")                              \
                                                                                                                        \
  do_intrinsic(_writeback0,               jdk_internal_misc_Unsafe,     writeback0_name, long_void_signature , F_RN)             \
   do_name(     writeback0_name,                                        "writeback0")                                            \
  do_intrinsic(_writebackPreSync0,        jdk_internal_misc_Unsafe,     writebackPreSync0_name, void_method_signature , F_RN)    \
   do_name(     writebackPreSync0_name,                                 "writebackPreSync0")                                     \
  do_intrinsic(_writebackPostSync0,       jdk_internal_misc_Unsafe,    writebackPostSync0_name, void_method_signature , F_RN)    \
   do_name(     writebackPostSync0_name,                                "writebackPostSync0")                                    \
  do_intrinsic(_allocateInstance,         jdk_internal_misc_Unsafe,     allocateInstance_name, allocateInstance_signature, F_RN) \
   do_name(     allocateInstance_name,                                  "allocateInstance")                                      \
   do_signature(allocateInstance_signature,                             "(Ljava/lang/Class;)Ljava/lang/Object;")                 \
  do_intrinsic(_allocateUninitializedArray, jdk_internal_misc_Unsafe,   allocateUninitializedArray_name, newArray_signature,  F_R) \
   do_name(     allocateUninitializedArray_name,                        "allocateUninitializedArray0")                           \
  do_intrinsic(_copyMemory,               jdk_internal_misc_Unsafe,     copyMemory_name, copyMemory_signature,         F_RN)     \
   do_name(     copyMemory_name,                                        "copyMemory0")                                           \
   do_signature(copyMemory_signature,                                   "(Ljava/lang/Object;JLjava/lang/Object;JJ)V")            \
  do_intrinsic(_setMemory,                jdk_internal_misc_Unsafe,     setMemory_name,  setMemory_signature,          F_RN)     \
   do_name(     setMemory_name,                                         "setMemory0")                                            \
   do_signature(setMemory_signature,                                    "(Ljava/lang/Object;JJB)V")                              \
  do_intrinsic(_loadFence,                jdk_internal_misc_Unsafe,     loadFence_name, loadFence_signature,           F_R)      \
   do_name(     loadFence_name,                                         "loadFence")                                             \
   do_alias(    loadFence_signature,                                    void_method_signature)                                   \
  do_intrinsic(_storeFence,               jdk_internal_misc_Unsafe,     storeFence_name, storeFence_signature,         F_R)      \
   do_name(     storeFence_name,                                        "storeFence")                                            \
   do_alias(    storeFence_signature,                                   void_method_signature)                                   \
  do_intrinsic(_storeStoreFence,          jdk_internal_misc_Unsafe,     storeStoreFence_name, storeStoreFence_signature, F_R)    \
   do_name(     storeStoreFence_name,                                   "storeStoreFence")                                       \
   do_alias(    storeStoreFence_signature,                              void_method_signature)                                   \
  do_intrinsic(_fullFence,                jdk_internal_misc_Unsafe,     fullFence_name, fullFence_signature,           F_RN)     \
   do_name(     fullFence_name,                                         "fullFence")                                             \
   do_alias(    fullFence_signature,                                    void_method_signature)                                   \
                                                                                                                        \
  /* Custom branch frequencies profiling support for JSR292 */                                                          \
  do_class(java_lang_invoke_MethodHandleImpl,               "java/lang/invoke/MethodHandleImpl")                        \
  do_intrinsic(_profileBoolean, java_lang_invoke_MethodHandleImpl, profileBoolean_name, profileBoolean_signature, F_S)  \
   do_name(     profileBoolean_name,                             "profileBoolean")                                      \
   do_signature(profileBoolean_signature,                        "(Z[I)Z")                                              \
  do_intrinsic(_isCompileConstant, java_lang_invoke_MethodHandleImpl, isCompileConstant_name, isCompileConstant_signature, F_S) \
   do_name(     isCompileConstant_name,                          "isCompileConstant")                                   \
   do_alias(    isCompileConstant_signature,                      object_boolean_signature)                             \
                                                                                                                        \
  do_intrinsic(_getObjectSize,   sun_instrument_InstrumentationImpl, getObjectSize_name, getObjectSize_signature, F_RN) \
   do_name(     getObjectSize_name,                               "getObjectSize0")                                     \
   do_alias(    getObjectSize_signature,                          long_object_long_signature)                           \
                                                                                                                        \
  /* special marker for blackholed methods: */                                                                          \
  do_intrinsic(_blackhole,                java_lang_Object,       blackhole_name, star_name, F_S)                       \
                                                                                                                        \
  /* unsafe memory references */                                                                                        \
  do_intrinsic(_getPrimitiveBitsMO, jdk_internal_misc_Unsafe, getPrimitiveBitsMO_name, getPrimitiveBitsMO_signature, F_PI) \
   do_name(     getPrimitiveBitsMO_name,                                "getPrimitiveBitsMO")                           \
   do_signature(getPrimitiveBitsMO_signature,                           "(BBLjava/lang/Object;J)J")                     \
  do_intrinsic(_putPrimitiveBitsMO, jdk_internal_misc_Unsafe, putPrimitiveBitsMO_name, putPrimitiveBitsMO_signature, F_PI) \
   do_name(     putPrimitiveBitsMO_name,                                "putPrimitiveBitsMO")                           \
   do_signature(putPrimitiveBitsMO_signature,                           "(BBLjava/lang/Object;JJ)V")                    \
  do_intrinsic(_getReferenceMO,     jdk_internal_misc_Unsafe, getReferenceMO_name, getReferenceMO_signature, F_PI)      \
   do_name(     getReferenceMO_name,                                    "getReferenceMO")                               \
   do_signature(getReferenceMO_signature,                               "(BLjava/lang/Object;J)Ljava/lang/Object;")     \
  do_intrinsic(_putReferenceMO,     jdk_internal_misc_Unsafe, putReferenceMO_name, putReferenceMO_signature, F_PI)      \
   do_name(     putReferenceMO_name,                                    "putReferenceMO")                               \
   do_signature(putReferenceMO_signature,                               "(BLjava/lang/Object;JLjava/lang/Object;)V")    \
                                                                                                                        \
  do_intrinsic(_compareAndSetPrimitiveBitsMO, jdk_internal_misc_Unsafe, compareAndSetPrimitiveBitsMO_name, compareAndSetPrimitiveBitsMO_signature, F_PI) \
   do_name(     compareAndSetPrimitiveBitsMO_name,                      "compareAndSetPrimitiveBitsMO")                 \
   do_signature(compareAndSetPrimitiveBitsMO_signature,                 "(BBLjava/lang/Object;JJJ)Z")                   \
  do_intrinsic(_compareAndExchangePrimitiveBitsMO, jdk_internal_misc_Unsafe, compareAndExchangePrimitiveBitsMO_name, compareAndExchangePrimitiveBitsMO_signature, F_PI) \
   do_name(     compareAndExchangePrimitiveBitsMO_name,                 "compareAndExchangePrimitiveBitsMO")            \
   do_signature(compareAndExchangePrimitiveBitsMO_signature,            "(BBLjava/lang/Object;JJJ)J")                   \
  do_intrinsic(_compareAndSetReferenceMO,     jdk_internal_misc_Unsafe, compareAndSetReferenceMO_name, compareAndSetReferenceMO_signature, F_PI) \
   do_name(     compareAndSetReferenceMO_name,                          "compareAndSetReferenceMO")                     \
   do_signature(compareAndSetReferenceMO_signature,      "(BLjava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z") \
  do_intrinsic(_compareAndExchangeReferenceMO, jdk_internal_misc_Unsafe, compareAndExchangeReferenceMO_name, compareAndExchangeReferenceMO_signature, F_PI) \
   do_name(     compareAndExchangeReferenceMO_name,                     "compareAndExchangeReferenceMO")                \
   do_signature(compareAndExchangeReferenceMO_signature,  "(BLjava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") \
  do_intrinsic(_getAndOperatePrimitiveBitsMO, jdk_internal_misc_Unsafe, getAndOperatePrimitiveBitsMO_name, getAndOperatePrimitiveBitsMO_signature, F_PI) \
   do_name(     getAndOperatePrimitiveBitsMO_name,                      "getAndOperatePrimitiveBitsMO")                 \
   do_signature(getAndOperatePrimitiveBitsMO_signature,                 "(BBBLjava/lang/Object;JJ)J" )                  \
  do_intrinsic(_getAndSetReferenceMO, jdk_internal_misc_Unsafe, getAndSetReferenceMO_name, getAndSetReferenceMO_signature, F_PI) \
   do_name(     getAndSetReferenceMO_name,                               "getAndSetReferenceMO")                        \
   do_signature(getAndSetReferenceMO_signature,           "(BLjava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;") \
                                                                                                                        \
  /* wrappers for polymorphic unsafe intrinsics (there are a lot of them...) */                                         \
  do_signature(getReference_signature,    "(Ljava/lang/Object;J)Ljava/lang/Object;")                                    \
  do_signature(putReference_signature,    "(Ljava/lang/Object;JLjava/lang/Object;)V")                                   \
  do_signature(getBoolean_signature,      "(Ljava/lang/Object;J)Z")                                                     \
  do_signature(putBoolean_signature,      "(Ljava/lang/Object;JZ)V")                                                    \
  do_signature(getByte_signature,         "(Ljava/lang/Object;J)B")                                                     \
  do_signature(putByte_signature,         "(Ljava/lang/Object;JB)V")                                                    \
  do_signature(getShort_signature,        "(Ljava/lang/Object;J)S")                                                     \
  do_signature(putShort_signature,        "(Ljava/lang/Object;JS)V")                                                    \
  do_signature(getChar_signature,         "(Ljava/lang/Object;J)C")                                                     \
  do_signature(putChar_signature,         "(Ljava/lang/Object;JC)V")                                                    \
  do_signature(getInt_signature,          "(Ljava/lang/Object;J)I")                                                     \
  do_signature(putInt_signature,          "(Ljava/lang/Object;JI)V")                                                    \
  do_signature(getLong_signature,         "(Ljava/lang/Object;J)J")                                                     \
  do_signature(putLong_signature,         "(Ljava/lang/Object;JJ)V")                                                    \
  do_signature(getFloat_signature,        "(Ljava/lang/Object;J)F")                                                     \
  do_signature(putFloat_signature,        "(Ljava/lang/Object;JF)V")                                                    \
  do_signature(getDouble_signature,       "(Ljava/lang/Object;J)D")                                                     \
  do_signature(putDouble_signature,       "(Ljava/lang/Object;JD)V")                                                    \
                                                                                                                        \
  do_name(getReference_name,"getReference")     do_name(putReference_name,"putReference")                               \
  do_name(getBoolean_name,"getBoolean")         do_name(putBoolean_name,"putBoolean")                                   \
  do_name(getByte_name,"getByte")               do_name(putByte_name,"putByte")                                         \
  do_name(getShort_name,"getShort")             do_name(putShort_name,"putShort")                                       \
  do_name(getChar_name,"getChar")               do_name(putChar_name,"putChar")                                         \
  do_name(getInt_name,"getInt")                 do_name(putInt_name,"putInt")                                           \
  do_name(getLong_name,"getLong")               do_name(putLong_name,"putLong")                                         \
  do_name(getFloat_name,"getFloat")             do_name(putFloat_name,"putFloat")                                       \
  do_name(getDouble_name,"getDouble")           do_name(putDouble_name,"putDouble")                                     \
                                                                                                                        \
                                                                                                                        \
  do_name(getShortUnaligned_name,"getShortUnaligned")     do_name(putShortUnaligned_name,"putShortUnaligned")           \
  do_name(getCharUnaligned_name,"getCharUnaligned")       do_name(putCharUnaligned_name,"putCharUnaligned")             \
  do_name(getIntUnaligned_name,"getIntUnaligned")         do_name(putIntUnaligned_name,"putIntUnaligned")               \
  do_name(getLongUnaligned_name,"getLongUnaligned")       do_name(putLongUnaligned_name,"putLongUnaligned")             \
                                                                                                                        \
                                                                                                                        \
  do_signature(compareAndSetReference_signature,      "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z")        \
  do_signature(compareAndExchangeReference_signature, "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;") \
  do_signature(compareAndSetLong_signature,        "(Ljava/lang/Object;JJJ)Z")                                          \
  do_signature(compareAndExchangeLong_signature,   "(Ljava/lang/Object;JJJ)J")                                          \
  do_signature(compareAndSetInt_signature,         "(Ljava/lang/Object;JII)Z")                                          \
  do_signature(compareAndExchangeInt_signature,    "(Ljava/lang/Object;JII)I")                                          \
  do_signature(compareAndSetByte_signature,        "(Ljava/lang/Object;JBB)Z")                                          \
  do_signature(compareAndExchangeByte_signature,   "(Ljava/lang/Object;JBB)B")                                          \
  do_signature(compareAndSetShort_signature,       "(Ljava/lang/Object;JSS)Z")                                          \
  do_signature(compareAndExchangeShort_signature,  "(Ljava/lang/Object;JSS)S")                                          \
                                                                                                                        \
  do_name(compareAndSetReference_name,              "compareAndSetReference")                                           \
  do_name(compareAndExchangeReference_name,         "compareAndExchangeReference")                                      \
  do_name(compareAndSetLong_name,                   "compareAndSetLong")                                                \
  do_name(compareAndExchangeLong_name,              "compareAndExchangeLong")                                           \
  do_name(compareAndSetInt_name,                    "compareAndSetInt")                                                 \
  do_name(compareAndExchangeInt_name,               "compareAndExchangeInt")                                            \
  do_name(compareAndSetByte_name,                   "compareAndSetByte")                                                \
  do_name(compareAndExchangeByte_name,              "compareAndExchangeByte")                                           \
  do_name(compareAndSetShort_name,                  "compareAndSetShort")                                               \
  do_name(compareAndExchangeShort_name,             "compareAndExchangeShort")                                          \
                                                                                                                        \
  do_name(weakCompareAndSetReference_name,          "weakCompareAndSetReference")                                       \
  do_name(weakCompareAndSetLong_name,               "weakCompareAndSetLong")                                            \
  do_name(weakCompareAndSetInt_name,                "weakCompareAndSetInt")                                             \
                                                                                                                        \
                                                                                                                                                             \
                           \
   do_name(     getAndAddInt_name,                                      "getAndAddInt")                                       \
   do_signature(getAndAddInt_signature,                                 "(Ljava/lang/Object;JI)I" )                           \
   do_name(     getAndAddLong_name,                                     "getAndAddLong")                                      \
   do_signature(getAndAddLong_signature,                                "(Ljava/lang/Object;JJ)J" )                           \
   do_name(     getAndAddByte_name,                                     "getAndAddByte")                                      \
   do_signature(getAndAddByte_signature,                                "(Ljava/lang/Object;JB)B" )                           \
   do_name(     getAndAddShort_name,                                    "getAndAddShort")                                     \
   do_signature(getAndAddShort_signature,                               "(Ljava/lang/Object;JS)S" )                           \
   do_name(     getAndSetInt_name,                                      "getAndSetInt")                                       \
   do_alias(    getAndSetInt_signature,                                 /*"(Ljava/lang/Object;JI)I"*/ getAndAddInt_signature) \
   do_name(     getAndSetLong_name,                                     "getAndSetLong")                                      \
   do_alias(    getAndSetLong_signature,                                /*"(Ljava/lang/Object;JJ)J"*/ getAndAddLong_signature)\
   do_name(     getAndSetByte_name,                                     "getAndSetByte")                                      \
   do_alias(    getAndSetByte_signature,                                /*"(Ljava/lang/Object;JB)B"*/ getAndAddByte_signature)\
   do_name(     getAndSetShort_name,                                    "getAndSetShort")                                             \
   do_alias(    getAndSetShort_signature,                               /*"(Ljava/lang/Object;JS)S"*/ getAndAddShort_signature)       \
   do_name(     getAndSetReference_name,                                "getAndSetReference")                                         \
   do_signature(getAndSetReference_signature,                           "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;" ) \
  /* end of wrappers for polymorphic unsafe intrinsics */                                                                    \
                                                                                                                             \
  /* Float16Math API intrinsification support */                                                                             \
  /* Float16 signatures */                                                                                                   \
  do_signature(float16_unary_math_op_sig, "(Ljava/lang/Class;"                                                               \
                                           "Ljava/lang/Object;"                                                              \
                                           "Ljava/util/function/UnaryOperator;)"                                             \
                                           "Ljava/lang/Object;")                                                             \
  do_signature(float16_ternary_math_op_sig, "(Ljava/lang/Class;"                                                             \
                                             "Ljava/lang/Object;"                                                            \
                                             "Ljava/lang/Object;"                                                            \
                                             "Ljava/lang/Object;"                                                            \
                                             "Ljdk/internal/vm/vector/Float16Math$TernaryOperator;)"                         \
                                             "Ljava/lang/Object;")                                                           \
  do_intrinsic(_sqrt_float16, jdk_internal_vm_vector_Float16Math, sqrt_name, float16_unary_math_op_sig, F_S)                 \
  do_intrinsic(_fma_float16, jdk_internal_vm_vector_Float16Math, fma_name, float16_ternary_math_op_sig, F_S)                 \
                                                                                                                                               \
  /* Vector API intrinsification support */                                                                                                    \
                                                                                                                                               \
  do_intrinsic(_VectorUnaryOp, jdk_internal_vm_vector_VectorSupport, vector_unary_op_name, vector_unary_op_sig, F_S)                           \
   do_signature(vector_unary_op_sig, "(I"                                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "I"                                                                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                          \
                                      "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$UnaryOperation;)"                                                 \
                                      "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                         \
   do_name(vector_unary_op_name,     "unaryOp")                                                                                                \
                                                                                                                                               \
  do_intrinsic(_VectorBinaryOp, jdk_internal_vm_vector_VectorSupport, vector_binary_op_name, vector_binary_op_sig, F_S)                        \
   do_signature(vector_binary_op_sig, "(I"                                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "I"                                                                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                                  \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                                  \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$BinaryOperation;)"                                               \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                                 \
   do_name(vector_binary_op_name,     "binaryOp")                                                                                              \
                                                                                                                                               \
  do_intrinsic(_VectorUnaryLibOp, jdk_internal_vm_vector_VectorSupport, vector_unary_lib_op_name, vector_unary_lib_op_sig, F_S)                \
   do_signature(vector_unary_lib_op_sig,"(J"                                                                                                   \
                                         "Ljava/lang/Class;"                                                                                   \
                                         "Ljava/lang/Class;"                                                                                   \
                                         "I"                                                                                                   \
                                         "Ljava/lang/String;"                                                                                  \
                                         "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                       \
                                         "Ljdk/internal/vm/vector/VectorSupport$UnaryOperation;)"                                              \
                                         "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                      \
   do_name(vector_unary_lib_op_name, "libraryUnaryOp")                                                                                         \
                                                                                                                                               \
  do_intrinsic(_VectorBinaryLibOp, jdk_internal_vm_vector_VectorSupport, vector_binary_lib_op_name, vector_binary_lib_op_sig, F_S)             \
   do_signature(vector_binary_lib_op_sig,"(J"                                                                                                  \
                                          "Ljava/lang/Class;"                                                                                  \
                                          "Ljava/lang/Class;"                                                                                  \
                                          "I"                                                                                                  \
                                          "Ljava/lang/String;"                                                                                 \
                                          "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                               \
                                          "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                               \
                                          "Ljdk/internal/vm/vector/VectorSupport$BinaryOperation;)"                                            \
                                          "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                              \
   do_name(vector_binary_lib_op_name, "libraryBinaryOp")                                                                                       \
                                                                                                                                               \
  do_intrinsic(_VectorTernaryOp, jdk_internal_vm_vector_VectorSupport, vector_ternary_op_name, vector_ternary_op_sig, F_S)                     \
   do_signature(vector_ternary_op_sig, "(I"                                                                                                    \
                                        "Ljava/lang/Class;"                                                                                    \
                                        "Ljava/lang/Class;"                                                                                    \
                                        "Ljava/lang/Class;"                                                                                    \
                                        "I"                                                                                                    \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                        \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                        \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                        \
                                        "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                    \
                                        "Ljdk/internal/vm/vector/VectorSupport$TernaryOperation;)"                                             \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                       \
   do_name(vector_ternary_op_name,     "ternaryOp")                                                                                            \
                                                                                                                                               \
  do_intrinsic(_VectorSelectFromTwoVectorOp, jdk_internal_vm_vector_VectorSupport, vector_select_from_op_name, vector_select_from_op_sig, F_S) \
   do_signature(vector_select_from_op_sig, "(Ljava/lang/Class;"                                                                                \
                                            "Ljava/lang/Class;"                                                                                \
                                            "I"                                                                                                \
                                            "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                    \
                                            "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                    \
                                            "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                    \
                                            "Ljdk/internal/vm/vector/VectorSupport$SelectFromTwoVector;)"                                      \
                                            "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                   \
   do_name(vector_select_from_op_name,     "selectFromTwoVectorOp")                                                                            \
                                                                                                                                               \
  do_intrinsic(_VectorFromBitsCoerced, jdk_internal_vm_vector_VectorSupport, vector_frombits_coerced_name, vector_frombits_coerced_sig, F_S)   \
   do_signature(vector_frombits_coerced_sig, "(Ljava/lang/Class;"                                                                              \
                                               "Ljava/lang/Class;"                                                                             \
                                               "I"                                                                                             \
                                               "J"                                                                                             \
                                               "I"                                                                                             \
                                               "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                          \
                                               "Ljdk/internal/vm/vector/VectorSupport$FromBitsCoercedOperation;)"                              \
                                               "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                         \
   do_name(vector_frombits_coerced_name, "fromBitsCoerced")                                                                                    \
                                                                                                                                               \
  do_intrinsic(_VectorLoadOp, jdk_internal_vm_vector_VectorSupport, vector_load_op_name, vector_load_op_sig, F_S)                              \
   do_signature(vector_load_op_sig, "(Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljava/lang/Object;"                                                                                      \
                                     "J"                                                                                                       \
                                     "Z"                                                                                                       \
                                     "Ljava/lang/Object;"                                                                                      \
                                     "J"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                                    \
                                     "Ljdk/internal/vm/vector/VectorSupport$LoadOperation;)"                                                   \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                                   \
   do_name(vector_load_op_name,     "load")                                                                                                    \
                                                                                                                                               \
  do_intrinsic(_VectorLoadMaskedOp, jdk_internal_vm_vector_VectorSupport, vector_load_masked_op_name, vector_load_masked_op_sig, F_S)          \
   do_signature(vector_load_masked_op_sig, "(Ljava/lang/Class;"                                                                                \
                                            "Ljava/lang/Class;"                                                                                \
                                            "Ljava/lang/Class;"                                                                                \
                                            "I"                                                                                                \
                                            "Ljava/lang/Object;"                                                                               \
                                            "J"                                                                                                \
                                            "Z"                                                                                                \
                                            "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                \
                                            "I"                                                                                                \
                                            "Ljava/lang/Object;"                                                                               \
                                            "J"                                                                                                \
                                            "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                             \
                                            "Ljdk/internal/vm/vector/VectorSupport$LoadVectorMaskedOperation;)"                                \
                                            "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                   \
   do_name(vector_load_masked_op_name,     "loadMasked")                                                                                       \
                                                                                                                                               \
  do_intrinsic(_VectorStoreOp, jdk_internal_vm_vector_VectorSupport, vector_store_op_name, vector_store_op_sig, F_S)                           \
   do_signature(vector_store_op_sig, "(Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "I"                                                                                                      \
                                      "Ljava/lang/Object;"                                                                                     \
                                      "J"                                                                                                      \
                                      "Z"                                                                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                                   \
                                      "Ljava/lang/Object;"                                                                                     \
                                      "J"                                                                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$StoreVectorOperation;)"                                           \
                                      "V")                                                                                                     \
   do_name(vector_store_op_name,     "store")                                                                                                  \
                                                                                                                                               \
  do_intrinsic(_VectorStoreMaskedOp, jdk_internal_vm_vector_VectorSupport, vector_store_masked_op_name, vector_store_masked_op_sig, F_S)       \
   do_signature(vector_store_masked_op_sig, "(Ljava/lang/Class;"                                                                               \
                                             "Ljava/lang/Class;"                                                                               \
                                             "Ljava/lang/Class;"                                                                               \
                                             "I"                                                                                               \
                                             "Ljava/lang/Object;"                                                                              \
                                             "J"                                                                                               \
                                             "Z"                                                                                               \
                                             "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                   \
                                             "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                               \
                                             "Ljava/lang/Object;"                                                                              \
                                             "J"                                                                                               \
                                             "Ljdk/internal/vm/vector/VectorSupport$StoreVectorMaskedOperation;)"                              \
                                             "V")                                                                                              \
   do_name(vector_store_masked_op_name,     "storeMasked")                                                                                     \
                                                                                                                                               \
  do_intrinsic(_VectorReductionCoerced, jdk_internal_vm_vector_VectorSupport, vector_reduction_coerced_name, vector_reduction_coerced_sig, F_S)\
   do_signature(vector_reduction_coerced_sig, "(I"                                                                                             \
                                               "Ljava/lang/Class;"                                                                             \
                                               "Ljava/lang/Class;"                                                                             \
                                               "Ljava/lang/Class;"                                                                             \
                                               "I"                                                                                             \
                                               "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                 \
                                               "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                             \
                                               "Ljdk/internal/vm/vector/VectorSupport$ReductionOperation;)"                                    \
                                               "J")                                                                                            \
   do_name(vector_reduction_coerced_name, "reductionCoerced")                                                                                  \
                                                                                                                                               \
  do_intrinsic(_VectorTest, jdk_internal_vm_vector_VectorSupport, vector_test_name, vector_test_sig, F_S)                                      \
   do_signature(vector_test_sig, "(I"                                                                                                          \
                                  "Ljava/lang/Class;"                                                                                          \
                                  "Ljava/lang/Class;"                                                                                          \
                                  "I"                                                                                                          \
                                  "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                          \
                                  "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                          \
                                  "Ljava/util/function/BiFunction;)"                                                                           \
                                  "Z")                                                                                                         \
   do_name(vector_test_name, "test")                                                                                                           \
                                                                                                                                               \
  do_intrinsic(_VectorBlend, jdk_internal_vm_vector_VectorSupport, vector_blend_name, vector_blend_sig, F_S)                                   \
   do_signature(vector_blend_sig, "(Ljava/lang/Class;"                                                                                         \
                                   "Ljava/lang/Class;"                                                                                         \
                                   "Ljava/lang/Class;"                                                                                         \
                                   "I"                                                                                                         \
                                   "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                             \
                                   "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                             \
                                   "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                         \
                                   "Ljdk/internal/vm/vector/VectorSupport$VectorBlendOp;)"                                                     \
                                   "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                            \
   do_name(vector_blend_name, "blend")                                                                                                         \
                                                                                                                                               \
  do_intrinsic(_VectorCompare, jdk_internal_vm_vector_VectorSupport, vector_compare_name, vector_compare_sig, F_S)                             \
   do_signature(vector_compare_sig, "(I"                                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;Ljava/lang/Class;"                                                                      \
                                     "I"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorCompareOp;)"                                                 \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorMask;")                                                      \
   do_name(vector_compare_name, "compare")                                                                                                     \
                                                                                                                                               \
  do_intrinsic(_VectorRearrange, jdk_internal_vm_vector_VectorSupport, vector_rearrange_name, vector_rearrange_sig, F_S)                       \
   do_signature(vector_rearrange_sig, "(Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "I"                                                                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                         \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorShuffle;"                                                  \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorRearrangeOp;)"                                             \
                                       "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                        \
   do_name(vector_rearrange_name, "rearrangeOp")                                                                                               \
                                                                                                                                               \
  do_intrinsic(_VectorSelectFrom, jdk_internal_vm_vector_VectorSupport, vector_select_from_name, vector_select_from_sig, F_S)                  \
   do_signature(vector_select_from_sig, "(Ljava/lang/Class;"                                                                                   \
                                        "Ljava/lang/Class;"                                                                                     \
                                        "Ljava/lang/Class;"                                                                                     \
                                        "I"                                                                                                     \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                         \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                         \
                                        "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                     \
                                        "Ljdk/internal/vm/vector/VectorSupport$VectorSelectFromOp;)"                                            \
                                        "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                        \
   do_name(vector_select_from_name, "selectFromOp")                                                                                              \
                                                                                                                                               \
  do_intrinsic(_VectorExtract, jdk_internal_vm_vector_VectorSupport, vector_extract_name, vector_extract_sig, F_S)                             \
   do_signature(vector_extract_sig, "(Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                                    \
                                     "I"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$VecExtractOp;)"                                                    \
                                     "J")                                                                                                      \
   do_name(vector_extract_name, "extract")                                                                                                     \
                                                                                                                                               \
 do_intrinsic(_VectorInsert, jdk_internal_vm_vector_VectorSupport, vector_insert_name, vector_insert_sig, F_S)                                 \
   do_signature(vector_insert_sig, "(Ljava/lang/Class;"                                                                                        \
                                    "Ljava/lang/Class;"                                                                                        \
                                    "I"                                                                                                        \
                                    "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                            \
                                    "IJ"                                                                                                       \
                                    "Ljdk/internal/vm/vector/VectorSupport$VecInsertOp;)"                                                      \
                                    "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                           \
   do_name(vector_insert_name, "insert")                                                                                                       \
                                                                                                                                               \
  do_intrinsic(_VectorBroadcastInt, jdk_internal_vm_vector_VectorSupport, vector_broadcast_int_name, vector_broadcast_int_sig, F_S)            \
   do_signature(vector_broadcast_int_sig, "(I"                                                                                                 \
                                           "Ljava/lang/Class;"                                                                                 \
                                           "Ljava/lang/Class;"                                                                                 \
                                           "Ljava/lang/Class;"                                                                                 \
                                           "I"                                                                                                 \
                                           "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                     \
                                           "I"                                                                                                 \
                                           "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                 \
                                           "Ljdk/internal/vm/vector/VectorSupport$VectorBroadcastIntOp;)"                                      \
                                           "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                    \
   do_name(vector_broadcast_int_name, "broadcastInt")                                                                                          \
                                                                                                                                               \
  do_intrinsic(_VectorConvert, jdk_internal_vm_vector_VectorSupport, vector_convert_name, vector_convert_sig, F_S)                             \
   do_signature(vector_convert_sig, "(I"                                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;"                                                    \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                                    \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorConvertOp;)"                                                 \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                                   \
   do_name(vector_convert_name, "convert")                                                                                                     \
                                                                                                                                               \
   do_intrinsic(_VectorGatherOp, jdk_internal_vm_vector_VectorSupport, vector_gather_name, vector_gather_sig, F_S)                             \
    do_signature(vector_gather_sig, "(Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljava/lang/Class;"                                                                                       \
                                     "I"                                                                                                       \
                                     "Ljava/lang/Object;"                                                                                      \
                                     "J"                                                                                                       \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                           \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                       \
                                     "Ljava/lang/Object;"                                                                                      \
                                     "I[II"                                                                                                    \
                                     "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                                    \
                                     "Ljdk/internal/vm/vector/VectorSupport$LoadVectorOperationWithMap;)"                                      \
                                     "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                          \
    do_name(vector_gather_name, "loadWithMap")                                                                                                 \
                                                                                                                                               \
   do_intrinsic(_VectorScatterOp, jdk_internal_vm_vector_VectorSupport, vector_scatter_name, vector_scatter_sig, F_S)                          \
    do_signature(vector_scatter_sig, "(Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "I"                                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "I"                                                                                                      \
                                      "Ljava/lang/Object;"                                                                                     \
                                      "J"                                                                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                          \
                                      "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                          \
                                      "Ljdk/internal/vm/vector/VectorSupport$VectorMask;Ljava/lang/Object;"                                    \
                                      "I[II"                                                                                                   \
                                      "Ljdk/internal/vm/vector/VectorSupport$StoreVectorOperationWithMap;)"                                    \
                                      "V")                                                                                                     \
    do_name(vector_scatter_name, "storeWithMap")                                                                                               \
                                                                                                                                               \
  do_intrinsic(_VectorRebox, jdk_internal_vm_vector_VectorSupport, vector_rebox_name, vector_rebox_sig, F_S)                                   \
    do_signature(vector_rebox_sig, "(Ljdk/internal/vm/vector/VectorSupport$VectorPayload;)"                                                    \
                                    "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                                    \
   do_name(vector_rebox_name, "maybeRebox")                                                                                                    \
                                                                                                                                               \
  do_intrinsic(_VectorMaskOp, jdk_internal_vm_vector_VectorSupport, vector_mask_oper_name, vector_mask_oper_sig, F_S)                          \
    do_signature(vector_mask_oper_sig, "(I"                                                                                                    \
                                        "Ljava/lang/Class;"                                                                                    \
                                        "Ljava/lang/Class;"                                                                                    \
                                        "I"                                                                                                    \
                                        "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                    \
                                        "Ljdk/internal/vm/vector/VectorSupport$VectorMaskOp;)"                                                 \
                                        "J")                                                                                                   \
    do_name(vector_mask_oper_name, "maskReductionCoerced")                                                                                     \
                                                                                                                                               \
  do_intrinsic(_VectorCompressExpand, jdk_internal_vm_vector_VectorSupport, vector_compress_expand_op_name, vector_compress_expand_op_sig, F_S)\
   do_signature(vector_compress_expand_op_sig, "(I"                                                                                            \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "Ljava/lang/Class;"                                                                                      \
                                      "I"                                                                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                          \
                                      "Ljdk/internal/vm/vector/VectorSupport$VectorMask;"                                                      \
                                      "Ljdk/internal/vm/vector/VectorSupport$CompressExpandOperation;)"                                        \
                                      "Ljdk/internal/vm/vector/VectorSupport$VectorPayload;")                                                  \
   do_name(vector_compress_expand_op_name,     "compressExpandOp")                                                                             \
                                                                                                                                               \
  do_intrinsic(_IndexVector, jdk_internal_vm_vector_VectorSupport, index_vector_op_name, index_vector_op_sig, F_S)                             \
    do_signature(index_vector_op_sig, "(Ljava/lang/Class;"                                                                                     \
                                       "Ljava/lang/Class;"                                                                                     \
                                       "I"                                                                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$Vector;"                                                         \
                                       "I"                                                                                                     \
                                       "Ljdk/internal/vm/vector/VectorSupport$VectorSpecies;"                                                  \
                                       "Ljdk/internal/vm/vector/VectorSupport$IndexOperation;)"                                                \
                                       "Ljdk/internal/vm/vector/VectorSupport$Vector;")                                                        \
    do_name(index_vector_op_name, "indexVector")                                                                                               \
                                                                                                                                               \
  do_intrinsic(_IndexPartiallyInUpperRange, jdk_internal_vm_vector_VectorSupport, index_partially_in_upper_range_name, index_partially_in_upper_range_sig, F_S)\
    do_signature(index_partially_in_upper_range_sig, "(Ljava/lang/Class;"                                                                                      \
                                                     "Ljava/lang/Class;"                                                                                       \
                                                     "I"                                                                                                       \
                                                     "J"                                                                                                       \
                                                     "J"                                                                                                       \
                                                     "Ljdk/internal/vm/vector/VectorSupport$IndexPartiallyInUpperRangeOperation;)"                             \
                                                     "Ljdk/internal/vm/vector/VectorSupport$VectorMask;")                                                      \
    do_name(index_partially_in_upper_range_name, "indexPartiallyInUpperRange")                                                                                 \
                                                                                                                               \
   /* (2) Bytecode intrinsics                                                                        */                        \
                                                                                                                               \
  do_intrinsic(_park,                     jdk_internal_misc_Unsafe,     park_name, park_signature,                     F_RN)   \
   do_name(     park_name,                                              "park")                                                \
   do_signature(park_signature,                                         "(ZJ)V")                                               \
  do_intrinsic(_unpark,                   jdk_internal_misc_Unsafe,     unpark_name, unpark_signature,                 F_RN)   \
   do_name(     unpark_name,                                            "unpark")                                              \
   do_alias(    unpark_signature,                                       /*(LObject;)V*/ object_void_signature)                 \
                                                                                                                               \
  do_intrinsic(_StringBuilder_void,   java_lang_StringBuilder, object_initializer_name, void_method_signature,     F_R)   \
  do_intrinsic(_StringBuilder_int,    java_lang_StringBuilder, object_initializer_name, int_void_signature,        F_R)   \
  do_intrinsic(_StringBuilder_String, java_lang_StringBuilder, object_initializer_name, string_void_signature,     F_R)   \
                                                                                                                          \
  do_intrinsic(_StringBuilder_append_char,   java_lang_StringBuilder, append_name, char_StringBuilder_signature,   F_R)   \
  do_intrinsic(_StringBuilder_append_int,    java_lang_StringBuilder, append_name, int_StringBuilder_signature,    F_R)   \
  do_intrinsic(_StringBuilder_append_String, java_lang_StringBuilder, append_name, String_StringBuilder_signature, F_R)   \
                                                                                                                          \
  do_intrinsic(_StringBuilder_toString, java_lang_StringBuilder, toString_name, void_string_signature,             F_R)   \
                                                                                                                          \
  do_intrinsic(_StringBuffer_void,   java_lang_StringBuffer, object_initializer_name, void_method_signature,       F_R)   \
  do_intrinsic(_StringBuffer_int,    java_lang_StringBuffer, object_initializer_name, int_void_signature,          F_R)   \
  do_intrinsic(_StringBuffer_String, java_lang_StringBuffer, object_initializer_name, string_void_signature,       F_R)   \
                                                                                                                          \
  do_intrinsic(_StringBuffer_append_char,   java_lang_StringBuffer, append_name, char_StringBuffer_signature,      F_Y)   \
  do_intrinsic(_StringBuffer_append_int,    java_lang_StringBuffer, append_name, int_StringBuffer_signature,       F_Y)   \
  do_intrinsic(_StringBuffer_append_String, java_lang_StringBuffer, append_name, String_StringBuffer_signature,    F_Y)   \
                                                                                                                          \
  do_intrinsic(_StringBuffer_toString,  java_lang_StringBuffer, toString_name, void_string_signature,              F_Y)   \
                                                                                                                          \
  do_intrinsic(_Integer_toString,      java_lang_Integer, toString_name, int_String_signature,                     F_S)   \
                                                                                                                          \
  do_intrinsic(_String_String, java_lang_String, object_initializer_name, string_void_signature,                   F_R)   \
                                                                                                                          \
  do_intrinsic(_Object_init,              java_lang_Object, object_initializer_name, void_method_signature,        F_R)   \
  /*    (symbol object_initializer_name defined above) */                                                                 \
                                                                                                                          \
  do_intrinsic(_invoke,                   java_lang_reflect_Method, invoke_name, object_object_array_object_signature, F_R) \
  /*   (symbols invoke_name and invoke_signature defined above) */                                                      \
  /* the polymorphic MH intrinsics must be in compact order, with _invokeGeneric first and _linkToInterface last */     \
  do_intrinsic(_invokeGeneric,            java_lang_invoke_MethodHandle, invoke_name,           star_name, F_RN)        \
  do_intrinsic(_invokeBasic,              java_lang_invoke_MethodHandle, invokeBasic_name,      star_name, F_RN)        \
  do_intrinsic(_linkToVirtual,            java_lang_invoke_MethodHandle, linkToVirtual_name,    star_name, F_SN)        \
  do_intrinsic(_linkToStatic,             java_lang_invoke_MethodHandle, linkToStatic_name,     star_name, F_SN)        \
  do_intrinsic(_linkToSpecial,            java_lang_invoke_MethodHandle, linkToSpecial_name,    star_name, F_SN)        \
  do_intrinsic(_linkToInterface,          java_lang_invoke_MethodHandle, linkToInterface_name,  star_name, F_SN)        \
  do_intrinsic(_linkToNative,             java_lang_invoke_MethodHandle, linkToNative_name,     star_name, F_SN)        \
  /* special marker for bytecode generated for the JVM from a LambdaForm: */                                            \
  do_intrinsic(_compiledLambdaForm,       java_lang_invoke_MethodHandle, compiledLambdaForm_name, star_name, F_RN)      \
                                                                                                                        \
  /* unboxing methods: */                                                                                               \
  do_intrinsic(_booleanValue,             java_lang_Boolean,      booleanValue_name, void_boolean_signature, F_R)       \
   do_name(     booleanValue_name,       "booleanValue")                                                                \
  do_intrinsic(_byteValue,                java_lang_Byte,         byteValue_name, void_byte_signature, F_R)             \
   do_name(     byteValue_name,          "byteValue")                                                                   \
  do_intrinsic(_charValue,                java_lang_Character,    charValue_name, void_char_signature, F_R)             \
   do_name(     charValue_name,          "charValue")                                                                   \
  do_intrinsic(_shortValue,               java_lang_Short,        shortValue_name, void_short_signature, F_R)           \
   do_name(     shortValue_name,         "shortValue")                                                                  \
  do_intrinsic(_intValue,                 java_lang_Integer,      intValue_name, void_int_signature, F_R)               \
   do_name(     intValue_name,           "intValue")                                                                    \
  do_intrinsic(_longValue,                java_lang_Long,         longValue_name, void_long_signature, F_R)             \
   do_name(     longValue_name,          "longValue")                                                                   \
  do_intrinsic(_floatValue,               java_lang_Float,        floatValue_name, void_float_signature, F_R)           \
   do_name(     floatValue_name,         "floatValue")                                                                  \
  do_intrinsic(_doubleValue,              java_lang_Double,       doubleValue_name, void_double_signature, F_R)         \
   do_name(     doubleValue_name,        "doubleValue")                                                                 \
                                                                                                                        \
  /* boxing methods: */                                                                                                 \
   do_name(    valueOf_name,              "valueOf")                                                                    \
  do_intrinsic(_Boolean_valueOf,          java_lang_Boolean,      valueOf_name, Boolean_valueOf_signature, F_S)         \
   do_name(     Boolean_valueOf_signature,                       "(Z)Ljava/lang/Boolean;")                              \
  do_intrinsic(_Byte_valueOf,             java_lang_Byte,         valueOf_name, Byte_valueOf_signature, F_S)            \
   do_name(     Byte_valueOf_signature,                          "(B)Ljava/lang/Byte;")                                 \
  do_intrinsic(_Character_valueOf,        java_lang_Character,    valueOf_name, Character_valueOf_signature, F_S)       \
   do_name(     Character_valueOf_signature,                     "(C)Ljava/lang/Character;")                            \
  do_intrinsic(_Short_valueOf,            java_lang_Short,        valueOf_name, Short_valueOf_signature, F_S)           \
   do_name(     Short_valueOf_signature,                         "(S)Ljava/lang/Short;")                                \
  do_intrinsic(_Integer_valueOf,          java_lang_Integer,      valueOf_name, Integer_valueOf_signature, F_S)         \
   do_name(     Integer_valueOf_signature,                       "(I)Ljava/lang/Integer;")                              \
  do_intrinsic(_Long_valueOf,             java_lang_Long,         valueOf_name, Long_valueOf_signature, F_S)            \
   do_name(     Long_valueOf_signature,                          "(J)Ljava/lang/Long;")                                 \
  do_intrinsic(_Float_valueOf,            java_lang_Float,        valueOf_name, Float_valueOf_signature, F_S)           \
   do_name(     Float_valueOf_signature,                         "(F)Ljava/lang/Float;")                                \
  do_intrinsic(_Double_valueOf,           java_lang_Double,       valueOf_name, Double_valueOf_signature, F_S)          \
   do_name(     Double_valueOf_signature,                        "(D)Ljava/lang/Double;")                               \
                                                                                                                        \
  /* forEachRemaining */                                                                             \
  do_intrinsic(_forEachRemaining, java_util_stream_StreamsRangeIntSpliterator, forEachRemaining_name, forEachRemaining_signature, F_R) \
   do_name(     forEachRemaining_name,    "forEachRemaining")                                                           \
   do_name(     forEachRemaining_signature,                      "(Ljava/util/function/IntConsumer;)V")                 \

    /*end*/

#define VM_INTRINSIC_ID_ENUM(id, klass, name, sig, flags)  id,
#define VM_INTRINSICS_CONST(id, klass, name, sig, flags)   static const vmIntrinsicID id = vmIntrinsicID::id;
#define __IGNORE_CLASS(id, name)                      /*ignored*/
#define __IGNORE_NAME(id, name)                       /*ignored*/
#define __IGNORE_SIGNATURE(id, name)                  /*ignored*/
#define __IGNORE_ALIAS(id, name)                      /*ignored*/

// VM Intrinsic ID's uniquely identify some very special methods
enum class vmIntrinsicID : int {
  _none = 0,                      // not an intrinsic (default answer)

  VM_INTRINSICS_DO(VM_INTRINSIC_ID_ENUM,
                   __IGNORE_CLASS, __IGNORE_NAME, __IGNORE_SIGNATURE, __IGNORE_ALIAS)

  ID_LIMIT,
  LAST_COMPILER_INLINE = _IndexPartiallyInUpperRange,
  FIRST_MH_SIG_POLY    = _invokeGeneric,
  FIRST_MH_STATIC      = _linkToVirtual,
  LAST_MH_SIG_POLY     = _linkToNative,

  FIRST_ID = _none + 1,
  LAST_ID = ID_LIMIT - 1,
};

ENUMERATOR_RANGE(vmIntrinsicID, vmIntrinsicID::FIRST_ID, vmIntrinsicID::LAST_ID)

class vmIntrinsics : AllStatic {
  friend class vmSymbols;
  friend class ciObjectFactory;

 public:
  typedef vmIntrinsicID ID;

  // Convenient access of vmIntrinsicID::FOO as vmIntrinsics::FOO
  static const ID _none                = vmIntrinsicID::_none;
  static const ID ID_LIMIT             = vmIntrinsicID::ID_LIMIT;
  static const ID LAST_COMPILER_INLINE = vmIntrinsicID::LAST_COMPILER_INLINE;
  static const ID FIRST_MH_SIG_POLY    = vmIntrinsicID::FIRST_MH_SIG_POLY;
  static const ID FIRST_MH_STATIC      = vmIntrinsicID::FIRST_MH_STATIC;
  static const ID LAST_MH_SIG_POLY     = vmIntrinsicID::LAST_MH_SIG_POLY;
  static const ID FIRST_ID             = vmIntrinsicID::FIRST_ID;

  VM_INTRINSICS_DO(VM_INTRINSICS_CONST,
                   __IGNORE_CLASS, __IGNORE_NAME, __IGNORE_SIGNATURE, __IGNORE_ALIAS)

  enum Flags {
    // AccessFlags syndromes relevant to intrinsics.
    F_none = 0,
    F_R,                        // !static !native !synchronized (R="regular")
    F_S,                        //  static !native !synchronized
    F_Y,                        // !static !native  synchronized
    F_RN,                       // !static  native !synchronized
    F_SN,                       //  static  native !synchronized
    F_PI,                       // polymorphic intrinsic

    FLAG_LIMIT
  };
  enum {
    log2_FLAG_LIMIT = 3         // checked by an assert at start-up
  };

  static constexpr bool is_flag_static(Flags flags) {
    switch (flags) {
      case F_S:
      case F_SN:
        return true;
      case F_R:
      case F_Y:
      case F_RN:
        return false;
      case F_PI:
        return false;  // there is no F_SPI
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  static constexpr bool is_flag_synchronized(Flags flags) {
    switch (flags) {
      case F_Y:
        return true;
      case F_RN:
      case F_SN:
      case F_S:
      case F_R:
      case F_PI:
        return false;
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  static constexpr TriBool is_flag_native(Flags flags) {
    switch (flags) {
      case F_RN:
      case F_SN:
        return true;
      case F_S:
      case F_R:
      case F_Y:
        return false;
      case F_PI:  // we don't care whether a poly-intrinsic is native or not
        return TriBool();
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  // Convert an arbitrary vmIntrinsicID to int (checks validity):
  //    vmIntrinsicID x = ...; int n = vmIntrinsics::as_int(x);
  // Convert a known vmIntrinsicID to int (no need for validity check):
  //    int n = static_cast<int>(vmIntrinsicID::_invokeGeneric);
  static constexpr int as_int(vmIntrinsicID id) {
    assert(is_valid_id(id), "must be");
    return static_cast<int>(id);
  }

  static constexpr int number_of_intrinsics() {
    return static_cast<int>(ID_LIMIT);
  }

public:
  static constexpr bool is_valid_id(int raw_id) {
    return (raw_id >= static_cast<int>(_none) && raw_id < static_cast<int>(ID_LIMIT));
  }

  static constexpr bool is_valid_id(ID id) {
    return is_valid_id(static_cast<int>(id));
  }

  static constexpr ID ID_from(int raw_id) {
    assert(is_valid_id(raw_id), "must be a valid intrinsic ID");
    return static_cast<ID>(raw_id);
  }

  static const char* name_at(ID id);

private:
  static ID find_id_impl(vmSymbolID holder,
                         vmSymbolID name,
                         vmSymbolID sig,
                         u2 flags);

  // check if the intrinsic is disabled by course-grained flags.
  static bool disabled_by_jvm_flags(vmIntrinsics::ID id);
  static void init_vm_intrinsic_name_table();
public:
  static ID find_id(const char* name);
  // Given a method's class, name, signature, and access flags, report its ID.
  static ID find_id(vmSymbolID holder,
                    vmSymbolID name,
                    vmSymbolID sig,
                    u2 flags) {
    ID id = find_id_impl(holder, name, sig, flags);
#ifdef ASSERT
    // ID _none does not hold the following asserts.
    if (id == _none)  return id;
#endif
    assert(    class_for(id) == holder, "correct class: %s",     name_at(id));
    assert(     name_for(id) == name,   "correct name: %s",      name_at(id));
    assert(signature_for(id) == sig,    "correct signature: %s", name_at(id));
    assert(      is_flag_static(flags_for(id)) == ((flags & JVM_ACC_STATIC)       != 0),
                 "correct static flag: %s", name_at(id));
    assert(is_flag_synchronized(flags_for(id)) == ((flags & JVM_ACC_SYNCHRONIZED) != 0),
           "correct synchronized flag: %s", name_at(id));
    assert(      is_flag_native(flags_for(id)).match((flags & JVM_ACC_NATIVE)     != 0),
                 "correct native flag: %s", name_at(id));
    return id;
  }

  // Find out the symbols behind an intrinsic:
  static vmSymbolID     class_for(ID id);
  static vmSymbolID      name_for(ID id);
  static vmSymbolID signature_for(ID id);
  static Flags          flags_for(ID id);

  static bool class_has_intrinsics(vmSymbolID holder);

  static const char* short_name_as_C_string(ID id, char* buf, int size);

  // The methods below provide information related to compiling intrinsics.

  // Infrastructure for polymorphic intrinsics.
  // These are parameterized by constant prefix arguments,
  // which act like instruction operation sub-fields.
  // For example:
  // - A MO argument selects between get-plain, get-volatile, etc.
  // - A BT argument selects between get-int, get-byte, etc.
  // - An OP argument selects between get-and-add, get-and-xor, etc.
  enum PolymorphicPrefix {
    PP_NONE = 0,        // regular intrinsic -- not parameterized
    PP_MO,              // getReferenceMO(byte mo, ...)
    PP_MO_BT,           // getPrimitiveBitsMO(byte mo, byte bt, ...)
    PP_MO_BT_OP,        // getAndOperatePrimitiveBitsMO(byte mo, byte bt, byte op, ...)
  };

  // The polymorphism enables the intrinsic to expand to a variable
  // range of instructions, rather than a single instruction (or
  // single sequence).  A polymorphic intrinsic may require some
  // or all of its prefix arguments to be compile-time constants,
  // if it is to be expanded.  For example, some code that performs
  // cmpxchg for a byte is different from code that does the same
  // operation on a long, but both are covered by the same intrinsic.
  // The difference would be T_BYTE vs. T_LONG, as a prefix.
  static PolymorphicPrefix polymorphic_prefix(vmIntrinsics::ID id);

  // Memory order codes used by polymorphic intrinsics.
  // These are also defined as byte constants in the Unsafe class.
  // (See also accessDecorators.hpp for a more detailed API.)
  #define VMI_MEMORY_ORDERS_DO(fn)                \
    fn(UNSAFE_MO_PLAIN,      1)                   \
    fn(UNSAFE_MO_VOLATILE,   2)                   \
    fn(UNSAFE_MO_OPAQUE,     3)                   \
    fn(UNSAFE_MO_ACQUIRE,    4)                   \
    fn(UNSAFE_MO_RELEASE,    8)                   \
    fn(UNSAFE_MO_WEAK_CAS,  16)                   \
    fn(UNSAFE_MO_UNALIGNED, 32)                   \
    /*end*/

  enum MemoryOrder : int {
    UNSAFE_MO_NONE = 0,
    #define VMI_MEMORY_ORDER_DEFINE(mo, code)     mo = code,
    VMI_MEMORY_ORDERS_DO(VMI_MEMORY_ORDER_DEFINE)
    #undef VMI_MEMORY_ORDER_DEFINE
    // There are important derived combinations,
    // such as UNALIGNED+PLAIN, or WEAK_CASE+VOLATILE.
    // These are not separately listed, but can appar
    // as MO arguments to unsafe access primitives.
  };
  static constexpr int UNSAFE_MO_MODE_MASK
      = (UNSAFE_MO_PLAIN|UNSAFE_MO_VOLATILE|
         UNSAFE_MO_ACQUIRE|UNSAFE_MO_RELEASE|UNSAFE_MO_OPAQUE);
  static constexpr int UNSAFE_MO_EXTRA_BITS_MASK
      = (UNSAFE_MO_WEAK_CAS|UNSAFE_MO_UNALIGNED);

  // These are defined as byte constants in the Unsafe class.
  // They are used only by Unsafe.getAndOperatePrimitiveBitsMO.
  #define VMI_PRIMITIVE_BITS_OPERATIONS_DO(fn)    \
    fn(OP_ADD,     '+')                           \
    fn(OP_BITAND,  '&')                           \
    fn(OP_BITOR,   '|')                           \
    fn(OP_BITXOR,  '^')                           \
    fn(OP_SWAP,    '=')                           \
    /*end*/

  enum BitsOperation {
    OP_NONE = 0,
    #define VMI_PRIMITIVE_BITS_OPERATION_DEFINE(op, code)   op = code,
    VMI_PRIMITIVE_BITS_OPERATIONS_DO(VMI_PRIMITIVE_BITS_OPERATION_DEFINE)
    #undef VMI_PRIMITIVE_BITS_OPERATION_DEFINE
  };
  // This high level of macro-abstraction is intended assists us in
  // validating that the constants are the same in Java as in C++.

  // valid mo constant is one of plain, volatile, acquire, or release
  static bool is_valid_memory_order(int mo, int optionally_exclude = -1) {
    switch (mo) {
    case UNSAFE_MO_PLAIN:   case UNSAFE_MO_VOLATILE:
    case UNSAFE_MO_ACQUIRE: case UNSAFE_MO_RELEASE:  case UNSAFE_MO_OPAQUE:
      return (mo != optionally_exclude);
    default:
      return false;
    }
  }

  // The low three bits of the 8 primitive BasicType values encode size.
  static const int PRIMITIVE_SIZE_MASK = 3;
  static size_t primitive_type_size(BasicType bt) {
    assert(is_valid_primitive_type(bt), "BT has no size: %02x", bt);
    return (size_t)1 << (bt & PRIMITIVE_SIZE_MASK);
  }

  static bool is_valid_primitive_type(int bt) {
    // cannot use is_java_primitive because bt might be a weirdo like -1
    return T_BOOLEAN <= bt && bt <= T_LONG;
  }
  static bool is_valid_primitive_bits_op(int op) {
  #define VALID_PRIMITIVE_BITS_CASE(ignore, code) \
    case code: return true;
    switch (op) {
      VMI_PRIMITIVE_BITS_OPERATIONS_DO(VALID_PRIMITIVE_BITS_CASE)
    default:
       return false;
    }
  #undef VALID_PRIMITIVE_BITS_CASE
  }

  // (1) Information needed by the C1 compiler.

  static bool preserves_state(vmIntrinsics::ID id);
  static bool can_trap(vmIntrinsics::ID id);
  static bool should_be_pinned(vmIntrinsics::ID id);
  static ID raw_floating_conversion(BasicType from, BasicType to) {
    switch (from) {
    case T_INT:     if (to == T_FLOAT)  return _intBitsToFloat;       break;
    case T_FLOAT:   if (to == T_INT)    return _floatToRawIntBits;    break;
    case T_LONG:    if (to == T_DOUBLE) return _longBitsToDouble;     break;
    case T_DOUBLE:  if (to == T_LONG)   return _doubleToRawLongBits;  break;
    default:  break;
    }
    return _none;
  }

  // (2) Information needed by the C2 compiler.

  // Returns true if the intrinsic for method 'method' will perform a virtual dispatch.
  static bool does_virtual_dispatch(vmIntrinsics::ID id);
  // A return value larger than 0 indicates that the intrinsic for method
  // 'method' requires predicated logic.
  static int predicates_needed(vmIntrinsics::ID id);

  // There are 2 kinds of JVM options to control intrinsics.
  // 1. Disable/Control Intrinsic accepts a list of intrinsic IDs.
  //    ControlIntrinsic is recommended. DisableIntrinic will be deprecated.
  //    Currently, the DisableIntrinsic list prevails if an intrinsic appears on
  //    both lists.
  //
  // 2. Explicit UseXXXIntrinsics options. eg. UseAESIntrinsics, UseCRC32Intrinsics etc.
  //    Each option can control a group of intrinsics. The user can specify them but
  //    their final values are subject to hardware inspection (VM_Version::initialize).
  //    Stub generators are controlled by them.
  //
  // An intrinsic is enabled if and only if neither the fine-grained control(1) nor
  // the corresponding coarse-grained control(2) disables it.
  static bool is_disabled_by_flags(vmIntrinsics::ID id);

  // Returns true if (a) the intrinsic is not disabled by flags,
  // and (b) it is supported by the current VM version.
  // The method AbstractCompiler::is_intrinsic_available wraps
  // this logic further, potentially reducing the availability
  // by additional checks, (c) whether the particular compiler
  // backend supports the intrinsic, and (d) whether compiler
  // directives (perhaps method-specific ones) have disabled
  // the intrinsic.
  static bool is_intrinsic_available(vmIntrinsics::ID id);
};

#undef VM_INTRINSIC_ENUM
#undef VM_INTRINSICS_CONST
#undef __IGNORE_CLASS
#undef __IGNORE_NAME
#undef __IGNORE_SIGNATURE
#undef __IGNORE_ALIAS

#endif // SHARE_CLASSFILE_VMINTRINSICS_HPP
