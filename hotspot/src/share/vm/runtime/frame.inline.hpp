/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_FRAME_INLINE_HPP
#define SHARE_VM_RUNTIME_FRAME_INLINE_HPP

#include "interpreter/bytecodeInterpreter.hpp"
#include "interpreter/bytecodeInterpreter.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/method.hpp"
#include "runtime/frame.hpp"
#include "runtime/signature.hpp"
#ifdef TARGET_ARCH_x86
# include "jniTypes_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "jniTypes_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "jniTypes_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "jniTypes_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "jniTypes_ppc.hpp"
#endif
#ifdef ZERO
#ifdef TARGET_ARCH_zero
# include "entryFrame_zero.hpp"
# include "fakeStubFrame_zero.hpp"
# include "interpreterFrame_zero.hpp"
# include "sharkFrame_zero.hpp"
#endif
#endif

// This file holds platform-independent bodies of inline functions for frames.

// Note: The bcx usually contains the bcp; however during GC it contains the bci
//       (changed by gc_prologue() and gc_epilogue()) to be Method* position
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

inline bool frame::is_stub_frame() const {
  return StubRoutines::is_stub_code(pc()) || (_cb != NULL && _cb->is_adapter_blob());
}

inline bool frame::is_first_frame() const {
  return is_entry_frame() && entry_frame_is_first();
}

// here are the platform-dependent bodies:

#ifdef TARGET_ARCH_x86
# include "frame_x86.inline.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "frame_sparc.inline.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "frame_zero.inline.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "frame_arm.inline.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "frame_ppc.inline.hpp"
#endif


#endif // SHARE_VM_RUNTIME_FRAME_INLINE_HPP
