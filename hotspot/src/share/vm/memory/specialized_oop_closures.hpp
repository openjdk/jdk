/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_SPECIALIZED_OOP_CLOSURES_HPP
#define SHARE_VM_MEMORY_SPECIALIZED_OOP_CLOSURES_HPP

#include "runtime/atomic.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1_specialized_oop_closures.hpp"
#endif // INCLUDE_ALL_GCS

// The following OopClosure types get specialized versions of
// "oop_oop_iterate" that invoke the closures' do_oop methods
// non-virtually, using a mechanism defined in this file.  Extend these
// macros in the obvious way to add specializations for new closures.

// Forward declarations.
class OopClosure;
class OopsInGenClosure;
// DefNew
class ScanClosure;
class FastScanClosure;
class FilteringClosure;
// ParNew
class ParScanWithBarrierClosure;
class ParScanWithoutBarrierClosure;
// CMS
class MarkRefsIntoAndScanClosure;
class Par_MarkRefsIntoAndScanClosure;
class PushAndMarkClosure;
class Par_PushAndMarkClosure;
class PushOrMarkClosure;
class Par_PushOrMarkClosure;
class CMSKeepAliveClosure;
class CMSInnerParMarkAndPushClosure;
// Misc
class NoHeaderExtendedOopClosure;

// This macro applies an argument macro to all OopClosures for which we
// want specialized bodies of "oop_oop_iterate".  The arguments to "f" are:
//   "f(closureType, non_virtual)"
// where "closureType" is the name of the particular subclass of OopClosure,
// and "non_virtual" will be the string "_nv" if the closure type should
// have its "do_oop" method invoked non-virtually, or else the
// string "_v".  ("OopClosure" itself will be the only class in the latter
// category.)

// This is split into several because of a Visual C++ 6.0 compiler bug
// where very long macros cause the compiler to crash

// Some other heap might define further specialized closures.
#ifndef FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES
#define FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES(f) \
        /* None */
#endif

#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_S(f)       \
  f(ScanClosure,_nv)                                    \
  f(FastScanClosure,_nv)                                \
  f(FilteringClosure,_nv)

#if INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_P(f)       \
  f(ParScanWithBarrierClosure,_nv)                      \
  f(ParScanWithoutBarrierClosure,_nv)
#else  // INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_P(f)
#endif // INCLUDE_ALL_GCS

#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_1(f)       \
  f(NoHeaderExtendedOopClosure,_nv)                     \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_S(f)             \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_P(f)

#if INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)       \
  f(MarkRefsIntoAndScanClosure,_nv)                     \
  f(Par_MarkRefsIntoAndScanClosure,_nv)                 \
  f(PushAndMarkClosure,_nv)                             \
  f(Par_PushAndMarkClosure,_nv)                         \
  f(PushOrMarkClosure,_nv)                              \
  f(Par_PushOrMarkClosure,_nv)                          \
  f(CMSKeepAliveClosure,_nv)                            \
  f(CMSInnerParMarkAndPushClosure,_nv)                  \
  FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES(f)
#else  // INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)
#endif // INCLUDE_ALL_GCS


// We separate these out, because sometime the general one has
// a different definition from the specialized ones, and sometimes it
// doesn't.

#define ALL_OOP_OOP_ITERATE_CLOSURES_1(f)               \
  f(ExtendedOopClosure,_v)                              \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_1(f)

#define ALL_OOP_OOP_ITERATE_CLOSURES_2(f)               \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)

#if INCLUDE_ALL_GCS
// This macro applies an argument macro to all OopClosures for which we
// want specialized bodies of a family of methods related to
// "par_oop_iterate".  The arguments to f are the same as above.
// The "root_class" is the most general class to define; this may be
// "OopClosure" in some applications and "OopsInGenClosure" in others.

#define SPECIALIZED_PAR_OOP_ITERATE_CLOSURES(f)        \
  f(MarkRefsIntoAndScanClosure,_nv)                    \
  f(PushAndMarkClosure,_nv)                            \
  f(Par_MarkRefsIntoAndScanClosure,_nv)                \
  f(Par_PushAndMarkClosure,_nv)

#define ALL_PAR_OOP_ITERATE_CLOSURES(f)                \
  f(ExtendedOopClosure,_v)                             \
  SPECIALIZED_PAR_OOP_ITERATE_CLOSURES(f)
#endif // INCLUDE_ALL_GCS

