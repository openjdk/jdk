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
#include "runtime/safefetch_method.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef SAFEFETCH_METHOD_STATIC_ASSEMBLY

extern "C" int _SafeFetch32(int* adr, int errValue);
extern "C" char _SafeFetch32_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetch32_fault[] __attribute__ ((visibility ("hidden")));

#ifdef _LP64
extern "C" uint64_t _SafeFetch64(uint64_t* adr, uint64_t errValue);
extern "C" char _SafeFetch64_continuation[] __attribute__ ((visibility ("hidden")));
extern "C" char _SafeFetch64_fault[] __attribute__ ((visibility ("hidden")));
#endif // _LP64

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

struct SafeFetchHelper : public AllStatic {

  static bool is_safefetch_fault(address pc) {
    return pc == (address)_SafeFetch32_fault
                 LP64_ONLY(|| pc == (address)_SafeFetch64_fault);
  }

  static address continuation_for_safefetch_fault(address pc) {
    if (pc == (address)_SafeFetch32_fault) {
      return (address)_SafeFetch32_continuation;
    }
#ifdef _LP64
    else if (pc == (address)_SafeFetch64_fault) {
      return (address)_SafeFetch64_continuation;
    }
#endif
    else {
      ShouldNotReachHere();
    }
    return NULL;
  }

};

#endif // SAFEFETCH_METHOD_STATIC_ASSEMBLY

#endif // SHARE_RUNTIME_SAFEFETCH_INLINE_HPP
