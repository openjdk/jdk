/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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
#if INCLUDE_CMSGC
#include "gc/cms/cms_specialized_oop_closures.hpp"
#endif
#if INCLUDE_G1GC
#include "gc/g1/g1_specialized_oop_closures.hpp"
#endif
#if INCLUDE_SERIALGC
#include "gc/serial/serial_specialized_oop_closures.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/z_specialized_oop_closures.hpp"
#endif

// The following OopClosure types get specialized versions of
// "oop_oop_iterate" that invoke the closures' do_oop methods
// non-virtually, using a mechanism defined in this file.  Extend these
// macros in the obvious way to add specializations for new closures.

// Forward declarations.
class ExtendedOopClosure;
class NoHeaderExtendedOopClosure;
class OopsInGenClosure;

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

#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_1(f)                 \
  f(NoHeaderExtendedOopClosure,_nv)                               \
  SERIALGC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_S(f))        \
     CMSGC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_P(f))

#define SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)                 \
  SERIALGC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_MS(f))       \
     CMSGC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_CMS(f))      \
      G1GC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_G1(f))       \
      G1GC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_G1FULL(f))   \
       ZGC_ONLY(SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_Z(f))

// We separate these out, because sometime the general one has
// a different definition from the specialized ones, and sometimes it
// doesn't.

#define ALL_OOP_OOP_ITERATE_CLOSURES_1(f)                         \
  f(ExtendedOopClosure,_v)                                        \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_1(f)

#define ALL_OOP_OOP_ITERATE_CLOSURES_2(f)                         \
  SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_2(f)

#endif // SHARE_VM_GC_SHARED_SPECIALIZED_OOP_CLOSURES_HPP