// This macro applies an argument macro to all OopClosures for which we
// want specialized bodies of a family of methods related to
// "oops_since_save_marks_do".  The arguments to f are the same as above.
// The "root_class" is the most general class to define; this may be
// "OopClosure" in some applications and "OopsInGenClosure" in others.


// Some other heap might define further specialized closures.
#ifndef FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES
#define FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f) \
        /* None */
#endif

#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_S(f) \
  f(ScanClosure,_nv)                                     \
  f(FastScanClosure,_nv)

#if INCLUDE_ALL_GCS
#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_P(f) \
  f(ParScanWithBarrierClosure,_nv)                       \
  f(ParScanWithoutBarrierClosure,_nv)                    \
  FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)
#else  // INCLUDE_ALL_GCS
#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_P(f)
#endif // INCLUDE_ALL_GCS

#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG(f)  \
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_S(f)      \
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_P(f)

#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)        \
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG(f)

// We separate these out, because sometime the general one has
// a different definition from the specialized ones, and sometimes it
// doesn't.
// NOTE:   One of the valid criticisms of this
// specialize-oop_oop_iterate-for-specific-closures idiom is that it is
// easy to have a silent performance bug: if you fail to de-virtualize,
// things still work, just slower.  The "SpecializationStats" mode is
// intended to at least make such a failure easy to detect.
// *Not* using the ALL_SINCE_SAVE_MARKS_CLOSURES(f) macro defined
// below means that *only* closures for which oop_oop_iterate specializations
// exist above may be applied to "oops_since_save_marks".  That is,
// this form of the performance bug is caught statically.  When you add
// a definition for the general type, this property goes away.
// Make sure you test with SpecializationStats to find such bugs
// when introducing a new closure where you don't want virtual dispatch.

#define ALL_SINCE_SAVE_MARKS_CLOSURES(f)                \
  f(OopsInGenClosure,_v)                                \
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)

// For keeping stats on effectiveness.
#define ENABLE_SPECIALIZATION_STATS 0


class SpecializationStats {
public:
  enum Kind {
    ik,             // InstanceKlass
    irk,            // InstanceRefKlass
    oa,             // ObjArrayKlass
    NUM_Kinds
  };

#if ENABLE_SPECIALIZATION_STATS
private:
  static bool _init;
  static bool _wrapped;
  static jint _numCallsAll;

  static jint _numCallsTotal[NUM_Kinds];
  static jint _numCalls_nv[NUM_Kinds];

  static jint _numDoOopCallsTotal[NUM_Kinds];
  static jint _numDoOopCalls_nv[NUM_Kinds];
public:
#endif
  static void clear()  PRODUCT_RETURN;

  static inline void record_call()  PRODUCT_RETURN;
  static inline void record_iterate_call_v(Kind k)  PRODUCT_RETURN;
  static inline void record_iterate_call_nv(Kind k)  PRODUCT_RETURN;
  static inline void record_do_oop_call_v(Kind k)  PRODUCT_RETURN;
  static inline void record_do_oop_call_nv(Kind k)  PRODUCT_RETURN;

  static void print() PRODUCT_RETURN;
};

#ifndef PRODUCT
#if ENABLE_SPECIALIZATION_STATS

inline void SpecializationStats::record_call() {
  Atomic::inc(&_numCallsAll);
}
inline void SpecializationStats::record_iterate_call_v(Kind k) {
  Atomic::inc(&_numCallsTotal[k]);
}
inline void SpecializationStats::record_iterate_call_nv(Kind k) {
  Atomic::inc(&_numCallsTotal[k]);
  Atomic::inc(&_numCalls_nv[k]);
}

inline void SpecializationStats::record_do_oop_call_v(Kind k) {
  Atomic::inc(&_numDoOopCallsTotal[k]);
}
inline void SpecializationStats::record_do_oop_call_nv(Kind k) {
  Atomic::inc(&_numDoOopCallsTotal[k]);
  Atomic::inc(&_numDoOopCalls_nv[k]);
}

#else   // !ENABLE_SPECIALIZATION_STATS

inline void SpecializationStats::record_call() {}
inline void SpecializationStats::record_iterate_call_v(Kind k) {}
inline void SpecializationStats::record_iterate_call_nv(Kind k) {}
inline void SpecializationStats::record_do_oop_call_v(Kind k) {}
inline void SpecializationStats::record_do_oop_call_nv(Kind k) {}
inline void SpecializationStats::clear() {}
inline void SpecializationStats::print() {}

#endif  // ENABLE_SPECIALIZATION_STATS
#endif  // !PRODUCT

#endif // SHARE_VM_MEMORY_SPECIALIZED_OOP_CLOSURES_HPP
