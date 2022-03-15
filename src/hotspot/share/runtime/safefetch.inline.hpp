/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_RUNTIME_SAFEFETCH_INLINE_HPP
#define SHARE_RUNTIME_SAFEFETCH_INLINE_HPP

// No safefetch.hpp
#include "memory/allStatic.hpp"
#include OS_HEADER(safefetch)
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef HAVE_STATIC_SAFEFETCH

inline int SafeFetch32(int* adr, int errValue) {
  return _SafeFetch32(adr, errValue);
}

inline intptr_t SafeFetchN(intptr_t* adr, intptr_t errValue) {
  return
      LP64_ONLY((intptr_t)_SafeFetch64((uint64_t*)adr, (uint64_t) errValue))
      NOT_LP64((intptr_t)_SafeFetch32((int*)adr, (int) errValue));
}

inline bool CanUseSafeFetch32() { return true; }
inline bool CanUseSafeFetchN()  { return true; }

class SafeFetchHelper : public AllStatic {
  static bool is_safefetch32_fault(address pc) { return pc == (address)_SafeFetch32_fault; }
  LP64_ONLY(static bool is_safefetch64_fault(address pc) { return pc == (address)_SafeFetch64_fault; })
public:
  static bool is_safefetch_fault(address pc) {
    return is_safefetch32_fault(pc) LP64_ONLY(|| is_safefetch64_fault(pc));
  }
  static address continuation_for_safefetch_fault(address pc) {
    assert(is_safefetch_fault(pc), "not a safefetch fault pc");
#ifdef _LP64
    return is_safefetch32_fault(pc) ? (address)_SafeFetch32_continuation : (address)_SafeFetch64_continuation;
#else
    return (address)_SafeFetch32_continuation;
#endif
  }
};

#else

#include "runtime/stubRoutines.hpp"
#include "runtime/threadWXSetters.inline.hpp"

// Safefetch allows to load a value from a location that's not known
// to be valid. If the load causes a fault, the error value is returned.
inline int SafeFetch32(int* adr, int errValue) {
  assert(StubRoutines::SafeFetch32_stub(), "stub not yet generated");
#if defined(__APPLE__) && defined(AARCH64)
  Thread* thread = Thread::current_or_null_safe();
  assert(thread != NULL, "required for W^X management");
  ThreadWXEnable wx(WXExec, thread);
#endif // __APPLE__ && AARCH64
  return StubRoutines::SafeFetch32_stub()(adr, errValue);
}

inline intptr_t SafeFetchN(intptr_t* adr, intptr_t errValue) {
  assert(StubRoutines::SafeFetchN_stub(), "stub not yet generated");
#if defined(__APPLE__) && defined(AARCH64)
  Thread* thread = Thread::current_or_null_safe();
  assert(thread != NULL, "required for W^X management");
  ThreadWXEnable wx(WXExec, thread);
#endif // __APPLE__ && AARCH64
  return StubRoutines::SafeFetchN_stub()(adr, errValue);
}

// returns true if SafeFetch32 and SafeFetchN can be used safely (stubroutines are already generated)
inline bool CanUseSafeFetch32() {
  return StubRoutines::SafeFetch32_stub() ? true : false;
}

inline bool CanUseSafeFetchN() {
  return StubRoutines::SafeFetchN_stub() ? true : false;
}

class SafeFetchHelper : public AllStatic {
public:
  static bool is_safefetch_fault(address pc) {
    return StubRoutines::is_safefetch_fault(pc);
  }
  static address continuation_for_safefetch_fault(address pc) {
    return StubRoutines::continuation_for_safefetch_fault(pc);
  }
};

#endif // HAVE_STATIC_SAFEFETCH

#endif // SHARE_RUNTIME_SAFEFETCH_INLINE_HPP
