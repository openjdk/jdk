/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_SPECIALIZED_OOP_CLOSURES_HPP
#define SHARE_VM_GC_SHARED_SPECIALIZED_OOP_CLOSURES_HPP

#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1_specialized_oop_closures.hpp"
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
// MarkSweep
class MarkAndPushClosure;
// ParNew
class ParScanWithBarrierClosure;
class ParScanWithoutBarrierClosure;
// CMS
class MarkRefsIntoAndScanClosure;
class ParMarkRefsIntoAndScanClosure;
class PushAndMarkClosure;
class ParPushAndMarkClosure;
class PushOrMarkClosure;
class ParPushOrMarkClosure;
class CMSKeepAliveClosure;
class CMSInnerParMarkAndPushClosure;
// Misc
class NoHeaderExtendedOopClosure;

// This macro applies an argument macro to all OopClosures for which we
// want specialized bodies of "oop_oop_iterate".  The arguments to "f" are:
//   "f(closureType, non_virtual)"
// where "closureType" is the name of the particular subclass of ExtendedOopClosure,
// and "non_virtual" will be the string "_nv" if the closure type should
// have its "do_oop" method invoked non-virtually, or else the
// string "_v".  ("ExtendedOopClosure" itself will be the only class in the latter
// category.)

// This is split into several because of a Visual C++ 6.0 compiler bug
// where very long macros cause the compiler to crash

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

#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_MS(f)      \
  f(MarkAndPushClosure,_nv)

#if INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_CMS(f)     \
  f(MarkRefsIntoAndScanClosure,_nv)                     \
  f(ParMarkRefsIntoAndScanClosure,_nv)                  \
  f(PushAndMarkClosure,_nv)                             \
  f(ParPushAndMarkClosure,_nv)                          \
  f(PushOrMarkClosure,_nv)                              \
  f(ParPushOrMarkClosure,_nv)                           \
  f(CMSKeepAliveClosure,_nv)                            \
  f(CMSInnerParMarkAndPushClosure,_nv)
#endif

#if INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)       \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_MS(f)            \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_CMS(f)           \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_G1(f)
#else  // INCLUDE_ALL_GCS
#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)       \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_MS(f)
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
  f(ParMarkRefsIntoAndScanClosure,_nv)                 \
  f(ParPushAndMarkClosure,_nv)

#define ALL_PAR_OOP_ITERATE_CLOSURES(f)                \
  f(ExtendedOopClosure,_v)                             \
  SPECIALIZED_PAR_OOP_ITERATE_CLOSURES(f)
#endif // INCLUDE_ALL_GCS

// This macro applies an argument macro to all OopClosures for which we
// want specialized bodies of a family of methods related to
// "oops_since_save_marks_do".  The arguments to f are the same as above.
// The "root_class" is the most general class to define; this may be
// "OopClosure" in some applications and "OopsInGenClosure" in others.

#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_S(f) \
  f(ScanClosure,_nv)                                     \
  f(FastScanClosure,_nv)

#if INCLUDE_ALL_GCS
#define SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES_YOUNG_P(f) \
  f(ParScanWithBarrierClosure,_nv)                       \
  f(ParScanWithoutBarrierClosure,_nv)
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

#define ALL_SINCE_SAVE_MARKS_CLOSURES(f)                \
  f(OopsInGenClosure,_v)                                \
  SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)

#endif // SHARE_VM_GC_SHARED_SPECIALIZED_OOP_CLOSURES_HPP
