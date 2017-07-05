/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// This file holds platform-independent bodies of inline functions for frames.

// Note: The bcx usually contains the bcp; however during GC it contains the bci
//       (changed by gc_prologue() and gc_epilogue()) to be methodOop position
//       independent. These accessors make sure the correct value is returned
//       by testing the range of the bcx value. bcp's are guaranteed to be above
//       max_method_code_size, since methods are always allocated in OldSpace and
//       Eden is allocated before OldSpace.
//
//       The bcp is accessed sometimes during GC for ArgumentDescriptors; than
//       the correct translation has to be performed (was bug).

inline bool frame::is_bci(intptr_t bcx) {
#ifdef _LP64
  return ((uintptr_t) bcx) <= ((uintptr_t) max_method_code_size) ;
#else
  return 0 <= bcx && bcx <= max_method_code_size;
#endif
}

inline bool frame::is_entry_frame() const {
  return StubRoutines::returns_to_call_stub(pc());
}

inline bool frame::is_first_frame() const {
  return is_entry_frame() && entry_frame_is_first();
}

// here are the platform-dependent bodies:

# include "incls/_frame_pd.inline.hpp.incl"
