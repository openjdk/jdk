/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

// The following OopClosure types get specialized versions of
// "oop_oop_iterate" that invoke the closures' do_oop methods
// non-virtually, using a mechanism defined in this file.  Extend these
// macros in the obvious way to add specializations for new closures.

// Forward declarations.
enum G1Barrier {
  G1BarrierNone, G1BarrierRS, G1BarrierEvac
};

template<bool do_gen_barrier, G1Barrier barrier,
         bool do_mark_forwardee>
class G1ParCopyClosure;
class G1ParScanClosure;
class G1ParPushHeapRSClosure;

typedef G1ParCopyClosure<false, G1BarrierEvac, false> G1ParScanHeapEvacClosure;

class FilterIntoCSClosure;
class FilterOutOfRegionClosure;
class FilterInHeapRegionAndIntoCSClosure;
class FilterAndMarkInHeapRegionAndIntoCSClosure;

#ifdef FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES
#error "FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES already defined."
#endif

#define FURTHER_SPECIALIZED_OOP_OOP_ITERATE_CLOSURES(f) \
      f(G1ParScanHeapEvacClosure,_nv)                   \
      f(G1ParScanClosure,_nv)                           \
      f(G1ParPushHeapRSClosure,_nv)                     \
      f(FilterIntoCSClosure,_nv)                        \
      f(FilterOutOfRegionClosure,_nv)                   \
      f(FilterInHeapRegionAndIntoCSClosure,_nv)         \
      f(FilterAndMarkInHeapRegionAndIntoCSClosure,_nv)

#ifdef FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES
#error "FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES already defined."
#endif

#define FURTHER_SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(f)
